package dev.moar.travel;

// Travel mission phases.
public enum TravelPhase {
    // No mission is active.
    IDLE,
    // Compute the route.
    PLANNING,
    // Walk to the highway entry.
    APPROACH_ONRAMP,
    // Bounce along the highway.
    BOUNCING,
    // Plan a detour around grief.
    VERIFYING_DETOUR,
    // Walk the detour segment.
    DETOURING,
    // Let movement settle before re-entering bounce.
    SETTLE,
    // Hand movement back to Baritone at the off-ramp.
    OFFRAMP_HANDOFF,
    // Mine or walk toward open nether.
    MINING_TO_FREENETHER,
    // Run the elytra resupply playbook.
    ELYTRA_RESUPPLY,
    // Launch the elytra.
    LAUNCH,
    // Let Baritone own elytra flight.
    ELYTRA_CRUISE,
    // Fly manually with rockets.
    ELYTRA_FALLBACK,
    // Mission finished.
    ARRIVED,
    // Mission aborted.
    ABORTED,
    // Mission paused by the user.
    PAUSED;

    // Keep milestone-gated phases grouped in one check.
    public boolean isReserved() {
        return this == BOUNCING || this == VERIFYING_DETOUR || this == DETOURING
                || this == LAUNCH || this == ELYTRA_CRUISE || this == ELYTRA_FALLBACK;
    }

    // Terminal phases fall back to IDLE automatically.
    public boolean isTerminal() {
        return this == ARRIVED || this == ABORTED;
    }
}
