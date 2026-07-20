package dev.moar.travel.highway;

import dev.moar.travel.plan.HighwayCandidate;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

// Sample highway integrity ahead of the player.
public final class HighwayVerifier {

    private static final int SAMPLE_INTERVAL   = 10;  // ticks
    private static final int LOOK_AHEAD_BLOCKS = 64;  // cells along axis
    private static final float GRIEF_THRESHOLD = 0.30f; // 30 % bad → GRIEFED (was 0.25; raised to reduce false positives at road crossings)
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
    // Bind the active highway and travel direction.
    public void setHighway(HighwayCandidate hw, int dx, int dz) {
        this.highway = hw;
        this.travelDx = dx;
        this.travelDz = dz;
        this.lastReport = IntegrityReport.insufficient();
        this.ticksSinceLastSample = SAMPLE_INTERVAL; // force sample next tick
    }

    // Drop the last report without forcing a new sample.
    public void resetLastReport() {
        this.lastReport = IntegrityReport.insufficient();
    }

    // Clear the active highway state.
    public void clear() {
        highway = null;
        lastReport = IntegrityReport.insufficient();
        ticksSinceLastSample = 0;
        travelDx = 0;
        travelDz = 0;
    }

    // Return the last integrity report.
    public IntegrityReport lastReport() { return lastReport; }

    // Sample on the configured interval.
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

        // Snap origin to highway centre — raw playerPos drifts perp during elytra, causing FP grief at high lookahead.
        int ox = playerPos.getX();
        int oz = playerPos.getZ();
        if (highway.axis.diagonal) {
            int perpDx = highway.axis.perpDx();
            int perpDz = highway.axis.perpDz();
            int ex = highway.entry.getX(), ez = highway.entry.getZ();
            // Project player onto axis through entry; |perp|² = 2 for unit-diagonal axes.
            int dp = (ox - ex) * perpDx + (oz - ez) * perpDz;
            int snapAmount = dp / (perpDx * perpDx + perpDz * perpDz);
            ox -= perpDx * snapAmount;
            oz -= perpDz * snapAmount;
        }

        int total = 0, griefed = 0, unloaded = 0;
        int griefStart = -1, griefEnd = -1;
        int sampleLimit = LOOK_AHEAD_BLOCKS;
        if (highway.exit != null) {
            int exitDx = highway.exit.getX() - ox;
            int exitDz = highway.exit.getZ() - oz;
            int directionDot = exitDx * travelDx + exitDz * travelDz;
            if (directionDot <= 0) return IntegrityReport.insufficient();
            sampleLimit = Math.min(sampleLimit, Math.max(Math.abs(exitDx), Math.abs(exitDz)));
        }

        for (int step = 1; step <= sampleLimit; step++) {
            int bx = ox + travelDx * step;
            int bz = oz + travelDz * step;

            // Floor-only: checkCell also needs air above, but nether netherrack causes FP grief.
            HighwayDetectorBridge.CellStatus status = bridge.checkFloorOnly(bx, floorY, bz);
            total++;
            switch (status) {
                case GRIEFED -> {
                    griefed++;
                    if (griefStart < 0) griefStart = step;
                    griefEnd = step;
                }
                case UNLOADED  -> unloaded++;
                case CAVE_PASS -> { /* natural cave intersection — not grief */ }
                case OK        -> { /* intact */ }
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
