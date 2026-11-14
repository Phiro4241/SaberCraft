package com.phiro.sabercraft.client.interior;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;

/**
 * Phase 15: furniture both ships share, drawn in a local frame: x across, y up, the person using
 * it on the +z side. {@link #at} places the frame (turned like Minecraft's yaw: 0 = the user stands
 * south of it, 90 = west, 180 = north, 270 = east).
 */
final class Props {
	private Props() {
	}

	/** Starts a frame at a point, turned so the user stands on that side. Pair with {@code b.pop()}. */
	static MeshBuilder at(MeshBuilder b, double x, double y, double z, float userSide) {
		return b.push().translate(x, y, z).rotateY(userSide);
	}

	/** The side of a block a user stands on, as an angle for {@link #at}. */
	static float south() {
		return 0.0F;
	}

	static float east() {
		return 90.0F;
	}

	static float north() {
		return 180.0F;
	}

	static float west() {
		return -90.0F;
	}

	/**
	 * A sloped desk console {@code width} wide (centred on x = 0, depth -0.45..0.45): a body with a
	 * panelled front, a lit sloping top, and a screen riser at the back if {@code riser}.
	 */
	static void desk(MeshBuilder b, float width, float height, int body, int top, int topGlow, int screenGlow, boolean riser, int seed) {
		float x0 = -width / 2;
		float x1 = width / 2;
		float back = -0.45F;
		float front = 0.38F;
		float low = height;
		float high = height + 0.22F;
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		// The body: front panel per block.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x0, 0.0F, back, x1, low, front, MeshBuilder.WEST | MeshBuilder.EAST | MeshBuilder.NORTH);
		b.box(x0 + 0.06F, 0.0F, front, x1 - 0.06F, 0.12F, front + 0.06F, MeshBuilder.UP | MeshBuilder.SOUTH);

		for (float x = x0; x < x1 - 0.01F; x += 1.0F) {
			float w = Math.min(1.0F, x1 - x);
			b.fit(body, 0.0F, 0.0F, w, 1.0F);
			b.rect(x, low, front, w, 0, 0, 0, -low, 0);
		}

		// The sloping top, rising towards the back.
		for (float x = x0; x < x1 - 0.01F; x += 1.0F) {
			float w = Math.min(1.0F, x1 - x);
			b.layer(Layer.SOLID).worldLight().fit(top, 0.0F, 0.0F, w, 1.0F);
			b.rect(x, high, back, w, 0, 0, 0, low - high, front - back);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(topGlow, 0.0F, 0.0F, w, 1.0F);
			b.push().translate(0.0F, 0.006F, 0.003F);
			b.rect(x, high, back, w, 0, 0, 0, low - high, front - back);
			b.pop();
		}

		b.layer(Layer.SOLID).worldLight().anim(0).tile(InteriorAtlas.IMP_METAL, 1.0F);
		// The sides of the slope.
		b.polyFacing(List.of(new Vector3f(x0, low, front), new Vector3f(x0, low, back), new Vector3f(x0, high, back)), new Vector3f(-1, 0, 0));
		b.polyFacing(List.of(new Vector3f(x1, low, front), new Vector3f(x1, low, back), new Vector3f(x1, high, back)), new Vector3f(1, 0, 0));
		b.box(x0, low, back - 0.001F, x1, high, back, MeshBuilder.NORTH);

		if (riser) {
			float r0 = high;
			float r1 = high + 0.5F;
			b.box(x0, r0, back, x1, r1, back + 0.12F, MeshBuilder.ALL & ~MeshBuilder.DOWN & ~MeshBuilder.SOUTH);

			for (float x = x0; x < x1 - 0.01F; x += 1.0F) {
				float w = Math.min(1.0F, x1 - x);
				b.layer(Layer.SOLID).worldLight().anim(0).fit(InteriorAtlas.IMP_SCREEN);
				b.rect(x + 0.04F, r1 - 0.04F, back + 0.12F, w - 0.08F, 0, 0, 0, -(r1 - r0 - 0.08F), 0);
				int glow = ((int) Math.floor(x) + seed & 3) == 0 ? InteriorAtlas.IMP_SCREEN_RADAR_GLOW : screenGlow;
				b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(glow, 0.0F, 0.2F, 1.0F, 0.8F);
				b.rect(x + 0.06F, r1 - 0.06F, back + 0.125F, w - 0.12F, 0, 0, 0, -(r1 - r0 - 0.12F), 0);
			}
		}

		b.layer(Layer.SOLID).worldLight().anim(0);
	}

	/** A seat facing -z (towards a desk), on a post. */
	static void chair(MeshBuilder b, int tile) {
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.cylinder(new Vector3f(0, 0, 0), new Vector3f(0, 0.06F, 0), 0.26F, 0.24F, 8, 1.0F, false, true);
		b.cylinder(new Vector3f(0, 0.06F, 0), new Vector3f(0, 0.36F, 0), 0.05F, 0.05F, 6, 1.0F, false, false);
		b.fit(tile);
		b.box(-0.24F, 0.42F, -0.22F, 0.24F, 0.5F, 0.24F);
		b.box(-0.22F, 0.5F, 0.18F, 0.22F, 1.02F, 0.26F);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(-0.04F, 0.36F, -0.04F, 0.04F, 0.42F, 0.04F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);

		for (float s : new float[] {-1.0F, 1.0F}) {
			b.box(s * 0.24F - 0.03F, 0.5F, -0.12F, s * 0.24F + 0.03F, 0.66F, 0.18F);
		}
	}

