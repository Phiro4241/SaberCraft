package com.phiro.sabercraft.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 19: something the story shows or plays once: a line over the comlink (a subtitle), a
 * hint, a fade to black, a sound or a piece of music, the positions a droid's scan found, the
 * chapter's end.
 *
 * @param cue    what ({@code Story.CUE_*})
 * @param who    the speaker, or empty
 * @param text   the line's or hint's key, the sound's or music's name
 * @param value  how long, or a figure's number
 * @param points positions (world space), for a scan
 */
public record StoryCuePayload(byte cue, String who, String text, int value, List<Vec3> points) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StoryCuePayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("story_cue"));
	public static final StreamCodec<FriendlyByteBuf, StoryCuePayload> CODEC = CustomPacketPayload.codec(StoryCuePayload::write, StoryCuePayload::new);

	private StoryCuePayload(FriendlyByteBuf buf) {
		this(buf.readByte(), buf.readUtf(64), buf.readUtf(256), buf.readVarInt(), buf.readList(b -> new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeByte(this.cue);
		buf.writeUtf(this.who, 64);
		buf.writeUtf(this.text, 256);
		buf.writeVarInt(this.value);
		buf.writeCollection(this.points, (b, p) -> {
			b.writeDouble(p.x);
			b.writeDouble(p.y);
			b.writeDouble(p.z);
		});
	}

	@Override
	public CustomPacketPayload.Type<StoryCuePayload> type() {
		return TYPE;
	}
}
