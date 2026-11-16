package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.ArrivalFilm;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 20, the parts of the story's overhaul that are looked at on their own (the story played
 * through is {@link StoryGameTest}):
 *
 * <ul>
 * <li>The film of the Falcon's arrival at the Tenebris: each shot's picture ({@code phase20_film_*}),
 * the camera out in the film's scene, the HUD hidden, back to the player at the end.</li>
 * <li>The reactor: the way to the lab open where it is drawn open and closed where the wall is
 * drawn, the door seen from the shaft floor and from the passage, the technician's console on the
 * catwalk ({@code phase20_reactor_*}).</li>
 * </ul>
 */
public class Phase20GameTest implements FabricClientGameTest {
	private ClientGameTestContext context;
	private TestServerContext server;
	private UUID self;
	private final List<String> problems = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		context.runOnClient(client -> {
			client.options.renderDistance().set(10);
			client.options.setCameraType(CameraType.FIRST_PERSON);

			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode survival @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));
			context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));
			context.waitTicks(60);
			check(this.on(s -> session(s) != null && "m1.briefing".equals(StoryTesting.step(session(s)))), "the story does not start at the briefing");

			this.film();
			this.reactor();

			context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
			context.waitTicks(40);
		} finally {
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.AUTO));
		}

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE20 PROBLEM: {}", problem));
			throw new AssertionError("Phase 20: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	// --- The film --------------------------------------------------------------------------------

	/** The film of the arrival: two pictures of each shot, and the camera's way back. */
	private void film() {
		ClientGameTestContext context = this.context;
		context.runOnClient(client -> StoryCinema.startForTest("arrival"));
		context.waitTicks(2);
		check(context.computeOnClient(client -> StoryCinema.active()), "the film does not start");
		check(context.computeOnClient(client -> client.gui.hud.isHidden()), "the HUD is shown during the film");
		double away = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().distanceTo(client.player.position()));
		check(away > 300.0, "the film's camera is not out in its scene (" + Math.round(away) + " blocks from the player)");
		int[] moments = {3, 30, 80, 140, 180, 225, 260, 300, 340, 372, 405, 440};

		for (int moment : moments) {
			context.runOnClient(client -> StoryCinema.seekForTest(moment));
			context.waitTicks(1);
			context.takeScreenshot(String.format(java.util.Locale.ROOT, "phase20_film_%03d", moment));
			SaberCraft.LOGGER.info("PHASE20: film at {}: {}", moment, ArrivalFilm.describeForTest(moment + 1));
		}

		int drawn = context.computeOnClient(client -> ArrivalFilm.drawnForTest());
		check(drawn >= moments.length, "the film's scene is not drawn (" + drawn + " frames)");
		context.runOnClient(client -> StoryCinema.seekForTest(ArrivalFilm.LENGTH - 2));
		context.waitTicks(6);
		check(!context.computeOnClient(client -> StoryCinema.active()), "the film does not end");
		check(!context.computeOnClient(client -> client.gui.hud.isHidden()), "the HUD stays hidden after the film");
		double back = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().distanceTo(client.player.getEyePosition()));
		check(back < 1.0, "the camera does not come back to the player after the film (" + back + ")");
		SaberCraft.LOGGER.info("PHASE20: film of the arrival: {} frames drawn", drawn);
	}

	// --- The reactor -----------------------------------------------------------------------------

	/** The way to the lab and the catwalk's console: what is drawn open is open, what is drawn closed is closed. */
	private void reactor() {
		double floor = DestroyerInterior.REACTOR_BOTTOM;
		double middle = DestroyerInterior.LAB_DOOR_X0 + 1.5;
		double door = DestroyerInterior.LAB_WALL;
		// There first (the server's collision sees only loaded chunks: from the Falcon all was free).
		this.place(new Vec3(middle, floor, door - 10.5), 0.0F, 8.0F);
		this.context.waitTicks(30);

		// Along the way to the door: room for the whole body all the way.
		for (double z = door - 8.0; z <= door - 0.4; z += 0.5) {
			Vec3 at = new Vec3(middle, floor, z);
			check(this.on(s -> this.free(s, at, 0.3, 1.8)), "one cannot walk the way to the lab at z " + z);
		}

		// Beside it, behind the shaft's drawn wall (radius REACTOR_WALL): solid (one walked into
		// places that looked closed).
		int closed = 0;
		int open = 0;

		for (int a = 50; a <= 95; a += 3) {
			double r = DestroyerInterior.REACTOR_WALL + 0.45;
			Vec3 at = new Vec3(DestroyerInterior.REACTOR_X + Math.cos(Math.toRadians(a)) * r, floor + 0.5,
					DestroyerInterior.REACTOR_Z + Math.sin(Math.toRadians(a)) * r);

			if (at.x > DestroyerInterior.LAB_DOOR_X0 - 0.15 && at.x < DestroyerInterior.LAB_DOOR_X0 + 3.15) {
				continue;
			}

			if (this.on(s -> this.free(s, at, 0.1, 0.2))) {
				open++;
				problem("behind the reactor wall by the way to the lab is room at " + at);
			} else {
				closed++;
			}
		}

		SaberCraft.LOGGER.info("PHASE20: reactor wall by the lab way: {} points closed, {} open", closed, open);
		// The pictures: the way from the shaft floor, the door from the passage, the catwalk's console.
		this.context.takeScreenshot("phase20_reactor_lab_way");
		this.place(new Vec3(middle, floor, door - 4.2), 0.0F, 5.0F);
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase20_reactor_lab_door");
		BlockPos desk = DestroyerInterior.CATWALK_DESK;
		this.place(new Vec3(desk.getX() + 1.6, desk.getY(), desk.getZ() - 2.6), 25.0F, 22.0F);
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase20_reactor_catwalk_console");
		check(!this.on(s -> this.free(s, new Vec3(desk.getX() + 0.5, desk.getY() + 0.1, desk.getZ() + 0.5), 0.2, 0.5)),
				"the catwalk's console does not collide");
	}

	/** Puts the player in the Star Destroyer of their story (plot space), looking that way. */
	private void place(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> session(s).origin(Story.DESTROYER));
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	/** Whether a box (half width, height) at a point of the Star Destroyer's plot is free of anything that collides. */
	private boolean free(MinecraftServer s, Vec3 at, double half, double height) {
		ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
		Vec3 world = at.add(Vec3.atLowerCornerOf(session(s).origin(Story.DESTROYER)));
		return level.noCollision(null, new AABB(world.x - half, world.y + 0.05, world.z - half, world.x + half, world.y + height, world.z + half));
	}

	// --- Helpers ---------------------------------------------------------------------------------

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE20 CHECK FAILED: {}", problem);
		this.problems.add(problem);
	}

	private <T> T on(java.util.function.Function<MinecraftServer, T> what) {
		return this.server.computeOnServer(what::apply);
	}

	private ServerPlayer me(MinecraftServer server) {
		return server.getPlayerList().getPlayer(this.self);
	}

	private StorySession session(MinecraftServer server) {
		ServerPlayer player = this.me(server);
		return player == null ? null : Story.sessionForTest(player).orElse(null);
	}
}
