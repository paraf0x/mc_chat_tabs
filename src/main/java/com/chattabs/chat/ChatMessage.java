package com.chattabs.chat;

import net.minecraft.network.chat.Component;

public class ChatMessage {
    private final Component originalMessage;
    private final MessageKind kind;
    private final String channelId;   // Set for CHANNEL messages, null for DM/SYSTEM
    private final String senderName;  // For DMs: the other player (sender if incoming, recipient if outgoing)
    private final String content;
    private final long timestamp;
    private boolean outgoing = false;  // For DMs: true if we sent it
    private boolean mention = false;   // True if player's name is mentioned

    public ChatMessage(Component originalMessage, MessageKind kind, String channelId,
                       String senderName, String content) {
        this.originalMessage = originalMessage;
        this.kind = kind;
        this.channelId = channelId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public Component getOriginalMessage() {
        return originalMessage;
    }

    public MessageKind getKind() {
        return kind;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isDM() {
        return kind == MessageKind.DM;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    public boolean isMention() {
        return mention;
    }

    public void setMention(boolean mention) {
        this.mention = mention;
    }
}
