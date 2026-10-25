package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * The pilot pulls the trigger (Phase 11). Carries nothing: the server knows which ship the player
 * sits in, aims along its nose and keeps the rate of fire.
 */
public record ShipFirePayload() implements CustomPacketPayload {
	public static final ShipFirePayload INSTANCE = new ShipFirePayload();
	public static final CustomPacketPayload.Type<ShipFirePayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ship_fire"));
	public static final StreamCodec<ByteBuf, ShipFirePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<ShipFirePayload> type() {
		return TYPE;
	}
}
