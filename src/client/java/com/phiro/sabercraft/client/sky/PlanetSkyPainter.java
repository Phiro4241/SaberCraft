package com.phiro.sabercraft.client.sky;

import org.joml.Vector3f;

/**
 * Paints the sky layer of a {@link PlanetPreset} into the six faces of a cube (3 x 2 tiles, the
 * same layout as the space sky), once for the day and once for the night. The layer lies over the
 * vanilla sky, fixed to the world: a haze band over the horizon, the planets and moons of the
 * preset, a glow of lava or city lights at night and, on Coruscant, a skyline all around.
 *
 * <p>Since Phase 9 each planet also has a far landscape on its horizon ({@link PlanetPreset.Horizon}):
 * dunes and mesas, giant trees, ice mountains, volcanoes, hills, and the details that go with it
 * (air traffic, an aurora, smoke plumes and lava, clouds, mist). It is only painted into the sky;
 * the real terrain stands in front of it and is not changed. {@link #paintTint} paints a plain
 * color layer for shader packs, which replace the vanilla sky color with their own.
 *
 * <p>Unlike the space sky the pixels carry a real alpha (straight, not premultiplied): the layer
 * covers the sky where it is solid and tints it where it is thin.
 */
public final class PlanetSkyPainter {
	private final PlanetPreset preset;
	private final int size;
	private final int width;
	private final boolean night;
	private final boolean details;
	private final float[] red;
	private final float[] green;
	private final float[] blue;
	private final float[] alpha;

	private PlanetSkyPainter(PlanetPreset preset, int size, boolean night, boolean details) {
		this.preset = preset;
		this.size = size;
		this.width = size * 3;
		this.night = night;
		this.details = details;
		int pixels = this.width * size * 2;
		this.red = new float[pixels];
		this.green = new float[pixels];
		this.blue = new float[pixels];
		this.alpha = new float[pixels];
	}

	/** Paints the day ({@code night} false) or the night layer; ARGB pixels, 3 x 2 tiles. */
	public static int[] paint(PlanetPreset preset, int size, boolean night) {
		return paint(preset, size, night, true);
	}

	/** Like {@link #paint(PlanetPreset, int, boolean)}; {@code details} false leaves out the horizon landscape. */
	public static int[] paint(PlanetPreset preset, int size, boolean night, boolean details) {
		PlanetSkyPainter painter = new PlanetSkyPainter(preset, size, night, details);
		painter.paintAll();
		return painter.pack();
	}

