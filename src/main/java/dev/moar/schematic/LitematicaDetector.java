package dev.moar.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.BlockState;
*//*?} else {*/
import net.minecraft.block.BlockState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Read Litematica placements via reflection or JSON fallback.
public final class LitematicaDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Detector");

    private LitematicaDetector() {}

    // One active Litematica placement.
    public record DetectedPlacement(
            Path schematicPath,
            String name,
            int originX, int originY, int originZ,
            String rotation,
            String mirror,
            int modifiedSubRegionCount
    ) {
        public boolean hasTopLevelTransform() {
            return !isIdentityTransform(rotation) || !isIdentityTransform(mirror);
        }

        public boolean hasModifiedSubRegions() {
            return modifiedSubRegionCount > 0;
        }

        public boolean hasUnsupportedTransform() {
            return hasTopLevelTransform() || hasModifiedSubRegions();
        }

        public String unsupportedTransformSummary() {
            List<String> parts = new ArrayList<>();
            if (!isIdentityTransform(rotation)) {
                parts.add("rotation=" + rotation);
            }
            if (!isIdentityTransform(mirror)) {
                parts.add("mirror=" + mirror);
            }
            if (modifiedSubRegionCount > 0) {
                parts.add(modifiedSubRegionCount + " modified sub-region"
                        + (modifiedSubRegionCount == 1 ? "" : "s"));
            }
            return parts.isEmpty() ? "identity placement" : String.join(", ", parts);
        }

        private static boolean isIdentityTransform(String value) {
            return value == null || "NONE".equals(value);
        }
    }

    // Return enabled placements. Prefer reflection, then JSON.
    public static List<DetectedPlacement> detectPlacements() {
        String currentContext = getCurrentPlacementContext();
        String currentDimension = getCurrentDimensionSuffix();
        List<DetectedPlacement> live = detectFromMemory();
        List<DetectedPlacement> configPlacements = detectFromConfig(currentContext, currentDimension);

        // Prefer current-world config files when available. They are scoped to
        // the active server/world, which avoids stale placements lingering in
        // Litematica's in-memory manager after a singleplayer world or server
        // switch. Fall back to live data when no scoped config exists yet.
        if (!configPlacements.isEmpty()) {
            return configPlacements;
        }
        if (!live.isEmpty()) {
            return live;
        }
        return configPlacements;
    }

    private static List<DetectedPlacement> detectFromConfig(String currentContext, String currentDimension) {
        List<DetectedPlacement> results = new ArrayList<>();

        Path configDir = FabricLoader.getInstance().getGameDir()
                .resolve("config").resolve("litematica");

        if (!Files.isDirectory(configDir)) {
            LOGGER.debug("Litematica config directory not found: {}", configDir);
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "litematica_*_dim_*.json")) {
            for (Path jsonFile : stream) {
                if (!matchesCurrentContext(jsonFile, currentContext, currentDimension)) {
                    continue;
                }
                results.addAll(parsePlacementFile(jsonFile));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan Litematica config directory", e);
        }

        return results;
    }

    // Return the first detected enabled placement, or null.
    public static DetectedPlacement detectFirst() {
        List<DetectedPlacement> all = detectPlacements();
        return all.isEmpty() ? null : all.get(0);
    }

    // Internals.

    // Read live placements via reflection.
    private static List<DetectedPlacement> detectFromMemory() {
        List<DetectedPlacement> results = new ArrayList<>();
        try {
            Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object placementMgr = dataManager.getMethod("getSchematicPlacementManager")
                    .invoke(null);
            @SuppressWarnings("unchecked")
            List<?> placements = (List<?>) placementMgr.getClass()
                    .getMethod("getAllSchematicsPlacements")
                    .invoke(placementMgr);

            for (Object p : placements) {
                Class<?> pClass = p.getClass();

                boolean enabled;
                try {
                    enabled = (boolean) pClass.getMethod("isEnabled").invoke(p);
                } catch (NoSuchMethodException e) {
                    enabled = true;
                }
                if (!enabled) continue;

                // Read the placement origin.
                Object origin = pClass.getMethod("getOrigin").invoke(p);
                int ox = (int) origin.getClass().getMethod("getX").invoke(origin);
                int oy = (int) origin.getClass().getMethod("getY").invoke(origin);
                int oz = (int) origin.getClass().getMethod("getZ").invoke(origin);

                // Read the backing .litematic path.
                java.io.File schematicFile = (java.io.File) pClass
                        .getMethod("getSchematicFile").invoke(p);
                if (schematicFile == null) continue;
                Path schematicPath = schematicFile.toPath().normalize();
                if (!schematicPath.toString().endsWith(".litematic")) continue;

                // Keep the origin even if the file is gone.
                if (!Files.exists(schematicPath)) {
                    LOGGER.warn("Litematica placement '{}' file not on disk: {} — including for origin only",
                            schematicPath.getFileName(), schematicPath);
                }

                String name;
                try {
                    name = (String) pClass.getMethod("getName").invoke(p);
                } catch (NoSuchMethodException e) {
                    name = schematicPath.getFileName().toString();
                }

                String rotation = getEnumName(pClass, p, "getRotation", "NONE");
                String mirror = getEnumName(pClass, p, "getMirror", "NONE");
                int modifiedSubRegions = countModifiedSubRegions(pClass, p);

                results.add(new DetectedPlacement(
                        schematicPath, name, ox, oy, oz, rotation, mirror, modifiedSubRegions));
                LOGGER.info("Live-detected Litematica placement: '{}' at ({}, {}, {}) file={} rotation={} mirror={} modifiedSubRegions={}",
                        name, ox, oy, oz, schematicPath, rotation, mirror, modifiedSubRegions);
            }
        } catch (ClassNotFoundException e) {
            // Litematica is optional.
            LOGGER.debug("Litematica classes not found — reflection detection unavailable");
        } catch (Exception e) {
            LOGGER.debug("Litematica reflection detection failed: {}", e.getMessage());
        }
        return results;
    }

    private static List<DetectedPlacement> parsePlacementFile(Path jsonFile) {
        List<DetectedPlacement> results = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (!root.has("placements")) return results;
            JsonObject placementsObj = root.getAsJsonObject("placements");

            if (!placementsObj.has("placements")) return results;
            JsonArray arr = placementsObj.getAsJsonArray("placements");

            for (JsonElement elem : arr) {
                if (!elem.isJsonObject()) continue;
                JsonObject entry = elem.getAsJsonObject();

                if (entry.has("enabled") && !entry.get("enabled").getAsBoolean()) continue;

                if (!entry.has("schematic")) continue;
                String schematicStr = entry.get("schematic").getAsString();
                Path schematicPath = Path.of(schematicStr).normalize();

                // Ignore non-schematic files.
                if (!schematicPath.toString().endsWith(".litematic")) {
                    LOGGER.warn("Skipping placement — not a .litematic file: {}", schematicStr);
                    continue;
                }

                if (!Files.exists(schematicPath)) {
                    LOGGER.debug("Skipping placement — schematic file not found: {}", schematicStr);
                    continue;
                }

                String name = entry.has("name") ? entry.get("name").getAsString() : "Unknown";

                if (!entry.has("origin")) continue;
                JsonArray origin = entry.getAsJsonArray("origin");
                if (origin.size() < 3) continue;

                int ox = origin.get(0).getAsInt();
                int oy = origin.get(1).getAsInt();
                int oz = origin.get(2).getAsInt();

                String rotation = entry.has("rotation")
                        ? entry.get("rotation").getAsString()
                        : "NONE";
                String mirror = entry.has("mirror")
                        ? entry.get("mirror").getAsString()
                        : "NONE";
                int modifiedSubRegions = countModifiedSubRegions(entry);

                results.add(new DetectedPlacement(
                        schematicPath, name, ox, oy, oz, rotation, mirror, modifiedSubRegions));
                LOGGER.debug("Detected Litematica placement: '{}' at ({}, {}, {}) from {} rotation={} mirror={} modifiedSubRegions={}",
                        name, ox, oy, oz, schematicPath.getFileName(), rotation, mirror, modifiedSubRegions);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Litematica placement file: {}", jsonFile.getFileName(), e);
        }

        return results;
    }

    private static boolean matchesCurrentContext(Path jsonFile, String currentContext, String currentDimension) {
        String fileName = jsonFile.getFileName().toString();

        if (currentDimension != null) {
            String expectedSuffix = "_dim_" + currentDimension + ".json";
            if (!fileName.endsWith(expectedSuffix)) {
                return false;
            }
        }

        if (currentContext != null) {
            String expectedPrefix = "litematica_" + currentContext + "_dim_";
            return fileName.startsWith(expectedPrefix);
        }

        return true;
    }

    private static String getCurrentPlacementContext() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        /*?}*/

        String singleplayerContext = extractSingleplayerContext(mc);
        if (singleplayerContext != null && !singleplayerContext.isBlank()) {
            return singleplayerContext;
        }

        String multiplayerContext = extractMultiplayerContext(mc);
        if (multiplayerContext != null && !multiplayerContext.isBlank()) {
            return multiplayerContext;
        }

        return null;
    }

    private static String extractSingleplayerContext(Object mc) {
        Object server = invokeNoArg(mc, "getSingleplayerServer");
        if (server == null) {
            server = invokeNoArg(mc, "getServer");
        }
        if (server == null) {
            return null;
        }

        String levelName = extractString(invokeNoArg(server, "getWorldData"), "getLevelName");
        if (levelName != null && !levelName.isBlank()) {
            return levelName;
        }

        return extractString(invokeNoArg(server, "getSaveProperties"), "getLevelName");
    }

    private static String extractMultiplayerContext(Object mc) {
        Object serverEntry = invokeNoArg(mc, "getCurrentServerEntry");
        if (serverEntry == null) {
            serverEntry = invokeNoArg(mc, "getCurrentServer");
        }
        if (serverEntry == null) {
            return null;
        }

        String address = extractFieldOrGetter(serverEntry, "address", "getAddress");
        if (address != null && !address.isBlank()) {
            return address;
        }

        String ip = extractFieldOrGetter(serverEntry, "ip", "getIp");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }

        return extractFieldOrGetter(serverEntry, "name", "getName");
    }

    private static String getCurrentDimensionSuffix() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.dimension().identifier().toString().replace(':', '_');
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        return mc.world.getRegistryKey().getValue().toString().replace(':', '_');
        /*?}*/
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractString(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof String str && !str.isBlank() ? str : null;
    }

    private static String extractFieldOrGetter(Object target, String fieldName, String getterName) {
        if (target == null) return null;
        try {
            Object value = target.getClass().getField(fieldName).get(target);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        } catch (Exception ignored) {
        }

        Object viaGetter = invokeNoArg(target, getterName);
        return viaGetter instanceof String str && !str.isBlank() ? str : null;
    }

    // Correlate anchors from SchematicWorld.

    // Detect anchor by reading blocks from Litematica's SchematicWorld.
    // Scans near the player, correlates against the schematic to compute
    // the anchor offset. Returns null if detection fails.
    public static BlockPos detectAnchorFromSchematicWorld(LitematicaSchematic schematic) {
        if (schematic == null) return null;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return null;

        // Read SchematicWorld via reflection.
        /*? if >=26.1 {*//*
        Level schematicWorld;
        *//*?} else {*/
        World schematicWorld;
        /*?}*/
        try {
            Class<?> swh = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
            Object world = swh.getMethod("getSchematicWorld").invoke(null);
            if (world == null) {
                LOGGER.debug("SchematicWorld is null — no schematic loaded in Litematica");
                return null;
            }
            /*? if >=26.1 {*//*
            if (!(world instanceof Level)) {
            *//*?} else {*/
            if (!(world instanceof World)) {
            /*?}*/
                /*? if >=26.1 {*//*
                LOGGER.warn("SchematicWorld is not a Level instance: {}", world.getClass());
                *//*?} else {*/
                LOGGER.warn("SchematicWorld is not a World instance: {}", world.getClass());
                /*?}*/
                return null;
            }
            /*? if >=26.1 {*//*
            schematicWorld = (Level) world;
            *//*?} else {*/
            schematicWorld = (World) world;
            /*?}*/
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Litematica SchematicWorldHandler not found");
            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to access SchematicWorld: {}", e.getMessage());
            return null;
        }

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/
        int scanRadius = 64;

        // Sample nearby hologram blocks.
        List<BlockPos> hologramBlocks = new ArrayList<>();
        List<BlockState> hologramStates = new ArrayList<>();

        for (int dy = -scanRadius; dy <= scanRadius && hologramBlocks.size() < 20; dy++) {
            for (int dx = -scanRadius; dx <= scanRadius && hologramBlocks.size() < 20; dx++) {
                for (int dz = -scanRadius; dz <= scanRadius && hologramBlocks.size() < 20; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos wp = playerPos.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos wp = playerPos.add(dx, dy, dz);
                    /*?}*/
                    BlockState bs = schematicWorld.getBlockState(wp);
                    if (!bs.isAir()) {
                        hologramBlocks.add(wp);
                        hologramStates.add(bs);
                    }
                }
            }
        }

        if (hologramBlocks.isEmpty()) {
            LOGGER.info("No hologram blocks found within {} blocks of player", scanRadius);
            return null;
        }

        LOGGER.info("Found {} hologram blocks near player — correlating with schematic", hologramBlocks.size());

        // Build anchor candidates from the first match.
        BlockPos firstWorld = hologramBlocks.get(0);
        BlockState firstState = hologramStates.get(0);

        List<BlockPos> candidates = new ArrayList<>();
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState rs = region.getBlockState(x, y, z);
                        if (rs.equals(firstState)) {
                            // Map back to schematic-local space.
                            int sx = region.originX + x;
                            int sy = region.originY + y;
                            int sz = region.originZ + z;
                            // Convert to an anchor candidate.
                            candidates.add(new BlockPos(
                                    firstWorld.getX() - sx,
                                    firstWorld.getY() - sy,
                                    firstWorld.getZ() - sz));
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            LOGGER.warn("No schematic position matches hologram block {} at {}",
                    firstState, firstWorld);
            return null;
        }

        // Keep the best-matching candidate.
        BlockPos bestAnchor = null;
        int bestScore = 0;

        for (BlockPos candidate : candidates) {
            int score = 0;
            for (int i = 0; i < hologramBlocks.size(); i++) {
                BlockPos wp = hologramBlocks.get(i);
                BlockState expected = hologramStates.get(i);
                int sx = wp.getX() - candidate.getX();
                int sy = wp.getY() - candidate.getY();
                int sz = wp.getZ() - candidate.getZ();
                BlockState schematicState = schematic.getBlockState(sx, sy, sz);
                if (schematicState.equals(expected)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestAnchor = candidate;
            }
        }

        if (bestAnchor != null) {
            LOGGER.info("Anchor correlated from SchematicWorld: {} (score {}/{})",
                    bestAnchor, bestScore, hologramBlocks.size());
        }
        return bestAnchor;
    }

    private static String getEnumName(Class<?> targetClass, Object target,
                                      String methodName, String fallback) {
        try {
            Object value = targetClass.getMethod(methodName).invoke(target);
            if (value instanceof Enum<?> enumValue) {
                return enumValue.name();
            }
            return value != null ? value.toString() : fallback;
        } catch (NoSuchMethodException e) {
            return fallback;
        } catch (Exception e) {
            LOGGER.debug("Failed reading Litematica placement {}: {}", methodName, e.getMessage());
            return fallback;
        }
    }

    private static int countModifiedSubRegions(Class<?> placementClass, Object placement) {
        try {
            Object raw = placementClass.getMethod("getAllSubRegionsPlacements").invoke(placement);
            if (!(raw instanceof Iterable<?> subRegions)) return 0;

            int modified = 0;
            for (Object subRegion : subRegions) {
                if (subRegion != null && isModifiedSubRegion(subRegion)) {
                    modified++;
                }
            }
            return modified;
        } catch (NoSuchMethodException e) {
            return 0;
        } catch (Exception e) {
            LOGGER.debug("Failed reading Litematica sub-region placement metadata: {}", e.getMessage());
            return 0;
        }
    }

    private static boolean isModifiedSubRegion(Object subRegion) {
        Class<?> subRegionClass = subRegion.getClass();
        try {
            return (boolean) subRegionClass.getMethod("isRegionPlacementModifiedFromDefault")
                    .invoke(subRegion);
        } catch (NoSuchMethodException ignored) {
            // Fall back to field checks.
        } catch (Exception e) {
            LOGGER.debug("Failed checking sub-region modification flag: {}", e.getMessage());
        }

        String rotation = getEnumName(subRegionClass, subRegion, "getRotation", "NONE");
        String mirror = getEnumName(subRegionClass, subRegion, "getMirror", "NONE");
        if (!"NONE".equals(rotation) || !"NONE".equals(mirror)) {
            return true;
        }

        try {
            Object pos = subRegionClass.getMethod("getPos").invoke(subRegion);
            Object defaultPos = subRegionClass.getMethod("getDefaultPos").invoke(subRegion);
            return pos != null && !pos.equals(defaultPos);
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            LOGGER.debug("Failed comparing sub-region positions: {}", e.getMessage());
            return false;
        }
    }

    private static int countModifiedSubRegions(JsonObject entry) {
        if (!entry.has("placements")) return 0;

        JsonArray placements = entry.getAsJsonArray("placements");
        int modified = 0;
        for (JsonElement elem : placements) {
            if (!elem.isJsonObject()) continue;
            JsonObject placementEntry = elem.getAsJsonObject();
            if (!placementEntry.has("placement") || !placementEntry.get("placement").isJsonObject()) continue;

            JsonObject subPlacement = placementEntry.getAsJsonObject("placement");
            String rotation = subPlacement.has("rotation")
                    ? subPlacement.get("rotation").getAsString()
                    : "NONE";
            String mirror = subPlacement.has("mirror")
                    ? subPlacement.get("mirror").getAsString()
                    : "NONE";

            if (!"NONE".equals(rotation) || !"NONE".equals(mirror)) {
                modified++;
            }
        }
        return modified;
    }
}
