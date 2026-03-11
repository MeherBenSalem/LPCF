package io.nightbeam.LPCF.listener;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import io.nightbeam.LPCF.chat.ChatFormatterService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FoliaChatListener implements Listener {

    private final LuckPermsChatFormatterFolia plugin;
    private final ChatFormatterService formatterService;

    public FoliaChatListener(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
        this.formatterService = new ChatFormatterService(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID senderId = event.getPlayer().getUniqueId();
        Component message = event.message();
        Set<UUID> playerViewerIds = new HashSet<>();
        boolean hasConsoleViewer = false;

        for (var viewer : event.viewers()) {
            if (viewer instanceof Player playerViewer) {
                playerViewerIds.add(playerViewer.getUniqueId());
                continue;
            }
            if (viewer.equals(Bukkit.getConsoleSender())) {
                hasConsoleViewer = true;
            }
        }

        event.setCancelled(true);

        boolean finalHasConsoleViewer = hasConsoleViewer;

        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null) {
            return;
        }

        sender.getScheduler().run(plugin, scheduledTask -> {
            Component rendered = formatterService.formatChatMessage(sender, message);
            dispatchToViewers(playerViewerIds, finalHasConsoleViewer, rendered);
        }, null);
    }

    private void dispatchToViewers(Set<UUID> playerViewerIds, boolean hasConsoleViewer, Component rendered) {
        for (UUID viewerId : playerViewerIds) {
            Player targetPlayer = Bukkit.getPlayer(viewerId);
            if (targetPlayer == null) {
                continue;
            }
            targetPlayer.getScheduler().run(plugin, scheduledTask -> targetPlayer.sendMessage(rendered), null);
        }

        if (hasConsoleViewer) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> plugin.getServer().getConsoleSender().sendMessage(rendered));
        }
    }
}
