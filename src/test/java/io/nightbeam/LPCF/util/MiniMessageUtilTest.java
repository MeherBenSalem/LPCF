package io.nightbeam.LPCF.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiniMessageUtilTest {

    @Test
    void normalizeConvertsAmpersandHexToMiniMessage() {
        assertEquals("<#FF0000>hello", MiniMessageUtil.normalize("&#FF0000hello"));
    }

    @Test
    void normalizeConvertsLegacyAmpersandCodes() {
        assertEquals("<red>text", MiniMessageUtil.normalize("&ctext"));
    }

    @Test
    void stripFormattingRemovesMiniMessageTags() {
        assertEquals("hello", MiniMessageUtil.stripFormatting("<red>hello</red>"));
    }

    @Test
    void stripFormattingRemovesLegacyCodes() {
        assertEquals("hello", MiniMessageUtil.stripFormatting("&chello"));
    }

    @Test
    void normalizeEmptyInputReturnsEmptyString() {
        assertEquals("", MiniMessageUtil.normalize(null));
        assertEquals("", MiniMessageUtil.normalize(""));
    }
}
