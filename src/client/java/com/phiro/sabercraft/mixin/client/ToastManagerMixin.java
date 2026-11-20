package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;

import com.phiro.sabercraft.client.story.StoryClient;

/**
 * Phase 28: Minecraft's toasts (achievements, recipes, tutorials) come in at the top right, where
 * the story's mission panel stands. While the panel shows, they come in just below it instead of
 * over it. Out of the story nothing changes.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void sabercraft$belowStoryPanel(GuiGraphicsExtractor graphics, CallbackInfo info) {
		graphics.pose().pushMatrix();
		int below = StoryClient.topRightReserved();

		if (below > 0) {
			graphics.pose().translate(0.0F, below);
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void sabercraft$restore(GuiGraphicsExtractor graphics, CallbackInfo info) {
		graphics.pose().popMatrix();
	}
}
