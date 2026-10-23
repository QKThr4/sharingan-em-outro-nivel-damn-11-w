package com.sharinganmod.client;

import com.sharinganmod.data.Tx;
import com.sharinganmod.net.SusanooColorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.neoforged.neoforge.network.PacketDistributor;

/** Escolha da cor do Susanoo: cores prontas e controles de vermelho, verde e azul. */
public class SusanooColorScreen extends Screen {
    private static final int[] PRESETS = {0x7366FF, 0xFF3030, 0x30FF60, 0xFFD030, 0xB040FF, 0xFFFFFF, 0x30E0FF, 0xFF7A20};
    private static final int DEFAULT_COLOR = 0x7366FF;

    private final Screen parent;
    private int r;
    private int g;
    private int b;

    public SusanooColorScreen(Screen parent) {
        super(Tx.t("susanoo.title"));
        this.parent = parent;
        int c = ClientData.DATA.susanooColor;
        this.r = (c >> 16) & 0xFF;
        this.g = (c >> 8) & 0xFF;
        this.b = c & 0xFF;
    }

    private int rgb() {
        return (r << 16) | (g << 8) | b;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 62;
        for (int i = 0; i < PRESETS.length; i++) {
            final int color = PRESETS[i];
            Component swatch = Component.literal("■").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
            addRenderableWidget(Button.builder(swatch, btn -> {
                r = (color >> 16) & 0xFF;
                g = (color >> 8) & 0xFF;
                b = color & 0xFF;
                rebuildWidgets();
            }).bounds(cx - 100 + i * 26, top, 24, 20).build());
        }
        addRenderableWidget(new Channel(cx - 100, top + 28, 0));
        addRenderableWidget(new Channel(cx - 100, top + 52, 1));
        addRenderableWidget(new Channel(cx - 100, top + 76, 2));
        addRenderableWidget(Button.builder(Tx.t("susanoo.reset"), btn -> {
            r = (DEFAULT_COLOR >> 16) & 0xFF;
            g = (DEFAULT_COLOR >> 8) & 0xFF;
            b = DEFAULT_COLOR & 0xFF;
            rebuildWidgets();
        }).bounds(cx - 100, top + 104, 98, 20).build());
        addRenderableWidget(Button.builder(Tx.t("susanoo.save"), btn -> {
            PacketDistributor.sendToServer(new SusanooColorPayload(rgb()));
            onClose();
        }).bounds(cx + 2, top + 104, 98, 20).build());
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.render(gg, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int top = this.height / 2 - 62;
        gg.drawCenteredString(this.font, this.title, cx, top - 22, 0xFFFFFF);
        int prev = 0xFF000000 | rgb();
        gg.fill(cx + 108, top + 28, cx + 148, top + 98, 0xFFFFFFFF);
        gg.fill(cx + 109, top + 29, cx + 147, top + 97, prev);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Controle de um canal de cor (0 vermelho, 1 verde, 2 azul). */
    private final class Channel extends AbstractSliderButton {
        private final int channel;

        Channel(int x, int y, int channel) {
            super(x, y, 200, 20, Component.empty(), (channel == 0 ? r : channel == 1 ? g : b) / 255.0);
            this.channel = channel;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int v = (int) Math.round(this.value * 255.0);
            setMessage(Tx.t(channel == 0 ? "susanoo.r" : channel == 1 ? "susanoo.g" : "susanoo.b", v));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(this.value * 255.0);
            if (channel == 0) r = v;
            else if (channel == 1) g = v;
            else b = v;
        }
    }
}
