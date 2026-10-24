package com.phiro.sabercraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.sky.PlanetPreset;
import com.phiro.sabercraft.client.sky.PlanetSky;

/**
 * Checks the Star Wars planet skies (Phase 8) in a real client: every planet is painted, changes
 * the sky and fog colors, and is photographed at noon (four directions and straight up), at
 * sunset and at midnight. With no planet chosen the colors stay vanilla.
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
				server.runCommand("time set midnight");

				for (float yaw : new float[] {0.0F, 180.0F}) {
					context.getInput().lookAt(yaw, -12.0F);
					context.waitTicks(5);
					context.takeScreenshot("planet_" + name + "_midnight_" + (int) yaw);
				}
			}

			// Off again: the vanilla colors are back at once.
			context.runOnClient(client -> ImmersionConfig.get().planet = "");
			context.waitTicks(3);
			check(context.computeOnClient(client -> PlanetSky.skyColor(VANILLA_SKY) == VANILLA_SKY), "with the planet off the sky is vanilla again");
			server.runCommand("time set noon");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
