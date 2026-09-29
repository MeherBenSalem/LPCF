package io.nightbeam.LPCF.display.team;

import io.nightbeam.LPCF.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies nametag prefixes through Bukkit scoreboard teams when the server implements them.
 * Folia (and some forks) throw {@link UnsupportedOperationException} from
 * {@link Scoreboard#registerNewTeam(String)}; this access never lets that escape to event handlers.
 */
public final class ScoreboardTeamAccess {

    static final String UNSUPPORTED_MESSAGE =
            "Bukkit scoreboard teams are unavailable on this server. "
                    + "Tab-list names will still be formatted; above-head nametag prefixes cannot be applied "
                    + "because CraftScoreboard.registerNewTeam is not implemented "
                    + "(Folia throws UnsupportedOperationException).";

    private final Supplier<Scoreboard> scoreboard;
    private final Logger logger;
    private final AtomicBoolean supported;
    private final AtomicBoolean loggedUnsupported = new AtomicBoolean(false);

    public ScoreboardTeamAccess(Supplier<Scoreboard> scoreboard, @Nullable Logger logger, boolean initiallySupported) {
        this.scoreboard = Objects.requireNonNull(scoreboard, "scoreboard");
        this.logger = logger;
        this.supported = new AtomicBoolean(initiallySupported);
    }

    public static ScoreboardTeamAccess forServer(@Nullable Logger logger) {
        return new ScoreboardTeamAccess(ScoreboardTeamAccess::mainScoreboard, logger, !SchedulerUtil.isFolia());
    }

    public boolean isSupported() {
        return supported.get();
    }

    public void ensureTeam(String name, Consumer<Team> configure) {
        if (!beginMutation()) {
            return;
        }
        try {
            Scoreboard board = scoreboard.get();
            if (board == null) {
                return;
            }
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            configure.accept(team);
        } catch (UnsupportedOperationException ex) {
            disable(ex);
        }
    }

    public void addEntry(String teamName, String playerName, Consumer<Team> configure) {
        if (!beginMutation()) {
            return;
        }
        try {
            Scoreboard board = scoreboard.get();
            if (board == null) {
                return;
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                configure.accept(team);
            }
            if (team != null && !team.hasEntry(playerName)) {
                team.addEntry(playerName);
            }
        } catch (UnsupportedOperationException ex) {
            disable(ex);
        }
    }

    /**
     * @return {@code true} if the Bukkit team is gone or should be treated as gone
     */
    public boolean removeEntry(String teamName, String playerName) {
        if (!beginMutation()) {
            return true;
        }
        try {
            Scoreboard board = scoreboard.get();
            if (board == null) {
                return true;
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                return true;
            }
            team.removeEntry(playerName);
            if (team.getEntries().isEmpty()) {
                team.unregister();
                return true;
            }
            return false;
        } catch (UnsupportedOperationException ex) {
            disable(ex);
            return true;
        }
    }

    public void unregister(String teamName) {
        if (!beginMutation()) {
            return;
        }
        try {
            Scoreboard board = scoreboard.get();
            if (board == null) {
                return;
            }
            Team team = board.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        } catch (UnsupportedOperationException ex) {
            disable(ex);
        }
    }

    private boolean beginMutation() {
        if (supported.get()) {
            return true;
        }
        noteUnsupported();
        return false;
    }

    private void disable(UnsupportedOperationException ex) {
        supported.set(false);
        if (loggedUnsupported.compareAndSet(false, true) && logger != null) {
            logger.log(Level.WARNING, UNSUPPORTED_MESSAGE, ex);
        }
    }

    private void noteUnsupported() {
        if (loggedUnsupported.compareAndSet(false, true) && logger != null) {
            logger.warning(UNSUPPORTED_MESSAGE);
        }
    }

    @Nullable
    private static Scoreboard mainScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        return manager == null ? null : manager.getMainScoreboard();
    }
}
