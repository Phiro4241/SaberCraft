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
 *   <li>{@code mainLeg}, {@code offLeg} (since Phase 7): angles of the leg on the saber side and of
 *       the other leg, like the arms (x negative = forward). Only applied with
 *       {@code legWeight} &gt; 0, otherwise the legs keep vanilla's walking.</li>
 *   <li>{@code lean}: the whole upper body bends forward at the hips (degrees), the legs stay where
 *       they are. Unlike {@code body[0]} the head and the arms go along.</li>
 *   <li>{@code root}: turn of the whole figure around its middle for rolls and flips: pitch
 *       (positive = head forward), roll (positive = to the figure's right) and a lift in blocks.
 *       The root is not mirrored for left-handed holders, a roll to the right stays a roll to the
 *       right.</li>
 *   <li>{@code crouch}: lowers the figure in blocks, on top of the drop the spread legs need to
 *       stay on the ground.</li>
 * </ul>
 */
public final class ThirdPersonPose {
	public final float[] mainArm = new float[3];
	public final float[] offArm = new float[3];
	public float offArmWeight;
	public final float[] body = new float[2];
	public float spin;
	public final float[] item = new float[3];
	public final float[] mainLeg = new float[3];
	public final float[] offLeg = new float[3];
	public float legWeight;
	public float lean;
	public final float[] root = new float[3];
	public float crouch;

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
		System.arraycopy(other.mainLeg, 0, this.mainLeg, 0, 3);
		System.arraycopy(other.offLeg, 0, this.offLeg, 0, 3);
		this.legWeight = other.legWeight;
		this.lean = other.lean;
		System.arraycopy(other.root, 0, this.root, 0, 3);
		this.crouch = other.crouch;
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

		// Legs fade in and out like the other arm.
		if (target.legWeight > 0.0F && this.legWeight > 0.0F) {
			for (int i = 0; i < 3; i++) {
				this.mainLeg[i] = Mth.lerp(amount, this.mainLeg[i], target.mainLeg[i]);
				this.offLeg[i] = Mth.lerp(amount, this.offLeg[i], target.offLeg[i]);
			}
		} else if (target.legWeight > 0.0F) {
			System.arraycopy(target.mainLeg, 0, this.mainLeg, 0, 3);
			System.arraycopy(target.offLeg, 0, this.offLeg, 0, 3);
		}

		this.legWeight = Mth.lerp(amount, this.legWeight, target.legWeight);
		this.lean = Mth.lerp(amount, this.lean, target.lean);

		for (int i = 0; i < 3; i++) {
			this.root[i] = Mth.lerp(amount, this.root[i], target.root[i]);
		}

		this.crouch = Mth.lerp(amount, this.crouch, target.crouch);
		return this;
	}
}
