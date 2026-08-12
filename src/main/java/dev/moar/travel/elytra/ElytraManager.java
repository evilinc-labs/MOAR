package dev.moar.travel.elytra;

import dev.moar.api.WebhookEvent;
import dev.moar.api.WebhookService;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.ItemEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
/*?}*/

import dev.moar.MoarMod;
import dev.moar.chest.ChestManager;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
import dev.moar.util.MoarNetworkManager;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;
import dev.moar.world.SetbackMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Manage travel equipment and resupply.
public final class ElytraManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Elytra");
    private static final Set<String> DISPOSABLE_TRAVEL_ITEM_IDS = Set.of(
            "minecraft:netherrack",
            "minecraft:cobblestone",
            "minecraft:stone",
            "minecraft:cobbled_deepslate",
            "minecraft:blackstone",
            "minecraft:gravel",
            "minecraft:dirt",
            "minecraft:end_stone"
    );

    public static final int LOW_DURABILITY_THRESHOLD = 5;
    public static final int FIREWORK_RESTOCK_THRESHOLD = 16;
    public static final int FIREWORK_RESTOCK_TARGET = 64;

    private static final int OPEN_TIMEOUT_TICKS      = 80;
    private static final int OPEN_RETRY_INTERVAL     = 14;
    private static final int CLICK_COOLDOWN          = 5;
    private static final int MEND_THROW_INTERVAL     = 4;
    private static final int MEND_MAX_TICKS          = 1800;
    private static final int PHASE_TIMEOUT           = 100;
    // Allow ender chest recovery on low-TPS servers.
    private static final int EC_BREAK_TIMEOUT        = 200;
    private static final int LOOK_SETTLE             = 4;
    private static final int SWAP_SETTLE             = 6;
    private static final int PLACE_RECENT_WINDOW     = 24;
    private static final int PLACE_STATIONARY_TICKS  = 5;
    private static final int MAX_SHULKER_FAILURES    = 2;
    private static final int PICKUP_WAIT_TICKS       = 25;

    public enum State {
        IDLE,
        CHECKING,
        SWAPPING_ELYTRA,
        MENDING,
        WALKING_TO_EC,
        OPENING_EC,
        FIND_SHULKER_EC,
        TAKING_SHULKER,
        PLACING_SHULKER,
        OPENING_SHULKER,
        TAKING_ELYTRA,
        EQUIPPING,
        BREAKING_SHULKER,
        WAITING_PICKUP,
        RETURNING_SHULKER,
        PLACING_EC,
        RECOVERING_EC,
        DONE,
        FAILED
    }

    private State state = State.IDLE;

    private BlockPos enderChestPos;
    private int stateTicks;
    private int actionCooldown;
    private int mendTicks;
    private int savedHotbarSlot = -1;
    private int openWaitTicks;
    private int openRetries;
    private boolean ecFromInventory = false;
    private int ecInvSlot = -1;
    private int shulkerPhase;
    private int shulkerTicks;
    private BlockPos shulkerPos;
    private int shulkerSlot = -1;
    private float savedYaw;
    private float savedPitch;
    private Runnable shulkerSneakRestore;

    // Avoid repeating a rejected armor swap.
    private boolean skipDirectEquip = false;
    private int equipSpareInvSlot = -1;
    private int shulkerFailures = 0;
    private final Set<Integer> preBreakShulkerSlots = new HashSet<>();
    private int recoveredShulkerSlot = -1;
    private boolean shulkerFetchedFromEc;
    private int returnPhase;
    private int returnTicks;
    private int elytraPickupCount;
    // Bound retrieval by inventory capacity.
    private int elytraTakeQuota = -1;
    private boolean mendingMode = false;
    private boolean fireworkMode = false;
    // Resume this state after shulker recovery.
    private State postShulkerState = State.DONE;
    private boolean disconnectOnFailure = true;

    public State getState()   { return state; }
    public boolean isDone()   { return state == State.DONE; }
    public boolean isFailed() { return state == State.FAILED; }
    public boolean isActive() { return state != State.IDLE && state != State.DONE && state != State.FAILED; }

    private boolean tryElytraInventory(int cooldownTicks) {
        return MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.INVENTORY,
                MoarNetworkManager.OWNER_ELYTRA, 1, cooldownTicks);
    }

    private boolean tryElytraInteraction(int cooldownTicks) {
        return MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.INTERACTION,
                MoarNetworkManager.OWNER_ELYTRA, 2, cooldownTicks);
    }

    private boolean tryElytraMining(int cooldownTicks) {
        return MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.MINING,
                MoarNetworkManager.OWNER_ELYTRA, 2, cooldownTicks);
    }

    public void setEnderChestPos(BlockPos pos) {
        /*? if >=26.1 {*//*
        this.enderChestPos = pos != null ? pos.immutable() : null;
        *//*?} else {*/
        this.enderChestPos = pos != null ? pos.toImmutable() : null;
        /*?}*/
    }

    public BlockPos getEnderChestPos() { return enderChestPos; }

    public void start() {
        resetAll();
        state = State.CHECKING;
        LOGGER.info("[Elytra] resupply started");
    }

    public void startRepair() {
        resetAll();
        mendingMode = true;
        state = State.CHECKING;
        LOGGER.info("[Elytra] repair started");
    }

    public void startFireworkRestock() {
        startFireworkRestock(false);
    }

    public void startFireworkRestockForTravel() {
        startFireworkRestock(true);
    }

    public void disconnectForTravelSafety(String reason) {
        resetAll();
        disconnectOnFailure = true;
        /*? if >=26.1 {*//*
        fail(Minecraft.getInstance(), reason);
        *//*?} else {*/
        fail(MinecraftClient.getInstance(), reason);
        /*?}*/
    }

    private void startFireworkRestock(boolean disconnectOnFailure) {
        resetAll();
        fireworkMode = true;
        this.disconnectOnFailure = disconnectOnFailure;
        state = State.CHECKING;
        LOGGER.info("[Elytra] firework restock started (disconnectOnFailure={})",
                disconnectOnFailure);
    }

    public void stop() {
        if (shulkerSneakRestore != null) {
            shulkerSneakRestore.run();
            shulkerSneakRestore = null;
        }
        PathWalker.stop();
        state = State.IDLE;
    }

    public void pause() {
        if (!isActive()) return;
        PathWalker.stop();
        if (shulkerSneakRestore != null) {
            shulkerSneakRestore.run();
            shulkerSneakRestore = null;
        }
        closeOpenContainers();
        checkpointForResume();
        actionCooldown = 0;
        stateTicks = 0;
    }

    public void resumeFromCheckpoint() {
        if (!isActive()) return;
        checkpointForResume();
        actionCooldown = 0;
        stateTicks = 0;
    }

    public void tick() {
        if (state == State.IDLE || state == State.DONE || state == State.FAILED) return;

        stateTicks++;
        if (actionCooldown > 0) { actionCooldown--; return; }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return;

        switch (state) {
            case CHECKING           -> tickChecking(mc);
            case SWAPPING_ELYTRA    -> tickSwappingElytra(mc);
            case MENDING            -> tickMending(mc);
            case WALKING_TO_EC      -> tickWalkingToEC();
            case OPENING_EC         -> tickOpeningEC(mc);
            case FIND_SHULKER_EC    -> tickFindShulkerEC(mc);
            case TAKING_SHULKER     -> tickTakingShulker(mc);
            case PLACING_SHULKER    -> tickPlacingShulker(mc);
            case OPENING_SHULKER    -> tickOpeningShulker(mc);
            case TAKING_ELYTRA      -> tickTakingElytra(mc);
            case EQUIPPING          -> tickEquipping(mc);
            case BREAKING_SHULKER   -> tickBreakingShulker(mc);
            case WAITING_PICKUP     -> tickWaitingPickup(mc);
            case RETURNING_SHULKER  -> tickReturningShulker(mc);
            case PLACING_EC         -> tickPlacingEC(mc);
            case RECOVERING_EC      -> tickRecoveringEC(mc);
            default                 -> {}
        }
    }

    /*? if >=26.1 {*//*
    private void tickChecking(Minecraft mc) {
    *//*?} else {*/
    private void tickChecking(MinecraftClient mc) {
    /*?}*/
        if (fireworkMode) {
            tickCheckingFireworks(mc);
            return;
        }

        // Keep repair-only runs on the Mending path.
        if (mendingMode) {
            ItemStack chestArmor = getChestStack(mc);
            if (chestArmor.isEmpty() || !hasEnchantment(chestArmor, "mending")) {
                fail(mc, "Worn elytra does not have the Mending enchantment");
                return;
            }
        }

        // Prefer an inventory spare unless direct swap failed.
        if (!skipDirectEquip && !mendingMode) {
            int spareSlot = findUsableElytraInInventory(mc);
            if (spareSlot >= 0) {
                LOGGER.info("[Elytra] spare elytra in slot {}, equipping directly", spareSlot);
                shulkerPhase = 0;
                shulkerSlot = spareSlot;
                transition(State.SWAPPING_ELYTRA);
                return;
            }
        } else {
            LOGGER.info("[Elytra] skipping direct-equip (timed out previously) — trying EC/shulker path");
            skipDirectEquip = false;
        }

        // Mend the worn elytra before replacing it.
        ItemStack chest = getChestStack(mc);
        if (!chest.isEmpty() && hasEnchantment(chest, "mending")) {
            int xpSlot = findItemInInventory(mc, "minecraft:experience_bottle");
            if (xpSlot >= 0) {
                LOGGER.info("[Elytra] elytra has Mending, XP bottles at slot {}", xpSlot);
                mendingMode = true;
                mendTicks = 0;
                transition(State.MENDING);
                return;
            }
            int xpShulkerSlot = findShulkerWithXpInInventory(mc);
            if (xpShulkerSlot >= 0) {
                LOGGER.info("[Elytra] elytra has Mending, shulker with XP at inventory slot {}", xpShulkerSlot);
                mendingMode = true;
                shulkerFetchedFromEc = false;
                shulkerSlot = xpShulkerSlot;
                shulkerPhase = 0;
                shulkerTicks = 0;
                transition(State.PLACING_SHULKER);
                return;
            }
            if (enderChestPos != null) {
                LOGGER.info("[Elytra] elytra has Mending, checking registered EC for XP shulker");
                mendingMode = true;
                transition(State.WALKING_TO_EC);
                return;
            }
            int ecItemSlot = findItemInInventory(mc, "minecraft:ender_chest");
            if (ecItemSlot >= 0) {
                LOGGER.info("[Elytra] elytra has Mending, placing inventory EC (slot {}) to fetch XP", ecItemSlot);
                mendingMode = true;
                ecFromInventory = true;
                ecInvSlot = ecItemSlot;
                shulkerPhase = 0;
                shulkerTicks = 0;
                transition(State.PLACING_EC);
                return;
            }
        }

        // Reuse an inventory shulker before visiting an ender chest.
        if (!mendingMode) {
            int shulkerInvSlot = findShulkerWithElytraInInventory(mc);
            if (shulkerInvSlot >= 0) {
                LOGGER.info("[Elytra] shulker with elytra already in inventory slot {}, placing directly",
                        shulkerInvSlot);
                shulkerFetchedFromEc = false;
                shulkerSlot = shulkerInvSlot;
                shulkerPhase = 0;
                shulkerTicks = 0;
                transition(State.PLACING_SHULKER);
                return;
            }
        }

        if (enderChestPos != null) {
            LOGGER.info("[Elytra] walking to ender chest at {}", enderChestPos.toShortString());
            transition(State.WALKING_TO_EC);
            return;
        }

        int ecItemSlot = findItemInInventory(mc, "minecraft:ender_chest");
        if (ecItemSlot >= 0) {
            LOGGER.info("[Elytra] ender chest item in inventory slot {}, will place it", ecItemSlot);
            ecFromInventory = true;
            ecInvSlot = ecItemSlot;
            shulkerPhase = 0;
            shulkerTicks = 0;
            transition(State.PLACING_EC);
            return;
        }

        LOGGER.warn("[Elytra] no viable options — {}", mendingMode ? "no XP found" : "disconnecting");
        fail(mc, mendingMode
                ? "No XP bottles found anywhere — cannot repair elytra"
                : "Out of elytra — no viable traveling materials");
    }

    /*? if >=26.1 {*//*
    private void tickCheckingFireworks(Minecraft mc) {
    *//*?} else {*/
    private void tickCheckingFireworks(MinecraftClient mc) {
    /*?}*/
        int rockets = countItemInInventory(mc, "minecraft:firework_rocket");
        if (rockets >= FIREWORK_RESTOCK_TARGET) {
            if (countItemInHotbar(mc, "minecraft:firework_rocket") >= FIREWORK_RESTOCK_THRESHOLD) {
                LOGGER.info("[Elytra] firework restock ready ({}, hotbar accessible)", rockets);
                transition(State.DONE);
                return;
            }
            int sourceSlot = findLargestItemSlot(mc, "minecraft:firework_rocket", 9, 36);
            int hotbarSlot = findTravelSupplyHotbarSlot(mc, "minecraft:firework_rocket");
            if (sourceSlot < 0 || hotbarSlot < 0) {
                fail(mc, "Fireworks are in inventory, but no safe hotbar slot is available");
                return;
            }
            stageInventorySlotInHotbar(mc, sourceSlot, hotbarSlot);
            return;
        }

        int rocketShulkerSlot = findShulkerWithFireworksInInventory(mc);
        if (rocketShulkerSlot >= 0) {
            LOGGER.info("[Elytra] firework shulker already in inventory slot {}", rocketShulkerSlot);
            shulkerFetchedFromEc = false;
            shulkerSlot = rocketShulkerSlot;
            shulkerPhase = 0;
            shulkerTicks = 0;
            transition(State.PLACING_SHULKER);
            return;
        }

        if (enderChestPos != null) {
            LOGGER.info("[Elytra] checking registered EC for firework shulker");
            transition(State.WALKING_TO_EC);
            return;
        }

        int ecItemSlot = findItemInInventory(mc, "minecraft:ender_chest");
        if (ecItemSlot >= 0) {
            LOGGER.info("[Elytra] placing inventory EC (slot {}) to fetch fireworks", ecItemSlot);
            ecFromInventory = true;
            ecInvSlot = ecItemSlot;
            shulkerPhase = 0;
            shulkerTicks = 0;
            transition(State.PLACING_EC);
            return;
        }

        fail(mc, "No fireworks found anywhere — cannot continue free-nether flight");
    }

    /*? if >=26.1 {*//*
    private void tickSwappingElytra(Minecraft mc) {
    *//*?} else {*/
    private void tickSwappingElytra(MinecraftClient mc) {
    /*?}*/
        // Confirm the previous swap before retrying.
        ItemStack chest = getChestStack(mc);
        if (!chest.isEmpty() && ItemIdentifier.getItemId(chest).equals("minecraft:elytra")
                && !isElytraLow(chest)) {
            LOGGER.info("[Elytra] spare elytra equipped successfully");
            ChatHelper.labelled("Travel", "§aElytra swapped — resuming.");
            transition(State.DONE);
            return;
        }

        if (stateTicks > PHASE_TIMEOUT) {
            // Fall back when the server rejects direct armor swaps.
            LOGGER.warn("[Elytra] equip timeout — falling back to EC/shulker path");
            skipDirectEquip = true;
            shulkerPhase = 0;
            shulkerSlot = -1;
            transition(State.CHECKING);
            return;
        }

        // Re-scan after server inventory corrections.
        int spareInvSlot = shulkerSlot;
        if (spareInvSlot < 0) {
            spareInvSlot = findUsableElytraInInventory(mc);
            if (spareInvSlot < 0) {
                transition(State.CHECKING);
                return;
            }
            shulkerSlot = spareInvSlot;
            shulkerPhase = 0;
        }
        int pshSlot = invSlotToPSHSlot(spareInvSlot);

        // Reuse the spare slot so swaps work with a full inventory.
        switch (shulkerPhase) {
            case 0 -> {
                LOGGER.info("[Elytra:swap] tick={} phase=0 — picking up spare psh={}", stateTicks, pshSlot);
                if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        mc.player.containerMenu.containerId, pshSlot, 0,
                        ContainerInput.PICKUP, mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, pshSlot, 0,
                        SlotActionType.PICKUP, mc.player);
                /*?}*/
                shulkerPhase = 1;
                actionCooldown = CLICK_COOLDOWN;
            }
            case 1 -> {
                LOGGER.info("[Elytra:swap] tick={} phase=1 — placing spare into chest slot 6", stateTicks);
                if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        mc.player.containerMenu.containerId, 6, 0,
                        ContainerInput.PICKUP, mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, 6, 0,
                        SlotActionType.PICKUP, mc.player);
                /*?}*/
                shulkerPhase = 2;
                actionCooldown = CLICK_COOLDOWN;
            }
            case 2 -> {
                LOGGER.info("[Elytra:swap] tick={} phase=2 — depositing old elytra at psh={}", stateTicks, pshSlot);
                if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        mc.player.containerMenu.containerId, pshSlot, 0,
                        ContainerInput.PICKUP, mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, pshSlot, 0,
                        SlotActionType.PICKUP, mc.player);
                /*?}*/
                shulkerPhase = 0;
                actionCooldown = CLICK_COOLDOWN;
            }
        }
    }

    /*? if >=26.1 {*//*
    private void tickMending(Minecraft mc) {
    *//*?} else {*/
    private void tickMending(MinecraftClient mc) {
    /*?}*/
        mendTicks++;

        // Reuse the armor swap phases for each damaged elytra.
        if (shulkerPhase > 0) {
            if (actionCooldown > 0) return;
            int pshSlot = invSlotToPSHSlot(shulkerSlot);
            switch (shulkerPhase) {
                case 1 -> {
                    LOGGER.info("[Elytra:mend-swap] phase=1 picking up damaged elytra psh={}", pshSlot);
                    if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                            mc.player.containerMenu.containerId, pshSlot, 0,
                            ContainerInput.PICKUP, mc.player);
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, pshSlot, 0,
                            SlotActionType.PICKUP, mc.player);
                    /*?}*/
                    shulkerPhase = 2;
                    actionCooldown = CLICK_COOLDOWN;
                }
                case 2 -> {
                    LOGGER.info("[Elytra:mend-swap] phase=2 placing into chest slot 6");
                    if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                            mc.player.containerMenu.containerId, 6, 0,
                            ContainerInput.PICKUP, mc.player);
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, 6, 0,
                            SlotActionType.PICKUP, mc.player);
                    /*?}*/
                    shulkerPhase = 3;
                    actionCooldown = CLICK_COOLDOWN;
                }
                case 3 -> {
                    LOGGER.info("[Elytra:mend-swap] phase=3 depositing mended elytra at psh={}", pshSlot);
                    if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                            mc.player.containerMenu.containerId, pshSlot, 0,
                            ContainerInput.PICKUP, mc.player);
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, pshSlot, 0,
                            SlotActionType.PICKUP, mc.player);
                    /*?}*/
                    shulkerPhase = 0;
                    mendTicks = 0;
                    actionCooldown = CLICK_COOLDOWN;
                }
            }
            return;
        }

        // Continue until the equipped elytra is fully repaired.
        ItemStack chest = getChestStack(mc);
        if (chest.isEmpty() || !ItemIdentifier.getItemId(chest).equals("minecraft:elytra")
                || isElytraFullyRepaired(chest)) {
            // Repair remaining damaged elytras in one session.
            int nextSlot = findDamagedMendableElytraInInventory(mc);
            if (nextSlot >= 0) {
                if (nextSlot == shulkerSlot) {
                    // Stop when a correction rejects the same swap.
                    LOGGER.warn("[Elytra] mend-swap for inv slot {} immediately re-selected after phase-3 — " +
                            "swap appears stuck (server reject?); skipping to DONE", shulkerSlot);
                    shulkerSlot = -1;
                } else {
                    LOGGER.info("[Elytra] worn elytra mended — swapping in next at inv slot {} for continued mending", nextSlot);
                    shulkerSlot = nextSlot;
                    shulkerPhase = 1;
                    actionCooldown = CLICK_COOLDOWN;
                    return;
                }
            }
            restoreHotbar(mc);
            LOGGER.info("[Elytra] all elytras mended after {} ticks", mendTicks);
            ChatHelper.labelled("Travel", "§aAll elytras mended — resuming.");
            transition(State.DONE);
            return;
        }

        // Fall back after the mending timeout.
        if (mendTicks >= MEND_MAX_TICKS) {
            restoreHotbar(mc);
            LOGGER.warn("[Elytra] Mending timed out after {} ticks", mendTicks);
            if (enderChestPos != null) {
                transition(State.WALKING_TO_EC);
            } else {
                int ecItemSlot = findItemInInventory(mc, "minecraft:ender_chest");
                if (ecItemSlot >= 0) {
                    LOGGER.info("[Elytra] Mending timed out, falling back to inventory EC at slot {}", ecItemSlot);
                    ecFromInventory = true;
                    ecInvSlot = ecItemSlot;
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    transition(State.PLACING_EC);
                } else {
                    fail(mc, "Mending timed out — no ender chest available");
                }
            }
            return;
        }

        // Find XP bottles
        int xpSlot = findItemInInventory(mc, "minecraft:experience_bottle");
        if (xpSlot < 0) {
            restoreHotbar(mc);
            LOGGER.warn("[Elytra] out of XP bottles for mending");
            // Reuse an inventory XP shulker before visiting the ender chest.
            int xpShulkerSlot = findShulkerWithXpInInventory(mc);
            if (xpShulkerSlot >= 0) {
                LOGGER.info("[Elytra] out of XP bottles — shulker with XP found at slot {}, placing", xpShulkerSlot);
                shulkerSlot = xpShulkerSlot;
                shulkerPhase = 0;
                shulkerTicks = 0;
                transition(State.PLACING_SHULKER);
                return;
            }
            if (enderChestPos != null) {
                transition(State.WALKING_TO_EC);
            } else {
                int ecItemSlot = findItemInInventory(mc, "minecraft:ender_chest");
                if (ecItemSlot >= 0) {
                    LOGGER.info("[Elytra] out of XP bottles, falling back to inventory EC at slot {}", ecItemSlot);
                    ecFromInventory = true;
                    ecInvSlot = ecItemSlot;
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    transition(State.PLACING_EC);
                } else {
                    fail(mc, "Out of XP bottles — no ender chest available");
                }
            }
            return;
        }

        // Wait for the hotbar swap before throwing.
        if (!ensureInHotbar(mc, xpSlot)) return;
        if (actionCooldown > 0) return;

        // Throw downward for immediate XP pickup.
        if (!tryElytraInteraction(MEND_THROW_INTERVAL)) return;
        /*? if >=26.1 {*//*
        float prevPitch = mc.player.getXRot();
        mc.player.setXRot(90.0f);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setXRot(prevPitch);
        *//*?} else {*/
        float prevPitch = mc.player.getPitch();
        mc.player.setPitch(90.0f);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.setPitch(prevPitch);
        /*?}*/
        actionCooldown = MEND_THROW_INTERVAL;
    }

    /*? if >=26.1 {*//*
    private void tickPlacingEC(Minecraft mc) {
    *//*?} else {*/
    private void tickPlacingEC(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        Level world = mc.level;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity player = mc.player;
        World world = mc.world;
        /*?}*/
        shulkerTicks++;

        switch (shulkerPhase) {

            // Find a safe ender chest placement.
            case 0 -> {
                if (!SetbackMonitor.get().isCalm()) return;
                if (!isPlacementWindowSafe()) return;

                // Re-scan after inventory movement.
                int slot = findItemInInventory(mc, "minecraft:ender_chest");
                if (slot < 0) { fail(mc, "Lost ender chest item before placement"); return; }
                ecInvSlot = slot;

                shulkerPos = findShulkerPlaceSpot(player, world);
                if (shulkerPos == null) { fail(mc, "No space to place ender chest"); return; }

                /*? if >=26.1 {*//*
                savedYaw = player.getYRot();
                savedPitch = player.getXRot();
                Vec3 preTarget = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                *//*?} else {*/
                savedYaw = player.getYaw();
                savedPitch = player.getPitch();
                Vec3d preTarget = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                /*?}*/
                lookAt(player, preTarget);
                shulkerPhase = 1;
                shulkerTicks = 0;
            }

            // Rotate, then select the ender chest.
            case 1 -> {
                /*? if >=26.1 {*//*
                Vec3 holdTarget = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                *//*?} else {*/
                Vec3d holdTarget = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                /*?}*/
                lookAt(player, holdTarget);
                if (shulkerTicks < LOOK_SETTLE) return;

                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                int hotbar = inv.getSelectedSlot();
                *//*?} else if >=1.21.5 {*//*
                PlayerInventory inv = player.getInventory();
                int hotbar = inv.getSelectedSlot();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                int hotbar = inv.selectedSlot;
                /*?}*/

                if (ecInvSlot >= 9) {
                    if (!tryElytraInventory(SWAP_SETTLE)) return;
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                            player.containerMenu.containerId, ecInvSlot, hotbar,
                            ContainerInput.SWAP, player);
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                            player.currentScreenHandler.syncId, ecInvSlot, hotbar,
                            SlotActionType.SWAP, player);
                    /*?}*/
                } else {
                    if (!tryElytraInventory(SWAP_SETTLE)) return;
                    /*? if >=1.21.5 {*/
                    inv.setSelectedSlot(ecInvSlot);
                    /*?} else if >=26.1 {*//*
                    inv.setSelectedSlot(ecInvSlot);
                    *//*?} else {*/
                    /*inv.selectedSlot = ecInvSlot;
                    *//*?}*/
                }
                shulkerPhase = 2;
                shulkerTicks = 0;
            }

            // Wait for swap acknowledgement before placing.
            case 2 -> {
                if (shulkerTicks < SWAP_SETTLE) return;
                if (!isPlacementWindowSafe()) {
                    if (shulkerTicks >= PHASE_TIMEOUT) { shulkerPhase = 0; shulkerTicks = 0; }
                    return;
                }
                if (!SetbackMonitor.get().isCalm()) { shulkerPhase = 0; shulkerTicks = 0; return; }

                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                ItemStack held = inv.getItem(inv.getSelectedSlot());
                *//*?} else if >=1.21.5 {*//*
                PlayerInventory inv = player.getInventory();
                ItemStack held = inv.getStack(inv.getSelectedSlot());
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                ItemStack held = inv.getStack(inv.selectedSlot);
                /*?}*/
                if (!ItemIdentifier.getItemId(held).equals("minecraft:ender_chest")) {
                    shulkerPhase = 0; shulkerTicks = 0; return;
                }

                /*? if >=26.1 {*//*
                Vec3 target = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                if (player.getEyePosition().distanceToSqr(target) > 4.5 * 4.5) {
                *//*?} else {*/
                Vec3d target = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                if (player.getEyePos().squaredDistanceTo(target) > 4.5 * 4.5) {
                /*?}*/
                    shulkerPhase = 0; shulkerTicks = 0; return;
                }

                lookAt(player, target);
                if (!tryElytraInteraction(2)) return;
                Runnable restoreSneak = PlacementEngine.ensureSneakForPlacement(player);
                BlockHitResult hit = new BlockHitResult(
                        target, Direction.UP,
                        /*? if >=26.1 {*//*
                        shulkerPos.below(),
                        *//*?} else {*/
                        shulkerPos.down(),
                        /*?}*/
                        false);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/
                shulkerSneakRestore = restoreSneak;
                shulkerPhase = 3;
                shulkerTicks = 0;
            }

            // Confirm placement from world state.
            case 3 -> {
                if (shulkerSneakRestore != null && shulkerTicks >= 1) {
                    shulkerSneakRestore.run();
                    shulkerSneakRestore = null;
                }
                BlockState st = world.getBlockState(shulkerPos);
                if (!st.isAir()) {
                    /*? if >=26.1 {*//*
                    enderChestPos = shulkerPos.immutable();
                    *//*?} else {*/
                    enderChestPos = shulkerPos.toImmutable();
                    /*?}*/
                    openWaitTicks = 0;
                    openRetries = 0;
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    transition(State.OPENING_EC);
                    return;
                }
                if (shulkerTicks >= PHASE_TIMEOUT) {
                    if (shulkerSneakRestore != null) { shulkerSneakRestore.run(); shulkerSneakRestore = null; }
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                }
            }
        }
    }

    private void tickWalkingToEC() {
        if (!PathWalker.isActive()) {
            PathWalker.walkToAdjacent(enderChestPos);
        }
        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            openWaitTicks = 0;
            openRetries = 0;
            transition(State.OPENING_EC);
            return;
        }
        if (PathWalker.isStuck()) {
            PathWalker.stop();
            LOGGER.warn("[Elytra] stuck walking to ender chest");
            /*? if >=26.1 {*//*
            fail(Minecraft.getInstance(), "Can't reach ender chest");
            *//*?} else {*/
            fail(MinecraftClient.getInstance(), "Can't reach ender chest");
            /*?}*/
        }
        PathWalker.tick();
    }

    /*? if >=26.1 {*//*
    private void tickOpeningEC(Minecraft mc) {
    *//*?} else {*/
    private void tickOpeningEC(MinecraftClient mc) {
    /*?}*/
        // Clear stale ender chest positions before opening.
        /*? if >=26.1 {*//*
        BlockState ecSt = mc.level.getBlockState(enderChestPos);
        if (!(ecSt.getBlock() instanceof EnderChestBlock)) {
        *//*?} else {*/
        BlockState ecSt = mc.world.getBlockState(enderChestPos);
        if (!(ecSt.getBlock() instanceof EnderChestBlock)) {
        /*?}*/
            LOGGER.warn("[Elytra] EC block at {} is gone/wrong ({}); clearing stale position",
                    enderChestPos, ecSt.getBlock());
            enderChestPos = null;
            ecFromInventory = false;
            transition(State.CHECKING);
            return;
        }

        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            // EC open → scan for shulker with elytra
            transition(State.FIND_SHULKER_EC);
            return;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (openWaitTicks == 1) {
            lookAtEnderChest(player);
        }

        if (openWaitTicks >= 3
                && (openWaitTicks == 3 || openWaitTicks % OPEN_RETRY_INTERVAL == 0)
                && SetbackMonitor.get().isCalm()) {
            interactWithEnderChest(mc, player);
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            LOGGER.warn("[Elytra] timeout opening ender chest");
            fail(mc, "Could not open ender chest");
        }
    }

    /*? if >=26.1 {*//*
    private void tickFindShulkerEC(Minecraft mc) {
    *//*?} else {*/
    private void tickFindShulkerEC(MinecraftClient mc) {
    /*?}*/
        if (stateTicks < 3) return;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (!(handler instanceof ChestMenu chestHandler)) {
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof GenericContainerScreenHandler chestHandler)) {
        /*?}*/
            // EC closed unexpectedly
            fail(mc, "Ender chest closed unexpectedly");
            return;
        }

        /*? if >=26.1 {*//*
        int ecSlots = chestHandler.getRowCount() * 9;
        *//*?} else {*/
        int ecSlots = chestHandler.getRows() * 9;
        /*?}*/

        // Select the first matching supply shulker.
        for (int slot = 0; slot < ecSlots; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = chestHandler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = chestHandler.getSlot(slot).getStack();
            /*?}*/
            if (!ChestManager.isShulkerBox(stack)) continue;
            if (!shulkerMatchesCurrentMode(stack)) continue;

            // Found — QUICK_MOVE it to player inventory
            if (!tryElytraInventory(CLICK_COOLDOWN)) return;
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
                    chestHandler.containerId, slot, 0,
                    ContainerInput.QUICK_MOVE, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(
                    chestHandler.syncId, slot, 0,
                    SlotActionType.QUICK_MOVE, mc.player);
            /*?}*/
            shulkerFetchedFromEc = true;
            transition(State.TAKING_SHULKER);
            actionCooldown = CLICK_COOLDOWN;
            return;
        }

        // No suitable shulker in EC
        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        fail(mc, "No " + currentShulkerLabel() + " found in ender chest");
    }

    /*? if >=26.1 {*//*
    private void tickTakingShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickTakingShulker(MinecraftClient mc) {
    /*?}*/
        // Wait for the container close acknowledgement.
        if (stateTicks < 3) return;

        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/

        // Confirm the shulker from inventory state.
        int slot = findCurrentModeShulkerInInventory(mc);
        if (slot < 0) {
            LOGGER.warn("[Elytra] shulker not found in inventory after taking");
            fail(mc, "Shulker did not transfer to inventory");
            return;
        }
        shulkerSlot = slot;
        LOGGER.info("[Elytra] shulker ({}) in slot {}", currentModeLabel(), shulkerSlot);

        shulkerPhase = 0;
        shulkerTicks = 0;
        transition(State.PLACING_SHULKER);
    }

    /*? if >=26.1 {*//*
    private void tickPlacingShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickPlacingShulker(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        Level world = mc.level;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity player = mc.player;
        World world = mc.world;
        /*?}*/
        shulkerTicks++;

        switch (shulkerPhase) {

            // Find a safe shulker placement.
            case 0 -> {
                if (!SetbackMonitor.get().isCalm()) return;
                if (!isPlacementWindowSafe()) return;

                if (shulkerFailures >= MAX_SHULKER_FAILURES) {
                    fail(mc, "Shulker placement failed too many times");
                    return;
                }

                shulkerSlot = findCurrentModeShulkerInInventory(mc);
                if (shulkerSlot < 0) {
                    fail(mc, "Lost shulker before placement");
                    return;
                }
                shulkerPos = findShulkerPlaceSpot(player, world);
                if (shulkerPos == null) {
                    fail(mc, "No space to place shulker");
                    return;
                }
                /*? if >=26.1 {*//*
                savedYaw = player.getYRot();
                savedPitch = player.getXRot();
                Vec3 preTarget = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                *//*?} else {*/
                savedYaw = player.getYaw();
                savedPitch = player.getPitch();
                Vec3d preTarget = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                /*?}*/
                lookAt(player, preTarget);
                shulkerPhase = 1;
                shulkerTicks = 0;
            }

            // Rotate, then select the shulker.
            case 1 -> {
                /*? if >=26.1 {*//*
                Vec3 holdTarget = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                *//*?} else {*/
                Vec3d holdTarget = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                /*?}*/
                lookAt(player, holdTarget);
                if (shulkerTicks < LOOK_SETTLE) return;

                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                int hotbar = inv.getSelectedSlot();
                *//*?} else if >=1.21.5 {*//*
                PlayerInventory inv = player.getInventory();
                int hotbar = inv.getSelectedSlot();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                int hotbar = inv.selectedSlot;
                /*?}*/

                if (shulkerSlot >= 9) {
                    if (!tryElytraInventory(SWAP_SETTLE)) return;
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                            player.containerMenu.containerId, shulkerSlot, hotbar,
                            ContainerInput.SWAP, player);
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                            player.currentScreenHandler.syncId, shulkerSlot, hotbar,
                            SlotActionType.SWAP, player);
                    /*?}*/
                } else {
                    if (!tryElytraInventory(SWAP_SETTLE)) return;
                    /*? if >=1.21.5 {*/
                    inv.setSelectedSlot(shulkerSlot);
                    /*?} else if >=26.1 {*//*
                    inv.setSelectedSlot(shulkerSlot);
                    *//*?} else {*/
                    /*inv.selectedSlot = shulkerSlot;
                    *//*?}*/
                }
                shulkerPhase = 2;
                shulkerTicks = 0;
            }

            // Wait for swap acknowledgement before placing.
            case 2 -> {
                if (shulkerTicks < SWAP_SETTLE) return;
                if (!isPlacementWindowSafe()) {
                    if (shulkerTicks >= PHASE_TIMEOUT) { shulkerFailures++; shulkerPhase = 0; shulkerTicks = 0; }
                    return;
                }
                if (!SetbackMonitor.get().isCalm()) { shulkerFailures++; shulkerPhase = 0; shulkerTicks = 0; return; }

                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                ItemStack held = inv.getItem(inv.getSelectedSlot());
                *//*?} else if >=1.21.5 {*//*
                PlayerInventory inv = player.getInventory();
                ItemStack held = inv.getStack(inv.getSelectedSlot());
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                ItemStack held = inv.getStack(inv.selectedSlot);
                /*?}*/
                if (!ChestManager.isShulkerBox(held)) { shulkerFailures++; shulkerPhase = 0; shulkerTicks = 0; return; }

                /*? if >=26.1 {*//*
                Vec3 target = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                if (player.getEyePosition().distanceToSqr(target) > 4.5 * 4.5) {
                *//*?} else {*/
                Vec3d target = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                if (player.getEyePos().squaredDistanceTo(target) > 4.5 * 4.5) {
                /*?}*/
                    shulkerFailures++; shulkerPhase = 0; shulkerTicks = 0; return;
                }

                lookAt(player, target);
                if (!tryElytraInteraction(2)) return;
                Runnable restoreSneak = PlacementEngine.ensureSneakForPlacement(player);
                BlockHitResult hit = new BlockHitResult(
                        target, Direction.UP,
                        /*? if >=26.1 {*//*
                        shulkerPos.below(),
                        *//*?} else {*/
                        shulkerPos.down(),
                        /*?}*/
                        false);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/
                shulkerSneakRestore = restoreSneak;
                shulkerPhase = 3;
                shulkerTicks = 0;
            }

            // Confirm placement from world state.
            case 3 -> {
                if (shulkerSneakRestore != null && shulkerTicks >= 1) {
                    shulkerSneakRestore.run();
                    shulkerSneakRestore = null;
                }
                BlockState st = world.getBlockState(shulkerPos);
                if (st.getBlock() instanceof ShulkerBoxBlock) {
                    shulkerFailures = 0;
                    transition(State.OPENING_SHULKER);
                    shulkerPhase = 4;
                    shulkerTicks = 0;
                    return;
                }
                if (shulkerTicks >= PHASE_TIMEOUT) {
                    if (shulkerSneakRestore != null) { shulkerSneakRestore.run(); shulkerSneakRestore = null; }
                    shulkerFailures++;
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                }
            }
        }
    }

    /*? if >=26.1 {*//*
    private void tickOpeningShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickOpeningShulker(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        Level world = mc.level;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        World world = mc.world;
        /*?}*/
        shulkerTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = player.containerMenu;
        if (handler instanceof ShulkerBoxMenu) {
        *//*?} else {*/
        ScreenHandler handler = player.currentScreenHandler;
        if (handler instanceof ShulkerBoxScreenHandler) {
        /*?}*/
            transition(State.TAKING_ELYTRA);
            shulkerTicks = 0;
            return;
        }

        // Retry immediately when the placed shulker disappears.
        BlockState shulkerSt = world.getBlockState(shulkerPos);
        if (!(shulkerSt.getBlock() instanceof ShulkerBoxBlock)) {
            openRetries++;
            if (openRetries < 3) {
                LOGGER.warn("[Elytra] shulker block gone during opening (attempt {}), replaying placement",
                        openRetries);
                shulkerPhase = 0;
                shulkerTicks = 0;
                transition(State.PLACING_SHULKER);
            } else {
                fail(mc, "Shulker block disappeared after placement");
            }
            return;
        }

        // Rotate before opening the shulker.
        /*? if >=26.1 {*//*
        Vec3 center = Vec3.atCenterOf(shulkerPos);
        *//*?} else {*/
        Vec3d center = Vec3d.ofCenter(shulkerPos);
        /*?}*/
        lookAt(player, center);

        if (shulkerTicks == 1 || shulkerTicks % OPEN_RETRY_INTERVAL == 0) {
            if (!tryElytraInteraction(2)) return;
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
            /*? if >=26.1 {*//*
            Vec3 toShulker = center.subtract(player.getEyePosition());
            Direction hitFace = Direction.getApproximateNearest(
                    (float) -toShulker.x, (float) -toShulker.y, (float) -toShulker.z);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(center, hitFace, shulkerPos, false));
            *//*?} else {*/
            Vec3d toShulker = center.subtract(player.getEyePos());
            Direction hitFace = Direction.getFacing(
                    (float) -toShulker.x, (float) -toShulker.y, (float) -toShulker.z);
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                    new BlockHitResult(center, hitFace, shulkerPos, false));
            /*?}*/
            restoreSneak.run();
        }

        if (shulkerTicks >= PHASE_TIMEOUT) {
            openRetries++;
            if (openRetries < 3) {
                LOGGER.warn("[Elytra] shulker open timeout (attempt {}), replaying placement",
                        openRetries);
                shulkerPhase = 0;
                shulkerTicks = 0;
                transition(State.PLACING_SHULKER);
            } else {
                fail(mc, "Could not open placed shulker");
            }
        }
    }

    /*? if >=26.1 {*//*
    private void tickTakingElytra(Minecraft mc) {
    *//*?} else {*/
    private void tickTakingElytra(MinecraftClient mc) {
    /*?}*/
        if (stateTicks < 6) return;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (!(handler instanceof ShulkerBoxMenu shulkerHandler)) {
            // Not open — retry opening
            transition(State.OPENING_SHULKER);
            shulkerTicks = 0;
            return;
        }
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof ShulkerBoxScreenHandler shulkerHandler)) {
            transition(State.OPENING_SHULKER);
            shulkerTicks = 0;
            return;
        }
        /*?}*/

        int target = currentTargetCount();
        String wantedItemId = currentWantedItemId();

        // Trust inventory state because quick-move can fail silently.
        int alreadyHave = countCurrentSupplies(mc);
        int need = Math.max(0, target - alreadyHave);

        if (need > 0 && countInventoryInsertCapacity(mc, wantedItemId) == 0) {
            int droppedSlot = dropOneDisposableStackFromShulkerScreen(mc, wantedItemId);
            if (droppedSlot >= 0) {
                LOGGER.warn("[Elytra] inventory full while taking {} — dropped disposable stack from inv slot {}",
                        currentPickupLabel(), droppedSlot);
                actionCooldown = CLICK_COOLDOWN;
                return;
            }
        }

        // Limit retrieval to remaining inventory capacity.
        if (elytraTakeQuota < 0) {
            int insertCapacity = countInventoryInsertCapacity(mc, wantedItemId);
            elytraTakeQuota = Math.min(need, insertCapacity);
            LOGGER.info("[Elytra] resupply quota: have={} target={} need={} insertCapacity={} → quota={}",
                    alreadyHave, target, need, insertCapacity, elytraTakeQuota);
            if (elytraTakeQuota == 0 && alreadyHave == 0) {
                // Fail before the next state stalls on a full inventory.
                /*? if >=26.1 {*//*
                mc.player.clientSideCloseContainer();
                *//*?} else {*/
                mc.player.closeHandledScreen();
                /*?}*/
                fail(mc, fireworkMode
                        ? "Inventory full — no room for fireworks"
                        : mendingMode
                        ? "Inventory full — no room for XP bottles"
                        : "Inventory full — clear a slot before elytra resupply");
                return;
            }
        }

        // Stop after meeting the target or quota.
        if (alreadyHave >= target || elytraPickupCount >= elytraTakeQuota) {
            LOGGER.info("[Elytra] resupply done (have={}/{}, sent={}/{})",
                    alreadyHave, target, elytraPickupCount, elytraTakeQuota);
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            finishShulkerExtraction(mc);
            return;
        }

        if (stateTicks > PHASE_TIMEOUT) {
            if (alreadyHave == 0) {
                /*? if >=26.1 {*//*
                mc.player.clientSideCloseContainer();
                *//*?} else {*/
                mc.player.closeHandledScreen();
                /*?}*/
                fail(mc, fireworkMode
                        ? "Timed out taking fireworks from shulker"
                        : mendingMode
                        ? "Timed out taking XP from shulker"
                        : "Timed out while taking elytra from shulker");
            } else {
                LOGGER.warn("[Elytra] TAKING_ELYTRA timeout — proceeding with {}/{}", alreadyHave, target);
                /*? if >=26.1 {*//*
                mc.player.clientSideCloseContainer();
                *//*?} else {*/
                mc.player.closeHandledScreen();
                /*?}*/
                finishShulkerExtraction(mc);
            }
            return;
        }

        // Find the next required item.
        int elytraShulkerSlot = -1;
        for (int slot = 0; slot < 27; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = shulkerHandler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = shulkerHandler.getSlot(slot).getStack();
            /*?}*/
            if (ItemIdentifier.getItemId(stack).equals(wantedItemId)
                    && ((mendingMode || fireworkMode) || !isElytraLow(stack))) {
                elytraShulkerSlot = slot;
                break;
            }
        }

        if (elytraShulkerSlot < 0) {
            // Continue with supplies already collected.
            if (alreadyHave == 0) {
                /*? if >=26.1 {*//*
                mc.player.clientSideCloseContainer();
                *//*?} else {*/
                mc.player.closeHandledScreen();
                /*?}*/
                fail(mc, fireworkMode
                        ? "No fireworks found in shulker"
                        : mendingMode
                        ? "No XP bottles found in shulker"
                        : "No usable elytra found in shulker");
                return;
            }
            LOGGER.info("[Elytra] shulker exhausted — proceeding with {}/{}", alreadyHave, target);
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            actionCooldown = CLICK_COOLDOWN;
            finishShulkerExtraction(mc);
            return;
        }

        // Move one item into inventory.
        LOGGER.info("[Elytra] taking {} from shulker slot {} ({}/{})",
                currentPickupLabel(),
                elytraShulkerSlot, elytraPickupCount + 1, elytraTakeQuota);
        if (!tryElytraInventory(CLICK_COOLDOWN)) return;
        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(
                shulkerHandler.containerId, elytraShulkerSlot, 0,
                ContainerInput.QUICK_MOVE, mc.player);
        *//*?} else {*/
        mc.interactionManager.clickSlot(
                shulkerHandler.syncId, elytraShulkerSlot, 0,
                SlotActionType.QUICK_MOVE, mc.player);
        /*?}*/
        elytraPickupCount++;
        actionCooldown = CLICK_COOLDOWN;
    }

    /*? if >=26.1 {*//*
    private void tickEquipping(Minecraft mc) {
    *//*?} else {*/
    private void tickEquipping(MinecraftClient mc) {
    /*?}*/
        // Check if chest slot now has a fresh elytra
        ItemStack chest = getChestStack(mc);
        if (!chest.isEmpty() && ItemIdentifier.getItemId(chest).equals("minecraft:elytra")
                && !isElytraLow(chest)) {
            LOGGER.info("[Elytra] fresh elytra equipped");
            ChatHelper.labelled("Travel", "§aNew elytra equipped — resuming.");
            // Record shulker slots before breaking so we can track the drop
            snapshotShulkerSlots(mc);
            postShulkerState = State.DONE;
            shulkerPhase = 6;
            shulkerTicks = 0;
            transition(State.BREAKING_SHULKER);
            return;
        }

        if (stateTicks > PHASE_TIMEOUT) {
            fail(mc, "Could not equip elytra from shulker");
            return;
        }

        // Reuse the spare slot when inventory is full.
        switch (shulkerPhase) {
            case 0 -> {
                // Wait for the quick-move acknowledgement.
                int spareSlot = findUsableElytraInInventory(mc);
                if (spareSlot < 0) {
                    LOGGER.debug("[Elytra] waiting for elytra to arrive in inventory (tick {})", stateTicks);
                    return;
                }
                equipSpareInvSlot = spareSlot;
                int pshSlot = invSlotToPSHSlot(spareSlot);
                LOGGER.info("[Elytra:equip] tick={} phase=0 — picking up spare psh={}", stateTicks, pshSlot);
                if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        mc.player.containerMenu.containerId, pshSlot, 0,
                        ContainerInput.PICKUP, mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, pshSlot, 0,
                        SlotActionType.PICKUP, mc.player);
                /*?}*/
                shulkerPhase = 1;
                actionCooldown = CLICK_COOLDOWN;
            }
            case 1 -> {
                LOGGER.info("[Elytra:equip] tick={} phase=1 — placing spare into chest slot 6", stateTicks);
                if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        mc.player.containerMenu.containerId, 6, 0,
                        ContainerInput.PICKUP, mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, 6, 0,
                        SlotActionType.PICKUP, mc.player);
                /*?}*/
                shulkerPhase = 2;
                actionCooldown = CLICK_COOLDOWN;
            }
            case 2 -> {
                int pshSlot = invSlotToPSHSlot(equipSpareInvSlot);
                LOGGER.info("[Elytra:equip] tick={} phase=2 — depositing old elytra at psh={}", stateTicks, pshSlot);
                if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        mc.player.containerMenu.containerId, pshSlot, 0,
                        ContainerInput.PICKUP, mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId, pshSlot, 0,
                        SlotActionType.PICKUP, mc.player);
                /*?}*/
                shulkerPhase = 0;
                equipSpareInvSlot = -1;
                actionCooldown = CLICK_COOLDOWN;
            }
        }
    }

    /*? if >=26.1 {*//*
    private void tickBreakingShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickBreakingShulker(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        Level world = mc.level;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity player = mc.player;
        World world = mc.world;
        /*?}*/
        shulkerTicks++;

        switch (shulkerPhase) {

            // Start breaking the shulker.
            case 6 -> {
                /*? if >=26.2 {*//*
                if (mc.gui.screen() != null) { player.clientSideCloseContainer(); return; }
                *//*?} else if >=26.1 {*//*
                if (mc.screen != null) { player.clientSideCloseContainer(); return; }
                *//*?} else {*/
                if (mc.currentScreen != null) { player.closeHandledScreen(); return; }
                /*?}*/
                BlockState st = world.getBlockState(shulkerPos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    shulkerPhase = 8; shulkerTicks = 0; return;
                }
                PlacementEngine.selectBestTool(player, mc, st);
                if (!tryElytraMining(1)) return;
                /*? if >=26.1 {*//*
                lookAt(player, Vec3.atCenterOf(shulkerPos));
                mc.gameMode.startDestroyBlock(shulkerPos, Direction.UP);
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                lookAt(player, Vec3d.ofCenter(shulkerPos));
                mc.interactionManager.attackBlock(shulkerPos, Direction.UP);
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
                shulkerPhase = 7;
                shulkerTicks = 0;
            }

            // Continue until world state confirms removal.
            case 7 -> {
                BlockState st = world.getBlockState(shulkerPos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    player.setYRot(savedYaw);
                    player.setXRot(savedPitch);
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    player.setYaw(savedYaw);
                    player.setPitch(savedPitch);
                    /*?}*/
                    shulkerPhase = 8;
                    shulkerTicks = 0;
                    return;
                }
                if (shulkerTicks >= PHASE_TIMEOUT) {
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    /*?}*/
                    fail(mc, "Could not break placed shulker");
                    return;
                }
                if (!tryElytraMining(1)) return;
                /*? if >=26.1 {*//*
                lookAt(player, Vec3.atCenterOf(shulkerPos));
                mc.gameMode.continueDestroyBlock(shulkerPos, Direction.UP);
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                lookAt(player, Vec3d.ofCenter(shulkerPos));
                mc.interactionManager.updateBlockBreakingProgress(shulkerPos, Direction.UP);
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
            }

            // Wait for the item drop.
            case 8 -> {
                if (shulkerTicks >= PICKUP_WAIT_TICKS) {
                    transition(State.WAITING_PICKUP);
                }
            }
        }
    }

    /*? if >=26.1 {*//*
    private void tickWaitingPickup(Minecraft mc) {
    *//*?} else {*/
    private void tickWaitingPickup(MinecraftClient mc) {
    /*?}*/
        // Detect pickup against the pre-break snapshot.
        int newSlot = findNewShulkerSlot(mc);
        if (newSlot >= 0) {
            recoveredShulkerSlot = newSlot;
            LOGGER.info("[Elytra] recovered shulker at slot {}", recoveredShulkerSlot);
            queuePostPickupState();
            return;
        }

        if (stateTicks >= PICKUP_WAIT_TICKS + 60) {
            // Continue when snapshot matching misses the pickup.
            int anyShulker = findAnyShulkerInInventory(mc);
            if (anyShulker >= 0) {
                recoveredShulkerSlot = anyShulker;
                LOGGER.info("[Elytra] using shulker at slot {} (snapshot miss)", recoveredShulkerSlot);
                queuePostPickupState();
            } else {
                LOGGER.warn("[Elytra] shulker drop not picked up — skipping return");
                transition(postShulkerState);
            }
        }
    }

    /*? if >=26.1 {*//*
    private void tickReturningShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickReturningShulker(MinecraftClient mc) {
    /*?}*/
        returnTicks++;

        switch (returnPhase) {

            // Walk to EC
            case 0 -> {
                if (enderChestPos == null) {
                    // Keep inventory-sourced shulkers in inventory.
                    LOGGER.info("[Elytra] no EC registered — leaving recovered shulker in inventory");
                    transition(postShulkerState);
                    return;
                }
                if (!PathWalker.isActive()) PathWalker.walkToAdjacent(enderChestPos);
                if (PathWalker.hasArrived()) {
                    PathWalker.stop();
                    openWaitTicks = 0;
                    openRetries = 0;
                    returnPhase = 1;
                    returnTicks = 0;
                    return;
                }
                if (PathWalker.isStuck()) {
                    PathWalker.stop();
                    LOGGER.warn("[Elytra] stuck walking back to EC for shulker return");
                    transition(postShulkerState);
                    return;
                }
                PathWalker.tick();
            }

            // Open EC
            case 1 -> {
                openWaitTicks++;

                /*? if >=26.1 {*//*
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (handler instanceof ChestMenu) {
                *//*?} else {*/
                ScreenHandler handler = mc.player.currentScreenHandler;
                if (handler instanceof GenericContainerScreenHandler) {
                /*?}*/
                    returnPhase = 2;
                    returnTicks = 0;
                    return;
                }

                /*? if >=26.1 {*//*
                LocalPlayer player = mc.player;
                *//*?} else {*/
                ClientPlayerEntity player = mc.player;
                /*?}*/
                if (openWaitTicks == 1) {
                    lookAtEnderChest(player);
                }
                if (openWaitTicks >= 3
                        && (openWaitTicks == 3 || openWaitTicks % OPEN_RETRY_INTERVAL == 0)
                        && SetbackMonitor.get().isCalm()) {
                    interactWithEnderChest(mc, player);
                }
                if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
                    LOGGER.warn("[Elytra] timeout re-opening EC for shulker return — giving up");
                    transition(postShulkerState);
                }
            }

            // Deposit shulker
            case 2 -> {
                if (returnTicks < 3) return;

                /*? if >=26.1 {*//*
                AbstractContainerMenu handler = mc.player.containerMenu;
                if (!(handler instanceof ChestMenu chestHandler)) {
                *//*?} else {*/
                ScreenHandler handler = mc.player.currentScreenHandler;
                if (!(handler instanceof GenericContainerScreenHandler chestHandler)) {
                /*?}*/
                    // Keep the shulker when the container closes early.
                    LOGGER.warn("[Elytra] EC closed before shulker deposit");
                    transition(State.DONE);
                    return;
                }

                // Re-scan after inventory movement.
                int shulkerInv = recoveredShulkerSlot >= 0
                        ? recoveredShulkerSlot
                        : findAnyShulkerInInventory(mc);
                if (shulkerInv < 0) {
                    /*? if >=26.1 {*//*
                    mc.player.clientSideCloseContainer();
                    *//*?} else {*/
                    mc.player.closeHandledScreen();
                    /*?}*/
                    transition(State.DONE);
                    return;
                }

                // Map inventory slots into the open container.
                /*? if >=26.1 {*//*
                int ecSlots = chestHandler.getRowCount() * 9;
                *//*?} else {*/
                int ecSlots = chestHandler.getRows() * 9;
                /*?}*/
                int handlerSlot = invSlotToECHandlerSlot(shulkerInv, ecSlots);

                if (!tryElytraInventory(CLICK_COOLDOWN)) return;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        chestHandler.containerId, handlerSlot, 0,
                        ContainerInput.QUICK_MOVE, mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        chestHandler.syncId, handlerSlot, 0,
                        SlotActionType.QUICK_MOVE, mc.player);
                /*?}*/

                actionCooldown = CLICK_COOLDOWN;
                /*? if >=26.1 {*//*
                mc.player.clientSideCloseContainer();
                *//*?} else {*/
                mc.player.closeHandledScreen();
                /*?}*/
                LOGGER.info("[Elytra] shulker deposited back to ender chest");
                if (ecFromInventory) {
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    transition(State.RECOVERING_EC);
                } else {
                    transition(postShulkerState);
                }
            }
        }
    }

    // Leave the ender chest in place without Silk Touch.
    /*? if >=26.1 {*//*
    private void tickRecoveringEC(Minecraft mc) {
    *//*?} else {*/
    private void tickRecoveringEC(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        Level world = mc.level;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity player = mc.player;
        World world = mc.world;
        /*?}*/
        shulkerTicks++;

        switch (shulkerPhase) {

            // Select a Silk Touch pickaxe.
            case 0 -> {
                int stSlot = findSilkTouchPickaxe(mc);
                if (stSlot < 0) {
                    LOGGER.info("[Elytra] no Silk Touch pickaxe; placed EC remains at {}",
                            enderChestPos == null ? "null" : enderChestPos.toShortString());
                    transition(postShulkerState);
                    return;
                }

                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                int hotbar = inv.getSelectedSlot();
                *//*?} else if >=1.21.5 {*//*
                PlayerInventory inv = player.getInventory();
                int hotbar = inv.getSelectedSlot();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                int hotbar = inv.selectedSlot;
                /*?}*/

                if (stSlot >= 9) {
                    if (!tryElytraInventory(SWAP_SETTLE)) return;
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                            player.containerMenu.containerId, stSlot, hotbar,
                            ContainerInput.SWAP, player);
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                            player.currentScreenHandler.syncId, stSlot, hotbar,
                            SlotActionType.SWAP, player);
                    /*?}*/
                } else {
                    if (!tryElytraInventory(SWAP_SETTLE)) return;
                    /*? if >=1.21.5 {*/
                    inv.setSelectedSlot(stSlot);
                    /*?} else if >=26.1 {*//*
                    inv.setSelectedSlot(stSlot);
                    *//*?} else {*/
                    /*inv.selectedSlot = stSlot;
                    *//*?}*/
                }

                /*? if >=26.1 {*//*
                savedYaw = player.getYRot();
                savedPitch = player.getXRot();
                *//*?} else {*/
                savedYaw = player.getYaw();
                savedPitch = player.getPitch();
                /*?}*/
                shulkerPhase = 1;
                shulkerTicks = 0;
                actionCooldown = SWAP_SETTLE;
            }

            // Start breaking the ender chest.
            case 1 -> {
                if (enderChestPos == null) { transition(State.DONE); return; }
                BlockState st = world.getBlockState(enderChestPos);
                if (st.isAir()) { shulkerPhase = 3; shulkerTicks = 0; return; }

                if (!tryElytraMining(1)) return;
                /*? if >=26.1 {*//*
                lookAt(player, Vec3.atCenterOf(enderChestPos));
                mc.gameMode.startDestroyBlock(enderChestPos, Direction.UP);
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                lookAt(player, Vec3d.ofCenter(enderChestPos));
                mc.interactionManager.attackBlock(enderChestPos, Direction.UP);
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
                shulkerPhase = 2;
                shulkerTicks = 0;
            }

            // Continue until world state confirms removal.
            case 2 -> {
                BlockState st = world.getBlockState(enderChestPos);
                if (st.isAir()) {
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    player.setYRot(savedYaw); player.setXRot(savedPitch);
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    player.setYaw(savedYaw); player.setPitch(savedPitch);
                    /*?}*/
                    shulkerPhase = 3;
                    shulkerTicks = 0;
                    return;
                }
                if (shulkerTicks >= EC_BREAK_TIMEOUT) {
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    /*?}*/
                    LOGGER.warn("[Elytra] timed out breaking placed EC at {}; abandoning position to prevent loop",
                            enderChestPos);
                    // Clear the stale position after timeout.
                    enderChestPos = null;
                    ecFromInventory = false;
                    transition(postShulkerState);
                    return;
                }
                if (!tryElytraMining(1)) return;
                /*? if >=26.1 {*//*
                lookAt(player, Vec3.atCenterOf(enderChestPos));
                mc.gameMode.continueDestroyBlock(enderChestPos, Direction.UP);
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                lookAt(player, Vec3d.ofCenter(enderChestPos));
                mc.interactionManager.updateBlockBreakingProgress(enderChestPos, Direction.UP);
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
            }

            // Wait for ender chest pickup.
            case 3 -> {
                if (shulkerTicks >= PICKUP_WAIT_TICKS) {
                    int recovered = findItemInInventory(mc, "minecraft:ender_chest");
                    if (recovered >= 0) {
                        LOGGER.info("[Elytra] recovered ender chest into slot {}", recovered);
                    } else {
                        LOGGER.warn("[Elytra] EC block broke but item not picked up (check Silk Touch)");
                    }
                    enderChestPos = null;
                    ecFromInventory = false;
                    transition(postShulkerState);
                }
            }
        }
    }

    /*? if >=26.1 {*//*
    public static boolean needsResupply(Minecraft mc) {
    *//*?} else {*/
    public static boolean needsResupply(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return false;
        ItemStack chest = getChestStack(mc);
        if (chest.isEmpty()) return true;
        String id = ItemIdentifier.getItemId(chest);
        if (!id.equals("minecraft:elytra")) return true;
        return isElytraLow(chest);
    }

    private static boolean isElytraLow(ItemStack stack) {
        /*? if >=26.1 {*//*
        return stack.getDamageValue() >= stack.getMaxDamage() - LOW_DURABILITY_THRESHOLD;
        *//*?} else {*/
        return stack.getDamage() >= stack.getMaxDamage() - LOW_DURABILITY_THRESHOLD;
        /*?}*/
    }

    private static boolean isElytraFullyRepaired(ItemStack stack) {
        /*? if >=26.1 {*//*
        return stack.getDamageValue() == 0;
        *//*?} else {*/
        return stack.getDamage() == 0;
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private static ItemStack getChestStack(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getItemBySlot(EquipmentSlot.CHEST);
    *//*?} else {*/
    private static ItemStack getChestStack(MinecraftClient mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getEquippedStack(EquipmentSlot.CHEST);
    /*?}*/
    }

    private static boolean hasEnchantment(ItemStack stack, String enchantPath) {
        /*? if >=26.1 {*//*
        ItemEnchantments enchants = stack.getOrDefault(
                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> entry : enchants.keySet()) {
            var key = entry.unwrapKey();
            if (key.isPresent() && enchantPath.equals(key.get().identifier().getPath())) return true;
        }
        *//*?} else {*/
        ItemEnchantmentsComponent enchants = stack.getOrDefault(
                DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            var key = entry.getKey();
            if (key.isPresent() && enchantPath.equals(key.get().getValue().getPath())) return true;
        }
        /*?}*/
        return false;
    }

    private static boolean shulkerContainsElytra(ItemStack shulker) {
        Map<String, Integer> contents = ChestManager.readShulkerContents(shulker);
        return contents.containsKey("minecraft:elytra");
    }

    private static boolean shulkerContainsXpBottles(ItemStack shulker) {
        Map<String, Integer> contents = ChestManager.readShulkerContents(shulker);
        return contents.containsKey("minecraft:experience_bottle");
    }

    private static boolean shulkerContainsFireworks(ItemStack shulker) {
        Map<String, Integer> contents = ChestManager.readShulkerContents(shulker);
        return contents.containsKey("minecraft:firework_rocket");
    }

    /*? if >=26.1 {*//*
    private static int findUsableElytraInInventory(Minecraft mc) {
    *//*?} else {*/
    private static int findUsableElytraInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        int bestSlot = -1;
        int bestDamage = Integer.MAX_VALUE;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            if (!ItemIdentifier.getItemId(stack).equals("minecraft:elytra")) continue;
            if (isElytraLow(stack)) continue;
            /*? if >=26.1 {*//*
            int dmg = stack.getDamageValue();
            *//*?} else {*/
            int dmg = stack.getDamage();
            /*?}*/
            if (dmg == 0) return i;
            if (dmg < bestDamage) {
                bestDamage = dmg;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /*? if >=26.1 {*//*
    private static int countUsableElytraInInventory(Minecraft mc) {
    *//*?} else {*/
    private static int countUsableElytraInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty()
                    && ItemIdentifier.getItemId(stack).equals("minecraft:elytra")
                    && !isElytraLow(stack)) {
                count++;
            }
        }
        return count;
    }

    /*? if >=26.1 {*//*
    private static int countInventoryInsertCapacity(Minecraft mc, String itemId) {
    *//*?} else {*/
    private static int countInventoryInsertCapacity(MinecraftClient mc, String itemId) {
    /*?}*/
        if (mc.player == null) return 0;
        int maxStack = maxStackSizeFor(itemId);
        int capacity = 0;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (stack.isEmpty()) {
                capacity += maxStack;
                continue;
            }
            if (ItemIdentifier.getItemId(stack).equals(itemId)) {
                capacity += Math.max(0, maxStack - stack.getCount());
            }
        }
        return capacity;
    }

    /*? if >=26.1 {*//*
    private static int findDamagedMendableElytraInInventory(Minecraft mc) {
    *//*?} else {*/
    private static int findDamagedMendableElytraInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            if (!ItemIdentifier.getItemId(stack).equals("minecraft:elytra")) continue;
            // Repair every damaged Mending elytra in this session.
            /*? if >=26.1 {*//*
            if (stack.getDamageValue() == 0) continue;
            *//*?} else {*/
            if (stack.getDamage() == 0) continue;
            /*?}*/
            if (!hasEnchantment(stack, "mending")) continue;
            return i;
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private static int findItemInInventory(Minecraft mc, String itemId) {
    *//*?} else {*/
    private static int findItemInInventory(MinecraftClient mc, String itemId) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty() && ItemIdentifier.getItemId(stack).equals(itemId)) return i;
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private static int findShulkerWithElytraInInventory(Minecraft mc) {
    *//*?} else {*/
    private static int findShulkerWithElytraInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack) && shulkerContainsElytra(stack)) return i;
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private static int findShulkerWithXpInInventory(Minecraft mc) {
    *//*?} else {*/
    private static int findShulkerWithXpInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack) && shulkerContainsXpBottles(stack)) return i;
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private static int findShulkerWithFireworksInInventory(Minecraft mc) {
    *//*?} else {*/
    private static int findShulkerWithFireworksInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack) && shulkerContainsFireworks(stack)) return i;
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private static int countItemInInventory(Minecraft mc, String itemId) {
    *//*?} else {*/
    private static int countItemInInventory(MinecraftClient mc, String itemId) {
    /*?}*/
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty() && ItemIdentifier.getItemId(stack).equals(itemId))
                count += stack.getCount();
        }
        return count;
    }

    /*? if >=26.1 {*//*
    private static int countItemInHotbar(Minecraft mc, String itemId) {
    *//*?} else {*/
    private static int countItemInHotbar(MinecraftClient mc, String itemId) {
    /*?}*/
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 9; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty() && ItemIdentifier.getItemId(stack).equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /*? if >=26.1 {*//*
    public static boolean needsFireworksRestock(Minecraft mc) {
    *//*?} else {*/
    public static boolean needsFireworksRestock(MinecraftClient mc) {
    /*?}*/
        return countItemInHotbar(mc, "minecraft:firework_rocket") < FIREWORK_RESTOCK_THRESHOLD;
    }

    /*? if >=26.1 {*//*
    private static int findLargestItemSlot(Minecraft mc, String itemId, int from, int to) {
    *//*?} else {*/
    private static int findLargestItemSlot(MinecraftClient mc, String itemId, int from, int to) {
    /*?}*/
        if (mc.player == null) return -1;
        int bestSlot = -1;
        int bestCount = -1;
        for (int i = from; i < to; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty() && ItemIdentifier.getItemId(stack).equals(itemId)
                    && stack.getCount() > bestCount) {
                bestSlot = i;
                bestCount = stack.getCount();
            }
        }
        return bestSlot;
    }

    /*? if >=26.1 {*//*
    private static int findTravelSupplyHotbarSlot(Minecraft mc, String itemId) {
        if (mc.player == null) return -1;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (DISPOSABLE_TRAVEL_ITEM_IDS.contains(ItemIdentifier.getItemId(inv.getItem(i)))) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (ItemIdentifier.getItemId(inv.getItem(i)).equals(itemId)) return i;
        }
        return -1;
    }

    private void stageInventorySlotInHotbar(Minecraft mc, int inventorySlot, int hotbarSlot) {
        if (!tryElytraInventory(SWAP_SETTLE)) return;
        mc.gameMode.handleContainerInput(
                mc.player.containerMenu.containerId, inventorySlot, hotbarSlot,
                ContainerInput.SWAP, mc.player);
        actionCooldown = SWAP_SETTLE;
        LOGGER.info("[Elytra] staged fireworks inventory slot {} -> hotbar {}", inventorySlot, hotbarSlot);
    }
    *//*?} else {*/
    private static int findTravelSupplyHotbarSlot(MinecraftClient mc, String itemId) {
        if (mc.player == null) return -1;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (DISPOSABLE_TRAVEL_ITEM_IDS.contains(ItemIdentifier.getItemId(inv.getStack(i)))) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (ItemIdentifier.getItemId(inv.getStack(i)).equals(itemId)) return i;
        }
        return -1;
    }

    private void stageInventorySlotInHotbar(MinecraftClient mc, int inventorySlot, int hotbarSlot) {
        if (!tryElytraInventory(SWAP_SETTLE)) return;
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId, inventorySlot, hotbarSlot,
                SlotActionType.SWAP, mc.player);
        actionCooldown = SWAP_SETTLE;
        LOGGER.info("[Elytra] staged fireworks inventory slot {} -> hotbar {}", inventorySlot, hotbarSlot);
    }
    /*?}*/

    private String currentWantedItemId() {
        if (fireworkMode) return "minecraft:firework_rocket";
        return mendingMode ? "minecraft:experience_bottle" : "minecraft:elytra";
    }

    private int currentTargetCount() {
        if (fireworkMode) return FIREWORK_RESTOCK_TARGET;
        return mendingMode ? 64
                : (MoarMod.getProperties() != null ? MoarMod.getProperties().getElytraResupplyCount() : 1);
    }

    private String currentModeLabel() {
        if (fireworkMode) return "fireworks";
        return mendingMode ? "XP" : "elytra";
    }

    private String currentPickupLabel() {
        if (fireworkMode) return "fireworks";
        return mendingMode ? "XP bottle" : "elytra";
    }

    private String currentShulkerLabel() {
        if (fireworkMode) return "firework shulker";
        return mendingMode ? "XP shulker" : "elytra shulker";
    }

    private boolean shulkerMatchesCurrentMode(ItemStack stack) {
        if (fireworkMode) return shulkerContainsFireworks(stack);
        return mendingMode ? shulkerContainsXpBottles(stack) : shulkerContainsElytra(stack);
    }

    /*? if >=26.1 {*//*
    private int findCurrentModeShulkerInInventory(Minecraft mc) {
    *//*?} else {*/
    private int findCurrentModeShulkerInInventory(MinecraftClient mc) {
    /*?}*/
        if (fireworkMode) return findShulkerWithFireworksInInventory(mc);
        return mendingMode ? findShulkerWithXpInInventory(mc) : findShulkerWithElytraInInventory(mc);
    }

    /*? if >=26.1 {*//*
    private int countCurrentSupplies(Minecraft mc) {
    *//*?} else {*/
    private int countCurrentSupplies(MinecraftClient mc) {
    /*?}*/
        if (fireworkMode) return countItemInInventory(mc, "minecraft:firework_rocket");
        return mendingMode ? countItemInInventory(mc, "minecraft:experience_bottle")
                : countUsableElytraInInventory(mc);
    }

    private static int maxStackSizeFor(String itemId) {
        return "minecraft:elytra".equals(itemId) ? 1 : 64;
    }

    /*? if >=26.1 {*//*
    private int dropOneDisposableStackFromShulkerScreen(Minecraft mc, String wantedItemId) {
    *//*?} else {*/
    private int dropOneDisposableStackFromShulkerScreen(MinecraftClient mc, String wantedItemId) {
    /*?}*/
        if (mc.player == null) return -1;
        int invSlot = findDisposableInventorySlot(mc, wantedItemId);
        if (invSlot < 0) return -1;
        int handlerSlot = invSlotToECHandlerSlot(invSlot, 27);
        if (!tryElytraInventory(CLICK_COOLDOWN)) return -1;
        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(
                mc.player.containerMenu.containerId, handlerSlot, 1,
                ContainerInput.THROW, mc.player);
        *//*?} else {*/
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId, handlerSlot, 1,
                SlotActionType.THROW, mc.player);
        /*?}*/
        return invSlot;
    }

    /*? if >=26.1 {*//*
    private int findDisposableInventorySlot(Minecraft mc, String wantedItemId) {
    *//*?} else {*/
    private int findDisposableInventorySlot(MinecraftClient mc, String wantedItemId) {
    /*?}*/
        if (mc.player == null) return -1;
        Set<String> throwawayIds = PathWalker.getThrowawayItemIds();
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            String itemId = ItemIdentifier.getItemId(stack);
            if (!isDisposableTravelStack(stack, itemId, wantedItemId, throwawayIds)) continue;
            int score = stack.getCount();
            if (i >= 9) score += 1000; // Prefer main inventory over hotbar.
            if (throwawayIds.contains(itemId)) score += 2000;
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private static boolean isDisposableTravelStack(
            ItemStack stack, String itemId, String wantedItemId, Set<String> throwawayIds) {
        if (itemId.equals(wantedItemId)) return false;
        if (itemId.equals("minecraft:elytra")
                || itemId.equals("minecraft:ender_chest")
                || itemId.equals("minecraft:firework_rocket")) {
            return false;
        }
        if (ChestManager.isShulkerBox(stack)) return false;
        return throwawayIds.contains(itemId) || DISPOSABLE_TRAVEL_ITEM_IDS.contains(itemId);
    }

    /*? if >=26.1 {*//*
    private static int findAnyShulkerInInventory(Minecraft mc) {
    *//*?} else {*/
    private static int findAnyShulkerInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack)) return i;
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private static int findSilkTouchPickaxe(Minecraft mc) {
    *//*?} else {*/
    private static int findSilkTouchPickaxe(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            if (!ItemIdentifier.getItemId(stack).contains("_pickaxe")) continue;
            if (hasEnchantment(stack, "silk_touch")) return i;
        }
        return -1;
    }

    // Snapshot shulkers before breaking the placed box.
    /*? if >=26.1 {*//*
    private void snapshotShulkerSlots(Minecraft mc) {
    *//*?} else {*/
    private void snapshotShulkerSlots(MinecraftClient mc) {
    /*?}*/
        preBreakShulkerSlots.clear();
        if (mc.player == null) return;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack)) preBreakShulkerSlots.add(i);
        }
    }

    /*? if >=26.1 {*//*
    private int findNewShulkerSlot(Minecraft mc) {
    *//*?} else {*/
    private int findNewShulkerSlot(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (preBreakShulkerSlots.contains(i)) continue;
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack)) return i;
        }
        return -1;
    }

    private static int invSlotToPSHSlot(int invSlot) {
        return invSlot < 9 ? 36 + invSlot : invSlot;
    }

    // Map inventory slots into an open container.
    private static int invSlotToECHandlerSlot(int invSlot, int ecSlots) {
        if (invSlot < 9) return ecSlots + 27 + invSlot;
        return ecSlots + (invSlot - 9);
    }

    /*? if >=26.1 {*//*
    private boolean ensureInHotbar(Minecraft mc, int invSlot) {
        Inventory inv = mc.player.getInventory();
        int hotbar = inv.getSelectedSlot();
        if (invSlot < 9) {
            if (hotbar == invSlot) return true;
            if (!MoarNetworkManager.tryAcquire(
                    MoarNetworkManager.Lane.INVENTORY,
                    MoarNetworkManager.OWNER_ELYTRA, 1, 2)) {
                return false;
            }
            if (savedHotbarSlot < 0) savedHotbarSlot = hotbar;
            inv.setSelectedSlot(invSlot);
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(invSlot));
            actionCooldown = Math.max(actionCooldown, 2);
            return false;
        } else {
            if (!MoarNetworkManager.tryAcquire(
                    MoarNetworkManager.Lane.INVENTORY,
                    MoarNetworkManager.OWNER_ELYTRA, 1, SWAP_SETTLE)) {
                return false;
            }
            if (savedHotbarSlot < 0) savedHotbarSlot = hotbar;
            mc.gameMode.handleContainerInput(
                    mc.player.containerMenu.containerId, invSlot, hotbar,
                    ContainerInput.SWAP, mc.player);
            actionCooldown = SWAP_SETTLE;
            return false;
        }
    }
    private void restoreHotbar(Minecraft mc) {
        if (savedHotbarSlot < 0 || mc.player == null) return;
        if (!MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.INVENTORY,
                MoarNetworkManager.OWNER_ELYTRA, 1, 2)) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(savedHotbarSlot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(savedHotbarSlot));
        savedHotbarSlot = -1;
    }
    *//*?} else {*/
    private boolean ensureInHotbar(MinecraftClient mc, int invSlot) {
        /*? if >=1.21.5 {*//*
        PlayerInventory inv = mc.player.getInventory();
        int hotbar = inv.getSelectedSlot();
        if (invSlot < 9) {
            if (hotbar == invSlot) return true;
            if (!MoarNetworkManager.tryAcquire(
                    MoarNetworkManager.Lane.INVENTORY,
                    MoarNetworkManager.OWNER_ELYTRA, 1, 2)) {
                return false;
            }
            if (savedHotbarSlot < 0) savedHotbarSlot = hotbar;
            inv.setSelectedSlot(invSlot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(invSlot));
            actionCooldown = Math.max(actionCooldown, 2);
            return false;
        } else {
            if (!MoarNetworkManager.tryAcquire(
                    MoarNetworkManager.Lane.INVENTORY,
                    MoarNetworkManager.OWNER_ELYTRA, 1, SWAP_SETTLE)) {
                return false;
            }
            if (savedHotbarSlot < 0) savedHotbarSlot = hotbar;
            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId, invSlot, hotbar,
                    SlotActionType.SWAP, mc.player);
            actionCooldown = SWAP_SETTLE;
            return false;
        }
        *//*?} else {*/
        PlayerInventory inv = mc.player.getInventory();
        int hotbar = inv.selectedSlot;
        if (invSlot < 9) {
            if (hotbar == invSlot) return true;
            if (!MoarNetworkManager.tryAcquire(
                    MoarNetworkManager.Lane.INVENTORY,
                    MoarNetworkManager.OWNER_ELYTRA, 1, 2)) {
                return false;
            }
            if (savedHotbarSlot < 0) savedHotbarSlot = hotbar;
            inv.selectedSlot = invSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(invSlot));
            actionCooldown = Math.max(actionCooldown, 2);
            return false;
        } else {
            if (!MoarNetworkManager.tryAcquire(
                    MoarNetworkManager.Lane.INVENTORY,
                    MoarNetworkManager.OWNER_ELYTRA, 1, SWAP_SETTLE)) {
                return false;
            }
            if (savedHotbarSlot < 0) savedHotbarSlot = hotbar;
            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId, invSlot, hotbar,
                    SlotActionType.SWAP, mc.player);
            actionCooldown = SWAP_SETTLE;
            return false;
        }
        /*?}*/
    }
    private void restoreHotbar(MinecraftClient mc) {
        if (savedHotbarSlot < 0 || mc.player == null) return;
        if (!MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.INVENTORY,
                MoarNetworkManager.OWNER_ELYTRA, 1, 2)) {
            return;
        }
        /*? if >=1.21.5 {*/
        mc.player.getInventory().setSelectedSlot(savedHotbarSlot);
        /*?} else {*/
        /*mc.player.getInventory().selectedSlot = savedHotbarSlot;
        *//*?}*/
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(savedHotbarSlot));
        savedHotbarSlot = -1;
    }
    /*?}*/

    private boolean isPlacementWindowSafe() {
        SetbackMonitor monitor = SetbackMonitor.get();
        // Require a stable placement window.
        if (monitor.recentSetbackCount(PLACE_RECENT_WINDOW) > 0) return false;
        return monitor.isStationaryFor(PLACE_STATIONARY_TICKS);
    }

    /*? if >=26.1 {*//*
    private static BlockPos findShulkerPlaceSpot(LocalPlayer player, Level world) {
        BlockPos playerFeet = player.blockPosition();
    *//*?} else {*/
    private static BlockPos findShulkerPlaceSpot(ClientPlayerEntity player, World world) {
        BlockPos playerFeet = player.getBlockPos();
    /*?}*/
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        boolean bestIsInteractive = true;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = playerFeet.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = playerFeet.add(dx, dy, dz);
                    /*?}*/
                    // Avoid placing inside the player.
                    if (px - 0.3 < pos.getX() + 1 && px + 0.3 > pos.getX()
                            && py < pos.getY() + 1 && py + 1.8 > pos.getY()
                            && pz - 0.3 < pos.getZ() + 1 && pz + 0.3 > pos.getZ()) continue;

                    BlockState blockState = world.getBlockState(pos);
                    /*? if >=26.1 {*//*
                    BlockState below = world.getBlockState(pos.below());
                    BlockState above = world.getBlockState(pos.above());
                    if ((blockState.isAir() || blockState.canBeReplaced())
                            && !below.getCollisionShape(world, pos.below()).isEmpty()
                            && (above.isAir() || above.canBeReplaced())) {
                        double dist = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                    *//*?} else {*/
                    BlockState below = world.getBlockState(pos.down());
                    BlockState above = world.getBlockState(pos.up());
                    if ((blockState.isAir() || blockState.isReplaceable())
                            && !below.getCollisionShape(world, pos.down()).isEmpty()
                            && (above.isAir() || above.isReplaceable())) {
                        double dist = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                    /*?}*/
                        if (dist > 4.5 * 4.5) continue;
                        boolean interactive = PlacementEngine.isInteractive(
                                /*? if >=26.1 {*//* below.getBlock() *//*?} else {*/below.getBlock()/*?}*/);
                        if (bestIsInteractive && !interactive) {
                            best = pos; bestDist = dist; bestIsInteractive = false;
                        } else if (interactive == bestIsInteractive && dist < bestDist) {
                            best = pos; bestDist = dist; bestIsInteractive = interactive;
                        }
                    }
                }
            }
        }
        return best;
    }

    /*? if >=26.1 {*//*
    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 toTarget = target.subtract(player.getEyePosition());
    *//*?} else {*/
    private static void lookAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d toTarget = target.subtract(player.getEyePos());
    /*?}*/
        double len = toTarget.length();
        if (len < 0.0001) return;
        double yaw   = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        double pitch = Math.toDegrees(-Math.asin(toTarget.y / len));
        // Send rotation before the following interaction.
        PlacementEngine.sendLookPacket(player, (float) yaw, (float) pitch);
    }

    /*? if >=26.1 {*//*
    private void finishShulkerExtraction(Minecraft mc) {
    *//*?} else {*/
    private void finishShulkerExtraction(MinecraftClient mc) {
    /*?}*/
        shulkerPhase = 0;
        if (mendingMode || fireworkMode) {
            if (mendingMode) {
                mendTicks = 0;
                postShulkerState = State.MENDING;
            } else {
                postShulkerState = State.CHECKING;
            }
            snapshotShulkerSlots(mc);
            shulkerPhase = 6;
            shulkerTicks = 0;
            transition(State.BREAKING_SHULKER);
            return;
        }
        transition(State.EQUIPPING);
    }

    private void checkpointForResume() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        /*?}*/

        openWaitTicks = 0;
        openRetries = 0;
        returnTicks = 0;
        actionCooldown = 0;

        if (state == State.IDLE || state == State.DONE || state == State.FAILED) return;

        switch (state) {
            case CHECKING, SWAPPING_ELYTRA -> transition(State.CHECKING);
            case MENDING -> transition(State.MENDING);
            case WALKING_TO_EC, OPENING_EC, FIND_SHULKER_EC, TAKING_SHULKER -> {
                PathWalker.stop();
                transition(State.WALKING_TO_EC);
            }
            case PLACING_SHULKER, OPENING_SHULKER, TAKING_ELYTRA, EQUIPPING -> {
                if (isPlacedShulkerStillPresent(mc)) {
                    shulkerPhase = 4;
                    shulkerTicks = 0;
                    transition(State.OPENING_SHULKER);
                } else if (findCurrentModeShulkerInInventory(mc) >= 0) {
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    transition(State.PLACING_SHULKER);
                } else {
                    transition(State.CHECKING);
                }
            }
            case BREAKING_SHULKER, WAITING_PICKUP, RETURNING_SHULKER -> {
                int shulkerInv = recoveredShulkerSlot >= 0 ? recoveredShulkerSlot : findAnyShulkerInInventory(mc);
                if (shulkerInv >= 0) {
                    recoveredShulkerSlot = shulkerInv;
                    queuePostPickupState();
                } else if (isPlacedShulkerStillPresent(mc)) {
                    shulkerPhase = 6;
                    shulkerTicks = 0;
                    transition(State.BREAKING_SHULKER);
                } else if (postShulkerState == State.MENDING) {
                    transition(State.MENDING);
                } else {
                    transition(State.CHECKING);
                }
            }
            case PLACING_EC -> {
                if (isPlacedEnderChestStillPresent(mc)) {
                    transition(State.OPENING_EC);
                } else if (findItemInInventory(mc, "minecraft:ender_chest") >= 0) {
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    transition(State.PLACING_EC);
                } else {
                    transition(State.CHECKING);
                }
            }
            case RECOVERING_EC -> {
                if (isPlacedEnderChestStillPresent(mc)) {
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    transition(State.RECOVERING_EC);
                } else {
                    transition(postShulkerState == State.MENDING ? State.MENDING : State.CHECKING);
                }
            }
            default -> {}
        }
    }

    private void closeOpenContainers() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.closeHandledScreen();
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private boolean isPlacedShulkerStillPresent(Minecraft mc) {
        return shulkerPos != null
                && mc.level != null
                && mc.level.getBlockState(shulkerPos).getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isPlacedEnderChestStillPresent(Minecraft mc) {
        return enderChestPos != null
                && mc.level != null
                && mc.level.getBlockState(enderChestPos).getBlock() instanceof EnderChestBlock;
    }
    *//*?} else {*/
    private boolean isPlacedShulkerStillPresent(MinecraftClient mc) {
        return shulkerPos != null
                && mc.world != null
                && mc.world.getBlockState(shulkerPos).getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isPlacedEnderChestStillPresent(MinecraftClient mc) {
        return enderChestPos != null
                && mc.world != null
                && mc.world.getBlockState(enderChestPos).getBlock() instanceof EnderChestBlock;
    }
    /*?}*/

    private void queuePostPickupState() {
        PathWalker.stop();

        if (postShulkerState == State.MENDING) {
            if (ecFromInventory) {
                shulkerPhase = 0;
                shulkerTicks = 0;
                transition(State.RECOVERING_EC);
            } else {
                LOGGER.info("[Elytra] keeping XP shulker in inventory and resuming mending");
                transition(State.MENDING);
            }
            return;
        }

        if (!shulkerFetchedFromEc) {
            LOGGER.info("[Elytra] keeping inventory shulker and continuing {}", postShulkerState);
            transition(postShulkerState);
            return;
        }

        returnPhase = 0;
        returnTicks = 0;
        transition(State.RETURNING_SHULKER);
    }

    /*? if >=26.1 {*//*
    private void lookAtEnderChest(LocalPlayer player) {
        if (enderChestPos == null) return;
        lookAt(player, Vec3.atCenterOf(enderChestPos));
    }
    *//*?} else {*/
    private void lookAtEnderChest(ClientPlayerEntity player) {
        if (enderChestPos == null) return;
        lookAt(player, Vec3d.ofCenter(enderChestPos));
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private void interactWithEnderChest(Minecraft mc, LocalPlayer player) {
        if (enderChestPos == null) return;
        if (!tryElytraInteraction(2)) return;
        Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
        try {
            Vec3 center = Vec3.atCenterOf(enderChestPos);
            Vec3 toTarget = center.subtract(player.getEyePosition());
            Direction hitFace = Direction.getApproximateNearest(
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);
            Vec3 hitPos = center.add(Vec3.atLowerCornerOf(hitFace.getUnitVec3i()).scale(0.48));
            lookAt(player, hitPos);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hitPos, hitFace, enderChestPos, false));
        } finally {
            restoreSneak.run();
        }
    }
    *//*?} else {*/
    private void interactWithEnderChest(MinecraftClient mc, ClientPlayerEntity player) {
        if (enderChestPos == null) return;
        if (!tryElytraInteraction(2)) return;
        Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
        try {
            Vec3d center = Vec3d.ofCenter(enderChestPos);
            Vec3d toTarget = center.subtract(player.getEyePos());
            Direction hitFace = Direction.getFacing(
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);
            Vec3d hitPos = center.add(Vec3d.of(hitFace.getVector()).multiply(0.48));
            lookAt(player, hitPos);
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                    new BlockHitResult(hitPos, hitFace, enderChestPos, false));
        } finally {
            restoreSneak.run();
        }
    }
    /*?}*/

    private void transition(State next) {
        LOGGER.debug("[Elytra] {} -> {}", state, next);
        state = next;
        stateTicks = 0;
    }

    /*? if >=26.1 {*//*
    private void fail(Minecraft mc, String reason) {
    *//*?} else {*/
    private void fail(MinecraftClient mc, String reason) {
    /*?}*/
        LOGGER.error("[Elytra] FAILED: {}", reason);
        ChatHelper.labelled("Travel", disconnectOnFailure
                ? "§c[Elytra] " + reason + " — disconnecting."
                : "§c[Elytra] " + reason + ".");
        state = State.FAILED;
        if (!disconnectOnFailure || mc.player == null) return;
        WebhookService.get().publish(WebhookEvent.of(
                WebhookEvent.Type.TRAVEL_DISCONNECT,
                "Safety disconnect",
                reason,
                WebhookEvent.Severity.ERROR,
                Map.of("Subsystem", "Elytra resupply")));
        /*? if >=26.1 {*//*
        mc.player.connection.getConnection().disconnect(
                Component.literal(reason));
        *//*?} else {*/
        mc.player.networkHandler.getConnection().disconnect(
                Text.literal(reason));
        /*?}*/
    }

    private void resetAll() {
        PathWalker.stop();
        if (shulkerSneakRestore != null) { shulkerSneakRestore.run(); shulkerSneakRestore = null; }
        stateTicks = 0;
        actionCooldown = 0;
        mendTicks = 0;
        savedHotbarSlot = -1;
        openWaitTicks = 0;
        openRetries = 0;
        shulkerPhase = 0;
        shulkerTicks = 0;
        shulkerFailures = 0;
        skipDirectEquip = false;
        equipSpareInvSlot = -1;
        shulkerPos = null;
        shulkerSlot = -1;
        ecFromInventory = false;
        ecInvSlot = -1;
        preBreakShulkerSlots.clear();
        recoveredShulkerSlot = -1;
        shulkerFetchedFromEc = false;
        returnPhase = 0;
        returnTicks = 0;
        elytraPickupCount = 0;
        elytraTakeQuota = -1;
        mendingMode = false;
        fireworkMode = false;
        postShulkerState = State.DONE;
        disconnectOnFailure = true;
    }
}
