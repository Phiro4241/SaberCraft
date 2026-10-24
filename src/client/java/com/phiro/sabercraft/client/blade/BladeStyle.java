package com.phiro.sabercraft.client.blade;

import net.minecraft.resources.Identifier;

/**
 * Visual definition of one energy blade. Every color variant (red, blue, green, ...) is a separate
 * style, so adding a new saber only means registering another style in {@link BladeStyles}.
 *
 * <p>All lengths are in item model units: one unit is the width of the 16x16 item texture.
 *
 * @param id           unique id, referenced from item model JSON as {@code "style"}
 * @param coreColor    RGB of the bright, almost white center
 * @param glowColor    RGB of the colored halo around the core
 * @param length       blade length from the emitter to the tip
 * @param coreRadius   radius of the solid core
 * @param glowRadius   outer radius of the soft glow, where it fades to zero
 * @param glowStrength overall brightness of the halo, 1.0 is the default
 * @param shimmer      amplitude of the subtle brightness shimmer, 0 turns it off
 */
public record BladeStyle(
		Identifier id,
		int coreColor,
		int glowColor,
		float length,
		float coreRadius,
		float glowRadius,
		float glowStrength,
		float shimmer
) {
	public static final float DEFAULT_LENGTH = 1.2F;
	public static final float DEFAULT_CORE_RADIUS = 0.022F;
	public static final float DEFAULT_GLOW_RADIUS = 0.12F;

	/** Creates a style with the default blade shape and only custom colors. */
	public static BladeStyle of(Identifier id, int coreColor, int glowColor) {
		return new BladeStyle(id, coreColor, glowColor, DEFAULT_LENGTH, DEFAULT_CORE_RADIUS, DEFAULT_GLOW_RADIUS, 1.0F, 0.04F);
	}

	public BladeStyle withLength(float newLength) {
		return new BladeStyle(this.id, this.coreColor, this.glowColor, newLength, this.coreRadius, this.glowRadius, this.glowStrength, this.shimmer);
	}
}
