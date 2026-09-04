package io.nightbeam.LPCF.display.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeTeamTest {

    @Test
    void createGeneratesBoundedTeamName() {
        FakeTeam team = FakeTeam.create("PlayerOne", Component.text("["), Component.text("]"), 5);

        assertTrue(team.getName().length() <= 16);
        assertTrue(team.getName().startsWith("L"));
        assertEquals(Component.text("["), team.getPrefix());
        assertEquals(Component.text("]"), team.getSuffix());
    }

    @Test
    void membersCanBeAddedAndRemoved() {
        FakeTeam team = FakeTeam.create("PlayerOne", Component.empty(), Component.empty(), 0);

        team.addMember("PlayerOne");
        assertTrue(team.getMembers().contains("PlayerOne"));

        assertTrue(team.removeMember("PlayerOne"));
        assertFalse(team.getMembers().contains("PlayerOne"));
    }

    @Test
    void resolvedNameColorUsesOverrideWhenPresent() {
        FakeTeam team = FakeTeam.create("PlayerOne", Component.empty(), Component.empty(), 0);
        team.setNameFormattingOverride(NamedTextColor.AQUA);

        assertEquals(NamedTextColor.AQUA, team.resolvedNameColor());
    }
}
