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
    private boolean globalPeekEnabled = true;
    private int globalPeekLines = 4;
    private int peekX = -1;      // -1 = auto
    private int peekY = -1;      // -1 = auto
    private int peekWidth = -1;  // -1 = auto (match chat width)
    private int chatOffsetX = 0;
    private int chatOffsetY = 0;
    private ChatMode chatMode = ChatMode.FILTERED;

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

    public boolean isGlobalPeekEnabled() {
        return globalPeekEnabled;
    }

    public void setGlobalPeekEnabled(boolean globalPeekEnabled) {
        this.globalPeekEnabled = globalPeekEnabled;
    }

    public int getGlobalPeekLines() {
        return Math.max(1, globalPeekLines);
    }

    public void setGlobalPeekLines(int globalPeekLines) {
        this.globalPeekLines = Math.max(1, globalPeekLines);
    }

    public int getPeekX() {
        return peekX;
    }

    public int getPeekY() {
        return peekY;
    }

    public int getPeekWidth() {
        return peekWidth;
    }

    public boolean hasPeekWidth() {
        return peekWidth > 0;
    }

    public void setPeekPosition(int x, int y) {
        this.peekX = x;
        this.peekY = y;
    }

    public void setPeekWidth(int width) {
        this.peekWidth = Math.max(60, width);
    }

    public void resetPeekPosition() {
        this.peekX = -1;
        this.peekY = -1;
        this.peekWidth = -1;
    }

    public boolean hasPeekPosition() {
        return peekX >= 0 && peekY >= 0;
    }

    public int getChatOffsetX() {
        return chatOffsetX;
    }

    public int getChatOffsetY() {
        return chatOffsetY;
    }

    public boolean hasChatOffset() {
        return chatOffsetX != 0 || chatOffsetY != 0;
    }

    public void setChatOffset(int x, int y) {
        this.chatOffsetX = x;
        this.chatOffsetY = y;
    }

    public void resetChatOffset() {
        this.chatOffsetX = 0;
        this.chatOffsetY = 0;
    }

    public ChatMode getChatMode() {
        return chatMode == null ? ChatMode.FILTERED : chatMode;
    }

    public boolean isSingleWindowMode() {
        return getChatMode() == ChatMode.SINGLE_WINDOW;
    }

    public void setChatMode(ChatMode chatMode) {
        this.chatMode = chatMode == null ? ChatMode.FILTERED : chatMode;
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
                    loaded.normalize();
                    return loaded;
                }
            } catch (IOException exception) {
                ChatTabsMod.LOGGER.error("Failed to load Chat Tabs config", exception);
            }
        }

        ChatTabsConfig config = new ChatTabsConfig();
        config.normalize();
        config.save();
        return config;
    }

    private void normalize() {
        this.globalPeekLines = Math.max(1, globalPeekLines);
        if (peekWidth != -1) {
            this.peekWidth = Math.max(60, peekWidth);
        }
        if (chatMode == null) {
            this.chatMode = ChatMode.FILTERED;
        }
    }

    public enum ChatMode {
        FILTERED("filtered"),
        SINGLE_WINDOW("single-window");

        private final String commandName;

        ChatMode(String commandName) {
            this.commandName = commandName;
        }

        public String getCommandName() {
            return commandName;
        }
    }
}
