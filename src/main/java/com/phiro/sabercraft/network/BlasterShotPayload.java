package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.SaberCraft;

/**
 * A blaster shot went off (Phase 11). Sent by the server to the shooter and to everyone who sees
 * them, so every client shows the muzzle flash and the recoil at the right blaster, in first and
 * third person. Purely visual: the bolts themselves are entities.
 *
 * @param entityId the shooter
 * @param hand     the hand that holds the blaster
 * @param blaster  the index of the blaster type in {@code BlasterType.ALL}
 */
public record BlasterShotPayload(int entityId, InteractionHand hand, int blaster) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlasterShotPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("blaster_shot"));
	public static final StreamCodec<ByteBuf, BlasterShotPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BlasterShotPayload::entityId,
			ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], InteractionHand::ordinal), BlasterShotPayload::hand,
			ByteBufCodecs.VAR_INT, BlasterShotPayload::blaster,
			BlasterShotPayload::new
	);

	@Override
	public CustomPacketPayload.Type<BlasterShotPayload> type() {
		return TYPE;
	}
}
