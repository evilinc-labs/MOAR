package dev.moar.travel.bounce;

import dev.moar.travel.plan.HighwayCandidate;
import dev.moar.util.MoarNetworkManager;
import dev.moar.world.SetbackMonitor;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.Blocks;
*//*?} else {*/
import net.minecraft.block.Blocks;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Drive highway sprint-jump travel while BOUNCE owns movement.
public final class BounceController {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Bounce");

    private static final BounceController INSTANCE = new BounceController();
    public static BounceController get() { return INSTANCE; }

    // ── Mission state ─────────────────────────────────────────────
    private HighwayCandidate highway;
    private BlockPos exitColumn;
    private int travelDx;
    private int travelDz;

    // ── Runtime state ─────────────────────────────────────────────
    private boolean active;
    private boolean arrived;
    private boolean stuck;
    // Flag a wall ahead so TravelManager can detour without aborting.
    private boolean wallAhead;
    private String wallReason;
    // Distinguish falls from generic no-progress stalls.
    private boolean stuckFromFall;
    private int     ticksActive;

    private enum LaunchPhase {
        GROUNDED,
        GROUND_JUMP_REQUESTED,
        ASCENDING,
        LAUNCH_REQUESTED,
        GLIDING,
        LANDING
    }

    private LaunchPhase launchPhase = LaunchPhase.GROUNDED;
    private int launchPhaseTicks;
    private int launchRequests;
    private int completedBounces;
    private int consecutiveLaunchFailures;
    private double takeoffY;
    private double peakY;
    private boolean ceilingContact;
    private boolean roofDetected;
    private boolean acceleratingArc;
    private int correctionRecoveryBounces;
    private float launchPitch;
    private float arcPitch;
    private boolean launchArmed;
    private boolean setbackHolding;
    private boolean elytraLaunchEnabled;
    private boolean jumpingEnabled;
    private int wallObservationTicks;
    private int correctionEpisodeBaseline;
    private double lastPerpOffset;
    private float lastPerpCorrection;

    // Stuck detection — track XZ progress in periodic windows
    private double lastProgressX;
    private double lastProgressZ;
    private int    noProgressTicks;
    private boolean progressSeeded;

    private BounceController() {}

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    public void start(HighwayCandidate hw, BlockPos exit, int dx, int dz) {
        highway        = hw;
        exitColumn     = exit;
        travelDx       = dx;
        travelDz       = dz;
        active         = true;
        arrived        = false;
        stuck          = false;
        wallAhead      = false;
        wallReason     = "none";
        stuckFromFall  = false;
        ticksActive    = 0;
        launchPhase    = LaunchPhase.GROUNDED;
        launchPhaseTicks = 0;
        launchRequests = 0;
        completedBounces = 0;
        consecutiveLaunchFailures = 0;
        takeoffY = Double.NaN;
        peakY = Double.NaN;
        ceilingContact = false;
        roofDetected = false;
        acceleratingArc = true;
        correctionRecoveryBounces = 0;
        launchPitch = BounceTuning.LAUNCH_PITCH;
        arcPitch = BounceTuning.GLIDE_ACCEL_PITCH;
        launchArmed = false;
        setbackHolding = false;
        elytraLaunchEnabled = true;
        jumpingEnabled = true;
        wallObservationTicks = 0;
        correctionEpisodeBaseline = SetbackMonitor.get().totalCorrectionEpisodes();
        lastPerpOffset = 0.0;
        lastPerpCorrection = 0.0f;
        noProgressTicks = 0;
        progressSeeded  = false;
        LOGGER.info("[Bounce] start axis={} dir={},{} exit={}", hw.axis, dx, dz, exit.toShortString());
    }

    public void stop() {
        active = false;
        releaseKeys();
        LOGGER.debug("[Bounce] stopped");
    }

    public boolean isActive()    { return active; }
    public boolean isArrived()   { return arrived; }
    public boolean isStuck()     { return stuck; }
    // Check whether a wall was seen this tick.
    public boolean isWallAhead()     { return wallAhead; }
    public String wallReason()       { return wallReason; }
    // Check whether the stuck state came from a fall.
    public boolean isStuckFromFall() { return stuckFromFall; }
    public int     ticksActive() { return ticksActive; }

