package com.sharinganmod.client;

import com.sharinganmod.SharinganMod;
import com.sharinganmod.data.SharinganData;
import com.sharinganmod.data.Skills;
import com.sharinganmod.data.Tx;
import com.sharinganmod.net.ActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Aba especial do Sharingan: olho em uso, clã, chakra e ações. */
public class SharinganScreen extends Screen {
    private Button equip;
    private Button split;

    public SharinganScreen() {
        super(Tx.t("screen.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 52;
        // abas: Sharingan (esta) e Chakra (upgrades)
        // botao quadrado no canto de cima, DENTRO do painel (visivel ate no GUI scale 4)
        Button tabChakra = addRenderableWidget(Button.builder(Component.literal("C").withStyle(ChatFormatting.AQUA),
                        b -> Minecraft.getInstance().setScreen(new ChakraScreen()))
                .bounds(cx + 140 - 24, this.height / 2 - 127, 20, 20).build());
        tabChakra.setTooltip(Tooltip.create(Tx.t("tab.chakra")));
        // ao lado do botao do Chakra: Celulas de Hashirama
        Button tabHashirama = addRenderableWidget(Button.builder(Component.literal("H").withStyle(ChatFormatting.GREEN),
                        b -> Minecraft.getInstance().setScreen(new HashiramaScreen()))
                .bounds(cx + 140 - 48, this.height / 2 - 127, 20, 20).build());
        tabHashirama.setTooltip(Tooltip.create(Tx.t("tab.hashirama")));
        // cor do Susanoo
        Button tabSusanoo = addRenderableWidget(Button.builder(Component.literal("S").withStyle(ChatFormatting.LIGHT_PURPLE),
                        b -> Minecraft.getInstance().setScreen(new SusanooColorScreen(this)))
                .bounds(cx + 140 - 72, this.height / 2 - 127, 20, 20).build());
        tabSusanoo.setTooltip(Tooltip.create(Tx.t("tab.susanoo")));
        equip = addRenderableWidget(Button.builder(Tx.t("btn.remove"), b -> send(8))
                .bounds(cx - 110, y, 220, 20).build());
        split = addRenderableWidget(Button.builder(Tx.t("btn.split.long"), b -> send(7))
                .bounds(cx - 110, y + 22, 220, 20).build());
        addRenderableWidget(Button.builder(Tx.t("btn.layout"),
                        b -> Minecraft.getInstance().setScreen(new LayoutScreen(this)))
                .bounds(cx - 110, y + 44, 220, 20).build());
        addRenderableWidget(Button.builder(Tx.t("btn.close"), b -> onClose())
                .bounds(cx - 110, y + 66, 220, 20).build());
        refreshButtons();
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new ActionPayload(action));
    }

    private void refreshButtons() {
        SharinganData d = ClientData.DATA;
        if (equip == null) return;
        Component what = d.stage >= 5 ? Tx.t("what.eternal") : d.stage == 4 ? Tx.t("what.mk") : Tx.t("name.tier", d.stage);
        Component label = d.stage <= 0 ? Tx.t("stage.none")
                : d.eyeSide != 0 ? Tx.t("btn.remove.side", what, Skills.sideName(d.eyeSide))
                : Tx.t("btn.remove.what", what);
        equip.setMessage(label);
        equip.active = d.stage > 0;
        split.setMessage(Tx.t(d.eyeSide != 0 ? "btn.split.done" : "btn.split.long"));
        split.active = d.stage > 0 && d.eyeSide == 0;
    }

    @Override
    public void tick() {
        refreshButtons();
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

    /** Nome da textura do olho mostrado: Mangekyou so aparece enquanto ele esta sendo usado. */
    private static String iconKey(SharinganData d) {
        if (d.eyesActive() && d.level() >= 4) {
            if (d.stage == 5) return d.type == 8 ? "eterno_madara" : "eterno";
            return switch (d.type) {
                case 1 -> "itachi";
                case 2 -> "sasuke";
                case 3 -> "obito";
                case 6 -> "madara";
                case 7 -> "izuna";
                default -> "shisui";
            };
        }
        return String.valueOf(Math.max(1, Math.min(3, d.stage)));
    }

    private static String time(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gg, mouseX, mouseY, partialTick);
        SharinganData d = ClientData.DATA;
        int cx = this.width / 2;
        int cy = this.height / 2;

        gg.fill(cx - 140, cy - 130, cx + 140, cy + 144, 0xC0101010);
        gg.fill(cx - 140, cy - 130, cx + 140, cy - 128, 0xFFAA1111);
        gg.drawCenteredString(this.font, Tx.t("screen.title"), cx, cy - 124, 0xFFFFFF);

        // slot do olho em uso
        gg.fill(cx - 34, cy - 112, cx + 34, cy - 44, 0xFF555555);
        gg.fill(cx - 32, cy - 110, cx + 32, cy - 46, 0xFF1A1A1A);
        if (d.stage > 0) {
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID,
                    "textures/gui/eye_" + iconKey(d) + ".png");
            // desligado = mais escuro
            if (!d.eyesActive()) gg.setColor(0.3f, 0.3f, 0.3f, 1f);
            gg.blit(tex, cx - 30, cy - 108, 60, 60, 0, 0, 128, 128, 128, 128);
            gg.setColor(1f, 1f, 1f, 1f);
        }

        int y = cy - 40;
        if (d.stage <= 0) {
            gg.drawCenteredString(this.font, Tx.t("stage.none"), cx, y, 0xAAAAAA);
            y += 11;
        } else if (d.eyesActive() && d.level() >= 4) {
            gg.drawCenteredString(this.font, Tx.t("screen.using", Skills.stageName(d)), cx, y, 0xFF5555);
            y += 11;
        } else {
            Component base = Tx.t("stage.tier", Math.min(3, d.stage));
            gg.drawCenteredString(this.font, Tx.t(d.eyesActive() ? "screen.active" : "screen.inactive", base), cx, y,
                    d.eyesActive() ? 0xFF5555 : 0x888888);
            y += 11;
            if (d.stage >= 4) {
                Component mk = d.stage >= 5 ? Skills.eternalName(d.type) : Tx.t("name.mk", Skills.typeName(d.type));
                gg.drawCenteredString(this.font, Tx.t("screen.mk.unlocked", mk), cx, y, 0xDD8888);
                y += 11;
            }
        }
        if (d.stage > 0 && d.eyeSide != 0) {
            gg.drawCenteredString(this.font, Tx.t("screen.only", Skills.sideName(d.eyeSide)), cx, y, 0xBB88FF);
            y += 11;
        }
        if (d.uchiha) {
            gg.drawCenteredString(this.font, Tx.t("screen.clan.yes"), cx, y, 0x66DD66);
        } else {
            gg.drawCenteredString(this.font, Tx.t("screen.clan.no"), cx, y, 0xDD6666);
        }
        y += 11;
        if (d.stage > 0) {
            gg.drawCenteredString(this.font, Tx.t("screen.chakra", (int) d.chakra, (int) d.maxChakra(),
                    time((int) d.fullSeconds())), cx, y, 0x66AAFF);
            y += 11;
            float oneEye = d.eyeSide != 0 ? 0.5f : 1f;
            if (d.stage >= 4) {
                gg.drawCenteredString(this.font, Tx.t("screen.dodge.mk", Math.round(SharinganData.DODGE[3] * 100f * oneEye),
                        Math.round(SharinganData.DODGE[4] * 100f * oneEye)), cx, y, 0x55DDFF);
            } else {
                gg.drawCenteredString(this.font, Tx.t("screen.dodge", Math.round(SharinganData.DODGE[d.stage] * 100f * oneEye)),
                        cx, y, 0x55DDFF);
            }
            y += 11;
        }

        if (d.stage == 0) {
            if (d.awakened) {
                gg.drawCenteredString(this.font, Tx.t("screen.implant.only"), cx, y, 0xAAAAAA);
            } else if (d.uchiha) {
                int chance = (int) (SharinganData.AWAKEN_CHANCE_UCHIHA * 100);
                gg.drawCenteredString(this.font, Tx.t("screen.awaken.chance", chance), cx, y, 0xAAAAAA);
            } else {
                gg.drawCenteredString(this.font, Tx.t("screen.awaken.emblem"), cx, y, 0xAAAAAA);
            }
        } else if (d.stage < 3) {
            gg.drawCenteredString(this.font, Tx.t("screen.xp", d.xp, d.xpNeeded(),
                    Component.keybind("key.sharingan.sharingan")), cx, y, 0xAAAAAA);
        } else if (d.stage == 3) {
            gg.drawCenteredString(this.font, Tx.t("screen.mk.how"), cx, y, 0xAAAAAA);
        } else {
            gg.drawCenteredString(this.font, d.stage == 5 ? Tx.t("screen.nostrain") : Tx.t("screen.strain", (int) d.strain),
                    cx, y, 0xAAAAAA);
        }
        y += 11;
        if (d.blind) {
            gg.drawCenteredString(this.font, Tx.t(d.stage == 5 ? "screen.blind.eternal" : "screen.blind"), cx, y, 0xFF2222);
        }
    }
}
