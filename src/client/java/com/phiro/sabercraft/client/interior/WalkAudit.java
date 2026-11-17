package com.phiro.sabercraft.client.interior;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ProxyShapes;

/**
 * Phase 17: walks through a built interior the way a player does and compares where one can go
 * with what is drawn. The Phase 16 flicker audit only compared the drawn surfaces with each other;
 * this one uses the game's own movement code (a real player moved step by step through every
 * place it can reach, sliding along walls, stepping up stairs and low things, jumping onto
 * things, falling down) and reports:
 *
 * <ul>
 * <li><b>in a wall</b>: a place one can stand where a drawn opaque surface passes through the
 * player's body (one walks into the wall or the machine, and the camera ends up behind its
 * surface: the "see-through" walls);</li>
 * <li><b>at the eyes</b>: the worst of those, a drawn surface within reach of the camera;</li>
 * <li><b>invisible</b>: places where the player is stopped by an invisible barrier with nothing
 * drawn there (the "big invisible blocks").</li>
 * </ul>
 *
 * Used by the tests only; it moves the given player and puts it back where it was.
 */
public final class WalkAudit {
	/** The grid the walk is made on (blocks). */
	static final double STEP = 0.25;
	/** How deep a drawn surface may reach into the player's body before it counts (the curves' steps). */
	public static final double BODY_TOLERANCE = 0.1;
	/** Half the width of the space round the eyes that must stay clear. */
	static final double EYE_HALF = 0.16;
	/** How far from a stopping barrier a drawn surface may be and still count as "the wall that stopped you". */
	static final double SEEN_REACH = 0.2;
	/** Phase 19: whether the walk jumps onto things too (off with SC_WALK_NOJUMP: only walking). */
	static final boolean JUMP = System.getenv("SC_WALK_NOJUMP") == null;
	static final int MAX_NODES = System.getenv("SC_WALK_MAX") != null ? Integer.parseInt(System.getenv("SC_WALK_MAX")) : 600_000;

	private WalkAudit() {
	}

	/** One triangle of the drawn model, in world space. */
	record Tri(Vector3f a, Vector3f b, Vector3f c, Layer layer, String from) {
	}

