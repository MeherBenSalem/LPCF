package io.nightbeam.LPCF.chat;

import io.nightbeam.LPCF.util.MiniMessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the component for what a player typed. Pure Adventure, no server state, so it is unit tested.
 *
 * <p>Players without the styling permission get their text literally. With it they get colours, decorations,
 * gradients and rainbow only: never click, hover, insertion, selectors, NBT or translations.</p>
 */
public final class MessageComponents {

    private static final MiniMessage SAFE_STYLE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()
            ))
            .build();

    // http(s)://host... or www.host.tld...; stops at whitespace and angle brackets.
    static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?://[^\\s<>]+|www\\.[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?:[/?#][^\\s<>]*)?)");

    private MessageComponents() {
    }

    public static Component styled(String raw, boolean allowStyle) {
        String text = raw == null ? "" : raw;
        if (!allowStyle) {
            return Component.text(text);
        }
        return SAFE_STYLE.deserialize(MiniMessageUtil.normalize(text));
    }

    /** Replaces each literal key (case-sensitive, longest first) with its component. */
    public static Component replace(Component message, Map<String, Component> replacements) {
        List<String> keys = new ArrayList<>(replacements.keySet());
        keys.removeIf(String::isEmpty);
        keys.sort(Comparator.comparingInt(String::length).reversed());
        if (keys.isEmpty()) {
            return message;
        }
        Pattern pattern = Pattern.compile(keys.stream().map(Pattern::quote).collect(Collectors.joining("|")));
        return message.replaceText(TextReplacementConfig.builder()
                .match(pattern)
                .replacement((match, builder) -> replacements.get(match.group()))
                .build());
    }

    /** Turns URLs into components made by {@code render} (given the URL with a scheme). */
    public static Component links(Component message, Function<String, Component> render) {
        return message.replaceText(TextReplacementConfig.builder()
                .match(URL_PATTERN)
                .replacement((match, builder) -> render.apply(withScheme(trimTrailingPunctuation(match.group()))))
                .build());
    }

    static String withScheme(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? url : "https://" + url;
    }

    // "see https://example.com." should not link the full stop.
    static String trimTrailingPunctuation(String url) {
        String result = url;
        while (!result.isEmpty() && ".,!?;:)'\"".indexOf(result.charAt(result.length() - 1)) >= 0) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Highlights mentions of {@code names}: "@name" always, a bare "name" too when {@code withoutAt}.
     * Matching ignores case; {@code render} gets the name as written in {@code names}; every mentioned name is
     * added to {@code mentioned}.
     */
    public static Component mentions(Component message, Collection<String> names, boolean withoutAt,
                                     Function<String, Component> render, Set<String> mentioned) {
        Pattern pattern = mentionPattern(names, withoutAt);
        if (pattern == null) {
            return message;
        }
        Map<String, String> canonical = new HashMap<>();
        for (String name : names) {
            canonical.put(name.toLowerCase(Locale.ROOT), name);
        }
        return message.replaceText(TextReplacementConfig.builder()
                .match(pattern)
                .replacement((match, builder) -> {
                    String name = canonical.get(match.group(1).toLowerCase(Locale.ROOT));
                    mentioned.add(name);
                    return render.apply(name);
                })
                .build());
    }

    static Pattern mentionPattern(Collection<String> names, boolean withoutAt) {
        List<String> usable = names.stream()
                .filter(name -> name != null && name.length() >= (withoutAt ? 3 : 1))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.toList());
        if (usable.isEmpty()) {
            return null;
        }
        String at = withoutAt ? "@?" : "@";
        return Pattern.compile("(?i)(?<![\\w@])" + at + "(" + String.join("|", usable) + ")(?!\\w)");
    }

    /** Shortens to {@code max} characters with "..." (for bubbles). */
    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 3)).stripTrailing() + "...";
    }
}