    // ──────────────────────────────────────────────────────────────
    // Tick — called by TravelManager via driveOwner() when BOUNCE owns
    // ──────────────────────────────────────────────────────────────

    public void tick() {
        if (!active) return;
        ticksActive++;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) {
            LOGGER.warn("[Bounce] no player, aborting");
            stuck = true;
            return;
        }

        applyCorrectionFallbacks();

        if (!SetbackMonitor.get().isCalm()) {
            enterSetbackHold();
            return;
        }
        if (setbackHolding) {
            resumeAfterSetback(mc);
        }

        // ── Current position ─────────────────────────────────────
        /*? if >=26.1 {*//*
        BlockPos pos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos pos = mc.player.getBlockPos();
        /*?}*/
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        // ── Fall detection ───────────────────────────────────────
        if (highway != null && highway.floorY > Integer.MIN_VALUE
                && pos.getY() < highway.floorY - BounceTuning.FALL_Y_THRESHOLD) {
            LOGGER.warn("[Bounce] fell off highway y={} floorY={}", pos.getY(), highway.floorY);
            stuckFromFall = true;
            stuck = true;
            releaseKeys();
            return;
        }

        // ── Wall / obstruction detection ─────────────────────────
        // Confirm a blocked corridor before requesting a detour.
        if (detectBlockedCorridor(mc)) {
            wallObservationTicks++;
        } else {
            wallObservationTicks = 0;
            wallReason = "none";
        }
        wallAhead = wallObservationTicks >= BounceTuning.WALL_CONFIRM_TICKS;
        if (wallAhead) return; // skip stuck-detection this tick

        // ── Exit check ───────────────────────────────────────────
        if (hasPassedExit(pos)) {
            LOGGER.info("[Bounce] arrived at exit {}", exitColumn.toShortString());
            arrived = true;
            releaseKeys();
            return;
        }

