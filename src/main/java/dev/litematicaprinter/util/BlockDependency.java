package dev.litematicaprinter.util;

import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/**
 * Determines block placement dependencies &mdash; whether a block needs an
 * adjacent support block to be placed (e.g.&nbsp;torches need a floor or wall,
 * ladders need a wall behind them, lanterns can hang from the ceiling).
 *
 * <p>Used by the placement queue to skip blocks whose support requirements
 * are not yet met, avoiding wasted placement attempts and false
 * &ldquo;missing item&rdquo; reports.  Support blocks (stone, planks, etc.)
 * are freestanding and always placed first, building up the structure that
 * dependent blocks (torches, signs, flowers, rails, etc.) attach to.
 *
 * <h3>Dependency tiers</h3>
 * <ul>
 *   <li><b>Tier&nbsp;0 &mdash; Freestanding:</b> stone, planks, glass, slabs,
 *       stairs, logs, concrete, etc.  No adjacent support needed.</li>
 *   <li><b>Tier&nbsp;1 &mdash; Floor-dependent:</b> torches (standing), signs
 *       (standing), flowers, saplings, carpet, pressure plates, rails,
 *       redstone wire, repeaters, comparators, doors, beds, tall plants,
 *       candles, flower pots, sea pickles, skulls (standing).</li>
 *   <li><b>Tier&nbsp;2 &mdash; Wall-dependent:</b> wall torches, wall signs,
 *       wall banners, ladders, wall skulls, tripwire hooks.  Requires a
 *       solid block on the face the block is attached to.</li>
 *   <li><b>Tier&nbsp;3 &mdash; Ceiling-dependent:</b> hanging signs, hanging
 *       lanterns, ceiling buttons / levers.</li>
 *   <li><b>Tier&nbsp;4 &mdash; Multi-face:</b> vines (attach to one or more
 *       wall faces, or hang from block above).  Handled separately.</li>
 * </ul>
 */
public final class BlockDependency {

    private BlockDependency() {}

