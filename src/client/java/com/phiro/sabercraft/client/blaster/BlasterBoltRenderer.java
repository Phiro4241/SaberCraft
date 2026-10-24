package com.phiro.sabercraft.client.blaster;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.render.BladeMesh;
import com.phiro.sabercraft.client.render.SaberRenderTypes;
import com.phiro.sabercraft.client.render.ShaderCompat;

/**
 * Draws blaster bolts (Phase 10). On the network a bolt is a snowball whose item is marked as a
 * bolt (see {@link BlasterBolt}); this renderer replaces the snowball renderer and draws those as
 * a short glowing streak along their flight, built like a saber blade (white core, colored glow)
 * with the same render types, so bolts stay bright and colored with shader packs too. Real
 * snowballs are drawn as before.
 */
public class BlasterBoltRenderer extends ThrownItemRenderer<Snowball> {
	/** Blade units to blocks: a bolt is thicker than a held blade, so it reads from afar. */
	private static final float SCALE = 2.2F;

	public static final class State extends ThrownItemRenderState {
		BlasterType type;
		final Vector3f direction = new Vector3f(0.0F, 0.0F, 1.0F);
		float age;
	}

	public BlasterBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ThrownItemRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Snowball entity, ThrownItemRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		State bolt = (State) state;
		bolt.type = typeOf(entity);

		if (bolt.type != null) {
			Vec3 motion = entity.getDeltaMovement();

			if (motion.lengthSqr() > 1.0E-6) {
				bolt.direction.set((float) motion.x, (float) motion.y, (float) motion.z).normalize();
			} else {
				float yaw = entity.getYRot(partialTicks) * Mth.DEG_TO_RAD;
				float pitch = entity.getXRot(partialTicks) * Mth.DEG_TO_RAD;
				bolt.direction.set(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch)).normalize();
			}

			bolt.age = entity.tickCount + partialTicks;
		}
	}

	@Override
	public void submit(ThrownItemRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		State bolt = (State) state;

		if (bolt.type == null) {
			super.submit(state, poseStack, collector, camera);
			return;
		}

		if (ShaderCompat.renderingShadowPass()) {
			return;
		}

		BladeStyle style = BladeStyles.get(bolt.type.color());
		// The streak never reaches back past the muzzle: it grows with the way flown so far.
		float length = Math.max(0.1F, Math.min(bolt.type.boltLength(), bolt.age * bolt.type.speed())) / SCALE;
		boolean shader = SaberRenderTypes.beams();
		poseStack.pushPose();
		poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, bolt.direction.x, bolt.direction.y, bolt.direction.z));
		poseStack.scale(SCALE, SCALE, SCALE);
		// The streak trails behind the bolt's position.
		poseStack.translate(0.0F, -length, 0.0F);
		collector.order(0).submitCustomGeometry(poseStack, SaberRenderTypes.haze(false), (pose, buffer) -> BladeMesh.haze(pose, buffer, style, length, 1.2F, shader));
		collector.order(1).submitCustomGeometry(poseStack, SaberRenderTypes.core(false), (pose, buffer) -> BladeMesh.core(pose, buffer, style, length, 1.2F, shader));
		collector.order(2).submitCustomGeometry(poseStack, SaberRenderTypes.bloom(false), (pose, buffer) -> BladeMesh.bloom(pose, buffer, style, length, 1.2F));
		poseStack.popPose();
	}

	/** The blaster type of a bolt, or null for a real snowball. */
	public static BlasterType typeOf(Snowball entity) {
		CustomModelData data = entity.getItem().get(DataComponents.CUSTOM_MODEL_DATA);

		if (data == null || !BlasterBolt.MARKER.equals(data.getString(0))) {
			return null;
		}

		String type = data.getString(1);
		return type == null ? BlasterType.E11 : BlasterType.byId(Identifier.tryParse(type)).orElse(BlasterType.E11);
	}
}
