package com.phiro.sabercraft.client.interior;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.InteriorPlan;

/**
 * Phase 15: covers the walls, floors and ceilings of an interior with panels. Every face of a wall
 * block that looks into a room gets a panel just in front of it; a {@link Style} decides what it
 * looks like from where it is (floor, ceiling or wall, how high above the floor, which room, which
 * block is behind it), and may add trims (skirting, the slanted strip under the ceiling, pillars
 * in the corners). So the models always fit the blocks, also after a plan changes.
 */
public final class Skin {
	/** What kind of surface. */
	public enum Kind {
		FLOOR, CEILING, WALL
	}

	/**
	 * One face to cover: the open cell in front of it, the direction from that cell to the block,
	 * what the block is, how many cells above the floor (walls; the floor is 0), and the room.
	 */
	public record Surface(int x, int y, int z, Direction toward, Kind kind, int height, int roomHeight, BlockState block,
			InteriorPlan.@Nullable Room room) {
		public boolean in(String room) {
			return this.room != null && this.room.id().equals(room);
		}

		/** Whether the cell above the open one is the ceiling (the top row of a wall). */
		public boolean top() {
			return this.height == this.roomHeight - 1;
		}

		/** The coordinate along the wall (x for walls facing north/south, z for east/west). */
		public int along() {
			return this.toward.getAxis() == Direction.Axis.Z ? this.x : this.z;
		}
	}

	/** Draws one surface (or nothing); may draw trims too. */
	public interface Style {
		void draw(MeshBuilder b, Surface s, VirtualPlot plot);
	}

	/** How far panels stand off their blocks. */
	public static final float EPS = 0.004F;

	private Skin() {
	}

	/** Covers every face between a full block and an open cell, in the given boxes only. */
	public static int apply(VirtualPlot plot, InteriorPlan plan, MeshBuilder b, Style style) {
		int count = 0;

		for (Long2ObjectMap.Entry<BlockState> entry : plot.blocks.long2ObjectEntrySet()) {
			BlockState state = entry.getValue();

			if (!state.canOcclude()) {
				continue;
			}

			long key = entry.getLongKey();
			int bx = BlockPos.getX(key);
			int by = BlockPos.getY(key);
			int bz = BlockPos.getZ(key);

			for (Direction direction : Direction.values()) {
				int x = bx + direction.getStepX();
				int y = by + direction.getStepY();
				int z = bz + direction.getStepZ();

				// Phase 16: the barriers of curved walls are partly room. Blocks next to them are
				// panelled towards them (the part in the room shows; the rest is behind the curve);
				// they themselves are panelled like walls where they face a room.
				boolean wallCell = plot.wall(bx, by, bz);
				boolean open = plot.open(x, y, z) || !wallCell && plot.wall(x, y, z);

				if (!open || wallCell && direction.getAxis() == Direction.Axis.Y || !inside(plot, x, y, z)) {
					continue;
				}

				// Phase 17: a curved wall's cell is panelled only towards a square room. Towards the
				// round room itself the smooth wall is drawn; a flat panel on the cell's face stood
				// in front of it (up to most of a block into the corridor: one walked into it, saw
				// through it from behind and it flickered against the curve).
				if (wallCell && plan.openRound(x + 0.5, z + 0.5, y)) {
					continue;
				}

				Direction toward = direction.getOpposite();
				Kind kind = toward == Direction.DOWN ? Kind.FLOOR : toward == Direction.UP ? Kind.CEILING : Kind.WALL;
				int height = 0;

				while (height < 40 && !plot.solid(x, y - height - 1, z)) {
					height++;
				}

				int up = 0;

				while (up < 40 && !plot.solid(x, y + up + 1, z)) {
					up++;
				}

				InteriorPlan.Room room = plan.room(new Vec3(x + 0.5, y + 0.5, z + 0.5));
				style.draw(b, new Surface(x, y, z, toward, kind, height, height + up + 1, state, room), plot);
				count++;
			}
		}

		return count;
	}

	/** Whether an open cell is enclosed (a wall somewhere on all four sides and below): not out in the void. */
	private static boolean inside(VirtualPlot plot, int x, int y, int z) {
		for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			boolean found = false;

			for (int i = 1; i <= 128 && !found; i++) {
				int cx = x + direction.getStepX() * i;
					int cy = y + direction.getStepY() * i;
					int cz = z + direction.getStepZ() * i;
					// Window panes (barriers) close a room as well as walls do.
					found = !plot.open(cx, cy, cz) || plot.is(cx, cy, cz, net.minecraft.world.level.block.Blocks.BARRIER);
			}

			if (!found) {
				return false;
			}
		}

