package io.nightbeam.LPCF.command;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class LPCFCommand implements CommandExecutor, TabCompleter {

    private final LuckPermsChatFormatterFolia plugin;

    public LPCFCommand(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lpcf.reload")) {
            return true;
        }

        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadPluginConfig();
            Component reloadMessage = MiniMessage.miniMessage().deserialize(plugin.pluginConfig().reloadMessage());
            sender.sendMessage(reloadMessage);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
