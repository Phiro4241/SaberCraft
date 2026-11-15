package com.phiro.sabercraft.client.interior;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Phase 15: a list of textured quads for one layer of one cell of an interior scene (see
 * {@link InteriorScene}). Every quad keeps its four corners (plot space), their atlas UVs and colours,
 * one normal, how it is lit and how it is animated. The light of the corners that are lit by the
 * world is looked up again from time to time ({@link #relight}), so the panels are as bright as the
 * blocks around them.
 */
final class Mesh {
	/** Floats per quad: 4 x (x, y, z, u, v) and the normal. */
	static final int FLOATS = 23;
	/** Ints per quad: 4 colours, 4 lights, light mode, animation, 5 x 3 sample cells (the corners, then the middle). */
	static final int INTS = 25;

	/** How a quad gets its light. */
	static final int LIGHT_WORLD = 0;
	static final int LIGHT_FULL = 1;
	static final int LIGHT_FIXED = 2;
	/** Phase 17: a bit of the animation int: the quad is part of the rooms (dimmed with the ship's lights). */
	static final int INSIDE = 1 << 16;

	float[] floats = new float[FLOATS * 64];
	int[] ints = new int[INTS * 64];
	int count;

	void add(float[] xyzuv, float nx, float ny, float nz, int[] colours, int lightMode, int fixedLight, int anim, int[] samples) {
		if ((this.count + 1) * FLOATS > this.floats.length) {
			this.floats = Arrays.copyOf(this.floats, this.floats.length * 2);
			this.ints = Arrays.copyOf(this.ints, this.ints.length * 2);
		}

		int f = this.count * FLOATS;
		System.arraycopy(xyzuv, 0, this.floats, f, 20);
		this.floats[f + 20] = nx;
		this.floats[f + 21] = ny;
		this.floats[f + 22] = nz;
		int i = this.count * INTS;
		System.arraycopy(colours, 0, this.ints, i, 4);
		int light = lightMode == LIGHT_FULL ? LightCoordsUtil.FULL_BRIGHT : lightMode == LIGHT_FIXED ? fixedLight : LightCoordsUtil.pack(8, 0);
		this.ints[i + 4] = light;
		this.ints[i + 5] = light;
		this.ints[i + 6] = light;
		this.ints[i + 7] = light;
		this.ints[i + 8] = lightMode;
		this.ints[i + 9] = anim;
		System.arraycopy(samples, 0, this.ints, i + 10, 15);
		this.count++;
	}

	/** Looks up the light of every world-lit corner again. */
	void relight(LightLookup lookup) {
		for (int q = 0; q < this.count; q++) {
			int i = q * INTS;

			if (this.ints[i + 8] != LIGHT_WORLD) {
				continue;
			}

			int m = i + 22;
			int middle = lookup.light(this.ints[m], this.ints[m + 1], this.ints[m + 2]);

			for (int v = 0; v < 4; v++) {
				int s = i + 10 + v * 3;
				int light = lookup.light(this.ints[s], this.ints[s + 1], this.ints[s + 2]);
				// A corner whose cell is solid (in a wall) takes the light of the middle.
				this.ints[i + 4 + v] = light < 0 ? Math.max(0, middle) : light;
			}
		}
	}

	/** The light of a cell in plot space, packed, or -1 if the cell is a solid block. */
	interface LightLookup {
		int light(int x, int y, int z);
	}

	/** Brightness of an animated quad (1 = as painted). */
	interface Animator {
		float factor(int anim, int quad);
	}

	private final Vector3f point = new Vector3f();
	private final Vector3f normal = new Vector3f();

	/**
	 * Writes the quads. {@code alphaAnim}: animation changes the alpha (translucent layers) instead
	 * of the brightness (additive glow).
	 */
	void emit(PoseStack.Pose pose, VertexConsumer buffer, Animator animator, boolean alphaAnim) {
		this.emit(pose, buffer, animator, alphaAnim, 1.0F, 1.0F);
	}

	/**
	 * Phase 17: as above; the quads of the rooms ({@link #INSIDE}) darker by the ship's lights:
	 * {@code lit} those lit by the world, {@code glow} the glowing ones (lamps, screens).
	 */
	void emit(PoseStack.Pose pose, VertexConsumer buffer, Animator animator, boolean alphaAnim, float lit, float glow) {
		Matrix4f matrix = pose.pose();
		Matrix3f normals = pose.normal();

		for (int q = 0; q < this.count; q++) {
			int f = q * FLOATS;
			int i = q * INTS;
			int raw = this.ints[i + 9];
			int anim = raw & 0xFFFF;
			float factor = anim == 0 ? 1.0F : animator.factor(anim, q);
			float dim = (raw & INSIDE) == 0 ? 1.0F : this.ints[i + 8] == LIGHT_WORLD ? lit : glow;

			if (factor <= 0.004F) {
				continue;
			}

			normals.transform(this.normal.set(this.floats[f + 20], this.floats[f + 21], this.floats[f + 22]));

			for (int v = 0; v < 4; v++) {
				int o = f + v * 5;
				matrix.transformPosition(this.floats[o], this.floats[o + 1], this.floats[o + 2], this.point);
				int colour = this.ints[i + v];

				if (factor != 1.0F) {
					colour = scale(colour, factor, alphaAnim);
				}

				if (dim != 1.0F) {
					colour = scale(colour, dim, false);
				}

				buffer.addVertex(this.point.x, this.point.y, this.point.z, colour, this.floats[o + 3], this.floats[o + 4], OverlayTexture.NO_OVERLAY,
						this.ints[i + 4 + v], this.normal.x, this.normal.y, this.normal.z);
			}
		}
	}

	static int scale(int argb, float factor, boolean alpha) {
		if (alpha) {
			int a = Math.min(255, Math.round((argb >>> 24) * factor));
			return a << 24 | argb & 0xFFFFFF;
		}

		int r = Math.min(255, Math.round((argb >> 16 & 0xFF) * factor));
		int g = Math.min(255, Math.round((argb >> 8 & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return argb & 0xFF000000 | r << 16 | g << 8 | b;
	}

	void clear() {
		this.count = 0;
	}

	boolean isEmpty() {
		return this.count == 0;
	}
}
