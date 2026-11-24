package dev.moar.mixin;

import dev.moar.xaero.XaeroTravelOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
/*?}*/

/** Adds a destination action to Xaero's map context menu when both views are in the Nether. */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class XaeroWorldMapMixin {
    @Shadow(remap = false) private int rightClickX;
    @Shadow(remap = false) private int rightClickZ;

    /*? if >=26.1 {*//*
    @Shadow(remap = false) private ResourceKey<Level> rightClickDim;
    *//*?} else {*/
    @Shadow(remap = false) private RegistryKey<World> rightClickDim;
    /*?}*/

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    private void moar$addTravelOption(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.NETHER || rightClickDim != Level.NETHER) {
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !World.NETHER.equals(mc.world.getRegistryKey())
                || !World.NETHER.equals(rightClickDim)) {
        /*?}*/
            return;
        }

        ArrayList<RightClickOption> options = cir.getReturnValue();
        options.add(new XaeroTravelOption(options.size(), (IRightClickableElement) (Object) this,
                rightClickX, rightClickZ));
    }
}
