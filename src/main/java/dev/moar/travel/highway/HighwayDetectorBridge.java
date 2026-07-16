package dev.moar.travel.highway;

import dev.moar.travel.plan.HighwayCandidate;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// World scanner for highway detection and verification.
public final class HighwayDetectorBridge {

    private static final HighwayDetectorBridge INSTANCE = new HighwayDetectorBridge();
    public static HighwayDetectorBridge get() { return INSTANCE; }
    private HighwayDetectorBridge() {}

    // ── ScanResult ───────────────────────────────────────────────
    public record ScanResult(int floorY, int width, int centerX, int centerZ,
                              int leftRailOffset, int rightRailOffset,
                              boolean hasLeftRail, boolean hasRightRail,
                              float blockConfidence) {}

    // ── CellStatus ───────────────────────────────────────────────
    /*
     * OK: floor is intact.
     * GRIEFED: floor is missing with solid terrain below.
     * UNLOADED: data is missing.
     * CAVE_PASS: missing floor looks like a cave opening or portal carve-out.
     */
    public enum CellStatus { OK, GRIEFED, UNLOADED, CAVE_PASS }

    // How many consecutive air-like blocks below the missing floor constitute a cave.
    private static final int CAVE_SCAN_DEPTH = 4;
    // Horizontal radius (blocks) searched for a lit Nether portal above a missing cell.
    private static final int PORTAL_H_RADIUS = 2;
    // Height range searched for portal blocks: from floorY up to floorY + this value.
    private static final int PORTAL_V_HEIGHT = 5;

    // ── Public API ───────────────────────────────────────────────
    /*
     * Check only the floor cell. Skip headroom so natural nether blocks above the
     * road do not look griefed. Suppress cave gaps and portal carve-outs.
     */
    public CellStatus checkFloorOnly(int bx, int floorY, int bz) {
        BlockPos pos = new BlockPos(bx, floorY, bz);
        if (!isChunkLoaded(pos)) return CellStatus.UNLOADED;
        if (isHighwayBlock(bx, floorY,     bz)) return CellStatus.OK;
        // Highway floor may step ±1 block in height at section joints; treat as intact.
        if (isHighwayBlock(bx, floorY + 1, bz)) return CellStatus.OK;
        if (isHighwayBlock(bx, floorY - 1, bz)) return CellStatus.OK;
        // Cave suppressor: a deep void below indicates the highway spans a natural
        // Nether cave opening rather than a targeted block removal.
        if (looksLikeCave(bx, floorY, bz)) return CellStatus.CAVE_PASS;
        // Portal suppressor: highway obsidian was legitimately mined out to build
        // a Nether portal next to the highway. Only applies to lit portals.
        if (looksLikePortal(bx, floorY, bz)) return CellStatus.CAVE_PASS;
        return CellStatus.GRIEFED;
    }

    // Try to confirm a highway near the player's current Y.
    public Optional<ScanResult> scanAt(BlockPos playerPos, HighwayCandidate.Axis axis) {
        for (int yOff = -4; yOff <= 4; yOff++) {
            ScanResult r = scanAlongAxis(
                    playerPos.getX(), playerPos.getY() + yOff, playerPos.getZ(), axis);
            if (r != null) return Optional.of(r);
        }
        return Optional.empty();
    }

    // How deep to scan below a candidate point for exposed lava.
    private static final int LAVA_CHECK_DEPTH = 6;

    // True if there's exposed lava within a short depth below this position —
    // used to reject detour waypoints that would send the player over an open
    // lava lake instead of solid ground. Nether highways are commonly built as
    // causeways over lava, and stepping off to the side has no guaranteed floor.
    public boolean hasLavaBelow(int x, int floorY, int z) {
        for (int dy = 0; dy <= LAVA_CHECK_DEPTH; dy++) {
            BlockPos probe = new BlockPos(x, floorY - dy, z);
            if (!isChunkLoaded(probe)) return false; // unknown — don't block a detour on missing data
            /*? if >=26.1 {*//*
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;
            Block b = mc.level.getBlockState(probe).getBlock();
            *//*?} else {*/
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return false;
            Block b = mc.world.getBlockState(probe).getBlock();
            /*?}*/
            if (b == Blocks.LAVA) return true;
            if (b != Blocks.AIR && b != Blocks.CAVE_AIR && b != Blocks.VOID_AIR
                    && b != Blocks.FIRE && b != Blocks.SOUL_FIRE) {
                return false; // hit solid ground before finding lava — safe
            }
        }
        return false; // no floor and no lava found within the checked depth
    }

