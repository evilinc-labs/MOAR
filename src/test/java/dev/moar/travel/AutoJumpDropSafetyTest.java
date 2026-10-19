package dev.moar.travel;

import org.junit.jupiter.api.Test;

import static dev.moar.travel.AutoJumpDropSafety.Slice.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoJumpDropSafetyTest {
    @Test
    void lavaBelowAnOpenLaunchDropIsNotSafe() {
        assertFalse(AutoJumpDropSafety.isClear(depth -> depth == 40 ? HAZARD : AIR, 8, 48));
    }

    @Test
    void solidCatchAfterEnoughClearAirIsSafe() {
        assertTrue(AutoJumpDropSafety.isClear(depth -> depth == 12 ? SAFE_FLOOR : AIR, 8, 48));
    }

    @Test
    void shallowOrUnverifiedDropsAreNotSafe() {
        assertFalse(AutoJumpDropSafety.isClear(depth -> depth == 8 ? SAFE_FLOOR : AIR, 8, 48));
        assertFalse(AutoJumpDropSafety.isClear(depth -> depth == 20 ? UNKNOWN : AIR, 8, 48));
    }

    @Test
    void tallOpenDropCanLaunchWithoutAVisibleFloor() {
        assertTrue(AutoJumpDropSafety.isClear(depth -> AIR, 8, 48));
    }
}
