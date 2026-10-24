package com.phiro.sabercraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.phiro.sabercraft.client.combat.CombatConfig;
import com.phiro.sabercraft.client.combat.SaberCombat;

/**
 * First person: lets the camera follow rolls and landings a little, by dipping the eyes and
 * tilting the view. Added where vanilla tilts the view when the player is hurt, which applies to
 * both the world and the held items, so the saber moves with the view.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	private static final float[] SABERCRAFT$CAMERA = new float[3];

	@Inject(method = "bobHurt", at = @At("RETURN"))
	private void sabercraft$cameraMotion(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		float strength = CombatConfig.get().cameraMotion;

		if (client.player == null || strength <= 0.0F || !client.options.getCameraType().isFirstPerson()
				|| client.getCameraEntity() != client.player) {
			return;
		}

		float[] camera = SABERCRAFT$CAMERA;

		if (SaberCombat.camera(client.player, client.getDeltaTracker().getGameTimeDeltaPartialTick(false), camera)) {
			poseStack.translate(0.0F, camera[0] * strength, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(camera[2] * strength));
			poseStack.mulPose(Axis.XP.rotationDegrees(camera[1] * strength));
		}
	}
}
