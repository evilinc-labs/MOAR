package dev.moar.travel.highway;

import dev.moar.travel.plan.HighwayCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

// Sample highway integrity ahead of the player.
public final class HighwayVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/HighwayVerifier");
    private static final int SAMPLE_INTERVAL   = 10;  // ticks
    private static final int LOOK_AHEAD_BLOCKS = 64;  // cells along axis
    private static final int NEAR_LOOK_AHEAD_BLOCKS = 16;
    private static final int NEAR_GRIEF_RUN = 3;
    private static final float GRIEF_THRESHOLD = 0.30f; // 30 % bad → GRIEFED (was 0.25; raised to reduce false positives at road crossings)
    private static final float UNLOADED_MAJORITY = 0.5f; // 50 % unloaded → UNLOADED

    private static final HighwayVerifier INSTANCE = new HighwayVerifier(HighwayDetectorBridge.get());
    public static HighwayVerifier get() { return INSTANCE; }
    private final HighwayDetectorBridge bridge;

    HighwayVerifier(HighwayDetectorBridge bridge) {
        this.bridge = bridge;
    }

    private HighwayCandidate highway;
    private IntegrityReport lastReport = IntegrityReport.insufficient();
    private int ticksSinceLastSample = SAMPLE_INTERVAL; // sample immediately on first tick
    private int travelDx;
    private int travelDz;
    private int verifiedFloorY = Integer.MIN_VALUE;
    private int verifiedWidth;
    private long floorTransitionProjection = Long.MAX_VALUE;
    private HighwayDetectorBridge.Surface surface = HighwayDetectorBridge.Surface.PAVED;

    // ── Public API ───────────────────────────────────────────────
    // Bind the active highway and travel direction.
    public void setHighway(HighwayCandidate hw, int dx, int dz) {
        this.highway = hw;
        this.travelDx = dx;
        this.travelDz = dz;
        this.verifiedFloorY = hw == null ? Integer.MIN_VALUE : hw.floorY;
        this.verifiedWidth = hw == null ? 0 : hw.width;
        this.surface = hw != null && hw.unpavedCorridor
                ? HighwayDetectorBridge.Surface.TUNNEL
                : HighwayDetectorBridge.Surface.PAVED;
        this.floorTransitionProjection = hw != null && hw.unpavedCorridor
                ? Long.MIN_VALUE : Long.MAX_VALUE;
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
        verifiedFloorY = Integer.MIN_VALUE;
        verifiedWidth = 0;
        floorTransitionProjection = Long.MAX_VALUE;
        surface = HighwayDetectorBridge.Surface.PAVED;
    }

    // Return the last integrity report.
    public IntegrityReport lastReport() { return lastReport; }

    public boolean hasUnpavedSurface() {
        return surface != HighwayDetectorBridge.Surface.PAVED;
    }

    public int verifiedFloorY() { return verifiedFloorY; }

    public long floorTransitionProjection() { return floorTransitionProjection; }

    // Refresh integrity before a movement handoff.
    public IntegrityReport sampleNow(BlockPos playerPos) {
        if (highway == null || playerPos == null) return lastReport;
        ticksSinceLastSample = 0;
        lastReport = sample(playerPos);
        return lastReport;
    }

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

        int floorY = verifiedFloorY == Integer.MIN_VALUE
                ? highway.floorY
                : verifiedFloorY;

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
        int nearRun = 0, nearRunStart = -1;
        int longestNearRun = 0, longestNearStart = -1, longestNearEnd = -1;
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
            HighwayDetectorBridge.CellStatus status = surface == HighwayDetectorBridge.Surface.PAVED
                    ? bridge.checkFloorOnly(bx, floorY, bz)
                    : bridge.checkTunnelCorridor(bx, floorY, bz, highway.axis, verifiedWidth);
            // A paved section can step into an unpaved road at another Y level.
            // Keep accepting the original paving during the transition window.
            if (status == HighwayDetectorBridge.CellStatus.GRIEFED
                    && surface != HighwayDetectorBridge.Surface.PAVED
                    && floorY != highway.floorY) {
                HighwayDetectorBridge.CellStatus paved =
                        bridge.checkFloorOnly(bx, highway.floorY, bz);
                if (paved == HighwayDetectorBridge.CellStatus.OK) status = paved;
            }
            total++;
            switch (status) {
                case GRIEFED -> {
                    griefed++;
                    if (griefStart < 0) griefStart = step;
                    griefEnd = step;
                    if (step <= NEAR_LOOK_AHEAD_BLOCKS) {
                        if (nearRun == 0) nearRunStart = step;
                        nearRun++;
                        if (nearRun > longestNearRun) {
                            longestNearRun = nearRun;
                            longestNearStart = nearRunStart;
                            longestNearEnd = step;
                        }
                    }
                }
                case UNLOADED -> {
                    unloaded++;
                    nearRun = 0;
                    nearRunStart = -1;
                }
                case CAVE_PASS, OK -> {
                    nearRun = 0;
                    nearRunStart = -1;
                }
            }
        }

        if (total == 0) return IntegrityReport.insufficient();

        float griefRatio    = (float) griefed  / total;
        float unloadedRatio = (float) unloaded / total;
        float confidence    = 1f - unloadedRatio; // lower confidence when few chunks loaded

        boolean griefDetected = griefRatio >= GRIEF_THRESHOLD
                || longestNearRun >= NEAR_GRIEF_RUN;
        if (griefDetected && surface == HighwayDetectorBridge.Surface.PAVED) {
            int probeStep = longestNearRun >= NEAR_GRIEF_RUN
                    ? longestNearStart
                    : griefStart;
            // Scan inside the suspect section first. A scan centred on the
            // pavement boundary includes too little of the new surface to pass
            // the detector's continuity threshold.
            int[] probes = {Math.min(sampleLimit, probeStep + 12), Math.max(1, probeStep)};
            for (int step : probes) {
                BlockPos probe = new BlockPos(
                        ox + travelDx * step, playerPos.getY(), oz + travelDz * step);
                Optional<HighwayDetectorBridge.ScanResult> scan =
                        bridge.scanAt(probe, highway.axis);
                if (scan.isEmpty() || scan.get().surface() == HighwayDetectorBridge.Surface.PAVED) {
                    continue;
                }
                HighwayDetectorBridge.ScanResult unpaved = scan.get();
                surface = unpaved.surface();
                verifiedFloorY = unpaved.floorY();
                verifiedWidth = unpaved.width();
                floorTransitionProjection = findFloorTransitionProjection(
                        ox, oz, sampleLimit, unpaved);
                LOGGER.info("[Travel] integrity switched to unpaved {} floorY={} width={} conf={}",
                        unpaved.surface(), unpaved.floorY(), unpaved.width(),
                        String.format("%.2f", unpaved.blockConfidence()));
                return sample(playerPos);
            }
        }

        if (griefDetected) {
            int reportStart = longestNearRun >= NEAR_GRIEF_RUN ? longestNearStart : griefStart;
            int reportEnd = longestNearRun >= NEAR_GRIEF_RUN ? longestNearEnd : griefEnd;
            return new IntegrityReport(IntegrityReport.Status.GRIEFED, confidence,
                    total, griefed, unloaded, reportStart, reportEnd);
        }
        if (unloadedRatio > UNLOADED_MAJORITY) {
            return new IntegrityReport(IntegrityReport.Status.UNLOADED, confidence,
                    total, griefed, unloaded, -1, -1);
        }
        return new IntegrityReport(IntegrityReport.Status.OK, confidence,
                total, griefed, unloaded, -1, -1);
    }

    private long findFloorTransitionProjection(int ox, int oz, int sampleLimit,
                                               HighwayDetectorBridge.ScanResult unpaved) {
        for (int step = 1; step <= sampleLimit; step++) {
            int x = ox + travelDx * step;
            int z = oz + travelDz * step;
            if (bridge.checkFloorOnly(x, highway.floorY, z) == HighwayDetectorBridge.CellStatus.OK
                    || bridge.checkTunnelCorridor(x, unpaved.floorY(), z, highway.axis,
                            unpaved.width()) != HighwayDetectorBridge.CellStatus.OK) {
                continue;
            }
            return (long) x * travelDx + (long) z * travelDz;
        }
        return (long) ox * travelDx + (long) oz * travelDz;
    }
}
