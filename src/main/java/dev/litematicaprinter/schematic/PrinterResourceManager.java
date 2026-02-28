package dev.litematicaprinter.schematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages designated supply-chest positions for the printer's AutoBuild mode.
 *
 * <p>Players can mark chests near their build site as "supply chests" so the
 * printer knows where additional blocks are stored.  This is essential for
 * megabase construction where inventory alone is insufficient.
 *
 * <p>Supply chest positions are persisted to
 * {@code litematica-printer/printer_supply.json}.
 */
public final class PrinterResourceManager {

    private PrinterResourceManager() {}

    // ── supply chest positions ──────────────────────────────────────────

    private static final List<BlockPos> supplyChests = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("litematica-printer")
            .resolve("printer_supply.json");

    // ── public API ──────────────────────────────────────────────────────

    public static boolean addSupplyChest(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        if (supplyChests.contains(immutable)) return false;
        supplyChests.add(immutable);
        save();
        return true;
    }

    public static boolean removeSupplyChest(BlockPos pos) {
        boolean removed = supplyChests.remove(pos.toImmutable());
        if (removed) save();
        return removed;
    }

    public static void clearSupplyChests() {
        supplyChests.clear();
        save();
    }

    public static List<BlockPos> getSupplyChests() {
        return Collections.unmodifiableList(supplyChests);
    }

    public static int supplyChestCount() {
        return supplyChests.size();
    }

    /** How few items trigger a restock run. */
    public static final int MIN_SUPPLY_ITEMS = 16;

    /**
     * Find the best supply chest for the given needed items.
     *
     * <p>If chests have been indexed (via {@link ChestIndexer}), prefers
     * chests that contain the needed items.  Falls back to nearest for
     * unindexed chests.
     *
     * @param from          position to measure distances from
     * @param neededItemIds item IDs the player needs
     * @param world         current world
     * @return best supply chest position, or {@code null} if none exist
     */
    public static BlockPos findBestSupplyChest(
            BlockPos from, Set<String> neededItemIds, World world) {

        return ChestIndexer.findBestChest(from, neededItemIds, supplyChests);
    }

    // ── materials analysis ──────────────────────────────────────────────

    /**
     * Computes the full bill of materials for a schematic: what's needed,
     * what's already placed, and what's missing.
     */
    public static MaterialsReport analyzeMaterials(
            LitematicaSchematic schematic,
            BlockPos anchor,
            World world) {
        if (schematic == null || world == null) return MaterialsReport.EMPTY;

        Map<String, Integer> unknownBlocks = schematic.hasUnknownBlocks()
                ? new HashMap<>(schematic.getUnknownBlocks())
                : Map.of();
        int unknownTotal = unknownBlocks.values().stream().mapToInt(Integer::intValue).sum();

        // 1. Count all required blocks from schematic
        Map<String, Integer> required = new HashMap<>();
        int totalBlocks = 0;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;

                        Item item = target.getBlock().asItem();
                        if (!(item instanceof BlockItem)) continue;

                        String itemId = Registries.ITEM.getId(item).toString();
                        required.merge(itemId, 1, Integer::sum);
                        totalBlocks++;
                    }
                }
            }
        }

        totalBlocks += unknownTotal;

        // 2. Count already-placed blocks
        Map<String, Integer> placed = new HashMap<>();
        int placedCount = 0;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        BlockState current = world.getBlockState(new BlockPos(wx, wy, wz));
                        if (current.equals(target)) {
                            Item item = target.getBlock().asItem();
                            if (item instanceof BlockItem) {
                                String itemId = Registries.ITEM.getId(item).toString();
                                placed.merge(itemId, 1, Integer::sum);
                                placedCount++;
                            }
                        }
                    }
                }
            }
        }

        // 3. Compute supply inventory from indexed chests
        Map<String, Integer> inSupply = ChestIndexer.getCombinedInventory();

        // 4. Compute missing = required - placed
        Map<String, Integer> missing = new HashMap<>();
        for (var entry : required.entrySet()) {
            int need = entry.getValue();
            int have = placed.getOrDefault(entry.getKey(), 0);
            int diff = need - have;
            if (diff > 0) {
                missing.put(entry.getKey(), diff);
            }
        }

        // 5. Compute stillNeeded = missing - inSupply
        Map<String, Integer> stillNeeded = new HashMap<>();
        for (var entry : missing.entrySet()) {
            int need = entry.getValue();
            int supply = inSupply.getOrDefault(entry.getKey(), 0);
            int diff = need - supply;
            if (diff > 0) {
                stillNeeded.put(entry.getKey(), diff);
            }
        }

        return new MaterialsReport(
                required, placed, missing,
                inSupply, stillNeeded,
                unknownBlocks, totalBlocks, placedCount
        );
    }

    // ── materials report record ─────────────────────────────────────────

    public record MaterialsReport(
            Map<String, Integer> required,
            Map<String, Integer> placed,
            Map<String, Integer> missing,
            Map<String, Integer> inSupply,
            Map<String, Integer> stillNeeded,
            Map<String, Integer> unknownBlocks,
            int totalBlocks,
            int placedBlocks
    ) {
        public static final MaterialsReport EMPTY = new MaterialsReport(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, 0
        );

        public boolean hasUnknownBlocks() { return !unknownBlocks.isEmpty(); }

        public int unknownCount() {
            return unknownBlocks.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int percentComplete() {
            return totalBlocks > 0 ? (placedBlocks * 100 / totalBlocks) : 0;
        }

        public int missingTypes() {
            return stillNeeded.size();
        }

        public int missingCount() {
            return stillNeeded.values().stream().mapToInt(Integer::intValue).sum();
        }

        /** Prettified item ID: "minecraft:oak_planks" → "Oak Planks" */
        public static String prettyName(String itemId) {
            String path = itemId.contains(":") ? itemId.split(":", 2)[1] : itemId;
            String[] parts = path.split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
            return sb.toString();
        }
    }

    // ── persistence ─────────────────────────────────────────────────────

    public static void load() {
        try {
            if (!Files.exists(FILE_PATH)) return;
            try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
                SavedData data = GSON.fromJson(reader, SavedData.class);
                if (data == null || data.positions == null) return;
                supplyChests.clear();
                for (int[] pos : data.positions) {
                    if (pos.length >= 3) {
                        supplyChests.add(new BlockPos(pos[0], pos[1], pos[2]));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            SavedData data = new SavedData();
            data.positions = new ArrayList<>();
            for (BlockPos pos : supplyChests) {
                data.positions.add(new int[] { pos.getX(), pos.getY(), pos.getZ() });
            }
            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class SavedData {
        List<int[]> positions;
    }
}
