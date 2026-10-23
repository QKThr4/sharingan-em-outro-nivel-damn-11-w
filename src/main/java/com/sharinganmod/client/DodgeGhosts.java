package com.sharinganmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sharinganmod.SharinganMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Afterimage de quem desviou de um golpe: uma silhueta vermelha ao lado que some em 1 segundo. */
@EventBusSubscriber(modid = SharinganMod.MODID, value = Dist.CLIENT)
public final class DodgeGhosts {
    private DodgeGhosts() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "textures/entity/afterimage.png");
    private static final long LIFE_MS = 1000L;
    /** Distancia (blocos) de cada silhueta ao lado do jogador. */
    private static final double SIDE = 0.55;

    private record Ghost(UUID id, double x, double y, double z, float bodyYaw, float netHeadYaw, float pitch,
                         float limbPos, float limbSpeed, float age, boolean crouch, boolean slim, long born) {}

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static PlayerModel<AbstractClientPlayer> wide;
    private static PlayerModel<AbstractClientPlayer> slim;

    private static PlayerModel<AbstractClientPlayer> model(boolean isSlim) {
        Minecraft mc = Minecraft.getInstance();
        if (isSlim) {
            if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slim;
        }
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wide;
    }

    /** Chamado pelo servidor (via rede) quando o jogador desvia. */
    public static void add(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Player pl = level.getPlayerByUUID(id);
        if (!(pl instanceof AbstractClientPlayer ap)) return;
        long now = System.currentTimeMillis();
        float bodyYaw = ap.yBodyRot;
        float headYaw = ap.yHeadRot;
        double rad = Math.toRadians(bodyYaw);
        // vetor para o lado do corpo (perpendicular a direcao em que ele olha)
        double sx = Math.cos(rad);
        double sz = Math.sin(rad);
        boolean isSlim = ap.getSkin().model() == PlayerSkin.Model.SLIM;
        // uma unica silhueta, ao lado do jogador (no mesmo lugar ela ficaria escondida dentro do corpo)
        GHOSTS.add(new Ghost(id, ap.getX() + sx * SIDE, ap.getY(), ap.getZ() + sz * SIDE, bodyYaw,
                Mth.wrapDegrees(headYaw - bodyYaw), ap.getXRot(), ap.walkAnimation.position(),
                Math.min(1.0f, ap.walkAnimation.speed()), ap.tickCount, ap.isCrouching(), isSlim, now));
    }

    public static void clear() {
        GHOSTS.clear();
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        clear();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || GHOSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            GHOSTS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(TEX);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack ps = e.getPoseStack();
        boolean drew = false;

        Iterator<Ghost> it = GHOSTS.iterator();
        while (it.hasNext()) {
            Ghost g = it.next();
            if (now - g.born() > LIFE_MS) {
                it.remove();
                continue;
            }
            Player pl = level.getPlayerByUUID(g.id());
            if (!(pl instanceof AbstractClientPlayer ap)) continue;
            double dx = g.x() - cam.x;
            double dy = g.y() - cam.y;
            double dz = g.z() - cam.z;
            // silhueta colada na camera tamparia a visao
            if (dx * dx + dz * dz < 0.5 && Math.abs(dy) < 2.0) continue;
            float life = 1f - (now - g.born()) / (float) LIFE_MS;
            int alpha = (int) (Mth.clamp(life, 0f, 1f) * 0x90);
            int color = (alpha << 24) | 0xE02020;

            PlayerModel<AbstractClientPlayer> m = model(g.slim());
            m.young = false;
            m.crouching = g.crouch();
            m.setupAnim(ap, g.limbPos(), g.limbSpeed(), g.age(), g.netHeadYaw(), g.pitch());

            ps.pushPose();
            ps.translate(dx, dy, dz);
            ps.mulPose(Axis.YP.rotationDegrees(180.0f - g.bodyYaw()));
            ps.scale(-1.0f, -1.0f, 1.0f);
            ps.scale(0.9375f, 0.9375f, 0.9375f);
            ps.translate(0.0f, -1.501f, 0.0f);
            m.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch(type);
    }
}
