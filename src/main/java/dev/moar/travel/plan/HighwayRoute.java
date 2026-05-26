package dev.moar.travel.plan;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.List;

/** Ordered plan consumed one leg at a time by TravelManager. */
public final class HighwayRoute {

    /** Route legs are immutable and ordered. */
    public sealed interface Leg permits ApproachLeg, BounceLeg, OffRampLeg, MineLeg, FlightLeg {}

    /** Walk/mine to the chosen on-ramp. */
    public record ApproachLeg(BlockPos onRamp) implements Leg {}

    /** Bounce along the highway in the explicit travel direction. */
    public record BounceLeg(HighwayCandidate highway, BlockPos exitColumn,
                            int travelDx, int travelDz) implements Leg {}

    /** Release bounce ownership at the exit column. */
    public record OffRampLeg(BlockPos handoffPoint) implements Leg {}

    /** Baritone walk/mine off the highway toward the takeoff point. */
    public record MineLeg(BlockPos freeNetherTarget) implements Leg {}

    /** Free-nether elytra flight to the final destination. */
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
