package dev.moar.travel.detour;

import dev.moar.travel.highway.HighwayDetectorBridge;
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

// Plan a short detour around grief.
public final class DetourPlanner {

    // Step far enough sideways to clear common guardrails.
    public static int SIDE_OFFSET = 3;

    // Push past the grief before merging back.
    public static int CLEAR_MARGIN = 8;

    // Ignore low-confidence grief reports.
    public static float MIN_CONFIDENCE = 0.4f;

    private DetourPlanner() {}

    // Build side, detour, and return waypoints.
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

        // Snap player position to highway center line before computing waypoints.
        // Prevents WP1 landing in the guardrail when the player has drifted off-center.
        int ex     = highway.entry.getX();
        int ez     = highway.entry.getZ();
        int perpSq = perpDx * perpDx + perpDz * perpDz; // 1 for cardinal, 2 for diagonal
        int dp     = (px - ex) * perpDx + (pz - ez) * perpDz;
        px -= perpDx * dp / perpSq;
        pz -= perpDz * dp / perpSq;

        // How far along the axis to travel to clear the grief region
        int clearDepth = report.griefEndOffset() + CLEAR_MARGIN;

        // Try the "natural" side first, then the opposite side if that one
        // hangs over exposed lava — nether highways are commonly built as
        // causeways, and stepping off to the side has no guaranteed floor.
        HighwayDetectorBridge bridge = HighwayDetectorBridge.get();
        int side = 1;
        if (isSideUnsafe(bridge, px, pz, floorY, perpDx, perpDz, stepDx, stepDz, clearDepth, side)) {
            side = -1;
            if (isSideUnsafe(bridge, px, pz, floorY, perpDx, perpDz, stepDx, stepDz, clearDepth, side)) {
                // Both sides hang over lava — don't send the player over it blind.
                return Collections.emptyList();
            }
        }

        // ── WP 1: slide perpendicular off the highway ─────────────
        BlockPos wp1 = new BlockPos(
                px + perpDx * SIDE_OFFSET * side,
                floorY,
                pz + perpDz * SIDE_OFFSET * side);

        // ── WP 2: forward past grief at the side offset ───────────
        BlockPos wp2 = new BlockPos(
                px + stepDx * clearDepth + perpDx * SIDE_OFFSET * side,
                floorY,
                pz + stepDz * clearDepth + perpDz * SIDE_OFFSET * side);

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

    private static boolean isSideUnsafe(HighwayDetectorBridge bridge, int px, int pz, int floorY,
                                        int perpDx, int perpDz, int stepDx, int stepDz,
                                        int clearDepth, int side) {
        int wp1x = px + perpDx * SIDE_OFFSET * side;
        int wp1z = pz + perpDz * SIDE_OFFSET * side;
        int wp2x = px + stepDx * clearDepth + perpDx * SIDE_OFFSET * side;
        int wp2z = pz + stepDz * clearDepth + perpDz * SIDE_OFFSET * side;
        return bridge.hasLavaBelow(wp1x, floorY, wp1z) || bridge.hasLavaBelow(wp2x, floorY, wp2z);
    }
}
