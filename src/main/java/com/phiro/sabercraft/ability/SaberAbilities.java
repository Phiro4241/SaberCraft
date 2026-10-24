package com.phiro.sabercraft.ability;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.network.AbilityCooldownPayload;
import com.phiro.sabercraft.network.AbilityEventPayload;
import com.phiro.sabercraft.network.AbilitySettingsPayload;
import com.phiro.sabercraft.network.StaminaPayload;
import com.phiro.sabercraft.network.UseAbilityPayload;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Server logic of the saber abilities. Everything that matters for the game happens here: the
 * cooldowns, the damage, blocking, and moving entities. Clients only send a request and get told
 * what happened, so all players see the same result and a modified client cannot cheat.
 *
 * <p>An ability needs a netherite sword (a saber) in the main hand. The server does not know if
 * the blade is lit, that is a client-only animation; the client only sends requests for a lit
 * blade.
 */
public final class SaberAbilities {
	private static final Identifier GUARD_SLOWDOWN = SaberCraft.id("guard_slowdown");
	private static final Identifier COUNTER_BONUS = SaberCraft.id("counter_bonus");
	/** Ticks after the start of the staff whirl at which it hits. */
	private static final int[] WHIRL_HITS = {4, 14, 24};
	private static final int WHIRL_TICKS = 25;
	/** Half angle of the area in front of the player for the push, the whirl and the stance. */
	private static final double FRONT_COS = Math.cos(Math.toRadians(60.0));

	private static final Map<UUID, State> STATES = new HashMap<>();

	private SaberAbilities() {
	}

	/** Per-player state on the server. */
	private static final class State {
		final EnumMap<SaberAbility, Long> readyAt = new EnumMap<>(SaberAbility.class);
		long guardStart = -1;
		long spinStart = -1;
		boolean whirl;
		long dashStart = -1;
		SaberManeuver maneuver = SaberManeuver.ROLL;
		final EnumMap<SaberAbility, Integer> totals = new EnumMap<>(SaberAbility.class);
		float stamina = AbilityConfig.get().staminaMax;
		long staminaUsedAt = Long.MIN_VALUE / 2;
		float sentStamina = -1.0F;
		long lastGuardRaise = Long.MIN_VALUE / 2;
		/** Whether the current stance may parry, see {@code parry_lockout}. */
		boolean parryWindow;
		long counterUntil = -1;
		boolean counterUsed;

