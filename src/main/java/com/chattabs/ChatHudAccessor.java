package com.chattabs;

import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.List;

public interface ChatHudAccessor {
    void chattabs$refreshWithMessages(List<GuiMessage> messages);
}
