package com.sharinganmod.client;

import com.sharinganmod.SharinganMod;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Olhar Revelador: o contorno brilhante (visivel atras de paredes) so existe no cliente de quem usou a habilidade,
 * entao nenhum outro jogador ve as entidades rereveladas. A contagem e em tempo real.
 */
@EventBusSubscriber(modid = SharinganMod.MODID, value = Dist.CLIENT)
public final class ClientReveal {
    private ClientReveal() {}

    /** Bandeiras compartilhadas de toda entidade (dado 0); o bit 6 (0x40) e o contorno brilhante. */
    private static final EntityDataAccessor<Byte> FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);
    private static final byte GLOW = 0x40;

    private static final Map<Integer, Long> UNTIL = new HashMap<>();

    public static void add(List<Integer> ids, int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        for (int id : ids) UNTIL.put(id, end);
    }

    public static void clear() {
        UNTIL.clear();
    }

    private static void setGlow(Entity e, boolean on) {
        byte b = e.getEntityData().get(FLAGS);
        byte nb = on ? (byte) (b | GLOW) : (byte) (b & ~GLOW);
        if (nb != b) e.getEntityData().set(FLAGS, nb);
    }

    /** Antes de desenhar as entidades, a cada quadro: o servidor pode reenviar as bandeiras e apagar o contorno. */
    @SubscribeEvent
    public static void frame(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || UNTIL.isEmpty()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = UNTIL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> en = it.next();
            boolean expired = now >= en.getValue();
            Entity ent = level.getEntity(en.getKey());
            if (ent != null) setGlow(ent, !expired);
            if (expired) it.remove();
        }
    }
}
