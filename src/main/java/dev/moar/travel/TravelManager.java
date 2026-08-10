package dev.moar.travel;

import dev.moar.MoarMod;
import dev.moar.api.MoarProperties;
import dev.moar.api.WebhookEvent;
import dev.moar.api.WebhookService;
import dev.moar.travel.bounce.BounceController;
import dev.moar.travel.bridge.TravelBaritoneBridge;
import dev.moar.travel.detour.DetourPlanner;
import dev.moar.travel.elytra.ElytraManager;
import dev.moar.travel.flight.FlightController;
import dev.moar.travel.highway.HighwayDetectorBridge;
import dev.moar.travel.highway.HighwayVerifier;
import dev.moar.travel.highway.IntegrityReport;
import dev.moar.travel.plan.HighwayCandidate;
import dev.moar.travel.plan.HighwayPlanner;
import dev.moar.travel.plan.HighwayRoute;
import dev.moar.travel.telemetry.TravelLog;
import dev.moar.travel.telemetry.TravelTelemetry;
import dev.moar.util.ChatHelper;
import dev.moar.world.SetbackMonitor;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Travel mission state machine; owns phase progression and movement handoffs.
public final class TravelManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Travel");

    private static final TravelManager INSTANCE = new TravelManager();
    public static TravelManager get() { return INSTANCE; }

    private final TravelState          state    = new TravelState();
    private final TravelBaritoneBridge bridge   = TravelBaritoneBridge.get();
    private final BounceController     bounce   = BounceController.get();
    private final FlightController     flight   = FlightController.get();
    private final HighwayPlanner       planner  = new HighwayPlanner();
    private final HighwayDetectorBridge detector = HighwayDetectorBridge.get();
    private final HighwayVerifier      verifier = HighwayVerifier.get();
    private final ElytraManager        elytra   = new ElytraManager();

    private int currentLegIndex = -1;

    // Bounce-leg exit to re-enter after a detour/resupply; null when none active.
    private BlockPos detourResumeExit;

    private int  settleTicks  = 0;
    private int  settleGroundedTicks = 0;
    private int  settleYawDx  = 0;
    private int  settleYawDz  = 0;

    // Retry aborted missions after a short delay.
    private int autoResumeTicks    = 0; // ticks until next re-plan; 0 = none pending
    private int autoResumeAttempts = 0; // consecutive retries since mission start
    private static final int AUTO_RESUME_DELAY_TICKS = 100; // 5 s between retries
    static final int MAX_AUTO_RESUME_ATTEMPTS = 10;          // cap on retry budget
    private static final int MAX_NO_PROGRESS_AUTO_RESUMES = 3;
    private static final int AUTO_RESUME_PROGRESS_BLOCKS = 8;
    private static final int MAX_MINING_RETARGET_ATTEMPTS = 6;
    private static final int TAKEOFF_MIN_SEARCH_AHEAD = 48;
    private static final int TAKEOFF_MAX_SEARCH_AHEAD = 16 * 32;
    private static final int TAKEOFF_SEARCH_STEP = 4;
    private static final int TAKEOFF_SEARCH_LATERAL = 10;
    private static final int TAKEOFF_FALLBACK_EXTENSION = 32;
    private static final int TAKEOFF_SEARCH_UP = 2;
    private static final int TAKEOFF_SEARCH_DOWN = 40;
    private static final int BOUNCE_ENTRY_ALIGN_RADIUS = 3;
    private static final int MINING_DESCENT_THRESHOLD = 4;
    private static final int MINING_TARGET_RADIUS = 2;
    private static final int MINING_ARRIVAL_Y_TOLERANCE = 3;
    private static final int MINING_RETARGET_STEP = 16;
    private static final int MINING_RETARGET_VERTICAL_STEP = 8;
    private static final int NEARBY_LAUNCH_RADIUS = 12;
    private static final int NEARBY_LAUNCH_UP = 4;
    private static final int NEARBY_LAUNCH_DOWN = 8;
    private static final int SETTLE_MIN_TICKS = 10;
    private static final int SETTLE_GROUNDED_TICKS = 3;
    private static final int SETTLE_TIMEOUT_TICKS = 60;
    private static final int DETOUR_WAYPOINT_RADIUS = 1;
    private static final int DETOUR_FALL_GRACE_TICKS = 10;
    private static final int DETOUR_FALL_TOLERANCE = 3;
    private static final int BOUNCE_FALL_CORRECTION_GRACE_TICKS = 50;
    private static final int BOUNCE_DEEP_FALL_ABORT_CONFIRM_TICKS = 2;
    private static final int RESUPPLY_GROUNDED_TICKS = 5;
    private static final int RESUPPLY_LANDING_RADIUS = 48;
    private static final int RESUPPLY_LANDING_DOWN = 64;
    private static final int RESUPPLY_LANDING_UP = 4;
    private static final int RESUPPLY_RETARGET_TICKS = 40;
    private static final int RESUPPLY_LOCAL_SETTLE_RADIUS = 8;
    private static final int RESUPPLY_LOCAL_SETTLE_VERTICAL = 4;
    private static final int RESUPPLY_LOCAL_SETTLE_TICKS = 40;
    private static final int RESUPPLY_BARITONE_QUIET_TICKS = 10;
    private static final int RESUPPLY_LANDING_SEARCH_TIMEOUT_TICKS = 60;
    private static final int FLIGHT_PROGRESS_TIMEOUT_TICKS = 200;
    private static final int FLIGHT_GLIDE_LOSS_TIMEOUT_TICKS = 40;
    private static final int FLIGHT_PROGRESS_MIN_BLOCKS = 4;
    private static final int BARITONE_FLIGHT_RECOVERY_TICKS = 160;
    private static final int BARITONE_FLIGHT_RETRY_TICKS = 40;
    private static final int MAX_FLIGHT_RECOVERY_ATTEMPTS = 2;
    private static final int MAX_FLIGHT_ESCAPE_ATTEMPTS = 3;
    private static final int MAX_LAUNCH_CANDIDATES = 24;
    private static final int LAUNCH_CORRIDOR_DISTANCE = 24;
    private static final int MANUAL_RECOVERY_CORRIDOR_DISTANCE = 24;
    private static final int HEALTH_LOSS_WINDOW_TICKS = 60;
    private static final float MIN_SAFE_TRAVEL_HEALTH = 14.0F;
    private static final float CRITICAL_TRAVEL_HEALTH = 8.0F;
    private static final float MAX_RECENT_HEALTH_LOSS = 6.0F;
    private static final float MANUAL_RECOVERY_MIN_HEALTH = 18.0F;
    private static final float MANUAL_RECOVERY_MAX_RECENT_LOSS = 2.0F;

    private int miningRetargetAttempts = 0;
    private MiningTraversal miningTraversal = MiningTraversal.NONE;
    private BlockPos activeMineTarget;
    private BlockPos activeTurnBranchTarget;
    private BlockPos activeTurnTarget;
    private int turnRetargetAttempts;
    private int bounceFallAbortTicks;
    private TravelPhase resupplyResumePhase;
    private BlockPos resupplyResumeTarget;
    private int detourMinSafeY = Integer.MIN_VALUE;
    private int detourCorrectionEpisodeBaseline;
    private ResupplyRequest pendingResupply = ResupplyRequest.NONE;
    private BlockPos resupplyLandingTarget;
    private int resupplyGroundedTicks;
    private int resupplyLocalSettleTicks;
    private int resupplyBaritoneQuietTicks;
    private boolean resupplyUsedBaritone;
    private boolean connectionPaused;
    private boolean connectionResumePending;
    private BlockPos connectionResumeTarget;
    private BlockPos flightProgressTarget;
    private int flightBestDistance = Integer.MAX_VALUE;
    private int flightNoProgressTicks;
    private int flightNotGlidingTicks;
    private int baritoneFlightRecoveryTicks;
    private int flightRecoveryAttempts;
    private int flightEscapeAttempts;
    private boolean flightEscapePrerequisite;
    private BlockPos lastAutoResumeAbortPos;
    private String lastAutoResumeAbortReason;
    private int noProgressAutoResumeCount;
    private float lastEffectiveHealth = Float.NaN;
    private float recentHealthLoss;
    private int healthLossWindowTicks;

    private enum ResupplyRequest {
        NONE,
        ELYTRA,
        FIREWORKS
    }

    private enum MiningTraversal {
        NONE,
        DIRECT,
        COLUMN_APPROACH,
        DESCEND_BARITONE,
        DESCEND_MANUAL,
        FINAL_APPROACH
    }

    private TravelManager() {}

    public synchronized boolean start(TravelMission mission) {
        if (state.phase != TravelPhase.IDLE) {
            LOGGER.warn("start() rejected: phase={} (not IDLE)", state.phase);
            return false;
        }
        bridge.cancelAll();
        bounce.stop();
        flight.stop();
        elytra.stop();
        state.reset();
        verifier.clear();
        state.mission = mission;
        currentLegIndex = -1;
        detourResumeExit = null;
        settleTicks = 0;
        settleGroundedTicks = 0;
        autoResumeAttempts = 0;
        autoResumeTicks = 0;
        clearAutoResumeProgress();
        miningRetargetAttempts = 0;
        miningTraversal = MiningTraversal.NONE;
        activeMineTarget = null;
        bounceFallAbortTicks = 0;
        clearTurnHandoff();
        resupplyResumePhase = null;
        resupplyResumeTarget = null;
        detourMinSafeY = Integer.MIN_VALUE;
        clearPendingResupply();
        clearConnectionCheckpoint();
        clearFlightProgress();
        clearFlightRecovery();
        resetTravelSafetyMonitor();
        transition(TravelPhase.PLANNING, "user start: " + mission);
        return true;
    }

    public synchronized void stop() {
        settleTicks = 0;
        settleGroundedTicks = 0;
        detourResumeExit = null;
        autoResumeTicks = 0;
        autoResumeAttempts = MAX_AUTO_RESUME_ATTEMPTS;
        clearAutoResumeProgress();
        miningRetargetAttempts = 0;
        miningTraversal = MiningTraversal.NONE;
        activeMineTarget = null;
        bounceFallAbortTicks = 0;
        clearTurnHandoff();
        detourMinSafeY = Integer.MIN_VALUE;
        clearPendingResupply();
        clearConnectionCheckpoint();
        clearFlightProgress();
        clearFlightRecovery();
        resetTravelSafetyMonitor();
        elytra.stop();
        bridge.cancelAll();
        bounce.stop();
        flight.stop();
        state.owner = MovementOwner.NONE;
        if (state.phase == TravelPhase.IDLE) {
            state.abortReason = "user stop";
            state.lastTransitionReason = "user stop";
            return;
        }
        state.abortReason = "user stop";
        transition(TravelPhase.ABORTED, "user stop");
    }

    public synchronized void pause() {
        if (state.phase == TravelPhase.IDLE || state.phase == TravelPhase.PAUSED) return;
        state.pausedFromPhase = state.phase;
        if (state.phase == TravelPhase.ELYTRA_RESUPPLY) elytra.pause();
        releaseOwner(state.owner);
        state.owner = MovementOwner.NONE;
        TravelPhase from = state.phase;
        state.phase = TravelPhase.PAUSED;
        state.ticksInPhase = 0;
        state.lastTransitionReason = "user pause from " + from;
        TravelLog.get().recordTransition(missionId(), state.missionTicks, from, TravelPhase.PAUSED, state.lastTransitionReason);
        publishTravelEvent(from, TravelPhase.PAUSED, state.lastTransitionReason);
    }

    public synchronized void resume() {
        if (!isPlayerInNether()) {
            state.lastTransitionReason = "resume blocked: not in the nether";
            return;
        }
        if (state.phase == TravelPhase.PAUSED) {
            if (connectionPaused) {
                resumeConnectionCheckpoint("user resume after reconnect");
                return;
            }
            TravelPhase target = state.pausedFromPhase != null ? state.pausedFromPhase : TravelPhase.PLANNING;
            if (target == TravelPhase.ELYTRA_RESUPPLY) elytra.resumeFromCheckpoint();
            resetTravelSafetyMonitor();
            transition(target, "user resume");
            return;
        }
        // Re-plan to last destination after stop.
        if (state.phase == TravelPhase.IDLE && state.mission != null) {
            if (resumeSavedProgress("user resume")) return;
            TravelMission m = state.mission;
            state.reset();
            verifier.clear();
            state.mission = m;
            currentLegIndex = -1;
            detourResumeExit = null;
            miningTraversal = MiningTraversal.NONE;
            activeMineTarget = null;
            resetTravelSafetyMonitor();
            transition(TravelPhase.PLANNING, "user resume");
        }
    }

    // Preserve the active leg across an involuntary disconnect.
    public synchronized void onDisconnect() {
        if (state.phase == TravelPhase.IDLE || state.phase.isTerminal() || state.mission == null) {
            bridge.cancelAll();
            bounce.stop();
            flight.stop();
            return;
        }
        TravelPhase from = state.phase;
        state.pausedFromPhase = from;
        if (from == TravelPhase.BOUNCING) rememberCurrentBounceExit();
        connectionResumeTarget = checkpointTargetFor(from);
        connectionPaused = true;
        connectionResumePending = false;
        if (from == TravelPhase.ELYTRA_RESUPPLY) elytra.pause();
        releaseOwner(state.owner);
        state.owner = MovementOwner.NONE;
        state.phase = TravelPhase.PAUSED;
        state.ticksInPhase = 0;
        state.lastTransitionReason = "connection lost; route checkpoint preserved";
        TravelLog.get().recordTransition(missionId(), state.missionTicks, from,
                TravelPhase.PAUSED, state.lastTransitionReason);
        publishTravelEvent(from, TravelPhase.PAUSED, state.lastTransitionReason);
        LOGGER.warn("[Travel] connection lost during {}; preserving leg {} target={}",
                from, currentLegIndex, connectionResumeTarget);
    }

    // Resume only after the joined Nether world is available.
    public synchronized void onReconnect() {
        if (connectionPaused && state.mission != null && state.mission.autoResume) {
            connectionResumePending = true;
        }
    }

    // EC position for ElytraManager resupply.
    public synchronized void setEnderChestPos(BlockPos pos) { elytra.setEnderChestPos(pos); }
    public synchronized BlockPos getEnderChestPos()         { return elytra.getEnderChestPos(); }

    // Repair worn elytra without a travel mission; returns to IDLE when done.
    public synchronized boolean startStandaloneRepair() {
        if (state.phase != TravelPhase.IDLE) {
            LOGGER.warn("[Travel] startStandaloneRepair rejected: phase={}", state.phase);
            return false;
        }
        clearResupplyResumeContext();
        elytra.startRepair();
        transition(TravelPhase.ELYTRA_RESUPPLY, "standalone repair");
        return true;
    }

    public synchronized TravelTelemetry snapshot() {
        if (state.mission == null) return TravelTelemetry.idle();
        return new TravelTelemetry(
                state.mission.id,
                state.phase,
                state.owner,
                state.ticksInPhase,
                state.missionTicks,
                state.mission.destination,
                bridge.currentTarget(),
                state.route != null ? state.route.primary : null,
                state.lastTransitionReason,
                state.abortReason,
                bridge.isPathing(),
                bridge.isStuck(),
                verifier.lastReport(),
                0.0, 0.0, 0, "n/a", false, "n/a"
        );
    }

    public synchronized TravelPhase currentPhase() { return state.phase; }

    // Drive bounce before movement physics.
    public synchronized void preTick(Object client) {
        if (state.phase == TravelPhase.BOUNCING) {
            bounce.preTick();
        } else if (state.phase == TravelPhase.SETTLE) {
            // Hold travel yaw while bounce settles.
            setPlayerYaw(yawForDir(settleYawDx, settleYawDz));
        }
    }

    public synchronized void tick(Object client) {
        if (state.phase == TravelPhase.IDLE) {
            // Replan when the retry delay expires.
            if (autoResumeTicks > 0) {
                autoResumeTicks--;
                if (autoResumeTicks == 0 && state.mission != null) {
                    LOGGER.info("[Travel] auto-resuming after abort (attempt {}/{})",
                            autoResumeAttempts, MAX_AUTO_RESUME_ATTEMPTS);
                    TravelMission m = state.mission;
                    state.reset();
                    verifier.clear();
                    state.mission = m;
                    currentLegIndex = -1;
                    detourResumeExit = null;
                    clearTurnHandoff();
                    resetTravelSafetyMonitor();
                    transition(TravelPhase.PLANNING, "auto-resume after abort (attempt " + autoResumeAttempts + ")");
                }
            }
            return;
        }
        if (state.phase != TravelPhase.PAUSED && isPlayerDead()) {
            haltAfterPlayerDeath();
            return;
        }
        if (state.phase != TravelPhase.PAUSED && !isPlayerInNether()) {
            pauseForDimensionChange();
            return;
        }
        if (state.phase == TravelPhase.PAUSED && connectionResumePending && isPlayerInNether()) {
            resumeConnectionCheckpoint("automatic reconnect resume");
            return;
        }
        if (state.phase != TravelPhase.PAUSED
                && !state.phase.isTerminal()
                && enforceTravelSafety()) return;
        state.ticksInPhase++;
        state.missionTicks++;

        driveOwner();
        tickVerifier();

        switch (state.phase) {
            case PLANNING               -> tickPlanning();
            case APPROACH_ONRAMP        -> tickApproach();
            case BOUNCING               -> tickBouncing();
            case MINING_TO_FREENETHER   -> tickMining();
            case ELYTRA_RESUPPLY        -> tickElytraResupply();
            case LANDING_FOR_RESUPPLY   -> tickLandingForResupply();
            case OFFRAMP_HANDOFF        -> tickOffRampHandoff();
            case ARRIVED, ABORTED       -> tickTerminal();
            case PAUSED                 -> { /* no-op */ }
            case VERIFYING_DETOUR       -> tickVerifyingDetour();
            case DETOURING              -> tickDetouring();
            case SETTLE                 -> tickSettle();
            case LAUNCH                 -> tickLaunch();
            case ELYTRA_CRUISE          -> tickElytraCruise();
            case ELYTRA_FALLBACK        -> tickElytraFallback();
            default -> { /* IDLE handled above */ }
        }
    }

    private void driveOwner() {
        if      (state.owner == MovementOwner.BARITONE) bridge.tick();
        else if (state.owner == MovementOwner.BOUNCE)   bounce.tick();
        else if (state.owner == MovementOwner.FLIGHT)   flight.tick();
    }

    private void tickVerifier() {
        if (state.route == null || state.route.primary == null) return;
        BlockPos pos = currentPlayerPos();
        if (pos == null) return;
        if (state.phase == TravelPhase.APPROACH_ONRAMP
                || state.phase == TravelPhase.MINING_TO_FREENETHER
                || state.phase == TravelPhase.BOUNCING) {
            verifier.tick(pos);
        }
    }

    private void tickPlanning() {
        if (state.route != null) return;

        BlockPos origin = currentPlayerPos();
        if (origin == null) { abort("no player on PLANNING"); return; }

        HighwayPlanner.Options opts = new HighwayPlanner.Options()
                .freeNetherFlightThreshold(state.mission.freeNetherFlightThreshold)
                .allowFlight(state.mission.useElytra)
                .horizontalDestination(state.mission.horizontalDestination);
        if (state.mission.expectedHighwayFloorY != Integer.MIN_VALUE)
            opts.expectedFloorY(state.mission.expectedHighwayFloorY);

        Optional<HighwayRoute> planned = planner.plan(origin, state.mission.destination, opts);
        if (planned.isEmpty()) {
            // Approach a plausible highway before replanning.
            if (tryStartDirectFlightFallback(origin, opts)) return;
            abort("planner returned no route");
            return;
        }

        state.route = planned.get();
        currentLegIndex = -1;
        clearTurnHandoff();
        LOGGER.info("[Travel] planned {}", state.route);
        advanceLeg("planning complete");
    }

    // Fly toward a plausible highway when no route is confirmed.
    private boolean tryStartDirectFlightFallback(BlockPos origin, HighwayPlanner.Options opts) {
        if (state.mission == null || !state.mission.useElytra) return false;
        if (horizontalDistance(origin, state.mission.destination) < state.mission.freeNetherFlightThreshold) {
            return false;
        }
        BlockPos flightTarget = planner.suggestFlightWaypoint(origin, state.mission.destination, opts);

        List<HighwayRoute.Leg> legs;
        double dist;
        if (isStrongLaunchAnchor(origin, flightTarget)) {
            legs = List.of(new HighwayRoute.FlightLeg(flightTarget));
            dist = horizontalDistance(origin, flightTarget);
        } else {
            BlockPos nearbyAnchor = findNearbyLaunchAnchor(origin, flightTarget, NEARBY_LAUNCH_RADIUS);
            if (nearbyAnchor == null) return false;
            legs = List.of(new HighwayRoute.ApproachLeg(nearbyAnchor), new HighwayRoute.FlightLeg(flightTarget));
            dist = horizontalDistance(origin, nearbyAnchor) + horizontalDistance(nearbyAnchor, flightTarget);
        }

        state.route = new HighwayRoute(null, legs, dist, 0, 0);
        currentLegIndex = -1;
        clearTurnHandoff();
        LOGGER.info("[Travel] planner returned no highway route; flying toward nearest plausible highway at {}",
                flightTarget.toShortString());
        advanceLeg("planning complete");
        return true;
    }

    private void tickApproach() {
        if (bridge.isArrived()) { advanceLeg("approach arrived"); return; }
        if (bridge.isStuck()) {
            // Retry confined-highway knockback through replanning.
            abort("approach stuck — wither/knockback? auto-resume will retry");
        }
    }

    private void tickBouncing() {
        if (startElytraResupplyIfNeeded()) return;

        boolean stalled = bounce.isStuck() && !bounce.isStuckFromFall();
        if (bounce.isStuckFromFall()) {
            if (!SetbackMonitor.get().isCalm()) {
                bounce.clearFallAlarmAfterCorrection();
                bounceFallAbortTicks = 0;
                return;
            }
            int confirmationTicks = bounce.isDeepHighwayFall()
                    ? BOUNCE_DEEP_FALL_ABORT_CONFIRM_TICKS
                    : BOUNCE_FALL_CORRECTION_GRACE_TICKS;
            if (bounceFallAbortTicks++ == 0 && !bounce.isDeepHighwayFall()) {
                LOGGER.warn("[Travel] holding shallow highway fall for up to {}t awaiting correction; drop={}",
                        confirmationTicks, String.format("%.3f", bounce.fallDepth()));
            }
            if (bounceFallAbortTicks < confirmationTicks) return;
            abort("bounce: fell off highway, gliding to safety");
            return;
        }
        bounceFallAbortTicks = 0;

        // Detour around damaged highway sections.
        BlockPos playerPos = currentPlayerPos();
        IntegrityReport rep = stalled
                ? verifier.sampleNow(playerPos)
                : verifier.lastReport();
        if (rep.status() == IntegrityReport.Status.GRIEFED
                && rep.confidence() >= DetourPlanner.MIN_CONFIDENCE) {
            if (state.mission == null || !state.mission.allowDetour) {
                abort("highway grief detected and detours disabled: " + rep);
                return;
            }
            LOGGER.warn("[Travel] grief detected during bounce: {}", rep);
            rememberCurrentBounceExit();
            releaseOwner(state.owner);
            state.owner = MovementOwner.NONE;
            transition(TravelPhase.VERIFYING_DETOUR, "grief detected: " + rep);
            return;
        }
        // Detour around immediate obstacles.
        if (bounce.isWallAhead()) {
            LOGGER.warn("[Travel] wall/obstacle ahead during bounce, triggering bypass: {}",
                    bounce.wallReason());
            if (state.mission == null || !state.mission.allowDetour) {
                abort("wall ahead and detours disabled");
                return;
            }
            triggerWallBypass();
            return;
        }
        // Recover lateral highway displacement.
        if (isPlayerKnockedOffHighway()) {
            if (state.mission == null || !state.mission.allowDetour) {
                abort("knocked off highway and detours disabled");
                return;
            }
            triggerKnockbackRecovery();
            return;
        }
        if (stalled) {
            if (state.mission == null || !state.mission.allowDetour) {
                abort("bounce stalled and detours disabled");
                return;
            }
            LOGGER.warn("[Travel] bounce stalled with {}; handing off to Baritone bypass", rep);
            triggerForwardBypass("bounce stall bypass", STALL_BYPASS_DISTANCE);
            return;
        }
        if (bounce.isArrived()) { advanceLeg("bounce arrived"); }
    }

    // Drive ElytraManager; resume bounce on done, abort on failure, IDLE for standalone.
    private void tickElytraResupply() {
        elytra.tick();
        if (elytra.isDone()) {
            LOGGER.info("[Travel] elytra resupply done");
            verifier.resetLastReport();
            if (resumeAfterResupply("elytra resupply complete")) {
                return;
            }
            if (state.route == null) {
                transition(TravelPhase.IDLE, "standalone repair complete");
            } else {
                advanceLeg("elytra resupply complete");
            }
        } else if (elytra.isFailed()) {
            if (state.route == null) {
                elytra.stop();
                transition(TravelPhase.IDLE, "standalone repair failed");
            } else {
                abort("elytra resupply failed — no viable traveling materials");
            }
        }
    }

    // Request a safe landing before repairing the elytra.
    private void startElytraResupply() {
        requestResupply(ResupplyRequest.ELYTRA, "elytra durability critical");
    }

    private boolean startElytraResupplyIfNeeded() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null || !ElytraManager.needsResupply(mc)) return false;
        startElytraResupply();
        return true;
    }

    private boolean startFireworkRestockIfNeeded() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null || !ElytraManager.needsFireworksRestock(mc)) return false;
        startFireworkRestock();
        return true;
    }

    private void startFireworkRestock() {
        requestResupply(ResupplyRequest.FIREWORKS, "fireworks low");
    }

    private void requestResupply(ResupplyRequest request, String reason) {
        if (pendingResupply != ResupplyRequest.NONE) return;
        rememberResupplyResumeContext();
        rememberCurrentBounceExit();
        pendingResupply = request;
        resupplyGroundedTicks = 0;
        resupplyLocalSettleTicks = 0;
        resupplyBaritoneQuietTicks = 0;
        resupplyUsedBaritone = bridge.isElytraOwning();

        if (isSafeGroundedForResupply()) {
            if (resupplyUsedBaritone) {
                transition(TravelPhase.LANDING_FOR_RESUPPLY,
                        reason + "; waiting for native flight shutdown");
            } else {
                startPendingResupply(reason + "; safe ground confirmed");
            }
            return;
        }

        resupplyLandingTarget = findSafeResupplyLanding();
        LOGGER.warn("[Travel] {} requested while airborne/unsafe; landing target={}",
                request, resupplyLandingTarget);
        if (resupplyLandingTarget != null) {
            if (state.owner != MovementOwner.BARITONE || !bridge.isElytraOwning()) {
                releaseOwner(state.owner);
                state.owner = MovementOwner.NONE;
            }
            if (canSettleLocallyForResupply(resupplyLandingTarget)) {
                resupplyLocalSettleTicks = RESUPPLY_LOCAL_SETTLE_TICKS;
                LOGGER.info("[Travel] nearby safe ground; settling without native elytra pathing");
            } else {
                startBaritoneResupplyLanding();
            }
        } else {
            releaseOwner(state.owner);
            state.owner = MovementOwner.NONE;
            bridge.cancelAll();
            flight.stop();
            LOGGER.error("[Travel] no verified resupply landing in loaded chunks; coasting while rescanning");
        }
        transition(TravelPhase.LANDING_FOR_RESUPPLY,
                reason + "; waiting for safe solid ground");
    }

    private void tickLandingForResupply() {
        if (isSafeGroundedForResupply()) {
            resupplyGroundedTicks++;
            if (resupplyGroundedTicks >= RESUPPLY_GROUNDED_TICKS) {
                if (bridge.isElytraOwning()) {
                    resupplyBaritoneQuietTicks = 0;
                    return;
                }
                if (resupplyUsedBaritone
                        && ++resupplyBaritoneQuietTicks < RESUPPLY_BARITONE_QUIET_TICKS) {
                    return;
                }
                startPendingResupply("safe landing confirmed");
            }
            return;
        }
        resupplyGroundedTicks = 0;
        resupplyBaritoneQuietTicks = 0;

        if (resupplyLocalSettleTicks > 0) {
            resupplyLocalSettleTicks--;
            if (canSettleLocallyForResupply(resupplyLandingTarget)) return;
            resupplyLocalSettleTicks = 0;
        }

        if (resupplyLandingTarget == null || !isSafeLandingPosition(resupplyLandingTarget)) {
            BlockPos replacement = findSafeResupplyLanding();
            if (replacement != null && !replacement.equals(resupplyLandingTarget)) {
                resupplyLandingTarget = replacement;
                LOGGER.info("[Travel] safe resupply landing retargeted to {}",
                        replacement.toShortString());
            }
        }

        if (resupplyLandingTarget == null
                && state.ticksInPhase >= RESUPPLY_LANDING_SEARCH_TIMEOUT_TICKS) {
            String reason = "No verified safe landing for travel resupply";
            stopUnsafeFlight(reason);
            elytra.disconnectForTravelSafety(reason);
            return;
        }

        if (resupplyLandingTarget != null
                && state.ticksInPhase % RESUPPLY_RETARGET_TICKS == 0
                && !bridge.isElytraOwning()) {
            startBaritoneResupplyLanding();
        }
    }

    private void startBaritoneResupplyLanding() {
        if (resupplyLandingTarget == null) return;
        if (bridge.isElytraOwning()) {
            state.owner = MovementOwner.BARITONE;
            resupplyUsedBaritone = true;
            bridge.startElytraFlight(resupplyLandingTarget);
            return;
        }
        if (state.owner != MovementOwner.BARITONE) {
            releaseOwner(state.owner);
            state.owner = MovementOwner.NONE;
            acquireOwner(MovementOwner.BARITONE);
        }
        resupplyUsedBaritone = true;
        bridge.startElytraFlight(resupplyLandingTarget);
    }

    private boolean canSettleLocallyForResupply(BlockPos target) {
        BlockPos pos = currentPlayerPos();
        if (pos == null || target == null || bridge.isElytraOwning()) return false;
        return horizontalDistance(pos, target) <= RESUPPLY_LOCAL_SETTLE_RADIUS
                && Math.abs(pos.getY() - target.getY()) <= RESUPPLY_LOCAL_SETTLE_VERTICAL;
    }

    private void startPendingResupply(String reason) {
        ResupplyRequest request = pendingResupply;
        if (request == ResupplyRequest.NONE || bridge.isElytraOwning()) return;
        if (state.owner != MovementOwner.BARITONE) {
            releaseOwner(state.owner);
        }
        state.owner = MovementOwner.NONE;
        bridge.clearElytraTarget();
        flight.stop();
        resupplyLandingTarget = null;
        resupplyGroundedTicks = 0;
        resupplyLocalSettleTicks = 0;
        resupplyBaritoneQuietTicks = 0;
        resupplyUsedBaritone = false;
        if (request == ResupplyRequest.FIREWORKS) {
            LOGGER.warn("[Travel] safe ground confirmed — entering firework resupply");
            elytra.startFireworkRestockForTravel();
        } else {
            LOGGER.warn("[Travel] safe ground confirmed — entering elytra resupply");
            elytra.start();
        }
        pendingResupply = ResupplyRequest.NONE;
        transition(TravelPhase.ELYTRA_RESUPPLY, reason);
    }

    // Plan detour waypoints and hand off to Baritone.
    private void tickVerifyingDetour() {
        BlockPos pos = currentPlayerPos();
        if (pos == null) { abort("no player pos during detour verification"); return; }
        HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
        if (bounceLeg == null) { abort("detour verification without active bounce leg"); return; }

        IntegrityReport rep = verifier.lastReport();
        List<BlockPos> waypoints = DetourPlanner.plan(
                bounceLeg.highway(), rep, pos, bounceLeg.travelDx(), bounceLeg.travelDz());
        if (waypoints.isEmpty()) {
            abort("detour planning failed: " + rep);
            return;
        }

        releaseOwner(state.owner);
        acquireOwner(MovementOwner.BARITONE);
        beginDetourTracking(pos, waypoints.get(0), DETOUR_WAYPOINT_RADIUS);
        bridge.walkToWaypoints(waypoints, DETOUR_WAYPOINT_RADIUS);
        transition(TravelPhase.DETOURING,
                "detour planned: " + waypoints.size() + " waypoints, griefRange=["
                + rep.griefStartOffset() + "," + rep.griefEndOffset() + "]");
    }

    // Resume bounce when Baritone finishes the detour.
    private void tickDetouring() {
        int correctionEpisodes = SetbackMonitor.get().totalCorrectionEpisodes()
                - detourCorrectionEpisodeBaseline;
        if (correctionEpisodes > 0) {
            LOGGER.warn("[Travel] cancelling rejected detour after {} correction episode(s)",
                    correctionEpisodes);
            abort("detour rejected by server correction");
            return;
        }
        // Ignore correction displacement while Baritone initializes.
        if (state.ticksInPhase >= DETOUR_FALL_GRACE_TICKS
                && SetbackMonitor.get().isCalm()
                && detourMinSafeY != Integer.MIN_VALUE) {
            BlockPos pos = currentPlayerPos();
            if (pos != null && pos.getY() < detourMinSafeY) {
                abort("detouring fall — player below safe Y " + detourMinSafeY
                        + " (y=" + pos.getY() + ")");
                return;
            }
        }
        // Keep detours on the active bounce leg.
        if (bridge.isElytraOwning()) {
            BlockPos target = bridge.currentTarget();
            LOGGER.warn("[Travel] DETOURING: rejecting unexpected Baritone elytra ownership");
            bridge.stopElytra();
            if (target == null) {
                abort("detour lost its ground target");
                return;
            }
            bridge.walkNear(target, DETOUR_WAYPOINT_RADIUS);
            return;
        }
        if (bridge.isArrived()) {
            LOGGER.info("[Travel] detour/bypass complete, settling before bounce resume");
            if (detourResumeExit != null && state.route != null) {
                HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
                if (bounceLeg == null) {
                    abort("detour complete but no bounce leg available to resume");
                    return;
                }
                settleYawDx = bounceLeg.travelDx();
                settleYawDz = bounceLeg.travelDz();
                settleTicks = 0;
                settleGroundedTicks = 0;
                releaseOwner(state.owner);
                state.owner = MovementOwner.NONE;
                transition(TravelPhase.SETTLE, "detour complete, entering settle");
            } else {
                detourResumeExit = null;
                advanceLeg("detour complete (no resume exit)");
            }
            return;
        }
        if (bridge.isStuck()) {
            abort("detour stuck — wither/knockback? auto-resume will retry");
        }
    }

    // Resume only after the player is stably grounded.
    private void tickSettle() {
        settleTicks++;
        if (isPlayerGrounded() && !isPlayerGliding()) {
            settleGroundedTicks++;
        } else {
            settleGroundedTicks = 0;
        }
        if (settleTicks >= SETTLE_MIN_TICKS
                && settleGroundedTicks >= SETTLE_GROUNDED_TICKS) {
            if (detourResumeExit != null && state.route != null && resumeCurrentBounce()) {
                LOGGER.info("[Travel] SETTLE done after {}t (grounded={}t), resuming bounce to {}",
                        settleTicks, settleGroundedTicks, detourResumeExit);
                // Clear stale integrity data.
                verifier.resetLastReport();
                detourResumeExit = null;
                settleTicks = 0;
                settleGroundedTicks = 0;
                autoResumeAttempts = 0; // detour succeeded: reset retry budget
                transition(TravelPhase.BOUNCING, "settle complete, resuming bounce");
            } else {
                detourResumeExit = null;
                settleTicks = 0;
                settleGroundedTicks = 0;
                advanceLeg("settle complete (no resume exit)");
            }
        } else if (settleTicks >= SETTLE_TIMEOUT_TICKS) {
            settleTicks = 0;
            settleGroundedTicks = 0;
            abort("settle timed out before stable ground contact");
        }
    }

    private void tickMining() {
        BlockPos pos = currentPlayerPos();
        BlockPos flightDest = plannedFlightDestination();
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player != null && flightDest != null && ElytraManager.needsFireworksRestock(mc)) {
            startFireworkRestock();
            return;
        }
        if (bridge.isArrived()) {
            if (advanceMiningTraversal(pos)) return;
            if (hasAnotherMineLegAhead()) {
                miningRetargetAttempts = 0;
                miningTraversal = MiningTraversal.NONE;
                activeMineTarget = null;
                advanceLeg("mining waypoint arrived");
                return;
            }
            if (pos != null && flightDest != null && !isStrongLaunchAnchor(pos, flightDest)) {
                BlockPos nearbyAnchor = findNearbyLaunchAnchor(pos, flightDest, NEARBY_LAUNCH_RADIUS);
                if (nearbyAnchor != null) {
                    if (outsideMiningArrivalEnvelope(pos, nearbyAnchor)
                            && (activeMineTarget == null || !sameHorizontalTarget(activeMineTarget, nearbyAnchor))) {
                        LOGGER.info("[Travel] nearby launch anchor found at {}", nearbyAnchor.toShortString());
                        startMiningTraversal(nearbyAnchor, "mining arrived -> move to nearby launch anchor");
                        return;
                    }
                }
                if (retargetMiningTakeoff(pos, flightDest, "mining arrived but takeoff corridor is still enclosed")) {
                    return;
                }
                stopUnsafeFlight("no verified open-nether takeoff corridor was found");
                return;
            }
            IntegrityReport rep = verifier.lastReport();
            LOGGER.info("[Travel] mining arrived; last integrity={}", rep);
            miningRetargetAttempts = 0;
            miningTraversal = MiningTraversal.NONE;
            activeMineTarget = null;
            advanceLeg("mining-leg arrived");
            return;
        }
        if (bridge.isStuck()) {
            if ((miningTraversal == MiningTraversal.DESCEND_BARITONE
                    || miningTraversal == MiningTraversal.COLUMN_APPROACH
                    || miningTraversal == MiningTraversal.FINAL_APPROACH)
                    && activeMineTarget != null
                    && tryManualMiningDescent("mining path stuck, forcing downward descent")) {
                return;
            }
            if (pos != null && flightDest != null
                    && retargetMiningTakeoff(pos, flightDest, "mining stuck, searching for better takeoff")) {
                return;
            }
            abort("mining stuck");
        }
    }

    private void tickOffRampHandoff() {
        if (bridge.isArrived()) {
            HighwayRoute.BounceLeg nextBounce = nextBounceLeg();
            BlockPos pos = currentPlayerPos();
            if (nextBounce == null || pos == null) {
                abort("junction handoff completed without a bounce target");
                return;
            }
            if (!confirmNextBounceAtJunction(pos, nextBounce)) {
                String reason = "planned highway branch not found at "
                        + pos.toShortString() + " axis=" + nextBounce.highway().axis;
                LOGGER.error("[Travel] {}; refusing synthetic branch", reason);
                ChatHelper.labelled("Travel", "§cNo highway was verified at this junction. Travel stopped safely.");
                stopUnsafeFlight(reason);
                return;
            }
            nextBounce = nextBounceLeg();
            settleYawDx = nextBounce.travelDx();
            settleYawDz = nextBounce.travelDz();
            settleTicks = 0;
            settleGroundedTicks = 0;
            detourResumeExit = null;
            clearTurnHandoff();
            releaseOwner(state.owner);
            state.owner = MovementOwner.NONE;
            transition(TravelPhase.SETTLE, "junction handoff arrived; settling before bounce");
            return;
        }
        if (bridge.isStuck()) {
            if (turnRetargetAttempts == 0 && activeTurnBranchTarget != null) {
                BlockPos replacement = resolveTurnTarget(activeTurnBranchTarget, activeTurnTarget);
                if (replacement != null && !replacement.equals(activeTurnTarget)) {
                    turnRetargetAttempts++;
                    activeTurnTarget = replacement;
                    LOGGER.info("[Travel] turn handoff retargeted to {}", replacement.toShortString());
                    startBaritoneWalk(replacement, 2, TravelPhase.OFFRAMP_HANDOFF,
                            "junction handoff retarget");
                    return;
                }
            }
            abort("junction handoff stuck");
        }
    }

    // Confirm the branch before bounce takes ownership.
    private boolean confirmNextBounceAtJunction(BlockPos pos, HighwayRoute.BounceLeg planned) {
        int index = nextBounceLegIndex();
        return index >= 0 && confirmBounceAtPosition(pos, planned, index, "junction branch");
    }

    private boolean confirmBounceAtPosition(BlockPos pos, HighwayRoute.BounceLeg planned,
                                            int index, String context) {
        Optional<HighwayDetectorBridge.ScanResult> scan = detector.scanAt(pos, planned.highway().axis);
        if (scan.isEmpty()) return false;

        HighwayDetectorBridge.ScanResult result = scan.get();
        if (result.surface() == HighwayDetectorBridge.Surface.TUNNEL
                && (result.width() < 3 || result.width() > 5)) {
            LOGGER.warn("[Travel] rejected cave-like {} axis={} width={} conf={}",
                    context, planned.highway().axis, result.width(),
                    String.format("%.2f", result.blockConfidence()));
            return false;
        }

        HighwayCandidate highway = planned.highway();
        BlockPos scannedEntry = new BlockPos(result.centerX(), result.floorY(), result.centerZ());
        if (horizontalDistance(scannedEntry, pos) > 12) return false;

        int perpX = highway.axis.perpDx();
        int perpZ = highway.axis.perpDz();
        int denominator = Math.max(1, perpX * perpX + perpZ * perpZ);
        int offsetNumerator = (scannedEntry.getX() - highway.entry.getX()) * perpX
                + (scannedEntry.getZ() - highway.entry.getZ()) * perpZ;
        int exitShiftX = (int) Math.round(perpX * offsetNumerator / (double) denominator);
        int exitShiftZ = (int) Math.round(perpZ * offsetNumerator / (double) denominator);
        BlockPos scannedExit = new BlockPos(
                planned.exitColumn().getX() + exitShiftX,
                result.floorY(),
                planned.exitColumn().getZ() + exitShiftZ);
        HighwayCandidate confirmed = new HighwayCandidate(
                highway.axis, highway.category, result.floorY(),
                scannedEntry, scannedExit, result.blockConfidence(),
                highway.ringOrDiamondDist, highway.ringSide, highway.diamondSegment,
                result.width(), result.hasLeftRail(), result.hasRightRail(),
                result.surface() == HighwayDetectorBridge.Surface.TUNNEL);

        if (state.route == null || index < 0 || index >= state.route.legs.size()) return false;
        List<HighwayRoute.Leg> legs = new ArrayList<>(state.route.legs);
        legs.set(index, new HighwayRoute.BounceLeg(
                confirmed, scannedExit, planned.travelDx(), planned.travelDz()));
        HighwayCandidate primary = state.route.primary == highway ? confirmed : state.route.primary;
        state.route = new HighwayRoute(
                primary, legs, state.route.estimatedCost,
                state.route.travelDx, state.route.travelDz);
        LOGGER.info("[Travel] verified {} axis={} surface={} floorY={} width={} conf={}",
                context, confirmed.axis, result.surface(), result.floorY(), result.width(),
                String.format("%.2f", result.blockConfidence()));
        return true;
    }

    private static boolean requiresLocalBounceConfirmation(HighwayRoute.BounceLeg bounceLeg) {
        return bounceLeg.highway().width <= 0;
    }

    private boolean recordAutoResumeAbort(String reason, BlockPos pos) {
        if (reason == null || pos == null || "user stop".equals(reason)) return false;
        boolean sameFailure = reason.equals(lastAutoResumeAbortReason)
                && lastAutoResumeAbortPos != null
                && horizontalDistance(lastAutoResumeAbortPos, pos) < AUTO_RESUME_PROGRESS_BLOCKS;
        noProgressAutoResumeCount = sameFailure ? noProgressAutoResumeCount + 1 : 1;
        lastAutoResumeAbortReason = reason;
        lastAutoResumeAbortPos = pos;
        return noProgressAutoResumeCount >= MAX_NO_PROGRESS_AUTO_RESUMES;
    }

    private void clearAutoResumeProgress() {
        lastAutoResumeAbortPos = null;
        lastAutoResumeAbortReason = null;
        noProgressAutoResumeCount = 0;
    }

    private void tickTerminal() {
        if (state.ticksInPhase >= 1) {
            TravelPhase from = state.phase;
            String savedAbortReason = state.abortReason;
            BlockPos abortPos = currentPlayerPos();
            releaseOwner(state.owner);
            verifier.clear();
            // Preserve the mission for resume.
            TravelMission lastMission = state.mission;
            state.reset();
            state.mission = lastMission;
            currentLegIndex = -1;
            detourResumeExit = null;
            miningTraversal = MiningTraversal.NONE;
            activeMineTarget = null;
            clearTurnHandoff();
            TravelLog.get().recordTransition(0, 0, from, TravelPhase.IDLE, "terminal cleanup");

            // Schedule eligible aborts for replanning.
            boolean autoResumeEligible = from == TravelPhase.ABORTED
                    && !"user stop".equals(savedAbortReason)
                    && lastMission != null && lastMission.autoResume;
            boolean noProgressLimitReached = autoResumeEligible
                    && recordAutoResumeAbort(savedAbortReason, abortPos);
            if (noProgressLimitReached) {
                autoResumeTicks = 0;
                autoResumeAttempts = MAX_AUTO_RESUME_ATTEMPTS;
                LOGGER.error("[Travel] auto-resume stopped after {} no-progress aborts near {} reason={}",
                        noProgressAutoResumeCount, abortPos, savedAbortReason);
                ChatHelper.labelled("Travel", "§cRepeated recovery attempts made no progress. Travel stopped safely.");
            } else if (autoResumeEligible
                    && autoResumeAttempts < MAX_AUTO_RESUME_ATTEMPTS) {
                autoResumeAttempts++;
                autoResumeTicks = AUTO_RESUME_DELAY_TICKS;
                LOGGER.warn("[Travel] auto-resume scheduled in {}t (attempt {}/{}) reason={}",
                        AUTO_RESUME_DELAY_TICKS, autoResumeAttempts,
                        MAX_AUTO_RESUME_ATTEMPTS, savedAbortReason);
            }
        }
    }

    // Wait for Baritone to claim elytra movement.
    private void tickLaunch() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player != null) {
            if (startElytraResupplyIfNeeded()) return;
            if (startFireworkRestockIfNeeded()) return;
        }
        if (flight.isCruising() && isPlayerGliding()) {
            LOGGER.info("[Travel] LAUNCH -> ELYTRA_FALLBACK (manual flight entered cruise)");
            transition(TravelPhase.ELYTRA_FALLBACK, "manual flight entered cruise");
            return;
        }
        if (bridge.isElytraOwning() && isPlayerGliding()) {
            LOGGER.info("[Travel] LAUNCH -> ELYTRA_CRUISE (Baritone elytra active, t={})", state.ticksInPhase);
            acquireOwner(MovementOwner.BARITONE);
            transition(TravelPhase.ELYTRA_CRUISE, "Baritone elytra started");
            return;
        }

        BlockPos dest = currentFlightDestination();
        if (dest == null && state.mission != null) dest = state.mission.destination;

        if (flight.isArrived()) {
            completeFlightLeg("manual launch reached destination before Baritone takeover");
            return;
        }
        if (flight.isStuck()) {
            handleUnsafeFlightFailure(dest, "manual elytra launch assist failed");
            return;
        }

        // Retry the flight goal without flooding Baritone.
        if (bridge.isAvailable() && dest != null
                && state.ticksInPhase % BARITONE_FLIGHT_RETRY_TICKS == 0) {
            startBaritoneElytraFlight(dest);
        }

        if (state.ticksInPhase > 200) {
            if (flight.isActive()) {
                LOGGER.warn("[Travel] Baritone elytra did not start within 200 ticks — continuing on manual flight");
                transition(TravelPhase.ELYTRA_FALLBACK, "Baritone elytra launch timeout, continuing manually");
            } else {
                handleUnsafeFlightFailure(dest, "Baritone elytra did not start within 200 ticks");
            }
        }
    }

    // Restore Baritone flight ownership when lost.
    private void tickElytraCruise() {
        if (startElytraResupplyIfNeeded()) return;
        if (startFireworkRestockIfNeeded()) return;
        BlockPos dest = currentFlightDestination();
        if (dest == null && state.mission != null) dest = state.mission.destination;
        if (bridge.isElytraArrived()) {
            completeFlightLeg("elytra cruise arrived");
            return;
        }
        if (dest != null && isBaritoneFlightProgressStalled(dest)) {
            recoverStalledBaritoneFlight(dest);
            return;
        }
        if (bridge.isElytraStuck() || !bridge.isElytraOwning()) {
            LOGGER.warn("[Travel] ELYTRA_CRUISE: Baritone elytra lost ownership");
            if (dest != null) {
                recoverStalledBaritoneFlight(dest);
            } else {
                abort("Baritone elytra lost and no flight destination");
            }
        }
    }

    // Fly manually when Baritone cannot claim elytra movement.
    private void tickElytraFallback() {
        if (startElytraResupplyIfNeeded()) return;
        if (startFireworkRestockIfNeeded()) return;
        BlockPos dest = currentFlightDestination();
        if (dest == null && state.mission != null) dest = state.mission.destination;
        if (baritoneFlightRecoveryTicks > 0) baritoneFlightRecoveryTicks--;
        if (baritoneFlightRecoveryTicks == 0 && bridge.isElytraOwning()) {
            LOGGER.info("[Travel] ELYTRA_FALLBACK -> ELYTRA_CRUISE (Baritone took over mid-flight)");
            acquireOwner(MovementOwner.BARITONE);
            resetFlightProgress(currentFlightDestination());
            transition(TravelPhase.ELYTRA_CRUISE, "Baritone took over from manual flight");
            return;
        }
        if (flight.isArrived()) {
            completeFlightLeg("manual elytra flight arrived");
            return;
        }
        if (flight.isStuck()) {
            handleUnsafeFlightFailure(dest, "manual elytra flight stuck");
            return;
        }
        if (baritoneFlightRecoveryTicks == 0 && bridge.isAvailable() && dest != null
                && state.ticksInPhase % BARITONE_FLIGHT_RETRY_TICKS == 0) {
            startBaritoneElytraFlight(dest);
        }
    }

    // Return the active flight destination.
    private BlockPos currentFlightDestination() {
        HighwayRoute.FlightLeg flightLeg = currentFlightLeg();
        return flightLeg != null ? flightLeg.destination() : null;
    }

    private void startBaritoneElytraFlight(BlockPos destination) {
        bridge.startElytraFlight(destination, isHorizontalMissionDestination(destination));
    }

    private boolean isHorizontalMissionDestination(BlockPos destination) {
        if (destination == null || state.mission == null || !state.mission.horizontalDestination) {
            return false;
        }
        BlockPos missionDestination = state.mission.destination;
        return destination.getX() == missionDestination.getX()
                && destination.getZ() == missionDestination.getZ();
    }

    private void completeFlightLeg(String reason) {
        releaseOwner(state.owner);
        state.owner = MovementOwner.NONE;
        bridge.stopElytra();
        flight.stop();
        clearFlightProgress();
        clearFlightRecovery();
        advanceLeg(reason);
    }

    private boolean isBaritoneFlightProgressStalled(BlockPos destination) {
        BlockPos pos = currentPlayerPos();
        if (pos == null) return false;
        if (flightProgressTarget == null || !flightProgressTarget.equals(destination)) {
            resetFlightProgress(destination);
        }
        if (!isPlayerGliding()) {
            if (SetbackMonitor.get().isCalm()) flightNotGlidingTicks++;
            return flightNotGlidingTicks >= FLIGHT_GLIDE_LOSS_TIMEOUT_TICKS;
        }
        flightNotGlidingTicks = 0;
        int distance = horizontalDistance(pos, destination);
        if (flightBestDistance == Integer.MAX_VALUE
                || distance + FLIGHT_PROGRESS_MIN_BLOCKS <= flightBestDistance) {
            flightBestDistance = distance;
            flightNoProgressTicks = 0;
            return false;
        }
        if (SetbackMonitor.get().isCalm()) flightNoProgressTicks++;
        return flightNoProgressTicks >= FLIGHT_PROGRESS_TIMEOUT_TICKS;
    }

    private void recoverStalledBaritoneFlight(BlockPos destination) {
        BlockPos pos = currentPlayerPos();
        int stalledTicks = Math.max(flightNoProgressTicks, flightNotGlidingTicks);
        flightRecoveryAttempts++;
        if (flightRecoveryAttempts > MAX_FLIGHT_RECOVERY_ATTEMPTS) {
            stopUnsafeFlight("Baritone flight repeatedly stalled; recovery budget exhausted");
            return;
        }
        if (!isPlayerGliding()) {
            if (startEnclosedFlightEscape(destination, pos, stalledTicks)) return;
            stopUnsafeFlight("unsafe enclosed takeoff; no escape route found");
            return;
        }
        String unsafeReason = manualFlightRecoveryUnsafeReason(pos, destination);
        if (unsafeReason != null) {
            LOGGER.warn("[Travel] manual flight recovery blocked at {}: {}",
                    pos, unsafeReason);
            ChatHelper.labelled("Travel", "§cFlight recovery stopped: " + unsafeReason + ".");
            stopUnsafeFlight("manual flight recovery unsafe: " + unsafeReason);
            return;
        }
        LOGGER.warn("[Travel] Baritone elytra made no destination progress for {}t at {}; "
                        + "switching to manual recovery toward {} (attempt {}/{})",
                stalledTicks, pos, destination.toShortString(),
                flightRecoveryAttempts, MAX_FLIGHT_RECOVERY_ATTEMPTS);
        releaseOwner(state.owner);
        state.owner = MovementOwner.NONE;
        acquireOwner(MovementOwner.FLIGHT);
        flight.start(destination);
        baritoneFlightRecoveryTicks = BARITONE_FLIGHT_RECOVERY_TICKS;
        resetFlightProgress(destination);
        transition(TravelPhase.ELYTRA_FALLBACK, "Baritone elytra progress stalled; manual recovery");
    }

    private String manualFlightRecoveryUnsafeReason(BlockPos pos, BlockPos destination) {
        if (pos == null || destination == null) return "missing flight position";
        float health = currentEffectiveHealth();
        if (health < MANUAL_RECOVERY_MIN_HEALTH) {
            return String.format("health is %.1f/20", health);
        }
        if (recentHealthLoss > MANUAL_RECOVERY_MAX_RECENT_LOSS) {
            return String.format("recent damage is %.1f", recentHealthLoss);
        }
        if (isPlayerInLava()) return "player entered lava";
        if (isPlayerColliding()) return "player is colliding with terrain";
        if (!hasClearFlightCorridor(pos, destination, MANUAL_RECOVERY_CORRIDOR_DISTANCE)) {
            return "forward flight corridor is blocked or unloaded";
        }
        return null;
    }

    private void resetFlightProgress(BlockPos destination) {
        flightProgressTarget = destination;
        flightBestDistance = Integer.MAX_VALUE;
        flightNoProgressTicks = 0;
        flightNotGlidingTicks = 0;
    }

    private void clearFlightProgress() {
        resetFlightProgress(null);
        baritoneFlightRecoveryTicks = 0;
    }

    private void clearFlightRecovery() {
        flightRecoveryAttempts = 0;
        flightEscapeAttempts = 0;
        flightEscapePrerequisite = false;
    }

    private boolean startEnclosedFlightEscape(BlockPos destination, BlockPos from, int stalledTicks) {
        if (from == null || flightEscapeAttempts >= MAX_FLIGHT_ESCAPE_ATTEMPTS) return false;

        BlockPos target = findNearbyLaunchAnchor(from, destination, NEARBY_LAUNCH_RADIUS * 2);
        if (target == null) target = findTakeoffCandidate(from, destination);
        if (target == null) {
            target = extendTowardDestination(from, destination,
                    TAKEOFF_FALLBACK_EXTENSION * (flightEscapeAttempts + 1));
        }
        target = resolveMiningTarget(target, destination);
        target = stageMiningRetarget(from, target);
        if (!outsideMiningArrivalEnvelope(from, target)) return false;

        flightEscapeAttempts++;
        flightEscapePrerequisite = true;
        releaseOwner(state.owner);
        state.owner = MovementOwner.NONE;
        bridge.cancelAll();
        flight.stop();
        currentLegIndex--;
        miningRetargetAttempts = 0;
        LOGGER.warn("[Travel] flight lost glide for {}t in enclosed terrain; mining escape to {} "
                        + "(attempt {}/{})",
                stalledTicks, target.toShortString(), flightEscapeAttempts, MAX_FLIGHT_ESCAPE_ATTEMPTS);
        startMiningTraversal(target, "enclosed takeoff -> mine to verified open nether");
        return true;
    }

    private void handleUnsafeFlightFailure(BlockPos destination, String reason) {
        BlockPos pos = currentPlayerPos();
        if (destination != null && !isPlayerGliding()
                && startEnclosedFlightEscape(destination, pos, state.ticksInPhase)) {
            return;
        }
        stopUnsafeFlight(reason);
    }

    private boolean enforceTravelSafety() {
        float health = currentEffectiveHealth();
        updateTravelSafetyMonitor(health);

        String reason = null;
        boolean disconnect = false;
        if (isPlayerInLava()) {
            reason = "player entered lava";
            disconnect = true;
        } else if (health <= CRITICAL_TRAVEL_HEALTH) {
            reason = String.format("critical health %.1f/20", health);
            disconnect = true;
        } else if (recentHealthLoss >= MAX_RECENT_HEALTH_LOSS) {
            reason = String.format("recent health loss reached %.1f", recentHealthLoss);
            disconnect = health <= MIN_SAFE_TRAVEL_HEALTH || isPlayerOnFire();
        } else if (health <= MIN_SAFE_TRAVEL_HEALTH) {
            reason = String.format("health fell to %.1f/20", health);
        }
        if (reason == null) return false;

        LOGGER.error("[Travel] safety interlock: {} phase={} owner={} pos={}",
                reason, state.phase, state.owner, currentPlayerPos());
        ChatHelper.labelled("Travel", "§cSafety stop: " + reason + ". Movement released.");
        stopUnsafeFlight("safety interlock: " + reason);
        if (disconnect) elytra.disconnectForTravelSafety(reason);
        return true;
    }

    private void updateTravelSafetyMonitor(float health) {
        if (!Float.isNaN(lastEffectiveHealth) && health < lastEffectiveHealth) {
            recentHealthLoss += lastEffectiveHealth - health;
            healthLossWindowTicks = HEALTH_LOSS_WINDOW_TICKS;
        } else if (healthLossWindowTicks > 0) {
            healthLossWindowTicks--;
            if (healthLossWindowTicks == 0) recentHealthLoss = 0.0F;
        }
        lastEffectiveHealth = health;
    }

    private void resetTravelSafetyMonitor() {
        lastEffectiveHealth = Float.NaN;
        recentHealthLoss = 0.0F;
        healthLossWindowTicks = 0;
    }

    private void stopUnsafeFlight(String reason) {
        autoResumeTicks = 0;
        autoResumeAttempts = MAX_AUTO_RESUME_ATTEMPTS;
        bridge.cancelAll();
        bounce.stop();
        flight.stop();
        elytra.stop();
        state.owner = MovementOwner.NONE;
        state.abortReason = reason;
        transition(TravelPhase.ABORTED, reason);
    }

    // Return the nearest remaining flight destination.
    private BlockPos plannedFlightDestination() {
        if (state.route == null) return null;
        int start = Math.max(currentLegIndex, 0);
        for (int i = start; i < state.route.legs.size(); i++) {
            HighwayRoute.Leg leg = state.route.legs.get(i);
            if (leg instanceof HighwayRoute.FlightLeg flightLeg) return flightLeg.destination();
        }
        return null;
    }

    // Replan when confirmed legs end before the destination.
    private static final int FINAL_ARRIVAL_RADIUS = 32;

    private boolean isNearMissionDestination() {
        if (state.mission == null) return false;
        BlockPos pos = currentPlayerPos();
        if (pos == null) return false;
        return horizontalDistance(pos, state.mission.destination) <= FINAL_ARRIVAL_RADIUS;
    }

    private void advanceLeg(String reason) {
        if (state.route == null) { abort("advanceLeg with null route"); return; }
        currentLegIndex++;
        if (currentLegIndex >= state.route.legs.size()) {
            if (isNearMissionDestination()) {
                transition(TravelPhase.ARRIVED, "all legs complete: " + reason);
            } else {
                releaseOwner(state.owner);
                state.owner = MovementOwner.NONE;
                state.route = null;
                currentLegIndex = -1;
                transition(TravelPhase.PLANNING,
                        "leg chain exhausted short of destination — replanning next hop (" + reason + ")");
            }
            return;
        }
        HighwayRoute.Leg leg = state.route.legs.get(currentLegIndex);

        if (leg instanceof HighwayRoute.ApproachLeg approach) {
            startBaritoneWalk(approach.onRamp(), 2, TravelPhase.APPROACH_ONRAMP, reason);
        } else if (leg instanceof HighwayRoute.BounceLeg bounceLeg) {
            if (!isReadyToBounce(bounceLeg)) {
                if (tryStartHighwayIngressAscent(bounceLeg, reason)) {
                    currentLegIndex--;
                    return;
                }
                startBaritoneWalk(bounceLeg.highway().entry, BOUNCE_ENTRY_ALIGN_RADIUS,
                        TravelPhase.APPROACH_ONRAMP, reason + " -> align for bounce");
                currentLegIndex--;
                return;
            }
            HighwayRoute.BounceLeg activeBounce = bounceLeg;
            if (requiresLocalBounceConfirmation(activeBounce)) {
                BlockPos pos = currentPlayerPos();
                if (pos == null || !confirmBounceAtPosition(
                        pos, activeBounce, currentLegIndex, "planned bounce leg")) {
                    String stopReason = "planned highway not found near bounce entry axis="
                            + activeBounce.highway().axis;
                    LOGGER.error("[Travel] {}; refusing planner-only highway geometry", stopReason);
                    ChatHelper.labelled("Travel", "§cNo highway was verified at the planned entry. Travel stopped safely.");
                    stopUnsafeFlight(stopReason);
                    return;
                }
                activeBounce = (HighwayRoute.BounceLeg) state.route.legs.get(currentLegIndex);
                if (!isReadyToBounce(activeBounce)) {
                    startBaritoneWalk(activeBounce.highway().entry, BOUNCE_ENTRY_ALIGN_RADIUS,
                            TravelPhase.APPROACH_ONRAMP,
                            reason + " -> align to verified highway lane");
                    currentLegIndex--;
                    return;
                }
            }
            verifier.setHighway(activeBounce.highway(), activeBounce.travelDx(), activeBounce.travelDz());
            acquireOwner(MovementOwner.BOUNCE);
            bounce.start(activeBounce.highway(), activeBounce.exitColumn(),
                    activeBounce.travelDx(), activeBounce.travelDz());
            transition(TravelPhase.BOUNCING, reason + " -> bouncing to "
                    + activeBounce.exitColumn().toShortString());
        } else if (leg instanceof HighwayRoute.TurnLeg turnLeg) {
            activeTurnBranchTarget = turnLeg.branchTarget();
            activeTurnTarget = resolveTurnTarget(activeTurnBranchTarget, null);
            turnRetargetAttempts = 0;
            if (activeTurnTarget == null) {
                abort("junction handoff has no walkable branch target");
                return;
            }
            if (!activeTurnTarget.equals(activeTurnBranchTarget)) {
                LOGGER.info("[Travel] turn target normalized {} -> {}",
                        activeTurnBranchTarget.toShortString(), activeTurnTarget.toShortString());
            }
            startBaritoneWalk(activeTurnTarget, 2, TravelPhase.OFFRAMP_HANDOFF,
                    reason + " -> turn to " + activeTurnTarget.toShortString());
        } else if (leg instanceof HighwayRoute.OffRampLeg) {
            advanceLeg("offramp leg");
        } else if (leg instanceof HighwayRoute.MineLeg mine) {
            miningRetargetAttempts = 0;
            BlockPos mineTarget = resolveMiningTarget(mine.freeNetherTarget(), plannedFlightDestination());
            startMiningTraversal(mineTarget, reason);
        } else if (leg instanceof HighwayRoute.FlightLeg flightLeg) {
            if (flightEscapePrerequisite) {
                flightEscapePrerequisite = false;
                flightRecoveryAttempts = 0;
            } else {
                clearFlightRecovery();
            }
            BlockPos pos = currentPlayerPos();
            if (pos != null && isMostlyVerticalHop(pos, flightLeg.destination())) {
                // Walk steep vertical hops instead of flying blindly.
                startBaritoneWalk(flightLeg.destination(), 2, TravelPhase.APPROACH_ONRAMP,
                        reason + " -> mostly-vertical hop, walking instead of flying");
                return;
            }
            if (state.mission != null && state.mission.useElytra) {
                if (pos != null && !isPlayerGliding()
                        && !isStrongLaunchAnchor(pos, flightLeg.destination())) {
                    if (startEnclosedFlightEscape(flightLeg.destination(), pos, 0)) return;
                    stopUnsafeFlight("flight leg has no verified open-nether launch anchor");
                    return;
                }
                acquireOwner(MovementOwner.FLIGHT);
                flight.start(flightLeg.destination());
                startBaritoneElytraFlight(flightLeg.destination());
                transition(TravelPhase.LAUNCH, reason + " -> launching to " + flightLeg.destination().toShortString());
            } else {
                LOGGER.info("[Travel] FlightLeg skipped (useElytra=false)");
                transition(TravelPhase.ARRIVED, "flight leg skipped (useElytra disabled)");
            }
        }
    }

    private void startBaritoneWalk(BlockPos target, int radius, TravelPhase phase, String reason) {
        acquireOwner(MovementOwner.BARITONE);
        bridge.walkNear(target, radius);
        transition(phase, reason + " -> walk to " + target.toShortString());
    }

    private BlockPos resolveTurnTarget(BlockPos requested, BlockPos excluded) {
        HighwayRoute.BounceLeg nextBounce = nextBounceLeg();
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int yOffset = 1; yOffset <= 3; yOffset++) {
                        BlockPos candidate = new BlockPos(
                                requested.getX() + dx,
                                requested.getY() + yOffset,
                                requested.getZ() + dz);
                        if (candidate.equals(excluded) || !isWalkableFeetPosition(candidate)) continue;
                        if (nextBounce != null && !isAlignedWithBounceLane(candidate, nextBounce)) continue;

                        int score = 200
                                - (Math.abs(dx) + Math.abs(dz)) * 12
                                - Math.abs(yOffset - 1) * 8;
                        if (nextBounce != null) {
                            int laneDx = candidate.getX() - nextBounce.highway().entry.getX();
                            int laneDz = candidate.getZ() - nextBounce.highway().entry.getZ();
                            score -= Math.abs(laneDx * nextBounce.highway().axis.perpDx()
                                    + laneDz * nextBounce.highway().axis.perpDz()) * 4;
                        }
                        if (score > bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    private HighwayRoute.BounceLeg nextBounceLeg() {
        int index = nextBounceLegIndex();
        if (index < 0) return null;
        return (HighwayRoute.BounceLeg) state.route.legs.get(index);
    }

    private int nextBounceLegIndex() {
        if (state.route == null) return -1;
        for (int i = currentLegIndex + 1; i < state.route.legs.size(); i++) {
            HighwayRoute.Leg leg = state.route.legs.get(i);
            if (leg instanceof HighwayRoute.BounceLeg) return i;
            if (!(leg instanceof HighwayRoute.TurnLeg)) break;
        }
        return -1;
    }

    private static boolean isAlignedWithBounceLane(BlockPos candidate, HighwayRoute.BounceLeg bounceLeg) {
        HighwayCandidate highway = bounceLeg.highway();
        int dx = candidate.getX() - highway.entry.getX();
        int dz = candidate.getZ() - highway.entry.getZ();
        int perpendicular = dx * highway.axis.perpDx() + dz * highway.axis.perpDz();
        int limit = highway.axis.diagonal ? 4 : 3;
        return Math.abs(perpendicular) <= limit;
    }

    private static BlockPos withY(BlockPos pos, int y) {
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static boolean isWalkableFeetPosition(BlockPos feet) {
        BlockPos floor = new BlockPos(feet.getX(), feet.getY() - 1, feet.getZ());
        BlockPos head = new BlockPos(feet.getX(), feet.getY() + 1, feet.getZ());
        return isChunkLoaded(floor)
                && hasCollision(floor)
                && isAirLike(feet)
                && isAirLike(head);
    }

    private void clearTurnHandoff() {
        activeTurnBranchTarget = null;
        activeTurnTarget = null;
        turnRetargetAttempts = 0;
    }

    private void startMiningTraversal(BlockPos target, String reason) {
        activeMineTarget = target;
        BlockPos pos = currentPlayerPos();
        if (target == null) {
            abort("mine traversal missing target");
            return;
        }
        if (pos != null && target.getY() < pos.getY() - MINING_DESCENT_THRESHOLD) {
            int horiz = horizontalDistance(pos, target);
            if (horiz > 4) {
                BlockPos aboveTarget = new BlockPos(target.getX(), pos.getY(), target.getZ());
                miningTraversal = MiningTraversal.COLUMN_APPROACH;
                startBaritoneWalk(aboveTarget, MINING_TARGET_RADIUS, TravelPhase.MINING_TO_FREENETHER,
                        reason + " -> align above descent");
            } else {
                startBaritoneDescent(target.getY(), reason);
            }
            return;
        }
        miningTraversal = MiningTraversal.DIRECT;
        startBaritoneWalk(target, MINING_TARGET_RADIUS, TravelPhase.MINING_TO_FREENETHER, reason);
    }

    private void startBaritoneDescent(int targetY, String reason) {
        miningTraversal = MiningTraversal.DESCEND_BARITONE;
        acquireOwner(MovementOwner.BARITONE);
        bridge.walkToYLevelWithPlacement(targetY);
        transition(TravelPhase.MINING_TO_FREENETHER, reason + " -> descend to Y " + targetY);
    }

    private boolean tryStartHighwayIngressAscent(HighwayRoute.BounceLeg bounceLeg, String reason) {
        BlockPos pos = currentPlayerPos();
        if (pos == null) return false;
        HighwayCandidate highway = bounceLeg.highway();
        if (highway == null) return false;
        if (highway.floorY <= Integer.MIN_VALUE || pos.getY() >= highway.floorY - 2) return false;

        acquireOwner(MovementOwner.BARITONE);
        bridge.walkToYLevelWithPlacement(highway.floorY);
        transition(TravelPhase.APPROACH_ONRAMP,
                reason + " -> climb to highway Y " + highway.floorY);
        return true;
    }

    private boolean tryManualMiningDescent(String reason) {
        if (activeMineTarget == null) return false;
        BlockPos pos = currentPlayerPos();
        if (pos == null || activeMineTarget.getY() >= pos.getY()) return false;
        miningTraversal = MiningTraversal.DESCEND_MANUAL;
        releaseOwner(state.owner);
        acquireOwner(MovementOwner.BARITONE);
        bridge.startMiningDescent(activeMineTarget.getY());
        state.ticksInPhase = 0;
        state.lastTransitionReason = reason + " -> manual descend to Y " + activeMineTarget.getY();
        LOGGER.warn("[Travel] {}", state.lastTransitionReason);
        return true;
    }

    private boolean advanceMiningTraversal(BlockPos pos) {
        if (activeMineTarget == null) return false;
        if (miningTraversal == MiningTraversal.COLUMN_APPROACH) {
            startBaritoneDescent(activeMineTarget.getY(), "mining aligned above descent");
            return true;
        }
        if (miningTraversal == MiningTraversal.DESCEND_BARITONE || miningTraversal == MiningTraversal.DESCEND_MANUAL) {
            if (pos != null && horizontalDistance(pos, activeMineTarget) > 2) {
                miningTraversal = MiningTraversal.FINAL_APPROACH;
                startBaritoneWalk(activeMineTarget, MINING_TARGET_RADIUS, TravelPhase.MINING_TO_FREENETHER,
                        "mining descent complete");
                return true;
            }
            miningTraversal = MiningTraversal.FINAL_APPROACH;
        }
        return false;
    }

    private static final int WALL_BYPASS_DISTANCE      = 12;
    private static final int WALL_BYPASS_CLEARANCE     =  8;
    private static final int BYPASS_TARGET_RADIUS      =  1;
    private static final int STALL_BYPASS_DISTANCE     = 64;
    private static final int KNOCKBACK_PERP_THRESHOLD  =  5; // off-axis blocks before recovery
    private static final int KNOCKBACK_RECOVERY_LEAD   =  8; // stay moving toward the exit after re-entry

    private void triggerWallBypass() {
        int distance = Math.max(WALL_BYPASS_DISTANCE,
                bounce.wallDistance() + WALL_BYPASS_CLEARANCE);
        triggerForwardBypass("wall bypass", distance);
    }

    private void triggerForwardBypass(String reason, int distance) {
        BlockPos pos = currentPlayerPos();
        HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
        if (pos == null || bounceLeg == null) {
            abort("no player pos for " + reason);
            return;
        }
        rememberCurrentBounceExit();
        HighwayCandidate highway = bounceLeg.highway();
        int travelDx = bounceLeg.travelDx();
        int travelDz = bounceLeg.travelDz();
        int directionSq = travelDx * travelDx + travelDz * travelDz;
        int along = directionSq == 0 ? 0 : (int) Math.round(
                ((pos.getX() - highway.entry.getX()) * (double) travelDx
                        + (pos.getZ() - highway.entry.getZ()) * (double) travelDz)
                        / directionSq);
        BlockPos goal = new BlockPos(
                highway.entry.getX() + travelDx * (along + distance),
                highway.floorY + 1,
                highway.entry.getZ() + travelDz * (along + distance));
        releaseOwner(state.owner);
        acquireOwner(MovementOwner.BARITONE);
        beginDetourTracking(pos, goal, BYPASS_TARGET_RADIUS);
        bridge.walkNear(goal, BYPASS_TARGET_RADIUS);
        transition(TravelPhase.DETOURING, reason + ": goal=" + goal.toShortString());
    }

    // Detect displacement beyond the highway lane.
    private boolean isPlayerKnockedOffHighway() {
        HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
        if (bounceLeg == null) return false;
        BlockPos pos = currentPlayerPos();
        if (pos == null) return false;
        HighwayCandidate hw = bounceLeg.highway();
        if (hw.entry == null) return false;
        int perpDx = hw.axis.perpDx();
        int perpDz = hw.axis.perpDz();
        int perpSq = perpDx * perpDx + perpDz * perpDz; // 1 cardinal, 2 diagonal
        // Compare squared offsets without division.
        int dot = (pos.getX() - hw.entry.getX()) * perpDx
                + (pos.getZ() - hw.entry.getZ()) * perpDz;
        return Math.abs(dot) > KNOCKBACK_PERP_THRESHOLD * perpSq;
    }

    // Walk back to the active highway axis.
    private void triggerKnockbackRecovery() {
        BlockPos pos = currentPlayerPos();
        HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
        if (pos == null || bounceLeg == null) { abort("no player pos for knockback recovery"); return; }
        HighwayCandidate hw = bounceLeg.highway();
        rememberCurrentBounceExit();
        IntegrityReport rep = verifier.lastReport();
        if (rep.status() == IntegrityReport.Status.GRIEFED
                && rep.confidence() >= DetourPlanner.MIN_CONFIDENCE) {
            List<BlockPos> waypoints = DetourPlanner.plan(hw, rep, pos,
                    bounceLeg.travelDx(), bounceLeg.travelDz());
            if (!waypoints.isEmpty()) {
                LOGGER.warn("[Travel] knocked off highway near grief — recovering via {} detour waypoints", waypoints.size());
                releaseOwner(state.owner);
                acquireOwner(MovementOwner.BARITONE);
                beginDetourTracking(pos, waypoints.get(0), DETOUR_WAYPOINT_RADIUS);
                bridge.walkToWaypoints(waypoints, DETOUR_WAYPOINT_RADIUS);
                transition(TravelPhase.DETOURING,
                        "knockback recovery detour: griefRange=["
                                + rep.griefStartOffset() + "," + rep.griefEndOffset() + "]");
                return;
            }
        }
        // Project the player onto the highway axis.
        int ex = hw.entry.getX(), ez = hw.entry.getZ();
        int dx = bounceLeg.travelDx(), dz = bounceLeg.travelDz();
        int dSq = dx * dx + dz * dz;
        int t   = ((pos.getX() - ex) * dx + (pos.getZ() - ez) * dz) / dSq;
        int recoveryT = Math.max(0, t) + KNOCKBACK_RECOVERY_LEAD;
        BlockPos onHighway = new BlockPos(ex + dx * recoveryT, hw.floorY + 1, ez + dz * recoveryT);
        LOGGER.warn("[Travel] knocked off highway — recovering forward to {}", onHighway.toShortString());
        releaseOwner(state.owner);
        acquireOwner(MovementOwner.BARITONE);
        beginDetourTracking(pos, onHighway, 2);
        bridge.walkNear(onHighway, 2);
        transition(TravelPhase.DETOURING, "knockback recovery to " + onHighway.toShortString());
    }

    private void beginDetourTracking(BlockPos start, BlockPos firstTarget, int radius) {
        int anchorY = Math.min(start.getY(), firstTarget.getY());
        detourMinSafeY = anchorY - DETOUR_FALL_TOLERANCE;
        detourCorrectionEpisodeBaseline = SetbackMonitor.get().totalCorrectionEpisodes();
        LOGGER.info("[Travel] detour handoff start={} first={} radius={} minSafeY={}",
                start.toShortString(), firstTarget.toShortString(), radius, detourMinSafeY);
    }

    private void abort(String reason) {
        state.abortReason = reason;
        transition(TravelPhase.ABORTED, reason);
    }

    private void haltAfterPlayerDeath() {
        autoResumeTicks = 0;
        autoResumeAttempts = MAX_AUTO_RESUME_ATTEMPTS;
        clearPendingResupply();
        clearFlightProgress();
        clearFlightRecovery();
        elytra.stop();
        bridge.cancelAll();
        bounce.stop();
        flight.stop();
        state.owner = MovementOwner.NONE;
        state.abortReason = "player died";
        transition(TravelPhase.ABORTED, "player died; automation halted");
    }

    private void acquireOwner(MovementOwner next) {
        if (state.owner == next) return;
        releaseOwner(state.owner);
        state.owner = next;
    }

    private void releaseOwner(MovementOwner cur) {
        switch (cur) {
            case BARITONE        -> bridge.cancelAll();
            case BOUNCE          -> bounce.stop();
            case FLIGHT          -> flight.stop();
            case NONE            -> { /* nothing */ }
        }
    }

    private void transition(TravelPhase next, String reason) {
        TravelPhase from = state.phase;
        if (from == next) return;
        if (next == TravelPhase.LAUNCH) clearFlightProgress();
        if (from == TravelPhase.DETOURING && next != TravelPhase.DETOURING) {
            detourMinSafeY = Integer.MIN_VALUE;
        }
        state.phase = next;
        state.ticksInPhase = 0;
        state.lastTransitionReason = reason;
        TravelLog.get().recordTransition(missionId(), state.missionTicks, from, next, reason);
        LOGGER.info("[Travel] {} -> {} ({})", from, next, reason);
        publishTravelEvent(from, next, reason);
        if (next.isTerminal()) {
            releaseOwner(state.owner);
            state.owner = MovementOwner.NONE;
        }
    }

    private long missionId() { return state.mission != null ? state.mission.id : 0L; }

    private void pauseForDimensionChange() {
        if (state.phase == TravelPhase.IDLE || state.phase == TravelPhase.PAUSED) return;
        state.pausedFromPhase = state.phase;
        if (state.phase == TravelPhase.ELYTRA_RESUPPLY) elytra.pause();
        releaseOwner(state.owner);
        state.owner = MovementOwner.NONE;
        TravelPhase from = state.phase;
        state.phase = TravelPhase.PAUSED;
        state.ticksInPhase = 0;
        state.lastTransitionReason = "auto pause: left nether during travel";
        TravelLog.get().recordTransition(missionId(), state.missionTicks, from, TravelPhase.PAUSED, state.lastTransitionReason);
        publishTravelEvent(from, TravelPhase.PAUSED, state.lastTransitionReason);
        ChatHelper.labelled("Travel", "§eTravel paused — left the Nether. Return to the Nether and use §f/moar travel resume§e.");
    }

    private void publishTravelEvent(TravelPhase from, TravelPhase to, String reason) {
        if (to == TravelPhase.IDLE || from.isTerminal()) return;

        MoarProperties properties = MoarMod.getProperties();
        WebhookEvent.Type type = to == TravelPhase.ARRIVED
                ? WebhookEvent.Type.DESTINATION_REACHED
                : to == TravelPhase.ABORTED
                        ? WebhookEvent.Type.TRAVEL_ABORTED
                        : WebhookEvent.Type.NAVIGATION_CHANGED;
        if (properties == null
                || !properties.isWebhookEnabled()
                || !properties.hasWebhookUrl()
                || !properties.isWebhookEventEnabled(type)) {
            return;
        }

        boolean detailed = properties.isWebhookIncludeCoordinates();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("From", from.name());
        fields.put("To", to.name());
        if (detailed) {
            if (reason != null && !reason.isBlank()) fields.put("Reason", reason);
            BlockPos position = currentPlayerPos();
            if (position != null) fields.put("Position", position.toShortString());
            if (state.mission != null) {
                fields.put("Destination", state.mission.destination.toShortString());
                fields.put("Mission", String.valueOf(state.mission.id));
            }
        }

        if (to == TravelPhase.ARRIVED) {
            WebhookService.get().publish(WebhookEvent.of(
                    type,
                    "Destination reached",
                    "MOAR completed the active travel mission.",
                    WebhookEvent.Severity.SUCCESS,
                    fields));
            return;
        }
        if (to == TravelPhase.ABORTED) {
            WebhookService.get().publish(WebhookEvent.of(
                    type,
                    "Travel aborted",
                    detailed && reason != null ? reason : "MOAR stopped before reaching the destination.",
                    WebhookEvent.Severity.ERROR,
                    fields));
            return;
        }
        WebhookService.get().publish(WebhookEvent.of(
                type,
                "Navigation changed",
                "Travel moved from " + from.name() + " to " + to.name() + ".",
                to == TravelPhase.PAUSED
                        ? WebhookEvent.Severity.WARNING
                        : WebhookEvent.Severity.INFO,
                fields));
    }

    private HighwayRoute.BounceLeg currentBounceLeg() {
        if (state.route == null || currentLegIndex < 0 || currentLegIndex >= state.route.legs.size()) return null;
        HighwayRoute.Leg leg = state.route.legs.get(currentLegIndex);
        return leg instanceof HighwayRoute.BounceLeg bounceLeg ? bounceLeg : null;
    }

    private HighwayRoute.FlightLeg currentFlightLeg() {
        if (state.route == null || currentLegIndex < 0 || currentLegIndex >= state.route.legs.size()) return null;
        HighwayRoute.Leg leg = state.route.legs.get(currentLegIndex);
        return leg instanceof HighwayRoute.FlightLeg flightLeg ? flightLeg : null;
    }

    private boolean hasAnotherMineLegAhead() {
        if (state.route == null) return false;
        for (int i = currentLegIndex + 1; i < state.route.legs.size(); i++) {
            HighwayRoute.Leg leg = state.route.legs.get(i);
            if (leg instanceof HighwayRoute.MineLeg) return true;
            if (!(leg instanceof HighwayRoute.OffRampLeg)) return false;
        }
        return false;
    }

    private void rememberCurrentBounceExit() {
        HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
        if (bounceLeg != null) detourResumeExit = bounceLeg.exitColumn();
    }

    private void rememberResupplyResumeContext() {
        resupplyResumePhase = null;
        resupplyResumeTarget = null;

        if (state.phase == TravelPhase.MINING_TO_FREENETHER && activeMineTarget != null) {
            resupplyResumePhase = TravelPhase.MINING_TO_FREENETHER;
            resupplyResumeTarget = activeMineTarget;
            return;
        }

        BlockPos flightTarget = currentFlightDestination();
        if (flightTarget == null && state.mission != null) flightTarget = state.mission.destination;
        if ((state.phase == TravelPhase.LAUNCH
                || state.phase == TravelPhase.ELYTRA_CRUISE
                || state.phase == TravelPhase.ELYTRA_FALLBACK)
                && flightTarget != null) {
            resupplyResumePhase = TravelPhase.LAUNCH;
            resupplyResumeTarget = flightTarget;
            return;
        }

        if (state.phase == TravelPhase.BOUNCING && plannedFlightDestination() != null) {
            resupplyResumePhase = TravelPhase.BOUNCING;
        }
    }

    private void clearResupplyResumeContext() {
        resupplyResumePhase = null;
        resupplyResumeTarget = null;
    }

    private void clearPendingResupply() {
        pendingResupply = ResupplyRequest.NONE;
        resupplyLandingTarget = null;
        resupplyGroundedTicks = 0;
        resupplyLocalSettleTicks = 0;
        resupplyBaritoneQuietTicks = 0;
        resupplyUsedBaritone = false;
    }

    private void clearConnectionCheckpoint() {
        connectionPaused = false;
        connectionResumePending = false;
        connectionResumeTarget = null;
        resetTravelSafetyMonitor();
    }

    private BlockPos checkpointTargetFor(TravelPhase phase) {
        if (phase == TravelPhase.LAUNCH || phase == TravelPhase.ELYTRA_CRUISE
                || phase == TravelPhase.ELYTRA_FALLBACK) {
            BlockPos target = currentFlightDestination();
            return target != null ? target : state.mission.destination;
        }
        if (phase == TravelPhase.MINING_TO_FREENETHER && activeMineTarget != null) {
            return activeMineTarget;
        }
        if (phase == TravelPhase.LANDING_FOR_RESUPPLY) return resupplyLandingTarget;
        return bridge.currentTarget();
    }

    private void resumeConnectionCheckpoint(String reason) {
        if (!connectionPaused || state.mission == null) return;
        TravelPhase resumePhase = state.pausedFromPhase != null
                ? state.pausedFromPhase : TravelPhase.PLANNING;
        BlockPos target = connectionResumeTarget;
        connectionPaused = false;
        connectionResumePending = false;
        connectionResumeTarget = null;

        if (resumePhase == TravelPhase.ELYTRA_RESUPPLY) {
            elytra.resumeFromCheckpoint();
            transition(TravelPhase.ELYTRA_RESUPPLY, reason);
            return;
        }
        if (resumePhase == TravelPhase.LANDING_FOR_RESUPPLY) {
            resupplyLandingTarget = findSafeResupplyLanding();
            transition(TravelPhase.LANDING_FOR_RESUPPLY, reason + "; reacquiring safe landing");
            if (resupplyLandingTarget != null) {
                acquireOwner(MovementOwner.BARITONE);
                bridge.startElytraFlight(resupplyLandingTarget);
            }
            return;
        }
        if (resumePhase == TravelPhase.LAUNCH || resumePhase == TravelPhase.ELYTRA_CRUISE
                || resumePhase == TravelPhase.ELYTRA_FALLBACK) {
            bridge.cancelAll();
            flight.stop();
            state.owner = MovementOwner.NONE;
            state.route = null;
            currentLegIndex = -1;
            transition(TravelPhase.PLANNING, reason + "; replanning flight from rejoin position");
            return;
        }
        if (resumePhase == TravelPhase.BOUNCING) {
            HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
            if (bounceLeg != null && isReadyToBounce(bounceLeg)) {
                verifier.setHighway(bounceLeg.highway(), bounceLeg.travelDx(), bounceLeg.travelDz());
                acquireOwner(MovementOwner.BOUNCE);
                bounce.start(bounceLeg.highway(), bounceLeg.exitColumn(),
                        bounceLeg.travelDx(), bounceLeg.travelDz());
                transition(TravelPhase.BOUNCING, reason + "; resuming active highway leg");
            } else if (bounceLeg != null) {
                currentLegIndex--;
                startBaritoneWalk(bounceLeg.highway().entry, BOUNCE_ENTRY_ALIGN_RADIUS,
                        TravelPhase.APPROACH_ONRAMP, reason + "; realigning active highway leg");
            } else {
                transition(TravelPhase.PLANNING, reason + "; bounce checkpoint unavailable");
            }
            return;
        }
        if (resumePhase == TravelPhase.MINING_TO_FREENETHER && target != null) {
            startMiningTraversal(target, reason + "; resuming mining leg");
            return;
        }
        if ((resumePhase == TravelPhase.APPROACH_ONRAMP
                || resumePhase == TravelPhase.OFFRAMP_HANDOFF) && target != null) {
            startBaritoneWalk(target, 2, resumePhase, reason + "; resuming ground leg");
            return;
        }
        if (resumePhase == TravelPhase.DETOURING && target != null) {
            BlockPos pos = currentPlayerPos();
            if (pos != null) beginDetourTracking(pos, target, DETOUR_WAYPOINT_RADIUS);
            acquireOwner(MovementOwner.BARITONE);
            bridge.walkNear(target, DETOUR_WAYPOINT_RADIUS);
            transition(TravelPhase.DETOURING, reason + "; resuming detour");
            return;
        }
        if (resumePhase == TravelPhase.SETTLE) {
            transition(TravelPhase.SETTLE, reason + "; resuming settle");
            return;
        }
        transition(TravelPhase.PLANNING, reason + "; replanning from checkpoint");
    }

    private boolean resumeAfterResupply(String reason) {
        if (resumeSavedProgress(reason)) return true;
        if (detourResumeExit != null && resumeCurrentBounce()) {
            detourResumeExit = null;
            clearResupplyResumeContext();
            transition(TravelPhase.BOUNCING, reason + ", resuming bounce");
            return true;
        }
        clearResupplyResumeContext();
        return false;
    }

    private boolean resumeSavedProgress(String reason) {
        if (resupplyResumePhase == null) return false;
        if (resupplyResumePhase == TravelPhase.MINING_TO_FREENETHER
                && resupplyResumeTarget != null
                && state.route != null) {
            BlockPos resumeTarget = resupplyResumeTarget;
            clearResupplyResumeContext();
            startMiningTraversal(resumeTarget, reason);
            return true;
        }
        if (resupplyResumePhase == TravelPhase.LAUNCH && resupplyResumeTarget != null) {
            BlockPos target = resupplyResumeTarget;
            clearResupplyResumeContext();
            acquireOwner(MovementOwner.FLIGHT);
            flight.start(target);
            startBaritoneElytraFlight(target);
            transition(TravelPhase.LAUNCH, reason + " -> resuming flight to " + target.toShortString());
            return true;
        }
        if (resupplyResumePhase == TravelPhase.BOUNCING && state.route != null
                && detourResumeExit != null && resumeCurrentBounce()) {
            detourResumeExit = null;
            clearResupplyResumeContext();
            transition(TravelPhase.BOUNCING, reason + " -> resuming bounce");
            return true;
        }
        return false;
    }

    private boolean resumeCurrentBounce() {
        HighwayRoute.BounceLeg bounceLeg = currentBounceLeg();
        if (bounceLeg == null || detourResumeExit == null) return false;
        verifier.setHighway(bounceLeg.highway(), bounceLeg.travelDx(), bounceLeg.travelDz());
        acquireOwner(MovementOwner.BOUNCE);
        bounce.start(bounceLeg.highway(), detourResumeExit,
                bounceLeg.travelDx(), bounceLeg.travelDz());
        return true;
    }

    private boolean retargetMiningTakeoff(BlockPos from, BlockPos destination, String reason) {
        if (miningRetargetAttempts >= MAX_MINING_RETARGET_ATTEMPTS) return false;
        BlockPos next = findNearbyLaunchAnchor(from, destination, NEARBY_LAUNCH_RADIUS * 2);
        if (next == null) {
            next = findTakeoffCandidate(from, destination);
        }
        if (next == null) {
            next = extendTowardDestination(from, destination,
                    TAKEOFF_FALLBACK_EXTENSION * (miningRetargetAttempts + 1));
        }
        if (next == null) return false;
        next = resolveMiningTarget(next, destination);
        next = stageMiningRetarget(from, next);
        BlockPos currentTarget = bridge.currentTarget();
        if (!outsideMiningArrivalEnvelope(from, next)
                || (currentTarget != null && sameHorizontalTarget(currentTarget, next))) {
            BlockPos extension = extendTowardDestination(from, destination,
                    TAKEOFF_FALLBACK_EXTENSION * (miningRetargetAttempts + 1));
            next = stageMiningRetarget(from, extension);
        }
        if (!outsideMiningArrivalEnvelope(from, next)) return false;
        if (currentTarget != null && sameHorizontalTarget(currentTarget, next)) return false;

        miningRetargetAttempts++;
        LOGGER.warn("[Travel] {} — retargeting mine leg to {} (attempt {}/{})",
                reason, next.toShortString(), miningRetargetAttempts, MAX_MINING_RETARGET_ATTEMPTS);
        startMiningTraversal(next, reason);
        return true;
    }

    private static boolean sameHorizontalTarget(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ() && Math.abs(a.getY() - b.getY()) <= 2;
    }

    private static boolean outsideMiningArrivalEnvelope(BlockPos from, BlockPos target) {
        if (from == null || target == null) return false;
        long dx = target.getX() - from.getX();
        long dz = target.getZ() - from.getZ();
        long arrivalRadius = MINING_TARGET_RADIUS + 1L;
        return dx * dx + dz * dz > arrivalRadius * arrivalRadius
                || Math.abs(target.getY() - from.getY()) > MINING_ARRIVAL_Y_TOLERANCE;
    }

    private static BlockPos stageMiningRetarget(BlockPos from, BlockPos target) {
        if (from == null || target == null) return target;

        int dx = target.getX() - from.getX();
        int dy = target.getY() - from.getY();
        int dz = target.getZ() - from.getZ();
        int horizontal = Math.max(Math.abs(dx), Math.abs(dz));
        if (horizontal <= MINING_RETARGET_STEP && Math.abs(dy) <= MINING_RETARGET_VERTICAL_STEP) {
            return target;
        }

        double scale = 1.0;
        if (horizontal > MINING_RETARGET_STEP) {
            scale = Math.min(scale, MINING_RETARGET_STEP / (double) horizontal);
        }
        if (Math.abs(dy) > MINING_RETARGET_VERTICAL_STEP) {
            scale = Math.min(scale, MINING_RETARGET_VERTICAL_STEP / (double) Math.abs(dy));
        }
        if (scale >= 1.0) return target;

        int x = from.getX() + (int) Math.round(dx * scale);
        int y = from.getY() + (int) Math.round(dy * scale);
        int z = from.getZ() + (int) Math.round(dz * scale);
        return new BlockPos(x, y, z);
    }

    private static BlockPos findNearbyLaunchAnchor(BlockPos from, BlockPos destination, int radius) {
        int dirX = Integer.compare(destination.getX(), from.getX());
        int dirZ = Integer.compare(destination.getZ(), from.getZ());
        if (dirX == 0 && dirZ == 0) return null;

        List<ScoredLaunchAnchor> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) > radius) continue;
                for (int dy = -NEARBY_LAUNCH_DOWN; dy <= NEARBY_LAUNCH_UP; dy++) {
                    BlockPos candidate = new BlockPos(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                    if (!outsideMiningArrivalEnvelope(from, candidate)) continue;
                    int rawScore = scoreTakeoffCandidate(candidate, dirX, dirZ);
                    if (rawScore == Integer.MIN_VALUE) continue;

                    int adjustedScore = rawScore
                            - Math.abs(dy) * 6
                            - horizontalDistance(from, candidate) * 2;
                    addLaunchCandidate(candidates, candidate, rawScore, adjustedScore);
                }
            }
        }
        return firstStrongLaunchAnchor(candidates, dirX, dirZ);
    }

    private static boolean isStrongLaunchAnchor(BlockPos pos, BlockPos destination) {
        int dirX = Integer.compare(destination.getX(), pos.getX());
        int dirZ = Integer.compare(destination.getZ(), pos.getZ());
        if (dirX == 0 && dirZ == 0) return false;
        int score = scoreTakeoffCandidate(pos, dirX, dirZ);
        if (score < 56) return false;
        return hasLaunchBubble(pos, 4, 4)
                && hasClearLaunchCorridor(pos, dirX, dirZ, LAUNCH_CORRIDOR_DISTANCE);
    }

    private static boolean hasLaunchBubble(BlockPos pos, int radius, int height) {
        int solid = 0;
        for (int y = 0; y <= height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && z == 0 && y <= 2) continue;
                    BlockPos sample = new BlockPos(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (!isChunkLoaded(sample)) return false;
                    if (!isAirLike(sample)) solid++;
                }
            }
        }
        return solid <= 12;
    }

    private static boolean hasClearFlightCorridor(BlockPos from, BlockPos destination, int distance) {
        int dirX = Integer.compare(destination.getX(), from.getX());
        int dirZ = Integer.compare(destination.getZ(), from.getZ());
        return hasClearLaunchCorridor(from, dirX, dirZ, distance);
    }

    private static boolean hasClearLaunchCorridor(BlockPos from, int dirX, int dirZ, int distance) {
        if (dirX == 0 && dirZ == 0) return false;
        int perpX = dirZ;
        int perpZ = -dirX;
        for (int step = 1; step <= distance; step++) {
            for (int side = -1; side <= 1; side++) {
                for (int y = 0; y <= 3; y++) {
                    BlockPos sample = new BlockPos(
                            from.getX() + dirX * step + perpX * side,
                            from.getY() + y,
                            from.getZ() + dirZ * step + perpZ * side);
                    if (!isChunkLoaded(sample) || !isAirLike(sample)) return false;
                }
            }
        }
        return true;
    }

    private BlockPos resolveMiningTarget(BlockPos requested, BlockPos destination) {
        if (requested == null) return null;
        if (hasLaunchFooting(requested)) return requested;

        BlockPos playerPos = currentPlayerPos();
        int dirX;
        int dirZ;
        if (destination != null) {
            dirX = Integer.compare(destination.getX(), requested.getX());
            dirZ = Integer.compare(destination.getZ(), requested.getZ());
        } else if (playerPos != null) {
            dirX = Integer.compare(requested.getX(), playerPos.getX());
            dirZ = Integer.compare(requested.getZ(), playerPos.getZ());
        } else {
            dirX = 0;
            dirZ = 0;
        }
        if (dirX == 0 && dirZ == 0 && playerPos != null) {
            dirX = Integer.compare(requested.getX(), playerPos.getX());
            dirZ = Integer.compare(requested.getZ(), playerPos.getZ());
        }

        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        int perpX = dirZ;
        int perpZ = -dirX;

        for (int back = 0; back <= 64; back += 2) {
            int baseX = requested.getX() - dirX * back;
            int baseZ = requested.getZ() - dirZ * back;
            for (int side = -8; side <= 8; side += 2) {
                for (int dy = -TAKEOFF_SEARCH_DOWN; dy <= TAKEOFF_SEARCH_UP; dy++) {
                    BlockPos candidate = new BlockPos(
                            baseX + perpX * side,
                            requested.getY() + dy,
                            baseZ + perpZ * side);
                    if (!hasLaunchFooting(candidate)) continue;

                    int score = 0;
                    if (dirX != 0 || dirZ != 0) {
                        score = scoreTakeoffCandidate(candidate, dirX, dirZ);
                    }
                    if (score == Integer.MIN_VALUE) continue;

                    int distance = horizontalDistance(candidate, requested);
                    if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                        best = candidate;
                        bestScore = score;
                        bestDistance = distance;
                    }
                }
            }
            if (best != null && bestScore >= 16) break;
        }

        if (best != null) {
            LOGGER.info("[Travel] normalized mining target {} -> {}", requested.toShortString(), best.toShortString());
            return best;
        }
        return requested;
    }

    private boolean isReadyToBounce(HighwayRoute.BounceLeg bounceLeg) {
        BlockPos pos = currentPlayerPos();
        if (pos == null) return false;
        HighwayCandidate highway = bounceLeg.highway();
        if (highway == null || highway.entry == null) return false;
        if (highway.floorY > Integer.MIN_VALUE && Math.abs(pos.getY() - highway.floorY) > 2) {
            return false;
        }
        int dx = pos.getX() - highway.entry.getX();
        int dz = pos.getZ() - highway.entry.getZ();
        int perpDot = dx * highway.axis.perpDx() + dz * highway.axis.perpDz();
        int alongDot = dx * bounceLeg.travelDx() + dz * bounceLeg.travelDz();
        int perpLimit = highway.axis.diagonal ? 4 : 3;
        if (Math.abs(perpDot) > perpLimit) return false;
        return alongDot >= -BOUNCE_ENTRY_ALIGN_RADIUS;
    }

    private static BlockPos extendTowardDestination(BlockPos from, BlockPos destination, int distance) {
        int dx = destination.getX() - from.getX();
        int dz = destination.getZ() - from.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.0001) return null;
        int tx = from.getX() + (int) Math.round(dx / length * distance);
        int tz = from.getZ() + (int) Math.round(dz / length * distance);
        return new BlockPos(tx, from.getY(), tz);
    }

    private static BlockPos findTakeoffCandidate(BlockPos from, BlockPos destination) {
        int dx = Integer.compare(destination.getX(), from.getX());
        int dz = Integer.compare(destination.getZ(), from.getZ());
        if (dx == 0 && dz == 0) return null;
        int perpX = dz;
        int perpZ = -dx;
        int maxAhead = computeLoadedTakeoffSearchAhead(from, dx, dz);
        if (maxAhead <= 0) return null;

        List<ScoredLaunchAnchor> candidates = new ArrayList<>();
        for (int ahead = TAKEOFF_SEARCH_STEP; ahead <= maxAhead; ahead += TAKEOFF_SEARCH_STEP) {
            int baseX = from.getX() + dx * ahead;
            int baseZ = from.getZ() + dz * ahead;
            for (int side = -TAKEOFF_SEARCH_LATERAL; side <= TAKEOFF_SEARCH_LATERAL; side += 2) {
                for (int dy = -TAKEOFF_SEARCH_DOWN; dy <= TAKEOFF_SEARCH_UP; dy++) {
                    BlockPos candidate = new BlockPos(
                            baseX + perpX * side,
                            from.getY() + dy,
                            baseZ + perpZ * side);
                    int score = scoreTakeoffCandidate(candidate, dx, dz);
                    addLaunchCandidate(candidates, candidate, score, score + ahead / 4);
                }
            }
        }
        return firstStrongLaunchAnchor(candidates, dx, dz);
    }

    private static void addLaunchCandidate(List<ScoredLaunchAnchor> candidates, BlockPos position,
                                           int rawScore, int rankScore) {
        if (rawScore < 56) return;
        int index = 0;
        while (index < candidates.size() && candidates.get(index).rankScore() >= rankScore) index++;
        candidates.add(index, new ScoredLaunchAnchor(position, rankScore));
        if (candidates.size() > MAX_LAUNCH_CANDIDATES) {
            candidates.remove(candidates.size() - 1);
        }
    }

    private static BlockPos firstStrongLaunchAnchor(List<ScoredLaunchAnchor> candidates, int dirX, int dirZ) {
        for (ScoredLaunchAnchor candidate : candidates) {
            if (hasLaunchBubble(candidate.position(), 4, 4)
                    && hasClearLaunchCorridor(candidate.position(), dirX, dirZ,
                    LAUNCH_CORRIDOR_DISTANCE)) {
                return candidate.position();
            }
        }
        return null;
    }

    private record ScoredLaunchAnchor(BlockPos position, int rankScore) {}

    private static int computeLoadedTakeoffSearchAhead(BlockPos from, int dirX, int dirZ) {
        int maxAhead = 0;
        for (int ahead = TAKEOFF_SEARCH_STEP; ahead <= TAKEOFF_MAX_SEARCH_AHEAD; ahead += TAKEOFF_SEARCH_STEP) {
            BlockPos sample = new BlockPos(
                    from.getX() + dirX * ahead,
                    from.getY(),
                    from.getZ() + dirZ * ahead);
            if (!isChunkLoaded(sample)) break;
            maxAhead = ahead;
        }
        if (maxAhead == 0) return 0;
        return Math.max(maxAhead, TAKEOFF_MIN_SEARCH_AHEAD);
    }

    private static int horizontalDistance(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    // Route steep vertical hops through Baritone.
    private static boolean isMostlyVerticalHop(BlockPos pos, BlockPos target) {
        int horiz = horizontalDistance(pos, target);
        int vert = Math.abs(target.getY() - pos.getY());
        return vert > 8 && horiz < Math.max(16, vert / 2);
    }

    private static int scoreTakeoffCandidate(BlockPos pos, int dirX, int dirZ) {
        if (!hasLaunchFooting(pos)) return Integer.MIN_VALUE;
        int score = 0;

        for (int y = 0; y <= 4; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos sample = new BlockPos(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (isAirLike(sample)) score++;
                }
            }
        }

        for (int step = 1; step <= 12; step++) {
            BlockPos feet = new BlockPos(pos.getX() + dirX * step, pos.getY(), pos.getZ() + dirZ * step);
            BlockPos head = new BlockPos(feet.getX(), feet.getY() + 1, feet.getZ());
            BlockPos above = new BlockPos(feet.getX(), feet.getY() + 2, feet.getZ());
            if (isAirLike(feet)) score++;
            if (isAirLike(head)) score += 2;
            if (isAirLike(above)) score += 2;
            else if (step <= 4) return Integer.MIN_VALUE;
        }

        return score;
    }

    private static boolean hasLaunchFooting(BlockPos pos) {
        if (!isChunkLoaded(pos)) return false;
        BlockPos below = new BlockPos(pos.getX(), pos.getY() - 1, pos.getZ());
        BlockPos head = new BlockPos(pos.getX(), pos.getY() + 1, pos.getZ());
        BlockPos above = new BlockPos(pos.getX(), pos.getY() + 2, pos.getZ());
        if (!isChunkLoaded(below) || !isChunkLoaded(head) || !isChunkLoaded(above)) return false;
        if (isAirLike(below)) return false;
        if (!hasCollision(below)) return false;
        if (isHazardousFooting(below)) return false;
        return isAirLike(pos) && isAirLike(head) && isAirLike(above);
    }

    private boolean isSafeGroundedForResupply() {
        BlockPos pos = currentPlayerPos();
        return isPlayerGrounded() && !isPlayerGliding()
                && pos != null && isSafeLandingPosition(pos);
    }

    private BlockPos findSafeResupplyLanding() {
        BlockPos origin = currentPlayerPos();
        if (origin == null) return null;
        for (int radius = 0; radius <= RESUPPLY_LANDING_RADIUS; radius += 2) {
            BlockPos best = null;
            int bestVertical = Integer.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos candidate = findSafeLandingInColumn(
                            origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                    if (candidate == null) continue;
                    int vertical = Math.abs(candidate.getY() - origin.getY());
                    if (vertical < bestVertical) {
                        best = candidate;
                        bestVertical = vertical;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private BlockPos findSafeLandingInColumn(int x, int originY, int z) {
        int minY = originY - RESUPPLY_LANDING_DOWN;
        for (int y = originY + RESUPPLY_LANDING_UP; y >= minY; y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (isSafeLandingPosition(candidate)) return candidate;
        }
        return null;
    }

    private boolean isSafeLandingPosition(BlockPos feet) {
        if (!isWalkableFeetPosition(feet)) return false;
        BlockPos floor = new BlockPos(feet.getX(), feet.getY() - 1, feet.getZ());
        if (isHazardousFooting(floor)) return false;

        int support = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos sample = new BlockPos(floor.getX() + dx, floor.getY(), floor.getZ() + dz);
                if (!isChunkLoaded(sample)) return false;
                if (hasCollision(sample)) {
                    if (isHazardousFooting(sample)) return false;
                    support++;
                } else if (detector.hasLavaBelow(sample.getX(), sample.getY(), sample.getZ())) {
                    return false;
                }
            }
        }
        return support >= 5;
    }

    private static boolean isHazardousFooting(BlockPos pos) {
        if (!isChunkLoaded(pos)) return true;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return true;
        BlockState state = mc.level.getBlockState(pos);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return true;
        BlockState state = mc.world.getBlockState(pos);
        /*?}*/
        return state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.MAGMA_BLOCK;
    }

    private static BlockPos currentPlayerPos() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.blockPosition();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        return mc.player.getBlockPos();
        /*?}*/
    }

    // MC yaw in degrees for a travel direction vector.
    private static float yawForDir(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // Set the player's view yaw (Stonecutter-safe).
    private static void setPlayerYaw(float yaw) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.setYRot(yaw);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.setYaw(yaw);
        /*?}*/
    }

    private static boolean isPlayerInNether() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == Level.NETHER;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && World.NETHER.equals(mc.world.getRegistryKey());
        /*?}*/
    }

    private static boolean isPlayerDead() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || mc.player.isDeadOrDying();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null || mc.player.isDead();
        /*?}*/
    }

    private static float currentEffectiveHealth() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0.0F;
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0.0F;
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
        /*?}*/
    }

    private static boolean isPlayerInLava() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isInLava();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.isInLava();
        /*?}*/
    }

    private static boolean isPlayerOnFire() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isOnFire();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.isOnFire();
        /*?}*/
    }

    private static boolean isPlayerColliding() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
                && (mc.player.horizontalCollision || mc.player.verticalCollision);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null
                && (mc.player.horizontalCollision || mc.player.verticalCollision);
        /*?}*/
    }

    private static boolean isPlayerGliding() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isFallFlying();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.isGliding();
        /*?}*/
    }

    private static boolean isPlayerGrounded() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.onGround();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.isOnGround();
        /*?}*/
    }

    private static boolean isChunkLoaded(BlockPos pos) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.isLoaded(pos);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        /*?}*/
    }

    private static boolean isAirLike(BlockPos pos) {
        if (!isChunkLoaded(pos)) return false;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        /*?}*/
        return state.getBlock() == Blocks.AIR
                || state.getBlock() == Blocks.CAVE_AIR
                || state.getBlock() == Blocks.VOID_AIR;
    }

    private static boolean hasCollision(BlockPos pos) {
        if (!isChunkLoaded(pos)) return false;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        return !state.getCollisionShape(mc.level, pos).isEmpty();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return !state.getCollisionShape(mc.world, pos).isEmpty();
        /*?}*/
    }
}
