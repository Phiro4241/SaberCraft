package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

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
import com.phiro.sabercraft.client.droid.DroidControl;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.client.menu.StoryMenuScreen;
import com.phiro.sabercraft.client.story.StoryVitals;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidMarker;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.FalconInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.Seats;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryStatePayload;
import com.phiro.sabercraft.story.Chapter1;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;
import com.phiro.sabercraft.client.interior.InteriorVisuals;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 22: graphics, surroundings, the story's HUD and its people.
 *
 * <ul>
 * <li>No lit surface (screens, strips) and no moving part (doors, hatches) lies in the plane of
 * another surface (they flickered); no look from a room of the Falcon ends in the void (gaps).</li>
 * <li>The way to the lab is walkable (no invisible walls); the detention door sits cleanly in its
 * wall (pictures).</li>
 * </ul>
 */
public class Phase22GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;

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
			this.run(context, singleplayer);
		}

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE22 PROBLEM: {}", problem));
			throw new AssertionError("Phase 22: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		try {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode creative @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());

			this.surfaces();
			this.labWay();
			this.server.runCommand("gamemode survival @a");
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));
			this.inStory();
			this.afterTheChapter();
		} finally {
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.AUTO));
		}
	}

	// --- Flicker and gaps ----------------------------------------------------------------------

	private void surfaces() {
		System.setProperty("sabercraft.audit.sources", "true");
		for (String plan : new String[] {"falcon", "star_destroyer"}) {
			BlockPos origin = this.on(s -> ShipInteriors.plotForTest(s, this.self, plan));
			Phase16GameTest.tp(this.context, this.server, origin, com.phiro.sabercraft.interior.InteriorPlan.of(plan).entry(), 0, 0);
			this.context.waitTicks(40);
			List<String> decals = this.context.computeOnClient(client -> MeshAudit.decals(plan, new InteriorVisuals.PlotView(origin, client.level), 400));
			decals.forEach(line -> SaberCraft.LOGGER.info("DECALS {}: {}", plan, line));
			check(decals.get(0).startsWith("0 "), plan + ": lit or moving surfaces lie on others (they flicker): " + decals.get(0)
					+ (decals.size() > 1 ? " e.g. " + decals.get(1) : ""));
			List<String> leaks = this.context.computeOnClient(client -> MeshAudit.leaks(plan, (x, y, z) -> client.level.getBlockState(origin.offset(x, y, z)),
					new InteriorVisuals.PlotView(origin, client.level), 48.0F, "falcon".equals(plan) ? 2400 : 320, "falcon".equals(plan) ? 1 : 2, 80));
			leaks.forEach(line -> SaberCraft.LOGGER.info("LEAKS {}: {}", plan, line));

			// The Falcon is closed all round (the open ramp and the cockpit's windows look out on purpose).
			if ("falcon".equals(plan)) {
				leaks.stream().skip(1).filter(line -> !line.startsWith("ramp ") && !line.startsWith("cockpit ") && !line.matches("\\? near 3[456],5,42 .*"))
						.forEach(line -> this.problem("a look from the Falcon ends in the void: " + line));
			}
		}

		BlockPos falcon = this.on(s -> ShipInteriors.plotForTest(s, this.self, "falcon"));
		// Round the hyperdrive and at both ends of the engine room (where the gaps were seen).
		Object[][] views = {{new Vec3(20.5, 7, 42.2), 0.0F, 5.0F, "drive_north"}, {new Vec3(16.6, 7, 46.0), -90.0F, 5.0F, "drive_west"},
				{new Vec3(24.4, 7, 46.0), 90.0F, 5.0F, "drive_east"}, {new Vec3(28.0, 7, 44.0), -60.0F, 0.0F, "engine_east_end"},
				{new Vec3(13.0, 7, 44.0), 60.0F, 0.0F, "engine_west_end"}, {new Vec3(25.0, 7, 43.0), 150.0F, -20.0F, "engine_up_east"},
				{new Vec3(16.0, 7, 43.0), -150.0F, -20.0F, "engine_up_west"}, {new Vec3(20.5, 7, 42.2), 180.0F, 0.0F, "engine_door"}};

		// Phase 22: two looks that ended in the stars (seen in pictures, over the machines at the
		// engine room's outer wall): they meet the ceiling now.
		float[][] probes = {{16.6F, 8.62F, 46.0F, 0.7846F, 0.4118F, 0.4634F}, {13.0F, 8.62F, 44.0F, -0.8351F, 0.4884F, 0.2531F}};

		for (float[] p : probes) {
			List<String> lines = this.context.computeOnClient(client -> MeshAudit.probe("falcon", (x, y, z) -> client.level.getBlockState(falcon.offset(x, y, z)),
					new org.joml.Vector3f(p[0], p[1], p[2]), new org.joml.Vector3f(p[3], p[4], p[5]), 30.0F));
			lines.forEach(line -> SaberCraft.LOGGER.info("PROBE {},{},{}: {}", p[0], p[1], p[2], line));
			check(lines.contains("leak: null"), "the look from " + p[0] + "," + p[1] + "," + p[2] + " ends in the void (the gap over the engine room's machines)");
		}

		for (Object[] view : views) {
			Phase16GameTest.look(this.context, this.server, falcon, (Vec3) view[0], (float) view[1], (float) view[2], "phase22_falcon_" + view[3]);
		}
	}

	// --- The way to the lab, the detention door ----------------------------------------------------

	private void labWay() {
		BlockPos destroyer = this.on(s -> ShipInteriors.plotForTest(s, this.self, "star_destroyer"));
		this.server.runOnServer(s -> com.phiro.sabercraft.interior.InteriorSystems.openDoorsForTest(s.getLevel(ShipInteriors.DIMENSION), destroyer,
				com.phiro.sabercraft.interior.InteriorPlan.of("star_destroyer")));
		com.phiro.sabercraft.client.interior.WalkAudit.Result walk = Phase19GameTest.walkReport(this.context, this.server, this.self, destroyer, "star_destroyer",
				List.of(new Vec3(44.5, R, 33.5), new Vec3(44.5, R, 44.0)));
		walk.lines().stream().filter(line -> line.startsWith("lab") || line.contains("reactor")).forEach(line -> SaberCraft.LOGGER.info("LABWALK {}", line));
		// Nothing in the way to the lab, and no drawn wall one walks into there.
		java.util.regex.Pattern cell = java.util.regex.Pattern.compile("^(?:in a wall|at the eyes|invisible) (-?\\d+),(-?\\d+),(-?\\d+) .*");

		for (String line : walk.lines()) {
			java.util.regex.Matcher m = cell.matcher(line);

			if (m.matches()) {
				int x = Integer.parseInt(m.group(1));
				int y = Integer.parseInt(m.group(2));
				int z = Integer.parseInt(m.group(3));

				if (x >= DestroyerInterior.LAB_DOOR_X0 - 1 && x <= DestroyerInterior.LAB_DOOR_X0 + 3 && y >= R && y <= R + 2 && z >= DestroyerInterior.LAB_WALL - 4
						&& z <= DestroyerInterior.LAB_WALL) {
					this.problem("the way to the lab: " + line);
				}
			}
		}

		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(44.5, R, 33.5), 0, 5, "phase22_lab_way_from_shaft");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(44.5, R, 37.5), 0, 5, "phase22_lab_way_inside");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(44.5, R, 42.5), 180, 5, "phase22_lab_way_from_lab");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(39.5, H, 0.5), 37, 5, "phase22_detention_door_corridor");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(31.5, H, 6.5), -90, 5, "phase22_detention_door_inside");
		// The new skins: a trooper and a technician, face to face.
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(DestroyerInterior.CX + 0.6, H, -0.5), -90, 8, "phase22_skin_trooper");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(DestroyerInterior.HANGAR_X1 - 2.4, H, -30.5), 90, 8, "phase22_skin_technician");
		this.walkSpeed(destroyer);
	}

	/** Walks the way to the lab as a player does (forward key) and measures how fast one gets on. */
	private void walkSpeed(BlockPos destroyer) {
		Phase16GameTest.tp(this.context, this.server, destroyer, new Vec3(44.5, R, 33.5), 0, 0);
		this.server.runCommand("gamemode survival @a");
		this.context.waitTicks(20);
		Vec3 start = this.context.computeOnClient(client -> client.player.position());
		this.context.getInput().holdKeyFor(options -> options.keyUp, 40);
		Vec3 end = this.context.computeOnClient(client -> client.player.position());
		double went = end.subtract(start).horizontalDistance();
		SaberCraft.LOGGER.info("LABWALK speed: {} blocks in 40 ticks from {} to {}", fmt(went), start.subtract(Vec3.atLowerCornerOf(destroyer)),
				end.subtract(Vec3.atLowerCornerOf(destroyer)));
		check(went > 6.5, "walking the way to the lab is slow: " + fmt(went) + " blocks in 2 s (4.3 blocks/s expected), ends at "
				+ end.subtract(Vec3.atLowerCornerOf(destroyer)));
		this.server.runCommand("gamemode creative @a");
	}

	// --- In the story: HUD, locked menus, R2-V7 ------------------------------------------------------

	/** Mission 6 with both droids: the story's HUD, the normal menus locked, R2-V7 his own droid. */
	private void inStory() {
		this.server.runOnServer(s -> StoryTesting.unlockAll(s, this.self));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.MISSION, "", 5));
		this.context.waitTicks(80);
		check(this.on(s -> this.session(s) != null), "Mission 6 does not start");

		if (this.on(s -> this.session(s) == null)) {
			return;
		}

		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 1_000_000));

		// The HUD: no hunger, no experience, the condition bar instead of the hearts.
		int before = this.context.computeOnClient(client -> StoryVitals.drawnForTest());
		this.context.waitTicks(20);
		check(this.context.computeOnClient(client -> StoryVitals.hidesVanilla()), "the story does not hide Minecraft's hunger and experience");
		check(this.context.computeOnClient(client -> StoryVitals.drawnForTest()) > before, "the story's condition bar is not drawn");
		this.context.takeScreenshot("phase22_story_hud_de");
		this.server.runOnServer(s -> this.me(s).setHealth(this.me(s).getMaxHealth() * 0.4F));
		this.context.waitTicks(6);
		float shown = this.context.computeOnClient(client -> StoryVitals.shownForTest());
		check(shown > 0.0F && shown < 0.7F, "the condition bar does not show the hit: " + fmt(shown));
		this.context.takeScreenshot("phase22_story_hud_hurt");
		this.context.runOnClient(client -> SaberLanguage.set(SaberLanguage.ENGLISH));
		this.context.waitTicks(6);
		this.context.takeScreenshot("phase22_story_hud_en");
		this.context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));
		this.server.runCommand("effect give @a minecraft:instant_health 1 4 true");

		// The normal menus: locked on the client and refused by the server.
		check(this.context.computeOnClient(client -> SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.SABER)
				&& SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.DROIDS) && SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.SHIPS)
				&& !SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.STORY) && !SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.SOUND)),
				"the menu's normal areas are not locked in the story (or the story's is)");
		check(this.context.computeOnClient(client -> SaberMenuScreen.create(client, SaberMenuScreen.Tab.SABER) instanceof StoryMenuScreen),
				"the SaberCraft menu opens on its saber area in the story");
		check(this.on(s -> Story.refuse(this.me(s))), "the server does not refuse the normal functions in the story");
		int droids = this.on(s -> Droids.crew(this.me(s)).size());
		this.server.runCommand("execute as @a run sabercraft blaster dl44");
		this.context.waitTicks(5);
		check(this.on(s -> Droids.crew(this.me(s)).size()) == droids, "the story's droids changed");

		// R2-V7: the story's own droid, by name and look, never the normal R2-D2.
		Object[] r2 = this.on(s -> {
			DroidEntity droid = Droids.droid(this.me(s), DroidType.R2D2);
			return droid == null ? null : new Object[] {DroidMarker.story(droid), droid.getCustomName() == null ? "" : droid.getCustomName().getString()};
		});
		check(r2 != null, "R2-V7 is not along in Mission 6");

		if (r2 != null) {
			check("r2v7".equals(r2[0]), "R2-V7 is drawn as the normal R2-D2 (story mark " + r2[0] + ")");
			check("R2-V7".equals(r2[1]), "R2-V7 is called " + r2[1]);
		}

		check("R2-V7".equals(this.on(s -> String.valueOf(Story.droidName(DroidType.R2D2) == null ? "" : Story.droidName(DroidType.R2D2).getString()))),
				"R2-V7's name in messages is not R2-V7");
		this.server.runOnServer(s -> {
			DroidEntity droid = Droids.droid(this.me(s), DroidType.R2D2);

			if (droid != null) {
				droid.teleportToOwner();
			}
		});
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.context.waitTicks(20);
		this.context.takeScreenshot("phase22_story_r2v7");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		// Outside the story nothing of it stays.
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(40);
		check(!this.context.computeOnClient(client -> StoryVitals.hidesVanilla()), "outside the story Minecraft's HUD stays hidden");
		check(!this.context.computeOnClient(client -> SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.SABER)), "outside the story the menu stays locked");
		check(!this.on(s -> Story.refuse(this.me(s))), "outside the story the server refuses the normal functions");
		this.context.takeScreenshot("phase22_outside_hud");
	}

	// --- After the chapter ---------------------------------------------------------------------------

	/**
	 * Continuing a finished chapter: the crew's new lines, no old objective, Dax and Kestrel in the
	 * cockpit's seats (sitting, and nobody sits on them too); a new run forgets it.
	 */
	private void afterTheChapter() {
		this.server.runOnServer(s -> StoryTesting.finishedFile(s, this.self));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));
		this.context.waitTicks(80);

		if (this.on(s -> this.session(s) == null)) {
			this.problem("continuing a finished chapter does not start the story");
			return;
		}

		check(this.on(s -> this.session(s).has(Chapter1.CHAPTER_DONE)), "continuing, the story forgot that the chapter is over");
		StoryStatePayload state = this.context.computeOnClient(client -> StoryClient.state());
		check(state != null && "sabercraft.story.obj.chapter_done".equals(state.objective()) && !state.target(),
				"after the chapter the objective is still " + (state == null ? "-" : state.objective()));

		for (String key : new String[] {"dax", "kestrel"}) {
			String where = this.on(s -> {
				Figure figure = this.session(s).figure(key);
				return figure == null ? "missing" : figure.seated() && figure.onSeat() ? "" : figure.position() + " pose " + figure.pose();
			});
			check(where.isEmpty(), key + " does not sit in the cockpit after the chapter: " + where);
		}

		// The pilot's seat is Dax's: the player does not sit down on him.
		InteriorPlan.Seat pilot = FalconInterior.SEATS.stream().filter(seat -> InteriorPlan.Seat.PILOT.equals(seat.role())).findFirst().orElseThrow();
		this.server.runOnServer(s -> InteriorSystems.useForTest(this.me(s), this.session(s).falcon.offset(pilot.cell())));
		this.context.waitTicks(10);
		check(this.on(s -> Seats.seat(this.me(s)) == null), "the player sat down on Dax in the pilot's seat");
		this.server.runOnServer(s -> this.me(s).stopRiding());

		// Their new lines.
		for (String key : new String[] {"dax", "kestrel", "mira"}) {
			int id = this.on(s -> this.session(s).figure(key) == null ? -1 : this.session(s).figure(key).id);

			if (id < 0) {
				this.problem("nobody called " + key + " after the chapter");
				continue;
			}

			// Spoken to where they are (the cockpit is out of reach from the lounge).
			this.server.runOnServer(s -> {
				StorySession session = this.session(s);
				session.chapter.talk(session, session.figure(key));
			});
			this.context.waitTicks(8);
			String line = this.on(s -> StoryTesting.line(this.session(s)));
			check(line.startsWith("after_"), key + " speaks " + (line.isEmpty() ? "nothing" : line) + " after the chapter, not the new lines");

			for (int i = 0; i < 12 && !this.on(s -> StoryTesting.line(this.session(s))).isEmpty(); i++) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), StoryTesting.answers(this.session(s)).isEmpty() ? -1
						: StoryTesting.answers(this.session(s)).size() - 1));
				this.context.waitTicks(4);
			}
		}

		// The cockpit with its pilots, and the HUD that says the chapter is done.
		this.server.runOnServer(s -> {
			Vec3 stand = new Vec3(44.0, FalconInterior.FLOOR + 1, 24.5);
			BlockPos falcon = this.session(s).falcon;
			this.me(s).teleportTo(s.getLevel(ShipInteriors.DIMENSION), falcon.getX() + stand.x, falcon.getY() + stand.y, falcon.getZ() + stand.z,
					java.util.Set.of(), 180.0F, 15.0F, false);
		});
		this.context.waitTicks(40);
		this.context.takeScreenshot("phase22_after_chapter_cockpit");

		// A new run starts over: nothing of the end is left.
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.RESTART, "", 0));
		this.context.waitTicks(80);
		check(this.on(s -> this.session(s) != null && !this.session(s).has(Chapter1.CHAPTER_DONE)), "a new run still knows the chapter is over");
		StoryStatePayload fresh = this.context.computeOnClient(client -> StoryClient.state());
		check(fresh != null && !"sabercraft.story.obj.chapter_done".equals(fresh.objective()), "a new run still shows the chapter as done");
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(40);
	}

	// --- Helpers ---------------------------------------------------------------------------------

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE22 CHECK FAILED: {}", problem);
		this.problems.add(problem);
	}

	private @Nullable StorySession session(MinecraftServer server) {
		ServerPlayer player = this.me(server);
		return player == null ? null : Story.sessionForTest(player).orElse(null);
	}

	private <T> T on(Function<MinecraftServer, T> what) {
		return this.server.computeOnServer(what::apply);
	}

	private @Nullable ServerPlayer me(MinecraftServer server) {
		return server.getPlayerList().getPlayer(this.self);
	}
}
