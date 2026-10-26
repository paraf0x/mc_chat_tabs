package com.chattabs.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/** Finds the player's names in chat text: whole words only, case-insensitive. */
public final class MentionMatcher {
    private MentionMatcher() {
    }

    public static boolean mentions(String text, Collection<String> names) {
        if (text == null || names == null) {
            return false;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Pattern word = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name.trim()) + "(?![\\p{L}\\p{N}_])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (word.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /** "Unicorn, UB" → [Unicorn, UB]; blanks dropped. */
    public static List<String> parseNames(String commaSeparated) {
        List<String> names = new ArrayList<>();
        if (commaSeparated == null) {
            return names;
        }
        for (String part : commaSeparated.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        return names;
    }

    public static String joinNames(List<String> names) {
        return String.join(", ", names);
    }
}
