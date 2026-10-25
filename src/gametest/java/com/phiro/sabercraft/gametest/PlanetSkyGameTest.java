package com.phiro.sabercraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.sky.PlanetCelestials;
import com.phiro.sabercraft.client.sky.PlanetPreset;
import com.phiro.sabercraft.client.sky.PlanetSky;

/**
 * Checks the Star Wars planet skies (Phase 8) in a real client: every planet is painted, changes
 * the sky and fog colors, and is photographed at noon (four directions and straight up), at
 * sunset and at midnight. With no planet chosen the colors stay vanilla.
 *
 * <p>Phase 10: each planet has its own sun and moon (photographed in the morning and evening), a
 * change of planet fades the colors over, the ultra quality paints, and switching off fades back.
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/planet_*}.
 */
public class PlanetSkyGameTest implements FabricClientGameTest {
	private static final int VANILLA_SKY = 0xFF78A7FF;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(6);
			ImmersionConfig.get().spaceSky = true;
			ImmersionConfig.get().spaceSkyQuality = 1;
			ImmersionConfig.get().planet = "";
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			server.runCommand("time set noon");
			context.runOnClient(client -> client.player.getAbilities().flying = true);
			server.runCommand("tp @a 0 110 0 0 -15");
			context.waitTicks(60);

			check(context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY) == VANILLA_SKY), "without a planet the sky stays vanilla");
			context.getInput().lookAt(0.0F, -15.0F);
			context.waitTicks(3);
			context.takeScreenshot("planet_none_noon");

			for (PlanetPreset planet : PlanetPreset.ALL) {
				String name = planet.id();
				context.runOnClient(client -> ImmersionConfig.get().planet = name);
				server.runCommand("time set noon");
				context.waitFor(client -> PlanetSky.ready(), 1200);
				check(true, name + " is painted");
				check(context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY) != VANILLA_SKY), name + " changes the sky color");
				check(context.computeOnClient(client -> PlanetSky.secondSun() != 0.0F) == (planet.secondSun() != 0.0F),
						name + " has " + (planet.secondSun() != 0.0F ? "two suns" : "one sun"));
				check(planet.equals(PlanetPreset.byId(name).orElse(null))
						&& PlanetCelestials.of(planet).equals(context.computeOnClient(client -> PlanetSky.celestials())), name + " has its own sun and moon");
				context.runOnClient(client -> ImmersionConfig.get().planetCelestials = false);
				check(context.computeOnClient(client -> PlanetSky.celestials()) == null, name + ": with the option off the vanilla sun and moon");
				context.runOnClient(client -> ImmersionConfig.get().planetCelestials = true);

				for (float yaw : new float[] {0.0F, 90.0F, 180.0F, 270.0F}) {
					context.getInput().lookAt(yaw, -12.0F);
					context.waitTicks(3);
					context.takeScreenshot("planet_" + name + "_noon_" + (int) yaw);
				}

				context.getInput().lookAt(0.0F, -88.0F);
				context.waitTicks(3);
				context.takeScreenshot("planet_" + name + "_noon_up");
				server.runCommand("time set 12600");
				context.getInput().lookAt(90.0F, -8.0F);
				context.waitTicks(5);
				context.takeScreenshot("planet_" + name + "_sunset");
				// Phase 10: the planet's sun in the morning and its moon in the evening, low in the east.
				server.runCommand("time set 1000");
				context.getInput().lookAt(-90.0F, -14.0F);
				context.waitTicks(5);
				context.takeScreenshot("planet_" + name + "_sun");
				server.runCommand("time set 13800");
				context.waitTicks(5);
				context.takeScreenshot("planet_" + name + "_moon");
				server.runCommand("time set midnight");

				for (float yaw : new float[] {0.0F, 180.0F}) {
					context.getInput().lookAt(yaw, -12.0F);
					context.waitTicks(5);
					context.takeScreenshot("planet_" + name + "_midnight_" + (int) yaw);
				}
			}

			// Phase 10: a change of planet fades over two seconds, colors and layer together.
			server.runCommand("time set noon");
			context.getInput().lookAt(0.0F, -12.0F);
			context.waitTicks(10);
			int naboo = context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY));
			context.runOnClient(client -> {
				ImmersionConfig.get().planetTransitionSeconds = 2.0F;
				ImmersionConfig.get().planet = "tatooine";
			});
			context.waitFor(client -> !PlanetSky.settled() || PlanetSky.ready(), 1200);
			context.waitTicks(16);
			int middle = context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY));
			check(!context.computeOnClient(client -> PlanetSky.settled()), "the change from Naboo to Tatooine is still fading after 0.8 s");
			context.takeScreenshot("planet_transition_naboo_tatooine");
			context.waitFor(client -> PlanetSky.ready(), 400);
			int tatooine = context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY));
			check(between(middle, naboo, tatooine), String.format("the sky color fades from Naboo %06X over %06X to Tatooine %06X", naboo & 0xFFFFFF,
					middle & 0xFFFFFF, tatooine & 0xFFFFFF));

			// The ultra quality paints the layer at 1536 pixels per side.
			context.runOnClient(client -> ImmersionConfig.get().spaceSkyQuality = 3);
			context.waitFor(client -> PlanetSky.ready(), 2400);
			check(true, "tatooine is painted at the ultra quality");
			context.takeScreenshot("planet_tatooine_ultra");
			context.runOnClient(client -> ImmersionConfig.get().spaceSkyQuality = 1);
			context.waitFor(client -> PlanetSky.ready(), 1200);

			// Off again: the vanilla colors come back with the same fade.
			context.runOnClient(client -> ImmersionConfig.get().planet = "");
			context.waitTicks(3);
			check(context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY) != VANILLA_SKY), "right after switching off the planet still fades out");
			context.waitFor(client -> PlanetSky.settled(), 400);
			check(context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY) == VANILLA_SKY), "with the planet off the sky is vanilla again");
			check(context.computeOnClient(client -> PlanetSky.celestials()) == null, "with the planet off the vanilla sun and moon");
		}
	}

	/** Whether each channel of {@code middle} lies between those of {@code from} and {@code to}, and it is neither end. */
	private static boolean between(int middle, int from, int to) {
		if (middle == from || middle == to) {
			return false;
		}

		for (int shift = 0; shift <= 16; shift += 8) {
			int m = middle >> shift & 255;
			int a = from >> shift & 255;
			int b = to >> shift & 255;

			if (m < Math.min(a, b) - 1 || m > Math.max(a, b) + 1) {
				return false;
			}
		}

		return true;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
