package dev.moar.travel.bounce;

/** Tunable constants for highway bounce travel. */
public final class BounceTuning {

    private BounceTuning() {}

    /** Abort when the player drops this far below the highway floor.
     *  On 2b2t the lava sea sits ≈2 blocks below the highway floor (Y≈9 vs Y≈11),
     *  so this must be smaller than that gap to fire before the player enters lava. */
    public static int FALL_Y_THRESHOLD = 2;

    /** Abort after this many ticks without meaningful progress. */
    public static int STUCK_TICKS = 100;

    /** Compare position once per progress window. */
    public static int PROGRESS_CHECK_INTERVAL = 20;

    /** Require at least 2 blocks of XZ progress per window. */
    public static double MIN_PROGRESS_PER_INTERVAL_SQ = 4.0;

    /** Skip yaw correction when already close enough. */
    public static float ALIGN_TOLERANCE_DEG = 1.0f;

    /** Cap yaw correction to avoid sharp server-visible snaps. */
    public static float MAX_YAW_STEP_DEG = 20.0f;

    /** Perpendicular drift correction gain (travel-direction units per block of offset). */
    public static double PERP_CORRECTION_GAIN = 0.15;

    /** Ignore perpendicular offsets smaller than this (blocks). */
    public static double PERP_CORRECTION_DEADZONE = 0.15;

    /** Activate elytra when upward velocity drops to this value (m/tick). */
    public static double ELYTRA_ACTIVATE_VY_THRESHOLD = 0.1;

    /** Glide pitch in degrees. Positive = looking down. 75 = aggressive, 40–55 = sweet-spot. */
    public static float BOUNCE_PITCH = 75.0f;
}
