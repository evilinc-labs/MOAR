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

    @Test
    void diamondProjectionStaysOnTheDeclaredSegment() {
        int[][] points = {{4000, 6000}, {-4000, 6000}, {-4000, -6000}, {4000, -6000}};
        for (int[] point : points) {
            HighwayGeometry.GeometryCandidate diamond = HighwayGeometry.rankCandidates(
                            point[0], point[1], true, true).stream()
                    .filter(candidate -> candidate.category == HighwayCandidate.Category.DIAMOND
                            && candidate.ringOrDiamondDist == 10000)
                    .findFirst().orElseThrow();
            assertArrayEquals(point, HighwayGeometry.projectOnto(diamond, point[0], point[1]));
            assertArrayEquals(point,
                    HighwayGeometry.declaredCenter(point[0], point[1], diamond.axis).orElseThrow());
        }
    }

    @Test
    void diamondProjectionClampsAtItsCorners() {
        HighwayGeometry.GeometryCandidate diamond = HighwayGeometry.rankCandidates(
                        4000, 6000, true, true).stream()
                .filter(candidate -> candidate.category == HighwayCandidate.Category.DIAMOND
                        && candidate.ringOrDiamondDist == 10000)
                .findFirst().orElseThrow();
        assertArrayEquals(new int[]{10000, 0}, HighwayGeometry.projectOnto(diamond, 20000, 0));
        assertArrayEquals(new int[]{0, 10000}, HighwayGeometry.projectOnto(diamond, 0, 20000));
    }

    @Test
    void diamondCornerRecognizesBothConnectingRoads() {
        assertArrayEquals(new int[]{0, 10000},
                HighwayGeometry.declaredCenter(0, 10000, HighwayCandidate.Axis.DIAG_MX_PZ).orElseThrow());
        assertArrayEquals(new int[]{0, 10000},
                HighwayGeometry.declaredCenter(0, 10000, HighwayCandidate.Axis.DIAG_MX_MZ).orElseThrow());
    }
}
