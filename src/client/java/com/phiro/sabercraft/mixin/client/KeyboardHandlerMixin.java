package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import com.phiro.sabercraft.client.story.StoryKeys;

/**
 * Phase 31: the story's key gate (see {@link StoryKeys}): a key used in a film, a dialog box or
 * the screw close-up does nothing else while it stays held (no repeat, no jump, no pause menu).
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void sabercraft$storyKeys(long window, int action, KeyEvent event, CallbackInfo info) {
		if (StoryKeys.onKey(this.minecraft, window, action, event)) {
			info.cancel();
		}
	}
}
