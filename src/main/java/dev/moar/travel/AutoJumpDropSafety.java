package dev.moar.travel;

import java.util.function.IntFunction;

// A grounded elytra launch needs room to fall without dropping into lava.
final class AutoJumpDropSafety {
    enum Slice { AIR, SAFE_FLOOR, HAZARD, UNKNOWN }

    private AutoJumpDropSafety() {}

    static boolean isClear(IntFunction<Slice> column, int minimumDrop, int scanDepth) {
        if (scanDepth < minimumDrop) return false;
        for (int depth = 1; depth <= scanDepth; depth++) {
            Slice slice = column.apply(depth);
            if (slice == Slice.AIR) continue;
            return slice == Slice.SAFE_FLOOR && depth > minimumDrop;
        }
        return true;
    }
}
