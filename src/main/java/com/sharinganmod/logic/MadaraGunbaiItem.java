package com.sharinganmod.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Gunbai (leque de guerra) do Madara: 13 de dano total, velocidade de ataque 1,0 e durabilidade 1100.
 * Reparavel com diamante.
 */
public class MadaraGunbaiItem extends SwordItem {
    public static final int DURABILITY = 1100;
    /** Modificador de velocidade: base 4.0 + (-3.0) = 1.0. */
    public static final float SPEED = -3.0f;
    /** Bonus informado a createAttributes: 9 + 3 (bonus do tier diamante) + 1 (base do jogador) = 13. */
    public static final int DAMAGE_PARAM = 9;

    public MadaraGunbaiItem(Item.Properties props) {
        super(Tiers.DIAMOND, props
                .durability(DURABILITY)
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, DAMAGE_PARAM, SPEED)));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(Items.DIAMOND);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.translatable("item.sharingan.gunbai_madara.desc").withStyle(ChatFormatting.GRAY));
    }
}
