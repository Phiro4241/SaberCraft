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

	/** Ansturm (Anakin): aggressive blows that push the target back, quick dodges, a weaker guard. */
	public static final StyleTraits ONSLAUGHT = new StyleTraits(-0.05F, 0, 1.1F, 0.8F, 0.9F, 5, 0.22F);
	/** Ataru (Yoda): the fastest, cheapest dodges and leaps, but a weak, costly guard. */
	public static final StyleTraits ATARU = new StyleTraits(-0.18F, 1, 1.35F, 0.45F, 0.55F, 4, 0.05F);
	/** Makashi (Dooku): precise fencing, a long parry window and economical blocks, no extra knockback. */
	public static final StyleTraits MAKASHI = new StyleTraits(0.1F, 5, 0.7F, 1.0F, 0.9F, 5, 0.0F);
	/** Reverse Shien (Ahsoka): close and quick, fast dodges, an average guard. */
	public static final StyleTraits REVERSE_SHIEN = new StyleTraits(-0.05F, 2, 1.0F, 0.6F, 0.7F, 4, 0.08F);
	/** Ungezügelt (Kylo Ren): the hardest blows and the furthest knockback, but a poor, costly guard. */
	public static final StyleTraits UNBRIDLED = new StyleTraits(-0.1F, -2, 1.4F, 1.25F, 1.2F, 6, 0.45F);
	/** Shii-Cho (Rey): wide, sweeping cuts and a solid, all-round guard. */
	public static final StyleTraits SHII_CHO = new StyleTraits(0.06F, 1, 0.9F, 1.0F, 1.0F, 5, 0.12F);
	/** Improvisiert (Ezra, Phase 10): an untrained guard, but the quickest, cheapest dodges after Ataru. */
	public static final StyleTraits IMPROVISED = new StyleTraits(-0.12F, 0, 1.25F, 0.5F, 0.6F, 4, 0.05F);

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
		} else if (SaberVariant.ONSLAUGHT.equals(style)) {
			return ONSLAUGHT;
		} else if (SaberVariant.ATARU.equals(style)) {
			return ATARU;
		} else if (SaberVariant.MAKASHI.equals(style)) {
			return MAKASHI;
		} else if (SaberVariant.REVERSE_SHIEN.equals(style)) {
			return REVERSE_SHIEN;
		} else if (SaberVariant.UNBRIDLED.equals(style)) {
			return UNBRIDLED;
		} else if (SaberVariant.SHII_CHO.equals(style)) {
			return SHII_CHO;
		} else if (SaberVariant.IMPROVISED.equals(style)) {
			return IMPROVISED;
		}

		return FLOWING;
	}

	/** The share of a frontal hit that the stance blocks with this style. */
	public float blockShare(float base) {
		return Mth.clamp(base + this.blockBonus, 0.0F, 0.95F);
	}
}
