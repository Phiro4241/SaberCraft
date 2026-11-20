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

	/**
	 * Phase 22: what {@link #report} does not see: lit surfaces (screens, strips, holograms) lying in
	 * the plane of a surface they are drawn on, and the parts that move (doors, hatches, the parts
	 * built every frame) lying in the plane of anything else. Both flicker as two opaque surfaces
	 * do. Lists "x,y,z what" per place, at most {@code limit}, after the count; {@code plot} is a
	 * plot of the plan in the level, for the doors and hatches as they stand there now.
	 */
	public static List<String> decals(String plan, InteriorVisuals.PlotView plot, int limit) {
		InteriorVisuals visuals = InteriorScene.visuals(plan);
		List<Quad> quads = new ArrayList<>();
		java.util.Set<Quad> moving = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		boolean[] animating = new boolean[1];
		Map<Quad, String> sources = System.getProperty("sabercraft.audit.sources") != null ? new java.util.IdentityHashMap<>() : null;
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
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

			if (n.x * nx + n.y * ny + n.z * nz < 0) {
				n.negate();
			}

			Vector3f c = new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]).mul(0.25F);
			Quad q = new Quad(layer, p, n, n.dot(p[0]), c, false);
			quads.add(q);

			if (sources != null) {
				sources.put(q, from());
			}

			if (animating[0]) {
				moving.add(q);
			}
		});
		visuals.build(builder);
		animating[0] = true;
		visuals.animate(builder, 0.0F);
		com.phiro.sabercraft.interior.InteriorPlan of = com.phiro.sabercraft.interior.InteriorPlan.of(plan);

		if (plot != null && of != null) {
			builder.inside(true);
			DoorModels.draw(builder, plot, of, InteriorAtlas.IMP_DOOR, InteriorAtlas.IMP_WALL_STRIP_GLOW);
			HatchModels.draw(builder, plot, of);
			builder.inside(false);
		}

		Map<Long, List<Quad>> buckets = new HashMap<>();

		for (Quad q : quads) {
			buckets.computeIfAbsent(key(q.n, q.d), unused -> new ArrayList<>()).add(q);
		}

		TreeMap<String, Integer> places = new TreeMap<>();

		for (Quad a : quads) {
			boolean lit = a.layer != Layer.SOLID;

			if (!lit && !moving.contains(a)) {
				continue;
			}

			for (int dd = -1; dd <= 1; dd++) {
				List<Quad> list = buckets.get(key(a.n, a.d + dd * 0.01F));

				if (list == null) {
					continue;
				}

				for (Quad b : list) {
					if (b == a || a.n.dot(b.n) < 0.9995F || Math.abs(a.d - b.d) > SAME_PLANE) {
						continue;
					}

					// Two lit surfaces over each other only add up; a lit one counts against what it
					// lies on, a moving one against anything.
					boolean pair = lit ? b.layer == Layer.SOLID : true;

					// Each pair once: a moving solid pair from the first of the two.
					if (!pair || !lit && moving.contains(b) && b.layer == Layer.SOLID && System.identityHashCode(b) < System.identityHashCode(a)) {
						continue;
					}

					if (overlap(a, b)) {
						String what = (lit ? a.layer.name().toLowerCase(java.util.Locale.ROOT) : "moving") + "/" + (moving.contains(b) ? "moving " : "")
								+ b.layer.name().toLowerCase(java.util.Locale.ROOT);
						places.merge(place(a.centre) + " " + what + " n=" + fmt(a.n) + " " + size(a) + "&" + size(b)
								+ (sources != null ? " from" + sources.get(a) + " &" + sources.get(b) : ""), 1, Integer::sum);
					}
				}
			}
		}

		List<String> out = new ArrayList<>();
		int total = places.values().stream().mapToInt(Integer::intValue).sum();
		out.add(total + " fighting decal or moving quad pairs at " + places.size() + " places (" + quads.size() + " quads, " + moving.size() + " moving)");

		for (Map.Entry<String, Integer> entry : places.entrySet()) {
			if (out.size() > limit) {
				break;
			}

			out.add(entry.getKey() + " x" + entry.getValue());
		}

		return out;
	}

	/**
	 * Phase 22: the gaps one sees the outside through. From points all through the plan's rooms
	 * (eye height, every couple of blocks, only where one could stand) looks out in many
	 * directions; a look that meets neither a drawn surface facing it (solid or glass: a window is
	 * meant to be seen through) nor a visible block within {@code reach} blocks ends in the empty
	 * dimension outside: a gap in the walls. Lists "room x,y,z (the barrier cell the look leaves
	 * by)" per place, at most {@code limit}, after the count.
	 */
	public static List<String> leaks(String plan, Blocks3 blocks, InteriorVisuals.PlotView plot, float reach, int looks, int spacing, int limit) {
		InteriorVisuals visuals = InteriorScene.visuals(plan);
		com.phiro.sabercraft.interior.InteriorPlan of = com.phiro.sabercraft.interior.InteriorPlan.of(plan);
		Map<Long, List<Vector3f[]>> grid = new HashMap<>();
		Map<Vector3f[], Vector3f> normals = new java.util.IdentityHashMap<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			if (layer != Layer.SOLID && layer != Layer.GLASS) {
				return;
			}

			Vector3f[] p = new Vector3f[4];
			Vector3f min = new Vector3f(Float.MAX_VALUE);
			Vector3f max = new Vector3f(-Float.MAX_VALUE);

			for (int v = 0; v < 4; v++) {
				p[v] = new Vector3f(xyzuv[v * 5], xyzuv[v * 5 + 1], xyzuv[v * 5 + 2]);
				min.min(p[v]);
				max.max(p[v]);
			}

			normals.put(p, new Vector3f(nx, ny, nz));

			for (int x = Mth.floor(min.x); x <= Mth.floor(max.x); x++) {
				for (int y = Mth.floor(min.y); y <= Mth.floor(max.y); y++) {
					for (int z = Mth.floor(min.z); z <= Mth.floor(max.z); z++) {
						grid.computeIfAbsent(net.minecraft.core.BlockPos.asLong(x, y, z), unused -> new ArrayList<>()).add(p);
					}
				}
			}
		});
		visuals.build(builder);
		visuals.animate(builder, 0.0F);

		if (plot != null && of != null) {
			builder.inside(true);
			DoorModels.draw(builder, plot, of, InteriorAtlas.IMP_DOOR, InteriorAtlas.IMP_WALL_STRIP_GLOW);
			HatchModels.draw(builder, plot, of);
			builder.inside(false);
		}

		// Directions spread evenly over the sphere.
		int count = looks;
		List<Vector3f> dirs = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			float y = 1.0F - 2.0F * (i + 0.5F) / count;
			float r = Mth.sqrt(1.0F - y * y);
			float a = i * 2.3999632F;
			dirs.add(new Vector3f(Mth.cos(a) * r, y, Mth.sin(a) * r));
		}

		TreeMap<String, Integer> places = new TreeMap<>();
		Map<String, String> examples = new HashMap<>();
		int points = 0;
		int rays = 0;

		// Only where one can get: standing places reached on foot from the entry and the rooms.
		it.unimi.dsi.fastutil.longs.LongOpenHashSet reached = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
		List<net.minecraft.world.phys.Vec3> seeds = new ArrayList<>();
		seeds.add(of.entry());
		of.rooms().forEach(room -> seeds.add(new net.minecraft.world.phys.Vec3(room.centre().x, room.area().minY, room.centre().z)));

		for (net.minecraft.world.phys.Vec3 seed : seeds) {
			for (int dy = 0; dy <= 2; dy++) {
				int x = Mth.floor(seed.x);
				int y = Mth.floor(seed.y) + dy;
				int z = Mth.floor(seed.z);

				if (stand(blocks, x, y, z) && reached.add(net.minecraft.core.BlockPos.asLong(x, y, z))) {
					queue.add(net.minecraft.core.BlockPos.asLong(x, y, z));
					break;
				}
			}
		}

		while (!queue.isEmpty() && reached.size() < 60_000) {
			long key = queue.poll();
			int x = net.minecraft.core.BlockPos.getX(key);
			int y = net.minecraft.core.BlockPos.getY(key);
			int z = net.minecraft.core.BlockPos.getZ(key);

			for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				for (int dy = 1; dy >= -3; dy--) {
					int nx = x + step[0];
					int ny = y + dy;
					int nz = z + step[1];

					if (dy == 1 && blocks.get(x, y + 2, z).blocksMotion()) {
						continue;
					}

					if (stand(blocks, nx, ny, nz)) {
						if (reached.add(net.minecraft.core.BlockPos.asLong(nx, ny, nz))) {
							queue.add(net.minecraft.core.BlockPos.asLong(nx, ny, nz));
						}

						break;
					}

					// Blocked at this height: no lower place through a wall.
					if (blocks.get(nx, ny + 1, nz).blocksMotion()) {
						break;
					}
				}
			}
		}

		for (long key : reached) {
			int x = net.minecraft.core.BlockPos.getX(key);
			int y = net.minecraft.core.BlockPos.getY(key);
			int z = net.minecraft.core.BlockPos.getZ(key);

			if (Math.floorMod(x, spacing) != 0 || Math.floorMod(z, spacing) != 0) {
				continue;
			}

			String roomId = "?";

			for (com.phiro.sabercraft.interior.InteriorPlan.Room room : of.rooms()) {
				if (room.area().contains(x + 0.5, y + 0.5, z + 0.5)) {
					roomId = room.id();
					break;
				}
			}

			// The eye where one stands and, when asked, a little off it (a crack seen from one
			// spot only).
			float[][] offsets = spacing == 1 ? new float[][] {{0.5F, 0.5F}, {0.2F, 0.2F}, {0.8F, 0.8F}, {0.2F, 0.8F}, {0.8F, 0.2F}} : new float[][] {{0.5F, 0.5F}};

			for (float[] off : offsets) {
				Vector3f eye = new Vector3f(x + off[0], y + 1.6F, z + off[1]);
				points++;

				for (Vector3f d : dirs) {
					rays++;
					String exit = leak(grid, normals, blocks, eye, d, reach);

					if (exit != null) {
						String place = roomId + " " + exit;
						places.merge(place, 1, Integer::sum);
						examples.putIfAbsent(place, String.format(java.util.Locale.ROOT, "from %.1f,%.1f,%.1f looking %s", eye.x, eye.y, eye.z, fmt(d)));
					}
				}
			}
		}

		List<String> out = new ArrayList<>();
		int total = places.values().stream().mapToInt(Integer::intValue).sum();
		out.add(total + " looks into the void at " + places.size() + " places (" + points + " points, " + rays + " looks)");
		places.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(limit).forEach(e -> out.add(e.getKey() + " x" + e.getValue() + " e.g. " + examples.get(e.getKey())));
		return out;
	}

	/** Phase 22, for finding a gap: what one look passes, cell by cell (blocks, surfaces met, which way they face). */
	public static List<String> probe(String plan, Blocks3 blocks, Vector3f o, Vector3f d, float reach) {
		InteriorVisuals visuals = InteriorScene.visuals(plan);
		List<String> out = new ArrayList<>();
		d = new Vector3f(d).normalize();
		Vector3f dir = d;
		Map<Long, List<Vector3f[]>> grid = new HashMap<>();
		Map<Vector3f[], Vector3f> normals = new java.util.IdentityHashMap<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			Vector3f[] p = new Vector3f[4];

			for (int v = 0; v < 4; v++) {
				p[v] = new Vector3f(xyzuv[v * 5], xyzuv[v * 5 + 1], xyzuv[v * 5 + 2]);
			}

			Vector3f mn = new Vector3f(Float.MAX_VALUE);
			Vector3f mx = new Vector3f(-Float.MAX_VALUE);

			for (Vector3f v : p) {
				mn.min(v);
				mx.max(v);
			}

			if (layer == Layer.SOLID || layer == Layer.GLASS) {
				normals.put(p, new Vector3f(nx, ny, nz));

				for (int x = Mth.floor(mn.x); x <= Mth.floor(mx.x); x++) {
					for (int y = Mth.floor(mn.y); y <= Mth.floor(mx.y); y++) {
						for (int z = Mth.floor(mn.z); z <= Mth.floor(mx.z); z++) {
							grid.computeIfAbsent(net.minecraft.core.BlockPos.asLong(x, y, z), unused -> new ArrayList<>()).add(p);
						}
					}
				}
			}

			if (hits(p, o, dir, reach)) {
				Vector3f n = new Vector3f(nx, ny, nz);
				out.add("quad " + layer + " n=" + fmt(n) + (n.dot(dir) < 0 ? " FRONT " : " back ") + sizeOf(p) + " from" + from());
			}
		});
		visuals.build(builder);
		visuals.animate(builder, 0.0F);

		out.add("leak: " + leak(grid, normals, blocks, o, dir, reach));

		for (float t = 0; t < reach; t += 0.25F) {
			Vector3f at = new Vector3f(dir).mul(t).add(o);
			BlockState state = blocks.get(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));

			if (!state.isAir()) {
				out.add(String.format(java.util.Locale.ROOT, "t=%.2f %d,%d,%d %s visible=%s", t, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), state,
						visible(state)));
			}
		}

		return out;
	}

	/** Whether one can stand in a cell: it and the one above free, something under it. */
	private static boolean stand(Blocks3 blocks, int x, int y, int z) {
		return !blocks.get(x, y, z).blocksMotion() && !blocks.get(x, y + 1, z).blocksMotion() && blocks.get(x, y - 1, z).blocksMotion();
	}

	/** One look: null if it meets something, else where it left the model (the last cell before the void). */
	private static String leak(Map<Long, List<Vector3f[]>> grid, Map<Vector3f[], Vector3f> normals, Blocks3 blocks, Vector3f o,
			Vector3f d, float reach) {
		int x = Mth.floor(o.x);
		int y = Mth.floor(o.y);
		int z = Mth.floor(o.z);
		int sx = d.x > 0 ? 1 : -1;
		int sy = d.y > 0 ? 1 : -1;
		int sz = d.z > 0 ? 1 : -1;
		float tdx = Math.abs(1.0F / (Math.abs(d.x) < 1.0E-6F ? 1.0E-6F : d.x));
		float tdy = Math.abs(1.0F / (Math.abs(d.y) < 1.0E-6F ? 1.0E-6F : d.y));
		float tdz = Math.abs(1.0F / (Math.abs(d.z) < 1.0E-6F ? 1.0E-6F : d.z));
		float tx = (sx > 0 ? x + 1 - o.x : o.x - x) * tdx;
		float ty = (sy > 0 ? y + 1 - o.y : o.y - y) * tdy;
		float tz = (sz > 0 ? z + 1 - o.z : o.z - z) * tdz;
		String last = null;
		java.util.Set<Vector3f[]> tried = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

		for (int step = 0; step < reach * 3; step++) {
			List<Vector3f[]> here = grid.get(net.minecraft.core.BlockPos.asLong(x, y, z));

			if (here != null) {
				for (Vector3f[] q : here) {
					if (tried.add(q) && normals.get(q).dot(d) < 0.0F && hits(q, o, d, reach)) {
						return null;
					}
				}
			}

			BlockState state = blocks.get(x, y, z);

			if (step > 0 && visible(state)) {
				return null;
			}

			if (!state.isAir()) {
				last = x + "," + y + "," + z;
			}

			float t;

			if (tx < ty && tx < tz) {
				t = tx;
				x += sx;
				tx += tdx;
			} else if (ty < tz) {
				t = ty;
				y += sy;
				ty += tdy;
			} else {
				t = tz;
				z += sz;
				tz += tdz;
			}

			if (t > reach) {
				break;
			}
		}

		return "near " + (last != null ? last : "?");
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

	/** Where the quad being made comes from (the interior classes in the stack). */
	private static String from() {
		StringBuilder from = new StringBuilder();

		for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
			String cls = e.getClassName();

			if (cls.startsWith("com.phiro.sabercraft.client.interior.") && !cls.endsWith("MeshBuilder") && !cls.endsWith("MeshAudit")) {
				from.append(' ').append(cls.substring(cls.lastIndexOf('.') + 1)).append(':').append(e.getLineNumber());
				break;
			}
		}

		return from.toString();
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
	/**
	 * Phase 25: her saber staff in every pose of the duel's animations (each kind of blow at every
	 * point of its wind-up and swing, each reaction, the spin, the throw's wind-up, the second blade
	 * coming out): the poses in which a part of the staff or of a blade lies in her body (the body
	 * as it is drawn: torso, head, legs). The first line counts the poses looked at and those found.
	 */
	public static List<String> staffInBody() {
		List<String> found = new ArrayList<>();
		float shoulder = CrewModels.BODY - 0.06F;
		int[] attacks = {0, CrewModels.OVERHEAD, CrewModels.SWEEP, CrewModels.THRUST, CrewModels.CROUCH, CrewModels.STAGGER};
		int[] reacts = {0, CrewModels.FLINCH, CrewModels.HEAVY, CrewModels.CLASH, CrewModels.PARRY, CrewModels.LOCK, CrewModels.THROW_WIND};
		float[] steps = {0.0F, 0.25F, 0.5F, 0.75F, 1.0F};
		float[] spins = {0.0F, 0.125F, 0.25F, 0.375F, 0.5F, 0.625F, 0.75F, 0.875F};
		float[] twins = {0.1F, 0.3F, 0.5F, 0.75F, 1.0F};
		int poses = 0;
		int inBody = 0;

		for (int attack : attacks) {
			for (float raise : steps) {
				for (float swing : steps) {
					for (int react : reacts) {
						for (float reactP : react == 0 ? new float[] {0.0F} : steps) {
							for (float spin : spins) {
								for (float twin : twins) {
									CrewModels.Gear gear = new CrewModels.Gear(false, 0xFFFF2020, true, raise, swing, false, false, attack, 0.0F, 0.0F, 0.0F,
											0.0F, spin, react, reactP, false, false, twin);
									float[] staff = CrewModels.staffHold(shoulder, gear, CrewModels.saberHold(shoulder, gear));
									poses++;
									// The second blade only as far as it is out (as drawn).
									float second = Mth.clamp((twin - 0.35F) / 0.65F, 0.0F, 1.0F);
									float from = -(CrewModels.STAFF_HALF + CrewModels.STAFF_BLADE * second);
									float to = CrewModels.STAFF_HALF + CrewModels.STAFF_BLADE;

									for (float t = from; t <= to + 0.001F; t += 0.04F) {
										float x = staff[0] + staff[3] * t;
										float y = staff[1] + staff[4] * t;
										float z = staff[2] + staff[5] * t;

										if (Math.abs(x) < CrewModels.HALF_W && Math.abs(z) < CrewModels.HALF_D && y > -CrewModels.LEG
												&& y < shoulder + CrewModels.HEAD) {
											inBody++;

											if (found.size() < 40) {
												found.add(String.format(java.util.Locale.ROOT, "attack %d raise %.2f swing %.2f react %d/%.2f spin %.3f twin %.2f: %.2f along at %.2f,%.2f,%.2f",
														attack, raise, swing, react, reactP, spin, twin, t, x, y, z));
											}

											break;
										}
									}
								}
							}
						}
					}
				}
			}
		}

		found.add(0, poses + " poses, " + inBody + " with the staff in her body");
		return found;
	}

	/** Phase 28, for the tests: how fast the client turns that figure's saber ring now (0 when still). */
	public static float ringSpeed(int id) {
		return StoryModels.ringSpeedForTest(id);
	}

	/** Phase 26: what Kestrel is drawn as in the story's plot with that corner now (a trooper: in the second armour). */
	public static String kestrelLook(net.minecraft.core.BlockPos origin) {
		return StoryModels.kestrelForTest(origin);
	}

	/**
	 * Phase 26: her Inquisitor's saber (grip, ring, both blades) in every pose of the duel as above,
	 * the ring's rush and its wind-up included, at eight angles of the ring and both still and
	 * spinning (the whole disc swept): the poses in which a part of it lies in her body after it is
	 * kept clear, and how far it had to be moved out at most. The first line counts them.
	 */
	public static List<String> ringInBody() {
		List<String> found = new ArrayList<>();
		float shoulder = CrewModels.BODY - 0.06F;
		int[] attacks = {0, CrewModels.OVERHEAD, CrewModels.SWEEP, CrewModels.THRUST, CrewModels.CROUCH, CrewModels.STAGGER};
		int[] reacts = {0, CrewModels.FLINCH, CrewModels.HEAVY, CrewModels.CLASH, CrewModels.PARRY, CrewModels.LOCK, CrewModels.THROW_WIND,
				CrewModels.RING};
		float[] steps = {0.0F, 0.5F, 1.0F};
		float[] reactSteps = {0.0F, 0.25F, 0.5F, 0.75F, 1.0F};
		float[] spins = {0.0F, 0.125F, 0.25F, 0.375F, 0.5F, 0.625F, 0.75F, 0.875F};
		float[] twins = {0.0F, 0.5F, 1.0F};
		int poses = 0;
		int inBody = 0;
		float most = 0.0F;

		for (int attack : attacks) {
			for (float raise : steps) {
				for (float swing : steps) {
					for (int react : reacts) {
						for (float reactP : react == 0 ? new float[] {0.0F} : reactSteps) {
							for (float spin : spins) {
								for (float twin : twins) {
									for (int spinning = 0; spinning <= 1; spinning++) {
										for (int i = 0; i < 8; i++) {
											float angle = Mth.TWO_PI * i / 8.0F;
											CrewModels.Gear gear = new CrewModels.Gear(false, 0xFFFF2020, true, raise, swing, false, true, attack, 0.0F, 0.0F,
													0.0F, 0.0F, spin, react, reactP, false, false, twin, true, angle, spinning, 0.0F);
											float[] hilt = CrewModels.saberHold(shoulder, gear);
											float[] staff = twin > 0.0F ? CrewModels.staffHold(shoulder, gear, hilt) : null;
											InquisitorSaber.Hold held = CrewModels.ringHold(shoulder, gear,
													staff != null ? InquisitorSaber.centred(staff) : InquisitorSaber.fromEmitter(hilt));
											boolean second = Mth.clamp((twin - 0.35F) / 0.65F, 0.0F, 1.0F) > 0.0F;
											InquisitorSaber.Hold clear = InquisitorSaber.clear(held, angle, second, shoulder, spinning == 1);
											poses++;
											float dx = clear.c()[0] - held.c()[0];
											float dz = clear.c()[2] - held.c()[2];
											most = Math.max(most, Mth.sqrt(dx * dx + dz * dz));

											if (spinning == 1 ? InquisitorSaber.discInBody(clear, second, shoulder)
													: InquisitorSaber.inBody(clear, angle, second, shoulder)) {
												inBody++;

												if (found.size() < 40) {
													found.add(String.format(java.util.Locale.ROOT,
															"attack %d raise %.2f swing %.2f react %d/%.2f spin %.3f twin %.2f angle %d spinning %d", attack, raise,
															swing, react, reactP, spin, twin, i, spinning));
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}

		found.add(0, poses + " poses, " + inBody + " with the saber in her body, moved out at most " + String.format(java.util.Locale.ROOT, "%.2f", most));
		return found;
	}
}
