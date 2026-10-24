package com.phiro.sabercraft.client.sky;

import java.util.List;

import org.joml.Vector3f;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * What the space sky of one dimension shows. All of it is painted at runtime from these numbers
 * by {@link SpaceSkyPainter}; no images are shipped. Each dimension gets its own view: the
 * Overworld a calm night sky with a ringed gas giant, the End a deep, red-violet sky with a large
 * galaxy, every other dimension a sky made from its name.
 *
 * @param name           short name for the log
 * @param seed           seed of the noise and the stars
 * @param nebulaA        main color of the nebulae (RGB)
 * @param nebulaB        second color of the nebulae (RGB)
 * @param nebulaStrength brightness of the nebulae, 0..1
 * @param band           normal of the plane of the milky way band
 * @param bandWidth      width of the band, smaller is narrower
 * @param stars          number of stars at medium quality
 * @param galaxies       distant galaxies
 * @param planets        planets and moons, painted in this order (later ones in front)
 */
public record SpaceSkyPreset(String name, long seed, int nebulaA, int nebulaB, float nebulaStrength, Vector3f band, float bandWidth, int stars,
		List<Galaxy> galaxies, List<Planet> planets) {
	public static final SpaceSkyPreset OVERWORLD = new SpaceSkyPreset("overworld", 4241L, 0x3A4FB8, 0x9B3FA8, 0.55F,
			new Vector3f(0.3F, 0.9F, 0.2F).normalize(), 0.35F, 9000,
			List.of(new Galaxy(60.0F, 35.0F, 7.0F, 30.0F, 0.45F, 0xFFE8C8)),
			List.of(
					new Planet(200.0F, 25.0F, 9.0F, 0xD9A066, 0x8C5A3A, 9.0F, 0xCDB89A, 120.0F, 30.0F),
					new Planet(214.0F, 31.0F, 2.2F, 0xB8B8C0, 0x6E6E78, 0.0F, 0, 120.0F, 30.0F),
					new Planet(320.0F, 50.0F, 3.5F, 0x4F8FD8, 0x1E3E78, 3.0F, 0, 40.0F, 10.0F)));
	public static final SpaceSkyPreset END = new SpaceSkyPreset("end", 6661L, 0x8A2E7A, 0xD24A3A, 0.8F,
			new Vector3f(0.1F, 0.95F, -0.3F).normalize(), 0.5F, 12000,
			List.of(new Galaxy(0.0F, 40.0F, 22.0F, -20.0F, 0.5F, 0xFFD8F0), new Galaxy(150.0F, -20.0F, 6.0F, 50.0F, 0.35F, 0xD8E8FF)),
			List.of(
					new Planet(250.0F, 15.0F, 12.0F, 0x8A3A2E, 0x3A1410, 5.0F, 0, 300.0F, 20.0F),
					new Planet(100.0F, 60.0F, 3.0F, 0xE8E0F0, 0x7A7090, 0.0F, 0, 300.0F, 20.0F)));

	/** The view of a dimension. */
	public static SpaceSkyPreset of(ResourceKey<Level> dimension) {
		if (dimension == Level.OVERWORLD) {
			return OVERWORLD;
		} else if (dimension == Level.END) {
			return END;
		}

		// Other dimensions (from data packs or mods): a sky of their own, always the same for a name.
		Identifier id = dimension.identifier();
		long seed = id.toString().hashCode();
		int hue = (int) Math.floorMod(seed, 360L);
		return new SpaceSkyPreset(id.toString(), seed, hsv(hue, 0.6F, 0.75F), hsv((hue + 70) % 360, 0.6F, 0.8F), 0.6F,
				new Vector3f(0.2F, 0.9F, 0.4F).normalize(), 0.4F, 9000,
				List.of(new Galaxy((hue * 7) % 360, 30.0F, 9.0F, 15.0F, 0.4F, 0xFFF0E0)),
				List.of(new Planet((hue * 3) % 360, 28.0F, 7.0F, hsv((hue + 180) % 360, 0.45F, 0.8F), hsv((hue + 200) % 360, 0.5F, 0.35F),
						6.0F, 0, (hue * 3 + 90) % 360, 25.0F)));
	}

	private static int hsv(int hue, float saturation, float value) {
		return java.awt.Color.HSBtoRGB(hue / 360.0F, saturation, value) & 0xFFFFFF;
	}

	/**
	 * A spiral galaxy.
	 *
	 * @param yaw    direction, degrees around the vertical
	 * @param pitch  height above the horizon in degrees
	 * @param size   radius in degrees
	 * @param tilt   rotation of the disc in the sky, degrees
	 * @param squash how flat the disc looks, 1 = seen from the front
	 * @param color  color of the core (RGB)
	 */
	public record Galaxy(float yaw, float pitch, float size, float tilt, float squash, int color) {
	}

	/**
	 * A planet or moon, lit from one side.
	 *
	 * @param yaw        direction, degrees around the vertical
	 * @param pitch      height above the horizon in degrees
	 * @param size       radius in degrees
	 * @param light      color of the lit surface (RGB)
	 * @param dark       second surface color for bands and spots (RGB)
	 * @param bands      number of cloud bands, 0 for a cratered moon
	 * @param ring       ring color (RGB), 0 for none
	 * @param lightYaw   direction the sunlight comes from
	 * @param lightPitch height of the sunlight
	 */
	public record Planet(float yaw, float pitch, float size, int light, int dark, float bands, int ring, float lightYaw, float lightPitch) {
	}
}
