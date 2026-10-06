package dev.moar.travel.bounce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BounceCorrectionRetryTest {
    @Test
    void correctionAfterAcceptedRetryLocksFlightForTheLeg() {
        BounceCorrectionRetry retry = new BounceCorrectionRetry();
        retry.arm(4);
        assertFalse(retry.correctionObserved(5)); // Sprint-jump during rearm.
        retry.flightAccepted(5);
        assertFalse(retry.correctionObserved(5));
        assertTrue(retry.correctionObserved(6));
        assertTrue(retry.isLocked());

        retry.arm(6);
        retry.flightAccepted(6);
        assertFalse(retry.correctionObserved(7));
        assertTrue(retry.isLocked());
    }

    @Test
    void newHighwayLegCanTryFlightAgain() {
        BounceCorrectionRetry retry = new BounceCorrectionRetry();
        retry.arm(4);
        retry.flightAccepted(4);
        assertTrue(retry.correctionObserved(5));
        retry.reset();
        assertFalse(retry.isLocked());
        retry.arm(5);
        retry.flightAccepted(5);
        assertTrue(retry.correctionObserved(6));
    }
}
