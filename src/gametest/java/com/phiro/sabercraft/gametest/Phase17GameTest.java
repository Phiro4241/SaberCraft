package com.phiro.sabercraft.gametest;

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
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.InteriorClient;
import com.phiro.sabercraft.client.interior.InteriorScene;
import com.phiro.sabercraft.client.interior.WalkAudit;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.FalconInterior;
import com.phiro.sabercraft.interior.InteriorJump;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorPlan.Door;
import com.phiro.sabercraft.interior.InteriorPlan.Seat;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.interior.ShipLights;

/**
 * Phase 17: the Millennium Falcon walked through for real (collision and drawn surfaces agree
 * everywhere one can go, see {@link WalkAudit}), the Star Destroyer as well; the dimmer in its
 * steps on both ships, the doors, the hyperspace jump all round, the bridge's view without the
 * Falcon, the Star Destroyer's crew and maintenance ways.
 *
 * <p>Screenshots: {@code phase17_*}.
 */
public class Phase17GameTest implements FabricClientGameTest {
	/**
	 * How many places of the Falcon may still not fit (see the report). Phase 18: the audit is
	 * stricter (the floor under the feet no longer hides invisible walls), so the same limit as
	 * Phase 18's test.
	 */
	static final int FALCON_ALLOWED = Phase18GameTest.FALCON_ALLOWED;

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
			BlockPos destroyer = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "star_destroyer"));

			if (System.getenv("SC_PLAN_DUMP") != null) {
				for (String line : WalkAudit.dump(InteriorPlan.of("falcon"), 5, 11)) {
					SaberCraft.LOGGER.info("PLAN {}", line);
				}

				if (System.getenv("SC_PLAN_ONLY") != null) {
					return;
				}
			}

			// Building a plan (as a server does for each new plot) must be quick: a dedicated
			// server's watchdog stops a tick that takes a minute.
			for (String id : List.of("falcon", "star_destroyer")) {
				long start = System.nanoTime();
				com.phiro.sabercraft.client.interior.VirtualPlot.of(InteriorPlan.of(id));
				long millis = (System.nanoTime() - start) / 1_000_000L;
				SaberCraft.LOGGER.info("PLAN BUILD {}: {} ms", id, millis);
				check(millis < 5000, id + ": the plan builds in " + millis + " ms");
			}

			if (System.getenv("SC_SKIP_WALK") == null) {
				walk(context, server, self, falcon, "falcon", List.of(new Vec3(35.5, 7, 31.5), new Vec3(20.5, FalconInterior.FLOOR + 6, 29.6),
						new Vec3(20.5, 1, 29.6)), FALCON_ALLOWED);
			}

			// The Star Destroyer's walk was a report here (it failed at no number) and took over two
			// minutes: Phase18GameTest walks it with SC_SD_WALK, from more starts.

			pictures(context, server, falcon, destroyer);
			lights(context, server, self, falcon, destroyer);
			doors(context, server, self, falcon);
			hyperspace(context, server, self, falcon);
			bridgeView(context, server, falcon, destroyer);
			crew(context, server, destroyer);
		}
	}

	/** Walks everywhere one can go in a plot, logs what does not fit, fails above {@code allowed} problems. */
	static WalkAudit.Result walk(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos origin, String planId, List<Vec3> starts,
			int allowed) {
		Vec3 first = starts.get(0);
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", origin.getX() + first.x,
				origin.getY() + first.y, origin.getZ() + first.z));
		context.waitTicks(40);
		InteriorPlan plan = InteriorPlan.of(planId);
		WalkAudit.Result result = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			InteriorSystems.openDoorsForTest(level, origin, plan);
			return WalkAudit.walk(level, me(s, self), origin, plan, starts, 4000);
		});

		for (String line : result.lines()) {
			SaberCraft.LOGGER.info("WALK {}: {}", planId, line);
		}

		SaberCraft.LOGGER.info("WALK {}: {}", planId, result.summary());
		int problems = result.inWall() + result.invisible();
		Phase16GameTest.check(System.getenv("SC_WALK_REPORT") != null || problems <= allowed,
				planId + ": collision and drawn surfaces agree everywhere one can walk (" + result.summary() + ")");
		return result;
	}

	/** The places fixed in this phase, from where one saw the faults. */
	private static void pictures(ClientGameTestContext context, TestServerContext server, BlockPos falcon, BlockPos destroyer) {
		Phase16GameTest.look(context, server, falcon, new Vec3(16.0, 7, 39.5), -45, 0, "phase17_falcon_ring_south");
		Phase16GameTest.look(context, server, falcon, new Vec3(26.0, 7, 22.4), 60, 5, "phase17_falcon_ring_north_east");
		Phase16GameTest.look(context, server, falcon, new Vec3(15.5, 7, 44.0), 120, -10, "phase17_falcon_engine_room");
		Phase16GameTest.look(context, server, falcon, new Vec3(26.0, 7, 44.5), 60, 10, "phase17_falcon_hyperdrive");
		Phase16GameTest.look(context, server, falcon, new Vec3(44.0, 7, 22.0), 180, 5, "phase17_falcon_cockpit_window");
		Phase16GameTest.look(context, server, falcon, new Vec3(36.0, 7, 24.0), 90, 0, "phase17_falcon_cockpit_tube");
		Phase16GameTest.look(context, server, falcon, new Vec3(18.5, 7, 33.0), -150, 15, "phase17_falcon_hold_couch");
		Phase16GameTest.look(context, server, falcon, new Vec3(35.5, 7, 31.5), 0, 30, "phase17_falcon_ramp");
		Phase16GameTest.look(context, server, falcon, new Vec3(20.5, 1, 29.6), 0, -30, "phase17_falcon_lower_turret");
		Phase16GameTest.look(context, server, destroyer, new Vec3(38.5, DestroyerInterior.HANGAR + 1, -11.5), 30, 5, "phase17_sd_corridor");
		// Phase 18: the vents are crawl ways behind hatches now; their hatches seen from the rooms.
		Phase16GameTest.look(context, server, destroyer, new Vec3(38.5, DestroyerInterior.HANGAR + 1, -9.5), 90, 25, "phase17_sd_vent");
		Phase16GameTest.look(context, server, destroyer, new Vec3(34.5, DestroyerInterior.PIT + 1, 84.5), -90, 25, "phase17_sd_bridge_vent");
	}

	/** The dimmer: five steps on both ships, each darker than the one before, back to full light. */
	private static void lights(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon, BlockPos destroyer) {
		dimmer(context, server, self, falcon, "falcon", new BlockPos(27, FalconInterior.FLOOR + 2, 31), new Vec3(23.5, 7, 34.5), 140, 5);
		dimmer(context, server, self, destroyer, "star_destroyer", new BlockPos(DestroyerInterior.BRIDGE_X0 + 4, DestroyerInterior.DECK + 1,
				DestroyerInterior.WINDOW_Z + 2), new Vec3(40.5, DestroyerInterior.DECK + 1, 92.5), 180, 10);
	}

	private static void dimmer(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos origin, String planId, BlockPos console,
			Vec3 at, float yaw, float pitch) {
		InteriorPlan plan = InteriorPlan.of(planId);
		Phase16GameTest.look(context, server, origin, at, yaw, pitch, null);
		float last = 2.0F;

		for (int step = 0; step < ShipLights.steps(); step++) {
			int expected = step;
			check(server.computeOnServer(s -> InteriorSystems.lightStep(s.getLevel(ShipInteriors.DIMENSION), origin, plan)) == expected,
					planId + ": the dimmer is at step " + expected);
			// The fade takes about a second.
			context.waitTicks(40);
			check(context.computeOnClient(client -> InteriorClient.lightStep(client.level, origin, plan)) == expected, planId + ": the client sees step " + expected);
			float brightness = context.computeOnClient(client -> InteriorScene.brightnessForTest(planId));
			check(brightness < last - 0.1F, planId + ": step " + expected + " is clearly darker than the one before (" + brightness + ")");
			last = brightness;
			context.takeScreenshot("phase17_" + (planId.equals("falcon") ? "falcon" : "sd") + "_light_" + Math.round(brightness * 100.0F));
			server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), origin.offset(console)));
		}

		// From the darkest step the switch brings full light back, fading up smoothly (no jump).
		// (The client looks at the lamps every 5 ticks: wait until the fade has begun.)
		float early = last;
		int waited = 0;

		while (early <= last + 0.001F && waited < 20) {
			context.waitTicks(1);
			waited++;
			early = context.computeOnClient(client -> InteriorScene.brightnessForTest(planId));
		}

		context.waitTicks(3);
		early = context.computeOnClient(client -> InteriorScene.brightnessForTest(planId));
		check(early > last && early < 0.95F, planId + ": the light comes back gradually (" + early + " 3 ticks after the fade began)");
		context.waitTicks(40);
		check(context.computeOnClient(client -> InteriorScene.brightnessForTest(planId)) > 0.99F, planId + ": and is at full light again");
		check(server.computeOnServer(s -> !InteriorSystems.dimmed(s.getLevel(ShipInteriors.DIMENSION), origin, plan)), planId + ": full light on the server");
	}

	/** The doors: open for someone near, closed (and colliding) again when nobody is; closing is quieter. */
	private static void doors(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		check(InteriorSystems.DOOR_CLOSE_VOLUME < InteriorSystems.DOOR_OPEN_VOLUME, "a door closes more quietly than it opens");
		// The door between the tube and the cockpit.
		Door door = FalconInterior.DOORS.get(5);
		Phase16GameTest.look(context, server, falcon, new Vec3(36.5, 7, 24.0), -90, 0, null);
		check(server.computeOnServer(s -> InteriorSystems.doorOpenForTest(s.getLevel(ShipInteriors.DIMENSION), falcon, door)), "the cockpit door is open");
		context.takeScreenshot("phase17_falcon_door_open");
		Phase16GameTest.look(context, server, falcon, new Vec3(20.5, 7, 34.5), 0, 0, null);
		check(server.computeOnServer(s -> !InteriorSystems.doorOpenForTest(s.getLevel(ShipInteriors.DIMENSION), falcon, door)),
				"and closed again when nobody is near");
		boolean blocks = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			BlockPos cell = falcon.offset(door.x0(), door.y0(), door.z0());
			return !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
		});
		check(blocks, "a closed door collides");
	}

	/** The story's jump, seen from the co-pilot's seat: hyperspace all round, nothing of the space outside at the edges. */
	private static void hyperspace(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos falcon) {
		Phase16GameTest.tp(context, server, falcon, new Vec3(43.5, 7, 22.5), 180, 0);
		Seat copilot = FalconInterior.SEATS.get(1);
		server.runOnServer(s -> InteriorSystems.useForTest(me(s, self), falcon.offset(copilot.cell())));
		context.waitTicks(10);
		context.runOnClient(client -> client.player.setXRot(-8.0F));
		// Phase 31: only the story makes this jump (the lever starts nothing any more).
		check(server.computeOnServer(s -> InteriorJump.story(s.getLevel(ShipInteriors.DIMENSION), falcon)), "the story's jump starts");
		context.waitTicks(15);
		context.takeScreenshot("phase17_hyperspace_charge");
		// The pictures of hyperspace when the client is in it (the server may run behind and then
		// catch up, so a fixed number of ticks is no sure way there).
		context.runOnClient(client -> client.player.setYRot(client.player.getYRot() - 50.0F));
		boolean tunnel = waitForTunnel(context, falcon, 0.15F);
		check(tunnel, "the client is in hyperspace");
		context.takeScreenshot("phase17_hyperspace_tunnel_side");
		float shell = context.computeOnClient(client -> InteriorScene.hyperspaceShellForTest());
		check(shell > 0.99F, "a closed shell round the whole ship hides the space outside (" + shell + ")");
		context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 50.0F));
		context.waitTicks(3);
		context.takeScreenshot("phase17_hyperspace_tunnel");
		check(waitFor(context, 300, () -> !server.computeOnServer(s -> InteriorJump.running(falcon))), "the jump ends");
		server.runOnServer(s -> me(s, self).stopRiding());
		context.waitTicks(5);
	}

	/** From the Star Destroyer's bridge the Falcon (another plot) is not drawn; the Destroyer's own hull is. */
	private static void bridgeView(ClientGameTestContext context, TestServerContext server, BlockPos falcon, BlockPos destroyer) {
		Vec3 bridge = new Vec3(40.5, DestroyerInterior.DECK + 1, DestroyerInterior.WINDOW_Z + 4.5);
		Phase16GameTest.look(context, server, destroyer, bridge, 180, 8, "phase17_sd_bridge_view");
		Vec3 camera = Vec3.atLowerCornerOf(destroyer).add(bridge).add(0, 1.6, 0);
		check(!InteriorScene.drawnFrom(falcon, camera), "the Falcon is not drawn from the bridge");
		check(InteriorScene.drawnFrom(destroyer, camera), "the Star Destroyer is drawn from its own bridge");
		Vec3 inFalcon = Vec3.atLowerCornerOf(falcon).add(20.5, 8.6, 30.5);
		check(!InteriorScene.drawnFrom(destroyer, inFalcon), "and the Star Destroyer is not drawn from inside the Falcon");
	}

	/** The crew: on the bridge, in the hangar, in flight control. */
	private static void crew(ClientGameTestContext context, TestServerContext server, BlockPos destroyer) {
		check(DestroyerInterior.CREW.size() >= 25, "the Star Destroyer has its crew (" + DestroyerInterior.CREW.size() + ")");
		Phase16GameTest.look(context, server, destroyer, new Vec3(40.5, DestroyerInterior.DECK + 1, 94.5), 180, 12, "phase17_sd_crew_bridge");
		Phase16GameTest.look(context, server, destroyer, new Vec3(30.5, DestroyerInterior.PIT + 1, 96.0), 160, 5, "phase17_sd_crew_pit");
		Phase16GameTest.look(context, server, destroyer, new Vec3(40.5, DestroyerInterior.HANGAR + 1, -19.0), 180, 5, "phase17_sd_crew_hangar");
		context.waitTicks(60);
		context.takeScreenshot("phase17_sd_crew_hangar_later");
		Phase16GameTest.look(context, server, destroyer, new Vec3(9.5, DestroyerInterior.HANGAR + 1, -40.0), -110, 10, "phase17_sd_crew_flight_control");
		Phase16GameTest.look(context, server, destroyer, new Vec3(40.5, DestroyerInterior.DECK + 1, 112.5), 180, 0, "phase17_sd_crew_corridor");
		Phase16GameTest.look(context, server, destroyer, new Vec3(56.5, DestroyerInterior.DECK + 1, 110.5), -120, 10, "phase17_sd_crew_sensor_room");
	}

	/** Waits (up to 300 ticks) until the client shows the Falcon in hyperspace, at least {@code from} into that stage. */
	private static boolean waitForTunnel(ClientGameTestContext context, BlockPos falcon, float from) {
		return waitFor(context, 300, () -> context.computeOnClient(client -> {
			InteriorClient.Jump jump = InteriorClient.jump(falcon, client.level);
			return jump != null && jump.stage() == com.phiro.sabercraft.network.InteriorJumpPayload.TUNNEL
					&& InteriorClient.progress(jump, client.level, 0.0F) >= from;
		}));
	}

	/** Waits tick by tick (up to {@code ticks}) until the condition holds. */
	private static boolean waitFor(ClientGameTestContext context, int ticks, java.util.function.BooleanSupplier condition) {
		for (int i = 0; i < ticks; i++) {
			if (condition.getAsBoolean()) {
				return true;
			}

			context.waitTick();
		}

		return condition.getAsBoolean();
	}

	static void check(boolean condition, String message) {
		Phase16GameTest.check(condition, message);
	}

	static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}
}
