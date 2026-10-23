package com.sharinganmod.logic;

import com.sharinganmod.data.Tx;
import com.sharinganmod.net.RevealPayload;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.network.PacketDistributor;
import com.sharinganmod.data.SharinganData;
import com.sharinganmod.data.Skills;
import com.sharinganmod.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Habilidades dos Mangekyou. Cada uso gasta chakra, entra em recarga e desgasta os olhos. */
public final class Abilities {
    private Abilities() {}

    public static void use(ServerPlayer p, SharinganData d, int slot) {
        if (d.stage < 4) { SharinganLogic.msg(p, Tx.t("need.mk")); return; }
        if (!d.mangekyouOn) {
            SharinganLogic.msg(p, Tx.t("need.mk.on", SharinganLogic.key("mangekyou")));
            return;
        }
        if (slot == 3) { toggleSusanoo(p, d); return; }
        if (d.cooldowns[slot] > 0) {
            SharinganLogic.msg(p, Tx.t("cooldown", Skills.name(d.type, slot), d.cooldowns[slot] / 20 + 1));
            return;
        }
        float cost = Skills.CHAKRA[d.type][slot] * d.penalty();
        if (d.chakra < cost) {
            SharinganLogic.msg(p, Tx.t("chakra.low", (int) d.chakra, (int) Math.ceil(cost)));
            return;
        }
        boolean ok = switch (d.type) {
            case 1 -> switch (slot) {
                case 0 -> tsukuyomi(p);
                case 1 -> amaterasu(p);
                default -> karasu(p);
            };
            case 2 -> switch (slot) {
                case 0 -> chidori(p);
                case 1 -> kagutsuchi(p);
                default -> kirin(p);
            };
            case 3 -> switch (slot) {
                case 0 -> kamuiIntangible(p, d);
                case 1 -> kamuiTeleport(p);
                default -> kamuiSuction(p, d);
            };
            case 4 -> switch (slot) {
                case 0 -> kotoamatsukami(p);
                case 1 -> espelhoIlusorio(p);
                default -> olhar(p);
            };
            case 5 -> switch (slot) {
                case 0 -> tsukuyomi(p);
                case 1 -> kagutsuchi(p);
                default -> kirin(p);
            };
            case 6, 8 -> switch (slot) {
                case 0 -> gokakyu(p);
                case 1 -> tengaiShinsei(p);
                default -> gunbai(p);
            };
            case 7 -> switch (slot) {
                case 0 -> hinomai(p);
                case 1 -> genjutsuSombrio(p);
                default -> goryuka(p);
            };
            default -> false;
        };
        if (ok) {
            d.chakra -= cost;
            d.cooldowns[slot] = Math.round(Skills.COOLDOWN[d.type][slot] * d.penalty() * (d.halfEye ? 1.5f : 1f));
            SharinganLogic.addStrain(p, d, Skills.STRAIN[d.type][slot]);
        }
    }

    private static void toggleSusanoo(ServerPlayer p, SharinganData d) {
        if (d.susanooOn) {
            SharinganLogic.endSusanoo(p, d);
            return;
        }
        if (d.eyeSide != 0) {
            SharinganLogic.msg(p, Tx.t("susanoo.eyes"));
            return;
        }
        if (d.cooldowns[3] > 0) {
            SharinganLogic.msg(p, Tx.t("cooldown", Skills.name(d.type, 3), d.cooldowns[3] / 20 + 1));
            return;
        }
        float cost = Skills.SUSANOO_COST * d.penalty();
        if (d.chakra < cost) {
            SharinganLogic.msg(p, Tx.t("chakra.low", (int) d.chakra, (int) Math.ceil(cost)));
            return;
        }
        d.chakra -= cost;
        SharinganLogic.startSusanoo(p, d);
        SharinganLogic.addStrain(p, d, Skills.SUSANOO_STRAIN_ON_START);
    }

