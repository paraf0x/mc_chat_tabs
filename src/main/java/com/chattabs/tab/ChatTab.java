package com.chattabs.tab;

import com.chattabs.chat.ChatChannel;
import com.chattabs.chat.ChatMessage;
import net.minecraft.client.gui.hud.ChatHudLine;

import java.util.ArrayList;
import java.util.List;

public class ChatTab {
    private final String id;
    private final String displayName;
    private final ChatChannel channel;
    private final String dmPlayerName;  // Only set for DM tabs
    private final List<ChatHudLine> messages;
    private int unreadCount;

    public ChatTab(String id, String displayName, ChatChannel channel) {
        this(id, displayName, channel, null);
    }

    public ChatTab(String id, String displayName, ChatChannel channel, String dmPlayerName) {
        this.id = id;
        this.displayName = displayName;
        this.channel = channel;
        this.dmPlayerName = dmPlayerName;
        this.messages = new ArrayList<>();
        this.unreadCount = 0;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatChannel getChannel() {
        return channel;
    }

    public String getDmPlayerName() {
        return dmPlayerName;
    }

    public boolean isDmTab() {
        return dmPlayerName != null;
    }

    public List<ChatHudLine> getMessages() {
        return messages;
    }

    public void addMessage(ChatHudLine message) {
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
        // "All" tab shows everything
        if (id.equals("all")) {
            return true;
        }

        // DM tab for specific player - show both incoming and outgoing messages
        if (isDmTab()) {
            return parsedMessage.isDM() &&
                   parsedMessage.getSenderName() != null &&
                   parsedMessage.getSenderName().equalsIgnoreCase(dmPlayerName);
        }

        // Local tab only shows [L] messages
        if (id.equals("local")) {
            return parsedMessage.getChannel() == ChatChannel.LOCAL;
        }

        // Channel-specific tabs
        return parsedMessage.getChannel() == channel;
    }

    /**
     * Returns true if this tab should track unread message counts.
     * Only Local and DM tabs track unread - All/Global does not.
     */
    public boolean shouldTrackUnread() {
        return id.equals("local") || isDmTab();
    }

    public String getOutgoingPrefix() {
        if (isDmTab()) {
            return channel.getPrefixWithPlayer(dmPlayerName);
        }
        return channel.getPrefix();
    }

    public static ChatTab createAllTab() {
        return new ChatTab("all", "All", ChatChannel.GLOBAL);
    }

    public static ChatTab createLocalTab() {
        return new ChatTab("local", "Local", ChatChannel.LOCAL);
    }

    public static ChatTab createDmTab(String playerName) {
        return new ChatTab("dm_" + playerName.toLowerCase(), playerName, ChatChannel.DM, playerName);
    }
}
