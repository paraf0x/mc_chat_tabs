package com.chattabs.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelPreviewTest {

    @Test
    void matchingLineSplitsSenderAndContent() {
        ChannelPreview preview = ChannelPreview.of("[K]", ">",
                "[K] [CrimsonHollow] [Lord] UnicornsBane > test message");
        assertTrue(preview.matched());
        assertEquals("[CrimsonHollow] [Lord] UnicornsBane", preview.sender());
        assertEquals("test message", preview.content());
    }

    @Test
    void nonMatchingLineReportsNoMatch() {
        ChannelPreview preview = ChannelPreview.of("[K]", ">", "[G] Player > hello");
        assertFalse(preview.matched());
        assertEquals("", preview.sender());
        assertEquals("", preview.content());
    }

    @Test
    void tagsAndSeparatorsWithMetacharactersAreLiteral() {
        ChannelPreview preview = ChannelPreview.of("(T)", "|", "(T) Trader | selling dirt");
        assertTrue(preview.matched());
        assertEquals("Trader", preview.sender());
        assertEquals("selling dirt", preview.content());
    }

    @Test
    void blankTagNeverMatches() {
        assertFalse(ChannelPreview.of("", ">", "Player > hi").matched());
    }

    @Test
    void sampleLineStartsWithTheTag() {
        String sample = ChannelPreview.sampleLineFor("[T]");
        assertTrue(sample.startsWith("[T] "));
        assertTrue(ChannelPreview.of("[T]", ">", sample).matched());
    }
}
