package com.chattabs.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ServerProfile {
    private String name = "default";
    private String serverAddress = "";
    private String separator = ">";
    private List<ChannelDefinition> channels = new ArrayList<>();

    public ServerProfile() {
    }

    public ServerProfile(String name, String serverAddress, String separator, List<ChannelDefinition> channels) {
        this.name = name;
        this.serverAddress = serverAddress;
        this.separator = separator;
        this.channels = new ArrayList<>(channels);
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public String getServerAddress() {
        return serverAddress == null ? "" : serverAddress;
    }

    public String getSeparator() {
        return separator == null || separator.isBlank() ? ">" : separator;
    }

    public boolean isDefaultProfile() {
        return getServerAddress().isBlank();
    }

    public boolean matchesAddress(String address) {
        if (address == null || isDefaultProfile()) {
            return false;
        }
        return getServerAddress().toLowerCase(Locale.ROOT).equals(address.toLowerCase(Locale.ROOT));
    }

    /** Valid channels in config order; invalid entries and duplicate ids are skipped. */
    public List<ChannelDefinition> validChannels() {
        List<ChannelDefinition> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (channels == null) {
            return result;
        }
        for (ChannelDefinition channel : channels) {
            if (channel == null || !channel.isValid()) {
                continue;
            }
            if (!seen.add(channel.getId().toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(channel);
        }
        return result;
    }

    public ChannelDefinition channelById(String id) {
        if (id == null) {
            return null;
        }
        for (ChannelDefinition channel : validChannels()) {
            if (channel.getId().equalsIgnoreCase(id)) {
                return channel;
            }
        }
        return null;
    }

    public static ServerProfile builtInDefault() {
        return new ServerProfile("default", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, true),
                new ChannelDefinition("local", "Local", "[L]", "l", true, false),
                new ChannelDefinition("kingdom", "Kingdom", "[K]", "channel join kingdom", false, false)
        ));
    }
}
