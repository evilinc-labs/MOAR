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
import net.minecraft.core.Vec3i;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Perf: detection reflects into Litematica and reparses config JSON from
    // disk. Callers poll it from periodic tick validation (~every 100t), so a
    // short TTL cache avoids re-reading unchanged files. 2s staleness is
    // acceptable for every call site (user commands + periodic checks).
    private static final long DETECT_CACHE_TTL_MS = 2000;
    private static List<DetectedPlacement> cachedPlacements;
    private static long cachedPlacementsExpiryMs;

    // Return enabled placements. Prefer reflection, then JSON.
    public static List<DetectedPlacement> detectPlacements() {
        long now = System.currentTimeMillis();
        if (cachedPlacements != null && now < cachedPlacementsExpiryMs) {
            return cachedPlacements;
        }
        String currentContext = getCurrentPlacementContext();
        String currentDimension = getCurrentDimensionSuffix();
        List<DetectedPlacement> live = detectFromMemory();
        List<DetectedPlacement> configPlacements = detectFromConfig(currentContext, currentDimension);
        // Exact server-address match can miss (connect.2b2t.org vs 2b2t.org).
        // If that yields nothing, accept any placement file for this dimension.
        if (configPlacements.isEmpty() && currentContext != null) {
            configPlacements = detectFromConfig(null, currentDimension);
        }

        // Live Litematica state is the source of truth while in-game. Config
        // JSON is only flushed on save/logout, so a placement you just created
        // exists in memory first. Prefer live whenever it has results.
        List<DetectedPlacement> result = !live.isEmpty() ? live : configPlacements;
        cachedPlacements = result;
        cachedPlacementsExpiryMs = now + DETECT_CACHE_TTL_MS;
        return result;
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
                try {
                    DetectedPlacement detected = readLivePlacement(p);
                    if (detected != null) {
                        results.add(detected);
                        LOGGER.info("Live-detected Litematica placement: '{}' at ({}, {}, {}) file={} rotation={} mirror={} modifiedSubRegions={}",
                                detected.name(), detected.originX(), detected.originY(), detected.originZ(),
                                detected.schematicPath(), detected.rotation(), detected.mirror(),
                                detected.modifiedSubRegionCount());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed reading one Litematica placement: {}", e.toString());
                }
            }
        } catch (ClassNotFoundException e) {
            // Litematica is optional.
            LOGGER.debug("Litematica classes not found — reflection detection unavailable");
        } catch (Exception e) {
            LOGGER.warn("Litematica live detection failed: {}", e.toString());
        }
        return results;
    }

    private static DetectedPlacement readLivePlacement(Object placement) throws Exception {
        Class<?> pClass = placement.getClass();

        boolean enabled;
        try {
            enabled = (boolean) pClass.getMethod("isEnabled").invoke(placement);
        } catch (NoSuchMethodException e) {
            enabled = true;
        }
        if (!enabled) return null;

        // Cast to Vec3i so Loom remaps getX/Y/Z. String reflection on
        // BlockPos looks for Yarn names and fails at runtime on intermediary.
        Object origin = pClass.getMethod("getOrigin").invoke(placement);
        BlockPos originPos = readBlockPos(origin);
        if (originPos == null) {
            LOGGER.warn("Could not read Litematica placement origin from {}",
                    origin == null ? "null" : origin.getClass().getName());
            return null;
        }

        Path schematicPath = readSchematicPath(pClass, placement);
        if (schematicPath == null) return null;
        if (!schematicPath.toString().endsWith(".litematic")) return null;

        if (!Files.exists(schematicPath)) {
            LOGGER.warn("Litematica placement '{}' file not on disk: {} — including for origin only",
                    schematicPath.getFileName(), schematicPath);
        }

        String name;
        try {
            name = (String) pClass.getMethod("getName").invoke(placement);
        } catch (NoSuchMethodException e) {
            name = schematicPath.getFileName().toString();
        }
        if (name == null || name.isBlank()) {
            name = schematicPath.getFileName().toString();
        }

        String rotation = getEnumName(pClass, placement, "getRotation", "NONE");
        String mirror = getEnumName(pClass, placement, "getMirror", "NONE");
        int modifiedSubRegions = countModifiedSubRegions(pClass, placement);

        return new DetectedPlacement(
                schematicPath, name,
                originPos.getX(), originPos.getY(), originPos.getZ(),
                rotation, mirror, modifiedSubRegions);
    }

    private static BlockPos readBlockPos(Object origin) {
        if (origin instanceof BlockPos pos) {
            return pos;
        }
        if (origin instanceof Vec3i vec) {
            return new BlockPos(vec.getX(), vec.getY(), vec.getZ());
        }
        if (origin == null) return null;
        try {
            int x = ((Number) origin.getClass().getMethod("getX").invoke(origin)).intValue();
            int y = ((Number) origin.getClass().getMethod("getY").invoke(origin)).intValue();
            int z = ((Number) origin.getClass().getMethod("getZ").invoke(origin)).intValue();
            return new BlockPos(x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    private static Path readSchematicPath(Class<?> placementClass, Object placement) {
        Path path = toPath(invokeQuiet(placementClass, placement, "getSchematicFile"));
        if (path == null) {
            Object schematic = invokeQuiet(placementClass, placement, "getSchematic");
            if (schematic != null) {
                Class<?> schematicClass = schematic.getClass();
                for (String method : new String[] {"getFile", "getCanonicalFile", "getPath", "getSchematicFile"}) {
                    path = toPath(invokeQuiet(schematicClass, schematic, method));
                    if (path != null) break;
                }
            }
        }
        if (path == null) {
            String name = null;
            Object rawName = invokeQuiet(placementClass, placement, "getName");
            if (rawName instanceof String str && !str.isBlank()) {
                name = str;
            }
            if (name != null) {
                String fileName = name.endsWith(".litematic") ? name : name + ".litematic";
                Path guess = FabricLoader.getInstance().getGameDir()
                        .resolve("schematics").resolve(fileName);
                if (Files.exists(guess)) {
                    path = guess;
                }
            }
        }
        return path == null ? null : path.normalize();
    }

    private static Path toPath(Object obj) {
        if (obj instanceof Path path) return path;
        if (obj instanceof File file) return file.toPath();
        if (obj instanceof String str && !str.isBlank()) return Path.of(str);
        return null;
    }

    private static Object invokeQuiet(Class<?> type, Object target, String methodName) {
        try {
            return type.getMethod(methodName).invoke(target);
        } catch (Exception e) {
            return null;
        }
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
        var server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip;
        }
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        var server = mc.getCurrentServerEntry();
        if (server != null && server.address != null && !server.address.isBlank()) {
            return server.address;
        }
        /*?}*/

        String singleplayerContext = extractSingleplayerContext(mc);
        if (singleplayerContext != null && !singleplayerContext.isBlank()) {
            return singleplayerContext;
        }

        return extractMultiplayerContext(mc);
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

        List<BlockPos> hologramBlocks = new ArrayList<>();
        List<BlockState> hologramStates = new ArrayList<>();
        collectHologramSamples(schematicWorld, playerPos, scanRadius, hologramBlocks, hologramStates);

        if (hologramBlocks.isEmpty()) {
            LOGGER.info("No hologram blocks found within {} blocks of player", scanRadius);
            return null;
        }

        LOGGER.info("Found {} hologram blocks near player — correlating with schematic", hologramBlocks.size());

        int sampleCount = hologramBlocks.size();
        int minimumScore = Math.max(4, (sampleCount * 3 + 3) / 4);

        // Prefer a live/config Litematica origin that matches the hologram.
        // Repetitive builds (thousands of the same block) cannot uniquely
        // invert origin from samples alone.
        BlockPos knownMatch = matchKnownPlacementOrigin(schematic, hologramBlocks, hologramStates);
        if (knownMatch != null) {
            return knownMatch;
        }

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
                            int sx = region.originX + x;
                            int sy = region.originY + y;
                            int sz = region.originZ + z;
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

        BlockPos bestAnchor = null;
        int bestScore = 0;
        int secondBestScore = 0;
        int bestScoreTies = 0;
        List<BlockPos> topAnchors = new ArrayList<>();

        for (BlockPos candidate : candidates) {
            int score = scoreAnchor(candidate, hologramBlocks, hologramStates, schematic);
            if (score > bestScore) {
                secondBestScore = bestScore;
                bestScore = score;
                bestAnchor = candidate;
                bestScoreTies = 1;
                topAnchors.clear();
                topAnchors.add(candidate);
            } else if (score == bestScore) {
                bestScoreTies++;
                topAnchors.add(candidate);
            } else if (score > secondBestScore) {
                secondBestScore = score;
            }
        }

        if (bestAnchor == null || bestScore < minimumScore) {
            LOGGER.warn("SchematicWorld anchor confidence too low: best score {}/{}"
                    + " below minimum {} ({} candidates)",
                    bestScore, sampleCount, minimumScore, candidates.size());
            return null;
        }
        if (bestScoreTies > 1 && bestScore - secondBestScore <= 1) {
            BlockPos disambiguated = pickKnownOrigin(topAnchors, schematic);
            if (disambiguated != null) {
                LOGGER.info("Anchor correlated from SchematicWorld via Litematica origin: {} (score {}/{})",
                        disambiguated, bestScore, sampleCount);
                return disambiguated;
            }
            LOGGER.warn("SchematicWorld anchor ambiguous: {} candidates tied at {}/{}",
                    bestScoreTies, bestScore, sampleCount);
            return null;
        }

        LOGGER.info("Anchor correlated from SchematicWorld: {} (score {}/{})",
                bestAnchor, bestScore, sampleCount);
        return bestAnchor;
    }

    /*? if >=26.1 {*//*
    private static void collectHologramSamples(Level schematicWorld, BlockPos playerPos, int scanRadius,
    *//*?} else {*/
    private static void collectHologramSamples(World schematicWorld, BlockPos playerPos, int scanRadius,
    /*?}*/
                                               List<BlockPos> hologramBlocks, List<BlockState> hologramStates) {
        Map<BlockState, Integer> perState = new HashMap<>();
        int maxPerState = 2;
        int maxSamples = 24;
        int[] strides = {8, 4, 1};
        for (int stride : strides) {
            if (hologramBlocks.size() >= maxSamples) break;
            for (int dy = -scanRadius; dy <= scanRadius && hologramBlocks.size() < maxSamples; dy += stride) {
                for (int dx = -scanRadius; dx <= scanRadius && hologramBlocks.size() < maxSamples; dx += stride) {
                    for (int dz = -scanRadius; dz <= scanRadius && hologramBlocks.size() < maxSamples; dz += stride) {
                        /*? if >=26.1 {*//*
                        BlockPos wp = playerPos.offset(dx, dy, dz);
                        *//*?} else {*/
                        BlockPos wp = playerPos.add(dx, dy, dz);
                        /*?}*/
                        BlockState bs = schematicWorld.getBlockState(wp);
                        if (bs.isAir()) continue;
                        if (perState.getOrDefault(bs, 0) >= maxPerState) continue;
                        hologramBlocks.add(wp);
                        hologramStates.add(bs);
                        perState.merge(bs, 1, Integer::sum);
                    }
                }
            }
        }
    }

    private static int scoreAnchor(BlockPos candidate, List<BlockPos> hologramBlocks,
                                   List<BlockState> hologramStates, LitematicaSchematic schematic) {
        int score = 0;
        for (int i = 0; i < hologramBlocks.size(); i++) {
            BlockPos wp = hologramBlocks.get(i);
            BlockState expected = hologramStates.get(i);
            int sx = wp.getX() - candidate.getX();
            int sy = wp.getY() - candidate.getY();
            int sz = wp.getZ() - candidate.getZ();
            if (schematic.getBlockState(sx, sy, sz).equals(expected)) {
                score++;
            }
        }
        return score;
    }

    private static BlockPos matchKnownPlacementOrigin(LitematicaSchematic schematic,
                                                      List<BlockPos> hologramBlocks,
                                                      List<BlockState> hologramStates) {
        BlockPos best = null;
        int bestScore = -1;
        int ties = 0;
        for (DetectedPlacement placement : detectPlacements()) {
            if (placement.hasUnsupportedTransform()) continue;
            BlockPos candidate = new BlockPos(
                    placement.originX() + schematic.getOriginOffsetX(),
                    placement.originY() + schematic.getOriginOffsetY(),
                    placement.originZ() + schematic.getOriginOffsetZ());
            int score = scoreAnchor(candidate, hologramBlocks, hologramStates, schematic);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                ties = 1;
            } else if (score == bestScore) {
                ties++;
            }
        }
        if (best != null && ties == 1 && bestScore >= hologramBlocks.size() && !hologramBlocks.isEmpty()) {
            LOGGER.info("Anchor matched known Litematica origin: {} (score {}/{})",
                    best, bestScore, hologramBlocks.size());
            return best;
        }
        return null;
    }

    private static BlockPos pickKnownOrigin(List<BlockPos> topAnchors, LitematicaSchematic schematic) {
        for (DetectedPlacement placement : detectPlacements()) {
            if (placement.hasUnsupportedTransform()) continue;
            BlockPos expected = new BlockPos(
                    placement.originX() + schematic.getOriginOffsetX(),
                    placement.originY() + schematic.getOriginOffsetY(),
                    placement.originZ() + schematic.getOriginOffsetZ());
            for (BlockPos candidate : topAnchors) {
                if (candidate.equals(expected)) {
                    return candidate;
                }
            }
        }
        return null;
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