		return true;
	}

	/** Draws the face of a surface with the pen as it is, {@code off} blocks in front of the block. */
	public static void face(MeshBuilder b, Surface s, float off) {
		face(b, s.x, s.y, s.z, s.toward, off, 0.0F, 0.0F, 1.0F, 1.0F);
	}

	/**
	 * Draws part of the face of the cell (x, y, z) towards {@code toward}: from (u0, v0) to (u1, v1)
	 * in face space (u to the right, v down, 0..1), {@code off} in front of the block.
	 */
	public static void face(MeshBuilder b, int x, int y, int z, Direction toward, float off, float u0, float v0, float u1, float v1) {
		float w = u1 - u0;
		float h = v1 - v0;

		switch (toward) {
			case DOWN -> b.rect(x + u0, y + off, z + v0, w, 0, 0, 0, 0, h);
			case UP -> b.rect(x + u0, y + 1 - off, z + 1 - v0, w, 0, 0, 0, 0, -h);
			case NORTH -> b.rect(x + u0, y + 1 - v0, z + off, w, 0, 0, 0, -h, 0);
			case SOUTH -> b.rect(x + 1 - u0, y + 1 - v0, z + 1 - off, -w, 0, 0, 0, -h, 0);
			case WEST -> b.rect(x + off, y + 1 - v0, z + 1 - u0, 0, 0, -w, 0, -h, 0);
			case EAST -> b.rect(x + 1 - off, y + 1 - v0, z + u0, 0, 0, w, 0, -h, 0);
		}
	}

	/**
	 * A strip slanting from a wall to the ceiling above it (45 degrees, {@code size} blocks), the
	 * typical angled corner of Imperial rooms.
	 */
	public static void ceilingChamfer(MeshBuilder b, Surface s, float size) {
		int x = s.x;
		int y = s.y;
		int z = s.z;
		float top = y + 1 - EPS * 2;
		float low = y + 1 - size;

		switch (s.toward) {
			case NORTH -> b.rect(x, top, z + size, 1, 0, 0, 0, low - top, -size + EPS);
			case SOUTH -> b.rect(x + 1, top, z + 1 - size, -1, 0, 0, 0, low - top, size - EPS);
			case WEST -> b.rect(x + size, top, z + 1, 0, 0, -1, -size + EPS, low - top, 0);
			case EAST -> b.rect(x + 1 - size, top, z, 0, 0, 1, size - EPS, low - top, 0);
			default -> {
			}
		}
	}

	/**
	 * A low bevelled skirting along the bottom of a wall. In an inner corner it stops where the
	 * other wall's skirting begins, so the two never lie on top of each other (Phase 16: they did,
	 * and their tops flickered).
	 */
	public static void skirting(MeshBuilder b, Surface s, VirtualPlot plot, float height, float depth) {
		int x = s.x;
		int y = s.y;
		int z = s.z;
		boolean alongX = s.toward.getAxis() == Direction.Axis.Z;
		// Walls at the two ends of the cell (along the wall): the other skirting runs there.
		float lo = alongX ? (plot.solid(x - 1, y, z) ? depth : 0.0F) : (plot.solid(x, y, z - 1) ? depth : 0.0F);
		float hi = alongX ? (plot.solid(x + 1, y, z) ? depth : 0.0F) : (plot.solid(x, y, z + 1) ? depth : 0.0F);
		// Only an end that was pulled in gets a face: one at the cell's edge would lie on the face of
		// whatever stands there (a door frame, the next panel).
		int ends = (lo > 0 ? (alongX ? MeshBuilder.WEST : MeshBuilder.NORTH) : 0) | (hi > 0 ? (alongX ? MeshBuilder.EAST : MeshBuilder.SOUTH) : 0);

		switch (s.toward) {
			case NORTH -> b.box(x + lo, y, z, x + 1 - hi, y + height, z + depth, MeshBuilder.UP | MeshBuilder.SOUTH | ends);
			case SOUTH -> b.box(x + lo, y, z + 1 - depth, x + 1 - hi, y + height, z + 1, MeshBuilder.UP | MeshBuilder.NORTH | ends);
			case WEST -> b.box(x, y, z + lo, x + depth, y + height, z + 1 - hi, MeshBuilder.UP | MeshBuilder.EAST | ends);
			case EAST -> b.box(x + 1 - depth, y, z + lo, x + 1, y + height, z + 1 - hi, MeshBuilder.UP | MeshBuilder.WEST | ends);
			default -> {
			}
		}
	}

	/** Whether the open cell has walls on two sides that meet (an inner corner), and which. */
	public static @Nullable Direction[] corner(VirtualPlot plot, int x, int y, int z) {
		Direction[][] pairs = {{Direction.NORTH, Direction.EAST}, {Direction.EAST, Direction.SOUTH}, {Direction.SOUTH, Direction.WEST},
				{Direction.WEST, Direction.NORTH}};

		for (Direction[] pair : pairs) {
			if (plot.solid(x + pair[0].getStepX(), y, z + pair[0].getStepZ()) && plot.solid(x + pair[1].getStepX(), y, z + pair[1].getStepZ())) {
				return pair;
			}
		}

		return null;
	}

	/** A slim pillar standing in the corner of a cell, between the two walls given. */
	public static void cornerPillar(MeshBuilder b, int x, int y0, int y1, int z, Direction[] walls, float size) {
		float px = walls[0] == Direction.EAST || walls[1] == Direction.EAST ? x + 1 - size : x;
		float pz = walls[0] == Direction.SOUTH || walls[1] == Direction.SOUTH ? z + 1 - size : z;
		b.box(px, y0, pz, px + size, y1, pz + size, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
	}
}