        // ── Stuck detection (periodic window check) ──────────────
        if (!progressSeeded) {
            lastProgressX  = px;
            lastProgressZ  = pz;
            progressSeeded = true;
        } else if (ticksActive % BounceTuning.PROGRESS_CHECK_INTERVAL == 0) {
            double dx = px - lastProgressX, dz = pz - lastProgressZ;
            if (dx * dx + dz * dz >= BounceTuning.MIN_PROGRESS_PER_INTERVAL_SQ) {
                noProgressTicks = 0;
                lastProgressX   = px;
                lastProgressZ   = pz;
            } else {
                noProgressTicks += BounceTuning.PROGRESS_CHECK_INTERVAL;
                if (noProgressTicks >= BounceTuning.STUCK_TICKS) {
                    LOGGER.warn("[Bounce] stuck at {} phase={} requests={} completed={}",
                            pos.toShortString(), launchPhase, launchRequests, completedBounces);
                    stuck = true;
                    releaseKeys();
                    return;
                }
            }
        }

    }

    // ──────────────────────────────────────────────────────────────
    // Pre-tick — runs before tickMovement(); applies movement inputs
    // ──────────────────────────────────────────────────────────────

    // Apply bounce inputs before vanilla movement runs.
    public void preTick() {
        if (!active) return;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return;
        if (!SetbackMonitor.get().isCalm()) {
            releaseKeys();
            return;
        }
        launchPhaseTicks++;

        // ── Yaw alignment ────────────────────────────────────────
        float targetYaw = yawForDirection(travelDx, travelDz);
        lastPerpOffset = 0.0;
        lastPerpCorrection = 0.0f;

        // ── Perp drift correction ─────────────────────────────────
        // Blend a small correction toward center into targetYaw to prevent
        // systematic guardrail drift over thousands of blocks.
        if (highway != null && highway.entry != null) {
            int perpDx  = highway.axis.perpDx();
            int perpDz  = highway.axis.perpDz();
            int perpSq  = perpDx * perpDx + perpDz * perpDz; // 1 cardinal, 2 diagonal
            double cpx  = mc.player.getX();
            double cpz  = mc.player.getZ();
            double perpOffset = ((cpx - highway.entry.getX() - 0.5) * perpDx
                               + (cpz - highway.entry.getZ() - 0.5) * perpDz) / perpSq;
            lastPerpOffset = perpOffset;
            if (Math.abs(perpOffset) > BounceTuning.PERP_CORRECTION_DEADZONE) {
                double normPerpX = perpDx / Math.sqrt(perpSq);
                double normPerpZ = perpDz / Math.sqrt(perpSq);
                double dirX = -Math.sin(Math.toRadians(targetYaw));
                double dirZ =  Math.cos(Math.toRadians(targetYaw));
                dirX -= normPerpX * perpOffset * BounceTuning.PERP_CORRECTION_GAIN;
                dirZ -= normPerpZ * perpOffset * BounceTuning.PERP_CORRECTION_GAIN;
                float correctedYaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
                /*? if >=26.1 {*//*
                float correction = Mth.wrapDegrees(correctedYaw - targetYaw);
                *//*?} else {*/
                float correction = MathHelper.wrapDegrees(correctedYaw - targetYaw);
                /*?}*/
                lastPerpCorrection = correction;
                targetYaw += lastPerpCorrection;
            }
        }

        /*? if >=26.1 {*//*
        float curYaw  = mc.player.getYRot();
        float diff    = Mth.wrapDegrees(targetYaw - curYaw);
        *//*?} else {*/
        float curYaw  = mc.player.getYaw();
        float diff    = MathHelper.wrapDegrees(targetYaw - curYaw);
        /*?}*/

        if (Math.abs(diff) > BounceTuning.ALIGN_TOLERANCE_DEG) {
            float step = Math.min(Math.abs(diff), BounceTuning.MAX_YAW_STEP_DEG)
                         * Math.signum(diff);
            /*? if >=26.1 {*//*
            mc.player.setYRot(curYaw + step);
            *//*?} else {*/
            mc.player.setYaw(curYaw + step);
            /*?}*/
        }

        // ── Emergency fall detection ──────────────────────────────
        // Player has dropped below the highway floor — there is a gap underfoot.
        // Set pitch to level so the recovery request carries the player horizontally.
        // Also arm stuckFromFall+stuck immediately so TravelManager can transition
        // to elytra recovery on the same tick without waiting for tick() to run.
        if (highway != null && highway.floorY > Integer.MIN_VALUE
                && mc.player.getY() < highway.floorY - 0.5) {
            /*? if >=26.1 {*//*
            mc.player.setXRot(0.0f);
            *//*?} else {*/
            mc.player.setPitch(0.0f);
            /*?}*/
            sendEmergencyStartFlying();
            if (!stuckFromFall) {
                stuckFromFall = true;
                stuck         = true;
                releaseKeys();
            }
            return;
        }

        // Use real vanilla state for every launch transition.
        /*? if >=26.1 {*//*
        Options opts = mc.options;
        opts.keyUp.setDown(true);
        opts.keySprint.setDown(true);
        opts.keyJump.setDown(false);
        boolean onGround = mc.player.onGround();
        boolean gliding = mc.player.isFallFlying();
        double velocityY = mc.player.getDeltaMovement().y;
        *//*?} else {*/
        GameOptions opts = mc.options;
        opts.forwardKey.setPressed(true);
        opts.sprintKey.setPressed(true);
        opts.jumpKey.setPressed(false);
        boolean onGround = mc.player.isOnGround();
        boolean gliding = mc.player.isGliding();
        double velocityY = mc.player.getVelocity().y;
        /*?}*/
        double rise = Double.isNaN(takeoffY) ? 0.0 : mc.player.getY() - takeoffY;
        if (!Double.isNaN(takeoffY)) {
            peakY = Double.isNaN(peakY) ? mc.player.getY() : Math.max(peakY, mc.player.getY());
            if (!onGround && mc.player.verticalCollision) {
                ceilingContact = true;
            }
        }
        float commandedPitch = launchPhase == LaunchPhase.GLIDING
                ? arcPitch
                : launchPitch;
        setPitch(elytraLaunchEnabled ? commandedPitch : 0.0f);

        switch (launchPhase) {
            case GROUNDED -> {
                if (!onGround) {
                    setLaunchPhase(LaunchPhase.LANDING);
                    return;
                }
                if (!jumpingEnabled) {
                    return;
                }
                if (requestGroundJump()) {
                    setLaunchPhase(LaunchPhase.GROUND_JUMP_REQUESTED);
                }
            }
            case GROUND_JUMP_REQUESTED -> {
                if (!onGround || rise >= BounceTuning.ELYTRA_ACTIVATE_MAX_RISE) {
                    if (!tryRequestLaunch(mc.player.getY(), velocityY, rise)) {
                        setLaunchPhase(LaunchPhase.ASCENDING);
                    }
                } else if (launchPhaseTicks >= BounceTuning.GROUND_JUMP_TIMEOUT_TICKS) {
                    setLaunchPhase(LaunchPhase.GROUNDED);
                }
            }
            case ASCENDING -> {
                if (launchArmed && gliding && !onGround) {
                    recordLaunchAccepted();
                    setLaunchPhase(LaunchPhase.GLIDING);
                } else if (!onGround || rise >= BounceTuning.ELYTRA_ACTIVATE_MAX_RISE) {
                    tryRequestLaunch(mc.player.getY(), velocityY, rise);
                } else if (onGround && launchPhaseTicks > 2) {
                    setLaunchPhase(LaunchPhase.GROUNDED);
                }
            }
            case LAUNCH_REQUESTED -> {
                if (gliding && !onGround) {
                    recordLaunchAccepted();
                    setLaunchPhase(LaunchPhase.GLIDING);
                } else if (onGround) {
                    recordLaunchRejected();
                    setLaunchPhase(LaunchPhase.GROUNDED);
                } else if (launchPhaseTicks >= BounceTuning.LAUNCH_ACK_TIMEOUT_TICKS) {
                    recordLaunchRejected();
                    setLaunchPhase(LaunchPhase.LANDING);
                }
            }
            case GLIDING -> {
                if (onGround) {
                    completedBounces++;
                    if (correctionRecoveryBounces > 0) {
                        correctionRecoveryBounces--;
                    }
                    if (completedBounces <= 3 || completedBounces % 10 == 0) {
                        double peakRise = Double.isNaN(peakY) || Double.isNaN(takeoffY)
                                ? 0.0 : peakY - takeoffY;
                        LOGGER.info("[Bounce] touchdown #{} speed={} peakRise={} ceiling={} roof={} mode={} launchPitch={} glidePitch={} offset={} steer={}",
                                completedBounces, String.format("%.3f", horizontalSpeed()),
                                String.format("%.3f", peakRise), ceilingContact,
                                roofDetected,
                                correctionRecoveryBounces > 0
                                        ? "RECOVERY"
                                        : acceleratingArc ? "ACCEL" : "CRUISE",
                                String.format("%.1f", launchPitch),
                                String.format("%.1f", arcPitch),
                                String.format("%.3f", lastPerpOffset),
                                String.format("%.2f", lastPerpCorrection));
                    }
                    setLaunchPhase(LaunchPhase.GROUNDED);
                } else if (!gliding) {
                    setLaunchPhase(LaunchPhase.LANDING);
                }
            }
            case LANDING -> {
                if (onGround) {
                    setLaunchPhase(LaunchPhase.GROUNDED);
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────

    // True once the player passes the exit column in the travel direction.
    private boolean hasPassedExit(BlockPos pos) {
        if (exitColumn == null || highway == null) return false;
        long playerProj = (long) pos.getX() * travelDx + (long) pos.getZ() * travelDz;
        long exitProj   = (long) exitColumn.getX() * travelDx + (long) exitColumn.getZ() * travelDz;
        return playerProj >= exitProj;
    }

    private static float yawForDirection(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // Find a blocked body-height corridor ahead.
    private boolean detectBlockedCorridor(
            /*? if >=26.1 {*//* Minecraft mc *//*?} else {*/ MinecraftClient mc /*?}*/) {
        if (mc.player == null || highway == null || exitColumn == null) return false;

        /*? if >=26.1 {*//*
        if (mc.level == null) return false;
        *//*?} else {*/
        if (mc.world == null) return false;
        /*?}*/

        /*? if >=26.1 {*//*
        int feetY = mc.player.blockPosition().getY();
        *//*?} else {*/
        int feetY = mc.player.getBlockPos().getY();
        /*?}*/
        float yaw = yawForDirection(travelDx, travelDz);
        double yawRad = Math.toRadians(yaw);
        double dirX   = -Math.sin(yawRad);
        double dirZ   =  Math.cos(yawRad);
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        long playerProjection = (long) Math.floor(px) * travelDx + (long) Math.floor(pz) * travelDz;
        long exitProjection = (long) exitColumn.getX() * travelDx + (long) exitColumn.getZ() * travelDz;
        int maxAhead = (int) Math.min(
                BounceTuning.OBSTACLE_SCAN_AHEAD,
                exitProjection - playerProjection - 1L);
        if (maxAhead < 2) return false;

        int perpX = highway.axis.perpDx();
        int perpZ = highway.axis.perpDz();
        for (int d = 2; d <= maxAhead; d++) {
            int centerX = (int) Math.floor(px + dirX * d);
            int centerZ = (int) Math.floor(pz + dirZ * d);
            boolean centerBlocked = false;
            int blockedLanes = 0;
            for (int lane = -1; lane <= 1; lane++) {
                int bx = centerX + perpX * lane;
                int bz = centerZ + perpZ * lane;
                BlockPos feet = new BlockPos(bx, feetY, bz);
                BlockPos head = new BlockPos(bx, feetY + 1, bz);
                /*? if >=26.1 {*//*
                boolean laneBlocked = isImpassable(mc.level.getBlockState(feet).getBlock())
                        || isImpassable(mc.level.getBlockState(head).getBlock());
                *//*?} else {*/
                boolean laneBlocked = isImpassable(mc.world.getBlockState(feet).getBlock())
                        || isImpassable(mc.world.getBlockState(head).getBlock());
                /*?}*/
                if (laneBlocked) {
                    blockedLanes++;
                    if (lane == 0) centerBlocked = true;
                }
            }
            if (centerBlocked) {
                String kind = blockedLanes == 3 ? "corridor" : "center-lane";
                wallReason = kind + "@" + centerX + "," + feetY + "," + centerZ + " d=" + d;
                return true;
            }
        }
        return false;
    }

    // Treat air and portals as passable; everything else blocks the corridor.
    /*? if >=26.1 {*//*
    private static boolean isImpassable(net.minecraft.world.level.block.Block b) {
    *//*?} else {*/
    private static boolean isImpassable(net.minecraft.block.Block b) {
    /*?}*/
        if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) return false;
        if (b == Blocks.NETHER_PORTAL) return false;
        return true;
    }

    // Latch the roof profile so pitch stays stable through the arc.
    private boolean hasLowCeiling(
            /*? if >=26.1 {*//* Minecraft mc *//*?} else {*/ MinecraftClient mc /*?}*/) {
        if (mc.player == null || highway == null) return false;
        /*? if >=26.1 {*//*
        if (mc.level == null) return false;
        *//*?} else {*/
        if (mc.world == null) return false;
        /*?}*/

        int x = (int) Math.floor(mc.player.getX());
        int z = (int) Math.floor(mc.player.getZ());
        int ceilingY = highway.floorY + BounceTuning.HEADROOM_Y_OFFSET;
        for (int ahead = 0; ahead <= BounceTuning.HEADROOM_SCAN_AHEAD; ahead++) {
            BlockPos pos = new BlockPos(
                    x + travelDx * ahead,
                    ceilingY,
                    z + travelDz * ahead);
            /*? if >=26.1 {*//*
            if (isImpassable(mc.level.getBlockState(pos).getBlock())) return true;
            *//*?} else {*/
            if (isImpassable(mc.world.getBlockState(pos).getBlock())) return true;
            /*?}*/
        }
        return false;
    }

    // Pulse vanilla jump input and let normal movement create the jump.
    private boolean requestGroundJump() {
        if (!MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.MOVEMENT,
                MoarNetworkManager.OWNER_BOUNCE, 1, 2)) {
            return false;
        }
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        mc.options.keyJump.setDown(true);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        mc.options.jumpKey.setPressed(true);
        /*?}*/
        roofDetected = hasLowCeiling(mc);
        double speed = horizontalSpeed();
        acceleratingArc = correctionRecoveryBounces == 0
                && speed < BounceTuning.TARGET_HORIZONTAL_SPEED;
        launchPitch = acceleratingArc
                ? accelerationLaunchPitch(speed)
                : BounceTuning.LAUNCH_PITCH;
        arcPitch = acceleratingArc
                ? accelerationPitch(speed)
                : BounceTuning.GLIDE_CRUISE_PITCH;
        setPitch(launchPitch);
        takeoffY = mc.player.getY();
        peakY = takeoffY;
        ceilingContact = false;
        launchArmed = false;
        LOGGER.debug("[Bounce] ground jump requested");
        return true;
    }

    private static float accelerationPitch(double speed) {
        if (speed < BounceTuning.ACCEL_LOW_SPEED_THRESHOLD) {
            return BounceTuning.GLIDE_ACCEL_LOW_SPEED_PITCH;
        }
        if (speed < BounceTuning.ACCEL_MID_SPEED_THRESHOLD) {
            return BounceTuning.GLIDE_ACCEL_MID_SPEED_PITCH;
        }
        return BounceTuning.GLIDE_ACCEL_PITCH;
    }

    private static float accelerationLaunchPitch(double speed) {
        if (speed < BounceTuning.ACCEL_LOW_SPEED_THRESHOLD) {
            return BounceTuning.LAUNCH_ACCEL_LOW_SPEED_PITCH;
        }
        if (speed < BounceTuning.ACCEL_MID_SPEED_THRESHOLD) {
            return BounceTuning.LAUNCH_ACCEL_MID_SPEED_PITCH;
        }
        return BounceTuning.LAUNCH_PITCH;
    }

    // Arm flight at the first safe fractional launch point.
    private boolean tryRequestLaunch(double y, double velocityY, double rise) {
        boolean reachedLaunchPoint = velocityY <= BounceTuning.ELYTRA_ACTIVATE_VY_THRESHOLD
                || rise >= BounceTuning.ELYTRA_ACTIVATE_MAX_RISE;
        if (!elytraLaunchEnabled || !reachedLaunchPoint
                || !requestStartFlying(y, velocityY, rise)) {
            return false;
        }
        launchRequests++;
        launchArmed = true;
        setLaunchPhase(LaunchPhase.LAUNCH_REQUESTED);
        return true;
    }

    // Pulse vanilla jump input after reaching a server-valid airborne state.
    private boolean requestStartFlying(double y, double velocityY, double rise) {
        if (!MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.MOVEMENT,
                MoarNetworkManager.OWNER_BOUNCE, 1, 2)) {
            return false;
        }
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        mc.options.keyJump.setDown(true);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        mc.options.jumpKey.setPressed(true);
        /*?}*/
        if (launchRequests < 3) {
            LOGGER.info("[Bounce] launch request #{} y={} rise={} vy={}", launchRequests + 1,
                    String.format("%.3f", y), String.format("%.3f", rise),
                    String.format("%.3f", velocityY));
        }
        return true;
    }

    // Make one best-effort recovery request after leaving the highway surface.
    private void sendEmergencyStartFlying() {
        if (!MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.MOVEMENT,
                MoarNetworkManager.OWNER_BOUNCE, 1, 2)) {
            return;
        }
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        /*?}*/
    }

    private void recordLaunchAccepted() {
        consecutiveLaunchFailures = 0;
        launchArmed = false;
        if (completedBounces < 3) {
            LOGGER.info("[Bounce] launch acknowledged #{} after {}t", launchRequests, launchPhaseTicks);
        }
    }

    private void recordLaunchRejected() {
        consecutiveLaunchFailures++;
        launchArmed = false;
        if (consecutiveLaunchFailures <= 3) {
            LOGGER.warn("[Bounce] launch not acknowledged phase={} failures={}",
                    launchPhase, consecutiveLaunchFailures);
        }
    }

    private void enterSetbackHold() {
        releaseKeys();
        noProgressTicks = 0;
        progressSeeded = false;
        if (!setbackHolding) {
            setbackHolding = true;
            correctionRecoveryBounces = BounceTuning.CORRECTION_RECOVERY_BOUNCES;
            LOGGER.warn("[Bounce] paused for server correction phase={} requests={} completed={}",
                    launchPhase, launchRequests, completedBounces);
        }
    }

    private void applyCorrectionFallbacks() {
        SetbackMonitor monitor = SetbackMonitor.get();
        int sessionEpisodes = Math.max(0,
                monitor.totalCorrectionEpisodes() - correctionEpisodeBaseline);
        int episodes = Math.min(sessionEpisodes,
                monitor.recentCorrectionEpisodeCount(BounceTuning.CORRECTION_STORM_WINDOW_TICKS));
        if (elytraLaunchEnabled && episodes >= BounceTuning.CORRECTIONS_DISABLE_ELYTRA) {
            elytraLaunchEnabled = false;
            LOGGER.warn("[Bounce] {} correction episodes; falling back to sprint-jump", episodes);
        }
        if (jumpingEnabled && episodes >= BounceTuning.CORRECTIONS_DISABLE_JUMP) {
            jumpingEnabled = false;
            LOGGER.warn("[Bounce] {} correction episodes; falling back to plain highway sprint", episodes);
        }
    }

    private void resumeAfterSetback(
            /*? if >=26.1 {*//* Minecraft mc *//*?} else {*/ MinecraftClient mc /*?}*/) {
        /*? if >=26.1 {*//*
        boolean onGround = mc.player.onGround();
        boolean gliding = mc.player.isFallFlying();
        *//*?} else {*/
        boolean onGround = mc.player.isOnGround();
        boolean gliding = mc.player.isGliding();
        /*?}*/
        LaunchPhase resumedPhase = onGround
                ? LaunchPhase.GROUNDED
                : gliding ? LaunchPhase.GLIDING : LaunchPhase.LANDING;
        setbackHolding = false;
        setLaunchPhase(resumedPhase);
        LOGGER.info("[Bounce] server correction settled; resuming phase={}", resumedPhase);
    }

    private void setLaunchPhase(LaunchPhase next) {
        if (launchPhase == next) return;
        LOGGER.debug("[Bounce] launch {} -> {}", launchPhase, next);
        launchPhase = next;
        launchPhaseTicks = 0;
    }

    private static double horizontalSpeed() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0.0;
        double x = mc.player.getDeltaMovement().x;
        double z = mc.player.getDeltaMovement().z;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0.0;
        double x = mc.player.getVelocity().x;
        double z = mc.player.getVelocity().z;
        /*?}*/
        return Math.sqrt(x * x + z * z);
    }

    private static void setPitch(float pitch) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.setXRot(pitch);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.setPitch(pitch);
        /*?}*/
    }

    private void releaseKeys() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null) return;

        /*? if >=26.1 {*//*
        Options opts = mc.options;
        opts.keyUp.setDown(false);
        opts.keySprint.setDown(false);
        opts.keyDown.setDown(false);
        opts.keyLeft.setDown(false);
        opts.keyRight.setDown(false);
        opts.keyJump.setDown(false);
        *//*?} else {*/
        GameOptions opts = mc.options;
        opts.forwardKey.setPressed(false);
        opts.sprintKey.setPressed(false);
        opts.backKey.setPressed(false);
        opts.leftKey.setPressed(false);
        opts.rightKey.setPressed(false);
        opts.jumpKey.setPressed(false);
        /*?}*/
    }
}
