package com.phiro.sabercraft.client.select;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.network.SelectSaberVariantPayload;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Changes the hilt and color of a held saber.
 *
 * <p>Only the server may change items, so the client asks it:
 *
 * <ol>
 *   <li>If the server has SaberCraft (always true in singleplayer), it gets a
 *       {@link SelectSaberVariantPayload} and stores the variant after checking it.</li>
 *   <li>Otherwise, in creative mode, the client sends the changed item with the vanilla creative
 *       inventory packet. That works on any server, just like picking an item in the creative
 *       inventory.</li>
 *   <li>Otherwise (survival on a server without SaberCraft) the variant cannot be changed.</li>
 * </ol>
 *
 * <p>In every case the new variant reaches all players through the normal item sync.
 */
public final class SaberSelection {
	/** Hotbar slot 0 in the player's inventory menu. */
	private static final int HOTBAR_START = InventoryMenu.USE_ROW_SLOT_START;

	private SaberSelection() {
	}

	public enum Result {
		/** Sent to a server with SaberCraft. */
		SENT,
		/** Applied through the creative inventory. */
		CREATIVE,
		/** Survival on a server without SaberCraft. */
		UNSUPPORTED,
		/** Nothing to change: no saber in that hand or an unknown variant. */
		NO_SABER
	}

	/** The hand that holds a saber, main hand first, or {@code null}. */
	public static @Nullable InteractionHand saberHand(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (SaberVariant.canHold(player.getItemInHand(hand))) {
				return hand;
			}
		}

		return null;
	}

	/** True if the variant of a held saber can be changed right now. */
	public static boolean canChange(Minecraft client) {
		return client.player != null
				&& (ClientPlayNetworking.canSend(SelectSaberVariantPayload.TYPE) || client.player.hasInfiniteMaterials());
	}

	public static Result apply(Minecraft client, InteractionHand hand, SaberVariant variant) {
		LocalPlayer player = client.player;

		if (player == null || !variant.isKnown() || !SaberVariant.canHold(player.getItemInHand(hand))) {
			return Result.NO_SABER;
		}

		if (ClientPlayNetworking.canSend(SelectSaberVariantPayload.TYPE)) {
			ClientPlayNetworking.send(new SelectSaberVariantPayload(hand, variant));
			return Result.SENT;
		}

		if (player.hasInfiniteMaterials() && client.gameMode != null) {
			applyCreative(client, hand, variant);
			return Result.CREATIVE;
		}

		return Result.UNSUPPORTED;
	}

	/**
	 * The creative mode way: changes the item locally and sends it with the vanilla creative
	 * inventory packet. Public so the game test can check it on a server that has SaberCraft too.
	 */
	public static void applyCreative(Minecraft client, InteractionHand hand, SaberVariant variant) {
		LocalPlayer player = client.player;

		if (player == null || client.gameMode == null) {
			return;
		}

		ItemStack changed = player.getItemInHand(hand).copy();
		variant.applyTo(changed);
		player.setItemInHand(hand, changed);
		int slot = hand == InteractionHand.MAIN_HAND ? HOTBAR_START + player.getInventory().getSelectedSlot() : InventoryMenu.SHIELD_SLOT;
		client.gameMode.handleCreativeModeItemAdd(changed, slot);
	}
}
