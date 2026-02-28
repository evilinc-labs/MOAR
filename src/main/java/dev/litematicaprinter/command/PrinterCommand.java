package dev.litematicaprinter.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.litematicaprinter.LitematicaPrinterMod;
import dev.litematicaprinter.printer.SchematicPrinter;
import dev.litematicaprinter.schematic.LitematicaDetector;
import dev.litematicaprinter.schematic.PrinterCheckpoint;
import dev.litematicaprinter.schematic.ChestIndexer;
import dev.litematicaprinter.schematic.PrinterResourceManager;
import dev.litematicaprinter.util.ChatHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Registers all {@code /printer} client commands using Fabric API's
 * client command system.
 *
 * <pre>
 * /printer load &lt;file&gt;          – load a .litematic and anchor at current pos
 * /printer unload                – unload the current schematic
 * /printer here                  – re-anchor at current position
 * /printer pos &lt;x&gt; &lt;y&gt; &lt;z&gt;      – set anchor to specific coordinates
 * /printer status                – show progress info
 * /printer list                  – show available schematics
 * /printer detect                – detect &amp; load Litematica's active placement
 * /printer resume                – restore from last checkpoint
 * /printer materials             – show bill of materials breakdown
 * /printer toggle                – toggle the printer on/off
 * /printer autobuild             – toggle AutoBuild mode
 * /printer supply add            – mark block at feet as supply chest
 * /printer supply add &lt;x&gt; &lt;y&gt; &lt;z&gt; – mark specific pos as supply chest
 * /printer supply remove         – unmark supply chest at feet
 * /printer supply list           – show all designated supply chests
 * /printer supply clear          – remove all supply chest designations
 * /printer supply scan           – show supply chest index summary
 * </pre>
 */
public final class PrinterCommand {

    private PrinterCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("printer");

