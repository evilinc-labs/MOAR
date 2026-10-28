package dev.moar.travel.plan;

import dev.moar.travel.highway.HighwayDetectorBridge;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HighwayPlannerTest {
    private final HighwayPlanner unscanned = new HighwayPlanner((pos, axis) -> Optional.empty());

    @Test
    void restartBeyondDiagonalOfframpContinuesToDestination() {
        BlockPos destination = new BlockPos(-872787, 64, -794058);
        HighwayRoute route = unscanned.plan(new BlockPos(-833520, 32, -833330), destination,
                new HighwayPlanner.Options().horizontalDestination(true)).orElseThrow();
        assertEquals(1, route.legs.size());
        HighwayRoute.FlightLeg flight = assertInstanceOf(HighwayRoute.FlightLeg.class, route.legs.get(0));
        assertEquals(destination.getX(), flight.destination().getX());
        assertEquals(destination.getZ(), flight.destination().getZ());
    }

    @Test
    void longUsefulHighwayRunStillBounces() {
        HighwayRoute route = unscanned.plan(new BlockPos(-799202, 120, -799202),
                new BlockPos(-872787, 64, -794058), new HighwayPlanner.Options()).orElseThrow();
        HighwayRoute.BounceLeg bounce = assertInstanceOf(HighwayRoute.BounceLeg.class, route.legs.get(0));
        assertEquals(-1, bounce.travelDx());
        assertEquals(-1, bounce.travelDz());
    }

    @Test
    void crossQuadrantTripStaysOnDetectedDiagonalUntilRingTransfer() {
        HighwayPlanner planner = new HighwayPlanner((pos, axis) ->
                axis.isParallelTo(HighwayCandidate.Axis.DIAG_PX_MZ)
                        ? Optional.of(new HighwayDetectorBridge.ScanResult(
                                119, 5, -177669, 177668, -3, 3, true, true, 0.6f))
                        : Optional.empty());
        HighwayRoute route = planner.plan(new BlockPos(-177668, 120, 177668),
                new BlockPos(2274459, 64, 2629901),
                new HighwayPlanner.Options().horizontalDestination(true)).orElseThrow();

        HighwayRoute.BounceLeg first = assertInstanceOf(HighwayRoute.BounceLeg.class, route.legs.get(0));
        assertTrue(first.exitColumn().getX() <= -250000);
        assertTrue(first.exitColumn().getZ() >= 250000);
        assertTrue(route.legs.stream().filter(HighwayRoute.BounceLeg.class::isInstance).count() > 1);
    }

    @Test
    void destinationOnDiamondUsesItsOffsetRoad() {
        HighwayRoute route = unscanned.plan(new BlockPos(4000, 120, 6000),
                new BlockPos(6000, 120, 4000), new HighwayPlanner.Options()).orElseThrow();

        HighwayRoute.BounceLeg bounce = assertInstanceOf(HighwayRoute.BounceLeg.class, route.legs.get(0));
        assertEquals(HighwayCandidate.Category.DIAMOND, bounce.highway().category);
        assertEquals(10000, bounce.highway().ringOrDiamondDist);
        assertEquals(new BlockPos(6000, 120, 4000), bounce.exitColumn());
        assertTrue(bounce.highway().entry.getX() + bounce.highway().entry.getZ() == 10000);
    }

    @Test
    void playerAlreadyOnScannedLaneDoesNotWalkBackToScanAnchor() {
        HighwayPlanner planner = new HighwayPlanner((pos, axis) -> axis == HighwayCandidate.Axis.MINUS_X
                ? Optional.of(new HighwayDetectorBridge.ScanResult(119, 5, -800012, 0,
                        -3, 3, true, true, 1f)) : Optional.empty());
        HighwayRoute route = planner.plan(new BlockPos(-800025, 120, 0),
                new BlockPos(-900000, 120, 0), new HighwayPlanner.Options()).orElseThrow();
        assertInstanceOf(HighwayRoute.BounceLeg.class, route.legs.get(0));
    }

    @Test
    void flightsDisabledNeverBypassReentryWithFlight() {
        HighwayRoute route = unscanned.plan(new BlockPos(-833520, 32, -833330),
                new BlockPos(-872787, 64, -794058), new HighwayPlanner.Options().allowFlight(false)).orElseThrow();
        assertTrue(route.legs.stream().noneMatch(HighwayRoute.FlightLeg.class::isInstance));
    }

    @Test
    void nearProjectedHighwayButFarBelowItUsesGroundApproach() {
        HighwayRoute route = unscanned.plan(new BlockPos(8, 52, -15310),
                new BlockPos(59456, 64, -511430),
                new HighwayPlanner.Options().horizontalDestination(true)).orElseThrow();
        assertInstanceOf(HighwayRoute.ApproachLeg.class, route.legs.get(0));
    }

    @Test
    void unverifiedSpawnCrossingDoesNotFallBackToDirectFlight() {
        assertTrue(unscanned.plan(new BlockPos(-20000, 120, 0),
                new BlockPos(20000, 120, 0), new HighwayPlanner.Options()).isEmpty());
    }
}
