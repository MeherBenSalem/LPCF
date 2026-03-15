package io.nightbeam.LPCF;

import io.nightbeam.LPCF.command.LPCFCommand;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.listener.FoliaChatListener;
import io.nightbeam.LPCF.listener.NametagListener;
import io.nightbeam.LPCF.chat.NametagService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckPermsChatFormatterFolia extends JavaPlugin {

    private PluginConfig pluginConfig;
    private LuckPerms luckPerms;
    private boolean placeholderApiPresent;
    private NametagService nametagService;

    @Override
    public void onEnable() {
        if (!isFoliaEnvironment()) {
            getLogger().severe("Folia API was not detected. This plugin requires a Folia-compatible Paper server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            this.luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            getLogger().severe("LuckPerms API is not available yet. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.placeholderApiPresent = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.nametagService = new NametagService(this);

        registerCommand();
        registerListeners();
        getServer().getGlobalRegionScheduler().execute(this, () -> nametagService.refreshAllNametags());

        getLogger().info("LuckPermsChatFormatterFolia enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LuckPermsChatFormatterFolia disabled.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.pluginConfig.reload();
        getServer().getGlobalRegionScheduler().execute(this, () -> nametagService.refreshAllNametags());
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

    public NametagService nametagService() {
        return this.nametagService;
    }

    private void registerCommand() {
        LPCFCommand command = new LPCFCommand(this);
        if (getCommand("lpcf") != null) {
            getCommand("lpcf").setExecutor(command);
            getCommand("lpcf").setTabCompleter(command);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FoliaChatListener(this), this);
        getServer().getPluginManager().registerEvents(new NametagListener(this), this);
    }

    private boolean isFoliaEnvironment() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
