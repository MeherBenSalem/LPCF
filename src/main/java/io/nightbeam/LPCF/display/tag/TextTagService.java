package io.nightbeam.LPCF.display.tag;

import io.nightbeam.LPCF.LpcfPlugin;
import io.nightbeam.LPCF.config.PluginConfig;
import io.nightbeam.LPCF.util.SchedulerUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Nametags and chat bubbles made of vanilla text displays.
 *
 * <p>Two ways of keeping a display above its player, chosen by server:</p>
 * <ul>
 *   <li><b>Follow</b> (Paper, Purpur): the display is moved to the top of the player's head every tick, with client
 *   interpolation where the server has it (1.20.2+). Nothing rides the player, because Paper refuses to teleport a
 *   player carrying passengers: /home, /spawn and /tpa plugins and portals keep working.</li>
 *   <li><b>Ride</b> (Folia): the display rides the player. Folia teleports players together with their passengers,
 *   and a rider always stays in its player's region, so it never needs a cross-region move. Clients never draw the
 *   name of an entity carrying a passenger, so this also hides the vanilla name where Folia has no teams to do it.</li>
 * </ul>
 *
 * <p>Every player gets a loop on their own entity scheduler (their region thread on Folia). The loop keeps the
 * displays in place after teleports, deaths and world changes, refreshes their text and hides them when the player
 * should not be seen. Displays are never saved and carry {@link #TAG}; any tagged display that loads from a chunk is
 * an orphan and removed.</p>
 */
public final class TextTagService implements Listener {

    public static final String TAG = "lpcf-tag";
    public static final String NAMETAG_TAG = "lpcf-nametag";
    public static final String BUBBLE_TAG = "lpcf-bubble";
    private static final byte SNEAK_OPACITY = (byte) 0x80;
    // Players are interpolated over 3 ticks on clients; the display follows the same way.
    private static final int FOLLOW_INTERPOLATION_TICKS = 3;
    // Further than this the display is re-created rather than slid across the map.
    private static final double JUMP_DISTANCE_SQUARED = 8 * 8;
    // Display#setTeleportDuration exists from 1.20.2.
    private static final Method SET_TELEPORT_DURATION = findTeleportDuration();

    private final LpcfPlugin plugin;
    private final NamespacedKey bubblesOffKey;
    private final NamespacedKey ownerKey;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final boolean ride;
    private final long loopPeriod;
    private final double baseLift;

    private static final class State {
        ScheduledTask task;
        long ticks;
        boolean dirty = true;
        @Nullable TextDisplay nametag;
        Component nametagText = Component.empty();
        int nametagLines;
        boolean sneakStyled;
        @Nullable TextDisplay bubble;
        @Nullable Component bubbleText;
        long bubbleUntil;
        boolean bubbleChanged;
    }

    public TextTagService(LpcfPlugin plugin) {
        this.plugin = plugin;
        this.bubblesOffKey = new NamespacedKey(plugin, "bubbles_off");
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.ride = SchedulerUtil.isFolia();
        this.loopPeriod = ride ? 2 : 1;
        this.baseLift = TagLayout.baseLift(Bukkit.getMinecraftVersion(), ride);
    }

    private static Method findTeleportDuration() {
        try {
            return Display.class.getMethod("setTeleportDuration", int.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /** How displays keep up with their player on this server, for the startup log and /lpcf debug. */
    public String placement() {
        if (ride) {
            return "riding the player (which also hides the vanilla name)";
        }
        return SET_TELEPORT_DURATION != null ? "following the player" : "following the player (no interpolation before 1.20.2)";
    }

    // ---------------------------------------------------------------------------------------------------------
    // Lifecycle

    /** Starts the player's loop. Call on the player's thread (join) or through their scheduler. */
    public void start(Player player) {
        State state = new State();
        State previous = states.put(player.getUniqueId(), state);
        if (previous != null) {
            discard(previous);
        }
        UUID id = player.getUniqueId();
        state.task = SchedulerUtil.repeat(plugin, player, 1, loopPeriod, task -> tick(player, state),
                () -> {
                    states.remove(id, state);
                    discard(state);
                });
    }

    public void stop(Player player) {
        State state = states.remove(player.getUniqueId());
        if (state != null) {
            discard(state);
        }
    }

    public void stopAll() {
        for (State state : states.values()) {
            discard(state);
        }
        states.clear();
    }

    /** Text is rebuilt on the next loop (after a LuckPerms change or a reload). */
    public void markDirty(Player player) {
        State state = states.get(player.getUniqueId());
        if (state != null) {
            state.dirty = true;
        }
    }

    public void markAllDirty() {
        for (State state : states.values()) {
            state.dirty = true;
        }
    }

    /** What the player's nametag shows right now (plain text, lines joined with " | "), or null when none is shown. */
    @Nullable
    public String shownNametag(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.nametag == null) {
            return null;
        }
        return PlainTextComponentSerializer.plainText().serialize(state.nametagText).replace("\n", " | ");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Bubbles

    public boolean bubblesEnabledFor(Player player) {
        Byte off = player.getPersistentDataContainer().get(bubblesOffKey, PersistentDataType.BYTE);
        return off == null || off == 0;
    }

    public void setBubblesEnabled(Player player, boolean enabled) {
        if (enabled) {
            player.getPersistentDataContainer().remove(bubblesOffKey);
        } else {
            player.getPersistentDataContainer().set(bubblesOffKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    /** Shows a bubble above the player. Call on the player's thread. */
    public void showBubble(Player player, Component text, int characters) {
        State state = states.get(player.getUniqueId());
        PluginConfig config = plugin.pluginConfig();
        if (state == null || !config.bubblesEnabled() || !bubblesEnabledFor(player)) {
            return;
        }
        double seconds = TagLayout.bubbleSeconds(characters, config.bubbleMinSeconds(),
                config.bubbleSecondsPerChar(), config.bubbleMaxSeconds());
        state.bubbleText = text;
        state.bubbleUntil = System.currentTimeMillis() + (long) (seconds * 1000);
        state.bubbleChanged = true;
        tick(player, state);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The loop

    private void tick(Player player, State state) {
        if (!player.isOnline()) {
            return;
        }
        try {
            PluginConfig config = plugin.pluginConfig();
            boolean show = canShow(player);
            boolean vanillaVisible = !plugin.vanillaNameHidden();

            if (config.nametagStyle() == PluginConfig.NametagStyle.AUTO && show) {
                long refreshEvery = Math.max(1, config.nametagUpdateTicks() / loopPeriod);
                if (state.dirty || state.ticks % refreshEvery == 0) {
                    state.dirty = false;
                    state.nametagText = plugin.formatter().renderLines(
                            vanillaVisible ? config.nametagLinesAboveVanilla() : config.nametagLines(),
                            player, plugin.formatter().values(player));
                    state.nametagLines = TagLayout.lineCount(PlainTextComponentSerializer.plainText().serialize(state.nametagText));
                }
                if (state.nametagLines > 0) {
                    double lift = TagLayout.nametagLift(baseLift, config.nametagOffset(), vanillaVisible);
                    state.nametag = keepUp(player, state.nametag, 1000, NAMETAG_TAG);
                    if (state.nametag != null) {
                        setLift(state.nametag, lift);
                        updateText(state.nametag, state.nametagText);
                        applySneak(player, state, config);
                    }
                } else {
                    state.nametag = remove(state.nametag);
                }
            } else {
                state.nametag = remove(state.nametag);
                state.nametagLines = 0;
            }

            boolean bubbleActive = state.bubbleText != null && System.currentTimeMillis() < state.bubbleUntil
                    && show && config.bubblesEnabled();
            if (bubbleActive) {
                int nametagLines = state.nametag != null ? state.nametagLines : 0;
                double lift = TagLayout.bubbleLift(baseLift, config.nametagOffset(), vanillaVisible, nametagLines);
                TextDisplay before = state.bubble;
                state.bubble = keepUp(player, state.bubble, config.bubbleLineWidth(), BUBBLE_TAG);
                if (state.bubble != null) {
                    if (state.bubbleChanged || state.bubble != before) {
                        state.bubble.text(state.bubbleText);
                        applyBackground(state.bubble, config.bubbleBackground());
                        state.bubbleChanged = false;
                    }
                    setLift(state.bubble, lift);
                }
            } else {
                state.bubble = remove(state.bubble);
                if (state.bubbleText != null && System.currentTimeMillis() >= state.bubbleUntil) {
                    state.bubbleText = null;
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not update the nametag of " + player.getName(), ex);
        } finally {
            state.ticks++;
        }
    }

    boolean canShow(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()
                || player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return false;
        }
        if (plugin.pluginConfig().hideNametagWhenSneaking() && player.isSneaking()) {
            return false;
        }
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return false;
            }
        }
        return true;
    }

    // Where a following display sits: the top of the player's head (lower while sneaking or swimming, like vanilla).
    private static Location anchor(Player player) {
        Location location = player.getLocation().add(0, player.getHeight(), 0);
        location.setYaw(0);
        location.setPitch(0);
        return location;
    }

    // Keeps the display above the player, re-creating it when it is gone, in another world or another region.
    @Nullable
    private TextDisplay keepUp(Player player, @Nullable TextDisplay display, int lineWidth, String kind) {
        if (display != null) {
            boolean usable = Bukkit.isOwnedByCurrentRegion(display) && display.isValid()
                    && display.getWorld().equals(player.getWorld());
            if (usable && ride) {
                if (player.getPassengers().contains(display) || player.addPassenger(display)) {
                    return display;
                }
            } else if (usable) {
                Location target = anchor(player);
                double distance = display.getLocation().distanceSquared(target);
                if (distance <= JUMP_DISTANCE_SQUARED) {
                    if (distance > 1.0e-6) {
                        display.teleport(target);
                    }
                    return display;
                }
            }
            remove(display);
        }
        return spawn(player, lineWidth, kind);
    }

    @Nullable
    private TextDisplay spawn(Player player, int lineWidth, String kind) {
        TextDisplay display = player.getWorld().spawn(anchor(player), TextDisplay.class);
        display.setPersistent(false);
        display.addScoreboardTag(TAG);
        display.addScoreboardTag(kind);
        display.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(false);
        display.setLineWidth(lineWidth);
        display.setInvulnerable(true);
        display.setSilent(true);
        if (!ride && SET_TELEPORT_DURATION != null) {
            try {
                SET_TELEPORT_DURATION.invoke(display, FOLLOW_INTERPOLATION_TICKS);
            } catch (ReflectiveOperationException ignored) {
                // Movement is then not interpolated; the display still follows.
            }
        }
        // The player never sees their own tag; it would float in front of the camera in third person.
        player.hideEntity(plugin, display);
        if (ride && !player.addPassenger(display)) {
            display.remove();
            return null;
        }
        return display;
    }

    private void setLift(TextDisplay display, double lift) {
        Transformation current = display.getTransformation();
        if (Math.abs(current.getTranslation().y() - lift) < 1.0e-3) {
            return;
        }
        display.setTransformation(new Transformation(
                new Vector3f(0, (float) lift, 0), new AxisAngle4f(), new Vector3f(1, 1, 1), new AxisAngle4f()));
    }

    // Marked as draft API ("subject to change") since 1.19.4; without it the bubble keeps the vanilla background.
    @SuppressWarnings("deprecation")
    private static void applyBackground(TextDisplay display, @Nullable Integer argb) {
        try {
            display.setBackgroundColor(argb == null ? null : Color.fromARGB(argb));
        } catch (LinkageError | IllegalArgumentException ex) {
            // Keep the default background.
        }
    }

    private void updateText(TextDisplay display, Component text) {
        if (!text.equals(display.text())) {
            display.text(text);
        }
    }

    // Like the vanilla tag: dimmer and not visible through walls while sneaking.
    private void applySneak(Player player, State state, PluginConfig config) {
        boolean sneaking = player.isSneaking() && !config.hideNametagWhenSneaking();
        TextDisplay nametag = state.nametag;
        if (nametag == null) {
            return;
        }
        if (sneaking != state.sneakStyled || nametag.isSeeThrough() == sneaking) {
            state.sneakStyled = sneaking;
            nametag.setSeeThrough(!sneaking);
            nametag.setTextOpacity(sneaking ? SNEAK_OPACITY : (byte) -1);
        }
    }

    @Nullable
    private TextDisplay remove(@Nullable TextDisplay display) {
        if (display == null) {
            return null;
        }
        if (Bukkit.isOwnedByCurrentRegion(display)) {
            display.remove();
        } else {
            display.getScheduler().run(plugin, task -> display.remove(), null);
        }
        return null;
    }

    private void discard(State state) {
        if (state.task != null) {
            state.task.cancel();
        }
        state.nametag = removeQuietly(state.nametag);
        state.bubble = removeQuietly(state.bubble);
    }

    @Nullable
    private TextDisplay removeQuietly(@Nullable TextDisplay display) {
        try {
            return remove(display);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // A tagged display loading from disk was left behind (a crash, or another server build); remove it.
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(TAG)) {
                entity.remove();
            }
        }
    }
}
