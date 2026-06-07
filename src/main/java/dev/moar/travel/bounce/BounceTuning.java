package dev.moar.travel.bounce;

// Tuning for highway bounce travel.
public final class BounceTuning {

    private BounceTuning() {}

    // Abort before a fall reaches lava.
    public static int FALL_Y_THRESHOLD = 2;

    // Abort when progress stalls.
    public static int STUCK_TICKS = 100;

    // Check progress on this interval.
    public static int PROGRESS_CHECK_INTERVAL = 20;

    // Minimum XZ progress per interval.
    public static double MIN_PROGRESS_PER_INTERVAL_SQ = 4.0;

    // Ignore small yaw error.
    public static float ALIGN_TOLERANCE_DEG = 1.0f;

    // Cap yaw correction per tick.
    public static float MAX_YAW_STEP_DEG = 20.0f;

    // Pull drift back toward center.
    public static double PERP_CORRECTION_GAIN = 0.15;

    // Ignore tiny sideways drift.
    public static double PERP_CORRECTION_DEADZONE = 0.15;

    // Activate elytra near jump apex.
    public static double ELYTRA_ACTIVATE_VY_THRESHOLD = 0.1;

    // Aggressive downward glide pitch.
    public static float BOUNCE_PITCH = 75.0f;
}
