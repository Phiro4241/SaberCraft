package com.phiro.sabercraft.client.interior;

import org.joml.Vector3f;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;

/**
 * Phase 26: the Schattenklinge's saber as an Inquisitor's lightsaber (after the Grand Inquisitor's
 * in Star Wars Rebels and the Inquisitors' in Obi-Wan Kenobi): an elongated grip in the middle; round
 * it, where the grip ends, a large circular ring on a hub; on the ring two emitters facing apart, and
 * a blade out of each, pointing away from the ring (one lit in the first phase of the duel, both in
 * the second). The ring with its emitters and blades turns round the grip on its hub, by itself:
 * the hand on the grip stays still while the blades whirl round it into a red disc (the spin, the
 * ring's rush, the throw). Not a double-bladed staff twirled by the arm.
 *
 * <p>Body space, as {@link CrewModels}: x to her left, y up from the hip, z forward. A {@link Hold}
 * says where the ring's middle is, which way the grip runs out of it (from the hand to the ring)
 * and which way the first blade points when the ring stands still; the ring's angle turns the
 * blades round the grip from there.
 *
 * <p>The blades glow as the mod's sabers do: a white core, a coloured inner blade, and a soft halo
 * of added light that always faces the camera (it brightens what is behind it, never darkens), a
 * blurred disc where the ring spins fast, and a flare when blades meet. The light they throw on
 * the floor round her comes from {@link StoryModels}.
 */
final class InquisitorSaber {
	/** The ring: its radius (to the middle of its band), the band's half width, its half thickness along the grip. */
	static final float RING_R = 0.17F;
	static final float RING_W = 0.022F;
	static final float RING_T = 0.024F;
	/** Where a blade leaves its emitter (from the ring's middle), and a blade's length. */
	static final float EMIT = RING_R + 0.055F;
	static final float BLADE = 0.92F;
	/** The grip: how far it runs behind the ring's middle (towards the hand) and before it, its radius. */
	static final float GRIP_BACK = 0.27F;
	static final float GRIP_FRONT = 0.045F;
	static final float GRIP_R = 0.027F;
	/** Where the hands hold the grip (behind the ring's middle). */
	static final float HAND = 0.105F;
	static final float HAND_2 = 0.2F;
	/** How fast the ring spins at most (radians a second), and from what speed on its blades blur into a disc. */
	static final float SPIN_FAST = 24.0F;
	static final float BLUR_FROM = 6.0F;

	/** Where the camera is (plot space), set before the people are drawn: the halos face it. */
	static final Vector3f EYE = new Vector3f();

	private InquisitorSaber() {
	}

	/**
	 * Where the saber is (body space): the ring's middle, the grip's direction out of it (from the
	 * hand to the ring: away from her), the first blade's direction while the ring stands still (at
	 * right angles to the grip).
	 */
	record Hold(float[] c, float[] a, float[] d) {
		/** A point on the grip this far behind the ring's middle. */
		float[] grip(float back) {
			return new float[] {this.c[0] - this.a[0] * back, this.c[1] - this.a[1] * back, this.c[2] - this.a[2] * back};
		}

		/** The direction of the first blade with the ring turned by that angle (radians). */
		float[] blade(float angle) {
			float[] e = cross(this.d, this.a);
			float c = Mth.cos(angle);
			float s = Mth.sin(angle);
			return new float[] {this.d[0] * c + e[0] * s, this.d[1] * c + e[1] * s, this.d[2] * c + e[2] * s};
		}

		Hold moved(float x, float y, float z) {
			return new Hold(new float[] {this.c[0] + x, this.c[1] + y, this.c[2] + z}, this.a, this.d);
		}
	}

	/** From a single blade's hold (where its blade begins, and its direction): the ring behind that emitter. */
	static Hold fromEmitter(float[] hilt) {
		float[] d = CrewModels.norm(hilt[3], hilt[4], hilt[5]);
		float[] c = {hilt[0] - d[0] * EMIT, hilt[1] - d[1] * EMIT, hilt[2] - d[2] * EMIT};
		return new Hold(c, gripAxis(c, d), d);
	}

