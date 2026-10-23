package com.sharinganmod.client;

import com.sharinganmod.data.SharinganData;
import com.sharinganmod.data.Tx;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Aba das Celulas de Hashirama: situacao, efeitos e aviso de que o implante e permanente. */
public class HashiramaScreen extends Screen {
    public HashiramaScreen() {
        super(Tx.t("hash.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        Button back = addRenderableWidget(Button.builder(Component.literal("<").withStyle(ChatFormatting.RED),
                        b -> Minecraft.getInstance().setScreen(new SharinganScreen()))
                .bounds(cx + 140 - 24, cy - 127, 20, 20).build());
        back.setTooltip(Tooltip.create(Tx.t("tab.eye")));
        addRenderableWidget(Button.builder(Tx.t("btn.close"), b -> onClose())
                .bounds(cx - 110, cy + 118, 220, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.SCREEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gg, mouseX, mouseY, partialTick);
        SharinganData d = ClientData.DATA;
        int cx = this.width / 2;
        int cy = this.height / 2;
        gg.fill(cx - 140, cy - 130, cx + 140, cy + 144, 0xC0101010);
        gg.fill(cx - 140, cy - 130, cx + 140, cy - 128, 0xFF2E9E4F);
        gg.drawCenteredString(this.font, Tx.t("hash.title"), cx, cy - 124, 0xFFFFFF);

        gg.drawCenteredString(this.font, Tx.t(d.hashirama ? "hash.status.yes" : "hash.status.no"), cx, cy - 100,
                d.hashirama ? 0x66DD66 : 0xDD6666);

        int y = cy - 80;
        int col = d.hashirama ? 0xFFFFFF : 0x888888;
        for (String key : new String[] {"hash.eff.hearts", "hash.eff.haste", "hash.eff.chakra", "hash.eff.strain"}) {
            gg.drawCenteredString(this.font, Tx.t(key), cx, y, col);
            y += 14;
        }
        y += 10;
        gg.drawCenteredString(this.font, Tx.t("hash.permanent.1"), cx, y, 0xFFAA33);
        y += 11;
        gg.drawCenteredString(this.font, Tx.t("hash.permanent.2"), cx, y, 0xFFAA33);
        y += 20;
        if (!d.hashirama) {
            gg.drawCenteredString(this.font, Tx.t("hash.how.1"), cx, y, 0xAAAAAA);
            y += 11;
            gg.drawCenteredString(this.font, Tx.t("hash.how.2"), cx, y, 0xAAAAAA);
        }
    }
}
