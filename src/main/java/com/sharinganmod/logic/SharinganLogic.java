package com.sharinganmod.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import com.sharinganmod.data.Tx;
import com.sharinganmod.data.SharinganData;
import com.sharinganmod.data.Skills;
import com.sharinganmod.net.FacePayload;
import com.sharinganmod.net.Net;
import com.sharinganmod.registry.ModRegistries;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Toda a logica de estado do Sharingan (servidor). */
public final class SharinganLogic {
    private SharinganLogic() {}

    /** Tick rate do jogo aplicado por nos (20 = normal). */
    private static float appliedRate = 20f;

    /** Mobs dominados pelo Kotoamatsukami: UUID -> ticks restantes. */
    public static final Map<UUID, Integer> CHARM = new HashMap<>();
    /** Marca gravada no mob dominado: continua aliado para sempre, mesmo depois de reiniciar o mundo. */
    public static final String ALLY_TAG = "sharingan_ally";
    /** Ultimo olho enviado a todos os clientes para o rosto de cada jogador. */
    private static final Map<UUID, Integer> LAST_FACE = new HashMap<>();

    // ---------- utilitarios ----------
    public static void msg(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    public static void msg(ServerPlayer p, Component text) {
        p.displayClientMessage(text, true);
    }

    public static void chat(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    public static void chat(ServerPlayer p, Component text) {
        p.sendSystemMessage(text);
    }

    /** Nome da tecla configurada pelo jogador (o cliente resolve o nome real). */
    public static MutableComponent key(String name) {
        return Component.keybind("key.sharingan." + name);
    }

    public static boolean isPet(Entity e, Entity owner) {
        return e instanceof TamableAnimal t && owner instanceof LivingEntity o && t.isOwnedBy(o);
    }

    public static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** Coloca o item na mao principal se estiver vazia; senao no inventario (ou no chao se cheio). */
    public static void giveInHand(ServerPlayer p, ItemStack s) {
        if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, s);
        else give(p, s);
    }

    /** Toca um som proprio do mod (audio enviado pelo jogador) na posicao do jogador. */
    static void snd(ServerPlayer p, java.util.function.Supplier<net.minecraft.sounds.SoundEvent> ev) {
        // som preso ao jogador: acompanha o jogador enquanto ele anda (pacote de som de entidade)
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder =
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev.get());
        p.serverLevel().getChunkSource().broadcastAndSend(p,
                new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(holder, SoundSource.PLAYERS, p,
                        1.0f, 1.0f, p.getRandom().nextLong()));
    }

    /** Som ao ligar o Mangekyou: depende do tipo (e dos olhos, no caso do Obito). O Eterno toca so na primeira vez. */
    private static void mangekyouOnSound(ServerPlayer p, SharinganData d) {
        if (d.stage >= 5 && d.type == 8) {
            // o Eterno de Madara nao tem o som da primeira vez: toca sempre o som do Madara
            snd(p, ModRegistries.SND_MK_MADARA);
            return;
        }
        if (d.stage >= 5) {
            if (!d.eternalHeard) {
                d.eternalHeard = true;
                snd(p, ModRegistries.SND_ETERNAL_FIRST);
            } else {
                snd(p, ModRegistries.SND_MK_SASUKE);
            }
            return;
        }
        switch (d.type) {
            case 1 -> snd(p, ModRegistries.SND_MK_BOTH);
            case 2 -> snd(p, ModRegistries.SND_MK_SASUKE);
            case 6 -> snd(p, ModRegistries.SND_MK_MADARA);
            case 7 -> snd(p, ModRegistries.SND_MK_IZUNA);
            case 3 -> snd(p, d.eyeSide == 1 ? ModRegistries.SND_MK_OBITO_LEFT
                    : d.eyeSide == 2 ? ModRegistries.SND_MK_OBITO_RIGHT : ModRegistries.SND_MK_BOTH);
            default -> snd(p, ModRegistries.SND_ON);
        }
    }

