package com.chattabs.tab;

import com.chattabs.chat.ChatMessage;
import com.chattabs.chat.MessageKind;
import com.chattabs.config.ChannelDefinition;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatTab {
    private final String id;
    private final String displayName;
    private final String channelId;     // Set for channel tabs, null for all/DM tabs
    private final String dmPlayerName;  // Only set for DM tabs
    private final boolean closable;
    private final boolean soundEnabled;
    private final boolean badgeEnabled;
    private final List<GuiMessage> messages;
    private int unreadCount;

    private ChatTab(String id, String displayName, String channelId, String dmPlayerName,
                    boolean closable, boolean soundEnabled, boolean badgeEnabled) {
        this.id = id;
        this.displayName = displayName;
        this.channelId = channelId;
        this.dmPlayerName = dmPlayerName;
        this.closable = closable;
        this.soundEnabled = soundEnabled;
        this.badgeEnabled = badgeEnabled;
        this.messages = new ArrayList<>();
        this.unreadCount = 0;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getDmPlayerName() {
        return dmPlayerName;
    }

    public boolean isAllTab() {
        return "all".equals(id);
    }

    public boolean isChannelTab() {
        return channelId != null;
    }

    public boolean isDmTab() {
        return dmPlayerName != null;
    }

    public boolean isClosable() {
        return closable;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean isBadgeEnabled() {
        return badgeEnabled;
    }

    public List<GuiMessage> getMessages() {
        return messages;
    }

    public void addMessage(GuiMessage message) {
        messages.add(0, message);  // Add to beginning (newest first)
        // Limit message history
        if (messages.size() > 100) {
            messages.remove(messages.size() - 1);
        }
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void incrementUnread() {
        unreadCount++;
    }

    public void clearUnread() {
        unreadCount = 0;
    }

    public void clearMessages() {
        messages.clear();
        unreadCount = 0;
    }

    public boolean shouldShowMessage(ChatMessage parsedMessage) {
        if (isAllTab()) {
            return true;
        }

        // DM tab for specific player - show both incoming and outgoing messages
        if (isDmTab()) {
            return parsedMessage.isDM()
                    && parsedMessage.getSenderName() != null
                    && parsedMessage.getSenderName().equalsIgnoreCase(dmPlayerName);
        }

        return parsedMessage.getKind() == MessageKind.CHANNEL
                && channelId.equalsIgnoreCase(parsedMessage.getChannelId());
    }

    /**
     * Returns true if this tab should track unread message counts.
     * Every tab except All does, unless its channel disabled the badge.
     */
    public boolean shouldTrackUnread() {
        return !isAllTab() && badgeEnabled;
    }

    public static ChatTab createAllTab() {
        return new ChatTab("all", "All", null, null, false, true, true);
    }

    public static ChatTab createChannelTab(ChannelDefinition definition) {
        return new ChatTab(definition.getId(), definition.getDisplayName(),
                definition.getId(), null, !definition.isAlwaysVisible(),
                definition.isSound(), definition.isBadge());
    }

    public static ChatTab createDmTab(String playerName) {
        return new ChatTab("dm_" + playerName.toLowerCase(), playerName, null, playerName,
                true, true, true);
    }
}
