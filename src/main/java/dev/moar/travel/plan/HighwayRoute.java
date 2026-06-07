package dev.moar.travel.plan;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.List;

// Ordered route consumed one leg at a time.
public final class HighwayRoute {

    public static final int SAFE_RING_RADIUS = 5_000;

    // Immutable route legs.
    public sealed interface Leg permits ApproachLeg, BounceLeg, TurnLeg, OffRampLeg, MineLeg, FlightLeg {}

    // Reach the chosen on-ramp.
    public record ApproachLeg(BlockPos onRamp) implements Leg {}

    // Bounce along the highway.
    public record BounceLeg(HighwayCandidate highway, BlockPos exitColumn,
                            int travelDx, int travelDz) implements Leg {}

    // Walk into the new branch before resuming bounce.
    public record TurnLeg(BlockPos branchTarget) implements Leg {}

    // Release bounce at the exit column.
    public record OffRampLeg(BlockPos handoffPoint) implements Leg {}

    // Walk or mine toward the takeoff point.
    public record MineLeg(BlockPos freeNetherTarget) implements Leg {}

    // Fly to the final destination.
    public record FlightLeg(BlockPos destination) implements Leg {}

    public final HighwayCandidate primary;
    public final List<Leg> legs;
    public final double estimatedCost;
    public final int travelDx;
    public final int travelDz;

    public HighwayRoute(HighwayCandidate primary, List<Leg> legs, double estimatedCost,
                        int travelDx, int travelDz) {
        this.primary = primary;
        this.legs = List.copyOf(legs);
        this.estimatedCost = estimatedCost;
        this.travelDx = travelDx;
        this.travelDz = travelDz;
    }

    @Override
    public String toString() {
        return "HighwayRoute{cost=" + String.format("%.1f", estimatedCost)
                + ", legs=" + legs.size() + ", primary=" + primary + "}";
    }
}
