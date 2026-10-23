package com.sharinganmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sharinganmod.SharinganMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Desenha os dois olhos (Sharingan) no rosto do jogador enquanto o Sharingan esta ativo. */
public class FaceLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final String[] NAMES = {"", "1", "2", "3", "itachi", "sasuke", "obito", "shisui", "eterno", "madara", "izuna", "eterno_madara"};
    private static final ResourceLocation[] TEX = new ResourceLocation[NAMES.length];

    static {
        for (int i = 1; i < NAMES.length; i++) {
            TEX[i] = ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "textures/gui/eye_" + NAMES[i] + ".png");
        }
    }

    /** Na frente da camada externa da cabeca (z = -4,5), por cima do cabelo e detalhes. */
    private static final float EYE_Z_FRONT = -4.6f;
    /** Entre a cabeca (z = -4) e a camada externa: o cabelo e os detalhes da segunda camada ficam por cima. */
    private static final float EYE_Z_BEHIND = -4.25f;

    public FaceLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int packed = ClientFace.get(player.getUUID());
        int code = packed & 15;
        // 0 os dois olhos, 1 so o esquerdo, 2 so o direito (o esquerdo do jogador fica em +x no modelo)
        int side = (packed >> 4) & 15;
        if (code <= 0 || code >= TEX.length || player.isInvisible() || player.isSpectator()) return;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX[code]));
        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);
        PoseStack.Pose pose = poseStack.last();
        // cada jogador ve o rosto dos outros do jeito que o dono configurou (o proprio, do jeito local)
        float dx = ClientLayout.DEFAULT_DX;
        float cy = ClientLayout.DEFAULT_Y;
        float half = ClientLayout.DEFAULT_HALF;
        float dx2 = dx;
        float cy2 = cy;
        boolean front = true;
        if (player == Minecraft.getInstance().player) {
            dx = ClientLayout.eyeDx;
            cy = ClientLayout.eyeY;
            half = ClientLayout.eyeHalf;
            dx2 = ClientLayout.eyeMirror ? dx : ClientLayout.eyeDx2;
            cy2 = ClientLayout.eyeMirror ? cy : ClientLayout.eyeY2;
            front = ClientLayout.eyeFront;
        } else {
            float[] l = ClientFace.layout(player.getUUID());
            if (l != null) {
                dx = l[0];
                cy = l[1];
                half = l[2];
                if (l.length > 5) {
                    dx2 = l[3];
                    cy2 = l[4];
                    front = l[5] > 0.5f;
                } else {
                    dx2 = dx;
                    cy2 = cy;
                }
            }
        }
        // ao ligar, os olhos dao uma girada (duas voltas, desacelerando) antes de ficar normais
        float spin = ClientFace.spinProgress(player.getUUID(), SPIN_MS);
        float angle = spin >= 1f ? 0f : (1f - (1f - spin) * (1f - spin) * (1f - spin)) * 720f;
        float[] fin = ClientFace.finalAngles(player.getUUID());
        float z = front ? EYE_Z_FRONT : EYE_Z_BEHIND;
        if (side == 0 || side == 1) quad(vc, pose, dx, cy, half, z, angle + fin[0]);
        if (side == 0 || side == 2) quad(vc, pose, -dx2, cy2, half, z, -angle + fin[1]);
        poseStack.popPose();
    }

    private static final long SPIN_MS = 900L;

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float half, float zPx,
                             float angleDeg) {
        float s = 1.0f / 16.0f;
        float z = zPx * s;
        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float ul = 0f;
        float ur = 1f;
        // cantos em ordem: inferior esquerdo, inferior direito, superior direito, superior esquerdo (y cresce para baixo)
        float[][] corners = {{-half, half, ul, 1f}, {half, half, ur, 1f}, {half, -half, ur, 0f}, {-half, -half, ul, 0f}};
        for (float[] c : corners) {
            float rx = c[0] * cos - c[1] * sin;
            float ry = c[0] * sin + c[1] * cos;
            vertex(vc, pose, (cx + rx) * s, (cy + ry) * s, z, c[2], c[3]);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0f, 0.0f, -1.0f);
    }
}
