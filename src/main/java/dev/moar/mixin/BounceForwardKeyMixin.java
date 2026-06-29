package dev.moar.mixin;

import dev.moar.travel.bounce.BounceController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces key.forward → isPressed/isDown = true while BounceController is active.
 *
 * Without this, KeyboardInput.tick() reads the real key state (not held) and
 * passes a zero forward-axis movement input to travel(). On the server, the
 * ground-phase predictor sees no forward input → predicts near-zero velocity →
 * the sprint-jump position the client sends diverges → setback under strict validation.
 *
 * Field rename history (Yarn):
 *   <=1.21.8 Yarn  : KeyBinding.translationKey  /  getTranslationKey()
 *   >=1.21.10 Yarn : KeyBinding.id              /  getId()
 *   26.1 Mojmap    : KeyMapping.name            /  getName()
 *
 * Method name:
 *   Yarn:       isPressed()
 *   Mojmap 26.1: isDown()
 */
/*? if >=26.1 {*//*
@Mixin(net.minecraft.client.KeyMapping.class)
*//*?} else {*/
@Mixin(net.minecraft.client.option.KeyBinding.class)
/*?}*/
public abstract class BounceForwardKeyMixin {

    /*? if >=26.1 {*//*
    @Shadow @Final private String name;
    *//*?} else if >=1.21.10 {*//*
    @Shadow @Final private String id;
    *//*?} else {*/
    @Shadow @Final private String translationKey;
    /*?}*/

    /*? if >=26.1 {*//*
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void moar$ebounceForward(CallbackInfoReturnable<Boolean> cir) {
        if (!"key.forward".equals(this.name)) return;
        if (!BounceController.get().isActive()) return;
        cir.setReturnValue(true);
    }
    *//*?} else if >=1.21.10 {*//*
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void moar$ebounceForward(CallbackInfoReturnable<Boolean> cir) {
        if (!"key.forward".equals(this.id)) return;
        if (!BounceController.get().isActive()) return;
        cir.setReturnValue(true);
    }
    *//*?} else {*/
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void moar$ebounceForward(CallbackInfoReturnable<Boolean> cir) {
        if (!"key.forward".equals(this.translationKey)) return;
        if (!BounceController.get().isActive()) return;
        cir.setReturnValue(true);
    }
    /*?}*/
}
