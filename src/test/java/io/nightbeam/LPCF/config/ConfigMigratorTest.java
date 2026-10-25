package io.nightbeam.LPCF.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    // The default config.yml shipped with 1.2.5, edited the way a server owner would.
    private static final String CONFIG_1_2_5 = """
            # my server
            chat-format: "{prefix}{name} > {message}"
            nametag-format: "<red>{prefix}{name}"
            group-formats:
              admin: "<red>[A] {name}: {message}"
            track-formats:
            use-item-placeholder: false
            reload-message: "<green>ok"
            update-check:
              enabled: false
              message: "new {latest}"
            """;

    private static YamlConfiguration defaults() throws IOException, InvalidConfigurationException {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = ConfigMigratorTest.class.getResourceAsStream("/config.yml")) {
            defaults.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return defaults;
    }

    @Test
    void upgradesA125ConfigKeepingEveryValue() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(CONFIG_1_2_5);
        List<String> changed = ConfigMigrator.migrate(config, defaults());

        assertFalse(changed.isEmpty());
        assertEquals(2, ConfigMigrator.versionOf(config));
        assertEquals("{prefix}{name} > {message}", config.getString("chat-format"));
        assertEquals("<red>{prefix}{name}", config.getString("nametag-format"));
        assertEquals("<red>[A] {name}: {message}", config.getString("group-formats.admin"));
        assertFalse(config.getBoolean("use-item-placeholder"));
        assertFalse(config.getBoolean("update-check.enabled"));
        assertEquals("new {latest}", config.getString("update-check.message"));

        // The head shows what the old team nametag showed.
        assertEquals(List.of("<red>{prefix}{name}"), config.getStringList("nametags.lines"));
        // Features another plugin may already provide start disabled on upgrades.
        assertFalse(config.getBoolean("tab.header-footer.enabled"));
        assertFalse(config.getBoolean("join-quit.enabled"));
        // Everything else arrives with its default.
        assertEquals("auto", config.getString("nametags.style"));
        assertTrue(config.getBoolean("bubbles.enabled"));
        assertTrue(config.getBoolean("mentions.enabled"));
        assertEquals("<red>❤</red>", config.getString("replacements.list.<3"));
        assertFalse(config.getStringList("tab.header-footer.header").isEmpty());
        // New sections come with their explanation.
        assertFalse(config.getComments("bubbles").isEmpty());
    }

    @Test
    void savedUpgradeLoadsBackTheSame() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(CONFIG_1_2_5);
        ConfigMigrator.migrate(config, defaults());
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        assertTrue(ConfigMigrator.migrate(reloaded, defaults()).isEmpty());
        assertEquals("{prefix}{name} > {message}", reloaded.getString("chat-format"));
    }

    @Test
    void currentConfigIsLeftAlone() throws Exception {
        YamlConfiguration config = defaults();
        assertTrue(ConfigMigrator.migrate(config, defaults()).isEmpty());
        assertTrue(config.getBoolean("tab.header-footer.enabled"));
        assertTrue(config.getBoolean("join-quit.enabled"));
    }

    @Test
    void deletedReplacementsAreNotReAdded() throws Exception {
        YamlConfiguration config = defaults();
        config.set("replacements.list", null);
        config.createSection("replacements.list").set(":wave:", "o/");
        assertTrue(ConfigMigrator.migrate(config, defaults()).isEmpty());
        assertEquals(1, config.getConfigurationSection("replacements.list").getKeys(false).size());
    }

    @Test
    void fileWithoutVersionIsVersionOne() {
        assertEquals(1, ConfigMigrator.versionOf(new YamlConfiguration()));
    }
}
