package com.phiro.sabercraft.client.combat;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.ability.DodgeDirection;

/**
 * A fighting style: which moves follow each other, how long a combo lasts and how the saber is
 * held between attacks. Styles are defined in {@code assets/sabercraft/combat/moves.json}.
 *
 * @param id               style id, the same as in {@code SaberVariant.STYLES}
 * @param combo            moves of the normal attack chain, played one after another
 * @param sprint           move for an attack while sprinting, or {@code null}
 * @param jump             move for an attack while falling after a jump, or {@code null}
 * @param special          move of the style's special ability (spin attack or blade whirl)
 * @param comboWindow      ticks after the start of an attack in which the next attack continues
 *                         the combo; later attacks start it from the beginning
 * @param idleFirstPerson  resting pose in first person
 * @param idleThirdPerson  resting pose seen from outside
 * @param guardFirstPerson defensive stance in first person
 * @param guardThirdPerson defensive stance seen from outside
 * @param runThirdPerson   pose while sprinting with a lit saber, or {@code null} for vanilla's arms
 * @param airThirdPerson   pose while in the air after a jump, or {@code null}
 * @param dodges           animation of the dodge key per direction (since Phase 7), see
 *                         {@code SaberManeuver} for the motion
 * @param land             move after landing from a higher jump, or {@code null}
 * @param landHeavy        move after a long fall or a force jump, or {@code null}
 * @param guardHit         the blade gives way under a blocked hit
 * @param parry            a successful parry
 * @param guardBreak       the stance breaks when the stamina runs out
 */
public record CombatStyle(
		Identifier id,
		List<CombatMove> combo,
		@Nullable CombatMove sprint,
		@Nullable CombatMove jump,
		@Nullable CombatMove special,
		float comboWindow,
		FirstPersonPose idleFirstPerson,
		ThirdPersonPose idleThirdPerson,
		FirstPersonPose guardFirstPerson,
		ThirdPersonPose guardThirdPerson,
		@Nullable ThirdPersonPose runThirdPerson,
		@Nullable ThirdPersonPose airThirdPerson,
		Map<DodgeDirection, CombatMove> dodges,
		@Nullable CombatMove land,
		@Nullable CombatMove landHeavy,
		@Nullable CombatMove guardHit,
		@Nullable CombatMove parry,
		@Nullable CombatMove guardBreak
) {
}
