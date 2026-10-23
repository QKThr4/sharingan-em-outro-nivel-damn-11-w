package com.sharinganmod.logic;

import com.sharinganmod.data.SharinganData;
import com.sharinganmod.net.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Efeito instantaneo da Pocao de Chakra: recupera uma porcentagem do chakra maximo (30% nivel I, 60% nivel II). */
public class ChakraEffect extends InstantenousMobEffect {
    public ChakraEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x3A7BFF);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer sp) {
            SharinganData d = SharinganData.get(sp);
            float max = d.maxChakra();
            if (max > 0f) {
                float pct = amplifier >= 1 ? 0.60f : 0.30f;
                d.chakra = Math.min(max, d.chakra + max * pct);
                Net.sync(sp);
            }
        }
        return true;
    }
}
