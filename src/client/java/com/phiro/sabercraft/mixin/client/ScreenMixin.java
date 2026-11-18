package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.phiro.sabercraft.client.menu.TitleStyle;

/**
 * Phase 24: SaberCraft's space picture in place of the panorama: behind the title screen and
 * the other menus before a world is loaded (those blur it as they blur the panorama).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void sabercraft$background(GuiGraphicsExtractor graphics, float a, CallbackInfo info) {
		if (TitleStyle.enabled()) {
			TitleStyle.background(graphics, this.width, this.height);
			info.cancel();
		}
	}
}
