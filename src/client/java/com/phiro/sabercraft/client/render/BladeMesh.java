package com.phiro.sabercraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.client.blade.BladeStyle;

/**
 * Builds the blade geometry in a local frame where the blade starts at the origin and points
 * along +Y. The blade consists of three layers:
 *
 * <ol>
 *   <li>core: a solid, almost white tube with a rounded tip,</li>
 *   <li>bloom: a thin additive shell that makes the core edge glow and blends it into the color,</li>
 *   <li>haze: a translucent colored shell plus a ring of crossed "fins" with a soft gradient that
 *       fades to zero at the edge. Seen from any angle, a few fins face the camera, which reads
 *       as a smooth volumetric glow.</li>
 * </ol>
 */
public final class BladeMesh {
	/** Corners of the round cross-section. */
	private static final int SIDES = 12;
	/** Steps used to round off the tip. */
	private static final int TIP_STEPS = 3;
	/** Number of crossed glow fins around the blade axis. */
	private static final int FINS = 8;
	/** The core starts slightly inside the emitter so no gap is visible. */
	private static final float BASE_OFFSET = -0.03F;

	/** Distance from the axis (fraction of the glow radius) of each vertex across a fin. */
	private static final float[] FIN_OFFSETS = {-1.0F, -0.42F, 0.0F, 0.42F, 1.0F};
	/** Glow alpha at each of those vertices. Roughly a gaussian falloff. */
	private static final float[] FIN_FALLOFF = {0.0F, 0.32F, 1.0F, 0.32F, 0.0F};

	private static final float[] COS = new float[SIDES + 1];
	private static final float[] SIN = new float[SIDES + 1];

	static {
		for (int i = 0; i <= SIDES; i++) {
			float angle = i * Mth.TWO_PI / SIDES;
			COS[i] = Mth.cos(angle);
			SIN[i] = Mth.sin(angle);
		}
	}

	private BladeMesh() {
	}

	/**
	 * Solid core, drawn with {@link SaberRenderTypes#BLADE_CORE}. {@code length} is the current
	 * blade length: the style's length when fully out, less while it ignites or retracts.
	 */
	public static void core(PoseStack.Pose pose, VertexConsumer buffer, BladeStyle style, float length, float intensity) {
		int color = argb(1.0F, scale(style.coreColor(), Math.min(1.0F, 0.94F + 0.06F * intensity)));
		tube(pose, buffer, style.coreRadius(), length, color, color);
	}

	/** Additive bloom around the core, drawn with {@link SaberRenderTypes#BLADE_BLOOM}. */
	public static void bloom(PoseStack.Pose pose, VertexConsumer buffer, BladeStyle style, float length, float intensity) {
		float alpha = Math.min(1.0F, 0.6F * style.glowStrength() * intensity);
		// Blade color mixed with white, so the core does not end in a hard edge.
		int hot = mix(style.glowColor(), 0xFFFFFF, 0.3F);
		float core = style.coreRadius();
		tube(pose, buffer, core * 1.7F, length + core * 0.4F, argb(alpha, hot), argb(alpha, hot));
	}

	/** Translucent colored glow, drawn with {@link SaberRenderTypes#BLADE_HAZE}. */
	public static void haze(PoseStack.Pose pose, VertexConsumer buffer, BladeStyle style, float length, float intensity) {
		float strength = style.glowStrength() * intensity;
		float core = style.coreRadius();
		int glow = style.glowColor();

		// Two thin shells give a stepped falloff from the core into the soft fins.
		int inner = argb(0.24F * strength, glow);
		int outer = argb(0.13F * strength, glow);
		tube(pose, buffer, core * 2.3F, length + core * 0.9F, inner, inner);
		tube(pose, buffer, core * 3.4F, length + core * 1.6F, outer, outer);
		fins(pose, buffer, style.glowRadius(), length, glow, 0.2F * strength);
	}

	/** A closed round tube along +Y with a dome-shaped tip. */
	private static void tube(PoseStack.Pose pose, VertexConsumer buffer, float radius, float tipY, int bodyColor, int tipColor) {
		float cylinderTop = Math.max(BASE_OFFSET, tipY - radius);
		ring(pose, buffer, radius, BASE_OFFSET, radius, cylinderTop, bodyColor, bodyColor);

		float lastRadius = radius;
		float lastY = cylinderTop;

		for (int step = 1; step <= TIP_STEPS; step++) {
			float angle = step * Mth.HALF_PI / TIP_STEPS;
			float nextRadius = radius * Mth.cos(angle);
			float nextY = cylinderTop + radius * Mth.sin(angle);
			ring(pose, buffer, lastRadius, lastY, nextRadius, nextY, bodyColor, tipColor);
			lastRadius = nextRadius;
			lastY = nextY;
		}
	}

