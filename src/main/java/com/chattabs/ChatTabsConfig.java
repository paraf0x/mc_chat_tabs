package com.chattabs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChatTabsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("chattabs.json");

    private static ChatTabsConfig instance;

    private int idleSwitchSeconds = -1;

    public static ChatTabsConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public int getIdleSwitchSeconds() {
        return idleSwitchSeconds;
    }

    public boolean isIdleSwitchDisabled() {
        return idleSwitchSeconds <= 0;
    }

    public void setIdleSwitchSeconds(int idleSwitchSeconds) {
        this.idleSwitchSeconds = idleSwitchSeconds <= 0 ? -1 : idleSwitchSeconds;
    }

    public String getIdleSwitchLabel() {
        return isIdleSwitchDisabled() ? "never" : idleSwitchSeconds + "s";
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            ChatTabsMod.LOGGER.error("Failed to save Chat Tabs config", exception);
        }
    }

    private static ChatTabsConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ChatTabsConfig loaded = GSON.fromJson(reader, ChatTabsConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException exception) {
                ChatTabsMod.LOGGER.error("Failed to load Chat Tabs config", exception);
            }
        }

        ChatTabsConfig config = new ChatTabsConfig();
        config.save();
        return config;
    }
}