	/** From a held staff's middle and its first blade's direction: the ring's middle there. */
	static Hold centred(float[] staff) {
		float[] d = CrewModels.norm(staff[3], staff[4], staff[5]);
		float[] c = {staff[0], staff[1], staff[2]};
		return new Hold(c, gripAxis(c, d), d);
	}

	/** A hold of its own: the ring's middle, the grip's direction, the blade's direction (made square to the grip). */
	static Hold of(float[] c, float[] a, float[] d) {
		float[] an = CrewModels.norm(a[0], a[1], a[2]);
		float k = dot(d, an);
		float[] dn = CrewModels.norm(d[0] - an[0] * k, d[1] - an[1] * k, d[2] - an[2] * k);
		return new Hold(c, an, dn);
	}

	/**
	 * The grip runs away from her (out of her fist towards the ring), a little up, at right angles
	 * to the blade: with the blade up the ring stands before her fist; with the blades across her
	 * front the ring faces forward like a shield.
	 */
	static float[] gripAxis(float[] c, float[] d) {
		float fx = c[0];
		float fz = c[2];
		float l = Mth.sqrt(fx * fx + fz * fz);

		if (l < 0.05F) {
			fx = 0.0F;
			fz = 1.0F;
		} else {
			fx /= l;
			fz /= l;
		}

		float[] ref = CrewModels.norm(fx, 0.55F, fz);
		float k = dot(ref, d);
		float ax = ref[0] - d[0] * k;
		float ay = ref[1] - d[1] * k;
		float az = ref[2] - d[2] * k;

		if (ax * ax + ay * ay + az * az < 0.04F) {
			// The blade points just where the grip would: the grip goes out to her right instead.
			float[] right = {-1.0F, 0.0F, 0.0F};
			k = dot(right, d);
			ax = right[0] - d[0] * k;
			ay = right[1] - d[1] * k;
			az = right[2] - d[2] * k;
		}

		return CrewModels.norm(ax, ay, az);
	}

	/**
	 * Moves the saber out of her body if a part of it (the grip, the ring, a lit blade, at the
	 * ring's angle now) would be in it: away from her, the way its middle lies from her. While the
	 * ring spins fast the blades sweep a whole disc: then the whole disc is kept out (so that the
	 * saber does not twitch to and fro with every half turn).
	 */
	static Hold clear(Hold hold, float angle, boolean second, float shoulder, boolean spinning) {
		float rx = hold.c()[0];
		float rz = hold.c()[2];
		float length = Mth.sqrt(rx * rx + rz * rz);

		if (length < 0.05F) {
			rx = 0.0F;
			rz = 1.0F;
		} else {
			rx /= length;
			rz /= length;
		}

		Hold h = hold;

		for (int i = 0; i < 80 && (spinning ? discInBody(h, second, shoulder) : inBody(h, angle, second, shoulder)); i++) {
			h = h.moved(rx * 0.025F, 0.0F, rz * 0.025F);
		}

		return h;
	}

	/** Whether the saber lies in her body at any angle of its ring (eight looked at). */
	static boolean discInBody(Hold h, boolean second, float shoulder) {
		for (int i = 0; i < 8; i++) {
			if (inBody(h, Mth.TWO_PI * i / 8.0F, second, shoulder)) {
				return true;
			}
		}

		return false;
	}

