package com.phiro.sabercraft.client.saber;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;

/** Registry of the ignite and retract animations. */
public final class SaberAnimations {
	private static final Map<Identifier, SaberAnimation> ANIMATIONS = new LinkedHashMap<>();

	/**
	 * The hilt comes up for 0.3 seconds, then the blade shoots out in 0.5 seconds, fast at first
	 * and settling softly, with a short bright flash. Back in within 0.35 seconds at an even speed,
	 * so it visibly slides into the hilt from the first moment.
	 */
	public static final SaberAnimation DEFAULT = register(
			new SaberAnimation(SaberCraft.id("default"), 6, 10, 7, Easing.OUT_QUAD, Easing.LINEAR, 0.35F)
	);

	/**
	 * For the dark hilt: the blade comes out a little slower, shoots out harder at first and settles
	 * longer, with a stronger flash. It goes back in a bit slower as well.
	 */
	public static final SaberAnimation HEAVY = register(
			new SaberAnimation(SaberCraft.id("heavy"), 6, 13, 9, Easing.OUT_CUBIC, Easing.LINEAR, 0.5F)
	);

	/**
	 * Phase 10, for Kylo Ren's crossguard: the unstable blade stalls twice on its way out and flares
	 * up hard, then crackles. It goes back in quickly, with a jolt at the start.
	 */
	public static final SaberAnimation UNSTABLE = register(
			new SaberAnimation(SaberCraft.id("unstable"), 6, 16, 8, Easing.STUTTER, Easing.LINEAR, 0.8F)
	);

	/**
	 * Phase 10, for the double-bladed hilt: one blade after the other (see the {@code ignite} window
	 * of each blade in the item model), so the whole ignition takes a bit longer.
	 */
	public static final SaberAnimation DOUBLE = register(
			new SaberAnimation(SaberCraft.id("double"), 6, 16, 10, Easing.LINEAR, Easing.LINEAR, 0.4F)
	);

	/** Phase 10, for the small, light hilts of Yoda and Ahsoka: a quick, snappy ignition. */
	public static final SaberAnimation QUICK = register(
			new SaberAnimation(SaberCraft.id("quick"), 5, 7, 5, Easing.OUT_CUBIC, Easing.LINEAR, 0.3F)
	);

	private SaberAnimations() {
	}

	public static SaberAnimation register(SaberAnimation animation) {
		if (ANIMATIONS.putIfAbsent(animation.id(), animation) != null) {
			throw new IllegalArgumentException("Duplicate saber animation " + animation.id());
		}

		return animation;
	}

	public static Map<Identifier, SaberAnimation> all() {
		return Collections.unmodifiableMap(ANIMATIONS);
	}
}
