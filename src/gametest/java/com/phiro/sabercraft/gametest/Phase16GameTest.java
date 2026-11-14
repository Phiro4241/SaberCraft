package com.phiro.sabercraft.gametest;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 16: the interiors without flickering or missing faces, with seats, turrets, doors and the
 * cockpit's hyperspace jump.
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
			look(context, server, falcon, new Vec3(13.5, 7, 23.5), 110, 5, "phase16_falcon_corridor_quarters");
			look(context, server, falcon, new Vec3(20.9, 7, 22.6), -90, 10, "phase16_falcon_door_hold_north");
			look(context, server, falcon, new Vec3(20.9, 7, 22.6), 0, 10, "phase16_falcon_door_hold_north_in");
			look(context, server, falcon, new Vec3(6.5, 7, 25.5), -90, 5, "phase16_falcon_quarters");
			look(context, server, falcon, new Vec3(6.5, 7, 36.5), -60, 5, "phase16_falcon_galley");
			look(context, server, falcon, new Vec3(20.5, 7, 34.5), 180, 0, "phase16_falcon_gunwell");
			look(context, server, falcon, new Vec3(20.5, 7, 40.0), 0, 0, "phase16_falcon_engine_door");
			look(context, server, falcon, new Vec3(16.0, 7, 39.5), -45, 0, "phase16_falcon_ring_south");
			look(context, server, falcon, new Vec3(15.5, 7, 44.0), 120, -20, "phase16_falcon_engine_west");
			look(context, server, destroyer, new Vec3(48.5, 20, 9.5), 0, 0, "phase16_sd_gallery");
			look(context, server, destroyer, new Vec3(48.5, 20, 15.5), 180, 10, "phase16_sd_gallery_back");
			look(context, server, destroyer, new Vec3(33.0, 20, 17.0), -120, 20, "phase16_sd_reactor");
			look(context, server, destroyer, new Vec3(40.5, 151, 84.5), 90, 35, "phase16_sd_bridge_pit");
			look(context, server, falcon, new Vec3(35.5, 7, 31.5), 0, 30, "phase16_falcon_ramp");
			audit(context, server, destroyer, "star_destroyer", new Vec3(40.5, 151, 90.5));
			audit(context, server, destroyer, "star_destroyer", new Vec3(40.5, 20, -20.5));
		}
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

	/** Puts the player at a spot of the plot, looking a way, and takes a picture (if named). */
	private static void look(ClientGameTestContext context, TestServerContext server, BlockPos origin, Vec3 local, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		context.waitTicks(40);

		if (name != null) {
			context.takeScreenshot(name);
		}
	}

	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
