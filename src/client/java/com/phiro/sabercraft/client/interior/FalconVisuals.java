package com.phiro.sabercraft.client.interior;

import static com.phiro.sabercraft.interior.FalconInterior.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;

/**
 * Phase 15: the Millennium Falcon around and inside its walkable rooms (see {@code FalconInterior}).
 *
 * <p>The hull: a round saucer {@value #R} blocks in radius, slightly domed above and below, with a
 * thick rim; two cargo mandibles reaching forward (north) with the gap between them; the cockpit
 * pod on the starboard (east) side with its round, framed window cone; the housing of the
 * boarding ramp; the gun turrets in glass domes above and below the middle, their quad guns; the
 * sensor dish; the wide glowing engine grille along the back. Original shapes after the ship's
 * well-known silhouette.
 */
public final class FalconVisuals implements InteriorVisuals {
	/** The saucer's radius (it encloses every block of the plan but the mandibles and the cockpit). */
	static final float R = 20.6F;
	static final float CXF = (float) CX;
	static final float CZF = (float) CZ;
	/** Heights of the saucer: top and bottom in the middle and at the rim. */
	static final float TOP_MID = 13.3F;
	static final float TOP_RIM = 12.25F;
	static final float BOTTOM_MID = 3.6F;
	static final float BOTTOM_RIM = 4.8F;
	/** The rim: a chamfer above and below a vertical band. */
	static final float RIM_IN = 20.0F;
	static final float BAND_TOP = 11.4F;
	static final float BAND_BOTTOM = 5.6F;

	static final Vector3f SUN = new Vector3f(-0.45F, 0.8F, -0.4F).normalize();
	static final int HULL_TINT = 0xFFE6E2D8;

	@Override
	public String plan() {
		return "falcon";
	}

	@Override
	public int version() {
		return com.phiro.sabercraft.interior.InteriorPlan.of("falcon").version();
	}

	@Override
	public AABB reach() {
		return new AABB(-2, -2, -14, 52, 20, 54);
	}

	@Override
	public void build(MeshBuilder b) {
		b.sun(SUN, 0.55F).fullBright().colour(HULL_TINT).layer(Layer.SOLID);
		saucer(b);
		mandibles(b);
		cockpit(b);
		rampHousing(b);
		turrets(b);
		dish(b);
		engines(b);
		greebles(b);
		b.sun(null, 0.0F).white().worldLight();

		if (ImmersionConfig.get().shipInteriorDetail) {
			FalconRooms.build(b);
		}
	}

	@Override
	public List<SoundSpot> sounds() {
		return List.of(new SoundSpot("ship.hyperdrive_hum", DRIVE_X, FLOOR + 2.5, DRIVE_Z, 0.7F, 16.0F),
				new SoundSpot("ship.falcon_consoles", 43.0, FLOOR + 1.8, 20.0, 0.45F, 9.0F),
				new SoundSpot("ship.falcon_consoles", 26.5, FLOOR + 1.8, 31.5, 0.4F, 9.0F));
	}

	@Override
	public void animate(MeshBuilder b, float seconds) {
		if (ImmersionConfig.get().shipInteriorDetail) {
			FalconRooms.animate(b, seconds);
		}

		// The dish turns slowly.
		b.sun(SUN, 0.55F).fullBright().colour(HULL_TINT).layer(Layer.SOLID);
		b.push().translate(DISH_X, dishY() + 2.6F, DISH_Z).rotateY(seconds * 12.0F).rotateX(-35.0F);
		b.fit(InteriorAtlas.FAL_DISH).twoSided(true);
		b.ring(0, 0, 0, 0.0F, 2.3F, 16, true);
		b.twoSided(false).tile(InteriorAtlas.FAL_METAL, 1.0F);
		b.cylinder(new Vector3f(0, 0, 0), new Vector3f(0, 0.9F, 0), 0.12F, 0.08F, 6, 1.0F, false, true);
		b.pop();
		b.sun(null, 0.0F).white().worldLight();
	}

	// --- The saucer ----------------------------------------------------------------------------

	/** Height of the top of the saucer at a radius from the middle. */
	static float top(float r) {
		if (r >= RIM_IN) {
			return Mth.lerp((r - RIM_IN) / (R - RIM_IN), TOP_RIM, BAND_TOP);
		}

		return Mth.lerp(Math.max(0.0F, r - 4.2F) / (RIM_IN - 4.2F), TOP_MID, TOP_RIM);
	}

