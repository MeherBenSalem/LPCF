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

    @Test
    void normalizeConvertsLegacyAmpersandRgbSequence() {
        assertEquals("<#AABBCC>hi", MiniMessageUtil.normalize("&x&A&A&B&B&C&Chi"));
    }

    @Test
    void normalizeConvertsSectionLegacyCodes() {
        assertEquals("<bold>hello", MiniMessageUtil.normalize("§lhello"));
    }

    @Test
    void stripFormattingRemovesHexAndSectionCodes() {
        assertEquals("hello", MiniMessageUtil.stripFormatting("&#FF0000hello"));
        assertEquals("hello", MiniMessageUtil.stripFormatting("&x&F&F&0&0&0&0hello"));
        assertEquals("hello", MiniMessageUtil.stripFormatting("§chello"));
    }

    @Test
    void stripFormattingEmptyInputReturnsEmptyString() {
        assertEquals("", MiniMessageUtil.stripFormatting(null));
        assertEquals("", MiniMessageUtil.stripFormatting(""));
    }
}
