package io.nightbeam.LPCF.chat;

import io.nightbeam.LPCF.util.MiniMessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Pure format/placeholder helpers used by the chat, nametag, tab and join/quit formatters.
 * Kept free of Bukkit types so unit tests can cover format parsing.
 *
 * <p>{name}, {displayname} and {message} become MiniMessage tags ({@link #NAME_TAG}, {@link #DISPLAY_NAME_TAG},
 * {@link #MESSAGE_TAG}) that are filled with components after parsing, so nothing a player controls (their message,
 * a nickname) is ever parsed as MiniMessage or passed to PlaceholderAPI.</p>
 */
public final class ChatFormatPlaceholders {

    public static final String NAME_TAG = "lpcf-name";
    public static final String DISPLAY_NAME_TAG = "lpcf-displayname";
    public static final String MESSAGE_TAG = "lpcf-message";

    static final Pattern ITEM_PATTERN = Pattern.compile("\\[item]", Pattern.CASE_INSENSITIVE);

    /** Where a chat format came from; shown by /lpcf debug. */
    public enum Source { GROUP, TRACK, WORLD, DEFAULT }

    public record Resolved(String format, Source source, String key) {
    }

    /** The LuckPerms and world values for one player, as raw config text (legacy codes allowed). */
    public record Values(String prefix, String suffix, String prefixes, String suffixes, String group, String world,
                         String usernameColor, String messageColor) {

        public static Values empty() {
            return new Values("", "", "", "", "default", "", "", "");
        }
    }

    private ChatFormatPlaceholders() {
    }

    public static String resolveFormat(
            String group,
            String defaultFormat,
            Map<String, String> groupFormats,
            Map<String, String> trackFormats,
            Predicate<String> trackContainsGroup
    ) {
        return resolve(group, null, defaultFormat, groupFormats, trackFormats, null, trackContainsGroup).format();
    }

    /** Priority: group format, then the first track containing the group, then the world format, then the default. */
    public static Resolved resolve(
            String group,
            String world,
            String defaultFormat,
            Map<String, String> groupFormats,
            Map<String, String> trackFormats,
            Map<String, String> worldFormats,
            Predicate<String> trackContainsGroup
    ) {
        if (group != null && groupFormats != null) {
            String groupFormat = groupFormats.get(group);
            if (groupFormat != null) {
                return new Resolved(groupFormat, Source.GROUP, group);
            }
        }

        if (trackFormats != null && trackContainsGroup != null) {
            for (Map.Entry<String, String> trackEntry : trackFormats.entrySet()) {
                if (trackContainsGroup.test(trackEntry.getKey())) {
                    return new Resolved(trackEntry.getValue(), Source.TRACK, trackEntry.getKey());
                }
            }
        }

        if (world != null && worldFormats != null) {
            String worldFormat = worldFormats.get(world);
            if (worldFormat != null) {
                return new Resolved(worldFormat, Source.WORLD, world);
            }
        }

        return new Resolved(defaultFormat, Source.DEFAULT, "chat-format");
    }

    /**
     * Fills every LPCF placeholder. The result is a MiniMessage string; run PlaceholderAPI on it (it only contains
     * admin-written text) and parse it with the name, display name and message tags resolved to components.
     */
    public static String substitute(String format, Values values) {
        return MiniMessageUtil.normalize(empty(format))
                .replace("{prefix}", MiniMessageUtil.normalize(empty(values.prefix())))
                .replace("{suffix}", MiniMessageUtil.normalize(empty(values.suffix())))
                .replace("{prefixes}", MiniMessageUtil.normalize(empty(values.prefixes())))
                .replace("{suffixes}", MiniMessageUtil.normalize(empty(values.suffixes())))
                .replace("{group}", escape(empty(values.group())))
                .replace("{world}", escape(empty(values.world())))
                .replace("{name}", "<" + NAME_TAG + ">")
                .replace("{displayname}", "<" + DISPLAY_NAME_TAG + ">")
                .replace("{username-color}", MiniMessageUtil.normalize(empty(values.usernameColor())))
                .replace("{message-color}", MiniMessageUtil.normalize(empty(values.messageColor())))
                .replace("{message}", "<" + MESSAGE_TAG + ">");
    }

    /** Replaces simple {key} placeholders with text escaped for MiniMessage. */
    public static String fill(String format, Map<String, String> values) {
        String result = empty(format);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", escape(empty(entry.getValue())));
        }
        return result;
    }

    public static boolean containsItemPlaceholder(String text) {
        return text != null && ITEM_PATTERN.matcher(text).find();
    }

    /** Escapes text so MiniMessage shows it literally. */
    public static String escape(String text) {
        return MiniMessage.miniMessage().escapeTags(empty(text));
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
