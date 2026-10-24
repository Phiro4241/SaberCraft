package com.phiro.sabercraft.client.sky;

import org.joml.Vector3f;

/**
 * Paints the sky layer of a {@link PlanetPreset} into the six faces of a cube (3 x 2 tiles, the
 * same layout as the space sky), once for the day and once for the night. The layer lies over the
 * vanilla sky, fixed to the world: a haze band over the horizon, the planets and moons of the
 * preset, a glow of lava or city lights at night and, on Coruscant, a skyline all around.
 *
 * <p>Unlike the space sky the pixels carry a real alpha (straight, not premultiplied): the layer
 * covers the sky where it is solid and tints it where it is thin.
 */
public final class PlanetSkyPainter {
	private final PlanetPreset preset;
	private final int size;
	private final int width;
	private final boolean night;
	private final float[] red;
	private final float[] green;
	private final float[] blue;
	private final float[] alpha;

	private PlanetSkyPainter(PlanetPreset preset, int size, boolean night) {
		this.preset = preset;
		this.size = size;
		this.width = size * 3;
		this.night = night;
		int pixels = this.width * size * 2;
		this.red = new float[pixels];
		this.green = new float[pixels];
		this.blue = new float[pixels];
		this.alpha = new float[pixels];
	}

	/** Paints the day ({@code night} false) or the night layer; ARGB pixels, 3 x 2 tiles. */
	public static int[] paint(PlanetPreset preset, int size, boolean night) {
		PlanetSkyPainter painter = new PlanetSkyPainter(preset, size, night);
		painter.paintAll();
		return painter.pack();
	}

	private void paintAll() {
		Vector3f dir = new Vector3f();
		float[] haze = rgb(this.preset.haze());
		float[] glow = rgb(this.preset.nightGlow());

		for (int face = 0; face < 6; face++) {
			for (int y = 0; y < this.size; y++) {
				for (int x = 0; x < this.size; x++) {
					SpaceSkyPainter.direction(face, (x + 0.5F) / this.size, (y + 0.5F) / this.size, dir);
					int i = this.index(face, x, y);
					float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, dir.y))));
					float azimuth = (float) Math.atan2(dir.x, dir.z);
					this.haze(i, dir, elevation, haze);

					if (this.night && this.preset.nightGlow() != 0) {
						this.glow(i, dir, elevation, glow);
					}

					for (SpaceSkyPreset.Planet body : this.preset.bodies()) {
						this.body(i, dir, body);
					}

