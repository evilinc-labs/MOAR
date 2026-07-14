package dev.moar.mixin;

import com.mojang.authlib.GameProfile;
import dev.moar.util.PlacementEngine;
/*? if >=26.1 {*//*
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
*//*?} else {*/
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
/*?}*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Legacy guard; normal flying packets should stay alive for strict server validation.
/*? if >=26.1 {*//*
@Mixin(LocalPlayer.class)
public abstract class PlacementMovementSuppressMixin extends AbstractClientPlayer {
*//*?} else {*/
@Mixin(ClientPlayerEntity.class)
public abstract class PlacementMovementSuppressMixin extends AbstractClientPlayerEntity {
/*?}*/

    //? if >=1.21.5 {
    /*@Shadow private double lastXClient;
    @Shadow private double lastYClient;
    @Shadow private double lastZClient;
    @Shadow private float lastYawClient;
    @Shadow private float lastPitchClient;
    *///?} else {
    @Shadow private double lastX;
    @Shadow private double lastBaseY;
    @Shadow private double lastZ;
    @Shadow private float lastYaw;
    @Shadow private float lastPitch;
    //?}

    @Shadow private boolean lastHorizontalCollision;
    @Shadow private boolean lastOnGround;
    @Shadow private int ticksSinceLastPositionPacketSent;
    @Shadow protected abstract boolean isCamera();
    /*? if >=26.1 {*//*
    protected PlacementMovementSuppressMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }
    *//*?} else {*/
    protected PlacementMovementSuppressMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }
    /*?}*/

    @Unique
    private void moar$setLastXYZ(double x, double y, double z) {
        //? if >=1.21.5 {
        /*this.lastXClient = x;
        this.lastYClient = y;
        this.lastZClient = z;
        *///?} else {
        this.lastX = x;
        this.lastBaseY = y;
        this.lastZ = z;
        //?}
    }

    @Unique
    private void moar$setLastYawPitch(float yaw, float pitch) {
        //? if >=1.21.5 {
        /*this.lastYawClient = yaw;
        this.lastPitchClient = pitch;
        *///?} else {
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        //?}
    }

    // Decoupled camera: swap the aim in only while the flying packet builds,
    // then restore the view. Server gets real rotation, our screen doesn't move.
    @Unique private boolean moar$aimSwapped;
    @Unique private float moar$savedYaw;
    @Unique private float moar$savedPitch;

    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void moar$suppressPlacementMovementPacket(CallbackInfo ci) {
        if (PlacementEngine.consumeSuppressVanillaMove()
                || PlacementEngine.shouldSuppressVanillaMovementPackets()) {
            if (!this.isCamera()) {
                return;
            }
            ci.cancel();
            this.moar$setLastXYZ(this.getX(), this.getY(), this.getZ());
            this.moar$setLastYawPitch(this.getYaw(), this.getPitch());
            this.lastHorizontalCollision = this.horizontalCollision;
            this.lastOnGround = this.isOnGround();
            this.ticksSinceLastPositionPacketSent = 0;
            return;
        }
        // Not suppressed - inject the placement aim for the decoupled camera.
        if (PlacementEngine.hasServerAimOverride() && this.isCamera()) {
            /*? if >=26.1 {*//*
            this.moar$savedYaw = this.getYRot();
            this.moar$savedPitch = this.getXRot();
            this.setYRot(PlacementEngine.getServerAimYaw());
            this.setXRot(PlacementEngine.getServerAimPitch());
            *//*?} else {*/
            this.moar$savedYaw = this.getYaw();
            this.moar$savedPitch = this.getPitch();
            this.setYaw(PlacementEngine.getServerAimYaw());
            this.setPitch(PlacementEngine.getServerAimPitch());
            /*?}*/
            this.moar$aimSwapped = true;
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void moar$restorePlacementAim(CallbackInfo ci) {
        if (this.moar$aimSwapped) {
            /*? if >=26.1 {*//*
            this.setYRot(this.moar$savedYaw);
            this.setXRot(this.moar$savedPitch);
            *//*?} else {*/
            this.setYaw(this.moar$savedYaw);
            this.setPitch(this.moar$savedPitch);
            /*?}*/
            this.moar$aimSwapped = false;
        }
    }
}
