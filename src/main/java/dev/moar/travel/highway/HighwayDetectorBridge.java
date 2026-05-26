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

/** Runtime world-block scanner for highway detection and verification. */
public final class HighwayDetectorBridge {

    private static final HighwayDetectorBridge INSTANCE = new HighwayDetectorBridge();
    public static HighwayDetectorBridge get() { return INSTANCE; }
    private HighwayDetectorBridge() {}

    // ── ScanResult ───────────────────────────────────────────────
    public record ScanResult(int floorY, int width,
                              int leftRailOffset, int rightRailOffset,
                              boolean hasLeftRail, boolean hasRightRail,
                              float blockConfidence) {}

    // ── CellStatus ───────────────────────────────────────────────
    public enum CellStatus { OK, GRIEFED, UNLOADED }

    // ── Public API ───────────────────────────────────────────────
    /** Try to confirm a highway near the player's current Y. */
    public Optional<ScanResult> scanAt(BlockPos playerPos, HighwayCandidate.Axis axis) {
        for (int yOff = -1; yOff <= 1; yOff++) {
            ScanResult r = scanAlongAxis(
                    playerPos.getX(), playerPos.getY() + yOff, playerPos.getZ(), axis);
            if (r != null) return Optional.of(r);
        }
        return Optional.empty();
    }

    /**
     * Classify one floor cell: is it an intact highway floor with
     * passable walk-space above, clearly griefed, or simply unloaded?
     */
    public CellStatus checkCell(BlockPos floorPos) {
        if (!isChunkLoaded(floorPos)) return CellStatus.UNLOADED;
        if (!isHighwayBlock(floorPos)) return CellStatus.GRIEFED;
        if (!isAirLike(above(floorPos, 1))) return CellStatus.GRIEFED;
        if (!isAirLike(above(floorPos, 2))) return CellStatus.GRIEFED;
        return CellStatus.OK;
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

        boolean leftRail  = hasGuardrail(
                centerX - perpDx * (medianLeft  + 1), floorY, centerZ - perpDz * (medianLeft  + 1));
        boolean rightRail = hasGuardrail(
                centerX + perpDx * (medianRight + 1), floorY, centerZ + perpDz * (medianRight + 1));

        float linearity = validSamples / (float)(SCAN_RANGE * 2 + 1);
        float conf = 0f;
        if      (linearity > 0.5f) conf += 0.4f;
        else if (linearity > 0.3f) conf += 0.2f;
        if (leftRail)             conf += 0.2f;
        if (rightRail)            conf += 0.2f;
        if (width >= 3)           conf += 0.2f;
        conf = Math.min(1f, conf);
        if (conf < 0.3f) return null;

        return new ScanResult(floorY, width,
                -(medianLeft + 1), medianRight + 1,
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
