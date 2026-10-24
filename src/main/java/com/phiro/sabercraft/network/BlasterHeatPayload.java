package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * The blaster heat of a player (0..100) and whether the blaster is overheated, sent by the server
 * to that player when it changes (Phase 10). Only for the HUD; the server keeps the real value.
 */
public record BlasterHeatPayload(float heat, boolean overheated) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlasterHeatPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("blaster_heat"));
	public static final StreamCodec<ByteBuf, BlasterHeatPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, BlasterHeatPayload::heat,
			ByteBufCodecs.BOOL, BlasterHeatPayload::overheated,
			BlasterHeatPayload::new
	);

	@Override
	public CustomPacketPayload.Type<BlasterHeatPayload> type() {
		return TYPE;
	}
}
