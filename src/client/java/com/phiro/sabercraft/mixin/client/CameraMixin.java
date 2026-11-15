package com.phiro.sabercraft.mixin.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.ship.CockpitView;
import com.phiro.sabercraft.client.ship.ShipClient;

/**
 * Phase 13: the camera behind a starship may be further away than the game's camera distance
 * attribute allows (at most 32 blocks). The Star Destroyer is about 80 blocks long, so the camera
 * of its pilot is moved back to see the whole ship. Blocks still pull the camera in as usual.
 *
 * <p>Phase 14: in the first-person view a pilot's camera sits in the cockpit of the ship, pitched
 * and banked with it (see {@link CockpitView}). Only the camera of this client changes.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private @org.jspecify.annotations.Nullable Entity entity;
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float sabercraft$shipCamera(float distance) {
		return ShipClient.cameraDistance(this.entity, distance);
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void sabercraft$cockpit(float partialTicks, CallbackInfo ci) {
		if (this.detached) {
			return;
		}

		CockpitView.Pose pose = CockpitView.pose(this.entity, partialTicks);

		if (pose == null) {
			return;
		}

		this.setRotation(pose.yaw(), pose.pitch());

		if (pose.roll() != 0.0F) {
			this.rotation.rotateZ((float) Math.toRadians(-pose.roll()));
			this.forwards.set(0.0F, 0.0F, -1.0F).rotate(this.rotation);
			this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
			this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
		}

		this.setPosition(pose.position());
	}
}
