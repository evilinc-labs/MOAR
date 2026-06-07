package dev.moar.travel.bounce;

import dev.moar.travel.plan.HighwayCandidate;

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
    // Flag a wall ahead so TravelManager can bypass without aborting.
    private boolean wallAhead;
    // Distinguish falls from generic no-progress stalls.
    private boolean stuckFromFall;
    private int     ticksActive;

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
        stuckFromFall  = false;
        ticksActive    = 0;
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
        // Check horizontalCollision and scan 2-6 blocks ahead at
        // player-body height.  TravelManager reads isWallAhead() to
        // trigger a bypass walk rather than a hard abort.
        wallAhead = detectWallOrCollision(mc);
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
                    LOGGER.warn("[Bounce] stuck at {}", pos.toShortString());
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

        // ── Yaw alignment ────────────────────────────────────────
        float targetYaw = yawForDirection(travelDx, travelDz);

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
            if (Math.abs(perpOffset) > BounceTuning.PERP_CORRECTION_DEADZONE) {
                double normPerpX = perpDx / Math.sqrt(perpSq);
                double normPerpZ = perpDz / Math.sqrt(perpSq);
                double dirX = -Math.sin(Math.toRadians(targetYaw));
                double dirZ =  Math.cos(Math.toRadians(targetYaw));
                dirX -= normPerpX * perpOffset * BounceTuning.PERP_CORRECTION_GAIN;
                dirZ -= normPerpZ * perpOffset * BounceTuning.PERP_CORRECTION_GAIN;
                targetYaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
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
        // Set pitch to LEVEL (0°) so that the elytra activation in sendStartFlying()
        // carries the player horizontally rather than diving straight into lava.
        // Also arm stuckFromFall+stuck immediately so TravelManager can transition
        // to elytra recovery on the same tick without waiting for tick() to run.
        if (highway != null && highway.floorY > Integer.MIN_VALUE
                && mc.player.getY() < highway.floorY - 0.5) {
            /*? if >=26.1 {*//*
            mc.player.setXRot(0.0f);
            *//*?} else {*/
            mc.player.setPitch(0.0f);
            /*?}*/
            sendStartFlying();
            if (!stuckFromFall) {
                stuckFromFall = true;
                stuck         = true;
                releaseKeys();
            }
            return;
        }

        // ── Sprint, jump, pitch, START_FALL_FLYING ────────────────
        mc.player.setSprinting(true);
        /*? if >=26.1 {*//*
        if (mc.player.onGround()) mc.player.jumpFromGround();
        mc.player.setXRot(BounceTuning.BOUNCE_PITCH);
        *//*?} else {*/
        if (mc.player.isOnGround()) mc.player.jump();
        mc.player.setPitch(BounceTuning.BOUNCE_PITCH);
        /*?}*/
        sendStartFlying();
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

    // True if a solid block is 2–6 ahead at body height, or horizontalCollision fired.
    // NETHER_PORTAL blocks are excluded — they have no collision shape and portals are
    // commonly built on highways; the player can glide through the opening.
    // Uses locked travel yaw so mouse movement doesn't cause false positives.
    private boolean detectWallOrCollision(
            /*? if >=26.1 {*//* Minecraft mc *//*?} else {*/ MinecraftClient mc /*?}*/) {
        if (mc.player == null || highway == null) return false;
        if (mc.player.horizontalCollision) return true;

        /*? if >=26.1 {*//*
        if (mc.level == null) return false;
        *//*?} else {*/
        if (mc.world == null) return false;
        /*?}*/

        int hwY   = highway.floorY + 1;  // player feet level
        float yaw = yawForDirection(travelDx, travelDz);
        double yawRad = Math.toRadians(yaw);
        double dirX   = -Math.sin(yawRad);
        double dirZ   =  Math.cos(yawRad);
        double px = mc.player.getX();
        double pz = mc.player.getZ();

        for (int d = 2; d <= 6; d++) {
            int bx = (int) Math.floor(px + dirX * d);
            int bz = (int) Math.floor(pz + dirZ * d);
            BlockPos feet = new BlockPos(bx, hwY,     bz);
            BlockPos head = new BlockPos(bx, hwY + 1, bz);
            /*? if >=26.1 {*//*
            if (isImpassable(mc.level.getBlockState(feet).getBlock())) return true;
            if (isImpassable(mc.level.getBlockState(head).getBlock())) return true;
            *//*?} else {*/
            if (isImpassable(mc.world.getBlockState(feet).getBlock())) return true;
            if (isImpassable(mc.world.getBlockState(head).getBlock())) return true;
            /*?}*/
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

    // Ask the server to start elytra flight.
    private void sendStartFlying() {
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
