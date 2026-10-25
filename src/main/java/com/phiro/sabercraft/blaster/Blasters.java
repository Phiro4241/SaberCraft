package com.phiro.sabercraft.blaster;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.item.ItemConversion;
import com.phiro.sabercraft.network.AbilityEventPayload;
import com.phiro.sabercraft.network.BlasterHeatPayload;
import com.phiro.sabercraft.network.BlasterSettingsPayload;
import com.phiro.sabercraft.network.BlasterShotPayload;
import com.phiro.sabercraft.network.SelectBlasterPayload;

/**
 * The blasters on the server (Phase 10, ammunition and the menu in Phase 11). Right-clicking with a
 * converted bow fires at once instead of drawing the bow; holding the key keeps firing at the
 * blaster's rate. The server decides everything: whether a shot is possible (fire rate, heat,
 * ammunition), where the bolt flies, what it hits and how much damage it does. The bolts are real
 * entities, so every player sees them; the shot, its muzzle flash, sound and particles are sent to
 * everyone nearby as well.
 *
 * <p><b>Ammunition (Phase 11):</b> every shot needs one plain arrow (no tipped or spectral
 * arrows), taken from the off hand first like a bow does, then from the hotbar and the rest of the
 * inventory. A shot without an arrow does not happen: nothing is fired, nothing heats up, and the
 * player hears a dry click and reads a short note. Exactly one arrow goes per shot, however many
 * bolts it has. The arrow is taken in the same server tick in which the bolts are made, and the fire
 * rate is a cooldown on the server, so fast clicking, lag or packets arriving in bursts never give
 * a free shot: every use packet is checked against the server's own state. Creative players need
 * no arrows (like a bow), and a blaster with Infinity needs one but keeps it (like a bow, can be
 * turned off with {@code blaster_infinity}). The blaster never shoots a real arrow.
 *
 * <p>Every shot also heats the blaster up; at 100 it overheats and locks until it has cooled down
 * completely. Each shot costs one point of durability, so Unbreaking and Mending matter (the stun
 * blaster in Ezra Bridger's saber hilt costs none). Power adds damage (+25 % per level), Flame sets
 * targets on fire and Punch pushes them back. All enchantments and other item data stay on the bow.
 */
public final class Blasters {
	public static final float MAX_HEAT = 100.0F;
	/** Ticks without a shot before the blaster starts to cool down. */
	private static final int COOL_DELAY = 10;
	/** Ticks between two "no ammunition" notes, so holding the key does not flood the player. */
	private static final int EMPTY_NOTE_TICKS = 10;
	private static final Map<UUID, Heat> HEAT = new HashMap<>();

	private static final class Heat {
		float value;
		boolean overheated;
		long lastShot = Long.MIN_VALUE / 2;
		long lastEmptyNote = Long.MIN_VALUE / 2;
		float sentValue = -1.0F;
		boolean sentOverheated;
	}

	/** Why a shot did not go off, for tests and the client's own check. */
	public enum Refusal {
		NONE, DISABLED, NOT_READY, OVERHEATED, NO_AMMO
	}

	private Blasters() {
	}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			BlasterType type = BlasterType.firing(stack);

