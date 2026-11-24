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
	/**
	 * Phase 14: the view when climbing into a starship: {@code cockpit} (first person from the
	 * pilot's seat, the default), {@code chase} (third person behind the ship, as in Phase 11) or
	 * {@code keep} (no change). F5 still switches as usual while flying; climbing out brings back
	 * the view from before. Empty in old files: taken from {@code ship_third_person}.
	 */
	@SerializedName("ship_camera")
	public String shipCamera = "";
	/** Phase 14: the cockpit frame (struts, window edges, consoles) drawn around the cockpit view. */
	@SerializedName("ship_cockpit_frame")
	public boolean shipCockpitFrame = true;
	/** Phase 14: the camera leans with the ship in turns in the cockpit view. */
	@SerializedName("ship_cockpit_roll")
	public boolean shipCockpitRoll = true;
	/** Phase 14: music of its own while flying a starship (and in space battles). */
	@SerializedName("music_flight")
	public boolean musicFlight = true;
	/** Phase 14: the quiet hum and machinery inside walkable ship interiors. */
	@SerializedName("ship_interior_ambient")
	public boolean shipInteriorAmbient = true;
	/**
	 * Phase 15: draws the detailed models of the ship interiors (panelled walls, consoles, screens,
	 * holograms) and the ship's hull around them. Off: the plain block rooms.
	 */
	@SerializedName("ship_interior_detail")
	public boolean shipInteriorDetail = true;
	/** Phase 11: the flight display (speed, height, lasers) while flying a starship. */
	@SerializedName("ship_hud")
	public boolean shipHud = true;
	/**
	 * Phase 13: players sitting in a starship are not drawn (the own player and others), so no
	 * oversized figure sticks out of the cockpit. Only the drawing changes.
	 */
	@SerializedName("ship_hide_pilot")
	public boolean shipHidePilot = true;
	/**
	 * Phase 13: players with a SaberCraft character or outfit are drawn without their armor, so the
	 * character stays recognizable. The armor still protects as always; only its look is hidden.
	 */
	@SerializedName("character_hide_armor")
	public boolean characterHideArmor = true;
	/** Phase 11: drawing and holstering a blaster are animated and heard. */
	@SerializedName("blaster_draw")
	public boolean blasterDraw = true;
	/** Phase 11: the menu fades a page in with a soft sweep when switching areas. */
	@SerializedName("menu_transitions")
	public boolean menuTransitions = true;

	/**
	 * Phase 12: volume of all lightsaber sounds (ignition, hum, swings, clashes, Force powers),
	 * 0..1, on top of Minecraft's sliders. 0 silences them.
	 */
	@SerializedName("saber_volume")
	public float saberVolume = 1.0F;
	/**
	 * Phase 12: the steady hum of a lit blade, 0..1, on top of {@code saber_volume}. Quieter than
	 * before by default, so a held saber does not drone in the foreground.
	 */
	@SerializedName("saber_hum_volume")
	public float saberHumVolume = 0.6F;
	/** Phase 12: the hum of a blade held still for a few seconds sinks to half its volume. */
	@SerializedName("saber_hum_idle_quiet")
	public boolean saberHumIdleQuiet = true;
	/** Phase 12: volume of the droids' voices, steps and abilities, 0..1. */
	@SerializedName("droid_volume")
	public float droidVolume = 0.8F;
	/** Phase 12: the droids' idle chatter (beeps without a reason). false: they only sound when they do something. */
	@SerializedName("droid_chatter")
	public boolean droidChatter = true;
	/** Phase 12: volume of the starships (engines, lasers, crashes), 0..1. */
	@SerializedName("ship_volume")
	public float shipVolume = 1.0F;

	/** Phase 12: the hint under the crosshair while looking at one of your droids (name, ability, keys). */
	@SerializedName("droid_target_hud")
	public boolean droidTargetHud = true;
	/**
	 * Phase 19: the language of SaberCraft's texts (story, menus, hints): {@code auto} (as
	 * Minecraft), {@code de_de} or {@code en_us}, independent of Minecraft's language.
	 */
	public String language = "auto";
	/** Phase 19: the story mode's hints: the objectives' marker, the eye that fills when someone notices you. */
	@SerializedName("story_marker")
	public boolean storyMarker = true;
	/** Phase 19: how fast the story's texts appear (0 at once, 1 normal, 2 slow). */
	@SerializedName("story_text_speed")
	public int storyTextSpeed = 1;
	/**
	 * Phase 24: the title screen and the menus before a world in SaberCraft's style: the space
	 * background, the sayings next to the logo and the buttons. Off: the vanilla menus.
	 */
	@SerializedName("title_screen")
	public boolean titleScreen = true;
	/**
	 * Phase 27: brightens the story's own darkening (the emergency lights, the alarm's pulsing,
	 * the lights failing as the ship shudders): 0 as made (the default), 1 brighter, 2 bright. The
	 * colours stay (red stays red), only less dark. Phase 31: 3 "Maximal" also lifts the dark places
	 * of the story's ships (the world's light and the rooms' dimming, see
	 * {@code client.story.StoryBrightness}); bright spots stay as they are.
	 */
	@SerializedName("story_brightness")
	public int storyBrightness = 0;
	/**
	 * Phase 27: the hints shown once (the menu key at the first start, how to use a crawl way),
	 * by name; and whether the menu key was moved off V (Simple Voice Chat's key) once.
	 */
	@SerializedName("hints_shown")
	public java.util.List<String> hintsShown = new java.util.ArrayList<>();
	@SerializedName("menu_key_moved")
	public boolean menuKeyMoved = false;

	/** Phase 31: the highest level of {@link #storyBrightness} ("Maximal"). */
	public static final int STORY_BRIGHTNESS_MAX = 3;
	public static final String CAMERA_COCKPIT = "cockpit";
	public static final String CAMERA_CHASE = "chase";
	public static final String CAMERA_KEEP = "keep";
	public static final String MODE_SABERCRAFT = "sabercraft";
	public static final String MODE_STARWARS = "starwars";

	/** Phase 27: how far the story's darkening is lifted (0 none .. 1 none of it left). */
	public float storyLift() {
		return switch (Mth.clamp(this.storyBrightness, 0, STORY_BRIGHTNESS_MAX)) {
			case 1 -> 0.4F;
			case 2 -> 0.7F;
			case 3 -> 0.85F;
			default -> 0.0F;
		};
	}

	/** Phase 27: whether a hint shown once was shown already. */
	public boolean shown(String hint) {
		return this.hintsShown != null && this.hintsShown.contains(hint);
	}

	/** Phase 27: marks a hint as shown (and saves). Returns whether it was new. */
	public static boolean markShown(String hint) {
		if (current.shown(hint)) {
			return false;
		}

		current.hintsShown.add(hint);
		save();
		return true;
	}

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
		if (!CAMERA_COCKPIT.equals(this.shipCamera) && !CAMERA_CHASE.equals(this.shipCamera) && !CAMERA_KEEP.equals(this.shipCamera)) {
			// Older settings files: the third-person switch decides between the new cockpit view and no change.
			this.shipCamera = this.shipThirdPerson ? CAMERA_COCKPIT : CAMERA_KEEP;
		}
		this.spaceSkyQuality = Mth.clamp(this.spaceSkyQuality, 0, 3);
		if (!"auto".equals(this.language) && !"de_de".equals(this.language) && !"en_us".equals(this.language)) {
			this.language = "auto";
		}
		this.storyTextSpeed = Mth.clamp(this.storyTextSpeed, 0, 2);
		this.storyBrightness = Mth.clamp(this.storyBrightness, 0, STORY_BRIGHTNESS_MAX);
		if (this.hintsShown == null) {
			this.hintsShown = new java.util.ArrayList<>();
		}
		if (this.planet == null) {
			this.planet = "";
		}
		this.planetTransitionSeconds = Mth.clamp(this.planetTransitionSeconds, 0.0F, 10.0F);
		this.musicVolume = Mth.clamp(this.musicVolume, 0.0F, 1.0F);
		this.planetAmbientVolume = Mth.clamp(this.planetAmbientVolume, 0.0F, 1.0F);
		this.musicCrossfadeSeconds = Mth.clamp(this.musicCrossfadeSeconds, 0.5F, 20.0F);
		this.starWarsVolume = Mth.clamp(this.starWarsVolume, 0.0F, 1.0F);
		this.saberVolume = Mth.clamp(this.saberVolume, 0.0F, 1.0F);
		this.saberHumVolume = Mth.clamp(this.saberHumVolume, 0.0F, 1.0F);
		this.droidVolume = Mth.clamp(this.droidVolume, 0.0F, 1.0F);
		this.shipVolume = Mth.clamp(this.shipVolume, 0.0F, 1.0F);
		if (!MODE_STARWARS.equals(this.musicMode)) {
			this.musicMode = MODE_SABERCRAFT;
		}
	}
}
