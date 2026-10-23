package com.sharinganmod.logic;

import com.sharinganmod.data.Tx;
import com.sharinganmod.data.Skills;
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
 * Olho de Mangekyou (ja inclui o Sharingan de 3 tomoe). kind: 1 Itachi, 2 Sasuke, 3 Obito, 4 Shisui,
 * 5 Mangekyou Eterno (Itachi + Sasuke), 6 Madara, 7 Izuna, 8 Mangekyou Eterno de Madara (Madara + Izuna). Clique direito para implantar; substitui o Sharingan que o jogador tiver.
 * O item pode conter os dois olhos (padrao) ou so um (olho esquerdo/direito, depois de partido).
 */
public class EyeItem extends Item {
    public final int kind;

    public EyeItem(Properties props, int kind) {
        super(props);
        this.kind = kind;
    }

    /** Qual olho o item contem: 0 os dois, 1 esquerdo, 2 direito. */
    public static int side(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0 : cd.copyTag().getInt("side");
    }

    public static void setSide(ItemStack stack, int side) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = cd == null ? new CompoundTag() : cd.copyTag();
        t.putInt("side", side);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = (kind == 5 || kind == 8) ? Skills.eternalName(kind) : Tx.t("name.mk", Skills.typeName(kind));
        return Tx.t("item.name", base, Skills.sideName(side(stack)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.fail(stack);
        ItemStack old = SharinganLogic.transplant(sp, kind, stack);
        return SharinganLogic.finishUse(sp, stack, old);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int side = side(stack);
        tip.add(Tx.t(kind == 3 ? ChatFormatting.DARK_PURPLE : ChatFormatting.DARK_RED, "tip.contains", Skills.sideName(side)));
        if (side != 0) {
            tip.add(Tx.t(ChatFormatting.DARK_PURPLE, "tip.half.join"));
            tip.add(Tx.t(ChatFormatting.DARK_PURPLE, "tip.half.nosus"));
        } else if (kind == 1 || kind == 2) {
            tip.add(Tx.t(ChatFormatting.DARK_RED, "tip.eternal.join", Skills.typeName(kind == 1 ? 2 : 1)));
        } else if (kind == 6 || kind == 7) {
            tip.add(Tx.t(ChatFormatting.DARK_RED, "tip.eternal.join", Skills.typeName(kind == 6 ? 7 : 6)));
        }
        tip.add(Tx.t(ChatFormatting.GRAY, "tip.implant1"));
        tip.add(Tx.t(ChatFormatting.GRAY, "tip.implant2"));
    }
}
