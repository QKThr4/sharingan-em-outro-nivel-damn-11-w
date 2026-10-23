package com.sharinganmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sharinganmod.SharinganMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Visao do Sharingan (tudo so no cliente de quem esta com o Sharingan ativo):
 * silhueta vermelha de entidades invisiveis, previsao do movimento (quanto mais tomoes, mais longe) e rastros (pegadas).
 */
@EventBusSubscriber(modid = SharinganMod.MODID, value = Dist.CLIENT)
public final class ClientVision {
    private ClientVision() {}

    private static final EntityDataAccessor<Byte> FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);
    private static final byte GLOW = 0x40;
    
    // ---- silhueta de invisiveis ----
    private static final Set<Integer> SILHOUETTE = new HashSet<>();
    private static final Map<Integer, String[]> TEAMED = new HashMap<>();
    private static final Map<Integer, int[]> CHAKRA = new HashMap<>();
    private static final Map<Integer, Long> WARN = new HashMap<>();

    public static void clear() {
        SILHOUETTE.clear();
        TEAMED.clear();
        CHAKRA.clear();
        WARN.clear();
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        clear();
    }

    private static void setGlow(Entity e, boolean on) {
        byte b = e.getEntityData().get(FLAGS);
        byte nb = on ? (byte) (b | GLOW) : (byte) (b & ~GLOW);
        if (nb != b) e.getEntityData().set(FLAGS, nb);
    }

    private static PlayerTeam team(Scoreboard sb, String name) {
        PlayerTeam t = sb.getPlayerTeam(name);
        if (t == null) {
            t = sb.addPlayerTeam(name);
            t.setColor(switch (name) {
                case "sharingan_c3" -> ChatFormatting.AQUA;
                case "sharingan_c2" -> ChatFormatting.BLUE;
                case "sharingan_c1" -> ChatFormatting.DARK_BLUE;
                case "sharingan_c0" -> ChatFormatting.DARK_GRAY;
                default -> ChatFormatting.RED;
            });
        }
        return t;
    }

    private static String chakraTeam(int pct) {
        return pct >= 66 ? "sharingan_c3" : pct >= 33 ? "sharingan_c2" : pct >= 8 ? "sharingan_c1" : "sharingan_c0";
    }

    private static void unassign(Scoreboard sb, int id) {
        String[] had = TEAMED.remove(id);
        if (had == null) return;
        PlayerTeam t = sb.getPlayerTeam(had[1]);
        if (t == null) return;
        try {
            sb.removePlayerFromTeam(had[0], t);
        } catch (RuntimeException ignored) {
            // a entidade ja saiu do time
        }
    }

    private static void releaseSilhouette(ClientLevel level) {
        Scoreboard sb = level.getScoreboard();
        for (int id : new ArrayList<>(TEAMED.keySet())) unassign(sb, id);
        for (int id : SILHOUETTE) {
            Entity ent = level.getEntity(id);
            if (ent != null) setGlow(ent, false);
        }
        SILHOUETTE.clear();
        TEAMED.clear();
    }

    /** Coloca a entidade no time colorido (contorno brilhante); false se ela ja pertence a um time de outra origem. */
    private static boolean assign(Scoreboard sb, Entity ent, String teamName) {
        String n = ent.getScoreboardName();
        String[] had = TEAMED.get(ent.getId());
        if (had == null && sb.getPlayersTeam(n) != null) return false;
        if (had != null && !had[1].equals(teamName)) {
            unassign(sb, ent.getId());
            had = null;
        }
        if (had == null) {
            sb.addPlayerToTeam(n, team(sb, teamName));
            TEAMED.put(ent.getId(), new String[] {n, teamName});
        }
        SILHOUETTE.add(ent.getId());
        setGlow(ent, true);
        return true;
    }

    /** Visao de chakra (azul) e leitura de ataque: dados vindos do servidor. pct &lt; 0 = vai atacar. */
    public static void setVision(List<Integer> ids, List<Integer> pcts) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        for (int i = 0; i < ids.size() && i < pcts.size(); i++) {
            int pct = pcts.get(i);
            if (pct < 0) WARN.put(ids.get(i), now + 6);
            else CHAKRA.put(ids.get(i), new int[] {pct, (int) (now + 12)});
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        try {
            tickInner();
        } catch (RuntimeException ex) {
            // a visao e so visual: um erro aqui nunca deve derrubar o jogo
            CHAKRA.clear();
            WARN.clear();
        }
    }

    private static void tickInner() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        int lv = ClientData.DATA.level();
        if (lv <= 0) {
            if (!SILHOUETTE.isEmpty()) releaseSilhouette(level);
            CHAKRA.clear();
            WARN.clear();
            return;
        }
        long now = level.getGameTime();
        Scoreboard sb = level.getScoreboard();
        Set<Integer> seen = new HashSet<>();
        for (Entity ent : level.entitiesForRendering()) {
            if (ent == mc.player || !(ent instanceof LivingEntity le)) continue;
            if (ent.distanceToSqr(mc.player) > 48 * 48) continue;
            // invisivel: silhueta vermelha; senao (3 tomoe em diante) brilho azul pelo chakra
            String want = null;
            if (le.isInvisible()) {
                want = "sharingan_vision";
            } else {
                int[] ch = CHAKRA.get(ent.getId());
                if (ch != null && ch[1] >= now) want = chakraTeam(ch[0]);
            }
            if (want != null && assign(sb, ent, want)) seen.add(ent.getId());
        }
        // quem deixou de se encaixar (ou sumiu) perde o contorno
        Iterator<Integer> it = SILHOUETTE.iterator();
        while (it.hasNext()) {
            int id = it.next();
            if (seen.contains(id)) continue;
            Entity ent = level.getEntity(id);
            if (ent != null) setGlow(ent, false);
            unassign(sb, id);
            it.remove();
        }
        if (now % 40 == 0) {
            CHAKRA.values().removeIf(v -> v[1] < now - 40);
            WARN.values().removeIf(v -> v < now - 40);
        }
    }

    /** O servidor pode reenviar as bandeiras da entidade e apagar o contorno: reaplica a cada quadro. */
    @SubscribeEvent
    public static void frameGlow(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || SILHOUETTE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int id : SILHOUETTE) {
            Entity ent = level.getEntity(id);
            if (ent != null) setGlow(ent, true);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || WARN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || ClientData.DATA.level() <= 0) return;
        try {
            drawWarnings(e, mc, level);
        } catch (RuntimeException ex) {
            WARN.clear();
        }
    }

    /** Leitura de ataque: caixa vermelha e um "!" sobre quem esta prestes a bater, atirar ou explodir. */
    private static void drawWarnings(RenderLevelStageEvent e, Minecraft mc, ClientLevel level) {
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = level.getGameTime();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        org.joml.Vector3f left = e.getCamera().getLeftVector();
        org.joml.Vector3f up = e.getCamera().getUpVector();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Map.Entry<Integer, Long> w : WARN.entrySet()) {
            if (w.getValue() < now) continue;
            Entity ent = level.getEntity(w.getKey());
            if (ent == null || ent == mc.player) continue;
            LevelRenderer.renderLineBox(ps, lines, ent.getBoundingBox().inflate(0.1), 1.0f, 0.05f, 0.05f, 1.0f);
        }
        buffers.endBatch(RenderType.lines());
        VertexConsumer q = buffers.getBuffer(RenderType.debugQuads());
        org.joml.Matrix4f m = ps.last().pose();
        for (Map.Entry<Integer, Long> w : WARN.entrySet()) {
            if (w.getValue() < now) continue;
            Entity ent = level.getEntity(w.getKey());
            if (ent == null || ent == mc.player) continue;
            Vec3 p = ent.getPosition(pt).add(0, ent.getBbHeight() + 0.7, 0);
            double[][] parts = {{-0.07, 0.07, 0.0, 0.32}, {-0.07, 0.07, -0.14, -0.04}};
            for (double[] r : parts) {
                double[][] c = {{r[0], r[2]}, {r[1], r[2]}, {r[1], r[3]}, {r[0], r[3]}};
                for (double[] cc : c) {
                    q.addVertex(m, (float) (p.x + left.x() * cc[0] + up.x() * cc[1]),
                            (float) (p.y + left.y() * cc[0] + up.y() * cc[1]),
                            (float) (p.z + left.z() * cc[0] + up.z() * cc[1])).setColor(255, 30, 30, 235);
                }
            }
        }
        buffers.endBatch(RenderType.debugQuads());
        ps.popPose();
    }
}
