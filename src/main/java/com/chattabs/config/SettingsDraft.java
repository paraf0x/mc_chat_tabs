package com.chattabs.config;

import com.chattabs.ChatTabsConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Working copy of everything the settings screen can edit. Screens mutate the
 * draft; nothing reaches the live config until {@link #commit(ChatTabsConfig)}.
 */
public class SettingsDraft {

    private final List<ServerProfile> profiles = new ArrayList<>();
    private int idleSwitchSeconds;
    private boolean peekEnabled;
    private int peekLines;
    private ChatTabsConfig.ChatMode chatMode;

    private SettingsDraft() {
    }

    public static SettingsDraft from(ChatTabsConfig config) {
        SettingsDraft draft = new SettingsDraft();
        draft.idleSwitchSeconds = config.getIdleSwitchSeconds();
        draft.peekEnabled = config.isGlobalPeekEnabled();
        draft.peekLines = config.getGlobalPeekLines();
        draft.chatMode = config.getChatMode();
        for (ServerProfile profile : config.getProfiles()) {
            if (profile != null) {
                draft.profiles.add(copyOf(profile));
            }
        }
        if (draft.profiles.isEmpty()) {
            draft.profiles.add(ServerProfile.builtInDefault());
        }
        return draft;
    }

    private static ServerProfile copyOf(ServerProfile source) {
        List<ChannelDefinition> channels = new ArrayList<>();
        for (ChannelDefinition channel : source.channelsForEditing()) {
            if (channel == null) {
                continue;
            }
            channels.add(new ChannelDefinition(channel.getId(), channel.getDisplayName(), channel.getTag(),
                    channel.getJoinCommand(), channel.isAlwaysVisible(), channel.isPeek(),
                    channel.isSound(), channel.isBadge()));
        }
        return new ServerProfile(source.getName(), source.getServerAddress(), source.getSeparator(), channels);
    }

    public List<ServerProfile> profiles() {
        return profiles;
    }

    public ServerProfile profile(int index) {
        return profiles.get(index);
    }

    public int profileCount() {
        return profiles.size();
    }

    public int idleSwitchSeconds() {
        return idleSwitchSeconds;
    }

    public void setIdleSwitchSeconds(int seconds) {
        this.idleSwitchSeconds = seconds;
    }

    public boolean peekEnabled() {
        return peekEnabled;
    }

    public void setPeekEnabled(boolean enabled) {
        this.peekEnabled = enabled;
    }

    public int peekLines() {
        return peekLines;
    }

    public void setPeekLines(int lines) {
        this.peekLines = Math.max(1, Math.min(10, lines));
    }

    public ChatTabsConfig.ChatMode chatMode() {
        return chatMode;
    }

    public void setChatMode(ChatTabsConfig.ChatMode mode) {
        this.chatMode = mode;
    }

    public void setSeparator(int profileIndex, String separator) {
        profile(profileIndex).setSeparator(separator);
    }

    /** Replaces the channel at {@code channelIndex}, or appends when it is negative. */
    public void putChannel(int profileIndex, int channelIndex, ChannelDefinition channel) {
        List<ChannelDefinition> channels = profile(profileIndex).channelsForEditing();
        if (channelIndex < 0 || channelIndex >= channels.size()) {
            channels.add(channel);
        } else {
            channels.set(channelIndex, channel);
        }
    }

    public void removeChannel(int profileIndex, int channelIndex) {
        List<ChannelDefinition> channels = profile(profileIndex).channelsForEditing();
        if (channelIndex >= 0 && channelIndex < channels.size()) {
            channels.remove(channelIndex);
        }
    }

    public int addProfileForAddress(String address, int copyFromIndex) {
        ServerProfile copy = copyOf(profile(copyFromIndex));
        profiles.add(new ServerProfile(address, address, copy.getSeparator(), copy.channelsForEditing()));
        return profiles.size() - 1;
    }

    public void commit(ChatTabsConfig config) {
        config.setIdleSwitchSeconds(idleSwitchSeconds);
        config.setGlobalPeekEnabled(peekEnabled);
        config.setGlobalPeekLines(peekLines);
        config.setChatMode(chatMode);
        config.getProfiles().clear();
        for (ServerProfile profile : profiles) {
            config.getProfiles().add(copyOf(profile));
        }
        config.save();
    }
}
