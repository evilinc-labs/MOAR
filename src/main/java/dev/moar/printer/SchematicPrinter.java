package dev.moar.printer;

import dev.moar.chest.ChestManager;
import dev.moar.schematic.LitematicaDetector;
import dev.moar.schematic.LitematicaSchematic;
import dev.moar.schematic.PrinterCheckpoint;
import dev.moar.schematic.PrinterResourceManager;
import dev.moar.util.BlockDependency;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
import dev.moar.util.PacketTelemetry;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;
import dev.moar.util.PrinterDatabase;
import dev.moar.util.PrinterNetworkCoordinator;
import dev.moar.MoarMod;
import dev.moar.util.SneakOverride;
import dev.moar.world.SetbackMonitor;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.piston.*;
*//*?} else {*/
import net.minecraft.block.*;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BedPart;
*//*?} else {*/
import net.minecraft.block.enums.BedPart;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
*//*?} else {*/
import net.minecraft.block.enums.DoubleBlockHalf;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
*//*?} else {*/
import net.minecraft.state.property.Properties;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.Property;
*//*?} else {*/
import net.minecraft.state.property.Property;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.player.LocalPlayer;
*//*?} else {*/
import net.minecraft.client.network.ClientPlayerEntity;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.component.DataComponents;
*//*?} else {*/
import net.minecraft.component.DataComponentTypes;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.component.ItemContainerContents;
*//*?} else {*/
import net.minecraft.component.type.ContainerComponent;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.entity.player.Inventory;
*//*?} else {*/
import net.minecraft.entity.player.PlayerInventory;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.BlockItem;
*//*?} else {*/
import net.minecraft.item.BlockItem;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Item;
*//*?} else {*/
import net.minecraft.item.Item;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.item.ItemStack;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Items;
*//*?} else {*/
import net.minecraft.item.Items;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.registries.BuiltInRegistries;
*//*?} else {*/
import net.minecraft.registry.Registries;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ChestMenu;
*//*?} else {*/
import net.minecraft.screen.GenericContainerScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.AbstractContainerMenu;
*//*?} else {*/
import net.minecraft.screen.ScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ShulkerBoxMenu;
*//*?} else {*/
import net.minecraft.screen.ShulkerBoxScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ContainerInput;
*//*?} else {*/
import net.minecraft.screen.slot.SlotActionType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionResult;
*//*?} else {*/
import net.minecraft.util.ActionResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionHand;
*//*?} else {*/
import net.minecraft.util.Hand;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.resources.Identifier;
*//*?} else {*/
import net.minecraft.util.Identifier;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.BlockHitResult;
*//*?} else {*/
import net.minecraft.util.hit.BlockHitResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.Direction;
*//*?} else {*/
import net.minecraft.util.math.Direction;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.util.Mth;
*//*?} else {*/
import net.minecraft.util.math.MathHelper;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.Vec3;
*//*?} else {*/
import net.minecraft.util.math.Vec3d;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.material.FluidState;
*//*?} else {*/
import net.minecraft.fluid.FluidState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.resources.ResourceKey;
*//*?} else {*/
import net.minecraft.registry.RegistryKey;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

// Schematic block placer. Manual mode: reach only. AutoBuild: walk, place, resupply.
public class SchematicPrinter {

    // enums

    public enum SortMode {
        NEAREST,
        BOTTOM_UP,
        TOP_DOWN
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");
    private static final String NETWORK_OWNER = "SchematicPrinter";

    public enum AutoState {
        CLEARING_AREA,
        WALKING_TO_CLEAR,
        WALKING_TO_DUMP,
        DUMPING,
        BUILDING,
        WALKING_TO_BUILD,
        WALKING_TO_SUPPLY,
        RESTOCKING,
        UNLOADING_SHULKER,
        WALKING_BACK,
        CLEANING_SCAFFOLD,
        IDLE
    }

    public enum BuildResult {
        NONE,
        COMPLETED,
        COMPLETED_WITH_MISSING_MATERIALS
    }

    private enum DumpMode {
        NONE,
        CHEST,
        LOCAL_SHULKER
    }

    // settings

    private int bps = 19;
    private double range = 4.2;
    private boolean swapItems = true;
    private boolean printInAir = true;
    private SortMode sortMode = SortMode.BOTTOM_UP;
    private boolean statusMessages = true;
    private boolean autoBuild = false;
    private static final int BUILD_SWAP_RECENT_SETBACK_WINDOW = 40;
    private static final int BOTTOM_UP_ACTIVE_BAND_HEIGHT = 1;
    private static final int EXTERIOR_BUILD_SHELL_DEPTH = 4;
    private static final int EXTERIOR_FAILED_ZONE_BUDGET = 24;

    private boolean tryPrinterLane(PrinterNetworkCoordinator.Lane lane, int cost, int cooldownTicks) {
        return PrinterNetworkCoordinator.tryAcquire(lane, NETWORK_OWNER, cost, cooldownTicks);
    }

    private boolean tryPrinterLook(int cooldownTicks) {
        return tryPrinterLane(PrinterNetworkCoordinator.Lane.LOOK, 1, cooldownTicks);
    }

    private boolean tryPrinterInteraction(int cooldownTicks) {
        return tryPrinterLane(PrinterNetworkCoordinator.Lane.INTERACTION, 2, cooldownTicks);
    }

    private boolean tryPrinterInventory(int cooldownTicks) {
        return tryPrinterLane(PrinterNetworkCoordinator.Lane.INVENTORY, 1, cooldownTicks);
    }

    private boolean tryPrinterMining(int cooldownTicks) {
        return tryPrinterLane(PrinterNetworkCoordinator.Lane.MINING, 2, cooldownTicks);
    }

    private boolean isNavigationAutoState() {
        return switch (autoState) {
            case WALKING_TO_CLEAR,
                    WALKING_TO_DUMP,
                    WALKING_TO_BUILD,
                    WALKING_TO_SUPPLY,
                    WALKING_BACK -> true;
            default -> false;
        };
    }

    private boolean allowLiveInventorySwapsDuringBuild() {
        if (!swapItems) return false;
        if (PlacementEngine.areSwapsSuspended()) return false;
        SetbackMonitor monitor = SetbackMonitor.get();
        return monitor.isCalm()
                && monitor.recentSetbackCount(BUILD_SWAP_RECENT_SETBACK_WINDOW) == 0
                && monitor.isStationaryFor(3)
                && PlacementEngine.getConsecutiveFailures() == 0
                && PlacementEngine.getConsecutiveRejections() == 0;
    }

    // state

    private boolean enabled = false;
    private BuildResult buildResult = BuildResult.NONE;

    // schematic state

    private LitematicaSchematic schematic;
    private BlockPos anchor;
    private int blocksPlaced;
    private Path schematicPath;
    private String schematicFile;
    // Dimension the schematic was loaded in — pauses on dimension change.
    /*? if >=26.1 {*//*
    private ResourceKey<Level> buildDimension;
    *//*?} else {*/
    private RegistryKey<World> buildDimension;
    /*?}*/

    // auto-build state

    private AutoState autoState = AutoState.IDLE;
    private BlockPos lastBuildPos;
    private BlockPos supplyTarget;
    private Set<String> neededItems;
    private Map<String, Integer> neededItemCounts = new LinkedHashMap<>();
    private int restockWaitTicks;
    // Ticks waiting for server to sync chest contents in RESTOCKING.
    private int chestSyncDelay;
    private static final int CONTAINER_ACTION_TIMEOUT_TICKS = 20;
    // Consecutive restock failures without grabbing any items.
    private int restockFailures;
    private enum ContainerTransferLane { NONE, RESTOCK, DUMP, SHULKER }
    private ContainerTransferLane pendingContainerLane = ContainerTransferLane.NONE;
    private int pendingContainerSyncId = -1;
    private int pendingContainerSlot = -1;
    private ItemStack pendingContainerSlotSnapshot = ItemStack.EMPTY;
    private int pendingContainerTicks;
    private ContainerTransferLane completedContainerLane = ContainerTransferLane.NONE;
    private boolean completedContainerChanged;
    private final Set<Integer> blockedRestockSlots = new HashSet<>();
    private final Set<Integer> blockedDumpSlots = new HashSet<>();
    private final Set<Integer> blockedShulkerSlots = new HashSet<>();
    // Chests Baritone couldn't reach — LRU-evicted at 64.
    private final Set<BlockPos> unreachableChests = Collections.newSetFromMap(
            new LinkedHashMap<>(32, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                    return size() > 64;
                }
            });
    private int idleScanCooldown;
    private int noProgressTicks;
    private Set<Item> lastMissingItems = new HashSet<>();
    private int missingItemMsgCooldown;
    // Items we've given up restocking — LRU-evicted at 64.
    private final Set<Item> skippedItems = Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<Item, Boolean> eldest) {
                    return size() > 64;
                }
            });
    private static final int FAILED_ZONE_MEMORY_MAX = 1024;
    private final Set<BlockPos> failedZones = Collections.newSetFromMap(
            new LinkedHashMap<>(128, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                    return size() > FAILED_ZONE_MEMORY_MAX;
                }
            });
    // Consecutive walk failures — triggers GoalNear fallback.
    private int walkFailCount;
    private static final int MAX_WALK_RETRIES = 3;
    // True if we already tried walkToWithPlacement and it failed — prevents retry loop.
    private boolean triedPlacementWalk;
    // The target zone we last navigated toward — prevent re-targeting.
    private BlockPos lastWalkTargetZone;
    private BlockPos lastWalkStandPos;
    private BlockPos lastWalkApproachPos;
    private int walkAttemptCooldown;
    private int stuckCycles;
    private static final int MAX_STUCK_CYCLES = 10;
    private int walkingSetbackPauseTicks;
    private int observedWalkingSetbacks;
    private int observedPlacementSetbacks;
    private static final int WALK_SETBACK_PAUSE_TICKS = 16;
    private int buildHandoffSettleTicks;
    private static final int BUILD_HANDOFF_SETTLE_TICKS = 4;
    private Item activeBuildMaterialRun;
    private Item deferredBuildInventorySwapItem;
    private int deferredBuildInventorySwapTicks;
    private static final int BUILD_INVENTORY_SWAP_WAIT_LIMIT_TICKS = 8;
    // Ticks until next skippedItems re-evaluation.
    private int skippedItemRecheckCooldown;
    private static final int SKIPPED_RECHECK_INTERVAL = 200; // ~10s
    private static final int STANDING_CLEARANCE_GOAL = 4;
    private static final double STANDING_BELOW_TARGET_PENALTY = 28.0;
    private static final double STANDING_LOW_HEADROOM_PENALTY = 14.0;
    private static final double STANDING_ENCLOSURE_PENALTY = 8.0;
    // Penalty applied when an access feet/head cell contains a fluid (water).
    // Sized smaller than the Y-difference cost (18 per block) so a same-Y wet
    // stance beats an above-Y dry stance. This is critical for sea-level
    // builds where the only "dry" stances are on top of already-placed walls,
    // and those wall-top stances are geometrically occluded from the missing
    // floor cells below them (entombment / pattern A occlusion). Original
    // value of 500 mirrored findStandingPosition (retriever) and pushed the
    // printer onto wall-tops every time, locking into a 0-blocks-placed loop.
    private static final double BUILD_ACCESS_WATER_PENALTY = 12.0;
    private static final int STAGING_PLAN_MAX_CANDIDATES = 18;
    private static final int STAGING_PLAN_CACHE_TTL = 4;
    private static final int BUILD_STAGING_WALK_RADIUS = 1;
    private static final int BUILD_STAGING_PLACE_WALK_RADIUS = 2;
    private static final double BUILD_STAGING_ARRIVAL_DISTANCE = 0.75;
    private static final int ACCESS_MINING_SEARCH_RADIUS = 5;
    // Max consecutive server-rejected placements before repositioning.
    private static final int SERVER_REJECT_THRESHOLD = 6;
    private static final int SERVER_TIMEOUT_REPOSITION_THRESHOLD = 2;
    private static final int TIMEOUT_SETBACK_REPOSITION_WINDOW_TICKS = 24;
    private static final int PLACEMENT_START_TIMEOUT_TICKS = 10;
    private static final int PLACEMENT_FAILURE_PAUSE_TICKS = 6;
    private static final int PLACEMENT_START_FAILURE_PAUSE_TICKS = 4;
    private static final int SOFT_TIMEOUT_FAILURE_PAUSE_TICKS = 4;
    private static final int BUILD_GATE_STALL_RECHECK_TICKS = 20;
    private static final int BUILD_GATE_STALL_TIMEOUT_TICKS = 80;
    private static final int PLACEMENT_START_FAILURE_TARGET_COOLDOWN_TICKS = 20;
    private static final int NO_VISIBLE_HIT_TARGET_COOLDOWN_TICKS = 160;
    private static final int TIMEOUT_TARGET_COOLDOWN_TICKS = 160;
    private static final int TIMEOUT_TARGET_COOLDOWN_STEP_TICKS = 80;
    private static final int MAX_TIMEOUT_TARGET_COOLDOWN_TICKS = 600;
    private static final int REJECTED_TARGET_COOLDOWN_TICKS = 36;
    private static final int OCCLUDED_TARGET_COOLDOWN_TICKS = 24;
    private static final int OCCLUDED_TARGET_COOLDOWN_LIMIT = 16;
    // Zone-level cooldown for "no viable stance reachable for this zone right now".
    // This is durably geometric (existing walls block approach) — 24t lets the
    // printer immediately re-pick the same entombed zone after another zone's
    // 24t cooldown expires, churning through ~6 entombed cells without progress.
    // 600t (30s) gives the printer time to exhaust the entombed cluster and
    // search higher Y / further out for a buildable zone.
    private static final int NO_ACCESS_ZONE_COOLDOWN_TICKS = 600;
    // Even longer cooldown for zones where findBuildAccessTarget reports
    // considered=0 (literally zero candidate stances passed feet/head/ground
    // checks). These are entombed — only an adjacent block placement can
    // change their accessibility, which won't happen if the printer keeps
    // picking them. 2400t (~2 min) effectively retires them for the session.
    private static final int ENTOMBED_ZONE_COOLDOWN_TICKS = 2400;
    // Three pure-occlusion / access failures on the same Y means that layer is
    // geometrically broken from the current approach; stop hammering it.
    // The 24-tick target cooldown lets the printer cycle through 10+ adjacent
    // zones before escalating, so a high threshold (was 8) effectively never tripped.
    private static final int BUILD_LAYER_ACCESS_FAILURE_THRESHOLD = 3;
    private static final int BUILD_LAYER_COOLDOWN_TICKS = 240;
    // Exponential layer cooldown: each successive re-trigger of the same Y
    // multiplies the cooldown duration. Prevents the printer from immediately
    // re-attempting a broken layer the moment its short cooldown expires.
    private static final int BUILD_LAYER_COOLDOWN_GROWTH_FACTOR = 3;
    private static final int BUILD_LAYER_COOLDOWN_MAX_TICKS = 6000;
    // Set by findBuildAccessTarget when it returns null, so the caller can
    // distinguish "considered=0 entombed" from "considered=N but all rejected"
    // and pick the appropriate cooldown. -1 = no recent reject.
    private int lastBuildAccessRejectConsidered = -1;
    private BlockPos pendingBuildPlacementPos;
    private BlockState pendingBuildPlacementState;
    private BlockPos pendingBuildPlacementSupportPos;
    private BlockPos pendingBuildPlacementStancePos;
    private long pendingBuildPlacementStartTick = -1;
    private int placementFailurePauseTicks;
    private int buildGateStallTicks;
    private boolean deferredPlacementReposition;
    private BlockPos deferredPlacementRepositionPos;
    private BlockPos lastPlacementStartFailurePos;
    private int placementStartFailureStreak;
    private final Map<BlockPos, Integer> buildPlacementFailures = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Integer> eldest) {
            return size() > 128;
        }
    };
    private final Map<BlockPos, Long> cooledDownPlacementTargets = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
            return size() > 128;
        }
    };
    private final Map<Integer, Integer> buildLayerAccessFailures = new LinkedHashMap<>(8, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
            return size() > 32;
        }
    };
    private final Map<Integer, Long> cooledDownBuildLayers = new LinkedHashMap<>(8, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
            return size() > 32;
        }
    };
    private final Map<Integer, Integer> buildLayerCooldownStage = new LinkedHashMap<>(8, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
            return size() > 32;
        }
    };
    private record PlacementAttemptKey(BlockPos target, BlockPos support, BlockPos stance) {}
    private record BuildPlacementContract(BlockPos target, Set<BlockPos> allowedSupports,
                                          boolean constrained) {
        Predicate<BlockPos> supportFilter() {
            if (!constrained) {
                return support -> true;
            }
            return allowedSupports::contains;
        }
    }
    private static final class BuildMaterialRunStats {
        final int firstIndex;
        int count;
        final boolean heldReady;
        final boolean hotbarReady;

        BuildMaterialRunStats(int firstIndex, boolean heldReady, boolean hotbarReady) {
            this.firstIndex = firstIndex;
            this.heldReady = heldReady;
            this.hotbarReady = hotbarReady;
            this.count = 1;
        }
    }
    private final Map<PlacementAttemptKey, Long> cooledDownPlacementAttempts =
            new LinkedHashMap<>(32, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<PlacementAttemptKey, Long> eldest) {
                    return size() > 256;
                }
            };
    // True during the redstone placement pass.
    private boolean redstonePass;
    // True during the liquid placement pass.
    private boolean liquidPass;
    // True if waypoint-based supply retry was already attempted.
    private boolean triedWaypointRestock;
    // True if linear-waypoint supply retry was already attempted.
    private boolean triedLinearRestock;
    // True if placement-walk supply retry was already attempted.
    private boolean triedPlacementRestock;

    // Multi-phase descent: 0=none, 1=horizontal, 2=descend, 3=approach.
    private int supplyDescentPhase;
    private BlockPos supplyDescentTarget;

    // scaffold tracking state
    private BlockPos scaffoldBreakTarget;
    private int scaffoldBreakTicks;
    private float scaffoldSavedYaw, scaffoldSavedPitch;
    private static final int MAX_SCAFFOLD_BREAK_TICKS = 60;
    private final Set<BlockPos> lastScaffoldScanBlocks = new HashSet<>();
    private boolean scaffoldScanSeeded;

    // area-clearing state
    private BlockPos clearBreakTarget;
    private BlockPos lastClearTargetBlock;
    private boolean accessClearInProgress;
    private int clearBreakTicks;
    private float clearSavedYaw, clearSavedPitch;
    private static final int MAX_CLEAR_BREAK_TICKS = 200;
    private static final double CLEAR_INTERACTION_RANGE = 3.0;
    private static final double CLEAR_FINAL_APPROACH_TRIGGER_SQ = 8.0 * 8.0;
    private static final double CLEAR_PATH_ARRIVAL_DISTANCE = 1.25;
    private static final int ACCESS_REPAIR_MEMORY_RADIUS = 1;
    private static final int ACCESS_REPAIR_MEMORY_MAX = 256;
    private int clearBlocksBroken;
    private boolean clearingDone;
    private int clearCooldownTicks;
    private int scaffoldCooldownTicks;
    private static final int BREAK_COOLDOWN_TICKS = 6;
    // Consecutive clearing failures — triggers stall recovery on threshold.
    private int consecutiveClearFailures;
    private static final int MAX_CONSECUTIVE_CLEAR_FAILURES = 20;
    // Stall-recovery resets done — gives up after MAX.
    private int clearStallResets;
    private static final int MAX_CLEAR_STALL_RESETS = 3;
    // Unreachable clearing targets — LRU-evicted at 64.
    private final Set<BlockPos> failedClearTargets = Collections.newSetFromMap(
            new LinkedHashMap<>(32, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                    return size() > 64;
                }
            });
    private final Set<BlockPos> repairPriorityTargets = Collections.newSetFromMap(
            new LinkedHashMap<>(64, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                    return size() > ACCESS_REPAIR_MEMORY_MAX;
                }
            });

    // shulker unloading state
    // Sub-phase: 0=find, 1=swap, 2=place, 3=wait, 4=open, 5=take, 6=break, 7=breaking, 8=pickup.
    private int shulkerUnloadPhase;
    private BlockPos shulkerPlacePos;
    private int shulkerUnloadTicks;
    private int shulkerTotalTicks;
    private static final int MAX_SHULKER_PHASE_TICKS = 80;
    private static final int MAX_SHULKER_TOTAL_TICKS = 600;
    private int shulkerHotbarSlot = -1;
    private float shulkerSavedYaw, shulkerSavedPitch;
    private static final int SHULKER_PLACE_DELAY = 10;
    private static final int SHULKER_PICKUP_DELAY = 15;
    private int shulkerSyncDelay;
    private int shulkerUnloadFailures;
    private static final int MAX_SHULKER_FAILURES = 3;
    private boolean shulkerMovedNeededItems;
    // Skip shulker shortcut — walk to supply chest instead.
    private boolean shulkerNoSpaceSkipped;
    private int platformBuildAttempts;
    private static final int MAX_PLATFORM_ATTEMPTS = 3;
    // Platform block position — tracked for scaffold cleanup.
    private BlockPos platformBlockPos;
    private int shulkerOpenRetries;
    private static final int MAX_SHULKER_OPEN_RETRIES = 3;
    private static final int SCAFFOLD_SCAN_INTERVAL = 10;
    private int scaffoldScanCooldown;

    // dump state — depositing mined items during clearing
    // Position we were clearing before the dump detour.
    private BlockPos preDumpClearPos;
    // The dump chest we're walking to / depositing into.
    private BlockPos dumpTarget;
    // Ticks waiting for the dump chest screen to open.
    private int dumpWaitTicks;
    // Ticks since the dump chest screen first appeared.
    private int dumpSyncDelay;
    private static final int DUMP_SYNC_DELAY = 3;
    private DumpMode dumpMode = DumpMode.NONE;
    private int dumpShulkerPhase;
    private BlockPos dumpShulkerPos;
    private int dumpShulkerSlot = -1;
    private int dumpShulkerOpenRetries;
    private int dumpShulkerTransferTimeouts;
    private static final int MAX_DUMP_SHULKER_OPEN_RETRIES = 3;
    private static final int MAX_DUMP_SHULKER_TRANSFER_TIMEOUTS = 2;
    private final Set<Integer> preDumpShulkerSlots = new HashSet<>();
    private final Set<Integer> exhaustedDumpShulkerSlots = new HashSet<>();

    private int placementCheckCooldown;

    // Ticks until next anchor correlation check (starts at 1).
    private int anchorCorrelationCooldown;
    // Interval (ticks) between automatic SchematicWorld anchor checks.
    private static final int ANCHOR_CORRELATION_INTERVAL = 200;
    // Whether the anchor has been confirmed via SchematicWorld correlation.
    private boolean anchorCorrelated;

    // True when loaded via Litematica auto-detect.
    private boolean autoDetected;

    private static final int RESTOCK_THRESHOLD = 64;
    private static final int RESTOCK_WORKING_SET_SCAN_LIMIT = 200;
    private static final int RESTOCK_WORKING_SET_TYPES = 10;
    private static final int RESTOCK_TARGET_STACKS_PER_ITEM = 1;
    private static final int RESTOCK_RESERVED_FREE_SLOTS = 4;
    private static final int SUPPLY_REGION_MARGIN_XZ = 256;
    private static final int SUPPLY_REGION_MARGIN_Y = 96;
    private static final int RENDER_SCAN_EXTRA_CHUNKS = 1;
    private static final int CHUNK_DIFF_CACHE_TTL = 20;
    private static final double APPROACH_STAGING_EXTRA_REACH = 2.5;
    private static final double APPROACH_STAGING_MAX_REACH = 7.5;
    private static final int MISSING_MSG_COOLDOWN = 100;
    private static final int CHEST_OPEN_TIMEOUT = 40;
    private static final int IDLE_SCAN_INTERVAL = 10;
    private static final int NO_PROGRESS_TIMEOUT = 40;
    private static final int NO_PROGRESS_WALK_RECHECK_TICKS = 6;
    private static final int PLACEMENT_START_REPOSITION_THRESHOLD = 3;
    // Delay for server to sync chest contents after screen opens.
    private static final int CHEST_SYNC_DELAY = 3;
    private static final int MAX_RESTOCK_FAILURES = 6;

    private record RenderWindow(int chunkRadius, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {}
    private record SchematicBlockRef(BlockPos pos, BlockState target, Item item, boolean liquid, boolean redstone) {}
    private record ChunkDiffSnapshot(long scanTick, List<SchematicBlockRef> unresolved) {}
    private record BuildStagingPlan(BlockPos zone, BlockPos standPos, boolean approachOnly) {}
    private record StagingTargetCandidate(SchematicBlockRef ref, double targetDist) {}
    private record BuildAccessTarget(BlockPos feetPos, BlockPos clearPos) {}
    private record AccessPathProbe(BlockPos clearPos, boolean blocked) {}

    private final Map<Long, List<SchematicBlockRef>> schematicBlocksByChunk = new HashMap<>();
    private final Map<Long, ChunkDiffSnapshot> chunkDiffCache = new HashMap<>();
    private long cachedBuildStagingPlanTick = Long.MIN_VALUE;
    private BlockPos cachedBuildStagingPlanPlayerPos;
    private int cachedBuildStagingPlanExtraReachBonus = Integer.MIN_VALUE;
    private BuildStagingPlan cachedBuildStagingPlan;
    private long cachedActiveBandTick = Long.MIN_VALUE;
    private int cachedActiveBandMinChunkX;
    private int cachedActiveBandMaxChunkX;
    private int cachedActiveBandMinChunkZ;
    private int cachedActiveBandMaxChunkZ;
    private int cachedActiveBandTopY = Integer.MAX_VALUE;

    // cached schematic scan results
    // TTL for full-schematic remaining-block scans.
    private static final int REMAINING_CACHE_TTL = 100; // ~5 seconds
    private long remainingCacheTick = Long.MIN_VALUE;
    private int  cachedCountRemaining = -1;
    private long solidsCacheTick = Long.MIN_VALUE;
    private boolean cachedHasSolids;
    private long redstoneCacheTick = Long.MIN_VALUE;
    private boolean cachedHasRedstone;
    private long liquidsCacheTick = Long.MIN_VALUE;
    private boolean cachedHasLiquids;

    // toggle / lifecycle

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
        clearBuildResult();

        // Sync with Litematica: full re-detect only when nothing is loaded.
        // Once a placement is loaded, keep that selection stable across
        // toggles and only re-sync the anchor when the match is unambiguous.
        boolean litematicaSynced = false;
        if (schematic == null || anchor == null) {
            if (tryAutoDetect()) {
                ChatHelper.labelled("Printer", "§aLoaded §f" + schematic.getName()
                        + " §7(" + schematic.getTotalNonAir() + " blocks)");
                litematicaSynced = true;
            } else if (schematic == null || anchor == null) {
                ChatHelper.info("§cNo schematic loaded. Use /printer load <file> or load one in Litematica.");
            }
        } else {
            // Already loaded — re-sync anchor only (avoid replacing schematic).
            if (trySyncAnchor()) {
                litematicaSynced = true;
            } else if (!autoDetected) {
                ChatHelper.info("§7Anchor unchanged — SchematicWorld correlation "
                        + "will auto-align on next tick.");
            }
        }

        // SchematicWorld correlation will auto-align on next tick if needed.

        if (schematic != null && anchor != null) {
            ChatHelper.info("Printing §a" + schematic.getName()
                    + "§f (" + schematic.getTotalNonAir() + " blocks)");
            ChatHelper.info("Anchor: §e" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());
            int x2 = anchor.getX() + schematic.getSizeX() - 1;
            int y2 = anchor.getY() + schematic.getSizeY() - 1;
            int z2 = anchor.getZ() + schematic.getSizeZ() - 1;
            ChatHelper.info("Region: §7("
                    + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ()
                    + ") → (" + x2 + ", " + y2 + ", " + z2 + ")");
            warnIfAnchorSuspicious();
            if (autoBuild) { 
                ChatHelper.info("§bAutoBuild §aenabled §7— clearing illegal blocks, then building.");
            }
        }

        blocksPlaced = 0;
        clearBlocksBroken = 0;
        clearingDone = false;
        clearBreakTarget = null;
        lastClearTargetBlock = null;
        accessClearInProgress = false;
        consecutiveClearFailures = 0;
        clearStallResets = 0;
        failedClearTargets.clear();
        repairPriorityTargets.clear();
        autoState = AutoState.CLEARING_AREA;
        noProgressTicks = 0;
        idleScanCooldown = 0;
        failedZones.clear();
        clearPendingBuildPlacement();
        buildPlacementFailures.clear();
        repairPriorityTargets.clear();
        resetPlacementFailureRecovery();
        resetBuildCooldownMemory();
        resetContainerTransferTracking();
        walkFailCount = 0;
        triedPlacementWalk = false;
            lastWalkTargetZone = null;
            walkAttemptCooldown = 0;
            stuckCycles = 0;
            walkingSetbackPauseTicks = 0;
            int totalSetbacks = SetbackMonitor.get().totalSetbacks();
            observedWalkingSetbacks = totalSetbacks;
            observedPlacementSetbacks = totalSetbacks;
        restockFailures = 0;
        unreachableChests.clear();
        skippedItems.clear();
        accessClearInProgress = false;
        lastMissingItems.clear();
        neededItems = null;
        redstonePass = false;
        liquidPass = false;
        triedWaypointRestock = false;
        triedLinearRestock = false;
        triedPlacementRestock = false;
        shulkerNoSpaceSkipped = false;
        shulkerMovedNeededItems = false;
        placementCheckCooldown = 0;
        skippedItemRecheckCooldown = SKIPPED_RECHECK_INTERVAL;
        neededItemCounts.clear();
        preDumpClearPos = null;
        dumpTarget = null;
        dumpWaitTicks = 0;
        dumpSyncDelay = 0;
        dumpMode = DumpMode.NONE;
        dumpShulkerPhase = 0;
        dumpShulkerPos = null;
        dumpShulkerSlot = -1;
        dumpShulkerOpenRetries = 0;
        dumpShulkerTransferTimeouts = 0;
        preDumpShulkerSlots.clear();
        // If Litematica placement data provided the anchor, trust it
        // and skip the immediate hologram correlation — the heuristic
        // scan can produce wrong anchors when many blocks share state.
        if (litematicaSynced) {
            anchorCorrelationCooldown = ANCHOR_CORRELATION_INTERVAL;
            anchorCorrelated = true;
        } else {
            anchorCorrelationCooldown = 1; // hologram scan on next tick
            anchorCorrelated = false;
        }

        PlacementEngine.clearCorrectionHistory();
        PlacementEngine.reset();

        // If the build site is far away (unloaded chunks), start
        // walking there immediately instead of waiting for the
        // no-progress timeout cascade.
        if (autoBuild && schematic != null && anchor != null) {
            /*? if >=26.1 {*//*
            Minecraft client = Minecraft.getInstance();
            *//*?} else {*/
            MinecraftClient client = MinecraftClient.getInstance();
            /*?}*/
            /*? if >=26.1 {*//*
            if (client.level != null && client.player != null) {
            *//*?} else {*/
            if (client.world != null && client.player != null) {
            /*?}*/
                // Check for illegal blocks first (clearing phase)
                /*? if >=26.1 {*//*
                BlockPos clearTarget = findNextClearTarget(client.player, client.level);
                *//*?} else {*/
                BlockPos clearTarget = findNextClearTarget(client.player, client.world);
                /*?}*/
                if (clearTarget == null) {
                    // No illegal blocks — skip to build phase
                    clearingDone = true;
                }
                /*? if >=26.1 {*//*
                BlockPos zone = findNextBuildZone(client.player, client.level);
                *//*?} else {*/
                BlockPos zone = findNextBuildZone(client.player, client.world);
                /*?}*/
                if (zone == null && clearTarget == null) {
                    // No work in loaded chunks — check unloaded
                    /*? if >=26.1 {*//*
                    BlockPos unloaded = findUnloadedBuildZone(client.player, client.level);
                    *//*?} else {*/
                    BlockPos unloaded = findUnloadedBuildZone(client.player, client.world);
                    /*?}*/
                    if (unloaded != null) {
                        ChatHelper.info("§bBuild site not loaded — walking there...");
                        PathWalker.walkToNearby(unloaded, (int) Math.ceil(range));
                        autoState = AutoState.WALKING_TO_CLEAR;
                    }
                }
            }
        }
    }

    private void disable() {
        disable(true);
    }

    private void disable(boolean announce) {
        enabled = false;
        clearBuildResult();
        PrinterDatabase.flushScaffoldIfDirty(); // persist any pending scaffold data
        saveCheckpoint();
        PlacementEngine.reset();
        PathWalker.stop();
        autoState = AutoState.IDLE;
        clearPendingBuildPlacement();
        buildPlacementFailures.clear();
        repairPriorityTargets.clear();
        resetPlacementFailureRecovery();
        resetBuildCooldownMemory();
        resetContainerTransferTracking();
        walkingSetbackPauseTicks = 0;
        buildHandoffSettleTicks = 0;
        int totalSetbacks = SetbackMonitor.get().totalSetbacks();
        observedWalkingSetbacks = totalSetbacks;
        observedPlacementSetbacks = totalSetbacks;
        SneakOverride.setForceSneak(false); // always release mixin sneak
        SneakOverride.setForceAbsoluteSneak(false);

        if (announce && statusMessages) {
            ChatHelper.info("Stopped. §e" + blocksPlaced + "§f blocks placed this session.");
        }
    }

    public void stopForQueueTransition() {
        if (enabled) {
            disable(false);
            return;
        }

        clearBuildResult();
        PlacementEngine.reset();
        PathWalker.stop();
        autoState = AutoState.IDLE;
        clearPendingBuildPlacement();
        buildPlacementFailures.clear();
        repairPriorityTargets.clear();
        resetPlacementFailureRecovery();
        resetBuildCooldownMemory();
        resetContainerTransferTracking();
        walkingSetbackPauseTicks = 0;
        int totalSetbacks = SetbackMonitor.get().totalSetbacks();
        observedWalkingSetbacks = totalSetbacks;
        observedPlacementSetbacks = totalSetbacks;
        SneakOverride.setForceSneak(false);
        SneakOverride.setForceAbsoluteSneak(false);
    }

    // Release all accumulated state on world disconnect to prevent leaks.
    public void onDisconnect() {
        if (enabled) disable();
        unreachableChests.clear();
        skippedItems.clear();
        failedZones.clear();
        clearPendingBuildPlacement();
        buildPlacementFailures.clear();
        resetPlacementFailureRecovery();
        resetBuildCooldownMemory();
        resetContainerTransferTracking();
        failedClearTargets.clear();
        repairPriorityTargets.clear();
        accessClearInProgress = false;
        lastMissingItems.clear();
    }

    // LITEMATICA AUTO-DETECTION

    public boolean tryAutoDetect() {
        List<LitematicaDetector.DetectedPlacement> placements =
                LitematicaDetector.detectPlacements();
        if (placements.isEmpty()) return false;

        int sameFilePlacementCount = 0;
        int unsupportedPlacements = 0;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        *//*?} else {*/
        BlockPos playerPos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
        /*?}*/

        // Prefer the nearest identity placement.
        LitematicaDetector.DetectedPlacement best = null;
        double bestDist = Double.MAX_VALUE;
        for (LitematicaDetector.DetectedPlacement p : placements) {
            if (p.hasUnsupportedTransform()) {
                unsupportedPlacements++;
                LOGGER.warn("Skipping placement '{}' — unsupported transform: {}",
                        p.name(), p.unsupportedTransformSummary());
                continue;
            }
            boolean originIsZero = p.originX() == 0 && p.originY() == 0 && p.originZ() == 0;
            double dx = p.originX() - playerPos.getX();
            double dy = p.originY() - playerPos.getY();
            double dz = p.originZ() - playerPos.getZ();
            double dist = dx * dx + dy * dy + dz * dz;

            // Ignore default-origin placements far from spawn.
            if (originIsZero && dist > 100) {
                LOGGER.warn("Skipping placement '{}' — origin is (0,0,0) and player is {} blocks away",
                        p.name(), (int) Math.sqrt(dist));
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }

        if (best == null) {
            // Everything got filtered out.
            if (unsupportedPlacements > 0) {
                ChatHelper.info("§e⚠ Found §f" + unsupportedPlacements
                        + "§e Litematica placement(s) using rotation, mirroring,"
                        + " or non-default sub-region placement.");
                ChatHelper.info("§7MOAR can only auto-detect identity placements right now."
                        + " Reset the placement in Litematica, or load manually and use"
                        + " §f/printer here§7.");
            } else if (!placements.isEmpty()) {
                ChatHelper.info("§e⚠ Found §f" + placements.size()
                        + "§e Litematica placement(s), but all appear to be at world origin."
                        + "\n§7Move your placement in Litematica to the build site,"
                        + " or use §f/printer load <file>§7 and §f/printer here§7.");
            }
            return false;
        }

        LitematicaDetector.DetectedPlacement placement = best;
        for (LitematicaDetector.DetectedPlacement p : placements) {
            if (!p.hasUnsupportedTransform()
                    && p.schematicPath().getFileName().equals(placement.schematicPath().getFileName())) {
                sameFilePlacementCount++;
            }
        }

        // Reuse the loaded schematic if only the origin remains.
        if (!Files.exists(placement.schematicPath())) {
            if (schematic != null) {
                this.anchor = new BlockPos(
                        placement.originX() + schematic.getOriginOffsetX(),
                        placement.originY() + schematic.getOriginOffsetY(),
                        placement.originZ() + schematic.getOriginOffsetZ());
                rebuildSchematicChunkIndex();
                this.autoDetected = true;
                warnIfAnchorSuspicious();
                return true;
            }
            ChatHelper.info("§cDetected placement file not found on disk: §7"
                    + placement.schematicPath().getFileName());
            return false;
        }

        try {
            this.schematic = LitematicaSchematic.load(placement.schematicPath());
            // Shift the anchor by the normalization offset.
            this.anchor = new BlockPos(
                    placement.originX() + schematic.getOriginOffsetX(),
                    placement.originY() + schematic.getOriginOffsetY(),
                    placement.originZ() + schematic.getOriginOffsetZ());

            // Use hologram correlation to break same-file ties.
            if (sameFilePlacementCount > 1) {
                BlockPos correlated = LitematicaDetector.detectAnchorFromSchematicWorld(schematic);
                if (correlated != null) {
                    this.anchor = correlated;
                    LOGGER.info("Disambiguated {} same-file placements via hologram correlation: {}",
                            sameFilePlacementCount, correlated);
                }
            }
            rebuildSchematicChunkIndex();
            this.blocksPlaced = 0;
            this.schematicPath = placement.schematicPath().normalize();
            this.schematicFile = placement.schematicPath().getFileName().toString();
            this.autoDetected = true;
            /*? if >=26.1 {*//*
            this.buildDimension = mc.level != null ? mc.level.dimension() : null;
            *//*?} else {*/
            this.buildDimension = mc.world != null ? mc.world.getRegistryKey() : null;
            /*?}*/
            PrinterDatabase.clearScaffold();
            MoarMod.getChestManager().clearSessionData();
            warnIfAnchorSuspicious();
            return true;
        } catch (IOException e) {
            ChatHelper.info("§cFailed to load detected schematic: " + e.getMessage());
            return false;
        }
    }

    // Syncs anchor to the matching Litematica placement origin.
    private boolean trySyncAnchor() {
        if (schematic == null || schematicFile == null) return false;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        List<LitematicaDetector.DetectedPlacement> placements =
                LitematicaDetector.detectPlacements();

        // Collect matching placements and reject transformed ones.
        List<LitematicaDetector.DetectedPlacement> fileMatches = new ArrayList<>();
        for (LitematicaDetector.DetectedPlacement p : placements) {
            String placementFile = p.schematicPath().getFileName().toString();
            if (!placementFile.equals(schematicFile)) continue;
            if (p.hasUnsupportedTransform()) {
                LOGGER.warn("Ignoring placement '{}' during anchor sync — unsupported transform: {}",
                        p.name(), p.unsupportedTransformSummary());
                continue;
            }
            fileMatches.add(p);
        }

        if (fileMatches.isEmpty()) return false;
        if (fileMatches.size() > 1) {
            LOGGER.debug("Anchor sync ambiguous: {} enabled placements match file '{}'",
                    fileMatches.size(), schematicFile);
            return false;
        }

        LitematicaDetector.DetectedPlacement bestMatch = fileMatches.get(0);

        if (bestMatch == null) return false;

        // Ignore default-origin placements far from spawn.
        if (bestMatch.originX() == 0 && bestMatch.originY() == 0 && bestMatch.originZ() == 0) {
            if (mc.player != null) {
                double dx = bestMatch.originX() - mc.player.getX();
                double dz = bestMatch.originZ() - mc.player.getZ();
                if (Math.sqrt(dx * dx + dz * dz) > 100) {
                    LOGGER.warn("Ignoring anchor sync — placement origin is (0,0,0) and player is far away");
                    return false;
                }
            }
        }

        BlockPos newAnchor = new BlockPos(
                bestMatch.originX() + schematic.getOriginOffsetX(),
                bestMatch.originY() + schematic.getOriginOffsetY(),
                bestMatch.originZ() + schematic.getOriginOffsetZ());

        if (!newAnchor.equals(anchor)) {
            ChatHelper.info("§eSynced anchor with Litematica placement → §e"
                    + newAnchor.getX() + " " + newAnchor.getY() + " " + newAnchor.getZ());
            this.anchor = newAnchor;
            rebuildSchematicChunkIndex();
        }
        return true;
    }

    // Check whether the current schematic is still present as an enabled
    // placement in Litematica.  Used by the periodic tick() validation.
    private boolean isPlacementStillActive() {
        if (schematicFile == null) return false;

        for (LitematicaDetector.DetectedPlacement p :
                LitematicaDetector.detectPlacements()) {
            if (p.schematicPath().getFileName().toString().equals(schematicFile)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSupportedPlacementMatch() {
        if (schematicFile == null) return false;

        for (LitematicaDetector.DetectedPlacement p : LitematicaDetector.detectPlacements()) {
            if (!p.schematicPath().getFileName().toString().equals(schematicFile)) continue;
            if (!p.hasUnsupportedTransform()) return true;
        }
        return false;
    }

    private LitematicaDetector.DetectedPlacement findUnsupportedPlacementMatch() {
        if (schematicFile == null) return null;

        for (LitematicaDetector.DetectedPlacement p : LitematicaDetector.detectPlacements()) {
            if (!p.schematicPath().getFileName().toString().equals(schematicFile)) continue;
            if (p.hasUnsupportedTransform()) return p;
        }
        return null;
    }

    // Warn the player if the current anchor looks suspicious — e.g. very
    // far from the player or sitting at world origin (0, y, 0), which is
    // almost always an un-moved default Litematica placement.
    private void warnIfAnchorSuspicious() {
        if (anchor == null) return;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return;

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/

        // Check for origin-zero anchor (common Litematica default)
        if (anchor.getX() == 0 && anchor.getZ() == 0) {
            ChatHelper.info("§e\u26A0 Anchor is at world origin (0, "
                    + anchor.getY() + ", 0) — this is usually a default"
                    + " Litematica placement that wasn't moved.");
            ChatHelper.info("§7If this is wrong, move the placement in"
                    + " Litematica or use §f/printer here§7 at the build site.");
            return;
        }

        // Check horizontal distance from player to anchor
        double dx = anchor.getX() - playerPos.getX();
        double dz = anchor.getZ() - playerPos.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 500) {
            ChatHelper.info("§e\u26A0 Build site is §f" + (int) horizDist
                    + "§e blocks away! This may be a mis-positioned placement.");
            ChatHelper.info("§7Use §f/printer here§7 to re-anchor at your"
                    + " position, or verify in Litematica.");
        }
    }

    public static List<LitematicaDetector.DetectedPlacement> detectAllPlacements() {
        return LitematicaDetector.detectPlacements();
    }

    // SCHEMATIC MANAGEMENT

    public void loadSchematic(Path path, BlockPos anchor) throws IOException {
        Path normalizedPath = path.normalize();
        this.schematic = LitematicaSchematic.load(normalizedPath);
        this.anchor = anchor;
        rebuildSchematicChunkIndex();
        this.blocksPlaced = 0;
        this.schematicPath = normalizedPath;
        this.schematicFile = normalizedPath.getFileName().toString();
        this.autoDetected = false;
        clearBuildResult();
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        this.buildDimension = mc.level != null ? mc.level.dimension() : null;
        *//*?} else {*/
        this.buildDimension = mc.world != null ? mc.world.getRegistryKey() : null;
        /*?}*/
        PrinterDatabase.clearScaffold();
        MoarMod.getChestManager().clearSessionData();
        resetPlacementFailureRecovery();
        resetBuildCooldownMemory();
        resetContainerTransferTracking();
        buildHandoffSettleTicks = 0;
    }

    public void unload() {
        this.schematic = null;
        this.anchor = null;
        schematicBlocksByChunk.clear();
        chunkDiffCache.clear();
        this.blocksPlaced = 0;
        this.schematicPath = null;
        this.schematicFile = null;
        this.autoDetected = false;
        this.buildDimension = null;
        clearBuildResult();
        PrinterCheckpoint.clear();
        PrinterDatabase.clearScaffold();
        MoarMod.getChestManager().clearSessionData();
        PathWalker.stop();
        autoState = AutoState.IDLE;
        buildHandoffSettleTicks = 0;
        clearBuildMaterialRun("unload");
        if (enabled) disable();
    }

    public boolean isLoaded()                         { return schematic != null && anchor != null; }
    public LitematicaSchematic getSchematic()          { return schematic; }
    public BlockPos getAnchor()                       { return anchor; }
    public Path getSchematicPath()                    { return schematicPath; }
    public String getSchematicFileName()              { return schematicFile; }

    // Updates the build anchor.  If AutoBuild is actively walking to a
    // build zone, the walk is cancelled and the state machine resets to
    // BUILDING so the next tick re-evaluates from the new anchor.
    public void setAnchor(BlockPos newAnchor) {
        BlockPos old = this.anchor;
        this.anchor = newAnchor;
        rebuildSchematicChunkIndex();

        if (enabled && autoBuild && old != null && newAnchor != null
                && !old.equals(newAnchor)) {
            PathWalker.stop();
            clearingDone = false;
            clearBreakTarget = null;
            lastClearTargetBlock = null;
            clearBlocksBroken = 0;
            consecutiveClearFailures = 0;
            clearStallResets = 0;
            failedClearTargets.clear();
            autoState = AutoState.CLEARING_AREA;
            noProgressTicks = 0;
            failedZones.clear();
            lastWalkTargetZone = null;
            walkFailCount = 0;
            triedPlacementWalk = false;
            resetPlacementFailureRecovery();
            resetBuildCooldownMemory();
            resetContainerTransferTracking();
        }
    }

    public void overrideAnchor(BlockPos newAnchor) {
        this.autoDetected = false;
        this.anchorCorrelated = false;
        this.anchorCorrelationCooldown = ANCHOR_CORRELATION_INTERVAL;
        setAnchor(newAnchor);
    }

    public BuildResult consumeBuildResult() {
        BuildResult result = buildResult;
        buildResult = BuildResult.NONE;
        return result;
    }

    private void markBuildResult(BuildResult result) {
        buildResult = result;
    }

    private void clearBuildResult() {
        buildResult = BuildResult.NONE;
    }

    public int getBlocksPlaced()                      { return blocksPlaced; }
    public AutoState getAutoState()                   { return autoState; }

    // settings accessors

    public int getBps()                 { return bps; }
    public void setBps(int bps)        { this.bps = Math.max(1, Math.min(20, bps)); }
    public double getRange()           { return range; }
    public void setRange(double range) { this.range = Math.max(2.0, Math.min(4.5, range)); }

    private double getClearReach() {
        return Math.min(range, CLEAR_INTERACTION_RANGE);
    }

    private void clearPendingBuildPlacement() {
        if (pendingBuildPlacementPos != null) {
            PlacementEngine.clearVerificationStatus(pendingBuildPlacementPos);
        }
        pendingBuildPlacementPos = null;
        pendingBuildPlacementState = null;
        pendingBuildPlacementSupportPos = null;
        pendingBuildPlacementStancePos = null;
        pendingBuildPlacementStartTick = -1;
    }

    private void resetPlacementFailureRecovery() {
        placementFailurePauseTicks = 0;
        buildGateStallTicks = 0;
        deferredPlacementReposition = false;
        deferredPlacementRepositionPos = null;
        clearPlacementStartFailureState();
    }

    private void resetBuildCooldownMemory() {
        cooledDownPlacementTargets.clear();
        cooledDownPlacementAttempts.clear();
        buildLayerAccessFailures.clear();
        cooledDownBuildLayers.clear();
        buildLayerCooldownStage.clear();
        clearBuildMaterialRun("reset");
        clearBuildInventorySwapWait();
    }

    private void prepareForBuildFromCurrentStance() {
        PlacementEngine.reset();
        clearPendingBuildPlacement();
        resetPlacementFailureRecovery();
        noProgressTicks = 0;
    }

    private void resetBuildPlannerState() {
        clearPendingBuildPlacement();
        buildPlacementFailures.clear();
        resetPlacementFailureRecovery();
        failedZones.clear();
        resetScaffoldScanMemory();
        walkFailCount = 0;
        triedPlacementWalk = false;
        lastWalkTargetZone = null;
        lastWalkStandPos = null;
        lastWalkApproachPos = null;
        noProgressTicks = 0;
        walkAttemptCooldown = 0;
        stuckCycles = 0;
        walkingSetbackPauseTicks = 0;
        buildHandoffSettleTicks = 0;
        buildGateStallTicks = 0;
        remainingCacheTick = Long.MIN_VALUE;
        clearBuildInventorySwapWait();
    }

    private void refreshPlacementPlannerState() {
        resetBuildPlannerState();
        rebuildSchematicChunkIndex();
    }

    private void enterBuildMode(String reason) {
        enterBuildMode(reason, true);
    }

    private void resumeBuildFromCurrentStance(String reason) {
        enterBuildMode(reason, false);
    }

    private void enterBuildMode(String reason, boolean resetPlanner) {
        PathWalker.stop();
        if (resetPlanner) {
            resetBuildPlannerState();
        } else {
            prepareForBuildFromCurrentStance();
            walkFailCount = 0;
            triedPlacementWalk = false;
            walkAttemptCooldown = 0;
            stuckCycles = 0;
            walkingSetbackPauseTicks = 0;
            buildGateStallTicks = 0;
        }
        autoState = AutoState.BUILDING;
        buildHandoffSettleTicks = BUILD_HANDOFF_SETTLE_TICKS;
        PacketTelemetry.mark("enter build reason=" + reason
                + " planner=" + (resetPlanner ? "reset" : "resume")
                + " settle=" + buildHandoffSettleTicks);
        LOGGER.debug("Entering build mode ({}, planner={})",
                reason, resetPlanner ? "reset" : "resume");
    }

    /*? if >=26.1 {*//*
    private boolean awaitBuildHandoffSettle(Minecraft mc) {
    *//*?} else {*/
    private boolean awaitBuildHandoffSettle(MinecraftClient mc) {
    /*?}*/
        if (buildHandoffSettleTicks <= 0) {
            return false;
        }
        if (PathWalker.isActive()) {
            PathWalker.tick();
        }
        if (PathWalker.isActive()
                || PlacementEngine.isBusy()
                || !"IDLE".equals(PlacementEngine.getPhase())
                || !SetbackMonitor.get().isCalm()) {
            PacketTelemetry.mark("build handoff waiting ticks=" + buildHandoffSettleTicks
                    + " walker=" + PathWalker.isActive()
                    + " busy=" + PlacementEngine.isBusy()
                    + " phase=" + PlacementEngine.getPhase()
                    + " calm=" + SetbackMonitor.get().isCalm());
            return true;
        }
        buildHandoffSettleTicks--;
        if (buildHandoffSettleTicks == 0) {
            PacketTelemetry.mark("build handoff ready");
        }
        return true;
    }

    /*? if >=26.1 {*//*
    private boolean canBuildFromCurrentStance(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean canBuildFromCurrentStance(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) {
            return false;
        }
        /*? if >=26.1 {*//*
        if (!player.onGround()) {
        *//*?} else {*/
        if (!player.isOnGround()) {
        /*?}*/
            return false;
        }
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/
        if (hasBuildOpportunityFromHere(player, world, false)) {
            return true;
        }
        if (lastWalkTargetZone != null) {
            int maxVerticalGap = Math.max(2, (int) Math.ceil(range) + 1);
            if (playerPos.getY() < lastWalkTargetZone.getY() - maxVerticalGap) {
                return false;
            }
        }
        return false;
    }

    private void markCurrentBuildApproachFailed(String reason) {
        if (lastWalkTargetZone != null) {
            addFailedZoneFootprint(lastWalkTargetZone, 3);
        }
        if (lastWalkStandPos != null) {
            addFailedZoneFootprint(lastWalkStandPos, 4);
        }
        if (lastWalkApproachPos != null) {
            addFailedZoneFootprint(lastWalkApproachPos, 5);
        }
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            addFailedZoneFootprint(mc.player.blockPosition(), 4);
        }
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            addFailedZoneFootprint(mc.player.getBlockPos(), 4);
        }
        /*?}*/
        invalidateBuildStagingPlanCache();
        walkAttemptCooldown = 0;
        noProgressTicks = Math.max(noProgressTicks, NO_PROGRESS_WALK_RECHECK_TICKS - 1);
        PacketTelemetry.mark("build approach failed reason=" + reason
                + " zone=" + posLabel(lastWalkTargetZone)
                + " stand=" + posLabel(lastWalkStandPos)
                + " approach=" + posLabel(lastWalkApproachPos));
        LOGGER.debug("Marking current build approach failed ({}) zone={} stand={} approach={}",
                reason,
                lastWalkTargetZone != null ? posLabel(lastWalkTargetZone) : "null",
                lastWalkStandPos != null ? posLabel(lastWalkStandPos) : "null",
                lastWalkApproachPos != null ? posLabel(lastWalkApproachPos) : "null");
    }

    private void schedulePlacementAccessReposition(BlockPos target, String reason, int pauseTicks) {
        if (target == null) {
            return;
        }
        deferredPlacementReposition = true;
        /*? if >=26.1 {*//*
        deferredPlacementRepositionPos = target.immutable();
        *//*?} else {*/
        deferredPlacementRepositionPos = target.toImmutable();
        /*?}*/
        placementFailurePauseTicks = Math.max(placementFailurePauseTicks, Math.max(1, pauseTicks));
        walkAttemptCooldown = Math.max(walkAttemptCooldown, Math.max(1, pauseTicks));
        noProgressTicks = 0;
        stuckCycles = 0;
        walkFailCount = 0;
        triedPlacementWalk = false;
        invalidateBuildStagingPlanCache();
        PacketTelemetry.mark("place blocked recovery target=" + posLabel(target)
                + " reason=" + reason
                + " pause=" + placementFailurePauseTicks);
    }

    private void deferBuildApproachReplan(String reason) {
        PathWalker.stop();
        walkFailCount = 0;
        triedPlacementWalk = false;
        noProgressTicks = 0;
        walkAttemptCooldown = Math.max(
                walkAttemptCooldown, NO_PROGRESS_WALK_RECHECK_TICKS * 2);
        autoState = AutoState.BUILDING;
        buildHandoffSettleTicks = BUILD_HANDOFF_SETTLE_TICKS;
        PacketTelemetry.mark("build approach defer reason=" + reason
                + " zone=" + posLabel(lastWalkTargetZone)
                + " stand=" + posLabel(lastWalkStandPos)
                + " approach=" + posLabel(lastWalkApproachPos)
                + " settle=" + buildHandoffSettleTicks);
        LOGGER.debug("Deferring build approach replan ({}) zone={} stand={} approach={}",
                reason,
                lastWalkTargetZone != null ? posLabel(lastWalkTargetZone) : "null",
                lastWalkStandPos != null ? posLabel(lastWalkStandPos) : "null",
                lastWalkApproachPos != null ? posLabel(lastWalkApproachPos) : "null");
    }

    private void resetScaffoldScanMemory() {
        lastScaffoldScanBlocks.clear();
        scaffoldScanSeeded = false;
    }

    private void addFailedZoneFootprint(BlockPos center, int radius) {
        if (center == null) {
            return;
        }
        failedZones.add(center);
        int r = Math.max(0, radius);
        int radiusSq = r * r;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                failedZones.add(center.add(dx, 0, dz));
            }
        }
    }

    private void rememberFailedBuildZone(BlockPos center, int radius) {
        addFailedZoneFootprint(center, radius);
        invalidateBuildStagingPlanCache();
        cachedActiveBandTick = Long.MIN_VALUE;
    }

    private void rememberSoftNoAccessBuildTarget(BlockPos center) {
        if (center == null) {
            return;
        }
        // Fix #10: pick cooldown based on the last findBuildAccessTarget reject
        // breakdown. If considered=0, every candidate stance failed basic
        // feet/head/ground checks \u2014 the zone is geometrically entombed and
        // only an adjacent placement can change that. Otherwise it's a softer
        // "no reachable stance right now" and may clear as the player moves.
        int considered = lastBuildAccessRejectConsidered;
        lastBuildAccessRejectConsidered = -1; // consume signal
        int cooldown = (considered == 0)
                ? ENTOMBED_ZONE_COOLDOWN_TICKS
                : NO_ACCESS_ZONE_COOLDOWN_TICKS;
        coolDownPlacementTarget(center, cooldown);
        recordBuildLayerAccessFailure(center,
                considered == 0 ? "entombed access" : "blocked access");
        PacketTelemetry.mark("build access target cooldown zone="
                + posLabel(center)
                + " ticks=" + cooldown
                + (considered == 0 ? " entombed" : ""));
    }

    /*? if >=26.1 {*//*
    private boolean isAtCurrentBuildStagingPosition(LocalPlayer player) {
    *//*?} else {*/
    private boolean isAtCurrentBuildStagingPosition(ClientPlayerEntity player) {
    /*?}*/
        if (player == null) {
            return false;
        }
        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
        return isNearBuildStagingPosition(playerPos, lastWalkStandPos)
                || isNearBuildStagingPosition(playerPos, lastWalkApproachPos);
    }

    /*? if >=26.1 {*//*
    private boolean isNearBuildStagingPosition(Vec3 playerPos, BlockPos stagingPos) {
    *//*?} else {*/
    private boolean isNearBuildStagingPosition(Vec3d playerPos, BlockPos stagingPos) {
    /*?}*/
        if (playerPos == null || stagingPos == null) {
            return false;
        }
        /*? if >=26.1 {*//*
        return playerPos.distanceToSqr(Vec3.atCenterOf(stagingPos)) <= 4.0;
        *//*?} else {*/
        return playerPos.squaredDistanceTo(Vec3d.ofCenter(stagingPos)) <= 4.0;
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private boolean shouldResumeBuildFromCurrentWalk(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean shouldResumeBuildFromCurrentWalk(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) {
            return false;
        }
        if (canBuildFromCurrentStance(player, world)) {
            return true;
        }
        if (!isAtCurrentBuildStagingPosition(player)) {
            return false;
        }
        if (hasBuildHandoffOpportunityFromHere(player, world)) {
            return true;
        }
        if (lastWalkTargetZone != null && !isPlacementTargetCoolingDown(lastWalkTargetZone)) {
            PacketTelemetry.mark("build walk soft handoff zone="
                    + posLabel(lastWalkTargetZone)
                    + " stance=" + posLabel(lastWalkStandPos != null
                    ? lastWalkStandPos
                    : lastWalkApproachPos));
            return true;
        }
        return false;
    }

    /*? if >=26.1 {*//*
    private boolean canForceBuildEvaluationFromCurrentWalk(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean canForceBuildEvaluationFromCurrentWalk(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null || lastWalkTargetZone == null
                || schematic == null || anchor == null) {
            return false;
        }
        if (!isAtCurrentBuildStagingPosition(player)) {
            return false;
        }
        if (hasBuildHandoffOpportunityFromHere(player, world)) {
            return true;
        }
        if (lastWalkStandPos == null && lastWalkApproachPos != null
                && canProbeLastWalkTargetFromCurrentApproach(player, world)) {
            PacketTelemetry.mark("build approach probe staging="
                    + posLabel(lastWalkApproachPos)
                    + " zone=" + posLabel(lastWalkTargetZone));
            return true;
        }
        int sx = lastWalkTargetZone.getX() - anchor.getX();
        int sy = lastWalkTargetZone.getY() - anchor.getY();
        int sz = lastWalkTargetZone.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) {
            return false;
        }
        BlockState target = schematic.getBlockState(sx, sy, sz);
        if (target.isAir()
                || isAutoCreatedPart(target)
                || !matchesCurrentBuildPass(target)
                || isPlacementTargetCoolingDown(lastWalkTargetZone)
                || isEffectivelyPlaced(world.getBlockState(lastWalkTargetZone), target)) {
            return false;
        }
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/
        if (!isCandidateStableForPlacement(playerPos, lastWalkTargetZone)
                || !PlacementEngine.canPlaceFromCurrentPosition(
                        lastWalkTargetZone, target, player, world,
                        createBuildSupportFilter(lastWalkTargetZone, world, playerPos))) {
            return false;
        }
        /*? if >=26.1 {*//*
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(lastWalkTargetZone))
                <= (range + 1.0) * (range + 1.0);
        *//*?} else {*/
        return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone))
                <= (range + 1.0) * (range + 1.0);
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private boolean canProbeLastWalkTargetFromCurrentApproach(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean canProbeLastWalkTargetFromCurrentApproach(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null || lastWalkTargetZone == null) {
            return false;
        }
        BlockState target = getDesiredBuildStateAt(lastWalkTargetZone);
        if (target == null
                || (!isPlaceable(target) && !isLiquidSource(target))
                || !matchesCurrentBuildPass(target)
                || isPlacementTargetCoolingDown(lastWalkTargetZone)) {
            return false;
        }
        BlockState current = world.getBlockState(lastWalkTargetZone);
        if (isEffectivelyPlaced(current, target)) {
            invalidateChunkDiffCache(lastWalkTargetZone);
            return false;
        }
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        int activeBandTopY = getBottomUpActiveBandTopY(world, getRenderWindow(playerPos));
        if (lastWalkTargetZone.getY() > activeBandTopY) {
            return false;
        }
        if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, lastWalkTargetZone)) {
            return false;
        }
        if (!BlockDependency.isReadyToPlace(world, lastWalkTargetZone, target)
                || !hasBuildPlacementFrontierSupport(lastWalkTargetZone, target, world)
                || wouldTrapPlayer(player, lastWalkTargetZone, world)
                || !isCandidateStableForPlacement(playerPos, lastWalkTargetZone)) {
            return false;
        }
        double rangeSq = range * range;
        /*? if >=26.1 {*//*
        return eyePos.distanceToSqr(Vec3.atCenterOf(lastWalkTargetZone)) <= rangeSq;
        *//*?} else {*/
        return eyePos.squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone)) <= rangeSq;
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private boolean canBuildSpecificTargetFromCurrentStance(LocalPlayer player,
                                                            Level world,
                                                            BlockPos targetPos) {
    *//*?} else {*/
    private boolean canBuildSpecificTargetFromCurrentStance(ClientPlayerEntity player,
                                                            World world,
                                                            BlockPos targetPos) {
    /*?}*/
        if (player == null || world == null || targetPos == null) {
            return false;
        }
        BlockState target = getDesiredBuildStateAt(targetPos);
        if (target == null
                || (!isPlaceable(target) && !isLiquidSource(target))
                || !matchesCurrentBuildPass(target)
                || isPlacementTargetCoolingDown(targetPos)) {
            return false;
        }
        BlockState current = world.getBlockState(targetPos);
        if (isEffectivelyPlaced(current, target)) {
            invalidateChunkDiffCache(targetPos);
            return false;
        }
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        int activeBandTopY = getBottomUpActiveBandTopY(world, getRenderWindow(playerPos));
        if (targetPos.getY() > activeBandTopY) {
            return false;
        }
        if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, targetPos)) {
            return false;
        }
        if (!BlockDependency.isReadyToPlace(world, targetPos, target)
                || !hasBuildPlacementFrontierSupport(targetPos, target, world)
                || wouldTrapPlayer(player, targetPos, world)
                || !isCandidateStableForPlacement(playerPos, targetPos)) {
            return false;
        }
        double rangeSq = range * range;
        /*? if >=26.1 {*//*
        return eyePos.distanceToSqr(Vec3.atCenterOf(targetPos)) <= rangeSq;
        *//*?} else {*/
        return eyePos.squaredDistanceTo(Vec3d.ofCenter(targetPos)) <= rangeSq;
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private boolean isBuildTargetStillActionable(BlockPos targetPos, Level world,
                                                 boolean respectCooldown) {
    *//*?} else {*/
    private boolean isBuildTargetStillActionable(BlockPos targetPos, World world,
                                                 boolean respectCooldown) {
    /*?}*/
        if (targetPos == null || world == null) {
            return false;
        }
        BlockState target = getDesiredBuildStateAt(targetPos);
        if (target == null
                || (!isPlaceable(target) && !isLiquidSource(target))
                || !matchesCurrentBuildPass(target)) {
            return false;
        }
        if (respectCooldown && isBuildTargetCoolingDown(targetPos)) {
            return false;
        }
        if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, targetPos)) {
            return false;
        }
        if (!BlockDependency.isReadyToPlace(world, targetPos, target)) {
            return false;
        }
        if (!hasBuildPlacementFrontierSupport(targetPos, target, world)) {
            return false;
        }
        if (isEffectivelyPlaced(world.getBlockState(targetPos), target)) {
            invalidateChunkDiffCache(targetPos);
            return false;
        }
        return true;
    }

    /*? if >=26.1 {*//*
    private boolean handleBuildSetback(Minecraft mc) {
    *//*?} else {*/
    private boolean handleBuildSetback(MinecraftClient mc) {
    /*?}*/
        SetbackMonitor setbackMonitor = SetbackMonitor.get();
        int totalSetbacks = setbackMonitor.totalSetbacks();
        if (totalSetbacks != observedPlacementSetbacks) {
            observedPlacementSetbacks = totalSetbacks;
            PlacementEngine.reset();
            PlacementEngine.clearCorrectionHistory();
            PathWalker.stop();
            refreshPlacementPlannerState();
            placementFailurePauseTicks = Math.max(
                    placementFailurePauseTicks, PLACEMENT_FAILURE_PAUSE_TICKS);
            walkingSetbackPauseTicks = 0;
            PacketTelemetry.mark("build setback refresh total=" + totalSetbacks);
            LOGGER.debug("Setback detected while building — refreshing placement planner");
            return true;
        }
        if (!setbackMonitor.isCalm()) {
            return true;
        }
        return false;
    }

    private void clearPendingContainerAction() {
        pendingContainerLane = ContainerTransferLane.NONE;
        pendingContainerSyncId = -1;
        pendingContainerSlot = -1;
        pendingContainerSlotSnapshot = ItemStack.EMPTY;
        pendingContainerTicks = 0;
    }

    private void resetContainerTransferTracking() {
        clearPendingContainerAction();
        completedContainerLane = ContainerTransferLane.NONE;
        completedContainerChanged = false;
        blockedRestockSlots.clear();
        blockedDumpSlots.clear();
        blockedShulkerSlots.clear();
    }

    /*? if >=26.1 {*//*
    private int getContainerSyncId(AbstractContainerMenu handler) {
        return handler.containerId;
    }
    *//*?} else {*/
    private int getContainerSyncId(ScreenHandler handler) {
        return handler.syncId;
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private ItemStack copyContainerSlotStack(AbstractContainerMenu handler, int slot) {
        return handler.getSlot(slot).getItem().copy();
    }
    *//*?} else {*/
    private ItemStack copyContainerSlotStack(ScreenHandler handler, int slot) {
        return handler.getSlot(slot).getStack().copy();
    }
    /*?}*/

    private boolean sameContainerSlotSnapshot(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() != b.isEmpty()) return false;
        if (a.getCount() != b.getCount()) return false;
        return ItemIdentifier.getItemId(a).equals(ItemIdentifier.getItemId(b));
    }

    /*? if >=26.1 {*//*
    private void beginPendingContainerAction(AbstractContainerMenu handler, int slot,
                                             ContainerTransferLane lane) {
    *//*?} else {*/
    private void beginPendingContainerAction(ScreenHandler handler, int slot,
                                             ContainerTransferLane lane) {
    /*?}*/
        completedContainerLane = ContainerTransferLane.NONE;
        completedContainerChanged = false;
        pendingContainerLane = lane;
        pendingContainerSyncId = getContainerSyncId(handler);
        pendingContainerSlot = slot;
        pendingContainerSlotSnapshot = copyContainerSlotStack(handler, slot);
        pendingContainerTicks = 0;
    }

    /*? if >=26.1 {*//*
    private boolean tickPendingContainerAction(AbstractContainerMenu handler,
                                               ContainerTransferLane lane,
                                               Set<Integer> blockedSlots) {
    *//*?} else {*/
    private boolean tickPendingContainerAction(ScreenHandler handler,
                                               ContainerTransferLane lane,
                                               Set<Integer> blockedSlots) {
    /*?}*/
        if (pendingContainerLane != lane) return false;
        if (handler == null || getContainerSyncId(handler) != pendingContainerSyncId) {
            clearPendingContainerAction();
            return false;
        }
        if (pendingContainerSlot < 0 || pendingContainerSlot >= handler.slots.size()) {
            clearPendingContainerAction();
            return false;
        }

        ItemStack current = copyContainerSlotStack(handler, pendingContainerSlot);
        if (!sameContainerSlotSnapshot(current, pendingContainerSlotSnapshot)) {
            completedContainerLane = lane;
            completedContainerChanged = true;
            clearPendingContainerAction();
            return true;
        }

        pendingContainerTicks++;
        if (pendingContainerTicks < CONTAINER_ACTION_TIMEOUT_TICKS) {
            return true;
        }

        blockedSlots.add(pendingContainerSlot);
        completedContainerLane = lane;
        completedContainerChanged = false;
        LOGGER.debug("Container action timed out lane={} slot={} syncId={} item={}",
                lane, pendingContainerSlot, pendingContainerSyncId,
                ItemIdentifier.getItemId(pendingContainerSlotSnapshot));
        clearPendingContainerAction();
        return true;
    }

    private boolean consumeCompletedContainerAction(ContainerTransferLane lane) {
        if (completedContainerLane != lane) return false;
        boolean changed = completedContainerChanged;
        completedContainerLane = ContainerTransferLane.NONE;
        completedContainerChanged = false;
        return changed;
    }

    private void pruneCooledDownPlacementTargets(long now) {
        if (now < 0 || cooledDownPlacementTargets.isEmpty()) return;
        cooledDownPlacementTargets.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void pruneCooledDownPlacementAttempts(long now) {
        if (now < 0 || cooledDownPlacementAttempts.isEmpty()) return;
        cooledDownPlacementAttempts.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void pruneCooledDownBuildLayers(long now) {
        if (now < 0 || cooledDownBuildLayers.isEmpty()) return;
        cooledDownBuildLayers.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private boolean isBuildLayerCoolingDown(int y) {
        long now = getWorldTick();
        pruneCooledDownBuildLayers(now);
        Long expiry = cooledDownBuildLayers.get(y);
        return expiry != null && expiry > now;
    }

    private boolean isBuildTargetCoolingDown(BlockPos pos) {
        return pos != null
                && (isBuildLayerCoolingDown(pos.getY()) || isPlacementTargetCoolingDown(pos));
    }

    private void recordBuildLayerAccessFailure(BlockPos zone, String reason) {
        long now = getWorldTick();
        if (zone == null || now < 0) return;
        int y = zone.getY();
        int failures = buildLayerAccessFailures.merge(y, 1, Integer::sum);
        if (failures < BUILD_LAYER_ACCESS_FAILURE_THRESHOLD) {
            if (failures == 1 || failures == BUILD_LAYER_ACCESS_FAILURE_THRESHOLD / 2) {
                PacketTelemetry.mark("build layer failure y=" + y
                        + " reason=" + reason
                        + " count=" + failures
                        + "/" + BUILD_LAYER_ACCESS_FAILURE_THRESHOLD);
            }
            return;
        }
        buildLayerAccessFailures.put(y, 0);
        // Exponential schedule: stage 0 -> 240t, stage 1 -> 720t, stage 2 -> 2160t,
        // capped at BUILD_LAYER_COOLDOWN_MAX_TICKS. Stage advances on each re-trigger.
        int stage = buildLayerCooldownStage.getOrDefault(y, 0);
        long cooldownTicks = BUILD_LAYER_COOLDOWN_TICKS;
        for (int i = 0; i < stage; i++) {
            cooldownTicks *= BUILD_LAYER_COOLDOWN_GROWTH_FACTOR;
            if (cooldownTicks >= BUILD_LAYER_COOLDOWN_MAX_TICKS) {
                cooldownTicks = BUILD_LAYER_COOLDOWN_MAX_TICKS;
                break;
            }
        }
        buildLayerCooldownStage.put(y, stage + 1);
        cooledDownBuildLayers.put(y, now + cooldownTicks);
        cachedActiveBandTick = Long.MIN_VALUE;
        PacketTelemetry.mark("build layer cooldown y=" + y
                + " reason=" + reason
                + " ticks=" + cooldownTicks
                + " stage=" + (stage + 1));
    }

    private boolean isPlacementTargetCoolingDown(BlockPos pos) {
        if (pos == null) return false;
        long now = getWorldTick();
        pruneCooledDownPlacementTargets(now);
        Long expiry = cooledDownPlacementTargets.get(pos);
        return expiry != null && expiry > now;
    }

    private boolean isPlacementAttemptCoolingDown(BlockPos target, BlockPos support, BlockPos stance) {
        if (target == null || support == null || stance == null) return false;
        long now = getWorldTick();
        pruneCooledDownPlacementAttempts(now);
        Long expiry = cooledDownPlacementAttempts.get(createPlacementAttemptKey(target, support, stance));
        return expiry != null && expiry > now;
    }

    private void coolDownPlacementTarget(BlockPos pos, int ticks) {
        long now = getWorldTick();
        if (pos == null || ticks <= 0 || now < 0) return;
        pruneCooledDownPlacementTargets(now);
        /*? if >=26.1 {*//*
        cooledDownPlacementTargets.put(pos.immutable(), now + ticks);
        *//*?} else {*/
        cooledDownPlacementTargets.put(pos.toImmutable(), now + ticks);
        /*?}*/
        cachedActiveBandTick = Long.MIN_VALUE;
    }

    private void recordNoVisibleHitPlacementBlock(BlockPos pos, BlockState target) {
        coolDownPlacementTarget(pos, NO_VISIBLE_HIT_TARGET_COOLDOWN_TICKS);
        recordBuildLayerAccessFailure(pos, "no visible hit");
        PacketTelemetry.mark("build no-hit cooldown target=" + pos
                + " desired=" + (target != null ? target.getBlock() : "unknown")
                + " ticks=" + NO_VISIBLE_HIT_TARGET_COOLDOWN_TICKS);
    }

    private boolean isWrongSideProbeFailure(String probeFailure) {
        return probeFailure != null && probeFailure.startsWith("wrong-side ");
    }

    private void recoverWrongSidePlacement(BlockPos pos, BlockState target, String probeFailure) {
        recordBuildLayerAccessFailure(pos, "wrong-side local stance");
        schedulePlacementAccessReposition(
                pos,
                "wrong-side support face",
                PLACEMENT_START_FAILURE_PAUSE_TICKS);
        PacketTelemetry.mark("build wrong-side recovery target=" + pos
                + " desired=" + (target != null ? target.getBlock() : "unknown")
                + " probe=" + probeFailure);
    }

    private void coolDownPlacementTargets(Collection<BlockPos> positions, int ticks, String reason) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        int count = 0;
        for (BlockPos pos : positions) {
            if (pos == null || isPlacementTargetCoolingDown(pos)) {
                continue;
            }
            coolDownPlacementTarget(pos, ticks);
            count++;
        }
        if (count > 0) {
            PacketTelemetry.mark("build cooldown targets reason=" + reason
                    + " count=" + count
                    + " ticks=" + ticks);
        }
    }

    private void coolDownPlacementAttempt(BlockPos target, BlockPos support, BlockPos stance, int ticks) {
        long now = getWorldTick();
        if (target == null || support == null || stance == null || ticks <= 0 || now < 0) return;
        pruneCooledDownPlacementAttempts(now);
        cooledDownPlacementAttempts.put(createPlacementAttemptKey(target, support, stance), now + ticks);
        cachedActiveBandTick = Long.MIN_VALUE;
    }

    private void clearPlacementAttemptCooldownsForTarget(BlockPos target) {
        if (target == null || cooledDownPlacementAttempts.isEmpty()) return;
        cooledDownPlacementAttempts.keySet().removeIf(key -> key.target().equals(target));
    }

    private PlacementAttemptKey createPlacementAttemptKey(BlockPos target, BlockPos support, BlockPos stance) {
        return new PlacementAttemptKey(immutableBlockPos(target), immutableBlockPos(support), immutableBlockPos(stance));
    }

    private BlockPos immutableBlockPos(BlockPos pos) {
        if (pos == null) return null;
        /*? if >=26.1 {*//*
        return pos.immutable();
        *//*?} else {*/
        return pos.toImmutable();
        /*?}*/
    }

    private void beginPlacementStartFailurePause(BlockPos pos) {
        BlockPos immutablePos = null;
        if (pos != null) {
            /*? if >=26.1 {*//*
            immutablePos = pos.immutable();
            *//*?} else {*/
            immutablePos = pos.toImmutable();
            /*?}*/
        }
        if (immutablePos != null && immutablePos.equals(lastPlacementStartFailurePos)) {
            placementStartFailureStreak++;
        } else {
            lastPlacementStartFailurePos = immutablePos;
            placementStartFailureStreak = immutablePos != null ? 1 : 0;
        }
        if (pos != null) {
            coolDownPlacementTarget(pos, PLACEMENT_START_FAILURE_TARGET_COOLDOWN_TICKS);
        }
        placementFailurePauseTicks = Math.max(
                placementFailurePauseTicks, PLACEMENT_START_FAILURE_PAUSE_TICKS);
        walkAttemptCooldown = Math.max(
                walkAttemptCooldown, PLACEMENT_START_FAILURE_PAUSE_TICKS);
        if (immutablePos != null
                && placementStartFailureStreak >= PLACEMENT_START_REPOSITION_THRESHOLD) {
            deferredPlacementReposition = true;
            deferredPlacementRepositionPos = immutablePos;
            placementFailurePauseTicks = Math.max(
                    placementFailurePauseTicks, PLACEMENT_FAILURE_PAUSE_TICKS);
            walkAttemptCooldown = Math.max(
                    walkAttemptCooldown, PLACEMENT_FAILURE_PAUSE_TICKS);
        }
    }

    private void recordBuildPlacementAttempt(BlockPos pos, BlockState state,
                                             BlockPos supportPos, BlockPos stancePos) {
        if (pos == null || state == null) return;
        invalidateChunkDiffCache(pos);
        pendingBuildPlacementPos = immutableBlockPos(pos);
        pendingBuildPlacementState = state;
        pendingBuildPlacementSupportPos = immutableBlockPos(supportPos);
        pendingBuildPlacementStancePos = immutableBlockPos(stancePos);
        pendingBuildPlacementStartTick = getWorldTick();
    }

    private void clearPlacementStartFailureState() {
        lastPlacementStartFailurePos = null;
        placementStartFailureStreak = 0;
    }

    private long getWorldTick() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;
        return mc.level.getGameTime();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return -1;
        return mc.world.getTime();
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private boolean handlePendingBuildPlacement(Minecraft mc) {
    *//*?} else {*/
    private boolean handlePendingBuildPlacement(MinecraftClient mc) {
    /*?}*/
        if (pendingBuildPlacementPos == null || pendingBuildPlacementState == null) {
            return false;
        }

        PlacementEngine.VerificationStatus status = PlacementEngine.getVerificationStatus(
                pendingBuildPlacementPos, pendingBuildPlacementState);
        if (status == PlacementEngine.VerificationStatus.PENDING) {
            long now = getWorldTick();
            if (now >= 0 && pendingBuildPlacementStartTick >= 0) {
                long elapsed = now - pendingBuildPlacementStartTick;
                if (elapsed == 3 || elapsed == PLACEMENT_START_TIMEOUT_TICKS) {
                    PacketTelemetry.mark("build wait verification status=PENDING elapsed="
                            + elapsed + " target=" + pendingBuildPlacementPos);
                }
            }
            return true;
        }

        if (status == PlacementEngine.VerificationStatus.NONE) {
            long now = getWorldTick();
            if (now >= 0 && pendingBuildPlacementStartTick >= 0
                    && now - pendingBuildPlacementStartTick < PLACEMENT_START_TIMEOUT_TICKS) {
                long elapsed = now - pendingBuildPlacementStartTick;
                if (elapsed == 3 || elapsed == PLACEMENT_START_TIMEOUT_TICKS - 1) {
                    PacketTelemetry.mark("build wait verification status=NONE elapsed="
                            + elapsed + " target=" + pendingBuildPlacementPos);
                }
                return true;
            }
            clearPendingBuildPlacement();
            return false;
        }

        BlockPos pos = pendingBuildPlacementPos;
        invalidateChunkDiffCache(pos);
        if (status == PlacementEngine.VerificationStatus.ACCEPTED) {
            buildPlacementFailures.remove(pos);
            cooledDownPlacementTargets.remove(pos);
            clearPlacementAttemptCooldownsForTarget(pos);
            buildLayerAccessFailures.remove(pos.getY());
            cooledDownBuildLayers.remove(pos.getY());
            deferredPlacementReposition = false;
            deferredPlacementRepositionPos = null;
            clearPlacementStartFailureState();
            clearPendingBuildPlacement();
            noProgressTicks = 0;
            failedZones.clear();
            walkFailCount = 0;
            triedPlacementWalk = false;
            stuckCycles = 0;
            lastWalkTargetZone = null;
            PacketTelemetry.mark("build verify accepted target=" + pos);
            return false;
        }

        BlockPos supportPos = pendingBuildPlacementSupportPos != null
                ? pendingBuildPlacementSupportPos
                : PlacementEngine.getVerificationSupportPos(pos);
        BlockPos stancePos = pendingBuildPlacementStancePos;
        int failures = buildPlacementFailures.merge(pos, 1, Integer::sum);
        LOGGER.debug("Placement {} at {} {} {} (failure {}/{})",
                status, pos.getX(), pos.getY(), pos.getZ(),
                failures, SERVER_TIMEOUT_REPOSITION_THRESHOLD);
        clearPendingBuildPlacement();

        SetbackMonitor setbackMonitor = SetbackMonitor.get();
        int recentSetbacks = setbackMonitor.recentSetbackCount(
                TIMEOUT_SETBACK_REPOSITION_WINDOW_TICKS);
        boolean retryAsVanilla = status == PlacementEngine.VerificationStatus.TIMEOUT
                && PlacementEngine.hasScheduledVanillaRetry(pos);
        boolean forceReposition = status == PlacementEngine.VerificationStatus.REJECTED
                || (status == PlacementEngine.VerificationStatus.TIMEOUT && !retryAsVanilla)
                || failures >= SERVER_TIMEOUT_REPOSITION_THRESHOLD
                || PlacementEngine.getConsecutiveFailures() >= SERVER_REJECT_THRESHOLD;
        beginPlacementFailureRecovery(
                mc, pos, supportPos, stancePos, status, failures, forceReposition, recentSetbacks);
        return true;
    }

    /*? if >=26.1 {*//*
    private void beginPlacementFailureRecovery(Minecraft mc, BlockPos pos,
                                               BlockPos supportPos,
                                               BlockPos stancePos,
                                               PlacementEngine.VerificationStatus status,
                                               int failureCount,
                                               boolean forceReposition,
                                               int recentSetbacks) {
    *//*?} else {*/
    private void beginPlacementFailureRecovery(MinecraftClient mc, BlockPos pos,
                                               BlockPos supportPos,
                                               BlockPos stancePos,
                                               PlacementEngine.VerificationStatus status,
                                               int failureCount,
                                               boolean forceReposition,
                                               int recentSetbacks) {
    /*?}*/
        boolean softTimeout = status == PlacementEngine.VerificationStatus.TIMEOUT
                && !forceReposition;
        int targetCooldown = 0;
        if (!softTimeout) {
            if (status == PlacementEngine.VerificationStatus.REJECTED) {
                targetCooldown = REJECTED_TARGET_COOLDOWN_TICKS;
            } else {
                int timeoutPenalty = Math.max(0, failureCount - 1) * TIMEOUT_TARGET_COOLDOWN_STEP_TICKS;
                targetCooldown = Math.min(
                        MAX_TIMEOUT_TARGET_COOLDOWN_TICKS,
                        TIMEOUT_TARGET_COOLDOWN_TICKS + timeoutPenalty);
            }
        }
        if (!softTimeout) {
            PlacementEngine.reset();
            PlacementEngine.clearCorrectionHistory();
            PathWalker.stop();
            coolDownPlacementTarget(pos, targetCooldown);
            coolDownPlacementAttempt(pos, supportPos, stancePos, targetCooldown);
        }
        int recoveryPause = softTimeout
                ? SOFT_TIMEOUT_FAILURE_PAUSE_TICKS
                : forceReposition
                ? PLACEMENT_FAILURE_PAUSE_TICKS + Math.min(8, Math.max(0, failureCount - 1) * 2)
                : PLACEMENT_FAILURE_PAUSE_TICKS;
        // Fix #18c: when consecutive timeouts are accumulating session-wide,
        // add a short adaptive drain pause so the server has time to catch up
        // before the next packet. Capped at 12 ticks so the steady-state rate
        // (after a single ACCEPTED clears the streak) is unaffected.
        if (status == PlacementEngine.VerificationStatus.TIMEOUT) {
            recoveryPause += PlacementEngine.getDrainPauseTicks();
        }
        placementFailurePauseTicks = Math.max(placementFailurePauseTicks, recoveryPause);
        deferredPlacementReposition = forceReposition;
        /*? if >=26.1 {*//*
        deferredPlacementRepositionPos = forceReposition && pos != null ? pos.immutable() : null;
        if (!softTimeout) {
            failedZones.add(mc.player.blockPosition());
        }
        *//*?} else {*/
        deferredPlacementRepositionPos = forceReposition && pos != null ? pos.toImmutable() : null;
        if (!softTimeout) {
            failedZones.add(mc.player.getBlockPos());
        }
        /*?}*/
        if (!softTimeout && pos != null) {
            failedZones.add(pos);
        }
        noProgressTicks = 0;
        stuckCycles = 0;
        walkFailCount = 0;
        triedPlacementWalk = false;
        if (!softTimeout) {
            walkingSetbackPauseTicks = Math.max(walkingSetbackPauseTicks, recoveryPause);
            walkAttemptCooldown = Math.max(walkAttemptCooldown, recoveryPause);
        }
        PacketTelemetry.mark("build recovery status=" + status
                + " failures=" + failureCount
                + " forceReposition=" + forceReposition
                + " softTimeout=" + softTimeout
                + " pause=" + placementFailurePauseTicks
                + " cooldown=" + targetCooldown
                + " recentSetbacks=" + recentSetbacks
                + " target=" + pos
                + " support=" + supportPos
                + " stance=" + stancePos);
        LOGGER.debug("Placement failure recovery: status={} failures={} recentSetbacks={} forceReposition={} pause={} cooldown={} target={} {} {} support={} stance={}",
                status, failureCount, recentSetbacks, forceReposition,
                placementFailurePauseTicks, targetCooldown,
                pos != null ? pos.getX() : 0,
                pos != null ? pos.getY() : 0,
                pos != null ? pos.getZ() : 0,
                supportPos,
                stancePos);
    }

    /*? if >=26.1 {*//*
    private boolean handlePlacementFailureRecovery(Minecraft mc) {
    *//*?} else {*/
    private boolean handlePlacementFailureRecovery(MinecraftClient mc) {
    /*?}*/
        if (!autoBuild) {
            resetPlacementFailureRecovery();
            return false;
        }
        if (placementFailurePauseTicks > 0) {
            placementFailurePauseTicks--;
            return true;
        }
        if (!deferredPlacementReposition || deferredPlacementRepositionPos == null) {
            return false;
        }
        BlockPos target = deferredPlacementRepositionPos;
        deferredPlacementReposition = false;
        deferredPlacementRepositionPos = null;
        PacketTelemetry.mark("build recovery reposition target=" + target);
        repositionAfterPlacementFailure(mc, target);
        return true;
    }

    /*? if >=26.1 {*//*
    private void repositionAfterPlacementFailure(Minecraft mc, BlockPos pos) {
    *//*?} else {*/
    private void repositionAfterPlacementFailure(MinecraftClient mc, BlockPos pos) {
    /*?}*/
        PlacementEngine.resetRejectionCounters();
        clearPlacementStartFailureState();
        boolean targetStillActionable;
        /*? if >=26.1 {*//*
        targetStillActionable = pos != null && isBuildTargetStillActionable(pos, mc.level, true);
        *//*?} else {*/
        targetStillActionable = pos != null && isBuildTargetStillActionable(pos, mc.world, true);
        /*?}*/
        if (targetStillActionable) {
            boolean startedAccess;
            /*? if >=26.1 {*//*
            startedAccess = startAccessMiningToBuildPocket(mc, pos);
            *//*?} else {*/
            startedAccess = startAccessMiningToBuildPocket(mc, pos);
            /*?}*/
            if (startedAccess) {
                PacketTelemetry.mark("build recovery access target=" + posLabel(pos));
                return;
            }
        } else if (pos != null) {
            PacketTelemetry.mark("build recovery stale-target target=" + posLabel(pos));
        }
        if (pos != null) {
            failedZones.add(pos);
        }
        /*? if >=26.1 {*//*
        failedZones.add(mc.player.blockPosition());
        *//*?} else {*/
        failedZones.add(mc.player.getBlockPos());
        /*?}*/
        noProgressTicks = 0;
        walkAttemptCooldown = 20;
        if (!tryWalkToNextZone(mc)) {
            /*? if >=26.1 {*//*
            BlockPos highZone = findHighBuildZone(mc.player, mc.level);
            *//*?} else {*/
            BlockPos highZone = findHighBuildZone(mc.player, mc.world);
            /*?}*/
            if (highZone != null) {
                int radius = (int) Math.ceil(range);
                walkToZoneWithPlacement(mc.player, highZone, radius);
                autoState = AutoState.WALKING_TO_BUILD;
            }
        } else {
            autoState = AutoState.WALKING_TO_BUILD;
        }
    }
    public boolean isSwapItems()       { return swapItems; }
    public void setSwapItems(boolean v){ this.swapItems = v; }
    public boolean isPrintInAir()      { return printInAir; }
    public void setPrintInAir(boolean v){ this.printInAir = v; }
    public SortMode getSortMode()      { return sortMode; }
    public void setSortMode(SortMode m){ this.sortMode = m; }
    public boolean isStatusMessages()  { return statusMessages; }
    public void setStatusMessages(boolean v){ this.statusMessages = v; }
    public boolean isAutoBuild()       { return autoBuild; }
    public String getAutoStateName()   { return autoState.name(); }
    public void setAutoBuild(boolean v){
        if (this.autoBuild == v) {
            if (!v && enabled && (isNavigationAutoState() || PathWalker.isActive())) {
                PacketTelemetry.mark("autobuild cleanup while already disabled state=" + autoState
                        + " walker=" + PathWalker.isActive());
                PathWalker.stop();
                PlacementEngine.reset();
                clearPendingBuildPlacement();
                resetPlacementFailureRecovery();
                autoState = AutoState.IDLE;
            }
            return;
        }
        this.autoBuild = v;

        // Switching modes while enabled can leave stale Baritone goals or
        // in-flight placement phases running. Reset both sides so manual
        // mode doesn't keep walking and auto mode re-evaluates cleanly.
        if (enabled) {
            PathWalker.stop();
            PlacementEngine.reset();
            clearPendingBuildPlacement();
            resetPlacementFailureRecovery();
            // Fix #19: PlacementEngine.reset() no longer clears the session
            // breaker state, so a user-driven toggle is the right place to
            // wipe it. Without this, a circuit-breaker tripped earlier would
            // remain tripped across a manual off/on cycle.
            PlacementEngine.clearSessionBreakerState();
            noProgressTicks = 0;
            walkFailCount = 0;
            triedPlacementWalk = false;
            lastWalkTargetZone = null;
            lastClearTargetBlock = null;
            walkAttemptCooldown = 0;
            stuckCycles = 0;
            walkingSetbackPauseTicks = 0;
            int totalSetbacks = SetbackMonitor.get().totalSetbacks();
            observedWalkingSetbacks = totalSetbacks;
            observedPlacementSetbacks = totalSetbacks;
            if (this.autoBuild) {
                if (clearingDone) {
                    enterBuildMode("auto toggled on with clearing already complete");
                } else {
                    autoState = AutoState.CLEARING_AREA;
                }
            } else {
                autoState = AutoState.IDLE;
            }
        }
    }

    // TICK (called from mod initializer)

    public void tick() {
        if (!enabled) return;
        if (!isLoaded()) return;

        if (statusMessages && PlacementEngine.consumeSwapsSuspendedNotice()) {
            ChatHelper.info("§eInventory swaps suspended§f — server stopped honoring container clicks."
                    + " Continuing with hotbar items only; restock manually to resume swap-required blocks.");
        }
        if (PlacementEngine.consumeAutoBuildKillSwitchNotice()) {
            // Server has stopped acknowledging placements entirely (UseItemOn drops).
            // Stop the whole printer loop; falling back to manual placement would
            // keep emitting the same packets that are already disappearing.
            if (statusMessages) {
                ChatHelper.info("§cPrinter paused§f — server stopped acknowledging placements."
                        + " Re-enable after the lag spike clears or you reconnect.");
            }
            setAutoBuild(false);
            disable(false);
            return;
        }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        /*?}*/

        // Stop if the auto-detected placement disappears or changes shape.
        if (autoDetected && schematicFile != null && ++placementCheckCooldown >= 100) {
            placementCheckCooldown = 0;
            boolean active = isPlacementStillActive();
            boolean supported = hasSupportedPlacementMatch();
            if (!active) {
                if (statusMessages) {
                    ChatHelper.info("§cLitematica placement unloaded — printer stopped.");
                }
                disable();
                unload();
                return;
            }
            if (!supported) {
                if (statusMessages) {
                    LitematicaDetector.DetectedPlacement unsupported = findUnsupportedPlacementMatch();
                    ChatHelper.info("§cLitematica placement changed to an unsupported transform — printer stopped.");
                    if (unsupported != null) {
                        ChatHelper.info("§7Current placement: §f" + unsupported.unsupportedTransformSummary());
                    }
                }
                disable();
                unload();
                return;
            }
        }

        // Forward BPS and keep manual mode silent.
        PlacementEngine.setBps(bps);
        if (autoBuild) {
            PlacementEngine.setSilentRotation(false);
        } else {
            PlacementEngine.setSilentRotation(true);
        }

        // Flush maintenance every ~10 s.
        /*? if >=26.1 {*//*
        if (mc.level.getGameTime() % 200 == 0) {
        *//*?} else {*/
        if (mc.world.getTime() % 200 == 0) {
        /*?}*/
            PrinterDatabase.flushScaffoldIfDirty();
            PlacementEngine.pruneCompletedCorrections();
        }

        boolean allowAnchorCorrelation = !autoBuild || autoState == AutoState.IDLE;

        // Only re-correlate while idle/manual so active planning isn't reset
        // in the middle of a walk/build handoff.
        if (schematic != null && allowAnchorCorrelation && --anchorCorrelationCooldown <= 0) {
            anchorCorrelationCooldown = ANCHOR_CORRELATION_INTERVAL;
            BlockPos prevAnchor = this.anchor;
            boolean synced = trySyncAnchor();
            boolean canUseHologramCorrelation = findUnsupportedPlacementMatch() == null;
            if (!synced && canUseHologramCorrelation) {
                BlockPos correlated = LitematicaDetector.detectAnchorFromSchematicWorld(schematic);
                if (correlated != null) {
                    if (!correlated.equals(anchor)) {
                        this.anchor = correlated;
                        if (statusMessages) {
                            ChatHelper.info("§aAnchor auto-aligned from hologram blocks → §e"
                                    + correlated.getX() + " " + correlated.getY() + " " + correlated.getZ());
                        }
                    }
                    anchorCorrelated = true;
                }
            } else if (!synced) {
                anchorCorrelated = false;
            } else {
                anchorCorrelated = true;
            }
            // Reset walking if the anchor moved.
            if (anchor != null && !anchor.equals(prevAnchor)) {
                rebuildSchematicChunkIndex();
                PathWalker.stop();
                if (autoBuild) {
                    autoState = clearingDone ? AutoState.BUILDING : AutoState.CLEARING_AREA;
                    noProgressTicks = 0;
                    failedZones.clear();
                    lastWalkTargetZone = null;
                }
            }
        }

        // Always observe placement ACKs, but never let placement and
        // navigation own the packet stream at the same time.
        PlacementEngine.tickVerification();
        if (isNavigationAutoState()) {
            if (!autoBuild) {
                PacketTelemetry.mark("navigation cleared while autobuild disabled state=" + autoState
                        + " phase=" + PlacementEngine.getPhase()
                        + " active=" + PlacementEngine.hasActivePhase());
                PathWalker.stop();
                PlacementEngine.reset();
                clearPendingBuildPlacement();
                resetPlacementFailureRecovery();
                autoState = AutoState.IDLE;
                return;
            }
            if (PlacementEngine.hasActivePhase()) {
                PacketTelemetry.mark("placement reset during navigation state=" + autoState
                        + " phase=" + PlacementEngine.getPhase()
                        + " active=" + PlacementEngine.hasActivePhase());
                PlacementEngine.reset();
                clearPendingBuildPlacement();
            }
            tickAutoBuild(mc);
            return;
        }
        if (PlacementEngine.isBusy()) {
            boolean staleVerificationGate = autoBuild
                    && !PlacementEngine.hasActivePhase()
                    && pendingBuildPlacementPos == null
                    && pendingBuildPlacementState == null;
            if (staleVerificationGate) {
                PacketTelemetry.mark("tick gate clear stale verification");
                PlacementEngine.resetRejectionCounters();
            }
            boolean placed = PlacementEngine.tick();
            if (placed) {
                /*? if >=26.1 {*//*
                BlockPos placementStance = mc.player.blockPosition();
                *//*?} else {*/
                BlockPos placementStance = mc.player.getBlockPos();
                /*?}*/
                recordBuildPlacementAttempt(
                        PlacementEngine.getLastVerificationPos(),
                        PlacementEngine.getLastVerificationState(),
                        PlacementEngine.getLastVerificationSupportPos(),
                        placementStance);
                blocksPlaced++;
                noProgressTicks = 0;
                // Invalidate cached remaining counts so the next query
                // reflects the newly placed block promptly.
                remainingCacheTick = Long.MIN_VALUE;
                solidsCacheTick = Long.MIN_VALUE;
                liquidsCacheTick = Long.MIN_VALUE;
                if (schematicFile != null) {
                    /*? if >=26.1 {*//*
                    PrinterCheckpoint.onBlockPlaced(schematicFile, anchor, blocksPlaced, mc.player.blockPosition());
                    *//*?} else {*/
                    PrinterCheckpoint.onBlockPlaced(schematicFile, anchor, blocksPlaced, mc.player.getBlockPos());
                    /*?}*/
                }
            }
            if (PlacementEngine.hasActivePhase() || !PlacementEngine.canPlace()) {
                return;
            }
        }

        if (autoBuild) {
            tickAutoBuild(mc);
        } else {
            /*? if >=26.1 {*//*
            if (mc.screen != null) return;
            *//*?} else {*/
            if (mc.currentScreen != null) return;
            /*?}*/
            if (handlePendingBuildPlacement(mc)) return;
            if (handlePlacementFailureRecovery(mc)) return;
            if (!PlacementEngine.canPlace()) return;
            /*? if >=26.1 {*//*
            tryPlaceNextBlock(mc.player, mc.level);
            *//*?} else {*/
            tryPlaceNextBlock(mc.player, mc.world);
            /*?}*/
        }
    }

    // AUTO-BUILD STATE MACHINE

    /*? if >=26.1 {*//*
    private void tickAutoBuild(Minecraft mc) {
    *//*?} else {*/
    private void tickAutoBuild(MinecraftClient mc) {
    /*?}*/
        // safety guards: dead player or wrong dimension
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return;
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player.isDeadOrDying()) {
        *//*?} else {*/
        if (mc.player.isDead()) {
        /*?}*/
            PlacementEngine.reset();
            PathWalker.stop();
            return;
        }
        /*? if >=26.1 {*//*
        if (buildDimension != null && !mc.level.dimension().equals(buildDimension)) {
        *//*?} else {*/
        if (buildDimension != null && !mc.world.getRegistryKey().equals(buildDimension)) {
        /*?}*/
            // Player switched dimensions — pause auto-build silently.
            // It will resume automatically if they return.
            PlacementEngine.reset();
            PathWalker.stop();
            return;
        }

        switch (autoState) {
            case CLEARING_AREA       -> tickClearingArea(mc);
            case WALKING_TO_CLEAR    -> tickWalkingToClear(mc);
            case WALKING_TO_DUMP     -> tickWalking(mc, AutoState.DUMPING);
            case DUMPING             -> tickDumping(mc);
            case BUILDING            -> tickBuilding(mc);
            case WALKING_TO_BUILD    -> tickWalking(mc, AutoState.BUILDING);
            case WALKING_BACK        -> tickWalking(mc, AutoState.BUILDING);
            case WALKING_TO_SUPPLY   -> tickWalkingToSupply(mc);
            case RESTOCKING          -> tickRestocking(mc);
            case UNLOADING_SHULKER   -> tickUnloadingShulker(mc);
            case CLEANING_SCAFFOLD   -> tickCleaningScaffold(mc);
            case IDLE                -> tickIdle(mc);
        }
    }

    /*? if >=26.1 {*//*
    private void tickBuilding(Minecraft mc) {
    *//*?} else {*/
    private void tickBuilding(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.screen != null) return;
        *//*?} else {*/
        if (mc.currentScreen != null) return;
        /*?}*/
        if (handleBuildSetback(mc)) return;
        if (handlePendingBuildPlacement(mc)) return;
        if (handlePlacementFailureRecovery(mc)) return;
        if (PathWalker.isActive()) {
            buildHandoffSettleTicks = Math.max(
                    buildHandoffSettleTicks, BUILD_HANDOFF_SETTLE_TICKS);
        }
        if (awaitBuildHandoffSettle(mc)) return;
        if (!PlacementEngine.canPlace()) {
            boolean staleVerificationGate = PlacementEngine.isBusy()
                    && !PlacementEngine.hasActivePhase()
                    && pendingBuildPlacementPos == null
                    && pendingBuildPlacementState == null;
            if (staleVerificationGate) {
                PacketTelemetry.mark("build gate clear stale verification");
                PlacementEngine.resetRejectionCounters();
            }
            if (PlacementEngine.isBusy()
                    || placementFailurePauseTicks > 0
                    || !"IDLE".equals(PlacementEngine.getPhase())
                    || !SetbackMonitor.get().isCalm()) {
                buildGateStallTicks = 0;
                return;
            }
            buildGateStallTicks++;
            if (buildGateStallTicks == BUILD_GATE_STALL_RECHECK_TICKS
                    || buildGateStallTicks == BUILD_GATE_STALL_TIMEOUT_TICKS) {
                PacketTelemetry.mark("build gate stalled ticks=" + buildGateStallTicks
                        + " busy=" + PlacementEngine.isBusy()
                        + " phase=" + PlacementEngine.getPhase()
                        + " pause=" + placementFailurePauseTicks
                        + " calm=" + SetbackMonitor.get().isCalm()
                        + " pendingBuild=" + (pendingBuildPlacementPos != null));
            }
            if (buildGateStallTicks == BUILD_GATE_STALL_RECHECK_TICKS
                    && walkAttemptCooldown <= 0
                    && tryWalkToNextZone(mc)) {
                buildGateStallTicks = 0;
                return;
            }
            if (buildGateStallTicks >= BUILD_GATE_STALL_TIMEOUT_TICKS) {
                LOGGER.debug("Build gate stalled from current stance — refreshing planner");
                prepareForBuildFromCurrentStance();
                if (tryWalkToNextZone(mc)) {
                    return;
                }
                buildGateStallTicks = 0;
            }
            return;
        }
        buildGateStallTicks = 0;
        if (missingItemMsgCooldown > 0) missingItemMsgCooldown--;

        // entrapment safety
        // If the player has no horizontal exit, stop building and try
        // to navigate to a safe position before continuing.
        /*? if >=26.1 {*//*
        if (isPlayerTrapped(mc.player, mc.level)) {
        *//*?} else {*/
        if (isPlayerTrapped(mc.player, mc.world)) {
        /*?}*/
            if (statusMessages) {
                ChatHelper.info("§c⚠ Blocked in! Finding escape route...");
            }
            PlacementEngine.reset();
            /*? if >=26.1 {*//*
            BlockPos escape = findEscapePosition(mc.player, mc.level);
            *//*?} else {*/
            BlockPos escape = findEscapePosition(mc.player, mc.world);
            /*?}*/
            if (escape != null) {
                PathWalker.walkTo(escape);
                autoState = AutoState.WALKING_TO_BUILD;
            } else {
                // Truly stuck — let Baritone try to path out via mining
                /*? if >=26.1 {*//*
                PathWalker.walkToNearby(mc.player.blockPosition().above(2), 3);
                *//*?} else {*/
                PathWalker.walkToNearby(mc.player.getBlockPos().up(2), 3);
                /*?}*/
                autoState = AutoState.WALKING_TO_BUILD;
            }
            return;
        }

        // Water bailout: if the player is swimming and the build zone
        // is above them, don't try to place — anti-cheat servers reject
        // placements from swimming positions, and the failed attempts
        // reset noProgressTicks so the walk-to-zone logic never triggers.
        // Instead, navigate to dry land or scaffold out first.
        /*? if >=26.1 {*//*
        if (mc.player.isInWater() && !liquidPass) {
        *//*?} else {*/
        if (mc.player.isTouchingWater() && !liquidPass) {
        /*?}*/
            /*? if >=26.1 {*//*
            BlockPos wbZone = findNextBuildZone(mc.player, mc.level);
            *//*?} else {*/
            BlockPos wbZone = findNextBuildZone(mc.player, mc.world);
            /*?}*/
            /*? if >=26.1 {*//*
            if (wbZone == null) wbZone = findHighBuildZone(mc.player, mc.level);
            *//*?} else {*/
            if (wbZone == null) wbZone = findHighBuildZone(mc.player, mc.world);
            /*?}*/
            /*? if >=26.1 {*//*
            if (wbZone != null && wbZone.getY() > mc.player.blockPosition().getY()) {
            *//*?} else {*/
            if (wbZone != null && wbZone.getY() > mc.player.getBlockPos().getY()) {
            /*?}*/
                int wbRadius = (int) Math.ceil(range);
                /*? if >=26.1 {*//*
                BlockPos wbStand = findStandingPosition(wbZone, mc.level, mc.player);
                *//*?} else {*/
                BlockPos wbStand = findStandingPosition(wbZone, mc.world, mc.player);
                /*?}*/
                /*? if >=26.1 {*//*
                wbStand = sanitizeBuildStandingPosition(wbZone, wbStand, mc.player.blockPosition());
                *//*?} else {*/
                wbStand = sanitizeBuildStandingPosition(wbZone, wbStand, mc.player.getBlockPos());
                /*?}*/
                // Reject standing positions that are also in water
                if (wbStand != null
                        /*? if >=26.1 {*//*
                        && !mc.level.getBlockState(wbStand).getFluidState().isEmpty()) {
                        *//*?} else {*/
                        && !mc.world.getBlockState(wbStand).getFluidState().isEmpty()) {
                        /*?}*/
                    wbStand = null;
                }
                if (wbStand != null) {
                    /*? if >=26.1 {*//*
                    Level w = mc.level;
                    *//*?} else {*/
                    World w = mc.world;
                    /*?}*/
                    if (w != null) {
                        PathWalker.setReservedItems(
                                getNeededItemsNearby(mc.player, w, 200));
                    }
                    PathWalker.walkToWithPlacement(
                            wbStand, wbRadius, mc.player);
                } else {
                    walkToZoneWithPlacement(
                            mc.player, wbZone, wbRadius);
                }
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Player in water — navigating out before building");
                return;
            }
        }

        // Server-rejection bailout: only hard rejections prove the server
        // disagreed with the placement. Confirmation timeouts can happen on
        // strict or delayed stacks, so don't let those alone force a walk.
        if (PlacementEngine.getConsecutiveRejections() >= SERVER_REJECT_THRESHOLD) {
            PlacementEngine.resetRejectionCounters();
            LOGGER.debug("Server rejected {} consecutive placements — repositioning",
                    SERVER_REJECT_THRESHOLD);
            if (!tryWalkToNextZone(mc)) {
                /*? if >=26.1 {*//*
                BlockPos highZone = findHighBuildZone(mc.player, mc.level);
                *//*?} else {*/
                BlockPos highZone = findHighBuildZone(mc.player, mc.world);
                /*?}*/
                if (highZone != null) {
                    int radius = (int) Math.ceil(range);
                    walkToZoneWithPlacement(mc.player, highZone, radius);
                    autoState = AutoState.WALKING_TO_BUILD;
                }
            } else {
                autoState = AutoState.WALKING_TO_BUILD;
            }
            noProgressTicks = 0;
            return;
        }

        // Periodic re-check of skipped items: if the player picked up
        // items that were previously skipped, un-skip them.
        if (!skippedItems.isEmpty()) {
            skippedItemRecheckCooldown--;
            if (skippedItemRecheckCooldown <= 0) {
                skippedItemRecheckCooldown = SKIPPED_RECHECK_INTERVAL;
                Map<Item, Integer> inv = PlacementEngine.getInventoryContentsCached();
                boolean unSkipped = skippedItems.removeIf(
                        item -> inv.getOrDefault(item, 0) > 0);
                if (unSkipped && statusMessages) {
                    ChatHelper.info("§aFound previously-missing items — resuming full build.");
                }
            }
        }

        /*? if >=26.1 {*//*
        boolean started = tryPlaceNextBlock(mc.player, mc.level);
        *//*?} else {*/
        boolean started = tryPlaceNextBlock(mc.player, mc.world);
        /*?}*/
        if (started) {
            // Pipeline started — block will be placed over next ticks
            noProgressTicks = 0;
            // NOTE: do NOT reset restockFailures here — placing a block we
            // already had materials for doesn't mean the supply chests have
            // the items we're still missing.  Resetting here causes an
            // infinite restock loop (place some → restock fail → reset → repeat).
            // restockFailures only resets on *successful* restock (got items).
            return;
        }

        if (placementFailurePauseTicks > 0) {
            return;
        }

        // nothing was placed this tick

        // Case 1: blocks exist nearby but we lack the items for ALL of them
        if (!lastMissingItems.isEmpty()) {
            handleMissingItems(mc);
            return;
        }

        // Case 1b: nothing started from this stance, but the nearby working set
        // is understocked enough that continuing to walk/build will just churn.
        if (tryStartProactiveRestock(mc)) {
            return;
        }

        // Case 2: no placeable candidates in reach — look for the next zone
        if (walkAttemptCooldown > 0) walkAttemptCooldown--;
        noProgressTicks++;
        if (noProgressTicks < NO_PROGRESS_TIMEOUT) {
            // After ~10 ticks (0.5 s) with no progress, speculatively scan
            // for a nearby zone to walk to.  Don't mark the current area as
            // failed yet — the miss might be transient (player settling after
            // a walk, blocks temporarily out of angle, etc.).
            if (noProgressTicks == NO_PROGRESS_WALK_RECHECK_TICKS
                    && walkAttemptCooldown <= 0) {
                if (!tryWalkToNextZone(mc)) {
                    // No loaded zones — check for unloaded build zones
                    // so we start walking immediately instead of waiting
                    // for the full stuck-cycle cascade.
                    /*? if >=26.1 {*//*
                    BlockPos unloaded = findUnloadedBuildZone(mc.player, mc.level);
                    *//*?} else {*/
                    BlockPos unloaded = findUnloadedBuildZone(mc.player, mc.world);
                    /*?}*/
                    if (unloaded != null) {
                        PathWalker.walkToNearby(unloaded, (int) Math.ceil(range));
                        autoState = AutoState.WALKING_TO_BUILD;
                    }
                }
            }
            return;
        }

        // Timeout reached — exhaustive check before giving up
        noProgressTicks = 0;
        stuckCycles++;

        // If all remaining blocks need skipped (missing) items, don't
        // waste 10 stuck cycles walking around — stop immediately and
        // tell the user which items are needed.
        if (!skippedItems.isEmpty() && stuckCycles >= 2) {
            /*? if >=26.1 {*//*
            int skippedCount = countSkippedBlocks(mc.level);
            *//*?} else {*/
            int skippedCount = countSkippedBlocks(mc.world);
            /*?}*/
            int totalRemaining = countRemaining();
            if (totalRemaining > 0 && skippedCount >= totalRemaining) {
                if (statusMessages) {
                    ChatHelper.info("§eBuild paused — all §f" + totalRemaining
                            + "§e remaining blocks need missing materials:"
                            + "\n§7" + formatMissingItems(skippedItems)
                            + "\n§7Get these items and run §f/printer auto§7 to resume.");
                }
                stuckCycles = 0;
                failedZones.clear();
                idleScanCooldown = 0;
                autoState = AutoState.IDLE;
                return;
            }
        }

        // If we've been stuck for too many cycles, stop looping and go idle
        if (stuckCycles >= MAX_STUCK_CYCLES) {
            if (statusMessages) {
                ChatHelper.info("§eStuck for " + stuckCycles
                        + " cycles — pausing. Remaining: §f" + countRemaining()
                        + "§e blocks. Check for missing items or unreachable areas.");
            }
            stuckCycles = 0;
            failedZones.clear();
            idleScanCooldown = 0;
            autoState = AutoState.IDLE;
            return;
        }

        // Before trying to walk to yet another zone, check if the
        // problem is vertical — the nearest unbuilt blocks may be above
        // the player.  Let Baritone handle the vertical movement with
        // allowPlace enabled (pillaring, bridging, parkour).
        // Threshold lowered to pY + 2 so blocks just 3 above the
        // player trigger Baritone scaffolding instead of spinning in
        // the no-progress loop (the previous pY + maxReach threshold
        // left a dead zone where blocks were technically "in range"
        // but couldn't actually be placed from ground level).
        {
            /*? if >=26.1 {*//*
            BlockPos nearbyZone = findNextBuildZone(mc.player, mc.level);
            *//*?} else {*/
            BlockPos nearbyZone = findNextBuildZone(mc.player, mc.world);
            /*?}*/
            if (nearbyZone != null) {
                /*? if >=26.1 {*//*
                int pY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int pY = mc.player.getBlockPos().getY();
                /*?}*/
                /*? if >=26.1 {*//*
                int stuckClimbOffset = mc.player.isInWater() ? 0 : 2;
                *//*?} else {*/
                int stuckClimbOffset = mc.player.isTouchingWater() ? 0 : 2;
                /*?}*/
                if (nearbyZone.getY() > pY + stuckClimbOffset) {
                    int radius = (int) Math.ceil(range);
                    // Prefer standing position on placed structure
                    BlockPos standPos = findStandingPosition(
                            /*? if >=26.1 {*//*
                            nearbyZone, mc.level, mc.player);
                            *//*?} else {*/
                            nearbyZone, mc.world, mc.player);
                            /*?}*/
                    /*? if >=26.1 {*//*
                    standPos = sanitizeBuildStandingPosition(nearbyZone, standPos, mc.player.blockPosition());
                    *//*?} else {*/
                    standPos = sanitizeBuildStandingPosition(nearbyZone, standPos, mc.player.getBlockPos());
                    /*?}*/
                    if (standPos != null) {
                        /*? if >=26.1 {*//*
                        Level w = mc.level;
                        *//*?} else {*/
                        World w = mc.world;
                        /*?}*/
                        if (w != null) {
                            PathWalker.setReservedItems(
                                    getNeededItemsNearby(mc.player, w, 200));
                        }
                        PathWalker.walkToWithPlacement(
                                standPos, radius, mc.player);
                    } else {
                        walkToZoneWithPlacement(
                                mc.player, nearbyZone, radius);
                    }
                    autoState = AutoState.WALKING_TO_BUILD;
                    LOGGER.debug("Target above — pathing to {} {} {}",
                            nearbyZone.getX(), nearbyZone.getY(), nearbyZone.getZ());
                    return;
                }
            }
        }

        /*? if >=26.1 {*//*
        failedZones.add(mc.player.blockPosition());
        *//*?} else {*/
        failedZones.add(mc.player.getBlockPos());
        /*?}*/
        if (!tryWalkToNextZone(mc)) {
            // No more reachable zones — clear the failed-zone exclusion
            // and try once more in case support was created elsewhere
            failedZones.clear();
            if (!tryWalkToNextZone(mc)) {
                // Before declaring complete, check for higher zones
                // that need vertical movement (skip during liquid pass)
                if (!liquidPass) {
                    /*? if >=26.1 {*//*
                    BlockPos highZone = findHighBuildZone(mc.player, mc.level);
                    *//*?} else {*/
                    BlockPos highZone = findHighBuildZone(mc.player, mc.world);
                    /*?}*/
                    if (highZone != null) {
                        int radius = (int) Math.ceil(range);
                        // Prefer walking to a standing position on
                        // already-placed structure over blind scaffolding.
                        BlockPos standPos = findStandingPosition(
                                /*? if >=26.1 {*//*
                                highZone, mc.level, mc.player);
                                *//*?} else {*/
                                highZone, mc.world, mc.player);
                                /*?}*/
                        /*? if >=26.1 {*//*
                        standPos = sanitizeBuildStandingPosition(highZone, standPos, mc.player.blockPosition());
                        *//*?} else {*/
                        standPos = sanitizeBuildStandingPosition(highZone, standPos, mc.player.getBlockPos());
                        /*?}*/
                        if (standPos != null) {
                            /*? if >=26.1 {*//*
                            Level w = mc.level;
                            *//*?} else {*/
                            World w = mc.world;
                            /*?}*/
                            if (w != null) {
                                PathWalker.setReservedItems(
                                        getNeededItemsNearby(mc.player, w, 200));
                            }
                            PathWalker.walkToWithPlacement(
                                    standPos, radius, mc.player);
                        } else {
                            walkToZoneWithPlacement(
                                    mc.player, highZone, radius);
                        }
                        autoState = AutoState.WALKING_TO_BUILD;
                        LOGGER.debug("Target above — pathing to {} {} {}",
                                highZone.getX(), highZone.getY(),
                                highZone.getZ());
                        return;
                    }
                }
                // Structural done — switch to redstone pass if any remain.
                /*? if >=26.1 {*//*
                if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.level)
                *//*?} else {*/
                if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.world)
                /*?}*/
                        /*? if >=26.1 {*//*
                        && hasRemainingRedstone(mc.level)) {
                        *//*?} else {*/
                        && hasRemainingRedstone(mc.world)) {
                        /*?}*/
                    redstonePass = true;
                    noProgressTicks = 0;
                    stuckCycles = 0;
                    failedZones.clear();
                    if (statusMessages) {
                        ChatHelper.info("§bStructural blocks done — placing redstone components...");
                    }
                    enterBuildMode("structural pass complete -> redstone");
                    return;
                }
                /*? if >=26.1 {*//*
                if (!liquidPass && !hasRemainingSolids(mc.level) && !hasRemainingRedstone(mc.level)
                *//*?} else {*/
                if (!liquidPass && !hasRemainingSolids(mc.world) && !hasRemainingRedstone(mc.world)
                /*?}*/
                        /*? if >=26.1 {*//*
                        && hasRemainingLiquids(mc.level)) {
                        *//*?} else {*/
                        && hasRemainingLiquids(mc.world)) {
                        /*?}*/
                    liquidPass = true;
                    redstonePass = false;
                    noProgressTicks = 0;
                    stuckCycles = 0;
                    failedZones.clear();
                    PathWalker.stop(); // stop Baritone — it can't path through liquids
                    if (statusMessages) {
                        ChatHelper.info("§bRedstone done — placing liquids...");
                    }
                    enterBuildMode("redstone pass complete -> liquids");
                    return;
                }

                // Chunk-loading awareness
                // All loaded chunks are done, but there may be unbuilt
                // blocks in unloaded parts of the schematic.  Walk
                // toward the nearest unloaded region so it loads.
                /*? if >=26.1 {*//*
                BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.level);
                *//*?} else {*/
                BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.world);
                /*?}*/
                if (unloadedZone != null) {
                    LOGGER.debug("Walking to unloaded region {} {} {}",
                            unloadedZone.getX(), unloadedZone.getY(), unloadedZone.getZ());
                    PathWalker.walkToNearby(unloadedZone, (int) Math.ceil(range));
                    autoState = AutoState.WALKING_TO_BUILD;
                    return;
                }

                int remainingAfterExhaustiveScan = countRemaining();
                if (remainingAfterExhaustiveScan > 0) {
                    PacketTelemetry.mark("build completion deferred remaining="
                            + remainingAfterExhaustiveScan);
                    LOGGER.debug("Build completion deferred; {} loaded unresolved blocks remain",
                            remainingAfterExhaustiveScan);
                    invalidateBuildStagingPlanCache();
                    noProgressTicks = 0;
                    walkAttemptCooldown = Math.max(
                            walkAttemptCooldown,
                            NO_PROGRESS_WALK_RECHECK_TICKS * 2);
                    idleScanCooldown = IDLE_SCAN_INTERVAL;
                    autoState = AutoState.BUILDING;
                    // Re-arm the handoff settle gate. Without this, the
                    // counter is 0 here and `awaitBuildHandoffSettle()`
                    // short-circuits, skipping the PathWalker/PlacementEngine/
                    // calm-window check on next tick. Other BUILDING entries
                    // (enterBuildMode + tickWalkingToBuild arrival) all set
                    // this; matching them here keeps the gate consistent.
                    buildHandoffSettleTicks = BUILD_HANDOFF_SETTLE_TICKS;
                    return;
                }

                // Build appears complete — check for scaffold to clean up.
                liquidPass = false; // reset for next build
                redstonePass = false;
                MoarMod.getChestManager().clearSnapshots();
                if (PrinterDatabase.hasScaffold()) {
                    autoState = AutoState.CLEANING_SCAFFOLD;
                    if (statusMessages) {
                        ChatHelper.info("§aBuild done! §7Cleaning up §e"
                                + PrinterDatabase.scaffoldCount()
                                + "§7 scaffold blocks...");
                    }
                } else {
                    autoState = AutoState.IDLE;
                    markBuildResult(skippedItems.isEmpty()
                            ? BuildResult.COMPLETED
                            : BuildResult.COMPLETED_WITH_MISSING_MATERIALS);
                    if (statusMessages) {
                        if (skippedItems.isEmpty()) {
                            ChatHelper.info("§aBuild appears complete! §e"
                                    + blocksPlaced + "§a blocks placed.");
                        } else {
                            ChatHelper.info("§aBuild finished with available materials! §e"
                                    + blocksPlaced + "§a blocks placed."
                                    /*? if >=26.1 {*//*
                                    + "\n§cMissing materials (§f" + countSkippedBlocks(mc.level)
                                    *//*?} else {*/
                                    + "\n§cMissing materials (§f" + countSkippedBlocks(mc.world)
                                    /*?}*/
                                    + "§c blocks not placed):"
                                    + "\n§7" + formatMissingItems(skippedItems)
                                    + "\n§7Get these items and run §f/printer auto§7 to resume.");
                        }
                    }
                }
            }
        }
    }

    // Handles the situation where blocks need placing but none of the
    // required items are in the player's inventory.
    /*? if >=26.1 {*//*
    private void handleMissingItems(Minecraft mc) {
    *//*?} else {*/
    private void handleMissingItems(MinecraftClient mc) {
    /*?}*/
        // If supply chests exist and we haven't exhausted restock attempts,
        // go restock.
        if (MoarMod.getChestManager().supplyChestCount() > 0 && restockFailures < MAX_RESTOCK_FAILURES) {
            if (statusMessages && missingItemMsgCooldown <= 0) {
                ChatHelper.info("§eMissing items — going to restock. Need: "
                        + formatMissingItems(lastMissingItems));
                missingItemMsgCooldown = MISSING_MSG_COOLDOWN;
            }
            noProgressTicks = 0;
            /*? if >=26.1 {*//*
            startRestockRun(mc.player, mc.level);
            *//*?} else {*/
            startRestockRun(mc.player, mc.world);
            /*?}*/
            return;
        }

        // Can't restock (no chests or all attempts exhausted) — skip
        // the missing items and keep building with whatever we have.
        skippedItems.addAll(lastMissingItems);
        if (statusMessages && missingItemMsgCooldown <= 0) {
            ChatHelper.info("§eSkipping unavailable items, building with what we have."
                    + "\n§7Skipped: " + formatMissingItems(skippedItems));
            missingItemMsgCooldown = MISSING_MSG_COOLDOWN;
        }
        lastMissingItems.clear();
        noProgressTicks = 0;
        // Stay in BUILDING — tryPlaceNextBlock will skip these items
    }

    // Finds the next build zone and starts walking to it.
    /*? if >=26.1 {*//*
    private boolean tryWalkToNextZone(Minecraft mc) {
    *//*?} else {*/
    private boolean tryWalkToNextZone(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        BuildStagingPlan preferredPlan = findBuildStagingPlan(mc.player, mc.level, Math.max(0, walkFailCount));
        BlockPos nextZone = preferredPlan != null
                ? preferredPlan.zone()
                : findNextBuildZone(mc.player, mc.level);
        *//*?} else {*/
        BuildStagingPlan preferredPlan = findBuildStagingPlan(mc.player, mc.world, Math.max(0, walkFailCount));
        BlockPos nextZone = preferredPlan != null
                ? preferredPlan.zone()
                : findNextBuildZone(mc.player, mc.world);
        /*?}*/
        if (nextZone == null) {
            // During liquid pass, don't try elevated zones — liquids don't
            // need adjacent support and vertical pathing can conflict.
            if (liquidPass) return false;

            // No reachable zones with adjacent support — check for elevated
            // zones and let Baritone handle the vertical movement.
            /*? if >=26.1 {*//*
            BlockPos highZone = findHighBuildZone(mc.player, mc.level);
            *//*?} else {*/
            BlockPos highZone = findHighBuildZone(mc.player, mc.world);
            /*?}*/
            if (highZone != null) {
                int radius = (int) Math.ceil(range);
                // Prefer walking to a standing position on already-placed
                // structure over blind scaffolding.
                BlockPos highStand = findStandingPosition(
                        /*? if >=26.1 {*//*
                        highZone, mc.level, mc.player);
                        *//*?} else {*/
                        highZone, mc.world, mc.player);
                        /*?}*/
                if (highStand != null) {
                    /*? if >=26.1 {*//*
                    Level w = mc.level;
                    *//*?} else {*/
                    World w = mc.world;
                    /*?}*/
                    if (w != null) {
                        PathWalker.setReservedItems(
                                getNeededItemsNearby(mc.player, w, 200));
                    }
                    PathWalker.walkToWithPlacement(
                            highStand, BUILD_STAGING_PLACE_WALK_RADIUS, mc.player);
                } else {
                    walkToZoneWithPlacement(mc.player, highZone, radius);
                }
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Target above — pathing to {} {} {}",
                        highZone.getX(), highZone.getY(), highZone.getZ());
                return true;
            }
            return false;
        }

        // If this is the exact same zone we just tried, mark it failed
        // and search again to avoid an infinite loop
        if (lastWalkTargetZone != null && nextZone.equals(lastWalkTargetZone)) {
            rememberSoftNoAccessBuildTarget(nextZone);
            /*? if >=26.1 {*//*
            preferredPlan = findBuildStagingPlan(mc.player, mc.level, Math.max(0, walkFailCount));
            nextZone = preferredPlan != null
                    ? preferredPlan.zone()
                    : findNextBuildZone(mc.player, mc.level);
            *//*?} else {*/
            preferredPlan = findBuildStagingPlan(mc.player, mc.world, Math.max(0, walkFailCount));
            nextZone = preferredPlan != null
                    ? preferredPlan.zone()
                    : findNextBuildZone(mc.player, mc.world);
            /*?}*/
            if (nextZone == null) return false;
        }

        // Check vertical reachability from the best standing position
        /*? if >=26.1 {*//*
        int playerY = mc.player.blockPosition().getY();
        *//*?} else {*/
        int playerY = mc.player.getBlockPos().getY();
        /*?}*/
        int targetY = nextZone.getY();
        int maxReach = (int) Math.ceil(range);
        BlockPos standPos;
        BlockPos approachStandPos;
        if (preferredPlan != null) {
            if (preferredPlan.approachOnly()) {
                standPos = null;
                approachStandPos = preferredPlan.standPos();
            } else {
                standPos = preferredPlan.standPos();
                approachStandPos = preferredPlan.standPos();
            }
        } else {
            /*? if >=26.1 {*//*
            standPos = findStandingPosition(nextZone, mc.level, mc.player);
            approachStandPos = standPos != null
                    ? standPos
                    : findApproachStandingPosition(nextZone, mc.level, mc.player, walkFailCount);
            *//*?} else {*/
            standPos = findStandingPosition(nextZone, mc.world, mc.player);
            approachStandPos = standPos != null
                    ? standPos
                    : findApproachStandingPosition(nextZone, mc.world, mc.player, walkFailCount);
            /*?}*/
        }
        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/
        standPos = sanitizeBuildStandingPosition(nextZone, standPos, playerPos);
        approachStandPos = sanitizeBuildStandingPosition(nextZone, approachStandPos, playerPos);
        boolean planAllowsApproachOnly = preferredPlan != null && preferredPlan.approachOnly();
        if (!planAllowsApproachOnly
                && standPos == null
                && approachStandPos != null
                /*? if >=26.1 {*//*
                && !canProbeBuildTargetFromStagingPosition(
                        approachStandPos, nextZone, mc.level, mc.player)) {
                *//*?} else {*/
                && !canProbeBuildTargetFromStagingPosition(
                        approachStandPos, nextZone, mc.world, mc.player)) {
                /*?}*/
            PacketTelemetry.mark("build approach reject no-probe zone="
                    + posLabel(nextZone)
                    + " approach=" + posLabel(approachStandPos));
            approachStandPos = null;
        }
        if (standPos == null && approachStandPos != null) {
            PacketTelemetry.mark("build approach fallback zone=" + posLabel(nextZone)
                    + " approach=" + posLabel(approachStandPos));
        }
        if (standPos == null && approachStandPos == null) {
            /*? if >=26.1 {*//*
            BuildStagingPlan stagingPlan = findBuildStagingPlan(mc.player, mc.level, walkFailCount);
            *//*?} else {*/
            BuildStagingPlan stagingPlan = findBuildStagingPlan(mc.player, mc.world, walkFailCount);
            /*?}*/
            if (stagingPlan != null) {
                nextZone = stagingPlan.zone();
                if (stagingPlan.approachOnly()) {
                    approachStandPos = stagingPlan.standPos();
                } else {
                    standPos = stagingPlan.standPos();
                }
                LOGGER.debug("Resolved interior build start via staging zone {} {} {} -> stand {} {} {}",
                        nextZone.getX(), nextZone.getY(), nextZone.getZ(),
                        stagingPlan.standPos().getX(),
                        stagingPlan.standPos().getY(),
                        stagingPlan.standPos().getZ());
            }
        }
        PacketTelemetry.mark("build walk choice zone=" + posLabel(nextZone)
                + " stand=" + posLabel(standPos)
                + " approach=" + posLabel(approachStandPos)
                + " player=" + posLabel(playerPos));

        if (standPos == null && approachStandPos == null) {
            if (startAccessMiningToBuildPocket(mc, nextZone)) {
                return true;
            }
            PacketTelemetry.mark("build access none zone=" + posLabel(nextZone)
                    + " player=" + posLabel(playerPos));
            rememberSoftNoAccessBuildTarget(nextZone);
            walkAttemptCooldown = NO_PROGRESS_WALK_RECHECK_TICKS;
            return false;
        }

        /*? if >=26.1 {*//*
        lastBuildPos = mc.player.blockPosition();
        *//*?} else {*/
        lastBuildPos = mc.player.getBlockPos();
        /*?}*/
        /*? if >=26.1 {*//*
        lastWalkTargetZone = nextZone.immutable();
        lastWalkStandPos = standPos != null ? standPos.immutable() : null;
        lastWalkApproachPos = approachStandPos != null ? approachStandPos.immutable() : null;
        *//*?} else {*/
        lastWalkTargetZone = nextZone.toImmutable();
        lastWalkStandPos = standPos != null ? standPos.toImmutable() : null;
        lastWalkApproachPos = approachStandPos != null ? approachStandPos.toImmutable() : null;
        /*?}*/

        noProgressTicks = 0;
        walkAttemptCooldown = NO_PROGRESS_TIMEOUT; // don't re-scan immediately if this fails

        int effectiveStandY = standPos != null ? standPos.getY() : playerY;

        // Determine if the target is vertically unreachable from ground.
        // When the player is in water, any block above them requires
        // climbing — swimming positions can't reliably place blocks and
        // anti-cheat servers reject placements from water.
        /*? if >=26.1 {*//*
        boolean playerInWater = mc.player.isInWater();
        *//*?} else {*/
        boolean playerInWater = mc.player.isTouchingWater();
        /*?}*/
        int climbOffset = playerInWater ? 0 : 2;
        boolean needsClimbing = false;
        // 1. Target is too far above any reachable standing position
        if (targetY > effectiveStandY + maxReach) {
            needsClimbing = true;
        }
        // 2. The standing position itself is above the player
        else if (standPos != null && standPos.getY() > playerY + climbOffset) {
            needsClimbing = true;
        }
        // 3. No standing position exists and target is above the player
        else if (standPos == null && targetY > playerY + climbOffset) {
            needsClimbing = true;
        }
        // 4. Target is above the player and has no adjacent solid block.
        //    Even if a standing position exists at ground level, the
        //    block can't be placed without an adjacent face to click on.
        //    Baritone's scaffold placement (pillar-up) will provide the
        //    support block automatically.
        else if (targetY > playerY + climbOffset
                /*? if >=26.1 {*//*
                && !PlacementEngine.hasAdjacentSolid(mc.level, nextZone)) {
                *//*?} else {*/
                && !PlacementEngine.hasAdjacentSolid(mc.world, nextZone)) {
                /*?}*/
            needsClimbing = true;
        }

        if (needsClimbing) {
            if (standPos != null) {
                // A valid standing position exists on already-placed structure
                // (e.g. a floor, platform, or staircase).  Walk to that position
                // directly and let Baritone plan a 3D path that uses the existing
                // terrain.  This avoids the horizontal-then-vertical waypoint
                // approach which forces Baritone to pillar instead of using
                // already-built staircases, ramps, or platforms.
                /*? if >=26.1 {*//*
                Level w = mc.level;
                *//*?} else {*/
                World w = mc.world;
                /*?}*/
                if (w != null) {
                    PathWalker.setReservedItems(getNeededItemsNearby(mc.player, w, 200));
                }
                PathWalker.walkToWithPlacement(
                        standPos, BUILD_STAGING_PLACE_WALK_RADIUS, mc.player);
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Target above — walking to standing position {} {} {} (on placed structure)",
                        standPos.getX(), standPos.getY(), standPos.getZ());
            } else if (approachStandPos != null) {
                /*? if >=26.1 {*//*
                Level w = mc.level;
                *//*?} else {*/
                World w = mc.world;
                /*?}*/
                if (w != null) {
                    PathWalker.setReservedItems(getNeededItemsNearby(mc.player, w, 200));
                }
                PathWalker.walkToWithPlacement(
                        approachStandPos, BUILD_STAGING_PLACE_WALK_RADIUS, mc.player);
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Target above — approaching staging position {} {} {}",
                        approachStandPos.getX(), approachStandPos.getY(), approachStandPos.getZ());
            } else {
                // No standing position found — let Baritone scaffold its own
                // path to the build zone via waypoints.
                walkToZoneWithPlacement(mc.player, nextZone, maxReach);
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Target above — scaffolding to {} {} {}",
                        nextZone.getX(), nextZone.getY(), nextZone.getZ());
            }
            return true;
        }

        // Target is too far below — navigate down.
        if (targetY < effectiveStandY - maxReach) {
            if (approachStandPos != null) {
                /*? if >=26.1 {*//*
                Level w = mc.level;
                *//*?} else {*/
                World w = mc.world;
                /*?}*/
                if (w != null) {
                    PathWalker.setReservedItems(getNeededItemsNearby(mc.player, w, 200));
                }
                PathWalker.walkToWithPlacement(
                        approachStandPos, BUILD_STAGING_PLACE_WALK_RADIUS, mc.player);
            } else {
                PathWalker.walkToNearby(nextZone, BUILD_STAGING_WALK_RADIUS);
            }
            autoState = AutoState.WALKING_TO_BUILD;
            LOGGER.debug("Target below — navigating to {} {} {}",
                    nextZone.getX(), nextZone.getY(), nextZone.getZ());
            return true;
        }

        if (liquidPass) {
            // During liquid pass, always use walkToNearby with extra radius.
            // Placed water/lava creates flowing currents that push the player
            // off-path; a wider radius lets Baritone route around them.
            int radius = (int) Math.ceil(range) + 2;
            PathWalker.walkToNearby(nextZone, radius);
        } else if (standPos != null && walkFailCount == 0) {
            // If the player is already at (or within 1.5 blocks of) the
            // standing position, skip walking — the build tick can place
            // from here.  Avoids sending Baritone a 0–1 block goal that
            // it can't compute near fences / iron bars / glass edges.
            /*? if >=26.1 {*//*
            double standDist = mc.player.position()
                    .distanceToSqr(Vec3.atCenterOf(standPos));
            *//*?} else if >=1.21.10 {*//*
            double standDist = mc.player.getSyncedPos()
                    .squaredDistanceTo(Vec3d.ofCenter(standPos));
            *//*?} else {*/
            double standDist = mc.player.getPos()
                    .squaredDistanceTo(Vec3d.ofCenter(standPos));
            /*?}*/
            if (standDist <= 2.25) { // 1.5 blocks
                /*? if >=26.1 {*//*
                if (hasBuildHandoffOpportunityFromHere(mc.player, mc.level)) {
                *//*?} else {*/
                if (hasBuildHandoffOpportunityFromHere(mc.player, mc.world)) {
                /*?}*/
                    resumeBuildFromCurrentStance("already at viable stand position");
                    return true;
                }
            }
            PathWalker.walkToExact(standPos, BUILD_STAGING_ARRIVAL_DISTANCE);
        } else if (approachStandPos != null) {
            if (standPos == null) {
                PathWalker.walkToExact(approachStandPos, BUILD_STAGING_ARRIVAL_DISTANCE);
            } else {
                PathWalker.walkToExact(approachStandPos, BUILD_STAGING_ARRIVAL_DISTANCE);
            }
        } else {
            // No standing position or repeated failures — walk near the target
            // zone directly.  With air-placement enabled, the player doesn't
            // need solid ground directly adjacent to the target.
            int radius = (int) Math.ceil(range) + walkFailCount;
            PathWalker.walkToNearby(nextZone, radius);
        }
        autoState = AutoState.WALKING_TO_BUILD;
        LOGGER.debug("Walking to build zone {} {} {}",
                nextZone.getX(), nextZone.getY(), nextZone.getZ());
        return true;
    }

    /*? if >=26.1 {*//*
    private void tickWalking(Minecraft mc, AutoState arrivalState) {
    *//*?} else {*/
    private void tickWalking(MinecraftClient mc, AutoState arrivalState) {
    /*?}*/
        SetbackMonitor setbackMonitor = SetbackMonitor.get();
        int totalSetbacks = setbackMonitor.totalSetbacks();
        if (totalSetbacks != observedWalkingSetbacks) {
            observedWalkingSetbacks = totalSetbacks;
            observedPlacementSetbacks = totalSetbacks;
            walkingSetbackPauseTicks = WALK_SETBACK_PAUSE_TICKS;
            if (PathWalker.isActive()) {
                PathWalker.stop();
            }
            PlacementEngine.reset();
            noProgressTicks = 0;
            walkAttemptCooldown = Math.max(walkAttemptCooldown, WALK_SETBACK_PAUSE_TICKS);
            if (arrivalState == AutoState.BUILDING) {
                /*? if >=26.1 {*//*
                if (canBuildFromCurrentStance(mc.player, mc.level)) {
                *//*?} else {*/
                if (canBuildFromCurrentStance(mc.player, mc.world)) {
                /*?}*/
                    placementFailurePauseTicks = Math.max(
                            placementFailurePauseTicks, PLACEMENT_FAILURE_PAUSE_TICKS);
                    resumeBuildFromCurrentStance("walking setback stabilized current stance");
                    LOGGER.debug("Setback detected while walking to build — resuming from stable current stance");
                } else {
                    refreshPlacementPlannerState();
                    deferBuildApproachReplan("walking setback current stance unstable");
                    LOGGER.debug("Setback detected while walking to build — current stance unstable, replanning build approach");
                }
                return;
            }
            LOGGER.debug("Setback detected while walking — pausing before replanning");
            return;
        }

        if (walkingSetbackPauseTicks > 0) {
            walkingSetbackPauseTicks--;
            return;
        }
        if (!setbackMonitor.isCalm()) {
            return;
        }

        if (PathWalker.isActive()) {
            PathWalker.tick();
        }

        if (!PathWalker.isActive()) {
            if (arrivalState == AutoState.BUILDING) {
                /*? if >=26.1 {*//*
                if (shouldResumeBuildFromCurrentWalk(mc.player, mc.level)) {
                *//*?} else {*/
                if (shouldResumeBuildFromCurrentWalk(mc.player, mc.world)) {
                /*?}*/
                    PacketTelemetry.mark("build walk handoff staging="
                            + posLabel(lastWalkStandPos != null ? lastWalkStandPos : lastWalkApproachPos)
                            + " zone=" + posLabel(lastWalkTargetZone));
                    resumeBuildFromCurrentStance("walk finished at build staging");
                    LOGGER.debug("Walk finished at build staging — resuming build evaluation from current stance");
                    return;
                }
                /*? if >=26.1 {*//*
                if (canForceBuildEvaluationFromCurrentWalk(mc.player, mc.level)) {
                *//*?} else {*/
                if (canForceBuildEvaluationFromCurrentWalk(mc.player, mc.world)) {
                /*?}*/
                    PacketTelemetry.mark("build walk handoff staging="
                            + posLabel(lastWalkStandPos != null ? lastWalkStandPos : lastWalkApproachPos)
                            + " zone=" + posLabel(lastWalkTargetZone));
                    resumeBuildFromCurrentStance("arrived at build staging position");
                    LOGGER.debug("Walk ended at build staging position — forcing build evaluation from current stance");
                    return;
                }
                if (isAtCurrentBuildStagingPosition(mc.player)) {
                    markCurrentBuildApproachFailed("walk ended at non-buildable staging position");
                }
                deferBuildApproachReplan("walk ended without stable build stance");
                LOGGER.debug("Walk ended without a stable build stance; deferring build replan");
                return;
            }
            // multi-phase descent continuation
            // walkToZoneWithPlacement may have set up a descent for
            // WALKING_BACK or WALKING_TO_BUILD.  Handle phase transitions
            // the same way tickWalkingToSupply does.
            if (supplyDescentPhase == 1 && supplyDescentTarget != null) {
                supplyDescentPhase = 2;
                LOGGER.debug("Descending to Y={} (walking)", supplyDescentTarget.getY());
                PathWalker.walkToYLevelWithPlacement(
                        supplyDescentTarget.getY(), mc.player);
                return;
            }
            if (supplyDescentPhase == 2 && supplyDescentTarget != null) {
                // Check if GoalYLevel actually brought us close to target Y.
                // If not (still far above), Baritone couldn't find a path
                // down — fall back to mining descent (break pillar blocks
                // under player's feet, 1 block at a time).
                /*? if >=26.1 {*//*
                int playerY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int playerY = mc.player.getBlockPos().getY();
                /*?}*/
                int targetY = supplyDescentTarget.getY();
                if (playerY - targetY > 5) {
                    LOGGER.debug("GoalYLevel failed — mining down from Y={} to Y={}",
                            playerY, targetY);
                    supplyDescentPhase = 2; // stay in phase 2 for re-check
                    PathWalker.startMiningDescent(targetY);
                    return;
                }
                supplyDescentPhase = 3;
                LOGGER.debug("Approaching target...");
                /*? if >=26.1 {*//*
                double dist = Math.sqrt(mc.player.blockPosition()
                *//*?} else {*/
                double dist = Math.sqrt(mc.player.getBlockPos()
                /*?}*/
                        /*? if >=26.1 {*//*
                        .distSqr(supplyDescentTarget));
                        *//*?} else {*/
                        .getSquaredDistance(supplyDescentTarget));
                        /*?}*/
                if (dist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            /*? if >=26.1 {*//*
                            mc.player.blockPosition(), supplyDescentTarget, 48);
                            *//*?} else {*/
                            mc.player.getBlockPos(), supplyDescentTarget, 48);
                            /*?}*/
                    PathWalker.walkToViaWaypointsWithPlacement(
                            horizLegs, (int) Math.ceil(range), mc.player);
                } else {
                    PathWalker.walkToWithPlacement(
                            supplyDescentTarget, (int) Math.ceil(range), mc.player);
                }
                return;
            }
            // Clear descent state if we were in phase 3 or not descending
            if (supplyDescentPhase > 0) {
                supplyDescentPhase = 0;
                supplyDescentTarget = null;
            }

            if (PathWalker.hasArrived()) {
                walkFailCount = 0;
                triedPlacementWalk = false;
                walkingSetbackPauseTicks = 0;
                // Keep the failed zone until a placement succeeds.
                // Player moved to a new position — chests that were
                // unreachable from the old position may be reachable
                // from here (e.g. after climbing to the build zone,
                // the ground-level chest is now a simple descent).
                unreachableChests.clear();
                shulkerNoSpaceSkipped = false;
                LOGGER.debug("Arrived at target");
                if (arrivalState == AutoState.BUILDING) {
                    /*? if >=26.1 {*//*
                    if (!shouldResumeBuildFromCurrentWalk(mc.player, mc.level)) {
                    *//*?} else {*/
                    if (!shouldResumeBuildFromCurrentWalk(mc.player, mc.world)) {
                    /*?}*/
                        /*? if >=26.1 {*//*
                        if (canForceBuildEvaluationFromCurrentWalk(mc.player, mc.level)) {
                        *//*?} else {*/
                        if (canForceBuildEvaluationFromCurrentWalk(mc.player, mc.world)) {
                        /*?}*/
                            PacketTelemetry.mark("build arrive handoff staging="
                                    + posLabel(lastWalkStandPos != null ? lastWalkStandPos : lastWalkApproachPos)
                                    + " zone=" + posLabel(lastWalkTargetZone));
                            resumeBuildFromCurrentStance("arrived at staging target");
                            return;
                        }
                        if (isAtCurrentBuildStagingPosition(mc.player)) {
                            markCurrentBuildApproachFailed("arrived at non-buildable staging position");
                        }
                        deferBuildApproachReplan("arrived near target with non-buildable stance");
                        LOGGER.debug("Arrived near target but stance is not buildable; deferring build replan");
                        return;
                    }
                    resumeBuildFromCurrentStance("arrived at build target");
                    return;
                }
                autoState = arrivalState;
                noProgressTicks = 0;
            } else {
                walkFailCount++;
                BlockPos walkTarget = PathWalker.getTarget();
                boolean pathImpossible = PathWalker.isStuck();

                // Retry with wider radius.  For clearing, retry even
                // when stuck — the illegal blocks ARE the obstacles and
                // a wider GoalNear gives Baritone room to path around
                // the cluster into open space.
                boolean clearingRetry = arrivalState == AutoState.CLEARING_AREA;
                if ((clearingRetry || !pathImpossible)
                        && walkFailCount < MAX_WALK_RETRIES) {
                    if (clearingRetry && lastClearTargetBlock != null) {
                        boolean restartedClearWalk;
                        /*? if >=26.1 {*//*
                        restartedClearWalk = startClearWalk(mc.player, mc.level, lastClearTargetBlock);
                        *//*?} else {*/
                        restartedClearWalk = startClearWalk(mc.player, mc.world, lastClearTargetBlock);
                        /*?}*/
                        if (restartedClearWalk) {
                            LOGGER.debug("Path blocked, replanning clear approach");
                            return;
                        }
                        autoState = AutoState.CLEARING_AREA;
                        return;
                    }
                    // Retry near the BUILD ZONE (not the standing position
                    // that may be across a gap) with increasing radius.
                    BlockPos retryTarget = lastWalkTargetZone != null
                            ? lastWalkTargetZone : walkTarget;
                    if (retryTarget != null) {
                        int extra = clearingRetry ? 3 * walkFailCount : walkFailCount;
                        int radius = (int) Math.ceil(range) + extra;
                        PathWalker.walkToNearby(retryTarget, radius);
                        LOGGER.debug("Path blocked, trying wider approach (r={})", radius);
                        return; // stay in walking state
                    }
                }
                walkFailCount = 0;

                // close-enough bail-out
                // All walk retries exhausted.  If the player is already
                // close enough to the target, fall back to building
                // from here rather than escalating further.
                if (arrivalState == AutoState.BUILDING
                        || arrivalState == AutoState.CLEARING_AREA) {
                    boolean inRange = false;
                    if (arrivalState == AutoState.CLEARING_AREA && lastClearTargetBlock != null) {
                        /*? if >=26.1 {*//*
                        inRange = canClearTargetFromHere(mc.player, mc.level, lastClearTargetBlock);
                        *//*?} else {*/
                        inRange = canClearTargetFromHere(mc.player, mc.world, lastClearTargetBlock);
                        /*?}*/
                    } else if (lastWalkTargetZone != null) {
                        /*? if >=26.1 {*//*
                        double d = mc.player.getEyePosition()
                                .distanceToSqr(Vec3.atCenterOf(lastWalkTargetZone));
                        *//*?} else {*/
                        double d = mc.player.getEyePos()
                                .squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone));
                        /*?}*/
                        if (d <= range * range) inRange = true;
                    }
                    if (!inRange
                            && arrivalState != AutoState.CLEARING_AREA
                            && walkTarget != null) {
                        /*? if >=26.1 {*//*
                        double d = mc.player.position()
                                .distanceToSqr(Vec3.atCenterOf(walkTarget));
                        *//*?} else if >=1.21.10 {*//*
                        double d = mc.player.getSyncedPos()
                                .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                        *//*?} else {*/
                        double d = mc.player.getPos()
                                .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                        /*?}*/
                        if (d <= (range + 1) * (range + 1)) inRange = true;
                    }
                    if (inRange) {
                        if (arrivalState == AutoState.BUILDING
                                /*? if >=26.1 {*//*
                                && !shouldResumeBuildFromCurrentWalk(mc.player, mc.level)) {
                                *//*?} else {*/
                                && !shouldResumeBuildFromCurrentWalk(mc.player, mc.world)) {
                                /*?}*/
                            /*? if >=26.1 {*//*
                            if (canForceBuildEvaluationFromCurrentWalk(mc.player, mc.level)) {
                            *//*?} else {*/
                            if (canForceBuildEvaluationFromCurrentWalk(mc.player, mc.world)) {
                            /*?}*/
                                PacketTelemetry.mark("build close handoff staging="
                                        + posLabel(lastWalkStandPos != null ? lastWalkStandPos : lastWalkApproachPos)
                                        + " zone=" + posLabel(lastWalkTargetZone));
                                resumeBuildFromCurrentStance("close enough at staging target");
                                return;
                            }
                            if (isAtCurrentBuildStagingPosition(mc.player)) {
                                markCurrentBuildApproachFailed("close enough but staging stance is not buildable");
                            }
                            if (lastWalkTargetZone != null) rememberFailedBuildZone(lastWalkTargetZone, 2);
                            if (walkTarget != null) rememberFailedBuildZone(walkTarget, 1);
                            deferBuildApproachReplan("walk retries exhausted near non-buildable target");
                            LOGGER.debug("Walk retries exhausted near target, but stance is not buildable; deferring build replan");
                            return;
                        }
                        triedPlacementWalk = false;
                        if (lastWalkTargetZone != null) rememberFailedBuildZone(lastWalkTargetZone, 2);
                        if (walkTarget != null) rememberFailedBuildZone(walkTarget, 1);
                        if (arrivalState == AutoState.BUILDING) {
                            resumeBuildFromCurrentStance("walk retries exhausted but target is in range");
                        } else {
                            autoState = arrivalState;
                        }
                        return;
                    }
                }

                // For clearing, try vanilla straight-line walk when
                // Baritone can't pathfind — the target is usually on
                // the same Y level and a direct WASD walk can reach it.
                if (!triedPlacementWalk
                        && arrivalState == AutoState.CLEARING_AREA
                        && lastWalkTargetZone != null) {
                    triedPlacementWalk = true;
                    LOGGER.debug("Can't path to clear target — trying vanilla walk");
                    PathWalker.walkToVanilla(lastWalkTargetZone);
                    return;
                }

                // If the target was above (or far from) us and we
                // haven't already tried placement, escalate to
                // waypoint-based placement walk.
                /*? if >=26.1 {*//*
                int playerY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int playerY = mc.player.getBlockPos().getY();
                /*?}*/
                if (!triedPlacementWalk
                        && lastWalkTargetZone != null
                        && (lastWalkTargetZone.getY() > playerY
                            || lastWalkTargetZone.getY() < playerY - 4)) {
                    triedPlacementWalk = true;
                    LOGGER.debug("Can't walk to zone — retrying with placement + waypoints");
                    int radius = (int) Math.ceil(range);
                    walkToZoneWithPlacement(mc.player, lastWalkTargetZone, radius);
                    return;
                }

                triedPlacementWalk = false;
                if (statusMessages) {
                    ChatHelper.info("§eWalking timed out, replanning from a new build zone.");
                }
                // Mark the approach and the blocked target as failed, then
                // immediately try to acquire a different zone before falling
                // back into the same dead area.
                if (walkTarget != null) rememberFailedBuildZone(walkTarget, 1);
                if (lastWalkTargetZone != null) rememberFailedBuildZone(lastWalkTargetZone, 2);
                if (arrivalState == AutoState.CLEARING_AREA && lastClearTargetBlock != null) {
                    failedClearTargets.add(lastClearTargetBlock);
                    consecutiveClearFailures++;
                }
                if (arrivalState == AutoState.BUILDING) {
                    deferBuildApproachReplan("walking timed out without fresh approach");
                    LOGGER.debug("Walking timed out without finding a fresh build approach; deferring build replan");
                    return;
                }
                autoState = arrivalState;
                noProgressTicks = 0;
            }
            return;
        }
        // Opportunistic build check
        // If Baritone has been working for 30+ ticks (1.5 s) and the
        // player is already within reach of the target, Baritone is
        // probably stalled on a short path it can't compute.  Cancel
        // and let the build-tick try placement from the current spot.
        // We give Baritone 30 ticks first so it has a real chance to
        // reposition the player to a better angle.
        if ((arrivalState == AutoState.BUILDING
                || arrivalState == AutoState.CLEARING_AREA)
                && PathWalker.getTicksWalking() >= 30) {
            boolean closeEnough = false;

            // Check 1: only stop early if the target is actually interactable now.
            if (arrivalState == AutoState.CLEARING_AREA && lastClearTargetBlock != null) {
                /*? if >=26.1 {*//*
                closeEnough = canClearTargetFromHere(mc.player, mc.level, lastClearTargetBlock);
                *//*?} else {*/
                closeEnough = canClearTargetFromHere(mc.player, mc.world, lastClearTargetBlock);
                /*?}*/
            } else if (lastWalkTargetZone != null) {
                /*? if >=26.1 {*//*
                double distSq = mc.player.getEyePosition()
                        .distanceToSqr(Vec3.atCenterOf(lastWalkTargetZone));
                *//*?} else {*/
                double distSq = mc.player.getEyePos()
                        .squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone));
                /*?}*/
                if (distSq <= range * range) {
                    closeEnough = true;
                }
            }

            // Check 2: player is within a few blocks of the walk target
            if (!closeEnough) {
                BlockPos walkTarget = PathWalker.getTarget();
                if (arrivalState != AutoState.CLEARING_AREA && walkTarget != null) {
                    /*? if >=26.1 {*//*
                    double walkDistSq = mc.player.position()
                            .distanceToSqr(Vec3.atCenterOf(walkTarget));
                    *//*?} else if >=1.21.10 {*//*
                    double walkDistSq = mc.player.getSyncedPos()
                            .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                    *//*?} else {*/
                    double walkDistSq = mc.player.getPos()
                            .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                    /*?}*/
                    if (walkDistSq <= (range + 1) * (range + 1)) {
                        closeEnough = true;
                    }
                }
            }

            if (closeEnough) {
                if (arrivalState == AutoState.BUILDING
                        /*? if >=26.1 {*//*
                        && !shouldResumeBuildFromCurrentWalk(mc.player, mc.level)) {
                        *//*?} else {*/
                        && !shouldResumeBuildFromCurrentWalk(mc.player, mc.world)) {
                        /*?}*/
                    PacketTelemetry.mark("build early handoff deferred"
                            + " zone=" + posLabel(lastWalkTargetZone)
                            + " walkTarget=" + posLabel(PathWalker.getTarget()));
                    return;
                }
                PathWalker.stop();
                walkFailCount = 0;
                triedPlacementWalk = false;
                if (lastWalkTargetZone != null) rememberFailedBuildZone(lastWalkTargetZone, 2);
                if (arrivalState == AutoState.BUILDING) {
                    resumeBuildFromCurrentStance("stopped early near build target");
                } else {
                    autoState = arrivalState;
                }
                return;
            }

            if (arrivalState == AutoState.BUILDING
                    && lastWalkTargetZone != null
                    && PathWalker.getTicksWalking() >= 80) {
                double targetDistSq;
                double walkDistSq = Double.MAX_VALUE;
                BlockPos walkTarget = PathWalker.getTarget();
                /*? if >=26.1 {*//*
                targetDistSq = mc.player.position()
                        .distanceToSqr(Vec3.atCenterOf(lastWalkTargetZone));
                if (walkTarget != null) {
                    walkDistSq = mc.player.position()
                            .distanceToSqr(Vec3.atCenterOf(walkTarget));
                }
                boolean hasHandoff = hasBuildHandoffOpportunityFromHere(mc.player, mc.level)
                        || canForceBuildEvaluationFromCurrentWalk(mc.player, mc.level);
                *//*?} else if >=1.21.10 {*//*
                targetDistSq = mc.player.getSyncedPos()
                        .squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone));
                if (walkTarget != null) {
                    walkDistSq = mc.player.getSyncedPos()
                            .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                }
                boolean hasHandoff = hasBuildHandoffOpportunityFromHere(mc.player, mc.world)
                        || canForceBuildEvaluationFromCurrentWalk(mc.player, mc.world);
                *//*?} else {*/
                targetDistSq = mc.player.getPos()
                        .squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone));
                if (walkTarget != null) {
                    walkDistSq = mc.player.getPos()
                            .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                }
                boolean hasHandoff = hasBuildHandoffOpportunityFromHere(mc.player, mc.world)
                        || canForceBuildEvaluationFromCurrentWalk(mc.player, mc.world);
                /*?}*/
                double targetNearSq = (range + 4.0) * (range + 4.0);
                boolean nearBadApproach = targetDistSq <= targetNearSq
                        || walkDistSq <= 9.0;
                if (nearBadApproach && !hasHandoff) {
                    /*? if >=26.1 {*//*
                    BlockPos playerBlockPos = mc.player.blockPosition();
                    *//*?} else {*/
                    BlockPos playerBlockPos = mc.player.getBlockPos();
                    /*?}*/
                    PacketTelemetry.mark("build handoff chokepoint"
                            + " zone=" + posLabel(lastWalkTargetZone)
                            + " stand=" + posLabel(lastWalkStandPos)
                            + " approach=" + posLabel(lastWalkApproachPos)
                            + " player=" + posLabel(playerBlockPos));
                    PathWalker.stop();
                    schedulePlacementAccessReposition(
                            lastWalkTargetZone,
                            "chokepoint without placeable handoff",
                            PLACEMENT_START_FAILURE_PAUSE_TICKS);
                    autoState = AutoState.BUILDING;
                    buildHandoffSettleTicks = BUILD_HANDOFF_SETTLE_TICKS;
                    LOGGER.debug("Build handoff chokepoint detected; scheduling access recovery");
                    return;
                }
            }
        }

        // While Baritone is walking with placement enabled, periodically
        // scan for scaffold blocks it may have placed.
        if (PathWalker.isPlacementEnabled()) {
            scaffoldScanCooldown--;
            if (scaffoldScanCooldown <= 0) {
                scaffoldScanCooldown = SCAFFOLD_SCAN_INTERVAL;
                /*? if >=26.1 {*//*
                scanForScaffoldBlocks(mc.player, mc.level);
                *//*?} else {*/
                scanForScaffoldBlocks(mc.player, mc.world);
                /*?}*/
            }
        }
    }

    /*? if >=26.1 {*//*
    private void tickWalkingToClear(Minecraft mc) {
    *//*?} else {*/
    private void tickWalkingToClear(MinecraftClient mc) {
    /*?}*/
        SetbackMonitor setbackMonitor = SetbackMonitor.get();
        int totalSetbacks = setbackMonitor.totalSetbacks();
        if (totalSetbacks != observedWalkingSetbacks) {
            observedWalkingSetbacks = totalSetbacks;
            walkingSetbackPauseTicks = WALK_SETBACK_PAUSE_TICKS;
            if (PathWalker.isActive()) {
                PathWalker.stop();
            }
            PlacementEngine.reset();
            noProgressTicks = 0;
            walkAttemptCooldown = Math.max(walkAttemptCooldown, WALK_SETBACK_PAUSE_TICKS);
            LOGGER.debug("Setback detected while clearing — pausing before replanning");
            return;
        }

        if (walkingSetbackPauseTicks > 0) {
            walkingSetbackPauseTicks--;
            return;
        }
        if (!setbackMonitor.isCalm()) {
            return;
        }

        if (!PathWalker.isActive()) {
            if (lastClearTargetBlock != null) {
                /*? if >=26.1 {*//*
                if (canClearTargetFromHere(mc.player, mc.level, lastClearTargetBlock)) {
                *//*?} else {*/
                if (canClearTargetFromHere(mc.player, mc.world, lastClearTargetBlock)) {
                /*?}*/
                    walkFailCount = 0;
                    triedPlacementWalk = false;
                    walkingSetbackPauseTicks = 0;
                    LOGGER.debug("Clear target is now in range");
                    autoState = AutoState.CLEARING_AREA;
                    noProgressTicks = 0;
                    return;
                }
            }

            walkFailCount++;
            if (lastClearTargetBlock != null && walkFailCount < MAX_WALK_RETRIES) {
                boolean restartedClearWalk;
                /*? if >=26.1 {*//*
                restartedClearWalk = startClearWalk(mc.player, mc.level, lastClearTargetBlock);
                *//*?} else {*/
                restartedClearWalk = startClearWalk(mc.player, mc.world, lastClearTargetBlock);
                /*?}*/
                if (restartedClearWalk) {
                    LOGGER.debug("Clear walk ended early, replanning exact approach");
                    return;
                }
            }

            BlockPos walkTarget = PathWalker.getTarget();
            if (!triedPlacementWalk && lastWalkTargetZone != null) {
                triedPlacementWalk = true;
                LOGGER.debug("Can't path to clear target — trying vanilla walk");
                PathWalker.walkToVanilla(lastWalkTargetZone);
                return;
            }

            triedPlacementWalk = false;
            walkFailCount = 0;
            if (statusMessages) {
                ChatHelper.info("§eClear walk timed out — trying another clear target.");
            }
            PacketTelemetry.mark("clear walk timeout target=" + posLabel(lastClearTargetBlock)
                    + " walk=" + posLabel(walkTarget)
                    + " last=" + posLabel(lastWalkTargetZone));
            if (walkTarget != null) failedZones.add(walkTarget);
            if (lastWalkTargetZone != null) failedZones.add(lastWalkTargetZone);
            if (lastClearTargetBlock != null) {
                failedClearTargets.add(lastClearTargetBlock);
                consecutiveClearFailures++;
                lastClearTargetBlock = null;
            }
            noProgressTicks = 0;

            /*? if >=26.1 {*//*
            BlockPos nextClear = findNextClearTarget(mc.player, mc.level);
            if (nextClear != null) {
                if (startClearWalk(mc.player, mc.level, nextClear)) {
                    autoState = AutoState.WALKING_TO_CLEAR;
                    return;
                }
                autoState = AutoState.CLEARING_AREA;
                return;
            }
            *//*?} else {*/
            BlockPos nextClear = findNextClearTarget(mc.player, mc.world);
            if (nextClear != null) {
                if (startClearWalk(mc.player, mc.world, nextClear)) {
                    autoState = AutoState.WALKING_TO_CLEAR;
                    return;
                }
                autoState = AutoState.CLEARING_AREA;
                return;
            }
            /*?}*/

            clearingDone = true;
            enterBuildMode("clearing skipped unreachable targets");
            return;
        }

        if (PathWalker.getTicksWalking() >= 30 && lastClearTargetBlock != null) {
            boolean closeEnough;
            /*? if >=26.1 {*//*
            closeEnough = canClearTargetFromHere(mc.player, mc.level, lastClearTargetBlock);
            *//*?} else {*/
            closeEnough = canClearTargetFromHere(mc.player, mc.world, lastClearTargetBlock);
            /*?}*/
            if (closeEnough) {
                PathWalker.stop();
                walkFailCount = 0;
                triedPlacementWalk = false;
                autoState = AutoState.CLEARING_AREA;
                return;
            }
        }

        if (!triedPlacementWalk
                && lastClearTargetBlock != null
                && PathWalker.getTicksWalking() >= 20) {
            /*? if >=26.1 {*//*
            double clearDistSq = mc.player.position().distanceToSqr(Vec3.atCenterOf(lastClearTargetBlock));
            boolean canClear = canClearTargetFromHere(mc.player, mc.level, lastClearTargetBlock);
            *//*?} else if >=1.21.10 {*//*
            double clearDistSq = mc.player.getSyncedPos().squaredDistanceTo(Vec3d.ofCenter(lastClearTargetBlock));
            boolean canClear = canClearTargetFromHere(mc.player, mc.world, lastClearTargetBlock);
            *//*?} else {*/
            double clearDistSq = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(lastClearTargetBlock));
            boolean canClear = canClearTargetFromHere(mc.player, mc.world, lastClearTargetBlock);
            /*?}*/
            if (!canClear && clearDistSq <= CLEAR_FINAL_APPROACH_TRIGGER_SQ) {
                triedPlacementWalk = true;
                LOGGER.debug("Clear target nearby but unresolved — forcing vanilla final approach");
                PathWalker.walkToVanillaExact(
                        lastClearTargetBlock,
                        CLEAR_PATH_ARRIVAL_DISTANCE * CLEAR_PATH_ARRIVAL_DISTANCE);
                return;
            }
        }

        PathWalker.tick();
    }

    // Records nearby blocks Baritone placed as scaffold (matches throwaway
    // items + schematic expects air). Called during placement walks.
    /*? if >=26.1 {*//*
    private void scanForScaffoldBlocks(LocalPlayer player, Level world) {
    *//*?} else {*/
    private void scanForScaffoldBlocks(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null || anchor == null || schematic == null) return;
        Set<String> throwaways = PathWalker.getThrowawayItemIds();
        if (throwaways.isEmpty()) return;

        /*? if >=26.1 {*//*
        BlockPos center = player.blockPosition();
        *//*?} else {*/
        BlockPos center = player.getBlockPos();
        /*?}*/
        int radius = 5; // scan nearby area
        Set<BlockPos> currentScan = new HashSet<>();
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = center.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = center.add(dx, dy, dz);
                    /*?}*/
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    // Check if this block's item is in Baritone's throwaway list
                    Item blockItem = state.getBlock().asItem();
                    if (blockItem == Items.AIR) continue;
                    /*? if >=26.1 {*//*
                    String itemId = BuiltInRegistries.ITEM.getKey(blockItem).toString();
                    *//*?} else {*/
                    String itemId = Registries.ITEM.getId(blockItem).toString();
                    /*?}*/
                    if (!throwaways.contains(itemId)) continue;

                    // Check if the schematic expects something else here
                    int sx = pos.getX() - anchor.getX();
                    int sy = pos.getY() - anchor.getY();
                    int sz = pos.getZ() - anchor.getZ();
                    if (schematic.contains(sx, sy, sz)) {
                        BlockState expected = schematic.getBlockState(sx, sy, sz);
                        if (!expected.isAir()) continue; // schematic wants a real block here
                    }

                    /*? if >=26.1 {*//*
                    BlockPos immutablePos = pos.immutable();
                    *//*?} else {*/
                    BlockPos immutablePos = pos.toImmutable();
                    /*?}*/
                    currentScan.add(immutablePos);
                    if (scaffoldScanSeeded
                            && !lastScaffoldScanBlocks.contains(immutablePos)
                            && !PrinterDatabase.isScaffold(immutablePos)) {
                        PrinterDatabase.addScaffold(immutablePos, itemId);
                    }
                }
            }
        }
        lastScaffoldScanBlocks.clear();
        lastScaffoldScanBlocks.addAll(currentScan);
        scaffoldScanSeeded = true;
    }

    /*? if >=26.1 {*//*
    private boolean isValidTrackedScaffold(BlockPos pos, String storedId, Level world) {
    *//*?} else {*/
    private boolean isValidTrackedScaffold(BlockPos pos, String storedId, World world) {
    /*?}*/
        if (pos == null || storedId == null || world == null) return false;
        BlockState state = world.getBlockState(pos);
        /*? if >=26.1 {*//*
        if (state.isAir() || state.canBeReplaced()) return false;
        *//*?} else {*/
        if (state.isAir() || state.isReplaceable()) return false;
        /*?}*/
        int sx = pos.getX() - anchor.getX();
        int sy = pos.getY() - anchor.getY();
        int sz = pos.getZ() - anchor.getZ();
        if (schematic != null && schematic.contains(sx, sy, sz)) {
            BlockState expected = schematic.getBlockState(sx, sy, sz);
            if (!expected.isAir()) return false;
        }
        Item blockItem = state.getBlock().asItem();
        if (blockItem == Items.AIR) return false;
        /*? if >=26.1 {*//*
        String currentId = BuiltInRegistries.ITEM.getKey(blockItem).toString();
        *//*?} else {*/
        String currentId = Registries.ITEM.getId(blockItem).toString();
        /*?}*/
        return currentId.equals(storedId);
    }

    private void finishScaffoldCleanupOrResume(String reason) {
        remainingCacheTick = Long.MIN_VALUE;
        if (countRemaining() > 0) {
            enterBuildMode(reason);
            return;
        }
        if (statusMessages) {
            ChatHelper.info("§aScaffold cleanup complete.");
        }
        autoState = AutoState.IDLE;
        markBuildResult(skippedItems.isEmpty()
                ? BuildResult.COMPLETED
                : BuildResult.COMPLETED_WITH_MISSING_MATERIALS);
    }

    // Area clearing
    // Mirrors the building scan: findNextClearTarget is the inverse of
    // findNextBuildZone, and tryClearNextBlock is the inverse of
    // tryPlaceNextBlock.  Building fills air with blocks; clearing
    // fills blocks with air.

    // Scan blocks within reach and break the nearest illegal one.
    // Mirrors tryPlaceNextBlock() but breaks instead of placing.
    /*? if >=26.1 {*//*
    private boolean tryClearNextBlock(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean tryClearNextBlock(ClientPlayerEntity player, World world) {
    /*?}*/
        double clearReach = getClearReach();
        double rangeSq = clearReach * clearReach;
        int maxReach = (int) Math.ceil(clearReach);
        Set<BlockPos> protectedStorage = getProtectedStoragePositions();

        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/

        /*? if >=26.1 {*//*
        Vec3 eye = player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = player.getEyePos();
        /*?}*/

        // Top-down demolition: prefer highest Y to avoid gravity drops
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        *//*?} else {*/
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        /*?}*/

        for (int dy = maxReach; dy >= -maxReach; dy--) {
            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    int wx = px + dx, wy = py + dy, wz = pz + dz;

                    /*? if >=26.1 {*//*
                    double dist = eye.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                    *//*?} else {*/
                    double dist = eye.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                    /*?}*/
                    if (dist > rangeSq) continue;

                    int sx = wx - anchor.getX();
                    int sy = wy - anchor.getY();
                    int sz = wz - anchor.getZ();
                    if (!schematic.contains(sx, sy, sz)) continue;

                    mutablePos.set(wx, wy, wz);
                    BlockState target = schematic.getBlockState(sx, sy, sz);
                    BlockState existing = world.getBlockState(mutablePos);

                    if (!isBroadSchematicClearTarget(
                            mutablePos, target, existing, protectedStorage)) {
                        continue;
                    }

                    // Prefer highest-Y, then nearest to eye
                    if (best != null) {
                        if (wy < best.getY()) continue;
                        if (wy == best.getY() && dist >= bestDist) continue;
                    }
                    bestDist = dist;
                    /*? if >=26.1 {*//*
                    best = mutablePos.immutable();
                    *//*?} else {*/
                    best = mutablePos.toImmutable();
                    /*?}*/
                }
            }
        }

        if (best == null) return false;

        // Start breaking
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        clearBreakTarget = best.immutable();
        *//*?} else {*/
        clearBreakTarget = best.toImmutable();
        /*?}*/
        lastClearTargetBlock = clearBreakTarget;
        clearBreakTicks = 0;
        /*? if >=26.1 {*//*
        clearSavedYaw = mc.player.getYRot();
        clearSavedPitch = mc.player.getXRot();
        *//*?} else {*/
        clearSavedYaw = mc.player.getYaw();
        clearSavedPitch = mc.player.getPitch();
        /*?}*/

        BlockState clearState = world.getBlockState(clearBreakTarget);
        PlacementEngine.selectBestTool(mc.player, mc, clearState);

        // Look at block center and start breaking
        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(clearBreakTarget);
        Vec3 toBlock = blockCenter.subtract(eye);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(clearBreakTarget);
        Vec3d toBlock = blockCenter.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
        PlacementEngine.setLookRotation(mc.player, breakYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

        if (!tryPrinterMining(1)) {
            return false;
        }
        /*? if >=26.1 {*//*
        mc.gameMode.startDestroyBlock(clearBreakTarget, Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        mc.interactionManager.attackBlock(clearBreakTarget, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
        /*?}*/

        LOGGER.debug("Clearing illegal block at {} {} {}",
                clearBreakTarget.getX(), clearBreakTarget.getY(),
                clearBreakTarget.getZ());
        return true;
    }

    // Find the nearest illegal block in the schematic — mirrors
    // findNextBuildZone().  Used as a walk target when nothing is
    // within reach.  Prefers highest Y (top-down clearing), then
    // nearest to player.
    /*? if >=26.1 {*//*
    private BlockPos findNextClearTarget(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findNextClearTarget(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null || schematic == null || anchor == null) return null;
        Set<BlockPos> protectedStorage = getProtectedStoragePositions();

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        *//*?} else {*/
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        /*?}*/

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = region.absY - 1; y >= 0; y--) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/

                        mutablePos.set(wx, wy, wz);
                        BlockState existing = world.getBlockState(mutablePos);

                        if (!isBroadSchematicClearTarget(
                                mutablePos, target, existing, protectedStorage)) {
                            continue;
                        }

                        // Prefer highest Y, then nearest
                        /*? if >=26.1 {*//*
                        double dist = playerPos.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                        *//*?} else {*/
                        double dist = playerPos.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                        /*?}*/
                        if (best != null) {
                            if (wy < best.getY()) continue;
                            if (wy == best.getY() && dist >= bestDist) continue;
                        }
                        bestDist = dist;
                        /*? if >=26.1 {*//*
                        best = mutablePos.immutable();
                        *//*?} else {*/
                        best = mutablePos.toImmutable();
                        /*?}*/
                    }
                }
            }
        }
        return best;
    }

    private boolean isBroadSchematicClearTarget(BlockPos pos, BlockState target,
                                                BlockState existing,
                                                Set<BlockPos> protectedStorage) {
        if (pos == null || target == null || existing == null) {
            return false;
        }
        // Non-air schematic cells are build/repair work, not broad demolition.
        if (!target.isAir()) {
            return false;
        }
        if (!PrinterDatabase.isScaffold(pos)) {
            return false;
        }
        if (isStorageBlacklistedForClearing(existing, pos, protectedStorage)) {
            return false;
        }
        if (existing.isAir()) {
            return false;
        }
        /*? if >=26.1 {*//*
        if (existing.canBeReplaced()) {
            return false;
        }
        *//*?} else {*/
        if (existing.isReplaceable()) {
            return false;
        }
        /*?}*/
        return !failedClearTargets.contains(pos);
    }

    // Reuse the build standing scan so clearing walks to a mineable spot.
    /*? if >=26.1 {*//*
    private BlockPos findClearApproachPosition(BlockPos target, Level world, LocalPlayer player) {
    *//*?} else {*/
    private BlockPos findClearApproachPosition(BlockPos target, World world, ClientPlayerEntity player) {
    /*?}*/
        return findStandingPosition(target, world, player, getClearReach());
    }

    // Only stop walking when the current position can really break this block.
    /*? if >=26.1 {*//*
    private boolean canClearTargetFromHere(LocalPlayer player, Level world, BlockPos target) {
    *//*?} else {*/
    private boolean canClearTargetFromHere(ClientPlayerEntity player, World world, BlockPos target) {
    /*?}*/
        if (player == null || world == null || target == null) return false;
        double clearReach = getClearReach();
        BlockState existing = world.getBlockState(target);
        if (isStorageBlacklistedForClearing(existing, target, getProtectedStoragePositions())) return false;
        if (!accessClearInProgress
                && !isBroadSchematicClearTarget(
                target,
                getRawSchematicStateAt(target),
                existing,
                getProtectedStoragePositions())) {
            return false;
        }
        if (existing.isAir()) return false;
        /*? if >=26.1 {*//*
        if (existing.canBeReplaced()) return false;
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= clearReach * clearReach;
        *//*?} else {*/
        if (existing.isReplaceable()) return false;
        return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target)) <= clearReach * clearReach;
        /*?}*/
    }

    // Start a clearing walk that ends in actual mining range.
    /*? if >=26.1 {*//*
    private boolean startClearWalk(LocalPlayer player, Level world, BlockPos target) {
    *//*?} else {*/
    private boolean startClearWalk(ClientPlayerEntity player, World world, BlockPos target) {
    /*?}*/
        if (target == null) return false;

        /*? if >=26.1 {*//*
        lastClearTargetBlock = target.immutable();
        *//*?} else {*/
        lastClearTargetBlock = target.toImmutable();
        /*?}*/

        if (canClearTargetFromHere(player, world, target)) {
            return false;
        }

        /*? if >=26.1 {*//*
        lastWalkTargetZone = target.immutable();
        *//*?} else {*/
        lastWalkTargetZone = target.toImmutable();
        /*?}*/
        PathWalker.walkToAdjacentExact(target, CLEAR_PATH_ARRIVAL_DISTANCE);
        return true;
    }

    /*? if >=26.1 {*//*
    private boolean startSpecificClearBreak(Minecraft mc, LocalPlayer player, Level world, BlockPos target) {
    *//*?} else {*/
    private boolean startSpecificClearBreak(MinecraftClient mc, ClientPlayerEntity player, World world, BlockPos target) {
    /*?}*/
        if (mc == null || player == null || world == null || target == null) return false;
        if (!canClearTargetFromHere(player, world, target)) return false;

        /*? if >=26.1 {*//*
        clearBreakTarget = target.immutable();
        *//*?} else {*/
        clearBreakTarget = target.toImmutable();
        /*?}*/
        lastClearTargetBlock = clearBreakTarget;
        clearBreakTicks = 0;
        /*? if >=26.1 {*//*
        clearSavedYaw = mc.player.getYRot();
        clearSavedPitch = mc.player.getXRot();
        Vec3 eye = player.getEyePosition();
        Vec3 blockCenter = Vec3.atCenterOf(clearBreakTarget);
        Vec3 toBlock = blockCenter.subtract(eye);
        *//*?} else {*/
        clearSavedYaw = mc.player.getYaw();
        clearSavedPitch = mc.player.getPitch();
        Vec3d eye = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(clearBreakTarget);
        Vec3d toBlock = blockCenter.subtract(eye);
        /*?}*/

        BlockState clearState = world.getBlockState(clearBreakTarget);
        PlacementEngine.selectBestTool(mc.player, mc, clearState);

        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        PlacementEngine.setLookRotation(mc.player, breakYaw, Mth.clamp(breakPitch, -90.0f, 90.0f));
        mc.gameMode.startDestroyBlock(clearBreakTarget, Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        PlacementEngine.setLookRotation(mc.player, breakYaw, MathHelper.clamp(breakPitch, -90.0f, 90.0f));
        if (!tryPrinterMining(1)) {
            return false;
        }
        mc.interactionManager.attackBlock(clearBreakTarget, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
        /*?}*/

        PacketTelemetry.mark("clear direct target=" + posLabel(clearBreakTarget)
                + " access=" + accessClearInProgress);
        LOGGER.debug("Clearing targeted block at {} {} {}",
                clearBreakTarget.getX(), clearBreakTarget.getY(), clearBreakTarget.getZ());
        return true;
    }

    // Clears illegal blocks from the schematic footprint.
    // Mirrors the building tick: try breaking in-reach blocks first
    // (tryClearNextBlock), then walk to the nearest remaining illegal
    // block if nothing is nearby.  Building fills air → blocks;
    // clearing fills blocks → air.
    /*? if >=26.1 {*//*
    private void tickClearingArea(Minecraft mc) {
    *//*?} else {*/
    private void tickClearingArea(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        // ── If PlacementEngine is actively breaking / placing, wait ──
        if (PlacementEngine.isBusy()) return;

        // Continue breaking current target
        if (clearBreakTarget != null) {
            BlockState current = world.getBlockState(clearBreakTarget);
            if (isStorageBlacklistedForClearing(current, clearBreakTarget, getProtectedStoragePositions())) {
                // Storage/container blacklist always wins: cancel this target.
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                clearBreakTarget = null;
                lastClearTargetBlock = null;
                if (accessClearInProgress) {
                    accessClearInProgress = false;
                    resumeBuildFromCurrentStance("access clear target became invalid");
                }
                clearBreakTicks = 0;
                return;
            }
            if (!accessClearInProgress
                    && !isBroadSchematicClearTarget(
                    clearBreakTarget,
                    getRawSchematicStateAt(clearBreakTarget),
                    current,
                    getProtectedStoragePositions())) {
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                PacketTelemetry.mark("clear cancel unsafe target=" + posLabel(clearBreakTarget));
                clearBreakTarget = null;
                lastClearTargetBlock = null;
                clearBreakTicks = 0;
                return;
            }
            /*? if >=26.1 {*//*
            if (current.isAir() || current.canBeReplaced()) {
            *//*?} else {*/
            if (current.isAir() || current.isReplaceable()) {
            /*?}*/
                // Block broken
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(clearSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(clearSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(clearSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(clearSavedPitch);
                /*?}*/
                clearBlocksBroken++;
                consecutiveClearFailures = 0;
                clearStallResets = 0;
                clearBreakTarget = null;
                lastClearTargetBlock = null;
                clearBreakTicks = 0;
                clearCooldownTicks = BREAK_COOLDOWN_TICKS;
                if (accessClearInProgress) {
                    accessClearInProgress = false;
                    resumeBuildFromCurrentStance("access clear complete");
                }
                return;
            }

            clearBreakTicks++;
            if (clearBreakTicks > MAX_CLEAR_BREAK_TICKS) {
                // Timed out — skip this block
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(clearSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(clearSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(clearSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(clearSavedPitch);
                /*?}*/
                LOGGER.debug("Clear break timed out at {} {} {}",
                        clearBreakTarget.getX(), clearBreakTarget.getY(),
                        clearBreakTarget.getZ());
                failedClearTargets.add(clearBreakTarget);
                consecutiveClearFailures++;
                clearBreakTarget = null;
                lastClearTargetBlock = null;
                clearBreakTicks = 0;
                clearCooldownTicks = BREAK_COOLDOWN_TICKS;
                if (accessClearInProgress) {
                    accessClearInProgress = false;
                    enterBuildMode("access clear timed out");
                }
                return;
            }

            // Continue breaking — maintain look direction
            /*? if >=26.1 {*//*
            Vec3 eyePos = mc.player.getEyePosition();
            *//*?} else {*/
            Vec3d eyePos = mc.player.getEyePos();
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 blockCenter = Vec3.atCenterOf(clearBreakTarget);
            *//*?} else {*/
            Vec3d blockCenter = Vec3d.ofCenter(clearBreakTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toBlock = blockCenter.subtract(eyePos);
            *//*?} else {*/
            Vec3d toBlock = blockCenter.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
            /*? if >=26.1 {*//*
            float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            /*?}*/
            PlacementEngine.setLookRotation(mc.player, breakYaw,
                    /*? if >=26.1 {*//*
                    Mth.clamp(breakPitch, -90.0f, 90.0f));
                    *//*?} else {*/
                    MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                    /*?}*/

            if (!tryPrinterMining(1)) {
                return;
            }
            /*? if >=26.1 {*//*
            mc.gameMode.continueDestroyBlock(clearBreakTarget, Direction.UP);
            *//*?} else {*/
            mc.interactionManager.updateBlockBreakingProgress(clearBreakTarget, Direction.UP);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.player.swing(InteractionHand.MAIN_HAND);
            *//*?} else {*/
            mc.player.swingHand(Hand.MAIN_HAND);
            /*?}*/
            return;
        }

        // Inter-block cooldown to avoid FastBreak detection
        if (clearCooldownTicks > 0) {
            clearCooldownTicks--;
            return;
        }

        if (lastClearTargetBlock != null) {
            BlockState targetedState = world.getBlockState(lastClearTargetBlock);
            /*? if >=26.1 {*//*
            boolean targetedGone = targetedState.isAir() || targetedState.canBeReplaced();
            *//*?} else {*/
            boolean targetedGone = targetedState.isAir() || targetedState.isReplaceable();
            /*?}*/
            if (targetedGone) {
                lastClearTargetBlock = null;
                if (accessClearInProgress) {
                    accessClearInProgress = false;
                    resumeBuildFromCurrentStance("access clear complete");
                    return;
                }
            } else {
                if (startSpecificClearBreak(mc, mc.player, world, lastClearTargetBlock)) {
                    return;
                }
                if (startClearWalk(mc.player, world, lastClearTargetBlock)) {
                    autoState = AutoState.WALKING_TO_CLEAR;
                    return;
                }
            }
        }

        // Dump inventory if nearly full
        if (MoarMod.getChestManager().dumpChestCount() > 0
                && MoarMod.getChestManager().isInventoryFull(mc)) {
            startDumpRun(mc);
            return;
        }

        // ── Try breaking an in-range block (mirrors tryPlaceNextBlock) ──
        if (tryClearNextBlock(mc.player, world)) {
            return;
        }

        // Nothing in reach — stall detection
        if (consecutiveClearFailures >= MAX_CONSECUTIVE_CLEAR_FAILURES) {
            clearStallResets++;
            if (clearStallResets > MAX_CLEAR_STALL_RESETS) {
                if (statusMessages) {
                    ChatHelper.info("§eCan't reach remaining illegal blocks — building from here.");
                }
                LOGGER.warn("Clearing gave up after {} stall resets ({} blocks broken)",
                        clearStallResets - 1, clearBlocksBroken);
                clearingDone = true;
                lastClearTargetBlock = null;
                enterBuildMode("clearing gave up after stall resets");
                return;
            }
            // Recovery: reset failed targets and retry
            LOGGER.info("Clearing stall recovery {}/{} — resetting {} failed targets",
                    clearStallResets, MAX_CLEAR_STALL_RESETS,
                    failedClearTargets.size());
            if (statusMessages) {
                ChatHelper.info("§eClearing stalled — retrying unreachable blocks ("
                        + clearStallResets + "/" + MAX_CLEAR_STALL_RESETS + ")");
            }
            failedClearTargets.clear();
            consecutiveClearFailures = 0;
            // Fall through to find next target with a clean slate
        }

        // Find walk target (mirrors findNextBuildZone)
        BlockPos target = findNextClearTarget(mc.player, world);

        if (target == null && !failedClearTargets.isEmpty()) {
            clearStallResets++;
            if (clearStallResets <= MAX_CLEAR_STALL_RESETS) {
                if (statusMessages) {
                    ChatHelper.info("§eAll targets skipped — retrying ("
                            + clearStallResets + "/" + MAX_CLEAR_STALL_RESETS + ")");
                }
                failedClearTargets.clear();
                consecutiveClearFailures = 0;
                target = findNextClearTarget(mc.player, world);
            }
        }
        if (target == null) {
            clearingDone = true;
            lastClearTargetBlock = null;
            if (statusMessages) {
                if (clearBlocksBroken > 0) {
                    ChatHelper.info("§aArea cleared! §7Removed §e" + clearBlocksBroken
                            + "§7 illegal block(s). Commencing build...");
                } else {
                    ChatHelper.info("§aNo illegal blocks found. Commencing build...");
                }
            }
            enterBuildMode("clearing complete");
            return;
        }

        /*? if >=26.1 {*//*
        if (!startClearWalk(mc.player, world, target)) {
        *//*?} else {*/
        if (!startClearWalk(mc.player, world, target)) {
        /*?}*/
            autoState = AutoState.CLEARING_AREA;
            return;
        }
        autoState = AutoState.WALKING_TO_CLEAR;
    }

    // Breaks scaffold blocks one at a time until all are removed.
    /*? if >=26.1 {*//*
    private void tickCleaningScaffold(Minecraft mc) {
    *//*?} else {*/
    private void tickCleaningScaffold(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
        /*?}*/

        // walking to a scaffold block
        if (PathWalker.isActive()) {
            PathWalker.tick();
            return;
        }

        // currently breaking a scaffold block
        if (scaffoldBreakTarget != null) {
            /*? if >=26.1 {*//*
            BlockState current = mc.level.getBlockState(scaffoldBreakTarget);
            *//*?} else {*/
            BlockState current = mc.world.getBlockState(scaffoldBreakTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            if (current.isAir() || current.canBeReplaced()) {
            *//*?} else {*/
            if (current.isAir() || current.isReplaceable()) {
            /*?}*/
                // Block broken — clean up
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(scaffoldSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(scaffoldSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(scaffoldSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(scaffoldSavedPitch);
                /*?}*/
                PrinterDatabase.removeScaffold(scaffoldBreakTarget);
                scaffoldBreakTarget = null;
                scaffoldBreakTicks = 0;
                scaffoldCooldownTicks = BREAK_COOLDOWN_TICKS;
                return; // next tick will pick up next scaffold
            }

            scaffoldBreakTicks++;
            if (scaffoldBreakTicks > MAX_SCAFFOLD_BREAK_TICKS) {
                // Timed out — skip this block
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(scaffoldSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(scaffoldSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(scaffoldSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(scaffoldSavedPitch);
                /*?}*/
                PrinterDatabase.removeScaffold(scaffoldBreakTarget);
                scaffoldBreakTarget = null;
                scaffoldBreakTicks = 0;
                scaffoldCooldownTicks = BREAK_COOLDOWN_TICKS;
                return;
            }

            // Maintain look direction + continue breaking
            /*? if >=26.1 {*//*
            Vec3 eyePos = mc.player.getEyePosition();
            *//*?} else {*/
            Vec3d eyePos = mc.player.getEyePos();
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 blockCenter = Vec3.atCenterOf(scaffoldBreakTarget);
            *//*?} else {*/
            Vec3d blockCenter = Vec3d.ofCenter(scaffoldBreakTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toBlock = blockCenter.subtract(eyePos);
            *//*?} else {*/
            Vec3d toBlock = blockCenter.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
            /*? if >=26.1 {*//*
            float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            /*?}*/
            PlacementEngine.setLookRotation(mc.player, breakYaw,
                    /*? if >=26.1 {*//*
                    Mth.clamp(breakPitch, -90.0f, 90.0f));
                    *//*?} else {*/
                    MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                    /*?}*/

            if (!tryPrinterMining(1)) {
                return;
            }
            /*? if >=26.1 {*//*
            mc.gameMode.continueDestroyBlock(scaffoldBreakTarget, Direction.UP);
            *//*?} else {*/
            mc.interactionManager.updateBlockBreakingProgress(scaffoldBreakTarget, Direction.UP);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.player.swing(InteractionHand.MAIN_HAND);
            *//*?} else {*/
            mc.player.swingHand(Hand.MAIN_HAND);
            /*?}*/
            return;
        }

        // Inter-block cooldown to avoid FastBreak detection
        if (scaffoldCooldownTicks > 0) {
            scaffoldCooldownTicks--;
            return;
        }

        // pick next scaffold block
        if (!PrinterDatabase.hasScaffold()) {
            finishScaffoldCleanupOrResume("scaffold cleanup finished with remaining work");
            return;
        }

        // Prune scaffold entries whose world block is already gone
        // or no longer looks like temporary pathing scaffold.
        List<BlockPos> gone = new ArrayList<>();
        for (var entry : PrinterDatabase.getScaffoldEntries().entrySet()) {
            BlockPos pos = entry.getKey();
            String storedId = entry.getValue();
            /*? if >=26.1 {*//*
            if (!isValidTrackedScaffold(pos, storedId, mc.level)) {
            *//*?} else {*/
            if (!isValidTrackedScaffold(pos, storedId, mc.world)) {
            /*?}*/
                gone.add(pos);
            }
        }
        PrinterDatabase.removeScaffoldBatch(gone);

        if (!PrinterDatabase.hasScaffold()) {
            finishScaffoldCleanupOrResume("scaffold cleanup pruned stale entries");
            return;
        }

        // Find the closest scaffold block
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        Vec3 eye = mc.player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = mc.player.getEyePos();
        /*?}*/
        for (BlockPos pos : PrinterDatabase.getScaffoldEntries().keySet()) {
            /*? if >=26.1 {*//*
            double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
            *//*?} else {*/
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
            /*?}*/
            if (d < closestDist) {
                closestDist = d;
                closest = pos;
            }
        }

        if (closest == null) {
            finishScaffoldCleanupOrResume("scaffold cleanup had no valid target");
            return;
        }

        double reachSq = range * range;
        if (closestDist > reachSq) {
            // Walk to the scaffold block
            PathWalker.walkToNearby(closest, (int) Math.ceil(range));
            LOGGER.debug("Walking to scaffold at {} {} {} ({} remaining)",
                    closest.getX(), closest.getY(), closest.getZ(),
                    PrinterDatabase.scaffoldCount());
            return;
        }

        // In reach — start breaking
        /*? if >=26.1 {*//*
        scaffoldBreakTarget = closest.immutable();
        *//*?} else {*/
        scaffoldBreakTarget = closest.toImmutable();
        /*?}*/
        scaffoldBreakTicks = 0;
        /*? if >=26.1 {*//*
        scaffoldSavedYaw = mc.player.getYRot();
        *//*?} else {*/
        scaffoldSavedYaw = mc.player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        scaffoldSavedPitch = mc.player.getXRot();
        *//*?} else {*/
        scaffoldSavedPitch = mc.player.getPitch();
        /*?}*/

        // Select the best tool for breaking this scaffold block
        /*? if >=26.1 {*//*
        BlockState scaffoldState = mc.level.getBlockState(scaffoldBreakTarget);
        *//*?} else {*/
        BlockState scaffoldState = mc.world.getBlockState(scaffoldBreakTarget);
        /*?}*/
        PlacementEngine.selectBestTool(mc.player, mc, scaffoldState);

        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(scaffoldBreakTarget);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(scaffoldBreakTarget);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toBlock = blockCenter.subtract(eye);
        *//*?} else {*/
        Vec3d toBlock = blockCenter.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
        PlacementEngine.setLookRotation(mc.player, breakYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

        if (!tryPrinterMining(1)) {
            return;
        }
        /*? if >=26.1 {*//*
        mc.gameMode.startDestroyBlock(scaffoldBreakTarget, Direction.UP);
        *//*?} else {*/
        mc.interactionManager.attackBlock(scaffoldBreakTarget, Direction.UP);
        /*?}*/
        /*? if >=26.1 {*//*
        mc.player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        mc.player.swingHand(Hand.MAIN_HAND);
        /*?}*/

        LOGGER.debug("Breaking scaffold at {} {} {} ({} remaining)",
                scaffoldBreakTarget.getX(), scaffoldBreakTarget.getY(),
                scaffoldBreakTarget.getZ(), PrinterDatabase.scaffoldCount());
    }

    /*? if >=26.1 {*//*
    private void tickWalkingToSupply(Minecraft mc) {
    *//*?} else {*/
    private void tickWalkingToSupply(MinecraftClient mc) {
    /*?}*/
        if (!PathWalker.isActive()) {
            // Try opening the chest regardless of PathWalker arrival
            // status — the player may be within interaction range even
            // if Baritone stopped slightly outside PathWalker's strict
            // arrival threshold.
            if (supplyTarget != null && tryOpenChest(mc, supplyTarget)) {
                clearPendingContainerAction();
                blockedRestockSlots.clear();
                autoState = AutoState.RESTOCKING;
                restockWaitTicks = 0;
                chestSyncDelay = 0;
                triedWaypointRestock = false;
                triedLinearRestock = false;
                triedPlacementRestock = false;
                supplyDescentPhase = 0;
                supplyDescentTarget = null;
                return;
            }

            // multi-phase descent continuation
            // Phase 1 complete (horizontal walk) → start phase 2 (descend)
            if (supplyDescentPhase == 1 && supplyDescentTarget != null) {
                supplyDescentPhase = 2;
                LOGGER.debug("Descending to Y={}", supplyDescentTarget.getY());
                PathWalker.walkToYLevelWithPlacement(
                        supplyDescentTarget.getY(), mc.player);
                return;
            }
            // Phase 2 complete (GoalYLevel descent) → start phase 3 (approach)
            if (supplyDescentPhase == 2 && supplyDescentTarget != null) {
                // Check if GoalYLevel actually brought us close to target Y.
                // If the player is still far above, Baritone couldn't path
                // down the pillar — fall back to mining descent.
                /*? if >=26.1 {*//*
                int playerY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int playerY = mc.player.getBlockPos().getY();
                /*?}*/
                int targetY = supplyDescentTarget.getY();
                if (playerY - targetY > 5) {
                    LOGGER.debug("GoalYLevel failed (supply) — mining down from Y={} to Y={}",
                            playerY, targetY);
                    supplyDescentPhase = 2; // stay in phase 2 for re-check
                    PathWalker.startMiningDescent(targetY);
                    return;
                }
                supplyDescentPhase = 3;
                LOGGER.debug("Walking to chest...");
                // Short horizontal walk from wherever we ended up
                // to the actual chest position.
                /*? if >=26.1 {*//*
                double dist = Math.sqrt(mc.player.blockPosition()
                *//*?} else {*/
                double dist = Math.sqrt(mc.player.getBlockPos()
                /*?}*/
                        /*? if >=26.1 {*//*
                        .distSqr(supplyDescentTarget));
                        *//*?} else {*/
                        .getSquaredDistance(supplyDescentTarget));
                        /*?}*/
                if (dist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            /*? if >=26.1 {*//*
                            mc.player.blockPosition(), supplyDescentTarget, 48);
                            *//*?} else {*/
                            mc.player.getBlockPos(), supplyDescentTarget, 48);
                            /*?}*/
                    PathWalker.walkToViaWaypointsWithPlacement(
                            horizLegs, 2, mc.player);
                } else {
                    PathWalker.walkToWithPlacement(
                            supplyDescentTarget, 2, mc.player);
                }
                return;
            }
            // Phase 3 complete or not in descent — clear descent state
            supplyDescentPhase = 0;
            supplyDescentTarget = null;

            // ── waypoint retry: compute database-driven waypoints and
            //    try again with placement enabled. Database waypoints
            //    may follow built structure paths (stairs, corridors)
            //    that straight-line legs miss. ─────────────────────────
            if (!triedWaypointRestock && supplyTarget != null) {
                List<BlockPos> waypoints = computeSupplyWaypoints(
                        /*? if >=26.1 {*//*
                        mc.player.blockPosition(), supplyTarget);
                        *//*?} else {*/
                        mc.player.getBlockPos(), supplyTarget);
                        /*?}*/
                if (waypoints.size() > 1) {
                    triedWaypointRestock = true;
                    LOGGER.debug("Retrying via {} database waypoint(s) + placement",
                            waypoints.size() - 1);
                    PathWalker.walkToViaWaypointsWithPlacement(waypoints, 2, mc.player);
                    return;
                }
            }

            // Elevation-aware retry
            if (!triedLinearRestock && supplyTarget != null) {
                triedLinearRestock = true;
                double retryDy = Math.abs(supplyTarget.getY()
                        /*? if >=26.1 {*//*
                        - mc.player.blockPosition().getY());
                        *//*?} else {*/
                        - mc.player.getBlockPos().getY());
                        /*?}*/
                if (retryDy > 8) {
                    LOGGER.debug("Retrying with elevation-aware placement walk");
                    walkToZoneWithPlacement(mc.player, supplyTarget, 2);
                    return;
                } else {
                    // Flat — try shorter legs
                    List<BlockPos> linear = computeLinearWaypoints(
                            /*? if >=26.1 {*//*
                            mc.player.blockPosition(), supplyTarget, 24);
                            *//*?} else {*/
                            mc.player.getBlockPos(), supplyTarget, 24);
                            /*?}*/
                    if (linear.size() > 1) {
                        LOGGER.debug("Retrying with shorter legs ({} x 24-block) + placement",
                                linear.size() - 1);
                        PathWalker.walkToViaWaypointsWithPlacement(linear, 2, mc.player);
                        return;
                    }
                }
            }

            // Direct placement retry
            if (!triedPlacementRestock && supplyTarget != null) {
                triedPlacementRestock = true;
                LOGGER.debug("Retrying with direct placement walk");
                PathWalker.walkToWithPlacement(supplyTarget, 2, mc.player);
                return;
            }

            // Mark this chest as unreachable so startRestockRun skips it
            if (supplyTarget != null) {
                /*? if >=26.1 {*//*
                unreachableChests.add(supplyTarget.immutable());
                *//*?} else {*/
                unreachableChests.add(supplyTarget.toImmutable());
                /*?}*/
            }
            triedWaypointRestock = false;
            triedLinearRestock = false;
            triedPlacementRestock = false;
            restockFailures++;
            if (restockFailures >= MAX_RESTOCK_FAILURES) {
                if (statusMessages) {
                    ChatHelper.info("§cCan't reach supply chests after "
                            + restockFailures + " attempts."
                            + " Skipping missing items, building with what we have."
                            + "\n§7Still need: " + formatNeededItemIds(neededItems));
                }
                // Skip these items and continue building
                skippedItems.addAll(lastMissingItems);
                addNeededToSkipped();
                resumeBuildFromCurrentStance("restock walk failed too many times");
            } else {
                if (statusMessages) {
                    ChatHelper.info("§eSupply chest at §f"
                            + supplyTarget.getX() + " " + supplyTarget.getY()
                            + " " + supplyTarget.getZ()
                            + "§e unreachable (attempt " + restockFailures
                            + "/" + MAX_RESTOCK_FAILURES + ") — trying another."
                            + "\n§7Looking for: " + formatNeededItemIds(neededItems));
                }
                resumeBuildFromCurrentStance("restock walk failed, trying another chest");
            }
            return;
        }
        // While Baritone is walking with placement enabled, periodically
        // scan for scaffold blocks it may have placed (bridges, pillars).
        if (PathWalker.isPlacementEnabled()) {
            scaffoldScanCooldown--;
            if (scaffoldScanCooldown <= 0) {
                scaffoldScanCooldown = SCAFFOLD_SCAN_INTERVAL;
                /*? if >=26.1 {*//*
                scanForScaffoldBlocks(mc.player, mc.level);
                *//*?} else {*/
                scanForScaffoldBlocks(mc.player, mc.world);
                /*?}*/
            }
        }
        PathWalker.tick();
    }

    /*? if >=26.1 {*//*
    private void tickRestocking(Minecraft mc) {
    *//*?} else {*/
    private void tickRestocking(MinecraftClient mc) {
    /*?}*/
        restockWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu containerHandler) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
        /*?}*/
            if (tickPendingContainerAction(containerHandler, ContainerTransferLane.RESTOCK, blockedRestockSlots)) {
                return;
            }
            // Wait for the server to sync chest contents — the handler is
            // created by OpenScreenS2CPacket, but slot data arrives via a
            // separate InventoryS2CPacket that may lag by 1-2 ticks.
            chestSyncDelay++;
            if (chestSyncDelay < CHEST_SYNC_DELAY) return;

            // Snapshot inventory before taking items so we can detect failure
            Map<Item, Integer> invBefore = PlacementEngine.getInventoryContents();
            int neededShulkerBefore = findShulkerWithNeededItems(mc.player);
            int neededShulkerCountBefore = countShulkersWithNeededItems(mc.player);

            // Index chest contents before taking items (snapshot for future queries)
            if (supplyTarget != null) {
                MoarMod.getChestManager().scanOpenChest(supplyTarget, containerHandler);
            }

            boolean transferDone = takeNeededItems(mc, mc.player, containerHandler);
            if (!transferDone) {
                return;
            }
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/

            // Verify that items were actually obtained
            Map<Item, Integer> invAfter = PlacementEngine.getInventoryContents();
            boolean inventoryGained = false;
            for (var entry : invAfter.entrySet()) {
                if (entry.getValue() > invBefore.getOrDefault(entry.getKey(), 0)) {
                    inventoryGained = true;
                    break;
                }
            }
            int neededShulkerAfter = findShulkerWithNeededItems(mc.player);
            int neededShulkerCountAfter = countShulkersWithNeededItems(mc.player);
            boolean gainedNeededShulker = neededShulkerCountAfter > neededShulkerCountBefore;
            boolean gotSomething = inventoryGained || gainedNeededShulker;
            if (gainedNeededShulker) {
                PacketTelemetry.mark("restock success via needed-shulker before="
                        + neededShulkerBefore + " after=" + neededShulkerAfter
                        + " countBefore=" + neededShulkerCountBefore
                        + " countAfter=" + neededShulkerCountAfter);
            }

            // Only invalidate the snapshot when we actually modified the
            // chest. If we took nothing, the scanOpenChest snapshot from
            // a few lines above is still accurate, and keeping it lets
            // ChestManager.findBestChest correctly skip this chest next
            // cycle for items it doesn't contain (instead of repeatedly
            // walking back to an empty chest because the unindexed-chest
            // fallback ranking re-picked it).
            if (inventoryGained && supplyTarget != null) {
                MoarMod.getChestManager().invalidateSnapshot(supplyTarget);
            }

            if (!gotSomething) {
                if (neededShulkerAfter >= 0 && neededShulkerCountAfter == neededShulkerCountBefore) {
                    PacketTelemetry.mark("restock unchanged needed-shulker before="
                            + neededShulkerBefore + " after=" + neededShulkerAfter
                            + " count=" + neededShulkerCountAfter);
                }
                restockFailures++;
                if (restockFailures >= MAX_RESTOCK_FAILURES) {
                    if (statusMessages) {
                        ChatHelper.info("§cSupply chests don't have needed items."
                                + " Skipping missing items, building with what we have."
                                + "\n§7Still need: " + formatNeededItemIds(neededItems));
                    }
                    skippedItems.addAll(lastMissingItems);
                    addNeededToSkipped();
                    resumeBuildFromCurrentStance("opened chest but got no needed items");
                    return;
                }
                LOGGER.debug("Chest had no needed items, trying another chest");
                // Try a different chest next time
                resumeBuildFromCurrentStance("restock chest empty for current working set");
                return;
            }

            // Success — reset failure counter
            restockFailures = 0;
            // We got items — previously-skipped materials may now be
            // available, so clear the skip list and let tryPlaceNextBlock
            // re-evaluate everything.
            skippedItems.clear();
            if (gainedNeededShulker) {
                shulkerNoSpaceSkipped = false;
            }
            // Only clear the specific chest we just used from the
            // unreachable list — other chests may genuinely be
            // unreachable from different positions in a large build.
            if (supplyTarget != null) {
                /*? if >=26.1 {*//*
                unreachableChests.remove(supplyTarget.immutable());
                *//*?} else {*/
                unreachableChests.remove(supplyTarget.toImmutable());
                /*?}*/
            }

            // Check if we grabbed any shulker boxes that need unloading.
            // If so, transition to UNLOADING_SHULKER instead of walking
            // back immediately — the build needs loose items, not shulkers.
            if (!shulkerNoSpaceSkipped
                    && neededShulkerAfter >= 0
                    && (gainedNeededShulker || inventoryGained)) {
                if (statusMessages) {
                    ChatHelper.info("§aRestocked (shulkers found). Unloading shulker boxes…");
                }
                shulkerUnloadPhase = 0;
                shulkerUnloadTicks = 0;
                shulkerTotalTicks = 0;
                shulkerUnloadFailures = 0;
                shulkerMovedNeededItems = false;
                clearPendingContainerAction();
                blockedShulkerSlots.clear();
                autoState = AutoState.UNLOADING_SHULKER;
                return;
            }

            if (statusMessages) {
                ChatHelper.info("§aRestocked. Walking back to build.");
            }

            if (lastBuildPos != null) {
                // Use elevation-aware navigation for the return walk.
                // The build zone may be far above the supply chest
                // (e.g. glass platform at Y=-22 vs ground at Y=-60).
                // walkToZoneWithPlacement handles ascent (pillar up in
                // 8-block steps) and descent (3-phase GoalYLevel).
                double returnDy = Math.abs(lastBuildPos.getY()
                        /*? if >=26.1 {*//*
                        - mc.player.blockPosition().getY());
                        *//*?} else {*/
                        - mc.player.getBlockPos().getY());
                        /*?}*/
                int radius = (int) Math.ceil(range);
                if (returnDy > 8) {
                    walkToZoneWithPlacement(mc.player, lastBuildPos, radius);
                } else {
                    PathWalker.walkToNearby(lastBuildPos, radius);
                }
                autoState = AutoState.WALKING_BACK;
            } else {
                resumeBuildFromCurrentStance("restock complete without return walk");
            }
            return;
        }

        if (restockWaitTicks >= CHEST_OPEN_TIMEOUT) {
            if (statusMessages) {
                ChatHelper.info("§eChest didn't open, resuming build.");
            }
            resumeBuildFromCurrentStance("restock chest open timeout");
        }
    }

    // DUMP CHEST — deposit mined items to free inventory space during clearing

    /*? if >=26.1 {*//*
    private void tickDumping(Minecraft mc) {
    *//*?} else {*/
    private void tickDumping(MinecraftClient mc) {
    /*?}*/
        if (dumpMode == DumpMode.LOCAL_SHULKER) {
            tickDumpingToLocalShulker(mc);
            return;
        }

        dumpWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu containerHandler) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
        /*?}*/
            if (tickPendingContainerAction(containerHandler, ContainerTransferLane.DUMP, blockedDumpSlots)) {
                return;
            }
            dumpSyncDelay++;
            if (dumpSyncDelay < DUMP_SYNC_DELAY) return;

            /*? if >=26.1 {*//*
            int chestSlots = containerHandler.getRowCount() * 9;
            *//*?} else {*/
            int chestSlots = containerHandler.getRows() * 9;
            /*?}*/
            int playerSlotStart = chestSlots;
            int playerSlotEnd = chestSlots + 36;

            for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
                if (blockedDumpSlots.contains(slot)) continue;
                /*? if >=26.1 {*//*
                ItemStack stack = containerHandler.getSlot(slot).getItem();
                *//*?} else {*/
                ItemStack stack = containerHandler.getSlot(slot).getStack();
                /*?}*/
                if (stack.isEmpty()) continue;
                /*? if >=26.1 {*//*
                if (stack.isDamageableItem()) continue;
                *//*?} else {*/
                if (stack.isDamageable()) continue;
                /*?}*/
                if (!tryPrinterInventory(2)) {
                    return;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        containerHandler.containerId, slot, 0,
                        *//*?} else {*/
                        containerHandler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, mc.player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, mc.player);
                        /*?}*/
                beginPendingContainerAction(containerHandler, slot, ContainerTransferLane.DUMP);
                dumpSyncDelay = 0;
                return;
            }

            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/

            if (statusMessages) {
                ChatHelper.info("§aItems deposited. Resuming clearing.");
            }

            if (preDumpClearPos != null) {
                PathWalker.walkToNearby(preDumpClearPos, (int) Math.ceil(range));
                autoState = AutoState.WALKING_TO_CLEAR;
            } else {
                autoState = AutoState.CLEARING_AREA;
            }
            return;
        }

        // Chest hasn't opened yet — try right-clicking it
        if (dumpWaitTicks == 1 && dumpTarget != null) {
            tryOpenChest(mc, dumpTarget);
        }

        if (dumpWaitTicks >= CHEST_OPEN_TIMEOUT) {
            if (statusMessages) {
                ChatHelper.info("§eDump chest didn't open, resuming clearing.");
            }
            autoState = AutoState.CLEARING_AREA;
        }
    }

    /*? if >=26.1 {*//*
    private void tickDumpingToLocalShulker(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        Level world = mc.level;
    *//*?} else {*/
    private void tickDumpingToLocalShulker(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity player = mc.player;
        World world = mc.world;
    /*?}*/
        dumpWaitTicks++;

        switch (dumpShulkerPhase) {
            case 0 -> {
                int slot = findAnyShulkerInInventory(player);
                if (slot < 0) {
                    resumeAfterDump(mc, false);
                    return;
                }
                BlockPos placePos = findShulkerPlaceSpot(player, world);
                if (placePos == null) {
                    if (statusMessages) {
                        ChatHelper.info("§eNo solid shulker placement spot nearby — resuming clearing.");
                    }
                    resumeAfterDump(mc, false);
                    return;
                }
                dumpShulkerSlot = slot;
                dumpShulkerPos = placePos;
                dumpWaitTicks = 0;
                dumpShulkerPhase = 1;
            }
            case 1 -> {
                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                /*?}*/
                if (dumpWaitTicks == 1) {
                    if (dumpShulkerSlot >= 9) {
                        if (!tryPrinterInventory(2)) {
                            dumpWaitTicks = 0;
                            return;
                        }
                        /*? if >=26.1 {*//*
                        mc.gameMode.handleContainerInput(
                                player.containerMenu.containerId,
                                dumpShulkerSlot,
                                inv.getSelectedSlot(),
                                ContainerInput.SWAP,
                                player
                        );
                        *//*?} else {*/
                        mc.interactionManager.clickSlot(
                                player.currentScreenHandler.syncId,
                                dumpShulkerSlot,
                                /*? if >=1.21.5 {*//*
                                inv.getSelectedSlot(),
                                *//*?} else {*/
                                inv.selectedSlot,
                                /*?}*/
                                SlotActionType.SWAP,
                                player
                        );
                        /*?}*/
                        return;
                    }
                    /*? if >=1.21.5 {*//*
                    inv.setSelectedSlot(dumpShulkerSlot);
                    *//*?} else {*/
                    inv.selectedSlot = dumpShulkerSlot;
                    /*?}*/
                    return;
                }
                if (dumpWaitTicks < 3) return;

                /*? if >=26.1 {*//*
                ItemStack held = inv.getItem(inv.getSelectedSlot());
                Vec3 eyePos = player.getEyePosition();
                Vec3 target = Vec3.atCenterOf(dumpShulkerPos.below()).add(0, 0.5, 0);
                Vec3 toTarget = target.subtract(eyePos);
                float placeYaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
                float placePitch = (float) -(Mth.atan2(toTarget.y,
                        Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)) * (180.0 / Math.PI));
                *//*?} else {*/
                ItemStack held = inv.getStack(
                        /*? if >=1.21.5 {*//* inv.getSelectedSlot() *//*?} else {*/inv.selectedSlot/*?}*/);
                Vec3d eyePos = player.getEyePos();
                Vec3d target = Vec3d.ofCenter(dumpShulkerPos.down()).add(0, 0.5, 0);
                Vec3d toTarget = target.subtract(eyePos);
                float placeYaw = (float) (MathHelper.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
                float placePitch = (float) -(MathHelper.atan2(toTarget.y,
                        Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)) * (180.0 / Math.PI));
                /*?}*/
                if (!isShulkerBox(held)) {
                    dumpShulkerPhase = 0;
                    dumpWaitTicks = 0;
                    return;
                }

                if (!tryPrinterLook(1)) {
                    return;
                }
                PlacementEngine.sendLookPacket(player, placeYaw,
                        /*? if >=26.1 {*//* Mth.clamp(placePitch, -90.0f, 90.0f) *//*?} else {*/MathHelper.clamp(placePitch, -90.0f, 90.0f)/*?}*/);
                if (dumpWaitTicks < 5) return;

                if (!tryPrinterInteraction(4)) {
                    return;
                }
                Runnable restoreSneak = PlacementEngine.ensureSneakForPlacement(player);
                BlockHitResult hit = new BlockHitResult(
                        /*? if >=26.1 {*//* Vec3.atCenterOf(dumpShulkerPos.below()) *//*?} else {*/Vec3d.ofCenter(dumpShulkerPos.down())/*?}*/
                                .add(0, 0.5, 0),
                        Direction.UP,
                        /*? if >=26.1 {*//* dumpShulkerPos.below() *//*?} else {*/dumpShulkerPos.down()/*?}*/,
                        false);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/
                restoreSneak.run();
                dumpShulkerPhase = 2;
                dumpWaitTicks = 0;
            }
            case 2 -> {
                BlockState st = world.getBlockState(dumpShulkerPos);
                if (st.getBlock() instanceof ShulkerBoxBlock) {
                    dumpShulkerPhase = 3;
                    dumpWaitTicks = 0;
                    dumpShulkerOpenRetries = 0;
                    return;
                }
                if (dumpWaitTicks >= SHULKER_PLACE_DELAY) {
                    dumpShulkerPhase = 0;
                    dumpWaitTicks = 0;
                }
            }
            case 3 -> {
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                Vec3 shulkerCenter = Vec3.atCenterOf(dumpShulkerPos);
                Vec3 toShulker = shulkerCenter.subtract(eyePos);
                float openYaw = (float) (Mth.atan2(toShulker.z, toShulker.x) * (180.0 / Math.PI)) - 90.0f;
                float openPitch = (float) -(Mth.atan2(toShulker.y,
                        Math.sqrt(toShulker.x * toShulker.x + toShulker.z * toShulker.z)) * (180.0 / Math.PI));
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                Vec3d shulkerCenter = Vec3d.ofCenter(dumpShulkerPos);
                Vec3d toShulker = shulkerCenter.subtract(eyePos);
                float openYaw = (float) (MathHelper.atan2(toShulker.z, toShulker.x) * (180.0 / Math.PI)) - 90.0f;
                float openPitch = (float) -(MathHelper.atan2(toShulker.y,
                        Math.sqrt(toShulker.x * toShulker.x + toShulker.z * toShulker.z)) * (180.0 / Math.PI));
                /*?}*/
                if (!tryPrinterLook(1)) {
                    return;
                }
                PlacementEngine.sendLookPacket(player, openYaw,
                        /*? if >=26.1 {*//* Mth.clamp(openPitch, -90.0f, 90.0f) *//*?} else {*/MathHelper.clamp(openPitch, -90.0f, 90.0f)/*?}*/);
                if (dumpWaitTicks < 3) return;

                if (!tryPrinterInteraction(4)) {
                    return;
                }
                Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
                /*? if >=26.1 {*//*
                Direction hitFace = Direction.getApproximateNearest(
                *//*?} else {*/
                Direction hitFace = Direction.getFacing(
                /*?}*/
                        (float) -toShulker.x, (float) -toShulker.y, (float) -toShulker.z);
                BlockHitResult hit = new BlockHitResult(
                        shulkerCenter,
                        hitFace,
                        dumpShulkerPos,
                        false);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/
                restoreSneak.run();
                dumpShulkerPhase = 4;
                dumpWaitTicks = 0;
                dumpSyncDelay = 0;
            }
            case 4 -> {
                /*? if >=26.1 {*//*
                AbstractContainerMenu handler = player.containerMenu;
                if (handler instanceof ShulkerBoxMenu shulkerHandler) {
                *//*?} else {*/
                ScreenHandler handler = player.currentScreenHandler;
                if (handler instanceof ShulkerBoxScreenHandler shulkerHandler) {
                /*?}*/
                    int blockedBefore = blockedDumpSlots.size();
                    if (tickPendingContainerAction(shulkerHandler, ContainerTransferLane.DUMP, blockedDumpSlots)) {
                        if (blockedDumpSlots.size() > blockedBefore) {
                            dumpShulkerTransferTimeouts++;
                            if (dumpShulkerTransferTimeouts >= MAX_DUMP_SHULKER_TRANSFER_TIMEOUTS) {
                                /*? if >=26.1 {*//*
                                if (mc.screen != null) player.clientSideCloseContainer();
                                *//*?} else {*/
                                if (mc.currentScreen != null) player.closeHandledScreen();
                                /*?}*/
                                if (statusMessages) {
                                    ChatHelper.info("§eOverflow shulker transfer stalled — aborting dump and resuming clearing.");
                                }
                                resumeAfterDump(mc, true);
                            }
                        }
                        return;
                    }
                    dumpSyncDelay++;
                    if (dumpSyncDelay < CHEST_SYNC_DELAY) return;

                    int playerSlotStart = 27;
                    int playerSlotEnd = 63;
                    Map<String, Integer> inventoryCounts = new HashMap<>();
                    for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
                        if (blockedDumpSlots.contains(slot)) continue;
                        /*? if >=26.1 {*//*
                        ItemStack stack = shulkerHandler.getSlot(slot).getItem();
                        *//*?} else {*/
                        ItemStack stack = shulkerHandler.getSlot(slot).getStack();
                        /*?}*/
                        if (stack.isEmpty()) continue;
                        inventoryCounts.merge(ItemIdentifier.getItemId(stack), stack.getCount(), Integer::sum);
                    }

                    boolean movedAnything = false;
                    for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
                        if (blockedDumpSlots.contains(slot)) continue;
                        /*? if >=26.1 {*//*
                        ItemStack stack = shulkerHandler.getSlot(slot).getItem();
                        *//*?} else {*/
                        ItemStack stack = shulkerHandler.getSlot(slot).getStack();
                        /*?}*/
                        if (!shouldDumpOverflowStack(stack, inventoryCounts)) continue;
                        if (!tryPrinterInventory(2)) {
                            return;
                        }
                        /*? if >=26.1 {*//*
                        mc.gameMode.handleContainerInput(
                                shulkerHandler.containerId, slot, 0,
                                ContainerInput.QUICK_MOVE, player);
                        *//*?} else {*/
                        mc.interactionManager.clickSlot(
                                shulkerHandler.syncId, slot, 0,
                                SlotActionType.QUICK_MOVE, player);
                        /*?}*/
                        inventoryCounts.merge(ItemIdentifier.getItemId(stack), -stack.getCount(), Integer::sum);
                        beginPendingContainerAction(shulkerHandler, slot, ContainerTransferLane.DUMP);
                        dumpSyncDelay = 0;
                        dumpShulkerTransferTimeouts = 0;
                        return;
                    }

                    snapshotDumpShulkerSlots(player);
                    /*? if >=26.1 {*//*
                    player.clientSideCloseContainer();
                    *//*?} else {*/
                    player.closeHandledScreen();
                    /*?}*/
                    dumpShulkerPhase = 5;
                    dumpWaitTicks = 0;
                    if (!movedAnything && statusMessages) {
                        ChatHelper.info("§eOverflow shulker had no dumpable stacks to take.");
                    }
                    return;
                }

                if (dumpWaitTicks >= MAX_SHULKER_PHASE_TICKS) {
                    /*? if >=26.1 {*//*
                    if (mc.screen != null) player.clientSideCloseContainer();
                    *//*?} else {*/
                    if (mc.currentScreen != null) player.closeHandledScreen();
                    /*?}*/
                    if (dumpShulkerOpenRetries++ < MAX_DUMP_SHULKER_OPEN_RETRIES) {
                        dumpShulkerPhase = 3;
                        dumpWaitTicks = 0;
                    } else {
                        dumpShulkerPhase = 5;
                        dumpWaitTicks = 0;
                    }
                }
            }
            case 5 -> {
                /*? if >=26.1 {*//*
                if (mc.screen != null) {
                    player.clientSideCloseContainer();
                    return;
                }
                *//*?} else {*/
                if (mc.currentScreen != null) {
                    player.closeHandledScreen();
                    return;
                }
                /*?}*/
                BlockState st = world.getBlockState(dumpShulkerPos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    dumpShulkerPhase = 7;
                    dumpWaitTicks = 0;
                    return;
                }
                if (!tryPrinterMining(1)) {
                    return;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(dumpShulkerPos, Direction.UP);
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(dumpShulkerPos, Direction.UP);
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
                dumpShulkerPhase = 6;
                dumpWaitTicks = 0;
            }
            case 6 -> {
                BlockState st = world.getBlockState(dumpShulkerPos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    /*? if >=26.1 {*//* mc.gameMode.stopDestroyBlock(); *//*?} else {*/mc.interactionManager.cancelBlockBreaking();/*?}*/
                    dumpShulkerPhase = 7;
                    dumpWaitTicks = 0;
                    return;
                }
                if (dumpWaitTicks >= MAX_SHULKER_PHASE_TICKS) {
                    /*? if >=26.1 {*//* mc.gameMode.stopDestroyBlock(); *//*?} else {*/mc.interactionManager.cancelBlockBreaking();/*?}*/
                    resumeAfterDump(mc, true);
                    return;
                }
                if (!tryPrinterMining(1)) {
                    return;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(dumpShulkerPos, Direction.UP);
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(dumpShulkerPos, Direction.UP);
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
            }
            case 7 -> {
                int recoveredSlot = findNewDumpShulkerSlot(player);
                if (recoveredSlot >= 0 || dumpWaitTicks >= SHULKER_PICKUP_DELAY + 40) {
                    resumeAfterDump(mc, dumpWaitTicks >= SHULKER_PICKUP_DELAY + 40 && recoveredSlot < 0);
                }
            }
            default -> resumeAfterDump(mc, false);
        }
    }

    // SHULKER UNLOADING — place → open → take items → break → pickup

    // Finds the first inventory shulker containing needed items, or -1.
    /*? if >=26.1 {*//*
    private int findShulkerWithNeededItems(LocalPlayer player) {
    *//*?} else {*/
    private int findShulkerWithNeededItems(ClientPlayerEntity player) {
    /*?}*/
        if (neededItems == null || neededItems.isEmpty()) return -1;
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (isShulkerBox(stack) && shulkerContainsNeeded(stack, neededItems)) {
                return i;
            }
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private int countShulkersWithNeededItems(LocalPlayer player) {
    *//*?} else {*/
    private int countShulkersWithNeededItems(ClientPlayerEntity player) {
    /*?}*/
        if (player == null || neededItems == null || neededItems.isEmpty()) return 0;
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/
        int count = 0;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (isShulkerBox(stack) && shulkerContainsNeeded(stack, neededItems)) {
                count++;
            }
        }
        return count;
    }

    /*? if >=26.1 {*//*
    private int findAnyShulkerInInventory(LocalPlayer player) {
    *//*?} else {*/
    private int findAnyShulkerInInventory(ClientPlayerEntity player) {
    /*?}*/
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (isShulkerBox(stack)) return i;
        }
        return -1;
    }

    // Finds a valid nearby position to place a shulker box.
    /*? if >=26.1 {*//*
    private BlockPos findShulkerPlaceSpot(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findShulkerPlaceSpot(ClientPlayerEntity player, World world) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerFeet = player.blockPosition();
        *//*?} else {*/
        BlockPos playerFeet = player.getBlockPos();
        /*?}*/
        // Skip positions overlapping the player bounding box (0.6×1.8).
        // Prefer non-interactive support blocks to avoid chest/barrel GUI.
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        boolean bestIsInteractive = true;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = playerFeet.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = playerFeet.add(dx, dy, dz);
                    /*?}*/
                    // Skip: player collision
                    if (px - 0.3 < pos.getX() + 1 && px + 0.3 > pos.getX()
                            && py < pos.getY() + 1 && py + 1.8 > pos.getY()
                            && pz - 0.3 < pos.getZ() + 1 && pz + 0.3 > pos.getZ()) {
                        continue;
                    }
                    BlockState state = world.getBlockState(pos);
                    /*? if >=26.1 {*//*
                    BlockState below = world.getBlockState(pos.below());
                    *//*?} else {*/
                    BlockState below = world.getBlockState(pos.down());
                    /*?}*/
                    // Need air/replaceable at the position, solid below,
                    // and air above so the shulker lid can open.
                    /*? if >=26.1 {*//*
                    BlockState above = world.getBlockState(pos.above());
                    *//*?} else {*/
                    BlockState above = world.getBlockState(pos.up());
                    /*?}*/
                    /*? if >=26.1 {*//*
                    if ((state.isAir() || state.canBeReplaced())
                    *//*?} else {*/
                    if ((state.isAir() || state.isReplaceable())
                    /*?}*/
                            /*? if >=26.1 {*//*
                            && below.isFaceSturdy(world, pos.below(), Direction.UP)
                            *//*?} else {*/
                            && below.isSideSolidFullSquare(world, pos.down(), Direction.UP)
                            /*?}*/
                            /*? if >=26.1 {*//*
                            && (above.isAir() || above.canBeReplaced())) {
                            *//*?} else {*/
                            && (above.isAir() || above.isReplaceable())) {
                            /*?}*/
                        /*? if >=26.1 {*//*
                        double dist = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                        *//*?} else {*/
                        double dist = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                        /*?}*/
                        if (dist > 4.5 * 4.5) continue;
                        boolean interactive = PlacementEngine.isInteractive(below.getBlock());
                        // Prefer non-interactive support, then nearest
                        if (bestIsInteractive && !interactive) {
                            // non-interactive wins
                            best = pos;
                            bestDist = dist;
                            bestIsInteractive = false;
                        } else if (interactive == bestIsInteractive && dist < bestDist) {
                            best = pos;
                            bestDist = dist;
                            bestIsInteractive = interactive;
                        }
                    }
                }
            }
        }
        return best;
    }

    private Set<BlockPos> getProtectedStoragePositions() {
        Set<BlockPos> out = new HashSet<>();
        out.addAll(MoarMod.getChestManager().getSupplyPositions());
        out.addAll(MoarMod.getChestManager().getDumpPositions());
        out.addAll(MoarMod.getChestManager().getStorageChests());
        return out;
    }

    /*? if >=26.1 {*//*
    private boolean isStorageBlacklistedForClearing(BlockState state, BlockPos pos, Set<BlockPos> protectedStorage) {
    *//*?} else {*/
    private boolean isStorageBlacklistedForClearing(BlockState state, BlockPos pos, Set<BlockPos> protectedStorage) {
    /*?}*/
        if (protectedStorage.contains(pos)) return true;
        Block b = state.getBlock();
        if (isLightSourceProtectedForClearing(b)) return true;
        return b instanceof AbstractChestBlock
                || b instanceof BarrelBlock
                || b instanceof ShulkerBoxBlock
                || b instanceof HopperBlock;
    }

    private static boolean isLightSourceProtectedForClearing(Block block) {
        return block instanceof TorchBlock
                || block instanceof WallTorchBlock
                || block instanceof LanternBlock
                || block == Blocks.GLOWSTONE
                || block == Blocks.SEA_LANTERN
                || block == Blocks.SHROOMLIGHT
                || block == Blocks.JACK_O_LANTERN
                || block == Blocks.REDSTONE_LAMP
                || block == Blocks.OCHRE_FROGLIGHT
                || block == Blocks.VERDANT_FROGLIGHT
                || block == Blocks.PEARLESCENT_FROGLIGHT
                || block == Blocks.END_ROD;
    }

    // Places a solid block adjacent to the player to create a shulker platform.
    // Used when findShulkerPlaceSpot returns null (narrow pillar/ledge).
    /*? if >=26.1 {*//*
    private boolean tryBuildShulkerPlatform(LocalPlayer player,
    *//*?} else {*/
    private boolean tryBuildShulkerPlatform(ClientPlayerEntity player,
    /*?}*/
                                            /*? if >=26.1 {*//*
                                            Level world,
                                            *//*?} else {*/
                                            World world,
                                            /*?}*/
                                            /*? if >=26.1 {*//*
                                            Minecraft mc) {
                                            *//*?} else {*/
                                            MinecraftClient mc) {
                                            /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerFeet = player.blockPosition();
        *//*?} else {*/
        BlockPos playerFeet = player.getBlockPos();
        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos standingOn = playerFeet.below();
        *//*?} else {*/
        BlockPos standingOn = playerFeet.down();
        /*?}*/
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/

        // Find a solid block in inventory to use as platform
        // Prefer cheap/common blocks.  Avoid shulker boxes themselves.
        int blockSlot = -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            if (isShulkerBox(stack)) continue;
            Block block = bi.getBlock();
            // Must be a full solid block (not a torch, slab, etc.)
            /*? if >=26.1 {*//*
            if (block.defaultBlockState().isCollisionShapeFullBlock(world, BlockPos.ZERO)) {
            *//*?} else {*/
            if (block.getDefaultState().isFullCube(world, BlockPos.ORIGIN)) {
            /*?}*/
                blockSlot = i;
                break;
            }
        }
        if (blockSlot < 0) return false; // no suitable block

        // Find a placement position
        // Search at multiple Y levels around the player, not just
        // standingOn's Y.  Also search adjacent to any previously
        // placed platform block so multi-attempt extensions work.
        // The placed block creates a surface for the shulker.
        // We prefer positions where an air block exists above the
        // candidate so the shulker can actually be placed there.
        BlockPos placeTarget = null;
        Direction placeFace = null;
        double bestDist = Double.MAX_VALUE;
        // Score: 0 = normal, -1 = has air above (preferred for shulker)
        int bestScore = Integer.MAX_VALUE;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos candidate = standingOn.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos candidate = standingOn.add(dx, dy, dz);
                    /*?}*/
                    // Don't place where the player is or directly below
                    if (candidate.equals(playerFeet) || candidate.equals(standingOn)
                            /*? if >=26.1 {*//*
                            || candidate.equals(playerFeet.above())) continue;
                            *//*?} else {*/
                            || candidate.equals(playerFeet.up())) continue;
                            /*?}*/
                    BlockState candidateState = world.getBlockState(candidate);
                    /*? if >=26.1 {*//*
                    if (!candidateState.isAir() && !candidateState.canBeReplaced()) continue;
                    *//*?} else {*/
                    if (!candidateState.isAir() && !candidateState.isReplaceable()) continue;
                    /*?}*/

                    // Determine if this position would yield a shulker spot:
                    // needs air above for the shulker box to be placed on top
                    /*? if >=26.1 {*//*
                    BlockState aboveState = world.getBlockState(candidate.above());
                    *//*?} else {*/
                    BlockState aboveState = world.getBlockState(candidate.up());
                    /*?}*/
                    /*? if >=26.1 {*//*
                    int score = (aboveState.isAir() || aboveState.canBeReplaced()) ? -1 : 0;
                    *//*?} else {*/
                    int score = (aboveState.isAir() || aboveState.isReplaceable()) ? -1 : 0;
                    /*?}*/

                    // Check if any face of this air block has a solid neighbor
                    // we can place against (including the previously placed
                    // platform block, if any)
                    for (Direction dir : Direction.values()) {
                        /*? if >=26.1 {*//*
                        BlockPos neighbor = candidate.relative(dir);
                        *//*?} else {*/
                        BlockPos neighbor = candidate.offset(dir);
                        /*?}*/
                        BlockState neighborState = world.getBlockState(neighbor);
                        /*? if >=26.1 {*//*
                        if (!neighborState.isAir() && !neighborState.canBeReplaced()
                        *//*?} else {*/
                        if (!neighborState.isAir() && !neighborState.isReplaceable()
                        /*?}*/
                                && !neighborState.getCollisionShape(world, neighbor).isEmpty()) {
                            /*? if >=26.1 {*//*
                            double dist = player.getEyePosition().distanceToSqr(
                            *//*?} else {*/
                            double dist = player.getEyePos().squaredDistanceTo(
                            /*?}*/
                                    /*? if >=26.1 {*//*
                                    Vec3.atCenterOf(candidate));
                                    *//*?} else {*/
                                    Vec3d.ofCenter(candidate));
                                    /*?}*/
                            if (dist <= 4.5 * 4.5
                                    && (score < bestScore
                                        || (score == bestScore && dist < bestDist))) {
                                bestScore = score;
                                bestDist = dist;
                                placeTarget = candidate;
                                // We place against the neighbor, clicking the
                                // face that faces toward the candidate
                                placeFace = dir.getOpposite();
                            }
                            break;
                        }
                    }
                }
            }
        }
        if (placeTarget == null || placeFace == null) return false;

        // Rotate toward the target before placing
        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos clickBlock = placeTarget.relative(placeFace.getOpposite());
        *//*?} else {*/
        BlockPos clickBlock = placeTarget.offset(placeFace.getOpposite());
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 clickCenter = Vec3.atCenterOf(clickBlock);
        *//*?} else {*/
        Vec3d clickCenter = Vec3d.ofCenter(clickBlock);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toClick = clickCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toClick = clickCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toClick.x * toClick.x + toClick.z * toClick.z);
        /*? if >=26.1 {*//*
        float platYaw = (float) (Mth.atan2(toClick.z, toClick.x)
        *//*?} else {*/
        float platYaw = (float) (MathHelper.atan2(toClick.z, toClick.x)
        /*?}*/
                * (180.0 / Math.PI)) - 90.0f;
        /*? if >=26.1 {*//*
        float platPitch = (float) -(Mth.atan2(toClick.y, horizDist)
        *//*?} else {*/
        float platPitch = (float) -(MathHelper.atan2(toClick.y, horizDist)
        /*?}*/
                * (180.0 / Math.PI));
        if (!tryPrinterLook(1)) {
            return false;
        }
        PlacementEngine.sendLookPacket(player, platYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(platPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(platPitch, -90.0f, 90.0f));
                /*?}*/

        // Swap the block into the current hotbar slot
        /*? if >=1.21.5 {*//*
        int currentSlot = inv.getSelectedSlot();
        *//*?} else {*/
        int currentSlot = inv.selectedSlot;
        /*?}*/
        if (blockSlot >= 9) {
            // Main inventory → swap into current hotbar slot
            if (!tryPrinterInventory(2)) {
                return false;
            }
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
            *//*?} else {*/
            mc.interactionManager.clickSlot(
            /*?}*/
                    /*? if >=26.1 {*//*
                    player.containerMenu.containerId,
                    *//*?} else {*/
                    player.currentScreenHandler.syncId,
                    /*?}*/
                    blockSlot, currentSlot,
                    /*? if >=26.1 {*//*
                    ContainerInput.SWAP, player);
                    *//*?} else {*/
                    SlotActionType.SWAP, player);
                    /*?}*/
            return false;
        } else if (blockSlot != currentSlot) {
            /*? if >=1.21.5 {*//*
            inv.setSelectedSlot(blockSlot);
            *//*?} else {*/
            inv.selectedSlot = blockSlot;
            /*?}*/
        }

        // Place the block
        Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

        BlockHitResult hit = new BlockHitResult(
                clickCenter,
                placeFace,
                clickBlock,
                false);
        if (!tryPrinterInteraction(4)) {
            restoreSneak.run();
            return false;
        }
        /*? if >=26.1 {*//*
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        *//*?} else {*/
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        /*?}*/

        restoreSneak.run();

        platformBlockPos = placeTarget;

        // Track as scaffold for cleanup
        /*? if >=26.1 {*//*
        ItemStack placedStack = inv.getItem(blockSlot >= 9 ? currentSlot : blockSlot);
        *//*?} else {*/
        ItemStack placedStack = inv.getStack(blockSlot >= 9 ? currentSlot : blockSlot);
        /*?}*/
        if (!placedStack.isEmpty()) {
            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(placedStack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(placedStack.getItem()).toString();
            /*?}*/
            PrinterDatabase.addScaffold(placeTarget, itemId);
        }

        LOGGER.debug("Built platform block for shulker placement");
        return true;
    }

    // State machine for unloading shulker boxes grabbed from the supply
    // chest.  Phases:
    //   0 — find next shulker with needed items in inventory
    //   1 — select/swap shulker into hotbar (wait 1 tick for server)
    //   2 — place the shulker on the ground
    //   3 — wait for server to register the placed shulker block
    //   4 — open the shulker (interact with it)
    //   5 — take needed items from the open shulker screen
    //   6 — close screen, start breaking the shulker
    //   7 — continue breaking until it drops
    //   8 — wait for the item entity to be picked up
    /*? if >=26.1 {*//*
    private void tickUnloadingShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickUnloadingShulker(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        /*?}*/
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        shulkerUnloadTicks++;
        shulkerTotalTicks++;

        // Global safety timeout — prevents unbreakable loops
        if (shulkerTotalTicks >= MAX_SHULKER_TOTAL_TICKS) {
            if (statusMessages) {
                ChatHelper.info("§c⚠ Shulker unloading timed out — aborting.");
            }
            // Clean up: close any open screen, cancel breaking
            /*? if >=26.1 {*//*
            if (mc.screen != null) player.clientSideCloseContainer();
            *//*?} else {*/
            if (mc.currentScreen != null) player.closeHandledScreen();
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            // Prevent immediate re-entry into shulker unloading at
            // the same position that just timed out.
            shulkerNoSpaceSkipped = true;
            finishShulkerUnloading(mc);
            return;
        }

        switch (shulkerUnloadPhase) {

            // Phase 0: Find a shulker in inventory
            case 0 -> {
                if (shulkerUnloadFailures >= MAX_SHULKER_FAILURES) {
                    if (statusMessages) {
                        ChatHelper.info("§eShulker unloading failed too many times — skipping.");
                    }
                    // Mark as no-space so the system doesn't immediately
                    // re-detect the same shulkers and loop at this position.
                    shulkerNoSpaceSkipped = true;
                    finishShulkerUnloading(mc);
                    return;
                }
                int slot = findShulkerWithNeededItems(player);
                if (slot < 0) {
                    // No more shulkers to unload — proceed to walk back
                    finishShulkerUnloading(mc);
                    return;
                }
                shulkerMovedNeededItems = false;
                shulkerHotbarSlot = slot;
                shulkerPlacePos = findShulkerPlaceSpot(player, world);
                if (shulkerPlacePos == null) {
                    // No placement spot — try building platform blocks.
                    // We allow up to MAX_PLATFORM_ATTEMPTS tries so that
                    // multi-block extensions can create enough surface.
                    if (platformBuildAttempts < MAX_PLATFORM_ATTEMPTS) {
                        platformBuildAttempts++;
                        if (tryBuildShulkerPlatform(player, world, mc)) {
                            // Block placed — wait a few ticks for server
                            // to register, then retry spot search.
                            shulkerUnloadTicks = 0;
                            return; // stay in phase 0
                        }
                    }
                    // We tried building a platform — wait for it to register
                    if (platformBlockPos != null && shulkerUnloadTicks < 8) {
                        return; // still waiting
                    }
                    // Retry spot search after platform wait
                    shulkerPlacePos = findShulkerPlaceSpot(player, world);
                    if (shulkerPlacePos == null) {
                        shulkerNoSpaceSkipped = true;
                        if (statusMessages) {
                            ChatHelper.info("§eNo space to place shulker — will restock from chest instead.");
                        }
                        finishShulkerUnloading(mc);
                        return;
                    }
                }
                /*? if >=26.1 {*//*
                shulkerSavedYaw = player.getYRot();
                *//*?} else {*/
                shulkerSavedYaw = player.getYaw();
                /*?}*/
                /*? if >=26.1 {*//*
                shulkerSavedPitch = player.getXRot();
                *//*?} else {*/
                shulkerSavedPitch = player.getPitch();
                /*?}*/
                shulkerUnloadPhase = 1;
                shulkerUnloadTicks = 0;
            }

            // Phase 1: Select/swap shulker into hotbar
            //    Must be a separate tick from placement so the server
            //    processes the slot change before we try to use it.
            case 1 -> {
                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                /*?}*/
                if (shulkerHotbarSlot >= 9) {
                    // Swap from main inventory to current hotbar slot
                    if (!tryPrinterInventory(2)) {
                        return;
                    }
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                    /*?}*/
                            /*? if >=26.1 {*//*
                            player.containerMenu.containerId,
                            *//*?} else {*/
                            player.currentScreenHandler.syncId,
                            /*?}*/
                            shulkerHotbarSlot,
                            /*? if >=1.21.5 {*//*
                            inv.getSelectedSlot(),
                            *//*?} else {*/
                            inv.selectedSlot,
                            /*?}*/
                            /*? if >=26.1 {*//*
                            ContainerInput.SWAP,
                            *//*?} else {*/
                            SlotActionType.SWAP,
                            /*?}*/
                            player
                    );
                } else {
                    /*? if >=1.21.5 {*//*
                    inv.setSelectedSlot(shulkerHotbarSlot);
                    *//*?} else {*/
                    inv.selectedSlot = shulkerHotbarSlot;
                    /*?}*/
                }
                // Wait 2 ticks for server to process the slot swap
                shulkerUnloadPhase = 2;
                shulkerUnloadTicks = 0;
            }

            // Phase 2: Place the shulker on the ground
            case 2 -> {
                // Wait at least 2 ticks after swap for server sync
                if (shulkerUnloadTicks < 2) return;

                // Verify we're actually holding a shulker box
                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                /*?}*/
                /*? if >=26.1 {*//*
                ItemStack held = inv.getItem(inv.getSelectedSlot());
                *//*?} else if >=1.21.5 {*//*
                ItemStack held = inv.getStack(inv.getSelectedSlot());
                *//*?} else {*/
                ItemStack held = inv.getStack(inv.selectedSlot);
                /*?}*/
                if (!isShulkerBox(held)) {
                    // Swap didn't work — count as failure
                    shulkerUnloadFailures++;
                    LOGGER.debug("Shulker not in hand after swap — retrying");
                    shulkerUnloadPhase = 0;
                    shulkerUnloadTicks = 0;
                    return;
                }

                // Rotate toward the placement target before placing so
                // servers with anti-cheat accept the interaction packet.
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 target = Vec3.atCenterOf(shulkerPlacePos.below())
                *//*?} else {*/
                Vec3d target = Vec3d.ofCenter(shulkerPlacePos.down())
                /*?}*/
                        .add(0, 0.5, 0); // top face of the support block
                /*? if >=26.1 {*//*
                Vec3 toTarget = target.subtract(eyePos);
                *//*?} else {*/
                Vec3d toTarget = target.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toTarget.x * toTarget.x
                        + toTarget.z * toTarget.z);
                /*? if >=26.1 {*//*
                float placeYaw = (float) (Mth.atan2(toTarget.z, toTarget.x)
                *//*?} else {*/
                float placeYaw = (float) (MathHelper.atan2(toTarget.z, toTarget.x)
                /*?}*/
                        * (180.0 / Math.PI)) - 90.0f;
                /*? if >=26.1 {*//*
                float placePitch = (float) -(Mth.atan2(toTarget.y, horizDist)
                *//*?} else {*/
                float placePitch = (float) -(MathHelper.atan2(toTarget.y, horizDist)
                /*?}*/
                        * (180.0 / Math.PI));
                if (!tryPrinterLook(1)) {
                    return;
                }
                PlacementEngine.sendLookPacket(player, placeYaw,
                        /*? if >=26.1 {*//*
                        Mth.clamp(placePitch, -90.0f, 90.0f));
                        *//*?} else {*/
                        MathHelper.clamp(placePitch, -90.0f, 90.0f));
                        /*?}*/

                // Wait for server to process look rotation.
                if (shulkerUnloadTicks < 4) return;

                // Sneak to place on interactive blocks (chests, barrels…)
                Runnable restoreSneak = PlacementEngine.ensureSneakForPlacement(player);

                // Place the shulker on top of the block below the target
                BlockHitResult hit = new BlockHitResult(
                        /*? if >=26.1 {*//*
                        Vec3.atCenterOf(shulkerPlacePos.below())
                        *//*?} else {*/
                        Vec3d.ofCenter(shulkerPlacePos.down())
                        /*?}*/
                                .add(0, 0.5, 0), // hit the top surface
                        Direction.UP,
                        /*? if >=26.1 {*//*
                        shulkerPlacePos.below(),
                        *//*?} else {*/
                        shulkerPlacePos.down(),
                        /*?}*/
                        false);
                if (!tryPrinterInteraction(4)) {
                    restoreSneak.run();
                    return;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/

                restoreSneak.run();

                shulkerUnloadPhase = 3;
                shulkerUnloadTicks = 0;
            }

            // Phase 3: Wait for placement to register
            case 3 -> {
                BlockState st = world.getBlockState(shulkerPlacePos);
                if (st.getBlock() instanceof ShulkerBoxBlock) {
                    // Placed successfully — reset failure counter
                    shulkerUnloadFailures = 0;
                    shulkerUnloadPhase = 4;
                    shulkerUnloadTicks = 0;
                    return;
                }
                if (shulkerUnloadTicks >= SHULKER_PLACE_DELAY) {
                    shulkerUnloadFailures++;
                    LOGGER.debug("Shulker placement failed (attempt {}/{})",
                            shulkerUnloadFailures, MAX_SHULKER_FAILURES);
                    shulkerUnloadPhase = 0;
                    shulkerUnloadTicks = 0;
                }
            }

            // Phase 4: Open the placed shulker
            case 4 -> {
                // Rotate to look at the shulker before interacting so the
                // server's line-of-sight / facing checks accept the request.
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 shulkerCenter = Vec3.atCenterOf(shulkerPlacePos);
                *//*?} else {*/
                Vec3d shulkerCenter = Vec3d.ofCenter(shulkerPlacePos);
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 toShulker = shulkerCenter.subtract(eyePos);
                *//*?} else {*/
                Vec3d toShulker = shulkerCenter.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toShulker.x * toShulker.x
                        + toShulker.z * toShulker.z);
                /*? if >=26.1 {*//*
                float openYaw = (float) (Mth.atan2(toShulker.z, toShulker.x)
                *//*?} else {*/
                float openYaw = (float) (MathHelper.atan2(toShulker.z, toShulker.x)
                /*?}*/
                        * (180.0 / Math.PI)) - 90.0f;
                /*? if >=26.1 {*//*
                float openPitch = (float) -(Mth.atan2(toShulker.y, horizDist)
                *//*?} else {*/
                float openPitch = (float) -(MathHelper.atan2(toShulker.y, horizDist)
                /*?}*/
                        * (180.0 / Math.PI));
                if (!tryPrinterLook(1)) {
                    return;
                }
                PlacementEngine.sendLookPacket(player, openYaw,
                        /*? if >=26.1 {*//*
                        Mth.clamp(openPitch, -90.0f, 90.0f));
                        *//*?} else {*/
                        MathHelper.clamp(openPitch, -90.0f, 90.0f));
                        /*?}*/

                // Wait for rotation to propagate to the server
                if (shulkerUnloadTicks < 3) return;

                Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

                // Use the face facing the player for a more natural hit
                /*? if >=26.1 {*//*
                Direction hitFace = Direction.getApproximateNearest(
                *//*?} else {*/
                Direction hitFace = Direction.getFacing(
                /*?}*/
                        (float) -toShulker.x, (float) -toShulker.y, (float) -toShulker.z);
                BlockHitResult hit = new BlockHitResult(
                        shulkerCenter,
                        hitFace,
                        shulkerPlacePos,
                        false);
                if (!tryPrinterInteraction(4)) {
                    restoreSneak.run();
                    return;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/

                restoreSneak.run();

                shulkerUnloadPhase = 5;
                shulkerUnloadTicks = 0;
                shulkerSyncDelay = 0;
            }

            // Phase 5: Take needed items from the shulker screen
            case 5 -> {
                /*? if >=26.1 {*//*
                AbstractContainerMenu handler = player.containerMenu;
                *//*?} else {*/
                ScreenHandler handler = player.currentScreenHandler;
                /*?}*/
                // Shulker boxes use ShulkerBoxScreenHandler, NOT GenericContainerScreenHandler
                /*? if >=26.1 {*//*
                if (handler instanceof ShulkerBoxMenu shulkerHandler) {
                *//*?} else {*/
                if (handler instanceof ShulkerBoxScreenHandler shulkerHandler) {
                /*?}*/
                    if (tickPendingContainerAction(shulkerHandler, ContainerTransferLane.SHULKER, blockedShulkerSlots)) {
                        if (consumeCompletedContainerAction(ContainerTransferLane.SHULKER)) {
                            shulkerMovedNeededItems = true;
                        }
                        return;
                    }
                    // Wait for server sync
                    shulkerSyncDelay++;
                    if (shulkerSyncDelay < CHEST_SYNC_DELAY) return;

                    // Count free inventory slots — reserve 1 for the broken
                    // shulker item so it can be picked up after breaking.
                    /*? if >=26.1 {*//*
                    Inventory inv = player.getInventory();
                    *//*?} else {*/
                    PlayerInventory inv = player.getInventory();
                    /*?}*/
                    int freeSlots = 0;
                    for (int i = 0; i < 36; i++) {
                        /*? if >=26.1 {*//*
                        if (inv.getItem(i).isEmpty()) freeSlots++;
                        *//*?} else {*/
                        if (inv.getStack(i).isEmpty()) freeSlots++;
                        /*?}*/
                    }
                    Map<String, Integer> remainingNeed = new LinkedHashMap<>(neededItemCounts);
                    for (int i = 0; i < 36; i++) {
                        /*? if >=26.1 {*//*
                        ItemStack invStack = inv.getItem(i);
                        *//*?} else {*/
                        ItemStack invStack = inv.getStack(i);
                        /*?}*/
                        if (invStack.isEmpty()) continue;
                        String invItemId = ItemIdentifier.getItemId(invStack);
                        if (!remainingNeed.containsKey(invItemId)) continue;
                        remainingNeed.put(invItemId, Math.max(0,
                                remainingNeed.getOrDefault(invItemId, 0) - invStack.getCount()));
                    }
                    // We need at least 1 slot free for the broken shulker.
                    // QUICK_MOVE stacks with existing items first, so only
                    // count items that would consume a NEW slot.
                    int slotsReserved = 1; // for the shulker item itself

                    boolean sawNeededItem = false;
                    boolean blockedBySpace = false;

                    // Take needed items — shulker boxes always have 27 slots (3×9)
                    for (int slot = 0; slot < 27; slot++) {
                        if (blockedShulkerSlots.contains(slot)) continue;
                        /*? if >=26.1 {*//*
                        ItemStack stack = shulkerHandler.getSlot(slot).getItem();
                        *//*?} else {*/
                        ItemStack stack = shulkerHandler.getSlot(slot).getStack();
                        /*?}*/
                        if (stack.isEmpty()) continue;
                        /*? if >=26.1 {*//*
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        *//*?} else {*/
                        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                        /*?}*/
                        int shortage = remainingNeed.getOrDefault(itemId, 0);
                        if (shortage > 0) {
                            sawNeededItem = true;
                            // Check if this item would stack with something
                            // already in the player's inventory.
                            boolean wouldStack = false;
                            for (int pi = 0; pi < 36; pi++) {
                                /*? if >=26.1 {*//*
                                ItemStack piStack = inv.getItem(pi);
                                *//*?} else {*/
                                ItemStack piStack = inv.getStack(pi);
                                /*?}*/
                                if (!piStack.isEmpty()
                                        /*? if >=26.1 {*//*
                                        && ItemStack.isSameItem(piStack, stack)
                                        *//*?} else {*/
                                        && ItemStack.areItemsEqual(piStack, stack)
                                        /*?}*/
                                        /*? if >=26.1 {*//*
                                        && piStack.getCount() < piStack.getMaxStackSize()) {
                                        *//*?} else {*/
                                        && piStack.getCount() < piStack.getMaxCount()) {
                                        /*?}*/
                                    wouldStack = true;
                                    break;
                                }
                            }
                            if (!wouldStack) {
                                // Would consume a new slot
                                if (freeSlots <= slotsReserved) {
                                    // Not enough room — stop taking items
                                    blockedBySpace = true;
                                    break;
                                }
                                freeSlots--;
                            }
                            if (!tryPrinterInventory(2)) {
                                return;
                            }
                            /*? if >=26.1 {*//*
                            mc.gameMode.handleContainerInput(
                            *//*?} else {*/
                            mc.interactionManager.clickSlot(
                            /*?}*/
                                    /*? if >=26.1 {*//*
                                    shulkerHandler.containerId, slot, 0,
                                    *//*?} else {*/
                                    shulkerHandler.syncId, slot, 0,
                                    /*?}*/
                                    /*? if >=26.1 {*//*
                                    ContainerInput.QUICK_MOVE, player);
                                    *//*?} else {*/
                                    SlotActionType.QUICK_MOVE, player);
                                    /*?}*/
                            remainingNeed.put(itemId, Math.max(0, shortage - stack.getCount()));
                            beginPendingContainerAction(shulkerHandler, slot, ContainerTransferLane.SHULKER);
                            shulkerSyncDelay = 0;
                            return;
                        }
                    }

                    if (!shulkerMovedNeededItems && sawNeededItem) {
                        shulkerNoSpaceSkipped = true;
                        PacketTelemetry.mark("restock shulker no-transfer"
                                + " slot=" + shulkerHotbarSlot
                                + " blockedBySpace=" + blockedBySpace
                                + " blockedSlots=" + blockedShulkerSlots.size());
                    }

                    // Close the screen
                    /*? if >=26.1 {*//*
                    player.clientSideCloseContainer();
                    *//*?} else {*/
                    player.closeHandledScreen();
                    /*?}*/
                    shulkerOpenRetries = 0;
                    shulkerUnloadPhase = 6;
                    shulkerUnloadTicks = 0;
                    return;
                }

                // Screen not open yet — wait, then retry opening
                if (shulkerUnloadTicks >= MAX_SHULKER_PHASE_TICKS) {
                    /*? if >=26.1 {*//*
                    if (mc.screen != null) player.clientSideCloseContainer();
                    *//*?} else {*/
                    if (mc.currentScreen != null) player.closeHandledScreen();
                    /*?}*/
                    if (shulkerOpenRetries < MAX_SHULKER_OPEN_RETRIES) {
                        shulkerOpenRetries++;
                        LOGGER.debug("Shulker screen didn't open (retry {}/{})",
                                shulkerOpenRetries, MAX_SHULKER_OPEN_RETRIES);
                        // Go back to phase 4 to retry the open interaction
                        shulkerUnloadPhase = 4;
                        shulkerUnloadTicks = 0;
                    } else {
                        // Exhausted retries — break it and move on
                        shulkerUnloadFailures++;
                        shulkerOpenRetries = 0;
                        LOGGER.debug("Shulker screen didn't open after {} retries — breaking it",
                                MAX_SHULKER_OPEN_RETRIES);
                        shulkerUnloadPhase = 6;
                        shulkerUnloadTicks = 0;
                    }
                }
            }

            // Phase 6: Start breaking the placed shulker
            case 6 -> {
                // Make sure screen is closed
                /*? if >=26.1 {*//*
                if (mc.screen != null) {
                *//*?} else {*/
                if (mc.currentScreen != null) {
                /*?}*/
                    /*? if >=26.1 {*//*
                    player.clientSideCloseContainer();
                    *//*?} else {*/
                    player.closeHandledScreen();
                    /*?}*/
                    return;
                }

                BlockState st = world.getBlockState(shulkerPlacePos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    // Already broken — go to pickup
                    shulkerUnloadPhase = 8;
                    shulkerUnloadTicks = 0;
                    return;
                }

                // Look at the shulker and start breaking
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 blockCenter = Vec3.atCenterOf(shulkerPlacePos);
                *//*?} else {*/
                Vec3d blockCenter = Vec3d.ofCenter(shulkerPlacePos);
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 toBlock = blockCenter.subtract(eyePos);
                *//*?} else {*/
                Vec3d toBlock = blockCenter.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
                /*? if >=26.1 {*//*
                float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                *//*?} else {*/
                float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                /*?}*/
                /*? if >=26.1 {*//*
                float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                *//*?} else {*/
                float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                /*?}*/
                /*? if >=26.1 {*//*
                player.setYRot(breakYaw);
                *//*?} else {*/
                player.setYaw(breakYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                player.setXRot(Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                player.setPitch(MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

                if (!tryPrinterMining(1)) {
                    return;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(shulkerPlacePos, Direction.UP);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(shulkerPlacePos, Direction.UP);
                /*?}*/
                /*? if >=26.1 {*//*
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
                shulkerUnloadPhase = 7;
                shulkerUnloadTicks = 0;
            }

            // Phase 7: Continue breaking until shulker drops
            case 7 -> {
                BlockState st = world.getBlockState(shulkerPlacePos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    // Broken!
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setYRot(shulkerSavedYaw);
                    *//*?} else {*/
                    player.setYaw(shulkerSavedYaw);
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setXRot(shulkerSavedPitch);
                    *//*?} else {*/
                    player.setPitch(shulkerSavedPitch);
                    /*?}*/
                    shulkerUnloadPhase = 8;
                    shulkerUnloadTicks = 0;
                    return;
                }

                if (shulkerUnloadTicks >= MAX_SHULKER_PHASE_TICKS) {
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setYRot(shulkerSavedYaw);
                    *//*?} else {*/
                    player.setYaw(shulkerSavedYaw);
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setXRot(shulkerSavedPitch);
                    *//*?} else {*/
                    player.setPitch(shulkerSavedPitch);
                    /*?}*/
                    shulkerUnloadFailures++;
                    if (statusMessages) {
                        ChatHelper.info("§eShulker break timed out — aborting.");
                    }
                    // Don't loop — just finish, items are already taken
                    finishShulkerUnloading(mc);
                    return;
                }

                // Maintain look direction + continue breaking
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 blockCenter = Vec3.atCenterOf(shulkerPlacePos);
                *//*?} else {*/
                Vec3d blockCenter = Vec3d.ofCenter(shulkerPlacePos);
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 toBlock = blockCenter.subtract(eyePos);
                *//*?} else {*/
                Vec3d toBlock = blockCenter.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
                /*? if >=26.1 {*//*
                float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                *//*?} else {*/
                float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                /*?}*/
                /*? if >=26.1 {*//*
                float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                *//*?} else {*/
                float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                /*?}*/
                /*? if >=26.1 {*//*
                player.setYRot(breakYaw);
                *//*?} else {*/
                player.setYaw(breakYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                player.setXRot(Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                player.setPitch(MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

                if (!tryPrinterMining(1)) {
                    return;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(shulkerPlacePos, Direction.UP);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(shulkerPlacePos, Direction.UP);
                /*?}*/
                /*? if >=26.1 {*//*
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
            }

            // Phase 8: Wait for item entity pickup
            case 8 -> {
                if (shulkerUnloadTicks >= SHULKER_PICKUP_DELAY) {
                    // Verify inventory has a shulker (i.e. the item was
                    // actually picked up).  If inventory is full the
                    // entity may still be on the ground — wait longer.
                    boolean pickedUp = false;
                    /*? if >=26.1 {*//*
                    Inventory inv = player.getInventory();
                    *//*?} else {*/
                    PlayerInventory inv = player.getInventory();
                    /*?}*/
                    for (int i = 0; i < 36; i++) {
                        /*? if >=26.1 {*//*
                        if (isShulkerBox(inv.getItem(i))) {
                        *//*?} else {*/
                        if (isShulkerBox(inv.getStack(i))) {
                        /*?}*/
                            pickedUp = true;
                            break;
                        }
                    }
                    if (!pickedUp && shulkerUnloadTicks < SHULKER_PICKUP_DELAY + 40) {
                        // Extended wait — inventory may be full.
                        // Keep waiting for space to free up or item to despawn.
                        return;
                    }
                    if (!pickedUp && statusMessages) {
                        ChatHelper.info("§c⚠ Broken shulker may not have been picked up!");
                    }
                    // Finish this cycle — only process ONE shulker per
                    // unload cycle to prevent inventory overflow and
                    // accidental shulker drops.
                    finishShulkerUnloading(mc);
                }
            }
        }
    }

    // Finishes the shulker unloading process and transitions to the
    // appropriate next state (walk back to build zone or resume building).
    /*? if >=26.1 {*//*
    private void finishShulkerUnloading(Minecraft mc) {
    *//*?} else {*/
    private void finishShulkerUnloading(MinecraftClient mc) {
    /*?}*/
        clearPendingContainerAction();
        blockedShulkerSlots.clear();
        shulkerUnloadPhase = 0;
        shulkerPlacePos = null;
        shulkerHotbarSlot = -1;
        shulkerTotalTicks = 0;
        shulkerUnloadFailures = 0;
        platformBuildAttempts = 0;
        platformBlockPos = null;
        shulkerOpenRetries = 0;

        if (!shulkerNoSpaceSkipped
                && !shulkerMovedNeededItems
                && mc.player != null
                && findShulkerWithNeededItems(mc.player) >= 0) {
            shulkerNoSpaceSkipped = true;
            PacketTelemetry.mark("restock shulker no-progress; using supply chest");
        }
        shulkerMovedNeededItems = false;

        if (statusMessages) {
            if (shulkerNoSpaceSkipped) {
                ChatHelper.info("§eNo shulker space — walking to supply chest.");
            } else {
                ChatHelper.info("§aShulker unloading complete. Walking back to build.");
            }
        }

        // If shulker unloading was skipped due to no placement space
        // (e.g. on a 1-block scaffold pillar), fall through to normal
        // supply walk instead of looping back to BUILDING which would
        // re-detect the shulkers and loop forever.
        if (shulkerNoSpaceSkipped) {
            // shulkerNoSpaceSkipped stays true — startRestockRun will
            // see it and skip the shulker-in-inventory shortcut.
            /*? if >=26.1 {*//*
            startRestockRun(mc.player, mc.level);
            *//*?} else {*/
            startRestockRun(mc.player, mc.world);
            /*?}*/
            return;
        }

        if (lastBuildPos != null) {
            double returnDy = Math.abs(lastBuildPos.getY()
                    /*? if >=26.1 {*//*
                    - mc.player.blockPosition().getY());
                    *//*?} else {*/
                    - mc.player.getBlockPos().getY());
                    /*?}*/
            int radius = (int) Math.ceil(range);
            if (returnDy > 8) {
                walkToZoneWithPlacement(mc.player, lastBuildPos, radius);
            } else {
                PathWalker.walkToNearby(lastBuildPos, radius);
            }
            autoState = AutoState.WALKING_BACK;
        } else {
            resumeBuildFromCurrentStance("shulker unload finished at build site");
        }
    }

    /*? if >=26.1 {*//*
    private void tickIdle(Minecraft mc) {
    *//*?} else {*/
    private void tickIdle(MinecraftClient mc) {
    /*?}*/
        SetbackMonitor setbackMonitor = SetbackMonitor.get();
        int totalSetbacks = setbackMonitor.totalSetbacks();
        if (totalSetbacks != observedPlacementSetbacks) {
            observedPlacementSetbacks = totalSetbacks;
            refreshPlacementPlannerState();
            idleScanCooldown = 0;
        }

        idleScanCooldown--;
        if (idleScanCooldown > 0) return;
        idleScanCooldown = IDLE_SCAN_INTERVAL;

        // If we went idle because of missing items, check whether the
        // player has obtained them (manually or via a newly added chest).
        if (!lastMissingItems.isEmpty()) {
            // Supply chest was added since we went idle — go restock
            if (MoarMod.getChestManager().supplyChestCount() > 0) {
                if (statusMessages) {
                    ChatHelper.info("§aSupply chest available — resuming build.");
                }
                // Reset unreachable set — the player may have moved
                // since the last failed attempt.
                unreachableChests.clear();
                resumeBuildFromCurrentStance("idle resume after supply chest became available");
                return;
            }
            // Check if the player picked up any of the missing items
            Map<Item, Integer> inv = PlacementEngine.getInventoryContentsCached();
            boolean hasAny = lastMissingItems.stream().anyMatch(i -> inv.getOrDefault(i, 0) > 0);
            if (hasAny) {
                if (statusMessages) {
                    ChatHelper.info("§aMaterials detected — resuming build.");
                }
                lastMissingItems.clear();
                resumeBuildFromCurrentStance("idle resume after materials detected");
                return;
            }
            return; // still missing, stay idle
        }

        // Phase transitions: structural → redstone → liquid
        /*? if >=26.1 {*//*
        if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.level)
        *//*?} else {*/
        if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.world)
        /*?}*/
                /*? if >=26.1 {*//*
                && hasRemainingRedstone(mc.level)) {
                *//*?} else {*/
                && hasRemainingRedstone(mc.world)) {
                /*?}*/
            redstonePass = true;
            noProgressTicks = 0;
            stuckCycles = 0;
            failedZones.clear();
            if (statusMessages) {
                ChatHelper.info("§bResuming — placing redstone components...");
            }
            enterBuildMode("idle -> redstone pass");
            return;
        }
        /*? if >=26.1 {*//*
        if (!liquidPass && !hasRemainingSolids(mc.level) && !hasRemainingRedstone(mc.level)
        *//*?} else {*/
        if (!liquidPass && !hasRemainingSolids(mc.world) && !hasRemainingRedstone(mc.world)
        /*?}*/
                /*? if >=26.1 {*//*
                && hasRemainingLiquids(mc.level)) {
                *//*?} else {*/
                && hasRemainingLiquids(mc.world)) {
                /*?}*/
            liquidPass = true;
            redstonePass = false;
            noProgressTicks = 0;
            stuckCycles = 0;
            failedZones.clear();
            PathWalker.stop();
            if (statusMessages) {
                ChatHelper.info("§bResuming — placing remaining liquids...");
            }
            enterBuildMode("idle -> liquid pass");
            return;
        }
        /*? if >=26.1 {*//*
        BlockPos nextZone = findNextBuildZone(mc.player, mc.level);
        *//*?} else {*/
        BlockPos nextZone = findNextBuildZone(mc.player, mc.world);
        /*?}*/
        // Idle should not trust stale diff state for long.
        // If work remains but no zone is visible, rebuild the chunk view
        // and retry once before sitting still again.
        if (nextZone == null && countRemaining() > 0) {
            rebuildSchematicChunkIndex();
            failedZones.clear();
            /*? if >=26.1 {*//*
            nextZone = findNextBuildZone(mc.player, mc.level);
            *//*?} else {*/
            nextZone = findNextBuildZone(mc.player, mc.world);
            /*?}*/
        }
        if (nextZone != null) {
            failedZones.clear();
            walkFailCount = 0;
            lastWalkTargetZone = null;
            walkAttemptCooldown = 0;
            // Reuse the main build-zone walk logic so idle recovery
            // respects the same "already in range, just build" rules.
            if (tryWalkToNextZone(mc)) {
                return;
            }
            if (statusMessages) {
                ChatHelper.info("§eBuild zone found but no reposition was needed — retrying from here.");
            }
            resumeBuildFromCurrentStance("idle found build zone already reachable");
            return;
        }

        // No zones in loaded chunks — check unloaded regions
        /*? if >=26.1 {*//*
        BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.level);
        *//*?} else {*/
        BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.world);
        /*?}*/
        if (unloadedZone != null) {
            failedZones.clear();
            LOGGER.debug("Resuming — walking to unloaded region {} {} {}",
                    unloadedZone.getX(), unloadedZone.getY(), unloadedZone.getZ());
            PathWalker.walkToNearby(unloadedZone, (int) Math.ceil(range));
            autoState = AutoState.WALKING_TO_BUILD;
            return;
        }

        // If we still have work but couldn't resolve a zone this pass,
        // bounce back into BUILDING immediately so the live world scan
        // can try again next tick instead of idling for seconds.
        if (countRemaining() > 0) {
            if (!setbackMonitor.isCalm()) {
                return;
            }
            resumeBuildFromCurrentStance("idle bounce after unresolved work remains");
        }
    }

    // Returns true if the world block at this position matches
    // the schematic's expected block (i.e. it's correctly placed).
    /*? if >=26.1 {*//*
    private boolean isCorrectSchematicBlock(BlockPos worldPos, Level world) {
    *//*?} else {*/
    private boolean isCorrectSchematicBlock(BlockPos worldPos, World world) {
    /*?}*/
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) return false;
        BlockState expected = schematic.getBlockState(sx, sy, sz);
        if (expected.isAir()) return false;
        return isEffectivelyPlaced(world.getBlockState(worldPos), expected);
    }

    private int resolveRenderDistanceChunks() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null || mc.options == null) return 8;

        Object options = mc.options;
        Integer value = invokeInteger(options, "getClampedViewDistance");
        if (value != null) return Math.max(2, value);

        value = invokeOptionInteger(options, "getViewDistance");
        if (value != null) return Math.max(2, value);

        value = readOptionInteger(options, "viewDistance");
        if (value != null) return Math.max(2, value);

        value = readOptionInteger(options, "renderDistance");
        if (value != null) return Math.max(2, value);

        return 8;
    }

    private static Integer invokeInteger(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Integer invokeOptionInteger(Object target, String methodName) {
        try {
            Object option = target.getClass().getMethod(methodName).invoke(target);
            if (option == null) return null;
            Integer value = invokeInteger(option, "get");
            if (value != null) return value;
            value = invokeInteger(option, "getValue");
            if (value != null) return value;
            return readOptionInteger(option, "value");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Integer readOptionInteger(Object target, String fieldName) {
        try {
            Object value = target.getClass().getField(fieldName).get(target);
            if (value instanceof Number) return ((Number) value).intValue();
            if (value == null) return null;
            Integer nested = invokeInteger(value, "get");
            if (nested != null) return nested;
            nested = invokeInteger(value, "getValue");
            if (nested != null) return nested;
            return null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private RenderWindow getRenderWindow(BlockPos playerPos) {
        int radius = resolveRenderDistanceChunks() + RENDER_SCAN_EXTRA_CHUNKS;
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        return new RenderWindow(radius,
                chunkX - radius, chunkX + radius,
                chunkZ - radius, chunkZ + radius);
    }

    private static boolean isWithinRenderWindow(RenderWindow window, int wx, int wz) {
        int chunkX = wx >> 4;
        int chunkZ = wz >> 4;
        return chunkX >= window.minChunkX() && chunkX <= window.maxChunkX()
                && chunkZ >= window.minChunkZ() && chunkZ <= window.maxChunkZ();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static long chunkKey(BlockPos pos) {
        return chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static int chunkXFromKey(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    private static int chunkZFromKey(long chunkKey) {
        return (int) chunkKey;
    }

    private void rebuildSchematicChunkIndex() {
        schematicBlocksByChunk.clear();
        chunkDiffCache.clear();
        invalidateBuildStagingPlanCache();
        remainingCacheTick = Long.MIN_VALUE;
        cachedCountRemaining = -1;
        solidsCacheTick = Long.MIN_VALUE;
        liquidsCacheTick = Long.MIN_VALUE;
        redstoneCacheTick = Long.MIN_VALUE;
        cachedActiveBandTick = Long.MIN_VALUE;

        if (schematic == null || anchor == null) return;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                        if (isAutoCreatedPart(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        boolean liquid = isLiquidSource(target);
                        boolean redstone = !liquid && BlockDependency.isRedstoneComponent(target);
                        Item item = liquid ? getLiquidBucketItem(target) : target.getBlock().asItem();
                        BlockPos pos = new BlockPos(wx, wy, wz);
                        long key = chunkKey(wx >> 4, wz >> 4);
                        schematicBlocksByChunk
                                .computeIfAbsent(key, ignored -> new ArrayList<>())
                                .add(new SchematicBlockRef(pos, target, item, liquid, redstone));
                    }
                }
            }
        }
    }

    private void invalidateChunkDiffCache(BlockPos pos) {
        if (pos == null) return;
        chunkDiffCache.remove(chunkKey(pos));
        invalidateBuildStagingPlanCache();
        remainingCacheTick = Long.MIN_VALUE;
        cachedCountRemaining = -1;
        solidsCacheTick = Long.MIN_VALUE;
        liquidsCacheTick = Long.MIN_VALUE;
        redstoneCacheTick = Long.MIN_VALUE;
        cachedActiveBandTick = Long.MIN_VALUE;
    }

    private void invalidateBuildStagingPlanCache() {
        cachedBuildStagingPlanTick = Long.MIN_VALUE;
        cachedBuildStagingPlanPlayerPos = null;
        cachedBuildStagingPlanExtraReachBonus = Integer.MIN_VALUE;
        cachedBuildStagingPlan = null;
        cachedActiveBandTick = Long.MIN_VALUE;
    }

    private void rememberRepairPriorityTarget(BlockPos pos) {
        if (pos == null) return;
        /*? if >=26.1 {*//*
        BlockPos immutablePos = pos.immutable();
        *//*?} else {*/
        BlockPos immutablePos = pos.toImmutable();
        /*?}*/
        repairPriorityTargets.remove(immutablePos);
        repairPriorityTargets.add(immutablePos);
        invalidateChunkDiffCache(immutablePos);
    }

    /*? if >=26.1 {*//*
    private void rememberRepairPriorityIfPlaced(BlockPos pos, Level world) {
    *//*?} else {*/
    private void rememberRepairPriorityIfPlaced(BlockPos pos, World world) {
    /*?}*/
        if (pos == null || world == null) return;
        BlockState desired = getDesiredBuildStateAt(pos);
        if (desired == null || desired.isAir()) return;
        if (!isEffectivelyPlaced(world.getBlockState(pos), desired)) return;
        rememberRepairPriorityTarget(pos);
    }

    /*? if >=26.1 {*//*
    private void rememberRepairPriorityNeighborhood(BlockPos center, Level world, int radius) {
    *//*?} else {*/
    private void rememberRepairPriorityNeighborhood(BlockPos center, World world, int radius) {
    /*?}*/
        if (center == null || world == null) return;
        int r = Math.max(0, radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    rememberRepairPriorityIfPlaced(center.offset(dx, dy, dz), world);
                    *//*?} else {*/
                    rememberRepairPriorityIfPlaced(center.add(dx, dy, dz), world);
                    /*?}*/
                }
            }
        }
    }

    /*? if >=26.1 {*//*
    private void rememberRepairPriorityCorridor(BlockPos fromFeet, BlockPos toFeet, Level world) {
    *//*?} else {*/
    private void rememberRepairPriorityCorridor(BlockPos fromFeet, BlockPos toFeet, World world) {
    /*?}*/
        if (fromFeet == null || toFeet == null || world == null) return;
        int dx = toFeet.getX() - fromFeet.getX();
        int dy = toFeet.getY() - fromFeet.getY();
        int dz = toFeet.getZ() - fromFeet.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos sample = new BlockPos(
                    fromFeet.getX() + (int) Math.round(dx * t),
                    fromFeet.getY() + (int) Math.round(dy * t),
                    fromFeet.getZ() + (int) Math.round(dz * t));
            rememberRepairPriorityNeighborhood(sample, world, ACCESS_REPAIR_MEMORY_RADIUS);
        }
    }

    /*? if >=26.1 {*//*
    private void seedAccessRepairTargets(BlockPos fromFeet, BuildAccessTarget access,
                                         BlockPos targetPos, Level world) {
    *//*?} else {*/
    private void seedAccessRepairTargets(BlockPos fromFeet, BuildAccessTarget access,
                                         BlockPos targetPos, World world) {
    /*?}*/
        if (access == null || access.feetPos() == null || world == null) return;
        rememberRepairPriorityCorridor(fromFeet, access.feetPos(), world);
        rememberRepairPriorityNeighborhood(access.feetPos(), world, ACCESS_REPAIR_MEMORY_RADIUS);
        rememberRepairPriorityNeighborhood(targetPos, world, ACCESS_REPAIR_MEMORY_RADIUS);
        if (access.clearPos() != null) {
            rememberRepairPriorityNeighborhood(access.clearPos(), world, ACCESS_REPAIR_MEMORY_RADIUS);
        }
    }

    private boolean isPendingSchematicPlacement(SchematicBlockRef ref) {
        return pendingBuildPlacementPos != null
                && pendingBuildPlacementState != null
                && pendingBuildPlacementPos.equals(ref.pos())
                && pendingBuildPlacementState.equals(ref.target());
    }

    /*? if >=26.1 {*//*
    private boolean isCurrentlyUnresolved(SchematicBlockRef ref, Level world) {
    *//*?} else {*/
    private boolean isCurrentlyUnresolved(SchematicBlockRef ref, World world) {
    /*?}*/
        if (ref == null || world == null || isPendingSchematicPlacement(ref)) {
            return false;
        }
        if (isEffectivelyPlaced(world.getBlockState(ref.pos()), ref.target())) {
            repairPriorityTargets.remove(ref.pos());
            invalidateChunkDiffCache(ref.pos());
            return false;
        }
        return true;
    }

    /*? if >=26.1 {*//*
    private List<SchematicBlockRef> getUnresolvedChunkEntries(Level world, long tick, long chunkKey) {
    *//*?} else {*/
    private List<SchematicBlockRef> getUnresolvedChunkEntries(World world, long tick, long chunkKey) {
    /*?}*/
        List<SchematicBlockRef> indexed = schematicBlocksByChunk.get(chunkKey);
        if (indexed == null || indexed.isEmpty()) return List.of();

        ChunkDiffSnapshot cached = chunkDiffCache.get(chunkKey);
        if (cached != null && tick - cached.scanTick() < CHUNK_DIFF_CACHE_TTL) {
            return cached.unresolved();
        }

        List<SchematicBlockRef> unresolved = new ArrayList<>();
        for (SchematicBlockRef ref : indexed) {
            if (isPendingSchematicPlacement(ref)) continue;
            if (isEffectivelyPlaced(world.getBlockState(ref.pos()), ref.target())) {
                repairPriorityTargets.remove(ref.pos());
                continue;
            }
            unresolved.add(ref);
        }
        List<SchematicBlockRef> snapshot = List.copyOf(unresolved);
        chunkDiffCache.put(chunkKey, new ChunkDiffSnapshot(tick, snapshot));
        return snapshot;
    }

    // Finds the lowest unbuilt block above the player's reach.
    // Among same-Y candidates, prefers nearest by horizontal distance.
    /*? if >=26.1 {*//*
    private BlockPos findHighBuildZone(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findHighBuildZone(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) return null;

        /*? if >=26.1 {*//*
        int playerY = player.blockPosition().getY();
        *//*?} else {*/
        int playerY = player.getBlockPos().getY();
        /*?}*/
        RenderWindow renderWindow =
                getRenderWindow(/*? if >=26.1 {*//* player.blockPosition() *//*?} else {*/player.getBlockPos()/*?}*/);
        BlockPos bestVisible = null;
        int bestVisibleY = Integer.MAX_VALUE;
        double bestVisibleDist = Double.MAX_VALUE;
        BlockPos bestFallback = null;
        int bestFallbackY = Integer.MAX_VALUE;
        double bestFallbackDist = Double.MAX_VALUE;
        int activeBandTopY = getBottomUpActiveBandTopY(world, renderWindow);

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/

        long tick = getWorldTick();
        for (long key : schematicBlocksByChunk.keySet()) {
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            /*? if >=26.1 {*//*
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            *//*?} else {*/
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            /*?}*/

            for (SchematicBlockRef ref : getUnresolvedChunkEntries(world, tick, key)) {
                if (!matchesCurrentBuildPass(ref)) continue;
                if (!isCurrentlyUnresolved(ref, world)) continue;
                BlockPos worldPos = ref.pos();
                if (worldPos.getY() > activeBandTopY) continue;
                if (isBuildTargetCoolingDown(worldPos)) continue;
                if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) continue;
                if (!BlockDependency.isReadyToPlace(world, worldPos, ref.target())) continue;

                int wy = worldPos.getY();
                if (wy <= playerY + 2) continue;
                /*? if >=26.1 {*//*
                double dist = playerPos.distanceToSqr(Vec3.atCenterOf(worldPos));
                *//*?} else {*/
                double dist = playerPos.squaredDistanceTo(Vec3d.ofCenter(worldPos));
                /*?}*/
                if (isWithinRenderWindow(renderWindow, worldPos.getX(), worldPos.getZ())) {
                    if (wy < bestVisibleY || (wy == bestVisibleY && dist < bestVisibleDist)) {
                        bestVisibleY = wy;
                        bestVisibleDist = dist;
                        bestVisible = worldPos;
                    }
                } else if (wy < bestFallbackY || (wy == bestFallbackY && dist < bestFallbackDist)) {
                    bestFallbackY = wy;
                    bestFallbackDist = dist;
                    bestFallback = worldPos;
                }
            }
        }

        // Prefer the rendered window; fall back to other loaded chunks.
        return bestVisible != null ? bestVisible : bestFallback;
    }

    // INVENTORY & RESTOCK

    /*? if >=26.1 {*//*
    private boolean shouldRestock(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean shouldRestock(ClientPlayerEntity player, World world) {
    /*?}*/
        if (MoarMod.getChestManager().supplyChestCount() == 0) return false;

        Map<Item, Integer> needed = getNeededItemsNearby(player, world, 200);
        Map<Item, Integer> inventory = PlacementEngine.getInventoryContentsCached();

        for (var entry : needed.entrySet()) {
            int have = inventory.getOrDefault(entry.getKey(), 0);
            if (have < RESTOCK_THRESHOLD && entry.getValue() > have) {
                return true;
            }
        }
        return false;
    }

    /*? if >=26.1 {*//*
    private boolean tryStartProactiveRestock(Minecraft mc) {
    *//*?} else {*/
    private boolean tryStartProactiveRestock(MinecraftClient mc) {
    /*?}*/
        if (mc == null || mc.player == null) return false;
        if (restockFailures >= MAX_RESTOCK_FAILURES) return false;
        if (MoarMod.getChestManager().supplyChestCount() == 0) return false;

        /*? if >=26.1 {*//*
        if (!shouldRestock(mc.player, mc.level)) {
        *//*?} else {*/
        if (!shouldRestock(mc.player, mc.world)) {
        /*?}*/
            return false;
        }

        if (statusMessages && missingItemMsgCooldown <= 0) {
            ChatHelper.info("§eBuild materials running low — restocking from supply.");
            missingItemMsgCooldown = MISSING_MSG_COOLDOWN;
        }
        noProgressTicks = 0;
        /*? if >=26.1 {*//*
        startRestockRun(mc.player, mc.level);
        *//*?} else {*/
        startRestockRun(mc.player, mc.world);
        /*?}*/
        return autoState == AutoState.WALKING_TO_SUPPLY
                || autoState == AutoState.RESTOCKING
                || autoState == AutoState.UNLOADING_SHULKER;
    }

    private boolean shulkerContainsNeeded(ItemStack shulkerStack, Map<String, Integer> remainingNeed) {
        if (remainingNeed == null || remainingNeed.isEmpty()) return false;
        /*? if >=26.1 {*//*
        ItemContainerContents cc = shulkerStack.get(DataComponents.CONTAINER);
        *//*?} else {*/
        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        /*?}*/
        if (cc == null) return false;

        /*? if >=26.1 {*//*
        for (ItemStack inner : cc.nonEmptyItemCopyStream().toList()) {
        *//*?} else {*/
        for (ItemStack inner : cc.iterateNonEmpty()) {
        /*?}*/
            String innerId = ItemIdentifier.getItemId(inner);
            if (remainingNeed.getOrDefault(innerId, 0) > 0) return true;
        }
        return false;
    }

    private boolean shulkerContainsCurrentRestockNeed(ItemStack shulkerStack,
                                                      Map<String, Integer> remainingNeed) {
        return shulkerContainsNeeded(shulkerStack, remainingNeed)
                || shulkerContainsNeeded(shulkerStack, neededItems);
    }

    private boolean shouldSkipRestockSlot(int slot, ItemStack stack,
                                          Map<String, Integer> remainingNeed) {
        if (!blockedRestockSlots.contains(slot)) {
            return false;
        }
        if (isShulkerBox(stack) && shulkerContainsCurrentRestockNeed(stack, remainingNeed)) {
            blockedRestockSlots.remove(slot);
            PacketTelemetry.mark("restock retry needed-shulker blocked-slot=" + slot
                    + " chest=" + supplyTarget);
            return false;
        }
        return true;
    }

    // DUMP — deposit mined items into a dump chest when inventory is full

    /*? if >=26.1 {*//*
    private void startDumpRun(Minecraft mc) {
    *//*?} else {*/
    private void startDumpRun(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/

        int shulkerSlot = findAnyShulkerInInventory(mc.player);
        if (shulkerSlot >= 0) {
            dumpMode = DumpMode.LOCAL_SHULKER;
            dumpShulkerSlot = shulkerSlot;
            dumpShulkerPhase = 0;
            dumpShulkerPos = null;
            dumpShulkerOpenRetries = 0;
            dumpShulkerTransferTimeouts = 0;
            clearPendingContainerAction();
            blockedDumpSlots.clear();
            dumpWaitTicks = 0;
            dumpSyncDelay = 0;
            preDumpClearPos =
                    /*? if >=26.1 {*//* playerPos.immutable() *//*?} else {*/playerPos.toImmutable()/*?}*/;
            autoState = AutoState.DUMPING;
            if (statusMessages) {
                ChatHelper.info("§7Inventory nearly full — stashing overflow into a local shulker.");
            }
            return;
        }

        BlockPos nearest = MoarMod.getChestManager().findNearestDumpChest(playerPos);
        if (nearest == null) {
            // No reachable dump chests — continue clearing
            return;
        }
        dumpMode = DumpMode.CHEST;
        dumpTarget = nearest;
        /*? if >=26.1 {*//*
        preDumpClearPos = playerPos.immutable();
        *//*?} else {*/
        preDumpClearPos = playerPos.toImmutable();
        /*?}*/
        clearPendingContainerAction();
        blockedDumpSlots.clear();
        dumpWaitTicks = 0;
        dumpSyncDelay = 0;

        double dy = Math.abs(nearest.getY() - playerPos.getY());
        double dist = Math.sqrt(
                /*? if >=26.1 {*//*
                playerPos.distSqr(nearest));
                *//*?} else {*/
                playerPos.getSquaredDistance(nearest));
                /*?}*/
        if (dy > 8) {
            walkToZoneWithPlacement(mc.player, nearest, 2);
        } else if (dist > 48) {
            List<BlockPos> legs = computeLinearWaypoints(playerPos, nearest, 48);
            PathWalker.walkToViaWaypoints(legs, 2);
        } else {
            PathWalker.walkToNearby(nearest, 2);
        }
        autoState = AutoState.WALKING_TO_DUMP;

        if (statusMessages) {
            ChatHelper.info("§7Inventory nearly full — dumping items at §e"
                    + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ());
        }
    }

    /*? if >=26.1 {*//*
    private void startRestockRun(LocalPlayer player, Level world) {
    *//*?} else {*/
    private void startRestockRun(ClientPlayerEntity player, World world) {
    /*?}*/
        Map<Item, Integer> needed = getNeededItemsNearby(player, world, RESTOCK_WORKING_SET_SCAN_LIMIT);
        Map<Item, Integer> inventory = PlacementEngine.getInventoryContentsCached();

        clearPendingContainerAction();
        blockedRestockSlots.clear();
        neededItems = new LinkedHashSet<>();
        neededItemCounts = new LinkedHashMap<>();

        // Prioritize the concrete items the build loop already proved are missing,
        // then stage the nearest additional types into a bounded working set.
        for (Item missing : lastMissingItems) {
            addWorkingSetNeed(missing, 1, inventory);
        }
        for (var entry : needed.entrySet()) {
            if (neededItems.size() >= RESTOCK_WORKING_SET_TYPES) break;
            addWorkingSetNeed(entry.getKey(), entry.getValue(), inventory);
        }

        if (neededItems.isEmpty()) {
            resumeBuildFromCurrentStance("restock found inventory already sufficient");
            return;
        }

        // Check if shulkers in inventory already have what we need
        // Before walking to a supply chest, see if the player is already
        // carrying shulker boxes with the required materials.  If so,
        // skip the supply walk entirely and go straight to unloading.
        // BUT: if we already tried and couldn't place a shulker (e.g.
        // standing on a 1-block pillar with no space), don't retry —
        // walk to a supply chest instead where there's flat ground.
        if (!shulkerNoSpaceSkipped && findShulkerWithNeededItems(player) >= 0) {
            /*? if >=26.1 {*//*
            lastBuildPos = player.blockPosition();
            *//*?} else {*/
            lastBuildPos = player.getBlockPos();
            /*?}*/
            if (statusMessages) {
                ChatHelper.info("§aNeeded items found in inventory shulkers — unloading.");
            }
            shulkerUnloadPhase = 0;
            shulkerUnloadTicks = 0;
            shulkerTotalTicks = 0;
            shulkerUnloadFailures = 0;
            shulkerMovedNeededItems = false;
            blockedShulkerSlots.clear();
            autoState = AutoState.UNLOADING_SHULKER;
            return;
        }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        Set<BlockPos> outOfRegionSupplyChests = findOutOfRegionSupplyChests();
        Set<BlockPos> excludedSupplyChests = new HashSet<>(unreachableChests);
        excludedSupplyChests.addAll(outOfRegionSupplyChests);
        int totalSupplyChests = MoarMod.getChestManager().supplyChestCount();
        int localSupplyChests = Math.max(0, totalSupplyChests - outOfRegionSupplyChests.size());
        if (!outOfRegionSupplyChests.isEmpty()) {
            PacketTelemetry.mark("restock supply scope local=" + localSupplyChests
                    + " outOfRegion=" + outOfRegionSupplyChests.size()
                    + " unreachable=" + unreachableChests.size());
        }
        BlockPos nearest = MoarMod.getChestManager().findBestChest(
                /*? if >=26.1 {*//*
                player.blockPosition(), neededItems, excludedSupplyChests);
                *//*?} else {*/
                player.getBlockPos(), neededItems, excludedSupplyChests);
                /*?}*/
        if (nearest == null) {
            if (statusMessages) {
                if (totalSupplyChests > 0 && localSupplyChests == 0) {
                    ChatHelper.info("§eNo supply chests inside this build region. "
                            + "Use §f/printer supply add §enear the active schematic.");
                } else if (!unreachableChests.isEmpty()) {
                    ChatHelper.info("§cAll supply chests unreachable ("
                            + unreachableChests.size() + " skipped). Going idle."
                            + " Move closer or add a reachable chest.");
                } else {
                    ChatHelper.info("§eNo supply chests configured. Use §f/printer supply add");
                }
            }
            autoState = AutoState.IDLE;
            return;
        }

        supplyTarget = nearest;
        /*? if >=26.1 {*//*
        lastBuildPos = player.blockPosition();
        *//*?} else {*/
        lastBuildPos = player.getBlockPos();
        /*?}*/
        triedWaypointRestock = false; // fresh attempt for new target
        triedLinearRestock = false;
        triedPlacementRestock = false;
        supplyDescentPhase = 0;
        supplyDescentTarget = null;
        // Tell PathWalker what items are reserved for the build so
        // Baritone only uses surplus blocks as scaffold material.
        PathWalker.setReservedItems(needed);

        // First attempt: simple walk WITHOUT placement.  Only use
        // placement as a retry strategy — scaffolding toward a chest
        // wastes blocks and leaves a mess when the chest is reachable
        // by normal walking (same elevation, no obstacles).
        /*? if >=26.1 {*//*
        double dy = Math.abs(nearest.getY() - player.blockPosition().getY());
        *//*?} else {*/
        double dy = Math.abs(nearest.getY() - player.getBlockPos().getY());
        /*?}*/
        double dist = Math.sqrt(
                /*? if >=26.1 {*//*
                player.blockPosition().distSqr(nearest));
                *//*?} else {*/
                player.getBlockPos().getSquaredDistance(nearest));
                /*?}*/
        if (dy > 8) {
            // Significant elevation change — use two-phase waypoints
            // with placement from the start.
            walkToZoneWithPlacement(player, nearest, 2);
        } else if (dist > 48) {
            // Long horizontal distance — break into legs but no
            // placement (Baritone walks around obstacles normally).
            List<BlockPos> legs = computeLinearWaypoints(
                    /*? if >=26.1 {*//*
                    player.blockPosition(), nearest, 48);
                    *//*?} else {*/
                    player.getBlockPos(), nearest, 48);
                    /*?}*/
            PathWalker.walkToViaWaypoints(legs, 2);
        } else {
            // Short/moderate distance, same elevation — simple walk.
            PathWalker.walkToNearby(nearest, 2);
        }
        autoState = AutoState.WALKING_TO_SUPPLY;

        if (statusMessages) {
            ChatHelper.info("§7Restocking — walking to supply §e"
                    + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
                    + "\n§7Looking for: " + formatNeededItemIds(neededItems));
        }
    }

    private Set<BlockPos> findOutOfRegionSupplyChests() {
        Set<BlockPos> out = new HashSet<>();
        if (schematic == null || anchor == null) {
            return out;
        }
        for (BlockPos pos : MoarMod.getChestManager().getSupplyPositions()) {
            if (!isInsideActiveBuildSupplyRegion(pos)) {
                out.add(immutableBlockPos(pos));
            }
        }
        return out;
    }

    private boolean isInsideActiveBuildSupplyRegion(BlockPos pos) {
        if (schematic == null || anchor == null || pos == null) {
            return true;
        }
        int minX = anchor.getX() - SUPPLY_REGION_MARGIN_XZ;
        int maxX = anchor.getX() + schematic.getSizeX() - 1 + SUPPLY_REGION_MARGIN_XZ;
        int minY = anchor.getY() - SUPPLY_REGION_MARGIN_Y;
        int maxY = anchor.getY() + schematic.getSizeY() - 1 + SUPPLY_REGION_MARGIN_Y;
        int minZ = anchor.getZ() - SUPPLY_REGION_MARGIN_XZ;
        int maxZ = anchor.getZ() + schematic.getSizeZ() - 1 + SUPPLY_REGION_MARGIN_XZ;
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    // Builds greedy nearest-neighbour waypoints from player to supply
    // chest using known database positions (other chests + scaffold).
    // Last entry is always the chest itself.
    private List<BlockPos> computeSupplyWaypoints(BlockPos from, BlockPos to) {
        // 1. Collect all known positions from the database
        List<BlockPos> candidates = new ArrayList<>();

        // Other registered chests (not the target itself)
        for (BlockPos chest : MoarMod.getChestManager().getSupplyPositions()) {
            if (!chest.equals(to) && !chest.equals(from)
                    && isInsideActiveBuildSupplyRegion(chest)) {
                candidates.add(chest);
            }
        }

        // Scaffold blocks
        for (BlockPos scaffold : PrinterDatabase.getScaffoldEntries().keySet()) {
            if (!scaffold.equals(to) && !scaffold.equals(from)
                    && isInsideActiveBuildSupplyRegion(scaffold)) {
                candidates.add(scaffold);
            }
        }

        if (candidates.isEmpty()) {
            // Nothing in the database to use as waypoints
            /*? if >=26.1 {*//*
            return new ArrayList<>(List.of(to.immutable()));
            *//*?} else {*/
            return new ArrayList<>(List.of(to.toImmutable()));
            /*?}*/
        }

        // 2. Build a greedy nearest-neighbour chain
        //  Starting from the player, repeatedly pick the candidate
        //  that is (a) closest to the current position and (b) makes
        //  forward progress toward the target (closer to target than
        //  the current position is).
        List<BlockPos> waypoints = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        BlockPos current = from;
        /*? if >=26.1 {*//*
        double currentDistToTarget = Math.sqrt(current.distSqr(to));
        *//*?} else {*/
        double currentDistToTarget = Math.sqrt(current.getSquaredDistance(to));
        /*?}*/

        // Minimum distance between waypoints — prevents picking
        // clusters of nearby scaffold blocks as separate legs.
        final double MIN_LEG_DIST_SQ = 8.0 * 8.0;

        while (!candidates.isEmpty()) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;

            for (BlockPos cand : candidates) {
                if (used.contains(cand)) continue;

                /*? if >=26.1 {*//*
                double distFromCurrent = Math.sqrt(current.distSqr(cand));
                *//*?} else {*/
                double distFromCurrent = Math.sqrt(current.getSquaredDistance(cand));
                /*?}*/
                /*? if >=26.1 {*//*
                double distToTarget = Math.sqrt(cand.distSqr(to));
                *//*?} else {*/
                double distToTarget = Math.sqrt(cand.getSquaredDistance(to));
                /*?}*/

                // Must make forward progress — candidate should be
                // closer to the target than we currently are
                if (distToTarget >= currentDistToTarget) continue;

                // Must be a meaningful hop (not too close to current)
                /*? if >=26.1 {*//*
                if (current.distSqr(cand) < MIN_LEG_DIST_SQ) continue;
                *//*?} else {*/
                if (current.getSquaredDistance(cand) < MIN_LEG_DIST_SQ) continue;
                /*?}*/

                // Prefer the candidate closest to our current position
                // so each leg is short enough for Baritone to handle
                if (distFromCurrent < bestDist) {
                    bestDist = distFromCurrent;
                    best = cand;
                }
            }

            if (best == null) break; // no more useful candidates

            waypoints.add(best);
            used.add(best);
            current = best;
            /*? if >=26.1 {*//*
            currentDistToTarget = Math.sqrt(current.distSqr(to));
            *//*?} else {*/
            currentDistToTarget = Math.sqrt(current.getSquaredDistance(to));
            /*?}*/

            // Stop adding waypoints once we're close to the target —
            // let the final leg handle the last stretch directly
            if (currentDistToTarget < 15.0) break;
        }

        // 3. Always end at the chest
        /*? if >=26.1 {*//*
        waypoints.add(to.immutable());
        *//*?} else {*/
        waypoints.add(to.toImmutable());
        /*?}*/

        return waypoints;
    }

    // Splits a long path into fixed-length legs for Baritone.
    private List<BlockPos> computeLinearWaypoints(BlockPos from, BlockPos to, int legLength) {
        List<BlockPos> waypoints = new ArrayList<>();
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= legLength) {
            /*? if >=26.1 {*//*
            waypoints.add(to.immutable());
            *//*?} else {*/
            waypoints.add(to.toImmutable());
            /*?}*/
            return waypoints;
        }

        int legs = (int) Math.ceil(dist / legLength);
        for (int i = 1; i <= legs; i++) {
            double t = (double) i / legs;
            int wx = from.getX() + (int) (dx * t);
            int wy = from.getY() + (int) (dy * t);
            int wz = from.getZ() + (int) (dz * t);
            waypoints.add(new BlockPos(wx, wy, wz));
        }
        // Ensure the last waypoint is exactly the target
        /*? if >=26.1 {*//*
        waypoints.set(waypoints.size() - 1, to.immutable());
        *//*?} else {*/
        waypoints.set(waypoints.size() - 1, to.toImmutable());
        /*?}*/
        return waypoints;
    }

    // Walks to a build zone with placement enabled.  For vertical paths,
    // uses horizontal-then-vertical phasing.  For horizontal, uses
    // linear interpolation legs.
    /*? if >=26.1 {*//*
    private void walkToZoneWithPlacement(LocalPlayer player,
    *//*?} else {*/
    private void walkToZoneWithPlacement(ClientPlayerEntity player,
    /*?}*/
                                         BlockPos target, int radius) {
        // Tell PathWalker what items are reserved for the build so
        // Baritone only uses surplus blocks as scaffold material.
        /*? if >=26.1 {*//*
        Level w = Minecraft.getInstance().level;
        *//*?} else {*/
        World w = MinecraftClient.getInstance().world;
        /*?}*/
        if (w != null) {
            PathWalker.setReservedItems(getNeededItemsNearby(player, w, 200));
        }

        /*? if >=26.1 {*//*
        BlockPos from = player.blockPosition();
        *//*?} else {*/
        BlockPos from = player.getBlockPos();
        /*?}*/
        double dx = target.getX() - from.getX();
        double dy = target.getY() - from.getY();
        double dz = target.getZ() - from.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        double vertDist = Math.abs(dy);
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        List<BlockPos> legs = new ArrayList<>();

        if (dy > 2) {
            // ASCENDING: horizontal first, then pillar up.
            // Any ascent > 2 blocks triggers this path — even moderate
            // climbs (3–8 blocks) need explicit vertical legs to ensure
            // Baritone actually pillars up.  Without this, GoalNear's
            // 3D radius causes Baritone to consider the player
            // "arrived" while still at ground level.
            if (horizDist > 4) {
                // Break the horizontal phase into legs if it's long
                BlockPos base = new BlockPos(target.getX(), from.getY(), target.getZ());
                if (horizDist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            from, base, 48);
                    legs.addAll(horizLegs);
                } else {
                    legs.add(base);
                }
            }

            // Vertical phase — climb in 8-block steps
            int absY = (int) vertDist;
            int vertLeg = 8;
            int currentY = from.getY();
            int fullLegs = absY / vertLeg;
            for (int i = 0; i < fullLegs; i++) {
                currentY += vertLeg;
                legs.add(new BlockPos(target.getX(), currentY, target.getZ()));
            }

            /*? if >=26.1 {*//*
            legs.add(target.immutable());
            *//*?} else {*/
            legs.add(target.toImmutable());
            /*?}*/

            // Use per-waypoint radii: loose for intermediate legs,
            // tight (1 block) for the final target to force Baritone
            // to actually reach the target's Y level.
            List<Integer> radii = new ArrayList<>();
            for (int i = 0; i < legs.size() - 1; i++) {
                radii.add(radius);
            }
            radii.add(Math.min(radius, 1)); // tight on final target
            PathWalker.walkToViaWaypointsWithRadiiAndPlacement(legs, radii, player);
            return;

        } else if (vertDist > 8 && dy < 0) {
            // DESCENDING: 3-phase approach
            // Phase 1: walk horizontally at CURRENT elevation to the
            //   target's XZ column.  The player is on the scaffold /
            //   build platform which provides walkable terrain.
            // Phase 2: GoalYLevel descent — Baritone finds its own
            //   way down (staircase, existing terrain).  No XZ
            //   constraint means the staircase can drift freely.
            // Phase 3: short flat walk from wherever the descent
            //   ended to the actual chest position.
            //
            // Phases 2 & 3 are handled by tickWalkingToSupply when
            // each prior phase completes.
            supplyDescentPhase = 1;
            /*? if >=26.1 {*//*
            supplyDescentTarget = target.immutable();
            *//*?} else {*/
            supplyDescentTarget = target.toImmutable();
            /*?}*/

            if (horizDist > 4) {
                BlockPos aboveTarget = new BlockPos(
                        target.getX(), from.getY(), target.getZ());
                if (horizDist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            from, aboveTarget, 48);
                    legs.addAll(horizLegs);
                } else {
                    legs.add(aboveTarget);
                }
            } else {
                // Already above the target — skip to phase 2
                supplyDescentPhase = 2;
                PathWalker.walkToYLevelWithPlacement(
                        target.getY(), player);
                return;
            }

        } else {
            // HORIZONTAL / MODERATE: linear interpolation
            int legLength;
            if (totalDist > 80) {
                legLength = 32;
            } else {
                legLength = 48;
            }
            legs = computeLinearWaypoints(from, target, legLength);
        }

        if (legs.size() > 1) {
            PathWalker.walkToViaWaypointsWithPlacement(legs, radius, player);
        } else {
            // Short enough for a direct walk
            PathWalker.walkToWithPlacement(target, radius, player);
        }
    }

    /*? if >=26.1 {*//*
    private Map<Item, Integer> getNeededItemsNearby(LocalPlayer player, Level world, int limit) {
    *//*?} else {*/
    private Map<Item, Integer> getNeededItemsNearby(ClientPlayerEntity player, World world, int limit) {
    /*?}*/
        Map<Item, Integer> needed = new HashMap<>();
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/
        RenderWindow renderWindow = getRenderWindow(playerPos);

        // Collect all unbuilt positions with their distances, then sort
        // by proximity so we scan nearest blocks first — critical for
        // large builds where schematic-order scanning may pick up blocks
        // hundreds of blocks away.
        record Candidate(SchematicBlockRef ref, double distSq) {}
        List<Candidate> renderedCandidates = new ArrayList<>();
        List<Candidate> fallbackCandidates = new ArrayList<>();
        int activeBandTopY = getBottomUpActiveBandTopY(world, renderWindow);
        Integer frontierY = null;

        long tick = getWorldTick();
        for (long key : schematicBlocksByChunk.keySet()) {
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            /*? if >=26.1 {*//*
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            *//*?} else {*/
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            /*?}*/

            for (SchematicBlockRef ref : getUnresolvedChunkEntries(world, tick, key)) {
                if (liquidPass) {
                    if (!ref.liquid()) continue;
                } else if (redstonePass) {
                    if (!ref.redstone()) continue;
                } else {
                    if (ref.liquid() || ref.redstone()) continue;
                }
                if (!isCurrentlyUnresolved(ref, world)) continue;
                if (ref.pos().getY() > activeBandTopY) continue;
                if (isBuildTargetCoolingDown(ref.pos())) continue;
                if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, ref.pos())) continue;
                if (!BlockDependency.isReadyToPlace(world, ref.pos(), ref.target())) continue;
                if (isNearFailedZone(ref.pos())) continue;
                if (frontierY == null || isBetterBuildLayer(ref.pos().getY(), frontierY)) {
                    frontierY = ref.pos().getY();
                }
            }
        }

        if (frontierY == null) {
            return needed;
        }

        for (long key : schematicBlocksByChunk.keySet()) {
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            /*? if >=26.1 {*//*
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            *//*?} else {*/
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            /*?}*/

            for (SchematicBlockRef ref : getUnresolvedChunkEntries(world, tick, key)) {
                if (liquidPass) {
                    if (!ref.liquid()) continue;
                } else if (redstonePass) {
                    if (!ref.redstone()) continue;
                } else {
                    if (ref.liquid() || ref.redstone()) continue;
                }
                if (!isCurrentlyUnresolved(ref, world)) continue;
                if (ref.pos().getY() > activeBandTopY) continue;
                if (isBuildTargetCoolingDown(ref.pos())) continue;
                if (!isWithinActiveBuildFrontier(ref.pos().getY(), frontierY, activeBandTopY)) continue;
                if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, ref.pos())) continue;
                if (!BlockDependency.isReadyToPlace(world, ref.pos(), ref.target())) continue;
                if (isNearFailedZone(ref.pos())) continue;

                /*? if >=26.1 {*//*
                double distSq = playerPos.distSqr(ref.pos());
                *//*?} else {*/
                double distSq = playerPos.getSquaredDistance(ref.pos().getX(), ref.pos().getY(), ref.pos().getZ());
                /*?}*/
                Candidate candidate = new Candidate(ref, distSq);
                if (isWithinRenderWindow(renderWindow, ref.pos().getX(), ref.pos().getZ())) {
                    renderedCandidates.add(candidate);
                } else {
                    fallbackCandidates.add(candidate);
                }
            }
        }

        renderedCandidates.sort(Comparator.comparingDouble(c -> c.distSq));
        fallbackCandidates.sort(Comparator.comparingDouble(c -> c.distSq));

        int count = 0;
        for (List<Candidate> candidates : List.of(renderedCandidates, fallbackCandidates)) {
            for (Candidate c : candidates) {
                if (count >= limit) break;

                if (c.ref.liquid()) {
                    Item bucket = c.ref.item();
                    if (bucket != null) {
                        needed.merge(bucket, 1, Integer::sum);
                        count++;
                    }
                    continue;
                }

                Item item = c.ref.item();
                if (item != Items.AIR) {
                    needed.merge(item, 1, Integer::sum);
                    count++;
                }
            }
        }
        return needed;
    }

    // CHEST INTERACTION

    /*? if >=26.1 {*//*
    private boolean tryOpenChest(Minecraft mc, BlockPos chestPos) {
    *//*?} else {*/
    private boolean tryOpenChest(MinecraftClient mc, BlockPos chestPos) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null) return false;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null) return false;
        /*?}*/

        /*? if >=26.1 {*//*
        double dist = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(chestPos));
        *//*?} else {*/
        double dist = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(chestPos));
        /*?}*/
        if (dist > 5.0 * 5.0) return false;

        // Verify the block is actually a chest/container — if the chest
        // was broken, moved, or the position is wrong, don't interact.
        /*? if >=26.1 {*//*
        BlockState chestState = mc.level != null ? mc.level.getBlockState(chestPos) : null;
        *//*?} else {*/
        BlockState chestState = mc.world != null ? mc.world.getBlockState(chestPos) : null;
        /*?}*/
        if (chestState == null || !(chestState.getBlock() instanceof ChestBlock
                || chestState.getBlock() instanceof BarrelBlock
                || chestState.getBlock() instanceof ShulkerBoxBlock)) {
            // Not a container — try scanning nearby for a chest
            /*? if >=26.1 {*//*
            BlockPos alt = findNearbyChest(mc.level, chestPos, 3);
            *//*?} else {*/
            BlockPos alt = findNearbyChest(mc.world, chestPos, 3);
            /*?}*/
            if (alt != null) {
                chestPos = alt;
            } else {
                return false;
            }
        }

        // Rotate toward the container so the server accepts the interaction
        /*? if >=26.1 {*//*
        Vec3 eyePos = mc.player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = mc.player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 chestCenter = Vec3.atCenterOf(chestPos);
        *//*?} else {*/
        Vec3d chestCenter = Vec3d.ofCenter(chestPos);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toChest = chestCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toChest = chestCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toChest.x * toChest.x + toChest.z * toChest.z);
        /*? if >=26.1 {*//*
        float chestYaw = (float) (Mth.atan2(toChest.z, toChest.x)
        *//*?} else {*/
        float chestYaw = (float) (MathHelper.atan2(toChest.z, toChest.x)
        /*?}*/
                * (180.0 / Math.PI)) - 90.0f;
        /*? if >=26.1 {*//*
        float chestPitch = (float) -(Mth.atan2(toChest.y, horizDist)
        *//*?} else {*/
        float chestPitch = (float) -(MathHelper.atan2(toChest.y, horizDist)
        /*?}*/
                * (180.0 / Math.PI));
        if (!tryPrinterLook(1)) {
            return false;
        }
        PlacementEngine.sendLookPacket(mc.player, chestYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(chestPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(chestPitch, -90.0f, 90.0f));
                /*?}*/

        // Release sneak overrides before interacting — if the player is
        // sneaking, interactBlock bypasses block use (chest open) and
        // tries to place the held item instead.
        Runnable restoreSneak = PlacementEngine.releaseForInteraction(mc.player);

        /*? if >=26.1 {*//*
        Direction hitFace = Direction.getApproximateNearest(
        *//*?} else {*/
        Direction hitFace = Direction.getFacing(
        /*?}*/
                (float) -toChest.x, (float) -toChest.y, (float) -toChest.z);
        BlockHitResult hit = new BlockHitResult(
                chestCenter, hitFace, chestPos, false);
        if (!tryPrinterInteraction(4)) {
            restoreSneak.run();
            return false;
        }
        /*? if >=26.1 {*//*
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        *//*?} else {*/
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        /*?}*/

        // Restore sneak overrides if they were active
        restoreSneak.run();

        /*? if >=26.1 {*//*
        return result.consumesAction();
        *//*?} else {*/
        return result.isAccepted();
        /*?}*/
    }

    // Finds nearby chest/barrel/shulker as fallback when exact pos is stale.
    /*? if >=26.1 {*//*
    private BlockPos findNearbyChest(Level world, BlockPos center, int radius) {
    *//*?} else {*/
    private BlockPos findNearbyChest(World world, BlockPos center, int radius) {
    /*?}*/
        if (world == null) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = center.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = center.add(dx, dy, dz);
                    /*?}*/
                    Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof ChestBlock
                            || block instanceof BarrelBlock
                            || block instanceof ShulkerBoxBlock) {
                        /*? if >=26.1 {*//*
                        double d = center.distSqr(pos);
                        *//*?} else {*/
                        double d = center.getSquaredDistance(pos);
                        /*?}*/
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    /*? if >=26.1 {*//*
    private boolean takeNeededItems(Minecraft mc, LocalPlayer player,
    *//*?} else {*/
    private boolean takeNeededItems(MinecraftClient mc, ClientPlayerEntity player,
    /*?}*/
                                 /*? if >=26.1 {*//*
                                 ChestMenu handler) {
                                 *//*?} else {*/
                                 GenericContainerScreenHandler handler) {
                                 /*?}*/
        if (neededItems == null || neededItems.isEmpty()) return true;

        /*? if >=26.1 {*//*
        int chestSlots = handler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = handler.getRows() * 9;
        /*?}*/

        // Pass 0: return unneeded shulkers to the chest
        // Shulker boxes from previous unload cycles may still be in the
        // player's inventory.  Deposit any that no longer contain needed
        // items so they don't waste inventory slots.  In the container
        // screen, player inventory slots start at chestSlots.
        int playerSlotStart = chestSlots;      // main inv (slots 9-35)
        int playerSlotEnd = chestSlots + 36;   // through hotbar (slots 0-8)
        Map<String, Integer> remainingNeed = new LinkedHashMap<>(neededItemCounts);

        for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
            if (blockedRestockSlots.contains(slot)) continue;
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) continue;
            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            /*?}*/
            if (!remainingNeed.containsKey(itemId)) continue;
            remainingNeed.put(itemId, Math.max(0, remainingNeed.get(itemId) - stack.getCount()));
        }

        for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
            if (blockedRestockSlots.contains(slot)) continue;
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) continue;
            if (!isShulkerBox(stack)) continue;
            // Keep shulkers that still have items we need
            if (shulkerContainsNeeded(stack, neededItems)) continue;
            // Deposit this shulker back into the chest
            if (!tryPrinterInventory(2)) {
                return false;
            }
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
            *//*?} else {*/
            mc.interactionManager.clickSlot(
            /*?}*/
                    /*? if >=26.1 {*//*
                    handler.containerId, slot, 0,
                    *//*?} else {*/
                    handler.syncId, slot, 0,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    ContainerInput.QUICK_MOVE, player);
                    *//*?} else {*/
                    SlotActionType.QUICK_MOVE, player);
                    /*?}*/
            beginPendingContainerAction(handler, slot, ContainerTransferLane.RESTOCK);
            chestSyncDelay = 0;
            return false;
        }

        // Reserve a slot for the shulker if one is needed from this chest.
        boolean hasNeededShulker = false;
        for (int slot = 0; slot < chestSlots; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (shouldSkipRestockSlot(slot, stack, remainingNeed)) continue;
            if (isShulkerBox(stack) && shulkerContainsCurrentRestockNeed(stack, remainingNeed)) {
                hasNeededShulker = true;
                break;
            }
        }
        // When a needed shulker is involved, keep one slot for the shulker
        // itself and another for the loose items that must come out of it.
        int reservedFreeSlots = RESTOCK_RESERVED_FREE_SLOTS + (hasNeededShulker ? 2 : 0);
        if (hasNeededShulker) {
            PacketTelemetry.mark("restock found needed-shulker chest=" + supplyTarget
                    + " needed=" + formatNeededItemIds(neededItems)
                    + " reservedSlots=" + reservedFreeSlots);
        }

        // Pass 1: stage one hotbar stack per needed item when possible.
        for (int slot = 0; slot < chestSlots; slot++) {
            if (blockedRestockSlots.contains(slot)) continue;
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty() || isShulkerBox(stack)) continue;

            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            /*?}*/
            if (!neededItems.contains(itemId)) continue;
            if (remainingNeed.getOrDefault(itemId, 0) <= 0) continue;
            if (hasNeededItemInHotbar(player, stack.getItem())) continue;

            int hotbarSlot = findReusableBuilderHotbarSlot(player, itemId);
            if (hotbarSlot < 0) continue;

            if (!tryPrinterInventory(2)) {
                return false;
            }
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
                    handler.containerId, slot, hotbarSlot,
                    ContainerInput.SWAP, player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(
                    handler.syncId, slot, hotbarSlot,
                    SlotActionType.SWAP, player);
            /*?}*/
            remainingNeed.put(itemId, Math.max(0,
                    remainingNeed.getOrDefault(itemId, 0) - stack.getCount()));
            beginPendingContainerAction(handler, slot, ContainerTransferLane.RESTOCK);
            chestSyncDelay = 0;
            return false;
        }

        // Pass 2: if the chest has a needed shulker, prefer taking ONE shulker
        // before greedily pulling more loose stacks. This keeps inventory
        // pressure low and gives the unloading playbook room to work.
        int freeSlotsAfterHotbarStage = 0;
        for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) freeSlotsAfterHotbarStage++;
        }
        if (hasNeededShulker && freeSlotsAfterHotbarStage > 0) {
            for (int slot = 0; slot < chestSlots; slot++) {
                /*? if >=26.1 {*//*
                ItemStack stack = handler.getSlot(slot).getItem();
                *//*?} else {*/
                ItemStack stack = handler.getSlot(slot).getStack();
                /*?}*/
                if (shouldSkipRestockSlot(slot, stack, remainingNeed)) continue;
                if (stack.isEmpty()) continue;
                if (!isShulkerBox(stack)) continue;
                if (!shulkerContainsCurrentRestockNeed(stack, remainingNeed)) continue;
                if (!tryPrinterInventory(2)) {
                    return false;
                }
                PacketTelemetry.mark("restock take needed-shulker slot=" + slot
                        + " chest=" + supplyTarget
                        + " remaining=" + formatNeededItemIds(remainingNeed.keySet()));
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        handler.containerId, slot, 0,
                        *//*?} else {*/
                        handler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, player);
                        /*?}*/
                beginPendingContainerAction(handler, slot, ContainerTransferLane.RESTOCK);
                chestSyncDelay = 0;
                return false; // only one shulker per visit
            }
        }

        // Pass 3: grab loose needed items to support the staged working set.
        for (int slot = 0; slot < chestSlots; slot++) {
            if (blockedRestockSlots.contains(slot)) continue;
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) continue;

            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            /*?}*/
            int shortage = remainingNeed.getOrDefault(itemId, 0);
            if (shortage > 0 && !isShulkerBox(stack)) {
                boolean wouldStack = false;
                int freeSlots = 0;
                for (int pi = playerSlotStart; pi < playerSlotEnd; pi++) {
                    /*? if >=26.1 {*//*
                    ItemStack invStack = handler.getSlot(pi).getItem();
                    *//*?} else {*/
                    ItemStack invStack = handler.getSlot(pi).getStack();
                    /*?}*/
                    if (invStack.isEmpty()) {
                        freeSlots++;
                        continue;
                    }
                    if (
                            /*? if >=26.1 {*//*
                            ItemStack.isSameItem(invStack, stack)
                            *//*?} else {*/
                            ItemStack.areItemsEqual(invStack, stack)
                            /*?}*/
                            /*? if >=26.1 {*//*
                            && invStack.getCount() < invStack.getMaxStackSize()) {
                            *//*?} else {*/
                            && invStack.getCount() < invStack.getMaxCount()) {
                            /*?}*/
                        wouldStack = true;
                        break;
                    }
                }
                if (!wouldStack && freeSlots <= reservedFreeSlots) {
                    continue;
                }
                if (!tryPrinterInventory(2)) {
                    return false;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        handler.containerId, slot, 0,
                        *//*?} else {*/
                        handler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, player);
                        /*?}*/
                remainingNeed.put(itemId, Math.max(0, shortage - stack.getCount()));
                beginPendingContainerAction(handler, slot, ContainerTransferLane.RESTOCK);
                chestSyncDelay = 0;
                return false;
            }
        }

        // Pass 4: grab at most ONE shulker that has needed items
        // Taking only one shulker per chest visit prevents inventory
        // flooding.  The unloading state machine will empty it, then
        // the printer can come back for another shulker if still needed.
        int freeSlotsAfterLoosePulls = 0;
        for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) freeSlotsAfterLoosePulls++;
        }
        for (int slot = 0; slot < chestSlots; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (shouldSkipRestockSlot(slot, stack, remainingNeed)) continue;
            if (stack.isEmpty()) continue;

            if (freeSlotsAfterLoosePulls <= 0) {
                break;
            }
            if (isShulkerBox(stack) && shulkerContainsCurrentRestockNeed(stack, remainingNeed)) {
                if (!tryPrinterInventory(2)) {
                    return false;
                }
                PacketTelemetry.mark("restock take fallback-shulker slot=" + slot
                        + " chest=" + supplyTarget
                        + " remaining=" + formatNeededItemIds(remainingNeed.keySet()));
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        handler.containerId, slot, 0,
                        *//*?} else {*/
                        handler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, player);
                        /*?}*/
                beginPendingContainerAction(handler, slot, ContainerTransferLane.RESTOCK);
                chestSyncDelay = 0;
                return false; // only one shulker per visit
            }
        }
        PacketTelemetry.mark("restock no matching transfer chest=" + supplyTarget
                + " needed=" + formatNeededItemIds(neededItems)
                + " hasNeededShulker=" + hasNeededShulker
                + " blockedSlots=" + blockedRestockSlots.size());
        return true;
    }

    private void addWorkingSetNeed(Item item, int desiredCount, Map<Item, Integer> inventoryCounts) {
        if (item == null || item == Items.AIR) return;
        /*? if >=26.1 {*//*
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        *//*?} else {*/
        String itemId = Registries.ITEM.getId(item).toString();
        /*?}*/
        if (!neededItems.contains(itemId) && neededItems.size() >= RESTOCK_WORKING_SET_TYPES) {
            return;
        }
        int targetCount = restockTargetCount(item, desiredCount);
        int have = inventoryCounts.getOrDefault(item, 0);
        int shortage = Math.max(0, targetCount - have);
        if (shortage <= 0) return;
        neededItems.add(itemId);
        neededItemCounts.merge(itemId, shortage, Integer::sum);
    }

    private int restockTargetCount(Item item, int desiredCount) {
        if (item == null || item == Items.AIR) return 0;
        /*? if >=26.1 {*//*
        ItemStack defaultStack = item.getDefaultInstance();
        *//*?} else {*/
        ItemStack defaultStack = item.getDefaultStack();
        /*?}*/
        String itemId = ItemIdentifier.getItemId(defaultStack);
        if ("minecraft:water_bucket".equals(itemId)
                || "minecraft:lava_bucket".equals(itemId)
                || "minecraft:powder_snow_bucket".equals(itemId)) {
            return 1;
        }
        return Math.min(desiredCount,
                Math.max(1, defaultStack.getMaxCount() * RESTOCK_TARGET_STACKS_PER_ITEM));
    }

    /*? if >=26.1 {*//*
    private boolean hasNeededItemInHotbar(LocalPlayer player, Item item) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).getItem() == item) return true;
        }
        return false;
    }
    *//*?} else {*/
    private boolean hasNeededItemInHotbar(ClientPlayerEntity player, Item item) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == item) return true;
        }
        return false;
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private int findReusableBuilderHotbarSlot(LocalPlayer player, String neededItemId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (isReusableBuilderHotbarStack(stack, neededItemId)) return i;
        }
        return -1;
    }
    *//*?} else {*/
    private int findReusableBuilderHotbarSlot(ClientPlayerEntity player, String neededItemId) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (isReusableBuilderHotbarStack(stack, neededItemId)) return i;
        }
        return -1;
    }
    /*?}*/

    private boolean isReusableBuilderHotbarStack(ItemStack stack, String neededItemId) {
        if (stack == null || stack.isEmpty()) return true;
        if (
                /*? if >=26.1 {*//*
                stack.isDamageableItem()
                *//*?} else {*/
                stack.isDamageable()
                /*?}*/
        ) {
            return false;
        }
        if (isShulkerBox(stack)) return false;
        if (stack.getItem() == Items.TOTEM_OF_UNDYING) return false;
        if (stack.getItem() == Items.ENDER_CHEST) return false;
        if (stack.getItem() == Items.ENDER_PEARL) return false;
        if (stack.getItem() == Items.FIREWORK_ROCKET) return false;
        /*? if >=26.1 {*//*
        String stackId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        *//*?} else {*/
        String stackId = Registries.ITEM.getId(stack.getItem()).toString();
        /*?}*/
        if (neededItems.contains(stackId) || stackId.equals(neededItemId)) return false;
        return stack.getItem() instanceof BlockItem || isBuildUtilityItem(stack.getItem());
    }

    private boolean isBuildUtilityItem(Item item) {
        return item == Items.WATER_BUCKET
                || item == Items.LAVA_BUCKET
                || item == Items.POWDER_SNOW_BUCKET;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean shouldDumpOverflowStack(ItemStack stack, Map<String, Integer> inventoryCounts) {
        if (stack == null || stack.isEmpty()) return false;
        if (isShulkerBox(stack)) return false;
        /*? if >=26.1 {*//*
        if (stack.isDamageableItem()) return false;
        *//*?} else {*/
        if (stack.isDamageable()) return false;
        /*?}*/
        if (MoarMod.getChestManager().getKeepItems().contains(stack.getItem())) return false;

        String itemId = ItemIdentifier.getItemId(stack);
        if ("minecraft:ender_chest".equals(itemId)) return false;

        int reserve = neededItemCounts.getOrDefault(itemId, 0);
        if (reserve <= 0) return true;
        return inventoryCounts.getOrDefault(itemId, 0) - stack.getCount() >= reserve;
    }

    /*? if >=26.1 {*//*
    private void snapshotDumpShulkerSlots(LocalPlayer player) {
        Inventory inv = player.getInventory();
    *//*?} else {*/
    private void snapshotDumpShulkerSlots(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
    /*?}*/
        preDumpShulkerSlots.clear();
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            if (isShulkerBox(inv.getItem(i))) {
            *//*?} else {*/
            if (isShulkerBox(inv.getStack(i))) {
            /*?}*/
                preDumpShulkerSlots.add(i);
            }
        }
    }

    /*? if >=26.1 {*//*
    private int findNewDumpShulkerSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
    *//*?} else {*/
    private int findNewDumpShulkerSlot(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
    /*?}*/
        for (int i = 0; i < 36; i++) {
            if (preDumpShulkerSlots.contains(i)) continue;
            /*? if >=26.1 {*//*
            if (isShulkerBox(inv.getItem(i))) {
            *//*?} else {*/
            if (isShulkerBox(inv.getStack(i))) {
            /*?}*/
                return i;
            }
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private void resumeAfterDump(Minecraft mc, boolean warnPickup) {
    *//*?} else {*/
    private void resumeAfterDump(MinecraftClient mc, boolean warnPickup) {
    /*?}*/
        if (warnPickup && statusMessages) {
            ChatHelper.info("§eOverflow shulker may not have been picked up cleanly.");
        }

        clearPendingContainerAction();
        blockedDumpSlots.clear();
        dumpMode = DumpMode.NONE;
        dumpShulkerPhase = 0;
        dumpShulkerPos = null;
        dumpShulkerSlot = -1;
        dumpShulkerOpenRetries = 0;
        dumpShulkerTransferTimeouts = 0;
        dumpWaitTicks = 0;
        dumpSyncDelay = 0;
        dumpTarget = null;
        preDumpShulkerSlots.clear();

        if (preDumpClearPos != null) {
            PathWalker.walkToNearby(preDumpClearPos, (int) Math.ceil(range));
            autoState = AutoState.WALKING_TO_CLEAR;
        } else {
            autoState = AutoState.CLEARING_AREA;
        }
    }

    private boolean shulkerContainsNeeded(ItemStack shulkerStack, Set<String> needSet) {
        /*? if >=26.1 {*//*
        ItemContainerContents cc = shulkerStack.get(DataComponents.CONTAINER);
        *//*?} else {*/
        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        /*?}*/
        if (cc == null) return false;

        /*? if >=26.1 {*//*
        for (ItemStack inner : cc.nonEmptyItemCopyStream().toList()) {
        *//*?} else {*/
        for (ItemStack inner : cc.iterateNonEmpty()) {
        /*?}*/
            /*? if >=26.1 {*//*
            String innerId = BuiltInRegistries.ITEM.getKey(inner.getItem()).toString();
            *//*?} else {*/
            String innerId = Registries.ITEM.getId(inner.getItem()).toString();
            /*?}*/
            if (needSet.contains(innerId)) return true;
        }
        return false;
    }

    private boolean matchesCurrentBuildPass(SchematicBlockRef ref) {
        if (ref == null) return false;
        if (liquidPass) return ref.liquid();
        if (redstonePass) return ref.redstone();
        return !ref.liquid() && !ref.redstone();
    }

    private boolean matchesCurrentBuildPass(BlockState target) {
        if (target == null) return false;
        boolean isLiquid = isLiquidSource(target);
        boolean isRedstone = !isLiquid && BlockDependency.isRedstoneComponent(target);
        if (liquidPass) return isLiquid;
        if (redstonePass) return isRedstone;
        return !isLiquid && !isRedstone;
    }

    /*? if >=26.1 {*//*
    private int getBottomUpActiveBandTopY(Level world) {
        return getBottomUpActiveBandTopY(world, null);
    }
    *//*?} else {*/
    private int getBottomUpActiveBandTopY(World world) {
        return getBottomUpActiveBandTopY(world, null);
    }
    /*?}*/

    /*? if >=26.1 {*//*
    private int getBottomUpActiveBandTopY(Level world, RenderWindow renderWindow) {
    *//*?} else {*/
    private int getBottomUpActiveBandTopY(World world, RenderWindow renderWindow) {
    /*?}*/
        if (sortMode != SortMode.BOTTOM_UP || world == null) {
            return Integer.MAX_VALUE;
        }

        long tick = getWorldTick();
        int minChunkX = renderWindow != null ? renderWindow.minChunkX() : Integer.MIN_VALUE;
        int maxChunkX = renderWindow != null ? renderWindow.maxChunkX() : Integer.MAX_VALUE;
        int minChunkZ = renderWindow != null ? renderWindow.minChunkZ() : Integer.MIN_VALUE;
        int maxChunkZ = renderWindow != null ? renderWindow.maxChunkZ() : Integer.MAX_VALUE;
        if (cachedActiveBandTick == tick
                && cachedActiveBandMinChunkX == minChunkX
                && cachedActiveBandMaxChunkX == maxChunkX
                && cachedActiveBandMinChunkZ == minChunkZ
                && cachedActiveBandMaxChunkZ == maxChunkZ) {
            return cachedActiveBandTopY;
        }

        Integer lowestActionableY = scanBottomUpActionableY(world, renderWindow, true);
        if (lowestActionableY == null) {
            lowestActionableY = scanBottomUpActionableY(world, renderWindow, false);
        }

        int result = lowestActionableY == null
                ? Integer.MAX_VALUE
                : lowestActionableY + BOTTOM_UP_ACTIVE_BAND_HEIGHT - 1;
        cachedActiveBandTick = tick;
        cachedActiveBandMinChunkX = minChunkX;
        cachedActiveBandMaxChunkX = maxChunkX;
        cachedActiveBandMinChunkZ = minChunkZ;
        cachedActiveBandMaxChunkZ = maxChunkZ;
        cachedActiveBandTopY = result;
        return result;
    }

    /*? if >=26.1 {*//*
    private Integer scanBottomUpActionableY(Level world, RenderWindow renderWindow,
                                            boolean localOnly) {
    *//*?} else {*/
    private Integer scanBottomUpActionableY(World world, RenderWindow renderWindow,
                                            boolean localOnly) {
    /*?}*/
        Integer lowestActionableY = null;
        long tick = getWorldTick();
        for (long key : schematicBlocksByChunk.keySet()) {
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            boolean inWindow = renderWindow != null
                    && chunkX >= renderWindow.minChunkX()
                    && chunkX <= renderWindow.maxChunkX()
                    && chunkZ >= renderWindow.minChunkZ()
                    && chunkZ <= renderWindow.maxChunkZ();
            if (localOnly && !inWindow) continue;
            if (!localOnly && renderWindow != null && inWindow) continue;
            /*? if >=26.1 {*//*
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            *//*?} else {*/
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            /*?}*/

            for (SchematicBlockRef ref : getUnresolvedChunkEntries(world, tick, key)) {
                if (!matchesCurrentBuildPass(ref)) continue;
                if (!isCurrentlyUnresolved(ref, world)) continue;
                BlockPos worldPos = ref.pos();
                if (isBuildTargetCoolingDown(worldPos)) continue;
                if (isNearFailedZone(worldPos)) continue;
                if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) continue;
                if (!BlockDependency.isReadyToPlace(world, worldPos, ref.target())) continue;
                if (!hasBuildPlacementFrontierSupport(worldPos, ref.target(), world)) continue;

                int y = worldPos.getY();
                if (lowestActionableY == null || y < lowestActionableY) {
                    lowestActionableY = y;
                }
            }
        }
        return lowestActionableY;
    }

    private boolean prefersHigherBuildLayers() {
        return sortMode == SortMode.TOP_DOWN;
    }

    private boolean isBetterBuildLayer(int candidateY, int bestY) {
        return prefersHigherBuildLayers() ? candidateY > bestY : candidateY < bestY;
    }

    private boolean isWithinActiveBuildFrontier(int y, int frontierY, int activeBandTopY) {
        if (sortMode == SortMode.BOTTOM_UP && activeBandTopY != Integer.MAX_VALUE) {
            int bandBottomY = activeBandTopY - BOTTOM_UP_ACTIVE_BAND_HEIGHT + 1;
            return y >= bandBottomY && y <= activeBandTopY;
        }
        return y == frontierY;
    }

    /*? if >=26.1 {*//*
    private BuildStagingPlan findBuildStagingPlan(LocalPlayer player, Level world,
                                                  int extraReachBonus) {
    *//*?} else {*/
    private BuildStagingPlan findBuildStagingPlan(ClientPlayerEntity player, World world,
                                                  int extraReachBonus) {
    /*?}*/
        if (player == null || world == null) return null;

        /*? if >=26.1 {*//*
        BlockPos playerBlockPos = player.blockPosition();
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        BlockPos playerBlockPos = player.getBlockPos();
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        BlockPos playerBlockPos = player.getBlockPos();
        Vec3d playerPos = player.getPos();
        /*?}*/
        long tick = getWorldTick();
        if (cachedBuildStagingPlanTick != Long.MIN_VALUE
                && tick - cachedBuildStagingPlanTick < STAGING_PLAN_CACHE_TTL
                && extraReachBonus == cachedBuildStagingPlanExtraReachBonus
                && playerBlockPos.equals(cachedBuildStagingPlanPlayerPos)) {
            return cachedBuildStagingPlan;
        }
        RenderWindow renderWindow = getRenderWindow(playerBlockPos);
        int activeBandTopY = getBottomUpActiveBandTopY(world, renderWindow);
        boolean preferExterior = shouldPreferExteriorBuildTargets(playerBlockPos);
        List<StagingTargetCandidate> repairVisibleExteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);
        List<StagingTargetCandidate> repairVisibleInteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);
        List<StagingTargetCandidate> repairFallbackExteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);
        List<StagingTargetCandidate> repairFallbackInteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);
        List<StagingTargetCandidate> visibleExteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);
        List<StagingTargetCandidate> visibleInteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);
        List<StagingTargetCandidate> fallbackExteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);
        List<StagingTargetCandidate> fallbackInteriorCandidates = new ArrayList<>(STAGING_PLAN_MAX_CANDIDATES);

        for (long key : schematicBlocksByChunk.keySet()) {
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            /*? if >=26.1 {*//*
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            *//*?} else {*/
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            /*?}*/

            for (SchematicBlockRef ref : getUnresolvedChunkEntries(world, tick, key)) {
                if (!matchesCurrentBuildPass(ref)) continue;
                if (!isCurrentlyUnresolved(ref, world)) continue;

                BlockPos worldPos = ref.pos();
                if (worldPos.getY() > activeBandTopY) continue;
                if (isBuildTargetCoolingDown(worldPos)) continue;
                if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) continue;
                if (!BlockDependency.isReadyToPlace(world, worldPos, ref.target())) continue;
                if (!hasBuildPlacementFrontierSupport(worldPos, ref.target(), world)) continue;
                if (isNearFailedZone(worldPos)) continue;

                /*? if >=26.1 {*//*
                double targetDist = playerPos.distanceToSqr(Vec3.atCenterOf(worldPos));
                *//*?} else {*/
                double targetDist = playerPos.squaredDistanceTo(Vec3d.ofCenter(worldPos));
	                /*?}*/
	                boolean visible = isWithinRenderWindow(renderWindow, worldPos.getX(), worldPos.getZ());
	                boolean exterior = isExteriorBuildTarget(worldPos);
	                boolean repair = repairPriorityTargets.contains(worldPos);
	                insertStagingCandidate(
	                        visible
	                                ? (repair
	                                ? (exterior ? repairVisibleExteriorCandidates : repairVisibleInteriorCandidates)
	                                : (exterior ? visibleExteriorCandidates : visibleInteriorCandidates))
	                                : (repair
	                                ? (exterior ? repairFallbackExteriorCandidates : repairFallbackInteriorCandidates)
	                                : (exterior ? fallbackExteriorCandidates : fallbackInteriorCandidates)),
	                        ref,
	                        targetDist);
	            }
	        }
	        BuildStagingPlan result = findBestStagingPlanByBuildSide(
	                preferExterior,
	                repairVisibleExteriorCandidates,
	                repairVisibleInteriorCandidates,
	                repairFallbackExteriorCandidates,
	                repairFallbackInteriorCandidates,
	                player,
	                world,
	                playerBlockPos,
	                playerPos,
	                extraReachBonus);
	        if (result == null) {
	            result = findBestStagingPlanByBuildSide(
	                preferExterior,
	                visibleExteriorCandidates,
	                visibleInteriorCandidates,
	                fallbackExteriorCandidates,
	                fallbackInteriorCandidates,
	                player,
	                world,
	                playerBlockPos,
	                playerPos,
	                extraReachBonus);
	        }
	        cachedBuildStagingPlanTick = tick;
        /*? if >=26.1 {*//*
        cachedBuildStagingPlanPlayerPos = playerBlockPos.immutable();
        *//*?} else {*/
        cachedBuildStagingPlanPlayerPos = playerBlockPos.toImmutable();
        /*?}*/
        cachedBuildStagingPlanExtraReachBonus = extraReachBonus;
        cachedBuildStagingPlan = result;
	        return result;
	    }

    /*? if >=26.1 {*//*
    private BuildStagingPlan findBestStagingPlanByBuildSide(boolean preferExterior,
                                                            List<StagingTargetCandidate> visibleExterior,
                                                            List<StagingTargetCandidate> visibleInterior,
                                                            List<StagingTargetCandidate> fallbackExterior,
                                                            List<StagingTargetCandidate> fallbackInterior,
                                                            LocalPlayer player, Level world,
                                                            BlockPos playerBlockPos, Vec3 playerPos,
                                                            int extraReachBonus) {
    *//*?} else if >=1.21.10 {*//*
    private BuildStagingPlan findBestStagingPlanByBuildSide(boolean preferExterior,
                                                            List<StagingTargetCandidate> visibleExterior,
                                                            List<StagingTargetCandidate> visibleInterior,
                                                            List<StagingTargetCandidate> fallbackExterior,
                                                            List<StagingTargetCandidate> fallbackInterior,
                                                            ClientPlayerEntity player, World world,
                                                            BlockPos playerBlockPos, Vec3d playerPos,
                                                            int extraReachBonus) {
    *//*?} else {*/
    private BuildStagingPlan findBestStagingPlanByBuildSide(boolean preferExterior,
                                                            List<StagingTargetCandidate> visibleExterior,
                                                            List<StagingTargetCandidate> visibleInterior,
                                                            List<StagingTargetCandidate> fallbackExterior,
                                                            List<StagingTargetCandidate> fallbackInterior,
                                                            ClientPlayerEntity player, World world,
                                                            BlockPos playerBlockPos, Vec3d playerPos,
                                                            int extraReachBonus) {
    /*?}*/
        BuildStagingPlan result;
        if (preferExterior) {
            result = evaluateStagingCandidates(visibleExterior, player, world, playerBlockPos, playerPos, extraReachBonus);
            if (result != null) return result;
            result = evaluateStagingCandidates(fallbackExterior, player, world, playerBlockPos, playerPos, extraReachBonus);
            if (result != null) return result;
            result = evaluateStagingCandidates(visibleInterior, player, world, playerBlockPos, playerPos, extraReachBonus);
            if (result != null) return result;
            return evaluateStagingCandidates(fallbackInterior, player, world, playerBlockPos, playerPos, extraReachBonus);
        }
        result = evaluateStagingCandidates(visibleInterior, player, world, playerBlockPos, playerPos, extraReachBonus);
        if (result != null) return result;
        result = evaluateStagingCandidates(visibleExterior, player, world, playerBlockPos, playerPos, extraReachBonus);
        if (result != null) return result;
        result = evaluateStagingCandidates(fallbackInterior, player, world, playerBlockPos, playerPos, extraReachBonus);
        if (result != null) return result;
        return evaluateStagingCandidates(fallbackExterior, player, world, playerBlockPos, playerPos, extraReachBonus);
    }

    private boolean shouldPreferExteriorBuildTargets(BlockPos playerBlockPos) {
        if (playerBlockPos == null || schematic == null || anchor == null) {
            return false;
        }
        if (failedZones.size() >= EXTERIOR_FAILED_ZONE_BUDGET) {
            return false;
        }
        int minX = anchor.getX();
        int maxX = anchor.getX() + schematic.getSizeX() - 1;
        int minZ = anchor.getZ();
        int maxZ = anchor.getZ() + schematic.getSizeZ() - 1;
        return playerBlockPos.getX() < minX
                || playerBlockPos.getX() > maxX
                || playerBlockPos.getZ() < minZ
                || playerBlockPos.getZ() > maxZ;
    }

    private boolean isExteriorBuildTarget(BlockPos worldPos) {
        if (worldPos == null || schematic == null || anchor == null) {
            return false;
        }
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) {
            return false;
        }
        if (hasExteriorAirRay(sx, sy, sz, 1, 0)
                || hasExteriorAirRay(sx, sy, sz, -1, 0)
                || hasExteriorAirRay(sx, sy, sz, 0, 1)
                || hasExteriorAirRay(sx, sy, sz, 0, -1)) {
            return true;
        }
        int edgeDistance = Math.min(
                Math.min(sx, schematic.getSizeX() - 1 - sx),
                Math.min(sz, schematic.getSizeZ() - 1 - sz));
        return edgeDistance <= EXTERIOR_BUILD_SHELL_DEPTH;
    }

    private boolean hasExteriorAirRay(int sx, int sy, int sz, int dx, int dz) {
        int x = sx + dx;
        int z = sz + dz;
        int maxSteps = schematic.getSizeX() + schematic.getSizeZ() + 4;
        for (int steps = 0; steps < maxSteps; steps++) {
            if (!schematic.contains(x, sy, z)) {
                return true;
            }
            BlockState state = schematic.getBlockState(x, sy, z);
            if (state != null && !state.isAir()) {
                return false;
            }
            x += dx;
            z += dz;
        }
        return false;
    }

    private void insertStagingCandidate(List<StagingTargetCandidate> candidates,
                                        SchematicBlockRef ref,
                                        double targetDist) {
        if (candidates == null || ref == null) return;
        int insertAt = 0;
        while (insertAt < candidates.size()) {
            StagingTargetCandidate existing = candidates.get(insertAt);
            int comparison = compareStagingCandidates(
                    ref.pos(), targetDist, existing.ref().pos(), existing.targetDist());
            if (comparison < 0) {
                break;
            }
            insertAt++;
        }
        if (insertAt >= STAGING_PLAN_MAX_CANDIDATES) {
            return;
        }
        candidates.add(insertAt, new StagingTargetCandidate(ref, targetDist));
        if (candidates.size() > STAGING_PLAN_MAX_CANDIDATES) {
            candidates.remove(candidates.size() - 1);
        }
    }

    private int compareStagingCandidates(BlockPos candidatePos, double candidateDist,
                                         BlockPos existingPos, double existingDist) {
        if (candidatePos != null && existingPos != null && candidatePos.getY() != existingPos.getY()) {
            if (isBetterBuildLayer(candidatePos.getY(), existingPos.getY())) {
                return -1;
            }
            if (isBetterBuildLayer(existingPos.getY(), candidatePos.getY())) {
                return 1;
            }
        }
        return Double.compare(candidateDist, existingDist);
    }

    /*? if >=26.1 {*//*
    private BuildStagingPlan evaluateStagingCandidates(List<StagingTargetCandidate> candidates,
                                                       LocalPlayer player, Level world,
                                                       BlockPos playerBlockPos, Vec3 playerPos,
                                                       int extraReachBonus) {
    *//*?} else if >=1.21.10 {*//*
    private BuildStagingPlan evaluateStagingCandidates(List<StagingTargetCandidate> candidates,
                                                       ClientPlayerEntity player, World world,
                                                       BlockPos playerBlockPos, Vec3d playerPos,
                                                       int extraReachBonus) {
    *//*?} else {*/
    private BuildStagingPlan evaluateStagingCandidates(List<StagingTargetCandidate> candidates,
                                                       ClientPlayerEntity player, World world,
                                                       BlockPos playerBlockPos, Vec3d playerPos,
                                                       int extraReachBonus) {
    /*?}*/
        if (candidates == null || candidates.isEmpty() || player == null || world == null) {
            return null;
        }

        BuildStagingPlan bestPlan = null;
        int bestY = prefersHigherBuildLayers() ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        double bestScore = Double.MAX_VALUE;

        for (StagingTargetCandidate candidate : candidates) {
            if (candidate == null || candidate.ref() == null) continue;
            BlockPos worldPos = candidate.ref().pos();
            BlockPos standPos = findStandingPosition(worldPos, world, player);
            boolean approachOnly = false;
            if (standPos == null) {
                standPos = findApproachStandingPosition(worldPos, world, player, extraReachBonus);
                approachOnly = true;
            }
            standPos = sanitizeBuildStandingPosition(worldPos, standPos, playerBlockPos);
            if (standPos == null) continue;
            boolean canBuildFromStand = canBuildTargetFromStagingPosition(
                    standPos, worldPos, world, player);
            boolean canProbeFromStand = canBuildFromStand
                    && canProbeBuildTargetPacketFromStagingPosition(
                            standPos, worldPos, world, player);
            if (!canBuildFromStand || !canProbeFromStand) {
                continue;
            }
            if (isNearFailedZone(standPos)) continue;
            if (canProbeFromStand
                    && isNearBuildStagingPosition(playerPos, standPos)
                    && !canBuildSpecificTargetFromCurrentStance(player, world, worldPos)) {
                continue;
            }

            /*? if >=26.1 {*//*
            double standDist = playerPos.distanceToSqr(Vec3.atCenterOf(standPos));
            *//*?} else {*/
            double standDist = playerPos.squaredDistanceTo(Vec3d.ofCenter(standPos));
            /*?}*/
            double score = standDist
                    + candidate.targetDist() * 0.15
                    + Math.abs(worldPos.getY() - standPos.getY()) * 12.0
                    + (approachOnly ? 96.0 : 0.0)
                    + (canProbeFromStand ? 0.0 : 160.0);
            if (bestPlan == null
                    || isBetterBuildLayer(worldPos.getY(), bestY)
                    || (worldPos.getY() == bestY && score < bestScore)) {
                bestPlan = new BuildStagingPlan(worldPos, standPos, approachOnly);
                bestY = worldPos.getY();
                bestScore = score;
            }
        }
        return bestPlan;
    }

    /*? if >=26.1 {*//*
    private boolean canProbeBuildTargetFromStagingPosition(BlockPos standPos,
                                                           BlockPos targetPos,
                                                           Level world,
                                                           LocalPlayer player) {
    *//*?} else {*/
    private boolean canProbeBuildTargetFromStagingPosition(BlockPos standPos,
                                                           BlockPos targetPos,
                                                           World world,
                                                           ClientPlayerEntity player) {
    /*?}*/
        return canBuildTargetFromStagingPosition(standPos, targetPos, world, player)
                && canProbeBuildTargetPacketFromStagingPosition(standPos, targetPos, world, player);
    }

    /*? if >=26.1 {*//*
    private boolean canBuildTargetFromStagingPosition(BlockPos standPos,
                                                      BlockPos targetPos,
                                                      Level world,
                                                      LocalPlayer player) {
    *//*?} else {*/
    private boolean canBuildTargetFromStagingPosition(BlockPos standPos,
                                                      BlockPos targetPos,
                                                      World world,
                                                      ClientPlayerEntity player) {
    /*?}*/
        if (standPos == null || targetPos == null || world == null || player == null) {
            return false;
        }
        BlockState target = getDesiredBuildStateAt(targetPos);
        if (target == null
                || (!isPlaceable(target) && !isLiquidSource(target))
                || !matchesCurrentBuildPass(target)
                || isBuildTargetCoolingDown(targetPos)
                || wouldPlacementIntersectStandingBody(standPos, targetPos)
                || isEffectivelyPlaced(world.getBlockState(targetPos), target)) {
            return false;
        }
        if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, targetPos)) {
            return false;
        }
        if (!BlockDependency.isReadyToPlace(world, targetPos, target)) {
            return false;
        }
        if (!hasBuildPlacementFrontierSupport(targetPos, target, world)) {
            return false;
        }
        if (!isCandidateStableForPlacement(standPos, targetPos)) {
            return false;
        }
        double reachSq = range * range;
        /*? if >=26.1 {*//*
        Vec3 eyePos = Vec3.atCenterOf(standPos).add(0.0, 1.12, 0.0);
        return eyePos.distanceToSqr(Vec3.atCenterOf(targetPos)) <= reachSq;
        *//*?} else {*/
        Vec3d eyePos = Vec3d.ofCenter(standPos).add(0.0, 1.12, 0.0);
        return eyePos.squaredDistanceTo(Vec3d.ofCenter(targetPos)) <= reachSq;
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private boolean canProbeBuildTargetPacketFromStagingPosition(BlockPos standPos,
                                                                 BlockPos targetPos,
                                                                 Level world,
                                                                 LocalPlayer player) {
    *//*?} else {*/
    private boolean canProbeBuildTargetPacketFromStagingPosition(BlockPos standPos,
                                                                 BlockPos targetPos,
                                                                 World world,
                                                                 ClientPlayerEntity player) {
    /*?}*/
        BlockState target = getDesiredBuildStateAt(targetPos);
        return target != null && PlacementEngine.canPlaceFromStandingPosition(
                targetPos, target, standPos, player, world,
                createBuildSupportFilter(targetPos, world, standPos));
    }

    /*? if >=26.1 {*//*
    private boolean startAccessMiningToBuildPocket(Minecraft mc, BlockPos targetPos) {
    *//*?} else {*/
    private boolean startAccessMiningToBuildPocket(MinecraftClient mc, BlockPos targetPos) {
    /*?}*/
        if (mc == null || targetPos == null) return false;
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return false;
        if (!isBuildTargetStillActionable(targetPos, mc.level, true)) {
            PacketTelemetry.mark("build access stale-target target=" + posLabel(targetPos));
            return false;
        }
        BuildAccessTarget access = findBuildAccessTarget(targetPos, mc.level, mc.player);
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return false;
        if (!isBuildTargetStillActionable(targetPos, mc.world, true)) {
            PacketTelemetry.mark("build access stale-target target=" + posLabel(targetPos));
            return false;
        }
        BuildAccessTarget access = findBuildAccessTarget(targetPos, mc.world, mc.player);
        /*?}*/
        if (access == null || access.feetPos() == null) {
            return false;
        }
        /*? if >=26.1 {*//*
        seedAccessRepairTargets(mc.player.blockPosition(), access, targetPos, mc.level);
        *//*?} else {*/
        seedAccessRepairTargets(mc.player.getBlockPos(), access, targetPos, mc.world);
        /*?}*/

        invalidateBuildStagingPlanCache();
        noProgressTicks = 0;
        walkAttemptCooldown = NO_PROGRESS_WALK_RECHECK_TICKS;
        triedPlacementWalk = false;
        /*? if >=26.1 {*//*
        lastWalkTargetZone = targetPos.immutable();
        lastWalkStandPos = access.feetPos().immutable();
        lastWalkApproachPos = access.feetPos().immutable();
        *//*?} else {*/
        lastWalkTargetZone = targetPos.toImmutable();
        lastWalkStandPos = access.feetPos().toImmutable();
        lastWalkApproachPos = access.feetPos().toImmutable();
        /*?}*/

        if (access.clearPos() != null) {
            PacketTelemetry.mark("build access clear target=" + posLabel(access.clearPos())
                    + " feet=" + posLabel(access.feetPos())
                    + " zone=" + posLabel(targetPos));
            accessClearInProgress = true;
            boolean started;
            /*? if >=26.1 {*//*
            started = startClearWalk(mc.player, mc.level, access.clearPos());
            *//*?} else {*/
            started = startClearWalk(mc.player, mc.world, access.clearPos());
            /*?}*/
            if (started) {
                autoState = AutoState.WALKING_TO_CLEAR;
            } else {
                autoState = AutoState.CLEARING_AREA;
            }
            return true;
        }

        PacketTelemetry.mark("build access walk feet=" + posLabel(access.feetPos())
                + " zone=" + posLabel(targetPos));
        PathWalker.setBreakingAllowed(false);
        PathWalker.walkTo(access.feetPos());
        autoState = AutoState.WALKING_TO_BUILD;
        return true;
    }

    /*? if >=26.1 {*//*
    private BuildAccessTarget findBuildAccessTarget(BlockPos targetPos, Level world, LocalPlayer player) {
    *//*?} else {*/
    private BuildAccessTarget findBuildAccessTarget(BlockPos targetPos, World world, ClientPlayerEntity player) {
    /*?}*/
        if (targetPos == null || world == null || player == null
                || schematic == null || anchor == null) {
            return null;
        }
        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        BlockPos playerBlockPos = player.blockPosition();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        BlockPos playerBlockPos = player.getBlockPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        BlockPos playerBlockPos = player.getBlockPos();
        /*?}*/

        BuildAccessTarget best = null;
        double bestScore = Double.MAX_VALUE;
        BuildAccessTarget bestReachable = null;
        double bestReachableScore = Double.MAX_VALUE;
        int rejectedFeet = 0;
        int rejectedHead = 0;
        int rejectedGround = 0;
        int rejectedClear = 0;
        int rejectedPath = 0;
        int rejectedRange = 0;
        int considered = 0;
        int radius = ACCESS_MINING_SEARCH_RADIUS;
        int minAccessY = Math.min(targetPos.getY(), playerBlockPos.getY());
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos feetPos = new BlockPos(
                            targetPos.getX() + dx,
                            targetPos.getY() + dy,
                            targetPos.getZ() + dz);
                    if (feetPos.getY() < minAccessY) continue;
                    if (!isBuildAccessCell(feetPos, world)) {
                        rejectedFeet++;
                        continue;
                    }
                    /*? if >=26.1 {*//*
                    BlockPos headPos = feetPos.above();
                    BlockPos groundPos = feetPos.below();
                    *//*?} else {*/
                    BlockPos headPos = feetPos.up();
                    BlockPos groundPos = feetPos.down();
                    /*?}*/
                    if (!isBuildAccessCell(headPos, world)) {
                        rejectedHead++;
                        continue;
                    }
                    if (!hasAccessMiningGround(groundPos, world)) {
                        rejectedGround++;
                        continue;
                    }
                    considered++;
                    boolean directProbe = canProbeBuildTargetFromStagingPosition(feetPos, targetPos, world, player);

                    BlockPos clearPos = null;
                    BlockState feetState = world.getBlockState(feetPos);
                    BlockState headState = world.getBlockState(headPos);
                    if (isMovementBlocking(feetState)) {
                        clearPos = feetPos;
                    } else if (isMovementBlocking(headState)) {
                        clearPos = headPos;
                    }
                    if (clearPos != null
                            && (failedClearTargets.contains(clearPos)
                            || clearPos.getY() < minAccessY
                            || isStorageBlacklistedForClearing(
                                    world.getBlockState(clearPos), clearPos,
                                    getProtectedStoragePositions()))) {
                        rejectedClear++;
                        continue;
                    }
                    AccessPathProbe pathProbe = findAccessPathProbe(playerBlockPos, feetPos, world);
                    if (pathProbe.blocked()) {
                        rejectedPath++;
                        continue;
                    }
                    if (clearPos == null) {
                        clearPos = pathProbe.clearPos();
                    }
                    if (clearPos != null && clearPos.getY() < minAccessY) {
                        rejectedClear++;
                        continue;
                    }

                    /*? if >=26.1 {*//*
                    Vec3 eyePos = Vec3.atCenterOf(feetPos).add(0.0, 1.62, 0.0);
                    double targetDist = eyePos.distanceToSqr(Vec3.atCenterOf(targetPos));
                    double playerDist = playerPos.distanceToSqr(Vec3.atCenterOf(feetPos));
                    *//*?} else {*/
                    Vec3d eyePos = Vec3d.ofCenter(feetPos).add(0.0, 1.62, 0.0);
                    double targetDist = eyePos.squaredDistanceTo(Vec3d.ofCenter(targetPos));
                    double playerDist = playerPos.squaredDistanceTo(Vec3d.ofCenter(feetPos));
                    /*?}*/
                    // Prefer dry feet/head, but only mildly. Water penalty
                    // is intentionally smaller than the Y-diff cost (18) so a
                    // wet same-Y stance beats a dry above-Y stance. See
                    // BUILD_ACCESS_WATER_PENALTY constant for rationale.
                    boolean wetFeet = !world.getBlockState(feetPos).getFluidState().isEmpty();
                    boolean wetHead = !world.getBlockState(headPos).getFluidState().isEmpty();
                    double waterPenalty = (wetFeet ? BUILD_ACCESS_WATER_PENALTY : 0.0)
                            + (wetHead ? BUILD_ACCESS_WATER_PENALTY : 0.0);
                    double score = targetDist * 2.0
                            + playerDist * 0.35
                            + Math.abs(feetPos.getY() - targetPos.getY()) * 18.0
                            + (clearPos != null ? 8.0 : 0.0)
                            + waterPenalty;
                    if (directProbe) {
                        if (targetDist > range * range) {
                            rejectedRange++;
                            continue;
                        }
                        if (score < bestScore) {
                            bestScore = score;
                            best = new BuildAccessTarget(feetPos, clearPos);
                        }
                    } else if (clearPos != null) {
                        // Only accept indirect access when there is real
                        // clearing work. Feet-only fallbacks loop after arrival.
                        double relaxedScore = playerDist * 0.55
                                + targetDist * 0.35
                                + Math.abs(feetPos.getY() - targetPos.getY()) * 14.0
                                + (clearPos != null ? 8.0 : 0.0)
                                + waterPenalty
                                + 24.0;
                        if (relaxedScore < bestReachableScore) {
                            bestReachableScore = relaxedScore;
                            bestReachable = new BuildAccessTarget(feetPos, clearPos);
                        }
                    }
                }
            }
        }
        if (best == null && bestReachable == null) {
            // Fix #10: surface considered count to caller so the soft-no-access
            // cooldown can escalate for considered=0 (entombed) zones.
            lastBuildAccessRejectConsidered = considered;
            PacketTelemetry.mark("build access reject zone=" + posLabel(targetPos)
                    + " considered=" + considered
                    + " feet=" + rejectedFeet
                    + " head=" + rejectedHead
                    + " ground=" + rejectedGround
                    + " clear=" + rejectedClear
                    + " path=" + rejectedPath
                    + " range=" + rejectedRange
                    + " player=" + posLabel(playerBlockPos));
        }
        return best != null ? best : bestReachable;
    }

    /*? if >=26.1 {*//*
    private boolean isBuildAccessCell(BlockPos pos, Level world) {
    *//*?} else {*/
    private boolean isBuildAccessCell(BlockPos pos, World world) {
    /*?}*/
        if (pos == null || world == null || schematic == null || anchor == null) return false;
        int sx = pos.getX() - anchor.getX();
        int sy = pos.getY() - anchor.getY();
        int sz = pos.getZ() - anchor.getZ();
        if (schematic.contains(sx, sy, sz)) {
            BlockState expected = schematic.getBlockState(sx, sy, sz);
            if (expected == null || !expected.isAir()) return false;
        }
        BlockState current = world.getBlockState(pos);
        // NOTE: water/lava cells are accepted here so sea-level builds (e.g.
        // 2b2t y=63 floors) have any feet candidates at all. The caller in
        // findBuildAccessTarget penalises wet positions via
        // BUILD_ACCESS_WATER_PENALTY so dry land is still preferred when
        // available. Hard-rejecting fluids previously caused
        // "considered=0 feet=N" telemetry whenever the schematic floor sat
        // in the ambient ocean.
        return !isStorageBlacklistedForClearing(current, pos, getProtectedStoragePositions());
    }

    /*? if >=26.1 {*//*
    private AccessPathProbe findAccessPathProbe(BlockPos fromFeet, BlockPos toFeet, Level world) {
    *//*?} else {*/
    private AccessPathProbe findAccessPathProbe(BlockPos fromFeet, BlockPos toFeet, World world) {
    /*?}*/
        if (fromFeet == null || toFeet == null || world == null) {
            return new AccessPathProbe(null, true);
        }
        int dx = toFeet.getX() - fromFeet.getX();
        int dy = toFeet.getY() - fromFeet.getY();
        int dz = toFeet.getZ() - fromFeet.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos feetPos = new BlockPos(
                    fromFeet.getX() + (int) Math.round(dx * t),
                    fromFeet.getY() + (int) Math.round(dy * t),
                    fromFeet.getZ() + (int) Math.round(dz * t));
            BlockPos clearPos = firstAccessBlocker(feetPos, world);
            if (clearPos == null) {
                continue;
            }
            if (failedClearTargets.contains(clearPos)) {
                return new AccessPathProbe(null, true);
            }
            if (!isAccessMiningCarvableCell(clearPos, world)
                    && !isTemporarilyCarvableAccessCell(clearPos, world)) {
                if (isCorrectSchematicBlock(clearPos, world)) {
                    PacketTelemetry.mark("build access blocked-by-built blocker=" + posLabel(clearPos)
                            + " feet=" + posLabel(toFeet));
                }
                return new AccessPathProbe(null, true);
            }
            return new AccessPathProbe(clearPos, false);
        }
        return new AccessPathProbe(null, false);
    }

    /*? if >=26.1 {*//*
    private BlockPos firstAccessBlocker(BlockPos feetPos, Level world) {
    *//*?} else {*/
    private BlockPos firstAccessBlocker(BlockPos feetPos, World world) {
    /*?}*/
        if (feetPos == null || world == null) return null;
        if (isMovementBlocking(world.getBlockState(feetPos))) {
            return feetPos;
        }
        /*? if >=26.1 {*//*
        BlockPos headPos = feetPos.above();
        *//*?} else {*/
        BlockPos headPos = feetPos.up();
        /*?}*/
        if (isMovementBlocking(world.getBlockState(headPos))) {
            return headPos;
        }
        return null;
    }

    /*? if >=26.1 {*//*
    private boolean isAccessMiningCarvableCell(BlockPos pos, Level world) {
    *//*?} else {*/
    private boolean isAccessMiningCarvableCell(BlockPos pos, World world) {
    /*?}*/
        if (pos == null || world == null || schematic == null || anchor == null) return false;
        int sx = pos.getX() - anchor.getX();
        int sy = pos.getY() - anchor.getY();
        int sz = pos.getZ() - anchor.getZ();
        if (schematic.contains(sx, sy, sz)) {
            BlockState expected = schematic.getBlockState(sx, sy, sz);
            if (expected == null || !expected.isAir()) return false;
        }
        BlockState current = world.getBlockState(pos);
        if (!current.getFluidState().isEmpty()) return false;
        return !isStorageBlacklistedForClearing(current, pos, getProtectedStoragePositions());
    }

    /*? if >=26.1 {*//*
    private boolean isTemporarilyCarvableAccessCell(BlockPos pos, Level world) {
    *//*?} else {*/
    private boolean isTemporarilyCarvableAccessCell(BlockPos pos, World world) {
    /*?}*/
        if (pos == null || world == null || schematic == null || anchor == null) return false;
        int sx = pos.getX() - anchor.getX();
        int sy = pos.getY() - anchor.getY();
        int sz = pos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) return false;
        BlockState expected = schematic.getBlockState(sx, sy, sz);
        if (expected == null || expected.isAir()) return false;
        BlockState current = world.getBlockState(pos);
        if (!current.getFluidState().isEmpty()) return false;
        if (current.isAir()) return false;
        if (isEffectivelyPlaced(current, expected)) return false;
        if (isStorageBlacklistedForClearing(current, pos, getProtectedStoragePositions())) return false;
        return isMovementBlocking(current);
    }

    /*? if >=26.1 {*//*
    private boolean hasAccessMiningGround(BlockPos groundPos, Level world) {
    *//*?} else {*/
    private boolean hasAccessMiningGround(BlockPos groundPos, World world) {
    /*?}*/
        if (groundPos == null || world == null) return false;
        BlockState state = world.getBlockState(groundPos);
        if (!state.getCollisionShape(world, groundPos).isEmpty()) {
            return true;
        }
        // Water/lava count as "ground" for access purposes — the player can
        // tread water and jump up to placed blocks. Without this, sea-level
        // builds reject every candidate at hasAccessMiningGround once
        // isBuildAccessCell starts admitting wet feet positions.
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        return isCorrectSchematicBlock(groundPos, world);
    }

    // NAVIGATION HELPERS

    /*? if >=26.1 {*//*
    private BlockPos findNextBuildZone(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findNextBuildZone(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) return null;

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
	        RenderWindow renderWindow =
	                getRenderWindow(/*? if >=26.1 {*//* player.blockPosition() *//*?} else {*/player.getBlockPos()/*?}*/);
	        boolean preferExterior =
	                shouldPreferExteriorBuildTargets(/*? if >=26.1 {*//* player.blockPosition() *//*?} else {*/player.getBlockPos()/*?}*/);
	        BlockPos bestRepairVisibleExterior = null;
	        double bestRepairVisibleExteriorDist = Double.MAX_VALUE;
	        BlockPos bestRepairVisibleInterior = null;
	        double bestRepairVisibleInteriorDist = Double.MAX_VALUE;
	        BlockPos bestRepairFallbackExterior = null;
	        double bestRepairFallbackExteriorDist = Double.MAX_VALUE;
	        BlockPos bestRepairFallbackInterior = null;
	        double bestRepairFallbackInteriorDist = Double.MAX_VALUE;
	        BlockPos bestVisibleExterior = null;
	        double bestVisibleExteriorDist = Double.MAX_VALUE;
	        BlockPos bestVisibleInterior = null;
	        double bestVisibleInteriorDist = Double.MAX_VALUE;
	        BlockPos bestFallbackExterior = null;
	        double bestFallbackExteriorDist = Double.MAX_VALUE;
	        BlockPos bestFallbackInterior = null;
	        double bestFallbackInteriorDist = Double.MAX_VALUE;
	        int activeBandTopY = getBottomUpActiveBandTopY(world, renderWindow);

        long tick = getWorldTick();
        for (long key : schematicBlocksByChunk.keySet()) {
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            /*? if >=26.1 {*//*
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            *//*?} else {*/
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            /*?}*/

            for (SchematicBlockRef ref : getUnresolvedChunkEntries(world, tick, key)) {
                if (!matchesCurrentBuildPass(ref)) continue;
                if (!isCurrentlyUnresolved(ref, world)) continue;

                BlockPos worldPos = ref.pos();
                if (worldPos.getY() > activeBandTopY) continue;
                if (isBuildTargetCoolingDown(worldPos)) continue;

                // Prefer blocks at the lowest or highest unbuilt Y-level
	                boolean visible = isWithinRenderWindow(renderWindow, worldPos.getX(), worldPos.getZ());
	                boolean exterior = isExteriorBuildTarget(worldPos);
	                boolean repair = repairPriorityTargets.contains(worldPos);
	                BlockPos currentBest = visible
	                        ? (repair
	                        ? (exterior ? bestRepairVisibleExterior : bestRepairVisibleInterior)
	                        : (exterior ? bestVisibleExterior : bestVisibleInterior))
	                        : (repair
	                        ? (exterior ? bestRepairFallbackExterior : bestRepairFallbackInterior)
	                        : (exterior ? bestFallbackExterior : bestFallbackInterior));
	                if (sortMode == SortMode.TOP_DOWN) {
	                    if (currentBest != null && worldPos.getY() < currentBest.getY()) continue;
	                } else {
                    if (currentBest != null && worldPos.getY() > currentBest.getY()) continue;
                }

                if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) continue;
                if (!BlockDependency.isReadyToPlace(world, worldPos, ref.target())) continue;
                if (!hasBuildPlacementFrontierSupport(worldPos, ref.target(), world)) continue;
                if (isNearFailedZone(worldPos)) continue;

                /*? if >=26.1 {*//*
                double dist = playerPos.distanceToSqr(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
                *//*?} else {*/
	                double dist = playerPos.squaredDistanceTo(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
	                /*?}*/
	                if (repair && visible && exterior) {
	                    if (dist < bestRepairVisibleExteriorDist) {
	                        bestRepairVisibleExteriorDist = dist;
	                        bestRepairVisibleExterior = worldPos;
	                    }
	                } else if (repair && visible) {
	                    if (dist < bestRepairVisibleInteriorDist) {
	                        bestRepairVisibleInteriorDist = dist;
	                        bestRepairVisibleInterior = worldPos;
	                    }
	                } else if (repair && exterior) {
	                    if (dist < bestRepairFallbackExteriorDist) {
	                        bestRepairFallbackExteriorDist = dist;
	                        bestRepairFallbackExterior = worldPos;
	                    }
	                } else if (repair) {
	                    if (dist < bestRepairFallbackInteriorDist) {
	                        bestRepairFallbackInteriorDist = dist;
	                        bestRepairFallbackInterior = worldPos;
	                    }
	                } else if (visible && exterior) {
	                    if (dist < bestVisibleExteriorDist) {
	                        bestVisibleExteriorDist = dist;
	                        bestVisibleExterior = worldPos;
	                    }
	                } else if (visible) {
	                    if (dist < bestVisibleInteriorDist) {
	                        bestVisibleInteriorDist = dist;
	                        bestVisibleInterior = worldPos;
	                    }
	                } else if (exterior) {
	                    if (dist < bestFallbackExteriorDist) {
	                        bestFallbackExteriorDist = dist;
	                        bestFallbackExterior = worldPos;
	                    }
	                } else if (dist < bestFallbackInteriorDist) {
	                    bestFallbackInteriorDist = dist;
	                    bestFallbackInterior = worldPos;
	                }
	            }
	        }
	        List<BlockPos> prioritized = new ArrayList<>(8);
	        if (preferExterior) {
	            prioritized.add(bestRepairVisibleExterior);
	            prioritized.add(bestRepairFallbackExterior);
	            prioritized.add(bestRepairVisibleInterior);
	            prioritized.add(bestRepairFallbackInterior);
	            prioritized.add(bestVisibleExterior);
	            prioritized.add(bestFallbackExterior);
	            prioritized.add(bestVisibleInterior);
	            prioritized.add(bestFallbackInterior);
	        } else {
	            prioritized.add(bestRepairVisibleInterior);
	            prioritized.add(bestRepairVisibleExterior);
	            prioritized.add(bestRepairFallbackInterior);
	            prioritized.add(bestRepairFallbackExterior);
	            prioritized.add(bestVisibleInterior);
	            prioritized.add(bestVisibleExterior);
	            prioritized.add(bestFallbackInterior);
	            prioritized.add(bestFallbackExterior);
	        }
	        return firstAvailableBuildZone(prioritized);
	    }

    private BlockPos firstAvailableBuildZone(List<BlockPos> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos candidate : candidates) {
            if (candidate == null || !seen.add(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    // Finds the nearest non-air schematic block in unloaded chunks.
    // Walking toward the result loads the chunk for placement.
    /*? if >=26.1 {*//*
    private BlockPos findUnloadedBuildZone(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findUnloadedBuildZone(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) return null;

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            // Sample every 4th block per axis to keep the scan fast.
            // On a 500×500×100 build this checks ~781K positions instead
            // of 25M, and still finds the nearest unloaded region.
            for (int y = 0; y < region.absY; y += 4) {
                for (int z = 0; z < region.absZ; z += 4) {
                    for (int x = 0; x < region.absX; x += 4) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target) && !isLiquidSource(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        // We only care about blocks in UNLOADED chunks
                        /*? if >=26.1 {*//*
                        if (world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/

                        /*? if >=26.1 {*//*
                        double dist = playerPos.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                        *//*?} else {*/
                        double dist = playerPos.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                        /*?}*/
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = new BlockPos(wx, wy, wz);
                        }
                    }
                }
            }
        }
        return best;
    }

    // PLACEMENT HELPERS

    // Formats items as a chat string (up to 5 names + overflow).
    private static String formatMissingItems(Set<Item> items) {
        if (items == null || items.isEmpty()) return "§7(none)";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Item item : items) {
            if (shown > 0) sb.append("§7, ");
            /*? if >=26.1 {*//*
            sb.append("§f").append(item.getName(item.getDefaultInstance()).getString());
            *//*?} else {*/
            sb.append("§f").append(item.getName().getString());
            /*?}*/
            if (++shown >= 5) {
                int more = items.size() - shown;
                if (more > 0) sb.append(" §7+").append(more).append(" more");
                break;
            }
        }
        return sb.toString();
    }

    // Formats item IDs as a chat string, resolving display names.
    private static String formatNeededItemIds(Set<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return "§7(none)";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String id : itemIds) {
            /*? if >=26.1 {*//*
            net.minecraft.resources.Identifier itemId = Identifier.tryParse(id);
            *//*?} else {*/
            net.minecraft.util.Identifier itemId = Identifier.tryParse(id);
            /*?}*/
            if (itemId == null) continue;
            /*? if >=26.1 {*//*
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            *//*?} else {*/
            Item item = Registries.ITEM.get(itemId);
            /*?}*/
            if (item == Items.AIR) continue;
            if (shown > 0) sb.append("§7, ");
            /*? if >=26.1 {*//*
            sb.append("§f").append(item.getName(item.getDefaultInstance()).getString());
            *//*?} else {*/
            sb.append("§f").append(item.getName().getString());
            /*?}*/
            if (++shown >= 5) {
                int more = itemIds.size() - shown;
                if (more > 0) sb.append(" §7+").append(more).append(" more");
                break;
            }
        }
        return shown == 0 ? "§7(unknown items)" : sb.toString();
    }

    // Adds neededItems IDs to skippedItems so tryPlaceNextBlock skips them.
    private void addNeededToSkipped() {
        for (String id : neededItems) {
            /*? if >=26.1 {*//*
            net.minecraft.resources.Identifier itemId = Identifier.tryParse(id);
            *//*?} else {*/
            net.minecraft.util.Identifier itemId = Identifier.tryParse(id);
            /*?}*/
            if (itemId == null) continue;
            /*? if >=26.1 {*//*
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            *//*?} else {*/
            Item item = Registries.ITEM.get(itemId);
            /*?}*/
            if (item != Items.AIR) {
                skippedItems.add(item);
            }
        }
    }

    // Counts unplaced schematic blocks whose item is in skippedItems.
    /*? if >=26.1 {*//*
    private int countSkippedBlocks(Level world) {
    *//*?} else {*/
    private int countSkippedBlocks(World world) {
    /*?}*/
        if (world == null || schematic == null) return 0;
        int count = 0;
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                        if (isAutoCreatedPart(target)) continue;

                        Item reqItem = isLiquidSource(target)
                                ? getLiquidBucketItem(target)
                                : target.getBlock().asItem();
                        if (reqItem == null || !skippedItems.contains(reqItem)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    // Returns true if the given block state can actually be placed
    // by a player (i.e. has a corresponding BlockItem).
    // Filters out liquids (water, lava), fire, portals, light blocks, etc.
    private static boolean isPlaceable(BlockState state) {
        /*? if >=26.1 {*//*
        if (state.getBlock() instanceof LiquidBlock) return false;
        *//*?} else {*/
        if (state.getBlock() instanceof FluidBlock) return false;
        /*?}*/
        if (!state.getFluidState().isEmpty()) return false;
        // Accept any block that has a valid item form (not Items.AIR).
        // This covers BlockItem, SignItem, HangingSignItem, and other
        // special item types that can still be placed.
        return state.getBlock().asItem() != Items.AIR;
    }

    // Returns true if the given block state is a placeable liquid
    // source block (water or lava with level 0).  Flowing liquid (level > 0)
    // is auto-generated and should not be individually placed.
    private static boolean isLiquidSource(BlockState state) {
        /*? if >=26.1 {*//*
        return state.getBlock() instanceof LiquidBlock
        *//*?} else {*/
        return state.getBlock() instanceof FluidBlock
        /*?}*/
            /*? if >=26.1 {*//*
            && state.getFluidState().isSource();
            *//*?} else {*/
            && state.getFluidState().isStill();
            /*?}*/
    }

    // Returns true if the schematic contains any liquid source blocks
    // that have not yet been placed in the world.
    /*? if >=26.1 {*//*
    private boolean hasRemainingLiquids(Level world) {
    *//*?} else {*/
    private boolean hasRemainingLiquids(World world) {
    /*?}*/
        if (schematic == null || anchor == null) return false;
        /*? if >=26.1 {*//*
        long tick = world.getGameTime();
        *//*?} else {*/
        long tick = world.getTime();
        /*?}*/
        if (tick - liquidsCacheTick < REMAINING_CACHE_TTL) return cachedHasLiquids;
        liquidsCacheTick = tick;
        cachedHasLiquids = hasRemainingLiquidsUncached(world);
        return cachedHasLiquids;
    }

    /*? if >=26.1 {*//*
    private boolean hasRemainingLiquidsUncached(Level world) {
    *//*?} else {*/
    private boolean hasRemainingLiquidsUncached(World world) {
    /*?}*/
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (!isLiquidSource(target)) continue;
                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // True if unplaced solid blocks remain in the schematic (TTL-cached).
    /*? if >=26.1 {*//*
    private boolean hasRemainingSolids(Level world) {
    *//*?} else {*/
    private boolean hasRemainingSolids(World world) {
    /*?}*/
        if (schematic == null || anchor == null) return false;
        /*? if >=26.1 {*//*
        long tick = world.getGameTime();
        *//*?} else {*/
        long tick = world.getTime();
        /*?}*/
        if (tick - solidsCacheTick < REMAINING_CACHE_TTL) return cachedHasSolids;
        solidsCacheTick = tick;
        cachedHasSolids = hasRemainingSolidsUncached(world);
        return cachedHasSolids;
    }

    /*? if >=26.1 {*//*
    private boolean hasRemainingSolidsUncached(Level world) {
    *//*?} else {*/
    private boolean hasRemainingSolidsUncached(World world) {
    /*?}*/
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target)) continue;
                        if (isAutoCreatedPart(target)) continue;
                        // Structural only — skip liquids and redstone
                        if (isLiquidSource(target)) continue;
                        if (BlockDependency.isRedstoneComponent(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // True if unplaced redstone components remain (TTL-cached).
    /*? if >=26.1 {*//*
    private boolean hasRemainingRedstone(Level world) {
    *//*?} else {*/
    private boolean hasRemainingRedstone(World world) {
    /*?}*/
        if (schematic == null || anchor == null) return false;
        /*? if >=26.1 {*//*
        long tick = world.getGameTime();
        *//*?} else {*/
        long tick = world.getTime();
        /*?}*/
        if (tick - redstoneCacheTick < REMAINING_CACHE_TTL) return cachedHasRedstone;
        redstoneCacheTick = tick;
        cachedHasRedstone = hasRemainingRedstoneUncached(world);
        return cachedHasRedstone;
    }

    /*? if >=26.1 {*//*
    private boolean hasRemainingRedstoneUncached(Level world) {
    *//*?} else {*/
    private boolean hasRemainingRedstoneUncached(World world) {
    /*?}*/
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target)) continue;
                        if (isAutoCreatedPart(target)) continue;
                        if (!BlockDependency.isRedstoneComponent(target)) continue;
                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Returns the bucket Item required to place the given fluid
    // block state, or null if the state is not a supported fluid.
    private static Item getLiquidBucketItem(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.WATER) return Items.WATER_BUCKET;
        if (block == Blocks.LAVA) return Items.LAVA_BUCKET;
        return null;
    }

    // Auto-created block parts that should NOT be individually placed
    // (door upper, bed head, tall plant upper).
    private static boolean isAutoCreatedPart(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock
                /*? if >=26.1 {*//*
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && state.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return true;
        }
        if (block instanceof BedBlock
                /*? if >=26.1 {*//*
                && state.hasProperty(BlockStateProperties.BED_PART)
                *//*?} else {*/
                && state.contains(Properties.BED_PART)
                /*?}*/
                /*? if >=26.1 {*//*
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                *//*?} else {*/
                && state.get(Properties.BED_PART) == BedPart.HEAD) {
                /*?}*/
            return true;
        }
        /*? if >=26.1 {*//*
        if (block instanceof DoublePlantBlock
        *//*?} else {*/
        if (block instanceof TallPlantBlock
        /*?}*/
                /*? if >=26.1 {*//*
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && state.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return true;
        }
        return false;
    }

    // Checks if existing block matches desired, ignoring neighbor-computed
    // dynamic properties (stair shape, fence connections, door open state,
    // redstone power, etc.).
    private static boolean isEffectivelyPlaced(BlockState existing, BlockState desired) {
        if (existing.equals(desired)) return true;
        if (existing.getBlock() != desired.getBlock()) return false;

        Block block = existing.getBlock();

        // Fluid blocks — same fluid type present as a source block
        /*? if >=26.1 {*//*
        if (block instanceof LiquidBlock) {
        *//*?} else {*/
        if (block instanceof FluidBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            return existing.getFluidState().isSource() && desired.getFluidState().isSource();
            *//*?} else {*/
            return existing.getFluidState().isStill() && desired.getFluidState().isStill();
            /*?}*/
        }

        // Stairs — STAIR_SHAPE is neighbor-computed; only FACING + HALF matter
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock) {
        *//*?} else {*/
        if (block instanceof StairsBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING)
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING)
            /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.HALF);
                *//*?} else {*/
                && propMatches(existing, desired, Properties.BLOCK_HALF);
                /*?}*/
        }

        // Doors — OPEN, POWERED are dynamic; FACING + HALF + HINGE matter
        if (block instanceof DoorBlock) {
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING)
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING)
            /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && propMatches(existing, desired, Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.DOOR_HINGE);
                *//*?} else {*/
                && propMatches(existing, desired, Properties.DOOR_HINGE);
                /*?}*/
        }

        // Fences — connection booleans are neighbor-computed
        if (block instanceof FenceBlock) {
            return true;
        }

        // Panes (iron bars, glass panes) — connection booleans are neighbor-computed
        /*? if >=26.1 {*//*
        if (block instanceof IronBarsBlock) {
        *//*?} else {*/
        if (block instanceof PaneBlock) {
        /*?}*/
            return true;
        }

        // Fence gates — OPEN, POWERED, IN_WALL are dynamic; only FACING matters
        if (block instanceof FenceGateBlock) {
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING);
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING);
            /*?}*/
        }

        // Walls — connection heights are neighbor-computed
        if (block instanceof WallBlock) {
            return true;
        }

        // Chests — CHEST_TYPE determined by neighbor chests
        if (block instanceof AbstractChestBlock) {
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING);
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING);
            /*?}*/
        }

        // Trapdoors — OPEN and POWERED are dynamic; only FACING + HALF matter
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING)
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING)
            /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.HALF);
                *//*?} else {*/
                && propMatches(existing, desired, Properties.BLOCK_HALF);
                /*?}*/
        }

        // Redstone wire — connections and power are neighbor-computed
        /*? if >=26.1 {*//*
        if (block instanceof RedStoneWireBlock) {
        *//*?} else {*/
        if (block instanceof RedstoneWireBlock) {
        /*?}*/
            return true;
        }

        // Mushroom blocks — face booleans (N/S/E/W/UP/DOWN) are neighbor-computed
        if (block instanceof MushroomBlock) {
            return true;
        }

        // Vines — face attachment booleans are neighbor-computed
        if (block instanceof VineBlock) {
            return true;
        }

        // Chorus plant — connections are neighbor-computed
        if (block instanceof ChorusPlantBlock) {
            return true;
        }

        // Tripwire — ATTACHED, POWERED, DISARMED are dynamic
        /*? if >=26.1 {*//*
        if (block instanceof TripWireBlock) {
        *//*?} else {*/
        if (block instanceof TripwireBlock) {
        /*?}*/
            return true;
        }

        // Tall plants — just needs to be the same block type
        /*? if >=26.1 {*//*
        if (block instanceof DoublePlantBlock) {
        *//*?} else {*/
        if (block instanceof TallPlantBlock) {
        /*?}*/
            return true;
        }

        // Note blocks — INSTRUMENT is neighbor-computed, NOTE and POWERED are dynamic
        if (block instanceof NoteBlock) {
            return true;
        }

        // Shulker boxes — FACING is cosmetic, same color is enough
        if (block instanceof ShulkerBoxBlock) {
            return true;
        }

        return false;
    }

    // Returns true if both states have the same value for the given
    // property, or if either state does not contain the property.
    private static <T extends Comparable<T>> boolean propMatches(
            BlockState a, BlockState b, Property<T> prop) {
        /*? if >=26.1 {*//*
        if (!a.hasProperty(prop) || !b.hasProperty(prop)) return true;
        *//*?} else {*/
        if (!a.contains(prop) || !b.contains(prop)) return true;
        /*?}*/
        /*? if >=26.1 {*//*
        return a.getValue(prop).equals(b.getValue(prop));
        *//*?} else {*/
        return a.get(prop).equals(b.get(prop));
        /*?}*/
    }

    // Returns true if the given position is near any previously
    // failed build zone.
    private boolean isNearFailedZone(BlockPos pos) {
        // Use the build reach distance as the exclusion radius.
        // If we can't path to one point, all blocks within our reach
        // distance of that point are likely unreachable too.
        double exclusionDist = Math.max(1.5, Math.min(2.5, Math.ceil(range) - 1.5));
        double exclusionDistSq = exclusionDist * exclusionDist;
        for (BlockPos fz : failedZones) {
            int dy = Math.abs(pos.getY() - fz.getY());
            if (dy > 1) continue;
            int dx = pos.getX() - fz.getX();
            int dz = pos.getZ() - fz.getZ();
            if ((double) dx * dx + (double) dz * dz <= exclusionDistSq) {
                return true;
            }
        }
        return false;
    }

    // Returns true if the player's bounding box overlaps the given
    // block position.  Placing a block here would push the player out.
    /*? if >=26.1 {*//*
    private static boolean playerOverlapsBlock(LocalPlayer player, BlockPos pos) {
    *//*?} else {*/
    private static boolean playerOverlapsBlock(ClientPlayerEntity player, BlockPos pos) {
    /*?}*/
        // Player hitbox: 0.6 wide (±0.3), 1.8 tall, centered on feet X/Z
        /*? if >=26.1 {*//*
        double px = player.position().x;
        double py = player.position().y;
        double pz = player.position().z;
        *//*?} else if >=1.21.10 {*//*
        double px = player.getSyncedPos().x;
        double py = player.getSyncedPos().y;
        double pz = player.getSyncedPos().z;
        *//*?} else {*/
        double px = player.getPos().x;
        double py = player.getPos().y;
        double pz = player.getPos().z;
        /*?}*/
        double halfW = 0.3;
        double height = 1.8;

        // AABB overlap test between player box and the block's unit cube
        return px + halfW > pos.getX() && px - halfW < pos.getX() + 1 &&
	               py + height > pos.getY() && py < pos.getY() + 1 &&
	               pz + halfW > pos.getZ() && pz - halfW < pos.getZ() + 1;
    }

    /*? if >=26.1 {*//*
    private static boolean playerTooCloseForServerPlacement(LocalPlayer player, BlockPos pos) {
    *//*?} else {*/
    private static boolean playerTooCloseForServerPlacement(ClientPlayerEntity player, BlockPos pos) {
    /*?}*/
        /*? if >=26.1 {*//*
        double px = player.position().x;
        double py = player.position().y;
        double pz = player.position().z;
        *//*?} else if >=1.21.10 {*//*
        double px = player.getSyncedPos().x;
        double py = player.getSyncedPos().y;
        double pz = player.getSyncedPos().z;
        *//*?} else {*/
        double px = player.getPos().x;
        double py = player.getPos().y;
        double pz = player.getPos().z;
        /*?}*/
        double halfW = 0.46;
        double height = 1.9;
        return px + halfW > pos.getX() && px - halfW < pos.getX() + 1 &&
               py + height > pos.getY() && py < pos.getY() + 1 &&
               pz + halfW > pos.getZ() && pz - halfW < pos.getZ() + 1;
    }

    private static boolean wouldPlacementIntersectStandingBody(BlockPos feetPos, BlockPos targetPos) {
        if (feetPos == null || targetPos == null) {
            return false;
        }
        double px = feetPos.getX() + 0.5;
        double py = feetPos.getY();
        double pz = feetPos.getZ() + 0.5;
        double halfW = 0.3;
        double height = 1.8;
        double margin = 0.2;
        return px + halfW > targetPos.getX() - margin
                && px - halfW < targetPos.getX() + 1.0 + margin
                && py + height > targetPos.getY() - margin
                && py < targetPos.getY() + 1.0 + margin
                && pz + halfW > targetPos.getZ() - margin
                && pz - halfW < targetPos.getZ() + 1.0 + margin;
    }

    // Finds a safe standing position near target within placement reach.
    // Needs solid ground, clear head room, and outside the build footprint.
    /*? if >=26.1 {*//*
    private BlockPos findStandingPosition(BlockPos target, Level world, LocalPlayer player) {
    *//*?} else {*/
    private BlockPos findStandingPosition(BlockPos target, World world, ClientPlayerEntity player) {
    /*?}*/
        return findStandingPosition(target, world, player, range);
    }

    /*? if >=26.1 {*//*
    private BlockPos findStandingPosition(BlockPos target, Level world, LocalPlayer player,
                                          double reachDistance) {
    *//*?} else {*/
    private BlockPos findStandingPosition(BlockPos target, World world, ClientPlayerEntity player,
                                          double reachDistance) {
    /*?}*/
        int maxReach = (int) Math.ceil(reachDistance);
        // Use a reduced range to account for the player not standing
        // exactly at block center (can be ±0.5 off).  This ensures that
        // returned positions are comfortably within reach, not marginal.
        double bufferedRange = Math.max(1.5, reachDistance - 0.3);
        double rangeSq = bufferedRange * bufferedRange;
        /*? if >=26.1 {*//*
        Vec3 targetCenter = Vec3.atCenterOf(target);
        *//*?} else {*/
        Vec3d targetCenter = Vec3d.ofCenter(target);
        /*?}*/
        BlockState desiredTarget = getDesiredBuildStateAt(target);

        BlockPos bestVisible = null;
        double bestVisibleDist = Double.MAX_VALUE;
        BlockPos bestFallback = null;
        double bestFallbackDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        int playerY = player.blockPosition().getY();
        *//*?} else {*/
        int playerY = player.getBlockPos().getY();
        /*?}*/

        // Search at y-offsets from -8 to +8 relative to the target
        // so the player can stand above, below, or at the same level.
        // Wider range lets us find standing positions on lower floors of
        // already-built structures.  The reach check (rangeSq) still
        // filters out-of-reach positions, so the wider scan is safe.
        for (int yOff = -8; yOff <= 8; yOff++) {
            int feetY = target.getY() + yOff;

            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    // Skip the target column itself and immediate neighbours
                    // in the same Y — those are where blocks need to be placed
                    if (dx == 0 && dz == 0) continue;

                    int wx = target.getX() + dx;
                    int wz = target.getZ() + dz;
                    BlockPos feetPos = new BlockPos(wx, feetY, wz);

                    // For same-level or higher targets, don't route the player
                    // down into pits or undercrofts just because they are
                    // technically within reach. Those stances correlate with
                    // perimeter loops and "walked under the build" failures.
                    if (target.getY() >= playerY - 1) {
                        if (feetPos.getY() < playerY - 1) continue;
                        if (feetPos.getY() < target.getY() - 2) continue;
                    }

                    // Must be within placement reach of the target
                    /*? if >=26.1 {*//*
                    double dist = Vec3.atCenterOf(feetPos).add(0, 1.62 - 0.5, 0) // eye height from feet center
                    *//*?} else {*/
                    double dist = Vec3d.ofCenter(feetPos).add(0, 1.62 - 0.5, 0) // eye height from feet center
                    /*?}*/
                            /*? if >=26.1 {*//*
                            .distanceToSqr(targetCenter);
                            *//*?} else {*/
                            .squaredDistanceTo(targetCenter);
                            /*?}*/
                    if (dist > rangeSq) continue;

                    // Ground must support the player (block below feet has
                    // a non-empty collision shape — covers glass, slabs, stairs,
                    // fences, etc.) OR be a correctly-placed schematic block.
                    /*? if >=26.1 {*//*
                    BlockPos groundPos = feetPos.below();
                    *//*?} else {*/
                    BlockPos groundPos = feetPos.down();
                    /*?}*/
                    BlockState groundState = world.getBlockState(groundPos);
                    boolean walkableGround = !groundState.getCollisionShape(world, groundPos).isEmpty();
                    if (!walkableGround) {
                        // Also accept ground that is a correctly-placed schematic block
                        if (!isCorrectSchematicBlock(groundPos, world)) continue;
                    }

                    // Feet and head level must be passable (air/replaceable/fluid).
                    // Reject lava (damage) but allow water (player can swim).
                    BlockState feetState = world.getBlockState(feetPos);
                    /*? if >=26.1 {*//*
                    BlockState headState = world.getBlockState(feetPos.above());
                    *//*?} else {*/
                    BlockState headState = world.getBlockState(feetPos.up());
                    /*?}*/
                    /*? if >=26.1 {*//*
                    if (!feetState.isAir() && !feetState.canBeReplaced()) continue;
                    *//*?} else {*/
                    if (!feetState.isAir() && !feetState.isReplaceable()) continue;
                    /*?}*/
                    /*? if >=26.1 {*//*
                    if (!headState.isAir() && !headState.canBeReplaced()) continue;
                    *//*?} else {*/
                    if (!headState.isAir() && !headState.isReplaceable()) continue;
                    /*?}*/
                    if (feetState.getBlock() == Blocks.LAVA) continue;
                    if (headState.getBlock() == Blocks.LAVA) continue;

                    // Must NOT be an unbuilt schematic position (feet or head)
                    if (isUnbuiltSchematicBlock(feetPos, world)) continue;
                    /*? if >=26.1 {*//*
                    if (isUnbuiltSchematicBlock(feetPos.above(), world)) continue;
                    *//*?} else {*/
                    if (isUnbuiltSchematicBlock(feetPos.up(), world)) continue;
                    /*?}*/

                    // Must have at least one horizontal escape that won't
                    // become a schematic block (so we don't get walled in)
                    if (!hasEscapeRoute(feetPos, world)) continue;

                    // Avoid positions near flowing water during solid building
                    // (Baritone can't navigate through currents).  Skip during
                    // liquid pass — placed water naturally creates flows.
                    if (!liquidPass && hasFlowingWaterNearby(feetPos, world)) continue;

                    // Prefer closer to the player's current position,
                    // with penalties for wet and unreachable positions.
                    /*? if >=26.1 {*//*
                    double playerDist = player.position().distanceToSqr(Vec3.atCenterOf(feetPos));
                    *//*?} else if >=1.21.10 {*//*
                    double playerDist = player.getSyncedPos().squaredDistanceTo(Vec3d.ofCenter(feetPos));
                    *//*?} else {*/
                    double playerDist = player.getPos().squaredDistanceTo(Vec3d.ofCenter(feetPos));
                    /*?}*/
                    boolean inWater = !feetState.getFluidState().isEmpty();
                    // Heavy penalty for water positions — anti-cheat servers
                    // reject placements from swimming, so strongly prefer
                    // dry-land positions.
                    double penalty = inWater ? 500.0 : 0.0;

                    // Strongly prefer standing positions near the target's
                    // Y-level. Working from well below the build often leads
                    // Baritone into caves/concaves where lower layers are
                    // reachable but upper layers repeatedly fail.
                    int blocksBelowTarget = Math.max(0, target.getY() - feetPos.getY());
                    penalty += blocksBelowTarget * STANDING_BELOW_TARGET_PENALTY;

                    // Prefer open, breathable platforms over cramped pockets.
                    // Low ceilings and boxed-in exits correlate strongly with
                    // slow/rejected placement when Baritone paths underneath
                    // the schematic footprint.
                    int overheadClearance = countStandingHeadroom(feetPos, world, STANDING_CLEARANCE_GOAL);
                    penalty += Math.max(0, STANDING_CLEARANCE_GOAL - overheadClearance)
                            * STANDING_LOW_HEADROOM_PENALTY;
                    int openSides = countStandingOpenSides(feetPos, world);
                    penalty += Math.max(0, 3 - openSides) * STANDING_ENCLOSURE_PENALTY;

                    // Penalise positions separated from the player by
                    // an air gap (no continuous ground between them).
                    // This deprioritises spots across chasms, but uses a
                    // moderate penalty so positions on already-placed
                    // elevated structure (stairs, floors, platforms) are
                    // still reachable — Baritone can path to them using
                    // the existing blocks even if the straight-line ground
                    // check fails.
                    /*? if >=26.1 {*//*
                    if (!hasGroundPath(player.blockPosition(), feetPos, world)) {
                    *//*?} else {*/
                    if (!hasGroundPath(player.getBlockPos(), feetPos, world)) {
                    /*?}*/
                        // Lower penalty for positions above the player —
                        // vertical travel via placed structure is expected.
                        /*? if >=26.1 {*//*
                        int yDiff = feetPos.getY() - player.blockPosition().getY();
                        *//*?} else {*/
                        int yDiff = feetPos.getY() - player.getBlockPos().getY();
                        /*?}*/
                        penalty += (Math.abs(yDiff) > 2) ? 200.0 : 10000.0;
                    }

                    double score = playerDist + penalty;
                    boolean visiblePlacement = desiredTarget != null
                            && isCandidateStableForPlacement(feetPos, target)
                            && PlacementEngine.canPlaceFromStandingPosition(
                                    target, desiredTarget, feetPos, player, world,
                                    createBuildSupportFilter(target, world, feetPos));

                    if (visiblePlacement) {
                        if (score < bestVisibleDist) {
                            bestVisibleDist = score;
                            bestVisible = feetPos;
                        }
                    } else if (score < bestFallbackDist) {
                        bestFallbackDist = score;
                        bestFallback = feetPos;
                    }
                }
            }
        }
        return bestVisible != null ? bestVisible : bestFallback;
    }

    /*? if >=26.1 {*//*
    private BlockPos findApproachStandingPosition(BlockPos target, Level world, LocalPlayer player,
                                                  int extraReachBonus) {
    *//*?} else {*/
    private BlockPos findApproachStandingPosition(BlockPos target, World world, ClientPlayerEntity player,
                                                  int extraReachBonus) {
    /*?}*/
        double approachReach = Math.min(APPROACH_STAGING_MAX_REACH,
                range + APPROACH_STAGING_EXTRA_REACH + Math.max(0, extraReachBonus));
        return findStandingPosition(target, world, player, approachReach);
    }

    /*? if >=26.1 {*//*
    private static int countStandingHeadroom(BlockPos feetPos, Level world, int maxBlocks) {
    *//*?} else {*/
    private static int countStandingHeadroom(BlockPos feetPos, World world, int maxBlocks) {
    /*?}*/
        int clear = 0;
        for (int i = 2; i < 2 + maxBlocks; i++) {
            /*? if >=26.1 {*//*
            BlockPos checkPos = feetPos.above(i);
            *//*?} else {*/
            BlockPos checkPos = feetPos.up(i);
            /*?}*/
            if (isMovementBlocking(world.getBlockState(checkPos))) {
                break;
            }
            clear++;
        }
        return clear;
    }

    /*? if >=26.1 {*//*
    private static int countStandingOpenSides(BlockPos feetPos, Level world) {
    *//*?} else {*/
    private static int countStandingOpenSides(BlockPos feetPos, World world) {
    /*?}*/
        int openSides = 0;
        for (Direction dir : HORIZONTALS) {
            /*? if >=26.1 {*//*
            BlockPos feetN = feetPos.relative(dir);
            BlockPos headN = feetN.above();
            *//*?} else {*/
            BlockPos feetN = feetPos.offset(dir);
            BlockPos headN = feetN.up();
            /*?}*/
            if (!isMovementBlocking(world.getBlockState(feetN))
                    && !isMovementBlocking(world.getBlockState(headN))) {
                openSides++;
            }
        }
        return openSides;
    }

    // Simple ground check: walks a straight line from start to end
    // at destination Y, verifying solid ground at each step.
    /*? if >=26.1 {*//*
    private static boolean hasGroundPath(BlockPos from, BlockPos to, Level world) {
    *//*?} else {*/
    private static boolean hasGroundPath(BlockPos from, BlockPos to, World world) {
    /*?}*/
        int x0 = from.getX(), z0 = from.getZ();
        int x1 = to.getX(), z1 = to.getZ();
        int y = to.getY(); // check ground at destination elevation

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        // Walk a Bresenham line from (x0,z0) to (x1,z1)
        int steps = 0;
        int maxSteps = dx + dz + 2; // safety limit
        while (steps++ < maxSteps) {
            if (x0 == x1 && z0 == z1) break;
            // Check ground under this column
            BlockPos groundPos = new BlockPos(x0, y - 1, z0);
            BlockState groundState = world.getBlockState(groundPos);
            /*? if >=26.1 {*//*
            if (groundState.isAir() || groundState.canBeReplaced()) {
            *//*?} else {*/
            if (groundState.isAir() || groundState.isReplaceable()) {
            /*?}*/
                return false; // air gap — no ground
            }

            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x0 += sx; }
            if (e2 < dx)  { err += dx; z0 += sz; }
        }
        return true;
    }

    // Returns true if the given world position corresponds to an
    // unbuilt schematic block (the schematic expects a block there but the
    // world doesn't have it yet).
    /*? if >=26.1 {*//*
    private boolean isUnbuiltSchematicBlock(BlockPos worldPos, Level world) {
    *//*?} else {*/
    private boolean isUnbuiltSchematicBlock(BlockPos worldPos, World world) {
    /*?}*/
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) return false;

        BlockState expected = schematic.getBlockState(sx, sy, sz);
        if (expected.isAir()) return false;
        if (!isPlaceable(expected) && !isLiquidSource(expected)) return false;

        return !isEffectivelyPlaced(world.getBlockState(worldPos), expected);
    }

    // ENTRAPMENT SAFETY

    // Cardinal directions the player can walk through.
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    // Whether a block state allows safe walking through it.
    // Excludes liquids — water/lava are technically passable but not
    // safe exits (player would swim / take damage).
    private static boolean isPassable(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        /*? if >=26.1 {*//*
        return state.isAir() || state.canBeReplaced() || !state.blocksMotion();
        *//*?} else {*/
        return state.isAir() || state.isReplaceable() || !state.blocksMovement();
        /*?}*/
    }

    // Whether a block state physically blocks movement.  Unlike
    // isPassable, this treats fluids as non-blocking because
    // the player can swim through them.  Used for entrapment detection
    // where we only care about solid walls, not water.
    private static boolean isMovementBlocking(BlockState state) {
        /*? if >=26.1 {*//*
        if (state.isAir() || state.canBeReplaced()) return false;
        *//*?} else {*/
        if (state.isAir() || state.isReplaceable()) return false;
        /*?}*/
        if (!state.getFluidState().isEmpty()) return false; // can swim
        /*? if >=26.1 {*//*
        return state.blocksMotion();
        *//*?} else {*/
        return state.blocksMovement();
        /*?}*/
    }

    // Returns true if placing a block at pos would seal
    // the player's last walkable horizontal exit.
    // Only relevant when the candidate block is in the player's
    // "exit ring" — one of the 4 cardinal neighbours at feet or head
    // level.  If placing it would drop the exit count from ≥1 to 0,
    // the placement is vetoed.
    /*? if >=26.1 {*//*
    private static boolean wouldTrapPlayer(LocalPlayer player,
    *//*?} else {*/
    private static boolean wouldTrapPlayer(ClientPlayerEntity player,
    /*?}*/
                                           /*? if >=26.1 {*//*
                                           BlockPos pos, Level world) {
                                           *//*?} else {*/
                                           BlockPos pos, World world) {
                                           /*?}*/
        /*? if >=26.1 {*//*
        BlockPos feetPos = player.blockPosition();
        *//*?} else {*/
        BlockPos feetPos = player.getBlockPos();
        /*?}*/

        // Only check blocks that form the player's exit ring
        int dy = pos.getY() - feetPos.getY();
        if (dy < 0 || dy > 1) return false; // not at feet or head level

        int dx = pos.getX() - feetPos.getX();
        int dz = pos.getZ() - feetPos.getZ();
        if (Math.abs(dx) + Math.abs(dz) != 1) return false; // not cardinal

        // Count exits before and after the hypothetical placement
        int exitsBefore = 0;
        int exitsAfter  = 0;

        for (Direction dir : HORIZONTALS) {
            /*? if >=26.1 {*//*
            BlockPos feetN = feetPos.relative(dir);
            *//*?} else {*/
            BlockPos feetN = feetPos.offset(dir);
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos headN = feetN.above();
            *//*?} else {*/
            BlockPos headN = feetN.up();
            /*?}*/

            // Use isMovementBlocking instead of isPassable so that
            // water/lava count as valid exits (player can swim through).
            boolean feetOk = !isMovementBlocking(world.getBlockState(feetN));
            boolean headOk = !isMovementBlocking(world.getBlockState(headN));

            if (feetOk && headOk) {
                exitsBefore++;
                // Would this exit survive the placement?
                if (!feetN.equals(pos) && !headN.equals(pos)) {
                    exitsAfter++;
                }
            }
        }

        return exitsBefore > 0 && exitsAfter == 0;
    }

    // Returns true if the player currently has no
    // walkable horizontal exit (all 4 cardinal directions are blocked
    // at either feet or head level by solid blocks).
    // Fluids do NOT count as entrapment — the player can swim
    // through water/lava even though it's undesirable.  Only solid
    // blocks that physically prevent horizontal movement trigger this.
    /*? if >=26.1 {*//*
    private static boolean isPlayerTrapped(LocalPlayer player,
    *//*?} else {*/
    private static boolean isPlayerTrapped(ClientPlayerEntity player,
    /*?}*/
                                           /*? if >=26.1 {*//*
                                           Level world) {
                                           *//*?} else {*/
                                           World world) {
                                           /*?}*/
        /*? if >=26.1 {*//*
        BlockPos feetPos = player.blockPosition();
        *//*?} else {*/
        BlockPos feetPos = player.getBlockPos();
        /*?}*/
        for (Direction dir : HORIZONTALS) {
            /*? if >=26.1 {*//*
            BlockPos feetN = feetPos.relative(dir);
            *//*?} else {*/
            BlockPos feetN = feetPos.offset(dir);
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos headN = feetN.above();
            *//*?} else {*/
            BlockPos headN = feetN.up();
            /*?}*/
            if (!isMovementBlocking(world.getBlockState(feetN)) &&
                    !isMovementBlocking(world.getBlockState(headN))) {
                return false; // at least one exit
            }
        }
        return true;
    }

    // Returns true if the given feet position has at least one
    // horizontal exit direction that is currently clear and
    // will remain clear after the schematic is fully built (i.e. the
    // exit blocks are not unbuilt schematic positions).
    /*? if >=26.1 {*//*
    private boolean hasEscapeRoute(BlockPos feetPos, Level world) {
    *//*?} else {*/
    private boolean hasEscapeRoute(BlockPos feetPos, World world) {
    /*?}*/
        for (Direction dir : HORIZONTALS) {
            /*? if >=26.1 {*//*
            BlockPos feetN = feetPos.relative(dir);
            *//*?} else {*/
            BlockPos feetN = feetPos.offset(dir);
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos headN = feetN.above();
            *//*?} else {*/
            BlockPos headN = feetN.up();
            /*?}*/

            // Currently passable?  Use isMovementBlocking so water
            // counts as a valid escape direction (player can swim).
            if (isMovementBlocking(world.getBlockState(feetN))) continue;
            if (isMovementBlocking(world.getBlockState(headN))) continue;

            // Won't become a schematic block later?
            if (isUnbuiltSchematicBlock(feetN, world)) continue;
            if (isUnbuiltSchematicBlock(headN, world)) continue;

            return true;
        }
        return false;
    }

    // Returns true if any block within 2 horizontal blocks of
    // feetPos (at feet or head level) contains flowing water or
    // lava.  Flowing liquids push the player, breaking Baritone's
    // pathfinding — so standing positions near them should be avoided.
    /*? if >=26.1 {*//*
    private static boolean hasFlowingWaterNearby(BlockPos feetPos, Level world) {
    *//*?} else {*/
    private static boolean hasFlowingWaterNearby(BlockPos feetPos, World world) {
    /*?}*/
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos check = feetPos.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos check = feetPos.add(dx, dy, dz);
                    /*?}*/
                    FluidState fluid = world.getBlockState(check).getFluidState();
                    /*? if >=26.1 {*//*
                    if (!fluid.isEmpty() && !fluid.isSource()) {
                    *//*?} else {*/
                    if (!fluid.isEmpty() && !fluid.isStill()) {
                    /*?}*/
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Finds an escape position outside an enclosed area.  Searches outward
    // up to 8 blocks, accepting water positions unlike findStandingPosition.
    /*? if >=26.1 {*//*
    private BlockPos findEscapePosition(LocalPlayer player,
    *//*?} else {*/
    private BlockPos findEscapePosition(ClientPlayerEntity player,
    /*?}*/
                                        /*? if >=26.1 {*//*
                                        Level world) {
                                        *//*?} else {*/
                                        World world) {
                                        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos origin = player.blockPosition();
        *//*?} else {*/
        BlockPos origin = player.getBlockPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // ring only
                    for (int dy = -2; dy <= 2; dy++) {
                        /*? if >=26.1 {*//*
                        BlockPos feetPos = origin.offset(dx, dy, dz);
                        *//*?} else {*/
                        BlockPos feetPos = origin.add(dx, dy, dz);
                        /*?}*/

                        // Ground must be solid (or solid under water)
                        /*? if >=26.1 {*//*
                        BlockPos groundPos = feetPos.below();
                        *//*?} else {*/
                        BlockPos groundPos = feetPos.down();
                        /*?}*/
                        BlockState groundState = world.getBlockState(groundPos);
                        /*? if >=26.1 {*//*
                        if (!groundState.isSolidRender()
                        *//*?} else {*/
                        if (!groundState.isSolidBlock(world, groundPos)
                        /*?}*/
                                && groundState.getFluidState().isEmpty()) continue;

                        // Feet + head must not be solid blocks.
                        // Water is acceptable here — we're escaping
                        // entrapment, not finding a comfortable position.
                        if (isMovementBlocking(world.getBlockState(feetPos))) continue;
                        /*? if >=26.1 {*//*
                        if (isMovementBlocking(world.getBlockState(feetPos.above()))) continue;
                        *//*?} else {*/
                        if (isMovementBlocking(world.getBlockState(feetPos.up()))) continue;
                        /*?}*/

                        // Not inside the schematic's unbuilt footprint
                        if (isUnbuiltSchematicBlock(feetPos, world)) continue;
                        /*? if >=26.1 {*//*
                        if (isUnbuiltSchematicBlock(feetPos.above(), world)) continue;
                        *//*?} else {*/
                        if (isUnbuiltSchematicBlock(feetPos.up(), world)) continue;
                        /*?}*/

                        /*? if >=26.1 {*//*
                        double dist = player.position().distanceToSqr(
                                Vec3.atCenterOf(feetPos));
                        *//*?} else if >=1.21.10 {*//*
                        double dist = player.getSyncedPos().squaredDistanceTo(
                                Vec3d.ofCenter(feetPos));
                        *//*?} else {*/
                        double dist = player.getPos().squaredDistanceTo(
                                Vec3d.ofCenter(feetPos));
                        /*?}*/

                        // Prefer positions NOT in water when possible
                        boolean inWater = !world.getFluidState(feetPos).isEmpty();
                        double penalty = inWater ? 100.0 : 0.0;

                        if (dist + penalty < bestDist) {
                            bestDist = dist + penalty;
                            best = feetPos;
                        }
                    }
                }
            }
            if (best != null) return best; // found one at this radius
        }
        return best;
    }

    // CORE PLACEMENT

    private int placeDebugCooldown = 0;
    private static final int PLACE_SCAN_NONE_COOLDOWN_TICKS = 40;

    private Item getRequiredBuildItem(BlockState target) {
        if (target == null || target.isAir()) {
            return Items.AIR;
        }
        if (isLiquidSource(target)) {
            Item bucket = getLiquidBucketItem(target);
            return bucket != null ? bucket : Items.AIR;
        }
        return target.getBlock().asItem();
    }

    private Item getRequiredBuildItem(BlockPos worldPos) {
        if (worldPos == null || schematic == null || anchor == null) {
            return Items.AIR;
        }
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) {
            return Items.AIR;
        }
        return getRequiredBuildItem(schematic.getBlockState(sx, sy, sz));
    }

    private int getChronologicalRegionMajor(BlockPos worldPos) {
        return anchor != null && worldPos != null ? worldPos.getZ() - anchor.getZ() : 0;
    }

    private int getChronologicalRegionMinor(BlockPos worldPos) {
        return anchor != null && worldPos != null ? worldPos.getX() - anchor.getX() : 0;
    }

    private void filterChronologicalBuildFrontier(List<BlockPos> candidates) {
        if (candidates == null || candidates.size() <= 1) {
            return;
        }
        if (sortMode != SortMode.BOTTOM_UP && sortMode != SortMode.TOP_DOWN) {
            return;
        }

        int frontierY = candidates.get(0).getY();
        for (BlockPos candidate : candidates) {
            int y = candidate.getY();
            if (sortMode == SortMode.BOTTOM_UP) {
                frontierY = Math.min(frontierY, y);
            } else {
                frontierY = Math.max(frontierY, y);
            }
        }

        int before = candidates.size();
        int finalFrontierY = frontierY;
        candidates.removeIf(pos -> pos.getY() != finalFrontierY);
        if (before != candidates.size()) {
            PacketTelemetry.mark("build chronological frontier y=" + finalFrontierY
                    + " kept=" + candidates.size()
                    + " deferred=" + (before - candidates.size()));
        }
    }

    private void clearBuildMaterialRun(String reason) {
        if (activeBuildMaterialRun != null) {
            PacketTelemetry.mark("build material run clear item=" + activeBuildMaterialRun
                    + " reason=" + reason);
        }
        activeBuildMaterialRun = null;
    }

    private void clearBuildInventorySwapWait() {
        deferredBuildInventorySwapItem = null;
        deferredBuildInventorySwapTicks = 0;
    }

    private int tickBuildInventorySwapWait(Item item) {
        if (item == null || item == Items.AIR) {
            clearBuildInventorySwapWait();
            return 0;
        }
        if (deferredBuildInventorySwapItem != item) {
            deferredBuildInventorySwapItem = item;
            deferredBuildInventorySwapTicks = 0;
        }
        return ++deferredBuildInventorySwapTicks;
    }

    private boolean canReleaseDeferredBuildInventorySwap() {
        if (!swapItems) return false;
        if (PlacementEngine.areSwapsSuspended()) return false;
        if (!SetbackMonitor.get().isCalm()) return false;
        return PlacementEngine.getConsecutiveFailures() == 0
                && PlacementEngine.getConsecutiveRejections() == 0;
    }

    private String buildInventorySwapGateReason() {
        if (!swapItems) return "swap-disabled";
        if (PlacementEngine.areSwapsSuspended()) return "swaps-suspended";
        SetbackMonitor monitor = SetbackMonitor.get();
        if (!monitor.isCalm()) return "setback-cooldown";
        int recentSetbacks = monitor.recentSetbackCount(BUILD_SWAP_RECENT_SETBACK_WINDOW);
        if (recentSetbacks > 0) return "recent-setbacks=" + recentSetbacks;
        if (!monitor.isStationaryFor(3)) return "not-stationary";
        if (PlacementEngine.getConsecutiveFailures() > 0) {
            return "failures=" + PlacementEngine.getConsecutiveFailures();
        }
        if (PlacementEngine.getConsecutiveRejections() > 0) {
            return "rejections=" + PlacementEngine.getConsecutiveRejections();
        }
        return "open";
    }

    private Item activateBuildMaterialRun(Item item, BuildMaterialRunStats stats, String reason) {
        if (item == null || item == Items.AIR || stats == null) {
            clearBuildMaterialRun(reason);
            return null;
        }
        if (activeBuildMaterialRun != item) {
            PacketTelemetry.mark("build material run item=" + item
                    + " candidates=" + stats.count
                    + " held=" + stats.heldReady
                    + " hotbar=" + stats.hotbarReady
                    + " reason=" + reason);
        }
        activeBuildMaterialRun = item;
        if (stats.heldReady || stats.hotbarReady) {
            clearBuildInventorySwapWait();
        }
        return item;
    }

    /*? if >=26.1 {*//*
    private Item chooseBuildMaterialRun(List<BlockPos> candidates, Level world, Item heldBuildItem,
                                        Map<Item, Integer> hotbarInventory,
                                        Map<Item, Integer> fullInventory) {
    *//*?} else {*/
    private Item chooseBuildMaterialRun(List<BlockPos> candidates, World world, Item heldBuildItem,
                                        Map<Item, Integer> hotbarInventory,
                                        Map<Item, Integer> fullInventory) {
    /*?}*/
        if (candidates == null || candidates.isEmpty()) {
            clearBuildMaterialRun("no-candidates");
            return null;
        }

        Map<Item, BuildMaterialRunStats> runnable = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            BlockPos worldPos = candidates.get(i);
            int sx = worldPos.getX() - anchor.getX();
            int sy = worldPos.getY() - anchor.getY();
            int sz = worldPos.getZ() - anchor.getZ();
            if (!schematic.contains(sx, sy, sz)) continue;

            BlockState target = schematic.getBlockState(sx, sy, sz);
            if (!BlockDependency.isReadyToPlace(world, worldPos, target)) continue;
            Item item = getRequiredBuildItem(target);
            if (item == null || item == Items.AIR || skippedItems.contains(item)) continue;
            if (fullInventory.getOrDefault(item, 0) <= 0) continue;

            boolean heldReady = item == heldBuildItem;
            boolean hotbarReady = heldReady || hotbarInventory.getOrDefault(item, 0) > 0;
            BuildMaterialRunStats stats = runnable.get(item);
            if (stats == null) {
                runnable.put(item, new BuildMaterialRunStats(i, heldReady, hotbarReady));
            } else {
                stats.count++;
            }
        }

        if (runnable.isEmpty()) {
            clearBuildMaterialRun("no-runnable-materials");
            return null;
        }

        BuildMaterialRunStats activeStats = runnable.get(activeBuildMaterialRun);
        if (activeStats != null) {
            return activeBuildMaterialRun;
        }
        if (activeBuildMaterialRun != null) {
            clearBuildMaterialRun("local-run-exhausted");
        }

        Item bestHeld = null;
        BuildMaterialRunStats bestHeldStats = null;
        Item bestHotbar = null;
        BuildMaterialRunStats bestHotbarStats = null;
        Item bestInventory = null;
        BuildMaterialRunStats bestInventoryStats = null;

        for (Map.Entry<Item, BuildMaterialRunStats> entry : runnable.entrySet()) {
            Item item = entry.getKey();
            BuildMaterialRunStats stats = entry.getValue();
            if (stats.heldReady) {
                if (bestHeldStats == null || stats.firstIndex < bestHeldStats.firstIndex) {
                    bestHeld = item;
                    bestHeldStats = stats;
                }
            } else if (stats.hotbarReady) {
                if (bestHotbarStats == null || stats.firstIndex < bestHotbarStats.firstIndex) {
                    bestHotbar = item;
                    bestHotbarStats = stats;
                }
            } else if (bestInventoryStats == null || stats.firstIndex < bestInventoryStats.firstIndex) {
                bestInventory = item;
                bestInventoryStats = stats;
            }
        }

        if (bestHeld != null) {
            return activateBuildMaterialRun(bestHeld, bestHeldStats, "held");
        }
        if (bestHotbar != null) {
            return activateBuildMaterialRun(bestHotbar, bestHotbarStats, "hotbar");
        }
        return activateBuildMaterialRun(bestInventory, bestInventoryStats, "inventory-staging");
    }

    /*? if >=26.1 {*//*
    private boolean tryPlaceNextBlock(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean tryPlaceNextBlock(ClientPlayerEntity player, World world) {
    /*?}*/
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        double rangeSq = range * range;
        int maxReach = (int) Math.ceil(range);

        List<BlockPos> candidates = new ArrayList<>();
        List<BlockPos> fallbackCandidates = new ArrayList<>();
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/
        int activeBandTopY = getBottomUpActiveBandTopY(world, getRenderWindow(playerPos));
        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/

        // Debug counters: track why blocks are filtered
        int dbgTotal = 0, dbgRange = 0, dbgOverlap = 0, dbgBounds = 0;
        int dbgAir = 0, dbgPlaceable = 0, dbgAutoCreated = 0, dbgLiquid = 0;
        int dbgPlaced = 0, dbgNoAdj = 0, dbgTrap = 0, dbgCoolingDown = 0;
        int dbgUnstable = 0, dbgOccluded = 0;
        BlockPos dbgFirstFiltered = null;
        String dbgFirstReason = null;
        Set<Long> observedPlacedChunks = new HashSet<>();

        for (int dy = -maxReach; dy <= maxReach; dy++) {
            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos worldPos = playerPos.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos worldPos = playerPos.add(dx, dy, dz);
                    /*?}*/

                    /*? if >=26.1 {*//*
                    if (eyePos.distanceToSqr(Vec3.atCenterOf(worldPos)) > rangeSq) continue;
                    *//*?} else {*/
                    if (eyePos.squaredDistanceTo(Vec3d.ofCenter(worldPos)) > rangeSq) continue;
                    /*?}*/
                    // Skip blocks the player is physically standing inside
                    // (placing there would push them out).  Player hitbox is
                    // 0.6×1.8×0.6 centered on their feet position.
                    if (playerOverlapsBlock(player, worldPos)
                            || playerTooCloseForServerPlacement(player, worldPos)) {
                        dbgOverlap++;
                        continue;
                    }

                    int sx = worldPos.getX() - anchor.getX();
                    int sy = worldPos.getY() - anchor.getY();
                    int sz = worldPos.getZ() - anchor.getZ();
                    if (!schematic.contains(sx, sy, sz)) continue;
                    if (isBuildTargetCoolingDown(worldPos)) {
                        dbgCoolingDown++;
                        continue;
                    }

                    BlockState target = schematic.getBlockState(sx, sy, sz);
                    if (target.isAir()) continue;
                    if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                    if (isAutoCreatedPart(target)) continue;
                    if (!matchesCurrentBuildPass(target)) continue;
                    if (worldPos.getY() > activeBandTopY) continue;

                    // This block needs placing — count it for debug
                    dbgTotal++;
                    BlockState existingState = world.getBlockState(worldPos);
                    if (isEffectivelyPlaced(existingState, target)) {
                        if (observedPlacedChunks.add(chunkKey(worldPos))) {
                            invalidateChunkDiffCache(worldPos);
                        }
                        dbgPlaced++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "already placed (" + existingState.getBlock() + " vs " + target.getBlock() + ")"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "already placed (" + existingState.getBlock() + " vs " + target.getBlock() + ")"; }
                        /*?}*/
                        continue;
                    }
                    /*? if >=26.1 {*//*
                    if (!existingState.isAir() && !existingState.canBeReplaced()
                    *//*?} else {*/
                    if (!existingState.isAir() && !existingState.isReplaceable()
                    /*?}*/
                            && existingState.getBlock() == target.getBlock()
                            && !PlacementEngine.isOrientationMismatch(existingState, target)) {
                        if (observedPlacedChunks.add(chunkKey(worldPos))) {
                            invalidateChunkDiffCache(worldPos);
                        }
                        dbgPlaced++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "occupied same block (" + existingState.getBlock() + " vs " + target.getBlock() + ")"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "occupied same block (" + existingState.getBlock() + " vs " + target.getBlock() + ")"; }
                        /*?}*/
                        continue;
                    }

                    if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) {
                        dbgNoAdj++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "no adjacent solid"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "no adjacent solid"; }
                        /*?}*/
                        continue;
                    }
                    if (!hasBuildPlacementFrontierSupport(worldPos, target, world)) {
                        dbgNoAdj++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "no frontier support"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "no frontier support"; }
                        /*?}*/
                        continue;
                    }

                    // Don't place a block that would wall the player in
                    if (wouldTrapPlayer(player, worldPos, world)) {
                        dbgTrap++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "would trap player"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "would trap player"; }
                        /*?}*/
                        continue;
                    }

                    boolean stableForPlacement = isCandidateStableForPlacement(playerPos, worldPos);
                    if (!stableForPlacement) {
                        dbgUnstable++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "target too high for current stance"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "target too high for current stance"; }
                        /*?}*/
                    }

                    boolean canPlaceFromCurrentPosition =
                            PlacementEngine.canPlaceFromCurrentPosition(
                                    worldPos, target, player, world,
                                    createBuildSupportFilter(worldPos, world, playerPos));
                    if (!canPlaceFromCurrentPosition) {
                        dbgOccluded++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "occluded placement path"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "occluded placement path"; }
                        /*?}*/
                    }
                    if (stableForPlacement && canPlaceFromCurrentPosition) {
                        candidates.add(worldPos);
                    } else {
                        fallbackCandidates.add(worldPos);
                    }
                }
            }
        }

        if (candidates.isEmpty() && !fallbackCandidates.isEmpty()) {
            String probeFailure = PlacementEngine.consumeLastProbeFailure();
            PacketTelemetry.mark("place blocked relaxed=" + fallbackCandidates.size()
                    + " unstable=" + dbgUnstable
                    + " occluded=" + dbgOccluded
                    + (probeFailure != null ? " probe=" + probeFailure : ""));
            boolean pureOcclusion = dbgOccluded > 0 && dbgUnstable == 0;
            BlockPos blockedTarget = fallbackCandidates.get(0);
            if (isWrongSideProbeFailure(probeFailure)) {
                int sx = blockedTarget.getX() - anchor.getX();
                int sy = blockedTarget.getY() - anchor.getY();
                int sz = blockedTarget.getZ() - anchor.getZ();
                BlockState blockedState = schematic.contains(sx, sy, sz)
                        ? schematic.getBlockState(sx, sy, sz)
                        : null;
                recoverWrongSidePlacement(blockedTarget, blockedState, probeFailure);
            } else if (pureOcclusion) {
                recordBuildLayerAccessFailure(blockedTarget, "occluded local stance");
                coolDownPlacementTargets(
                        fallbackCandidates,
                        OCCLUDED_TARGET_COOLDOWN_TICKS,
                        "occluded local stance");
                if (!PathWalker.isActive()) {
                    invalidateBuildStagingPlanCache();
                    walkAttemptCooldown = 0;
                    if (tryWalkToNextZone(mc)) {
                        autoState = AutoState.WALKING_TO_BUILD;
                    }
                }
            } else if (!deferredPlacementReposition) {
                schedulePlacementAccessReposition(
                        blockedTarget,
                        "nearby candidates need a better stance",
                        PLACEMENT_START_FAILURE_PAUSE_TICKS);
            }
            noProgressTicks = Math.max(noProgressTicks, NO_PROGRESS_WALK_RECHECK_TICKS - 1);
            LOGGER.debug("No strict placement candidates from current stance; {} relaxed candidates need reposition",
                    fallbackCandidates.size());
        }

        if (candidates.isEmpty()) {
            boolean reportPlaceScanNone = placeDebugCooldown <= 0;
            if (reportPlaceScanNone) {
                PacketTelemetry.mark("place scan none"
                        + " total=" + dbgTotal
                        + " placed=" + dbgPlaced
                        + " overlap=" + dbgOverlap
                        + " noAdj=" + dbgNoAdj
                        + " trap=" + dbgTrap
                        + " cooldown=" + dbgCoolingDown
                        + " unstable=" + dbgUnstable
                        + " occluded=" + dbgOccluded
                        + " player=" + posLabel(playerPos)
                        + " lastZone=" + posLabel(lastWalkTargetZone)
                        + " activeTop=" + activeBandTopY
                        + (dbgFirstFiltered != null
                        ? " first=" + posLabel(dbgFirstFiltered) + ":" + dbgFirstReason
                        : ""));
            }
            // Debug output (rate-limited to once per 5 s / 100 ticks)
            if (LOGGER.isDebugEnabled() && statusMessages && reportPlaceScanNone) {
                StringBuilder sb = new StringBuilder("[PlaceDbg] ");
                sb.append("0 candidates. ").append(dbgTotal).append(" schematic blocks checked nearby. ");
                if (dbgTotal > 0) {
                    sb.append("Filtered: ");
                    if (dbgPlaced > 0) sb.append("placed=").append(dbgPlaced).append(" ");
                    if (dbgOverlap > 0) sb.append("overlap=").append(dbgOverlap).append(" ");
                    if (dbgNoAdj > 0) sb.append("noAdj=").append(dbgNoAdj).append(" ");
                    if (dbgTrap > 0) sb.append("trap=").append(dbgTrap).append(" ");
                    if (dbgCoolingDown > 0) sb.append("cooldown=").append(dbgCoolingDown).append(" ");
                    if (dbgUnstable > 0) sb.append("unstable=").append(dbgUnstable).append(" ");
                    if (dbgOccluded > 0) sb.append("occluded=").append(dbgOccluded).append(" ");
                    if (dbgFirstFiltered != null) {
                        sb.append(" First: ").append(dbgFirstFiltered.getX())
                          .append(" ").append(dbgFirstFiltered.getY())
                          .append(" ").append(dbgFirstFiltered.getZ())
                          .append(": ").append(dbgFirstReason);
                    }
                } else {
                    sb.append("(no unplaced blocks in scan range from ")
                      .append(playerPos.getX()).append(" ").append(playerPos.getY())
                      .append(" ").append(playerPos.getZ()).append(")");
                }
                LOGGER.debug(sb.toString());
            }
            if (reportPlaceScanNone) {
                placeDebugCooldown = PLACE_SCAN_NONE_COOLDOWN_TICKS;
            }
            if (placeDebugCooldown > 0) placeDebugCooldown--;
            if (dbgTotal > 0 && dbgPlaced >= dbgTotal && lastWalkTargetZone != null) {
                BlockPos completedZone = lastWalkTargetZone;
                rememberFailedBuildZone(completedZone, 2);
                recordBuildLayerAccessFailure(completedZone, "local pocket complete");
                lastWalkTargetZone = null;
                lastWalkStandPos = null;
                lastWalkApproachPos = null;
                walkAttemptCooldown = 0;
                noProgressTicks = Math.max(noProgressTicks, NO_PROGRESS_WALK_RECHECK_TICKS - 1);
                PacketTelemetry.mark("build local pocket complete zone="
                        + posLabel(completedZone)
                        + " scanned=" + dbgTotal);
            }
            lastMissingItems.clear();
            return false;
        }

        Map<Item, Integer> hotbarInventory = PlacementEngine.getHotbarContents();
        Map<Item, Integer> fullInventory = PlacementEngine.getInventoryContentsCached();
        boolean liveInventorySwapAllowed = allowLiveInventorySwapsDuringBuild();
        /*? if >=26.1 {*//*
        Inventory playerInv = player.getInventory();
        Item heldBuildItem = playerInv.getItem(playerInv.getSelectedSlot()).getItem();
        *//*?} else {*/
        PlayerInventory playerInv = player.getInventory();
        /*? if >=1.21.5 {*//*
        Item heldBuildItem = playerInv.getStack(playerInv.getSelectedSlot()).getItem();
        *//*?} else {*/
        Item heldBuildItem = playerInv.getStack(playerInv.selectedSlot).getItem();
        /*?}*/
        /*?}*/

        // Dependency-aware sorting
        // Freestanding blocks (tier 0) are tried before blocks that need
        // adjacent support (tier 1: torches, flowers, rails, etc.).  This
        // ensures support structures are built before dependent blocks
        // are attempted, reducing wasted placement attempts.
        Comparator<BlockPos> comparator;
        if (sortMode == SortMode.BOTTOM_UP) {
            comparator = Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt((BlockPos p) -> {
                        int sx2 = p.getX() - anchor.getX();
                        int sy2 = p.getY() - anchor.getY();
                        int sz2 = p.getZ() - anchor.getZ();
                        return BlockDependency.getTier(schematic.getBlockState(sx2, sy2, sz2));
                    })
                .thenComparingInt(this::getChronologicalRegionMajor)
                .thenComparingInt(this::getChronologicalRegionMinor)
                .thenComparingInt(p -> -getConfirmedBuildAdjacencyScore(p, world))
                .thenComparingInt(p -> getBuildItemPriority(p, heldBuildItem, hotbarInventory))
                /*? if >=26.1 {*//*
                .thenComparingDouble(p -> eyePos.distanceToSqr(Vec3.atCenterOf(p)));
                *//*?} else {*/
                .thenComparingDouble(p -> eyePos.squaredDistanceTo(Vec3d.ofCenter(p)));
                /*?}*/
        } else if (sortMode == SortMode.TOP_DOWN) {
            comparator = Comparator.<BlockPos>comparingInt((BlockPos p) -> -p.getY())
                .thenComparingInt((BlockPos p) -> {
                        int sx2 = p.getX() - anchor.getX();
                        int sy2 = p.getY() - anchor.getY();
                        int sz2 = p.getZ() - anchor.getZ();
                        return BlockDependency.getTier(schematic.getBlockState(sx2, sy2, sz2));
                    })
                .thenComparingInt(this::getChronologicalRegionMajor)
                .thenComparingInt(this::getChronologicalRegionMinor)
                .thenComparingInt(p -> -getConfirmedBuildAdjacencyScore(p, world))
                .thenComparingInt(p -> getBuildItemPriority(p, heldBuildItem, hotbarInventory))
                /*? if >=26.1 {*//*
                .thenComparingDouble(p -> eyePos.distanceToSqr(Vec3.atCenterOf(p)));
                *//*?} else {*/
                .thenComparingDouble(p -> eyePos.squaredDistanceTo(Vec3d.ofCenter(p)));
                /*?}*/
        } else {
            comparator = Comparator.<BlockPos>comparingInt((BlockPos p) -> {
                        int sx2 = p.getX() - anchor.getX();
                        int sy2 = p.getY() - anchor.getY();
                        int sz2 = p.getZ() - anchor.getZ();
                        return BlockDependency.getTier(schematic.getBlockState(sx2, sy2, sz2));
                    })
                .thenComparingInt(this::getChronologicalRegionMajor)
                .thenComparingInt(this::getChronologicalRegionMinor)
                .thenComparingInt(p -> -getConfirmedBuildAdjacencyScore(p, world))
                .thenComparingInt(p -> getBuildItemPriority(p, heldBuildItem, hotbarInventory))
                /*? if >=26.1 {*//*
                .thenComparingDouble(p -> eyePos.distanceToSqr(Vec3.atCenterOf(p)));
                *//*?} else {*/
                .thenComparingDouble(p -> eyePos.squaredDistanceTo(Vec3d.ofCenter(p)));
                /*?}*/
        }
        candidates.sort(comparator);
        filterChronologicalBuildFrontier(candidates);

        Item materialRunItem = chooseBuildMaterialRun(
                candidates, world, heldBuildItem, hotbarInventory, fullInventory);
        if (materialRunItem != null && materialRunItem != Items.AIR) {
            int beforeMaterialFilter = candidates.size();
            candidates.removeIf(pos -> getRequiredBuildItem(pos) != materialRunItem);
            if (beforeMaterialFilter != candidates.size()) {
                PacketTelemetry.mark("build material run filter item=" + materialRunItem
                        + " kept=" + candidates.size()
                        + " dropped=" + (beforeMaterialFilter - candidates.size()));
            }
            if (candidates.isEmpty()) {
                clearBuildMaterialRun("filtered-empty");
                return false;
            }
        }

        boolean inventorySwapAllowedThisTick = liveInventorySwapAllowed;
        boolean releasedDeferredInventorySwap = false;
        int inventorySwapWaitTicks = 0;
        String inventorySwapGateReason = buildInventorySwapGateReason();
        if (liveInventorySwapAllowed) {
            clearBuildInventorySwapWait();
        } else if (materialRunItem != null
                && materialRunItem != Items.AIR
                && !isBuildItemFastLaneEligible(materialRunItem, heldBuildItem, hotbarInventory)
                && fullInventory.getOrDefault(materialRunItem, 0) > 0) {
            inventorySwapWaitTicks = tickBuildInventorySwapWait(materialRunItem);
            if (inventorySwapWaitTicks >= BUILD_INVENTORY_SWAP_WAIT_LIMIT_TICKS
                    && canReleaseDeferredBuildInventorySwap()) {
                inventorySwapAllowedThisTick = true;
                releasedDeferredInventorySwap = true;
                PacketTelemetry.mark("build inventory-swap release item=" + materialRunItem
                        + " wait=" + inventorySwapWaitTicks
                        + " gate=" + inventorySwapGateReason);
                clearBuildInventorySwapWait();
            }
        }

        Set<Item> missing = new HashSet<>();
        int dbgDepSkip = 0, dbgPlaceFail = 0, dbgScaffold = 0;
        int deferredInventorySwapCandidates = 0;
        Item deferredInventorySwapItem = null;
        BlockPos dbgFirstPlaceFail = null;
        BlockState dbgFirstPlaceFailTarget = null;
        BlockState dbgFirstPlaceFailExisting = null;

        List<BlockPos>   batchTargets = null;
        List<BlockState> batchStates  = null;
        Item             batchItem    = null;
        boolean allowBatchPlacement = false;

        boolean sawFastLaneCandidate = false;
        for (int lane = 0; lane < 2; lane++) {
            boolean fastLaneOnly = lane == 0;
            for (BlockPos worldPos : candidates) {
                if (isBuildTargetCoolingDown(worldPos)) {
                    dbgCoolingDown++;
                    continue;
                }
                int sx = worldPos.getX() - anchor.getX();
                int sy = worldPos.getY() - anchor.getY();
                int sz = worldPos.getZ() - anchor.getZ();
                BlockState target = schematic.getBlockState(sx, sy, sz);

                // Dependency check: skip blocks whose support is missing
                // Torches without a wall, flowers without a floor, rails
                // without ground, etc. are silently skipped.  They will be
                // retried once their support blocks have been placed.
                if (!BlockDependency.isReadyToPlace(world, worldPos, target)) {
                    dbgDepSkip++;
                    continue;
                }

                // Skip blocks whose materials we already gave up on
                // If restock failed and this item was marked skipped, don't
                // waste time trying — move on to blocks we CAN place.
                if (!skippedItems.isEmpty()) {
                    Item reqItem = isLiquidSource(target)
                            ? getLiquidBucketItem(target)
                            : target.getBlock().asItem();
                    if (reqItem != null && skippedItems.contains(reqItem)) continue;
                }

                // liquid source block → bucket placement
                if (isLiquidSource(target)) {
                    Item bucketItem = getLiquidBucketItem(target);
                    if (bucketItem == null) continue;
                    boolean fastLaneEligible = isBuildItemFastLaneEligible(bucketItem, heldBuildItem, hotbarInventory);
                    if (fastLaneOnly != fastLaneEligible) continue;
                    if (fastLaneEligible) {
                        sawFastLaneCandidate = true;
                    }

                    // Check if we actually have this bucket in inventory.
                    // Unlike solid blocks, don't add to 'missing' on placement
                    // failure — placeLiquid can fail due to angle/position issues
                    // even when we have buckets, and we want to try other positions.
                    if (fullInventory.getOrDefault(bucketItem, 0) <= 0) {
                        missing.add(bucketItem);
                        continue;
                    }
                    if (missing.contains(bucketItem)) continue;
                    if (!fastLaneOnly && !inventorySwapAllowedThisTick) {
                        deferredInventorySwapCandidates++;
                        if (deferredInventorySwapItem == null) {
                            deferredInventorySwapItem = bucketItem;
                        }
                        continue;
                    }

                    if (PlacementEngine.placeLiquid(worldPos, target, !fastLaneOnly && inventorySwapAllowedThisTick)) {
                        lastMissingItems.clear();
                        clearBuildInventorySwapWait();
                        return true;
                    }
                    // Don't add to missing — placement failed for positional
                    // reasons, not because we lack the item.
                    continue;
                }

                // normal block placement
                Item requiredItem = target.getBlock().asItem();
                boolean fastLaneEligible = isBuildItemFastLaneEligible(requiredItem, heldBuildItem, hotbarInventory);
                if (fastLaneOnly != fastLaneEligible) continue;
                if (fastLaneEligible) {
                    sawFastLaneCandidate = true;
                }
                // Skip items we already know are missing (avoid redundant hotbar scans)
                if (missing.contains(requiredItem)) continue;
                int inventoryCount = fullInventory.getOrDefault(requiredItem, 0);
                if (inventoryCount <= 0) {
                    missing.add(requiredItem);
                    continue;
                }
                if (!fastLaneOnly && !inventorySwapAllowedThisTick) {
                    deferredInventorySwapCandidates++;
                    if (deferredInventorySwapItem == null) {
                        deferredInventorySwapItem = requiredItem;
                    }
                    continue;
                }

                // If a scaffold block (placed by Baritone) occupies this
                // position, break it first so the correct block can be placed.
                // PlacementEngine's correction mechanism will break the scaffold,
                // and on the next cycle the position will be air → normal placement.
                BlockState existing = world.getBlockState(worldPos);
                if (isEffectivelyPlaced(existing, target)) {
                    invalidateChunkDiffCache(worldPos);
                    continue;
                }
                /*? if >=26.1 {*//*
                if (!existing.isAir() && !existing.canBeReplaced()
                *//*?} else {*/
                if (!existing.isAir() && !existing.isReplaceable()
                /*?}*/
                        && existing.getBlock() == target.getBlock()
                        && !PlacementEngine.isOrientationMismatch(existing, target)) {
                    invalidateChunkDiffCache(worldPos);
                    PacketTelemetry.mark("build skip occupied same-block target=" + worldPos
                            + " desired=" + target.getBlock()
                            + " actual=" + existing.getBlock());
                    continue;
                }
                /*? if >=26.1 {*//*
                if (!existing.isAir() && !existing.canBeReplaced()
                *//*?} else {*/
                if (!existing.isAir() && !existing.isReplaceable()
                /*?}*/
                        && existing.getBlock() != target.getBlock()) {
                    dbgScaffold++;
                    if (PlacementEngine.placeBlock(
                            worldPos, target, !fastLaneOnly && inventorySwapAllowedThisTick,
                            createBuildSupportFilter(worldPos, world, playerPos))) {
                        lastMissingItems.clear();
                        clearBuildInventorySwapWait();
                        return true;
                    }
                    if (PlacementEngine.wasLastPlacementBlockedByNoVisibleHit(worldPos)) {
                        String probeFailure = PlacementEngine.consumeLastProbeFailure();
                        if (isWrongSideProbeFailure(probeFailure)) {
                            recoverWrongSidePlacement(worldPos, target, probeFailure);
                        } else {
                            recordNoVisibleHitPlacementBlock(worldPos, target);
                        }
                    } else if (PlacementEngine.wasLastPlacementBlockedByBodyClearance(worldPos)) {
                        coolDownPlacementTarget(worldPos, PLACEMENT_START_FAILURE_TARGET_COOLDOWN_TICKS);
                        PacketTelemetry.mark("build body-clearance cooldown target=" + worldPos
                                + " desired=" + target.getBlock());
                    }
                    // PlacementEngine busy or can't start — skip for now
                    continue;
                }

                if (allowBatchPlacement && batchTargets == null && PlacementEngine.canBatchPlace()) {
                    batchTargets = new ArrayList<>(9);
                    batchStates  = new ArrayList<>(9);
                    batchItem    = requiredItem;
                }
                if (batchTargets != null
                        && batchTargets.size() < 9
                        && requiredItem == batchItem) {
                    /*? if >=26.1 {*//*
                    batchTargets.add(worldPos.immutable());
                    *//*?} else {*/
                    batchTargets.add(worldPos.toImmutable());
                    /*?}*/
                    batchStates.add(target);
                    continue;
                }

                if (PlacementEngine.placeBlock(
                        worldPos, target, !fastLaneOnly && inventorySwapAllowedThisTick,
                        createBuildSupportFilter(worldPos, world, playerPos))) {
                    lastMissingItems.clear();
                    clearBuildInventorySwapWait();
                    placeDebugCooldown = 0;
                    return true;
                }
                if (PlacementEngine.wasLastPlacementBlockedByNoVisibleHit(worldPos)) {
                    String probeFailure = PlacementEngine.consumeLastProbeFailure();
                    if (isWrongSideProbeFailure(probeFailure)) {
                        recoverWrongSidePlacement(worldPos, target, probeFailure);
                    } else {
                        recordNoVisibleHitPlacementBlock(worldPos, target);
                    }
                    continue;
                } else if (PlacementEngine.wasLastPlacementBlockedByBodyClearance(worldPos)) {
                    coolDownPlacementTarget(worldPos, PLACEMENT_START_FAILURE_TARGET_COOLDOWN_TICKS);
                    PacketTelemetry.mark("build body-clearance cooldown target=" + worldPos
                            + " desired=" + target.getBlock());
                    continue;
                }

                // Track first placeBlock failure for debug
                dbgPlaceFail++;
                if (dbgFirstPlaceFail == null) {
                    /*? if >=26.1 {*//*
                    dbgFirstPlaceFail = worldPos.immutable();
                    *//*?} else {*/
                    dbgFirstPlaceFail = worldPos.toImmutable();
                    /*?}*/
                    dbgFirstPlaceFailTarget = target;
                    dbgFirstPlaceFailExisting = existing;
                }
            }
        }

        if (batchTargets != null && !batchTargets.isEmpty()) {
            int placed = PlacementEngine.placeBatch(batchTargets, batchStates,
                    inventorySwapAllowedThisTick);
            if (placed > 0) {
                lastMissingItems.clear();
                clearBuildInventorySwapWait();
                placeDebugCooldown = 0;
                return true;
            }
        }

        // Debug output when candidates exist but no placement started
        if (LOGGER.isDebugEnabled() && statusMessages && !candidates.isEmpty() && placeDebugCooldown <= 0) {
            placeDebugCooldown = 100;
            StringBuilder sb = new StringBuilder("[PlaceDbg] ");
            sb.append(candidates.size()).append(" candidates, none placed. ");
            if (dbgDepSkip > 0) sb.append("depSkip=").append(dbgDepSkip).append(" ");
            if (dbgPlaceFail > 0) sb.append("placeFail=").append(dbgPlaceFail).append(" ");
            if (dbgScaffold > 0) sb.append("scaffold=").append(dbgScaffold).append(" ");
            sb.append("missing=").append(missing.size());
            if (dbgFirstPlaceFail != null) {
                sb.append(" First fail: ").append(dbgFirstPlaceFail.getX())
                  .append(" ").append(dbgFirstPlaceFail.getY())
                  .append(" ").append(dbgFirstPlaceFail.getZ())
                  .append(" want=").append(dbgFirstPlaceFailTarget.getBlock())
                  .append(" existing=").append(dbgFirstPlaceFailExisting.getBlock())
                  .append(" phase=").append(PlacementEngine.getPhase());
            }
            LOGGER.debug(sb.toString());
        }
        if (placeDebugCooldown > 0) placeDebugCooldown--;

        if (deferredInventorySwapCandidates > 0) {
            if (inventorySwapWaitTicks <= 0) {
                inventorySwapWaitTicks = tickBuildInventorySwapWait(deferredInventorySwapItem);
            }
            PacketTelemetry.mark("build wait inventory-swap candidates=" + deferredInventorySwapCandidates
                    + " fastLaneSeen=" + sawFastLaneCandidate
                    + " calm=" + SetbackMonitor.get().isCalm()
                    + " failures=" + PlacementEngine.getConsecutiveFailures()
                    + " rejections=" + PlacementEngine.getConsecutiveRejections()
                    + " wait=" + inventorySwapWaitTicks + "/" + BUILD_INVENTORY_SWAP_WAIT_LIMIT_TICKS
                    + " gate=" + inventorySwapGateReason
                    + " released=" + releasedDeferredInventorySwap);
            if (inventorySwapWaitTicks < BUILD_INVENTORY_SWAP_WAIT_LIMIT_TICKS) {
                placementFailurePauseTicks = Math.max(
                        placementFailurePauseTicks, PLACEMENT_START_FAILURE_PAUSE_TICKS);
                walkAttemptCooldown = Math.max(
                        walkAttemptCooldown, PLACEMENT_START_FAILURE_PAUSE_TICKS);
                noProgressTicks = 0;
            } else {
                clearBuildMaterialRun("inventory-swap-gated");
                noProgressTicks = Math.max(noProgressTicks, NO_PROGRESS_WALK_RECHECK_TICKS - 1);
            }
            lastMissingItems.clear();
            return false;
        }

        if (dbgPlaceFail > 0 && dbgFirstPlaceFail != null) {
            beginPlacementStartFailurePause(dbgFirstPlaceFail);
        }

        lastMissingItems = missing;
        return false;
    }

    /*? if >=26.1 {*//*
    private int getConfirmedBuildAdjacencyScore(BlockPos worldPos, Level world) {
    *//*?} else {*/
    private int getConfirmedBuildAdjacencyScore(BlockPos worldPos, World world) {
    /*?}*/
        if (worldPos == null || world == null || schematic == null || anchor == null) {
            return 0;
        }

        int score = 0;
        for (Direction dir : Direction.values()) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = worldPos.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = worldPos.offset(dir);
            /*?}*/
            int sx = neighbor.getX() - anchor.getX();
            int sy = neighbor.getY() - anchor.getY();
            int sz = neighbor.getZ() - anchor.getZ();
            if (!schematic.contains(sx, sy, sz)) continue;

            BlockState expected = schematic.getBlockState(sx, sy, sz);
            if (expected.isAir()) continue;
            if (!isEffectivelyPlaced(world.getBlockState(neighbor), expected)) continue;

            if (dir == Direction.DOWN) {
                score += 4;
            } else if (dir == Direction.UP) {
                score += 1;
            } else {
                score += 3;
            }
        }
        return score;
    }

    /*? if >=26.1 {*//*
    private Predicate<BlockPos> createBuildSupportFilter(BlockPos targetPos, Level world) {
    *//*?} else {*/
    private Predicate<BlockPos> createBuildSupportFilter(BlockPos targetPos, World world) {
    /*?}*/
        return createBuildSupportFilter(targetPos, world, null);
    }

    /*? if >=26.1 {*//*
    private Predicate<BlockPos> createBuildSupportFilter(BlockPos targetPos, Level world, BlockPos stancePos) {
    *//*?} else {*/
    private Predicate<BlockPos> createBuildSupportFilter(BlockPos targetPos, World world, BlockPos stancePos) {
    /*?}*/
        return createBuildPlacementContract(targetPos, world, stancePos).supportFilter();
    }

    /*? if >=26.1 {*//*
    private BuildPlacementContract createBuildPlacementContract(BlockPos targetPos, Level world, BlockPos stancePos) {
    *//*?} else {*/
    private BuildPlacementContract createBuildPlacementContract(BlockPos targetPos, World world, BlockPos stancePos) {
    /*?}*/
        if (targetPos == null || world == null || schematic == null || anchor == null) {
            return new BuildPlacementContract(targetPos, Set.of(), false);
        }

        Set<BlockPos> allowedSupports = new LinkedHashSet<>();
        for (Direction dir : Direction.values()) {
            /*? if >=26.1 {*//*
            BlockPos support = targetPos.relative(dir);
            *//*?} else {*/
            BlockPos support = targetPos.offset(dir);
            /*?}*/
            if (stancePos != null && isPlacementAttemptCoolingDown(targetPos, support, stancePos)) {
                continue;
            }
            if (isStableBuildPlacementSupport(support, world)) {
                allowedSupports.add(immutableBlockPos(support));
            }
        }

        return new BuildPlacementContract(
                immutableBlockPos(targetPos), Set.copyOf(allowedSupports), true);
    }

    /*? if >=26.1 {*//*
    private boolean hasBuildPlacementFrontierSupport(BlockPos targetPos, BlockState target, Level world) {
    *//*?} else {*/
    private boolean hasBuildPlacementFrontierSupport(BlockPos targetPos, BlockState target, World world) {
    /*?}*/
        if (targetPos == null || target == null || world == null) {
            return false;
        }
        return !createBuildPlacementContract(targetPos, world, null)
                .allowedSupports()
                .isEmpty();
    }

    /*? if >=26.1 {*//*
    private boolean isStableBuildPlacementSupport(BlockPos support, Level world) {
    *//*?} else {*/
    private boolean isStableBuildPlacementSupport(BlockPos support, World world) {
    /*?}*/
        if (support == null || world == null || schematic == null || anchor == null) {
            return false;
        }
        // A support is just a click anchor for vanilla placement. ANY
        // physically solid face in the LIVE world is clickable, regardless
        // of whether the schematic happens to want a different block at
        // that position. Previously this method also required the live
        // block to match the schematic's expected state when the support
        // was inside the schematic footprint — that turned the hologram
        // into a click-reference rather than a goal description, and
        // rejected nearly every candidate (including natural terrain and
        // already-placed wrong-color blocks) as occluded. Cleared blocks
        // and illegal-block removal are handled by separate passes.
        return isPhysicalPlacementSupport(support, world);
    }

    /*? if >=26.1 {*//*
    private boolean isConfirmedBuildSupport(BlockPos support, Level world) {
    *//*?} else {*/
    private boolean isConfirmedBuildSupport(BlockPos support, World world) {
    /*?}*/
        if (support == null || world == null || schematic == null || anchor == null) {
            return false;
        }
        int sx = support.getX() - anchor.getX();
        int sy = support.getY() - anchor.getY();
        int sz = support.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) {
            return false;
        }

        BlockState expected = schematic.getBlockState(sx, sy, sz);
        return !expected.isAir() && isEffectivelyPlaced(world.getBlockState(support), expected);
    }

    /*? if >=26.1 {*//*
    private boolean isPhysicalPlacementSupport(BlockPos support, Level world) {
    *//*?} else {*/
    private boolean isPhysicalPlacementSupport(BlockPos support, World world) {
    /*?}*/
        if (support == null || world == null) {
            return false;
        }
        BlockState state = world.getBlockState(support);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return isMovementBlocking(state);
    }

    private int getBuildItemPriority(BlockPos worldPos, Item heldBuildItem, Map<Item, Integer> hotbarInventory) {
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        BlockState target = schematic.getBlockState(sx, sy, sz);
        Item requiredItem = isLiquidSource(target)
                ? getLiquidBucketItem(target)
                : target.getBlock().asItem();
        if (requiredItem == null || requiredItem == Items.AIR) {
            return 3;
        }
        if (requiredItem == heldBuildItem) {
            return 0;
        }
        if (hotbarInventory.getOrDefault(requiredItem, 0) > 0) {
            return 1;
        }
        return 2;
    }

    private boolean isBuildItemFastLaneEligible(Item requiredItem, Item heldBuildItem,
                                                Map<Item, Integer> hotbarInventory) {
        if (requiredItem == null || requiredItem == Items.AIR) {
            return false;
        }
        return requiredItem == heldBuildItem
                || hotbarInventory.getOrDefault(requiredItem, 0) > 0;
    }

    private boolean isCandidateStableForPlacement(BlockPos playerPos, BlockPos targetPos) {
        int dy = targetPos.getY() - playerPos.getY();
        if (dy <= 3) {
            return true;
        }
        int dx = targetPos.getX() - playerPos.getX();
        int dz = targetPos.getZ() - playerPos.getZ();
        int horizontalSq = dx * dx + dz * dz;
        // Building often needs to place one layer above head height
        // from ground level. Keep the guard for extreme below-angle
        // attempts, but allow close vertical wall placements.
        return dy <= 4 && horizontalSq <= 4;
    }

    /*? if >=26.1 {*//*
    private boolean hasBuildOpportunityFromHere(LocalPlayer player, Level world, boolean allowRelaxed) {
    *//*?} else {*/
    private boolean hasBuildOpportunityFromHere(ClientPlayerEntity player, World world, boolean allowRelaxed) {
    /*?}*/
        if (player == null || world == null || schematic == null || anchor == null) {
            return false;
        }

        int maxReach = (int) Math.ceil(range);
        double rangeSq = range * range;
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        int activeBandTopY = getBottomUpActiveBandTopY(world, getRenderWindow(playerPos));
        boolean relaxedOpportunity = false;

        for (int dy = -maxReach; dy <= maxReach; dy++) {
            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos worldPos = playerPos.offset(dx, dy, dz);
                    if (eyePos.distanceToSqr(Vec3.atCenterOf(worldPos)) > rangeSq) continue;
                    *//*?} else {*/
                    BlockPos worldPos = playerPos.add(dx, dy, dz);
                    if (eyePos.squaredDistanceTo(Vec3d.ofCenter(worldPos)) > rangeSq) continue;
                    /*?}*/
                    if (playerOverlapsBlock(player, worldPos)
                            || playerTooCloseForServerPlacement(player, worldPos)) continue;

                    int sx = worldPos.getX() - anchor.getX();
                    int sy = worldPos.getY() - anchor.getY();
                    int sz = worldPos.getZ() - anchor.getZ();
                    if (!schematic.contains(sx, sy, sz)) continue;

                    BlockState target = schematic.getBlockState(sx, sy, sz);
                    if (target.isAir()) continue;
                    if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                    if (isAutoCreatedPart(target)) continue;
                    if (!matchesCurrentBuildPass(target)) continue;
                    if (worldPos.getY() > activeBandTopY) continue;
                    if (isEffectivelyPlaced(world.getBlockState(worldPos), target)) continue;

                    if (isBuildTargetCoolingDown(worldPos)) continue;
                    if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) continue;
                    if (!BlockDependency.isReadyToPlace(world, worldPos, target)) continue;
                    if (!hasBuildPlacementFrontierSupport(worldPos, target, world)) continue;
                    if (wouldTrapPlayer(player, worldPos, world)) continue;
                    boolean stableForPlacement = isCandidateStableForPlacement(playerPos, worldPos);
                    boolean canPlaceFromCurrentPosition =
                            PlacementEngine.canPlaceFromCurrentPosition(
                                    worldPos, target, player, world,
                                    createBuildSupportFilter(worldPos, world, playerPos));
                    if (stableForPlacement && canPlaceFromCurrentPosition) {
                        return true;
                    }
                    // "Relaxed" build opportunities are only useful for
                    // builder handoff if there is at least some placement
                    // path from the current stance. Purely occluded targets
                    // just bounce BUILDING back into WALKING_TO_BUILD.
                    if (canPlaceFromCurrentPosition) {
                        relaxedOpportunity = true;
                    }
                }
            }
        }
        return allowRelaxed && relaxedOpportunity;
    }

    /*? if >=26.1 {*//*
    private boolean hasBuildHandoffOpportunityFromHere(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean hasBuildHandoffOpportunityFromHere(ClientPlayerEntity player, World world) {
    /*?}*/
        return hasBuildOpportunityFromHere(player, world, false);
    }

    /*? if >=26.1 {*//*
    private boolean hasStableBuildTargetFromHere(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean hasStableBuildTargetFromHere(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null || schematic == null || anchor == null) {
            return false;
        }

        int maxReach = (int) Math.ceil(range);
        double rangeSq = range * range;
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        int activeBandTopY = getBottomUpActiveBandTopY(world, getRenderWindow(playerPos));

        for (int dy = -maxReach; dy <= maxReach; dy++) {
            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos worldPos = playerPos.offset(dx, dy, dz);
                    if (eyePos.distanceToSqr(Vec3.atCenterOf(worldPos)) > rangeSq) continue;
                    *//*?} else {*/
                    BlockPos worldPos = playerPos.add(dx, dy, dz);
                    if (eyePos.squaredDistanceTo(Vec3d.ofCenter(worldPos)) > rangeSq) continue;
                    /*?}*/
                    if (playerOverlapsBlock(player, worldPos)
                            || playerTooCloseForServerPlacement(player, worldPos)) continue;

                    int sx = worldPos.getX() - anchor.getX();
                    int sy = worldPos.getY() - anchor.getY();
                    int sz = worldPos.getZ() - anchor.getZ();
                    if (!schematic.contains(sx, sy, sz)) continue;

                    BlockState target = schematic.getBlockState(sx, sy, sz);
                    if (target.isAir()) continue;
                    if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                    if (isAutoCreatedPart(target)) continue;
                    if (!matchesCurrentBuildPass(target)) continue;
                    if (worldPos.getY() > activeBandTopY) continue;
                    if (isEffectivelyPlaced(world.getBlockState(worldPos), target)) continue;
                    if (isBuildTargetCoolingDown(worldPos)) continue;
                    if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) continue;
                    if (!BlockDependency.isReadyToPlace(world, worldPos, target)) continue;
                    if (!hasBuildPlacementFrontierSupport(worldPos, target, world)) continue;
                    if (wouldTrapPlayer(player, worldPos, world)) continue;
                    if (isCandidateStableForPlacement(playerPos, worldPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private BlockState getDesiredBuildStateAt(BlockPos worldPos) {
        if (worldPos == null || schematic == null || anchor == null) {
            return null;
        }
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) {
            return null;
        }
        BlockState desired = schematic.getBlockState(sx, sy, sz);
        if (desired == null || desired.isAir() || isAutoCreatedPart(desired)) {
            return null;
        }
        return desired;
    }

    private BlockState getRawSchematicStateAt(BlockPos worldPos) {
        if (worldPos == null || schematic == null || anchor == null) {
            return null;
        }
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) {
            return null;
        }
        return schematic.getBlockState(sx, sy, sz);
    }

    private BlockPos sanitizeBuildStandingPosition(BlockPos targetPos, BlockPos standPos,
                                                   BlockPos playerPos) {
        if (targetPos == null || standPos == null || playerPos == null) {
            return standPos;
        }
        if (standPos.getY() < targetPos.getY() - 2
                && targetPos.getY() >= playerPos.getY() - 3) {
            return null;
        }
        if (targetPos.getY() >= playerPos.getY() - 1) {
            if (standPos.getY() < playerPos.getY() - 1) {
                return null;
            }
            if (standPos.getY() < targetPos.getY() - 2) {
                return null;
            }
        }
        // Don't walk underneath a target that is at or above the player.
        // Those concave stances correlate with "visible hologram but no
        // placement attempts" because the build scan ends up looking up
        // through the structure instead of working from the exposed face.
        if (targetPos.getY() >= playerPos.getY()) {
            int maxVerticalGap = Math.max(2, (int) Math.ceil(range));
            if (standPos.getY() < targetPos.getY() - maxVerticalGap) {
                return null;
            }
            if (standPos.getY() < playerPos.getY() - 2) {
                return null;
            }
        }
        return standPos;
    }

    private String posLabel(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // UTILITY / STATUS

    public int countRemaining() {
        if (!isLoaded()) return -1;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return -1;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return -1;
        /*?}*/

        // Return cached value if computed recently
        /*? if >=26.1 {*//*
        long tick = mc.level.getGameTime();
        *//*?} else {*/
        long tick = mc.world.getTime();
        /*?}*/
        if (tick - remainingCacheTick < REMAINING_CACHE_TTL && cachedCountRemaining >= 0) {
            return cachedCountRemaining;
        }

        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/
        int remaining = 0;
        for (long key : schematicBlocksByChunk.keySet()) {
            int chunkX = chunkXFromKey(key);
            int chunkZ = chunkZFromKey(key);
            /*? if >=26.1 {*//*
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            *//*?} else {*/
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            /*?}*/
            remaining += getUnresolvedChunkEntries(world, tick, key).size();
        }
        remainingCacheTick = tick;
        cachedCountRemaining = remaining;
        return remaining;
    }    public static List<String> listSchematics() {
        List<String> names = new ArrayList<>();
        Path dir = getSchematicsDir();
        if (!Files.isDirectory(dir)) return names;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.litematic")) {
            for (Path entry : stream) {
                String fname = entry.getFileName().toString();
                names.add(fname.substring(0, fname.length() - ".litematic".length()));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to list schematics directory", e);
        }
        return names;
    }

    public static Path getSchematicsDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    // CHECKPOINT / RESUME

    public void saveCheckpoint() {
        if (schematicFile != null && anchor != null) {
            /*? if >=26.1 {*//*
            Minecraft mc = Minecraft.getInstance();
            *//*?} else {*/
            MinecraftClient mc = MinecraftClient.getInstance();
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos playerPos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
            *//*?} else {*/
            BlockPos playerPos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
            /*?}*/
            PrinterCheckpoint.save(schematicFile, anchor, blocksPlaced, playerPos);
        }
    }

    public void restoreFromCheckpoint(PrinterCheckpoint.CheckpointData data, Path schematicPath) throws IOException {
        Path normalizedPath = schematicPath.normalize();
        this.schematic = LitematicaSchematic.load(normalizedPath);
        this.anchor = data.anchorPos();
        rebuildSchematicChunkIndex();
        this.blocksPlaced = data.blocksPlaced;
        this.schematicPath = normalizedPath;
        this.schematicFile = normalizedPath.getFileName().toString();
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        this.buildDimension = mc.level != null ? mc.level.dimension() : null;
        *//*?} else {*/
        this.buildDimension = mc.world != null ? mc.world.getRegistryKey() : null;
        /*?}*/
    }

    public String getSchematicFile() { return schematicFile; }

    // MATERIALS REPORT

    public PrinterResourceManager.MaterialsReport analyzeMaterials() {
        if (!isLoaded()) return PrinterResourceManager.MaterialsReport.EMPTY;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return PrinterResourceManager.MaterialsReport.EMPTY;
        *//*?} else {*/
        if (mc.world == null) return PrinterResourceManager.MaterialsReport.EMPTY;
        /*?}*/
        /*? if >=26.1 {*//*
        return PrinterResourceManager.analyzeMaterials(schematic, anchor, mc.level);
        *//*?} else {*/
        return PrinterResourceManager.analyzeMaterials(schematic, anchor, mc.world);
        /*?}*/
    }
}
