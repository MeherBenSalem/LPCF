# LPCF patch notes

Full notes per release are in [patchnotes/](patchnotes/).

## 2.0.0: Chat, Nametags & Bubbles
- Text-display nametags above heads (gradients, several lines), on Paper, Purpur **and Folia**.
- Chat bubbles, hover cards and click-to-message on names, mentions, clickable links, emoji shortcuts.
- Tab-list header/footer and sorting by LuckPerms weight; per-group join, first-join and quit messages; world formats.
- `/lpcf debug`, `/lpcf preview`, `/lpcf bubbles`.
- One Java 17 jar for 1.20.1 to 26.3 again (1.2.4 and 1.2.5 only loaded on 26.x).
- Fixed chat injection: player text is never parsed as a format or passed to PlaceholderAPI; `lpcf.colorcodes` no
  longer allows click/hover tags.
- Renamed to `LPCF` (data folder `plugins/LPCF`, moved automatically); config upgraded in place with a backup.
- See [patchnotes/2.0.0.md](patchnotes/2.0.0.md).

## 1.2.5
- Stop Folia **26.2** from failing `PlayerJoinEvent` with `UnsupportedOperationException` from `CraftScoreboard.registerNewTeam` (issue #1).
- Keep Bukkit scoreboard nametags on Paper and other servers where team registration still works.
- Always apply the formatted tab-list name first. If scoreboard teams cannot be registered, skip above-head nametag teams, log once, and let join continue.

## 1.2.4
- Add Minecraft / Paper **26.3** support.
- Compile against Paper API `26.3.build.49-alpha` (JDK 25). Bytecode target remains Java 25.
- Keep `api-version: 1.21` so currently supported older servers still load the plugin.
- Keep Folia support (`folia-supported: true`). There is no Folia 26.3; Folia users stay on 26.2.
- Exercise `/lpcf reload` and help/usage on real servers (see PR test notes).
- Tested on Paper **26.3 build 133** (ALPHA), Paper 26.2 build 129, Paper 26.1.2 build 74, and Folia 26.2 build 7 with LuckPerms 5.5.71 and PlaceholderAPI 2.12.3. Clean enable, `/lpcf reload`, and `/help lpcf` on all four.

## 1.2.3
- Replace broken **1.2.2** NMS nametag path (`PlayerTeam.setColor(Optional)`) that crashes on Folia **26.1.2**.
- Ship the Bukkit scoreboard nametag implementation (compatible with 26.1–26.2).
- Refresh display name / nametag on `PlayerChangedWorldEvent` after teleports between RegionVerse worlds.
