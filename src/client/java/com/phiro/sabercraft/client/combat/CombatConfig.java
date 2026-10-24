package com.phiro.sabercraft.client.combat;

import com.google.gson.annotations.SerializedName;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.config.JsonConfig;

/**
 * Client settings for the fighting animations and effects, in
 * {@code config/sabercraft/combat_client.json}. Read once at startup.
 */
public final class CombatConfig {
	private static CombatConfig current = new CombatConfig();

	/** Own attack animations instead of the vanilla swing. */
	@SerializedName("combat_animations")
	public boolean combatAnimations = true;
	/** Resting and defensive poses while a lit saber is held. */
	@SerializedName("ready_stance")
	public boolean readyStance = true;
	/** Light trail behind the blade during fast movements. */
	@SerializedName("blade_trails")
	public boolean bladeTrails = true;
	/** Brightness of the light trail, 0..2. */
	@SerializedName("trail_strength")
	public float trailStrength = 1.0F;
	/** Amount of sparks and other particles: 0 = none, 1 = normal, 2 = more. */
	@SerializedName("particles")
	public int particles = 1;
	/** Hides vanilla's white sweep arc next to sabers, the light trail replaces it. */
	@SerializedName("hide_vanilla_sweep")
	public boolean hideVanillaSweep = true;
	/** Shows the ability cooldowns next to the hotbar while a saber is held. */
	@SerializedName("ability_hud")
	public boolean abilityHud = true;
	/** How much the first-person camera follows rolls and landings, 0 = not at all, 1 = full. */
	@SerializedName("camera_motion")
	public float cameraMotion = 1.0F;
	/** Landing animation after higher jumps. */
	@SerializedName("landing_animation")
	public boolean landingAnimation = true;

	public static CombatConfig get() {
		return current;
	}

	public static void load() {
		CombatConfig config = JsonConfig.load("combat_client", CombatConfig.class, CombatConfig::new);
		config.trailStrength = Mth.clamp(config.trailStrength, 0.0F, 2.0F);
		config.particles = Mth.clamp(config.particles, 0, 2);
		config.cameraMotion = Mth.clamp(config.cameraMotion, 0.0F, 1.0F);
		current = config;
	}

	/** Writes the current settings, after a change in the menu. */
	public static void save() {
		JsonConfig.save("combat_client", current);
	}
}
