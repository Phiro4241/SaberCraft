package com.phiro.sabercraft.client.blaster;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.blaster.Blasters;
import com.phiro.sabercraft.client.render.BlasterFlashItemModel;
import com.phiro.sabercraft.network.BlasterHeatPayload;
import com.phiro.sabercraft.network.BlasterSettingsPayload;
import com.phiro.sabercraft.network.BlasterShotPayload;
import com.phiro.sabercraft.network.UseAbilityPayload;

/**
 * The client side of the blasters (Phase 10, muzzle flash, ammunition and vents in Phase 11): the
 * bolt renderer, the heat and ammunition display, the right-click, the muzzle flash and the
 * recoil. A click on a blaster is sent to the server as a normal item use, the server fires; the
 * client only makes sure the bow is not drawn. Without an arrow it does not even ask the server
 * and gives the "no arrows" note at once (the server checks again and would refuse anyway).
 *
 * <p>The flash and the recoil of a shot come from the server's shot message, so every player sees
 * them on every blaster. The own shot is shown at once when it is sure to go off (ready, not
 * overheated, an arrow at hand), and the server's message for it is then not shown a second time.
 * On a server without SaberCraft a converted bow stays a bow.
 */
public final class BlasterClient {
	/** How long a muzzle flash lasts, in milliseconds. */
	private static final float FLASH_MILLIS = 120.0F;
	/** Screenshot tests hold the flash longer, since a picture takes longer than a flash. */
	private static float flashMillis = FLASH_MILLIS;
	/** An own shot shown in advance hides the server's message about it for this long. */
	private static final long PREDICTION_MILLIS = 400L;

	private static float heat;
	private static boolean overheated;
	private static long overheatedSince = Long.MIN_VALUE / 2;
	private static boolean ammoRule = true;
	private static boolean infinityRule = true;
	private static long lastShotMillis = Long.MIN_VALUE / 2;
	private static InteractionHand lastShotHand = InteractionHand.MAIN_HAND;
	private static long lastPredictedMillis = Long.MIN_VALUE / 2;
	private static long lastEmptyMillis = Long.MIN_VALUE / 2;
	/** Last shot per entity and hand: entity id * 2 + hand ordinal. */
	private static final Map<Integer, Long> SHOTS = new HashMap<>();
	/** The hand of a shot whose arm swing is still to come in this tick. */
	private static @Nullable InteractionHand shotSwing;

