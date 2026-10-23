package com.sharinganmod.logic;

import com.sharinganmod.data.Tx;
import com.sharinganmod.data.Skills;
import com.sharinganmod.registry.ModRegistries;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Sharingan retirado dos olhos (1, 2 ou 3 tomoe), com o XP que o dono tinha. Clique direito para equipar.
 * Pode conter os dois olhos ou so um (esquerdo/direito).
 */
public class SharinganItem extends Item {
    public final int tier;

    public SharinganItem(Properties props, int tier) {
        super(props);
        this.tier = tier;
    }

    /** Cria o item guardando o progresso de XP do Sharingan retirado e qual olho ele contem (0 = os dois). */
    public static ItemStack make(int tier, int xp, int side) {
        ItemStack s = new ItemStack(ModRegistries.sharinganItem(tier));
        CompoundTag t = new CompoundTag();
        t.putInt("xp", xp);
        t.putInt("side", side);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return s;
    }

    private static int storedXp(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0 : cd.copyTag().getInt("xp");
    }

    @Override
    public Component getName(ItemStack stack) {
        return Tx.t("item.name", Tx.t("name.tier", tier), Skills.sideName(EyeItem.side(stack)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.fail(stack);
        ItemStack old = SharinganLogic.equipTier(sp, tier, storedXp(stack), EyeItem.side(stack));
        return SharinganLogic.finishUse(sp, stack, old);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int side = EyeItem.side(stack);
        tip.add(Tx.t(ChatFormatting.DARK_RED, "tip.tier.contains", Skills.sideName(side)));
        tip.add(Tx.t(ChatFormatting.GRAY, "tip.tier.equip"));
        tip.add(Tx.t(ChatFormatting.GRAY, "tip.tier.replace"));
        if (side != 0) {
            tip.add(Tx.t(ChatFormatting.GRAY, "tip.tier.join"));
        }
        if (tier < 3) {
            tip.add(Tx.t(ChatFormatting.DARK_RED, "tip.xp", storedXp(stack)));
        }
        tip.add(Tx.t(ChatFormatting.DARK_RED, "implant.noclan"));
    }
}
