package github.com.gengyoubo.thaumgiesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Drawing flat on a table top, in the node's pixels: x across (the screen's right), z towards the camera (the screen's foot), lifted
 * a little off the wood so that nothing flickers into it. Everything is seen from either side, so the turn of the scene never hides it.
 */
final class TableCanvas {
    private static final Map<ResourceLocation, Boolean> TEXTURES = new HashMap<>();
    private final PoseStack poseStack;
    private final MultiBufferSource buffers;
    private final int light;

    TableCanvas(PoseStack poseStack, MultiBufferSource buffers, int light) {
        this.poseStack = poseStack;
        this.buffers = buffers;
        this.light = light;
    }

    /** A flat rectangle {@code lift} pixels over the table. */
    void rect(float x, float z, float width, float depth, float lift, int argb) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
        flat(consumer, x, z, x + width, z + depth, lift, argb, false, 0, 0, 1, 1);
    }

    /** A line between two points, {@code width} pixels wide. */
    void line(float x0, float z0, float x1, float z1, float width, float lift, int argb) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        float nx = -dz / length * width / 2;
        float nz = dx / length * width / 2;
        quad(buffers.getBuffer(RenderType.textBackground()), new float[][] {
                {x0 + nx, lift, z0 + nz}, {x1 + nx, lift, z1 + nz}, {x1 - nx, lift, z1 - nz}, {x0 - nx, lift, z0 - nz}}, argb);
    }

    /** A flat-topped hexagon of {@code size} (middle to corner), filled. */
    void hexagon(float x, float z, float size, float lift, int argb) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
        for (int side = 0; side < 6; side++) {
            float a0 = side * Mth.PI / 3;
            float a1 = (side + 1) * Mth.PI / 3;
            quad(consumer, new float[][] {{x, lift, z}, {x + size * Mth.cos(a0), lift, z + size * Mth.sin(a0)},
                    {x + size * Mth.cos(a1), lift, z + size * Mth.sin(a1)}, {x, lift, z}}, argb);
        }
    }

    /** A flat-topped hexagon's edge. */
    void hexagonEdge(float x, float z, float size, float width, float lift, int argb) {
        for (int side = 0; side < 6; side++) {
            float a0 = side * Mth.PI / 3;
            float a1 = (side + 1) * Mth.PI / 3;
            line(x + size * Mth.cos(a0), z + size * Mth.sin(a0), x + size * Mth.cos(a1), z + size * Mth.sin(a1), width, lift, argb);
        }
    }

    /** A disc of {@code radius}. */
    void disc(float x, float z, float radius, float lift, int argb) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
        int steps = 16;
        for (int step = 0; step < steps; step++) {
            float a0 = step * Mth.TWO_PI / steps;
            float a1 = (step + 1) * Mth.TWO_PI / steps;
            quad(consumer, new float[][] {{x, lift, z}, {x + radius * Mth.cos(a0), lift, z + radius * Mth.sin(a0)},
                    {x + radius * Mth.cos(a1), lift, z + radius * Mth.sin(a1)}, {x, lift, z}}, argb);
        }
    }

    /** A box standing on the table: its five faces that can be seen, {@code top} lifted from {@code bottom}. */
    void box(float x0, float z0, float x1, float z1, float bottom, float top, int side, int cap) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
        quad(consumer, new float[][] {{x0, top, z0}, {x1, top, z0}, {x1, top, z1}, {x0, top, z1}}, cap);
        quad(consumer, new float[][] {{x0, bottom, z1}, {x1, bottom, z1}, {x1, top, z1}, {x0, top, z1}}, side);
        quad(consumer, new float[][] {{x0, bottom, z0}, {x1, bottom, z0}, {x1, top, z0}, {x0, top, z0}}, side);
        quad(consumer, new float[][] {{x0, bottom, z0}, {x0, bottom, z1}, {x0, top, z1}, {x0, top, z0}}, darker(side));
        quad(consumer, new float[][] {{x1, bottom, z0}, {x1, bottom, z1}, {x1, top, z1}, {x1, top, z0}}, darker(side));
    }

    /** A texture laid flat, tinted; false when the texture is not there. */
    boolean texture(ResourceLocation texture, float x, float z, float width, float depth, float lift, int argb) {
        if (!exists(texture)) {
            return false;
        }
        flat(buffers.getBuffer(RenderType.text(texture)), x, z, x + width, z + depth, lift, argb, true, 0, 0, 1, 1);
        return true;
    }

    /**
     * A texture laid flat that runs over the table's edge at {@code edgeZ}: what lies past it hangs down over the edge, {@code drop}
     * degrees from flat, as a sheet of paper too big for the table does. False when the texture is not there.
     */
    boolean drapedTexture(ResourceLocation texture, float x, float z, float width, float depth, float lift, float edgeZ,
                          float drop, int argb) {
        if (!exists(texture)) {
            return false;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture));
        float end = z + depth;
        if (end <= edgeZ) {
            flat(consumer, x, z, x + width, end, lift, argb, true, 0, 0, 1, 1);
            return true;
        }
        float fold = (edgeZ - z) / depth;
        flat(consumer, x, z, x + width, edgeZ, lift, argb, true, 0, 0, 1, fold);
        float hanging = end - edgeZ;
        float angle = drop * Mth.DEG_TO_RAD;
        float tipY = lift - hanging * Mth.sin(angle);
        float tipZ = edgeZ + hanging * Mth.cos(angle);
        Matrix4f matrix = poseStack.last().pose();
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        float[][] corners = {{x, lift, edgeZ, 0, fold}, {x, tipY, tipZ, 0, 1}, {x + width, tipY, tipZ, 1, 1},
                {x + width, lift, edgeZ, 1, fold}};
        for (int side = 0; side < 2; side++) {
            for (int corner = 0; corner < 4; corner++) {
                float[] at = corners[side == 0 ? corner : 3 - corner];
                consumer.vertex(matrix, at[0], at[1], at[2]).color(r, g, b, a).uv(at[3], at[4]).uv2(light).endVertex();
            }
        }
        return true;
    }

    /** Text lying on the table, read from the camera's side; {@code scale} pixels to a font unit. */
    void text(String text, float x, float z, float scale, float lift, int argb) {
        Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(x, lift, z)
                .mul(new Matrix4f(scale, 0, 0, 0, 0, 0, scale, 0, 0, 1, 0, 0, 0, 0, 0, 1));
        Minecraft.getInstance().font.drawInBatch(text, 0, 0, argb, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
    }

    float width(String text, float scale) {
        return Minecraft.getInstance().font.width(text) * scale;
    }

    private void flat(VertexConsumer consumer, float x0, float z0, float x1, float z1, float lift, int argb,
                      boolean textured, float u0, float v0, float u1, float v1) {
        Matrix4f matrix = poseStack.last().pose();
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        float[][] corners = {{x0, z0, u0, v0}, {x0, z1, u0, v1}, {x1, z1, u1, v1}, {x1, z0, u1, v0}};
        for (int side = 0; side < 2; side++) {
            for (int corner = 0; corner < 4; corner++) {
                float[] at = corners[side == 0 ? corner : 3 - corner];
                consumer.vertex(matrix, at[0], lift, at[1]).color(r, g, b, a);
                if (textured) {
                    consumer.uv(at[2], at[3]);
                }
                consumer.uv2(light).endVertex();
            }
        }
    }

    private void quad(VertexConsumer consumer, float[][] corners, int argb) {
        Matrix4f matrix = poseStack.last().pose();
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        for (int side = 0; side < 2; side++) {
            for (int corner = 0; corner < 4; corner++) {
                float[] at = corners[side == 0 ? corner : 3 - corner];
                consumer.vertex(matrix, at[0], at[1], at[2]).color(r, g, b, a).uv2(light).endVertex();
            }
        }
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 3 / 4;
        int g = (argb >> 8 & 0xFF) * 3 / 4;
        int b = (argb & 0xFF) * 3 / 4;
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    static boolean exists(ResourceLocation texture) {
        return TEXTURES.computeIfAbsent(texture,
                location -> Minecraft.getInstance().getResourceManager().getResource(location).isPresent());
    }
}
