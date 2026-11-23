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
    private static final int HORIZONTAL_GOAL_REFERENCE_Y = 64;

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
                    .then(ClientCommands.literal("bounce").executes(ctx -> doBounceToggle()))
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
                                    .executes(ctx -> doRepairElytras()))))
                    .then(ClientCommands.literal("stop").executes(ctx -> doStop()))
                    .then(ClientCommands.literal("bounce")
                            .executes(ctx -> doBounceToggle())
                            .then(ClientCommands.literal("on").executes(ctx -> doBounceSet(true)))
                            .then(ClientCommands.literal("off").executes(ctx -> doBounceSet(false)))
                            .then(ClientCommands.literal("status").executes(ctx -> doBounceStatus())));
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
                    .then(ClientCommandManager.literal("bounce").executes(ctx -> doBounceToggle()))
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
                                    .executes(ctx -> doRepairElytras()))))
                    .then(ClientCommandManager.literal("stop").executes(ctx -> doStop()))
                    .then(ClientCommandManager.literal("bounce")
                            .executes(ctx -> doBounceToggle())
                            .then(ClientCommandManager.literal("on").executes(ctx -> doBounceSet(true)))
                            .then(ClientCommandManager.literal("off").executes(ctx -> doBounceSet(false)))
                            .then(ClientCommandManager.literal("status").executes(ctx -> doBounceStatus())));
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
            chat("\u00a7c[Travel] repair rejected — travel or Elytra Bounce is active.");
        }
        return started ? 1 : 0;
    }

    private static int doGoto(int x, int z) {
        return doGoto(x, z, false);
    }

    /** Starts the same Nether-coordinate mission from an optional map integration. */
    public static boolean startNetherFromMap(int x, int z) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != net.minecraft.world.level.Level.NETHER) {
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !net.minecraft.world.World.NETHER.equals(mc.world.getRegistryKey())) {
        /*?}*/
            chat("§c[Travel] open the map while in the Nether to start a map route");
            return false;
        }
        return doGoto(x, z) == 1;
    }

    private static int doBounceToggle() {
        return reportBounceResult(TravelManager.get().toggleStandaloneBounce());
    }

    private static int doBounceSet(boolean enabled) {
        return reportBounceResult(TravelManager.get().setStandaloneBounceEnabled(enabled));
    }

    private static int doBounceStatus() {
        chat(TravelManager.get().isStandaloneBounceActive()
                ? "§a[Bounce] enabled — open-ended highway bounce"
                : "§e[Bounce] disabled");
        return 1;
    }

    private static int reportBounceResult(TravelManager.BounceToggleResult result) {
        chat((result.accepted() ? result.enabled() ? "§a" : "§e" : "§c")
                + "[Bounce] " + result.message());
        return result.accepted() ? 1 : 0;
    }

    private static int doGoto(int x, int z, boolean overworldCoords) {
        BlockPos origin = playerPos();
        if (origin == null) {
            chat("§c[Travel] no player");
            return 0;
        }
        int nx = overworldCoords ? Math.floorDiv(x, 8) : x;
        int nz = overworldCoords ? Math.floorDiv(z, 8) : z;
        BlockPos dest = new BlockPos(nx, HORIZONTAL_GOAL_REFERENCE_Y, nz);
        TravelMission m = TravelMission.to(dest)
                .horizontalDestination(true)
                .build();
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
        chat("§e[Travel] stopped");
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
