package io.nightbeam.LPCF.listener;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import io.nightbeam.LPCF.chat.ChatFormatterService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class FoliaChatListener implements Listener {

    private final LuckPermsChatFormatterFolia plugin;
    private final ChatFormatterService formatterService;

    public FoliaChatListener(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
        this.formatterService = new ChatFormatterService(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) ->
                formatterService.formatChatMessage(source, sourceDisplayName, message));
    }
}
