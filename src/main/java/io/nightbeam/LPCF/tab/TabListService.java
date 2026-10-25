package io.nightbeam.LPCF.tab;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.chat.ChatFormatPlaceholders;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.util.SchedulerUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Tab-list header/footer and ordering by LuckPerms weight. */
public final class TabListService {

    // Player#setPlayerListOrder exists from Minecraft 1.21.2; older servers sort through the team names.
    private static final Method SET_LIST_ORDER = findListOrder();

    private final LpcfPlugin plugin;
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private volatile boolean orderErrorLogged;

    public TabListService(LpcfPlugin plugin) {
        this.plugin = plugin;
    }

    private static Method findListOrder() {
        try {
            return Player.class.getMethod("setPlayerListOrder", int.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    public static boolean listOrderSupported() {
        return SET_LIST_ORDER != null;
    }

    public void applyOrder(Player player) {
        if (SET_LIST_ORDER == null || !plugin.pluginConfig().tabSortByWeight()) {
            return;
        }
        try {
            SET_LIST_ORDER.invoke(player, Math.max(0, plugin.formatter().weight(player)));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!orderErrorLogged) {
                orderErrorLogged = true;
                plugin.getLogger().log(Level.WARNING, "Could not set the tab-list order", ex);
            }
        }
    }

    public void start(Player player) {
        stop(player);
        PluginConfig config = plugin.pluginConfig();
        if (!config.tabHeaderFooterEnabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        ScheduledTask task = SchedulerUtil.repeat(plugin, player, 1, config.tabUpdateTicks(), t -> send(player),
                () -> tasks.remove(id));
        if (task != null) {
            tasks.put(id, task);
        }
    }

    public void stop(Player player) {
        ScheduledTask task = tasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /** Re-reads the config for everyone (after /lpcf reload). */
    public void restartAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.run(plugin, player, () -> {
                start(player);
                applyOrder(player);
                if (!plugin.pluginConfig().tabHeaderFooterEnabled()) {
                    player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
                }
            });
        }
    }

    public void stopAll() {
        for (ScheduledTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    private void send(Player player) {
        if (!player.isOnline()) {
            return;
        }
        try {
            PluginConfig config = plugin.pluginConfig();
            ChatFormatPlaceholders.Values values = plugin.formatter().values(player);
            player.sendPlayerListHeaderAndFooter(
                    lines(config.tabHeader(), player, values),
                    lines(config.tabFooter(), player, values));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not update the tab list of " + player.getName(), ex);
        }
    }

    private Component lines(List<String> lines, Player player, ChatFormatPlaceholders.Values values) {
        List<Component> rendered = new ArrayList<>();
        for (String line : lines) {
            String filled = line
                    .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("{max-players}", String.valueOf(Bukkit.getMaxPlayers()))
                    .replace("{ping}", String.valueOf(player.getPing()));
            rendered.add(plugin.formatter().render(filled, player, values, Component.text(player.getName()),
                    player.displayName(), Component.empty(), net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.empty()));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), rendered);
    }
}
