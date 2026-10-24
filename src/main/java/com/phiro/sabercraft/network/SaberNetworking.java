package com.phiro.sabercraft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Server side of the variant selection.
 *
 * <p>Only needed to change a saber in survival on a multiplayer server. Showing variants needs no
 * server support at all, and in singleplayer the built-in server always has the mod.
 */
public final class SaberNetworking {
	private SaberNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(SelectSaberVariantPayload.TYPE, SelectSaberVariantPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectSaberVariantPayload.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	/**
	 * Applies a selection if it is valid: the player is alive, holds a netherite sword in that hand
	 * and asks for a known hilt and color. Anything else is ignored. Runs on the server thread.
	 */
	static void handle(ServerPlayer player, SelectSaberVariantPayload payload) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}

		if (!payload.variant().isKnown()) {
			SaberCraft.LOGGER.warn("{} asked for an unknown saber variant {}", player.getPlainTextName(), payload.variant());
			return;
		}

		ItemStack stack = player.getItemInHand(payload.hand());

		if (!SaberVariant.canHold(stack)) {
			return;
		}

		payload.variant().applyTo(stack);
	}
}
