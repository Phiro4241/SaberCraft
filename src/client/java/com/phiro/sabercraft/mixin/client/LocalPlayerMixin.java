package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.client.blaster.BlasterClient;

/**
 * Phase 10: a shot is sent to the server as an item use that vanilla would answer with an arm
 * swing. A blaster has its own recoil instead and stays aimed; Ezra's hilt plays its own shot.
 * Only the swing of that use is left out, so neither this player nor anyone else sees it; attacks
 * still swing.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
	private void sabercraft$noBlasterSwing(InteractionHand hand, CallbackInfo info) {
		if (BlasterClient.takeShotSwing(hand)) {
			info.cancel();
		}
	}
}
