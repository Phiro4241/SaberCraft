package com.phiro.sabercraft.client.render;

import net.minecraft.util.Mth;

/**
 * What the blade renderer needs to know about one held saber in one frame.
 *
 * <p>Values are rounded to a few hundred steps so the record works well as part of the item model
 * identity, which Minecraft uses to cache item renders.
 *
 * @param extension how far the blade is out, 0 = only the hilt, 1 = full length
 * @param flare     extra brightness during the ignition, 0 = none
 */
public record BladeRenderState(float extension, float flare) {
	private static final float STEPS = 512.0F;

	public static final BladeRenderState FULL = new BladeRenderState(1.0F, 0.0F);
	public static final BladeRenderState RETRACTED = new BladeRenderState(0.0F, 0.0F);

	public static BladeRenderState of(float extension, float flare) {
		float roundedExtension = Math.round(Mth.clamp(extension, 0.0F, 1.0F) * STEPS) / STEPS;
		float roundedFlare = Math.round(Math.max(0.0F, flare) * STEPS) / STEPS;

		if (roundedFlare == 0.0F) {
			if (roundedExtension == 1.0F) {
				return FULL;
			}

			if (roundedExtension == 0.0F) {
				return RETRACTED;
			}
		}

		return new BladeRenderState(roundedExtension, roundedFlare);
	}

	public boolean isVisible() {
		return this.extension > 0.0F;
	}

	public boolean isAnimating() {
		return this.extension > 0.0F && this.extension < 1.0F || this.flare > 0.0F;
	}
}
