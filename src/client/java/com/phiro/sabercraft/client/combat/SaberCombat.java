package com.phiro.sabercraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * Connects the fighting animation with Minecraft's rendering. The mixins in
 * {@code com.phiro.sabercraft.mixin.client} call into this class:
 *
 * <ul>
 *   <li>first person: {@link #firstPerson} replaces vanilla's item position and swing,</li>
 *   <li>third person: {@link #thirdPerson} is stored on the entity's render state and
 *       {@link #applyThirdPerson} poses the arms and body of the humanoid model,</li>
 *   <li>{@link #applyItemTurn} turns the saber in the hand, for wrist movements and the twirl of
 *       the double-bladed staff.</li>
 * </ul>
 */
public final class SaberCombat {
	/** Fighting pose of one entity, extracted once per frame for its render state. */
	public static final RenderStateDataKey<ThirdPersonData> THIRD_PERSON = RenderStateDataKey.create(() -> "sabercraft:combat");

	/**
	 * Where the grip of a hilt is in the third-person item space, after vanilla's handheld
	 * transform. The saber turns around this point.
	 */
	private static final float TP_PIVOT_Y = 0.0041208F;
	private static final float TP_PIVOT_Z = 0.0746052F;
	/** Length of a humanoid leg in blocks, from the hip to the sole. */
	private static final float LEG_LENGTH = 0.75F;
	/** Height of the middle of a humanoid figure, the point rolls and flips turn around. */
	private static final float BODY_CENTER = 0.9F;

	private SaberCombat() {
	}

	/**
	 * @param pose       angles for a right-handed holder
	 * @param weight     how much the pose replaces vanilla's own animation, 0..1
	 * @param saberArm   the arm that holds the animated saber
	 * @param offArmFree whether the other hand is empty, so it can join a two-handed grip
	 * @param moving     whether a move is running (not only the resting pose)
	 */
	public record ThirdPersonData(ThirdPersonPose pose, float weight, HumanoidArm saberArm, boolean offArmFree, boolean moving) {
		/** How far the figure sinks so the spread legs still reach the ground, in blocks. */
		public float drop() {
			float legs = this.pose.legWeight * this.weight;

			if (legs <= 0.0F && this.pose.crouch == 0.0F) {
				return 0.0F;
			}

			// The more upright leg decides: the figure sinks as far as that leg rises, so neither
			// foot goes into the ground. Leaning at the hips swings both legs back by the lean.
			float lean = this.pose.lean * Mth.DEG_TO_RAD;
			float main = upright(this.pose.mainLeg, lean);
			float off = upright(this.pose.offLeg, lean);
			return LEG_LENGTH * (1.0F - Math.max(main, off)) * legs + this.pose.crouch * this.weight;
		}

		private static float upright(float[] leg, float lean) {
			return Mth.cos(leg[0] * Mth.DEG_TO_RAD + lean) * Mth.cos(leg[2] * Mth.DEG_TO_RAD);
		}
	}

	/** The first-person pose of a saber in {@code hand}, or {@code null} for vanilla's animation. */
	public static @Nullable FirstPersonPose firstPerson(LivingEntity player, InteractionHand hand, float partialTick) {
		SaberTracker tracker = SaberTracker.INSTANCE;
		CombatState state = tracker.combat(player);

		if (state == null || state.style() == null || tracker.combatHand(player) != hand) {
			return null;
		}

		SaberHandState blade = tracker.state(player, hand);

		if (blade == null) {
			return null;
		}

		float now = tracker.time(partialTick);
		FirstPersonPose pose = state.firstPerson(now, new FirstPersonPose());
		float weight = state.weight(now, partialTick);
		return weight >= 1.0F ? pose : FirstPersonPose.REST.copy().lerpTo(pose, weight);
	}

	/** The third-person data of an entity for this frame, or {@code null}. */
	public static @Nullable ThirdPersonData thirdPerson(LivingEntity entity, float partialTick) {
		SaberTracker tracker = SaberTracker.INSTANCE;
		CombatState state = tracker.combat(entity);

		if (state == null || state.style() == null) {
			return null;
		}

		float now = tracker.time(partialTick);
		float weight = state.weight(now, partialTick);

		if (weight <= 0.0F) {
			return null;
		}

		InteractionHand hand = tracker.combatHand(entity);
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ThirdPersonPose pose = state.thirdPerson(now, ThirdPersonPose.rest());
		return new ThirdPersonData(pose, weight, arm, entity.getItemInHand(other).isEmpty(), state.move() != null);
	}

	/**
	 * Moves the whole figure: lowers it, turns it for rolls and flips and bends it at the hips.
	 * Called from the renderer's body rotation, before the model is drawn.
	 */
	public static void applyRoot(PoseStack poseStack, ThirdPersonData data, float scale) {
		ThirdPersonPose pose = data.pose();
		float weight = data.weight();
		float lift = (pose.root[2] * weight - data.drop()) * scale;

		if (lift != 0.0F) {
			poseStack.translate(0.0F, lift, 0.0F);
		}

		float pitch = pose.root[0] * weight;
		float roll = pose.root[1] * weight;

		if (pitch != 0.0F || roll != 0.0F) {
			poseStack.translate(0.0F, BODY_CENTER * scale, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
			poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
			poseStack.translate(0.0F, -BODY_CENTER * scale, 0.0F);
		}

		float lean = pose.lean * weight;

		if (lean != 0.0F) {
			poseStack.translate(0.0F, LEG_LENGTH * scale, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
			poseStack.translate(0.0F, -LEG_LENGTH * scale, 0.0F);
		}
	}

	/** Poses the model in place of vanilla's attack animation. */
	public static void applyThirdPerson(HumanoidModel<?> model, HumanoidRenderState state, ThirdPersonData data) {
		ThirdPersonPose pose = data.pose();
		float weight = data.weight();
		boolean right = data.saberArm() == HumanoidArm.RIGHT;
		float mirror = right ? 1.0F : -1.0F;
		applyLegs(model, state, data, right, mirror);

		float bodyYaw = pose.body[1] * Mth.DEG_TO_RAD * mirror * weight;
		model.body.yRot = bodyYaw;
		model.body.xRot += pose.body[0] * Mth.DEG_TO_RAD * weight;

		if (Math.abs(bodyYaw) > 1.0E-4F) {
			// Like vanilla's attack animation: the shoulders turn with the body.
			float ageScale = state.ageScale;
			model.rightArm.z = Mth.sin(bodyYaw) * 5.0F * ageScale;
			model.rightArm.x = -Mth.cos(bodyYaw) * 5.0F * ageScale;
			model.leftArm.z = -Mth.sin(bodyYaw) * 5.0F * ageScale;
			model.leftArm.x = Mth.cos(bodyYaw) * 5.0F * ageScale;
		}

		ModelPart saberArm = model.getArm(data.saberArm());
		ModelPart otherArm = model.getArm(data.saberArm().getOpposite());
		// A little of the walking swing stays, so a fighter does not look frozen while moving.
		float walk = state.walkAnimationSpeed / Math.max(0.01F, state.speedValue);
		float phase = state.walkAnimationPos * 0.6662F;
		float saberWalk = Mth.cos(phase + (right ? Mth.PI : 0.0F)) * walk * 0.3F;
		float otherWalk = Mth.cos(phase + (right ? 0.0F : Mth.PI)) * walk * 0.3F;

		pose(saberArm, pose.mainArm, mirror, bodyYaw, saberWalk, weight);

		float otherWeight = data.offArmFree() ? weight * pose.offArmWeight : 0.0F;

		if (otherWeight > 0.0F) {
			pose(otherArm, pose.offArm, mirror, bodyYaw, otherWalk, otherWeight);
		} else {
			otherArm.yRot += bodyYaw;
		}
	}

	/**
	 * The legs: the move's steps while standing, mostly vanilla's walking while moving, so a fighter
	 * never slides over the ground with frozen legs. A bend at the hips turns the legs back
	 * against it, so the feet stay where they are.
	 */
	private static void applyLegs(HumanoidModel<?> model, HumanoidRenderState state, ThirdPersonData data, boolean right, float mirror) {
		ThirdPersonPose pose = data.pose();
		float weight = data.weight();
		float walk = Mth.clamp(state.walkAnimationSpeed / Math.max(0.01F, state.speedValue) * 1.6F, 0.0F, 1.0F);
		float legWeight = weight * pose.legWeight * (1.0F - 0.7F * walk);
		ModelPart mainLeg = right ? model.rightLeg : model.leftLeg;
		ModelPart offLeg = right ? model.leftLeg : model.rightLeg;

		if (legWeight > 0.0F && !state.isPassenger) {
			leg(mainLeg, pose.mainLeg, mirror, legWeight);
			leg(offLeg, pose.offLeg, mirror, legWeight);
		}

		float lean = pose.lean * weight * Mth.DEG_TO_RAD;

		if (lean != 0.0F) {
			model.rightLeg.xRot -= lean;
			model.leftLeg.xRot -= lean;
		}
	}

	private static void leg(ModelPart leg, float[] angles, float mirror, float weight) {
		// Keeps vanilla's walking swing on top of the stance.
		float walking = leg.xRot;
		leg.xRot = Mth.lerp(weight, walking, angles[0] * Mth.DEG_TO_RAD + walking * 0.35F);
		leg.yRot = Mth.lerp(weight, leg.yRot, angles[1] * Mth.DEG_TO_RAD * mirror);
		leg.zRot = Mth.lerp(weight, leg.zRot, angles[2] * Mth.DEG_TO_RAD * mirror);
	}

	/**
	 * The first-person camera movement of the local player's current move, written as dip
	 * (blocks), pitch and roll (degrees) into {@code out}. Returns false when there is none.
	 */
	public static boolean camera(LivingEntity player, float partialTick, float[] out) {
		SaberTracker tracker = SaberTracker.INSTANCE;
		CombatState state = tracker.combat(player);

		if (state == null || state.style() == null || state.move() == null) {
			return false;
		}

		FirstPersonPose pose = state.firstPerson(tracker.time(partialTick), new FirstPersonPose());
		System.arraycopy(pose.camera, 0, out, 0, 3);
		return out[0] != 0.0F || out[1] != 0.0F || out[2] != 0.0F;
	}

	private static void pose(ModelPart arm, float[] angles, float mirror, float bodyYaw, float walk, float weight) {
		arm.xRot = Mth.lerp(weight, arm.xRot, angles[0] * Mth.DEG_TO_RAD + walk);
		arm.yRot = Mth.lerp(weight, arm.yRot, angles[1] * Mth.DEG_TO_RAD * mirror + bodyYaw);
		arm.zRot = Mth.lerp(weight, arm.zRot, angles[2] * Mth.DEG_TO_RAD * mirror);
	}

	/** Turns the held saber around its grip. Called right before the item in the hand is drawn. */
	public static void applyItemTurn(PoseStack poseStack, ThirdPersonData data, HumanoidArm arm) {
		if (arm != data.saberArm()) {
			return;
		}

		float[] item = data.pose().item;
		float weight = data.weight();

		if (item[0] == 0.0F && item[1] == 0.0F && item[2] == 0.0F) {
			return;
		}

		float mirror = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		poseStack.translate(0.0F, TP_PIVOT_Y, TP_PIVOT_Z);
		poseStack.mulPose(Axis.YP.rotationDegrees(item[1] * mirror * weight));
		poseStack.mulPose(Axis.XP.rotationDegrees(item[0] * weight));
		poseStack.mulPose(Axis.ZP.rotationDegrees(item[2] * mirror * weight));
		poseStack.translate(0.0F, -TP_PIVOT_Y, -TP_PIVOT_Z);
	}
}
