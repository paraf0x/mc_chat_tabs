package com.chattabs.tab;

import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TabManagerTest {

    @BeforeEach
    void freshManager() {
        TabManager.resetForTesting();
        TabManager.getInstance().applyProfile(ServerProfile.builtInDefault());
    }

    private static GuiMessage line(String text) {
        return new GuiMessage(0, Component.literal(text), null, null, null);
    }

    @Test
    void profileTabsAreBuiltInOrderWithActivityChannelsHidden() {
        TabManager manager = TabManager.getInstance();
        List<ChatTab> visible = manager.getVisibleTabs();
        assertEquals(List.of("all", "global", "local"),
                visible.stream().map(ChatTab::getId).toList());
        assertFalse(manager.isChannelTabVisible("kingdom"));
        assertNotNull(manager.getChannelTab("kingdom"));
    }

    @Test
    void incomingActivityChannelMessageRevealsTabAndCountsUnread() {
        TabManager manager = TabManager.getInstance();
        Component text = Component.literal("[K] [CrimsonHollow] [Lord] UnicornsBane > test");
        manager.onMessageReceived(text, line("[K] [CrimsonHollow] [Lord] UnicornsBane > test"));

        assertTrue(manager.isChannelTabVisible("kingdom"));
        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());
        assertEquals(1, manager.getChannelTab("kingdom").getUnreadCount());
    }

    @Test
    void closingActiveChannelTabFallsBackToAll() {
        TabManager manager = TabManager.getInstance();
        manager.revealChannelTab("kingdom");
        manager.setActiveTab(manager.getChannelTab("kingdom"), false);

        manager.closeChannelTab("kingdom");

        assertFalse(manager.isChannelTabVisible("kingdom"));
        assertEquals("all", manager.getActiveTab().getId());
    }

    @Test
    void closingHiddenChannelTabKeepsHistory() {
        TabManager manager = TabManager.getInstance();
        manager.onMessageReceived(Component.literal("[K] [C] [L] U > brb"), line("[K] [C] [L] U > brb"));
        manager.closeChannelTab("kingdom");

        assertFalse(manager.isChannelTabVisible("kingdom"));
        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());
    }

    @Test
    void typedJoinCommandRevealsActivityChannel() {
        TabManager manager = TabManager.getInstance();
        manager.noteOutgoingCommand("/Channel  Join   KINGDOM ");
        assertTrue(manager.isChannelTabVisible("kingdom"));
    }

    @Test
    void otherCommandsDoNotRevealChannels() {
        TabManager manager = TabManager.getInstance();
        manager.noteOutgoingCommand("/g");
        manager.noteOutgoingCommand("/gift someone");
        manager.noteOutgoingCommand("/channel join global");
        assertFalse(manager.isChannelTabVisible("kingdom"));
    }

    @Test
    void peekCollectsOnlyFlaggedChannelsAndStripsTag() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("peeky", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false),
                new ChannelDefinition("trade", "Trade", "[T]", "", true, true))));

        manager.onMessageReceived(Component.literal("[G] Player > hello world"),
                line("[G] Player > hello world"));
        manager.onMessageReceived(Component.literal("[T] Trader > selling dirt"),
                line("[T] Trader > selling dirt"));

        assertEquals(1, manager.getGlobalPeekMessages().size());
        assertEquals("Trader > selling dirt", manager.getGlobalPeekMessages().get(0).getString());
    }

    @Test
    void applyProfilePreservesMessagesForSurvivingIdsAndDropsRemoved() {
        TabManager manager = TabManager.getInstance();
        manager.onMessageReceived(Component.literal("[K] [C] [L] U > hi"), line("[K] [C] [L] U > hi"));
        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());

        manager.applyProfile(new ServerProfile("other", "", ">", List.of(
                new ChannelDefinition("kingdom", "Kingdom", "[K]", "", false, false),
                new ChannelDefinition("trade", "Trade", "[T]", "", true, false))));

        assertEquals(1, manager.getChannelTab("kingdom").getMessages().size());
        assertNotNull(manager.getChannelTab("trade"));
        assertNull(manager.getChannelTab("global"));
    }
}
