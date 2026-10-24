package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.blaster.BlasterType;

/**
 * Phase 10: a blaster is never swung like a sword. A shot is sent to the server as an item use
 * that vanilla would answer with an arm swing; the blaster has its own recoil instead, and blasters
 * stay aimed. Neither this player nor anyone else sees the swing.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
	private void sabercraft$noBlasterSwing(InteractionHand hand, CallbackInfo info) {
		if (BlasterType.of(((LocalPlayer) (Object) this).getItemInHand(hand)) != null) {
			info.cancel();
		}
	}
}
