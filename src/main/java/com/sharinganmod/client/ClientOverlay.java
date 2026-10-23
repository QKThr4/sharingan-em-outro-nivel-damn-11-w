package com.sharinganmod.client;

import com.sharinganmod.data.SharinganData;
import com.sharinganmod.data.Skills;
import com.sharinganmod.data.Tx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ClientOverlay {
    private ClientOverlay() {}

    /** Tela de visao: tinta do Sharingan, desgaste e cegueira. */
    public static void renderVision(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SharinganData d = ClientData.DATA;
        int w = gg.guiWidth();
        int h = gg.guiHeight();

        if (d.eyesActive()) gg.fill(0, 0, w, h, 0x18FF0000);
        if (d.susanooOn) gg.fill(0, 0, w, h, (0x20 << 24) | (d.susanooColor & 0xFFFFFF));

        if (d.blind && d.stage < 5) {
            // Cego para sempre: escuridao total. Com o Sharingan ativo, restam so silhuetas escuras do mundo.
            int alpha = d.eyesActive() ? 0xD2 : 0xFF;
            gg.fill(0, 0, w, h, alpha << 24);
        } else if (d.strain > 60) {
            int a = (int) ((d.strain - 60) / 40f * 0x90);
            gg.fill(0, 0, w, h, (a << 24) | 0x300000);
        }
    }

    private static String time(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    private static void bar(GuiGraphics gg, int x, int y, int w, int h, float frac, int color) {
        gg.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        gg.fill(x, y, x + w, y + h, 0xFF202028);
        int fw = (int) (w * Math.max(0f, Math.min(1f, frac)));
        gg.fill(x, y, x + fw, y + h, color);
    }

    private static int shade(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((argb & 0xFF) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Barra com moldura metalica, fundo escuro, enchimento com brilho, divisoes e um ornamento em cada ponta. */
    private static void fancyBar(GuiGraphics gg, int x, int y, int w, int h, float frac, int color) {
        // moldura externa escura e aro metalico (claro em cima/esquerda, escuro embaixo/direita)
        gg.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF0B0B10);
        gg.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF6E7380);
        gg.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, 0xFF30333B);
        gg.fill(x + w + 1, y - 2, x + w + 2, y + h + 2, 0xFF30333B);
        gg.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF14141A);
        // fundo
        gg.fill(x, y, x + w, y + h, 0xFF1C2230);
        gg.fill(x, y, x + w, y + 1, 0xFF0F131C);
        // enchimento com degrade e brilho
        int fw = (int) (w * Math.max(0f, Math.min(1f, frac)));
        if (fw > 0) {
            int half = Math.max(1, h / 2);
            gg.fill(x, y, x + fw, y + half, shade(color, 1.25f));
            gg.fill(x, y + half, x + fw, y + h, shade(color, 0.75f));
            gg.fill(x, y, x + fw, y + 1, shade(color, 1.7f));
            gg.fill(x, y + h - 1, x + fw, y + h, shade(color, 0.5f));
        }
        // divisoes a cada 10%
        for (int i = 1; i < 10; i++) {
            int sx = x + w * i / 10;
            gg.fill(sx, y, sx + 1, y + h, 0x55000000);
        }
        // ornamentos nas pontas
        gg.fill(x - 4, y + h / 2 - 1, x - 2, y + h / 2 + 2, 0xFFB7BCC8);
        gg.fill(x + w + 2, y + h / 2 - 1, x + w + 4, y + h / 2 + 2, 0xFFB7BCC8);
        gg.fill(x - 3, y - 3, x - 1, y - 1, 0xFFB7BCC8);
        gg.fill(x + w + 1, y - 3, x + w + 3, y - 1, 0xFFB7BCC8);
        gg.fill(x - 3, y + h + 1, x - 1, y + h + 3, 0xFFB7BCC8);
        gg.fill(x + w + 1, y + h + 1, x + w + 3, y + h + 3, 0xFFB7BCC8);
    }

    /** HUD: bloco de status (chakra etc.) e bloco de habilidades, cada um na posicao escolhida no layout (aba J). */
    public static void renderHud(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // com o F3 aberto o HUD do mod some, para dar para ler a tela de depuracao
        if (mc.gui.getDebugOverlay().showDebugScreen()) return;
        SharinganData d = ClientData.DATA;
        if (d.stage <= 0) return;
        int sw = gg.guiWidth();
        int sh = gg.guiHeight();
        scaled(gg, ClientLayout.place(ClientLayout.statusX, sw, ClientLayout.STATUS_W),
                ClientLayout.place(ClientLayout.statusY, sh, ClientLayout.STATUS_H), () -> drawStatus(gg, mc.font, d));
        if (d.stage >= 4) {
            scaled(gg, ClientLayout.place(ClientLayout.skillsX, sw, ClientLayout.SKILLS_W),
                    ClientLayout.place(ClientLayout.skillsY, sh, ClientLayout.SKILLS_H), () -> drawSkills(gg, mc.font, d));
        }
    }

    private static void scaled(GuiGraphics gg, int x, int y, Runnable draw) {
        gg.pose().pushPose();
        gg.pose().translate(x, y, 0);
        gg.pose().scale(ClientLayout.SCALE, ClientLayout.SCALE, 1f);
        draw.run();
        gg.pose().popPose();
    }

    private static void drawStatus(GuiGraphics gg, Font f, SharinganData d) {
        int x = 0;
        int y = 0;
        int barW = 110;

        MutableComponent title = Tx.t(d.eyesActive() ? "hud.active" : "hud.off", Skills.stageName(d));
        if (d.blind && d.stage < 5) title.append(Tx.t("hud.blind"));
        gg.drawString(f, title, x, y, d.eyesActive() ? 0xFF4444 : 0x999999);
        y += 11;

        // chakra
        float max = Math.max(1f, d.maxChakra());
        y += 2;
        fancyBar(gg, x + 4, y, barW, 7, d.chakra / max, d.eyesActive() ? 0xFF3A7BFF : 0xFF2A5FCC);
        String chakraText = Tx.t("hud.chakra", (int) d.chakra, (int) max).getString()
                + (d.eyesActive() ? "  (" + time(d.secondsLeft()) + ")" : "");
        gg.drawString(f, chakraText, x + barW + 14, y, d.chakraFraction() <= SharinganData.LOW_START ? 0xFF8844 : 0x66AAFF);
        y += 12;

        // barra de XP (1 a 2 tomoe) ou de desgaste ocular (Mangekyou); ticks e esquiva ficam embaixo dela
        if (d.stage < 3) {
            bar(gg, x, y, barW, 4, d.xp / (float) Math.max(1, d.xpNeeded()), 0xFFDD2222);
            gg.drawString(f, Tx.t("hud.xp", d.xp, d.xpNeeded()), x + barW + 6, y - 2, 0xFF8888);
            y += 8;
        } else if (d.stage == 4) {
            y += 2;
            fancyBar(gg, x + 4, y, barW, 5, d.strain / 100f, 0xFFCC1111);
            gg.drawString(f, Tx.t("hud.strain", (int) d.strain), x + barW + 14, y - 1, 0xFF8888);
            y += 10;
        }

        if (d.eyesActive()) {
            int n = SharinganData.SLOW_TICKS[d.level()];
            gg.drawString(f, Tx.t("hud.ticks", 20 - n, n), x, y, 0x88DDFF);
            y += 10;
            int pct = Math.round(d.dodgeChance() * 100f);
            Component dodge = d.dodgeCd > 0f ? Tx.t("hud.dodge.cd", pct, (int) Math.ceil(d.dodgeCd))
                    : Tx.t("hud.dodge.ready", pct);
            gg.drawString(f, dodge, x, y, d.dodgeCd > 0f ? 0xAAAAAA : 0x55DDFF);
            y += 10;
        }
        if (d.stage >= 3) {
            Component pts = d.pointsEarned >= SharinganData.MAX_UPGRADES
                    ? Tx.t("hud.points.max", d.pointsAvailable())
                    : Tx.t("hud.points", d.pointsAvailable(), d.pointXp, SharinganData.POINT_XP);
            gg.drawString(f, pts, x, y, d.pointsAvailable() > 0 ? 0xFFDD55 : 0x66CCFF);
        }
    }

    private static void drawSkills(GuiGraphics gg, Font f, SharinganData d) {
        int y = 0;
        for (int i = 0; i < 4; i++) {
            float base = i == 3 ? Skills.SUSANOO_COST : Skills.CHAKRA[d.type][i];
            int cost = Math.round(base * d.penalty());
            Component state = d.cooldowns[i] > 0 ? Tx.t("hud.cd", d.cooldowns[i] / 20 + 1) : Tx.t("hud.ready");
            int col = d.cooldowns[i] > 0 ? 0xAAAAAA : (d.chakra >= cost ? 0x55FF55 : 0xFFAA33);
            if (i == 3 && d.eyeSide != 0) {
                state = Tx.t("hud.needboth");
                col = 0xFF5555;
            } else if (i == 3 && d.susanooOn) {
                state = Tx.t("hud.susanoo.on");
                col = 0x8888FF;
            }
            // mostra a tecla que o jogador realmente configurou nos controles
            MutableComponent line = Component.literal("[")
                    .append(ModKeys.SKILLS[i].getTranslatedKeyMessage())
                    .append(Component.literal("] "))
                    .append(Skills.name(d.type, i))
                    .append(Component.literal(" (" + cost + "): "))
                    .append(state);
            gg.drawString(f, line, 0, y, col);
            y += 10;
        }
    }
}
