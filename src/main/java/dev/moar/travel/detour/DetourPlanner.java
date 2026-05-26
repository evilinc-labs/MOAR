package dev.moar.travel.detour;

import dev.moar.travel.highway.IntegrityReport;
import dev.moar.travel.plan.HighwayCandidate;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plans a short side-step detour around a griefed highway section. */
public final class DetourPlanner {

    /** Perpendicular offset in blocks — enough to clear an 8-wide griefed section. */
    public static int SIDE_OFFSET = 12;

    /** Extra blocks past the grief end before returning to the highway. */
    public static int CLEAR_MARGIN = 8;

    /**
     * Minimum confidence in the integrity report required before trusting the
     * grief offsets.  Below this threshold we return an empty list (caller aborts).
     */
    public static float MIN_CONFIDENCE = 0.4f;

    private DetourPlanner() {}

    /** Compute side, bypass, and return waypoints. */
    public static List<BlockPos> plan(HighwayCandidate highway,
                                      IntegrityReport report,
                                      BlockPos playerPos,
                                      int travelDx,
                                      int travelDz) {
        if (highway == null || report == null || playerPos == null) return Collections.emptyList();
        if (report.status() != IntegrityReport.Status.GRIEFED)     return Collections.emptyList();
        if (report.confidence() < MIN_CONFIDENCE)                   return Collections.emptyList();
        if (report.griefEndOffset() < 0)                           return Collections.emptyList();
        if (travelDx == 0 && travelDz == 0)                         return Collections.emptyList();

        int floorY  = highway.floorY;
        if (floorY == Integer.MIN_VALUE) return Collections.emptyList();

        int stepDx  = travelDx;
        int stepDz  = travelDz;
        int perpDx  = stepDz;
        int perpDz  = -stepDx;

        int px = playerPos.getX();
        int pz = playerPos.getZ();

        // How far along the axis to travel to clear the grief region
        int clearDepth = report.griefEndOffset() + CLEAR_MARGIN;

        // ── WP 1: slide perpendicular off the highway ─────────────
        BlockPos wp1 = new BlockPos(
                px + perpDx * SIDE_OFFSET,
                floorY,
                pz + perpDz * SIDE_OFFSET);

        // ── WP 2: forward past grief at the side offset ───────────
        BlockPos wp2 = new BlockPos(
                px + stepDx * clearDepth + perpDx * SIDE_OFFSET,
                floorY,
                pz + stepDz * clearDepth + perpDz * SIDE_OFFSET);

        // ── WP 3: return to highway ───────────────────────────────
        BlockPos wp3 = new BlockPos(
                px + stepDx * clearDepth,
                floorY,
                pz + stepDz * clearDepth);

        List<BlockPos> waypoints = new ArrayList<>(3);
        waypoints.add(wp1);
        waypoints.add(wp2);
        waypoints.add(wp3);
        return waypoints;
    }
}
