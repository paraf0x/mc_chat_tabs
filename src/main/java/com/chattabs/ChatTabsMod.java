package com.chattabs;

import com.chattabs.tab.TabManager;
import com.chattabs.ui.GlobalPeekOverlay;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
                                        })))));
    }
}
