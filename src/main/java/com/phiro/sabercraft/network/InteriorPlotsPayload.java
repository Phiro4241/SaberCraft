package com.phiro.sabercraft.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 15: the built interior plots, sent to players in the interiors dimension, so their client
 * knows where to draw the detailed rooms and the outer hull of each ship (also out of block range,
 * for spectators flying around a Star Destroyer). Only the plots, never who owns them.
 *
 * @param plots every built plot: its plan, its index along x and the plan version it was built from
 */
public record InteriorPlotsPayload(List<Plot> plots) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<InteriorPlotsPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("interior_plots"));

	public record Plot(String plan, int index, int version) {
		public static final StreamCodec<ByteBuf, Plot> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Plot::plan,
				ByteBufCodecs.VAR_INT, Plot::index,
				ByteBufCodecs.VAR_INT, Plot::version,
				Plot::new
		);
	}

	public static final StreamCodec<ByteBuf, InteriorPlotsPayload> CODEC = StreamCodec.composite(
			Plot.CODEC.apply(ByteBufCodecs.list(4096)), InteriorPlotsPayload::plots,
			InteriorPlotsPayload::new
	);

	@Override
	public CustomPacketPayload.Type<InteriorPlotsPayload> type() {
		return TYPE;
	}
}
