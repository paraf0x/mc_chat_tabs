package com.chattabs;

import com.chattabs.tab.TabManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatTabsMod implements ClientModInitializer {
    public static final String MOD_ID = "chattabs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Chat Tabs Mod initializing...");

        // Initialize TabManager
        TabManager.getInstance();

        LOGGER.info("Chat Tabs Mod initialized!");
    }
}
