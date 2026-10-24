package com.phiro.sabercraft.ability;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.saber.SaberVariant;

/**
 * How a fighting style changes the defense and the dodges on the server. The animations of a style
 * are in {@code moves.json}; these numbers make the styles also play differently.
 *
 * @param blockBonus     added to the blocked share of a hit (negative blocks less)
 * @param parryBonus     extra ticks of the parry window after raising the stance
 * @param blockCost      multiplier of the stamina a blocked hit costs
 * @param dodgeCooldown  multiplier of the dodge cooldown
 * @param dodgeCost      multiplier of the stamina a dodge costs
 * @param spinStrike     tick of the spin attack at which it hits, matching the animation
 */
public record StyleTraits(float blockBonus, int parryBonus, float blockCost, float dodgeCooldown, float dodgeCost, int spinStrike) {
	public static final StyleTraits FLOWING = new StyleTraits(0.0F, 0, 1.0F, 1.0F, 1.0F, 5);
	public static final StyleTraits HEAVY = new StyleTraits(0.05F, -1, 0.9F, 1.2F, 1.2F, 6);
	/** Standhaft: long parry window, strong and cheap blocks, slow dodges. */
	public static final StyleTraits DEFENSIVE = new StyleTraits(0.2F, 4, 0.6F, 1.25F, 1.0F, 5);
	/** Wendig: weak blocks, but quick and cheap dodges. */
	public static final StyleTraits AGILE = new StyleTraits(-0.15F, -1, 1.3F, 0.5F, 0.6F, 4);
	public static final StyleTraits STAFF = new StyleTraits(0.0F, 0, 1.0F, 1.0F, 1.0F, 5);

	public static StyleTraits of(@Nullable Identifier style) {
		if (SaberVariant.HEAVY.equals(style)) {
			return HEAVY;
		} else if (SaberVariant.DEFENSIVE.equals(style)) {
			return DEFENSIVE;
		} else if (SaberVariant.AGILE.equals(style)) {
			return AGILE;
		} else if (SaberVariant.STAFF.equals(style)) {
			return STAFF;
		}

		return FLOWING;
	}

	/** The share of a frontal hit that the stance blocks with this style. */
	public float blockShare(float base) {
		return Mth.clamp(base + this.blockBonus, 0.0F, 0.95F);
	}
}
