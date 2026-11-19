package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

import com.phiro.sabercraft.client.ship.ShipClient;

/**
 * Phase 11: in the cockpit of a starship the attack key fires the laser cannons. It neither hits
 * the mob in the crosshair nor digs the block there, while clicked or held.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Shadow
	private int rightClickDelay;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void sabercraft$fireLasers(CallbackInfoReturnable<Boolean> info) {
		if (ShipClient.trigger((Minecraft) (Object) this)) {
			info.setReturnValue(false);
			return;
		}

		// Phase 19: a stroke in the story's fight (the swing itself goes on).
		com.phiro.sabercraft.client.story.StoryInput.attack((Minecraft) (Object) this);
	}

	/** Phase 18: the use key on someone of a ship's crew speaks to them. */
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void sabercraft$talkToCrew(CallbackInfo info) {
		// Phase 19: in the story the use key uses its things and speaks to its people first.
		// Phase 20: on an open hatch it shuts the hatch.
		Minecraft client = (Minecraft) (Object) this;

		// Phase 26: the use key still held after shutting a hatch does nothing more on that hatch
		// (it used to open it again at once: the held key repeats, and the shut hatch is a block one
		// clicks on).
		if (com.phiro.sabercraft.client.interior.InteriorClient.heldAfterHatch(client)
				|| com.phiro.sabercraft.client.story.StoryInput.use(client)
				|| com.phiro.sabercraft.client.interior.InteriorClient.hatch(client)
				|| com.phiro.sabercraft.client.interior.InteriorClient.talk(client)) {
			// Phase 26: as vanilla does for every use: the key, held, repeats only every 4 ticks
			// (cancelled here before vanilla set it, it repeated every tick).
			this.rightClickDelay = 4;
			info.cancel();
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void sabercraft$keepFiring(boolean down, CallbackInfo info) {
		Minecraft client = (Minecraft) (Object) this;

		if (ShipClient.piloting(client) != null) {
			if (down) {
				ShipClient.trigger(client);
			}

			info.cancel();
		}
	}
}
