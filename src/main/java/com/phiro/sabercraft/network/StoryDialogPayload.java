package com.phiro.sabercraft.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 19: a line of a story dialog to show (or the dialog closes, {@code open} false): who says
 * it (their name and picture come from {@code speaker}), whether over the comlink, the words and
 * the answers (translation keys; the client looks them up in the player's SaberCraft language).
 */
public record StoryDialogPayload(boolean open, String speaker, boolean radio, String text, List<String> answers, String line) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StoryDialogPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("story_dialog"));
	public static final StreamCodec<FriendlyByteBuf, StoryDialogPayload> CODEC = CustomPacketPayload.codec(StoryDialogPayload::write, StoryDialogPayload::new);

	public static StoryDialogPayload closed() {
		return new StoryDialogPayload(false, "", false, "", List.of(), "");
	}

	private StoryDialogPayload(FriendlyByteBuf buf) {
		this(buf.readBoolean(), buf.readUtf(64), buf.readBoolean(), buf.readUtf(256), buf.readList(b -> b.readUtf(256)), buf.readUtf(128));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(this.open);
		buf.writeUtf(this.speaker, 64);
		buf.writeBoolean(this.radio);
		buf.writeUtf(this.text, 256);
		buf.writeCollection(this.answers, (b, s) -> b.writeUtf(s, 256));
		buf.writeUtf(this.line, 128);
	}

	@Override
	public CustomPacketPayload.Type<StoryDialogPayload> type() {
		return TYPE;
	}
}
