package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

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
import net.minecraft.world.phys.shapes.VoxelShape;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.client.interior.MeshBuilder;
import com.phiro.sabercraft.client.music.StoryMusic;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.saber.SaberVariant;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 25: the story's polish after Phiro's playthrough of Phase 24 (the test cases of the phase's
 * list that can be played by a test; how it feels, the camera in motion and the music's moods by
 * ear stay for the manual test, see {@code docs/phase-25-status.md}).
 *
 * <ul>
 * <li>Hale's scene after the card (1-7): it begins at once, running off changes nothing, skipped
 * the talk still begins, once only; a capture in the middle and a file loaded there play it again.</li>
 * <li>The escape out of the reactor (8-15): the way lit, the emergency release, the door bolted
 * behind one, the troopers search where one vanished, the crawl way's scene (played and skipped);
 * the same troopers after a capture.</li>
 * <li>The lab's guard (24): at the door, his back to it, lured off by a noise (as M-31's).</li>
 * <li>Nobody of the story stands in a wall where the finale is played.</li>
 * <li>Taking the saber (26, 27): the scene, the saber in hand, the reactor's tremor; skipped.</li>
 * <li>The hangar (23, 25): she and her TIE are there before its door opens; she does not fight
 * yet; the corridor's troopers hold their posts.</li>
 * <li>Her saber staff (16-21): no pose puts it through her body; the second blade, the spin, the
 * throw out and back.</li>
 * <li>The finale's music (33-36, the pieces chosen), the reactor's light, the Falcon's hatch,
 * the ramp hall's corner and the turret's collar (28-32).</li>
 * </ul>
 */
