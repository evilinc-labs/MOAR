package dev.moar.util;

import dev.moar.world.SetbackMonitor;

/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.piston.*;
*//*?} else {*/
import net.minecraft.block.*;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BedPart;
*//*?} else {*/
import net.minecraft.block.enums.BedPart;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.AttachFace;
*//*?} else {*/
import net.minecraft.block.enums.BlockFace;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.Half;
*//*?} else {*/
import net.minecraft.block.enums.BlockHalf;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
*//*?} else {*/
import net.minecraft.block.enums.DoorHinge;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
*//*?} else {*/
import net.minecraft.block.enums.DoubleBlockHalf;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.SlabType;
*//*?} else {*/
import net.minecraft.block.enums.SlabType;
/*?}*/
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
import net.minecraft.world.entity.player.Inventory;
*//*?} else {*/
import net.minecraft.entity.player.PlayerInventory;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.BlockItem;
*//*?} else {*/
import net.minecraft.item.BlockItem;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Item;
*//*?} else {*/
import net.minecraft.item.Item;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.item.ItemStack;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Items;
*//*?} else {*/
import net.minecraft.item.Items;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
*//*?} else {*/
import net.minecraft.state.property.Properties;
/*?}*/
/*? if >=26.1 {*//*
*//*?} else if >=1.21.5 {*/
/*?} else {*/
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ContainerInput;
*//*?} else {*/
import net.minecraft.screen.slot.SlotActionType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionResult;
*//*?} else {*/
import net.minecraft.util.ActionResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionHand;
*//*?} else {*/
import net.minecraft.util.Hand;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
*//*?} else {*/
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
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
/*? if >=26.1 {*//*
import net.minecraft.world.phys.shapes.Shapes;
*//*?} else {*/
import net.minecraft.util.shape.VoxelShapes;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.entity.ai.attributes.Attributes;
*//*?} else {*/
import net.minecraft.entity.attribute.EntityAttributes;
/*?}*/

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Core automation engine for block placement tasks.
 */
public final class PlacementEngine {

    private PlacementEngine() {}
    private static final String NETWORK_OWNER = "PlacementEngine";

    private enum PlacePhase { IDLE, SELECTING, ROTATING, SYNCING_LOOK, PLACING, FINISHING, BREAKING }
    private enum ItemSelectionResult { READY, STAGED, FAILED }
    private enum ItemSelectionKind { NONE, HOTBAR_SELECT, INVENTORY_SWAP }
    private enum ItemSelectionStep { NONE, APPLY, SETTLE, VALIDATE }

    private static final class VisiblePlacementHit {
        final Direction face;
        final BlockHitResult hit;

        VisiblePlacementHit(Direction face, BlockHitResult hit) {
            this.face = face;
            this.hit = hit;
        }
    }

    private static PlacePhase phase = PlacePhase.IDLE;

