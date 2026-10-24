package com.phiro.sabercraft.client.sky;

import java.util.Random;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Paints the space sky of a {@link SpaceSkyPreset} into the six faces of a cube, laid out as 3 x 2
 * tiles in one image. Runs on a background thread, the result is a plain ARGB array.
 *
 * <p>Layers from back to front: nebulae (3D noise, brightest along the milky way band), stars
 * (more of them in the band, in different colors and sizes), spiral galaxies, then planets and
 * moons as lit spheres, the gas giants with cloud bands and rings.
 *
 * <p>The alpha of a pixel is its brightness and the color is divided by it, so with translucent
 * blending the picture adds onto the dark night sky, and empty space lets the sky color through
 * at dusk.
 */
public final class SpaceSkyPainter {
	/** Face normals and the two axes across each face: +X, -X, +Y, -Y, +Z, -Z. */
	static final float[][][] FACES = {
			{{1, 0, 0}, {0, 0, -1}, {0, -1, 0}},
			{{-1, 0, 0}, {0, 0, 1}, {0, -1, 0}},
			{{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},
			{{0, -1, 0}, {1, 0, 0}, {0, 0, -1}},
			{{0, 0, 1}, {1, 0, 0}, {0, -1, 0}},
			{{0, 0, -1}, {-1, 0, 0}, {0, -1, 0}},
	};

	/** The largest angle between a face normal and a point on the face: the cube corner, 54.7 degrees. */
	private static final double FACE_REACH = Math.acos(1.0 / Math.sqrt(3.0));

	private final SpaceSkyPreset preset;
	private final int size;
	private final int width;
	private final float[] red;
	private final float[] green;
	private final float[] blue;
	private final boolean[] solid;

	private SpaceSkyPainter(SpaceSkyPreset preset, int size) {
		this.preset = preset;
		this.size = size;
		this.width = size * 3;
		int pixels = this.width * size * 2;
		this.red = new float[pixels];
		this.green = new float[pixels];
		this.blue = new float[pixels];
		this.solid = new boolean[pixels];
	}

	/** Paints the sky with {@code size} pixels per cube side; returns ARGB pixels, 3 x 2 tiles. */
	public static int[] paint(SpaceSkyPreset preset, int size) {
		SpaceSkyPainter painter = new SpaceSkyPainter(preset, size);
		painter.background();
		painter.stars();
		painter.planets();
		return painter.pack();
	}

	private void background() {
		SpaceSkyPreset p = this.preset;
		float[] a = rgb(p.nebulaA());
		float[] b = rgb(p.nebulaB());
		float ox = (p.seed() & 1023) * 0.37F;
		float oz = (p.seed() >> 10 & 1023) * 0.53F;
		Vector3f dir = new Vector3f();

		for (int face = 0; face < 6; face++) {
			for (int y = 0; y < this.size; y++) {
				for (int x = 0; x < this.size; x++) {
					direction(face, (x + 0.5F) / this.size, (y + 0.5F) / this.size, dir);
					float band = band(dir);
					float n1 = fbm(dir.x * 2.2F + ox, dir.y * 2.2F, dir.z * 2.2F + oz, 5);
					float n2 = fbm(dir.x * 4.7F - oz, dir.y * 4.7F + ox, dir.z * 4.7F, 4);
					float main = clamp((n1 - 0.42F) * 2.4F) * (0.25F + 0.75F * band) * p.nebulaStrength();
					float second = clamp((n2 - 0.5F) * 2.6F) * band * p.nebulaStrength() * 0.8F;
					// Dark dust lanes cut through the glow.
					float dust = clamp((fbm(dir.x * 9.0F, dir.y * 9.0F + oz, dir.z * 9.0F - ox, 3) - 0.55F) * 3.0F);
					float glow = band * 0.05F;
					int i = this.index(face, x, y);
					float keep = 1.0F - 0.7F * dust * band;
					this.red[i] = (a[0] * (main + glow) + b[0] * second) * keep;
					this.green[i] = (a[1] * (main + glow) + b[1] * second) * keep;
					this.blue[i] = (a[2] * (main + glow) + b[2] * second) * keep;
				}
			}
		}

		for (SpaceSkyPreset.Galaxy galaxy : p.galaxies()) {
			this.galaxy(galaxy);
		}
	}

	private void galaxy(SpaceSkyPreset.Galaxy galaxy) {
		Vector3f center = fromAngles(galaxy.yaw(), galaxy.pitch());
		Vector3f[] basis = tangents(center, galaxy.tilt());
		float radius = (float) Math.toRadians(galaxy.size());
		float[] color = rgb(galaxy.color());
		this.forEachNear(center, radius * 1.6F, (i, dir) -> {
			float x = dir.dot(basis[0]) / radius;
			float y = dir.dot(basis[1]) / radius / galaxy.squash();
			float r = (float) Math.sqrt(x * x + y * y);
			float theta = (float) Math.atan2(y, x);
			float arms = 0.5F + 0.5F * (float) Math.cos(2.0F * theta - 5.5F * Math.log(r + 0.06F));
			float bright = (float) (Math.exp(-r * 3.2F) * (0.25F + 0.75F * arms * arms) + Math.exp(-r * r * 70.0F) * 1.4F) * 0.85F;
			this.red[i] += color[0] * bright;
			this.green[i] += color[1] * bright * 0.95F;
			this.blue[i] += color[2] * bright * (0.8F + 0.4F * arms);
		});
	}

	private void stars() {
		Random random = new Random(this.preset.seed() * 31L + 7L);
		// More pixels need more stars, but not four times as many, or the sky turns grey.
		int count = (int) (this.preset.stars() * Math.sqrt(this.size / 512.0));
		Vector3f dir = new Vector3f();
		float pixelAngle = 2.0F / this.size;

		for (int n = 0; n < count; n++) {
			randomDirection(random, dir);

			// Extra stars in the band of the milky way.
			if (random.nextFloat() > 0.35F + 0.65F * band(dir)) {
				continue;
			}

			float brightness = (float) Math.pow(random.nextFloat(), 5.0) * 1.6F + 0.08F;
			float temperature = random.nextFloat();
			float r = temperature < 0.2F ? 0.7F : temperature > 0.85F ? 1.0F : 0.95F;
			float g = temperature < 0.2F ? 0.8F : temperature > 0.85F ? 0.78F : 0.93F;
			float b = temperature < 0.2F ? 1.0F : temperature > 0.85F ? 0.55F : 0.88F;
			float radius = (0.55F + brightness * 0.9F) * pixelAngle;
			this.splat(dir, radius, r * brightness, g * brightness, b * brightness);
		}
	}

	/** A soft round dot around {@code dir}. */
	private void splat(Vector3f dir, float radius, float r, float g, float b) {
		Vector3f center = new Vector3f(dir);
		this.forEachNear(center, radius * 2.0F, (i, at) -> {
			float d = at.distance(center) / radius;
			float w = (float) Math.exp(-d * d * 1.5F);
			this.red[i] += r * w;
			this.green[i] += g * w;
			this.blue[i] += b * w;
		});
	}

	private void planets() {
		for (SpaceSkyPreset.Planet planet : this.preset.planets()) {
			this.planet(planet);
		}
	}

	private void planet(SpaceSkyPreset.Planet planet) {
		Vector3f center = fromAngles(planet.yaw(), planet.pitch());
		Vector3f[] basis = tangents(center, 18.0F);
		float radius = (float) Math.toRadians(planet.size());
		Vector3f light = fromAngles(planet.lightYaw(), planet.lightPitch());
		// The light in the planet's own frame: x and y across the disc, z towards the viewer.
		Vector3f lightLocal = new Vector3f(light.dot(basis[0]), light.dot(basis[1]), -light.dot(center)).normalize();
		float[] bright = rgb(planet.light());
		float[] dark = rgb(planet.dark());
		float[] ringColor = rgb(planet.ring());
		boolean ring = planet.ring() != 0;
		long seed = this.preset.seed() + (long) (planet.yaw() * 1000.0F);

		this.forEachNear(center, radius * (ring ? 2.4F : 1.05F), (i, dir) -> {
			float x = dir.dot(basis[0]) / radius;
			float y = dir.dot(basis[1]) / radius;
			float rr = x * x + y * y;
			// The ring, seen almost edge-on: an ellipse around the planet.
			float ry = y / 0.28F;
			float ringDistance = (float) Math.sqrt(x * x + ry * ry);
			boolean onRing = ring && ringDistance > 1.35F && ringDistance < 2.25F;
			boolean ringInFront = onRing && (ry > 0.0F || rr > 1.0F);

			if (rr <= 1.0F && !ringInFront) {
				float z = (float) Math.sqrt(1.0F - rr);
				float diffuse = Math.max(0.0F, x * lightLocal.x + y * lightLocal.y + z * lightLocal.z);
				float pattern;

				if (planet.bands() > 0.0F) {
					float lat = y * planet.bands();
					pattern = 0.5F + 0.5F * (float) Math.sin(lat * 2.2F + fbm(x * 3.0F + seed % 97, y * 9.0F, z * 3.0F, 3) * 4.0F);
				} else {
					// A moon: grey with darker maria and a few craters.
					pattern = clamp(fbm(x * 4.0F + seed % 97, y * 4.0F, z * 4.0F, 4) * 1.6F - 0.3F);
				}

				float shade = 0.04F + 0.96F * diffuse;
				// A thin bright edge on the lit side, like an atmosphere.
				float rim = (float) Math.pow(1.0F - z, 3.0) * diffuse * 0.6F;
				this.red[i] = (bright[0] + (dark[0] - bright[0]) * pattern) * shade + rim * bright[0];
				this.green[i] = (bright[1] + (dark[1] - bright[1]) * pattern) * shade + rim * bright[1];
				this.blue[i] = (bright[2] + (dark[2] - bright[2]) * pattern) * shade + rim * bright[2];
				this.solid[i] = true;
			} else if (onRing) {
				float stripes = 0.55F + 0.45F * (float) Math.sin(ringDistance * 38.0F) * (float) Math.sin(ringDistance * 11.0F);
				float alpha = clamp((ringDistance - 1.35F) * 8.0F) * clamp((2.25F - ringDistance) * 8.0F) * stripes;
				// The planet's shadow falls on the ring behind it.
				float lit = ry < 0.0F && Math.abs(x) < 1.0F ? 0.35F : 1.0F;
				this.red[i] = this.red[i] * (1.0F - alpha) + ringColor[0] * alpha * lit;
				this.green[i] = this.green[i] * (1.0F - alpha) + ringColor[1] * alpha * lit;
				this.blue[i] = this.blue[i] * (1.0F - alpha) + ringColor[2] * alpha * lit;
				this.solid[i] |= alpha > 0.5F;
			}
		});
	}

	private int[] pack() {
		int[] out = new int[this.red.length];

		for (int i = 0; i < out.length; i++) {
			float r = clamp(this.red[i]);
			float g = clamp(this.green[i]);
			float b = clamp(this.blue[i]);
			float alpha = this.solid[i] ? 1.0F : Math.max(r, Math.max(g, b));

			if (alpha < 1.0F / 255.0F) {
				out[i] = 0;
				continue;
			}

			// Colour divided by alpha, so translucent blending adds the full colour onto the dark sky.
			int ai = Math.round(alpha * 255.0F);
			out[i] = ai << 24 | Math.round(Math.min(1.0F, r / alpha) * 255.0F) << 16
					| Math.round(Math.min(1.0F, g / alpha) * 255.0F) << 8 | Math.round(Math.min(1.0F, b / alpha) * 255.0F);
		}

		return out;
	}

	private interface PixelAction {
		void apply(int index, Vector3f direction);
	}

	/** Calls {@code action} for every pixel within {@code angle} radians of {@code center}. */
	private void forEachNear(Vector3f center, float angle, PixelAction action) {
		float cos = (float) Math.cos(angle);
		Vector3f dir = new Vector3f();

		for (int face = 0; face < 6; face++) {
			float[][] f = FACES[face];

			// Skip faces the area cannot reach: every point of a face is at most 54.7 degrees from its normal.
			if (center.x * f[0][0] + center.y * f[0][1] + center.z * f[0][2] < Math.cos(Math.min(Math.PI, FACE_REACH + angle))) {
				continue;
			}

			int[] box = this.boundsOnFace(face, center, angle);

			if (box == null) {
				continue;
			}

			for (int y = box[2]; y <= box[3]; y++) {
				for (int x = box[0]; x <= box[1]; x++) {
					direction(face, (x + 0.5F) / this.size, (y + 0.5F) / this.size, dir);

					if (dir.dot(center) >= cos) {
						action.apply(this.index(face, x, y), dir);
					}
				}
			}
		}
	}

	/**
	 * Pixel bounds on a face of a small area around {@code center}, or the whole face for large
	 * areas. The bounds come from points around the edge of the area projected onto the face.
	 */
	private int @Nullable [] boundsOnFace(int face, Vector3f center, float angle) {
		if (angle > 0.6F) {
			return new int[] {0, this.size - 1, 0, this.size - 1};
		}

		float[][] f = FACES[face];
		Vector3f[] basis = tangents(center, 0.0F);
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float minU = Float.MAX_VALUE;
		float maxU = -Float.MAX_VALUE;
		float minV = Float.MAX_VALUE;
		float maxV = -Float.MAX_VALUE;
		Vector3f point = new Vector3f();

		for (int k = 0; k <= 16; k++) {
			if (k == 16) {
				point.set(center);
			} else {
				double a = k * Math.PI / 8.0;
				point.set(center).mul(cos).fma((float) Math.cos(a) * sin, basis[0]).fma((float) Math.sin(a) * sin, basis[1]);
			}

			float depth = point.x * f[0][0] + point.y * f[0][1] + point.z * f[0][2];
			// Points far off to the side are not on this face anyway.
			if (depth < 0.2F) {
				continue;
			}

			float u = (point.x * f[1][0] + point.y * f[1][1] + point.z * f[1][2]) / depth;
			float v = (point.x * f[2][0] + point.y * f[2][1] + point.z * f[2][2]) / depth;
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minV = Math.min(minV, v);
			maxV = Math.max(maxV, v);
		}

		if (minU > maxU) {
			return null;
		}

		// A little extra for the curve between the points; the angle test decides per pixel.
		float margin = (maxU - minU + maxV - minV) * 0.05F + 2.0F / this.size;
		int x0 = (int) Math.floor((minU - margin + 1.0F) * 0.5F * this.size);
		int x1 = (int) Math.ceil((maxU + margin + 1.0F) * 0.5F * this.size);
		int y0 = (int) Math.floor((minV - margin + 1.0F) * 0.5F * this.size);
		int y1 = (int) Math.ceil((maxV + margin + 1.0F) * 0.5F * this.size);

		if (x1 < 0 || y1 < 0 || x0 >= this.size || y0 >= this.size) {
			return null;
		}

		return new int[] {Math.max(0, x0), Math.min(this.size - 1, x1), Math.max(0, y0), Math.min(this.size - 1, y1)};
	}

	private int index(int face, int x, int y) {
		int tileX = face % 3 * this.size;
		int tileY = face / 3 * this.size;
		return (tileY + y) * this.width + tileX + x;
	}

	/** Direction of the point (s, t) in 0..1 on a face. */
	static void direction(int face, float s, float t, Vector3f out) {
		float[][] f = FACES[face];
		float a = 2.0F * s - 1.0F;
		float b = 2.0F * t - 1.0F;
		out.set(f[0][0] + a * f[1][0] + b * f[2][0], f[0][1] + a * f[1][1] + b * f[2][1], f[0][2] + a * f[1][2] + b * f[2][2]).normalize();
	}

	private float band(Vector3f dir) {
		float d = dir.dot(this.preset.band()) / this.preset.bandWidth();
		return (float) Math.exp(-d * d);
	}

	static Vector3f fromAngles(float yaw, float pitch) {
		double y = Math.toRadians(yaw);
		double p = Math.toRadians(pitch);
		return new Vector3f((float) (Math.cos(p) * Math.sin(y)), (float) Math.sin(p), (float) (Math.cos(p) * Math.cos(y)));
	}

	/** Two unit vectors across the sky at {@code center}, turned by {@code tilt} degrees. */
	private static Vector3f[] tangents(Vector3f center, float tilt) {
		Vector3f up = Math.abs(center.y) > 0.95F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f right = new Vector3f(up).cross(center).normalize();
		Vector3f down = new Vector3f(center).cross(right).normalize();
		double angle = Math.toRadians(tilt);
		float c = (float) Math.cos(angle);
		float s = (float) Math.sin(angle);
		Vector3f a = new Vector3f(right).mul(c).add(new Vector3f(down).mul(s));
		Vector3f b = new Vector3f(down).mul(c).sub(new Vector3f(right).mul(s));
		return new Vector3f[] {a, b};
	}

	private static void randomDirection(Random random, Vector3f out) {
		float z = random.nextFloat() * 2.0F - 1.0F;
		float angle = random.nextFloat() * (float) Math.PI * 2.0F;
		float r = (float) Math.sqrt(1.0F - z * z);
		out.set(r * (float) Math.cos(angle), z, r * (float) Math.sin(angle));
	}

	private static float[] rgb(int color) {
		return new float[] {(color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F};
	}

	private static float clamp(float value) {
		return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
	}

	/** Fractal value noise, roughly 0..1. */
	static float fbm(float x, float y, float z, int octaves) {
		float sum = 0.0F;
		float amplitude = 0.5F;
		float total = 0.0F;

		for (int i = 0; i < octaves; i++) {
			sum += noise(x, y, z) * amplitude;
			total += amplitude;
			x *= 2.03F;
			y *= 2.03F;
			z *= 2.03F;
			amplitude *= 0.5F;
		}

		return sum / total;
	}

	private static float noise(float x, float y, float z) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		float xf = smooth(x - xi);
		float yf = smooth(y - yi);
		float zf = smooth(z - zi);
		float x00 = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), xf);
		float x10 = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), xf);
		float x01 = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), xf);
		float x11 = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), xf);
		return lerp(lerp(x00, x10, yf), lerp(x01, x11, yf), zf);
	}

	private static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ h >>> 13) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
