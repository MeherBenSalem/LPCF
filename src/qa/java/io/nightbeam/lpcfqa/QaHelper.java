package io.nightbeam.lpcfqa;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * QA only (never shipped). Answers tools/qa in the server log, the same way on Paper, Purpur and Folia (Folia
 * disables /data and cross-region selectors):
 *
 * <ul>
 *   <li>{@code /qatp <player> <world> <x> <y> <z>}: teleports through the Bukkit API, the way /home, /spawn and /tpa
 *   plugins do. Logs {@code QA-TP <player> result=<true|false>}.</li>
 *   <li>{@code /qainfo <player>}: logs the player's world and position and the LPCF displays around them.</li>
 *   <li>{@code /qaground <player> <x> <z> [yaw] [pitch]}: stands the player on the highest block there (for the
 *   marketing screenshots) and logs that block.</li>
 * </ul>
 */
public final class QaHelper extends JavaPlugin {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        if (player == null) {
            getLogger().info("QA-" + command.getName().toUpperCase() + " " + (args.length > 0 ? args[0] : "?") + " missing");
            return true;
        }
        if (command.getName().equals("qatp") && args.length == 5) {
            World world = Bukkit.getWorld(args[1]);
            Location target = new Location(world, Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
            // teleportAsync is the Folia-safe API and exists on Paper too.
            player.teleportAsync(target).thenAccept(ok -> getLogger().info("QA-TP " + player.getName() + " result=" + ok));
            return true;
        }
        if (command.getName().equals("qaground") && args.length >= 3) {
            // Stand the player on the highest block at x, z (logged with that block, so water can be avoided).
            double x = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);
            float yaw = args.length > 3 ? Float.parseFloat(args[3]) : 0;
            float pitch = args.length > 4 ? Float.parseFloat(args[4]) : 0;
            World world = player.getWorld();
            Bukkit.getRegionScheduler().execute(this, world, (int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, () -> {
                org.bukkit.block.Block top = world.getHighestBlockAt((int) Math.floor(x), (int) Math.floor(z));
                Location target = new Location(world, x, top.getY() + 1, z, yaw, pitch);
                player.teleportAsync(target).thenAccept(ok -> getLogger().info(
                        "QA-GROUND " + player.getName() + " y=" + (top.getY() + 1) + " block=" + top.getType().name().toLowerCase()));
            });
            return true;
        }
        if (command.getName().equals("qainfo")) {
            player.getScheduler().run(this, task -> getLogger().info(info(player)), null);
            return true;
        }
        return false;
    }

    private String info(Player player) {
        Location at = player.getLocation();
        StringBuilder out = new StringBuilder("QA-INFO ").append(player.getName())
                .append(" world=").append(at.getWorld().getName())
                .append(String.format(" pos=%.1f,%.1f,%.1f", at.getX(), at.getY(), at.getZ()))
                .append(" passengers=").append(player.getPassengers().size());
        int near = 0;
        for (Entity entity : player.getNearbyEntities(6, 6, 6)) {
            if (!(entity instanceof TextDisplay display) || !display.getScoreboardTags().contains("lpcf-tag")) {
                continue;
            }
            near++;
            String owner = display.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey("lpcf", "owner"), org.bukkit.persistence.PersistentDataType.STRING);
            if (!player.getUniqueId().toString().equals(owner)) {
                continue;
            }
            String kind = display.getScoreboardTags().contains("lpcf-bubble") ? "bubble" : "nametag";
            double dy = display.getLocation().getY() - at.getY();
            double dxz = Math.hypot(display.getLocation().getX() - at.getX(), display.getLocation().getZ() - at.getZ());
            out.append(' ').append(kind).append("=<<").append(PlainTextComponentSerializer.plainText().serialize(display.text())
                    .replace('\n', '|')).append(">>")
                    .append(' ').append(kind).append("SeeThrough=").append(display.isSeeThrough())
                    .append(' ').append(kind).append("Riding=").append(player.getPassengers().contains(display))
                    .append(' ').append(kind).append(String.format("Offset=%.2f/%.2f", dxz, dy));
        }
        return out.append(" near=").append(near).toString();
    }
}
