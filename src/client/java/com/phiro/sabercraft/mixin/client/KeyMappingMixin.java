package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyMapping;

import com.phiro.sabercraft.client.story.StoryKeys;

/**
 * Phase 31: when a screen closes, Minecraft reads anew which keys are held ({@code setAll}); a
 * key the story's screens used and that is still held stays up (no jump after the last line of a
 * dialog). See {@link StoryKeys}.
 */
@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {
	@Inject(method = "setAll", at = @At("TAIL"))
	private static void sabercraft$storyKeysStayUp(CallbackInfo info) {
		StoryKeys.afterSetAll();
	}
}
