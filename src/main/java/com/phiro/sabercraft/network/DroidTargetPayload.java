package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 12: a droid key was pressed while the player looked at a droid. The server checks it like
 * a menu command and more: the entity is a droid of this player, close enough
 * ({@code Droids.TARGET_RANGE}) and in sight.
 *
 * @param entityId the droid looked at
 * @param action   {@code ability} (its own ability) or {@code stay} (wait here / follow again)
 */
public record DroidTargetPayload(int entityId, String action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DroidTargetPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("droid_target"));
	public static final StreamCodec<ByteBuf, DroidTargetPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroidTargetPayload::entityId,
			ByteBufCodecs.stringUtf8(16), DroidTargetPayload::action,
			DroidTargetPayload::new
	);

	@Override
	public CustomPacketPayload.Type<DroidTargetPayload> type() {
		return TYPE;
	}
}
