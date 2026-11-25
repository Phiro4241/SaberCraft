package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/**
 * Phase 19: what the game tests may look at and do in a story session (the story's own workings
 * stay package-private). Nothing here is used by the game itself.
 */
public final class StoryTesting {
	private StoryTesting() {
	}

	/** The open dialog and line ({@code m1_briefing.situation}), or empty. */
	public static String line(StorySession s) {
		return s.dialog != null && s.line != null ? s.dialog.id + "." + s.line : "";
	}

	/** The ids of the answers shown now (empty: the line goes on by itself). */
	public static List<String> answers(StorySession s) {
		List<String> list = new ArrayList<>();
		Dialog.Node node = s.dialog != null && s.line != null ? s.dialog.node(s.line) : null;

		if (node != null) {
			for (Dialog.Answer answer : node.shown(s)) {
				list.add(answer.id());
			}
		}

		return list;
	}

	/** Picks an answer (-1: goes on), as the client's choice does. */
	public static void answer(StorySession s, int index) {
		s.answer(index);
		s.afterTick();
	}

	/** The step now ({@code m3.card}). */
	public static String step(StorySession s) {
		return s.step().id();
	}

	/** Nobody sees the player for that long (a test walking the story through), or again at once (0). */
	public static void unseen(StorySession s, int ticks) {
		s.grace = ticks;
		// Phase 27: also no bearings after a checkpoint (the test decides).
		s.shield = 0;
	}

	/** Phase 27: the bearings after a checkpoint left (ticks; 0 once over). */
	public static int shield(StorySession s) {
		return s.shield;
	}

	/** Phase 27: the place kept with the checkpoint set to that (as a save from before kept it). */
	public static void keptPlace(StorySession s, String ship, Vec3 at, float yaw) {
		s.progress.placeShip = ship;
		s.progress.placeX = at.x;
		s.progress.placeY = at.y;
		s.progress.placeZ = at.z;
		s.progress.placeYaw = yaw;
	}

	/** Phase 27: a safe checkpoint now, if the chapter makes one where the player is. */
	public static void safeCheckpointNow(StorySession s) {
		Chapter1.safeCheckpointForTest(s);
	}

	/** Where the marker points now (world space), or null. */
	public static @Nullable Vec3 marker(StorySession s) {
		return Story.marker(s);
	}

	/** A thing to use by its id (world space centre), or null when it is not there now. */
	public static @Nullable Vec3 point(StorySession s, String id) {
		for (Mission.Point point : s.chapter.points(s)) {
			if (point.id().equals(id)) {
				return point.box().getCenter().add(Vec3.atLowerCornerOf(s.origin(point.ship())));
			}
		}

		return null;
	}

	/** Every key the dialogs of the chapters use (lines and answers), to check both languages. */
	public static List<String> dialogKeys() {
		List<String> keys = new ArrayList<>();

		for (Dialog dialog : Chapter1Dialogs.all()) {
			for (Dialog.Node node : dialog.nodes.values()) {
				keys.add(node.key(dialog));

				for (Dialog.Answer answer : node.answers()) {
					keys.add(answer.key(dialog, node));
				}
			}
		}

		return keys;
	}

	/** Every other key the chapter uses: missions, objectives, things to use, speakers. */
	public static List<String> otherKeys() {
		List<String> keys = new ArrayList<>();

		for (Mission mission : Chapter1.INSTANCE.missions()) {
			keys.add(mission.title());

			for (Mission.Step step : mission.steps()) {
				keys.add(step.objective());
			}

			for (Mission.Optional optional : mission.optional()) {
				keys.add(optional.objective());
			}
		}

		for (Dialog dialog : Chapter1Dialogs.all()) {
			for (Dialog.Node node : dialog.nodes.values()) {
				keys.add("sabercraft.story.name." + node.speaker());
			}
		}

		// Phase 29: the things to use too (the cover's and the toolbox's words were missing), and
		// the plain words shown for a thing whose words are missing.
		for (String id : pointIds()) {
			keys.add(Mission.Point.LABEL_PREFIX + id);
		}

		keys.add(Mission.Point.LABEL_PREFIX + "generic");
		return keys;
	}

	/** Phase 29: every thing to use the chapter declares (Chapter1's class is loaded first: it declares them). */
	public static java.util.Set<String> pointIds() {
		Chapter1.INSTANCE.id();
		return Mission.Point.declaredForTest();
	}

	/** Phase 29: things used in the story so far that were never declared (their words may be missing). */
	public static java.util.Set<String> undeclaredPoints() {
		return Mission.Point.undeclaredForTest();
	}

	/** In the finale: the fighter is open (as after a blocked blow), so the next stroke hits. */
	public static void open(Figure figure) {
		figure.action = Finale.OPEN_UP;
		figure.attack = Finale.STAGGER;
		figure.timer = Finale.OPEN;
		figure.windup = 0;
		figure.hits = 0;
	}

