package com.sharinganmod.net;

import com.sharinganmod.SharinganMod;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor para UM cliente: destaca (com contorno, mesmo atras de paredes) as entidades 'ids' por 'seconds' segundos. */
public record RevealPayload(List<Integer> ids, int seconds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RevealPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "reveal"));
    public static final StreamCodec<ByteBuf, RevealPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RevealPayload::ids,
            ByteBufCodecs.VAR_INT, RevealPayload::seconds,
            RevealPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
