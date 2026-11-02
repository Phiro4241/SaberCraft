package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 13: the flight energy of the ship a player flies, for their flight display. Sent to the
 * pilot a few times a second and whenever the engines cut out or come back.
 *
 * @param entityId the ship
 * @param energy   what is left, 0 (empty) to 1 (full)
 * @param state    0 normal, 1 empty (the engines are out, the ship glides down or holds still
 *                 until enough is charged again), 2 the energy limit is off on this server
 * @param seconds  seconds of flight left at the current use, or of charging until it can fly again
 */
public record ShipEnergyPayload(int entityId, float energy, byte state, short seconds) implements CustomPacketPayload {
	public static final byte NORMAL = 0;
	public static final byte EMPTY = 1;
	public static final byte UNLIMITED = 2;
	public static final CustomPacketPayload.Type<ShipEnergyPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ship_energy"));
	public static final StreamCodec<ByteBuf, ShipEnergyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShipEnergyPayload::entityId,
			ByteBufCodecs.FLOAT, ShipEnergyPayload::energy,
			ByteBufCodecs.BYTE, ShipEnergyPayload::state,
			ByteBufCodecs.SHORT, ShipEnergyPayload::seconds,
			ShipEnergyPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ShipEnergyPayload> type() {
		return TYPE;
	}
}
