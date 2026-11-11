package com.chattabs.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChannelValidationTest {

    private static ChannelDefinition channel(String id, String tag) {
        return new ChannelDefinition(id, id, tag, "", true, false);
    }

    @Test
    void validChannelPassesAgainstOthers() {
        Optional<String> error = ChannelValidation.validate(
                channel("trade", "[T]"), List.of(channel("global", "[G]")));
        assertTrue(error.isEmpty());
    }

    @Test
    void blankIdAndBlankTagAreRejected() {
        assertTrue(ChannelValidation.validate(channel("", "[T]"), List.of()).isPresent());
        assertTrue(ChannelValidation.validate(channel("trade", " "), List.of()).isPresent());
    }

    @Test
    void reservedIdIsRejectedCaseInsensitively() {
        assertTrue(ChannelValidation.validate(channel("all", "[A]"), List.of()).isPresent());
        assertTrue(ChannelValidation.validate(channel("ALL", "[A]"), List.of()).isPresent());
    }

    @Test
    void duplicateIdIsRejectedCaseInsensitively() {
        List<ChannelDefinition> others = List.of(channel("trade", "[T]"));
        assertTrue(ChannelValidation.validate(channel("TRADE", "[X]"), others).isPresent());
        assertTrue(ChannelValidation.validate(channel("other", "[X]"), others).isEmpty());
    }

    @Test
    void systemIdIsReserved() {
        assertTrue(ChannelValidation.validate(channel("system", "[S]"), List.of()).isPresent());
        assertFalse(new ChannelDefinition("System", "S", "[S]", "", true, false).isValid());
    }

    @Test
    void sendCommandWithEmptyJoinCommandIsValid() {
        ChannelDefinition crew = new ChannelDefinition("crew", "Crew", "[Crew]", "", "hoard c", true, false, true, true);
        assertTrue(ChannelValidation.validate(crew, List.of()).isEmpty());
    }

    @Test
    void sendCommandSurvivesJsonRoundTripAndDefaultsToEmpty() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        ChannelDefinition crew = new ChannelDefinition("crew", "Crew", "[Crew]", "", "hoard c", true, false, true, true);
        assertEquals("hoard c", gson.fromJson(gson.toJson(crew), ChannelDefinition.class).getSendCommand());
        assertEquals("", gson.fromJson("{\"id\":\"g\",\"tag\":\"[G]\"}", ChannelDefinition.class).getSendCommand());
    }
}
