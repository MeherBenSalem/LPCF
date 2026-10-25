#!/usr/bin/env python3
"""Takes the listing screenshots (marketing/images/screenshots/) on a running test server with three real clients.

Setup (see marketing/README.md): a Paper 26.3 server prepared with --normal-world, and the clients Builder, Alex and
Steve joined (tools/qa/mcclient.py). Steve is the camera. Every capture is one unedited frame of Steve's game window.

  python3 tools/qa/marketing_shots.py [hero nametag-lines chat tablist debug]

The Folia capture (folia.jpg) needs a Folia server and two clients: `python3 tools/qa/marketing_shots.py folia`.
"""
from __future__ import annotations

import os
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import Session  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'marketing' / 'images' / 'screenshots'
QA_HOME = Path(os.environ.get('QA_HOME', '/tmp/lpcf-qa'))

s = Session(display=os.environ.get('DISPLAY', ':1'))


def cmd(*commands, pause=0.6):
    for c in commands:
        s.cmd(c)
        time.sleep(pause)


def say(who, text, pause=0.8):
    s.use(who).chat(text)
    time.sleep(pause)


def save(name):
    """One frame of Steve's window, saved as JPEG."""
    png = OUT / f'{name}.png'
    s.use('Steve').shot(png)
    from PIL import Image
    Image.open(png).convert('RGB').save(OUT / f'{name}.jpg', quality=92)
    png.unlink()
    print('saved', name)


def server_dir():
    for d in sorted((QA_HOME / 'servers').iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
        if (d / 'server.pid').exists():
            return d
    raise SystemExit('no running QA server')


def set_config(edits):
    import yaml
    path = server_dir() / 'plugins' / 'LPCF' / 'config.yml'
    data = yaml.safe_load(path.read_text())
    for key, value in edits.items():
        node = data
        parts = key.split('.')
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = value
    path.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=1000))
    cmd('lpcf reload')


def ground(x, z):
    """Top block height at x, z: spreadplayers puts the marker player on the surface."""
    cmd(f'spreadplayers {x} {z} 0 1 false Steve', pause=1.5)
    out = s.cmd('data get entity Steve Pos[1]')
    match = re.search(r'(-?\d+(?:\.\d+)?)d', out)
    return float(match.group(1)) if match else 70.0


def setup():
    for c in ['lp creategroup owner', 'lp group owner setweight 300',
              'lp group owner meta setprefix 300 "<gradient:#ff7a59:#ffd166>[Owner]</gradient> "',
              'lp creategroup vip', 'lp group vip setweight 100', 'lp group vip meta setprefix 100 "<#4cc9f0>[VIP]</#4cc9f0> "',
              'lp user Builder parent set owner', 'lp user Alex parent set vip', 'lp user Builder permission set lpcf.colorcodes true',
              'op Steve', 'time set noon', 'weather clear', 'difficulty peaceful']:
        cmd(c, pause=0.8)
    for gamerule in ('advance_time false', 'doDaylightCycle false', 'advance_weather false', 'doWeatherCycle false'):
        s.cmd(f'gamerule {gamerule}')
    set_config({'tab.header-footer.enabled': True, 'join-quit.enabled': True})
    cmd('gamemode creative Builder', 'gamemode creative Alex', 'gamemode creative Steve')


def stage(x, z, steve_back=4.6):
    """Builder and Alex side by side facing south; Steve a few blocks south of them looking north."""
    y = ground(x, z)
    cmd(f'tp Builder {x - 0.8} {y} {z} 0 0', f'tp Alex {x + 0.8} {y} {z} 0 0',
        f'tp Steve {x} {y} {z + steve_back} 180 6', pause=1)
    return y


def hero(x, z):
    stage(x, z)
    s.use('Steve').hud(False)
    time.sleep(3)
    say('Builder', 'Welcome to the server!', pause=0.2)
    say('Alex', 'Thanks! Love the new tags <3', pause=1.2)
    save('hero')
    s.use('Steve').hud(True)


def nametag_lines(x, z):
    set_config({'nametags.lines': ['{prefix}{name}', '<#ff6b6b>❤ <white>%player_health_rounded%</white> <gray>· <#9b8cff>{world}']})
    stage(x, z, steve_back=3.6)
    s.use('Steve').hud(False)
    time.sleep(3)
    save('nametag-lines')
    s.use('Steve').hud(True)
    set_config({'nametags.lines': ['{prefix}{name}']})


def chat(x, z):
    stage(x, z)
    cmd('clear Alex', 'give Alex minecraft:diamond_sword 1')
    say('Builder', 'Welcome to the server!')
    say('Alex', 'hi steve, check my <3 new sword [item]')
    say('Builder', 'Rules and map: https://nightbeam.dev/support :star:')
    say('Steve', 'thanks @builder :check:')
    # Open Steve's chat and hover the name in Builder's first line.
    s.use('Steve').key('t')
    time.sleep(1)
    hover_name()
    save('chat')
    s.use('Steve').key('Escape')


def hover_name():
    # With guiScale 2 a chat line is 18 px tall; the newest line sits just above the input box.
    # Builder's welcome is the fourth line from the bottom; its name starts after the "[Owner] " prefix.
    s.use('Steve').move(110, 1080 - 34 - 18 * 4 - 9)
    time.sleep(1.2)


def tablist(x, z):
    stage(x, z)
    s.use('Steve').hud(True)
    s.use('Steve').focus()
    s.xdo('keydown', 'Tab')
    time.sleep(1.2)
    save('tablist')
    s.xdo('keyup', 'Tab')


def debug(x, z):
    stage(x, z)
    say('Alex', '<red>try %player_name% <click:run_command:/op Alex>me', pause=0.6)
    say('Steve', '/lpcf debug Builder', pause=1.5)
    s.use('Steve').key('t')
    time.sleep(1)
    save('debug')
    s.use('Steve').key('Escape')


def folia():
    """On a Folia server: Steve looks at Builder; the rank line floats above the vanilla name."""
    y = ground(0, 0)
    cmd(f'tp Builder -0.5 {y} 0.5 0 0', f'tp Steve -0.5 {y} 4.1 180 6')
    s.use('Steve').hud(False)
    time.sleep(3)
    say('Builder', 'Hello from Folia!', pause=1.0)
    save('folia')
    s.use('Steve').hud(True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    wanted = sys.argv[1:] or ['hero', 'nametag-lines', 'chat', 'tablist', 'debug']
    if wanted == ['folia']:
        folia()
        return
    setup()
    # The plains village of the showcase world (seed 7214950366214): /locate structure minecraft:village_plains.
    out = s.cmd('locate structure minecraft:village_plains')
    match = re.search(r'\[(-?\d+), ~?(-?\d+|~), (-?\d+)\]', out)
    x, z = (int(match.group(1)) + 6, int(match.group(3)) + 6) if match else (0, 0)
    scenes = {'hero': hero, 'nametag-lines': nametag_lines, 'chat': chat, 'tablist': tablist, 'debug': debug}
    for name in wanted:
        scenes[name](x, z)


if __name__ == '__main__':
    main()
