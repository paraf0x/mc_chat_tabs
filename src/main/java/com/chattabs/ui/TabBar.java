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

import java.util.ArrayList;
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
        ChatTab activeTab = tabManager.getActiveTab();
        boolean showUnreadIndicators = !tabManager.isSingleWindowMode();

        for (TabSlot slot : buildTabSlots(tabManager, textRenderer)) {
            renderTab(context, textRenderer, slot, mouseX, mouseY, activeTab, showUnreadIndicators);
        }
    }

    private void renderTab(DrawContext context, TextRenderer textRenderer, TabSlot slot, int mouseX, int mouseY,
                           ChatTab activeTab, boolean showUnreadIndicators) {
        ChatTab tab = slot.tab();
        int x = slot.x();
        int tabWidth = slot.width();
        int textWidth = textRenderer.getWidth(tab.getDisplayName());
        boolean isActive = tab == activeTab;
        boolean isHovered = isMouseOverArea(mouseX, mouseY, x, getY(), tabWidth, TAB_HEIGHT);

        // Background
        int bgColor = isActive ? COLOR_ACTIVE : (isHovered ? COLOR_HOVER : COLOR_INACTIVE);
        context.fill(x, getY(), x + tabWidth, getY() + TAB_HEIGHT, bgColor);

        // Text
        int textColor = isActive ? COLOR_TEXT : COLOR_TEXT_DIM;
        context.drawText(textRenderer, tab.getDisplayName(), x + TAB_PADDING, getY() + 3, textColor, false);

        // Unread indicator (only for non-active tabs)
        if (showUnreadIndicators && tab.getUnreadCount() > 0 && !isActive) {
            String unreadText = tab.getUnreadCount() > 99 ? "99+" : String.valueOf(tab.getUnreadCount());
            int unreadWidth = textRenderer.getWidth(unreadText) + 4;
            int unreadX = x + TAB_PADDING + textWidth + 2;
            context.fill(unreadX, getY() + 2, unreadX + unreadWidth, getY() + 11, COLOR_UNREAD);
            context.drawText(textRenderer, unreadText, unreadX + 2, getY() + 3, COLOR_TEXT, false);
        }

        // Close button for DM tabs
        if (slot.dmTab()) {
            int closeX = slot.closeX();
            boolean closeHovered = isMouseOverArea(mouseX, mouseY, closeX, getY(), CLOSE_BUTTON_WIDTH, TAB_HEIGHT);
            context.drawText(textRenderer, "x", closeX + 2, getY() + 3, closeHovered ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return false;

        double mouseX = click.x();
        double mouseY = click.y();

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        TabManager tabManager = TabManager.getInstance();
        for (TabSlot slot : buildTabSlots(tabManager, textRenderer)) {
            if (isMouseOverArea((int) mouseX, (int) mouseY, slot.x(), getY(), slot.width(), TAB_HEIGHT)) {
                if (slot.dmTab() && mouseX >= slot.closeX() && mouseX < slot.closeX() + CLOSE_BUTTON_WIDTH) {
                    tabManager.closeDmTab(slot.tab().getDmPlayerName());
                } else {
                    tabManager.setActiveTab(slot.tab());
                }
                // Don't return true - let the click "fall through" so focus stays on chat
                return false;
            }
        }

        return false;
    }

    private List<TabSlot> buildTabSlots(TabManager tabManager, TextRenderer textRenderer) {
        List<TabSlot> slots = new ArrayList<>();
        int currentX = getX();

        for (ChatTab tab : tabManager.getFixedTabs()) {
            int tabWidth = textRenderer.getWidth(tab.getDisplayName()) + TAB_PADDING * 2;
            slots.add(new TabSlot(tab, currentX, tabWidth, false, -1));
            currentX += tabWidth + TAB_MARGIN;
        }

        for (ChatTab tab : tabManager.getDmTabs()) {
            int textWidth = textRenderer.getWidth(tab.getDisplayName());
            int tabWidth = textWidth + TAB_PADDING * 2 + CLOSE_BUTTON_WIDTH;
            int closeX = currentX + tabWidth - CLOSE_BUTTON_WIDTH;
            slots.add(new TabSlot(tab, currentX, tabWidth, true, closeX));
            currentX += tabWidth + TAB_MARGIN;
        }

        return slots;
    }

    private boolean isMouseOverArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // Accessibility narration for screen readers
    }

    private record TabSlot(ChatTab tab, int x, int width, boolean dmTab, int closeX) {
    }
}
