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
import com.phiro.sabercraft.client.render.ShaderCompat;

/**
 * The Star Wars planet skies (Phase 8). With a planet chosen, the Overworld looks like it from the
 * ground: the sky and fog take the planet's colors (through the values vanilla computes, so day,
 * night, weather and Sodium keep working), a painted layer adds its haze, moons, gas giant, city
 * skyline or lava glow ({@link PlanetSkyPainter}), Tatooine gets its second sun, and snow or ash
 * drift through the air. The terrain and world generation are not changed at all. With "none"
 * chosen (the default) nothing of this runs.
 *
 * <p>Only the Overworld turns into the planet; the Nether and the End stay as they are.
 *
 * <p>Since Phase 9 the layer also holds a far landscape on the horizon (option "Horizon and sky
 * details"), and with an Iris shader pack a plain tint layer in the planet's sky color is drawn
 * under it (option "Planet tint with shaders"), because shader packs replace the vanilla sky color.
 *
 * <p>Since Phase 10 a newly chosen planet fades in over the old sky (colors, layer, sun and moon),
 * and switching the planet off fades back to vanilla. Each planet also has its own sun and moon
 * ({@link PlanetCelestials}), and the layer can be painted at an "ultra" size.
 */
public final class PlanetSky {
	/** Pixels per cube side for the quality settings low, medium, high and ultra (Phase 10; shared with the space sky). */
	private static final int[] SIZES = {256, 512, 1024, 1536};
	/** Luminance of a clear vanilla day sky and day fog, to tell how much daylight there is. */
	private static final float DAY_SKY = luminance(0x78A7FF);
	private static final float DAY_FOG = luminance(0xC0D8FF);
	/** Pixels per cube side of the tint layer for shader packs: a soft gradient needs no more. */
	private static final int TINT_SIZE = 64;

	private static @Nullable DynamicTexture day;
	private static @Nullable DynamicTexture night;
	private static @Nullable GpuBuffer cube;
	private static @Nullable DynamicTexture tintDay;
	private static @Nullable DynamicTexture tintNight;
	private static @Nullable GpuBuffer tintCube;
	private static int cubeSize;
	private static @Nullable String shownKey;
	private static @Nullable String pendingKey;
	private static @Nullable CompletableFuture<int[][]> pending;
	private static int pendingSize;
	/** The planet whose pictures are shown, and the one that fades out (Phase 10). */
	private static @Nullable PlanetPreset shown;
	private static @Nullable PlanetPreset previous;
	private static @Nullable DynamicTexture previousDay;
	private static @Nullable DynamicTexture previousNight;
	private static @Nullable GpuBuffer previousCube;
	private static long transitionStart = Long.MIN_VALUE / 2;

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

	/** How far the fade from the previous to the shown planet is, 0..1 (Phase 10). */
	private static float transition() {
		float seconds = ImmersionConfig.get().planetTransitionSeconds;

		if (seconds <= 0.0F) {
			return 1.0F;
		}

		return Mth.clamp((Util.getMillis() - transitionStart) / (seconds * 1000.0F), 0.0F, 1.0F);
	}

	/** Whether the camera is in the Overworld, the only dimension that becomes a planet. */
	private static boolean inOverworld() {
		Minecraft client = Minecraft.getInstance();
		return client.level != null && client.level.dimension() == Level.OVERWORLD;
	}

	/**
	 * Follows the choice in the settings. A new planet starts to fade in once its pictures are
	 * painted (see {@link #prepare}); switching it off starts the fade back to vanilla at once.
	 */
	private static void follow() {
		if (!inOverworld()) {
			return;
		}

		Optional<PlanetPreset> target = PlanetPreset.byId(ImmersionConfig.get().planet);

		if (target.isEmpty() && shown != null) {
			startTransition(null);
		}
	}

	private static void startTransition(@Nullable PlanetPreset next) {
		// The pictures of the planet that fades out are kept until the fade is over.
		closePrevious();
		previous = shown;
		previousDay = day;
		previousNight = night;
		previousCube = cube;
		day = null;
		night = null;
		cube = null;
		shownKey = null;
		shown = next;
		transitionStart = Util.getMillis();
	}

