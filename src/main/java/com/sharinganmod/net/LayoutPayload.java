package com.sharinganmod.net;

import com.sharinganmod.SharinganMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor para clientes: como o dono 'uuid' configurou o tamanho/posicao dos olhos no proprio rosto. */
public record LayoutPayload(UUID uuid, float dx, float y, float half, float dx2, float y2, boolean front) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LayoutPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "layout"));
    public static final StreamCodec<ByteBuf, LayoutPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, v.uuid());
                buf.writeFloat(v.dx());
                buf.writeFloat(v.y());
                buf.writeFloat(v.half());
                buf.writeFloat(v.dx2());
                buf.writeFloat(v.y2());
                buf.writeBoolean(v.front());
            },
            buf -> new LayoutPayload(UUIDUtil.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
