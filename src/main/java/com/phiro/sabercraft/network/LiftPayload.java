package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 30: the reactor's maintenance lift on its way, for the HUD: how far along the ride is
 * ({@code ticks} of {@code total}), and whether it goes down. Sent to the rider only: every tick
 * while the ride counts (also lower again while they move about on it); once with {@code ticks} ==
 * {@code total} when it arrives (the client shows the arrival a moment); once with {@code ticks}
 * -1 when a ride ends without arriving (stepped off, or moved until it counted back to nothing).
 * Nothing while the lift rests after a ride or the rider is not ready for it.
 */
public record LiftPayload(int ticks, int total, boolean down) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LiftPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("lift"));
	public static final StreamCodec<ByteBuf, LiftPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LiftPayload::ticks,
			ByteBufCodecs.VAR_INT, LiftPayload::total,
			ByteBufCodecs.BOOL, LiftPayload::down,
			LiftPayload::new
	);

	@Override
	public CustomPacketPayload.Type<LiftPayload> type() {
		return TYPE;
	}
}
