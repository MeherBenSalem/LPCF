package io.nightbeam.LPCF.command;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.chat.ChatFormatPlaceholders;
import io.nightbeam.LPCF.chat.ChatFormatterService;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.tab.TabListService;
import io.nightbeam.LPCF.util.LuckPermsUtil;
import io.nightbeam.LPCF.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /lpcf reload | debug [player] | preview <text> | bubbles [on|off] | help */
public final class LPCFCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "<gradient:#ff7a59:#ffd166><bold>LPCF</bold></gradient> <dark_gray>»</dark_gray> ";

    private static final ThreadLocal<Boolean> DEFERRED = ThreadLocal.withInitial(() -> false);

    private final LpcfPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LPCFCommand(LpcfPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!check(sender, "lpcf.reload")) {
                    return true;
                }
                plugin.reloadPluginConfig();
                sender.sendMessage(miniMessage.deserialize(plugin.pluginConfig().reloadMessage()));
            }
            case "debug" -> {
                if (!check(sender, "lpcf.debug")) {
                    return true;
                }
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    send(sender, "<red>Usage: /" + label + " debug <player> (the player must be online)");
                    return true;
                }
                debug(sender, target);
            }
            case "preview" -> {
                if (!check(sender, "lpcf.preview")) {
                    return true;
                }
                preview(sender, label, args);
            }
            case "bubbles" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "<red>Only players have chat bubbles.");
                    return true;
                }
                if (!check(sender, "lpcf.bubbles.toggle")) {
                    return true;
                }
                boolean enable = args.length > 1
                        ? args[1].equalsIgnoreCase("on")
                        : !plugin.textTagService().bubblesEnabledFor(player);
                plugin.textTagService().setBubblesEnabled(player, enable);
                send(sender, enable ? "<green>Your chat bubbles are on." : "<yellow>Your chat bubbles are off.");
            }
            default -> help(sender, label);
        }
        return true;
    }

    // In game: /lpcf preview <message> shows your own line. From the console: /lpcf preview <player> <message>
    // shows that player's line and its JSON (hover, click and link events included).
    private void preview(CommandSender sender, String label, String[] args) {
        boolean console = !(sender instanceof Player);
        int textStart = console ? 2 : 1;
        Player target = console ? (args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null) : (Player) sender;
        if (target == null || args.length <= textStart) {
            send(sender, "<red>Usage: /" + label + (console ? " preview <player> <message>" : " preview <message>"));
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, textStart, args.length));
        onPlayerThread(sender, target, () -> {
            ChatFormatterService.Result result = plugin.chatFormatter().format(target, target.displayName(), text);
            reply(sender, Component.text("Preview (only you see this):", NamedTextColor.GRAY));
            reply(sender, result.line());
            if (console) {
                reply(sender, Component.text("JSON: " + GsonComponentSerializer.gson().serialize(result.line())));
            }
        });
    }

    // Runs now when this thread owns the player (always on Paper), otherwise on the player's region thread.
    private void onPlayerThread(CommandSender sender, Player target, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(target)) {
            task.run();
        } else {
            SchedulerUtil.run(plugin, target, () -> {
                DEFERRED.set(true);
                try {
                    task.run();
                } finally {
                    DEFERRED.remove();
                }
            });
        }
    }

    // RCON only collects what is sent before the command returns; on Folia the answer comes later, so log it.
    private void reply(CommandSender sender, Component message) {
        sender.sendMessage(message);
        if (sender instanceof RemoteConsoleCommandSender && DEFERRED.get()) {
            plugin.getLogger().info(PlainTextComponentSerializer.plainText().serialize(message));
        }
    }

    private void debug(CommandSender sender, Player target) {
        // LuckPerms data is thread-safe; the player's world and scoreboard are read on their own thread.
        onPlayerThread(sender, target, () -> {
            PluginConfig config = plugin.pluginConfig();
            CachedMetaData meta = plugin.formatter().meta(target);
            ChatFormatPlaceholders.Values values = plugin.formatter().values(target, meta);
            ChatFormatPlaceholders.Resolved format = plugin.chatFormatter().resolveFormat(values.group(), values.world(), config);
            List<Component> lines = new ArrayList<>();
            lines.add(line("Player", target.getName() + " (" + values.world() + ")"));
            lines.add(line("Primary group", values.group() + " (weight " + plugin.formatter().weight(target) + ")"));
            lines.add(line("Prefix", quote(LuckPermsUtil.prefix(meta))));
            lines.add(line("Suffix", quote(LuckPermsUtil.suffix(meta))));
            lines.add(line("username-color / message-color", quote(values.usernameColor()) + " / " + quote(values.messageColor())));
            lines.add(line("Chat format", describe(format)));
            String shown = plugin.textTagService().shownNametag(target);
            lines.add(line("Nametag", nametagMode(config) + (shown != null ? ", shown: " + shown : ", not shown")));
            lines.add(line("Bubbles", !config.bubblesEnabled() ? "disabled in config"
                    : plugin.textTagService().bubblesEnabledFor(target) ? "on" : "turned off by the player"));
            lines.add(line("Tab order", TabListService.listOrderSupported() ? "player-list order" : "team names"));
            lines.add(line("PlaceholderAPI", plugin.hasPlaceholderApi() ? "installed" : "not installed"));
            lines.add(line("Server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion()));
            Component nametagPreview = plugin.formatter().renderLines(config.nametagLines(), target, values);
            reply(sender, miniMessage.deserialize(PREFIX + "<gray>Debug for <white>" + target.getName()));
            for (Component l : lines) {
                reply(sender, l);
            }
            reply(sender, Component.text("  Nametag preview: ", NamedTextColor.GRAY).append(nametagPreview));
        });
    }

    private String nametagMode(PluginConfig config) {
        return switch (config.nametagStyle()) {
            case AUTO -> (plugin.vanillaNameHidden() ? "text display (vanilla name hidden)" : "text display above the vanilla name")
                    + " " + plugin.textTagService().placement();
            case TEAM -> "scoreboard team";
            case OFF -> "off";
        };
    }

    private static String describe(ChatFormatPlaceholders.Resolved format) {
        return switch (format.source()) {
            case GROUP -> "group-formats." + format.key();
            case TRACK -> "track-formats." + format.key();
            case WORLD -> "world-formats." + format.key();
            case DEFAULT -> "chat-format";
        };
    }

    private static String quote(String value) {
        return value == null || value.isEmpty() ? "(none)" : "\"" + value + "\"";
    }

    private static Component line(String key, String value) {
        return Component.text("  " + key + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private void help(CommandSender sender, String label) {
        send(sender, "<white>v" + plugin.getPluginMeta().getVersion() + "</white> <gray>commands:");
        if (sender.hasPermission("lpcf.reload")) {
            send(sender, "<yellow>/" + label + " reload</yellow> <gray>reload config.yml");
        }
        if (sender.hasPermission("lpcf.debug")) {
            send(sender, "<yellow>/" + label + " debug [player]</yellow> <gray>show group, prefix and format");
        }
        if (sender.hasPermission("lpcf.preview")) {
            send(sender, "<yellow>/" + label + (sender instanceof Player ? " preview <message>" : " preview <player> <message>")
                    + "</yellow> <gray>see a chat line");
        }
        if (sender.hasPermission("lpcf.bubbles.toggle")) {
            send(sender, "<yellow>/" + label + " bubbles [on|off]</yellow> <gray>your chat bubbles");
        }
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, "<red>You need the permission " + permission + ".");
        return false;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(miniMessage.deserialize(PREFIX + message));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String[] option : new String[][]{{"reload", "lpcf.reload"}, {"debug", "lpcf.debug"},
                    {"preview", "lpcf.preview"}, {"bubbles", "lpcf.bubbles.toggle"}, {"help", null}}) {
                if ((option[1] == null || sender.hasPermission(option[1])) && option[0].startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option[0]);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(player.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("bubbles")) {
            result.add("on");
            result.add("off");
        }
        return result;
    }
}
