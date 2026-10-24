package com.phiro.sabercraft.network;

import java.util.Optional;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;

/**
 * Client to server: the built-in outfit this player wants others to see, or none (their plain
 * skin). Files of the player's own are never sent.
 */
public record ChooseOutfitPayload(Optional<Identifier> outfit) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ChooseOutfitPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("choose_outfit"));
	public static final StreamCodec<ByteBuf, ChooseOutfitPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(Identifier.STREAM_CODEC), ChooseOutfitPayload::outfit,
			ChooseOutfitPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ChooseOutfitPayload> type() {
		return TYPE;
	}
}
