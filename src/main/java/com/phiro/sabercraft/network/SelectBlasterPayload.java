package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.SaberCraft;

/**
 * Sent by the blaster menu (Phase 11): make the bow in a hand a blaster of a type, or (empty type)
 * turn the blaster back into the bow it was. Only a request: the server checks the hand, the item,
 * the type and whether the player may convert items, like {@code /sabercraft blaster}.
 *
 * @param hand the hand with the bow
 * @param blaster the path of the blaster type, for example {@code dl44}, or empty to turn it back
 */
public record SelectBlasterPayload(InteractionHand hand, String blaster) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectBlasterPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("select_blaster"));
	public static final StreamCodec<ByteBuf, SelectBlasterPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], InteractionHand::ordinal), SelectBlasterPayload::hand,
			ByteBufCodecs.stringUtf8(32), SelectBlasterPayload::blaster,
			SelectBlasterPayload::new
	);

	@Override
	public CustomPacketPayload.Type<SelectBlasterPayload> type() {
		return TYPE;
	}
}
