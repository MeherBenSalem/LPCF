package io.nightbeam.LPCF;

import io.nightbeam.LPCF.chat.ChatFormatterService;
import io.nightbeam.LPCF.command.LPCFCommand;
import io.nightbeam.LPCF.config.ConfigMigrator;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.display.DisplayNameService;
import io.nightbeam.LPCF.display.tag.TextTagService;
import io.nightbeam.LPCF.display.team.NametagManager;
import io.nightbeam.LPCF.format.PlayerFormatter;
import io.nightbeam.LPCF.listener.ChatListener;
import io.nightbeam.LPCF.listener.JoinQuitListener;
import io.nightbeam.LPCF.listener.PlayerJoinListener;
import io.nightbeam.LPCF.tab.TabListService;
import io.nightbeam.LPCF.util.SchedulerUtil;
import io.nightbeam.LPCF.util.UpdateChecker;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;

public final class LpcfPlugin extends JavaPlugin {

    /** The plugin's name and data folder up to 1.2.x. */
    static final String LEGACY_NAME = "LuckPermsChatFormatterFolia";

    private PluginConfig pluginConfig;
    private LuckPerms luckPerms;
    private boolean placeholderApiPresent;
    private PlayerFormatter formatter;
    private ChatFormatterService chatFormatter;
    private DisplayNameService displayNameService;
    private NametagManager nametagManager;
    private TextTagService textTagService;
    private TabListService tabListService;
    private UpdateChecker updateChecker;
    private EventSubscription<UserDataRecalculateEvent> luckPermsSubscription;

    @Override
    public void onLoad() {
        migrateLegacyDataFolder();
    }

    @Override
    public void onEnable() {
        try {
            this.luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            getLogger().severe("LuckPerms API is not available yet. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin(LEGACY_NAME) != null) {
            getLogger().warning("The old " + LEGACY_NAME + " jar is still installed: delete it from plugins/, "
                    + "LPCF replaces it (chat would be formatted twice).");
        }

        this.placeholderApiPresent = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        loadConfiguration();

        new Metrics(this, 33654);

        this.formatter = new PlayerFormatter(this);
        this.chatFormatter = new ChatFormatterService(this);
        this.nametagManager = new NametagManager(this);
        this.textTagService = new TextTagService(this);
        this.tabListService = new TabListService(this);
        this.displayNameService = new DisplayNameService(this);
        this.updateChecker = new UpdateChecker(this);

        registerCommand();
        registerListeners();
        subscribeLuckPermsEvents();

        // After /reload or a plugin manager load, players are already online.
        for (Player player : getServer().getOnlinePlayers()) {
            SchedulerUtil.run(this, player, () -> {
                displayNameService.updateDisplayName(player);
                textTagService.start(player);
                tabListService.start(player);
            });
        }

        this.updateChecker.start();

        getLogger().info("LPCF " + getPluginMeta().getVersion() + " enabled on " + getServer().getName() + " "
                + getServer().getMinecraftVersion() + (SchedulerUtil.isFolia() ? " (Folia)" : "") + ". Nametags: "
                + describeNametags() + ". PlaceholderAPI: " + (placeholderApiPresent ? "yes" : "no") + ".");
    }

    @Override
    public void onDisable() {
        if (luckPermsSubscription != null) {
            luckPermsSubscription.close();
        }
        if (textTagService != null) {
            textTagService.stopAll();
        }
        if (tabListService != null) {
            tabListService.stopAll();
        }
        if (nametagManager != null) {
            try {
                nametagManager.reset();
            } catch (RuntimeException ex) {
                getLogger().log(Level.WARNING, "Failed to clear scoreboard nametag teams on disable.", ex);
            }
        }
        getLogger().info("LPCF disabled.");
    }

    public void reloadPluginConfig() {
        loadConfiguration();
        this.displayNameService.updateAll();
        this.textTagService.markAllDirty();
        this.tabListService.restartAll();
        if (updateChecker != null) {
            updateChecker.start();
        }
    }

    // 1.x used plugins/LuckPermsChatFormatterFolia; move it once so configs carry over.
    private void migrateLegacyDataFolder() {
        File dataFolder = getDataFolder();
        File legacy = new File(dataFolder.getParentFile(), LEGACY_NAME);
        if (dataFolder.exists() || !legacy.isDirectory()) {
            return;
        }
        try {
            Files.move(legacy.toPath(), dataFolder.toPath());
            getLogger().info("Moved plugins/" + LEGACY_NAME + " to plugins/" + dataFolder.getName() + " (the plugin is now called LPCF).");
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Could not move plugins/" + LEGACY_NAME + " to plugins/"
                    + dataFolder.getName() + "; copy your config.yml over by hand.", ex);
        }
    }

