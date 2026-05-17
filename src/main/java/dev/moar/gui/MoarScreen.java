package dev.moar.gui;

import dev.moar.MoarMod;
import dev.moar.api.ApiServer;
import dev.moar.api.MoarProperties;
import dev.moar.printer.SchematicPrinter;
import dev.moar.spawnproof.SpawnProofer;
import dev.moar.stash.StashDatabase;
import dev.moar.stash.StashManager;
import dev.moar.stash.StashRetriever;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class MoarScreen extends Screen {

    private static final int MAX_NAME_LENGTH = 48;
    private static final int MAX_ITEM_COUNT = 1_000_000;
    private static final int MAX_BULK_REQUEST_LENGTH = 2048;
    private static final int MAX_API_KEY_LENGTH = 256;
    private static final int MAX_WEBHOOK_LENGTH = 2048;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1," + MAX_NAME_LENGTH + "}");
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern BIND_ADDRESS = Pattern.compile("[A-Za-z0-9_.:-]{1,255}");

    private enum Section {
        KITS("Kits"),
        INDEX("Index"),
        REGIONS("Regions"),
        RETRIEVE("Retrieve"),
        PRINTER("Printer"),
        SPAWNPROOF("Spawnproof"),
        API("API");

        private final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private Section section = Section.KITS;
    private String notice = "";

    /*? if >=26.1 {*//*
    private EditBox kitNameField;
    private EditBox kitItemField;
    private EditBox kitCountField;
    private EditBox regionNameField;
    private EditBox retrieveItemField;
    private EditBox retrieveCountField;
    private EditBox bulkRetrieveField;
    private EditBox lightSourceField;
    private EditBox apiBindField;
    private EditBox apiPortField;
    private EditBox apiKeyField;
    private EditBox webhookField;
    *//*?} else {*/
    private TextFieldWidget kitNameField;
    private TextFieldWidget kitItemField;
    private TextFieldWidget kitCountField;
    private TextFieldWidget regionNameField;
    private TextFieldWidget retrieveItemField;
    private TextFieldWidget retrieveCountField;
    private TextFieldWidget bulkRetrieveField;
    private TextFieldWidget lightSourceField;
    private TextFieldWidget apiBindField;
    private TextFieldWidget apiPortField;
    private TextFieldWidget apiKeyField;
    private TextFieldWidget webhookField;
    /*?}*/

    public MoarScreen() {
        super(text("MOAR"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        /*? if >=26.1 {*//*
        clearWidgets();
        *//*?} else {*/
        clearChildren();
        /*?}*/
        int left = 12;
        int y = 28;
        for (Section candidate : Section.values()) {
            Section target = candidate;
            addButton(left, y, 100, 20,
                    (candidate == section ? "> " : "") + candidate.label,
                    () -> {
                        section = target;
                        notice = "";
                        rebuild();
                    });
            y += 24;
        }

        int x = contentX();
        y = 48;
        switch (section) {
            case KITS -> buildKits(x, y);
            case INDEX -> buildIndex(x, y);
            case REGIONS -> buildRegions(x, y);
            case RETRIEVE -> buildRetrieve(x, y);
            case PRINTER -> buildPrinter(x, y);
            case SPAWNPROOF -> buildSpawnproof(x, y);
            case API -> buildApi(x, y);
        }
    }

    private void buildKits(int x, int y) {
        kitNameField = addTextField(x, y, 156, "kit name", "");
        kitItemField = addTextField(x + 168, y, 156, "item id", "minecraft:stone");
        kitCountField = addTextField(x + 336, y, 60, "count", "64");
        y += 30;

        addButton(x, y, 96, 20, "Create", this::createKit);
        addButton(x + 104, y, 96, 20, "Snapshot", this::snapshotKit);
        addButton(x + 208, y, 96, 20, "Retrieve", this::retrieveKit);
        addButton(x + 312, y, 84, 20, "Delete", this::deleteKit);
        y += 26;
        addButton(x, y, 96, 20, "Add Item", this::addKitItem);
        addButton(x + 104, y, 96, 20, "Remove Item", this::removeKitItem);
    }

    private void buildIndex(int x, int y) {
        addButton(x, y, 116, 20, "Set Pos 1 Here", () -> setStashCorner(true));
        addButton(x + 124, y, 116, 20, "Set Pos 2 Here", () -> setStashCorner(false));
        addButton(x + 248, y, 80, 20, "Scan", () -> {
            if (MoarMod.getStashManager().start()) notice("Stash scan started.");
        });
        addButton(x + 336, y, 80, 20, "Stop", () -> {
            MoarMod.getStashManager().stop();
            MoarMod.getStashManager().getRetriever().stop();
            notice("Stash activity stopped.");
        });
        y += 28;
        addButton(x, y, 116, 20, "Organize", () -> {
            MoarMod.getStashManager().getOrganizer().start();
            notice("Organizer started.");
        });
        addButton(x + 124, y, 116, 20, "Stop Organize", () -> {
            MoarMod.getStashManager().getOrganizer().stop();
            notice("Organizer stopped.");
        });
        addButton(x + 248, y, 80, 20, "Export", () -> {
            Path path = MoarMod.getStashManager().exportCsv();
            notice(path == null ? "Export failed." : "Exported " + path.getFileName());
        });
        addButton(x + 336, y, 80, 20, "Clear", () -> {
            MoarMod.getStashManager().clearIndex();
            notice("Stash index cleared.");
        });
    }

    private void buildRegions(int x, int y) {
        regionNameField = addTextField(x, y, 180, "region name", "stash");
        y += 30;
        addButton(x, y, 112, 20, "Save Profile", this::saveRegion);
        addButton(x + 120, y, 112, 20, "Load Profile", this::loadRegion);
        addButton(x + 240, y, 112, 20, "Delete Profile", this::deleteRegion);
        y += 26;
        addButton(x, y, 112, 20, "Set Pos 1 Here", () -> setStashCorner(true));
        addButton(x + 120, y, 112, 20, "Set Pos 2 Here", () -> setStashCorner(false));
    }

    private void buildRetrieve(int x, int y) {
        retrieveItemField = addTextField(x, y, 220, "item id", "minecraft:stone");
        retrieveCountField = addTextField(x + 232, y, 64, "count", "64");
        addButton(x + 308, y, 108, 20, "Retrieve Item", this::retrieveItem);
        y += 32;
        bulkRetrieveField = addTextField(x, y, 296, "item count, item count", "minecraft:stone 64, minecraft:dirt 64");
        addButton(x + 308, y, 108, 20, "Retrieve List", this::retrieveBulk);
        y += 32;
        addButton(x, y, 124, 20, "Retrieve Kit", this::retrieveKit);
        addButton(x + 132, y, 124, 20, "Stop Retrieval", () -> {
            MoarMod.getStashManager().getRetriever().stop();
            notice("Retrieval stopped.");
        });
    }

    private void buildPrinter(int x, int y) {
        SchematicPrinter printer = MoarMod.getPrinter();
        addButton(x, y, 88, 20, printer.isEnabled() ? "Disable" : "Enable", () -> {
            printer.setEnabled(!printer.isEnabled());
            rebuild();
        });
        addButton(x + 96, y, 80, 20, "BPS -", () -> {
            printer.setBps(printer.getBps() - 1);
            rebuild();
        });
        addButton(x + 184, y, 80, 20, "BPS +", () -> {
            printer.setBps(printer.getBps() + 1);
            rebuild();
        });
        addButton(x + 272, y, 80, 20, "Range -", () -> {
            printer.setRange(printer.getRange() - 0.1);
            rebuild();
        });
        addButton(x + 360, y, 80, 20, "Range +", () -> {
            printer.setRange(printer.getRange() + 0.1);
            rebuild();
        });
        y += 28;
        addButton(x, y, 112, 20, "Set Anchor Here", this::setPrinterAnchor);
        addButton(x + 120, y, 104, 20, "Swap " + onOff(printer.isSwapItems()), () -> {
            printer.setSwapItems(!printer.isSwapItems());
            rebuild();
        });
        addButton(x + 232, y, 112, 20, "Air " + onOff(printer.isPrintInAir()), () -> {
            printer.setPrintInAir(!printer.isPrintInAir());
            rebuild();
        });
        addButton(x + 352, y, 112, 20, "Auto " + onOff(printer.isAutoBuild()), () -> {
            printer.setAutoBuild(!printer.isAutoBuild());
            rebuild();
        });
        y += 28;
        addButton(x, y, 136, 20, "Sort: " + printer.getSortMode().name(), () -> {
            SchematicPrinter.SortMode[] modes = SchematicPrinter.SortMode.values();
            printer.setSortMode(modes[(printer.getSortMode().ordinal() + 1) % modes.length]);
            rebuild();
        });
        addButton(x + 144, y, 136, 20, "Status " + onOff(printer.isStatusMessages()), () -> {
            printer.setStatusMessages(!printer.isStatusMessages());
            rebuild();
        });
    }

    private void buildSpawnproof(int x, int y) {
        SpawnProofer proofer = MoarMod.getSpawnProofer();
        lightSourceField = addTextField(x, y, 180, "light source", proofer.getLightSourceName());
        addButton(x + 192, y, 112, 20, "Set Light", this::setLightSource);
        addButton(x + 312, y, 112, 20, "Embed " + onOff(proofer.isEmbedInGround()), () -> {
            if (!proofer.isEmbedInGround() && !proofer.isFullBlockLightSource()) {
                notice("Choose a full-block light source before embedding.");
                return;
            }
            proofer.setEmbedInGround(!proofer.isEmbedInGround());
            rebuild();
        });
        y += 30;
        addButton(x, y, 112, 20, "Set Pos 1 Here", () -> setSpawnproofCorner(true));
        addButton(x + 120, y, 112, 20, "Set Pos 2 Here", () -> setSpawnproofCorner(false));
        addButton(x + 240, y, 80, 20, "Start", () -> {
            if (proofer.start()) notice("Spawnproofing started.");
        });
        addButton(x + 328, y, 80, 20, "Stop", () -> {
            proofer.stop();
            notice("Spawnproofing stopped.");
        });
        y += 28;
        addButton(x, y, 96, 20, "Pause", () -> {
            proofer.pause();
            notice("Spawnproofing paused.");
        });
        addButton(x + 104, y, 96, 20, "Resume", () -> {
            proofer.resume();
            notice("Spawnproofing resumed.");
        });
    }

    private void buildApi(int x, int y) {
        MoarProperties props = MoarMod.getProperties();
        if (props == null) return;
        apiBindField = addTextField(x, y, 140, "bind address", props.getApiBindAddress());
        apiPortField = addTextField(x + 152, y, 72, "port", String.valueOf(props.getApiPort()));
        addButton(x + 236, y, 96, 20, "API " + onOff(props.isApiEnabled()), () -> {
            props.setApiEnabled(!props.isApiEnabled());
            syncApiServer();
            rebuild();
        });
        addButton(x + 340, y, 76, 20, "Apply", this::applyApiConfig);
        y += 30;
        apiKeyField = addTextField(x, y, 416, "api key", props.getApiKey());
        y += 30;
        webhookField = addTextField(x, y, 416, "webhook url", props.getWebhookUrl());
        y += 30;
        addButton(x, y, 116, 20, "Restart API", () -> {
            ApiServer server = MoarMod.getApiServer();
            if (server != null) {
                server.close();
                server.start();
                notice(server.isRunning() ? "API server restarted." : "API server is stopped.");
            }
        });
    }

    /*? if >=26.1 {*//*
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    *//*?} else {*/
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    /*?}*/
        /*? if >=26.1 {*//*
        extractBackground(context, mouseX, mouseY, delta);
        *//*?} else {*/
        renderBackground(context, mouseX, mouseY, delta);
        /*?}*/
        drawTitle(context);
        drawBody(context);
        /*? if >=26.1 {*//*
        super.extractRenderState(context, mouseX, mouseY, delta);
        *//*?} else {*/
        super.render(context, mouseX, mouseY, delta);
        /*?}*/
        if (!notice.isEmpty()) {
            drawText(context, notice, contentX(), height - 24, 0xFFFFDD55);
        }
    }

    /*? if >=26.1 {*//*
    private void drawTitle(GuiGraphicsExtractor context) {
    *//*?} else {*/
    private void drawTitle(DrawContext context) {
    /*?}*/
        fill(context, 0, 0, width, 22, 0xAA101820);
        drawCenteredText(context, "MOAR", width / 2, 7, 0xFFFFFFFF);
        fill(context, 120, 22, 121, height, 0x66334455);
    }

    /*? if >=26.1 {*//*
    private void drawBody(GuiGraphicsExtractor context) {
    *//*?} else {*/
    private void drawBody(DrawContext context) {
    /*?}*/
        int x = contentX();
        int y = 28;
        drawText(context, section.label, x, y, 0xFFFFFFFF);
        y += 16;
        switch (section) {
            case KITS -> drawKitInfo(context, x, y + 78);
            case INDEX -> drawIndexInfo(context, x, y + 64);
            case REGIONS -> drawRegionInfo(context, x, y + 64);
            case RETRIEVE -> drawRetrieveInfo(context, x, y + 96);
            case PRINTER -> drawPrinterInfo(context, x, y + 92);
            case SPAWNPROOF -> drawSpawnproofInfo(context, x, y + 92);
            case API -> drawApiInfo(context, x, y + 112);
        }
    }

    /*? if >=26.1 {*//*
    private void drawKitInfo(GuiGraphicsExtractor context, int x, int y) {
    *//*?} else {*/
    private void drawKitInfo(DrawContext context, int x, int y) {
    /*?}*/
        StashDatabase db = openDatabase();
        if (db == null) {
            drawText(context, "Database unavailable.", x, y, 0xFFFF7777);
            return;
        }
        List<String> kits = db.listKits();
        drawText(context, "Saved kits: " + kits.size(), x, y, 0xFFBBBBBB);
        int row = 0;
        for (String kit : kits) {
            if (row >= 8) {
                drawText(context, "... " + (kits.size() - row) + " more", x, y + 14 + row * 12, 0xFF888888);
                break;
            }
            drawText(context, kit + " (" + db.countKitSlots(kit) + "/" + StashDatabase.KIT_MAX_SLOTS + " slots)",
                    x, y + 14 + row * 12, 0xFFE8E8E8);
            row++;
        }
    }

    /*? if >=26.1 {*//*
    private void drawIndexInfo(GuiGraphicsExtractor context, int x, int y) {
    *//*?} else {*/
    private void drawIndexInfo(DrawContext context, int x, int y) {
    /*?}*/
        StashManager mgr = MoarMod.getStashManager();
        drawText(context, "Status: " + mgr.getStatus(), x, y, 0xFFE8E8E8);
        drawText(context, "Region: " + mgr.getRegionInfo(), x, y + 14, 0xFFBBBBBB);
        drawText(context, "Index: " + mgr.getDetailedSummary(), x, y + 28, 0xFFBBBBBB);
        drawText(context, "Remaining queue: " + mgr.getRemainingCount()
                + " | skipped: " + mgr.getTotalSkipped(), x, y + 42, 0xFF999999);
    }

    /*? if >=26.1 {*//*
    private void drawRegionInfo(GuiGraphicsExtractor context, int x, int y) {
    *//*?} else {*/
    private void drawRegionInfo(DrawContext context, int x, int y) {
    /*?}*/
        StashDatabase db = openDatabase();
        if (db == null) return;
        Map<String, BlockPos[]> regions = db.loadAllRegions();
        drawText(context, "Profiles: " + regions.size(), x, y, 0xFFBBBBBB);
        int row = 0;
        for (Map.Entry<String, BlockPos[]> entry : regions.entrySet()) {
            if (row >= 8) break;
            BlockPos[] corners = entry.getValue();
            drawText(context, entry.getKey() + "  " + pos(corners[0]) + " -> " + pos(corners[1]),
                    x, y + 14 + row * 12, 0xFFE8E8E8);
            row++;
        }
    }

    /*? if >=26.1 {*//*
    private void drawRetrieveInfo(GuiGraphicsExtractor context, int x, int y) {
    *//*?} else {*/
    private void drawRetrieveInfo(DrawContext context, int x, int y) {
    /*?}*/
        StashRetriever retriever = MoarMod.getStashManager().getRetriever();
        drawText(context, "Retriever: " + retriever.getState(), x, y, 0xFFE8E8E8);
        drawText(context, "List syntax: minecraft:stone 64, minecraft:dirt 128", x, y + 14, 0xFF999999);
        drawText(context, "Use kit name above for Retrieve Kit.", x, y + 28, 0xFF999999);
    }

    /*? if >=26.1 {*//*
    private void drawPrinterInfo(GuiGraphicsExtractor context, int x, int y) {
    *//*?} else {*/
    private void drawPrinterInfo(DrawContext context, int x, int y) {
    /*?}*/
        SchematicPrinter printer = MoarMod.getPrinter();
        drawText(context, "Loaded: " + (printer.isLoaded() ? printer.getSchematic().getName() : "none"),
                x, y, 0xFFE8E8E8);
        drawText(context, "State: " + (printer.isEnabled() ? "printing" : "idle")
                + " | autobuild: " + printer.getAutoState(), x, y + 14, 0xFFBBBBBB);
        drawText(context, "BPS: " + printer.getBps() + " | range: " + String.format(Locale.ROOT, "%.1f", printer.getRange())
                + " | sort: " + printer.getSortMode(), x, y + 28, 0xFFBBBBBB);
        drawText(context, "Anchor: " + (printer.getAnchor() == null ? "unset" : pos(printer.getAnchor())),
                x, y + 42, 0xFFBBBBBB);
    }

    /*? if >=26.1 {*//*
    private void drawSpawnproofInfo(GuiGraphicsExtractor context, int x, int y) {
    *//*?} else {*/
    private void drawSpawnproofInfo(DrawContext context, int x, int y) {
    /*?}*/
        SpawnProofer proofer = MoarMod.getSpawnProofer();
        drawText(context, "Status: " + proofer.getStatus(), x, y, 0xFFE8E8E8);
        drawText(context, "Region: " + cornerSummary(proofer.getCorner1(), proofer.getCorner2()),
                x, y + 14, 0xFFBBBBBB);
        drawText(context, "Light: " + proofer.getLightSourceName()
                + " | embed: " + onOff(proofer.isEmbedInGround()), x, y + 28, 0xFFBBBBBB);
        drawText(context, "Dark spots: " + proofer.getDarkSpotCount()
                + " | placed: " + proofer.getTotalPlaced(), x, y + 42, 0xFFBBBBBB);
    }

    /*? if >=26.1 {*//*
    private void drawApiInfo(GuiGraphicsExtractor context, int x, int y) {
    *//*?} else {*/
    private void drawApiInfo(DrawContext context, int x, int y) {
    /*?}*/
        MoarProperties props = MoarMod.getProperties();
        ApiServer server = MoarMod.getApiServer();
        if (props == null) return;
        drawText(context, "Configured: " + onOff(props.isApiEnabled())
                + " | server: " + (server != null && server.isRunning() ? "running" : "stopped"),
                x, y, 0xFFE8E8E8);
        drawText(context, "URL: http://" + props.getApiBindAddress() + ":" + props.getApiPort()
                + "/api/v1/status", x, y + 14, 0xFFBBBBBB);
    }

    private void createKit() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(kitNameField), "kit name");
        if (db == null || name.isEmpty()) {
            return;
        }
        notice(db.createKit(name) ? "Created kit " + name + "." : "Kit already exists or could not be created.");
    }

    private void snapshotKit() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(kitNameField), "kit name");
        if (db == null || name.isEmpty()) {
            return;
        }
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) {
            notice("Join a world before snapshotting inventory.");
            return;
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty()) items.merge(ItemIdentifier.getItemId(stack), stack.getCount(), Integer::sum);
        }
        if (items.isEmpty()) {
            notice("Inventory is empty.");
            return;
        }
        if (!db.kitExists(name)) db.createKit(name);
        notice(db.snapshotKit(name, items)
                ? "Snapshot saved to " + name + " (" + db.countKitSlots(name) + " slots)."
                : "Snapshot failed.");
    }

    private void retrieveKit() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(kitNameField), "kit name");
        if (db == null || name.isEmpty()) {
            return;
        }
        Map<String, Integer> items = db.loadKitItems(name);
        if (items.isEmpty()) {
            notice("Kit is empty or missing.");
            return;
        }
        StashRetriever retriever = MoarMod.getStashManager().getRetriever();
        notice(retriever.startKit(name, items) ? "Retrieving kit " + name + "." : "Could not start kit retrieval.");
    }

    private void deleteKit() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(kitNameField), "kit name");
        if (db == null || name.isEmpty()) {
            return;
        }
        notice(db.deleteKit(name) ? "Deleted kit " + name + "." : "Kit not found.");
    }

    private void addKitItem() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(kitNameField), "kit name");
        String item = readItemId(textValue(kitItemField));
        int count = parseBoundedInt(textValue(kitCountField), 1, MAX_ITEM_COUNT, 1, "count");
        if (count < 0) return;
        if (db == null || name.isEmpty() || item.isEmpty()) {
            return;
        }
        if (!db.kitExists(name)) db.createKit(name);
        notice(db.addKitItem(name, item, count) ? "Added " + item + " x" + count + "." : "Kit is full.");
    }

    private void removeKitItem() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(kitNameField), "kit name");
        String item = readItemId(textValue(kitItemField));
        if (db == null || name.isEmpty() || item.isEmpty()) {
            return;
        }
        notice(db.removeKitItem(name, item) ? "Removed " + item + "." : "Item was not in the kit.");
    }

    private void saveRegion() {
        StashDatabase db = openDatabase();
        StashManager mgr = MoarMod.getStashManager();
        String name = readName(textValue(regionNameField), "region name");
        if (db == null || name.isEmpty() || mgr.getCorner1() == null || mgr.getCorner2() == null) {
            if (!name.isEmpty()) notice("Set both stash corners before saving.");
            return;
        }
        db.saveRegion(name, mgr.getCorner1(), mgr.getCorner2());
        notice("Saved region " + name + ".");
    }

    private void loadRegion() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(regionNameField), "region name");
        if (db == null || name.isEmpty()) {
            return;
        }
        BlockPos[] corners = db.loadRegion(name);
        if (corners == null) {
            notice("Region not found.");
            return;
        }
        MoarMod.getStashManager().setCorner1(corners[0]);
        MoarMod.getStashManager().setCorner2(corners[1]);
        notice("Loaded region " + name + ".");
    }

    private void deleteRegion() {
        StashDatabase db = openDatabase();
        String name = readName(textValue(regionNameField), "region name");
        if (db == null || name.isEmpty()) {
            return;
        }
        notice(db.deleteRegion(name) ? "Deleted region " + name + "." : "Region not found.");
    }

    private void retrieveItem() {
        String item = readItemId(textValue(retrieveItemField));
        int count = parseBoundedInt(textValue(retrieveCountField), 1, MAX_ITEM_COUNT, 64, "count");
        if (count < 0) return;
        if (item.isEmpty()) {
            return;
        }
        notice(MoarMod.getStashManager().getRetriever().start(item, count)
                ? "Retrieving " + item + " x" + count + "."
                : "Could not start retrieval.");
    }

    private void retrieveBulk() {
        Map<String, Integer> items = parseBulkItems(textValue(bulkRetrieveField));
        if (items.isEmpty()) {
            notice("Enter one or more item count pairs.");
            return;
        }
        notice(MoarMod.getStashManager().getRetriever().startKit("GUI request", items)
                ? "Retrieving " + items.size() + " requested item types."
                : "Could not start retrieval list.");
    }

    private void setStashCorner(boolean first) {
        BlockPos pos = playerPos();
        if (pos == null) {
            notice("Join a world first.");
            return;
        }
        if (first) MoarMod.getStashManager().setCorner1(pos);
        else MoarMod.getStashManager().setCorner2(pos);
        notice("Set stash pos " + (first ? "1" : "2") + " to " + pos(pos) + ".");
    }

    private void setPrinterAnchor() {
        BlockPos pos = playerPos();
        if (pos == null) {
            notice("Join a world first.");
            return;
        }
        if (!MoarMod.getPrinter().isLoaded()) {
            notice("Load a schematic before setting the printer anchor.");
            return;
        }
        MoarMod.getPrinter().overrideAnchor(pos);
        notice("Printer anchor set to " + pos(pos) + ".");
    }

    private void setSpawnproofCorner(boolean first) {
        BlockPos pos = playerPos();
        if (pos == null) {
            notice("Join a world first.");
            return;
        }
        if (first) MoarMod.getSpawnProofer().setCorner1(pos);
        else MoarMod.getSpawnProofer().setCorner2(pos);
        notice("Set spawnproof pos " + (first ? "1" : "2") + " to " + pos(pos) + ".");
    }

    private void setLightSource() {
        String id = readItemId(textValue(lightSourceField));
        if (id.isEmpty()) return;
        if (MoarMod.getSpawnProofer().setLightSource(id)) {
            notice("Light source set to " + id + ".");
            rebuild();
        } else {
            notice("Unknown or non-luminous block.");
        }
    }

    private void applyApiConfig() {
        MoarProperties props = MoarMod.getProperties();
        if (props == null) return;
        String bindAddress = sanitizeBindAddress(textValue(apiBindField));
        if (bindAddress.isEmpty()) return;
        String apiKey = sanitizeConfigValue(textValue(apiKeyField), MAX_API_KEY_LENGTH, "API key");
        if (apiKey == null) return;
        String webhook = sanitizeWebhookUrl(textValue(webhookField));
        if (webhook == null) return;
        int port = parseBoundedInt(textValue(apiPortField), 1, 65535, props.getApiPort(), "API port");
        if (port < 0) return;

        props.setApiBindAddress(bindAddress);
        props.setApiPort(port);
        props.setApiKey(apiKey);
        props.setWebhookUrl(webhook);
        syncApiServer();
        notice("API config applied.");
    }

    private void syncApiServer() {
        ApiServer server = MoarMod.getApiServer();
        MoarProperties props = MoarMod.getProperties();
        if (server == null || props == null) return;
        server.close();
        if (props.isApiEnabled()) server.start();
    }

    private StashDatabase openDatabase() {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) return null;
        if (!db.isOpen()) db.open();
        return db.isOpen() ? db : null;
    }

    private Map<String, Integer> parseBulkItems(String value) {
        Map<String, Integer> result = new LinkedHashMap<>();
        String request = value.trim();
        if (request.length() > MAX_BULK_REQUEST_LENGTH) {
            notice("Retrieval list is too long.");
            return result;
        }
        for (String raw : request.split("[,;]")) {
            String part = raw.trim();
            if (part.isEmpty()) continue;
            String[] pieces = part.split("\\s+");
            String item = normalizeItemId(pieces[0]);
            if (!isValidItemId(item)) {
                notice("Invalid item id: " + pieces[0]);
                result.clear();
                return result;
            }
            int count = 64;
            if (pieces.length >= 2) {
                count = parseBoundedInt(pieces[1].replaceFirst("^[xX]", ""),
                        1, MAX_ITEM_COUNT, 64, "count");
                if (count < 0) {
                    result.clear();
                    return result;
                }
            }
            if (!item.isEmpty()) result.merge(item, count, Integer::sum);
        }
        return result;
    }

    private String normalizeItemId(String value) {
        String item = value.trim().toLowerCase(Locale.ROOT);
        if (item.isEmpty()) return "";
        return item.contains(":") ? item : "minecraft:" + item;
    }

    private boolean isValidItemId(String item) {
        return ITEM_ID.matcher(item).matches();
    }

    private String readItemId(String value) {
        String item = normalizeItemId(value);
        if (item.isEmpty()) {
            notice("Enter an item id.");
            return "";
        }
        if (!isValidItemId(item)) {
            notice("Use a valid namespaced id like minecraft:stone.");
            return "";
        }
        return item;
    }

    private String readName(String value, String label) {
        String name = value.trim();
        if (!SAFE_NAME.matcher(name).matches()) {
            notice("Use 1-" + MAX_NAME_LENGTH
                    + " letters, numbers, dots, underscores, or hyphens for " + label + ".");
            return "";
        }
        return name;
    }

    private String sanitizeBindAddress(String value) {
        String bindAddress = value.trim();
        if (!BIND_ADDRESS.matcher(bindAddress).matches()) {
            notice("Use a valid bind address such as 127.0.0.1, 0.0.0.0, or localhost.");
            return "";
        }
        return bindAddress;
    }

    private String sanitizeConfigValue(String value, int maxLength, String label) {
        String sanitized = value.trim();
        if (sanitized.indexOf('\n') >= 0 || sanitized.indexOf('\r') >= 0) {
            notice(label + " cannot contain newlines.");
            return null;
        }
        if (sanitized.length() > maxLength) {
            notice(label + " is too long.");
            return null;
        }
        return sanitized;
    }

    private String sanitizeWebhookUrl(String value) {
        String webhook = sanitizeConfigValue(value, MAX_WEBHOOK_LENGTH, "Webhook URL");
        if (webhook == null || webhook.isEmpty()) return webhook;
        if (!(webhook.startsWith("http://") || webhook.startsWith("https://"))) {
            notice("Webhook URL must start with http:// or https://.");
            return null;
        }
        return webhook;
    }

    private int parseBoundedInt(String value, int min, int max, int fallback, String label) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= min && parsed <= max) return parsed;
        } catch (NumberFormatException ignored) {
        }
        notice("Use a " + label + " from " + min + " to " + max + ".");
        return -1;
    }

    private BlockPos playerPos() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.blockPosition();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        return mc.player.getBlockPos();
        /*?}*/
    }

    private String cornerSummary(BlockPos a, BlockPos b) {
        if (a == null || b == null) return "unset";
        return pos(a) + " -> " + pos(b);
    }

    private String pos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private void notice(String value) {
        notice = value;
        ChatHelper.labelled("GUI", value);
    }

    private int contentX() {
        return 136;
    }

    @Override
    /*? if >=26.1 {*//*
    public boolean isPauseScreen() {
    *//*?} else {*/
    public boolean shouldPause() {
    /*?}*/
        return false;
    }

    /*? if >=26.1 {*//*
    private EditBox addTextField(int x, int y, int width, String hint, String value) {
        EditBox field = new EditBox(font, x, y, width, 20, Component.literal(hint));
        field.setMaxLength(512);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void addButton(int x, int y, int width, int height, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, width, height)
                .build());
    }

    private String textValue(EditBox field) {
        return field == null ? "" : field.getValue();
    }

    private static Component text(String value) {
        return Component.literal(value);
    }

    private void drawText(GuiGraphicsExtractor context, String text, int x, int y, int color) {
        context.text(font, text, x, y, color);
    }

    private void drawCenteredText(GuiGraphicsExtractor context, String text, int x, int y, int color) {
        context.centeredText(font, text, x, y, color);
    }

    private void fill(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }
    *//*?} else {*/
    private TextFieldWidget addTextField(int x, int y, int width, String hint, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.literal(hint));
        field.setMaxLength(512);
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void addButton(int x, int y, int width, int height, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> action.run())
                .dimensions(x, y, width, height)
                .build());
    }

    private String textValue(TextFieldWidget field) {
        return field == null ? "" : field.getText();
    }

    private static Text text(String value) {
        return Text.literal(value);
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        context.drawTextWithShadow(textRenderer, text, x, y, color);
    }

    private void drawCenteredText(DrawContext context, String text, int x, int y, int color) {
        context.drawCenteredTextWithShadow(textRenderer, text, x, y, color);
    }

    private void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }
    /*?}*/
}
