package dev.moar.travel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DetourHandoffGateTest {
    @Test
    void waitsForGroundContactAndThreeSlowTicks() {
        DetourHandoffGate gate = new DetourHandoffGate();
        gate.reset();
        assertEquals(DetourHandoffGate.Result.WAIT, gate.tick(false, true, 1.98));
        assertEquals(DetourHandoffGate.Result.WAIT, gate.tick(true, true, 0.9));
        assertEquals(DetourHandoffGate.Result.WAIT, gate.tick(true, false, 0.19));
        assertEquals(DetourHandoffGate.Result.WAIT, gate.tick(true, false, 0.21));
        assertEquals(DetourHandoffGate.Result.WAIT, gate.tick(true, false, 0.10));
        assertEquals(DetourHandoffGate.Result.WAIT, gate.tick(true, false, 0.05));
        assertEquals(DetourHandoffGate.Result.READY, gate.tick(true, false, 0.03));
    }

    @Test
    void persistentGlideTimesOutInsteadOfStartingGroundPathing() {
        DetourHandoffGate gate = new DetourHandoffGate();
        gate.reset();
        for (int i = 1; i < DetourHandoffGate.MAX_WAIT_TICKS; i++) {
            assertEquals(DetourHandoffGate.Result.WAIT, gate.tick(false, true, 1.5));
        }
        assertEquals(DetourHandoffGate.Result.TIMED_OUT, gate.tick(false, true, 1.5));
    }
}
