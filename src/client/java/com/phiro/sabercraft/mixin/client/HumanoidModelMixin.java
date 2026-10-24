package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import com.phiro.sabercraft.client.combat.SaberCombat;

/** Third person: the fighting pose replaces vanilla's attack animation of humanoid models. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
	private void sabercraft$saberPose(HumanoidRenderState state, CallbackInfo ci) {
		SaberCombat.ThirdPersonData data = ((FabricRenderState) state).getData(SaberCombat.THIRD_PERSON);

		// A vanilla swing while the saber only rests (for example during the ignition) stays vanilla.
		if (data == null || !data.moving() && state.attackTime > 0.0F) {
			return;
		}

		SaberCombat.applyThirdPerson((HumanoidModel<?>) (Object) this, state, data);
		ci.cancel();
	}
}
