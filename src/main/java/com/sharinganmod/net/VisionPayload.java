package com.sharinganmod.net;

import com.sharinganmod.SharinganMod;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor para UM cliente com Sharingan: chakra (0-100) das entidades proximas; pct negativo = vai atacar agora. */
public record VisionPayload(List<Integer> ids, List<Integer> pcts) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VisionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "vision"));
    public static final StreamCodec<ByteBuf, VisionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), VisionPayload::ids,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), VisionPayload::pcts,
            VisionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
