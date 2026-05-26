package dev.moar.travel.bounce;

import dev.moar.travel.plan.HighwayCandidate;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drives highway sprint-jump travel while BOUNCE owns movement. */
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

    public boolean isActive()  { return active; }
    public boolean isArrived() { return arrived; }
    public boolean isStuck()   { return stuck; }
    public int     ticksActive(){ return ticksActive; }

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
            stuck = true;
            releaseKeys();
            return;
        }

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

        // ── Yaw alignment ────────────────────────────────────────
        float targetYaw = yawForDirection(travelDx, travelDz);

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

        // ── Keys: hold forward + sprint ──────────────────────────
        /*? if >=26.1 {*//*
        Options opts = mc.options;
        opts.keyUp.setDown(true);
        opts.keySprint.setDown(true);
        opts.keyDown.setDown(false);
        opts.keyLeft.setDown(false);
        opts.keyRight.setDown(false);
        *//*?} else {*/
        GameOptions opts = mc.options;
        opts.forwardKey.setPressed(true);
        opts.sprintKey.setPressed(true);
        opts.backKey.setPressed(false);
        opts.leftKey.setPressed(false);
        opts.rightKey.setPressed(false);
        /*?}*/

        // ── Jump whenever grounded ───────────────────────────────
        /*? if >=26.1 {*//*
        if (mc.player.onGround()) mc.player.jumpFromGround();
        *//*?} else {*/
        if (mc.player.isOnGround()) mc.player.jump();
        /*?}*/
    }

    // ──────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────

    /**
     * Return true once the player reaches the exit along the chosen direction.
     */
    private boolean hasPassedExit(BlockPos pos) {
        if (exitColumn == null || highway == null) return false;
        long playerProj = (long) pos.getX() * travelDx + (long) pos.getZ() * travelDz;
        long exitProj   = (long) exitColumn.getX() * travelDx + (long) exitColumn.getZ() * travelDz;
        return playerProj >= exitProj;
    }

    private static float yawForDirection(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
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
        *//*?} else {*/
        GameOptions opts = mc.options;
        opts.forwardKey.setPressed(false);
        opts.sprintKey.setPressed(false);
        opts.backKey.setPressed(false);
        opts.leftKey.setPressed(false);
        opts.rightKey.setPressed(false);
        /*?}*/
    }
}
