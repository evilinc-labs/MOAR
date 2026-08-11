package dev.moar.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Serializes access to Baritone's native pathfinder contexts.
public final class BaritoneNativeContextGate {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/BaritoneCompat");
    private static final Object LOCK = new Object();

    static {
        LOGGER.info("Baritone native context serialization active");
    }

    private BaritoneNativeContextGate() {
    }

    public static Object lock() {
        return LOCK;
    }
}
