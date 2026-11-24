package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

import com.phiro.sabercraft.client.story.StoryBrightness;

/**
 * Phase 31: the story's brightness at "Maximal" lifts the world's light in the story's ships
 * (see {@link StoryBrightness}). Runs only when the game works the light out anew (once a tick),
 * so a change of the setting, or leaving the story, shows within a tick.
 */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void sabercraft$storyBrightness(LightmapRenderState state, float partialTicks, CallbackInfo info) {
		StoryBrightness.lightmap(state);
	}
}
