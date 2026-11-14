package com.phiro.sabercraft.client.interior;

import static com.phiro.sabercraft.interior.DestroyerInterior.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;

/**
 * Phase 15: the Star Destroyer around its walkable rooms (see {@code DestroyerInterior}).
 *
 * <p>The ship: a wedge {@value com.phiro.sabercraft.interior.DestroyerInterior#LENGTH} blocks
 * long and twice {@value com.phiro.sabercraft.interior.DestroyerInterior#HALF_WIDTH} wide at the
 * stern, its upper and lower hull meeting at a rim with a trench of machinery; three stepped
 * superstructure decks, the command tower, the bridge module across its top with the great
 * window, and two shield domes; turbolaser turrets along the rims; the engines glowing at the
 * stern; the hangar opening in the belly. Proportions after the ship's look (a wedge about 0.6
 * times as wide as long), shortened so the whole ship fits in view; all parts are original shapes.
 *
 * <p>The rooms inside: Imperial panelling with light strips, polished black floors, coffered
 * ceilings; consoles and seats in the crew pits, the helm and the officers' stations, the window
 * frames; the conference table with its hologram, the sensor room's screens and tactical table;
 * in the hangar TIE fighters on their racks, gantries, crates and the force field; the flight
 * control room; the reactor with its core, energy rings and catwalk.
 */
public final class DestroyerVisuals implements InteriorVisuals {
	/** Where the light falls from, for the outside of the hull. */
	static final Vector3f SUN = new Vector3f(-0.45F, 0.8F, -0.4F).normalize();

	/** Heights of the hull at the stern: the ridge on top and the keel below. */
	static final float RIM = 3.0F;
	static final float RIDGE_STERN = EQUATOR + 44.0F;
	static final float RIDGE_NOSE = EQUATOR + RIM;
	static final float KEEL_STERN = EQUATOR - 37.0F;
	static final float KEEL_NOSE = EQUATOR - RIM;

	/** The hole in the belly below the hangar's force field. */
	static final float HOLE_X0 = FIELD_X0 - 1;
	static final float HOLE_X1 = FIELD_X1 + 2;
	static final float HOLE_Z0 = FIELD_Z0 - 1;
	static final float HOLE_Z1 = FIELD_Z1 + 2;

	@Override
	public String plan() {
		return "star_destroyer";
	}

	@Override
	public int version() {
		return 2;
	}

	@Override
	public AABB reach() {
		return new AABB(CX - HALF_WIDTH, -10, NOSE, CX + HALF_WIDTH, 200, STERN + 30);
	}

	@Override
	public void build(MeshBuilder b) {
		hull(b);
		superstructure(b);
		bridgeModule(b);
		engines(b);
		turrets(b);

		if (com.phiro.sabercraft.client.ImmersionConfig.get().shipInteriorDetail) {
			DestroyerRooms.build(b);
		}
	}

	@Override
	public List<SoundSpot> sounds() {
		return List.of(new SoundSpot("ship.reactor_hum", REACTOR_X, HANGAR + 4, REACTOR_Z, 0.8F, 28.0F),
				new SoundSpot("ship.imperial_consoles", BRIDGE_X0 + 8, PIT + 1.5, 84.0, 0.5F, 10.0F),
				new SoundSpot("ship.imperial_consoles", BRIDGE_X1 - 8, PIT + 1.5, 86.0, 0.5F, 10.0F),
				new SoundSpot("ship.imperial_consoles", CX + 0.5, DECK + 1.5, WINDOW_Z + 3, 0.4F, 10.0F),
				new SoundSpot("ship.imperial_consoles", 66.0, DECK + 1.5, 107.5, 0.45F, 10.0F),
				new SoundSpot("ship.imperial_consoles", CONTROL_X0 + 6, HANGAR + 1.5, -47.0, 0.45F, 10.0F),
				new SoundSpot("ship.hangar_air", CX + 0.5, HANGAR + 8, -40.0, 0.6F, 40.0F));
	}

