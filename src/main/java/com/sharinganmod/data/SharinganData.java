package com.sharinganmod.data;

import com.sharinganmod.registry.ModRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Estado do Sharingan de um jogador (salvo no jogador e copiado ao morrer). */
public class SharinganData implements INBTSerializable<CompoundTag> {
    /** "Ticks" que o Sharingan tira dos inimigos por nivel (indice = nivel 0..5). */
    public static final int[] SLOW_TICKS = {0, 2, 3, 4, 5, 6};
    /** XP para sair de 1 para 2 tomoe e de 2 para 3 tomoe. */
    public static final int XP_TO_2 = 170;
    public static final int XP_TO_3 = 350;
    /** Chakra recuperado por segundo com o Sharingan desligado (fixo, nao depende do tamanho da barra). */
    public static final float REGEN_PER_SECOND = 1f;
    /** Chakra baixo: abaixo desta fracao do chakra maximo comecam os efeitos negativos (e pioram ate desligar). */
    public static final float LOW_START = 0.30f;
    /** Chance de esquivar (desviar de um ataque) por nivel de poder: 1 tomoe 10%, 2 tomoe 20%, 3 tomoe 30%, Mangekyou e Eterno 40%. */
    /** Chakra gasto em cada esquiva bem-sucedida (sem chakra suficiente nao ha esquiva). */
    public static final float DODGE_CHAKRA = 10f;
    public static final float[] DODGE = {0f, 0.20f, 0.30f, 0.40f, 0.50f, 0.50f};
    /** Recarga da esquiva depois de levar um golpe: sorteada entre 5 e 10 segundos reais. */
    public static final float DODGE_CD_MIN = 3f;
    public static final float DODGE_CD_MAX = 5f;
    /** Upgrades de chakra: ate 5, pagos em niveis de XP (10, 15, 20, 25, 30), cada um soma 132 (o 5o chega a 960). */
    public static final int MAX_UPGRADES = 5;
    /** Celulas de Hashirama: chakra extra por segundo, desgaste ocular regenerado (% por minuto) e vida extra. */
    public static final float HASHIRAMA_CHAKRA_REGEN = 2f;
    public static final float HASHIRAMA_STRAIN_PER_MINUTE = 1f;
    /** O desgaste ocular fica 50% mais dificil: cabe 50% mais uso ate cegar. */
    public static final float STRAIN_RESISTANCE = 1.5f;
    public static final double HASHIRAMA_EXTRA_HEALTH = 10.0;
    /** XP ganho com 3 tomoe (ou mais) para cada ponto de habilidade; no maximo 5 pontos no total. */
    public static final int POINT_XP = 400;
    public static final int[] UPGRADE_LEVELS = {10, 15, 20, 25, 30};
    public static final float UPGRADE_STEP = 132f;
    /** Chance de despertar o Sharingan ao sobreviver a um golpe quase fatal. So quem e do Cla Uchiha desperta. */
    public static final float AWAKEN_CHANCE_UCHIHA = 0.30f;
    public static final float AWAKEN_CHANCE_OTHER = 0.0f;

    /** 0 nada, 1-3 tomoe, 4 mangekyou, 5 mangekyou eterno */
    public int stage;
    /** 0 nenhum, 1 Itachi, 2 Sasuke, 3 Obito, 4 Shisui, 5 Eterno */
    public int type;
    public int xp;
    /** Upgrades de chakra comprados (0 a 5): valem do 3 tomoe em diante e ficam com o jogador. */
    public int chakraUp;
    /** Celulas de Hashirama implantadas (permanente: so sai por comando de administrador). */
    public boolean hashirama;
    /** Pontos de habilidade ja ganhos (0 a 5, ficam com o jogador). Disponiveis = ganhos - upgrades comprados. */
    public int pointsEarned;
    /** XP acumulado (com 3 tomoe ou mais) rumo ao proximo ponto de habilidade. */
    public int pointXp;
    /** Segundos reais que faltam para a esquiva voltar a funcionar (0 = pronta). */
    public float dodgeCd;
    public boolean equipped = true;
    public boolean sharinganOn;
    public boolean mangekyouOn;
    public boolean susanooOn;
    /** Cor (RGB) do Susanoo escolhida pelo jogador. */
    public int susanooColor = 0x7366FF;
    /** Cegueira permanente: nao ha como desfazer. */
    public boolean blind;
    /** Tem so um olho (partido): halfEye == (eyeSide != 0). */
    public boolean halfEye;
    public boolean gaveEye;
    /** Quais olhos o jogador tem: 0 os dois, 1 so o esquerdo, 2 so o direito. */
    public int eyeSide;
    /** Ja teve um Sharingan neste mundo: so pode conseguir outro por implante. */
    public boolean awakened;
    /** Ja tocou o audio do primeiro uso do Mangekyou Eterno (toca so uma vez). */
    public boolean eternalHeard;
    /** Bits dos tipos de Mangekyou ja tidos (1 Itachi, 2 Sasuke, 3 Obito, 4 Shisui), para a conquista. */
    public int seenTypes;
    /** Ticks sem recuperar chakra (evita recuperar apertando a tecla sem parar). Nao e salvo. */
    public int regenDelay;
    /** Ticks ate poder ligar/desligar o Sharingan ou o Mangekyou de novo (2 s). Nao e salvo. */
    public int toggleCd;
    /** Desgaste ocular 0..100 (permanente, nunca diminui). */
    public float strain;
    public float chakra;
    public boolean uchiha;
    /** Ja recebeu o Emblema do Cla Uchiha no primeiro login do mundo. */
    public boolean kit;
    public int susanooTicks;
    public int intangibleTicks;
    public int suctionTicks;
    public int[] cooldowns = new int[4];

