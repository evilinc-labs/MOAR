package dev.moar.travel;

import dev.moar.travel.plan.HighwayRoute;

// Mutable state for the active mission.
public final class TravelState {

    // Current phase.
    public TravelPhase phase = TravelPhase.IDLE;

    // Current movement owner.
    public MovementOwner owner = MovementOwner.NONE;

    // Active mission, or null when idle.
    public TravelMission mission;

    // Planned route, or null before planning finishes.
    public HighwayRoute route;

    // Ticks since the last phase change.
    public int ticksInPhase;

    // Ticks since the mission started.
    public int missionTicks;

    // Last transition reason.
    public String lastTransitionReason = "";

    // Phase to restore when unpausing.
    public TravelPhase pausedFromPhase = TravelPhase.IDLE;

    // Last abort reason.
    public String abortReason = "";

    // Reset to a clean idle state.
    public void reset() {
        phase = TravelPhase.IDLE;
        owner = MovementOwner.NONE;
        mission = null;
        route = null;
        ticksInPhase = 0;
        missionTicks = 0;
        lastTransitionReason = "";
        pausedFromPhase = TravelPhase.IDLE;
        abortReason = "";
    }
}
