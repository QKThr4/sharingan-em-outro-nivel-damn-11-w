package com.sharinganmod.net;

import com.sharinganmod.SharinganMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cor (RGB, 0xRRGGBB) do Susanoo escolhida pelo jogador, enviada ao servidor. */
public record SusanooColorPayload(int rgb) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SusanooColorPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SharinganMod.MODID, "susanoo_color"));
    public static final StreamCodec<ByteBuf, SusanooColorPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(SusanooColorPayload::new, SusanooColorPayload::rgb);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
