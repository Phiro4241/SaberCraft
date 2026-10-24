package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.client.ability.ManeuverController;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.combat.CombatLibrary;
import com.phiro.sabercraft.client.combat.CombatMove;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.combat.CombatStyle;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * Checks the Phase 7 full-body moves in a real client: the directional dodges of every style
 * (distance and direction of the real movement, screenshots in first and third person), a roll
 * into a wall, melee hits missing during a roll and the landing animations.
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/maneuver_*}.
 */
public class ManeuverGameTest implements FabricClientGameTest {
	/** Style, hilt, color. */
	private static final String[][] STYLES = {
			{"flowing", "guardian", "blue"},
			{"heavy", "dark_lord", "red"},
			{"staff", "double", "purple"},
			{"juyo", "double", "red"},
	};
	private static final String[] DIRECTIONS = {"forward", "left", "right", "back"};

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
			server.runCommand("fill -12 ~-1 -12 12 ~-1 12 minecraft:stone");
			server.runCommand("fill -12 ~ -12 12 ~4 12 minecraft:air");
			start(server);
			context.waitTicks(20);

			for (String[] style : STYLES) {
				give(server, style[1], style[2], style[0]);
				select(context);
				context.waitTicks(30);

				for (int i = 0; i < DIRECTIONS.length; i++) {
					this.dodge(context, server, style[0], i);
				}
			}

