package dev.moar.travel.bounce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BounceGroundHandoffTest {
    @Test
    void ordinaryTravelReboundsImmediately() {
        BounceGroundHandoff handoff = new BounceGroundHandoff();
        handoff.reset(0);

        assertFalse(handoff.afterTouchdown(10));
        assertFalse(handoff.waitBeforeGroundJump());
        assertEquals(0, handoff.extraGroundTicks());
    }

    @Test
    void reboundCorrectionsAddAtMostThreeGroundTicks() {
        BounceGroundHandoff handoff = new BounceGroundHandoff();
        handoff.reset(0);

        assertFalse(handoff.onCorrection(false));
        assertEquals(0, handoff.extraGroundTicks());
        assertTrue(handoff.onCorrection(true));
        assertEquals(1, handoff.extraGroundTicks());
        assertTrue(handoff.afterTouchdown(10));
        assertFalse(handoff.waitBeforeGroundJump());

        assertTrue(handoff.onCorrection(true));
        assertEquals(2, handoff.extraGroundTicks());
        assertTrue(handoff.afterTouchdown(20));
        assertTrue(handoff.waitBeforeGroundJump());
        assertFalse(handoff.waitBeforeGroundJump());

        assertTrue(handoff.onCorrection(true));
        assertFalse(handoff.onCorrection(true));
        assertEquals(3, handoff.extraGroundTicks());
    }

    @Test
    void recentCorrectionsStartConservativelyThenStableBouncesRelaxDelay() {
        BounceGroundHandoff handoff = new BounceGroundHandoff();
        handoff.reset(4);
        assertEquals(1, handoff.extraGroundTicks());

        for (int i = 0; i < 7; i++) {
            assertTrue(handoff.afterTouchdown(i));
        }
        assertFalse(handoff.afterTouchdown(7));
        assertEquals(0, handoff.extraGroundTicks());
    }

    @Test
    void reboundCorrectionIsRecognizedAfterPhaseReturnsToGrounded() {
        BounceGroundHandoff handoff = new BounceGroundHandoff();
        handoff.reset(0);

        assertFalse(handoff.reboundAttemptedRecently(10));
        handoff.afterTouchdown(10);
        assertFalse(handoff.reboundAttemptedRecently(12));
        handoff.onGroundJumpRequested(12);
        assertTrue(handoff.reboundAttemptedRecently(15));
        assertTrue(handoff.onCorrection(handoff.reboundAttemptedRecently(15)));
        assertEquals(1, handoff.extraGroundTicks());

        assertFalse(handoff.reboundAttemptedRecently(17));
        handoff.afterTouchdown(20);
        assertFalse(handoff.reboundAttemptedRecently(21));
    }
}
