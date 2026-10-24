package com.phiro.sabercraft.client.saber;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;

/** Registry of the ignite and retract animations. Phase 4 ships the default film-like one. */
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
