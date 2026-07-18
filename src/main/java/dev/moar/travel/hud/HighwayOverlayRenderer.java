package dev.moar.travel.hud;

import dev.moar.travel.TravelManager;
import dev.moar.travel.plan.HighwayCandidate;
import dev.moar.travel.telemetry.TravelTelemetry;

/*? if >=26.1 {*//*
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
*//*?} else if >=1.21.11 {*//*
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
*//*?} else if >=1.21.10 {*//*
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
*//*?} else {*/
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
/*?}*/

import org.joml.Matrix4f;

// Draw wireframe blocks for the detected highway.
public final class HighwayOverlayRenderer {

    private static final float ALPHA      = 1.0f;
    private static final float LINE_WIDTH = 2.0f;

    // ── Custom lines pipeline for 1.21.11 (RenderLayer.getLines() removed) ──
    /*? if >=26.1 {*//*
    *//*?} else if >=1.21.11 {*//*
    private static final RenderPipeline HIGHWAY_LINES_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("moar", "pipeline/highway_lines"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
            .build();
    private static final RenderLayer HIGHWAY_LINES_LAYER = RenderLayer.of("moar_highway_lines",
            RenderSetup.builder(HIGHWAY_LINES_PIPELINE)
                    .expectedBufferSize(4096)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .build());
    *//*?}*/

    private HighwayOverlayRenderer() {}

    // ── Registration ──────────────────────────────────────────────

    public static void register() {
        /*? if >=26.1 {*//*
        LevelRenderEvents.END_MAIN.register(HighwayOverlayRenderer::onRender26);
        *//*?} else if >=1.21.10 {*//*
        WorldRenderEvents.END_MAIN.register(HighwayOverlayRenderer::onRender);
        *//*?} else {*/
        WorldRenderEvents.LAST.register(HighwayOverlayRenderer::onRender);
        /*?}*/
    }

    // ── Render callbacks ──────────────────────────────────────────

