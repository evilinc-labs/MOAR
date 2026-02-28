package dev.litematicaprinter.util;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
/*? if >=1.21.8 {*//*
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
/*?}*/
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Core automation engine for block placement tasks.
 *
 * <p>Uses a multi-tick pipeline to comply with anticheat placement validation:
 * <ol>
 *   <li><b>Tick 1 (ROTATE):</b> select item, find face, rotate player toward
 *       the hit position, start sneaking if the neighbour is interactive.</li>
 *   <li><b>Tick 2 (PLACE):</b> verify rotation has converged, build a proper
 *       ray-face hit result from the player's eye along the look vector, and
 *       send the interact-block packet.</li>
 *   <li><b>Tick 3 (FINISH):</b> release sneak if it was pressed.</li>
 * </ol>
 *
 * <p>Designed to stay under server placement-rate limits (default 8 BPS,
 * safely under the 9 BPS policy) and pass GrimAC RotationPlace /
 * FabricatedPlace / FarPlace / BadPackets checks.
 */
public final class PlacementEngine {

    private PlacementEngine() {}

    // ── placement pipeline states ───────────────────────────────────────

    private enum PlacePhase { IDLE, ROTATING, PLACING, FINISHING }

    private static PlacePhase phase = PlacePhase.IDLE;

    // ── pipeline context (set in ROTATING, consumed in PLACING/FINISHING) ─

    private static BlockPos   pendingTarget;
    private static Direction  pendingFace;
    private static boolean    pendingNeedsSneak;
    private static float      targetYaw;
    private static float      targetPitch;
    private static float      savedYaw;
    private static float      savedPitch;
    private static int        rotateTicks;

    /** Max ticks to spend converging rotation before giving up. */
    private static final int  MAX_ROTATE_TICKS = 4;
    /** Yaw/pitch must be within this many degrees to be considered converged. */
    private static final float CONVERGE_THRESHOLD = 1.0f;
    /** Max rotation speed per tick (degrees). */
    private static final float MAX_TURN_SPEED = 45.0f;

    // ── rate limiter ────────────────────────────────────────────────────

    private static int    bps               = 8;
    private static long   lastPlacementNano = 0;

    /** Set the maximum blocks-per-second rate (clamped 1–9). */
    public static void setBps(int value) {
        bps = Math.max(1, Math.min(9, value));
    }

    public static int getBps() { return bps; }

    /**
     * Returns {@code true} if the engine is idle and enough time has passed
     * since the last placement to stay under the configured BPS ceiling.
     */
    public static boolean canPlace() {
        if (phase != PlacePhase.IDLE) return false;
        long nowNano = System.nanoTime();
        long intervalNano = 1_000_000_000L / bps;
        return (nowNano - lastPlacementNano) >= intervalNano;
    }

    /** Whether the pipeline is actively executing a placement. */
    public static boolean isBusy() {
        return phase != PlacePhase.IDLE;
    }

    /** Record a placement action (updates the rate-limiter timestamp). */
    public static void recordPlacement() {
        lastPlacementNano = System.nanoTime();
    }

    /** Cancel any in-progress placement and return to idle. */
    public static void reset() {
        if (pendingNeedsSneak && phase == PlacePhase.FINISHING) {
            releaseSneakPacket();
        }
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingFace = null;
        pendingNeedsSneak = false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MULTI-TICK PIPELINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tick the placement pipeline.  Must be called every client tick while
     * the printer is active.
     *
     * @return {@code true} on the tick a block was actually placed
     */
    public static boolean tick() {
        return switch (phase) {
            case IDLE     -> false;
            case ROTATING -> tickRotate();
            case PLACING  -> tickPlace();
            case FINISHING -> tickFinish();
        };
    }

    // ── phase: ROTATING ─────────────────────────────────────────────────

    private static boolean tickRotate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { reset(); return false; }

        rotateTicks++;

        // Smoothly interpolate yaw/pitch toward target
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        newPitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        // Check convergence
        boolean converged = Math.abs(MathHelper.wrapDegrees(targetYaw - newYaw)) < CONVERGE_THRESHOLD
                         && Math.abs(targetPitch - newPitch) < CONVERGE_THRESHOLD;

        if (converged || rotateTicks >= MAX_ROTATE_TICKS) {
            // Snap to exact angles on convergence
            if (converged) {
                mc.player.setYaw(targetYaw);
                mc.player.setPitch(targetPitch);
            }

            // Send sneak packet this tick (will interact next tick)
            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            phase = PlacePhase.PLACING;
        }

        return false;
    }

