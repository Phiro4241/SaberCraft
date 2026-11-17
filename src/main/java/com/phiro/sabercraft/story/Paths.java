package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Phase 20: ways for the story's people round walls, consoles and corners. Before, a chaser ran
 * straight at the player and stopped at the first wall; one sent to look somewhere, or back to
 * their post, slid along walls and, stuck, was put back in place. Now they find a way: a search
 * over the floor's cells (a block each, eight neighbours, a step up or down of up to a block, a
 * corner cut only where both sides are free), within {@link #RADIUS} blocks and {@link #NODES}
 * cells. When the goal cannot be reached, the way leads to the cell nearest it (they come as close
 * as they can, as a person would).
 *
 * <p>Positions are in plot space (as {@link Figure}); {@code corner} is the plot's corner in the
 * world.
 */
final class Paths {
	/** How far from where they start a way may lead (blocks), and how many cells are looked at. */
	static final int RADIUS = 32;
	static final int NODES = 2600;
	/** How high a step up may be, and how far down one steps. */
	static final double UP = 0.55;
	static final double DOWN = 1.1;
	private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private Paths() {
	}

	/** A cell on the way: its block, the floor's height there, the way so far, the guess to the goal. */
	private record Node(int x, int z, double y, double cost, double guess, @Nullable Node from) {
	}

	/**
	 * A way from one place to another (plot space): the cells' centres on the floor, the first one
	 * past the start; empty when they are there already or no cell around is free.
	 */
	static List<Vec3> find(ServerLevel level, Vec3 corner, Vec3 from, Vec3 to, double height) {
		int sx = floor(from.x);
		int sz = floor(from.z);
		int gx = floor(to.x);
		int gz = floor(to.z);
		Double startY = stand(level, corner, sx + 0.5, from.y, sz + 0.5, height);

		if (startY == null) {
			startY = from.y;
		}

		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.cost + a.guess, b.cost + b.guess));
		Map<Long, Double> best = new HashMap<>();
		Node start = new Node(sx, sz, startY, 0.0, distance(sx, sz, startY, to), null);
		open.add(start);
		best.put(key(sx, sz, startY), 0.0);
		Node nearest = start;
		int looked = 0;

		while (!open.isEmpty() && looked < NODES) {
			Node node = open.poll();
			looked++;

			if (node.guess < nearest.guess) {
				nearest = node;
			}

			if (Math.abs(node.x - gx) <= 0 && Math.abs(node.z - gz) <= 0 && Math.abs(node.y - to.y) < 1.6) {
				nearest = node;
				break;
			}

			for (int[] n : NEIGHBOURS) {
				int x = node.x + n[0];
				int z = node.z + n[1];

				if (Math.abs(x - sx) > RADIUS || Math.abs(z - sz) > RADIUS) {
					continue;
				}

				Double y = stand(level, corner, x + 0.5, node.y, z + 0.5, height);

				if (y == null) {
					continue;
				}

				boolean diagonal = n[0] != 0 && n[1] != 0;

				// A corner is cut only where both sides are free (nobody walks through a wall's edge).
				if (diagonal && (stand(level, corner, node.x + n[0] + 0.5, node.y, node.z + 0.5, height) == null
						|| stand(level, corner, node.x + 0.5, node.y, node.z + n[1] + 0.5, height) == null)) {
					continue;
				}

				double cost = node.cost + (diagonal ? 1.414 : 1.0) + Math.abs(y - node.y) * 0.5;
				long k = key(x, z, y);
				Double known = best.get(k);

				if (known != null && known <= cost) {
					continue;
				}

				best.put(k, cost);
				open.add(new Node(x, z, y, cost, distance(x, z, y, to), node));
			}
		}

		List<Vec3> way = new ArrayList<>();

		for (Node node = nearest; node != null && node.from != null; node = node.from) {
			way.add(0, new Vec3(node.x + 0.5, node.y, node.z + 0.5));
		}

		return smooth(level, corner, from, way, height);
	}

	/**
	 * Leaves out the cells one can walk past straight from the one before (the way bends only at
	 * corners, so they do not zigzag along a corridor).
	 */
	private static List<Vec3> smooth(ServerLevel level, Vec3 corner, Vec3 from, List<Vec3> way, double height) {
		if (way.size() < 3) {
			return way;
		}

		List<Vec3> out = new ArrayList<>();
		Vec3 at = from;
		int i = 0;

		while (i < way.size()) {
			int far = i;

			for (int j = Math.min(way.size() - 1, i + 8); j > i; j--) {
				if (straight(level, corner, at, way.get(j), height)) {
					far = j;
					break;
				}
			}

			out.add(way.get(far));
			at = way.get(far);
			i = far + 1;
		}

		return out;
	}

	/** Whether one walks from a point to another straight on (the floor level all the way, nothing in between). */
	static boolean straight(ServerLevel level, Vec3 corner, Vec3 from, Vec3 to, double height) {
		if (Math.abs(from.y - to.y) > 0.3) {
			return false;
		}

		double length = Math.hypot(to.x - from.x, to.z - from.z);
		int steps = Math.max(1, (int) Math.ceil(length / 0.35));

		for (int k = 1; k <= steps; k++) {
			double t = k / (double) steps;
			double x = from.x + (to.x - from.x) * t;
			double z = from.z + (to.z - from.z) * t;

			if (!free(level, corner, x, from.y, z, height) || free(level, corner, x, from.y - 0.3, z, height)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The floor's height where one stands at that cell, coming from that height (a step up of at
	 * most {@link #UP}, down at most {@link #DOWN}), or null when one cannot stand there.
	 */
	static @Nullable Double stand(ServerLevel level, Vec3 corner, double x, double y, double z, double height) {
		Vec3 top = corner.add(x, y + UP + 0.02, z);
		Vec3 bottom = corner.add(x, y - DOWN - 0.02, z);
		HitResult hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}

		double floor = hit.getLocation().y - corner.y;
		return free(level, corner, x, floor, z, height) ? floor : null;
	}

	/** Whether a person fits there (plot space, standing on that height). */
	static boolean free(ServerLevel level, Vec3 corner, double x, double y, double z, double height) {
		AABB box = new AABB(x - 0.28, y + 0.01, z - 0.28, x + 0.28, y + Math.min(height, 1.8), z + 0.28).move(corner);
		return level.noBlockCollision(null, box);
	}

	private static double distance(int x, int z, double y, Vec3 to) {
		double dx = x + 0.5 - to.x;
		double dz = z + 0.5 - to.z;
		double dy = y - to.y;
		return Math.sqrt(dx * dx + dz * dz + dy * dy * 4.0);
	}

	private static long key(int x, int z, double y) {
		return ((long) (x & 0xFFFFF) << 40) | ((long) ((int) Math.floor(y * 2.0) & 0xFFFF) << 20) | (z & 0xFFFFF);
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}
}
