package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.interior.FalconInterior;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 19: the way up to the Falcon's upper gun turret without surfaces in front of the eyes;
 * the ships without invisible walls round their people.
 *
 * <p>Screenshots: {@code phase19_*}.
 */
public class Phase19GameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
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
			census(context, "census");

			if (System.getenv("SC_FLICKER") != null) {
				for (String plan : new String[] {"falcon", "star_destroyer"}) {
					BlockPos origin = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, plan));
					Phase16GameTest.tp(context, server, origin, InteriorPlanEntry.entry(plan), 0, 0);
					List<String> report = context.computeOnClient(client -> MeshAudit.report(plan,
							(x, y, z) -> client.level.getBlockState(origin.offset(x, y, z)), 40));
					report.forEach(line -> SaberCraft.LOGGER.info("FLICKER {}: {}", plan, line));
				}
			}

			if (System.getenv("SC_ONLY_CENSUS") != null) {
				return;
			}

			if (System.getenv("SC_SD_WALK") != null) {
				BlockPos destroyer = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "star_destroyer"));
				walkReport(context, server, self, destroyer, "star_destroyer", List.of(new Vec3(40.5, com.phiro.sabercraft.interior.DestroyerInterior.HANGAR + 1, -28.5),
						new Vec3(40.5, com.phiro.sabercraft.interior.DestroyerInterior.DECK + 1, 90.5),
						new Vec3(28.8, com.phiro.sabercraft.interior.DestroyerInterior.REACTOR_BOTTOM, 25.0),
						new Vec3(18.5, com.phiro.sabercraft.interior.DestroyerInterior.HANGAR + 1, -4.5)));
			}

			if (System.getenv("SC_FALCON_WALK") != null) {
				walkReport(context, server, self, falcon, "falcon", List.of(new Vec3(35.5, 7, 31.5), new Vec3(20.5, FalconInterior.FLOOR + 6, 29.6),
						new Vec3(20.5, 1, 29.6)));
			}

			if (System.getenv("SC_ONLY_LIFTS") == null) {
				turretClimb(context, server, self, falcon);
			}

			BlockPos destroyer = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "star_destroyer"));
			lifts(context, server, self, destroyer);
		}
	}

	/**
	 * Every lift of the Star Destroyer with real input: from where the lift at the other end lets one
	 * out, walk onto it, stand still, and arrive (the reactor's lift down and back up was blocked).
	 */
	static void lifts(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos sd) {
		List<com.phiro.sabercraft.interior.InteriorPlan.Lift> lifts = com.phiro.sabercraft.interior.InteriorPlan.of("star_destroyer").lifts();
		int[] from = {1, 0, 3, 2};
		String[] names = {"lobby up to the command deck", "command deck down to the lobby", "reactor catwalk down to the floor", "reactor floor up to the catwalk"};
		server.runCommand("gamemode survival @a");
		context.waitTicks(5);

		for (int i = 0; i < from.length; i++) {
			com.phiro.sabercraft.interior.InteriorPlan.Lift lift = lifts.get(i);
			Vec3 start = lifts.get(from[i]).arrival();
			Vec3 centre = lift.area().getCenter();
			float yaw = (float) Math.toDegrees(Math.atan2(-(centre.x - start.x), centre.z - start.z));
			Phase16GameTest.tp(context, server, sd, start, yaw, 10);
			context.waitTicks(10);
			context.getInput().holdKey(options -> options.keyUp);
			boolean on = false;
			int ticks = 0;

			for (; ticks < 120 && !on; ticks++) {
				context.waitTick();
				on = server.computeOnServer(s -> lift.area().contains(local(s, self, sd)));
			}

			// One stands on the lift (walking on could lead across it and off again).
			context.getInput().releaseKey(options -> options.keyUp);
			Vec3 want = lift.arrival();
			// (Phase 28: the reactor's lift goes only after standing still on it a while, and not
			// again right after a ride: waited for here.)
			int wait = lift.slow() ? 40 + lift.ticks() + lift.cooldown() : 40;

			for (int w = 0; w < wait; w++) {
				context.waitTick();

				if (lift.slow() && server.computeOnServer(s -> {
					Vec3 now = local(s, self, sd);
					return Math.abs(now.y - want.y) < 0.6 && Math.hypot(now.x - want.x, now.z - want.z) < 1.5;
				})) {
					context.waitTicks(10);
					break;
				}
			}

			Vec3 at = server.computeOnServer(s -> local(s, self, sd));
			String where = String.format(Locale.ROOT, "%.2f %.2f %.2f", at.x, at.y, at.z);
			if (!on) {
				// What stops one: the collision boxes round the player, where the client thinks it is.
				String client = context.computeOnClient(c -> String.format(Locale.ROOT, "client %.2f %.2f %.2f yaw %.1f ground %s", c.player.getX() - sd.getX(),
						c.player.getY() - sd.getY(), c.player.getZ() - sd.getZ(), c.player.getYRot(), c.player.onGround()));
				SaberCraft.LOGGER.info("LIFT {}: {}", i, client);
				server.runOnServer(s -> {
					ServerPlayer player = me(s, self);
					for (net.minecraft.world.phys.shapes.VoxelShape shape : player.level().getBlockCollisions(player, player.getBoundingBox().inflate(1.2, 0.0, 1.2))) {
						shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> SaberCraft.LOGGER.info("LIFT box {} {} {} .. {} {} {}", String.format(Locale.ROOT, "%.2f", x0 - sd.getX()),
								String.format(Locale.ROOT, "%.2f", y0 - sd.getY()), String.format(Locale.ROOT, "%.2f", z0 - sd.getZ()),
								String.format(Locale.ROOT, "%.2f", x1 - sd.getX()), String.format(Locale.ROOT, "%.2f", y1 - sd.getY()),
								String.format(Locale.ROOT, "%.2f", z1 - sd.getZ())));
					}
				});
			}

			check(on, "lift " + names[i] + ": one walks onto it from the other end's way out (" + ticks + " ticks, at " + where + ")");
			check(Math.abs(at.y - want.y) < 0.6 && Math.hypot(at.x - want.x, at.z - want.z) < 1.5, "lift " + names[i] + ": it takes one there (at " + where + ")");
			context.takeScreenshot("phase19_lift_" + i);
		}

		// Walking up to slim things that collide as drawn: one gets right up to them (each cell with
		// some of them pushed one out whole before, see the lift).
		int hangar = com.phiro.sabercraft.interior.DestroyerInterior.HANGAR;
		int deck = com.phiro.sabercraft.interior.DestroyerInterior.DECK;
		// Phase 23: the catwalk's railing is gone (narrow bridges without one over the open shaft):
		// one walks right up to the technician's console on the deck level instead.
		Vec3 rail = approach(context, server, self, sd, new Vec3(40.5, hangar + 1, 17.6), 180.0F);
		double gap = rail.z - (com.phiro.sabercraft.interior.DestroyerInterior.CATWALK_DESK.getZ() + 1.0) - 0.3;
		check(gap < 0.2, String.format(Locale.ROOT, "one walks right up to the console on the reactor's deck level (%.2f to spare)", gap));
		context.takeScreenshot("phase19_walk_up_rail");
		Vec3 table = approach(context, server, self, sd, new Vec3(8.5, deck + 1, 107.5), -90.0F);
		check(table.x > 12.55, String.format(Locale.ROOT, "one walks right up to the end of the conference table (x %.2f, its end at 13.05)", table.x));
		context.takeScreenshot("phase19_walk_up_table");
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
	}

	/** Walks from a spot (plot space) one way for two seconds with real input; where one ends up. */
	static Vec3 approach(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos origin, Vec3 from, float yaw) {
		Phase16GameTest.tp(context, server, origin, from, yaw, 15);
		context.waitTicks(5);
		context.getInput().holdKeyFor(options -> options.keyUp, 40);
		context.waitTicks(10);
		return server.computeOnServer(s -> local(s, self, origin));
	}

	/** Where the player is in a plot (plot space). */
	static Vec3 local(MinecraftServer server, UUID self, BlockPos origin) {
		return me(server, self).position().subtract(origin.getX(), origin.getY(), origin.getZ());
	}

	/**
	 * The ladder from the main hold up to the upper turret: what of the model lies right in front of
	 * the eye on the way (a surface closer than a third of a block reads as standing inside a model),
	 * and pictures on the way up in all directions.
	 */
	static void turretClimb(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		List<Vector3f> eyes = new ArrayList<>();

		for (double feet = FalconInterior.FLOOR + 1.0; feet <= FalconInterior.FLOOR + 6.2; feet += 0.1) {
			for (double x : new double[] {20.3, 20.5, 20.7}) {
				for (double z : new double[] {30.21, 30.5}) {
					eyes.add(new Vector3f((float) x, (float) (feet + 1.62), (float) z));
				}
			}
		}

		List<MeshAudit.Near> near = context.computeOnClient(client -> MeshAudit.near("falcon", eyes, 0.3F, false));
		java.util.Map<String, Integer> sources = new java.util.TreeMap<>();
		List<String> across = new ArrayList<>();

		for (MeshAudit.Near one : near) {
			String line = one.toString();
			sources.merge(line.substring(line.indexOf(' ') + 1).replaceAll(" d=[0-9.]+", ""), 1, Integer::sum);

			// Something flat over or under the eye on the way up: one climbs through a surface.
			if (one.across(eyes.get(one.point())) && one.distance() < 0.25F) {
				across.add(line + " eye " + eyes.get(one.point()));
			}
		}

		SaberCraft.LOGGER.info("Turret climb: {} near surfaces at {} eye points, {} kinds", near.size(), eyes.size(), sources.size());
		sources.forEach((key, count) -> SaberCraft.LOGGER.info("  near x{} {}", count, key));
		check(across.isEmpty(), "nothing flat across the way up to the turret, right before the eyes (" + across.size() + ": "
				+ across.stream().limit(3).toList() + ")");
		// The guns stand outside the glass domes (they stood inside the upper one at head height).
		check(com.phiro.sabercraft.interior.Turrets.SIDE - 0.5 > FalconInterior.TOP_DOME_R + 0.1
				&& com.phiro.sabercraft.interior.Turrets.SIDE - 0.5 > FalconInterior.BOTTOM_DOME_R + 0.05, "the turret guns stand outside the glass domes");

		if (System.getenv("SC_TURRET_PICTURES") != null) {
			server.runOnServer(s -> {
				ServerPlayer player = me(s, self);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			});

			for (double feet : new double[] {8.6, 9.4, 9.8, 10.3, 10.8, 11.4, 12.0}) {
				for (float[] view : new float[][] {{0, 0}, {180, 0}, {90, 0}, {-90, 0}, {180, -70}, {0, 70}}) {
					Phase16GameTest.tp(context, server, falcon, new Vec3(20.5, feet, 30.45), view[0], view[1]);
					context.waitTicks(5);
					context.takeScreenshot(String.format(Locale.ROOT, "phase19_climb_%04.1f_%s_%s", feet, (int) view[0], (int) view[1]));
				}
			}

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			for (double feet : new double[] {9.4, 10.8, 12.0}) {
				Phase16GameTest.tp(context, server, falcon, new Vec3(20.5, feet, 30.45), 180, 20);
				context.waitTicks(5);
				context.takeScreenshot(String.format(Locale.ROOT, "phase19_climb_third_%04.1f", feet));
			}

			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			// Standing in the turret by the seat, looking round; the guns from outside.
			Phase16GameTest.look(context, server, falcon, new Vec3(21.6, FalconInterior.FLOOR + 6, 29.4), 60, 5, "phase19_turret_inside_west");
			Phase16GameTest.look(context, server, falcon, new Vec3(19.4, FalconInterior.FLOOR + 6, 29.4), -60, 5, "phase19_turret_inside_east");
			server.runCommand("gamemode spectator @a");
			Phase16GameTest.look(context, server, falcon, new Vec3(31.0, 19.0, 44.0), 145, 25, "phase19_turret_guns_outside");
			Phase16GameTest.look(context, server, falcon, new Vec3(31.0, -3.0, 44.0), 145, -20, "phase19_turret_guns_below");
			server.runCommand("gamemode creative @a");
			context.waitTicks(5);
		}
	}

	/** Walks a plot (all reachable places), logs the problems by room and kind. */
	static com.phiro.sabercraft.client.interior.WalkAudit.Result walkReport(ClientGameTestContext context, TestServerContext server, UUID self,
			BlockPos origin, String plan, List<Vec3> starts) {
		Vec3 first = starts.get(0);
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", origin.getX() + first.x,
				origin.getY() + first.y, origin.getZ() + first.z));
		context.waitTicks(40);
		com.phiro.sabercraft.interior.InteriorPlan interior = com.phiro.sabercraft.interior.InteriorPlan.of(plan);
		com.phiro.sabercraft.client.interior.WalkAudit.Result result = server.computeOnServer(s -> {
			net.minecraft.server.level.ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			com.phiro.sabercraft.interior.InteriorSystems.openDoorsForTest(level, origin, interior);
			return com.phiro.sabercraft.client.interior.WalkAudit.walk(level, me(s, self), origin, interior, starts, 1_000_000);
		});
		SaberCraft.LOGGER.info("WALK {}: {}", plan, result.summary());
		List<String> top = new ArrayList<>(result.lines());
		java.util.regex.Pattern times = java.util.regex.Pattern.compile(".* x(\\d+) e\\.g\\..*");
		top.sort(java.util.Comparator.comparingInt((String line) -> {
			java.util.regex.Matcher m = times.matcher(line);
			return m.matches() ? -Integer.parseInt(m.group(1)) : 0;
		}));
		top.stream().limit(150).forEach(line -> SaberCraft.LOGGER.info("WALKTOP {}: {}", plan, line));
		java.util.Map<String, Integer> byRoom = new java.util.TreeMap<>();

		java.util.regex.Pattern place = java.util.regex.Pattern.compile("^(.*?) (-?\\d+,-?\\d+,-?\\d+) (\\S+).* x(\\d+) e\\.g\\..*$");

		for (String line : result.lines()) {
			java.util.regex.Matcher m = place.matcher(line);

			if (m.matches()) {
				byRoom.merge(m.group(1) + " " + m.group(3), Integer.parseInt(m.group(4)), Integer::sum);
			}
		}

		byRoom.forEach((key, count) -> SaberCraft.LOGGER.info("WALKROOM {} {} x{}", plan, key, count));
		return result;
	}

	/** The invisible barriers of both ships by room (logged; Phase 19 compares before and after). */
	static void census(ClientGameTestContext context, String what) {
		for (String plan : new String[] {"falcon", "star_destroyer"}) {
			List<String> lines = context.computeOnClient(client -> com.phiro.sabercraft.client.interior.WalkAudit.census(
					com.phiro.sabercraft.interior.InteriorPlan.of(plan)));
			lines.forEach(line -> SaberCraft.LOGGER.info("CENSUS {} {}: {}", what, plan, line));
		}
	}

	/** Where a plan's entry is (plot space). */
	private static final class InteriorPlanEntry {
		static Vec3 entry(String plan) {
			return com.phiro.sabercraft.interior.InteriorPlan.of(plan).entry();
		}
	}

	static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
