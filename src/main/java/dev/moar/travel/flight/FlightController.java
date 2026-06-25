package dev.moar.travel.flight;

import dev.moar.util.MoarNetworkManager;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Hand;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Drive manual elytra launch and rocket flight.
public final class FlightController {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Flight");

    private static final FlightController INSTANCE = new FlightController();
    public static FlightController get() { return INSTANCE; }

    // ── Mission ─────────────────────────────────────────────────
    private BlockPos destination;

    // ── Status ──────────────────────────────────────────────────
    private boolean active;
    private boolean arrived;
    private boolean stuck;
    private boolean launching;  // true = in launch phase, false = in cruise phase
    private int ticksActive;
    private int rocketCooldown;

    // Progress tracking (same window logic as BounceController)
    private double  lastProgressX;
    private double  lastProgressZ;
    private boolean progressSeeded;
    private int     noProgressTicks;

    // Launch sub-state
    private boolean sentGroundJump;  // true after the initial sprint-jump to get airborne
    private boolean sentJumpPress;
    private boolean firedFirstRocket;

    private FlightController() {}

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    public void start(BlockPos dest) {
        destination       = dest;
        active            = true;
        arrived           = false;
        stuck             = false;
        launching         = true;
        ticksActive       = 0;
        rocketCooldown    = 0;
        progressSeeded    = false;
        noProgressTicks   = 0;
        sentGroundJump    = false;
        sentJumpPress     = false;
        firedFirstRocket  = false;
        LOGGER.info("[Flight] start dest={}", dest.toShortString());
    }

    public void stop() {
        active    = false;
        launching = false;
        releaseKeys();
        LOGGER.debug("[Flight] stopped");
    }

    public boolean isActive()  { return active; }
    public boolean isArrived() { return arrived; }
    public boolean isStuck()   { return stuck; }
    public boolean isLaunching() { return active && launching; }
    public boolean isCruising()  { return active && !launching; }