			if (type == null || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			if (!AbilityConfig.get().blastersEnabled) {
				// A converted bow stays a blaster that does nothing: never draw it and shoot an arrow.
				return BlasterType.of(stack) != null ? InteractionResult.FAIL : InteractionResult.PASS;
			}

			fire(serverPlayer, hand, stack, type);
			// Never start drawing the bow, whether the shot went off or not.
			// A saber on Ezra's hilt fires its stun blaster the same way.
			return InteractionResult.CONSUME;
		});
		ServerTickEvents.END_SERVER_TICK.register(Blasters::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HEAT.remove(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.getPlayer(), BlasterSettingsPayload.TYPE)) {
				AbilityConfig config = AbilityConfig.get();
				ServerPlayNetworking.send(handler.getPlayer(), new BlasterSettingsPayload(config.blasterAmmo, config.blasterInfinity));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SelectBlasterPayload.TYPE, (payload, context) -> select(context.player(), payload));
	}

	/** Fires one shot if the blaster is ready and there is ammunition. Returns whether it fired. */
	public static boolean fire(ServerPlayer player, InteractionHand hand, ItemStack stack, BlasterType type) {
		return tryFire(player, hand, stack, type) == Refusal.NONE;
	}

	/** Fires one shot if possible; returns why not otherwise. */
	public static Refusal tryFire(ServerPlayer player, InteractionHand hand, ItemStack stack, BlasterType type) {
		if (player.isSpectator() || !player.isAlive() || player.getCooldowns().isOnCooldown(stack)) {
			return Refusal.NOT_READY;
		}

		ServerLevel level = player.level();
		AbilityConfig config = AbilityConfig.get();
		Heat heat = HEAT.computeIfAbsent(player.getUUID(), id -> new Heat());

		if (heat.overheated) {
			return Refusal.OVERHEATED;
		}

		// Ammunition first: no arrow, no shot, no heat, no cooldown. Only blasters made from a bow
		// take arrows; the stun blaster built into Ezra's saber hilt has its own power cell.
		int arrowSlot = -1;

		if (BlasterType.of(stack) != null && needsAmmo(player, config)) {
			arrowSlot = findArrow(player.getInventory());

			if (arrowSlot < 0) {
				noAmmo(player, heat, level.getGameTime());
				return Refusal.NO_AMMO;
			}
		}

		if (arrowSlot >= 0 && !(config.blasterInfinity && level(level, stack, Enchantments.INFINITY) > 0)) {
			player.getInventory().removeItem(arrowSlot, 1);
		}

		int power = level(level, stack, Enchantments.POWER);
		float damage = type.damage() * config.blasterDamage * (1.0F + 0.25F * power);
		boolean flame = level(level, stack, Enchantments.FLAME) > 0;
		float punch = level(level, stack, Enchantments.PUNCH) + type.knockback();
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
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound("blaster.vent"), SoundSource.PLAYERS, 0.7F, 1.0F);
			}
		}

		if (type.slowsShooter()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 0, false, false, false));
		}

		boolean bow = BlasterType.of(stack) != null;

		if (bow && !player.getAbilities().instabuild) {
			stack.hurtAndBreak(1, player, hand);
		}

		if (!bow) {
			// The blaster in Ezra's hilt: every client plays the shot from the hilt.
			SaberAbilities.broadcast(player, new AbilityEventPayload(player.getId(), SaberAbility.GUARD, AbilityEventPayload.Event.SHOT));
		}

		// Muzzle flash and recoil on every client that sees the shooter (Phase 11).
		SaberAbilities.broadcast(player, new BlasterShotPayload(player.getId(), hand, BlasterType.ALL.indexOf(type)));
		// A few sparks a little in front of the barrel, small enough not to cover the view.
		int rgb = boltRgb(type);
		Vec3 flash = muzzle.add(player.getLookAngle().scale(0.6));
		level.sendParticles(new DustParticleOptions(rgb, 0.5F), flash.x, flash.y, flash.z, 3, 0.02, 0.02, 0.02, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, flash.x, flash.y, flash.z, 2, 0.02, 0.02, 0.02, 0.08);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, sound("blaster." + type.id().getPath()), SoundSource.PLAYERS, 1.0F,
				0.95F + level.getRandom().nextFloat() * 0.1F);
		sync(player, heat);
		SaberAdvancements.award(player, SaberAdvancements.FIRST_SHOT);
		return Refusal.NONE;
	}

	/** Whether a shot of this player takes an arrow: ammunition is on and they are not in creative. */
	public static boolean needsAmmo(ServerPlayer player, AbilityConfig config) {
		return config.blasterAmmo && !player.hasInfiniteMaterials();
	}

	/**
	 * The slot of the arrow a shot takes: the off hand first (where a bow looks first), then the
	 * hotbar and the rest of the inventory in order. Only plain arrows count, so tipped and spectral
	 * arrows are never wasted. -1 if there is none.
	 */
	public static int findArrow(Inventory inventory) {
		if (isAmmo(inventory.getItem(Inventory.SLOT_OFFHAND))) {
			return Inventory.SLOT_OFFHAND;
		}

		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (isAmmo(inventory.getItem(slot))) {
				return slot;
			}
		}

		return -1;
	}

	/** Plain arrows, the blaster ammunition. */
	public static boolean isAmmo(ItemStack stack) {
		return stack.is(Items.ARROW) && !stack.isEmpty();
	}

	/** Counts the arrows a blaster can use, for the HUD and the tests. */
	public static int countAmmo(Inventory inventory) {
		int count = isAmmo(inventory.getItem(Inventory.SLOT_OFFHAND)) ? inventory.getItem(Inventory.SLOT_OFFHAND).getCount() : 0;

		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (isAmmo(stack)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	/** The dry click and a short note in the action bar, at most every half second. */
	private static void noAmmo(ServerPlayer player, Heat heat, long now) {
		if (now - heat.lastEmptyNote < EMPTY_NOTE_TICKS) {
			return;
		}

		heat.lastEmptyNote = now;
		player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.blaster.no_ammo", "No arrows – a blaster needs one arrow per shot.")
				.withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound("blaster.empty"), SoundSource.PLAYERS, 0.9F, 1.0F);
	}

	/**
	 * The blaster menu asked to turn the bow in a hand into a blaster of a type, or back. The same
	 * rules as {@code /sabercraft blaster}: alive, not a spectator, a bow in that hand, a known type,
	 * and allowed to convert ({@code commands_for_everyone} or an operator).
	 */
	static void select(ServerPlayer player, SelectBlasterPayload payload) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}

		if (!AbilityConfig.get().commandsForEveryone && !Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) {
			player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.blaster.menu.not_allowed", "Only operators may convert items on this server.")
					.withStyle(ChatFormatting.RED));
			return;
		}

		ItemStack stack = player.getItemInHand(payload.hand());

		if (payload.blaster().isEmpty()) {
			if (ItemConversion.fromBlaster(stack) == ItemConversion.Result.DONE) {
				player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.command.reverted_bow", "The blaster is a normal bow again."));
			}

			return;
		}

		BlasterType type = BlasterType.byId(SaberCraft.id(payload.blaster())).orElse(null);

		if (type == null) {
			SaberCraft.LOGGER.warn("{} asked for an unknown blaster {}", player.getPlainTextName(), payload.blaster());
			return;
		}

		ItemConversion.Result result = ItemConversion.toBlaster(stack, type);

		if (result == ItemConversion.Result.DONE || result == ItemConversion.Result.CHANGED) {
			player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.command.blaster_changed", "The blaster is now a %s.",
					Component.translatableWithFallback(type.translationKey(), type.id().getPath())));
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound("blaster.vent"), SoundSource.PLAYERS, 0.4F, 1.4F);
		}
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
