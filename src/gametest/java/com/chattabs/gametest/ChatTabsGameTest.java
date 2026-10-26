package com.chattabs.gametest;

import com.chattabs.ChatTabsConfig;
import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import com.chattabs.tab.ChatTab;
import com.chattabs.tab.TabManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.sounds.SoundEventListener;
import com.chattabs.ui.TabBar;
import com.chattabs.ui.settings.ChatTabsSettingsScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Drives the real chat screen: tab clicks, Ctrl+Tab, typing, history, incoming lines.
 * Every check is reported as CHATTABS-GAMETEST ok/FAIL; the run fails at the end if any failed.
 */
public class ChatTabsGameTest implements FabricClientGameTest {
    private static final String WHISPER = "commands.message.display."; // whispering yourself echoes outgoing + incoming
    private static final String CHAT = "chat.type.text:";
    private static final String EMOTE = "chat.type.emote:";

    // The user's Beacoland profile (local + kingdom), plus a global channel whose join command
    // vanilla knows, so switch commands are observable in singleplayer.
    private static final ServerProfile PROFILE = new ServerProfile("test", "", ">", List.of(
            new ChannelDefinition("global", "Global", "[G]", "me joined-global", true, false),
            new ChannelDefinition("local", "Local", "[L]", "me joined-local", true, false),
            new ChannelDefinition("kingdom", "Kingdom", "[K]", "channel join kingdom", false, false)));

