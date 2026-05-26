package dev.moar.travel;

/** Phases of one travel mission. */
public enum TravelPhase {
    /** No mission active. */
    IDLE,
    /** HighwayPlanner is computing a route. */
    PLANNING,
    /** Baritone is walking the player to the chosen highway entry point. */
    APPROACH_ONRAMP,
    /** Reserved for milestone 3. Highway bounce-flight. */
    BOUNCING,
    /** Reserved for milestone 4. Detour planner is running after grief detection. */
    VERIFYING_DETOUR,
    /** Reserved for milestone 4. Baritone bridges around a griefed section. */
    DETOURING,
    /** Transitional. Releases bounce ownership and hands off to Baritone. */
    OFFRAMP_HANDOFF,
    /** Baritone walks/mines from the off-ramp to a free-nether takeoff point. */
    MINING_TO_FREENETHER,
    /** Reserved for milestone 5a. Equip, jump, START_FALL_FLYING, initial boost. */
    LAUNCH,
    /** Reserved for milestone 5a. Baritone elytra owns movement. */
    ELYTRA_CRUISE,
    /** Manual heading and rocket flight. */
    ELYTRA_FALLBACK,
    /** Mission finished successfully. */
    ARRIVED,
    /** Mission aborted (user stop, planner failure, stuck, etc.). */
    ABORTED,
    /** User-requested pause. */
    PAUSED;

    /** True for phases that are reserved for later milestones. */
    public boolean isReserved() {
        return this == BOUNCING || this == VERIFYING_DETOUR || this == DETOURING
                || this == LAUNCH || this == ELYTRA_CRUISE || this == ELYTRA_FALLBACK;
    }

    /** True for terminal phases that automatically transition back to IDLE. */
    public boolean isTerminal() {
        return this == ARRIVED || this == ABORTED;
    }
}
