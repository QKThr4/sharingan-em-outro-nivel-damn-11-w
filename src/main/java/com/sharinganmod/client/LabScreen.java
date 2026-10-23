package com.sharinganmod.client;

import com.sharinganmod.data.Tx;
import com.sharinganmod.logic.LabBlockEntity;
import com.sharinganmod.logic.LabMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Tela do Laboratorio de Analise (desenhada por codigo, sem textura de fundo). */
public class LabScreen extends AbstractContainerScreen<LabMenu> {
    public LabScreen(LabMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
        this.inventoryLabelY = 92;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Tx.t("lab.start"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
        }).bounds(this.leftPos + 44, this.topPos + 68, 88, 18).build());
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.render(gg, mouseX, mouseY, partialTick);
        this.renderTooltip(gg, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // painel claro de laboratorio com borda
        gg.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF2B2F36);
        gg.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFFE6EAEE);
        gg.fill(x + 2, y + 2, x + this.imageWidth - 2, y + 4, 0xFF3FB6D9);
        for (Slot s : this.menu.slots) {
            int sx = x + s.x;
            int sy = y + s.y;
            gg.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8A949E);
            gg.fill(sx, sy, sx + 16, sy + 16, 0xFFC4CBD2);
        }
        gg.drawCenteredString(this.font, Tx.t("lab.sample"), x + 34, y + 33, 0x2B2F36);
        gg.drawCenteredString(this.font, Tx.t("lab.request"), x + 88, y + 33, 0x2B2F36);
        gg.drawCenteredString(this.font, Tx.t("lab.output"), x + 142, y + 33, 0x2B2F36);

        int state = this.menu.state();
        int secs = (int) Math.ceil(this.menu.timerTicks() / 20.0);
        Component status;
        int col = 0x2B2F36;
        switch (state) {
            case LabBlockEntity.ANALYZING -> {
                status = Tx.t("lab.state.analyzing", secs);
                col = 0x1C7C99;
            }
            case LabBlockEntity.WAITING -> {
                ItemStack want = new ItemStack(LabBlockEntity.askable(this.menu.requestIndex()));
                status = Tx.t("lab.state.waiting", want.getHoverName(), this.menu.steps() + 1, LabBlockEntity.ASKS);
                col = 0xB26A00;
                // mostra o item pedido ao lado do slot de entrega
                gg.renderItem(want, x + 100, y + 44);
            }
            case LabBlockEntity.FINAL -> {
                status = Tx.t("lab.state.final", secs);
                col = 0x1C7C99;
            }
            default -> status = Tx.t("lab.state.idle");
        }
        gg.drawCenteredString(this.font, status, x + this.imageWidth / 2, y + 20, col);
    }
}
