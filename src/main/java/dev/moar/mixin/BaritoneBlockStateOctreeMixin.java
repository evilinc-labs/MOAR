package dev.moar.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.moar.compat.BaritoneNativeContextGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Keeps Baritone's cached chunk pointer valid during block reads.
@Pseudo
@Mixin(targets = "baritone.process.elytra.BlockStateOctreeInterface", remap = false)
public abstract class BaritoneBlockStateOctreeMixin {

    @WrapMethod(method = "get0", require = 0)
    private boolean moar$serializeBlockRead(int x, int y, int z, Operation<Boolean> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            return original.call(x, y, z);
        }
    }
}
