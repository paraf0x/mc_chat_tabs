package com.chattabs.tab;

import com.chattabs.chat.ChatChannel;
import com.chattabs.chat.ChatMessage;
import com.chattabs.chat.ChatParser;
import com.chattabs.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;

import java.util.*;

public class TabManager {
    private static TabManager instance;

    private final List<ChatTab> fixedTabs;
    private final Map<String, ChatTab> dmTabs;  // player name -> tab
    private ChatTab activeTab;
    private final List<TabChangeListener> listeners;

    private TabManager() {
        this.fixedTabs = new ArrayList<>();
        this.dmTabs = new LinkedHashMap<>();  // Maintains insertion order
        this.listeners = new ArrayList<>();

        // Create fixed tabs (no Party tab)
        fixedTabs.add(ChatTab.createAllTab());
        fixedTabs.add(ChatTab.createLocalTab());

        // Default to "All" tab
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

            // Send context switch command to server
            if (sendCommand && tab != oldTab) {
                sendContextSwitchCommand(tab);
            }

            notifyTabChanged(oldTab, activeTab);
        }
    }

    private void sendContextSwitchCommand(ChatTab tab) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // DM tabs don't switch server context - we prefix messages instead
        String command = switch (tab.getChannel()) {
            case GLOBAL -> "g";
            case LOCAL -> "l";
            case DM -> null;  // Don't send context switch for DM - we prefix messages
            case SYSTEM -> null;
        };

        if (command != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    /**
     * Returns the prefix to add to outgoing messages for the current tab.
     * For DM tabs, returns "/tell playername ", for others returns null.
     */
    public String getOutgoingMessagePrefix() {
        if (activeTab.isDmTab() && activeTab.getDmPlayerName() != null) {
            return "/tell " + activeTab.getDmPlayerName() + " ";
        }
        return null;
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
            // Switch to "All" tab if closing active DM tab
            setActiveTab(fixedTabs.get(0));
        }
    }

    public void onMessageReceived(Text message, ChatHudLine hudLine) {
        ChatMessage parsed = ChatParser.parse(message);

        // Auto-create DM tab for new conversations (before routing so it gets the message)
        if (parsed.isDM() && parsed.getSenderName() != null) {
            getOrCreateDmTab(parsed.getSenderName());
        }

        // Route message to appropriate tabs
        for (ChatTab tab : getAllTabs()) {
            if (tab.shouldShowMessage(parsed)) {
                tab.addMessage(hudLine);
                // Only increment unread for Local and DM tabs (not All/Global)
                if (tab != activeTab && tab.shouldTrackUnread()) {
                    tab.incrementUnread();
                }
            }
        }
    }

    public boolean shouldDisplayMessage(Text message) {
        // "All" tab shows everything
        if (activeTab.getId().equals("all")) {
            return true;
        }

        ChatMessage parsed = ChatParser.parse(message);
        return activeTab.shouldShowMessage(parsed);
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

    private void notifyTabChanged(ChatTab oldTab, ChatTab newTab) {
        // Refresh chat display with new tab's messages
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
    }

    public interface TabChangeListener {
        void onTabChanged(ChatTab oldTab, ChatTab newTab);
    }
}
