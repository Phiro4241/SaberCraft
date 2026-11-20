package com.phiro.sabercraft.client.interior;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 29: rays through the drawn rooms of an interior (its detailed model), for the story's film
 * camera ({@code FilmCamera}): where a ray first meets a surface that faces the way it comes from
 * (a wall seen from the room it starts in), and whether a point lies behind a surface (inside a
 * wall, out in the dark beyond it). The rooms' walls are drawn one-sided, so from behind one sees
 * straight through them: a camera there shows the outside of the ship (Phiro's playtest: the
 * takedown's and the boss's cameras did).
 *
 * <p>The solid and glass quads of the model are put into a grid of {@link #CELL}-block cells
 * (world space); a ray walks the cells it crosses and tests only the quads in them.
 */
public final class MeshRays {
	/** The grid's cell size (blocks). */
	static final double CELL = 2.0;
	/** Floats per quad here: 4 corners (x, y, z) and the normal, in world space. */
	private static final int STRIDE = 15;

	private float[] quads = new float[STRIDE * 1024];
	private int count;
	private final Map<Long, int[]> grid = new HashMap<>();
	/** Per quad: the last ray that tested it (each quad once a ray). */
	private int[] stamps = new int[0];
	private int stamp;
	private long buildNanos;

	MeshRays(InteriorScene.Model model) {
		long start = System.nanoTime();
		double ox = model.origin.getX();
		double oy = model.origin.getY();
		double oz = model.origin.getZ();
		Map<Long, int[]> lists = new HashMap<>();
		Map<Long, Integer> sizes = new HashMap<>();

		for (InteriorScene.Cell cell : model.cells) {
			for (MeshBuilder.Layer layer : new MeshBuilder.Layer[] {MeshBuilder.Layer.SOLID, MeshBuilder.Layer.GLASS}) {
				Mesh mesh = cell.meshes.get(layer);

				if (mesh == null) {
					continue;
				}

				for (int q = 0; q < mesh.count; q++) {
					int f = q * Mesh.FLOATS;

					if (this.count * STRIDE + STRIDE > this.quads.length) {
						this.quads = Arrays.copyOf(this.quads, this.quads.length * 2);
					}

					int at = this.count * STRIDE;
					float minX = Float.MAX_VALUE;
					float minY = Float.MAX_VALUE;
					float minZ = Float.MAX_VALUE;
					float maxX = -Float.MAX_VALUE;
					float maxY = -Float.MAX_VALUE;
					float maxZ = -Float.MAX_VALUE;

					for (int v = 0; v < 4; v++) {
						float x = (float) (mesh.floats[f + v * 5] + ox);
						float y = (float) (mesh.floats[f + v * 5 + 1] + oy);
						float z = (float) (mesh.floats[f + v * 5 + 2] + oz);
						this.quads[at + v * 3] = x;
						this.quads[at + v * 3 + 1] = y;
						this.quads[at + v * 3 + 2] = z;
						minX = Math.min(minX, x);
						minY = Math.min(minY, y);
						minZ = Math.min(minZ, z);
						maxX = Math.max(maxX, x);
						maxY = Math.max(maxY, y);
						maxZ = Math.max(maxZ, z);
					}

					this.quads[at + 12] = mesh.floats[f + 20];
					this.quads[at + 13] = mesh.floats[f + 21];
					this.quads[at + 14] = mesh.floats[f + 22];
					int index = this.count++;

					for (int cx = cell(minX); cx <= cell(maxX); cx++) {
						for (int cy = cell(minY); cy <= cell(maxY); cy++) {
							for (int cz = cell(minZ); cz <= cell(maxZ); cz++) {
								long key = key(cx, cy, cz);
								int[] list = lists.get(key);
								int size = sizes.getOrDefault(key, 0);

								if (list == null) {
									list = new int[4];
									lists.put(key, list);
								} else if (size == list.length) {
									list = Arrays.copyOf(list, size * 2);
									lists.put(key, list);
								}

								list[size] = index;
								sizes.put(key, size + 1);
							}
						}
					}
				}
			}
		}

		for (Map.Entry<Long, int[]> entry : lists.entrySet()) {
			this.grid.put(entry.getKey(), Arrays.copyOf(entry.getValue(), sizes.get(entry.getKey())));
		}

		this.stamps = new int[this.count];
		this.buildNanos = System.nanoTime() - start;
	}

	private static int cell(double v) {
		return Mth.floor(v / CELL);
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x1FFFFFL) << 42 | ((long) y & 0x1FFFFFL) << 21 | (long) z & 0x1FFFFFL;
	}

	/** How many quads the rays know. */
	public int quads() {
		return this.count;
	}

	public long buildMillis() {
		return this.buildNanos / 1_000_000L;
	}

	/**
	 * Where (a share of the way, 0..1) the ray from {@code a} to {@code b} first meets a surface
	 * facing back towards {@code a} (a wall seen from the side the ray comes from); 1 when none.
	 * With {@code anyFacing} it stops at a surface facing either way.
	 */
	public double clip(Vec3 a, Vec3 b, boolean anyFacing) {
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (length < 1.0E-6) {
			return 1.0;
		}

		if (++this.stamp == Integer.MAX_VALUE) {
			Arrays.fill(this.stamps, 0);
			this.stamp = 1;
		}

		double best = 1.0;
		// The grid walk (3D DDA) over the cells the segment crosses.
		int x = cell(a.x);
		int y = cell(a.y);
		int z = cell(a.z);
		int endX = cell(b.x);
		int endY = cell(b.y);
		int endZ = cell(b.z);
		int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
		int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
		int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
		double tMaxX = stepX == 0 ? Double.MAX_VALUE : ((stepX > 0 ? (x + 1) * CELL : x * CELL) - a.x) / dx;
		double tMaxY = stepY == 0 ? Double.MAX_VALUE : ((stepY > 0 ? (y + 1) * CELL : y * CELL) - a.y) / dy;
		double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? (z + 1) * CELL : z * CELL) - a.z) / dz;
		double tDeltaX = stepX == 0 ? Double.MAX_VALUE : CELL / Math.abs(dx);
		double tDeltaY = stepY == 0 ? Double.MAX_VALUE : CELL / Math.abs(dy);
		double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : CELL / Math.abs(dz);

		for (int guard = 0; guard < 4096; guard++) {
			int[] list = this.grid.get(key(x, y, z));

			if (list != null) {
				for (int index : list) {
					if (this.stamps[index] == this.stamp) {
						continue;
					}

					this.stamps[index] = this.stamp;
					double t = this.hit(index, a.x, a.y, a.z, dx, dy, dz, anyFacing);

					if (t >= 0.0 && t < best) {
						best = t;
					}
				}
			}

			double next = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

			if (x == endX && y == endY && z == endZ || next > best || next > 1.0) {
				break;
			}

			if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
				x += stepX;
				tMaxX += tDeltaX;
			} else if (tMaxY <= tMaxZ) {
				y += stepY;
				tMaxY += tDeltaY;
			} else {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
		}

		return best;
	}

	/**
	 * Whether that point lies behind a drawn surface: along any of the six axes the nearest surface
	 * within {@code reach} is seen from behind (its drawn side faces away from the point). Inside a
	 * room every nearest surface faces it.
	 */
	public boolean behind(Vec3 point, double reach) {
		double[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

		for (double[] axis : axes) {
			Vec3 to = point.add(axis[0] * reach, axis[1] * reach, axis[2] * reach);
			int[] found = new int[1];
			double t = this.nearest(point, to, found);

			if (t < 1.0) {
				int at = found[0] * STRIDE;
				double facing = this.quads[at + 12] * axis[0] + this.quads[at + 13] * axis[1] + this.quads[at + 14] * axis[2];

				if (facing > 0.05) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * The nearest surface of either facing along the segment (share of the way), its quad in
	 * {@code found[0]}. Of two quads in the same place (a two-sided surface: drawn once each way)
	 * the one facing back along the segment counts: from neither side is one behind such a surface.
	 */
	private double nearest(Vec3 a, Vec3 b, int[] found) {
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		double best = 1.0;
		boolean bestFacesBack = false;
		int minX = cell(Math.min(a.x, b.x));
		int maxX = cell(Math.max(a.x, b.x));
		int minY = cell(Math.min(a.y, b.y));
		int maxY = cell(Math.max(a.y, b.y));
		int minZ = cell(Math.min(a.z, b.z));
		int maxZ = cell(Math.max(a.z, b.z));

		if (++this.stamp == Integer.MAX_VALUE) {
			Arrays.fill(this.stamps, 0);
			this.stamp = 1;
		}

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					int[] list = this.grid.get(key(x, y, z));

					if (list == null) {
						continue;
					}

					for (int index : list) {
						if (this.stamps[index] == this.stamp) {
							continue;
						}

						this.stamps[index] = this.stamp;
						double t = this.hit(index, a.x, a.y, a.z, dx, dy, dz, true);

						if (t < 0.0) {
							continue;
						}

						int q = index * STRIDE;
						boolean facesBack = this.quads[q + 12] * dx + this.quads[q + 13] * dy + this.quads[q + 14] * dz < 0.0;
						double same = 1.0E-4 / Math.max(1.0E-6, Math.sqrt(dx * dx + dy * dy + dz * dz));

						if (t < best - same || Math.abs(t - best) <= same && facesBack && !bestFacesBack) {
							best = t;
							found[0] = index;
							bestFacesBack = facesBack;
						} else if (Math.abs(t - best) <= same && facesBack == bestFacesBack && t < best) {
							best = t;
							found[0] = index;
						}
					}
				}
			}
		}

		return best;
	}

	/**
	 * Where the ray (origin, direction = the whole segment) meets that quad (a share of the
	 * segment), or -1: its two triangles, Möller and Trumbore's test. Only a quad facing the ray
	 * counts unless {@code anyFacing}.
	 */
	private double hit(int index, double ox, double oy, double oz, double dx, double dy, double dz, boolean anyFacing) {
		int at = index * STRIDE;

		if (!anyFacing && this.quads[at + 12] * dx + this.quads[at + 13] * dy + this.quads[at + 14] * dz >= 0.0) {
			return -1.0;
		}

		double t = this.triangle(at, 0, 1, 2, ox, oy, oz, dx, dy, dz);

		if (t < 0.0) {
			t = this.triangle(at, 0, 2, 3, ox, oy, oz, dx, dy, dz);
		}

		return t;
	}

	private double triangle(int at, int i0, int i1, int i2, double ox, double oy, double oz, double dx, double dy, double dz) {
		double v0x = this.quads[at + i0 * 3];
		double v0y = this.quads[at + i0 * 3 + 1];
		double v0z = this.quads[at + i0 * 3 + 2];
		double e1x = this.quads[at + i1 * 3] - v0x;
		double e1y = this.quads[at + i1 * 3 + 1] - v0y;
		double e1z = this.quads[at + i1 * 3 + 2] - v0z;
		double e2x = this.quads[at + i2 * 3] - v0x;
		double e2y = this.quads[at + i2 * 3 + 1] - v0y;
		double e2z = this.quads[at + i2 * 3 + 2] - v0z;
		double px = dy * e2z - dz * e2y;
		double py = dz * e2x - dx * e2z;
		double pz = dx * e2y - dy * e2x;
		double det = e1x * px + e1y * py + e1z * pz;

		if (Math.abs(det) < 1.0E-12) {
			return -1.0;
		}

		double inv = 1.0 / det;
		double sx = ox - v0x;
		double sy = oy - v0y;
		double sz = oz - v0z;
		double u = (sx * px + sy * py + sz * pz) * inv;

		if (u < -1.0E-6 || u > 1.0 + 1.0E-6) {
			return -1.0;
		}

		double qx = sy * e1z - sz * e1y;
		double qy = sz * e1x - sx * e1z;
		double qz = sx * e1y - sy * e1x;
		double v = (dx * qx + dy * qy + dz * qz) * inv;

		if (v < -1.0E-6 || u + v > 1.0 + 1.0E-6) {
			return -1.0;
		}

		double t = (e2x * qx + e2y * qy + e2z * qz) * inv;
		return t >= 0.0 && t <= 1.0 ? t : -1.0;
	}
}
