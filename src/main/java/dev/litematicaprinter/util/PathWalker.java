package dev.litematicaprinter.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Movement controller that walks the player toward a target position.
 *
 * <p>When Baritone is installed, delegates pathfinding to Baritone's A*
 * engine via reflection — no compile-time Baritone dependency required.
 * When Baritone is absent, falls back to vanilla key simulation with
 * straight-line walking, auto-jumping, and stuck detection.
 */
public final class PathWalker {

    private static final Logger LOGGER = LoggerFactory.getLogger("LitematicaPrinter/PathWalker");

    private PathWalker() {}

    // ── Baritone detection (cached) ─────────────────────────────────

    private static final boolean BARITONE_AVAILABLE;
    static {
        boolean found;
        try {
            Class.forName("baritone.api.BaritoneAPI");
            found = true;
        } catch (ClassNotFoundException e) {
            found = false;
        }
        BARITONE_AVAILABLE = found;
        if (found) {
            LOGGER.info("Baritone detected — using Baritone for pathfinding");
        } else {
            LOGGER.info("Baritone not found — using vanilla straight-line walker");
        }
    }

    /** Returns {@code true} if Baritone is present at runtime. */
    public static boolean isBaritoneAvailable() {
        return BARITONE_AVAILABLE;
    }

    // ── vanilla config ──────────────────────────────────────────────

    private static final double ARRIVAL_DIST_SQ = 2.5 * 2.5;
    private static final double ARRIVAL_Y_TOLERANCE = 3.0;
    private static final int MAX_TICKS = 1800;
    private static final int STUCK_THRESHOLD = 60;
    private static final double MIN_PROGRESS_SQ = 1.0;
    private static final int STUCK_CYCLES_BEFORE_GIVE_UP = 3;

    // ── shared state ────────────────────────────────────────────────

    private static BlockPos target;
    private static boolean active;
    private static boolean arrived;
    private static boolean stuck;
    private static int ticksWalking;

    // ── vanilla-only state ──────────────────────────────────────────

    private static Vec3d lastProgressPos;
    private static int lastProgressTick;
    private static int stuckCycles;

    // ── public API ──────────────────────────────────────────────────

