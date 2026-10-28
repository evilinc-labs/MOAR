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

    @Test
    void referenceRingDistancesAreRecognizedAtTheirDeclaredCoordinates() {
        for (int radius : new int[]{200, 7500, 62500}) {
            HighwayGeometry.GeometryCandidate ring = HighwayGeometry.rankCandidates(
                            radius, 300, true, false).stream()
                    .filter(candidate -> candidate.category == HighwayCandidate.Category.RING)
                    .filter(candidate -> candidate.ringOrDiamondDist == radius)
                    .findFirst().orElseThrow();
            assertArrayEquals(new int[]{radius, 300},
                    HighwayGeometry.projectOnto(ring, radius + 2, 300));
        }
    }

    @Test
    void referenceTenThousandDiamondIsRecognizedWhenEnabled() {
        assertTrue(HighwayGeometry.rankCandidates(4000, 6000, true, true).stream()
                .anyMatch(candidate -> candidate.category == HighwayCandidate.Category.DIAMOND
                        && candidate.ringOrDiamondDist == 10000));
    }
}
