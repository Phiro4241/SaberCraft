package com.phiro.sabercraft.client.music;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.network.StoryStatePayload;

/**
 * Phase 19: the story's music. While the player is in the story, the story chooses the piece (the
 * server sends it with each step: sneaking, the hideout, the alarm, the duel) and it loops; the
 * SaberCraft and Star Wars music step aside meanwhile. During an alarm the calm pieces give way to
 * the alarm piece until it is quiet again. Changes crossfade; an empty choice fades to silence.
 * With the SaberCraft music switched off the story is silent too.
 */
public final class StoryMusic {
	/** The calm pieces an alarm interrupts. */
	private static final List<String> CALM = List.of("music.story_stealth", "music.story_hideout", "music.exploration_wander", "music.night_stars");
	private static final String ALARM = "music.story_alarm";
	/** The sneaking music sits a little lower, under the footsteps and voices. */
	private static final float STEALTH_GAIN = 0.8F;

	private static @Nullable MusicPiece current;
	private static @Nullable String playing;
	private static final List<MusicPiece> FADING = new ArrayList<>();
	private static boolean owns;

	private StoryMusic() {
	}

	/** Whether the story's music plays now (the other music holds back then). Called every tick by {@link SaberMusic}. */
	static boolean tick(Minecraft client, float crossfade) {
		boolean wanted = ImmersionConfig.get().music && client.level != null && client.player != null && StoryClient.active();

		if (!wanted) {
			if (owns) {
				owns = false;
				fadeOut(Math.max(10.0F, crossfade * 0.5F));
				playing = null;
			}

			tidy(client);
			return false;
		}

		owns = true;
		String piece = wantedPiece();

		if (!java.util.Objects.equals(piece, playing)) {
			fadeOut(crossfade);
			playing = piece;

			if (piece != null) {
				Identifier id = SaberCraft.id(piece);
				MusicPiece next = new MusicPiece(id, true, Math.max(10.0F, crossfade));
				current = next;
				client.getSoundManager().play(next);
			}
		} else if (current != null && !current.isPlaying(client) && !current.fadingOut() && current.age() > 40 && piece != null) {
			// A looping piece the sound engine dropped (a reload): start it again.
			MusicPiece again = new MusicPiece(SaberCraft.id(piece), true, 20.0F);
			current = again;
			client.getSoundManager().play(again);
		}

		float volume = ImmersionConfig.get().musicVolume * client.getMusicVolume();

		if (current != null) {
			current.setMaster(volume * ("music.story_stealth".equals(playing) ? STEALTH_GAIN : 1.0F));
		}

		for (MusicPiece piece2 : FADING) {
			piece2.setMaster(volume);
		}

		tidy(client);
		return true;
	}

	/** The piece the story wants now: the server's choice, the alarm over a calm one. */
	private static @Nullable String wantedPiece() {
		// Phase 20: a film has its own music.
		if (com.phiro.sabercraft.client.story.StoryCinema.active()) {
			return com.phiro.sabercraft.client.story.StoryCinema.music();
		}

		String chosen = StoryClient.music();
		StoryStatePayload state = StoryClient.state();

		if (state != null && state.alarm() >= 3 && (chosen == null || CALM.contains(chosen))) {
			return ALARM;
		}

		return chosen == null || chosen.isEmpty() ? null : chosen;
	}

	private static void fadeOut(float ticks) {
		if (current != null) {
			current.fadeOut(ticks);
			FADING.add(current);
			current = null;
		}
	}

	private static void tidy(Minecraft client) {
		FADING.removeIf(piece -> piece.isStopped() || !piece.isPlaying(client) && piece.age() > 40);
	}

	/** The piece playing now (for tests). */
	public static @Nullable String playing() {
		return owns ? playing : null;
	}

	static void reset() {
		Minecraft client = Minecraft.getInstance();

		if (current != null) {
			client.getSoundManager().stop(current);
		}

		FADING.forEach(client.getSoundManager()::stop);
		FADING.clear();
		current = null;
		playing = null;
		owns = false;
	}
}
