package com.sharinganmod.logic;

import com.sharinganmod.registry.ModRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu do Laboratorio de Analise: amostra, item pedido, resultado e o inventario do jogador. */
public class LabMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    /** Lado do cliente. */
    public LabMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(3), new SimpleContainerData(4));
    }

    public LabMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(ModRegistries.LAB_MENU.get(), id);
        checkContainerSize(container, 3);
        checkContainerDataCount(data, 4);
        this.container = container;
        this.data = data;
        this.addSlot(new Slot(container, LabBlockEntity.SAMPLE, 26, 44) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModRegistries.HASHIRAMA_SAMPLE.get());
            }
        });
        this.addSlot(new Slot(container, LabBlockEntity.REQUEST, 80, 44));
        this.addSlot(new Slot(container, LabBlockEntity.OUTPUT, 134, 44) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 104 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c, 8 + c * 18, 162));
        }
        this.addDataSlots(data);
    }

    public int state() {
        return data.get(0);
    }

    public int timerTicks() {
        return data.get(1);
    }

    public int steps() {
        return data.get(2);
    }

    public int requestIndex() {
        return data.get(3);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && container instanceof LabBlockEntity lab) {
            lab.start();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack st = slot.getItem();
            result = st.copy();
            if (index < 3) {
                if (!this.moveItemStackTo(st, 3, 39, true)) return ItemStack.EMPTY;
            } else if (st.is(ModRegistries.HASHIRAMA_SAMPLE.get())) {
                if (!this.moveItemStackTo(st, 0, 1, false)) return ItemStack.EMPTY;
            } else if (state() == LabBlockEntity.WAITING && st.is(LabBlockEntity.askable(requestIndex()))) {
                if (!this.moveItemStackTo(st, 1, 2, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (st.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, st);
        }
        return result;
    }
}
