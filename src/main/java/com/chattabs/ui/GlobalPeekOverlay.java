package com.chattabs.ui;

import com.chattabs.ChatTabsConfig;
import com.chattabs.tab.TabManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GlobalPeekOverlay {
    private static final int PADDING = ChatLayoutMath.PEEK_PADDING;
    private static final int LINE_HEIGHT = ChatLayoutMath.PEEK_LINE_HEIGHT;
    private static final int HEADER_GAP = ChatLayoutMath.PEEK_HEADER_GAP;
    private static final int BG_COLOR = 0x88000000;
    private static final int HEADER_COLOR = 0xFFAAAAAA;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    // Pattern: "Rank PlayerName > message" — name is the last word before " > "
    private static final Pattern SENDER_PATTERN = Pattern.compile("^(.+?)\\s*>\\s*(.*)$");

    private GlobalPeekOverlay() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("chattabs", "global_peek"),
                GlobalPeekOverlay::render
        );
    }

    private static final long FADE_DURATION_MS = 10_000L;
    private static final long FADE_OUT_MS = 2_000L;

    private static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
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

        List<Component> messages = tabManager.getGlobalPeekMessages();
        if (messages.isEmpty()) {
            return;
        }

        long elapsed = System.currentTimeMillis() - tabManager.getLastPeekMessageAt();
        if (elapsed > FADE_DURATION_MS) {
            return;
        }

        // Calculate opacity for fade-out in the last FADE_OUT_MS
        float opacity = 1.0f;
        if (elapsed > FADE_DURATION_MS - FADE_OUT_MS) {
            opacity = 1.0f - (float) (elapsed - (FADE_DURATION_MS - FADE_OUT_MS)) / FADE_OUT_MS;
        }
        int alpha = (int) (opacity * 0x88);
        if (alpha <= 4) return;
        int textAlpha = (int) (opacity * 255);
        if (textAlpha <= 4) return;

        int chatWidth;
        if (config.hasPeekWidth()) {
            chatWidth = config.getPeekWidth();
        } else {
            chatWidth = ChatLayoutMath.getChatWidth(client);
        }
        int maxLines = Math.max(1, config.getGlobalPeekLines());
        int textWidth = chatWidth - PADDING * 2;

        Font textRenderer = client.font;
        List<Component> coloredMessages = new ArrayList<>();
        int startIndex = Math.max(0, messages.size() - maxLines);
        for (int i = startIndex; i < messages.size(); i++) {
            coloredMessages.add(colorizeSender(messages.get(i).getString()));
        }

        List<Component> wrappedLines = new ArrayList<>();
        for (Component msg : coloredMessages) {
            List<Component> wrapped = wrapText(textRenderer, msg, textWidth);
            wrappedLines.addAll(wrapped);
        }

        int totalLines = wrappedLines.size();
        int height = PADDING * 2 + HEADER_GAP + LINE_HEIGHT * (totalLines + 1);

        int x;
        int y;
        if (config.hasPeekPosition()) {
            x = config.getPeekX();
            y = config.getPeekY();
        } else {
            int screenHeight = context.guiHeight();
            x = ChatLayoutMath.getAutoPeekX();
            y = ChatLayoutMath.getAutoPeekY(screenHeight, height);
        }

        int bgColor = (alpha << 24);
        context.fill(x, y, x + chatWidth, y + height, bgColor);

        int headerColor = (textAlpha << 24) | (HEADER_COLOR & 0x00FFFFFF);
        context.text(textRenderer, "Global", x + PADDING, y + PADDING, headerColor, true);

        int textColor = (textAlpha << 24) | (TEXT_COLOR & 0x00FFFFFF);
        int textY = y + PADDING + LINE_HEIGHT + HEADER_GAP;
        for (Component line : wrappedLines) {
            context.text(textRenderer, line, x + PADDING, textY, textColor, true);
            textY += LINE_HEIGHT;
        }
    }

    private static Component colorizeSender(String text) {
        Matcher matcher = SENDER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Component.literal(text);
        }

        String senderPart = matcher.group(1).trim();
        String messagePart = matcher.group(2).trim();

        // Extract the player name (last word in sender part, which may include rank)
        String[] parts = senderPart.split("\\s+");
        String playerName = parts[parts.length - 1];
        String rankPrefix = parts.length > 1 ? senderPart.substring(0, senderPart.lastIndexOf(playerName)).trim() + " " : "";

        MutableComponent result = Component.empty();
        if (!rankPrefix.isEmpty()) {
            result.append(Component.literal(rankPrefix).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
        }
        result.append(Component.literal(playerName).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
        result.append(Component.literal(" > ").setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY)));
        result.append(Component.literal(messagePart).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));

        return result;
    }

    private static List<Component> wrapText(Font textRenderer, Component text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        String str = text.getString();

        // If it fits in one line, just return it
        if (textRenderer.width(text) <= maxWidth) {
            lines.add(text);
            return lines;
        }

        // Simple word-wrap on the string, re-colorize each line
        String[] words = str.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String test = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textRenderer.width(test) > maxWidth && !currentLine.isEmpty()) {
                lines.add(Component.literal(currentLine.toString()).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) currentLine.append(" ");
                currentLine.append(word);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(Component.literal(currentLine.toString()).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
        }

        // Keep the first line colorized
        if (!lines.isEmpty()) {
            Matcher matcher = SENDER_PATTERN.matcher(lines.get(0).getString());
            if (matcher.find()) {
                lines.set(0, colorizeSender(lines.get(0).getString()));
            }
        }

        return lines;
    }
}
