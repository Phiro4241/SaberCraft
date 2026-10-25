package com.phiro.sabercraft.client.droid;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.animal.pig.Pig;

import com.phiro.sabercraft.droid.DroidMarker;
import com.phiro.sabercraft.droid.DroidType;

/**
 * Draws the droids (Phase 11). On the network a droid is a pig marked as a droid (see
 * {@link DroidMarker}); this renderer replaces the pig renderer and draws those as their droid
 * model, animated by {@link DroidPose}. Real pigs are drawn as before. Name tags (the droid's
 * name when looked at) work as for every named mob.
 */
public class DroidRenderer extends PigRenderer {
	public static final class State extends PigRenderState {
		@Nullable DroidType droid;
		@Nullable List<ItemStackRenderState> parts;
		DroidPose pose = DroidPose.idle(0.0F);
	}

	public DroidRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public PigRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Pig entity, PigRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		State droid = (State) state;
		droid.droid = DroidMarker.of(entity);

		if (droid.droid == null) {
			droid.parts = null;
			return;
		}

		// A pig that just became a droid on this client: take the droid's size for clicking.
		if (Math.abs(entity.getBbWidth() - droid.droid.width()) > 0.01F || Math.abs(entity.getBbHeight() - droid.droid.height()) > 0.01F) {
			entity.refreshDimensions();
		}

		droid.parts = DroidModels.resolve(droid.droid, droid.parts, entity.level(), entity, entity.getId() * 31);
		DroidClient.Event event = DroidClient.event(entity.getId());
		droid.pose = new DroidPose(state.walkAnimationPos, Math.min(1.0F, state.walkAnimationSpeed), state.yRot, state.xRot,
				entity.tickCount + partialTicks, event != null ? event.event() : 0, event != null ? event.progress() : 1.0F);
		droid.shadowRadius = droid.droid.width() * 0.45F;
	}

	@Override
	public void submit(PigRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		State droid = (State) state;

		if (droid.droid == null || droid.parts == null) {
			super.submit(state, poseStack, collector, camera);
			return;
		}

		if (!state.isInvisible) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.bodyRot));
			DroidModels.submit(poseStack, collector, droid.droid, droid.pose, droid.parts, state.lightCoords, state.outlineColor);
			poseStack.popPose();
		}

		this.submitNameDisplay(state, poseStack, collector, camera);
	}
}
