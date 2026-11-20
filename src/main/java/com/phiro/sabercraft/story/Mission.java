package com.phiro.sabercraft.story;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 19: a mission of a chapter: its steps one after another, and optional objectives on the
 * side. Each step is a checkpoint: it begins at a known place, and being caught, dying or coming
 * back later starts it again from there with what was known at its beginning. (Phase 20: most
 * steps are; see {@link Step}.)
 *
 * @param id       {@code m1}..: also the start of its words' keys ({@code sabercraft.story.mission.m1})
 * @param steps    the steps in order
 * @param optional what can be done on the side (a flag {@code opt.<id>} when done)
 */
public record Mission(String id, List<Step> steps, List<Optional> optional) {
	/** The title's key. */
	public String title() {
		return "sabercraft.story.mission." + this.id;
	}

	/** The step with that id, or -1. */
	public int indexOf(String step) {
		for (int i = 0; i < this.steps.size(); i++) {
			if (this.steps.get(i).id().equals(step)) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * A step: its objective ({@code sabercraft.story.obj.<id>}), the ship and place it starts at
	 * (plot space), whether being detected fails it at once (sections one must get through unseen),
	 * and where the marker points (or nowhere).
	 *
	 * <p>Phase 20: the place is the step's safe place, where nobody sees the player: a checkpoint
	 * made as the step begins ({@code checkpoint}) puts the player back there. A step without a
	 * checkpoint of its own is played again from the checkpoint before it (or from one made in it).
	 */
	public record Step(String id, String ship, Vec3 spawn, float yaw, boolean unseen, boolean checkpoint, @Nullable Target target) {
		public String objective() {
			return "sabercraft.story.obj." + this.id;
		}
	}

	/** Phase 20: a place to put the player: the ship, the point (plot space), the look. */
	public record Place(String ship, Vec3 at, float yaw) {
	}

	/** Where an objective is: in which ship, at what point (plot space). */
	public record Target(String ship, Vec3 at) {
	}

	/**
	 * An optional objective: shown (grey) while {@code shown} holds; done when the flag
	 * {@code opt.<id>} is set.
	 */
	public record Optional(String id, Predicate<StorySession> shown) {
		public String objective() {
			return "sabercraft.story.opt." + this.id;
		}

		public String flag() {
			return "opt." + this.id;
		}
	}

	/**
	 * A thing to use in a ship: a box (plot space) one right-clicks, with its own words ({@code sabercraft.story.point.<id>}).
	 *
	 * <p>Phase 29: every id must be declared ({@link #declare}) by the chapter that uses it. The
	 * tests check that each declared id has its words in every language of the mod, so the prompt
	 * under the crosshair can never show a raw key again (Phase 28's cover and toolbox had none and
	 * showed {@code sabercraft.story.point.vent_cover}). An id used without being declared is
	 * logged once and counted ({@link #undeclaredForTest}); the client shows a plain "Use" for words
	 * it does not know.
	 */
	public record Point(String id, String ship, AABB box, boolean hack) {
		private static final java.util.Set<String> DECLARED = java.util.concurrent.ConcurrentHashMap.newKeySet();
		private static final java.util.Set<String> UNDECLARED = java.util.concurrent.ConcurrentHashMap.newKeySet();

		public Point {
			if (!DECLARED.contains(id) && UNDECLARED.add(id)) {
				com.phiro.sabercraft.SaberCraft.LOGGER.error("Story: the point '{}' is not declared (Mission.Point.declare): its words may be missing", id);
			}
		}

		public String label() {
			return LABEL_PREFIX + this.id;
		}

		/** The start of every point's words' key. */
		public static final String LABEL_PREFIX = "sabercraft.story.point.";

		/** Phase 29: a chapter's points (their ids), each with its words in the language files. */
		public static void declare(String... ids) {
			DECLARED.addAll(java.util.List.of(ids));
		}

		/** Phase 29, for tests: every declared id. */
		public static java.util.Set<String> declaredForTest() {
			return java.util.Set.copyOf(DECLARED);
		}

		/** Phase 29, for tests: the ids used without being declared. */
		public static java.util.Set<String> undeclaredForTest() {
			return java.util.Set.copyOf(UNDECLARED);
		}
	}
}
