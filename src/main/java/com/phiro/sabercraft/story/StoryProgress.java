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
 */
public final class StoryProgress {
	/** The file's layout; raised when it changes. */
	public int format = 1;
	/** The chapter ({@code nightshade}). */
	public String chapter = Chapter1.ID;
	/** Whether the player is in the story now (their own things are in the stash). */
	public boolean active;
	/** The mission (0 = the first) and the step in it that is to be done. */
	public int mission;
	public int step;
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

	/** Whether a flag is set. */
	public boolean has(String flag) {
		return this.flags.contains(flag);
	}
}
