package io.nightbeam.LPCF.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public final class SchedulerUtil {

    private static final boolean FOLIA = detectFolia();

    private SchedulerUtil() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static void run(JavaPlugin plugin, Player player, Runnable task) {
        if (FOLIA) {
            runFolia(plugin, player, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (FOLIA) {
            runFoliaAsync(plugin, task);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    private static void runFoliaAsync(JavaPlugin plugin, Runnable task) {
        try {
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            asyncScheduler.getClass()
                    .getMethod("runNow", org.bukkit.plugin.Plugin.class, Consumer.class)
                    .invoke(asyncScheduler, plugin, consumer);
        } catch (ReflectiveOperationException ex) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    @SuppressWarnings("unchecked")
    private static void runFolia(JavaPlugin plugin, Player player, Runnable task) {
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Consumer<Object> consumer = scheduledTask -> task.run();
            scheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class)
                    .invoke(scheduler, plugin, consumer, null);
        } catch (ReflectiveOperationException ex) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

}
