package com.phiro.sabercraft.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * The droids a player has out, for their droid menu (Phase 11). Sent to that player only, on join
 * and whenever one of their droids changes.
 *
 * @param enabled whether droids are enabled on the server
 * @param limit   how many droids the player may have out at once
 * @param droids  the droids out now
 */
public record DroidStatusPayload(boolean enabled, int limit, List<Entry> droids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DroidStatusPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("droid_status"));
	public static final StreamCodec<ByteBuf, DroidStatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DroidStatusPayload::enabled,
			ByteBufCodecs.VAR_INT, DroidStatusPayload::limit,
			Entry.CODEC.apply(ByteBufCodecs.list(16)), DroidStatusPayload::droids,
			DroidStatusPayload::new
	);

	@Override
	public CustomPacketPayload.Type<DroidStatusPayload> type() {
		return TYPE;
	}

	/**
	 * One droid that is out.
	 *
	 * @param droid    its kind
	 * @param entityId the entity, or -1 while it is being put back
	 * @param staying  whether it was told to stay
	 * @param cooldown ticks until its ability is ready again
	 * @param total    the full cooldown of its ability, in ticks
	 */
	public record Entry(String droid, int entityId, boolean staying, int cooldown, int total) {
		public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(16), Entry::droid,
				ByteBufCodecs.VAR_INT, Entry::entityId,
				ByteBufCodecs.BOOL, Entry::staying,
				ByteBufCodecs.VAR_INT, Entry::cooldown,
				ByteBufCodecs.VAR_INT, Entry::total,
				Entry::new
		);
	}
}
