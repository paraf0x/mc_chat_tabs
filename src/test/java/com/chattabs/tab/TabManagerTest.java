package com.chattabs.tab;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabManagerTest {

    @BeforeEach
    void freshManager() {
        TabManager.resetForTesting();
    }

    @Test
    void kingdomTabHiddenByDefaultButAlwaysCollects() {
        TabManager manager = TabManager.getInstance();
        assertFalse(manager.isKingdomTabVisible());
        assertTrue(manager.getAllTabs().contains(manager.getKingdomTab()));
        assertFalse(manager.getFixedTabs().contains(manager.getKingdomTab()));
    }

    @Test
    void incomingKingdomMessageRevealsTabAndCountsUnread() {
        TabManager manager = TabManager.getInstance();
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > test");
        GuiMessage hudLine = new GuiMessage(0, line, null, null, null);

        manager.onMessageReceived(line, hudLine);

        assertTrue(manager.isKingdomTabVisible());
        assertEquals(1, manager.getKingdomTab().getMessages().size());
        assertEquals(1, manager.getKingdomTab().getUnreadCount());
    }

    @Test
    void closingActiveKingdomTabFallsBackToAllTab() {
        TabManager manager = TabManager.getInstance();
        manager.revealKingdomTab();
        manager.setActiveTab(manager.getKingdomTab(), false);
        assertEquals(manager.getKingdomTab(), manager.getActiveTab());

        manager.closeKingdomTab();

        assertFalse(manager.isKingdomTabVisible());
        assertEquals("all", manager.getActiveTab().getId());
    }

    @Test
    void closingHiddenKingdomTabKeepsHistory() {
        TabManager manager = TabManager.getInstance();
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > brb");
        manager.onMessageReceived(line, new GuiMessage(0, line, null, null, null));
        manager.closeKingdomTab();

        assertFalse(manager.isKingdomTabVisible());
        assertEquals(1, manager.getKingdomTab().getMessages().size());
    }
}
