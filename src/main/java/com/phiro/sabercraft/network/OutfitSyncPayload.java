package com.phiro.sabercraft.network;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;

/**
 * Server to clients: the outfits of players. {@code full} replaces everything the client knows
 * (sent on join), otherwise the entries are changes; an empty outfit means "none any more".
 */
public record OutfitSyncPayload(boolean full, List<Entry> entries) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OutfitSyncPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("outfit_sync"));
	public static final StreamCodec<ByteBuf, OutfitSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, OutfitSyncPayload::full,
			Entry.CODEC.apply(ByteBufCodecs.list(1024)), OutfitSyncPayload::entries,
			OutfitSyncPayload::new
	);

	@Override
	public CustomPacketPayload.Type<OutfitSyncPayload> type() {
		return TYPE;
	}

	public record Entry(UUID player, Optional<Identifier> outfit) {
		public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Entry::player,
				ByteBufCodecs.optional(Identifier.STREAM_CODEC), Entry::outfit,
				Entry::new
		);
	}
}
