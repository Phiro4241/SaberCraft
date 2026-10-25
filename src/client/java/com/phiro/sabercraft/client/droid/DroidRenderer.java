package com.phiro.sabercraft.client.droid;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.animal.pig.Pig;

import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.client.ship.ShipModels;
import com.phiro.sabercraft.droid.DroidMarker;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.ship.ShipMarker;
import com.phiro.sabercraft.ship.ShipType;

/**
 * Draws the droids (Phase 11). On the network a droid is a pig marked as a droid (see
 * {@link DroidMarker}); this renderer replaces the pig renderer and draws those as their droid
 * model, animated by {@link DroidPose}. Real pigs are drawn as before. Name tags (the droid's
 * name when looked at) work as for every named mob.
 *
 * <p>Starships (Phase 11, see {@link ShipMarker}) are drawn here too: turned, pitched and banked
 * like the ship flies, the S-foils opening off the ground (see {@link ShipClient}).
 */
public class DroidRenderer extends PigRenderer {
	public static final class State extends PigRenderState {
		@Nullable DroidType droid;
		@Nullable List<ItemStackRenderState> parts;
		DroidPose pose = DroidPose.idle(0.0F);
		// Phase 11: a starship.
		@Nullable ShipType ship;
		@Nullable List<ItemStackRenderState> shipParts;
		float shipYaw;
		float shipPitch;
		float shipRoll;
		float foils;
		boolean piloted;
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
		droid.ship = ShipMarker.of(entity);

		if (droid.ship != null) {
			droid.parts = null;
			extractShip(entity, droid, partialTicks);
			return;
		}

		droid.shipParts = null;

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

	private static void extractShip(Pig entity, State state, float partialTicks) {
		ShipType ship = state.ship;

		if (Math.abs(entity.getBbWidth() - ship.width()) > 0.01F || Math.abs(entity.getBbHeight() - ship.height()) > 0.01F) {
			entity.refreshDimensions();
		}

		state.shipParts = ShipModels.resolve(ship, state.shipParts, entity.level(), entity, entity.getId() * 31);
		ShipClient.Visual visual = ShipClient.visual(entity);
		state.shipYaw = entity.getYRot(partialTicks);
		state.shipPitch = entity.getXRot(partialTicks);
		state.shipRoll = visual.roll(partialTicks);
		state.foils = visual.foils(partialTicks);
		state.piloted = entity.hasPassenger(Minecraft.getInstance().getCameraEntity());
		state.shadowRadius = ship.width() * 0.6F;
	}

	@Override
	public void submit(PigRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		State droid = (State) state;

		if (droid.ship != null && droid.shipParts != null) {
			if (!state.isInvisible) {
				// Turned like the ship, then pitched and banked around its middle.
				float middle = ShipModels.parts(droid.ship).isEmpty() ? 1.0F : ShipModels.parts(droid.ship).getFirst().y();
				poseStack.pushPose();
				poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - droid.shipYaw));
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.mulPose(Axis.XP.rotationDegrees(-droid.shipPitch));
				poseStack.mulPose(Axis.ZP.rotationDegrees(-droid.shipRoll));
				poseStack.translate(0.0F, -middle, 0.0F);
				ShipModels.submit(poseStack, collector, droid.ship, droid.foils, droid.shipParts, state.lightCoords, state.outlineColor);
				poseStack.popPose();
			}

			// The pilot does not need their own ship's name over the cockpit.
			if (!droid.piloted) {
				this.submitNameDisplay(state, poseStack, collector, camera);
			}

			return;
		}

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
