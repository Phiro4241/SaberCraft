package com.phiro.sabercraft.gametest;

import java.util.List;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.menu.ShipMenuScreen;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipMarker;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Phase 11: the X-Wing, through the hangar and the real keys of the player.
 *
 * <p>The hangar calls the ship, which lands in front of the player (planned ships cannot be
 * called); a right-click climbs in, the camera goes behind the ship. Space lifts it while
 * hovering, W opens the throttle up to top speed (and the "first flight" advancement), A and D
 * turn and bank it, the S-foils open in the air. The attack key fires the laser cannons in turn at
 * the ship's rate: the bolts hit what is in front, the muzzle flash reaches the client, and no
 * block breaks. Flying into a wall stops the ship with a crash and leaves the wall standing.
 * Sneaking climbs out. Logging out in the cockpit and coming back puts the player back into the
 * ship, and no pig is left over. On a dedicated server the client flies its ship and sees it where
 * the server has it.
 *
 * <p>Screenshots: {@code ship_hangar}, {@code ship_landed}, {@code ship_cockpit},
 * {@code ship_flight_*}, {@code ship_lasers}, {@code ship_crash}, {@code ship_dedicated_server}.
 */
public class ShipGameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-300, -64, -300, 300, 320, 300);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(6));
		TestWorldSave save;
		UUID self;

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("kill @e[type=!player]");
			context.waitTicks(20);
			self = context.computeOnClient(client -> client.player.getUUID());

			hangar(context, server, self);
			board(context, server, self);
			fly(context, server, self);
			lasers(context, server, self);
			crash(context, server, self);
			climbOut(context, server, self);
			plainPig(context, server);
			// Back in the cockpit for the next check: logging out while flying.
			server.runCommand("tp @a 0 -60 0 180 0");
			server.computeOnServer(s -> Starships.command(me(s, self), "board", "xwing"));
			until(context, client -> ShipClient.piloting(client) != null, 60, "back in the cockpit before closing the world");
			context.waitTicks(10);
		}

		reopen(context, save, self);
		dedicatedServer(context);
	}

	/** The hangar lists the ships; only the X-Wing can be called. */
	private static void hangar(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.waitFor(client -> ShipClient.status() != null, 40);
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SHIPS));
		context.waitTicks(5);
		context.clickScreenButton(ShipType.TIE_FIGHTER.translationKey());
		context.waitTicks(3);
		check(!context.computeOnClient(client -> ((ShipMenuScreen) client.gui.screen()).callActive()), "a planned ship cannot be called");
		check(server.computeOnServer(s -> Starships.command(me(s, self), "call", "tie_fighter")) != null && Starships.liveCount() == 0,
				"the server refuses a planned ship too");
		server.runOnServer(s -> Starships.resetCooldowns());
		context.clickScreenButton(ShipType.XWING.translationKey());
		context.waitTicks(3);
		check(context.computeOnClient(client -> ((ShipMenuScreen) client.gui.screen()).callActive()), "the X-Wing can be called");
		// The mouse off the cards, so no tooltip covers the description in the picture.
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(2);
		context.takeScreenshot("ship_hangar");

		// Ships switched off: refused, then back on.
		server.runOnServer(s -> AbilityConfig.get().shipsEnabled = false);
		check(server.computeOnServer(s -> {
			Starships.resetCooldowns();
			Starships.command(me(s, self), "call", "xwing");
			return Starships.liveCount();
		}) == 0, "with ships switched off nothing is called");
		server.runOnServer(s -> {
			AbilityConfig.get().shipsEnabled = true;
			Starships.resetCooldowns();
		});
		context.waitTicks(5);
		context.clickScreenButton("sabercraft.ship.menu.call");
		context.waitFor(client -> clientShips(client) == 1, 40);
		check(server.computeOnServer(s -> Starships.liveCount()) == 1, "the hangar's call button brings one X-Wing");
		check(context.computeOnClient(client -> clientShips(client)) == 1, "the client sees it as an X-Wing");
		check(context.computeOnClient(client -> {
			Entity ship = clientShip(client);
			return Math.abs(ship.getBbWidth() - ShipType.XWING.width()) < 0.01F && Math.abs(ship.getBbHeight() - ShipType.XWING.height()) < 0.01F;
		}), "the client knows the ship's size");
		Vec3 at = server.computeOnServer(s -> ship(s).position());
		check(Math.abs(at.x) < 4.0 && at.z < -3.0 && at.z > -12.0, "it landed a few blocks in front of the player (" + at + ")");

		// Calling again does not make a second one.
		server.runOnServer(s -> {
			Starships.resetCooldowns();
			Starships.command(me(s, self), "call", "xwing");
		});
		context.waitTicks(5);
		check(server.computeOnServer(s -> Starships.liveCount()) == 1 && server.computeOnServer(s -> allPigs(s)) == 1, "calling again moves the same ship");
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(3);

		// A look at it from the side.
		server.runCommand("tp @a 7 -60 -3 120 15");
		context.waitTicks(20);
		context.takeScreenshot("ship_landed");
	}

	/** A right-click on the ship climbs in, and the camera goes behind it. */
	private static void board(ClientGameTestContext context, TestServerContext server, UUID self) {
		Vec3 ship = server.computeOnServer(s -> ship(s).position());
		server.runCommand("tp @a " + ship.x + " " + ship.y + " " + (ship.z + 3.5) + " 180 25");
		context.waitTicks(10);
		context.waitFor(client -> client.crosshairPickEntity != null && ShipMarker.of(client.crosshairPickEntity) != null, 40);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		until(context, client -> ShipClient.piloting(client) != null, 40, "a right-click on the X-Wing climbs into the cockpit");
		check(server.computeOnServer(s -> me(s, self).getVehicle() instanceof ShipEntity), "the server has the player in the ship");
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK), "the camera goes behind the ship");
		context.getInput().lookAt(180.0F, 10.0F);
		context.waitTicks(10);
		context.takeScreenshot("ship_cockpit");
	}

	/** Space lifts, W speeds up to top speed, A and D turn and bank, the S-foils open. */
	private static void fly(ClientGameTestContext context, TestServerContext server, UUID self) {
		double ground = server.computeOnServer(s -> ship(s).getY());
		context.getInput().holdKeyFor(options -> options.keyJump, 30);
		context.waitTicks(3);
		double lifted = server.computeOnServer(s -> ship(s).getY());
		check(lifted > ground + 4.0, "Space lifts the hovering ship (" + ground + " -> " + lifted + ")");
		until(context, client -> ShipClient.visual(clientShip(client)).foils(1.0F) > 0.95F, 40, "the S-foils open in the air");

		check(!server.computeOnServer(s -> SaberAdvancements.has(me(s, self), SaberAdvancements.FIRST_FLIGHT)), "no first flight yet");
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(30);
		double half = server.computeOnServer(s -> ship(s).speed());
		check(half > 0.5, "W opens the throttle (speed " + half + ")");
		context.takeScreenshot("ship_flight_throttle");
		context.waitTicks(30);
		double top = server.computeOnServer(s -> ship(s).speed());
		check(top <= ShipType.XWING.maxSpeed() + 1e-6 && top > ShipType.XWING.maxSpeed() * 0.95, "the speed stops at top speed (" + top + ")");
		check(server.computeOnServer(s -> SaberAdvancements.has(me(s, self), SaberAdvancements.FIRST_FLIGHT)), "full speed: the first flight advancement");

		float yaw = server.computeOnServer(s -> ship(s).getYRot());
		context.getInput().holdKey(options -> options.keyRight);
		context.waitTicks(12);
		float roll = context.computeOnClient(client -> ShipClient.visual(clientShip(client)).roll(1.0F));
		context.takeScreenshot("ship_flight_bank");
		context.waitTicks(8);
		context.getInput().releaseKey(options -> options.keyRight);
		float turned = server.computeOnServer(s -> net.minecraft.util.Mth.wrapDegrees(ship(s).getYRot() - yaw));
		check(turned > 30.0F, "D turns the ship to the right (" + turned + " degrees)");
		check(Math.abs(roll) > 10.0F, "it banks into the turn (" + roll + " degrees)");
		float view = context.computeOnClient(client -> net.minecraft.util.Mth.wrapDegrees(client.player.getYRot() - clientShip(client).getYRot()));
		check(Math.abs(view) < 20.0F, "the pilot's view turned with the ship (" + view + " degrees off the nose)");

		// Nose up while flying.
		double before = server.computeOnServer(s -> ship(s).getY());
		context.getInput().holdKeyFor(options -> options.keyJump, 20);
		check(server.computeOnServer(s -> ship(s).getXRot()) < -20.0F, "Space pulls the nose up in flight");
		check(server.computeOnServer(s -> ship(s).getY()) > before + 5.0, "and the ship climbs");
		context.takeScreenshot("ship_flight_climb");
		context.waitTicks(20);
		context.getInput().releaseKey(options -> options.keyUp);

		// Brake to a hover.
		context.getInput().holdKeyFor(options -> options.keyDown, 40);
		check(server.computeOnServer(s -> ship(s).speed()) < 0.05, "S brakes to a hover");
		check(server.computeOnServer(s -> Math.abs(ship(s).getXRot())) < 5.0F, "hovering, the nose levels out");
	}

	/** The attack key fires the cannons at their rate; they hit and break no block. */
	private static void lasers(ClientGameTestContext context, TestServerContext server, UUID self) {
		// Two floating targets in front of the wing tips, a glass box behind them.
		String box = server.computeOnServer(s -> {
			ShipEntity ship = ship(s);
			Vec3 forward = Vec3.directionFromRotation(0.0F, ship.getYRot());
			Vec3 right = Vec3.directionFromRotation(0.0F, ship.getYRot() + 90.0F);

			for (int side : new int[] {-1, 1}) {
				Vec3 at = ship.position().add(forward.scale(18.0)).add(right.scale(side * 2.7)).add(0.0, 0.2, 0.0);
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "summon minecraft:husk " + at.x + " " + at.y + " " + at.z
						+ " {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:200}],Health:200f}");
			}

			BlockPos wall = BlockPos.containing(ship.position().add(forward.scale(26.0)));
			return (wall.getX() - 6) + " " + (wall.getY() - 2) + " " + (wall.getZ() - 6) + " " + (wall.getX() + 6) + " " + (wall.getY() + 4) + " "
					+ (wall.getZ() + 6);
		});
		server.runCommand("fill " + box + " minecraft:glass hollow");
		context.waitTicks(10);
		int glass = server.computeOnServer(ShipGameTest::glass);
		int seen = context.computeOnClient(client -> ShipClient.shotsSeen());
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		until(context, client -> ShipClient.shotsSeen() > seen, 10, "the attack key fires a cannon, and the client gets its muzzle flash");
		context.takeScreenshot("ship_lasers");
		context.waitTicks(16);
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		int shots = context.computeOnClient(client -> ShipClient.shotsSeen()) - seen;
		check(shots >= 3 && shots <= 20 / ShipType.XWING.interval() + 1, "held for a second: fired at the cannons' rate (" + shots + " shots)");
		context.waitTicks(20);
		List<Float> health = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Zombie.class, AREA, Entity::isAlive).stream()
				.map(Zombie::getHealth).toList());
		check(health.size() == 2 && health.stream().allMatch(h -> h < 200.0F), "both targets in front of the wing tips were hit " + health);
		check(server.computeOnServer(ShipGameTest::glass) == glass, "the lasers broke no block (" + glass + " glass)");
		server.runCommand("kill @e[type=husk]");
		server.runCommand("fill " + box + " minecraft:air");
	}

	/** Flying into a wall: a crash, the ship stops, the wall stays. */
	private static void crash(ClientGameTestContext context, TestServerContext server, UUID self) {
		int stone = server.computeOnServer(s -> {
			ShipEntity ship = ship(s);
			Vec3 forward = Vec3.directionFromRotation(0.0F, ship.getYRot());
			Vec3 right = Vec3.directionFromRotation(0.0F, ship.getYRot() + 90.0F);
			BlockPos center = BlockPos.containing(ship.position().add(forward.scale(24.0)));
			int count = 0;

			for (int along = -8; along <= 8; along++) {
				for (int up = -4; up <= 6; up++) {
					BlockPos pos = BlockPos.containing(Vec3.atCenterOf(center).add(right.scale(along))).above(up);
					s.overworld().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
					s.overworld().setBlockAndUpdate(pos.relative(net.minecraft.core.Direction.getApproximateNearest(forward)),
							Blocks.STONE.defaultBlockState());
				}
			}

			return countStone(s);
		});
		int crashes = server.computeOnServer(s -> ShipEntity.crashes());
		context.getInput().holdKey(options -> options.keyUp);
		for (int i = 0; i < 80 && server.computeOnServer(s -> ShipEntity.crashes()) == crashes; i++) {
			context.waitTick();
		}

		context.getInput().releaseKey(options -> options.keyUp);
		check(server.computeOnServer(s -> ShipEntity.crashes()) > crashes, "flying into the wall is a crash");
		context.waitTicks(2);
		context.takeScreenshot("ship_crash");
		check(server.computeOnServer(s -> ship(s).speed()) < 0.8, "the crash stops the ship (speed " + server.computeOnServer(s -> ship(s).speed()) + ")");
		check(server.computeOnServer(s -> s.overworld().noCollision(ship(s), ship(s).getBoundingBox())), "the ship is not inside the wall");
		check(server.computeOnServer(ShipGameTest::countStone) == stone, "the wall is still standing, every block");
		check(context.computeOnClient(client -> ShipClient.piloting(client) != null), "the pilot is still in the cockpit");
		context.getInput().holdKeyFor(options -> options.keyDown, 30);
	}

	/** Sneaking climbs out; high up the pilot glides down. */
	private static void climbOut(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.getInput().holdKeyFor(options -> options.keyJump, 20);
		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		until(context, client -> client.player.getVehicle() == null, 20, "sneaking climbs out");
		check(server.computeOnServer(s -> me(s, self).getVehicle() == null), "the server lets the player out too");
		check(server.computeOnServer(s -> me(s, self).hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)), "high up the pilot glides down");
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.options.getCameraType() == CameraType.FIRST_PERSON), "the camera is back where it was");
		for (int i = 0; i < 300 && !server.computeOnServer(s -> ship(s).onGround()); i++) {
			context.waitTick();
		}

		check(server.computeOnServer(s -> ship(s).onGround()), "the empty ship settles to the ground");
		until(context, client -> ShipClient.visual(clientShip(client)).foils(1.0F) < 0.05F, 40, "landed, its S-foils close");
		// Step back and look at the parked ship for the picture.
		server.runCommand("execute as @p at @e[type=minecraft:pig,limit=1,sort=nearest] positioned ^ ^ ^10 run tp @s ~ ~1 ~ facing entity @e[type=minecraft:pig,limit=1,sort=nearest]");
		context.waitTicks(10);
		context.takeScreenshot("ship_settled");
		check(server.computeOnServer(s -> Starships.liveCount()) == 1, "still exactly one ship");
	}

	/** A real pig is still a pig. */
	private static void plainPig(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute at @p run summon minecraft:pig ~2 ~ ~2 {Tags:[\"plain\"]}");
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.level.getEntitiesOfClass(Pig.class, AREA, pig -> ShipMarker.of(pig) == null).size()) == 1,
				"a summoned pig stays a pig");
		context.takeScreenshot("ship_plain_pig");
		server.runCommand("kill @e[tag=plain]");
	}

	/** Logged out in the cockpit: back in the ship after opening the world, no pig left over. */
	private static void reopen(ClientGameTestContext context, TestWorldSave save, UUID self) {
		try (TestSingleplayerContext singleplayer = save.open()) {
			context.waitTicks(80);
			TestServerContext server = singleplayer.getServer();
			check(server.computeOnServer(s -> Starships.liveCount()) == 1, "after opening the world the X-Wing is back, once");
			check(server.computeOnServer(s -> me(s, self).getVehicle() instanceof ShipEntity), "and the player sits in it again");
			check(server.computeOnServer(s -> allPigs(s)) == 1, "no ship was saved as a pig");
			until(context, client -> ShipClient.piloting(client) != null, 40, "the client is in the cockpit again");
			context.takeScreenshot("ship_reopened");
			context.getInput().holdKeyFor(options -> options.keyShift, 3);
			context.waitTicks(10);
			server.computeOnServer(s -> {
				Starships.resetCooldowns();
				return Starships.command(me(s, self), "dismiss", "xwing");
			});
			context.waitTicks(10);
			check(server.computeOnServer(s -> Starships.liveCount() == 0 && Starships.parked(me(s, self)) == null), "back to the hangar: the ship is gone");
		}
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("time set day");
				context.waitFor(client -> ShipClient.status() != null, 40);
				context.runOnClient(client -> ShipClient.command("board", ShipType.XWING));
				until(context, client -> ShipClient.piloting(client) != null, 60, "dedicated server: the hangar's fly button puts the player in the cockpit");
				context.getInput().holdKeyFor(options -> options.keyJump, 20);
				context.getInput().holdKey(options -> options.keyUp);
				context.waitTicks(40);
				Vec3 onServer = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ShipEntity.class, AREA).getFirst().position());
				Vec3 onClient = context.computeOnClient(client -> clientShip(client).position());
				context.getInput().releaseKey(options -> options.keyUp);
				check(onServer.distanceTo(onClient) < 5.0, "dedicated server: the client sees the ship where the server has it (" + onServer + " / " + onClient + ")");
				check(onServer.horizontalDistance() > 20.0, "dedicated server: it really flew (" + onServer + ")");
				context.takeScreenshot("ship_dedicated_server");
				context.getInput().holdKeyFor(options -> options.keyShift, 3);
				context.waitTicks(10);
			}

			context.waitTicks(20);
			check(server.computeOnServer(s -> Starships.liveCount() == 0), "dedicated server: the ship leaves with its player");
		}
	}

	// Helpers.

	private static ShipEntity ship(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(ShipEntity.class, AREA, Entity::isAlive).getFirst();
	}

	private static Entity clientShip(Minecraft client) {
		return client.level.getEntitiesOfClass(Pig.class, AREA, pig -> ShipMarker.of(pig) != null).getFirst();
	}

	private static int clientShips(Minecraft client) {
		return client.level.getEntitiesOfClass(Pig.class, AREA, pig -> ShipMarker.of(pig) != null).size();
	}

	/** Pigs that look like ships: the real ship, and any copy that should not be there. */
	private static int allPigs(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(Pig.class, AREA, pig -> ShipMarker.of(pig) != null).size();
	}

	private static int glass(MinecraftServer server) {
		return count(server, Blocks.GLASS);
	}

	private static int countStone(MinecraftServer server) {
		return count(server, Blocks.STONE);
	}

	private static int count(MinecraftServer server, net.minecraft.world.level.block.Block block) {
		int count = 0;
		ShipEntity ship = ship(server);
		BlockPos center = ship.blockPosition();

		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-40, -8, -40), center.offset(40, 12, 40))) {
			if (server.overworld().getBlockState(pos).is(block)) {
				count++;
			}
		}

		return count;
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static void until(ClientGameTestContext context, java.util.function.Predicate<Minecraft> condition, int ticks, String message) {
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