    // ═══════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns the direction from which the block requires support (e.g.
     * {@link Direction#DOWN} for floor-placed blocks, the wall direction
     * for wall torches), or {@code null} if the block is freestanding.
     *
     * <p>For blocks with variable attachment (buttons, levers), the
     * direction is determined from block state properties.
     *
     * <p><b>Note:</b> Vines are not handled here because they can attach
     * to multiple faces.  Use {@link #isReadyToPlace} instead.
     */
    public static Direction getRequiredSupport(BlockState state) {
        Block block = state.getBlock();

        // ── Wall-mounted torches ────────────────────────────────
        if (block instanceof WallTorchBlock
                || block instanceof WallRedstoneTorchBlock) {
            if (state.contains(Properties.HORIZONTAL_FACING)) {
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
            }
        }
        // ── Standing torches ────────────────────────────────────
        if (block instanceof TorchBlock
                && !(block instanceof WallTorchBlock)) {
            return Direction.DOWN;
        }
        if (block instanceof RedstoneTorchBlock
                && !(block instanceof WallRedstoneTorchBlock)) {
            return Direction.DOWN;
        }

        // ── Wall signs ──────────────────────────────────────────
        if (block instanceof WallSignBlock
                || block instanceof WallHangingSignBlock) {
            if (state.contains(Properties.HORIZONTAL_FACING)) {
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
            }
        }
        // ── Standing signs ──────────────────────────────────────
        if (block instanceof SignBlock
                && !(block instanceof WallSignBlock)) {
            return Direction.DOWN;
        }
        // ── Hanging signs (chain below a block) ─────────────────
        if (block instanceof HangingSignBlock
                && !(block instanceof WallHangingSignBlock)) {
            return Direction.UP;
        }

        // ── Banners ─────────────────────────────────────────────
        if (block instanceof AbstractBannerBlock
                && !(block instanceof WallBannerBlock)) {
            return Direction.DOWN;
        }
        if (block instanceof WallBannerBlock) {
            if (state.contains(Properties.HORIZONTAL_FACING)) {
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
            }
        }

        // ── Ladders ─────────────────────────────────────────────
        if (block instanceof LadderBlock) {
            if (state.contains(Properties.HORIZONTAL_FACING)) {
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
            }
        }

        // ── Lanterns ────────────────────────────────────────────
        if (block instanceof LanternBlock) {
            if (state.contains(Properties.HANGING)
                    && state.get(Properties.HANGING)) {
                return Direction.UP;
            }
            return Direction.DOWN;
        }

        // ── Buttons / Levers (floor, wall, ceiling) ─────────────
        if (block instanceof ButtonBlock || block instanceof LeverBlock) {
            if (state.contains(Properties.BLOCK_FACE)) {
                BlockFace face = state.get(Properties.BLOCK_FACE);
                return switch (face) {
                    case FLOOR   -> Direction.DOWN;
                    case CEILING -> Direction.UP;
                    case WALL    -> {
                        if (state.contains(Properties.HORIZONTAL_FACING)) {
                            yield state.get(Properties.HORIZONTAL_FACING)
                                    .getOpposite();
                        }
                        yield null;
                    }
                };
            }
        }

        // ── Skulls / heads ──────────────────────────────────────
        if (block instanceof WallSkullBlock) {
            if (state.contains(Properties.HORIZONTAL_FACING)) {
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
            }
        }
        if (block instanceof SkullBlock
                && !(block instanceof WallSkullBlock)) {
            return Direction.DOWN;
        }

        // ── Trapdoors ───────────────────────────────────────────
        if (block instanceof TrapdoorBlock) {
            if (state.contains(Properties.BLOCK_HALF)) {
                BlockHalf half = state.get(Properties.BLOCK_HALF);
                return half == BlockHalf.TOP ? Direction.UP : Direction.DOWN;
            }
        }

        // ── Doors / Beds / Tall plants ──────────────────────────
        if (block instanceof DoorBlock)      return Direction.DOWN;
        if (block instanceof BedBlock)       return Direction.DOWN;
        if (block instanceof TallPlantBlock) return Direction.DOWN;
        // ── Hoppers ─────────────────────────────────────────
        //  Hopper facing is determined by the clicked face. The block
        //  in the output direction must exist first so we can click it.
        if (block instanceof HopperBlock) {
            if (state.contains(Properties.HOPPER_FACING)) {
                Direction facing = state.get(Properties.HOPPER_FACING);
                // DOWN hoppers: need block below (or above) to click
                if (facing == Direction.DOWN) return Direction.DOWN;
                // Horizontal: need the block in the output direction
                return facing;
            }
        }
        // ── Floor-dependent blocks (previously uncovered) ───────
        if (block instanceof CarpetBlock)               return Direction.DOWN;
        if (block instanceof AbstractPressurePlateBlock) return Direction.DOWN;
        if (block instanceof AbstractRailBlock)          return Direction.DOWN;
        if (block instanceof RedstoneWireBlock)          return Direction.DOWN;
        if (block instanceof AbstractRedstoneGateBlock)  return Direction.DOWN;
        if (block instanceof FlowerPotBlock)             return Direction.DOWN;
        if (block instanceof CandleBlock)                return Direction.DOWN;
        if (block instanceof SeaPickleBlock)             return Direction.DOWN;

        // ── Tripwire hooks ──────────────────────────────────────
        if (block instanceof TripwireHookBlock) {
            if (state.contains(Properties.HORIZONTAL_FACING)) {
                return state.get(Properties.HORIZONTAL_FACING).getOpposite();
            }
        }

        // ── Amethyst clusters / buds ────────────────────────────
        if (block instanceof AmethystClusterBlock) {
            if (state.contains(Properties.FACING)) {
                return state.get(Properties.FACING).getOpposite();
            }
        }

        // ── Catch-all: PlantBlock covers flowers, saplings,
        //    ferns, dead bush, crops, mushrooms, etc. ────────────
        //    (checked AFTER TallPlantBlock to avoid shadowing)
        if (block instanceof PlantBlock) return Direction.DOWN;

        // ── Freestanding — no dependency ────────────────────────
        return null;
    }

    /**
     * Returns {@code true} if this block's placement dependencies are
     * satisfied in the current world state.  Freestanding blocks always
     * return {@code true}.
     *
     * <p>For blocks that need support (torches, ladders, flowers, etc.),
     * this checks whether the required adjacent block exists and is solid.
     *
     * <p>Special case: {@link VineBlock} can attach to multiple faces —
     * returns {@code true} if at least one attached face has support.
     */
    public static boolean isReadyToPlace(World world, BlockPos pos,
                                          BlockState target) {
        Block block = target.getBlock();

        // ── Vines: multiple attachment faces ────────────────────
        if (block instanceof VineBlock) {
            return hasVineSupport(world, pos, target);
        }

        Direction support = getRequiredSupport(target);
        if (support == null) return true; // freestanding

        return isSolidAt(world, pos.offset(support));
    }

    /**
     * Returns the dependency tier of a block state.
     * <ul>
     *   <li><b>0</b> — freestanding (no dependency)</li>
     *   <li><b>1</b> — needs adjacent support</li>
     * </ul>
     * Used in comparator tie-breaking to prioritize freestanding blocks.
     */
    public static int getTier(BlockState state) {
        if (state.getBlock() instanceof VineBlock) return 1;
        return getRequiredSupport(state) == null ? 0 : 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks whether the block at {@code pos} is solid (non-replaceable
     * with a non-empty collision shape).
     */
    private static boolean isSolidAt(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isReplaceable()
                && state.getOutlineShape(world, pos) != VoxelShapes.empty();
    }

    /**
     * Vines can attach to one or more wall faces, or hang from the bottom
     * of a block above.  Returns {@code true} if at least one attachment
     * point has solid support.
     */
    private static boolean hasVineSupport(World world, BlockPos pos,
                                           BlockState state) {
        if (hasVineFace(world, pos, state, Properties.NORTH, Direction.NORTH)) return true;
        if (hasVineFace(world, pos, state, Properties.SOUTH, Direction.SOUTH)) return true;
        if (hasVineFace(world, pos, state, Properties.EAST,  Direction.EAST))  return true;
        if (hasVineFace(world, pos, state, Properties.WEST,  Direction.WEST))  return true;
        // Vines can also hang from the block above
        return isSolidAt(world, pos.up());
    }

    /**
     * Returns {@code true} if the vine is attached to the given face AND
     * there is a solid block in that direction.
     */
    private static boolean hasVineFace(World world, BlockPos pos,
                                        BlockState state,
                                        Property<Boolean> prop,
                                        Direction dir) {
        if (!state.contains(prop) || !state.get(prop)) return false;
        return isSolidAt(world, pos.offset(dir));
    }
}
