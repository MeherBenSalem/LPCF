package io.nightbeam.LPCF.listener;

import io.nightbeam.LPCF.LpcfPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public final class PlayerJoinListener implements Listener {

    private final LpcfPlugin plugin;

    public PlayerJoinListener(LpcfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refreshDisplay(player);
        plugin.textTagService().start(player);
        plugin.tabListService().start(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // {world} may be part of the formats.
        refreshDisplay(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.textTagService().stop(player);
        plugin.tabListService().stop(player);
        if (plugin.nametagManager() == null) {
            return;
        }
        try {
            plugin.nametagManager().reset(player.getName());
        } catch (RuntimeException ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Failed to clear nametag state for " + player.getName()
                            + "; PlayerQuitEvent will continue.",
                    ex
            );
        }
    }

    private void refreshDisplay(Player player) {
        try {
            plugin.displayNameService().updateDisplayName(player);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Failed to refresh nametag/display for " + player.getName()
                            + "; PlayerJoinEvent/world-change will continue.",
                    ex
            );
        }
    }
}
