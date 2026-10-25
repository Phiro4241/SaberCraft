package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * A button of the droid menu (Phase 11), sent to the server, which checks it: the droid kind must
 * exist, droids must be enabled, the limit holds, and a player can send one every few ticks.
 *
 * @param action {@code spawn}, {@code recall}, {@code stay}, {@code ability} or {@code dismiss}
 * @param droid  the droid kind, as in {@code DroidType.id()}
 */
public record DroidCommandPayload(String action, String droid) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DroidCommandPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("droid_command"));
	public static final StreamCodec<ByteBuf, DroidCommandPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(16), DroidCommandPayload::action,
			ByteBufCodecs.stringUtf8(16), DroidCommandPayload::droid,
			DroidCommandPayload::new
	);

	@Override
	public CustomPacketPayload.Type<DroidCommandPayload> type() {
		return TYPE;
	}
}
