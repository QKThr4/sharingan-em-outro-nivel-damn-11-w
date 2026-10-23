package com.sharinganmod.client;

import com.sharinganmod.SharinganMod;
import com.sharinganmod.data.SharinganData;
import com.sharinganmod.data.Tx;
import com.sharinganmod.net.ActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/** Aba de upgrades de chakra: arvore com o 3 tomoe no meio e 5 upgrades em sequencia, pagos com niveis de XP. */
public class ChakraScreen extends Screen {
    private static final int NODE = 30;
    private static final int ROOT = 38;
    private static final int RADIUS = 58;
    private static final ResourceLocation EYE3 =
            ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "textures/gui/eye_3.png");

    public ChakraScreen() {
        super(Tx.t("chakra.title"));
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

    /** Centro do no k (0 = 3 tomoe no meio, 1 a 5 = upgrades em volta, em sequencia no sentido horario). */
    private int[] pos(int k) {
        int cx = this.width / 2;
        int cy = this.height / 2 - 10;
        if (k == 0) return new int[] {cx, cy};
        double a = Math.toRadians(-90 + (k - 1) * 72);
        return new int[] {cx + (int) Math.round(Math.cos(a) * RADIUS), cy + (int) Math.round(Math.sin(a) * RADIUS)};
    }

    private int nodeAt(double mx, double my) {
        for (int k = 0; k <= SharinganData.MAX_UPGRADES; k++) {
            int[] p = pos(k);
            int half = (k == 0 ? ROOT : NODE) / 2;
            if (mx >= p[0] - half && mx <= p[0] + half && my >= p[1] - half && my <= p[1] + half) return k;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            SharinganData d = ClientData.DATA;
            int k = nodeAt(mouseX, mouseY);
            if (k >= 1 && d.stage >= 3 && k == d.chakraUp + 1) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                PacketDistributor.sendToServer(new ActionPayload(20));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void line(GuiGraphics gg, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            gg.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    private static float chakraAt(int k) {
        return SharinganData.POOL[3] + SharinganData.UPGRADE_STEP * k;
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gg, mouseX, mouseY, partialTick);
        SharinganData d = ClientData.DATA;
        int cx = this.width / 2;
        int cy = this.height / 2;
        boolean unlocked = d.stage >= 3;

        gg.fill(cx - 140, cy - 130, cx + 140, cy + 144, 0xC0101010);
        gg.fill(cx - 140, cy - 130, cx + 140, cy - 128, 0xFF2A5FCC);
        gg.drawCenteredString(this.font, Tx.t("chakra.title"), cx, cy - 124, 0xFFFFFF);
        gg.drawCenteredString(this.font, Tx.t("chakra.max", (int) d.maxChakra(), d.chakraUp, SharinganData.MAX_UPGRADES),
                cx, cy - 113, 0x66AAFF);
        gg.drawCenteredString(this.font, Tx.t("chakra.points", d.pointsAvailable(), d.pointsEarned,
                SharinganData.MAX_UPGRADES, SharinganData.POINT_XP), cx, cy - 102, d.pointsAvailable() > 0 ? 0xFFDD55 : 0x88CCFF);

        // linhas da arvore: 3 tomoe -> 1 -> 2 -> 3 -> 4 -> 5
        for (int k = 1; k <= SharinganData.MAX_UPGRADES; k++) {
            int[] a = pos(k - 1);
            int[] b = pos(k);
            line(gg, a[0], a[1], b[0], b[1], unlocked && k <= d.chakraUp ? 0xFF66DD66 : 0xFF555555);
        }

        int hover = nodeAt(mouseX, mouseY);
        int next = d.chakraUp + 1;
        for (int k = 0; k <= SharinganData.MAX_UPGRADES; k++) {
            int[] p = pos(k);
            int size = k == 0 ? ROOT : NODE;
            int x0 = p[0] - size / 2;
            int y0 = p[1] - size / 2;
            int fill;
            int border;
            if (k == 0) {
                fill = unlocked ? 0xFF7A1A1A : 0xFF2A2A2A;
                border = unlocked ? 0xFFFF5555 : 0xFF666666;
            } else if (unlocked && k <= d.chakraUp) {
                fill = 0xFF2E7D32;
                border = 0xFF66DD66;
            } else if (unlocked && k == next) {
                boolean afford = Minecraft.getInstance().player != null
                        && Minecraft.getInstance().player.experienceLevel >= d.nextUpgradeCost()
                        && d.pointsAvailable() > 0;
                fill = afford ? 0xFF7A6414 : 0xFF3A3410;
                border = 0xFFFFCC33;
            } else {
                fill = 0xFF2A2A2A;
                border = 0xFF666666;
            }
            if (k == hover) border = 0xFFFFFFFF;
            gg.fill(x0 - 2, y0 - 2, x0 + size + 2, y0 + size + 2, border);
            gg.fill(x0, y0, x0 + size, y0 + size, fill);
            if (k == 0) {
                if (!unlocked) gg.setColor(0.3f, 0.3f, 0.3f, 1f);
                gg.blit(EYE3, x0 + 3, y0 + 3, size - 6, size - 6, 0, 0, 128, 128, 128, 128);
                gg.setColor(1f, 1f, 1f, 1f);
            } else {
                gg.drawCenteredString(this.font, String.valueOf(k), p[0], p[1] - 4, 0xFFFFFF);
            }
            // chakra maximo que esse no entrega
            gg.drawCenteredString(this.font, String.valueOf((int) chakraAt(k)), p[0], y0 + size + 4, 0xFF88BBFF);
        }

        // detalhes do no sob o mouse (ou do proximo upgrade)
        int target = hover >= 0 ? hover : Math.min(SharinganData.MAX_UPGRADES, next);
        int y = cy + 72;
        if (target == 0) {
            gg.drawCenteredString(this.font, Tx.t("chakra.node.root", (int) chakraAt(0)), cx, y, 0xFFFFFF);
        } else {
            gg.drawCenteredString(this.font, Tx.t("chakra.node.title", target, (int) chakraAt(target)), cx, y, 0xFFFFFF);
            y += 11;
            Component state;
            int col;
            if (!unlocked) {
                state = Tx.t("chakra.node.need3");
                col = 0xFF5555;
            } else if (target <= d.chakraUp) {
                state = Tx.t("chakra.node.owned");
                col = 0x66DD66;
            } else if (target == next) {
                int have = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.experienceLevel;
                int cost = SharinganData.UPGRADE_LEVELS[target - 1];
                state = d.pointsAvailable() > 0 ? Tx.t("chakra.node.cost", cost, have) : Tx.t("chakra.node.nopoint");
                col = d.pointsAvailable() > 0 && have >= cost ? 0xFFDD55 : 0xFF8844;
            } else {
                state = Tx.t("chakra.node.prev", target - 1);
                col = 0xAAAAAA;
            }
            gg.drawCenteredString(this.font, state, cx, y, col);
            if (unlocked && target == next) {
                y += 11;
                gg.drawCenteredString(this.font, Tx.t("chakra.node.click"), cx, y, 0xBBBBBB);
            }
        }
        if (unlocked && d.chakraUp >= SharinganData.MAX_UPGRADES) {
            gg.drawCenteredString(this.font, Tx.t("chakra.allowned"), cx, cy + 104, 0xFFAA33);
        }
    }
}
