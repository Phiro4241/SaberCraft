package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 14: a hyperspace jump of a ship, for everyone who sees the ship (and its pilot), so every
 * client shows the same effect at the same places: the hyperdrive spooling up, the jump at the
 * start, the arrival at the target, or a jump that was called off.
 *
 * @param entityId the ship
 * @param stage    {@link #CHARGE}, {@link #JUMP}, {@link #ARRIVE} or {@link #ABORT}
 * @param from     where the ship leaves
 * @param to       where it comes out
 * @param ticks    how long this stage lasts, ticks
 */
public record HyperspacePayload(int entityId, byte stage, Vec3 from, Vec3 to, short ticks) implements CustomPacketPayload {
	public static final byte CHARGE = 0;
	public static final byte JUMP = 1;
	public static final byte ARRIVE = 2;
	public static final byte ABORT = 3;
	public static final CustomPacketPayload.Type<HyperspacePayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("hyperspace"));
	public static final StreamCodec<ByteBuf, HyperspacePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HyperspacePayload::entityId,
			ByteBufCodecs.BYTE, HyperspacePayload::stage,
			Vec3.STREAM_CODEC, HyperspacePayload::from,
			Vec3.STREAM_CODEC, HyperspacePayload::to,
			ByteBufCodecs.SHORT, HyperspacePayload::ticks,
			HyperspacePayload::new
	);

	@Override
	public CustomPacketPayload.Type<HyperspacePayload> type() {
		return TYPE;
	}
}
