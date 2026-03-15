package io.nightbeam.LPCF.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerJoinListener implements Listener {

    private final io.nightbeam.LPCF.LuckPermsChatFormatterFolia plugin;

    public PlayerJoinListener(io.nightbeam.LPCF.LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.displayNameService().updateDisplayName(event.getPlayer());
        if (plugin.nametagManager() != null) {
            plugin.nametagManager().sendTeams(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.nametagManager() != null) {
            plugin.nametagManager().reset(event.getPlayer().getName());
        }
    }
}
