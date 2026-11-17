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
import com.phiro.sabercraft.client.interior.InteriorClient;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorSystems;
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
 * <li>A crawl way both ways: the hatch opened with the use key from the corridor, crawled into,
 * M-31 right beside one in the way (nobody pushed), the hatch kept open while M-31 is in it, shut
 * with the use key from inside, opened again from inside, out again, shut by itself once nobody is
 * near ({@code phase20_vent_*}).</li>
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
			// Nobody of the story sees one while walking round the ship here (the corridor's troopers
			// caught one at the hatch).
			this.server.runOnServer(s -> StoryTesting.unseen(session(s), 1_000_000));
			this.reactor();
			this.vents();

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

	// --- A crawl way ------------------------------------------------------------------------------

	/** The crawl way behind the hangar corridor's hatch, in and out again (the hatch opens to the east). */
	private void vents() {
		ClientGameTestContext context = this.context;
		BlockPos cell = DestroyerInterior.HATCHES.get(0).cell();
		double h = DestroyerInterior.HANGAR + 1;
		double hx = cell.getX();
		double hz = cell.getZ() + 0.5;
		this.place(new Vec3(hx + 2.4, h, hz), 90.0F, 28.0F);
		context.waitTicks(20);
		context.takeScreenshot("phase20_vent_hatch_shut");
		check(!this.hatchOpen(cell), "the hatch is open before anyone opened it");
		// Open with the use key, from the corridor (aimed at the hatch's middle).
		Vec3 eye = new Vec3(hx + 1.9, h + 1.62, hz);
		float down = (float) Math.toDegrees(Math.atan2(eye.y - (h + 0.5), eye.x - (hx + 0.5)));
		this.place(new Vec3(hx + 1.9, h, hz), 90.0F, down);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(8);

		if (!this.hatchOpen(cell)) {
			problem("the use key does not open the hatch from the corridor");
			this.server.runOnServer(s -> InteriorSystems.useForTest(me(s), this.world(s, cell)));
			context.waitTicks(5);
		}

		context.takeScreenshot("phase20_vent_hatch_open");
		// In, crouching, then on along the way.
		this.place(new Vec3(hx + 1.55, h, hz), 90.0F, 0.0F);
		context.getInput().holdKey(options -> options.keyShift);
		context.getInput().holdKeyFor(options -> options.keyUp, 30);
		context.getInput().releaseKey(options -> options.keyShift);
		context.getInput().holdKeyFor(options -> options.keyUp, 25);
		context.waitTicks(15);
		Vec3 in = this.on(s -> session(s).local(Story.DESTROYER));
		check(this.on(s -> me(s).getPose() == net.minecraft.world.entity.Pose.SWIMMING) && in.x < hx - 0.5, "one does not crawl into the way (" + in + ")");
		// M-31 right where one is: nobody is pushed, one crawls on past it. (The story gives M-31 a
		// little later, in Mission 1: here it comes along now.)
		this.server.runOnServer(s -> {
			session(s).set("droid.m31");
			Story.updateDroids(session(s));
		});
		context.waitTicks(10);
		this.server.runOnServer(s -> Droids.place(me(s), DroidType.MOUSE, me(s).position(), 90.0F, true));
		context.waitTicks(40);
		Vec3 still = this.on(s -> session(s).local(Story.DESTROYER));
		Vec3 droidAt = this.on(s -> {
			var droid = Droids.droid(me(s), DroidType.MOUSE);
			return droid == null ? Vec3.ZERO : droid.position().subtract(Vec3.atLowerCornerOf(session(s).origin(Story.DESTROYER)));
		});
		check(still.distanceTo(in) < 0.1, "M-31 pushes one along the crawl way (" + in + " -> " + still + ")");
		check(droidAt.distanceTo(in) < 0.3, "M-31 is pushed in the crawl way (" + droidAt + " from " + in + ")");
		context.getInput().holdKeyFor(options -> options.keyUp, 25);
		Vec3 past = this.on(s -> session(s).local(Story.DESTROYER));
		check(past.x < in.x - 1.0, "M-31 blocks the crawl way (" + in + " -> " + past + ")");
		context.takeScreenshot("phase20_vent_past_m31");
		// M-31 in the hatch's opening, one further in: the hatch stays open (it shut on droids).
		this.server.runOnServer(s -> Droids.place(me(s), DroidType.MOUSE, Vec3.atBottomCenterOf(this.world(s, cell)), 90.0F, true));
		this.place(new Vec3(hx - 3.5, h, hz), -90.0F, 0.0F);
		context.waitTicks(220);
		check(this.hatchOpen(cell), "the hatch shuts on M-31 in its opening");
		// M-31 along again; looking back at the hatch from inside, the use key shuts it.
		// (Phase 22: the player first, then M-31 behind them: put the other way round, M-31 was
		// put behind where the player had been, and the player then landed on M-31's way and
		// pushed it in front, into the hatch's opening.)
		this.place(new Vec3(hx - 1.6, h, hz), -90.0F, 4.0F);
		this.server.runOnServer(s -> Droids.place(me(s), DroidType.MOUSE, me(s).position().add(-1.2, 0.0, 0.0), -90.0F, false));
		context.waitTicks(10);
		int shut = context.computeOnClient(client -> InteriorClient.hatchesForTest());
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(8);
		check(!this.hatchOpen(cell), "the use key does not shut the open hatch from inside");
		check(context.computeOnClient(client -> InteriorClient.hatchesForTest()) == shut + 1, "the use key on the open hatch was not taken as shutting it");
		context.takeScreenshot("phase20_vent_hatch_inside");
		// Phase 22: the client must know the hatch is shut before the key is pressed again (it
		// decides from its own world whether the key shuts an open hatch or uses the shut one);
		// on a slow machine the change could still be on its way.
		this.clientSees(cell, false);
		// What the key will meet (logged, to tell a failure's cause).
		String aimed = context.computeOnClient(client -> {
			var hit = client.hitResult;
			String what = hit == null ? "nothing" : hit.getType() + (hit instanceof net.minecraft.world.phys.BlockHitResult block ? " " + block.getBlockPos().toShortString()
					+ " " + client.level.getBlockState(block.getBlockPos()) : hit instanceof net.minecraft.world.phys.EntityHitResult entity ? " " + entity.getEntity() : "");
			return what + ", pose " + client.player.getPose() + " at " + client.player.position() + ", eye " + client.player.getEyePosition();
		});
		String mouseAt = this.on(s -> {
			var droid = Droids.droid(me(s), DroidType.MOUSE);
			return droid == null ? "-" : String.valueOf(droid.position());
		});
		SaberCraft.LOGGER.info("PHASE20: hatch from inside, before opening: aimed at {}; M-31 at {}; hatch at {}", aimed, mouseAt, this.on(s -> this.world(s, cell)));
		// And opens it again from inside; out into the corridor, standing up.
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(8);

		if (!this.hatchOpen(cell)) {
			problem("the use key does not open the hatch from inside (aimed at " + aimed + "; M-31 at " + mouseAt + ")");
			this.server.runOnServer(s -> InteriorSystems.useForTest(me(s), this.world(s, cell)));
			context.waitTicks(5);
		}

		// (Crawling is slow: about a block and a half a second.)
		context.getInput().holdKeyFor(options -> options.keyUp, 70);
		context.waitTicks(5);
		Vec3 out = this.on(s -> session(s).local(Story.DESTROYER));
		check(out.x > hx + 1.0 && this.on(s -> me(s).getPose() == net.minecraft.world.entity.Pose.STANDING), "one does not come out of the crawl way standing (" + out + ")");
		// Nobody near: it shuts by itself.
		this.place(new Vec3(hx + 6.5, h, hz), 90.0F, 0.0F);
		this.server.runOnServer(s -> Droids.place(me(s), DroidType.MOUSE, me(s).position().add(1.0, 0.0, 0.0), 90.0F, false));
		context.waitTicks(220);
		check(!this.hatchOpen(cell), "the hatch does not shut by itself once nobody is near");
		SaberCraft.LOGGER.info("PHASE20: crawl way: in at {}, past M-31 to {}, out at {}", in, past, out);
	}

	/** Phase 22: waits (a second at most) until the client's world shows the hatch open or shut. */
	private void clientSees(BlockPos cell, boolean open) {
		BlockPos at = this.on(s -> this.world(s, cell));

		for (int i = 0; i < 20 && this.context.computeOnClient(client -> client.level.getBlockState(at).isAir()) != open; i++) {
			this.context.waitTicks(1);
		}
	}

	private boolean hatchOpen(BlockPos cell) {
		return this.on(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(this.world(s, cell)).isAir());
	}

	private BlockPos world(MinecraftServer s, BlockPos local) {
		return session(s).origin(Story.DESTROYER).offset(local);
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
