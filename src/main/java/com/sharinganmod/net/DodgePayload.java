package com.sharinganmod.net;

import com.sharinganmod.SharinganMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor para os clientes que veem o jogador: ele acabou de desviar de um golpe (mostra o afterimage). */
public record DodgePayload(UUID uuid) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DodgePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "dodge"));
    public static final StreamCodec<ByteBuf, DodgePayload> CODEC =
            StreamCodec.composite(UUIDUtil.STREAM_CODEC, DodgePayload::uuid, DodgePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
