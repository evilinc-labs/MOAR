package dev.moar.travel.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HighwayGeometryTest {
    @Test
    void declaredDiagonalWorksInEitherDirection() {
        assertArrayEquals(new int[]{-800001, -800001},
                HighwayGeometry.declaredCenter(-800000, -800002, HighwayCandidate.Axis.DIAG_MX_MZ).orElseThrow());
        assertTrue(HighwayGeometry.declaredCenter(-800000, -800002, HighwayCandidate.Axis.DIAG_PX_PZ).isPresent());
    }

    @Test
    void destinationToleranceDoesNotDeclareRemoteTerrainAsHighway() {
        assertTrue(HighwayGeometry.declaredCenter(-800000, -799000, HighwayCandidate.Axis.DIAG_MX_MZ).isEmpty());
    }

    @Test
    void declaredRingFollowsItsOwnCoordinateInsteadOfSpawnAxis() {
        assertArrayEquals(new int[]{-100000, -30000},
                HighwayGeometry.declaredCenter(-100002, -30000, HighwayCandidate.Axis.MINUS_Z).orElseThrow());
        assertTrue(HighwayGeometry.declaredCenter(-100002, -30000, HighwayCandidate.Axis.MINUS_X).isEmpty());
    }
}
