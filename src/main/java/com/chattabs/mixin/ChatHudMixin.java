package com.chattabs.mixin;

import com.chattabs.ChatHudAccessor;
import com.chattabs.ChatTabsConfig;
import com.chattabs.tab.TabManager;
import com.chattabs.ui.ChatLayoutMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements ChatHudAccessor {

    @Shadow @Final private List<GuiMessage> allMessages;

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Shadow protected abstract void addMessageToDisplayQueue(GuiMessage message);

    @Shadow protected abstract void addMessageToQueue(GuiMessage message);

    @Unique
    private boolean chattabs$offsetApplied = false;

    // MC 26.1.x renders chat via the "extract render state" pipeline; the chat
    // X/Y offset is applied by translating the GuiGraphicsExtractor pose stack.
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"))
    private void onRenderHead(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                              ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        ChatTabsConfig config = ChatTabsConfig.getInstance();
        int offsetX = ChatLayoutMath.getChatRenderOffsetX(config);
        int offsetY = ChatLayoutMath.getChatRenderOffsetY(config);
        chattabs$offsetApplied = (offsetX != 0 || offsetY != 0);
        if (chattabs$offsetApplied) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(offsetX, offsetY);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN"))
    private void onRenderReturn(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                                ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        if (chattabs$offsetApplied) {
            graphics.pose().popMatrix();
            chattabs$offsetApplied = false;
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source,
                              GuiMessageTag tag, CallbackInfo ci) {
        TabManager tabManager = TabManager.getInstance();

        GuiMessage hudLine = new GuiMessage(
            Minecraft.getInstance().gui.getGuiTicks(),
            message,
            signature,
            source,
            tag
        );

        tabManager.onMessageReceived(message, hudLine);

        if (!tabManager.shouldDisplayMessage(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void onClear(boolean clearHistory, CallbackInfo ci) {
        if (clearHistory) {
            TabManager.getInstance().clearAllMessages();
        }
    }

    @Unique
    @Override
    public void chattabs$refreshWithMessages(List<GuiMessage> tabMessages) {
        this.allMessages.clear();
        this.trimmedMessages.clear();

        for (int i = tabMessages.size() - 1; i >= 0; i--) {
            GuiMessage line = tabMessages.get(i);
            this.addMessageToQueue(line);
            this.addMessageToDisplayQueue(line);
        }
    }
}
