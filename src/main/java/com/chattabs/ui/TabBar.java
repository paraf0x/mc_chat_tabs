package com.chattabs.ui;

import com.chattabs.tab.ChatTab;
import com.chattabs.tab.TabManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TabBar extends AbstractWidget {
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
        super(x, y, width, TAB_HEIGHT, Component.empty());
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
    public boolean keyPressed(KeyEvent input) {
        // Never consume key events - let ChatScreen handle them
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Font textRenderer = Minecraft.getInstance().font;
        TabManager tabManager = TabManager.getInstance();
        ChatTab activeTab = tabManager.getActiveTab();
        boolean showUnreadIndicators = !tabManager.isSingleWindowMode();

        for (TabSlot slot : buildTabSlots(tabManager, textRenderer)) {
            renderTab(context, textRenderer, slot, mouseX, mouseY, activeTab, showUnreadIndicators);
        }
    }

    private void renderTab(GuiGraphicsExtractor context, Font textRenderer, TabSlot slot, int mouseX, int mouseY,
                           ChatTab activeTab, boolean showUnreadIndicators) {
        ChatTab tab = slot.tab();
        int x = slot.x();
        int tabWidth = slot.width();
        int textWidth = textRenderer.width(tab.getDisplayName());
        boolean isActive = tab == activeTab;
        boolean isHovered = isMouseOverArea(mouseX, mouseY, x, getY(), tabWidth, TAB_HEIGHT);

        // Background
        int bgColor = isActive ? COLOR_ACTIVE : (isHovered ? COLOR_HOVER : COLOR_INACTIVE);
        context.fill(x, getY(), x + tabWidth, getY() + TAB_HEIGHT, bgColor);

        // Text
        int textColor = isActive ? COLOR_TEXT : COLOR_TEXT_DIM;
        context.text(textRenderer, tab.getDisplayName(), x + TAB_PADDING, getY() + 3, textColor, false);

        // Unread indicator (only for non-active tabs)
        if (showUnreadIndicators && tab.getUnreadCount() > 0 && !isActive) {
            String unreadText = tab.getUnreadCount() > 99 ? "99+" : String.valueOf(tab.getUnreadCount());
            int unreadWidth = textRenderer.width(unreadText) + 4;
            int unreadX = x + TAB_PADDING + textWidth + 2;
            context.fill(unreadX, getY() + 2, unreadX + unreadWidth, getY() + 11, COLOR_UNREAD);
            context.text(textRenderer, unreadText, unreadX + 2, getY() + 3, COLOR_TEXT, false);
        }

        // Close button for closable tabs (DM and kingdom)
        if (slot.closable()) {
            int closeX = slot.closeX();
            boolean closeHovered = isMouseOverArea(mouseX, mouseY, closeX, getY(), CLOSE_BUTTON_WIDTH, TAB_HEIGHT);
            context.text(textRenderer, "x", closeX + 2, getY() + 3, closeHovered ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return false;

        double mouseX = click.x();
        double mouseY = click.y();

        Font textRenderer = Minecraft.getInstance().font;
        TabManager tabManager = TabManager.getInstance();
        for (TabSlot slot : buildTabSlots(tabManager, textRenderer)) {
            if (isMouseOverArea((int) mouseX, (int) mouseY, slot.x(), getY(), slot.width(), TAB_HEIGHT)) {
                if (slot.closable() && mouseX >= slot.closeX() && mouseX < slot.closeX() + CLOSE_BUTTON_WIDTH) {
                    if (slot.tab().isDmTab()) {
                        tabManager.closeDmTab(slot.tab().getDmPlayerName());
                    } else {
                        tabManager.closeKingdomTab();
                    }
                } else {
                    tabManager.setActiveTab(slot.tab());
                }
                // Don't return true - let the click "fall through" so focus stays on chat
                return false;
            }
        }

        return false;
    }

    private List<TabSlot> buildTabSlots(TabManager tabManager, Font textRenderer) {
        List<TabSlot> slots = new ArrayList<>();
        int currentX = getX();

        for (ChatTab tab : tabManager.getFixedTabs()) {
            int tabWidth = textRenderer.width(tab.getDisplayName()) + TAB_PADDING * 2;
            slots.add(new TabSlot(tab, currentX, tabWidth, false, -1));
            currentX += tabWidth + TAB_MARGIN;
        }

        if (tabManager.isKingdomTabVisible()) {
            ChatTab kingdomTab = tabManager.getKingdomTab();
            int textWidth = textRenderer.width(kingdomTab.getDisplayName());
            int tabWidth = textWidth + TAB_PADDING * 2 + CLOSE_BUTTON_WIDTH;
            int closeX = currentX + tabWidth - CLOSE_BUTTON_WIDTH;
            slots.add(new TabSlot(kingdomTab, currentX, tabWidth, true, closeX));
            currentX += tabWidth + TAB_MARGIN;
        }

        for (ChatTab tab : tabManager.getDmTabs()) {
            int textWidth = textRenderer.width(tab.getDisplayName());
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
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        // Accessibility narration for screen readers
    }

    private record TabSlot(ChatTab tab, int x, int width, boolean closable, int closeX) {
    }
}
