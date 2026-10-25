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

	/** Force grip (Phase 7): reach, longest hold, stamina per tick while holding. */
	@SerializedName("grip_enabled")
	public boolean gripEnabled = true;
	@SerializedName("grip_cooldown")
	public int gripCooldown = 200;
	@SerializedName("grip_range")
	public float gripRange = 10.0F;
	@SerializedName("grip_max_ticks")
	public int gripMaxTicks = 80;
	@SerializedName("grip_cost")
	public float gripCost = 15.0F;
	@SerializedName("grip_drain")
	public float gripDrain = 0.5F;
	/** Whether grip and pull work on other players. Only if PvP is on. */
	@SerializedName("force_players")
	public boolean forcePlayers = false;

	@SerializedName("pull_enabled")
	public boolean pullEnabled = true;
	@SerializedName("pull_cooldown")
	public int pullCooldown = 160;
	@SerializedName("pull_range")
	public float pullRange = 12.0F;
	@SerializedName("pull_strength")
	public float pullStrength = 1.0F;
	@SerializedName("pull_cost")
	public float pullCost = 25.0F;

	@SerializedName("jump_enabled")
	public boolean jumpEnabled = true;
	@SerializedName("jump_cooldown")
	public int jumpCooldown = 100;
	/** Height of the force jump as a multiplier, 1 = about 5 blocks. Sent to the clients. */
	@SerializedName("jump_strength")
	public float jumpStrength = 1.0F;
	@SerializedName("jump_cost")
	public float jumpCost = 30.0F;

	/**
	 * Projectile deflection (Phase 7): the stance, a swing or the staff whirl send projectiles of
	 * the entity type tag {@code sabercraft:deflectable} back. Right after raising the stance or in
	 * the first ticks of a swing they fly where the player looks, otherwise they glance off.
	 */
	@SerializedName("deflect_enabled")
	public boolean deflectEnabled = true;
	@SerializedName("deflect_cost")
	public float deflectCost = 6.0F;

	/**
	 * Stamina (Phase 7): blocking, dodging, the spin and the push cost stamina, which comes back
	 * after a short rest. Without stamina a block breaks and the other abilities wait. So no
	 * defense lasts forever. false turns stamina off completely.
	 */
	@SerializedName("stamina_enabled")
	public boolean staminaEnabled = true;
	@SerializedName("stamina_max")
	public float staminaMax = 100.0F;
	/** Stamina regained per tick once the rest delay is over (20 ticks = 1 second). */
	@SerializedName("stamina_regen")
	public float staminaRegen = 1.0F;
	/** Ticks without using stamina before it comes back. */
	@SerializedName("stamina_regen_delay")
	public int staminaRegenDelay = 20;
	@SerializedName("guard_cost")
	public float guardCost = 5.0F;
	/** Stamina per point of damage a block takes, changed by the fighting style. */
	@SerializedName("block_cost_per_damage")
	public float blockCostPerDamage = 6.0F;
	@SerializedName("parry_cost")
	public float parryCost = 4.0F;
	@SerializedName("dodge_cost")
	public float dodgeCost = 25.0F;
	@SerializedName("spin_cost")
	public float spinCost = 30.0F;
	@SerializedName("push_cost")
	public float pushCost = 25.0F;

	/**
	 * Raising the stance again within this many ticks after the last raise gives no parry window,
	 * only a normal block. Stops parrying by tapping the key all the time.
	 */
	@SerializedName("parry_lockout")
	public int parryLockout = 16;
	/** Ticks after a parry in which the next own hit is a counter with extra damage. */
	@SerializedName("counter_ticks")
	public int counterTicks = 30;
	/** Extra damage of the counter, 0.5 = +50 %. */
	@SerializedName("counter_bonus")
	public float counterBonus = 0.5F;
	/** Ticks a parried attacker is off balance (slowed and weakened). */
	@SerializedName("unbalance_ticks")
	public int unbalanceTicks = 30;
	/** Cooldown of the stance after it was broken. */
	@SerializedName("guard_break_cooldown")
	public int guardBreakCooldown = 50;

	/**
	 * Phase 10: hilts change the fight a little (damage, guard, parry window, knockback, dodge
	 * cooldown, see {@code HiltTraits}). false: every hilt plays like the standard one.
	 */
	@SerializedName("hilt_traits")
	public boolean hiltTraits = true;

	/**
	 * Phase 10: lightsaber duels with {@code /sabercraft duel <player>}, only between two players
	 * who both agreed (see {@code Duels}). false: no new challenges, and running duels end.
	 */
	@SerializedName("duels_enabled")
	public boolean duelsEnabled = true;

	/** Hunger cost (exhaustion) per ability use, like sprinting or jumping. */
	@SerializedName("exhaustion")
	public float exhaustion = 1.0F;

	/**
	 * Phase 10: who may use {@code /sabercraft convert}, {@code revert} and {@code blaster}.
	 * true: every player, for the item in their own hand. false: only operators (permission level 2).
	 */
	@SerializedName("commands_for_everyone")
	public boolean commandsForEveryone = true;
	/**
	 * Phase 11: every blaster shot needs and uses one plain arrow from the inventory (the off hand
	 * first, like a bow). Creative players need none. false: blasters fire without ammunition, as
	 * in Phase 10.
	 */
	@SerializedName("blaster_ammo")
	public boolean blasterAmmo = true;
	/**
	 * Phase 11: a blaster with Infinity needs one arrow but keeps it, like a bow with Infinity.
	 * false: Infinity does nothing on blasters, every shot uses an arrow.
	 */
	@SerializedName("blaster_infinity")
	public boolean blasterInfinity = true;
	/**
	 * Phase 11: the blow that defeats a target with a saber (or ends a duel round) is a finisher:
	 * a flash and shock ring in the blade color and its own sound for everyone nearby.
	 */
	@SerializedName("finisher_effect")
	public boolean finisherEffect = true;
	/**
	 * Phase 11: in single player a finisher slows the game down for a moment (half a second at
	 * half speed). Never on a server with more than one player, and never while a /tick rate is set.
	 */
	@SerializedName("finisher_slow_motion")
	public boolean finisherSlowMotion = true;
	/** Phase 11: ticks before the same player can land the next finisher. */
	@SerializedName("finisher_cooldown")
	public int finisherCooldown = 40;
	/** Phase 10: blasters fire. false: converted bows do nothing on right-click (and stay blasters). */
	@SerializedName("blasters_enabled")
	public boolean blastersEnabled = true;
	/** Damage of all blaster bolts, as a multiplier of their built-in damage. */
	@SerializedName("blaster_damage")
	public float blasterDamage = 1.0F;
	/** Whether blasters heat up and overheat. false: only the fire rate limits them. */
	@SerializedName("blaster_heat")
	public boolean blasterHeat = true;
	/** Heat lost per tick when not firing (out of 100). */
	@SerializedName("blaster_cool_rate")
	public float blasterCoolRate = 2.0F;
	/** Heat lost per tick while overheated; firing is blocked until it is down to 0. */
	@SerializedName("blaster_vent_rate")
	public float blasterVentRate = 1.5F;
	/** Phase 11: players can call droid companions (menu or /sabercraft droid). */
	@SerializedName("droids_enabled")
	public boolean droidsEnabled = true;
	/** How many droids one player can have out at the same time (each kind at most once). */
	@SerializedName("droid_limit")
	public int droidLimit = 4;
	/** Multiplier for the cooldowns of the droid abilities. */
	@SerializedName("droid_cooldown")
	public float droidCooldown = 1.0F;
	/** Whether the combat droids (Chopper, B1, K-2SO) may hurt or push other players. */
	@SerializedName("droids_hit_players")
	public boolean droidsHitPlayers = false;

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
		config.staminaMax = Mth.clamp(config.staminaMax, 10.0F, 1000.0F);
		config.staminaRegen = Mth.clamp(config.staminaRegen, 0.05F, 100.0F);
		config.staminaRegenDelay = Mth.clamp(config.staminaRegenDelay, 0, 200);
		config.guardCost = Mth.clamp(config.guardCost, 0.0F, config.staminaMax);
		config.blockCostPerDamage = Mth.clamp(config.blockCostPerDamage, 0.0F, 100.0F);
		config.parryCost = Mth.clamp(config.parryCost, 0.0F, config.staminaMax);
		config.dodgeCost = Mth.clamp(config.dodgeCost, 0.0F, config.staminaMax);
		config.spinCost = Mth.clamp(config.spinCost, 0.0F, config.staminaMax);
		config.pushCost = Mth.clamp(config.pushCost, 0.0F, config.staminaMax);
		config.parryLockout = Mth.clamp(config.parryLockout, 0, 200);
		config.counterTicks = Mth.clamp(config.counterTicks, 0, 200);
		config.counterBonus = Mth.clamp(config.counterBonus, 0.0F, 3.0F);
		config.unbalanceTicks = Mth.clamp(config.unbalanceTicks, 0, 200);
		config.guardBreakCooldown = Mth.clamp(config.guardBreakCooldown, 0, 1200);
		config.gripCooldown = Mth.clamp(config.gripCooldown, 10, 6000);
		config.gripRange = Mth.clamp(config.gripRange, 2.0F, 24.0F);
		config.gripMaxTicks = Mth.clamp(config.gripMaxTicks, 10, 600);
		config.gripCost = Mth.clamp(config.gripCost, 0.0F, config.staminaMax);
		config.gripDrain = Mth.clamp(config.gripDrain, 0.0F, 20.0F);
		config.pullCooldown = Mth.clamp(config.pullCooldown, 10, 6000);
		config.pullRange = Mth.clamp(config.pullRange, 2.0F, 24.0F);
		config.pullStrength = Mth.clamp(config.pullStrength, 0.0F, 3.0F);
		config.pullCost = Mth.clamp(config.pullCost, 0.0F, config.staminaMax);
		config.jumpCooldown = Mth.clamp(config.jumpCooldown, 10, 6000);
		config.jumpStrength = Mth.clamp(config.jumpStrength, 0.3F, 2.0F);
		config.jumpCost = Mth.clamp(config.jumpCost, 0.0F, config.staminaMax);
		config.deflectCost = Mth.clamp(config.deflectCost, 0.0F, config.staminaMax);
		config.blasterDamage = Mth.clamp(config.blasterDamage, 0.0F, 10.0F);
		config.blasterCoolRate = Mth.clamp(config.blasterCoolRate, 0.1F, 100.0F);
		config.blasterVentRate = Mth.clamp(config.blasterVentRate, 0.1F, 100.0F);
		config.droidLimit = Mth.clamp(config.droidLimit, 1, 8);
		config.droidCooldown = Mth.clamp(config.droidCooldown, 0.1F, 10.0F);
		current = config;
	}
}
