package com.phiro.sabercraft.ability;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.saber.SaberVariant;

/**
 * How a hilt changes the fight on the server (Phase 10). The fighting style ({@link StyleTraits})
 * decides how a saber is swung; the hilt adds its own small edge on top: a heavy hilt hits harder,
 * the crossguard catches blades, Yoda's short blade does less damage but lets him dodge sooner.
 * The numbers are deliberately small, so every hilt stays a fair choice.
 *
 * <p>Server operators can switch the whole thing off with {@code hilt_traits} in
 * {@code config/sabercraft/abilities.json}; then every hilt plays like the standard one.
 *
 * @param damage        multiplier of the damage of a melee hit with the saber
 * @param blockBonus    added to the blocked share of a hit, like {@link StyleTraits#blockBonus()}
 * @param parryBonus    extra ticks of the parry window
 * @param knockback     extra knockback of a melee hit, in blocks per tick
 * @param dodgeCooldown multiplier of the dodge cooldown
 */
public record HiltTraits(float damage, float blockBonus, int parryBonus, float knockback, float dodgeCooldown) {
	public static final HiltTraits NEUTRAL = new HiltTraits(1.0F, 0.0F, 0, 0.0F, 1.0F);

	private static final Map<Identifier, HiltTraits> TRAITS = Map.ofEntries(
			// The classic Jedi hilt: a slightly better guard.
			Map.entry(SaberVariant.GUARDIAN, new HiltTraits(1.0F, 0.04F, 1, 0.0F, 1.0F)),
			// Vader's heavy hilt: harder blows and a firm grip, slower to dodge with.
			Map.entry(SaberVariant.DARK_LORD, new HiltTraits(1.08F, 0.04F, 0, 0.1F, 1.1F)),
			// Light and handy.
			Map.entry(SaberVariant.SENTINEL, new HiltTraits(1.0F, 0.0F, 0, 0.0F, 0.9F)),
			// Obi-Wan: built for defense.
			Map.entry(SaberVariant.KENOBI, new HiltTraits(0.97F, 0.06F, 2, 0.0F, 1.0F)),
			Map.entry(SaberVariant.SKYWALKER, new HiltTraits(1.03F, 0.02F, 0, 0.05F, 0.95F)),
			// Anakin: aggressive.
			Map.entry(SaberVariant.ANAKIN, new HiltTraits(1.06F, 0.0F, 0, 0.08F, 0.95F)),
			// Yoda: the short blade does less damage, but the small hilt is the quickest to move with.
			Map.entry(SaberVariant.YODA, new HiltTraits(0.88F, 0.0F, 1, 0.0F, 0.8F)),
			// Dooku: the curved hilt is made for precise fencing, the longest parry window.
			Map.entry(SaberVariant.DOOKU, new HiltTraits(1.0F, 0.03F, 3, 0.0F, 1.0F)),
			// Ahsoka: light and quick, a little weaker guard.
			Map.entry(SaberVariant.TANO, new HiltTraits(0.95F, -0.02F, 1, 0.0F, 0.85F)),
			// Kylo: the unstable blade burns harder and the quillons catch blades, but it is unwieldy.
			Map.entry(SaberVariant.CROSSGUARD, new HiltTraits(1.12F, 0.06F, -1, 0.12F, 1.15F)),
			Map.entry(SaberVariant.REY, new HiltTraits(1.02F, 0.03F, 0, 0.04F, 1.0F)),
			// Ezra: a weaker blade, the strength of this hilt is its stun blaster.
			Map.entry(SaberVariant.EZRA, new HiltTraits(0.94F, 0.0F, 0, 0.0F, 0.9F)),
			// Two blades cover more, but the long staff is slower to dodge with.
			Map.entry(SaberVariant.DOUBLE, new HiltTraits(1.0F, 0.08F, 0, 0.0F, 1.1F))
	);

	/** The traits of a hilt; the standard hilt and unknown ids have none. */
	public static HiltTraits of(@Nullable Identifier hilt) {
		HiltTraits traits = hilt != null ? TRAITS.get(hilt) : null;
		return traits != null ? traits : NEUTRAL;
	}

	/** The traits of the saber in a stack, or none if they are switched off or it is no saber. */
	public static HiltTraits of(ItemStack stack) {
		if (!AbilityConfig.get().hiltTraits || !SaberVariant.canHold(stack)) {
			return NEUTRAL;
		}

		return of(SaberVariant.hiltOf(stack));
	}
}
