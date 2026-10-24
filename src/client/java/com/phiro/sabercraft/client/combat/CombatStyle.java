package com.phiro.sabercraft.client.combat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

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
		ThirdPersonPose guardThirdPerson
) {
}
