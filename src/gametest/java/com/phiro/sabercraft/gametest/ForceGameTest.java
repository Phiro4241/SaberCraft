package com.phiro.sabercraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.Projectile;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;

/**
 * Checks the Phase 7 force powers and the projectile deflection in a real client: the grip lifts
 * and holds a pig and lets it down without fall damage, the pull draws it closer but not through
 * a wall, the force jump goes high without fall damage, and arrows and snowballs are sent back by
 * the stance (along the look direction right after raising it).
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/force_*}.
 */
public class ForceGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty easy");
			server.runCommand("time set day");
			server.runCommand("gamemode survival @a");
			server.runCommand("kill @e[type=!minecraft:player]");
			server.runCommand("fill -4 ~-1 -6 14 ~-1 6 minecraft:stone");
			server.runCommand("fill -4 ~ -6 14 ~8 6 minecraft:air");
			server.runCommand("tp @a 0 ~ 0 -90 5");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword[minecraft:custom_model_data={strings:"
					+ "[\"sabercraft:guardian\",\"sabercraft:blue\",\"sabercraft:flowing\"]}]");
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.waitTicks(30);

			this.grip(context, server);
			this.pull(context, server);
			this.jump(context, server);
			this.deflect(context, server);
		}
	}

	private void grip(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		summonPig(server, 4.5);
		context.waitTicks(20);
		double base = server.computeOnServer(s -> pig(s).getY());
		context.getInput().holdKey(SaberAbilityClient.GRIP_KEY);
		context.waitTicks(20);
		double held = server.computeOnServer(s -> pig(s).getY());
		check(held > base + 1.0, "the grip lifts the pig: " + base + " -> " + held);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("force_grip_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.takeScreenshot("force_grip_fp");
		check(context.computeOnClient(client -> SaberAbilityClient.isGripping()), "the client shows the grip");
		// Looking up lifts it higher.
		context.getInput().lookAt(-90.0F, -25.0F);
		context.waitTicks(10);
		double higher = server.computeOnServer(s -> pig(s).getY());
		check(higher > held + 0.5, "the pig follows the look: " + held + " -> " + higher);
		context.getInput().releaseKey(SaberAbilityClient.GRIP_KEY);
		context.getInput().lookAt(-90.0F, 5.0F);
		context.waitTicks(3);
		check(!context.computeOnClient(client -> SaberAbilityClient.isGripping()), "releasing the key ends the grip");
		check(context.computeOnClient(client -> SaberAbilityClient.cooldown(SaberAbility.GRIP) != null
				&& SaberAbilityClient.cooldown(SaberAbility.GRIP).left() > 100), "the grip starts its cooldown after the release");
		context.waitTicks(60);
		check(server.computeOnServer(s -> pig(s).onGround()), "the released pig lands");
		float health = server.computeOnServer(s -> pig(s).getHealth());
		check(health == 10.0F, "the released pig takes no fall damage: " + health);
		server.runCommand("kill @e[type=minecraft:pig]");

		// Nothing in front: no grip and no cooldown.
		reset(context, server);
		context.waitTicks(5);
		context.getInput().holdKey(SaberAbilityClient.GRIP_KEY);
		context.waitTicks(5);
		context.getInput().releaseKey(SaberAbilityClient.GRIP_KEY);
		context.waitTicks(3);
		SaberAbilityClient.Cooldown none = context.computeOnClient(client -> SaberAbilityClient.cooldown(SaberAbility.GRIP));
		check(none == null || none.left() == 0, "without a target the grip does nothing");
	}

	private void pull(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		summonPig(server, 9.5);
		context.waitTicks(20);
		context.getInput().pressKey(SaberAbilityClient.PULL_KEY);
		context.waitTicks(4);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.takeScreenshot("force_pull_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(40);
		double x = server.computeOnServer(s -> pig(s).getX());
		check(x > 1.0 && x < 5.0, "the pull draws the pig close: " + x);
		server.runCommand("kill @e[type=minecraft:pig]");

		// A wall in between: the pig is out of sight and stays.
		reset(context, server);
		server.runCommand("fill 4 ~ -2 4 ~3 2 minecraft:stone");
		summonPig(server, 8.5);
		context.waitTicks(20);
		context.getInput().pressKey(SaberAbilityClient.PULL_KEY);
		context.waitTicks(30);
		double behind = server.computeOnServer(s -> pig(s).getX());
		check(Math.abs(behind - 8.5) < 0.3, "no pull through a wall: " + behind);
		server.runCommand("fill 4 ~ -2 4 ~3 2 minecraft:air");
		server.runCommand("kill @e[type=minecraft:pig]");
	}

	private void jump(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		context.waitTicks(10);
		double ground = server.computeOnServer(s -> player(s).getY());
		context.getInput().pressKey(SaberAbilityClient.JUMP_KEY);
		double top = ground;

		for (int i = 0; i < 25; i++) {
			context.waitTicks(1);
			top = Math.max(top, server.computeOnServer(s -> player(s).getY()));

			if (i == 9) {
				context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				context.takeScreenshot("force_jump_tp");
				context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			}
		}

		check(top - ground > 3.5, "the force jump goes high: " + (top - ground));
		context.waitFor(client -> client.player.onGround(), 100);
		context.waitTicks(5);
		float health = server.computeOnServer(s -> player(s).getHealth());
		check(health == 20.0F, "no fall damage after the force jump: " + health);
	}

	private void deflect(ClientGameTestContext context, TestServerContext server) {
		// Right after raising the stance: back along the look direction, and now the player's.
		reset(context, server);
		server.runCommand("tp @a 0 ~ 0 -90 0");
		context.waitTicks(10);
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(2);
		server.runCommand("summon minecraft:arrow 5.5 ~1.3 0.5 {Motion:[-1.6d,0.0d,0.0d]}");
		context.waitTicks(4);
		context.takeScreenshot("force_deflect_fp");
		check(server.computeOnServer(s -> player(s).getHealth()) == 20.0F, "the deflected arrow does not hurt");
		Projectile arrow = server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ARROW, a -> true).getFirst());
		check(server.computeOnServer(s -> arrow.getDeltaMovement().x > 0.5), "the arrow flies back the way the player looks");
		check(server.computeOnServer(s -> arrow.getOwner() == player(s)), "the deflected arrow belongs to the player");
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		server.runCommand("kill @e[type=minecraft:arrow]");

		// Later in the stance a snowball still glances off.
		reset(context, server);
		context.waitTicks(25);
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(20);
		server.runCommand("summon minecraft:snowball 5.5 ~1.3 0.5 {Motion:[-1.4d,0.0d,0.0d]}");
		context.waitTicks(5);
		boolean away = server.computeOnServer(s -> {
			var balls = s.overworld().getEntities(EntityTypes.SNOWBALL, b -> true);
			return !balls.isEmpty() && balls.stream().allMatch(ball -> ball.getDeltaMovement().x > 0.0);
		});
		check(away, "the snowball glances off the stance");
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		server.runCommand("kill @e[type=minecraft:snowball]");

		// Without the stance the arrow hits.
		reset(context, server);
		context.waitTicks(25);
		server.runCommand("summon minecraft:arrow 5.5 ~1.3 0.5 {Motion:[-1.6d,0.0d,0.0d]}");
		context.waitTicks(6);
		float health = server.computeOnServer(s -> player(s).getHealth());
		check(health < 20.0F, "without the stance the arrow hits: " + health);
		server.runCommand("kill @e[type=minecraft:arrow]");
	}

	private static void reset(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("tp @a 0 ~ 0 -90 5");
		server.runOnServer(s -> {
			SaberAbilities.reset(null);
			player(s).setHealth(20.0F);
		});
		context.runOnClient(client -> SaberAbilityClient.resetForTests());
	}

	private static void summonPig(TestServerContext server, double x) {
		server.runCommand("summon minecraft:pig " + x + " ~ 0.5 {PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:movement_speed\",base:0.0}]}");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Pig pig(MinecraftServer server) {
		return server.overworld().getEntities(EntityTypes.PIG, pig -> pig.isAlive()).getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
