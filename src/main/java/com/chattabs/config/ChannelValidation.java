package com.chattabs.config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Rules a channel must satisfy before it can be saved. */
public final class ChannelValidation {

    private ChannelValidation() {
    }

    public static Optional<String> validate(ChannelDefinition candidate, List<ChannelDefinition> others) {
        String id = candidate.getId().trim();
        if (id.isBlank()) {
            return Optional.of("Channel id must not be empty.");
        }
        if (id.equalsIgnoreCase("all")) {
            return Optional.of("\"all\" is reserved for the All tab.");
        }
        if (id.equalsIgnoreCase("system")) {
            return Optional.of("\"system\" is reserved for the System tab.");
        }
        if (candidate.getTag().isBlank()) {
            return Optional.of("Chat tag must not be empty.");
        }
        for (ChannelDefinition other : others) {
            if (other != null && other.getId().equalsIgnoreCase(id)) {
                return Optional.of("Another channel already uses the id \"" + id.toLowerCase(Locale.ROOT) + "\".");
            }
        }
        return Optional.empty();
    }
}
