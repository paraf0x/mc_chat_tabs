package com.chattabs.ui;

import com.chattabs.ChatTabsConfig;
import com.chattabs.tab.TabManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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
                Identifier.of("chattabs", "global_peek"),
                GlobalPeekOverlay::render
        );
    }

    private static final long FADE_DURATION_MS = 10_000L;
    private static final long FADE_OUT_MS = 2_000L;

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
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

        TextRenderer textRenderer = client.textRenderer;
        List<Text> coloredMessages = new ArrayList<>();
        int startIndex = Math.max(0, messages.size() - maxLines);
        for (int i = startIndex; i < messages.size(); i++) {
            coloredMessages.add(colorizeSender(messages.get(i).getString()));
        }

        List<Text> wrappedLines = new ArrayList<>();
        for (Text msg : coloredMessages) {
            List<Text> wrapped = wrapText(textRenderer, msg, textWidth);
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
            int screenHeight = client.getWindow().getScaledHeight();
            x = ChatLayoutMath.getAutoPeekX();
            y = ChatLayoutMath.getAutoPeekY(screenHeight, height);
        }

        int bgColor = (alpha << 24);
        context.fill(x, y, x + chatWidth, y + height, bgColor);

        int headerColor = (textAlpha << 24) | (HEADER_COLOR & 0x00FFFFFF);
        context.drawTextWithShadow(textRenderer, "Global", x + PADDING, y + PADDING, headerColor);

        int textColor = (textAlpha << 24) | (TEXT_COLOR & 0x00FFFFFF);
        int textY = y + PADDING + LINE_HEIGHT + HEADER_GAP;
        for (Text line : wrappedLines) {
            context.drawTextWithShadow(textRenderer, line, x + PADDING, textY, textColor);
            textY += LINE_HEIGHT;
        }
    }

    private static Text colorizeSender(String text) {
        Matcher matcher = SENDER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Text.literal(text);
        }

        String senderPart = matcher.group(1).trim();
        String messagePart = matcher.group(2).trim();

        // Extract the player name (last word in sender part, which may include rank)
        String[] parts = senderPart.split("\\s+");
        String playerName = parts[parts.length - 1];
        String rankPrefix = parts.length > 1 ? senderPart.substring(0, senderPart.lastIndexOf(playerName)).trim() + " " : "";

        MutableText result = Text.empty();
        if (!rankPrefix.isEmpty()) {
            result.append(Text.literal(rankPrefix).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
        }
        result.append(Text.literal(playerName).setStyle(Style.EMPTY.withColor(Formatting.YELLOW)));
        result.append(Text.literal(" > ").setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)));
        result.append(Text.literal(messagePart).setStyle(Style.EMPTY.withColor(Formatting.WHITE)));

        return result;
    }

    private static List<Text> wrapText(TextRenderer textRenderer, Text text, int maxWidth) {
        List<Text> lines = new ArrayList<>();
        String str = text.getString();

        // If it fits in one line, just return it
        if (textRenderer.getWidth(text) <= maxWidth) {
            lines.add(text);
            return lines;
        }

        // Simple word-wrap on the string, re-colorize each line
        String[] words = str.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String test = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textRenderer.getWidth(test) > maxWidth && !currentLine.isEmpty()) {
                lines.add(Text.literal(currentLine.toString()).setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) currentLine.append(" ");
                currentLine.append(word);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(Text.literal(currentLine.toString()).setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
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
