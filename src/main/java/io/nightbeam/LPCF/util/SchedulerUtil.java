package io.nightbeam.LPCF.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia-safe scheduling. Paper (and Purpur) implement the same region, entity and async schedulers as Folia since
 * 1.19.4, so one code path serves every supported server.
 */
public final class SchedulerUtil {

    private static final boolean FOLIA = detectFolia();

    private SchedulerUtil() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /** Runs on the entity's owning thread (next tick); dropped if the entity is removed first. */
    public static void run(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    public static void runLater(Plugin plugin, Entity entity, long delayTicks, Runnable task) {
        entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, Math.max(1, delayTicks));
    }

    @Nullable
    public static ScheduledTask repeat(Plugin plugin, Entity entity, long delayTicks, long periodTicks,
                                       Consumer<ScheduledTask> task, @Nullable Runnable retired) {
        return entity.getScheduler().runAtFixedRate(plugin, task, retired, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
    }

    public static void runAsyncLater(Plugin plugin, long delayMillis, Runnable task) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), delayMillis, TimeUnit.MILLISECONDS);
    }
}
