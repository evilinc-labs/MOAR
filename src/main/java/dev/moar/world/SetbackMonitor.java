package dev.moar.world;

import dev.moar.util.PacketTelemetry;
import dev.moar.util.MoarNetworkManager;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
/*?}*/

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Detect server corrections and hold automation until movement settles.
public final class SetbackMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Setback");

    // Stable ticks required after the last setback before isCalm() returns true.
    // 2b2t trace: placements sent ~0.6s after a rubber-band still got
    // swallowed - Grim ignores actions between issuing a teleport and the
    // client's confirm settling, and that grace outlasts the old 12-tick
    // window on real-world latency. 30 ticks (1.5s) clears it with margin.
    private static final int CALM_WINDOW_TICKS = 30;

    // Ring buffer length for recentSetbackCount().
    private static final int HISTORY_SIZE = 64;

    // Movement below this per-tick delta counts as stationary.
    private static final double STATIONARY_DELTA_BLOCKS = 0.025;

    private final AtomicInteger pendingCorrections = new AtomicInteger();
    private final AtomicInteger pendingAcknowledgements = new AtomicInteger();

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
        if (packet instanceof ClientboundPlayerPositionPacket) {
        *//*?} else {*/
        if (packet instanceof PlayerPositionLookS2CPacket) {
        /*?}*/
            pendingCorrections.incrementAndGet();
        }
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
            return;
        }

        int correctionCount = pendingCorrections.getAndSet(0);
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

        if (correctionCount > 0) {
            if (ticksSinceSetback >= CALM_WINDOW_TICKS) {
                recordCorrectionEpisode(correctionCount);
            }
            for (int i = 0; i < correctionCount; i++) {
                recordSetback("server-correction");
            }
        } else if (ticksSinceSetback < CALM_WINDOW_TICKS) {
            ticksSinceSetback++;
        }
    }

    private void recordCorrectionEpisode(int packetCount) {
        totalCorrectionEpisodes++;
        correctionEpisodeTicks[correctionEpisodeHead] = currentTick;
        correctionEpisodeHead = (correctionEpisodeHead + 1) % HISTORY_SIZE;
        LOGGER.warn("[Setback] correction episode #{} started with {} packet(s)",
                totalCorrectionEpisodes, packetCount);
    }

    private void recordSetback(String source) {
        ticksSinceSetback = 0;
        totalSetbacks++;
        setbackTicks[historyHead] = currentTick;
        historyHead = (historyHead + 1) % HISTORY_SIZE;
        MoarNetworkManager.pauseAutomation(CALM_WINDOW_TICKS, source);
        if ("server-correction".equals(source)) {
            totalServerCorrections++;
            LOGGER.warn("[Setback] server correction #{}; holding automation for {}t",
                    totalServerCorrections, CALM_WINDOW_TICKS);
        }
        PacketTelemetry.markSetback(totalSetbacks, ticksSinceSetback, source);
    }

    // True when no setback has occurred in the last CALM_WINDOW_TICKS ticks.
    public boolean isCalm() {
        return pendingCorrections.get() == 0 && ticksSinceSetback >= CALM_WINDOW_TICKS;
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
        for (int i = 0; i < setbackTicks.length; i++) setbackTicks[i] = 0;
        for (int i = 0; i < correctionEpisodeTicks.length; i++) correctionEpisodeTicks[i] = 0;
    }
}
