package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * The player's starship, for their hangar (Phase 11). Sent to that player only, on join and
 * whenever it changes.
 *
 * @param enabled  whether ships are enabled on the server
 * @param ship     the ship the player has out, or an empty string
 * @param entityId the ship entity, or -1 while it is not in the world (parked far away)
 */
public record ShipStatusPayload(boolean enabled, String ship, int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShipStatusPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ship_status"));
	public static final StreamCodec<ByteBuf, ShipStatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ShipStatusPayload::enabled,
			ByteBufCodecs.stringUtf8(32), ShipStatusPayload::ship,
			ByteBufCodecs.VAR_INT, ShipStatusPayload::entityId,
			ShipStatusPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ShipStatusPayload> type() {
		return TYPE;
	}
}