    /*? if >=26.2 {*//*
    private static void onRender26(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TravelTelemetry t = TravelManager.get().snapshot();
        HighwayCandidate hw = t.selectedHighway();
        if (hw == null || hw.floorY == Integer.MIN_VALUE) return;

        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        BlockPos playerPos = mc.player.blockPosition();

        PoseStack ms = ctx.poseStack();
        ms.pushPose();
        ms.translate(cam.x, cam.y, cam.z);
        ctx.submitNodeCollector().submitCustomGeometry(ms, RenderTypes.lines(),
                (pose, lines) -> renderHighway26(pose, cam, lines, hw, playerPos));
        ms.popPose();
    }

    private static void renderHighway26(PoseStack.Pose pose, Vec3 cam,
            VertexConsumer vc, HighwayCandidate hw, BlockPos playerPos) {
        int stepDx = hw.axis.stepDx, stepDz = hw.axis.stepDz;
        int perpDx = hw.axis.perpDx(), perpDz = hw.axis.perpDz();
        int halfWidth = hw.width > 0 ? hw.width / 2 : 1;
        int floorY    = hw.floorY;
        int anchorX   = hw.entry.getX(), anchorZ = hw.entry.getZ();
        int playerStep = (playerPos.getX() - anchorX) * stepDx
                       + (playerPos.getZ() - anchorZ) * stepDz;

        for (int step = -10; step <= 10; step++) {
            int ax = anchorX + stepDx * (playerStep + step);
            int az = anchorZ + stepDz * (playerStep + step);

            for (int p = -halfWidth; p <= halfWidth; p++) {
                emitBox26(pose, vc, cam, ax + perpDx * p, floorY, az + perpDz * p, 0f, 1f, 0f);
            }
            emitBox26(pose, vc, cam, ax - perpDx * (halfWidth + 1), floorY,   az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox26(pose, vc, cam, ax - perpDx * (halfWidth + 1), floorY+1, az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox26(pose, vc, cam, ax + perpDx * (halfWidth + 1), floorY,   az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox26(pose, vc, cam, ax + perpDx * (halfWidth + 1), floorY+1, az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
        }
    }

    private static void emitBox26(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
            int bx, int by, int bz, float r, float g, float b) {
        float x1 = (float)(bx - cam.x), y1 = (float)(by - cam.y), z1 = (float)(bz - cam.z);
        float x2 = x1 + 1f, y2 = y1 + 1f, z2 = z1 + 1f;
        org.joml.Matrix4f m = pose.pose();
        emitLine26(vc,m,pose, x1,y1,z1, x2,y1,z1, r,g,b); emitLine26(vc,m,pose, x2,y1,z1, x2,y1,z2, r,g,b);
        emitLine26(vc,m,pose, x2,y1,z2, x1,y1,z2, r,g,b); emitLine26(vc,m,pose, x1,y1,z2, x1,y1,z1, r,g,b);
        emitLine26(vc,m,pose, x1,y2,z1, x2,y2,z1, r,g,b); emitLine26(vc,m,pose, x2,y2,z1, x2,y2,z2, r,g,b);
        emitLine26(vc,m,pose, x2,y2,z2, x1,y2,z2, r,g,b); emitLine26(vc,m,pose, x1,y2,z2, x1,y2,z1, r,g,b);
        emitLine26(vc,m,pose, x1,y1,z1, x1,y2,z1, r,g,b); emitLine26(vc,m,pose, x2,y1,z1, x2,y2,z1, r,g,b);
        emitLine26(vc,m,pose, x2,y1,z2, x2,y2,z2, r,g,b); emitLine26(vc,m,pose, x1,y1,z2, x1,y2,z2, r,g,b);
    }

    private static void emitLine26(VertexConsumer vc, org.joml.Matrix4f m,
            com.mojang.blaze3d.vertex.PoseStack.Pose pose,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float b) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (len<0.0001f) return;
        float nx=dx/len, ny=dy/len, nz=dz/len;
        vc.addVertex(m,x1,y1,z1).setColor(r,g,b,ALPHA).setNormal(pose,nx,ny,nz).setLineWidth(LINE_WIDTH);
        vc.addVertex(m,x2,y2,z2).setColor(r,g,b,ALPHA).setNormal(pose,nx,ny,nz).setLineWidth(LINE_WIDTH);
    }
    *//*?} else if >=26.1 {*//*
    private static void onRender26(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TravelTelemetry t = TravelManager.get().snapshot();
        HighwayCandidate hw = t.selectedHighway();
        if (hw == null || hw.floorY == Integer.MIN_VALUE) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        BlockPos playerPos = mc.player.blockPosition();
        var bufferSource = ctx.bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderTypes.lines());

        PoseStack ms = ctx.poseStack();
        ms.pushPose();
        ms.translate(cam.x, cam.y, cam.z);
        renderHighway26(ms.last(), cam, lines, hw, playerPos);
        ms.popPose();
        bufferSource.endBatch(RenderTypes.lines());
    }

    private static void renderHighway26(PoseStack.Pose pose, Vec3 cam,
            VertexConsumer vc, HighwayCandidate hw, BlockPos playerPos) {
        int stepDx = hw.axis.stepDx, stepDz = hw.axis.stepDz;
        int perpDx = hw.axis.perpDx(), perpDz = hw.axis.perpDz();
        int halfWidth = hw.width > 0 ? hw.width / 2 : 1;
        int floorY = hw.floorY;
        int anchorX = hw.entry.getX(), anchorZ = hw.entry.getZ();
        int playerStep = (playerPos.getX() - anchorX) * stepDx
                + (playerPos.getZ() - anchorZ) * stepDz;

        for (int step = -10; step <= 10; step++) {
            int ax = anchorX + stepDx * (playerStep + step);
            int az = anchorZ + stepDz * (playerStep + step);
            for (int p = -halfWidth; p <= halfWidth; p++) {
                emitBox26(pose, vc, cam, ax + perpDx * p, floorY, az + perpDz * p, 0f, 1f, 0f);
            }
            emitBox26(pose, vc, cam, ax - perpDx * (halfWidth + 1), floorY, az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox26(pose, vc, cam, ax - perpDx * (halfWidth + 1), floorY + 1, az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox26(pose, vc, cam, ax + perpDx * (halfWidth + 1), floorY, az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox26(pose, vc, cam, ax + perpDx * (halfWidth + 1), floorY + 1, az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
        }
    }

    private static void emitBox26(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
            int bx, int by, int bz, float r, float g, float b) {
        float x1 = (float) (bx - cam.x), y1 = (float) (by - cam.y), z1 = (float) (bz - cam.z);
        float x2 = x1 + 1f, y2 = y1 + 1f, z2 = z1 + 1f;
        org.joml.Matrix4f m = pose.pose();
        emitLine26(vc,m,pose, x1,y1,z1, x2,y1,z1, r,g,b); emitLine26(vc,m,pose, x2,y1,z1, x2,y1,z2, r,g,b);
        emitLine26(vc,m,pose, x2,y1,z2, x1,y1,z2, r,g,b); emitLine26(vc,m,pose, x1,y1,z2, x1,y1,z1, r,g,b);
        emitLine26(vc,m,pose, x1,y2,z1, x2,y2,z1, r,g,b); emitLine26(vc,m,pose, x2,y2,z1, x2,y2,z2, r,g,b);
        emitLine26(vc,m,pose, x2,y2,z2, x1,y2,z2, r,g,b); emitLine26(vc,m,pose, x1,y2,z2, x1,y2,z1, r,g,b);
        emitLine26(vc,m,pose, x1,y1,z1, x1,y2,z1, r,g,b); emitLine26(vc,m,pose, x2,y1,z1, x2,y2,z1, r,g,b);
        emitLine26(vc,m,pose, x2,y1,z2, x2,y2,z2, r,g,b); emitLine26(vc,m,pose, x1,y1,z2, x1,y2,z2, r,g,b);
    }

    private static void emitLine26(VertexConsumer vc, org.joml.Matrix4f m,
            PoseStack.Pose pose, float x1, float y1, float z1,
            float x2, float y2, float z2, float r, float g, float b) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.0001f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        vc.addVertex(m,x1,y1,z1).setColor(r,g,b,ALPHA).setNormal(pose,nx,ny,nz).setLineWidth(LINE_WIDTH);
        vc.addVertex(m,x2,y2,z2).setColor(r,g,b,ALPHA).setNormal(pose,nx,ny,nz).setLineWidth(LINE_WIDTH);
    }
    *//*?} else if >=1.21.11 {*//*
    private static void onRender(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        TravelTelemetry t = TravelManager.get().snapshot();
        HighwayCandidate hw = t.selectedHighway();
        if (hw == null || hw.floorY == Integer.MIN_VALUE) return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        BlockPos playerPos = mc.player.getBlockPos();

        VertexConsumerProvider.Immediate consumers =
                mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = consumers.getBuffer(HIGHWAY_LINES_LAYER);

        MatrixStack ms = ctx.matrices();
        ms.push();
        ms.translate(cam.x, cam.y, cam.z);
        renderHighway11(ms, cam, lines, hw, playerPos);
        ms.pop();
        consumers.draw(HIGHWAY_LINES_LAYER);
    }

    private static void renderHighway11(MatrixStack ms, Vec3d cam,
            VertexConsumer vc, HighwayCandidate hw, BlockPos playerPos) {
        int stepDx = hw.axis.stepDx, stepDz = hw.axis.stepDz;
        int perpDx = hw.axis.perpDx(), perpDz = hw.axis.perpDz();
        int halfWidth = hw.width > 0 ? hw.width / 2 : 1;
        int floorY    = hw.floorY;
        int anchorX   = hw.entry.getX(), anchorZ = hw.entry.getZ();
        int playerStep = (playerPos.getX() - anchorX) * stepDx
                       + (playerPos.getZ() - anchorZ) * stepDz;

        for (int step = -10; step <= 10; step++) {
            int ax = anchorX + stepDx * (playerStep + step);
            int az = anchorZ + stepDz * (playerStep + step);

            for (int p = -halfWidth; p <= halfWidth; p++) {
                emitBox11(ms, vc, cam, ax + perpDx * p, floorY, az + perpDz * p, 0f, 1f, 0f);
            }
            // Rails (always rendered regardless of hasLeftRail/hasRightRail when width > 0)
            emitBox11(ms, vc, cam, ax - perpDx * (halfWidth + 1), floorY,   az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox11(ms, vc, cam, ax - perpDx * (halfWidth + 1), floorY+1, az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox11(ms, vc, cam, ax + perpDx * (halfWidth + 1), floorY,   az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox11(ms, vc, cam, ax + perpDx * (halfWidth + 1), floorY+1, az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
        }
    }

    private static void emitBox11(MatrixStack ms, VertexConsumer vc, Vec3d cam,
            int bx, int by, int bz, float r, float g, float b) {
        float x1 = (float)(bx - cam.x), y1 = (float)(by - cam.y), z1 = (float)(bz - cam.z);
        float x2 = x1 + 1f, y2 = y1 + 1f, z2 = z1 + 1f;
        Matrix4f m = ms.peek().getPositionMatrix();
        var e = ms.peek();
        emitLine11(vc,m,e, x1,y1,z1, x2,y1,z1, r,g,b); emitLine11(vc,m,e, x2,y1,z1, x2,y1,z2, r,g,b);
        emitLine11(vc,m,e, x2,y1,z2, x1,y1,z2, r,g,b); emitLine11(vc,m,e, x1,y1,z2, x1,y1,z1, r,g,b);
        emitLine11(vc,m,e, x1,y2,z1, x2,y2,z1, r,g,b); emitLine11(vc,m,e, x2,y2,z1, x2,y2,z2, r,g,b);
        emitLine11(vc,m,e, x2,y2,z2, x1,y2,z2, r,g,b); emitLine11(vc,m,e, x1,y2,z2, x1,y2,z1, r,g,b);
        emitLine11(vc,m,e, x1,y1,z1, x1,y2,z1, r,g,b); emitLine11(vc,m,e, x2,y1,z1, x2,y2,z1, r,g,b);
        emitLine11(vc,m,e, x2,y1,z2, x2,y2,z2, r,g,b); emitLine11(vc,m,e, x1,y1,z2, x1,y2,z2, r,g,b);
    }

    private static void emitLine11(VertexConsumer vc, Matrix4f m,
            net.minecraft.client.util.math.MatrixStack.Entry e,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float b) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (len<0.0001f) return;
        float nx=dx/len, ny=dy/len, nz=dz/len;
        vc.vertex(m,x1,y1,z1).color(r,g,b,ALPHA).normal(e,nx,ny,nz).lineWidth(LINE_WIDTH);
        vc.vertex(m,x2,y2,z2).color(r,g,b,ALPHA).normal(e,nx,ny,nz).lineWidth(LINE_WIDTH);
    }
    *//*?} else if >=1.21.10 {*//*
    private static void onRender(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        TravelTelemetry t = TravelManager.get().snapshot();
        HighwayCandidate hw = t.selectedHighway();
        if (hw == null || hw.floorY == Integer.MIN_VALUE) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        BlockPos playerPos = mc.player.getBlockPos();

        VertexConsumerProvider.Immediate consumers =
                mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        MatrixStack ms = ctx.matrices();
        ms.push();
        ms.translate(cam.x, cam.y, cam.z);
        renderHighway10(ms, cam, lines, hw, playerPos);
        ms.pop();
        consumers.draw(RenderLayer.getLines());
    }

    private static void renderHighway10(MatrixStack ms, Vec3d cam,
            VertexConsumer vc, HighwayCandidate hw, BlockPos playerPos) {
        int stepDx = hw.axis.stepDx, stepDz = hw.axis.stepDz;
        int perpDx = hw.axis.perpDx(), perpDz = hw.axis.perpDz();
        int halfWidth = hw.width > 0 ? hw.width / 2 : 1;
        int floorY    = hw.floorY;
        int anchorX   = hw.entry.getX(), anchorZ = hw.entry.getZ();
        int playerStep = (playerPos.getX() - anchorX) * stepDx
                       + (playerPos.getZ() - anchorZ) * stepDz;

        for (int step = -10; step <= 10; step++) {
            int ax = anchorX + stepDx * (playerStep + step);
            int az = anchorZ + stepDz * (playerStep + step);

            for (int p = -halfWidth; p <= halfWidth; p++) {
                emitBox10(ms, vc, cam, ax + perpDx * p, floorY, az + perpDz * p, 0f, 1f, 0f);
            }
            emitBox10(ms, vc, cam, ax - perpDx * (halfWidth + 1), floorY,   az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox10(ms, vc, cam, ax - perpDx * (halfWidth + 1), floorY+1, az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox10(ms, vc, cam, ax + perpDx * (halfWidth + 1), floorY,   az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox10(ms, vc, cam, ax + perpDx * (halfWidth + 1), floorY+1, az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
        }
    }

    private static void emitBox10(MatrixStack ms, VertexConsumer vc, Vec3d cam,
            int bx, int by, int bz, float r, float g, float b) {
        float x1 = (float)(bx - cam.x), y1 = (float)(by - cam.y), z1 = (float)(bz - cam.z);
        float x2 = x1 + 1f, y2 = y1 + 1f, z2 = z1 + 1f;
        Matrix4f m = ms.peek().getPositionMatrix();
        var e = ms.peek();
        emitLine10(vc,m,e, x1,y1,z1, x2,y1,z1, r,g,b); emitLine10(vc,m,e, x2,y1,z1, x2,y1,z2, r,g,b);
        emitLine10(vc,m,e, x2,y1,z2, x1,y1,z2, r,g,b); emitLine10(vc,m,e, x1,y1,z2, x1,y1,z1, r,g,b);
        emitLine10(vc,m,e, x1,y2,z1, x2,y2,z1, r,g,b); emitLine10(vc,m,e, x2,y2,z1, x2,y2,z2, r,g,b);
        emitLine10(vc,m,e, x2,y2,z2, x1,y2,z2, r,g,b); emitLine10(vc,m,e, x1,y2,z2, x1,y2,z1, r,g,b);
        emitLine10(vc,m,e, x1,y1,z1, x1,y2,z1, r,g,b); emitLine10(vc,m,e, x2,y1,z1, x2,y2,z1, r,g,b);
        emitLine10(vc,m,e, x2,y1,z2, x2,y2,z2, r,g,b); emitLine10(vc,m,e, x1,y1,z2, x1,y2,z2, r,g,b);
    }

    private static void emitLine10(VertexConsumer vc, Matrix4f m,
            net.minecraft.client.util.math.MatrixStack.Entry e,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float b) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (len<0.0001f) return;
        float nx=dx/len, ny=dy/len, nz=dz/len;
        vc.vertex(m,x1,y1,z1).color(r,g,b,ALPHA).normal(e,nx,ny,nz);
        vc.vertex(m,x2,y2,z2).color(r,g,b,ALPHA).normal(e,nx,ny,nz);
    }
    *//*?} else {*/
    private static void onRender(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        TravelTelemetry t = TravelManager.get().snapshot();
        HighwayCandidate hw = t.selectedHighway();
        if (hw == null || hw.floorY == Integer.MIN_VALUE) return;

        Vec3d cam = ctx.camera().getPos();
        BlockPos playerPos = mc.player.getBlockPos();

        VertexConsumerProvider.Immediate consumers =
                mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(cam.x, cam.y, cam.z);
        renderHighway(ms, cam, lines, hw, playerPos);
        ms.pop();
        consumers.draw(RenderLayer.getLines());
    }

