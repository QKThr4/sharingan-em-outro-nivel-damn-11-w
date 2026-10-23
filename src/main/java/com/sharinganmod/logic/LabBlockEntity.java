package com.sharinganmod.logic;

import com.sharinganmod.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Laboratorio de Analise: coloca-se a Amostra de Hashirama e inicia. Analise de 30 s, depois pede 5 itens
 * aleatorios (um de cada vez) e termina com outra analise de 30 s, entregando a Celula de Hashirama.
 */
public class LabBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SAMPLE = 0;
    public static final int REQUEST = 1;
    public static final int OUTPUT = 2;

    public static final int IDLE = 0;
    public static final int ANALYZING = 1;
    public static final int WAITING = 2;
    public static final int FINAL = 3;

    /** Duracao de cada analise (30 segundos). */
    public static final int ANALYZE_TICKS = 600;
    /** Quantos itens o laboratorio pede no meio do processo. */
    public static final int ASKS = 5;
    /** Itens que ele pode pedir. */
    private static final Item[] ASKABLE = {
            Items.COAL, Items.DIAMOND, Items.REDSTONE, Items.EMERALD,
            Items.AMETHYST_SHARD, Items.BLAZE_POWDER, Items.NETHER_WART, Items.BONE
    };

    public static Item askable(int index) {
        return ASKABLE[Math.floorMod(index, ASKABLE.length)];
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int state;
    private int timer;
    private int steps;
    private int request;

    /** Dados sincronizados com a tela: estado, tempo restante, itens ja entregues e indice do item pedido. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> state;
                case 1 -> timer;
                case 2 -> steps;
                default -> request;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> state = v;
                case 1 -> timer = v;
                case 2 -> steps = v;
                default -> request = v;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public LabBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistries.LAB_BE.get(), pos, blockState);
    }

    /** Botao "Iniciar": consome a amostra e comeca a primeira analise. */
    public boolean start() {
        ItemStack sample = items.get(SAMPLE);
        if (state != IDLE || sample.isEmpty() || !sample.is(ModRegistries.HASHIRAMA_SAMPLE.get())
                || !items.get(OUTPUT).isEmpty()) {
            return false;
        }
        sample.shrink(1);
        state = ANALYZING;
        timer = ANALYZE_TICKS;
        steps = 0;
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState bs, LabBlockEntity be) {
        be.tick(level);
    }

    private void tick(Level lvl) {
        switch (state) {
            case ANALYZING -> {
                timer--;
                if (timer <= 0) {
                    state = WAITING;
                    request = lvl.random.nextInt(ASKABLE.length);
                    lvl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.2f);
                    setChanged();
                }
            }
            case WAITING -> {
                ItemStack in = items.get(REQUEST);
                if (!in.isEmpty() && in.is(askable(request))) {
                    in.shrink(1);
                    steps++;
                    lvl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.6f);
                    if (steps >= ASKS) {
                        state = FINAL;
                        timer = ANALYZE_TICKS;
                    } else {
                        request = lvl.random.nextInt(ASKABLE.length);
                    }
                    setChanged();
                }
            }
            case FINAL -> {
                timer--;
                if (timer <= 0) {
                    items.set(OUTPUT, new ItemStack(ModRegistries.HASHIRAMA_CELL.get()));
                    state = IDLE;
                    steps = 0;
                    lvl.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1.0f);
                    setChanged();
                }
            }
            default -> { }
        }
    }

    // ---------- Container ----------
    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack r = ContainerHelper.removeItem(items, slot, amount);
        if (!r.isEmpty()) setChanged();
        return r;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ---------- MenuProvider ----------
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.sharingan.laboratorio_analise");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new com.sharinganmod.logic.LabMenu(id, inv, this, data);
    }

    // ---------- salvar ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("state", state);
        tag.putInt("timer", timer);
        tag.putInt("steps", steps);
        tag.putInt("request", request);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        state = tag.getInt("state");
        timer = tag.getInt("timer");
        steps = tag.getInt("steps");
        request = tag.getInt("request");
    }
}
