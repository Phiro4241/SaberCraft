package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbility;

/**
 * Sent by the client when an ability key is pressed, and for the defensive stance also when it
 * is released. The server checks everything itself, this is only a request.
 */
public record UseAbilityPayload(SaberAbility ability, boolean pressed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UseAbilityPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("use_ability"));
	public static final StreamCodec<ByteBuf, UseAbilityPayload> CODEC = StreamCodec.composite(
			SaberAbility.STREAM_CODEC, UseAbilityPayload::ability,
			ByteBufCodecs.BOOL, UseAbilityPayload::pressed,
			UseAbilityPayload::new
	);

	@Override
	public CustomPacketPayload.Type<UseAbilityPayload> type() {
		return TYPE;
	}
}
