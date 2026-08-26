package io.nightbeam.LPCF.display.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class FakeTeam {

    private final String name;
    private final String usedPlayerName;
    private final int sortPriority;
    private final Set<String> members = new HashSet<>();

    private Component prefix;
    private Component suffix;
    private boolean visible = true;
    private NamedTextColor nameFormattingOverride;

    private FakeTeam(String name, String usedPlayerName, int sortPriority, Component prefix, Component suffix) {
        this.name = name;
        this.usedPlayerName = usedPlayerName;
        this.sortPriority = sortPriority;
        setPrefix(prefix);
        setSuffix(suffix);
    }

    public static FakeTeam create(@NotNull String player, Component prefix, Component suffix, int sortPriority) {
        Objects.requireNonNull(player, "player");
        return new FakeTeam(generateTeamName(player, sortPriority), player, sortPriority, prefix, suffix);
    }

    private static String generateTeamName(String player, int sortPriority) {
        int priority = sortPriority < 0 ? 9999 : Math.min(Math.max(sortPriority, 0), 9999);
        String priorityPart = String.format(Locale.ROOT, "%04d", priority);
        String hash = Integer.toHexString(player.toLowerCase(Locale.ROOT).hashCode() & 0x7FFFFFFF);
        if (hash.length() > 11) {
            hash = hash.substring(0, 11);
        }
        String teamName = "L" + priorityPart + hash;
        return teamName.length() > 16 ? teamName.substring(0, 16) : teamName;
    }

    public void addMember(final @NotNull String player) {
        members.add(Objects.requireNonNull(player, "player"));
    }

    @Unmodifiable
    public Set<String> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean removeMember(final String player) {
        return members.remove(player);
    }

    @NotNull
    public String getName() {
        return name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setNameFormattingOverride(@Nullable NamedTextColor nameFormattingOverride) {
        this.nameFormattingOverride = nameFormattingOverride;
    }

    @Nullable
    public NamedTextColor getNameFormattingOverride() {
        return nameFormattingOverride;
    }

    @Nullable
    public NamedTextColor resolvedNameColor() {
        if (nameFormattingOverride != null) {
            return nameFormattingOverride;
        }
        if (Component.empty().equals(prefix)) {
            return null;
        }
        Style lastStyle = Style.empty();
        for (Component child : prefix.iterable(ComponentIteratorType.DEPTH_FIRST)) {
            lastStyle = child.style();
        }
        return Optional.ofNullable(lastStyle.color()).map(NamedTextColor::nearestTo).orElse(null);
    }

    public Component getPrefix() {
        return prefix;
    }

    public void setPrefix(@NotNull Component prefix) {
        this.prefix = prefix != null ? prefix : Component.empty();
    }

    public Component getSuffix() {
        return suffix;
    }

    public void setSuffix(Component suffix) {
        this.suffix = suffix != null ? suffix : Component.empty();
    }

    public boolean isSimilar(String name, int sortPriority, Component prefix, Component suffix, boolean visible) {
        return usedPlayerName.equals(name)
                && this.sortPriority == sortPriority
                && this.prefix.equals(prefix)
                && this.suffix.equals(suffix)
                && this.visible == visible;
    }

}
