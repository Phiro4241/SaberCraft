package com.phiro.sabercraft.network;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 19: what the client shows of the story: whether one is in it, the mission and its
 * objectives, where the marker points, the alarm, the disguise, what can be used, the journal and
 * what was decided; for the menu also what was played before.
 */
public record StoryStatePayload(boolean active, boolean saved, String chapter, int mission, int missions, List<Integer> completed, boolean finished,
		String title, String objective, List<Objective> optional, boolean target, Vec3 marker, int alarm, int disguise, List<Point> points,
		BlockPos falcon, BlockPos destroyer, List<String> journal, List<String> known, int captures, List<String> droids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StoryStatePayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("story_state"));
	public static final StreamCodec<FriendlyByteBuf, StoryStatePayload> CODEC = CustomPacketPayload.codec(StoryStatePayload::write, StoryStatePayload::new);

	/** An optional objective: its words, whether done. */
	public record Objective(String key, boolean done) {
	}

	/** A thing to use: its id and words, whether a droid must open it, its box (world space). */
	public record Point(String id, String label, boolean hack, AABB box) {
	}

	private StoryStatePayload(FriendlyByteBuf buf) {
		this(buf.readBoolean(), buf.readBoolean(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readList(FriendlyByteBuf::readVarInt), buf.readBoolean(),
				buf.readUtf(128), buf.readUtf(128), buf.readList(b -> new Objective(b.readUtf(128), b.readBoolean())), buf.readBoolean(),
				new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readVarInt(),
				buf.readList(b -> new Point(b.readUtf(64), b.readUtf(128), b.readBoolean(),
						new AABB(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()))),
				buf.readBlockPos(), buf.readBlockPos(), buf.readList(b -> b.readUtf(128)), buf.readList(b -> b.readUtf(128)), buf.readVarInt(),
				buf.readList(b -> b.readUtf(32)));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(this.active);
		buf.writeBoolean(this.saved);
		buf.writeUtf(this.chapter, 32);
		buf.writeVarInt(this.mission);
		buf.writeVarInt(this.missions);
		buf.writeCollection(this.completed, FriendlyByteBuf::writeVarInt);
		buf.writeBoolean(this.finished);
		buf.writeUtf(this.title, 128);
		buf.writeUtf(this.objective, 128);
		buf.writeCollection(this.optional, (b, o) -> {
			b.writeUtf(o.key(), 128);
			b.writeBoolean(o.done());
		});
		buf.writeBoolean(this.target);
		buf.writeDouble(this.marker.x);
		buf.writeDouble(this.marker.y);
		buf.writeDouble(this.marker.z);
		buf.writeVarInt(this.alarm);
		buf.writeVarInt(this.disguise);
		buf.writeCollection(this.points, (b, p) -> {
			b.writeUtf(p.id(), 64);
			b.writeUtf(p.label(), 128);
			b.writeBoolean(p.hack());
			b.writeDouble(p.box().minX);
			b.writeDouble(p.box().minY);
			b.writeDouble(p.box().minZ);
			b.writeDouble(p.box().maxX);
			b.writeDouble(p.box().maxY);
			b.writeDouble(p.box().maxZ);
		});
		buf.writeBlockPos(this.falcon);
		buf.writeBlockPos(this.destroyer);
		buf.writeCollection(this.journal, (b, s) -> b.writeUtf(s, 128));
		buf.writeCollection(this.known, (b, s) -> b.writeUtf(s, 128));
		buf.writeVarInt(this.captures);
		buf.writeCollection(this.droids, (b, s) -> b.writeUtf(s, 32));
	}

	@Override
	public CustomPacketPayload.Type<StoryStatePayload> type() {
		return TYPE;
	}
}
