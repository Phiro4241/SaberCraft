package com.phiro.sabercraft.client.music;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * The optional background music of SaberCraft (Phase 7), purely on this client.
 *
 * <p>It picks a mood from what happens around the player: {@link Mood#EVENT} near a boss,
 * {@link Mood#COMBAT} while fighting, {@link Mood#NIGHT} at night, underground and in dimensions
 * without a day, otherwise {@link Mood#EXPLORATION}. Calm moods play one piece and then leave a
 * pause, like vanilla; combat and event music loop while they last. A change of mood crossfades;
 * at most the current piece and the one fading out play at the same time, never two pieces of
 * the same mood. While it is on, the vanilla game music is held back (the title screen keeps its
 * vanilla music), and its volume is its own slider on top of the vanilla music slider. The pieces
 * are original compositions ({@code tools/synth_music.py}); players can add their own Ogg files
 * ({@link UserMusic}).
 */
public final class SaberMusic {
	/** Bosses that bring the event music. */
	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, SaberCraft.id("boss_music"));
	private static final int COMBAT_LINGER = 300;
	private static final int CALM_DELAY = 100;
	private static final int MOOD_SETTLE = 60;

	/** Moods of the music, also the names of the folders for your own files. */
	public enum Mood {
		EXPLORATION("exploration", false, "exploration_horizon", "exploration_wander"),
		NIGHT("night", false, "night_stars"),
		COMBAT("combat", true, "combat_duel", "combat_shadow"),
		EVENT("event", true, "event_rise");

		private final String folder;
		private final boolean loops;
		private final List<Identifier> builtIn;

		Mood(String folder, boolean loops, String... pieces) {
			this.folder = folder;
			this.loops = loops;
			this.builtIn = java.util.Arrays.stream(pieces).map(name -> SaberCraft.id("music." + name)).toList();
		}

		public String folder() {
			return this.folder;
		}

		public boolean loops() {
			return this.loops;
		}
	}

	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<MusicPiece> FADING = new ArrayList<>();
	private static final Map<Mood, Identifier> LAST = new EnumMap<>(Mood.class);
	private static @Nullable MusicPiece current;
	private static @Nullable Mood mood;
	private static @Nullable Mood candidate;
	private static int candidateTicks;
	private static int silence;
	private static long combatUntil;
	private static boolean bossNearby;
	private static boolean held;
	private static int ticks;
	private static @Nullable Identifier testPick;

	private SaberMusic() {
	}

	/** Whether the SaberCraft music plays instead of the vanilla game music right now. */
	public static boolean replacesVanilla() {
		Minecraft client = Minecraft.getInstance();
		return ImmersionConfig.get().music && client.level != null && client.player != null;
	}

	public static void tick(Minecraft client) {
		ticks++;
		float crossfade = ImmersionConfig.get().musicCrossfadeSeconds * 20.0F;

		if (!replacesVanilla()) {
			if (held) {
				held = false;
				fadeOutAll(client.level == null ? 1.0F : crossfade * 0.5F);
				mood = null;
			}
		} else {
			if (!held) {
				// Let go of the vanilla piece that may be playing.
				held = true;
				client.getMusicManager().stopPlaying();
				silence = 40;
			}

			Mood wanted = detect(client);
			if (wanted != mood) {
				candidateTicks = wanted == candidate ? candidateTicks + 1 : 0;
				candidate = wanted;

				// Fights and bosses start at once, calm music waits until the mood has settled.
				if (mood == null || wanted == Mood.COMBAT || wanted == Mood.EVENT || candidateTicks >= MOOD_SETTLE) {
					switchTo(wanted, crossfade);
				}
			} else {
				candidate = null;
			}

			if (current != null && !current.isPlaying(client) && !current.fadingOut()) {
				current = null;
				// After a calm piece a pause of 30 to 90 seconds, like the vanilla music.
				silence = mood != null && mood.loops() ? 20 : 600 + RANDOM.nextInt(1200);
			}

			if (current == null && mood != null && --silence <= 0) {
				start(client, mood, crossfade);
			}
		}

		float volume = ImmersionConfig.get().musicVolume * (client.level == null ? 1.0F : client.getMusicVolume());
		if (current != null) {
			current.setMaster(volume);
		}
		for (MusicPiece piece : FADING) {
			piece.setMaster(volume);
		}
		FADING.removeIf(piece -> piece.isStopped() || !piece.isPlaying(client) && piece.age() > 40);
	}

	private static void switchTo(Mood wanted, float crossfade) {
		if (current != null) {
			current.fadeOut(crossfade);
			FADING.add(current);
			current = null;
		}

		// Calm music comes back after a short breath; combat and events start right away.
		silence = mood == Mood.COMBAT && !wanted.loops() ? CALM_DELAY : 1;
		mood = wanted;
		candidate = null;
	}

	private static void start(Minecraft client, Mood wanted, float crossfade) {
		List<Identifier> choices = new ArrayList<>(wanted.builtIn);
		if (ImmersionConfig.get().musicUserFiles) {
			choices.addAll(UserMusic.pieces(wanted));
		}

		if (choices.isEmpty()) {
			silence = 200;
			return;
		}

		// Not the same piece twice in a row when there is a choice (a piece asked for by a test wins).
		Identifier last = LAST.get(wanted);
		if (testPick != null && choices.contains(testPick)) {
			choices = List.of(testPick);
		} else if (choices.size() > 1 && last != null) {
			choices.remove(last);
		}

		Identifier pick = choices.get(RANDOM.nextInt(choices.size()));
		LAST.put(wanted, pick);
		MusicPiece piece = new MusicPiece(pick, wanted.loops(), crossfade);
		current = piece;
		client.getSoundManager().play(piece);
		SaberCraft.LOGGER.debug("SaberCraft music: {} ({})", pick, wanted.folder());
	}

	private static void fadeOutAll(float ticksToSilence) {
		if (current != null) {
			current.fadeOut(ticksToSilence);
			FADING.add(current);
			current = null;
		}
	}

	/** The mood for this moment. */
	private static Mood detect(Minecraft client) {
		LocalPlayer player = client.player;

		if (ticks % 10 == 0) {
			scan(client, player);
		}

		if (bossNearby || client.gui.hud.getBossOverlay().shouldPlayMusic()) {
			return Mood.EVENT;
		} else if (client.level.getGameTime() < combatUntil) {
			return Mood.COMBAT;
		} else if (client.level.isDarkOutside() || client.level.dimensionType().hasFixedTime()
				|| player.getY() < 40.0 && !client.level.canSeeSky(player.blockPosition())) {
			return Mood.NIGHT;
		}

		return Mood.EXPLORATION;
	}

	/** Looks for bosses and enemies around the player; a fight keeps the combat music for 15 s. */
	private static void scan(Minecraft client, LocalPlayer player) {
		boolean boss = false;
		boolean fight = false;
		boolean armed = saberLit(player) || SaberAbilityClient.isGuarding();

		for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(48.0), LivingEntity::isAlive)) {
			if (entity.is(BOSSES)) {
				boss = true;
			} else if (entity instanceof Enemy && player.hasLineOfSight(entity)) {
				double distance = entity.distanceTo(player);

				// An enemy close by with the blade drawn, an enemy hitting the player, or the player hitting one.
				if (armed && distance < 8.0 || player.hurtTime > 0 && distance < 16.0 || entity.hurtTime > 0 && distance < 6.0) {
					fight = true;
				}
			}
		}

		bossNearby = boss;
		if (fight) {
			combatUntil = client.level.getGameTime() + COMBAT_LINGER;
		}
	}

	private static boolean saberLit(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			SaberHandState state = SaberTracker.INSTANCE.state(player, hand);
			if (state != null && state.isLit()) {
				return true;
			}
		}

		return false;
	}

	/** The piece playing now, for tests and the menu. */
	public static @Nullable Identifier currentPiece() {
		return current == null ? null : current.getIdentifier();
	}

	public static @Nullable Mood mood() {
		return mood;
	}

	/** How many pieces are audible now (the current one and those fading out). */
	public static int playing(Minecraft client) {
		int count = current != null && current.isPlaying(client) ? 1 : 0;
		for (MusicPiece piece : FADING) {
			if (!piece.isStopped() && piece.isPlaying(client)) {
				count++;
			}
		}
		return count;
	}

	/** Starts a fight for the music, for tests. */
	public static void markCombatForTests(Minecraft client) {
		combatUntil = client.level.getGameTime() + COMBAT_LINGER;
	}

	/** Makes the next piece of a mood this one, when the mood has it; for tests. */
	public static void pickForTests(@Nullable Identifier piece) {
		testPick = piece;
	}

	/** Forgets everything when leaving a world. */
	public static void reset() {
		Minecraft client = Minecraft.getInstance();
		if (current != null) {
			client.getSoundManager().stop(current);
		}
		FADING.forEach(client.getSoundManager()::stop);
		current = null;
		FADING.clear();
		mood = null;
		candidate = null;
		held = false;
		combatUntil = 0L;
		bossNearby = false;
	}
}
