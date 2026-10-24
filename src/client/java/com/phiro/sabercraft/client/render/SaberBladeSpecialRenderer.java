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
 *
 * <p>The color comes from the item (see {@link com.phiro.sabercraft.saber.SaberVariant}) and is
 * looked up every frame, so changes from the config file apply without rebuilding the models. The
 * {@code style} in the JSON is the color for swords that have none stored.
 *
 * <p>A hilt can carry more than one blade: each {@code sabercraft:saber_blade} entry in the item
 * model draws one blade from its own {@code emitter}, turned by {@code angle} degrees in the item
 * plane. The double-bladed hilt uses two entries, the second one with {@code "angle": 180}.
 */
public final class SaberBladeSpecialRenderer implements SpecialModelRenderer<BladeRenderState> {
	/** Where the default hilt texture ends: the emitter, on the item diagonal. */
	public static final Vector3fc DEFAULT_EMITTER = new Vector3f(0.58F, 0.58F, 0.5F);
	/** Turns the local +Y axis onto the item diagonal (+X, +Y). */
	private static final Quaternionf DIAGONAL = new Quaternionf().rotationZ(-Mth.PI / 4.0F);

	private final Identifier defaultStyle;
	private final Vector3fc emitter;
	private final float lengthOverride;
	private final Quaternionf direction;

	/**
	 * @param defaultStyle   color for items without a stored color
	 * @param emitter        where the blade starts, in model space
	 * @param lengthOverride blade length, or 0 to use the length of the style
	 * @param angle          turn of the blade in the item plane in degrees, 0 = along the diagonal
	 */
	public SaberBladeSpecialRenderer(Identifier defaultStyle, Vector3fc emitter, float lengthOverride, float angle) {
		this.defaultStyle = defaultStyle;
		this.emitter = emitter;
		this.lengthOverride = lengthOverride;
		this.direction = new Quaternionf(DIAGONAL).rotateZ(-angle * Mth.DEG_TO_RAD);
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

		if (!state.isVisible() || ShaderCompat.renderingShadowPass()) {
			// Fully retracted: only the hilt is drawn, by the hilt model. A glowing blade casts no
			// shadow, so it is left out of the shadow map of shader packs.
			return;
		}

		BladeStyle style = BladeStyles.get(state.style() != null ? state.style() : this.defaultStyle);
		float length = this.fullLength(style) * state.extension();
		// The glow builds up over the first part of the blade's way out, so a tiny stub does not
		// already have a full-size halo. During ignition it flares up briefly.
		float build = Mth.clamp(state.extension() / 0.25F, 0.0F, 1.0F);
		// An attack makes the blade glow a little brighter, strongest at the strike.
		float swing = state.swing() != null ? state.swing().strength() : 0.0F;
		float intensity = intensity(style) * (0.55F + 0.45F * build) * (1.0F + state.flare() + 0.22F * swing);

		boolean shader = SaberRenderTypes.beams();
		poseStack.pushPose();
		this.applyBladeTransform(poseStack);
		// Haze first, then the opaque core on top of it, so the core stays white instead of being
		// tinted by the haze in front of it. The additive bloom comes last.
		submitNodeCollector.order(0).submitCustomGeometry(
				poseStack, SaberRenderTypes.haze(state.firstPerson()), (pose, buffer) -> BladeMesh.haze(pose, buffer, style, length, intensity * build, shader)
		);
		submitNodeCollector.order(1).submitCustomGeometry(
				poseStack, SaberRenderTypes.core(state.firstPerson()), (pose, buffer) -> BladeMesh.core(pose, buffer, style, length, intensity, shader)
		);
		submitNodeCollector.order(2).submitCustomGeometry(
				poseStack, SaberRenderTypes.bloom(state.firstPerson()), (pose, buffer) -> BladeMesh.bloom(pose, buffer, style, length, intensity * build)
		);

		if (state.swing() != null) {
			BladeTrails.submit(state.swing(), this, poseStack, submitNodeCollector, style, length);
		}

		poseStack.popPose();
	}

	/** Outside of held items (see {@link SaberBladeItemModel}) the blade is always fully out. */
	@Override
	public BladeRenderState extractArgument(ItemStack stack) {
		return BladeRenderState.FULL.withStyle(BladeStyles.colorOf(stack));
	}

	private float fullLength(BladeStyle style) {
		return this.lengthOverride > 0.0F ? this.lengthOverride : style.length();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		PoseStack poseStack = new PoseStack();
		this.applyBladeTransform(poseStack);

		// Large enough for every color, so culling never cuts off a recolored blade.
		float radius = 0.0F;
		float top = 0.0F;

		for (BladeStyle style : BladeStyles.all().values()) {
			radius = Math.max(radius, style.glowRadius());
			top = Math.max(top, this.fullLength(style) + style.glowRadius());
		}


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
		poseStack.mulPose(this.direction);
	}

	/** Subtle, irregular brightness shimmer, like the unstable glow of a real energy blade. */
	private static float intensity(BladeStyle style) {
		if (style.shimmer() <= 0.0F) {
			return 1.0F;
		}

		float time = (Util.getMillis() % 1_000_000L) / 1000.0F;
		float wave = 0.6F * Mth.sin(time * 7.3F) + 0.4F * Mth.sin(time * 17.9F + 1.3F);
		return 1.0F + style.shimmer() * wave;
	}

	/**
	 * JSON form of the renderer. Example:
	 * <pre>{@code {"type": "sabercraft:saber_blade", "style": "sabercraft:red"}}</pre>
	 * Optional fields: {@code "emitter": [x, y, z]} for hilts of other shapes, {@code "length"} to
	 * override the style's blade length and {@code "angle"} to turn the blade in the item plane,
	 * for example 180 for the second blade of a double-bladed hilt.
	 */
	public record Unbaked(Identifier style, Vector3fc emitter, Optional<Float> length, float angle) implements SpecialModelRenderer.Unbaked<BladeRenderState> {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
								Identifier.CODEC.optionalFieldOf("style", BladeStyles.RED.id()).forGetter(Unbaked::style),
								ExtraCodecs.VECTOR3F.optionalFieldOf("emitter", DEFAULT_EMITTER).forGetter(Unbaked::emitter),
								Codec.floatRange(0.1F, 4.0F).optionalFieldOf("length").forGetter(Unbaked::length),
								Codec.floatRange(-360.0F, 360.0F).optionalFieldOf("angle", 0.0F).forGetter(Unbaked::angle)
						)
						.apply(instance, Unbaked::new)
		);

		@Override
		public SaberBladeSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
			// Unknown styles are reported once here; at render time they fall back to red.
			BladeStyles.get(this.style);
			return new SaberBladeSpecialRenderer(this.style, this.emitter, this.length.orElse(0.0F), this.angle);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
