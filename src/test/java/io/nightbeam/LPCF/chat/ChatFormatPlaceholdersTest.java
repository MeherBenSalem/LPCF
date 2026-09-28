package io.nightbeam.LPCF.chat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFormatPlaceholdersTest {

    @Test
    void substituteReplacesChatTokensAndNormalizesLegacyPrefix() {
        String resolved = ChatFormatPlaceholders.substitute(
                "{prefix}{name}<dark_gray> »<reset> {message}",
                "&cAdmin ",
                "",
                "&cAdmin ",
                "",
                "world",
                "&#00FF00",
                "&7",
                "hello"
        );

        assertEquals(
                "<red>Admin " + ChatFormatPlaceholders.NAME_TOKEN + "<dark_gray> »<reset> hello",
                resolved
        );
    }

    @Test
    void substituteLeavesWorldRawAndUsesDisplayNameToken() {
        String resolved = ChatFormatPlaceholders.substitute(
                "{world} {displayname} {username-color}{message-color}{message}",
                null,
                null,
                null,
                null,
                "spawn_nether",
                "&#ABCDEF",
                "&b",
                "hi"
        );

        assertEquals(
                "spawn_nether " + ChatFormatPlaceholders.DISPLAY_NAME_TOKEN + " <#ABCDEF><aqua>hi",
                resolved
        );
    }

    @Test
    void substituteJoinsPrefixesAndSuffixes() {
        String resolved = ChatFormatPlaceholders.substitute(
                "{prefixes}{name}{suffixes}",
                "&aA ",
                "&bB",
                "&aA &eB ",
                " &7[X]",
                "world",
                "",
                "",
                "ignored"
        );

        assertEquals(
                "<green>A <yellow>B " + ChatFormatPlaceholders.NAME_TOKEN + " <gray>[X]",
                resolved
        );
    }

    @Test
    void resolveFormatPrefersGroupOverTrackAndDefault() {
        Map<String, String> groups = Map.of("admin", "<red>{name}: {message}");
        Map<String, String> tracks = new LinkedHashMap<>();
        tracks.put("staff", "<gold>{name}: {message}");

        assertEquals(
                "<red>{name}: {message}",
                ChatFormatPlaceholders.resolveFormat("admin", "default {message}", groups, tracks, track -> true)
        );
        assertEquals(
                "<gold>{name}: {message}",
                ChatFormatPlaceholders.resolveFormat("mod", "default {message}", groups, tracks, "staff"::equals)
        );
        assertEquals(
                "default {message}",
                ChatFormatPlaceholders.resolveFormat("default", "default {message}", groups, tracks, track -> false)
        );
    }

    @Test
    void containsItemPlaceholderIsCaseInsensitive() {
        assertTrue(ChatFormatPlaceholders.containsItemPlaceholder("Look at [ITEM]"));
        assertTrue(ChatFormatPlaceholders.containsItemPlaceholder("[item]"));
        assertTrue(ChatFormatPlaceholders.containsItemPlaceholder("a [ItEm] b"));
        assertFalse(ChatFormatPlaceholders.containsItemPlaceholder("no placeholder"));
        assertFalse(ChatFormatPlaceholders.containsItemPlaceholder(null));
    }
}
