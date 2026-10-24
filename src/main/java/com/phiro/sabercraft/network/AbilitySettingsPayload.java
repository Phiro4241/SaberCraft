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
 * <p>The stamina costs let the client hold back a dodge the server would refuse, so the player
 * does not start rolling without a reason.
 *
 * @param dodgeSpeed multiplier of the dodge and lunge speed, 1 = default
 * @param staminaMax most stamina, 0 if the server has stamina turned off
 * @param dodgeCost  stamina of a dodge before the style multiplier
 * @param spinCost   stamina of the spin
 * @param pushCost   stamina of the push
 * @param guardCost  stamina to raise the stance
 */
public record AbilitySettingsPayload(float dodgeSpeed, float staminaMax, float dodgeCost, float spinCost, float pushCost, float guardCost)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilitySettingsPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ability_settings"));
	public static final StreamCodec<ByteBuf, AbilitySettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::dodgeSpeed,
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::staminaMax,
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::dodgeCost,
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::spinCost,
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::pushCost,
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::guardCost,
			AbilitySettingsPayload::new
	);

	@Override
	public CustomPacketPayload.Type<AbilitySettingsPayload> type() {
		return TYPE;
	}
}
