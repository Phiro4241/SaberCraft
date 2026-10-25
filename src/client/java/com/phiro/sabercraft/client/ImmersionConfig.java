package com.phiro.sabercraft.client;

import com.google.gson.annotations.SerializedName;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.config.JsonConfig;

/**
 * Client settings of the Phase 7 surroundings, in {@code config/sabercraft/immersion_client.json}:
 * the space sky and the background music. Both are purely visual or audible and only on this
 * client; the server is not involved.
 */
public final class ImmersionConfig {
	private static final String NAME = "immersion_client";
	private static ImmersionConfig current = new ImmersionConfig();

	/** The space sky: stars, nebulae, galaxies, planets and moons instead of the plain night sky. */
	@SerializedName("space_sky")
	public boolean spaceSky = true;
	/** Detail of the sky: 0 = low (256 px per side), 1 = medium (512), 2 = high (1024), 3 = ultra (1536, Phase 10). */
	@SerializedName("space_sky_quality")
	public int spaceSkyQuality = 1;
	/** Also a space sky in the End, over the vanilla End sky. */
	@SerializedName("space_sky_in_end")
	public boolean spaceSkyInEnd = true;
	/**
	 * Keep the space sky while an Iris shader pack is active. Off by default: shader packs draw
	 * their own sky, and two skies on top of each other look wrong.
	 */
	@SerializedName("space_sky_with_shaders")
	public boolean spaceSkyWithShaders = false;

	/**
	 * Star Wars planet for the Overworld sky (Phase 8): {@code tatooine}, {@code coruscant},
	 * {@code endor}, {@code hoth}, {@code mustafar}, {@code naboo}, or empty for the normal sky.
	 */
	@SerializedName("planet")
	public String planet = "";
	/** Snow on Hoth and ash on Mustafar drifting through the air. */
	@SerializedName("planet_particles")
	public boolean planetParticles = true;
	/**
	 * The far landscape on the planet's horizon and the details in its sky (Phase 9): dunes, giant
	 * trees, ice mountains, volcanoes, hills, air traffic, aurora, clouds. Only painted into the sky.
	 */
	@SerializedName("planet_horizon")
	public boolean planetHorizon = true;
	/**
	 * With an Iris shader pack: an extra layer in the planet's sky color (Phase 9). Shader packs
	 * draw their own sky color, so without it a planet keeps little of its tone.
	 */
	@SerializedName("planet_shader_tint")
	public boolean planetShaderTint = true;
	/**
	 * The planet's own sun and moon (Phase 10): size and color, Tatooine's two suns in different
	 * colors, Mustafar's dim red sun, Hoth's small cold one.
	 */
	@SerializedName("planet_celestials")
	public boolean planetCelestials = true;
	/** Seconds a newly chosen planet takes to fade in over the old sky (Phase 10). */
	@SerializedName("planet_transition_seconds")
	public float planetTransitionSeconds = 2.0F;

	/** Background music of SaberCraft, instead of the vanilla music. */
	@SerializedName("music")
	public boolean music = true;
	/** Volume of the SaberCraft music, 0..1, on top of the master and music sliders. */
	@SerializedName("music_volume")
	public float musicVolume = 0.6F;
	/** Also play your own .ogg files from config/sabercraft/music/<mood>/. */
	@SerializedName("music_user_files")
	public boolean musicUserFiles = true;
	/** Length of the crossfade between two pieces, in seconds. */
	@SerializedName("music_crossfade_seconds")
	public float musicCrossfadeSeconds = 4.0F;
	/**
	 * Phase 10: a short piece once after a won fight (victory) and when you fall (defeat), in both
	 * music modes. In the Star Wars mode only if there are files in {@code victory/} or {@code defeat/}.
	 */
	@SerializedName("music_stingers")
	public boolean musicStingers = true;
	/**
	 * Phase 10: the sound of the planet chosen in the Sky menu while you are outdoors in the
	 * Overworld: desert wind on Tatooine, the blizzard on Hoth, lava on Mustafar, and so on.
	 */
	@SerializedName("planet_ambient")
	public boolean planetAmbient = true;
	/** Volume of the planet sounds, 0..1, on top of the master and the "Ambient/Environment" slider. */
	@SerializedName("planet_ambient_volume")
	public float planetAmbientVolume = 0.5F;

	/**
	 * Which music plays (Phase 8): {@code sabercraft} for the SaberCraft music above, or
	 * {@code starwars} for the Star Wars mode, which plays your own files from
	 * {@code config/sabercraft/starwars_music/}. The settings of the two modes are separate.
	 */
	@SerializedName("music_mode")
	public String musicMode = MODE_SABERCRAFT;
	/** Volume of the Star Wars mode, 0..1, on top of the master and music sliders. */
	@SerializedName("starwars_volume")
	public float starWarsVolume = 0.7F;
	/** Crossfade between the tracks of the Star Wars mode; off = a short cut. */
	@SerializedName("starwars_crossfade")
	public boolean starWarsCrossfade = true;
	/** Star Wars mode: music in fights, duels and boss fights. */
	@SerializedName("starwars_combat")
	public boolean starWarsCombat = true;
	/** Star Wars mode: music while exploring and in calm moments. */
	@SerializedName("starwars_exploration")
	public boolean starWarsExploration = true;
	/** Star Wars mode: a character's theme when they appear (you or another player). */
	@SerializedName("starwars_character_themes")
	public boolean starWarsCharacterThemes = true;
	/** Phase 11: switch to the third-person view behind the ship when climbing into a starship. */
	@SerializedName("ship_third_person")
	public boolean shipThirdPerson = true;
	/** Phase 11: the flight display (speed, height, lasers) while flying a starship. */
	@SerializedName("ship_hud")
	public boolean shipHud = true;
	/** Phase 11: drawing and holstering a blaster are animated and heard. */
	@SerializedName("blaster_draw")
	public boolean blasterDraw = true;
	/** Phase 11: the menu fades a page in with a soft sweep when switching areas. */
	@SerializedName("menu_transitions")
	public boolean menuTransitions = true;

	public static final String MODE_SABERCRAFT = "sabercraft";
	public static final String MODE_STARWARS = "starwars";

	public boolean starWarsMode() {
		return MODE_STARWARS.equals(this.musicMode);
	}

	public static ImmersionConfig get() {
		return current;
	}

	public static void load() {
		ImmersionConfig config = JsonConfig.load(NAME, ImmersionConfig.class, ImmersionConfig::new);
		config.clamp();
		current = config;
	}

	/** Writes the current settings, after a change in the menu. */
	public static void save() {
		current.clamp();
		JsonConfig.save(NAME, current);
	}

	private void clamp() {
		this.spaceSkyQuality = Mth.clamp(this.spaceSkyQuality, 0, 3);
		if (this.planet == null) {
			this.planet = "";
		}
		this.planetTransitionSeconds = Mth.clamp(this.planetTransitionSeconds, 0.0F, 10.0F);
		this.musicVolume = Mth.clamp(this.musicVolume, 0.0F, 1.0F);
		this.planetAmbientVolume = Mth.clamp(this.planetAmbientVolume, 0.0F, 1.0F);
		this.musicCrossfadeSeconds = Mth.clamp(this.musicCrossfadeSeconds, 0.5F, 20.0F);
		this.starWarsVolume = Mth.clamp(this.starWarsVolume, 0.0F, 1.0F);
		if (!MODE_STARWARS.equals(this.musicMode)) {
			this.musicMode = MODE_SABERCRAFT;
		}
	}
}