		boolean guarding() {
			return this.guardStart >= 0;
		}
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> AbilityConfig.load());
		ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.TYPE, (payload, context) -> use(context.player(), payload.ability(), payload.pressed(), payload.data()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.getPlayer(), AbilitySettingsPayload.TYPE)) {
				AbilityConfig config = AbilityConfig.get();
				ServerPlayNetworking.send(handler.getPlayer(), new AbilitySettingsPayload(config.dodgeSpeed,
						config.staminaEnabled ? config.staminaMax : 0.0F, config.dodgeCost, config.spinCost, config.pushCost, config.guardCost));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SaberAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> STATES.remove(newPlayer.getUUID()));
		// No normal attacks while the stance is up: the blade is busy blocking. A hit after a parry
		// is a counter, its extra damage ends with it.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			State state = STATES.get(serverPlayer.getUUID());

			if (state != null && state.guarding()) {
				return InteractionResult.FAIL;
			}

			if (state != null && state.counterUntil >= 0) {
				state.counterUsed = true;
			}

			return InteractionResult.PASS;
		});
	}

	/** The stamina of a player, the maximum if they never used any. */
	public static float stamina(Player player) {
		State state = STATES.get(player.getUUID());
		return state != null ? state.stamina : AbilityConfig.get().staminaMax;
	}

	/** True for a short time after a parry, while the next hit is a counter. */
	public static boolean hasCounter(Player player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.counterUntil >= 0;
	}

	/** Stamina an ability costs with a fighting style. */
	public static float cost(AbilityConfig config, SaberAbility ability, @Nullable Identifier style) {
		return switch (ability) {
			case GUARD -> config.guardCost;
			case SPIN -> config.spinCost;
			case PUSH -> config.pushCost;
			case DASH -> config.dodgeCost * StyleTraits.of(style).dodgeCost();
		};
	}

	public static boolean isGuarding(Player player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.guarding();
	}

	/** Handles a key press or release from a client. Runs on the server thread. */
	static void use(ServerPlayer player, SaberAbility ability, boolean pressed, int requested) {
		AbilityConfig config = AbilityConfig.get();
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		long now = player.level().getGameTime();

		if (ability == SaberAbility.GUARD && !pressed) {
			stopGuard(player, state, now, config.guardCooldown);
			return;
		}

		if (!pressed || !canUse(player) || !enabled(config, ability) || now < state.readyAt.getOrDefault(ability, 0L)) {
			// Tell the client the real cooldown, in case it guessed wrong.
			sendCooldown(player, state, ability, now);
			return;
		}

		if (state.guarding() && ability != SaberAbility.GUARD) {
			return;
		}

		ItemStack saber = player.getMainHandItem();
		Identifier style = SaberVariant.styleOf(saber);
		int data = 0;

		if (ability == SaberAbility.GUARD && state.guarding()) {
			return;
		}

		// Out of breath: the ability waits until enough stamina is back.
		float cost = cost(config, ability, style);

		if (config.staminaEnabled && state.stamina < cost) {
			sendCooldown(player, state, ability, now);
			sendStamina(player, state);

			if (ability == SaberAbility.GUARD) {
				// The client already shows the stance: take it down again.
				broadcast(player, new AbilityEventPayload(player.getId(), SaberAbility.GUARD, AbilityEventPayload.Event.STOP));
			}

			return;
		}

		spend(state, config, cost, now);

		switch (ability) {
			case GUARD -> {
				state.guardStart = now;
				state.parryWindow = now - state.lastGuardRaise >= config.parryLockout;
				state.lastGuardRaise = now;
				AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

				if (speed != null && !speed.hasModifier(GUARD_SLOWDOWN)) {
					speed.addTransientModifier(new AttributeModifier(GUARD_SLOWDOWN, config.guardSlowdown, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
			case SPIN -> {
				state.spinStart = now;
				state.whirl = SaberVariant.STAFF.equals(style);
				startCooldown(player, state, ability, now, state.whirl ? config.whirlCooldown : config.spinCooldown);
			}
			case PUSH -> {
				push(player, config);
				startCooldown(player, state, ability, now, config.pushCooldown);
			}
			case DASH -> {
				// The client of the player moves them (player movement is client side in Minecraft), with
				// the speed the server sent on join. The server times the strike and the evasion.
				DodgeDirection direction = DodgeDirection.byId(requested);
				data = direction.ordinal();
				state.maneuver = SaberManeuver.of(style, direction);
				state.dashStart = now;
				startCooldown(player, state, ability, now, Math.round(config.dashCooldown * StyleTraits.of(style).dodgeCooldown()));
			}
		}

		player.causeFoodExhaustion(config.exhaustion);
		broadcast(player, new AbilityEventPayload(player.getId(), ability, AbilityEventPayload.Event.START, data));
	}

	private static boolean enabled(AbilityConfig config, SaberAbility ability) {
		return switch (ability) {
			case GUARD -> config.guardEnabled;
			case SPIN -> config.spinEnabled;
			case PUSH -> config.pushEnabled;
			case DASH -> config.dashEnabled;
		};
	}

	private static boolean canUse(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isPassenger()
				&& SaberVariant.canHold(player.getMainHandItem());
	}

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}

		AbilityConfig config = AbilityConfig.get();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = STATES.get(player.getUUID());

			if (state == null) {
				continue;
			}

			long now = player.level().getGameTime();

			if (state.guarding() && (!canUse(player) || now - state.guardStart >= config.guardMaxTicks)) {
				stopGuard(player, state, now, config.guardCooldown);
			}

			tickStamina(player, state, config, now);

			if (state.counterUntil >= 0 && (state.counterUsed || now > state.counterUntil || !canUse(player))) {
				endCounter(player, state);
			}

			if (state.spinStart >= 0) {
				tickSpin(player, state, config, now - state.spinStart);
			}

			if (state.dashStart >= 0) {
				tickManeuver(player, state, config, now - state.dashStart);
			}
		}
	}

	private static void tickStamina(ServerPlayer player, State state, AbilityConfig config, long now) {
		if (!config.staminaEnabled) {
			return;
		}

		if (!state.guarding() && now - state.staminaUsedAt >= config.staminaRegenDelay && state.stamina < config.staminaMax) {
			state.stamina = Math.min(config.staminaMax, state.stamina + config.staminaRegen);
		}

		if (Math.abs(state.stamina - state.sentStamina) >= 1.0F || state.stamina >= config.staminaMax && state.sentStamina < config.staminaMax) {
			sendStamina(player, state);
		}
	}

	private static void spend(State state, AbilityConfig config, float cost, long now) {
		if (config.staminaEnabled && cost > 0.0F) {
			state.stamina = Math.max(0.0F, state.stamina - cost);
			state.staminaUsedAt = now;
		}
	}

	private static void sendStamina(ServerPlayer player, State state) {
		state.sentStamina = state.stamina;

		if (ServerPlayNetworking.canSend(player, StaminaPayload.TYPE)) {
			ServerPlayNetworking.send(player, new StaminaPayload(state.stamina));
		}
	}

	private static void startCounter(ServerPlayer player, State state, AbilityConfig config, long now) {
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);

		if (damage == null || config.counterTicks <= 0 || config.counterBonus <= 0.0F) {
			return;
		}

		damage.removeModifier(COUNTER_BONUS);
		damage.addTransientModifier(new AttributeModifier(COUNTER_BONUS, config.counterBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		state.counterUntil = now + config.counterTicks;
		state.counterUsed = false;
	}

	private static void endCounter(ServerPlayer player, State state) {
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);

		if (damage != null) {
			damage.removeModifier(COUNTER_BONUS);
		}

		state.counterUntil = -1;
		state.counterUsed = false;
	}

	private static void tickManeuver(ServerPlayer player, State state, AbilityConfig config, long age) {
		SaberManeuver maneuver = state.maneuver;

		if (age == maneuver.strikeTick() && canUse(player)) {
			if (maneuver == SaberManeuver.SPIN_STEP) {
				hitArea(player, 2.8, -1.0, config.dashDamageShare * 0.9F, 2);
			} else {
				hitArea(player, 2.6, 0.85, config.dashDamageShare, 1);
			}
		}

		if (age >= maneuver.duration() || !player.isAlive()) {
			state.dashStart = -1;
		}
	}

	private static void tickSpin(ServerPlayer player, State state, AbilityConfig config, long age) {
		if (!canUse(player)) {
			state.spinStart = -1;
			return;
		}

		if (state.whirl) {
			// The whirl in front of the body hits a few times and turns projectiles away.
			for (int hit : WHIRL_HITS) {
				if (age == hit) {
					hitArea(player, config.whirlRadius, FRONT_COS, config.whirlDamageShare, 3);
				}
			}

			deflectProjectiles(player, config.whirlRadius);

			if (age >= WHIRL_TICKS) {
				state.spinStart = -1;
			}
		} else {
			// The strike moment of the spin animations.
			int strike = StyleTraits.of(SaberVariant.styleOf(player.getMainHandItem())).spinStrike();

			if (age >= strike) {
				hitArea(player, config.spinRadius, -1.0, config.spinDamageShare, 2);
				state.spinStart = -1;
			}
		}
	}

	private static void stopGuard(ServerPlayer player, State state, long now, int cooldown) {
		if (!state.guarding()) {
			return;
		}

		state.guardStart = -1;
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null) {
			speed.removeModifier(GUARD_SLOWDOWN);
		}

		startCooldown(player, state, SaberAbility.GUARD, now, cooldown);
		broadcast(player, new AbilityEventPayload(player.getId(), SaberAbility.GUARD, AbilityEventPayload.Event.STOP));
	}

	/**
	 * Called from the damage code: returns how much of {@code damage} the stance blocks. A
	 * frontal hit is blocked partly, or fully right after the stance was raised (a parry).
	 * Fall damage, fire and other hits without a direction are never blocked.
	 */
	public static float blockedDamage(LivingEntity entity, ServerLevel level, DamageSource source, float damage) {
		if (!(entity instanceof ServerPlayer player) || damage <= 0.0F) {
			return 0.0F;
		}

		State state = STATES.get(player.getUUID());

		if (state == null) {
			return 0.0F;
		}

		// Rolling or stepping out of the way: melee hits from any side miss.
		if (state.dashStart >= 0 && state.maneuver.evades(level.getGameTime() - state.dashStart)
				&& source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker
				&& !source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			broadcast(player, new AbilityEventPayload(player.getId(), SaberAbility.DASH, AbilityEventPayload.Event.EVADE));
			return damage;
		}

		if (!(state.guarding() || state.whirl && state.spinStart >= 0)) {
			return 0.0F;
		}

		if (source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return 0.0F;
		}

		Vec3 from = source.getSourcePosition();

		if (from == null || !inFront(player, from, FRONT_COS)) {
			return 0.0F;
		}

		AbilityConfig config = AbilityConfig.get();
		long now = level.getGameTime();
		StyleTraits traits = StyleTraits.of(SaberVariant.styleOf(player.getMainHandItem()));
		boolean parry = state.guarding() && state.parryWindow && now - state.guardStart <= config.guardParryTicks + traits.parryBonus();
		float share = traits.blockShare(config.guardBlockShare);

		if (state.guarding()) {
			// Every block costs stamina, a parry only a little. Without enough the stance breaks.
			float cost = parry ? config.parryCost : damage * share * config.blockCostPerDamage * traits.blockCost();

			if (config.staminaEnabled && state.stamina < cost) {
				state.stamina = 0.0F;
				state.staminaUsedAt = now;
				stopGuard(player, state, now, config.guardBreakCooldown);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, false, false, true));
				broadcast(player, new AbilityEventPayload(player.getId(), SaberAbility.GUARD, AbilityEventPayload.Event.GUARD_BREAK));
				sendStamina(player, state);
				return 0.0F;
			}

			spend(state, config, cost, now);
		}

		if (parry && source.getDirectEntity() instanceof LivingEntity attacker) {
			// Throw the attacker off balance and open a counter.
			attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ(), source, 0.0F);
			attacker.hurtMarked = true;

			if (config.unbalanceTicks > 0) {
				attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, config.unbalanceTicks, 2, false, false, true), player);
				attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, config.unbalanceTicks, 0, false, false, true), player);
				broadcast(player, new AbilityEventPayload(attacker.getId(), SaberAbility.GUARD, AbilityEventPayload.Event.UNBALANCED));
			}

			startCounter(player, state, config, now);
		}

		broadcast(player, new AbilityEventPayload(player.getId(), SaberAbility.GUARD,
				parry ? AbilityEventPayload.Event.PARRY : AbilityEventPayload.Event.BLOCK));
		return parry ? damage : damage * share;
	}

	/**
	 * Damages the living entities around the player.
	 *
	 * @param minCos  only entities whose direction has at least this cosine to the look direction,
	 *                -1 for all around
	 * @param maxHits most entities hit at once
	 */
	private static void hitArea(ServerPlayer player, double radius, double minCos, float share, int maxHits) {
		ServerLevel level = player.level();
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * share;

		if (damage <= 0.0F) {
			return;
		}

		List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 1.0, radius),
				target -> canAffect(player, target) && player.distanceTo(target) <= radius + target.getBbWidth() * 0.5
						&& (minCos <= -1.0 || inFront(player, target.position(), minCos)) && player.hasLineOfSight(target)));
		targets.sort((a, b) -> Double.compare(player.distanceToSqr(a), player.distanceToSqr(b)));
		DamageSource source = level.damageSources().playerAttack(player);

		for (int i = 0; i < Math.min(maxHits, targets.size()); i++) {
			LivingEntity target = targets.get(i);

			if (target.hurtServer(level, source, damage)) {
				target.knockback(0.35, player.getX() - target.getX(), player.getZ() - target.getZ(), source, damage);
			}
		}
	}

	private static void push(ServerPlayer player, AbilityConfig config) {
		ServerLevel level = player.level();
		double range = config.pushRange;
		List<Entity> targets = level.getEntities(player, player.getBoundingBox().inflate(range), entity ->
				entity.isAlive() && player.distanceTo(entity) <= range && inFront(player, entity.position(), FRONT_COS) && player.hasLineOfSight(entity));

		for (Entity entity : targets) {
			if (entity instanceof Projectile projectile) {
				if (projectile.getOwner() != player) {
					projectile.deflect(ProjectileDeflection.REVERSE, player, EntityReference.of(player), false);
				}

				continue;
			}

			if (!(entity instanceof LivingEntity target) || !canAffect(player, target)
					|| target instanceof Player && !config.pushPlayers) {
				continue;
			}

			// Stronger up close, and knockback resistance (netherite armor, heavy mobs) counts.
			double falloff = 1.0 - 0.6 * player.distanceTo(target) / range;
			double strength = config.pushStrength * falloff * (1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));

			if (strength <= 0.0) {
				continue;
			}

			Vec3 away = target.position().subtract(player.position());
			away = new Vec3(away.x, 0.0, away.z);
			away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away.normalize();
			Vec3 motion = target.getDeltaMovement();
			target.setDeltaMovement(motion.x * 0.5 + away.x * strength, Math.max(motion.y, 0.25 + 0.15 * strength), motion.z * 0.5 + away.z * strength);
			target.hurtMarked = true;
			target.needsSync = true;
		}
	}

	private static void deflectProjectiles(ServerPlayer player, double radius) {
		AABB box = player.getBoundingBox().inflate(radius);

		for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != player)) {
			Vec3 motion = projectile.getDeltaMovement();
			Vec3 toPlayer = player.position().subtract(projectile.position());

			// Only projectiles in front that are still flying towards the player.
			if (inFront(player, projectile.position(), FRONT_COS) && motion.dot(toPlayer) > 0.0) {
				projectile.deflect(ProjectileDeflection.REVERSE, player, EntityReference.of(player), false);
			}
		}
	}

	/** Whether an ability may hurt or push this entity: no allies, own pets, or players without PvP. */
	private static boolean canAffect(ServerPlayer player, LivingEntity target) {
		if (target == player || !target.isAlive() || !target.isAttackable() || target instanceof ArmorStand || player.isAlliedTo(target)) {
			return false;
		}

		if (target instanceof OwnableEntity pet && pet.getOwner() == player) {
			return false;
		}

		if (target instanceof Player other) {
			return !other.isSpectator() && !other.isCreative() && player.canHarmPlayer(other);
		}

		return true;
	}

	private static boolean inFront(Player player, Vec3 position, double minCos) {
		Vec3 look = player.getLookAngle();
		Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
		Vec3 to = position.subtract(player.position());
		Vec3 flatTo = new Vec3(to.x, 0.0, to.z);

		if (flatTo.lengthSqr() < 0.25 || flatLook.lengthSqr() < 1.0E-4) {
			return true;
		}

		return flatLook.normalize().dot(flatTo.normalize()) >= minCos;
	}

	private static void startCooldown(ServerPlayer player, State state, SaberAbility ability, long now, int ticks) {
		state.readyAt.put(ability, now + ticks);
		state.totals.put(ability, ticks);
		sendCooldown(player, state, ability, now);
	}

	private static void sendCooldown(ServerPlayer player, State state, SaberAbility ability, long now) {
		long readyAt = state.readyAt.getOrDefault(ability, 0L);
		int left = (int) Math.max(0L, readyAt - now);
		int total = state.totals.containsKey(ability) ? state.totals.get(ability) : switch (ability) {
			case GUARD -> AbilityConfig.get().guardCooldown;
			case SPIN -> state.whirl ? AbilityConfig.get().whirlCooldown : AbilityConfig.get().spinCooldown;
			case PUSH -> AbilityConfig.get().pushCooldown;
			case DASH -> AbilityConfig.get().dashCooldown;
		};

		if (ServerPlayNetworking.canSend(player, AbilityCooldownPayload.TYPE)) {
			ServerPlayNetworking.send(player, new AbilityCooldownPayload(ability, left, Math.max(total, left)));
		}
	}

	/** Sends to the player and to everyone who can see them. */
	private static void broadcast(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}

		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player && ServerPlayNetworking.canSend(other, payload.type())) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}

	/** Only for tests: sets the stamina of a player. */
	public static void setStaminaForTests(ServerPlayer player, float stamina) {
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		state.stamina = stamina;
		state.staminaUsedAt = player.level().getGameTime();
		sendStamina(player, state);
	}

	/** Only for tests: forgets all cooldowns and states. */
	public static void reset(@Nullable ServerPlayer player) {
		if (player == null) {
			STATES.clear();
		} else {
			STATES.remove(player.getUUID());
		}
	}
}
