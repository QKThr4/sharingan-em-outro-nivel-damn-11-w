package com.sharinganmod.logic;

import com.sharinganmod.data.Tx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Celula de Hashirama: implante PERMANENTE (so sai por comando). Clique direito avisa; o segundo clique implanta. */
public class HashiramaCellItem extends Item {
    public HashiramaCellItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.fail(stack);
        if (SharinganLogic.tryImplantHashirama(sp)) {
            stack.shrink(1);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Tx.t(ChatFormatting.GREEN, "hash.tip1"));
        tip.add(Tx.t(ChatFormatting.GRAY, "hash.tip2"));
        tip.add(Tx.t(ChatFormatting.RED, "hash.tip3"));
    }
}