    private void loadConfiguration() {
        saveDefaultConfig();
        File file = new File(getDataFolder(), "config.yml");
        try (InputStream in = getResource("config.yml")) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            int from = ConfigMigrator.versionOf(config);
            if (from < ConfigMigrator.CURRENT_VERSION) {
                File backup = new File(getDataFolder(), "config-" + (from == 1 ? "1.x" : "v" + from) + "-backup.yml");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            List<String> changed = ConfigMigrator.migrate(config, defaults);
            if (!changed.isEmpty()) {
                config.save(file);
                getLogger().info("Updated config.yml (version " + from + " to " + ConfigMigrator.CURRENT_VERSION
                        + "): added " + changed.size() + " new setting(s); your values were kept"
                        + (from < ConfigMigrator.CURRENT_VERSION ? " and the old file is in config-1.x-backup.yml." : "."));
            }
        } catch (IOException | InvalidConfigurationException | RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Could not read or upgrade config.yml; using what could be read.", ex);
        }
        reloadConfig();
        this.pluginConfig = new PluginConfig(getConfig());
    }

    private String describeNametags() {
        return switch (pluginConfig.nametagStyle()) {
            case AUTO -> "text displays, " + textTagService.placement();
            case TEAM -> SchedulerUtil.isFolia() ? "scoreboard teams (unavailable on Folia)" : "scoreboard teams";
            case OFF -> "off";
        };
    }

    /**
     * True where the vanilla name is hidden under the text-display nametag: by the player's scoreboard team (Paper,
     * Purpur), or on Folia by the display riding the player (clients never draw the name of an entity carrying a
     * passenger).
     */
    public boolean vanillaNameHidden() {
        return pluginConfig.nametagStyle() == PluginConfig.NametagStyle.AUTO
                && (SchedulerUtil.isFolia() || (nametagManager != null && nametagManager.teamsSupported()));
    }

    public PluginConfig pluginConfig() {
        return this.pluginConfig;
    }

    public LuckPerms luckPerms() {
        return this.luckPerms;
    }

    public boolean hasPlaceholderApi() {
        return this.placeholderApiPresent;
    }

    public PlayerFormatter formatter() {
        return this.formatter;
    }

    public ChatFormatterService chatFormatter() {
        return this.chatFormatter;
    }

    public DisplayNameService displayNameService() {
        return this.displayNameService;
    }

    public NametagManager nametagManager() {
        return this.nametagManager;
    }

    public TextTagService textTagService() {
        return this.textTagService;
    }

    public TabListService tabListService() {
        return this.tabListService;
    }

    private void registerCommand() {
        LPCFCommand command = new LPCFCommand(this);
        PluginCommand pluginCommand = getCommand("lpcf");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(textTagService, this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
    }

    private void subscribeLuckPermsEvents() {
        this.luckPermsSubscription = luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class, event -> {
            Player player = getServer().getPlayer(event.getUser().getUniqueId());
            if (player != null && player.isOnline()) {
                SchedulerUtil.run(this, player, () -> {
                    try {
                        displayNameService.updateDisplayName(player);
                    } catch (RuntimeException ex) {
                        getLogger().log(
                                Level.WARNING,
                                "Failed to refresh display name after LuckPerms update for " + player.getName(),
                                ex
                        );
                    }
                });
            }
        });
    }
}
