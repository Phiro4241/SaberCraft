package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * The blaster rules of the server (Phase 11), sent when a player joins: whether shots need arrows
 * and whether Infinity keeps them. The client uses it only to show the ammunition and to give the
 * "no arrows" note without a round trip; the server checks every shot itself.
 */
public record BlasterSettingsPayload(boolean ammo, boolean infinity) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlasterSettingsPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("blaster_settings"));
	public static final StreamCodec<ByteBuf, BlasterSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, BlasterSettingsPayload::ammo,
			ByteBufCodecs.BOOL, BlasterSettingsPayload::infinity,
			BlasterSettingsPayload::new
	);

	@Override
	public CustomPacketPayload.Type<BlasterSettingsPayload> type() {
		return TYPE;
	}
}
