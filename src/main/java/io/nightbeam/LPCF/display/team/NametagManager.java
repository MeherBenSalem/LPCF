package io.nightbeam.LPCF.display.team;

import io.nightbeam.LPCF.LuckPermsChatFormatterFolia;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class NametagManager {

    private final Map<String, FakeTeam> TEAMS = new ConcurrentHashMap<>();
    private final ReadWriteLock TEAMS_LOCK = new ReentrantReadWriteLock();

    private final Map<String, FakeTeam> CACHED_FAKE_TEAMS = new ConcurrentHashMap<>();

    public NametagManager(final LuckPermsChatFormatterFolia plugin) {
    }

    @Nullable
    private FakeTeam getFakeTeam(String name, int sortPriority, Component prefix, Component suffix, boolean visible) {
        return TEAMS.values().stream()
                .filter(fakeTeam -> fakeTeam.isSimilar(name, sortPriority, prefix, suffix, visible))
                .findFirst()
                .orElse(null);
    }

    private void addPlayerToTeam(String player, Component prefix, Component suffix, int sortPriority, boolean playerTag, boolean visible, NamedTextColor nameFormattingOverride) {
        String addingName;
        Player adding = Bukkit.getPlayerExact(player);
        if (adding != null) {
            addingName = adding.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(player);
            if (offline == null || offline.getName() == null) {
                return;
            }
            addingName = offline.getName();
        }

        player = addingName;

        FakeTeam previous = getFakeTeam(player);
        if (previous != null && previous.isSimilar(player, sortPriority, prefix, suffix, visible)) {
            return;
        }

        reset(player);

        FakeTeam joining;
        try {
            TEAMS_LOCK.readLock().lock();
            joining = getFakeTeam(player, sortPriority, prefix, suffix, visible);
        } finally {
            TEAMS_LOCK.readLock().unlock();
        }

        if (joining != null) {
            synchronized (joining) {
                joining.addMember(player);
                addPlayerToScoreboardTeam(joining, player);
            }
        } else {
            joining = FakeTeam.create(player, prefix, suffix, sortPriority);
            joining.setVisible(visible);
            joining.addMember(player);
            joining.setNameFormattingOverride(nameFormattingOverride);

            try {
                TEAMS_LOCK.writeLock().lock();
                TEAMS.put(joining.getName(), joining);
            } finally {
                TEAMS_LOCK.writeLock().unlock();
            }

            synchronized (joining) {
                registerScoreboardTeam(joining);
                addPlayerToScoreboardTeam(joining, player);
            }
        }

        cache(player, joining);
    }

    public FakeTeam reset(String player) {
        return reset(player, decache(player));
    }

    private FakeTeam reset(String player, FakeTeam fakeTeam) {
        if (fakeTeam == null) {
            return null;
        }

        boolean delete = false;
        synchronized (fakeTeam) {
            if (fakeTeam.removeMember(player)) {
                Player removing = Bukkit.getPlayerExact(player);
                if (removing != null) {
                    delete = removePlayerFromScoreboardTeam(fakeTeam, removing.getName());
                } else {
                    OfflinePlayer toRemoveOffline = Bukkit.getOfflinePlayerIfCached(player);
                    if (toRemoveOffline == null || toRemoveOffline.getName() == null) {
                        return fakeTeam;
                    }
                    delete = removePlayerFromScoreboardTeam(fakeTeam, toRemoveOffline.getName());
                }
            }
        }

        if (delete) {
            try {
                TEAMS_LOCK.writeLock().lock();
                TEAMS.remove(fakeTeam.getName());
            } finally {
                TEAMS_LOCK.writeLock().unlock();
            }
            unregisterScoreboardTeam(fakeTeam);
        }

        return fakeTeam;
    }

    private FakeTeam decache(String player) {
        return CACHED_FAKE_TEAMS.remove(player);
    }

    public FakeTeam getFakeTeam(String player) {
        return CACHED_FAKE_TEAMS.get(player);
    }

    private void cache(String player, FakeTeam fakeTeam) {
        CACHED_FAKE_TEAMS.put(player, fakeTeam);
    }

    public void setNametag(String player, Component prefix, Component suffix) {
        setNametag(player, prefix, suffix, -1);
    }

    public void setNametag(String player, Component prefix, Component suffix, boolean visible) {
        setNametag(player, prefix, suffix, -1, false, visible, null);
    }

    public void setNametag(String player, Component prefix, Component suffix, int sortPriority) {
        setNametag(player, prefix, suffix, sortPriority, false, true, null);
    }

    public void setNametag(String player, Component prefix, Component suffix, int sortPriority, boolean playerTag, boolean visible, NamedTextColor nameFormattingOverride) {
        addPlayerToTeam(
                player,
                prefix != null ? prefix : Component.empty(),
                suffix != null ? suffix : Component.empty(),
                sortPriority,
                playerTag,
                visible,
                nameFormattingOverride
        );
    }

    public void sendTeams(Player player) {
        // Main scoreboard teams are global; nothing to resend per player.
    }

    public void reset() {
        try {
            TEAMS_LOCK.writeLock().lock();
            for (FakeTeam fakeTeam : TEAMS.values()) {
                synchronized (fakeTeam) {
                    unregisterScoreboardTeam(fakeTeam);
                }
            }
            CACHED_FAKE_TEAMS.clear();
            TEAMS.clear();
        } finally {
            TEAMS_LOCK.writeLock().unlock();
        }
    }

    private Scoreboard mainScoreboard() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private void registerScoreboardTeam(FakeTeam fakeTeam) {
        Scoreboard board = mainScoreboard();
        Team team = board.getTeam(fakeTeam.getName());
        if (team == null) {
            team = board.registerNewTeam(fakeTeam.getName());
        }
        applyFakeTeamToScoreboard(fakeTeam, team);
    }

    private void applyFakeTeamToScoreboard(FakeTeam fakeTeam, Team team) {
        team.prefix(fakeTeam.getPrefix());
        team.suffix(fakeTeam.getSuffix());
        team.setOption(
                Team.Option.NAME_TAG_VISIBILITY,
                fakeTeam.isVisible() ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER
        );
        NamedTextColor color = fakeTeam.resolvedNameColor();
        if (color != null) {
            team.color(color);
        }
    }

    private void addPlayerToScoreboardTeam(FakeTeam fakeTeam, String playerName) {
        Scoreboard board = mainScoreboard();
        Team team = board.getTeam(fakeTeam.getName());
        if (team == null) {
            registerScoreboardTeam(fakeTeam);
            team = board.getTeam(fakeTeam.getName());
        }
        if (team != null && !team.hasEntry(playerName)) {
            team.addEntry(playerName);
        }
    }

    private boolean removePlayerFromScoreboardTeam(FakeTeam fakeTeam, String playerName) {
        Scoreboard board = mainScoreboard();
        Team team = board.getTeam(fakeTeam.getName());
        if (team != null) {
            team.removeEntry(playerName);
            if (team.getEntries().isEmpty()) {
                team.unregister();
                return true;
            }
        }
        return fakeTeam.getMembers().isEmpty();
    }

    private void unregisterScoreboardTeam(FakeTeam fakeTeam) {
        Scoreboard board = mainScoreboard();
        Team team = board.getTeam(fakeTeam.getName());
        if (team != null) {
            team.unregister();
        }
    }

}