	static float bottom(float r) {
		if (r >= RIM_IN) {
			return Mth.lerp((r - RIM_IN) / (R - RIM_IN), BOTTOM_RIM, BAND_BOTTOM);
		}

		return Mth.lerp(Math.max(0.0F, r - 4.4F) / (RIM_IN - 4.4F), BOTTOM_MID, BOTTOM_RIM);
	}

	private static Vector3f at(float angle, float r, float y) {
		return new Vector3f(CXF + Mth.cos(angle * Mth.DEG_TO_RAD) * r, y, CZF + Mth.sin(angle * Mth.DEG_TO_RAD) * r);
	}

	private static void saucer(MeshBuilder b) {
		int segments = 64;
		float[] rings = {4.2F, 8.0F, 12.0F, 16.0F, RIM_IN};

		for (int i = 0; i < segments; i++) {
			float a0 = 360.0F * i / segments;
			float a1 = 360.0F * (i + 1) / segments;

			// The upper surface, in rings of plates.
			for (int k = 0; k + 1 < rings.length; k++) {
				float r0 = rings[k];
				float r1 = rings[k + 1];
				b.tiles(3.0F, 30 + k, InteriorAtlas.FAL_HULL_A, InteriorAtlas.FAL_HULL_B, InteriorAtlas.FAL_HULL_A, InteriorAtlas.FAL_HULL_GREEBLE);
				b.polyFacing(List.of(at(a0, r0, top(r0)), at(a1, r0, top(r0)), at(a1, r1, top(r1)), at(a0, r1, top(r1))), new Vector3f(0, 1, 0));
				b.tiles(3.0F, 40 + k, InteriorAtlas.FAL_HULL_B, InteriorAtlas.FAL_HULL_A, InteriorAtlas.FAL_HULL_GREEBLE);
				b.polyFacing(List.of(at(a0, r0, bottom(r0)), at(a1, r0, bottom(r0)), at(a1, r1, bottom(r1)), at(a0, r1, bottom(r1))),
						new Vector3f(0, -1, 0));
			}

			// The rim: chamfers and the band (vents all round, the engine grille at the back is drawn over it).
			Vector3f out = at((a0 + a1) / 2, 1.0F, 0.0F).sub(CXF, 0, CZF);
			b.tile(InteriorAtlas.FAL_HULL_B, 2.0F);
			b.polyFacing(List.of(at(a0, RIM_IN, TOP_RIM), at(a1, RIM_IN, TOP_RIM), at(a1, R, BAND_TOP), at(a0, R, BAND_TOP)),
					new Vector3f(out).add(0, 1, 0));
			b.polyFacing(List.of(at(a0, RIM_IN, BOTTOM_RIM), at(a1, RIM_IN, BOTTOM_RIM), at(a1, R, BAND_BOTTOM), at(a0, R, BAND_BOTTOM)),
					new Vector3f(out).add(0, -1, 0));
			b.tiles(2.0F, 50, InteriorAtlas.FAL_HULL_GREEBLE, InteriorAtlas.FAL_HULL_VENTS, InteriorAtlas.FAL_HULL_B);
			b.polyFacing(List.of(at(a0, R, BAND_TOP), at(a1, R, BAND_TOP), at(a1, R, BAND_BOTTOM), at(a0, R, BAND_BOTTOM)), out);
		}

		// Trenches: two dark rings in the upper surface, and radial seams.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);

		for (float r : new float[] {8.0F, 15.0F}) {
			for (int i = 0; i < segments; i++) {
				float a0 = 360.0F * i / segments;
				float a1 = 360.0F * (i + 1) / segments;
				b.polyFacing(List.of(at(a0, r - 0.3F, top(r - 0.3F) + 0.02F), at(a1, r - 0.3F, top(r - 0.3F) + 0.02F), at(a1, r + 0.3F, top(r + 0.3F) + 0.02F),
						at(a0, r + 0.3F, top(r + 0.3F) + 0.02F)), new Vector3f(0, 1, 0));
			}
		}

		for (int i = 0; i < 12; i++) {
			float a = i * 30.0F + 15.0F;
			Vector3f p0 = at(a, 4.6F, top(4.6F) + 0.02F);
			Vector3f p1 = at(a, RIM_IN - 0.4F, top(RIM_IN - 0.4F) + 0.02F);
			Vector3f side = new Vector3f(-Mth.sin(a * Mth.DEG_TO_RAD), 0, Mth.cos(a * Mth.DEG_TO_RAD)).mul(0.18F);
			b.polyFacing(List.of(new Vector3f(p0).add(side), new Vector3f(p1).add(side), new Vector3f(p1).sub(side), new Vector3f(p0).sub(side)),
					new Vector3f(0, 1, 0));
		}
	}

