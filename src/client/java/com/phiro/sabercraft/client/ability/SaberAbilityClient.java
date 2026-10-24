package com.phiro.sabercraft.client.ability;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.DodgeDirection;
import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.ability.SaberManeuver;
import com.phiro.sabercraft.ability.StyleTraits;
import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.client.combat.CombatLibrary;
import com.phiro.sabercraft.client.combat.CombatMove;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.combat.CombatStyle;
import com.phiro.sabercraft.client.effect.SaberEffects;
import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.sound.SaberSounds;
import com.phiro.sabercraft.network.AbilityCooldownPayload;
import com.phiro.sabercraft.network.AbilityEventPayload;
import com.phiro.sabercraft.network.AbilitySettingsPayload;
import com.phiro.sabercraft.network.StaminaPayload;
import com.phiro.sabercraft.saber.SaberVariant;
import com.phiro.sabercraft.network.UseAbilityPayload;

/**
 * Client side of the abilities: the keys, the animations and sounds when an ability is used, and
 * the cooldown display.
 *
 * <p>The keys only send a request. The server decides, and tells every client that can see the
 * player what happened, so everybody sees the same spin or push. Your own animation starts right
 * on the key press, so it feels instant on a server with lag; the server's answer is then only
 * used for the cooldown.
 */
public final class SaberAbilityClient {
	public static final Identifier MOVE_PUSH = SaberCraft.id("force_push");
	public static final Identifier MOVE_DASH = SaberCraft.id("dash");

	public static final KeyMapping GUARD_KEY = key("guard", InputConstants.KEY_R);
	public static final KeyMapping SPIN_KEY = key("spin", InputConstants.KEY_Z);
	public static final KeyMapping PUSH_KEY = key("push", InputConstants.KEY_B);
	public static final KeyMapping DASH_KEY = key("dash", InputConstants.KEY_LALT);

	/** Ticks after a predicted use in which the server's start event for yourself is skipped. */
	private static final int PREDICTION_TICKS = 20;

