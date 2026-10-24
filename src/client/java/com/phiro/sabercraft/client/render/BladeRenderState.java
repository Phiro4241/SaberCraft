package com.phiro.sabercraft.client.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * What the blade renderer needs to know about one held saber in one frame.
 *
 * <p>Values are rounded to a few hundred steps so the record works well as part of the item model
 * identity, which Minecraft uses to cache item renders.
 *
 * @param extension how far the blade is out, 0 = only the hilt, 1 = full length
 * @param flare     extra brightness during the ignition, 0 = none
 * @param style     the blade color stored on the item, or {@code null} for the item model's own
 *                  style (the default color of the hilt)
 * @param swing     where the blade is in a fighting move, or {@code null} while it is not moving:
 *                  drives the light trail and the brighter glow of an attack
 */
public record BladeRenderState(float extension, float flare, @Nullable Identifier style, @Nullable Swing swing) {
	/**
	 * A blade in the middle of an attack.
	 *
	 * @param entityId    the holder, so each blade keeps its own trail
	 * @param hand        the hand holding it, as {@code InteractionHand.ordinal()}
	 * @param firstPerson whether it is the first-person view of the local player
	 * @param strength    0..1, how strongly the trail and glow show at this moment
	 */
	public record Swing(int entityId, int hand, boolean firstPerson, float strength) {
	}

	private static final float STEPS = 512.0F;

	public static final BladeRenderState FULL = new BladeRenderState(1.0F, 0.0F, null, null);
	public static final BladeRenderState RETRACTED = new BladeRenderState(0.0F, 0.0F, null, null);

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

		return new BladeRenderState(roundedExtension, roundedFlare, null, null);
	}

	/** The same state with the given blade color, {@code null} keeps the model's own style. */
	public BladeRenderState withStyle(@Nullable Identifier newStyle) {
		return newStyle == null || newStyle.equals(this.style) ? this : new BladeRenderState(this.extension, this.flare, newStyle, this.swing);
	}

	/** The same state during an attack, {@code null} for a blade at rest. */
	public BladeRenderState withSwing(@Nullable Swing newSwing) {
		return newSwing == null && this.swing == null ? this : new BladeRenderState(this.extension, this.flare, this.style, newSwing);
	}

	public boolean isVisible() {
		return this.extension > 0.0F;
	}

	public boolean isAnimating() {
		return this.extension > 0.0F && this.extension < 1.0F || this.flare > 0.0F || this.swing != null;
	}
}
