package com.chattabs.chat;

public enum ChatChannel {
    GLOBAL("g", "[G]"),
    LOCAL("l", "[L]"),
    DM("tell", "✉"),
    SYSTEM(null, null);  // System messages, notifications, etc.

    private final String command;
    private final String indicator;

    ChatChannel(String command, String indicator) {
        this.command = command;
        this.indicator = indicator;
    }

    public String getCommand() {
        return command;
    }

    public String getIndicator() {
        return indicator;
    }

    public String getPrefix() {
        if (command == null) return "";
        return "/" + command + " ";
    }

    public String getPrefixWithPlayer(String playerName) {
        if (this == DM) {
            return "/tell " + playerName + " ";
        }
        return getPrefix();
    }
}
