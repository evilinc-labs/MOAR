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
    public record ScanResult(int floorY, int width, int centerX, int centerZ,
                              int leftRailOffset, int rightRailOffset,
                              boolean hasLeftRail, boolean hasRightRail,
                              float blockConfidence) {}

    // ── CellStatus ───────────────────────────────────────────────
    /**
     * OK        — floor block present and intact.
     * GRIEFED   — floor block missing, solid terrain below → targeted removal.
     * UNLOADED  — chunk not in memory; result undefined.
     * CAVE_PASS — floor block missing but suppressed by one of two heuristics:
     *              (a) natural cave: ≥4 consecutive void blocks directly below, OR
     *              (b) portal area:  NETHER_PORTAL block found within the scan
     *                  box above the cell (highway mined out for portal construction).
     *             In both cases the cell counts toward total but not toward griefed.
     */
    public enum CellStatus { OK, GRIEFED, UNLOADED, CAVE_PASS }

    // How many consecutive air-like blocks below the missing floor constitute a cave.
    private static final int CAVE_SCAN_DEPTH = 4;
    // Horizontal radius (blocks) searched for a lit Nether portal above a missing cell.
    private static final int PORTAL_H_RADIUS = 2;
    // Height range searched for portal blocks: from floorY up to floorY + this value.
    private static final int PORTAL_V_HEIGHT = 5;

    // ── Public API ───────────────────────────────────────────────
    /**
     * Floor-presence check used by the integrity verifier.
     * Deliberately does NOT check clearance above — nether rack can generate
     * at Y+1 above a clean highway floor and must not trigger false griefs.
     *
     * Before returning GRIEFED, two suppressors are tried:
     *  1. Cave suppressor — if the column below is open void for ≥ CAVE_SCAN_DEPTH
     *     blocks, the highway spans a natural cave opening (not targeted removal).
     *  2. Portal suppressor — if a lit Nether portal block exists within
     *     PORTAL_H_RADIUS blocks horizontally and PORTAL_V_HEIGHT blocks above,
     *     the highway was mined out for portal construction, not griefed.
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

    // ── Cave detection ────────────────────────────────────────────
    /**
     * Returns true when the column below a missing floor cell looks like a
     * natural Nether cave rather than targeted grief.
     *
     * Strategy: scan straight down from (bx, floorY-1). Grief typically leaves
     * solid netherrack immediately below the removed obsidian.  A cave leaves
     * consecutive air / lava / fire — natural Nether void material.  We require
     * at least CAVE_SCAN_DEPTH consecutive void blocks before concluding cave.
     * Stops early on solid blocks or if we reach the bedrock band (Y < 5).
     * Returns false if any scanned chunk is not loaded (caller falls back to GRIEFED).
     */
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

    /**
     * Returns true when a lit Nether portal is present near the missing floor cell.
     * Scans a box from (bx ± PORTAL_H_RADIUS, floorY, bz ± PORTAL_H_RADIUS) up
     * PORTAL_V_HEIGHT blocks for NETHER_PORTAL blocks.  Only lit portals produce
     * the portal block, so unlit frames (where there is no visual portal) are not
     * suppressed — they are indistinguishable from ordinary missing floor sections.
     * The scan is intentionally small (5×5×6 = 150 queries) because it is only
     * reached after the cave check has already returned false.
     */
    private static boolean looksLikePortal(int bx, int floorY, int bz) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        /*?}*/
        for (int dy = 0; dy <= PORTAL_V_HEIGHT; dy++) {
            int y = floorY + dy;
            for (int dx = -PORTAL_H_RADIUS; dx <= PORTAL_H_RADIUS; dx++) {
                for (int dz = -PORTAL_H_RADIUS; dz <= PORTAL_H_RADIUS; dz++) {
                    BlockPos p = new BlockPos(bx + dx, y, bz + dz);
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

    /**
     * True for blocks that represent natural Nether air void — the kind left
     * when a highway spans a cave opening.
     *
     * Lava, fire, and soul_fire are intentionally excluded.  On 2b2t the
     * highway floor (Y≈11) is frequently built directly over a lava lake; if
     * a griefer removes only the obsidian floor the exposed lava immediately
     * below would otherwise satisfy the CAVE_SCAN_DEPTH run and produce a
     * false CAVE_PASS classification (false negative).  Only true air is a
     * reliable indicator of a bridged-over cave gap, not an exposed lava
     * surface.
     */
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
