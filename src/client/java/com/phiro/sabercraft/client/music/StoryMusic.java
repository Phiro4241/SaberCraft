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
 * SaberCraft and Star Wars music step aside meanwhile. Changes crossfade; an empty choice fades to
 * silence. With the SaberCraft music switched off the story is silent too.
 *
 * <p>Phase 24: over a calm piece the music follows the danger, in moods: someone grows suspicious
 * nearby (tension), they are after the player (the alarm), they lost them and search (still tense,
 * but calmer; also after fleeing into another room), and for half a minute after it is over a
 * piece that calms down slowly, before the sneaking music comes back. Rising danger cuts in fast,
 * calming down fades slowly; each mood holds a moment so that it does not flicker. The sneaking
 * music takes turns between two pieces.
 */
public final class StoryMusic {
	/** The calm pieces the danger's moods lay themselves over. */
	private static final List<String> CALM = List.of("music.story_stealth", "music.story_hideout", "music.exploration_wander", "music.night_stars",
			"music.story_corridors", "music.story_infiltration");
	private static final String STEALTH = "music.story_stealth";
	private static final String CORRIDORS = "music.story_corridors";
	private static final String HIDEOUT = "music.story_hideout";
	private static final String ALARM = "music.story_alarm";
	/** Phase 24: the moods: suspicion nearby, the search, after the danger. */
	private static final String TENSION = "music.story_tension";
	private static final String SEARCH = "music.story_search";
	private static final String AFTERMATH = "music.story_aftermath";
	/** The sneaking music sits a little lower, under the footsteps and voices. */
	private static final float STEALTH_GAIN = 0.8F;
	/** Phase 24: how long the calming piece plays after the danger (ticks), and how long suspicion holds the tension. */
	static final int AFTER = 600;
	static final int TENSION_HOLD = 160;
	/** Phase 24: after this long in one sneaking piece the other takes over (ticks). */
	static final int TURN = 3600;
	/** Phase 24: how far (blocks) someone's suspicion makes the music tense. */
	static final double TENSION_RANGE = 18.0;

	/** Phase 24: client ticks counted here; when the danger was last there; suspicion last seen; the sneaking piece's turn. */
	private static long ticks;
	private static long dangerAt = Long.MIN_VALUE / 2;
	private static long suspectAt = Long.MIN_VALUE / 2;
	private static int calmTurn;
	private static long calmSince;
	private static boolean wasCalm;
	/** Phase 24: the mood now (for the tests): film, story, chase, search, after, tension, calm. */
	private static String mood = "";

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
		ticks++;
		String was = mood;
		String piece = wantedPiece();

		if (!java.util.Objects.equals(piece, playing)) {
			// Phase 24: rising danger cuts in quickly, calming down fades out slowly.
			float fade = rising(was, mood) ? Math.max(8.0F, crossfade * 0.35F) : calming(was, mood) ? Math.max(40.0F, crossfade * 2.0F) : crossfade;
			fadeOut(fade);
			playing = piece;

			if (piece != null) {
				Identifier id = SaberCraft.id(piece);
				MusicPiece next = new MusicPiece(id, true, Math.max(10.0F, fade));
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
			current.setMaster(volume * (STEALTH.equals(playing) || CORRIDORS.equals(playing) ? STEALTH_GAIN : 1.0F));
		}

		for (MusicPiece piece2 : FADING) {
			piece2.setMaster(volume);
		}

		tidy(client);
		return true;
	}

	/**
	 * The piece the story wants now: a film's own; the server's choice when it is a moment of its
	 * own (the duel, the escape); over a calm one the mood of the danger.
	 */
	private static @Nullable String wantedPiece() {
		// Phase 20: a film has its own music.
		// (Phase 24: a short scene without music of its own keeps the piece playing.)
		String film = com.phiro.sabercraft.client.story.StoryCinema.active() ? com.phiro.sabercraft.client.story.StoryCinema.music() : null;

		if (film != null) {
			mood = "film";
			return film;
		}

		String chosen = StoryClient.music();
		StoryStatePayload state = StoryClient.state();
		int alarm = state != null ? state.alarm() : 0;

		if (alarm >= 2) {
			dangerAt = ticks;
		}

		if (chosen != null && !chosen.isEmpty() && !CALM.contains(chosen)) {
			wasCalm = false;
			mood = "story";
			return chosen;
		}

		if (alarm >= 3) {
			wasCalm = false;
			mood = "chase";
			return ALARM;
		}

		if (alarm == 2) {
			// Out of their sight, they search: tense still, a little calmer (in the hideout: the calming piece).
			wasCalm = false;
			mood = "search";
			return HIDEOUT.equals(chosen) ? AFTERMATH : SEARCH;
		}

		if (ticks - dangerAt < AFTER) {
			wasCalm = false;
			mood = "after";
			return AFTERMATH;
		}

		if (HIDEOUT.equals(chosen)) {
			// Safe in the hideout: the suspicion left outside does not follow in.
			suspectAt = Long.MIN_VALUE / 2;
		} else if (StoryClient.wariness(TENSION_RANGE) >= 1) {
			suspectAt = ticks;
		}

		if (ticks - suspectAt < TENSION_HOLD) {
			wasCalm = false;
			mood = "tension";
			return TENSION;
		}

		mood = "calm";

		if (chosen == null || chosen.isEmpty()) {
			return null;
		}

		if (!STEALTH.equals(chosen)) {
			return chosen;
		}

		// The sneaking music takes turns: the other piece each time one is back from danger, and after a few minutes.
		if (!wasCalm) {
			if (calmSince != 0L) {
				calmTurn++;
			}

			wasCalm = true;
			calmSince = ticks;
		} else if (ticks - calmSince > TURN) {
			calmTurn++;
			calmSince = ticks;
		}

		return calmTurn % 2 == 0 ? STEALTH : CORRIDORS;
	}

	private static final List<String> LADDER = List.of("calm", "tension", "after", "search", "chase");

	/** Phase 24: whether the danger rose from one mood to the other (the music cuts in fast then). */
	private static boolean rising(String from, String to) {
		int a = LADDER.indexOf(from);
		int b = LADDER.indexOf(to);
		return a >= 0 && b >= 0 && b > a && !"after".equals(to);
	}

	/** Phase 24: whether it calms down (the music fades out slowly then). */
	private static boolean calming(String from, String to) {
		int a = LADDER.indexOf(from);
		int b = LADDER.indexOf(to);
		return a >= 0 && b >= 0 && b < a || "after".equals(to);
	}

	/** Phase 24: the mood of the music now (for tests). */
	public static String mood() {
		return mood;
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
		dangerAt = Long.MIN_VALUE / 2;
		suspectAt = Long.MIN_VALUE / 2;
		wasCalm = false;
		mood = "";
	}
}
