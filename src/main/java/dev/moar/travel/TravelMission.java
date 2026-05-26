package dev.moar.travel;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.concurrent.atomic.AtomicLong;

/** Immutable description of a travel job. */
public final class TravelMission {

    private static final AtomicLong ID_GEN = new AtomicLong(1);

    /** Final destination. */
    public final BlockPos destination;

    /** If true, the free-nether flight phase is allowed. */
    public final boolean useElytra;

    /** If true, detour planning is allowed when grief is detected on the highway. */
    public final boolean allowDetour;

    /**
     * Distance (in blocks, XZ) from the off-ramp at which a destination is
     * considered "far enough" to justify launching into free-nether flight
     * rather than walking.
     */
    public final int freeNetherFlightThreshold;

    /** Server-supplied highway floor Y. Integer.MIN_VALUE means no hint. */
    public final int expectedHighwayFloorY;

    /** Unique identifier for telemetry correlation. */
    public final long id;

    private TravelMission(Builder b) {
        this.destination = b.destination;
        this.useElytra = b.useElytra;
        this.allowDetour = b.allowDetour;
        this.freeNetherFlightThreshold = b.freeNetherFlightThreshold;
        this.expectedHighwayFloorY = b.expectedHighwayFloorY;
        this.id = ID_GEN.getAndIncrement();
    }

    public static Builder to(BlockPos destination) {
        return new Builder(destination);
    }

    @Override
    public String toString() {
        return "TravelMission#" + id + "{dest=" + destination.toShortString()
                + ", elytra=" + useElytra + ", detour=" + allowDetour
                + ", flightThreshold=" + freeNetherFlightThreshold + "}";
    }

    public static final class Builder {
        private final BlockPos destination;
        private boolean useElytra = true;
        private boolean allowDetour = true;
        private int freeNetherFlightThreshold = 1500;
        private int expectedHighwayFloorY = Integer.MIN_VALUE;

        private Builder(BlockPos destination) {
            this.destination = destination;
        }

        public Builder useElytra(boolean v)              { this.useElytra = v; return this; }
        public Builder allowDetour(boolean v)            { this.allowDetour = v; return this; }
        public Builder freeNetherFlightThreshold(int v)  { this.freeNetherFlightThreshold = v; return this; }
        public Builder expectedHighwayFloorY(int v)      { this.expectedHighwayFloorY = v; return this; }

        public TravelMission build() {
            return new TravelMission(this);
        }
    }
}
