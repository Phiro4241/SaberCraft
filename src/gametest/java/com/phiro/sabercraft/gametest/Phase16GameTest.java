package com.phiro.sabercraft.gametest;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.client.interior.InteriorClient;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.FalconInterior;
import com.phiro.sabercraft.interior.InteriorJump;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorPlan.Door;
import com.phiro.sabercraft.interior.InteriorPlan.Seat;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.Seats;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.interior.Turrets;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Phase 16: the interiors without flickering or missing faces, with seats, turrets, doors, lights,
 * the ship's status and the cockpit's hyperspace jump; no ships called inside them.
 *
 * <p>Screenshots: {@code phase16_*}.
 */
public class Phase16GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("SC_AUDIT_TRACE") != null) {
			System.setProperty("sabercraft.audit.trace", System.getenv("SC_AUDIT_TRACE"));
		}

		context.runOnClient(client -> {
			client.options.renderDistance().set(10);

			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("gamemode creative @a");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());
			BlockPos falcon = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "falcon"));
			BlockPos destroyer = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "star_destroyer"));
			audit(context, server, falcon, "falcon", new Vec3(20.5, 7, 34.5));

			// Former flicker spots, doorways through the curved walls.
			look(context, server, falcon, new Vec3(13.5, 7, 23.5), 110, 5, "phase16_falcon_corridor_quarters");
			look(context, server, falcon, new Vec3(20.9, 7, 22.6), -90, 10, "phase16_falcon_door_hold_north");
			look(context, server, falcon, new Vec3(20.9, 7, 22.6), 0, 10, "phase16_falcon_door_hold_north_in");
			look(context, server, falcon, new Vec3(6.5, 7, 25.5), -90, 5, "phase16_falcon_quarters");
			look(context, server, falcon, new Vec3(6.5, 7, 36.5), -60, 5, "phase16_falcon_galley");
			look(context, server, falcon, new Vec3(20.5, 7, 34.5), 180, 0, "phase16_falcon_gunwell");
			look(context, server, falcon, new Vec3(16.0, 7, 39.5), -45, 0, "phase16_falcon_ring_south");
			look(context, server, falcon, new Vec3(15.5, 7, 44.0), 120, -20, "phase16_falcon_engine_west");
			look(context, server, falcon, new Vec3(18.5, 7, 33.0), -150, 15, "phase16_falcon_hold_couch");
			look(context, server, falcon, new Vec3(35.5, 7, 31.5), 0, 30, "phase16_falcon_ramp");
			look(context, server, destroyer, new Vec3(48.5, 20, 9.5), 0, 0, "phase16_sd_gallery");
			look(context, server, destroyer, new Vec3(33.0, 20, 17.0), -120, 20, "phase16_sd_reactor");
			look(context, server, destroyer, new Vec3(40.5, 151, 84.5), 90, 35, "phase16_sd_bridge_pit");

			cockpit(context, server, falcon);
			spawnBlocked(server, self);
			seats(context, server, self, falcon, destroyer);
			doors(context, server, self, falcon, destroyer);
			consoles(context, server, self, falcon);
			turrets(context, server, self, falcon);
			hyperspace(context, server, self, falcon);

			audit(context, server, destroyer, "star_destroyer", new Vec3(40.5, 151, 90.5));
		}
	}

	/** The new cockpit from inside and outside. */
	private static void cockpit(ClientGameTestContext context, TestServerContext server, BlockPos falcon) {
		look(context, server, falcon, new Vec3(44.0, 7, 28.3), 180, 8, "phase16_cockpit_back");
		look(context, server, falcon, new Vec3(43.5, 7, 22.6), 160, 15, "phase16_cockpit_seats");
		look(context, server, falcon, new Vec3(35.5, 7, 24.0), -90, 5, "phase16_cockpit_from_tube");
		look(context, server, falcon, new Vec3(44.0, 7, 22.8), 0, 10, "phase16_cockpit_to_back");
		server.runCommand("gamemode spectator @a");
		look(context, server, falcon, new Vec3(54.0, 13.0, 15.0), 50, 15, "phase16_cockpit_outside");
		look(context, server, falcon, new Vec3(36.0, 15.0, 10.0), -30, 25, "phase16_cockpit_outside_front");
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
	}

	/** Calling a ship inside an interior is refused with a message. */
	private static void spawnBlocked(TestServerContext server, UUID self) {
		String key = server.computeOnServer(s -> key(Starships.commandForTest(me(s, self), "call", ShipType.XWING)));
		check("sabercraft.ship.not_inside".equals(key), "calling a ship inside an interior is refused (" + key + ")");
	}

	/** Sitting down and standing up, on chairs, the couch, in both ships. */
	private static void seats(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon, BlockPos destroyer) {
		Seat pilot = FalconInterior.SEATS.get(0);
		tp(context, server, falcon, new Vec3(43.5, 7, 22.5), 180, 0);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(pilot.cell())));
		context.waitTicks(10);
		check(server.computeOnServer(s -> Seats.seat(me(s, self)) == pilot), "a click on the pilot's seat sits the player down there");
		Vec3 at = pilot.at().add(falcon.getX(), falcon.getY(), falcon.getZ());
		check(context.computeOnClient(client -> client.player.getVehicle() instanceof Display.ItemDisplay vehicle && vehicle.position().distanceTo(at) < 0.05),
				"the client sees the player on the seat");
		float yaw = context.computeOnClient(client -> client.player.getYRot());
		check(Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - 180.0F)) < 1.0F, "the player faces the window (yaw " + yaw + ")");
		check(context.computeOnClient(client -> InteriorClient.sitting(client) != null), "the client knows the seat");
		context.takeScreenshot("phase16_seat_pilot_first_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("phase16_seat_pilot_third_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot("phase16_seat_pilot_third_person_back");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		standUp(context, server, self, pilot, falcon);

		// The couch: a click anywhere on it takes the nearest place.
		BlockPos couch = server.computeOnServer(s -> {
			for (Seat seat : FalconInterior.SEATS) {
				if (seat.role().equals(Seat.COUCH)) {
					return seat.cell();
				}
			}

			return null;
		});
		tp(context, server, falcon, new Vec3(17.5, 7, 32.5), 150, 20);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(couch)));
		context.waitTicks(10);
		Seat onCouch = server.computeOnServer(s -> Seats.seat(me(s, self)));
		check(onCouch != null && onCouch.role().equals(Seat.COUCH), "a click on the couch sits the player on it");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("phase16_seat_couch_third_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		standUp(context, server, self, onCouch, falcon);

		// A chair in the Star Destroyer's bridge pit.
		Seat chair = DestroyerInterior.SEATS.get(0);
		tp(context, server, destroyer, chair.at().add(0, 0.5, 2.0), 180, 20);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), destroyer.offset(chair.cell())));
		context.waitTicks(10);
		check(server.computeOnServer(s -> Seats.seat(me(s, self)) == chair), "a click on a bridge pit chair sits the player down");
		context.takeScreenshot("phase16_seat_sd_pit_first_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot("phase16_seat_sd_pit_third_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		standUp(context, server, self, chair, destroyer);
		check(server.computeOnServer(s -> Seats.satForTest() >= 3), "three times sat down");
	}

	/** Stands up (as sneaking does) and checks the player is free and on their feet near the seat. */
	private static void standUp(ClientGameTestContext context, TestServerContext server, UUID self, Seat seat, BlockPos origin) {
		server.runOnServer(s -> me(s, self).stopRiding());
		context.waitTicks(10);
		check(server.computeOnServer(s -> !me(s, self).isPassenger() && Seats.takenForTest() == 0), "standing up frees the seat");
		String where = server.computeOnServer(s -> {
			ServerPlayer player = me(s, self);
			AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position());
			double away = player.position().distanceTo(seat.at().add(origin.getX(), origin.getY(), origin.getZ()));
			boolean free = player.level().noCollision(player, box);
			int left = ((ServerLevel) player.level()).getEntitiesOfClass(Display.ItemDisplay.class, box.inflate(4.0),
					display -> display.entityTags().contains(Seats.TAG)).size();
			return String.format(Locale.ROOT, "%.2f %s %d", away, free, left);
		});
		String[] parts = where.split(" ");
		check(Double.parseDouble(parts[0]) < 1.9 && parts[1].equals("true") && parts[2].equals("0"),
				"the player stands next to the seat, not in anything, the seat entity is gone (" + where + ")");
		check(context.computeOnClient(client -> client.player.getVehicle() == null), "the client is off the seat");
	}

	/** Doors open when someone comes near and close behind them. */
	private static void doors(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon, BlockPos destroyer) {
		Door quarters = FalconInterior.DOORS.get(0);
		tp(context, server, falcon, new Vec3(14.6, 7, 24.0), 70, 5);
		context.waitTicks(30);
		check(server.computeOnServer(s -> !InteriorSystems.doorOpenForTest(s.getLevel(ShipInteriors.DIMENSION), falcon, quarters)),
				"the quarters' door is closed while nobody is near");
		context.takeScreenshot("phase16_door_falcon_closed");
		tp(context, server, falcon, new Vec3(12.5, 7, 25.5), 90, 5);
		context.waitTicks(30);
		check(server.computeOnServer(s -> InteriorSystems.doorOpenForTest(s.getLevel(ShipInteriors.DIMENSION), falcon, quarters)),
				"it opens when someone comes near");
		context.takeScreenshot("phase16_door_falcon_open");
		look(context, server, falcon, new Vec3(6.5, 7, 25.5), -90, 5, "phase16_door_falcon_from_inside_open");
		tp(context, server, falcon, new Vec3(7.0, 7, 24.0), -67, 5);
		context.waitTicks(40);
		check(server.computeOnServer(s -> !InteriorSystems.doorOpenForTest(s.getLevel(ShipInteriors.DIMENSION), falcon, quarters)),
				"and closes behind them");
		context.takeScreenshot("phase16_door_falcon_from_inside_closed");

		Door conference = DestroyerInterior.DOORS.get(1);
		tp(context, server, destroyer, new Vec3(41.5, 151, 104.5), 60, 5);
		context.waitTicks(30);
		check(server.computeOnServer(s -> !InteriorSystems.doorOpenForTest(s.getLevel(ShipInteriors.DIMENSION), destroyer, conference)),
				"the conference room's door is closed");
		context.takeScreenshot("phase16_door_sd_closed");
		tp(context, server, destroyer, new Vec3(38.5, 151, 107.5), 90, 5);
		context.waitTicks(30);
		check(server.computeOnServer(s -> InteriorSystems.doorOpenForTest(s.getLevel(ShipInteriors.DIMENSION), destroyer, conference)),
				"and opens for someone in front of it");
		context.takeScreenshot("phase16_door_sd_open");
		look(context, server, destroyer, new Vec3(40.5, 151, 96.0), 0, 5, "phase16_door_sd_bridge_from_bridge");
	}

	/** The lights and the ship's status at the engineering station. */
	private static void consoles(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		tp(context, server, falcon, new Vec3(23.5, 7, 31.5), 90, 0);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(27, FalconInterior.FLOOR + 2, 31)));
		context.waitTicks(20);
		InteriorPlan plan = InteriorPlan.of("falcon");
		check(server.computeOnServer(s -> InteriorSystems.dimmed(s.getLevel(ShipInteriors.DIMENSION), falcon, plan)), "the light switch dims the lights");
		look(context, server, falcon, new Vec3(26.0, 7, 36.0), 140, 10, "phase16_falcon_hold_dimmed");
		check(context.computeOnClient(client -> InteriorClient.dimmed(client.level, falcon, plan)), "the client sees the lights dimmed");
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(27, FalconInterior.FLOOR + 2, 31)));
		context.waitTicks(10);
		check(server.computeOnServer(s -> !InteriorSystems.dimmed(s.getLevel(ShipInteriors.DIMENSION), falcon, plan)), "and switches them on again");
		String status = server.computeOnServer(s -> key(InteriorSystems.statusForTest(me(s, self), plan)));
		check(status.startsWith("sabercraft.interior.status"), "the engineering station reports the ship's state (" + status + ")");
	}

	/** The gunner's seat: aiming and firing. */
	private static void turrets(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		Seat gunner = FalconInterior.SEATS.stream().filter(seat -> seat.role().equals(Seat.GUNNER) && Turrets.upper(seat)).findFirst().orElseThrow();
		tp(context, server, falcon, new Vec3(20.5, FalconInterior.FLOOR + 6, 29.6), 180, 0);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(gunner.cell())));
		context.waitTicks(10);
		check(server.computeOnServer(s -> Seats.seat(me(s, self)) == gunner), "the player sits at the upper guns");
		context.runOnClient(client -> {
			client.player.setYRot(140.0F);
			client.player.setXRot(-12.0F);
		});
		context.waitTicks(10);
		int before = server.computeOnServer(s -> Turrets.shotsForTest());

		for (int i = 0; i < 6; i++) {
			server.runOnServer(s -> Turrets.fire(me(s, self)));
			context.waitTicks(5);
		}

		int shots = server.computeOnServer(s -> Turrets.shotsForTest()) - before;
		check(shots == 6, "the guns fire (" + shots + " shots)");
		int bolts = server.computeOnServer(s -> s.getLevel(ShipInteriors.DIMENSION)
				.getEntitiesOfClass(BlasterBolt.class, new AABB(falcon).inflate(120.0), bolt -> true).size());
		check(bolts > 0, "the bolts fly out of the guns (" + bolts + " in flight)");
		context.takeScreenshot("phase16_turret_firing");
		server.runOnServer(s -> {
			for (int i = 0; i < 3; i++) {
				Turrets.fire(me(s, self));
			}
		});
		check(server.computeOnServer(s -> Turrets.shotsForTest()) - before == 7, "no faster than the guns' rate (one shot in the same tick)");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot("phase16_turret_third_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		standUp(context, server, self, gunner, falcon);
		server.runCommand("gamemode spectator @a");
		look(context, server, falcon, new Vec3(31.0, 19.0, 44.0), 145, 25, "phase16_turret_guns_outside");
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
	}

	/** The jump from the cockpit: only from its front seats, then the drive cools down. */
	private static void hyperspace(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		tp(context, server, falcon, new Vec3(43.5, 7, 22.5), 180, 0);
		check(key(server.computeOnServer(s -> InteriorJump.request(me(s, self)))).equals("sabercraft.interior.jump_seat"), "no jump standing");
		Seat rear = FalconInterior.SEATS.get(2);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(rear.cell())));
		context.waitTicks(5);
		check(key(server.computeOnServer(s -> InteriorJump.request(me(s, self)))).equals("sabercraft.interior.jump_seat"), "no jump from a rear seat");
		server.runOnServer(s -> me(s, self).stopRiding());
		context.waitTicks(5);
		Seat copilot = FalconInterior.SEATS.get(1);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(copilot.cell())));
		context.waitTicks(10);
		context.runOnClient(client -> client.player.setXRot(-8.0F));
		// The hyperdrive lever on the dashboard, used from the seat.
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(45, FalconInterior.FLOOR + 1, FalconInterior.COCKPIT_FRONT + 1)));
		check(server.computeOnServer(s -> InteriorJump.running(falcon)), "the hyperdrive lever starts the jump from the co-pilot's seat");
		check(key(server.computeOnServer(s -> InteriorJump.request(me(s, self)))).equals("sabercraft.hyperspace.busy"), "one jump at a time");
		context.waitTicks(20);
		check(context.computeOnClient(client -> InteriorClient.jump(falcon, client.level) != null), "the client shows the jump");
		context.takeScreenshot("phase16_hyperspace_charge");
		context.waitTicks(25);
		context.takeScreenshot("phase16_hyperspace_tunnel");
		context.waitTicks(40);
		context.takeScreenshot("phase16_hyperspace_tunnel_late");
		context.waitTicks(55);
		context.takeScreenshot("phase16_hyperspace_arrive");
		context.waitTicks(40);
		check(server.computeOnServer(s -> !InteriorJump.running(falcon)), "the jump ends");
		context.takeScreenshot("phase16_hyperspace_after");
		check(key(server.computeOnServer(s -> InteriorJump.request(me(s, self)))).equals("sabercraft.hyperspace.cooldown"), "then the drive cools down");
		server.runOnServer(s -> me(s, self).stopRiding());
		context.waitTicks(5);
	}

	/** No two opaque surfaces of the model in the same place, none lying on a block's face. */
	private static void audit(ClientGameTestContext context, TestServerContext server, BlockPos origin, String plan, Vec3 at) {
		look(context, server, origin, at, 0, 0, null);
		List<String> report = context.computeOnClient(client -> MeshAudit.report(plan,
				(x, y, z) -> client.level.getBlockState(origin.offset(x, y, z)), 3000));

		for (String line : report) {
			SaberCraft.LOGGER.info("AUDIT {}: {}", plan, line);
		}

		List<String> raw = context.computeOnClient(client -> MeshAudit.uncovered(plan,
				(x, y, z) -> client.level.getBlockState(origin.offset(x, y, z)), 3000));

		for (String line : raw) {
			SaberCraft.LOGGER.info("UNCOVERED {}: {}", plan, line);
		}
	}

	private static void tp(ClientGameTestContext context, TestServerContext server, BlockPos origin, Vec3 local, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		context.waitTicks(10);
	}

	/** Puts the player at a spot of the plot, looking a way, and takes a picture (if named). */
	private static void look(ClientGameTestContext context, TestServerContext server, BlockPos origin, Vec3 local, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		context.waitTicks(40);

		if (name != null) {
			context.takeScreenshot(name);
		}
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static String key(Component message) {
		return message == null ? "null" : message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : message.getString();
	}

	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
