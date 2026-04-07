package dev.moar.stash;

import dev.moar.stash.StashManager.ContainerEntry;
import dev.moar.stash.StashManager.ShulkerDetail;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

// SQLite storage for stash scan results (config/moar/stash.db).
// Tables: containers, items, shulkers, shulker_items.
public final class StashDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/StashDB");

    private static final Path DB_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
            .resolve("stash.db");

    private Connection connection;

    // ── Lifecycle ───────────────────────────────────────────────────────

    /** Open (or create) the database and ensure tables exist. */
    public void open() {
        try {
            // Load SQLite JDBC (Fabric classloader not visible to DriverManager)
            Class.forName("org.sqlite.JDBC");

            Files.createDirectories(DB_PATH.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH.toAbsolutePath());
            connection.setAutoCommit(false);
            createTables();
            LOGGER.info("Stash database opened: {}", DB_PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to open stash database", e);
        }
    }

    /** Close the database connection. */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.error("Failed to close stash database", e);
            }
            connection = null;
        }
    }

    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Schema ──────────────────────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS containers (
                    x         INTEGER NOT NULL,
                    y         INTEGER NOT NULL,
                    z         INTEGER NOT NULL,
                    block_type TEXT   NOT NULL,
                    is_double  INTEGER NOT NULL DEFAULT 0,
                    shulker_count INTEGER NOT NULL DEFAULT 0,
                    timestamp  INTEGER NOT NULL,
                    label      TEXT    DEFAULT NULL,
                    PRIMARY KEY (x, y, z)
                )
                """);

            // Add label column if upgrading from older schema
            migrateAddLabel(stmt);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS items (
                    x        INTEGER NOT NULL,
                    y        INTEGER NOT NULL,
                    z        INTEGER NOT NULL,
                    item_id  TEXT    NOT NULL,
                    quantity INTEGER NOT NULL,
                    PRIMARY KEY (x, y, z, item_id),
                    FOREIGN KEY (x, y, z) REFERENCES containers(x, y, z) ON DELETE CASCADE
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS shulkers (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    x            INTEGER NOT NULL,
                    y            INTEGER NOT NULL,
                    z            INTEGER NOT NULL,
                    shulker_type TEXT    NOT NULL,
                    FOREIGN KEY (x, y, z) REFERENCES containers(x, y, z) ON DELETE CASCADE
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS shulker_items (
                    shulker_id INTEGER NOT NULL,
                    item_id    TEXT    NOT NULL,
                    quantity   INTEGER NOT NULL,
                    PRIMARY KEY (shulker_id, item_id),
                    FOREIGN KEY (shulker_id) REFERENCES shulkers(id) ON DELETE CASCADE
                )
                """);

            // Index for fast item lookups across all containers
            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_items_item_id ON items(item_id)
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS regions (
                    name       TEXT    PRIMARY KEY,
                    x1         INTEGER NOT NULL,
                    y1         INTEGER NOT NULL,
                    z1         INTEGER NOT NULL,
                    x2         INTEGER NOT NULL,
                    y2         INTEGER NOT NULL,
                    z2         INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
        }
        connection.commit();
    }

    /** Safely add the label column to an existing containers table. */
    private void migrateAddLabel(Statement stmt) {
        try {
            stmt.executeUpdate("ALTER TABLE containers ADD COLUMN label TEXT DEFAULT NULL");
        } catch (SQLException ignored) {
            // Column already exists — expected on non-first run
        }
    }

    // ── Region operations ────────────────────────────────────────────────

    /** Save a named region (upsert). */
    public void saveRegion(String name, BlockPos corner1, BlockPos corner2) {
        if (!isOpen()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO regions (name, x1, y1, z1, x2, y2, z2, created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, name);
            ps.setInt(2, corner1.getX());
            ps.setInt(3, corner1.getY());
            ps.setInt(4, corner1.getZ());
            ps.setInt(5, corner2.getX());
            ps.setInt(6, corner2.getY());
            ps.setInt(7, corner2.getZ());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save region '{}'", name, e);
            rollback();
        }
    }

    /** Load a named region. Returns [corner1, corner2] or null if not found. */
    public BlockPos[] loadRegion(String name) {
        if (!isOpen()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT x1, y1, z1, x2, y2, z2 FROM regions WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BlockPos c1 = new BlockPos(rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"));
                    BlockPos c2 = new BlockPos(rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2"));
                    return new BlockPos[]{c1, c2};
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load region '{}'", name, e);
        }
        return null;
    }

    /** List all saved region names. */
    public List<String> listRegions() {
        List<String> names = new ArrayList<>();
        if (!isOpen()) return names;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM regions ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to list regions", e);
        }
        return names;
    }

    /** Get all regions with their bounds. */
    public Map<String, BlockPos[]> loadAllRegions() {
        Map<String, BlockPos[]> result = new LinkedHashMap<>();
        if (!isOpen()) return result;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, x1, y1, z1, x2, y2, z2 FROM regions ORDER BY name")) {
            while (rs.next()) {
                BlockPos c1 = new BlockPos(rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"));
                BlockPos c2 = new BlockPos(rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2"));
                result.put(rs.getString("name"), new BlockPos[]{c1, c2});
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load all regions", e);
        }
        return result;
    }

    /** Delete a named region. Returns true if a row was deleted. */
    public boolean deleteRegion(String name) {
        if (!isOpen()) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM regions WHERE name=?")) {
            ps.setString(1, name);
            int rows = ps.executeUpdate();
            connection.commit();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to delete region '{}'", name, e);
            rollback();
            return false;
        }
    }

    // ── Label operations ────────────────────────────────────────────────

    /** Set the label for a container at the given position. */
    public void updateLabel(BlockPos pos, String label) {
        if (!isOpen()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE containers SET label=? WHERE x=? AND y=? AND z=?")) {
            ps.setString(1, label);
            ps.setInt(2, pos.getX());
            ps.setInt(3, pos.getY());
            ps.setInt(4, pos.getZ());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to update label at {}", pos, e);
            rollback();
        }
    }

    /** Batch-update labels for multiple containers. */
    public void updateLabels(Map<BlockPos, String> labels) {
        if (!isOpen() || labels.isEmpty()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE containers SET label=? WHERE x=? AND y=? AND z=?")) {
            for (var entry : labels.entrySet()) {
                ps.setString(1, entry.getValue());
                ps.setInt(2, entry.getKey().getX());
                ps.setInt(3, entry.getKey().getY());
                ps.setInt(4, entry.getKey().getZ());
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to batch-update labels", e);
            rollback();
        }
    }

    /** Get the label for a container. Returns null if not set. */
    public String getLabel(BlockPos pos) {
        if (!isOpen()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT label FROM containers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("label") : null;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get label at {}", pos, e);
            return null;
        }
    }

    /** Get all labels (position → label) for containers that have one. */
    public Map<BlockPos, String> getAllLabels() {
        Map<BlockPos, String> result = new LinkedHashMap<>();
        if (!isOpen()) return result;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT x, y, z, label FROM containers WHERE label IS NOT NULL")) {
            while (rs.next()) {
                BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                result.put(pos, rs.getString("label"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get all labels", e);
        }
        return result;
    }

    // ── Write operations ────────────────────────────────────────────────

    /**
     * Save a single container entry (upsert). Replaces any existing data
     * at the same position.
     */
    public void saveContainer(ContainerEntry entry) {
        if (!isOpen()) return;
        try {
            deleteContainerAt(entry.pos());
            insertContainer(entry);
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save container at {}", entry.pos(), e);
            rollback();
        }
    }

    /**
     * Batch-save the entire in-memory index to the database.
     * Existing entries at the same positions are replaced.
     */
    public void saveAll(Map<BlockPos, ContainerEntry> index) {
        if (!isOpen() || index.isEmpty()) return;
        try {
            for (ContainerEntry entry : index.values()) {
                deleteContainerAt(entry.pos());
                insertContainer(entry);
            }
            connection.commit();
            LOGGER.info("Saved {} containers to database", index.size());
        } catch (SQLException e) {
            LOGGER.error("Failed to batch-save containers", e);
            rollback();
        }
    }

    private void insertContainer(ContainerEntry entry) throws SQLException {
        BlockPos pos = entry.pos();

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO containers (x, y, z, block_type, is_double, shulker_count, timestamp) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.setString(4, entry.blockType());
            ps.setInt(5, entry.isDouble() ? 1 : 0);
            ps.setInt(6, entry.shulkerCount());
            ps.setLong(7, entry.timestamp());
            ps.executeUpdate();
        }

        // Items
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO items (x, y, z, item_id, quantity) VALUES (?,?,?,?,?)")) {
            for (var item : entry.items().entrySet()) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getY());
                ps.setInt(3, pos.getZ());
                ps.setString(4, item.getKey());
                ps.setInt(5, item.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Shulkers
        for (ShulkerDetail sd : entry.shulkerDetails()) {
            long shulkerId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO shulkers (x, y, z, shulker_type) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getY());
                ps.setInt(3, pos.getZ());
                ps.setString(4, sd.shulkerType());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    shulkerId = keys.getLong(1);
                }
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO shulker_items (shulker_id, item_id, quantity) VALUES (?,?,?)")) {
                for (var item : sd.contents().entrySet()) {
                    ps.setLong(1, shulkerId);
                    ps.setString(2, item.getKey());
                    ps.setInt(3, item.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private void deleteContainerAt(BlockPos pos) throws SQLException {
        // SQLite doesn't enforce FK cascades by default — delete manually
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM shulkers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM shulker_items WHERE shulker_id=?")) {
                    while (rs.next()) {
                        del.setLong(1, rs.getLong(1));
                        del.addBatch();
                    }
                    del.executeBatch();
                }
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM shulkers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM items WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM containers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.executeUpdate();
        }
    }

    /** Wipe all data from the database. */
    public void wipeAll() {
        if (!isOpen()) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM shulker_items");
            stmt.executeUpdate("DELETE FROM shulkers");
            stmt.executeUpdate("DELETE FROM items");
            stmt.executeUpdate("DELETE FROM containers");
            connection.commit();
            LOGGER.info("Stash database wiped");
        } catch (SQLException e) {
            LOGGER.error("Failed to wipe stash database", e);
            rollback();
        }
    }

    // ── Read operations ─────────────────────────────────────────────────

    /**
     * Load all container entries from the database into a map.
     * Returns an empty map on error.
     */
    public Map<BlockPos, ContainerEntry> loadAll() {
        Map<BlockPos, ContainerEntry> result = new LinkedHashMap<>();
        if (!isOpen()) return result;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT x, y, z, block_type, is_double, shulker_count, timestamp FROM containers")) {

            while (rs.next()) {
                BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                String blockType = rs.getString("block_type");
                boolean isDouble = rs.getInt("is_double") == 1;
                int shulkerCount = rs.getInt("shulker_count");
                long timestamp = rs.getLong("timestamp");

                Map<String, Integer> items = loadItems(pos);
                List<ShulkerDetail> shulkerDetails = loadShulkers(pos);

                result.put(pos, new ContainerEntry(
                        pos, blockType, isDouble, items,
                        shulkerCount, shulkerDetails, timestamp));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load containers from database", e);
        }
        return result;
    }

    private Map<String, Integer> loadItems(BlockPos pos) throws SQLException {
        Map<String, Integer> items = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_id, quantity FROM items WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getString("item_id"), rs.getInt("quantity"));
                }
            }
        }
        return Map.copyOf(items);
    }

    private List<ShulkerDetail> loadShulkers(BlockPos pos) throws SQLException {
        List<ShulkerDetail> shulkers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, shulker_type FROM shulkers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String type = rs.getString("shulker_type");
                    Map<String, Integer> contents = loadShulkerItems(id);
                    shulkers.add(new ShulkerDetail(type, contents));
                }
            }
        }
        return List.copyOf(shulkers);
    }

    private Map<String, Integer> loadShulkerItems(long shulkerId) throws SQLException {
        Map<String, Integer> items = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_id, quantity FROM shulker_items WHERE shulker_id=?")) {
            ps.setLong(1, shulkerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getString("item_id"), rs.getInt("quantity"));
                }
            }
        }
        return Map.copyOf(items);
    }

    /**
     * Search for all containers that hold a specific item.
     * Returns a list of (ContainerEntry, quantity) pairs sorted by quantity descending.
     */
    public List<SearchResult> searchItem(String itemIdFragment) {
        List<SearchResult> results = new ArrayList<>();
        if (!isOpen()) return results;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT i.x, i.y, i.z, i.item_id, i.quantity " +
                "FROM items i WHERE i.item_id LIKE ?")) {
            ps.setString(1, "%" + itemIdFragment + "%");
            try (ResultSet rs = ps.executeQuery()) {
                // group by position
                Map<BlockPos, Map<String, Integer>> posItems = new LinkedHashMap<>();
                while (rs.next()) {
                    BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    posItems.computeIfAbsent(pos, k -> new HashMap<>())
                            .merge(rs.getString("item_id"), rs.getInt("quantity"), Integer::sum);
                }

                for (var entry : posItems.entrySet()) {
                    int totalQty = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
                    results.add(new SearchResult(entry.getKey(), entry.getValue(), totalQty));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to search stash database", e);
        }

        results.sort(Comparator.comparingInt(SearchResult::totalQuantity).reversed());
        return results;
    }

    /** Result of an item search against the database. */
    public record SearchResult(BlockPos pos, Map<String, Integer> matchedItems, int totalQuantity) {}

    /** Count total containers stored in the database. */
    public int countContainers() {
        if (!isOpen()) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM containers")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to count containers", e);
            return 0;
        }
    }

    /** Count total unique item types stored in the database. */
    public int countItemTypes() {
        if (!isOpen()) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(DISTINCT item_id) FROM items")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to count item types", e);
            return 0;
        }
    }

    /** Sum total item quantity across all containers. */
    public int countTotalItems() {
        if (!isOpen()) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(quantity), 0) FROM items")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to count total items", e);
            return 0;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void rollback() {
        try {
            if (connection != null) connection.rollback();
        } catch (SQLException e) {
            LOGGER.error("Rollback failed", e);
        }
    }
}
