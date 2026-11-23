package dev.moar.travel;

// Let bounce momentum decay before Baritone steers toward a ground waypoint.
final class DetourHandoffGate {
    static final int MAX_WAIT_TICKS = 40;
    private static final int REQUIRED_STABLE_TICKS = 3;
    private static final double MAX_HORIZONTAL_SPEED = 0.20;

    enum Result { WAIT, READY, TIMED_OUT }

    private int ticksWaited;
    private int stableTicks;

    void reset() {
        ticksWaited = 0;
        stableTicks = 0;
    }

    Result tick(boolean grounded, boolean gliding, double horizontalSpeed) {
        ticksWaited++;
        if (grounded && !gliding && horizontalSpeed <= MAX_HORIZONTAL_SPEED) {
            stableTicks++;
            if (stableTicks >= REQUIRED_STABLE_TICKS) return Result.READY;
        } else {
            stableTicks = 0;
        }
        return ticksWaited >= MAX_WAIT_TICKS ? Result.TIMED_OUT : Result.WAIT;
    }

    int ticksWaited() { return ticksWaited; }
}
