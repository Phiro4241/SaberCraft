package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * The server's settings that the client needs to move its own player like the server wants, sent
 * when a player joins. Player movement runs on the client in Minecraft, so the client applies the
 * dodge motion itself; the server decides how strong it is.
 *
 * @param dodgeSpeed multiplier of the dodge and lunge speed, 1 = default
 */
public record AbilitySettingsPayload(float dodgeSpeed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilitySettingsPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ability_settings"));
	public static final StreamCodec<ByteBuf, AbilitySettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::dodgeSpeed,
			AbilitySettingsPayload::new
	);

	@Override
	public CustomPacketPayload.Type<AbilitySettingsPayload> type() {
		return TYPE;
	}
}
