package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.CollisionDebug;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.CrewTalk;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.FalconInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorPlan.Seat;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.Seats;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 18: the Millennium Falcon free to walk (no invisible walls or floors, seats and consoles
 * in reach of a click, nobody suffocates sitting down or gets stuck standing up), the turret and
 * the cockpit closed, a livelier Star Destroyer.
 *
 * <p>Screenshots: {@code phase18_*}.
 */
public class Phase18GameTest implements FabricClientGameTest {
	/**
	 * How many places of the Falcon may still be stopped by something not drawn, stand on it, or
	 * stand in a wall (the audit is stricter since this phase: the floor no longer counts as seen).
	 * Left: 38 places, all invisible stops, none in a wall: 8 single places at the edges of the
	 * hold's doorways and 30 on the edge of the rear cockpit seats by the round wall.
	 */
	static final int FALCON_ALLOWED = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				CHAT.add(message.getString());
			}
		});
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
			server.runCommand("difficulty normal");
			server.runCommand("gamemode creative @a");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());
			BlockPos falcon = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "falcon"));

			// Phase17GameTest walks the Falcon with the same starts and the same limit, before this
			// class: the suite does not walk it twice (about 1.5 minutes). SC_FALCON_WALK walks it
			// here too, for running this class on its own.
			if (System.getenv("SC_FALCON_WALK") != null && System.getenv("SC_SKIP_WALK") == null) {
				Phase17GameTest.walk(context, server, self, falcon, "falcon", List.of(new Vec3(35.5, 7, 31.5),
						new Vec3(20.5, FalconInterior.FLOOR + 6, 29.6), new Vec3(20.5, 1, 29.6)), FALCON_ALLOWED);
			}

			if (System.getenv("SC_ONLY_SD") == null) {
				seats(context, server, self, falcon);
				reach(context, server, self, falcon);
				pictures(context, server, self, falcon);
			}

			crew(context, server, self, falcon);
			BlockPos destroyer = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "star_destroyer"));

			if (System.getenv("SC_SD_WALK") != null) {
				Phase17GameTest.walk(context, server, self, destroyer, "star_destroyer", List.of(new Vec3(40.5, DestroyerInterior.HANGAR + 1, -28.5),
						new Vec3(40.5, DestroyerInterior.DECK + 1, 90.5), new Vec3(28.8, DestroyerInterior.REACTOR_BOTTOM, 25.0),
						new Vec3(18.5, DestroyerInterior.HANGAR + 1, -4.5)), Integer.MAX_VALUE);
			}

			destroyer(context, server, self, destroyer);
		}
	}

	private static final List<String> CHAT = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** The Falcon's people: there, spoken to (a line in the chat), pictures. */
	private static void crew(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		int y0 = FalconInterior.FLOOR + 1;
		Phase16GameTest.look(context, server, falcon, new Vec3(17.2, y0, 26.8), -135, 5, "phase18_falcon_smuggler");
		Phase16GameTest.look(context, server, falcon, new Vec3(15.0, y0, 43.2), -48, 5, "phase18_falcon_wookiee");
		Phase16GameTest.look(context, server, falcon, new Vec3(14.5, y0, 9.0), 150, 5, "phase18_falcon_rebel");

		for (Crew one : FalconInterior.CREW) {
			talk(context, server, self, falcon, FalconInterior.CREW, one, "the Falcon's " + one.kind());
		}
	}

	/**
	 * Stands a step in front of one of a crew, looking at their chest, and right-clicks (the use key,
	 * as a player would): the server answers once, and not again at once (cooldown).
	 */
	static void talk(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos origin, List<Crew> all, Crew one, String what) {
		double[] at = one.where(0);
		// A free place 1.7 blocks from them: in front, at either side or behind (their yaw: 0 = facing south).
		Vec3 feet = null;
		float yaw = 0.0F;

		for (float turn : new float[] {0.0F, 90.0F, -90.0F, 180.0F}) {
			double a = Math.toRadians(one.yaw() + turn);
			Vec3 spot = new Vec3(at[0] - Math.sin(a) * 1.7, at[1], at[2] + Math.cos(a) * 1.7);
			boolean free = server.computeOnServer(s -> {
				ServerPlayer player = me(s, self);
				Vec3 world = spot.add(origin.getX(), origin.getY(), origin.getZ());
				AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(world);
				CrewTalk.Hit seen = CrewTalk.hit(all, spot.add(0, 1.62, 0), new Vec3(at[0], at[1] + 1.2, at[2]), 0, 0.0);
				return player.level().noCollision(box) && seen != null && seen.crew() == one
						&& all.stream().noneMatch(other -> other.box(0).inflate(0.4).contains(spot.add(0, 0.5, 0)));
			});

			if (free) {
				feet = spot;
				yaw = one.yaw() + turn + 180.0F;
				break;
			}
		}

		check(feet != null, "there is room to stand by " + what);

		if (feet == null) {
			return;
		}

		float pitch = (float) Math.toDegrees(Math.atan2(0.42, 1.7));
		Phase16GameTest.tp(context, server, origin, feet, yaw, pitch);
		context.waitTicks(10);
		int before = server.computeOnServer(s -> CrewTalk.saidForTest());
		int talked = context.computeOnClient(client -> com.phiro.sabercraft.client.interior.InteriorClient.talkedForTest());
		int chat = CHAT.size();
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(10);
		int after = server.computeOnServer(s -> CrewTalk.saidForTest());
		String why = after == before + 1 ? "" : context.computeOnClient(client -> "client asked " + (com.phiro.sabercraft.client.interior.InteriorClient
				.talkedForTest() - talked) + ", crosshair " + client.hitResult.getType() + " " + client.hitResult.getLocation()) + ", server sees "
				+ server.computeOnServer(s -> {
					ServerPlayer player = me(s, self);
					Vec3 corner = Vec3.atLowerCornerOf(origin);
					Vec3 eye = player.getEyePosition();
					CrewTalk.Hit hit = CrewTalk.hit(all, eye.subtract(corner), eye.add(player.getViewVector(1.0F).scale(CrewTalk.REACH)).subtract(corner),
							s.getLevel(ShipInteriors.DIMENSION).getGameTime(), 0.2);
					BlockHitResult wall = player.level().clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(CrewTalk.REACH)),
							ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
					return (hit == null ? "nobody" : hit.crew().kind() + " at " + hit.distance()) + ", wall " + wall.getType() + " "
							+ wall.getBlockPos().subtract(origin).toShortString() + " at " + wall.getLocation().distanceTo(eye) + " " + player.getPose() + " "
							+ player.level().getBlockState(wall.getBlockPos()) + " shape " + com.phiro.sabercraft.interior.ProxyShapes.at(wall.getBlockPos());
				});
		check(after == before + 1, "one can speak to " + what + " (" + before + " -> " + after + why + ")");
		check(CHAT.size() > chat, "their answer is in the chat (" + (CHAT.isEmpty() ? "-" : CHAT.get(CHAT.size() - 1)) + ")");
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		context.waitTicks(10);
		int again = server.computeOnServer(s -> CrewTalk.saidForTest());
		check(again == after, what + " does not answer again at once (" + after + " -> " + again + ")");
	}

	/**
	 * The Star Destroyer: the crew spoken to, the crawl way (a hatch opened, crouching in front of it
	 * one crawls in, out into the hidden room standing), the reactor's lift down to the new lower
	 * level, the drawn steps and crates, pictures of the crew.
	 */
	private static void destroyer(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos sd) {
		int deck = DestroyerInterior.DECK + 1;
		int hangar = DestroyerInterior.HANGAR + 1;
		server.runCommand("gamemode survival @a");
		context.waitTicks(5);

		// Speaking: the guard at the bridge door, an officer in the pit, a pilot, a guard of honour, the spy.
		List<Crew> crew = DestroyerInterior.CREW;

		for (Crew one : crew) {
			if (one.still() && (one.role().equals("bridge_guard") || one.role().equals("pilot") || one.role().equals("guard")
					|| one.role().equals("reactor") && one.y() == DestroyerInterior.REACTOR_BOTTOM && one.kind().equals(Crew.TECH))) {
				talk(context, server, self, sd, crew, one, one.kind() + " (" + one.role() + ")");
			}
		}

		// The spy in the hidden room (sitting on the cot).
		for (Crew one : crew) {
			if (one.role().equals("spy")) {
				talk(context, server, self, sd, crew, one, "the spy in the hidden room");
			}
		}

		// The hangar deck lies north of the plot's corner: it had no plan before (its seats, hatches
		// and crew could not be clicked). A seat in flight control and a cargo container's store.
		for (com.phiro.sabercraft.interior.InteriorPlan.Seat seat : DestroyerInterior.SEATS) {
			if (seat.cell().getZ() < 0) {
				Phase16GameTest.tp(context, server, sd, seat.at().add(0.0, 0.2, 0.0), seat.yaw(), 0);
				server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), sd.offset(seat.cell())));
				context.waitTicks(10);
				check(server.computeOnServer(s -> com.phiro.sabercraft.interior.Seats.seat(me(s, self)) == seat),
						"one can sit down in flight control (" + seat.cell().toShortString() + ")");
				server.runOnServer(s -> me(s, self).stopRiding());
				context.waitTicks(5);
				break;
			}
		}

		BlockPos store = DestroyerInterior.STORES.get(0);
		Phase16GameTest.tp(context, server, sd, Vec3.atBottomCenterOf(store.above()).add(0.0, 0.0, 1.6), 180, 30);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), sd.offset(store.above())));
		context.waitTicks(5);
		check(server.computeOnServer(s -> me(s, self).containerMenu != me(s, self).inventoryMenu), "a click on a hangar container opens the store under it");
		server.runOnServer(s -> me(s, self).closeContainer());
		context.waitTicks(5);

		// A remark by itself, near the bridge door.
		Phase16GameTest.tp(context, server, sd, new Vec3(40.5, deck, 100.5), 180, 0);
		check(server.computeOnServer(s -> CrewTalk.remark(me(s, self), s.getLevel(ShipInteriors.DIMENSION).getGameTime())),
				"someone near remarks on something by themselves");

		// The crawl way: the hatch in the hangar corridor's west wall.
		BlockPos hatch = sd.offset(DestroyerInterior.HATCHES.get(0).cell());
		Phase16GameTest.look(context, server, sd, new Vec3(38.4, hangar, -9.5), 90, 30, "phase18_sd_hatch_closed");
		String clicked = server.computeOnServer(s -> {
			net.minecraft.world.level.block.state.BlockState before = s.getLevel(ShipInteriors.DIMENSION).getBlockState(hatch);
			InteriorSystems.useForTest(me(s, self), hatch);
			return before + " -> " + s.getLevel(ShipInteriors.DIMENSION).getBlockState(hatch);
		});
		context.waitTicks(10);
		check(server.computeOnServer(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(hatch).isAir()),
				"a click opens the hatch (" + clicked + ", then " + server.computeOnServer(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(hatch)) + ")");
		Phase16GameTest.look(context, server, sd, new Vec3(38.4, hangar, -9.5), 90, 30, "phase18_sd_hatch_open");
		Phase16GameTest.tp(context, server, sd, new Vec3(37.55, hangar, -9.5), 90, 10);
		context.getInput().holdKey(options -> options.keyShift);
		context.getInput().holdKeyFor(options -> options.keyUp, 30);
		context.getInput().releaseKey(options -> options.keyShift);
		context.getInput().holdKeyFor(options -> options.keyUp, 30);
		String crawl = server.computeOnServer(s -> {
			ServerPlayer player = me(s, self);
			return player.getPose() + " " + String.format(Locale.ROOT, "%.2f", player.getX() - sd.getX());
		});
		check(crawl.startsWith("SWIMMING") && Double.parseDouble(crawl.split(" ")[1]) < 35.5, "crouching at the hatch one crawls into the vent (" + crawl + ")");
		context.takeScreenshot("phase18_sd_vent_crawl");
		// From the branch south into the hidden room: standing up again there.
		Phase16GameTest.tp(context, server, sd, new Vec3(18.5, hangar, -9.5), 0, 10);
		context.waitTicks(5);
		context.getInput().holdKeyFor(options -> options.keyUp, 50);
		context.waitTicks(10);
		String out = server.computeOnServer(s -> {
			ServerPlayer player = me(s, self);
			return player.getPose() + " " + String.format(Locale.ROOT, "%.2f", player.getZ() - sd.getZ());
		});
		check(out.startsWith("STANDING") && Double.parseDouble(out.split(" ")[1]) > -7.0, "out of the vent into the hidden room, standing (" + out + ")");
		Phase16GameTest.look(context, server, sd, new Vec3(18.5, hangar, -6.5), 30, 15, "phase18_sd_hidden_room");
		Phase16GameTest.look(context, server, sd, new Vec3(20.5, hangar, -3.0), 110, 20, "phase18_sd_hidden_room_spy");
		Phase16GameTest.look(context, server, sd, new Vec3(18.5, hangar, -3.0), 180, 20, "phase18_sd_hidden_room_exit");
		context.waitTicks(220);
		check(server.computeOnServer(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(hatch).is(net.minecraft.world.level.block.Blocks.BARRIER)),
				"the hatch closes by itself when nobody is near");

		// The reactor: down with the lift from the catwalk, the lower level.
		Phase16GameTest.tp(context, server, sd, new Vec3(31.0, hangar, 25.0), 90, 0);
		context.waitTicks(80);
		double y = server.computeOnServer(s -> me(s, self).getY() - sd.getY());
		check(Math.abs(y - DestroyerInterior.REACTOR_BOTTOM) < 0.6, "the reactor's lift takes one down to the lower level (y " + y + ")");
		context.takeScreenshot("phase18_sd_reactor_lift_arrival");
		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
		Phase16GameTest.look(context, server, sd, new Vec3(28.8, DestroyerInterior.REACTOR_BOTTOM, 25.0), -90, -5, "phase18_sd_reactor_floor");
		Phase16GameTest.look(context, server, sd, new Vec3(43.5, DestroyerInterior.REACTOR_BOTTOM, 14.5), -43, 50, "phase18_sd_reactor_converter");
		Phase16GameTest.look(context, server, sd, new Vec3(40.5, DestroyerInterior.REACTOR_BOTTOM, 34.0), 0, 10, "phase18_sd_reactor_console");
		Phase16GameTest.look(context, server, sd, new Vec3(33.5, DestroyerInterior.REACTOR_BOTTOM, 31.5), -135, -35, "phase18_sd_reactor_up");
		Phase16GameTest.look(context, server, sd, new Vec3(28.8, hangar, 25.0), -60, 35, "phase18_sd_reactor_catwalk");

		// The crew.
		Phase16GameTest.look(context, server, sd, new Vec3(40.5, deck, 103.0), 180, 5, "phase18_sd_troopers_guard");
		Phase16GameTest.look(context, server, sd, new Vec3(41.5, deck, 106.0), 160, 5, "phase18_sd_patrol");
		Phase16GameTest.look(context, server, sd, new Vec3(40.5, deck, 110.0), 0, 5, "phase18_sd_guards");
		Phase16GameTest.look(context, server, sd, new Vec3(37.5, deck, 67.5), 90, 5, "phase18_sd_admiral");
		Phase16GameTest.look(context, server, sd, new Vec3(43.0, hangar, -22.0), -143, 5, "phase18_sd_pilots");
		Phase16GameTest.look(context, server, sd, new Vec3(40.5, deck, 80.0), 90, 30, "phase18_sd_pit");
		Phase16GameTest.look(context, server, sd, new Vec3(40.5, deck, 76.5), 90, 25, "phase18_sd_steps");
		Phase16GameTest.look(context, server, sd, new Vec3(40.5, deck, 69.0), 180, 35, "phase18_sd_helm");
		Phase16GameTest.look(context, server, sd, new Vec3(56.0, hangar, -52.0), -148, 10, "phase18_sd_hangar_crates");
		Phase16GameTest.look(context, server, sd, new Vec3(24.5, hangar, -40.0), -90, -35, "phase18_sd_tie_hangers");
		Phase16GameTest.look(context, server, sd, new Vec3(40.5, hangar, -12.0), 180, 5, "phase18_sd_hangar_patrol");
		// The chat with an answer in it.
		context.runOnClient(client -> client.gui.hud.toggle());
		Crew guard = DestroyerInterior.CREW.stream().filter(one -> one.role().equals("bridge_guard")).findFirst().orElseThrow();
		server.runOnServer(s -> CrewTalk.forgetForTest());
		talk(context, server, self, sd, DestroyerInterior.CREW, guard, "the bridge guard (again, for the picture)");
		context.takeScreenshot("phase18_sd_talk_chat");
		context.runOnClient(client -> client.gui.hud.toggle());
	}

	/**
	 * Every seat of the Falcon in survival: a click sits one down, nothing hurts while sitting (the
	 * couch suffocated people), standing up leaves one free to walk.
	 */
	private static void seats(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		server.runCommand("gamemode survival @a");
		server.runCommand("effect clear @a");
		context.waitTicks(5);

		for (Seat seat : FalconInterior.SEATS) {
			Vec3 near = seat.at().add(0.0, 0.2, 0.0);
			Phase16GameTest.tp(context, server, falcon, near, seat.yaw(), 0);
			server.runOnServer(s -> {
				ServerPlayer player = Phase17GameTest.me(s, self);
				player.setHealth(player.getMaxHealth());
				InteriorSystems.useForTest(player, falcon.offset(seat.cell()));
			});
			context.waitTicks(60);
			String state = server.computeOnServer(s -> {
				ServerPlayer player = Phase17GameTest.me(s, self);
				return Seats.seat(player) == seat ? "seated " + player.getHealth() + " " + player.isInWall() : "not seated";
			});
			check(state.equals("seated 20.0 false"), seat.role() + " seat at " + seat.cell() + ": seated, no harm after 3 s (" + state + ")");
			server.runOnServer(s -> Phase17GameTest.me(s, self).stopRiding());
			context.waitTicks(10);
			String after = server.computeOnServer(s -> {
				ServerPlayer player = Phase17GameTest.me(s, self);
				AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position());
				return String.format(Locale.ROOT, "%s %s %.1f", player.isPassenger(), player.level().noCollision(player, box), player.getHealth());
			});
			check(after.startsWith("false true"), seat.role() + " seat at " + seat.cell() + ": standing up leaves the player free (" + after + ")");
		}

		server.runCommand("gamemode creative @a");
		context.waitTicks(5);
	}

	/**
	 * The crosshair reaches what one uses: looking at a seat or console from where one stands
	 * hits it (an invisible full block in front of it took the click before).
	 */
	private static void reach(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		int y0 = FalconInterior.FLOOR + 1;
		List<Object[]> cases = new ArrayList<>();
		// Standing (feet), looking at (a point on the thing), what for.
		cases.add(new Object[] {new Vec3(44.5, y0, 22.6), new Vec3(44.6, y0 + 0.85, 20.5), "hyperspace lever"});
		cases.add(new Object[] {new Vec3(45.5, y0, 23.0), new Vec3(45.4, y0 + 0.85, 20.6), "hyperspace lever (co-pilot's side)"});
		cases.add(new Object[] {new Vec3(43.6, y0, 23.0), new Vec3(42.5, y0 + 0.4, 21.7), "pilot's seat"});
		cases.add(new Object[] {new Vec3(43.6, y0, 25.6), new Vec3(42.5, y0 + 0.4, 24.5), "rear seat"});
		cases.add(new Object[] {new Vec3(16.5, y0, 32.4), new Vec3(16.0, y0 + 0.4, 30.4), "couch (front)"});
		cases.add(new Object[] {new Vec3(14.8, y0, 32.2), new Vec3(14.9, y0 + 0.4, 29.9), "couch (side)"});
		cases.add(new Object[] {new Vec3(18.8, y0, 32.4), new Vec3(17.4, y0 + 0.4, 30.3), "couch (other end)"});
		cases.add(new Object[] {new Vec3(24.0, y0, 31.0), new Vec3(25.5, y0 + 0.4, 31.5), "engineer's seat"});
		cases.add(new Object[] {new Vec3(23.5, y0, 31.0), new Vec3(26.4, y0 + 0.75, 30.5), "status desk"});
		cases.add(new Object[] {new Vec3(24.3, y0, 32.5), new Vec3(27.05, y0 + 1.5, 31.5), "light switch"});
		cases.add(new Object[] {new Vec3(20.5, FalconInterior.FLOOR + 6, 29.7), new Vec3(20.5, FalconInterior.FLOOR + 6.4, 28.5), "upper gunner's seat"});
		cases.add(new Object[] {new Vec3(20.5, 1, 29.7), new Vec3(20.5, 1.4, 28.5), "lower gunner's seat"});

		for (Object[] c : cases) {
			Vec3 feet = (Vec3) c[0];
			Vec3 target = (Vec3) c[1];
			String what = (String) c[2];
			String result = server.computeOnServer(s -> {
				ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
				ServerPlayer player = Phase17GameTest.me(s, self);
				Vec3 eye = feet.add(falcon.getX(), falcon.getY() + player.getEyeHeight(Pose.STANDING), falcon.getZ());
				Vec3 to = target.add(falcon.getX(), falcon.getY(), falcon.getZ());
				Vec3 far = eye.add(to.subtract(eye).normalize().scale(player.blockInteractionRange()));
				BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

				if (hit.getType() != HitResult.Type.BLOCK) {
					return "nothing hit";
				}

				boolean used = InteriorSystems.handles(level, hit.getBlockPos(), hit.getLocation());
				return used + " " + hit.getBlockPos().subtract(falcon).toShortString() + " at " + hit.getLocation().distanceTo(eye);
			});
			check(result.startsWith("true"), "one can click the " + what + " (" + result + ")");
		}
	}

	/** Pictures of the places fixed, the collision shown. */
	private static void pictures(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		int y0 = FalconInterior.FLOOR + 1;
		Phase16GameTest.look(context, server, falcon, new Vec3(18.5, y0, 33.0), -150, 15, "phase18_falcon_couch");
		Phase16GameTest.look(context, server, falcon, new Vec3(13.5, y0, 26.5), -120, 20, "phase18_falcon_couch_side");

		// Climbing the ladder to the upper turret: the camera passes the ceiling (flying, so the
		// player stays where it is put).
		fly(context, server, self, true);

		// (While looking for where the camera passed the ceiling there were 30 of these; the suite
		// keeps the heights that showed it.)
		for (double y : new double[] {y0 + 3.4, y0 + 4.6}) {
			for (int yaw : new int[] {0, 180}) {
				Phase16GameTest.look(context, server, falcon, new Vec3(20.5, y, 29.75), yaw, -10,
						String.format(Locale.ROOT, "phase18_falcon_ladder_%.1f_%d", y - y0, yaw));
			}
		}

		// Down into the lower turret.
		for (double y : new double[] {1.0}) {
			for (int yaw : new int[] {0, 180}) {
				Phase16GameTest.look(context, server, falcon, new Vec3(20.5, y0 - 5 + y, 29.75), yaw, -10,
						String.format(Locale.ROOT, "phase18_falcon_ladder_down_%.1f_%d", y, yaw));
			}
		}

		fly(context, server, self, false);
		Phase16GameTest.look(context, server, falcon, new Vec3(20.5, FalconInterior.FLOOR + 6, 29.75), 0, 20, "phase18_falcon_top_turret");
		Phase16GameTest.look(context, server, falcon, new Vec3(20.5, FalconInterior.FLOOR + 6, 29.75), 180, -20, "phase18_falcon_top_turret_back");
		Phase16GameTest.look(context, server, falcon, new Vec3(20.5, 1, 29.75), 0, 30, "phase18_falcon_lower_turret");

		// Behind the cockpit door, looking down.
		for (int yaw : new int[] {-90, -60, -120, 90}) {
			Phase16GameTest.look(context, server, falcon, new Vec3(40.2, y0, 24.0), yaw, 60, "phase18_falcon_cockpit_door_" + yaw);
		}

		Phase16GameTest.look(context, server, falcon, new Vec3(40.2, y0, 24.0), -90, -60, "phase18_falcon_cockpit_door_up");
		Phase16GameTest.look(context, server, falcon, new Vec3(37.5, y0, 24.0), -90, 30, "phase18_falcon_cockpit_door_from_tube");
		Phase16GameTest.look(context, server, falcon, new Vec3(44.0, y0, 23.0), 180, 25, "phase18_falcon_cockpit_dashboard");

		// The collision debug view.
		context.runOnClient(client -> CollisionDebug.set(true));
		Phase16GameTest.look(context, server, falcon, new Vec3(19.5, y0, 33.5), -150, 25, "phase18_debug_collision_couch");
		check(context.computeOnClient(client -> CollisionDebug.shownForTest()) > 0, "the collision debug view shows boxes");
		Phase16GameTest.look(context, server, falcon, new Vec3(44.0, y0, 25.5), 180, 20, "phase18_debug_collision_cockpit");
		Phase16GameTest.look(context, server, falcon, new Vec3(16.0, y0, 40.0), -45, 10, "phase18_debug_collision_ring");
		context.runOnClient(client -> CollisionDebug.set(false));
		context.waitTicks(5);
	}

	/** Lets the player fly (stays where it is put) or not, on both sides. */
	static void fly(ClientGameTestContext context, TestServerContext server, UUID self, boolean on) {
		server.runOnServer(s -> {
			ServerPlayer player = me(s, self);
			player.getAbilities().flying = on;
			player.onUpdateAbilities();
		});
		context.waitTicks(5);
	}

	static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	static void check(boolean condition, String message) {
		if (!condition) {
			SaberCraft.LOGGER.error("FAILED: {}", message);

			if (System.getenv("SC_SOFT") == null) {
				throw new AssertionError(message);
			}

			return;
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}

	static InteriorPlan falconPlan() {
		return InteriorPlan.of("falcon");
	}
}
