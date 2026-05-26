package dev.moar.travel.highway;

import dev.moar.travel.plan.HighwayCandidate;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

/** Samples highway integrity ahead of the player on a fixed interval. */
public final class HighwayVerifier {

    private static final int SAMPLE_INTERVAL   = 10;  // ticks
    private static final int LOOK_AHEAD_BLOCKS = 64;  // cells along axis
    private static final float GRIEF_THRESHOLD = 0.25f; // 25 % bad → GRIEFED
    private static final float UNLOADED_MAJORITY = 0.5f; // 50 % unloaded → UNLOADED

    private static final HighwayVerifier INSTANCE = new HighwayVerifier();
    public static HighwayVerifier get() { return INSTANCE; }
    private HighwayVerifier() {}

    private HighwayCandidate highway;
    private IntegrityReport lastReport = IntegrityReport.insufficient();
    private int ticksSinceLastSample = SAMPLE_INTERVAL; // sample immediately on first tick
    private int travelDx;
    private int travelDz;

    // ── Public API ───────────────────────────────────────────────
    /** Bind the current highway and forward travel direction. */
    public void setHighway(HighwayCandidate hw, int dx, int dz) {
        this.highway = hw;
        this.travelDx = dx;
        this.travelDz = dz;
        this.lastReport = IntegrityReport.insufficient();
        this.ticksSinceLastSample = SAMPLE_INTERVAL; // force sample next tick
    }

    /** Clear the current highway and reset state. */
    public void clear() {
        highway = null;
        lastReport = IntegrityReport.insufficient();
        ticksSinceLastSample = 0;
        travelDx = 0;
        travelDz = 0;
    }

    /** Most recent integrity report. Never null. */
    public IntegrityReport lastReport() { return lastReport; }

    /**
     * Drive one tick. Should be called from TravelManager while a highway
     * is set. Cheap on non-sample ticks (one counter increment).
     */
    public void tick(BlockPos playerPos) {
        if (highway == null || playerPos == null) return;
        ticksSinceLastSample++;
        if (ticksSinceLastSample < SAMPLE_INTERVAL) return;
        ticksSinceLastSample = 0;
        lastReport = sample(playerPos);
    }

    // ── Internal sampling ────────────────────────────────────────
    private IntegrityReport sample(BlockPos playerPos) {
        if (highway == null || highway.floorY == Integer.MIN_VALUE)
            return IntegrityReport.insufficient();

        HighwayDetectorBridge bridge = HighwayDetectorBridge.get();
        int floorY  = highway.floorY;

        int total = 0, griefed = 0, unloaded = 0;
        int griefStart = -1, griefEnd = -1;

        for (int step = 1; step <= LOOK_AHEAD_BLOCKS; step++) {
            int bx = playerPos.getX() + travelDx * step;
            int bz = playerPos.getZ() + travelDz * step;
            BlockPos floorPos = new BlockPos(bx, floorY, bz);

            HighwayDetectorBridge.CellStatus status = bridge.checkCell(floorPos);
            total++;
            switch (status) {
                case GRIEFED -> {
                    griefed++;
                    if (griefStart < 0) griefStart = step;
                    griefEnd = step;
                }
                case UNLOADED -> unloaded++;
                case OK -> { /* intact */ }
            }
        }

        if (total == 0) return IntegrityReport.insufficient();

        float griefRatio    = (float) griefed  / total;
        float unloadedRatio = (float) unloaded / total;
        float confidence    = 1f - unloadedRatio; // lower confidence when few chunks loaded

        if (griefRatio >= GRIEF_THRESHOLD) {
            return new IntegrityReport(IntegrityReport.Status.GRIEFED, confidence,
                    total, griefed, unloaded, griefStart, griefEnd);
        }
        if (unloadedRatio > UNLOADED_MAJORITY) {
            return new IntegrityReport(IntegrityReport.Status.UNLOADED, confidence,
                    total, griefed, unloaded, -1, -1);
        }
        return new IntegrityReport(IntegrityReport.Status.OK, confidence,
                total, griefed, unloaded, -1, -1);
    }
}