	/** Whether a part of the saber (grip, ring, the first blade and, if lit, the second) lies in her body. */
	static boolean inBody(Hold h, float angle, boolean second, float shoulder) {
		float[] c = h.c();
		float[] a = h.a();

		for (float t = -GRIP_BACK; t <= GRIP_FRONT; t += 0.05F) {
			if (CrewModels.inBody(c[0] + a[0] * t, c[1] + a[1] * t, c[2] + a[2] * t, shoulder)) {
				return true;
			}
		}

		for (int i = 0; i < 12; i++) {
			float[] r = h.blade(angle + Mth.TWO_PI * i / 12.0F);

			if (CrewModels.inBody(c[0] + r[0] * RING_R, c[1] + r[1] * RING_R, c[2] + r[2] * RING_R, shoulder)) {
				return true;
			}
		}

		float[] b = h.blade(angle);

		for (int side = 1; side >= (second ? -1 : 1); side -= 2) {
			for (float t = EMIT; t <= EMIT + BLADE + 0.001F; t += 0.05F) {
				if (CrewModels.inBody(c[0] + b[0] * t * side, c[1] + b[1] * t * side, c[2] + b[2] * t * side, shoulder)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Draws it (in the builder's current space, body space): the grip, the hub, the ring turned by
	 * {@code angle} with its emitters, and the blades: the first lit as far as {@code first} (0..1),
	 * the second as far as {@code second}; {@code blur} 0..1 how fast the ring spins (a disc of
	 * light), {@code flare} 0..1 the blades flaring where they met another.
	 */
	static void draw(MeshBuilder b, Hold h, float angle, float first, float second, int colour, float blur, float flare) {
		float[] c = h.c();
		float[] a = h.a();
		float[] d = h.d();
		float[] e = cross(d, a);
		b.push().translate(c[0], c[1], c[2]);
		// Local space: x the first blade at rest, y along the grip (towards the ring's front), z across.
		b.basis(new Vector3f(d[0], d[1], d[2]), new Vector3f(a[0], a[1], a[2]), new Vector3f(e[0], e[1], e[2]));
		hilt(b, angle);
		b.rotateY(-angle * Mth.RAD_TO_DEG);

		if (first > 0.0F) {
			blade(b, EMIT, EMIT + BLADE * first, colour, flare);
		}

		if (second > 0.0F) {
			blade(b, -EMIT, -EMIT - BLADE * second, colour, flare);
		}

		if (blur > 0.0F && first > 0.0F) {
			disc(b, colour, blur, second > 0.0F);
		}

		b.pop();
		b.layer(Layer.SOLID).worldLight().white().twoSided(false).noLight();
	}

	/**
	 * The hilt (local space: the ring flat in x/z, the grip along y): the grip with its bands and
	 * pommel, the hub at the ring's middle, the ring turned by {@code angle} with its two spokes and
	 * the emitters' shrouds (their glowing mouths), notches round its rim to see it turn.
	 */
	static void hilt(MeshBuilder b, float angle) {
		b.layer(Layer.SOLID).worldLight().white().twoSided(false).noLight();
		// The grip: black, ridged, a metal pommel at the hand's end, a collar where it meets the hub.
		b.fit(InteriorAtlas.IMP_BLACK);
		b.cylinder(new Vector3f(0.0F, -GRIP_BACK, 0.0F), new Vector3f(0.0F, GRIP_BACK + GRIP_FRONT, 0.0F), GRIP_R, GRIP_R, 8, 1.0F, true, true);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (float y : new float[] {-0.235F, -0.15F, -0.065F}) {
			b.cylinder(new Vector3f(0.0F, y, 0.0F), new Vector3f(0.0F, 0.018F, 0.0F), GRIP_R + 0.005F, GRIP_R + 0.005F, 8, 1.0F, false, false);
		}

		b.cylinder(new Vector3f(0.0F, -GRIP_BACK - 0.02F, 0.0F), new Vector3f(0.0F, 0.03F, 0.0F), GRIP_R + 0.008F, GRIP_R + 0.004F, 8, 1.0F, true, true);
		// The hub (it turns with the ring; the grip stays).
		b.push().rotateY(-angle * Mth.RAD_TO_DEG);
		b.cylinder(new Vector3f(0.0F, -RING_T, 0.0F), new Vector3f(0.0F, RING_T * 2.0F, 0.0F), 0.045F, 0.045F, 10, 1.0F, true, true);
		// Two spokes from the hub to the ring, at right angles to the blades.
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.012F, -0.012F, 0.04F, 0.012F, 0.012F, RING_R - RING_W + 0.004F);
		b.box(-0.012F, -0.012F, -RING_R + RING_W - 0.004F, 0.012F, 0.012F, -0.04F);
		// The ring: a flat band of black segments, a metal rim on its outer edge.
		int segments = 20;

		for (int i = 0; i < segments; i++) {
			float a0 = Mth.TWO_PI * i / segments;
			float a1 = Mth.TWO_PI * (i + 1) / segments;
			b.fit(InteriorAtlas.IMP_BLACK);
			band(b, a0, a1, RING_R - RING_W, RING_R + RING_W * 0.4F, RING_T);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			band(b, a0, a1, RING_R + RING_W * 0.4F, RING_R + RING_W, RING_T * 0.75F);
		}

		// Notches on the rim, every 60 degrees (with the emitters they show the ring turning).
		b.fit(InteriorAtlas.IMP_METAL);

		for (int i = 1; i < 6; i++) {
			if (i == 3) {
				continue;
			}

			float m = Mth.TWO_PI * i / 6.0F;
			b.push().rotateY(-m * Mth.RAD_TO_DEG);
			b.box(RING_R + RING_W - 0.004F, -RING_T * 0.6F, -0.012F, RING_R + RING_W + 0.012F, RING_T * 0.6F, 0.012F);
			b.pop();
		}

		// The emitters, facing apart on the ring: a shroud, a band, the mouth glowing.
		for (int side = 1; side >= -1; side -= 2) {
			float x0 = side * (RING_R - 0.01F);
			float x1 = side * (EMIT - 0.006F);
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(Math.min(x0, x1), -0.03F, -0.03F, Math.max(x0, x1), 0.03F, 0.03F);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(side * (RING_R + 0.018F), -0.034F, -0.034F, side * (RING_R + 0.03F), 0.034F, 0.034F);
			b.layer(Layer.GLOW).fullBright().colour(0xFFFF6A50).fit(InteriorAtlas.WHITE);
			b.box(side * (EMIT - 0.008F), -0.016F, -0.016F, side * (EMIT - 0.002F), 0.016F, 0.016F);
			b.layer(Layer.SOLID).worldLight().white();
		}

		b.pop();
	}

	/** A piece of the ring's band (local x/z, from angle a0 to a1, radii r0 to r1, half thickness t along y). */
	private static void band(MeshBuilder b, float a0, float a1, float r0, float r1, float t) {
		float c0 = Mth.cos(a0);
		float s0 = Mth.sin(a0);
		float c1 = Mth.cos(a1);
		float s1 = Mth.sin(a1);
		Vector3f i0t = new Vector3f(c0 * r0, t, s0 * r0);
		Vector3f i1t = new Vector3f(c1 * r0, t, s1 * r0);
		Vector3f o0t = new Vector3f(c0 * r1, t, s0 * r1);
		Vector3f o1t = new Vector3f(c1 * r1, t, s1 * r1);
		Vector3f i0b = new Vector3f(c0 * r0, -t, s0 * r0);
		Vector3f i1b = new Vector3f(c1 * r0, -t, s1 * r0);
		Vector3f o0b = new Vector3f(c0 * r1, -t, s0 * r1);
		Vector3f o1b = new Vector3f(c1 * r1, -t, s1 * r1);
		// Top and bottom faces, outer and inner rims.
		b.polyFacing(java.util.List.of(i0t, i1t, o1t, o0t), new Vector3f(0.0F, 1.0F, 0.0F));
		b.polyFacing(java.util.List.of(i0b, o0b, o1b, i1b), new Vector3f(0.0F, -1.0F, 0.0F));
		Vector3f out = new Vector3f(Mth.cos((a0 + a1) * 0.5F), 0.0F, Mth.sin((a0 + a1) * 0.5F));
		b.polyFacing(java.util.List.of(o0t, o1t, o1b, o0b), out);
		b.polyFacing(java.util.List.of(i0t, i0b, i1b, i1t), new Vector3f(out).negate());
	}

	/**
	 * A blade along local x from {@code from} to {@code to}: the white core, the coloured blade
	 * round it, and two soft halos of added light facing the camera (as the mod's sabers glow).
	 */
	static void blade(MeshBuilder b, float from, float to, int colour, float flare) {
		float lo = Math.min(from, to);
		float hi = Math.max(from, to);
		float out = Math.signum(to - from);
		float core = 0.016F + 0.006F * flare;
		// The core: white hot, a little rounded at the tip.
		b.layer(Layer.GLOW).fullBright().colour(0xFFFFFFFF).fit(InteriorAtlas.WHITE).twoSided(false);
		b.box(lo, -core, -core, hi, core, core);
		// The blade's colour round the core (see-through at its edge).
		b.layer(Layer.HOLO).fullBright().colourAlpha(0xC8000000 | colour & 0xFFFFFF).twoSided(true);
		b.box(Math.min(from - out * 0.008F, to + out * 0.025F), -0.034F, -0.034F, Math.max(from - out * 0.008F, to + out * 0.025F), 0.034F, 0.034F);
		// The halos: added light, brightest along the blade, fading out to the side and past the tip.
		halo(b, new Vector3f(from, 0.0F, 0.0F), new Vector3f(to, 0.0F, 0.0F), 0.075F + 0.03F * flare, scale(colour, 0.95F + 0.3F * flare));
		halo(b, new Vector3f(from, 0.0F, 0.0F), new Vector3f(to, 0.0F, 0.0F), 0.24F + 0.08F * flare, scale(colour, 0.42F + 0.25F * flare));
		b.twoSided(false);
	}

	/**
	 * A soft halo of added light round the line from {@code s} to {@code e} (local space): a flat
	 * strip turned towards the camera, full on the line, nothing at {@code width} from it (also
	 * past its ends).
	 */
	static void halo(MeshBuilder b, Vector3f s, Vector3f e, float width, int colour) {
		Vector3f eye = b.toLocal(EYE);
		Vector3f u = new Vector3f(e).sub(s);
		float length = u.length();

		if (length < 1.0E-4F) {
			return;
		}

		u.div(length);
		Vector3f mid = new Vector3f(s).add(e).mul(0.5F);
		Vector3f view = new Vector3f(eye).sub(mid);
		Vector3f side = new Vector3f(u).cross(view);

		if (side.lengthSquared() < 1.0E-8F) {
			side = Math.abs(u.y) < 0.9F ? new Vector3f(u).cross(0.0F, 1.0F, 0.0F) : new Vector3f(u).cross(1.0F, 0.0F, 0.0F);
		}

		side.normalize().mul(width);
		Vector3f along = new Vector3f(u).mul(width);
		Vector3f[] rows = {new Vector3f(s).sub(along), new Vector3f(s), new Vector3f(e), new Vector3f(e).add(along)};
		Vector3f[] cols = {new Vector3f(side).negate(), new Vector3f(), new Vector3f(side)};
		b.layer(Layer.LIGHT).fullBright().colour(colour).fit(InteriorAtlas.WHITE).twoSided(true).lightFrom(s, e, 0.0F, width);

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 2; j++) {
				Vector3f o = new Vector3f(rows[i]).add(cols[j]);
				b.rect(o, new Vector3f(cols[j + 1]).sub(cols[j]), new Vector3f(rows[i + 1]).sub(rows[i]));
			}
		}

		b.noLight();
	}

	/**
	 * The spinning blades blurred into a disc of light (local x/z, round the ring): stronger the
	 * faster the ring spins; a ring of brighter light where the blades' cores sweep.
	 */
	static void disc(MeshBuilder b, int colour, float blur, boolean both) {
		float k = Mth.clamp(blur, 0.0F, 1.0F) * (both ? 1.0F : 0.6F);
		int segments = 28;
		b.layer(Layer.LIGHT).fullBright().fit(InteriorAtlas.WHITE).twoSided(true).noLight();

		for (int pass = 0; pass < 2; pass++) {
			float r0 = pass == 0 ? EMIT : EMIT + BLADE * 0.15F;
			float r1 = pass == 0 ? EMIT + BLADE : EMIT + BLADE * 0.85F;
			b.colour(scale(colour, k * (pass == 0 ? 0.22F : 0.18F)));

			for (int i = 0; i < segments; i++) {
				float a0 = Mth.TWO_PI * i / segments;
				float a1 = Mth.TWO_PI * (i + 1) / segments;
				Vector3f i0 = new Vector3f(Mth.cos(a0) * r0, 0.0F, Mth.sin(a0) * r0);
				Vector3f i1 = new Vector3f(Mth.cos(a1) * r0, 0.0F, Mth.sin(a1) * r0);
				Vector3f o0 = new Vector3f(Mth.cos(a0) * r1, 0.0F, Mth.sin(a0) * r1);
				Vector3f o1 = new Vector3f(Mth.cos(a1) * r1, 0.0F, Mth.sin(a1) * r1);
				b.polyFacing(java.util.List.of(i0, i1, o1, o0), new Vector3f(0.0F, 1.0F, 0.0F));
			}
		}

		b.twoSided(false);
	}

	/**
	 * Its hilt carried on her back (before the duel): the ring flat against her back between the
	 * shoulder blades, the grip pointing back out of it.
	 */
	static void carried(MeshBuilder b, float shoulder) {
		b.push().translate(0.0F, shoulder - 0.3F, -CrewModels.HALF_D - RING_T - 0.012F);
		// Local: x across her back, y (the grip's front) into her back, z down.
		b.basis(new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(0.0F, -1.0F, 0.0F));
		hilt(b, 0.6F);
		b.pop();
	}

	/**
	 * In flight (thrown, plot space): the ring spinning flat with both blades lit (a disc of light),
	 * its grip upright through the middle.
	 */
	static void flying(MeshBuilder b, float x, float y, float z, float yaw, float spin, int colour) {
		b.push().translate(x, y, z).rotateY(-yaw).rotateX(-12.0F);
		hilt(b, spin * Mth.DEG_TO_RAD);
		b.rotateY(-spin);
		blade(b, EMIT, EMIT + BLADE, colour, 0.0F);
		blade(b, -EMIT, -EMIT - BLADE, colour, 0.0F);
		disc(b, colour, 0.85F, true);
		b.pop();
		b.layer(Layer.SOLID).worldLight().white().twoSided(false).noLight();
	}

	/**
	 * Struck from her hand (plot space), unlit: tumbling as it flies ({@code pitch} going round),
	 * at rest (pitch 90) lying on its ring, the grip up out of it.
	 */
	static void lying(MeshBuilder b, float x, float y, float z, float yaw, float pitch) {
		b.push().translate(x, y + GRIP_FRONT + 0.006F, z).rotateY(-yaw).rotateX(pitch + 90.0F);
		hilt(b, 0.0F);
		b.pop();
		b.layer(Layer.SOLID).worldLight().white().twoSided(false).noLight();
	}

	private static int scale(int colour, float k) {
		return Mesh.scale(colour | 0xFF000000, Mth.clamp(k, 0.0F, 1.5F), false);
	}

	static float[] cross(float[] p, float[] q) {
		return new float[] {p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0]};
	}

	static float dot(float[] p, float[] q) {
		return p[0] * q[0] + p[1] * q[1] + p[2] * q[2];
	}
}
