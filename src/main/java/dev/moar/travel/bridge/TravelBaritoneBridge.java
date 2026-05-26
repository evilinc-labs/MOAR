package dev.moar.travel.bridge;

import dev.moar.util.PathWalker;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.List;

/** Narrow adapter over PathWalker and optional Baritone features. */
public final class TravelBaritoneBridge {

    private static final TravelBaritoneBridge INSTANCE = new TravelBaritoneBridge();

    public static TravelBaritoneBridge get() { return INSTANCE; }

    private TravelBaritoneBridge() {}

    /** True if Baritone is on the classpath. */
    public boolean isAvailable() {
        return PathWalker.isBaritoneAvailable();
    }

    /** Walk near a target. */
    public void walkNear(BlockPos pos, int radius) {
        PathWalker.walkToNearby(pos, radius);
    }

    /** Walk directly to a target. */
    public void walkTo(BlockPos pos) {
        PathWalker.walkTo(pos);
    }

    /** Walk through ordered detour waypoints. */
    public void walkToWaypoints(List<BlockPos> waypoints, int radius) {
        PathWalker.walkToViaWaypoints(waypoints, radius);
    }

    /** Stop ground and elytra pathing. */
    public void cancelAll() {
        PathWalker.stop();
        PathWalker.stopElytra();
    }

    public boolean isPathing()   { return PathWalker.isActive() && !PathWalker.hasArrived() && !PathWalker.isStuck(); }
    public boolean isArrived()   { return PathWalker.hasArrived(); }
    public boolean isStuck()     { return PathWalker.isStuck(); }
    public BlockPos currentTarget() { return PathWalker.getTarget(); }
    public int ticksWalking()    { return PathWalker.getTicksWalking(); }

    /** Start Baritone's elytra process if available. */
    public void startElytraFlight(BlockPos dest) {
        PathWalker.startElytra(dest);
    }

    /** True while Baritone's elytra process owns movement. */
    public boolean isElytraOwning() {
        return PathWalker.isElytraActive();
    }

    /** True when the elytra process is close enough to the destination. */
    public boolean isElytraArrived() {
        return PathWalker.hasElytraArrived();
    }

    /** Keep elytra stuck detection separate from ground walking. */
    public boolean isElytraStuck() {
        return false;
    }

    /** Stop Baritone elytra pathing. */
    public void stopElytra() {
        PathWalker.stopElytra();
    }

    /** Tick ground PathWalker while Baritone owns movement. */
    public void tick() {
        PathWalker.tick();
    }
}