    private final List<String> failures = new ArrayList<>();
    private ClientGameTestContext ctx;
    private TestSingleplayerContext world;
    private final TabManager tabs = TabManager.getInstance();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        TestWorldSave save;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            this.world = world;
            world.getConnection().waitForChunksDownload();
            ctx.runOnClient(mc -> tabs.applyProfile(PROFILE));
            ctx.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(true));

            routing();
            hudShowsOnlyActiveTab();
            liveFilterAndUnread();
            clickClearsUnread();
            ctrlTabCycles();
            sending();
            history();
            joinCommands();
            scrollResetsOnSwitch();
            closeTabs();
            singleWindowMode();
            mentions();
            systemTab();
            settingsScreen();
            showcase();

            // Stay in Local and leave the world: the JOIN on reopen re-applies the profile.
            clickTab("local");
            save = world.getWorldSave();
        }
        try (TestSingleplayerContext world = save.open()) {
            this.world = world;
            world.getConnection().waitForChunksDownload();
            rejoin();
        }

        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " check(s) failed:\n  " + String.join("\n  ", failures));
        }
    }

    // ------------------------------------------------------------------ scenarios

    private void routing() {
        incoming("[G] Bob > g-one");
        incoming("[L] Bob > l-one");
        incoming("[K] Bob > k-one");
        incoming("Server restarting soon");
        whisperSelf("dm-one");

        check("routing: Global has only [G]", texts("global"), List.of("[G] Bob > g-one"));
        check("routing: Local has only [L]", texts("local"), List.of("[L] Bob > l-one"));
        check("routing: Kingdom revealed", tabs.isChannelTabVisible("kingdom"), true);
        check("routing: Kingdom has only [K]", texts("kingdom"), List.of("[K] Bob > k-one"));
        check("routing: DM tab created", dmTab() != null, true);
        check("routing: DM tab has only the whisper pair",
                dmTab() == null ? List.of() : texts(dmTab().getId()),
                List.of("You whisper to Tester: dm-one", "Tester whispers to you: dm-one"));
        check("routing: All has everything", texts("all").size(), 6);
    }

    private void hudShowsOnlyActiveTab() {
        for (String id : List.of("global", "local", "kingdom", dmTab().getId(), "all")) {
            clickTab(id);
            check("hud after click " + id, hud(), texts(id));
        }
    }

    private void liveFilterAndUnread() {
        clickTab("local");
        int globalUnread = unread("global");
        incoming("[G] Bob > g-live");
        incoming("[L] Bob > l-live");
        incoming("plain-live");
        check("live: Local HUD shows the new [L] only", hud().stream().filter(t -> t.contains("live")).toList(),
                List.of("[L] Bob > l-live"));
        check("live: Global unread +1", unread("global"), globalUnread + 1);
        check("live: Local (active) unread stays 0", unread("local"), 0);
    }

    private void clickClearsUnread() {
        clickTab("global");
        check("click clears unread", unread("global"), 0);
        check("click shows new [G]", hud().contains("[G] Bob > g-live"), true);
    }

    private void ctrlTabCycles() {
        clickTab("all");
        List<String> order = visibleIds();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            ctrlTab(false);
            seen.add(activeId());
        }
        List<String> expected = new ArrayList<>(order.subList(1, order.size()));
        expected.add(order.getFirst());
        check("ctrl+tab cycles forward and wraps", seen, expected);

        ctrlTab(true);
        check("ctrl+shift+tab goes back and wraps", activeId(), order.getLast());
        check("hud follows ctrl+tab", hud(), texts(activeId()));
    }

    private void sending() {
        clickTab("all");
        typeAndSend("from-all");
        expectLast("send in All", CHAT, "from-all");

        clickTab("local");
        typeAndSend("from-local");
        expectLast("send in Local", CHAT, "from-local");

        clickTab(dmTab().getId());
        typeAndSend("from-dm");
        expectLast("send in DM", WHISPER, "from-dm");

        clickTab("local");
        typeAndSend("from-local-again");
        expectLast("send in Local after DM", CHAT, "from-local-again");

        clickTab(dmTab().getId());
        typeAndSend("/me slash-in-dm");
        expectLast("slash command in DM is not wrapped", EMOTE, "slash-in-dm");
    }

    private void history() {
        clickTab(dmTab().getId());
        typeAndSend("hist-dm");
        expectLast("history: DM line", WHISPER, "hist-dm");

        clickTab("local");
        recallAndSend(1);
        expectLast("history: ↑ in Local resends DM line as chat", CHAT, "hist-dm");

        clickTab(dmTab().getId());
        recallAndSend(1);
        expectLast("history: ↑ in DM resends Local line as whisper", WHISPER, "hist-dm");
    }

    private void joinCommands() {
        // applyProfile reset the server channel to the first one (global).
        clickTab("global");
        clickTab("local");
        expectLast("join: switching to Local sends its command", EMOTE, "joined-local");
        int before = allCount();
        clickTab("local");
        clickTab(dmTab().getId());
        clickTab("local");
        ctx.waitTicks(10);
        check("join: Local -> DM -> Local sends nothing", allCount(), before);
        clickTab("all");
        expectLast("join: All goes back to the first channel", EMOTE, "joined-global");
        before = allCount();
        clickTab("kingdom");
        ctx.waitTicks(10);
        check("join: unknown server command is skipped", allCount(), before);
    }

    private void scrollResetsOnSwitch() {
        clickTab("all");
        for (int i = 0; i < 40; i++) {
            incoming("filler " + i);
        }
        ctx.runOnClient(mc -> mc.gui.hud.getChat().scrollChat(15));
        check("scroll: scrolled up in All", scrollPos() > 0, true);
        clickTab("local");
        check("scroll: switching tabs starts at the newest line", scrollPos(), 0);
    }

    private void closeTabs() {
        clickTab(dmTab().getId());
        clickClose(dmTab().getId());
        check("close DM: active falls back to All", activeId(), "all");
        check("close DM: tab gone", dmTab() == null, true);
        check("close DM: HUD shows All", hud(), texts("all"));

        clickTab("kingdom");
        clickClose("kingdom");
        check("close Kingdom: active falls back to All", activeId(), "all");
        check("close Kingdom: hidden", tabs.isChannelTabVisible("kingdom"), false);
        incoming("[K] Bob > k-again");
        check("close Kingdom: new [K] reveals it again", tabs.isChannelTabVisible("kingdom"), true);
    }

    private void singleWindowMode() {
        ctx.runOnClient(mc -> {
            ChatTabsConfig.getInstance().setChatMode(ChatTabsConfig.ChatMode.SINGLE_WINDOW);
            tabs.refreshForModeChange();
        });
        clickTab("local");
        check("single: HUD shows All in Local", hud(), texts("all"));
        int globalUnread = unread("global");
        incoming("[G] Bob > g-single");
        check("single: no unread counting", unread("global"), globalUnread);
        typeAndSend("single-local");
        expectLast("single: Local sends chat", CHAT, "single-local");
        ctx.runOnClient(mc -> {
            ChatTabsConfig.getInstance().setChatMode(ChatTabsConfig.ChatMode.FILTERED);
            tabs.refreshForModeChange();
        });
        check("filtered again: HUD shows Local", hud(), texts("local"));
    }

    private void mentions() {
        // Like the user's Beacoland profile: no Global tab, [G] lines live in All. Local is quiet.
        ServerProfile profile = new ServerProfile("mention", "", ">", List.of(
                new ChannelDefinition("local", "Local", "[L]", "", true, false, false, false)));
        profile.setMentionAlert(true);
        profile.setMentionNames(List.of("UB"));
        ctx.runOnClient(mc -> tabs.applyProfile(profile));
        List<String> plings = new ArrayList<>();
        SoundEventListener listener = (sound, events, range) -> {
            if (sound.getIdentifier().getPath().contains("note_block.pling")) {
                plings.add(sound.getIdentifier().toString());
            }
        };
        ctx.runOnClient(mc -> mc.getSoundManager().addListener(listener));
        try {
            clickTab("local");
            incoming("[G] [Town] Bob > hi there");
            incoming("UB joined the game");
            ctx.waitTicks(2);
            check("mention: plain Global line stays quiet", List.of(unread("all"), plings.size()), List.of(0, 0));

            incoming("[G] [Town] Bob > ub trade?");
            ctx.waitTicks(2);
            check("mention: Global line with alias badges All", unread("all"), 1);
            check("mention: Global line with alias plings", plings.size(), 1);

            incoming("[L] Bob > hey tester");
            ctx.waitTicks(2);
            check("mention: own name in active quiet Local plings", plings.size(), 2);
            check("mention: active Local gets no badge", unread("local"), 0);

            clickTab("all");
            check("mention: opening All clears its badge", unread("all"), 0);
            incoming("[L] Bob > UB?");
            ctx.waitTicks(2);
            check("mention: quiet Local gets a badge", unread("local"), 1);
            check("mention: quiet Local plings", plings.size(), 3);

            typeAndSend("calling UB myself");
            expectLast("mention: own chat line arrives", CHAT, "calling UB myself");
            ctx.waitTicks(2);
            check("mention: own line never plings", plings.size(), 3);

            ctx.runOnClient(mc -> {
                profile.setMentionAlert(false);
                tabs.applyProfile(profile);
            });
            clickTab("local");
            incoming("[G] Bob > ub again");
            ctx.waitTicks(2);
            check("mention: alert off stays quiet", List.of(unread("all"), plings.size()), List.of(0, 3));
        } finally {
            ctx.runOnClient(mc -> mc.getSoundManager().removeListener(listener));
            ctx.runOnClient(mc -> tabs.applyProfile(PROFILE));
        }
    }

    private void systemTab() {
        ServerProfile profile = new ServerProfile("system", "", ">", List.of(
                new ChannelDefinition("local", "Local", "[L]", "", true, false)));
        profile.setSystemTab(true);
        ctx.runOnClient(mc -> tabs.applyProfile(profile));
        try {
            check("system: tab shown after channels", visibleIds().contains("system"), true);
            clickTab("system");
            incoming("Bob joined the game");
            incoming("[L] Bob > local chat");
            incoming("[G] [Town] Bob > global chat");
            incoming("[Shop] You sold 64 dirt");
            check("system: HUD shows server lines only", hud().stream().filter(t -> !t.startsWith("filler")).toList()
                    .subList(Math.max(0, hud().size() - 2), hud().size()),
                    List.of("Bob joined the game", "[Shop] You sold 64 dirt"));
            check("system: player lines not in System", hud().contains("[L] Bob > local chat")
                    || hud().contains("[G] [Town] Bob > global chat"), false);
            check("system: All still has them", texts("all").containsAll(List.of("Bob joined the game",
                    "[L] Bob > local chat", "[G] [Town] Bob > global chat", "[Shop] You sold 64 dirt")), true);
            typeAndSend("from-system");
            expectLast("system: typing sends plain chat", CHAT, "from-system");
            clickTab("local");
            incoming("Alice left the game");
            check("system: no badge on System", unread("system"), 0);
            ctx.runOnClient(mc -> {
                profile.setSystemTab(false);
                tabs.applyProfile(profile);
            });
            check("system: switched off hides the tab", visibleIds().contains("system"), false);
        } finally {
            ctx.runOnClient(mc -> tabs.applyProfile(PROFILE));
        }
    }

    /** Screenshots at the test window's small GUI and at the user's GUI scale 3 on a 1080p window. */
    private void settingsScreen() {
        ctx.getInput().setCursorPos(0, 0);
        ctx.setScreen(() -> new ChatTabsSettingsScreen(null));
        ctx.waitForScreen(ChatTabsSettingsScreen.class);
        ctx.waitTicks(2);
        System.out.println("CHATTABS-GAMETEST screenshot " + ctx.takeScreenshot("settings-small"));
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });
        ctx.waitTicks(2);
        System.out.println("CHATTABS-GAMETEST screenshot " + ctx.takeScreenshot("settings-gui3"));
        ctx.setScreen(() -> null);
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(0);
            mc.resizeGui();
        });
        ctx.waitTicks(2);
    }

    /**
     * Staged scenes for the README and Modrinth gallery, at GUI scale 3 on a 1080p window.
     * Written to build/run/clientGameTest/screenshots/ as showcase-*.png.
     */
    private void showcase() {
        ServerProfile profile = new ServerProfile("showcase", "", ">", List.of(
                new ChannelDefinition("local", "Local", "[L]", "l", true, false),
                new ChannelDefinition("kingdom", "Kingdom", "[K]", "channel join kingdom", false, false)));
        profile.setMentionAlert(true);
        profile.setMentionNames(List.of("Uni", "Beef"));
        profile.setSystemTab(true);
        world.getServer().runCommand("gamemode spectator Tester");
        world.getServer().runCommand("time set 1000");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(true);
            tabs.applyProfile(profile);
        });
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });
        try {
            clickTab("local");
            incoming("Steve joined the game");
            incoming("[G] [Deepstone] [Member] labsami > crimson planks! :D");
            incoming("[L] [Riverside] [Mayor] Alex > anyone up for the nether trip?");
            incoming("[G] [Harbor] [Trader] Notch > selling mending books, 10 diamonds");
            incoming("[K] [CrimsonHollow] [Lord] UnicornsBane > walls are done, gate next");
            incoming("[Shop] You sold 64 Dirt for 12 coins");
            incomingWhisper("Alex", "meet at spawn?");
            incoming("[L] [Riverside] [Mayor] Alex > bring torches");
            incoming("[G] [Deepstone] [Member] labsami > beef, got any iron to spare?");

            shot("showcase-tabs");
            clickTab("all");
            shot("showcase-all");
            clickTab("system");
            shot("showcase-system");
            clickTab("dm_alex");
            ctx.getInput().typeChars("on my way");
            shot("showcase-dm");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));

            ServerProfile saved = onClient(() -> ChatTabsConfig.getInstance().getProfiles().getFirst());
            ctx.runOnClient(mc -> {
                saved.setMentionAlert(true);
                saved.setMentionNames(List.of("Uni", "Beef"));
                saved.setSystemTab(true);
            });
            ctx.setScreen(() -> new ChatTabsSettingsScreen(null));
            ctx.waitForScreen(ChatTabsSettingsScreen.class);
            shot("showcase-settings");
            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> {
                saved.setMentionAlert(false);
                saved.setMentionNames(List.of());
                saved.setSystemTab(false);
            });
        } finally {
            world.getServer().runCommand("gamemode creative Tester");
            ctx.getInput().resizeWindow(1280, 720);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(0);
                mc.resizeGui();
                tabs.applyProfile(PROFILE);
            });
            ctx.waitTicks(2);
        }
    }

    private void shot(String name) {
        ctx.getInput().setCursorPos(0, 0);
        ctx.waitTicks(3);
        System.out.println("CHATTABS-GAMETEST screenshot " + ctx.takeScreenshot(name));
    }

    private void rejoin() {
        ctx.runOnClient(mc -> tabs.applyProfile(PROFILE)); // JOIN applied the run dir's default; use ours again
        check("rejoin: active tab is a visible tab", onClient(() -> tabs.getVisibleTabs().contains(tabs.getActiveTab())), true);
        check("rejoin: Local still highlighted", activeId(), "local");
        int unreadBefore = unread("local");
        incoming("[L] Bob > l-after-rejoin");
        check("rejoin: [L] in active Local counts no unread", unread("local"), unreadBefore);
        check("rejoin: HUD shows the new [L]", hud().contains("[L] Bob > l-after-rejoin"), true);
        // Leaving a world clears chat (vanilla Hud.onDisconnected), tabs included.
        check("rejoin: HUD shows Local only", hud(), texts("local"));
    }

    // ------------------------------------------------------------------ actions

    private void ensureChatOpen() {
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChatScreen)) {
            ctx.setScreen(() -> new ChatScreen("", false));
            ctx.waitForScreen(ChatScreen.class);
        }
    }

    private void clickTab(String tabId) {
        ensureChatOpen();
        double[] pos = ctx.computeOnClient(mc -> tabPoint(mc, tabId, false));
        ctx.getInput().setCursorPos(pos[0], pos[1]);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        check("click selects " + tabId, activeId(), tabId.toLowerCase());
    }

    private void clickClose(String tabId) {
        ensureChatOpen();
        double[] pos = ctx.computeOnClient(mc -> tabPoint(mc, tabId, true));
        ctx.getInput().setCursorPos(pos[0], pos[1]);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    /** Center of a tab label, or of its close button, from the tab bar's own layout (x 2, y height - 40). */
    private double[] tabPoint(Minecraft mc, String tabId, boolean closeButton) {
        for (TabBar.TabSlot slot : TabBar.layout(tabs, mc.font, 2)) {
            if (slot.tab().getId().equalsIgnoreCase(tabId)) {
                double scale = mc.getWindow().getGuiScale();
                double px = closeButton ? slot.closeX() + 4 : slot.x() + 6;
                double py = mc.getWindow().getGuiScaledHeight() - 40 + 7;
                return new double[]{px * scale, py * scale};
            }
        }
        throw new AssertionError("tab not visible: " + tabId);
    }

    /**
     * TestInput sends every key with modifiers 0 (and holdControl() is Cmd on macOS), so the
     * Ctrl+Tab event goes straight to the open chat screen with the SDL Ctrl/Shift bits set.
     */
    private void ctrlTab(boolean shift) {
        ensureChatOpen();
        int modifiers = 0x0040 | (shift ? 0x0001 : 0); // SDL_KMOD_LCTRL, SDL_KMOD_LSHIFT
        boolean handled = ctx.computeOnClient(mc -> mc.gui.screen().keyPressed(
                new KeyEvent(InputConstants.KEY_TAB, '\t', modifiers)));
        check("ctrl+tab handled", handled, true);
        ctx.waitTick();
    }

    private void typeAndSend(String text) {
        ensureChatOpen();
        ctx.getInput().typeChars(text);
        ctx.getInput().pressKey(InputConstants.KEY_RETURN);
    }

    private void recallAndSend(int steps) {
        ensureChatOpen();
        for (int i = 0; i < steps; i++) {
            ctx.getInput().pressKey(InputConstants.KEY_UP);
        }
        ctx.getInput().pressKey(InputConstants.KEY_RETURN);
    }

    /** A server line the client parses like any chat-plugin message. */
    private void incoming(String text) {
        int before = allCount();
        world.getServer().runOnServer(server ->
                server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(text), false));
        ctx.waitFor(mc -> allCountHere() > before, 100);
    }

    /** A vanilla whisper from another player, as the server would deliver it. */
    private void incomingWhisper(String from, String text) {
        int before = allCount();
        world.getServer().runOnServer(server -> server.getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.translatable("commands.message.display.incoming",
                        net.minecraft.network.chat.Component.literal(from),
                        net.minecraft.network.chat.Component.literal(text)), false));
        ctx.waitFor(mc -> allCountHere() > before, 100);
    }

    private void whisperSelf(String text) {
        int before = allCount();
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("tell Tester " + text));
        ctx.waitFor(mc -> allCountHere() >= before + 2, 100);
    }

    private void expectLast(String name, String key, String text) {
        try {
            ctx.waitFor(mc -> describe(newest()).contains(text), 100);
        } catch (Throwable t) {
            failures.add(name + ": nothing containing '" + text + "' arrived, newest " + describe(newest()));
            System.out.println("CHATTABS-GAMETEST FAIL " + name);
            return;
        }
        String got = ctx.computeOnClient(mc -> describe(newest()));
        check(name, got.startsWith(key) ? key : got, key);
    }

    // ------------------------------------------------------------------ reads

    private <T> void check(String name, T actual, T expected) {
        if (expected.equals(actual)) {
            System.out.println("CHATTABS-GAMETEST ok " + name);
        } else {
            String failure = name + ": expected " + expected + ", got " + actual;
            failures.add(failure);
            System.out.println("CHATTABS-GAMETEST FAIL " + failure);
        }
    }

    /** Client thread only. */
    private ChatTab tab(String id) {
        return tabs.getAllTabs().stream().filter(t -> t.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    private ChatTab dmTab() {
        return onClient(() -> tabs.getDmTabs().isEmpty() ? null : tabs.getDmTabs().getFirst());
    }

    private List<ChatTab> visibleTabs() {
        return onClient(tabs::getVisibleTabs);
    }

    private List<String> visibleIds() {
        return visibleTabs().stream().map(ChatTab::getId).toList();
    }

    private String activeId() {
        return onClient(() -> tabs.getActiveTab().getId());
    }

    private int unread(String id) {
        return onClient(() -> tab(id).getUnreadCount());
    }

    /** Oldest first, like the screen reads top to bottom. */
    private List<String> texts(String id) {
        return onClient(() -> tab(id).getMessages().reversed().stream().map(m -> m.content().getString()).toList());
    }

    private int allCount() {
        return onClient(this::allCountHere);
    }

    /** Client thread only, for waitFor predicates. */
    private int allCountHere() {
        return tabs.getAllTabs().getFirst().getMessages().size();
    }

    /** What the chat HUD holds right now, oldest first. */
    private List<String> hud() {
        return ctx.computeOnClient(mc -> mc.gui.hud.getChat().allMessages.reversed().stream()
                .map(m -> m.content().getString()).toList());
    }

    private int scrollPos() {
        return ctx.computeOnClient(mc -> {
            try {
                Field f = ChatComponent.class.getDeclaredField("chatScrollbarPos");
                f.setAccessible(true);
                return f.getInt(mc.gui.hud.getChat());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private GuiMessage newest() {
        ChatTab all = tabs.getAllTabs().getFirst();
        return all.getMessages().isEmpty() ? null : all.getMessages().getFirst();
    }

    private static String describe(GuiMessage message) {
        if (message == null) {
            return "";
        }
        String key = message.content().getContents() instanceof TranslatableContents t ? t.getKey() : "literal";
        return key + ":" + message.content().getString();
    }

    private <T> T onClient(Supplier<T> read) {
        return ctx.computeOnClient(mc -> read.get());
    }
}
