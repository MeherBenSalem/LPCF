# LPCF 1.2.5 Patch Notes

## Fixes
- Stop Folia **26.2** from failing `PlayerJoinEvent` with `UnsupportedOperationException` from `CraftScoreboard.registerNewTeam`.
- Keep Bukkit scoreboard nametags on servers where team registration still works.
- Always apply the formatted tab-list name. If a team cannot be registered, skip above-head nametag teams instead of crashing join.

## Folia limitation
Folia does not implement Bukkit scoreboard team registration, so above-head nametag prefixes/suffixes cannot be applied through that API. Chat formatting and tab-list (`playerListName`) display names still work.

## Install
Replace `LuckPermsChatFormatterFolia-1.2.4.jar` with `LuckPermsChatFormatterFolia-1.2.5.jar` (or `LuckPermsChatFormatterFolia.jar` from the shadow build).
