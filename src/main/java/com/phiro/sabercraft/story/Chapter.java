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
}
