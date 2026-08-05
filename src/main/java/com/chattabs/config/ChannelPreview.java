package com.chattabs.config;

import com.chattabs.chat.ChatParser;

import java.util.regex.Matcher;

/**
 * How a sample chat line parses with a given tag and separator. Uses the very
 * same pattern builder the runtime parser uses, so the preview cannot drift.
 */
public record ChannelPreview(boolean matched, String sender, String content) {

    private static final ChannelPreview NO_MATCH = new ChannelPreview(false, "", "");

    public static ChannelPreview of(String tag, String separator, String sampleLine) {
        if (tag == null || tag.isBlank() || sampleLine == null) {
            return NO_MATCH;
        }
        String effectiveSeparator = separator == null || separator.isBlank() ? ">" : separator;
        Matcher matcher = ChatParser.buildChannelPattern(tag, effectiveSeparator).matcher(sampleLine.trim());
        if (!matcher.find()) {
            return NO_MATCH;
        }
        return new ChannelPreview(true, matcher.group(1).trim(), matcher.group(2).trim());
    }

    public static String sampleLineFor(String tag) {
        String effectiveTag = tag == null || tag.isBlank() ? "[G]" : tag;
        return effectiveTag + " [Rank] PlayerName > message text";
    }
}