	/** How fast that figure's suspicion of the player fills now (a second); 0 when they do not see them. */
	public static float sees(StorySession s, Figure figure) {
		net.minecraft.server.level.ServerPlayer player = s.player();
		net.minecraft.server.level.ServerLevel level = Chapter1.interiors(s);

		if (player == null || level == null || s.here == null) {
			return 0.0F;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		return Stealth.sees(s, figure, player, player.position().subtract(corner), level, corner, s.here);
	}

	/** Whether someone watching would stop the player using a thing in secret now. */
	public static boolean watched(StorySession s) {
		return Chapter1.watched(s);
	}

	/** The fighter nearest a point (plot space), or null. */
	public static @Nullable Figure fighter(StorySession s, Vec3 at) {
		return Finale.nearestFighter(s, at);
	}

	// --- Phase 20: checkpoints ------------------------------------------------------------------------

	/** Caught now (whatever the grace after the last capture): back to the last checkpoint. */
	public static void capture(StorySession s) {
		s.grace = 0;
		Story.capture(s, null);
	}

	/** Whether everything a checkpoint keeps (step, flags, journal) is as the last one kept it. */
	public static boolean atCheckpoint(StorySession s) {
		return !s.unsaved();
	}

	/** How many checkpoints this session made, and how often it went back to one. */
	public static int checkpoints(StorySession s) {
		return s.checkpoints;
	}

	public static int restores(StorySession s) {
		return s.restores;
	}

	/** What the last checkpoint kept: the step's id, the flags, the journal. */
	public static String kept(StorySession s) {
		return s.progress.stepId + " " + new java.util.TreeSet<>(s.progress.flags) + " " + s.progress.journal;
	}

	/** What the session knows now, as {@link #kept} (temporary flags left out). */
	public static String known(StorySession s) {
		java.util.TreeSet<String> flags = new java.util.TreeSet<>();

		for (String flag : s.flags) {
			if (!flag.startsWith("tmp.")) {
				flags.add(flag);
			}
		}

		return s.step().id() + " " + flags + " " + s.journal;
	}

	/** The last checkpoint's place (plot space) and ship, or null when it has none. */
	public static @Nullable Vec3 checkpointPlace(StorySession s) {
		return s.progress.placeShip.isEmpty() ? null : new Vec3(s.progress.placeX, s.progress.placeY, s.progress.placeZ);
	}

	public static String checkpointShip(StorySession s) {
		return s.progress.placeShip;
	}

	/** Phase 20: that one has seen enough of the player (as if the suspicion had filled): what they do then. */
	public static void detect(StorySession s, Figure figure) {
		figure.suspicion = 1.0F;
		figure.stage = Figure.DETECTED;
		figure.seenAt = s.local(figure.ship);
		figure.lastSeen = s.time();
		Story.detected(s, figure);
	}

	/** Phase 20: the alarm ends in a moment (as after the search). */
	public static void endAlarm(StorySession s) {
		if (s.alarm > 2) {
			s.alarm = 2;
		}
	}

	/** Phase 20: whether a chaser has reached the player now ("Hands up!", the capture a moment later). */
	public static boolean cornered(StorySession s) {
		return s.cornered > 0;
	}

	/** Phase 20: the way a figure follows now (plot space). */
	public static List<Vec3> path(Figure figure) {
		return List.copyOf(figure.path);
	}

	/** Phase 20: whether the ship is on its emergency lights now. */
	public static boolean emergency(StorySession s) {
		return s.chapter.emergency(s);
	}

	/** How suspicious the most suspicious one is now (0..1). */
	public static float suspicion(StorySession s) {
		return s.suspicion;
	}

	/**
	 * Makes the player's saved file look as Phase 19 wrote it: only the numbers of the mission and
	 * the step (no step id, no place), the old format. The player must be out of the story.
	 */
	public static void phase19File(net.minecraft.server.MinecraftServer server, java.util.UUID player, int mission, int step) {
		StoryProgress progress = StoryStore.load(server, player);
		progress.format = 1;
		progress.mission = mission;
		progress.step = step;
		progress.stepId = "";
		progress.placeShip = "";
		StoryStore.save(server, player, progress);
	}

	// --- Phase 21 ----------------------------------------------------------------------------------

	/** Phase 21: every mission open to choose (the file says all were played). The player must be out of the story. */
	public static void unlockAll(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
		StoryProgress progress = StoryStore.load(server, player);

		for (int i = 0; i < 6; i++) {
			progress.completed.add(i);
		}

		StoryStore.save(server, player, progress);
	}

	// --- Phase 22 ----------------------------------------------------------------------------------

	/**
	 * Phase 22: the player's file as the end of the chapter leaves it: every mission played, the
	 * chapter finished, at the epilogue in the Falcon, and known to be over. Out of the story only.
	 */
	public static void finishedFile(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
		StoryProgress progress = StoryStore.load(server, player);

		for (int i = 0; i < 6; i++) {
			progress.completed.add(i);
		}

		progress.mission = 5;
		progress.step = 0;
		progress.stepId = "m6.epilogue";
		progress.placeShip = "";
		progress.finished = true;
		progress.flags.add(Chapter1.CHAPTER_DONE);
		StoryStore.save(server, player, progress);
	}

	/** Phase 22: how many blaster bolts were sent back to their shooter (and how many of those hit him). */
	public static int returnedBolts() {
		return Finale.returned;
	}

	/** Phase 21: a noise there (plot space) as M-31 makes it: who went to see, who looked, who did not care. */
	public static int[] noise(StorySession s, Vec3 at) {
		net.minecraft.server.level.ServerLevel level = Chapter1.interiors(s);

		if (level == null || s.here == null) {
			return new int[3];
		}

		Stealth.Noise noise = Stealth.noise(s, s.here, at, level, Vec3.atLowerCornerOf(s.origin(s.here)));
		return new int[] {noise.went().size(), noise.looked().size(), noise.ignored().size()};
	}

	/** Phase 21: how many stun bolts the troopers fired, and how many hit. */
	public static int stunShots() {
		return Combat.shotsForTest();
	}

	/** Phase 21: how wary the ship is in this mission (0..3), whether they search, the search patrols there are. */
	public static int wary(StorySession s) {
		return Stealth.level(s);
	}

	public static boolean searching(StorySession s) {
		return s.searching;
	}

	public static int patrols(StorySession s) {
		int count = 0;

		for (Figure figure : s.figures) {
			if (figure.key.startsWith("patrol.")) {
				count++;
			}
		}

		return count;
	}

	/** Phase 21: the place a droid keeps to near its player in the story now (world space), or null. */
	public static @Nullable Vec3 droidSpot(com.phiro.sabercraft.droid.DroidEntity droid, net.minecraft.server.level.ServerPlayer owner) {
		com.phiro.sabercraft.droid.DroidEntity.Spot spot = Companions.spot(droid, owner);
		return spot != null ? spot.at() : null;
	}

	/** Phase 21: the duel. He begins a blow of that kind (1 overhead, 2 sweep, 3 thrust), the last of its combo. */
	public static void blow(Figure figure, int kind, int ticks) {
		figure.action = Finale.WINDUP;
		figure.attack = kind;
		figure.windup = ticks;
		figure.timer = ticks;
		figure.combo = 1;
	}

	/** Phase 21: he is on guard, and will not go in for that long. */
	public static void guard(Figure figure, int ticks) {
		figure.action = Finale.GUARD;
		figure.attack = 0;
		figure.windup = 0;
		figure.timer = 0;
		figure.cooldown = ticks;
		figure.parried = 0;
	}

	/** Phase 21: what he does now: guard, windup, open, leap, push, lock (Phase 23), recoil, escape, clash (Phase 23). */
	public static String action(Figure figure) {
		return switch (figure.action) {
			case Finale.WINDUP -> "windup";
			case Finale.OPEN_UP -> "open";
			case Finale.LEAP -> "leap";
			case Finale.PUSH -> "push";
			case Finale.LOCK -> "lock";
			case Finale.RECOIL -> "recoil";
			case Finale.ESCAPE -> "escape";
			case Finale.CLASHED -> "clash";
			case Finale.RING_START -> "ring_up";
			default -> "guard";
		};
	}

	public static int phase(Figure figure) {
		return Finale.phase(figure);
	}

	/** Phase 21: how the keys of reinforcements begin. */
	public static String reinforcementPrefix() {
		return Stealth.REINFORCEMENT;
	}

	public static int ripostes() {
		return Finale.ripostesForTest();
	}

	public static int foci() {
		return Finale.fociForTest();
	}

	/** What a light stroke takes of her on Normal (Phase 27: she is stronger). */
	public static float hit() {
		return Finale.HIT / Finale.STRENGTH;
	}

	/** Phase 27: what a light stroke takes of her at that difficulty. */
	public static float hit(StoryDifficulty difficulty) {
		return Finale.HIT / (Finale.STRENGTH * difficulty.bossHealth);
	}

	public static float phaseTwo() {
		return Finale.PHASE_TWO;
	}

	// --- Phase 23 ----------------------------------------------------------------------------------

	/** Phase 23: counters of the duel since the game started: clashes, perfect parries, dodges, locks, disarms. */
	public static int[] duelCounts() {
		return Finale.countsForTest();
	}

	/** Phase 23: where the Schattenklinge's saber lies after the duel (plot space), or null. */
	public static @Nullable Vec3 dropped(StorySession s) {
		return s.dropped;
	}

	/** Phase 23: what a figure's body does now (the reaction's kind, 0 = nothing). */
	public static int react(Figure figure) {
		return figure.react;
	}

	/** Phase 23: whether that one can fight (armed, or a guard): only they are drawn into a fight. */
	public static boolean combatant(Figure figure) {
		return Combat.combatant(figure);
	}

	/** Phase 23: whether that one is drawn into the fight (a fighter now, from an old chase or search). */
	public static boolean engaged(Figure figure) {
		return figure.engaged;
	}

	/** Phase 23: whether that one carries a pistol (an officer: he fights, and calls for help over his comlink). */
	public static boolean pistol(Figure figure) {
		return Combat.pistol(figure);
	}

	/** Phase 23: a stroke at that figure, light or heavy, as if the player struck now. */
	public static void stroke(StorySession s, boolean heavy) {
		s.lastStroke = heavy ? -1000L : s.time();
	}

	/** Phase 23: the stolen armour put on (Mission 3's locker), as if the player took it now. */
	public static void putOnArmour(StorySession s) {
		Chapter1.putOnArmour(s);
	}

	/** Phase 23: Ilyra's saber taken (Mission 6, in the lab), as if the player took it now. */
	public static void takeSaber(StorySession s) {
		Chapter1.takeSaber(s);
	}

	/** Phase 23: the step now done (as a dialog or a use would finish it). */
	public static void finishStep(StorySession s) {
		Chapter1.done(s, s.step().id());
	}

	/**
	 * Phase 23: the way someone of the crew would walk in the Star Destroyer (plot space) from one
	 * place to another, as the story's people find it: the points, and as the last element a point
	 * whose x is 1 when the way reaches the goal (0 when it ends where one comes nearest).
	 */
	public static List<Vec3> way(StorySession s, Vec3 from, Vec3 to) {
		net.minecraft.server.level.ServerLevel level = Chapter1.interiors(s);

		if (level == null) {
			return List.of();
		}

		Paths.Way way = Paths.way(level, Vec3.atLowerCornerOf(s.destroyer), from, to, 1.8);
		List<Vec3> list = new ArrayList<>(way.points());
		list.add(new Vec3(way.reaches() ? 1.0 : 0.0, 0.0, 0.0));
		return list;
	}

	/** Phase 23: how many officers called for help over the comlink since the game started. */
	public static int officerCalls() {
		return Story.officerCallsForTest();
	}

	/** Phase 23: the troopers' bolts in a fight since the game started. */
	public static int fightShots() {
		return Finale.shotsForTest();
	}

	/**
	 * Phase 23: the player's file at that step of that mission (every mission before it played),
	 * as a checkpoint there saves it. Out of the story only.
	 */
	public static void fileAt(net.minecraft.server.MinecraftServer server, java.util.UUID player, int mission, String stepId) {
		StoryProgress progress = StoryStore.load(server, player);

		for (int i = 0; i < mission; i++) {
			progress.completed.add(i);
		}

		progress.mission = mission;
		progress.step = 0;
		progress.stepId = stepId;
		progress.placeShip = "";
		progress.finished = false;
		progress.flags.remove(Chapter1.CHAPTER_DONE);

		if (Chapter1.PRISON_STEPS.contains(stepId)) {
			// Phase 31: a step of the detention block by file is the way without Hale's trap: with the
			// trap's flag (left by a test before) it would read as an older version's break-out and
			// be put back into the cell. The trap is played, never set by file.
			progress.flags.remove("m5.trapped");
		}

		StoryStore.save(server, player, progress);
	}

	// --- Phase 24 ------------------------------------------------------------------------------------

	/**
	 * Phase 24: where a player can get to on foot in a plot (plot space), from these places: walking,
	 * stepping up, jumping up to 1.25 blocks, dropping down (up to 40 blocks); not through lifts.
	 * Each place found: {x, z, feet height} (the middle of its block column). Capped at {@code limit}.
	 */
	public static List<double[]> reachable(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin, List<Vec3> starts, int limit) {
		return reachable(level, origin, starts, limit, false);
	}

	/**
	 * Phase 24: as above; with {@code gaps} also running jumps over gaps two and three blocks wide
	 * (level or a block up), a generous model of what a player can do.
	 */
	public static List<double[]> reachable(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin, List<Vec3> starts, int limit,
			boolean gaps) {
		java.util.Map<Long, double[]> seen = new java.util.HashMap<>();
		java.util.ArrayDeque<double[]> open = new java.util.ArrayDeque<>();
		Vec3 corner = Vec3.atLowerCornerOf(origin);

		for (Vec3 start : starts) {
			Double feet = feet(level, corner, (int) Math.floor(start.x), (int) Math.floor(start.z), start.y + 0.6, 3.0);

			if (feet != null) {
				double[] cell = {Math.floor(start.x), Math.floor(start.z), feet};

				if (seen.putIfAbsent(key(cell), cell) == null) {
					open.add(cell);
				}
			}
		}

		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

		while (!open.isEmpty() && seen.size() < limit) {
			double[] cell = open.poll();
			int x = (int) cell[0];
			int z = (int) cell[1];
			double h = cell[2];

			for (int[] d : dirs) {
				int nx = x + d[0];
				int nz = z + d[1];

				for (double up : new double[] {0.0, 0.6, 1.25}) {
					// Room to rise here (for a step or a jump), room there at that height, the way between free.
					if (up > 0.0 && !free(level, corner, x, z, h + up)) {
						continue;
					}

					if (!free(level, corner, nx, nz, h + up) || !freeBetween(level, corner, x, z, nx, nz, h + up)) {
						continue;
					}

					Double feet = feet(level, corner, nx, nz, h + up, 40.0);

					if (feet != null) {
						double[] next = {nx, nz, feet};

						if (seen.putIfAbsent(key(next), next) == null) {
							open.add(next);
						}
					}

					break;
				}

				if (!gaps) {
					continue;
				}

				// A running jump: over two or three cells, the way free at the jump's height all along.
				for (int far = 2; far <= 3; far++) {
					for (double up : new double[] {0.0, 1.0}) {
						if (far == 3 && up > 0.0 || !free(level, corner, x, z, h + 1.25)) {
							continue;
						}

						boolean clear = true;

						for (int k = 1; k <= far && clear; k++) {
							clear = free(level, corner, x + d[0] * k, z + d[1] * k, h + 1.25)
									&& freeBetween(level, corner, x + d[0] * (k - 1), z + d[1] * (k - 1), x + d[0] * k, z + d[1] * k, h + 1.25);
						}

						if (!clear) {
							continue;
						}

						int fx = x + d[0] * far;
						int fz = z + d[1] * far;
						Double feet = feet(level, corner, fx, fz, h + Math.max(up, 1.25) - 0.001, 40.0);

						if (feet != null && feet >= h + up - 0.6) {
							double[] next = {fx, fz, feet};

							if (seen.putIfAbsent(key(next), next) == null) {
								open.add(next);
							}
						}
					}
				}
			}
		}

		return new ArrayList<>(seen.values());
	}

	private static long key(double[] cell) {
		return ((long) cell[0] & 0xFFFFL) << 40 | ((long) cell[1] & 0xFFFFL) << 20 | (long) Math.round(cell[2] * 8.0) & 0xFFFFFL;
	}

	private static net.minecraft.world.phys.AABB body(Vec3 corner, int x, int z, double feet) {
		return new net.minecraft.world.phys.AABB(corner.x + x + 0.2, corner.y + feet + 0.001, corner.z + z + 0.2, corner.x + x + 0.8, corner.y + feet + 1.8,
				corner.z + z + 0.8);
	}

	private static boolean free(net.minecraft.server.level.ServerLevel level, Vec3 corner, int x, int z, double feet) {
		return level.noCollision(body(corner, x, z, feet));
	}

	private static boolean freeBetween(net.minecraft.server.level.ServerLevel level, Vec3 corner, int x, int z, int nx, int nz, double feet) {
		net.minecraft.world.phys.AABB a = body(corner, x, z, feet);
		net.minecraft.world.phys.AABB b = body(corner, nx, nz, feet);
		return level.noCollision(a.minmax(b));
	}

	/** The feet's height (plot space) of one standing in that column, falling from {@code from} at most {@code drop}; null: no room or no ground. */
	private static @Nullable Double feet(net.minecraft.server.level.ServerLevel level, Vec3 corner, int x, int z, double from, double drop) {
		net.minecraft.world.phys.AABB box = body(corner, x, z, from);

		if (!level.noCollision(box)) {
			return null;
		}

		List<net.minecraft.world.phys.shapes.VoxelShape> shapes = new ArrayList<>();
		level.getBlockCollisions(null, box.expandTowards(0.0, -drop, 0.0)).forEach(shapes::add);
		double moved = net.minecraft.world.phys.shapes.Shapes.collide(net.minecraft.core.Direction.Axis.Y, box, shapes, -drop);

		if (moved <= -drop + 1.0E-4) {
			return null;
		}

		return from + moved;
	}

	/** Phase 24: the name of the block one stands on there (plot space feet). */
	public static String ground(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin, double[] cell) {
		net.minecraft.core.BlockPos below = origin.offset((int) cell[0], (int) Math.floor(cell[2] - 0.01), (int) cell[1]);
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(below).getBlock()).getPath();
	}

