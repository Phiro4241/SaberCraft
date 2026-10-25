package com.phiro.sabercraft.client.ship;

import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import com.phiro.sabercraft.ship.ShipType;

/**
 * A large, turning starship in the hangar of the SaberCraft menu (Phase 11), like the droid
 * preview. Its S-foils open and close every few seconds.
 */
public final class ShipPreview {
	private ShipPreview() {
	}

	public static void init() {
		PictureInPictureRendererRegistry.register(context -> new Renderer());
	}

	/** Draws {@code ship} with its S-foils {@code foils} open, turned by {@code yaw} and {@code pitch}, into the rectangle. */
	public static void draw(GuiGraphicsExtractor graphics, ShipType ship, float foils, float yaw, float pitch, int x0, int y0, int x1, int y1) {
		Minecraft client = Minecraft.getInstance();
		List<ItemStackRenderState> parts = ShipModels.resolve(ship, null, client.level, client.player, 0);
		// The X-Wing is about 5.5 blocks across and 5 long.
		float scale = (x1 - x0) * 0.8F / 6.0F;
		graphics.guiRenderState.addPicturesInPictureState(new State(ship, foils, parts, yaw, pitch, x0, y0, x1, y1, scale, graphics.scissorStack.peek()));
	}

	public record State(ShipType ship, float foils, List<ItemStackRenderState> parts, float yaw, float pitch, int x0, int y0, int x1, int y1,
			float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
		State(ShipType ship, float foils, List<ItemStackRenderState> parts, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
				@Nullable ScreenRectangle scissorArea) {
			this(ship, foils, parts, yaw, pitch, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
		}
	}

	static final class Renderer extends PictureInPictureRenderer<State> {
		@Override
		public Class<State> getRenderStateClass() {
			return State.class;
		}

		@Override
		protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
			Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
			poseStack.scale(1.0F, -1.0F, -1.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch()));
			poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw()));
			List<ShipParts.Part> parts = ShipModels.parts(state.ship());
			poseStack.translate(0.0F, parts.isEmpty() ? -1.0F : -parts.getFirst().y(), 0.0F);
			ShipModels.submit(poseStack, collector, state.ship(), state.foils(), state.parts(), 0xF000F0, 0);
		}

		@Override
		protected float getTranslateY(int height, int guiScale) {
			return height / 2.0F;
		}

		@Override
		protected String getTextureLabel() {
			return "sabercraft_ship_preview";
		}
	}
}
