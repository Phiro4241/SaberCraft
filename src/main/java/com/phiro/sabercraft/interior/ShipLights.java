package com.phiro.sabercraft.interior;

/**
 * Phase 17: the dimmer of the ships' lights, in steps from full to very dim. The step of a plot
 * is kept in its light blocks (their levels, lowered by the step's share), so every player sees
 * the same and it lasts. The models of the rooms fade smoothly to the step's brightness on the
 * client (see InteriorScene).
 */
public final class ShipLights {
	private ShipLights() {
	}

	/** The share of each light block's level per step (step 0: as built). */
	static final double[] LEVELS = {1.0, 0.75, 0.5, 0.3, 0.12};
	/** How bright the rooms' models are drawn per step (lit surfaces). */
	static final float[] SURFACES = {1.0F, 0.74F, 0.52F, 0.34F, 0.2F};

	public static int steps() {
		return LEVELS.length;
	}

	/** The level of a light block built at {@code built} in step {@code step}. */
	public static int level(int built, int step) {
		return step == 0 ? built : Math.max(1, (int) Math.round(built * LEVELS[step]));
	}

	/** The step a light block built at {@code built} is in, from its level now (the nearest). */
	public static int stepOf(int built, int now) {
		int best = 0;

		for (int step = 1; step < LEVELS.length; step++) {
			if (Math.abs(level(built, step) - now) < Math.abs(level(built, best) - now)) {
				best = step;
			}
		}

		return best;
	}

	/** The brightness of lit surfaces in the rooms in a step (1 = full). */
	public static float surfaces(int step) {
		return SURFACES[Math.clamp(step, 0, SURFACES.length - 1)];
	}

	/** The brightness of glowing screens and signs at a surface brightness (they dim less). */
	public static float glow(float surfaces) {
		return 0.3F + 0.7F * surfaces;
	}

	/** The brightness of the lamps themselves at a surface brightness (they dim most). */
	public static float lamps(float surfaces) {
		return surfaces * surfaces * 0.85F + surfaces * 0.15F;
	}
}