    public static SharinganData get(Player p) {
        return p.getData(ModRegistries.SHARINGAN);
    }

    /** Nivel efetivo dos poderes (0 = desligado). */
    public int level() {
        if (!sharinganOn || !equipped || stage <= 0) return 0;
        if (stage <= 3) return stage;
        return mangekyouOn ? stage : 3;
    }

    /** Chance de esquiva agora: so com o Sharingan ativo, pelo nivel de poder em uso (Mangekyou ligado = 40%). */
    public float dodgeChance() {
        float c = DODGE[Math.max(0, Math.min(5, level()))];
        // com so um olho (Sharingan partido ao meio) a esquiva cai pela metade
        return eyeSide != 0 ? c * 0.5f : c;
    }

    public boolean eyesActive() {
        return sharinganOn && equipped && stage > 0;
    }

    public void setBoth() {
        eyeSide = 0;
        halfEye = false;
        gaveEye = false;
    }

    /** side: 1 = so o olho esquerdo, 2 = so o direito. */
    public void setOnly(int side) {
        eyeSide = side;
        halfEye = side != 0;
        gaveEye = halfEye;
    }

    /** Chakra gasto por segundo por nivel de poder: 1 tomoe 2, 2 tomoe 1,5, 3 tomoe 1, Mangekyou e Eterno (ligados) 3. */
    public static final float[] DRAIN_PER_SECOND = {0f, 2f, 1.5f, 1f, 3f, 3f};
    /**
     * Tamanho da barra de chakra de 1, 2 e 3 tomoe, calculado como se todo Sharingan gastasse 1 por segundo:
     * 2 min = 120, 3 min e meio = 210 e 5 min = 300. Mangekyou e Eterno NAO aumentam a barra (so desbloqueiam).
     */
    public static final float[] POOL = {0f, 120f, 210f, 300f};

    /** Nivel usado no gasto: o nivel ativo, ou o do modo que seria ligado (Sharingan comum; Mangekyou usa 3 tomoe se desligado). */
    private int drainLevel() {
        int l = level();
        return l > 0 ? l : Math.max(1, Math.min(3, stage));
    }

    /** Chakra gasto por tick (sem compensar o tick rate do jogo). */
    public float drainPerTick() {
        return DRAIN_PER_SECOND[Math.min(5, drainLevel())] / 20f;
    }

    /** Segundos de Sharingan com a barra cheia, no modo que esta ligado (ou que seria ligado). */
    public float fullSeconds() {
        if (stage <= 0) return 0f;
        return maxChakra() / DRAIN_PER_SECOND[Math.min(5, drainLevel())];
    }

    public int xpNeeded() {
        return stage == 1 ? XP_TO_2 : stage == 2 ? XP_TO_3 : 0;
    }

    /** Barra de chakra: 120 / 210 / 300 por tomoe; do 3 tomoe em diante soma os upgrades (ate 960 com os 5). */
    public float maxChakra() {
        if (stage <= 0) return 0f;
        float base = POOL[Math.min(3, stage)];
        return stage >= 3 ? base + chakraUp * UPGRADE_STEP : base;
    }

