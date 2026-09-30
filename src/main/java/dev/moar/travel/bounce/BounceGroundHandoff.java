package dev.moar.travel.bounce;

// Give a lagging server time to observe touchdown before the next flight launch.
final class BounceGroundHandoff {
    private static final int MAX_EXTRA_GROUND_TICKS = 3;
    private static final int STABLE_BOUNCES_TO_RELAX = 8;
    private static final int REBOUND_CORRECTION_WINDOW_TICKS = 6;

    private int extraGroundTicks;
    private int pendingGroundTicks;
    private int stableBounces;
    private int lastTouchdownTick;
    private int lastGroundJumpTick;

    void reset(int recentCorrectionEpisodes) {
        extraGroundTicks = recentCorrectionEpisodes >= 4 ? 1 : 0;
        pendingGroundTicks = 0;
        stableBounces = 0;
        lastTouchdownTick = Integer.MIN_VALUE;
        lastGroundJumpTick = Integer.MIN_VALUE;
    }

    int extraGroundTicks() {
        return extraGroundTicks;
    }

    boolean afterTouchdown(int tick) {
        lastTouchdownTick = tick;
        lastGroundJumpTick = Integer.MIN_VALUE;
        if (extraGroundTicks > 0 && ++stableBounces >= STABLE_BOUNCES_TO_RELAX) {
            extraGroundTicks--;
            stableBounces = 0;
        }
        pendingGroundTicks = Math.max(0, extraGroundTicks - 1);
        return extraGroundTicks > 0;
    }

    void onGroundJumpRequested(int tick) {
        lastGroundJumpTick = tick;
    }

    boolean reboundAttemptedRecently(int tick) {
        return lastTouchdownTick != Integer.MIN_VALUE
                && lastGroundJumpTick >= lastTouchdownTick
                && tick - lastTouchdownTick <= REBOUND_CORRECTION_WINDOW_TICKS;
    }

    boolean waitBeforeGroundJump() {
        if (pendingGroundTicks <= 0) return false;
        pendingGroundTicks--;
        return true;
    }

    boolean onCorrection(boolean afterRebound) {
        pendingGroundTicks = 0;
        if (!afterRebound) return false;
        stableBounces = 0;
        int previous = extraGroundTicks;
        extraGroundTicks = Math.min(MAX_EXTRA_GROUND_TICKS, extraGroundTicks + 1);
        return extraGroundTicks != previous;
    }
}
