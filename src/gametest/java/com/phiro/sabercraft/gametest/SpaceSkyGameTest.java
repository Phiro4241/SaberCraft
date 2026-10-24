package com.phiro.sabercraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.world.level.Level;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.sky.SpaceSky;

/**
 * Checks the optional space sky in a real client: it is painted and shown at night in the
 * Overworld, the vanilla sky comes back when the option is off, and the End gets its own picture.
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/sky_*}.
 */
public class SpaceSkyGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			ImmersionConfig.get().spaceSky = true;
			ImmersionConfig.get().spaceSkyQuality = 1;
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			server.runCommand("time set midnight");
			context.runOnClient(client -> client.player.getAbilities().flying = true);
			server.runCommand("tp @a 0 120 0 0 -50");
			context.waitTicks(40);

			context.waitFor(client -> SpaceSky.active(), 1200);
			check(true, "the Overworld sky is painted");
			for (float yaw : new float[] {0.0F, 90.0F, 180.0F, 270.0F}) {
				context.getInput().lookAt(yaw, -40.0F);
				context.waitTicks(3);
				context.takeScreenshot("sky_overworld_" + (int) yaw);
			}
			context.getInput().lookAt(0.0F, -88.0F);
			context.waitTicks(3);
			context.takeScreenshot("sky_overworld_up");

			server.runCommand("time set 13000");
			context.getInput().lookAt(270.0F, -20.0F);
			context.waitTicks(5);
			context.takeScreenshot("sky_overworld_dusk");
			server.runCommand("time set noon");
			context.waitTicks(5);
			context.takeScreenshot("sky_overworld_day");

			server.runCommand("time set midnight");
			context.runOnClient(client -> ImmersionConfig.get().spaceSky = false);
			context.getInput().lookAt(0.0F, -40.0F);
			context.waitTicks(5);
			check(!context.computeOnClient(client -> SpaceSky.active()), "with the option off the sky is vanilla");
			context.takeScreenshot("sky_overworld_off");
			context.runOnClient(client -> ImmersionConfig.get().spaceSky = true);

			server.runCommand("execute in minecraft:the_end run tp @a 0 100 0 0 -40");
			context.waitFor(client -> client.level != null && client.level.dimension() == Level.END, 600);
			context.waitTicks(40);
			// Without the dragon: its boss fog would hide the sky.
			server.runCommand("kill @e[type=minecraft:ender_dragon]");
			context.runOnClient(client -> client.player.getAbilities().flying = true);
			context.waitFor(client -> SpaceSky.active(), 1200);
			check(true, "the End sky is painted");
			context.waitTicks(5);
			context.takeScreenshot("sky_end");
			context.getInput().lookAt(120.0F, -60.0F);
			context.waitTicks(3);
			context.takeScreenshot("sky_end_2");
			context.runOnClient(client -> ImmersionConfig.get().spaceSkyInEnd = false);
			context.waitTicks(3);
			context.takeScreenshot("sky_end_off");
			context.runOnClient(client -> ImmersionConfig.get().spaceSkyInEnd = true);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
