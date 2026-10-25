package com.phiro.sabercraft.client.droid;

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

import com.phiro.sabercraft.droid.DroidType;

/**
 * A large, turning droid in the droid menu (Phase 11), with all its parts and animations, like the
 * blaster preview of the blaster menu. It walks on the spot now and then, so arms, legs and BB-8's
 * rolling ball show.
 */
public final class DroidPreview {
	private DroidPreview() {
	}

	public static void init() {
		PictureInPictureRendererRegistry.register(context -> new Renderer());
	}

	/** Draws {@code droid} in {@code pose}, turned by {@code yaw} and {@code pitch}, into the rectangle. */
	public static void draw(GuiGraphicsExtractor graphics, DroidType droid, DroidPose pose, float yaw, float pitch, int x0, int y0, int x1, int y1) {
		Minecraft client = Minecraft.getInstance();
		List<ItemStackRenderState> parts = DroidModels.resolve(droid, null, client.level, client.player, 0);
		float size = Math.max(droid.height() + (droid.hovers() ? 0.3F : 0.0F), droid.width() * 1.4F);
		float scale = (y1 - y0) * 0.86F / size;
		graphics.guiRenderState.addPicturesInPictureState(new State(droid, pose, parts, yaw, pitch, x0, y0, x1, y1, scale, graphics.scissorStack.peek()));
	}

	public record State(DroidType droid, DroidPose animation, List<ItemStackRenderState> parts, float yaw, float pitch, int x0, int y0, int x1, int y1,
			float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
		State(DroidType droid, DroidPose animation, List<ItemStackRenderState> parts, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
				@Nullable ScreenRectangle scissorArea) {
			this(droid, animation, parts, yaw, pitch, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
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
			poseStack.translate(0.0F, -(state.droid().height() + (state.droid().hovers() ? 0.3F : 0.0F)) / 2.0F, 0.0F);
			DroidModels.submit(poseStack, collector, state.droid(), state.animation(), state.parts(), 0xF000F0, 0);
		}

		@Override
		protected float getTranslateY(int height, int guiScale) {
			return height / 2.0F;
		}

		@Override
		protected String getTextureLabel() {
			return "sabercraft_droid_preview";
		}
	}
}
