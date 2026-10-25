package com.phiro.sabercraft.client.music;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.sky.PlanetPreset;
import com.phiro.sabercraft.client.sky.PlanetSky;

/**
 * The sound of the planet chosen in the Sky menu (Phase 10): desert wind on Tatooine, the
 * blizzard on Hoth, lava on Mustafar, the forest of Endor, the lake shore of Naboo and the city
 * of Coruscant. All of them are synthesized by {@code tools/synth_sounds.py --ambient}, no
 * recordings.
 *
 * <p>It plays only with a planet sky in the Overworld, as ambience (the vanilla "Ambient/Environment"
 * slider applies) with its own volume, {@code planet_ambient_volume}. Under a roof or underground
 * it fades to a quarter, and a new planet fades over the old one like the sky does. Nothing about
 * the world changes; it is just a sound for this player.
 */
public final class PlanetAmbience {
	/** Ticks of the fade in and out. */
	private static final float FADE = 60.0F;
	/** How loud the planet still is indoors, relative to outdoors. */
	private static final float INDOORS = 0.25F;

	private static final List<MusicPiece> FADING = new ArrayList<>();
	private static @Nullable MusicPiece current;
	private static @Nullable String planet;
	private static float outdoors = 1.0F;

	private PlanetAmbience() {
	}

	public static void tick(Minecraft client) {
		ImmersionConfig config = ImmersionConfig.get();
		String wanted = config.planetAmbient && config.planetAmbientVolume > 0.0F && client.player != null
				? PlanetSky.current().map(PlanetPreset::id).orElse(null) : null;

		if (wanted == null ? planet != null : !wanted.equals(planet)) {
			fadeOut();
			planet = wanted;

			if (wanted != null) {
				current = new MusicPiece(sound(wanted), true, FADE, SoundSource.AMBIENT);
				client.getSoundManager().play(current);
			}
		}

		if (current != null && !current.isPlaying(client) && !current.fadingOut() && current.age() > 40) {
			// The sound engine dropped it (for example after the sound settings changed): start again.
			current = new MusicPiece(sound(planet), true, FADE, SoundSource.AMBIENT);
			client.getSoundManager().play(current);
		}

		if (client.player != null) {
			outdoors = Mth.approach(outdoors, outdoorsNow(client, client.player) ? 1.0F : INDOORS, 1.0F / FADE);
		}

		if (current != null) {
			current.setMaster(config.planetAmbientVolume * outdoors);
		}

		for (MusicPiece piece : FADING) {
			piece.setMaster(config.planetAmbientVolume * outdoors);
		}

		FADING.removeIf(piece -> piece.isStopped() || !piece.isPlaying(client) && piece.age() > 40);
	}

	private static Identifier sound(String planet) {
		return SaberCraft.id("ambient." + planet);
	}

	private static boolean outdoorsNow(Minecraft client, LocalPlayer player) {
		BlockPos head = BlockPos.containing(player.getEyePosition());
		return client.level.canSeeSky(head);
	}

	private static void fadeOut() {
		if (current != null) {
			current.fadeOut(FADE);
			FADING.add(current);
			current = null;
		}
	}

	/** Stops at once, when the world is left. */
	public static void reset() {
		Minecraft client = Minecraft.getInstance();

		if (current != null) {
			client.getSoundManager().stop(current);
		}

		for (MusicPiece piece : FADING) {
			client.getSoundManager().stop(piece);
		}

		FADING.clear();
		current = null;
		planet = null;
	}

	/** The planet whose sound plays now, for tests and the menu. */
	public static @Nullable String playing() {
		return current == null ? null : planet;
	}

	/** The volume of the planet sound now, 0..1, for tests. */
	public static float volume() {
		return current == null ? 0.0F : current.getVolume();
	}

	/** How far the sound is outdoors, INDOORS..1, for tests. */
	public static float outdoors() {
		return outdoors;
	}
}
