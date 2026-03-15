package com.chattabs;

import com.chattabs.tab.TabManager;
import com.chattabs.ui.GlobalPeekOverlay;
import com.chattabs.ui.PeekPositionScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatTabsMod implements ClientModInitializer {
    public static final String MOD_ID = "chattabs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Chat Tabs Mod initializing...");

        ChatTabsConfig.getInstance();
        TabManager.getInstance();

        ClientTickEvents.END_CLIENT_TICK.register(client -> TabManager.getInstance().checkIdleTimeout());
        GlobalPeekOverlay.register();
        registerClientCommands();

        LOGGER.info("Chat Tabs Mod initialized!");
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("chattabs")
                        .then(ClientCommandManager.literal("idle")
                                .executes(context -> {
                                    ChatTabsConfig config = ChatTabsConfig.getInstance();
                                    context.getSource().sendFeedback(Text.literal("ChatTabs idle auto-switch: " + config.getIdleSwitchLabel()));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("never")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setIdleSwitchSeconds(-1);
                                            config.save();
                                            context.getSource().sendFeedback(Text.literal("ChatTabs idle auto-switch disabled."));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setIdleSwitchSeconds(seconds);
                                            config.save();
                                            context.getSource().sendFeedback(Text.literal("ChatTabs idle auto-switch set to " + seconds + "s."));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("peek")
                                .executes(context -> {
                                    ChatTabsConfig config = ChatTabsConfig.getInstance();
                                    String status = config.isGlobalPeekEnabled() ? "on" : "off";
                                    context.getSource().sendFeedback(Text.literal("ChatTabs global peek: " + status + " (" + config.getGlobalPeekLines() + " lines)"));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("on")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setGlobalPeekEnabled(true);
                                            config.save();
                                            context.getSource().sendFeedback(Text.literal("ChatTabs global peek enabled."));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("off")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setGlobalPeekEnabled(false);
                                            config.save();
                                            context.getSource().sendFeedback(Text.literal("ChatTabs global peek disabled."));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("lines")
                                        .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 10))
                                                .executes(context -> {
                                                    int count = IntegerArgumentType.getInteger(context, "count");
                                                    ChatTabsConfig config = ChatTabsConfig.getInstance();
                                                    config.setGlobalPeekLines(count);
                                                    config.save();
                                                    context.getSource().sendFeedback(Text.literal("ChatTabs global peek set to " + count + " lines."));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("position")
                                        .executes(context -> {
                                            // Schedule screen open for next tick (can't open screen from command thread)
                                            MinecraftClient.getInstance().send(() ->
                                                    MinecraftClient.getInstance().setScreen(new PeekPositionScreen()));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("reset")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.resetPeekPosition();
                                            config.save();
                                            context.getSource().sendFeedback(Text.literal("ChatTabs peek position reset to auto."));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("layout")
                                .executes(context -> {
                                    MinecraftClient.getInstance().send(() ->
                                            MinecraftClient.getInstance().setScreen(new PeekPositionScreen()));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("reset")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.resetPeekPosition();
                                            config.resetChatOffset();
                                            config.save();
                                            context.getSource().sendFeedback(Text.literal("ChatTabs layout reset to defaults."));
                                            return 1;
                                        })))));
    }
}