	private static void closePrevious() {
		if (previousDay != null) {
			previousDay.close();
		}

		if (previousNight != null) {
			previousNight.close();
		}

		if (previousCube != null) {
			previousCube.close();
		}

		previousDay = null;
		previousNight = null;
		previousCube = null;
		previous = null;
	}

	/** The night sky of the chosen planet for the space sky, if the dimension is the Overworld. */
	public static Optional<SpaceSkyPreset> nightSky(ResourceKey<Level> dimension) {
		return of(dimension).map(PlanetPreset::night);
	}

	/** The sky color: the planet's day or night color, as bright as the vanilla sky is right now. */
	public static int skyColor(int vanilla) {
		return faded(vanilla, planet -> blend(vanilla, planet.skyDay(), planet.skyNight(), DAY_SKY, planet.strength()));
	}

	/** The fog color in the open air, changed in place. */
	public static void fogColor(Vector4f color) {
		int vanilla = ARGB.colorFromFloat(1.0F, color.x, color.y, color.z);
		int changed = faded(vanilla, planet -> blend(vanilla, planet.fogDay(), planet.fogNight(), DAY_FOG, planet.strength()));

		if (changed != vanilla) {
			color.set(ARGB.redFloat(changed), ARGB.greenFloat(changed), ARGB.blueFloat(changed), color.w);
		}
	}

	/** The glow of sunrise and sunset, in the planet's color; the alpha (strength) stays vanilla. */
	public static int sunsetColor(int vanilla) {
		int rgb = faded(vanilla & 0xFFFFFF, planet -> mix(vanilla & 0xFFFFFF, planet.sunset(), planet.strength()));
		return vanilla & 0xFF000000 | rgb & 0xFFFFFF;
	}

	/**
	 * A color of the shown planet, faded from the previous planet (or vanilla) while a new one comes
	 * in. Vanilla outside the Overworld and without a planet.
	 */
	private static int faded(int vanilla, java.util.function.ToIntFunction<PlanetPreset> color) {
		if (!inOverworld()) {
			return vanilla;
		}

		follow();
		float f = transition();
		int to = shown != null ? color.applyAsInt(shown) : vanilla;

		if (f >= 1.0F) {
			return to;
		}

		int from = previous != null ? color.applyAsInt(previous) : vanilla;
		return 0xFF000000 & to | mix(from & 0xFFFFFF, to & 0xFFFFFF, f);
	}

	/** The planet that rules the sun and moon right now: the new one from half-way through a fade. */
	private static @Nullable PlanetPreset leading() {
		if (!inOverworld()) {
			return null;
		}

		follow();
		return transition() >= 0.5F ? shown : previous;
	}

	/** Angle in degrees from the sun to the second sun, 0 when there is only one. */
	public static float secondSun() {
		PlanetPreset planet = leading();
		return planet != null ? planet.secondSun() : 0.0F;
	}

	/** The planet's own sun and moon, or null for the vanilla ones (Phase 10). */
	public static @Nullable PlanetCelestials celestials() {
		PlanetPreset planet = leading();
		return planet != null && ImmersionConfig.get().planetCelestials ? PlanetCelestials.of(planet) : null;
	}

