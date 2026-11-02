package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 12: the "Make lightsaber" and "Normal sword again" buttons of the saber menu. The server
 * checks the sword, the server settings ({@code saber_swords}, {@code commands_for_everyone}) and
 * changes the item, exactly like {@code /sabercraft convert} and {@code revert}.
 *
 * @param hand    the hand that holds the sword
 * @param toSaber true: make a saber of it; false: turn it back into a plain sword
 */
public record ConvertSaberPayload(InteractionHand hand, boolean toSaber) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ConvertSaberPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("convert_saber"));
	public static final StreamCodec<ByteBuf, ConvertSaberPayload> CODEC = StreamCodec.composite(
			InteractionHand.STREAM_CODEC, ConvertSaberPayload::hand,
			ByteBufCodecs.BOOL, ConvertSaberPayload::toSaber,
			ConvertSaberPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ConvertSaberPayload> type() {
		return TYPE;
	}
}
