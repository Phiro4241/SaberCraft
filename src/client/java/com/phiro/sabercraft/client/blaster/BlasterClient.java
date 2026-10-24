package com.phiro.sabercraft.client.blaster;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.network.BlasterHeatPayload;
import com.phiro.sabercraft.network.UseAbilityPayload;

/**
 * The client side of the blasters (Phase 10): the bolt renderer, the heat display, and the
 * right-click. A click on a blaster is sent to the server as a normal item use, the server fires;
 * the client only makes sure the bow is not drawn, and remembers the time of the shot for the
 * recoil of the first-person model. On a server without SaberCraft a converted bow stays a bow.
 */
public final class BlasterClient {
	private static float heat;
	private static boolean overheated;
	private static long lastShotMillis = Long.MIN_VALUE / 2;
	private static InteractionHand lastShotHand = InteractionHand.MAIN_HAND;
	/** The hand of a shot whose arm swing is still to come in this tick. */
	private static @Nullable InteractionHand shotSwing;

	private BlasterClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(EntityTypes.SNOWBALL, BlasterBoltRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(BlasterHeatPayload.TYPE, (payload, context) -> {
			heat = payload.heat();
			overheated = payload.overheated();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			heat = 0.0F;
			overheated = false;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);

			if (!level.isClientSide() || BlasterType.firing(stack) == null || !serverHasBlasters()) {
				return InteractionResult.PASS;
			}

			if (overheated || player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.FAIL;
			}

			lastShotMillis = Util.getMillis();
			lastShotHand = hand;
			shotSwing = hand;
			// Only SUCCESS is sent to the server as an item use. Its arm swing is left out for
			// blasters (LocalPlayerMixin), and the bow is not drawn.
			return InteractionResult.SUCCESS;
		});
		// The swing follows the shot in the same tick; never keep the mark longer.
		ClientTickEvents.END_CLIENT_TICK.register(client -> shotSwing = null);
		HudElementRegistry.addLast(SaberCraft.id("blaster_heat"), new BlasterHud());
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
}
