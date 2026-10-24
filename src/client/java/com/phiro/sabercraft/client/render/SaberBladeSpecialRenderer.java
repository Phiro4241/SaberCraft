package com.phiro.sabercraft.client.render;

import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;

/**
 * Item model renderer for the energy blade, used as {@code "type": "sabercraft:saber_blade"} inside
 * a {@code minecraft:special} item model. It runs after the item display transform, so the blade
 * follows the hilt in first person, third person and on armor stands without extra code.
 *
 * <p>Model space is the usual item space: the 16x16 texture spans 0..1 on X and Y, and handheld
 * items point their blade along the diagonal from bottom left to top right.
 */
public final class SaberBladeSpecialRenderer implements NoDataSpecialModelRenderer {
	/** Where the default hilt texture ends: the emitter, on the item diagonal. */
	public static final Vector3fc DEFAULT_EMITTER = new Vector3f(0.58F, 0.58F, 0.5F);
	/** Turns the local +Y axis onto the item diagonal (+X, +Y). */
	private static final Quaternionf DIAGONAL = new Quaternionf().rotationZ(-Mth.PI / 4.0F);

	private final BladeStyle style;
	private final Vector3fc emitter;

	public SaberBladeSpecialRenderer(BladeStyle style, Vector3fc emitter) {
		this.style = style;
		this.emitter = emitter;
	}

	@Override
	public void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			int overlayCoords,
			boolean hasFoil,
			int outlineColor
	) {
		float intensity = this.intensity();

		poseStack.pushPose();
		this.applyBladeTransform(poseStack);
		// Haze first, then the opaque core on top of it, so the core stays white instead of being
		// tinted by the haze in front of it. The additive bloom comes last.
		submitNodeCollector.order(0).submitCustomGeometry(
				poseStack, SaberRenderTypes.BLADE_HAZE, (pose, buffer) -> BladeMesh.haze(pose, buffer, this.style, intensity)
		);
		submitNodeCollector.order(1).submitCustomGeometry(
				poseStack, SaberRenderTypes.BLADE_CORE, (pose, buffer) -> BladeMesh.core(pose, buffer, this.style, intensity)
		);
		submitNodeCollector.order(2).submitCustomGeometry(
				poseStack, SaberRenderTypes.BLADE_BLOOM, (pose, buffer) -> BladeMesh.bloom(pose, buffer, this.style, intensity)
		);
		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		PoseStack poseStack = new PoseStack();
		this.applyBladeTransform(poseStack);

		float radius = this.style.glowRadius();
		float top = this.style.length() + radius;

		for (float y : new float[] {0.0F, top}) {
			for (float x : new float[] {-radius, radius}) {
				for (float z : new float[] {-radius, radius}) {
					output.accept(poseStack.last().pose().transformPosition(x, y, z, new Vector3f()));
				}
			}
		}
	}

	private void applyBladeTransform(PoseStack poseStack) {
		poseStack.translate(this.emitter.x(), this.emitter.y(), this.emitter.z());
		poseStack.mulPose(DIAGONAL);
	}

	/** Subtle, irregular brightness shimmer, like the unstable glow of a real energy blade. */
	private float intensity() {
		if (this.style.shimmer() <= 0.0F) {
			return 1.0F;
		}

		float time = (Util.getMillis() % 1_000_000L) / 1000.0F;
		float wave = 0.6F * Mth.sin(time * 7.3F) + 0.4F * Mth.sin(time * 17.9F + 1.3F);
		return 1.0F + this.style.shimmer() * wave;
	}

	/**
	 * JSON form of the renderer. Example:
	 * <pre>{@code {"type": "sabercraft:saber_blade", "style": "sabercraft:red"}}</pre>
	 * Optional fields: {@code "emitter": [x, y, z]} for hilts of other shapes and {@code "length"}
	 * to override the style's blade length.
	 */
	public record Unbaked(Identifier style, Vector3fc emitter, Optional<Float> length) implements NoDataSpecialModelRenderer.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
								Identifier.CODEC.optionalFieldOf("style", BladeStyles.RED.id()).forGetter(Unbaked::style),
								ExtraCodecs.VECTOR3F.optionalFieldOf("emitter", DEFAULT_EMITTER).forGetter(Unbaked::emitter),
								Codec.floatRange(0.1F, 4.0F).optionalFieldOf("length").forGetter(Unbaked::length)
						)
						.apply(instance, Unbaked::new)
		);

		@Override
		public SaberBladeSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
			BladeStyle bladeStyle = BladeStyles.get(this.style);

			if (this.length.isPresent()) {
				bladeStyle = bladeStyle.withLength(this.length.get());
			}

			return new SaberBladeSpecialRenderer(bladeStyle, this.emitter);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