    static void glass(ServerPlayer p, float pitch) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, pitch);
    }

    // ---------- progressao ----------
    /** Chance de o primeiro despertar ja comecar com 2 tomoe. */
    public static final float AWAKEN_TWO_TOMOE_CHANCE = 0.05f;

    /** Recarga (ms, tempo real) entre dois ganhos de XP por kill, para nao farmar XP facil (ex.: slimes). */
    public static final long KILL_XP_COOLDOWN_MS = 5000L;
    private static final Map<UUID, Long> KILL_XP_READY = new HashMap<>();

    /** Da o XP da kill se a recarga de 5 s ja passou. */
    public static void killXp(ServerPlayer p, SharinganData d, int amount) {
        long now = System.currentTimeMillis();
        Long ready = KILL_XP_READY.get(p.getUUID());
        if (ready != null && now < ready) return;
        KILL_XP_READY.put(p.getUUID(), now + KILL_XP_COOLDOWN_MS);
        addXp(p, d, amount);
    }

    /** Libera no livro de receitas as receitas das armas do mod (ao entrar no mundo). */
    public static void unlockRecipes(ServerPlayer p) {
        if (p.getServer() == null) return;
        for (String name : new String[] {"espada_sasuke", "gunbai_madara"}) {
            p.getServer().getRecipeManager()
                    .byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sharinganmod.SharinganMod.MODID, name))
                    .ifPresent(h -> p.awardRecipes(java.util.List.of(h)));
        }
    }

    public static void awaken(ServerPlayer p, SharinganData d) {
        d.stage = p.getRandom().nextFloat() < AWAKEN_TWO_TOMOE_CHANCE ? 2 : 1;
        d.xp = 0;
        d.awakened = true;
        d.setBoth();
        d.equipped = true;
        d.chakra = d.maxChakra();
        chat(p, Tx.t(ChatFormatting.RED, "awaken", key("sharingan"), key("screen")).withStyle(ChatFormatting.BOLD));
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 1f, 0.7f);
        Net.sync(p);
    }

    public static void addXp(ServerPlayer p, SharinganData d, int amount) {
        if (d.stage < 1) return;
        if (d.stage >= 3) {
            addPointXp(p, d, amount);
            return;
        }
        d.xp += amount;
        while (d.stage < 3 && d.xp >= d.xpNeeded()) {
            d.xp -= d.xpNeeded();
            // a barra cresce: o chakra que faltava continua faltando (nao enche de graca)
            float oldMax = d.maxChakra();
            d.stage++;
            d.chakra = Math.min(d.maxChakra(), d.chakra + (d.maxChakra() - oldMax));
            chat(p, Tx.t(ChatFormatting.RED, "evolve", d.stage).withStyle(ChatFormatting.BOLD));
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.6f);
            if (d.sharinganOn) refreshEffects(p, d);
        }
        if (d.stage >= 3) d.xp = 0;
    }

    /** Com 3 tomoe ou mais, cada 200 de XP ganho vira 1 ponto de habilidade (ate 5 no total). */
    private static void addPointXp(ServerPlayer p, SharinganData d, int amount) {
        if (d.pointsEarned >= SharinganData.MAX_UPGRADES) {
            d.pointXp = 0;
            return;
        }
        d.pointXp += amount;
        while (d.pointXp >= SharinganData.POINT_XP && d.pointsEarned < SharinganData.MAX_UPGRADES) {
            d.pointXp -= SharinganData.POINT_XP;
            d.pointsEarned++;
            chat(p, Tx.t(ChatFormatting.AQUA, "point.gain", d.pointsEarned, SharinganData.MAX_UPGRADES));
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.4f);
        }
        if (d.pointsEarned >= SharinganData.MAX_UPGRADES) d.pointXp = 0;
        Net.sync(p);
    }

    /** Retorna o tipo de Mangekyou (1-4) para o mob domesticado morto, ou 0. */
    /** Qualquer animal domesticado dá um Mangekyou aleatório (1 Itachi, 2 Sasuke, 3 Obito, 4 Shisui). */
    public static int mangekyouTypeFor(LivingEntity victim) {
        boolean tamed = (victim instanceof TamableAnimal t && t.isTame())
                || (victim instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h && h.isTamed())
                || (victim instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwnerUUID() != null);
        int[] pool = {1, 2, 3, 4, 6, 7};
        return tamed ? pool[victim.getRandom().nextInt(pool.length)] : 0;
    }

    /**
     * Dono do pet morto, se estiver online (em qualquer dimensao), ou null. O dono recebe o Mangekyou quando o
     * pet morre de qualquer jeito: por ele, por outro jogador, por mob, queda, fogo, vazio, comando etc.
     */
    public static ServerPlayer tamedOwner(LivingEntity victim) {
        if (!(victim instanceof net.minecraft.world.entity.OwnableEntity o)) return null;
        UUID id = o.getOwnerUUID();
        if (id == null || victim.getServer() == null) return null;
        return victim.getServer().getPlayerList().getPlayer(id);
    }

    public static void awakenMangekyou(ServerPlayer p, SharinganData d, int type) {
        d.stage = 4;
        d.type = type;
        d.mangekyouOn = false;
        chat(p, Tx.t(ChatFormatting.DARK_RED, "mk.awaken", Skills.typeName(type)).withStyle(ChatFormatting.BOLD));
        chat(p, Tx.t(ChatFormatting.GRAY, "mk.keys", key("mangekyou"), key("skill1"), key("skill2"), key("skill3"),
                key("skill4")));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.8f, 1.4f);
        Net.sync(p);
    }

    /** Item que representa o Sharingan atual do jogador (vazio se ele nao tem nenhum). */
    private static ItemStack currentAsItem(SharinganData d) {
        if (d.stage <= 0) return ItemStack.EMPTY;
        ItemStack s;
        if (d.stage >= 5) s = new ItemStack(ModRegistries.eyeFor(d.type == 8 ? 8 : 5));
        else if (d.stage == 4) s = new ItemStack(ModRegistries.eyeFor(d.type));
        else return SharinganItem.make(d.stage, d.xp, d.eyeSide);
        EyeItem.setSide(s, d.eyeSide);
        return s;
    }

    /** Tira todo o Sharingan do jogador (volta a nao ter nenhum). */
    private static void clearCurrent(ServerPlayer p, SharinganData d) {
        deactivateAll(p, d);
        d.stage = 0;
        d.type = 0;
        d.xp = 0;
        d.setBoth();
        d.mangekyouOn = false;
    }

    /**
     * Fim do uso de um item de Sharingan: consome o item usado e devolve ao jogador o Sharingan que ele
     * tinha antes (na mao se o item acabou, senao no inventario). 'old' null = nao foi possivel usar.
     */
    public static InteractionResultHolder<ItemStack> finishUse(ServerPlayer sp, ItemStack stack, ItemStack old) {
        if (old == null) return InteractionResultHolder.fail(stack);
        stack.shrink(1);
        if (!old.isEmpty()) {
            if (stack.isEmpty()) return InteractionResultHolder.success(old);
            give(sp, old);
        }
        return InteractionResultHolder.success(stack);
    }

    /** Equipa um Sharingan de 1-3 tomoe com o XP guardado no item, substituindo o atual. side: 0 os dois olhos. */
    public static ItemStack equipTier(ServerPlayer p, int tier, int storedXp, int side) {
        return implant(p, tier, 0, storedXp, side);
    }

    /**
     * Implanta um olho (kind 1-4 = Mangekyou de Itachi/Sasuke/Obito/Shisui, 5 = Mangekyou Eterno).
     * O Mangekyou e o Eterno ja incluem o Sharingan de 3 tomoe, entao implantar um implanta o outro.
     * Substitui o Sharingan que o jogador tiver (o antigo volta como item).
     * Retorna null se nao foi possivel; senao o item que o jogador perdeu na troca (vazio se nao tinha).
     */
    public static ItemStack transplant(ServerPlayer p, int kind, ItemStack stack) {
        return implant(p, (kind == 5 || kind == 8) ? 5 : 4, kind, 0, EyeItem.side(stack));
    }

    /**
     * Implante generico. stage: 1-3 tomoe, 4 Mangekyou, 5 Eterno. type: tipo do Mangekyou (1-5) ou 0.
     * side: qual olho o item contem (0 os dois, 1 esquerdo, 2 direito).
     */
    private static ItemStack implant(ServerPlayer p, int stage, int type, int xp, int side) {
        SharinganData d = SharinganData.get(p);
        int itemType = type;
        int curType = d.type;

        if (d.stage == stage && curType == itemType) {
            // mesmo Sharingan: se o jogador tem so um olho e o item traz o outro, os dois se unem
            if (d.eyeSide != 0 && side != 0 && side == 3 - d.eyeSide) {
                d.setBoth();
                d.xp = Math.max(d.xp, stage >= 3 ? 0 : Math.min(xp, Math.max(0, d.xpNeeded() - 1)));
                chat(p, Tx.t(ChatFormatting.DARK_PURPLE, "implant.join"));
                p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
                Net.sync(p);
                return ItemStack.EMPTY;
            }
            if (d.eyeSide == side) {
                msg(p, Tx.t("implant.have"));
                return null;
            }
        }

        // Itachi + Sasuke = Mangekyou Eterno; Madara + Izuna = Mangekyou Eterno de Madara (olhos inteiros)
        boolean pairSasuke = (d.type == 1 && type == 2) || (d.type == 2 && type == 1);
        boolean pairMadara = (d.type == 6 && type == 7) || (d.type == 7 && type == 6);
        if (d.stage == 4 && d.eyeSide == 0 && side == 0 && stage == 4 && (pairSasuke || pairMadara)) {
            d.stage = 5;
            d.type = pairMadara ? 8 : 5;
            d.mangekyouOn = false;
            if (d.susanooOn) endSusanoo(p, d);
            chat(p, Tx.t(ChatFormatting.DARK_RED, pairMadara ? "eternal.join.madara" : "eternal.join").withStyle(ChatFormatting.BOLD));
            chat(p, Tx.t(ChatFormatting.GRAY, d.blind ? "eternal.blind" : "eternal.ok"));
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8f, 1.2f);
            Net.sync(p);
            return ItemStack.EMPTY;
        }

        ItemStack old = currentAsItem(d);
        clearCurrent(p, d);
        d.equipped = true;
        d.awakened = true;
        d.stage = stage;
        d.type = stage >= 4 ? type : 0;
        d.xp = stage >= 3 ? 0 : Math.min(xp, Math.max(0, d.xpNeeded() - 1));
        d.setOnly(side);
        d.chakra = Math.min(d.chakra, d.maxChakra());
        Component name = stage == 5 ? Skills.eternalName(type)
                : stage == 4 ? Tx.t("name.mk", Skills.typeName(type))
                : Tx.t("name.tier", stage);
        chat(p, Tx.t(ChatFormatting.RED, "implant.done", name, Skills.sideName(side)));
        if (stage >= 4) chat(p, Tx.t(ChatFormatting.GRAY, "implant.includes3"));
        if (!d.uchiha) chat(p, Tx.t(ChatFormatting.GRAY, "implant.noclan"));
        if (!old.isEmpty()) chat(p, Tx.t(ChatFormatting.GRAY, "implant.old"));
        p.level().playSound(null, p.blockPosition(), stage >= 4 ? SoundEvents.WITHER_SPAWN : SoundEvents.ENDERMAN_STARE,
                SoundSource.PLAYERS, 0.6f, 1.4f);
        Net.sync(p);
        return old;
    }

    // ---------- acoes vindas do cliente ----------
    public static void handleAction(ServerPlayer p, int id) {
        SharinganData d = SharinganData.get(p);
        switch (id) {
            case 0, 1 -> {
                if (d.toggleCd > 0) {
                    msg(p, Tx.t("toggle.wait", d.toggleCd / 20 + 1));
                } else {
                    boolean sOn = d.sharinganOn;
                    boolean mOn = d.mangekyouOn;
                    if (id == 0) toggleSharingan(p, d);
                    else toggleMangekyou(p, d);
                    // so conta quando algo mudou de verdade (ligou ou desligou): evita ligar/desligar sem parar
                    if (sOn != d.sharinganOn || mOn != d.mangekyouOn) d.toggleCd = 40;
                }
            }
            case 2, 3, 4, 5 -> Abilities.use(p, d, id - 2);
            case 7 -> splitEye(p, d);
            case 20 -> buyChakraUpgrade(p, d);
            case 8, 9 -> removeSharingan(p, d);
            default -> { }
        }
        Net.sync(p);
    }

    /** Ataques que podem ser esquivados: golpes e projeteis de entidades. Nao vale queda, fogo, lava, afogamento, explosao etc. */
    /** Janela (ms reais) depois de uma esquiva em que golpes seguintes de entidades tambem sao evitados. */
    private static final long DODGE_SHIELD_MS = 700L;
    private static final java.util.Map<java.util.UUID, Long> DODGE_SHIELD = new java.util.HashMap<>();

    /** Contra-ataque: depois de uma esquiva, o proximo golpe do jogador em ate 1 segundo causa um pouco mais de dano. */
    private static final long COUNTER_MS = 1000L;
    private static final java.util.Map<java.util.UUID, Long> COUNTER = new java.util.HashMap<>();

    public static void counter(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (COUNTER.isEmpty() || !(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        if (e.getSource().getDirectEntity() != sp) return;
        Long until = COUNTER.get(sp.getUUID());
        if (until == null) return;
        COUNTER.remove(sp.getUUID());
        if (System.currentTimeMillis() > until || SharinganData.get(sp).level() <= 0) return;
        LivingEntity t = e.getEntity();
        e.setAmount(e.getAmount() * 1.3f + 1f);
        ServerLevel sl = sp.serverLevel();
        sl.sendParticles(new DustParticleOptions(new Vector3f(0.95f, 0.05f, 0.05f), 1.6f),
                t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 30, 0.4, 0.5, 0.4, 0.0);
        sl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 15, 0.3, 0.4, 0.3, 0.2);
        sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    /** Visao de chakra (3 tomoe em diante) e leitura de ataque: vale para qualquer entidade viva, nao so jogadores. */
    private static void sendVision(ServerPlayer p, SharinganData d) {
        int lv = d.level();
        if (lv <= 0 || p.tickCount % 2 != 0) return;
        double margin = lv >= 4 ? 2.5 : lv == 3 ? 1.6 : lv == 2 ? 0.9 : 0.3;
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        java.util.List<Integer> pcts = new java.util.ArrayList<>();
        for (LivingEntity le : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32),
                x -> x != p && x.isAlive() && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            if (ids.size() < 100) {
                float frac;
                if (le instanceof net.minecraft.world.entity.player.Player pl && SharinganData.get(pl).maxChakra() > 0f) {
                    SharinganData pd = SharinganData.get(pl);
                    frac = pd.chakra / pd.maxChakra();
                } else {
                    frac = le.getHealth() / Math.max(1f, le.getMaxHealth());
                }
                ids.add(le.getId());
                pcts.add(Math.max(0, Math.min(100, Math.round(frac * 100f))));
            }
            if (ids.size() < 100 && le instanceof net.minecraft.world.entity.Mob m && le.distanceTo(p) <= 24
                    && attackImminent(m, lv, margin)) {
                ids.add(le.getId());
                pcts.add(-1);
            }
        }
        if (!ids.isEmpty()) PacketDistributor.sendToPlayer(p, new com.sharinganmod.net.VisionPayload(ids, pcts));
    }

    private static boolean attackImminent(net.minecraft.world.entity.Mob m, int lv, double margin) {
        LivingEntity t = m.getTarget();
        if (t == null || !t.isAlive()) return false;
        if (m instanceof net.minecraft.world.entity.monster.Creeper c && c.getSwellDir() > 0) return true;
        if (m instanceof net.minecraft.world.entity.monster.Ghast g && g.isCharging()) return true;
        if (m.isUsingItem()) return true;
        double dist = m.distanceTo(t);
        if (lv >= 3 && m instanceof net.minecraft.world.entity.monster.RangedAttackMob && dist < 20 && m.hasLineOfSight(t)) {
            return true;
        }
        double reach = m.getBbWidth() * 2.0 + t.getBbWidth() + 0.6;
        return dist <= reach + margin;
    }

    private static boolean dodgeable(DamageSource s) {
        if (s.getEntity() == null && s.getDirectEntity() == null) return false;
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        // Qualquer projetil (inclusive magicos de outros mods) pode ser desviado, mesmo que o tipo de dano ignore armadura.
        if (s.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) return true;
        if (s.is(DamageTypeTags.BYPASSES_ARMOR)) return false;
        if (s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.IS_FALL) || s.is(DamageTypeTags.IS_DROWNING)
                || s.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        return !(s.is(DamageTypeTags.IS_FIRE) && !s.is(DamageTypeTags.IS_PROJECTILE));
    }

    /**
     * Esquiva: com o Sharingan ativo, cada golpe tem uma chance de ser desviado. A recarga de 3 a 5 segundos
     * (sorteada) so comeca quando o jogador DESVIA com sucesso. Retorna true se desviou (o dano deve ser cancelado).
     */
    public static boolean tryDodge(ServerPlayer p, DamageSource src) {
        SharinganData d = SharinganData.get(p);
        // Depois de desviar, os golpes seguintes por um instante (ex.: slime encostado, que bate todo tick) tambem
        // sao desviados: e o mesmo ataque. Sem isso o proximo golpe entrava e parecia que desviar nao funcionava.
        Long until = DODGE_SHIELD.get(p.getUUID());
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                if (dodgeable(src)) return true;
            } else {
                DODGE_SHIELD.remove(p.getUUID());
            }
        }
        float chance = d.dodgeChance();
        if (chance <= 0f || !dodgeable(src) || p.invulnerableTime > 10 || d.dodgeCd > 0f
                || d.chakra < SharinganData.DODGE_CHAKRA) {
            return false;
        }
        if (p.getRandom().nextFloat() < chance) {
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 14, 0.35, 0.6, 0.35, 0.05);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.8f);
            p.displayClientMessage(Tx.t(ChatFormatting.RED, "dodge"), true);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new com.sharinganmod.net.DodgePayload(p.getUUID()));
            DODGE_SHIELD.put(p.getUUID(), System.currentTimeMillis() + DODGE_SHIELD_MS);
            COUNTER.put(p.getUUID(), System.currentTimeMillis() + COUNTER_MS);
            d.chakra = Math.max(0f, d.chakra - SharinganData.DODGE_CHAKRA);
            d.dodgeCd = SharinganData.DODGE_CD_MIN
                    + p.getRandom().nextFloat() * (SharinganData.DODGE_CD_MAX - SharinganData.DODGE_CD_MIN);
            Net.sync(p);
            return true;
        }
        return false;
    }

    /** Arvore de chakra: compra o proximo upgrade (so do 3 tomoe em diante, em ordem, pagando niveis de XP). */
    private static void buyChakraUpgrade(ServerPlayer p, SharinganData d) {
        if (d.stage < 3) {
            msg(p, Tx.t(ChatFormatting.RED, "up.locked"));
            return;
        }
        if (d.chakraUp >= SharinganData.MAX_UPGRADES) {
            msg(p, Tx.t(ChatFormatting.GOLD, "up.max"));
            return;
        }
        boolean free = p.getAbilities().instabuild;
        if (!free && d.pointsAvailable() <= 0) {
            msg(p, Tx.t(ChatFormatting.RED, "up.nopoints", SharinganData.POINT_XP));
            return;
        }
        int cost = d.nextUpgradeCost();
        if (!free && p.experienceLevel < cost) {
            msg(p, Tx.t(ChatFormatting.RED, "up.nolevels", cost, p.experienceLevel));
            return;
        }
        if (!free) p.giveExperienceLevels(-cost);
        float oldMax = d.maxChakra();
        d.chakraUp++;
        // criativo ganha o upgrade sem gastar ponto: garante que os pontos ganhos nunca fiquem abaixo dos upgrades
        d.pointsEarned = Math.max(d.pointsEarned, d.chakraUp);
        d.chakra = Math.min(d.maxChakra(), d.chakra + (d.maxChakra() - oldMax));
        chat(p, Tx.t(ChatFormatting.AQUA, "up.done", d.chakraUp, (int) d.maxChakra()));
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
    }

    private static boolean enoughChakraToStart(ServerPlayer p, SharinganData d) {
        if (d.chakra < d.maxChakra() * 0.1f) {
            msg(p, Tx.t("chakra.low.wait", (int) d.chakra, (int) d.maxChakra()));
            // apertar a tecla sem parar nao acelera a recuperacao: cada tentativa pausa a recuperacao por 3 s
            d.regenDelay = 60;
            return false;
        }
        return true;
    }

    private static void toggleSharingan(ServerPlayer p, SharinganData d) {
        if (d.stage < 1) { msg(p, Tx.t("not.awakened")); return; }
        if (!d.equipped) {
            msg(p, Tx.t("not.equipped", key("screen")));
            return;
        }
        if (d.sharinganOn) {
            deactivateAll(p, d);
            msg(p, Tx.t("sharingan.off"));
            snd(p, ModRegistries.SND_OFF);
        } else {
            if (!enoughChakraToStart(p, d)) return;
            d.sharinganOn = true;
            refreshEffects(p, d);
            msg(p, Tx.t("sharingan.on"));
            snd(p, ModRegistries.SND_ON);
        }
    }

    private static void toggleMangekyou(ServerPlayer p, SharinganData d) {
        if (d.stage < 4) { msg(p, Tx.t("need.mk")); return; }
        if (!d.equipped) {
            msg(p, Tx.t("not.equipped", key("screen")));
            return;
        }
        if (d.blind && d.stage < 5) { msg(p, Tx.t("destroyed")); return; }
        if (d.mangekyouOn) {
            // desligar o Mangekyou desliga tambem o Sharingan de 3 tomoe
            deactivateAll(p, d);
            msg(p, Tx.t("mk.off"));
            snd(p, ModRegistries.SND_OFF);
        } else {
            if (!enoughChakraToStart(p, d)) return;
            d.sharinganOn = true;
            d.mangekyouOn = true;
            refreshEffects(p, d);
            msg(p, Tx.t("mk.on"));
            mangekyouOnSound(p, d);
        }
    }

    /**
     * Retira o Sharingan e entrega o item na mao, em qualquer estagio. O Mangekyou e o Eterno sao o 3 tomoe
     * evoluido: sai tudo junto num item so (o jogador fica sem Sharingan). 1/2/3 tomoe viram o item do
     * estagio, com o XP guardado.
     */
    private static void removeSharingan(ServerPlayer p, SharinganData d) {
        if (d.stage <= 0) { msg(p, Tx.t("remove.none")); return; }
        int stage = d.stage;
        ItemStack out = currentAsItem(d);
        clearCurrent(p, d);
        giveInHand(p, out);
        glass(p, 0.8f);
        Component what = stage <= 3 ? Tx.t("name.tier", stage) : stage == 4 ? Tx.t("what.mk") : Tx.t("what.eternal");
        chat(p, Tx.t(ChatFormatting.RED, "remove.done", what));
        if (stage >= 4) chat(p, Tx.t(ChatFormatting.GRAY, "remove.strain"));
    }

    /**
     * Parte o olho ao meio, com qualquer Sharingan (1/2/3 tomoe, Mangekyou ou Eterno). O olho que sai e
     * sorteado (esquerdo ou direito) e vira um item na mao; o jogador fica so com o outro. Mangekyou/Eterno
     * levam o 3 tomoe junto. Com um olho so nao existe Susanoo.
     */
    private static void splitEye(ServerPlayer p, SharinganData d) {
        if (d.stage <= 0) { msg(p, Tx.t("split.none")); return; }
        if (d.eyeSide != 0) { msg(p, Tx.t("split.already")); return; }
        int removed = 1 + p.getRandom().nextInt(2);
        ItemStack out = currentAsItem(d);
        EyeItem.setSide(out, removed);
        d.setOnly(3 - removed);
        if (d.susanooOn) endSusanoo(p, d);
        giveInHand(p, out);
        glass(p, 1.0f);
        Adv.grant(p, "split");
        chat(p, Tx.t(ChatFormatting.DARK_PURPLE, "split.done", Skills.sideName(removed), Skills.sideName(d.eyeSide)));
        chat(p, Tx.t(ChatFormatting.GRAY, "split.note"));
    }

    // ---------- efeitos ----------
    public static void deactivateAll(ServerPlayer p, SharinganData d) {
        d.regenDelay = 60;
        d.sharinganOn = false;
        d.mangekyouOn = false;
        if (d.susanooOn) endSusanoo(p, d);
        // habilidades do Mangekyou ainda em andamento param junto com ele
        d.intangibleTicks = 0;
        d.suctionTicks = 0;
        removeEffects(p);
    }

    public static void refreshEffects(ServerPlayer p, SharinganData d) {
        removeEffects(p);
        int lvl = d.level();
        if (lvl <= 0) return;
        applyEffects(p, lvl);
    }

    /**
     * 1 tomoe: visao noturna. 2: + velocidade. 3: + forca. Mangekyou: + resistencia e forca II.
     * Eterno: resistencia II.
     */
    private static void applyEffects(ServerPlayer p, int lvl) {
        effect(p, MobEffects.NIGHT_VISION, 260, 0);
        if (lvl >= 2) effect(p, MobEffects.MOVEMENT_SPEED, 60, 0);
        if (lvl >= 3) effect(p, MobEffects.DAMAGE_BOOST, 60, lvl >= 4 ? 1 : 0);
        if (lvl >= 4) effect(p, MobEffects.DAMAGE_RESISTANCE, 60, lvl >= 5 ? 1 : 0);
    }

    static void effect(ServerPlayer p, Holder<MobEffect> eff, int ticks, int amp) {
        p.addEffect(new MobEffectInstance(eff, ticks, amp, true, false, false));
    }

    private static void removeOurs(ServerPlayer p, Holder<MobEffect> eff) {
        MobEffectInstance i = p.getEffect(eff);
        if (i != null && i.isAmbient() && !i.isVisible()) p.removeEffect(eff);
    }

    private static void removeEffects(ServerPlayer p) {
        removeOurs(p, MobEffects.NIGHT_VISION);
        removeOurs(p, MobEffects.MOVEMENT_SPEED);
        removeOurs(p, MobEffects.DAMAGE_BOOST);
        removeOurs(p, MobEffects.DAMAGE_RESISTANCE);
        removeOurs(p, MobEffects.ABSORPTION);
    }

    // ---------- desgaste e cegueira (permanentes) ----------
    public static void addStrain(ServerPlayer p, SharinganData d, float amount) {
        if (d.stage >= 5 || d.blind || amount <= 0) return;
        float old = d.strain;
        d.strain += amount * d.strainFactor();
        if (old < 60 && d.strain >= 60) msg(p, Tx.t(ChatFormatting.RED, "strain.burn"));
        if (old < 85 && d.strain >= 85) msg(p, Tx.t(ChatFormatting.DARK_RED, "strain.near").withStyle(ChatFormatting.BOLD));
        if (d.strain >= 100) {
            d.strain = 100;
            d.blind = true;
            d.mangekyouOn = false;
            if (d.susanooOn) endSusanoo(p, d);
            refreshEffects(p, d);
            chat(p, Tx.t(ChatFormatting.DARK_RED, "strain.blind").withStyle(ChatFormatting.BOLD));
            chat(p, Tx.t(ChatFormatting.GRAY, "strain.blind.hint", key("sharingan")));
            p.level().playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8f, 0.8f);
        }
    }

    // ---------- Susanoo ----------
    public static void startSusanoo(ServerPlayer p, SharinganData d) {
        boolean perfect = d.stage >= 5;
        d.susanooOn = true;
        d.susanooTicks = perfect ? 1800 : 900;
        effect(p, MobEffects.ABSORPTION, d.susanooTicks, perfect ? 9 : 4);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.9f, 0.6f);
        msg(p, Tx.t(perfect ? "susanoo.perfect" : "susanoo.on"));
        Adv.grant(p, "susanoo");
    }

    public static void endSusanoo(ServerPlayer p, SharinganData d) {
        d.susanooOn = false;
        d.susanooTicks = 0;
        d.cooldowns[3] = Math.max(d.cooldowns[3], 1200);
        removeOurs(p, MobEffects.ABSORPTION);
        msg(p, Tx.t("susanoo.end"));
    }

    private static void susanooTick(ServerPlayer p, SharinganData d) {
        boolean perfect = d.stage >= 5;
        d.susanooTicks--;
        d.chakra -= Skills.SUSANOO_DRAIN_PER_TICK;
        addStrain(p, d, Skills.SUSANOO_STRAIN_PER_TICK);
        if (!d.susanooOn) return;
        if (p.tickCount % 20 == 0) {
            effect(p, MobEffects.DAMAGE_RESISTANCE, 60, 3);
            effect(p, MobEffects.DAMAGE_BOOST, 60, perfect ? 2 : 1);
        }
        if (p.tickCount % 10 == 0) {
            double rad = perfect ? 6 : 4;
            float dmg = perfect ? 10f : 6f;
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(rad),
                    x -> x != p && x.isAlive() && !isPet(x, p)
                            && (x instanceof Enemy || (x instanceof Mob m && m.getTarget() == p)))) {
                e.hurt(p.damageSources().playerAttack(p), dmg);
                e.knockback(0.8, p.getX() - e.getX(), p.getZ() - e.getZ());
            }
        }
        if (p.tickCount % 4 == 0) susanooShell(p, d, perfect);
        if (d.chakra <= 0f || d.susanooTicks <= 0 || !d.mangekyouOn) endSusanoo(p, d);
    }

    // ---------- Kamui: intangibilidade sem hitbox ----------
    private static final String NOCOL_TEAM = "sharingan_nocollide";
    private static final Map<UUID, String> PREV_TEAM = new HashMap<>();

    /**
     * Liga/desliga a ausencia de colisao do jogador com outras entidades: enquanto estiver na equipe especial
     * (regra de colisao NUNCA) ele nao empurra nem e empurrado por mobs e jogadores. Devolve a equipe anterior ao sair.
     */
    public static void setNoCollide(ServerPlayer p, boolean on) {
        if (p.getServer() == null) return;
        net.minecraft.world.scores.Scoreboard sb = p.getServer().getScoreboard();
        String name = p.getScoreboardName();
        net.minecraft.world.scores.PlayerTeam cur = sb.getPlayersTeam(name);
        boolean in = cur != null && NOCOL_TEAM.equals(cur.getName());
        if (on && !in) {
            net.minecraft.world.scores.PlayerTeam t = sb.getPlayerTeam(NOCOL_TEAM);
            if (t == null) {
                t = sb.addPlayerTeam(NOCOL_TEAM);
                t.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
            }
            if (cur != null) PREV_TEAM.put(p.getUUID(), cur.getName());
            sb.addPlayerToTeam(name, t);
        } else if (!on && in) {
            sb.removePlayerFromTeam(name, cur);
            String prev = PREV_TEAM.remove(p.getUUID());
            if (prev != null) {
                net.minecraft.world.scores.PlayerTeam pt = sb.getPlayerTeam(prev);
                if (pt != null) sb.addPlayerToTeam(name, pt);
            }
        }
    }

    /** Armadura de chakra azul/roxa ao redor do jogador (visual simplificado do Susanoo). */
    private static void susanooShell(ServerPlayer p, SharinganData d, boolean perfect) {
        ServerLevel l = p.serverLevel();
        double radius = perfect ? 2.6 : 1.8;
        double height = perfect ? 5.0 : 3.4;
        int rgb = d.susanooColor;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.6f);
        double[] hs = {0.1, height * 0.3, height * 0.55, height * 0.8};
        for (int i = 0; i < 10; i++) {
            double a = i * (Math.PI * 2 / 10) + p.tickCount * 0.1;
            for (double h : hs) {
                double r = radius * (0.6 + 0.4 * Math.sin(Math.PI * h / height));
                l.sendParticles(dust, p.getX() + Math.cos(a) * r, p.getY() + h, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    // ---------- rosto (olhos visiveis em todos os clientes) ----------
    /** Baixo 4 bits: 0 nenhum, 1-3 tomoe, 4 Itachi, 5 Sasuke, 6 Obito, 7 Shisui, 8 Eterno, 9 Madara, 10 Izuna, 11 Eterno de Madara. Bits altos: olho(s). */
    public static int faceCode(SharinganData d) {
        if (d.stage <= 0 || !d.eyesActive()) return 0;
        int base;
        if (d.level() >= 4) {
            if (d.stage >= 5) base = d.type == 8 ? 11 : 8;
            else if (d.type == 6) base = 9;
            else if (d.type == 7) base = 10;
            else base = 3 + Math.max(1, Math.min(4, d.type));
        }
        else base = Math.min(3, d.stage);
        // bits altos: quais olhos aparecem (0 os dois, 1 so o esquerdo, 2 so o direito)
        return base | (d.eyeSide << 4);
    }

    private static void updateFace(ServerPlayer p, SharinganData d) {
        int base = faceCode(d);
        int last = LAST_FACE.getOrDefault(p.getUUID(), 0);
        if (base != (last & 0xFF)) {
            // bits 8 a 19: semente do angulo dos tomoe, sorteada ao ligar (ou quando o tipo do olho muda)
            int seed = 0;
            if (base > 0) {
                seed = (last & 15) != (base & 15) || last == 0
                        ? 1 + p.getRandom().nextInt(4095)
                        : (last >> 8) & 0xFFF;
            }
            int code = base | (seed << 8);
            LAST_FACE.put(p.getUUID(), code);
            PacketDistributor.sendToAllPlayers(new FacePayload(p.getUUID(), code));
        }
    }

    /** Ao entrar, o jogador recebe o rosto de quem ja esta com o Sharingan ativo. */
    public static void sendFacesTo(ServerPlayer joiner) {
        for (Map.Entry<UUID, Integer> en : LAST_FACE.entrySet()) {
            if (en.getValue() > 0 && !en.getKey().equals(joiner.getUUID())) {
                PacketDistributor.sendToPlayer(joiner, new FacePayload(en.getKey(), en.getValue()));
            }
        }
    }

    public static void forgetFace(ServerPlayer p) {
        LAST_FACE.remove(p.getUUID());
    }

    /** Layout dos olhos de cada jogador no proprio rosto: {dx, y, half, dx2, y2, na frente (0/1)}. */
    private static final Map<UUID, float[]> LAYOUTS = new java.util.HashMap<>();

    private static float fin(float v, float def) {
        return Float.isNaN(v) || Float.isInfinite(v) ? def : v;
    }

    /** Guarda o layout do jogador (limitado aos mesmos valores do editor) e repassa a todos. */
    public static void setLayout(ServerPlayer p, float dx, float y, float half, float dx2, float y2, boolean front) {
        float h = Math.max(0.6f, Math.min(3.0f, fin(half, 1.5f)));
        float x = Math.max(0.3f, Math.min(Math.max(0.3f, 4.2f - h), fin(dx, 2.15f)));
        float yy = Math.max(-7.6f, Math.min(-0.4f, fin(y, -3.5f)));
        float x2 = Math.max(0.3f, Math.min(Math.max(0.3f, 4.2f - h), fin(dx2, 2.15f)));
        float yy2 = Math.max(-7.6f, Math.min(-0.4f, fin(y2, -3.5f)));
        LAYOUTS.put(p.getUUID(), new float[] {x, yy, h, x2, yy2, front ? 1f : 0f});
        PacketDistributor.sendToAllPlayers(new com.sharinganmod.net.LayoutPayload(p.getUUID(), x, yy, h, x2, yy2, front));
    }

    /** Ao entrar, o jogador recebe o layout dos olhos de quem ja esta no servidor. */
    public static void sendLayoutsTo(ServerPlayer joiner) {
        for (Map.Entry<UUID, float[]> en : LAYOUTS.entrySet()) {
            if (!en.getKey().equals(joiner.getUUID())) {
                float[] l = en.getValue();
                PacketDistributor.sendToPlayer(joiner, new com.sharinganmod.net.LayoutPayload(en.getKey(), l[0], l[1], l[2], l[3], l[4], l[5] > 0.5f));
            }
        }
    }

    public static void forgetLayout(ServerPlayer p) {
        LAYOUTS.remove(p.getUUID());
    }

    public static void clearAll() {
        LAST_FACE.clear();
        LAYOUTS.clear();
        Abilities.clearPending();
        CHARM.clear();
        appliedRate = 20f;
    }

    // ---------- tick do jogador ----------
    public static void tick(ServerPlayer p) {
        SharinganData d = SharinganData.get(p);
        updateFace(p, d);
        if (d.toggleCd > 0) d.toggleCd--;
        // recarga da esquiva em tempo real (cada tick dura mais quando o jogo esta mais lento)
        if (d.dodgeCd > 0f) d.dodgeCd = Math.max(0f, d.dodgeCd - 1f / Math.max(1f, appliedRate));
        hashiramaTick(p, d);
        sendVision(p, d);
        if (p.tickCount % 20 == 0) Adv.check(p, d);
        if (d.stage > 0) d.awakened = true;
        blindEffect(p, d);
        chakraLock(p, d);
        setNoCollide(p, d.intangibleTicks > 0);
        if (d.stage <= 0) return;

        for (int i = 0; i < 4; i++) if (d.cooldowns[i] > 0) d.cooldowns[i]--;

        // chakra: gasta com o Sharingan ativo, recupera com ele desligado
        float max = d.maxChakra();
        if (d.chakra > max) d.chakra = max;
        if (d.eyesActive()) {
            // com o jogo mais lento cada tick dura mais: compensa para o tempo real continuar o mesmo
            d.chakra -= d.drainPerTick() * (20f / appliedRate);
            if (d.chakra <= 0f) {
                d.chakra = 0f;
                deactivateAll(p, d);
                // chakra zerado: 10 s sem regenerar, cego, sem poder pular e com Lentidao IV
                d.regenDelay = 200;
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, true, true));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 3, false, true, true));
                msg(p, Tx.t(ChatFormatting.RED, "chakra.out"));
                snd(p, ModRegistries.SND_OFF);
            }
        } else if (d.regenDelay > 0) {
            d.regenDelay--;
        } else if (d.chakra < max) {
            // 1 de chakra por segundo de tempo real (cada tick dura mais quando o jogo esta mais lento)
            d.chakra = Math.min(max, d.chakra
                    + (SharinganData.REGEN_PER_SECOND + (d.hashirama ? SharinganData.HASHIRAMA_CHAKRA_REGEN : 0f))
                    / Math.max(1f, appliedRate));
        }
        lowChakraEffects(p, d);
        if (d.eyesActive() && p.tickCount % 5 == 0) purgeBadEffects(p, d);

        int lvl = d.level();
        if (lvl > 0) {
            if (p.tickCount % 20 == 0) {
                applyEffects(p, lvl);
                addXp(p, d, 1);
            }
        }

        // sem o Mangekyou ligado nenhuma habilidade dele continua funcionando (qualquer que seja o motivo)
        if (!d.mangekyouOn) {
            if (d.susanooOn) endSusanoo(p, d);
            d.intangibleTicks = 0;
            d.suctionTicks = 0;
        }
        if (d.mangekyouOn) addStrain(p, d, Skills.MANGEKYOU_STRAIN_PER_TICK);
        if (d.susanooOn && d.eyeSide != 0) endSusanoo(p, d);
        if (d.susanooOn) susanooTick(p, d);

        if (d.intangibleTicks > 0) {
            d.intangibleTicks--;
            if (p.tickCount % 2 == 0) {
                p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.8, 0.4, 0.1);
            }
        }

        if (d.suctionTicks > 0) suctionTick(p, d);

        if (p.tickCount % 10 == 0) Net.sync(p);
    }

    /** Cegueira permanente: efeito de cegueira constante, exceto com o Eterno nos olhos ou com o Sharingan ativo. */
    private static void blindEffect(ServerPlayer p, SharinganData d) {
        if (!d.blind || p.tickCount % 20 != 0) return;
        if (d.stage >= 5 || d.eyesActive()) {
            removeOurs(p, MobEffects.BLINDNESS);
        } else {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, true, false, true));
        }
    }

    private static final net.minecraft.resources.ResourceLocation NO_JUMP_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sharinganmod.SharinganMod.MODID, "chakra_no_jump");

    /**
     * Chakra zerado (os 10 s sem regenerar): o jogador nao consegue pular e fica com Lentidao IV. Passados os 10 s
     * tudo volta ao normal. O pulo e bloqueado zerando o atributo de forca de pulo enquanto durar.
     */
    private static void chakraLock(ServerPlayer p, SharinganData d) {
        boolean lock = d.stage > 0 && d.chakra <= 0f && d.regenDelay > 0 && !d.eyesActive();
        net.minecraft.world.entity.ai.attributes.AttributeInstance jump =
                p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        if (jump == null) return;
        boolean has = jump.getModifier(NO_JUMP_ID) != null;
        if (lock && !has) {
            jump.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(NO_JUMP_ID, -1.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        } else if (!lock && has) {
            jump.removeModifier(NO_JUMP_ID);
        }
        if (lock && p.tickCount % 10 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, true, true));
        }
    }

    /**
     * Efeitos colaterais de chakra baixo, em % do chakra maximo atual: com 30% comeca a fome e a lentidao, que piora
     * por faixas ate o nivel III (30% a 20%: I, 20% a 10%: II, abaixo de 10%: III); com 15% nausea e com 7,5%
     * fraqueza. Com o chakra em zero a lentidao vai para o nivel IV (junto do bloqueio do pulo, ver chakraLock).
     */
    private static void lowChakraEffects(ServerPlayer p, SharinganData d) {
        if (p.tickCount % 20 != 0) return;
        float f = d.chakraFraction();
        float s = SharinganData.LOW_START;
        if (f <= s) {
            lowEffect(p, MobEffects.HUNGER);
            int level = d.chakra <= 0f ? 4 : Math.min(3, 1 + (int) ((s - f) / s * 3f));
            net.minecraft.world.effect.MobEffectInstance cur = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            // so troca a lentidao que ja era nossa (ambiente) quando o nivel muda, sem mexer na de outras fontes
            if (cur != null && cur.isAmbient() && cur.getAmplifier() != level - 1) p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, level - 1, true, false, true));
        }
        if (f <= s * 0.5f) lowEffect(p, MobEffects.CONFUSION);
        if (f <= s * 0.25f) lowEffect(p, MobEffects.WEAKNESS);
    }

    private static void lowEffect(ServerPlayer p, Holder<MobEffect> eff) {
        p.addEffect(new MobEffectInstance(eff, 100, 0, true, false, true));
    }

    /** Com o Sharingan ativo: remove nausea, escuridao e cegueira (a nausea do chakra baixo continua). */
    private static void purgeBadEffects(ServerPlayer p, SharinganData d) {
        if (p.hasEffect(MobEffects.DARKNESS)) p.removeEffect(MobEffects.DARKNESS);
        if (p.hasEffect(MobEffects.BLINDNESS)) p.removeEffect(MobEffects.BLINDNESS);
        if (d.chakraFraction() > SharinganData.LOW_START * 0.5f && p.hasEffect(MobEffects.CONFUSION)) {
            p.removeEffect(MobEffects.CONFUSION);
        }
    }

    /**
     * Diminuir os ticks: enquanto algum jogador estiver com o Sharingan ativo, o jogo inteiro roda a
     * (20 - N) ticks por segundo, exatamente como "/tick rate". Com varios jogadores vale o maior N.
     * Ao desligar, volta a 20.
     */
    public static void updateTickRate(MinecraftServer server) {
        int slow = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!sp.isAlive()) continue;
            int lvl = SharinganData.get(sp).level();
            if (lvl > 0) slow = Math.max(slow, SharinganData.SLOW_TICKS[lvl]);
        }
        float want = 20f - slow;
        if (want != appliedRate) {
            appliedRate = want;
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), "tick rate " + (int) want);
        }
    }

    // ---------- Celulas de Hashirama ----------
    private static final net.minecraft.resources.ResourceLocation HASHIRAMA_HP =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sharinganmod.SharinganMod.MODID, "hashirama_health");
    /** Tempo, em segundos reais, para confirmar o implante das Celulas com o segundo clique. */
    public static final long HASHIRAMA_CONFIRM_MS = 10_000L;
    private static final Map<UUID, Long> HASHIRAMA_ARMED = new HashMap<>();

    /** Vida extra, Pressa I infinita e regeneracao do desgaste ocular enquanto o jogador tiver as Celulas. */
    private static void hashiramaTick(ServerPlayer p, SharinganData d) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance hp = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp == null) return;
        if (!d.hashirama) {
            if (hp.hasModifier(HASHIRAMA_HP)) {
                hp.removeModifier(HASHIRAMA_HP);
                if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            }
            return;
        }
        if (!hp.hasModifier(HASHIRAMA_HP)) {
            hp.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HASHIRAMA_HP,
                    SharinganData.HASHIRAMA_EXTRA_HEALTH,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        // Pressa I "infinita": renovada sempre que estiver acabando (nao atrapalha uma Pressa melhor de outra fonte)
        MobEffectInstance haste = p.getEffect(MobEffects.DIG_SPEED);
        if (haste == null || (haste.getAmplifier() == 0 && haste.getDuration() < 100)) {
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 0, true, false, false));
        }
        // 1% de desgaste ocular por minuto (em tempo real). Quem ja ficou cego nao recupera.
        if (d.strain > 0f && !d.blind) {
            d.strain = Math.max(0f, d.strain
                    - SharinganData.HASHIRAMA_STRAIN_PER_MINUTE / 60f / Math.max(1f, appliedRate));
        }
    }

    /** Clique direito com a Celula de Hashirama: o primeiro clique avisa, o segundo implanta (permanente). */
    public static boolean tryImplantHashirama(ServerPlayer p) {
        SharinganData d = SharinganData.get(p);
        if (d.hashirama) {
            msg(p, Tx.t(ChatFormatting.RED, "hash.already"));
            return false;
        }
        long now = System.currentTimeMillis();
        Long armed = HASHIRAMA_ARMED.get(p.getUUID());
        if (armed == null || now - armed > HASHIRAMA_CONFIRM_MS) {
            HASHIRAMA_ARMED.put(p.getUUID(), now);
            chat(p, Tx.t(ChatFormatting.GOLD, "hash.warn", HASHIRAMA_CONFIRM_MS / 1000).withStyle(ChatFormatting.BOLD));
            return false;
        }
        HASHIRAMA_ARMED.remove(p.getUUID());
        d.hashirama = true;
        hashiramaTick(p, d);
        p.heal((float) SharinganData.HASHIRAMA_EXTRA_HEALTH);
        chat(p, Tx.t(ChatFormatting.GREEN, "hash.done").withStyle(ChatFormatting.BOLD));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.3f);
        Net.sync(p);
        return true;
    }

    /** Comando de administrador: tira as Celulas (a unica forma de remove-las). */
    public static void removeHashirama(ServerPlayer p) {
        SharinganData d = SharinganData.get(p);
        d.hashirama = false;
        hashiramaTick(p, d);
        chat(p, Tx.t(ChatFormatting.YELLOW, "hash.removed"));
        Net.sync(p);
    }

    public static void giveHashirama(ServerPlayer p) {
        SharinganData d = SharinganData.get(p);
        d.hashirama = true;
        hashiramaTick(p, d);
        Net.sync(p);
    }

    /** Tick rate atual aplicado pelo mod (usado pelo HUD no servidor integrado). */
    public static float appliedRate() {
        return appliedRate;
    }

    private static void suctionTick(ServerPlayer p, SharinganData d) {
        d.suctionTicks--;
        // ponto de sucção bem a frente do jogador: um creeper puxado nao chega perto o bastante para explodir
        Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(6.5));
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(14))) {
            if (!(e instanceof LivingEntity le) || isPet(e, p)) continue;
            Vec3 pull = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double dist = pull.length();
            if (dist < 0.5) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.35)));
            e.hurtMarked = true;
            if (dist < 3.5 && p.tickCount % 10 == 0) le.hurt(p.damageSources().indirectMagic(p, p), 8f);
        }
        p.serverLevel().sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 25, 1.2, 1.2, 1.2, 0.3);
        suctionSpiral(p, c);
    }

    /** Espiral giratoria de particulas ao redor do ponto de succao (3 bracos que giram e se fecham). */
    private static void suctionSpiral(ServerPlayer p, Vec3 c) {
        Vec3 look = p.getLookAngle();
        Vec3 u = look.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = look.cross(u).normalize();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.55f, 0.1f, 0.85f), 1.2f);
        double base = p.tickCount * 0.35;
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 14; k++) {
                double r = 0.3 + k * 0.28;
                double a = base + arm * (Math.PI * 2 / 3) + k * 0.45;
                Vec3 pt = c.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r)).add(look.scale(-k * 0.12));
                p.serverLevel().sendParticles(dust, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                if (k % 4 == 0) p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0.02);
            }
        }
    }

    // ---------- Kotoamatsukami (controle de mobs) ----------
    public static void charmTick(MinecraftServer server) {
        if (CHARM.isEmpty() || server.getTickCount() % 10 != 0) return;
        Iterator<Map.Entry<UUID, Integer>> it = CHARM.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            if (en.getValue() < Integer.MAX_VALUE) en.setValue(en.getValue() - 10);
            Entity ent = null;
            for (ServerLevel l : server.getAllLevels()) {
                ent = l.getEntity(en.getKey());
                if (ent != null) break;
            }
            if (!(ent instanceof Mob m) || !m.isAlive() || en.getValue() <= 0) {
                it.remove();
                continue;
            }
            Monster target = m.level().getEntitiesOfClass(Monster.class, m.getBoundingBox().inflate(16),
                            x -> x != m && x.isAlive() && !isPermanentAlly(x))
                    .stream().min(Comparator.comparingDouble((Monster x) -> m.distanceToSqr(x))).orElse(null);
            m.setTarget(target);
            // nunca foca jogador algum (nem quem lhe lancou o genjutsu, nem quem o feriu)
            if (m.getLastHurtByMob() instanceof net.minecraft.world.entity.player.Player) m.setLastHurtByMob(null);
        }
    }

    /** Aliado para sempre (Kotoamatsukami). */
    public static boolean isPermanentAlly(Entity e) {
        return Integer.valueOf(Integer.MAX_VALUE).equals(CHARM.get(e.getUUID())) || e.getTags().contains(ALLY_TAG);
    }

    /** Mob sob Kotoamatsukami/genjutsu: nao pode ter jogador como alvo. */
    public static boolean isCharmed(Entity e) {
        return CHARM.containsKey(e.getUUID()) || e.getTags().contains(ALLY_TAG);
    }

    /** Morte do jogador: desliga o Sharingan/Mangekyou/Susanoo e zera recargas e habilidades em andamento. */
    public static void resetOnDeath(ServerPlayer p) {
        SharinganData d = SharinganData.get(p);
        deactivateAll(p, d);
        d.susanooOn = false;
        d.susanooTicks = 0;
        d.intangibleTicks = 0;
        d.suctionTicks = 0;
        d.regenDelay = 0;
        d.dodgeCd = 0f;
        DODGE_SHIELD.remove(p.getUUID());
        HASHIRAMA_ARMED.remove(p.getUUID());
        for (int i = 0; i < d.cooldowns.length; i++) d.cooldowns[i] = 0;
        LAST_FACE.remove(p.getUUID());
        PacketDistributor.sendToAllPlayers(new FacePayload(p.getUUID(), 0));
        Net.sync(p);
    }

    public static void onRespawn(ServerPlayer p) {
        SharinganData d = SharinganData.get(p);
        d.sharinganOn = false;
        d.mangekyouOn = false;
        d.susanooOn = false;
        d.susanooTicks = 0;
        d.intangibleTicks = 0;
        d.suctionTicks = 0;
        for (int i = 0; i < d.cooldowns.length; i++) d.cooldowns[i] = 0;
        Net.sync(p);
    }
}
