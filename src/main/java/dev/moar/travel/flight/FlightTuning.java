package dev.moar.travel.flight;

// Tuning for manual elytra fallback flight.
public final class FlightTuning {

    private FlightTuning() {}

    // Hold forward long enough to step off the ledge.
    public static final int LAUNCH_WALK_TICKS          = 12;

    // Abort slow launches.
    public static final int LAUNCH_TIMEOUT_TICKS       = 140;

    // Space out rocket boosts.
    public static final int ROCKET_COOLDOWN_TICKS      = 25;

    // Arrival threshold in XZ distance squared.
    public static final double ARRIVAL_RADIUS_SQ       = 625.0;

    // Cruise pitch. Slightly nose-down keeps speed up.
    public static final float FALLBACK_PITCH           = -12f;

    // Cap yaw correction per tick.
    public static final float MAX_YAW_STEP_DEG         = 15f;

    // Ignore small yaw error.
    public static final float ALIGN_TOLERANCE_DEG      = 5f;

    // Declare stuck after this many flat ticks.
    public static final int STUCK_TICKS                = 200;

    // Check progress on this interval.
    public static final int PROGRESS_CHECK_INTERVAL    = 40;

    // Minimum XZ progress per interval.
    public static final double MIN_PROGRESS_PER_INTERVAL_SQ = 25.0;
}
