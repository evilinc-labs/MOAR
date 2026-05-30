package dev.moar.mixin;

import dev.moar.travel.bounce.BounceController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spoofs getPose() to STANDING and isSprinting() to true while BounceController is active.
 *
 * Without the pose spoof, isGliding=true causes FALL_FLYING pose client-side,
 * mismatching the server's STANDING pose and triggering anti-cheat setbacks.
 *
 * Sprint spoof ensures the horizontal boost is applied to jump() regardless
 * of key-binding processing order within the same tick.
 */
/*? if >=26.1 {*//*
@Mixin(net.minecraft.world.entity.Entity.class)
*//*?} else {*/
@Mixin(net.minecraft.entity.Entity.class)
/*?}*/
public abstract class BounceGlideSpoofEntityMixin {

    /*? if >=26.1 {*//*
    @Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
    private void moar$ebouncePoseSpoof(CallbackInfoReturnable<net.minecraft.world.entity.Pose> cir) {
        if (!(((Object) this) instanceof net.minecraft.client.player.LocalPlayer)) return;
        if (net.minecraft.client.Minecraft.getInstance().player != (Object) this) return;
        if (!BounceController.get().isActive()) return;
        cir.setReturnValue(net.minecraft.world.entity.Pose.STANDING);
    }

    @Inject(method = "isSprinting", at = @At("HEAD"), cancellable = true)
    private void moar$ebounceSprintingSpoof(CallbackInfoReturnable<Boolean> cir) {
        if (!(((Object) this) instanceof net.minecraft.client.player.LocalPlayer)) return;
        if (net.minecraft.client.Minecraft.getInstance().player != (Object) this) return;
        if (!BounceController.get().isActive()) return;
        cir.setReturnValue(true);
    }
    *//*?} else {*/
    @Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
    private void moar$ebouncePoseSpoof(CallbackInfoReturnable<net.minecraft.entity.EntityPose> cir) {
        if (!(((Object) this) instanceof net.minecraft.client.network.ClientPlayerEntity)) return;
        if (net.minecraft.client.MinecraftClient.getInstance().player != (Object) this) return;
        if (!BounceController.get().isActive()) return;
        cir.setReturnValue(net.minecraft.entity.EntityPose.STANDING);
    }

    @Inject(method = "isSprinting", at = @At("HEAD"), cancellable = true)
    private void moar$ebounceSprintingSpoof(CallbackInfoReturnable<Boolean> cir) {
        if (!(((Object) this) instanceof net.minecraft.client.network.ClientPlayerEntity)) return;
        if (net.minecraft.client.MinecraftClient.getInstance().player != (Object) this) return;
        if (!BounceController.get().isActive()) return;
        cir.setReturnValue(true);
    }
    /*?}*/
}
