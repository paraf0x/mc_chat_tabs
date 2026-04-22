package com.chattabs.ui;

import com.chattabs.ChatTabsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;

public final class ChatLayoutMath {
    public static final int CHAT_BASE_X = 4;
    public static final int CHAT_BOTTOM_MARGIN = 40;
    public static final int AUTO_PEEK_BOTTOM_MARGIN = 54;

    public static final int PEEK_PADDING = 4;
    public static final int PEEK_LINE_HEIGHT = 9;
    public static final int PEEK_HEADER_GAP = 2;

    private ChatLayoutMath() {
    }

    public static int getChatWidth(MinecraftClient client) {
        double scale = Math.max(client.options.getChatScale().getValue(), 0.1);
        return (int) (ChatHud.getWidth(client.options.getChatWidth().getValue()) * scale);
    }

    public static int getChatHeight(MinecraftClient client) {
        double scale = Math.max(client.options.getChatScale().getValue(), 0.1);
        return (int) (ChatHud.getHeight(client.options.getChatHeightFocused().getValue()) * scale);
    }

    public static int getVanillaChatY(int screenHeight, int chatHeight) {
        return screenHeight - CHAT_BOTTOM_MARGIN - chatHeight;
    }

    public static int getConfiguredChatX(ChatTabsConfig config) {
        return CHAT_BASE_X + config.getChatOffsetX();
    }

    public static int getConfiguredChatY(ChatTabsConfig config, int screenHeight, int chatHeight) {
        return getVanillaChatY(screenHeight, chatHeight) + config.getChatOffsetY();
    }

    public static int getChatRenderOffsetX(ChatTabsConfig config) {
        return config.getChatOffsetX();
    }

    public static int getChatRenderOffsetY(ChatTabsConfig config) {
        return config.getChatOffsetY();
    }

    public static int getAutoPeekX() {
        return CHAT_BASE_X;
    }

    public static int getAutoPeekY(int screenHeight, int peekHeight) {
        return screenHeight - AUTO_PEEK_BOTTOM_MARGIN - peekHeight;
    }

    public static int computePeekHeight(int peekLines) {
        int lines = Math.max(1, peekLines);
        return PEEK_PADDING * 2 + PEEK_HEADER_GAP + PEEK_LINE_HEIGHT * (lines + 1);
    }
}
