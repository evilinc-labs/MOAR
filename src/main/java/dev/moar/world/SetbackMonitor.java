package dev.moar.world;

import dev.moar.util.PacketTelemetry;
import dev.moar.util.MoarNetworkManager;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.entity.Relative;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
/*?}*/

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Detect server corrections and hold automation until movement settles.
public final class SetbackMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Setback");

    // Stable ticks required after the last setback before isCalm() returns true.
    // Travel / elytra / restock keep this long window. Printer placement uses
    // PLACEMENT_RESUME_TICKS instead so walk-and-place isn't frozen for 1.5s
    // after every small rubber-band.
    private static final int CALM_WINDOW_TICKS = 30;
    // Ticks after a real setback before the printer may click again. Long
    // enough for teleport-confirm to leave the packet stream, short enough
    // that walking doesn't stall the placer.
    private static final int PLACEMENT_RESUME_TICKS = 2;
    // Don't freeze every network lane for the full calm window — that is the
    // "printer pauses while I walk" symptom on 2b2t.
    private static final int AUTOMATION_PAUSE_TICKS = 3;

    // Ring buffer length for recentSetbackCount().
    private static final int HISTORY_SIZE = 64;

    // Movement below this per-tick delta counts as stationary.
    private static final double STATIONARY_DELTA_BLOCKS = 0.025;
    // Ignore walk-sync / jitter. Real Grim placement flags on 2b2t were ~0.57+.
    private static final double MIN_CORRECTION_DISTANCE_BLOCKS = 0.40;

    private final AtomicInteger pendingCorrections = new AtomicInteger();
    private final AtomicInteger pendingAcknowledgements = new AtomicInteger();
    private final AtomicLong pendingMaxCorrectionBits = new AtomicLong();

    private boolean primed;
    private double lastX, lastY, lastZ;

    // Ticks elapsed since the last detected setback (capped at CALM_WINDOW_TICKS).
    private int ticksSinceSetback = CALM_WINDOW_TICKS;

    // Consecutive low-movement ticks.
    private int stationaryTicks;

    // Total setbacks observed since join.
    private int totalSetbacks;
    private int totalServerCorrections;
    private int totalCorrectionEpisodes;

    // Tick timestamps of recent setbacks (newest first), capped to HISTORY_SIZE.
    private final long[] setbackTicks = new long[HISTORY_SIZE];
    private final long[] correctionEpisodeTicks = new long[HISTORY_SIZE];
    private int historyHead;
    private int correctionEpisodeHead;
    private long currentTick;

    // Singleton — there's only one local player.
    private static final SetbackMonitor INSTANCE = new SetbackMonitor();

    public static SetbackMonitor get() { return INSTANCE; }

    private SetbackMonitor() {}

    // Queue packet observations for the client tick thread.
    public void onIncomingPacket(Object packet) {
        if (packet == null) return;
        /*? if >=26.1 {*//*
        if (packet instanceof ClientboundPlayerPositionPacket correction) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) {
                recordPendingCorrection(Double.POSITIVE_INFINITY);
                return;
            }
            var position = correction.change().position();
            var relatives = correction.relatives();
            double targetX = position.x + (relatives.contains(Relative.X) ? player.getX() : 0.0);
            double targetY = position.y + (relatives.contains(Relative.Y) ? player.getY() : 0.0);
            double targetZ = position.z + (relatives.contains(Relative.Z) ? player.getZ() : 0.0);
        *//*?} else {*/
        if (packet instanceof PlayerPositionLookS2CPacket correction) {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;
            if (player == null) {
                recordPendingCorrection(Double.POSITIVE_INFINITY);
                return;
            }
            var position = correction.change().position();
            var relatives = correction.relatives();
            double targetX = position.x + (relatives.contains(PositionFlag.X) ? player.getX() : 0.0);
            double targetY = position.y + (relatives.contains(PositionFlag.Y) ? player.getY() : 0.0);
            double targetZ = position.z + (relatives.contains(PositionFlag.Z) ? player.getZ() : 0.0);
        /*?}*/
            double dx = targetX - player.getX();
            double dy = targetY - player.getY();
            double dz = targetZ - player.getZ();
            recordPendingCorrection(Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
    }

    private void recordPendingCorrection(double distance) {
        pendingCorrections.incrementAndGet();
        long candidate = Double.doubleToRawLongBits(distance);
        pendingMaxCorrectionBits.getAndUpdate(currentBits -> {
            double current = Double.longBitsToDouble(currentBits);
            return distance > current ? candidate : currentBits;
        });
    }

    // Track the client acknowledgement for correction diagnostics.
    public void onOutgoingPacket(Object packet) {
        if (packet == null) return;
        /*? if >=26.1 {*//*
        if (packet instanceof ServerboundAcceptTeleportationPacket) {
        *//*?} else {*/
        if (packet instanceof TeleportConfirmC2SPacket) {
        /*?}*/
            pendingAcknowledgements.incrementAndGet();
        }
    }

    // Call once per client tick (END_CLIENT_TICK). Safe when no player is loaded.
    /*? if >=26.1 {*//*
    public void tick(Minecraft mc) {
    *//*?} else {*/
    public void tick(MinecraftClient mc) {
    /*?}*/
        if (mc == null || mc.player == null
                /*? if >=26.1 {*//*|| mc.level == null*//*?} else {*/|| mc.world == null/*?}*/) {
            primed = false;
            ticksSinceSetback = CALM_WINDOW_TICKS;
            pendingCorrections.set(0);
            pendingAcknowledgements.set(0);
            pendingMaxCorrectionBits.set(0L);
            return;
        }
        currentTick++;
        /*? if >=26.1 {*//*
        LocalPlayer p = mc.player;
        *//*?} else {*/
        ClientPlayerEntity p = mc.player;
        /*?}*/
        double x = p.getX(), y = p.getY(), z = p.getZ();
        if (!primed) {
            lastX = x; lastY = y; lastZ = z;
            primed = true;
            pendingCorrections.set(0);
            pendingAcknowledgements.set(0);
            pendingMaxCorrectionBits.set(0L);
            return;
        }

        int correctionCount = pendingCorrections.getAndSet(0);
        double maxCorrectionDistance = Double.longBitsToDouble(
                pendingMaxCorrectionBits.getAndSet(0L));
        int acknowledgementCount = pendingAcknowledgements.getAndSet(0);
        if (acknowledgementCount > 0) {
            PacketTelemetry.markCorrectionAcknowledged(acknowledgementCount);
        }

        double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        double stationarySq = STATIONARY_DELTA_BLOCKS * STATIONARY_DELTA_BLOCKS;
        lastX = x; lastY = y; lastZ = z;

        if (distSq <= stationarySq) {
            stationaryTicks++;
        } else {
            stationaryTicks = 0;
        }

        if (correctionCount > 0
                && maxCorrectionDistance < MIN_CORRECTION_DISTANCE_BLOCKS) {
            LOGGER.info("[Setback] ignored position sync packets={} maxDelta={}",
                    correctionCount, String.format("%.3f", maxCorrectionDistance));
        } else if (correctionCount > 0) {
            if (ticksSinceSetback >= CALM_WINDOW_TICKS) {
                recordCorrectionEpisode(correctionCount, maxCorrectionDistance);
            }
            for (int i = 0; i < correctionCount; i++) {
                recordSetback("server-correction");
            }
        } else if (ticksSinceSetback < CALM_WINDOW_TICKS) {
            ticksSinceSetback++;
        }
    }

    private void recordCorrectionEpisode(int packetCount, double maxDistance) {
        totalCorrectionEpisodes++;
        correctionEpisodeTicks[correctionEpisodeHead] = currentTick;
        correctionEpisodeHead = (correctionEpisodeHead + 1) % HISTORY_SIZE;
        LOGGER.warn("[Setback] correction episode #{} started with {} packet(s), maxDelta={}",
                totalCorrectionEpisodes, packetCount, String.format("%.3f", maxDistance));
    }

    private void recordSetback(String source) {
        ticksSinceSetback = 0;
        totalSetbacks++;
        setbackTicks[historyHead] = currentTick;
        historyHead = (historyHead + 1) % HISTORY_SIZE;
        MoarNetworkManager.pauseAutomation(AUTOMATION_PAUSE_TICKS, source);
        if ("server-correction".equals(source)) {
            totalServerCorrections++;
            LOGGER.warn("[Setback] server correction #{}; holding automation for {}t",
                    totalServerCorrections, AUTOMATION_PAUSE_TICKS);
        }
        PacketTelemetry.markSetback(totalSetbacks, ticksSinceSetback, source);
    }

    // True when no setback has occurred in the last CALM_WINDOW_TICKS ticks.
    public boolean isCalm() {
        return pendingCorrections.get() == 0 && ticksSinceSetback >= CALM_WINDOW_TICKS;
    }

    // Printer-only: skip the teleport-confirm tick, then keep placing even if
    // the long travel calm window hasn't elapsed (walking on 2b2t).
    public boolean isQuietEnoughToPlace() {
        return pendingCorrections.get() == 0 && ticksSinceSetback >= PLACEMENT_RESUME_TICKS;
    }

    // Ticks elapsed since the most recent setback (capped at CALM_WINDOW_TICKS).
    public int ticksSinceSetback() { return ticksSinceSetback; }

    // Total setbacks observed this session.
    public int totalSetbacks() { return totalSetbacks; }

    public int totalServerCorrections() { return totalServerCorrections; }

    public int totalCorrectionEpisodes() { return totalCorrectionEpisodes; }

    // Setbacks within the last windowTicks client ticks.
    public int recentSetbackCount(int windowTicks) {
        if (windowTicks <= 0) return 0;
        long cutoff = currentTick - windowTicks;
        int count = 0;
        for (long t : setbackTicks) {
            if (t > cutoff && t > 0) count++;
        }
        return count;
    }

    // Correction episodes within the last windowTicks client ticks.
    public int recentCorrectionEpisodeCount(int windowTicks) {
        if (windowTicks <= 0) return 0;
        long cutoff = currentTick - windowTicks;
        int count = 0;
        for (long t : correctionEpisodeTicks) {
            if (t > cutoff && t > 0) count++;
        }
        return count;
    }

    // True when movement has stayed below STATIONARY_DELTA_BLOCKS for minTicks.
    public boolean isStationaryFor(int minTicks) {
        if (minTicks <= 0) return true;
        return stationaryTicks >= minTicks;
    }

    // Reset state. Call on disconnect/world unload to clear baseline.
    public void reset() {
        primed = false;
        ticksSinceSetback = CALM_WINDOW_TICKS;
        totalSetbacks = 0;
        totalServerCorrections = 0;
        totalCorrectionEpisodes = 0;
        stationaryTicks = 0;
        currentTick = 0;
        historyHead = 0;
        correctionEpisodeHead = 0;
        pendingCorrections.set(0);
        pendingAcknowledgements.set(0);
        pendingMaxCorrectionBits.set(0L);
        for (int i = 0; i < setbackTicks.length; i++) setbackTicks[i] = 0;
        for (int i = 0; i < correctionEpisodeTicks.length; i++) correctionEpisodeTicks[i] = 0;
    }
}
