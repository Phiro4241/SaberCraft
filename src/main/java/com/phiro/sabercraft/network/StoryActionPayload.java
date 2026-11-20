package com.phiro.sabercraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 19: the player does something in the story: from the menu (continue, start again, play a
 * mission again, leave), in a dialog (an answer), in the ship (use a thing, speak to someone, a
 * blow with the saber in the finale).
 *
 * @param action what ({@link #CONTINUE}..)
 * @param text   the thing used, or empty
 * @param value  the answer, the mission, the figure
 */
public record StoryActionPayload(byte action, String text, int value) implements CustomPacketPayload {
	public static final byte CONTINUE = 0;
	public static final byte RESTART = 1;
	public static final byte MISSION = 2;
	public static final byte LEAVE = 3;
	public static final byte ANSWER = 4;
	public static final byte USE = 5;
	public static final byte TALK = 6;
	public static final byte STRIKE = 7;
	/** Asks for the state (the menu opened). */
	public static final byte STATE = 8;
	/** Phase 20: the player skipped a film (its name in the text). */
	public static final byte SKIP = 9;
	/** Phase 21: the Focus key (the duel's slowed time). */
	public static final byte FOCUS = 10;
	/** Phase 27: the story's difficulty chosen in the menu (value: 0 easy, 1 normal, 2 hard). */
	public static final byte DIFFICULTY = 11;
public static final CustomPacketPayload.Type<StoryActionPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("story_action"));
	public static final StreamCodec<FriendlyByteBuf, StoryActionPayload> CODEC = CustomPacketPayload.codec(StoryActionPayload::write, StoryActionPayload::new);

	private StoryActionPayload(FriendlyByteBuf buf) {
		this(buf.readByte(), buf.readUtf(64), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeByte(this.action);
		buf.writeUtf(this.text, 64);
		buf.writeVarInt(this.value);
	}

	@Override
	public CustomPacketPayload.Type<StoryActionPayload> type() {
		return TYPE;
	}
}
