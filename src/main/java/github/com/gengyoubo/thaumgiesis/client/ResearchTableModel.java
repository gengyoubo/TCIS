package github.com.gengyoubo.thaumgiesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Thaumcraft 4's research table, drawn in the scene while the station is open in place of Thaumcraft's own renderer (which also lays
 * a blank parchment on it): the same boxes as its {@code ModelResearchTable}, on its own texture, and the quill as its renderer
 * draws it, a flat icon. Drawn in the desk node's pixels, the node lying on the table top at the block's corner.
 */
final class ResearchTableModel {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/restable.png");
    static final ResourceLocation QUILL = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/tablequill.png");
    private static ModelPart table;
    private static ModelPart inkwell;

    private ResearchTableModel() {
    }

    /** Thaumcraft's model, box for box: its pivots and boxes in its own pixels, mirrored as it mirrors them. */
    private static void bake() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("top", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0, 0, 0, 32, 4, 16),
                PartPose.offset(-8, 0, -8));
        float[][] legs = {{-6, 4, -6}, {-6, 4, 2}, {18, 4, -6}, {18, 4, 2}};
        for (int index = 0; index < legs.length; index++) {
            root.addOrReplaceChild("leg" + index, CubeListBuilder.create().texOffs(0, 24).mirror().addBox(0, 0, 0, 4, 12, 4),
                    PartPose.offset(legs[index][0], legs[index][1], legs[index][2]));
        }
        root.addOrReplaceChild("crossbar", CubeListBuilder.create().texOffs(24, 24).mirror().addBox(0, 0, 0, 24, 4, 4),
                PartPose.offset(-4, 10, -2));
        root.addOrReplaceChild("inkwell", CubeListBuilder.create().texOffs(0, 44).mirror().addBox(0, 0, 0, 3, 2, 3),
                PartPose.offset(-6, -2, 3));
        ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
        inkwell = baked.getChild("inkwell");
        table = baked;
    }

    /** The table, and its inkwell when the scribing tools are on it. */
    static void render(PoseStack pose, MultiBufferSource buffers, int light, boolean tools) {
        if (table == null) {
            bake();
        }
        // As Thaumcraft's renderer places it: from the middle of the block's top, turned over (its model's y runs down).
        pose.pushPose();
        pose.translate(8, 0, 8);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.scale(16, 16, 16);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        inkwell.visible = tools;
        table.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * The quill, Thaumcraft's flat icon {@code size} pixels high, its tip at {@code x, y, z}: standing, leant back from the camera by
     * {@code lean} degrees and turned by {@code turn}. Seen from both sides.
     */
    static void quill(PoseStack pose, MultiBufferSource buffers, int light, float x, float y, float z, float size, float lean,
                      float turn) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.mulPose(Axis.XP.rotationDegrees(lean));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(QUILL));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float[][] corners = {{0, 0, 0, 1}, {size, 0, 1, 1}, {size, size, 1, 0}, {0, size, 0, 0}};
        for (float[] corner : corners) {
            consumer.vertex(matrix, corner[0], corner[1], 0).color(255, 255, 255, 255).uv(corner[2], corner[3])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 0, 1).endVertex();
        }
        pose.popPose();
    }
}
