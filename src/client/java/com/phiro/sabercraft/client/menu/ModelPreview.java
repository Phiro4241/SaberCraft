package com.phiro.sabercraft.client.menu;

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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * A large, turning 3D view of an item model in a menu (Phase 11, the blaster gallery). The item is
 * drawn into its own picture like the player in the inventory, without any display transform,
 * centered on its own bounds and scaled to fill the frame, so long rifles and short pistols both
 * fill the view. The turn comes from the caller (slowly by itself, or dragged with the mouse).
 *
 * <p>Minecraft keeps one picture per kind and frame, so a menu shows one turning model at a time;
 * the small icons of a gallery are normal item icons.
 */
public final class ModelPreview {
	private ModelPreview() {
	}

	public static void init() {
		PictureInPictureRendererRegistry.register(context -> new Renderer());
	}

	/**
	 * Draws a turning model of {@code stack} into the rectangle x0, y0 to x1, y1.
	 *
	 * @param yaw   turn around the vertical axis in degrees
	 * @param pitch tilt towards the viewer in degrees
	 */
	public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, float yaw, float pitch, int x0, int y0, int x1, int y1) {
		Minecraft client = Minecraft.getInstance();
		ItemStackRenderState item = new ItemStackRenderState();
		client.getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.NONE, client.level, client.player, 0);

		if (item.isEmpty()) {
			return;
		}

		AABB bounds = item.getModelBoundingBox();
		double size = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
		// Scale: the longest side of the model fills about 90 % of the wider side of the frame.
		float scale = (float) (Math.min(x1 - x0, (y1 - y0) * 1.6F) * 0.9F / Math.max(0.01, size));
		graphics.guiRenderState.addPicturesInPictureState(new State(item, bounds, yaw, pitch, x0, y0, x1, y1, scale, graphics.scissorStack.peek()));
	}

	public record State(ItemStackRenderState item, AABB box, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
			@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
		State(ItemStackRenderState item, AABB box, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea) {
			this(item, box, yaw, pitch, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
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
			// GUI y grows downwards, the model's upwards.
			poseStack.scale(1.0F, -1.0F, -1.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch()));
			poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw()));
			AABB bounds = state.box();
			poseStack.translate(-(bounds.minX + bounds.maxX) / 2.0, -(bounds.minY + bounds.maxY) / 2.0, -(bounds.minZ + bounds.maxZ) / 2.0);
			state.item().submit(poseStack, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
		}

		@Override
		protected float getTranslateY(int height, int guiScale) {
			return height / 2.0F;
		}

		@Override
		protected String getTextureLabel() {
			return "sabercraft_model_preview";
		}
	}
}
