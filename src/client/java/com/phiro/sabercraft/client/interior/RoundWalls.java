package com.phiro.sabercraft.client.interior;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.interior.InteriorBuilder;
import com.phiro.sabercraft.interior.InteriorPlan.Doorway;

/**
 * Phase 16: smooth round walls with doorways through them. The wall is open exactly between the
 * sides of each doorway (it used to be open by whole segments, so it ended a little before or
 * after the doorway and one saw behind it), and the doorways are lined where they pass through
 * the wall: their sides and their tops, only where the wall is.
 */
final class RoundWalls {
	private RoundWalls() {
	}

	/** Chooses the look of one piece of wall: its row (0 at the bottom) and its index round the circle. */
	interface Tiles {
		void apply(MeshBuilder b, int row, int index);
	}

	/**
	 * A round wall round (cx, cz) at radius r from y0 to y1, facing in (towards the middle) or out,
	 * from angle a0 to a1 (degrees, 0 = east, 90 = south), {@code segments} pieces to the circle.
	 */
	static void wall(MeshBuilder b, float cx, float cz, float r, int y0, int y1, boolean inward, float a0, float a1, int segments, List<Doorway> doors, Tiles tiles) {
		float step = 360.0F / segments;

		for (int y = y0; y < y1; y++) {
			int h = y - y0;
			List<float[]> parts = new ArrayList<>();
			parts.add(new float[] {a0, a1});

			for (float[] gap : gaps(cx, cz, r, y, doors)) {
				List<float[]> next = new ArrayList<>();

				for (float[] part : parts) {
					if (gap[1] <= part[0] || gap[0] >= part[1]) {
						next.add(part);
						continue;
					}

					if (gap[0] > part[0]) {
						next.add(new float[] {part[0], gap[0]});
					}

					if (gap[1] < part[1]) {
						next.add(new float[] {gap[1], part[1]});
					}
				}

				parts = next;
			}

			for (float[] part : parts) {
				int n = Math.max(1, Math.round((part[1] - part[0]) / step));

				for (int i = 0; i < n; i++) {
					float s0 = Mth.lerp(i / (float) n, part[0], part[1]);
					float s1 = Mth.lerp((i + 1) / (float) n, part[0], part[1]);
					int index = (int) (((s0 + s1) / 2.0F - a0) / step);
					tiles.apply(b, h, index);
					b.arcWall(cx, cz, r, s0, s1, y, y + 1, 1, inward);
				}
			}
		}
	}

	/** The angles (degrees, 0..360) where doorways go through the circle of radius r at row y. */
	static List<float[]> gaps(double cx, double cz, float r, int y, List<Doorway> doors) {
		List<float[]> out = new ArrayList<>();

		for (Doorway door : doors) {
			if (y < door.y0() || y > door.y1()) {
				continue;
			}

			double near = Math.hypot(Math.max(0.0, Math.max(door.x0() - cx, cx - door.x1() - 1)), Math.max(0.0, Math.max(door.z0() - cz, cz - door.z1() - 1)));
			double far = Math.hypot(Math.max(Math.abs(door.x0() - cx), Math.abs(door.x1() + 1 - cx)),
					Math.max(Math.abs(door.z0() - cz), Math.abs(door.z1() + 1 - cz)));

			if (near >= r || far <= r) {
				continue;
			}

			float lo = Float.MAX_VALUE;
			float hi = -Float.MAX_VALUE;
			boolean found = true;

			for (int side = 0; side < 2; side++) {
				double angle;

				if (door.alongX()) {
					double zs = side == 0 ? door.z0() : door.z1() + 1;
					double dz = zs - cz;

					if (Math.abs(dz) >= r) {
						found = false;
						break;
					}

					double dx = Math.sqrt(r * r - dz * dz);
					double mid = (door.x0() + door.x1() + 1) / 2.0 - cx;
					angle = Math.toDegrees(Math.atan2(dz, mid < 0 ? -dx : dx));
				} else {
					double xs = side == 0 ? door.x0() : door.x1() + 1;
					double dx = xs - cx;

					if (Math.abs(dx) >= r) {
						found = false;
						break;
					}

					double dz = Math.sqrt(r * r - dx * dx);
					double mid = (door.z0() + door.z1() + 1) / 2.0 - cz;
					angle = Math.toDegrees(Math.atan2(mid < 0 ? -dz : dz, dx));
				}

				float a = (float) Mth.positiveModulo(angle, 360.0);
				lo = Math.min(lo, a);
				hi = Math.max(hi, a);
			}

			if (!found) {
				continue;
			}

			if (hi - lo > 180.0F) {
				// Across 0 degrees.
				out.add(new float[] {hi, 360.0F});
				out.add(new float[] {0.0F, lo});
			} else {
				out.add(new float[] {lo, hi});
			}
		}

		return out;
	}

	/**
	 * The insides of the doorways through the curved walls: their sides and their tops, only
	 * where the wall is (not out in the rooms on either side).
	 */
	static void linings(MeshBuilder b, List<Doorway> doors, InteriorBuilder.OpenTest open, Runnable sideTile, Runnable topTile) {
		linings(b, doors, open, (x, y, z) -> false, (door, x, z) -> true, sideTile, topTile);
	}

