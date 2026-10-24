package com.phiro.sabercraft.client.sky;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;

/**
 * The Star Wars planet skies (Phase 8). With a planet chosen, the Overworld looks like it from the
 * ground: the sky and fog take the planet's colors (through the values vanilla computes, so day,
 * night, weather and Sodium keep working), a painted layer adds its haze, moons, gas giant, city
 * skyline or lava glow ({@link PlanetSkyPainter}), Tatooine gets its second sun, and snow or ash
 * drift through the air. The terrain and world generation are not changed at all. With "none"
 * chosen (the default) nothing of this runs.
 *
 * <p>Only the Overworld turns into the planet; the Nether and the End stay as they are.
 */
public final class PlanetSky {
	/** Pixels per cube side for the quality settings low, medium, high (shared with the space sky). */
	private static final int[] SIZES = {256, 512, 1024};
	/** Luminance of a clear vanilla day sky and day fog, to tell how much daylight there is. */
	private static final float DAY_SKY = luminance(0x78A7FF);
	private static final float DAY_FOG = luminance(0xC0D8FF);

	private static @Nullable DynamicTexture day;
	private static @Nullable DynamicTexture night;
	private static @Nullable GpuBuffer cube;
	private static int cubeSize;
	private static @Nullable String shownKey;
	private static @Nullable String pendingKey;
	private static @Nullable CompletableFuture<int[][]> pending;
	private static int pendingSize;

	private PlanetSky() {
	}

	/** The chosen planet, if the camera is in the Overworld. */
	public static Optional<PlanetPreset> current() {
		Minecraft client = Minecraft.getInstance();
		return client.level == null ? Optional.empty() : of(client.level.dimension());
	}

	private static Optional<PlanetPreset> of(ResourceKey<Level> dimension) {
		return dimension == Level.OVERWORLD ? PlanetPreset.byId(ImmersionConfig.get().planet) : Optional.empty();
	}

	/** The night sky of the chosen planet for the space sky, if the dimension is the Overworld. */
	public static Optional<SpaceSkyPreset> nightSky(ResourceKey<Level> dimension) {
		return of(dimension).map(PlanetPreset::night);
	}

	/** The sky color: the planet's day or night color, as bright as the vanilla sky is right now. */
	public static int skyColor(int vanilla) {
		return current().map(planet -> blend(vanilla, planet.skyDay(), planet.skyNight(), DAY_SKY, planet.strength())).orElse(vanilla);
	}

	/** The fog color in the open air, changed in place. */
	public static void fogColor(Vector4f color) {
		current().ifPresent(planet -> {
			int vanilla = ARGB.colorFromFloat(1.0F, color.x, color.y, color.z);
			int changed = blend(vanilla, planet.fogDay(), planet.fogNight(), DAY_FOG, planet.strength());
			color.set(ARGB.redFloat(changed), ARGB.greenFloat(changed), ARGB.blueFloat(changed), color.w);
		});
	}

	/** The glow of sunrise and sunset, in the planet's color; the alpha (strength) stays vanilla. */
	public static int sunsetColor(int vanilla) {
		return current().map(planet -> {
			int rgb = mix(vanilla & 0xFFFFFF, planet.sunset(), planet.strength());
			return vanilla & 0xFF000000 | rgb;
		}).orElse(vanilla);
	}

	/** Angle in degrees from the sun to the second sun, 0 when there is only one. */
	public static float secondSun() {
		return current().map(PlanetPreset::secondSun).orElse(0.0F);
	}

	/**
	 * Draws the planet's layer over the sky, fixed to the world. The day and the night picture
	 * fade into each other with the height of the sun; rain thins them out.
	 */
	public static void render(RenderTarget target, Matrix4f pose, float sunAngle, float rainBrightness) {
		Optional<PlanetPreset> planet = current();

		if (planet.isEmpty()) {
			return;
		}

		prepare(planet.get(), ImmersionConfig.get().spaceSkyQuality);

		if (day == null || night == null) {
			return;
		}

		if (cube == null) {
			cube = SpaceSky.createCube(cubeSize, "SaberCraft planet sky cube");
		}

		// The sun angle is 0 at noon and half a turn at midnight.
		float daylight = Mth.clamp(Mth.cos(sunAngle) * 2.2F + 0.5F, 0.0F, 1.0F);
		float weather = 0.45F + 0.55F * rainBrightness;

		if (daylight < 0.99F) {
			SpaceSky.drawCube(target, pose, cube, night, (1.0F - daylight) * weather, "SaberCraft planet sky (night)");
		}

		if (daylight > 0.01F) {
			SpaceSky.drawCube(target, pose, cube, day, daylight * weather, "SaberCraft planet sky (day)");
		}
	}

