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
 * @param data     extra information: for the dodge the {@code DodgeDirection} ordinal, for the
 *                 grip and the pull the id of the target entity; for a block, a parry and a
 *                 broken stance the id of the attacker plus one, 0 if there is none (Phase 10)
 */
public record AbilityEventPayload(int entityId, SaberAbility ability, Event event, int data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilityEventPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ability_event"));
	public static final StreamCodec<ByteBuf, AbilityEventPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AbilityEventPayload::entityId,
			SaberAbility.STREAM_CODEC, AbilityEventPayload::ability,
			Event.STREAM_CODEC, AbilityEventPayload::event,
			ByteBufCodecs.VAR_INT, AbilityEventPayload::data,
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
		PARRY,
		/** A melee hit missed because the player was rolling or stepping out of the way. */
		EVADE,
		/** A block took more than the stamina left: the stance breaks and the hit lands. */
		GUARD_BREAK,
		/** A parried attacker is thrown off balance. Sent with the attacker as the entity. */
		UNBALANCED,
		/** A projectile was sent back; data is 1 for a precise deflection along the look direction. */
		DEFLECT,
		/** The stun blaster in Ezra Bridger's hilt fired (Phase 10); the ability is not used. */
		SHOT;

		public static final StreamCodec<ByteBuf, Event> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);
	}

	public AbilityEventPayload(int entityId, SaberAbility ability, Event event) {
		this(entityId, ability, event, 0);
	}

	@Override
	public CustomPacketPayload.Type<AbilityEventPayload> type() {
		return TYPE;
	}
}