	// --- The mandibles -------------------------------------------------------------------------

	/** Where the mandibles end at the front, and how they taper. */
	static final float TIP = -10.0F;

	private static void mandibles(MeshBuilder b) {
		for (float[] x : new float[][] {{7.8F, 17.2F}, {24.8F, 34.2F}}) {
			boolean port = x[0] < CXF;
			float z1 = 22.0F;
			float topTip = 10.4F;
			float bottomTip = 6.8F;
			float zFull = 1.0F;
			float x0 = x[0];
			float x1 = x[1];
			// Outline seen from the side: top and bottom slope down/up to the tip.
			b.tiles(3.0F, port ? 61 : 62, InteriorAtlas.FAL_HULL_A, InteriorAtlas.FAL_HULL_B, InteriorAtlas.FAL_HULL_GREEBLE);
			// Top: sloped front part and flat part.
			b.polyFacing(List.of(new Vector3f(x0, topTip, TIP), new Vector3f(x1, topTip, TIP), new Vector3f(x1, TOP_RIM, zFull), new Vector3f(x0, TOP_RIM, zFull)),
					new Vector3f(0, 1, -0.2F));
			b.polyFacing(List.of(new Vector3f(x0, TOP_RIM, zFull), new Vector3f(x1, TOP_RIM, zFull), new Vector3f(x1, TOP_RIM, z1), new Vector3f(x0, TOP_RIM, z1)),
					new Vector3f(0, 1, 0));
			b.polyFacing(List.of(new Vector3f(x0, bottomTip, TIP), new Vector3f(x1, bottomTip, TIP), new Vector3f(x1, BOTTOM_RIM, zFull),
					new Vector3f(x0, BOTTOM_RIM, zFull)), new Vector3f(0, -1, -0.2F));
			b.polyFacing(List.of(new Vector3f(x0, BOTTOM_RIM, zFull), new Vector3f(x1, BOTTOM_RIM, zFull), new Vector3f(x1, BOTTOM_RIM, z1),
					new Vector3f(x0, BOTTOM_RIM, z1)), new Vector3f(0, -1, 0));
			// The front face, with a dark recess.
			b.tile(InteriorAtlas.FAL_HULL_B, 2.0F);
			b.polyFacing(List.of(new Vector3f(x0, topTip, TIP), new Vector3f(x1, topTip, TIP), new Vector3f(x1, bottomTip, TIP), new Vector3f(x0, bottomTip, TIP)),
					new Vector3f(0, 0, -1));
			b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
			b.box(x0 + 1.2F, bottomTip + 0.8F, TIP - 0.05F, x1 - 1.2F, topTip - 0.8F, TIP + 0.02F, MeshBuilder.NORTH);
			// Both sides: the outline as a polygon (the inner side greebled, the outer plated).
			for (float side : new float[] {x0, x1}) {
				boolean inner = port ? side == x1 : side == x0;
				b.tiles(2.0F, inner ? 63 : 64, inner ? InteriorAtlas.FAL_HULL_GREEBLE : InteriorAtlas.FAL_HULL_A,
						inner ? InteriorAtlas.FAL_HULL_VENTS : InteriorAtlas.FAL_HULL_B);
				b.polyFacing(List.of(new Vector3f(side, topTip, TIP), new Vector3f(side, TOP_RIM, zFull), new Vector3f(side, TOP_RIM, z1),
						new Vector3f(side, BOTTOM_RIM, z1), new Vector3f(side, BOTTOM_RIM, zFull), new Vector3f(side, bottomTip, TIP)),
						new Vector3f(side == x0 ? -1 : 1, 0, 0));
			}

			// Machinery along the inner side of the gap, and a bumper at the tip.
			float inner = port ? x1 : x0;
			float sign = port ? 1.0F : -1.0F;
			b.tile(InteriorAtlas.FAL_METAL, 1.0F);

			for (float z = TIP + 2.0F; z < 9.0F; z += 3.5F) {
				b.box(inner, 7.4F, z, inner + sign * 0.6F, 9.4F, z + 2.2F);
				b.tile(InteriorAtlas.FAL_PIPE_GREY, 1.0F);
				b.cylinder(new Vector3f(inner + sign * 0.35F, 10.3F, z), new Vector3f(0, 0, 3.2F), 0.25F, 0.25F, 6, 2.0F, true, true);
				b.tile(InteriorAtlas.FAL_METAL, 1.0F);
			}

			b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
			b.box(x0 + 0.6F, bottomTip - 0.4F, TIP - 0.6F, x1 - 0.6F, bottomTip + 0.6F, TIP + 1.5F);
		}

		// The front edge of the saucer between the mandibles (the gap).
		b.tile(InteriorAtlas.FAL_HULL_GREEBLE, 2.0F);
	}

