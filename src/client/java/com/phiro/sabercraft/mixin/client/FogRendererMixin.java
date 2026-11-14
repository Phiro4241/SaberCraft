package com.phiro.sabercraft.mixin.client;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;

import com.phiro.sabercraft.client.interior.InteriorScene;
import com.phiro.sabercraft.client.sky.PlanetSky;

/**
 * Gives the fog in the open air the color of the chosen Star Wars planet (Phase 8), so the horizon
 * matches the sky. Water, lava, powder snow and the effects keep their vanilla fog.
 *
 * <p>Phase 15: in the starship interiors there is no fog at the render distance, so the whole
 * Star Destroyer can be seen from its bridge (the void has no chunks to hide anyway).
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void sabercraft$planetFog(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f dest,
			CallbackInfo info, @Local FogType fogType) {
		if (fogType == FogType.ATMOSPHERIC) {
			PlanetSky.fogColor(dest);
		}
	}

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void sabercraft$clearSpace(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
			CallbackInfoReturnable<FogData> info) {
		FogData fog = info.getReturnValue();

		if (InteriorScene.clearFog(level) && fog.environmentalEnd >= 512.0F) {
			fog.renderDistanceStart = InteriorScene.FOG_START;
			fog.renderDistanceEnd = InteriorScene.FOG_END;
			fog.environmentalStart = Math.max(fog.environmentalStart, InteriorScene.FOG_START);
			fog.environmentalEnd = Math.max(fog.environmentalEnd, InteriorScene.FOG_END);
		}
	}
}
