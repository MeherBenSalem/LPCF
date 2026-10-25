#!/usr/bin/env python3
"""A minimal offline launcher for vanilla Minecraft clients, used by the QA scripts to join the test server.

  python3 tools/qa/mcclient.py prepare 26.3 [--assets]         # client jar, libraries, asset index (and objects)
  python3 tools/qa/mcclient.py launch 26.3 Builder 127.0.0.1:25565 --log client.log
  python3 tools/qa/mcclient.py stop Builder

Files go to $QA_HOME/clients (default /tmp/lpcf-qa/clients). Each username gets its own game directory with an
options.txt that skips the first-launch screens. The client runs on the Java its version asks for
(/usr/lib/jvm/java-<N>-openjdk-amd64). Asset objects (about 500 MB, shared by every version) are needed to start
the game; `launch` downloads whatever is missing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
import time
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
HOME = Path(os.environ.get('QA_HOME', '/tmp/lpcf-qa')) / 'clients'

OPTIONS = {
    'onboardAccessibility': 'false', 'skipMultiplayerWarning': 'true', 'joinedFirstServer': 'true',
    'tutorialStep': 'none', 'pauseOnLostFocus': 'false', 'soundCategory_master': '0.0', 'renderDistance': '4',
    'simulationDistance': '5', 'chatScale': '1.0', 'guiScale': '2', 'narrator': '0', 'renderClouds': '"false"',
    'maxFps': '30', 'graphicsMode': '0', 'fullscreen': 'false',
}


def fetch(url: str, dest: Path, sha1: str | None = None) -> Path:
    if dest.exists() and (sha1 is None or hashlib.sha1(dest.read_bytes()).hexdigest() == sha1):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + '.part')
    with urllib.request.urlopen(url, timeout=120) as res, open(tmp, 'wb') as out:
        while chunk := res.read(1 << 16):
            out.write(chunk)
    if sha1 and hashlib.sha1(tmp.read_bytes()).hexdigest() != sha1:
        tmp.unlink()
        raise IOError(f'checksum mismatch for {url}')
    tmp.replace(dest)
    return dest


def allowed(rules) -> bool:
    if not rules:
        return True
    result = False
    for rule in rules:
        os_rule = rule.get('os', {})
        matches = (not os_rule.get('name') or os_rule['name'] == 'linux') and \
                  (not os_rule.get('arch') or os_rule['arch'] == platform.machine())
        if rule.get('features'):
            matches = False
        if matches:
            result = rule['action'] == 'allow'
    return result


def version_json(version: str) -> dict:
    path = HOME / version / f'{version}.json'
    if not path.exists():
        manifest = json.load(urllib.request.urlopen(MANIFEST, timeout=60))
        entry = next(v for v in manifest['versions'] if v['id'] == version)
        fetch(entry['url'], path, entry.get('sha1'))
    return json.loads(path.read_text())


def prepare(version: str, assets: bool = False) -> dict:
    meta = version_json(version)
    base = HOME / version
    fetch(meta['downloads']['client']['url'], base / 'client.jar', meta['downloads']['client']['sha1'])
    jobs = []
    for lib in meta['libraries']:
        if not allowed(lib.get('rules')):
            continue
        art = lib.get('downloads', {}).get('artifact')
        if art:
            jobs.append((art['url'], HOME / 'libraries' / art['path'], art['sha1']))
    index = meta['assetIndex']
    index_path = fetch(index['url'], HOME / 'assets' / 'indexes' / f"{index['id']}.json", index['sha1'])
    if assets:
        for obj in json.loads(index_path.read_text())['objects'].values():
            h = obj['hash']
            jobs.append((f'https://resources.download.minecraft.net/{h[:2]}/{h}',
                         HOME / 'assets' / 'objects' / h[:2] / h, h))
    with ThreadPoolExecutor(8) as pool:
        list(pool.map(lambda job: fetch(*job), jobs))
    return meta


def classpath(meta: dict, version: str) -> str:
    paths = []
    for lib in meta['libraries']:
        art = lib.get('downloads', {}).get('artifact')
        if art and allowed(lib.get('rules')):
            paths.append(str(HOME / 'libraries' / art['path']))
    paths.append(str(HOME / version / 'client.jar'))
    return os.pathsep.join(paths)


def java_for(meta: dict) -> str:
    major = meta.get('javaVersion', {}).get('majorVersion', 21)
    override = os.environ.get(f'QA_JAVA_{major}')
    if override:
        return override
    for candidate in (major, 25 if major > 21 else 21):
        path = Path(f'/usr/lib/jvm/java-{candidate}-openjdk-amd64/bin/java')
        if path.exists():
            return str(path)
    return 'java'


def launch(version: str, username: str, server: str | None, log: Path | None) -> subprocess.Popen:
    meta = prepare(version, assets=True)
    game_dir = HOME / version / 'game' / username
    natives = game_dir / 'natives'
    for sub in ('', 'java', 'jna', 'lwjgl', 'netty'):
        (natives / sub).mkdir(parents=True, exist_ok=True)
    options = game_dir / 'options.txt'
    if not options.exists():
        options.write_text(''.join(f'{k}:{v}\n' for k, v in OPTIONS.items()))
    values = {
        'auth_player_name': username, 'version_name': version, 'game_directory': str(game_dir),
        'assets_root': str(HOME / 'assets'), 'assets_index_name': meta['assetIndex']['id'],
        'auth_uuid': str(uuid.uuid3(uuid.NAMESPACE_OID, 'OfflinePlayer:' + username)).replace('-', ''),
        'auth_access_token': '0', 'clientid': '0', 'auth_xuid': '0', 'user_type': 'legacy',
        'version_type': 'release', 'natives_directory': str(natives), 'launcher_name': 'lpcf-qa',
        'launcher_version': '1', 'classpath': classpath(meta, version),
    }

    def expand(args):
        out = []
        for arg in args:
            if isinstance(arg, dict):
                if not allowed(arg.get('rules')):
                    continue
                arg = arg['value']
            for part in (arg if isinstance(arg, list) else [arg]):
                for key, value in values.items():
                    part = part.replace('${' + key + '}', value)
                out.append(part)
        return out

    cmd = [java_for(meta), '-Xmx1536m', *expand(meta['arguments']['jvm']), meta['mainClass'],
           *expand(meta['arguments']['game'])]
    cmd += ['--width', os.environ.get('QA_WIDTH', '1920'), '--height', os.environ.get('QA_HEIGHT', '1080')]
    if server:
        cmd += ['--quickPlayMultiplayer', server]
    env = dict(os.environ, DISPLAY=os.environ.get('DISPLAY', ':1'), LIBGL_ALWAYS_SOFTWARE='1',
               GALLIUM_DRIVER='llvmpipe', SDL_VIDEO_FORCE_EGL='1')
    out = open(log, 'w') if log else subprocess.DEVNULL
    before = set(windows(env))
    proc = subprocess.Popen(cmd, cwd=game_dir, env=env, stdout=out, stderr=subprocess.STDOUT,
                            start_new_session=True)
    (HOME / f'{username}.pid').write_text(str(proc.pid))
    (HOME / f'{username}.wid').unlink(missing_ok=True)
    # Remember the client's window: older clients (GLFW) do not tell X their process id, so the QA scripts find
    # each player's window by this id.
    deadline = time.time() + 300
    while time.time() < deadline and proc.poll() is None:
        new = [w for w in windows(env) if w not in before]
        if new:
            (HOME / f'{username}.wid').write_text(new[-1])
            break
        time.sleep(1)
    return proc


def windows(env) -> list[str]:
    try:
        out = subprocess.run(['xdotool', 'search', '--onlyvisible', '--name', 'Minecraft'], env=env, capture_output=True, text=True)
        return out.stdout.split()
    except FileNotFoundError:
        return []


def stop(username: str) -> None:
    pid_file = HOME / f'{username}.pid'
    if pid_file.exists():
        try:
            os.kill(int(pid_file.read_text()), 15)
        except ProcessLookupError:
            pass
        pid_file.unlink()
    (HOME / f'{username}.wid').unlink(missing_ok=True)


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest='cmd', required=True)
    p = sub.add_parser('prepare')
    p.add_argument('version')
    p.add_argument('--assets', action='store_true')
    l = sub.add_parser('launch')
    l.add_argument('version')
    l.add_argument('username')
    l.add_argument('server', nargs='?')
    l.add_argument('--log')
    st = sub.add_parser('stop')
    st.add_argument('username')
    args = ap.parse_args()
    if args.cmd == 'stop':
        stop(args.username)
        return
    if args.cmd == 'prepare':
        prepare(args.version, args.assets)
        print('prepared', args.version)
    else:
        proc = launch(args.version, args.username, args.server, Path(args.log) if args.log else None)
        print(proc.pid)
        sys.stdout.flush()


if __name__ == '__main__':
    main()