	/**
	 * The tint layer for shader packs: the planet's sky color over the whole sky, its fog color
	 * towards the horizon, semi-transparent. Shader packs draw their own sky and keep only part of
	 * what is drawn over it, so without this layer Mustafar's red or Tatooine's sand tone is lost.
	 */
	public static int[] paintTint(PlanetPreset preset, int size, boolean night) {
		PlanetSkyPainter painter = new PlanetSkyPainter(preset, size, night, false);
		Vector3f dir = new Vector3f();
		float[] sky = rgb(night ? preset.skyNight() : preset.skyDay());
		float[] fog = rgb(night ? preset.fogNight() : preset.fogDay());
		float opacity = (night ? 0.55F : 0.5F) * preset.strength();

		for (int face = 0; face < 6; face++) {
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					SpaceSkyPainter.direction(face, (x + 0.5F) / size, (y + 0.5F) / size, dir);
					float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, dir.y))));
					float low = clamp(1.0F - elevation / 25.0F);
					float a = opacity * clamp((elevation + 12.0F) / 10.0F);
					painter.over(painter.index(face, x, y), sky[0] + (fog[0] - sky[0]) * low, sky[1] + (fog[1] - sky[1]) * low,
							sky[2] + (fog[2] - sky[2]) * low, a);
				}
			}
		}

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

					if (this.details) {
						this.sky(i, dir, elevation, azimuth);
					}

					if (this.preset.skyline()) {
						this.skyline(i, elevation, azimuth);
					}

					if (this.details) {
						this.horizon(i, dir, elevation, azimuth);
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

	/** Volcanoes of Mustafar: azimuth in degrees, height and half width in degrees. */
	private static final float[][] VOLCANOES = {{35.0F, 10.0F, 16.0F}, {118.0F, 6.5F, 12.0F}, {205.0F, 13.0F, 19.0F}, {292.0F, 7.5F, 13.0F}};

	/**
	 * What is in the sky above the horizon landscape (Phase 9): the aurora of Hoth, clouds over
	 * Naboo and the smoke plumes of Mustafar's volcanoes.
	 */
	private void sky(int i, Vector3f dir, float elevation, float azimuth) {
		switch (this.preset.horizon()) {
			case ICE -> {
				if (this.night) {
					this.aurora(i, dir, elevation, azimuth);
				}
			}
			case HILLS -> this.clouds(i, dir, elevation);
			case VOLCANOES -> this.plumes(i, dir, elevation, azimuth);
			default -> {
			}
		}
	}

	/** Green curtains of light over the northern half of the sky, turning violet at the top. */
	private void aurora(int i, Vector3f dir, float elevation, float azimuth) {
		if (elevation < 8.0F || elevation > 70.0F) {
			return;
		}

		// Strongest towards the north (negative z), fading out to the east and west.
		float sector = clamp(-dir.z / (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z + 1.0E-6F) * 1.6F + 0.2F);
		float wave = ring(azimuth, 2.0F, 31.0F, 3);
		float center = 24.0F + 18.0F * wave;
		float band = (elevation - center) / (elevation < center ? 5.0F : 16.0F);
		float curtain = (float) Math.exp(-band * band);
		float rays = 0.45F + 0.55F * (float) Math.pow(0.5F + 0.5F * Math.sin(azimuth * 46.0F + wave * 18.0F), 3.0F);
		float flow = 0.5F + ring(azimuth, 5.0F, 47.0F, 2);
		float a = 0.6F * sector * curtain * rays * Math.min(1.0F, flow);
		float top = clamp((elevation - center) / 18.0F);
		this.over(i, 0.3F + 0.35F * top, 1.0F - 0.55F * top, 0.62F + 0.3F * top, a);
	}

	/** Fair-weather clouds over Naboo, white with grey-blue bellies, thinner at night. */
	private void clouds(int i, Vector3f dir, float elevation) {
		if (elevation < 5.0F || elevation > 55.0F) {
			return;
		}

		// Noise on a flat cloud deck overhead: towards the horizon the clouds crowd together.
		float deck = 1.0F / Math.max(0.08F, dir.y);
		float n = SpaceSkyPainter.fbm(dir.x * deck * 0.9F + 5.0F, 3.0F, dir.z * deck * 0.9F, 5);
		float cover = clamp((n - 0.53F) * 6.0F) * clamp((elevation - 5.0F) / 6.0F) * clamp((55.0F - elevation) / 25.0F);

		if (cover <= 0.0F) {
			return;
		}

		float belly = clamp((0.62F - n) * 6.0F);
		float light = this.night ? 0.16F : 1.0F;
		float r = (0.98F - 0.28F * belly) * light;
		float g = (0.98F - 0.22F * belly) * light;
		float b = (1.0F - 0.12F * belly) * light;
		this.over(i, r, g, b, cover * (this.night ? 0.5F : 0.85F));
	}

	/** Smoke rising from the craters of Mustafar's volcanoes and leaning with the wind. */
	private void plumes(int i, Vector3f dir, float elevation, float azimuth) {
		float degrees = (float) Math.toDegrees(azimuth) + 180.0F;

		for (float[] volcano : VOLCANOES) {
			float rise = elevation - volcano[1] * 0.9F;

			if (rise < -1.0F || rise > 45.0F) {
				continue;
			}

			float lean = rise * 0.45F;
			float off = angleBetween(degrees, volcano[0] + lean);
			float width = 1.6F + rise * 0.32F;
			float column = clamp(1.0F - Math.abs(off) / width);

			if (column <= 0.0F) {
				continue;
			}

			float smoke = SpaceSkyPainter.fbm(dir.x * 7.0F + volcano[0], dir.y * 7.0F - rise * 0.05F, dir.z * 7.0F, 4);
			float a = column * column * clamp(smoke * 1.8F - 0.25F) * clamp(1.0F - rise / 45.0F) * 0.85F;
			// Lit from below by the lava: orange near the crater, grey higher up.
			float ember = clamp(1.0F - rise / (this.night ? 14.0F : 7.0F));
			float base = this.night ? 0.05F : 0.2F;
			this.over(i, base + 0.75F * ember, base * 0.9F + 0.22F * ember, base * 0.85F + 0.04F * ember, a);
		}
	}

	/**
	 * The far landscape on the horizon: two rows, the far one paler in the haze. It reaches a few
	 * degrees below the horizon, so no gap shows between it and the fog of the real terrain.
	 */
	private void horizon(int i, Vector3f dir, float elevation, float azimuth) {
		PlanetPreset.Horizon type = this.preset.horizon();

		if (type == PlanetPreset.Horizon.NONE || elevation > 20.0F || elevation < -6.0F) {
			return;
		}

		if (type == PlanetPreset.Horizon.CITY) {
			this.traffic(i, elevation, azimuth);
			return;
		}

		float[] haze = rgb(this.preset.haze());
		float below = clamp((elevation + 6.0F) / 3.0F);

		for (int layer = 0; layer <= 1; layer++) {
			float h = profile(type, azimuth, layer);

			if (elevation > h) {
				continue;
			}

			float depth = h - elevation;
			float[] color = this.ground(type, azimuth, layer, depth, h);
			float mist = layer == 1 ? 0.5F : 0.18F;

			if (this.night) {
				float[] sky = rgb(this.preset.skyNight());
				color[0] = color[0] * 0.08F + sky[0] * 0.5F;
				color[1] = color[1] * 0.08F + sky[1] * 0.5F;
				color[2] = color[2] * 0.08F + sky[2] * 0.5F;
				mist *= 0.3F;
			}

			float r = color[0] + (haze[0] * (this.night ? 0.12F : 1.0F) - color[0]) * mist;
			float g = color[1] + (haze[1] * (this.night ? 0.12F : 1.0F) - color[1]) * mist;
			float b = color[2] + (haze[2] * (this.night ? 0.12F : 1.0F) - color[2]) * mist;
			// A soft top edge, a pixel or two wide.
			float edge = clamp(depth * 5.0F);
			this.over(i, r, g, b, edge * below);

			if (type == PlanetPreset.Horizon.VOLCANOES) {
				this.lava(i, azimuth, elevation, layer, depth);
			}

			break;
		}

		if (type == PlanetPreset.Horizon.FOREST && elevation < 5.0F) {
			// Mist among the trunks of the giant trees.
			float n = ring(azimuth, 4.0F, 71.0F, 3);
			float a = clamp(1.0F - (elevation + 1.0F) / 5.0F) * clamp(n * 1.2F - 0.1F) * (this.night ? 0.2F : 0.4F);
			float light = this.night ? 0.1F : 0.9F;
			this.over(i, light * 0.92F, light, light * 0.95F, a * below);
		}
	}

	/** Height in degrees of a row of the landscape at an angle around the horizon; row 1 is the far one. */
	private static float profile(PlanetPreset.Horizon type, float azimuth, int layer) {
		float degrees = (float) Math.toDegrees(azimuth) + 180.0F;

		return switch (type) {
			case DUNES -> layer == 0
					? 0.8F + 2.6F * clamp(ring(azimuth, 4.5F, 3.0F, 3) * 1.8F - 0.4F)
					// Mesas: flat tops with steep sides, only now and then.
					: 1.4F + 4.2F * smoothstep(0.52F, 0.66F, ring(azimuth, 3.0F, 17.0F, 3)) * (0.9F + 0.1F * ring(azimuth, 40.0F, 5.0F, 2));
			case FOREST -> {
				if (layer == 1) {
					yield 2.5F + 2.0F * ring(azimuth, 6.0F, 9.0F, 3);
				}

				// Single giant trees: narrow crowns on tall trunks, side by side.
				float width = 1.5F;
				int slot = (int) Math.floor(degrees / width);
				float within = degrees / width - slot;
				float tall = 3.5F + 4.0F * hash(slot, 5, 9);
				float crown = 1.0F - Math.abs(within - 0.5F) * 2.0F;
				// A rounded crown that the next tree overlaps a little.
				yield tall * (0.78F + 0.22F * (float) Math.sqrt(crown)) + 0.6F * ring(azimuth, 30.0F, 3.0F, 2);
			}
			case ICE -> layer == 0
					? 1.0F + 5.0F * ridge(azimuth, 5.0F, 23.0F)
					: 2.5F + 9.0F * ridge(azimuth, 3.2F, 41.0F);
			case VOLCANOES -> {
				float h = layer == 0 ? 0.8F + 1.6F * ring(azimuth, 8.0F, 13.0F, 3) : 1.0F + 2.0F * ridge(azimuth, 4.0F, 29.0F);

				for (float[] volcano : VOLCANOES) {
					float d = Math.abs(angleBetween(degrees, volcano[0])) / volcano[2];

					if (d < 1.0F) {
						// A cone with a cut-off top: the crater.
						float cone = volcano[1] * (float) Math.pow(1.0F - d, 1.35F);
						float height = Math.min(cone, volcano[1] * 0.88F) * (layer == 0 ? 1.0F : 0.0F);
						h = Math.max(h, height);
					}
				}

				yield h;
			}
			case HILLS -> layer == 0
					? 0.6F + 2.4F * ring(azimuth, 3.5F, 7.0F, 3)
					: 1.5F + 3.5F * ring(azimuth, 2.2F, 19.0F, 4);
			default -> 0.0F;
		};
	}

	/** The color of the landscape at a point, by day. */
	private float[] ground(PlanetPreset.Horizon type, float azimuth, int layer, float depth, float height) {
		return switch (type) {
			case DUNES -> {
				// The crests of the dunes catch the light, the hollows lie in shadow.
				float crest = clamp(1.0F - depth / 1.2F);
				yield layer == 0 ? mixed(0xC09468, 0xEAC898, crest) : mixed(0xB88A64, 0xD8AE84, crest * 0.6F);
			}
			case FOREST -> layer == 0 ? mixed(0x1C3020, 0x2E5232, clamp(1.0F - depth / 2.5F)) : rgb(0x3A6050);
			case ICE -> {
				// The side towards the evening sun is bright, the other one blue in shadow.
				// Faces of light and shadow that slant down from the ridges.
				float k = 14.0F;
				float faces = SpaceSkyPainter.fbm((float) Math.cos(azimuth) * k + depth * 0.45F, (float) Math.sin(azimuth) * k - depth * 0.2F,
						depth * 0.3F + layer * 7.0F, 3);
				float lit = smoothstep(0.44F, 0.56F, faces);
				float snow = clamp(1.0F - depth / 4.0F);
				yield layer == 0 ? mixed(0x7E96B8, 0xF2F6FC, lit * 0.8F + snow * 0.2F) : mixed(0x9AAEC8, 0xE4ECF6, lit * 0.7F);
			}
			case VOLCANOES -> layer == 0 ? mixed(0x140A08, 0x2A1410, clamp(depth / 3.0F)) : rgb(0x30160F);
			case HILLS -> {
				float meadow = clamp(1.0F - depth / 2.5F);
				yield layer == 0 ? mixed(0x3E7A3A, 0x6AA858, meadow) : mixed(0x5E8E78, 0x7EAA94, meadow);
			}
			default -> rgb(0x808080);
		};
	}

	/** Lava running down the near slopes and pooling at the foot of the volcanoes; bright at night. */
	private void lava(int i, float azimuth, float elevation, int layer, float depth) {
		if (layer != 0) {
			return;
		}

		float degrees = (float) Math.toDegrees(azimuth) + 180.0F;
		float a = 0.0F;

		for (float[] volcano : VOLCANOES) {
			float off = angleBetween(degrees, volcano[0]);

			if (Math.abs(off) > volcano[2]) {
				continue;
			}

			float cone = volcano[1] * (float) Math.pow(1.0F - Math.abs(off) / volcano[2], 1.35F);

			if (elevation < 0.0F || elevation > Math.min(cone, volcano[1] * 0.88F)) {
				continue;
			}

			// Two or three streams from the crater, meandering down and spreading towards the foot.
			float fall = 1.0F - elevation / (volcano[1] * 0.88F);
			float wobble = (SpaceSkyPainter.fbm(elevation * 0.35F, volcano[0], off * 0.2F, 2) - 0.5F) * 3.0F * fall;
			float spread = (off - wobble) / (0.6F + fall * volcano[2] * 0.35F);
			float streams = (float) Math.abs(Math.sin(spread * 2.4F + volcano[0]));
			float line = clamp((streams - 0.86F) * 9.0F) * clamp(depth / 0.5F);
			a = Math.max(a, line * (0.5F + 0.5F * (1.0F - fall)));
		}

		// Glowing lava fields along the foot.
		a = Math.max(a, clamp(1.0F - (elevation + 0.5F) / 1.5F) * 0.45F);
		this.over(i, 1.0F, 0.42F, 0.08F, a * (this.night ? 1.0F : 0.6F));
	}

	/**
	 * Lanes of air traffic over Coruscant's skyline: rows of small craft, dark specks by day, white
	 * and red lights at night.
	 */
	private void traffic(int i, float elevation, float azimuth) {
		float degrees = (float) Math.toDegrees(azimuth) + 180.0F;
		float[] lanes = {6.5F, 9.5F, 13.0F, 17.5F};

		for (int lane = 0; lane < lanes.length; lane++) {
			float center = lanes[lane] + 0.6F * (float) Math.sin(Math.toRadians(degrees * (2 + lane)) + lane);
			float off = Math.abs(elevation - center);
			float thickness = 0.12F + 0.03F * lane;

			if (off > thickness * 3.0F) {
				continue;
			}

			// A faint trail of the lane itself.
			this.over(i, 0.85F, 0.8F, 0.7F, (this.night ? 0.12F : 0.05F) * clamp(1.0F - off / (thickness * 3.0F)));
			float spacing = 0.55F + lane * 0.15F;
			int slot = (int) Math.floor(degrees / spacing);
			float within = degrees / spacing - slot;

			if (hash(slot, lane, 11) > 0.45F && Math.abs(within - 0.5F) < 0.16F && off < thickness) {
				boolean red = hash(slot, lane, 13) > 0.7F;

				if (this.night) {
					this.over(i, 1.0F, red ? 0.3F : 0.95F, red ? 0.25F : 0.85F, 0.95F);
				} else {
					this.over(i, 0.22F, 0.22F, 0.26F, 0.85F);
				}
			}
		}
	}

	/** Seamless noise around the horizon, 0..1 with more contrast than plain fbm. */
	private static float ring(float azimuth, float frequency, float seed, int octaves) {
		float n = SpaceSkyPainter.fbm((float) Math.cos(azimuth) * frequency + seed, (float) Math.sin(azimuth) * frequency, seed * 1.7F, octaves);
		return clamp((n - 0.5F) * 2.2F + 0.5F);
	}

	/** Sharp ridges around the horizon, for mountains. */
	private static float ridge(float azimuth, float frequency, float seed) {
		float n = SpaceSkyPainter.fbm((float) Math.cos(azimuth) * frequency + seed, (float) Math.sin(azimuth) * frequency, seed * 1.3F, 5);
		float r = 1.0F - Math.abs(n - 0.5F) * 2.0F;
		return clamp(r * r * 1.3F - 0.15F);
	}

	/** Signed difference between two angles in degrees, -180..180. */
	private static float angleBetween(float a, float b) {
		float d = (a - b) % 360.0F;
		return d > 180.0F ? d - 360.0F : d < -180.0F ? d + 360.0F : d;
	}

	private static float smoothstep(float from, float to, float value) {
		float t = clamp((value - from) / (to - from));
		return t * t * (3.0F - 2.0F * t);
	}

	private static float[] mixed(int from, int to, float amount) {
		float[] a = rgb(from);
		float[] b = rgb(to);
		return new float[] {a[0] + (b[0] - a[0]) * amount, a[1] + (b[1] - a[1]) * amount, a[2] + (b[2] - a[2]) * amount};
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
