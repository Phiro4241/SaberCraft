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
	}

	/** Where the marker points now (world space), or null. */
	public static @Nullable Vec3 marker(StorySession s) {
		Mission.Target goal = s.chapter instanceof Chapter1 c1 ? c1.target(s) : s.step().target();
		return goal == null ? null : goal.at().add(Vec3.atLowerCornerOf(s.origin(goal.ship())));
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

		return keys;
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

	public static float hit() {
		return Finale.HIT;
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
		StoryStore.save(server, player, progress);
	}
}
