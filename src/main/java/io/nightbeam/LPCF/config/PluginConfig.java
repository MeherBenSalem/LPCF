package io.nightbeam.LPCF.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** An immutable-by-convention snapshot of config.yml, rebuilt on every reload. */
public final class PluginConfig {

    public enum NametagStyle { AUTO, TEAM, OFF;

        static NametagStyle parse(String value) {
            try {
                return valueOf(value == null ? "AUTO" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return AUTO;
            }
        }
    }

    private final FileConfiguration config;

    private String chatFormat;
    private String nametagFormat;
    private Map<String, String> groupFormats;
    private Map<String, String> trackFormats;
    private Map<String, String> worldFormats;
    private boolean useItemPlaceholder;
    private String reloadMessage;
    private boolean updateCheckEnabled;
    private String updateCheckMessage;

    private boolean nameHoverEnabled;
    private List<String> nameHoverLines;
    private String nameClickSuggest;

    private boolean mentionsEnabled;
    private String mentionFormat;
    private boolean mentionsWithoutAt;
    private String mentionSound;
    private String mentionActionbar;

    private boolean linksEnabled;
    private String linkFormat;
    private String linkHover;

    private boolean replacementsEnabled;
    private Map<String, String> replacements;

    private NametagStyle nametagStyle;
    private List<String> nametagLines;
    private List<String> nametagLinesAboveVanilla;
    private boolean hideNametagWhenSneaking;
    private double nametagOffset;
    private int nametagUpdateTicks;

    private boolean bubblesEnabled;
    private String bubbleFormat;
    private Integer bubbleBackground;
    private double bubbleMinSeconds;
    private double bubbleSecondsPerChar;
    private double bubbleMaxSeconds;
    private int bubbleLineWidth;
    private int bubbleMaxCharacters;

    private boolean tabSortByWeight;
    private boolean tabHeaderFooterEnabled;
    private List<String> tabHeader;
    private List<String> tabFooter;
    private int tabUpdateTicks;

    private boolean joinQuitEnabled;
    private String joinFormat;
    private String quitFormat;
    private String firstJoinFormat;
    private Map<String, String> groupJoinFormats;
    private Map<String, String> groupQuitFormats;

    public PluginConfig(FileConfiguration config) {
        this.config = config;
        reload();
    }

    private void reload() {
        this.chatFormat = config.getString("chat-format", "{prefix}{name}<dark_gray> »<reset> {message}");
        this.nametagFormat = config.getString("nametag-format", "{prefix}{name}");
        this.groupFormats = readSectionMap("group-formats");
        this.trackFormats = readSectionMap("track-formats");
        this.worldFormats = readSectionMap("world-formats");
        this.useItemPlaceholder = config.getBoolean("use-item-placeholder", true);
        this.reloadMessage = config.getString("reload-message", "<green>LPCF configuration reloaded successfully!");
        this.updateCheckEnabled = config.getBoolean("update-check.enabled", true);
        this.updateCheckMessage = config.getString(
                "update-check.message",
                "<yellow>A new LPCF version is available: <white>{current} <gray>-> <green>{latest}"
        );

        this.nameHoverEnabled = config.getBoolean("name-hover.enabled", true);
        this.nameHoverLines = config.getStringList("name-hover.lines");
        this.nameClickSuggest = config.getString("name-hover.click-suggest", "/msg {name} ");

        this.mentionsEnabled = config.getBoolean("mentions.enabled", true);
        this.mentionFormat = config.getString("mentions.format", "<yellow>@{name}</yellow>");
        this.mentionsWithoutAt = config.getBoolean("mentions.without-at", true);
        this.mentionSound = config.getString("mentions.sound", "minecraft:entity.experience_orb.pickup");
        this.mentionActionbar = config.getString("mentions.actionbar", "<yellow>{sender} mentioned you");

        this.linksEnabled = config.getBoolean("links.enabled", true);
        this.linkFormat = config.getString("links.format", "<aqua><u>{url}</u></aqua>");
        this.linkHover = config.getString("links.hover", "<gray>Click to open {url}");

        this.replacementsEnabled = config.getBoolean("replacements.enabled", true);
        this.replacements = readSectionMap("replacements.list");

        this.nametagStyle = NametagStyle.parse(config.getString("nametags.style", "auto"));
        this.nametagLines = config.isList("nametags.lines")
                ? config.getStringList("nametags.lines") : Collections.singletonList(nametagFormat);
        this.nametagLinesAboveVanilla = config.isList("nametags.lines-above-vanilla-name")
                ? config.getStringList("nametags.lines-above-vanilla-name") : Collections.singletonList("{prefix}");
        this.hideNametagWhenSneaking = config.getBoolean("nametags.hide-when-sneaking", false);
        this.nametagOffset = config.getDouble("nametags.offset", 0.0);
        this.nametagUpdateTicks = Math.max(1, config.getInt("nametags.update-interval-ticks", 10));

        this.bubblesEnabled = config.getBoolean("bubbles.enabled", true);
        this.bubbleFormat = config.getString("bubbles.format", "<white>{message}");
        this.bubbleBackground = parseArgb(config.getString("bubbles.background", "default"));
        this.bubbleMinSeconds = config.getDouble("bubbles.min-seconds", 3.0);
        this.bubbleSecondsPerChar = config.getDouble("bubbles.seconds-per-character", 0.08);
        this.bubbleMaxSeconds = config.getDouble("bubbles.max-seconds", 10.0);
        this.bubbleLineWidth = Math.max(40, config.getInt("bubbles.line-width", 180));
        this.bubbleMaxCharacters = Math.max(8, config.getInt("bubbles.max-characters", 120));

        this.tabSortByWeight = config.getBoolean("tab.sort-by-weight", true);
        this.tabHeaderFooterEnabled = config.getBoolean("tab.header-footer.enabled", false);
        this.tabHeader = config.getStringList("tab.header-footer.header");
        this.tabFooter = config.getStringList("tab.header-footer.footer");
        this.tabUpdateTicks = Math.max(5, config.getInt("tab.header-footer.update-interval-ticks", 40));

        this.joinQuitEnabled = config.getBoolean("join-quit.enabled", false);
        this.joinFormat = config.getString("join-quit.join", "");
        this.quitFormat = config.getString("join-quit.quit", "");
        this.firstJoinFormat = config.getString("join-quit.first-join", "");
        this.groupJoinFormats = readSectionMap("join-quit.group-join");
        this.groupQuitFormats = readSectionMap("join-quit.group-quit");
    }

