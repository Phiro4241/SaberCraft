package com.phiro.sabercraft.gametest;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.story.FilmCamera;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.client.story.StoryHud;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Mission;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StoryDifficulty;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 29: the boss fight harder and fairer, the film cameras clean, after Phiro's playtest. The
 * parts a test can play in the real game (the feel stays for the manual test list in
 * {@code docs/phase-29-status.md}):
 *
 * <ol>
 * <li>Under the crosshair the cover and the toolbox show their words, never a key (2).</li>
 * <li>The doors: the patrols open none they do not walk through (the corridor's patrol turning
 * before the hangar's door); in the duel the doors by the arena stay shut while the two fight
 * beside them, and still open for the player walking through (6).</li>
 * <li>The film cameras: the saber's scene in the lab (never in the player, the figure always
 * drawn), Hale's and the crawl way's scenes, the takedowns of the detention block's guard and the
 * lab's guard, the duel's films and the scene at half her strength by the wall: never inside a
 * wall, nothing solid between the camera and what it shows, never in a person, no jumps (3, 4).</li>
 * <li>Her back: a stroke from behind lands once, the next soon after is turned aside with a blow
 * back; jumping behind her over and over does not pay; standing behind her she turns (not in one
 * snap) or answers; standing in her one is pushed out (5.1).</li>
 * <li>The guard: holding it drains stamina, it gives way by itself and stays down a moment, the
 * player worn out; stamina comes back only after a while and slower; blows in a row cost more
 * each; a guard raised in time still parries perfectly, one raised again at once does not (5.2).</li>
 * <li>Her attacks: the slam (telegraphed, hits, dodged by a step aside or a jump, costly on the
 * guard), the shove, the throw high and low by turns, how often and how varied she attacks in
 * both phases (5.3, 5.4).</li>
 * <li>The scene at half her strength: with the player in the air it waits until they land, or
 * sets them down; from her side, behind her, by a wall: the two set up facing each other (5.5).</li>
 * </ol>
 */
