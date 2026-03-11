package io.nightbeam.LPCF.util;

import java.util.regex.Pattern;

public final class MiniMessageUtil {

    private static final Pattern AMPERSAND_RGB_SEQUENCE_PATTERN = Pattern.compile("(?i)&x(?:&[A-F0-9]){6}");
    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern AMPERSAND_LEGACY_PATTERN = Pattern.compile("(?i)&([0-9A-FK-OR])");
    private static final Pattern SECTION_LEGACY_PATTERN = Pattern.compile("(?i)§([0-9A-FK-OR])");
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[^<>]+>");

    private MiniMessageUtil() {
    }

    // LuckPerms meta often uses '&#RRGGBB' format; MiniMessage expects '<#RRGGBB>'.
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String normalized = normalizeLegacyHex(input);
        normalized = AMPERSAND_HEX_PATTERN.matcher(normalized).replaceAll("<#$1>");
        normalized = AMPERSAND_LEGACY_PATTERN.matcher(normalized)
                .replaceAll(matchResult -> toMiniTag(matchResult.group(1).charAt(0)));
        return SECTION_LEGACY_PATTERN.matcher(normalized)
                .replaceAll(matchResult -> toMiniTag(matchResult.group(1).charAt(0)));
    }

    public static String stripFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String withoutTags = MINI_TAG_PATTERN.matcher(input).replaceAll("");
        String withoutLegacyHex = AMPERSAND_RGB_SEQUENCE_PATTERN.matcher(withoutTags).replaceAll("");
        String withoutHex = AMPERSAND_HEX_PATTERN.matcher(withoutLegacyHex).replaceAll("");
        String withoutAmpersandLegacy = AMPERSAND_LEGACY_PATTERN.matcher(withoutHex).replaceAll("");
        return SECTION_LEGACY_PATTERN.matcher(withoutAmpersandLegacy).replaceAll("");
    }

    private static String normalizeLegacyHex(String input) {
        return AMPERSAND_RGB_SEQUENCE_PATTERN.matcher(input)
                .replaceAll(matchResult -> {
                    String sequence = matchResult.group();
                    StringBuilder hex = new StringBuilder(6);

                    for (int i = 2; i < sequence.length(); i += 2) {
                        hex.append(sequence.charAt(i + 1));
                    }

                    return "<#" + hex + ">";
                });
    }

    private static String toMiniTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> "";
        };
    }
}