    // ── phase: PLACING ──────────────────────────────────────────────────

    private static boolean tickPlace() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            reset();
            return false;
        }

        ClientPlayerEntity player = mc.player;

        // Build hit result from the player's current eye position + look vector
        Vec3d eyePos = player.getEyePos();
        BlockPos neighbor = pendingTarget.offset(pendingFace);
        Direction clickSide = pendingFace.getOpposite();

        // Compute a proper hit position: ray from eye to the face of the neighbor block
        Vec3d hitPos = computeRayFaceHit(eyePos, player.getYaw(), player.getPitch(),
                                          neighbor, clickSide, mc.world);

        BlockHitResult hitResult = new BlockHitResult(hitPos, clickSide, neighbor, false);

        // Interact
        ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
        }

        recordPlacement();

        if (pendingNeedsSneak) {
            phase = PlacePhase.FINISHING;
        } else {
            // Restore look direction
            restoreLook(player);
            phase = PlacePhase.IDLE;
            pendingTarget = null;
        }

        return true;
    }

    // ── phase: FINISHING (release sneak on a separate tick) ──────────────

    private static boolean tickFinish() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            releaseSneakPacket();
            restoreLook(mc.player);
        }
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingNeedsSneak = false;
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BLOCK PLACEMENT (entry point — begins the pipeline)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Begins a placement attempt.  Does NOT place immediately — instead
     * starts the rotate → place → finish pipeline.  Call {@link #tick()}
     * on subsequent ticks to drive it.
     *
     * @param target       world position to place at
     * @param desired      target block state (used to determine item)
     * @param allowSwap    whether to pull items from main inventory to hotbar
     * @return {@code true} if the pipeline was successfully started
     */
    public static boolean placeBlock(BlockPos target, BlockState desired, boolean allowSwap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        if (phase != PlacePhase.IDLE) return false;

        ClientPlayerEntity player = mc.player;
        World world = mc.world;

        // ── 1. find the required item ───────────────────────────────────
        Item requiredItem = desired.getBlock().asItem();
        if (!(requiredItem instanceof BlockItem)) return false;

        if (!selectItem(player, mc, requiredItem, allowSwap)) return false;

        // ── 2. find an adjacent face to click ───────────────────────────
        Direction face = findPlacementFace(world, target);
        if (face == null) return false;

        BlockPos neighbor = target.offset(face);
        Block neighborBlock = world.getBlockState(neighbor).getBlock();

        // ── 3. compute where we need to look ────────────────────────────
        Direction clickSide = face.getOpposite();
        Vec3d eyePos = player.getEyePos();
        Vec3d hitPos = computeRayFaceHit(eyePos, player.getYaw(), player.getPitch(),
                                          neighbor, clickSide, world);

        // Compute yaw/pitch from eye to hit position
        Vec3d toHit = hitPos.subtract(eyePos);
        double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
        float desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
        float desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));

        // ── 4. store pipeline state and begin rotation ──────────────────
        pendingTarget = target.toImmutable();
        pendingFace = face;
        pendingNeedsSneak = isInteractive(neighborBlock);
        targetYaw = desiredYaw;
        targetPitch = MathHelper.clamp(desiredPitch, -90.0f, 90.0f);
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RAY-FACE HIT CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes a hit position on the given face of {@code neighbor} that
     * lies on a ray from {@code eyePos} along the player's look direction.
     *
     * <p>If the ray doesn't intersect the face cleanly (edge cases with
     * very oblique angles), falls back to the center of the face, which
     * is still valid for anticheat since the hit pos is on the block face.
     */
    private static Vec3d computeRayFaceHit(Vec3d eyePos, float yaw, float pitch,
                                            BlockPos neighbor, Direction face, World world) {
        // Ray direction from yaw/pitch
        float yawRad  = (float) Math.toRadians(-yaw - 180.0f);
        float pitchRad = (float) Math.toRadians(-pitch);
        float cosP = MathHelper.cos(pitchRad);
        Vec3d lookDir = new Vec3d(
                MathHelper.sin(yawRad) * cosP,
                MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * cosP
        );

        // Face plane: the face of the neighbor block
        // The face is on the surface of the neighbor block at `face` direction
        Vec3d faceCenter = Vec3d.ofCenter(neighbor)
                .add(Vec3d.of(face.getVector()).multiply(0.5));

        // Normal of the face
        Vec3d faceNormal = Vec3d.of(face.getVector());

        // Ray-plane intersection: t = dot(faceCenter - eyePos, normal) / dot(lookDir, normal)
        double denom = lookDir.dotProduct(faceNormal);
        if (Math.abs(denom) < 1e-6) {
            // Ray is nearly parallel to face — use face center
            return faceCenter;
        }

        double t = faceCenter.subtract(eyePos).dotProduct(faceNormal) / denom;
        if (t < 0) {
            // Intersection is behind the player — use face center
            return faceCenter;
        }

        Vec3d intersection = eyePos.add(lookDir.multiply(t));

        // Clamp to the face bounds (block goes from neighbor to neighbor+1)
        double hx = clampToFace(intersection.x, neighbor.getX(), face, Direction.Axis.X);
        double hy = clampToFace(intersection.y, neighbor.getY(), face, Direction.Axis.Y);
        double hz = clampToFace(intersection.z, neighbor.getZ(), face, Direction.Axis.Z);

        return new Vec3d(hx, hy, hz);
    }

    /**
     * Clamps a coordinate to the 0–1 range of the block, but fixes it to
     * the face boundary for the axis matching the face direction.
     */
    private static double clampToFace(double value, int blockOrigin, Direction face, Direction.Axis axis) {
        double min = blockOrigin;
        double max = blockOrigin + 1.0;

        if (face.getAxis() == axis) {
            // This axis is fixed to the face surface
            return face.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
        }

        // Clamp to block bounds with small inset to avoid exact edges
        return MathHelper.clamp(value, min + 0.01, max - 0.01);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  SNEAK PACKET HELPERS (separated for cross-tick timing)
    // ═══════════════════════════════════════════════════════════════════

    private static void pressSneakPacket(ClientPlayerEntity player) {
        /*? if >=1.21.8 {*//*
        player.networkHandler.sendPacket(
                new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, true, false)));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        /*?}*/
    }

    private static void releaseSneakPacket() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        /*? if >=1.21.8 {*//*
        mc.player.networkHandler.sendPacket(
                new PlayerInputC2SPacket(PlayerInput.DEFAULT));
        *//*?} else {*/
        mc.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        /*?}*/
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LOOK RESTORE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Smoothly restores the player's look direction after placement.
     * Since the actual restore is just resetting yaw/pitch, the next
     * mouse movement from the player will override anyway.
     */
    private static void restoreLook(ClientPlayerEntity player) {
        player.setYaw(savedYaw);
        player.setPitch(savedPitch);
    }

    // ── inventory helpers ───────────────────────────────────────────────

    /**
     * Selects the required item in the player's hotbar.
     *
     * @return {@code true} if the item is now in the selected hotbar slot
     */
    public static boolean selectItem(ClientPlayerEntity player, MinecraftClient mc,
                                     Item item, boolean allowSwap) {
        PlayerInventory inv = player.getInventory();

        // check current slot first
        /*? if >=1.21.5 {*//*
        if (inv.getStack(inv.getSelectedSlot()).getItem() == item) return true;
        *//*?} else {*/
        if (inv.getStack(inv.selectedSlot).getItem() == item) return true;
        /*?}*/

        // check rest of hotbar
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == item) {
                /*? if >=1.21.5 {*//*
                inv.setSelectedSlot(i);
                *//*?} else {*/
                inv.selectedSlot = i;
                /*?}*/
                return true;
            }
        }

        // check main inventory and swap if allowed
        if (allowSwap) {
            for (int i = 9; i < 36; i++) {
                if (inv.getStack(i).getItem() == item) {
                    mc.interactionManager.clickSlot(
                            player.currentScreenHandler.syncId,
                            i,
                            /*? if >=1.21.5 {*//*
                            inv.getSelectedSlot(),
                            *//*?} else {*/
                            inv.selectedSlot,
                            /*?}*/
                            SlotActionType.SWAP,
                            player
                    );
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Build a snapshot of all items in the player's inventory.
     *
     * @return map of Item → total count (across all slots)
     */
    public static Map<Item, Integer> getInventoryContents() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Map.of();
        PlayerInventory inv = mc.player.getInventory();
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return contents;
    }

    // ── placement face finding ──────────────────────────────────────────

    /**
     * Finds the best adjacent direction whose neighbour has a clickable solid
     * shape.  Prefers non-interactive neighbours.
     *
     * @return direction from {@code target} to the solid neighbour, or
     *         {@code null} if none exist
     */
    public static Direction findPlacementFace(World world, BlockPos target) {
        Direction fallback = null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = target.offset(dir);
            BlockState neighborState = world.getBlockState(neighbor);

            if (neighborState.isReplaceable()) continue;
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;

            if (!isInteractive(neighborState.getBlock())) {
                return dir;
            }
            if (fallback == null) fallback = dir;
        }
        return fallback;
    }

    /** Whether any adjacent block is solid (supports placement). */
    public static boolean hasAdjacentSolid(World world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = world.getBlockState(neighbor);
            if (!state.isReplaceable() &&
                    state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                return true;
            }
        }
        return false;
    }

    // ── interactive block detection ─────────────────────────────────────

    /** Set of block classes that open GUIs / handle interactions on right-click. */
    private static final Set<Class<? extends Block>> INTERACTIVE = Set.of(
            AbstractChestBlock.class,
            AbstractFurnaceBlock.class,
            AnvilBlock.class,
            BarrelBlock.class,
            BeaconBlock.class,
            BedBlock.class,
            BellBlock.class,
            BrewingStandBlock.class,
            ButtonBlock.class,
            CartographyTableBlock.class,
            CakeBlock.class,
            CommandBlock.class,
            ComparatorBlock.class,
            CraftingTableBlock.class,
            DoorBlock.class,
            DispenserBlock.class,
            DropperBlock.class,
            EnchantingTableBlock.class,
            FenceGateBlock.class,
            GrindstoneBlock.class,
            HopperBlock.class,
            JukeboxBlock.class,
            LecternBlock.class,
            LeverBlock.class,
            LoomBlock.class,
            NoteBlock.class,
            RepeaterBlock.class,
            ShulkerBoxBlock.class,
            SmithingTableBlock.class,
            StonecutterBlock.class,
            TrapdoorBlock.class
    );

    /**
     * Returns {@code true} if right-clicking the given block would open a GUI
     * or toggle state instead of placing against it.
     */
    public static boolean isInteractive(Block block) {
        for (Class<? extends Block> clazz : INTERACTIVE) {
            if (clazz.isInstance(block)) return true;
        }
        return false;
    }
}