					if (this.preset.skyline()) {
						this.skyline(i, elevation, azimuth);
					}
				}
			}
		}
	}

	/** The haze band: thick at the horizon, thinning out upwards, with a little cloudiness. */
	private void haze(int i, Vector3f dir, float elevation, float[] color) {
		float height = this.preset.hazeHeight();
		float t = clamp(1.0F - elevation / height);
		float cloud = 0.75F + 0.5F * SpaceSkyPainter.fbm(dir.x * 3.0F, dir.y * 9.0F, dir.z * 3.0F, 3);
		float a = this.preset.hazeAlpha() * t * t * Math.min(1.0F, cloud);
		float light = this.night ? 0.12F : 1.0F;
		this.over(i, color[0] * light, color[1] * light, color[2] * light, a);
	}

	/** Lava or city glow low over the horizon at night. */
	private void glow(int i, Vector3f dir, float elevation, float[] color) {
		float t = clamp(1.0F - elevation / 9.0F);
		float flicker = 0.6F + 0.8F * SpaceSkyPainter.fbm(dir.x * 6.0F + 11.0F, dir.y * 14.0F, dir.z * 6.0F, 3);
		this.over(i, color[0], color[1], color[2], 0.55F * t * t * t * Math.min(1.0F, flicker));
	}

	/** A planet or moon. By day its dark side melts into the sky and the lit side is a bit pale. */
	private void body(int i, Vector3f dir, SpaceSkyPreset.Planet planet) {
		Vector3f center = SpaceSkyPainter.fromAngles(planet.yaw(), planet.pitch());
		float radius = (float) Math.toRadians(planet.size());
		boolean ring = planet.ring() != 0;

		if (dir.dot(center) < Math.cos(radius * (ring ? 2.4F : 1.05F))) {
			return;
		}

		Vector3f[] basis = SpaceSkyPainter.tangents(center, 18.0F);
		Vector3f light = SpaceSkyPainter.fromAngles(planet.lightYaw(), planet.lightPitch());
		Vector3f lightLocal = new Vector3f(light.dot(basis[0]), light.dot(basis[1]), -light.dot(center)).normalize();
		float[] bright = rgb(planet.light());
		float[] dark = rgb(planet.dark());
		float x = dir.dot(basis[0]) / radius;
		float y = dir.dot(basis[1]) / radius;
		float rr = x * x + y * y;
		float ry = y / 0.28F;
		float ringDistance = (float) Math.sqrt(x * x + ry * ry);
		boolean onRing = ring && ringDistance > 1.35F && ringDistance < 2.25F;
		boolean ringInFront = onRing && (ry > 0.0F || rr > 1.0F);

		if (rr <= 1.0F && !ringInFront) {
			float z = (float) Math.sqrt(1.0F - rr);
			float diffuse = Math.max(0.0F, x * lightLocal.x + y * lightLocal.y + z * lightLocal.z);
			float seed = planet.yaw() * 0.37F;
			float pattern = planet.bands() > 0.0F
					? 0.5F + 0.5F * (float) Math.sin(y * planet.bands() * 2.2F + SpaceSkyPainter.fbm(x * 3.0F + seed, y * 9.0F, z * 3.0F, 3) * 4.0F)
					: clamp(SpaceSkyPainter.fbm(x * 4.0F + seed, y * 4.0F, z * 4.0F, 4) * 1.6F - 0.3F);
			float shade = 0.05F + 0.95F * diffuse;
			float r = (bright[0] + (dark[0] - bright[0]) * pattern) * shade;
			float g = (bright[1] + (dark[1] - bright[1]) * pattern) * shade;
			float b = (bright[2] + (dark[2] - bright[2]) * pattern) * shade;
			// Soft edge, so the disc does not look cut out.
			float edge = clamp((1.0F - rr) * 12.0F);
			// By day the sky shines through the unlit part; at night the whole disc is solid.
			float a = this.night ? edge : edge * (0.25F + 0.65F * clamp(diffuse * 1.5F));
			this.over(i, r, g, b, a);
		} else if (onRing) {
			float[] ringColor = rgb(planet.ring());
			float stripes = 0.55F + 0.45F * (float) Math.sin(ringDistance * 38.0F) * (float) Math.sin(ringDistance * 11.0F);
			float a = clamp((ringDistance - 1.35F) * 8.0F) * clamp((2.25F - ringDistance) * 8.0F) * stripes * (this.night ? 0.9F : 0.6F);
			float lit = ry < 0.0F && Math.abs(x) < 1.0F ? 0.35F : 1.0F;
			this.over(i, ringColor[0] * lit, ringColor[1] * lit, ringColor[2] * lit, a);
		}
	}

	/**
	 * An endless city around the horizon: two rows of towers of different heights, the far row
	 * paler in the haze. At night thousands of windows light up.
	 */
	private void skyline(int i, float elevation, float azimuth) {
		float degrees = (float) Math.toDegrees(azimuth) + 180.0F;

		for (int layer = 1; layer >= 0; layer--) {
			float h = towerHeight(degrees, layer);

			if (elevation > h) {
				continue;
			}

			float far = layer == 1 ? 1.0F : 0.0F;
			float r;
			float g;
			float b;

			if (this.night) {
				float base = 0.03F + 0.03F * far;
				r = base;
				g = base;
				b = base * 1.4F;
				// Windows: a fine grid on the towers, some of them lit in warm or cool light.
				int column = (int) Math.floor(degrees / 0.32F);
				int row = (int) Math.floor((elevation + 20.0F) / 0.28F);
				float pick = hash(column, row, layer);

				if (pick > 0.62F && (column & 1) == 0 && (row & 1) == 0) {
					boolean cool = hash(column, row, 7) > 0.7F;
					float strength = far > 0 ? 0.55F : 1.0F;
					r = (cool ? 0.62F : 1.0F) * strength;
					g = (cool ? 0.8F : 0.78F) * strength;
					b = (cool ? 1.0F : 0.45F) * strength;
				}
			} else {
				float[] haze = rgb(this.preset.haze());
				float shade = 0.36F + 0.08F * hash((int) Math.floor(degrees / 0.9F), layer, 3);
				r = shade + (haze[0] - shade) * 0.55F * far;
				g = (shade + 0.02F) + (haze[1] - shade) * 0.55F * far;
				b = (shade + 0.07F) + (haze[2] - shade) * 0.55F * far;
			}

			this.over(i, r, g, b, 1.0F);
			return;
		}
	}

	/** Height of the towers in degrees above the horizon at an angle around it. */
	private static float towerHeight(float degrees, int layer) {
		float width = layer == 0 ? 1.6F : 1.1F;
		int slot = (int) Math.floor(degrees / width);
		float pick = hash(slot, layer, 1);
		float height = (layer == 0 ? 2.5F : 4.5F) + pick * (layer == 0 ? 6.0F : 7.0F);

		// Now and then a much taller spire.
		if (hash(slot, layer, 2) > 0.93F) {
			float within = degrees / width - slot;
			height += 7.0F * (1.0F - Math.abs(within - 0.5F) * 2.0F);
		}

		return height;
	}

	private void over(int i, float r, float g, float b, float a) {
		if (a <= 0.0F) {
			return;
		}

		a = Math.min(1.0F, a);
		float out = a + this.alpha[i] * (1.0F - a);

		if (out <= 0.0F) {
			return;
		}

		this.red[i] = (r * a + this.red[i] * this.alpha[i] * (1.0F - a)) / out;
		this.green[i] = (g * a + this.green[i] * this.alpha[i] * (1.0F - a)) / out;
		this.blue[i] = (b * a + this.blue[i] * this.alpha[i] * (1.0F - a)) / out;
		this.alpha[i] = out;
	}

	private int[] pack() {
		int[] out = new int[this.red.length];

		for (int i = 0; i < out.length; i++) {
			int a = Math.round(clamp(this.alpha[i]) * 255.0F);
			out[i] = a == 0 ? 0 : a << 24 | Math.round(clamp(this.red[i]) * 255.0F) << 16 | Math.round(clamp(this.green[i]) * 255.0F) << 8
					| Math.round(clamp(this.blue[i]) * 255.0F);
		}

		return out;
	}

	private int index(int face, int x, int y) {
		return (face / 3 * this.size + y) * this.width + face % 3 * this.size + x;
	}

	private static float[] rgb(int color) {
		return new float[] {(color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F};
	}

	private static float clamp(float value) {
		return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
	}

	private static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ h >>> 13) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}
}
