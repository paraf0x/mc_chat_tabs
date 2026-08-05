package com.chattabs.chat;

import com.chattabs.config.ChannelDefinition;
import com.chattabs.config.ServerProfile;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatParserTest {

    @BeforeEach
    void useDefaultProfile() {
        ChatParser.setActiveProfile(ServerProfile.builtInDefault());
    }

    @Test
    void kingdomLineParsesWithChannelId() {
        Component line = Component.literal("[K] [CrimsonHollow] [Lord of the Hollow] UnicornsBane > test");
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(MessageKind.CHANNEL, parsed.getKind());
        assertEquals("kingdom", parsed.getChannelId());
        assertEquals("[CrimsonHollow] [Lord of the Hollow] UnicornsBane", parsed.getSenderName());
        assertEquals("test", parsed.getContent());
    }

    @Test
    void globalLineParsesWithChannelId() {
        Component line = Component.literal("[G] [Deepstone] [Member] labsami > crimson planks! :D");
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(MessageKind.CHANNEL, parsed.getKind());
        assertEquals("global", parsed.getChannelId());
    }

    @Test
    void unknownTagAndFeedbackLinesStaySystem() {
        assertEquals(MessageKind.SYSTEM,
                ChatParser.parse(Component.literal("[X] Somebody > hello")).getKind());
        assertEquals(MessageKind.SYSTEM,
                ChatParser.parse(Component.literal("[ỉ] Switched mode for channel kingdom from read to write.")).getKind());
    }

    @Test
    void customProfileChannelIsRecognized() {
        ChatParser.setActiveProfile(new ServerProfile("custom", "", "»", List.of(
                new ChannelDefinition("trade", "Trade", "«T»", "", true, false))));
        ChatMessage parsed = ChatParser.parse(Component.literal("«T» Trader » selling dirt"));
        assertEquals(MessageKind.CHANNEL, parsed.getKind());
        assertEquals("trade", parsed.getChannelId());
        assertEquals("Trader", parsed.getSenderName());
        assertEquals("selling dirt", parsed.getContent());
    }

    @Test
    void patternQuotesRegexMetacharacters() {
        assertTrue(ChatParser.buildChannelPattern("[G]", ">")
                .matcher("[G] Player > hi").find());
        assertFalse(ChatParser.buildChannelPattern("[G]", ">")
                .matcher("xGx Player > hi").find());
    }

    @Test
    void dmParsingStillWorks() {
        ChatMessage parsed = ChatParser.parse(Component.literal("✉ you → SomePlayer > hello there"));
        assertTrue(parsed.isDM());
        assertEquals(MessageKind.DM, parsed.getKind());
        assertEquals("SomePlayer", parsed.getSenderName());
    }
}
