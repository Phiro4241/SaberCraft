package com.phiro.sabercraft.client.droid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.droid.DroidType;

/**
 * How each droid moves its parts (Phase 11), for the world and the menu preview alike. All angles
 * are in droid space: the droid stands at the origin, y up, its face looking to -z.
 *
 * <ul>
 * <li>R2-D2 rocks a little on its legs and turns its dome; talking to it spins the dome.</li>
 * <li>BB-8 really rolls: the ball turns with the way it rolled, the head stays on top, looks
 * where BB-8 looks and leans into the motion; talking to it tilts the head.</li>
 * <li>C-3PO, the B1 and K-2SO walk with swinging arms and legs; talking makes them wave.</li>
 * <li>Chopper waddles, the mouse droid scurries, the probe droid floats and bobs.</li>
 * </ul>
 *
 * @param walkPos    distance walked, as vanilla's walk animation counts it
 * @param walkSpeed  0 standing, 1 running
 * @param headYaw    head turn against the body, in degrees (positive: to the droid's right)
 * @param headPitch  head tilt, in degrees (positive: looking down)
 * @param age        ticks since the droid appeared, with the partial tick
 * @param event      the last thing it did ({@code Droids.EVENT_*}), or 0
 * @param progress   how far that animation is, 0 to 1 (1 or more: over)
 */
public record DroidPose(float walkPos, float walkSpeed, float headYaw, float headPitch, float age, byte event, float progress) {
	public static final byte TALK = 1;
	public static final byte ABILITY = 2;
	public static final byte ARRIVE = 3;
	/** BB-8's ball radius, in blocks; the ball turns by way / radius. */
	private static final float BB8_RADIUS = 0.27F;

	public static DroidPose idle(float age) {
		return new DroidPose(0.0F, 0.0F, 0.0F, 0.0F, age, (byte) 0, 1.0F);
	}

	private boolean playing(byte which) {
		return this.event == which && this.progress < 1.0F;
	}

	/** A bump that goes 0 to 1 and back to 0 over an animation. */
	private float bump() {
		return Mth.sin(Math.clamp(this.progress, 0.0F, 1.0F) * Mth.PI);
	}

	/** The whole droid, before its parts: floating, scaling in, rocking. */
	public void root(PoseStack poseStack, DroidType droid) {
		if (this.playing(ARRIVE)) {
			float scale = 0.3F + 0.7F * ease(this.progress);
			poseStack.scale(scale, scale, scale);
		}

		switch (droid) {
			case PROBE -> {
				poseStack.translate(0.0F, 0.12F + Mth.sin(this.age * 0.09F) * 0.06F, 0.0F);
				poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(this.age * 0.05F) * 3.0F));
			}
			case MOUSE -> poseStack.translate(0.0F, Math.abs(Mth.sin(this.walkPos * 1.8F)) * 0.025F * this.walkSpeed, 0.0F);
			case R2D2 -> poseStack.mulPose(Axis.XP.rotationDegrees(-4.0F * this.walkSpeed + Mth.sin(this.walkPos * 0.7F) * 2.0F * this.walkSpeed));
			case CHOPPER -> poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(this.walkPos * 0.9F) * 6.0F * this.walkSpeed));
			default -> {
			}
		}
	}

	private static float ease(float t) {
		float x = Math.clamp(t, 0.0F, 1.0F);
		return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
	}

	/** One part, at its pivot: turns it. */
	public void part(PoseStack poseStack, DroidType droid, String part) {
		float swing = Mth.cos(this.walkPos * 0.6662F) * 1.2F * this.walkSpeed;

		switch (part) {
			case "head" -> {
				float yaw = -this.headYaw;
				float pitch = -this.headPitch;

				if (droid == DroidType.R2D2 && (this.playing(TALK) || this.playing(ABILITY))) {
					yaw += 360.0F * ease(this.progress);
				} else if (droid == DroidType.CHOPPER && this.playing(TALK)) {
					yaw += Mth.sin(this.progress * Mth.PI * 4.0F) * 45.0F;
				} else if (droid == DroidType.CHOPPER && this.playing(ABILITY)) {
					yaw += 360.0F * 2.0F * ease(this.progress);
				}

				poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

				if (droid == DroidType.BB8) {
					// Leans into the roll, and tilts the head when talked to.
					pitch -= 14.0F * this.walkSpeed;
					poseStack.mulPose(Axis.ZP.rotationDegrees(this.playing(TALK) ? 24.0F * this.bump() : 0.0F));
				}

				poseStack.mulPose(Axis.XP.rotationDegrees(Math.clamp(pitch, -35.0F, 35.0F)));
			}
			case "body" -> {
				if (droid == DroidType.BB8) {
					poseStack.mulPose(Axis.XP.rotation(-this.walkPos / (4.0F * BB8_RADIUS)));
					if (this.playing(ABILITY)) {
						poseStack.mulPose(Axis.YP.rotationDegrees(360.0F * ease(this.progress)));
					}
				} else if (droid == DroidType.PROBE) {
					poseStack.mulPose(Axis.YP.rotationDegrees(-this.headYaw + (this.playing(ABILITY) ? 360.0F * ease(this.progress) : 0.0F)));
				} else if (droid == DroidType.MOUSE && (this.playing(TALK) || this.playing(ABILITY))) {
					poseStack.mulPose(Axis.YP.rotationDegrees(360.0F * ease(this.progress)));
				}
			}
			case "leg_l" -> poseStack.mulPose(Axis.XP.rotation(swing));
			case "leg_r" -> poseStack.mulPose(Axis.XP.rotation(-swing));
			case "arm_l" -> this.arm(poseStack, droid, -swing * 0.8F, false);
			case "arm_r" -> this.arm(poseStack, droid, swing * 0.8F, true);
			default -> {
			}
		}
	}

	private void arm(PoseStack poseStack, DroidType droid, float swing, boolean right) {
		if (this.playing(TALK) && right) {
			// A wave: the arm up to the side, waving.
			float up = this.bump();
			poseStack.mulPose(Axis.ZP.rotationDegrees(-150.0F * Math.min(1.0F, up * 2.0F)));
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(this.progress * Mth.PI * 6.0F) * 18.0F * up));
			return;
		}

		if (this.playing(ABILITY)) {
			float up = Math.min(1.0F, this.bump() * 2.5F);

			switch (droid) {
				case B1 -> {
					// Aims with both arms.
					poseStack.mulPose(Axis.XP.rotationDegrees(90.0F * up));
					return;
				}
				case K2SO -> {
					if (right) {
						poseStack.mulPose(Axis.XP.rotationDegrees(100.0F * up));
						return;
					}
				}
				case C3PO -> {
					// Arms up in alarm, shaking.
					poseStack.mulPose(Axis.XP.rotationDegrees((70.0F + Mth.sin(this.progress * Mth.PI * 8.0F) * 12.0F) * up));
					poseStack.mulPose(Axis.ZP.rotationDegrees((right ? -20.0F : 20.0F) * up));
					return;
				}
				default -> {
				}
			}
		}

		poseStack.mulPose(Axis.XP.rotation(swing));
	}
}
