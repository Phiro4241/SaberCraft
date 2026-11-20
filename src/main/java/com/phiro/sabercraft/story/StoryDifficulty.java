package com.phiro.sabercraft.story;

/**
 * Phase 27: the story's difficulty, chosen before the story begins and kept with each player's
 * save ({@link StoryProgress#difficulty}), so it is the same after a capture, a relog or the next
 * mission, and every player has their own. It changes how the enemies behave, not only how hard
 * they hit: how many there are, how far and how fast they notice the player, how quickly they
 * move, search and shoot, and how the boss fights. Normal is the story as it was made (with the
 * stronger boss of Phase 27).
 *
 * <p>It can be changed in the story's menu at any time. In a running mission the change takes
 * effect at once by going back to the last checkpoint (like being caught, without counting as
 * caught): everyone is placed anew for the new difficulty there, so no enemy is added or taken
 * away in the middle of a scene.
 */
public enum StoryDifficulty {
	/**
	 * Easy: fewer enemies (the extra patrols are left out), shorter sight, slower to notice and to
	 * follow, a shorter search, slower and less accurate shots, one stun bolt more before one is
	 * caught, help comes later and fewer; the boss has less strength, attacks less often and leaves
	 * longer openings.
	 */
	EASY("easy", 0.8, 0.65, 0.9, 0.7, 1.35, 1.35, 1.35, 4, 1, 3, -1, 0.8F, 0.8F, 1.4F, 0.75F),
	/** Normal: as the story was made. */
	NORMAL("normal", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 3, 2, 4, 0, 1.0F, 1.0F, 1.0F, 1.0F),
	/**
	 * Hard: more enemies (extra patrols and fighters), longer sight, quicker to notice, faster on
	 * their feet, longer searches, quicker and more accurate shots, caught after two stun bolts,
	 * help sooner and more of it; the boss has more strength, attacks faster and more often, and
	 * her openings are shorter.
	 */
	HARD("hard", 1.2, 1.4, 1.12, 1.35, 0.75, 0.75, 0.75, 2, 3, 6, 1, 1.3F, 1.25F, 0.7F, 1.3F);

	/** The name in the save and the menu ({@code sabercraft.story.difficulty.<id>}). */
	public final String id;
	/** How far they see (times the normal sight). */
	public final double sight;
	/** How fast their suspicion grows while they see the player. */
	public final double notice;
	/** How fast they walk and run. */
	public final double speed;
	/** How long a search lasts (times the normal). */
	public final double search;
	/** How long they aim before a shot, and wait between shots (times the normal), and how far bolts scatter. */
	public final double aim;
	public final double cooldown;
	public final double spread;
	/** How many stun bolts catch the player. */
	public final int stunHits;
	/** How many come as help at a call, and how many such helpers may be about at once. */
	public final int reinforce;
	public final int reinforceMax;
	/** Fewer (-1), the made number (0) or more (1) enemies: extra patrols and fighters. */
	public final int enemies;
	/** The boss: her strength (times the normal), how fast she attacks, how long her openings last, how hard she hits. */
	public final float bossHealth;
	public final float bossTempo;
	public final float bossOpening;
	public final float bossDamage;

	StoryDifficulty(String id, double sight, double notice, double speed, double search, double aim, double cooldown, double spread, int stunHits,
			int reinforce, int reinforceMax, int enemies, float bossHealth, float bossTempo, float bossOpening, float bossDamage) {
		this.id = id;
		this.sight = sight;
		this.notice = notice;
		this.speed = speed;
		this.search = search;
		this.aim = aim;
		this.cooldown = cooldown;
		this.spread = spread;
		this.stunHits = stunHits;
		this.reinforce = reinforce;
		this.reinforceMax = reinforceMax;
		this.enemies = enemies;
		this.bossHealth = bossHealth;
		this.bossTempo = bossTempo;
		this.bossOpening = bossOpening;
		this.bossDamage = bossDamage;
	}

	/** The difficulty of that name (an unknown or missing one, as in a save from before Phase 27: normal). */
	public static StoryDifficulty of(String id) {
		for (StoryDifficulty difficulty : values()) {
			if (difficulty.id.equals(id)) {
				return difficulty;
			}
		}

		return NORMAL;
	}

	/** By its place in the menu (0 easy, 1 normal, 2 hard). */
	public static StoryDifficulty byIndex(int index) {
		StoryDifficulty[] all = values();
		return index >= 0 && index < all.length ? all[index] : NORMAL;
	}

	/** A number of ticks scaled (at least 1). */
	public int ticks(int ticks, double factor) {
		return Math.max(1, (int) Math.round(ticks * factor));
	}
}