    public String chatFormat() { return chatFormat; }
    public String nametagFormat() { return nametagFormat; }
    public Map<String, String> groupFormats() { return groupFormats; }
    public Map<String, String> trackFormats() { return trackFormats; }
    public Map<String, String> worldFormats() { return worldFormats; }
    public boolean useItemPlaceholder() { return useItemPlaceholder; }
    public String reloadMessage() { return reloadMessage; }
    public boolean updateCheckEnabled() { return updateCheckEnabled; }
    public String updateCheckMessage() { return updateCheckMessage; }

    public boolean nameHoverEnabled() { return nameHoverEnabled; }
    public List<String> nameHoverLines() { return nameHoverLines; }
    public String nameClickSuggest() { return nameClickSuggest; }

    public boolean mentionsEnabled() { return mentionsEnabled; }
    public String mentionFormat() { return mentionFormat; }
    public boolean mentionsWithoutAt() { return mentionsWithoutAt; }
    public String mentionSound() { return mentionSound; }
    public String mentionActionbar() { return mentionActionbar; }

    public boolean linksEnabled() { return linksEnabled; }
    public String linkFormat() { return linkFormat; }
    public String linkHover() { return linkHover; }

    public boolean replacementsEnabled() { return replacementsEnabled; }
    public Map<String, String> replacements() { return replacements; }

    public NametagStyle nametagStyle() { return nametagStyle; }
    public List<String> nametagLines() { return nametagLines; }
    public List<String> nametagLinesAboveVanilla() { return nametagLinesAboveVanilla; }
    public boolean hideNametagWhenSneaking() { return hideNametagWhenSneaking; }
    public double nametagOffset() { return nametagOffset; }
    public int nametagUpdateTicks() { return nametagUpdateTicks; }

    public boolean bubblesEnabled() { return bubblesEnabled; }
    public String bubbleFormat() { return bubbleFormat; }
    /** ARGB, or null for the vanilla background. */
    public Integer bubbleBackground() { return bubbleBackground; }
    public double bubbleMinSeconds() { return bubbleMinSeconds; }
    public double bubbleSecondsPerChar() { return bubbleSecondsPerChar; }
    public double bubbleMaxSeconds() { return bubbleMaxSeconds; }
    public int bubbleLineWidth() { return bubbleLineWidth; }
    public int bubbleMaxCharacters() { return bubbleMaxCharacters; }

    public boolean tabSortByWeight() { return tabSortByWeight; }
    public boolean tabHeaderFooterEnabled() { return tabHeaderFooterEnabled; }
    public List<String> tabHeader() { return tabHeader; }
    public List<String> tabFooter() { return tabFooter; }
    public int tabUpdateTicks() { return tabUpdateTicks; }

    public boolean joinQuitEnabled() { return joinQuitEnabled; }
    public String joinFormat() { return joinFormat; }
    public String quitFormat() { return quitFormat; }
    public String firstJoinFormat() { return firstJoinFormat; }
    public Map<String, String> groupJoinFormats() { return groupJoinFormats; }
    public Map<String, String> groupQuitFormats() { return groupQuitFormats; }

    /** "#AARRGGBB" or "#RRGGBB" (opaque) to ARGB; anything else (such as "default") to null. */
    static Integer parseArgb(String value) {
        if (value == null) {
            return null;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("(?i)[0-9a-f]{6}|[0-9a-f]{8}")) {
            return null;
        }
        long argb = Long.parseLong(hex, 16);
        return (int) (hex.length() == 6 ? 0xFF000000L | argb : argb);
    }

    private Map<String, String> readSectionMap(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Collections.emptyMap();
        }

        // Insertion order matters for track formats and replacements.
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return Collections.unmodifiableMap(values);
    }
}
