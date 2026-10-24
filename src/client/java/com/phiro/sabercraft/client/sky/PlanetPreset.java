package com.phiro.sabercraft.client.sky;

import java.util.List;
import java.util.Optional;

import org.joml.Vector3f;

/**
 * The look of one Star Wars planet (Phase 8): the colors of its sky and fog by day and by night,
 * a haze over the horizon, things in the sky (a second sun, moons, a gas giant, a city skyline)
 * and its night sky. The terrain and the world generation are not touched: only the sky, the fog
 * color and a few particles in the air change. Everything is painted at runtime from these
 * numbers ({@link PlanetSkyPainter}); no images are shipped.
 *
 * @param id          name in the config and in the language files ({@code sabercraft.planet.<id>})
 * @param skyDay      sky color at noon (RGB)
 * @param skyNight    sky color at midnight (RGB)
 * @param fogDay      fog and horizon color at noon (RGB)
 * @param fogNight    fog color at midnight (RGB)
 * @param sunset      color of the sunrise and sunset glow (RGB)
 * @param strength    how far the colors move from the vanilla ones, 0..1
 * @param haze        color of the haze band over the horizon (RGB)
 * @param hazeAlpha   opacity of the haze right at the horizon, 0..1
 * @param hazeHeight  height of the haze band in degrees
 * @param nightGlow   glow over the horizon at night (RGB), for lava or city lights; 0 for none
 * @param skyline     whether a city skyline stands around the horizon
 * @param horizon     what stands on the horizon and what else is in the sky (Phase 9)
 * @param secondSun   angle in degrees between the sun and a second sun, 0 for one sun
 * @param bodies      planets and moons that stay in the sky by day and night (world-fixed)
 * @param particles   what drifts through the air
 * @param night       the night sky (stars, nebulae, moons) when the space sky is on
 */
