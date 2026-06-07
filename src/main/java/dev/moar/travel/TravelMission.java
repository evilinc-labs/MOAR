package dev.moar.travel;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.concurrent.atomic.AtomicLong;

// Immutable travel request.
public final class TravelMission {

    private static final AtomicLong ID_GEN = new AtomicLong(1);

    // Final destination.
    public final BlockPos destination;

    // Allow free-nether flight.
    public final boolean useElytra;

    // Allow detours around griefed highway sections.
    public final boolean allowDetour;

    // Retry automatically after non-user aborts.
    public final boolean autoResume;

    // Prefer flight when the off-ramp is at least this far from the goal.
    public final int freeNetherFlightThreshold;

    // Server-supplied highway floor Y, or Integer.MIN_VALUE when unknown.
    public final int expectedHighwayFloorY;

    // Stable ID for logs and telemetry.
    public final long id;

    private TravelMission(Builder b) {
        this.destination = b.destination;
        this.useElytra = b.useElytra;
        this.allowDetour = b.allowDetour;
        this.autoResume = b.autoResume;
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
                + ", autoResume=" + autoResume
                + ", flightThreshold=" + freeNetherFlightThreshold + "}";
    }

    public static final class Builder {
        private final BlockPos destination;
        private boolean useElytra = true;
        private boolean allowDetour = true;
        private boolean autoResume = true;
        private int freeNetherFlightThreshold = 1500;
        private int expectedHighwayFloorY = Integer.MIN_VALUE;

        private Builder(BlockPos destination) {
            this.destination = destination;
        }

        public Builder useElytra(boolean v)              { this.useElytra = v; return this; }
        public Builder allowDetour(boolean v)            { this.allowDetour = v; return this; }
        public Builder autoResume(boolean v)             { this.autoResume = v; return this; }
        public Builder freeNetherFlightThreshold(int v)  { this.freeNetherFlightThreshold = v; return this; }
        public Builder expectedHighwayFloorY(int v)      { this.expectedHighwayFloorY = v; return this; }

        public TravelMission build() {
            return new TravelMission(this);
        }
    }
}
