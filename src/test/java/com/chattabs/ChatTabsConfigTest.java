package com.chattabs;

import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTabsConfigTest {

    private static final Gson GSON = new Gson();

    @Test
    void legacyConfigWithoutProfilesGetsDefaultProfile() {
        ChatTabsConfig config = GSON.fromJson("{\"idleSwitchSeconds\": 30}", ChatTabsConfig.class);
        boolean changed = config.normalize();

        assertTrue(changed);
        assertEquals(30, config.getIdleSwitchSeconds());
        assertEquals(1, config.getProfiles().size());
        assertEquals("global", config.getProfiles().get(0).validChannels().get(0).getId());
    }

    @Test
    void normalizeIsIdempotentOnMigratedConfig() {
        ChatTabsConfig config = GSON.fromJson("{}", ChatTabsConfig.class);
        config.normalize();
        assertFalse(config.normalize());
    }

    @Test
    void profileForAddressPrefersExactMatchThenDefault() {
        ChatTabsConfig config = GSON.fromJson("{}", ChatTabsConfig.class);
        config.normalize();
        config.getProfiles().add(new ServerProfile("srv", "play.example.net", ">",
                java.util.List.of(new ChannelDefinition("trade", "Trade", "[T]", "", true, false))));

        assertEquals("srv", config.profileForAddress("PLAY.EXAMPLE.NET").getName());
        assertEquals("default", config.profileForAddress("other.server").getName());
        assertEquals("default", config.profileForAddress(null).getName());
    }
}
