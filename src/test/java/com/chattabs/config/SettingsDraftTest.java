package com.chattabs.config;

import com.chattabs.ChatTabsConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingsDraftTest {

    private ChatTabsConfig config;

    @BeforeEach
    void freshConfig() {
        config = new Gson().fromJson("{}", ChatTabsConfig.class);
        config.normalize();
    }

    @Test
    void editingTheDraftLeavesTheConfigUntouchedUntilCommit() {
        SettingsDraft draft = SettingsDraft.from(config);
        draft.setIdleSwitchSeconds(42);
        draft.putChannel(0, 0, new ChannelDefinition("global", "Welt", "[W]", "g", true, true));

        assertEquals(-1, config.getIdleSwitchSeconds());
        assertEquals("[G]", config.getProfiles().get(0).validChannels().get(0).getTag());

        draft.commit(config);

        assertEquals(42, config.getIdleSwitchSeconds());
        assertEquals("[W]", config.getProfiles().get(0).validChannels().get(0).getTag());
        assertEquals("Welt", config.getProfiles().get(0).validChannels().get(0).getDisplayName());
    }

    @Test
    void addingAndRemovingChannelsRoundTrips() {
        SettingsDraft draft = SettingsDraft.from(config);
        int before = draft.profile(0).validChannels().size();

        draft.putChannel(0, -1, new ChannelDefinition("trade", "Trade", "[T]", "", true, false));
        assertEquals(before + 1, draft.profile(0).validChannels().size());

        draft.removeChannel(0, 0);
        draft.commit(config);

        var channels = config.getProfiles().get(0).validChannels();
        assertEquals(before, channels.size());
        assertEquals("trade", channels.get(channels.size() - 1).getId());
        assertFalse(channels.stream().anyMatch(c -> c.getId().equals("global")));
    }

    @Test
    void addProfileForAddressCopiesChannelsOfTheSourceProfile() {
        SettingsDraft draft = SettingsDraft.from(config);
        int index = draft.addProfileForAddress("play.example.net", 0);

        assertEquals(1, index);
        assertEquals("play.example.net", draft.profile(index).getServerAddress());
        assertEquals(draft.profile(0).validChannels().size(), draft.profile(index).validChannels().size());

        draft.putChannel(index, 0, new ChannelDefinition("global", "Global", "[GG]", "g", true, true));
        assertEquals("[G]", draft.profile(0).validChannels().get(0).getTag());

        draft.commit(config);
        assertEquals(2, config.getProfiles().size());
    }

    @Test
    void separatorIsEditablePerProfile() {
        SettingsDraft draft = SettingsDraft.from(config);
        draft.setSeparator(0, "»");
        draft.commit(config);
        assertEquals("»", config.getProfiles().get(0).getSeparator());
    }
}
