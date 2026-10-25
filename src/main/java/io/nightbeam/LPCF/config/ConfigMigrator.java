package io.nightbeam.LPCF.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Upgrades a config.yml in place. Old keys are never renamed or removed; missing keys are added from the defaults
 * together with their comments. Kept free of server state so it can be unit tested.
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 2;
    static final String VERSION_KEY = "config-version";

    private ConfigMigrator() {
    }

    public static int versionOf(ConfigurationSection config) {
        return config.getInt(VERSION_KEY, 1);
    }

    /** @return the paths that were added or changed (empty when the file is already current) */
    public static List<String> migrate(YamlConfiguration config, YamlConfiguration defaults) {
        int from = versionOf(config);
        Set<String> before = new HashSet<>(config.getKeys(true));
        List<String> changed = new ArrayList<>();

        if (from < 2) {
            // 1.x showed 'nametag-format' above the head through scoreboard teams; keep showing the same thing,
            // now as a text display with full colours.
            String nametagFormat = config.getString("nametag-format");
            if (nametagFormat != null && !config.isSet("nametags.lines")) {
                config.set("nametags.lines", Collections.singletonList(nametagFormat));
                changed.add("nametags.lines");
            }
            // An existing server may already use another plugin for these; start them disabled.
            if (!config.isSet("tab.header-footer.enabled")) {
                config.set("tab.header-footer.enabled", false);
                changed.add("tab.header-footer.enabled");
            }
            if (!config.isSet("join-quit.enabled")) {
                config.set("join-quit.enabled", false);
                changed.add("join-quit.enabled");
            }
        }

        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || config.isSet(path)) {
                continue;
            }
            String userMap = userMapOf(path);
            if (userMap != null && before.contains(userMap)) {
                continue;
            }
            config.set(path, defaults.get(path));
            changed.add(path);
        }
        copyComments(config, defaults, changed, before);

        if (from != CURRENT_VERSION) {
            config.set(VERSION_KEY, CURRENT_VERSION);
            changed.add(VERSION_KEY);
        }
        return changed;
    }

    private static final String[] USER_MAPS = {"replacements.list", "group-formats", "track-formats", "world-formats",
            "join-quit.group-join", "join-quit.group-quit"};

    // Entries of these maps belong to the user: once the map exists, deleted default entries are not re-added.
    private static String userMapOf(String path) {
        for (String map : USER_MAPS) {
            if (path.startsWith(map + ".")) {
                return map;
            }
        }
        return null;
    }

    private static void copyComments(YamlConfiguration config, YamlConfiguration defaults,
                                     List<String> changed, Set<String> before) {
        try {
            for (String path : changed) {
                applyComments(config, defaults, path);
                // A section created by this migration gets the defaults' explanation too.
                for (int dot = path.indexOf('.'); dot > 0; dot = path.indexOf('.', dot + 1)) {
                    String section = path.substring(0, dot);
                    if (!before.contains(section)) {
                        applyComments(config, defaults, section);
                    }
                }
            }
        } catch (NoSuchMethodError ignored) {
            // Comment API missing: values are still migrated, only without their explanations.
        }
    }

    private static void applyComments(YamlConfiguration config, YamlConfiguration defaults, String path) {
        List<String> comments = defaults.getComments(path);
        if (!comments.isEmpty() && config.getComments(path).isEmpty()) {
            config.setComments(path, comments);
        }
        List<String> inline = defaults.getInlineComments(path);
        if (!inline.isEmpty() && config.getInlineComments(path).isEmpty()) {
            config.setInlineComments(path, inline);
        }
    }
}
