package com.chattabs.mixin;

import com.chattabs.ChatFocusHelper;
import com.chattabs.tab.TabManager;
import com.chattabs.ui.TabBar;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected EditBox input;

    @Shadow
    public abstract void moveInHistory(int dir);

    @Unique
    private TabBar tabBar;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        ChatFocusHelper.setChatField(this.input);

        tabBar = new TabBar(2, this.height - 40, this.width - 4);
        this.addRenderableWidget(tabBar);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        ChatFocusHelper.clearChatField();
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void onMouseClicked(CallbackInfoReturnable<Boolean> cir) {
        ChatFocusHelper.refocus();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        int key = input.key();

        if (key == InputConstants.KEY_TAB && input.hasControlDown()) {
            TabManager.getInstance().cycleActiveTab(input.hasShiftDown() ? -1 : 1);
            ChatFocusHelper.refocusImmediate();
            cir.setReturnValue(true);
            return;
        }

        if (key == InputConstants.KEY_UP) {
            moveInHistory(-1);
            ChatFocusHelper.refocusImmediate();
            cir.setReturnValue(true);
            return;
        }
        if (key == InputConstants.KEY_DOWN) {
            moveInHistory(1);
            ChatFocusHelper.refocusImmediate();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "moveInHistory", at = @At("TAIL"))
    private void onMoveInHistory(int offset, CallbackInfo ci) {
        ChatFocusHelper.refocusImmediate();
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"))
    private void noteOutgoingCommand(String message, boolean addToRecent, CallbackInfo ci) {
        if (message.startsWith("/")) {
            TabManager.getInstance().noteOutgoingCommand(message);
        }
    }

    // Turn the line into a DM here, after it went into chat history unprefixed.
    @Redirect(method = "handleChatInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"))
    private void sendChatOrDm(ClientPacketListener connection, String message) {
        TabManager tabManager = TabManager.getInstance();
        tabManager.noteOutgoingMessage();

        String dmCommand = tabManager.getOutgoingDmCommand(message);
        if (dmCommand != null) {
            connection.sendCommand(dmCommand);
        } else {
            connection.sendChat(message);
        }
    }
}
