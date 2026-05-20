package com.chattabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

public class ChatFocusHelper {
    private static EditBox currentChatField;
    private static boolean pendingRefocus = false;

    public static void setChatField(EditBox chatField) {
        currentChatField = chatField;
    }

    public static void clearChatField() {
        currentChatField = null;
        pendingRefocus = false;
    }

    public static void refocus() {
        if (currentChatField == null) return;

        // Immediate refocus
        focusAndUnlockField();

        // Also schedule for next tick in case something else steals focus
        pendingRefocus = true;
        Minecraft.getInstance().execute(() -> {
            if (pendingRefocus && currentChatField != null) {
                focusAndUnlockField();
                pendingRefocus = false;
            }
        });
    }

    public static void refocusImmediate() {
        if (currentChatField != null) {
            focusAndUnlockField();
        }
    }

    private static void focusAndUnlockField() {
        currentChatField.setFocused(true);
        currentChatField.setEditable(true);
    }
}
