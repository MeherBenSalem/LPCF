# QA record: 2.0.0

`tools/qa/run_matrix.sh` (see [CONTRIBUTING](../CONTRIBUTING.md#testing-in-game)) on a fresh server and flat world per
build: the newest server build for that Minecraft version, LuckPerms 5.5.71, PlaceholderAPI 2.12.3 with its player
expansion, and two real vanilla game clients (Builder and Alex, offline mode) under Xvfb with software rendering.
Everything players say is typed into the clients; chat is read from the clients' logs; entities through RCON and a
QA-only helper plugin (`src/qa`, never shipped) that teleports through the Bukkit API and reports LPCF's displays,
because Folia disables `/data`. Every server starts from a **1.2.5 config in `plugins/LuckPermsChatFormatterFolia`**,
so every build also checks the rename and the config migration.

All builds passed on commit `43c2ac8`, with the jar that is now `releases/LPCF-2.0.0.jar`
(sha256 `6819ad341be5f54b9e6804953c5c0a2df2a85742c8cae1d4c21592b74e30ce04`, Java 17 bytecode). Unit tests:
56, 0 failures (`./gradlew test`).

| Server | Build | Java | Checks | Failures | Nametag mode |
|--------|-------|------|--------|----------|--------------|
| Paper 1.20.1 | 196 | 17 | 51 | 0 | follows the player (no client interpolation before 1.20.2) |
| Paper 1.20.4 | 499 | 17 | 51 | 0 | follows the player |
| Paper 1.21.1 | 133 | 21 | 51 | 0 | follows the player |
| Paper 1.21.11 | 132 | 21 | 51 | 0 | follows the player |
| Paper 26.1.2 | 74 | 25 | 51 | 0 | follows the player |
| Paper 26.2 | 135 | 25 | 51 | 0 | follows the player |
| Paper 26.3 | 223 | 25 | 51 | 0 | follows the player |
| Purpur 1.21.1 | 2329 | 21 | 51 | 0 | follows the player |
| Purpur 26.3 | 2654 | 25 | 51 | 0 | follows the player |
| Folia 26.2 | 7 | 25 | 50 | 0 | rides the player |
| Folia 26.3 | 5 | 25 | 50 | 0 | rides the player |

Folia runs one check fewer: it has no scoreboard teams, so "vanilla name hidden through the player team" does not
apply. There the riding tag hides the vanilla name instead: the matrix's own captures of Builder from Alex's client
on Folia 26.2 and 26.3 (`$QA_HOME/shots/folia-*/nametag.png`, HUD on) show only the LPCF tag, as does
`marketing/images/screenshots/folia.jpg`.

## What is checked on every build

| Group | Checks |
|-------|--------|
| startup | `LPCF 2.0.0 enabled`, and the nametag mode in the startup line |
| migration | `plugins/LuckPermsChatFormatterFolia` moved to `plugins/LPCF`; `config-version: 2` and `config-1.x-backup.yml`; the old values kept (chat format, update check off), `nametags.lines` taken from the old `nametag-format`, join/quit messages and the tab header left off on an upgrade, new sections added |
| setup | `/lpcf reload` over RCON (after turning join/quit messages and the tab header on in the file) |
| join | Alex's client joins; Builder sees the first-join message |
| chat | `[Admin] Builder » hello world` with the 1.x format and a gradient LuckPerms prefix; Alex's `<red>x %player_name% {prefix} <click:...>y` arrives exactly as typed; with `lpcf.colorcodes` Builder's `<red>`/`&a` are parsed and `<click:...>` stays text; `<3` and `:star:` become ❤ and ★ |
| nametag | Builder's display shows `[Admin] Builder`; on Paper/Purpur it sits at his head with **nothing riding him**, on Folia it rides him; his scoreboard team exists (vanilla name hidden, Paper/Purpur); no stray displays |
| bubble | A bubble with the message appears over Builder and is gone after its time; `/lpcf bubbles off` stops them, `on` brings them back |
| mention, item | Alex's "hi builder" shows "hi @Builder"; `[item]` shows `[Diamond Sword]` |
| preview | `/lpcf preview` JSON: the link has `open_url`, the name has a hover card (Rank: ...) and `suggest_command /msg Builder `, `[ITEM]` has `show_item`, the mention is formatted |
| debug | `/lpcf debug` typed in game names the nametag mode, what it shows, and the chat format in use |
| lifecycle | The tag follows a vanilla `/tp` 3000 blocks away and back; **Bukkit API teleports** (what /home, /spawn and /tpa plugins use) far away, into the nether and back **succeed** with the tag still in place; hidden while invisible, in spectator, and back after; back after death and respawn; dimmed and not see-through while sneaking (Builder holds shift in his client); no leftover displays |
| live | Changing the LuckPerms prefix updates the nametag and the chat within seconds |
| world | Editing `world-formats` + `/lpcf reload`; vanilla `/tp` into the nether keeps the tag; the nether format with a PlaceholderAPI placeholder (`[N:Builder] Builder » hot here`); back to the overworld |
| orphan | A display tagged `lpcf-tag` saved in an unloaded chunk (as after a crash) is removed when the chunk loads |
| quit | Builder sees Alex's quit message when Alex's client closes |
| log | No ERROR lines at all in the server log, and no LPCF warning or stack trace |

Not observable by the scripts, checked in the code and by screenshots: the mention sound and action bar, tab-list
header/footer and order (screenshot of the tab list on every build: `$QA_HOME/shots/<build>/tablist.png`), bubble
colours.

## Found and fixed by this QA

* **Riding displays broke teleport plugins.** The first design made the tag ride the player. The Bukkit-API
  teleport check showed Paper and Purpur then refuse to teleport the player: every `Player#teleport` on Purpur
  1.21.1, every cross-world one on 26.3 (and vanilla `/tp` into the nether on 1.21.1). On Paper and Purpur the tag now
  follows the player instead (moved every tick on the player's thread, client-interpolated from 1.20.2); on Folia,
  where API teleports with the rider succeed, it still rides.
* **Folia shows the full nametag.** The Folia capture showed that clients do not draw the vanilla name of an entity
  carrying a passenger, so the riding tag hides it. Folia now shows the same `nametags.lines` as Paper instead of a
  prefix line above the vanilla name.
* A config upgraded from 1.x did not receive the default emoji list (the migration skipped every entry of "user
  maps"); now only maps that already exist are left alone (unit test added).
* Blank lines in `name-hover.lines` were dropped from the hover card.
* The "teams unavailable" note on Folia was a warning although it is expected there; it is information now.
* Bubbles looked exactly like nametags; they have their own background (`bubbles.background`).
* QA tooling: `xdotool search` combines criteria with OR by default (keys went to the wrong client), 26.x client
  windows only carry `_NET_WM_NAME`, older (GLFW 3.3) ones no process id; Folia disables `/data` and `/item` and
  answers RCON for player-bound commands after RCON has returned (so `/lpcf preview` and `/lpcf debug` also log their
  answer when called over RCON on Folia).
