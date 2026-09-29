package dev.moar.travel.bounce;

// Learn whether this connection accepts the first flight request at the usual launch point.
final class BounceLaunchTiming {
    enum Change { NONE, DELAYED, EARLY }

    private static final int RETRIES_TO_ADAPT = 3;
    private static final int FAILED_DELAYED_LAUNCHES = 3;
    private static final int RETRY_COOLDOWN_BOUNCES = 40;

    private boolean delayed;
    private int earlyRetries;
    private int delayedRetries;
    private int cooldownBounces;

    int firstRequestAirTicks() {
        return BounceTuning.LAUNCH_MIN_AIRBORNE_TICKS + (delayed ? 1 : 0);
    }

    Change observeTouchdown(int attempts, int firstRequestAirTicks) {
        if (delayed) {
            if (attempts <= 1) {
                delayedRetries = 0;
            } else if (++delayedRetries >= FAILED_DELAYED_LAUNCHES) {
                delayed = false;
                earlyRetries = 0;
                delayedRetries = 0;
                cooldownBounces = RETRY_COOLDOWN_BOUNCES;
                return Change.EARLY;
            }
            return Change.NONE;
        }
        if (cooldownBounces > 0) {
            cooldownBounces--;
            return Change.NONE;
        }
        if (attempts > 1 && firstRequestAirTicks == BounceTuning.LAUNCH_MIN_AIRBORNE_TICKS) {
            if (++earlyRetries >= RETRIES_TO_ADAPT) {
                delayed = true;
                earlyRetries = 0;
                return Change.DELAYED;
            }
        } else {
            earlyRetries = 0;
        }
        return Change.NONE;
    }

    void reset() {
        delayed = false;
        earlyRetries = 0;
        delayedRetries = 0;
        cooldownBounces = 0;
    }
}
