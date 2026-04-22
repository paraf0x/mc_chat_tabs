package com.chattabs.mixin;

import com.chattabs.ChatHudAccessor;
import com.chattabs.ChatTabsConfig;
import com.chattabs.tab.TabManager;
import com.chattabs.ui.ChatLayoutMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements ChatHudAccessor {

    @Shadow @Final private List<ChatHudLine> messages;

    @Shadow @Final private List<ChatHudLine.Visible> visibleMessages;

    @Shadow protected abstract void addVisibleMessage(ChatHudLine message);

    @Shadow public abstract void clear(boolean clearHistory);

    @Unique
    private int getCurrentTicks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            return client.inGameHud.getTicks();
        }
        return 0;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("HEAD"))
    private void onRenderHead(DrawContext context, TextRenderer textRenderer, int tickDelta, int screenWidth, int screenHeight, boolean focused, boolean bl, CallbackInfo ci) {
        ChatTabsConfig config = ChatTabsConfig.getInstance();
        int offsetX = ChatLayoutMath.getChatRenderOffsetX(config);
        int offsetY = ChatLayoutMath.getChatRenderOffsetY(config);
        if (offsetX != 0 || offsetY != 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(offsetX, offsetY);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
            at = @At("RETURN"))
    private void onRenderReturn(DrawContext context, TextRenderer textRenderer, int tickDelta, int screenWidth, int screenHeight, boolean focused, boolean bl, CallbackInfo ci) {
        ChatTabsConfig config = ChatTabsConfig.getInstance();
        int offsetX = ChatLayoutMath.getChatRenderOffsetX(config);
        int offsetY = ChatLayoutMath.getChatRenderOffsetY(config);
        if (offsetX != 0 || offsetY != 0) {
            context.getMatrices().popMatrix();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        TabManager tabManager = TabManager.getInstance();

        ChatHudLine hudLine = new ChatHudLine(
            getCurrentTicks(),
            message,
            signatureData,
            indicator
        );

        tabManager.onMessageReceived(message, hudLine);

        if (!tabManager.shouldDisplayMessage(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void onClear(boolean clearHistory, CallbackInfo ci) {
        if (clearHistory) {
            TabManager.getInstance().clearAllMessages();
        }
    }

    @Unique
    @Override
    public void chattabs$refreshWithMessages(List<ChatHudLine> tabMessages) {
        this.messages.clear();
        this.visibleMessages.clear();

        for (int i = tabMessages.size() - 1; i >= 0; i--) {
            ChatHudLine line = tabMessages.get(i);
            this.messages.add(0, line);
            this.addVisibleMessage(line);
        }
    }
}