	private BlasterClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(EntityTypes.SNOWBALL, BlasterBoltRenderer::new);
		ItemModels.ID_MAPPER.put(SaberCraft.id("blaster_flash"), BlasterFlashItemModel.Unbaked.MAP_CODEC);
		ClientPlayNetworking.registerGlobalReceiver(BlasterHeatPayload.TYPE, (payload, context) -> {
			if (payload.overheated() && !overheated) {
				overheatedSince = Util.getMillis();
			}

			heat = payload.heat();
			overheated = payload.overheated();
		});
		ClientPlayNetworking.registerGlobalReceiver(BlasterSettingsPayload.TYPE, (payload, context) -> {
			ammoRule = payload.ammo();
			infinityRule = payload.infinity();
		});
		ClientPlayNetworking.registerGlobalReceiver(BlasterShotPayload.TYPE, (payload, context) -> onShot(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			heat = 0.0F;
			overheated = false;
			ammoRule = true;
			infinityRule = true;
			SHOTS.clear();
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);

			if (!level.isClientSide() || BlasterType.firing(stack) == null || !serverHasBlasters()) {
				return InteractionResult.PASS;
			}

			if (overheated || player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.FAIL;
			}

			if (BlasterType.of(stack) != null && needsAmmo(player) && Blasters.findArrow(player.getInventory()) < 0) {
				noAmmo(Minecraft.getInstance());
				return InteractionResult.FAIL;
			}

			long now = Util.getMillis();
			lastShotMillis = now;
			lastPredictedMillis = now;
			lastShotHand = hand;
			shotSwing = hand;
			SHOTS.put(key(player.getId(), hand), now);
			// Only SUCCESS is sent to the server as an item use. Its arm swing is left out for
			// blasters (LocalPlayerMixin), and the bow is not drawn.
			return InteractionResult.SUCCESS;
		});
		// The swing follows the shot in the same tick; never keep the mark longer.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			shotSwing = null;
			vent(client);
		});
		HudElementRegistry.addLast(SaberCraft.id("blaster_heat"), new BlasterHud());
	}

	private static void onShot(Minecraft client, BlasterShotPayload payload) {
		long now = Util.getMillis();
		boolean own = client.player != null && payload.entityId() == client.player.getId();

		if (own && now - lastPredictedMillis < PREDICTION_MILLIS) {
			// Already shown when the key was pressed.
			lastPredictedMillis = Long.MIN_VALUE / 2;
			return;
		}

		SHOTS.put(key(payload.entityId(), payload.hand()), now);

		if (own) {
			lastShotMillis = now;
			lastShotHand = payload.hand();
		}
	}

	private static int key(int entityId, InteractionHand hand) {
		return entityId * 2 + hand.ordinal();
	}

	/** Whether shots of the local player use arrows: the server's rule, not in creative. */
	public static boolean needsAmmo(net.minecraft.world.entity.player.Player player) {
		return ammoRule && !player.hasInfiniteMaterials();
	}

	/** The "no arrows" note: action bar and a dry click, at most twice a second. */
	private static void noAmmo(Minecraft client) {
		long now = Util.getMillis();

		if (now - lastEmptyMillis < 500L) {
			return;
		}

		lastEmptyMillis = now;
		client.gui.hud.setOverlayMessage(Component.translatableWithFallback("sabercraft.blaster.no_ammo", "No arrows – a blaster needs one arrow per shot.")
				.withStyle(ChatFormatting.RED), false);
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(SaberCraft.id("blaster.empty")), 1.0F, 0.9F));
	}

	/** While the blaster is overheated, steam comes out of it now and then. */
	private static void vent(Minecraft client) {
		LocalPlayer player = client.player;

		if (!overheated || player == null || client.level == null || player.tickCount % 3 != 0) {
			return;
		}

		InteractionHand hand = BlasterType.firing(player.getMainHandItem()) != null ? InteractionHand.MAIN_HAND
				: BlasterType.firing(player.getOffhandItem()) != null ? InteractionHand.OFF_HAND : null;

		if (hand == null) {
			return;
		}

		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.7)).add(right.scale(0.3 * side)).add(0.0, -0.3, 0.0);
		client.level.addParticle(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 0.0, 0.03, 0.0);
	}

	/** True once for the arm swing that vanilla adds to a shot, which is then left out. */
	public static boolean takeShotSwing(InteractionHand hand) {
		if (shotSwing != hand) {
			return false;
		}

		shotSwing = null;
		return true;
	}

	/** Whether the server has SaberCraft, so it fires the blasters. */
	public static boolean serverHasBlasters() {
		return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(UseAbilityPayload.TYPE);
	}

	public static float heat() {
		return heat;
	}

	public static boolean overheated() {
		return overheated;
	}

	/** Whether the server wants arrows for blaster shots. */
	public static boolean ammoRule() {
		return ammoRule;
	}

	/** Whether the server lets Infinity keep the arrows. */
	public static boolean infinityRule() {
		return infinityRule;
	}

	/** How bright the muzzle flash of an entity's hand is right now, 0..1. */
	public static float flash(int entityId, InteractionHand hand) {
		Long at = SHOTS.get(key(entityId, hand));

		if (at == null) {
			return 0.0F;
		}

		float age = Util.getMillis() - at;

		if (age < 0.0F || age > flashMillis) {
			return 0.0F;
		}

		return age < 35.0F ? 1.0F : 1.0F - (age - 35.0F) / (flashMillis - 35.0F);
	}

	/** For screenshot tests: let the flash last this long (0 = normal). */
	public static void holdFlashForTests(float millis) {
		flashMillis = millis > 0.0F ? millis : FLASH_MILLIS;
	}

	/** When the "no arrows" note was last shown, for tests; never = a very small number. */
	public static long lastEmpty() {
		return lastEmptyMillis;
	}

	/** When the last shot of an entity's hand was seen, for tests; 0 if never. */
	public static long lastShot(int entityId, InteractionHand hand) {
		return SHOTS.getOrDefault(key(entityId, hand), 0L);
	}

	/** How far the first-person blaster is kicked back right now, 0..1. */
	public static float recoil(InteractionHand hand) {
		if (hand != lastShotHand) {
			return 0.0F;
		}

		float age = (Util.getMillis() - lastShotMillis) / 1000.0F;

		if (age < 0.0F || age > 0.18F) {
			return 0.0F;
		}

		// A sharp kick, then back over the rest of the time.
		return age < 0.03F ? age / 0.03F : 1.0F - (age - 0.03F) / 0.15F;
	}

	/**
	 * How far the first-person blaster is turned aside to vent while it is overheated, 0..1: it
	 * turns over within a quarter second and back when it has cooled down.
	 */
	public static float venting() {
		if (!overheated) {
			return 0.0F;
		}

		float age = (Util.getMillis() - overheatedSince) / 250.0F;
		return Mth.clamp(age, 0.0F, 1.0F);
	}
}
