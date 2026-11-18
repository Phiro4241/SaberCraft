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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
 *
 * <p>Phase 20: during a story film the camera follows the film's shots instead.
 *
 * <p>Phase 23: in a story fight the view closes in a little for a clash and while the blades are
 * locked (scaled by the game's FOV effects setting).
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

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void sabercraft$storyZoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float scale = com.phiro.sabercraft.client.story.StoryClient.fovScale(partialTicks);

		if (scale != 1.0F) {
			float effect = net.minecraft.client.Minecraft.getInstance().options.fovEffectScale().get().floatValue();
			cir.setReturnValue(cir.getReturnValueF() * (1.0F + (scale - 1.0F) * effect));
		}
	}

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float sabercraft$shipCamera(float distance) {
		return ShipClient.cameraDistance(this.entity, distance);
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void sabercraft$cockpit(float partialTicks, CallbackInfo ci) {
		// Phase 20: a story film moves the camera along its shots.
		com.phiro.sabercraft.client.interior.ArrivalFilm.Shot film = com.phiro.sabercraft.client.story.StoryCinema.camera(partialTicks);

		if (film != null) {
			this.setRotation(film.yaw(), film.pitch());
			this.setPosition(film.position());

			// Phase 21: a film where the story is shows the player from outside (Phase 25: not
			// while the camera is right at their eyes, gliding out of them or back in: one would
			// see the inside of their head).
			if (com.phiro.sabercraft.client.story.StoryCinema.inWorld()
					&& !com.phiro.sabercraft.client.story.StoryCinema.atEyes(film.position(), this.entity, partialTicks)) {
				this.detached = true;
			}

			return;
		}

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
