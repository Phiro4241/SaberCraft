package com.phiro.sabercraft.gametest;

import java.util.Locale;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.InteriorScene;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 15: the detailed interiors and hulls of the Star Destroyer and the Millennium Falcon.
 * Takes pictures from many places and angles (first person, third person, spectator outside,
 * out of the windows) and checks the models are built, drawn and fast enough.
 *
 * <p>Screenshots: {@code interior_*}.
 */
public class InteriorDesignGameTest implements FabricClientGameTest {
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
			BlockPos destroyer = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "star_destroyer"));
			check(destroyer != null, "the Star Destroyer plot is built");
			audit(server, destroyer, "star_destroyer");
			destroyerViews(context, server, destroyer);
			BlockPos falcon = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "falcon"));
			check(falcon != null, "the Falcon plot is built");
			audit(server, falcon, "falcon");
			falconViews(context, server, falcon);
			migration(context, server, self, falcon);
		}
	}

	/**
	 * A plot built from an older plan is rebuilt from the new one the next time it is asked for,
	 * and what was stored in its chests and barrels is kept.
	 */
	private static void migration(ClientGameTestContext context, TestServerContext server, UUID self, BlockPos origin) {
		String put = server.computeOnServer(s -> {
			for (BlockPos pos : containers(s, origin, "falcon")) {
				net.minecraft.world.Container container = (net.minecraft.world.Container) s.getLevel(ShipInteriors.DIMENSION).getBlockEntity(pos);
				container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
				container.setChanged();
				return pos.subtract(origin).toShortString();
			}

			return "";
		});
		check(!put.isEmpty(), "migration: 7 diamonds put into a container of the Falcon at " + put);
		// Nobody may be aboard while a plot is rebuilt (Phase 16: the camera views leave the player
		// in this plot, which counts as aboard now): out to the overworld first.
		server.runCommand("execute in minecraft:overworld run tp @a 0 120 0");
		context.waitTicks(20);
		// Break something, as if the old plan had looked different, and mark the plot as old.
		server.runOnServer(s -> {
			s.getLevel(ShipInteriors.DIMENSION).setBlock(origin.offset(20, 7, 26), net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(), 3);
			ShipInteriors.agePlotForTest(self, "falcon", 1);
		});
		BlockPos again = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "falcon"));
		check(origin.equals(again), "migration: the plot stays where it was");
		String after = server.computeOnServer(s -> {
			net.minecraft.server.level.ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			int diamonds = 0;

			for (BlockPos pos : containers(s, origin, "falcon")) {
				net.minecraft.world.Container container = (net.minecraft.world.Container) level.getBlockEntity(pos);

				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					if (container.getItem(slot).is(net.minecraft.world.item.Items.DIAMOND)) {
						diamonds += container.getItem(slot).getCount();
					}
				}
			}

			return diamonds + " " + level.getBlockState(origin.offset(20, 7, 26)).isAir();
		});
		check(after.equals("7 true"), "migration: the old plot is rebuilt and the 7 diamonds are kept in its containers (diamonds, block gone: " + after + ")");
		audit(server, origin, "falcon");
	}

	/** Every container (chest, barrel) of a plot. */
	private static java.util.List<BlockPos> containers(net.minecraft.server.MinecraftServer server, BlockPos origin, String planId) {
		net.minecraft.server.level.ServerLevel level = server.getLevel(ShipInteriors.DIMENSION);
		java.util.List<BlockPos> found = new java.util.ArrayList<>();

		for (net.minecraft.world.level.levelgen.structure.BoundingBox box : com.phiro.sabercraft.interior.InteriorPlan.of(planId).volumes()) {
			for (BlockPos local : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockEntity(origin.offset(local)) instanceof net.minecraft.world.Container) {
					found.add(origin.offset(local).immutable());
				}
			}
		}

		return found;
	}

	private static void destroyerViews(ClientGameTestContext context, TestServerContext server, BlockPos o) {
		// On the bridge, at the window.
		look(context, server, o, new Vec3(40.5, 151, 72.5), 180.0F, 8.0F, "interior_sd_bridge_window");
		check(context.computeOnClient(client -> InteriorScene.knowsPlot("star_destroyer")), "the client knows the plot");
		SaberCraft.LOGGER.info("Star Destroyer model: {} quads, built in {} ms, {} quads and {} cells drawn",
				context.computeOnClient(client -> InteriorScene.modelQuads("star_destroyer")), context.computeOnClient(client -> InteriorScene.lastBuildMillis()),
				context.computeOnClient(client -> InteriorScene.lastQuads()), context.computeOnClient(client -> InteriorScene.lastCells()));
		look(context, server, o, new Vec3(40.5, 151, 94.5), 180.0F, 15.0F, "interior_sd_bridge_walkway");
		look(context, server, o, new Vec3(26.5, 147, 95.5), 200.0F, 0.0F, "interior_sd_bridge_pit");
		look(context, server, o, new Vec3(40.5, 151, 67.5), 180.0F, 30.0F, "interior_sd_bridge_down");
		look(context, server, o, new Vec3(40.5, 151, 85.5), 0.0F, 10.0F, "interior_sd_bridge_back");
		look(context, server, o, new Vec3(32.5, 151, 102.5), 60.0F, 12.0F, "interior_sd_conference");
		look(context, server, o, new Vec3(52.5, 151, 102.5), 300.0F, 12.0F, "interior_sd_sensor");
		look(context, server, o, new Vec3(6.5, 152, 107.5), 90.0F, 15.0F, "interior_sd_side_window");
		look(context, server, o, new Vec3(40.5, 20, -20.5), 180.0F, 10.0F, "interior_sd_hangar");
		look(context, server, o, new Vec3(40.5, 26, -63.5), 0.0F, 15.0F, "interior_sd_hangar_back");
		look(context, server, o, new Vec3(10.5, 20, -50.5), 270.0F, 10.0F, "interior_sd_flight_control");
		look(context, server, o, new Vec3(40.5, 20, 13.5), 0.0F, 12.0F, "interior_sd_reactor");
		context.waitTicks(20);
		int playing = context.computeOnClient(client -> InteriorScene.soundSpotsPlaying());
		check(playing > 0, "the reactor hums: " + playing + " ambient sounds playing near the core");
		// Outside, as a spectator.
		server.runCommand("gamemode spectator @a");
		look(context, server, o, new Vec3(40.5, 200, 260.5), 180.0F, 18.0F, "interior_sd_outside_stern_high");
		look(context, server, o, new Vec3(-180.5, 120, -200.5), 225.0F, 10.0F, "interior_sd_outside_side");
		look(context, server, o, new Vec3(40.5, 130, -620.5), 0.0F, 6.0F, "interior_sd_outside_nose");
		look(context, server, o, new Vec3(160.5, -20, -120.5), 135.0F, -20.0F, "interior_sd_outside_below");
		look(context, server, o, new Vec3(40.5, 185, -20.5), 0.0F, 18.0F, "interior_sd_outside_tower");
		look(context, server, o, new Vec3(64.5, 158, 20.5), 25.0F, 2.0F, "interior_sd_outside_bridge_front");
		server.runCommand("gamemode creative @a");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		look(context, server, o, new Vec3(40.5, 151, 80.5), 180.0F, 10.0F, "interior_sd_bridge_third_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private static void falconViews(ClientGameTestContext context, TestServerContext server, BlockPos o) {
		server.runCommand("gamemode creative @a");
		look(context, server, o, new Vec3(21.0, 7.0, 36.0), 200.0F, 12.0F, "interior_falcon_hold");
		check(context.computeOnClient(client -> InteriorScene.knowsPlot("falcon")), "the client knows the Falcon's plot");
		SaberCraft.LOGGER.info("Falcon model: {} quads, built in {} ms", context.computeOnClient(client -> InteriorScene.modelQuads("falcon")),
				context.computeOnClient(client -> InteriorScene.lastBuildMillis()));
		look(context, server, o, new Vec3(14.0, 7.0, 31.5), 240.0F, 20.0F, "interior_falcon_table");
		look(context, server, o, new Vec3(44.0, 7.0, 28.0), 180.0F, 5.0F, "interior_falcon_cockpit");
		look(context, server, o, new Vec3(31.5, 7.0, 24.0), 90.0F, 5.0F, "interior_falcon_cockpit_tube");
		look(context, server, o, new Vec3(9.5, 7.0, 30.5), 180.0F, 5.0F, "interior_falcon_corridor");
		look(context, server, o, new Vec3(28.5, 7.0, 42.5), 95.0F, 10.0F, "interior_falcon_engine_room");
		look(context, server, o, new Vec3(12.5, 7.0, 15.0), 180.0F, 10.0F, "interior_falcon_cargo_hold");
		look(context, server, o, new Vec3(8.5, 7.0, 25.5), 90.0F, 15.0F, "interior_falcon_quarters");
		look(context, server, o, new Vec3(35.5, 7.0, 31.5), 0.0F, 30.0F, "interior_falcon_ramp");
		look(context, server, o, new Vec3(20.5, 12.0, 29.5), 180.0F, 0.0F, "interior_falcon_top_turret");
		server.runCommand("gamemode spectator @a");
		look(context, server, o, new Vec3(20.5, 40.0, 75.5), 180.0F, 35.0F, "interior_falcon_outside_stern");
		look(context, server, o, new Vec3(60.5, 22.0, -20.5), 40.0F, 15.0F, "interior_falcon_outside_front");
		look(context, server, o, new Vec3(-25.5, 18.0, 30.5), 270.0F, 15.0F, "interior_falcon_outside_side");
		look(context, server, o, new Vec3(45.5, -12.0, 40.5), 140.0F, -35.0F, "interior_falcon_outside_below");
		look(context, server, o, new Vec3(43.0, 9.0, 8.5), 0.0F, 5.0F, "interior_falcon_outside_cockpit");
		server.runCommand("gamemode creative @a");
	}

	/**
	 * Every block of an interior that needs something to hold on to (ladders, levers, chains, chests
	 * on hatches...) has it: nothing floats or would pop off.
	 */
	private static void audit(TestServerContext server, BlockPos origin, String planId) {
		String result = server.computeOnServer(s -> {
			net.minecraft.server.level.ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			com.phiro.sabercraft.interior.InteriorPlan plan = com.phiro.sabercraft.interior.InteriorPlan.of(planId);
			StringBuilder bad = new StringBuilder();
			int checked = 0;

			for (net.minecraft.world.level.levelgen.structure.BoundingBox box : plan.volumes()) {
				for (BlockPos local : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					BlockPos pos = origin.offset(local);
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

					if (state.isAir()) {
						continue;
					}

					checked++;

					if (!state.canSurvive(level, pos) && bad.length() < 400) {
						bad.append(local.toShortString()).append(' ').append(state).append("; ");
					}
				}
			}

			return checked + "|" + bad;
		});
		String[] parts = result.split("\\|", 2);
		check(parts[1].isEmpty(), planId + ": every block is held (" + parts[0] + " blocks checked)" + (parts[1].isEmpty() ? "" : ": " + parts[1]));
	}

	/** Puts the player at a spot of the plot, looking a way, and takes a picture. */
	private static void look(ClientGameTestContext context, TestServerContext server, BlockPos origin, Vec3 local, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		context.waitTicks(40);
		context.takeScreenshot(name);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