	/**
	 * Draws the planet's layer over the sky, fixed to the world. The day and the night picture
	 * fade into each other with the height of the sun; rain thins them out.
	 */
	public static void render(RenderTarget target, Matrix4f pose, float sunAngle, float rainBrightness) {
		if (!inOverworld()) {
			return;
		}

		Optional<PlanetPreset> planet = current();
		follow();

		if (planet.isPresent()) {
			prepare(planet.get(), ImmersionConfig.get().spaceSkyQuality, ImmersionConfig.get().planetHorizon);
		}

		// The sun angle is 0 at noon and half a turn at midnight.
		float daylight = Mth.clamp(Mth.cos(sunAngle) * 2.2F + 0.5F, 0.0F, 1.0F);
		float weather = 0.45F + 0.55F * rainBrightness;
		float f = transition();

		if (f >= 1.0F && previous != null) {
			closePrevious();
		}

		if (previousDay != null && previousNight != null && previousCube != null) {
			// The planet that fades out.
			float fade = (1.0F - f) * weather;

			if (daylight < 0.99F) {
				SpaceSky.drawCube(target, pose, previousCube, previousNight, (1.0F - daylight) * fade, "SaberCraft planet sky (night, fading out)");
			}

			if (daylight > 0.01F) {
				SpaceSky.drawCube(target, pose, previousCube, previousDay, daylight * fade, "SaberCraft planet sky (day, fading out)");
			}
		}

		if (shown == null || day == null || night == null) {
			return;
		}

		if (cube == null) {
			cube = SpaceSky.createCube(cubeSize, "SaberCraft planet sky cube");
		}

		weather *= f;

		if (tinting() && tintDay != null && tintNight != null) {
			if (tintCube == null) {
				tintCube = SpaceSky.createCube(TINT_SIZE, "SaberCraft planet tint cube");
			}

			if (daylight < 0.99F) {
				SpaceSky.drawCube(target, pose, tintCube, tintNight, (1.0F - daylight) * weather, "SaberCraft planet tint (night)");
			}

			if (daylight > 0.01F) {
				SpaceSky.drawCube(target, pose, tintCube, tintDay, daylight * weather, "SaberCraft planet tint (day)");
			}
		}

		if (daylight < 0.99F) {
			SpaceSky.drawCube(target, pose, cube, night, (1.0F - daylight) * weather, "SaberCraft planet sky (night)");
		}

		if (daylight > 0.01F) {
			SpaceSky.drawCube(target, pose, cube, day, daylight * weather, "SaberCraft planet sky (day)");
		}
	}

	/** Starts painting the pictures of a planet, or uploads them once they are painted. */
	private static void prepare(PlanetPreset planet, int quality, boolean details) {
		int size = SIZES[Math.clamp(quality, 0, SIZES.length - 1)];
		String key = key(planet, size, details);

		if (pending != null) {
			if (!pending.isDone()) {
				return;
			}

			int[][] pixels = pending.getNow(null);
			pending = null;

			if (pixels != null) {
				PlanetPreset painted = PlanetPreset.byId(pendingKey.substring(0, pendingKey.indexOf('@'))).orElse(planet);

				if (painted != shown) {
					// A new planet: the old pictures fade out under the new ones.
					startTransition(painted);
				}

				upload(pixels, pendingSize);
				shownKey = pendingKey;
			}
		}

		if (!key.equals(shownKey) && !key.equals(pendingKey)) {
			pendingKey = key;
			pendingSize = size;
			long start = System.nanoTime();
			pending = CompletableFuture.supplyAsync(() -> {
				int[][] pixels = {PlanetSkyPainter.paint(planet, size, false, details), PlanetSkyPainter.paint(planet, size, true, details),
						PlanetSkyPainter.paintTint(planet, TINT_SIZE, false), PlanetSkyPainter.paintTint(planet, TINT_SIZE, true)};
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

		day = null;
		night = null;

		if (tintDay != null) {
			tintDay.close();
		}

		if (tintNight != null) {
			tintNight.close();
		}

		day = SpaceSky.texture(pixels[0], size, "SaberCraft planet sky (day)");
		night = SpaceSky.texture(pixels[1], size, "SaberCraft planet sky (night)");
		tintDay = SpaceSky.texture(pixels[2], TINT_SIZE, "SaberCraft planet tint (day)");
		tintNight = SpaceSky.texture(pixels[3], TINT_SIZE, "SaberCraft planet tint (night)");

		if (cube != null && cubeSize != size) {
			cube.close();
			cube = null;
		}

		cubeSize = size;
	}

	/** Whether the tint layer is drawn: a shader pack is in use and the option is on. */
	public static boolean tinting() {
		return ImmersionConfig.get().planetShaderTint && ShaderCompat.shaderPackInUse();
	}

	/** Whether the pictures of the chosen planet are painted, uploaded and faded in, for tests. */
	public static boolean ready() {
		ImmersionConfig config = ImmersionConfig.get();
		int size = SIZES[Math.clamp(config.spaceSkyQuality, 0, SIZES.length - 1)];
		return current().map(planet -> key(planet, size, config.planetHorizon).equals(shownKey) && pending == null && settled()).orElse(false);
	}

	/** Whether no fade between planets is running, for tests. */
	public static boolean settled() {
		return transition() >= 1.0F;
	}

	private static String key(PlanetPreset planet, int size, boolean details) {
		return planet.id() + "@" + size + (details ? "" : "/plain");
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
