package io.nightbeam.LPCF.config;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PluginConfig {

    private final LuckPermsChatFormatterFolia plugin;

    private String chatFormat;
    private String nametagFormat;
    private Map<String, String> groupFormats;
    private Map<String, String> trackFormats;
    private boolean useItemPlaceholder;
    private String reloadMessage;

    public PluginConfig(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.chatFormat = plugin.getConfig().getString("chat-format", "{prefix}{name}<dark_gray> »<reset> {message}");
        this.nametagFormat = plugin.getConfig().getString("nametag-format", "{prefix}{name}");
        this.groupFormats = readSectionMap("group-formats");
        this.trackFormats = readSectionMap("track-formats");
        this.useItemPlaceholder = plugin.getConfig().getBoolean("use-item-placeholder", true);
        this.reloadMessage = plugin.getConfig().getString("reload-message", "<green>LPCF configuration reloaded successfully!");
    }

    public String chatFormat() {
        return chatFormat;
    }

    public String nametagFormat() {
        return nametagFormat;
    }

    public Map<String, String> groupFormats() {
        return groupFormats;
    }

    public Map<String, String> trackFormats() {
        return trackFormats;
    }

    public boolean useItemPlaceholder() {
        return useItemPlaceholder;
    }

    public String reloadMessage() {
        return reloadMessage;
    }

    private Map<String, String> readSectionMap(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<String, String> values = new HashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return Collections.unmodifiableMap(values);
    }
}
