package com.chattabs.tab;

import com.chattabs.ChatHudAccessor;
import com.chattabs.ChatTabsConfig;
import com.chattabs.chat.ChatChannel;
import com.chattabs.chat.ChatMessage;
import com.chattabs.chat.ChatParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;
import java.util.regex.Pattern;

public class TabManager {
    private static TabManager instance;
    private static final long DM_FAILURE_CONTEXT_WINDOW_MS = 5_000L;
    private static final Pattern CHAT_HEADS_PATTERN = Pattern.compile("\\[\\w+ head\\]");

    private final List<ChatTab> fixedTabs;
    private final Map<String, ChatTab> dmTabs;
    private ChatTab activeTab;
    private final List<TabChangeListener> listeners;
    private final List<Text> globalPeekMessages;

    private long activeTabLastActivityAt = System.currentTimeMillis();
    private ChatChannel lastServerChannel = ChatChannel.GLOBAL;
    private String lastOutgoingDmPlayer;
    private long lastOutgoingDmAt;
    private String forcedVisibleMessage;
    private long forcedVisibleMessageAt;
    private long lastPeekMessageAt;

    private TabManager() {
        this.fixedTabs = new ArrayList<>();
        this.dmTabs = new LinkedHashMap<>();
        this.listeners = new ArrayList<>();
        this.globalPeekMessages = new ArrayList<>();

        fixedTabs.add(ChatTab.createAllTab());
        fixedTabs.add(ChatTab.createLocalTab());

        activeTab = fixedTabs.get(0);
    }

    public static TabManager getInstance() {
        if (instance == null) {
            instance = new TabManager();
        }
        return instance;
    }

    public List<ChatTab> getFixedTabs() {
        return Collections.unmodifiableList(fixedTabs);
    }

    public List<ChatTab> getDmTabs() {
        return new ArrayList<>(dmTabs.values());
    }

    public List<ChatTab> getAllTabs() {
        List<ChatTab> all = new ArrayList<>(fixedTabs);
        all.addAll(dmTabs.values());
        return all;
    }

    public ChatTab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(ChatTab tab) {
        setActiveTab(tab, true);
    }

    public void setActiveTab(ChatTab tab, boolean sendCommand) {
        if (tab != null && (fixedTabs.contains(tab) || dmTabs.containsValue(tab))) {
            ChatTab oldTab = activeTab;
            activeTab = tab;
            activeTab.clearUnread();
            touchActiveTabActivity();

            if (sendCommand && tab != oldTab) {
                sendContextSwitchCommand(tab);
            }

            notifyTabChanged(oldTab, activeTab);
        }
    }

    private void sendContextSwitchCommand(ChatTab tab) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ChatChannel targetChannel;
        if ("all".equals(tab.getId())) {
            targetChannel = ChatChannel.GLOBAL;
        } else {
            targetChannel = tab.getChannel();
        }

        if (targetChannel == lastServerChannel) {
            return;
        }

        String command = switch (targetChannel) {
            case GLOBAL -> "g";
            case LOCAL -> "l";
            case DM -> null;
            case SYSTEM -> null;
        };