	/** Phase 24: the guard of the detention block overpowered from behind (as the player's use would). */
	public static void takedown(StorySession s) {
		Chapter1.takedown(s);
	}

	public static int takedowns() {
		return Chapter1.takedownsForTest();
	}

	/**
	 * Phase 31: as {@link #takedown(StorySession)}, crouching (a takedown asks for it): the player
	 * crouches for that moment only (the client did not press the key, so it would not tell the
	 * server to stand up again).
	 */
	public static void takedownCrouched(StorySession s) {
		net.minecraft.server.level.ServerPlayer player = s.player();

		if (player == null) {
			return;
		}

		player.setShiftKeyDown(true);

		try {
			Chapter1.takedown(s);
		} finally {
			player.setShiftKeyDown(false);
		}
	}

	/** Phase 31: that one overpowered (as the player's use key on him would); returns whether the scene began. */
	public static boolean takedown(StorySession s, Figure figure) {
		return Chapter1.overpower(s, figure);
	}

	/** Phase 31: whether that one can be overpowered now (who he is, how unaware), and from where the player stands. */
	public static boolean takedownable(StorySession s, Figure figure) {
		return Chapter1.takedownable(s, figure);
	}

	public static boolean takedownReady(StorySession s, Figure figure) {
		return s.chapter.takedownReady(s, figure, s.local(Story.DESTROYER));
	}

