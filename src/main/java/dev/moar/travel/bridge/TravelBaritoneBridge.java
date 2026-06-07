package dev.moar.travel.bridge;

import dev.moar.util.PathWalker;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.List;

// Small adapter over PathWalker and optional Baritone features.
public final class TravelBaritoneBridge {

    private static final TravelBaritoneBridge INSTANCE = new TravelBaritoneBridge();

    public static TravelBaritoneBridge get() { return INSTANCE; }

    private TravelBaritoneBridge() {}

    // Check whether Baritone is available.
    public boolean isAvailable() {
        return PathWalker.isBaritoneAvailable();
    }

    // Walk near a target.
    public void walkNear(BlockPos pos, int radius) {
        PathWalker.walkToNearby(pos, radius);
    }

    // Walk directly to a target.
    public void walkTo(BlockPos pos) {
        PathWalker.walkTo(pos);
    }

    // Walk through ordered waypoints.
    public void walkToWaypoints(List<BlockPos> waypoints, int radius) {
        PathWalker.walkToViaWaypoints(waypoints, radius);
    }

    // Move toward a Y level with placement enabled.
    public void walkToYLevelWithPlacement(int y) {
        PathWalker.walkToYLevelWithPlacement(y, null);
    }

    // Mine downward until the target Y or a safe stop.
    public void startMiningDescent(int targetY) {
        PathWalker.startMiningDescent(targetY);
    }

    // Stop all Baritone movement.
    public void cancelAll() {
        PathWalker.stop();
        PathWalker.stopElytra();
    }

    public boolean isPathing()   { return PathWalker.isActive() && !PathWalker.hasArrived() && !PathWalker.isStuck(); }
    public boolean isArrived()   { return PathWalker.hasArrived(); }
    public boolean isStuck()     { return PathWalker.isStuck(); }
    public BlockPos currentTarget() { return PathWalker.getTarget(); }
    public int ticksWalking()    { return PathWalker.getTicksWalking(); }

    // Start Baritone elytra flight.
    public void startElytraFlight(BlockPos dest) {
        PathWalker.startElytra(dest);
    }

    // Check whether Baritone owns elytra movement.
    public boolean isElytraOwning() {
        return PathWalker.isElytraActive();
    }

    // Check whether Baritone considers elytra flight arrived.
    public boolean isElytraArrived() {
        return PathWalker.hasElytraArrived();
    }

    // Elytra stuck handling is tracked separately for now.
    public boolean isElytraStuck() {
        return false;
    }

    // Stop Baritone elytra flight.
    public void stopElytra() {
        PathWalker.stopElytra();
    }

    // Tick PathWalker while Baritone owns movement.
    public void tick() {
        PathWalker.tick();
    }
}
