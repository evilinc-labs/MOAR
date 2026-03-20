package dev.moar.mixin;

import dev.moar.util.SneakOverride;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
/*? if >=1.21.4 {*//*
import net.minecraft.util.PlayerInput;
*//*?}*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Injects at the tail of KeyboardInput.tick() to override the sneak input
// after keyboard polling but before movement processing. Same approach as
// Baritone's InputOverrideHandler.
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    /*? if >=1.21.4 {*//*
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void moar$overrideSneak(CallbackInfo ci) {
        if (SneakOverride.shouldSneak()) {
            PlayerInput old = this.playerInput;
            this.playerInput = new PlayerInput(
                    old.forward(), old.backward(), old.left(), old.right(),
                    old.jump(), true, old.sprint());
        }
    }
    *//*?} else {*/
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void moar$overrideSneak(boolean slowDown, float movementMultiplier, CallbackInfo ci) {
        if (SneakOverride.shouldSneak()) {
            this.sneaking = true;
        }
    }
    /*?}*/
}