	/** With SC_WALK_TRACE set: where in the model code a quad was made (for the report). */
	private static String from() {
		if (System.getenv("SC_WALK_TRACE") == null) {
			return "";
		}

		return StackWalker.getInstance().walk(frames -> frames
				.filter(f -> f.getClassName().startsWith("com.phiro.sabercraft.client.interior.") && !f.getClassName().endsWith("MeshBuilder")
						&& !f.getClassName().endsWith("WalkAudit"))
				.limit(2).map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + ":" + f.getLineNumber())
				.reduce((a, b) -> a + "<" + b).orElse(""));
	}

	/** What the walk found. */
	public record Result(int nodes, int inWall, int atEyes, int invisible, List<String> lines) {
		public String summary() {
			return String.format(Locale.ROOT, "%d places walked, %d in a wall (%d at the eyes), %d invisible stops", this.nodes, this.inWall, this.atEyes,
					this.invisible);
		}
	}

	/** Places to start the walk from (plot space, feet). */
	public static Result walk(ServerLevel level, ServerPlayer player, BlockPos origin, InteriorPlan plan, List<Vec3> starts, int limit) {
		InteriorVisuals visuals = InteriorScene.visuals(plan.id());
		Map<Long, List<Tri>> grid = new HashMap<>();
		// Phase 18: holograms and force fields apart: one walks through them, but one sees a force
		// field one stands on (the hangar's opening in the floor).
		Map<Long, List<Tri>> holo = new HashMap<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			Vector3f[] p = new Vector3f[4];

			for (int v = 0; v < 4; v++) {
				p[v] = new Vector3f(xyzuv[v * 5] + origin.getX(), xyzuv[v * 5 + 1] + origin.getY(), xyzuv[v * 5 + 2] + origin.getZ());
			}

			String from = from();
			Map<Long, List<Tri>> into = layer == Layer.HOLO ? holo : grid;
			add(into, new Tri(p[0], p[1], p[2], layer, from));
			add(into, new Tri(p[0], p[2], p[3], layer, from));
		});
		visuals.build(builder);
		// Phase 19: the crew no longer collide; one may stand partly in someone (that is no wall).

		Vec3 home = player.position();
		float homeYaw = player.getYRot();
		boolean flying = player.getAbilities().flying;
		player.getAbilities().flying = false;
		player.setShiftKeyDown(false);
		player.setSprinting(false);

		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<Vec3> queue = new ArrayDeque<>();
		TreeMap<String, int[]> places = new TreeMap<>();
		int[] counts = new int[3];

		for (Vec3 start : starts) {
			Vec3 at = settle(player, start.add(origin.getX(), origin.getY() + 0.01, origin.getZ()));

			if (at != null && seen.add(key(at))) {
				queue.add(at);
			}
		}

		int nodes = 0;
		NODES.clear();
		STOPS.clear();
		SAMPLES.clear();
		double[][] dirs = new double[8][];

		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			dirs[i] = new double[] {Math.abs(Math.cos(a)) > 0.3 ? Math.signum(Math.cos(a)) * STEP : 0.0,
					Math.abs(Math.sin(a)) > 0.3 ? Math.signum(Math.sin(a)) * STEP : 0.0};
		}

		while (!queue.isEmpty() && nodes < MAX_NODES) {
			Vec3 at = queue.poll();
			nodes++;
			int before = counts[0] + counts[1] * 2;
			check(grid, plan, origin, at, places, counts);
			ground(level, grid, holo, plan, origin, at, places, counts);
			NODES.add(new double[] {at.x, at.y, at.z, counts[0] + counts[1] * 2 - before});

			for (double[] dir : dirs) {
				Vec3 target = at.add(dir[0], 0, dir[1]);
				Vec3 to = step(player, at, dir[0], dir[1], false);

				if (to == null) {
					continue;
				}

				if (Math.abs(to.x - target.x) > 0.02 || Math.abs(to.z - target.z) > 0.02) {
					stopped(level, grid, plan, origin, at, dir, places, counts);
				}

				if (to.distanceToSqr(at) > 0.0004 && seen.add(key(to))) {
					queue.add(to);
				}

				// A jump: onto something higher than a step (Phase 19: SC_WALK_NOJUMP walks only, the
				// way one goes from room to room).
				Vec3 up = JUMP ? step(player, at, dir[0], dir[1], true) : null;

				if (up != null && up.y > at.y + 0.6 && seen.add(key(up))) {
					queue.add(up);
				}
			}
		}

		player.getAbilities().flying = flying;
		player.teleportTo(home.x, home.y, home.z);
		player.setYRot(homeYaw);
		List<String> lines = new ArrayList<>();
		int shown = 0;

		for (Map.Entry<String, int[]> entry : places.entrySet()) {
			if (shown++ >= limit) {
				lines.add("... " + (places.size() - limit) + " more places");
				break;
			}

			lines.add(entry.getKey() + " x" + entry.getValue()[0] + " e.g. " + SAMPLES.get(entry.getKey()));
		}

		if (System.getenv("SC_WALK_MAPS") != null) {
			maps(level, grid, origin, plan, System.getenv("SC_WALK_MAPS"));
		}

		return new Result(nodes, counts[0], counts[1], counts[2], lines);
	}

	/**
	 * Phase 19: a census of the plan's invisible barriers next to where one can stand (an air cell
	 * with a floor under it within two blocks), by room: how many there are, how many of them reach
	 * somewhere nothing is drawn (checked in slices a quarter block high: a barrier block over a
	 * console half a block high is drawn below and invisible above), how many only collide
	 * ("ghosts": caps over furniture). One line per room, then the total.
	 */
	public static List<String> census(InteriorPlan plan) {
		String detail = System.getenv("SC_CENSUS_DETAIL");
		List<String> details = new ArrayList<>();
		Map<Long, List<Tri>> grid = new HashMap<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			Vector3f[] p = new Vector3f[4];

			for (int v = 0; v < 4; v++) {
				p[v] = new Vector3f(xyzuv[v * 5], xyzuv[v * 5 + 1], xyzuv[v * 5 + 2]);
			}

			add(grid, new Tri(p[0], p[1], p[2], layer, ""));
			add(grid, new Tri(p[0], p[2], p[3], layer, ""));
		});
		InteriorScene.visuals(plan.id()).build(builder);
		it.unimi.dsi.fastutil.longs.Long2ObjectMap<BlockState> blocks = ProxyShapes.blocks(plan);
		it.unimi.dsi.fastutil.longs.Long2ObjectMap<VoxelShape> shapes = ProxyShapes.of(plan);
		it.unimi.dsi.fastutil.longs.LongSet ghosts = ProxyShapes.ghosts(plan);
		TreeMap<String, int[]> rooms = new TreeMap<>();
		int[] total = new int[4];

		for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<BlockState> entry : blocks.long2ObjectEntrySet()) {
			if (!entry.getValue().is(Blocks.BARRIER)) {
				continue;
			}

			long key = entry.getLongKey();
			int x = BlockPos.getX(key);
			int y = BlockPos.getY(key);
			int z = BlockPos.getZ(key);
			String room = null;

			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int ax = x + d[0];
				int az = z + d[1];

				if (blocks.containsKey(BlockPos.asLong(ax, y, az))) {
					continue;
				}

				// Someone can stand there: a floor under the air within two blocks.
				for (int down = 1; down <= 2; down++) {
					if (blocks.containsKey(BlockPos.asLong(ax, y - down, az))) {
						InteriorPlan.Room in = plan.room(new Vec3(ax + 0.5, y + 0.5, az + 0.5));
						in = in != null ? in : plan.room(new Vec3(ax + 0.5, y - down + 1.5, az + 0.5));
						room = in != null ? in.id() : "-";
						break;
					}
				}

				if (room != null) {
					break;
				}
			}

			if (room == null) {
				continue;
			}

			VoxelShape shape = shapes.get(key);

			if (shape != null && shape.isEmpty()) {
				continue;
			}

			boolean invisible = false;

			for (AABB part : (shape != null ? shape : net.minecraft.world.phys.shapes.Shapes.block()).toAabbs()) {
				AABB solid = part.move(x, y, z);

				for (double y0 = solid.minY; y0 < solid.maxY - 0.01 && !invisible; y0 += 0.25) {
					AABB slice = new AABB(solid.minX, y0, solid.minZ, solid.maxX, Math.min(solid.maxY, y0 + 0.25), solid.maxZ).inflate(0.12);
					invisible = first(grid, slice, null) == null;
				}
			}

			boolean ghost = ghosts.contains(key);

			if (invisible && detail != null && (detail.equals("*") || detail.equals(room))) {
				details.add(String.format(Locale.ROOT, "  invisible %s %d,%d,%d %s%s", room, x, y, z, ghost ? "ghost" : shape != null ? "shaped" : "full",
						shape != null ? " " + shape.bounds() : ""));
			}

			int[] counts = rooms.computeIfAbsent(room, unused -> new int[4]);
			counts[0]++;
			total[0]++;

			if (invisible) {
				counts[1]++;
				total[1]++;
			}

			if (ghost) {
				counts[2]++;
				total[2]++;
			}

			if (shape != null) {
				counts[3]++;
				total[3]++;
			}
		}

		List<String> out = new ArrayList<>();

		for (Map.Entry<String, int[]> entry : rooms.entrySet()) {
			int[] c = entry.getValue();
			out.add(String.format(Locale.ROOT, "%-18s %5d barriers by the way, %4d invisible somewhere, %4d only collide, %4d shaped", entry.getKey(), c[0], c[1],
					c[2], c[3]));
		}

		out.add(String.format(Locale.ROOT, "%-18s %5d barriers by the way, %4d invisible somewhere, %4d only collide, %4d shaped", "TOTAL", total[0], total[1],
				total[2], total[3]));
		java.util.Collections.sort(details);
		out.addAll(details);
		return out;
	}

	/**
	 * For finding faults: the plan's blocks row by row as letters ({@code .} air, {@code #} a
	 * shell block, {@code B} a full barrier, {@code s} a shaped barrier, {@code L} light, other
	 * blocks by their first letter), and how many drawn opaque triangles each cell holds next to it.
	 */
	public static List<String> dump(InteriorPlan plan, int y0, int y1) {
		it.unimi.dsi.fastutil.longs.Long2ObjectMap<BlockState> blocks = VirtualPlot.of(plan).blocks;
		it.unimi.dsi.fastutil.longs.Long2ObjectMap<VoxelShape> shapes = com.phiro.sabercraft.interior.ProxyShapes.of(plan);
		Map<Long, int[]> tris = new HashMap<>();
		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			if (layer == Layer.SOLID) {
				float cx = (xyzuv[0] + xyzuv[5] + xyzuv[10] + xyzuv[15]) / 4.0F;
				float cy = (xyzuv[1] + xyzuv[6] + xyzuv[11] + xyzuv[16]) / 4.0F;
				float cz = (xyzuv[2] + xyzuv[7] + xyzuv[12] + xyzuv[17]) / 4.0F;
				tris.computeIfAbsent(BlockPos.asLong(Mth.floor(cx), Mth.floor(cy), Mth.floor(cz)), unused -> new int[1])[0]++;
			}
		});
		InteriorScene.visuals(plan.id()).build(builder);
		List<String> out = new ArrayList<>();
		BlockPos size = plan.size();

		for (int y = y0; y <= y1; y++) {
			out.add("row y=" + y + "   x 0.." + size.getX());

			for (int z = 0; z <= size.getZ(); z++) {
				StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%3d ", z));
				StringBuilder count = new StringBuilder("  ");

				for (int x = 0; x <= size.getX(); x++) {
					long key = BlockPos.asLong(x, y, z);
					BlockState state = blocks.get(key);
					char c;

					if (state == null) {
						c = '.';
					} else if (state.is(Blocks.BARRIER)) {
						c = shapes.containsKey(key) ? 's' : 'B';
					} else if (state.is(Blocks.LIGHT)) {
						c = 'L';
					} else {
						String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
						c = name.endsWith("terracotta") || name.endsWith("concrete") || name.startsWith("polished") ? '#' : name.charAt(0);
					}

					line.append(c);
					int[] n = tris.get(key);
					count.append(n == null ? ' ' : n[0] > 9 ? '+' : (char) ('0' + n[0]));
				}

				out.add(line.append(count).toString());
			}
		}

		return out;
	}

	/** The places walked in the last walk (world space x, y, z, and 0, 1 in a wall, 3 at the eyes). */
	static final List<double[]> NODES = new ArrayList<>();
	static final List<double[]> STOPS = new ArrayList<>();
	static final Map<String, String> SAMPLES = new HashMap<>();

	/**
	 * For finding faults by eye: a top view of each floor the walk reached, 16 pixels a block:
	 * grey where something collides at knee and at head height, the drawn surfaces cut at those
	 * heights as lines (white knee, cyan head), the places walked as dots (green fine, orange in a
	 * wall, red at the eyes), invisible stops in magenta.
	 */
	private static void maps(ServerLevel level, Map<Long, List<Tri>> grid, BlockPos origin, InteriorPlan plan, String dir) {
		java.util.TreeSet<Integer> floors = new java.util.TreeSet<>();

		for (double[] node : NODES) {
			floors.add(Mth.floor(node[1] + 0.01));
		}

		BlockPos size = plan.size();
		int px = 24;

		for (int floor : floors) {
			java.awt.image.BufferedImage image = new java.awt.image.BufferedImage((size.getX() + 4) * px, (size.getZ() + 4) * px,
					java.awt.image.BufferedImage.TYPE_INT_RGB);
			int ox = origin.getX() - 2;
			int oz = origin.getZ() - 2;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

			for (int ix = 0; ix < image.getWidth(); ix++) {
				for (int iz = 0; iz < image.getHeight(); iz++) {
					double x = ox + (ix + 0.5) / px;
					double z = oz + (iz + 0.5) / px;
					int colour = 0x101010;

					for (double h : new double[] {0.7, 1.7}) {
						double y = floor + h;
						pos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
						BlockState state = level.getBlockState(pos);
						VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());

						for (AABB part : shape.toAabbs()) {
							if (part.move(pos).contains(x, y, z)) {
								colour = h < 1.0 ? (state.is(Blocks.BARRIER) ? 0x505060 : 0x605040) : colour == 0x101010 ? 0x303048 : colour + 0x181818;
								break;
							}
						}
					}

					image.setRGB(ix, iz, colour);
				}
			}

			// The drawn surfaces cut at knee and head height.
			for (double h : new double[] {0.3, 1.0, 1.65}) {
				double y = floor + h;
				int colour = h < 0.5 ? 0xFFE040 : h < 1.2 ? 0xFFFFFF : 0x40E0FF;

				for (int x = origin.getX() - 2; x < origin.getX() + size.getX() + 2; x++) {
					for (int z = origin.getZ() - 2; z < origin.getZ() + size.getZ() + 2; z++) {
						List<Tri> list = grid.get(BlockPos.asLong(x, Mth.floor(y), z));

						if (list == null) {
							continue;
						}

						for (Tri tri : list) {
							if (tri.layer() != Layer.SOLID) {
								continue;
							}

							double[] seg = cut(tri, y);

							if (seg != null) {
								line(image, (seg[0] - ox) * px, (seg[1] - oz) * px, (seg[2] - ox) * px, (seg[3] - oz) * px, colour);
							}
						}
					}
				}
			}

			for (double[] node : NODES) {
				if (Mth.floor(node[1] + 0.01) == floor) {
					int colour = node[3] >= 2 ? 0xFF2020 : node[3] >= 1 ? 0xFF9020 : 0x20A040;
					dot(image, (int) ((node[0] - ox) * px), (int) ((node[2] - oz) * px), colour);
				}
			}

			for (double[] stop : STOPS) {
				if (Mth.floor(stop[1] + 0.01) == floor) {
					dot(image, (int) ((stop[0] - ox) * px), (int) ((stop[2] - oz) * px), 0xFF20FF);
				}
			}

			try {
				java.nio.file.Files.createDirectories(java.nio.file.Path.of(dir));
				javax.imageio.ImageIO.write(image, "png", java.nio.file.Path.of(dir, plan.id() + "_floor_" + (floor - origin.getY()) + ".png").toFile());
			} catch (java.io.IOException error) {
				throw new java.io.UncheckedIOException(error);
			}
		}
	}

	private static double @Nullable [] cut(Tri tri, double y) {
		Vector3f[] p = {tri.a(), tri.b(), tri.c()};
		double[] out = new double[4];
		int n = 0;

		for (int i = 0; i < 3 && n < 4; i++) {
			Vector3f a = p[i];
			Vector3f b = p[(i + 1) % 3];

			if ((a.y - y) * (b.y - y) < 0) {
				double t = (y - a.y) / (b.y - a.y);
				out[n++] = a.x + (b.x - a.x) * t;
				out[n++] = a.z + (b.z - a.z) * t;
			}
		}

		return n == 4 ? out : null;
	}

	private static void line(java.awt.image.BufferedImage image, double x0, double z0, double x1, double z1, int colour) {
		int steps = (int) Math.max(1, Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0))));

		for (int i = 0; i <= steps; i++) {
			int x = (int) (x0 + (x1 - x0) * i / steps);
			int z = (int) (z0 + (z1 - z0) * i / steps);

			if (x >= 0 && z >= 0 && x < image.getWidth() && z < image.getHeight()) {
				image.setRGB(x, z, colour);
			}
		}
	}

	private static void dot(java.awt.image.BufferedImage image, int x, int z, int colour) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (x + dx >= 0 && z + dz >= 0 && x + dx < image.getWidth() && z + dz < image.getHeight() && (dx == 0 || dz == 0)) {
					image.setRGB(x + dx, z + dz, colour);
				}
			}
		}
	}

	/** Puts the player at a spot and lets it fall onto the ground; {@code null} if it is stuck in something there. */
	private static @Nullable Vec3 settle(ServerPlayer player, Vec3 at) {
		player.setPos(at.x, at.y, at.z);

		if (!player.level().noCollision(player, player.getBoundingBox().deflate(0.001))) {
			return null;
		}

		for (int i = 0; i < 8 && !player.onGround(); i++) {
			player.setDeltaMovement(Vec3.ZERO);
			player.move(MoverType.SELF, new Vec3(0, -1.0, 0));
		}

		return player.onGround() ? player.position() : null;
	}

	/** One step from a place (walking, or jumping first), then falling onto the ground. */
	private static @Nullable Vec3 step(ServerPlayer player, Vec3 from, double dx, double dz, boolean jump) {
		player.setPos(from.x, from.y, from.z);
		player.setOnGround(true);
		player.setDeltaMovement(Vec3.ZERO);

		if (jump) {
			player.move(MoverType.SELF, new Vec3(0, 1.25, 0));
			player.setOnGround(false);
		}

		player.move(MoverType.SELF, new Vec3(dx, 0, dz));
		player.setDeltaMovement(Vec3.ZERO);

		for (int i = 0; i < 8; i++) {
			double y = player.getY();
			player.move(MoverType.SELF, new Vec3(0, -1.0, 0));

			if (player.onGround() || player.getY() == y) {
				break;
			}
		}

		if (!player.onGround() || player.getY() < from.y - 12.0) {
			return null;
		}

		return player.position();
	}

	private static long key(Vec3 at) {
		return BlockPos.asLong(Mth.floor(at.x * 16.0 + 0.5), Mth.floor(at.y * 8.0 + 0.5), Mth.floor(at.z * 16.0 + 0.5));
	}

	/** Whether drawn surfaces pass through the player's body or come near its eyes at that place. */
	private static void check(Map<Long, List<Tri>> grid, InteriorPlan plan, BlockPos origin, Vec3 at, TreeMap<String, int[]> places, int[] counts) {
		double t = BODY_TOLERANCE;
		AABB body = new AABB(at.x - 0.3 + t, at.y + 0.12, at.z - 0.3 + t, at.x + 0.3 - t, at.y + 1.8 - t, at.z + 0.3 - t);
		Tri hit = first(grid, body, Layer.SOLID);

		if (hit == null) {
			return;
		}

		AABB eyes = new AABB(at.x - EYE_HALF, at.y + 1.3, at.z - EYE_HALF, at.x + EYE_HALF, at.y + 1.8, at.z + EYE_HALF);
		Tri eyeHit = first(grid, eyes, Layer.SOLID);
		boolean eye = eyeHit != null;

		if (eye) {
			hit = eyeHit;
		}
		counts[0]++;

		if (eye) {
			counts[1]++;
		}

		report(places, plan, origin, at, eye ? "at the eyes" : "in a wall", hit);
	}

	/**
	 * Phase 18: whether the player stands on an invisible barrier with nothing drawn under the
	 * feet (standing in the air on top of an invisible block: one climbed onto it).
	 */
	private static void ground(ServerLevel level, Map<Long, List<Tri>> grid, Map<Long, List<Tri>> holo, InteriorPlan plan, BlockPos origin, Vec3 at,
			TreeMap<String, int[]> places, int[] counts) {
		AABB feet = new AABB(at.x - 0.3, at.y - 0.04, at.z - 0.3, at.x + 0.3, at.y - 0.001, at.z + 0.3);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos barrier = null;

		for (int x = Mth.floor(feet.minX); x <= Mth.floor(feet.maxX); x++) {
			for (int z = Mth.floor(feet.minZ); z <= Mth.floor(feet.maxZ); z++) {
				pos.set(x, Mth.floor(feet.minY), z);
				BlockState state = level.getBlockState(pos);

				for (AABB part : state.getCollisionShape(level, pos, CollisionContext.empty()).toAabbs()) {
					if (part.move(pos).intersects(feet)) {
						if (!state.is(Blocks.BARRIER)) {
							return;
						}

						barrier = pos.immutable();
					}
				}
			}
		}

		AABB under = new AABB(at.x - 0.4, at.y - 0.15, at.z - 0.4, at.x + 0.4, at.y + 0.05, at.z + 0.4);

		if (barrier != null && first(grid, under, null) == null && first(holo, under, null) == null && !vanilla(level, under)) {
			counts[2]++;
			STOPS.add(new double[] {at.x, at.y, at.z});
			report(places, plan, origin, new Vec3(barrier.getX() + 0.5, barrier.getY(), barrier.getZ() + 0.5), "invisible ground", null, at);
		}
	}

	/** The player was stopped going that way: by what, and is anything drawn there? */
	private static void stopped(ServerLevel level, Map<Long, List<Tri>> grid, InteriorPlan plan, BlockPos origin, Vec3 at, double[] dir,
			TreeMap<String, int[]> places, int[] counts) {
		AABB box = new AABB(at.x - 0.3 + dir[0], at.y + 0.61, at.z - 0.3 + dir[1], at.x + 0.3 + dir[0], at.y + 1.79, at.z + 0.3 + dir[1]);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
			for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);

					if (!state.is(Blocks.BARRIER)) {
						continue;
					}

					// (Phase 19: the cells that only collide count as well: the crew's columns are gone,
					// and caps over furniture are invisible walls like any other.)
					VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());

					for (AABB part : shape.toAabbs()) {
						AABB solid = part.move(x, y, z);

						if (!solid.intersects(box)) {
							continue;
						}

						AABB overlap = solid.intersect(box);

						// A cap over furniture (one cannot climb onto it) is seen with the furniture under it.
						// Phase 18: but never by the floor one stands on (it reached down to the floor
						// before, so nearly every invisible wall counted as seen).
						AABB seenBox = overlap.inflate(SEEN_REACH, 0.0, SEEN_REACH).expandTowards(0, -0.7, 0);
						seenBox = new AABB(seenBox.minX, Math.max(seenBox.minY, at.y + 0.06), seenBox.minZ, seenBox.maxX, Math.max(seenBox.maxY, at.y + 0.07),
								seenBox.maxZ);

						if (first(grid, seenBox, null) == null && !vanilla(level, seenBox)) {
							counts[2]++;
							STOPS.add(new double[] {(overlap.minX + overlap.maxX) / 2.0, at.y, (overlap.minZ + overlap.maxZ) / 2.0});
							report(places, plan, origin, new Vec3(x + 0.5, y, z + 0.5), "invisible", null, at);
						}
					}
				}
			}
		}
	}

	private static void report(TreeMap<String, int[]> places, InteriorPlan plan, BlockPos origin, Vec3 at, String what, @Nullable Tri tri) {
		report(places, plan, origin, at, what, tri, at);
	}

	private static void report(TreeMap<String, int[]> places, InteriorPlan plan, BlockPos origin, Vec3 at, String what, @Nullable Tri tri, Vec3 sample) {
		Vec3 node = sample.subtract(origin.getX(), origin.getY(), origin.getZ());
		Vec3 local = at.subtract(origin.getX(), origin.getY(), origin.getZ());
		InteriorPlan.Room room = plan.room(local);
		String where = String.format(Locale.ROOT, "%s %d,%d,%d %s", what, Mth.floor(local.x), Mth.floor(local.y), Mth.floor(local.z),
				room != null ? room.id() : "-");

		if (tri != null) {
			Vector3f lo = new Vector3f(tri.a()).min(tri.b()).min(tri.c()).sub(origin.getX(), origin.getY(), origin.getZ());
			Vector3f hi = new Vector3f(tri.a()).max(tri.b()).max(tri.c()).sub(origin.getX(), origin.getY(), origin.getZ());
			where += String.format(Locale.ROOT, " (surface %.2f,%.2f,%.2f to %.2f,%.2f,%.2f%s)", lo.x, lo.y, lo.z, hi.x, hi.y, hi.z,
					tri.from().isEmpty() ? "" : " " + tri.from());
		}

		if (!places.containsKey(where)) {
			SAMPLES.put(where, String.format(Locale.ROOT, "%.2f,%.2f,%.2f", node.x, node.y, node.z));
		}

		places.computeIfAbsent(where, unused -> new int[1])[0]++;
	}

	private static void add(Map<Long, List<Tri>> grid, Tri tri) {
		float minX = Math.min(tri.a().x, Math.min(tri.b().x, tri.c().x));
		float minY = Math.min(tri.a().y, Math.min(tri.b().y, tri.c().y));
		float minZ = Math.min(tri.a().z, Math.min(tri.b().z, tri.c().z));
		float maxX = Math.max(tri.a().x, Math.max(tri.b().x, tri.c().x));
		float maxY = Math.max(tri.a().y, Math.max(tri.b().y, tri.c().y));
		float maxZ = Math.max(tri.a().z, Math.max(tri.b().z, tri.c().z));

		// Degenerate or huge (the hull, seen from far): still listed in every cell it covers.
		for (int x = Mth.floor(minX); x <= Mth.floor(maxX); x++) {
			for (int y = Mth.floor(minY); y <= Mth.floor(maxY); y++) {
				for (int z = Mth.floor(minZ); z <= Mth.floor(maxZ); z++) {
					grid.computeIfAbsent(BlockPos.asLong(x, y, z), unused -> new ArrayList<>()).add(tri);
				}
			}
		}
	}

	/**
	 * Phase 18: whether a block the game draws itself (a barrel, a chest: not a barrier, not light)
	 * is in the box; the audit only knows the interior's own models, so it took the cap over the
	 * galley's barrel for an invisible wall.
	 */
	private static boolean vanilla(ServerLevel level, AABB box) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
			for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);

					if (state.isAir() || state.is(Blocks.BARRIER) || state.is(Blocks.LIGHT)) {
						continue;
					}

					for (AABB part : state.getCollisionShape(level, pos, CollisionContext.empty()).toAabbs()) {
						if (part.move(pos).intersects(box)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	/** The first triangle of that layer (any layer if {@code null}) that passes through the box. */
	private static @Nullable Tri first(Map<Long, List<Tri>> grid, AABB box, @Nullable Layer layer) {
		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
			for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
					List<Tri> list = grid.get(BlockPos.asLong(x, y, z));

					if (list == null) {
						continue;
					}

					for (Tri tri : list) {
						if ((layer == null || tri.layer() == layer) && overlaps(tri, box)) {
							return tri;
						}
					}
				}
			}
		}

		return null;
	}

	/** Triangle against box, separating axis test (Akenine-Möller). */
	static boolean overlaps(Tri tri, AABB box) {
		double cx = (box.minX + box.maxX) / 2.0;
		double cy = (box.minY + box.maxY) / 2.0;
		double cz = (box.minZ + box.maxZ) / 2.0;
		double hx = (box.maxX - box.minX) / 2.0;
		double hy = (box.maxY - box.minY) / 2.0;
		double hz = (box.maxZ - box.minZ) / 2.0;
		double[] v0 = {tri.a().x - cx, tri.a().y - cy, tri.a().z - cz};
		double[] v1 = {tri.b().x - cx, tri.b().y - cy, tri.b().z - cz};
		double[] v2 = {tri.c().x - cx, tri.c().y - cy, tri.c().z - cz};
		double[] h = {hx, hy, hz};

		// The box's axes.
		for (int i = 0; i < 3; i++) {
			double min = Math.min(v0[i], Math.min(v1[i], v2[i]));
			double max = Math.max(v0[i], Math.max(v1[i], v2[i]));

			if (min > h[i] || max < -h[i]) {
				return false;
			}
		}

		double[] e0 = sub(v1, v0);
		double[] e1 = sub(v2, v1);
		double[] e2 = sub(v0, v2);
		// The triangle's plane.
		double[] n = cross(e0, e1);
		double d = dot(n, v0);
		double r = h[0] * Math.abs(n[0]) + h[1] * Math.abs(n[1]) + h[2] * Math.abs(n[2]);

		if (Math.abs(d) > r) {
			return false;
		}

		// The nine cross products.
		double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

		for (double[] e : new double[][] {e0, e1, e2}) {
			for (double[] u : axes) {
				double[] a = cross(u, e);

				if (dot(a, a) < 1.0E-12) {
					continue;
				}

				double p0 = dot(a, v0);
				double p1 = dot(a, v1);
				double p2 = dot(a, v2);
				double rr = h[0] * Math.abs(a[0]) + h[1] * Math.abs(a[1]) + h[2] * Math.abs(a[2]);

				if (Math.min(p0, Math.min(p1, p2)) > rr || Math.max(p0, Math.max(p1, p2)) < -rr) {
					return false;
				}
			}
		}

		return true;
	}

	private static double[] sub(double[] a, double[] b) {
		return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}
}
