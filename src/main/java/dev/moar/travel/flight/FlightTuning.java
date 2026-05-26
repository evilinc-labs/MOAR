package dev.moar.travel.flight;

/** Tunable constants for manual elytra fallback flight. */
public final class FlightTuning {

    private FlightTuning() {}

    /** Ticks to hold forward so the player walks off the mining-exit ledge. */
    public static final int LAUNCH_WALK_TICKS          = 12;

    /** Abort if elytra activation takes too long. */
    public static final int LAUNCH_TIMEOUT_TICKS       = 140;

    /** Ticks between consecutive rocket fires during cruise. */
    public static final int ROCKET_COOLDOWN_TICKS      = 25;

    /**
     * Arrival XZ squared-distance threshold.
     * 25 blocks → 625 (generous, to compensate for nether-ceiling descent).
     */
    public static final double ARRIVAL_RADIUS_SQ       = 625.0;

    /**
     * Pitch during cruise (degrees, negative = nose slightly down).
     * A slight nose-down keeps speed up without diving too fast.
     */
    public static final float FALLBACK_PITCH           = -12f;

    /** Max yaw correction per tick (degrees). */
    public static final float MAX_YAW_STEP_DEG         = 15f;

    /** Yaw tolerance before correction kicks in (degrees). */
    public static final float ALIGN_TOLERANCE_DEG      = 5f;

    /** Ticks of no meaningful XZ progress before declaring stuck. */
    public static final int STUCK_TICKS                = 200;

    /** XZ progress evaluation window (ticks). */
    public static final int PROGRESS_CHECK_INTERVAL    = 40;

    /** Min XZ distance² per window to count as non-stuck progress. */
    public static final double MIN_PROGRESS_PER_INTERVAL_SQ = 25.0;
}
