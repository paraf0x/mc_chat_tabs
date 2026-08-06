package com.chattabs.compat;

import com.chattabs.ui.settings.ChatTabsSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Only loaded when ModMenu is installed. */
public class ChatTabsModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ChatTabsSettingsScreen::new;
    }
}
