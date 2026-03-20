package dev.moar.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

// Thread-safe sneak override flag. A mixin on KeyboardInput.tick() checks
// shouldSneak() after keyboard polling. Sneak is only forced when the player
// is near a platform edge (via EdgeDetector), preserving full walk speed on
// safe ground. setForceAbsoluteSneak bypasses the edge check entirely.
public final class SneakOverride {

    private SneakOverride() {}

    private static volatile boolean forceSneak;

    // Force sneak unconditionally (bypasses EdgeDetector). Used during
    // edge-walking where the position is inherently narrow.
    private static volatile boolean forceAbsoluteSneak;

    /** Enable/disable the edge-safe sneak request. */
    public static void setForceSneak(boolean value) {
        forceSneak = value;
    }

    /**
     * Enable/disable unconditional sneak — no edge detection check.
     * Use during edge-walking and bridging where every position is
     * inherently dangerous.
     */
    public static void setForceAbsoluteSneak(boolean value) {
        forceAbsoluteSneak = value;
    }

    /** True if the printer wants edge safety. */
    public static boolean isForceSneak() {
        return forceSneak;
    }

    /** True if unconditional sneak is active (edge-walking). */
    public static boolean isForceAbsoluteSneak() {
        return forceAbsoluteSneak;
    }

    // Called by KeyboardInputMixin. Returns true if sneak should be forced
    // (absolute mode, or edge-safe mode when near a ledge).
    public static boolean shouldSneak() {
        if (forceAbsoluteSneak) return true;

        if (!forceSneak) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;

        return EdgeDetector.isNearEdge(mc.player, mc.world);
    }
}
