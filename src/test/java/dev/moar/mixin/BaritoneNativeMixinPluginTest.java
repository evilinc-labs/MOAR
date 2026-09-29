package dev.moar.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaritoneNativeMixinPluginTest {

    @Test
    void skipsLegacyGateOnlyWhenBaritoneHasBothContextLocks() throws IOException {
        assertTrue(hasInternalLocks(ModernContext.class));
        assertFalse(hasInternalLocks(ReadOnlyContext.class));
        assertFalse(hasInternalLocks(LegacyContext.class));
    }

    private static boolean hasInternalLocks(Class<?> contextClass) throws IOException {
        String resource = "/" + contextClass.getName().replace('.', '/') + ".class";
        try (InputStream in = contextClass.getResourceAsStream(resource)) {
            assertNotNull(in);
            return BaritoneNativeMixinPlugin.hasInternalLocks(in);
        }
    }

    private static final class ModernContext {
        ReentrantReadWriteLock.ReadLock readLock;
        ReentrantReadWriteLock.WriteLock writeLock;
    }

    private static final class ReadOnlyContext {
        ReentrantReadWriteLock.ReadLock readLock;
    }

    private static final class LegacyContext {
        long context;
    }
}