	// --- The cockpit ---------------------------------------------------------------------------

	static final float COCKPIT_X = 43.0F;
	static final float COCKPIT_Y = 8.5F;
	static final float COCKPIT_R = 6.2F;
	static final float COCKPIT_BACK = 27.4F;
	static final float COCKPIT_FRONT = 19.0F;
	static final float WINDOW_TIP = 15.6F;
	static final float WINDOW_R = 3.0F;

	private static void cockpit(MeshBuilder b) {
		// The pod: a cylinder along z, closed at the back.
		b.tiles(2.0F, 70, InteriorAtlas.FAL_HULL_A, InteriorAtlas.FAL_HULL_B);
		b.cylinder(new Vector3f(COCKPIT_X, COCKPIT_Y, COCKPIT_BACK), new Vector3f(0, 0, COCKPIT_FRONT - COCKPIT_BACK), COCKPIT_R, COCKPIT_R, 24, 6.0F,
				true, false);
		// The tube from the saucer, under the pod's side.
		b.tile(InteriorAtlas.FAL_HULL_B, 2.0F);
		b.cylinder(new Vector3f(36.0F, 8.0F, 24.0F), new Vector3f(3.5F, 0, 0), 2.95F, 2.95F, 14, 2.0F, false, false);
		// Bands round the pod.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);

		for (float z : new float[] {COCKPIT_BACK - 1.2F, COCKPIT_FRONT + 1.4F}) {
			b.cylinder(new Vector3f(COCKPIT_X, COCKPIT_Y, z), new Vector3f(0, 0, -0.5F), COCKPIT_R + 0.08F, COCKPIT_R + 0.08F, 24, 3.0F, false, false);
		}

		// The window cone: glass between a frame of rings and spokes, a glass cap at the tip.
		b.layer(Layer.GLASS).worldLight().colourAlpha(0x46A0B8D0).fit(InteriorAtlas.FAL_COCKPIT_GLASS).twoSided(true);
		b.cylinder(new Vector3f(COCKPIT_X, COCKPIT_Y, COCKPIT_FRONT), new Vector3f(0, 0, WINDOW_TIP - COCKPIT_FRONT), COCKPIT_R - 0.05F, WINDOW_R, 16, 1.0F,
				false, true);
		b.twoSided(false).layer(Layer.SOLID).fullBright().colour(HULL_TINT).sun(SUN, 0.55F).tile(InteriorAtlas.FAL_METAL, 1.0F);

		for (int i = 0; i < 8; i++) {
			float a = (i * 45.0F + 22.5F) * Mth.DEG_TO_RAD;
			Vector3f p0 = new Vector3f(COCKPIT_X + Mth.cos(a) * COCKPIT_R, COCKPIT_Y + Mth.sin(a) * COCKPIT_R, COCKPIT_FRONT);
			Vector3f p1 = new Vector3f(COCKPIT_X + Mth.cos(a) * WINDOW_R, COCKPIT_Y + Mth.sin(a) * WINDOW_R, WINDOW_TIP);
			b.cylinder(p0, new Vector3f(p1).sub(p0), 0.16F, 0.14F, 5, 1.0F, false, false);
		}

		for (float t : new float[] {0.0F, 0.45F, 1.0F}) {
			float z = Mth.lerp(t, COCKPIT_FRONT, WINDOW_TIP);
			float r = Mth.lerp(t, COCKPIT_R, WINDOW_R);
			List<Float> ring = new ArrayList<>();

			for (int i = 0; i <= 16; i++) {
				float a = i * 22.5F * Mth.DEG_TO_RAD;
				ring.add(COCKPIT_X + Mth.cos(a) * r);
				ring.add(COCKPIT_Y + Mth.sin(a) * r);
				ring.add(z);
			}

			float[] points = new float[ring.size()];

			for (int i = 0; i < points.length; i++) {
				points[i] = ring.get(i);
			}

			b.pipe(0.13F, 5, points);
		}

		// The cockpit's lower half: plated, the window only above.
		b.tiles(2.0F, 71, InteriorAtlas.FAL_HULL_A, InteriorAtlas.FAL_HULL_B);
		List<Vector3f> chin = new ArrayList<>();

		for (int i = 0; i <= 8; i++) {
			float a = (180.0F + i * 22.5F) * Mth.DEG_TO_RAD;
			chin.add(new Vector3f(COCKPIT_X + Mth.cos(a) * (COCKPIT_R + 0.02F), COCKPIT_Y + Mth.sin(a) * (COCKPIT_R + 0.02F), COCKPIT_FRONT));
		}

		for (int i = 0; i + 1 < chin.size(); i++) {
			Vector3f a = chin.get(i);
			Vector3f c = chin.get(i + 1);
			float t = 0.55F;
			Vector3f a2 = new Vector3f(COCKPIT_X, COCKPIT_Y, COCKPIT_FRONT).lerp(new Vector3f(COCKPIT_X, COCKPIT_Y, WINDOW_TIP), t)
					.add(new Vector3f(a).sub(COCKPIT_X, COCKPIT_Y, COCKPIT_FRONT).mul(Mth.lerp(t, 1.0F, WINDOW_R / COCKPIT_R)));
			Vector3f c2 = new Vector3f(COCKPIT_X, COCKPIT_Y, COCKPIT_FRONT).lerp(new Vector3f(COCKPIT_X, COCKPIT_Y, WINDOW_TIP), t)
					.add(new Vector3f(c).sub(COCKPIT_X, COCKPIT_Y, COCKPIT_FRONT).mul(Mth.lerp(t, 1.0F, WINDOW_R / COCKPIT_R)));
			Vector3f mid = new Vector3f(a).add(c).mul(0.5F).sub(COCKPIT_X, COCKPIT_Y, COCKPIT_FRONT);
			b.twoSided(true).polyFacing(List.of(a, c, c2, a2), new Vector3f(mid.x, mid.y, -2.0F)).twoSided(false);
		}
	}

