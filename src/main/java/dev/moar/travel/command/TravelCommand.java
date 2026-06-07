package dev.moar.travel.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import dev.moar.MoarMod;
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

// Register /moar travel commands.
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
                                                    IntegerArgumentType.getInteger(ctx, "z")))
                                            .then(ClientCommands.literal("overworld")
                                                    .executes(ctx -> doGoto(
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "z"),
                                                            true)))
                                            .then(ClientCommands.literal("nether")
                                                    .executes(ctx -> doGoto(
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "z"),
                                                            false))))))
                    .then(ClientCommands.literal("bounce").executes(ctx -> doBounce()))
                    .then(ClientCommands.literal("stop").executes(ctx -> doStop()))
                    .then(ClientCommands.literal("pause").executes(ctx -> doPause()))
                    .then(ClientCommands.literal("resume").executes(ctx -> doResume()))
                    .then(ClientCommands.literal("status").executes(ctx -> doStatus()))
                    .then(ClientCommands.literal("log").executes(ctx -> doLog()))
                    .then(ClientCommands.literal("enderchest")
                            .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                    .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                            .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                    .executes(ctx -> doEnderChest(
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "y"),
                                                            IntegerArgumentType.getInteger(ctx, "z")))))))
                    .then(ClientCommands.literal("elytra")
                            .then(ClientCommands.literal("resupply-count")
                                    .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 27))
                                            .executes(ctx -> doElytraResupplyCount(
                                                    IntegerArgumentType.getInteger(ctx, "count")))))
                            .then(ClientCommands.literal("repair")
                                    .executes(ctx -> doRepairElytras()))));
            *//*?} else {*/
            var root = ClientCommandManager.literal("moar").then(ClientCommandManager.literal("travel")
                    .then(ClientCommandManager.literal("goto")
                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> doGoto(
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "z")))
                                            .then(ClientCommandManager.literal("overworld")
                                                    .executes(ctx -> doGoto(
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "z"),
                                                            true)))
                                            .then(ClientCommandManager.literal("nether")
                                                    .executes(ctx -> doGoto(
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "z"),
                                                            false))))))
                    .then(ClientCommandManager.literal("bounce").executes(ctx -> doBounce()))
                    .then(ClientCommandManager.literal("stop").executes(ctx -> doStop()))
                    .then(ClientCommandManager.literal("pause").executes(ctx -> doPause()))
                    .then(ClientCommandManager.literal("resume").executes(ctx -> doResume()))
                    .then(ClientCommandManager.literal("status").executes(ctx -> doStatus()))
                    .then(ClientCommandManager.literal("log").executes(ctx -> doLog()))
                    .then(ClientCommandManager.literal("enderchest")
                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                    .executes(ctx -> doEnderChest(
                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                            IntegerArgumentType.getInteger(ctx, "y"),
                                                            IntegerArgumentType.getInteger(ctx, "z")))))))
                    .then(ClientCommandManager.literal("elytra")
                            .then(ClientCommandManager.literal("resupply-count")
                                    .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 27))
                                            .executes(ctx -> doElytraResupplyCount(
                                                    IntegerArgumentType.getInteger(ctx, "count")))))
                            .then(ClientCommandManager.literal("repair")
                                    .executes(ctx -> doRepairElytras()))));
            /*?}*/
            dispatcher.register(root);
            LOGGER.info("TravelCommand: /moar travel registered");
        });
    }

    private static int doElytraResupplyCount(int count) {
        MoarMod.getProperties().setElytraResupplyCount(count);
        chat("\u00a7a[Travel] elytra resupply count set to \u00a7f" + count
                + "\u00a7a. Will grab \u00a7f" + count + "\u00a7a elytra(s) per shulker trip.");
        return 1;
    }

    private static int doRepairElytras() {
        boolean started = TravelManager.get().startStandaloneRepair();
        if (started) {
            chat("\u00a7a[Travel] elytra repair started — locating XP bottles to Mend the worn elytra.");
        } else {
            chat("\u00a7c[Travel] repair rejected — a travel mission is already in progress.");
        }
        return started ? 1 : 0;
    }

    private static int doGoto(int x, int z) {
        return doGoto(x, z, false);
    }

    // Build a long bounce target from the current yaw.
    private static int doBounce() {
        BlockPos origin = playerPos();
        if (origin == null) { chat("§c[Travel] no player"); return 0; }

        float yaw = playerYaw();
        // Snap to nearest 45° increment then derive integer step direction.
        float snapped  = Math.round(yaw / 45f) * 45f;
        double rad     = Math.toRadians(snapped);
        int dx = (int) Math.round(-Math.sin(rad));
        int dz = (int) Math.round( Math.cos(rad));
        if (dx == 0 && dz == 0) dz = 1;  // degenerate guard — shouldn’t happen

        BlockPos dest = new BlockPos(
                origin.getX() + dx * 500_000,
                origin.getY(),
                origin.getZ() + dz * 500_000);
        TravelMission m = TravelMission.to(dest).build();
        boolean ok = TravelManager.get().start(m);
        if (ok) {
            chat("§a[Travel] bounce started → axis (" + dx + "," + dz
                    + "), dest=" + dest.toShortString());
        } else {
            chat("§c[Travel] rejected — already running");
        }
        return ok ? 1 : 0;
    }

    private static int doGoto(int x, int z, boolean overworldCoords) {
        BlockPos origin = playerPos();
        if (origin == null) {
            chat("§c[Travel] no player");
            return 0;
        }
        int nx = overworldCoords ? x / 8 : x;
        int nz = overworldCoords ? z / 8 : z;
        BlockPos dest = new BlockPos(nx, origin.getY(), nz);
        TravelMission m = TravelMission.to(dest).build();
        boolean ok = TravelManager.get().start(m);
        if (ok) {
            String note = overworldCoords
                    ? " §7(overworld→nether: " + x + "," + z + " → " + nx + "," + nz + ")"
                    : "";
            chat("§a[Travel] started → " + dest.toShortString() + note);
        } else {
            chat("§c[Travel] rejected — already running");
        }
        return ok ? 1 : 0;
    }

    private static int doEnderChest(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TravelManager.get().setEnderChestPos(pos);
        chat("§a[Travel] ender chest registered at " + pos.toShortString()
                + " — elytra resupply will use this chest.");
        return 1;
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
    private static float playerYaw() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getYRot() : 0f;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getYaw() : 0f;
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