	@Override
	public void animate(MeshBuilder b, float seconds) {
		if (com.phiro.sabercraft.client.ImmersionConfig.get().shipInteriorDetail) {
			DestroyerRooms.animate(b, seconds);
		}
	}

	// --- The hull ------------------------------------------------------------------------------

	/** 0 at the nose, 1 at the stern. */
	static float along(float z) {
		return Mth.clamp((z - NOSE) / (float) LENGTH, 0.0F, 1.0F);
	}

	static float halfWidth(float z) {
		return HALF_WIDTH * along(z);
	}

	static float ridge(float z) {
		return Mth.lerp(along(z), RIDGE_NOSE, RIDGE_STERN);
	}

	static float keel(float z) {
		return Mth.lerp(along(z), KEEL_NOSE, KEEL_STERN);
	}

	/** The height of the upper hull's surface at (x, z). */
	static float upper(float x, float z) {
		float hw = Math.max(1.0E-3F, halfWidth(z));
		return Mth.lerp(Mth.clamp(Math.abs(x - CX) / hw, 0.0F, 1.0F), ridge(z), EQUATOR + RIM);
	}

	/** The height of the lower hull's surface at (x, z). */
	static float lower(float x, float z) {
		float hw = Math.max(1.0E-3F, halfWidth(z));
		return Mth.lerp(Mth.clamp(Math.abs(x - CX) / hw, 0.0F, 1.0F), keel(z), EQUATOR - RIM);
	}

	private static void hull(MeshBuilder b) {
		b.sun(SUN, 0.55F).fullBright().layer(Layer.SOLID).white();
		Vector3f nose = new Vector3f(CX, EQUATOR + RIM, NOSE);
		Vector3f noseLow = new Vector3f(CX, EQUATOR - RIM, NOSE);
		Vector3f portTop = new Vector3f(CX - HALF_WIDTH, EQUATOR + RIM, STERN);
		Vector3f starTop = new Vector3f(CX + HALF_WIDTH, EQUATOR + RIM, STERN);
		Vector3f portLow = new Vector3f(CX - HALF_WIDTH, EQUATOR - RIM, STERN);
		Vector3f starLow = new Vector3f(CX + HALF_WIDTH, EQUATOR - RIM, STERN);
		Vector3f ridge = new Vector3f(CX, RIDGE_STERN, STERN);
		Vector3f keel = new Vector3f(CX, KEEL_STERN, STERN);

		// Upper hull: two sloping faces from the rim up to the ridge.
		b.tiles(10.0F, 1, InteriorAtlas.IMP_HULL_A, InteriorAtlas.IMP_HULL_A, InteriorAtlas.IMP_HULL_B, InteriorAtlas.IMP_HULL_C,
				InteriorAtlas.IMP_HULL_A, InteriorAtlas.IMP_HULL_B);
		b.polyFacing(List.of(nose, starTop, ridge), new Vector3f(0.3F, 1.0F, 0.0F));
		b.polyFacing(List.of(nose, ridge, portTop), new Vector3f(-0.3F, 1.0F, 0.0F));

		// Lower hull, with the hole of the hangar shaft cut out.
		b.tiles(10.0F, 2, InteriorAtlas.IMP_HULL_B, InteriorAtlas.IMP_HULL_DARK, InteriorAtlas.IMP_HULL_A, InteriorAtlas.IMP_HULL_C);
		lowerFace(b, List.of(noseLow, keel, starLow), new Vector3f(0.3F, -1.0F, 0.0F));
		lowerFace(b, List.of(noseLow, portLow, keel), new Vector3f(-0.3F, -1.0F, 0.0F));

		// The rim: a band of dense machinery between the two hulls, along both sides.
		b.tile(InteriorAtlas.IMP_HULL_TRENCH, 6.0F);
		b.polyFacing(List.of(nose, noseLow, starLow, starTop), new Vector3f(1.0F, 0.0F, -0.3F));
		b.polyFacing(List.of(nose, portTop, portLow, noseLow), new Vector3f(-1.0F, 0.0F, -0.3F));
		// A lip above and below the trench, catching the light.
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 4.0F);
		lip(b, nose, starTop, 1.0F, 0.6F);
		lip(b, portTop, nose, 1.0F, 0.6F);

