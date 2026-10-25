#!/usr/bin/env python3
"""Real Paper / Folia / Purpur test servers for QA.

  python3 tools/qa/server.py prepare paper 26.3 [--legacy-config] [--normal-world] [--port 25565]
  python3 tools/qa/server.py start paper 26.3
  python3 tools/qa/server.py stop paper 26.3

`prepare` downloads the newest build of the server for that Minecraft version, LuckPerms and PlaceholderAPI (from
Modrinth) and installs build/libs/LPCF-<VERSION>.jar. It resets the world and the plugin folders, and writes
server.properties with RCON on 25575 (password qa), offline mode, no whitelist and a flat world. With
--legacy-config it installs a 1.2.5 config in plugins/LuckPermsChatFormatterFolia, to check the 2.0 migration.
Servers live in $QA_HOME/servers/<platform>-<version> (default /tmp/lpcf-qa); the log is server.log there.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import signal
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOME = Path(os.environ.get('QA_HOME', '/tmp/lpcf-qa'))
CACHE = HOME / 'cache'

LEGACY_CONFIG = '''# LuckPermsChatFormatterFolia Configuration (1.2.5 default, as a server would have it)
chat-format: "{prefix}{name}<dark_gray> »<reset> {message}"

nametag-format: "{prefix}{name}"

group-formats:
#  default: "<gray>[User]</gray> {name}<dark_gray> »<reset> {message}"

track-formats:
#  staff_track: "<gold>[Staff]</gold> {name}<dark_gray> »<reset> {message}"

use-item-placeholder: true

reload-message: "<green>LPCF configuration reloaded successfully!"

update-check:
  enabled: false
  message: "<yellow>A new LPCF version is available: <white>{current} <gray>-> <green>{latest}"
'''


def get_json(url: str):
    req = urllib.request.Request(url, headers={'User-Agent': 'NightBeam-LPCF-QA'})
    return json.load(urllib.request.urlopen(req, timeout=60))


def download(url: str, dest: Path) -> Path:
    if dest.exists():
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={'User-Agent': 'NightBeam-LPCF-QA'})
    tmp = dest.with_suffix('.part')
    with urllib.request.urlopen(req, timeout=300) as res, open(tmp, 'wb') as out:
        shutil.copyfileobj(res, out)
    tmp.replace(dest)
    return dest


def server_jar(platform: str, version: str) -> tuple[Path, str]:
    if platform in ('paper', 'folia'):
        builds = get_json(f'https://fill.papermc.io/v3/projects/{platform}/versions/{version}/builds')
        build = builds[0]
        url = build['downloads']['server:default']['url']
        return download(url, CACHE / f'{platform}-{version}-{build["id"]}.jar'), str(build['id'])
    if platform == 'purpur':
        build = get_json(f'https://api.purpurmc.org/v2/purpur/{version}')['builds']['latest']
        return download(f'https://api.purpurmc.org/v2/purpur/{version}/{build}/download',
                        CACHE / f'purpur-{version}-{build}.jar'), str(build)
    raise SystemExit(f'unknown platform {platform}')


def modrinth_jar(project: str, version: str, loader: str) -> Path:
    q = urllib.parse.urlencode({'game_versions': json.dumps([version]), 'loaders': json.dumps([loader])})
    versions = get_json(f'https://api.modrinth.com/v2/project/{project}/version?{q}')
    if not versions and loader != 'paper':
        return modrinth_jar(project, version, 'paper')
    file = next(f for f in versions[0]['files'] if f.get('primary', True))
    return download(file['url'], CACHE / file['filename'])


def java_for(version: str) -> str:
    # 1.20.4 and older run on Java 17, 1.20.5 to 1.21.11 on 21, 26.x on 25.
    major = 17 if version.startswith('1.20') and version not in ('1.20.5', '1.20.6') else 21 if version.startswith('1.') else 25
    path = Path(f'/usr/lib/jvm/java-{major}-openjdk-amd64/bin/java')
    return str(path) if path.exists() else 'java'


def server_dir(platform: str, version: str) -> Path:
    return HOME / 'servers' / f'{platform}-{version}'


def plugin_jar() -> Path:
    version = (ROOT / 'VERSION').read_text().strip()
    jar = Path(os.environ.get('LPCF_JAR', ROOT / 'build' / 'libs' / f'LPCF-{version}.jar'))
    if not jar.exists():
        raise SystemExit(f'{jar} not found: run ./gradlew shadowJar first')
    return jar


def prepare(platform: str, version: str, legacy_config: bool, normal_world: bool, port: int) -> None:
    d = server_dir(platform, version)
    d.mkdir(parents=True, exist_ok=True)
    jar, build = server_jar(platform, version)
    shutil.copy(jar, d / 'server.jar')
    (d / 'build.txt').write_text(f'{platform} {version} build {build}\n')
    for world in ('world', 'world_nether', 'world_the_end'):
        shutil.rmtree(d / world, ignore_errors=True)
    plugins = d / 'plugins'
    for name in ('LPCF', 'LuckPermsChatFormatterFolia', 'LuckPerms', 'PlaceholderAPI'):
        shutil.rmtree(plugins / name, ignore_errors=True)
    plugins.mkdir(exist_ok=True)
    for old in plugins.glob('*.jar'):
        old.unlink()
    loader = 'folia' if platform == 'folia' else 'paper'
    shutil.copy(modrinth_jar('luckperms', version, loader), plugins)
    shutil.copy(modrinth_jar('placeholderapi', version, loader), plugins)
    shutil.copy(plugin_jar(), plugins)
    helper = ROOT / 'build' / 'qa' / 'LPCF-QA-Helper.jar'
    if not helper.exists():
        raise SystemExit(f'{helper} not found: run ./gradlew qaHelperJar first')
    # QA only: teleports through the Bukkit API like /home and /spawn plugins, and reports LPCF's displays.
    shutil.copy(helper, plugins)
    # The PlaceholderAPI "player" expansion, for the format-side placeholder check. Fetched once by
    # `papi ecloud download Player` on any test server and kept in the cache.
    expansion = CACHE / 'Expansion-player.jar'
    if expansion.exists():
        (plugins / 'PlaceholderAPI' / 'expansions').mkdir(parents=True, exist_ok=True)
        shutil.copy(expansion, plugins / 'PlaceholderAPI' / 'expansions')
    if legacy_config:
        legacy = plugins / 'LuckPermsChatFormatterFolia'
        legacy.mkdir()
        (legacy / 'config.yml').write_text(LEGACY_CONFIG)
    for name in ('usercache.json', 'ops.json', 'whitelist.json', 'banned-players.json'):
        (d / name).unlink(missing_ok=True)
    (d / 'eula.txt').write_text('eula=true\n')
    level_type = 'minecraft\\:normal' if normal_world else 'minecraft\\:flat'
    (d / 'server.properties').write_text(f'''enable-rcon=true
rcon.port=25575
rcon.password=qa
broadcast-rcon-to-ops=false
server-port={port}
online-mode=false
enforce-secure-profile=false
level-name=world
level-type={level_type}
generator-settings={{"layers"\\:[{{"block"\\:"minecraft\\:bedrock","height"\\:1}},{{"block"\\:"minecraft\\:dirt","height"\\:2}},{{"block"\\:"minecraft\\:grass_block","height"\\:1}}],"biome"\\:"minecraft\\:plains"}}
level-seed=7214950366214
generate-structures={'true' if normal_world else 'false'}
spawn-protection=0
max-tick-time=-1
view-distance=5
simulation-distance=5
difficulty=peaceful
gamemode=creative
spawn-monsters=false
spawn-npcs=false
spawn-animals=false
white-list=false
enforce-whitelist=false
allow-flight=true
motd=LPCF QA
''')
    print(f'prepared {d} ({platform} {version} build {build})')


def start(platform: str, version: str) -> None:
    d = server_dir(platform, version)
    log = open(d / 'server.log', 'w')
    proc = subprocess.Popen([java_for(version), '-Xms1G', '-Xmx2G', '-jar', 'server.jar', '--nogui'], cwd=d,
                            stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, start_new_session=True)
    (d / 'server.pid').write_text(str(proc.pid))
    print(proc.pid)


def stop(platform: str, version: str) -> None:
    d = server_dir(platform, version)
    pid_file = d / 'server.pid'
    if not pid_file.exists():
        return
    pid = int(pid_file.read_text())
    try:
        os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    pid_file.unlink()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('action', choices=['prepare', 'start', 'stop', 'dir'])
    ap.add_argument('platform', choices=['paper', 'folia', 'purpur'])
    ap.add_argument('version')
    ap.add_argument('--legacy-config', action='store_true')
    ap.add_argument('--normal-world', action='store_true')
    ap.add_argument('--port', type=int, default=25565)
    a = ap.parse_args()
    if a.action == 'prepare':
        prepare(a.platform, a.version, a.legacy_config, a.normal_world, a.port)
    elif a.action == 'start':
        start(a.platform, a.version)
    elif a.action == 'stop':
        stop(a.platform, a.version)
    else:
        print(server_dir(a.platform, a.version))
    sys.stdout.flush()


if __name__ == '__main__':
    main()
