#!/usr/bin/env python3
"""Drives a running QA client (on an X display) together with its server (over RCON).

Used for the client checks and for the marketing screenshots: commands go to the server console, keys and mouse go
to the game window with xdotool, and captures are the X display's pixels (ImageMagick `import`).

  from client import Session
  s = Session(display=':1')
  s.cmd('time set noon'); s.chat('hello'); s.shot('out.png')

With two clients on the display, s.use('Alex') sends keys and mouse to Alex's window.
"""
from __future__ import annotations

import math
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rcon import SafeRcon  # noqa: E402

PLAYER = 'Builder'


class Session:
    def __init__(self, display=':1', port=25575):
        self.env = dict(os.environ, DISPLAY=display)
        self.rcon = SafeRcon(port)
        self.hud_hidden = False
        self.active = None

    # Server side
    def cmd(self, *commands):
        out = ''
        for c in commands:
            out = self.rcon.command(c)
        return out

    def tp(self, x, y, z, yaw=None, pitch=None, facing=None, eye=1.62):
        if facing:
            # `tp ... facing` does not stick on 26.3 clients, so aim with explicit angles.
            dx, dy, dz = facing[0] - x, facing[1] - (y + eye), facing[2] - z
            yaw = math.degrees(math.atan2(-dx, dz))
            pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        return self.cmd(f'tp {PLAYER} {x} {y} {z} {yaw or 0:.2f} {pitch or 0:.2f}')

    # Client side
    def xdo(self, *args):
        subprocess.run(['xdotool', *args], env=self.env, check=False)

    def window(self):
        if self.active:
            clients = Path(os.environ.get('QA_HOME', '/tmp/lpcf-qa')) / 'clients'
            pid_file = clients / f'{self.active}.pid'
            if pid_file.exists():
                # Newer clients tell X their process id. (Their title is only in _NET_WM_NAME, so no --name here.)
                out = subprocess.run(['xdotool', 'search', '--onlyvisible', '--pid', pid_file.read_text().strip()],
                                     env=self.env, capture_output=True, text=True).stdout.split()
                if out:
                    return out[-1]
            # Older clients (GLFW) do not: use the window mcclient.py saw appear when it launched this client.
            wid_file = clients / f'{self.active}.wid'
            if wid_file.exists():
                return wid_file.read_text().strip()
        out = subprocess.run(['xdotool', 'search', '--onlyvisible', '--name', 'Minecraft'], env=self.env,
                             capture_output=True, text=True).stdout.split()
        return out[-1] if out else None

    def use(self, username):
        """Selects which client (by player name, see mcclient.py) receives keys and mouse."""
        self.active = username
        return self

    def focus(self):
        w = self.window()
        if w:
            # Xvfb has no window manager, so focus the window directly (no _NET_ACTIVE_WINDOW). Older clients (GLFW
            # 3.3) also need to be on top with the pointer over them; the centre is where a grabbed cursor rests, so
            # the camera does not turn.
            if w != getattr(self, 'focused', None):
                self.xdo('windowraise', w)
                self.xdo('windowfocus', '--sync', w)
                self.xdo('mousemove', '--window', w, '960', '540')
                time.sleep(0.3)
                self.focused = w
            else:
                self.xdo('windowfocus', '--sync', w)
        return w

    def key(self, *keys, delay=0.15):
        self.focus()
        for k in keys:
            self.xdo('key', '--delay', '60', k)
            time.sleep(delay)

    def type(self, text):
        self.focus()
        self.xdo('type', '--delay', '40', text)

    def chat(self, text):
        """Runs a client chat command (e.g. a /command as the player)."""
        self.key('t')
        time.sleep(0.4)
        self.type(text)
        time.sleep(0.2)
        self.key('Return')

    def hold(self, button, seconds, sneak=False):
        self.focus()
        if sneak:
            self.xdo('keydown', 'shift')
        self.xdo('mousedown', str(button))
        time.sleep(seconds)
        self.xdo('mouseup', str(button))
        if sneak:
            self.xdo('keyup', 'shift')

    def click(self, button=1, x=None, y=None):
        self.focus()
        if x is not None:
            self.xdo('mousemove', str(x), str(y))
            time.sleep(0.1)
        self.xdo('click', str(button))

    def move(self, x, y):
        self.focus()
        self.xdo('mousemove', str(x), str(y))

    def hud(self, visible):
        if visible == self.hud_hidden:
            self.key('F1')
            self.hud_hidden = not visible

    def shot(self, path, crop=None):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        w = self.window()
        if w:
            # Several clients share the display: bring the chosen one to the front first.
            self.xdo('windowraise', w)
            self.focused = None
            time.sleep(0.5)
        subprocess.run(['import', '-window', 'root', str(path)], env=self.env, check=True)
        if crop:
            from PIL import Image
            Image.open(path).crop(crop).save(path)
        return path

    @staticmethod
    def wait(seconds):
        time.sleep(seconds)