    // Classify one floor cell with headroom checks.
    public CellStatus checkCell(BlockPos floorPos) {
        if (!isChunkLoaded(floorPos)) return CellStatus.UNLOADED;
        if (!isHighwayBlock(floorPos)) return CellStatus.GRIEFED;
        if (!isAirLike(above(floorPos, 1))) return CellStatus.GRIEFED;
        if (!isAirLike(above(floorPos, 2))) return CellStatus.GRIEFED;
        return CellStatus.OK;
    }

    // ── Cave detection ────────────────────────────────────────────
    // Treat a deep void below the floor as a natural cave gap.
    private static boolean looksLikeCave(int bx, int floorY, int bz) {
        int voidRun = 0;
        for (int dy = 1; dy <= CAVE_SCAN_DEPTH * 2; dy++) {
            int y = floorY - dy;
            if (y < 5) break; // near bedrock — stop rather than false-positive
            BlockPos pos = new BlockPos(bx, y, bz);
            if (!isChunkLoaded(pos)) return false; // unknown data → conservative
            if (isNaturalVoid(bx, y, bz)) {
                voidRun++;
                if (voidRun >= CAVE_SCAN_DEPTH) return true;
            } else {
                break; // solid block hit; void pocket is too shallow
            }
        }
        return false;
    }