	/** Phase 31: the player crouches (as holding the sneak key would; the server is told so with the next input). */
	public static void crouch(net.minecraft.server.level.ServerPlayer player, boolean crouch) {
		player.setShiftKeyDown(crouch);
	}

	/** Phase 31: the field of Hale's cell switched off (as M-31's work at its emitter ends). */
	public static void openCell(StorySession s) {
		s.set("cell2.open");
		Chapter1.field(s, 1, true);
	}

	/** Phase 31: whether the story holds the cell's checkpoint now (the break-out from Hale's cell). */
	public static boolean prisonBreak(StorySession s) {
		return Chapter1.prisonBreak(s);
	}

	/** Phase 31: the takedown's flags (known how, where one lies), and how often the tip was given. */
	public static String knowTakedown() {
		return Chapter1.KNOW_TAKEDOWN;
	}

	public static String downPrefix() {
		return Chapter1.DOWN_PREFIX;
	}

	public static int takedownTips() {
		return Chapter1.takedownTipsForTest();
	}

	/** Phase 31: the ticks until the detention block's guard looks to the door again (0 while he does). */
	public static long detGuardDoorIn(StorySession s) {
		Figure guard = s.figure("det_guard");

		if (guard == null) {
			return 0L;
		}

		long cycle = Chapter1.GUARD_DOOR_TICKS + Chapter1.GUARD_ROOM_TICKS + 2L * Chapter1.GUARD_TURN_TICKS;
		long t = Math.floorMod(s.time() + guard.id * 37L, cycle);
		return t < Chapter1.GUARD_DOOR_TICKS - 40 ? 0L : cycle - t + 4L;
	}

	/** Phase 24: the blade lock's scene (how often it began), the escape film, shudders of the failing reactor, wings kept. */
	public static int bossScenes() {
		return Finale.bossScenesForTest();
	}

	public static int escapes() {
		return Chapter1.escapesForTest();
	}

	public static int shudders() {
		return Chapter1.shuddersForTest();
	}

	public static int instability(StorySession s) {
		return Chapter1.instabilityLevel(s);
	}

	/** Phase 24: whether one is after the player now (seen lately, alarm or fight, no search). */
	public static boolean pursued(StorySession s) {
		return s.pursued();
	}

	/** Phase 24: a film skipped by the player (as the client's skip). */
	public static void skip(StorySession s, String film) {
		if (s.chapter instanceof Chapter1 chapter) {
			chapter.skipFilm(s, film);
		}
	}

	/** Phase 24: the duel's lock: as when her strength falls to half (the scene begins). */
	public static void halfStrength(StorySession s, Figure figure, net.minecraft.server.level.ServerPlayer player) {
		figure.health = Finale.PHASE_TWO + 0.01F;
		s.lastStroke = s.time();
		Finale.bossHitForTest(s, figure, player);
	}

	// --- Phase 25 ----------------------------------------------------------------------------------

