package com.chattabs;

import com.chattabs.config.ServerProfile;
import com.chattabs.tab.TabManager;
import com.chattabs.ui.GlobalPeekOverlay;
import com.chattabs.ui.PeekPositionScreen;
import com.chattabs.ui.settings.ChatTabsSettingsScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                TabManager.getInstance().applyProfile(
                        ChatTabsConfig.getInstance().profileForAddress(currentServerAddress(client))));
        registerClientCommands();

        LOGGER.info("Chat Tabs Mod initialized!");
    }

    private static String currentServerAddress(Minecraft client) {
        net.minecraft.client.multiplayer.ServerData server = client.getCurrentServer();
        return server == null ? null : server.ip;
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("chattabs")
                        .then(ClientCommands.literal("idle")
                                .executes(context -> {
                                    ChatTabsConfig config = ChatTabsConfig.getInstance();
                                    context.getSource().sendFeedback(Component.literal("ChatTabs idle auto-switch: " + config.getIdleSwitchLabel()));
                                    return 1;
                                })
                                .then(ClientCommands.literal("never")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setIdleSwitchSeconds(-1);
                                            config.save();
                                            context.getSource().sendFeedback(Component.literal("ChatTabs idle auto-switch disabled."));
                                            return 1;
                                        }))
                                .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setIdleSwitchSeconds(seconds);
                                            config.save();
                                            context.getSource().sendFeedback(Component.literal("ChatTabs idle auto-switch set to " + seconds + "s."));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("mode")
                                .executes(context -> {
                                    ChatTabsConfig config = ChatTabsConfig.getInstance();
                                    context.getSource().sendFeedback(Component.literal(
                                            "ChatTabs mode: " + config.getChatMode().getCommandName()));
                                    return 1;
                                })
                                .then(ClientCommands.literal("filtered")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setChatMode(ChatTabsConfig.ChatMode.FILTERED);
                                            config.save();
                                            TabManager.getInstance().refreshForModeChange();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "ChatTabs mode set to filtered tabs."));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("single")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setChatMode(ChatTabsConfig.ChatMode.SINGLE_WINDOW);
                                            config.save();
                                            TabManager.getInstance().refreshForModeChange();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "ChatTabs mode set to single-window tabs."));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("single-window")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setChatMode(ChatTabsConfig.ChatMode.SINGLE_WINDOW);
                                            config.save();
                                            TabManager.getInstance().refreshForModeChange();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "ChatTabs mode set to single-window tabs."));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("peek")
                                .executes(context -> {
                                    ChatTabsConfig config = ChatTabsConfig.getInstance();
                                    String status = config.isGlobalPeekEnabled() ? "on" : "off";
                                    context.getSource().sendFeedback(Component.literal("ChatTabs global peek: " + status + " (" + config.getGlobalPeekLines() + " lines)"));
                                    return 1;
                                })
                                .then(ClientCommands.literal("on")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setGlobalPeekEnabled(true);
                                            config.save();
                                            context.getSource().sendFeedback(Component.literal("ChatTabs global peek enabled."));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("off")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.setGlobalPeekEnabled(false);
                                            config.save();
                                            context.getSource().sendFeedback(Component.literal("ChatTabs global peek disabled."));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("lines")
                                        .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 10))
                                                .executes(context -> {
                                                    int count = IntegerArgumentType.getInteger(context, "count");
                                                    ChatTabsConfig config = ChatTabsConfig.getInstance();
                                                    config.setGlobalPeekLines(count);
                                                    config.save();
                                                    context.getSource().sendFeedback(Component.literal("ChatTabs global peek set to " + count + " lines."));
                                                    return 1;
                                                })))
                                .then(ClientCommands.literal("position")
                                        .executes(context -> {
                                            // Schedule screen open for next tick (can't open screen from command thread)
                                            Minecraft.getInstance().execute(() ->
                                                    Minecraft.getInstance().gui.setScreen(new PeekPositionScreen()));
                                            return 1;
                                        }))
                                .then(ClientCommands.literal("reset")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.resetPeekPosition();
                                            config.save();
                                            context.getSource().sendFeedback(Component.literal("ChatTabs peek position reset to auto."));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("settings")
                                .executes(context -> {
                                    Minecraft.getInstance().execute(() ->
                                            Minecraft.getInstance().gui.setScreen(
                                                    new ChatTabsSettingsScreen(null)));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("profile")
                                .executes(context -> {
                                    ServerProfile profile = TabManager.getInstance().getActiveProfile();
                                    context.getSource().sendFeedback(Component.literal(
                                            "ChatTabs profile: " + profile.getName() + " ("
                                                    + profile.validChannels().size() + " channels)"));
                                    return 1;
                                })
                                .then(ClientCommands.literal("reload")
                                        .executes(context -> {
                                            ChatTabsConfig.reload();
                                            Minecraft client = Minecraft.getInstance();
                                            TabManager.getInstance().applyProfile(
                                                    ChatTabsConfig.getInstance().profileForAddress(currentServerAddress(client)));
                                            context.getSource().sendFeedback(Component.literal(
                                                    "ChatTabs config reloaded."));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("layout")
                                .executes(context -> {
                                    Minecraft.getInstance().execute(() ->
                                            Minecraft.getInstance().gui.setScreen(new PeekPositionScreen()));
                                    return 1;
                                })
                                .then(ClientCommands.literal("reset")
                                        .executes(context -> {
                                            ChatTabsConfig config = ChatTabsConfig.getInstance();
                                            config.resetPeekPosition();
                                            config.resetChatOffset();
                                            config.save();
                                            context.getSource().sendFeedback(Component.literal("ChatTabs layout reset to defaults."));
                                            return 1;
                                        })))));
    }
}
