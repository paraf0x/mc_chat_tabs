package com.chattabs.config;

public class ChannelDefinition {
    private String id = "";
    private String displayName = "";
    private String tag = "";
    private String joinCommand = "";
    private boolean alwaysVisible = true;
    private boolean peek = false;

    public ChannelDefinition() {
    }

    public ChannelDefinition(String id, String displayName, String tag, String joinCommand,
                             boolean alwaysVisible, boolean peek) {
        this.id = id;
        this.displayName = displayName;
        this.tag = tag;
        this.joinCommand = joinCommand;
        this.alwaysVisible = alwaysVisible;
        this.peek = peek;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getDisplayName() {
        return displayName == null || displayName.isBlank() ? getId() : displayName;
    }

    public String getTag() {
        return tag == null ? "" : tag;
    }

    public String getJoinCommand() {
        return joinCommand == null ? "" : joinCommand;
    }

    public boolean isAlwaysVisible() {
        return alwaysVisible;
    }

    public boolean isPeek() {
        return peek;
    }

    public boolean isValid() {
        return !getId().isBlank() && !getTag().isBlank() && !getId().equalsIgnoreCase("all");
    }
}
