# LPCF 2.0: audit, market gaps and plan

This document records what LPCF 1.2.5 actually did (read from the code and the built jar, not the store page), what
server owners expect from a chat / nametag / tab formatter today, where 1.2.5 fell short, and what 2.0 changes. Every
2.0 claim below is implemented and checked in game, by `tools/qa/lpcf_checks.py` or, where a script cannot see it
(sounds, the tab list, colours), by the QA screenshots (see [QA-2.0.0](QA-2.0.0.md)).

## 1. Audit of 1.2.5

| Area | What the code did |
|------|-------------------|
| Chat | One `AsyncChatEvent` renderer. Format priority group > track > default. `{prefix}`, `{suffix}`, `{prefixes}`, `{suffixes}`, `{world}`, `{name}`, `{displayname}`, `{username-color}`, `{message-color}`, `{message}`. MiniMessage plus `&` / `&#RRGGBB` / `&x` codes. Solid. |
| Player text | The player's message was pasted into the format **as a string** and the whole line was then run through PlaceholderAPI and MiniMessage. With PlaceholderAPI installed any player could type `%some_placeholder%` and have the server expand it in public chat. Players without `lpcf.colorcodes` had every `<...>` removed from their message (so "<3" vanished) and `&` codes stripped. Players with it got *every* MiniMessage tag, including click, hover and insertion. |
| `[ITEM]` | Held item with a hover tooltip, item name from the enum (`diamond sword`). |
| Nametags | Tab-list name from `nametag-format`. Above-head prefix/suffix through Bukkit scoreboard teams on the main scoreboard: a team colour can only be one of the 16 named colours, so gradients and hex in the name were lost. On **Folia** team registration throws, so since 1.2.5 there were **no above-head nametags at all** on Folia (issue #1 and the 1.2.5 notes). |
| Tab list | Display name only. No header/footer. Order: the team name encodes the prefix weight, which only sorts where teams work (not Folia). |
| Commands | `/lpcf reload`. Nothing to see why a prefix did not show (the most common support question for this kind of plugin). |
| Config | No version key, no migration path. |
| Versions | **The jar is compiled for Java 25** (class file major 69) with `api-version: 1.21`. 1.20.1 servers (Java 17, API 1.20) and 1.21.1 servers (Java 21) cannot load it, although the README said "1.20.1–26.3". 1.2.3 was even listed for 1.20.1–1.21.11 on Modrinth. 1.2.4 and 1.2.5 were only usable on 26.x. |
| Name | The plugin is called `LuckPermsChatFormatterFolia` (data folder, jar, logs), the listing "LPCF - Chat Formatter". Neither says it does nametags, and "Folia" in the name suggests it is Folia-only. |
| Tooling | 6 test classes; no in-game QA; the publish workflow built on CI, needed repo variables or failed silently with `test -n`, and sent a fixed 26.x version list. |

## 2. What server owners expect

Gathered from the store pages, comment sections and issue trackers of the chat-formatting, nametag and tab plugins in
this space, plus our own issue tracker. Recurring expectations:

1. **One plugin for the rank everywhere.** The LuckPerms prefix should show in chat, above the head and in the tab
   list. Today that usually takes three plugins (chat formatter, nametag plugin, tab plugin), and the nametag and tab
   plugins fight over scoreboard teams.
2. **Nametags that look like the chat.** Team nametags cannot show gradients or hex colours on the name and are
   limited to one line. Newer nametag plugins use text displays for this, but they are separate plugins and few of
   them run on Folia.
3. **Folia support that is real**, not "loads without crashing" (our own issue #1).
4. **Interactive chat**: hover over a name for rank info, click to message, clickable links, `@mentions` with a sound,
   showing the held item. These are the features store pages lead with.
5. **Safe chat**: players must not be able to inject click events, hover text or placeholders. Colour permissions
   should be separate from "dangerous" tags.
6. **Fun touches** that players notice: chat bubbles above heads, emoji shortcuts like `<3` → ❤.
7. **Tab list polish**: header/footer, sorted by rank.
8. **Per-group join and quit messages**, a first-join welcome.
9. **Easy debugging**: "why is my prefix not showing?" is the most asked question on every formatter page.
10. **Every version on one jar** and configs that survive updates.

## 3. Gap table

| Expectation | 1.2.5 | 2.0 |
|-------------|-------|-----|
| Rich nametags above the head | team prefix/suffix, 16 colours, one line; none on Folia | **Text-display nametags**: full MiniMessage (gradients, hex), several lines, LuckPerms prefix/suffix/meta and PlaceholderAPI, on Paper, Purpur **and Folia** (vanilla name hidden everywhere); hidden when sneaking (optional), invisible, in spectator, vanished or dead; the owner never sees their own; teleport plugins keep working |
| Chat bubbles | none | **Chat bubbles**: public messages float above the speaker for a few seconds; per-player toggle `/lpcf bubbles` |
| Hover / click on names | none | Hover card (rank, world, anything from PlaceholderAPI) and click-to-message on the name in chat |
| Mentions | none | `@name` or a plain name of an online player is highlighted; the player hears a sound and gets an action-bar notice |
| Links | plain text | URLs become clickable (`lpcf.links`) |
| Emoji shortcuts | none | Configurable replacements (`<3` → ❤, `:shrug:` → ¯\\\_(ツ)\_/¯) |
| Safe chat | PlaceholderAPI ran on player text; `lpcf.colorcodes` allowed click/hover/insert | The message is built as its own component and never passes through PlaceholderAPI or the format parser; `lpcf.colorcodes` allows colours, decorations, gradients and rainbow only; players without it see their text literally ("<3" stays) |
| Tab list | name only | Header and footer with placeholders, sorted by LuckPerms weight: group weight through the player-list order on 1.21.2+ (Folia included), prefix weight through team names before that |
| Join / quit | vanilla | Per-group join, quit and first-join messages |
| Formats | group > track > default | group > track > **world** > default |
| Debugging | none | `/lpcf debug [player]` shows group, prefix, suffix, meta, which format applies and why, the nametag mode and integrations; `/lpcf preview <text>` shows your chat line to you only |
| Versions | Java 25 jar: 26.x only | **One Java 17 jar for 1.20.1–26.3** on Paper, Purpur and Folia (newer APIs used only when present) |
| Config | no migration | `config-version: 2`; 1.x configs are backed up and upgraded in place, old keys keep working |
| Name | `LuckPermsChatFormatterFolia` | **LPCF**, listed as "LPCF: Chat, Nametags & Bubbles" (see section 5) |
| Tests / QA | 6 test classes | JUnit for all Minecraft-free logic; an in-game QA script (real server, real client typing in chat) on every version and platform |

## 4. The plan as built

### The headline: nametags and bubbles from text displays

Scoreboard teams cannot carry a gradient name and do not exist on Folia. A vanilla `text_display` entity above the
player can show any component, is seen by every client without a mod or resource pack, and is created with the
normal Bukkit API on the player's own region thread, so it works on Folia.

* **One display per player**, lifted to where the vanilla nametag sits. It is not persistent (never saved) and
  carries the scoreboard tag `lpcf-tag`; any tagged display found when entities load is removed, so a crash cannot
  leave one behind.
* **How it keeps up depends on the server.** The first prototype made the display ride the player
  (`addPassenger`). QA showed that Paper and Purpur then **refuse to teleport the player through the API**: on 1.21.1
  every `Player#teleport` returned false, on 26.x every cross-world one did. That would break `/home`, `/spawn`,
  `/tpa` and warp plugins. So on Paper and Purpur nothing rides the player: the display is moved to the top of the
  head every tick on the player's thread, with client interpolation (`Display#setTeleportDuration`, 3 ticks, like
  player movement) from 1.20.2, and re-created instead of sliding when the player jumps more than 8 blocks or
  changes world. On Folia, a rider is the better choice: Folia teleports players together with their passengers
  (checked: API teleports far away and to the nether succeed with the display still riding), and a rider never has
  to cross a region border on its own.
* **A per-player loop** (the player's entity scheduler: every tick on Paper and Purpur, every 2 ticks on Folia; text
  refreshed every 10 ticks by default) re-creates the display after teleports, deaths and world changes, refreshes
  the text (LuckPerms, PlaceholderAPI), and hides it while the player is dead, invisible, in spectator mode or
  vanished (the `vanished` metadata vanish plugins set). Sneaking makes it dim and not see-through like the vanilla
  tag (and lower, as the head is lower), or hides it (`hide-when-sneaking`).
* **The vanilla name** is hidden with the player's team (`NAME_TAG_VISIBILITY: NEVER`) on Paper and Purpur. Folia
  has no teams, and the first plan was to show only a rank line (the prefix) above the vanilla name there. The
  marketing captures showed something better: clients never draw the name of an entity that carries a passenger, so
  the riding tag hides the vanilla name on Folia by itself, and Folia shows the same full tag as Paper.
  `lines-above-vanilla-name` remains only for a server that can do neither. `/lpcf debug` and the startup log say
  which mode is active.
* **The owner never sees their own** display (`Player#hideEntity`).
* **Bubbles** are a second display, kept up the same way, above the nametag showing the player's last public chat
  message (only when the chat event reaches every online player, so channel, staff and muted messages are never
  shown), on a light background with dark text (`bubbles.background`) so they read differently from nametags. Time on screen grows
  with the message length. `/lpcf bubbles` toggles them per player (stored on the player).
* `nametags.style: team` keeps the 1.x team nametags; `off` disables both.

### Chat pipeline

The format (admin-controlled) is resolved to a MiniMessage string: LuckPerms values, `{world}`, `{group}`, then
PlaceholderAPI. `{name}`, `{displayname}` and `{message}` become component placeholders, so the player's name and
text are inserted after parsing. The message component is built from the player's text: literal for most players,
safe styling tags for `lpcf.colorcodes`; then emoji replacements, links, mentions and `[ITEM]` are applied to it as
components. The name gets the hover card and click action.

### One jar for 1.20.1 to 26.3

The plugin is compiled against the Paper 1.20.1 API with `--release 17` and `api-version: 1.20`. Everything it uses
exists from 1.20.1 on (text displays, the region/entity schedulers that Paper shares with Folia, Adventure).
`Player#setPlayerListOrder` (1.21.2+) is looked up at runtime. Folia, Paper and Purpur use the same code path because
Paper implements the Folia schedulers too.

### Config migration

1.x keys are unchanged: `chat-format`, `nametag-format` (now the tab-list name and the classic team nametag),
`group-formats`, `track-formats`, `use-item-placeholder`, `reload-message`, `update-check`. On the first 2.0 start a
config without `config-version` is copied to `config-1.x-backup.yml`, new sections are added with their comments, and:
`nametags.lines` is set from the old `nametag-format` (so the head shows what the old team nametag showed, now with full
colours), and the tab header/footer and join/quit messages start disabled (they could clash with another plugin the
server already uses for that). New installs get everything enabled.

### Code

* `chat/`: `ChatFormatPlaceholders` (format resolution and placeholder substitution), `MessageText` (styling,
  replacements, links, mentions; pure), `ChatFormatterService` (components), `MentionNotifier`.
* `display/tag/`: `TagLayout` (lines, offsets, bubble timing and wrapping; pure) and `TextTagService` (the displays).
* `display/team/`: the 1.x team code, now also used to hide the vanilla name.
* `tab/TabListService`, `listener/JoinQuitListener`, `config/ConfigMigrator` (pure, tested), `command/LPCFCommand`.

### Out of scope for 2.0

* Chat channels, private messages, moderation and filters: other plugins do this well and LPCF stays a formatter.
* Proxy (Velocity / BungeeCord) networks.
* Per-viewer nametags (a different nametag for each viewer) and packet-level tricks: LPCF uses only the Bukkit API,
  which is why it can run on every version and on Folia without ProtocolLib.

## 5. Rename decision

**The plugin is renamed from `LuckPermsChatFormatterFolia` to `LPCF`, and listed as "LPCF: Chat, Nametags & Bubbles".**

* The short name and the `/lpcf` command, permissions and Modrinth project stay, so existing users, links and the
  update checker keep working and the 4,000+ existing downloads stay on the same page.
* The listing title gains the words people search for (chat, nametags, bubbles) and drops "Folia" from the internal
  name, which made the plugin look Folia-only.
* **Migration**: `plugin.yml` `name` becomes `LPCF`, so the data folder becomes `plugins/LPCF`. On load, if
  `plugins/LPCF` does not exist and `plugins/LuckPermsChatFormatterFolia` does, the old folder is moved to the new
  one (logged). If the old jar is still installed next to the new one, LPCF logs a warning naming the file to delete.
  Commands (`/lpcf`) and every permission (`lpcf.*`) are unchanged. The jar is now `LPCF-<version>.jar`.
