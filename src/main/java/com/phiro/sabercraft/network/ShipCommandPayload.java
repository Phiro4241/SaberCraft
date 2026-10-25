package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * A button of the hangar (Phase 11), sent to the server, which checks it: the ship must exist and
 * be available, ships must be enabled, and a player can send one every few ticks.
 *
 * @param action {@code call}, {@code board} or {@code dismiss}
 * @param ship   the ship, as in {@code ShipType.id()}
 */
public record ShipCommandPayload(String action, String ship) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShipCommandPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ship_command"));
	public static final StreamCodec<ByteBuf, ShipCommandPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(16), ShipCommandPayload::action,
			ByteBufCodecs.stringUtf8(32), ShipCommandPayload::ship,
			ShipCommandPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ShipCommandPayload> type() {
		return TYPE;
	}
}
