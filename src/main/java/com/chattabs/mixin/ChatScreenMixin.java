package com.chattabs.mixin;

import com.chattabs.ChatFocusHelper;
import com.chattabs.tab.TabManager;
import com.chattabs.ui.TabBar;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private TabBar tabBar;

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
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        ChatFocusHelper.clearChatField();
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void onMouseClicked(CallbackInfoReturnable<Boolean> cir) {
        // Always keep focus on chat field after any click (especially tab clicks)
        if (chatField != null) {
            this.setFocused(chatField);
            chatField.setFocused(true);
        }
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
