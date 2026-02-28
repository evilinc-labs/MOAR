package dev.litematicaprinter.printer;

import dev.litematicaprinter.schematic.LitematicaDetector;
import dev.litematicaprinter.schematic.LitematicaSchematic;
import dev.litematicaprinter.schematic.ChestIndexer;
import dev.litematicaprinter.schematic.PrinterCheckpoint;
import dev.litematicaprinter.schematic.PrinterResourceManager;
import dev.litematicaprinter.util.ChatHelper;
import dev.litematicaprinter.util.PathWalker;
import dev.litematicaprinter.util.PlacementEngine;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Automatically places blocks from a loaded {@code .litematic} schematic.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Manual (AutoBuild OFF)</b> — places blocks only within reach;
 *       the player walks themselves.</li>
 *   <li><b>AutoBuild (ON)</b> — full automation: walks to the next build zone,
 *       places blocks, walks to supply chests when inventory runs low,
 *       takes needed items, walks back, and resumes building.</li>
 * </ul>
 *
 * <p>Load/unload and position commands are handled by
 * {@link dev.litematicaprinter.command.PrinterCommand PrinterCommand}.
 */
public class SchematicPrinter {

    // ── enums ───────────────────────────────────────────────────────────

    public enum SortMode {
        NEAREST,
        BOTTOM_UP
    }

    /** AutoBuild state machine states. */
    public enum AutoState {
        BUILDING,
        WALKING_TO_BUILD,
        WALKING_TO_SUPPLY,
        RESTOCKING,
        WALKING_BACK,
        IDLE
    }

    // ── settings (simple fields, configurable via commands) ─────────────

    private int bps = 8;
    private double range = 4.5;
    private boolean swapItems = true;
    private boolean printInAir = false;
    private SortMode sortMode = SortMode.BOTTOM_UP;
    private boolean statusMessages = true;
    private boolean autoBuild = false;

    // ── state ───────────────────────────────────────────────────────────

    private boolean enabled = false;

    // ── schematic state ─────────────────────────────────────────────────

    private LitematicaSchematic schematic;
    private BlockPos anchor;
    private int blocksPlaced;
    private String schematicFile;

    // ── auto-build state ────────────────────────────────────────────────

    private AutoState autoState = AutoState.IDLE;
    private BlockPos lastBuildPos;
    private BlockPos supplyTarget;
    private List<String> neededItems;
    private int restockWaitTicks;
    private int idleScanCooldown;
    private int noProgressTicks;

    private static final int RESTOCK_THRESHOLD = 16;
    private static final int CHEST_OPEN_TIMEOUT = 40;
    private static final int IDLE_SCAN_INTERVAL = 60;
    private static final int NO_PROGRESS_TIMEOUT = 40;

