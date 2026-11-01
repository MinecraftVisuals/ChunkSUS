package com.suschunkfinder;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public final class SusRenderer {
    /** #F050F8 */
    private static final int PINK_RGB = 0xF050F8;

    private SusRenderer() {}

    public static void init() {
        WorldRenderEvents.END_MAIN.register(SusRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        SusConfig cfg = SusConfig.get();
        if (!cfg.enabled) return;

        Map<ChunkPos, ChunkScanner.Flagged> flagged = ChunkScanner.flagged();
        if (flagged.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        RenderLayer layer = RenderLayers.debugQuads();
        VertexConsumer vc = consumers.getBuffer(layer);
        MatrixStack.Entry entry = matrices.peek();

        int worldBottom = mc.world.getBottomY();
        int worldTop = worldBottom + mc.world.getHeight();
        int baseAlpha = Math.round(cfg.opacityPercent / 100f * 255f);
        double maxDist = cfg.renderDistanceChunks * 16.0;

        for (ChunkScanner.Flagged f : flagged.values()) {
            ChunkPos p = f.pos();
            double cx = p.getStartX() + 8.0;
            double cz = p.getStartZ() + 8.0;
            if (Math.abs(cx - cam.x) > maxDist || Math.abs(cz - cam.z) > maxDist) continue;

            int yMin;
            int yMax;
            if (cfg.fullColumn) {
                yMin = worldBottom;
                yMax = Math.min(worldTop, Math.max(f.maxY() + 2, (int) cam.y + 48));
            } else {
                yMin = f.minY() - 1;
                yMax = f.maxY() + 2;
            }

            // Fade when the camera is inside the column so the screen isn't tinted solid pink
            boolean inside = cam.x >= p.getStartX() && cam.x <= p.getStartX() + 16
                    && cam.z >= p.getStartZ() && cam.z <= p.getStartZ() + 16;
            int alpha = inside ? Math.max(8, baseAlpha / 3) : baseAlpha;
            int argb = (alpha << 24) | PINK_RGB;

            float x1 = (float) (p.getStartX() - cam.x);
            float x2 = x1 + 16f;
            float z1 = (float) (p.getStartZ() - cam.z);
            float z2 = z1 + 16f;
            float y1 = (float) (yMin - cam.y);
            float y2 = (float) (yMax - cam.y);

            box(vc, entry, argb, x1, y1, z1, x2, y2, z2);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    private static void box(VertexConsumer vc, MatrixStack.Entry e, int argb,
                            float x1, float y1, float z1, float x2, float y2, float z2) {
        quad(vc, e, argb, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2); // bottom
        quad(vc, e, argb, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1); // top
        quad(vc, e, argb, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1); // north
        quad(vc, e, argb, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2); // south
        quad(vc, e, argb, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1); // west
        quad(vc, e, argb, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2); // east
    }

    /** Emits the quad twice (both windings) so it is visible from either side regardless of culling. */
    private static void quad(VertexConsumer vc, MatrixStack.Entry e, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        v(vc, e, argb, ax, ay, az);
        v(vc, e, argb, bx, by, bz);
        v(vc, e, argb, cx, cy, cz);
        v(vc, e, argb, dx, dy, dz);

        v(vc, e, argb, dx, dy, dz);
        v(vc, e, argb, cx, cy, cz);
        v(vc, e, argb, bx, by, bz);
        v(vc, e, argb, ax, ay, az);
    }

    private static void v(VertexConsumer vc, MatrixStack.Entry e, int argb, float x, float y, float z) {
        vc.vertex(e, x, y, z).color(argb);
    }
}
