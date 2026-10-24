package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbility;

/**
 * Sent by the server to the user of an ability and to every player who can see them, so all
 * clients play the same animation, sound and particles.
 *
 * @param entityId id of the entity that used the ability
 * @param ability  which ability
 * @param event    what happened, see {@link Event}
 */
public record AbilityEventPayload(int entityId, SaberAbility ability, Event event) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilityEventPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ability_event"));
	public static final StreamCodec<ByteBuf, AbilityEventPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AbilityEventPayload::entityId,
			SaberAbility.STREAM_CODEC, AbilityEventPayload::ability,
			Event.STREAM_CODEC, AbilityEventPayload::event,
			AbilityEventPayload::new
	);

	public enum Event {
		/** The ability starts (stance raised, spin, push, dash). */
		START,
		/** The defensive stance is lowered. */
		STOP,
		/** A hit was blocked by the stance. */
		BLOCK,
		/** A hit was parried at the right moment. */
		PARRY;

		public static final StreamCodec<ByteBuf, Event> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);
	}

	@Override
	public CustomPacketPayload.Type<AbilityEventPayload> type() {
		return TYPE;
	}
}
