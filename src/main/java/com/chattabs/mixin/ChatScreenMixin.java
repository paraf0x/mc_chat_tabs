package com.chattabs.mixin;

import com.chattabs.ChatFocusHelper;
import com.chattabs.tab.TabManager;
import com.chattabs.ui.TabBar;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private TabBar tabBar;

    @Unique
    private static final List<String> messageHistory = new ArrayList<>();

    @Unique
    private static final int MAX_HISTORY = 50;

    @Unique
    private int historyIndex = -1;

    @Unique
    private String currentInput = "";

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Register chat field for refocus helper
        ChatFocusHelper.setChatField(this.chatField);

        // Create tab bar above chat
        tabBar = new TabBar(2, this.height - 40, this.width - 4);
        this.addDrawableChild(tabBar);

        // Reset history navigation
        historyIndex = -1;
        currentInput = "";
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        ChatFocusHelper.clearChatField();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == GLFW.GLFW_KEY_UP) {
            navigateHistory(1);
            cir.setReturnValue(true);
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            navigateHistory(-1);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // Always keep focus on chat field after any click (especially tab clicks)
        if (chatField != null) {
            // Set as focused element in the screen
            this.setFocused(chatField);
            chatField.setFocused(true);
        }
    }

    @Unique
    private void navigateHistory(int direction) {
        if (messageHistory.isEmpty()) return;

        // Save current input when starting navigation
        if (historyIndex == -1) {
            currentInput = chatField.getText();
        }

        int newIndex = historyIndex + direction;

        // Clamp to valid range
        if (newIndex < -1) {
            newIndex = -1;
        } else if (newIndex >= messageHistory.size()) {
            newIndex = messageHistory.size() - 1;
        }

        historyIndex = newIndex;

        if (historyIndex == -1) {
            // Back to current input
            chatField.setText(currentInput);
        } else {
            // Show history entry (history is newest first)
            chatField.setText(messageHistory.get(historyIndex));
        }

        // Move cursor to end
        chatField.setCursorToEnd(false);
    }

    @Inject(method = "sendMessage", at = @At("HEAD"))
    private void onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        // Add to our history (don't add commands or empty messages)
        if (!message.isEmpty() && !message.startsWith("/")) {
            // Remove duplicate if exists
            messageHistory.remove(message);
            // Add at beginning (newest first)
            messageHistory.add(0, message);
            // Limit history size
            while (messageHistory.size() > MAX_HISTORY) {
                messageHistory.remove(messageHistory.size() - 1);
            }
        }

        // Reset navigation
        historyIndex = -1;
        currentInput = "";
    }

    @ModifyVariable(method = "sendMessage", at = @At("HEAD"), argsOnly = true)
    private String prefixDmMessage(String message) {
        // Don't modify commands or empty messages
        // Empty message + prefix would send "/tell player" which puts server in permanent DM mode
        if (message.startsWith("/") || message.trim().isEmpty()) {
            return message;
        }

        // Prefix with /tell player for DM tabs
        String prefix = TabManager.getInstance().getOutgoingMessagePrefix();
        if (prefix != null) {
            return prefix + message;
        }
        return message;
    }
}
