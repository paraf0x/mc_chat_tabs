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
    void cyclingMovesForwardAndWrapsOverVisibleTabs() {
        TabManager manager = TabManager.getInstance();
        // visible: all, global, local (kingdom hidden until activity)
        assertEquals("all", manager.getActiveTab().getId());

        manager.cycleActiveTab(1);
        assertEquals("global", manager.getActiveTab().getId());
        manager.cycleActiveTab(1);
        assertEquals("local", manager.getActiveTab().getId());
        manager.cycleActiveTab(1);
        assertEquals("all", manager.getActiveTab().getId());
    }

    @Test
    void cyclingBackwardsWrapsToLastVisibleTab() {
        TabManager manager = TabManager.getInstance();
        manager.cycleActiveTab(-1);
        assertEquals("local", manager.getActiveTab().getId());
        manager.cycleActiveTab(-1);
        assertEquals("global", manager.getActiveTab().getId());
    }

    @Test
    void cyclingIncludesRevealedChannelsOnly() {
        TabManager manager = TabManager.getInstance();
        manager.revealChannelTab("kingdom");

        manager.cycleActiveTab(-1);
        assertEquals("kingdom", manager.getActiveTab().getId());

        manager.closeChannelTab("kingdom");
        manager.setActiveTab(manager.getChannelTab("local"), false);
        manager.cycleActiveTab(1);
        assertEquals("all", manager.getActiveTab().getId());
    }

    @Test
    void mutedChannelNeitherPingsNorBadgesWhenBothFlagsOff() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("quiet", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false),
                new ChannelDefinition("spam", "Spam", "[S]", "", true, false, false, false))));
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[S] Bot > buy gold"), line("[S] Bot > buy gold"));

        assertEquals(0, manager.getChannelTab("spam").getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());
        assertEquals(1, manager.getChannelTab("spam").getMessages().size());
    }

    @Test
    void badgeWithoutSoundCountsSilently() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("silent", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false),
                new ChannelDefinition("quiet", "Quiet", "[Q]", "", true, false, false, true))));
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[Q] Player > hi"), line("[Q] Player > hi"));

        assertEquals(1, manager.getChannelTab("quiet").getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());
    }

    @Test
    void soundWithoutBadgePingsWithoutCounting() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(new ServerProfile("loud", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false),
                new ChannelDefinition("loud", "Loud", "[L2]", "", true, false, true, false))));
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[L2] Player > hi"), line("[L2] Player > hi"));

        assertEquals(0, manager.getChannelTab("loud").getUnreadCount());
        assertEquals(1, manager.soundRequestsForTesting());
    }

    @Test
    void defaultChannelsStillPingOncePerMessage() {
        TabManager manager = TabManager.getInstance();
        manager.resetSoundRequestsForTesting();

        manager.onMessageReceived(Component.literal("[K] [C] [L] U > hi"), line("[K] [C] [L] U > hi"));

        assertEquals(1, manager.soundRequestsForTesting());
        assertEquals(1, manager.getChannelTab("kingdom").getUnreadCount());
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

    @Test
    void joinCommandIsSentOnlyWhenServerKnowsItsRoot() {
        com.mojang.brigadier.CommandDispatcher<Object> dispatcher = new com.mojang.brigadier.CommandDispatcher<>();
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.literal("channel"));
        assertTrue(TabManager.serverKnowsCommand(dispatcher, "channel join kingdom"));
        assertFalse(TabManager.serverKnowsCommand(dispatcher, "l"));
        assertFalse(TabManager.serverKnowsCommand(dispatcher, "  "));
    }

    @Test
    void missingCommandTreeDoesNotBlockJoinCommand() {
        assertTrue(TabManager.serverKnowsCommand(null, "l"));
    }

    @Test
    void vanillaWhisperOpensDmTab() {
        TabManager manager = TabManager.getInstance();
        Component text = Component.translatable("commands.message.display.incoming",
                Component.literal("Steve"), Component.literal("hi"));
        manager.onMessageReceived(text, new GuiMessage(0, text, null, null, null));
        assertEquals(List.of("steve"), manager.getDmTabs().stream()
                .map(tab -> tab.getDmPlayerName().toLowerCase()).toList());
        assertEquals(1, manager.getDmTabs().get(0).getMessages().size());
    }

    @Test
    void dmCommandOnlyAppliesWhileDmTabIsActive() {
        TabManager manager = TabManager.getInstance();
        ChatTab dm = manager.getOrCreateDmTab("UnicornBeef");
        manager.setActiveTab(dm, false);
        assertEquals("tell UnicornBeef hi", manager.getOutgoingDmCommand("hi"));

        manager.setActiveTab(manager.getChannelTab("local"), false);
        assertNull(manager.getOutgoingDmCommand("hi"));
    }

    /** Local is a quiet channel (no sound, no badge); [G] lines have no channel and land in All only. */
    private static ServerProfile mentionProfile(boolean mentionAlert) {
        ServerProfile profile = new ServerProfile("m", "", ">", List.of(
                new ChannelDefinition("local", "Local", "[L]", "l", true, false, false, false),
                new ChannelDefinition("kingdom", "Kingdom", "[K]", "", true, false)));
        profile.setMentionNames(List.of("UB"));
        profile.setMentionAlert(mentionAlert);
        return profile;
    }

    private static void receive(TabManager manager, String text) {
        manager.onMessageReceived(Component.literal(text), line(text));
    }

    @Test
    void mentionInQuietChannelBadgesAndPlings() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(mentionProfile(true));
        manager.resetSoundRequestsForTesting();

        receive(manager, "[L] Bob > just chatting");
        assertEquals(0, manager.getChannelTab("local").getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());

        receive(manager, "[L] Bob > ub, you there?");
        assertEquals(1, manager.getChannelTab("local").getUnreadCount());
        assertEquals(0, manager.getAllTabs().getFirst().getUnreadCount(), "badge only where the line has its tab");
        assertEquals(1, manager.soundRequestsForTesting());
    }

    @Test
    void mentionWithoutChannelBadgesAll() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(mentionProfile(true));
        manager.setActiveTab(manager.getChannelTab("local"), false);
        manager.resetSoundRequestsForTesting();

        receive(manager, "[G] [Town] Bob > hi there");
        receive(manager, "UB joined the game");
        assertEquals(0, manager.getAllTabs().getFirst().getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());

        receive(manager, "[G] [Town] Bob > ub trade?");
        assertEquals(1, manager.getAllTabs().getFirst().getUnreadCount());
        assertEquals(1, manager.soundRequestsForTesting());

        manager.setActiveTab(manager.getAllTabs().getFirst(), false);
        assertEquals(0, manager.getAllTabs().getFirst().getUnreadCount(), "opening All clears it");
    }

    @Test
    void mentionAlertOffKeepsQuietLinesSilent() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(mentionProfile(false));
        manager.setActiveTab(manager.getChannelTab("kingdom"), false);
        manager.resetSoundRequestsForTesting();

        receive(manager, "[L] Bob > ub, you there?");
        receive(manager, "[G] Bob > ub trade?");
        assertEquals(0, manager.getChannelTab("local").getUnreadCount());
        assertEquals(0, manager.getAllTabs().getFirst().getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());
    }

    @Test
    void mentionInActiveTabPlingsWithoutBadge() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(mentionProfile(true));
        manager.setActiveTab(manager.getChannelTab("local"), false);
        manager.resetSoundRequestsForTesting();

        receive(manager, "[L] Bob > ub look");
        assertEquals(0, manager.getChannelTab("local").getUnreadCount());
        assertEquals(1, manager.soundRequestsForTesting());
    }

    @Test
    void mentionInLoudChannelPlingsOnce() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(mentionProfile(true));
        manager.resetSoundRequestsForTesting();

        receive(manager, "[K] Bob > ub over here");
        assertEquals(1, manager.getChannelTab("kingdom").getUnreadCount());
        assertEquals(1, manager.soundRequestsForTesting());
    }

    private static ServerProfile systemProfile(boolean systemTab) {
        ServerProfile profile = new ServerProfile("s", "", ">", List.of(
                new ChannelDefinition("local", "Local", "[L]", "l", true, false)));
        profile.setSystemTab(systemTab);
        return profile;
    }

    @Test
    void systemTabIsHiddenUnlessEnabled() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(systemProfile(false));
        assertEquals(List.of("all", "local"), manager.getVisibleTabs().stream().map(ChatTab::getId).toList());

        manager.applyProfile(systemProfile(true));
        manager.getOrCreateDmTab("Bob");
        assertEquals(List.of("all", "local", "system", "dm_bob"),
                manager.getVisibleTabs().stream().map(ChatTab::getId).toList());
    }

    @Test
    void systemTabCollectsServerLinesWhichAlsoStayInAll() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(systemProfile(true));
        manager.setActiveTab(manager.getChannelTab("local"), false);
        manager.resetSoundRequestsForTesting();

        receive(manager, "Bob joined the game");
        receive(manager, "[L] Bob > local chat");
        receive(manager, "[G] [Town] Bob > global chat");
        receive(manager, "[Shop] You sold 64 dirt");

        ChatTab system = manager.getVisibleTabs().stream().filter(ChatTab::isSystemTab).findFirst().orElseThrow();
        assertEquals(List.of("[Shop] You sold 64 dirt", "Bob joined the game"),
                system.getMessages().stream().map(m -> m.content().getString()).toList());
        assertEquals(4, manager.getAllTabs().getFirst().getMessages().size());
        assertEquals(0, system.getUnreadCount());
        assertEquals(0, manager.soundRequestsForTesting());
    }

    @Test
    void disablingSystemTabWhileActiveFallsBackToAll() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(systemProfile(true));
        ChatTab system = manager.getVisibleTabs().stream().filter(ChatTab::isSystemTab).findFirst().orElseThrow();
        manager.setActiveTab(system, false);
        assertEquals("system", manager.getActiveTab().getId());

        manager.applyProfile(systemProfile(false));
        assertEquals("all", manager.getActiveTab().getId());
        manager.setActiveTab(system, false);
        assertEquals("all", manager.getActiveTab().getId(), "a hidden system tab cannot be selected");
    }

    @Test
    void clearingChatAlsoClearsADisabledSystemTab() {
        TabManager manager = TabManager.getInstance();
        manager.applyProfile(systemProfile(true));
        receive(manager, "Bob joined the game");
        manager.applyProfile(systemProfile(false));
        manager.clearAllMessages();

        manager.applyProfile(systemProfile(true));
        ChatTab system = manager.getVisibleTabs().stream().filter(ChatTab::isSystemTab).findFirst().orElseThrow();
        assertTrue(system.getMessages().isEmpty());
    }
}