	/** A railing along x from x0 to x1 (posts every ~1.5 blocks), top rail at {@code height}. */
	static void railing(MeshBuilder b, float x0, float x1, float height, boolean glowStrip) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		float length = x1 - x0;
		int posts = Math.max(1, Math.round(length / 1.5F));

		for (int i = 0; i <= posts; i++) {
			float x = x0 + length * i / posts;
			b.box(x - 0.04F, 0.0F, -0.04F, x + 0.04F, height, 0.04F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		}

		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x0, height, -0.06F, x1, height + 0.07F, 0.06F);
		b.box(x0, height * 0.5F - 0.025F, -0.03F, x1, height * 0.5F + 0.025F, 0.03F);
		// A dark glass panel between the rails.
		b.layer(Layer.GLASS).colourAlpha(0x50FFFFFF).fit(InteriorAtlas.IMP_GLASS).twoSided(true);
		b.rect(x0, height - 0.02F, 0.0F, length, 0, 0, 0, -(height - 0.12F), 0);
		b.twoSided(false).white();

		if (glowStrip) {
			b.layer(Layer.GLOW).fullBright().fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F).colour(0xFFB8C8E8);
			b.rect(x0, height - 0.001F, 0.06F, length, 0, 0, 0, 0, -0.12F);
			b.white();
		}

		b.layer(Layer.SOLID).worldLight();
	}

	/** A ring railing around (cx, cz) at {@code radius}, from y with the top rail at y + height; gaps given in degrees. */
	static void ringRailing(MeshBuilder b, float cx, float y, float cz, float radius, float height, float gapFrom, float gapTo) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		int segments = Math.max(12, Math.round(Mth.TWO_PI * radius / 1.2F));
		List<Float> points = new ArrayList<>();

		for (int i = 0; i <= segments; i++) {
			float a = 360.0F * i / segments;

			if (gapFrom < gapTo && a > gapFrom && a < gapTo) {
				continue;
			}

			points.add(a);
		}

		for (float a : points) {
			float x = cx + Mth.cos(a * Mth.DEG_TO_RAD) * radius;
			float z = cz + Mth.sin(a * Mth.DEG_TO_RAD) * radius;
			b.box(x - 0.04F, y, z - 0.04F, x + 0.04F, y + height, z + 0.04F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		}

		b.tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (int i = 0; i + 1 < points.size(); i++) {
			float a0 = points.get(i);
			float a1 = points.get(i + 1);

			if (a1 - a0 > 360.0F / segments + 0.1F) {
				continue;
			}

			for (float h : new float[] {height, height * 0.5F}) {
				Vector3f p0 = new Vector3f(cx + Mth.cos(a0 * Mth.DEG_TO_RAD) * radius, y + h, cz + Mth.sin(a0 * Mth.DEG_TO_RAD) * radius);
				Vector3f p1 = new Vector3f(cx + Mth.cos(a1 * Mth.DEG_TO_RAD) * radius, y + h, cz + Mth.sin(a1 * Mth.DEG_TO_RAD) * radius);
				b.cylinder(p0, new Vector3f(p1).sub(p0), 0.045F, 0.045F, 5, 1.0F, false, false);
			}
		}
	}

	/** A hologram: a translucent, glowing, slowly turning shape is drawn by the caller; this is the beam under it. */
	static void holoBeam(MeshBuilder b, float x, float y, float z, float r0, float r1, float height, int colour) {
		b.layer(Layer.HOLO).fullBright().colourAlpha(colour).fit(InteriorAtlas.IMP_HOLO).anim(InteriorScene.ANIM_SHIMMER).twoSided(true);
		b.cylinder(new Vector3f(x, y, z), new Vector3f(0, height, 0), r0, r1, 12, 1.0F, false, false);
		b.twoSided(false).white().anim(0).layer(Layer.SOLID).worldLight();
	}

	/** A crate (blocks given), with lids and bands. */
	static void crate(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, int tile) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).fit(tile);
		float inset = 0.02F;

		for (float x = x0; x < x1 - 0.01F; x += 1.0F) {
			for (float y = y0; y < y1 - 0.01F; y += 1.0F) {
				b.rect(x, y + 1, z1 - inset, 1, 0, 0, 0, -1, 0);
				b.rect(x + 1, y + 1, z0 + inset, -1, 0, 0, 0, -1, 0);
			}
		}

		for (float z = z0; z < z1 - 0.01F; z += 1.0F) {
			for (float y = y0; y < y1 - 0.01F; y += 1.0F) {
				b.rect(x0 + inset, y + 1, z, 0, 0, 1, 0, -1, 0);
				b.rect(x1 - inset, y + 1, z + 1, 0, 0, -1, 0, -1, 0);
			}

			for (float x = x0; x < x1 - 0.01F; x += 1.0F) {
				b.rect(x, y1 - inset, z, 1, 0, 0, 0, 0, 1);
			}
		}
	}
}