	/** A test on a cell (plot space): whether it is one of a curved wall's cells. */
	interface CellTest {
		boolean test(int x, int y, int z);
	}

	/** Whether a doorway's passage reaches a point beyond its cells (plot space). */
	interface Reach {
		boolean reaches(Doorway door, double x, double z);
	}

	/**
	 * Phase 17: as above; the sides go on beyond the doorway's cells (up to 1.5 blocks along the
	 * way through) inside the curved wall's cells ({@code wallCell}): where the curve passes just
	 * outside a doorway's cells, the passage is open up to it, so its side must be closed there.
	 * Only as far as the passage {@code reach}es (not on through another wall, where the next
	 * doorway's own lining is).
	 */
	static void linings(MeshBuilder b, List<Doorway> doors, InteriorBuilder.OpenTest open, CellTest wallCell, Reach reach, Runnable sideTile,
			Runnable topTile) {
		int res = 16;

		for (Doorway door : doors) {
			boolean ax = door.alongX();
			// Phase 17: a little beyond the doorway's cells along the way through: the curve can pass
			// just outside them, and the passage is open up to it (see FalconInterior.inPassage).
			float d0 = ax ? door.x0() : door.z0();
			float d1 = ax ? door.x1() + 1 : door.z1() + 1;
			float s0 = d0 - 1.5F;
			float s1 = d1 + 1.5F;
			int steps = Math.round((s1 - s0) * res);
			sideTile.run();

			for (int side = 0; side < 2; side++) {
				float plane = ax ? (side == 0 ? door.z0() : door.z1() + 1) : (side == 0 ? door.x0() : door.x1() + 1);
				float out = side == 0 ? -0.05F : 0.05F;
				float in = side == 0 ? Skin.EPS : -Skin.EPS;
				Vector3f facing = ax ? new Vector3f(0, 0, side == 0 ? 1 : -1) : new Vector3f(side == 0 ? 1 : -1, 0, 0);

				for (int y = door.y0(); y <= door.y1(); y++) {
					int from = -1;

					for (int k = 0; k <= steps; k++) {
						boolean closed = false;

						if (k < steps) {
							float t = s0 + (k + 0.5F) / res;
							closed = ax ? !open.open(t, plane + out, y) : !open.open(plane + out, t, y);

							if (closed && (t < d0 || t > d1)) {
								int cx = (int) Math.floor(ax ? t : plane + out);
								int cz = (int) Math.floor(ax ? plane + out : t);
								// The passage reaches the whole step (a step half beyond it was a sliver in
								// the next doorway's own lining).
								float ra = s0 + (float) k / res;
								float rb = s0 + (k + 1.0F) / res;
								closed = wallCell.test(cx, y, cz) && (ax ? reach.reaches(door, ra, plane) && reach.reaches(door, rb, plane)
										: reach.reaches(door, plane, ra) && reach.reaches(door, plane, rb));
							}
						}

						if (closed && from < 0) {
							from = k;
						} else if (!closed && from >= 0) {
							float ta = s0 + (float) from / res;
							float tb = s0 + (float) k / res;
							float p = plane + in;
							b.polyFacing(ax ? List.of(new Vector3f(ta, y, p), new Vector3f(tb, y, p), new Vector3f(tb, y + 1, p), new Vector3f(ta, y + 1, p))
									: List.of(new Vector3f(p, y, ta), new Vector3f(p, y, tb), new Vector3f(p, y + 1, tb), new Vector3f(p, y + 1, ta)), facing);
							from = -1;
						}
					}
				}
			}

			// The top: where the wall (or the corridor's ceiling) is above the doorway, in strips
			// across the doorway so its edge follows the curve.
			int top = door.y1() + 1;
			s0 = d0;
			steps = Math.round((d1 - d0) * res);
			float w0 = ax ? door.z0() : door.x0();
			float w1 = ax ? door.z1() + 1 : door.x1() + 1;
			int strips = Math.round((w1 - w0) * 4);
			float y = top - Skin.EPS;
			topTile.run();

			for (int strip = 0; strip < strips; strip++) {
				float wa = w0 + (w1 - w0) * strip / strips;
				float wb = w0 + (w1 - w0) * (strip + 1) / strips;
				float wm = (wa + wb) / 2.0F;
				int from = -1;

				for (int k = 0; k <= steps; k++) {
					boolean closed = false;

					if (k < steps) {
						float t = s0 + (k + 0.5F) / res;
						closed = ax ? !open.open(t, wm, top) : !open.open(wm, t, top);
					}

					if (closed && from < 0) {
						from = k;
					} else if (!closed && from >= 0) {
						float ta = s0 + (float) from / res;
						float tb = s0 + (float) k / res;
						b.polyFacing(ax ? List.of(new Vector3f(ta, y, wa), new Vector3f(tb, y, wa), new Vector3f(tb, y, wb), new Vector3f(ta, y, wb))
								: List.of(new Vector3f(wa, y, ta), new Vector3f(wb, y, ta), new Vector3f(wb, y, tb), new Vector3f(wa, y, tb)), new Vector3f(0, -1, 0));
						from = -1;
					}
				}
			}
		}
	}
}
