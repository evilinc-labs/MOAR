package dev.moar.travel;

import dev.moar.travel.bounce.BounceController;
import dev.moar.travel.bridge.TravelBaritoneBridge;
import dev.moar.travel.detour.DetourPlanner;
import dev.moar.travel.flight.FlightController;
import dev.moar.travel.highway.HighwayVerifier;
import dev.moar.travel.highway.IntegrityReport;
import dev.moar.travel.plan.HighwayPlanner;
import dev.moar.travel.plan.HighwayRoute;
import dev.moar.travel.telemetry.TravelLog;
import dev.moar.travel.telemetry.TravelTelemetry;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/** Owns the travel mission state machine and movement handoffs. */
public final class TravelManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Travel");

    private static final TravelManager INSTANCE = new TravelManager();
    public static TravelManager get() { return INSTANCE; }

    private final TravelState          state    = new TravelState();
    private final TravelBaritoneBridge bridge   = TravelBaritoneBridge.get();
    private final BounceController     bounce   = BounceController.get();
    private final FlightController     flight   = FlightController.get();
    private final HighwayPlanner       planner  = new HighwayPlanner();
    private final HighwayVerifier      verifier = HighwayVerifier.get();

    private int currentLegIndex = -1;

    /**
     * Saved bounce leg re-entry point after a detour completes.
     * Set when entering VERIFYING_DETOUR; cleared on detour completion or abort.
     */
    private BlockPos detourResumeExit;

    private TravelManager() {}

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    public synchronized boolean start(TravelMission mission) {
        if (state.phase != TravelPhase.IDLE) {
            LOGGER.warn("start() rejected: phase={} (not IDLE)", state.phase);
            return false;
        }
        state.reset();
        verifier.clear();
        state.mission = mission;
        currentLegIndex = -1;
        detourResumeExit = null;
        transition(TravelPhase.PLANNING, "user start: " + mission);
        return true;
    }

    public synchronized void stop() {
        if (state.phase == TravelPhase.IDLE) return;
        state.abortReason = "user stop";
        transition(TravelPhase.ABORTED, "user stop");
    }

    public synchronized void pause() {
        if (state.phase == TravelPhase.IDLE || state.phase == TravelPhase.PAUSED) return;
        state.pausedFromPhase = state.phase;
        releaseOwner(state.owner);
        state.owner = MovementOwner.NONE;
        TravelPhase from = state.phase;
        state.phase = TravelPhase.PAUSED;
        state.ticksInPhase = 0;
        state.lastTransitionReason = "user pause from " + from;
        TravelLog.get().recordTransition(missionId(), state.missionTicks, from, TravelPhase.PAUSED, state.lastTransitionReason);
    }

    public synchronized void resume() {
        if (state.phase != TravelPhase.PAUSED) return;
        TravelPhase target = state.pausedFromPhase != null ? state.pausedFromPhase : TravelPhase.PLANNING;
        transition(target, "user resume");
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

    // ──────────────────────────────────────────────────────────────
    // Tick
    // ──────────────────────────────────────────────────────────────

    public synchronized void tick(Object client) {
        if (state.phase == TravelPhase.IDLE) return;

        state.ticksInPhase++;
        state.missionTicks++;

        driveOwner();
        tickVerifier();

        switch (state.phase) {
            case PLANNING               -> tickPlanning();
            case APPROACH_ONRAMP        -> tickApproach();
            case BOUNCING               -> tickBouncing();
            case MINING_TO_FREENETHER   -> tickMining();
            case OFFRAMP_HANDOFF        -> tickOffRampHandoff();
            case ARRIVED, ABORTED       -> tickTerminal();
            case PAUSED                 -> { /* no-op */ }
            case VERIFYING_DETOUR       -> tickVerifyingDetour();
            case DETOURING              -> tickDetouring();
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

    // ──────────────────────────────────────────────────────────────
    // Phase handlers
    // ──────────────────────────────────────────────────────────────

    private void tickPlanning() {
        if (state.route != null) return;

        BlockPos origin = currentPlayerPos();
        if (origin == null) { abort("no player on PLANNING"); return; }

        HighwayPlanner.Options opts = new HighwayPlanner.Options()
                .freeNetherFlightThreshold(state.mission.freeNetherFlightThreshold);
        if (state.mission.expectedHighwayFloorY != Integer.MIN_VALUE)
            opts.expectedFloorY(state.mission.expectedHighwayFloorY);

        Optional<HighwayRoute> planned = planner.plan(origin, state.mission.destination, opts);
        if (planned.isEmpty()) { abort("planner returned no route"); return; }

        state.route = planned.get();
        currentLegIndex = -1;
        verifier.setHighway(state.route.primary, state.route.travelDx, state.route.travelDz);
        LOGGER.info("[Travel] planned {}", state.route);
        advanceLeg("planning complete");
    }

    private void tickApproach() {
        if (bridge.isArrived()) { advanceLeg("approach arrived"); return; }
        if (bridge.isStuck())   { abort("approach stuck"); }
    }

    private void tickBouncing() {
        // ── Grief check: interrupt and plan detour ────────────────
        IntegrityReport rep = verifier.lastReport();
        if (rep.status() == IntegrityReport.Status.GRIEFED
                && rep.confidence() >= DetourPlanner.MIN_CONFIDENCE) {
            if (state.mission == null || !state.mission.allowDetour) {
                abort("highway grief detected and detours disabled: " + rep);
                return;
            }
            LOGGER.warn("[Travel] grief detected during bounce: {}", rep);
            if (state.route != null) {
                for (HighwayRoute.Leg leg : state.route.legs) {
                    if (leg instanceof HighwayRoute.BounceLeg bl) {
                        detourResumeExit = bl.exitColumn();
                        break;
                    }
                }
            }
            releaseOwner(state.owner);
            state.owner = MovementOwner.NONE;
            transition(TravelPhase.VERIFYING_DETOUR, "grief detected: " + rep);
            return;
        }
        if (bounce.isArrived()) { advanceLeg("bounce arrived"); return; }
        if (bounce.isStuck())   { abort("bounce stuck"); }
    }

    /**
     * Plan detour waypoints and hand movement to Baritone.
     */
    private void tickVerifyingDetour() {
        BlockPos pos = currentPlayerPos();
        if (pos == null) { abort("no player pos during detour verification"); return; }

        IntegrityReport rep = verifier.lastReport();
        List<BlockPos> waypoints = DetourPlanner.plan(
                state.route.primary, rep, pos, state.route.travelDx, state.route.travelDz);
        if (waypoints.isEmpty()) {
            abort("detour planning failed: " + rep);
            return;
        }

        releaseOwner(state.owner);
        acquireOwner(MovementOwner.BARITONE);
        bridge.walkToWaypoints(waypoints, 3);
        transition(TravelPhase.DETOURING,
                "detour planned: " + waypoints.size() + " waypoints, griefRange=["
                + rep.griefStartOffset() + "," + rep.griefEndOffset() + "]");
    }

    /**
     * Resume bounce when Baritone finishes the detour.
     */
    private void tickDetouring() {
        if (bridge.isArrived()) {
            LOGGER.info("[Travel] detour complete, resuming bounce to {}", detourResumeExit);
            if (detourResumeExit != null && state.route != null) {
                acquireOwner(MovementOwner.BOUNCE);
                bounce.start(state.route.primary, detourResumeExit,
                        state.route.travelDx, state.route.travelDz);
                detourResumeExit = null;
                transition(TravelPhase.BOUNCING, "detour complete, resuming bounce");
            } else {
                detourResumeExit = null;
                advanceLeg("detour complete (no resume exit)");
            }
            return;
        }
        if (bridge.isStuck()) { abort("detour stuck"); }
    }

    private void tickMining() {
        if (bridge.isArrived()) {
            IntegrityReport rep = verifier.lastReport();
            LOGGER.info("[Travel] mining arrived; last integrity={}", rep);
            // If the mission has a flight leg and elytra is enabled, advance to LAUNCH
            // Otherwise end the mission here.
            advanceLeg("mining-leg arrived");
            return;
        }
        if (bridge.isStuck()) { abort("mining stuck"); }
    }

    private void tickOffRampHandoff() {
        advanceLeg("handoff complete");
    }

    private void tickTerminal() {
        if (state.ticksInPhase >= 1) {
            TravelPhase from = state.phase;
            releaseOwner(state.owner);
            verifier.clear();
            state.reset();
            currentLegIndex = -1;
            detourResumeExit = null;
            TravelLog.get().recordTransition(0, 0, from, TravelPhase.IDLE, "terminal cleanup");
        }
    }

    /**
     * Let FlightController launch, then try Baritone elytra or manual cruise.
     */
    private void tickLaunch() {
        if (flight.isArrived()) {
            transition(TravelPhase.ARRIVED, "flight arrived during launch (very short hop)");
            return;
        }
        if (flight.isStuck()) {
            abort("flight stuck during LAUNCH");
            return;
        }
        if (!flight.isActive()) {
            abort("flight inactive unexpectedly in LAUNCH");
            return;
        }
        if (state.ticksInPhase > 20 && bridge.isAvailable()) {
            BlockPos dest = flightDestination();
            if (dest != null) {
                bridge.startElytraFlight(dest);
                if (bridge.isElytraOwning()) {
                    LOGGER.info("[Travel] LAUNCH -> ELYTRA_CRUISE (Baritone elytra)");
                    acquireOwner(MovementOwner.BARITONE);
                    transition(TravelPhase.ELYTRA_CRUISE, "Baritone elytra started");
                    return;
                }
            }
        }
        if (state.ticksInPhase > 30) {
            LOGGER.info("[Travel] LAUNCH -> ELYTRA_FALLBACK (manual flight)");
            transition(TravelPhase.ELYTRA_FALLBACK, "no Baritone elytra, manual flight");
        }
    }

    /** Fall back to manual flight if Baritone elytra drops. */
    private void tickElytraCruise() {
        if (bridge.isElytraArrived()) {
            transition(TravelPhase.ARRIVED, "elytra cruise arrived");
            return;
        }
        if (bridge.isElytraStuck() || !bridge.isElytraOwning()) {
            LOGGER.warn("[Travel] ELYTRA_CRUISE -> ELYTRA_FALLBACK (Baritone elytra lost/stuck)");
            bridge.cancelAll();
            BlockPos dest = flightDestination();
            if (dest != null) {
                acquireOwner(MovementOwner.FLIGHT);
                flight.start(dest);
            }
            transition(TravelPhase.ELYTRA_FALLBACK, "Baritone elytra unavailable");
        }
    }

    /** Manual rocket flight owns movement. */
    private void tickElytraFallback() {
        if (flight.isArrived()) {
            transition(TravelPhase.ARRIVED, "manual flight arrived");
            return;
        }
        if (flight.isStuck()) {
            abort("manual flight stuck");
        }
    }

    /** Extract the destination from the FlightLeg in the current route, or null. */
    private BlockPos flightDestination() {
        if (state.route == null) return null;
        for (HighwayRoute.Leg leg : state.route.legs) {
            if (leg instanceof HighwayRoute.FlightLeg fl) return fl.destination();
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────
    // Leg advancement
    // ──────────────────────────────────────────────────────────────

    private void advanceLeg(String reason) {
        if (state.route == null) { abort("advanceLeg with null route"); return; }
        currentLegIndex++;
        if (currentLegIndex >= state.route.legs.size()) {
            transition(TravelPhase.ARRIVED, "all legs complete: " + reason);
            return;
        }
        HighwayRoute.Leg leg = state.route.legs.get(currentLegIndex);

        if (leg instanceof HighwayRoute.ApproachLeg approach) {
            startBaritoneWalk(approach.onRamp(), 2, TravelPhase.APPROACH_ONRAMP, reason);
        } else if (leg instanceof HighwayRoute.BounceLeg bounceLeg) {
            acquireOwner(MovementOwner.BOUNCE);
            bounce.start(bounceLeg.highway(), bounceLeg.exitColumn(),
                    bounceLeg.travelDx(), bounceLeg.travelDz());
            transition(TravelPhase.BOUNCING, reason + " -> bouncing to " + bounceLeg.exitColumn().toShortString());
        } else if (leg instanceof HighwayRoute.OffRampLeg) {
            transition(TravelPhase.OFFRAMP_HANDOFF, "offramp leg");
        } else if (leg instanceof HighwayRoute.MineLeg mine) {
            startBaritoneWalk(mine.freeNetherTarget(), 1, TravelPhase.MINING_TO_FREENETHER, reason);
        } else if (leg instanceof HighwayRoute.FlightLeg flightLeg) {
            if (state.mission != null && state.mission.useElytra) {
                acquireOwner(MovementOwner.FLIGHT);
                flight.start(flightLeg.destination());
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

    private void abort(String reason) {
        state.abortReason = reason;
        transition(TravelPhase.ABORTED, reason);
    }

    // ──────────────────────────────────────────────────────────────
    // Ownership
    // ──────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────
    // Transition
    // ──────────────────────────────────────────────────────────────

    private void transition(TravelPhase next, String reason) {
        TravelPhase from = state.phase;
        if (from == next) return;
        state.phase = next;
        state.ticksInPhase = 0;
        state.lastTransitionReason = reason;
        TravelLog.get().recordTransition(missionId(), state.missionTicks, from, next, reason);
        LOGGER.info("[Travel] {} -> {} ({})", from, next, reason);
        if (next.isTerminal()) {
            releaseOwner(state.owner);
            state.owner = MovementOwner.NONE;
        }
    }

    private long missionId() { return state.mission != null ? state.mission.id : 0L; }

    // ──────────────────────────────────────────────────────────────
    // Stonecutter-quarantined helpers
    // ──────────────────────────────────────────────────────────────

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
}
