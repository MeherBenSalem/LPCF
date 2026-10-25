package io.nightbeam.LPCF.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageComponentsTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<Component> flatten(Component component) {
        List<Component> out = new ArrayList<>();
        out.add(component);
        for (Component child : component.children()) {
            out.addAll(flatten(child));
        }
        return out;
    }

    @Test
    void playersWithoutPermissionSeeTheirTextLiterally() {
        Component c = MessageComponents.styled("<red>hi <3 &chey", false);
        assertEquals("<red>hi <3 &chey", plain(c));
        assertNull(c.color());
    }

    @Test
    void styledAllowsColoursButNotClickHoverOrInsertion() {
        Component c = MessageComponents.styled(
                "<red>red</red> <click:run_command:/op>x</click> <hover:show_text:'h'>y</hover> &aGreen", true);
        assertEquals("red <click:run_command:/op>x</click> <hover:show_text:'h'>y</hover> Green", plain(c));
        for (Component part : flatten(c)) {
            assertNull(part.clickEvent());
            assertNull(part.hoverEvent());
        }
        assertTrue(flatten(c).stream().anyMatch(p -> NamedTextColor.RED.equals(p.color())));
    }

    @Test
    void replacementsUseLongestKeyFirst() {
        Map<String, Component> map = new LinkedHashMap<>();
        map.put("<3", Component.text("❤", NamedTextColor.RED));
        map.put(":)", Component.text("☺"));
        map.put(":))", Component.text("☻"));
        Component c = MessageComponents.replace(Component.text("I <3 you :)) :)"), map);
        assertEquals("I ❤ you ☻ ☺", plain(c));
    }

    @Test
    void linksBecomeClickableWithoutTrailingPunctuation() {
        Component c = MessageComponents.links(Component.text("see https://nightbeam.dev/support and www.example.com!"),
                url -> Component.text(url).clickEvent(ClickEvent.openUrl(url)));
        List<String> urls = new ArrayList<>();
        for (Component part : flatten(c)) {
            if (part.clickEvent() != null) {
                urls.add(part.clickEvent().value());
            }
        }
        assertEquals(List.of("https://nightbeam.dev/support", "https://www.example.com"), urls);
    }

    @Test
    void mentionsMatchAtAndBareNamesIgnoringCase() {
        Set<String> mentioned = new HashSet<>();
        Component c = MessageComponents.mentions(Component.text("hey @builder and Alex, mail@Steve x"),
                List.of("Builder", "Alex", "Steve"), true, name -> Component.text("@" + name), mentioned);
        assertEquals("hey @Builder and @Alex, mail@Steve x", plain(c));
        assertEquals(Set.of("Builder", "Alex"), mentioned);
    }

    @Test
    void mentionsWithoutBareNamesNeedAt() {
        Set<String> mentioned = new HashSet<>();
        Component c = MessageComponents.mentions(Component.text("Alex and @Alex"), List.of("Alex"), false,
                name -> Component.text("[" + name + "]"), mentioned);
        assertEquals("Alex and [Alex]", plain(c));
        assertEquals(Set.of("Alex"), mentioned);
    }

    @Test
    void mentionsDoNotMatchInsideWords() {
        Set<String> mentioned = new HashSet<>();
        Component c = MessageComponents.mentions(Component.text("Alexander"), List.of("Alex"), true,
                name -> Component.text("!"), mentioned);
        assertEquals("Alexander", plain(c));
        assertTrue(mentioned.isEmpty());
    }

    @Test
    void truncateAddsEllipsis() {
        assertEquals("hello", MessageComponents.truncate("hello", 10));
        assertEquals("hello...", MessageComponents.truncate("hello world", 8));
        assertEquals("", MessageComponents.truncate(null, 5));
    }

    @Test
    void urlHelpers() {
        assertEquals("https://www.a.com", MessageComponents.withScheme("www.a.com"));
        assertEquals("http://a.com", MessageComponents.withScheme("http://a.com"));
        assertEquals("https://a.com/x", MessageComponents.trimTrailingPunctuation("https://a.com/x)."));
    }
}
