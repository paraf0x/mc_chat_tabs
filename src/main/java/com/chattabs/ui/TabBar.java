package com.chattabs.ui;

import com.chattabs.tab.ChatTab;
import com.chattabs.tab.TabManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.List;

public class TabBar extends ClickableWidget {
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PADDING = 4;
    private static final int TAB_MARGIN = 2;

    private static final int COLOR_ACTIVE = 0xFF4CAF50;      // Green
    private static final int COLOR_INACTIVE = 0xFF424242;    // Dark gray
    private static final int COLOR_HOVER = 0xFF616161;       // Lighter gray
    private static final int COLOR_UNREAD = 0xFFFF5722;      // Orange for unread indicator
    private static final int COLOR_TEXT = 0xFFFFFFFF;        // White
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;    // Dimmed white

    private static final int CLOSE_BUTTON_WIDTH = 10;

    public TabBar(int x, int y, int width) {
        super(x, y, width, TAB_HEIGHT, Text.empty());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean isFocused() {
        return false;  // Never take focus from chat field
    }

    @Override
    public void setFocused(boolean focused) {
        // Ignore focus requests - keep focus on chat field
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Never consume key events - let ChatScreen handle them
        return false;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        TabManager tabManager = TabManager.getInstance();
        List<ChatTab> fixedTabs = tabManager.getFixedTabs();
        List<ChatTab> dmTabs = tabManager.getDmTabs();
        ChatTab activeTab = tabManager.getActiveTab();

        int currentX = getX();

        // Render fixed tabs
        for (ChatTab tab : fixedTabs) {
            currentX = renderTab(context, textRenderer, tab, currentX, mouseX, mouseY, activeTab, false);
        }

        // Render DM tabs inline (with close button)
        for (ChatTab tab : dmTabs) {
            currentX = renderTab(context, textRenderer, tab, currentX, mouseX, mouseY, activeTab, true);
        }
    }

    private int renderTab(DrawContext context, TextRenderer textRenderer, ChatTab tab, int x, int mouseX, int mouseY, ChatTab activeTab, boolean showCloseButton) {
        int textWidth = textRenderer.getWidth(tab.getDisplayName());
        int tabWidth = textWidth + TAB_PADDING * 2 + (showCloseButton ? CLOSE_BUTTON_WIDTH : 0);
        boolean isActive = tab == activeTab;
        boolean isHovered = isMouseOverArea(mouseX, mouseY, x, getY(), tabWidth, TAB_HEIGHT);

        // Background
        int bgColor = isActive ? COLOR_ACTIVE : (isHovered ? COLOR_HOVER : COLOR_INACTIVE);
        context.fill(x, getY(), x + tabWidth, getY() + TAB_HEIGHT, bgColor);

        // Text
        int textColor = isActive ? COLOR_TEXT : COLOR_TEXT_DIM;
        context.drawText(textRenderer, tab.getDisplayName(), x + TAB_PADDING, getY() + 3, textColor, false);

        // Unread indicator (only for non-active tabs)
        if (tab.getUnreadCount() > 0 && !isActive) {
            String unreadText = tab.getUnreadCount() > 99 ? "99+" : String.valueOf(tab.getUnreadCount());
            int unreadWidth = textRenderer.getWidth(unreadText) + 4;
            int unreadX = x + TAB_PADDING + textWidth + 2;
            context.fill(unreadX, getY() + 2, unreadX + unreadWidth, getY() + 11, COLOR_UNREAD);
            context.drawText(textRenderer, unreadText, unreadX + 2, getY() + 3, COLOR_TEXT, false);
        }

        // Close button for DM tabs
        if (showCloseButton) {
            int closeX = x + tabWidth - CLOSE_BUTTON_WIDTH;
            boolean closeHovered = isMouseOverArea(mouseX, mouseY, closeX, getY(), CLOSE_BUTTON_WIDTH, TAB_HEIGHT);
            context.drawText(textRenderer, "x", closeX + 2, getY() + 3, closeHovered ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        }

        return x + tabWidth + TAB_MARGIN;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return false;

        double mouseX = click.x();
        double mouseY = click.y();

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        TabManager tabManager = TabManager.getInstance();
        List<ChatTab> fixedTabs = tabManager.getFixedTabs();
        List<ChatTab> dmTabs = tabManager.getDmTabs();

        int currentX = getX();

        // Check fixed tabs
        for (ChatTab tab : fixedTabs) {
            int tabWidth = textRenderer.getWidth(tab.getDisplayName()) + TAB_PADDING * 2;
            if (isMouseOverArea((int) mouseX, (int) mouseY, currentX, getY(), tabWidth, TAB_HEIGHT)) {
                tabManager.setActiveTab(tab);
                // Don't return true - let the click "fall through" so focus stays on chat
                return false;
            }
            currentX += tabWidth + TAB_MARGIN;
        }

        // Check DM tabs (with close button)
        for (ChatTab tab : dmTabs) {
            int textWidth = textRenderer.getWidth(tab.getDisplayName());
            int tabWidth = textWidth + TAB_PADDING * 2 + CLOSE_BUTTON_WIDTH;

            if (isMouseOverArea((int) mouseX, (int) mouseY, currentX, getY(), tabWidth, TAB_HEIGHT)) {
                // Check if close button was clicked
                int closeX = currentX + tabWidth - CLOSE_BUTTON_WIDTH;
                if (mouseX >= closeX) {
                    tabManager.closeDmTab(tab.getDmPlayerName());
                } else {
                    tabManager.setActiveTab(tab);
                }
                // Don't return true - let the click "fall through" so focus stays on chat
                return false;
            }
            currentX += tabWidth + TAB_MARGIN;
        }

        return false;
    }

    private boolean isMouseOverArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // Accessibility narration for screen readers
    }
}
