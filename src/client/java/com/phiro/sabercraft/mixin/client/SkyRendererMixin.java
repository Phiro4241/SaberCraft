package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SkyRenderer;

import com.phiro.sabercraft.client.sky.SpaceSky;

/**
 * Draws the optional space sky ({@link SpaceSky}) in the vanilla sky pass: in place of the
 * vanilla stars (turned the same way, the dark parts let the moon shine through), and over the
 * End sky. With the option off
 * the vanilla stars are drawn as always.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Shadow
	@Final
	private RenderTarget renderTarget;

	@WrapOperation(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SkyRenderer;renderStars(FLcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private void sabercraft$drawSpaceSky(SkyRenderer self, float starBrightness, PoseStack poseStack, Operation<Void> original,
			@Local(argsOnly = true, ordinal = 3) float rainBrightness) {
		if (SpaceSky.active()) {
			SpaceSky.renderNight(this.renderTarget, poseStack.last().pose(), rainBrightness, starBrightness);
		} else {
			original.call(self, starBrightness, poseStack);
		}
	}

	@Inject(method = "renderEndSky", at = @At("TAIL"))
	private void sabercraft$drawSpaceSkyInEnd(CallbackInfo info) {
		if (SpaceSky.active()) {
			SpaceSky.renderEnd(this.renderTarget);
		}
	}
}
