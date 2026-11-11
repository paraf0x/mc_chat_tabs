package com.chattabs.tab;

import java.util.function.Predicate;

/**
 * Decides what happens to a line typed in a channel that has a send command (e.g. "hoard c").
 * The line becomes the client command "hoard c <line>" and never reaches the server as chat.
 * A send command whose root is not a registered client command blocks the line.
 */
public final class SendCommandRouter {

    public enum Action { PASS_THROUGH, RUN_CLIENT_COMMAND, BLOCKED }

    public record Result(Action action, String command, String notice) {
    }

    private static final Result PASS = new Result(Action.PASS_THROUGH, null, null);

    private SendCommandRouter() {
    }

    /**
     * @param sendCommand   the channel's send command, with or without slash; blank means none
     * @param line          the typed chat line
     * @param rootAvailable true when the given command root is a registered client command
     */
    public static Result route(String sendCommand, String line, Predicate<String> rootAvailable) {
        String command = sendCommand == null ? "" : sendCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        String text = line == null ? "" : line.trim();
        if (command.isEmpty() || text.startsWith("/")) {
            return PASS;
        }

        String root = command.split("\\s+", 2)[0];
        if (text.isEmpty() || !rootAvailable.test(root)) {
            String reason = text.isEmpty() ? "line is empty" : root + " is not available";
            return new Result(Action.BLOCKED, null, "Chat Tabs: " + reason + ", line not sent");
        }
        return new Result(Action.RUN_CLIENT_COMMAND, command + " " + text, null);
    }
}
