package com.phiro.sabercraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Sent by the client when the player picks a hilt and color for the saber in one hand. This is the
 * only packet of the mod. The server checks it and stores the variant on the item, from where the
 * normal item sync brings it to every client.
 */
public record SelectSaberVariantPayload(InteractionHand hand, SaberVariant variant) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectSaberVariantPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("select_variant"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectSaberVariantPayload> CODEC = StreamCodec.composite(
			InteractionHand.STREAM_CODEC, SelectSaberVariantPayload::hand,
			SaberVariant.STREAM_CODEC, SelectSaberVariantPayload::variant,
			SelectSaberVariantPayload::new
	);

	@Override
	public CustomPacketPayload.Type<SelectSaberVariantPayload> type() {
		return TYPE;
	}
}
