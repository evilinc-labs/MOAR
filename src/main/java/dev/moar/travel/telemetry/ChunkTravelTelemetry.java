package dev.moar.travel.telemetry;

import dev.moar.util.PacketTelemetry;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Measures decoded chunk arrival versus availability in the client world. */
public final class ChunkTravelTelemetry {
    private static final int MAX_PENDING_CHUNKS = 8192;
    private static final int MAX_RUNWAY_BLOCKS = 512;
    private static final long REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final ConcurrentHashMap<Long, Long> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger RECEIVED = new AtomicInteger();
    private static final AtomicInteger LOADED = new AtomicInteger();
    private static final AtomicInteger UNMATCHED = new AtomicInteger();
    private static final AtomicInteger OVERFLOWS = new AtomicInteger();
    private static final AtomicLong TOTAL_READY_NANOS = new AtomicLong();
    private static final AtomicLong MAX_READY_NANOS = new AtomicLong();
    private static long lastReportNanos;
    private static long lastTickNanos;
    private static long maxTickGapNanos;

    private ChunkTravelTelemetry() {}

    public static void reset() {
        PENDING.clear();
        RECEIVED.set(0);
        LOADED.set(0);
        UNMATCHED.set(0);
        OVERFLOWS.set(0);
        TOTAL_READY_NANOS.set(0);
        MAX_READY_NANOS.set(0);
        lastReportNanos = 0;
        lastTickNanos = 0;
        maxTickGapNanos = 0;
    }

    // Called on the Netty thread after packet decoding, before world insertion.
    public static void onDecodedChunk(int chunkX, int chunkZ) {
        if (!recording()) return;
        if (PENDING.size() >= MAX_PENDING_CHUNKS) {
            PENDING.clear();
            OVERFLOWS.incrementAndGet();
        }
        PENDING.put(key(chunkX, chunkZ), System.nanoTime());
        RECEIVED.incrementAndGet();
    }

    // Fabric fires this after the chunk becomes available to world queries.
    public static void onChunkLoaded(int chunkX, int chunkZ) {
        if (!recording()) return;
        Long receivedNanos = PENDING.remove(key(chunkX, chunkZ));
        if (receivedNanos == null) {
            UNMATCHED.incrementAndGet();
            return;
        }
        long delay = Math.max(0, System.nanoTime() - receivedNanos);
        LOADED.incrementAndGet();
        TOTAL_READY_NANOS.addAndGet(delay);
        MAX_READY_NANOS.accumulateAndGet(delay, Math::max);
    }

    // Called on the client tick thread; one concise row per second avoids filling the trace.
    public static void tick() {
        if (!recording()) return;
        long now = System.nanoTime();
        if (lastTickNanos != 0) {
            maxTickGapNanos = Math.max(maxTickGapNanos, now - lastTickNanos);
        }
        lastTickNanos = now;
        if (lastReportNanos == 0) {
            lastReportNanos = now;
            return;
        }
        if (now - lastReportNanos < REPORT_INTERVAL_NANOS) return;

        int received = RECEIVED.getAndSet(0);
        int loaded = LOADED.getAndSet(0);
        int unmatched = UNMATCHED.getAndSet(0);
        int overflows = OVERFLOWS.getAndSet(0);
        long totalReady = TOTAL_READY_NANOS.getAndSet(0);
        long maxReady = MAX_READY_NANOS.getAndSet(0);
        long maxGap = maxTickGapNanos;
        maxTickGapNanos = 0;
        double seconds = (now - lastReportNanos) / 1_000_000_000.0;
        lastReportNanos = now;

        Motion motion = currentMotion();
        int runway = motion == null ? -1 : loadedRunway(motion);
        PacketTelemetry.markTravelChunk("window seconds=" + format(seconds)
                + " decoded=" + received
                + " worldReady=" + loaded
                + " unmatched=" + unmatched
                + " pending=" + PENDING.size()
                + " overflow=" + overflows
                + " decodedToReadyAvgMs=" + (loaded == 0 ? "n/a" : format(totalReady / (loaded * 1_000_000.0)))
                + " decodedToReadyMaxMs=" + (loaded == 0 ? "n/a" : format(maxReady / 1_000_000.0))
                + " maxClientTickGapMs=" + format(maxGap / 1_000_000.0)
                + " speedBps=" + (motion == null ? "n/a" : format(motion.speed * 20.0))
                + " loadedAheadBlocks=" + runway);
    }

    private static boolean recording() {
        return PacketTelemetry.isEnabled() && PacketTelemetry.mode() == PacketTelemetry.Mode.TRAVEL;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private record Motion(double x, double z, double dx, double dz, double speed) {}

    private static Motion currentMotion() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        var velocity = mc.player.getDeltaMovement();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return null;
        var velocity = mc.player.getVelocity();
        /*?}*/
        double speed = Math.hypot(velocity.x, velocity.z);
        if (speed < 0.01) return new Motion(mc.player.getX(), mc.player.getZ(), 0, 0, speed);
        return new Motion(mc.player.getX(), mc.player.getZ(),
                velocity.x / speed, velocity.z / speed, speed);
    }

    private static int loadedRunway(Motion motion) {
        if (motion.speed < 0.01) return 0;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return -1;
        /*?}*/
        for (int distance = 0; distance <= MAX_RUNWAY_BLOCKS; distance += 4) {
            int chunkX = Math.floorDiv((int) Math.floor(motion.x + motion.dx * distance), 16);
            int chunkZ = Math.floorDiv((int) Math.floor(motion.z + motion.dz * distance), 16);
            /*? if >=26.1 {*//*
            if (!mc.level.hasChunk(chunkX, chunkZ)) return distance;
            *//*?} else {*/
            if (!mc.world.isChunkLoaded(chunkX, chunkZ)) return distance;
            /*?}*/
        }
        return MAX_RUNWAY_BLOCKS;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
