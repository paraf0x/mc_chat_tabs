package com.chattabs.tab;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SendCommandRouterTest {

    private static final Predicate<String> HOARD_ONLY = Set.of("hoard")::contains;

    @Test
    void typedLineBecomesCommandWithSendCommandPrefix() {
        SendCommandRouter.Result result = SendCommandRouter.route("hoard c", "hello crew", HOARD_ONLY);
        assertEquals(SendCommandRouter.Action.RUN_CLIENT_COMMAND, result.action());
        assertEquals("hoard c hello crew", result.command());
    }

    @Test
    void leadingSlashInSendCommandAndExtraSpacesAreNormalised() {
        SendCommandRouter.Result result = SendCommandRouter.route(" /hoard c ", "  hi  ", HOARD_ONLY);
        assertEquals("hoard c hi", result.command());
    }

    @Test
    void unknownRootFailsClosedWithNotice() {
        SendCommandRouter.Result result = SendCommandRouter.route("hoard c", "hello", root -> false);
        assertEquals(SendCommandRouter.Action.BLOCKED, result.action());
        assertNull(result.command());
        assertEquals("Chat Tabs: hoard is not available, line not sent", result.notice());
    }

    @Test
    void emptySendCommandPassesThrough() {
        assertEquals(SendCommandRouter.Action.PASS_THROUGH,
                SendCommandRouter.route("", "hello", HOARD_ONLY).action());
        assertEquals(SendCommandRouter.Action.PASS_THROUGH,
                SendCommandRouter.route(null, "hello", HOARD_ONLY).action());
        assertEquals(SendCommandRouter.Action.PASS_THROUGH,
                SendCommandRouter.route("   ", "hello", HOARD_ONLY).action());
    }

    @Test
    void lineStartingWithSlashPassesThrough() {
        assertEquals(SendCommandRouter.Action.PASS_THROUGH,
                SendCommandRouter.route("hoard c", "/home", HOARD_ONLY).action());
    }

    @Test
    void blankLineIsNeverSent() {
        assertEquals(SendCommandRouter.Action.BLOCKED,
                SendCommandRouter.route("hoard c", "   ", HOARD_ONLY).action());
    }

    @Test
    void rootCheckIsNotCalledWhenPassingThrough() {
        SendCommandRouter.route("hoard c", "/x", root -> {
            fail("no lookup expected");
            return true;
        });
    }
}