public record PlanetPreset(String id, int skyDay, int skyNight, int fogDay, int fogNight, int sunset, float strength, int haze, float hazeAlpha,
		float hazeHeight, int nightGlow, boolean skyline, Horizon horizon, float secondSun, List<SpaceSkyPreset.Planet> bodies, Particles particles,
		SpaceSkyPreset night) {
	/** Desert planet with two suns: a pale, hot sky, sand haze, warm evenings. */
	public static final PlanetPreset TATOOINE = new PlanetPreset("tatooine", 0xC9D4DC, 0x0E1222, 0xE8D2A8, 0x1C1A24, 0xF08A3A, 0.85F,
			0xE6C890, 0.55F, 14.0F, 0, false, Horizon.DUNES, 9.0F,
			List.of(new SpaceSkyPreset.Planet(150.0F, 38.0F, 2.6F, 0xD8D0C0, 0x8A8070, 0.0F, 0, 300.0F, 25.0F),
					new SpaceSkyPreset.Planet(170.0F, 52.0F, 1.6F, 0xC8B8A8, 0x7A6A5A, 0.0F, 0, 300.0F, 25.0F)),
			Particles.NONE,
			new SpaceSkyPreset("tatooine", 1977L, 0x5A4A8A, 0xA06A3A, 0.35F, new Vector3f(0.4F, 0.85F, 0.3F).normalize(), 0.3F, 11000,
					List.of(new SpaceSkyPreset.Galaxy(80.0F, 45.0F, 5.0F, 20.0F, 0.4F, 0xFFE8C8)),
					List.of(new SpaceSkyPreset.Planet(300.0F, 30.0F, 3.0F, 0xE0D8C8, 0x7A7060, 0.0F, 0, 120.0F, 20.0F))));
	/** City planet: a smoggy amber sky over an endless skyline that lights up at night. */
	public static final PlanetPreset CORUSCANT = new PlanetPreset("coruscant", 0x9DB2C8, 0x140E1C, 0xB8A898, 0x2A1A2A, 0xF0A060, 0.7F,
			0xA89888, 0.5F, 10.0F, 0xE08A40, true, Horizon.CITY, 0.0F,
			List.of(new SpaceSkyPreset.Planet(60.0F, 48.0F, 1.8F, 0xD0D0D8, 0x707080, 0.0F, 0, 200.0F, 20.0F)),
			Particles.NONE,
			new SpaceSkyPreset("coruscant", 1999L, 0x4A3A6A, 0x8A4A3A, 0.15F, new Vector3f(0.2F, 0.9F, 0.3F).normalize(), 0.3F, 2500,
					List.of(), List.of(new SpaceSkyPreset.Planet(60.0F, 48.0F, 1.8F, 0xD0D0D8, 0x707080, 0.0F, 0, 200.0F, 20.0F))));
	/** Forest moon: a soft green-blue sky with the ringed gas giant hanging over the trees. */
	public static final PlanetPreset ENDOR = new PlanetPreset("endor", 0x8FB8D8, 0x0A1418, 0xA8C8B8, 0x0E1C1A, 0xE8A070, 0.6F,
			0x9CC0A8, 0.45F, 9.0F, 0, false, Horizon.FOREST, 0.0F,
			List.of(new SpaceSkyPreset.Planet(225.0F, 28.0F, 13.0F, 0x7FA6C8, 0x3E5E86, 7.0F, 0xB8C8D0, 120.0F, 30.0F),
					new SpaceSkyPreset.Planet(110.0F, 55.0F, 1.5F, 0xC8C8C8, 0x6A6A6A, 0.0F, 0, 120.0F, 30.0F)),
			Particles.NONE,
			new SpaceSkyPreset("endor", 1983L, 0x2A6A5A, 0x3A4A8A, 0.45F, new Vector3f(0.3F, 0.9F, -0.2F).normalize(), 0.35F, 10000,
					List.of(new SpaceSkyPreset.Galaxy(20.0F, 50.0F, 7.0F, -30.0F, 0.45F, 0xE8F0FF)), List.of()));
	/** Ice planet: a pale, cold sky, white mist over the horizon, snow in the air, two moons. */
	public static final PlanetPreset HOTH = new PlanetPreset("hoth", 0xB8D0E8, 0x0C1426, 0xDCE8F4, 0x18243A, 0xB8C8F0, 0.8F,
			0xE8F0FA, 0.6F, 12.0F, 0, false, Horizon.ICE, 0.0F,
			List.of(new SpaceSkyPreset.Planet(290.0F, 34.0F, 2.8F, 0xE0E8F0, 0x8090A8, 0.0F, 0, 160.0F, 15.0F),
					new SpaceSkyPreset.Planet(310.0F, 22.0F, 1.7F, 0xD0D8E8, 0x707C90, 0.0F, 0, 160.0F, 15.0F)),
			Particles.SNOW,
			new SpaceSkyPreset("hoth", 1980L, 0x2A5AA8, 0x6A9AD8, 0.5F, new Vector3f(0.1F, 0.95F, 0.3F).normalize(), 0.4F, 12000,
					List.of(new SpaceSkyPreset.Galaxy(200.0F, 40.0F, 8.0F, 10.0F, 0.5F, 0xE8F0FF)), List.of()));
	/** Volcanic planet: a dark red, smoky sky, a lava glow over the horizon and falling ash. */
	public static final PlanetPreset MUSTAFAR = new PlanetPreset("mustafar", 0x5A2418, 0x1A0604, 0x6A2A18, 0x2A0A04, 0xFF5A1A, 0.95F,
			0x3A1A14, 0.7F, 16.0F, 0xFF4A10, false, Horizon.VOLCANOES, 0.0F,
			List.of(), Particles.ASH,
			new SpaceSkyPreset("mustafar", 2005L, 0x8A1A0A, 0x4A1A2A, 0.6F, new Vector3f(0.2F, 0.9F, 0.4F).normalize(), 0.5F, 3000,
					List.of(), List.of()));
	/** Green lake planet: a clear, deep blue sky, soft light, a large pale moon and a distant one. */
	public static final PlanetPreset NABOO = new PlanetPreset("naboo", 0x6FA4E8, 0x0A1430, 0xB8D4F0, 0x101C34, 0xF0B080, 0.55F,
			0xC8DCF0, 0.35F, 8.0F, 0, false, Horizon.HILLS, 0.0F,
			List.of(new SpaceSkyPreset.Planet(40.0F, 32.0F, 5.0F, 0xE8E4DC, 0x8A8A90, 0.0F, 0, 100.0F, 25.0F),
					new SpaceSkyPreset.Planet(75.0F, 50.0F, 2.0F, 0xD8C8B0, 0x8A7A68, 0.0F, 0, 100.0F, 25.0F)),
			Particles.NONE,
			new SpaceSkyPreset("naboo", 1999L, 0x3A5AB8, 0x8A4AA8, 0.55F, new Vector3f(0.3F, 0.9F, 0.2F).normalize(), 0.35F, 10000,
					List.of(new SpaceSkyPreset.Galaxy(160.0F, 35.0F, 9.0F, 25.0F, 0.45F, 0xFFE8D8)), List.of()));

	/** All planets in menu order. */
	public static final List<PlanetPreset> ALL = List.of(TATOOINE, CORUSCANT, ENDOR, HOTH, MUSTAFAR, NABOO);

	/** The planet with this id, or empty for "none" and unknown ids. */
	public static Optional<PlanetPreset> byId(String id) {
		return ALL.stream().filter(planet -> planet.id.equals(id)).findFirst();
	}

	/**
	 * The far landscape painted on the horizon of the sky layer, and the details that go with it
	 * (Phase 9). It is only painted, far away behind the real terrain, which stays untouched.
	 */
	public enum Horizon {
		/** Nothing on the horizon. */
		NONE,
		/** Tatooine: soft dunes in front, flat-topped mesas far behind. */
		DUNES,
		/** Coruscant: the skyline (see {@code skyline}) and lanes of air traffic over it. */
		CITY,
		/** Endor: a wall of giant trees with mist at their feet. */
		FOREST,
		/** Hoth: jagged ice mountains, lit on one side; an aurora at night. */
		ICE,
		/** Mustafar: volcanoes with lava running down and smoke plumes rising. */
		VOLCANOES,
		/** Naboo: rolling green hills and white clouds. */
		HILLS
	}

	/** What drifts through the air around the player. */
	public enum Particles {
		NONE,
		SNOW,
		ASH
	}
}
