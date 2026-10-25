package io.nightbeam.LPCF.display.tag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagLayoutTest {

    @Test
    void passengerSeatMovedUpIn1202() {
        assertTrue(TagLayout.seatAtThreeQuarters("1.20.1"));
        assertTrue(TagLayout.seatAtThreeQuarters("1.20"));
        assertFalse(TagLayout.seatAtThreeQuarters("1.20.2"));
        assertFalse(TagLayout.seatAtThreeQuarters("1.21.1"));
        assertFalse(TagLayout.seatAtThreeQuarters("26.1.2"));
        assertFalse(TagLayout.seatAtThreeQuarters("26.3"));
        assertFalse(TagLayout.seatAtThreeQuarters(null));
        assertFalse(TagLayout.seatAtThreeQuarters("garbage"));
        assertTrue(TagLayout.baseLift("1.20.1", true) > TagLayout.baseLift("1.21.1", true));
        // A display that follows the head is placed the same way on every version.
        assertEquals(TagLayout.baseLift("1.20.1", false), TagLayout.baseLift("26.3", false), 1e-9);
        assertEquals(TagLayout.HEAD_LIFT, TagLayout.baseLift("26.3", true), 1e-9);
    }

    @Test
    void nametagGoesAboveTheVanillaNameWhenItIsVisible() {
        assertEquals(0.5, TagLayout.nametagLift(0.5, 0, false), 1e-9);
        assertEquals(0.5 + TagLayout.LINE + 0.2, TagLayout.nametagLift(0.5, 0.2, true), 1e-9);
    }

    @Test
    void bubbleStacksAboveEveryNametagLine() {
        assertEquals(0.5, TagLayout.bubbleLift(0.5, 0, false, 0), 1e-9);
        assertEquals(0.5 + 2 * TagLayout.LINE + TagLayout.GAP, TagLayout.bubbleLift(0.5, 0, false, 2), 1e-9);
        assertEquals(0.5 + 2 * TagLayout.LINE + TagLayout.GAP, TagLayout.bubbleLift(0.5, 0, true, 1), 1e-9);
    }

    @Test
    void bubbleTimeGrowsWithLengthWithinBounds() {
        assertEquals(3.0, TagLayout.bubbleSeconds(0, 3, 0.08, 10), 1e-9);
        assertEquals(3.8, TagLayout.bubbleSeconds(10, 3, 0.08, 10), 1e-9);
        assertEquals(10.0, TagLayout.bubbleSeconds(500, 3, 0.08, 10), 1e-9);
        assertEquals(2.0, TagLayout.bubbleSeconds(5, 3, 0.08, 2), 1e-9);
    }

    @Test
    void lineCountCountsNewlines() {
        assertEquals(0, TagLayout.lineCount(""));
        assertEquals(0, TagLayout.lineCount("  "));
        assertEquals(1, TagLayout.lineCount("Builder"));
        assertEquals(2, TagLayout.lineCount("[Admin]\nBuilder"));
    }
}
