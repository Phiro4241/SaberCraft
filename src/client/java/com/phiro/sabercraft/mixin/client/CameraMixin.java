package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

import com.phiro.sabercraft.client.ship.ShipClient;

/**
 * Phase 13: the camera behind a starship may be further away than the game's camera distance
 * attribute allows (at most 32 blocks). The Star Destroyer is about 80 blocks long, so the camera
 * of its pilot is moved back to see the whole ship. Blocks still pull the camera in as usual.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private @org.jspecify.annotations.Nullable Entity entity;

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float sabercraft$shipCamera(float distance) {
		return ShipClient.cameraDistance(this.entity, distance);
	}
}
