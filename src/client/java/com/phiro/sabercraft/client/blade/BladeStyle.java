package com.phiro.sabercraft.client.blade;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Visual definition of one energy blade. Every color variant (red, blue, green, ...) is a separate
 * style, so adding a new saber only means registering another style in {@link BladeStyles}. All
 * values can be changed per style in {@code config/sabercraft/blade_styles.json}, see
 * {@link BladeStyleConfig}.
 *
 * <p>All lengths are in item model units: one unit is the width of the 16x16 item texture.
 *
 * @param id            unique id, referenced from item model JSON as {@code "style"} and stored on
 *                      the item as the blade color
 * @param coreColor     RGB of the bright, almost white energy core
 * @param bloomColor    RGB of the additive bloom right around the core
 * @param glowColor     RGB of the soft colored halo around the blade
 * @param length        blade length from the emitter to the tip
 * @param coreRadius    radius of the solid core
 * @param glowRadius    outer radius of the soft glow, where it fades to zero
 * @param bloomStrength brightness of the bloom, 1.0 is the default
 * @param glowStrength  overall brightness of the halo, 1.0 is the default
 * @param shimmer       amplitude of the subtle brightness shimmer, 0 turns it off
 */
public record BladeStyle(
		Identifier id,
		int coreColor,
		int bloomColor,
		int glowColor,
		float length,
		float coreRadius,
		float glowRadius,
		float bloomStrength,
		float glowStrength,
		float shimmer
) {
	public static final float DEFAULT_LENGTH = 1.2F;
	public static final float DEFAULT_CORE_RADIUS = 0.022F;
	public static final float DEFAULT_GLOW_RADIUS = 0.12F;
	public static final float DEFAULT_SHIMMER = 0.04F;

	/**
	 * Creates a style with the default blade shape and only custom colors. The bloom is the blade
	 * color mixed with white, so the core does not end in a hard edge.
	 */
	public static BladeStyle of(Identifier id, int coreColor, int glowColor) {
		return of(id, coreColor, mix(glowColor, 0xFFFFFF, 0.3F), glowColor);
	}

	/** Creates a style with the default blade shape and an explicit bloom color. */
	public static BladeStyle of(Identifier id, int coreColor, int bloomColor, int glowColor) {
		return new BladeStyle(
				id, coreColor, bloomColor, glowColor, DEFAULT_LENGTH, DEFAULT_CORE_RADIUS, DEFAULT_GLOW_RADIUS, 1.0F, 1.0F, DEFAULT_SHIMMER
		);
	}

	public BladeStyle withStrength(float newBloomStrength, float newGlowStrength) {
		return new BladeStyle(
				this.id, this.coreColor, this.bloomColor, this.glowColor, this.length, this.coreRadius, this.glowRadius,
				newBloomStrength, newGlowStrength, this.shimmer
		);
	}

	/** Mixes two RGB colors, {@code amount} 0 gives {@code from}, 1 gives {@code to}. */
	public static int mix(int from, int to, float amount) {
		int r = Math.round(Mth.lerp(amount, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(amount, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(amount, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
