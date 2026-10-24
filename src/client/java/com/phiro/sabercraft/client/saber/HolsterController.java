package com.phiro.sabercraft.client.saber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/**
 * Lets the blade retract fully before the own player puts the saber away.
 *
 * <p>Without this, scrolling to another hotbar slot would swap the item at once and the blade
 * would simply vanish together with the hilt. Instead, when the player leaves a slot with a lit
 * saber, the slot change is held back for the few ticks the retract animation takes. The hilt
 * stays in the hand, the blade goes back in, then the new slot is selected. Scrolling further in
 * the meantime only changes the slot that will be selected at the end.
 *
 * <p>The change is held back before Minecraft tells the server about it, so the server and all
 * other players simply see the saber a little longer and then the new item. Other players with
 * SaberCraft therefore also see the complete retract animation, without any extra packets.
 *
 * <p>Only hotbar changes of the own player are delayed. Dying, dropping the saber, swapping hands
 * or moving it in the inventory still take effect immediately.
 */
public final class HolsterController {
	public static final HolsterController INSTANCE = new HolsterController();

	/** Safety margin in ticks on top of the retract time. */
	private static final int TIMEOUT_MARGIN = 5;

	private boolean enabled = true;
	private int lastSlot = -1;
	private int holsterSlot = -1;
	private int targetSlot = -1;
	private int holsterTicks;

	private HolsterController() {
	}

	/** Runs at the start of every client tick, before the selected slot is sent to the server. */
	public void tick(Minecraft client) {
		LocalPlayer player = client.player;

		if (player == null) {
			this.reset();
			return;
		}

		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();

		if (this.holsterSlot >= 0) {
			this.continueHolster(player, inventory, selected);
		} else if (this.enabled && this.lastSlot >= 0 && selected != this.lastSlot && this.shouldHolster(player, inventory, selected)) {
			this.holsterSlot = this.lastSlot;
			this.targetSlot = selected;
			this.holsterTicks = 0;
			inventory.setSelectedSlot(this.holsterSlot);
		}

		this.lastSlot = inventory.getSelectedSlot();
	}

	private void continueHolster(LocalPlayer player, Inventory inventory, int selected) {
		if (selected != this.holsterSlot) {
			// The player scrolled on while the blade retracts: remember the newest choice.
			this.targetSlot = selected;
			inventory.setSelectedSlot(this.holsterSlot);
		}

		this.holsterTicks++;
		SaberHandState state = SaberTracker.INSTANCE.state(player, InteractionHand.MAIN_HAND);
		int timeout = state != null ? state.profile().animation().retractTicks() + TIMEOUT_MARGIN : 0;
		boolean done = state == null
				|| state.isRetracted()
				|| this.holsterTicks > timeout
				|| !player.isAlive()
				|| !SaberProfiles.isSaber(inventory.getItem(this.holsterSlot));

		if (done) {
			inventory.setSelectedSlot(this.targetSlot);
			this.holsterSlot = -1;
			this.targetSlot = -1;
		}
	}

	private boolean shouldHolster(LocalPlayer player, Inventory inventory, int selected) {
		if (!player.isAlive() || player.isSpectator() || this.lastSlot >= Inventory.getSelectionSize()) {
			return false;
		}

		if (!SaberProfiles.isSaber(inventory.getItem(this.lastSlot)) || SaberProfiles.isSaber(inventory.getItem(selected))) {
			// Switching between two sabers keeps the blade lit.
			return false;
		}

		SaberHandState state = SaberTracker.INSTANCE.state(player, InteractionHand.MAIN_HAND);
		return state != null && state.isActive() && state.extension(1.0F) > 0.05F;
	}

	/** True while the own player's main hand saber is being put away. */
	public boolean isHolstering(LivingEntity entity, InteractionHand hand) {
		return this.holsterSlot >= 0 && hand == InteractionHand.MAIN_HAND && entity == Minecraft.getInstance().player;
	}

	/** The slot that will be selected once the blade is in, or -1. For tests. */
	public int pendingSlot() {
		return this.targetSlot;
	}

	/** Turns the delayed slot change on or off. Meant for the config screen of a later phase. */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		Minecraft client = Minecraft.getInstance();

		if (!enabled && this.holsterSlot >= 0 && client.player != null) {
			client.player.getInventory().setSelectedSlot(this.targetSlot);
		}

		if (!enabled) {
			this.reset();
		}
	}

	public void reset() {
		this.lastSlot = -1;
		this.holsterSlot = -1;
		this.targetSlot = -1;
	}
}
