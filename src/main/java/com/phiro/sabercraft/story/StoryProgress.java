package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Phase 19: what one player has done in the story, saved in {@code <world>/sabercraft/story/<uuid>.json}
 * whenever it changes. Everything is as of the last checkpoint: a step of a mission that was not
 * finished starts again from its beginning after a relog, a capture or a restart, so the world
 * is always clean.
 *
 * <p>Phase 20: the checkpoint is the whole state at one moment (mission, step, what is known, the
 * journal) and the safe place the player is put back at. Checkpoints are made at the beginning of
 * most steps, after milestones (the armour on, locked in the cell) and in safe places (the
 * hideout, the crawl ways). A file of Phase 19 (format 1) has no place: one continues at the safe
 * place of its step.
 */
public final class StoryProgress {
	/**
	 * The file's layout; raised when it changes (2: Phase 20, with the step's id and a place; 3:
	 * Phase 22, the chapter's end kept as the flag {@code chapter.done}).
	 */
	public static final int FORMAT = 3;
	public int format = 1;
	/** The chapter ({@code nightshade}). */
	public String chapter = Chapter1.ID;
	/** Whether the player is in the story now (their own things are in the stash). */
	public boolean active;
	/** The mission (0 = the first) and the step in it that is to be done. */
	public int mission;
	public int step;
	/** Phase 20: the step's id (the index alone breaks when a mission gets a step more). */
	public String stepId = "";
	/**
	 * Phase 20: where the checkpoint puts the player back: the ship ({@code ""}: the step's safe
	 * place), the place in its plot and the look.
	 */
	public String placeShip = "";
	public double placeX;
	public double placeY;
	public double placeZ;
	public float placeYaw;
	/** Missions finished at least once. */
	public Set<Integer> completed = new TreeSet<>();
	/**
	 * What is known, decided and done: knowledge ({@code know.*}), decisions ({@code trust.hale}),
	 * optional objectives done ({@code opt.*}), story items held ({@code item.*}), droids recruited
	 * ({@code droid.*}).
	 */
	public Set<String> flags = new TreeSet<>();
	/** The flags as they were when each mission first began (for playing a mission again). */
	public TreeMap<Integer, List<String>> missionStart = new TreeMap<>();
	/** Datapads and recordings found, in the order found (the journal). */
	public List<String> journal = new ArrayList<>();
	/** Whether the chapter was finished once (the saber is the player's to keep). */
	public boolean finished;
	/** How often the player was caught (shown in the journal). */
	public int captures;
	/** Where the player was before the story, to go back there: dimension, position, look, game mode. */
	public String returnDimension = "";
	public double returnX;
	public double returnY;
	public double returnZ;
	public float returnYaw;
	public float returnPitch;
	public String returnMode = "survival";
	/** The droids the player had out before the story (given back after). */
	public List<String> ownDroids = new ArrayList<>();
	/** Whether the player had a stash file written (their inventory before the story). */
	public boolean stashed;
	/**
	 * Phase 27: the story's difficulty ({@code easy}, {@code normal}, {@code hard}; see
	 * {@link StoryDifficulty}). A save from before Phase 27 has none: normal.
	 */
	public String difficulty = StoryDifficulty.NORMAL.id;

	/** Whether a flag is set. */
	public boolean has(String flag) {
		return this.flags.contains(flag);
	}
}
