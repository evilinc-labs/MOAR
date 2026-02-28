package dev.litematicaprinter.schematic;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Client-side supply chest indexer that tracks the contents of opened chests
 * (and shulker boxes within them) to enable intelligent restocking.
 *
 * On the client side, you cannot read a chest's inventory without opening
 * it.  This indexer snapshots a chest's contents every time the player opens
 * one of the registered supply chests.  The snapshot includes items held
 * directly in the chest, as well as items inside any shulker boxes found in
 * the chest (read via {@code DataComponentTypes.CONTAINER}).
 *
 * Thread safety
 * All methods are called on the client/render thread only.
 */
public final class ChestIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger("LitematicaPrinter/ChestIndexer");

    private ChestIndexer() {}

    // ── per-chest snapshots ─────────────────────────────────────────────

    /**
     * Snapshot of a single supply chest's contents at the time it was last
     * opened.  Includes items directly in the chest and items found inside
     * shulker boxes within it.
     */
    public record ChestSnapshot(
            BlockPos pos,
            /** Item ID → total count (direct + shulker contents) */
            Map<String, Integer> items,
            /** Number of shulker boxes found in this chest */
            int shulkerCount,
            /** System.currentTimeMillis() when this snapshot was taken */
            long timestamp
    ) {
        public boolean contains(String itemId) {
            return items.containsKey(itemId);
        }

        public int getCount(String itemId) {
            return items.getOrDefault(itemId, 0);
        }

        /** Seconds since this snapshot was taken. */
        public long ageSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }

    // ── state ───────────────────────────────────────────────────────────

    /** BlockPos → last-known snapshot */
    private static final Map<BlockPos, ChestSnapshot> index = new LinkedHashMap<>();

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Scans the currently open chest screen and caches the contents if the
     * chest position matches a registered supply chest.
     *
     * @param chestPos the world position of the chest being opened
     * @param handler  the open container screen handler
     */
    public static void scanOpenChest(BlockPos chestPos, GenericContainerScreenHandler handler) {
        if (chestPos == null || handler == null) return;

        BlockPos key = chestPos.toImmutable();
        Map<String, Integer> items = new HashMap<>();
        int shulkerCount = 0;

        int chestSlots = handler.getRows() * 9;
        for (int slot = 0; slot < chestSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            // ── check if this is a shulker box ──────────────────────────
            if (isShulkerBox(stack)) {
                shulkerCount++;
                // index the shulker's contents
                Map<String, Integer> shulkerContents = readShulkerContents(stack);
                for (var entry : shulkerContents.entrySet()) {
                    items.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                // also count the shulker box item itself
                items.merge(itemId, stack.getCount(), Integer::sum);
            } else {
                items.merge(itemId, stack.getCount(), Integer::sum);
            }
        }

        ChestSnapshot snapshot = new ChestSnapshot(key, Map.copyOf(items), shulkerCount, System.currentTimeMillis());
        index.put(key, snapshot);

        LOGGER.debug("ChestIndexer: indexed {} at ({}, {}, {}) — {} item types, {} shulkers",
                chestSlots, key.getX(), key.getY(), key.getZ(), items.size(), shulkerCount);
    }

    /**
     * Read a shulker box ItemStack's contents via DataComponentTypes.CONTAINER.
     *
     * @return itemId → count for all items inside the shulker
     */
    public static Map<String, Integer> readShulkerContents(ItemStack shulkerStack) {
        Map<String, Integer> contents = new HashMap<>();
        if (shulkerStack == null || shulkerStack.isEmpty()) return contents;

        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        if (cc == null) return contents;

        for (ItemStack inner : cc.iterateNonEmpty()) {
            String innerId = Registries.ITEM.getId(inner.getItem()).toString();
            contents.merge(innerId, inner.getCount(), Integer::sum);
        }
        return contents;
    }

    /**
     * Find the best supply chest for a set of needed item IDs.
     *
     * Ranking strategy:
     *   Chests that have been indexed and contain at least one needed item
     *       are preferred, sorted by number of matching item types (descending),
     *       then by distance (ascending).
     *   Unindexed (never-opened) chests come next, sorted by distance.
     *   Indexed chests with no matching items are sorted by distance as a
     *       fallback (the snapshot may be stale).
     *
     * @param from          player position
     * @param neededItemIds set of item ID strings the player needs
     * @param supplyChests  all registered supply chest positions
     * @return best chest position, or {@code null} if no supply chests exist
     */
    public static BlockPos findBestChest(BlockPos from, Set<String> neededItemIds, List<BlockPos> supplyChests) {
        if (supplyChests.isEmpty()) return null;
        if (neededItemIds.isEmpty()) {
            // no specific needs — just return nearest
            return nearestOf(from, supplyChests);
        }

        BlockPos bestIndexed = null;
        int bestMatchCount = 0;
        double bestIndexedDist = Double.MAX_VALUE;

        BlockPos bestUnindexed = null;
        double bestUnindexedDist = Double.MAX_VALUE;

        BlockPos bestFallback = null;
        double bestFallbackDist = Double.MAX_VALUE;

        for (BlockPos pos : supplyChests) {
            double dist = from.getSquaredDistance(pos);
            ChestSnapshot snapshot = index.get(pos);

            if (snapshot == null) {
                // never opened — could contain anything
                if (dist < bestUnindexedDist) {
                    bestUnindexedDist = dist;
                    bestUnindexed = pos;
                }
            } else {
                int matchCount = 0;
                for (String needed : neededItemIds) {
                    if (snapshot.contains(needed)) matchCount++;
                }
                if (matchCount > 0) {
                    if (matchCount > bestMatchCount ||
                            (matchCount == bestMatchCount && dist < bestIndexedDist)) {
                        bestMatchCount = matchCount;
                        bestIndexedDist = dist;
                        bestIndexed = pos;
                    }
                } else {
                    // indexed but no matches — lowest priority
                    if (dist < bestFallbackDist) {
                        bestFallbackDist = dist;
                        bestFallback = pos;
                    }
                }
            }
        }

        // prefer indexed-with-matches > unindexed > fallback
        if (bestIndexed != null) return bestIndexed;
        if (bestUnindexed != null) return bestUnindexed;
        return bestFallback;
    }

    /**
     * Get the cached snapshot for a supply chest, or null if it
     * hasn't been scanned yet.
     */
    public static ChestSnapshot getSnapshot(BlockPos pos) {
        return index.get(pos.toImmutable());
    }

    /**
     * Get a combined inventory of all indexed supply chests.
     *
     * @return itemId → total count across all indexed chests (including shulker contents)
     */
    public static Map<String, Integer> getCombinedInventory() {
        Map<String, Integer> combined = new HashMap<>();
        for (ChestSnapshot snapshot : index.values()) {
            for (var entry : snapshot.items().entrySet()) {
                combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return combined;
    }

    /**
     * Get a summary of the index for display.
     */
    public static IndexSummary getSummary() {
        int indexed = 0;
        int unindexed = 0;
        int totalItems = 0;
        int totalShulkers = 0;
        int totalTypes = 0;
        Set<String> allTypes = new HashSet<>();

        List<BlockPos> supplyChests = PrinterResourceManager.getSupplyChests();
        for (BlockPos pos : supplyChests) {
            ChestSnapshot snapshot = index.get(pos);
            if (snapshot != null) {
                indexed++;
                totalShulkers += snapshot.shulkerCount();
                for (var entry : snapshot.items().entrySet()) {
                    totalItems += entry.getValue();
                    allTypes.add(entry.getKey());
                }
            } else {
                unindexed++;
            }
        }
        totalTypes = allTypes.size();
        return new IndexSummary(indexed, unindexed, totalItems, totalTypes, totalShulkers);
    }

    public record IndexSummary(
            int indexedChests,
            int unindexedChests,
            int totalItems,
            int totalItemTypes,
            int totalShulkers
    ) {
        public int totalChests() {
            return indexedChests + unindexedChests;
        }
    }

    /**
     * Invalidate (remove) the snapshot for a specific chest position.
     * Useful when the chest has been modified.
     */
    public static void invalidate(BlockPos pos) {
        index.remove(pos.toImmutable());
    }

    /** Clear the entire index. */
    public static void clearIndex() {
        index.clear();
    }

    // ── internals ───────────────────────────────────────────────────────

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static BlockPos nearestOf(BlockPos from, List<BlockPos> positions) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            double dist = from.getSquaredDistance(pos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        return nearest;
    }
}
