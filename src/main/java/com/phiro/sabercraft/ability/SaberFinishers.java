package com.phiro.sabercraft.ability;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.network.SaberFinisherPayload;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Finishing blows (Phase 11). The melee hit with a saber that defeats a target, or that ends a duel
 * round, is sent once to the attacker and to everyone who sees the target; their clients show a
 * flash and a shock ring in the blade color and play the finisher sound. Nothing is cut and
 * nothing bleeds: the target simply falls (or, in a duel, gets up for the next round).
 *
 * <p>Not every blow: only the defeating one, and at most one per player every
 * {@code finisher_cooldown} ticks, so a sweep through a crowd of mobs stays calm. In single player
 * the game also slows down for a moment; nothing is locked, the player keeps control throughout.
 */
public final class SaberFinishers {
	/** Ticks (at the slowed rate) the slow motion lasts. */
	static final int SLOW_TICKS = 5;
	/** The tick rate of the slow motion; the normal rate is 20. */
	static final float SLOW_RATE = 10.0F;

	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static int slowTicksLeft;
	private static float rateBefore;
	private static int finishers;
	private static int slowMotions;

	private SaberFinishers() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(SaberFinishers::onDeath);
		ServerTickEvents.END_SERVER_TICK.register(SaberFinishers::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			endSlowMotion(server);
			LAST.clear();
		});
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		ServerPlayer attacker = saberAttacker(source);

		if (attacker != null && attacker != entity) {
			finalBlow(attacker, entity);
		}
	}

	/**
	 * The saber holder whose melee hit this is, or {@code null}: a player attack whose direct
	 * entity is a player with a saber in the main hand (not a thrown or deflected projectile).
	 */
	public static @Nullable ServerPlayer saberAttacker(DamageSource source) {
		if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getDirectEntity() instanceof ServerPlayer player)
				|| !SaberVariant.canHold(player.getMainHandItem())) {
			return null;
		}

		return player;
	}

	/**
	 * A blow of {@code attacker} defeated {@code target}. Called on death and by the duels when a
	 * deadly blow ends a round. Returns whether it was shown as a finisher.
	 */
	public static boolean finalBlow(ServerPlayer attacker, LivingEntity target) {
		AbilityConfig config = AbilityConfig.get();
		MinecraftServer server = attacker.level().getServer();
		long now = attacker.level().getGameTime();
		Long last = LAST.get(attacker.getUUID());

		if (!config.finisherEffect || server == null || last != null && now - last < config.finisherCooldown && now >= last) {
			return false;
		}

		LAST.put(attacker.getUUID(), now);
		finishers++;
		SaberFinisherPayload payload = new SaberFinisherPayload(attacker.getId(), target.getId());
		// Every viewer once: the attacker, the target if it is a player, and who sees either.
		Set<ServerPlayer> viewers = new LinkedHashSet<>();
		viewers.add(attacker);

		if (target instanceof ServerPlayer player) {
			viewers.add(player);
		}

		viewers.addAll(PlayerLookup.tracking(target));
		viewers.addAll(PlayerLookup.tracking(attacker));

		for (ServerPlayer viewer : viewers) {
			if (ServerPlayNetworking.canSend(viewer, SaberFinisherPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}

		SaberAdvancements.award(attacker, SaberAdvancements.FINISHER);
		slowMotion(server, config);
		return true;
	}

	/** Half speed for a moment, in single player only, and only from the normal rate. */
	private static void slowMotion(MinecraftServer server, AbilityConfig config) {
		ServerTickRateManager rates = server.tickRateManager();

		if (!config.finisherSlowMotion || !server.isSingleplayer() || server.getPlayerCount() > 1 || slowTicksLeft > 0
				|| rates.isFrozen() || rates.isSprinting() || rates.tickrate() != 20.0F) {
			return;
		}

		rateBefore = rates.tickrate();
		rates.setTickRate(SLOW_RATE);
		slowTicksLeft = SLOW_TICKS;
		slowMotions++;
	}

	private static void tick(MinecraftServer server) {
		if (slowTicksLeft > 0 && --slowTicksLeft == 0) {
			endSlowMotion(server);
		}
	}

	private static void endSlowMotion(MinecraftServer server) {
		ServerTickRateManager rates = server.tickRateManager();

		// Only undo our own change: a /tick rate in between stays.
		if (rateBefore > 0.0F && rates.tickrate() == SLOW_RATE) {
			rates.setTickRate(rateBefore);
		}

		rateBefore = 0.0F;
		slowTicksLeft = 0;
	}

	/** Whether the slow motion is running, for tests. */
	public static boolean slowMotionActive() {
		return slowTicksLeft > 0;
	}

	/** How many slow motions ran since the start, for tests. */
	public static int slowMotions() {
		return slowMotions;
	}

	/** How many finishers were shown since the start, for tests. */
	public static int count() {
		return finishers;
	}

	/** Only for tests: forgets the cooldowns. */
	public static void reset() {
		LAST.clear();
	}
}