    // Treat nearby lit portals as intentional carve-outs, not grief.
    private static boolean looksLikePortal(int bx, int floorY, int bz) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        /*?}*/
        // Perf: single mutable cursor instead of up-to-125 BlockPos allocations
        // per probed cell.
        /*? if >=26.1 {*//*
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        *//*?} else {*/
        BlockPos.Mutable p = new BlockPos.Mutable();
        /*?}*/
        for (int dy = 0; dy <= PORTAL_V_HEIGHT; dy++) {
            int y = floorY + dy;
            for (int dx = -PORTAL_H_RADIUS; dx <= PORTAL_H_RADIUS; dx++) {
                for (int dz = -PORTAL_H_RADIUS; dz <= PORTAL_H_RADIUS; dz++) {
                    p.set(bx + dx, y, bz + dz);
                    if (!isChunkLoaded(p)) continue; // skip unloaded cells
                    /*? if >=26.1 {*//*
                    if (mc.level.getBlockState(p).getBlock() == Blocks.NETHER_PORTAL) return true;
                    *//*?} else {*/
                    if (mc.world.getBlockState(p).getBlock() == Blocks.NETHER_PORTAL) return true;
                    /*?}*/
                }
            }
        }
        return false;
    }

    // Count only true air as cave void. Exposed lava is still suspicious.
    private static boolean isNaturalVoid(int x, int y, int z) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Block b = mc.level.getBlockState(new BlockPos(x, y, z)).getBlock();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        Block b = mc.world.getBlockState(new BlockPos(x, y, z)).getBlock();
        /*?}*/
        return b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR;
    }

    // ── Internal scan ────────────────────────────────────────────
    private ScanResult scanAlongAxis(int px, int floorY, int pz,
                                      HighwayCandidate.Axis axis) {
        int perpDx = axis.perpDx();
        int perpDz = axis.perpDz();
        int stepDx = axis.stepDx;
        int stepDz = axis.stepDz;
        final int SCAN_RANGE = 20;
        final int MAX_PERP   = 8;

        // Find center: player may be slightly off-center perpendicular
        int centerX = px, centerZ = pz;
        if (!isHighwayBlock(px, floorY, pz)) {
            boolean found = false;
            for (int shift = 1; shift <= 4; shift++) {
                if (isHighwayBlock(px + perpDx * shift, floorY, pz + perpDz * shift)) {
                    centerX = px + perpDx * shift;
                    centerZ = pz + perpDz * shift;
                    found = true;
                    break;
                }
                if (isHighwayBlock(px - perpDx * shift, floorY, pz - perpDz * shift)) {
                    centerX = px - perpDx * shift;
                    centerZ = pz - perpDz * shift;
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }

        List<Integer> leftEdges  = new ArrayList<>();
        List<Integer> rightEdges = new ArrayList<>();
        int validSamples = 0;

        for (int step = -SCAN_RANGE; step <= SCAN_RANGE; step++) {
            int sx = centerX + stepDx * step;
            int sz = centerZ + stepDz * step;
            if (!isHighwayBlock(sx, floorY, sz)) continue;

            int left = 0;
            for (int i = 1; i <= MAX_PERP; i++) {
                if (isHighwayBlock(sx - perpDx * i, floorY, sz - perpDz * i)) left = i;
                else break;
            }
            int right = 0;
            for (int i = 1; i <= MAX_PERP; i++) {
                if (isHighwayBlock(sx + perpDx * i, floorY, sz + perpDz * i)) right = i;
                else break;
            }
            leftEdges.add(left);
            rightEdges.add(right);
            validSamples++;
        }

        if (validSamples < 5) return null;

        leftEdges.sort(null);
        rightEdges.sort(null);
        int medianLeft  = leftEdges.get(leftEdges.size() / 2);
        int medianRight = rightEdges.get(rightEdges.size() / 2);
        int width = medianLeft + 1 + medianRight;
        if (width < 2 || width > 7) return null;

        // Shift the anchor to the geometric midpoint of the highway floor.
        // The initial centerX/Z is wherever the player happened to snap — it is
        // NOT necessarily the true center. For example, if the player snapped to
        // the guardrail edge, medianLeft=0 and medianRight=width-1, so the anchor
        // is completely off-center. Correcting this before returning the ScanResult
        // is what makes the planner path to the highway center instead of the rail.
        int centerAdjust = (medianRight - medianLeft) / 2;
        centerX += perpDx * centerAdjust;
        centerZ += perpDz * centerAdjust;
        int adjLeft  = medianLeft  + centerAdjust;
        int adjRight = medianRight - centerAdjust;

        boolean leftRail  = hasGuardrail(
                centerX - perpDx * (adjLeft  + 1), floorY, centerZ - perpDz * (adjLeft  + 1));
        boolean rightRail = hasGuardrail(
                centerX + perpDx * (adjRight + 1), floorY, centerZ + perpDz * (adjRight + 1));

        float linearity = validSamples / (float)(SCAN_RANGE * 2 + 1);
        float conf = 0f;
        if      (linearity > 0.5f) conf += 0.4f;
        else if (linearity > 0.3f) conf += 0.2f;
        if (leftRail)             conf += 0.2f;
        if (rightRail)            conf += 0.2f;
        if (width >= 3)           conf += 0.2f;
        conf = Math.min(1f, conf);
        if (conf < 0.3f) return null;

        return new ScanResult(floorY, width, centerX, centerZ,
                -(adjLeft + 1), adjRight + 1,
                leftRail, rightRail, conf);
    }

    // ── Stonecutter-quarantined block helpers ─────────────────────
    private static boolean isHighwayBlock(int x, int y, int z) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Block b = mc.level.getBlockState(new BlockPos(x, y, z)).getBlock();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        Block b = mc.world.getBlockState(new BlockPos(x, y, z)).getBlock();
        /*?}*/
        return b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN;
    }

    private static boolean isHighwayBlock(BlockPos pos) {
        return isHighwayBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean isAirLike(BlockPos pos) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return true;
        Block b = mc.level.getBlockState(pos).getBlock();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return true;
        Block b = mc.world.getBlockState(pos).getBlock();
        /*?}*/
        return b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR
                || b == Blocks.FIRE || b == Blocks.SOUL_FIRE;
    }

    private static boolean hasGuardrail(int x, int floorY, int z) {
        return isHighwayBlock(x, floorY, z) && isHighwayBlock(x, floorY + 1, z);
    }

    private static boolean isChunkLoaded(BlockPos pos) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return mc.level.isLoaded(pos);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        return mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        /*?}*/
    }

    private static BlockPos above(BlockPos pos, int n) {
        /*? if >=26.1 {*//*
        return pos.above(n);
        *//*?} else {*/
        return pos.up(n);
        /*?}*/
    }
}
