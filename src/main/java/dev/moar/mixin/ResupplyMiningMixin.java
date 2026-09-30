package dev.moar.mixin;

import dev.moar.travel.TravelManager;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Resupply advances mining once per tick. Vanilla's unpressed attack key would
// otherwise cancel that progress before every following resupply tick.
/*? if >=26.1 {*//*
@Mixin(Minecraft.class)
*//*?} else {*/
@Mixin(MinecraftClient.class)
/*?}*/
public abstract class ResupplyMiningMixin {
    /*? if >=26.1 {*//*
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    *//*?} else {*/
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    /*?}*/
    private void moar$preserveResupplyMining(boolean attacking, CallbackInfo ci) {
        if (TravelManager.get().isMiningResupplyContainer()) ci.cancel();
    }
}
