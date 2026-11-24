package dev.moar.travel.highway;

import dev.moar.travel.plan.HighwayCandidate;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighwayVerifierTest {
    @Test
    void followsDeclaredUnpavedRoadAfterPavingEndsOneBlockLower() {
        int roadZ = 1_000_000;
        Predicate<BlockPos> paved = p -> p.getY() == 119 && p.getX() <= 140
                && Math.abs(p.getZ() - roadZ) <= 1;
        Predicate<BlockPos> floor = p -> paved.test(p)
                || (p.getY() == 120 && p.getX() >= 141 && Math.abs(p.getZ() - roadZ) <= 1);
        Predicate<BlockPos> air = p -> p.getY() >= 120 && !floor.test(p);
        HighwayDetectorBridge detector = new HighwayDetectorBridge(paved, air, floor, p -> true);

        // The first suspect cell straddles the boundary and cannot establish
        // enough continuity at either floor height on its own.
        assertTrue(detector.scanAt(new BlockPos(143, 120, roadZ),
                HighwayCandidate.Axis.PLUS_X).isEmpty());

        HighwayCandidate road = new HighwayCandidate(HighwayCandidate.Axis.PLUS_X,
                HighwayCandidate.Category.RING, 119,
                new BlockPos(8, 119, roadZ), new BlockPos(1_000_000, 119, roadZ),
                0.6f, 1_000_000, HighwayCandidate.RingSide.SOUTH, null,
                3, false, false);
        HighwayVerifier verifier = new HighwayVerifier(detector);
        verifier.setHighway(road, 1, 0);

        IntegrityReport report = verifier.sampleNow(new BlockPos(104, 120, roadZ));
        assertEquals(IntegrityReport.Status.OK, report.status());
        assertEquals(0, report.griefedCells());
        assertTrue(verifier.hasUnpavedSurface());
        assertEquals(120, verifier.verifiedFloorY());
        assertEquals(141L, verifier.floorTransitionProjection());
    }
}
