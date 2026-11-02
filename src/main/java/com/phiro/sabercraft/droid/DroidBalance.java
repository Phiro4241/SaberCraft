package com.phiro.sabercraft.droid;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import net.minecraft.util.Mth;

/**
 * Phase 13: the numbers behind one droid's ability, editable per droid in
 * {@code config/sabercraft/abilities.json} under {@code droid_balance}. What {@code strength}
 * means depends on the droid; see {@link #defaults()} and {@code docs/configuration.md}.
 */
public final class DroidBalance {
	/** Seconds after a use before the ability is ready again (times {@code droid_cooldown}). */
	@SerializedName("cooldown_seconds")
	public float cooldownSeconds;
	/** How far the droid reaches, in blocks. */
	@SerializedName("range")
	public float range;
	/** Damage per hit, before {@code droid_group_penalty}. 0 for droids that never hurt. */
	@SerializedName("damage")
	public float damage;
	/**
	 * Droid specific: R2-D2 share of wear repaired, BB-8 seconds of glowing, Chopper seconds of
	 * slowness, B1 number of bolts, K-2SO push strength. Unused by the others.
	 */
	@SerializedName("strength")
	public float strength;

	public DroidBalance() {
	}

	public DroidBalance(float cooldownSeconds, float range, float damage, float strength) {
		this.cooldownSeconds = cooldownSeconds;
		this.range = range;
		this.damage = damage;
		this.strength = strength;
	}

	/** The built-in values of one droid. */
	public static DroidBalance of(DroidType type) {
		return switch (type) {
			case R2D2 -> new DroidBalance(30, 0, 0, 0.2F);
			case C3PO -> new DroidBalance(10, 32, 0, 0);
			case BB8 -> new DroidBalance(25, 20, 0, 8);
			case CHOPPER -> new DroidBalance(18, 4, 3, 2);
			case MOUSE -> new DroidBalance(12, 10, 0, 0);
			case PROBE -> new DroidBalance(40, 12, 0, 0);
			case B1 -> new DroidBalance(12, 16, 3, 3);
			case K2SO -> new DroidBalance(14, 5, 2, 1.0F);
		};
	}

	/** Every droid with its built-in values, in menu order. */
	public static Map<String, DroidBalance> defaults() {
		Map<String, DroidBalance> map = new LinkedHashMap<>();

		for (DroidType type : DroidType.values()) {
			map.put(type.id(), of(type));
		}

		return map;
	}

	/** The built-in values, overridden by the droids found in {@code read}, all kept in sane limits. */
	public static Map<String, DroidBalance> merged(Map<String, DroidBalance> read) {
		Map<String, DroidBalance> map = defaults();

		if (read != null) {
			read.forEach((id, balance) -> {
				if (id != null && balance != null && map.containsKey(id)) {
					map.put(id, balance);
				}
			});
		}

		map.values().forEach(DroidBalance::clamp);
		return map;
	}

	private void clamp() {
		this.cooldownSeconds = Mth.clamp(this.cooldownSeconds, 0.5F, 3600.0F);
		this.range = Mth.clamp(this.range, 0.0F, 48.0F);
		this.damage = Mth.clamp(this.damage, 0.0F, 40.0F);
		this.strength = Mth.clamp(this.strength, 0.0F, 60.0F);
	}
}
