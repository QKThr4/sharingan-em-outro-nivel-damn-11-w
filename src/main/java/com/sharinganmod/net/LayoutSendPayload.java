package com.sharinganmod.net;

import com.sharinganmod.SharinganMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente para servidor: como o jogador configurou os olhos no proprio rosto (o servidor repassa aos outros). */
public record LayoutSendPayload(float dx, float y, float half, float dx2, float y2, boolean front) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LayoutSendPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "layout_send"));
    public static final StreamCodec<ByteBuf, LayoutSendPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, LayoutSendPayload::dx,
            ByteBufCodecs.FLOAT, LayoutSendPayload::y,
            ByteBufCodecs.FLOAT, LayoutSendPayload::half,
            ByteBufCodecs.FLOAT, LayoutSendPayload::dx2,
            ByteBufCodecs.FLOAT, LayoutSendPayload::y2,
            ByteBufCodecs.BOOL, LayoutSendPayload::front,
            LayoutSendPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
