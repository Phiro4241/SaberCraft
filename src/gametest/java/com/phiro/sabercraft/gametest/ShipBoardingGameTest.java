package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Walking aboard a ship must work on the first try, every time (bug report 2026-11-14: the hangar
 * button "Walk aboard" only worked about every second time for the Falcon, and never from its
 * cockpit). Every way in is tried many times in a row, each at a different moment after landing:
 * the hangar button (the same packet the button sends), sneak-using the ship (a real click of the
 * client), from the cockpit, and for the Star Destroyer also its tractor beam. Afterwards the ships
 * must still fly as before. Last, the same on a dedicated server.
 */
public class ShipBoardingGameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-600, -64, -600, 600, 400, 600);
	private static final int ROUNDS = 8;
	private static final List<String> FAILED = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(6);
			ImmersionConfig.get().shipCamera = ImmersionConfig.CAMERA_KEEP;
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("kill @e[type=!player]");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());
			context.waitFor(client -> ShipClient.status() != null, 40);

			onePress(context, server, self);
			falcon(context, server, self);
			destroyer(context, server, self);
			flightUnchanged(context, server, self);
		}

		dedicatedServer(context);
		check(FAILED.isEmpty(), "every way aboard works on the first try: " + String.join(" || ", FAILED));
	}

	// --- One press, nothing called yet ------------------------------------------------------------

	/** The hangar button alone: the ship is not out yet (or far away); it is called and you walk aboard. */
	private static void onePress(ClientGameTestContext context, TestServerContext server, UUID self) {
		for (ShipType type : List.of(ShipType.FALCON, ShipType.STAR_DESTROYER)) {
			server.runOnServer(s -> Starships.resetCooldowns());
			check(server.computeOnServer(s -> Starships.ship(me(s, self)) == null), "no ship out before pressing for the " + type.id());
			context.runOnClient(client -> ShipClient.command("interior", type));
			check(aboard(context, 60), "one press of the hangar button: called and aboard the " + type.id() + " (" + refusal(server) + ")");
			off(context, server, self);
			// Far away from it now: one press brings it back and takes you aboard.
			server.runCommand("tp @a 120 ~ 0");
			context.waitTicks(40);
			context.runOnClient(client -> ShipClient.command("interior", type));
			check(aboard(context, 60), "from 120 blocks away, one press: aboard the " + type.id() + " (" + refusal(server) + ")");
			context.waitTicks(5);
			String again = server.computeOnServer(s -> String.valueOf(Starships.command(me(s, self), "interior", type.id())));
			check(again.contains("already_aboard"), "pressing again while aboard just says so (" + again + ")");
			check(server.computeOnServer(s -> ShipInteriors.aboardKey(me(s, self))) != null, "and you stay aboard");
			off(context, server, self);
			server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", type.id()));
			server.runCommand("tp @a 0 ~ 0 180 0");
			context.waitTicks(20);
		}
	}

	// --- Millennium Falcon ------------------------------------------------------------------------

	private static void falcon(ClientGameTestContext context, TestServerContext server, UUID self) {
		call(context, server, self, ShipType.FALCON);
		List<String> failures = new ArrayList<>();

		// The hangar button, pressed at a different moment after landing each time.
		for (int round = 0; round < ROUNDS; round++) {
			context.waitTicks(1 + round % 4);
			context.runOnClient(client -> ShipClient.command("interior", ShipType.FALCON));

			if (!aboard(context, 40)) {
				failures.add("button round " + round + ": " + refusal(server));
			}

			off(context, server, self);
		}

		// Sneak and use the ship: a real click of the client, also at different moments.
		for (int round = 0; round < ROUNDS; round++) {
			context.waitTicks(1 + round % 4);
			faceShip(context, server, self);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(2);
			boolean clicked = context.computeOnClient(client -> {
				Entity target = clientShip(client);

				if (target == null) {
					return false;
				}

				client.gameMode.interact(client.player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
				return true;
			});
			context.getInput().releaseKey(options -> options.keyShift);

			if (!clicked || !aboard(context, 40)) {
				failures.add("sneak-use round " + round + (clicked ? "" : " (no ship in sight)") + ": " + refusal(server));
			}

			off(context, server, self);
		}

		// From the cockpit: in, a short hop, back down, then the button.
		for (int round = 0; round < 4; round++) {
			context.waitTicks(5);
			server.computeOnServer(s -> Starships.command(me(s, self), "board", "falcon"));
			until(context, client -> ShipClient.piloting(client) != null, 60, "into the Falcon's cockpit");

			if (round % 2 == 1) {
				context.getInput().holdKeyFor(options -> options.keyJump, 20);
				context.waitTicks(10);
			}

			context.waitTicks(4 + round);
			context.runOnClient(client -> ShipClient.command("interior", ShipType.FALCON));

			if (!aboard(context, 80)) {
				failures.add("from the cockpit round " + round + ": " + refusal(server) + " " + server.computeOnServer(s -> state(ship(s))));
				context.getInput().holdKeyFor(options -> options.keyShift, 3);
				context.waitTicks(20);
			} else {
				// Left hovering without its pilot, the Falcon settles onto the ground by itself (its
				// chunk may sleep while nobody is outside; it is checked once you are back out).
				context.waitTicks(40);
				off(context, server, self);

				for (int i = 0; i < 100 && !server.computeOnServer(s -> ship(s) != null && ship(s).landed()); i++) {
					context.waitTick();
				}

				check(server.computeOnServer(s -> ship(s) != null && ship(s).landed()), "the Falcon has settled on the ground: "
						+ server.computeOnServer(s -> state(ship(s))));
			}
		}

		report("Falcon", failures);
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "falcon"));
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(20);
	}

	// --- Star Destroyer ---------------------------------------------------------------------------

	private static void destroyer(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> Starships.resetCooldowns());
		call(context, server, self, ShipType.STAR_DESTROYER);
		List<String> failures = new ArrayList<>();

		for (int round = 0; round < ROUNDS; round++) {
			context.waitTicks(1 + round % 4);
			context.runOnClient(client -> ShipClient.command("interior", ShipType.STAR_DESTROYER));

			if (!aboard(context, 40)) {
				failures.add("button round " + round + ": " + refusal(server) + " " + server.computeOnServer(s -> state(ship(s)) + " player "
						+ me(s, self).position() + " dist " + Math.round(Math.sqrt(ship(s).distanceToSqr(me(s, self))))));
			}

			off(context, server, self);
		}

		// Walked away a bit (the ship is huge): the button still works from anywhere around it.
		for (int round = 0; round < 4; round++) {
			int angle = round * 90;
			server.runOnServer(s -> {
				ShipEntity ship = ship(s);
				ServerPlayer player = me(s, self);
				double r = 30.0;
				double x = ship.getX() + Math.cos(Math.toRadians(angle)) * r;
				double z = ship.getZ() + Math.sin(Math.toRadians(angle)) * r;
				int y = s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x),
						(int) Math.floor(z));
				player.teleportTo(x, y, z);
			});
			context.waitTicks(10);
			context.runOnClient(client -> ShipClient.command("interior", ShipType.STAR_DESTROYER));

			if (!aboard(context, 40)) {
				failures.add("button 30 blocks away, " + angle + "°: " + refusal(server) + " dist "
						+ server.computeOnServer(s -> Math.round(Math.sqrt(ship(s).distanceToSqr(me(s, self))))));
			}

			off(context, server, self);
		}

		// The tractor beam: stand sneaking below the hangar.
		for (int round = 0; round < 3; round++) {
			server.runOnServer(s -> {
				ShipEntity ship = ship(s);
				int y = s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(ship.getX()),
						(int) Math.floor(ship.getZ()));
				me(s, self).teleportTo(ship.getX(), y, ship.getZ());
			});
			context.waitTicks(5);
			context.getInput().holdKey(options -> options.keyShift);
			boolean in = aboard(context, 80);
			context.getInput().releaseKey(options -> options.keyShift);

			if (!in) {
				failures.add("tractor beam round " + round + ": " + refusal(server));
			}

			off(context, server, self);
		}

		// From the helm onto the bridge.
		for (int round = 0; round < 3; round++) {
			server.computeOnServer(s -> Starships.command(me(s, self), "board", "star_destroyer"));
			until(context, client -> ShipClient.piloting(client) != null, 60, "onto the Star Destroyer's helm");
			context.waitTicks(10 + round * 3);
			context.runOnClient(client -> ShipClient.command("interior", ShipType.STAR_DESTROYER));

			if (!aboard(context, 60)) {
				failures.add("from the helm round " + round + ": " + refusal(server));
				context.getInput().holdKeyFor(options -> options.keyShift, 3);
				context.waitTicks(20);
			} else {
				off(context, server, self);
			}
		}

		report("Star Destroyer", failures);
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "star_destroyer"));
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(20);
	}

	// --- Flight still as before -------------------------------------------------------------------

	private static void flightUnchanged(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> Starships.resetCooldowns());

		for (ShipType type : List.of(ShipType.XWING, ShipType.FALCON)) {
			server.computeOnServer(s -> Starships.command(me(s, self), "board", type.id()));
			until(context, client -> ShipClient.piloting(client) != null, 80, "into the " + type.id());
			double startY = server.computeOnServer(s -> ship(s).getY());
			context.getInput().holdKeyFor(options -> options.keyJump, 30);
			check(server.computeOnServer(s -> ship(s).getY()) > startY + 2.0, type.id() + " lifts off with jump");
			context.getInput().holdKeyFor(options -> options.keyUp, 40);
			check(server.computeOnServer(s -> ship(s).speed()) > 0.2, type.id() + " speeds up with W (" + server.computeOnServer(s -> ship(s).speed()) + ")");
			double flying = server.computeOnServer(s -> ship(s).getY());
			context.waitTicks(40);
			check(server.computeOnServer(s -> ship(s).getY()) > flying - 6.0, type.id() + " keeps flying without sinking away");
			// Down again: brake, then sink with sprint until it touches the ground.
			context.getInput().holdKeyFor(options -> options.keyDown, 60);
			context.getInput().holdKey(options -> options.keySprint);

			for (int i = 0; i < 200 && !server.computeOnServer(s -> ship(s).landed()); i++) {
				context.waitTick();
			}

			context.getInput().releaseKey(options -> options.keySprint);
			check(server.computeOnServer(s -> ship(s).landed()), type.id() + " lands");
			context.getInput().holdKeyFor(options -> options.keyShift, 3);
			until(context, client -> client.player.getVehicle() == null, 20, "out of the " + type.id());
			context.waitTicks(20);
			server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", type.id()));
			context.waitTicks(10);
		}
	}

	// --- Dedicated server -------------------------------------------------------------------------

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				context.waitTicks(100);
				server.runCommand("gamerule advance_time false");
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("time set day");
				server.runCommand("op @a");
				server.runCommand("tp @a 0 ~ 0 180 0");
				server.runCommand("kill @e[type=!player]");
				context.waitTicks(20);
				context.waitFor(client -> ShipClient.status() != null, 60);
				List<String> failures = new ArrayList<>();

				for (ShipType type : List.of(ShipType.FALCON, ShipType.STAR_DESTROYER)) {
					server.runOnServer(s -> Starships.resetCooldowns());
					context.runOnClient(client -> ShipClient.command("call", type));
					context.waitTicks(20);
					waitLanded(context, server, null);

					for (int round = 0; round < 4; round++) {
						context.waitTicks(1 + round);
						context.runOnClient(client -> ShipClient.command("interior", type));

						if (!aboard(context, 80)) {
							failures.add(type.id() + " button round " + round + ": " + refusal(server));
						}

						server.runOnServer(s -> ShipInteriors.leaveForTest(s.getPlayerList().getPlayers().getFirst()));
						until(context, client -> !ShipInteriors.isInterior(client.level), 80, "dedicated server: off the " + type.id());
						context.waitTicks(30);
					}

					context.runOnClient(client -> ShipClient.command("dismiss", type));
					context.waitTicks(10);
				}

				report("dedicated server", failures);
			}
		}
	}

	// --- Helpers ----------------------------------------------------------------------------------

	private static void call(ClientGameTestContext context, TestServerContext server, UUID self, ShipType type) {
		context.runOnClient(client -> ShipClient.command("call", type));
		context.waitTicks(10);
		check(server.computeOnServer(s -> ship(s) != null && ship(s).ship() == type), type.id() + " called");
		waitLanded(context, server, type);
	}

	/** Until the called ship has come down (fighters) or settled at its height (Star Destroyer). */
	private static void waitLanded(ClientGameTestContext context, TestServerContext server, ShipType type) {
		double last = Double.NaN;

		for (int i = 0; i < 300; i++) {
			context.waitTick();
			double y = server.computeOnServer(s -> {
				ShipEntity ship = ship(s);
				return ship == null ? Double.NaN : ship.getY();
			});

			if (i > 20 && Math.abs(y - last) < 1.0E-4) {
				break;
			}

			last = y;
		}
	}

	/** Off the ship again (by the ramp or hangar exit, as the test helper does it), then a moment outside. */
	private static void off(ClientGameTestContext context, TestServerContext server, UUID self) {
		if (context.computeOnClient(client -> ShipInteriors.isInterior(client.level))) {
			server.runOnServer(s -> ShipInteriors.leaveForTest(me(s, self)));
			until(context, client -> !ShipInteriors.isInterior(client.level), 60, "off the ship");
		}

		context.waitTicks(15);
	}

	private static void faceShip(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> {
			ShipEntity ship = ship(s);
			ServerPlayer player = me(s, self);
			Vec3 to = ship.getBoundingBox().getCenter().subtract(player.getEyePosition());
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
			// Close enough to reach it.
			Vec3 near = ship.position().add(Vec3.directionFromRotation(0.0F, yaw).scale(-(ship.getBbWidth() / 2.0 + 1.5)));
			player.teleportTo((ServerLevel) ship.level(), near.x, ship.getY(), near.z, java.util.Set.of(), yaw, pitch, true);
		});
		context.waitTicks(3);
	}

	/** On the client a ship is a plain pig with a marker: the closest one. */
	private static Entity clientShip(Minecraft client) {
		Entity best = null;

		for (Entity entity : client.level.entitiesForRendering()) {
			if (com.phiro.sabercraft.ship.ShipMarker.of(entity) != null
					&& (best == null || entity.distanceToSqr(client.player) < best.distanceToSqr(client.player))) {
				best = entity;
			}
		}

		return best;
	}

	private static boolean aboard(ClientGameTestContext context, int ticks) {
		for (int i = 0; i < ticks && !context.computeOnClient(client -> ShipInteriors.isInterior(client.level)); i++) {
			context.waitTick();
		}

		return context.computeOnClient(client -> ShipInteriors.isInterior(client.level));
	}

	/** What the server last answered a refused boarding (the message the player saw). */
	private static String refusal(TestServerContext server) {
		return server.computeOnServer(s -> {
			net.minecraft.network.chat.Component refused = ShipInteriors.lastRefusalForTest();
			return refused == null ? "no answer" : refused.getString();
		});
	}

	private static String state(ShipEntity ship) {
		return ship == null ? "no ship" : ship.ship().id() + " at " + ship.position() + " ground=" + ship.onGround() + " landed=" + ship.landed();
	}

	/** Every part runs to the end, so one report shows all failed boardings at once. */
	private static void report(String what, List<String> failures) {
		if (!failures.isEmpty()) {
			String line = what + ": " + failures.size() + " boardings failed: " + String.join(" | ", failures);
			SaberCraft.LOGGER.error("FAIL: {}", line);
			FAILED.add(line);
		} else {
			SaberCraft.LOGGER.info("OK: {}: every boarding worked on the first try", what);
		}
	}

	private static ShipEntity ship(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<ShipEntity> ships = level.getEntitiesOfClass(ShipEntity.class, AREA, Entity::isAlive);

			if (!ships.isEmpty()) {
				return ships.getFirst();
			}
		}

		return null;
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static void until(ClientGameTestContext context, Predicate<Minecraft> condition, int ticks, String message) {
		for (int i = 0; i < ticks && !context.computeOnClient(condition::test); i++) {
			context.waitTick();
		}

		check(context.computeOnClient(condition::test), message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
