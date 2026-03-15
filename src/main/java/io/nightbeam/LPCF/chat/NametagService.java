package io.nightbeam.LPCF.chat;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.util.LuckPermsUtil;
import io.nightbeam.LPCF.util.MiniMessageUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

public final class NametagService {

    private static final String NAME_TOKEN = "__LPCF_NAMETAG_NAME__";
    private static final String TEAM_PREFIX = "lpcf";

    private final LuckPermsChatFormatterFolia plugin;
    private final MiniMessage miniMessage;

    public NametagService(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void refreshAllNametags() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNametag(player);
        }
    }

    public void updateNametag(Player player) {
        CachedMetaData metaData = plugin.luckPerms().getPlayerAdapter(Player.class).getMetaData(player);
        String resolved = resolveNametagFormat(player, metaData);

        int tokenIndex = resolved.indexOf(NAME_TOKEN);
        String prefixPart = tokenIndex >= 0 ? resolved.substring(0, tokenIndex) : resolved;
        String suffixPart = tokenIndex >= 0 ? resolved.substring(tokenIndex + NAME_TOKEN.length()) : "";

        Component prefix = miniMessage.deserialize(prefixPart);
        Component suffix = miniMessage.deserialize(suffixPart);

        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null
                ? null
                : Bukkit.getScoreboardManager().getMainScoreboard();

        if (scoreboard == null) {
            return;
        }

        Team team = scoreboard.getTeam(teamName(player.getUniqueId()));
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName(player.getUniqueId()));
        }

        String entryName = player.getName();
        if (!team.hasEntry(entryName)) {
            team.addEntry(entryName);
        }

        team.prefix(prefix);
        team.suffix(suffix);
    }

    public void clearNametag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null
                ? null
                : Bukkit.getScoreboardManager().getMainScoreboard();

        if (scoreboard == null) {
            return;
        }

        Team team = scoreboard.getTeam(teamName(player.getUniqueId()));
        if (team == null) {
            return;
        }

        String entryName = player.getName();
        if (team.hasEntry(entryName)) {
            team.removeEntry(entryName);
        }

        if (team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    private String resolveNametagFormat(Player player, CachedMetaData metaData) {
        PluginConfig config = plugin.pluginConfig();

        String resolved = MiniMessageUtil.normalize(config.nametagFormat())
                .replace("{prefix}", MiniMessageUtil.normalize(LuckPermsUtil.prefix(metaData)))
                .replace("{suffix}", MiniMessageUtil.normalize(LuckPermsUtil.suffix(metaData)))
                .replace("{prefixes}", MiniMessageUtil.normalize(LuckPermsUtil.joinedPrefixes(metaData)))
                .replace("{suffixes}", MiniMessageUtil.normalize(LuckPermsUtil.joinedSuffixes(metaData)))
                .replace("{world}", player.getWorld().getName())
                .replace("{username-color}", MiniMessageUtil.normalize(LuckPermsUtil.metaValue(metaData, "username-color")))
                .replace("{message-color}", MiniMessageUtil.normalize(LuckPermsUtil.metaValue(metaData, "message-color")))
                .replace("{message}", "")
                .replace("{displayname}", NAME_TOKEN)
                .replace("{name}", NAME_TOKEN);

        if (plugin.hasPlaceholderApi()) {
            resolved = MiniMessageUtil.normalize(PlaceholderAPI.setPlaceholders(player, resolved));
        }

        if (!resolved.contains(NAME_TOKEN)) {
            resolved = resolved + NAME_TOKEN;
        }

        return resolved;
    }

    private String teamName(UUID uniqueId) {
        String compact = uniqueId.toString().replace("-", "");
        return TEAM_PREFIX + compact.substring(0, 12);
    }
}
