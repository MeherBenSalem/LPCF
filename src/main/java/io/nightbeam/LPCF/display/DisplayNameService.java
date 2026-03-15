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

    private final LuckPermsChatFormatterFolia plugin;
    private final MiniMessage miniMessage;

    public DisplayNameService(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void updateDisplayName(Player player) {
        PluginConfig config = plugin.pluginConfig();
        CachedMetaData metaData = plugin.luckPerms().getPlayerAdapter(Player.class).getMetaData(player);

        String format = config.nametagFormat();

        String resolved = MiniMessageUtil.normalize(format)
                .replace("{prefix}", MiniMessageUtil.normalize(LuckPermsUtil.prefix(metaData)))
                .replace("{suffix}", MiniMessageUtil.normalize(LuckPermsUtil.suffix(metaData)))
                .replace("{prefixes}", MiniMessageUtil.normalize(LuckPermsUtil.joinedPrefixes(metaData)))
                .replace("{suffixes}", MiniMessageUtil.normalize(LuckPermsUtil.joinedSuffixes(metaData)))
                .replace("{world}", player.getWorld().getName())
                .replace("{name}", player.getName())
                .replace("{username-color}", MiniMessageUtil.normalize(LuckPermsUtil.metaValue(metaData, "username-color")));

        if (plugin.hasPlaceholderApi()) {
            resolved = MiniMessageUtil.normalize(PlaceholderAPI.setPlaceholders(player, resolved));
        }

        Component displayName = miniMessage.deserialize(resolved);
        // REMOVED player.displayName(displayName) to fix the double prefix in chat
        player.playerListName(displayName);
        
        Component prefixComponent = miniMessage.deserialize(MiniMessageUtil.normalize(LuckPermsUtil.prefix(metaData)));
        Component suffixComponent = miniMessage.deserialize(MiniMessageUtil.normalize(LuckPermsUtil.suffix(metaData)));
        plugin.nametagManager().setNametag(player.getName(), prefixComponent, suffixComponent, LuckPermsUtil.sortPriority(metaData));
    }

    public void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateDisplayName(player);
        }
    }
}
