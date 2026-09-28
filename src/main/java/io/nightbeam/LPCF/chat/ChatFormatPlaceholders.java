package io.nightbeam.LPCF.chat;

import io.nightbeam.LPCF.util.MiniMessageUtil;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Pure format/placeholder helpers used by {@link ChatFormatterService}.
 * Kept free of Bukkit types so unit tests can cover chat format parsing.
 */
public final class ChatFormatPlaceholders {

    public static final String NAME_TOKEN = "__LPCF_NAME__";
    public static final String DISPLAY_NAME_TOKEN = "__LPCF_DISPLAYNAME__";

    static final Pattern ITEM_PATTERN = Pattern.compile("\\[item]", Pattern.CASE_INSENSITIVE);

    private ChatFormatPlaceholders() {
    }

    public static String resolveFormat(
            String group,
            String defaultFormat,
            Map<String, String> groupFormats,
            Map<String, String> trackFormats,
            Predicate<String> trackContainsGroup
    ) {
        if (group != null && groupFormats != null) {
            String groupFormat = groupFormats.get(group);
            if (groupFormat != null) {
                return groupFormat;
            }
        }

        if (trackFormats != null && trackContainsGroup != null) {
            for (Map.Entry<String, String> trackEntry : trackFormats.entrySet()) {
                if (trackContainsGroup.test(trackEntry.getKey())) {
                    return trackEntry.getValue();
                }
            }
        }

        return defaultFormat;
    }

    public static String substitute(
            String format,
            String prefix,
            String suffix,
            String prefixes,
            String suffixes,
            String world,
            String usernameColor,
            String messageColor,
            String message
    ) {
        return MiniMessageUtil.normalize(empty(format))
                .replace("{prefix}", MiniMessageUtil.normalize(empty(prefix)))
                .replace("{suffix}", MiniMessageUtil.normalize(empty(suffix)))
                .replace("{prefixes}", MiniMessageUtil.normalize(empty(prefixes)))
                .replace("{suffixes}", MiniMessageUtil.normalize(empty(suffixes)))
                .replace("{world}", empty(world))
                .replace("{name}", NAME_TOKEN)
                .replace("{displayname}", DISPLAY_NAME_TOKEN)
                .replace("{username-color}", MiniMessageUtil.normalize(empty(usernameColor)))
                .replace("{message-color}", MiniMessageUtil.normalize(empty(messageColor)))
                .replace("{message}", empty(message));
    }

    public static boolean containsItemPlaceholder(String text) {
        return text != null && ITEM_PATTERN.matcher(text).find();
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