            // /printer load <file>
            root.then(ClientCommandManager.literal("load")
                    .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                for (String name : SchematicPrinter.listSchematics()) {
                                    builder.suggest(name);
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String filename = StringArgumentType.getString(ctx, "file");
                                return loadSchematic(filename);
                            })
                    )
            );

            // /printer unload
            root.then(ClientCommandManager.literal("unload")
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        if (!printer.isLoaded()) {
                            ChatHelper.info("No schematic loaded.");
                            return 0;
                        }
                        printer.unload();
                        ChatHelper.info("Schematic unloaded.");
                        return 1;
                    })
            );

            // /printer here
            root.then(ClientCommandManager.literal("here")
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null) return 0;

                        if (!printer.isLoaded()) {
                            ChatHelper.info("§cNo schematic loaded.");
                            return 0;
                        }
                        BlockPos pos = mc.player.getBlockPos();
                        printer.setAnchor(pos);
                        ChatHelper.info("Anchor set to §e" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        return 1;
                    })
            );

            // /printer pos <x> <y> <z>
            root.then(ClientCommandManager.literal("pos")
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> {
                                                SchematicPrinter printer = getPrinter();
                                                if (!printer.isLoaded()) {
                                                    ChatHelper.info("§cNo schematic loaded.");
                                                    return 0;
                                                }

                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                printer.setAnchor(new BlockPos(x, y, z));
                                                ChatHelper.info("Anchor set to §e" + x + " " + y + " " + z);
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /printer status
            root.then(ClientCommandManager.literal("status")
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        if (!printer.isLoaded()) {
                            ChatHelper.info("No schematic loaded.");
                            return 1;
                        }

                        ChatHelper.info("§lSchematic Printer Status");
                        ChatHelper.info("File: §b" + printer.getSchematic().getName());
                        ChatHelper.info("Author: §7" + printer.getSchematic().getAuthor());
                        ChatHelper.info("Size: §e"
                                + printer.getSchematic().getSizeX() + "x"
                                + printer.getSchematic().getSizeY() + "x"
                                + printer.getSchematic().getSizeZ());
                        ChatHelper.info("Total blocks: §e" + printer.getSchematic().getTotalNonAir());

                        BlockPos anchor = printer.getAnchor();
                        ChatHelper.info("Anchor: §e" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());
                        ChatHelper.info("Placed (session): §a" + printer.getBlocksPlaced());
                        ChatHelper.info("Printing: " + (printer.isEnabled() ? "§aON" : "§cOFF"));

                        int remaining = printer.countRemaining();
                        if (remaining >= 0) {
                            int total = printer.getSchematic().getTotalNonAir();
                            int done = total - remaining;
                            int pct = total > 0 ? (done * 100 / total) : 100;
                            ChatHelper.info("Progress: §e" + done + "/" + total
                                    + " §7(§a" + pct + "%§7)");
                        }

                        return 1;
                    })
            );

            // /printer list
            root.then(ClientCommandManager.literal("list")
                    .executes(ctx -> {
                        List<String> schematics = SchematicPrinter.listSchematics();
                        if (schematics.isEmpty()) {
                            ChatHelper.info("No schematics found in §7"
                                    + SchematicPrinter.getSchematicsDir().toString());
                        } else {
                            ChatHelper.info("§lAvailable schematics (" + schematics.size() + "):");
                            for (String name : schematics) {
                                ChatHelper.info(" §7- §f" + name);
                            }
                        }
                        return 1;
                    })
            );

            // /printer detect
            root.then(ClientCommandManager.literal("detect")
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();

                        List<LitematicaDetector.DetectedPlacement> placements =
                                SchematicPrinter.detectAllPlacements();

                        if (placements.isEmpty()) {
                            ChatHelper.info("§cNo active Litematica placements detected.");
                            ChatHelper.info("§7Make sure you have a schematic loaded and placed in Litematica.");
                            return 0;
                        }

                        ChatHelper.info("§lDetected Litematica placements (" + placements.size() + "):");
                        for (int i = 0; i < placements.size(); i++) {
                            LitematicaDetector.DetectedPlacement p = placements.get(i);
                            ChatHelper.info(" §7" + (i + 1) + ". §f" + p.schematicPath().getFileName()
                                    + " §7at §e" + p.originX() + " " + p.originY() + " " + p.originZ()
                                    + " §7(\"" + p.name() + "\")");
                        }

                        if (printer.tryAutoDetect()) {
                            ChatHelper.info("§aLoaded: §f" + printer.getSchematic().getName()
                                    + " §7(" + printer.getSchematic().getTotalNonAir() + " blocks)");
                            BlockPos a = printer.getAnchor();
                            ChatHelper.info("Anchored at §e" + a.getX() + " " + a.getY() + " " + a.getZ());
                            ChatHelper.info("§7Use §f/printer toggle §7to start printing.");
                        }

                        return 1;
                    })
            );

            // /printer resume
            root.then(ClientCommandManager.literal("resume")
                    .executes(ctx -> {
                        if (!PrinterCheckpoint.exists()) {
                            ChatHelper.info("§cNo checkpoint found. Nothing to resume.");
                            return 0;
                        }

                        PrinterCheckpoint.CheckpointData data = PrinterCheckpoint.load();
                        if (data == null) {
                            ChatHelper.info("§cCheckpoint file is corrupt or empty.");
                            return 0;
                        }

                        SchematicPrinter printer = getPrinter();

                        Path file = SchematicPrinter.getSchematicsDir()
                                .resolve(data.schematicFile);
                        if (!Files.exists(file)) {
                            ChatHelper.info("§cSchematic file not found: §7" + data.schematicFile);
                            return 0;
                        }

                        try {
                            printer.restoreFromCheckpoint(data, file);
                            ChatHelper.info("§aResumed §f" + printer.getSchematic().getName());
                            ChatHelper.info("Anchor: §e" + data.anchorX + " " + data.anchorY + " " + data.anchorZ);
                            ChatHelper.info("Previously placed: §e" + data.blocksPlaced + " §7blocks");
                            ChatHelper.info("Checkpoint saved: §7" + data.timeSince());
                            ChatHelper.info("§7Use §f/printer toggle §7to continue.");
                            return 1;
                        } catch (IOException e) {
                            ChatHelper.info("§cFailed to resume: " + e.getMessage());
                            return 0;
                        }
                    })
            );

            // /printer materials
            root.then(ClientCommandManager.literal("materials")
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();

                        if (!printer.isLoaded()) {
                            ChatHelper.info("§cNo schematic loaded.");
                            return 0;
                        }

                        ChatHelper.info("§7Analyzing materials... (may take a moment)");

                        PrinterResourceManager.MaterialsReport report = printer.analyzeMaterials();
                        if (report.totalBlocks() == 0) {
                            ChatHelper.info("§cSchematic has no placeable blocks.");
                            return 0;
                        }

                        ChatHelper.info("§l§6Materials Report");
                        ChatHelper.info("Total blocks: §e" + report.totalBlocks());
                        ChatHelper.info("Placed: §a" + report.placedBlocks()
                                + " §7(§a" + report.percentComplete() + "%§7)");
                        ChatHelper.info("Remaining: §c" + report.missingCount());

                        // show top 10 most-needed items
                        List<Map.Entry<String, Integer>> top = report.missing().entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .limit(10)
                                .toList();

                        if (!top.isEmpty()) {
                            ChatHelper.info("§7Top missing items:");
                            for (var entry : top) {
                                String pretty = PrinterResourceManager.MaterialsReport.prettyName(entry.getKey());
                                int need = entry.getValue();
                                ChatHelper.info(" §7- §f" + pretty + "§7: §c" + need);
                            }
                        }

                        if (report.hasUnknownBlocks()) {
                            ChatHelper.info("§e⚠ Unrecognized blocks (newer MC version): §c"
                                    + report.unknownCount() + "§e blocks across §c"
                                    + report.unknownBlocks().size() + "§e type(s)");
                            report.unknownBlocks().entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .limit(5)
                                    .forEach(e -> ChatHelper.info("  §7- §c" + e.getKey()
                                            + " §7(×" + e.getValue() + ")"));
                            ChatHelper.info("§7These blocks cannot be placed on this MC version.");
                        }

                        return 1;
                    })
            );

            // /printer toggle
            root.then(ClientCommandManager.literal("toggle")
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        printer.toggle();
                        return 1;
                    })
            );

            // /printer autobuild
            root.then(ClientCommandManager.literal("autobuild")
                    .executes(ctx -> {
                        SchematicPrinter printer = getPrinter();
                        boolean newValue = !printer.isAutoBuild();
                        printer.setAutoBuild(newValue);
                        ChatHelper.info("AutoBuild: " + (newValue ? "§aON" : "§cOFF"));
                        return 1;
                    })
            );

            // ── /printer supply ─────────────────────────────────────────

            var supply = ClientCommandManager.literal("supply");

            // /printer supply add
            supply.then(ClientCommandManager.literal("add")
                    .executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null) return 0;
                        BlockPos pos = mc.player.getBlockPos();
                        if (PrinterResourceManager.addSupplyChest(pos)) {
                            ChatHelper.info("§aMarked supply chest at §e"
                                    + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        } else {
                            ChatHelper.info("§eThat position is already marked.");
                        }
                        return 1;
                    })
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> {
                                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                                int y = IntegerArgumentType.getInteger(ctx, "y");
                                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                                BlockPos pos = new BlockPos(x, y, z);
                                                if (PrinterResourceManager.addSupplyChest(pos)) {
                                                    ChatHelper.info("§aMarked supply chest at §e"
                                                            + x + " " + y + " " + z);
                                                } else {
                                                    ChatHelper.info("§eThat position is already marked.");
                                                }
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            // /printer supply remove
            supply.then(ClientCommandManager.literal("remove")
                    .executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player == null) return 0;
                        BlockPos pos = mc.player.getBlockPos();
                        if (PrinterResourceManager.removeSupplyChest(pos)) {
                            ChatHelper.info("§aRemoved supply chest at §e"
                                    + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        } else {
                            ChatHelper.info("§cNo supply chest at your position.");
                        }
                        return 1;
                    })
            );

            // /printer supply list
            supply.then(ClientCommandManager.literal("list")
                    .executes(ctx -> {
                        List<BlockPos> chests = PrinterResourceManager.getSupplyChests();
                        if (chests.isEmpty()) {
                            ChatHelper.info("No supply chests designated.");
                            ChatHelper.info("§7Use §f/printer supply add §7while standing at a chest.");
                        } else {
                            ChatHelper.info("§lSupply chests (" + chests.size() + "):");
                            for (BlockPos pos : chests) {
                                ChatHelper.info(" §7- §e" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                            }
                        }
                        return 1;
                    })
            );

            // /printer supply clear
            supply.then(ClientCommandManager.literal("clear")
                    .executes(ctx -> {
                        PrinterResourceManager.clearSupplyChests();
                        ChestIndexer.clearIndex();
                        ChatHelper.info("§aAll supply chest designations cleared.");
                        return 1;
                    })
            );

            // /printer supply scan
            supply.then(ClientCommandManager.literal("scan")
                    .executes(ctx -> {
                        List<BlockPos> chests = PrinterResourceManager.getSupplyChests();
                        if (chests.isEmpty()) {
                            ChatHelper.info("§cNo supply chests designated.");
                            ChatHelper.info("§7Use §f/printer supply add §7while standing at a chest.");
                            return 0;
                        }

                        ChestIndexer.IndexSummary summary = ChestIndexer.getSummary();
                        ChatHelper.info("§l§6Supply Index Summary");
                        ChatHelper.info("Chests: §e" + summary.totalChests()
                                + " §7(§a" + summary.indexedChests() + " indexed§7, §c"
                                + summary.unindexedChests() + " unscanned§7)");

                        if (summary.indexedChests() > 0) {
                            ChatHelper.info("Shulker boxes found: §d" + summary.totalShulkers());
                            ChatHelper.info("Item types indexed: §e" + summary.totalItemTypes());
                            ChatHelper.info("Total items available: §a" + summary.totalItems());

                            // Show top 10 items in supply
                            Map<String, Integer> combined = ChestIndexer.getCombinedInventory();
                            List<Map.Entry<String, Integer>> top = combined.entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .limit(10)
                                    .toList();

                            if (!top.isEmpty()) {
                                ChatHelper.info("§7Top items in supply:");
                                for (var entry : top) {
                                    String pretty = PrinterResourceManager.MaterialsReport.prettyName(entry.getKey());
                                    ChatHelper.info(" §7- §f" + pretty + "§7: §a" + entry.getValue());
                                }
                                if (combined.size() > 10) {
                                    ChatHelper.info(" §7... and " + (combined.size() - 10) + " more types");
                                }
                            }
                        }

                        if (summary.unindexedChests() > 0) {
                            ChatHelper.info("§7Open unscanned chests to index them, or they'll be");
                            ChatHelper.info("§7auto-indexed during restocking.");
                        }

                        return 1;
                    })
            );

            root.then(supply);

            dispatcher.register(root);
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static int loadSchematic(String filename) {
        SchematicPrinter printer = getPrinter();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;

        Path dir = SchematicPrinter.getSchematicsDir();
        Path file = dir.resolve(filename.endsWith(".litematic") ? filename : filename + ".litematic");

        if (!Files.exists(file)) {
            ChatHelper.info("§cFile not found: §7" + file.getFileName());
            ChatHelper.info("§7Use §f/printer list §7to see available schematics.");
            return 0;
        }

        try {
            BlockPos anchor = mc.player.getBlockPos();
            printer.loadSchematic(file, anchor);

            ChatHelper.info("§aLoaded §f" + printer.getSchematic().getName()
                    + " §7by " + printer.getSchematic().getAuthor());
            ChatHelper.info("Size: §e"
                    + printer.getSchematic().getSizeX() + "x"
                    + printer.getSchematic().getSizeY() + "x"
                    + printer.getSchematic().getSizeZ()
                    + " §7(" + printer.getSchematic().getTotalNonAir() + " blocks)");
            ChatHelper.info("Anchored at §e"
                    + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());

            if (printer.getSchematic().isFromFuture()) {
                ChatHelper.info("§e⚠ This schematic was created in a newer Minecraft version.");
            }
            if (printer.getSchematic().hasUnknownBlocks()) {
                var unknown = printer.getSchematic().getUnknownBlocks();
                int totalUnknown = unknown.values().stream().mapToInt(Integer::intValue).sum();
                ChatHelper.info("§e⚠ " + unknown.size() + " block type(s) not recognized ("
                        + totalUnknown + " blocks will be skipped):");
                unknown.entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(8)
                        .forEach(e -> ChatHelper.info("  §7- §c" + e.getKey() + " §7(×" + e.getValue() + ")"));
                if (unknown.size() > 8) {
                    ChatHelper.info("  §7... and " + (unknown.size() - 8) + " more");
                }
            }

            ChatHelper.info("§7Use §f/printer toggle §7to start printing.");

            return 1;
        } catch (IOException e) {
            ChatHelper.info("§cFailed to load: " + e.getMessage());
            return 0;
        }
    }

    private static SchematicPrinter getPrinter() {
        return LitematicaPrinterMod.getPrinter();
    }
}