    /** Chakra atual como fracao (0 a 1) do maximo. */
    public float chakraFraction() {
        float max = maxChakra();
        return max <= 0f ? 1f : chakra / max;
    }

    /** Custo em niveis do proximo upgrade de chakra (0 se ja comprou todos). */
    public int nextUpgradeCost() {
        return chakraUp >= MAX_UPGRADES ? 0 : UPGRADE_LEVELS[chakraUp];
    }

    /** Multiplicador de recarga e de custo de chakra: quem nao e do cla Uchiha (ex.: Sharingan dado por admin) paga 50% a mais. */
    public float penalty() {
        return uchiha ? 1.0f : 1.5f;
    }

    /** Multiplicador do desgaste ocular: +25% sem o cla, -50% com meio olho (Obito). */
    public float strainFactor() {
        return (uchiha ? 1.0f : 1.25f) * (halfEye ? 0.5f : 1.0f) / STRAIN_RESISTANCE;
    }

    /** Segundos de Sharingan restantes com o chakra atual. */
    /** Pontos de habilidade que o jogador ainda pode gastar em upgrades de chakra. */
    public int pointsAvailable() {
        return Math.max(0, pointsEarned - chakraUp);
    }

    public int secondsLeft() {
        return (int) (chakra / drainPerTick() / 20f);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("stage", stage);
        t.putInt("type", type);
        t.putInt("xp", xp);
        t.putInt("cup", chakraUp);
        t.putBoolean("hsh", hashirama);
        t.putInt("spe", pointsEarned);
        t.putInt("spx", pointXp);
        t.putFloat("dg", dodgeCd);
        t.putBoolean("equipped", equipped);
        t.putBoolean("on", sharinganOn);
        t.putBoolean("mk", mangekyouOn);
        t.putBoolean("sus", susanooOn);
        t.putInt("susc", susanooColor);
        t.putBoolean("blind", blind);
        t.putBoolean("half", halfEye);
        t.putInt("side", eyeSide);
        t.putBoolean("awk", awakened);
        t.putBoolean("eh", eternalHeard);
        t.putInt("seen", seenTypes);
        t.putBoolean("gave", gaveEye);
        t.putFloat("strain", strain);
        t.putFloat("chakra", chakra);
        t.putBoolean("uchiha", uchiha);
        t.putBoolean("kit", kit);
        t.putInt("susT", susanooTicks);
        t.putInt("intT", intangibleTicks);
        t.putInt("sucT", suctionTicks);
        t.putIntArray("cd", cooldowns);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        stage = t.getInt("stage");
        type = t.getInt("type");
        xp = t.getInt("xp");
        chakraUp = Math.max(0, Math.min(MAX_UPGRADES, t.getInt("cup")));
        hashirama = t.getBoolean("hsh");
        // quem ja tinha comprado upgrades antes dos pontos existirem nao perde nada
        pointsEarned = Math.max(chakraUp, Math.min(MAX_UPGRADES, t.getInt("spe")));
        pointXp = Math.max(0, t.getInt("spx"));
        dodgeCd = Math.max(0f, t.getFloat("dg"));
        equipped = !t.contains("equipped") || t.getBoolean("equipped");
        sharinganOn = t.getBoolean("on");
        mangekyouOn = t.getBoolean("mk");
        susanooOn = t.getBoolean("sus");
        susanooColor = t.contains("susc") ? (t.getInt("susc") & 0xFFFFFF) : 0x7366FF;
        blind = t.getBoolean("blind");
        halfEye = t.getBoolean("half");
        eyeSide = t.getInt("side");
        if (halfEye && eyeSide == 0) eyeSide = 1;
        halfEye = eyeSide != 0;
        awakened = t.getBoolean("awk") || stage > 0;
        eternalHeard = t.getBoolean("eh");
        seenTypes = t.getInt("seen");
        gaveEye = t.getBoolean("gave");
        strain = t.getFloat("strain");
        chakra = t.getFloat("chakra");
        uchiha = t.getBoolean("uchiha");
        kit = t.getBoolean("kit");
        susanooTicks = t.getInt("susT");
        intangibleTicks = t.getInt("intT");
        suctionTicks = t.getInt("sucT");
        int[] a = t.getIntArray("cd");
        cooldowns = new int[4];
        for (int i = 0; i < 4 && i < a.length; i++) cooldowns[i] = a[i];
    }
}
