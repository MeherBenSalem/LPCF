package io.nightbeam.LPCF.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void compareVersionsOrdersNumericSegments() {
        assertTrue(UpdateChecker.compareVersions("1.2.0", "1.2.1") < 0);
        assertTrue(UpdateChecker.compareVersions("1.2.1", "1.2.0") > 0);
        assertEquals(0, UpdateChecker.compareVersions("1.2.0", "1.2.0"));
    }

    @Test
    void compareVersionsHandlesDifferentLengths() {
        assertTrue(UpdateChecker.compareVersions("1.2", "1.2.1") < 0);
        assertTrue(UpdateChecker.compareVersions("1.2.1", "1.2") > 0);
    }

    @Test
    void compareVersionsIgnoresSuffixAfterDash() {
        assertEquals(0, UpdateChecker.compareVersions("1.2.0-SNAPSHOT", "1.2.0"));
    }
}
