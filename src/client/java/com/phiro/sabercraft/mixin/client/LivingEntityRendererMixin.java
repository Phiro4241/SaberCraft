package com.phiro.sabercraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import com.phiro.sabercraft.client.combat.SaberCombat;

/**
 * Third person: moves the whole figure for rolls, flips, lunges and landings, right after vanilla
 * turned it to face its walking direction.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void sabercraft$moveWholeBody(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		SaberCombat.ThirdPersonData data = ((FabricRenderState) state).getData(SaberCombat.THIRD_PERSON);

		if (data != null && state.deathTime <= 0.0F) {
			SaberCombat.applyRoot(poseStack, data, entityScale);
		}
	}
}
