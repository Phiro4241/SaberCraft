package com.phiro.sabercraft.droid;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.ability.AbilityConfig;

/**
 * The droid abilities (Phase 11), on the server. Each returns whether it did something (only then
 * the cooldown starts) and what the owner is told. None of them breaks or places blocks.
 */
final class DroidAbilities {
	/** What an ability did. */
	record Result(boolean used, @Nullable Component message) {
		static Result used(Component message) {
			return new Result(true, message);
		}

		static Result nothing(Component message) {
			return new Result(false, message);
		}
	}

	/** What the probe droid looks for, most valuable first. */
	private static final List<Ore> ORES = List.of(
			new Ore("ancient_debris", null, Blocks.ANCIENT_DEBRIS),
			new Ore("diamond", BlockItemTags.DIAMOND_ORES.block(), null),
			new Ore("emerald", BlockItemTags.EMERALD_ORES.block(), null),
			new Ore("gold", BlockItemTags.GOLD_ORES.block(), null),
			new Ore("redstone", BlockItemTags.REDSTONE_ORES.block(), null),
			new Ore("lapis", BlockItemTags.LAPIS_ORES.block(), null),
			new Ore("iron", BlockItemTags.IRON_ORES.block(), null),
			new Ore("copper", BlockItemTags.COPPER_ORES.block(), null),
			new Ore("coal", BlockItemTags.COAL_ORES.block(), null));
	/** How far the probe droid scans, in blocks in every direction. */
	static final int PROBE_RADIUS = 16;

	private DroidAbilities() {
	}

	private record Ore(String name, @Nullable TagKey<Block> tag, @Nullable Block block) {
		boolean matches(BlockState state) {
			return this.tag != null ? state.is(this.tag) : state.is(this.block);
		}
	}

	static Result use(ServerPlayer owner, DroidEntity droid) {
		return switch (droid.droid()) {
			case R2D2 -> repair(owner);
			case C3PO -> report(owner, droid);
			case BB8 -> scan(owner, droid);
			case CHOPPER -> zap(owner, droid);
			case MOUSE -> fetch(owner, droid);
			case PROBE -> survey(owner, droid);
			case B1 -> burst(owner, droid);
			case K2SO -> shove(owner, droid);
		};
	}

	/** R2-D2: a quarter of the wear of the item in the main hand is gone. */
	private static Result repair(ServerPlayer owner) {
		ItemStack stack = owner.getMainHandItem();

		if (!stack.isDamageableItem() || !stack.isDamaged()) {
			return Result.nothing(Component.translatableWithFallback("sabercraft.droid.r2d2.nothing", "R2-D2 beeps: nothing in your hand needs repairing."));
		}

		int repaired = Math.min(stack.getDamageValue(), Math.max(1, stack.getMaxDamage() / 4));
		stack.setDamageValue(stack.getDamageValue() - repaired);
		owner.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 1.0, owner.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
		return Result.used(Component.translatableWithFallback("sabercraft.droid.r2d2.repaired", "R2-D2 repaired wear: %s points.", repaired));
	}

	/** C-3PO: how many hostile creatures are around, the time of day and the weather. */
	private static Result report(ServerPlayer owner, DroidEntity droid) {
		ServerLevel level = owner.level();
		int hostiles = hostiles(level, owner, droid, 32.0).size();
		Component time;

		if (!level.dimensionType().hasFixedTime()) {
			long t = level.getOverworldClockTime() % 24000L;
			String key = t < 1000L ? "morning" : t < 11500L ? "day" : t < 13500L ? "evening" : t < 22500L ? "night" : "dawn";
			time = word("sabercraft.droid.c3po.time.", key);
		} else {
			time = word("sabercraft.droid.c3po.time.", "none");
		}

		String weather = level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
		Component report = hostiles == 0
				? Component.translatableWithFallback("sabercraft.droid.c3po.safe", "C-3PO: \"No hostile creatures within thirty-two blocks. It is %s, and the weather is %s. How reassuring.\"",
						time, word("sabercraft.droid.c3po.weather.", weather))
				: Component.translatableWithFallback("sabercraft.droid.c3po.danger", "C-3PO: \"Hostile creatures nearby: %s! It is %s, and the weather is %s. Do be careful.\"",
						hostiles, time, word("sabercraft.droid.c3po.weather.", weather));
		owner.sendSystemMessage(report);
		return Result.used(report);
	}

