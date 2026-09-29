package io.nightbeam.LPCF.display.team;

import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardTeamAccessTest {

    @Test
    void registerNewTeamUnsupportedDoesNotEscapeAndDisablesFurtherMutations() {
        AtomicInteger registerCalls = new AtomicInteger();
        Scoreboard board = throwingRegisterBoard(registerCalls);
        ScoreboardTeamAccess access = new ScoreboardTeamAccess(() -> board, Logger.getAnonymousLogger(), true);

        assertDoesNotThrow(() -> access.ensureTeam("L0001abc", team -> team.prefix(Component.text("Admin"))));
        assertFalse(access.isSupported());
        assertEquals(1, registerCalls.get());

        assertDoesNotThrow(() -> access.addEntry("L0001abc", "Steve", team -> {
        }));
        assertDoesNotThrow(() -> access.removeEntry("L0001abc", "Steve"));
        assertDoesNotThrow(() -> access.unregister("L0001abc"));
        assertEquals(1, registerCalls.get());
    }

    @Test
    void skippedFoliaPathNeverTouchesRegisterNewTeam() {
        AtomicInteger registerCalls = new AtomicInteger();
        Scoreboard board = throwingRegisterBoard(registerCalls);
        ScoreboardTeamAccess access = new ScoreboardTeamAccess(() -> board, null, false);

        assertFalse(access.isSupported());
        assertDoesNotThrow(() -> access.ensureTeam("L0001abc", team -> {
            throw new AssertionError("configure must not run when teams are unsupported");
        }));
        assertEquals(0, registerCalls.get());
    }

    @Test
    void workingScoreboardRegistersTeamAppliesPrefixAndAddsEntry() {
        MemoryScoreboard memory = new MemoryScoreboard();
        ScoreboardTeamAccess access = new ScoreboardTeamAccess(memory::asScoreboard, null, true);

        access.ensureTeam("L0005hash", team -> {
            team.prefix(Component.text("[Admin] "));
            team.suffix(Component.text(" *"));
        });
        access.addEntry("L0005hash", "Steve", team -> {
            throw new AssertionError("existing team should not be reconfigured");
        });

        assertTrue(access.isSupported());
        MemoryTeam team = memory.teams.get("L0005hash");
        assertEquals(Component.text("[Admin] "), team.prefix);
        assertEquals(Component.text(" *"), team.suffix);
        assertTrue(team.entries.contains("Steve"));
    }

    @Test
    void removeLastEntryUnregistersTeam() {
        MemoryScoreboard memory = new MemoryScoreboard();
        ScoreboardTeamAccess access = new ScoreboardTeamAccess(memory::asScoreboard, null, true);

        access.addEntry("teamA", "Steve", team -> {
        });
        assertTrue(memory.teams.containsKey("teamA"));
        assertTrue(access.removeEntry("teamA", "Steve"));
        assertFalse(memory.teams.containsKey("teamA"));
    }

    @Test
    void nullScoreboardIsIgnoredWithoutThrowing() {
        ScoreboardTeamAccess access = new ScoreboardTeamAccess(() -> null, null, true);

        assertDoesNotThrow(() -> access.ensureTeam("x", team -> {
        }));
        assertDoesNotThrow(() -> access.addEntry("x", "Steve", team -> {
        }));
        assertTrue(access.removeEntry("x", "Steve"));
        assertDoesNotThrow(() -> access.unregister("x"));
        assertTrue(access.isSupported());
    }

    @Test
    void teamMutationUnsupportedDisablesWithoutThrowing() {
        Scoreboard board = (Scoreboard) Proxy.newProxyInstance(
                Scoreboard.class.getClassLoader(),
                new Class<?>[]{Scoreboard.class},
                (proxy, method, args) -> {
                    if ("getTeam".equals(method.getName())) {
                        return throwingPrefixTeam();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        ScoreboardTeamAccess access = new ScoreboardTeamAccess(() -> board, null, true);

        assertDoesNotThrow(() -> access.ensureTeam("L0001abc", team -> team.prefix(Component.text("Admin"))));
        assertFalse(access.isSupported());
    }

    private static Team throwingPrefixTeam() {
        return (Team) Proxy.newProxyInstance(
                Team.class.getClassLoader(),
                new Class<?>[]{Team.class},
                (proxy, method, args) -> {
                    if ("prefix".equals(method.getName()) && args != null && args.length == 1) {
                        throw new UnsupportedOperationException();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Scoreboard throwingRegisterBoard(AtomicInteger registerCalls) {
        return (Scoreboard) Proxy.newProxyInstance(
                Scoreboard.class.getClassLoader(),
                new Class<?>[]{Scoreboard.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getTeam".equals(name)) {
                        return null;
                    }
                    if ("registerNewTeam".equals(name)) {
                        registerCalls.incrementAndGet();
                        throw new UnsupportedOperationException();
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "ThrowingScoreboard";
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type.isPrimitive()) {
            return 0;
        }
        return null;
    }

    private static final class MemoryScoreboard {
        private final Map<String, MemoryTeam> teams = new LinkedHashMap<>();

        Scoreboard asScoreboard() {
            return (Scoreboard) Proxy.newProxyInstance(
                    Scoreboard.class.getClassLoader(),
                    new Class<?>[]{Scoreboard.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTeam" -> {
                            MemoryTeam team = teams.get((String) args[0]);
                            yield team == null ? null : team.asTeam();
                        }
                        case "registerNewTeam" -> {
                            String name = (String) args[0];
                            if (teams.containsKey(name)) {
                                throw new IllegalArgumentException("team exists");
                            }
                            MemoryTeam created = new MemoryTeam(name, teams);
                            teams.put(name, created);
                            yield created.asTeam();
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "MemoryScoreboard";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static final class MemoryTeam {
        private final String name;
        private final Map<String, MemoryTeam> owner;
        private final Set<String> entries = new LinkedHashSet<>();
        private Component prefix = Component.empty();
        private Component suffix = Component.empty();
        private Team proxy;

        private MemoryTeam(String name, Map<String, MemoryTeam> owner) {
            this.name = name;
            this.owner = owner;
        }

        Team asTeam() {
            if (proxy != null) {
                return proxy;
            }
            proxy = (Team) Proxy.newProxyInstance(
                    Team.class.getClassLoader(),
                    new Class<?>[]{Team.class},
                    (p, method, args) -> {
                        String methodName = method.getName();
                        return switch (methodName) {
                            case "getName" -> name;
                            case "prefix" -> {
                                if (args != null && args.length == 1) {
                                    prefix = (Component) args[0];
                                    yield null;
                                }
                                yield prefix;
                            }
                            case "suffix" -> {
                                if (args != null && args.length == 1) {
                                    suffix = (Component) args[0];
                                    yield null;
                                }
                                yield suffix;
                            }
                            case "hasEntry" -> entries.contains((String) args[0]);
                            case "addEntry" -> entries.add((String) args[0]);
                            case "removeEntry" -> entries.remove((String) args[0]);
                            case "getEntries" -> Set.copyOf(entries);
                            case "unregister" -> {
                                owner.remove(name);
                                yield null;
                            }
                            case "setOption", "color" -> null;
                            case "equals" -> p == args[0];
                            case "hashCode" -> System.identityHashCode(p);
                            case "toString" -> "MemoryTeam:" + name;
                            default -> defaultValue(method.getReturnType());
                        };
                    }
            );
            return proxy;
        }
    }
}
