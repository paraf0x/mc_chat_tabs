package com.chattabs.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerProfileTest {

    @Test
    void builtInDefaultReproducesTodaysChannels() {
        ServerProfile profile = ServerProfile.builtInDefault();
        assertTrue(profile.isDefaultProfile());
        assertEquals(">", profile.getSeparator());

        List<ChannelDefinition> channels = profile.validChannels();
        assertEquals(3, channels.size());

        ChannelDefinition global = channels.get(0);
        assertEquals("global", global.getId());
        assertEquals("[G]", global.getTag());
        assertEquals("g", global.getJoinCommand());
        assertTrue(global.isAlwaysVisible());
        assertTrue(global.isPeek());

        ChannelDefinition local = channels.get(1);
        assertEquals("local", local.getId());
        assertEquals("l", local.getJoinCommand());
        assertTrue(local.isAlwaysVisible());
        assertFalse(local.isPeek());

        ChannelDefinition kingdom = channels.get(2);
        assertEquals("kingdom", kingdom.getId());
        assertEquals("[K]", kingdom.getTag());
        assertEquals("channel join kingdom", kingdom.getJoinCommand());
        assertFalse(kingdom.isAlwaysVisible());
    }

    @Test
    void validChannelsSkipsInvalidAndDuplicateEntries() {
        ServerProfile profile = new ServerProfile("test", "", ">", List.of(
                new ChannelDefinition("trade", "Trade", "[T]", "", true, false),
                new ChannelDefinition("", "NoId", "[X]", "", true, false),
                new ChannelDefinition("notag", "NoTag", "", "", true, false),
                new ChannelDefinition("all", "Reserved", "[A]", "", true, false),
                new ChannelDefinition("trade", "Duplicate", "[T2]", "", true, false)
        ));
        List<ChannelDefinition> valid = profile.validChannels();
        assertEquals(1, valid.size());
        assertEquals("trade", valid.get(0).getId());
    }

    @Test
    void matchesAddressIsExactAndCaseInsensitive() {
        ServerProfile profile = new ServerProfile("srv", "Play.Example.net", ">", List.of());
        assertTrue(profile.matchesAddress("play.example.net"));
        assertFalse(profile.matchesAddress("play.example.net:25565"));
        assertFalse(profile.matchesAddress("other.example.net"));
        assertFalse(profile.matchesAddress(null));
        assertFalse(profile.isDefaultProfile());
    }

    @Test
    void channelByIdFindsValidChannelsOnly() {
        ServerProfile profile = ServerProfile.builtInDefault();
        assertNotNull(profile.channelById("kingdom"));
        assertNull(profile.channelById("nope"));
    }
}
