package dev.moar.command;

import dev.moar.MoarMod;
import dev.moar.util.ChatHelper;
import dev.moar.util.PacketTelemetry;
/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*//*?} else {*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
/*?}*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

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
                    )
                    /*? if >=26.1 {*//*
                    .then(ClientCommands.literal("packetlog")
                            .then(ClientCommands.literal("on").executes(ctx -> packetLogOn()))
                            .then(ClientCommands.literal("travel").executes(ctx -> packetLogTravel()))
                            .then(ClientCommands.literal("off").executes(ctx -> packetLogOff()))
                            .then(ClientCommands.literal("status").executes(ctx -> packetLogStatus()))
                            .then(ClientCommands.literal("clear").executes(ctx -> packetLogClear()))
                            .then(ClientCommands.literal("mark").executes(ctx -> packetLogMark()))
                            .then(ClientCommands.literal("dump").executes(ctx -> packetLogDump()))
                    );
                    *//*?} else {*/
                    .then(ClientCommandManager.literal("packetlog")
                            .then(ClientCommandManager.literal("on").executes(ctx -> packetLogOn()))
                            .then(ClientCommandManager.literal("travel").executes(ctx -> packetLogTravel()))
                            .then(ClientCommandManager.literal("off").executes(ctx -> packetLogOff()))
                            .then(ClientCommandManager.literal("status").executes(ctx -> packetLogStatus()))
                            .then(ClientCommandManager.literal("clear").executes(ctx -> packetLogClear()))
                            .then(ClientCommandManager.literal("mark").executes(ctx -> packetLogMark()))
                            .then(ClientCommandManager.literal("dump").executes(ctx -> packetLogDump()))
                    );
                    /*?}*/

            dispatcher.register(root);
            LOGGER.info("MoarCommand: /moar registered");
        });
    }

    private static int openGui() {
        MoarMod.requestGuiOpen();
        return 1;
    }

    private static int packetLogOn() {
        if (PacketTelemetry.isEnabled()
                && PacketTelemetry.mode() == PacketTelemetry.Mode.TRAVEL) {
            ChatHelper.info("§eTravel packet telemetry is already active; §f/moar packetlog on§e did not clear it. Use §f/moar packetlog off§e first to switch modes.");
            return 1;
        }
        PacketTelemetry.clear();
        PacketTelemetry.setEnabled(true);
        ChatHelper.info("§aGeneral packet telemetry enabled. Use §f/moar packetlog travel§a for elytra diagnostics.");
        return 1;
    }

    private static int packetLogTravel() {
        PacketTelemetry.startTravel();
        ChatHelper.info("§aTravel packet telemetry enabled. Use §f/moar packetlog dump§a after a few bounce cycles.");
        return 1;
    }

    private static int packetLogOff() {
        PacketTelemetry.setEnabled(false);
        ChatHelper.info("§ePacket telemetry disabled. Use §f/moar packetlog dump§e to write the trace.");
        return 1;
    }

    private static int packetLogStatus() {
        ChatHelper.info((PacketTelemetry.isEnabled() ? "§aenabled" : "§edisabled")
                + " §7mode=" + PacketTelemetry.mode()
                + " events=" + PacketTelemetry.size());
        return 1;
    }

    private static int packetLogClear() {
        PacketTelemetry.clear();
        ChatHelper.info("§7Packet telemetry cleared.");
        return 1;
    }

    private static int packetLogMark() {
        PacketTelemetry.mark("manual");
        ChatHelper.info("§7Packet telemetry marker added.");
        return 1;
    }

    private static int packetLogDump() {
        try {
            Path path = PacketTelemetry.dumpToFile();
            ChatHelper.info("§aPacket telemetry written to §f" + path);
        } catch (IOException e) {
            LOGGER.warn("Failed to write packet telemetry", e);
            ChatHelper.info("§cFailed to write packet telemetry: " + e.getMessage());
        }
        return 1;
    }
}
