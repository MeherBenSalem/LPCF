package io.nightbeam.LPCF.util;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import io.nightbeam.LPCF.config.PluginConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker implements Listener {

    private static final URI MODRINTH_VERSIONS = URI.create(
            "https://api.modrinth.com/v2/project/cq7XqCTD/version?loaders=%5B%22paper%22%5D&version_type=release"
    );
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

    private final LuckPermsChatFormatterFolia plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private volatile String latestVersion;

    public UpdateChecker(LuckPermsChatFormatterFolia plugin) {
        this.plugin = plugin;
    }

    public void start() {
        PluginConfig config = plugin.pluginConfig();
        if (!config.updateCheckEnabled()) {
            return;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(MODRINTH_VERSIONS)
                        .header("User-Agent", "LuckPermsChatFormatterFolia/" + plugin.getDescription().getVersion())
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("Update check failed: HTTP " + response.statusCode());
                    return;
                }

                String remoteVersion = parseLatestVersion(response.body());
                if (remoteVersion == null || remoteVersion.isEmpty()) {
                    plugin.getLogger().warning("Update check failed: could not parse Modrinth response.");
                    return;
                }

                String currentVersion = plugin.getDescription().getVersion();
                if (compareVersions(currentVersion, remoteVersion) < 0) {
                    latestVersion = remoteVersion;
                    plugin.getLogger().warning(
                            "A new LPCF version is available: " + currentVersion + " -> " + remoteVersion
                    );
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Update check failed: " + ex.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PluginConfig config = plugin.pluginConfig();
        if (!config.updateCheckEnabled()) {
            return;
        }

        String remoteVersion = latestVersion;
        if (remoteVersion == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("lpcf.reload")) {
            return;
        }

        String currentVersion = plugin.getDescription().getVersion();
        if (compareVersions(currentVersion, remoteVersion) >= 0) {
            return;
        }

        String message = config.updateCheckMessage()
                .replace("{current}", currentVersion)
                .replace("{latest}", remoteVersion);
        player.sendMessage(miniMessage.deserialize(MiniMessageUtil.normalize(message)));
    }

    private static String parseLatestVersion(String body) {
        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    static int compareVersions(String current, String latest) {
        int[] currentParts = parseVersionParts(current);
        int[] latestParts = parseVersionParts(latest);
        int length = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? currentParts[i] : 0;
            int latestPart = i < latestParts.length ? latestParts[i] : 0;
            if (currentPart != latestPart) {
                return Integer.compare(currentPart, latestPart);
            }
        }
        return 0;
    }

    private static int[] parseVersionParts(String version) {
        String base = version.split("-", 2)[0];
        String[] segments = base.split("\\.");
        int[] parts = new int[segments.length];

        for (int i = 0; i < segments.length; i++) {
            String digits = segments[i].replaceAll("[^0-9].*", "");
            parts[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return parts;
    }

    public String latestVersion() {
        return latestVersion;
    }

}
