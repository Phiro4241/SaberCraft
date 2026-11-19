package com.phiro.sabercraft.story;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Phase 19: a chapter of the story: its missions, people, things to use and what happens. The
 * session runs the systems (seeing, chasing, checkpoints, dialogs); the chapter says what they
 * mean. Kapitel 1 is {@link Chapter1}; later chapters (flights, other ships) are more of these.
 */
public interface Chapter {
	String id();

	List<Mission> missions();

	/** Everyone in the story's ships at the session's mission and step (and with what is known). */
	List<Figure> figures(StorySession session);

	/** What can be used now. */
	List<Mission.Point> points(StorySession session);

	/** A step begins: the first time ({@code again} false), or again after a capture or a relog. */
	void begin(StorySession session, Mission.Step step, boolean again);

	/** Every tick while the player is in the story. */
	void tick(StorySession session);

	/** The player used a point. */
	void use(StorySession session, Mission.Point point);

	/** A droid's ability was used in the story ({@code mouse}: M-31, {@code astromech}: R2-V7); returns the message, or null for the normal ability. */
	@Nullable String droid(StorySession session, String droid);

	/** The player speaks to someone. Returns whether they answered. */
	boolean talk(StorySession session, Figure figure);

	/** A dialog by id. */
	@Nullable Dialog dialog(String id);

	/** Someone saw the player and raised the alarm (or caught them); returns whether the chapter took care of it. */
	default boolean detected(StorySession session, Figure figure) {
		return false;
	}

	/** Which areas are dark (plot space) in that ship now: there one is seen far only in a lamp's light. */
	default boolean dark(StorySession session, String ship, double x, double y, double z) {
		return false;
	}

	/** Whether that area is closed to everyone (a disguise does not help there). */
	default boolean restricted(StorySession session, String ship, double x, double y, double z) {
		return false;
	}

	/** Whether a door (plot space, its first cell) is locked now. */
	default boolean locked(StorySession session, String ship, int x, int y, int z) {
		return false;
	}

	/** Phase 20: the step's safe place now (by default the step's own; a chapter may choose by what is known). */
	default Mission.Place place(StorySession session, Mission.Step step) {
		return new Mission.Place(step.ship(), step.spawn(), step.yaw());
	}

	/** Phase 20: whether the step makes a checkpoint as it begins (by default: as the step says). */
	default boolean checkpoint(StorySession session, Mission.Step step) {
		return step.checkpoint();
	}

	/** Phase 20: before a checkpoint: what is under way and should count as done (those walking off are gone). */
	default void settle(StorySession session) {
	}

	/** Phase 20: after being put back at a checkpoint: where the droids are (by default with the player). */
	default void droids(StorySession session) {
		Story.gatherDroids(session);
	}

	/** Phase 20: why a hatch of a story's ship does not open now (a key of words), or null when it does. */
	default @Nullable String hatchClosed(StorySession session, String ship, int x, int y, int z) {
		return this.locked(session, ship, x, y, z) ? "sabercraft.story.hint.hatch_locked" : null;
	}

	/**
	 * Phase 26: whether that hatch (plot space) is a way out the story means for a chase: it opens
	 * while someone is after the player (other crawl ways do not, so nobody slips away before the
	 * hunters' eyes). Only where and while the story offers it as the escape.
	 */
	default boolean escapeHatch(StorySession session, String ship, int x, int y, int z) {
		return false;
	}

	/** Phase 24: whether one can speak to someone now (an alarm or a fight leaves no time for small talk). */
	default boolean talksNow(StorySession session, Figure figure) {
		return true;
	}

	/** Phase 20: the wall comms of a ship (plot space, where one stands to use them), to raise an alarm. */
	default java.util.List<net.minecraft.world.phys.Vec3> comms(StorySession session, String ship) {
		return java.util.List.of();
	}

	/** Phase 20: the doors (plot space) troopers come through after an alarm was called in. */
	default java.util.List<net.minecraft.world.phys.Vec3> entries(StorySession session, String ship) {
		return java.util.List.of();
	}

	/** Phase 20: whether the ship runs on its emergency lights now (dimmer: one is seen less far). */
	default boolean emergency(StorySession session) {
		return false;
	}
}
