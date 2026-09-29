package io.nightbeam.LPCF.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public final class PlayerJoinListener implements Listener {

    private final io.nightbeam.LPCF.LuckPermsChatFormatterFolia plugin;

    public PlayerJoinListener(io.nightbeam.LPCF.LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        refreshDisplay(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Folia region/world switches can drop client team state; re-apply nametags when possible.
        refreshDisplay(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.nametagManager() == null) {
            return;
        }
        try {
            plugin.nametagManager().reset(event.getPlayer().getName());
        } catch (RuntimeException ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Failed to clear nametag state for " + event.getPlayer().getName()
                            + "; PlayerQuitEvent will continue.",
                    ex
            );
        }
    }

    private void refreshDisplay(Player player) {
        try {
            plugin.displayNameService().updateDisplayName(player);
            if (plugin.nametagManager() != null) {
                plugin.nametagManager().sendTeams(player);
            }
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