    /**
     * Start walking to the given position.
     * Uses Baritone pathfinding if available, vanilla key simulation otherwise.
     */
    public static void walkTo(BlockPos pos) {
        target = pos.toImmutable();
        active = true;
        arrived = false;
        stuck = false;
        ticksWalking = 0;
        lastProgressPos = null;
        lastProgressTick = 0;
        stuckCycles = 0;
        LOGGER.debug("PathWalker: walking to ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());

        if (BARITONE_AVAILABLE) {
            BaritoneDelegate.walkTo(pos);
        }
    }

    /**
     * Start walking <b>adjacent</b> to the given position (next to it, not on
     * top of it).  Useful for navigating to chests or interactable blocks.
     * Falls back to {@link #walkTo(BlockPos)} when Baritone is not available.
     */
    public static void walkToAdjacent(BlockPos pos) {
        if (BARITONE_AVAILABLE) {
            target = pos.toImmutable();
            active = true;
            arrived = false;
            stuck = false;
            ticksWalking = 0;
            LOGGER.debug("PathWalker: walking adjacent to ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());
            BaritoneDelegate.walkToAdjacent(pos);
        } else {
            walkTo(pos);
        }
    }

    /** Stop all pathing and release keys. */
    public static void stop() {
        if (active && !BARITONE_AVAILABLE) {
            releaseKeys();
        }
        if (BARITONE_AVAILABLE) {
            BaritoneDelegate.stop();
        }
        active = false;
        arrived = false;
        stuck = false;
        target = null;
        stuckCycles = 0;
        LOGGER.debug("PathWalker: stopped");
    }

    public static boolean isActive()   { return active; }
    public static boolean hasArrived() { return arrived; }
    public static boolean isStuck()    { return stuck; }
    public static BlockPos getTarget() { return target; }
    public static int getTicksWalking() { return ticksWalking; }

    /**
     * Tick the movement controller.  Must be called every client tick while
     * navigation is active.
     */
    public static void tick() {
        if (!active || target == null) return;

        ticksWalking++;

        if (BARITONE_AVAILABLE) {
            tickBaritone();
        } else {
            tickVanilla();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BARITONE PATH — accessed entirely via reflection.
    // ═══════════════════════════════════════════════════════════════════

    private static void tickBaritone() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            stop();
            return;
        }

        // ── timeout check ───────────────────────────────────────────
        if (ticksWalking >= MAX_TICKS) {
            LOGGER.warn("PathWalker[Baritone]: timeout after {} ticks", ticksWalking);
            stuck = true;
            active = false;
            BaritoneDelegate.stop();
            return;
        }

        // ── check if Baritone finished pathing ──────────────────────
        if (!BaritoneDelegate.isPathing()) {
            // Baritone stopped — check if we're near the goal
            /*? if >=1.21.10 {*//*
            Vec3d playerPos = mc.player.getSyncedPos();
            *//*?} else {*/
            Vec3d playerPos = mc.player.getPos();
            /*?}*/
            Vec3d targetCenter = Vec3d.ofCenter(target);
            double dxz2 = horizontalDistSq(playerPos, targetCenter);
            double dy = Math.abs(playerPos.y - targetCenter.y);

            if (dxz2 <= ARRIVAL_DIST_SQ && dy <= ARRIVAL_Y_TOLERANCE) {
                LOGGER.debug("PathWalker[Baritone]: arrived after {} ticks", ticksWalking);
                arrived = true;
            } else {
                LOGGER.debug("PathWalker[Baritone]: pathing stopped without arriving (dist²={}, dy={})",
                        String.format("%.1f", dxz2), String.format("%.1f", dy));
            }
            active = false;
        }
    }

    /**
     * Inner class that accesses Baritone's API entirely via reflection.
     * No compile-time dependency on Baritone is required — all class,
     * method, and constructor references are resolved at runtime.
     * Reflection handles are cached in a static initializer for performance.
     * If reflection setup fails (e.g. incompatible Baritone version),
     * {@code ready} is set to {@code false} and all methods become no-ops.
     */
    private static final class BaritoneDelegate {

        private BaritoneDelegate() {}

        // ── reflection handles (resolved once, cached) ──────────────
        private static boolean ready;
        private static Method getProvider;
        private static Method getPrimaryBaritone;
        private static Method getCustomGoalProcess;
        private static Method setGoalAndPath;
        private static Method getPathingBehavior;
        private static Method cancelEverything;
        private static Method isPathingMethod;
        private static Constructor<?> goalBlockCtor;
        private static Constructor<?> goalGetToBlockCtor;

        static {
            try {
                Class<?> api = Class.forName("baritone.api.BaritoneAPI");
                getProvider = api.getMethod("getProvider");

                Class<?> provider = Class.forName("baritone.api.IBaritoneProvider");
                getPrimaryBaritone = provider.getMethod("getPrimaryBaritone");

                Class<?> iBaritone = Class.forName("baritone.api.IBaritone");
                getCustomGoalProcess = iBaritone.getMethod("getCustomGoalProcess");
                getPathingBehavior = iBaritone.getMethod("getPathingBehavior");

                Class<?> goal = Class.forName("baritone.api.pathing.goals.Goal");
                Class<?> iCustomGoalProcess = Class.forName("baritone.api.process.ICustomGoalProcess");
                setGoalAndPath = iCustomGoalProcess.getMethod("setGoalAndPath", goal);

                Class<?> iPathingBehavior = Class.forName("baritone.api.behavior.IPathingBehavior");
                cancelEverything = iPathingBehavior.getMethod("cancelEverything");
                isPathingMethod = iPathingBehavior.getMethod("isPathing");

                Class<?> goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
                goalBlockCtor = goalBlock.getConstructor(int.class, int.class, int.class);

                Class<?> goalGetToBlock = Class.forName("baritone.api.pathing.goals.GoalGetToBlock");
                goalGetToBlockCtor = goalGetToBlock.getConstructor(BlockPos.class);

                ready = true;
            } catch (Exception e) {
                LOGGER.error("PathWalker: Baritone reflection setup failed", e);
                ready = false;
            }
        }

        static boolean isReady() {
            return ready;
        }

        private static Object getPrimary() throws Exception {
            Object prov = getProvider.invoke(null);
            return getPrimaryBaritone.invoke(prov);
        }

        static void walkTo(BlockPos pos) {
            if (!ready) return;
            try {
                Object process = getCustomGoalProcess.invoke(getPrimary());
                Object goal = goalBlockCtor.newInstance(pos.getX(), pos.getY(), pos.getZ());
                setGoalAndPath.invoke(process, goal);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to set goal", e);
            }
        }

        static void walkToAdjacent(BlockPos pos) {
            if (!ready) return;
            try {
                Object process = getCustomGoalProcess.invoke(getPrimary());
                Object goal = goalGetToBlockCtor.newInstance(pos);
                setGoalAndPath.invoke(process, goal);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to set adjacent goal", e);
            }
        }

        static void stop() {
            if (!ready) return;
            try {
                Object behavior = getPathingBehavior.invoke(getPrimary());
                cancelEverything.invoke(behavior);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to cancel pathing", e);
            }
        }

        static boolean isPathing() {
            if (!ready) return false;
            try {
                Object behavior = getPathingBehavior.invoke(getPrimary());
                return (boolean) isPathingMethod.invoke(behavior);
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Fallback
    // ═══════════════════════════════════════════════════════════════════

    private static void tickVanilla() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            stop();
            return;
        }

        ClientPlayerEntity player = mc.player;

        // ── timeout check ───────────────────────────────────────────
        if (ticksWalking >= MAX_TICKS) {
            LOGGER.warn("PathWalker[Vanilla]: timeout after {} ticks", ticksWalking);
            releaseKeys();
            stuck = true;
            active = false;
            return;
        }

        // ── arrival check ───────────────────────────────────────────
        /*? if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dxz2 = horizontalDistSq(playerPos, targetCenter);
        double dy = Math.abs(playerPos.y - targetCenter.y);

        if (dxz2 <= ARRIVAL_DIST_SQ && dy <= ARRIVAL_Y_TOLERANCE) {
            LOGGER.debug("PathWalker[Vanilla]: arrived at target after {} ticks", ticksWalking);
            releaseKeys();
            arrived = true;
            active = false;
            return;
        }

        // ── stuck detection ─────────────────────────────────────────
        if (lastProgressPos == null) {
            lastProgressPos = playerPos;
            lastProgressTick = ticksWalking;
        } else if (ticksWalking - lastProgressTick >= STUCK_THRESHOLD) {
            double progressDist2 = horizontalDistSq(playerPos, lastProgressPos);
            if (progressDist2 < MIN_PROGRESS_SQ) {
                stuckCycles++;
                LOGGER.debug("PathWalker[Vanilla]: stuck cycle {} — trying jump", stuckCycles);
                if (player.isOnGround()) {
                    player.jump();
                }
                if (stuckCycles >= STUCK_CYCLES_BEFORE_GIVE_UP) {
                    LOGGER.warn("PathWalker[Vanilla]: stuck after {} cycles, declaring stuck", stuckCycles);
                    stuck = true;
                }
            } else {
                stuckCycles = 0;
            }
            lastProgressPos = playerPos;
            lastProgressTick = ticksWalking;
        }

        // ── look at target ──────────────────────────────────────────
        double dx = targetCenter.x - playerPos.x;
        double dz = targetCenter.z - playerPos.z;
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        float currentYaw = player.getYaw();
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float maxTurn = 15.0f;
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -maxTurn, maxTurn);
        player.setYaw(newYaw);
        player.setPitch(5.0f);

        // ── movement ────────────────────────────────────────────────
        GameOptions options = mc.options;

        options.forwardKey.setPressed(true);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);

        boolean facingTarget = Math.abs(yawDiff) < 30.0f;
        options.sprintKey.setPressed(facingTarget && dxz2 > 9.0);

        // ── proactive obstacle jumping ──────────────────────────────
        if (player.isOnGround()) {
            if (player.horizontalCollision) {
                player.jump();
            } else if (shouldJumpAhead(player, mc.world, targetYaw)) {
                player.jump();
            }
        }
    }

    // ── internals ───────────────────────────────────────────────────────

    private static boolean shouldJumpAhead(ClientPlayerEntity player, World world, float yaw) {
        double rad = Math.toRadians(yaw + 90.0);
        double aheadX = player.getX() + (-Math.sin(rad) * 1.2);
        double aheadZ = player.getZ() + (Math.cos(rad) * 1.2);

        BlockPos ahead = new BlockPos(
                (int) Math.floor(aheadX),
                (int) Math.floor(player.getY()),
                (int) Math.floor(aheadZ));

        BlockState footState  = world.getBlockState(ahead);
        BlockState headState  = world.getBlockState(ahead.up());
        BlockState aboveHead  = world.getBlockState(ahead.up(2));

        boolean footSolid  = !footState.isAir() && !footState.isReplaceable();
        boolean headClear  = headState.isAir() || headState.isReplaceable();
        boolean aboveClear = aboveHead.isAir() || aboveHead.isReplaceable();

        return footSolid && headClear && aboveClear;
    }

    private static void releaseKeys() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        GameOptions options = mc.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.sprintKey.setPressed(false);
    }

    private static double horizontalDistSq(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
