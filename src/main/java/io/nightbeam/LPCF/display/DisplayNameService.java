package io.nightbeam.LPCF.display;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.util.LuckPermsUtil;
import io.nightbeam.LPCF.util.MiniMessageUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;

public final class DisplayNameService {

    private static final String NAME_TOKEN = "__LPCF_NAMETAG_NAME__";

    private final LuckPermsChatFormatterFolia plugin;
    private final MiniMessage miniMessage;

    public DisplayNameService(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void updateDisplayName(Player player) {
        PluginConfig config = plugin.pluginConfig();
        CachedMetaData metaData = plugin.luckPerms().getPlayerAdapter(Player.class).getMetaData(player);

        String resolvedNametag = resolveNametagFormat(config.nametagFormat(), player, metaData);
        Component displayName = miniMessage.deserialize(resolvedNametag.replace(NAME_TOKEN, player.getName()));
        player.playerListName(displayName);

        NametagParts nametagParts = splitNametagParts(resolvedNametag, player.getName());
        plugin.nametagManager().setNametag(
                player.getName(),
                nametagParts.prefix(),
                nametagParts.suffix(),
                LuckPermsUtil.sortPriority(metaData)
        );
    }

    public void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            io.nightbeam.LPCF.util.SchedulerUtil.run(plugin, player, () -> updateDisplayName(player));
        }
    }

    private String resolveNametagFormat(String format, Player player, CachedMetaData metaData) {
        String resolved = MiniMessageUtil.normalize(format)
                .replace("{prefix}", MiniMessageUtil.normalize(LuckPermsUtil.prefix(metaData)))
                .replace("{suffix}", MiniMessageUtil.normalize(LuckPermsUtil.suffix(metaData)))
                .replace("{prefixes}", MiniMessageUtil.normalize(LuckPermsUtil.joinedPrefixes(metaData)))
                .replace("{suffixes}", MiniMessageUtil.normalize(LuckPermsUtil.joinedSuffixes(metaData)))
                .replace("{world}", player.getWorld().getName())
                .replace("{name}", NAME_TOKEN)
                .replace("{displayname}", NAME_TOKEN)
                .replace("{username-color}", MiniMessageUtil.normalize(LuckPermsUtil.metaValue(metaData, "username-color")));

        if (plugin.hasPlaceholderApi()) {
            resolved = MiniMessageUtil.normalize(PlaceholderAPI.setPlaceholders(player, resolved));
        }

        return resolved;
    }

    private NametagParts splitNametagParts(String resolvedNametag, String playerName) {
        int nameIndex = resolvedNametag.indexOf(NAME_TOKEN);
        if (nameIndex < 0) {
            return new NametagParts(miniMessage.deserialize(resolvedNametag), Component.empty());
        }

        String prefix = resolvedNametag.substring(0, nameIndex).replace(NAME_TOKEN, playerName);
        String suffix = resolvedNametag.substring(nameIndex + NAME_TOKEN.length()).replace(NAME_TOKEN, playerName);
        return new NametagParts(miniMessage.deserialize(prefix), miniMessage.deserialize(suffix));
    }

    private record NametagParts(Component prefix, Component suffix) {
    }
}
