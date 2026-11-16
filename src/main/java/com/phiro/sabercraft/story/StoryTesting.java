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
		figure.timer = Finale.OPEN;
		figure.windup = 0;
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
}
