package com.sharinganmod.logic;

import com.sharinganmod.SharinganMod;
import com.sharinganmod.data.SharinganData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Conquistas do mod (arquivos em data/sharingan/advancement; sao concedidas pelo codigo). */
public final class Adv {
    private Adv() {}

    public static void grant(ServerPlayer p, String id) {
        if (p.getServer() == null) return;
        AdvancementHolder h = p.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, id));
        if (h != null) p.getAdvancements().award(h, "done");
    }

    /** Confere o estado do jogador e concede as conquistas correspondentes (chamado a cada segundo). */
    public static void check(ServerPlayer p, SharinganData d) {
        grant(p, "root");
        if (d.uchiha) grant(p, "uchiha");
        if (d.stage >= 1) grant(p, "awaken_1");
        if (d.stage >= 2) grant(p, "tomoe_2");
        if (d.stage >= 3) grant(p, "tomoe_3");
        if (d.stage >= 4) grant(p, "mangekyou");
        if (d.stage >= 5) grant(p, "eternal");
        if (d.blind) grant(p, "blind");
        if (d.stage == 4 && d.type >= 1 && d.type <= 4) d.seenTypes |= 1 << d.type;
        if (d.stage >= 5) d.seenTypes |= 1;
        if ((d.seenTypes & 0b11110) == 0b11110) grant(p, "all_eyes");
    }
}
