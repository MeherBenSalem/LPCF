package io.nightbeam.LPCF.display.tag;

/**
 * Where nametag and bubble text sits above a player, and how long bubbles stay. Pure arithmetic, unit tested.
 *
 * <p>A text display's text grows upwards from the display's position. LPCF's displays sit at the top of the player's
 * head (or, on Folia, ride the player); the vanilla nametag sits about half a block above the head.</p>
 */
public final class TagLayout {

    /** Height of one line of display text, in blocks (font line 10 px at 1/40 block per px, plus padding). */
    public static final double LINE = 0.27;
    /** Space between the nametag and a bubble above it. */
    public static final double GAP = 0.08;

    private TagLayout() {
    }

    /** Lift above the top of the head to the bottom of where the vanilla name tag sits. */
    public static final double HEAD_LIFT = 0.35;

    /**
     * Lift of a display that sits at the top of the player's head (one that follows the player) or rides the player.
     * Riders sit at the passenger seat: up to 1.20.1 that is 75% of the player's height (1.35 blocks), from 1.20.2
     * the top of the head.
     */
    public static double baseLift(String minecraftVersion, boolean riding) {
        return riding && seatAtThreeQuarters(minecraftVersion) ? HEAD_LIFT + 0.45 : HEAD_LIFT;
    }

    static boolean seatAtThreeQuarters(String minecraftVersion) {
        if (minecraftVersion == null) {
            return false;
        }
        String[] parts = minecraftVersion.trim().split("[.\\-]");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? parseLeadingInt(parts[2]) : 0;
            if (major != 1) {
                return false;
            }
            return minor < 20 || (minor == 20 && patch <= 1);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static int parseLeadingInt(String value) {
        String digits = value.replaceAll("[^0-9].*", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    /** Lift of the nametag display: above the vanilla name when that is still shown. */
    public static double nametagLift(double base, double offset, boolean vanillaNameVisible) {
        return base + offset + (vanillaNameVisible ? LINE : 0);
    }

    /** Lift of the bubble: above the vanilla name (if shown) and every nametag line. */
    public static double bubbleLift(double base, double offset, boolean vanillaNameVisible, int nametagLines) {
        int lines = Math.max(0, nametagLines) + (vanillaNameVisible ? 1 : 0);
        return base + offset + lines * LINE + (lines > 0 ? GAP : 0);
    }

    /** Seconds a bubble stays: min + per-character, at most max. */
    public static double bubbleSeconds(int characters, double min, double perCharacter, double max) {
        double seconds = min + Math.max(0, characters) * Math.max(0, perCharacter);
        return Math.max(Math.min(min, max), Math.min(max, seconds));
    }

    /** Lines in a rendered text (newlines + 1), 0 for blank text. */
    public static int lineCount(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < plainText.length(); i++) {
            if (plainText.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
