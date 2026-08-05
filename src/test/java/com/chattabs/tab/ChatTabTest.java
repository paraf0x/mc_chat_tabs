package com.chattabs.tab;

import com.chattabs.chat.ChatChannel;
import com.chattabs.chat.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTabTest {

    @Test
    void kingdomTabShowsOnlyKingdomMessages() {
        ChatTab tab = ChatTab.createKingdomTab();
        assertEquals("kingdom", tab.getId());
        assertEquals("Kingdom", tab.getDisplayName());
        assertFalse(tab.isDmTab());

        ChatMessage kingdomMsg = new ChatMessage(null, ChatChannel.KINGDOM, "UnicornsBane", "test");
        ChatMessage globalMsg = new ChatMessage(null, ChatChannel.GLOBAL, "labsami", "hi");
        assertTrue(tab.shouldShowMessage(kingdomMsg));
        assertFalse(tab.shouldShowMessage(globalMsg));
    }

    @Test
    void kingdomTabTracksUnread() {
        assertTrue(ChatTab.createKingdomTab().shouldTrackUnread());
    }
}
