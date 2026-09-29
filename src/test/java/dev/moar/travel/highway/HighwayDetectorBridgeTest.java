package dev.moar.travel.highway;

import dev.moar.travel.plan.HighwayCandidate;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class HighwayDetectorBridgeTest {
    @Test
    void detectsFifteenBlockWidePavedHighway() {
        Predicate<BlockPos> floor = p -> p.getY() == 119 && Math.abs(p.getX()) <= 7;
        HighwayDetectorBridge detector = new HighwayDetectorBridge(floor, p -> p.getY() >= 120, floor);
        var scan = detector.scanAt(new BlockPos(0, 120, -800000), HighwayCandidate.Axis.MINUS_Z).orElseThrow();
        assertEquals(15, scan.width());
        assertEquals(119, scan.floorY());
        assertEquals(HighwayDetectorBridge.Surface.PAVED, scan.surface());
    }

    @Test
    void scansInsideRoadWhenPlayerIsStandingOnGuardrail() {
        Predicate<BlockPos> pavement = p -> Math.abs(p.getX()) <= 3
                && (p.getY() == 119 || (p.getY() == 120 && Math.abs(p.getX()) == 3));
        HighwayDetectorBridge detector = new HighwayDetectorBridge(pavement, pavement.negate(), pavement);
        var scan = detector.scanAt(new BlockPos(3, 121, -800000), HighwayCandidate.Axis.MINUS_Z).orElseThrow();
        assertEquals(119, scan.floorY());
        assertEquals(0, scan.centerX());
        assertEquals(5, scan.width());
        assertTrue(scan.hasLeftRail());
        assertTrue(scan.hasRightRail());
    }

    @Test
    void detectsDeclaredUnpavedRoadWithoutTunnelRoof() {
        Predicate<BlockPos> floor = p -> p.getY() == 119 && Math.abs(p.getX()) <= 1;
        HighwayDetectorBridge detector = new HighwayDetectorBridge(p -> false, p -> p.getY() >= 120, floor);
        var scan = detector.scanAt(new BlockPos(0, 120, -800000), HighwayCandidate.Axis.MINUS_Z).orElseThrow();
        assertEquals(HighwayDetectorBridge.Surface.DECLARED, scan.surface());
        assertEquals(119, scan.floorY());
    }

    @Test
    void declaredCoordinatesAloneDoNotConfirmRoad() {
        HighwayDetectorBridge detector = new HighwayDetectorBridge(p -> false, p -> true, p -> false);
        assertTrue(detector.scanAt(new BlockPos(0, 120, -800000), HighwayCandidate.Axis.MINUS_Z).isEmpty());
    }

    @Test
    void smallObsidianPatchDoesNotConfirmHighway() {
        Predicate<BlockPos> floor = p -> p.getY() == 119 && Math.abs(p.getX()) <= 3
                && Math.abs(p.getZ() + 800000) <= 4;
        HighwayDetectorBridge detector = new HighwayDetectorBridge(floor, p -> p.getY() >= 120, floor);
        assertTrue(detector.scanAt(new BlockPos(0, 120, -800000), HighwayCandidate.Axis.MINUS_Z).isEmpty());
    }

    @Test
    void openNaturalTerrainAwayFromDeclaredRoadIsNotHighway() {
        Predicate<BlockPos> floor = p -> p.getY() == 119;
        HighwayDetectorBridge detector = new HighwayDetectorBridge(p -> false, p -> p.getY() >= 120, floor);
        assertTrue(detector.scanAt(new BlockPos(12345, 120, 54321), HighwayCandidate.Axis.PLUS_X).isEmpty());
    }
}
