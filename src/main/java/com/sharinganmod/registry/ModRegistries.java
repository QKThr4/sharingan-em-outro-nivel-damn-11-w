package com.sharinganmod.registry;

import com.sharinganmod.SharinganMod;
import com.sharinganmod.data.SharinganData;
import com.sharinganmod.logic.AmaterasuEffect;
import com.sharinganmod.logic.ChakraEffect;
import com.sharinganmod.logic.EyeItem;
import com.sharinganmod.logic.HashiramaCellItem;
import com.sharinganmod.logic.LabBlock;
import com.sharinganmod.logic.LabBlockEntity;
import com.sharinganmod.logic.LabMenu;
import com.sharinganmod.logic.MadaraGunbaiItem;
import com.sharinganmod.logic.SasukeSwordItem;
import com.sharinganmod.logic.SharinganItem;
import com.sharinganmod.logic.UchihaEmblemItem;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SharinganMod.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, SharinganMod.MODID);
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(BuiltInRegistries.POTION, SharinganMod.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SharinganMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SharinganMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SharinganMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SharinganMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SharinganMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SharinganMod.MODID);

    public static final Supplier<AttachmentType<SharinganData>> SHARINGAN =
            ATTACHMENTS.register("sharingan",
                    () -> AttachmentType.serializable(() -> new SharinganData()).copyOnDeath().build());

    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, name)));
    }

    public static final Supplier<SoundEvent> SND_ON = sound("sharingan_on");
    public static final Supplier<SoundEvent> SND_OFF = sound("sharingan_off");
    public static final Supplier<SoundEvent> SND_MK_SASUKE = sound("mk_sasuke");
    public static final Supplier<SoundEvent> SND_MK_OBITO_LEFT = sound("mk_obito_left");
    public static final Supplier<SoundEvent> SND_MK_OBITO_RIGHT = sound("mk_obito_right");
    public static final Supplier<SoundEvent> SND_MK_MADARA = sound("mk_madara");
    public static final Supplier<SoundEvent> SND_MK_IZUNA = sound("mk_izuna");
    public static final Supplier<SoundEvent> SND_MK_BOTH = sound("mk_itachi_obito_both");
    public static final Supplier<SoundEvent> SND_ETERNAL_FIRST = sound("eternal_first");

    public static final DeferredHolder<MobEffect, AmaterasuEffect> AMATERASU =
            EFFECTS.register("amaterasu", () -> new AmaterasuEffect());

    public static final DeferredHolder<MobEffect, ChakraEffect> CHAKRA_RESTORE =
            EFFECTS.register("chakra_restore", () -> new ChakraEffect());
    /** Pocao de Chakra (30% do chakra maximo) e a versao forte com glowstone (60%). */
    public static final DeferredHolder<Potion, Potion> POTION_CHAKRA = POTIONS.register("chakra",
            () -> new Potion("chakra", new MobEffectInstance(CHAKRA_RESTORE, 1, 0)));
    public static final DeferredHolder<Potion, Potion> POTION_CHAKRA_STRONG = POTIONS.register("strong_chakra",
            () -> new Potion("chakra", new MobEffectInstance(CHAKRA_RESTORE, 1, 1)));

    public static final DeferredItem<EyeItem> EYE_ITACHI = ITEMS.register("eye_itachi",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 1));
    public static final DeferredItem<EyeItem> EYE_SASUKE = ITEMS.register("eye_sasuke",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 2));
    public static final DeferredItem<EyeItem> EYE_OBITO = ITEMS.register("eye_obito",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 3));
    public static final DeferredItem<EyeItem> EYE_SHISUI = ITEMS.register("eye_shisui",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 4));

    public static final DeferredItem<EyeItem> EYE_MADARA = ITEMS.register("eye_madara",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 6));
    public static final DeferredItem<EyeItem> EYE_IZUNA = ITEMS.register("eye_izuna",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 7));
    public static final DeferredItem<EyeItem> EYE_ETERNO_MADARA = ITEMS.register("eye_eterno_madara",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 8));

    public static final DeferredItem<EyeItem> EYE_ETERNO = ITEMS.register("eye_eterno",
            () -> new EyeItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 5));

    public static final DeferredItem<UchihaEmblemItem> UCHIHA_EMBLEM = ITEMS.register("uchiha_emblem",
            () -> new UchihaEmblemItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<SharinganItem> SHARINGAN_1 = ITEMS.register("sharingan_1",
            () -> new SharinganItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), 1));
    public static final DeferredItem<SharinganItem> SHARINGAN_2 = ITEMS.register("sharingan_2",
            () -> new SharinganItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), 2));
    public static final DeferredItem<SharinganItem> SHARINGAN_3 = ITEMS.register("sharingan_3",
            () -> new SharinganItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 3));

    public static final DeferredItem<SasukeSwordItem> ESPADA_SASUKE = ITEMS.register("espada_sasuke",
            () -> new SasukeSwordItem(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<MadaraGunbaiItem> GUNBAI_MADARA = ITEMS.register("gunbai_madara",
            () -> new MadaraGunbaiItem(new Item.Properties().rarity(Rarity.EPIC)));

    // ---- Celulas de Hashirama e Laboratorio de Analise ----
    public static final DeferredItem<Item> HASHIRAMA_SAMPLE = ITEMS.register("amostra_hashirama",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<HashiramaCellItem> HASHIRAMA_CELL = ITEMS.register("celula_hashirama",
            () -> new HashiramaCellItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredBlock<LabBlock> LAB_BLOCK = BLOCKS.register("laboratorio_analise",
            () -> new LabBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> LAB_ITEM = ITEMS.register("laboratorio_analise",
            () -> new BlockItem(LAB_BLOCK.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<LabBlockEntity>> LAB_BE = BLOCK_ENTITIES.register("laboratorio_analise",
            () -> BlockEntityType.Builder.of(LabBlockEntity::new, LAB_BLOCK.get()).build(null));
    public static final Supplier<MenuType<LabMenu>> LAB_MENU = MENUS.register("laboratorio_analise",
            () -> new MenuType<>(LabMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sharingan"))
                    .icon(() -> new ItemStack(EYE_ITACHI.get()))
                    .displayItems((params, out) -> {
                        out.accept(UCHIHA_EMBLEM.get());
                        out.accept(LAB_ITEM.get());
                        out.accept(ESPADA_SASUKE.get());
                        out.accept(GUNBAI_MADARA.get());
                        out.accept(HASHIRAMA_SAMPLE.get());
                        out.accept(HASHIRAMA_CELL.get());
                        out.accept(SHARINGAN_1.get());
                        out.accept(SHARINGAN_2.get());
                        out.accept(SHARINGAN_3.get());
                        out.accept(EYE_ITACHI.get());
                        out.accept(EYE_SASUKE.get());
                        out.accept(EYE_OBITO.get());
                        out.accept(EYE_SHISUI.get());
                        out.accept(EYE_ETERNO.get());
                        out.accept(EYE_MADARA.get());
                        out.accept(EYE_IZUNA.get());
                        out.accept(EYE_ETERNO_MADARA.get());
                        out.accept(PotionContents.createItemStack(Items.POTION, POTION_CHAKRA));
                        out.accept(PotionContents.createItemStack(Items.POTION, POTION_CHAKRA_STRONG));
                    })
                    .build());

    public static Item sharinganItem(int tier) {
        return switch (tier) {
            case 1 -> SHARINGAN_1.get();
            case 2 -> SHARINGAN_2.get();
            default -> SHARINGAN_3.get();
        };
    }

    public static Item eyeFor(int type) {
        return switch (type) {
            case 1 -> EYE_ITACHI.get();
            case 2 -> EYE_SASUKE.get();
            case 3 -> EYE_OBITO.get();
            case 5 -> EYE_ETERNO.get();
            case 6 -> EYE_MADARA.get();
            case 7 -> EYE_IZUNA.get();
            case 8 -> EYE_ETERNO_MADARA.get();
            default -> EYE_SHISUI.get();
        };
    }
}
