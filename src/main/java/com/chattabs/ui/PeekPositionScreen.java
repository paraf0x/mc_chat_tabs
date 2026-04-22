package com.chattabs.ui;

import com.chattabs.ChatTabsConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class PeekPositionScreen extends Screen {
    private static final int MIN_WIDTH = 60;
    private static final int MIN_HEIGHT = 30;
    private static final int BG_COLOR = 0x88000000;
    private static final int HANDLE_SIZE = 8;

    private static final int PEEK_BORDER = 0xFFFFFF00;   // Yellow
    private static final int PEEK_RESIZE = 0xFFFFAA00;
    private static final int CHAT_BORDER = 0xFF44AAFF;   // Blue
    private static final int CHAT_RESIZE = 0xFF2288DD;
    private static final int TEXT_COLOR  = 0xFFFFFFFF;
    private static final int HINT_COLOR  = 0xFFAAAAAA;
    private static final int LABEL_COLOR = 0xFFCCCCCC;

    // Peek box
    private int peekX, peekY, peekW, peekH;
    // Chat box (position is offset from vanilla bottom-left)
    private int chatX, chatY, chatW, chatH;

    private enum DragTarget { NONE, PEEK_MOVE, PEEK_RESIZE, CHAT_MOVE }
    private DragTarget dragTarget = DragTarget.NONE;
    private int dragOffsetX, dragOffsetY;

    public PeekPositionScreen() {
        super(Text.literal("Layout Editor"));
    }

    @Override
    protected void init() {
        ChatTabsConfig config = ChatTabsConfig.getInstance();
        int defaultChatW = ChatLayoutMath.getChatWidth(client);

        // --- Peek ---
        peekW = config.hasPeekWidth() ? config.getPeekWidth() : defaultChatW;
        peekH = ChatLayoutMath.computePeekHeight(config.getGlobalPeekLines());
        if (config.hasPeekPosition()) {
            peekX = config.getPeekX();
            peekY = config.getPeekY();
        } else {
            peekX = ChatLayoutMath.getAutoPeekX();
            peekY = ChatLayoutMath.getAutoPeekY(height, peekH);
        }

        // --- Chat ---
        chatW = defaultChatW;
        chatH = ChatLayoutMath.getChatHeight(client);
        chatX = ChatLayoutMath.getConfiguredChatX(config);
        chatY = ChatLayoutMath.getConfiguredChatY(config, height, chatH);

        clampPeek();
        clampChat();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.fill(0, 0, width, height, 0x66000000);

        // --- Chat box (blue) ---
        drawBox(context, chatX, chatY, chatW, chatH, CHAT_BORDER, BG_COLOR, false);
        context.drawTextWithShadow(textRenderer, "Chat", chatX + 4, chatY + 4, LABEL_COLOR);
        String[] chatSamples = { "[G] Player1 > Hello everyone", "[G] Player2 > What's up?", "[L] Nearby > local msg" };
        int cy = chatY + 4 + 11;
        for (String s : chatSamples) {
            if (cy + 9 > chatY + chatH) break;
            context.drawTextWithShadow(textRenderer, s, chatX + 4, cy, 0xFFBBBBBB);
            cy += 9;
        }

        // --- Peek box (yellow) ---
        drawBox(context, peekX, peekY, peekW, peekH, PEEK_BORDER, BG_COLOR, true);
        context.drawTextWithShadow(textRenderer, "Global Peek", peekX + 4, peekY + 4, LABEL_COLOR);
        String[] peekSamples = { "PlayerName > Hello world", "OtherPlayer > Example msg", "Someone > Third line" };
        int py = peekY + 4 + 11;
        for (String s : peekSamples) {
            if (py + 9 > peekY + peekH) break;
            context.drawTextWithShadow(textRenderer, s, peekX + 4, py, TEXT_COLOR);
            py += 9;
        }

        // Size labels
        String peekLabel = "Peek: " + peekW + "x" + peekH;
        context.drawTextWithShadow(textRenderer, peekLabel, peekX, peekY - 10, PEEK_BORDER);
        String chatLabel = "Chat offset: " + (chatX - ChatLayoutMath.CHAT_BASE_X)
                + ", " + (chatY - ChatLayoutMath.getVanillaChatY(height, chatH));
        context.drawTextWithShadow(textRenderer, chatLabel, chatX, chatY - 10, CHAT_BORDER);

        // Instructions
        String hint = "Drag to move  |  Orange corner = resize peek  |  ESC = save  |  R = reset all";
        int hintWidth = textRenderer.getWidth(hint);
        context.drawTextWithShadow(textRenderer, hint, (width - hintWidth) / 2, 10, HINT_COLOR);
    }

    private void drawBox(DrawContext context, int x, int y, int w, int h, int borderColor, int bgColor, boolean resizeHandle) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, borderColor);
        context.fill(x, y, x + w, y + h, bgColor);
        if (resizeHandle) {
            context.fill(x + w - HANDLE_SIZE, y + h - HANDLE_SIZE, x + w, y + h, PEEK_RESIZE);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();

        // Peek resize handle
        if (mx >= peekX + peekW - HANDLE_SIZE && mx <= peekX + peekW
                && my >= peekY + peekH - HANDLE_SIZE && my <= peekY + peekH) {
            dragTarget = DragTarget.PEEK_RESIZE;
            return true;
        }

        // Peek move
        if (isOver(mx, my, peekX, peekY, peekW, peekH)) {
            dragTarget = DragTarget.PEEK_MOVE;
            dragOffsetX = (int) mx - peekX;
            dragOffsetY = (int) my - peekY;
            return true;
        }

        // Chat move
        if (isOver(mx, my, chatX, chatY, chatW, chatH)) {
            dragTarget = DragTarget.CHAT_MOVE;
            dragOffsetX = (int) mx - chatX;
            dragOffsetY = (int) my - chatY;
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && dragTarget != DragTarget.NONE) {
            dragTarget = DragTarget.NONE;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() != 0) return super.mouseDragged(click, deltaX, deltaY);

        switch (dragTarget) {
            case PEEK_MOVE -> {
                peekX = (int) click.x() - dragOffsetX;
                peekY = (int) click.y() - dragOffsetY;
                clampPeek();
                return true;
            }
            case PEEK_RESIZE -> {
                peekW = Math.max(MIN_WIDTH, (int) click.x() - peekX);
                peekH = Math.max(MIN_HEIGHT, (int) click.y() - peekY);
                clampPeek();
                return true;
            }
            case CHAT_MOVE -> {
                chatX = (int) click.x() - dragOffsetX;
                chatY = (int) click.y() - dragOffsetY;
                clampChat();
                return true;
            }
            default -> { return super.mouseDragged(click, deltaX, deltaY); }
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_R) {
            ChatTabsConfig config = ChatTabsConfig.getInstance();
            config.resetPeekPosition();
            config.resetChatOffset();
            config.save();
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        ChatTabsConfig config = ChatTabsConfig.getInstance();

        // Save peek
        config.setPeekPosition(peekX, peekY);
        config.setPeekWidth(peekW);

        // Save chat offset (relative to vanilla position)
        int vanillaChatY = ChatLayoutMath.getVanillaChatY(height, chatH);
        config.setChatOffset(chatX - ChatLayoutMath.CHAT_BASE_X, chatY - vanillaChatY);

        config.save();
        super.close();
    }

    private boolean isOver(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    private void clampPeek() {
        peekW = Math.min(peekW, width);
        peekH = Math.min(peekH, height);
        peekX = Math.max(0, Math.min(peekX, width - peekW));
        peekY = Math.max(0, Math.min(peekY, height - peekH));
    }

    private void clampChat() {
        chatX = Math.max(0, Math.min(chatX, width - chatW));
        chatY = Math.max(0, Math.min(chatY, height - chatH));
    }
}
