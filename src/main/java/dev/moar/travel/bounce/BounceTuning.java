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

    // Leave enough landing distance at full bounce speed.
    public static int OBSTACLE_SCAN_AHEAD = 24;

    // Activate only after the jump apex.
    public static double ELYTRA_ACTIVATE_VY_THRESHOLD = -0.04;

    // Flatten the arc before the player's head reaches a low ceiling.
    public static double ELYTRA_ACTIVATE_MAX_RISE = 0.35;

    // Retry if vanilla does not accept the jump input.
    public static int GROUND_JUMP_TIMEOUT_TICKS = 3;

    // Stop waiting for a rejected launch before landing.
    public static int LAUNCH_ACK_TIMEOUT_TICKS = 8;

    // Confirm glide before advancing the launch phase.
    public static int LAUNCH_CONFIRM_TICKS = 3;

    // Retry one rejected launch after its key edge has cleared.
    public static int LAUNCH_RETRY_AFTER_TICKS = 4;
    public static int LAUNCH_ATTEMPTS_PER_JUMP = 2;

    // Fall back after repeated correction episodes.
    public static int CORRECTIONS_DISABLE_ELYTRA = 4;

    // Forget isolated correction episodes after this window.
    public static int CORRECTION_STORM_WINDOW_TICKS = 400;

    // Fall back to plain sprint after persistent episodes.
    public static int CORRECTIONS_DISABLE_JUMP = 6;

    // Keep launch acknowledgement on the proven posture.
    public static float LAUNCH_PITCH = 68.0f;

    // Taper launch pitch as horizontal speed rises.
    public static float LAUNCH_ACCEL_LOW_SPEED_PITCH = 68.0f;
    public static float LAUNCH_ACCEL_MID_SPEED_PITCH = 68.0f;

    // Stabilize speed after reaching the target.
    public static float GLIDE_CRUISE_PITCH = 60.0f;

    // Accelerate only after the server accepts gliding.
    public static float GLIDE_ACCEL_PITCH = 40.0f;

    // Hold the stable glide angle throughout acceleration.
    public static float GLIDE_ACCEL_LOW_SPEED_PITCH = 40.0f;
    public static float GLIDE_ACCEL_MID_SPEED_PITCH = 40.0f;
    public static float GLIDE_ACCEL_DIVE_PITCH = 56.0f;
    public static double GLIDE_ACCEL_DIVE_MIN_RISE = 0.10;
    public static double GLIDE_ACCEL_DIVE_MAX_ASCENT_VELOCITY = 0.04;
    public static float GLIDE_ACCEL_DIVE_MIN_PITCH = 50.0f;
    public static float GLIDE_ACCEL_DIVE_MAX_PITCH = 72.0f;
    public static float GLIDE_ACCEL_DIVE_MAX_DOWN_STEP = 1.25f;
    public static float GLIDE_ACCEL_DIVE_MAX_UP_STEP = 0.50f;
    public static float GLIDE_ACCEL_APEX_MAX_PITCH_STEP = 0.35f;
    public static double GLIDE_ACCEL_APEX_VELOCITY_BAND = 0.08;
    public static double GLIDE_ACCEL_VERTICAL_ACCEL_FILTER = 0.25;
    public static double GLIDE_ACCEL_VERTICAL_ACCEL_LIMIT = 0.10;
    public static double GLIDE_ACCEL_LANDING_TICKS = 5.0;
    public static double GLIDE_ACCEL_PITCH_GAIN = 120.0;
    public static double GLIDE_ACCEL_FLIGHT_FRAME_FILTER = 0.25;
    public static double GLIDE_ACCEL_FLIGHT_FRAME_SAMPLE_LIMIT = 0.10;
    public static double GLIDE_ACCEL_TANGENTIAL_DECEL_THRESHOLD = -0.01;
    public static int GLIDE_ACCEL_TANGENTIAL_DECEL_TICKS = 2;
    public static double GLIDE_ACCEL_TANGENTIAL_LOSS_DEADZONE = 0.005;
    public static double GLIDE_ACCEL_TANGENTIAL_LOSS_GAIN = 80.0;
    public static float GLIDE_ACCEL_SPEED_COMPENSATION_MAX = 3.0f;
    public static float GLIDE_ACCEL_TARGET_SPEED_BIAS_MAX = 5.0f;
    public static double ACCEL_LOW_SPEED_THRESHOLD = 1.25;
    public static double ACCEL_MID_SPEED_THRESHOLD = 1.70;

    // Accelerate until reaching 80 blocks per second.
    public static double TARGET_HORIZONTAL_SPEED = 4.0;
    public static double TARGET_HORIZONTAL_SPEED_HYSTERESIS = 0.15;

    // Stabilize several bounces after a server correction.
    public static int CORRECTION_RECOVERY_BOUNCES = 8;

}
