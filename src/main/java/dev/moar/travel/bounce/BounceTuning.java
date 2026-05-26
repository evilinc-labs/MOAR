package dev.moar.travel.bounce;

/** Tunable constants for highway bounce travel. */
public final class BounceTuning {

    private BounceTuning() {}

    /** Abort when the player drops this far below the highway floor. */
    public static int FALL_Y_THRESHOLD = 5;

    /** Abort after this many ticks without meaningful progress. */
    public static int STUCK_TICKS = 100;

    /** Compare position once per progress window. */
    public static int PROGRESS_CHECK_INTERVAL = 20;

    /** Require at least 2 blocks of XZ progress per window. */
    public static double MIN_PROGRESS_PER_INTERVAL_SQ = 4.0;

    /** Skip yaw correction when already close enough. */
    public static float ALIGN_TOLERANCE_DEG = 8.0f;

    /** Cap yaw correction to avoid sharp server-visible snaps. */
    public static float MAX_YAW_STEP_DEG = 20.0f;
}
