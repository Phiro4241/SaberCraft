package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Something a droid does that its model shows (Phase 11): it was talked to, used its ability, or
 * just arrived. Sent to everyone who sees the droid.
 *
 * @param entityId the droid
 * @param event    1 talk, 2 ability, 3 arrive
 */
public record DroidEventPayload(int entityId, byte event) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DroidEventPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("droid_event"));
	public static final StreamCodec<ByteBuf, DroidEventPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroidEventPayload::entityId,
			ByteBufCodecs.BYTE, DroidEventPayload::event,
			DroidEventPayload::new
	);

	@Override
	public CustomPacketPayload.Type<DroidEventPayload> type() {
		return TYPE;
	}
}
