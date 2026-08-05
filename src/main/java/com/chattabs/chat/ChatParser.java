package com.chattabs.chat;

import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatParser {
    // DM patterns for this server format - flexible to handle various arrow characters
    // Outgoing: ✉ you → PlayerName > message  (or with different arrows like ->, →, ➔, etc.)
    // Incoming: ✉ PlayerName → YourName > message (server uses actual player name, not "you")
    // Arrow chars: → ➔ -> ► ▶ (Unicode and ASCII variants)
    // Note: no ^ anchor to handle color codes/prefixes before the envelope
    private static final String ARROW_CHARS = "[→➔►▶]|->|>";

    // Chat Heads mod inserts "[PlayerName head]" before player names - strip it
    private static final Pattern CHAT_HEADS_PATTERN = Pattern.compile("\\[\\w+ head\\]");

    // Generic DM pattern - captures both sides of the arrow
    // Format: ✉ SenderOrYou → RecipientOrYou > message
    private static final Pattern DM_PATTERN = Pattern.compile(
            "[✉]\\s*(.+?)\\s*(?:" + ARROW_CHARS + ")\\s*(.+?)\\s*>\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    // Fallback: check if message contains DM indicator anywhere (for unusual formats)
    private static final Pattern DM_INDICATOR_PATTERN = Pattern.compile("[✉].*(?:" + ARROW_CHARS + ").*>", Pattern.CASE_INSENSITIVE);

    private record ChannelPattern(String channelId, Pattern pattern) {
    }

    private static volatile List<ChannelPattern> channelPatterns = buildPatterns(ServerProfile.builtInDefault());

    public static void setActiveProfile(ServerProfile profile) {
        channelPatterns = buildPatterns(profile == null ? ServerProfile.builtInDefault() : profile);
    }

    private static List<ChannelPattern> buildPatterns(ServerProfile profile) {
        List<ChannelPattern> patterns = new ArrayList<>();
        for (ChannelDefinition channel : profile.validChannels()) {
            patterns.add(new ChannelPattern(channel.getId(),
                    buildChannelPattern(channel.getTag(), profile.getSeparator())));
        }
        return patterns;
    }

    static Pattern buildChannelPattern(String tag, String separator) {
        return Pattern.compile("^" + Pattern.quote(tag) + "\\s+(.+?)\\s*"
                + Pattern.quote(separator) + "\\s*(.*)$");
    }

    private static String getOwnPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            return client.player.getName().getString();
        }
        return null;
    }

    public static ChatMessage parse(Component message) {
        // Strip Chat Heads mod placeholders like "[PlayerName head]"
        String text = CHAT_HEADS_PATTERN.matcher(message.getString()).replaceAll("").trim();

        Matcher matcher;

        // Check DMs FIRST (before channel patterns) - more specific patterns
        // Format: ✉ SenderOrYou → RecipientOrYou > message
        matcher = DM_PATTERN.matcher(text);
        if (matcher.find()) {
            String leftSide = matcher.group(1).trim();   // sender or "you"
            String rightSide = matcher.group(2).trim();  // recipient or player name
            String content = matcher.group(3).trim();

            String ownName = getOwnPlayerName();
            boolean isOutgoing = leftSide.equalsIgnoreCase("you") ||
                    (ownName != null && leftSide.equalsIgnoreCase(ownName));

            // The "other player" is the one that isn't us
            String otherPlayer = isOutgoing ? rightSide : leftSide;

            ChatMessage msg = new ChatMessage(message, MessageKind.DM, null, otherPlayer, content);
            msg.setOutgoing(isOutgoing);
            return msg;
        }

        // Channel patterns from the active profile, in config order
        for (ChannelPattern channelPattern : channelPatterns) {
            matcher = channelPattern.pattern().matcher(text);
            if (matcher.find()) {
                String sender = matcher.group(1).trim();
                String content = matcher.group(2).trim();
                ChatMessage msg = new ChatMessage(message, MessageKind.CHANNEL,
                        channelPattern.channelId(), sender, content);
                String ownName = getOwnPlayerName();
                if (ownName != null && content.toLowerCase().contains(ownName.toLowerCase())) {
                    msg.setMention(true);
                }
                return msg;
            }
        }

        // Fallback: if message contains DM indicator, treat as DM (extract player name heuristically)
        if (DM_INDICATOR_PATTERN.matcher(text).find()) {
            String ownName = getOwnPlayerName();
            // Try to extract player name - look for pattern between ✉ and arrow
            Pattern extractPattern = Pattern.compile("[✉]\\s*(.+?)\\s*(?:" + ARROW_CHARS + ")");
            Matcher extractMatcher = extractPattern.matcher(text);
            if (extractMatcher.find()) {
                String leftSide = extractMatcher.group(1).trim();
                // Check if left side is us (outgoing)
                boolean isOutgoing = leftSide.equalsIgnoreCase("you") ||
                        (ownName != null && leftSide.equalsIgnoreCase(ownName));

                if (isOutgoing) {
                    // Find recipient after arrow
                    Pattern recipientPattern = Pattern.compile("(?:" + ARROW_CHARS + ")\\s*(.+?)\\s*>");
                    Matcher recipientMatcher = recipientPattern.matcher(text);
                    if (recipientMatcher.find()) {
                        ChatMessage msg = new ChatMessage(message, MessageKind.DM, null,
                                recipientMatcher.group(1).trim(), text);
                        msg.setOutgoing(true);
                        return msg;
                    }
                } else {
                    // Incoming - left side is the sender
                    return new ChatMessage(message, MessageKind.DM, null, leftSide, text);
                }
            }
        }

        // Default to SYSTEM for non-chat messages (notifications, etc.)
        return new ChatMessage(message, MessageKind.SYSTEM, null, null, text);
    }
}
