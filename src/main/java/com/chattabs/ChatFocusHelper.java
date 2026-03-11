package com.chattabs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;

public class ChatFocusHelper {
    private static TextFieldWidget currentChatField;
    private static boolean pendingRefocus = false;

    public static void setChatField(TextFieldWidget chatField) {
        currentChatField = chatField;
    }

    public static void clearChatField() {
        currentChatField = null;
        pendingRefocus = false;
    }

    public static void refocus() {
        if (currentChatField == null) return;

        // Immediate refocus
        currentChatField.setFocused(true);
        currentChatField.setEditable(true);

        // Also schedule for next tick in case something else steals focus
        pendingRefocus = true;
        MinecraftClient.getInstance().execute(() -> {
            if (pendingRefocus && currentChatField != null) {
                currentChatField.setFocused(true);
                currentChatField.setEditable(true);
                pendingRefocus = false;
            }
        });
    }

    public static void refocusImmediate() {
        if (currentChatField != null) {
            currentChatField.setFocused(true);
            currentChatField.setEditable(true);
        }
    }
}
