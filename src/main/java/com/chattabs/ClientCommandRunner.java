package com.chattabs;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Runs commands on Fabric's client command dispatcher only.
 * ClientPacketListener.sendCommand would fall through to the server when the root is unknown
 * (Fabric's mixin only cancels commands the client dispatcher can parse), so it is not used here.
 */
public final class ClientCommandRunner {

    private ClientCommandRunner() {
    }

    public static boolean hasRoot(String root) {
        return ClientCommands.getActiveDispatcher().getRoot().getChild(root) != null;
    }

    /** Executes "command" (no slash) on the client dispatcher; errors are shown locally. */
    public static void run(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            return;
        }
        FabricClientCommandSource source = (FabricClientCommandSource) client.getConnection().getSuggestionsProvider();
        try {
            ClientCommands.getActiveDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            source.sendError(Component.literal(e.getMessage()));
        }
    }
}
