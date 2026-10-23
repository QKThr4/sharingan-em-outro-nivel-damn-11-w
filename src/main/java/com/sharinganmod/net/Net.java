package com.sharinganmod.net;

import com.sharinganmod.SharinganMod;
import com.sharinganmod.client.ClientFace;
import com.sharinganmod.client.ClientReveal;
import com.sharinganmod.data.SharinganData;
import com.sharinganmod.logic.SharinganLogic;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = SharinganMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class Net {
    private Net() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("7");
        reg.playToClient(SyncPayload.TYPE, SyncPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientNet.handleSync(payload)));
        reg.playToClient(FacePayload.TYPE, FacePayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientFace.set(payload.uuid(), payload.code())));
        reg.playToClient(LayoutPayload.TYPE, LayoutPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(
                        () -> ClientFace.setLayout(payload.uuid(), payload.dx(), payload.y(), payload.half(), payload.dx2(), payload.y2(), payload.front())));
        reg.playToClient(RevealPayload.TYPE, RevealPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientReveal.add(payload.ids(), payload.seconds())));
        reg.playToClient(DodgePayload.TYPE, DodgePayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.sharinganmod.client.DodgeGhosts.add(payload.uuid())));
        reg.playToClient(VisionPayload.TYPE, VisionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.sharinganmod.client.ClientVision.setVision(payload.ids(), payload.pcts())));
        reg.playToServer(LayoutSendPayload.TYPE, LayoutSendPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        SharinganLogic.setLayout(sp, payload.dx(), payload.y(), payload.half(), payload.dx2(), payload.y2(), payload.front());
                    }
                }));
        reg.playToServer(SusanooColorPayload.TYPE, SusanooColorPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        SharinganData.get(sp).susanooColor = payload.rgb() & 0xFFFFFF;
                        sync(sp);
                    }
                }));
        reg.playToServer(ActionPayload.TYPE, ActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        SharinganLogic.handleAction(sp, payload.action());
                    }
                }));
    }

    public static void sync(ServerPlayer sp) {
        SharinganData d = SharinganData.get(sp);
        PacketDistributor.sendToPlayer(sp, new SyncPayload(d.serializeNBT(sp.level().registryAccess())));
    }
}
