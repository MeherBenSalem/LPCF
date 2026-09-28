package io.nightbeam.LPCF.chat;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.util.LuckPermsUtil;
import io.nightbeam.LPCF.util.MiniMessageUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.track.Track;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Pattern;

public final class ChatFormatterService {

    private static final Pattern NAME_TOKEN_PATTERN = Pattern.compile(Pattern.quote(ChatFormatPlaceholders.NAME_TOKEN));
    private static final Pattern DISPLAY_NAME_TOKEN_PATTERN = Pattern.compile(Pattern.quote(ChatFormatPlaceholders.DISPLAY_NAME_TOKEN));

    private final LuckPermsChatFormatterFolia plugin;
    private final MiniMessage miniMessage;

    public ChatFormatterService(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public Component formatChatMessage(Player player, Component sourceDisplayName, Component message) {
        PluginConfig config = plugin.pluginConfig();
        CachedMetaData metaData = plugin.luckPerms().getPlayerAdapter(Player.class).getMetaData(player);

        String group = LuckPermsUtil.primaryGroup(metaData);
        String format = resolveFormat(group, config);

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(message);
        rawMessage = player.hasPermission("lpcf.colorcodes")
                ? MiniMessageUtil.normalize(rawMessage)
                : MiniMessageUtil.stripFormatting(rawMessage);

        String resolved = ChatFormatPlaceholders.substitute(
                format,
                LuckPermsUtil.prefix(metaData),
                LuckPermsUtil.suffix(metaData),
                LuckPermsUtil.joinedPrefixes(metaData),
                LuckPermsUtil.joinedSuffixes(metaData),
                player.getWorld().getName(),
                LuckPermsUtil.metaValue(metaData, "username-color"),
                LuckPermsUtil.metaValue(metaData, "message-color"),
                rawMessage
        );

        if (plugin.hasPlaceholderApi()) {
            resolved = MiniMessageUtil.normalize(PlaceholderAPI.setPlaceholders(player, resolved));
        }

        Component rendered = miniMessage.deserialize(resolved);
        Component fallbackName = Component.text(player.getName());
        Component finalDisplayName = sourceDisplayName == null ? fallbackName : sourceDisplayName;

        rendered = rendered.replaceText(TextReplacementConfig.builder()
            .match(NAME_TOKEN_PATTERN)
            .replacement(finalDisplayName)
            .build());

        rendered = rendered.replaceText(TextReplacementConfig.builder()
            .match(DISPLAY_NAME_TOKEN_PATTERN)
            .replacement(finalDisplayName)
            .build());

        if (config.useItemPlaceholder() && player.hasPermission("lpcf.itemplaceholder")) {
            rendered = applyItemPlaceholder(rendered, player);
        }

        return rendered;
    }

    private String resolveFormat(String group, PluginConfig config) {
        return ChatFormatPlaceholders.resolveFormat(
                group,
                config.chatFormat(),
                config.groupFormats(),
                config.trackFormats(),
                trackName -> {
                    Track track = plugin.luckPerms().getTrackManager().getTrack(trackName);
                    return track != null && track.containsGroup(group);
                }
        );
    }

    private Component applyItemPlaceholder(Component rendered, Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            return rendered;
        }

        Component itemDisplay = defaultItemName(heldItem);
        if (heldItem.getItemMeta() != null && heldItem.getItemMeta().hasDisplayName()) {
            Component displayName = heldItem.getItemMeta().displayName();
            if (displayName != null) {
                itemDisplay = displayName;
            }
        }

        Component finalItemDisplay = itemDisplay.hoverEvent(heldItem);

        return rendered.replaceText(TextReplacementConfig.builder()
                .match(ChatFormatPlaceholders.ITEM_PATTERN)
                .replacement(finalItemDisplay)
                .build());
    }

    private Component defaultItemName(ItemStack itemStack) {
        String pretty = itemStack.getType().name().toLowerCase().replace('_', ' ');
        return Component.text(pretty);
    }
}
