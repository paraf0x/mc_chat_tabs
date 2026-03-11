package com.chattabs.chat;

import net.minecraft.text.Text;

public class ChatMessage {
    private final Text originalMessage;
    private final ChatChannel channel;
    private final String senderName;  // For DMs: the other player (sender if incoming, recipient if outgoing)
    private final String content;
    private final long timestamp;
    private boolean outgoing = false;  // For DMs: true if we sent it

    public ChatMessage(Text originalMessage, ChatChannel channel, String senderName, String content) {
        this.originalMessage = originalMessage;
        this.channel = channel;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public Text getOriginalMessage() {
        return originalMessage;
    }

    public ChatChannel getChannel() {
        return channel;
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
        return channel == ChatChannel.DM;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }
}
