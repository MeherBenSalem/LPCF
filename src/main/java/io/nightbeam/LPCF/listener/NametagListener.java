package io.nightbeam.LPCF.listener;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class NametagListener implements Listener {

    private final LuckPermsChatFormatterFolia plugin;

    public NametagListener(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.nametagService().updateNametag(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.nametagService().updateNametag(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.nametagService().clearNametag(event.getPlayer());
    }
}
