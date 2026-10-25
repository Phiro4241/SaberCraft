package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * A finishing blow with a saber (Phase 11): the hit that defeats the target, or that ends a duel
 * round. Sent once by the server to the attacker and to everyone who sees the target, so every
 * client shows the flash, the shock ring and the sound exactly once.
 *
 * @param attackerId who struck
 * @param targetId   who was defeated
 */
public record SaberFinisherPayload(int attackerId, int targetId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SaberFinisherPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("saber_finisher"));
	public static final StreamCodec<ByteBuf, SaberFinisherPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SaberFinisherPayload::attackerId,
			ByteBufCodecs.VAR_INT, SaberFinisherPayload::targetId,
			SaberFinisherPayload::new
	);

	@Override
	public CustomPacketPayload.Type<SaberFinisherPayload> type() {
		return TYPE;
	}
}
