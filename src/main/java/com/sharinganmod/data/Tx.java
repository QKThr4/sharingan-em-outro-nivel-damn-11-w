package com.sharinganmod.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Textos traduzíveis do mod: o jogo mostra em português (pt_br/pt_pt) ou em inglês (os demais idiomas). */
public final class Tx {
    private Tx() {}

    public static MutableComponent t(String key, Object... args) {
        return Component.translatable("sharingan." + key, args);
    }

    public static MutableComponent t(ChatFormatting color, String key, Object... args) {
        return Component.translatable("sharingan." + key, args).withStyle(color);
    }
}
