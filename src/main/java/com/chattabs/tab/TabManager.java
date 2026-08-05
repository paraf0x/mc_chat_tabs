package com.chattabs.tab;

import com.chattabs.ChatHudAccessor;
import com.chattabs.ChatTabsConfig;
import com.chattabs.chat.ChatMessage;
import com.chattabs.chat.ChatParser;
import com.chattabs.chat.MessageKind;
import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.*;
import java.util.regex.Pattern;

public class TabManager {
    private static TabManager instance;
    private static final long DM_FAILURE_CONTEXT_WINDOW_MS = 5_000L;
    private static final Pattern CHAT_HEADS_PATTERN = Pattern.compile("\\[\\w+ head\\]");

    private ServerProfile activeProfile;
    private final ChatTab allTab;
    private final Map<String, ChatTab> channelTabs = new LinkedHashMap<>(); // config order, key = channel id lowercase
    private final Set<String> revealedChannels = new HashSet<>();
    private final Map<String, ChatTab> dmTabs;
    private ChatTab activeTab;
    private final List<TabChangeListener> listeners;
    private final List<Component> globalPeekMessages;

    private long activeTabLastActivityAt = System.currentTimeMillis();
    private String lastServerChannelId;
    private String lastOutgoingDmPlayer;
    private long lastOutgoingDmAt;
    private String forcedVisibleMessage;
    private long forcedVisibleMessageAt;
    private long lastPeekMessageAt;

    private TabManager() {
        this.allTab = ChatTab.createAllTab();
        this.dmTabs = new LinkedHashMap<>();
        this.listeners = new ArrayList<>();
        this.globalPeekMessages = new ArrayList<>();

        applyProfile(ChatTabsConfig.getInstance().profileForAddress(null));
        activeTab = allTab;
    }

    public static TabManager getInstance() {
        if (instance == null) {
            instance = new TabManager();
        }
        return instance;
    }

    static void resetForTesting() {
        instance = null;
    }

    public void applyProfile(ServerProfile profile) {
        ServerProfile effective = profile == null ? ServerProfile.builtInDefault() : profile;
        Map<String, ChatTab> oldTabs = new LinkedHashMap<>(channelTabs);
        channelTabs.clear();
        revealedChannels.clear();
        for (ChannelDefinition channel : effective.validChannels()) {
            String key = channel.getId().toLowerCase(Locale.ROOT);
            ChatTab tab = ChatTab.createChannelTab(channel);
            ChatTab old = oldTabs.get(key);
            if (old != null) {
                tab.getMessages().addAll(old.getMessages());
            }
            channelTabs.put(key, tab);
        }
        activeProfile = effective;
        lastServerChannelId = firstChannelId();
        if (activeTab == null || (activeTab.isChannelTab()
                && !channelTabs.containsKey(activeTab.getChannelId().toLowerCase(Locale.ROOT)))) {
            activeTab = allTab;
        }
        ChatParser.setActiveProfile(effective);
        refreshChatDisplay();
    }

    public ServerProfile getActiveProfile() {
        return activeProfile;
    }

    private String firstChannelId() {
        return channelTabs.isEmpty() ? null : channelTabs.keySet().iterator().next();
    }

