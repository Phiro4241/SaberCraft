package com.phiro.sabercraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.client.combat.SaberCombat;

/** Third person: turns the saber in the hand for wrist movements and staff twirls. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(
			method = "submitArmWithItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void sabercraft$turnSaber(
			ArmedEntityRenderState state,
			ItemStackRenderState item,
			ItemStack itemStack,
			HumanoidArm arm,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		SaberCombat.ThirdPersonData data = ((FabricRenderState) state).getData(SaberCombat.THIRD_PERSON);

		if (data != null) {
			SaberCombat.applyItemTurn(poseStack, data, arm);
		}
	}
}