		// The stern: a flat wall from the ridge to the keel, the engines stand out of it.
		b.tiles(8.0F, 3, InteriorAtlas.IMP_HULL_DARK, InteriorAtlas.IMP_HULL_TRENCH, InteriorAtlas.IMP_HULL_DARK);
		b.polyFacing(List.of(portTop, ridge, starTop, starLow, keel, portLow), new Vector3f(0.0F, 0.0F, 1.0F));

		// The shaft below the hangar: its walls down from the deck through the hull.
		b.tile(InteriorAtlas.IMP_HULL_DARK, 4.0F);
		float top = HANGAR;
		float bottom = HANGAR - 12.0F;
		b.box(HOLE_X0, bottom, HOLE_Z0, HOLE_X1, top, HOLE_Z0 + 0.01F, MeshBuilder.SOUTH | MeshBuilder.NORTH);
		b.box(HOLE_X0, bottom, HOLE_Z1 - 0.01F, HOLE_X1, top, HOLE_Z1, MeshBuilder.NORTH | MeshBuilder.SOUTH);
		b.box(HOLE_X0, bottom, HOLE_Z0, HOLE_X0 + 0.01F, top, HOLE_Z1, MeshBuilder.EAST | MeshBuilder.WEST);
		b.box(HOLE_X1 - 0.01F, bottom, HOLE_Z0, HOLE_X1, top, HOLE_Z1, MeshBuilder.WEST | MeshBuilder.EAST);
		// Landing lights around the opening.
		b.layer(Layer.GLOW).anim(InteriorScene.ANIM_BLINK).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);

		for (float x = HOLE_X0; x <= HOLE_X1; x += 4.0F) {
			for (float z : new float[] {HOLE_Z0 - 2.0F, HOLE_Z1 + 2.0F}) {
				float y = lower(x, z) - 0.05F;
				b.rect(x - 0.4F, y, z - 0.4F, 0.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.8F);
			}
		}

		b.layer(Layer.SOLID).anim(0);
		greebles(b);
		b.sun(null, 0.0F);
	}

	/** A face of the lower hull, without the part inside the hangar shaft's hole. */
	private static void lowerFace(MeshBuilder b, List<Vector3f> triangle, Vector3f facing) {
		// Four regions around the hole: north of it, south of it, west and east of it.
		float[][] regions = {
				{-1e9F, 1e9F, -1e9F, HOLE_Z0}, {-1e9F, 1e9F, HOLE_Z1, 1e9F}, {-1e9F, HOLE_X0, HOLE_Z0, HOLE_Z1}, {HOLE_X1, 1e9F, HOLE_Z0, HOLE_Z1}};

		for (float[] r : regions) {
			List<Vector3f> part = new ArrayList<>(triangle);
			part = clip(part, 0, r[0], true);
			part = clip(part, 0, r[1], false);
			part = clip(part, 2, r[2], true);
			part = clip(part, 2, r[3], false);

			if (part.size() >= 3) {
				b.polyFacing(part, facing);
			}
		}
	}

	/** Clips a 3D polygon by a vertical plane x = bound or z = bound (axis 0 or 2). */
	static List<Vector3f> clip(List<Vector3f> poly, int axis, float bound, boolean keepAbove) {
		List<Vector3f> out = new ArrayList<>();

		for (int i = 0; i < poly.size(); i++) {
			Vector3f p = poly.get(i);
			Vector3f q = poly.get((i + 1) % poly.size());
			float pv = axis == 0 ? p.x : p.z;
			float qv = axis == 0 ? q.x : q.z;
			boolean pin = keepAbove ? pv >= bound : pv <= bound;
			boolean qin = keepAbove ? qv >= bound : qv <= bound;

			if (pin) {
				out.add(p);
			}

			if (pin != qin) {
				float t = (bound - pv) / (qv - pv);
				out.add(new Vector3f(p).lerp(q, t));
			}
		}

		return out;
	}

	/** A slanted strip along an edge of the rim (a lip of light metal). */
	private static void lip(MeshBuilder b, Vector3f from, Vector3f to, float height, float out) {
		Vector3f along = new Vector3f(to).sub(from);
		Vector3f side = new Vector3f(along.z, 0.0F, -along.x).normalize().mul(out);

		if (side.x * (from.x + to.x - 2 * CX) < 0.0F) {
			side.negate();
		}

		Vector3f a = new Vector3f(from).add(side);
		Vector3f bb = new Vector3f(to).add(side);
		Vector3f facing = new Vector3f(side).normalize().add(0.0F, 0.6F, 0.0F);
		b.polyFacing(List.of(new Vector3f(from).add(0, height, 0), a, bb, new Vector3f(to).add(0, height, 0)), facing);
		b.polyFacing(List.of(a, new Vector3f(from).add(0, -2 * RIM - height, 0), new Vector3f(to).add(0, -2 * RIM - height, 0), bb),
				new Vector3f(side).normalize().add(0.0F, -0.6F, 0.0F));
	}

	/** Boxes, vents and hatches scattered over the upper hull, standing on its slope. */
	private static void greebles(MeshBuilder b) {
		Random random = new Random(1977L);

		for (int i = 0; i < 520; i++) {
			float z = NOSE + 30 + random.nextFloat() * (LENGTH - 40);
			float hw = halfWidth(z);
			float side = random.nextBoolean() ? 1.0F : -1.0F;
			float dx = (0.12F + random.nextFloat() * 0.82F) * hw;
			float x = CX + side * dx;
			float y = upper(x, z);
			// The slope's normal on this side.
			Vector3f n = new Vector3f(side * (ridge(z) - EQUATOR - RIM) / Math.max(1.0F, hw), 1.0F, 0.0F).normalize();
			Vector3f back = new Vector3f(0.0F, 0.0F, 1.0F);
			Vector3f right = new Vector3f(n).cross(back).normalize().negate();
			back = new Vector3f(right).cross(n).normalize();
			float w = 2.0F + random.nextFloat() * 7.0F;
			float d = 2.0F + random.nextFloat() * 10.0F;
			float h = 0.6F + random.nextFloat() * (random.nextInt(6) == 0 ? 5.0F : 2.0F);
			b.push().translate(x, y - 0.2F, z).basis(right, n, back);
			b.tiles(4.0F, i, InteriorAtlas.IMP_HULL_C, InteriorAtlas.IMP_HULL_B, InteriorAtlas.IMP_METAL_LIGHT);
			b.box(-w / 2, 0, -d / 2, w / 2, h, d / 2, MeshBuilder.ALL & ~MeshBuilder.DOWN);

			if (random.nextInt(4) == 0) {
				b.tile(InteriorAtlas.IMP_HULL_DARK, 2.0F);
				b.box(-w / 4, h, -d / 4, w / 4, h + 0.8F, d / 4, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			}

			b.pop();
		}

		// Rows of lit windows along the upper hull, near the rim.
		b.layer(Layer.SOLID);

		for (int s = -1; s <= 1; s += 2) {
			for (float z = NOSE + 120; z < STERN - 20; z += 9.0F) {
				float hw = halfWidth(z);
				float x = CX + s * hw * 0.93F;
				float y = upper(x, z);
				Vector3f n = new Vector3f(s * (ridge(z) - EQUATOR - RIM) / Math.max(1.0F, hw), 1.0F, 0.0F).normalize();
				Vector3f back = new Vector3f(0.0F, 0.0F, 1.0F);
				Vector3f right = new Vector3f(n).cross(back).normalize().negate();
				back = new Vector3f(right).cross(n).normalize();
				b.push().translate(x, y + 0.05F, z).basis(right, n, back);
				b.layer(Layer.SOLID).fit(InteriorAtlas.IMP_HULL_WINDOWS);
				b.rect(-1.5F, 0.0F, -4.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.0F, 8.0F);
				b.layer(Layer.GLOW).fit(InteriorAtlas.IMP_HULL_WINDOWS_GLOW);
				b.rect(-1.5F, 0.02F, -4.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.0F, 8.0F);
				b.pop();
			}
		}

		b.layer(Layer.SOLID);
	}

	// --- The superstructure --------------------------------------------------------------------

	/** One stepped deck: a wedge-shaped block from {@code z0} (narrow, front) to {@code z1} (wide). */
	private static void deck(MeshBuilder b, float z0, float z1, float hw0, float hw1, float y0, float y1, int seed) {
		float[] xz = {CX - hw0, z0, CX + hw0, z0, CX + hw1, z1, CX - hw1, z1};
		b.tiles(6.0F, seed, InteriorAtlas.IMP_HULL_B, InteriorAtlas.IMP_HULL_WINDOWS, InteriorAtlas.IMP_HULL_B, InteriorAtlas.IMP_HULL_C);
		b.prism(xz, y0, y1 - 1.5F, false, false);
		// A slanted cap: the top edge leans in.
		float in = 2.0F;
		float[] top = {CX - hw0 + in, z0 + in, CX + hw0 - in, z0 + in, CX + hw1 - in, z1 - in, CX - hw1 + in, z1 - in};
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 4.0F);

		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			Vector3f a = new Vector3f(xz[i * 2], y1 - 1.5F, xz[i * 2 + 1]);
			Vector3f c = new Vector3f(xz[j * 2], y1 - 1.5F, xz[j * 2 + 1]);
			Vector3f d = new Vector3f(top[j * 2], y1, top[j * 2 + 1]);
			Vector3f e = new Vector3f(top[i * 2], y1, top[i * 2 + 1]);
			Vector3f mid = new Vector3f(a).add(c).mul(0.5F);
			b.polyFacing(List.of(a, c, d, e), new Vector3f(mid.x - CX, 2.0F, mid.z - (z0 + z1) / 2.0F).normalize());
		}

		b.tiles(8.0F, seed + 7, InteriorAtlas.IMP_HULL_A, InteriorAtlas.IMP_HULL_C, InteriorAtlas.IMP_HULL_A);
		List<Vector3f> cap = new ArrayList<>();

		for (int i = 0; i < 4; i++) {
			cap.add(new Vector3f(top[i * 2], y1, top[i * 2 + 1]));
		}

		b.polyFacing(cap, new Vector3f(0.0F, 1.0F, 0.0F));
	}

	private static void superstructure(MeshBuilder b) {
		b.sun(SUN, 0.55F).fullBright().white().layer(Layer.SOLID);
		deck(b, -150, STERN - 8, 26, 86, EQUATOR + 20, 96, 11);
		deck(b, -60, STERN - 12, 20, 60, 90, 107, 12);
		deck(b, 8, STERN - 16, 16, 42, 100, 117, 13);
		// The command tower: a narrow neck up to the bridge module.
		deck(b, 72, 118, 10, 14, 110, 145, 14);

		// Small blocks on the decks: antennae, sensor boxes.
		Random random = new Random(42L);
		b.tiles(3.0F, 5, InteriorAtlas.IMP_HULL_C, InteriorAtlas.IMP_METAL_LIGHT);

		for (int i = 0; i < 60; i++) {
			int level = random.nextInt(3);
			float z0 = level == 0 ? -140 : level == 1 ? -50 : 16;
			float z = z0 + random.nextFloat() * (STERN - 30 - z0);
			float y = level == 0 ? 96 : level == 1 ? 107 : 117;
			float t = (z - z0) / (STERN - z0);
			float hw = level == 0 ? Mth.lerp(t, 24, 84) : level == 1 ? Mth.lerp(t, 18, 58) : Mth.lerp(t, 14, 40);
			float x = CX + (random.nextFloat() * 2 - 1) * (hw - 4);
			float w = 1.5F + random.nextFloat() * 4;
			float h = 1 + random.nextFloat() * 3;
			b.box(x - w / 2, y - 0.1F, z - w / 2, x + w / 2, y + h, z + w / 2, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		}
	}

	// --- The bridge module ---------------------------------------------------------------------

	/** The outside of the bridge module, from the window wall back. */
	static final float MOD_X0 = SIDE_WEST;
	static final float MOD_X1 = SIDE_EAST + 1;
	static final float MOD_Y0 = 143;
	static final float MOD_Y1 = 166;
	static final float MOD_Z0 = WINDOW_Z;
	static final float MOD_Z1 = COMMAND_BACK + 2;

	private static void bridgeModule(MeshBuilder b) {
		b.sun(SUN, 0.55F).fullBright().white().layer(Layer.SOLID);
		float x0 = MOD_X0;
		float x1 = MOD_X1;
		float y0 = MOD_Y0;
		float y1 = MOD_Y1;
		float z0 = MOD_Z0;
		float z1 = MOD_Z1;
		// The window opening in the front.
		float wx0 = BRIDGE_X0 + 1;
		float wx1 = BRIDGE_X1;
		float wy0 = PIT + 1;
		float wy1 = BRIDGE_TOP - 1;
		float chamfer = 4.0F;
		b.tiles(4.0F, 21, InteriorAtlas.IMP_HULL_A, InteriorAtlas.IMP_HULL_B);
		// Front face: around the window.
		frontFrame(b, x0, x1, wy0 - 1, wy1, z0, wx0, wx1, wy0, wy1);
		// Lower chamfer under the window, upper chamfer above it (leaning back).
		b.polyFacing(List.of(new Vector3f(x0, wy0 - 1, z0), new Vector3f(x1, wy0 - 1, z0), new Vector3f(x1, y0, z0 + chamfer),
				new Vector3f(x0, y0, z0 + chamfer)), new Vector3f(0, -1, -1));
		b.polyFacing(List.of(new Vector3f(x0, wy1, z0), new Vector3f(x1, wy1, z0), new Vector3f(x1, y1, z0 + chamfer * 1.5F),
				new Vector3f(x0, y1, z0 + chamfer * 1.5F)), new Vector3f(0, 1, -1));
		// Top and bottom.
		b.tiles(6.0F, 22, InteriorAtlas.IMP_HULL_A, InteriorAtlas.IMP_HULL_C, InteriorAtlas.IMP_HULL_B);
		b.polyFacing(List.of(new Vector3f(x0, y1, z0 + chamfer * 1.5F), new Vector3f(x1, y1, z0 + chamfer * 1.5F), new Vector3f(x1, y1, z1),
				new Vector3f(x0, y1, z1)), new Vector3f(0, 1, 0));
		b.polyFacing(List.of(new Vector3f(x0, y0, z0 + chamfer), new Vector3f(x1, y0, z0 + chamfer), new Vector3f(x1, y0, z1),
				new Vector3f(x0, y0, z1)), new Vector3f(0, -1, 0));
		// Back.
		b.polyFacing(List.of(new Vector3f(x0, y1, z1), new Vector3f(x1, y1, z1), new Vector3f(x1, y0, z1), new Vector3f(x0, y0, z1)),
				new Vector3f(0, 0, 1));
		// Sides, with the windows of the conference room (west) and the sensor room (east).
		float sz0 = SIDE_Z0 + 2;
		float sz1 = SIDE_Z1 - 1;
		float sy0 = DECK + 2;
		float sy1 = COMMAND_TOP - 1;
		sideFace(b, x0, -1.0F, y0, y1, z0, z1, chamfer, sz0, sz1, sy0, sy1);
		sideFace(b, x1, 1.0F, y0, y1, z0, z1, chamfer, sz0, sz1, sy0, sy1);

		// Window frames outside: deep mullions between the panes, and a sill.
		b.tile(InteriorAtlas.IMP_METAL, 2.0F);

		for (float x = wx0; x <= wx1 + 0.01F; x += (wx1 - wx0) / 9.0F) {
			b.box(x - 0.3F, wy0, z0 - 0.8F, x + 0.3F, wy1, z0, MeshBuilder.ALL & ~MeshBuilder.SOUTH);
		}

		for (float y : new float[] {wy0 + 4.0F, wy0 + 9.0F}) {
			b.box(wx0, y - 0.25F, z0 - 0.6F, wx1, y + 0.25F, z0, MeshBuilder.ALL & ~MeshBuilder.SOUTH);
		}

		b.box(wx0 - 0.5F, wy0 - 0.6F, z0 - 1.2F, wx1 + 0.5F, wy0, z0, MeshBuilder.ALL & ~MeshBuilder.SOUTH);

		// The two shield domes on top, on short pedestals.
		b.tile(InteriorAtlas.IMP_DOME, 4.0F);

		for (float dx : new float[] {-26.0F, 26.0F}) {
			Vector3f centre = new Vector3f(CX + dx, y1 + 9.0F, z1 - 14.0F);
			b.dome(centre, 9.0F, 20, 10, -20.0F, 90.0F);
			b.tile(InteriorAtlas.IMP_HULL_B, 2.0F);
			b.cylinder(new Vector3f(centre.x, y1 - 0.1F, centre.z), new Vector3f(0, 6.5F, 0), 3.0F, 2.2F, 10, 2.0F, false, false);
			b.tile(InteriorAtlas.IMP_DOME, 4.0F);
		}

		// A lit strip of windows along the module's sides.
		b.layer(Layer.GLOW).fit(InteriorAtlas.IMP_HULL_WINDOWS_GLOW);

		for (float z = z0 + 8; z < z1 - 4; z += 6) {
			if (z > sz0 - 2 && z < sz1 + 2) {
				continue;
			}

			b.rect(x0 - 0.02F, y1 - 4, z + 5, 0, 0, -5, 0, -1.2F, 0);
			b.rect(x1 + 0.02F, y1 - 4, z, 0, 0, 5, 0, -1.2F, 0);
		}

		b.layer(Layer.SOLID);
		b.sun(null, 0.0F);
	}

	/** The front face of the module with the window cut out. */
	private static void frontFrame(MeshBuilder b, float x0, float x1, float y0, float y1, float z, float wx0, float wx1, float wy0, float wy1) {
		Vector3f north = new Vector3f(0, 0, -1);
		b.polyFacing(List.of(new Vector3f(x0, y1, z), new Vector3f(wx0, y1, z), new Vector3f(wx0, y0, z), new Vector3f(x0, y0, z)), north);
		b.polyFacing(List.of(new Vector3f(wx1, y1, z), new Vector3f(x1, y1, z), new Vector3f(x1, y0, z), new Vector3f(wx1, y0, z)), north);
		b.polyFacing(List.of(new Vector3f(wx0, wy0, z), new Vector3f(wx1, wy0, z), new Vector3f(wx1, y0, z), new Vector3f(wx0, y0, z)), north);
	}

	/** A side of the module: the chamfered front, the window of the side room cut out. */
	private static void sideFace(MeshBuilder b, float x, float sign, float y0, float y1, float z0, float z1, float chamfer, float wz0, float wz1,
			float wy0, float wy1) {
		Vector3f facing = new Vector3f(sign, 0, 0);
		// The outline seen from the side: the front leans with the chamfers.
		float wTop = PIT + 1 + 0.0F;
		List<Vector3f> front = List.of(new Vector3f(x, y1, z0 + chamfer * 1.5F), new Vector3f(x, BRIDGE_TOP - 1, z0), new Vector3f(x, wTop - 1, z0),
				new Vector3f(x, y0, z0 + chamfer), new Vector3f(x, y0, wz0), new Vector3f(x, y1, wz0));
		b.polyFacing(front, facing);
		b.polyFacing(List.of(new Vector3f(x, y1, wz1), new Vector3f(x, y0, wz1), new Vector3f(x, y0, z1), new Vector3f(x, y1, z1)), facing);
		b.polyFacing(List.of(new Vector3f(x, wy0, wz0), new Vector3f(x, y0, wz0), new Vector3f(x, y0, wz1), new Vector3f(x, wy0, wz1)), facing);
		b.polyFacing(List.of(new Vector3f(x, y1, wz0), new Vector3f(x, wy1, wz0), new Vector3f(x, wy1, wz1), new Vector3f(x, y1, wz1)), facing);
	}

	// --- Engines and turrets -------------------------------------------------------------------

	private static void engines(MeshBuilder b) {
		b.sun(SUN, 0.5F).fullBright().white().layer(Layer.SOLID);
		float[][] engines = {{0, EQUATOR + 6, 15}, {-44, EQUATOR + 4, 15}, {44, EQUATOR + 4, 15}, {-82, EQUATOR + 2, 8}, {82, EQUATOR + 2, 8},
				{-22, EQUATOR - 10, 8}, {22, EQUATOR - 10, 8}};

		for (float[] e : engines) {
			Vector3f base = new Vector3f(CX + e[0], e[1], STERN - 2);
			float r = e[2];
			// The housing and the nozzle bell.
			b.tile(InteriorAtlas.IMP_HULL_B, 6.0F);
			b.cylinder(base, new Vector3f(0, 0, r * 0.7F), r * 1.15F, r * 1.1F, 24, 4.0F, false, false);
			b.tile(InteriorAtlas.IMP_ENGINE_BELL, 4.0F);
			Vector3f lip = new Vector3f(base).add(0, 0, r * 0.7F);
			b.push().translate(lip.x, lip.y, lip.z).rotateX(90.0F);
			b.ring(0, 0, 0, r * 0.82F, r * 1.1F, 24, false);
			b.pop();
			// Inside the bell, then the glowing core.
			b.cylinder(new Vector3f(lip).add(0, 0, -r * 0.5F), new Vector3f(0, 0, r * 0.5F), r * 0.7F, r * 0.82F, 24, 4.0F, false, false);
			b.layer(Layer.GLOW).anim(InteriorScene.ANIM_PULSE).fit(InteriorAtlas.IMP_ENGINE_GLOW);
			b.push().translate(lip.x, lip.y, lip.z - r * 0.48F).rotateX(-90.0F);
			b.ring(0, 0, 0, 0, r * 0.74F, 24, true);
			b.pop();
			b.layer(Layer.SOLID).anim(0);
		}

		b.sun(null, 0.0F);
	}

	private static void turrets(MeshBuilder b) {
		b.sun(SUN, 0.5F).fullBright().white().layer(Layer.SOLID);
		Random random = new Random(7L);

		for (int s = -1; s <= 1; s += 2) {
			for (float z = NOSE + 90; z < STERN - 12; z += 22 + random.nextFloat() * 10) {
				float hw = halfWidth(z);
				float x = CX + s * (hw * 0.88F - 2);
				float y = upper(x, z);
				float r = 2.2F + random.nextFloat() * 1.0F;
				b.tile(InteriorAtlas.IMP_DOME, 2.0F);
				b.cylinder(new Vector3f(x, y - 0.5F, z), new Vector3f(0, 1.6F, 0), r * 1.3F, r * 1.2F, 12, 2.0F, false, false);
				b.dome(new Vector3f(x, y + 1.1F, z), r, 12, 4, 0.0F, 90.0F);
				b.tile(InteriorAtlas.IMP_METAL, 2.0F);

				for (float off : new float[] {-0.6F, 0.6F}) {
					b.cylinder(new Vector3f(x + off, y + 1.8F, z - r * 0.4F), new Vector3f(0, 0.3F, -r * 2.2F), 0.3F, 0.25F, 6, 1.0F, false, true);
				}
			}
		}

		b.sun(null, 0.0F);
	}
}
