package dev.moar.travel.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import dev.moar.travel.TravelManager;
import dev.moar.travel.TravelMission;
import dev.moar.travel.telemetry.TravelLog;
import dev.moar.travel.telemetry.TravelTelemetry;

/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*//*?} else {*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
/*?}*/
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.chat.Component;
*//*?} else {*/
import net.minecraft.text.Text;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the /moar travel client commands. */
public final class TravelCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Travel");

    private TravelCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            /*? if >=26.1 {*//*
            var root = ClientCommands.literal("moar").then(ClientCommands.literal("travel")
                    .then(ClientCommands.literal("goto")
                            .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                    .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> doGoto(
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "z"))))))
                    .then(ClientCommands.literal("stop").executes(ctx -> doStop()))
                    .then(ClientCommands.literal("pause").executes(ctx -> doPause()))
                    .then(ClientCommands.literal("resume").executes(ctx -> doResume()))
                    .then(ClientCommands.literal("status").executes(ctx -> doStatus()))
                    .then(ClientCommands.literal("log").executes(ctx -> doLog())));
            *//*?} else {*/
            var root = ClientCommandManager.literal("moar").then(ClientCommandManager.literal("travel")
                    .then(ClientCommandManager.literal("goto")
                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> doGoto(
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "z"))))))
                    .then(ClientCommandManager.literal("stop").executes(ctx -> doStop()))
                    .then(ClientCommandManager.literal("pause").executes(ctx -> doPause()))
                    .then(ClientCommandManager.literal("resume").executes(ctx -> doResume()))
                    .then(ClientCommandManager.literal("status").executes(ctx -> doStatus()))
                    .then(ClientCommandManager.literal("log").executes(ctx -> doLog())));
            /*?}*/
            dispatcher.register(root);
            LOGGER.info("TravelCommand: /moar travel registered");
        });
    }

    private static int doGoto(int x, int z) {
        BlockPos origin = playerPos();
        if (origin == null) {
            chat("§c[Travel] no player");
            return 0;
        }
        BlockPos dest = new BlockPos(x, origin.getY(), z);
        TravelMission m = TravelMission.to(dest).build();
        boolean ok = TravelManager.get().start(m);
        chat(ok ? "§a[Travel] started → " + dest.toShortString()
                : "§c[Travel] rejected — already running");
        return ok ? 1 : 0;
    }

    private static int doStop() {
        TravelManager.get().stop();
        chat("§e[Travel] stop requested");
        return 1;
    }

    private static int doPause() {
        TravelManager.get().pause();
        chat("§e[Travel] pause requested");
        return 1;
    }

    private static int doResume() {
        TravelManager.get().resume();
        chat("§e[Travel] resume requested");
        return 1;
    }

    private static int doStatus() {
        TravelTelemetry t = TravelManager.get().snapshot();
        chat("§b[Travel] phase=" + t.phase() + " owner=" + t.owner()
                + " tickInPhase=" + t.ticksInPhase()
                + " missionTicks=" + t.missionTicks());
        if (t.destination() != null) {
            chat("§b[Travel] dest=" + t.destination().toShortString()
                    + " curTgt=" + (t.currentTarget() == null ? "—" : t.currentTarget().toShortString()));
        }
        if (t.selectedHighway() != null) {
            chat("§b[Travel] " + t.selectedHighway());
        }
        if (!t.lastTransitionReason().isEmpty()) {
            chat("§7[Travel] last: " + t.lastTransitionReason());
        }
        if (!t.abortReason().isEmpty()) {
            chat("§c[Travel] abortReason: " + t.abortReason());
        }
        return 1;
    }

    private static int doLog() {
        var entries = TravelLog.get().snapshot();
        if (entries.isEmpty()) {
            chat("§7[Travel] log empty");
            return 1;
        }
        int from = Math.max(0, entries.size() - 10);
        for (int i = from; i < entries.size(); i++) {
            var e = entries.get(i);
            chat("§7[" + e.tick() + "] " + e.kind() + " "
                    + e.from() + "→" + e.to() + " :: " + e.detail());
        }
        return 1;
    }

    // ─── helpers ──────────────────────────────────────────────────

    private static BlockPos playerPos() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.blockPosition();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        return mc.player.getBlockPos();
        /*?}*/
    }

    private static void chat(String msg) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(msg));
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal(msg), false);
        /*?}*/
    }
}
