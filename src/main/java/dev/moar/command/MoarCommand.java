package dev.moar.command;

import dev.moar.MoarMod;
/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*//*?} else {*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
/*?}*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoarCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    private MoarCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            /*? if >=26.1 {*//*
            var root = ClientCommands.literal("moar")
            *//*?} else {*/
            var root = ClientCommandManager.literal("moar")
            /*?}*/
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.literal("gui")
                    *//*?} else {*/
                    .then(ClientCommandManager.literal("gui")
                    /*?}*/
                            .executes(ctx -> openGui())
                    );

            dispatcher.register(root);
            LOGGER.info("MoarCommand: /moar registered");
        });
    }

    private static int openGui() {
        MoarMod.requestGuiOpen();
        return 1;
    }
}
