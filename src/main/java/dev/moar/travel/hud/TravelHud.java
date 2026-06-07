package dev.moar.travel.hud;

import dev.moar.travel.TravelManager;
import dev.moar.travel.TravelPhase;
import dev.moar.travel.highway.IntegrityReport;
import dev.moar.travel.plan.HighwayCandidate;
import dev.moar.travel.telemetry.TravelTelemetry;

/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
*//*?} else {*/
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
/*?}*/

// Draw a compact travel-status overlay.
public final class TravelHud {

    private TravelHud() {}

    public static void register() {
        /*? if >=26.1 {*//*
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moar", "travel_hud"),
                (GuiGraphicsExtractor ctx, DeltaTracker delta) -> renderState(ctx));
        *//*?} else {*/
        HudRenderCallback.EVENT.register((DrawContext ctx, RenderTickCounter tc) -> render(ctx));
        /*?}*/
    }

    // ──────────────────────────────────────────────────────────────
    // 26.1+  (GuiGraphicsExtractor / HudElement API)
    // ──────────────────────────────────────────────────────────────
    /*? if >=26.1 {*//*
    private static void renderState(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TravelTelemetry t = TravelManager.get().snapshot();
        if (t.phase() == TravelPhase.IDLE) return;

        Font font = mc.font;
        int x = 4, y = 4;
        final int LINE_H = 10;

        ctx.text(font, "[Travel] " + t.phase(), x, y, phaseColor(t.phase()));
        y += LINE_H;

        HighwayCandidate hw = t.selectedHighway();
        if (hw != null) {
            String conf = String.format("%.0f%%", hw.confidence * 100);
            String rails = (hw.hasLeftRail || hw.hasRightRail)
                    ? " rails=" + (hw.hasLeftRail ? "L" : "") + (hw.hasRightRail ? "R" : "") : "";
            ctx.text(font, "HW: " + hw.axis.name()
                    + " conf=" + conf + (hw.width > 0 ? " w=" + hw.width : "") + rails,
                    x, y, 0x55FF55);
            y += LINE_H;
        } else if (t.phase() == TravelPhase.PLANNING) {
            ctx.text(font, "HW: scanning...", x, y, 0xFFFF55);
            y += LINE_H;
        }
        if (t.currentTarget() != null) {
            ctx.text(font, "-> " + t.currentTarget().toShortString(), x, y, 0xAAAAAA);
            y += LINE_H;
        }
        IntegrityReport ir = t.integrityReport();
        if (ir != null && ir.status() != IntegrityReport.Status.INSUFFICIENT_DATA) {
            ctx.text(font, "Integrity: " + ir.status().name()
                    + String.format(" (%.0f%%)", ir.confidence() * 100), x, y, irColor(ir.status()));
            y += LINE_H;
        }
        if (t.destination() != null) {
            ctx.text(font, "Dest: " + t.destination().toShortString(), x, y, 0x888888);
        }
    }
    *//*?} else {*/

    // ──────────────────────────────────────────────────────────────
    // <26.1  (DrawContext / HudRenderCallback API)
    // ──────────────────────────────────────────────────────────────
    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        TravelTelemetry t = TravelManager.get().snapshot();
        if (t.phase() == TravelPhase.IDLE) return;

        var font = mc.textRenderer;
        int x = 4, y = 4;
        final int LINE_H = 10;

        ctx.drawTextWithShadow(font, "[Travel] " + t.phase(), x, y, phaseColor(t.phase()));
        y += LINE_H;

        HighwayCandidate hw = t.selectedHighway();
        if (hw != null) {
            String conf = String.format("%.0f%%", hw.confidence * 100);
            String rails = (hw.hasLeftRail || hw.hasRightRail)
                    ? " rails=" + (hw.hasLeftRail ? "L" : "") + (hw.hasRightRail ? "R" : "") : "";
            ctx.drawTextWithShadow(font, "HW: " + hw.axis.name()
                    + " conf=" + conf + (hw.width > 0 ? " w=" + hw.width : "") + rails,
                    x, y, 0x55FF55);
            y += LINE_H;
        } else if (t.phase() == TravelPhase.PLANNING) {
            ctx.drawTextWithShadow(font, "HW: scanning...", x, y, 0xFFFF55);
            y += LINE_H;
        }
        if (t.currentTarget() != null) {
            ctx.drawTextWithShadow(font, "-> " + t.currentTarget().toShortString(), x, y, 0xAAAAAA);
            y += LINE_H;
        }
        IntegrityReport ir = t.integrityReport();
        if (ir != null && ir.status() != IntegrityReport.Status.INSUFFICIENT_DATA) {
            ctx.drawTextWithShadow(font, "Integrity: " + ir.status().name()
                    + String.format(" (%.0f%%)", ir.confidence() * 100), x, y, irColor(ir.status()));
            y += LINE_H;
        }
        if (t.destination() != null) {
            ctx.drawTextWithShadow(font, "Dest: " + t.destination().toShortString(), x, y, 0x888888);
        }
    }
    /*?}*/

    // ──────────────────────────────────────────────────────────────
    // Color helpers (shared)
    // ──────────────────────────────────────────────────────────────
    private static int phaseColor(TravelPhase phase) {
        return switch (phase) {
            case PLANNING                -> 0xFFFF55;
            case APPROACH_ONRAMP         -> 0x55FFFF;
            case BOUNCING                -> 0x00FF00;
            case LAUNCH, ELYTRA_CRUISE,
                 ELYTRA_FALLBACK         -> 0xAA00FF;
            case MINING_TO_FREENETHER,
                 OFFRAMP_HANDOFF         -> 0xFF9900;
            case VERIFYING_DETOUR,
                 DETOURING               -> 0xFF5500;
            case ARRIVED                 -> 0x55FF55;
            case ABORTED                 -> 0xFF5555;
            case PAUSED                  -> 0xAAAAAA;
            default                      -> 0xFFFFFF;
        };
    }

    private static int irColor(IntegrityReport.Status s) {
        return switch (s) {
            case OK       -> 0x55FF55;
            case GRIEFED  -> 0xFF5555;
            case UNLOADED -> 0xFFAA00;
            default       -> 0xAAAAAA;
        };
    }
}