public class Phase29GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	private static final String SABER = "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:blue\"]}]";

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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE29 PROBLEM: {}", problem));
			throw new AssertionError("Phase 29: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		try {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode survival @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());

			this.part("hover", this::hover);
			this.part("patrolDoors", this::patrolDoors);
			this.part("fightDoors", this::fightDoors);
			this.part("saberCamera", this::saberCamera);
			this.part("sceneCameras", this::sceneCameras);
			this.part("takedownCamera", this::takedownCamera);
			this.part("rear", this::rear);
			this.part("guard", this::guardRules);
			this.part("slam", this::slam);
			this.part("pressure", this::pressure);
			this.part("sceneInAir", this::sceneInAir);
			this.part("sceneSides", this::sceneSides);
			this.part("defeatCamera", this::defeatCamera);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE29 aborted", e);
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
			context.getInput().releaseMouse(0);
			context.runOnClient(client -> FilmCamera.auditForTest(false));

			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE29_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE29_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			SaberCraft.LOGGER.info("PHASE29 part {}", name);
			int before = this.problems.size();
			long start = System.currentTimeMillis();
			part.run();
			SaberCraft.LOGGER.info("PHASE29 part {} done in {} s, {} problem(s)", name, (System.currentTimeMillis() - start) / 1000, this.problems.size() - before);
		}
	}

	// --- 1 (2): the words under the crosshair -----------------------------------------------------------------

	private void hover() {
		String de = this.context.computeOnClient(client -> read(client, "lang/de_de.json"));
		String en = this.context.computeOnClient(client -> read(client, "lang/en_us.json"));
		Set<String> ids = StoryTesting.pointIds();
		List<String> missing = new ArrayList<>();

		for (String id : ids) {
			String key = "\"" + Mission.Point.LABEL_PREFIX + id + "\"";

			if (!de.contains(key)) {
				missing.add("de " + id);
			}

			if (!en.contains(key)) {
				missing.add("en " + id);
			}
		}

		SaberCraft.LOGGER.info("PHASE29 HOVER: {} things to use declared", ids.size());
		check(ids.contains("vent_cover") && ids.contains("toolbox"), "the chapter does not declare the cover and the toolbox among its things to use (" + ids + ")");
		check(missing.isEmpty(), "things to use without their words: " + missing);
		String generic = "\"" + Mission.Point.LABEL_PREFIX + "generic\"";
		check(de.contains(generic) && en.contains(generic), "no plain words for a thing to use whose own words are missing");

		this.enter(1, "m2.drop");
		this.unseen();
		this.server.runOnServer(s -> StoryTesting.ventCover(this.session(s)));
		int wordless = this.context.computeOnClient(client -> StoryHud.wordlessForTest());
		// The cover, from the hangar's side (where Phiro saw the key).
		this.put(new Vec3(12.6, H, -28.5), 90.0F, 5.0F);
		this.context.waitTicks(8);
		this.prompt("vent_cover");
		// The toolbox by the containers.
		Vec3 box = this.on(s -> {
			Vec3 world = StoryTesting.point(this.session(s), "toolbox");
			return world == null ? null : world.subtract(Vec3.atLowerCornerOf(this.session(s).destroyer));
		});

		if (box == null) {
			this.problem("the toolbox cannot be used at the start of Mission 2");
		} else {
			Vec3 stand = this.standBy(box);

			if (stand == null) {
				this.problem("no place to stand by the toolbox");
			} else {
				Vec3 eye = stand.add(0.0, 1.62, 0.0);
				this.put(stand, yaw(eye, box), pitch(eye, box));
				this.context.waitTicks(8);
				this.prompt("toolbox");
			}
		}

		check(this.context.computeOnClient(client -> StoryHud.wordlessForTest()) == wordless, "a thing to use showed the plain words (its own were missing)");
		Set<String> undeclared = StoryTesting.undeclaredPoints();
		check(undeclared.isEmpty(), "things used in the story that were never declared (their words may be missing): " + undeclared);
	}

	/** What the crosshair shows at a thing to use: its words, never a key. */
	private void prompt(String id) {
		String text = this.context.computeOnClient(StoryHud::promptForTest);
		String words = this.context.computeOnClient(client -> net.minecraft.locale.Language.getInstance().getOrDefault(Mission.Point.LABEL_PREFIX + id));
		SaberCraft.LOGGER.info("PHASE29 HOVER {}: '{}'", id, text);

		if (text == null) {
			this.problem("nothing to use is shown under the crosshair at the " + id);
			return;
		}

		check(!text.contains("sabercraft.") && !text.contains("story.point"), "the crosshair shows an internal key at the " + id + ": " + text);
		check(!words.startsWith("sabercraft.") && text.contains(words), "the crosshair does not show the words of the " + id + " ('" + text + "', wanted '" + words + "')");
	}

	/** A free place beside a thing (south, east, west, north of it) with floor under it. */
	private @Nullable Vec3 standBy(Vec3 point) {
		for (Vec3 offset : List.of(new Vec3(0.0, 0.0, 1.4), new Vec3(1.4, 0.0, 0.0), new Vec3(-1.4, 0.0, 0.0), new Vec3(0.0, 0.0, -1.4))) {
			Vec3 at = new Vec3(point.x + offset.x, H, point.z + offset.z);

			if (this.on(s -> this.free(s, at))) {
				return at;
			}
		}

		return null;
	}

	// --- 2 (6): the doors ----------------------------------------------------------------------------

	private void patrolDoors() {
		this.watchDoors(1, "m2.drop");
		this.watchDoors(4, "m5.r2");
	}

	/**
	 * Half a minute with the player far from every door: each door that opens must lie on the way of
	 * one of the story's people walking a round (a patrol turning before a door opened it).
	 */
	private void watchDoors(int mission, String step) {
		this.enter(mission, step);
		this.unseen();
		Vec3 far = null;

		for (Vec3 spot : List.of(new Vec3(30.5, H, -52.5), new Vec3(48.5, H, -52.5), new Vec3(30.5, H, -45.5), new Vec3(48.5, H, -45.5))) {
			if (this.on(s -> this.free(s, spot))) {
				far = spot;
				break;
			}
		}

		if (far == null) {
			this.problem("no free place in the hangar's south for the door watch");
			return;
		}

		this.put(far, 0.0F, 0.0F);
		List<InteriorPlan.Door> doors = DestroyerInterior.DOORS;
		boolean[] was = this.doorStates();
		int[] opens = new int[doors.size()];
		List<String> wrong = new ArrayList<>();
		// The corridor's patrol (from the hangar's door to the lobby and back): how far he came each way.
		double[] corridor = {Double.MAX_VALUE, -Double.MAX_VALUE};

		for (int t = 0; t < 640; t += 2) {
			this.context.waitTicks(2);
			boolean[] now = this.doorStates();

			for (int i = 0; i < doors.size(); i++) {
				if (now[i] && !was[i]) {
					opens[i]++;
					int index = i;
					String why = this.on(s -> this.opener(s, doors.get(index)));

					if (why != null) {
						wrong.add(why);
					}
				}
			}

			was = now;
			double[] z = this.on(s -> {
				for (Figure f : this.session(s).figures) {
					if (f.crew != null && f.mode == Figure.ROUTINE && f.crew.path().size() == 2 && Math.abs(f.crew.path().get(0).x - (DestroyerInterior.CX - 1.5)) < 0.1
							&& Math.abs(f.crew.path().get(0).y - H) < 0.1) {
						return new double[] {f.z};
					}
				}

				return null;
			});

			if (z != null) {
				corridor[0] = Math.min(corridor[0], z[0]);
				corridor[1] = Math.max(corridor[1], z[0]);
			}
		}

		StringBuilder opened = new StringBuilder();

		for (int i = 0; i < doors.size(); i++) {
			if (opens[i] > 0) {
				InteriorPlan.Door door = doors.get(i);
				opened.append(String.format(Locale.ROOT, "(%d,%d %dx) ", door.x0(), door.z0(), opens[i]));
			}
		}

		SaberCraft.LOGGER.info("PHASE29 PATROL DOORS at {}: opened {}; the corridor's patrol walked z {} to {}", step, opened.length() == 0 ? "none" : opened,
				String.format(Locale.ROOT, "%.1f", corridor[0]), String.format(Locale.ROOT, "%.1f", corridor[1]));
		check(wrong.isEmpty(), "at " + step + " doors open for people whose way does not go through them: " + wrong);

		if (mission == 1) {
			check(corridor[0] < DestroyerInterior.HANGAR_Z1 + 3.2, "the corridor's patrol does not come to the end of his round by the hangar (to z " + corridor[0] + ")");
		}
	}

	/** Whether each door of the Star Destroyer of the story is open now. */
	private boolean[] doorStates() {
		return this.on(s -> {
			BlockPos origin = this.session(s).destroyer;
			List<InteriorPlan.Door> doors = DestroyerInterior.DOORS;
			boolean[] open = new boolean[doors.size()];

			for (int i = 0; i < doors.size(); i++) {
				open[i] = InteriorSystems.doorOpenForTest(this.level(s), origin, doors.get(i));
			}

			return open;
		});
	}

	/**
	 * A door just opened with the player far away: null when one of the story's people walking a
	 * round has it on their way (or stands in it), else who was nearest and what they did.
	 */
	private @Nullable String opener(MinecraftServer s, InteriorPlan.Door door) {
		StorySession session = this.session(s);
		Vec3 middle = new Vec3((door.x0() + door.x1() + 1) / 2.0, door.y0(), (door.z0() + door.z1() + 1) / 2.0);
		Figure nearest = null;

		for (Figure f : session.figures) {
			if (!Story.DESTROYER.equals(f.ship) || f.mode == Figure.DOWN) {
				continue;
			}

			if (crosses(f.crew != null ? f.crew.path() : List.of(), door) || f.mode != Figure.ROUTINE && !f.path.isEmpty() && crosses(f.path, door)) {
				return null;
			}

			if (nearest == null || f.position().distanceTo(middle) < nearest.position().distanceTo(middle)) {
				nearest = f;
			}
		}

		Vec3 me = session.local(Story.DESTROYER);

		if (me.distanceTo(middle) < 4.0) {
			return null;
		}

		return String.format(Locale.ROOT, "door at (%d, %d, %d) opened, nearest %s %.1f away (mode %d) at %s", door.x0(), door.y0(), door.z0(),
				nearest == null ? "nobody" : nearest.key, nearest == null ? 0.0 : nearest.position().distanceTo(middle), nearest == null ? -1 : nearest.mode,
				nearest == null ? "-" : nearest.position());
	}

	/** Whether a closed round (plot space) goes through a door's opening. */
	private static boolean crosses(List<Vec3> route, InteriorPlan.Door door) {
		int n = route.size();

		if (n < 2) {
			return false;
		}

		double lo = (door.alongX() ? door.z0() : door.x0()) - 0.3;
		double hi = (door.alongX() ? door.z1() : door.x1()) + 1.3;

		for (int i = 0; i < n; i++) {
			Vec3 a = route.get(i);
			Vec3 b = route.get((i + 1) % n);
			double da = (door.alongX() ? a.x : a.z) - door.plane();
			double db = (door.alongX() ? b.x : b.z) - door.plane();

			if (da == db || (da <= 0.0) == (db <= 0.0)) {
				continue;
			}

			Vec3 c = a.lerp(b, da / (da - db));
			double side = door.alongX() ? c.z : c.x;

			if (side >= lo && side <= hi && c.y >= door.y0() - 1.6 && c.y <= door.y1() + 1.0) {
				return true;
			}
		}

		return false;
	}

	private void fightDoors() {
		// The rules first (a door in a wall at z 10, its plane at 10.5, three blocks wide), in a story fight.
		InteriorPlan.Door wall = InteriorPlan.Door.wall(false, 10, 0, 2, 0, 2);
		List<String> wrong = new ArrayList<>();
		this.fightDoor(wrong, "a player stepping aside before it", wall, new Vec3(1.8, 0.0, 11.8), new Vec3(1.0, 0.0, 11.8), new Vec3(0.4, 0.0, 11.9), false);
		this.fightDoor(wrong, "a player dodging back and forth before it", wall, new Vec3(1.5, 0.0, 11.7), new Vec3(1.5, 0.0, 12.3), new Vec3(1.5, 0.0, 11.8), false);
		this.fightDoor(wrong, "a player stepping once towards it from afar", wall, new Vec3(1.5, 0.0, 12.4), new Vec3(1.5, 0.0, 12.8), new Vec3(1.5, 0.0, 12.8), false);
		this.fightDoor(wrong, "a player running at it", wall, new Vec3(1.5, 0.0, 11.9), new Vec3(1.5, 0.0, 12.5), new Vec3(1.5, 0.0, 13.1), true);
		this.fightDoor(wrong, "a player in the doorway", wall, new Vec3(1.5, 0.0, 10.9), new Vec3(1.5, 0.0, 11.4), new Vec3(1.5, 0.0, 11.9), true);
		this.fightDoor(wrong, "a player put right before it", wall, new Vec3(1.5, 0.0, 11.6), null, null, false);
		check(wrong.isEmpty(), "in a story fight doors decide wrongly: " + wrong);
		// A door never shuts on one of the story's people still in its opening (through, their way on
		// beyond it); a shut one does not open for one passing that near without going through.
		List<String> held = new ArrayList<>();
		List<Vec3> beyond = List.of(new Vec3(1.5, 0.0, 7.0));
		List<Vec3> along = List.of(new Vec3(6.0, 0.0, 11.25));

		if (!InteriorSystems.wantedForTest(wall, false, new Vec3(1.5, 0.0, 9.75), new Vec3(1.5, 0.0, 10.1), null, beyond, true, false)) {
			held.add("an open door shuts on one of the story's people still in it (0.75 past its plane)");
		}

		if (InteriorSystems.wantedForTest(wall, false, new Vec3(1.5, 0.0, 11.25), new Vec3(0.5, 0.0, 11.25), null, along, false, false)) {
			held.add("a shut door opens for one of the story's people walking past 0.75 from it");
		}

		// Right after it shut: a player walking up to it (two looks in a row) opens it without being
		// stopped short; one stepping once towards it from afar does not.
		if (!InteriorSystems.wantedForTest(wall, true, new Vec3(1.5, 0.0, 11.9), new Vec3(1.5, 0.0, 12.4), new Vec3(1.5, 0.0, 12.9), List.of(), false, false, true)) {
			held.add("right after it shut a door does not open for a player walking up to it");
		}

		if (InteriorSystems.wantedForTest(wall, true, new Vec3(1.5, 0.0, 12.0), new Vec3(1.5, 0.0, 12.4), new Vec3(1.5, 0.0, 12.4), List.of(), false, false, true)) {
			held.add("right after it shut a door opens again for a player stepping once towards it");
		}

		check(held.isEmpty(), "doors decide wrongly: " + held);

		if (!this.duel()) {
			return;
		}

		Vec3 arena = new Vec3(40.5, H, -18.8);
		List<InteriorPlan.Door> doors = DestroyerInterior.DOORS;
		List<Integer> near = new ArrayList<>();

		for (int i = 0; i < doors.size(); i++) {
			InteriorPlan.Door door = doors.get(i);
			Vec3 middle = new Vec3((door.x0() + door.x1() + 1) / 2.0, door.y0(), (door.z0() + door.z1() + 1) / 2.0);

			if (middle.distanceTo(arena) < 10.0 && Math.abs(door.y0() - H) < 3) {
				near.add(i);
			}
		}

		SaberCraft.LOGGER.info("PHASE29 FIGHT DOORS: {} doors by the arena: {}", near.size(), near.stream().map(i -> "(" + doors.get(i).x0() + "," + doors.get(i).z0() + ")").toList());
		check(!near.isEmpty(), "no door by the duel's arena (the test looks at the wrong place)");
		// (Phase 32: the hangar is sealed in the duel; the doors' own fight rules are tested here with
		// the seal lifted, as in a fight elsewhere, so that nothing of them passes by the seal alone.)
		StoryTesting.unsealHangar(true);

		try {
			this.fightDoorsAtArena(arena, doors, near);
		} finally {
			StoryTesting.unsealHangar(false);
		}

		// Phase 32: with the seal (the duel as played) the hangar's door stays shut for the player walking at it.
		InteriorPlan.Door sealed = doors.get(near.stream().min((a, b) -> Double.compare(this.doorDistance(doors.get(a), arena), this.doorDistance(doors.get(b), arena))).get());
		check(!this.walkThrough(sealed, arena), "in the duel the hangar's door opens for the player walking at it (it is sealed in the duel)");
	}

	/** Phase 29: the fight beside the arena's doors (no flapping) and a walk through the nearest (it opens). */
	private void fightDoorsAtArena(Vec3 arena, List<InteriorPlan.Door> doors, List<Integer> near) {
		check(this.on(s -> near.stream().anyMatch(i -> !Story.locked(this.session(s).destroyer, doors.get(i).x0(), doors.get(i).y0(), doors.get(i).z0()))),
				"the doors by the arena are all locked (the checks below could not fail)");
		// She circles on guard right before the hangar's door, the player steps about beside it (never towards it).
		this.server.runOnServer(s -> {
			Figure f = this.her(s);
			StoryTesting.guard(f, 4000);
			f.x = 40.5;
			f.z = -21.3;
			this.teleport(s, this.session(s), arena, 180.0F);
		});
		this.context.waitTicks(4);
		boolean[] was = this.doorStates();
		int[] opens = new int[doors.size()];
		Random random = new Random(29);
		double nearest = Double.MAX_VALUE;

		for (int t = 0; t < 240; t += 2) {
			if (t % 6 == 0) {
				double dx = random.nextDouble() * 2.4 - 1.2;
				double dz = -random.nextDouble() * 0.7;
				this.server.runOnServer(s -> {
					Figure f = this.her(s);
					StoryTesting.guard(f, 4000);
					Vec3 to = arena.add(dx, 0.0, dz);
					this.teleport(s, this.session(s), to, yaw(to, f.position()));
				});
			}

			this.context.waitTicks(2);
			boolean[] now = this.doorStates();

			for (int i : near) {
				if (now[i] && !was[i]) {
					opens[i]++;
				}
			}

			was = now;
			nearest = Math.min(nearest, this.on(s -> Math.abs(this.her(s).z - DestroyerInterior.HANGAR_Z1)));
		}

		int total = 0;

		for (int i : near) {
			total += opens[i];
		}

		SaberCraft.LOGGER.info("PHASE29 FIGHT DOORS: opened {} times while the two fought beside them (she came within {} of the wall)", total,
				String.format(Locale.ROOT, "%.1f", nearest));
		check(total == 0, "in the duel the doors by the arena open " + total + " times while the two only fight beside them (flapping)");
		// The player walks through the hangar's door: it opens, also in the fight.
		InteriorPlan.Door door = doors.get(near.stream().min((a, b) -> Double.compare(this.doorDistance(doors.get(a), arena), this.doorDistance(doors.get(b), arena))).get());
		check(this.walkThrough(door, arena), "in the duel the door by the arena does not open for the player walking through it");
	}

	/** Phase 29: whether that door opens for the player walking at it from 3 blocks off (on the arena's side). */
	private boolean walkThrough(InteriorPlan.Door door, Vec3 arena) {
		Vec3 middle = new Vec3((door.x0() + door.x1() + 1) / 2.0, H, (door.z0() + door.z1() + 1) / 2.0);
		Vec3 start = door.alongX() ? middle.add(middle.x > arena.x ? -3.0 : 3.0, 0.0, 0.0) : middle.add(0.0, 0.0, middle.z > arena.z ? -3.0 : 3.0);
		this.server.runOnServer(s -> {
			StoryTesting.guard(this.her(s), 4000);
			this.teleport(s, this.session(s), start, yaw(start, middle));
		});
		this.context.waitTicks(3);
		boolean opened = false;
		this.context.getInput().holdKey(options -> options.keyUp);

		for (int i = 0; i < 40 && !opened; i++) {
			this.context.waitTick();
			opened = this.on(s -> InteriorSystems.doorOpenForTest(this.level(s), this.session(s).destroyer, door));
		}

		this.context.getInput().releaseKey(options -> options.keyUp);
		this.context.waitTicks(10);
		return opened;
	}

	private void fightDoor(List<String> wrong, String who, InteriorPlan.Door door, Vec3 at, @Nullable Vec3 was, @Nullable Vec3 older, boolean expected) {
		if (InteriorSystems.wantedForTest(door, true, at, was, older, List.of(), false, true) != expected) {
			wrong.add(who + (expected ? " does not open it" : " opens it"));
		}
	}

	private double doorDistance(InteriorPlan.Door door, Vec3 at) {
		return new Vec3((door.x0() + door.x1() + 1) / 2.0, door.y0(), (door.z0() + door.z1() + 1) / 2.0).distanceTo(at);
	}

	// --- 3 (3, 4): the film cameras --------------------------------------------------------------------------

	private void saberCamera() {
		this.enter(5, "m6.saber");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.audit(true);
		this.take();
		Shots shots = this.watchFilm("saber", 260);

		if (shots != null) {
			this.cameraChecks("saber", shots, 1.0, true);
		}

		this.context.waitTicks(10);
		check(this.on(s -> this.session(s).has("saber.taken")), "the saber is not taken after its scene");
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

	private void sceneCameras() {
		// Hale comes into the armory (it begins as the file is loaded).
		this.audit(true);
		this.enter(2, "m3.hale");
		Shots hale = this.watchFilm("hale_in", 200);

		if (hale != null) {
			this.cameraChecks("hale_in", hale, -1.0, false);
		}

		// The crawl way's scene (the camera's shots only: played on the client with the plot's corner).
		this.enter(5, "m6.escape2");
		this.unseen();
		this.put(new Vec3(49.0, R, 8.0), 0.0F, 10.0F);
		Vec3 corner = this.on(s -> Vec3.atLowerCornerOf(this.session(s).destroyer));
		this.audit(true);
		this.context.runOnClient(client -> StoryCinema.startForTest("service", 150, corner));
		Shots service = this.watchFilm("service", 200);

		if (service != null) {
			this.cameraChecks("service", service, -1.0, false);
		}
	}

	private void takedownCamera() {
		// The detention block's guard (Phiro: the camera went into the cell's wall), at three moments of his look round.
		for (int k = 0; k < 3; k++) {
			// (He stays down once overpowered, in the saved story too: the test goes back to before that.)
			this.enter(4, "m5.r2", StoryTesting.detGuardDown());
			this.context.waitTicks(30 + k * 50);
			int takedowns = this.on(s -> StoryTesting.takedowns());
			this.audit(true);
			// (Behind him and the takedown in the same tick: he turns as he looks round.)
			boolean behind = this.on(s -> {
				Figure guard = this.session(s).figure("det_guard");
				Vec3 back = this.behindOf(s, guard, 1.4);

				if (back == null) {
					return false;
				}

				this.teleport(s, this.session(s), back, yaw(back, guard.position()));
				// (Phase 31: crouching, as one has to.)
				StoryTesting.takedownCrouched(this.session(s));
				return true;
			});

			if (!behind) {
				this.problem("no free place behind the detention block's guard (" + k + ")");
				this.audit(false);
				continue;
			}

			if (this.on(s -> StoryTesting.takedowns()) == takedowns) {
				this.problem("the detention block's guard cannot be overpowered from behind (" + k + ")");
				this.audit(false);
				continue;
			}

			Shots shots = this.watchFilm("takedown", 120);

			if (shots != null) {
				this.cameraChecks("takedown (detention, " + k + ")", shots, -1.0, false);
			}
		}

		// The lab's guard, while he looks east (as in Phase 27; the saber not yet taken, he not yet down).
		this.enter(5, "m6.door", "saber.taken", StoryTesting.labGuardDown());
		int east = this.on(s -> StoryTesting.labLookIn(this.session(s), 2));
		this.context.waitTicks(east + 2);
		int takedowns = this.on(s -> StoryTesting.takedowns());
		this.server.runOnServer(s -> this.teleport(s, this.session(s), new Vec3(41.6, R, 36.6), 0.0F));
		this.context.waitTicks(1);
		this.audit(true);
		this.server.runOnServer(s -> StoryTesting.takedownCrouched(this.session(s)));

		if (this.on(s -> StoryTesting.takedowns()) == takedowns) {
			this.problem("the lab's guard cannot be overpowered from behind while he looks east");
			this.audit(false);
			return;
		}

		Shots shots = this.watchFilm("takedown", 120);

		if (shots != null) {
			this.cameraChecks("takedown (lab)", shots, -1.0, false);
		}
	}

	/** What a film's camera did: every frame as fitted (see {@link FilmCamera.Frame}), and the camera the game used each tick. */
	private record Shots(List<FilmCamera.Frame> frames, int ticks, int insideTicks, int attachedTicks, int longestAir) {
	}

	/** Starts recording the film camera's frames (or stops). */
	private void audit(boolean on) {
		this.context.runOnClient(client -> FilmCamera.auditForTest(on));
	}

	/** Waits for that film and watches it to its end (that many ticks at most); null when it does not begin. */
	private @Nullable Shots watchFilm(String name, int ticks) {
		String film = this.film(60);

		if (!name.equals(film)) {
			this.problem("the film '" + name + "' does not begin (" + film + ")");
			this.audit(false);
			return null;
		}

		int seen = 0;
		int insideTicks = 0;
		int attached = 0;
		int air = 0;
		int longestAir = 0;

		for (int i = 0; i < ticks; i++) {
			Object[] now = this.context.computeOnClient(client -> {
				if (!name.equals(StoryCinema.filmForTest())) {
					return null;
				}

				Vec3 camera = client.gameRenderer.mainCamera().position();
				return new Object[] {FilmCamera.inside(client.level, camera), client.gameRenderer.mainCamera().isDetached(), StoryCinema.ageForTest(),
						StoryCinema.lengthForTest(), camera};
			});

			if (now == null) {
				break;
			}

			seen++;
			int age = (int) now[2];
			int length = (int) now[3];

			if ((boolean) now[0]) {
				if (insideTicks == 0) {
					SaberCraft.LOGGER.warn("PHASE29 CAMERA {}: inside something at age {}: {}", name, age, now[4]);
				}

				insideTicks++;
			}

			if (!(boolean) now[1] && age > 2 && age < length - 2) {
				attached++;
			}

			boolean grounded = !this.on(this::airborne);
			air = grounded ? 0 : air + 1;
			longestAir = Math.max(longestAir, air);
			this.context.waitTick();
		}

		List<FilmCamera.Frame> frames = this.context.computeOnClient(client -> FilmCamera.framesForTest());
		this.audit(false);
		return new Shots(frames.stream().filter(frame -> frame.key().startsWith(name + ":")).toList(), seen, insideTicks, attached, longestAir);
	}

	/**
	 * The checks of a film's camera: never inside something solid, nothing solid between it and what
	 * it shows, never in a person, no jumps within a shot; that far from the player's eyes at least
	 * (when not negative); the player's figure drawn the whole film (when asked).
	 */
	private void cameraChecks(String name, Shots shots, double fromEyes, boolean figure) {
		List<FilmCamera.Frame> frames = shots.frames();
		int inside = 0;
		int blocked = 0;
		int inBody = 0;
		int jumps = 0;
		double nearestEyes = Double.MAX_VALUE;
		double shortest = Double.MAX_VALUE;
		FilmCamera.Frame last = null;
		String firstWrong = null;
		// Where the camera trailed its fitted place most (it never leaps: it follows, or holds).
		double maxLag = 0.0;
		FilmCamera.Frame lagging = null;
		int trailing = 0;

		for (int index = 0; index < frames.size(); index++) {
			FilmCamera.Frame frame = frames.get(index);

			if (frame.inside() || frame.blocked() || frame.inBody()) {
				if (firstWrong == null) {
					firstWrong = String.format(Locale.ROOT, "%s at %.1f: camera %s about %s%s%s%s", frame.key(), frame.t(), frame.camera(), frame.about(), frame.inside() ? " inside" : "",
							frame.blocked() ? " blocked" : "", frame.inBody() ? " in a body" : "");
				}
			}

			inside += frame.inside() ? 1 : 0;
			blocked += frame.blocked() ? 1 : 0;
			inBody += frame.inBody() ? 1 : 0;

			if (frame.fromEyes() >= 0.0) {
				nearestEyes = Math.min(nearestEyes, frame.fromEyes());
			}

			shortest = Math.min(shortest, frame.reach());
			trailing += frame.lag() > 0.05 ? 1 : 0;

			if (frame.lag() > maxLag) {
				maxLag = frame.lag();
				lagging = frame;
			}

			if (last != null && last.key().equals(frame.key())) {
				double moved = last.camera().distanceTo(frame.camera());
				double dt = Math.max(0.0F, frame.t() - last.t());

				if (moved > 0.9 + 0.6 * dt) {
					if (jumps == 0) {
						SaberCraft.LOGGER.warn("PHASE29 CAMERA {}: a jump of {} in {} ticks in shot {} at {}", name, String.format(Locale.ROOT, "%.2f", moved),
								String.format(Locale.ROOT, "%.2f", dt), frame.key(), String.format(Locale.ROOT, "%.1f", frame.t()));

						// The frames round it (to see what moved: the point, or the camera round it).
						for (int k = Math.max(0, index - 4); k < Math.min(frames.size(), index + 3); k++) {
							FilmCamera.Frame f = frames.get(k);
							SaberCraft.LOGGER.warn("PHASE29 CAMERA {}:   {} t {} camera ({}, {}, {}) about ({}, {}, {}) reach {} [{}] lag {}", name, f.key(),
									String.format(Locale.ROOT, "%.2f", f.t()), String.format(Locale.ROOT, "%.2f", f.camera().x), String.format(Locale.ROOT, "%.2f", f.camera().y),
									String.format(Locale.ROOT, "%.2f", f.camera().z), String.format(Locale.ROOT, "%.2f", f.about().x), String.format(Locale.ROOT, "%.2f", f.about().y),
									String.format(Locale.ROOT, "%.2f", f.about().z), String.format(Locale.ROOT, "%.2f", f.reach()), f.note(), String.format(Locale.ROOT, "%.2f", f.lag()));
						}
					}

					jumps++;
				}
			}

			last = frame;
		}

		int[] counts = this.context.computeOnClient(client -> FilmCamera.countsForTest());
		SaberCraft.LOGGER.info("PHASE29 CAMERA {}: {} ticks, {} frames fitted; inside {}, blocked {}, in a body {}, jumps {}; game camera inside {} ticks, at the eyes {} ticks; "
				+ "nearest the eyes {}, nearest the point {}; the player in the air at most {} ticks; fitted so far {}", name, shots.ticks(), frames.size(), inside, blocked,
				inBody, jumps, shots.insideTicks(), shots.attachedTicks(), nearestEyes == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.2f", nearestEyes),
				shortest == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.2f", shortest), shots.longestAir(), java.util.Arrays.toString(counts));

		if (firstWrong != null) {
			SaberCraft.LOGGER.warn("PHASE29 CAMERA {}: first wrong frame: {}", name, firstWrong);
		}

		if (lagging != null) {
			SaberCraft.LOGGER.info("PHASE29 CAMERA {}: trailed its fitted place in {} frames, most {} in shot {} at {} [{}]", name, trailing,
					String.format(Locale.ROOT, "%.2f", maxLag), lagging.key(), String.format(Locale.ROOT, "%.1f", lagging.t()), lagging.note());
		}

		check(frames.size() >= Math.min(20, shots.ticks() / 2), "the camera of " + name + " is not fitted into the rooms (" + frames.size() + " frames)");
		check(inside == 0 && shots.insideTicks() == 0, "the camera of " + name + " is inside a wall (" + inside + " frames, " + shots.insideTicks() + " ticks)");
		check(blocked == 0, "the camera of " + name + " looks through a wall at what it shows (" + blocked + " frames)");
		check(inBody == 0, "the camera of " + name + " is in a person (" + inBody + " frames)");
		check(jumps == 0, "the camera of " + name + " jumps within a shot (" + jumps + " times)");

		if (fromEyes >= 0.0 && nearestEyes != Double.MAX_VALUE) {
			check(nearestEyes >= fromEyes, "the camera of " + name + " comes " + String.format(Locale.ROOT, "%.2f", nearestEyes) + " near the player's eyes (into their skin)");
		}

		if (figure) {
			check(shots.attachedTicks() == 0, "in " + name + " the camera sits in the player's eyes for " + shots.attachedTicks() + " ticks (the figure vanishes)");
		}
	}

	// --- 4 (5.1): her back -----------------------------------------------------------------------------------------

	private void rear() {
		if (!this.duel()) {
			return;
		}

		int[] timing = this.on(s -> StoryTesting.rearTiming(this.session(s)));
		// Struck from behind twice in a row: the first stroke hits, the second (soon after) she turns aside and strikes back.
		this.server.runOnServer(s -> {
			this.calm(s, 40.5, -30.0, 0.0F);
			this.behind(s, 1.8);
		});
		this.context.waitTicks(1);
		int[] b0 = this.on(s -> StoryTesting.boss29());
		float h0 = this.on(s -> this.her(s).health);
		this.strike();
		int[] b1 = this.on(s -> StoryTesting.boss29());
		float h1 = this.on(s -> this.her(s).health);
		check(b1[0] == b0[0] + 1 && h1 < h0, "a stroke from behind her does not hit (" + b0[0] + " -> " + b1[0] + ", " + h0 + " -> " + h1 + ")");
		check(b1[2] > b0[2], "struck from behind she does not whirl round with a blow");
		// (A stroke needs 6 ticks after the last: the player's saber, not her.)
		this.context.waitTicks(4);
		this.server.runOnServer(s -> {
			this.calm(s, this.her(s).x, this.her(s).z, 0.0F);
			this.behind(s, 1.8);
		});
		this.context.waitTicks(1);
		this.strike();
		int[] b2 = this.on(s -> StoryTesting.boss29());
		float h2 = this.on(s -> this.her(s).health);
		check(b2[1] == b1[1] + 1, "a second stroke from behind right after the first is not turned aside (" + b1[1] + " -> " + b2[1] + ")");
		check(h2 >= h1 - 1.0E-4F, "the second stroke from behind right after the first still hurts her");
		check(b2[2] > b1[2], "turning a stroke from behind aside she does not strike back");

		// Jumping behind her again and again (she left as she is): it does not pay.
		this.server.runOnServer(s -> this.before(s, 4.0));
		this.context.waitTicks(timing[1] + 10);
		this.server.runOnServer(s -> this.calm(s, this.her(s).x, this.her(s).z, this.her(s).headYaw));
		int hits = this.on(s -> this.session(s).counters.getOrDefault("duel_hits", 0));
		int[] b3 = this.on(s -> StoryTesting.boss29());

		for (int i = 0; i < 6; i++) {
			this.server.runOnServer(s -> {
				Figure f = this.her(s);
				String doing = StoryTesting.action(f);

				if (!"windup".equals(doing) && !"open".equals(doing)) {
					// (On guard again between the tries: no new attack of hers in the way of the test.)
					StoryTesting.guard(f, 4000);
				}

				this.behind(s, 1.8);
			});
			this.context.waitTicks(1);
			this.strike();
			this.context.waitTicks(6);
		}

		int landed = this.on(s -> this.session(s).counters.getOrDefault("duel_hits", 0)) - hits;
		int[] b4 = this.on(s -> StoryTesting.boss29());
		SaberCraft.LOGGER.info("PHASE29 REAR: 6 strokes from behind in a row: {} landed, {} turned aside, {} turn-about blows", landed, b4[1] - b3[1], b4[2] - b3[2]);
		check(landed >= 1, "not even the first stroke from behind lands");
		check(landed <= 2, "striking from behind again and again still lands " + landed + " of 6 strokes");

		// Standing behind her: after a moment she notices and turns (quickly, not in one snap), or answers.
		this.server.runOnServer(s -> {
			this.calm(s, 40.5, -30.0, 0.0F);
			this.behind(s, 2.3);
		});
		int answers = this.on(s -> StoryTesting.boss29()[3]);
		float previous = this.on(s -> this.her(s).headYaw);
		float biggest = 0.0F;
		int faced = -1;

		for (int i = 0; i < 50 && faced < 0; i++) {
			this.context.waitTick();
			float[] now = this.on(s -> {
				Figure f = this.her(s);
				Vec3 me = this.session(s).local(Story.DESTROYER);
				return new float[] {f.headYaw, Math.abs(Mth.wrapDegrees(yaw(f.position(), me) - f.headYaw))};
			});
			biggest = Math.max(biggest, Math.abs(Mth.wrapDegrees(now[0] - previous)));
			previous = now[0];

			if (now[1] < 45.0F) {
				faced = i;
			}
		}

		int answered = this.on(s -> StoryTesting.boss29()[3]) - answers;
		SaberCraft.LOGGER.info("PHASE29 REAR: standing behind her she faced the player after {} ticks (notices after {}), answered {} times, turned at most {} degrees a tick",
				faced, timing[0], answered, String.format(Locale.ROOT, "%.1f", biggest));
		check(faced >= 0 && faced <= timing[0] + 20, "standing behind her she does not turn to the player (after " + faced + " ticks)");
		check(faced >= 2, "she turns to one behind her at once, as if she saw through her back (" + faced + " ticks)");
		check(biggest <= 32.0F, "she snaps round (" + biggest + " degrees in one tick)");

		// Standing in her (on her back): pushed out.
		this.server.runOnServer(s -> {
			Figure f = this.her(s);
			StoryTesting.guard(f, 4000);
			this.teleport(s, this.session(s), f.position().add(f.look().scale(-0.15)), f.headYaw);
		});
		this.context.waitTicks(14);
		double apart = this.on(s -> {
			Vec3 me = this.session(s).local(Story.DESTROYER);
			return Math.hypot(me.x - this.her(s).x, me.z - this.her(s).z);
		});
		check(apart >= 0.6, "standing in her the player is not pushed out (" + String.format(Locale.ROOT, "%.2f", apart) + " apart)");
	}

	// --- 5 (5.2): the guard ---------------------------------------------------------------------------------

	private void guardRules() {
		if (!this.duel()) {
			return;
		}

		check(AbilityConfig.get().staminaEnabled, "stamina is off in the test's settings");
		SaberAbilities.StrictGuard rules = StoryTesting.strictGuard(StoryDifficulty.NORMAL);
		float max = AbilityConfig.get().staminaMax;
		this.fresh(4.0);

		// Holding it costs stamina all the time.
		if (!this.raise()) {
			return;
		}

		float s0 = this.stamina();
		this.context.waitTicks(40);
		float s1 = this.stamina();
		SaberCraft.LOGGER.info("PHASE29 GUARD: held 40 ticks: {} -> {}", String.format(Locale.ROOT, "%.1f", s0), String.format(Locale.ROOT, "%.1f", s1));
		check(s0 - s1 >= 0.8F * 40 * rules.holdDrain() * max / 100.0F, "holding the guard up costs (almost) nothing (" + s0 + " -> " + s1 + ")");
		// Held on and on: it gives way by itself, stays down a moment, the player worn out.
		long[] strict0 = this.on(s -> SaberAbilities.strictForTest(this.me(s)));
		boolean down = false;
		int heldFor = 40;

		int raisedAgain = 0;

		for (int i = 0; i < 400 && !down; i++) {
			this.context.waitTick();
			heldFor++;

			if (!this.on(s -> SaberAbilities.isGuarding(this.me(s)))) {
				if (this.on(s -> SaberAbilities.strictForTest(this.me(s)))[4] > strict0[4]) {
					down = true;
				} else if (raisedAgain < 3) {
					// Down by the settings' longest hold (not worn out): raised again as soon as it can be, as one holding on would.
					raisedAgain++;
					this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
					this.context.waitTicks(AbilityConfig.get().guardCooldown + 2);

					if (!this.raise()) {
						down = true;
					}
				} else {
					down = true;
				}
			}
		}

		long[] strict1 = this.on(s -> SaberAbilities.strictForTest(this.me(s)));
		SaberCraft.LOGGER.info("PHASE29 GUARD: raised again {} times on the way (the settings' longest hold)", raisedAgain);
		SaberCraft.LOGGER.info("PHASE29 GUARD: it gave way after {} ticks held; state {}", heldFor, java.util.Arrays.toString(strict1));
		check(down, "the guard can be held up for ever (still up after " + heldFor + " ticks)");
		check(strict1[4] > strict0[4] && strict1[1] == 1 && strict1[2] > 0, "the guard that gave way leaves the player neither worn out nor locked (" + java.util.Arrays.toString(strict1) + ")");
		this.context.waitTicks(4);
		check(!this.on(s -> SaberAbilities.isGuarding(this.me(s))), "the guard that gave way comes up again at once (the key held)");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(2);
		this.context.getInput().pressKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(3);
		check(!this.on(s -> SaberAbilities.isGuarding(this.me(s))), "the guard that gave way can be raised again at once");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		// Stamina comes back only after a while, and slower (worn out, slower still).
		long wait = this.on(s -> SaberAbilities.strictForTest(this.me(s)))[3];
		float a0 = this.stamina();
		this.context.waitTicks((int) Math.max(1, wait - 2));
		float a1 = this.stamina();
		this.context.waitTicks(60);
		float a2 = this.stamina();
		SaberCraft.LOGGER.info("PHASE29 GUARD: stamina back {} ticks after it gave way ({} left now): {} -> {} (waiting) -> {} (60 ticks later)", strict1[3], wait,
				String.format(Locale.ROOT, "%.1f", a0), String.format(Locale.ROOT, "%.1f", a1), String.format(Locale.ROOT, "%.1f", a2));
		check(strict1[3] >= rules.regenDelay() - 2, "after the guard gave way stamina comes back too soon (in " + strict1[3] + " ticks)");
		check(a1 <= a0 + 1.0F, "stamina comes back while it should still wait (" + a0 + " -> " + a1 + ")");
		check(a2 > a1, "after the guard gave way stamina does not come back at all (" + a1 + " -> " + a2 + ")");
		check(a2 - a1 <= AbilityConfig.get().staminaRegen * 60 * rules.regenRate() * rules.tiredRegen() * 1.25F,
				"worn out, stamina does not come back slower (" + a1 + " -> " + a2 + " in 60 ticks)");

		// Blows in a row cost more each.
		this.fresh(2.3);

		if (!this.raise()) {
			return;
		}

		this.context.waitTicks(10);
		List<Float> costs = new ArrayList<>();
		List<Long> chain = new ArrayList<>();

		for (int blow = 0; blow < 5; blow++) {
			float before = this.stamina();
			int[] counts = this.on(s -> this.counts(this.session(s)));
			this.server.runOnServer(s -> {
				this.before(s, 2.3);
				Figure f = this.her(s);
				StoryTesting.guard(f, 4000);
				StoryTesting.blow(f, 1, 4);
			});
			int[] after = counts;

			for (int i = 0; i < 20 && java.util.Arrays.equals(after, counts); i++) {
				this.context.waitTick();
				after = this.on(s -> this.counts(this.session(s)));
			}

			if (after[3] > counts[3] || after[4] > counts[4]) {
				break;
			}

			costs.add(before - this.stamina());
			chain.add(this.on(s -> SaberAbilities.strictForTest(this.me(s)))[0]);
			this.context.waitTicks(2);
		}

		SaberCraft.LOGGER.info("PHASE29 GUARD: blows in a row cost {} (in a row {})", costs.stream().map(c -> String.format(Locale.ROOT, "%.1f", c)).toList(), chain);
		check(costs.size() >= 2, "the guard breaks at the first or second blow (" + costs + ")");
		check(costs.size() <= 4, "the guard holds " + costs.size() + " blows in a row on Normal (too many)");
		check(costs.size() < 2 || costs.get(costs.size() - 1) > costs.get(0) + 2.0F, "blows in a row do not cost more each (" + costs + ")");
		check(chain.size() < 2 || chain.get(chain.size() - 1) > chain.get(0), "blows in a row are not counted as such (" + chain + ")");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);

		// Raised just in time: a perfect parry still (the opening), it costs little and starts the run of blows anew.
		this.fresh(2.3);
		this.context.waitTicks(24);
		int[] counts = this.on(s -> this.counts(this.session(s)));
		this.server.runOnServer(s -> {
			this.before(s, 2.3);
			StoryTesting.blow(this.her(s), 1, 6);
		});
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(10);
		int[] after = this.on(s -> this.counts(this.session(s)));
		String opened = this.on(s -> StoryTesting.action(this.her(s)));
		check(after[1] == counts[1] + 1, "a guard raised just in time does not parry perfectly any more (" + java.util.Arrays.toString(counts) + " -> " + java.util.Arrays.toString(after) + ")");
		check("open".equals(opened), "after a perfect parry she is not open (" + opened + ")");
		check(this.on(s -> SaberAbilities.strictForTest(this.me(s)))[0] == 0, "a perfect parry counts as one more blow in a row");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		// Raised again at once (down and up): no perfect parry. With the guard key it cannot even be
		// raised again so soon (its pause after lowering, as in free play); the block with the use key
		// has no such pause, so there her own rule counts (a guard raised again within RAISE_LOCK
		// ticks parries nothing perfectly).
		this.fresh(2.3);
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(16);
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(3);
		counts = this.on(s -> this.counts(this.session(s)));
		this.server.runOnServer(s -> {
			this.faced(s, 2.3);
			StoryTesting.blow(this.her(s), 1, 6);
		});
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(3);
		boolean upAgain = this.on(s -> SaberAbilities.isGuarding(this.me(s)));
		this.context.waitTicks(7);
		after = this.on(s -> this.counts(this.session(s)));
		SaberCraft.LOGGER.info("PHASE29 GUARD: guard key lowered and pressed again at once as her blow came: up again {}; {} -> {} (blocked, perfect, shaking, broken, hurt)",
				upAgain, java.util.Arrays.toString(counts), java.util.Arrays.toString(after));
		check(after[1] == counts[1], "a guard raised again at once (down and up) still parries perfectly");
		check(!upAgain || after[0] + after[2] > counts[0] + counts[2], "a guard raised again at once neither waits for its pause nor takes her blow");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		boolean usable = this.on(s -> this.me(s).getMainHandItem().getUseDuration(this.me(s)) > 0);

		if (usable) {
			for (boolean again : new boolean[] {false, true}) {
				this.fresh(2.3);
				this.context.waitTicks(24);

				if (again) {
					this.server.runOnServer(s -> this.me(s).startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND));
					this.context.waitTicks(12);
					this.server.runOnServer(s -> this.me(s).stopUsingItem());
					this.context.waitTicks(3);
				}

				int[] was = this.on(s -> this.counts(this.session(s)));
				this.server.runOnServer(s -> {
					this.faced(s, 2.3);
					StoryTesting.blow(this.her(s), 1, 6);
					this.me(s).startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
				});
				this.context.waitTicks(10);
				int[] now = this.on(s -> this.counts(this.session(s)));
				this.server.runOnServer(s -> this.me(s).stopUsingItem());
				SaberCraft.LOGGER.info("PHASE29 GUARD: block with the use key raised {}: {} -> {}", again ? "again at once" : "just in time", java.util.Arrays.toString(was),
						java.util.Arrays.toString(now));

				if (again) {
					check(now[1] == was[1] && now[0] > was[0], "a block with the use key raised again at once parries perfectly (or does not hold)");
				} else {
					check(now[1] == was[1] + 1, "a block with the use key raised just in time does not parry perfectly");
				}
			}
		} else {
			SaberCraft.LOGGER.info("PHASE29 GUARD: the saber has no block on the use key here (only the guard key)");
		}

		// Timed parries keep paying: two in a row (the guard down a while between, nothing reset): both perfect, both open her up.
		// (Within one combo of hers the guard key cannot be raised again in time: its cooldown after lowering, as in free play.)
		this.fresh(2.3);
		this.context.waitTicks(24);
		int[] start = this.on(s -> this.counts(this.session(s)));
		List<String> opens = new ArrayList<>();

		for (int k = 0; k < 2; k++) {
			this.server.runOnServer(s -> {
				this.faced(s, 2.3);
				StoryTesting.blow(this.her(s), 1, 6);
			});
			this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
			this.context.waitTicks(10);
			opens.add(this.on(s -> StoryTesting.action(this.her(s))));
			this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
			this.context.waitTicks(40);
		}

		int[] end = this.on(s -> this.counts(this.session(s)));
		SaberCraft.LOGGER.info("PHASE29 GUARD: two timed parries 40 ticks apart: {} -> {}, she after each: {}", java.util.Arrays.toString(start), java.util.Arrays.toString(end), opens);
		check(end[1] == start[1] + 2, "a second timed parry later in the fight is not perfect any more (" + java.util.Arrays.toString(start) + " -> " + java.util.Arrays.toString(end) + ")");
		check(opens.stream().allMatch("open"::equals), "after a timed parry she is not open each time (" + opens + ")");
		this.server.runOnServer(s -> {
			StoryTesting.freshGuard(this.me(s));
			SaberAbilities.setStaminaForTests(this.me(s), max);
		});
	}

	/** Her on guard for a long while, the player that far before her, the stricter guard afresh, stamina full, the guard down. */
	private void fresh(double distance) {
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(2);
		this.server.runOnServer(s -> {
			this.calm(s, 40.5, -30.0, 180.0F);
			this.before(s, distance);
			StoryTesting.freshGuard(this.me(s));
			// (A guard the boss broke stays down a moment by the duel's own timer, too: forgotten for the next try.)
			this.session(s).timers.remove("guard_broken");
			SaberAbilities.setStaminaForTests(this.me(s), AbilityConfig.get().staminaMax);
		});
		this.context.waitTicks(4);
	}

	/** Raises the guard with its key (held); whether it is up. */
	private boolean raise() {
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);

		for (int i = 0; i < 30; i++) {
			this.context.waitTick();

			if (this.on(s -> SaberAbilities.isGuarding(this.me(s)))) {
				return true;
			}
		}

		this.problem("the test cannot raise the guard in the duel");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		return false;
	}

	private float stamina() {
		return this.on(s -> SaberAbilities.stamina(this.me(s)));
	}

	/** Blocks, perfect parries, guards shaking, broken; blows that came through (the duel's counters). */
	private int[] counts(StorySession s) {
		return new int[] {s.counters.getOrDefault("blocked", 0), s.counters.getOrDefault("perfect", 0), s.counters.getOrDefault("guard_strained", 0),
				s.counters.getOrDefault("guard_broken", 0), s.counters.getOrDefault("duel_hurt", 0)};
	}

	// --- 6 (5.3, 5.4): her attacks --------------------------------------------------------------------------

	private void slam() {
		if (!this.duel()) {
			return;
		}

		double[] reach = StoryTesting.slamReach();
		// Standing before her, guard down: it hits (after its warning, seen on the client).
		this.fresh(2.6);
		this.server.runOnServer(s -> this.faced(s, 2.6));
		this.context.waitTicks(2);
		int seen = this.context.computeOnClient(client -> StoryClient.slamsSeenForTest());
		int[] b0 = this.on(s -> StoryTesting.boss29());
		int hurt = this.on(s -> this.session(s).counters.getOrDefault("duel_hurt", 0));
		this.server.runOnServer(s -> StoryTesting.slam(this.session(s), this.her(s)));
		int warned = -1;
		int fell = -1;

		for (int i = 0; i < 60 && fell < 0; i++) {
			this.context.waitTick();

			if (warned < 0 && this.context.computeOnClient(client -> StoryClient.slamsSeenForTest()) > seen) {
				warned = i;
			}

			int[] b = this.on(s -> StoryTesting.boss29());

			if (b[7] + b[8] + b[9] > b0[7] + b0[8] + b0[9]) {
				fell = i;
			}
		}

		int[] b1 = this.on(s -> StoryTesting.boss29());
		SaberCraft.LOGGER.info("PHASE29 SLAM: warned at {}, fell at {} (ticks)", warned, fell);
		check(warned >= 0 && warned <= 3, "her slam comes without its warning on the client (" + warned + ")");
		check(fell >= 15, "her slam falls too soon after its warning to react (" + fell + " ticks)");
		check(b1[8] == b0[8] + 1 && this.on(s -> this.session(s).counters.getOrDefault("duel_hurt", 0)) > hurt, "standing before her, guard down, her slam does not hit");
		check("open".equals(this.on(s -> StoryTesting.action(this.her(s)))) || "recoil".equals(this.on(s -> StoryTesting.action(this.her(s)))),
				"after her slam she is neither open nor stepping back");

		// A step aside, walking (no sprint, no dash), begun a moment after its warning, staying within
		// its reach (the walk stops short of it): out of its arc it misses, and she is open (her blade
		// in the floor). (Standing still before her it hits: measured above.)
		this.fresh(2.6);
		this.server.runOnServer(s -> this.faced(s, 2.6));
		this.context.waitTicks(2);
		int[] c0 = this.on(s -> StoryTesting.boss29());
		this.server.runOnServer(s -> StoryTesting.slam(this.session(s), this.her(s)));
		this.context.waitTicks(5);
		this.context.getInput().holdKey(options -> options.keyLeft);
		// Where the player was against the way it strikes when it fell: how far, how many degrees off.
		double[] aside = {0.0, 0.0};

		for (int i = 0; i < 40 && this.on(s -> StoryTesting.boss29())[7] + this.on(s -> StoryTesting.boss29())[8] == c0[7] + c0[8]; i++) {
			double[] now = this.on(s -> {
				Figure f = this.her(s);
				Vec3 me = this.session(s).local(Story.DESTROYER);
				Vec3 flat = new Vec3(me.x - f.x, 0.0, me.z - f.z);
				Vec3 way = StoryTesting.slamWay(f);
				double off = way == null || flat.lengthSqr() < 1.0E-6 ? -1.0
						: Math.toDegrees(Math.acos(Mth.clamp(flat.normalize().dot(new Vec3(way.x, 0.0, way.z).normalize()), -1.0, 1.0)));
				return new double[] {flat.length(), off};
			});
			aside = now;

			// (Let go well short of its reach: one slides on a little after.)
			if (now[0] >= reach[0] - 0.65) {
				this.context.getInput().releaseKey(options -> options.keyLeft);
			}

			this.context.waitTick();
		}

		this.context.getInput().releaseKey(options -> options.keyLeft);
		int[] c1 = this.on(s -> StoryTesting.boss29());
		SaberCraft.LOGGER.info("PHASE29 SLAM: walked aside within its reach: {} blocks from her, {} degrees off its way as it fell; missed {}, hit {}",
				String.format(Locale.ROOT, "%.2f", aside[0]), String.format(Locale.ROOT, "%.1f", aside[1]), c1[7] - c0[7], c1[8] - c0[8]);
		check(aside[0] <= reach[0], "(the test) the walk aside left her slam's reach (" + String.format(Locale.ROOT, "%.2f", aside[0]) + "): it says nothing about its arc");
		check(c1[7] == c0[7] + 1 && c1[8] == c0[8], "walking aside out of her slam's arc does not escape it (missed " + (c1[7] - c0[7]) + ", hit " + (c1[8] - c0[8]) + ")");
		check("open".equals(this.on(s -> StoryTesting.action(this.her(s)))), "after her missed slam she is not open");

		// A jump over the shock wave.
		this.fresh(2.6);
		this.server.runOnServer(s -> this.faced(s, 2.6));
		this.context.waitTicks(2);
		int[] d0 = this.on(s -> StoryTesting.boss29());
		this.server.runOnServer(s -> StoryTesting.slam(this.session(s), this.her(s)));
		this.untilWindup(4);
		this.server.runOnServer(s -> {
			Vec3 me = this.session(s).local(Story.DESTROYER);
			this.teleport(s, this.session(s), me.add(0.0, 1.1, 0.0), this.me(s).getYRot());
		});
		this.context.waitTicks(12);
		int[] d1 = this.on(s -> StoryTesting.boss29());
		check(d1[7] == d0[7] + 1, "a jump over her slam's shock wave does not escape it");

		// On guard: it holds, but costs much.
		this.fresh(2.6);
		this.server.runOnServer(s -> this.faced(s, 2.6));

		if (!this.raise()) {
			return;
		}

		this.context.waitTicks(10);
		float before = this.stamina();
		int[] e0 = this.on(s -> StoryTesting.boss29());
		this.server.runOnServer(s -> StoryTesting.slam(this.session(s), this.her(s)));
		this.context.waitTicks(40);
		int[] e1 = this.on(s -> StoryTesting.boss29());
		float after = this.stamina();
		SaberCraft.LOGGER.info("PHASE29 SLAM: on guard stamina {} -> {}", String.format(Locale.ROOT, "%.1f", before), String.format(Locale.ROOT, "%.1f", after));
		check(e1[9] == e0[9] + 1 || e1[8] == e0[8] + 1, "her slam on the guard is neither blocked nor breaking through");
		check(before - after >= 30.0F, "her slam costs the guard little (" + before + " -> " + after + ")");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		SaberCraft.LOGGER.info("PHASE29 SLAM: its reach {} blocks, {} degrees each side", reach[0], reach[1]);

		// Crowding her: a shove (her hand first), the player thrown off.
		this.fresh(1.0);
		int shoves = this.on(s -> StoryTesting.boss29()[4]);
		this.server.runOnServer(s -> {
			Figure f = this.her(s);
			StoryTesting.guard(f, 0);
			this.before(s, 1.0);
		});
		this.context.waitTicks(30);
		double apart = this.on(s -> this.session(s).local(Story.DESTROYER).distanceTo(this.her(s).position()));
		check(this.on(s -> StoryTesting.boss29()[4]) > shoves, "crowding her draws no shove");
		check(apart >= 1.8, "her shove does not throw the player off (" + String.format(Locale.ROOT, "%.1f", apart) + " apart)");

		// Her throw: at the chest and at the legs by turns.
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
		this.film(30);
		this.context.waitTicks(5);
		this.skip("boss_mid");
		boolean[] low = new boolean[2];

		for (int k = 0; k < 2; k++) {
			int index = k;
			this.server.runOnServer(s -> {
				this.calm(s, this.her(s).x, this.her(s).z, this.her(s).headYaw);
				this.before(s, 6.0);
				StoryTesting.throwNow(this.session(s), this.her(s));
				low[index] = this.her(s).throwLow;
			});
			this.context.waitTicks(80);
		}

		check(low[0] != low[1], "her staff is thrown the same way twice running (low " + low[0] + ", " + low[1] + ")");

		// The low throw: standing it hits the legs; jumped over at the right moment it passes under,
		// and comes back high over the player's head (not into their legs from behind).
		for (boolean jump : new boolean[] {false, true}) {
			int hits = this.on(s -> this.session(s).counters.getOrDefault("throw_hit", 0));
			double[] floor = new double[1];
			this.server.runOnServer(s -> {
				this.calm(s, this.her(s).x, this.her(s).z, this.her(s).headYaw);
				this.before(s, 6.0);
				floor[0] = this.me(s).getY();
				StoryTesting.throwNow(this.session(s), this.her(s));
				this.her(s).throwLow = true;
			});
			boolean jumped = false;
			int heldAt = -100;
			// How high the player's feet and the staff were while it passed them (lowest feet, staff on its way out and back).
			double lowestFeet = Double.MAX_VALUE;
			double[] staff = {Double.MAX_VALUE, Double.MAX_VALUE};

			for (int i = 0; i < 80; i++) {
				this.context.waitTick();

				if (i == heldAt + 3) {
					this.context.getInput().releaseKey(options -> options.keyJump);
				}

				if (jump && !jumped) {
					// Jumped 4 or 5 ticks before it reaches the legs: high in the jump as it passes.
					int[] in = this.on(s -> StoryTesting.staffArrives(this.her(s), this.me(s).position(), 0.9));

					if (in != null && in[1] == 1 && in[0] >= 4 && in[0] <= 5 && this.context.computeOnClient(client -> client.player.onGround())) {
						this.context.getInput().holdKey(options -> options.keyJump);
						heldAt = i;
						jumped = true;
					}
				}

				double[] passing = this.on(s -> {
					Figure f = this.her(s);
					Vec3 at = StoryTesting.staffAt(f);
					Vec3 me = this.me(s).position();

					if (at == null || Math.hypot(at.x - me.x, at.z - me.z) > 0.9) {
						return null;
					}

					return new double[] {me.y - floor[0], at.y - floor[0], StoryTesting.staffLeg(f)};
				});

				if (passing != null) {
					int leg = (int) passing[2] - 1;

					if (leg == 0) {
						lowestFeet = Math.min(lowestFeet, passing[0]);
					}

					if (leg >= 0 && leg < 2) {
						staff[leg] = Math.min(staff[leg], passing[1]);
					}
				}

				if (!this.on(s -> StoryTesting.staffOut(this.her(s))) && i > 30) {
					break;
				}
			}

			this.context.getInput().releaseKey(options -> options.keyJump);
			int hit = this.on(s -> this.session(s).counters.getOrDefault("throw_hit", 0)) - hits;
			SaberCraft.LOGGER.info("PHASE29 THROW: low, {}: hit {} times; passing the player its lowest {} out, {} back above their feet' place; their feet at least {} up as it passed on its way out",
					jump ? "jumped over" : "standing", hit, staff[0] == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.2f", staff[0]),
					staff[1] == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.2f", staff[1]),
					lowestFeet == Double.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.2f", lowestFeet));
			if (jump) {
				check(jumped, "(the test) no moment to jump over her low throw");
				check(hit == 0, "jumped over at the right moment, her low throw still hits");
				check(staff[1] != Double.MAX_VALUE, "(the test) her low throw did not come back past the player");
				check(staff[1] == Double.MAX_VALUE || staff[1] > 2.4, "her low throw comes back low past the player (into them from behind)");
			} else {
				check(hit == 1, "her low throw does not hit one standing still once (" + hit + " times)");
			}
		}
	}

	/** Waits until her slam's wind-up has that many ticks left (its way set). */
	private void untilWindup(int left) {
		for (int i = 0; i < 60; i++) {
			if (this.on(s -> this.her(s).windup) <= left) {
				return;
			}

			this.context.waitTick();
		}
	}

	private void pressure() {
		if (!this.duel()) {
			return;
		}

		this.server.runOnServer(s -> {
			Figure f = this.her(s);
			StoryTesting.guard(f, 20);
			this.before(s, 2.6);
		});
		Pressure first = this.press(300);
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
		this.film(30);
		this.context.waitTicks(5);
		this.skip("boss_mid");
		this.server.runOnServer(s -> this.before(s, 2.6));
		Pressure second = this.press(600);
		SaberCraft.LOGGER.info("PHASE29 PRESSURE first phase: {} attacks in 15 s, longest calm {} s, moves {}, blows on the player {}", first.attacks(),
				first.calm() / 20.0, first.moves(), first.landed());
		SaberCraft.LOGGER.info("PHASE29 PRESSURE second phase: {} attacks in 30 s, longest calm {} s, moves {}, blows on the player {}", second.attacks(),
				second.calm() / 20.0, second.moves(), second.landed());
		check(first.attacks() >= 5, "in her first phase she attacks only " + first.attacks() + " times in 15 s");
		check(first.calm() <= 100, "in her first phase she does nothing for " + first.calm() / 20.0 + " s");
		check(second.attacks() >= 10, "in her second phase she attacks only " + second.attacks() + " times in 30 s");
		check(second.calm() <= 80, "in her second phase she does nothing for " + second.calm() / 20.0 + " s");
		long kinds = second.moves().entrySet().stream().filter(e -> e.getValue() > 0 && !"turn".equals(e.getKey())).count();
		int total = second.moves().values().stream().mapToInt(Integer::intValue).sum();
		int blades = second.moves().getOrDefault("blades", 0);
		check(kinds >= 4, "in her second phase she uses only " + kinds + " kinds of attack (" + second.moves() + ")");
		check(total == 0 || blades <= total * 0.8, "in her second phase her blade combos are nearly all she does (" + second.moves() + ")");
		check(second.landed() >= 4, "in 30 s of her second phase only " + second.landed() + " of her attacks reach a player standing still");
	}

	/** How she fought for a while: attacks begun, the longest stretch without one (ticks), her moves by name, blows that reached the player. */
	private record Pressure(int attacks, int calm, java.util.Map<String, Integer> moves, int landed) {
	}

	/** Watches her fight for that many ticks, the player standing still near her (put back near her now and then). */
	private Pressure press(int ticks) {
		int[] moves0 = StoryTesting.moves();
		int landed0 = this.on(s -> this.session(s).counters.getOrDefault("duel_hurt", 0) + this.session(s).counters.getOrDefault("blocked", 0));
		Set<String> attacking = Set.of("windup", "slam", "push", "rush", "leap", "throw");
		String was = "guard";
		int attacks = 0;
		int calm = 0;
		int longest = 0;

		for (int t = 0; t < ticks; t += 2) {
			this.context.waitTicks(2);
			String now = this.on(s -> this.session(s).has("tmp.duel") ? StoryTesting.act(this.her(s)) : "over");

			if ("over".equals(now)) {
				this.problem("the duel ended while she only fought a player standing still");
				break;
			}

			if (attacking.contains(now)) {
				if (!now.equals(was)) {
					attacks++;
				}

				calm = 0;
			} else {
				calm += 2;
				longest = Math.max(longest, calm);
			}

			was = now;

			if (t % 80 == 78) {
				this.server.runOnServer(s -> {
					double apart = this.session(s).local(Story.DESTROYER).distanceTo(this.her(s).position());

					if (apart > 4.5 || apart < 1.0) {
						Vec3 to = StoryTesting.openSpot(this.session(s), this.her(s), 2.6, new Vec3(40.5, H, -40.5));
						this.teleport(s, this.session(s), to, yaw(to, this.her(s).position()));
					}
				});
			}
		}

		int[] moves1 = StoryTesting.moves();
		java.util.Map<String, Integer> moves = new java.util.TreeMap<>();

		for (int m = 0; m < moves1.length; m++) {
			String name = StoryTesting.moveName(m);

			if (name != null && moves1[m] > moves0[m]) {
				moves.put(name, moves1[m] - moves0[m]);
			}
		}

		int landed = this.on(s -> this.session(s).counters.getOrDefault("duel_hurt", 0) + this.session(s).counters.getOrDefault("blocked", 0)) - landed0;
		return new Pressure(attacks, longest, moves, landed);
	}

	// --- 7 (5.5): the scene at half her strength -------------------------------------------------------------

	private void sceneInAir() {
		if (!this.duel()) {
			return;
		}

		// Half her strength reached with the player in the air: the scene waits until they land.
		this.server.runOnServer(s -> {
			this.calm(s, 40.5, -30.0, 180.0F);
			this.before(s, 3.0);
		});
		this.context.waitTicks(4);
		int[] b0 = this.on(s -> StoryTesting.boss29());
		int scenes = this.on(s -> StoryTesting.bossScenes());
		this.server.runOnServer(s -> {
			Vec3 me = this.session(s).local(Story.DESTROYER);
			this.teleport(s, this.session(s), me.add(0.0, 2.6, 0.0), this.me(s).getYRot());
		});
		this.context.waitTicks(3);
		check(this.on(this::airborne), "(the test) the player is not in the air");
		this.audit(true);
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
		check(this.on(s -> StoryTesting.scenePending(this.session(s))), "half her strength reached in the air: the scene does not wait for the player to land");
		check(this.on(s -> StoryTesting.bossScenes()) == scenes, "the scene at half her strength begins with the player in the air");
		boolean began = false;
		boolean grounded = false;

		for (int i = 0; i < 60 && !began; i++) {
			this.context.waitTick();

			if (this.on(s -> StoryTesting.bossSceneOn(this.session(s)))) {
				began = true;
				grounded = !this.on(this::airborne);
			}
		}

		check(began, "the scene at half her strength does not begin after the player landed");
		check(grounded, "the scene at half her strength begins with the player still in the air");
		check(this.on(s -> StoryTesting.boss29())[11] == b0[11] + 1, "the scene's wait for the landing is not counted");
		Shots shots = this.watchFilm("boss_mid", 200);

		if (shots != null) {
			this.cameraChecks("boss_mid (after a jump)", shots, -1.0, false);
			check(shots.longestAir() <= 30, "in the scene at half her strength the player hangs in the air for " + shots.longestAir() + " ticks");
		}

		check(this.on(s -> StoryTesting.phase(this.her(s))) == 2, "after the scene her second phase does not begin");

		// Kept in the air past the wait: set down on the floor first, then the scene.
		if (!this.duel()) {
			return;
		}

		this.server.runOnServer(s -> {
			this.calm(s, 40.5, -30.0, 180.0F);
			this.before(s, 3.0);
		});
		this.context.waitTicks(4);
		int[] c0 = this.on(s -> StoryTesting.boss29());
		Vec3 ground = this.on(s -> this.session(s).local(Story.DESTROYER));
		boolean pending = false;
		boolean beganAir = false;

		for (int i = 0; i < 70 && !beganAir; i++) {
			if (!beganAir) {
				this.server.runOnServer(s -> this.teleport(s, this.session(s), ground.add(0.0, 2.2, 0.0), this.me(s).getYRot()));
			}

			this.context.waitTick();

			if (i == 3) {
				this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
				pending = this.on(s -> StoryTesting.scenePending(this.session(s)));
			}

			beganAir = this.on(s -> StoryTesting.bossSceneOn(this.session(s)));
		}

		this.context.waitTicks(3);
		double height = this.on(s -> this.session(s).local(Story.DESTROYER).y) - ground.y;
		int[] c1 = this.on(s -> StoryTesting.boss29());
		SaberCraft.LOGGER.info("PHASE29 SCENE: kept in the air: waited {}, began {}, set down {} times, {} above the floor after", pending, beganAir, c1[12] - c0[12],
				String.format(Locale.ROOT, "%.2f", height));
		check(pending, "kept in the air, the scene at half her strength does not wait");
		check(beganAir, "kept in the air, the scene at half her strength never begins");
		check(c1[12] > c0[12] && Math.abs(height) < 0.3, "kept in the air past the wait, the player is not set down on the floor for the scene (" + height + " above it)");
		this.film(20);
		this.skip("boss_mid");
	}

	private void sceneSides() {
		for (String where : List.of("side", "behind", "wall")) {
			if (!this.duel()) {
				return;
			}

			boolean placed = this.on(s -> {
				Figure f = this.her(s);

				switch (where) {
					case "side" -> {
						this.calm(s, 40.5, -30.0, 0.0F);
						Vec3 look = f.look();
						Vec3 to = f.position().add(new Vec3(-look.z, 0.0, look.x).scale(2.0));
						this.teleport(s, this.session(s), to, yaw(to, f.position()));
					}
					case "behind" -> {
						this.calm(s, 40.5, -30.0, 0.0F);
						this.behind(s, 2.0);
					}
					default -> {
						// The player with their back to the hangar's north wall (west of its door), she before them.
						this.calm(s, 33.5, -19.6, 0.0F);
						Vec3 to = new Vec3(33.5, H, -17.5);

						if (!this.free(s, to)) {
							return false;
						}

						this.teleport(s, this.session(s), to, 180.0F);
					}
				}

				return true;
			});

			if (!placed) {
				this.problem("no free place by the hangar's north wall for the scene's test");
				continue;
			}

			this.context.waitTicks(4);
			int[] placed0 = this.on(s -> StoryTesting.scenePlaced());
			this.audit(true);
			this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
			this.context.waitTicks(3);
			check(this.on(s -> StoryTesting.bossSceneOn(this.session(s))), "the scene at half her strength does not begin with the player at her " + where);
			double[] setup = this.on(s -> {
				Figure f = this.her(s);
				Vec3 me = this.session(s).local(Story.DESTROYER);
				double apart = Math.hypot(me.x - f.x, me.z - f.z);
				double herOff = Math.abs(Mth.wrapDegrees(yaw(f.position(), me) - f.headYaw));
				double myOff = Math.abs(Mth.wrapDegrees(yaw(me, f.position()) - this.me(s).getYRot()));
				boolean clear = this.level(s).noCollision(this.me(s), this.me(s).getBoundingBox().deflate(0.01));
				return new double[] {apart, herOff, myOff, clear ? 1 : 0};
			});
			int[] placed1 = this.on(s -> StoryTesting.scenePlaced());
			SaberCraft.LOGGER.info("PHASE29 SCENE from her {}: {} apart, she {} degrees off the player, the player {} off her; placed {}", where,
					String.format(Locale.ROOT, "%.2f", setup[0]), String.format(Locale.ROOT, "%.0f", setup[1]), String.format(Locale.ROOT, "%.0f", setup[2]),
					java.util.Arrays.toString(new int[] {placed1[0] - placed0[0], placed1[1] - placed0[1], placed1[2] - placed0[2]}));
			check(Math.abs(setup[0] - StoryTesting.lockDistance()) < 0.45, "from her " + where + " the scene does not set the two at the lock's distance (" + setup[0] + ")");
			check(setup[1] < 20.0 && setup[2] < 20.0, "from her " + where + " the two do not face each other in the scene (" + setup[1] + ", " + setup[2] + ")");
			check(setup[3] > 0.5, "from her " + where + " the scene puts the player into something");
			Shots shots = this.watchFilm("boss_mid", 200);

			if (shots != null) {
				this.cameraChecks("boss_mid (from her " + where + ")", shots, -1.0, false);
			}
		}
	}

	// --- 3 (3): the duel's films: before it, her defeat by the wall -----------------------------------------

	private void defeatCamera() {
		// (The duel's film plays once a story: earlier parts saw it; the test goes back to before that.)
		this.enter(5, "m6.duel", "seen.duel_film");
		this.flags("saber.taken", "droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.audit(true);
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		String film = this.film(200);

		if ("duel".equals(film)) {
			Shots shots = this.watchFilm("duel", 300);

			if (shots != null) {
				this.cameraChecks("duel", shots, -1.0, false);
			}
		} else {
			this.audit(false);
			this.problem("the duel's film does not begin (" + film + ")");
		}

		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film");
			return;
		}

		this.converse();
		this.context.waitTicks(10);
		// Her defeat by the hangar's north wall, the last stroke from a jump.
		this.server.runOnServer(s -> {
			this.calm(s, 33.5, -18.6, 90.0F);
			this.teleport(s, this.session(s), new Vec3(35.6, H, -18.0), 90.0F);
			StoryTesting.lastStrength(this.session(s), this.her(s), this.me(s));
		});
		this.context.waitTicks(5);
		boolean jumped = false;

		for (int i = 0; i < 40 && this.on(s -> this.session(s).has("tmp.duel")); i++) {
			boolean jump = !jumped;
			this.server.runOnServer(s -> {
				Figure f = this.her(s);

				if (f.hostile && !"lock".equals(StoryTesting.action(f))) {
					this.before(s, 2.0);
					StoryTesting.open(f);

					if (jump) {
						Vec3 me = this.session(s).local(Story.DESTROYER);
						this.teleport(s, this.session(s), me.add(0.0, 0.9, 0.0), this.me(s).getYRot());
					}
				}
			});
			jumped = true;

			if (i == 0) {
				this.audit(true);
			}

			this.context.waitTicks(2);
			this.context.getInput().pressKey(options -> options.keyAttack);
			this.context.waitTicks(6);
		}

		if (this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("she is not beaten");
			this.audit(false);
			return;
		}

		Shots shots = this.watchFilm("duel_end", 400);

		if (shots != null) {
			this.cameraChecks("duel_end (by the wall)", shots, -1.0, false);
			check(shots.longestAir() <= 25, "after her defeat from a jump the player hangs in the air for " + shots.longestAir() + " ticks");
		}
	}

	// --- Helpers ------------------------------------------------------------------------------------------------------------

	/** Her: the Schattenklinge. */
	private Figure her(MinecraftServer s) {
		return this.session(s).figure("shadow");
	}

	/** She stands there on guard for a long while (no attack of hers in the way), facing that way, nothing going on. */
	private void calm(MinecraftServer s, double x, double z, float yaw) {
		Figure f = this.her(s);
		StoryTesting.guard(f, 4000);
		f.react = 0;
		f.reactTicks = 0;
		f.x = x;
		f.z = z;
		f.yaw = yaw;
		f.headYaw = yaw;
		f.speed = 0.0F;
	}

	/** On guard, quiet, the player that far before her (as they stood), she facing them and they her. */
	private void faced(MinecraftServer s, double distance) {
		Figure f = this.her(s);
		StoryTesting.guard(f, 4000);
		f.react = 0;
		f.reactTicks = 0;
		f.speed = 0.0F;
		this.before(s, distance);
		float yaw = com.phiro.sabercraft.interior.Crew.yaw(f.position(), this.session(s).local(Story.DESTROYER));
		f.yaw = yaw;
		f.headYaw = yaw;
	}

	/** Puts the player that far behind her (where she looks away from), facing her. */
	private void behind(MinecraftServer s, double distance) {
		Figure f = this.her(s);
		Vec3 look = f.look();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		Vec3 to = f.position().subtract(flat.scale(distance));
		this.teleport(s, this.session(s), to, yaw(to, f.position()));
	}

	/** A free place behind one of the story's people (that far, or a little nearer or to the side), or null. */
	private @Nullable Vec3 behindOf(MinecraftServer s, @Nullable Figure f, double distance) {
		if (f == null) {
			return null;
		}

		Vec3 look = f.look();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();

		for (double d : new double[] {distance, distance - 0.3, distance - 0.5}) {
			for (double turn : new double[] {0.0, 25.0, -25.0}) {
				double a = Math.toRadians(turn);
				Vec3 dir = new Vec3(flat.x * Math.cos(a) - flat.z * Math.sin(a), 0.0, flat.x * Math.sin(a) + flat.z * Math.cos(a));
				Vec3 at = f.position().subtract(dir.scale(d));

				if (this.free(s, at)) {
					return at;
				}
			}
		}

		return null;
	}

	/** Whether the player fits standing at that place (plot space), with floor under it. */
	private boolean free(MinecraftServer s, Vec3 local) {
		Vec3 world = local.add(Vec3.atLowerCornerOf(this.session(s).destroyer));
		return this.level(s).noCollision(new AABB(world.x - 0.3, world.y, world.z - 0.3, world.x + 0.3, world.y + 1.8, world.z + 0.3))
				&& !this.level(s).noCollision(new AABB(world.x - 0.3, world.y - 0.2, world.z - 0.3, world.x + 0.3, world.y - 0.01, world.z + 0.3));
	}

	/** A stroke with the attack key (the crosshair on her). */
	private void strike() {
		this.context.getInput().pressKey(options -> options.keyAttack);
		this.context.waitTicks(3);
	}

	/** The duel begun: the story at it, the films and her words skipped, the player with a saber and unhurt. */
	private boolean duel() {
		this.enter(5, "m6.duel");
		// (Both droids with the player, as at this point of the story: the test's file came from other parts.)
		this.flags("saber.taken", "droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped)");
			return false;
		}

		this.converse();
		this.context.waitTicks(10);
		return true;
	}

	/** Answers every line of the open dialog (the first answer) until it ends. */
	private void converse() {
		for (int i = 0; i < 80; i++) {
			if (this.on(s -> this.session(s) == null || StoryTesting.line(this.session(s)).isEmpty())) {
				return;
			}

			int answers = this.on(s -> StoryTesting.answers(this.session(s)).size());
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.ANSWER, "", answers > 0 ? 0 : -1));
			this.context.waitTicks(3);
		}

		this.problem("a dialog does not end: " + this.on(s -> StoryTesting.line(this.session(s))));
	}

	/** The film the client plays (waiting up to that many ticks for one to begin). */
	private @Nullable String film(int ticks) {
		for (int i = 0; i < ticks; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.filmForTest());

			if (film != null) {
				return film;
			}

			this.context.waitTick();
		}

		return null;
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
			String film = this.context.computeOnClient(client -> StoryCinema.filmForTest());

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

	/** Puts the player that far in front of her, facing her. */
	private void before(MinecraftServer s, double distance) {
		StorySession session = this.session(s);
		Figure f = session.figure("shadow");
		Vec3 her = f.position();
		Vec3 me = session.local(Story.DESTROYER);
		Vec3 away = new Vec3(me.x - her.x, 0.0, me.z - her.z);
		away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		Vec3 to = her.add(away.scale(distance));
		this.teleport(s, session, to, (float) Math.toDegrees(Math.atan2(away.x, -away.z)));
	}

	private static float yaw(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static float pitch(Vec3 from, Vec3 to) {
		double flat = Math.hypot(to.x - from.x, to.z - from.z);
		return (float) -Math.toDegrees(Math.atan2(to.y - from.y, flat));
	}

	private static String read(Minecraft client, String path) {
		return client.getResourceManager().getResource(SaberCraft.id(path)).map(resource -> {
			try (BufferedReader reader = resource.openAsReader()) {
				return reader.lines().collect(Collectors.joining("\n"));
			} catch (java.io.IOException e) {
				return "";
			}
		}).orElse("");
	}

	/** Continues the story at that step (a file saved there), as soon as it is there. */
	private void enter(int mission, String step, String... forget) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		if (forget.length > 0) {
			this.server.runOnServer(s -> StoryTesting.forget(s, this.self, forget));
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

	/** Whether nothing is right under the player's feet (by the world, not by the on-ground flag their game sent). */
	private boolean airborne(MinecraftServer s) {
		ServerPlayer player = this.me(s);
		net.minecraft.world.phys.AABB box = player.getBoundingBox();
		return player.level().noCollision(player, new net.minecraft.world.phys.AABB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY, box.maxZ));
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

	/** Puts the player at a place of the Star Destroyer of the story (plot space). */
	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	/** Puts the player there at once, on the server (plot space). */
	private void teleport(MinecraftServer server, StorySession session, Vec3 local, float yaw) {
		ServerPlayer player = this.me(server);
		Vec3 world = local.add(Vec3.atLowerCornerOf(session.destroyer));
		player.teleportTo(server.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, java.util.Set.of(), yaw, 0.0F, true);
	}

	private ServerLevel level(MinecraftServer s) {
		return s.getLevel(ShipInteriors.DIMENSION);
	}

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE29 CHECK FAILED: {}", problem);
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
