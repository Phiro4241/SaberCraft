package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
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
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void sabercraft$fireLasers(CallbackInfoReturnable<Boolean> info) {
		if (ShipClient.trigger((Minecraft) (Object) this)) {
			info.setReturnValue(false);
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
