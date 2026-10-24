package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.client.character.OutfitClient;

/**
 * Small characters (Yoda, Phase 9) are drawn smaller; the hitbox, the camera and the name tag stay
 * where they are. Since Phase 10 the skin itself is replaced (see {@link AbstractClientPlayerMixin}),
 * so nothing about the skin layers is changed here any more. Blasters are aimed with both hands.
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

	/** Phase 10: blasters are held with both hands and aimed, like a loaded crossbow. */
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("RETURN"), cancellable = true)
	private static void sabercraft$aimBlaster(Avatar avatar, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> info) {
		BlasterType type = BlasterType.of(stack);

		if (type != null && !avatar.swinging) {
			info.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
