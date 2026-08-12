package dev.moar.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.babbaj.pathfinder.PathSegment;
import dev.moar.compat.BaritoneNativeContextGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.lang.ref.SoftReference;

// Prevents concurrent access to Baritone's native elytra context.
@Pseudo
@Mixin(targets = "baritone.process.elytra.NetherPathfinderContext", remap = false)
public abstract class BaritoneNetherPathfinderContextMixin {

    @WrapMethod(method = "lambda$queueCacheCulling$0", require = 0)
    private void moar$serializeCacheCulling(@Coerce Object blockInterface, int chunkX, int chunkZ,
                                            int maxDistance, Operation<Void> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            original.call(blockInterface, chunkX, chunkZ, maxDistance);
        }
    }

    @WrapMethod(method = "lambda$queueForPacking$1", require = 0)
    private void moar$serializeChunkPacking(SoftReference<?> chunk, Operation<Void> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            original.call(chunk);
        }
    }

    @WrapMethod(method = "lambda$queueBlockUpdate$3", require = 0)
    private void moar$serializeBlockUpdate(@Coerce Object event, Operation<Void> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            original.call(event);
        }
    }

    @WrapOperation(
            method = "hasChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/babbaj/pathfinder/NetherPathfinder;hasChunkFromJava(JII)Z",
                    remap = false
            ),
            require = 0
    )
    private boolean moar$serializeHasChunk(long context, int chunkX, int chunkZ,
                                            Operation<Boolean> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            return original.call(context, chunkX, chunkZ);
        }
    }

    @WrapOperation(
            method = "lambda$pathFindAsync$4",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/babbaj/pathfinder/NetherPathfinder;pathFind(JIIIIIIZZIZ)Ldev/babbaj/pathfinder/PathSegment;",
                    remap = false
            ),
            require = 0
    )
    private PathSegment moar$serializePathFind(long context,
                                               int srcX, int srcY, int srcZ,
                                               int dstX, int dstY, int dstZ,
                                               boolean includePartiallyLoaded,
                                               boolean allowOutsideWorld,
                                               int maxIterations,
                                               boolean fakeMissingChunks,
                                               Operation<PathSegment> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            return original.call(context, srcX, srcY, srcZ, dstX, dstY, dstZ,
                    includePartiallyLoaded, allowOutsideWorld, maxIterations, fakeMissingChunks);
        }
    }

    @WrapOperation(
            method = "raytrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/babbaj/pathfinder/NetherPathfinder;isVisible(JIDDDDDD)Z",
                    remap = false
            ),
            require = 0
    )
    private boolean moar$serializeVisible(long context, int cacheMissMode,
                                           double startX, double startY, double startZ,
                                           double endX, double endY, double endZ,
                                           Operation<Boolean> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            return original.call(context, cacheMissMode, startX, startY, startZ, endX, endY, endZ);
        }
    }

    @WrapOperation(
            method = "raytrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/babbaj/pathfinder/NetherPathfinder;isVisibleMulti(JII[D[DZ)I",
                    remap = false
            ),
            require = 0
    )
    private int moar$serializeVisibleMulti(long context, int cacheMissMode, int count,
                                           double[] starts, double[] ends, boolean any,
                                           Operation<Integer> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            return original.call(context, cacheMissMode, count, starts, ends, any);
        }
    }

    @WrapOperation(
            method = "raytrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/babbaj/pathfinder/NetherPathfinder;raytrace(JII[D[D[Z[D)V",
                    remap = false
            ),
            require = 0
    )
    private void moar$serializeRaytrace(long context, int cacheMissMode, int count,
                                        double[] starts, double[] ends,
                                        boolean[] hits, double[] hitPositions,
                                        Operation<Void> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            original.call(context, cacheMissMode, count, starts, ends, hits, hitPositions);
        }
    }

    @WrapOperation(
            method = "destroy",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/babbaj/pathfinder/NetherPathfinder;freeContext(J)V",
                    remap = false
            ),
            require = 0
    )
    private void moar$serializeFree(long context, Operation<Void> original) {
        synchronized (BaritoneNativeContextGate.lock()) {
            original.call(context);
        }
    }
}
