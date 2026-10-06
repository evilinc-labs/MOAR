package dev.moar.travel;

import dev.moar.travel.plan.HighwayCandidate;
import dev.moar.travel.plan.HighwayRoute;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HighwayIngressRecoveryTest {
    private static final BlockPos ENTRY = new BlockPos(0, 120, -15313);
    private static final BlockPos EXIT = new BlockPos(0, 120, -50000);
    private static final HighwayCandidate HIGHWAY = new HighwayCandidate(
            HighwayCandidate.Axis.MINUS_Z, HighwayCandidate.Category.CARDINAL,
            120, ENTRY, EXIT, 0.05f);

    private static HighwayRoute ingressRoute() {
        return new HighwayRoute(HIGHWAY, List.of(
                new HighwayRoute.FlightLeg(ENTRY),
                new HighwayRoute.BounceLeg(HIGHWAY, EXIT, 0, -1)), 100, 0, -1);
    }

    @Test
    void groundedArrivalNearRoadCanReplaceFailedIngressFlightWithAscent() {
        assertEquals(0, HighwayIngressRecovery.nearbyFlightLegIndex(
                ingressRoute(), -1, new BlockPos(8, 52, -15310), ENTRY));
    }

    @Test
    void distantOrUnrelatedFlightDoesNotSkipItsDestination() {
        HighwayRoute route = ingressRoute();
        assertEquals(-1, HighwayIngressRecovery.nearbyFlightLegIndex(
                route, -1, new BlockPos(50, 52, -15310), ENTRY));
        assertEquals(-1, HighwayIngressRecovery.nearbyFlightLegIndex(
                route, -1, new BlockPos(8, 10, -15310), ENTRY));
        assertEquals(-1, HighwayIngressRecovery.nearbyFlightLegIndex(
                route, -1, new BlockPos(8, 52, -15310), EXIT));
        assertEquals(-1, HighwayIngressRecovery.nearbyFlightLegIndex(
                route, 0, new BlockPos(8, 52, -15310), ENTRY));
    }
}
