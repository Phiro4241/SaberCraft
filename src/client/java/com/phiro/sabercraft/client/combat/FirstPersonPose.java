package com.phiro.sabercraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;

/**
 * Where the saber is in the first-person view.
 *
 * <p>The values are chosen so moves can be written down the way they look on screen:
 *
 * <ul>
 *   <li>{@code grip}: where the hand holds the hilt. X and Y are screen positions in half screen
 *       heights from the screen center (right and up are positive), so 0/0 is the crosshair and
 *       Y = -1 the bottom edge. Z moves the hand further away from the camera.</li>
 *   <li>{@code aim}: where the blade points. Elevation in degrees above the horizon (90 = straight
 *       up), azimuth in degrees to the left of straight ahead (90 = to the left, -90 = to the
 *       right) and a roll around the blade, which only turns the hilt.</li>
 * </ul>
 *
 * <p>For the left hand everything is mirrored. Vanilla's resting position of a held sword is
 * {@link #REST}: the hand at the bottom right, the blade leaning back by 20 degrees.
 */
public final class FirstPersonPose {
	/** Vanilla's first-person position of a held sword, without any swing. */
	public static final FirstPersonPose REST = new FirstPersonPose(1.2551F, -1.0103F, -0.0023F, 110.0F, 0.0F, 0.0F);

	/** Distance of the vanilla item position from the camera. */
	private static final float BASE_DEPTH = 0.72F;
	/** tan(35 degrees): half the vertical field of view of the hand rendering (70 degrees). */
	private static final float TAN_HALF_FOV = 0.70020753F;
	/** Vanilla's item position in the hand space, see ItemInHandRenderer.applyItemArmTransform. */
	private static final float ARM_X = 0.56F;
	private static final float ARM_Y = -0.52F;
	private static final float ARM_Z = -0.72F;
	/**
	 * Where the grip of a hilt ends up after the vanilla handheld first-person transform, relative
	 * to the item position. All SaberCraft hilts are held at the same model point.
	 */
	private static final float PIVOT_X = 0.070625F;
	private static final float PIVOT_Y = 0.0123078F;
	private static final float PIVOT_Z = 0.0023106F;
	/** The handheld transform leans the blade back by 20 degrees: pitch 0 is 110 degrees elevation. */
	private static final float REST_ELEVATION = 110.0F;

	public float gripX;
	public float gripY;
	public float gripZ;
	public float elevation;
	public float azimuth;
	public float roll;

	public FirstPersonPose() {
		this.set(REST);
	}

	public FirstPersonPose(float gripX, float gripY, float gripZ, float elevation, float azimuth, float roll) {
		this.gripX = gripX;
		this.gripY = gripY;
		this.gripZ = gripZ;
		this.elevation = elevation;
		this.azimuth = azimuth;
		this.roll = roll;
	}

	public FirstPersonPose set(FirstPersonPose other) {
		this.gripX = other.gripX;
		this.gripY = other.gripY;
		this.gripZ = other.gripZ;
		this.elevation = other.elevation;
		this.azimuth = other.azimuth;
		this.roll = other.roll;
		return this;
	}

	public FirstPersonPose copy() {
		return new FirstPersonPose().set(this);
	}

	/** Moves this pose towards {@code target} by {@code amount} (0 = unchanged, 1 = target). */
	public FirstPersonPose lerpTo(FirstPersonPose target, float amount) {
		this.gripX = Mth.lerp(amount, this.gripX, target.gripX);
		this.gripY = Mth.lerp(amount, this.gripY, target.gripY);
		this.gripZ = Mth.lerp(amount, this.gripZ, target.gripZ);
		this.elevation = Mth.lerp(amount, this.elevation, target.elevation);
		this.azimuth = Mth.lerp(amount, this.azimuth, target.azimuth);
		this.roll = Mth.lerp(amount, this.roll, target.roll);
		return this;
	}

	void setGrip(float[] values) {
		this.gripX = values[0];
		this.gripY = values[1];
		this.gripZ = values[2];
	}

	void setAim(float[] values) {
		this.elevation = values[0];
		this.azimuth = values[1];
		this.roll = values[2];
	}

	/**
	 * Applies the pose in place of vanilla's item position and swing. Expects the pose stack of the
	 * first-person hand rendering, before the item is drawn.
	 *
	 * @param rightHand         whether the item is in the right hand
	 * @param inverseArmHeight  vanilla's lowering while the item is swapped (0 = fully raised)
	 */
	public void apply(PoseStack poseStack, boolean rightHand, float inverseArmHeight) {
		float invert = rightHand ? 1.0F : -1.0F;
		float depth = BASE_DEPTH + this.gripZ;
		float targetX = invert * this.gripX * TAN_HALF_FOV * depth;
		float targetY = this.gripY * TAN_HALF_FOV * depth;
		float targetZ = -depth;

		// Vanilla's item position including the swap lowering, then on to the grip target.
		poseStack.translate(invert * ARM_X, ARM_Y + inverseArmHeight * -0.6F, ARM_Z);
		poseStack.translate(targetX - invert * ARM_X, targetY - ARM_Y, targetZ - ARM_Z);
		poseStack.mulPose(Axis.YP.rotationDegrees(invert * this.azimuth));
		poseStack.mulPose(Axis.XP.rotationDegrees(this.elevation - REST_ELEVATION));
		poseStack.mulPose(Axis.ZP.rotationDegrees(invert * this.roll));
		poseStack.translate(-invert * PIVOT_X, -PIVOT_Y, -PIVOT_Z);
	}
}
