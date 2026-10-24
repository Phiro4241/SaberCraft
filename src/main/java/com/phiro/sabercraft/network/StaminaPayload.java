package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * The stamina of a player, sent by the server to that player when it changes. Only for the HUD
 * and to hold back requests the server would refuse; the server keeps the real value.
 */
public record StaminaPayload(float stamina) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaminaPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("stamina"));
	public static final StreamCodec<ByteBuf, StaminaPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, StaminaPayload::stamina,
			StaminaPayload::new
	);

	@Override
	public CustomPacketPayload.Type<StaminaPayload> type() {
		return TYPE;
	}
}
