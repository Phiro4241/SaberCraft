package com.phiro.sabercraft.client.combat;

import net.minecraft.util.Mth;

/**
 * Body and arm angles of a saber move seen from outside, in degrees, for a right-handed holder.
 * Left-handed holders get the mirror image.
 *
 * <ul>
 *   <li>{@code mainArm}: rotation of the arm holding the saber (x = raise forward when negative,
 *       y = swing to the side, z = lift outwards), like vanilla's {@code ModelPart} angles.</li>
 *   <li>{@code offArm}: the other arm, used for two-handed grips. Only applied if the other hand
 *       is free.</li>
 *   <li>{@code body}: lean forward (x) and twist of the upper body (y, positive turns the saber
 *       shoulder back).</li>
 *   <li>{@code spin}: turn of the whole body in degrees, for spinning attacks.</li>
 *   <li>{@code item}: extra turn of the saber in the hand around the grip, like a wrist movement
 *       or the twirl of the double-bladed staff.</li>
 * </ul>
 */
public final class ThirdPersonPose {
	public final float[] mainArm = new float[3];
	public final float[] offArm = new float[3];
	public float offArmWeight;
	public final float[] body = new float[2];
	public float spin;
	public final float[] item = new float[3];

	/** Vanilla-like resting angles: the saber arm slightly forward, nothing else. */
	public static ThirdPersonPose rest() {
		ThirdPersonPose pose = new ThirdPersonPose();
		pose.mainArm[0] = -18.0F;
		return pose;
	}

	public ThirdPersonPose set(ThirdPersonPose other) {
		System.arraycopy(other.mainArm, 0, this.mainArm, 0, 3);
		System.arraycopy(other.offArm, 0, this.offArm, 0, 3);
		this.offArmWeight = other.offArmWeight;
		System.arraycopy(other.body, 0, this.body, 0, 2);
		this.spin = other.spin;
		System.arraycopy(other.item, 0, this.item, 0, 3);
		return this;
	}

	public ThirdPersonPose copy() {
		return new ThirdPersonPose().set(this);
	}

	public ThirdPersonPose lerpTo(ThirdPersonPose target, float amount) {
		for (int i = 0; i < 3; i++) {
			this.mainArm[i] = Mth.lerp(amount, this.mainArm[i], target.mainArm[i]);
			this.item[i] = Mth.lerp(amount, this.item[i], target.item[i]);
		}

		// The other arm fades in and out on its own, so one-handed moves leave it to vanilla.
		if (target.offArmWeight > 0.0F && this.offArmWeight > 0.0F) {
			for (int i = 0; i < 3; i++) {
				this.offArm[i] = Mth.lerp(amount, this.offArm[i], target.offArm[i]);
			}
		} else if (target.offArmWeight > 0.0F) {
			System.arraycopy(target.offArm, 0, this.offArm, 0, 3);
		}

		this.offArmWeight = Mth.lerp(amount, this.offArmWeight, target.offArmWeight);

		for (int i = 0; i < 2; i++) {
			this.body[i] = Mth.lerp(amount, this.body[i], target.body[i]);
		}

		this.spin = Mth.lerp(amount, this.spin, target.spin);
		return this;
	}
}
