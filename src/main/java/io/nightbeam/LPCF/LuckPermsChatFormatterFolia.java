package io.nightbeam.LPCF;

import io.nightbeam.LPCF.command.LPCFCommand;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.display.DisplayNameService;
import io.nightbeam.LPCF.listener.FoliaChatListener;
import io.nightbeam.LPCF.listener.PlayerJoinListener;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckPermsChatFormatterFolia extends JavaPlugin {

    private PluginConfig pluginConfig;
    private LuckPerms luckPerms;
    private boolean placeholderApiPresent;
    private DisplayNameService displayNameService;
    private io.nightbeam.LPCF.display.team.NametagManager nametagManager;
    private EventSubscription<UserDataRecalculateEvent> luckPermsSubscription;

    @Override
    public void onEnable() {
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

        this.nametagManager = new io.nightbeam.LPCF.display.team.NametagManager(this);
        this.displayNameService = new DisplayNameService(this);

        registerCommand();
        registerListeners();
        subscribeLuckPermsEvents();

        getLogger().info("LuckPermsChatFormatterFolia enabled.");
    }

    @Override
    public void onDisable() {
        if (luckPermsSubscription != null) {
            luckPermsSubscription.close();
        }
        if (nametagManager != null) {
            nametagManager.reset();
        }
        getLogger().info("LuckPermsChatFormatterFolia disabled.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.pluginConfig.reload();
        this.displayNameService.updateAll();
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

    public DisplayNameService displayNameService() {
        return this.displayNameService;
    }

    public io.nightbeam.LPCF.display.team.NametagManager nametagManager() {
        return this.nametagManager;
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
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }

    private void subscribeLuckPermsEvents() {
        this.luckPermsSubscription = luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class, event -> {
            Player player = getServer().getPlayer(event.getUser().getUniqueId());
            if (player != null && player.isOnline()) {
                // Schedule on the player's region for Folia compatibility
                player.getScheduler().run(this, scheduledTask -> displayNameService.updateDisplayName(player), null);
            }
        });
    }
}