	/** BB-8: hostile creatures within 24 blocks glow for ten seconds. */
	private static Result scan(ServerPlayer owner, DroidEntity droid) {
		List<LivingEntity> found = hostiles(owner.level(), owner, droid, 24.0);

		for (LivingEntity entity : found) {
			entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false), droid);
		}

		return Result.used(Component.translatableWithFallback("sabercraft.droid.bb8.scanned", "BB-8 marked hostile creatures: %s.", found.size()));
	}

	/** Chopper: an electric zap to every hostile creature right next to it. */
	private static Result zap(ServerPlayer owner, DroidEntity droid) {
		ServerLevel level = owner.level();
		List<LivingEntity> found = hostiles(level, owner, droid, 4.0);

		if (found.isEmpty()) {
			return Result.nothing(Component.translatableWithFallback("sabercraft.droid.chopper.nothing", "Chopper grumbles: nobody to zap."));
		}

		for (LivingEntity entity : found) {
			entity.hurtServer(level, level.damageSources().mobAttack(droid), 4.0F);
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), droid);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 12, 0.25, 0.4, 0.25, 0.1);
		}

		return Result.used(Component.translatableWithFallback("sabercraft.droid.chopper.zapped", "Chopper zapped hostile creatures: %s.", found.size()));
	}

	/** Mouse droid: dropped items within 12 blocks come to the owner. */
	private static Result fetch(ServerPlayer owner, DroidEntity droid) {
		List<ItemEntity> items = owner.level().getEntitiesOfClass(ItemEntity.class, droid.getBoundingBox().inflate(12.0),
				item -> item.isAlive() && !item.getItem().isEmpty());

		if (items.isEmpty()) {
			return Result.nothing(Component.translatableWithFallback("sabercraft.droid.mouse.nothing", "The mouse droid finds nothing to fetch."));
		}

		int count = 0;

		for (ItemEntity item : items.subList(0, Math.min(64, items.size()))) {
			item.teleportTo(owner.getX(), owner.getY() + 0.3, owner.getZ());
			item.setDeltaMovement(Vec3.ZERO);
			item.setNoPickUpDelay();
			count += item.getItem().getCount();
		}

		return Result.used(Component.translatableWithFallback("sabercraft.droid.mouse.fetched", "The mouse droid brought items: %s.", count));
	}

	/** Probe droid: the most valuable ore within 16 blocks, and where it is. */
	private static Result survey(ServerPlayer owner, DroidEntity droid) {
		Level level = owner.level();
		BlockPos center = droid.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bestRank = ORES.size();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int dx = -PROBE_RADIUS; dx <= PROBE_RADIUS; dx++) {
			for (int dy = -PROBE_RADIUS; dy <= PROBE_RADIUS; dy++) {
				for (int dz = -PROBE_RADIUS; dz <= PROBE_RADIUS; dz++) {
					pos.setWithOffset(center, dx, dy, dz);

					if (!level.isLoaded(pos)) {
						continue;
					}

					BlockState state = level.getBlockState(pos);

					if (state.isAir()) {
						continue;
					}

					for (int rank = 0; rank <= Math.min(bestRank, ORES.size() - 1); rank++) {
						if (ORES.get(rank).matches(state)) {
							double distance = pos.distSqr(center);

							if (rank < bestRank || distance < bestDistance) {
								bestRank = rank;
								best = pos.immutable();
								bestDistance = distance;
							}

							break;
						}
					}
				}
			}
		}

		if (best == null) {
			return Result.used(Component.translatableWithFallback("sabercraft.droid.probe.nothing", "Probe droid: no ore within %s blocks.", PROBE_RADIUS));
		}

		int dx = best.getX() - owner.getBlockX();
		int dy = best.getY() - owner.getBlockY();
		int dz = best.getZ() - owner.getBlockZ();
		String direction = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
		Component ore = word("sabercraft.droid.probe.ore.", ORES.get(bestRank).name());
		Component message = Component.translatableWithFallback("sabercraft.droid.probe.found", "Probe droid: %s, %s blocks %s, %s blocks %s.", ore,
				(int) Math.round(Math.sqrt(dx * dx + dz * dz)), word("sabercraft.droid.direction.", direction),
				Math.abs(dy), word("sabercraft.droid.direction.", dy < 0 ? "down" : "up"));
		owner.sendSystemMessage(message);
		return Result.used(message);
	}

	/** B1: a burst of three blaster bolts at the nearest hostile creature it can see. */
	private static Result burst(ServerPlayer owner, DroidEntity droid) {
		LivingEntity target = hostiles(owner.level(), owner, droid, 16.0).stream()
				.filter(droid::hasLineOfSight)
				.min(Comparator.comparingDouble(droid::distanceToSqr))
				.orElse(null);

		if (target == null) {
			return Result.nothing(Component.translatableWithFallback("sabercraft.droid.b1.nothing", "B1: \"No targets in sight.\""));
		}

		droid.startBurst(target, 3);
		return Result.used(Component.translatableWithFallback("sabercraft.droid.b1.firing", "B1 opens fire."));
	}

	/** K-2SO: hostile creatures close to the owner are shoved away. */
	private static Result shove(ServerPlayer owner, DroidEntity droid) {
		ServerLevel level = owner.level();
		List<LivingEntity> found = hostiles(level, owner, owner, 6.0);
		found.removeIf(entity -> entity.distanceToSqr(droid) > 12.0 * 12.0);

		if (found.isEmpty()) {
			return Result.nothing(Component.translatableWithFallback("sabercraft.droid.k2so.nothing", "K-2SO: \"There is nobody here worth shoving.\""));
		}

		for (LivingEntity entity : found) {
			Vec3 away = entity.position().subtract(owner.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? droid.getLookAngle().multiply(1.0, 0.0, 1.0) : away.normalize();
			entity.hurtServer(level, level.damageSources().mobAttack(droid), 2.0F);
			entity.setDeltaMovement(away.scale(1.3).add(0.0, 0.35, 0.0));
			entity.hurtMarked = true;
		}

		return Result.used(Component.translatableWithFallback("sabercraft.droid.k2so.shoved", "K-2SO shoved hostile creatures away: %s.", found.size()));
	}

	/** A word of a message: translated on clients with SaberCraft, the plain English word otherwise. */
	private static Component word(String prefix, String key) {
		return Component.translatableWithFallback(prefix + key, key.replace('_', ' '));
	}

	/**
	 * Hostile creatures within {@code radius} of {@code around}: monsters, and other players only
	 * when {@code droids_hit_players} is on. Never the owner, other droids or creative players.
	 */
	static List<LivingEntity> hostiles(ServerLevel level, ServerPlayer owner, Entity around, double radius) {
		boolean players = AbilityConfig.get().droidsHitPlayers;
		AABB box = around.getBoundingBox().inflate(radius);
		return level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive() && entity != owner
				&& entity.distanceToSqr(around) <= radius * radius
				&& DroidMarker.of(entity) == null
				&& (entity instanceof Enemy || players && entity instanceof Player player && !player.isSpectator() && !player.isCreative()));
	}
}
