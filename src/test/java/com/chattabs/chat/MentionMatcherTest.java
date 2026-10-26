package com.chattabs.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MentionMatcherTest {

    @Test
    void matchesWholeWordsIgnoringCase() {
        List<String> names = List.of("UnicornBeef", "UB");
        assertTrue(MentionMatcher.mentions("hey unicornbeef, trade?", names));
        assertTrue(MentionMatcher.mentions("ub you there", names));
        assertTrue(MentionMatcher.mentions("@UB!", names));
    }

    @Test
    void ignoresNamesInsideOtherWords() {
        List<String> names = List.of("UB", "Unicorn");
        assertFalse(MentionMatcher.mentions("anyone at the pub", names));
        assertFalse(MentionMatcher.mentions("unicorns are cool", names));
        assertFalse(MentionMatcher.mentions("club_ub2", names));
    }

    @Test
    void blankAndNullNamesNeverMatch() {
        assertFalse(MentionMatcher.mentions("anything", List.of("", "  ")));
        assertFalse(MentionMatcher.mentions("anything", java.util.Arrays.asList((String) null)));
        assertFalse(MentionMatcher.mentions(null, List.of("UB")));
    }

    @Test
    void parsesCommaSeparatedNames() {
        assertEquals(List.of("Unicorn", "UB", "Beef"), MentionMatcher.parseNames(" Unicorn, UB ,,Beef "));
        assertEquals(List.of(), MentionMatcher.parseNames(""));
        assertEquals("Unicorn, UB", MentionMatcher.joinNames(List.of("Unicorn", "UB")));
    }
}
