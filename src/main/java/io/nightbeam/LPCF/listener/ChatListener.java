package io.nightbeam.LPCF.listener;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.chat.ChatFormatterService;
import io.nightbeam.LPCF.chat.MessageComponents;
import io.nightbeam.LPCF.util.SchedulerUtil;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Formats chat (HIGHEST, so filters and mute plugins run first) and, once the message is really sent (MONITOR),
 * shows the speaker's bubble and pings mentioned players.
 */
public final class ChatListener implements Listener {

    private final LpcfPlugin plugin;
    private final ChatFormatterService formatterService;

    public ChatListener(LpcfPlugin plugin) {
        this.plugin = plugin;
        this.formatterService = plugin.chatFormatter();
    }

    /** Our renderer, so the MONITOR handler can tell whether another plugin replaced it. */
    private record LpcfRenderer(ChatFormatterService.Result result) implements ChatRenderer {
        @Override
        public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
            return result.line();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        try {
            ChatFormatterService.Result result = formatterService.format(player, player.displayName(), event.message());
            event.renderer(new LpcfRenderer(result));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not format a chat message from " + player.getName(), ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatSent(AsyncChatEvent event) {
        if (!(event.renderer() instanceof LpcfRenderer renderer)) {
            return;
        }
        Player player = event.getPlayer();
        ChatFormatterService.Result result = renderer.result();
        Set<Audience> viewers = event.viewers();

        // Only public messages get a bubble: never a channel, staff or partly muted message.
        if (plugin.pluginConfig().bubblesEnabled() && player.hasPermission("lpcf.bubbles")
                && viewers.containsAll(Bukkit.getOnlinePlayers())) {
            String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
            int max = plugin.pluginConfig().bubbleMaxCharacters();
            Component bubble = plain.length() > max
                    ? formatterService.format(player, player.displayName(), MessageComponents.truncate(plain, max)).bubble()
                    : result.bubble();
            SchedulerUtil.run(plugin, player, () -> plugin.textTagService().showBubble(player, bubble, Math.min(plain.length(), max)));
        }

        for (String name : result.mentioned()) {
            Player target = Bukkit.getPlayerExact(name);
            if (target != null && viewers.contains(target)) {
                SchedulerUtil.run(plugin, target, () -> notifyMention(target, player));
            }
        }
    }

    private void notifyMention(Player target, Player sender) {
        String sound = plugin.pluginConfig().mentionSound();
        if (sound != null && !sound.isEmpty()) {
            try {
                target.playSound(Sound.sound(Key.key(sound), Sound.Source.PLAYER, 1f, 1f));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Invalid mentions.sound '" + sound + "': " + ex.getMessage());
            }
        }
        String actionbar = plugin.pluginConfig().mentionActionbar();
        if (actionbar != null && !actionbar.isEmpty()) {
            target.sendActionBar(plugin.formatter().simple(actionbar, Map.of("sender", sender.getName())));
        }
    }
}