	/** Phase 25: the short scenes aboard begun and skipped since the game started. */
	public static int scenesStarted() {
		return Scenes.startedForTest();
	}

	public static int scenesSkipped() {
		return Scenes.skippedForTest();
	}

	/** Phase 25: the maintenance door's emergency release pulled, and the door bolted, since the game started. */
	public static int releases() {
		return Scenes.releasesForTest();
	}

	public static int bolts() {
		return Scenes.boltsForTest();
	}

	/** Phase 25: whether a scene aboard runs now (and which: {@code tmp.scene.<name>}). */
	public static boolean scene(StorySession s, String film) {
		return Scenes.running(s, film);
	}

	/** Phase 25: the emergency release clicked, the bolt panel clicked (as the player's use). */
	public static void release(StorySession s) {
		Scenes.release(s);
	}

	public static void bolt(StorySession s) {
		Scenes.bolt(s);
	}

	/** Phase 25: "take it" in the saber's talk: the scene begins (as the answer does). */
	public static void saberScene(StorySession s) {
		Scenes.saberBegin(s);
	}

	/** Phase 25: her throws since the game started: begun, hits, blocked, dodged; and spins begun. */
	public static int[] throwsCount() {
		return Finale.throwsForTest();
	}

	/** Phase 25: she throws her staff now (from where she stands, at the player). */
	public static void throwNow(StorySession s, Figure figure) {
		Finale.throwForTest(s, figure);
	}

	/** Phase 25: she spins now (a blow with the staff round her). */
	public static void spinNow(StorySession s, Figure figure) {
		Finale.spinForTest(s, figure);
	}

	/** Phase 30: whether her spin's blades whirl round her now (the red circle hitting whoever is in it). */
	public static boolean spinLive(Figure figure) {
		return Finale.spinLive(figure);
	}

	/**
	 * Phase 30: her spin's reach (the circle's radius: the player's middle within it is hit, as the
	 * drawn circle shows), how long it spins up and how long it hits (ticks).
	 */
	public static double[] spinNumbers() {
		return new double[] {Finale.SPIN_REACH, Finale.SPIN_RISE, Finale.SPIN_LIVE};
	}

	/** Phase 30: how long the maintenance door's release takes (ticks). */
	public static int releaseTicks() {
		return Scenes.RELEASE;
	}

	/** Phase 25: whether her staff is in flight now. */
	public static boolean staffOut(Figure figure) {
		return figure.action == Finale.THROW && figure.windup <= 0 && figure.throwFrom != null;
	}

	// --- Phase 26 ----------------------------------------------------------------------------------

	/** Phase 26: M-31 at the cell's field since the game started: {begun, ended with the field off}. */
	public static int[] cellField() {
		return new int[] {CellField.startedForTest(), CellField.finishedForTest()};
	}

	/** Phase 26: the field's taking down now: 0 none, 1 M-31 on its way, 2 at work. */
	public static int cellFieldStage(StorySession s) {
		return s.has(CellField.WORKING) ? 2 : s.has(CellField.GOING) ? 1 : 0;
	}

	/** Phase 26: where M-31 works on the field (plot space), and how long it works (ticks). */
	public static Vec3 cellFieldPanel() {
		return CellField.PANEL;
	}

	public static int cellFieldWork() {
		return CellField.WORK_TICKS;
	}

	/** Phase 26: where Kestrel waits at the crawl way out of the detention block (plot space). */
	public static Vec3 kestrelAtVent() {
		return Chapter1.KESTREL_AT_VENT;
	}

	/** Phase 26: the lab's guard: his post (plot space), the mark that he was overpowered. */
	public static Vec3 labGuardPost() {
		return Chapter1.LAB_GUARD;
	}

	public static String labGuardDown() {
		return Chapter1.LAB_GUARD_DOWN;
	}

	/** Phase 27: the figures made again for the step as it is now (as at a checkpoint). */
	public static void refigure(StorySession s) {
		s.rebuildFigures();
	}

	/** Phase 27: the difficulty kept in the player's story file. */
	public static String fileDifficulty(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
		return StoryStore.load(server, player).difficulty;
	}

	/** Phase 27: places of the story (plot space): the hideout's opening from the crawl way, the checkpoint with the stick, the safe places by the cells. */
	public static Vec3 hideoutMouth() {
		return Chapter1.HIDEOUT_MOUTH.at();
	}

	public static Vec3 crateSafe() {
		return Chapter1.CRATE_SAFE;
	}

	public static Vec3 consoleSafe() {
		return Chapter1.CONSOLE_SAFE;
	}

	/** Phase 27: whether a place (plot space) is in one of the Star Destroyer's crawl ways; where a checkpoint there would be kept (null: none). */
	public static boolean inVent(Vec3 at) {
		return Chapter1.inVent(at);
	}

	public static @Nullable Vec3 crawlAnchor(Vec3 at) {
		return Chapter1.crawlAnchor(at);
	}

	/** Phase 27: how many saved places were not trusted on a restore (the player put at the step's own place instead). */
	public static int untrusted() {
		return Story.untrustedForTest();
	}

	/** Phase 27: the lab guard's looks: how many, the yaw of one, and ticks until he holds that one next. */
	public static int labLooks() {
		return Chapter1.LAB_WATCH.length;
	}

	public static float labLookYaw(int look) {
		return Chapter1.LAB_WATCH[look][0];
	}

	public static int labLookIn(StorySession s, int look) {
		Figure guard = s.figure("lab_guard");
		long now = s.time();
		return (int) (Chapter1.labWatchAt(now, look, guard == null ? 0 : guard.id) - now);
	}

	/** Phase 27: how fast that figure's suspicion fills if it looked that way now (its look put back after). */
	public static float seesLooking(StorySession s, Figure figure, float yaw) {
		float yaw0 = figure.yaw;
		float head0 = figure.headYaw;
		figure.yaw = yaw;
		figure.headYaw = yaw;
		float rate = sees(s, figure);
		figure.yaw = yaw0;
		figure.headYaw = head0;
		return rate;
	}

	/** Phase 27: how far that figure hears M-31, whether it is a sentry, how often a noise drew it (and when). */
	public static double hearing(Figure figure) {
		return figure.hearing;
	}

	public static boolean sentry(Figure figure) {
		return figure.sentry;
	}

	public static void distractedLongAgo(Figure figure) {
		figure.distractedAt -= Stealth.DISTRACT_FORGET + 20;
	}

	/** Phase 26: whether that guard can be overpowered now (from close behind). */
	public static boolean takedownOpen(StorySession s, Figure guard) {
		return Chapter1.takedownOpen(s, guard);
	}

	/** Phase 26: whether the lift to the bridge is shut now, and whether the Falcon waits under the hangar. */
	public static boolean bridgeClosed(StorySession s) {
		return Chapter1.bridgeClosed(s);
	}

	public static boolean falconWaits(StorySession s) {
		return Chapter1.falconWaits(s);
	}

