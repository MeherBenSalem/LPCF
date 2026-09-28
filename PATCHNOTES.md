# LPCF patch notes

## 1.2.4
- Add Minecraft / Paper **26.3** support.
- Compile against Paper API `26.3.build.49-alpha` (JDK 25). Bytecode target remains Java 25.
- Keep `api-version: 1.21` so currently supported older servers still load the plugin.
- Keep Folia support (`folia-supported: true`). There is no Folia 26.3; Folia users stay on 26.2.
- Exercise `/lpcf reload` and help/usage on real servers (see PR test notes).
- Tested on Paper **26.3 build 133** (ALPHA).

## 1.2.3
- Replace broken **1.2.2** NMS nametag path (`PlayerTeam.setColor(Optional)`) that crashes on Folia **26.1.2**.
- Ship the Bukkit scoreboard nametag implementation (compatible with 26.1–26.2).
- Refresh display name / nametag on `PlayerChangedWorldEvent` after teleports between RegionVerse worlds.
