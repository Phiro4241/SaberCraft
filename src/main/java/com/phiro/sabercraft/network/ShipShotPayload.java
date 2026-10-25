package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * A starship fired a laser cannon (Phase 11), for the muzzle flash on its model. Sent to everyone
 * who sees the ship.
 *
 * @param entityId the ship
 * @param cannon   which cannon, as in {@code ShipType.cannons()}
 */
public record ShipShotPayload(int entityId, byte cannon) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShipShotPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ship_shot"));
	public static final StreamCodec<ByteBuf, ShipShotPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShipShotPayload::entityId,
			ByteBufCodecs.BYTE, ShipShotPayload::cannon,
			ShipShotPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ShipShotPayload> type() {
		return TYPE;
	}
}
