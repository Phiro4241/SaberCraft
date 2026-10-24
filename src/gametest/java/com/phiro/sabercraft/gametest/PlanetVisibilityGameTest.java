package com.phiro.sabercraft.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.sky.PlanetPreset;
import com.phiro.sabercraft.client.sky.PlanetSky;

/**
 * Measures how much of each planet sky is really seen (Phase 9), without a shader pack and with
 * every pack given in {@code sabercraft.shaderpacks} (see {@link ShaderBladeGameTest}). For each
 * planet the picture is compared with the same view without a planet: the mean color difference
 * in the upper part of the picture (the sky) looking up, and in a band around the horizon looking
 * straight ahead, by day and by night. With a shader pack it is measured with the planet tint
 * on and off. The numbers go to the log and to {@code planet_visibility.csv} in the game folder.
 *
 * <p>Without a shader pack every planet must change the sky clearly. With a shader pack the
 * numbers are only reported: how much a pack keeps is up to the pack.
 */
public class PlanetVisibilityGameTest implements FabricClientGameTest {
	/** Mean difference (0..255 per channel) a planet must make in the vanilla sky; Naboo is close to vanilla blue. */
	private static final double VANILLA_MIN = 5.0;

	private final List<String> rows = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(6);
			ImmersionConfig.get().spaceSky = true;
			ImmersionConfig.get().spaceSkyQuality = 1;
			ImmersionConfig.get().planet = "";
		});
		List<String> packs = new ArrayList<>();
		packs.add("");

		if (FabricLoader.getInstance().isModLoaded("iris")) {
			for (String path : System.getProperty("sabercraft.shaderpacks", "").split(",")) {
				if (!path.isBlank()) {
					packs.add(ShaderBladeGameTest.install(Path.of(path.trim())));
				}
			}
		}

		this.rows.add("pack,planet,tint,time,view,difference,noise");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(150);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			context.runOnClient(client -> client.player.getAbilities().flying = true);
			server.runCommand("tp @a 0 110 0 0 0");
			ShaderBladeGameTest.hideHud(context, true);

			for (String pack : packs) {
				ShaderBladeGameTest.usePack(context, pack);
				String tag = pack.isEmpty() ? "vanilla" : ShaderBladeGameTest.shortName(pack);

				for (String time : new String[] {"noon", "midnight"}) {
					server.runCommand("time set " + time);
					context.runOnClient(client -> ImmersionConfig.get().planet = "");
					context.waitTicks(20);
					BufferedImage[] none = this.views(context, tag + "_none_" + time);
					// The same view again, for the noise floor: clouds and shader effects move.
					BufferedImage[] again = this.views(context, tag + "_none2_" + time);
					double[] noise = {difference(none[0], again[0], true), difference(none[1], again[1], false)};

					for (PlanetPreset planet : PlanetPreset.ALL) {
						for (boolean tint : pack.isEmpty() ? new boolean[] {true} : new boolean[] {true, false}) {
							context.runOnClient(client -> {
								ImmersionConfig.get().planet = planet.id();
								ImmersionConfig.get().planetShaderTint = tint;
							});
							context.waitFor(client -> PlanetSky.ready(), 1200);
							context.waitTicks(10);
							String name = tag + "_" + planet.id() + (pack.isEmpty() ? "" : tint ? "_tint" : "_notint") + "_" + time;
							BufferedImage[] shots = this.views(context, name);
							double sky = difference(none[0], shots[0], true);
							double horizon = difference(none[1], shots[1], false);
							this.record(tag, planet.id(), pack.isEmpty() ? "-" : tint ? "on" : "off", time, "sky", sky, noise[0]);
							this.record(tag, planet.id(), pack.isEmpty() ? "-" : tint ? "on" : "off", time, "horizon", horizon, noise[1]);

							if (pack.isEmpty() && time.equals("noon")) {
								check(sky > VANILLA_MIN, planet.id() + " changes the vanilla sky clearly (" + format(sky) + ")");
								check(horizon > VANILLA_MIN, planet.id() + " changes the vanilla horizon clearly (" + format(horizon) + ")");
							}
						}
					}
				}
			}

			context.runOnClient(client -> {
				ImmersionConfig.get().planet = "";
				ImmersionConfig.get().planetShaderTint = true;
			});
			ShaderBladeGameTest.usePack(context, "");
			ShaderBladeGameTest.hideHud(context, false);
		}

		try {
			Path file = FabricLoader.getInstance().getGameDir().resolve("planet_visibility.csv");
			Files.write(file, this.rows);
			SaberCraft.LOGGER.info("Planet visibility written to {}", file);
		} catch (IOException e) {
			throw new AssertionError("Cannot write the planet visibility table", e);
		}
	}

	/** Looking up at the sky, and straight at the horizon. */
	private BufferedImage[] views(ClientGameTestContext context, String name) {
		context.getInput().lookAt(0.0F, -40.0F);
		context.waitTicks(4);
		BufferedImage up = read(context.takeScreenshot("visibility_" + name + "_up"));
		context.getInput().lookAt(0.0F, -2.0F);
		context.waitTicks(4);
		BufferedImage ahead = read(context.takeScreenshot("visibility_" + name + "_horizon"));
		return new BufferedImage[] {up, ahead};
	}

	private void record(String pack, String planet, String tint, String time, String view, double difference, double noise) {
		this.rows.add(String.join(",", pack, planet, tint, time, view, format(difference), format(noise)));
		SaberCraft.LOGGER.info("Visibility {} {} tint={} {} {}: {} (noise {})", pack, planet, tint, time, view, format(difference), format(noise));
	}

	/**
	 * Mean absolute difference per channel (0..255): over the upper 60 % of the picture for the
	 * sky, over a band from 30 % to 52 % of the height for the horizon (just above the middle,
	 * where the far landscape stands).
	 */
	private static double difference(BufferedImage a, BufferedImage b, boolean sky) {
		int from = sky ? 0 : a.getHeight() * 30 / 100;
		int to = sky ? a.getHeight() * 60 / 100 : a.getHeight() * 52 / 100;
		long sum = 0;
		long count = 0;

		for (int y = from; y < to; y += 2) {
			for (int x = 0; x < a.getWidth(); x += 2) {
				int p = a.getRGB(x, y);
				int q = b.getRGB(x, y);
				sum += Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255)) + Math.abs((p & 255) - (q & 255));
				count += 3;
			}
		}

		return (double) sum / count;
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static BufferedImage read(Path file) {
		try {
			return ImageIO.read(file.toFile());
		} catch (IOException e) {
			throw new AssertionError("Cannot read " + file, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
