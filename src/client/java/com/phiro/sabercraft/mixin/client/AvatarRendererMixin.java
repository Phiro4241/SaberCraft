package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import com.phiro.sabercraft.client.character.OutfitClient;

/**
 * Small characters (Yoda, Phase 9) are drawn smaller; the hitbox, the camera and the name tag stay
 * where they are. Since Phase 10 the skin itself is replaced (see {@link AbstractClientPlayerMixin}),
 * so nothing about the skin layers is changed here any more.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
	private void sabercraft$characterSize(AvatarRenderState state, PoseStack poseStack, CallbackInfo info) {
		OutfitClient.Look look = OutfitClient.lookOf(state);

		if (look != null && look.scale() != 1.0F) {
			poseStack.scale(look.scale(), look.scale(), look.scale());
		}
	}
}