    // ---------- helpers ----------
    private static LivingEntity rayEntity(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        BlockHitResult br = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double max = br.getType() == HitResult.Type.MISS ? range : br.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(max));
        AABB box = p.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0);
        EntityHitResult r = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != p, max * max);
        return r != null && r.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static Vec3 rayPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult br = p.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return br.getType() == HitResult.Type.MISS ? end : br.getLocation();
    }

    /** Teletransporta para perto de 'dest' (ponto na altura dos olhos) se houver espaco livre. */
    private static boolean teleportSafe(ServerPlayer p, Vec3 dest) {
        for (double dy : new double[]{-1.62, -1.0, 0, 0.5, 1.0, 2.0}) {
            AABB bb = p.getBoundingBox().move(dest.x - p.getX(), dest.y + dy - p.getY(), dest.z - p.getZ());
            if (p.level().noCollision(p, bb)) {
                p.connection.teleport(dest.x, dest.y + dy, dest.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
                return true;
            }
        }
        SharinganLogic.msg(p, Tx.t("tp.nospace"));
        return false;
    }

    private static void snd(ServerPlayer p, SoundEvent s, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, 1f, pitch);
    }

    private static void part(ServerLevel l, ParticleOptions o, Vec3 c, int n, double spread) {
        l.sendParticles(o, c.x, c.y, c.z, n, spread, spread, spread, 0.02);
    }

    private static boolean notAlly(LivingEntity e, ServerPlayer p) {
        return e != p && e.isAlive() && !SharinganLogic.isPet(e, p);
    }

    // ---------- Itachi / Eterno ----------
    /** Duracao do Tsukuyomi (20 s) e dos efeitos que ficam na vitima depois dele (1 min). */
    private static final int TSUKUYOMI_TICKS = 400;
    private static final int TSUKUYOMI_AFTER_TICKS = 1200;

    private record Pending(LivingEntity target, int atTick) {
    }

    private static final List<Pending> AFTER_TSUKUYOMI = new ArrayList<>();

    /** Chamado todo tick do servidor: aplica os efeitos finais nas vitimas cujo Tsukuyomi acabou. */
    public static void tsukuyomiTick(net.minecraft.server.MinecraftServer server) {
        if (AFTER_TSUKUYOMI.isEmpty()) return;
        int now = server.getTickCount();
        java.util.Iterator<Pending> it = AFTER_TSUKUYOMI.iterator();
        while (it.hasNext()) {
            Pending pe = it.next();
            if (pe.target().isRemoved() || !pe.target().isAlive()) {
                it.remove();
                continue;
            }
            if (now >= pe.atTick()) {
                LivingEntity t = pe.target();
                t.removeEffect(MobEffects.BLINDNESS);
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TSUKUYOMI_AFTER_TICKS, 0));
                t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, TSUKUYOMI_AFTER_TICKS, 0));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TSUKUYOMI_AFTER_TICKS, 0));
                it.remove();
            }
        }
    }

    public static void clearPending() {
        AFTER_TSUKUYOMI.clear();
    }

    private static boolean tsukuyomi(ServerPlayer p) {
        LivingEntity t = rayEntity(p, 32);
        if (t == null) { SharinganLogic.msg(p, Tx.t("target.none")); return false; }
        t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, TSUKUYOMI_TICKS, 0));
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TSUKUYOMI_TICKS, 5));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TSUKUYOMI_TICKS, 2));
        t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, TSUKUYOMI_TICKS, 0));
        // depois dos 20 s do genjutsu: fraqueza, nausea e lentidao por 1 minuto
        AFTER_TSUKUYOMI.add(new Pending(t, p.getServer().getTickCount() + TSUKUYOMI_TICKS));
        t.hurt(p.damageSources().indirectMagic(p, p), 8f);
        if (t instanceof Mob m) m.setTarget(null);
        part(p.serverLevel(), ParticleTypes.SMOKE, t.position().add(0, t.getBbHeight() / 2, 0), 40, 0.5);
        snd(p, SoundEvents.ENDERMAN_STARE, 0.5f);
        return true;
    }

    /** Duracao do Amaterasu (Itachi e Sasuke): 30 segundos. */
    private static final int AMATERASU_TICKS = 600;

    /** Amaterasu do Itachi: raio de chamas negras em linha reta, ate 52 blocos. */
    private static boolean amaterasu(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        for (int i = 1; i <= 40; i++) {
            Vec3 pt = eye.add(look.scale(i));
            BlockPos bp = BlockPos.containing(pt);
            if (!l.getBlockState(bp).getCollisionShape(l, bp).isEmpty()) break;
            part(l, ParticleTypes.SOUL_FIRE_FLAME, pt, 4, 0.25);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(pt, pt).inflate(1.2), x -> notAlly(x, p))) {
                e.addEffect(new MobEffectInstance(ModRegistries.AMATERASU, AMATERASU_TICKS, 0));
            }
        }
        snd(p, SoundEvents.BLAZE_SHOOT, 0.5f);
        return true;
    }

    private static boolean karasu(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Crows.spawn(p);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 120, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 2));
        part(l, ParticleTypes.LARGE_SMOKE, p.position().add(0, 1, 0), 80, 1.0);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6), x -> notAlly(x, p))) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            if (e instanceof Mob m) m.setTarget(null);
        }
        snd(p, SoundEvents.ENDERMAN_TELEPORT, 1.6f);
        return true;
    }

    // ---------- Sasuke ----------
    private static boolean chidori(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 start = p.position();
        AABB path = new AABB(start, start.add(look.scale(10))).inflate(1.5, 1.5, 1.5).move(0, 1, 0);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, path, x -> notAlly(x, p))) {
            e.hurt(p.damageSources().playerAttack(p), 20f);
        }
        p.setDeltaMovement(look.x * 2.6, look.y * 2.0 + 0.15, look.z * 2.6);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ServerLevel l = p.serverLevel();
        for (int i = 0; i < 12; i++) {
            part(l, ParticleTypes.ELECTRIC_SPARK, start.add(look.scale(i * 0.9)).add(0, 1, 0), 6, 0.3);
        }
        snd(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f);
        return true;
    }

    /** Enton: Kagutsuchi (Sasuke): chamas negras no ponto que o jogador olha (ate 32 blocos), numa area de 3,5 blocos. */
    private static boolean kagutsuchi(ServerPlayer p) {
        LivingEntity t = rayEntity(p, 42);
        Vec3 c = t != null ? t.position() : rayPoint(p, 42);
        ServerLevel l = p.serverLevel();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(3.5), x -> notAlly(x, p))) {
            e.addEffect(new MobEffectInstance(ModRegistries.AMATERASU, AMATERASU_TICKS, 0));
        }
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(a) * 3, c.y + 0.2, c.z + Math.sin(a) * 3, 3, 0.1, 0.4, 0.1, 0.02);
        }
        part(l, ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 0.5, 0), 40, 1.2);
        snd(p, SoundEvents.FIRECHARGE_USE, 0.5f);
        return true;
    }

    private static boolean kirin(ServerPlayer p) {
        LivingEntity t = rayEntity(p, 48);
        Vec3 target = t != null ? t.position() : rayPoint(p, 48);
        if (target.distanceTo(p.position()) < 6) {
            SharinganLogic.msg(p, Tx.t("kirin.near"));
            return false;
        }
        ServerLevel l = p.serverLevel();
        for (int i = 0; i < 3; i++) {
            LightningBolt b = EntityType.LIGHTNING_BOLT.create(l);
            if (b != null) {
                b.moveTo(target.x + (i == 0 ? 0 : (l.random.nextDouble() - 0.5) * 4), target.y,
                        target.z + (i == 0 ? 0 : (l.random.nextDouble() - 0.5) * 4));
                b.setCause(p);
                l.addFreshEntity(b);
            }
        }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(5), x -> notAlly(x, p))) {
            e.hurt(p.damageSources().indirectMagic(p, p), 25f);
        }
        return true;
    }

    // ---------- Madara / Eterno de Madara ----------
    /** Katon: bola de fogo gigante que explode no ponto mirado (ate 14 blocos). */
    private static boolean gokakyu(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 c = rayPoint(p, 14);
        double len = c.distanceTo(eye);
        for (int i = 1; i < len; i++) {
            part(l, ParticleTypes.FLAME, eye.add(look.scale(i)), 6, 0.35);
        }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4.5), x -> notAlly(x, p))) {
            e.hurt(p.damageSources().indirectMagic(p, p), 32f);
            e.setRemainingFireTicks(200);
        }
        part(l, ParticleTypes.FLAME, c, 120, 2.0);
        part(l, ParticleTypes.LAVA, c, 30, 1.5);
        part(l, ParticleTypes.LARGE_SMOKE, c.add(0, 1, 0), 40, 1.5);
        snd(p, SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.8f);
        return true;
    }

    /** Tengai Shinsei: chuva de meteoros sobre o alvo (ate 48 blocos). */
    private static boolean tengaiShinsei(ServerPlayer p) {
        LivingEntity t = rayEntity(p, 48);
        Vec3 target = t != null ? t.position() : rayPoint(p, 48);
        if (target.distanceTo(p.position()) < 8) {
            SharinganLogic.msg(p, Tx.t("meteor.near"));
            return false;
        }
        ServerLevel l = p.serverLevel();
        for (int i = 0; i < 8; i++) {
            double ox = (l.random.nextDouble() - 0.5) * 10;
            double oz = (l.random.nextDouble() - 0.5) * 10;
            Vec3 hit = target.add(ox, 0, oz);
            for (int h = 14; h > 0; h -= 2) {
                part(l, ParticleTypes.FLAME, hit.add(0, h, 0), 5, 0.2);
            }
            part(l, ParticleTypes.EXPLOSION, hit.add(0, 0.5, 0), 2, 0.6);
            part(l, ParticleTypes.LAVA, hit.add(0, 0.5, 0), 12, 1.0);
            part(l, ParticleTypes.LARGE_SMOKE, hit.add(0, 1, 0), 10, 1.0);
        }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(7), x -> notAlly(x, p))) {
            e.hurt(p.damageSources().indirectMagic(p, p), 22f);
            e.setRemainingFireTicks(200);
        }
        snd(p, SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.6f);
        return true;
    }

    /** Gunbai: golpe do leque de guerra: empurra e fere quem esta na frente e devolve os projeteis. */
    private static boolean gunbai(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 c = p.position().add(0, 1, 0).add(look.scale(3));
        AABB box = new AABB(c, c).inflate(4);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, x -> notAlly(x, p))) {
            e.hurt(p.damageSources().playerAttack(p), 16f);
            e.knockback(1.6, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        for (net.minecraft.world.entity.projectile.Projectile pr : l.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class, box.inflate(2), x -> true)) {
            pr.setOwner(p);
            double speed = Math.max(1.2, pr.getDeltaMovement().length());
            pr.setDeltaMovement(look.scale(speed));
            pr.hurtMarked = true;
        }
        part(l, ParticleTypes.SWEEP_ATTACK, c, 8, 1.5);
        part(l, ParticleTypes.CLOUD, c, 25, 1.5);
        snd(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f);
        return true;
    }

    // ---------- Izuna ----------
    /** Katon: Hinomai: explosao de chamas em aneis ao redor do jogador (raio 8): dano, fogo e empurrao. */
    private static boolean hinomai(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 c = p.position();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8), x -> notAlly(x, p))) {
            e.hurt(p.damageSources().indirectMagic(p, p), 24f);
            e.setRemainingFireTicks(200);
            e.knockback(1.4, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        for (int ring = 1; ring <= 4; ring++) {
            double r = ring * 2.0;
            for (int i = 0; i < 28; i++) {
                double a = i * Math.PI * 2 / 28;
                l.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 2, 0.1, 0.3, 0.1, 0.02);
            }
        }
        part(l, ParticleTypes.LARGE_SMOKE, c.add(0, 1, 0), 30, 1.5);
        snd(p, SoundEvents.BLAZE_SHOOT, 0.6f);
        return true;
    }

    /** Genjutsu: cega, atordoa e enfraquece os inimigos num raio de 10 blocos. */
    private static boolean genjutsuSombrio(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), x -> notAlly(x, p))) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 3));
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1));
            if (e instanceof Mob m) m.setTarget(null);
            part(l, ParticleTypes.SMOKE, e.position().add(0, e.getBbHeight() / 2, 0), 20, 0.4);
        }
        snd(p, SoundEvents.ENDERMAN_STARE, 0.7f);
        return true;
    }

    /** Katon: Goryuka: dragao de fogo em linha reta (ate 28 blocos). */
    private static boolean goryuka(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        for (int i = 1; i <= 28; i++) {
            Vec3 pt = eye.add(look.scale(i));
            BlockPos bp = BlockPos.containing(pt);
            if (!l.getBlockState(bp).getCollisionShape(l, bp).isEmpty()) break;
            part(l, ParticleTypes.FLAME, pt, 8, 0.5);
            part(l, ParticleTypes.LARGE_SMOKE, pt, 2, 0.4);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(pt, pt).inflate(2.0), x -> notAlly(x, p))) {
                if (hit.add(e)) {
                    e.hurt(p.damageSources().indirectMagic(p, p), 30f);
                    e.setRemainingFireTicks(200);
                }
            }
        }
        snd(p, SoundEvents.GHAST_SHOOT, 0.7f);
        return true;
    }

    // ---------- Obito ----------
    private static boolean kamuiIntangible(ServerPlayer p, SharinganData d) {
        d.intangibleTicks = 300;
        snd(p, SoundEvents.ENDERMAN_TELEPORT, 0.5f);
        return true;
    }

    private static boolean kamuiTeleport(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(64));
        BlockHitResult br = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 dest = br.getType() == HitResult.Type.MISS ? end : br.getLocation().subtract(look.scale(0.6));
        ServerLevel l = p.serverLevel();
        part(l, ParticleTypes.PORTAL, p.position().add(0, 1, 0), 60, 0.6);
        if (!teleportSafe(p, dest)) return false;
        part(l, ParticleTypes.PORTAL, p.position().add(0, 1, 0), 60, 0.6);
        snd(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f);
        return true;
    }

    private static boolean kamuiSuction(ServerPlayer p, SharinganData d) {
        d.suctionTicks = 80;
        snd(p, SoundEvents.ENDER_DRAGON_GROWL, 1.8f);
        return true;
    }

    // ---------- Shisui ----------
    private static boolean kotoamatsukami(ServerPlayer p) {
        LivingEntity t = rayEntity(p, 32);
        if (t == null) { SharinganLogic.msg(p, Tx.t("target.none")); return false; }
        if (t instanceof Mob m) {
            // aliado PARA SEMPRE: nunca expira, nao some por distancia e continua aliado depois de reiniciar o mundo
            SharinganLogic.CHARM.put(t.getUUID(), Integer.MAX_VALUE);
            m.addTag(SharinganLogic.ALLY_TAG);
            m.setPersistenceRequired();
            m.setTarget(null);
            m.setLastHurtByMob(null);
        } else {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 3));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 3));
            if (t instanceof ServerPlayer tp) SharinganLogic.msg(tp, Tx.t("koto.victim"));
        }
        // so quem usou ve o contorno do alvo
        PacketDistributor.sendToPlayer(p, new RevealPayload(List.of(t.getId()), 6));
        part(p.serverLevel(), ParticleTypes.ENCHANT, t.position().add(0, t.getBbHeight(), 0), 40, 0.5);
        snd(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1f);
        return true;
    }

    /**
     * Genjutsu: Espelho Ilusorio: os monstros num raio de 16 blocos ficam sob genjutsu por 10 s: nao enxergam o jogador
     * (nao o focam nem atacam) e atacam uns aos outros.
     */
    private static boolean espelhoIlusorio(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int n = 0;
        for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16),
                x -> notAlly(x, p) && x instanceof net.minecraft.world.entity.monster.Enemy)) {
            Integer cur = SharinganLogic.CHARM.get(m.getUUID());
            if (cur == null || cur < 200) SharinganLogic.CHARM.put(m.getUUID(), 200);
            m.setTarget(null);
            m.setLastHurtByMob(null);
            part(l, ParticleTypes.ENCHANT, m.position().add(0, m.getBbHeight(), 0), 25, 0.5);
            n++;
        }
        if (n == 0) {
            SharinganLogic.msg(p, Tx.t("target.none"));
            return false;
        }
        part(l, ParticleTypes.ENCHANT, p.position().add(0, 1, 0), 40, 1.2);
        snd(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f);
        return true;
    }

    /** Quanto tempo, em segundos reais, o Olhar Revelador destaca as entidades. */
    private static final int REVEAL_SECONDS = 45;

    private static boolean olhar(ServerPlayer p) {
        // revela as entidades ao redor SO para quem usou (outros jogadores nao veem) e por bem mais tempo
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(60), x -> x != p && x.isAlive())) {
            ids.add(e.getId());
        }
        PacketDistributor.sendToPlayer(p, new RevealPayload(ids, REVEAL_SECONDS));
        snd(p, SoundEvents.BEACON_ACTIVATE, 1.8f);
        return true;
    }
}
