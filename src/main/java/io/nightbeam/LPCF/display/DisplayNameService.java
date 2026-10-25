package io.nightbeam.LPCF.display;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.chat.ChatFormatPlaceholders;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.format.PlayerFormatter;
import io.nightbeam.LPCF.util.LuckPermsUtil;
import io.nightbeam.LPCF.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * The tab-list name ('nametag-format') and the player's scoreboard team: the classic prefix/suffix nametag for
 * 'nametags.style: team', or a team that hides the vanilla name under the text-display nametag for 'auto'.
 */
public final class DisplayNameService {

    private static final String NAME_TAG = "<" + ChatFormatPlaceholders.NAME_TAG + ">";

    private final LpcfPlugin plugin;

    public DisplayNameService(LpcfPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateDisplayName(Player player) {
        PluginConfig config = plugin.pluginConfig();
        PlayerFormatter formatter = plugin.formatter();
        CachedMetaData metaData = formatter.meta(player);
        ChatFormatPlaceholders.Values values = formatter.values(player, metaData);

        String prepared = formatter.prepare(config.nametagFormat(), player, values);
        Component name = Component.text(player.getName());
        player.playerListName(formatter.parse(prepared, name, name, Component.empty(), TagResolver.empty()));

        plugin.tabListService().applyOrder(player);
        plugin.textTagService().markDirty(player);

        if (plugin.nametagManager() == null) {
            return;
        }
        try {
            int priority = LuckPermsUtil.sortPriority(metaData);
            switch (config.nametagStyle()) {
                case TEAM -> {
                    int nameIndex = prepared.indexOf(NAME_TAG);
                    String prefix = nameIndex < 0 ? prepared : prepared.substring(0, nameIndex);
                    String suffix = nameIndex < 0 ? "" : prepared.substring(nameIndex + NAME_TAG.length());
                    plugin.nametagManager().setNametag(
                            player.getName(),
                            formatter.parse(prefix, name, name, Component.empty(), TagResolver.empty()),
                            formatter.parse(suffix, name, name, Component.empty(), TagResolver.empty()),
                            priority
                    );
                }
                // The text display shows the name; the team hides the vanilla one and keeps the tab order.
                case AUTO -> plugin.nametagManager().setNametag(player.getName(), Component.empty(), Component.empty(),
                        priority, false, false, null);
                case OFF -> plugin.nametagManager().reset(player.getName());
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not apply scoreboard nametag for " + player.getName()
                            + "; tab-list display name was still updated.",
                    ex
            );
        }
    }

    public void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            SchedulerUtil.run(plugin, player, () -> {
                try {
                    updateDisplayName(player);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "Failed to refresh " + player.getName(), ex);
                }
            });
        }
    }
}
