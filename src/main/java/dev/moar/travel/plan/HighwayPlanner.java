package dev.moar.travel.plan;

import dev.moar.travel.highway.HighwayDetectorBridge;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plans highway, mining, and optional flight legs for a travel mission. */
public final class HighwayPlanner {

    /** Default nether highway floor Y (2b2t ≈ 120; refined at runtime by block scan). */
    private static final int DEFAULT_NETHER_FLOOR_Y = 120;

    public static final class Options {
        /** Server-supplied floor Y hint — overrides the default. */
        public Integer expectedFloorY = null;
        /** Distance (XZ blocks) from exit to dest beyond which a FlightLeg is added. */
        public int freeNetherFlightThreshold = 1500;
        /** Enable ring-road detection. */
        public boolean detectRings = false;
        /** Enable diamond-road detection. */
        public boolean detectDiamonds = false;
        /** Minimum coordinate confidence to trust a highway candidate. */
        public float minConfidence = 0.15f;

        public Options expectedFloorY(int y)                { this.expectedFloorY = y;               return this; }
        public Options freeNetherFlightThreshold(int v)     { this.freeNetherFlightThreshold = v;    return this; }
        public Options detectRings(boolean v)               { this.detectRings = v;                  return this; }
        public Options detectDiamonds(boolean v)            { this.detectDiamonds = v;               return this; }
        public Options minConfidence(float v)               { this.minConfidence = v;                return this; }
    }

    public Optional<HighwayRoute> plan(BlockPos origin, BlockPos destination, Options opts) {
        if (origin == null || destination == null) return Optional.empty();
        if (opts == null) opts = new Options();

        int ox = origin.getX(),      oz = origin.getZ();
        int dx = destination.getX(), dz = destination.getZ();

        // ── 1. Rank highway candidates by destination coordinates ──
        List<HighwayGeometry.GeometryCandidate> candidates =
                HighwayGeometry.rankCandidates(dx, dz, opts.detectRings, opts.detectDiamonds);

        HighwayGeometry.GeometryCandidate best = null;
        for (HighwayGeometry.GeometryCandidate c : candidates) {
            if (c.confidence >= opts.minConfidence) { best = c; break; }
        }
        if (best == null) best = fallbackAxis(ox, oz, dx, dz);

        // ── 2. Determine floor Y ──────────────────────────────────
        int floorY = opts.expectedFloorY != null ? opts.expectedFloorY : DEFAULT_NETHER_FLOOR_Y;

        // ── 3. Project origin and destination onto highway ────────
        int[] onRampXZ  = HighwayGeometry.projectOnto(best, ox, oz);
        int[] exitXZ    = HighwayGeometry.projectOnto(best, dx, dz);

        BlockPos onRamp     = new BlockPos(onRampXZ[0], floorY, onRampXZ[1]);
        BlockPos exitColumn = new BlockPos(exitXZ[0],   floorY, exitXZ[1]);

        // ── 4. Optional: refine floorY via live block scan at origin ──
        Optional<HighwayDetectorBridge.ScanResult> scan =
                HighwayDetectorBridge.get().scanAt(new BlockPos(ox, floorY, oz), best.axis);
        if (scan.isPresent()) {
            floorY      = scan.get().floorY();
            onRamp      = new BlockPos(onRampXZ[0], floorY, onRampXZ[1]);
            exitColumn  = new BlockPos(exitXZ[0],   floorY, exitXZ[1]);
        }

        HighwayCandidate primary = new HighwayCandidate(
                best.axis, best.category, floorY, onRamp, exitColumn, best.confidence,
                best.ringOrDiamondDist, best.ringSide, best.diamondSegment,
                scan.map(HighwayDetectorBridge.ScanResult::width).orElse(0),
                scan.map(HighwayDetectorBridge.ScanResult::hasLeftRail).orElse(false),
                scan.map(HighwayDetectorBridge.ScanResult::hasRightRail).orElse(false));

        int[] travelDir = travelDirection(best, onRampXZ, exitXZ);

        // ── 5. Build legs ─────────────────────────────────────────
        List<HighwayRoute.Leg> legs = new ArrayList<>();
        double originToOnRamp  = HighwayGeometry.horizontalDistance(ox, oz, onRampXZ[0], onRampXZ[1]);
        double bounceLength    = HighwayGeometry.horizontalDistance(onRampXZ[0], onRampXZ[1], exitXZ[0], exitXZ[1]);
        double exitToDest      = HighwayGeometry.horizontalDistance(exitXZ[0], exitXZ[1], dx, dz);

        // Only add approach leg when we are meaningfully off the highway
        if (originToOnRamp > 10) {
            legs.add(new HighwayRoute.ApproachLeg(onRamp));
        }
        legs.add(new HighwayRoute.BounceLeg(primary, exitColumn, travelDir[0], travelDir[1]));
        legs.add(new HighwayRoute.OffRampLeg(exitColumn));
        legs.add(new HighwayRoute.MineLeg(destination));
        if (exitToDest > opts.freeNetherFlightThreshold) {
            legs.add(new HighwayRoute.FlightLeg(destination));
        }

        double totalCost = originToOnRamp + bounceLength + exitToDest;
        return Optional.of(new HighwayRoute(primary, legs, totalCost, travelDir[0], travelDir[1]));
    }

    // ── Helpers ───────────────────────────────────────────────────
    private static int[] travelDirection(HighwayGeometry.GeometryCandidate c,
                                         int[] onRampXZ, int[] exitXZ) {
        int tx = Integer.compare(exitXZ[0], onRampXZ[0]);
        int tz = Integer.compare(exitXZ[1], onRampXZ[1]);
        if (tx == 0 && tz == 0) {
            tx = c.axis.stepDx;
            tz = c.axis.stepDz;
        }
        return new int[]{tx, tz};
    }

    private static HighwayGeometry.GeometryCandidate fallbackAxis(int ox, int oz, int dx, int dz) {
        int absDx = Math.abs(dx - ox);
        int absDz = Math.abs(dz - oz);
        HighwayCandidate.Axis axis = absDx >= absDz
                ? (dx >= ox ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X)
                : (dz >= oz ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z);
        return new HighwayGeometry.GeometryCandidate(axis, 0.05f);
    }
}