    private static void renderHighway(MatrixStack ms, Vec3d cam,
            VertexConsumer vc, HighwayCandidate hw, BlockPos playerPos) {
        int stepDx = hw.axis.stepDx, stepDz = hw.axis.stepDz;
        int perpDx = hw.axis.perpDx(), perpDz = hw.axis.perpDz();
        int halfWidth = hw.width > 0 ? hw.width / 2 : 1;
        int floorY    = hw.floorY;
        int anchorX   = hw.entry.getX(), anchorZ = hw.entry.getZ();
        int playerStep = (playerPos.getX() - anchorX) * stepDx
                       + (playerPos.getZ() - anchorZ) * stepDz;

        for (int step = -10; step <= 10; step++) {
            int ax = anchorX + stepDx * (playerStep + step);
            int az = anchorZ + stepDz * (playerStep + step);

            for (int p = -halfWidth; p <= halfWidth; p++) {
                emitBox(ms, vc, cam, ax + perpDx * p, floorY, az + perpDz * p, 0f, 1f, 0f);
            }
            // Left rail
            emitBox(ms, vc, cam, ax - perpDx * (halfWidth + 1), floorY,   az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox(ms, vc, cam, ax - perpDx * (halfWidth + 1), floorY+1, az - perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            // Right rail
            emitBox(ms, vc, cam, ax + perpDx * (halfWidth + 1), floorY,   az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
            emitBox(ms, vc, cam, ax + perpDx * (halfWidth + 1), floorY+1, az + perpDz * (halfWidth + 1), 0f, 0.53f, 1f);
        }
    }

    private static void emitBox(MatrixStack ms, VertexConsumer vc, Vec3d cam,
            int bx, int by, int bz, float r, float g, float b) {
        float x1 = (float)(bx - cam.x), y1 = (float)(by - cam.y), z1 = (float)(bz - cam.z);
        float x2 = x1 + 1f, y2 = y1 + 1f, z2 = z1 + 1f;
        Matrix4f m = ms.peek().getPositionMatrix();
        var e = ms.peek();
        emitLine(vc,m,e, x1,y1,z1, x2,y1,z1, r,g,b); emitLine(vc,m,e, x2,y1,z1, x2,y1,z2, r,g,b);
        emitLine(vc,m,e, x2,y1,z2, x1,y1,z2, r,g,b); emitLine(vc,m,e, x1,y1,z2, x1,y1,z1, r,g,b);
        emitLine(vc,m,e, x1,y2,z1, x2,y2,z1, r,g,b); emitLine(vc,m,e, x2,y2,z1, x2,y2,z2, r,g,b);
        emitLine(vc,m,e, x2,y2,z2, x1,y2,z2, r,g,b); emitLine(vc,m,e, x1,y2,z2, x1,y2,z1, r,g,b);
        emitLine(vc,m,e, x1,y1,z1, x1,y2,z1, r,g,b); emitLine(vc,m,e, x2,y1,z1, x2,y2,z1, r,g,b);
        emitLine(vc,m,e, x2,y1,z2, x2,y2,z2, r,g,b); emitLine(vc,m,e, x1,y1,z2, x1,y2,z2, r,g,b);
    }

    private static void emitLine(VertexConsumer vc, Matrix4f m,
            net.minecraft.client.util.math.MatrixStack.Entry e,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float b) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (len<0.0001f) return;
        float nx=dx/len, ny=dy/len, nz=dz/len;
        vc.vertex(m,x1,y1,z1).color(r,g,b,ALPHA).normal(e,nx,ny,nz);
        vc.vertex(m,x2,y2,z2).color(r,g,b,ALPHA).normal(e,nx,ny,nz);
    }
    /*?}*/
}
