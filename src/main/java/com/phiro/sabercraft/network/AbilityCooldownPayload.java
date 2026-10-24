package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbility;

/**
 * Tells a player that one of their abilities is cooling down, for the cooldown display. The server
 * keeps the real cooldown; this is only for showing it.
 *
 * @param ticks how many ticks are left
 * @param total the full cooldown in ticks, for the progress bar
 */
public record AbilityCooldownPayload(SaberAbility ability, int ticks, int total) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilityCooldownPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ability_cooldown"));
	public static final StreamCodec<ByteBuf, AbilityCooldownPayload> CODEC = StreamCodec.composite(
			SaberAbility.STREAM_CODEC, AbilityCooldownPayload::ability,
			ByteBufCodecs.VAR_INT, AbilityCooldownPayload::ticks,
			ByteBufCodecs.VAR_INT, AbilityCooldownPayload::total,
			AbilityCooldownPayload::new
	);

	@Override
	public CustomPacketPayload.Type<AbilityCooldownPayload> type() {
		return TYPE;
	}
}
