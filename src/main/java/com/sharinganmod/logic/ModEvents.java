package com.sharinganmod.logic;

import com.sharinganmod.data.Tx;
import com.sharinganmod.SharinganMod;
import com.sharinganmod.data.SharinganData;
import com.sharinganmod.net.Net;
import com.sharinganmod.registry.ModRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SharinganMod.MODID)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) SharinganLogic.tick(sp);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        SharinganLogic.charmTick(e.getServer());
        Crows.tick(e.getServer());
        Abilities.tsukuyomiTick(e.getServer());
        SharinganLogic.updateTickRate(e.getServer());
    }

    /** Kamui: intangibilidade bloqueia todo o dano; a esquiva do Sharingan desvia de golpes (com chance). */
    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent e) {
        SharinganLogic.counter(e);
        if (e.getEntity() instanceof ServerPlayer sp) {
            if (SharinganData.get(sp).intangibleTicks > 0 || SharinganLogic.tryDodge(sp, e.getSource())) {
                e.setCanceled(true);
            }
        }
    }

    /** Despertar do Sharingan: sobreviver a um golpe quase fatal. */
    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            SharinganData d = SharinganData.get(sp);
            float chance = d.uchiha ? SharinganData.AWAKEN_CHANCE_UCHIHA : SharinganData.AWAKEN_CHANCE_OTHER;
            // so quem pertence ao Cla Uchiha pode despertar o Sharingan
            if (d.uchiha && d.stage == 0 && !d.awakened && sp.getHealth() > 0 && sp.getHealth() <= 4f
                    && sp.getRandom().nextFloat() < chance) {
                SharinganLogic.awaken(sp, d);
            }
        }
    }

    /** Roda por ultimo (LOWEST) e so se a morte realmente aconteceu (nao foi cancelada, ex.: Totem). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        // ao morrer: Sharingan desligado e todas as habilidades resetadas
        if (victim instanceof ServerPlayer dead) SharinganLogic.resetOnDeath(dead);

        // Mangekyou: o DONO do pet recebe quando o pet morre de qualquer jeito e por qualquer motivo
        // (so o dono: quem mata o pet de outro jogador nao recebe nada)
        ServerPlayer owner = SharinganLogic.tamedOwner(victim);
        if (owner != null) {
            // a morte de um mob domesticado tambem pode despertar o Sharingan pela primeira vez (so Uchiha)
            SharinganData od = SharinganData.get(owner);
            if (od.uchiha && od.stage == 0 && !od.awakened && owner.getHealth() > 0) SharinganLogic.awaken(owner, od);
            tryMangekyou(owner, victim);
        }

        // XP do Sharingan por matar inimigos com ele ativo
        if (e.getSource().getEntity() instanceof ServerPlayer sp) {
            SharinganData d = SharinganData.get(sp);
            if (d.eyesActive() && victim instanceof Enemy) SharinganLogic.killXp(sp, d, 5);
        }
    }

    /** Com 3 tomoe, a morte do proprio pet domesticado da um Mangekyou aleatorio. Retorna true se deu. */
    private static boolean tryMangekyou(ServerPlayer p, LivingEntity victim) {
        SharinganData d = SharinganData.get(p);
        if (d.stage != 3) return false;
        int type = SharinganLogic.mangekyouTypeFor(victim);
        if (type <= 0) return false;
        SharinganLogic.awakenMangekyou(p, d, type);
        return true;
    }

    // O XP vanilla (orbes de experiencia) NAO da XP do Sharingan: nem tomoes, nem pontos de habilidade.

    /** Mob sob Kotoamatsukami/genjutsu nunca escolhe um jogador como alvo (tira o foco totalmente). */
    @SubscribeEvent
    public static void changeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
        if (e.getEntity() instanceof Mob m && e.getNewAboutToBeSetTarget() instanceof net.minecraft.world.entity.player.Player
                && SharinganLogic.isCharmed(m)) {
            e.setNewAboutToBeSetTarget(null);
        }
    }

    /** Mob dominado pelo Kotoamatsukami volta a ser aliado ao carregar de novo (reiniciar o mundo, mudar de chunk). */
    @SubscribeEvent
    public static void allyLoaded(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Mob m
                && m.getTags().contains(SharinganLogic.ALLY_TAG)) {
            SharinganLogic.CHARM.put(m.getUUID(), Integer.MAX_VALUE);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        SharinganData d = SharinganData.get(sp);
        if (!d.kit) {
            d.kit = true;
            SharinganLogic.give(sp, new ItemStack(ModRegistries.UCHIHA_EMBLEM.get()));
            SharinganLogic.chat(sp, Tx.t(net.minecraft.ChatFormatting.RED, "kit"));
        }
        SharinganLogic.unlockRecipes(sp);
        SharinganLogic.setNoCollide(sp, false);
        SharinganLogic.forgetFace(sp);
        SharinganLogic.sendFacesTo(sp);
        SharinganLogic.sendLayoutsTo(sp);
        Net.sync(sp);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            SharinganLogic.setNoCollide(sp, false);
            SharinganLogic.forgetFace(sp);
            SharinganLogic.forgetLayout(sp);
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent e) {
        SharinganLogic.clearAll();
        Crows.clear();
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) SharinganLogic.onRespawn(sp);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Net.sync(sp);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        SharinganCommands.register(e.getDispatcher());
    }
}
