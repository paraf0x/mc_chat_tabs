package com.chattabs.mixin;

import com.chattabs.ChatHudAccessor;
import com.chattabs.tab.TabManager;
import net.minecraft.client.MinecraftClient;
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

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        TabManager tabManager = TabManager.getInstance();

        // Create a ChatHudLine with current tick count for proper fading
        ChatHudLine hudLine = new ChatHudLine(
            getCurrentTicks(),
            message,
            signatureData,
            indicator
        );

        // Route message to tabs
        tabManager.onMessageReceived(message, hudLine);

        // Only show in ChatHud if current tab should display this message
        if (!tabManager.shouldDisplayMessage(message)) {
            ci.cancel();
            return;
        }

        // For All tab, let vanilla handle it
        // For other tabs, we still need the message to be added,
        // but only if it passes the filter (which we checked above)
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
        // Clear existing messages
        this.messages.clear();
        this.visibleMessages.clear();

        // Add messages from the tab (they're stored newest-first, so reverse to add oldest first)
        // Messages keep their original tick count so fading works correctly
        for (int i = tabMessages.size() - 1; i >= 0; i--) {
            ChatHudLine line = tabMessages.get(i);
            this.messages.add(0, line);
            this.addVisibleMessage(line);
        }
    }
}
