package com.phiro.sabercraft.ability;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The special abilities of a lit saber. The server decides about all of them: it checks the
 * cooldown, deals the damage, moves the entities and tells the clients what happened.
 */
public enum SaberAbility {
	/** Defensive stance while the key is held. Blocks frontal hits, a hit right after raising it is parried. */
	GUARD("guard"),
	/** Spinning attack around the player; with the double-bladed staff a whirl in front of the body. */
	SPIN("spin"),
	/** Pushes nearby enemies and projectiles in front away. */
	PUSH("push"),
	/** Short lunge forward. */
	DASH("dash");

	public static final StreamCodec<ByteBuf, SaberAbility> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

	private final String key;

	SaberAbility(String key) {
		this.key = key;
	}

	/** Short name, used for translation keys. */
	public String key() {
		return this.key;
	}
}
