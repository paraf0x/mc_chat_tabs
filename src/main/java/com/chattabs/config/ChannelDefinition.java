package com.chattabs.config;

public class ChannelDefinition {
    private String id = "";
    private String displayName = "";
    private String tag = "";
    private String joinCommand = "";
    private boolean alwaysVisible = true;
    private boolean peek = false;
    private boolean sound = true;
    private boolean badge = true;

    public ChannelDefinition() {
    }

    public ChannelDefinition(String id, String displayName, String tag, String joinCommand,
                             boolean alwaysVisible, boolean peek) {
        this(id, displayName, tag, joinCommand, alwaysVisible, peek, true, true);
    }

    public ChannelDefinition(String id, String displayName, String tag, String joinCommand,
                             boolean alwaysVisible, boolean peek, boolean sound, boolean badge) {
        this.id = id;
        this.displayName = displayName;
        this.tag = tag;
        this.joinCommand = joinCommand;
        this.alwaysVisible = alwaysVisible;
        this.peek = peek;
        this.sound = sound;
        this.badge = badge;
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

    public boolean isSound() {
        return sound;
    }

    public boolean isBadge() {
        return badge;
    }

    public boolean isValid() {
        return !getId().isBlank() && !getTag().isBlank() && !getId().equalsIgnoreCase("all")
                && !getId().equalsIgnoreCase("system");
    }
}
