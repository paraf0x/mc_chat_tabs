package com.chattabs.tab;

import com.chattabs.chat.ChatMessage;
import com.chattabs.chat.MessageKind;
import com.chattabs.config.ChannelDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTabTest {

    private static final ChannelDefinition KINGDOM =
            new ChannelDefinition("kingdom", "Kingdom", "[K]", "channel join kingdom", false, false);

    @Test
    void channelTabShowsOnlyItsChannel() {
        ChatTab tab = ChatTab.createChannelTab(KINGDOM);
        assertEquals("kingdom", tab.getId());
        assertEquals("kingdom", tab.getChannelId());
        assertTrue(tab.isChannelTab());
        assertTrue(tab.isClosable());

        ChatMessage kingdomMsg = new ChatMessage(null, MessageKind.CHANNEL, "kingdom", "UnicornsBane", "test");
        ChatMessage globalMsg = new ChatMessage(null, MessageKind.CHANNEL, "global", "labsami", "hi");
        ChatMessage system = new ChatMessage(null, MessageKind.SYSTEM, null, null, "note");
        assertTrue(tab.shouldShowMessage(kingdomMsg));
        assertFalse(tab.shouldShowMessage(globalMsg));
        assertFalse(tab.shouldShowMessage(system));
    }

    @Test
    void alwaysVisibleChannelTabIsNotClosable() {
        ChatTab tab = ChatTab.createChannelTab(
                new ChannelDefinition("global", "Global", "[G]", "g", true, true));
        assertFalse(tab.isClosable());
    }

    @Test
    void allTabShowsEverythingAndNeverCountsUnread() {
        ChatTab all = ChatTab.createAllTab();
        assertTrue(all.isAllTab());
        assertFalse(all.isClosable());
        assertTrue(all.shouldShowMessage(new ChatMessage(null, MessageKind.SYSTEM, null, null, "x")));
        assertFalse(all.shouldTrackUnread());
    }

    @Test
    void channelFlagsDefaultToEnabled() {
        ChatTab tab = ChatTab.createChannelTab(
                new ChannelDefinition("trade", "Trade", "[T]", "", true, false));
        assertTrue(tab.isSoundEnabled());
        assertTrue(tab.isBadgeEnabled());
        assertTrue(tab.shouldTrackUnread());
    }

    @Test
    void badgeFlagControlsUnreadTracking() {
        ChatTab silentBadge = ChatTab.createChannelTab(
                new ChannelDefinition("a", "A", "[A]", "", true, false, false, true));
        assertFalse(silentBadge.isSoundEnabled());
        assertTrue(silentBadge.shouldTrackUnread());

        ChatTab noBadge = ChatTab.createChannelTab(
                new ChannelDefinition("b", "B", "[B]", "", true, false, true, false));
        assertTrue(noBadge.isSoundEnabled());
        assertFalse(noBadge.shouldTrackUnread());
    }

    @Test
    void dmAndAllTabsKeepDefaultNotificationBehavior() {
        ChatTab dm = ChatTab.createDmTab("SomePlayer");
        assertTrue(dm.isSoundEnabled());
        assertTrue(dm.shouldTrackUnread());

        ChatTab all = ChatTab.createAllTab();
        assertTrue(all.isSoundEnabled());
        assertFalse(all.shouldTrackUnread());
    }

    @Test
    void dmTabMatchesSenderAndTracksUnread() {
        ChatTab dm = ChatTab.createDmTab("SomePlayer");
        assertTrue(dm.isDmTab());
        assertTrue(dm.isClosable());
        assertTrue(dm.shouldTrackUnread());
        ChatMessage msg = new ChatMessage(null, MessageKind.DM, null, "someplayer", "hi");
        assertTrue(dm.shouldShowMessage(msg));
    }
}
