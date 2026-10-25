package io.nightbeam.LPCF.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigTest {

    @Test
    void parsesArgbColours() {
        assertEquals(0xE8F4F5FF, PluginConfig.parseArgb("#E8F4F5FF"));
        assertEquals(0xFF112233, PluginConfig.parseArgb("#112233"));
        assertEquals(0x40000000, PluginConfig.parseArgb("40000000"));
        assertNull(PluginConfig.parseArgb("default"));
        assertNull(PluginConfig.parseArgb("#12345"));
        assertNull(PluginConfig.parseArgb(null));
    }

    @Test
    void emptyConfigFallsBackToSafeDefaults() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        assertEquals(PluginConfig.NametagStyle.AUTO, config.nametagStyle());
        assertEquals(List.of("{prefix}{name}"), config.nametagLines());
        assertTrue(config.bubblesEnabled());
        assertNull(config.bubbleBackground());
        // Features that may clash with other plugins stay off unless configured.
        assertFalse(config.tabHeaderFooterEnabled());
        assertFalse(config.joinQuitEnabled());
        assertTrue(config.updateCheckEnabled());
    }

    @Test
    void unknownNametagStyleMeansAuto() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nametags.style", "sparkly");
        assertEquals(PluginConfig.NametagStyle.AUTO, new PluginConfig(yaml).nametagStyle());
        yaml.set("nametags.style", "TEAM");
        assertEquals(PluginConfig.NametagStyle.TEAM, new PluginConfig(yaml).nametagStyle());
    }
}