	// --- The ramp housing ----------------------------------------------------------------------

	private static void rampHousing(MeshBuilder b) {
		float x0 = 31.6F;
		float x1 = 38.4F;
		float z0 = 28.6F;
		float z1 = 43.4F;
		float y0 = 0.6F;
		float y1 = TOP_RIM;
		float c = 1.2F;
		// An eight-sided outline seen from above, extruded.
		float[] outline = {x0, z0 + c, x0 + c, z0, x1 - c, z0, x1, z0 + c, x1, z1 - c, x1 - c, z1, x0 + c, z1, x0, z1 - c};
		b.tiles(2.0F, 80, InteriorAtlas.FAL_HULL_A, InteriorAtlas.FAL_HULL_B, InteriorAtlas.FAL_HULL_GREEBLE);
		b.prism(outline, y0, y1, true, true);
		// The hatch at the back end, lit from inside.
		b.layer(Layer.GLOW).fullBright().colour(0xFFFFF0D0).fit(InteriorAtlas.FAL_CEILING_LIGHT_GLOW);
		b.rect(34.0F, 6.0F, z1 + 0.02F, 3.0F, 0, 0, 0, -3.0F, 0);
		// Seen from the ramp: the closed outer hatch, with a lamp strip over it.
		b.layer(Layer.SOLID).worldLight().sun(null, 1.0F).white().fit(InteriorAtlas.FAL_HATCH);
		b.rect(37.0F, 5.8F, z1 - 0.1F, -3.0F, 0, 0, 0, -2.8F, 0);
		b.layer(Layer.GLOW).fullBright().colour(0xFFFFD9A0).fit(InteriorAtlas.FAL_CEILING_LIGHT_GLOW);
		b.rect(36.6F, 6.0F, z1 - 0.12F, -2.2F, 0, 0, 0, -0.12F, 0);
		b.fullBright().sun(SUN, 0.55F);
		b.layer(Layer.SOLID).colour(HULL_TINT).tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(33.7F, 2.7F, z1, 37.3F, 6.3F, z1 + 0.15F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
		// A docking ring on the outer side.
		b.tile(InteriorAtlas.FAL_HULL_GREEBLE, 2.0F);
		b.cylinder(new Vector3f(x1, 7.5F, 35.5F), new Vector3f(0.8F, 0, 0), 3.0F, 2.8F, 16, 2.0F, false, true);
	}

	// --- Turrets, dish, engines ------------------------------------------------------------------

	private static void turrets(MeshBuilder b) {
		for (int s = -1; s <= 1; s += 2) {
			float base = s > 0 ? 12.0F : 4.0F;
			// The collar the dome sits in, closing the hull round it.
			b.tile(InteriorAtlas.FAL_METAL, 1.0F);
			float c0 = s > 0 ? 12.4F : 3.8F;
			// No cap: the gunner looks out over it through the dome.
			b.cylinder(new Vector3f(CXF, c0, CZF), new Vector3f(0, s * 1.2F, 0), 4.45F, 4.1F, 20, 3.0F, false, false);
			// The guns: two pairs of barrels pointing forward, on the dome's sides.
			b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);

			for (int g = -1; g <= 1; g += 2) {
				float gx = CXF + g * 2.4F;
				float gy = base + s * 2.2F;
				b.box(gx - 0.5F, gy - 0.5F, CZF - 2.2F, gx + 0.5F, gy + 0.5F, CZF + 0.3F);

				for (int k = -1; k <= 1; k += 2) {
					b.cylinder(new Vector3f(gx + k * 0.22F, gy, CZF - 2.2F), new Vector3f(0, 0, -3.2F), 0.12F, 0.1F, 6, 1.0F, false, true);
				}
			}

			// The glass dome (seen from both sides).
			b.layer(Layer.GLASS).worldLight().colourAlpha(0x46A0B8D0).fit(InteriorAtlas.FAL_COCKPIT_GLASS).twoSided(true);
			Vector3f centre = new Vector3f(CXF, base, CZF);

			if (s > 0) {
				b.dome(centre, 4.0F, 16, 5, 15.0F, 90.0F);
			} else {
				b.dome(centre, 4.2F, 16, 5, -90.0F, -15.0F);
			}

			b.twoSided(false).layer(Layer.SOLID).fullBright().colour(HULL_TINT).sun(SUN, 0.55F).tile(InteriorAtlas.FAL_METAL, 1.0F);

			// Frame ribs over the dome.
			for (int i = 0; i < 6; i++) {
				float a = i * 60.0F * Mth.DEG_TO_RAD;
				List<Float> pts = new ArrayList<>();

				for (int k = 0; k <= 6; k++) {
					float lat = (s > 0 ? 15.0F + k * 12.5F : -15.0F - k * 12.5F) * Mth.DEG_TO_RAD;
					float r = (s > 0 ? 4.02F : 4.22F);
					pts.add(CXF + Mth.cos(lat) * Mth.cos(a) * r);
					pts.add(base + Mth.sin(lat) * r);
					pts.add(CZF + Mth.cos(lat) * Mth.sin(a) * r);
				}

				float[] p = new float[pts.size()];

				for (int k = 0; k < p.length; k++) {
					p[k] = pts.get(k);
				}

				b.pipe(0.08F, 4, p);
			}
		}
	}

