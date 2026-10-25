package com.phiro.sabercraft.client.saber;

import net.minecraft.util.Mth;

/**
 * A curve that maps the linear progress of an animation (0..1) to how far the blade is out (0..1).
 * Curves must start at 0, end at 1 and never go down in between, so they can be inverted. That
 * lets an animation switch direction halfway through without the blade jumping.
 */
@FunctionalInterface
public interface Easing {
	/** Constant speed. */
	Easing LINEAR = t -> t;
	/** Fast start, gentle stop. */
	Easing OUT_QUAD = t -> 1.0F - (1.0F - t) * (1.0F - t);
	/** Very fast start, long soft stop. Feels like the blade shoots out and settles. */
	Easing OUT_CUBIC = t -> 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
	/** Slow start and stop. */
	Easing IN_OUT_SINE = t -> 0.5F - 0.5F * Mth.cos(t * Mth.PI);

	/**
	 * Fast start with two short stalls on the way, like a blade that catches before it holds
	 * (Phase 10, Kylo Ren's unstable saber). It never goes down, so it still inverts.
	 */
	Easing STUTTER = t -> {
		// Two plateaus of the linear time, then the remaining time rescaled to 0..1.
		float u = t < 0.2F ? t : t < 0.32F ? 0.2F : t < 0.55F ? t - 0.12F : t < 0.65F ? 0.43F : t - 0.22F;
		return OUT_QUAD.apply(u / 0.78F);
	};

	float apply(float t);

	/** Finds the progress for a given output by bisection. Precise enough for animation. */
	default float inverse(float value) {
		if (value <= 0.0F) {
			return 0.0F;
		}

		if (value >= 1.0F) {
			return 1.0F;
		}

		float low = 0.0F;
		float high = 1.0F;

		for (int i = 0; i < 20; i++) {
			float mid = (low + high) * 0.5F;

			if (this.apply(mid) < value) {
				low = mid;
			} else {
				high = mid;
			}
		}

		return (low + high) * 0.5F;
	}
}
