package com.chattabs.config;

import java.util.List;

/** Test fixtures. */
public final class TestProfiles {
    private TestProfiles() {
    }

    /** The three-channel profile that used to be the built-in default: Global, Local, Kingdom. */
    public static ServerProfile withKingdom() {
        return new ServerProfile("default", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, true),
                new ChannelDefinition("local", "Local", "[L]", "l", true, false),
                new ChannelDefinition("kingdom", "Kingdom", "[K]", "channel join kingdom", false, false)
        ));
    }
}
