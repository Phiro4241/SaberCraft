package com.phiro.sabercraft.client.interior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.joml.Vector3f;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;

/**
 * Phase 16: finds the causes of flickering in an interior model. Two opaque surfaces that lie in
 * the same plane (closer than the depth buffer can tell apart) and overlap fight over every pixel
 * ("z-fighting"); so does a surface lying on the visible face of a block. This builds the model
 * once, compares every opaque quad with the ones in nearly the same plane and with the blocks
 * behind it, and lists where it happens. Used by the tests; costs nothing in play.
 */
public final class MeshAudit {
	/** Closer than this (blocks) counts as the same plane. */
	static final float SAME_PLANE = 0.0025F;

	private MeshAudit() {
	}

	/** Blocks of the plot, in plot space. */
	public interface Blocks3 {
		BlockState get(int x, int y, int z);
	}

	private record Quad(Layer layer, Vector3f[] p, Vector3f n, float d, Vector3f centre, boolean twoSided) {
	}

	/**
	 * Lists every place where opaque surfaces of the plan's model fight with each other or with a
	 * block face: "x,y,z what" per place, at most {@code limit}, after the count.
	 */
	public static List<String> report(String plan, Blocks3 blocks, int limit) {
		String trace = System.getProperty("sabercraft.audit.trace");
		List<String> traces = new ArrayList<>();
		InteriorVisuals visuals = InteriorScene.visuals(plan);
		List<Quad> quads = new ArrayList<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			if (layer != Layer.SOLID) {
				return;
			}

			Vector3f[] p = new Vector3f[4];

			for (int v = 0; v < 4; v++) {
				p[v] = new Vector3f(xyzuv[v * 5], xyzuv[v * 5 + 1], xyzuv[v * 5 + 2]);
			}

			Vector3f n = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));

			if (n.lengthSquared() < 1.0E-10F) {
				n = new Vector3f(p[2]).sub(p[0]).cross(new Vector3f(p[3]).sub(p[0]));
			}

			if (n.lengthSquared() < 1.0E-10F) {
				return;
			}

			n.normalize();

			// Face the way the stored normal says.
			if (n.x * nx + n.y * ny + n.z * nz < 0) {
				n.negate();
			}

			Vector3f c = new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]).mul(0.25F);
			quads.add(new Quad(layer, p, n, n.dot(p[0]), c, false));

			// For finding where a quad comes from: the calls that made the quads at one place.
			if (trace != null && trace.equals(place(c))) {
				StringBuilder from = new StringBuilder();

				for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
					String cls = e.getClassName();

					if (cls.startsWith("com.phiro.sabercraft.client.interior.") && !cls.endsWith("MeshBuilder") && !cls.endsWith("MeshAudit")) {
						from.append(' ').append(cls.substring(cls.lastIndexOf('.') + 1)).append(':').append(e.getLineNumber());
					}
				}

				traces.add(place(c) + " n=" + fmt(n) + " " + sizeOf(p) + " from" + from);
			}
		});
		visuals.build(builder);

		Map<Long, List<Quad>> buckets = new HashMap<>();

		for (Quad q : quads) {
			buckets.computeIfAbsent(key(q.n, q.d), unused -> new ArrayList<>()).add(q);
		}

		TreeMap<String, Integer> places = new TreeMap<>();

		for (Quad a : quads) {
			// Other surfaces in the same plane.
			for (int dd = -1; dd <= 1; dd++) {
				List<Quad> list = buckets.get(key(a.n, a.d + dd * 0.01F));

				if (list == null) {
					continue;
				}

				for (Quad b : list) {
					if (b == a || System.identityHashCode(b) < System.identityHashCode(a)) {
						continue;
					}

					if (a.n.dot(b.n) < 0.9995F || Math.abs(a.d - b.d) > SAME_PLANE) {
						continue;
					}

					if (overlap(a, b)) {
						places.merge(place(a.centre) + " model/model n=" + fmt(a.n) + " d=" + String.format(java.util.Locale.ROOT, "%.3f", a.d) + " " + size(a) + "&" + size(b), 1, Integer::sum);
					}
				}
			}

			// A block face right behind the surface.
			if (blocks != null && axis(a.n)) {
				float behind = Math.abs(a.n.x) > 0.5F ? a.p[0].x : Math.abs(a.n.y) > 0.5F ? a.p[0].y : a.p[0].z;

				if (Math.abs(behind - Math.round(behind)) < SAME_PLANE) {
					int fx = Mth.floor(a.centre.x + a.n.x * 0.05F);
					int fy = Mth.floor(a.centre.y + a.n.y * 0.05F);
					int fz = Mth.floor(a.centre.z + a.n.z * 0.05F);
					int bx = Mth.floor(a.centre.x - a.n.x * 0.05F);
					int by = Mth.floor(a.centre.y - a.n.y * 0.05F);
					int bz = Mth.floor(a.centre.z - a.n.z * 0.05F);
					BlockState back = blocks.get(bx, by, bz);
					BlockState front = blocks.get(fx, fy, fz);

					if (visible(back) && !front.canOcclude()) {
						places.merge(place(a.centre) + " model/block " + back.getBlock().getName().getString() + " n=" + fmt(a.n) + " " + size(a), 1, Integer::sum);
					}
				}
			}
		}

		List<String> out = new ArrayList<>();
		int total = places.values().stream().mapToInt(Integer::intValue).sum();
		out.add(total + " fighting quad pairs at " + places.size() + " places (" + quads.size() + " opaque quads)");

		for (Map.Entry<String, Integer> entry : places.entrySet()) {
			if (out.size() > limit) {
				break;
			}

			out.add(entry.getKey() + " x" + entry.getValue());
		}

		for (String t : traces) {
			out.add("trace " + t);
		}

		return out;
	}

	/** Phase 19: a surface of the model near a point: which point, how far, which way it faces, its bounds, where it was made. */
	public record Near(int point, Layer layer, float distance, Vector3f normal, Vector3f min, Vector3f max, boolean front, String from) {
		/** Whether the surface lies flat (floor or ceiling) and right over or under the point. */
		public boolean across(Vector3f eye) {
			return Math.abs(this.normal.y) > 0.9F && eye.x > this.min.x && eye.x < this.max.x && eye.z > this.min.z && eye.z < this.max.z;
		}

		@Override
		public String toString() {
			return "point#" + this.point + " " + this.layer + (this.front ? "" : " (behind)") + String.format(java.util.Locale.ROOT, " d=%.3f", this.distance)
					+ " n=" + fmt(this.normal) + " " + sizeOf(new Vector3f[] {this.min, this.max}) + " from" + this.from;
		}
	}

	/**
	 * Phase 19: what of the model lies closer than {@code radius} to any of the points (the eye on a
	 * way through the ship: a surface right in front of the camera, or cut by its near plane, looks
	 * as if one stood inside a model), nearest first per point; {@code behindToo} counts the side
	 * behind a one-sided quad as well (that side is not drawn).
	 */
	public static List<Near> near(String plan, List<Vector3f> points, float radius, boolean behindToo) {
		InteriorVisuals visuals = InteriorScene.visuals(plan);
		List<Near> found = new ArrayList<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			Vector3f[] p = new Vector3f[4];
			Vector3f min = new Vector3f(Float.MAX_VALUE);
			Vector3f max = new Vector3f(-Float.MAX_VALUE);

			for (int v = 0; v < 4; v++) {
				p[v] = new Vector3f(xyzuv[v * 5], xyzuv[v * 5 + 1], xyzuv[v * 5 + 2]);
				min.min(p[v]);
				max.max(p[v]);
			}

			Vector3f n = new Vector3f(nx, ny, nz);

			for (int i = 0; i < points.size(); i++) {
				Vector3f eye = points.get(i);
				float d1 = distance(eye, p[0], p[1], p[2]);
				float d2 = distance(eye, p[0], p[2], p[3]);
				// A degenerate half (a quad that is a triangle) has no distance.
				float d = Math.min(Float.isNaN(d1) ? Float.MAX_VALUE : d1, Float.isNaN(d2) ? Float.MAX_VALUE : d2);

				if (d >= radius) {
					continue;
				}

				// Only the front of a one-sided quad is drawn: seen from behind it is not there.
				boolean front = n.dot(new Vector3f(eye).sub(p[0])) > 0.0F;

				if (!front && !behindToo) {
					continue;
				}

				StringBuilder from = new StringBuilder();

				for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
					String cls = e.getClassName();

					if (cls.startsWith("com.phiro.sabercraft.client.interior.") && !cls.endsWith("MeshBuilder") && !cls.endsWith("MeshAudit")) {
						from.append(' ').append(cls.substring(cls.lastIndexOf('.') + 1)).append(':').append(e.getLineNumber());

						if (from.length() > 80) {
							break;
						}
					}
				}

				found.add(new Near(i, layer, d, n, min, max, front, from.toString()));
			}
		});
		visuals.build(builder);
		found.sort(java.util.Comparator.comparingInt(Near::point).thenComparingDouble(Near::distance));
		return found;
	}

	/** Distance from a point to a triangle (closest point on the triangle, after Ericson). */
	static float distance(Vector3f p, Vector3f a, Vector3f b, Vector3f c) {
		Vector3f ab = new Vector3f(b).sub(a);
		Vector3f ac = new Vector3f(c).sub(a);
		Vector3f ap = new Vector3f(p).sub(a);
		float d1 = ab.dot(ap);
		float d2 = ac.dot(ap);

		if (d1 <= 0 && d2 <= 0) {
			return p.distance(a);
		}

		Vector3f bp = new Vector3f(p).sub(b);
		float d3 = ab.dot(bp);
		float d4 = ac.dot(bp);

		if (d3 >= 0 && d4 <= d3) {
			return p.distance(b);
		}

		float vc = d1 * d4 - d3 * d2;

		if (vc <= 0 && d1 >= 0 && d3 <= 0) {
			float v = d1 / (d1 - d3);
			return p.distance(new Vector3f(a).add(new Vector3f(ab).mul(v)));
		}

		Vector3f cp = new Vector3f(p).sub(c);
		float d5 = ab.dot(cp);
		float d6 = ac.dot(cp);

		if (d6 >= 0 && d5 <= d6) {
			return p.distance(c);
		}

		float vb = d5 * d2 - d1 * d6;

		if (vb <= 0 && d2 >= 0 && d6 <= 0) {
			float w = d2 / (d2 - d6);
			return p.distance(new Vector3f(a).add(new Vector3f(ac).mul(w)));
		}

		float va = d3 * d6 - d5 * d4;

		if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0) {
			float w = (d4 - d3) / (d4 - d3 + (d5 - d6));
			return p.distance(new Vector3f(b).add(new Vector3f(c).sub(b).mul(w)));
		}

		float denom = 1.0F / (va + vb + vc);
		float v = vb * denom;
		float w = vc * denom;
		return p.distance(new Vector3f(a).add(new Vector3f(ab).mul(v)).add(new Vector3f(ac).mul(w)));
	}

	/**
	 * Phase 16: finds block faces that can be seen from a room but are not covered by the model:
	 * raw blocks showing through, missing panels, one-sided faces seen from behind. From a few
	 * points of every face of a visible block next to a room cell, looks out along the face's
	 * normal for an opaque quad facing back within a little more than a block. "x,y,z dir block"
	 * per face, at most {@code limit}, after the count.
	 */
	public static List<String> uncovered(String plan, Blocks3 blocks, int limit) {
		InteriorVisuals visuals = InteriorScene.visuals(plan);
		com.phiro.sabercraft.interior.InteriorPlan interior = com.phiro.sabercraft.interior.InteriorPlan.of(plan);
		it.unimi.dsi.fastutil.longs.Long2ObjectMap<net.minecraft.world.phys.shapes.VoxelShape> shapes = com.phiro.sabercraft.interior.ProxyShapes.of(interior);
		Map<Long, List<Vector3f[]>> grid = new HashMap<>();
		Map<Vector3f[], Vector3f> normals = new java.util.IdentityHashMap<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			if (layer != Layer.SOLID) {
				return;
			}

			Vector3f[] p = new Vector3f[4];
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

			for (int v = 0; v < 4; v++) {
				p[v] = new Vector3f(xyzuv[v * 5], xyzuv[v * 5 + 1], xyzuv[v * 5 + 2]);
				minX = Math.min(minX, p[v].x);
				minY = Math.min(minY, p[v].y);
				minZ = Math.min(minZ, p[v].z);
				maxX = Math.max(maxX, p[v].x);
				maxY = Math.max(maxY, p[v].y);
				maxZ = Math.max(maxZ, p[v].z);
			}

			if (maxX - minX > 24 || maxY - minY > 24 || maxZ - minZ > 24) {
				return;
			}

			normals.put(p, new Vector3f(nx, ny, nz));

			for (int x = Mth.floor(minX); x <= Mth.floor(maxX); x++) {
				for (int y = Mth.floor(minY); y <= Mth.floor(maxY); y++) {
					for (int z = Mth.floor(minZ); z <= Mth.floor(maxZ); z++) {
						grid.computeIfAbsent(net.minecraft.core.BlockPos.asLong(x, y, z), unused -> new ArrayList<>()).add(p);
					}
				}
			}
		});
		visuals.build(builder);
		TreeMap<String, Integer> places = new TreeMap<>();
		int faces = 0;

		for (long key : roomCells(interior, blocks)) {
			int x = net.minecraft.core.BlockPos.getX(key);
			int y = net.minecraft.core.BlockPos.getY(key);
			int z = net.minecraft.core.BlockPos.getZ(key);

			{
				{
					{
						BlockState cell = blocks.get(x, y, z);

						if (!enclosed(blocks, x, y, z)) {
							continue;
						}
						// The faces of the blocks around this room cell, seen from it.
						for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
							int bx = x + dir.getStepX();
							int by = y + dir.getStepY();
							int bz = z + dir.getStepZ();
							BlockState block = blocks.get(bx, by, bz);

							// Chests, barrels and the like are meant to be seen.
							if (!visible(block) || block.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) {
								continue;
							}

							faces++;
							// Looking from the block into the cell.
							Vector3f n = new Vector3f(-dir.getStepX(), -dir.getStepY(), -dir.getStepZ());
							int missing = 0;

							for (float[] uv : new float[][] {{0.2F, 0.2F}, {0.8F, 0.2F}, {0.2F, 0.8F}, {0.8F, 0.8F}, {0.5F, 0.5F}}) {
								Vector3f point = facePoint(bx, by, bz, dir.getOpposite(), uv[0], uv[1]);
								// Hidden inside a shaped barrier (a curved wall): not seen.
								Vector3f ahead = new Vector3f(point).add(new Vector3f(n).mul(0.03F));
								net.minecraft.world.phys.shapes.VoxelShape shape = shapes.get(net.minecraft.core.BlockPos.asLong(x, y, z));

								if (shape != null && !shape.isEmpty() && shape.bounds().move(x, y, z).contains(ahead.x, ahead.y, ahead.z)
										&& inside(shape, ahead.x - x, ahead.y - y, ahead.z - z)) {
									continue;
								}

								if (!covered(grid, normals, point, n)) {
									missing++;
								}
							}

							if (missing > 0) {
								places.merge(bx + "," + by + "," + bz + " " + dir.getOpposite().getName() + " " + block.getBlock().getName().getString(), missing, Integer::sum);
							}
						}
					}
				}
			}
		}

		List<String> out = new ArrayList<>();
		out.add(places.size() + " uncovered block faces (" + faces + " faces looked at)");

		for (Map.Entry<String, Integer> entry : places.entrySet()) {
			if (out.size() > limit) {
				break;
			}

			out.add(entry.getKey() + " x" + entry.getValue());
		}

		return out;
	}

	/**
	 * The cells people can be in or see into: everything open that can be reached from where people
	 * come aboard, the lifts' arrivals and the rooms' middles, without going through a block.
	 */
	private static it.unimi.dsi.fastutil.longs.LongSet roomCells(com.phiro.sabercraft.interior.InteriorPlan plan, Blocks3 blocks) {
		it.unimi.dsi.fastutil.longs.LongSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
		List<net.minecraft.world.phys.Vec3> seeds = new ArrayList<>();
		seeds.add(plan.entry().add(0, 0.5, 0));

		for (com.phiro.sabercraft.interior.InteriorPlan.Lift lift : plan.lifts()) {
			seeds.add(lift.arrival().add(0, 0.5, 0));
		}

		for (com.phiro.sabercraft.interior.InteriorPlan.Room room : plan.rooms()) {
			seeds.add(room.centre());
		}

		for (net.minecraft.world.phys.Vec3 seed : seeds) {
			long key = net.minecraft.core.BlockPos.asLong(Mth.floor(seed.x), Mth.floor(seed.y), Mth.floor(seed.z));
			BlockState state = blocks.get(Mth.floor(seed.x), Mth.floor(seed.y), Mth.floor(seed.z));

			if (!state.canOcclude() && seen.add(key)) {
				queue.add(key);
			}
		}

		List<net.minecraft.world.level.levelgen.structure.BoundingBox> volumes = plan.volumes();

		while (!queue.isEmpty() && seen.size() < 400_000) {
			long key = queue.poll();
			int x = net.minecraft.core.BlockPos.getX(key);
			int y = net.minecraft.core.BlockPos.getY(key);
			int z = net.minecraft.core.BlockPos.getZ(key);

			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
				int nx = x + dir.getStepX();
				int ny = y + dir.getStepY();
				int nz = z + dir.getStepZ();
				boolean in = false;

				for (net.minecraft.world.level.levelgen.structure.BoundingBox box : volumes) {
					in |= box.isInside(nx, ny, nz);
				}

				if (!in) {
					continue;
				}

				long next = net.minecraft.core.BlockPos.asLong(nx, ny, nz);
				BlockState state = blocks.get(nx, ny, nz);

				if ((!state.canOcclude() || state.is(Blocks.BARRIER)) && seen.add(next)) {
					queue.add(next);
				}
			}
		}

		return seen;
	}

	/** Whether a cell has blocks round it (four sides and below): a room, not the void round the ship. */
	private static boolean enclosed(Blocks3 blocks, int x, int y, int z) {
		for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
				net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.DOWN}) {
			boolean found = false;

			for (int i = 1; i <= 64 && !found; i++) {
				found = !blocks.get(x + dir.getStepX() * i, y + dir.getStepY() * i, z + dir.getStepZ() * i).isAir();
			}

			if (!found) {
				return false;
			}
		}

		return true;
	}

	private static boolean inside(net.minecraft.world.phys.shapes.VoxelShape shape, double x, double y, double z) {
		for (net.minecraft.world.phys.AABB box : shape.toAabbs()) {
			if (box.contains(x, y, z)) {
				return true;
			}
		}

		return false;
	}

	/** A point on the face of block (x, y, z) towards {@code side}, at (u, v) across it. */
	private static Vector3f facePoint(int x, int y, int z, net.minecraft.core.Direction side, float u, float v) {
		return switch (side) {
			case UP -> new Vector3f(x + u, y + 1, z + v);
			case DOWN -> new Vector3f(x + u, y, z + v);
			case NORTH -> new Vector3f(x + u, y + v, z);
			case SOUTH -> new Vector3f(x + u, y + v, z + 1);
			case WEST -> new Vector3f(x, y + v, z + u);
			case EAST -> new Vector3f(x + 1, y + v, z + u);
		};
	}

	/** Whether an opaque quad facing back lies in front of the point, within 1.3 blocks along n. */
	private static boolean covered(Map<Long, List<Vector3f[]>> grid, Map<Vector3f[], Vector3f> normals, Vector3f point, Vector3f n) {
		java.util.Set<Vector3f[]> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

		for (float t = 0.0F; t <= 1.3F; t += 0.25F) {
			Vector3f at = new Vector3f(point).add(new Vector3f(n).mul(t));
			List<Vector3f[]> list = grid.get(net.minecraft.core.BlockPos.asLong(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z)));

			if (list == null) {
				continue;
			}

			for (Vector3f[] q : list) {
				if (!seen.add(q)) {
					continue;
				}

				Vector3f qn = normals.get(q);

				if (qn.dot(n) < 0.15F) {
					continue;
				}

				if (hits(q, point, n, 1.3F)) {
					return true;
				}
			}
		}

		return false;
	}

	/** Whether the ray from o along d hits the quad within (0, max]. */
	private static boolean hits(Vector3f[] q, Vector3f o, Vector3f d, float max) {
		return tri(q[0], q[1], q[2], o, d, max) || tri(q[0], q[2], q[3], o, d, max);
	}

	private static boolean tri(Vector3f a, Vector3f b, Vector3f c, Vector3f o, Vector3f d, float max) {
		Vector3f e1 = new Vector3f(b).sub(a);
		Vector3f e2 = new Vector3f(c).sub(a);
		Vector3f p = new Vector3f(d).cross(e2);
		float det = e1.dot(p);

		if (Math.abs(det) < 1.0E-9F) {
			return false;
		}

		float inv = 1.0F / det;
		Vector3f s = new Vector3f(o).sub(a);
		float u = s.dot(p) * inv;

		if (u < -1.0E-4F || u > 1.0001F) {
			return false;
		}

		Vector3f qv = new Vector3f(s).cross(e1);
		float v = d.dot(qv) * inv;

		if (v < -1.0E-4F || u + v > 1.0001F) {
			return false;
		}

		float t = e2.dot(qv) * inv;
		return t > -1.0E-4F && t <= max;
	}

	private static String fmt(Vector3f n) {
		return String.format(java.util.Locale.ROOT, "(%.2f,%.2f,%.2f)", n.x, n.y, n.z);
	}

	private static String size(Quad q) {
		return sizeOf(q.p);
	}

	private static String sizeOf(Vector3f[] ps) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

		for (Vector3f p : ps) {
			minX = Math.min(minX, p.x);
			minY = Math.min(minY, p.y);
			minZ = Math.min(minZ, p.z);
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
			maxZ = Math.max(maxZ, p.z);
		}

		return String.format(java.util.Locale.ROOT, "[%.2f,%.2f,%.2f..%.2f,%.2f,%.2f]", minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static boolean visible(BlockState state) {
		return !state.isAir() && !state.is(Blocks.BARRIER) && !state.is(Blocks.LIGHT) && state.canOcclude();
	}

	private static boolean axis(Vector3f n) {
		return Math.abs(n.x) > 0.999F || Math.abs(n.y) > 0.999F || Math.abs(n.z) > 0.999F;
	}

	private static String place(Vector3f c) {
		return Mth.floor(c.x) + "," + Mth.floor(c.y) + "," + Mth.floor(c.z);
	}

	private static long key(Vector3f n, float d) {
		long nx = Math.round(n.x * 20) + 32;
		long ny = Math.round(n.y * 20) + 32;
		long nz = Math.round(n.z * 20) + 32;
		long dd = Math.round(d * 100) + (1L << 30);
		return ((nx * 64 + ny) * 64 + nz) << 32 ^ dd;
	}

	/** Whether two quads in the same plane share some area (touching edges do not count). */
	private static boolean overlap(Quad a, Quad b) {
		Vector3f u = Math.abs(a.n.x) < 0.9F ? new Vector3f(1, 0, 0).cross(a.n).normalize() : new Vector3f(0, 1, 0).cross(a.n).normalize();
		Vector3f v = new Vector3f(a.n).cross(u);
		float[][] pa = project(a, u, v);
		float[][] pb = project(b, u, v);
		return !separated(pa, pb) && !separated(pb, pa);
	}

	/** The corners in 2D, pulled a little towards the middle so that touching edges do not count. */
	private static float[][] project(Quad q, Vector3f u, Vector3f v) {
		float cu = q.centre.dot(u);
		float cv = q.centre.dot(v);
		float[][] out = new float[4][2];

		for (int i = 0; i < 4; i++) {
			float pu = q.p[i].dot(u);
			float pv = q.p[i].dot(v);
			float du = pu - cu;
			float dv = pv - cv;
			float len = (float) Math.sqrt(du * du + dv * dv);
			float pull = len < 1.0E-6F ? 0 : Math.min(0.02F, len * 0.5F) / len;
			out[i][0] = pu - du * pull;
			out[i][1] = pv - dv * pull;
		}

		return out;
	}

	/** Whether an edge of polygon a separates it from b. */
	private static boolean separated(float[][] a, float[][] b) {
		for (int i = 0; i < a.length; i++) {
			float[] p0 = a[i];
			float[] p1 = a[(i + 1) % a.length];
			float ex = p1[0] - p0[0];
			float ey = p1[1] - p0[1];

			if (ex * ex + ey * ey < 1.0E-10F) {
				continue;
			}

			// Axis: the edge's normal.
			float ax = -ey;
			float ay = ex;
			float minA = Float.MAX_VALUE;
			float maxA = -Float.MAX_VALUE;
			float minB = Float.MAX_VALUE;
			float maxB = -Float.MAX_VALUE;

			for (float[] p : a) {
				float s = p[0] * ax + p[1] * ay;
				minA = Math.min(minA, s);
				maxA = Math.max(maxA, s);
			}

			for (float[] p : b) {
				float s = p[0] * ax + p[1] * ay;
				minB = Math.min(minB, s);
				maxB = Math.max(maxB, s);
			}

			if (maxA <= minB || maxB <= minA) {
				return true;
			}
		}

		return false;
	}
}
