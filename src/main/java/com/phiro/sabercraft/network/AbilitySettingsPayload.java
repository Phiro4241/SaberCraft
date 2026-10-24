package com.phiro.sabercraft.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbility;

/**
 * The server's settings that the client needs to move its own player like the server wants, sent
 * when a player joins. Player movement runs on the client in Minecraft, so the client applies the
 * dodge and force jump motion itself; the server decides how strong it is.
 *
 * <p>The stamina costs let the client hold back requests the server would refuse, so the player
 * does not start rolling or jumping without a reason.
 *
 * @param dodgeSpeed   multiplier of the dodge and lunge speed, 1 = default
 * @param jumpStrength multiplier of the force jump height, 1 = default
 * @param staminaMax   most stamina, 0 if the server has stamina turned off
 * @param costs        stamina cost of each ability, by {@link SaberAbility} ordinal (the dodge
 *                     before the style multiplier)
 */
public record AbilitySettingsPayload(float dodgeSpeed, float jumpStrength, float staminaMax, List<Float> costs) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilitySettingsPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("ability_settings"));
	public static final StreamCodec<ByteBuf, AbilitySettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::dodgeSpeed,
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::jumpStrength,
			ByteBufCodecs.FLOAT, AbilitySettingsPayload::staminaMax,
			ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(32)), AbilitySettingsPayload::costs,
			AbilitySettingsPayload::new
	);

	/** Settings while no server with SaberCraft sent any: no stamina. */
	public static final AbilitySettingsPayload NONE = new AbilitySettingsPayload(1.0F, 1.0F, 0.0F, List.of());

	/** The stamina cost of an ability, 0 if the server sent none. */
	public float cost(SaberAbility ability) {
		return ability.ordinal() < this.costs.size() ? this.costs.get(ability.ordinal()) : 0.0F;
	}

	@Override
	public CustomPacketPayload.Type<AbilitySettingsPayload> type() {
		return TYPE;
	}
}