        if (command != null) {
            client.player.networkHandler.sendChatCommand(command);
            lastServerChannel = targetChannel;
        }
    }

    public String getOutgoingMessagePrefix() {
        if (activeTab.isDmTab() && activeTab.getDmPlayerName() != null) {
            return "/tell " + activeTab.getDmPlayerName() + " ";
        }
        return null;
    }

    public void noteOutgoingMessage() {
        touchActiveTabActivity();

        if (activeTab.isDmTab() && activeTab.getDmPlayerName() != null) {
            lastOutgoingDmPlayer = activeTab.getDmPlayerName();
            lastOutgoingDmAt = System.currentTimeMillis();
            getOrCreateDmTab(activeTab.getDmPlayerName());
        }
    }

    public void setActiveTabById(String tabId) {
        for (ChatTab tab : getAllTabs()) {
            if (tab.getId().equals(tabId)) {
                setActiveTab(tab);
                return;
            }
        }
    }

    public void setActiveTabByIndex(int index) {
        if (index >= 0 && index < fixedTabs.size()) {
            setActiveTab(fixedTabs.get(index));
        }
    }

    public ChatTab getOrCreateDmTab(String playerName) {
        String key = playerName.toLowerCase();
        if (!dmTabs.containsKey(key)) {
            ChatTab newTab = ChatTab.createDmTab(playerName);
            dmTabs.put(key, newTab);
        }
        return dmTabs.get(key);
    }

    public void closeDmTab(String playerName) {
        String key = playerName.toLowerCase();
        ChatTab tab = dmTabs.remove(key);
        if (tab != null && activeTab == tab) {
            setActiveTab(fixedTabs.get(0), false);
        }
    }

    public void onMessageReceived(Text message, ChatHudLine hudLine) {
        ChatMessage parsed = ChatParser.parse(message);

        if (parsed.isDM() && parsed.getSenderName() != null) {
            getOrCreateDmTab(parsed.getSenderName());
        }

        recordGlobalPeekMessage(parsed, message);
        boolean mirroredDmFailure = mirrorLikelyDmFailure(message, hudLine, parsed);

        boolean playedSound = false;
        for (ChatTab tab : getAllTabs()) {
            if (tab.shouldShowMessage(parsed)) {
                tab.addMessage(hudLine);

                if (tab == activeTab) {
                    touchActiveTabActivity();
                }

                if (tab != activeTab && tab.shouldTrackUnread()) {
                    tab.incrementUnread();
                    if (!playedSound) {
                        playNotificationSound(parsed);
                        playedSound = true;
                    }
                }
            }
        }

        if (!playedSound && parsed.isMention()) {
            playNotificationSound(parsed);
        }

        if (mirroredDmFailure && activeTab.isDmTab() && activeTab.getDmPlayerName() != null
                && activeTab.getDmPlayerName().equalsIgnoreCase(lastOutgoingDmPlayer)) {
            forceDisplayMessage(message.getString());
        }
    }

    private void recordGlobalPeekMessage(ChatMessage parsed, Text message) {
        if (parsed.getChannel() != ChatChannel.GLOBAL) {
            return;
        }

        // Strip "[PlayerName head]" placeholders from Chat Heads mod and build clean text
        String raw = CHAT_HEADS_PATTERN.matcher(message.getString()).replaceAll("").trim();
        // Remove the [G] prefix for peek display
        String display = raw.replaceFirst("^\\[G\\]\\s*", "");

        int maxMessages = Math.max(1, ChatTabsConfig.getInstance().getGlobalPeekLines());
        globalPeekMessages.add(Text.literal(display));
        while (globalPeekMessages.size() > maxMessages) {
            globalPeekMessages.remove(0);
        }
        lastPeekMessageAt = System.currentTimeMillis();
    }

    private boolean mirrorLikelyDmFailure(Text message, ChatHudLine hudLine, ChatMessage parsed) {
        if (parsed.getChannel() != ChatChannel.SYSTEM) {
            return false;
        }

        if (lastOutgoingDmPlayer == null) {
            return false;
        }

        if (System.currentTimeMillis() - lastOutgoingDmAt > DM_FAILURE_CONTEXT_WINDOW_MS) {
            return false;
        }

        String text = message.getString().toLowerCase(Locale.ROOT);
        if (!looksLikeDmDeliveryFailure(text)) {
            return false;
        }

        ChatTab dmTab = getOrCreateDmTab(lastOutgoingDmPlayer);
        dmTab.addMessage(hudLine);

        if (dmTab != activeTab) {
            dmTab.incrementUnread();
        } else {
            touchActiveTabActivity();
        }

        return true;
    }

    private boolean looksLikeDmDeliveryFailure(String text) {
        return text.contains("not online")
                || text.contains("offline")
                || text.contains("unknown player")
                || text.contains("player not found")
                || text.contains("cannot message")
                || text.contains("can't message")
                || text.contains("unable to message")
                || text.contains("no player was found")
                || text.contains("not accepting messages");
    }

    private void playNotificationSound(ChatMessage parsed) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (parsed.isMention()) {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
        } else {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.5f, 1.0f);
        }
    }

    public boolean shouldDisplayMessage(Text message) {
        if (activeTab.getId().equals("all")) {
            return true;
        }

        if (shouldForceDisplay(message)) {
            return true;
        }

        ChatMessage parsed = ChatParser.parse(message);
        return activeTab.shouldShowMessage(parsed);
    }

    public boolean isAllTabActive() {
        return activeTab != null && "all".equals(activeTab.getId());
    }

    public List<Text> getGlobalPeekMessages() {
        return List.copyOf(globalPeekMessages);
    }

    public long getLastPeekMessageAt() {
        return lastPeekMessageAt;
    }

    public String getActivePrefix() {
        return activeTab.getOutgoingPrefix();
    }

    public void addListener(TabChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TabChangeListener listener) {
        listeners.remove(listener);
    }

    public void checkIdleTimeout() {
        if (activeTab == null || activeTab.getId().equals("all")) {
            return;
        }

        ChatTabsConfig config = ChatTabsConfig.getInstance();
        if (config.isIdleSwitchDisabled()) {
            return;
        }

        long timeoutMs = config.getIdleSwitchSeconds() * 1000L;
        if (System.currentTimeMillis() - activeTabLastActivityAt >= timeoutMs) {
            setActiveTab(fixedTabs.get(0), true);
        }
    }

    private void touchActiveTabActivity() {
        activeTabLastActivityAt = System.currentTimeMillis();
    }

    private void forceDisplayMessage(String message) {
        forcedVisibleMessage = message;
        forcedVisibleMessageAt = System.currentTimeMillis();
    }

    private boolean shouldForceDisplay(Text message) {
        if (forcedVisibleMessage == null) {
            return false;
        }

        if (System.currentTimeMillis() - forcedVisibleMessageAt > 2_000L) {
            forcedVisibleMessage = null;
            return false;
        }

        if (forcedVisibleMessage.equals(message.getString())) {
            forcedVisibleMessage = null;
            return true;
        }

        return false;
    }

    private void notifyTabChanged(ChatTab oldTab, ChatTab newTab) {
        refreshChatDisplay();

        for (TabChangeListener listener : listeners) {
            listener.onTabChanged(oldTab, newTab);
        }
    }

    private void refreshChatDisplay() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) return;

        ChatHud chatHud = client.inGameHud.getChatHud();
        if (chatHud instanceof ChatHudAccessor accessor) {
            accessor.chattabs$refreshWithMessages(activeTab.getMessages());
        }
    }

    public void clearAllMessages() {
        for (ChatTab tab : getAllTabs()) {
            tab.clearMessages();
        }
        globalPeekMessages.clear();
    }

    public interface TabChangeListener {
        void onTabChanged(ChatTab oldTab, ChatTab newTab);
    }
}
