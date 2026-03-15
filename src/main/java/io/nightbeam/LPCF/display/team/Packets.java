package io.nightbeam.LPCF.display.team;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import net.minecraft.network.protocol.Packet;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;

public class Packets {
    public static void broadcast(LuckPermsChatFormatterFolia plugin, final Packet<?> packet) {
        send(plugin, Bukkit.getServer().getOnlinePlayers(), packet);
    }

    public static void send(LuckPermsChatFormatterFolia plugin, final Collection<? extends Player> players, final Packet<?> packet) {
        if (packet == null)
            return;

        for (final Player player : players) {
            player.getScheduler().run(plugin, task -> {
                ((CraftPlayer) player).getHandle().connection.send(packet);
            }, null);
        }
    }
}
