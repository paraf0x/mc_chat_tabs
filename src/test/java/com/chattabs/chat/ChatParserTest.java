package com.chattabs.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatParserTest {

    @Test
    void kingdomLineParsesAsKingdomChannel() {
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > test");
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(ChatChannel.KINGDOM, parsed.getChannel());
        assertEquals("[CrimsonHollow] [Lord of the Hollow] UnicornsBane", parsed.getSenderName());
        assertEquals("test", parsed.getContent());
    }

    @Test
    void globalLineStillParsesAsGlobal() {
        Component line = Component.literal("[G] [Deepstone] [Member] labsami > crimson planks! :D");
        assertEquals(ChatChannel.GLOBAL, ChatParser.parse(line).getChannel());
    }

    @Test
    void channelSwitchFeedbackStaysSystem() {
        Component line = Component.literal("[ỉ] Switched mode for channel kingdom from read to write.");
        assertEquals(ChatChannel.SYSTEM, ChatParser.parse(line).getChannel());
    }

    @Test
    void unknownBracketTagStaysSystem() {
        Component line = Component.literal("[X] Somebody > hello");
        assertEquals(ChatChannel.SYSTEM, ChatParser.parse(line).getChannel());
    }
}
