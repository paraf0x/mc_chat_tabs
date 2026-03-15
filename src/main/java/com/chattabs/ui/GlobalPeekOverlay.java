package com.chattabs.ui;

import com.chattabs.ChatTabsConfig;
import com.chattabs.tab.TabManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;

import java.util.List;

public final class GlobalPeekOverlay {
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 3;
    private static final int LINE_HEIGHT = 9;
    private static final int CHAT_LEFT = 4;
    private static final int CHAT_BOTTOM_OFFSET = 54;
    private static final int HEADER_GAP = 2;
    private static final int BG_COLOR = 0x88000000;
    private static final int HEADER_COLOR = 0xFFAAAAAA;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private GlobalPeekOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(GlobalPeekOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        ChatTabsConfig config = ChatTabsConfig.getInstance();
        if (!config.isGlobalPeekEnabled()) {
            return;
        }

        TabManager tabManager = TabManager.getInstance();
        if (tabManager.isAllTabActive()) {
            return;
        }

        List<Text> messages = tabManager.getGlobalPeekMessages();
        if (messages.isEmpty()) {
            return;
        }

        int maxLines = Math.max(1, config.getGlobalPeekLines());
        int lines = Math.min(maxLines, messages.size());
        int width = getPeekWidth(client, messages, lines);
        int height = PADDING_Y * 2 + HEADER_GAP + LINE_HEIGHT * (lines + 1);

        int x = CHAT_LEFT;
        int y = client.getWindow().getScaledHeight() - CHAT_BOTTOM_OFFSET - height;
        if (client.currentScreen instanceof ChatScreen) {
            y -= 14;
        }

        context.fill(x, y, x + width, y + height, BG_COLOR);
        context.drawTextWithShadow(client.textRenderer, "Global", x + PADDING_X, y + PADDING_Y, HEADER_COLOR);

        int textY = y + PADDING_Y + LINE_HEIGHT + HEADER_GAP;
        int startIndex = messages.size() - lines;
        for (int i = startIndex; i < messages.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, messages.get(i), x + PADDING_X, textY, TEXT_COLOR);
            textY += LINE_HEIGHT;
        }
    }

    private static int getPeekWidth(MinecraftClient client, List<Text> messages, int lines) {
        int width = client.textRenderer.getWidth("Global") + PADDING_X * 2;
        int startIndex = Math.max(0, messages.size() - lines);
        for (int i = startIndex; i < messages.size(); i++) {
            width = Math.max(width, client.textRenderer.getWidth(messages.get(i)) + PADDING_X * 2);
        }
        return Math.min(width, client.getWindow().getScaledWidth() / 2);
    }
}
