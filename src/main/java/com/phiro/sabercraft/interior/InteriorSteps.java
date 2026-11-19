package com.phiro.sabercraft.interior;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 26: what one's steps sound like aboard. The ships' decks are drawn over the game's
 * blocks (stone bricks, invisible barriers), which step like stone: aboard, a boot on a deck
 * plate is heard instead (a dull thud and the plate's short ring, six of them, a little
 * different each time), and crawling in a vent a knee on thin sheet metal (five of them). Same
 * loudness and timing as the game's own steps, so nothing else about them changes.
 */
public final class InteriorSteps {
	public static final SoundEvent DECK_STEP = SoundEvent.createVariableRangeEvent(SaberCraft.id("ship.deck_step"));
	public static final SoundEvent VENT_CRAWL = SoundEvent.createVariableRangeEvent(SaberCraft.id("story.vent_crawl"));
	static final float STEP_VOLUME = 0.22F;
	static final float CRAWL_VOLUME = 0.3F;
	private static int steps;
	private static int crawls;

	private InteriorSteps() {
	}

	/** Plays a deck step or a vent crawl instead of the stone step, if this is one aboard; true if it did. */
	public static boolean step(Entity entity, BlockState state) {
		// (Every floor aboard is a drawn deck, whatever block lies under it; in water, as the game does.)
		if (!(entity instanceof Player) || state.isAir() || entity.isInWater() || !ShipInteriors.isInterior(entity.level())) {
			return false;
		}

		boolean crawling = entity.getPose() == Pose.SWIMMING;
		float pitch = 0.93F + entity.getRandom().nextFloat() * 0.14F;
		entity.playSound(crawling ? VENT_CRAWL : DECK_STEP, crawling ? CRAWL_VOLUME : STEP_VOLUME, pitch);

		if (crawling) {
			crawls++;
		} else {
			steps++;
		}

		return true;
	}

	/** For the tests: {deck steps, vent crawls} played (both sides together in one game). */
	public static int[] countsForTest() {
		return new int[] {steps, crawls};
	}
}
