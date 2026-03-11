package com.chattabs;

import net.minecraft.client.gui.hud.ChatHudLine;

import java.util.List;

public interface ChatHudAccessor {
    void chattabs$refreshWithMessages(List<ChatHudLine> messages);
}
