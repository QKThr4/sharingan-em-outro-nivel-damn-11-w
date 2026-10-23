package com.sharinganmod.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Karasu Bunshin (Itachi): clones de corvo de verdade. Cada clone e um morcego preto sem IA que orbita o
 * dono e mergulha nos inimigos proximos por 10 s, causando dano e cegando, e depois some em fumaça.
 */
public final class Crows {
    private Crows() {}

    private static final int COUNT = 6;
    private static final int LIFETIME = 200;

    private static final class Crow {
        UUID bat;
        UUID owner;
        int ticks;
        double angle;
    }

    private static final List<Crow> CROWS = new ArrayList<>();

    /** Invoca os clones de corvo ao redor do jogador. Retorna quantos foram criados. */
    public static int spawn(ServerPlayer p) {
        removeOf(p.getUUID(), p.getServer());
        ServerLevel l = p.serverLevel();
        int made = 0;
        for (int i = 0; i < COUNT; i++) {
            Bat bat = EntityType.BAT.create(l);
            if (bat == null) continue;
            double a = i * (Math.PI * 2 / COUNT);
            bat.moveTo(p.getX() + Math.cos(a) * 2.2, p.getY() + 1.4, p.getZ() + Math.sin(a) * 2.2, 0f, 0f);
            bat.setNoAi(true);
            bat.setNoGravity(true);
            bat.setInvulnerable(true);
            bat.setSilent(true);
            bat.setResting(false);
            bat.setPersistenceRequired();
            l.addFreshEntity(bat);
            Crow c = new Crow();
            c.bat = bat.getUUID();
            c.owner = p.getUUID();
            c.angle = a;
            CROWS.add(c);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, bat.getX(), bat.getY(), bat.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            made++;
        }
        return made;
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    private static void kill(MinecraftServer server, Crow c) {
        Entity e = find(server, c.bat);
        if (e != null && e.level() instanceof ServerLevel l) {
            l.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.3, e.getZ(), 8, 0.25, 0.25, 0.25, 0.02);
            e.discard();
        }
    }

    private static void removeOf(UUID owner, MinecraftServer server) {
        if (server == null) return;
        Iterator<Crow> it = CROWS.iterator();
        while (it.hasNext()) {
            Crow c = it.next();
            if (c.owner.equals(owner)) {
                kill(server, c);
                it.remove();
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (CROWS.isEmpty()) return;
        Iterator<Crow> it = CROWS.iterator();
        while (it.hasNext()) {
            Crow c = it.next();
            Entity be = find(server, c.bat);
            ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
            c.ticks++;
            if (!(be instanceof Bat bat) || !bat.isAlive() || owner == null || !owner.isAlive()
                    || bat.level() != owner.level() || c.ticks > LIFETIME) {
                kill(server, c);
                it.remove();
                continue;
            }
            LivingEntity target = null;
            double best = 14 * 14;
            for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class,
                    owner.getBoundingBox().inflate(14), x -> x != owner && x.isAlive() && x instanceof Enemy
                            && !SharinganLogic.isPet(x, owner))) {
                double dd = e.distanceToSqr(owner);
                if (dd < best) {
                    best = dd;
                    target = e;
                }
            }
            Vec3 pos = bat.position();
            Vec3 dest;
            if (target != null) {
                dest = target.position().add(0, target.getBbHeight() * 0.6, 0);
            } else {
                c.angle += 0.22;
                dest = owner.position().add(Math.cos(c.angle) * 2.2, 1.3 + Math.sin(c.angle * 0.5) * 0.6,
                        Math.sin(c.angle) * 2.2);
            }
            Vec3 d = dest.subtract(pos);
            double dist = d.length();
            double step = target != null ? 0.85 : 0.6;
            Vec3 np = dist > step ? pos.add(d.scale(step / dist)) : dest;
            float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
            bat.moveTo(np.x, np.y, np.z, yaw, 0f);
            if (c.ticks % 4 == 0 && bat.level() instanceof ServerLevel l) {
                l.sendParticles(ParticleTypes.SMOKE, np.x, np.y, np.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            if (target != null && dist < 1.6 && c.ticks % 8 == 0) {
                target.hurt(owner.damageSources().indirectMagic(owner, owner), 3f);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                if (target instanceof net.minecraft.world.entity.Mob m) m.setTarget(null);
            }
        }
    }

    public static void clear() {
        CROWS.clear();
    }
}
