package com.phiro.sabercraft.client.combat;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * One attack movement, for example a diagonal cut or a spinning slash. Moves are defined in
 * {@code assets/sabercraft/combat/moves.json}, see {@link CombatLibrary}.
 *
 * <p>A move runs on game ticks from 0 to {@link #duration}. It fades in from wherever the saber
 * was during {@link #blendIn} ticks and back into the resting pose of its style during the last
 * {@link #blendOut} ticks, so moves always connect smoothly, also when one interrupts another.
 *
 * @param id          unique id
 * @param duration    length in ticks
 * @param strike      tick at which the blade moves fastest. The swing sound and the hit effects
 *                    are timed to it.
 * @param interrupt   from this tick on, a new attack may cut the move short and start the next
 * @param blendIn     ticks to fade in from the previous pose
 * @param blendOut    ticks to fade out into the resting pose
 * @param sound       which sound the strike makes
 * @param volume      loudness of that sound
 * @param trail       whether the blade leaves a light trail, off for moves like the force push
 * @param fpGrip      first person: grip position track, or {@code null}
 * @param fpAim       first person: blade direction track, or {@code null}
 * @param tpMainArm   third person: angles of the saber arm
 * @param tpOffArm    third person: angles of the other arm, or {@code null} for a one-handed move
 * @param tpBody      third person: body lean and twist
 * @param tpSpin      third person: turn of the whole body
 * @param tpItem      third person: turn of the saber in the hand
 * @param fpCamera    first person: dip, pitch and roll of the camera, or {@code null}
 * @param tpMainLeg   third person: leg on the saber side, or {@code null} to keep the resting legs
 * @param tpOffLeg    third person: the other leg
 * @param tpLean      third person: bend of the upper body at the hips
 * @param tpRoot      third person: pitch, roll and lift of the whole figure
 * @param tpCrouch    third person: extra lowering of the figure in blocks
 */
public record CombatMove(
		Identifier id,
		float duration,
		float strike,
		float interrupt,
		float blendIn,
		float blendOut,
		StrikeSound sound,
		float volume,
		boolean trail,
		@Nullable KeyTrack fpGrip,
		@Nullable KeyTrack fpAim,
		@Nullable KeyTrack tpMainArm,
		@Nullable KeyTrack tpOffArm,
		@Nullable KeyTrack tpBody,
		@Nullable KeyTrack tpSpin,
		@Nullable KeyTrack tpItem,
		@Nullable KeyTrack fpCamera,
		@Nullable KeyTrack tpMainLeg,
		@Nullable KeyTrack tpOffLeg,
		@Nullable KeyTrack tpLean,
		@Nullable KeyTrack tpRoot,
		@Nullable KeyTrack tpCrouch
) {
	/** The sound played at the strike of a move. */
	public enum StrikeSound {
		/** The swing sound of the saber's own sound profile. */
		SWING,
		/** The swing sound, deeper and louder, for heavy blows. */
		HEAVY,
		/** A long whoosh for spins. */
		SPIN,
		/** No sound. */
		NONE
	}

	private static final ThreadLocal<float[]> BUFFER = ThreadLocal.withInitial(() -> new float[3]);

	/** Writes the raw first-person pose at {@code age} ticks into {@code out}. */
	public void firstPerson(float age, FirstPersonPose out) {
		float[] buffer = BUFFER.get();

		if (this.fpGrip != null) {
			this.fpGrip.sample(age, buffer);
			out.setGrip(buffer);
		}

		if (this.fpAim != null) {
			this.fpAim.sample(age, buffer);
			out.setAim(buffer);
		}

		if (this.fpCamera != null) {
			this.fpCamera.sample(age, out.camera);
		}
	}

	/** Writes the raw third-person pose at {@code age} ticks into {@code out}. */
	public void thirdPerson(float age, ThirdPersonPose out) {
		float[] buffer = BUFFER.get();

		if (this.tpMainArm != null) {
			this.tpMainArm.sample(age, out.mainArm);
		}

		if (this.tpOffArm != null) {
			this.tpOffArm.sample(age, out.offArm);
			out.offArmWeight = 1.0F;
		} else {
			out.offArmWeight = 0.0F;
		}

		if (this.tpBody != null) {
			this.tpBody.sample(age, out.body);
		} else {
			out.body[0] = 0.0F;
			out.body[1] = 0.0F;
		}

		if (this.tpSpin != null) {
			this.tpSpin.sample(age, buffer);
			// After the last keyframe a full turn counts as none, so blending out does not turn back.
			out.spin = age >= this.tpSpin.lastTime() ? Mth.wrapDegrees(buffer[0]) : buffer[0];
		} else {
			out.spin = 0.0F;
		}

		if (this.tpItem != null) {
			this.tpItem.sample(age, out.item);
		} else {
			out.item[0] = 0.0F;
			out.item[1] = 0.0F;
			out.item[2] = 0.0F;
		}

		// The whole-body tracks keep the resting pose's values when a move does not set them.
		if (this.tpMainLeg != null) {
			this.tpMainLeg.sample(age, out.mainLeg);
			out.legWeight = 1.0F;
		}

		if (this.tpOffLeg != null) {
			this.tpOffLeg.sample(age, out.offLeg);
			out.legWeight = 1.0F;
		}

		if (this.tpLean != null) {
			this.tpLean.sample(age, buffer);
			out.lean = buffer[0];
		}

		if (this.tpRoot != null) {
			this.tpRoot.sample(age, out.root);

			if (age >= this.tpRoot.lastTime()) {
				out.root[0] = Mth.wrapDegrees(out.root[0]);
				out.root[1] = Mth.wrapDegrees(out.root[1]);
			}
		}

		if (this.tpCrouch != null) {
			this.tpCrouch.sample(age, buffer);
			out.crouch = buffer[0];
		}
	}
}
