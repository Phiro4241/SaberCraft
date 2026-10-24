package com.phiro.sabercraft.blaster;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.network.BlasterHeatPayload;

/**
 * The blasters on the server (Phase 10). Right-clicking with a converted bow fires at once
 * instead of drawing the bow; holding the key keeps firing at the blaster's rate. The server
 * decides everything: whether a shot is possible (fire rate, heat), where the bolt flies, what it
 * hits and how much damage it does. The bolts are real entities, so every player sees them; the
 * shot and impact sounds and particles are sent to everyone nearby as well.
 *
 * <p>No arrows are needed. Instead every shot heats the blaster up; at 100 it overheats and
 * locks until it has cooled down completely. Each shot also costs one point of durability, so
 * Unbreaking and Mending matter. Power adds damage (+25 % per level), Flame sets targets on fire
 * and Punch pushes them back. All enchantments and other item data stay on the bow.
 */
public final class Blasters {
	public static final float MAX_HEAT = 100.0F;
	/** Ticks without a shot before the blaster starts to cool down. */
	private static final int COOL_DELAY = 10;
	private static final Map<UUID, Heat> HEAT = new HashMap<>();

	private static final class Heat {
		float value;
		boolean overheated;
		long lastShot = Long.MIN_VALUE / 2;
		float sentValue = -1.0F;
		boolean sentOverheated;
	}

	private Blasters() {
	}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			BlasterType type = BlasterType.of(stack);

			if (type == null || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			if (!AbilityConfig.get().blastersEnabled) {
				return InteractionResult.PASS;
			}

			fire(serverPlayer, hand, stack, type);
			// Never start drawing the bow, whether the shot went off or not.
			return InteractionResult.CONSUME;
		});
		ServerTickEvents.END_SERVER_TICK.register(Blasters::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HEAT.remove(handler.getPlayer().getUUID()));
	}

	/** Fires one shot if the blaster is ready. Returns whether it fired. */
	public static boolean fire(ServerPlayer player, InteractionHand hand, ItemStack stack, BlasterType type) {
		if (player.isSpectator() || !player.isAlive() || player.getCooldowns().isOnCooldown(stack)) {
			return false;
		}

		ServerLevel level = player.level();
		AbilityConfig config = AbilityConfig.get();
		Heat heat = HEAT.computeIfAbsent(player.getUUID(), id -> new Heat());

		if (heat.overheated) {
			return false;
		}

		int power = level(level, stack, Enchantments.POWER);
		float damage = type.damage() * config.blasterDamage * (1.0F + 0.25F * power);
		boolean flame = level(level, stack, Enchantments.FLAME) > 0;
		float punch = level(level, stack, Enchantments.PUNCH);
		Vec3 muzzle = muzzle(player, hand);

		for (int i = 0; i < type.bolts(); i++) {
			BlasterBolt bolt = new BlasterBolt(level, player, type, damage, flame, punch);
			bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
			bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, type.speed(), type.spread());
			level.addFreshEntity(bolt);
		}

		player.getCooldowns().addCooldown(stack, type.interval());
		heat.lastShot = level.getGameTime();

		if (config.blasterHeat) {
			heat.value = Math.min(MAX_HEAT, heat.value + type.heat());

			if (heat.value >= MAX_HEAT) {
				heat.overheated = true;
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound("blaster.overheat"), SoundSource.PLAYERS, 0.8F, 1.0F);
			}
		}

		if (type.slowsShooter()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 0, false, false, false));
		}

		if (!player.getAbilities().instabuild) {
			stack.hurtAndBreak(1, player, hand);
		}

		// The muzzle flash a little in front of the barrel, small enough not to cover the view.
		int rgb = boltRgb(type);
		Vec3 flash = muzzle.add(player.getLookAngle().scale(0.6));
		level.sendParticles(new DustParticleOptions(rgb, 0.5F), flash.x, flash.y, flash.z, 3, 0.02, 0.02, 0.02, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, flash.x, flash.y, flash.z, 2, 0.02, 0.02, 0.02, 0.08);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, sound("blaster." + type.id().getPath()), SoundSource.PLAYERS, 1.0F,
				0.95F + level.getRandom().nextFloat() * 0.1F);
		sync(player, heat);
		return true;
	}

	/** Where the bolts leave: a bit in front of the eyes, to the side of the hand holding the blaster. */
	static Vec3 muzzle(ServerPlayer player, InteractionHand hand) {
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		Vec3 look = player.getLookAngle();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		return player.getEyePosition().add(look.scale(0.5)).add(right.scale(0.22 * side)).add(0.0, -0.15, 0.0);
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Heat>> entries = HEAT.entrySet().iterator();

		while (entries.hasNext()) {
			Map.Entry<UUID, Heat> entry = entries.next();
			Heat heat = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null) {
				entries.remove();
				continue;
			}

			long now = player.level().getGameTime();

			if (heat.overheated) {
				heat.value -= AbilityConfig.get().blasterVentRate;

				if (heat.value <= 0.0F) {
					heat.value = 0.0F;
					heat.overheated = false;
				}
			} else if (now - heat.lastShot > COOL_DELAY && heat.value > 0.0F) {
				heat.value = Math.max(0.0F, heat.value - AbilityConfig.get().blasterCoolRate);
			}

			sync(player, heat);
		}
	}

	private static void sync(ServerPlayer player, Heat heat) {
		boolean changed = Math.abs(heat.value - heat.sentValue) >= 1.0F || heat.value == 0.0F && heat.sentValue != 0.0F || heat.overheated != heat.sentOverheated;

		if (changed && ServerPlayNetworking.canSend(player, BlasterHeatPayload.TYPE)) {
			ServerPlayNetworking.send(player, new BlasterHeatPayload(heat.value, heat.overheated));
			heat.sentValue = heat.value;
			heat.sentOverheated = heat.overheated;
		}
	}

	/** The heat of a player (0..100), for tests. */
	public static float heat(UUID player) {
		Heat heat = HEAT.get(player);
		return heat == null ? 0.0F : heat.value;
	}

	/** Whether a player's blaster is overheated, for tests. */
	public static boolean overheated(UUID player) {
		Heat heat = HEAT.get(player);
		return heat != null && heat.overheated;
	}

	/** Forgets a player's heat, for tests. */
	public static void cool(UUID player) {
		HEAT.remove(player);
	}

	/** The color of a bolt for particles. */
	public static int boltRgb(BlasterType type) {
		return switch (type.color().getPath()) {
			case "blue" -> 0x3A7BFF;
			case "green" -> 0x3BFF5A;
			case "yellow" -> 0xFFD23A;
			case "purple" -> 0xB04BFF;
			case "white" -> 0xF0F0FF;
			default -> 0xFF2A1E;
		};
	}

	static Holder<SoundEvent> sound(String name) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id(name)));
	}

	private static int level(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key) {
		Holder<Enchantment> enchantment = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
	}
}
