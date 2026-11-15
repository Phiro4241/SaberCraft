package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 16: a hyperspace jump made from a ship's interior, for everyone in that interior: the
 * drive spooling up, the time in hyperspace, the way back to normal space.
 *
 * @param origin the plot's corner
 * @param stage  {@link #CHARGE}, {@link #TUNNEL} or {@link #ARRIVE}
 * @param ticks  how long this stage lasts
 */
public record InteriorJumpPayload(BlockPos origin, byte stage, short ticks) implements CustomPacketPayload {
	public static final byte CHARGE = 0;
	public static final byte TUNNEL = 1;
	public static final byte ARRIVE = 2;
	public static final CustomPacketPayload.Type<InteriorJumpPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("interior_jump"));
	public static final StreamCodec<ByteBuf, InteriorJumpPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, InteriorJumpPayload::origin,
			ByteBufCodecs.BYTE, InteriorJumpPayload::stage,
			ByteBufCodecs.SHORT, InteriorJumpPayload::ticks,
			InteriorJumpPayload::new
	);

	@Override
	public CustomPacketPayload.Type<InteriorJumpPayload> type() {
		return TYPE;
	}
}
