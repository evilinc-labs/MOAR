package dev.moar.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// Scaffold tracking (config/moar/printer_scaffold.json).
public final class PrinterDatabase {

    private PrinterDatabase() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ═══════════════════════════════════════════════════════════════════
    //  SCAFFOLD TRACKING — blocks placed by Baritone during pathfinding
    // ═══════════════════════════════════════════════════════════════════

    // Scaffold positions -> block item ID. Persisted across restarts.
    private static final Map<BlockPos, String> scaffoldTable = new LinkedHashMap<>();

    private static final Path SCAFFOLD_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
            .resolve("printer_scaffold.json");

    /** True when scaffold table modified since last disk write. */
    private static boolean scaffoldDirty;

    /** Record a scaffold block position with its block item ID. */
    public static void addScaffold(BlockPos pos, String itemId) {
        /*? if >=26.1 {*//*
        scaffoldTable.put(pos.immutable(), itemId);
        *//*?} else {*/
        scaffoldTable.put(pos.toImmutable(), itemId);
        /*?}*/
        scaffoldDirty = true;
    }

    public static void removeScaffold(BlockPos pos) {
        if (scaffoldTable.remove(pos) != null) {
            scaffoldDirty = true;
        }
    }

    /** Remove multiple scaffold positions in one batch. */
    public static void removeScaffoldBatch(Collection<BlockPos> positions) {
        boolean changed = false;
        for (BlockPos pos : positions) {
            if (scaffoldTable.remove(pos) != null) changed = true;
        }
        if (changed) scaffoldDirty = true;
    }

    /** Write scaffold data to disk if modified since last write. */
    public static void flushScaffoldIfDirty() {
        if (scaffoldDirty) {
            saveScaffold();
            scaffoldDirty = false;
        }
    }

    public static boolean hasScaffold() {
        return !scaffoldTable.isEmpty();
    }

    public static int scaffoldCount() {
        return scaffoldTable.size();
    }

    public static boolean isScaffold(BlockPos pos) {
        return scaffoldTable.containsKey(pos);
    }

    /** Block item ID for a scaffold position, or null. */
    public static String getScaffoldBlockId(BlockPos pos) {
        return scaffoldTable.get(pos);
    }

    /** Unmodifiable view of all scaffold entries (position → item ID). */
    public static Map<BlockPos, String> getScaffoldEntries() {
        return Collections.unmodifiableMap(scaffoldTable);
    }

    /** Ordered stream of scaffold positions (insertion order). */
    public static java.util.stream.Stream<BlockPos> scaffoldStream() {
        return scaffoldTable.keySet().stream();
    }

    /** Clear all tracked scaffold positions and delete the file. */
    public static void clearScaffold() {
        scaffoldTable.clear();
        scaffoldDirty = false;
        saveScaffold();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PERSISTENCE — scaffold blocks
    // ═══════════════════════════════════════════════════════════════════

    /** Load scaffold entries from disk. */
    public static void loadScaffold() {
        try {
            if (!Files.exists(SCAFFOLD_FILE)) return;
            try (Reader reader = Files.newBufferedReader(SCAFFOLD_FILE)) {
                SavedScaffoldData data = GSON.fromJson(reader, SavedScaffoldData.class);
                if (data == null || data.entries == null) return;
                scaffoldTable.clear();
                for (ScaffoldEntry entry : data.entries) {
                    if (entry.pos != null && entry.pos.length >= 3
                            && entry.blockId != null) {
                        scaffoldTable.put(
                                new BlockPos(entry.pos[0], entry.pos[1], entry.pos[2]),
                                entry.blockId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load scaffold data", e);
        }
    }

    /** Save scaffold entries to disk. */
    public static void saveScaffold() {
        try {
            Files.createDirectories(SCAFFOLD_FILE.getParent());
            SavedScaffoldData data = new SavedScaffoldData();
            data.entries = new ArrayList<>();
            for (var entry : scaffoldTable.entrySet()) {
                ScaffoldEntry se = new ScaffoldEntry();
                se.pos = new int[] {
                        entry.getKey().getX(),
                        entry.getKey().getY(),
                        entry.getKey().getZ()
                };
                se.blockId = entry.getValue();
                data.entries.add(se);
            }
            try (Writer writer = Files.newBufferedWriter(SCAFFOLD_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save scaffold data", e);
        }
    }

    private static class SavedScaffoldData {
        List<ScaffoldEntry> entries;
    }

    private static class ScaffoldEntry {
        int[] pos;
        String blockId;
    }
}