	/** Starts painting the pictures of a planet, or uploads them once they are painted. */
	private static void prepare(PlanetPreset planet, int quality) {
		int size = SIZES[Math.clamp(quality, 0, SIZES.length - 1)];
		String key = planet.id() + "@" + size;

		if (pending != null) {
			if (!pending.isDone()) {
				return;
			}

			int[][] pixels = pending.getNow(null);
			pending = null;

			if (pixels != null) {
				upload(pixels, pendingSize);
				shownKey = pendingKey;
			}
		}

		if (!key.equals(shownKey) && !key.equals(pendingKey)) {
			pendingKey = key;
			pendingSize = size;
			long start = System.nanoTime();
			pending = CompletableFuture.supplyAsync(() -> {
				int[][] pixels = {PlanetSkyPainter.paint(planet, size, false), PlanetSkyPainter.paint(planet, size, true)};
				SaberCraft.LOGGER.info("Painted the planet sky '{}' at {} px in {} ms", planet.id(), size, (System.nanoTime() - start) / 1_000_000L);
				return pixels;
			}, Util.backgroundExecutor()).exceptionally(error -> {
				SaberCraft.LOGGER.error("Could not paint the planet sky", error);
				return null;
			});
		} else if (key.equals(shownKey)) {
			pendingKey = null;
		}
	}

	private static void upload(int[][] pixels, int size) {
		if (day != null) {
			day.close();
		}

		if (night != null) {
			night.close();
		}

		day = SpaceSky.texture(pixels[0], size, "SaberCraft planet sky (day)");
		night = SpaceSky.texture(pixels[1], size, "SaberCraft planet sky (night)");

		if (cube != null && cubeSize != size) {
			cube.close();
			cube = null;
		}

		cubeSize = size;
	}

	/** Whether the pictures of the chosen planet are painted and uploaded, for tests. */
	public static boolean ready() {
		return current().map(planet -> shownKey != null && shownKey.startsWith(planet.id() + "@") && pending == null).orElse(false);
	}

	/** Snow or ash drifting down around the player, a few flakes per tick. */
	public static void tick(Minecraft client) {
		if (client.level == null || client.player == null || client.isPaused() || !ImmersionConfig.get().planetParticles) {
			return;
		}

		Optional<PlanetPreset> planet = current();

		if (planet.isEmpty() || planet.get().particles() == PlanetPreset.Particles.NONE) {
			return;
		}

		ParticleOptions particle = planet.get().particles() == PlanetPreset.Particles.SNOW ? ParticleTypes.SNOWFLAKE : ParticleTypes.ASH;
		RandomSource random = client.level.getRandom();
		int count = client.options.particles().get().ordinal() == 0 ? 6 : 3;

		for (int i = 0; i < count; i++) {
			double x = client.player.getX() + (random.nextDouble() - 0.5) * 24.0;
			double z = client.player.getZ() + (random.nextDouble() - 0.5) * 24.0;
			double y = client.player.getY() + 2.0 + random.nextDouble() * 10.0;

			// Only in the open air, not in caves or under a roof.
			int ground = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));

			if (y > ground) {
				client.level.addParticle(particle, x, y, z, 0.0, -0.05, 0.0);
			}
		}
	}

	private static int blend(int vanilla, int planetDay, int planetNight, float dayLuminance, float strength) {
		float daylight = Mth.clamp(luminance(vanilla) / dayLuminance, 0.0F, 1.0F);
		int target = mix(planetNight, planetDay, daylight);
		return 0xFF000000 | mix(vanilla & 0xFFFFFF, target, strength);
	}

	private static float luminance(int rgb) {
		return (0.3F * (rgb >> 16 & 255) + 0.59F * (rgb >> 8 & 255) + 0.11F * (rgb & 255)) / 255.0F;
	}

	private static int mix(int from, int to, float amount) {
		int r = Math.round(Mth.lerp(amount, from >> 16 & 255, to >> 16 & 255));
		int g = Math.round(Mth.lerp(amount, from >> 8 & 255, to >> 8 & 255));
		int b = Math.round(Mth.lerp(amount, from & 255, to & 255));
		return r << 16 | g << 8 | b;
	}
}
