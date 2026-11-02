package com.phiro.sabercraft.client.ship;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.phiro.sabercraft.client.droid.DroidModels;
import com.phiro.sabercraft.ship.ShipType;

/**
 * The starship models (Phase 11): like the droids (see {@link DroidModels}), every part is an item
 * model of its own (see {@link ShipParts}), drawn at its pivot, twice as large as it is built. The
 * X-Wing's four wings turn around the long axis, so the S-foils open into the X in flight.
 */
public final class ShipModels {
	/** How far each S-foil opens, in degrees. */
	public static final float FOIL_ANGLE = 13.0F;

	private ShipModels() {
	}

	public static List<ShipParts.Part> parts(ShipType ship) {
		return ShipParts.PARTS.getOrDefault(ship.id(), List.of());
	}

	/** The icon of a ship: the whole ship in one model, or {@code null} for a planned one. */
	public static @Nullable ItemStack icon(ShipType ship) {
		return ShipParts.PARTS.containsKey(ship.id()) ? DroidModels.stack("sabercraft:ship/" + ship.id() + "_icon") : null;
	}

	/** Resolves the part models of a ship into {@code into} (reused if it has the right size). */
	public static List<ItemStackRenderState> resolve(ShipType ship, @Nullable List<ItemStackRenderState> into, @Nullable Level level,
			@Nullable Entity owner, int seed) {
		List<ShipParts.Part> parts = parts(ship);
		List<ItemStackRenderState> states = into != null && into.size() == parts.size() ? into : new ArrayList<>(parts.size());
		ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();

		for (int i = 0; i < parts.size(); i++) {
			if (states.size() <= i) {
				states.add(new ItemStackRenderState());
			}

			resolver.updateForTopItem(states.get(i), DroidModels.stack(parts.get(i).model()), ItemDisplayContext.NONE, level, null, seed + i);
		}

		return states;
	}

	/**
	 * How the moving parts of a ship stand (Phase 13).
	 *
	 * @param foils how far the X-Wing's S-foils are open, 0 closed to 1 open
	 * @param dish  the Falcon's radar dish, degrees around its post
	 * @param legs  the Falcon's landing legs, 0 drawn in to 1 out
	 */
	public record Pose(float foils, float dish, float legs) {
		public static final Pose LANDED = new Pose(0.0F, 30.0F, 1.0F);
		public static final Pose FLYING = new Pose(1.0F, 30.0F, 0.0F);
	}

	/** How far the Falcon's legs draw up into the hull, blocks. */
	private static final float LEG_TRAVEL = 0.9F;

	/** Draws a ship standing at the origin, its nose to -z, in blocks. */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, ShipType ship, Pose pose, List<ItemStackRenderState> states,
			int light, int outline) {
		List<ShipParts.Part> parts = parts(ship);

		for (int i = 0; i < parts.size() && i < states.size(); i++) {
			ShipParts.Part part = parts.get(i);
			poseStack.pushPose();
			poseStack.translate(part.x(), part.y(), part.z());
			float angle = pose.foils() * FOIL_ANGLE;

			// Upper right and lower left turn one way, the others the other way: an X.
			switch (part.name()) {
				case "wing_ur", "wing_ll" -> poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
				case "wing_ul", "wing_lr" -> poseStack.mulPose(Axis.ZP.rotationDegrees(-angle));
				case "dish" -> poseStack.mulPose(Axis.YP.rotationDegrees(pose.dish()));
				case "legs" -> poseStack.translate(0.0F, (1.0F - pose.legs()) * LEG_TRAVEL, 0.0F);
				default -> {
				}
			}

			// The item model is drawn around its middle, which is the part's pivot.
			poseStack.scale(part.scale(), part.scale(), part.scale());
			states.get(i).submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, outline);
			poseStack.popPose();
		}
	}
}
