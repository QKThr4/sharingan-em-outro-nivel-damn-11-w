package com.sharinganmod.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/** Preferencias de layout do jogador (posicao/tamanho dos olhos no rosto e posicao dos blocos do HUD). */
public final class ClientLayout {
    private ClientLayout() {}

    public static final float HALF_MIN = 0.6f;
    public static final float HALF_MAX = 3.0f;

    /** Tamanho dos blocos do HUD usado para posicionar (o mesmo no editor e no jogo). */
    /** Escala do HUD (70% = 30% menor que o original, para nao sobrepor a vida/fome/hotbar). */
    public static final float SCALE = 0.7f;
    public static final int STATUS_W = 190;
    public static final int STATUS_H = 60;
    public static final int SKILLS_W = 150;
    public static final int SKILLS_H = 29;

    /** Distancia do centro de cada olho ao meio do rosto (pixels da cabeca, que tem 8 de largura). */
    public static final float DEFAULT_DX = 2.15f;
    public static final float DEFAULT_Y = -3.5f;
    public static final float DEFAULT_HALF = 1.5f;

    public static float eyeDx = DEFAULT_DX;
    /** Altura do centro dos olhos no modelo (a cabeca vai de -8 ate 0, y para baixo). */
    public static float eyeY = DEFAULT_Y;
    /** Metade do lado de cada olho em pixels da pele. */
    public static float eyeHalf = DEFAULT_HALF;
    /** Segundo olho (o da esquerda na tela), usado quando o espelhamento esta desligado: distancia ao meio e altura. */
    public static float eyeDx2 = DEFAULT_DX;
    public static float eyeY2 = DEFAULT_Y;
    /** Espelhamento ligado: os dois olhos andam juntos, simetricos (como sempre foi). Desligado: independentes. */
    public static boolean eyeMirror = true;
    /** true = Sharingan na frente da segunda camada da skin (cabelo etc.); false = atras dela. */
    public static boolean eyeFront = true;

    /** Posicao dos blocos do HUD como fracao (0 a 1) da area livre da tela. */
    public static float statusX = 0f;
    public static float statusY = 0f;
    public static float skillsX = 1f;
    public static float skillsY = 1f;

    static {
        load();
    }

    public static void reset() {
        eyeDx = 2.15f;
        eyeY = -3.5f;
        eyeHalf = 1.5f;
        eyeDx2 = 2.15f;
        eyeY2 = -3.5f;
        eyeMirror = true;
        eyeFront = true;
        statusX = 0f;
        statusY = 0f;
        skillsX = 1f;
        skillsY = 1f;
    }

    public static void clamp() {
        eyeHalf = Math.max(HALF_MIN, Math.min(HALF_MAX, eyeHalf));
        eyeDx = Math.max(0.3f, Math.min(Math.max(0.3f, 4.2f - eyeHalf), eyeDx));
        eyeY = Math.max(-7.6f, Math.min(-0.4f, eyeY));
        eyeDx2 = Math.max(0.3f, Math.min(Math.max(0.3f, 4.2f - eyeHalf), eyeDx2));
        eyeY2 = Math.max(-7.6f, Math.min(-0.4f, eyeY2));
        if (eyeMirror) {
            eyeDx2 = eyeDx;
            eyeY2 = eyeY;
        }
        statusX = Math.max(0f, Math.min(1f, statusX));
        statusY = Math.max(0f, Math.min(1f, statusY));
        skillsX = Math.max(0f, Math.min(1f, skillsX));
        skillsY = Math.max(0f, Math.min(1f, skillsY));
    }

    /** Posicao em pixels de um bloco de 'size' numa tela de 'total', com margem de 6. */
    public static int place(float frac, int total, int size) {
        int free = Math.max(0, total - size - 12);
        return 6 + Math.round(frac * free);
    }

    /** Inverso de place: fracao correspondente a uma posicao em pixels. */
    public static float frac(double pos, int total, int size) {
        int free = total - size - 12;
        if (free <= 0) return 0f;
        return (float) Math.max(0.0, Math.min(1.0, (pos - 6) / free));
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("sharingan-layout.properties");
    }

    private static float get(Properties p, String key, float def) {
        try {
            String v = p.getProperty(key);
            return v == null ? def : Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            }
            eyeDx = get(p, "eyeDx", eyeDx);
            eyeY = get(p, "eyeY", eyeY);
            eyeHalf = get(p, "eyeHalf", eyeHalf);
            eyeDx2 = get(p, "eyeDx2", eyeDx);
            eyeY2 = get(p, "eyeY2", eyeY);
            eyeMirror = !"false".equals(p.getProperty("eyeSymmetric"));
            eyeFront = !"false".equals(p.getProperty("eyeFront"));
            statusX = get(p, "statusX", statusX);
            statusY = get(p, "statusY", statusY);
            skillsX = get(p, "skillsX", skillsX);
            skillsY = get(p, "skillsY", skillsY);
            clamp();
        } catch (Exception ignored) {
            // sem arquivo ou arquivo invalido: fica com o padrao
        }
    }

    public static void save() {
        try {
            clamp();
            Properties p = new Properties();
            p.setProperty("eyeDx", Float.toString(eyeDx));
            p.setProperty("eyeY", Float.toString(eyeY));
            p.setProperty("eyeHalf", Float.toString(eyeHalf));
            p.setProperty("eyeDx2", Float.toString(eyeDx2));
            p.setProperty("eyeY2", Float.toString(eyeY2));
            p.setProperty("eyeSymmetric", Boolean.toString(eyeMirror));
            p.setProperty("eyeFront", Boolean.toString(eyeFront));
            p.setProperty("statusX", Float.toString(statusX));
            p.setProperty("statusY", Float.toString(statusY));
            p.setProperty("skillsX", Float.toString(skillsX));
            p.setProperty("skillsY", Float.toString(skillsY));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Layout do mod Sharingan");
            }
        } catch (Exception ignored) {
            // nao foi possivel salvar: o layout vale so ate fechar o jogo
        }
        publish();
    }

    /** Envia ao servidor como o jogador quer os olhos no proprio rosto, para os outros jogadores verem igual. */
    public static void publish() {
        if (net.minecraft.client.Minecraft.getInstance().player == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.sharinganmod.net.LayoutSendPayload(eyeDx, eyeY, eyeHalf, eyeDx2, eyeY2, eyeFront));
    }
}