    public ChatTab getChannelTab(String id) {
        return id == null ? null : channelTabs.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean isChannelTabVisible(String id) {
        ChatTab tab = getChannelTab(id);
        if (tab == null) {
            return false;
        }
        return !tab.isClosable() || revealedChannels.contains(id.toLowerCase(Locale.ROOT));
    }

    public void revealChannelTab(String id) {
        if (getChannelTab(id) != null) {
            revealedChannels.add(id.toLowerCase(Locale.ROOT));
        }
    }

    public void closeChannelTab(String id) {
        ChatTab tab = getChannelTab(id);
        if (tab == null || !tab.isClosable()) {
            return;
        }
        revealedChannels.remove(id.toLowerCase(Locale.ROOT));
        if (activeTab == tab) {
            setActiveTab(allTab, true);
        }
    }

    public List<ChatTab> getVisibleTabs() {
        List<ChatTab> visible = new ArrayList<>();
        visible.add(allTab);
        for (ChatTab tab : channelTabs.values()) {
            if (!tab.isClosable() || revealedChannels.contains(tab.getId().toLowerCase(Locale.ROOT))) {
                visible.add(tab);
            }
        }
        visible.addAll(dmTabs.values());
        return visible;
    }

    public List<ChatTab> getAllTabs() {
        List<ChatTab> all = new ArrayList<>();
        all.add(allTab);
        all.addAll(channelTabs.values());
        all.addAll(dmTabs.values());
        return all;
    }

    public List<ChatTab> getDmTabs() {
        return new ArrayList<>(dmTabs.values());
    }

    public ChatTab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(ChatTab tab) {
        setActiveTab(tab, true);
    }

    public void setActiveTab(ChatTab tab, boolean sendCommand) {
        if (tab != null && (tab == allTab || channelTabs.containsValue(tab) || dmTabs.containsValue(tab))) {
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        String targetChannelId = tab.isAllTab() ? firstChannelId() : tab.getChannelId();
        if (targetChannelId == null || targetChannelId.equalsIgnoreCase(lastServerChannelId)) {
            return;
        }

        ChannelDefinition definition = activeProfile.channelById(targetChannelId);
        if (definition == null || definition.getJoinCommand().isBlank()) {
            return;
        }

        client.player.connection.sendCommand(definition.getJoinCommand());
        lastServerChannelId = targetChannelId;
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

    /**
     * Tracks channel-switch commands the user types manually so the mod's
     * channel state stays in sync (a join command with extra arguments may
     * just send a one-off message on some servers, so only exact matches
     * count as switches).
     */
    public void noteOutgoingCommand(String rawInput) {
        if (rawInput == null || !rawInput.startsWith("/")) {
            return;
        }

        String normalized = rawInput.substring(1).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (ChannelDefinition channel : activeProfile.validChannels()) {
            String join = channel.getJoinCommand();
            if (!join.isBlank() && normalized.equals(join.toLowerCase(Locale.ROOT))) {
                lastServerChannelId = channel.getId();
                if (!channel.isAlwaysVisible()) {
                    revealChannelTab(channel.getId());
                }
                return;
            }
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
            setActiveTab(allTab, false);
        }
    }

    public void onMessageReceived(Component message, GuiMessage hudLine) {
        ChatMessage parsed = ChatParser.parse(message);

        if (parsed.isDM() && parsed.getSenderName() != null) {
            getOrCreateDmTab(parsed.getSenderName());
        }

        if (parsed.getKind() == MessageKind.CHANNEL) {
            revealChannelTab(parsed.getChannelId());
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

                if (tab != activeTab && tab.shouldTrackUnread() && !isSingleWindowMode()) {
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

    private void recordGlobalPeekMessage(ChatMessage parsed, Component message) {
        if (parsed.getKind() != MessageKind.CHANNEL || activeProfile == null) {
            return;
        }
        ChannelDefinition definition = activeProfile.channelById(parsed.getChannelId());
        if (definition == null || !definition.isPeek()) {
            return;
        }

        // Strip "[PlayerName head]" placeholders from Chat Heads mod and the channel tag
        String raw = CHAT_HEADS_PATTERN.matcher(message.getString()).replaceAll("").trim();
        String display = raw.replaceFirst("^" + Pattern.quote(definition.getTag()) + "\\s*", "");

        int maxMessages = Math.max(1, ChatTabsConfig.getInstance().getGlobalPeekLines());
        globalPeekMessages.add(Component.literal(display));
        while (globalPeekMessages.size() > maxMessages) {
            globalPeekMessages.remove(0);
        }
        lastPeekMessageAt = System.currentTimeMillis();
    }

    private boolean mirrorLikelyDmFailure(Component message, GuiMessage hudLine, ChatMessage parsed) {
        if (parsed.getKind() != MessageKind.SYSTEM) {
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

        if (dmTab != activeTab && !isSingleWindowMode()) {
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        if (parsed.isMention()) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
        } else {
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.0f);
        }
    }

    public boolean shouldDisplayMessage(Component message) {
        if (isSingleWindowMode()) {
            return true;
        }

        if (activeTab.isAllTab()) {
            return true;
        }

        if (shouldForceDisplay(message)) {
            return true;
        }

        ChatMessage parsed = ChatParser.parse(message);
        return activeTab.shouldShowMessage(parsed);
    }

    public boolean isAllTabActive() {
        return activeTab != null && activeTab.isAllTab();
    }

    public List<Component> getGlobalPeekMessages() {
        return List.copyOf(globalPeekMessages);
    }

    public long getLastPeekMessageAt() {
        return lastPeekMessageAt;
    }

    public void addListener(TabChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TabChangeListener listener) {
        listeners.remove(listener);
    }

    public void checkIdleTimeout() {
        if (activeTab == null || activeTab.isAllTab()) {
            return;
        }

        ChatTabsConfig config = ChatTabsConfig.getInstance();
        if (config.isIdleSwitchDisabled()) {
            return;
        }

        long timeoutMs = config.getIdleSwitchSeconds() * 1000L;
        if (System.currentTimeMillis() - activeTabLastActivityAt >= timeoutMs) {
            setActiveTab(allTab, true);
        }
    }

    private void touchActiveTabActivity() {
        activeTabLastActivityAt = System.currentTimeMillis();
    }

    private void forceDisplayMessage(String message) {
        forcedVisibleMessage = message;
        forcedVisibleMessageAt = System.currentTimeMillis();
    }

    private boolean shouldForceDisplay(Component message) {
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null) {
            return;
        }

        ChatComponent chatHud = client.gui.hud.getChat();
        if (chatHud instanceof ChatHudAccessor accessor) {
            ChatTab tabToDisplay = isSingleWindowMode() ? allTab : activeTab;
            if (tabToDisplay == null) {
                tabToDisplay = allTab;
            }
            accessor.chattabs$refreshWithMessages(tabToDisplay.getMessages());
        }
    }

    public void clearAllMessages() {
        for (ChatTab tab : getAllTabs()) {
            tab.clearMessages();
        }
        globalPeekMessages.clear();
    }

    public void refreshForModeChange() {
        if (isSingleWindowMode()) {
            clearUnreadCounts();
        }
        refreshChatDisplay();
    }

    public boolean isSingleWindowMode() {
        return ChatTabsConfig.getInstance().isSingleWindowMode();
    }

    private void clearUnreadCounts() {
        for (ChatTab tab : getAllTabs()) {
            tab.clearUnread();
        }
    }

    public interface TabChangeListener {
        void onTabChanged(ChatTab oldTab, ChatTab newTab);
    }
}
