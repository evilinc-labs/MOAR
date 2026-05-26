package dev.moar.travel;

import dev.moar.travel.plan.HighwayRoute;

/** Mutable runtime state for the active travel mission. */
public final class TravelState {

    /** Current phase. IDLE means no active mission. */
    public TravelPhase phase = TravelPhase.IDLE;

    /** Who currently owns movement. */
    public MovementOwner owner = MovementOwner.NONE;

    /** Active mission, or null when IDLE. */
    public TravelMission mission;

    /** Computed route, or null if planning hasn't completed. */
    public HighwayRoute route;

    /** Client ticks since the last phase transition. */
    public int ticksInPhase;

    /** Total client ticks since the mission started. */
    public int missionTicks;

    /** Last transition reason — for telemetry & logs. */
    public String lastTransitionReason = "";

    /** When PAUSED, the phase to resume to. */
    public TravelPhase pausedFromPhase = TravelPhase.IDLE;

    /** Last abort reason, set before ABORTED. */
    public String abortReason = "";

    /** Reset everything to a fresh IDLE state. */
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
