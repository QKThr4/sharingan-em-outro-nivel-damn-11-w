package com.sharinganmod.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Espada do Sasuke: dano 6 (igual a espada de ferro), velocidade de ataque maior (2.0 em vez de 1.6)
 * e durabilidade 500 (ferro 250, diamante 1561). Reparavel com ferro.
 */
public class SasukeSwordItem extends SwordItem {
    public static final int DURABILITY = 500;
    /** Modificador de velocidade: base 4.0 + (-2.0) = 2.0 (a de ferro e 1.6). */
    public static final float SPEED = -2.0f;

    public SasukeSwordItem(Item.Properties props) {
        super(Tiers.IRON, props
                .durability(DURABILITY)
                .attributes(SwordItem.createAttributes(Tiers.IRON, 3, SPEED)));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(Items.IRON_INGOT);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.translatable("item.sharingan.espada_sasuke.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
