package dev.moar.world;

import dev.moar.util.MoarNetworkManager;
import dev.moar.util.PacketTelemetry;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
/*?}*/

// Pause automation while server-applied knockback settles.
public final class VelocityMonitor {

    private static final double MIN_IMPULSE_DELTA = 0.08;
    private static final double SMALL_VELOCITY = 0.3;
    private static final int SMALL_SETTLE_TICKS = 4;
    private static final int NORMAL_SETTLE_TICKS = 2;

    private static final VelocityMonitor INSTANCE = new VelocityMonitor();

    private boolean primed;
    private double lastVelocityX;
    private double lastVelocityY;
    private double lastVelocityZ;
    private int lastHurtTime;
    private int settleTicks;
    private boolean settlingThisTick;

    public static VelocityMonitor get() {
        return INSTANCE;
    }

    private VelocityMonitor() {}

    /*? if >=26.1 {*//*
    public void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            reset();
            return;
        }
        LocalPlayer player = mc.player;
        Vec3 velocity = player.getDeltaMovement();
        sample(velocity.x, velocity.y, velocity.z, player.hurtTime, player.isFallFlying());
    }
    *//*?} else {*/
    public void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            reset();
            return;
        }
        ClientPlayerEntity player = mc.player;
        Vec3d velocity = player.getVelocity();
        sample(velocity.x, velocity.y, velocity.z, player.hurtTime, player.isGliding());
    }
    /*?}*/

    private void sample(double x, double y, double z, int hurtTime, boolean gliding) {
        settlingThisTick = false;
        if (!primed) {
            remember(x, y, z, hurtTime);
            primed = true;
            return;
        }

        double dx = x - lastVelocityX;
        double dy = y - lastVelocityY;
        double dz = z - lastVelocityZ;
        double impulse = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double velocity = Math.sqrt(x * x + y * y + z * z);
        boolean newlyHurt = hurtTime > lastHurtTime;
        remember(x, y, z, hurtTime);

        if (!gliding && newlyHurt && impulse >= MIN_IMPULSE_DELTA) {
            int pause = velocity <= SMALL_VELOCITY ? SMALL_SETTLE_TICKS : NORMAL_SETTLE_TICKS;
            settleTicks = Math.max(settleTicks, pause);
            PacketTelemetry.mark("velocity settle magnitude=" + format(velocity)
                    + " impulse=" + format(impulse) + " ticks=" + pause);
        }

        if (settleTicks > 0) {
            settlingThisTick = true;
            MoarNetworkManager.pauseAutomation(settleTicks, "velocity");
            settleTicks--;
        }
    }

    public boolean isSettling() {
        return settlingThisTick;
    }

    public int settleTicks() {
        return settleTicks;
    }

    public void reset() {
        primed = false;
        settleTicks = 0;
        settlingThisTick = false;
        lastHurtTime = 0;
        lastVelocityX = 0.0;
        lastVelocityY = 0.0;
        lastVelocityZ = 0.0;
    }

    private void remember(double x, double y, double z, int hurtTime) {
        lastVelocityX = x;
        lastVelocityY = y;
        lastVelocityZ = z;
        lastHurtTime = hurtTime;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
