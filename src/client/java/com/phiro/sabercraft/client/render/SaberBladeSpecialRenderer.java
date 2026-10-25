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
	private final boolean unstable;
	private final float igniteFrom;
	private final float igniteTo;

	/**
	 * @param defaultStyle   color for items without a stored color
	 * @param emitter        where the blade starts, in model space
	 * @param lengthOverride blade length, or 0 to use the length of the style
	 * @param angle          turn of the blade in the item plane in degrees, 0 = along the diagonal
	 * @param unstable       whether the blade crackles like a cracked crystal (Phase 9, crossguard hilt)
	 * @param igniteFrom     the part of the ignition at which this blade starts to come out (Phase 10)
	 * @param igniteTo       the part of the ignition at which this blade is fully out
	 */
	public SaberBladeSpecialRenderer(Identifier defaultStyle, Vector3fc emitter, float lengthOverride, float angle, boolean unstable,
			float igniteFrom, float igniteTo) {
		this.unstable = unstable;
		this.igniteFrom = igniteFrom;
		this.igniteTo = Math.max(igniteFrom + 0.01F, igniteTo);
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
		float extension = this.extension(state.extension());

		if (!state.isVisible() || extension <= 0.0F || ShaderCompat.renderingShadowPass()) {
			// Fully retracted: only the hilt is drawn, by the hilt model. A glowing blade casts no
			// shadow, so it is left out of the shadow map of shader packs.
			return;
		}

		BladeStyle style = BladeStyles.get(state.style() != null ? state.style() : this.defaultStyle);
		float length = this.fullLength(style) * extension;
		// The glow builds up over the first part of the blade's way out, so a tiny stub does not
		// already have a full-size halo. During ignition it flares up briefly.
		float build = Mth.clamp(extension / 0.25F, 0.0F, 1.0F);
		// An attack makes the blade glow a little brighter, strongest at the strike.
		float swing = state.swing() != null ? state.swing().strength() : 0.0F;
		float intensity = (this.unstable ? unstableIntensity() : intensity(style)) * (0.55F + 0.45F * build) * (1.0F + state.flare() + 0.22F * swing);

		boolean shader = SaberRenderTypes.beams();
		poseStack.pushPose();
		this.applyBladeTransform(poseStack);
		// Haze first, then the opaque core on top of it, so the core stays white instead of being
		// tinted by the haze in front of it. The additive bloom comes last.
		submitNodeCollector.order(0).submitCustomGeometry(
				poseStack, SaberRenderTypes.haze(state.firstPerson()), (pose, buffer) -> BladeMesh.haze(pose, buffer, style, length, intensity * build, shader)
		);
		if (this.unstable) {
			// Ragged flames of glow along the blade, new ones every few frames.
			long frame = Util.getMillis() / 45L;
			int seed = (int) (frame * 31L) ^ System.identityHashCode(this);
			submitNodeCollector.order(0).submitCustomGeometry(
					poseStack, SaberRenderTypes.haze(state.firstPerson()), (pose, buffer) -> BladeMesh.crackle(pose, buffer, style, length, intensity * build, seed, shader)
			);
		}

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

	/**
	 * How far this blade is out, from how far the saber is out: a blade with an ignite window only
	 * moves during its part of the ignition (Phase 10). An unstable blade sputters on its way, its
	 * length jumping back and forth a little, until it holds.
	 */
	private float extension(float saber) {
		float extension = windowed(saber, this.igniteFrom, this.igniteTo);

		if (this.unstable && extension > 0.0F && extension < 1.0F) {
			int step = (int) (Util.getMillis() / 50L) ^ System.identityHashCode(this);
			float jolt = (step * 1103515245 + 12345 >>> 16 & 0xFF) / 255.0F;
			extension *= 0.8F + 0.2F * jolt;
		}

		return extension;
	}

	/** How far a blade with the ignite window {@code from}..{@code to} is out when the saber is out by {@code saber}. */
	public static float windowed(float saber, float from, float to) {
		return from <= 0.0F && to >= 1.0F ? saber : Mth.clamp((saber - from) / Math.max(0.01F, to - from), 0.0F, 1.0F);
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

	/** The hard, irregular flicker of an unstable blade: mostly bright, with short dips and spikes. */
	private static float unstableIntensity() {
		long millis = Util.getMillis();
		float time = (millis % 1_000_000L) / 1000.0F;
		float wave = 0.5F * Mth.sin(time * 23.0F) + 0.5F * Mth.sin(time * 41.0F + 0.7F);
		int step = (int) (millis / 60L);
		float jolt = (step * 1103515245 + 12345 >>> 16 & 0xFF) / 255.0F;
		return 1.0F + 0.12F * wave + (jolt > 0.85F ? 0.25F : jolt < 0.1F ? -0.2F : 0.0F);
	}

	/**
	 * JSON form of the renderer. Example:
	 * <pre>{@code {"type": "sabercraft:saber_blade", "style": "sabercraft:red"}}</pre>
	 * Optional fields: {@code "emitter": [x, y, z]} for hilts of other shapes, {@code "length"} to
	 * override the style's blade length and {@code "angle"} to turn the blade in the item plane,
	 * for example 180 for the second blade of a double-bladed hilt. {@code "unstable": true} makes the
	 * blade crackle (Phase 9). {@code "ignite_from"} and {@code "ignite_to"} (0..1) give the part of
	 * the ignition in which this blade comes out, so blades of one hilt can ignite one after the
	 * other (Phase 10); retracting runs the other way round.
	 */
	public record Unbaked(Identifier style, Vector3fc emitter, Optional<Float> length, float angle, boolean unstable, float igniteFrom, float igniteTo)
			implements SpecialModelRenderer.Unbaked<BladeRenderState> {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
								Identifier.CODEC.optionalFieldOf("style", BladeStyles.RED.id()).forGetter(Unbaked::style),
								ExtraCodecs.VECTOR3F.optionalFieldOf("emitter", DEFAULT_EMITTER).forGetter(Unbaked::emitter),
								Codec.floatRange(0.1F, 4.0F).optionalFieldOf("length").forGetter(Unbaked::length),
								Codec.floatRange(-360.0F, 360.0F).optionalFieldOf("angle", 0.0F).forGetter(Unbaked::angle),
								Codec.BOOL.optionalFieldOf("unstable", false).forGetter(Unbaked::unstable),
								Codec.floatRange(0.0F, 0.99F).optionalFieldOf("ignite_from", 0.0F).forGetter(Unbaked::igniteFrom),
								Codec.floatRange(0.01F, 1.0F).optionalFieldOf("ignite_to", 1.0F).forGetter(Unbaked::igniteTo)
						)
						.apply(instance, Unbaked::new)
		);

		@Override
		public SaberBladeSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
			// Unknown styles are reported once here; at render time they fall back to red.
			BladeStyles.get(this.style);
			return new SaberBladeSpecialRenderer(this.style, this.emitter, this.length.orElse(0.0F), this.angle, this.unstable, this.igniteFrom, this.igniteTo);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