	static final float DISH_X = 31.0F;
	static final float DISH_Z = 20.5F;

	static float dishY() {
		return top(Mth.sqrt((DISH_X - CXF) * (DISH_X - CXF) + (DISH_Z - CZF) * (DISH_Z - CZF)));
	}

	private static void dish(MeshBuilder b) {
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);
		float y = dishY();
		b.cylinder(new Vector3f(DISH_X, y - 0.1F, DISH_Z), new Vector3f(0, 1.2F, 0), 0.9F, 0.5F, 10, 1.0F, false, true);
		b.cylinder(new Vector3f(DISH_X, y + 1.1F, DISH_Z), new Vector3f(0, 1.5F, 0), 0.22F, 0.22F, 6, 1.0F, false, true);
	}

	private static void engines(MeshBuilder b) {
		// The engine grille: a wide glowing band round the back of the rim.
		float a0 = 32.0F;
		float a1 = 148.0F;
		int segments = 40;
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);

		for (int i = 0; i < segments; i++) {
			float s0 = Mth.lerp(i / (float) segments, a0, a1);
			float s1 = Mth.lerp((i + 1) / (float) segments, a0, a1);
			Vector3f out = at((s0 + s1) / 2, 1.0F, 0.0F).sub(CXF, 0, CZF);
			// The housing lips above and below.
			b.polyFacing(List.of(at(s0, R + 0.02F, BAND_TOP - 0.6F), at(s1, R + 0.02F, BAND_TOP - 0.6F), at(s1, R + 0.4F, BAND_TOP - 0.9F),
					at(s0, R + 0.4F, BAND_TOP - 0.9F)), new Vector3f(out).add(0, 1, 0));
			b.polyFacing(List.of(at(s0, R + 0.02F, BAND_BOTTOM + 0.6F), at(s1, R + 0.02F, BAND_BOTTOM + 0.6F), at(s1, R + 0.4F, BAND_BOTTOM + 0.9F),
					at(s0, R + 0.4F, BAND_BOTTOM + 0.9F)), new Vector3f(out).add(0, -1, 0));
		}

		b.layer(Layer.GLOW).fullBright().white().anim(InteriorScene.ANIM_PULSE).fit(InteriorAtlas.FAL_ENGINE_GLOW);

		for (int i = 0; i < segments; i++) {
			float s0 = Mth.lerp(i / (float) segments, a0, a1);
			float s1 = Mth.lerp((i + 1) / (float) segments, a0, a1);
			Vector3f out = at((s0 + s1) / 2, 1.0F, 0.0F).sub(CXF, 0, CZF);
			b.polyFacing(List.of(at(s0, R + 0.3F, BAND_TOP - 0.9F), at(s1, R + 0.3F, BAND_TOP - 0.9F), at(s1, R + 0.3F, BAND_BOTTOM + 0.9F),
					at(s0, R + 0.3F, BAND_BOTTOM + 0.9F)), out);
		}

		b.anim(0).layer(Layer.SOLID).colour(HULL_TINT);
		// Thrust vanes over the grille.
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);

		for (float a = a0 + 4.0F; a < a1; a += 8.0F) {
			Vector3f p = at(a, R + 0.35F, BAND_BOTTOM + 0.9F);
			Vector3f q = at(a, R + 0.35F, BAND_TOP - 0.9F);
			b.cylinder(p, new Vector3f(q).sub(p), 0.07F, 0.07F, 4, 1.0F, false, false);
		}
	}

	/** Boxes and pipes on the upper and lower hull: the busy, patched look. */
	private static void greebles(MeshBuilder b) {
		Random random = new Random(11L);
		b.tile(InteriorAtlas.FAL_HULL_GREEBLE, 1.0F);

		for (int i = 0; i < 160; i++) {
			float a = random.nextFloat() * 360.0F;
			float r = 5.5F + random.nextFloat() * (RIM_IN - 6.5F);
			Vector3f p = at(a, r, 0.0F);

			// Not on the turret ring, not in the dish.
			if (Math.abs(r - 8.0F) < 0.6F || Math.abs(r - 15.0F) < 0.6F || p.distance(DISH_X, 0, DISH_Z) < 2.0F) {
				continue;
			}

			boolean below = i % 3 == 0;
			float y = below ? bottom(r) : top(r);
			float w = 0.4F + random.nextFloat() * 1.4F;
			float d = 0.4F + random.nextFloat() * 1.0F;
			float h = 0.15F + random.nextFloat() * 0.4F;
			b.push().translate(p.x, y, p.z).rotateY(-a + 90.0F);
			b.tile(i % 5 == 0 ? InteriorAtlas.FAL_HULL_VENTS : i % 2 == 0 ? InteriorAtlas.FAL_METAL : InteriorAtlas.FAL_HULL_GREEBLE, 1.0F);

			if (below) {
				b.box(-w / 2, -h, -d / 2, w / 2, 0.05F, d / 2, MeshBuilder.ALL & ~MeshBuilder.UP);
			} else {
				b.box(-w / 2, -0.05F, -d / 2, w / 2, h, d / 2, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			}

			b.pop();
		}

		// Pipes along the upper hull towards the engines.
		b.tile(InteriorAtlas.FAL_PIPE_GREY, 1.0F);

		for (float a : new float[] {60.0F, 75.0F, 105.0F, 120.0F}) {
			Vector3f p0 = at(a, 10.0F, top(10.0F) + 0.18F);
			Vector3f p1 = at(a, 18.0F, top(18.0F) + 0.18F);
			b.cylinder(p0, new Vector3f(p1).sub(p0), 0.18F, 0.18F, 6, 3.0F, true, true);
		}
	}
}
