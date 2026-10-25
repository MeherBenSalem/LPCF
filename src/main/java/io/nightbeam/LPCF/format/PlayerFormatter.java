package io.nightbeam.LPCF.format;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.chat.ChatFormatPlaceholders;
import io.nightbeam.LPCF.util.LuckPermsUtil;
import io.nightbeam.LPCF.util.MiniMessageUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Turns admin-written formats into components for one player. Thread-safe (used from the async chat thread). */
public final class PlayerFormatter {

    private final LpcfPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile boolean placeholderErrorLogged;

    public PlayerFormatter(LpcfPlugin plugin) {
        this.plugin = plugin;
    }

    public CachedMetaData meta(Player player) {
        return plugin.luckPerms().getPlayerAdapter(Player.class).getMetaData(player);
    }

    public ChatFormatPlaceholders.Values values(Player player) {
        return values(player, meta(player));
    }

    public ChatFormatPlaceholders.Values values(Player player, CachedMetaData meta) {
        return new ChatFormatPlaceholders.Values(
                LuckPermsUtil.prefix(meta),
                LuckPermsUtil.suffix(meta),
                LuckPermsUtil.joinedPrefixes(meta),
                LuckPermsUtil.joinedSuffixes(meta),
                LuckPermsUtil.primaryGroup(meta),
                player.getWorld().getName(),
                LuckPermsUtil.metaValue(meta, "username-color"),
                LuckPermsUtil.metaValue(meta, "message-color")
        );
    }

    /** LPCF placeholders, then PlaceholderAPI (on admin text only), as a MiniMessage string. */
    public String prepare(String format, Player player, ChatFormatPlaceholders.Values values) {
        String resolved = ChatFormatPlaceholders.substitute(format, values);
        if (plugin.hasPlaceholderApi() && resolved.indexOf('%') >= 0) {
            try {
                resolved = MiniMessageUtil.normalize(PlaceholderAPI.setPlaceholders(player, resolved));
            } catch (RuntimeException ex) {
                if (!placeholderErrorLogged) {
                    placeholderErrorLogged = true;
                    plugin.getLogger().log(Level.WARNING, "A PlaceholderAPI placeholder failed; it is left as is.", ex);
                }
            }
        }
        return resolved;
    }

    public Component render(String format, Player player) {
        return render(format, player, values(player), Component.text(player.getName()), player.displayName(),
                Component.empty(), TagResolver.empty());
    }

    public Component render(String format, Player player, ChatFormatPlaceholders.Values values, Component name,
                            Component displayName, Component message, TagResolver extra) {
        return parse(prepare(format, player, values), name, displayName, message, extra);
    }

    public Component parse(String prepared, Component name, Component displayName, Component message, TagResolver extra) {
        return miniMessage.deserialize(prepared, TagResolver.resolver(
                Placeholder.component(ChatFormatPlaceholders.NAME_TAG, name),
                Placeholder.component(ChatFormatPlaceholders.DISPLAY_NAME_TAG, displayName),
                Placeholder.component(ChatFormatPlaceholders.MESSAGE_TAG, message),
                extra
        ));
    }

    /** Several lines joined with newlines; blank results are dropped. */
    public Component renderLines(List<String> lines, Player player, ChatFormatPlaceholders.Values values) {
        Component result = null;
        Component name = Component.text(player.getName());
        for (String line : lines) {
            Component rendered = render(line, player, values, name, player.displayName(), Component.empty(), TagResolver.empty());
            if (PlainTextComponentSerializer.plainText().serialize(rendered).isBlank()) {
                continue;
            }
            if (result == null) {
                result = rendered;
            } else {
                result = result.append(Component.newline()).append(rendered);
            }
        }
        return result == null ? Component.empty() : result;
    }

    /** Simple {key} text placeholders (escaped), then MiniMessage. */
    public Component simple(String format, Map<String, String> values) {
        return miniMessage.deserialize(MiniMessageUtil.normalize(ChatFormatPlaceholders.fill(format, values)));
    }

    /** LuckPerms weight of the player's primary group, or the highest prefix weight when the group has none. */
    public int weight(Player player) {
        CachedMetaData meta = meta(player);
        Group group = plugin.luckPerms().getGroupManager().getGroup(LuckPermsUtil.primaryGroup(meta));
        if (group != null && group.getWeight().isPresent()) {
            return group.getWeight().getAsInt();
        }
        return meta.getPrefixes().keySet().stream().max(Integer::compareTo).orElse(0);
    }
}