	/** Phase 26: the finale's shots: {least ticks between shots, random ticks on top, a second shot's gap, the gap between two troopers' shots}. */
	public static int[] shotTiming() {
		return new int[] {Finale.SHOT_MIN, Finale.SHOT_RANDOM, Finale.DOUBLE_GAP, Finale.VOLLEY_GAP};
	}

	/** Phase 26: her rushes with the ring: {begun, blocked, hits, lunges}. */
	public static int[] rushes() {
		return Finale.rushesForTest();
	}

	/** Phase 26: she begins a rush with the ring now. */
	public static void rushNow(StorySession s, Figure figure) {
		Finale.rushForTest(s, figure);
	}

	/** Phase 26: whether she is in a rush with the ring now (spinning up or driving at the player). */
	public static boolean rushing(Figure figure) {
		return figure.action == Finale.RUSH;
	}

	/** Phase 26: how often the blade lock's scene set the two up, and how often the player was held in place. */
	public static int[] sceneHolds() {
		return Finale.sceneHoldsForTest();
	}

	/** Phase 26: how far apart the blade lock's scene puts the two; her strength below which she fights fiercely. */
	public static double lockDistance() {
		return Finale.LOCK_DISTANCE;
	}

	/** Phase 26: a stroke takes her just below her last quarter (she turns fierce). */
	public static void lastStrength(StorySession s, Figure figure, net.minecraft.server.level.ServerPlayer player) {
		figure.health = Finale.FIERCE + hit(s.difficulty()) - 0.005F;
		s.lastStroke = s.time();
		Finale.bossHitForTest(s, figure, player);
	}

	public static float fierce() {
		return Finale.FIERCE;
	}

	// --- Phase 28 ------------------------------------------------------------------------------------

	/** Phase 28: the side way's cover and the toolbox placed as the story has them now (else every second). */
	public static void ventCover(StorySession s) {
		Chapter1.ventCover(s);
	}

	/** Phase 28: screwdrivers taken, close-ups begun, covers opened. */
	public static int[] cover() {
		return Chapter1.coverForTest();
	}

	/** Phase 28: as the client does when the four screws are out. */
	public static boolean unscrewed(StorySession s, net.minecraft.server.level.ServerPlayer player) {
		return Chapter1.coverUnscrewed(s, player);
	}

	public static net.minecraft.core.BlockPos coverCell() {
		return Chapter1.VENT_COVER;
	}

	public static net.minecraft.core.BlockPos toolboxCell() {
		return Chapter1.TOOLBOX;
	}

	public static String coverOpen() {
		return Chapter1.COVER_OPEN;
	}

	public static String driver() {
		return Chapter1.DRIVER;
	}

	/** Phase 28: the flags the last checkpoint keeps (a capture goes back to them). */
	public static java.util.List<String> keptNow(StorySession s) {
		return java.util.List.copyOf(s.progress.flags);
	}

	/** Phase 28: the catwalk technician's key, post, hearing; when his round holds that look. */
	public static String catwalkKey() {
		return Chapter1.catwalkKey();
	}

	public static Vec3 catwalkPost() {
		return Chapter1.CATWALK_POST;
	}

	public static long catwalkWatchAt(long now, int look, int id) {
		return Chapter1.catwalkWatchAt(now, look, id);
	}

	public static float catwalkYaw(int look) {
		return Chapter1.CATWALK_WATCH[look][0];
	}

	public static int lureWays() {
		return Stealth.lureWaysForTest();
	}

	/** Phase 28: the ring set going, stopped; guards strained, broken (since the game started). */
	public static int[] ringAndGuard() {
		return Finale.ringAndGuardForTest();
	}

	/** Phase 28: her throws in this fight; whether her ring keeps turning. */
	public static int thrown(Figure figure) {
		return figure.thrown;
	}

	public static boolean ringOn(Figure figure) {
		return figure.ringOn;
	}

	public static int throwsMax() {
		return Finale.THROWS_MAX;
	}

	/** Phase 28: whether her throw is due now, and what she does (for the test's log). */
	public static String throwState(StorySession s, Figure f) {
		return "due " + Finale.throwDue(s, f, Finale.phase(f)) + ", " + action(f) + " cd " + f.cooldown + ", throw " + s.timers.getOrDefault("throw", -1) + " push "
				+ s.timers.getOrDefault("push", -1) + " leap " + s.timers.getOrDefault("leap", -1) + " rush " + s.timers.getOrDefault("rush", -1) + " back "
				+ s.timers.getOrDefault("throw_back", -1) + ", dialog " + (s.dialog == null ? "-" : s.dialog.id) + " film " + s.has("tmp.film") + " scene " + s.has(Finale.BOSS_SCENE)
				+ " fighting " + s.fighting + " react " + f.react + "/" + f.reactTicks + " health " + f.health + " at " + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", f.x, f.y, f.z);
	}

