package com.phiro.sabercraft.client.saber;

import net.minecraft.resources.Identifier;

/**
 * Timing of the ignite and retract animation of one saber model. Different sabers can register
 * their own animation in {@link SaberAnimations}, for example a slower, heavier blade or a
 * crackling, unstable one.
 *
 * @param id           unique id
 * @param igniteDelay  ticks between drawing the saber and the ignition. In first person the hand
 *                     needs a moment to come up after switching items; without the delay most of
 *                     the ignition would happen below the screen edge.
 * @param igniteTicks  how long the blade takes to come out, in game ticks (20 ticks = 1 second)
 * @param retractTicks how long the blade takes to go back into the hilt
 * @param igniteCurve  shape of the ignite movement over time
 * @param retractCurve shape of the retract movement. It runs backwards from 1 to 0, so an
 *                     "out" curve starts slowly and speeds up towards the hilt.
 * @param igniteFlare  extra brightness at the start of the ignition, 0 turns it off
 */
public record SaberAnimation(
		Identifier id,
		int igniteDelay,
		int igniteTicks,
		int retractTicks,
		Easing igniteCurve,
		Easing retractCurve,
		float igniteFlare
) {
	public SaberAnimation {
		if (igniteDelay < 0 || igniteTicks < 1 || retractTicks < 1) {
			throw new IllegalArgumentException("Animation " + id + " needs at least one tick per direction");
		}
	}
}
