package io.nightbeam.LPCF.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        // Folia region/world switches can drop client team state; re-apply Bukkit scoreboard teams.
        refreshDisplay(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.nametagManager() != null) {
            plugin.nametagManager().reset(event.getPlayer().getName());
        }
    }

    private void refreshDisplay(Player player) {
        plugin.displayNameService().updateDisplayName(player);
        if (plugin.nametagManager() != null) {
            plugin.nametagManager().sendTeams(player);
        }
    }
}
