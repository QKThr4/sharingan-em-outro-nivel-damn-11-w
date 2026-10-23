package com.sharinganmod.logic;

import com.sharinganmod.SharinganMod;
import com.sharinganmod.registry.ModRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/** Receitas da bancada de pocoes: Pocao Estranha + Fragmento de ametista = Chakra; + Po de glowstone = Chakra II. */
@EventBusSubscriber(modid = SharinganMod.MODID)
public final class ModBrewing {
    private ModBrewing() {}

    @SubscribeEvent
    public static void recipes(RegisterBrewingRecipesEvent e) {
        e.getBuilder().addMix(Potions.AWKWARD, Items.AMETHYST_SHARD, ModRegistries.POTION_CHAKRA);
        e.getBuilder().addMix(ModRegistries.POTION_CHAKRA, Items.GLOWSTONE_DUST, ModRegistries.POTION_CHAKRA_STRONG);
    }
}
