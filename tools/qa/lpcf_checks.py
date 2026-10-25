#!/usr/bin/env python3
"""End-to-end LPCF checks against a running test server with a real client.

Expects: a server from tools/qa/server.py (prepared with --legacy-config) and the client "Builder" joined
(tools/qa/mcclient.py). The script launches a second client, "Alex", itself, so it can check join messages and what
other players see. Everything players say is typed into the real clients; chat is read from the clients' logs,
entities through RCON.

  python3 tools/qa/lpcf_checks.py --platform paper --version 26.3 --shots /tmp/lpcf-qa/shots/paper-26.3

Prints one line per check and, last, "<n> checks, <m> failures".
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import Session  # noqa: E402

HERE = Path(__file__).resolve().parent
QA_HOME = Path(os.environ.get('QA_HOME', '/tmp/lpcf-qa'))
VERSION = (HERE.parents[1] / 'VERSION').read_text().strip()


def plain(snbt: str) -> str:
    """The visible text of a text display's 'text' data (SNBT on 1.21.5+, a JSON string before): every string
    that is a 'text' value or a bare list element, in order."""
    data = snbt.strip()
    if data.startswith("'{") or data.startswith("'\""):
        import json

        def walk(node):
            if isinstance(node, str):
                return node
            if isinstance(node, list):
                return ''.join(walk(n) for n in node)
            return node.get('text', '') + ''.join(walk(n) for n in node.get('extra', []))
        try:
            return walk(json.loads(data[1:-1].replace("\\'", "'")))
        except ValueError:
            pass
    out = []
    for key, value in re.findall(r'(?:("?\w+"?): ?)?"((?:[^"\\]|\\.)*)"(?!\s*:)', snbt):
        if not key or key.strip('"') == 'text':
            out.append(value.replace('\\"', '"'))
    return ''.join(out) if out else snbt.strip().strip("'")


def parse_info(text: str) -> dict:
    info = {k: v for k, v in re.findall(r'(\w+)=<<(.*?)>>', text)}
    rest = re.sub(r'\w+=<<.*?>>', '', text)
    info.update({k: v for k, v in re.findall(r'(\w+)=(\S+)', rest)})
    return info


def count(out: str) -> int:
    match = re.search(r'[Cc]ount: (\d+)', out)
    return int(match.group(1)) if match else 0


class ChatLog:
    """New [CHAT] lines of a client log since the last mark."""

    def __init__(self, path: Path):
        self.path = path
        self.offset = 0

    def mark(self):
        self.offset = self.path.stat().st_size if self.path.exists() else 0

    def lines(self):
        if not self.path.exists():
            return []
        with open(self.path, 'rb') as f:
            f.seek(self.offset)
            text = f.read().decode('utf-8', 'replace')
        return [line.split('[CHAT] ', 1)[1] for line in text.splitlines() if '[CHAT] ' in line]

    def wait(self, pattern, timeout=8.0):
        regex = re.compile(pattern)
        deadline = time.time() + timeout
        while time.time() < deadline:
            for line in self.lines():
                if regex.search(line):
                    return line
            time.sleep(0.4)
        return None


class Checks:
    def __init__(self, args):
        self.args = args
        self.platform, self.version = args.platform, args.version
        self.folia = args.platform == 'folia'
        self.server_dir = QA_HOME / 'servers' / f'{self.platform}-{self.version}'
        self.server_log = self.server_dir / 'server.log'
        self.shots = Path(args.shots)
        self.results = []
        self.s = Session(display=os.environ.get('DISPLAY', ':1'))
        self.builder = ChatLog(Path(args.builder_log))
        self.alex = ChatLog(Path(args.alex_log))

    # -- helpers ------------------------------------------------------------------------------------------------
    def check(self, group, name, ok, detail=''):
        self.results.append(ok)
        print(f"{'PASS' if ok else 'FAIL'} [{group}] {name}{': ' + str(detail) if detail else ''}", flush=True)
        return ok

    def cmd(self, *commands):
        return self.s.cmd(*commands)

    def say(self, who, text, pause=0.6):
        self.s.use(who).chat(text)
        time.sleep(pause)

    def mark(self):
        self.builder.mark()
        self.alex.mark()

    def info(self, who='Builder', timeout=8.0):
        """What the QA helper reports about the player and their LPCF displays (see src/qa)."""
        start = self.server_log.stat().st_size
        self.cmd(f'qainfo {who}')
        deadline = time.time() + timeout
        while time.time() < deadline:
            with open(self.server_log, 'rb') as f:
                f.seek(start)
                text = f.read().decode('utf-8', 'replace')
            for line in text.splitlines():
                if f'QA-INFO {who} ' in line:
                    return parse_info(line.split(f'QA-INFO {who} ', 1)[1])
            time.sleep(0.3)
        return {}

    def passenger(self, tag, what='text'):
        """The plain text (or see-through flag) of Builder's display of this kind, or None when there is none."""
        kind = 'bubble' if tag == 'lpcf-bubble' else 'nametag'
        info = self.info()
        if kind not in info:
            return None
        return info.get(kind + 'SeeThrough') if what == 'see_through' else info[kind]

    def placed(self, info, kind='nametag'):
        """Following (Paper, Purpur): at the player's head, nothing riding. Riding (Folia): a passenger."""
        if kind not in info:
            return False
        if self.folia:
            return info.get(kind + 'Riding') == 'true'
        dxz = float(info.get(kind + 'Offset', '9/9').split('/')[0])
        return info.get('passengers') == '0' and dxz < 0.6

    def leftover_displays(self, expected=None):
        """LPCF displays near Builder that belong to nobody nearby: everything but Builder's and Alex's own."""
        builder, alex = self.info('Builder'), self.info('Alex')
        own = sum(('nametag' in i) + ('bubble' in i) for i in (builder, alex))
        near = int(builder.get('near', '-1'))
        return near - own, near

    def wait_passenger(self, tag, predicate=lambda out: out is not None, timeout=6.0, what='text'):
        deadline = time.time() + timeout
        out = None
        while time.time() < deadline:
            out = self.passenger(tag, what)
            if predicate(out):
                return True, out
            time.sleep(0.5)
        return False, out

    def shot(self, who, name):
        try:
            self.s.use(who).shot(self.shots / f'{name}.png')
        except Exception as ex:  # screenshots are evidence, not checks
            print(f'(screenshot {name} failed: {ex})')

    def edit_config(self, edits):
        """Sets keys (dotted paths) in plugins/LPCF/config.yml, like a server owner editing the file."""
        import yaml
        path = self.server_dir / 'plugins' / 'LPCF' / 'config.yml'
        data = yaml.safe_load(path.read_text()) or {}
        for key, value in edits.items():
            node = data
            parts = key.split('.')
            for part in parts[:-1]:
                if not isinstance(node.get(part), dict):
                    node[part] = {}
                node = node[part]
            node[parts[-1]] = value
        path.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=1000))

    def gamerule(self, new, old, value):
        out = self.cmd(f'gamerule {new} {value}')
        if 'Unknown' in out or 'Incorrect' in out or 'Expected' in out or not out:
            out = self.cmd(f'gamerule {old} {value}')
        return out

    # -- the checks ---------------------------------------------------------------------------------------------
    def run(self):
        self.startup()
        self.setup()
        self.join_messages()
        self.chat()
        self.nametags()
        self.bubbles()
        self.styling()
        self.mentions_and_items()
        self.preview_json()
        self.debug_command()
        self.lifecycle()
        self.live_update()
        self.world_format()
        self.tab_and_shots()
        self.orphans()
        self.quit_message()
        self.log()
        failures = self.results.count(False)
        print(f'{len(self.results)} checks, {failures} failures')
        return failures

    def startup(self):
        log = self.server_log.read_text(errors='replace')
        self.check('startup', 'LPCF enabled', f'LPCF {VERSION} enabled on' in log)
        mode = 'text displays above the vanilla name (Folia has no scoreboard teams), riding the player' if self.folia \
            else 'Nametags: text displays, following the player'
        self.check('startup', 'nametag mode logged', mode in log, mode)
        self.check('migration', '1.x data folder moved',
                   'Moved plugins/LuckPermsChatFormatterFolia to plugins/LPCF' in log
                   and not (self.server_dir / 'plugins' / 'LuckPermsChatFormatterFolia').exists())
        import yaml
        config = yaml.safe_load((self.server_dir / 'plugins' / 'LPCF' / 'config.yml').read_text())
        self.check('migration', 'config upgraded to version 2', config.get('config-version') == 2
                   and (self.server_dir / 'plugins' / 'LPCF' / 'config-1.x-backup.yml').exists())
        self.check('migration', 'old values kept, new sections added',
                   config.get('chat-format') == '{prefix}{name}<dark_gray> »<reset> {message}'
                   and config.get('update-check', {}).get('enabled') is False
                   and config.get('nametags', {}).get('lines') == ['{prefix}{name}']
                   and config.get('join-quit', {}).get('enabled') is False and 'bubbles' in config)

    def setup(self):
        if not (self.server_dir / 'plugins' / 'PlaceholderAPI' / 'expansions' / 'Expansion-player.jar').exists():
            self.cmd('papi ecloud download Player')
            time.sleep(6)
        self.cmd('papi reload')
        for c in ['lp creategroup admin', 'lp group admin setweight 100',
                  'lp group admin meta setprefix 100 "<gradient:#ff5f6d:#ffc371>[Admin]</gradient> "',
                  'lp user Builder parent set admin', 'op Builder', 'time set noon', 'weather clear',
                  'lp user Builder permission set lpcf.colorcodes true']:
            self.cmd(c)
            time.sleep(0.8)
        self.gamerule('immediate_respawn', 'doImmediateRespawn', 'true')
        self.gamerule('advance_time', 'doDaylightCycle', 'false')
        self.gamerule('show_death_messages', 'showDeathMessages', 'false')
        # Upgraded configs keep join/quit messages off; turn them on to check them.
        self.edit_config({'join-quit.enabled': True, 'tab.header-footer.enabled': True})
        out = self.cmd('lpcf reload')
        self.check('setup', '/lpcf reload over RCON', 'reloaded' in out, out.strip()[:80])
        self.cmd('tp Builder 0.5 -60 0.5 0 0')
        time.sleep(2)

    def join_messages(self):
        self.mark()
        log = QA_HOME / 'clients' / 'alex.log'
        subprocess.run([sys.executable, str(HERE / 'mcclient.py'), 'launch', self.args.client_version, 'Alex',
                        f'127.0.0.1:{self.args.port}', '--log', self.args.alex_log], check=True,
                       stdout=subprocess.DEVNULL)
        joined = subprocess.run([str(HERE / 'waitfor.sh'), str(self.server_log), 'Alex joined the game|Alex\\[', '600'])
        self.check('join', 'second client joined', joined.returncode == 0)
        line = self.builder.wait(r'Welcome Alex to the server for the first time', 30)
        self.check('join', 'first-join message', line is not None, line)
        time.sleep(6)
        self.cmd('tp Alex 0.5 -60 4.5 180 0', 'tp Builder 0.5 -60 0.5 0 0', 'gamemode creative Alex')
        time.sleep(2)

    def chat(self):
        self.mark()
        self.say('Builder', 'hello world')
        line = self.alex.wait(r'Builder » hello world')
        self.check('chat', 'LuckPerms prefix and the 1.x format', line == '[Admin] Builder » hello world'
                   or (line or '').endswith('[Admin] Builder » hello world'), line)
        self.mark()
        self.say('Alex', '<red>x %player_name% {prefix} <click:run_command:/op Alex>y')
        line = self.builder.wait(r'Alex » ')
        self.check('chat', 'player text is literal: no tags, placeholders or format tokens',
                   line is not None and line.endswith('Alex » <red>x %player_name% {prefix} <click:run_command:/op Alex>y'), line)

    def nametags(self):
        ok, out = self.wait_passenger('lpcf-nametag', lambda o: o is not None and 'Admin' in o)
        info = self.info()
        if self.folia:
            # No teams on Folia: the display carries the prefix line above the vanilla name.
            self.check('nametag', 'nametag shows the prefix line', ok and 'Builder' not in (out or ''), out)
            self.check('nametag', 'and rides the player (Folia)', self.placed(info), info)
        else:
            self.check('nametag', 'nametag shows prefix and name', ok and 'Builder' in (out or ''), out)
            self.check('nametag', 'and follows the head with nothing riding the player', self.placed(info), info)
            teams = self.cmd('team list')
            team = re.search(r'\[(L\d{4}[0-9a-f]+)', teams)
            members = self.cmd(f'team list {team.group(1)}') if team else ''
            self.check('nametag', 'vanilla name hidden through the player team', 'Builder' in members, members.strip()[:90])
        left, near = self.leftover_displays(2)
        self.check('nametag', 'no stray displays (one tag each for Builder and Alex)', left <= 0, f'{near} near Builder')

    def bubbles(self):
        self.say('Builder', 'bubble test from Builder', pause=0.2)
        ok, out = self.wait_passenger('lpcf-bubble', lambda o: o is not None and 'bubble test' in o, 4)
        self.check('bubble', 'chat bubble above the speaker', ok, out)
        time.sleep(0.5)
        self.shot('Alex', 'bubble')
        gone, out = self.wait_passenger('lpcf-bubble', lambda o: o is None, 10)
        self.check('bubble', 'bubble removed after its time', gone, out)
        self.mark()
        self.say('Builder', '/lpcf bubbles off')
        self.check('bubble', '/lpcf bubbles off answers', self.builder.wait('chat bubbles are off') is not None)
        self.say('Builder', 'no bubble please', pause=1.5)
        self.check('bubble', 'no bubble while turned off', self.passenger('lpcf-bubble') is None)
        self.mark()
        self.say('Builder', '/lpcf bubbles on')
        self.check('bubble', '/lpcf bubbles on answers', self.builder.wait('chat bubbles are on') is not None)

    def styling(self):
        self.mark()
        self.say('Builder', '<red>red</red> &aGreen <click:run_command:/stop>z')
        line = self.alex.wait(r'Builder » ')
        self.check('chat', 'lpcf.colorcodes: colours parsed, click tag refused',
                   line is not None and line.endswith('Builder » red Green <click:run_command:/stop>z'), line)
        self.mark()
        self.say('Alex', 'I <3 you :star:')
        line = self.builder.wait(r'Alex » I')
        self.check('chat', 'emoji replacements', line is not None and line.endswith('Alex » I ❤ you ★'), line)

    def mentions_and_items(self):
        self.mark()
        self.say('Alex', 'hi builder, look')
        line = self.builder.wait(r'Alex » hi')
        self.check('mention', 'bare name highlighted as @Builder', line is not None and 'hi @Builder, look' in line, line)
        # /item is disabled on Folia; /give fills the selected (empty) hotbar slot everywhere.
        self.cmd('clear Builder', 'give Builder minecraft:diamond_sword 1')
        time.sleep(0.5)
        self.mark()
        self.say('Builder', 'look at my [item]')
        line = self.alex.wait(r'look at my')
        self.check('item', '[ITEM] shows the held item', line is not None and line.endswith('look at my [Diamond Sword]'), line)

    def preview_json(self):
        text = 'visit https://nightbeam.dev/support and [item] @Alex'
        out = self.cmd(f'lpcf preview Builder {text}')
        if 'JSON:' not in out:
            # Folia answers from the player's region thread, after RCON returned: read it from the log.
            time.sleep(1.5)
            log = self.server_log.read_text(errors='replace')
            out = log[log.rfind('Preview (only you see this)'):] if 'Preview (only you see this)' in log else ''
        compact = out.replace(' ', '')
        self.check('preview', 'link is clickable', '"open_url"' in compact and 'https://nightbeam.dev/support' in compact)
        self.check('preview', 'name has a hover card and click-to-message',
                   '"show_text"' in compact and 'Rank:' in out and '"suggest_command"' in compact and '/msgBuilder' in compact)
        self.check('preview', '[ITEM] has the item hover', '"show_item"' in compact and 'diamond_sword' in compact)
        self.check('preview', 'mention formatted', '@Alex' in out and '"yellow"' in compact)

    def debug_command(self):
        self.mark()
        self.say('Builder', '/lpcf debug')
        line = self.builder.wait(r'Nametag: ')
        parts = (['text display above the vanilla name riding the player', 'shown: [Admin]'] if self.folia
                 else ['text display (vanilla name hidden) following the player', 'shown: [Admin] Builder'])
        self.check('debug', '/lpcf debug explains the nametag', line is not None and all(p in line for p in parts), line)
        fmt = self.builder.wait(r'Chat format: ')
        self.check('debug', '/lpcf debug names the chat format', fmt is not None and 'chat-format' in fmt, fmt)

    def lifecycle(self):
        self.cmd('tp Builder 3000.5 -60 3000.5')
        time.sleep(1)
        ok, out = self.wait_passenger('lpcf-nametag', timeout=10)
        self.check('lifecycle', 'nametag follows a teleport 3000 blocks away', ok, out)
        self.cmd('tp Builder 0.5 -60 0.5 0 0')
        ok, _ = self.wait_passenger('lpcf-nametag', timeout=10)
        self.check('lifecycle', 'and back', ok)

        # Plugins (/home, /spawn, /tpa) teleport through the Bukkit API, not the vanilla command.
        for label, world, x, y, z in (('far away', 'world', 2500.5, -60, -2500.5), ('to the nether', 'world_nether', 8.5, 120, 8.5),
                                      ('back', 'world', 0.5, -60, 0.5)):
            mark = len(self.server_log.read_text(errors='replace'))
            self.cmd(f'qatp Builder {world} {x} {y} {z}')
            deadline, result = time.time() + 10, None
            while time.time() < deadline and result is None:
                found = re.search(r'QA-TP Builder result=(\w+)', self.server_log.read_text(errors='replace')[mark:])
                result = found.group(1) if found else None
                time.sleep(0.5)
            ok, out = self.wait_passenger('lpcf-nametag', timeout=10)
            info = self.info()
            self.check('lifecycle', f'plugin (Bukkit API) teleport {label}, nametag still there',
                       result == 'true' and ok and info.get('world') == world and self.placed(info),
                       f'teleport={result}, world={info.get("world")}, nametag={out}')
        self.cmd('tp Builder 0.5 -60 0.5 0 0')

        self.cmd('effect give Builder minecraft:invisibility 60 0 true')
        hidden, _ = self.wait_passenger('lpcf-nametag', lambda o: o is None, 4)
        self.check('lifecycle', 'hidden while invisible', hidden)
        self.cmd('effect clear Builder')
        ok, _ = self.wait_passenger('lpcf-nametag', timeout=4)
        self.check('lifecycle', 'back when visible again', ok)

        self.cmd('gamemode spectator Builder')
        hidden, _ = self.wait_passenger('lpcf-nametag', lambda o: o is None, 4)
        self.check('lifecycle', 'hidden in spectator mode', hidden)
        self.cmd('gamemode creative Builder')
        ok, _ = self.wait_passenger('lpcf-nametag', timeout=4)
        self.check('lifecycle', 'back in creative', ok)

        self.cmd('kill Builder')
        time.sleep(2)
        self.cmd('tp Builder 0.5 -60 0.5 0 0')
        ok, _ = self.wait_passenger('lpcf-nametag', timeout=10)
        self.check('lifecycle', 'back after death and respawn', ok)

        self.s.use('Builder').focus()
        self.s.xdo('keydown', 'shift')
        sneak, out = self.wait_passenger('lpcf-nametag', lambda o: o == 'false', 4, what='see_through')
        self.s.xdo('keyup', 'shift')
        self.check('lifecycle', 'sneaking: dimmed and not seen through walls', sneak, out)
        self.cmd('tp Alex 0.5 -60 4.5 180 0')
        time.sleep(2)
        left, near = self.leftover_displays(2)
        self.check('lifecycle', 'no leftover displays after all that', left <= 0, f'{near} near Builder')

    def live_update(self):
        self.cmd('lp group admin meta removeprefix 100')
        time.sleep(0.8)
        self.cmd('lp group admin meta setprefix 100 "&b[Staff] "')
        ok, out = self.wait_passenger('lpcf-nametag', lambda o: o is not None and 'Staff' in o, 8)
        self.check('live', 'nametag follows a LuckPerms prefix change', ok, out)
        self.mark()
        self.say('Builder', 'new rank')
        line = self.alex.wait(r'new rank')
        self.check('live', 'chat follows it too', line is not None and line.endswith('[Staff] Builder » new rank'), line)

    def world_format(self):
        self.edit_config({'world-formats.world_nether': '<red>[N:%player_name%]</red> {name} » {message}'})
        out = self.cmd('lpcf reload')
        self.check('world', 'reload after editing world-formats', 'reloaded' in out)
        self.cmd('execute in minecraft:the_nether run tp Builder 0.5 120 0.5')
        time.sleep(3)
        ok, out = self.wait_passenger('lpcf-nametag', timeout=10)
        info = self.info()
        self.check('world', 'vanilla /tp to the nether, nametag follows', ok and info.get('world') == 'world_nether'
                   and self.placed(info), f'world={info.get("world")}, nametag={out}')
        self.mark()
        self.say('Builder', 'hot here')
        line = self.alex.wait(r'hot here')
        self.check('world', 'world format with a PlaceholderAPI placeholder',
                   line is not None and line.endswith('[N:Builder] Builder » hot here'), line)
        self.cmd('execute in minecraft:overworld run tp Builder 0.5 -60 0.5 0 0')
        ok, _ = self.wait_passenger('lpcf-nametag', timeout=10)
        self.check('world', 'and back to the overworld', ok)

    def tab_and_shots(self):
        self.cmd('tp Alex 0.5 -60 4.5 180 0', 'tp Builder 0.5 -60 0.5 0 0')
        time.sleep(3)
        self.shot('Alex', 'nametag')
        self.s.use('Alex').focus()
        self.s.xdo('keydown', 'Tab')
        time.sleep(1)
        self.shot('Alex', 'tablist')
        self.s.xdo('keyup', 'Tab')

    def orphans(self):
        # A tagged display saved in a chunk (as after a crash) is removed when the chunk loads.
        self.cmd('forceload add 4000 4000')
        time.sleep(1)
        self.cmd('summon minecraft:text_display 4000 -60 4000 {Tags:["lpcf-tag","qa-orphan"],text:\'"orphan"\'}')
        present = self.cmd('execute if entity @e[tag=qa-orphan]')
        self.cmd('forceload remove 4000 4000')
        time.sleep(15)
        self.cmd('tp Builder 4000.5 -60 4002.5')
        time.sleep(5)
        left = self.cmd('execute if entity @e[tag=qa-orphan]')
        self.check('orphan', 'left-behind display removed when its chunk loads',
                   'passed' in present.lower() and 'failed' in left.lower(), f'{present.strip()} / {left.strip()}')
        self.cmd('tp Builder 0.5 -60 0.5 0 0')

    def quit_message(self):
        self.mark()
        subprocess.run([sys.executable, str(HERE / 'mcclient.py'), 'stop', 'Alex'])
        line = self.builder.wait(r'\[-\] .*Alex', 30)
        self.check('join', 'quit message', line is not None, line)

    def log(self):
        log = self.server_log.read_text(errors='replace')
        errors = [l for l in log.splitlines() if re.search(r'\bERROR\]|/ERROR\]', l)]
        lpcf = [l for l in log.splitlines() if 'LPCF' in l and re.search(r'WARN|ERROR|Exception', l)]
        traces = [l for l in log.splitlines() if 'io.nightbeam' in l]
        self.check('log', 'no errors in the server log', not errors, errors[:3])
        self.check('log', 'no LPCF warnings or stack traces', not lpcf and not traces, (lpcf + traces)[:3])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--platform', required=True)
    ap.add_argument('--version', required=True)
    ap.add_argument('--client-version')
    ap.add_argument('--port', type=int, default=25565)
    ap.add_argument('--builder-log', required=True)
    ap.add_argument('--alex-log', required=True)
    ap.add_argument('--shots', required=True)
    args = ap.parse_args()
    args.client_version = args.client_version or args.version
    sys.exit(1 if Checks(args).run() else 0)


if __name__ == '__main__':
    main()
