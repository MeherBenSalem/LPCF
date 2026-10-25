package io.nightbeam.LPCF.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFormatPlaceholdersTest {

    private static final String NAME = "<" + ChatFormatPlaceholders.NAME_TAG + ">";
    private static final String DISPLAY = "<" + ChatFormatPlaceholders.DISPLAY_NAME_TAG + ">";
    private static final String MESSAGE = "<" + ChatFormatPlaceholders.MESSAGE_TAG + ">";

    private static ChatFormatPlaceholders.Values values(String prefix, String suffix, String prefixes, String suffixes,
                                                        String world, String usernameColor, String messageColor) {
        return new ChatFormatPlaceholders.Values(prefix, suffix, prefixes, suffixes, "admin", world, usernameColor, messageColor);
    }

    @Test
    void substituteReplacesChatTokensAndNormalizesLegacyPrefix() {
        String resolved = ChatFormatPlaceholders.substitute(
                "{prefix}{name}<dark_gray> »<reset> {message}",
                values("&cAdmin ", "", "&cAdmin ", "", "world", "&#00FF00", "&7"));

        assertEquals("<red>Admin " + NAME + "<dark_gray> »<reset> " + MESSAGE, resolved);
    }

    @Test
    void substituteFillsWorldGroupDisplayNameAndColors() {
        String resolved = ChatFormatPlaceholders.substitute(
                "{world} {group} {displayname} {username-color}{message-color}{message}",
                values(null, null, null, null, "spawn_nether", "&#ABCDEF", "&b"));

        assertEquals("spawn_nether admin " + DISPLAY + " <#ABCDEF><aqua>" + MESSAGE, resolved);
    }

    @Test
    void substituteJoinsPrefixesAndSuffixes() {
        String resolved = ChatFormatPlaceholders.substitute(
                "{prefixes}{name}{suffixes}",
                values("&aA ", "&bB", "&aA &eB ", " &7[X]", "world", "", ""));

        assertEquals("<green>A <yellow>B " + NAME + " <gray>[X]", resolved);
    }

    @Test
    void playerTextIsInsertedAfterParsingSoItCannotInjectTagsOrPlaceholders() {
        String format = ChatFormatPlaceholders.substitute("{prefix}{name}: {message}",
                values("<red>[Admin] ", "", "", "", "world", "", ""));
        Component message = Component.text("<click:run_command:'/op me'>hi</click> %player_ip% {prefix}");
        Component line = MiniMessage.miniMessage().deserialize(format, TagResolver.resolver(
                Placeholder.component(ChatFormatPlaceholders.NAME_TAG, Component.text("Builder")),
                Placeholder.component(ChatFormatPlaceholders.MESSAGE_TAG, message)));

        assertEquals("[Admin] Builder: <click:run_command:'/op me'>hi</click> %player_ip% {prefix}",
                PlainTextComponentSerializer.plainText().serialize(line));
    }

    @Test
    void worldAndGroupNamesAreEscaped() {
        String resolved = ChatFormatPlaceholders.substitute("{world}",
                values("", "", "", "", "<red>world", "", ""));
        assertEquals("<red>world", PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(resolved)));
    }

    @Test
    void resolveFormatPrefersGroupOverTrackOverWorldOverDefault() {
        Map<String, String> groups = Map.of("admin", "<red>{name}: {message}");
        Map<String, String> tracks = new LinkedHashMap<>();
        tracks.put("staff", "<gold>{name}: {message}");
        Map<String, String> worlds = Map.of("world_nether", "<dark_red>{name}: {message}");

        assertEquals(ChatFormatPlaceholders.Source.GROUP,
                ChatFormatPlaceholders.resolve("admin", "world_nether", "d", groups, tracks, worlds, track -> true).source());
        ChatFormatPlaceholders.Resolved track =
                ChatFormatPlaceholders.resolve("mod", "world_nether", "d", groups, tracks, worlds, "staff"::equals);
        assertEquals(ChatFormatPlaceholders.Source.TRACK, track.source());
        assertEquals("staff", track.key());
        ChatFormatPlaceholders.Resolved world =
                ChatFormatPlaceholders.resolve("default", "world_nether", "d", groups, tracks, worlds, t -> false);
        assertEquals("<dark_red>{name}: {message}", world.format());
        assertEquals(ChatFormatPlaceholders.Source.WORLD, world.source());
        assertEquals(ChatFormatPlaceholders.Source.DEFAULT,
                ChatFormatPlaceholders.resolve("default", "world", "d", groups, tracks, worlds, t -> false).source());
        assertEquals("default {message}",
                ChatFormatPlaceholders.resolveFormat("default", "default {message}", groups, tracks, t -> false));
    }

    @Test
    void fillEscapesValues() {
        assertEquals("@\\<b>Steve", ChatFormatPlaceholders.fill("@{name}", Map.of("name", "<b>Steve")));
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
