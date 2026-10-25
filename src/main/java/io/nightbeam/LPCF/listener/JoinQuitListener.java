package io.nightbeam.LPCF.listener;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.util.LuckPermsUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.logging.Level;

/** Per-group join, first-join and quit messages ('join-quit'). */
public final class JoinQuitListener implements Listener {

    private final LpcfPlugin plugin;

    public JoinQuitListener(LpcfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        PluginConfig config = plugin.pluginConfig();
        if (!config.joinQuitEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        try {
            String format = pick(player, config.groupJoinFormats(), config.joinFormat());
            if (!player.hasPlayedBefore() && config.firstJoinFormat() != null && !config.firstJoinFormat().isEmpty()) {
                format = config.firstJoinFormat();
            }
            if (format != null && !format.isEmpty()) {
                event.joinMessage(plugin.formatter().render(format, player));
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not format the join message of " + player.getName(), ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        PluginConfig config = plugin.pluginConfig();
        if (!config.joinQuitEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        try {
            String format = pick(player, config.groupQuitFormats(), config.quitFormat());
            if (format != null && !format.isEmpty()) {
                event.quitMessage(plugin.formatter().render(format, player));
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not format the quit message of " + player.getName(), ex);
        }
    }

    private String pick(Player player, Map<String, String> byGroup, String fallback) {
        String group = LuckPermsUtil.primaryGroup(plugin.formatter().meta(player));
        String format = byGroup.get(group);
        return format != null ? format : fallback;
    }
}
