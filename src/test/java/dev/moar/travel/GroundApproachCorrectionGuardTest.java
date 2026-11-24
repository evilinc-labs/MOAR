package dev.moar.travel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GroundApproachCorrectionGuardTest {
    @Test
    void repeatedCorrectionsAtOneBlockStopTheApproach() {
        GroundApproachCorrectionGuard guard = new GroundApproachCorrectionGuard();
        guard.start(747489.7, 1000000.5, 43);
        for (int corrections = 44; corrections < 48; corrections++) {
            assertFalse(guard.rejected(747490.2, 1000000.5, corrections));
        }
        assertTrue(guard.rejected(747489.8, 1000000.5, 48));
    }

    @Test
    void forwardProgressResetsTheCorrectionBudget() {
        GroundApproachCorrectionGuard guard = new GroundApproachCorrectionGuard();
        guard.start(100, 100, 10);
        assertFalse(guard.rejected(101, 100, 14));
        assertFalse(guard.rejected(104, 100, 14));
        assertFalse(guard.rejected(105, 100, 18));
        assertTrue(guard.rejected(105, 100, 19));
        guard.clear();
        assertFalse(guard.rejected(105, 100, 19));
    }
}