    private static BlockPos   pendingTarget;
    private static BlockState pendingDesired;
    private static Direction  pendingFace;
    private static boolean    pendingNeedsSneak;
    private static boolean    pendingAirPlace;
    private static Predicate<BlockPos> pendingSupportFilter;
    private static boolean    singleTickInProgress;
    private static Item       pendingItem;
    private static float      targetYaw;
    private static float      targetPitch;
    private static float      savedYaw;
    private static float      savedPitch;
    private static int        rotateTicks;
    private static int        lookSyncTicks;
    private static boolean    lookSyncedForPendingPlacement;
    private static int        pendingHotbarSlot = -1;
    private static int        pendingInventorySwapSlot = -1;
    private static int        pendingInventorySwapHotbarSlot = -1;
    private static boolean    pendingInventorySwapNeedsSlotSync;
    private static ItemSelectionKind pendingSelectionKind = ItemSelectionKind.NONE;
    private static ItemSelectionStep pendingSelectionStep = ItemSelectionStep.NONE;
    private static int        pendingSelectionSettleTicks;
    private static final int  HOTBAR_SELECT_SETTLE_TICKS = 2;
    private static final int  HOTBAR_SELECT_PACKET_SETTLE_TICKS = 3;
    private static final int  PRE_PLACE_LOOK_SYNC_TICKS = 1;
    private static final int  INVENTORY_SWAP_SETTLE_TICKS = 7;
    private static final int  INVENTORY_SWAP_ITEM_VARIETY_SETTLE_TICKS = 4;
    private static final float ORIENTATION_LOOK_MAX_YAW_DIFF = 18.0f;
    private static final float ORIENTATION_LOOK_MAX_PITCH_DIFF = 18.0f;
    private static final double FACE_PROBE_OFFSET = 0.22;
    private static int        inventorySwapSettleTicks;
    private static Item       lastPlacementItem;
    private static int        itemVarietyCooldownTicks;
    private static final int  ITEM_VARIETY_SETTLE_TICKS = 3;
    private static final int  SETBACK_RECENT_WINDOW_TICKS = 40;
    private static final double SAFE_PLACE_REACH_BLOCKS = 3.85;
    // Tolerance subtracted from block_interaction_range when computing reach;
    // absorbs minor client/server eye desync. Stationary printer needs little.
    private static final double REACH_AC_SAFETY_MARGIN = 0.15;
    /** Sane upper bound that prevents runaway in case of a misconfigured attribute. */
    private static final double REACH_HARD_CEILING = 6.0;
    private static final double PLACEMENT_ENTITY_MARGIN = 0.20;
    private static final Direction[] NORMAL_PLACE_FACE_ORDER = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.UP
    };
    private static final Direction[] HORIZONTAL_PLACE_FACE_ORDER = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
    };

    // self-correction state
    /** Position of a block that was placed with wrong orientation. */
    private static BlockPos   correctionTarget;
    /** The desired state for a correction re-place. */
    private static BlockState correctionDesired;
    private static int        breakingTicks;
    private static final int  MAX_BREAKING_TICKS = 200;
    private static int        postBreakWait;
    private static int        finishingTicks;
    private static final int MAX_CORRECTION_ENTRIES = 128;
    private static final Map<BlockPos, Integer> correctionAttempts = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Integer> eldest) {
            return size() > MAX_CORRECTION_ENTRIES;
        }
    };
    private static final int MAX_CORRECTION_ATTEMPTS = 2;
    private static final int  MAX_ROTATE_TICKS = 4;
    private static final float CONVERGE_THRESHOLD = 1.0f;
    private static final float MAX_TURN_SPEED = 30.0f;

    private static boolean silentRotation = false;

    public static void setSilentRotation(boolean value) { silentRotation = value; }
    public static boolean isSilentRotation() { return silentRotation; }

    // Fix #12 diagnostic: capture the last reason findVisiblePlacementHit
    // failed so SchematicPrinter can surface it alongside the
    // "place blocked" telemetry mark. Helps identify why universal
    // occlusion is happening when the support filter accepts neighbors
    // but no probe raycast lands on the requested face. Multiple faces
    // are tried per target; we keep the highest-ranked (closest-to-success)
    // failure so the surfaced reason is the most informative one rather
    // than just the last face attempted in iteration order.
    private static volatile String lastProbeFailure = null;
    private static volatile int    lastProbeFailureRank = -1;
    // rank: higher = more informative. 5 same-neighbor wrong-face, 4 wall hit,
    // 3 mixed miss+far, 2 all miss, 1 all too far, 0 support-filter rejected.
    private static void recordProbeFailure(String reason, int rank) {
        if (rank > lastProbeFailureRank) {
            lastProbeFailure = reason;
            lastProbeFailureRank = rank;
        }
    }
    private static void resetProbeFailure() {
        lastProbeFailure = null;
        lastProbeFailureRank = -1;
    }
    public static String consumeLastProbeFailure() {
        String r = lastProbeFailure;
        resetProbeFailure();
        return r;
    }

    private static boolean usesDirectLookPackets() {
        return false;
    }

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MAX_PLACE_CREDITS = 1.0;

    /** Per-block cadence floor -- the minimum gap (in ns) between any two
     *  consecutive placements. Derived from the user-facing BPS setting. */
    private static int    bps               = 19;
    private static long   lastPlacementTick = Long.MIN_VALUE;
    private static long   throttleTick      = Long.MIN_VALUE;
    private static double placeCredits      = MAX_PLACE_CREDITS;

    // Server-side placement verification — detect strict server validation rollbacks.
    private static final int VERIFY_DELAY_TICKS = 3;
    // Low-TPS servers run at 0.25–1 TPS; block-change responses can take 1000–4000 ms.
    // Trace showed late-accept totalElapsed=109 — server intake processes UseItemOn
    // at ~109 client-ticks under heavy load. 120 ticks (6s) gives headroom and covers
    // down to ~0.17 TPS (one server tick every 6 client seconds).
    private static final int VERIFY_TIMEOUT_TICKS = 120;
    private static final int MAX_VERIFY_QUEUE = 32;
    // Fix #22: window size 1 — only one UseItemOn in-flight at a time.
    // Test #13/#14 traced the timeout pattern to the server's per-player
    // UseItemOn intake queue. When we send packet N+1 before the server
    // has processed packet N, the server queues N+1 and the combined
    // processing delay exceeds our timeout window, triggering unnecessary
    // retries that compound the backlog. With window=1 we wait for the
    // server to acknowledge each placement before sending the next,
    // serializing through the server's queue. Throughput is limited to
    // ~1 placement per server processing cycle (~109 ticks = 5.45s under
    // heavy load), but every packet lands cleanly.
    private static final int MAX_INFLIGHT_PLACEMENT_VERIFICATIONS = 4;

    // Fix #22: placement queue — pre-planned candidates filled while the
    // current placement is awaiting server ACK.  When canPlace() is checked
    // and verifyQueue is non-empty (in-flight), the scanner can still run
    // and push new candidates here.  tickPlacementQueue() drains one entry
    // per ACK cycle, feeding placeBlock() in order.  Capacity=4 means we
    // look 4 moves ahead; the queue is cleared on any setback or reset so
    // stale entries never reach the server.
    private record QueuedPlacement(
            BlockPos target, BlockState desired,
            boolean allowSwap,
            java.util.function.Predicate<BlockPos> supportFilter) {}
    private static final ArrayDeque<QueuedPlacement> placementQueue = new ArrayDeque<>();
    private static final int PLACEMENT_QUEUE_CAPACITY = 8;
    // Freshly placed supports ACK in ~3 ticks, so 8 ticks is enough to let the
    // server register a support before we build against it without abandoning
    // the local pocket and walking away.
    private static final int RECENT_SUPPORT_SETTLE_TICKS = 8;
    private static final int MAX_RECENT_ACCEPTED_SUPPORTS = 128;
    private static final int POST_PLACE_SETTLE_TICKS = 2;
    private static final int PLACE_ROTATION_PRESERVE_TICKS = 1;
    private static int lastSentSelectedSlot = -1;
    private static boolean suppressVanillaMoveOnce = false;
    private static int consecutiveFailures = 0;
    private static int totalTimeouts = 0;
    private static int consecutiveRejections = 0;
    private static int totalRejections = 0;
    // Timeout breakers: first suspend inventory swaps, then only hard-pause
    // AutoBuild if the timeout streak includes swap-dependent placements.
    private static final int TIMEOUT_SUSPEND_THRESHOLD = 2;
    private static final int TIMEOUT_DISABLE_THRESHOLD = 8;
    private static int consecutiveSwapTimeouts = 0;
    private static int consecutiveTimeouts = 0;
    private static boolean swapsSuspended = false;
    private static boolean swapsSuspendedNoticePending = false;
    private static boolean autoBuildKillSwitch = false;
    private static boolean autoBuildKillSwitchNoticePending = false;
    /** Set true while an INVENTORY_SWAP-staged placement is in flight, so that the
     *  resulting verification entry can record whether the placement depended on a
     *  recent inventory swap. Cleared as soon as the verification entry is enqueued. */
    private static boolean placementUsedInventorySwap = false;
    private static int postPlaceSettleTicks = 0;
    private static String lastSequenceFailure = "not-started";
    private static final int MAX_VANILLA_RETRY_TARGETS = 128;
    private static final Map<BlockPos, Integer> vanillaRetryTargets = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Integer> eldest) {
            return size() > MAX_VANILLA_RETRY_TARGETS;
        }
    };
    private static BlockPos lastVerificationPos;
    private static BlockState lastVerificationState;
    private static BlockPos lastVerificationSupportPos;
    private static BlockPos lastNoVisiblePlacementHitTarget;
    private static BlockPos lastBodyClearancePlacementTarget;

    public enum VerificationStatus {
        NONE,
        PENDING,
        ACCEPTED,
        TIMEOUT,
        REJECTED
    }

    private record PendingVerification(BlockPos pos, BlockState expected, BlockState original,
                                       BlockPos supportPos, PlacementLane lane, boolean usedSwap,
                                       long placeTick) {}
    private record VerificationSnapshot(BlockState expected, VerificationStatus status,
                                         BlockPos supportPos) {}
    private enum PlacementLane {
        DIRECT,
        VANILLA
    }
    private static final ArrayDeque<PendingVerification> verifyQueue = new ArrayDeque<>();
    private static final Map<BlockPos, VerificationSnapshot> verificationStates = new HashMap<>();

    // Post-timeout watch list: after marking a placement TIMEOUT, keep checking
    // the position for LATE_WATCH_TICKS in case the ack arrived after the window.
    // A late-accept means the packet was delayed, not dropped — useful for
    // diagnosing high-latency/low-TPS, region-threaded, strict-validation and
    // protocol-translation behavior.
    private record LateAcceptWatch(BlockState expected, long timeoutTick,
                                   PlacementLane lane, long placeTick) {}
    private static final Map<BlockPos, LateAcceptWatch> lateAcceptWatch = new HashMap<>();
    private static final long LATE_WATCH_TICKS = 100; // ~5s past timeout

    // Last sent block-use sequence id. Captured by trySequencedBlockPlacement so
    // the caller can cross-reference it against server reach/window logs.
    private static int lastSentBlockUseSequence = -1;
    private static final Map<BlockPos, Long> recentAcceptedSupports = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
            return size() > MAX_RECENT_ACCEPTED_SUPPORTS;
        }
    };

    /** Number of consecutive placements that failed confirmation. */
    public static int getConsecutiveFailures() { return consecutiveFailures; }
    /** Total placements that timed out since last reset. */
    public static int getTotalTimeouts() { return totalTimeouts; }
    /** Number of consecutive placements that were rejected by the server. */
    public static int getConsecutiveRejections() { return consecutiveRejections; }
    /** Total placements rejected since last reset. */
    public static int getTotalRejections() { return totalRejections; }
    /** True when the inventory-swap circuit breaker has tripped for this session. */
    public static boolean areSwapsSuspended() { return swapsSuspended; }
    /** Returns true exactly once after the swap circuit breaker trips, so callers can
     *  emit a chat notice without spamming. */
    public static boolean consumeSwapsSuspendedNotice() {
        if (!swapsSuspendedNoticePending) return false;
        swapsSuspendedNoticePending = false;
        return true;
    }
    /** True when the AutoBuild kill switch has tripped for this session. The printer
     *  uses this as a signal to disable AutoBuild entirely after the server has stopped
     *  acknowledging placements. */
    public static boolean isAutoBuildKillSwitchTripped() { return autoBuildKillSwitch; }
    /** Returns true exactly once after the AutoBuild kill switch trips. */
    public static boolean consumeAutoBuildKillSwitchNotice() {
        if (!autoBuildKillSwitchNoticePending) return false;
        autoBuildKillSwitchNoticePending = false;
        return true;
    }
    /** Fix #18c: adaptive single-shot drain bonus. Returns extra recovery pause
     *  ticks the printer should add to the standard timeout pause, scaling with
     *  consecutive timeouts so the server has more time to drain its packet
     *  queue before we resume. Returns 0 in the steady state, capped at 12 ticks. */
    public static int getDrainPauseTicks() {
        if (consecutiveTimeouts <= 0) return 0;
        return Math.min(12, consecutiveTimeouts * 4);
    }
    public static void resetRejectionCounters() {
        verifyQueue.clear();
        verificationStates.clear();
        consecutiveFailures = 0;
        totalTimeouts = 0;
        consecutiveRejections = 0;
        totalRejections = 0;
        // Fix #19: do NOT clear consecutiveTimeouts / breaker state here.
        // resetRejectionCounters() is called by reset() on every recovery,
        // including timeout recoveries with forceReposition=true. Clearing
        // the session-wide counter on every recovery means it can never
        // accumulate to the breaker threshold even with five consecutive
        // timeouts. The breaker counters are only cleared by an ACCEPTED
        // placement (steady-state success) or by clearSessionBreakerState()
        // (user-driven AutoBuild toggle).
        placementUsedInventorySwap = false;
        lastVerificationPos = null;
        lastVerificationState = null;
        lastVerificationSupportPos = null;
    }

    /** Fully clear the session-sticky breaker state. Called from the printer when
     *  the user toggles AutoBuild off/on, so a fresh build session can attempt
     *  inventory swaps and packet placements normally. */
    public static void clearSessionBreakerState() {
        consecutiveSwapTimeouts = 0;
        consecutiveTimeouts = 0;
        swapsSuspended = false;
        swapsSuspendedNoticePending = false;
        autoBuildKillSwitch = false;
        autoBuildKillSwitchNoticePending = false;
        // Fix #20: late-watch entries from a prior session are no longer
        // meaningful; drop them so the new session starts clean.
        lateAcceptWatch.clear();
        placementQueue.clear();
    }

    public static VerificationStatus getVerificationStatus(BlockPos pos, BlockState expected) {
        VerificationSnapshot snapshot = verificationStates.get(pos);
        if (snapshot == null) return VerificationStatus.NONE;
        if (snapshot.expected.getBlock() != expected.getBlock()) {
            return VerificationStatus.NONE;
        }
        return snapshot.status;
    }

    public static BlockPos getVerificationSupportPos(BlockPos pos) {
        VerificationSnapshot snapshot = verificationStates.get(pos);
        return snapshot != null ? snapshot.supportPos : null;
    }

    public static void clearVerificationStatus(BlockPos pos) {
        verificationStates.remove(pos);
    }

    /** True while a placement at this pos is still awaiting server confirmation. */
    public static boolean isPlacementVerificationUnstable(BlockPos pos, BlockState expected) {
        if (pos == null) return false;
        VerificationSnapshot snapshot = verificationStates.get(pos);
        if (snapshot == null) return false;
        if (expected != null && snapshot.expected.getBlock() != expected.getBlock()) {
            return false;
        }
        return snapshot.status == VerificationStatus.PENDING;
    }

    private static void pruneRecentAcceptedSupports(long currentTick) {
        if (recentAcceptedSupports.isEmpty()) return;
        recentAcceptedSupports.entrySet().removeIf(entry ->
                currentTick - entry.getValue() >= RECENT_SUPPORT_SETTLE_TICKS);
    }

    private static boolean isFreshAcceptedSupport(BlockPos pos, long currentTick) {
        if (pos == null || currentTick < 0) return false;
        pruneRecentAcceptedSupports(currentTick);
        Long acceptedTick = recentAcceptedSupports.get(pos);
        return acceptedTick != null && currentTick - acceptedTick < RECENT_SUPPORT_SETTLE_TICKS;
    }

    public static BlockPos getLastVerificationPos() {
        return lastVerificationPos;
    }

    public static BlockState getLastVerificationState() {
        return lastVerificationState;
    }

    public static BlockPos getLastVerificationSupportPos() {
        return lastVerificationSupportPos;
    }

    public static boolean wasLastPlacementBlockedByNoVisibleHit(BlockPos target) {
        return target != null && target.equals(lastNoVisiblePlacementHitTarget);
    }

    public static boolean wasLastPlacementBlockedByBodyClearance(BlockPos target) {
        return target != null && target.equals(lastBodyClearancePlacementTarget);
    }

    /*? if >=26.1 {*//*
    public static boolean wouldIntersectPlayerOnPlacement(LocalPlayer player, BlockPos target) {
    *//*?} else {*/
    public static boolean wouldIntersectPlayerOnPlacement(ClientPlayerEntity player, BlockPos target) {
    /*?}*/
        return player != null && target != null && playerTooCloseToPlacement(player, target);
    }

    /** Tick the verification queue. Call once per game tick. */
    public static void tickVerification() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return;
        *//*?} else {*/
        if (mc.world == null) return;
        /*?}*/
        if (inventorySwapSettleTicks > 0) inventorySwapSettleTicks--;
        if (itemVarietyCooldownTicks > 0) itemVarietyCooldownTicks--;
        if (postPlaceSettleTicks > 0) postPlaceSettleTicks--;
        /*? if >=26.1 {*//*
        long currentTick = mc.level.getGameTime();
        *//*?} else {*/
        long currentTick = mc.world.getTime();
        /*?}*/

        while (!verifyQueue.isEmpty()) {
            PendingVerification pv = verifyQueue.peek();
            if (currentTick - pv.placeTick < VERIFY_DELAY_TICKS) break;
            long elapsedTicks = currentTick - pv.placeTick;

            /*? if >=26.1 {*//*
            BlockState actual = mc.level.getBlockState(pv.pos);
            *//*?} else {*/
            BlockState actual = mc.world.getBlockState(pv.pos);
            /*?}*/
            if (actual.getBlock() == pv.expected.getBlock()) {
                // Confirmed — server accepted the placement
                verifyQueue.poll();
                verificationStates.put(pv.pos,
                        new VerificationSnapshot(
                                pv.expected, VerificationStatus.ACCEPTED, pv.supportPos));
                recentAcceptedSupports.put(pv.pos, currentTick);
                PacketTelemetry.mark("verify accepted pos=" + pv.pos
                        + " expected=" + pv.expected.getBlock()
                        + " lane=" + pv.lane
                        + " elapsed=" + elapsedTicks);
                consecutiveFailures = 0;
                consecutiveRejections = 0;
                consecutiveSwapTimeouts = 0;
                consecutiveTimeouts = 0;
                clearVanillaRetry(pv.pos);
            } else if (actual.getBlock() != pv.original.getBlock()) {
                // Another server-side update won the race, so this attempt was rejected.
                verifyQueue.poll();
                verificationStates.put(pv.pos,
                        new VerificationSnapshot(
                                pv.expected, VerificationStatus.REJECTED, pv.supportPos));
                PacketTelemetry.mark("verify rejected pos=" + pv.pos
                        + " expected=" + pv.expected.getBlock()
                        + " original=" + pv.original.getBlock()
                        + " actual=" + actual.getBlock()
                        + " lane=" + pv.lane
                        + " elapsed=" + elapsedTicks);
                consecutiveFailures++;
                consecutiveRejections++;
                totalRejections++;
                if (pv.lane == PlacementLane.DIRECT) {
                    scheduleVanillaRetry(pv.pos);
                } else {
                    clearVanillaRetry(pv.pos);
                }
            } else if (elapsedTicks >= VERIFY_TIMEOUT_TICKS) {
                // The server never reflected the placement or a rollback packet.
                verifyQueue.poll();
                verificationStates.put(pv.pos,
                        new VerificationSnapshot(
                                pv.expected, VerificationStatus.TIMEOUT, pv.supportPos));
                VerificationSnapshot supportSnap = pv.supportPos != null
                        ? verificationStates.get(pv.supportPos) : null;
                String sessSupport = (supportSnap != null
                        && supportSnap.status() == VerificationStatus.ACCEPTED)
                        ? " sessSupport=" + pv.supportPos : "";
                PacketTelemetry.mark("verify timeout pos=" + pv.pos
                        + " expected=" + pv.expected.getBlock()
                        + " actual=" + actual.getBlock()
                        + " lane=" + pv.lane
                        + " elapsed=" + elapsedTicks
                        + " support=" + pv.supportPos
                        + " usedSwap=" + pv.usedSwap
                        + sessSupport);
                // Fix #20: enroll in late-accept watch so we can detect whether
                // the server's ack just arrived after our timeout window.
                lateAcceptWatch.put(pv.pos,
                        new LateAcceptWatch(pv.expected, currentTick, pv.lane, pv.placeTick));
                consecutiveFailures++;
                totalTimeouts++;
                consecutiveTimeouts++;
                if (pv.usedSwap) {
                    consecutiveSwapTimeouts++;
                }
                // Fix #18b: trip on ANY timeout, not just swap-attributed ones.
                // Test #10 confirmed pure hotbar placements also vanish once the
                // server-side state has drifted, so the breaker has to watch all
                // timeouts to be useful.
                if (!swapsSuspended
                        && consecutiveTimeouts >= TIMEOUT_SUSPEND_THRESHOLD) {
                    swapsSuspended = true;
                    swapsSuspendedNoticePending = true;
                    PacketTelemetry.mark("breaker swaps-suspended"
                            + " consecutiveTimeouts=" + consecutiveTimeouts
                            + " consecutiveSwapTimeouts=" + consecutiveSwapTimeouts
                            + " threshold=" + TIMEOUT_SUSPEND_THRESHOLD);
                }
                if (!autoBuildKillSwitch
                        && consecutiveSwapTimeouts > 0
                        && consecutiveTimeouts >= TIMEOUT_DISABLE_THRESHOLD) {
                    autoBuildKillSwitch = true;
                    autoBuildKillSwitchNoticePending = true;
                    PacketTelemetry.mark("breaker autobuild-disabled"
                            + " consecutiveTimeouts=" + consecutiveTimeouts
                            + " consecutiveSwapTimeouts=" + consecutiveSwapTimeouts
                            + " threshold=" + TIMEOUT_DISABLE_THRESHOLD);
                }
                if (pv.lane == PlacementLane.DIRECT) {
                    scheduleVanillaRetry(pv.pos);
                } else {
                    clearVanillaRetry(pv.pos);
                }
            } else {
                verificationStates.put(pv.pos,
                        new VerificationSnapshot(
                                pv.expected, VerificationStatus.PENDING, pv.supportPos));
                break;
            }
        }

        // Fix #20: late-accept watcher — see if a server ack arrived after we
        // already gave up. Distinguishes "packet dropped" (never resolves) from
        // "ack delayed" (resolves with a `verify late-accept` mark).
        if (!lateAcceptWatch.isEmpty()) {
            final long nowTick = currentTick;
            lateAcceptWatch.entrySet().removeIf(entry -> {
                BlockPos pos = entry.getKey();
                LateAcceptWatch lw = entry.getValue();
                /*? if >=26.1 {*//*
                BlockState actual = mc.level.getBlockState(pos);
                *//*?} else {*/
                BlockState actual = mc.world.getBlockState(pos);
                /*?}*/
                if (actual.getBlock() == lw.expected.getBlock()) {
                    PacketTelemetry.mark("verify late-accept pos=" + pos
                            + " expected=" + lw.expected.getBlock()
                            + " lane=" + lw.lane
                            + " postTimeoutTicks=" + (nowTick - lw.timeoutTick)
                            + " totalElapsed=" + (nowTick - lw.placeTick));
                    return true;
                }
                return nowTick - lw.timeoutTick >= LATE_WATCH_TICKS;
            });
        }
    }

    private static void enqueueVerification(BlockPos pos, BlockState expected, BlockState originalState,
                                            BlockPos supportPos, PlacementLane lane) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return;
        *//*?} else {*/
        if (mc.world == null) return;
        /*?}*/
        if (verifyQueue.size() >= MAX_VERIFY_QUEUE) {
            PendingVerification dropped = verifyQueue.poll();
            if (dropped != null) {
                verificationStates.put(dropped.pos,
                        new VerificationSnapshot(
                                dropped.expected, VerificationStatus.TIMEOUT, dropped.supportPos));
                PacketTelemetry.mark("verify dropped pos=" + dropped.pos
                        + " expected=" + dropped.expected.getBlock());
            }
        }
        BlockState original = originalState;
        if (original == null) {
            /*? if >=26.1 {*//*
            original = mc.level.getBlockState(pos);
            *//*?} else {*/
            original = mc.world.getBlockState(pos);
            /*?}*/
        }
        PendingVerification pending = new PendingVerification(
                /*? if >=26.1 {*//*
                pos.immutable(), expected, original,
                supportPos != null ? supportPos.immutable() : null,
                lane,
                placementUsedInventorySwap,
                mc.level.getGameTime());
                *//*?} else {*/
                pos.toImmutable(), expected, original,
                supportPos != null ? supportPos.toImmutable() : null,
                lane,
                placementUsedInventorySwap,
                mc.world.getTime());
                /*?}*/
        placementUsedInventorySwap = false;
        verifyQueue.add(pending);
        lastVerificationPos = pending.pos;
        lastVerificationState = pending.expected;
        lastVerificationSupportPos = pending.supportPos;
        verificationStates.put(pending.pos,
                new VerificationSnapshot(
                        pending.expected, VerificationStatus.PENDING, pending.supportPos));
    }

    private static void scheduleVanillaRetry(BlockPos pos) {
        if (pos == null) {
            return;
        }
        BlockPos key = immutablePos(pos);
        if (vanillaRetryTargets.getOrDefault(key, 0) <= 0) {
            vanillaRetryTargets.put(key, 1);
            PacketTelemetry.mark("place retry scheduled lane=vanilla target=" + key);
        }
    }

    private static boolean consumeVanillaRetry(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        Integer attempts = vanillaRetryTargets.remove(immutablePos(pos));
        return attempts != null && attempts > 0;
    }

    private static void clearVanillaRetry(BlockPos pos) {
        if (pos != null) {
            vanillaRetryTargets.remove(immutablePos(pos));
        }
    }

    /** True if a vanilla retry is queued for this pos (non-consuming). */
    public static boolean hasScheduledVanillaRetry(BlockPos pos) {
        return pos != null && vanillaRetryTargets.getOrDefault(immutablePos(pos), 0) > 0;
    }

    private static BlockPos immutablePos(BlockPos pos) {
        /*? if >=26.1 {*//*
        return pos.immutable();
        *//*?} else {*/
        return pos.toImmutable();
        /*?}*/
    }

    public static void setBps(int value) {
        bps = Math.max(1, Math.min(20, value));
    }

    public static int getBps() { return bps; }

    public static boolean canPlace() {
        if (phase != PlacePhase.IDLE) return false;
        if (!isPlacementWindowSafe()) return false;
        if (inventorySwapSettleTicks > 0 || itemVarietyCooldownTicks > 0 || postPlaceSettleTicks > 0) return false;
        if (verifyQueue.size() >= maxInflightPlacementVerifications()) {
            // In-flight placement present — allow the scanner to keep running so it
            // can fill the placement queue while we wait for the server ACK.
            // placeBlock() will redirect these calls into tryEnqueuePlacement().
            return placementQueue.size() < PLACEMENT_QUEUE_CAPACITY;
        }
        long currentTick = getCurrentWorldTick();
        if (currentTick < 0) return false;

        syncPlacementCredits(currentTick);
        if (currentTick == lastPlacementTick) return false;
        return placeCredits >= 1.0;
    }

    public static boolean isBusy() {
        return phase != PlacePhase.IDLE
                || hasPendingPlacementVerification()
                || postPlaceSettleTicks > 0;
    }

    public static boolean hasActivePhase() {
        return phase != PlacePhase.IDLE || postPlaceSettleTicks > 0;
    }

    public static boolean shouldFreezeMovementInputs() {
        if (postPlaceSettleTicks > 0) {
            return true;
        }
        return switch (phase) {
            case SELECTING, ROTATING, SYNCING_LOOK, PLACING, FINISHING -> true;
            default -> false;
        };
    }

    public static boolean shouldSuppressVanillaMovementPackets() {
        // Keep vanilla flying packets alive; strict validation uses them to close bursts.
        return false;
    }

    public static String getPhase() {
        return phase.name();
    }

    public static boolean isCorrecting() {
        return phase == PlacePhase.BREAKING;
    }

    public static void recordPlacement() {
        long currentTick = getCurrentWorldTick();
        if (currentTick < 0) return;
        syncPlacementCredits(currentTick);
        lastPlacementTick = currentTick;
        placeCredits = Math.max(0.0, placeCredits - 1.0);
        postPlaceSettleTicks = Math.max(postPlaceSettleTicks, POST_PLACE_SETTLE_TICKS);
    }

    private static boolean hasPendingPlacementVerification() {
        return !verifyQueue.isEmpty();
    }

    private static int maxInflightPlacementVerifications() {
        if (consecutiveFailures > 0 || consecutiveRejections > 0) {
            return 1;
        }
        if (!isPlacementWindowSafe()) {
            return 1;
        }
        return MAX_INFLIGHT_PLACEMENT_VERIFICATIONS;
    }

    private static boolean canChangeHeldItemNow() {
        return !hasPendingPlacementVerification() && postPlaceSettleTicks <= 0;
    }

    public static boolean canBatchPlace() {
        return false;
    }

    /** Enqueue a placement candidate to be sent after the current in-flight ACK.
     *  Returns true if successfully enqueued (caller may treat this like a successful
     *  placeBlock() in terms of scan-loop flow — i.e. stop looking for more candidates
     *  for this tick unless the queue still has room). */
    private static boolean tryEnqueuePlacement(BlockPos target, BlockState desired,
                                               boolean allowSwap,
                                               java.util.function.Predicate<BlockPos> supportFilter) {
        if (placementQueue.size() >= PLACEMENT_QUEUE_CAPACITY) return false;
        if (!isPlacementWindowSafe()) return false;
        /*? if >=26.1 {*//*
        BlockPos imm = target.immutable();
        *//*?} else {*/
        BlockPos imm = target.toImmutable();
        /*?}*/
        // Dedup: skip if this position is already in-flight or already queued.
        for (PendingVerification pv : verifyQueue) {
            if (pv.pos.equals(imm)) return false;
        }
        for (QueuedPlacement qp : placementQueue) {
            if (qp.target().equals(imm)) return false;
        }
        placementQueue.addLast(new QueuedPlacement(imm, desired, allowSwap, supportFilter));
        PacketTelemetry.mark("queue enqueue target=" + imm
                + " desired=" + desired.getBlock()
                + " depth=" + placementQueue.size());
        return true;
    }

    /** Drain one entry from the placement queue when the verification window is clear.
     *  Called at the top of tick() every game tick so the queue advances as fast as
     *  the server ACK cadence allows. */
    public static void tickPlacementQueue() {
        if (placementQueue.isEmpty()) return;
        if (phase != PlacePhase.IDLE) return;
        if (!verifyQueue.isEmpty()) return;           // wait for in-flight ACK
        if (postPlaceSettleTicks > 0
                || inventorySwapSettleTicks > 0
                || itemVarietyCooldownTicks > 0) return;
        if (!isPlacementWindowSafe()) return;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.world == null || mc.player == null) return;
        QueuedPlacement head = placementQueue.pollFirst();
        if (head == null) return;
        // Validate the entry is still needed — world state may have changed
        // while the entry was sitting in the queue.
        /*? if >=26.1 {*//*
        BlockState current = mc.level.getBlockState(head.target());
        boolean stillNeeded = current.isAir() || current.canBeReplaced();
        *//*?} else {*/
        BlockState current = mc.world.getBlockState(head.target());
        boolean stillNeeded = current.isAir() || current.isReplaceable();
        /*?}*/
        if (!stillNeeded) {
            PacketTelemetry.mark("queue drain skip target=" + head.target()
                    + " already-filled remaining=" + placementQueue.size());
            return; // discard; next tick will try the next entry
        }
        PacketTelemetry.mark("queue drain target=" + head.target()
                + " desired=" + head.desired().getBlock()
                + " remaining=" + placementQueue.size());
        placeBlock(head.target(), head.desired(), head.allowSwap(), head.supportFilter());
    }

    public static boolean consumeSuppressVanillaMove() {
        if (!suppressVanillaMoveOnce) {
            return false;
        }
        suppressVanillaMoveOnce = false;
        return true;
    }

    private static void syncPlacementCredits(long currentTick) {
        if (throttleTick == Long.MIN_VALUE) {
            throttleTick = currentTick;
            placeCredits = MAX_PLACE_CREDITS;
            return;
        }
        if (currentTick <= throttleTick) {
            return;
        }

        long elapsedTicks = currentTick - throttleTick;
        throttleTick = currentTick;
        placeCredits = Math.min(MAX_PLACE_CREDITS,
                placeCredits + elapsedTicks * (bps / TICKS_PER_SECOND));
    }

    private static long getCurrentWorldTick() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1L;
        return mc.level.getGameTime();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return -1L;
        return mc.world.getTime();
        /*?}*/
    }

    public static void reset() {
        if (pendingNeedsSneak && phase == PlacePhase.FINISHING) {
            if (SneakOverride.isForceAbsoluteSneak()) {
                /*? if >=26.1 {*//*
                Minecraft mc = Minecraft.getInstance();
                *//*?} else {*/
                MinecraftClient mc = MinecraftClient.getInstance();
                /*?}*/
                if (mc.player != null) pressSneakPacket(mc.player);
            } else {
                releaseSneakPacket();
            }
        }
        if (phase == PlacePhase.BREAKING) {
            /*? if >=26.1 {*//*
            Minecraft mc = Minecraft.getInstance();
            *//*?} else {*/
            MinecraftClient mc = MinecraftClient.getInstance();
            /*?}*/
            /*? if >=26.1 {*//*
            if (mc.gameMode != null) {
            *//*?} else {*/
            if (mc.interactionManager != null) {
            /*?}*/
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
            }
        }
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingDesired = null;
        pendingFace = null;
        pendingNeedsSneak = false;
        pendingAirPlace = false;
        pendingSupportFilter = null;
        singleTickInProgress = false;
        pendingItem = null;
        clearPendingSelectionState();
        inventorySwapSettleTicks = 0;
        itemVarietyCooldownTicks = 0;
        postPlaceSettleTicks = 0;
        finishingTicks = 0;
        lookSyncTicks = 0;
        lookSyncedForPendingPlacement = false;
        lastSentSelectedSlot = -1;
        suppressVanillaMoveOnce = false;
        lastPlacementItem = null;
        correctionTarget = null;
        correctionDesired = null;
        breakingTicks = 0;
        postBreakWait = 0;
        lastPlacementTick = Long.MIN_VALUE;
        throttleTick = Long.MIN_VALUE;
        placeCredits = MAX_PLACE_CREDITS;
        resetRejectionCounters();
        recentAcceptedSupports.clear();
        placementQueue.clear();
    }

    private static void clearPendingSelectionState() {
        pendingHotbarSlot = -1;
        pendingInventorySwapSlot = -1;
        pendingInventorySwapHotbarSlot = -1;
        pendingInventorySwapNeedsSlotSync = false;
        pendingSelectionKind = ItemSelectionKind.NONE;
        pendingSelectionStep = ItemSelectionStep.NONE;
        pendingSelectionSettleTicks = 0;
    }

    public static void clearCorrectionHistory() {
        correctionAttempts.clear();
    }

    public static void pruneCompletedCorrections() {
        correctionAttempts.values().removeIf(v -> v < MAX_CORRECTION_ATTEMPTS);
    }

    private static Map<Item, Integer> cachedInventory = Map.of();
    private static long cachedInventoryTick = -1;

    /** Cached inventory (invalidated once per tick). */
    public static Map<Item, Integer> getInventoryContentsCached() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return Map.of();
        *//*?} else {*/
        if (mc.world == null) return Map.of();
        /*?}*/
        /*? if >=26.1 {*//*
        long tick = mc.level.getGameTime();
        *//*?} else {*/
        long tick = mc.world.getTime();
        /*?}*/
        if (tick != cachedInventoryTick) {
            cachedInventory = getInventoryContents();
            cachedInventoryTick = tick;
        }
        return cachedInventory;
    }

    /** Tick the placement state machine. Returns true when a block was placed. */
    public static boolean tick() {
        tickPlacementQueue();
        return switch (phase) {
            case IDLE     -> false;
            case SELECTING -> tickSelecting();
            case ROTATING -> tickRotate();
            case SYNCING_LOOK -> tickLookSync();
            case PLACING  -> tickPlace();
            case FINISHING -> tickFinish();
            case BREAKING -> tickBreaking();
        };
    }

    private static boolean tickSelecting() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
        /*?}*/
            reset();
            return false;
        }
        if (pendingItem == null) {
            reset();
            return false;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (pendingSelectionKind == ItemSelectionKind.NONE
                || pendingSelectionStep == ItemSelectionStep.NONE) {
            failPendingSelection();
            return false;
        }

        if (pendingSelectionStep == ItemSelectionStep.APPLY) {
            applyPendingSelection(player, mc);
            return false;
        }

        if (pendingSelectionStep == ItemSelectionStep.SETTLE) {
            if (pendingSelectionSettleTicks > 0) {
                pendingSelectionSettleTicks--;
                return false;
            }
            if (hasPendingPlacementVerification()
                    || inventorySwapSettleTicks > 0
                    || itemVarietyCooldownTicks > 0
                    || postPlaceSettleTicks > 0) {
                return false;
            }
            if (!isPlacementWindowSafe()) {
                return false;
            }
            if (pendingInventorySwapNeedsSlotSync) {
                if (!PrinterNetworkCoordinator.tryAcquire(
                        PrinterNetworkCoordinator.Lane.INVENTORY, NETWORK_OWNER, 1,
                        HOTBAR_SELECT_SETTLE_TICKS)) {
                    return false;
                }
                PacketTelemetry.mark("select inventory-swap sync selected="
                        + getSelectedHotbarSlot(player));
                syncSelectedSlotPacket(player);
                pendingInventorySwapNeedsSlotSync = false;
                pendingSelectionSettleTicks = HOTBAR_SELECT_PACKET_SETTLE_TICKS;
                return false;
            }
            pendingSelectionStep = ItemSelectionStep.VALIDATE;
            return false;
        }

        if (getSelectedItem(player) != pendingItem) {
            failPendingSelection();
            return false;
        }

        clearPendingSelectionState();
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;
        return false;
    }

    /*? if >=26.1 {*//*
    private static void applyPendingSelection(LocalPlayer player, Minecraft mc) {
    *//*?} else {*/
    private static void applyPendingSelection(ClientPlayerEntity player, MinecraftClient mc) {
    /*?}*/
        if (pendingSelectionKind == ItemSelectionKind.INVENTORY_SWAP) {
            if (pendingInventorySwapSlot < 0 || pendingInventorySwapHotbarSlot < 0) {
                failPendingSelection();
                return;
            }
            if (!PrinterNetworkCoordinator.tryAcquire(
                    PrinterNetworkCoordinator.Lane.INVENTORY, NETWORK_OWNER, 3, INVENTORY_SWAP_SETTLE_TICKS)) {
                return;
            }
            int swapSlot = pendingInventorySwapSlot;
            int hotbarSlot = pendingInventorySwapHotbarSlot;
            boolean needsSlotSync = getSelectedHotbarSlot(player) != hotbarSlot;
            pendingInventorySwapSlot = -1;
            pendingInventorySwapHotbarSlot = -1;
            PacketTelemetry.mark("select inventory-swap slot=" + swapSlot
                    + " hotbar=" + hotbarSlot
                    + " sync=" + needsSlotSync);
            swapInventorySlotIntoHotbarSlot(player, mc, swapSlot, hotbarSlot);
            selectHotbarSlot(player, hotbarSlot);
            pendingInventorySwapNeedsSlotSync = needsSlotSync;
            placementUsedInventorySwap = true;
            recordSelectedItem(pendingItem, true);
            pendingSelectionSettleTicks = INVENTORY_SWAP_SETTLE_TICKS;
            itemVarietyCooldownTicks = Math.max(itemVarietyCooldownTicks,
                    INVENTORY_SWAP_ITEM_VARIETY_SETTLE_TICKS);
            pendingSelectionStep = ItemSelectionStep.SETTLE;
            return;
        }

        if (pendingSelectionKind == ItemSelectionKind.HOTBAR_SELECT) {
            if (pendingHotbarSlot < 0) {
                failPendingSelection();
                return;
            }
            if (!PrinterNetworkCoordinator.tryAcquire(
                    PrinterNetworkCoordinator.Lane.INVENTORY, NETWORK_OWNER, 1, HOTBAR_SELECT_SETTLE_TICKS)) {
                return;
            }
            int slot = pendingHotbarSlot;
            pendingHotbarSlot = -1;
            PacketTelemetry.mark("select hotbar slot=" + slot);
            selectHotbarSlot(player, slot);
            syncSelectedSlotPacket(player);
            recordSelectedItem(pendingItem, true);
            pendingSelectionSettleTicks = HOTBAR_SELECT_PACKET_SETTLE_TICKS;
            pendingSelectionStep = ItemSelectionStep.SETTLE;
            return;
        }

        failPendingSelection();
    }

    private static void failPendingSelection() {
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingDesired = null;
        pendingFace = null;
        pendingNeedsSneak = false;
        pendingSupportFilter = null;
        pendingItem = null;
        clearPendingSelectionState();
    }

    private static void abortPendingPlacementAttempt() {
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingDesired = null;
        pendingFace = null;
        pendingNeedsSneak = false;
        pendingAirPlace = false;
        pendingSupportFilter = null;
        pendingItem = null;
        lookSyncedForPendingPlacement = false;
        clearPendingSelectionState();
    }

    private static ItemSelectionResult stageHotbarSelection(int slot) {
        pendingHotbarSlot = slot;
        pendingInventorySwapSlot = -1;
        pendingInventorySwapHotbarSlot = -1;
        pendingInventorySwapNeedsSlotSync = false;
        pendingSelectionKind = ItemSelectionKind.HOTBAR_SELECT;
        pendingSelectionStep = ItemSelectionStep.APPLY;
        pendingSelectionSettleTicks = 0;
        phase = PlacePhase.SELECTING;
        return ItemSelectionResult.STAGED;
    }

    private static ItemSelectionResult stageInventorySwap(int inventorySlot, int hotbarSlot) {
        if (hotbarSlot < 0) {
            PacketTelemetry.mark("select inventory-swap blocked protected-hotbar inv=" + inventorySlot);
            return ItemSelectionResult.FAILED;
        }
        pendingHotbarSlot = -1;
        pendingInventorySwapSlot = inventorySlot;
        pendingInventorySwapHotbarSlot = hotbarSlot;
        pendingInventorySwapNeedsSlotSync = false;
        pendingSelectionKind = ItemSelectionKind.INVENTORY_SWAP;
        pendingSelectionStep = ItemSelectionStep.APPLY;
        pendingSelectionSettleTicks = 0;
        phase = PlacePhase.SELECTING;
        return ItemSelectionResult.STAGED;
    }

    private static boolean tickRotate() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) { reset(); return false; }

        rotateTicks++;

        if (silentRotation && usesDirectLookPackets()) {
            if (!PrinterNetworkCoordinator.tryAcquire(
                    PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, PRE_PLACE_LOOK_SYNC_TICKS)) {
                return false;
            }
            sendSilentLookPacket(mc.player, targetYaw, targetPitch);
            lookSyncedForPendingPlacement = true;

            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            phase = PlacePhase.PLACING;
            return false;
        }

        /*? if >=26.1 {*//*
        float currentYaw = mc.player.getYRot();
        *//*?} else {*/
        float currentYaw = mc.player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        float currentPitch = mc.player.getXRot();
        *//*?} else {*/
        float currentPitch = mc.player.getPitch();
        /*?}*/

        /*? if >=26.1 {*//*
        float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
        *//*?} else {*/
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        /*?}*/
        float pitchDiff = targetPitch - currentPitch;

        /*? if >=26.1 {*//*
        float newYaw = currentYaw + Mth.clamp(yawDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        *//*?} else {*/
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        /*?}*/
        /*? if >=26.1 {*//*
        float newPitch = currentPitch + Mth.clamp(pitchDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        *//*?} else {*/
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        /*?}*/
        /*? if >=26.1 {*//*
        newPitch = Mth.clamp(newPitch, -90.0f, 90.0f);
        *//*?} else {*/
        newPitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);
        /*?}*/

        /*? if >=26.1 {*//*
        mc.player.setYRot(newYaw);
        *//*?} else {*/
        mc.player.setYaw(newYaw);
        /*?}*/
        /*? if >=26.1 {*//*
        mc.player.setXRot(newPitch);
        *//*?} else {*/
        mc.player.setPitch(newPitch);
        /*?}*/

        /*? if >=26.1 {*//*
        boolean converged = Math.abs(Mth.wrapDegrees(targetYaw - newYaw)) < CONVERGE_THRESHOLD
        *//*?} else {*/
        boolean converged = Math.abs(MathHelper.wrapDegrees(targetYaw - newYaw)) < CONVERGE_THRESHOLD
        /*?}*/
                         && Math.abs(targetPitch - newPitch) < CONVERGE_THRESHOLD;

        if (converged || rotateTicks >= MAX_ROTATE_TICKS) {
            if (converged) {
                /*? if >=26.1 {*//*
                mc.player.setYRot(targetYaw);
                *//*?} else {*/
                mc.player.setYaw(targetYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(targetPitch);
                *//*?} else {*/
                mc.player.setPitch(targetPitch);
                /*?}*/
            }

            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            lookSyncedForPendingPlacement = false;
            lookSyncTicks = PRE_PLACE_LOOK_SYNC_TICKS;
            phase = PlacePhase.SYNCING_LOOK;
        }

        return false;
    }

    private static boolean tickLookSync() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
        /*?}*/
            reset();
            return false;
        }

        if (lookSyncTicks == PRE_PLACE_LOOK_SYNC_TICKS) {
            if (!PrinterNetworkCoordinator.tryAcquire(
                    PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, PRE_PLACE_LOOK_SYNC_TICKS)) {
                return false;
            }
            /*? if >=26.1 {*//*
            sendImmediateLookSync(mc.player, mc.player.getYRot(), mc.player.getXRot());
            *//*?} else {*/
            sendImmediateLookSync(mc.player, mc.player.getYaw(), mc.player.getPitch());
            /*?}*/
            lookSyncedForPendingPlacement = true;
        }

        if (lookSyncTicks > 0) {
            lookSyncTicks--;
            if (lookSyncTicks > 0) {
                return false;
            }
        }

        phase = PlacePhase.PLACING;
        return false;
    }

    private static boolean tickPlace() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
        /*?}*/
            reset();
            return false;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        // A container SWAP can succeed client-side before the server treats the
        // new hotbar item as held. Waiting briefly avoids place packets racing
        // ahead of the inventory update.
        if (inventorySwapSettleTicks > 0 || itemVarietyCooldownTicks > 0) {
            return false;
        }

        if (!isPlacementWindowSafe()) {
            return false;
        }

        if (pendingItem != null) {
            /*? if >=26.1 {*//*
            Inventory inv = player.getInventory();
            *//*?} else {*/
            PlayerInventory inv = player.getInventory();
            /*?}*/
            /*? if >=26.1 {*//*
            Item held = inv.getItem(inv.getSelectedSlot()).getItem();
            *//*?} else if >=1.21.5 {*//*
            Item held = inv.getStack(inv.getSelectedSlot()).getItem();
            *//*?} else {*/
            Item held = inv.getStack(inv.selectedSlot).getItem();
            /*?}*/
            if (held != pendingItem) {
                ItemSelectionResult selection = selectItem(player, mc, pendingItem, true);
                if (selection == ItemSelectionResult.FAILED) {
                    if (pendingNeedsSneak) {
                        if (SneakOverride.isForceAbsoluteSneak()) {
                            pressSneakPacket(player);
                        } else {
                            releaseSneakPacket();
                        }
                    }
                    if (!silentRotation) {
                        restoreLook(player);
                    } else if (usesDirectLookPackets()) {
                        if (PrinterNetworkCoordinator.tryAcquire(
                                PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, 1)) {
                        /*? if >=26.1 {*//*
                        sendSilentLookPacket(player, player.getYRot(), player.getXRot());
                        *//*?} else {*/
                        sendSilentLookPacket(player, player.getYaw(), player.getPitch());
                        /*?}*/
                        }
                    }
                    phase = PlacePhase.IDLE;
                    pendingTarget = null;
                    pendingDesired = null;
                    return false;
                }
                if (selection == ItemSelectionResult.STAGED) {
                    return false;
                }
            }
        }

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/

        /*? if >=26.1 {*//*
        float placeYaw   = silentRotation ? targetYaw   : player.getYRot();
        *//*?} else {*/
        float placeYaw   = silentRotation ? targetYaw   : player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        float placePitch  = silentRotation ? targetPitch : player.getXRot();
        *//*?} else {*/
        float placePitch  = silentRotation ? targetPitch : player.getPitch();
        /*?}*/

        BlockHitResult hitResult;
        BlockPos hitBlockPos;
        Direction hitSide;
        if (pendingAirPlace) {
            Direction airFace = Direction.UP;
            /*? if >=26.1 {*//*
            Vec3 hitPos = Vec3.atCenterOf(pendingTarget).add(
            *//*?} else {*/
            Vec3d hitPos = Vec3d.ofCenter(pendingTarget).add(
            /*?}*/
                    /*? if >=26.1 {*//*
                    airFace.getStepX() * 0.5,
                    *//*?} else {*/
                    airFace.getOffsetX() * 0.5,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    airFace.getStepY() * 0.5,
                    *//*?} else {*/
                    airFace.getOffsetY() * 0.5,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    airFace.getStepZ() * 0.5);
                    *//*?} else {*/
                    airFace.getOffsetZ() * 0.5);
                    /*?}*/
            hitPos = adjustHitForAirPlace(hitPos, pendingTarget, pendingDesired);
            hitResult = new BlockHitResult(hitPos, airFace, pendingTarget, false);
            hitBlockPos = pendingTarget;
            hitSide = airFace;
        } else {
            /*? if >=26.1 {*//*
            VisiblePlacementHit visibleHit = findBestVisiblePlacementHit(
                    player, mc.level, pendingTarget, pendingDesired, pendingSupportFilter);
            *//*?} else {*/
            VisiblePlacementHit visibleHit = findBestVisiblePlacementHit(
                    player, mc.world, pendingTarget, pendingDesired, pendingSupportFilter);
            /*?}*/
            if (visibleHit != null) {
                hitResult = visibleHit.hit;
                pendingFace = visibleHit.face;
                hitBlockPos = visibleHit.hit.getBlockPos();
                /*? if >=26.1 {*//*
                hitSide = visibleHit.hit.getDirection();
                *//*?} else {*/
                hitSide = visibleHit.hit.getSide();
                /*?}*/
            } else {
                PacketTelemetry.mark("place abort no-visible-hit target=" + pendingTarget
                        + " desired=" + pendingDesired.getBlock()
                        + " face=" + pendingFace);
                abortPendingPlacementAttempt();
                return false;
            }
        }

        if (playerTooCloseToPlacement(player, pendingTarget)) {
            PacketTelemetry.mark("place abort body-clearance target=" + pendingTarget
                    + " desired=" + pendingDesired.getBlock()
                    + " face=" + pendingFace
                    + " air=" + pendingAirPlace);
            abortPendingPlacementAttempt();
            return false;
        }

        double hitDistanceSq;
        {
            double reachLimit = effectivePlaceReach(player);
            double reachSq = reachLimit * reachLimit;
            /*? if >=26.1 {*//*
            hitDistanceSq = eyePos.distanceToSqr(hitResult.getLocation());
            if (hitDistanceSq > reachSq) {
            *//*?} else {*/
            hitDistanceSq = eyePos.squaredDistanceTo(hitResult.getPos());
            if (hitDistanceSq > reachSq) {
            /*?}*/
                double hitDistance = Math.round(Math.sqrt(hitDistanceSq) * 100.0) / 100.0;
                PacketTelemetry.mark("place abort out-of-reach target=" + pendingTarget
                        + " desired=" + pendingDesired.getBlock()
                        + " face=" + pendingFace
                        + " air=" + pendingAirPlace
                        + " hitDist=" + hitDistance
                        + " reach=" + reachLimit
                        + " hit=" + hitResult);
                abortPendingPlacementAttempt();
                return false;
            }
        }

        double hitDistance = Math.round(Math.sqrt(hitDistanceSq) * 100.0) / 100.0;
        /*? if >=26.1 {*//*
        BlockState targetState = mc.level.getBlockState(pendingTarget);
        BlockState hitBlockState = mc.level.getBlockState(hitBlockPos);
        *//*?} else {*/
        BlockState targetState = mc.world.getBlockState(pendingTarget);
        BlockState hitBlockState = mc.world.getBlockState(hitBlockPos);
        /*?}*/

        /*? if >=26.1 {*//*
        boolean isLiquidPlacement = pendingDesired.getBlock() instanceof LiquidBlock;
        *//*?} else {*/
        boolean isLiquidPlacement = pendingDesired.getBlock() instanceof FluidBlock;
        /*?}*/

        if (!pendingAirPlace && !isLiquidPlacement) {
            /*? if >=26.1 {*//*
            boolean targetReplaceable = targetState.canBeReplaced();
            *//*?} else {*/
            boolean targetReplaceable = targetState.isReplaceable();
            /*?}*/
            if (!targetState.isAir() && !targetReplaceable) {
                PacketTelemetry.mark("place abort target-filled target=" + pendingTarget
                        + " desired=" + pendingDesired.getBlock()
                        + " actual=" + targetState.getBlock()
                        + " face=" + pendingFace
                        + " hitBlock=" + hitBlockPos
                        + " hitSide=" + hitSide);
                clearVanillaRetry(pendingTarget);
                abortPendingPlacementAttempt();
                return false;
            }

            /*? if >=26.1 {*//*
            boolean supportReplaceable = hitBlockState.canBeReplaced();
            boolean supportHasShape = hitBlockState.getShape(mc.level, hitBlockPos) != Shapes.empty();
            *//*?} else {*/
            boolean supportReplaceable = hitBlockState.isReplaceable();
            boolean supportHasShape = hitBlockState.getOutlineShape(mc.world, hitBlockPos) != VoxelShapes.empty();
            /*?}*/
            if (supportReplaceable || !supportHasShape) {
                PacketTelemetry.mark("place abort support-invalid target=" + pendingTarget
                        + " desired=" + pendingDesired.getBlock()
                        + " support=" + hitBlockPos
                        + " supportState=" + hitBlockState.getBlock()
                        + " face=" + pendingFace
                        + " hitSide=" + hitSide);
                clearVanillaRetry(pendingTarget);
                abortPendingPlacementAttempt();
                return false;
            }
            if (!isSupportAllowed(pendingSupportFilter, hitBlockPos)) {
                PacketTelemetry.mark("place abort support-filter target=" + pendingTarget
                        + " desired=" + pendingDesired.getBlock()
                        + " support=" + hitBlockPos
                        + " face=" + pendingFace
                        + " hitSide=" + hitSide);
                clearVanillaRetry(pendingTarget);
                abortPendingPlacementAttempt();
                return false;
            }
        }

        if (silentRotation && !singleTickInProgress && usesDirectLookPackets()) {
            if (!PrinterNetworkCoordinator.tryAcquire(
                    PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, PRE_PLACE_LOOK_SYNC_TICKS)) {
                return false;
            }
            sendSilentLookPacket(player, placeYaw, placePitch);
            lookSyncedForPendingPlacement = true;
        }

        // On the normal printer path we already rotated locally in ROTATING.
        // Let vanilla carry that state instead of forcing an extra move packet
        // right before every block use.
        if (usesDirectLookPackets() && !lookSyncedForPendingPlacement) {
            if (!PrinterNetworkCoordinator.tryAcquire(
                    PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, PRE_PLACE_LOOK_SYNC_TICKS)) {
                return false;
            }
            sendImmediateLookSync(player, placeYaw, placePitch);
            lookSyncedForPendingPlacement = true;
        }

        PacketTelemetry.mark("place hand=" + getSelectedItem(player).toString()
                + " target=" + pendingTarget
                + " desired=" + pendingDesired.getBlock()
                + " face=" + pendingFace
                + " air=" + pendingAirPlace
                + " hitDist=" + hitDistance
                + " eye=" + String.format("%.2f,%.2f,%.2f", eyePos.x, eyePos.y, eyePos.z)
                + " eyeToCenter=" + String.format("%.2f",
                        Math.sqrt(
                                (eyePos.x - (pendingTarget.getX() + 0.5))
                                        * (eyePos.x - (pendingTarget.getX() + 0.5))
                              + (eyePos.y - (pendingTarget.getY() + 0.5))
                                        * (eyePos.y - (pendingTarget.getY() + 0.5))
                              + (eyePos.z - (pendingTarget.getZ() + 0.5))
                                        * (eyePos.z - (pendingTarget.getZ() + 0.5))))
                + " reach=" + String.format("%.2f", effectivePlaceReach(player))
                + " yawRaw=" + String.format("%.2f", placeYaw)
                + " yawNorm=" + String.format("%.2f",
                        ((placeYaw % 360f) + 540f) % 360f - 180f)
                + " pitch=" + String.format("%.2f", placePitch)
                + " hitBlock=" + hitBlockPos
                + " hitSide=" + hitSide
                + " targetState=" + targetState.getBlock()
                + " hitState=" + hitBlockState.getBlock()
                + " hit=" + hitResult);

        boolean placed;
        if (!PrinterNetworkCoordinator.tryAcquire(
                PrinterNetworkCoordinator.Lane.INTERACTION, NETWORK_OWNER, 2, POST_PLACE_SETTLE_TICKS)) {
            return false;
        }
        if (isLiquidPlacement) {
            /*? if >=26.1 {*//*
            InteractionResult result = mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            if (result.consumesAction()) {
                if (shouldClientSwing(result)) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
            }
            if (result.consumesAction()) {
            *//*?} else {*/
            ActionResult result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            if (result.isAccepted()) {
                if (shouldClientSwing(result)) {
                    player.swingHand(Hand.MAIN_HAND);
                }
            }
            if (result.isAccepted()) {
            /*?}*/
                /*? if >=26.1 {*//*
                BlockState afterState = mc.level.getBlockState(pendingTarget);
                *//*?} else {*/
                BlockState afterState = mc.world.getBlockState(pendingTarget);
                /*?}*/
                /*? if >=26.1 {*//*
                placed = afterState.getBlock() instanceof LiquidBlock
                *//*?} else {*/
                placed = afterState.getBlock() instanceof FluidBlock
                /*?}*/
                      /*? if >=26.1 {*//*
                      && afterState.getFluidState().isSource();
                      *//*?} else {*/
                      && afterState.getFluidState().isStill();
                      /*?}*/
            } else {
                placed = false;
            }
        } else {
            // Keep normal building on one explicit packet lane.
            boolean vanillaRetry = consumeVanillaRetry(pendingTarget);
            if (!vanillaRetry && trySequencedBlockPlacement(player, hitResult)) {
                sendPlacementSwing(player);
                PacketTelemetry.mark("place lane=direct target=" + pendingTarget
                        + " seq=" + lastSentBlockUseSequence);
                enqueueVerification(pendingTarget, pendingDesired, targetState, hitBlockPos, PlacementLane.DIRECT);
                placed = true;
            } else {
                PacketTelemetry.mark("place lane=vanilla target=" + pendingTarget
                        + " retry=" + vanillaRetry);
            /*? if >=26.1 {*//*
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
            if (result.consumesAction()) {
                if (shouldClientSwing(result)) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
                enqueueVerification(pendingTarget, pendingDesired, targetState, hitBlockPos, PlacementLane.VANILLA);
                placed = true;
            } else {
                placed = false;
            }
            *//*?} else {*/
            ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
            if (result.isAccepted()) {
                if (shouldClientSwing(result)) {
                    player.swingHand(Hand.MAIN_HAND);
                }
                enqueueVerification(pendingTarget, pendingDesired, targetState, hitBlockPos, PlacementLane.VANILLA);
                placed = true;
            } else {
                placed = false;
            }
            /*?}*/
            }
        }

        if (placed) {
            recordPlacement();
            finishingTicks = PLACE_ROTATION_PRESERVE_TICKS;
            phase = PlacePhase.FINISHING;
        } else {
            if (!singleTickInProgress) {
                if (!silentRotation) {
                    restoreLook(player);
                } else if (usesDirectLookPackets()) {
                    if (PrinterNetworkCoordinator.tryAcquire(
                            PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, 1)) {
                    /*? if >=26.1 {*//*
                    sendSilentLookPacket(player, player.getYRot(), player.getXRot());
                    *//*?} else {*/
                    sendSilentLookPacket(player, player.getYaw(), player.getPitch());
                    /*?}*/
                    }
                }
            }
            phase = PlacePhase.IDLE;
            pendingTarget = null;
            pendingDesired = null;
            pendingFace = null;
            pendingNeedsSneak = false;
            pendingAirPlace = false;
            pendingSupportFilter = null;
            pendingItem = null;
            lookSyncedForPendingPlacement = false;
            clearPendingSelectionState();
        }

        return placed;
    }

    private static boolean tickFinish() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player != null) {
            if (finishingTicks > 0) {
                finishingTicks--;
                return false;
            }
            if (SneakOverride.isForceAbsoluteSneak()) {
                pressSneakPacket(mc.player);
            } else {
                releaseSneakPacket();
            }
            if (!singleTickInProgress) {
                if (!silentRotation) {
                    restoreLook(mc.player);
                } else if (usesDirectLookPackets()) {
                    if (!PrinterNetworkCoordinator.tryAcquire(
                            PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, 1)) {
                        return false;
                    }
                    /*? if >=26.1 {*//*
                    sendSilentLookPacket(mc.player, mc.player.getYRot(), mc.player.getXRot());
                    *//*?} else {*/
                    sendSilentLookPacket(mc.player, mc.player.getYaw(), mc.player.getPitch());
                    /*?}*/
                }
            }
        }
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingDesired = null;
        pendingFace = null;
        pendingNeedsSneak = false;
        pendingAirPlace = false;
        pendingSupportFilter = null;
        pendingItem = null;
        lookSyncedForPendingPlacement = false;
        clearPendingSelectionState();
        finishingTicks = 0;
        return false;
    }

    private static boolean tickBreaking() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
        /*?}*/
            reset();
            return false;
        }

        if (postBreakWait > 0) {
            postBreakWait--;
            if (postBreakWait <= 0) {
                restoreLook(mc.player);
                correctionTarget = null;
                correctionDesired = null;
                phase = PlacePhase.IDLE;
            }
            return false;
        }

        if (inventorySwapSettleTicks > 0 || itemVarietyCooldownTicks > 0) {
            return false;
        }

        if (breakingTicks >= MAX_BREAKING_TICKS) {
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            restoreLook(mc.player);
            correctionTarget = null;
            correctionDesired = null;
            phase = PlacePhase.IDLE;
            return false;
        }

        /*? if >=26.1 {*//*
        BlockState current = mc.level.getBlockState(correctionTarget);
        *//*?} else {*/
        BlockState current = mc.world.getBlockState(correctionTarget);
        /*?}*/
        /*? if >=26.1 {*//*
        if (current.isAir() || current.canBeReplaced()) {
        *//*?} else {*/
        if (current.isAir() || current.isReplaceable()) {
        /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            postBreakWait = 5;
            return false;
        }

        /*? if >=26.1 {*//*
        Vec3 eyePos = mc.player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = mc.player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(correctionTarget);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(correctionTarget);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toBlock = blockCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toBlock = blockCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/

        if (!PrinterNetworkCoordinator.tryAcquire(
                PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, 1)) {
            return false;
        }
        if (!PrinterNetworkCoordinator.tryAcquire(
                PrinterNetworkCoordinator.Lane.MINING, NETWORK_OWNER, 2, 1)) {
            return false;
        }

        sendLookPacket(mc.player, breakYaw, breakPitch);

        Direction breakFace = Direction.UP;
        if (breakingTicks == 0) {
            /*? if >=26.1 {*//*
            mc.gameMode.startDestroyBlock(correctionTarget, breakFace);
            *//*?} else {*/
            mc.interactionManager.attackBlock(correctionTarget, breakFace);
            /*?}*/
        } else {
            /*? if >=26.1 {*//*
            mc.gameMode.continueDestroyBlock(
            *//*?} else {*/
            mc.interactionManager.updateBlockBreakingProgress(
            /*?}*/
                    correctionTarget, breakFace);
        }
        /*? if >=26.1 {*//*
        mc.player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        mc.player.swingHand(Hand.MAIN_HAND);
        /*?}*/

        breakingTicks++;

        return false;
    }

    public static boolean placeBlock(BlockPos target, BlockState desired, boolean allowSwap) {
        return placeBlock(target, desired, allowSwap, null);
    }

    public static boolean placeBlock(BlockPos target, BlockState desired, boolean allowSwap,
                                     Predicate<BlockPos> supportFilter) {
        lastNoVisiblePlacementHitTarget = null;
        lastBodyClearancePlacementTarget = null;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) return false;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        /*?}*/
        if (phase != PlacePhase.IDLE) return false;

        // Queue this placement for later when a placement is already in-flight.
        // The queue is drained by tickPlacementQueue() as each server ACK arrives.
        if (!verifyQueue.isEmpty()) {
            return tryEnqueuePlacement(target, desired, allowSwap, supportFilter);
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        // Skip auto-created parts (door upper, bed head, tall plant upper)
        Block desiredBlock = desired.getBlock();
        if (desiredBlock instanceof DoorBlock
                /*? if >=26.1 {*//*
                && desired.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && desired.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && desired.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && desired.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return false;
        }
        if (desiredBlock instanceof BedBlock
                /*? if >=26.1 {*//*
                && desired.hasProperty(BlockStateProperties.BED_PART)
                *//*?} else {*/
                && desired.contains(Properties.BED_PART)
                /*?}*/
                /*? if >=26.1 {*//*
                && desired.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                *//*?} else {*/
                && desired.get(Properties.BED_PART) == BedPart.HEAD) {
                /*?}*/
            return false;
        }
        /*? if >=26.1 {*//*
        if (desiredBlock instanceof DoublePlantBlock
        *//*?} else {*/
        if (desiredBlock instanceof TallPlantBlock
        /*?}*/
                /*? if >=26.1 {*//*
                && desired.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && desired.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && desired.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && desired.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return false;
        }

        // Wrong orientation → break and re-place
        BlockState existing = world.getBlockState(target);
        /*? if >=26.1 {*//*
        if (!existing.isAir() && !existing.canBeReplaced()
        *//*?} else {*/
        if (!existing.isAir() && !existing.isReplaceable()
        /*?}*/
                && existing.getBlock() == desired.getBlock()
                && !existing.equals(desired)
                && isOrientationMismatch(existing, desired)) {
            /*? if >=26.1 {*//*
            BlockPos immutable = target.immutable();
            *//*?} else {*/
            BlockPos immutable = target.toImmutable();
            /*?}*/
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // Scaffold removal
        /*? if >=26.1 {*//*
        if (!existing.isAir() && !existing.canBeReplaced()
        *//*?} else {*/
        if (!existing.isAir() && !existing.isReplaceable()
        /*?}*/
                && existing.getBlock() != desired.getBlock()
                && PrinterDatabase.isScaffold(target)) {
            /*? if >=26.1 {*//*
            BlockPos immutable = target.immutable();
            *//*?} else {*/
            BlockPos immutable = target.toImmutable();
            /*?}*/
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // Foreign block replacement
        /*? if >=26.1 {*//*
        if (!existing.isAir() && !existing.canBeReplaced()
        *//*?} else {*/
        if (!existing.isAir() && !existing.isReplaceable()
        /*?}*/
                && existing.getBlock() != desired.getBlock()) {
            /*? if >=26.1 {*//*
            BlockPos immutable = target.immutable();
            *//*?} else {*/
            BlockPos immutable = target.toImmutable();
            /*?}*/
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // 1. find the required item
        Item requiredItem = desired.getBlock().asItem();
        if (requiredItem == Items.AIR) return false;

        if (getSelectedItem(player) != requiredItem && !canChangeHeldItemNow()) {
            return false;
        }

        if (playerTooCloseToPlacement(player, target)) {
            lastBodyClearancePlacementTarget = immutablePos(target);
            PacketTelemetry.mark("place blocked body-clearance target=" + target
                    + " desired=" + desired.getBlock());
            return false;
        }

        /*? if >=26.1 {*//*
        if (!world.isUnobstructed(desired, target,
                net.minecraft.world.phys.shapes.CollisionContext.empty())) {
        *//*?} else {*/
        if (!world.canPlace(desired, target,
                net.minecraft.block.ShapeContext.absent())) {
        /*?}*/
            return false;
        }
        {
            /*? if >=26.1 {*//*
            net.minecraft.world.phys.AABB placeBox =
                    placementEntityBox(target);
            *//*?} else {*/
            net.minecraft.util.math.Box placeBox =
                    placementEntityBox(target);
            /*?}*/
            /*? if >=26.1 {*//*
            List<net.minecraft.world.entity.Entity> entities =
            *//*?} else {*/
            List<net.minecraft.entity.Entity> entities =
            /*?}*/
                    /*? if >=26.1 {*//*
                    world.getEntities((net.minecraft.world.entity.Entity) null, placeBox, e -> true);
                    *//*?} else {*/
                    world.getOtherEntities(null, placeBox);
                    /*?}*/
            /*? if >=26.1 {*//*
            for (net.minecraft.world.entity.Entity entity : entities) {
            *//*?} else {*/
            for (net.minecraft.entity.Entity entity : entities) {
            /*?}*/
                if (!entity.isSpectator() && entity.isAlive()) {
                    return false;
                }
            }
        }

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/

        VisiblePlacementHit visibleHit = findBestVisiblePlacementHit(
                player, world, target, desired, eyePos, supportFilter);
        if (visibleHit == null) {
            lastNoVisiblePlacementHitTarget = immutablePos(target);
            PacketTelemetry.mark("place blocked no-visible-hit target=" + target
                    + " desired=" + desired.getBlock());
            return false;
        }
        Direction face = visibleHit.face;
        BlockHitResult placementHit = visibleHit.hit;

        long currentTick = getCurrentWorldTick();
        if (currentTick >= 0) {
            /*? if >=26.1 {*//*
            BlockPos supportPos = target.relative(face);
            *//*?} else {*/
            BlockPos supportPos = target.offset(face);
            /*?}*/
            if (isFreshAcceptedSupport(supportPos, currentTick)) {
                PacketTelemetry.mark("place defer fresh-support target=" + target
                        + " support=" + supportPos);
                return false;
            }
        }

        float desiredYaw;
        float desiredPitch;
        boolean needsSneak = false;

        {
            BlockPos neighbor = placementHit.getBlockPos();
            Block neighborBlock = world.getBlockState(neighbor).getBlock();
            needsSneak = isInteractive(neighborBlock);

            /*? if >=26.1 {*//*
            Vec3 hitPos = placementHit.getLocation();
            *//*?} else {*/
            Vec3d hitPos = placementHit.getPos();
            /*?}*/

            /*? if >=26.1 {*//*
            Vec3 toHit = hitPos.subtract(eyePos);
            *//*?} else {*/
            Vec3d toHit = hitPos.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            /*? if >=26.1 {*//*
            float hitYaw = (float) (Mth.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            float hitYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            float hitPitch = (float) -(Mth.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            float hitPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            /*?}*/
            desiredYaw = hitYaw;
            desiredPitch = hitPitch;

            Float facingYaw = getRequiredYaw(desired);
            Float facingPitch = getRequiredPitch(desired);
            if (facingYaw != null) {
                float orientationYaw = facingYaw;
                float orientationPitch = facingPitch != null
                        ? facingPitch
                        : computePitchToward(eyePos, hitPos);
                if (isOrientationLookCloseToHit(orientationYaw, orientationPitch, hitYaw, hitPitch)) {
                    desiredYaw = orientationYaw;
                    desiredPitch = orientationPitch;
                }
            } else if (facingPitch != null
                    && isOrientationLookCloseToHit(hitYaw, facingPitch, hitYaw, hitPitch)) {
                desiredPitch = facingPitch;
            }
        }

        /*? if >=26.1 {*//*
        pendingTarget = target.immutable();
        *//*?} else {*/
        pendingTarget = target.toImmutable();
        /*?}*/
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = false;
        pendingSupportFilter = supportFilter;
        pendingNeedsSneak = needsSneak;
        pendingItem = requiredItem;
        /*? if >=26.1 {*//*
        targetYaw = snapToMouseGCD(desiredYaw, player.getYRot());
        *//*?} else {*/
        targetYaw = snapToMouseGCD(desiredYaw, player.getYaw());
        /*?}*/
        /*? if >=26.1 {*//*
        targetPitch = Mth.clamp(
        *//*?} else {*/
        targetPitch = MathHelper.clamp(
        /*?}*/
                /*? if >=26.1 {*//*
                snapToMouseGCD(desiredPitch, player.getXRot()),
                *//*?} else {*/
                snapToMouseGCD(desiredPitch, player.getPitch()),
                /*?}*/
                -90.0f, 90.0f);
        /*? if >=26.1 {*//*
        savedYaw = player.getYRot();
        *//*?} else {*/
        savedYaw = player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        savedPitch = player.getXRot();
        *//*?} else {*/
        savedPitch = player.getPitch();
        /*?}*/
        clearPendingSelectionState();
        ItemSelectionResult selection = selectItem(player, mc, requiredItem, allowSwap);
        if (selection == ItemSelectionResult.FAILED) {
            pendingTarget = null;
            pendingDesired = null;
            pendingFace = null;
            pendingNeedsSneak = false;
            pendingAirPlace = false;
            pendingSupportFilter = null;
            pendingItem = null;
            return false;
        }
        if (selection == ItemSelectionResult.STAGED) {
            return true;
        }
        rotateTicks = 0;
        lookSyncedForPendingPlacement = false;
        phase = PlacePhase.ROTATING;

        return true;
    }

    /*? if >=26.1 {*//*
    private static net.minecraft.world.phys.AABB placementEntityBox(BlockPos target) {
        return net.minecraft.world.phys.AABB
                .unitCubeFromLowerCorner(Vec3.atLowerCornerOf(target))
                .inflate(PLACEMENT_ENTITY_MARGIN);
    }
    *//*?} else {*/
    private static net.minecraft.util.math.Box placementEntityBox(BlockPos target) {
        return new net.minecraft.util.math.Box(
                target.getX(), target.getY(), target.getZ(),
                target.getX() + 1.0, target.getY() + 1.0, target.getZ() + 1.0)
                .expand(PLACEMENT_ENTITY_MARGIN);
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private static boolean playerTooCloseToPlacement(LocalPlayer player, BlockPos target) {
        return player.getBoundingBox().intersects(placementEntityBox(target));
    }
    *//*?} else {*/
    private static boolean playerTooCloseToPlacement(ClientPlayerEntity player, BlockPos target) {
        return player.getBoundingBox().intersects(placementEntityBox(target));
    }
    /*?}*/

    // Effective max placement reach (blocks). Reads block_interaction_range
    // (1.20.5+) so survival gets 4.5, creative 6.0, server overrides honored.
    // Subtracts a small safety margin and floors at SAFE_PLACE_REACH_BLOCKS.
    // Vanilla sends placement packets to the attribute value and strict
    // validation follows the same attribute; the old hardcoded 3.85 was too
    // conservative and rejected legitimate edge-of-reach placements.
    /*? if >=26.1 {*//*
    private static double effectivePlaceReach(LocalPlayer player) {
        double attr;
        try {
            attr = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        } catch (Throwable ignored) {
            attr = 4.5;
        }
        if (attr <= 0.0 || Double.isNaN(attr)) attr = 4.5;
        double effective = Math.min(REACH_HARD_CEILING, attr) - REACH_AC_SAFETY_MARGIN;
        return Math.max(SAFE_PLACE_REACH_BLOCKS, effective);
    }
    *//*?} else {*/
    private static double effectivePlaceReach(ClientPlayerEntity player) {
        double attr;
        try {
            attr = player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE);
        } catch (Throwable ignored) {
            attr = 4.5;
        }
        if (attr <= 0.0 || Double.isNaN(attr)) attr = 4.5;
        double effective = Math.min(REACH_HARD_CEILING, attr) - REACH_AC_SAFETY_MARGIN;
        return Math.max(SAFE_PLACE_REACH_BLOCKS, effective);
    }
    /*?}*/

    /** Squared form of effectivePlaceReach for distance comparisons. */
    /*? if >=26.1 {*//*
    private static double effectivePlaceReachSq(LocalPlayer player) {
    *//*?} else {*/
    private static double effectivePlaceReachSq(ClientPlayerEntity player) {
    /*?}*/
        double r = effectivePlaceReach(player);
        return r * r;
    }

    public static boolean placeLiquid(BlockPos target, BlockState desired, boolean allowSwap) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) return false;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        /*?}*/
        if (phase != PlacePhase.IDLE) return false;

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        Item bucketItem;
        Block block = desired.getBlock();
        if (block == Blocks.WATER) bucketItem = Items.WATER_BUCKET;
        else if (block == Blocks.LAVA) bucketItem = Items.LAVA_BUCKET;
        else return false;

        BlockState currentState = world.getBlockState(target);
        /*? if >=26.1 {*//*
        if (!currentState.isAir() && !currentState.canBeReplaced()) {
        *//*?} else {*/
        if (!currentState.isAir() && !currentState.isReplaceable()) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (currentState.getBlock() instanceof LiquidBlock) {
            *//*?} else {*/
            if (currentState.getBlock() instanceof FluidBlock) {
            /*?}*/
                /*? if >=26.1 {*//*
                if (currentState.getFluidState().isSource()) {
                *//*?} else {*/
                if (currentState.getFluidState().isStill()) {
                /*?}*/
                    return false;
                }
            } else {
                return false;
            }
        }

        // Eye must be at or above target for correct ray cast
        /*? if >=26.1 {*//*
        if (player.getEyePosition().y < target.getY()) {
        *//*?} else {*/
        if (player.getEyePos().y < target.getY()) {
        /*?}*/
            return false;
        }

        if (getSelectedItem(player) != bucketItem && !canChangeHeldItemNow()) {
            return false;
        }

        Direction face = findPlacementFace(world, target);
        if (face == null) return false;

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        float desiredYaw;
        float desiredPitch;
        boolean needsSneak = false;

        {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(face);
            *//*?} else {*/
            BlockPos neighbor = target.offset(face);
            /*?}*/
            Block neighborBlock = world.getBlockState(neighbor).getBlock();
            needsSneak = isInteractive(neighborBlock);

            Direction clickSide = face.getOpposite();
            /*? if >=26.1 {*//*
            Vec3 faceCenter = Vec3.atCenterOf(neighbor)
            *//*?} else {*/
            Vec3d faceCenter = Vec3d.ofCenter(neighbor)
            /*?}*/
                    /*? if >=26.1 {*//*
                    .add(Vec3.atLowerCornerOf(clickSide.getUnitVec3i()).scale(0.5));
                    *//*?} else {*/
                    .add(Vec3d.of(clickSide.getVector()).multiply(0.5));
                    /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toHit = faceCenter.subtract(eyePos);
            *//*?} else {*/
            Vec3d toHit = faceCenter.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            /*? if >=26.1 {*//*
            desiredYaw = (float) (Mth.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            desiredPitch = (float) -(Mth.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            /*?}*/
        }

        /*? if >=26.1 {*//*
        pendingTarget = target.immutable();
        *//*?} else {*/
        pendingTarget = target.toImmutable();
        /*?}*/
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = false;  // liquids never air-place
        pendingNeedsSneak = needsSneak;
        pendingItem = bucketItem;
        /*? if >=26.1 {*//*
        targetYaw = snapToMouseGCD(desiredYaw, player.getYRot());
        *//*?} else {*/
        targetYaw = snapToMouseGCD(desiredYaw, player.getYaw());
        /*?}*/
        /*? if >=26.1 {*//*
        targetPitch = Mth.clamp(
        *//*?} else {*/
        targetPitch = MathHelper.clamp(
        /*?}*/
                /*? if >=26.1 {*//*
                snapToMouseGCD(desiredPitch, player.getXRot()),
                *//*?} else {*/
                snapToMouseGCD(desiredPitch, player.getPitch()),
                /*?}*/
                -90.0f, 90.0f);
        /*? if >=26.1 {*//*
        savedYaw = player.getYRot();
        *//*?} else {*/
        savedYaw = player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        savedPitch = player.getXRot();
        *//*?} else {*/
        savedPitch = player.getPitch();
        /*?}*/
        clearPendingSelectionState();
        ItemSelectionResult selection = selectItem(player, mc, bucketItem, allowSwap);
        if (selection == ItemSelectionResult.FAILED) {
            pendingTarget = null;
            pendingDesired = null;
            pendingFace = null;
            pendingNeedsSneak = false;
            pendingAirPlace = false;
            pendingItem = null;
            return false;
        }
        if (selection == ItemSelectionResult.STAGED) {
            return true;
        }
        rotateTicks = 0;
        lookSyncedForPendingPlacement = false;
        phase = PlacePhase.ROTATING;

        return true;
    }
    public static int placeBatch(List<BlockPos> targets, List<BlockState> states,
                                 boolean allowSwap) {
        // Strict server validation and region-threaded servers expect block use
        // packets to stay aligned to world ticks. Bursting several placements
        // in one client tick causes more harm than it saves, so keep batch
        // placement disabled and fall back to the single-placement path.
        return 0;
    }

    /*? if >=26.1 {*//*
    private static Direction getFaceTowardPlayer(LocalPlayer player,
    *//*?} else {*/
    private static Direction getFaceTowardPlayer(ClientPlayerEntity player,
    /*?}*/
                                                 BlockPos pos) {
        /*? if >=26.1 {*//*
        Vec3 eye = player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 center = Vec3.atCenterOf(pos);
        *//*?} else {*/
        Vec3d center = Vec3d.ofCenter(pos);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 delta = eye.subtract(center);
        *//*?} else {*/
        Vec3d delta = eye.subtract(center);
        /*?}*/

        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);

        if (ay >= ax && ay >= az) return delta.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= ay && ax >= az) return delta.x > 0 ? Direction.EAST : Direction.WEST;
        return delta.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Break the misplaced block for re-placement.
     */
    private static boolean startCorrection(BlockPos target, BlockState desired,
                                           /*? if >=26.1 {*//*
                                           LocalPlayer player,
                                           *//*?} else {*/
                                           ClientPlayerEntity player,
                                           /*?}*/
                                           /*? if >=26.1 {*//*
                                           Minecraft mc) {
                                           *//*?} else {*/
                                           MinecraftClient mc) {
                                           /*?}*/
        /*? if >=26.1 {*//*
        correctionTarget = target.immutable();
        *//*?} else {*/
        correctionTarget = target.toImmutable();
        /*?}*/
        correctionDesired = desired;
        breakingTicks = 0;
        /*? if >=26.1 {*//*
        savedYaw = player.getYRot();
        *//*?} else {*/
        savedYaw = player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        savedPitch = player.getXRot();
        *//*?} else {*/
        savedPitch = player.getPitch();
        /*?}*/

        /*? if >=26.1 {*//*
        BlockState existing = mc.level.getBlockState(target);
        *//*?} else {*/
        BlockState existing = mc.world.getBlockState(target);
        /*?}*/
        selectBestTool(player, mc, existing);

        if (inventorySwapSettleTicks > 0 || itemVarietyCooldownTicks > 0) {
            phase = PlacePhase.BREAKING;
            return true;
        }

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(target);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(target);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toBlock = blockCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toBlock = blockCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/

        if (!PrinterNetworkCoordinator.tryAcquire(
                PrinterNetworkCoordinator.Lane.LOOK, NETWORK_OWNER, 1, 1)
                || !PrinterNetworkCoordinator.tryAcquire(
                PrinterNetworkCoordinator.Lane.MINING, NETWORK_OWNER, 1, 1)) {
            phase = PlacePhase.BREAKING;
            return true;
        }
        sendLookPacket(player, breakYaw, breakPitch);
        /*? if >=26.1 {*//*
        mc.gameMode.startDestroyBlock(target, Direction.UP);
        *//*?} else {*/
        mc.interactionManager.attackBlock(target, Direction.UP);
        /*?}*/
        phase = PlacePhase.BREAKING;
        return true;
    }

    /** Computes a hit position on the given face via ray cast, falling back to face center. */
    /*? if >=26.1 {*//*
    private static Vec3 getFaceCenterHit(BlockPos neighbor, Direction face) {
        return Vec3.atCenterOf(neighbor)
                .add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
    }
    *//*?} else {*/
    private static Vec3d getFaceCenterHit(BlockPos neighbor, Direction face) {
        return Vec3d.ofCenter(neighbor)
                .add(Vec3d.of(face.getVector()).multiply(0.5));
    }
    /*?}*/

    /** Computes a hit position on the given face via ray cast, falling back to face center. */
    /*? if >=26.1 {*//*
    private static Vec3 computeRayFaceHit(Vec3 eyePos, float yaw, float pitch,
    *//*?} else {*/
    private static Vec3d computeRayFaceHit(Vec3d eyePos, float yaw, float pitch,
    /*?}*/
                                            /*? if >=26.1 {*//*
                                            BlockPos neighbor, Direction face, Level world) {
                                            *//*?} else {*/
                                            BlockPos neighbor, Direction face, World world) {
                                            /*?}*/
        // Ray direction from yaw/pitch
        float yawRad  = (float) Math.toRadians(-yaw - 180.0f);
        float pitchRad = (float) Math.toRadians(-pitch);
        /*? if >=26.1 {*//*
        float cosP = Mth.cos(pitchRad);
        *//*?} else {*/
        float cosP = MathHelper.cos(pitchRad);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 lookDir = new Vec3(
        *//*?} else {*/
        Vec3d lookDir = new Vec3d(
        /*?}*/
                /*? if >=26.1 {*//*
                Mth.sin(yawRad) * cosP,
                *//*?} else {*/
                MathHelper.sin(yawRad) * cosP,
                /*?}*/
                /*? if >=26.1 {*//*
                Mth.sin(pitchRad),
                *//*?} else {*/
                MathHelper.sin(pitchRad),
                /*?}*/
                /*? if >=26.1 {*//*
                Mth.cos(yawRad) * cosP
                *//*?} else {*/
                MathHelper.cos(yawRad) * cosP
                /*?}*/
        );

        // Face plane: the face of the neighbor block
        // The face is on the surface of the neighbor block at `face` direction
        /*? if >=26.1 {*//*
        Vec3 faceCenter = Vec3.atCenterOf(neighbor)
        *//*?} else {*/
        Vec3d faceCenter = Vec3d.ofCenter(neighbor)
        /*?}*/
                /*? if >=26.1 {*//*
                .add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
                *//*?} else {*/
                .add(Vec3d.of(face.getVector()).multiply(0.5));
                /*?}*/

        // Normal of the face
        /*? if >=26.1 {*//*
        Vec3 faceNormal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        *//*?} else {*/
        Vec3d faceNormal = Vec3d.of(face.getVector());
        /*?}*/

        // Ray-plane intersection: t = dot(faceCenter - eyePos, normal) / dot(lookDir, normal)
        /*? if >=26.1 {*//*
        double denom = lookDir.dot(faceNormal);
        *//*?} else {*/
        double denom = lookDir.dotProduct(faceNormal);
        /*?}*/
        if (Math.abs(denom) < 1e-6) {
            // Ray is nearly parallel to face — use face center
            return faceCenter;
        }

        /*? if >=26.1 {*//*
        double t = faceCenter.subtract(eyePos).dot(faceNormal) / denom;
        *//*?} else {*/
        double t = faceCenter.subtract(eyePos).dotProduct(faceNormal) / denom;
        /*?}*/
        if (t < 0) {
            // Intersection is behind the player — use face center
            return faceCenter;
        }

        /*? if >=26.1 {*//*
        Vec3 intersection = eyePos.add(lookDir.scale(t));
        *//*?} else {*/
        Vec3d intersection = eyePos.add(lookDir.multiply(t));
        /*?}*/

        // Clamp to the face bounds (block goes from neighbor to neighbor+1)
        double hx = clampToFace(intersection.x, neighbor.getX(), face, Direction.Axis.X);
        double hy = clampToFace(intersection.y, neighbor.getY(), face, Direction.Axis.Y);
        double hz = clampToFace(intersection.z, neighbor.getZ(), face, Direction.Axis.Z);

        /*? if >=26.1 {*//*
        return new Vec3(hx, hy, hz);
        *//*?} else {*/
        return new Vec3d(hx, hy, hz);
        /*?}*/
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
            /*? if >=26.1 {*//*
            return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max : min;
            *//*?} else {*/
            return face.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
            /*?}*/
        }

        // Clamp to block bounds with small inset to avoid exact edges
        /*? if >=26.1 {*//*
        return Mth.clamp(value, min + 0.01, max - 0.01);
        *//*?} else {*/
        return MathHelper.clamp(value, min + 0.01, max - 0.01);
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private static Vec3 offsetFaceHit(Vec3 baseHit, BlockPos neighbor, Direction face,
                                      double primaryOffset, double secondaryOffset) {
        double x = baseHit.x;
        double y = baseHit.y;
        double z = baseHit.z;
        switch (face.getAxis()) {
            case X -> {
                y = clampFaceInterior(y + primaryOffset, neighbor.getY());
                z = clampFaceInterior(z + secondaryOffset, neighbor.getZ());
            }
            case Y -> {
                x = clampFaceInterior(x + primaryOffset, neighbor.getX());
                z = clampFaceInterior(z + secondaryOffset, neighbor.getZ());
            }
            case Z -> {
                x = clampFaceInterior(x + primaryOffset, neighbor.getX());
                y = clampFaceInterior(y + secondaryOffset, neighbor.getY());
            }
        }
        return new Vec3(x, y, z);
    }
    *//*?} else {*/
    private static Vec3d offsetFaceHit(Vec3d baseHit, BlockPos neighbor, Direction face,
                                       double primaryOffset, double secondaryOffset) {
        double x = baseHit.x;
        double y = baseHit.y;
        double z = baseHit.z;
        switch (face.getAxis()) {
            case X -> {
                y = clampFaceInterior(y + primaryOffset, neighbor.getY());
                z = clampFaceInterior(z + secondaryOffset, neighbor.getZ());
            }
            case Y -> {
                x = clampFaceInterior(x + primaryOffset, neighbor.getX());
                z = clampFaceInterior(z + secondaryOffset, neighbor.getZ());
            }
            case Z -> {
                x = clampFaceInterior(x + primaryOffset, neighbor.getX());
                y = clampFaceInterior(y + secondaryOffset, neighbor.getY());
            }
        }
        return new Vec3d(x, y, z);
    }
    /*?}*/

    private static double clampFaceInterior(double value, int blockOrigin) {
        /*? if >=26.1 {*//*
        return Mth.clamp(value, blockOrigin + 0.08, blockOrigin + 0.92);
        *//*?} else {*/
        return MathHelper.clamp(value, blockOrigin + 0.08, blockOrigin + 0.92);
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private static boolean trySequencedBlockPlacement(LocalPlayer player, BlockHitResult hitResult) {
        return false;
    }
    *//*?} else {*/
    private static boolean trySequencedBlockPlacement(ClientPlayerEntity player, BlockHitResult hitResult) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            lastSequenceFailure = "no-world";
            PacketTelemetry.mark("place direct unavailable reason=" + lastSequenceFailure);
            return false;
        }

        lastSequenceFailure = "lookup-handler";
        Object pendingUpdateManager = invokeNoArg(mc.world,
                "getPendingUpdateManager",
                "getBlockStatePredictionHandler",
                "method_41925");
        if (pendingUpdateManager == null) {
            pendingUpdateManager = findMemberByTypeName(mc.world, "PendingUpdateManager");
        }
        if (pendingUpdateManager == null) {
            pendingUpdateManager = findMemberByTypeName(mc.world, "BlockStatePredictionHandler");
        }
        if (pendingUpdateManager == null) {
            pendingUpdateManager = findMemberByTypeName(mc.world, "class_7202");
        }
        if (pendingUpdateManager == null) {
            lastSequenceFailure = "no-prediction-handler world=" + mc.world.getClass().getName();
            PacketTelemetry.mark("place direct unavailable reason=" + lastSequenceFailure);
            return false;
        }

        lastSequenceFailure = "start-predicting handler=" + pendingUpdateManager.getClass().getName();
        Object sequenceHandle = invokeNoArg(pendingUpdateManager,
                "incrementSequence",
                "startPredicting",
                "method_41937");
        if (sequenceHandle == null) {
            lastSequenceFailure = "no-start-predicting handler=" + pendingUpdateManager.getClass().getName();
            PacketTelemetry.mark("place direct unavailable reason=" + lastSequenceFailure);
            return false;
        }

        try {
            lastSequenceFailure = "read-sequence handle=" + sequenceHandle.getClass().getName();
            Object sequence = invokeNoArg(sequenceHandle,
                    "getSequence",
                    "currentSequence",
                    "method_41942");
            if (sequence instanceof Integer value) {
                lastSequenceFailure = "";
                lastSentBlockUseSequence = value;
                // Swap item to offhand, place from offhand, swap back — keeps strict server validation happy.
                player.networkHandler.sendPacket(
                        new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                                net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN));
                player.networkHandler.sendPacket(
                        new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(
                                Hand.OFF_HAND, hitResult, value));
                player.networkHandler.sendPacket(
                        new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                                net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                net.minecraft.util.math.BlockPos.ORIGIN, net.minecraft.util.math.Direction.DOWN));
                return true;
            }
            lastSequenceFailure = "no-current-sequence handle=" + sequenceHandle.getClass().getName();
            PacketTelemetry.mark("place direct unavailable reason=" + lastSequenceFailure);
            return false;
        } finally {
            closeQuietly(sequenceHandle);
        }
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private static void sendPlacementSwing(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }
    *//*?} else {*/
    private static void sendPlacementSwing(ClientPlayerEntity player) {
        player.swingHand(Hand.MAIN_HAND);
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private static void syncSelectedSlotPacket(LocalPlayer player) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (selectedSlot == lastSentSelectedSlot) {
            PacketTelemetry.mark("slot sync skip selected=" + selectedSlot);
            updateInteractionManagerSelectedSlot(selectedSlot);
            return;
        }
        lastSentSelectedSlot = selectedSlot;
        updateInteractionManagerSelectedSlot(selectedSlot);
        PacketTelemetry.mark("slot sync sent selected=" + selectedSlot);
        player.connection.send(new ServerboundSetCarriedItemPacket(selectedSlot));
    }
    *//*?} else {*/
    private static void syncSelectedSlotPacket(ClientPlayerEntity player) {
        /*? if >=1.21.5 {*//*
        int selectedSlot = player.getInventory().getSelectedSlot();
        *//*?} else {*/
        int selectedSlot = player.getInventory().selectedSlot;
        /*?}*/
        if (selectedSlot == lastSentSelectedSlot) {
            PacketTelemetry.mark("slot sync skip selected=" + selectedSlot);
            updateInteractionManagerSelectedSlot(selectedSlot);
            return;
        }
        lastSentSelectedSlot = selectedSlot;
        updateInteractionManagerSelectedSlot(selectedSlot);
        PacketTelemetry.mark("slot sync sent selected=" + selectedSlot);
        player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
    }
    /*?}*/

    private static void updateInteractionManagerSelectedSlot(int selectedSlot) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        Object interactionManager = mc.gameMode;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        Object interactionManager = mc.interactionManager;
        /*?}*/
        if (interactionManager == null) {
            return;
        }
        setIntFieldIfPresent(interactionManager, selectedSlot, "carriedIndex", "selectedSlot", "lastSelectedSlot");
    }

    private static Integer nextBlockUseSequence() {
        /*? if >=26.1 {*//*
        return null;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            lastSequenceFailure = "no-world";
            return null;
        }

        lastSequenceFailure = "lookup-handler";
        Object pendingUpdateManager = invokeNoArg(mc.world,
                "getPendingUpdateManager",
                "getBlockStatePredictionHandler",
                "method_41925");
        if (pendingUpdateManager == null) {
            pendingUpdateManager = findMemberByTypeName(mc.world, "PendingUpdateManager");
        }
        if (pendingUpdateManager == null) {
            pendingUpdateManager = findMemberByTypeName(mc.world, "BlockStatePredictionHandler");
        }
        if (pendingUpdateManager == null) {
            pendingUpdateManager = findMemberByTypeName(mc.world, "class_7202");
        }
        if (pendingUpdateManager == null) {
            lastSequenceFailure = "no-prediction-handler world=" + mc.world.getClass().getName();
            return null;
        }

        lastSequenceFailure = "start-predicting handler=" + pendingUpdateManager.getClass().getName();
        Object sequenceHandle = invokeNoArg(pendingUpdateManager,
                "incrementSequence",
                "startPredicting",
                "method_41937");
        if (sequenceHandle == null) {
            lastSequenceFailure = "no-start-predicting handler=" + pendingUpdateManager.getClass().getName();
            return null;
        }

        try {
            lastSequenceFailure = "read-sequence handle=" + sequenceHandle.getClass().getName();
            Object sequence = invokeNoArg(sequenceHandle,
                    "getSequence",
                    "currentSequence",
                    "method_41942");
            if (sequence instanceof Integer value) {
                lastSequenceFailure = "";
                return value;
            }
            lastSequenceFailure = "no-current-sequence handle=" + sequenceHandle.getClass().getName();
            return null;
        } finally {
            closeQuietly(sequenceHandle);
        }
        /*?}*/
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // keep walking
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = invokeNoArg(target, methodName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object findMemberByTypeName(Object target, String simpleTypeName) {
        if (target == null) {
            return null;
        }

        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterCount() == 0
                        && method.getReturnType().getSimpleName().equals(simpleTypeName)) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target);
                    } catch (ReflectiveOperationException ignored) {
                        return null;
                    }
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (field.getType().getSimpleName().equals(simpleTypeName)) {
                    try {
                        field.setAccessible(true);
                        return field.get(target);
                    } catch (ReflectiveOperationException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static void closeQuietly(Object value) {
        if (value instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static void setIntFieldIfPresent(Object target, int value, String... candidateNames) {
        if (target == null) {
            return;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (String candidateName : candidateNames) {
                try {
                    Field field = type.getDeclaredField(candidateName);
                    if (field.getType() != int.class) {
                        continue;
                    }
                    field.setAccessible(true);
                    field.setInt(target, value);
                    return;
                } catch (ReflectiveOperationException ignored) {
                    // try next field
                }
            }
        }
    }

    /*? if >=26.1 {*//*
    private static boolean shouldClientSwing(InteractionResult result) {
        return result instanceof InteractionResult.Success success
                && success.e() == InteractionResult.SwingSource.CLIENT;
    }
    *//*?} else {*/
    private static boolean shouldClientSwing(ActionResult result) {
        Boolean reflected = invokeBooleanNoArg(result, "shouldSwingHand", "shouldSwing");
        if (reflected != null) {
            return reflected;
        }
        return result == ActionResult.SUCCESS;
    }
    /*?}*/

    private static Boolean invokeBooleanNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (String methodName : methodNames) {
                try {
                    Method method = type.getDeclaredMethod(methodName);
                    if (method.getReturnType() != boolean.class
                            && method.getReturnType() != Boolean.class) {
                        continue;
                    }
                    method.setAccessible(true);
                    Object value = method.invoke(target);
                    if (value instanceof Boolean bool) {
                        return bool;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // try next method name
                }
            }
        }
        return null;
    }

    /** Sends a look packet without modifying client-side rotation. */
    /*? if >=26.1 {*//*
    private static void sendSilentLookPacket(LocalPlayer player, float yaw, float pitch) {
    *//*?} else {*/
    private static void sendSilentLookPacket(ClientPlayerEntity player, float yaw, float pitch) {
    /*?}*/
        if (!usesDirectLookPackets()) {
            setLookRotation(player, yaw, pitch);
            return;
        }
        yaw = wrapDegrees180(yaw);
        /*? if >=26.1 {*//*
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        *//*?} else {*/
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        /*?}*/
        /*? if >=26.1 {*//*
        player.connection.send(
                new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                        player.onGround(), player.horizontalCollision));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround(), player.horizontalCollision));
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private static void sendImmediateLookSync(LocalPlayer player, float yaw, float pitch) {
        yaw = wrapDegrees180(yaw);
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        player.connection.send(
                new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                        player.onGround(), player.horizontalCollision));
    }
    *//*?} else {*/
    private static void sendImmediateLookSync(ClientPlayerEntity player, float yaw, float pitch) {
        yaw = wrapDegrees180(yaw);
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(
                        yaw, pitch,
                        player.isOnGround(), player.horizontalCollision));
    }
    /*?}*/

    /** Sets entity yaw/pitch and sends a matching look packet. */
    /*? if >=26.1 {*//*
    public static void sendLookPacket(LocalPlayer player, float yaw, float pitch) {
    *//*?} else {*/
    public static void sendLookPacket(ClientPlayerEntity player, float yaw, float pitch) {
    /*?}*/
        if (!usesDirectLookPackets()) {
            setLookRotation(player, yaw, pitch);
            return;
        }
        yaw = wrapDegrees180(yaw);
        /*? if >=26.1 {*//*
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        *//*?} else {*/
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        /*?}*/
        /*? if >=26.1 {*//*
        player.setYRot(yaw);
        *//*?} else {*/
        player.setYaw(yaw);
        /*?}*/
        /*? if >=26.1 {*//*
        player.setXRot(pitch);
        *//*?} else {*/
        player.setPitch(pitch);
        /*?}*/
        /*? if >=26.1 {*//*
        player.connection.send(
                new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                        player.onGround(), player.horizontalCollision));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround(), player.horizontalCollision));
        /*?}*/
    }

    // Sets yaw/pitch with jitter, relying on vanilla's end-of-tick flying
    // packet to avoid duplicate-packet flags under strict server validation.
    /*? if >=26.1 {*//*
    public static void setLookRotation(LocalPlayer player, float yaw, float pitch) {
    *//*?} else {*/
    public static void setLookRotation(ClientPlayerEntity player, float yaw, float pitch) {
    /*?}*/
        float jitter = ThreadLocalRandom.current().nextFloat() * 0.02f - 0.01f;
        /*? if >=26.1 {*//*
        pitch = Mth.clamp(pitch + jitter, -90.0f, 90.0f);
        *//*?} else {*/
        pitch = MathHelper.clamp(pitch + jitter, -90.0f, 90.0f);
        /*?}*/
        yaw = wrapDegrees180(yaw + jitter);
        /*? if >=26.1 {*//*
        player.setYRot(yaw);
        *//*?} else {*/
        player.setYaw(yaw);
        /*?}*/
        /*? if >=26.1 {*//*
        player.setXRot(pitch);
        *//*?} else {*/
        player.setPitch(pitch);
        /*?}*/
    }

    /** Forces sneak on for placement; returns a Runnable that restores it. */
    /*? if >=26.1 {*//*
    public static Runnable ensureSneakForPlacement(LocalPlayer player) {
    *//*?} else {*/
    public static Runnable ensureSneakForPlacement(ClientPlayerEntity player) {
    /*?}*/
        boolean wasAbsoluteSneak = SneakOverride.isForceAbsoluteSneak();
        boolean wasForceSneak = SneakOverride.isForceSneak();
        SneakOverride.setForceSneak(true);
        /*? if >=26.1 {*//*
        player.setShiftKeyDown(true);
        *//*?} else {*/
        player.setSneaking(true);
        /*?}*/
        pressSneakPacket(player);
        return () -> {
            if (!wasAbsoluteSneak) SneakOverride.setForceAbsoluteSneak(false);
            if (!wasForceSneak) SneakOverride.setForceSneak(false);
            if (!wasAbsoluteSneak && !wasForceSneak) {
                /*? if >=26.1 {*//*
                player.setShiftKeyDown(false);
                *//*?} else {*/
                player.setSneaking(false);
                /*?}*/
                releaseSneakPacket();
            }
        };
    }

    /** Releases sneak for interaction; returns a Runnable that restores it. */
    /*? if >=26.1 {*//*
    public static Runnable releaseForInteraction(LocalPlayer player) {
    *//*?} else {*/
    public static Runnable releaseForInteraction(ClientPlayerEntity player) {
    /*?}*/
        boolean wasAbsoluteSneak = SneakOverride.isForceAbsoluteSneak();
        boolean wasForceSneak = SneakOverride.isForceSneak();
        SneakOverride.setForceAbsoluteSneak(false);
        SneakOverride.setForceSneak(false);
        /*? if >=26.1 {*//*
        player.setShiftKeyDown(false);
        *//*?} else {*/
        player.setSneaking(false);
        /*?}*/
        if (wasAbsoluteSneak || wasForceSneak) {
            releaseSneakPacket();
        }
        return () -> {
            if (wasAbsoluteSneak) SneakOverride.setForceAbsoluteSneak(true);
            if (wasForceSneak) SneakOverride.setForceSneak(true);
            if (wasAbsoluteSneak || wasForceSneak) {
                pressSneakPacket(player);
            }
        };
    }

    /*? if >=26.1 {*//*
    private static void pressSneakPacket(LocalPlayer player) {
        // Newer versions rely on the local sneak override and vanilla movement packets.
    }
    *//*?} else {*/
    private static void pressSneakPacket(ClientPlayerEntity player) {
        /*? if >=1.21.5 {*//*
        // Newer versions rely on the local sneak override and vanilla movement packets.
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        /*?}*/
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private static void releaseSneakPacket() {
        // Newer versions rely on the local sneak override and vanilla movement packets.
    }
    *//*?} else {*/
    private static void releaseSneakPacket() {
        /*? if >=1.21.5 {*//*
        // Newer versions rely on the local sneak override and vanilla movement packets.
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        /*?}*/
    }
    /*?}*/

    /** Restores local camera aim without sending extra restore packets. */
    /*? if >=26.1 {*//*
    private static void restoreLook(LocalPlayer player) {
    *//*?} else {*/
    private static void restoreLook(ClientPlayerEntity player) {
    /*?}*/
        /*? if >=26.1 {*//*
        float currentYaw = player.getYRot();
        *//*?} else {*/
        float currentYaw = player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        float currentPitch = player.getXRot();
        *//*?} else {*/
        float currentPitch = player.getPitch();
        /*?}*/

        /*? if >=26.1 {*//*
        float yawDiff = Mth.wrapDegrees(savedYaw - currentYaw);
        *//*?} else {*/
        float yawDiff = MathHelper.wrapDegrees(savedYaw - currentYaw);
        /*?}*/
        float pitchDiff = savedPitch - currentPitch;

        // Lerp 65% back toward saved look
        float newYaw = currentYaw + yawDiff * 0.65f;
        float newPitch = currentPitch + pitchDiff * 0.65f;

        // Snap if close
        if (Math.abs(yawDiff) < 1.5f && Math.abs(pitchDiff) < 1.5f) {
            newYaw = savedYaw;
            newPitch = savedPitch;
        }

        setLookRotation(player, newYaw, newPitch);
    }

    // inventory helpers

    /** Selects the required item in the hotbar (or swaps from inventory). */
    /*? if >=26.1 {*//*
    public static ItemSelectionResult selectItem(LocalPlayer player, Minecraft mc,
    *//*?} else {*/
    public static ItemSelectionResult selectItem(ClientPlayerEntity player, MinecraftClient mc,
    /*?}*/
                                     Item item, boolean allowSwap) {
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/

        // check current slot first
        /*? if >=26.1 {*//*
        if (inv.getItem(inv.getSelectedSlot()).getItem() == item) {
            recordSelectedItem(item, false);
            return ItemSelectionResult.READY;
        }
        *//*?} else if >=1.21.5 {*//*
        if (inv.getStack(inv.getSelectedSlot()).getItem() == item) {
            recordSelectedItem(item, false);
            return ItemSelectionResult.READY;
        }
        *//*?} else {*/
        if (inv.getStack(inv.selectedSlot).getItem() == item) {
            recordSelectedItem(item, false);
            return ItemSelectionResult.READY;
        }
        /*?}*/

        // check rest of hotbar
        for (int i = 0; i < 9; i++) {
            /*? if >=26.1 {*//*
            if (inv.getItem(i).getItem() == item) {
            *//*?} else {*/
            if (inv.getStack(i).getItem() == item) {
            /*?}*/
                return stageHotbarSelection(i);
            }
        }

        // check main inventory and swap if allowed
        if (allowSwap) {
            for (int i = 9; i < 36; i++) {
                /*? if >=26.1 {*//*
                if (inv.getItem(i).getItem() == item) {
                *//*?} else {*/
                if (inv.getStack(i).getItem() == item) {
                /*?}*/
                    return stageInventorySwap(i, findBuilderInventorySwapHotbarSlot(inv, item));
                }
            }
        }
        return ItemSelectionResult.FAILED;
    }

    private static void recordSelectedItem(Item item, boolean changedSlot) {
        if (lastPlacementItem != null && lastPlacementItem != item) {
            itemVarietyCooldownTicks = Math.max(itemVarietyCooldownTicks, ITEM_VARIETY_SETTLE_TICKS);
        }
        if (changedSlot) {
            itemVarietyCooldownTicks = Math.max(itemVarietyCooldownTicks, HOTBAR_SELECT_SETTLE_TICKS);
        }
        lastPlacementItem = item;
    }

    /*? if >=26.1 {*//*
    private static Item getSelectedItem(LocalPlayer player) {
        return player.getInventory().getItem(player.getInventory().getSelectedSlot()).getItem();
    }
    *//*?} else {*/
    private static Item getSelectedItem(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        /*? if >=1.21.5 {*//*
        return inv.getStack(inv.getSelectedSlot()).getItem();
        *//*?} else {*/
        return inv.getStack(inv.selectedSlot).getItem();
        /*?}*/
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private static int getSelectedHotbarSlot(LocalPlayer player) {
        return player.getInventory().getSelectedSlot();
    }
    *//*?} else {*/
    private static int getSelectedHotbarSlot(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        /*? if >=1.21.5 {*//*
        return inv.getSelectedSlot();
        *//*?} else {*/
        return inv.selectedSlot;
        /*?}*/
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private static int findBuilderInventorySwapHotbarSlot(Inventory inv, Item incomingItem) {
        int selected = inv.getSelectedSlot();
        if (isReusableBuilderSwapHotbarStack(inv.getItem(selected), incomingItem)) {
            return selected;
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i != selected && isReusableBuilderSwapHotbarStack(inv.getItem(i), incomingItem)) {
                return i;
            }
        }
        return -1;
    }
    *//*?} else {*/
    private static int findBuilderInventorySwapHotbarSlot(PlayerInventory inv, Item incomingItem) {
        /*? if >=1.21.5 {*//*
        int selected = inv.getSelectedSlot();
        *//*?} else {*/
        int selected = inv.selectedSlot;
        /*?}*/
        if (isReusableBuilderSwapHotbarStack(inv.getStack(selected), incomingItem)) {
            return selected;
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i != selected && isReusableBuilderSwapHotbarStack(inv.getStack(i), incomingItem)) {
                return i;
            }
        }
        return -1;
    }
    /*?}*/

    private static boolean isReusableBuilderSwapHotbarStack(ItemStack stack, Item incomingItem) {
        if (stack == null || stack.isEmpty()) return true;
        if (stack.getItem() == incomingItem) return false;
        if (isProtectedHotbarStack(stack)) return false;
        return stack.getItem() instanceof BlockItem;
    }

    private static boolean isProtectedHotbarStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (
                /*? if >=26.1 {*//*
                stack.isDamageableItem()
                *//*?} else {*/
                stack.isDamageable()
                /*?}*/
        ) {
            return true;
        }
        Item item = stack.getItem();
        if (item == Items.TOTEM_OF_UNDYING
                || item == Items.ENDER_CHEST
                || item == Items.ENDER_PEARL
                || item == Items.FIREWORK_ROCKET
                || item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE) {
            return true;
        }
        return item instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    /*? if >=26.1 {*//*
    private static void selectHotbarSlot(LocalPlayer player, int slot) {
        Inventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot);
        // Keep the slot change local here and let the actual item-use path
        // perform the carried-slot sync in the same semantic lane as the click.
        lastSentSelectedSlot = -1;
    }
    *//*?} else {*/
    private static void selectHotbarSlot(ClientPlayerEntity player, int slot) {
        PlayerInventory inv = player.getInventory();
        /*? if >=1.21.5 {*//*
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot);
        *//*?} else {*/
        if (inv.selectedSlot == slot) return;
        inv.selectedSlot = slot;
        /*?}*/
        // Keep the slot change local here and let the actual item-use path
        // perform the carried-slot sync in the same semantic lane as the click.
        lastSentSelectedSlot = -1;
    }
    /*?}*/

    private static int inventorySlotToContainerSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    /*? if >=26.1 {*//*
    private static void swapInventorySlotIntoSelectedHotbar(LocalPlayer player, Minecraft mc, int inventorySlot) {
        swapInventorySlotIntoHotbarSlot(player, mc, inventorySlot, player.getInventory().getSelectedSlot());
    }

    private static void swapInventorySlotIntoHotbarSlot(LocalPlayer player, Minecraft mc,
                                                        int inventorySlot, int hotbarSlot) {
        int containerInvSlot = inventorySlotToContainerSlot(inventorySlot);
        int containerHotbarSlot = inventorySlotToContainerSlot(hotbarSlot);
        // Three PICKUP clicks: pick up from inv, swap into hotbar, return old hotbar item to inv.
        // Avoids SWAP click type which protocol-translation layers may mishandle when downgrading 1.21.5+ packet format.
        mc.gameMode.handleContainerInput(player.containerMenu.containerId, containerInvSlot, 0, ContainerInput.PICKUP, player);
        mc.gameMode.handleContainerInput(player.containerMenu.containerId, containerHotbarSlot, 0, ContainerInput.PICKUP, player);
        mc.gameMode.handleContainerInput(player.containerMenu.containerId, containerInvSlot, 0, ContainerInput.PICKUP, player);
        inventorySwapSettleTicks = Math.max(inventorySwapSettleTicks, INVENTORY_SWAP_SETTLE_TICKS);
    }
    *//*?} else {*/
    private static void swapInventorySlotIntoSelectedHotbar(ClientPlayerEntity player, MinecraftClient mc, int inventorySlot) {
        PlayerInventory inv = player.getInventory();
        /*? if >=1.21.5 {*//*
        swapInventorySlotIntoHotbarSlot(player, mc, inventorySlot, inv.getSelectedSlot());
        *//*?} else {*/
        swapInventorySlotIntoHotbarSlot(player, mc, inventorySlot, inv.selectedSlot);
        /*?}*/
    }

    private static void swapInventorySlotIntoHotbarSlot(ClientPlayerEntity player, MinecraftClient mc,
                                                        int inventorySlot, int hotbarSlot) {
        int containerInvSlot = inventorySlotToContainerSlot(inventorySlot);
        int containerHotbarSlot = inventorySlotToContainerSlot(hotbarSlot);
        // Three PICKUP clicks: pick up from inv, swap into hotbar, return old hotbar item to inv.
        // Avoids SWAP click type which protocol-translation layers may mishandle when downgrading 1.21.5+ packet format.
        mc.interactionManager.clickSlot(player.currentScreenHandler.syncId, containerInvSlot, 0, SlotActionType.PICKUP, player);
        mc.interactionManager.clickSlot(player.currentScreenHandler.syncId, containerHotbarSlot, 0, SlotActionType.PICKUP, player);
        mc.interactionManager.clickSlot(player.currentScreenHandler.syncId, containerInvSlot, 0, SlotActionType.PICKUP, player);
        inventorySwapSettleTicks = Math.max(inventorySwapSettleTicks, INVENTORY_SWAP_SETTLE_TICKS);
    }
    /*?}*/

    private static boolean isPlacementWindowSafe() {
        SetbackMonitor monitor = SetbackMonitor.get();
        // `isCalm()` already means we've gone a full quiet window since the
        // last setback. Requiring zero "recent" setbacks on top of that can
        // freeze the printer in a post-setback limbo where it can aim and
        // path, but never actually advances into the place step.
        if (!monitor.isCalm()) {
            return false;
        }
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.onGround();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        return mc.player.isOnGround();
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private static boolean hasStablePlacementStance(LocalPlayer player, BlockPos target) {
        BlockPos feetPos = player.blockPosition();
        if (!player.onGround()) return false;
        return target.getY() <= feetPos.getY() + 1;
    }
    *//*?} else {*/
    private static boolean hasStablePlacementStance(ClientPlayerEntity player, BlockPos target) {
        BlockPos feetPos = player.getBlockPos();
        if (!player.isOnGround()) return false;
        return target.getY() <= feetPos.getY() + 1;
    }
    /*?}*/

    /** Selects the best tool for breaking the given block. */
    /*? if >=26.1 {*//*
    public static void selectBestTool(LocalPlayer player, Minecraft mc,
    *//*?} else {*/
    public static void selectBestTool(ClientPlayerEntity player, MinecraftClient mc,
    /*?}*/
                                       BlockState state) {
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/
        float bestSpeed = 1.0f; // bare-hand baseline
        int   bestSlot  = -1;

        // Scan entire inventory (0-8 hotbar, 9-35 main)
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            /*? if >=26.1 {*//*
            float speed = stack.getDestroySpeed(state);
            *//*?} else {*/
            float speed = stack.getMiningSpeedMultiplier(state);
            /*?}*/
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot < 0) return; // no tool improves the speed

        if (bestSlot < 9) {
            /*? if >=1.21.5 {*//*
            if (inv.getSelectedSlot() != bestSlot) {
                selectHotbarSlot(player, bestSlot);
                itemVarietyCooldownTicks = Math.max(itemVarietyCooldownTicks, HOTBAR_SELECT_SETTLE_TICKS);
            }
            *//*?} else {*/
            if (inv.selectedSlot != bestSlot) {
                selectHotbarSlot(player, bestSlot);
                itemVarietyCooldownTicks = Math.max(itemVarietyCooldownTicks, HOTBAR_SELECT_SETTLE_TICKS);
            }
            /*?}*/
        } else {
            swapInventorySlotIntoSelectedHotbar(player, mc, bestSlot);
            itemVarietyCooldownTicks = Math.max(itemVarietyCooldownTicks, ITEM_VARIETY_SETTLE_TICKS);
        }
    }

    /** Snapshot of all items in the player's inventory (Item → total count). */
    public static Map<Item, Integer> getInventoryContents() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return Map.of();
        /*? if >=26.1 {*//*
        Inventory inv = mc.player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = mc.player.getInventory();
        /*?}*/
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return contents;
    }

    public static boolean hasItemInHotbar(Item item) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return false;
        /*? if >=26.1 {*//*
        Inventory inv = mc.player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = mc.player.getInventory();
        /*?}*/
        for (int i = 0; i < 9; i++) {
            /*? if >=26.1 {*//*
            if (inv.getItem(i).getItem() == item) {
            *//*?} else {*/
            if (inv.getStack(i).getItem() == item) {
            /*?}*/
                return true;
            }
        }
        return false;
    }

    public static Map<Item, Integer> getHotbarContents() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return Map.of();
        /*? if >=26.1 {*//*
        Inventory inv = mc.player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = mc.player.getInventory();
        /*?}*/
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return contents;
    }

    /** Returns the yaw needed to produce the desired FACING, or null. */
    private static Float getRequiredYaw(BlockState desired) {
        Block block = desired.getBlock();

        // Stairs — FACING is set to the player's look direction (NOT opposite)
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock) {
        *//*?} else {*/
        if (block instanceof StairsBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing);
            }
        }

        // Horizontal facing blocks (placed opposite player)
        if (block instanceof GlazedTerracottaBlock
                || block instanceof CarvedPumpkinBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof LoomBlock
                || block instanceof StonecutterBlock
                || block instanceof CraftingTableBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing.getOpposite());
            }
        }

        // Blocks that use FACING (all 6 directions) — dispenser, observer, piston
        if (block instanceof DispenserBlock
                || block instanceof ObserverBlock
                /*? if >=26.1 {*//*
                || block instanceof PistonBaseBlock) {
                *//*?} else {*/
                || block instanceof PistonBlock) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
                // UP/DOWN handled by pitch override
            }
        }

        // Anvil — uses HORIZONTAL_FACING
        if (block instanceof AnvilBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                /*? if >=26.1 {*//*
                return directionToYaw(facing.getClockWise()); // perpendicular
                *//*?} else {*/
                return directionToYaw(facing.rotateYClockwise()); // perpendicular
                /*?}*/
            }
        }

        // Chests, trapped chests, ender chests — HORIZONTAL_FACING
        if (block instanceof AbstractChestBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing.getOpposite());
            }
        }

        // Barrels — FACING (all 6 directions)
        if (block instanceof BarrelBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Shulker boxes — FACING (all 6 directions)
        if (block instanceof ShulkerBoxBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Hoppers — yaw not used (facing from clicked face)

        // End rods, lightning rods
        if (block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Rotation-based blocks (banners, signs)
        // yaw = rotation * 22.5 - 180
        if ((block instanceof AbstractBannerBlock && !(block instanceof WallBannerBlock))
                || (block instanceof SignBlock && !(block instanceof WallSignBlock))
                || (block instanceof HangingSignBlock && !(block instanceof WallHangingSignBlock))) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.ROTATION_16)) {
            *//*?} else {*/
            if (desired.contains(Properties.ROTATION)) {
            /*?}*/
                /*? if >=26.1 {*//*
                int rotation = desired.getValue(BlockStateProperties.ROTATION_16);
                *//*?} else {*/
                int rotation = desired.get(Properties.ROTATION);
                /*?}*/
                return rotation * 22.5f - 180.0f;
            }
        }

        // Trapdoors, fence gates — face opposite to the player
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock
        *//*?} else {*/
        if (block instanceof TrapdoorBlock
        /*?}*/
                || block instanceof FenceGateBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing.getOpposite());
            }
        }

        // Doors — facing = player look (not opposite)
        if (block instanceof DoorBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing);
            }
        }

        // Beds — facing = foot-to-head direction
        if (block instanceof BedBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing);
            }
        }

        // Catch-all for other HORIZONTAL_FACING blocks
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
        *//*?} else {*/
        if (desired.contains(Properties.HORIZONTAL_FACING)) {
        /*?}*/
            if (block instanceof CampfireBlock
                    || block instanceof BeehiveBlock
                    || block instanceof LecternBlock
                    || block instanceof GrindstoneBlock
                    || block instanceof BellBlock
                    || block instanceof RespawnAnchorBlock) {
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing.getOpposite());
            }
        }

        return null;
    }

    /** Returns the pitch needed for UP/DOWN facing blocks, or null. */
    private static Float getRequiredPitch(BlockState desired) {
        Block block = desired.getBlock();

        // 6-direction FACING blocks: dispenser, dropper, observer, piston
        if (block instanceof DispenserBlock
                || block instanceof ObserverBlock
                /*? if >=26.1 {*//*
                || block instanceof PistonBaseBlock) {
                *//*?} else {*/
                || block instanceof PistonBlock) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f; // look straight up
                if (facing == Direction.DOWN) return 90.0f;  // look straight down
            }
        }

        // Barrels — FACING (all 6 directions)
        if (block instanceof BarrelBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        // Shulker boxes — FACING (all 6 directions)
        if (block instanceof ShulkerBoxBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        // End rods, lightning rods — FACING (all 6)
        if (block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        return null;
    }

    /** Adjusts hit Y for correct slab/stair/trapdoor half placement. */
    /*? if >=26.1 {*//*
    private static Vec3 adjustHitForHalf(Vec3 hitPos, BlockPos neighbor,
    *//*?} else {*/
    private static Vec3d adjustHitForHalf(Vec3d hitPos, BlockPos neighbor,
    /*?}*/
                                          Direction clickSide, BlockState desired) {
        Block block = desired.getBlock();

        // Stairs
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof StairsBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            if (clickSide == Direction.UP) {
                // TOP stair on top face → force upper hit
                /*? if >=26.1 {*//*
                if (half == Half.TOP) {
                *//*?} else {*/
                if (half == BlockHalf.TOP) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, true);
                }
            } else if (clickSide == Direction.DOWN) {
                /*? if >=26.1 {*//*
                if (half == Half.BOTTOM) {
                *//*?} else {*/
                if (half == BlockHalf.BOTTOM) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, false);
                }
            } else {
                // Side face — control via Y position
                /*? if >=26.1 {*//*
                return forceHitY(hitPos, neighbor, half == Half.TOP);
                *//*?} else {*/
                return forceHitY(hitPos, neighbor, half == BlockHalf.TOP);
                /*?}*/
            }
        }

        // Slabs
        /*? if >=26.1 {*//*
        if (block instanceof SlabBlock && desired.hasProperty(BlockStateProperties.SLAB_TYPE)) {
        *//*?} else {*/
        if (block instanceof SlabBlock && desired.contains(Properties.SLAB_TYPE)) {
        /*?}*/
            /*? if >=26.1 {*//*
            SlabType type = desired.getValue(BlockStateProperties.SLAB_TYPE);
            *//*?} else {*/
            SlabType type = desired.get(Properties.SLAB_TYPE);
            /*?}*/
            if (type == SlabType.DOUBLE) return hitPos; // double slab — no half

            boolean wantTop = (type == SlabType.TOP);
            if (clickSide == Direction.UP) {
                if (wantTop) return forceHitY(hitPos, neighbor, true);
            } else if (clickSide == Direction.DOWN) {
                if (!wantTop) return forceHitY(hitPos, neighbor, false);
            } else {
                return forceHitY(hitPos, neighbor, wantTop);
            }
        }

        // Trapdoors
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            if (clickSide == Direction.UP) {
                // TOP trapdoor on top face → force upper hit
                /*? if >=26.1 {*//*
                if (half == Half.TOP) {
                *//*?} else {*/
                if (half == BlockHalf.TOP) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, true);
                }
            } else if (clickSide == Direction.DOWN) {
                // BOTTOM trapdoor on bottom face → force lower hit
                /*? if >=26.1 {*//*
                if (half == Half.BOTTOM) {
                *//*?} else {*/
                if (half == BlockHalf.BOTTOM) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, false);
                }
            } else {
                /*? if >=26.1 {*//*
                return forceHitY(hitPos, neighbor, half == Half.TOP);
                *//*?} else {*/
                return forceHitY(hitPos, neighbor, half == BlockHalf.TOP);
                /*?}*/
            }
        }

        return hitPos;
    }

    /** Forces hit Y to upper or lower quarter of the block face. */
    /*? if >=26.1 {*//*
    private static Vec3 forceHitY(Vec3 hitPos, BlockPos neighbor, boolean upper) {
    *//*?} else {*/
    private static Vec3d forceHitY(Vec3d hitPos, BlockPos neighbor, boolean upper) {
    /*?}*/
        double y = upper
                ? neighbor.getY() + 0.75   // upper quarter of the block
                : neighbor.getY() + 0.25;  // lower quarter of the block
        /*? if >=26.1 {*//*
        return new Vec3(hitPos.x, y, hitPos.z);
        *//*?} else {*/
        return new Vec3d(hitPos.x, y, hitPos.z);
        /*?}*/
    }

    /** Adjusts hit X/Z to influence door hinge side. */
    /*? if >=26.1 {*//*
    private static Vec3 adjustHitForDoorHinge(Vec3 hitPos, BlockPos neighbor,
    *//*?} else {*/
    private static Vec3d adjustHitForDoorHinge(Vec3d hitPos, BlockPos neighbor,
    /*?}*/
                                                BlockState desired) {
        if (!(desired.getBlock() instanceof DoorBlock)) return hitPos;
        /*? if >=26.1 {*//*
        if (!desired.hasProperty(BlockStateProperties.DOOR_HINGE)
        *//*?} else {*/
        if (!desired.contains(Properties.DOOR_HINGE)
        /*?}*/
                /*? if >=26.1 {*//*
                || !desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return hitPos;
                *//*?} else {*/
                || !desired.contains(Properties.HORIZONTAL_FACING)) return hitPos;
                /*?}*/

        /*? if >=26.1 {*//*
        DoorHingeSide hinge = desired.getValue(BlockStateProperties.DOOR_HINGE);
        *//*?} else {*/
        DoorHinge hinge = desired.get(Properties.DOOR_HINGE);
        /*?}*/
        /*? if >=26.1 {*//*
        Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
        *//*?} else {*/
        Direction facing = desired.get(Properties.HORIZONTAL_FACING);
        /*?}*/
        /*? if >=26.1 {*//*
        boolean wantLeft = (hinge == DoorHingeSide.LEFT);
        *//*?} else {*/
        boolean wantLeft = (hinge == DoorHinge.LEFT);
        /*?}*/

        double bx = neighbor.getX();
        double bz = neighbor.getZ();

        switch (facing) {
            case NORTH -> {
                // LEFT → hitX ≤ 0.5 → use 0.25, RIGHT → hitX > 0.5 → use 0.75
                double x = wantLeft ? bx + 0.25 : bx + 0.75;
                /*? if >=26.1 {*//*
                return new Vec3(x, hitPos.y, hitPos.z);
                *//*?} else {*/
                return new Vec3d(x, hitPos.y, hitPos.z);
                /*?}*/
            }
            case SOUTH -> {
                // LEFT → hitX ≥ 0.5 → use 0.75, RIGHT → hitX < 0.5 → use 0.25
                double x = wantLeft ? bx + 0.75 : bx + 0.25;
                /*? if >=26.1 {*//*
                return new Vec3(x, hitPos.y, hitPos.z);
                *//*?} else {*/
                return new Vec3d(x, hitPos.y, hitPos.z);
                /*?}*/
            }
            case EAST -> {
                // LEFT → hitZ ≤ 0.5 → use 0.25, RIGHT → hitZ > 0.5 → use 0.75
                double z = wantLeft ? bz + 0.25 : bz + 0.75;
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, hitPos.y, z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, hitPos.y, z);
                /*?}*/
            }
            case WEST -> {
                // LEFT → hitZ ≥ 0.5 → use 0.75, RIGHT → hitZ < 0.5 → use 0.25
                double z = wantLeft ? bz + 0.75 : bz + 0.25;
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, hitPos.y, z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, hitPos.y, z);
                /*?}*/
            }
            default -> { return hitPos; }
        }
    }

    /** Adjusts hit Y for air-placed half-blocks (TOP → Y+0.75). */
    /*? if >=26.1 {*//*
    private static Vec3 adjustHitForAirPlace(Vec3 hitPos, BlockPos target,
    *//*?} else {*/
    private static Vec3d adjustHitForAirPlace(Vec3d hitPos, BlockPos target,
    /*?}*/
                                              BlockState desired) {
        Block block = desired.getBlock();

        /*? if >=26.1 {*//*
        if (block instanceof StairBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof StairsBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            /*? if >=26.1 {*//*
            if (half == Half.TOP) {
            *//*?} else {*/
            if (half == BlockHalf.TOP) {
            /*?}*/
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, target.getY() + 0.75, hitPos.z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
                /*?}*/
            }
        }

        /*? if >=26.1 {*//*
        if (block instanceof SlabBlock && desired.hasProperty(BlockStateProperties.SLAB_TYPE)) {
        *//*?} else {*/
        if (block instanceof SlabBlock && desired.contains(Properties.SLAB_TYPE)) {
        /*?}*/
            /*? if >=26.1 {*//*
            SlabType type = desired.getValue(BlockStateProperties.SLAB_TYPE);
            *//*?} else {*/
            SlabType type = desired.get(Properties.SLAB_TYPE);
            /*?}*/
            if (type == SlabType.TOP) {
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, target.getY() + 0.75, hitPos.z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
                /*?}*/
            }
        }

        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            /*? if >=26.1 {*//*
            if (half == Half.TOP) {
            *//*?} else {*/
            if (half == BlockHalf.TOP) {
            /*?}*/
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, target.getY() + 0.75, hitPos.z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
                /*?}*/
            }
        }

        return hitPos;
    }

    /** Finds an adjacent face, with orientation awareness for wall/floor/ceiling blocks. */
    /*? if >=26.1 {*//*
    private static Direction findOrientedPlacementFace(Level world, BlockPos target,
    *//*?} else {*/
    private static Direction findOrientedPlacementFace(World world, BlockPos target,
    /*?}*/
                                                       BlockState desired) {
        Block block = desired.getBlock();

        // Wall-mounted torches
        if (block instanceof WallTorchBlock
                /*? if >=26.1 {*//*
                || block instanceof RedstoneWallTorchBlock) {
                *//*?} else {*/
                || block instanceof WallRedstoneTorchBlock) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                Direction supportDir = facing.getOpposite();
                return requireSolidFace(world, target, supportDir);
            }
        }

        // Standing torches
        if (block instanceof TorchBlock && !(block instanceof WallTorchBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }
        /*? if >=26.1 {*//*
        if (block instanceof RedstoneTorchBlock && !(block instanceof RedstoneWallTorchBlock)) {
        *//*?} else {*/
        if (block instanceof RedstoneTorchBlock && !(block instanceof WallRedstoneTorchBlock)) {
        /*?}*/
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Wall signs
        if (block instanceof WallSignBlock || block instanceof WallHangingSignBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Standing signs
        if (block instanceof SignBlock && !(block instanceof WallSignBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Hanging signs
        if (block instanceof HangingSignBlock && !(block instanceof WallHangingSignBlock)) {
            return requireSolidFace(world, target, Direction.UP);
        }

        // Standing banners
        if (block instanceof AbstractBannerBlock && !(block instanceof WallBannerBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Wall banners
        if (block instanceof WallBannerBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Ladders
        if (block instanceof LadderBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Lanterns
        if (block instanceof LanternBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HANGING) && desired.getValue(BlockStateProperties.HANGING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HANGING) && desired.get(Properties.HANGING)) {
            /*?}*/
                return requireSolidFace(world, target, Direction.UP);
            } else {
                return requireSolidFace(world, target, Direction.DOWN);
            }
        }

        // Buttons (wall / floor / ceiling)
        if (block instanceof ButtonBlock) {
            return resolveWallMountedFace(world, target, desired);
        }

        // Levers (wall / floor / ceiling)
        if (block instanceof LeverBlock) {
            return resolveWallMountedFace(world, target, desired);
        }

        // Skulls / heads on walls
        if (block instanceof WallSkullBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }
        if (block instanceof SkullBlock && !(block instanceof WallSkullBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Trapdoors — BLOCK_HALF determines attachment direction
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HALF)) {
            *//*?} else {*/
            if (desired.contains(Properties.BLOCK_HALF)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Half half = desired.getValue(BlockStateProperties.HALF);
                *//*?} else {*/
                BlockHalf half = desired.get(Properties.BLOCK_HALF);
                /*?}*/
                /*? if >=26.1 {*//*
                if (half == Half.TOP) {
                *//*?} else {*/
                if (half == BlockHalf.TOP) {
                /*?}*/
                    // Top trapdoor — click bottom face of block above
                    return requireSolidFace(world, target, Direction.UP);
                } else {
                    // Bottom trapdoor — click top face of block below
                    return requireSolidFace(world, target, Direction.DOWN);
                }
            }
            return findPlacementFace(world, target);
        }

        // Doors — click floor (lower half only, upper auto-created)
        if (block instanceof DoorBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Beds — click floor (foot part only, head auto-created)
        if (block instanceof BedBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Tall plants — place lower half on floor
        /*? if >=26.1 {*//*
        if (block instanceof DoublePlantBlock) {
        *//*?} else {*/
        if (block instanceof TallPlantBlock) {
        /*?}*/
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Hoppers — facing from clicked face
        if (block instanceof HopperBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            *//*?} else {*/
            if (desired.contains(Properties.HOPPER_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING_HOPPER);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HOPPER_FACING);
                /*?}*/
                if (facing == Direction.DOWN) {
                    return findPlacementFace(world, target);
                } else {
                    // Click the face in the output direction
                    return requireSolidFace(world, target, facing);
                }
            }
        }

        // Stairs/Slabs/Trapdoors — prefer side faces for half control
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock
        *//*?} else {*/
        if (block instanceof StairsBlock
        /*?}*/
                || block instanceof SlabBlock
                /*? if >=26.1 {*//*
                || block instanceof TrapDoorBlock) {
                *//*?} else {*/
                || block instanceof TrapdoorBlock) {
                /*?}*/
            Direction sideFace = findSidePlacementFace(world, target);
            if (sideFace != null) return sideFace;
            // Fall through to generic if no side face available
            return findPlacementFace(world, target);
        }

        // Pillar blocks — click face along desired axis
        /*? if >=26.1 {*//*
        if (block instanceof RotatedPillarBlock && desired.hasProperty(BlockStateProperties.AXIS)) {
        *//*?} else {*/
        if (block instanceof PillarBlock && desired.contains(Properties.AXIS)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Direction.Axis desiredAxis = desired.getValue(BlockStateProperties.AXIS);
            *//*?} else {*/
            Direction.Axis desiredAxis = desired.get(Properties.AXIS);
            /*?}*/
            Direction preferred = preferFaceForAxis(world, target, desiredAxis);
            if (preferred != null) return preferred;
            // No matching face — air placement fallback
            return null;
        }

        // Shulker boxes — click opposite face for correct FACING.
        if (block instanceof ShulkerBoxBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                Direction supportDir = facing.getOpposite();
                Direction result = requireSolidFace(world, target, supportDir);
                if (result != null) return result;
            }
            return findPlacementFace(world, target);
        }

        return findPlacementFace(world, target);
    }

    /** Returns dir if there's a solid support block in that direction, else null. */
    /*? if >=26.1 {*//*
    private static Direction requireSolidFace(Level world, BlockPos target, Direction dir) {
    *//*?} else {*/
    private static Direction requireSolidFace(World world, BlockPos target, Direction dir) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos neighbor = target.relative(dir);
        *//*?} else {*/
        BlockPos neighbor = target.offset(dir);
        /*?}*/
        BlockState state = world.getBlockState(neighbor);
        /*? if >=26.1 {*//*
        if (!state.canBeReplaced()
        *//*?} else {*/
        if (!state.isReplaceable()
        /*?}*/
                /*? if >=26.1 {*//*
                && state.getShape(world, neighbor) != Shapes.empty()) {
                *//*?} else {*/
                && state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                /*?}*/
            return dir;
        }
        return null; // required support block not present
    }

    /** Resolves placement face for FLOOR/WALL/CEILING blocks (buttons, levers). */
    /*? if >=26.1 {*//*
    private static Direction resolveWallMountedFace(Level world, BlockPos target,
    *//*?} else {*/
    private static Direction resolveWallMountedFace(World world, BlockPos target,
    /*?}*/
                                                     BlockState desired) {
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.ATTACH_FACE)) {
        *//*?} else {*/
        if (desired.contains(Properties.BLOCK_FACE)) {
        /*?}*/
            /*? if >=26.1 {*//*
            AttachFace face = desired.getValue(BlockStateProperties.ATTACH_FACE);
            *//*?} else {*/
            BlockFace face = desired.get(Properties.BLOCK_FACE);
            /*?}*/
            return switch (face) {
                case FLOOR   -> requireSolidFace(world, target, Direction.DOWN);
                case CEILING -> requireSolidFace(world, target, Direction.UP);
                case WALL    -> {
                    /*? if >=26.1 {*//*
                    if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    *//*?} else {*/
                    if (desired.contains(Properties.HORIZONTAL_FACING)) {
                    /*?}*/
                        /*? if >=26.1 {*//*
                        Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                        *//*?} else {*/
                        Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                        /*?}*/
                        yield requireSolidFace(world, target, facing.getOpposite());
                    }
                    yield findPlacementFace(world, target);
                }
            };
        }
        return findPlacementFace(world, target);
    }

    /** Finds a face along the desired axis for pillar block placement. */
    /*? if >=26.1 {*//*
    private static Direction preferFaceForAxis(Level world, BlockPos target,
    *//*?} else {*/
    private static Direction preferFaceForAxis(World world, BlockPos target,
    /*?}*/
                                               Direction.Axis desiredAxis) {
        // Faces whose normal matches the axis
        Direction[] axisDirections = switch (desiredAxis) {
            case X -> new Direction[]{ Direction.EAST, Direction.WEST };
            case Y -> new Direction[]{ Direction.UP, Direction.DOWN };
            case Z -> new Direction[]{ Direction.NORTH, Direction.SOUTH };
        };

        for (Direction dir : axisDirections) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState state = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (!state.canBeReplaced()
            *//*?} else {*/
            if (!state.isReplaceable()
            /*?}*/
                    /*? if >=26.1 {*//*
                    && state.getShape(world, neighbor) != Shapes.empty()) {
                    *//*?} else {*/
                    && state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                    /*?}*/
                return dir;
            }
        }
        return null; // no preferred face available — caller falls back to default
    }

    // Detects whether two states of the same block differ in a property the
    // printer can correct by re-placing.
    // Covered (placement-controlled): HORIZONTAL_FACING, FACING, AXIS, HALF,
    // SLAB_TYPE, ROTATION_16, HOPPER_FACING, DOOR_HINGE — set by getStateForPlacement
    // from click context, so re-placing with corrected hit/yaw fixes them.
    // Not covered (neighbor-derived): STAIRS_SHAPE, wall/fence/pane connections,
    // redstone wire sides, POWER, CHEST_TYPE — auto-update via updateShape, so
    // re-placing early just burns an attempt.
    // Not covered (post-placement use): OPEN, POWERED, LIT, OCCUPIED, repeater
    // DELAY/LOCKED, comparator MODE — re-placing yields the default and loops.
    // WATERLOGGED is a known gap (no water-bucket integration), tracked separately.
    public static boolean isOrientationMismatch(BlockState existing, BlockState desired) {
        if (existing.getBlock() != desired.getBlock()) return false;

        // Check all common orientation properties
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
        *//*?} else {*/
        if (desired.contains(Properties.HORIZONTAL_FACING)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                *//*?} else {*/
                && existing.contains(Properties.HORIZONTAL_FACING)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.HORIZONTAL_FACING) != desired.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (existing.get(Properties.HORIZONTAL_FACING) != desired.get(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.FACING)
        *//*?} else {*/
        if (desired.contains(Properties.FACING)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.FACING)) {
                *//*?} else {*/
                && existing.contains(Properties.FACING)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.FACING) != desired.getValue(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (existing.get(Properties.FACING) != desired.get(Properties.FACING)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.AXIS)
        *//*?} else {*/
        if (desired.contains(Properties.AXIS)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.AXIS)) {
                *//*?} else {*/
                && existing.contains(Properties.AXIS)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.AXIS) != desired.getValue(BlockStateProperties.AXIS)) {
            *//*?} else {*/
            if (existing.get(Properties.AXIS) != desired.get(Properties.AXIS)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.HALF)
        *//*?} else {*/
        if (desired.contains(Properties.BLOCK_HALF)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.HALF)) {
                *//*?} else {*/
                && existing.contains(Properties.BLOCK_HALF)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.HALF) != desired.getValue(BlockStateProperties.HALF)) {
            *//*?} else {*/
            if (existing.get(Properties.BLOCK_HALF) != desired.get(Properties.BLOCK_HALF)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.SLAB_TYPE)
        *//*?} else {*/
        if (desired.contains(Properties.SLAB_TYPE)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                *//*?} else {*/
                && existing.contains(Properties.SLAB_TYPE)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.SLAB_TYPE) != desired.getValue(BlockStateProperties.SLAB_TYPE)) {
            *//*?} else {*/
            if (existing.get(Properties.SLAB_TYPE) != desired.get(Properties.SLAB_TYPE)) {
            /*?}*/
                return true;
            }
        }
        // Standing banners, standing signs, hanging signs — ROTATION (0-15)
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.ROTATION_16)
        *//*?} else {*/
        if (desired.contains(Properties.ROTATION)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.ROTATION_16)) {
                *//*?} else {*/
                && existing.contains(Properties.ROTATION)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (!existing.getValue(BlockStateProperties.ROTATION_16).equals(desired.getValue(BlockStateProperties.ROTATION_16))) {
            *//*?} else {*/
            if (!existing.get(Properties.ROTATION).equals(desired.get(Properties.ROTATION))) {
            /*?}*/
                return true;
            }
        }
        // Hoppers — HOPPER_FACING (DOWN + 4 horizontal)
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.FACING_HOPPER)
        *//*?} else {*/
        if (desired.contains(Properties.HOPPER_FACING)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.FACING_HOPPER)) {
                *//*?} else {*/
                && existing.contains(Properties.HOPPER_FACING)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.FACING_HOPPER) != desired.getValue(BlockStateProperties.FACING_HOPPER)) {
            *//*?} else {*/
            if (existing.get(Properties.HOPPER_FACING) != desired.get(Properties.HOPPER_FACING)) {
            /*?}*/
                return true;
            }
        }
        // Doors — DOOR_HINGE (LEFT/RIGHT). Set by server from click hit X/Z
        // relative to door cell center; adjustHitForDoorHinge handles this on
        // re-placement. Without this check, doors silently mismatched would
        // never be corrected.
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.DOOR_HINGE)
        *//*?} else {*/
        if (desired.contains(Properties.DOOR_HINGE)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.DOOR_HINGE)) {
                *//*?} else {*/
                && existing.contains(Properties.DOOR_HINGE)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.DOOR_HINGE) != desired.getValue(BlockStateProperties.DOOR_HINGE)) {
            *//*?} else {*/
            if (existing.get(Properties.DOOR_HINGE) != desired.get(Properties.DOOR_HINGE)) {
            /*?}*/
                return true;
            }
        }
        return false;
    }

    /** Converts a cardinal Direction to player yaw in degrees. */
    private static float directionToYaw(Direction dir) {
        return switch (dir) {
            case SOUTH -> 0.0f;
            case WEST  -> 90.0f;
            case NORTH -> 180.0f;
            case EAST  -> -90.0f;
            default    -> 0.0f;  // UP/DOWN — irrelevant
        };
    }

    /** Computes pitch angle from eye to target. */
    /*? if >=26.1 {*//*
    private static float computePitchToward(Vec3 eye, Vec3 target) {
    *//*?} else {*/
    private static float computePitchToward(Vec3d eye, Vec3d target) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 diff = target.subtract(eye);
        *//*?} else {*/
        Vec3d diff = target.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        /*? if >=26.1 {*//*
        return (float) -(Mth.atan2(diff.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        return (float) -(MathHelper.atan2(diff.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
    }

    private static boolean isOrientationLookCloseToHit(float yaw, float pitch,
                                                       float hitYaw, float hitPitch) {
        return Math.abs(wrapDegreesLocal(yaw - hitYaw)) <= ORIENTATION_LOOK_MAX_YAW_DIFF
                && Math.abs(pitch - hitPitch) <= ORIENTATION_LOOK_MAX_PITCH_DIFF;
    }

    private static float wrapDegreesLocal(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    /** Wraps a yaw to [-180, 180) so unbounded accumulation never reaches the server. */
    private static float wrapDegrees180(float deg) {
        return ((deg % 360f) + 540f) % 360f - 180f;
    }

    private static float snapToMouseGCD(float desired, float serverCurrent) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        double sens = mc.options.sensitivity().get();
        *//*?} else {*/
        double sens = mc.options.getMouseSensitivity().getValue();
        /*?}*/
        double gcd = Math.pow(sens * 0.6 + 0.2, 3.0) * 1.2;
        if (gcd < 0.001) return desired;            // zero-sensitivity guard
        return (float) (desired - (desired - serverCurrent) % gcd);
    }

    // placement face finding

    /** Finds the best adjacent solid face, preferring non-interactive neighbors. */
    /*? if >=26.1 {*//*
    public static Direction findPlacementFace(Level world, BlockPos target) {
    *//*?} else {*/
    public static Direction findPlacementFace(World world, BlockPos target) {
    /*?}*/
        Direction fallback = null;
        for (Direction dir : NORMAL_PLACE_FACE_ORDER) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState neighborState = world.getBlockState(neighbor);

            /*? if >=26.1 {*//*
            if (neighborState.canBeReplaced()) continue;
            *//*?} else {*/
            if (neighborState.isReplaceable()) continue;
            /*?}*/
            /*? if >=26.1 {*//*
            if (neighborState.getShape(world, neighbor) == Shapes.empty()) continue;
            *//*?} else {*/
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            /*?}*/

            if (!isInteractive(neighborState.getBlock())) {
                return dir;
            }
            if (fallback == null) fallback = dir;
        }
        return fallback;
    }

    /** Finds a horizontal side face (no UP/DOWN) for half-block placement. */
    /*? if >=26.1 {*//*
    private static Direction findSidePlacementFace(Level world, BlockPos target) {
    *//*?} else {*/
    private static Direction findSidePlacementFace(World world, BlockPos target) {
    /*?}*/
        Direction fallback = null;
        for (Direction dir : HORIZONTAL_PLACE_FACE_ORDER) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState neighborState = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (neighborState.canBeReplaced()) continue;
            *//*?} else {*/
            if (neighborState.isReplaceable()) continue;
            /*?}*/
            /*? if >=26.1 {*//*
            if (neighborState.getShape(world, neighbor) == Shapes.empty()) continue;
            *//*?} else {*/
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            /*?}*/
            if (!isInteractive(neighborState.getBlock())) {
                return dir;
            }
            if (fallback == null) fallback = dir;
        }
        return fallback;
    }

    /** Finds a non-interactive adjacent face (for chests, etc). */
    /*? if >=26.1 {*//*
    private static Direction findNonInteractiveFace(Level world, BlockPos target) {
    *//*?} else {*/
    private static Direction findNonInteractiveFace(World world, BlockPos target) {
    /*?}*/
        Direction interactive = null;
        for (Direction dir : NORMAL_PLACE_FACE_ORDER) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState neighborState = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (neighborState.canBeReplaced()) continue;
            *//*?} else {*/
            if (neighborState.isReplaceable()) continue;
            /*?}*/
            /*? if >=26.1 {*//*
            if (neighborState.getShape(world, neighbor) == Shapes.empty()) continue;
            *//*?} else {*/
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            /*?}*/
            if (!isInteractive(neighborState.getBlock())) {
                return dir; // non-interactive — ideal
            }
            if (interactive == null) interactive = dir;
        }
        return interactive; // null if nothing solid adjacent
    }

    /** Whether any adjacent block is solid (supports placement). */
    /*? if >=26.1 {*//*
    public static boolean hasAdjacentSolid(Level world, BlockPos pos) {
    *//*?} else {*/
    public static boolean hasAdjacentSolid(World world, BlockPos pos) {
    /*?}*/
        for (Direction dir : Direction.values()) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = pos.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = pos.offset(dir);
            /*?}*/
            BlockState state = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (!state.canBeReplaced() &&
            *//*?} else {*/
            if (!state.isReplaceable() &&
            /*?}*/
                    /*? if >=26.1 {*//*
                    state.getShape(world, neighbor) != Shapes.empty()) {
                    *//*?} else {*/
                    state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                    /*?}*/
                return true;
            }
        }
        return false;
    }

    /*? if >=26.1 {*//*
    public static boolean canPlaceFromCurrentPosition(BlockPos target, BlockState desired,
                                                      LocalPlayer player, Level world) {
    *//*?} else {*/
    public static boolean canPlaceFromCurrentPosition(BlockPos target, BlockState desired,
                                                      ClientPlayerEntity player, World world) {
    /*?}*/
        return canPlaceFromCurrentPosition(target, desired, player, world, null);
    }

    /*? if >=26.1 {*//*
    public static boolean canPlaceFromCurrentPosition(BlockPos target, BlockState desired,
                                                      LocalPlayer player, Level world,
                                                      Predicate<BlockPos> supportFilter) {
    *//*?} else {*/
    public static boolean canPlaceFromCurrentPosition(BlockPos target, BlockState desired,
                                                      ClientPlayerEntity player, World world,
                                                      Predicate<BlockPos> supportFilter) {
    /*?}*/
        if (target == null || desired == null || player == null || world == null) {
            return false;
        }
        return findBestVisiblePlacementHit(player, world, target, desired, supportFilter) != null;
    }

    /*? if >=26.1 {*//*
    public static boolean canPlaceFromStandingPosition(BlockPos target, BlockState desired,
                                                       BlockPos standPos, LocalPlayer player,
                                                       Level world) {
    *//*?} else {*/
    public static boolean canPlaceFromStandingPosition(BlockPos target, BlockState desired,
                                                       BlockPos standPos, ClientPlayerEntity player,
                                                       World world) {
    /*?}*/
        return canPlaceFromStandingPosition(target, desired, standPos, player, world, null);
    }

    /*? if >=26.1 {*//*
    public static boolean canPlaceFromStandingPosition(BlockPos target, BlockState desired,
                                                       BlockPos standPos, LocalPlayer player,
                                                       Level world,
                                                       Predicate<BlockPos> supportFilter) {
    *//*?} else {*/
    public static boolean canPlaceFromStandingPosition(BlockPos target, BlockState desired,
                                                       BlockPos standPos, ClientPlayerEntity player,
                                                       World world,
                                                       Predicate<BlockPos> supportFilter) {
    /*?}*/
        if (target == null || desired == null || standPos == null || player == null || world == null) {
            return false;
        }
        /*? if >=26.1 {*//*
        Vec3 eyePos = Vec3.atCenterOf(standPos).add(0.0, 1.12, 0.0);
        *//*?} else {*/
        Vec3d eyePos = Vec3d.ofCenter(standPos).add(0.0, 1.12, 0.0);
        /*?}*/
        return findBestVisiblePlacementHit(player, world, target, desired, eyePos, supportFilter) != null;
    }

    /*? if >=26.1 {*//*
    private static VisiblePlacementHit findBestVisiblePlacementHit(LocalPlayer player, Level world,
                                                                   BlockPos target, BlockState desired) {
    *//*?} else {*/
    private static VisiblePlacementHit findBestVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                                   BlockPos target, BlockState desired) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eyePos = player != null ? player.getEyePosition() : null;
        *//*?} else {*/
        Vec3d eyePos = player != null ? player.getEyePos() : null;
        /*?}*/
        return findBestVisiblePlacementHit(player, world, target, desired, eyePos, null);
    }

    /*? if >=26.1 {*//*
    private static VisiblePlacementHit findBestVisiblePlacementHit(LocalPlayer player, Level world,
                                                                   BlockPos target, BlockState desired,
                                                                   Predicate<BlockPos> supportFilter) {
    *//*?} else {*/
    private static VisiblePlacementHit findBestVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                                   BlockPos target, BlockState desired,
                                                                   Predicate<BlockPos> supportFilter) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eyePos = player != null ? player.getEyePosition() : null;
        *//*?} else {*/
        Vec3d eyePos = player != null ? player.getEyePos() : null;
        /*?}*/
        return findBestVisiblePlacementHit(player, world, target, desired, eyePos, supportFilter);
    }

    /*? if >=26.1 {*//*
    private static VisiblePlacementHit findBestVisiblePlacementHit(LocalPlayer player, Level world,
                                                                   BlockPos target, BlockState desired,
                                                                   Predicate<BlockPos> supportFilter,
                                                                   boolean requireExactFace) {
    *//*?} else {*/
    private static VisiblePlacementHit findBestVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                                   BlockPos target, BlockState desired,
                                                                   Predicate<BlockPos> supportFilter,
                                                                   boolean requireExactFace) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eyePos = player != null ? player.getEyePosition() : null;
        *//*?} else {*/
        Vec3d eyePos = player != null ? player.getEyePos() : null;
        /*?}*/
        return findBestVisiblePlacementHit(player, world, target, desired, eyePos, supportFilter,
                requireExactFace);
    }

    /*? if >=26.1 {*//*
    private static VisiblePlacementHit findBestVisiblePlacementHit(LocalPlayer player, Level world,
                                                                   BlockPos target, BlockState desired,
                                                                   Vec3 eyePos) {
    *//*?} else {*/
    private static VisiblePlacementHit findBestVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                                   BlockPos target, BlockState desired,
                                                                   Vec3d eyePos) {
    /*?}*/
        return findBestVisiblePlacementHit(player, world, target, desired, eyePos, null);
    }

    /*? if >=26.1 {*//*
    private static VisiblePlacementHit findBestVisiblePlacementHit(LocalPlayer player, Level world,
                                                                   BlockPos target, BlockState desired,
                                                                   Vec3 eyePos,
                                                                   Predicate<BlockPos> supportFilter) {
    *//*?} else {*/
    private static VisiblePlacementHit findBestVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                                   BlockPos target, BlockState desired,
                                                                   Vec3d eyePos,
                                                                   Predicate<BlockPos> supportFilter) {
    /*?}*/
        return findBestVisiblePlacementHit(player, world, target, desired, eyePos, supportFilter,
                true);
    }

    /*? if >=26.1 {*//*
    private static VisiblePlacementHit findBestVisiblePlacementHit(LocalPlayer player, Level world,
                                                                   BlockPos target, BlockState desired,
                                                                   Vec3 eyePos,
                                                                   Predicate<BlockPos> supportFilter,
                                                                   boolean requireExactFace) {
    *//*?} else {*/
    private static VisiblePlacementHit findBestVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                                   BlockPos target, BlockState desired,
                                                                   Vec3d eyePos,
                                                                   Predicate<BlockPos> supportFilter,
                                                                   boolean requireExactFace) {
    /*?}*/
        if (player == null || world == null || target == null || desired == null || eyePos == null) {
            return null;
        }
        // Fix #14a: reset the per-attempt probe failure ranking so each
        // call to the outermost entry point starts fresh. Without this, a
        // low-rank failure from a previous target can suppress higher-rank
        // failures from a new target if no consume happened in between.
        resetProbeFailure();
        Direction primaryFace = findOrientedPlacementFace(world, target, desired);
        if (primaryFace == null) {
            return null;
        }
        BlockHitResult primaryHit = findVisiblePlacementHit(
                player, world, target, desired, primaryFace, eyePos, supportFilter, requireExactFace);
        if (primaryHit != null) {
            return new VisiblePlacementHit(primaryFace, primaryHit);
        }
        if (!canProbeAlternateSupportFaces(desired)) {
            return null;
        }
        VisiblePlacementHit interactiveFallback = null;
        for (Direction face : NORMAL_PLACE_FACE_ORDER) {
            if (face == primaryFace || requireSolidFace(world, target, face) == null) {
                continue;
            }
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(face);
            *//*?} else {*/
            BlockPos neighbor = target.offset(face);
            /*?}*/
            if (!isSupportAllowed(supportFilter, neighbor)) {
                continue;
            }
            BlockHitResult hit = findVisiblePlacementHit(
                    player, world, target, desired, face, eyePos, supportFilter, requireExactFace);
            if (hit == null) {
                continue;
            }
            if (!isInteractive(world.getBlockState(neighbor).getBlock())) {
                return new VisiblePlacementHit(face, hit);
            }
            if (interactiveFallback == null) {
                interactiveFallback = new VisiblePlacementHit(face, hit);
            }
        }
        if (interactiveFallback != null) {
            return interactiveFallback;
        }
        // Fix #14b: relaxed second pass. The strict pass requires the
        // raycast to hit the requested clickSide of the neighbor block.
        // Small eye-position drift between the planning stance and the
        // runtime player position can cause the actual ray to land on a
        // different side of the same neighbor block (e.g. north face
        // instead of east face). Vanilla server validates only reach +
        // hitPos-on-named-face, so the synthetic packet built by the
        // relaxed path (hitPos at face center + clickSide as requested)
        // still passes server checks. Only attempt this when the desired
        // block accepts any support face (canProbeAlternateSupportFaces)
        // and only when strict failed first (so telemetry surfaces the
        // closest-to-success failure correctly).
        if (requireExactFace) {
            BlockHitResult relaxedPrimaryHit = findVisiblePlacementHit(
                    player, world, target, desired, primaryFace, eyePos, supportFilter, false);
            if (relaxedPrimaryHit != null) {
                return new VisiblePlacementHit(primaryFace, relaxedPrimaryHit);
            }
            VisiblePlacementHit relaxedInteractiveFallback = null;
            for (Direction face : NORMAL_PLACE_FACE_ORDER) {
                if (face == primaryFace || requireSolidFace(world, target, face) == null) {
                    continue;
                }
                /*? if >=26.1 {*//*
                BlockPos neighbor = target.relative(face);
                *//*?} else {*/
                BlockPos neighbor = target.offset(face);
                /*?}*/
                if (!isSupportAllowed(supportFilter, neighbor)) {
                    continue;
                }
                BlockHitResult hit = findVisiblePlacementHit(
                        player, world, target, desired, face, eyePos, supportFilter, false);
                if (hit == null) {
                    continue;
                }
                if (!isInteractive(world.getBlockState(neighbor).getBlock())) {
                    return new VisiblePlacementHit(face, hit);
                }
                if (relaxedInteractiveFallback == null) {
                    relaxedInteractiveFallback = new VisiblePlacementHit(face, hit);
                }
            }
            if (relaxedInteractiveFallback != null) {
                return relaxedInteractiveFallback;
            }
        }
        return null;
    }

    private static boolean canProbeAlternateSupportFaces(BlockState desired) {
        return desired != null && desired.getProperties().isEmpty();
    }

    /*? if >=26.1 {*//*
    private static BlockHitResult findVisiblePlacementHit(LocalPlayer player, Level world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face) {
    *//*?} else {*/
    private static BlockHitResult findVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eyePos = player != null ? player.getEyePosition() : null;
        *//*?} else {*/
        Vec3d eyePos = player != null ? player.getEyePos() : null;
        /*?}*/
        return findVisiblePlacementHit(player, world, target, desired, face, eyePos, null);
    }

    /*? if >=26.1 {*//*
    private static BlockHitResult findVisiblePlacementHit(LocalPlayer player, Level world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face, Vec3 eyePos) {
    *//*?} else {*/
    private static BlockHitResult findVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face, Vec3d eyePos) {
    /*?}*/
        return findVisiblePlacementHit(player, world, target, desired, face, eyePos, null);
    }

    /*? if >=26.1 {*//*
    private static BlockHitResult findVisiblePlacementHit(LocalPlayer player, Level world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face, Vec3 eyePos,
                                                          Predicate<BlockPos> supportFilter) {
    *//*?} else {*/
    private static BlockHitResult findVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face, Vec3d eyePos,
                                                          Predicate<BlockPos> supportFilter) {
    /*?}*/
        return findVisiblePlacementHit(player, world, target, desired, face, eyePos, supportFilter,
                true);
    }

    /*? if >=26.1 {*//*
    private static BlockHitResult findVisiblePlacementHit(LocalPlayer player, Level world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face, Vec3 eyePos,
                                                          Predicate<BlockPos> supportFilter,
                                                          boolean requireExactFace) {
    *//*?} else {*/
    private static BlockHitResult findVisiblePlacementHit(ClientPlayerEntity player, World world,
                                                          BlockPos target, BlockState desired,
                                                          Direction face, Vec3d eyePos,
                                                          Predicate<BlockPos> supportFilter,
                                                          boolean requireExactFace) {
    /*?}*/
        if (player == null || world == null || target == null || desired == null || face == null) {
            return null;
        }
        if (eyePos == null) {
            return null;
        }
        /*? if >=26.1 {*//*
        BlockPos neighbor = target.relative(face);
        *//*?} else {*/
        BlockPos neighbor = target.offset(face);
        /*?}*/
        if (!isSupportAllowed(supportFilter, neighbor)) {
            recordProbeFailure("support-filter-rejected face=" + face
                    + " neighbor=" + neighbor.getX() + "," + neighbor.getY() + "," + neighbor.getZ(), 0);
            return null;
        }
        Direction clickSide = face.getOpposite();
        // Fix #12 diagnostic: track the closest-to-success failure mode
        // across all probes so we can identify the actual occlusion cause.
        int probesTooFar = 0;
        int probesMissed = 0;
        int probesHitOther = 0;
        BlockPos firstHitPos = null;
        Direction firstHitSide = null;
        BlockState firstHitState = null;
        double[][] probes = {
                {0.0, 0.0},
                { FACE_PROBE_OFFSET, 0.0},
                {-FACE_PROBE_OFFSET, 0.0},
                {0.0,  FACE_PROBE_OFFSET},
                {0.0, -FACE_PROBE_OFFSET},
                { FACE_PROBE_OFFSET,  FACE_PROBE_OFFSET},
                { FACE_PROBE_OFFSET, -FACE_PROBE_OFFSET},
                {-FACE_PROBE_OFFSET,  FACE_PROBE_OFFSET},
                {-FACE_PROBE_OFFSET, -FACE_PROBE_OFFSET}
        };
        // Reach is computed once outside the probe loop \u2014 the attribute lookup
        // is non-trivial and the value is constant for the whole probe sweep.
        double probeReachSq = effectivePlaceReachSq(player);

        for (double[] probe : probes) {
            /*? if >=26.1 {*//*
            Vec3 hitPos = getFaceCenterHit(neighbor, clickSide);
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, desired);
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, desired);
            hitPos = offsetFaceHit(hitPos, neighbor, clickSide, probe[0], probe[1]);
            if (eyePos.distanceToSqr(hitPos) > probeReachSq) {
                probesTooFar++;
                continue;
            }
            HitResult sight = world.clip(new ClipContext(
                    eyePos,
                    hitPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player));
            if (sight.getType() != HitResult.Type.BLOCK) {
                probesMissed++;
                continue;
            }
            BlockHitResult blockSight = (BlockHitResult) sight;
            if (blockSight.getBlockPos().equals(neighbor)
                    && blockSight.getDirection() == clickSide) {
                return new BlockHitResult(hitPos, clickSide, neighbor, false);
            }
            if (!requireExactFace
                    && blockSight.getBlockPos().equals(neighbor)
                    && canProbeAlternateSupportFaces(desired)) {
                return new BlockHitResult(hitPos, clickSide, neighbor, false);
            }
            probesHitOther++;
            if (firstHitPos == null) {
                firstHitPos = blockSight.getBlockPos();
                firstHitSide = blockSight.getDirection();
                firstHitState = world.getBlockState(firstHitPos);
            }
            *//*?} else {*/
            Vec3d hitPos = getFaceCenterHit(neighbor, clickSide);
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, desired);
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, desired);
            hitPos = offsetFaceHit(hitPos, neighbor, clickSide, probe[0], probe[1]);
            if (eyePos.squaredDistanceTo(hitPos) > probeReachSq) {
                probesTooFar++;
                continue;
            }
            HitResult sight = world.raycast(new RaycastContext(
                    eyePos,
                    hitPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player));
            if (sight.getType() != HitResult.Type.BLOCK) {
                probesMissed++;
                continue;
            }
            BlockHitResult blockSight = (BlockHitResult) sight;
            if (blockSight.getBlockPos().equals(neighbor)
                    && blockSight.getSide() == clickSide) {
                return new BlockHitResult(hitPos, clickSide, neighbor, false);
            }
            if (!requireExactFace
                    && blockSight.getBlockPos().equals(neighbor)
                    && canProbeAlternateSupportFaces(desired)) {
                return new BlockHitResult(hitPos, clickSide, neighbor, false);
            }
            // Fix #12: capture WHAT got in the way so we can tell the
            // difference between (a) raycast hits a different block (wall
            // occluding line of sight) vs (b) raycast hits the right block
            // but on the wrong face (geometry / clickSide mismatch).
            probesHitOther++;
            if (firstHitPos == null) {
                firstHitPos = blockSight.getBlockPos();
                firstHitSide = blockSight.getSide();
                firstHitState = world.getBlockState(firstHitPos);
            }
            /*?}*/
        }

        StringBuilder reason = new StringBuilder();
        reason.append("face=").append(face)
                .append(" neighbor=").append(neighbor.getX()).append(',')
                .append(neighbor.getY()).append(',').append(neighbor.getZ())
                .append(" tooFar=").append(probesTooFar)
                .append(" miss=").append(probesMissed)
                .append(" hitOther=").append(probesHitOther);
        boolean sameNeighborWrongFace = false;
        if (firstHitPos != null) {
            sameNeighborWrongFace = firstHitPos.equals(neighbor);
            reason.append(" firstOther=")
                    .append(firstHitPos.getX()).append(',')
                    .append(firstHitPos.getY()).append(',')
                    .append(firstHitPos.getZ())
                    .append(':')
                    .append(firstHitState != null ? firstHitState.getBlock() : "?")
                    .append('@').append(firstHitSide);
            if (sameNeighborWrongFace) {
                reason.append(" SAME_NEIGHBOR");
            }
        }
        // Fix #14a: rank failures so the most-informative one survives
        // across multiple face attempts. Same-neighbor-wrong-face = closest
        // to success; wall hit on different block = next; mostly missed
        // probes = lower; all-too-far = lowest meaningful (eye too far).
        int rank;
        if (sameNeighborWrongFace) {
            rank = 5;
        } else if (probesHitOther > 0) {
            rank = 4;
        } else if (probesMissed > 0 && probesTooFar > 0) {
            rank = 3;
        } else if (probesMissed > 0) {
            rank = 2;
        } else {
            rank = 1;
        }
        recordProbeFailure(reason.toString(), rank);
        return null;
    }

    private static boolean isSupportAllowed(Predicate<BlockPos> supportFilter, BlockPos support) {
        return supportFilter == null || supportFilter.test(support);
    }

    // interactive block detection

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
            /*? if >=26.1 {*//*
            TrapDoorBlock.class
            *//*?} else {*/
            TrapdoorBlock.class
            /*?}*/
    );

    /** Returns true if right-clicking this block opens a GUI or toggles state. */
    public static boolean isInteractive(Block block) {
        for (Class<? extends Block> clazz : INTERACTIVE) {
            if (clazz.isInstance(block)) return true;
        }
        return false;
    }

    /**
     * Temporarily forces the player into a sneaking state for block placement.
     * Returns a Runnable that restores the original sneaking state.
     */
    /*? if >=26.1 {*//*
    public static Runnable forceForPlacement(net.minecraft.client.player.LocalPlayer player) {
        boolean wasSneaking = player.isShiftKeyDown();
        player.setShiftKeyDown(true);
        return () -> player.setShiftKeyDown(wasSneaking);
    }
    *//*?} else {*/
    public static Runnable forceForPlacement(net.minecraft.client.network.ClientPlayerEntity player) {
        boolean wasSneaking = player.isSneaking();
        player.setSneaking(true);
        return () -> player.setSneaking(wasSneaking);
    }
    /*?}*/
}
