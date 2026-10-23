package com.sharinganmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Qual olho aparece no rosto de cada jogador (recebido do servidor). Codigos em FacePayload. */
public final class ClientFace {
    private ClientFace() {}

    private static final Map<UUID, Integer> FACES = new HashMap<>();

    /** Quando (ms) cada jogador ligou o Sharingan: os olhos dao uma girada logo depois de ligar. */
    private static final Map<UUID, Long> SPIN_START = new HashMap<>();

    /** Semente do angulo final dos tomoe de cada jogador (vem do servidor, igual para todos os clientes). */
    private static final Map<UUID, Integer> SEEDS = new HashMap<>();

    public static void set(UUID id, int code) {
        if (code <= 0) {
            FACES.remove(id);
            SPIN_START.remove(id);
            SEEDS.remove(id);
        } else {
            SEEDS.put(id, (code >> 8) & 0xFFF);
            Integer old = FACES.put(id, code);
            // gira ao ligar e tambem quando o olho muda (ex.: 3 tomoe -> Mangekyou ligado depois)
            if (old == null || (old & 15) != (code & 15)) SPIN_START.put(id, System.currentTimeMillis());
        }
    }

    /** Progresso (0 a 1) da girada dos olhos; 1 = terminou. */
    public static float spinProgress(UUID id, long durationMs) {
        Long start = SPIN_START.get(id);
        if (start == null) return 1f;
        float t = (System.currentTimeMillis() - start) / (float) durationMs;
        if (t >= 1f) {
            SPIN_START.remove(id);
            return 1f;
        }
        return Math.max(0f, t);
    }

    /**
     * Angulos finais {olho esquerdo, olho direito} em graus, que a girada de ativacao deixa nos olhos.
     * 1 tomoe: angulo aleatorio; 50% de chance dos dois iguais e 50% de um ficar no angulo contrario (180 graus).
     * 2 tomoe: os dois sempre iguais, com angulo aleatorio. Demais olhos: sem rotacao extra.
     */
    public static float[] finalAngles(UUID id) {
        int code = FACES.getOrDefault(id, 0);
        int type = code & 15;
        if (type != 1 && type != 2) return new float[] {0f, 0f};
        java.util.Random r = new java.util.Random(SEEDS.getOrDefault(id, 0) * 2654435761L + type);
        float a = r.nextFloat() * 360f;
        boolean opposite = type == 1 && r.nextBoolean();
        return new float[] {a, opposite ? a + 180f : a};
    }

    public static int get(UUID id) {
        return FACES.getOrDefault(id, 0);
    }

    /** Layout dos olhos escolhido por cada jogador no proprio rosto: {dx, y, half, dx2, y2, na frente (0/1)}. */
    private static final Map<UUID, float[]> LAYOUTS = new HashMap<>();

    public static void setLayout(UUID id, float dx, float y, float half, float dx2, float y2, boolean front) {
        LAYOUTS.put(id, new float[] {dx, y, half, dx2, y2, front ? 1f : 0f});
    }

    /** Layout do jogador, ou null se ele ainda nao enviou (usa o padrao). */
    public static float[] layout(UUID id) {
        return LAYOUTS.get(id);
    }

    public static void clear() {
        FACES.clear();
        SPIN_START.clear();
        SEEDS.clear();
        LAYOUTS.clear();
    }
}
