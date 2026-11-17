package com.phiro.sabercraft.network;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 19: the people in a story ship near the player, a few times a second (the client draws
 * them in between). Positions are in plot space, from the plot's corner {@code origin}.
 */
public record StoryFiguresPayload(BlockPos origin, long time, List<Fig> figures) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StoryFiguresPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("story_figures"));
	public static final StreamCodec<FriendlyByteBuf, StoryFiguresPayload> CODEC = CustomPacketPayload.codec(StoryFiguresPayload::write, StoryFiguresPayload::new);

	/** Flags of a figure. */
	public static final int LAMP = 1;
	public static final int SABER = 2;
	public static final int HOSTILE = 4;
	public static final int TALKS = 8;
	public static final int OBSERVER = 16;

	/**
	 * One person: their number, kind and pose (as {@code Crew}), name ({@code speaker}), where and
	 * which way they and their head face, how fast they walk, their stage and suspicion (0..255),
	 * flags, health (0..255) and a blow being wound up (ticks).
	 */
	public record Fig(int id, String kind, String pose, String speaker, float x, float y, float z, float yaw, float head, float speed, int stage, int suspicion,
			int flags, int health, int windup) {
		static Fig read(FriendlyByteBuf buf) {
			return new Fig(buf.readVarInt(), buf.readUtf(32), buf.readUtf(16), buf.readUtf(32), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
					buf.readUnsignedByte());
		}

		void write(FriendlyByteBuf buf) {
			buf.writeVarInt(this.id);
			buf.writeUtf(this.kind, 32);
			buf.writeUtf(this.pose, 16);
			buf.writeUtf(this.speaker, 32);
			buf.writeFloat(this.x);
			buf.writeFloat(this.y);
			buf.writeFloat(this.z);
			buf.writeFloat(this.yaw);
			buf.writeFloat(this.head);
			buf.writeFloat(this.speed);
			buf.writeByte(this.stage);
			buf.writeByte(this.suspicion);
			buf.writeByte(this.flags);
			buf.writeByte(this.health);
			buf.writeByte(this.windup);
		}
	}

	private StoryFiguresPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readLong(), buf.readList(Fig::read));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(this.origin);
		buf.writeLong(this.time);
		buf.writeCollection(this.figures, (b, f) -> f.write(b));
	}

	@Override
	public CustomPacketPayload.Type<StoryFiguresPayload> type() {
		return TYPE;
	}
}