	/** Phase 28: whether she sees the player from where she stands (as her guard tests it), and where the player is. */
	public static String sight(StorySession s, Figure f, net.minecraft.server.level.ServerPlayer player) {
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(f.ship));
		Vec3 me = player.position().subtract(corner);
		return "sees " + Stealth.clear((net.minecraft.server.level.ServerLevel) player.level(), corner.add(f.eye()), player.getEyePosition()) + ", player at "
				+ String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", me.x, me.y, me.z);
	}

	/**
	 * Phase 28: a spot that far from her (plot space) with a straight, walkable line from her to it,
	 * the first of eight ways round starting toward that point; her own spot if there is none.
	 */
	public static Vec3 openSpot(StorySession s, Figure f, double distance, Vec3 toward) {
		net.minecraft.server.level.ServerLevel level = Chapter1.interiors(s);
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(f.ship));
		double start = Math.atan2(toward.z - f.z, toward.x - f.x);

		for (int i = 0; i < 8 && level != null; i++) {
			double a = start + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * Math.PI / 4.0;
			Vec3 to = new Vec3(f.x + Math.cos(a) * distance, f.y, f.z + Math.sin(a) * distance);

			if (Paths.straight(level, corner, f.position(), to, 1.8)) {
				return to;
			}
		}

		return f.position();
	}

	/** Phase 28: whether a guard she broke is still down. */
	public static boolean guardBroken(StorySession s) {
		return s.timer("guard_broken");
	}

	/** Phase 28: where the droids wait during the duel (M-31, R2-V7), and whether they wait now. */
	public static Vec3 duelWait(com.phiro.sabercraft.droid.DroidType type) {
		return Chapter1.duelWait(type);
	}

	public static boolean droidsWait(StorySession s) {
		return Chapter1.droidsWait(s);
	}

	/** Phase 28: the droids of the story now (who comes along). */
	public static java.util.List<com.phiro.sabercraft.droid.DroidType> storyDroids(StorySession s) {
		return Story.storyDroids(s);
	}

	/**
	 * Phase 28: how many of her first phase's blows a guard with that much stamina holds at that
	 * difficulty before it breaks (as her blows strain it); the stamina is full again after.
	 */
	public static int blocksHeld(net.minecraft.server.level.ServerPlayer player, StoryDifficulty difficulty, float stamina) {
		// Phase 29: under that difficulty's stricter guard (blows in a row cost more each), from a fresh state.
		com.phiro.sabercraft.ability.SaberAbilities.strictGuard(player, null);
		com.phiro.sabercraft.ability.SaberAbilities.strictGuard(player, Finale.strictGuard(difficulty));
		com.phiro.sabercraft.ability.SaberAbilities.setStaminaForTests(player, stamina);
		int held = 50;

		for (int n = 0; n < 50; n++) {
			if (com.phiro.sabercraft.ability.SaberAbilities.strainGuard(player, Finale.STRAIN_BLOW * difficulty.bossStrain, Finale.UNSTABLE,
					difficulty.guardBreak) == com.phiro.sabercraft.ability.SaberAbilities.GUARD_BROKEN) {
				held = n;
				break;
			}
		}

		com.phiro.sabercraft.ability.SaberAbilities.strictGuard(player, null);
		com.phiro.sabercraft.ability.SaberAbilities.setStaminaForTests(player, com.phiro.sabercraft.ability.AbilityConfig.get().staminaMax);
		return held;
	}

	// --- Phase 29 ------------------------------------------------------------------------------------

	/**
	 * Phase 29: the boss's tallies since the game started: strokes from behind that hit, turned
	 * aside; turn-about blows, answers to one behind her, shoves, pushes out of her body; slams,
	 * dodged, hits, blocked; strokes dodged while her staff was away; scenes that waited for the
	 * player to land, players set down on the floor.
	 */
	public static int[] boss29() {
		return Finale.phase29ForTest();
	}

	/** Phase 29: how the scene at half her strength set the two up: on the line, turned round the player, the player moved. */
	public static int[] scenePlaced() {
		return Finale.scenePlacedForTest();
	}

	/** Phase 29: she begins her slam now. */
	public static void slam(StorySession s, Figure f) {
		Finale.slamForTest(s, f);
	}

	/** Phase 29: how often she chose each of her moves (see {@link #moveName}). */
	public static int[] moves() {
		return Finale.movesForTest();
	}

	/** Phase 29: the name of one of her moves (as counted by {@link #moves}), or null for a number that is none. */
	public static @Nullable String moveName(int move) {
		return switch (move) {
			case Finale.WINDUP -> "blades";
			case Finale.LEAP -> "leap";
			case Finale.PUSH -> "push";
			case Finale.THROW -> "throw";
			case Finale.RUSH -> "rush";
			case Finale.SLAM -> "slam";
			case Finale.MOVE_LUNGE -> "lunge";
			case Finale.MOVE_SHOVE -> "shove";
			case Finale.MOVE_TURN -> "turn";
			case Finale.RING_START -> "ring";
			default -> null;
		};
	}

	/** Phase 29: what she does now, her slam, throw and rush included (else as {@link #action}). */
	public static String act(Figure figure) {
		return switch (figure.action) {
			case Finale.SLAM -> "slam";
			case Finale.THROW -> "throw";
			case Finale.RUSH -> "rush";
			default -> action(figure);
		};
	}

	/** Phase 29: whether the scene at half her strength waits for the player to land. */
	public static boolean scenePending(StorySession s) {
		return s.has(Finale.SCENE_PENDING);
	}

	/** Phase 29: whether the scene at half her strength runs now (the blade lock and what follows). */
	public static boolean bossSceneOn(StorySession s) {
		return s.has(Finale.BOSS_SCENE);
	}

	/** Phase 29: after how many ticks behind her she notices the player there; how long a stroke from behind is remembered. */
	public static int[] rearTiming(StorySession s) {
		return new int[] {Finale.notice(s), Finale.REAR_MEMORY[Math.min(Finale.REAR_MEMORY.length - 1, s.difficulty().ordinal())]};
	}

	/** Phase 29: the stricter guard of her fight at that difficulty. */
	public static com.phiro.sabercraft.ability.SaberAbilities.StrictGuard strictGuard(StoryDifficulty difficulty) {
		return Finale.strictGuard(difficulty);
	}

	/** Phase 29: the stricter guard's state turned off now (the next tick of the duel turns it on afresh). */
	public static void freshGuard(net.minecraft.server.level.ServerPlayer player) {
		com.phiro.sabercraft.ability.SaberAbilities.strictGuard(player, null);
		com.phiro.sabercraft.ability.SaberAbilities.readyForTests(player, com.phiro.sabercraft.ability.SaberAbility.GUARD);
	}

	/** Phase 29: these flags taken out of a player's saved story (a test going back to a point before them). */
	/**
	 * Phase 31: the takedown as never learnt in the saved file (known how, the guards down, where
	 * anyone lay; also in the missions' starts), so that a test after this one starts without it.
	 */
	public static void forgetTakedowns(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
		StoryProgress progress = StoryStore.load(server, player);
		java.util.function.Predicate<String> takedown = flag -> flag.equals(Chapter1.KNOW_TAKEDOWN) || flag.equals(Chapter1.GUARD_DOWN)
				|| flag.equals(Chapter1.LAB_GUARD_DOWN) || flag.startsWith(Chapter1.DOWN_PREFIX);
		progress.flags.removeIf(takedown);
		progress.missionStart.replaceAll((mission, flags) -> flags.stream().filter(takedown.negate()).toList());
		StoryStore.save(server, player, progress);
	}

	public static void forget(net.minecraft.server.MinecraftServer server, java.util.UUID player, String... flags) {
		StoryProgress progress = StoryStore.load(server, player);

		for (String flag : flags) {
			progress.flags.remove(flag);
			// Phase 31: a guard forgotten as overpowered does not lie where he fell either.
			String key = Chapter1.GUARD_DOWN.equals(flag) ? "det_guard" : Chapter1.LAB_GUARD_DOWN.equals(flag) ? "lab_guard" : null;

			if (key != null) {
				progress.flags.removeIf(kept -> kept.startsWith(Chapter1.DOWN_PREFIX) && kept.contains("/" + key + "/"));
			}
		}

		StoryStore.save(server, player, progress);
	}

	/**
	 * Phase 29: in how many ticks her flying staff next comes within {@code near} blocks (sideways)
	 * of that place (world space), by its own path, and on which way (1 out, 2 back); null when it
	 * does not any more (or is not out).
	 */
	public static int @Nullable [] staffArrives(Figure f, Vec3 at, double near) {
		if (f.throwFrom == null || f.throwFar == null || f.throwHand == null) {
			return null;
		}

		boolean wasNear = Math.hypot(StaffThrow.at(f.throwFrom, f.throwFar, f.throwHand, f.throwOut, f.throwBack, f.throwAge).x - at.x,
				StaffThrow.at(f.throwFrom, f.throwFar, f.throwHand, f.throwOut, f.throwBack, f.throwAge).z - at.z) < near;

		for (int t = f.throwAge + 1; t <= f.throwOut + f.throwBack; t++) {
			Vec3 p = StaffThrow.at(f.throwFrom, f.throwFar, f.throwHand, f.throwOut, f.throwBack, t);
			boolean isNear = Math.hypot(p.x - at.x, p.z - at.z) < near;

			if (isNear && !wasNear) {
				return new int[] {t - f.throwAge, t <= f.throwOut ? 1 : 2};
			}

			wasNear = isNear;
		}

		return null;
	}

	/** Phase 29: where her flying staff is now (world space, as its flight), or null when it is not out. */
	public static @Nullable Vec3 staffAt(Figure f) {
		if (f.throwFrom == null || f.throwFar == null || f.throwHand == null) {
			return null;
		}

		return StaffThrow.at(f.throwFrom, f.throwFar, f.throwHand, f.throwOut, f.throwBack, f.throwAge);
	}

	/** Phase 29: on which way her flying staff is (1 out, 2 back; 0 when it is not out). */
	public static int staffLeg(Figure f) {
		return f.throwFrom == null ? 0 : f.throwAge <= f.throwOut ? 1 : 2;
	}

	/** Phase 29: the detention block guard's flag once he is down. */
	public static String detGuardDown() {
		return Chapter1.GUARD_DOWN;
	}

	/** Phase 29: the way her slam will strike (plot space, level), once it is set; null before (or without a slam). */
	public static @Nullable Vec3 slamWay(Figure f) {
		return f.slamDir;
	}

	/** Phase 29: how far her slam reaches, and how wide its arc is (degrees to each side). */
	public static double[] slamReach() {
		return new double[] {Finale.SLAM_REACH, Finale.SLAM_ARC};
	}

	/** Phase 28: the objective's key for the step now (the text of the refusing branch, else the step's own). */
	public static String objective(StorySession s) {
		return Chapter1.objective(s, s.step());
	}

	// --- Phase 31: the boss fight begun again ---------------------------------------------------------

	/**
	 * Phase 31: everything left over of a fight, as a list of what is not as in a fresh one: her
	 * (phase, strength, what she does, staff, ring, spin, slam, moves, reactions), the session's
	 * flags and timers of a fight, the Focus, and the player (stricter guard, stamina, the guard's
	 * cooldown, slowness). {@code started}: the fight was just begun (its own flag and the first
	 * pauses are expected then). Empty: nothing is left over.
	 */
	public static List<String> fightLeftovers(StorySession s, net.minecraft.server.level.ServerPlayer player, boolean started) {
		List<String> left = new java.util.ArrayList<>();
		Figure f = s.figure("shadow");

		if (f != null) {
			check(left, f.health >= 1.0F, "health " + f.health);
			check(left, Finale.phase(f) == 1, "phase " + Finale.phase(f));
			check(left, !f.ringOn, "ring on");
			check(left, f.poise >= 1.0F && f.discHitAt == -1000L, "poise " + f.poise + "/disc " + f.discHitAt);
			check(left, f.thrown == 0, "thrown " + f.thrown);
			check(left, f.throwFrom == null && f.throwAge == 0, "staff out");
			check(left, !f.lunge && !f.lockScene && !f.disarmed, "lunge/lock/disarmed");
			check(left, !Finale.spinLive(f) && f.spinHitWindup == -1 && !f.spinHurt && !f.spinReached && !f.spinDodged, "spin");
			check(left, f.slamDir == null && !f.shove, "slam/shove");
			check(left, java.util.Arrays.stream(f.moves).allMatch(m -> m == 0), "moves " + java.util.Arrays.toString(f.moves));
			check(left, f.pattern == -1 && f.combo == 0 && f.hits == 0 && f.parried == 0 && f.burst == 0, "pattern/combo/hits");
			check(left, f.react == 0 && f.reactTicks == 0 && f.pushX == 0.0 && f.pushZ == 0.0, "reaction " + f.react);
			check(left, f.rearHits == 0 && f.behindTicks == 0 && f.flightHits == 0 && f.strokes == 0, "rear/flight/strokes");
			check(left, started ? f.action == Finale.GUARD : f.action == Finale.GUARD && !f.lit, "action " + action(f) + (f.lit ? " lit" : ""));
		}

		for (String flag : List.of(Finale.BOSS_SCENE, Finale.BOSS_DONE, "tmp.boss_reveal", Finale.SCENE_PENDING, "tmp.boss_fierce", "tmp.hint_guard_break",
				"tmp.hint_guard_strain")) {
			check(left, !s.has(flag), flag);
		}

		check(left, started == s.has("tmp.duel"), "tmp.duel " + s.has("tmp.duel"));

		for (String timer : List.of("rush", "throw", "slam", "lunge", "shove", "ring_mode", "ring_rest", "rear_answer", "guard_broken", "throw_back", "boss_mid",
				"boss_pending", "stroke")) {
			check(left, !s.timers.containsKey(timer), "timer " + timer);
		}

		if (!started) {
			check(left, !s.timers.containsKey("push") && !s.timers.containsKey("leap"), "timer push/leap");
		}

		check(left, s.focus >= 1.0F && s.focusTicks == 0, "focus " + s.focus + "/" + s.focusTicks);
		long[] strict = com.phiro.sabercraft.ability.SaberAbilities.strictForTest(player);
		check(left, strict[0] == 0 && strict[1] == 0 && strict[2] == 0 && strict[3] == 0, "strict guard " + java.util.Arrays.toString(strict));
		check(left, com.phiro.sabercraft.ability.SaberAbilities.stamina(player) >= com.phiro.sabercraft.ability.AbilityConfig.get().staminaMax - 0.5F,
				"stamina " + com.phiro.sabercraft.ability.SaberAbilities.stamina(player));
		check(left, !player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "slowness");
		check(left, !s.captureDue, "capture still due");
		return left;
	}

	private static void check(List<String> left, boolean fresh, String what) {
		if (!fresh) {
			left.add(what);
		}
	}

	/** Phase 31: the next hit takes the player down (no resistance, one half heart). */
	public static void lethal(net.minecraft.server.level.ServerPlayer player) {
		player.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
		player.setHealth(1.0F);
	}

	/** Phase 31: whether the player's saved file says they are in the story (the way back kept there). */
	public static boolean fileActive(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
		return StoryStore.load(server, player).active;
	}

	/** Phase 31: an input of the player reaches the server (as from the client): a stroke, a key. */
	public static void action(net.minecraft.server.level.ServerPlayer player, byte action) {
		Story.action(player, new com.phiro.sabercraft.network.StoryActionPayload(action, "", 0));
	}

	/** Phase 31: whether the player was struck down and is still to be caught (at the next tick). */
	public static boolean captureDue(StorySession s) {
		return s.captureDue;
	}

	/** Phase 31: the duel begins anew with the figure she is now (as her "threat" line does). */
	public static void restartDuel(StorySession s) {
		Finale.duelStarts(s);
	}

	/** Phase 31: how often a fight was reset since the game started. */
	public static int fightResets() {
		return Finale.resetsForTest();
	}
}
