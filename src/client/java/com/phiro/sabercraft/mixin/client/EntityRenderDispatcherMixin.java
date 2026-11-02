package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import com.phiro.sabercraft.client.ship.ShipClient;

/**
 * Phase 13: a player who sits in a starship is not drawn at all, neither the own player in third
 * person nor other players, so no oversized figure sticks out of the cockpit. Only drawing is
 * skipped: the player is still there for the game, so steering, hits, the camera and everything
 * else work as before. The check is made fresh every frame from the vehicle the game knows, so
 * the player shows again the moment they climb out, die, the ship is destroyed or they leave the
 * world; nothing has to be switched back.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void sabercraft$hidePilots(E entity, Frustum culler, double camX, double camY, double camZ,
			CallbackInfoReturnable<Boolean> info) {
		if (ShipClient.hidesPilot(entity)) {
			info.setReturnValue(false);
		}
	}
}
