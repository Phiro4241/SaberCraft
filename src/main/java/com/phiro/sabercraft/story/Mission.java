package com.phiro.sabercraft.story;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 19: a mission of a chapter: its steps one after another, and optional objectives on the
 * side. Each step is a checkpoint: it begins at a known place, and being caught, dying or coming
 * back later starts it again from there with what was known at its beginning.
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
	 */
	public record Step(String id, String ship, Vec3 spawn, float yaw, boolean unseen, @Nullable Target target) {
		public String objective() {
			return "sabercraft.story.obj." + this.id;
		}
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

	/** A thing to use in a ship: a box (plot space) one right-clicks, with its own words ({@code sabercraft.story.point.<id>}). */
	public record Point(String id, String ship, AABB box, boolean hack) {
		public String label() {
			return "sabercraft.story.point." + this.id;
		}
	}
}
