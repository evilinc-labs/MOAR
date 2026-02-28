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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Core automation engine for block placement tasks.
 *
 * <p>Provides rate-limited block placement, inventory management, adjacent-face
 * detection, and interactive-block sneaking.  Designed to stay under server
 * placement-rate limits (default 8 BPS, safely under the 9 BPS policy).
 */
public final class PlacementEngine {

    private PlacementEngine() {}

    // ── rate limiter ────────────────────────────────────────────────────

    private static int    bps               = 8;
    private static long   lastPlacementNano = 0;

    /** Set the maximum blocks-per-second rate (clamped 1–9). */
    public static void setBps(int value) {
        bps = Math.max(1, Math.min(9, value));
    }

    public static int getBps() { return bps; }

    /**
     * Returns {@code true} if enough time has passed since the last placement
     * to stay under the configured BPS ceiling.
     */
    public static boolean canPlace() {
        long nowNano = System.nanoTime();
        long intervalNano = 1_000_000_000L / bps;
        return (nowNano - lastPlacementNano) >= intervalNano;
    }

    /** Record a placement action (updates the rate-limiter timestamp). */
    public static void recordPlacement() {
        lastPlacementNano = System.nanoTime();
    }

    // ── block placement ─────────────────────────────────────────────────

    /**
     * Attempts to place a block at {@code target} matching {@code desired}.
     *
     * <ol>
     *   <li>Finds the correct item in the player's inventory</li>
     *   <li>Swaps it to the hotbar if needed</li>
     *   <li>Locates an adjacent solid face to click</li>
     *   <li>Sneaks if the neighbour is interactive</li>
     *   <li>Sends the interact-block packet</li>
     * </ol>
     *
     * @param target       world position to place at
     * @param desired      target block state (used to determine item)
     * @param allowSwap    whether to pull items from main inventory to hotbar
     * @return {@code true} if a placement packet was sent
     */
    public static boolean placeBlock(BlockPos target, BlockState desired, boolean allowSwap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;

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
        boolean needsSneak = isInteractive(neighborBlock);

        // ── 3. build hit result ─────────────────────────────────────────
        Direction clickSide = face.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(target)
                .add(Vec3d.of(face.getVector()).multiply(0.5));
        BlockHitResult hitResult = new BlockHitResult(hitPos, clickSide, neighbor, false);

        // ── 4. sneak if needed ──────────────────────────────────────────
        if (needsSneak) {
            /*? if >=1.21.8 {*//*
            player.networkHandler.sendPacket(
                    new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, true, false)));
            *//*?} else {*/
            player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            /*?}*/
        }

        // ── 5. interact ─────────────────────────────────────────────────
        ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
        }

        // ── 6. release sneak ────────────────────────────────────────────
        if (needsSneak) {
            /*? if >=1.21.8 {*//*
            player.networkHandler.sendPacket(
                    new PlayerInputC2SPacket(PlayerInput.DEFAULT));
            *//*?} else {*/
            player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            /*?}*/
        }

        recordPlacement();
        return true;
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
