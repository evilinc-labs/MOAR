package dev.moar.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Applies MOAR's legacy native-context gate only to Baritone builds without their own locks. */
public final class BaritoneNativeMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/BaritoneCompat");
    private static final String CONTEXT_CLASS = "baritone/process/elytra/NetherPathfinderContext.class";
    private static final String CONTEXT_MIXIN = "dev.moar.mixin.BaritoneNetherPathfinderContextMixin";
    private static final String OCTREE_MIXIN = "dev.moar.mixin.BaritoneBlockStateOctreeMixin";
    private Boolean hasInternalLocks;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!CONTEXT_MIXIN.equals(mixinClassName) && !OCTREE_MIXIN.equals(mixinClassName)) {
            return true;
        }
        if (hasInternalLocks == null) {
            hasInternalLocks = detectInternalLocks();
            if (hasInternalLocks) {
                LOGGER.info("Baritone has native-context read/write locks; skipping MOAR's legacy gate");
            }
        }
        return !hasInternalLocks;
    }

    private boolean detectInternalLocks() {
        try {
            var baritone = FabricLoader.getInstance().getModContainer("baritone");
            if (baritone.isEmpty()) {
                return false;
            }
            var classPath = baritone.get().findPath(CONTEXT_CLASS);
            if (classPath.isEmpty()) {
                return false;
            }
            return hasInternalLocks(classPath.get());
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Could not inspect Baritone's native context; retaining legacy gate", ex);
            return false;
        }
    }

    static boolean hasInternalLocks(Path classPath) throws IOException {
        try (InputStream in = Files.newInputStream(classPath)) {
            return hasInternalLocks(in);
        }
    }

    static boolean hasInternalLocks(InputStream in) throws IOException {
        boolean[] locks = new boolean[2];
        new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                if ("readLock".equals(name)
                        && "Ljava/util/concurrent/locks/ReentrantReadWriteLock$ReadLock;".equals(descriptor)) {
                    locks[0] = true;
                } else if ("writeLock".equals(name)
                        && "Ljava/util/concurrent/locks/ReentrantReadWriteLock$WriteLock;".equals(descriptor)) {
                    locks[1] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return locks[0] && locks[1];
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
