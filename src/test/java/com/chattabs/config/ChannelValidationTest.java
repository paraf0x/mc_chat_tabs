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
}
