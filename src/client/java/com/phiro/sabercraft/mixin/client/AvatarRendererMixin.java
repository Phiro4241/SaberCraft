package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.character.OutfitLayer;

/**
 * Under an outfit the outer layers of the skin (jacket, sleeves, trouser legs, and the hat under
 * a hood or helmet) are left out, so they do not poke through the outfit. Without an outfit
 * nothing changes. Since Phase 9 small characters (Yoda) are also drawn smaller; the hitbox, the
 * camera and the name tag stay where they are.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void sabercraft$hideSkinLayersUnderOutfit(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo info) {
		OutfitClient.Look look = OutfitClient.look(entity.getUUID());

		if (look != null) {
			state.showJacket = false;
			state.showLeftSleeve = false;
			state.showRightSleeve = false;
			state.showLeftPants = false;
			state.showRightPants = false;

			if (look.coversHead()) {
				state.showHat = false;
			}
		}
	}

	@Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
	private void sabercraft$characterSize(AvatarRenderState state, PoseStack poseStack, CallbackInfo info) {
		OutfitClient.Look look = OutfitLayer.lookOf(state);

		if (look != null && look.scale() != 1.0F) {
			poseStack.scale(look.scale(), look.scale(), look.scale());
		}
	}
}
