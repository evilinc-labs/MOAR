package dev.moar.travel.bounce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BounceLaunchTimingTest {
    @Test
    void repeatedEarlyRetriesMoveFirstRequestOneTickLater() {
        BounceLaunchTiming timing = new BounceLaunchTiming();
        assertEquals(2, timing.firstRequestAirTicks());
        assertEquals(BounceLaunchTiming.Change.NONE, timing.observeTouchdown(2, 2));
        assertEquals(BounceLaunchTiming.Change.NONE, timing.observeTouchdown(2, 2));
        assertEquals(BounceLaunchTiming.Change.DELAYED, timing.observeTouchdown(2, 2));
        assertEquals(3, timing.firstRequestAirTicks());
        assertEquals(BounceLaunchTiming.Change.NONE, timing.observeTouchdown(1, 3));
        assertEquals(3, timing.firstRequestAirTicks());
    }

    @Test
    void delayedRetriesReturnToEarlyLaunchWithCooldown() {
        BounceLaunchTiming timing = new BounceLaunchTiming();
        for (int i = 0; i < 3; i++) timing.observeTouchdown(2, 2);
        for (int i = 0; i < 2; i++) {
            assertEquals(BounceLaunchTiming.Change.NONE, timing.observeTouchdown(2, 3));
        }
        assertEquals(BounceLaunchTiming.Change.EARLY, timing.observeTouchdown(2, 3));
        assertEquals(2, timing.firstRequestAirTicks());
        for (int i = 0; i < 40; i++) {
            assertEquals(BounceLaunchTiming.Change.NONE, timing.observeTouchdown(2, 2));
        }
        assertEquals(2, timing.firstRequestAirTicks());
        for (int i = 0; i < 3; i++) timing.observeTouchdown(2, 2);
        assertEquals(3, timing.firstRequestAirTicks());
    }

    @Test
    void isolatedRetriesAndCorrectionsDoNotChangeTiming() {
        BounceLaunchTiming timing = new BounceLaunchTiming();
        timing.observeTouchdown(2, 2);
        timing.observeTouchdown(1, 2);
        timing.observeTouchdown(2, 2);
        assertEquals(2, timing.firstRequestAirTicks());
        timing.observeTouchdown(2, 2);
        timing.observeTouchdown(2, 2);
        assertEquals(3, timing.firstRequestAirTicks());
        timing.reset();
        assertEquals(2, timing.firstRequestAirTicks());
    }
}
