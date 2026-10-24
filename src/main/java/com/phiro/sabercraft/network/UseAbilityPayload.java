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
 *
 * @param data extra input of the ability: for the dodge the {@code DodgeDirection} ordinal
 */
public record UseAbilityPayload(SaberAbility ability, boolean pressed, int data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UseAbilityPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("use_ability"));
	public static final StreamCodec<ByteBuf, UseAbilityPayload> CODEC = StreamCodec.composite(
			SaberAbility.STREAM_CODEC, UseAbilityPayload::ability,
			ByteBufCodecs.BOOL, UseAbilityPayload::pressed,
			ByteBufCodecs.VAR_INT, UseAbilityPayload::data,
			UseAbilityPayload::new
	);

	@Override
	public CustomPacketPayload.Type<UseAbilityPayload> type() {
		return TYPE;
	}
}
