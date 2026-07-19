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

    // Require a persistent blocked corridor before detouring.
    public static int WALL_CONFIRM_TICKS = 3;

    // Leave enough stopping distance at full bounce speed.
    public static int OBSTACLE_SCAN_AHEAD = 10;

    // Activate only after the jump apex.
    public static double ELYTRA_ACTIVATE_VY_THRESHOLD = -0.04;

    // Flatten the arc before the player's head reaches a low ceiling.
    public static double ELYTRA_ACTIVATE_MAX_RISE = 0.35;

    // Retry if vanilla does not accept the jump input.
    public static int GROUND_JUMP_TIMEOUT_TICKS = 3;

    // Stop waiting for a rejected launch before landing.
    public static int LAUNCH_ACK_TIMEOUT_TICKS = 8;

    // Fall back after repeated correction episodes.
    public static int CORRECTIONS_DISABLE_ELYTRA = 2;

    // Forget isolated correction episodes after this window.
    public static int CORRECTION_STORM_WINDOW_TICKS = 400;

    // Fall back to plain sprint after persistent episodes.
    public static int CORRECTIONS_DISABLE_JUMP = 4;

    // Keep launch acknowledgement on the proven posture.
    public static float LAUNCH_PITCH = 68.0f;

    // Stabilize speed after reaching the target.
    public static float GLIDE_CRUISE_PITCH = 60.0f;

    // Accelerate only after the server accepts gliding.
    public static float GLIDE_ACCEL_PITCH = 52.0f;

    // Extend low-speed glides to build horizontal momentum.
    public static float GLIDE_ACCEL_LOW_SPEED_PITCH = 44.0f;
    public static float GLIDE_ACCEL_MID_SPEED_PITCH = 48.0f;
    public static double ACCEL_LOW_SPEED_THRESHOLD = 0.75;
    public static double ACCEL_MID_SPEED_THRESHOLD = 1.35;

    // Accelerate until reaching 40 blocks per second.
    public static double TARGET_HORIZONTAL_SPEED = 2.0;

    // Stabilize several bounces after a server correction.
    public static int CORRECTION_RECOVERY_BOUNCES = 8;

    // Check the roof above the player's full bounce height.
    public static int HEADROOM_Y_OFFSET = 4;

    // Observe immediate headroom once per bounce.
    public static int HEADROOM_SCAN_AHEAD = 2;

}
