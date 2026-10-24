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
import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;

/**
 * Draws the energy blade at a given length. It runs after the item display transform, so the blade
 * follows the hilt in first person, third person and on armor stands without extra code.
 *
 * <p>Held sabers use it through {@link SaberBladeItemModel}, which passes in how far the blade is
 * out. It can also be used as {@code "type": "sabercraft:saber_blade"} inside a vanilla
 * {@code minecraft:special} item model. That shows the blade always at full length, which is handy
 * for decoration in maps.
 *
 * <p>Model space is the usual item space: the 16x16 texture spans 0..1 on X and Y, and handheld
 * items point their blade along the diagonal from bottom left to top right.
 */
public final class SaberBladeSpecialRenderer implements SpecialModelRenderer<BladeRenderState> {
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
			@Nullable BladeRenderState blade,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			int overlayCoords,
			boolean hasFoil,
			int outlineColor
	) {
		BladeRenderState state = blade != null ? blade : BladeRenderState.FULL;

		if (!state.isVisible()) {
			// Fully retracted: only the hilt is drawn, by the hilt model.
			return;
		}

		float length = this.style.length() * state.extension();
		// The glow builds up over the first part of the blade's way out, so a tiny stub does not
		// already have a full-size halo. During ignition it flares up briefly.
		float build = Mth.clamp(state.extension() / 0.25F, 0.0F, 1.0F);
		float intensity = this.intensity() * (0.55F + 0.45F * build) * (1.0F + state.flare());

		poseStack.pushPose();
		this.applyBladeTransform(poseStack);
		// Haze first, then the opaque core on top of it, so the core stays white instead of being
		// tinted by the haze in front of it. The additive bloom comes last.
		submitNodeCollector.order(0).submitCustomGeometry(
				poseStack, SaberRenderTypes.BLADE_HAZE, (pose, buffer) -> BladeMesh.haze(pose, buffer, this.style, length, intensity * build)
		);
		submitNodeCollector.order(1).submitCustomGeometry(
				poseStack, SaberRenderTypes.BLADE_CORE, (pose, buffer) -> BladeMesh.core(pose, buffer, this.style, length, intensity)
		);
		submitNodeCollector.order(2).submitCustomGeometry(
				poseStack, SaberRenderTypes.BLADE_BLOOM, (pose, buffer) -> BladeMesh.bloom(pose, buffer, this.style, length, intensity * build)
		);
		poseStack.popPose();
	}

	/** Outside of held items (see {@link SaberBladeItemModel}) the blade is always fully out. */
	@Override
	public BladeRenderState extractArgument(ItemStack stack) {
		return BladeRenderState.FULL;
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
	public record Unbaked(Identifier style, Vector3fc emitter, Optional<Float> length) implements SpecialModelRenderer.Unbaked<BladeRenderState> {
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
