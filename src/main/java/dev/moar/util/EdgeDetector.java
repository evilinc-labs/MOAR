package dev.moar.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

// Detects dangerous platform edges (3+ block drops) around a player's hitbox.
// Grid-scans all block columns under the expanded hitbox bounding box.
// Used by the sneak system to only engage sneak near actual ledges.
public final class EdgeDetector {

    private EdgeDetector() {}

    private static final double HALF_WIDTH = 0.3; // player hitbox half-width

    private static final double PROBE_MARGIN = 0.30; // margin beyond hitbox for drop-off detection

    // ── per-tick cache (isNearEdge is called per render frame) ───────
    private static long   cachedTick   = Long.MIN_VALUE;
    private static boolean cachedResult = false;

    // True if the player is near a 3+ block drop (dangerous ledge).
    public static boolean isNearEdge(ClientPlayerEntity player, World world) {
        // Cache result for the current game tick — this method is called
        // per render frame via mixin but the answer only changes per tick.
        long tick = world.getTime();
        if (tick == cachedTick) return cachedResult;
        cachedTick = tick;

        /*? if >=1.21.10 {*//*
        double px = player.getSyncedPos().x;
        double py = player.getSyncedPos().y;
        double pz = player.getSyncedPos().z;
        *//*?} else {*/
        double px = player.getPos().x;
        double py = player.getPos().y;
        double pz = player.getPos().z;
        /*?}*/

        // Expanded bounding perimeter: hitbox + probe margin
        double extent = HALF_WIDTH + PROBE_MARGIN;

        // Iterate over every block column the expanded bbox overlaps.
        // This catches ALL edge shapes including concave inner corners
        // of cross-shaped, L-shaped, or irregular partially-built platforms.
        int minBx = MathHelper.floor(px - extent);
        int maxBx = MathHelper.floor(px + extent);
        int minBz = MathHelper.floor(pz - extent);
        int maxBz = MathHelper.floor(pz + extent);
        int footY = MathHelper.floor(py);

        for (int bx = minBx; bx <= maxBx; bx++) {
            for (int bz = minBz; bz <= maxBz; bz++) {
                if (!hasSafeGround(world, bx, footY, bz)) {
                    cachedResult = true;
                    return true; // dangerous drop-off found
                }
            }
        }

        cachedResult = false;
        return false; // all columns have safe ground — no need to sneak
    }

    // True if there's solid ground within 3 blocks below foot level at (x, z).
    private static boolean hasSafeGround(World world, int x, int y, int z) {
        for (int dy = 0; dy >= -2; dy--) {
            if (isGroundBlock(world, new BlockPos(x, y + dy, z))) return true;
        }
        return false;
    }

    // True if the block has a collision shape (standable).
    private static boolean isGroundBlock(World world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
}
