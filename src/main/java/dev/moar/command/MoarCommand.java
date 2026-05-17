package dev.moar.command;

import dev.moar.gui.MoarScreen;
/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
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
                    .executes(ctx -> {
                        /*? if >=26.1 {*//*
                        Minecraft.getInstance().setScreen(new MoarScreen());
                        *//*?} else {*/
                        MinecraftClient.getInstance().setScreen(new MoarScreen());
                        /*?}*/
                        return 1;
                    });

            dispatcher.register(root);
            LOGGER.info("MoarCommand: /moar registered");
        });
    }
}
