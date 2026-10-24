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
 * @param hitKnockback   extra knockback of a melee hit with the saber, in blocks per tick (Phase 8)
 */
public record StyleTraits(float blockBonus, int parryBonus, float blockCost, float dodgeCooldown, float dodgeCost, int spinStrike, float hitKnockback) {
	public static final StyleTraits FLOWING = new StyleTraits(0.0F, 0, 1.0F, 1.0F, 1.0F, 5, 0.0F);
	public static final StyleTraits HEAVY = new StyleTraits(0.05F, -1, 0.9F, 1.2F, 1.2F, 6, 0.15F);
	/** Standhaft: long parry window, strong and cheap blocks, slow dodges. */
	public static final StyleTraits DEFENSIVE = new StyleTraits(0.2F, 4, 0.6F, 1.25F, 1.0F, 5, 0.0F);
	/** Wendig: weak blocks, but quick and cheap dodges. */
	public static final StyleTraits AGILE = new StyleTraits(-0.15F, -1, 1.3F, 0.5F, 0.6F, 4, 0.0F);
	public static final StyleTraits STAFF = new StyleTraits(0.0F, 0, 1.0F, 1.0F, 1.0F, 5, 0.0F);
	/** Djem So (Vader): a firm guard and crushing blows that throw the target back, but slow, costly dodges. */
	public static final StyleTraits DJEM_SO = new StyleTraits(0.12F, 1, 0.8F, 1.35F, 1.3F, 7, 0.35F);
	/** Soresu (Obi-Wan): the strongest defense, the longest parry window and the cheapest blocks. */
	public static final StyleTraits SORESU = new StyleTraits(0.28F, 6, 0.5F, 1.2F, 1.0F, 5, 0.0F);
	/** Juyo (Maul): fast spins with the double blade and quick dodges, but a weaker guard. */
	public static final StyleTraits JUYO = new StyleTraits(-0.08F, 0, 1.15F, 0.7F, 0.8F, 4, 0.1F);

	public static StyleTraits of(@Nullable Identifier style) {
		if (SaberVariant.HEAVY.equals(style)) {
			return HEAVY;
		} else if (SaberVariant.DEFENSIVE.equals(style)) {
			return DEFENSIVE;
		} else if (SaberVariant.AGILE.equals(style)) {
			return AGILE;
		} else if (SaberVariant.STAFF.equals(style)) {
			return STAFF;
		} else if (SaberVariant.DJEM_SO.equals(style)) {
			return DJEM_SO;
		} else if (SaberVariant.SORESU.equals(style)) {
			return SORESU;
		} else if (SaberVariant.JUYO.equals(style)) {
			return JUYO;
		}

		return FLOWING;
	}

	/** The share of a frontal hit that the stance blocks with this style. */
	public float blockShare(float base) {
		return Mth.clamp(base + this.blockBonus, 0.0F, 0.95F);
	}
}