public class Phase25GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	/** Where Hale's scene leaves the player and Hale (as {@code Scenes}). */
	private static final Vec3 HALE_PLAYER = new Vec3(48.6, H, -7.6);
	private static final Vec3 HALE_STOP = new Vec3(47.1, H, -9.3);
	/** Where the lab's guard stands (as {@code Chapter1.LAB_GUARD}; Phase 27: west of the way to the door). */
	private static final Vec3 LAB_GUARD = new Vec3(41.8, R, 35.4);

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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE25 PROBLEM: {}", problem));
			throw new AssertionError("Phase 25: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		try {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.self = context.computeOnClient(client -> client.player.getUUID());

			this.staffPoses();
			this.hale();
			this.escape();
			this.escapeSkipped();
			this.escapeAgain();
			this.escapeRun(false);
			this.escapeRun(true);
			this.labGuard();
			this.saber();
			this.hangar();
			this.staff();
			this.music();
			this.light();

			if (this.on(s -> this.session(s) != null)) {
				this.leave();
			}

			this.falcon();
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE25 aborted", e);
		} finally {
			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}
		}
	}

	// --- 3 (16-18): her saber staff never goes through her ----------------------------------------------

	private void staffPoses() {
		List<String> lines = this.context.computeOnClient(client -> MeshAudit.staffInBody());
		lines.forEach(line -> SaberCraft.LOGGER.info("PHASE25 STAFF {}", line));
		check(lines.get(0).contains(", 0 with"), "her saber staff goes through her body: " + lines.get(0) + (lines.size() > 1 ? " e.g. " + lines.get(1) : ""));
	}

	// --- 1 (1-7): Hale comes in after the card ----------------------------------------------------------

	private void hale() {
		int started = this.on(s -> StoryTesting.scenesStarted());
		// A file of the moment the card was taken (the armour and the card are taken).
		this.enter(2, "m3.hale");
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 1, "Hale's scene does not begin as the card is taken");
		check(this.on(s -> StoryTesting.scene(this.session(s), "hale_in")), "no scene of Hale coming in runs");
		check("hale_in".equals(this.film(40)), "the client does not show Hale coming in");
		check(this.on(s -> this.session(s).has("qm.away")), "the quartermaster is still about when Hale comes");
		this.filmAge("hale_in", 30);
		this.context.takeScreenshot("phase25_hale_30");
		// Off and away as fast as one can (out of the armory): it changes nothing.
		this.put(new Vec3(40.5, H, -18.0), 180.0F, 0.0F);
		this.filmAge("hale_in", 80);
		this.context.takeScreenshot("phase25_hale_80");
		this.filmAge("hale_in", 110);
		this.context.takeScreenshot("phase25_hale_110");
		// He walked in during the scene (not put there at its end): through the door, up to the player.
		check(this.on(s -> this.session(s).figure("hale") != null && this.session(s).figure("hale").position().distanceTo(HALE_STOP) < 1.0),
				"Hale does not come into the armory during his scene (" + this.on(s -> String.valueOf(this.session(s).figure("hale").position())) + ")");
		this.waitLine("m3_hale.start", 80, "Hale's talk does not begin after his scene (the player ran off)");
		check(this.on(s -> this.session(s).local(Story.DESTROYER).distanceTo(HALE_PLAYER)) < 1.0, "the player is not before Hale when the talk begins");
		check(this.on(s -> this.session(s).figure("hale") != null && this.session(s).figure("hale").position().distanceTo(HALE_STOP) < 0.6),
				"Hale is not before the player when the talk begins");
		check(this.armoryBolted(), "the armory's door is not bolted behind Hale");
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase25_hale_talk");
		this.context.waitTicks(40);
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 1, "Hale's scene plays a second time");

		// Skipped: the talk begins at once, Hale in the room, the door bolted.
		int skipped = this.on(s -> StoryTesting.scenesSkipped());
		this.enter(2, "m3.hale");
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 2, "Hale's scene does not begin again from a file of that moment");
		this.film(40);
		this.context.waitTicks(10);
		this.skip("hale_in");
		check(this.on(s -> StoryTesting.scenesSkipped()) == skipped + 1, "Hale's scene is not skipped");
		this.waitLine("m3_hale.start", 10, "Hale's talk does not begin when his scene is skipped");
		check(this.on(s -> this.session(s).figure("hale") != null && this.session(s).figure("hale").position().distanceTo(HALE_STOP) < 0.6),
				"Hale is not in the room after his scene was skipped");
		check(this.armoryBolted(), "the armory's door is not bolted after Hale's scene was skipped");
		this.context.waitTicks(150);
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 2, "Hale's scene begins again after it was skipped");
		check(this.on(s -> StoryTesting.line(this.session(s))).startsWith("m3_hale."), "Hale's talk ends by itself after the skipped scene");

		// Caught in the middle of it (as by a death) in a file saved there: the scene again, once.
		this.enter(2, "m3.hale");
		this.film(40);
		this.context.waitTicks(20);
		int before = this.on(s -> StoryTesting.scenesStarted());
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));
		this.context.waitTicks(10);
		check(this.on(s -> StoryTesting.step(this.session(s))).equals("m3.hale"), "a capture in Hale's scene does not go back to the file's step");
		check(this.on(s -> StoryTesting.scenesStarted()) == before + 1, "Hale's scene does not begin once again after a capture");
		this.skip("hale_in");
		this.waitLine("m3_hale.start", 20, "Hale's talk does not begin after a capture in his scene");

		// Played on from the console (the checkpoint before the armour and the card): caught in the
		// scene, back at the console, the scene over on the client too; taken again, it plays again.
		this.enter(2, "m3.console");
		this.unseen();
		this.onTo("m3.hale");
		before = this.on(s -> StoryTesting.scenesStarted());
		check(this.on(s -> StoryTesting.scene(this.session(s), "hale_in")), "Hale's scene does not begin when the card's step is done");
		check("hale_in".equals(this.film(40)), "the client does not show Hale coming in (played on from the console)");
		this.context.waitTicks(20);
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));
		this.context.waitTicks(10);
		check(this.on(s -> StoryTesting.step(this.session(s))).equals("m3.console"), "a capture in Hale's scene does not go back to the console's checkpoint");
		check(this.on(s -> !this.session(s).has("tmp.film") && !StoryTesting.scene(this.session(s), "hale_in")), "Hale's scene goes on after a capture");
		check(!this.context.computeOnClient(client -> StoryCinema.active()), "the client still shows Hale's scene after a capture");
		this.unseen();
		this.onTo("m3.hale");
		check(this.on(s -> StoryTesting.scenesStarted()) == before + 1, "Hale's scene does not play again after the capture");
		this.skip("hale_in");
		this.waitLine("m3_hale.start", 20, "Hale's talk does not begin again after the capture");
	}

	/** On through the steps (as each step's own action would finish it) up to that one. */
	private void onTo(String step) {
		for (int i = 0; i < 5 && !this.on(s -> StoryTesting.step(this.session(s))).equals(step); i++) {
			this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
			this.context.waitTicks(5);
		}

		this.waitStep(step, 20);
	}

	private boolean armoryBolted() {
		return this.on(s -> Story.locked(this.session(s).destroyer, DestroyerInterior.CX + 4, H, -14));
	}

	// --- 2 (8-15): out of the reactor by the maintenance way --------------------------------------------

	private void escape() {
		int started = this.on(s -> StoryTesting.scenesStarted());
		int releases = this.on(s -> StoryTesting.releases());
		int bolts = this.on(s -> StoryTesting.bolts());
		this.enter(4, "m5.escape");
		List<String> hunters = this.hunters();
		SaberCraft.LOGGER.info("PHASE25 ESCAPE: at the start {}", hunters);
		long chasing = hunters.stream().filter(h -> h.contains("mode " + Figure.CHASE)).count();
		check(chasing >= 1 && chasing <= 3, "not a small group hunts the player after the download (" + chasing + " chasing)");
		check(this.on(s -> this.session(s).has("service.lit")), "the maintenance way is not lit");
		check(this.on(s -> this.session(s).figure("svc_guard") != null), "no guard walks the north platform across from the maintenance door");
		check(this.on(s -> StoryTesting.point(this.session(s), "service_release") != null), "the emergency release cannot be used");
		check(this.on(s -> Story.locked(this.session(s).destroyer, 48, R, DestroyerInterior.SERVICE_DOOR_Z)), "the maintenance door is open before its release");
		this.notInWalls("m5.escape");
		this.unseen();
		// Along the walkway to the door: the light shows the way.
		this.put(new Vec3(48.6, R, 18.6), 180.0F, 5.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase25_escape_walkway");
		// The release, pulled beside it.
		this.put(new Vec3(48.6, R, 13.0), 90.0F, 10.0F);
		this.server.runOnServer(s -> StoryTesting.release(this.session(s)));
		this.context.waitTicks(20);
		this.context.takeScreenshot("phase25_escape_release");
		this.context.waitTicks(30);
		check(this.on(s -> this.session(s).has("service.open")), "the emergency release does not open the maintenance door");
		check(this.on(s -> StoryTesting.releases()) == releases + 1, "the release was not counted as pulled once");
		check(!this.on(s -> Story.locked(this.session(s).destroyer, 48, R, DestroyerInterior.SERVICE_DOOR_Z)), "the maintenance door stays shut after its release");
		// Through, and bolted from inside.
		this.put(new Vec3(49.0, R, 8.0), 0.0F, 10.0F);
		check(this.on(s -> StoryTesting.point(this.session(s), "service_lock") != null), "the panel that bolts the door cannot be used");
		this.server.runOnServer(s -> StoryTesting.bolt(this.session(s)));
		this.context.waitTicks(4);
		check(this.on(s -> this.session(s).has("service.locked")), "the panel does not bolt the maintenance door");
		check(this.on(s -> StoryTesting.bolts()) == bolts + 1, "the door was not counted as bolted once");
		check(this.on(s -> Story.locked(this.session(s).destroyer, 48, R, DestroyerInterior.SERVICE_DOOR_Z)), "the bolted maintenance door opens");
		// They lost the player: they search where the player vanished, and the alarm goes on.
		check(this.on(s -> this.session(s).alarm > 0), "the alarm ends when the door is bolted");
		check(this.on(s -> this.session(s).searching), "they do not search when the player vanished behind the door");
		check(this.on(s -> this.session(s).huntAt != null && this.session(s).huntAt.distanceTo(new Vec3(48.9, R, 16.2)) < 0.5),
				"they do not search at the door the player vanished through");
		this.context.takeScreenshot("phase25_escape_chamber");
		// Into the crawl way: the scene (the hammering on the door, the search), then the hideout.
		this.put(new Vec3(48.5, R, 3.6), 180.0F, 0.0F);
		check("service".equals(this.film(40)), "the client does not show the crawl way's scene");
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 1, "the crawl way's scene does not begin once");

		for (int age : new int[] {40, 76, 104}) {
			this.filmAge("service", age);
			this.context.takeScreenshot("phase25_escape_scene_" + age);
		}

		this.waitStep("m5.recording", 140);
		check(this.on(s -> DestroyerInterior.HIDDEN.contains(this.session(s).local(Story.DESTROYER).add(0.0, 0.5, 0.0))), "the player is not in the hideout after the scene");
		check(this.on(s -> this.session(s).alarm > 0), "the ship forgot the intruder after the escape (no alarm)");
		List<String> after = this.hunters();
		SaberCraft.LOGGER.info("PHASE25 ESCAPE: after the scene {}", after);
		check(after.stream().anyMatch(h -> h.contains("mode " + Figure.INVESTIGATE)), "nobody searches the reactor after the escape");
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 1, "the crawl way's scene plays twice");
	}

	/** The crawl way's scene skipped (and the door not bolted by hand: it is shut behind the player). */
	private void escapeSkipped() {
		int skipped = this.on(s -> StoryTesting.scenesSkipped());
		this.enter(4, "m5.escape");
		this.unseen();
		this.flags("service.open");
		this.put(new Vec3(48.5, R, 3.6), 180.0F, 0.0F);
		check("service".equals(this.film(40)), "the client does not show the crawl way's scene (door not bolted by hand)");
		this.context.waitTicks(10);
		check(this.on(s -> this.session(s).has("service.locked")), "the door is not shut behind the player who did not bolt it");
		this.skip("service");
		check(this.on(s -> StoryTesting.scenesSkipped()) == skipped + 1, "the crawl way's scene is not skipped");
		this.waitStep("m5.recording", 20);
		check(this.on(s -> DestroyerInterior.HIDDEN.contains(this.session(s).local(Story.DESTROYER).add(0.0, 0.5, 0.0))),
				"the player is not in the hideout after the skipped scene");
	}

	/**
	 * Test case 15: the escape as it is played: no grace, the chase on from the start, the player
	 * walking the way (a little under sprinting speed) along the walkway, pulling the release, through
	 * the door, bolting it, into the crawl way. Nobody may catch the player on the way, the first
	 * time and after a capture.
	 */
	private void escapeRun(boolean again) {
		String when = again ? "after a capture" : "the first time";
		this.enter(4, "m5.escape");

		if (again) {
			this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));
			this.context.waitTicks(5);
			this.waitStep("m5.escape", 20);
		}

		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 0));
		int captures = this.on(s -> Story.capturesForTest());
		int started = this.on(s -> StoryTesting.scenesStarted());
		Vec3 start = this.on(s -> this.session(s).local(Story.DESTROYER));
		this.walk(List.of(start, new Vec3(48.6, R, 21.0), new Vec3(48.6, R, 13.0)), 0.24);
		this.server.runOnServer(s -> StoryTesting.release(this.session(s)));

		for (int i = 0; i < 60 && !this.on(s -> this.session(s).has("service.open")); i++) {
			this.context.waitTick();
		}

		boolean open = this.on(s -> this.session(s).has("service.open"));
		check(open, "the release is not pulled before the player is caught (" + when + ")");
		double nearest = this.on(s -> {
			Vec3 me = this.session(s).local(Story.DESTROYER);
			return this.session(s).figures.stream().filter(f -> Story.DESTROYER.equals(f.ship) && f.mode == Figure.CHASE)
					.mapToDouble(f -> f.position().distanceTo(me)).min().orElse(99.0);
		});
		this.walk(List.of(new Vec3(48.6, R, 13.0), new Vec3(48.6, R, 10.5), new Vec3(49.0, R, 8.0)), 0.24);
		this.server.runOnServer(s -> StoryTesting.bolt(this.session(s)));
		this.context.waitTicks(4);
		this.walk(List.of(new Vec3(49.0, R, 8.0), new Vec3(48.5, R, 6.2), new Vec3(48.5, R, 3.6)), 0.24);
		String film = this.film(40);
		int stuns = this.on(s -> this.session(s).stuns);
		int caught = this.on(s -> Story.capturesForTest()) - captures;
		SaberCraft.LOGGER.info("PHASE25 ESCAPE RUN ({}): door open {}, nearest chaser then {} blocks, stun hits {}, captures {}, scene {}", when, open,
				String.format(Locale.ROOT, "%.1f", nearest), stuns, caught, film);
		check(caught == 0, "the player is caught on the maintenance way (" + when + ", " + stuns + " stun hits)");
		check("service".equals(film) && this.on(s -> StoryTesting.scenesStarted()) == started + 1, "the crawl way's scene does not begin on a real run (" + when + ")");
		this.skip("service");
		this.waitStep("m5.recording", 40);
	}

	/** Moves the player along those points (plot space), that many blocks a tick, facing the way. */
	private void walk(List<Vec3> points, double perTick) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);

		for (int k = 1; k < points.size(); k++) {
			Vec3 a = points.get(k - 1);
			Vec3 b = points.get(k);
			int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / perTick));
			float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));

			for (int i = 1; i <= steps; i++) {
				Vec3 p = a.lerp(b, i / (double) steps);
				this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f 5.0", origin.getX() + p.x,
						origin.getY() + p.y, origin.getZ() + p.z, yaw));
				this.context.waitTick();
			}
		}
	}

	/** Test case 14: caught and back at the escape's checkpoint: the same troopers, the same help. */
	private void escapeAgain() {
		this.enter(4, "m5.escape");
		this.unseen();
		// (Out of everyone's way meanwhile: on the walkway in the passage.)
		this.put(new Vec3(48.6, R, 13.0), 180.0F, 0.0F);
		this.context.waitTicks(260);
		List<String> first = this.hunters();
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));
		this.context.waitTicks(5);
		this.waitStep("m5.escape", 20);
		this.unseen();
		this.put(new Vec3(48.6, R, 13.0), 180.0F, 0.0F);
		this.context.waitTicks(260);
		List<String> again = this.hunters();
		SaberCraft.LOGGER.info("PHASE25 ESCAPE AGAIN: first {} | after a capture {}", first, again);
		check(first.size() == again.size(), "after a capture there are " + again.size() + " troopers in the reactor, at first " + first.size());
		String prefix = StoryTesting.reinforcementPrefix();
		long help = first.stream().filter(h -> h.startsWith(prefix)).count();
		long helpAgain = again.stream().filter(h -> h.startsWith(prefix)).count();
		check(help > 0 && help == helpAgain, "the help down the lift is not the same each time (" + help + ", " + helpAgain + ")");
	}

	/** The troopers on the reactor's floor now (key, mode). */
	private List<String> hunters() {
		return this.on(s -> {
			List<String> list = new ArrayList<>();

			for (Figure f : this.session(s).figures) {
				if (Story.DESTROYER.equals(f.ship) && f.y < R + 3.0 && f.y > R - 3.0 && f.mode != Figure.DOWN && "trooper".equals(f.kind())) {
					list.add(f.key + " mode " + f.mode);
				}
			}

			return list;
		});
	}

	// --- 6 (24): the lab's guard ------------------------------------------------------------------------

	private void labGuard() {
		this.enter(5, "m6.door");
		Figure guard = this.on(s -> this.session(s).figure("lab_guard"));

		if (guard == null) {
			this.problem("no guard at the lab's door");
			return;
		}

		check(this.on(s -> this.session(s).figure("lab_guard").position().distanceTo(LAB_GUARD) < 0.4), "the lab's guard is not at his place by the door");
		this.notInWalls("m6.door");
		// (Measured by hand: meanwhile nobody may really notice the player.)
		this.unseen();
		// Phase 27: west of the way to the door, looking in turn west along the platform, over the
		// shaft and east: looking east, the way one comes and the door are behind him; looking
		// west, one coming along the platform is seen.
		this.put(new Vec3(44.5, R, 38.2), 0.0F, 0.0F);
		float atDoor = this.on(s -> StoryTesting.seesLooking(this.session(s), this.session(s).figure("lab_guard"), StoryTesting.labLookYaw(2)));
		this.put(new Vec3(37.5, R, 34.0), 270.0F, 0.0F);
		float behind = this.on(s -> StoryTesting.seesLooking(this.session(s), this.session(s).figure("lab_guard"), StoryTesting.labLookYaw(2)));
		float front = this.on(s -> StoryTesting.seesLooking(this.session(s), this.session(s).figure("lab_guard"), StoryTesting.labLookYaw(0)));
		SaberCraft.LOGGER.info("PHASE25 LAB GUARD: looking east he sees the player at the door {}, coming along the platform {}; looking west {}", atDoor, behind,
				front);
		check(atDoor == 0.0F, "the lab's guard looking east sees the player at the lab's door (" + atDoor + ")");
		check(behind == 0.0F, "the lab's guard looking east sees the player coming along the platform behind him (" + behind + ")");
		check(front > 0.0F, "the lab's guard looking west does not see the player coming along the platform");
		// Seen from the way one comes along the platform, and from the shaft.
		this.unseen();
		this.put(new Vec3(38.6, R, 33.6), 270.0F, 8.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase25_lab_guard_platform");
		this.put(new Vec3(42.0, R + 1.2, 28.5), 330.0F, 12.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase25_lab_guard_shaft");
		// M-31's noise along the platform: he goes to see (one of them, not everyone).
		int[] noise = this.on(s -> StoryTesting.noise(this.session(s), new Vec3(37.5, R, 34.0)));
		SaberCraft.LOGGER.info("PHASE25 LAB GUARD: the noise: went {}, looked {}, ignored {}", noise[0], noise[1], noise[2]);
		check(noise[0] == 1, "not one goes to see M-31's noise by the lab (" + noise[0] + ")");
		this.context.waitTicks(80);
		double off = this.on(s -> this.session(s).figure("lab_guard").position().distanceTo(LAB_GUARD));
		check(off > 2.0, "M-31's noise does not lure the lab's guard off his place (" + String.format(Locale.ROOT, "%.1f", off) + " blocks)");
	}

	// --- 7.1 (26, 27): taking the saber ------------------------------------------------------------------

	private void saber() {
		int started = this.on(s -> StoryTesting.scenesStarted());
		int rumbles = this.context.computeOnClient(client -> StoryClient.rumblesForTest());
		this.enter(5, "m6.saber");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.take();
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 1, "taking the saber does not begin its scene");
		check("saber".equals(this.film(40)), "the client does not show the saber's scene");
		this.filmAge("saber", 30);
		this.context.takeScreenshot("phase25_saber_30");
		check(!this.on(s -> this.session(s).has("saber.taken")), "the saber is in the hand before the scene shows it taken");

		// (Not right on a cut: the first frames after one may still miss what the old place hid.)
		for (int age : new int[] {70, 100, 150, 181}) {
			this.filmAge("saber", age);
			this.context.takeScreenshot("phase25_saber_" + age);
		}

		this.waitStep("m6.escape1", 80);
		check(this.on(s -> this.session(s).has("saber.taken")), "the saber is not taken after its scene");
		check(this.on(s -> SaberVariant.canHold(this.me(s).getMainHandItem())), "Ilyra's saber is not in the hand after its scene");
		check(this.context.computeOnClient(client -> StoryClient.rumblesForTest()) > rumbles, "the ship does not tremble as the saber is taken");
		check(this.on(s -> this.session(s).fighting), "the fight does not begin after the saber's scene");

		// Skipped: on at once, the saber in hand; the scene not again.
		int skipped = this.on(s -> StoryTesting.scenesSkipped());
		this.enter(5, "m6.saber");
		this.take();
		this.film(40);
		this.context.waitTicks(10);
		this.skip("saber");
		check(this.on(s -> StoryTesting.scenesSkipped()) == skipped + 1, "the saber's scene is not skipped");
		this.waitStep("m6.escape1", 20);
		check(this.on(s -> SaberVariant.canHold(this.me(s).getMainHandItem())), "Ilyra's saber is not in the hand after the skipped scene");
		this.context.waitTicks(40);
		check(this.on(s -> StoryTesting.scenesStarted()) == started + 2, "the saber's scene plays again after it was skipped");
		this.notInWalls("m6.escape1");
	}

	/** "Take it" in the saber's talk (as the player answers). */
	private void take() {
		this.server.runOnServer(s -> this.session(s).talk("m6_saber", null));
		this.context.waitTicks(5);
		List<String> answers = this.on(s -> StoryTesting.answers(this.session(s)));

		if (!answers.contains("take")) {
			this.problem("the saber's talk has no answer to take it (" + answers + ")");
			return;
		}

		this.server.runOnServer(s -> StoryTesting.answer(this.session(s), answers.indexOf("take")));
		this.context.waitTicks(2);
	}

	// --- 4, 5 (23, 25): the corridor and the hangar ------------------------------------------------------

	private void hangar() {
		this.enter(5, "m6.escape2");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		// She is in the hangar already, and her TIE (the client knows it before the door opens).
		Figure shadow = this.on(s -> this.session(s).figure("shadow"));
		check(shadow != null && shadow.position().distanceTo(new Vec3(40.5, H, -28.5)) < 0.6, "the Shadowblade is not in the hangar before its door opens");
		check(this.on(s -> this.session(s).has("tie.parked")), "her TIE is not in the hangar before its door opens");
		this.context.waitTicks(5);
		check(this.context.computeOnClient(client -> StoryClient.has("tie.parked")), "the client does not draw her TIE before the hangar's door opens");
		// The corridor's troopers, the lobby's and the door's: at their posts.
		List<String> posts = List.of("h4", "h4b", "h5", "h6");
		List<Vec3> at = this.on(s -> posts.stream().map(key -> this.session(s).figure(key)).filter(f -> f != null).map(Figure::position).toList());
		SaberCraft.LOGGER.info("PHASE25 HANGAR: the troopers on the way {}", at);
		check(at.size() >= 3 && at.size() <= 4, "not three or four troopers on the way from the reactor to the hangar (" + at.size() + ")");
		check(this.on(s -> this.session(s).figure("h5") != null && this.session(s).figure("h5").post && this.session(s).figure("h6") != null
				&& this.session(s).figure("h6").post), "the lobby's and the hangar door's troopers do not hold posts");
		this.notInWalls("m6.escape2");
		// Nobody comes while the player is far (at the lift); she does not move.
		this.context.waitTicks(60);
		List<Vec3> later = this.on(s -> posts.stream().map(key -> this.session(s).figure(key)).filter(f -> f != null).map(Figure::position).toList());
		boolean held = at.size() == later.size();

		for (int i = 0; held && i < at.size(); i++) {
			held = at.get(i).distanceTo(later.get(i)) < 1.5;
		}

		check(held, "the troopers on the way leave their posts unprovoked (" + at + " -> " + later + ")");
		// Before the hangar's door (behind its guard): it opens on her and her TIE.
		this.server.runOnServer(s -> {
			for (String key : posts) {
				Figure f = this.session(s).figure(key);

				if (f != null) {
					f.mode = Figure.DOWN;
				}
			}
		});
		this.put(new Vec3(40.5, H, -14.2), 180.0F, 0.0F);
		this.context.waitTicks(30);
		this.context.takeScreenshot("phase25_hangar_door");
		this.context.waitTicks(60);
		check(this.on(s -> this.session(s).figure("shadow") != null && this.session(s).figure("shadow").position().distanceTo(new Vec3(40.5, H, -28.5)) < 0.6
				&& !this.session(s).figure("shadow").hostile && !this.session(s).has("tmp.duel")), "she comes out to fight before the duel");
		check(this.on(s -> StoryTesting.step(this.session(s))).equals("m6.escape2"), "the duel begins before the player is in the hangar");
	}

	// --- 3 (16-22): her saber staff in the second phase ---------------------------------------------------

	private void staff() {
		this.enter(5, "m6.duel");
		this.flags("saber.taken");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped)");
			return;
		}

		// The first phase: one blade, no staff, no throw.
		check(!this.context.computeOnClient(client -> StoryClient.twinBlade()), "her second blade is out in the first phase");
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		check("boss_mid".equals(this.film(20)), "the blade lock's scene does not begin at half her strength");
		this.context.waitTicks(10);
		this.skip("boss_mid");
		check(this.on(s -> StoryTesting.phase(this.session(s).figure("shadow")) == 2), "the second phase does not begin");
		// The second blade comes out (pictures of the moment, from where the player stands).
		this.facing();

		for (int i = 0; i < 4; i++) {
			this.context.takeScreenshot("phase25_twin_" + i);
			this.context.waitTicks(4);
		}

		check(this.context.computeOnClient(client -> StoryClient.twinBlade()), "her second blade does not come out in the second phase");

		// The spin: both blades round her.
		int[] counts = this.on(s -> StoryTesting.throwsCount());
		this.server.runOnServer(s -> StoryTesting.spinNow(this.session(s), this.session(s).figure("shadow")));
		check(this.on(s -> StoryTesting.throwsCount())[4] == counts[4] + 1, "she does not spin");
		this.context.waitTicks(8);
		this.facing();
		this.context.takeScreenshot("phase25_spin_windup");
		this.context.waitTicks(14);
		this.context.takeScreenshot("phase25_spin");
		this.context.waitTicks(30);

		// The throw: drawn back, out at the player, back to her hand.
		this.server.runOnServer(s -> {
			Figure f = this.session(s).figure("shadow");
			Vec3 her = f.position();
			Vec3 me = this.session(s).local(Story.DESTROYER);
			Vec3 away = new Vec3(me.x - her.x, 0.0, me.z - her.z);
			away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			Vec3 to = her.add(away.scale(6.5));
			ServerPlayer player = this.me(s);
			Vec3 world = to.add(Vec3.atLowerCornerOf(this.session(s).destroyer));
			player.teleportTo(s.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, java.util.Set.of(),
					(float) Math.toDegrees(Math.atan2(away.x, -away.z)), 0.0F, true);
			StoryTesting.throwNow(this.session(s), f);
		});
		check(this.on(s -> StoryTesting.throwsCount())[0] == counts[0] + 1, "she does not throw her staff");
		int seen = this.context.computeOnClient(client -> StoryClient.throwsSeenForTest());
		this.context.waitTicks(10);
		this.facing();
		this.context.takeScreenshot("phase25_throw_windup");
		boolean out = false;

		for (int i = 0; i < 30 && !out; i++) {
			this.context.waitTick();
			out = this.on(s -> StoryTesting.staffOut(this.session(s).figure("shadow")));
		}

		check(out, "her staff does not leave her hand");
		this.context.waitTicks(2);
		check(this.context.computeOnClient(client -> StoryClient.throwsSeenForTest()) > seen, "the client does not see the throw");
		check(this.context.computeOnClient(client -> StoryClient.staffFlyingForTest()), "the client does not draw her staff in flight");
		this.context.takeScreenshot("phase25_throw_out");
		this.context.waitTicks(8);
		this.context.takeScreenshot("phase25_throw_far");
		boolean back = false;

		for (int i = 0; i < 60 && !back; i++) {
			this.context.waitTick();
			back = !this.on(s -> StoryTesting.staffOut(this.session(s).figure("shadow")));
		}

		check(back, "her staff does not come back to her hand");
		this.context.waitTicks(8);
		check(!this.context.computeOnClient(client -> StoryClient.staffFlyingForTest()), "the client still draws her staff in flight after it came back");
		this.context.takeScreenshot("phase25_throw_caught");
		int[] after = this.on(s -> StoryTesting.throwsCount());
		SaberCraft.LOGGER.info("PHASE25 THROW: begun {}, hits {}, blocked {}, dodged {}, spins {}", after[0], after[1], after[2], after[3], after[4]);
		check(after[1] + after[2] + after[3] > counts[1] + counts[2] + counts[3], "the thrown staff did not reach the player standing in its way");
		check(this.on(s -> this.session(s).timers.getOrDefault("throw", 0) > 0), "she can throw again at once");
	}

	/** Turns the player to her. */
	private void facing() {
		this.server.runOnServer(s -> {
			Figure f = this.session(s).figure("shadow");
			ServerPlayer player = this.me(s);

			if (f != null && player != null) {
				Vec3 eye = player.getEyePosition();
				Vec3 her = f.position().add(Vec3.atLowerCornerOf(this.session(s).destroyer)).add(0.0, 1.2, 0.0);
				Vec3 d = her.subtract(eye);
				float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
				player.teleportTo(s.getLevel(ShipInteriors.DIMENSION), player.getX(), player.getY(), player.getZ(), java.util.Set.of(), yaw, pitch, true);
			}
		});
		this.context.waitTicks(2);
	}

	// --- 9 (33-36): the finale's music ----------------------------------------------------------------------

	private void music() {
		check(this.context.computeOnClient(client -> client.getSoundManager().getSoundEvent(SaberCraft.id("music.story_triumph")) != null
				&& client.getSoundManager().getSoundEvent(SaberCraft.id("music.story_launch")) != null), "the finale's new pieces are not in sounds.json");
		this.enter(5, "m6.falcon");
		this.music("music.story_triumph", "back to the Falcon after the duel");
		this.enter(5, "m6.jump");
		this.music("music.story_launch", "aboard the Falcon before the jump");
	}

	private void music(String piece, String where) {
		String playing = null;

		for (int i = 0; i < 16; i++) {
			playing = this.context.computeOnClient(client -> StoryMusic.playing());

			if (piece.equals(playing)) {
				return;
			}

			this.context.waitTicks(10);
		}

		this.problem("the music " + where + " is " + playing + ", not " + piece);
	}

	// --- 10 (38): the reactor's light ----------------------------------------------------------------------

	private void light() {
		float y = R + 0.012F;
		List<Vector3f> points = List.of(new Vector3f(48.5F, y, 24.5F), new Vector3f(40.5F, y, 34.5F), new Vector3f(40.5F, H + 0.012F, -30.0F));
		List<MeshAudit.Near> near = this.context.computeOnClient(client -> MeshAudit.near("star_destroyer", points, 0.03F, true));
		check(near.stream().anyMatch(n -> n.point() == 0 && n.layer() == MeshBuilder.Layer.LIGHT), "no light of the core on the east platform");
		check(near.stream().anyMatch(n -> n.point() == 1 && n.layer() == MeshBuilder.Layer.LIGHT), "no light of the core on the south platform");
		check(near.stream().noneMatch(n -> n.point() == 2 && n.layer() == MeshBuilder.Layer.LIGHT), "the core's light reaches the hangar");
		// On the shaft's wall at the working level, somewhere round it (not in the doorways).
		List<Vector3f> wall = new ArrayList<>();

		for (int a = 0; a < 360; a += 30) {
			double r = DestroyerInterior.REACTOR_WALL - 0.03;
			wall.add(new Vector3f((float) (DestroyerInterior.REACTOR_X + r * Math.cos(Math.toRadians(a))), R + 1.5F,
					(float) (DestroyerInterior.REACTOR_Z + r * Math.sin(Math.toRadians(a)))));
		}

		List<MeshAudit.Near> lit = this.context.computeOnClient(client -> MeshAudit.near("star_destroyer", wall, 0.08F, true));
		long places = lit.stream().filter(n -> n.layer() == MeshBuilder.Layer.LIGHT).map(MeshAudit.Near::point).distinct().count();
		check(places >= 6, "the core's light is on the shaft's wall at only " + places + " of 12 places round it");
		// Pictures: the shaft, the lab (the saber's field, the tracker), the hologram.
		this.enter(3, "m4.download");
		this.unseen();
		this.put(new Vec3(48.0, R, 24.5), 90.0F, 10.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase25_light_reactor");
		this.put(new Vec3(48.0, R, 24.5), 90.0F, 42.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase25_light_floor");
		this.put(new Vec3(40.5, R, 33.0), 180.0F, -20.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase25_light_reactor_up");
		this.enter(5, "m6.saber");
		this.unseen();
		this.put(new Vec3(47.5, R, 43.0), 30.0F, 25.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase25_light_lab");
	}

	// --- 8 (28-32): the Falcon -------------------------------------------------------------------------------

	private void falcon() {
		BlockPos falcon = this.on(s -> ShipInteriors.plotForTest(s, this.self, "falcon"));
		Phase16GameTest.tp(this.context, this.server, falcon, InteriorPlan.of("falcon").entry(), 0, 0);
		this.context.waitTicks(40);
		// The hatch at the ramp's foot, from the ramp: every look at it meets it (no slit to the stars).
		Vector3f eye = new Vector3f(35.5F, 5.4F, 38.5F);
		int leaks = 0;

		for (float hx = 34.15F; hx <= 36.9F; hx += 0.45F) {
			for (float hy = 3.1F; hy <= 5.95F; hy += 0.35F) {
				float x = hx;
				float y = hy;
				Vector3f d = new Vector3f(x, y, 42.1F).sub(eye);
				List<String> lines = this.context.computeOnClient(client -> MeshAudit.probe("falcon", (bx, by, bz) -> client.level.getBlockState(falcon.offset(bx, by, bz)),
						eye, d, 8.0F));

				if (!lines.contains("leak: null")) {
					leaks++;
					lines.forEach(line -> SaberCraft.LOGGER.info("PHASE25 HATCH {},{}: {}", x, y, line));
				}
			}
		}

		check(leaks == 0, leaks + " looks at the Falcon's hatch pass through it");
		// The ramp hall's corners by the hatch: nothing slanted pokes into them (the engine grille's lip did).
		List<Vector3f> corners = List.of(new Vector3f(37.9F, 6.05F, 41.8F), new Vector3f(33.1F, 6.05F, 41.8F), new Vector3f(37.9F, 6.6F, 41.9F));
		List<MeshAudit.Near> near = this.context.computeOnClient(client -> MeshAudit.near("falcon", corners, 0.35F, true));
		List<MeshAudit.Near> slanted = near.stream().filter(n -> Math.abs(n.normal().y) > 0.2F && Math.abs(n.normal().y) < 0.95F).toList();
		slanted.forEach(n -> SaberCraft.LOGGER.info("PHASE25 CORNER {}", n));
		check(slanted.isEmpty(), slanted.size() + " slanted surfaces poke into the ramp hall's corners by the hatch");
		// The turret's collar: looks out over it from the gun well meet it (or the dome's glass).
		int over = 0;

		for (float y : new float[] {12.4F, 12.9F, 13.4F}) {
			for (int turn = 0; turn < 360; turn += 30) {
				int a = turn;
				float c = (float) Math.cos(Math.toRadians(a));
				float s = (float) Math.sin(Math.toRadians(a));
				Vector3f o = new Vector3f(20.5F + 2.0F * c, y, 30.5F + 2.0F * s);
				List<String> lines = this.context.computeOnClient(client -> MeshAudit.probe("falcon", (bx, by, bz) -> client.level.getBlockState(falcon.offset(bx, by, bz)),
						o, new Vector3f(c, 0.08F, s), 10.0F));

				if (!lines.contains("leak: null")) {
					over++;
					lines.forEach(line -> SaberCraft.LOGGER.info("PHASE25 COLLAR {} {}: {}", y, a, line));
				}
			}
		}

		check(over == 0, over + " looks out of the turret's well pass over its collar");
		// Pictures: the hatch from the ramp, the corner, the well.
		Phase16GameTest.tp(this.context, this.server, falcon, new Vec3(35.5, 3.8, 38.5), 0.0F, 5.0F);
		this.context.waitTicks(20);
		this.context.takeScreenshot("phase25_falcon_hatch");
		Phase16GameTest.tp(this.context, this.server, falcon, new Vec3(35.0, 4.2, 39.5), -45.0F, -20.0F);
		this.context.takeScreenshot("phase25_falcon_hatch_corner");
		Phase16GameTest.tp(this.context, this.server, falcon, new Vec3(21.5, 12.0, 30.5), 90.0F, 12.0F);
		this.context.takeScreenshot("phase25_falcon_turret");
	}

	// --- Nobody in a wall --------------------------------------------------------------------------------------

	/** No one of the story stands in a wall or a solid model (what one cannot see, or sees half). */
	private void notInWalls(String where) {
		List<String> in = this.on(s -> {
			StorySession session = this.session(s);
			ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			BlockPos o = session.destroyer;
			List<String> list = new ArrayList<>();

			for (Figure f : session.figures) {
				if (!Story.DESTROYER.equals(f.ship) || f.mode == Figure.DOWN || f.seated()) {
					continue;
				}

				AABB box = new AABB(f.x - 0.2, f.y + 0.3, f.z - 0.2, f.x + 0.2, f.y + 1.5, f.z + 0.2).move(o.getX(), o.getY(), o.getZ());

				for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
					VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);

					if (!shape.isEmpty() && shape.bounds().move(p).intersects(box)) {
						list.add(String.format(Locale.ROOT, "%s (%s) at %.1f,%.1f,%.1f", f.key, f.kind(), f.x, f.y, f.z));
						break;
					}
				}
			}

			return list;
		});
		SaberCraft.LOGGER.info("PHASE25 WALLS {}: {}", where, in);
		check(in.isEmpty(), "at " + where + " someone stands in a wall: " + in);
	}

	// --- Helpers ------------------------------------------------------------------------------------------------

	/** Continues the story at that step (a file saved there), as soon as it is there. */
	private void enter(int mission, String step) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.fileAt(s, this.self, mission, step));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));

		for (int i = 0; i < 160; i++) {
			this.context.waitTick();

			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				this.context.waitTicks(2);
				return;
			}
		}

		throw new AssertionError("the story does not continue at " + step);
	}

	private void leave() {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(30);
		check(this.on(s -> !Story.playing(this.me(s))), "still in the story after leaving");
	}

	private void waitStep(String step, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				return;
			}

			this.context.waitTick();
		}

		this.problem("the story is not at " + step + " (at " + this.on(s -> this.session(s) == null ? "-" : StoryTesting.step(this.session(s))) + ")");
	}

	/** Waits for that line of a talk (at most that many ticks). */
	private void waitLine(String line, int ticks, String failure) {
		String now = "";

		for (int i = 0; i < ticks; i++) {
			now = this.on(s -> this.session(s) == null ? "" : StoryTesting.line(this.session(s)));

			if (line.equals(now)) {
				return;
			}

			this.context.waitTick();
		}

		this.problem(failure + " (line '" + now + "')");
	}

	/** Nobody sees the player (the test goes where it likes). */
	private void unseen() {
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 100000));
	}

	private void flags(String... flags) {
		this.server.runOnServer(s -> {
			for (String flag : flags) {
				this.session(s).set(flag);
			}
		});
	}

	/** The film the client plays (waiting up to that many ticks for one to begin). */
	private @Nullable String film(int ticks) {
		for (int i = 0; i < ticks; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.active() ? StoryCinema.filmForTest() : null);

			if (film != null) {
				return film;
			}

			this.context.waitTick();
		}

		return null;
	}

	/** Waits until the client's film of that name is that far (ticks), or over. */
	private void filmAge(String name, int age) {
		for (int i = 0; i < 240; i++) {
			if (this.context.computeOnClient(client -> !StoryCinema.active() || !name.equals(StoryCinema.filmForTest()) || StoryCinema.ageForTest() >= age)) {
				return;
			}

			this.context.waitTick();
		}
	}

	/** Skips that film as the player does (the key: the server is told, the client stops it). */
	private void skip(String film) {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(5);
	}

	/** Skips every film the story plays until the duel begins, at most that many ticks. */
	private void skipFilms(int ticks) {
		for (int i = 0; i < ticks / 5; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.active() ? StoryCinema.filmForTest() : null);

			if (film != null) {
				this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
				this.context.runOnClient(client -> StoryCinema.skipForTest());
			} else if (this.on(s -> this.session(s).has("tmp.duel"))) {
				return;
			} else if (this.on(s -> this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			}

			this.context.waitTicks(5);
		}
	}

	/** Puts the player at a place of the Star Destroyer of the story (plot space). */
	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE25 CHECK FAILED: {}", problem);
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
