package io.nightbeam.LPCF.chat;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.format.PlayerFormatter;
import io.nightbeam.LPCF.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChatFormatterService {

    /** One formatted chat message. */
    public record Result(Component line, Component bubble, Set<String> mentioned,
                         ChatFormatPlaceholders.Resolved format) {
    }

    private final LpcfPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatFormatterService(LpcfPlugin plugin) {
        this.plugin = plugin;
    }

    public Result format(Player player, Component sourceDisplayName, Component message) {
        return format(player, sourceDisplayName, PlainTextComponentSerializer.plainText().serialize(message));
    }

    public Result format(Player player, Component sourceDisplayName, String rawMessage) {
        PluginConfig config = plugin.pluginConfig();
        PlayerFormatter formatter = plugin.formatter();
        CachedMetaData meta = formatter.meta(player);
        ChatFormatPlaceholders.Values values = formatter.values(player, meta);
        ChatFormatPlaceholders.Resolved resolved = resolveFormat(values.group(), values.world(), config);

        Component text = MessageComponents.styled(rawMessage, player.hasPermission("lpcf.colorcodes"));
        if (config.replacementsEnabled() && player.hasPermission("lpcf.replacements") && !config.replacements().isEmpty()) {
            text = MessageComponents.replace(text, replacementComponents(config.replacements()));
        }
        Component bubbleText = text;

        Set<String> mentioned = new LinkedHashSet<>();
        if (config.mentionsEnabled() && player.hasPermission("lpcf.mention")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    names.add(online.getName());
                }
            }
            text = MessageComponents.mentions(text, names, config.mentionsWithoutAt(),
                    name -> formatter.simple(config.mentionFormat(), Map.of("name", name)), mentioned);
        }
        if (config.linksEnabled() && player.hasPermission("lpcf.links")) {
            text = MessageComponents.links(text, url -> formatter.simple(config.linkFormat(), Map.of("url", url))
                    .hoverEvent(HoverEvent.showText(formatter.simple(config.linkHover(), Map.of("url", url))))
                    .clickEvent(ClickEvent.openUrl(url)));
        }
        if (config.useItemPlaceholder() && player.hasPermission("lpcf.itemplaceholder")) {
            text = applyItemPlaceholder(text, player);
            bubbleText = applyItemPlaceholder(bubbleText, player);
        }

        Component displayName = sourceDisplayName == null ? Component.text(player.getName()) : sourceDisplayName;
        Component name = decorateName(displayName, player, values, config);
        Component line = formatter.render(resolved.format(), player, values, name, name, text, TagResolver.empty());

        Component bubble = formatter.render(config.bubbleFormat(), player, values, displayName, displayName,
                bubbleText, TagResolver.empty());
        return new Result(line, bubble, mentioned, resolved);
    }

    public ChatFormatPlaceholders.Resolved resolveFormat(String group, String world, PluginConfig config) {
        return ChatFormatPlaceholders.resolve(
                group,
                world,
                config.chatFormat(),
                config.groupFormats(),
                config.trackFormats(),
                config.worldFormats(),
                trackName -> {
                    Track track = plugin.luckPerms().getTrackManager().getTrack(trackName);
                    return track != null && track.containsGroup(group);
                }
        );
    }

    private Component decorateName(Component name, Player player, ChatFormatPlaceholders.Values values, PluginConfig config) {
        if (!config.nameHoverEnabled()) {
            return name;
        }
        Component result = name;
        if (!config.nameHoverLines().isEmpty()) {
            result = result.hoverEvent(HoverEvent.showText(plugin.formatter().renderLines(config.nameHoverLines(), player, values)));
        }
        String suggest = config.nameClickSuggest();
        if (suggest != null && !suggest.isEmpty()) {
            result = result.clickEvent(ClickEvent.suggestCommand(suggest.replace("{name}", player.getName())));
        }
        return result;
    }

    private Map<String, Component> replacementComponents(Map<String, String> replacements) {
        Map<String, Component> components = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            components.put(entry.getKey(), miniMessage.deserialize(MiniMessageUtil.normalize(entry.getValue())));
        }
        return components;
    }

    private Component applyItemPlaceholder(Component rendered, Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType().isAir()) {
            return rendered;
        }

        Component itemDisplay = itemName(heldItem);
        if (heldItem.getAmount() > 1) {
            itemDisplay = itemDisplay.append(Component.text(" x" + heldItem.getAmount()));
        }
        Component finalItemDisplay = Component.text("[").append(itemDisplay).append(Component.text("]"))
                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .hoverEvent(heldItem);

        return rendered.replaceText(TextReplacementConfig.builder()
                .match(ChatFormatPlaceholders.ITEM_PATTERN)
                .replacement(finalItemDisplay)
                .build());
    }

    static Component itemName(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            Component displayName = itemMeta.displayName();
            if (displayName != null) {
                return displayName;
            }
        }
        try {
            // Shows in each player's own language.
            return Component.translatable(itemStack.getType().translationKey());
        } catch (LinkageError | RuntimeException ex) {
            return Component.text(prettyName(itemStack.getType().name()));
        }
    }

    static String prettyName(String enumName) {
        String[] words = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
