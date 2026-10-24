package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import com.phiro.sabercraft.client.combat.SaberCombat;

/**
 * Stores the fighting pose of every armed entity on its render state, once per frame, and turns
 * the whole body for spinning attacks.
 */
@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {
	@Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
	private static void sabercraft$extractCombat(
			LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver itemModelResolver, float partialTicks, CallbackInfo ci
	) {
		SaberCombat.ThirdPersonData data = SaberCombat.thirdPerson(entity, partialTicks);
		((FabricRenderState) state).setData(SaberCombat.THIRD_PERSON, data);

		if (data != null && data.pose().spin != 0.0F) {
			float mirror = data.saberArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
			state.bodyRot += data.pose().spin * mirror * data.weight();
		}
	}
}
