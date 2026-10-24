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
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;

import com.phiro.sabercraft.client.sky.PlanetSky;
import com.phiro.sabercraft.client.sky.SpaceSky;

/**
 * Draws the optional space sky ({@link SpaceSky}) in the vanilla sky pass: in place of the
 * vanilla stars (turned the same way, the dark parts let the moon shine through), and over the
 * End sky. With the option off
 * the vanilla stars are drawn as always.
 *
 * <p>Since Phase 8 it also turns the Overworld sky into the chosen Star Wars planet
 * ({@link PlanetSky}): the sky and sunset colors, the planet's layer before the sun and moon,
 * and a second sun. Without a planet chosen these hooks change nothing.
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

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void sabercraft$planetColors(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo info) {
		state.skyColor = PlanetSky.skyColor(state.skyColor);
		state.sunriseAndSunsetColor = PlanetSky.sunsetColor(state.sunriseAndSunsetColor);
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
	private void sabercraft$drawPlanetSky(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
			float rainBrightness, float starBrightness, CallbackInfo info) {
		PlanetSky.render(this.renderTarget, poseStack.last().pose(), sunAngle, rainBrightness);
	}

	@WrapOperation(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSun(FLcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private void sabercraft$secondSun(SkyRenderer self, float rainBrightness, PoseStack poseStack, Operation<Void> original) {
		original.call(self, rainBrightness, poseStack);
		float offset = PlanetSky.secondSun();

		if (offset != 0.0F) {
			// The smaller companion sun, beside the first one and a little behind it on its path, so
			// both stand over the horizon together at sunset.
			poseStack.pushPose();
			poseStack.mulPose(Axis.ZP.rotationDegrees(offset));
			poseStack.mulPose(Axis.XP.rotationDegrees(offset * 0.35F));
			poseStack.scale(0.65F, 1.0F, 0.65F);
			original.call(self, rainBrightness, poseStack);
			poseStack.popPose();
		}
	}
}
