package com.phiro.sabercraft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Server side of the variant selection, and registration of all SaberCraft packets.
 *
 * <p>Only needed to change a saber in survival on a multiplayer server. Showing variants needs no
 * server support at all, and in singleplayer the built-in server always has the mod. The ability
 * packets are handled in {@link com.phiro.sabercraft.ability.SaberAbilities}.
 */
public final class SaberNetworking {
	private SaberNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(SelectSaberVariantPayload.TYPE, SelectSaberVariantPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectSaberVariantPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		PayloadTypeRegistry.serverboundPlay().register(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbilityEventPayload.TYPE, AbilityEventPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbilityCooldownPayload.TYPE, AbilityCooldownPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbilitySettingsPayload.TYPE, AbilitySettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StaminaPayload.TYPE, StaminaPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChooseOutfitPayload.TYPE, ChooseOutfitPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OutfitSyncPayload.TYPE, OutfitSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BlasterHeatPayload.TYPE, BlasterHeatPayload.CODEC);
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
