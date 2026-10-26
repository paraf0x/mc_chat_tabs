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

    @Test
    void vanillaIncomingWhisperIsDmFromSender() {
        Component line = Component.translatable("commands.message.display.incoming",
                Component.literal("Steve"), Component.literal("hi there"));
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(MessageKind.DM, parsed.getKind());
        assertEquals("Steve", parsed.getSenderName());
        assertEquals("hi there", parsed.getContent());
        assertFalse(parsed.isOutgoing());
    }

    @Test
    void vanillaOutgoingWhisperIsDmToRecipient() {
        Component line = Component.translatable("commands.message.display.outgoing",
                Component.literal("Alex"), Component.literal("on my way"));
        ChatMessage parsed = ChatParser.parse(line);
        assertEquals(MessageKind.DM, parsed.getKind());
        assertEquals("Alex", parsed.getSenderName());
        assertEquals("on my way", parsed.getContent());
        assertTrue(parsed.isOutgoing());
    }

    @Test
    void vanillaPublicChatStaysSystem() {
        Component line = Component.translatable("chat.type.text",
                Component.literal("Steve"), Component.literal("hello"));
        assertEquals(MessageKind.SYSTEM, ChatParser.parse(line).getKind());
    }

    @Test
    void mentionUsesOwnNameAndProfileNames() {
        ServerProfile profile = new ServerProfile("p", "", ">", List.of(
                new ChannelDefinition("global", "Global", "[G]", "g", true, false)));
        profile.setMentionNames(List.of("Unicorn", "UB"));
        ChatParser.setActiveProfile(profile);
        ChatParser.setOwnNameForTesting("UnicornBeef");
        try {
            assertTrue(ChatParser.parse(Component.literal("[G] Bob > hi unicornbeef")).isMention());
            assertTrue(ChatParser.parse(Component.literal("[G] Bob > ub, trade?")).isMention());
            assertTrue(ChatParser.parse(Component.literal("[G] Bob > Unicorn come here")).isMention());
            assertFalse(ChatParser.parse(Component.literal("[G] Bob > at the pub")).isMention());
            assertFalse(ChatParser.parse(Component.literal("[G] [Town] [Lord] UnicornBeef > ub here")).isMention(),
                    "own messages never count as mentions");
        } finally {
            ChatParser.setOwnNameForTesting(null);
        }
    }

    @Test
    void mentionAlsoFoundInChatLinesWithoutChannel() {
        ServerProfile profile = new ServerProfile("p", "", ">", List.of(
                new ChannelDefinition("local", "Local", "[L]", "l", true, false)));
        profile.setMentionNames(List.of("UB"));
        ChatParser.setActiveProfile(profile);
        ChatParser.setOwnNameForTesting("UnicornBeef");
        try {
            ChatMessage global = ChatParser.parse(Component.literal("[G] [Town] [Lord] Bob > ub trade?"));
            assertEquals(MessageKind.SYSTEM, global.getKind());
            assertTrue(global.isMention());
            assertTrue(ChatParser.parse(Component.translatable("chat.type.text",
                    Component.literal("Bob"), Component.literal("hey UnicornBeef"))).isMention());
            assertFalse(ChatParser.parse(Component.literal("UnicornBeef joined the game")).isMention());
            assertFalse(ChatParser.parse(Component.literal("[G] UnicornBeef > ub")).isMention());
            assertFalse(ChatParser.parse(Component.translatable("chat.type.text",
                    Component.literal("UnicornBeef"), Component.literal("ub"))).isMention());
        } finally {
            ChatParser.setOwnNameForTesting(null);
        }
    }
}