    // ──────────────────────────────────────────────────────────────
    // Tick
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
            LOGGER.warn("[Flight] no player — aborting");
            stuck = true;
            active = false;
            return;
        }

        if (launching) {
            tickLaunching(mc);
        } else {
            tickCruising(mc);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Launch phase
    // ──────────────────────────────────────────────────────────────

    /*? if >=26.1 {*//*
    private void tickLaunching(Minecraft mc) {
        Options opts = mc.options;
        LocalPlayer player = mc.player;
    *//*?} else {*/
    private void tickLaunching(MinecraftClient mc) {
        GameOptions opts = mc.options;
        ClientPlayerEntity player = mc.player;
    /*?}*/

        // Timeout guard
        if (ticksActive > FlightTuning.LAUNCH_TIMEOUT_TICKS) {
            LOGGER.warn("[Flight] launch timeout at tick {}", ticksActive);
            stuck = true;
            active = false;
            releaseKeys();
            return;
        }

        // Phase 1: hold forward + sprint to build speed toward edge or flat-ground launch
        if (ticksActive <= FlightTuning.LAUNCH_WALK_TICKS) {
            /*? if >=26.1 {*//*
            opts.keyUp.setDown(true);
            opts.keySprint.setDown(true);
            *//*?} else {*/
            opts.forwardKey.setPressed(true);
            opts.sprintKey.setPressed(true);
            /*?}*/
            return;
        }

        /*? if >=26.1 {*//*
        boolean onGround = player.onGround();
        boolean flying   = player.isFallFlying();
        *//*?} else {*/
        boolean onGround = player.isOnGround();
        boolean flying   = player.isGliding();
        /*?}*/

        // Phase 2a: still on the ground — sprint-jump to get airborne
        if (onGround && !flying && !sentGroundJump) {
            /*? if >=26.1 {*//*
            opts.keySprint.setDown(true);
            player.jumpFromGround();
            *//*?} else {*/
            opts.sprintKey.setPressed(true);
            player.jump();
            /*?}*/
            sentGroundJump = true;
            LOGGER.debug("[Flight] sent ground-jump for airborne at tick {}", ticksActive);
            return;
        }

        // If we landed again before activating elytra, reset and retry
        if (onGround && sentGroundJump && !flying) {
            sentGroundJump = false;
            sentJumpPress  = false;
            return;
        }

        // Phase 2b: in air — press jump once to activate elytra
        if (!onGround && !flying && !sentJumpPress) {
            /*? if >=26.1 {*//*
            opts.keyJump.setDown(true);
            *//*?} else {*/
            opts.jumpKey.setPressed(true);
            /*?}*/
            sentJumpPress = true;
            LOGGER.debug("[Flight] sent elytra-activate jump at tick {}", ticksActive);
            return;
        }

        // Release jump key one tick after pressing it
        if (sentJumpPress) {
            /*? if >=26.1 {*//*
            opts.keyJump.setDown(false);
            *//*?} else {*/
            opts.jumpKey.setPressed(false);
            /*?}*/
        }

        // Phase 3: elytra active → fire first rocket, enter cruise
        if (flying && !firedFirstRocket) {
            firedFirstRocket = true;
            fireRocket(mc, player);
            rocketCooldown = FlightTuning.ROCKET_COOLDOWN_TICKS;
            LOGGER.info("[Flight] elytra active, first rocket fired — entering cruise");
            launching = false;
            // Seed progress for stuck detection
            lastProgressX  = player.getX();
            lastProgressZ  = player.getZ();
            progressSeeded = true;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Cruise phase
    // ──────────────────────────────────────────────────────────────

    /*? if >=26.1 {*//*
    private void tickCruising(Minecraft mc) {
        Options opts = mc.options;
        LocalPlayer player = mc.player;
    *//*?} else {*/
    private void tickCruising(MinecraftClient mc) {
        GameOptions opts = mc.options;
        ClientPlayerEntity player = mc.player;
    /*?}*/

        double px = player.getX();
        double pz = player.getZ();

        // ── Arrival check ─────────────────────────────────────────
        double dx = destination.getX() - px;
        double dz = destination.getZ() - pz;
        if (dx * dx + dz * dz < FlightTuning.ARRIVAL_RADIUS_SQ) {
            LOGGER.info("[Flight] arrived near dest {}", destination.toShortString());
            arrived = true;
            active = false;
            releaseKeys();
            return;
        }

        // ── Stuck detection ───────────────────────────────────────
        if (progressSeeded && ticksActive % FlightTuning.PROGRESS_CHECK_INTERVAL == 0) {
            double ddx = px - lastProgressX, ddz = pz - lastProgressZ;
            if (ddx * ddx + ddz * ddz >= FlightTuning.MIN_PROGRESS_PER_INTERVAL_SQ) {
                noProgressTicks = 0;
                lastProgressX   = px;
                lastProgressZ   = pz;
            } else {
                noProgressTicks += FlightTuning.PROGRESS_CHECK_INTERVAL;
                if (noProgressTicks >= FlightTuning.STUCK_TICKS) {
                    LOGGER.warn("[Flight] stuck in cruise at {},{}", (int) px, (int) pz);
                    stuck = true;
                    active = false;
                    releaseKeys();
                    return;
                }
            }
        }

        // ── Orient yaw toward destination ─────────────────────────
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        /*? if >=26.1 {*//*
        float curYaw = player.getYRot();
        float diff   = Mth.wrapDegrees(targetYaw - curYaw);
        *//*?} else {*/
        float curYaw = player.getYaw();
        float diff   = MathHelper.wrapDegrees(targetYaw - curYaw);
        /*?}*/
        if (Math.abs(diff) > FlightTuning.ALIGN_TOLERANCE_DEG) {
            float step = Math.min(Math.abs(diff), FlightTuning.MAX_YAW_STEP_DEG) * Math.signum(diff);
            /*? if >=26.1 {*//*
            player.setYRot(curYaw + step);
            *//*?} else {*/
            player.setYaw(curYaw + step);
            /*?}*/
        }

        // ── Pitch: slight nose-down for glide efficiency ──────────
        /*? if >=26.1 {*//*
        player.setXRot(FlightTuning.FALLBACK_PITCH);
        *//*?} else {*/
        player.setPitch(FlightTuning.FALLBACK_PITCH);
        /*?}*/

        // ── Hold forward + sprint ─────────────────────────────────
        /*? if >=26.1 {*//*
        opts.keyUp.setDown(true);
        opts.keySprint.setDown(true);
        opts.keyJump.setDown(false);
        opts.keyDown.setDown(false);
        *//*?} else {*/
        opts.forwardKey.setPressed(true);
        opts.sprintKey.setPressed(true);
        opts.jumpKey.setPressed(false);
        opts.backKey.setPressed(false);
        /*?}*/

        // ── Rocket cadence ────────────────────────────────────────
        if (rocketCooldown > 0) {
            rocketCooldown--;
        } else {
            if (fireRocket(mc, player)) {
                rocketCooldown = FlightTuning.ROCKET_COOLDOWN_TICKS;
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Rocket helper (fully duplicated per version to avoid mixed types)
    // ──────────────────────────────────────────────────────────────

    /*? if >=26.1 {*//*
    private boolean fireRocket(Minecraft mc, LocalPlayer player) {
        var inv = player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).getItem() == Items.FIREWORK_ROCKET) { slot = i; break; }
        }
        if (slot < 0) { LOGGER.debug("[Flight] no FIREWORK_ROCKET in hotbar"); return false; }
        inv.setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        LOGGER.debug("[Flight] fired rocket slot {}", slot);
        return true;
    }
    *//*?} else {*/
    private boolean fireRocket(MinecraftClient mc, ClientPlayerEntity player) {
        var inv = player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == Items.FIREWORK_ROCKET) { slot = i; break; }
        }
        if (slot < 0) { LOGGER.debug("[Flight] no FIREWORK_ROCKET in hotbar"); return false; }
        if (!MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.INVENTORY,
                MoarNetworkManager.OWNER_FLIGHT, 1, 2)
                || !MoarNetworkManager.tryAcquire(
                MoarNetworkManager.Lane.INTERACTION,
                MoarNetworkManager.OWNER_FLIGHT, 1, 2)) {
            return false;
        }
        /*? if >=1.21.5 {*//*
        inv.setSelectedSlot(slot);
        *//*?} else {*/
        inv.selectedSlot = slot;
        /*?}*/
        player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        LOGGER.debug("[Flight] fired rocket slot {}", slot);
        return true;
    }
    /*?}*/

    // ──────────────────────────────────────────────────────────────
    // Release all keys
    // ──────────────────────────────────────────────────────────────

    private void releaseKeys() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Options opts = mc.options;
        opts.keyUp.setDown(false);
        opts.keySprint.setDown(false);
        opts.keyJump.setDown(false);
        opts.keyDown.setDown(false);
        opts.keyLeft.setDown(false);
        opts.keyRight.setDown(false);
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        GameOptions opts = mc.options;
        opts.forwardKey.setPressed(false);
        opts.sprintKey.setPressed(false);
        opts.jumpKey.setPressed(false);
        opts.backKey.setPressed(false);
        opts.leftKey.setPressed(false);
        opts.rightKey.setPressed(false);
        /*?}*/
    }
}
