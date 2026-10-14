package dev.moar.travel;

import dev.moar.travel.plan.HighwayCandidate;
import dev.moar.travel.plan.HighwayRoute;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

// Only skip a failed ingress flight when its next leg is a nearby highway.
final class HighwayIngressRecovery {
    private static final int MAX_HORIZONTAL_DISTANCE = 32;
    private static final int MAX_ASCENT = 96;

    private HighwayIngressRecovery() {}

    static int nearbyFlightLegIndex(HighwayRoute route, int completedLegIndex,
                                   BlockPos position, BlockPos flightDestination) {
        if (route == null || position == null || flightDestination == null) return -1;
        int flightIndex = completedLegIndex + 1;
        if (flightIndex < 0 || flightIndex + 1 >= route.legs.size()
                || !(route.legs.get(flightIndex) instanceof HighwayRoute.FlightLeg flight)
                || !flight.destination().equals(flightDestination)
                || !(route.legs.get(flightIndex + 1) instanceof HighwayRoute.BounceLeg bounce)) {
            return -1;
        }

        HighwayCandidate highway = bounce.highway();
        if (highway == null || highway.entry == null || highway.floorY == Integer.MIN_VALUE) {
            return -1;
        }
        int ascent = highway.floorY - position.getY();
        int horizontal = Math.max(
                Math.abs(highway.entry.getX() - position.getX()),
                Math.abs(highway.entry.getZ() - position.getZ()));
        return ascent >= 3 && ascent <= MAX_ASCENT
                && horizontal <= MAX_HORIZONTAL_DISTANCE ? flightIndex : -1;
    }
}