	/** One band of quads between two cross-sections. Wound counter-clockwise seen from outside. */
	private static void ring(
			PoseStack.Pose pose, VertexConsumer buffer, float r0, float y0, float r1, float y1, int color0, int color1
	) {
		for (int i = 0; i < SIDES; i++) {
			vertex(pose, buffer, COS[i] * r0, y0, SIN[i] * r0, color0);
			vertex(pose, buffer, COS[i] * r1, y1, SIN[i] * r1, color1);
			vertex(pose, buffer, COS[i + 1] * r1, y1, SIN[i + 1] * r1, color1);
			vertex(pose, buffer, COS[i + 1] * r0, y0, SIN[i + 1] * r0, color0);
		}
	}

	/** Flat glow planes crossing on the blade axis, each with a soft falloff to both sides. */
	private static void fins(PoseStack.Pose pose, VertexConsumer buffer, float radius, float length, int rgb, float alpha) {
		// The glow fades in over the first part of the blade, so it does not swallow the emitter.
		// While the blade is still short, all three sections shrink with it.
		float fadeEnd = Math.min(radius * 1.2F, length * 0.4F);
		float tipStart = Math.max(fadeEnd, length - radius * 0.3F);
		float tipEnd = tipStart + Math.min(radius * 1.1F, Math.max(length, 0.02F) * 0.8F);
		int clear = argb(0.0F, rgb);

		for (int fin = 0; fin < FINS; fin++) {
			float angle = fin * Mth.PI / FINS;
			float dx = Mth.cos(angle);
			float dz = Mth.sin(angle);

			for (int band = 0; band < FIN_OFFSETS.length - 1; band++) {
				float o0 = FIN_OFFSETS[band] * radius;
				float o1 = FIN_OFFSETS[band + 1] * radius;
				int c0 = argb(alpha * FIN_FALLOFF[band], rgb);
				int c1 = argb(alpha * FIN_FALLOFF[band + 1], rgb);

				// Base: narrow and clear at the emitter, full glow at fadeEnd.
				vertex(pose, buffer, dx * o0 * 0.4F, 0.0F, dz * o0 * 0.4F, clear);
				vertex(pose, buffer, dx * o0, fadeEnd, dz * o0, c0);
				vertex(pose, buffer, dx * o1, fadeEnd, dz * o1, c1);
				vertex(pose, buffer, dx * o1 * 0.4F, 0.0F, dz * o1 * 0.4F, clear);

				// Body: constant glow up to the start of the tip.
				vertex(pose, buffer, dx * o0, fadeEnd, dz * o0, c0);
				vertex(pose, buffer, dx * o0, tipStart, dz * o0, c0);
				vertex(pose, buffer, dx * o1, tipStart, dz * o1, c1);
				vertex(pose, buffer, dx * o1, fadeEnd, dz * o1, c1);

				// Tip: narrows and fades out just beyond the end of the core.
				vertex(pose, buffer, dx * o0, tipStart, dz * o0, c0);
				vertex(pose, buffer, dx * o0 * 0.35F, tipEnd, dz * o0 * 0.35F, clear);
				vertex(pose, buffer, dx * o1 * 0.35F, tipEnd, dz * o1 * 0.35F, clear);
				vertex(pose, buffer, dx * o1, tipStart, dz * o1, c1);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int argb) {
		buffer.addVertex(pose, x, y, z).setColor(argb);
	}

	private static int argb(float alpha, int rgb) {
		int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
		return a << 24 | rgb & 0xFFFFFF;
	}

	private static int scale(int rgb, float factor) {
		int r = Math.round((rgb >> 16 & 0xFF) * factor);
		int g = Math.round((rgb >> 8 & 0xFF) * factor);
		int b = Math.round((rgb & 0xFF) * factor);
		return r << 16 | g << 8 | b;
	}

	private static int mix(int from, int to, float amount) {
		int r = Math.round(Mth.lerp(amount, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(amount, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(amount, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