	private static final Map<SaberAbility, Cooldown> COOLDOWNS = new EnumMap<>(SaberAbility.class);
	private static final Map<SaberAbility, Integer> PREDICTED = new EnumMap<>(SaberAbility.class);
	private static boolean guardSent;
	/** The server ended the stance while the key was still held: wait for a release. */
	private static boolean guardBlocked;
	private static int ticks;
	/** The server's settings, or the defaults until a server with SaberCraft sent them. */
	private static AbilitySettingsPayload settings = new AbilitySettingsPayload(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
	private static float stamina;

	private SaberAbilityClient() {
	}

	/** A cooldown as shown in the HUD. */
	public record Cooldown(int left, int total) {
	}

	private static KeyMapping key(String name, int key) {
		return new KeyMapping("key.sabercraft." + name, InputConstants.Type.KEYSYM, key, SaberCraftClient.KEY_CATEGORY);
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(GUARD_KEY);
		KeyMappingHelper.registerKeyMapping(SPIN_KEY);
		KeyMappingHelper.registerKeyMapping(PUSH_KEY);
		KeyMappingHelper.registerKeyMapping(DASH_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(SaberAbilityClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(AbilityEventPayload.TYPE, (payload, context) -> onEvent(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(AbilityCooldownPayload.TYPE, (payload, context) ->
				COOLDOWNS.put(payload.ability(), new Cooldown(payload.ticks(), Math.max(1, payload.total()))));
		ClientPlayNetworking.registerGlobalReceiver(AbilitySettingsPayload.TYPE, (payload, context) -> {
			settings = payload;
			stamina = payload.staminaMax();
			ManeuverController.setSpeedScale(payload.dodgeSpeed());
		});
		ClientPlayNetworking.registerGlobalReceiver(StaminaPayload.TYPE, (payload, context) -> stamina = payload.stamina());
		ClientTickEvents.START_CLIENT_TICK.register(ManeuverController::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			reset();
			settings = new AbilitySettingsPayload(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
			ManeuverController.setSpeedScale(1.0F);
		});
		// No attacks while the stance is up, the server would ignore them anyway.
		ClientPreAttackCallback.EVENT.register((client, player, clicks) -> guardSent);
		HudElementRegistry.addLast(SaberCraft.id("ability_cooldowns"), new AbilityHud());
	}

	public static @Nullable Cooldown cooldown(SaberAbility ability) {
		return COOLDOWNS.get(ability);
	}

	public static boolean isGuarding() {
		return guardSent;
	}

	/** The stamina the server last sent. */
	public static float stamina() {
		return stamina;
	}

	/** Most stamina, 0 if the server has none (turned off, or no SaberCraft on the server). */
	public static float maxStamina() {
		return settings.staminaMax();
	}

	/** Stamina the ability would cost now, as the server counts it. */
	public static float cost(SaberAbility ability, LocalPlayer player) {
		return switch (ability) {
			case GUARD -> settings.guardCost();
			case SPIN -> settings.spinCost();
			case PUSH -> settings.pushCost();
			case DASH -> settings.dodgeCost() * StyleTraits.of(SaberVariant.styleOf(player.getMainHandItem())).dodgeCost();
		};
	}

	public static KeyMapping keyOf(SaberAbility ability) {
		return switch (ability) {
			case GUARD -> GUARD_KEY;
			case SPIN -> SPIN_KEY;
			case PUSH -> PUSH_KEY;
			case DASH -> DASH_KEY;
		};
	}

	/** Forgets all cooldowns, for tests that reset the server side as well. */
	public static void resetForTests() {
		reset();
	}

	private static void reset() {
		COOLDOWNS.clear();
		PREDICTED.clear();
		ManeuverController.reset();
		guardSent = false;
		guardBlocked = false;
	}

	private static void tick(Minecraft client) {
		ticks++;
		COOLDOWNS.replaceAll((ability, cooldown) -> new Cooldown(Math.max(0, cooldown.left() - 1), cooldown.total()));
		LocalPlayer player = client.player;

		if (player == null) {
			drain();
			return;
		}

		boolean lit = litSaber(player);
		boolean screenOpen = client.gui.screen() != null;
		boolean wantGuard = GUARD_KEY.isDown() && lit && !screenOpen && player.isAlive();

		if (!GUARD_KEY.isDown()) {
			guardBlocked = false;
		}

		if (wantGuard && !guardSent && !guardBlocked && ready(SaberAbility.GUARD) && rested(player, SaberAbility.GUARD)) {
			if (send(player, SaberAbility.GUARD, true)) {
				guardSent = true;
				setGuarding(player, true);
				PREDICTED.put(SaberAbility.GUARD, ticks);
			}
		} else if (!wantGuard && guardSent) {
			guardSent = false;
			send(player, SaberAbility.GUARD, false);
			setGuarding(player, false);
		}

		trigger(client, player, SPIN_KEY, SaberAbility.SPIN, lit);
		trigger(client, player, PUSH_KEY, SaberAbility.PUSH, lit);
		trigger(client, player, DASH_KEY, SaberAbility.DASH, lit);
		// Clicks of the guard key itself are not needed, only whether it is held.
		while (GUARD_KEY.consumeClick()) {
		}
	}

	private static void drain() {
		for (SaberAbility ability : SaberAbility.values()) {
			while (keyOf(ability).consumeClick()) {
			}
		}
	}

	private static void trigger(Minecraft client, LocalPlayer player, KeyMapping key, SaberAbility ability, boolean lit) {
		boolean pressed = false;

		while (key.consumeClick()) {
			pressed = true;
		}

		if (!pressed || client.gui.screen() != null || !player.isAlive() || guardSent) {
			return;
		}

		if (!lit) {
			player.sendOverlayMessage(Component.translatable("sabercraft.ability.no_saber"));
			return;
		}

		if (!ready(ability)) {
			return;
		}

		if (!rested(player, ability)) {
			player.sendOverlayMessage(Component.translatable("sabercraft.ability.exhausted"));
			return;
		}

		int data = 0;

		if (ability == SaberAbility.DASH) {
			Input input = player.input.keyPresses;
			data = DodgeDirection.fromInput(input.forward(), input.backward(), input.left(), input.right()).ordinal();
		}

		if (send(player, ability, true, data)) {
			PREDICTED.put(ability, ticks);
			start(player, ability, data);
		}
	}

	/** Enough stamina for the ability, so the server will not refuse it. */
	private static boolean rested(LocalPlayer player, SaberAbility ability) {
		return settings.staminaMax() <= 0.0F || stamina >= cost(ability, player);
	}

	private static boolean ready(SaberAbility ability) {
		Cooldown cooldown = COOLDOWNS.get(ability);
		return cooldown == null || cooldown.left() <= 0;
	}

	private static boolean send(LocalPlayer player, SaberAbility ability, boolean pressed) {
		return send(player, ability, pressed, 0);
	}

	private static boolean send(LocalPlayer player, SaberAbility ability, boolean pressed, int data) {
		if (!ClientPlayNetworking.canSend(UseAbilityPayload.TYPE)) {
			if (pressed) {
				player.sendOverlayMessage(Component.translatable("sabercraft.ability.no_server"));
			}

			return false;
		}

		ClientPlayNetworking.send(new UseAbilityPayload(ability, pressed, data));
		return true;
	}

	/** A lit saber in the main hand, the hand the server checks. */
	private static boolean litSaber(LivingEntity entity) {
		SaberHandState state = SaberTracker.INSTANCE.state(entity, InteractionHand.MAIN_HAND);
		return state != null && state.isActive() && state.extension(1.0F) > 0.8F;
	}

	private static void onEvent(Minecraft client, AbilityEventPayload payload) {
		if (client.level == null || !(client.level.getEntity(payload.entityId()) instanceof LivingEntity entity)) {
			return;
		}

		boolean self = entity == client.player;

		switch (payload.event()) {
			case START -> {
				Integer predicted = PREDICTED.get(payload.ability());

				if (self && predicted != null && ticks - predicted <= PREDICTION_TICKS) {
					PREDICTED.remove(payload.ability());
					return;
				}

				if (payload.ability() == SaberAbility.GUARD) {
					setGuarding(entity, true);
				} else {
					start(entity, payload.ability(), payload.data());
				}
			}
			case STOP -> {
				setGuarding(entity, false);

				if (self && guardSent) {
					guardSent = false;
					guardBlocked = true;
				}
			}
			case BLOCK, PARRY -> {
				boolean parry = payload.event() == AbilityEventPayload.Event.PARRY;
				play(entity, parry ? SaberSounds.PARRY : SaberSounds.CLASH, parry ? 1.0F : 0.85F);
				SaberEffects.onBlock(entity, parry);
				react(entity, style -> parry ? style.parry() : style.guardHit());
			}
			case GUARD_BREAK -> {
				// The block took more than the stamina left: the stance is gone and the blade knocked aside.
				setGuarding(entity, false);

				if (self && guardSent) {
					guardSent = false;
					guardBlocked = true;
				}

				play(entity, SaberSounds.CLASH, 1.0F, 0.7F);
				SaberEffects.onBlock(entity, false);
				react(entity, CombatStyle::guardBreak);
			}
			case UNBALANCED -> SaberEffects.onUnbalanced(entity);
			case EVADE -> play(entity, SaberSounds.DASH, 0.5F);
		}
	}

	/** Plays the animation, sound and effect of an ability on any entity. */
	private static void start(LivingEntity entity, SaberAbility ability, int data) {
		CombatState combat = SaberTracker.INSTANCE.combat(entity);
		float now = SaberTracker.INSTANCE.time(0.0F);
		CombatMove move = null;
		CombatStyle style = combat != null ? combat.style() : null;

		switch (ability) {
			case GUARD -> setGuarding(entity, true);
			case SPIN -> move = style != null ? style.special() : null;
			case PUSH -> {
				move = CombatLibrary.INSTANCE.move(MOVE_PUSH);
				play(entity, SaberSounds.PUSH, 1.0F);
			}
			case DASH -> {
				// Style and direction pick the move: a roll, a lunge, a step or a flip.
				DodgeDirection direction = DodgeDirection.byId(data);
				move = style != null ? style.dodges().get(direction) : null;

				if (move == null) {
					move = CombatLibrary.INSTANCE.move(MOVE_DASH);
				}

				play(entity, SaberSounds.DASH, 0.9F);

				if (entity instanceof LocalPlayer player) {
					ManeuverController.start(player, SaberManeuver.of(SaberVariant.styleOf(player.getMainHandItem()), direction), direction);
				}
			}
		}

		if (combat != null && move != null) {
			combat.play(move, now);
		}

		SaberEffects.onAbility(entity, ability);
	}

	/** Plays a reaction move of the entity's style, if it has one. */
	private static void react(LivingEntity entity, java.util.function.Function<CombatStyle, @Nullable CombatMove> pick) {
		CombatState combat = SaberTracker.INSTANCE.combat(entity);
		CombatStyle style = combat != null ? combat.style() : null;
		CombatMove move = style != null ? pick.apply(style) : null;

		if (move != null) {
			combat.play(move, SaberTracker.INSTANCE.time(0.0F));
		}
	}

	private static void setGuarding(LivingEntity entity, boolean guarding) {
		CombatState combat = SaberTracker.INSTANCE.combat(entity);

		if (combat != null && combat.isGuarding() != guarding) {
			combat.setGuarding(guarding, SaberTracker.INSTANCE.time(0.0F));

			if (guarding) {
				play(entity, SaberSounds.GUARD, 0.8F);
			}
		}
	}

	private static void play(Entity entity, SoundEvent sound, float volume) {
		play(entity, sound, volume, 1.0F);
	}

	private static void play(Entity entity, SoundEvent sound, float volume, float basePitch) {
		float pitch = basePitch * (0.95F + entity.getRandom().nextFloat() * 0.1F);
		Minecraft.getInstance().getSoundManager().play(new EntityBoundSoundInstance(
				sound, entity.getSoundSource(), volume, pitch, entity, entity.getRandom().nextLong()));
	}
}