    // ── toggle / lifecycle ──────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public void toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !this.enabled) enable();
        else if (!enabled && this.enabled) disable();
    }

    private void enable() {
        enabled = true;

        // auto-detect from Litematica if nothing loaded
        if (schematic == null || anchor == null) {
            if (tryAutoDetect()) {
                ChatHelper.labelled("Printer", "§aAuto-detected §f" + schematic.getName()
                        + " §7(" + schematic.getTotalNonAir() + " blocks)");
            } else {
                ChatHelper.info("§cNo schematic loaded. Use /printer load <file> or load one in Litematica.");
            }
        }

        if (schematic != null && anchor != null) {
            ChatHelper.info("Printing §a" + schematic.getName()
                    + "§f (" + schematic.getTotalNonAir() + " blocks)");
            ChatHelper.info("Anchor: §e" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());
            if (autoBuild) {
                ChatHelper.info("§bAutoBuild §aenabled §7— walk + restock is automatic.");
            }
        }

        blocksPlaced = 0;
        autoState = AutoState.BUILDING;
        noProgressTicks = 0;
        idleScanCooldown = 0;
    }

    private void disable() {
        enabled = false;
        saveCheckpoint();
        PathWalker.stop();
        autoState = AutoState.IDLE;

        if (statusMessages) {
            ChatHelper.info("Stopped. §e" + blocksPlaced + "§f blocks placed this session.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LITEMATICA AUTO-DETECTION
    // ═══════════════════════════════════════════════════════════════════

    public boolean tryAutoDetect() {
        LitematicaDetector.DetectedPlacement placement = LitematicaDetector.detectFirst();
        if (placement == null) return false;

        try {
            this.schematic = LitematicaSchematic.load(placement.schematicPath());
            this.anchor = new BlockPos(placement.originX(), placement.originY(), placement.originZ());
            this.blocksPlaced = 0;
            this.schematicFile = placement.schematicPath().getFileName().toString();
            return true;
        } catch (IOException e) {
            ChatHelper.info("§cFailed to load detected schematic: " + e.getMessage());
            return false;
        }
    }

    public static List<LitematicaDetector.DetectedPlacement> detectAllPlacements() {
        return LitematicaDetector.detectPlacements();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  SCHEMATIC MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    public void loadSchematic(Path path, BlockPos anchor) throws IOException {
        this.schematic = LitematicaSchematic.load(path);
        this.anchor = anchor;
        this.blocksPlaced = 0;
        this.schematicFile = path.getFileName().toString();
    }

    public void unload() {
        this.schematic = null;
        this.anchor = null;
        this.blocksPlaced = 0;
        this.schematicFile = null;
        PrinterCheckpoint.clear();
        PathWalker.stop();
        autoState = AutoState.IDLE;
        if (enabled) disable();
    }

    public boolean isLoaded()                         { return schematic != null && anchor != null; }
    public LitematicaSchematic getSchematic()          { return schematic; }
    public BlockPos getAnchor()                       { return anchor; }
    public void setAnchor(BlockPos anchor)            { this.anchor = anchor; }
    public int getBlocksPlaced()                      { return blocksPlaced; }
    public AutoState getAutoState()                   { return autoState; }

    // ── settings accessors ──────────────────────────────────────────────

    public int getBps()                 { return bps; }
    public void setBps(int bps)        { this.bps = Math.max(1, Math.min(9, bps)); }
    public double getRange()           { return range; }
    public void setRange(double range) { this.range = Math.max(2.0, Math.min(5.0, range)); }
    public boolean isSwapItems()       { return swapItems; }
    public void setSwapItems(boolean v){ this.swapItems = v; }
    public boolean isPrintInAir()      { return printInAir; }
    public void setPrintInAir(boolean v){ this.printInAir = v; }
    public SortMode getSortMode()      { return sortMode; }
    public void setSortMode(SortMode m){ this.sortMode = m; }
    public boolean isStatusMessages()  { return statusMessages; }
    public void setStatusMessages(boolean v){ this.statusMessages = v; }
    public boolean isAutoBuild()       { return autoBuild; }
    public void setAutoBuild(boolean v){ this.autoBuild = v; }

    // ═══════════════════════════════════════════════════════════════════
    //  TICK (called from mod initializer)
    // ═══════════════════════════════════════════════════════════════════

    public void tick() {
        if (!enabled) return;
        if (!isLoaded()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        PlacementEngine.setBps(bps);

        if (autoBuild) {
            tickAutoBuild(mc);
        } else {
            if (mc.currentScreen != null) return;
            if (!PlacementEngine.canPlace()) return;
            tryPlaceNextBlock(mc.player, mc.world);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  AUTO-BUILD STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════

    private void tickAutoBuild(MinecraftClient mc) {
        switch (autoState) {
            case BUILDING            -> tickBuilding(mc);
            case WALKING_TO_BUILD    -> tickWalking(mc, AutoState.BUILDING);
            case WALKING_BACK        -> tickWalking(mc, AutoState.BUILDING);
            case WALKING_TO_SUPPLY   -> tickWalkingToSupply(mc);
            case RESTOCKING          -> tickRestocking(mc);
            case IDLE                -> tickIdle(mc);
        }
    }

    private void tickBuilding(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        if (!PlacementEngine.canPlace()) return;

        boolean placed = tryPlaceNextBlock(mc.player, mc.world);
        if (placed) {
            noProgressTicks = 0;
            return;
        }

        noProgressTicks++;
        if (noProgressTicks < NO_PROGRESS_TIMEOUT) return;
        noProgressTicks = 0;

        if (shouldRestock(mc.player, mc.world)) {
            startRestockRun(mc.player, mc.world);
            return;
        }

        BlockPos nextZone = findNextBuildZone(mc.player, mc.world);
        if (nextZone != null) {
            lastBuildPos = mc.player.getBlockPos();
            PathWalker.walkTo(nextZone);
            autoState = AutoState.WALKING_TO_BUILD;
            if (statusMessages) {
                ChatHelper.info("§7Walking to next build zone §e"
                        + nextZone.getX() + " " + nextZone.getY() + " " + nextZone.getZ());
            }
        } else {
            autoState = AutoState.IDLE;
            if (statusMessages) {
                ChatHelper.info("§aBuild appears complete! §e"
                        + blocksPlaced + "§a blocks placed.");
            }
        }
    }

    private void tickWalking(MinecraftClient mc, AutoState arrivalState) {
        if (!PathWalker.isActive()) {
            if (PathWalker.hasArrived() && statusMessages) {
                ChatHelper.info("§7Arrived at target.");
            } else if (statusMessages) {
                ChatHelper.info("§eWalking timed out, building from here.");
            }
            autoState = arrivalState;
            noProgressTicks = 0;
            return;
        }
        PathWalker.tick();
    }

    private void tickWalkingToSupply(MinecraftClient mc) {
        if (!PathWalker.isActive()) {
            if (PathWalker.hasArrived() && supplyTarget != null) {
                if (tryOpenChest(mc, supplyTarget)) {
                    autoState = AutoState.RESTOCKING;
                    restockWaitTicks = 0;
                    return;
                }
            }
            if (statusMessages) {
                ChatHelper.info("§eCouldn't reach/open supply chest, resuming build.");
            }
            autoState = AutoState.BUILDING;
            noProgressTicks = 0;
            return;
        }
        PathWalker.tick();
    }

    private void tickRestocking(MinecraftClient mc) {
        restockWaitTicks++;

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
            // Index chest contents before taking items (snapshot for future queries)
            if (supplyTarget != null) {
                ChestIndexer.scanOpenChest(supplyTarget, containerHandler);
            }

            takeNeededItems(mc, mc.player, containerHandler);
            mc.player.closeHandledScreen();

            // Invalidate the snapshot since we just modified the chest
            if (supplyTarget != null) {
                ChestIndexer.invalidate(supplyTarget);
            }

            if (statusMessages) {
                ChatHelper.info("§aRestocked. Walking back to build.");
            }

            if (lastBuildPos != null) {
                PathWalker.walkTo(lastBuildPos);
                autoState = AutoState.WALKING_BACK;
            } else {
                autoState = AutoState.BUILDING;
                noProgressTicks = 0;
            }
            return;
        }

        if (restockWaitTicks >= CHEST_OPEN_TIMEOUT) {
            if (statusMessages) {
                ChatHelper.info("§eChest didn't open, resuming build.");
            }
            autoState = AutoState.BUILDING;
            noProgressTicks = 0;
        }
    }

    private void tickIdle(MinecraftClient mc) {
        idleScanCooldown--;
        if (idleScanCooldown > 0) return;
        idleScanCooldown = IDLE_SCAN_INTERVAL;

        BlockPos nextZone = findNextBuildZone(mc.player, mc.world);
        if (nextZone != null) {
            lastBuildPos = mc.player.getBlockPos();
            PathWalker.walkTo(nextZone);
            autoState = AutoState.WALKING_TO_BUILD;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  INVENTORY & RESTOCK
    // ═══════════════════════════════════════════════════════════════════

    private boolean shouldRestock(ClientPlayerEntity player, World world) {
        if (PrinterResourceManager.supplyChestCount() == 0) return false;

        Map<Item, Integer> needed = getNeededItemsNearby(player, world, 200);
        Map<Item, Integer> inventory = PlacementEngine.getInventoryContents();

        for (var entry : needed.entrySet()) {
            int have = inventory.getOrDefault(entry.getKey(), 0);
            if (have < RESTOCK_THRESHOLD && entry.getValue() > have) {
                return true;
            }
        }
        return false;
    }

    private void startRestockRun(ClientPlayerEntity player, World world) {
        Map<Item, Integer> needed = getNeededItemsNearby(player, world, 500);
        Map<Item, Integer> inventory = PlacementEngine.getInventoryContents();

        neededItems = new ArrayList<>();
        for (var entry : needed.entrySet()) {
            int have = inventory.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                neededItems.add(Registries.ITEM.getId(entry.getKey()).toString());
            }
        }

        if (neededItems.isEmpty()) {
            autoState = AutoState.BUILDING;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos nearest = (mc.world != null)
                ? PrinterResourceManager.findBestSupplyChest(
                    player.getBlockPos(), new HashSet<>(neededItems), mc.world)
                : findNearestSupplyChest(player.getBlockPos());
        if (nearest == null) {
            if (statusMessages) {
                ChatHelper.info("§eNo supply chests configured. Use §f/printer supply add");
            }
            autoState = AutoState.BUILDING;
            return;
        }

        supplyTarget = nearest;
        lastBuildPos = player.getBlockPos();
        PathWalker.walkToAdjacent(nearest);
        autoState = AutoState.WALKING_TO_SUPPLY;

        if (statusMessages) {
            ChatHelper.info("§7Restocking — walking to supply §e"
                    + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
                    + " §7(need " + neededItems.size() + " item types)");
        }
    }

    private Map<Item, Integer> getNeededItemsNearby(ClientPlayerEntity player, World world, int limit) {
        Map<Item, Integer> needed = new HashMap<>();
        int count = 0;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY && count < limit; y++) {
                for (int z = 0; z < region.absZ && count < limit; z++) {
                    for (int x = 0; x < region.absX && count < limit; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        if (world.getBlockState(new BlockPos(wx, wy, wz)).equals(target)) continue;

                        Item item = target.getBlock().asItem();
                        if (item instanceof BlockItem) {
                            needed.merge(item, 1, Integer::sum);
                            count++;
                        }
                    }
                }
            }
        }
        return needed;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CHEST INTERACTION
    // ═══════════════════════════════════════════════════════════════════

    private boolean tryOpenChest(MinecraftClient mc, BlockPos chestPos) {
        if (mc.player == null || mc.interactionManager == null) return false;

        double dist = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(chestPos));
        if (dist > 5.0 * 5.0) return false;

        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(chestPos), Direction.UP, chestPos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        return result.isAccepted();
    }

    private void takeNeededItems(MinecraftClient mc, ClientPlayerEntity player,
                                 GenericContainerScreenHandler handler) {
        if (neededItems == null || neededItems.isEmpty()) return;

        Set<String> needSet = new HashSet<>(neededItems);
        int chestSlots = handler.getRows() * 9;

        for (int slot = 0; slot < chestSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            if (needSet.contains(itemId)) {
                mc.interactionManager.clickSlot(
                        handler.syncId, slot, 0,
                        SlotActionType.QUICK_MOVE, player);
                continue;
            }

            if (isShulkerBox(stack) && shulkerContainsNeeded(stack, needSet)) {
                mc.interactionManager.clickSlot(
                        handler.syncId, slot, 0,
                        SlotActionType.QUICK_MOVE, player);
            }
        }
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean shulkerContainsNeeded(ItemStack shulkerStack, Set<String> needSet) {
        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        if (cc == null) return false;

        for (ItemStack inner : cc.iterateNonEmpty()) {
            String innerId = Registries.ITEM.getId(inner.getItem()).toString();
            if (needSet.contains(innerId)) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  NAVIGATION HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private BlockPos findNextBuildZone(ClientPlayerEntity player, World world) {
        if (player == null || world == null) return null;

        /*? if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                boolean foundInLayer = false;
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        BlockPos worldPos = new BlockPos(wx, wy, wz);

                        if (world.getBlockState(worldPos).equals(target)) continue;

                        foundInLayer = true;
                        double dist = playerPos.squaredDistanceTo(Vec3d.ofCenter(worldPos));
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = worldPos;
                        }
                    }
                }
                if (foundInLayer) break;
            }
        }
        return best;
    }

    private BlockPos findNearestSupplyChest(BlockPos from) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : PrinterResourceManager.getSupplyChests()) {
            double dist = from.getSquaredDistance(pos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        return nearest;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CORE PLACEMENT
    // ═══════════════════════════════════════════════════════════════════

    private boolean tryPlaceNextBlock(ClientPlayerEntity player, World world) {
        double rangeSq = range * range;
        int maxReach = (int) Math.ceil(range);

        List<BlockPos> candidates = new ArrayList<>();
        BlockPos playerPos = player.getBlockPos();

        for (int dy = -maxReach; dy <= maxReach; dy++) {
            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    BlockPos worldPos = playerPos.add(dx, dy, dz);

                    if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(worldPos)) > rangeSq) continue;
                    /*? if >=1.21.10 {*//*
                    if (player.getSyncedPos().squaredDistanceTo(Vec3d.ofCenter(worldPos)) < 1.0) continue;
                    *//*?} else {*/
                    if (player.getPos().squaredDistanceTo(Vec3d.ofCenter(worldPos)) < 1.0) continue;
                    /*?}*/

                    int sx = worldPos.getX() - anchor.getX();
                    int sy = worldPos.getY() - anchor.getY();
                    int sz = worldPos.getZ() - anchor.getZ();
                    if (!schematic.contains(sx, sy, sz)) continue;

                    BlockState target = schematic.getBlockState(sx, sy, sz);
                    if (target.isAir()) continue;
                    if (world.getBlockState(worldPos).equals(target)) continue;
                    if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) continue;

                    candidates.add(worldPos);
                }
            }
        }

        if (candidates.isEmpty()) return false;

        Comparator<BlockPos> comparator = (sortMode == SortMode.BOTTOM_UP)
                ? Comparator.<BlockPos>comparingInt(BlockPos::getY)
                    .thenComparingDouble(p -> player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p)))
                : Comparator.comparingDouble(p -> player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(p)));
        candidates.sort(comparator);

        for (BlockPos worldPos : candidates) {
            int sx = worldPos.getX() - anchor.getX();
            int sy = worldPos.getY() - anchor.getY();
            int sz = worldPos.getZ() - anchor.getZ();
            BlockState target = schematic.getBlockState(sx, sy, sz);

            if (PlacementEngine.placeBlock(worldPos, target, swapItems)) {
                blocksPlaced++;
                if (schematicFile != null) {
                    PrinterCheckpoint.onBlockPlaced(schematicFile, anchor, blocksPlaced, player.getBlockPos());
                }
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILITY / STATUS
    // ═══════════════════════════════════════════════════════════════════

    public int countRemaining() {
        if (!isLoaded()) return -1;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return -1;

        World world = mc.world;
        int remaining = 0;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        if (!world.getBlockState(new BlockPos(wx, wy, wz)).equals(target)) {
                            remaining++;
                        }
                    }
                }
            }
        }
        return remaining;
    }

    public static List<String> listSchematics() {
        List<String> names = new ArrayList<>();
        Path dir = getSchematicsDir();
        if (!Files.isDirectory(dir)) return names;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.litematic")) {
            for (Path entry : stream) {
                String fname = entry.getFileName().toString();
                names.add(fname.substring(0, fname.length() - ".litematic".length()));
            }
        } catch (IOException ignored) {}
        return names;
    }

    public static Path getSchematicsDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CHECKPOINT / RESUME
    // ═══════════════════════════════════════════════════════════════════

    public void saveCheckpoint() {
        if (schematicFile != null && anchor != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            BlockPos playerPos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
            PrinterCheckpoint.save(schematicFile, anchor, blocksPlaced, playerPos);
        }
    }

    public void restoreFromCheckpoint(PrinterCheckpoint.CheckpointData data, Path schematicPath) throws IOException {
        this.schematic = LitematicaSchematic.load(schematicPath);
        this.anchor = data.anchorPos();
        this.blocksPlaced = data.blocksPlaced;
        this.schematicFile = schematicPath.getFileName().toString();
    }

    public String getSchematicFile() { return schematicFile; }

    // ═══════════════════════════════════════════════════════════════════
    //  MATERIALS REPORT
    // ═══════════════════════════════════════════════════════════════════

    public PrinterResourceManager.MaterialsReport analyzeMaterials() {
        if (!isLoaded()) return PrinterResourceManager.MaterialsReport.EMPTY;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return PrinterResourceManager.MaterialsReport.EMPTY;
        return PrinterResourceManager.analyzeMaterials(schematic, anchor, mc.world);
    }
}
