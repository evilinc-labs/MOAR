package dev.moar.gui;

import dev.moar.update.MoarUpdateService;
import dev.moar.update.MoarUpdateService.InstallResult;
import dev.moar.update.MoarUpdateService.ReleaseOffer;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/*?}*/

/** Startup prompt for a version-matched MOAR release. */
public final class MoarUpdateScreen extends Screen {

    private final Screen parent;
    private final MoarUpdateService service;
    private final ReleaseOffer offer;
    private final String currentVersion;
    private boolean installing;
    private InstallResult result;

    public MoarUpdateScreen(Screen parent, MoarUpdateService service, ReleaseOffer offer) {
        super(text("MOAR update"));
        this.parent = parent;
        this.service = service;
        this.offer = offer;
        this.currentVersion = FabricLoader.getInstance().getModContainer("moar").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
    }

    @Override
    protected void init() {
        /*? if >=26.1 {*//*
        clearWidgets();
        *//*?} else {*/
        clearChildren();
        /*?}*/
        int y = height / 2 + 28;
        if (result != null && result.installed()) {
            addButton(width / 2 - 100, y, 200, "Done", this::returnToParent);
        } else if (installing) {
            addButton(width / 2 - 100, y, 200, "Downloading...", () -> { });
        } else {
            addButton(width / 2 - 154, y, 100, result == null ? "Update" : "Retry", this::install);
            addButton(width / 2 - 50, y, 100, "Later", this::returnToParent);
            addButton(width / 2 + 54, y, 100, "Skip version", () -> {
                service.skipVersion(offer);
                returnToParent();
            });
        }
    }

    private void install() {
        if (installing) return;
        installing = true;
        result = null;
        init();
        service.installAsync(offer, outcome -> {
            /*? if >=26.1 {*//*
            Minecraft client = Minecraft.getInstance();
            *//*?} else {*/
            MinecraftClient client = MinecraftClient.getInstance();
            /*?}*/
            client.execute(() -> {
                result = outcome;
                installing = false;
                if (isCurrentScreen()) init();
            });
        });
    }

    private boolean isCurrentScreen() {
        /*? if >=26.2 {*//*
        return Minecraft.getInstance().gui.screen() == this;
        *//*?} else if >=26.1 {*//*
        return Minecraft.getInstance().screen == this;
        *//*?} else {*/
        return MinecraftClient.getInstance().currentScreen == this;
        /*?}*/
    }

    private void returnToParent() {
        /*? if >=26.2 {*//*
        Minecraft.getInstance().gui.setScreen(parent);
        *//*?} else if >=26.1 {*//*
        Minecraft.getInstance().setScreen(parent);
        *//*?} else {*/
        MinecraftClient.getInstance().setScreen(parent);
        /*?}*/
    }

    @Override
    /*? if >=26.1 {*//*
    public void onClose() {
    *//*?} else {*/
    public void close() {
    /*?}*/
        returnToParent();
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
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    *//*?} else {*/
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    /*?}*/
        context.fill(0, 0, width, height, 0xD010141A);
        line(context, "MOAR update available", height / 2 - 54, 0xFFFFFFFF);
        line(context, "MOAR " + currentVersion + "  →  " + offer.version()
                        + " for Minecraft " + offer.minecraftVersion(),
                height / 2 - 34, 0xFFB6D9FF);
        if (installing) {
            line(context, "Downloading and verifying the release...", height / 2 - 8, 0xFFE8E8E8);
        } else if (result == null) {
            line(context, "Install the matching build for your next launch?", height / 2 - 8, 0xFFE8E8E8);
            line(context, "Skip version keeps this build and hides this release.", height / 2 + 7, 0xFFAAAAAA);
        } else {
            line(context, result.message(), height / 2 - 8,
                    result.installed() ? 0xFFAAFFAA : 0xFFFFAA88);
            if (result.stagedJar() != null) {
                line(context, "Verified JAR saved in config/moar/updates for manual installation.",
                        height / 2 + 7, 0xFFE8E8E8);
            }
        }
        /*? if >=26.1 {*//*
        super.extractRenderState(context, mouseX, mouseY, delta);
        *//*?} else {*/
        super.render(context, mouseX, mouseY, delta);
        /*?}*/
    }

    /*? if >=26.1 {*//*
    private void line(GuiGraphicsExtractor context, String value, int y, int color) {
        context.centeredText(font, value, width / 2, y, color);
    }

    private static Component text(String value) {
        return Component.literal(value);
    }

    private void addButton(int x, int y, int width, String label, Runnable action) {
        addRenderableWidget(Button.builder(text(label), button -> action.run())
                .bounds(x, y, width, 20).build());
    }
    *//*?} else {*/
    private void line(DrawContext context, String value, int y, int color) {
        context.drawCenteredTextWithShadow(textRenderer, value, width / 2, y, color);
    }

    private static Text text(String value) {
        return Text.literal(value);
    }

    private void addButton(int x, int y, int width, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(text(label), button -> action.run())
                .dimensions(x, y, width, 20).build());
    }
    /*?}*/
}
