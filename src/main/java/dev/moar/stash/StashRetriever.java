package dev.moar.stash;

import dev.moar.MoarMod;
import dev.moar.chest.ChestManager;
import dev.moar.stash.StashDatabase.SearchResult;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.player.LocalPlayer;
*//*?} else {*/
import net.minecraft.client.network.ClientPlayerEntity;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.item.ItemStack;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ChestMenu;
*//*?} else {*/
import net.minecraft.screen.GenericContainerScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.AbstractContainerMenu;
*//*?} else {*/
import net.minecraft.screen.ScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ContainerInput;
*//*?} else {*/
import net.minecraft.screen.slot.SlotActionType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionHand;
*//*?} else {*/
import net.minecraft.util.Hand;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.BlockHitResult;
*//*?} else {*/
import net.minecraft.util.hit.BlockHitResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.Direction;
*//*?} else {*/
import net.minecraft.util.math.Direction;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.util.Mth;
*//*?} else {*/
import net.minecraft.util.math.MathHelper;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.Vec3;
*//*?} else {*/
import net.minecraft.util.math.Vec3d;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight state machine that walks to containers and retrieves items
 * into the player's inventory.  Driven by /stash get.
 */
public final class StashRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Retriever");

    private static final int OPEN_TIMEOUT_TICKS = 60;
    private static final int CLICK_COOLDOWN_TICKS = 3;
    private static final int HOTBAR_SIZE = 9;

    public enum State { IDLE, WALKING, OPENING, TAKING }

    private State state = State.IDLE;

    // Current retrieval target
    private String targetItemId;
    private int targetCount;
    private int takenCount;

    /** Kit-mode: item_id -> quantity still needed. null for single-item mode. */
    private Map<String, Integer> kitRemaining;

    // Walking / opening
    private final Deque<BlockPos> containerQueue = new ArrayDeque<>();
    private BlockPos walkTarget;
    private int openWaitTicks;
    private int actionSlotIndex;
    private int actionCooldown;

    // Public API

    public State getState() { return state; }
    public boolean isActive() { return state != State.IDLE; }

    /**
     * Start retrieval: find containers holding the item, queue them
     * closest-first, and begin walking.
     */
    public boolean start(String itemIdFragment, int count) {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) {
            ChatHelper.labelled("Stash", "§cDatabase not available.");
            return false;
        }

        List<SearchResult> results = db.searchItem(itemIdFragment);
        if (results.isEmpty()) {
            ChatHelper.labelled("Stash", "§cNo containers found with '§f" + itemIdFragment + "§c'.");
            return false;
        }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return false;

        // Resolve the exact item ID from the first result
        // (user may have passed a fragment like "diamond" — we need the full ID)
        String exactId = null;
        for (SearchResult sr : results) {
            for (String id : sr.matchedItems().keySet()) {
                if (id.contains(itemIdFragment)) {
                    exactId = id;
                    break;
                }
            }
            if (exactId != null) break;
        }
        if (exactId == null) exactId = results.getFirst().matchedItems().keySet().iterator().next();

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/

        // Sort results by distance to player
        String finalExactId = exactId;
        results.sort((a, b) -> {
            int qtyA = a.matchedItems().getOrDefault(finalExactId, 0);
            int qtyB = b.matchedItems().getOrDefault(finalExactId, 0);
            if (qtyA == 0 && qtyB > 0) return 1;
            if (qtyB == 0 && qtyA > 0) return -1;
            /*? if >=26.1 {*//*
            double distA = playerPos.distSqr(a.pos());
            double distB = playerPos.distSqr(b.pos());
            *//*?} else {*/
            double distA = playerPos.getSquaredDistance(a.pos());
            double distB = playerPos.getSquaredDistance(b.pos());
            /*?}*/
            return Double.compare(distA, distB);
        });

        containerQueue.clear();
        for (SearchResult sr : results) {
            if (sr.matchedItems().containsKey(exactId)) {
                containerQueue.add(sr.pos());
            }
        }

        if (containerQueue.isEmpty()) {
            ChatHelper.labelled("Stash", "§cNo containers hold exact item '§f" + exactId + "§c'.");
            return false;
        }

        targetItemId = exactId;
        targetCount = count;
        takenCount = 0;
        kitRemaining = null;

        advanceToNextContainer();
        String shortId = exactId.startsWith("minecraft:") ? exactId.substring(10) : exactId;
        ChatHelper.labelled("Stash", "§aRetrieving §f" + shortId + " §7x" + count
                + " §afrom " + containerQueue.size() + " container(s)...");
        return true;
    }

    /** Start kit-mode retrieval: find kit items in stash DB, walk to containers, collect. */
    public boolean startKit(String kitName, Map<String, Integer> kitItems) {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) {
            ChatHelper.labelled("Stash", "§cDatabase not available.");
            return false;
        }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null || mc.player == null) return false;

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/

        // Find containers holding kit items (incl. shulker contents).
        Map<BlockPos, Map<String, Integer>> containerMap = db.findContainersForExactItems(kitItems.keySet());
        Set<BlockPos> containers = containerMap.keySet();

        if (containers.isEmpty()) {
            ChatHelper.labelled("Stash", "§cNo containers found holding items from kit '§e" + kitName + "§c'.");
            return false;
        }

        // Sort by distance to player
        List<BlockPos> sorted = new java.util.ArrayList<>(containers);
        sorted.sort((a, b) -> {
            /*? if >=26.1 {*//*
            return Double.compare(playerPos.distSqr(a), playerPos.distSqr(b));
            *//*?} else {*/
            return Double.compare(playerPos.getSquaredDistance(a), playerPos.getSquaredDistance(b));
            /*?}*/
        });

        containerQueue.clear();
        containerQueue.addAll(sorted);

        kitRemaining = new LinkedHashMap<>(kitItems);
        targetItemId = null;
        targetCount = 0;
        takenCount = 0;

        advanceToNextContainer();
        ChatHelper.labelled("Stash", "§aLoading kit '§e" + kitName + "§a' (" + kitItems.size()
                + " items from " + containers.size() + " container(s))...");
        return true;
    }

    public void stop() {
        PathWalker.stop();
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc != null && mc.player != null) {
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
        }
        state = State.IDLE;
        containerQueue.clear();
        kitRemaining = null;
        ChatHelper.labelled("Stash", "§eRetrieval stopped.");
    }

    // Tick

    public void tick() {
        if (state == State.IDLE) return;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc == null || mc.player == null || mc.level == null) return;
        *//*?} else {*/
        if (mc == null || mc.player == null || mc.world == null) return;
        /*?}*/

        switch (state) {
            case WALKING -> tickWalking(mc);
            case OPENING -> tickOpening(mc);
            case TAKING  -> tickTaking(mc);
        }
    }

    // State handlers

    /*? if >=26.1 {*//*
    private void tickWalking(Minecraft mc) {
    *//*?} else {*/
    private void tickWalking(MinecraftClient mc) {
    /*?}*/
        if (walkTarget == null) { finish(); return; }

        /*? if >=26.1 {*//*
        double distSq = mc.player.position().distanceToSqr(
        *//*?} else {*/
        double distSq = mc.player.squaredDistanceTo(
        /*?}*/
                walkTarget.getX() + 0.5,
                walkTarget.getY() + 0.5,
                walkTarget.getZ() + 0.5);

        if (distSq <= 4.5 * 4.5) {
            PathWalker.stop();
            openWaitTicks = 0;
            state = State.OPENING;
            return;
        }

        if (!PathWalker.isActive()) {
            PathWalker.walkToAdjacent(walkTarget);
        }

        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            openWaitTicks = 0;
            state = State.OPENING;
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            ChatHelper.labelled("Stash", "§eCan't reach "
                    + walkTarget.getX() + " " + walkTarget.getY() + " " + walkTarget.getZ()
                    + ", trying next...");
            advanceToNextContainer();
        }

        PathWalker.tick();
    }

    /*? if >=26.1 {*//*
    private void tickOpening(Minecraft mc) {
    *//*?} else {*/
    private void tickOpening(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            actionSlotIndex = 0;
            actionCooldown = 0;
            state = State.TAKING;
            return;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(player, Vec3.atCenterOf(walkTarget));
            *//*?} else {*/
            lookAt(player, Vec3d.ofCenter(walkTarget));
            /*?}*/
        }

        if (openWaitTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(walkTarget);
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(walkTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toTarget = center.subtract(player.getEyePosition());
            *//*?} else {*/
            Vec3d toTarget = center.subtract(player.getEyePos());
            /*?}*/
            /*? if >=26.1 {*//*
            Direction hitFace = Direction.getApproximateNearest(
            *//*?} else {*/
            Direction hitFace = Direction.getFacing(
            /*?}*/
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);

            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(
            *//*?} else {*/
            mc.interactionManager.interactBlock(
            /*?}*/
                    player,
                    /*? if >=26.1 {*//*
                    InteractionHand.MAIN_HAND,
                    *//*?} else {*/
                    Hand.MAIN_HAND,
                    /*?}*/
                    new BlockHitResult(center, hitFace, walkTarget, false)
            );

            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Stash", "§eTimeout opening container, trying next...");
            advanceToNextContainer();
        }
    }

    /*? if >=26.1 {*//*
    private void tickTaking(Minecraft mc) {
    *//*?} else {*/
    private void tickTaking(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

        // Check if we've taken enough
        if (isDone()) {
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            finish();
            return;
        }

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (!(handler instanceof ChestMenu containerHandler)) {
        *//*?} else {*/
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
        /*?}*/
            advanceToNextContainer();
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlots = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = containerHandler.getRows() * 9;
        /*?}*/

        while (actionSlotIndex < chestSlots) {
            /*? if >=26.1 {*//*
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getItem();
            *//*?} else {*/
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getStack();
            /*?}*/
            if (!stack.isEmpty()) {
                String itemId = ItemIdentifier.getItemId(stack);
                boolean wanted = isWanted(itemId);

                // If not directly wanted, check if it's a shulker containing wanted items
                if (!wanted && ChestManager.isShulkerBox(stack)) {
                    Map<String, Integer> contents = ItemIdentifier.readShulkerContents(stack);
                    for (String innerItem : contents.keySet()) {
                        if (isWanted(innerItem)) {
                            wanted = true;
                            break;
                        }
                    }
                }

                if (wanted) {
                    if (!hasInventoryRoom(mc.player)) {
                        ChatHelper.labelled("Stash", "§eInventory full.");
                        /*? if >=26.1 {*//*
                        mc.player.clientSideCloseContainer();
                        *//*?} else {*/
                        mc.player.closeHandledScreen();
                        /*?}*/
                        state = State.IDLE;
                        return;
                    }

                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                    /*?}*/
                            /*? if >=26.1 {*//*
                            containerHandler.containerId,
                            *//*?} else {*/
                            containerHandler.syncId,
                            /*?}*/
                            actionSlotIndex,
                            0,
                            /*? if >=26.1 {*//*
                            ContainerInput.QUICK_MOVE,
                            *//*?} else {*/
                            SlotActionType.QUICK_MOVE,
                            /*?}*/
                            mc.player
                    );

                    // If we took a shulker, count the inner contents toward fulfillment
                    if (ChestManager.isShulkerBox(stack)) {
                        Map<String, Integer> contents = ItemIdentifier.readShulkerContents(stack);
                        for (var e : contents.entrySet()) {
                            if (isWanted(e.getKey())) {
                                recordTaken(e.getKey(), e.getValue());
                            }
                        }
                    } else {
                        recordTaken(itemId, stack.getCount());
                    }

                    actionSlotIndex++;
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    return;
                }
            }
            actionSlotIndex++;
        }

        // Exhausted this container — close and move on
        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        advanceToNextContainer();
    }

    // Helpers

    private void advanceToNextContainer() {
        BlockPos next = containerQueue.poll();
        if (next == null) {
            finish();
            return;
        }
        walkTarget = next;
        state = State.WALKING;
    }

    private void finish() {
        PathWalker.stop();
        state = State.IDLE;
        containerQueue.clear();
        if (kitRemaining != null) {
            if (kitRemaining.isEmpty()) {
                ChatHelper.labelled("Stash", "§aKit fully loaded.");
            } else {
                ChatHelper.labelled("Stash", "§eKit partially loaded. Still need:");
                for (var e : kitRemaining.entrySet()) {
                    String shortId = e.getKey().startsWith("minecraft:") ? e.getKey().substring(10) : e.getKey();
                    ChatHelper.labelled("Stash", " §c" + shortId + " §7x" + e.getValue());
                }
            }
            kitRemaining = null;
        } else {
            String shortId = targetItemId.startsWith("minecraft:") ? targetItemId.substring(10) : targetItemId;
            if (takenCount > 0) {
                ChatHelper.labelled("Stash", "§aRetrieved §f" + shortId + " §7x" + takenCount + "§a.");
            } else {
                ChatHelper.labelled("Stash", "§cCould not retrieve any §f" + shortId + "§c.");
            }
        }
    }

    /** True if itemId is still needed in current retrieval. */
    private boolean isWanted(String itemId) {
        if (kitRemaining != null) {
            return kitRemaining.containsKey(itemId) && kitRemaining.get(itemId) > 0;
        }
        return itemId.equals(targetItemId) && takenCount < targetCount;
    }

    /** Record that we took some items. */
    private void recordTaken(String itemId, int count) {
        if (kitRemaining != null) {
            int remaining = kitRemaining.getOrDefault(itemId, 0) - count;
            if (remaining <= 0) {
                kitRemaining.remove(itemId);
            } else {
                kitRemaining.put(itemId, remaining);
            }
        } else {
            takenCount += count;
        }
    }

    /** True when all target items have been collected. */
    private boolean isDone() {
        if (kitRemaining != null) {
            return kitRemaining.isEmpty();
        }
        return takenCount >= targetCount;
    }

    /*? if >=26.1 {*//*
    private boolean hasInventoryRoom(LocalPlayer player) {
    *//*?} else {*/
    private boolean hasInventoryRoom(ClientPlayerEntity player) {
    /*?}*/
        for (int i = HOTBAR_SIZE; i < 36; i++) {
            /*? if >=26.1 {*//*
            if (player.getInventory().getItem(i).isEmpty()) return true;
            *//*?} else {*/
            if (player.getInventory().getStack(i).isEmpty()) return true;
            /*?}*/
        }
        return false;
    }

    /*? if >=26.1 {*//*
    private static void lookAt(LocalPlayer player, Vec3 target) {
    *//*?} else {*/
    private static void lookAt(ClientPlayerEntity player, Vec3d target) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eye = player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toTarget = target.subtract(eye);
        *//*?} else {*/
        Vec3d toTarget = target.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        /*? if >=26.1 {*//*
        float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float yaw = (float) (MathHelper.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float pitch = (float) -(Mth.atan2(toTarget.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float pitch = (float) -(MathHelper.atan2(toTarget.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
        /*? if >=26.1 {*//*
        PlacementEngine.sendLookPacket(player, yaw, Mth.clamp(pitch, -90.0f, 90.0f));
        *//*?} else {*/
        PlacementEngine.sendLookPacket(player, yaw, MathHelper.clamp(pitch, -90.0f, 90.0f));
        /*?}*/
    }
}
