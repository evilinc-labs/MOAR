package dev.moar.mixin;

import dev.moar.util.SneakOverride;
/*? if >=26.1 {*//*
import net.minecraft.client.player.ClientInput;
*//*?} else {*/
import net.minecraft.client.input.Input;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.player.KeyboardInput;
*//*?} else {*/
import net.minecraft.client.input.KeyboardInput;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.entity.player.Input;
*//*?} else if >=1.21.4 {*//*
import net.minecraft.util.PlayerInput;
*//*?}*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Override sneak input at tail of KeyboardInput.tick().
@Mixin(KeyboardInput.class)
/*? if >=26.1 {*//*
public abstract class KeyboardInputMixin extends ClientInput {
*//*?} else {*/
public abstract class KeyboardInputMixin extends Input {
/*?}*/

    /*? if >=26.1 {*//*
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void moar$overrideSneak(CallbackInfo ci) {
        if (SneakOverride.shouldSneak()) {
            Input old = this.keyPresses;
            this.keyPresses = new Input(
                    old.forward(), old.backward(), old.left(), old.right(),
                    old.jump(), true, old.sprint());
        }
    }
    *//*?} else if >=1.21.4 {*//*
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