			this.sideView(context, server);
			give(server, "guardian", "blue", "flowing");
			context.waitTicks(20);
			this.wall(context, server);
			this.evade(context, server);
			this.landing(context, server);
		}
	}

	private void dodge(ClientGameTestContext context, TestServerContext server, String style, int direction) {
		start(server);
		server.runOnServer(s -> SaberAbilities.reset(null));
		context.runOnClient(client -> SaberAbilityClient.resetForTests());
		context.waitTicks(10);
		KeyMapping key = context.computeOnClient(client -> switch (direction) {
			case 1 -> client.options.keyLeft;
			case 2 -> client.options.keyRight;
			case 3 -> client.options.keyDown;
			default -> null;
		});
		Vec3 before = server.computeOnServer(s -> player(s).position());

		if (key != null) {
			context.getInput().holdKey(key);
		}

		context.waitTicks(2);
		context.getInput().pressKey(SaberAbilityClient.DASH_KEY);

		if (key != null) {
			context.getInput().releaseKey(key);
		}

		String name = "maneuver_" + style + "_" + DIRECTIONS[direction];
		boolean third = direction % 2 == 1;
		context.runOnClient(client -> client.options.setCameraType(third ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK));
		context.waitTicks(2);
		context.takeScreenshot(name + "_tp_a");
		context.waitTicks(2);
		context.takeScreenshot(name + "_tp_b");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(2);
		context.takeScreenshot(name + "_fp");
		String move = context.computeOnClient(client -> {
			CombatMove current = SaberTracker.INSTANCE.combat(client.player).move();
			return current != null ? current.id().toString() : "none";
		});
		context.waitTicks(20);
		Vec3 moved = server.computeOnServer(s -> player(s).position()).subtract(before);
		// The player looks along +x: left is -z, right is +z.
		Vec3 expected = switch (direction) {
			case 1 -> new Vec3(0, 0, -1);
			case 2 -> new Vec3(0, 0, 1);
			case 3 -> new Vec3(-1, 0, 0);
			default -> new Vec3(1, 0, 0);
		};
		Vec3 flat = new Vec3(moved.x, 0, moved.z);
		SaberCraft.LOGGER.info("{}: move {} moved {}", name, move, moved);
		check(flat.length() > 1.0 && flat.normalize().dot(expected) > 0.85, name + " moves the player the right way: " + moved + " (" + move + ")");
		check(Math.abs(moved.y) < 0.1, name + " ends on the ground: " + moved.y);
		check(!context.computeOnClient(client -> ManeuverController.isActive()), name + " has ended");
	}

	/**
	 * Every dodge and landing move on a mannequin seen exactly from the side, with the clock frozen
	 * at several moments, to check that the body stays on the ground and nothing cuts into it.
	 */
	private void sideView(ClientGameTestContext context, TestServerContext server) {
		start(server);
		server.runCommand("summon minecraft:mannequin 3.5 ~ 0.5 {Tags:[\"side\"]}");
		server.runCommand("tp @e[tag=side] 3.5 ~ 0.5 0 0");

		for (String[] style : STYLES) {
			give(server, style[1], style[2], style[0]);
			server.runCommand("item replace entity @e[tag=side] weapon.mainhand from entity @p weapon.mainhand");
			context.waitTicks(30);
			CombatStyle combat = context.computeOnClient(client -> CombatLibrary.INSTANCE.style(Identifier.parse("sabercraft:" + style[0])));
			List<CombatMove> moves = new ArrayList<>(combat.dodges().values());
			moves.add(combat.land());
			moves.add(combat.landHeavy());

			for (CombatMove move : moves) {
				float begin = context.computeOnClient(client -> {
					float now = SaberTracker.INSTANCE.time(0.0F);
					SaberTracker.INSTANCE.combat(mannequin(client)).play(move, now);
					return now;
				});

				for (int i = 0; i <= 4; i++) {
					float age = move.duration() * i / 5.0F;
					context.runOnClient(client -> SaberTracker.INSTANCE.freezeTime(begin + age));
					context.takeScreenshot("maneuver_side_" + style[0] + "_" + move.id().getPath() + "_" + i);
				}

				context.runOnClient(client -> SaberTracker.INSTANCE.freezeTime(Float.NaN));
				context.waitTicks((int) move.duration() + 5);
			}
		}

		server.runCommand("kill @e[tag=side]");
	}

	private static Mannequin mannequin(Minecraft client) {
		for (var entity : client.level.entitiesForRendering()) {
			if (entity instanceof Mannequin mannequin) {
				return mannequin;
			}
		}

		throw new AssertionError("no mannequin on the client");
	}

	/** A roll into a wall stops at the wall and the animation ends with it. */
	private void wall(ClientGameTestContext context, TestServerContext server) {
		start(server);
		server.runCommand("fill 2 ~ -2 2 ~2 2 minecraft:stone");
		server.runOnServer(s -> SaberAbilities.reset(null));
		context.runOnClient(client -> SaberAbilityClient.resetForTests());
		context.waitTicks(10);
		context.getInput().pressKey(SaberAbilityClient.DASH_KEY);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(3);
		context.takeScreenshot("maneuver_wall_a");
		context.waitTicks(3);
		context.takeScreenshot("maneuver_wall_b");
		boolean active = context.computeOnClient(client -> ManeuverController.isActive());
		boolean animating = context.computeOnClient(client -> SaberTracker.INSTANCE.combat(client.player).move() != null);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(15);
		double x = server.computeOnServer(s -> player(s).getX());
		check(!active, "the roll ends at the wall");
		check(!animating, "the roll animation stops at the wall");
		check(x < 1.71 && x > 1.0, "the player stops in front of the wall: " + x);
		server.runCommand("fill 2 ~ -2 2 ~2 2 minecraft:air");
	}

	/** Melee hits miss during the roll, after it they land again. */
	private void evade(ClientGameTestContext context, TestServerContext server) {
		start(server);
		server.runCommand("summon minecraft:pig -3 ~ 0 {PersistenceRequired:1b,Silent:1b,NoAI:1b}");
		server.runOnServer(s -> {
			SaberAbilities.reset(null);
			player(s).setHealth(20.0F);
		});
		context.runOnClient(client -> SaberAbilityClient.resetForTests());
		context.waitTicks(25);
		context.getInput().pressKey(SaberAbilityClient.DASH_KEY);
		context.waitTicks(3);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(2);
		float during = server.computeOnServer(s -> player(s).getHealth());
		check(during == 20.0F, "a melee hit during the roll misses: " + during);
		context.waitTicks(30);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(2);
		float after = server.computeOnServer(s -> player(s).getHealth());
		check(after < 20.0F, "after the roll hits land again: " + after);
		server.runCommand("kill @e[type=minecraft:pig]");
		server.runOnServer(s -> player(s).setHealth(20.0F));
	}

	private void landing(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);

		for (int height : new int[] {3, 7}) {
			start(server);
			context.waitTicks(10);
			server.runCommand("tp @a 0 ~" + height + " 0 -90 5");
			String expected = height > 5 ? "sabercraft:land_heavy" : "sabercraft:land";
			String seen = "none";

			for (int i = 0; i < 40 && "none".equals(seen); i++) {
				context.waitTicks(1);
				seen = context.computeOnClient(client -> {
					CombatState combat = SaberTracker.INSTANCE.combat(client.player);
					return client.player.onGround() && combat.move() != null ? combat.move().id().toString() : "none";
				});
			}

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(2);
			context.takeScreenshot("maneuver_" + expected.substring(11) + "_tp");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			check(expected.equals(seen), "a fall of " + height + " blocks plays " + expected + ": " + seen);
			context.waitTicks(20);
		}

		server.runCommand("gamemode survival @a");
	}

	private static void start(TestServerContext server) {
		server.runCommand("tp @a 0 ~ 0 -90 5");
	}

	private static void give(TestServerContext server, String hilt, String color, String style) {
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:"
				+ hilt + "\",\"sabercraft:" + color + "\",\"sabercraft:" + style + "\"]}]");
	}

	private static void select(ClientGameTestContext context) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
