package com.phiro.sabercraft.ability;

import com.google.gson.annotations.SerializedName;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.config.JsonConfig;

/**
 * Server settings of the saber abilities, in {@code config/sabercraft/abilities.json}. Read when
 * the server starts. All times are in ticks (20 ticks = 1 second). On a multiplayer server only
 * the server's file counts.
 */
public final class AbilityConfig {
	private static AbilityConfig current = new AbilityConfig();

	@SerializedName("guard_enabled")
	public boolean guardEnabled = true;
	/** Share of a frontal hit that the stance blocks, 0..1. */
	@SerializedName("guard_block_share")
	public float guardBlockShare = 0.6F;
	/** Ticks after raising the stance in which a hit is parried completely. */
	@SerializedName("guard_parry_ticks")
	public int guardParryTicks = 6;
	/** Longest time the stance can be held. */
	@SerializedName("guard_max_ticks")
	public int guardMaxTicks = 100;
	/** Cooldown after lowering the stance. */
	@SerializedName("guard_cooldown")
	public int guardCooldown = 20;
	/** Movement speed change while guarding, -1..0. */
	@SerializedName("guard_slowdown")
	public float guardSlowdown = -0.4F;

	@SerializedName("spin_enabled")
	public boolean spinEnabled = true;
	@SerializedName("spin_cooldown")
	public int spinCooldown = 120;
	/** Damage of the spin as a share of the player's attack damage. */
	@SerializedName("spin_damage_share")
	public float spinDamageShare = 0.6F;
	@SerializedName("spin_radius")
	public float spinRadius = 3.0F;
	/** The whirl of the double-bladed staff: cooldown, damage per hit and reach. */
	@SerializedName("whirl_cooldown")
	public int whirlCooldown = 160;
	@SerializedName("whirl_damage_share")
	public float whirlDamageShare = 0.3F;
	@SerializedName("whirl_radius")
	public float whirlRadius = 3.5F;

	@SerializedName("push_enabled")
	public boolean pushEnabled = true;
	@SerializedName("push_cooldown")
	public int pushCooldown = 240;
	@SerializedName("push_range")
	public float pushRange = 6.0F;
	@SerializedName("push_strength")
	public float pushStrength = 1.2F;
	/** Whether the push affects other players. Only if PvP is on. */
	@SerializedName("push_players")
	public boolean pushPlayers = true;

	@SerializedName("dash_enabled")
	public boolean dashEnabled = true;
	@SerializedName("dash_cooldown")
	public int dashCooldown = 100;
	/**
	 * Speed of the dodge moves (rolls, lunge, steps) as a multiplier of their built-in speed, 1 = a
	 * roll of about 3.5 blocks. Sent to the clients, which move their own player.
	 */
	@SerializedName("dodge_speed")
	public float dodgeSpeed = 1.0F;
	/** Damage of the stab of the lunge and the cut of the spin step, as a share of the attack damage. */
	@SerializedName("dash_damage_share")
	public float dashDamageShare = 0.5F;

	/** Hunger cost (exhaustion) per ability use, like sprinting or jumping. */
	@SerializedName("exhaustion")
	public float exhaustion = 1.0F;

	public static AbilityConfig get() {
		return current;
	}

	public static void load() {
		AbilityConfig config = JsonConfig.load("abilities", AbilityConfig.class, AbilityConfig::new);
		config.guardBlockShare = Mth.clamp(config.guardBlockShare, 0.0F, 1.0F);
		config.guardParryTicks = Mth.clamp(config.guardParryTicks, 0, 40);
		config.guardMaxTicks = Mth.clamp(config.guardMaxTicks, 10, 1200);
		config.guardCooldown = Mth.clamp(config.guardCooldown, 0, 1200);
		config.guardSlowdown = Mth.clamp(config.guardSlowdown, -1.0F, 0.0F);
		config.spinCooldown = Mth.clamp(config.spinCooldown, 10, 6000);
		config.spinDamageShare = Mth.clamp(config.spinDamageShare, 0.0F, 2.0F);
		config.spinRadius = Mth.clamp(config.spinRadius, 1.0F, 6.0F);
		config.whirlCooldown = Mth.clamp(config.whirlCooldown, 10, 6000);
		config.whirlDamageShare = Mth.clamp(config.whirlDamageShare, 0.0F, 2.0F);
		config.whirlRadius = Mth.clamp(config.whirlRadius, 1.0F, 6.0F);
		config.pushCooldown = Mth.clamp(config.pushCooldown, 10, 6000);
		config.pushRange = Mth.clamp(config.pushRange, 1.0F, 12.0F);
		config.pushStrength = Mth.clamp(config.pushStrength, 0.0F, 4.0F);
		config.dashCooldown = Mth.clamp(config.dashCooldown, 10, 6000);
		config.dodgeSpeed = Mth.clamp(config.dodgeSpeed, 0.3F, 2.0F);
		config.dashDamageShare = Mth.clamp(config.dashDamageShare, 0.0F, 2.0F);
		config.exhaustion = Mth.clamp(config.exhaustion, 0.0F, 10.0F);
		current = config;
	}
}
