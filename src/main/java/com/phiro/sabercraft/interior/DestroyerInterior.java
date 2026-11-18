package com.phiro.sabercraft.interior;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The walkable part of the Star Destroyer (Phase 14, rebuilt in Phase 15).
 *
 * <p>Phase 15 places the rooms where they are in the ship: a client with SaberCraft draws the
 * whole ship around them (see {@code client.interior.DestroyerVisuals}): a wedge
 * {@value #LENGTH} blocks long, the stepped superstructure, the command tower and the bridge on
 * top of it. So the rooms sit in two groups, in plot space (x east, y up, z south, the nose to the
 * north):
 *
 * <ul>
 * <li><b>Bridge deck</b>, at the top of the tower: the bridge with its great window looking
 * forward over the whole length of the ship, the raised walkway between the two sunken crew pits,
 * the command platform with the helm at the window; behind it the command corridor, the
 * conference room and the sensor room, each with side windows over the hull, and the turbolift.</li>
 * <li><b>Hangar deck</b>, in the belly: the main hangar with its force field open to space below
 * (standing on it lowers you to the ground), TIE fighters on their racks, crates, the flight
 * control room behind a window; a corridor to the turbolift lobby and the reactor room with the
 * glowing core in its shaft.</li>
 * </ul>
 *
 * The blocks give the walls, floors, light and what can be used (the helm lever, the crates);
 * window panes are barriers, so the view is clear. Players without SaberCraft see the block rooms.
 */
public final class DestroyerInterior extends InteriorPlan {
	// --- The ship (shared with the client's models) ----------------------------------------------

	/** The centre line of the ship. */
	public static final int CX = 40;
	/** The height of the hull's rim (the "equator" between the upper and the lower hull). */
	public static final int EQUATOR = 40;
	/** Where the nose and the stern are. */
	public static final int NOSE = -470;
	public static final int STERN = 150;
	public static final int LENGTH = STERN - NOSE;
	/** Half the width at the stern. */
	public static final int HALF_WIDTH = 190;

	/**
	 * Phase 23: the reactor's steelwork, after Phiro's wish for a deep, open shaft like in the films:
	 * small platforms at the wall on two levels, narrow steel bridges without railings over the
	 * drop, the core rising out of the depth. Each piece ({@link #GANTRIES}) is a row of barriers
	 * exactly where it is drawn; under and between them the shaft is open far down (one who falls
	 * is caught and set back where they last stood, see {@link #pit}).
	 */
	private static void reactorWorks(InteriorBuilder b) {
		for (Gantry g : GANTRIES) {
			for (int x = g.x0(); x <= g.x1(); x++) {
				for (int z = g.z0(); z <= g.z1(); z++) {
					if (b.isAir(x, g.y() - 1, z)) {
						b.set(x, g.y() - 1, z, PROXY);
					}
				}
			}
		}

		// The core: a column from the depth to the top; nobody walks (or jumps) into its light.
		int k0 = (int) Math.floor(REACTOR_X - CORE_R - 1);
		int k1 = (int) Math.ceil(REACTOR_X + CORE_R + 1);
		int q0 = (int) Math.floor(REACTOR_Z - CORE_R - 1);
		int q1 = (int) Math.ceil(REACTOR_Z + CORE_R + 1);

		for (int y = PIT_FLOOR + 1; y < REACTOR_TOP; y++) {
			b.solid(k0, q0, k1, q1, y, (x, z) -> Math.hypot(x - REACTOR_X, z - REACTOR_Z) < CORE_R, 1.0, java.util.Set.of());
		}

		for (BlockPos console : REACTOR_CONSOLES) {
			b.shaped(console.getX(), console.getY(), console.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0), false);
			// Nobody climbs onto a console (one stood in its screen): a cap right over it.
			b.ghost(console.getX(), console.getY() + 1, console.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
		}

		// Phase 20: the console up on the deck level collides as drawn, nobody climbs onto it.
		b.shaped(CATWALK_DESK.getX(), CATWALK_DESK.getY(), CATWALK_DESK.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.1, 1.0, 1.0, 1.0),
				false);
		b.ghost(CATWALK_DESK.getX(), CATWALK_DESK.getY() + 1, CATWALK_DESK.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.1, 1.0, 0.5, 1.0));

		// The lift's four posts and the ribs up the shaft wall collide as drawn (one walked into them).
		for (int y = REACTOR_BOTTOM; y < HANGAR; y++) {
			b.solid((int) REACTOR_LIFT.minX - 1, (int) REACTOR_LIFT.minZ - 1, (int) REACTOR_LIFT.maxX, (int) REACTOR_LIFT.maxZ, y,
					DestroyerInterior::liftPostAt, 1.0, java.util.Set.of());
		}

		for (float angle : RIB_ANGLES) {
			double rx = REACTOR_X + Math.cos(Math.toRadians(angle)) * RIB_R;
			double rz = REACTOR_Z + Math.sin(Math.toRadians(angle)) * RIB_R;
			ProxyShapes.Solid rib = (x, z) -> (x - rx) * (x - rx) + (z - rz) * (z - rz) < RIB_SIZE * RIB_SIZE;

			for (int y = PIT_FLOOR + 1; y < REACTOR_TOP; y++) {
				// Phase 23: a rib's cells also hold the wall's curve (the round shell skips cells
				// already shaped): one walked into the wall beside every rib.
				final int row = y;
				ProxyShapes.Solid shape = (x, z) -> rib.at(x, z) || !openReactor(x, z, row) && !inGallery(x, z, row) && !inLabWay(x, z, row);
				b.solid((int) Math.floor(rx - 1), (int) Math.floor(rz - 1), (int) Math.floor(rx + 1), (int) Math.floor(rz + 1), y, shape, 1.0,
						java.util.Set.of());
			}
		}

		// Phase 23: the coolant valve by the wall (drawn with the story's rooms) stands on the lab's
		// platform now: its arm and wheel at the waist collide (one walked through them).
		for (int x = (int) VALVE_X - 1; x <= (int) VALVE_X + 1; x++) {
			for (int z = (int) VALVE_Z - 1; z <= (int) VALVE_Z + 1; z++) {
				b.solid(x, z, x, z, REACTOR_BOTTOM, DestroyerInterior::valveAt, 1.0, java.util.Set.of());
			}
		}

		// Light on the platforms (under the deck level too), round the core up the shaft.
		int r = REACTOR_BOTTOM;
		int[][] lamps = {{29, r + 3, 22, 12}, {29, r + 3, 27, 12}, {51, r + 3, 22, 12}, {51, r + 3, 28, 12}, {40, r + 3, 34, 12}, {46, r + 3, 35, 11},
				{41, r + 3, 12, 11}, {46, HANGAR + 3, 15, 12}, {30, HANGAR + 3, 23, 12}, {37, HANGAR + 3, 17, 11}, {36, r - 6, 24, 13}, {45, r - 6, 24, 13},
				{36, r + 2, 24, 14}, {45, r + 2, 24, 14}, {40, r + 9, 20, 13}, {40, r + 9, 29, 13}, {40, REACTOR_TOP - 3, 20, 14}};

		for (int[] lamp : lamps) {
			if (b.isAir(lamp[0], lamp[1], lamp[2])) {
				b.light(lamp[0], lamp[1], lamp[2], lamp[3]);
			}
		}
	}

	/**
	 * Phase 23: a piece of the reactor's steelwork one walks on: the cells from (x0, z0) to (x1, z1)
	 * (both included), people standing at y; a bridge (narrow, no railing) or a platform.
	 */
	public record Gantry(String id, int x0, int z0, int x1, int z1, int y, boolean bridge) {
		/** Whether a point (plot space) is over this piece. */
		public boolean contains(double x, double z) {
			return x >= this.x0 && x < this.x1 + 1 && z >= this.z0 && z < this.z1 + 1;
		}

		/** Whether one stands on it there (plot space). */
		public boolean on(double x, double y, double z) {
			return this.contains(x, z) && Math.abs(y - this.y) < 0.9;
		}

		public Vec3 centre() {
			return new Vec3((this.x0 + this.x1 + 1) / 2.0, this.y, (this.z0 + this.z1 + 1) / 2.0);
		}
	}

	/**
	 * Phase 23: the steelwork. On the working level (where the consoles are): the lift's platform
	 * by the west wall, the main console's in the east, the lab's in the south (its way out to the
	 * lab), a small one by the north console; narrow bridges between them round the open middle:
	 * the long one from the lift's platform to the lab's ({@code collapse}, it breaks in the
	 * escape: then the lab's side, with the east, is cut off from the lift), a short one from the
	 * lab's to the east platform, one from the lift's up to the north. On the deck level: the
	 * gallery's landing, a long bridge west over the shaft (the console on a bit of platform by it),
	 * and down south to the platform round the lift's top.
	 */
	public static final List<Gantry> GANTRIES = List.of(
			new Gantry("west", 27, 21, 34, 28, DestroyerInterior.REACTOR_BOTTOM, false),
			new Gantry("east", 48, 20, 53, 29, DestroyerInterior.REACTOR_BOTTOM, false),
			new Gantry("south", 37, 33, 49, 37, DestroyerInterior.REACTOR_BOTTOM, false),
			new Gantry("north", 37, 11, 44, 15, DestroyerInterior.REACTOR_BOTTOM, false),
			new Gantry("collapse_long", 33, 29, 34, 34, DestroyerInterior.REACTOR_BOTTOM, true),
			new Gantry("collapse_end", 35, 33, 36, 34, DestroyerInterior.REACTOR_BOTTOM, true),
			new Gantry("east_bridge", 48, 30, 49, 32, DestroyerInterior.REACTOR_BOTTOM, true),
			new Gantry("north_bridge", 33, 16, 34, 20, DestroyerInterior.REACTOR_BOTTOM, true),
			new Gantry("north_bridge_end", 33, 14, 36, 15, DestroyerInterior.REACTOR_BOTTOM, true),
			new Gantry("gallery", 44, 13, 49, 17, DestroyerInterior.HANGAR + 1, false),
			new Gantry("desk", 39, 14, 41, 15, DestroyerInterior.HANGAR + 1, false),
			new Gantry("deck_bridge", 33, 16, 43, 17, DestroyerInterior.HANGAR + 1, true),
			new Gantry("deck_bridge_end", 33, 18, 34, 21, DestroyerInterior.HANGAR + 1, true),
			new Gantry("lift_top", 28, 22, 34, 27, DestroyerInterior.HANGAR + 1, false));

	/** Phase 23: the bridge that breaks behind the player in the escape (from the lab's platform to the lift's). */
	public static final List<Gantry> COLLAPSE = GANTRIES.stream().filter(g -> g.id().startsWith("collapse")).toList();

	/** Phase 23: the core's column (it collides from the depth to the top). */
	public static final double CORE_R = 2.8;
	/** Phase 23: the shaft's floor far down (an invisible one; nobody gets there, see {@link #pit}). */
	public static final int PIT_FLOOR = 4;
	/** Phase 23: how deep the shaft is drawn (it seems to have no bottom, only a glow far down). */
	public static final int PIT_DRAWN = DestroyerInterior.REACTOR_BOTTOM - 30;
	/** Phase 23: how far one may drop in the shaft before being caught (from the deck level onto a platform is further). */
	public static final double PIT_FALL = 4.5;

	/** Phase 23: whether one stands on the bridge that breaks (plot space). */
	public static boolean onCollapse(double x, double y, double z) {
		return COLLAPSE.stream().anyMatch(g -> g.on(x, y, z));
	}

	/** Phase 23: whether the bridge that breaks stands in the plot with that corner. */
	public static boolean collapseStands(net.minecraft.world.level.BlockGetter level, BlockPos origin) {
		Gantry g = COLLAPSE.getFirst();
		return level.getBlockState(origin.offset(g.x0(), g.y() - 1, g.z1())).is(net.minecraft.world.level.block.Blocks.BARRIER);
	}

	/** Phase 23: puts the bridge that breaks back, or takes it away (it fell). */
	public static void setCollapse(net.minecraft.server.level.ServerLevel level, BlockPos origin, boolean standing) {
		net.minecraft.world.level.block.state.BlockState state = standing ? net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState()
				: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

		for (Gantry g : COLLAPSE) {
			for (int x = g.x0(); x <= g.x1(); x++) {
				for (int z = g.z0(); z <= g.z1(); z++) {
					BlockPos pos = origin.offset(x, g.y() - 1, z);

					if (!level.getBlockState(pos).equals(state)) {
						level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	@Override
	public boolean pit(Vec3 local, double fallen) {
		double dx = local.x - REACTOR_X;
		double dz = local.z - REACTOR_Z;

		if (dx * dx + dz * dz > REACTOR_WALL * REACTOR_WALL || local.y > REACTOR_TOP || local.y < PIT_FLOOR - 2) {
			return false;
		}

		return local.y < REACTOR_BOTTOM - 1.5 || fallen > PIT_FALL;
	}

	@Override
	public Vec3 pitRescue(Vec3 local) {
		Vec3 best = REACTOR_LIFT_ARRIVAL;
		double bestScore = Double.MAX_VALUE;

		for (Gantry g : GANTRIES) {
			if (g.bridge() || g.id().equals("desk")) {
				continue;
			}

			Vec3 c = g.centre();
			double score = Math.hypot(c.x - local.x, c.z - local.z) + Math.max(0.0, c.y - local.y - 1.0) * 0.5;

			if (score < bestScore) {
				bestScore = score;
				best = c;
			}
		}

		return best;
	}

	/** Phase 23: where the reactor's lift lets one out below. */
	public static final Vec3 REACTOR_LIFT_ARRIVAL = new Vec3(28.8, DestroyerInterior.REACTOR_BOTTOM, 25.0);

	// --- Bridge deck ---------------------------------------------------------------------------

	/** Floor blocks of the crew pits; people stand at + 1. */
	public static final int PIT = 146;
	/** Floor blocks of the walkway, the platform and the command deck; people stand at + 1. */
	public static final int DECK = 150;
	/** Ceiling blocks of the bridge. */
	public static final int BRIDGE_TOP = 161;
	/** The window wall of the bridge, and its sides. */
	public static final int WINDOW_Z = 65;
	public static final int BRIDGE_X0 = 17;
	public static final int BRIDGE_X1 = 63;
	/** The back wall of the bridge. */
	public static final int BRIDGE_BACK = 97;
	/** The walkway down the middle. */
	public static final int WALK_X0 = 37;
	public static final int WALK_X1 = 43;
	/** The command platform along the window. */
	public static final int PLATFORM_Z1 = 70;
	/** The rows of consoles in the pits. */
	public static final int[] PIT_ROWS = {74, 80, 86, 92};
	/** The command deck behind the bridge: ceiling blocks, back wall. */
	public static final int COMMAND_TOP = 157;
	public static final int COMMAND_BACK = 118;
	/** The side rooms: their outer walls (with windows). */
	public static final int SIDE_WEST = 3;
	public static final int SIDE_EAST = 77;
	public static final int SIDE_Z0 = 99;
	public static final int SIDE_Z1 = 115;


	private static void storyRooms(InteriorBuilder b) {
		net.minecraft.world.phys.shapes.VoxelShape low = net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.85, 1.0);

		// The maintenance room and the airlock: dark, low, pipes along the walls (drawn).
		b.room(1, HANGAR, -9, 12, HANGAR + 4, 12, DARK);
		b.box(2, HANGAR, -8, 11, HANGAR, 11, FLOOR);
		b.box(2, HANGAR + 1, AIRLOCK_WALL, 11, HANGAR + 3, AIRLOCK_WALL, DARK);
		b.clear(5, HANGAR + 1, AIRLOCK_WALL, 7, HANGAR + 3, AIRLOCK_WALL);
		// The machinery in the middle (two blocks high), crates as cover, the workbench, the cable tangle.
		b.box((int) MACHINERY.minX, HANGAR + 1, (int) MACHINERY.minZ, (int) MACHINERY.maxX - 1, HANGAR + 2, (int) MACHINERY.maxZ - 1, PROXY);
		b.box(3, HANGAR + 1, -7, 4, HANGAR + 1, -7, PROXY);
		b.set(3, HANGAR + 2, -7, PROXY);
		b.box(9, HANGAR + 1, 4, 10, HANGAR + 1, 5, PROXY);
		b.set(10, HANGAR + 2, 5, PROXY);
		b.box(2, HANGAR + 1, 3, 2, HANGAR + 1, 4, PROXY);

		for (int z = 0; z <= 1; z++) {
			b.shaped(11, HANGAR + 1, z, net.minecraft.world.phys.shapes.Shapes.box(0.35, 0.0, 0.0, 1.0, 0.85, 1.0), false);
		}

		b.shaped(2, HANGAR + 1, -5, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 0.3, 1.0, 1.0), false);
		b.light(4, HANGAR + 3, -5, 3);
		b.light(9, HANGAR + 3, 0, 2);
		b.light(4, HANGAR + 3, 5, 3);
		b.light(7, HANGAR + 3, 10, 6);

		// The armory, its door in the corridor's east wall.
		b.room(CX + 4, HANGAR, -15, 56, HANGAR + 5, -3, DARK);
		b.box(CX + 5, HANGAR, -14, 55, HANGAR, -4, FLOOR);
		b.clear(CX + 4, HANGAR + 1, -14, CX + 4, HANGAR + 3, -12);

		for (int z : ARMORY_LOCKERS) {
			b.shaped(ARMORY_LOCKER_X, HANGAR + 1, z, net.minecraft.world.phys.shapes.Shapes.box(0.3, 0.0, 0.0, 1.0, 1.0, 1.0), false);
			b.shaped(ARMORY_LOCKER_X, HANGAR + 2, z, net.minecraft.world.phys.shapes.Shapes.box(0.3, 0.0, 0.0, 1.0, 1.0, 1.0), false);
		}

		for (int x = 47; x <= 53; x++) {
			b.shaped(x, HANGAR + 1, -14, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.45), false);
			b.shaped(x, HANGAR + 2, -14, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.9, 0.45), false);
		}

		for (int x = 47; x <= 49; x++) {
			b.shaped(x, HANGAR + 1, DESK_Z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.15, 1.0, 0.85, 0.85), false);
		}

		b.box(50, HANGAR + 1, -10, 51, HANGAR + 1, -9, PROXY);
		b.set(51, HANGAR + 2, -10, PROXY);
		b.light(50, HANGAR + 4, -9, 10);
		b.light(48, HANGAR + 4, -5, 9);

		// The detention block, its door in the lobby's west wall.
		b.room(25, HANGAR, -2, CX - 4, HANGAR + 5, 10, DARK);
		b.box(26, HANGAR, -1, 35, HANGAR, 9, FLOOR);
		// Phase 22: the door two wide, a block of wall on each side (it stood in the corner by the
		// last cell, its frame cut off by the cell's field).
		b.clear(CX - 4, HANGAR + 1, DETENTION_DOOR_Z0, CX - 4, HANGAR + 3, DETENTION_DOOR_Z0 + 1);

		for (int x : new int[] {29, 33}) {
			b.box(x, HANGAR + 1, -1, x, HANGAR + 4, CELL_FRONT, DARK);
		}

		// The force fields (barriers, drawn glowing; the story switches them off).
		for (int[] cell : CELLS) {
			b.box(cell[0], HANGAR + 1, CELL_FRONT, cell[1], HANGAR + 3, CELL_FRONT, PANE);
			b.box(cell[0], HANGAR + 4, CELL_FRONT, cell[1], HANGAR + 4, CELL_FRONT, DARK);
		}

		b.set(CELL_CONSOLE.getX(), CELL_CONSOLE.getY(), CELL_CONSOLE.getZ(), PROXY);
		b.shaped(CELL_CONSOLE.getX() + 1, CELL_CONSOLE.getY(), CELL_CONSOLE.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.1, 0.0, 0.5, 0.9, 0.2, 1.0), false);
		b.box(30, HANGAR + 1, 6, 30, HANGAR + 2, 6, PROXY);
		b.box(33, HANGAR + 1, 9, 34, HANGAR + 1, 9, PROXY);
		b.light(30, HANGAR + 4, 6, 9);
		b.light(30, HANGAR + 3, 0, 6);

		// The lab under the reactor: the tracker, the containment field, the holo projector, consoles.
		b.room(36, REACTOR_BOTTOM - 1, LAB_WALL, 52, REACTOR_BOTTOM + 5, 52, DARK);
		b.box(37, REACTOR_BOTTOM - 1, LAB_WALL + 1, 51, REACTOR_BOTTOM - 1, 51, FLOOR);
		b.clear(LAB_DOOR_X0, REACTOR_BOTTOM, LAB_WALL, LAB_DOOR_X0 + 2, REACTOR_BOTTOM + 2, LAB_WALL);

		for (int y = REACTOR_BOTTOM; y <= REACTOR_BOTTOM + 2; y++) {
			double top = y == REACTOR_BOTTOM + 2 ? 0.6 : 1.0;
			b.spans((int) (TRACKER_X - TRACKER_R - 1), (int) (TRACKER_Z - TRACKER_R - 1), (int) (TRACKER_X + TRACKER_R + 1), (int) (TRACKER_Z + TRACKER_R + 1), y,
					(x, z) -> Math.hypot(x - TRACKER_X, z - TRACKER_Z) < TRACKER_R ? new double[] {0.0, top} : null, java.util.Set.of());
		}

		b.shaped(SABER_FIELD.getX(), SABER_FIELD.getY(), SABER_FIELD.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.15, 0.0, 0.15, 0.85, 1.0, 0.85), false);
		b.shaped(HOLO.getX(), HOLO.getY(), HOLO.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.1, 0.0, 0.1, 0.9, 0.15, 0.9), false);

		for (int z = 42; z <= 48; z += 2) {
			b.set(51, REACTOR_BOTTOM, z, PROXY);
			b.shaped(51, REACTOR_BOTTOM + 1, z, low, false);
		}

		b.light(40, REACTOR_BOTTOM + 3, 43, 9);
		b.light(48, REACTOR_BOTTOM + 3, 43, 9);
		b.light(44, REACTOR_BOTTOM + 4, 49, 11);
	}

	// --- Hangar deck ---------------------------------------------------------------------------

	/** Floor blocks of the hangar deck; people stand at + 1. */
	public static final int HANGAR = 19;
	public static final int HANGAR_TOP = 36;
	public static final int HANGAR_X0 = 17;
	public static final int HANGAR_X1 = 63;
	public static final int HANGAR_Z0 = -66;
	public static final int HANGAR_Z1 = -16;
	/** The force field in the floor, open to space below. */
	public static final int FIELD_X0 = 31;
	public static final int FIELD_X1 = 49;
	public static final int FIELD_Z0 = -54;
	public static final int FIELD_Z1 = -36;
	/**
	 * Phase 24: the TIE fighter the Schattenklinge escapes in: standing on its wings on the hangar
	 * floor west of the field (its ball's middle; only drawn while the story has it there).
	 */
	public static final Vec3 ESCAPE_TIE = new Vec3(23.5, HANGAR + 1 + 3.3, -46.5);
	/** Phase 24: where she stands before its ball (it faces east; between its wings) before she leaps up into it. */
	public static final Vec3 ESCAPE_TIE_FOOT = new Vec3(26.2, HANGAR + 1, -46.5);
	/** The flight control room on the west side. */
	public static final int CONTROL_X0 = 5;
	/** The corridor and the lift lobby behind the hangar. */
	public static final int LOBBY_Z0 = -2;
	public static final int LOBBY_Z1 = 6;
	/**
	 * Phase 23: the turbolift stands back in a bay of its own behind the lobby (floor z 7..9), so
	 * the detention block's door is no longer right beside it at the corridor's end.
	 */
	public static final int LIFT_Z0 = LOBBY_Z1 + 1;
	public static final int LIFT_Z1 = LOBBY_Z1 + 3;
	/** The reactor room: a round shaft. */
	public static final double REACTOR_X = 40.5;
	public static final double REACTOR_Z = 24.5;
	public static final double REACTOR_R = 14.0;
	/**
	 * Phase 20: the console on the catwalk, against the railing (cell; it faces away from the core).
	 * Before the crew (they are placed in this class's initialisation, the technician at it).
	 */
	public static final BlockPos CATWALK_DESK = new BlockPos(40, HANGAR + 1, 15);
	public static final int REACTOR_BOTTOM = 14;
	public static final int REACTOR_TOP = 44;
	/** Phase 16: where the reactor shaft's round wall is drawn (and collides). */
	public static final double REACTOR_WALL = REACTOR_R - 0.8;

	// --- Phase 19: the story's rooms -------------------------------------------------------------

	/**
	 * The maintenance room (inside, plot space): a dark, forgotten room off the crawl ways, where
	 * the Falcon's people dock at an old airlock. Its hatch leads into the crawl way to the hidden
	 * room; Kestrel scratched a bird beside it.
	 */
	public static final AABB MAINTENANCE = new AABB(2, HANGAR + 1, -8, 12, HANGAR + 4, 7);
	/** The wall between the maintenance room and the airlock (z), with a door. */
	public static final int AIRLOCK_WALL = 7;
	public static final AABB AIRLOCK = new AABB(2, HANGAR + 1, 8, 12, HANGAR + 4, 12);
	public static final BlockPos MAINTENANCE_HATCH = new BlockPos(10, HANGAR + 1, -9);
	/** The machinery block in the middle of the maintenance room (the patrol walks round it). */
	public static final AABB MACHINERY = new AABB(5, HANGAR + 1, -2, 9, HANGAR + 3, 2);
	/** The armory east of the hangar corridor: lockers, racks, the quartermaster's desk. */
	public static final AABB ARMORY = new AABB(45, HANGAR + 1, -14, 56, HANGAR + 5, -3);
	public static final int ARMORY_LOCKER_X = 55;
	public static final int[] ARMORY_LOCKERS = {-13, -12, -11, -10, -9, -8, -7, -6, -5};
	public static final int DESK_Z = -6;
	/** The detention block west of the lobby: three cells behind force fields, a corridor, the cell console. */
	public static final AABB DETENTION = new AABB(26, HANGAR + 1, -1, 36, HANGAR + 5, 10);
	/** The cells (inside, x0..x1 and z -1..1); their force fields are at z = {@link #CELL_FRONT}. */
	public static final int[][] CELLS = {{26, 28}, {30, 32}, {34, 35}};
	public static final int CELL_FRONT = 2;
	public static final BlockPos CELL_CONSOLE = new BlockPos(26, HANGAR + 1, 9);
	public static final BlockPos HIDEOUT_HATCH = new BlockPos(23, HANGAR + 1, -4);
	public static final BlockPos DETENTION_HATCH = new BlockPos(25, HANGAR + 1, 6);
	/** Phase 22: the detention block's door (z, two wide), in the lobby's west wall. */
	public static final int DETENTION_DOOR_Z0 = 4;
	/** The lab under the reactor (inside), its wall towards the shaft (z) and its blast door. */
	public static final AABB LAB = new AABB(37, REACTOR_BOTTOM, 40, 52, REACTOR_BOTTOM + 5, 52);
	public static final int LAB_WALL = 39;
	public static final int LAB_DOOR_X0 = 43;
	/** The tracker in the middle of the lab, the containment field with the saber, the holo projector. */
	public static final double TRACKER_X = 44.5;
	public static final double TRACKER_Z = 46.0;
	public static final double TRACKER_R = 2.2;
	public static final BlockPos SABER_FIELD = new BlockPos(44, REACTOR_BOTTOM, 50);
	public static final BlockPos HOLO = new BlockPos(39, REACTOR_BOTTOM, 46);

	private static final String WALL = "gray_concrete";
	private static final String DARK = "polished_deepslate";
	private static final String BLACK = "polished_blackstone";
	private static final String FLOOR = "polished_blackstone_bricks";
	private static final String PANE = "barrier";
	private static final String PROXY = "barrier";

	/** Phase 16: a chair collides only with its seat (people walk past its back and arms). */
	// Phase 18: as high as the backrest (one stepped onto the seat and stood in the armrests and the
	// backrest); one sits down with a click as before.
	static final net.minecraft.world.phys.shapes.VoxelShape CHAIR = net.minecraft.world.phys.shapes.Shapes.box(0.22, 0.0, 0.22, 0.78, 1.0, 0.78);

	// --- Phase 17: the crew ------------------------------------------------------------------------

	// Phase 18: the crew's kinds and poses are in Crew (kept here under their old names).
	public static final String TROOPER = Crew.TROOPER;
	public static final String OFFICER = Crew.OFFICER;
	public static final String TECH = Crew.TECH;
	public static final String STAND = Crew.STAND;
	public static final String WORK = Crew.WORK;
	public static final String SIT = Crew.SIT;
	public static final String TALK = Crew.TALK;
	public static final String PATROL = Crew.PATROL;

	/** Phase 18: the hangar's stores: chests in the floor under the cargo containers. */
	public static final List<BlockPos> STORES = buildStores();

	private static List<BlockPos> buildStores() {
		List<BlockPos> list = new java.util.ArrayList<>();

		for (int z = HANGAR_Z0 + 3; z <= HANGAR_Z0 + 9; z += 3) {
			list.add(new BlockPos(HANGAR_X1 - 3, HANGAR, z));
			list.add(new BlockPos(HANGAR_X1 - 3, HANGAR, z + 1));
			list.add(new BlockPos(HANGAR_X1 - 2, HANGAR, z + 1));
		}

		for (int x = HANGAR_X0 + 3; x <= HANGAR_X0 + 9; x += 3) {
			list.add(new BlockPos(x, HANGAR, HANGAR_Z0 + 2));
			list.add(new BlockPos(x + 1, HANGAR, HANGAR_Z0 + 2));
		}

		return List.copyOf(list);
	}

	/** Phase 18: a step down into the pits: its upper half towards the walkway (east, or west). */
	static final net.minecraft.world.phys.shapes.VoxelShape STEP_EAST = net.minecraft.world.phys.shapes.Shapes.or(
			net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0), net.minecraft.world.phys.shapes.Shapes.box(0.5, 0.5, 0.0, 1.0, 1.0, 1.0));
	static final net.minecraft.world.phys.shapes.VoxelShape STEP_WEST = net.minecraft.world.phys.shapes.Shapes.or(
			net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0), net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.5, 0.0, 0.5, 1.0, 1.0));

	/** Phase 17: the chairs the crew sits on (not free for players). */
	static final List<BlockPos> CREW_CHAIRS = List.of(new BlockPos(24, PIT + 1, 87), new BlockPos(28, PIT + 1, 75), new BlockPos(52, PIT + 1, 81),
			new BlockPos(58, PIT + 1, 93), new BlockPos(56, PIT + 1, 75), new BlockPos(HANGAR_X0 - 2, HANGAR + 1, -56), new BlockPos(HANGAR_X0 - 2, HANGAR + 1, -50));

	public static final List<Crew> CREW = buildCrew();

	@Override
	public List<Crew> crew() {
		return CREW;
	}

	private static List<Crew> buildCrew() {
		List<Crew> list = new java.util.ArrayList<>();
		int deck = DECK + 1;
		int hangar = HANGAR + 1;

		// The bridge: the admiral at the window, an officer of the watch beside, a pair talking on
		// the platform (the fleet and the security bureau), one walking the walkway, guards at the
		// door, the crew at the consoles in the pits (Phase 18: officers and technicians in turn).
		list.add(Crew.at(Crew.ADMIRAL, STAND, CX - 5.5, deck, WINDOW_Z + 1.7, 180.0F).as("admiral"));
		list.add(Crew.at(OFFICER, STAND, CX - 7.3, deck, WINDOW_Z + 2.3, 150.0F).as("watch"));
		list.add(Crew.at(OFFICER, TALK, CX + 7.2, deck, WINDOW_Z + 3.4, 100.0F).as("bridge"));
		list.add(Crew.at(Crew.OFFICER_BLACK, TALK, CX + 5.9, deck, WINDOW_Z + 3.6, 280.0F).as("security"));
		list.add(Crew.patrol(OFFICER, 0.9, 0.0, new Vec3(CX + 0.5, deck, PLATFORM_Z1 + 2.5), new Vec3(CX + 0.5, deck, BRIDGE_BACK - 3.5)).as("bridge"));
		list.add(Crew.at(TROOPER, STAND, CX - 2.5, deck, BRIDGE_BACK + 1.6, 0.0F).as("bridge_guard"));
		list.add(Crew.at(TROOPER, STAND, CX + 3.5, deck, BRIDGE_BACK + 1.6, 0.0F).as("bridge_guard"));
		int pit = 0;

		for (BlockPos chair : CREW_CHAIRS) {
			if (chair.getY() == PIT + 1) {
				list.add(Crew.at(pit++ % 2 == 0 ? TECH : OFFICER, SIT, chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5, 180.0F).as("pit"));
			}
		}

		// Standing at the consoles of the pits, between the chairs.
		list.add(Crew.at(TECH, WORK, 31.5, PIT + 1, 81.35, 180.0F).as("pit"));
		list.add(Crew.at(OFFICER, WORK, 49.5, PIT + 1, 87.35, 180.0F).as("pit"));

		// The command deck: a patrol along the corridor, honour guards by the lift, the sensor
		// room's crew, the army and the security bureau in conference.
		list.add(Crew.patrol(TROOPER, 1.1, 0.0, new Vec3(CX - 1.5, deck, BRIDGE_BACK + 2.5), new Vec3(CX - 1.5, deck, COMMAND_BACK - 4.5)).as("patrol"));
		list.add(Crew.at(Crew.GUARD, STAND, CX - 2.45, deck, COMMAND_BACK - 3.6, 270.0F).as("guard"));
		list.add(Crew.at(Crew.GUARD, STAND, CX + 3.45, deck, COMMAND_BACK - 3.6, 90.0F).as("guard"));
		int sx = (CX + 4 + SIDE_EAST) / 2 + 4;
		list.add(Crew.at(TECH, WORK, sx + 0.5, deck, 104.3, 0.0F).as("sensor"));
		list.add(Crew.at(OFFICER, WORK, sx - 3.3, deck, 107.5, 270.0F).as("sensor"));
		list.add(Crew.at(TECH, WORK, CX + 6.35, deck, SIDE_Z0 + 3.5, 90.0F).as("sensor"));
		int tx = (SIDE_WEST + CX - 4) / 2;
		list.add(Crew.at(Crew.OFFICER_OLIVE, SIT, tx - 2.5, deck, 105.5, 0.0F).as("army"));
		list.add(Crew.at(Crew.OFFICER_BLACK, SIT, tx + 3.5, deck, 109.5, 180.0F).as("conference"));

		// The hangar: guards at the door, a squad of two walking round the force field, a
		// technician at the crates, an officer by the landing circle with two pilots.
		list.add(Crew.at(TROOPER, STAND, CX - 3.5, hangar, HANGAR_Z1 - 1.4, 180.0F).as("hangar"));
		list.add(Crew.at(TROOPER, STAND, CX + 4.5, hangar, HANGAR_Z1 - 1.4, 180.0F).as("hangar"));
		Vec3[] loop = {new Vec3(27.5, hangar, -59.5), new Vec3(53.5, hangar, -59.5), new Vec3(53.5, hangar, -21.5), new Vec3(27.5, hangar, -21.5)};
		list.add(Crew.patrol(TROOPER, 1.2, 0.0, loop).as("patrol"));
		list.add(Crew.patrol(TROOPER, 1.2, 1.4, loop).as("patrol"));
		list.add(Crew.at(TECH, WORK, HANGAR_X1 - 4.4, hangar, -30.5, 270.0F).as("maintenance"));
		list.add(Crew.at(OFFICER, STAND, CX + 4.0, hangar, -27.0, 160.0F).as("hangar"));
		list.add(Crew.at(Crew.PILOT, TALK, CX + 5.4, hangar, -25.0, 240.0F).as("pilot"));
		list.add(Crew.at(Crew.PILOT, STAND, CX + 6.9, hangar, -25.9, 60.0F).as("pilot"));

		// Flight control: two at the consoles, one standing behind them.
		for (BlockPos chair : CREW_CHAIRS) {
			if (chair.getY() == HANGAR + 1) {
				list.add(Crew.at(TECH, SIT, chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5, 270.0F).as("flight_control"));
			}
		}

		list.add(Crew.at(Crew.OFFICER_OLIVE, STAND, CONTROL_X0 + 6.5, hangar, -44.5, 270.0F).as("flight_control"));

		// The corridor to the lift: a patrol; a guard at the reactor gallery; a technician on the catwalk.
		list.add(Crew.patrol(TROOPER, 1.0, 0.0, new Vec3(CX - 1.5, hangar, HANGAR_Z1 + 2.5), new Vec3(CX - 1.5, hangar, LOBBY_Z1 - 4.5)).as("patrol"));
		list.add(Crew.at(TROOPER, STAND, CX + 2.6, hangar, LOBBY_Z0 + 1.5, 90.0F).as("reactor"));
		// Phase 20: the technician on the catwalk works at a console on the railing (stood at the bare
		// railing, looking at nothing).
		list.add(Crew.at(TECH, WORK, CATWALK_DESK.getX() + 0.5, hangar, CATWALK_DESK.getZ() - 0.95, 0.0F).as("reactor"));

		// Phase 18: the reactor's lower level: technicians at the consoles, a guard by the lift.
		list.add(Crew.at(TECH, WORK, 51.05, REACTOR_BOTTOM, 24.5, 270.0F).as("reactor"));
		list.add(Crew.at(Crew.OFFICER_OLIVE, WORK, 40.5, REACTOR_BOTTOM, 35.05, 0.0F).as("reactor"));
		list.add(Crew.at(TROOPER, STAND, 29.0, REACTOR_BOTTOM, 27.6, 225.0F).as("reactor"));

		// Phase 18: in the hidden room off the crawl way, someone who should not be aboard.
		list.add(Crew.at(Crew.REBEL, SIT, 15.0, hangar, -5.5, 0.0F).as("spy"));
		return List.copyOf(list);
	}

	/** Phase 16: the chairs: in the bridge pits, round the conference table, in flight control. */
	public static final List<Seat> SEATS = buildSeats();

	private static List<Seat> buildSeats() {
		List<Seat> list = new java.util.ArrayList<>();

		for (int z : PIT_ROWS) {
			for (int x = BRIDGE_X0 + 2; x <= BRIDGE_X1 - 2; x++) {
				if ((x < WALK_X0 - 4 || x > WALK_X1 + 4) && (x & 1) == 0) {
					list.add(Seat.chair(x, PIT + 1, z + 1, 0.5, 180.0F, Seat.CHAIR));
				}
			}
		}

		int tx = (SIDE_WEST + CX - 4) / 2;

		for (int x = tx - 5; x <= tx + 5; x += 2) {
			list.add(Seat.chair(x, DECK + 1, 105, 0.5, 0.0F, Seat.CHAIR));
			list.add(Seat.chair(x, DECK + 1, 109, 0.5, 180.0F, Seat.CHAIR));
		}

		for (int z = -56; z <= -46; z += 2) {
			list.add(Seat.chair(HANGAR_X0 - 2, HANGAR + 1, z, 0.5, -90.0F, Seat.CHAIR));
		}

		// Phase 17: not the chairs the crew sits on.
		list.removeIf(seat -> CREW_CHAIRS.contains(seat.cell()) || seat.cell().equals(new BlockPos((SIDE_WEST + CX - 4) / 2 - 3, DECK + 1, 105))
				|| seat.cell().equals(new BlockPos((SIDE_WEST + CX - 4) / 2 + 3, DECK + 1, 109)));
		return List.copyOf(list);
	}

	@Override
	public List<Seat> seats() {
		return SEATS;
	}

	/** The doors: bridge, conference and sensor rooms, hangar, flight control, reactor gallery. */
	public static final List<Door> DOORS = List.of(
			Door.wall(false, BRIDGE_BACK, CX - 2, CX + 2, DECK + 1, DECK + 4),
			Door.wall(true, CX - 4, 106, 108, DECK + 1, DECK + 3),
			Door.wall(true, CX + 4, 106, 108, DECK + 1, DECK + 3),
			Door.wall(false, HANGAR_Z1, CX - 2, CX + 2, HANGAR + 1, HANGAR + 4),
			Door.wall(true, HANGAR_X0, -40, -38, HANGAR + 1, HANGAR + 3),
			Door.wall(true, CX + 4, LOBBY_Z0, LOBBY_Z0 + 2, HANGAR + 1, HANGAR + 3),
			// Phase 19: the airlock's inner door, the armory, the detention block, the lab's blast door.
			Door.wall(false, AIRLOCK_WALL, 5, 7, HANGAR + 1, HANGAR + 3),
			Door.wall(true, CX + 4, -14, -12, HANGAR + 1, HANGAR + 3),
			Door.wall(true, CX - 4, DETENTION_DOOR_Z0, DETENTION_DOOR_Z0 + 1, HANGAR + 1, HANGAR + 3),
			Door.wall(false, LAB_WALL, LAB_DOOR_X0, LAB_DOOR_X0 + 2, REACTOR_BOTTOM, REACTOR_BOTTOM + 2));

	/** Phase 19: the doors by name (their index in {@link #DOORS}). */
	public static final int DOOR_GALLERY = 5;
	public static final int DOOR_AIRLOCK = 6;
	public static final int DOOR_ARMORY = 7;
	public static final int DOOR_DETENTION = 8;
	public static final int DOOR_LAB = 9;

	@Override
	public List<Door> doors() {
		return DOORS;
	}

	/** The officers' stations on the bridge (lights), the sensor room's tactical table (the ship's state). */
	@Override
	public List<Console> consoles() {
		List<Console> list = new java.util.ArrayList<>();

		for (int x : new int[] {BRIDGE_X0 + 4, BRIDGE_X1 - 4}) {
			list.add(new Console(new BlockPos(x, DECK + 1, WINDOW_Z + 2), Console.LIGHTS));
		}

		int sx = (CX + 4 + SIDE_EAST) / 2 + 4;

		for (int x = sx - 2; x <= sx + 2; x++) {
			for (int z = 105; z <= 109; z++) {
				list.add(new Console(new BlockPos(x, DECK + 1, z), Console.STATUS));
			}
		}

		// Phase 18: the hangar's cargo containers open the store under them.
		for (BlockPos store : STORES) {
			list.add(new Console(store.above(), Console.STORAGE));
		}

		return list;
	}

	@Override
	public String id() {
		return "star_destroyer";
	}

	@Override
	public int version() {
		// 3 (Phase 16): the reactor shaft's round wall is shaped barriers where the drawn wall runs.
		// 5 (Phase 17): maintenance ways, the crew's places. 6 (Phase 18): drawn steps, crates and
		// throttle instead of vanilla blocks, the crawl vents and the hidden room, the reactor's
		// lower level, more crew. 7 (Phase 19): nobody of the crew collides, railings and
		// converters as drawn, lower caps over the pits' consoles. 8 (Phase 19): the story's rooms
		// (maintenance room and airlock, armory, detention block, lab under the reactor) and
		// Kestrel's crawl way. 9 (Phase 22): the detention door two wide, away from the corner;
		// the way to the lab collides where its walls are drawn. 10 (Phase 23): the turbolift back in
		// a bay of its own behind the lobby (the detention door no longer beside it); the reactor an
		// open shaft far down, platforms and narrow bridges instead of the floor and the catwalk.
		return 10;
	}

	@Override
	public BlockPos size() {
		return new BlockPos(80, 176, 120);
	}

	@Override
	public List<BoundingBox> volumes() {
		// Phase 23: and the reactor's shaft, deeper now.
		return List.of(new BoundingBox(1, 10, -70, 79, 46, 54), new BoundingBox(1, 143, 62, 79, 165, 120), PIT_VOLUME);
	}

	/** Phase 23: the depth of the reactor's shaft (under the rest of the ship's rooms). */
	private static final BoundingBox PIT_VOLUME = new BoundingBox(25, PIT_FLOOR, 9, 56, 9, 40);

	@Override
	public List<BoundingBox> clearVolumes() {
		// The Phase 14 rooms stood from the plot corner up to (45, 44, 50).
		return List.of(new BoundingBox(0, 0, 0, 46, 45, 51), new BoundingBox(1, 10, -70, 79, 46, 54), new BoundingBox(1, 143, 62, 79, 165, 120), PIT_VOLUME);
	}

	@Override
	public Vec3 entry() {
		return new Vec3(CX + 0.5, HANGAR + 1, -28.5);
	}

	@Override
	public float entryYaw() {
		return 180.0F;
	}

	@Override
	public List<AABB> exits() {
		return List.of(new AABB(FIELD_X0 + 2, HANGAR + 1, FIELD_Z0 + 2, FIELD_X1 - 1, HANGAR + 4, FIELD_Z1 - 1));
	}

	@Override
	public List<Lift> lifts() {
		return List.of(
				new Lift(new AABB(CX - 2, HANGAR + 1, LIFT_Z0, CX + 3, HANGAR + 4, LIFT_Z1 + 1), new Vec3(CX + 0.5, DECK + 1, COMMAND_BACK - 3.5), 180.0F),
				// Phase 23: out of the bay into the lobby, facing the corridor.
				new Lift(new AABB(CX - 2, DECK + 1, COMMAND_BACK - 2, CX + 3, DECK + 4, COMMAND_BACK), new Vec3(CX + 0.5, HANGAR + 1, LIFT_Z0 - 2.5), 180.0F),
				// Phase 18: the reactor's maintenance lift, from the catwalk down to the shaft floor and back.
				new Lift(REACTOR_LIFT.move(0, HANGAR + 1 - REACTOR_BOTTOM, 0), new Vec3(REACTOR_LIFT.minX - 1.2, REACTOR_BOTTOM, REACTOR_LIFT.getCenter().z), 0.0F),
				new Lift(REACTOR_LIFT, new Vec3(REACTOR_LIFT.minX - 1.2, HANGAR + 1, REACTOR_LIFT.getCenter().z), 180.0F));
	}

	/**
	 * Phase 18: the reactor's maintenance lift (its pad on the lift's platform; its twin right above
	 * on the deck level). Phase 23: the energy converters stand in the depth of the open shaft, up
	 * to just under the working level (drawn only: nobody gets near them). Angles from east,
	 * clockwise seen from above.
	 */
	public static final AABB REACTOR_LIFT = new AABB(30, REACTOR_BOTTOM, 24, 32, REACTOR_BOTTOM + 2, 26);
	public static final double CONVERTER_R = 9.0;
	public static final double CONVERTER_SIZE = 1.3;
	public static final double[] CONVERTER_ANGLES = {45.0, 135.0, 225.0, 315.0};
	/** The consoles of the working level: cells (on the platforms by the wall, facing the core). */
	public static final List<BlockPos> REACTOR_CONSOLES = List.of(new BlockPos(52, REACTOR_BOTTOM, 24), new BlockPos(40, REACTOR_BOTTOM, 36),
			new BlockPos(40, REACTOR_BOTTOM, 12));
	/** Phase 19: half the thickness of a railing (the stairs' and walkways' rails collide as drawn). */
	public static final double RAIL_HALF = 0.08;

	/** Phase 19: the conference table as drawn: its sides (z) and its top over the deck row. */
	public static final double TABLE_Z0 = 106.05;
	public static final double TABLE_Z1 = 108.95;
	public static final double TABLE_TOP = 0.92;
	/** Phase 18: half the thickness of the lift's posts (one at each corner of {@link #REACTOR_LIFT}). */
	public static final double LIFT_POST = 0.0625;
	/** Phase 18: the ribs up the shaft wall (they collide as drawn): their angles, where they stand, how thick. */
	// Phase 20: the rib at 75 degrees stood in the way to the lab (a pipe across the doorway, solid
	// besides); it stands beside the doorway now, at 61 degrees.
	/** The coolant valve on the reactor's wall (plot space): its wheel's middle; its pipe rises 0.45 further out on both axes. */
	public static final double VALVE_X = 49.2;
	public static final double VALVE_Z = 33.2;

	/**
	 * Phase 23: whether a point (plot space) is in the coolant valve's arm (from its pipe to the
	 * wheel, a little thicker than drawn) or its wheel (across the arm, 0.3 either side).
	 */
	static boolean valveAt(double x, double z) {
		double px = x - VALVE_X;
		double pz = z - VALVE_Z;
		// Along the arm (out to the pipe) and across it.
		double along = (px + pz) / Math.sqrt(2.0);
		double across = (px - pz) / Math.sqrt(2.0);
		boolean arm = along > -0.05 && along < 0.64 && Math.abs(across) < 0.1;
		boolean wheel = Math.abs(along) < 0.06 && Math.abs(across) < 0.31;
		return arm || wheel;
	}

	public static final float[] RIB_ANGLES = {15, 45, 61, 105, 135, 165, 195, 225, 255, 285, 345};
	public static final double RIB_R = REACTOR_WALL - 0.25;
	public static final double RIB_SIZE = 0.21;

	/** Phase 18: whether a point (plot space) is in one of the lift's posts. */
	public static boolean liftPostAt(double x, double z) {
		for (double cx : new double[] {REACTOR_LIFT.minX, REACTOR_LIFT.maxX}) {
			for (double cz : new double[] {REACTOR_LIFT.minZ, REACTOR_LIFT.maxZ}) {
				if (Math.abs(x - cx) < LIFT_POST && Math.abs(z - cz) < LIFT_POST) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public BlockPos helm() {
		return new BlockPos(CX, DECK + 2, WINDOW_Z + 2);
	}

	@Override
	public String ambient() {
		return "ship.interior_bridge";
	}

	@Override
	public List<Room> rooms() {
		return List.of(
				new Room("lift", new AABB(CX - 2, HANGAR + 1, LIFT_Z0, CX + 3, HANGAR + 4, LIFT_Z1 + 1)),
				new Room("lift", new AABB(CX - 2, DECK + 1, COMMAND_BACK - 2, CX + 3, DECK + 4, COMMAND_BACK)),
				new Room("flight_control", new AABB(CONTROL_X0 + 1, HANGAR + 1, -58, HANGAR_X0, HANGAR + 6, -36)),
				new Room("hangar", new AABB(HANGAR_X0 + 1, HANGAR + 1, HANGAR_Z0 + 1, HANGAR_X1, HANGAR_TOP, HANGAR_Z1)),
				new Room("reactor", new AABB(REACTOR_X - REACTOR_R, PIT_FLOOR, REACTOR_Z - REACTOR_R, REACTOR_X + REACTOR_R, REACTOR_TOP,
						REACTOR_Z + REACTOR_R)),
				new Room("hangar_corridor", new AABB(CX - 3, HANGAR + 1, HANGAR_Z1, CX + 4, HANGAR + 5, LOBBY_Z1 + 1)),
				new Room("conference", new AABB(SIDE_WEST + 1, DECK + 1, SIDE_Z0 + 1, CX - 4, COMMAND_TOP, SIDE_Z1)),
				new Room("sensor_room", new AABB(CX + 5, DECK + 1, SIDE_Z0 + 1, SIDE_EAST, COMMAND_TOP, SIDE_Z1)),
				new Room("command_corridor", new AABB(CX - 3, DECK + 1, BRIDGE_BACK, CX + 4, COMMAND_TOP, COMMAND_BACK)),
				new Room("vent", VENTS.get(0)),
				new Room("vent", VENTS.get(1)),
				new Room("vent", VENTS.get(2)),
				new Room("vent", VENTS.get(3)),
				new Room("hidden_room", HIDDEN),
				new Room("vent", VENTS.get(4)),
				// Phase 19: the story's rooms.
				new Room("maintenance", MAINTENANCE),
				new Room("airlock", AIRLOCK),
				new Room("armory", ARMORY),
				new Room("detention", DETENTION),
				new Room("lab", LAB),
				new Room("bridge", new AABB(BRIDGE_X0 + 1, PIT + 1, WINDOW_Z + 1, BRIDGE_X1, BRIDGE_TOP, BRIDGE_BACK)));
	}

	@Override
	public void build(InteriorBuilder b) {
		hangar(b);
		flightControl(b);
		corridor(b);
		reactor(b);
		// Phase 23: after the reactor (the bay's back wall is where the shaft's wall ring begins).
		liftBay(b);
		bridge(b);
		commandDeck(b);
		// Phase 19: the story's rooms (before the crawl ways, whose hatches go through their walls).
		storyRooms(b);
		vents(b);
		furnish(b);
		// Phase 19: the crew no longer collide (one walks partly through someone rather than into
		// an invisible column round them).
	}

	/**
	 * Phase 17: the corridor to the lift was bare: a rack of rifles on its west wall, a security
	 * desk on its east wall (proxies, drawn by the client).
	 */
	private static void furnish(InteriorBuilder b) {
		// Phase 18: both collide as drawn: the rack 0.55 deep against the wall (a full block of
		// invisible rack stood in the corridor), the desk where it is drawn (its blocks were half a
		// block off, one stood in its end).
		for (int z = -7; z <= -6; z++) {
			b.shaped(CX - 3, HANGAR + 1, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 0.55, 1.0, 1.0), false);
			b.shaped(CX - 3, HANGAR + 2, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 0.55, 0.85, 1.0), false);
		}

		for (int z = -10; z <= -8; z++) {
			double z0 = Math.max(0.0, -9.45 - z);
			double z1 = Math.min(1.0, -7.55 - z);
			b.shaped(CX + 3, HANGAR + 1, z, net.minecraft.world.phys.shapes.Shapes.box(0.12, 0.0, z0, 0.95, 1.0, z1), false);
		}
	}

	/**
	 * Phase 17: the maintenance ways. Phase 18: crawl ways, one block high, grating all round, a
	 * dim lamp now and then; one gets in through a small hatch low in a wall (crouching in front of
	 * it, one goes down on all fours). A side way leads to a hidden room.
	 */
	public static final List<AABB> VENTS = List.of(
			// From the hangar corridor west, then north into the flight control room.
			new AABB(10, HANGAR + 1, -10, 37, HANGAR + 2, -9),
			new AABB(10, HANGAR + 1, -35, 11, HANGAR + 2, -9),
			// Under the bridge's walkway, from one crew pit to the other.
			new AABB(WALK_X0, PIT + 1, 84, WALK_X1 + 1, PIT + 2, 85),
			// Phase 18: the side way south to the hidden room.
			new AABB(18, HANGAR + 1, -9, 19, HANGAR + 2, -7),
			// Phase 19: Kestrel's way from the hidden room east to the detention block.
			new AABB(24, HANGAR + 1, -4, 25, HANGAR + 2, 7));

	/** Phase 18: the hidden room off the crawl way (inside, walls included: x 13..23, z -8..-1). */
	public static final AABB HIDDEN = new AABB(14, HANGAR + 1, -7, 23, HANGAR + 4, -1);

	public static final List<Hatch> HATCHES = List.of(
			new Hatch(new BlockPos(CX - 4, HANGAR + 1, -10), net.minecraft.core.Direction.EAST),
			new Hatch(new BlockPos(10, HANGAR + 1, -35), net.minecraft.core.Direction.NORTH),
			new Hatch(new BlockPos(WALK_X0, PIT + 1, 84), net.minecraft.core.Direction.WEST),
			new Hatch(new BlockPos(WALK_X1, PIT + 1, 84), net.minecraft.core.Direction.EAST),
			// Phase 19: from the maintenance room into the crawl way (Kestrel's bird beside it),
			// and both ends of the way from the hidden room to the detention block.
			new Hatch(MAINTENANCE_HATCH, net.minecraft.core.Direction.SOUTH),
			new Hatch(HIDEOUT_HATCH, net.minecraft.core.Direction.WEST),
			new Hatch(DETENTION_HATCH, net.minecraft.core.Direction.EAST));

	/** The hidden room's opening (no hatch: from inside, nobody needs to hide it). */
	public static final BlockPos HIDDEN_MOUTH = new BlockPos(18, HANGAR + 1, -8);

	@Override
	public List<Hatch> hatches() {
		return HATCHES;
	}

	@Override
	public List<BlockPos> crawlMouths() {
		List<BlockPos> list = new java.util.ArrayList<>();

		for (Hatch hatch : HATCHES) {
			list.add(hatch.cell());
		}

		list.add(HIDDEN_MOUTH);
		return list;
	}

	private static void vents(InteriorBuilder b) {
		// The hangar deck's way: a shell round it first (one block of floor, way, ceiling), then hollowed out.
		b.box(9, HANGAR, -11, CX - 4, HANGAR + 2, -9, DARK);
		b.box(9, HANGAR, -35, 11, HANGAR + 2, -9, DARK);
		// Phase 19: Kestrel's way to the detention block.
		b.box(23, HANGAR, -5, 25, HANGAR + 2, 7, DARK);
		// Under the walkway: the way's upper row closed.
		b.box(WALK_X0, PIT + 2, 84, WALK_X1, PIT + 2, 84, BLACK);
		// The hidden room: a store room nobody remembers, three blocks high.
		b.room(13, HANGAR, -8, 23, HANGAR + 4, -1, DARK);
		b.box(14, HANGAR, -7, 22, HANGAR, -2, FLOOR);

		for (AABB vent : VENTS) {
			b.clear((int) vent.minX, (int) vent.minY, (int) vent.minZ, (int) vent.maxX - 1, (int) vent.maxY - 1, (int) vent.maxZ - 1);
		}

		// The hatches start closed.
		for (Hatch hatch : HATCHES) {
			b.set(hatch.cell().getX(), hatch.cell().getY(), hatch.cell().getZ(), PROXY);
		}

		// In the hidden room: a cot, crates, a desk with a small terminal (proxies, drawn by the client).
		// (The cot's head end with the rolled-up jacket on it: one stood in the jacket.)
		b.shaped(14, HANGAR + 1, -6, net.minecraft.world.phys.shapes.Shapes.or(net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.05, 1.0, 0.45, 0.95),
				net.minecraft.world.phys.shapes.Shapes.box(0.12, 0.45, 0.2, 0.5, 0.58, 0.8)), false);
		b.shaped(15, HANGAR + 1, -6, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.05, 1.0, 0.45, 0.95), false);
		b.box(21, HANGAR + 1, -7, 22, HANGAR + 2, -6, PROXY);
		b.set(22, HANGAR + 1, -5, PROXY);
		b.shaped(17, HANGAR + 1, -2, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.3, 1.0, 0.82, 1.0), false);
		b.shaped(18, HANGAR + 1, -2, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.3, 1.0, 0.82, 1.0), false);
		// Nobody climbs onto the desk (one stood in the terminal's screen).
		b.ghost(17, HANGAR + 2, -2, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.3, 1.0, 0.5, 1.0));
		b.ghost(18, HANGAR + 2, -2, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.3, 1.0, 0.5, 1.0));

		b.light(22, HANGAR + 1, -10, 6);
		b.light(10, HANGAR + 1, -22, 6);
		b.light(CX, PIT + 1, 84, 5);
		// Phase 19: the hidden room is a refuge now: a warmer light, and the desk lamp's glow.
		b.light(16, HANGAR + 3, -4, 12);
		b.light(20, HANGAR + 3, -4, 12);
		b.light(17, HANGAR + 2, -3, 9);
	}

	// --- Hangar deck ---------------------------------------------------------------------------

	private static void hangar(InteriorBuilder b) {
		b.room(HANGAR_X0, HANGAR, HANGAR_Z0, HANGAR_X1, HANGAR_TOP + 1, HANGAR_Z1, WALL);
		b.box(HANGAR_X0 + 1, HANGAR, HANGAR_Z0 + 1, HANGAR_X1 - 1, HANGAR, HANGAR_Z1 - 1, FLOOR);
		b.box(HANGAR_X0 + 1, HANGAR_TOP + 1, HANGAR_Z0 + 1, HANGAR_X1 - 1, HANGAR_TOP + 1, HANGAR_Z1 - 1, DARK);

		// The force field: an opening in the floor (barriers to stand on), a frame of hazard stripes.
		b.box(FIELD_X0, HANGAR, FIELD_Z0, FIELD_X1, HANGAR, FIELD_Z1, PANE);
		b.box(FIELD_X0 - 1, HANGAR, FIELD_Z0 - 1, FIELD_X1 + 1, HANGAR, FIELD_Z0 - 1, "yellow_concrete");
		b.box(FIELD_X0 - 1, HANGAR, FIELD_Z1 + 1, FIELD_X1 + 1, HANGAR, FIELD_Z1 + 1, "yellow_concrete");
		b.box(FIELD_X0 - 1, HANGAR, FIELD_Z0, FIELD_X0 - 1, HANGAR, FIELD_Z1, "yellow_concrete");
		b.box(FIELD_X1 + 1, HANGAR, FIELD_Z0, FIELD_X1 + 1, HANGAR, FIELD_Z1, "yellow_concrete");

		// Phase 18: the field's emitters along both edges collide as drawn (one walked through them).
		for (int x = FIELD_X0; x <= FIELD_X1; x += 3) {
			b.shaped(x, HANGAR + 1, FIELD_Z0 - 1, net.minecraft.world.phys.shapes.Shapes.box(0.25, 0.0, 0.1, 0.75, 0.25, 0.5), false);
			b.shaped(x, HANGAR + 1, FIELD_Z1 + 1, net.minecraft.world.phys.shapes.Shapes.box(0.25, 0.0, 0.1, 0.75, 0.25, 0.5), false);
		}
		// The shaft below the field, down through the hull.
		b.box(FIELD_X0 - 1, HANGAR - 6, FIELD_Z0 - 1, FIELD_X1 + 1, HANGAR - 1, FIELD_Z1 + 1, DARK);
		b.clear(FIELD_X0, HANGAR - 6, FIELD_Z0, FIELD_X1, HANGAR - 1, FIELD_Z1);

		// The landing circle where the tractor beam sets people down.
		b.ring(CX + 0.5, HANGAR, -28.5, 1.6, 2.6, "white_concrete");
		b.disc(CX + 0.5, HANGAR, -28.5, 1.6, "light_gray_concrete");

		// TIE fighter racks along both sides: a rail under the ceiling, the TIEs hang from it
		// (drawn by the client); each rack has a gantry walkway below.
		// (Phase 18: the hangers are drawn by the client, no chains any more.)

		// Crates, stacked against the walls. Phase 18: imperial cargo containers (proxies for the
		// client's models) instead of barrels, chests and iron blocks.
		for (int z = HANGAR_Z0 + 3; z <= HANGAR_Z0 + 9; z += 3) {
			b.box(HANGAR_X1 - 3, HANGAR + 1, z, HANGAR_X1 - 2, HANGAR + 1, z + 1, PROXY);
			b.set(HANGAR_X1 - 2, HANGAR + 2, z, PROXY);
		}

		for (int x = HANGAR_X0 + 3; x <= HANGAR_X0 + 9; x += 3) {
			b.box(x, HANGAR + 1, HANGAR_Z0 + 2, x + 1, HANGAR + 1, HANGAR_Z0 + 2, PROXY);
		}

		// The store under them (chests in the floor, hidden by the containers; a click on a
		// container opens the one under it).
		for (BlockPos store : STORES) {
			b.set(store.getX(), store.getY(), store.getZ(), "chest[facing=north]");
		}

		// Stacks of cargo crates (proxies for the client's models).
		b.box(HANGAR_X1 - 3, HANGAR + 1, -32, HANGAR_X1 - 1, HANGAR + 2, -29, PROXY);
		b.box(HANGAR_X1 - 2, HANGAR + 3, -31, HANGAR_X1 - 1, HANGAR + 3, -30, PROXY);
		b.box(HANGAR_X0 + 1, HANGAR + 1, -27, HANGAR_X0 + 3, HANGAR + 2, -24, PROXY);
		b.box(HANGAR_X0 + 1, HANGAR + 3, -26, HANGAR_X0 + 2, HANGAR + 3, -25, PROXY);

		// Light: high bay lamps and a band along the walls.
		for (int x = HANGAR_X0 + 4; x <= HANGAR_X1 - 4; x += 7) {
			for (int z = HANGAR_Z0 + 4; z <= HANGAR_Z1 - 4; z += 7) {
				b.light(x, HANGAR_TOP - 1, z, 15);
				b.light(x, HANGAR + 4, z, 11);
			}
		}

		// The door to the corridor at the back.
		b.clear(CX - 2, HANGAR + 1, HANGAR_Z1, CX + 2, HANGAR + 4, HANGAR_Z1);
	}

	/** The flight control room on the west side, its window onto the hangar. */
	private static void flightControl(InteriorBuilder b) {
		b.room(CONTROL_X0, HANGAR, -59, HANGAR_X0, HANGAR + 7, -35, DARK);
		b.box(CONTROL_X0 + 1, HANGAR, -58, HANGAR_X0 - 1, HANGAR, -36, FLOOR);
		// The window wall (the hangar's west wall) and the door.
		b.box(HANGAR_X0, HANGAR + 3, -57, HANGAR_X0, HANGAR + 6, -37, PANE);
		b.clear(HANGAR_X0, HANGAR + 1, -40, HANGAR_X0, HANGAR + 3, -38);

		// Consoles under the window (proxies for the client's models), seats behind them.
		for (int z = -57; z <= -42; z++) {
			b.set(HANGAR_X0 - 1, HANGAR + 1, z, PROXY);
			b.shaped(HANGAR_X0 - 1, HANGAR + 2, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0), false);
		}

		for (int z = -56; z <= -46; z += 2) {
			b.shaped(HANGAR_X0 - 2, HANGAR + 1, z, CHAIR, false);
		}

		// Screens on the back wall.
		for (int z = -56; z <= -38; z += 3) {
			b.set(CONTROL_X0 + 1, HANGAR + 1, z, PROXY);
		}

		b.light(CONTROL_X0 + 6, HANGAR + 5, -47, 12);
		b.light(CONTROL_X0 + 6, HANGAR + 5, -40, 11);
		b.light(CONTROL_X0 + 6, HANGAR + 5, -54, 11);
	}

	/** The corridor from the hangar to the lift lobby, and the door to the reactor. */
	private static void corridor(InteriorBuilder b) {
		b.room(CX - 4, HANGAR, HANGAR_Z1, CX + 4, HANGAR + 6, LOBBY_Z1 + 1, DARK);
		b.box(CX - 3, HANGAR, HANGAR_Z1, CX + 3, HANGAR, LOBBY_Z1, FLOOR);
		b.clear(CX - 2, HANGAR + 1, HANGAR_Z1, CX + 2, HANGAR + 4, HANGAR_Z1);
		// (Phase 23: the lift is no longer at the end of the lobby but in its bay behind it.)

		for (int z = HANGAR_Z1 + 2; z <= LOBBY_Z1 - 2; z += 4) {
			b.light(CX, HANGAR + 4, z, 12);
		}

		// The door to the reactor gallery, east of the lobby.
		b.room(CX + 4, HANGAR, LOBBY_Z0 - 1, CX + 10, HANGAR + 4, LOBBY_Z0 + 3, DARK);
		b.clear(CX + 4, HANGAR + 1, LOBBY_Z0, CX + 4, HANGAR + 3, LOBBY_Z0 + 2);
		b.box(CX + 5, HANGAR, LOBBY_Z0, CX + 9, HANGAR, LOBBY_Z0 + 2, FLOOR);
		b.clear(CX + 7, HANGAR + 1, LOBBY_Z0 + 3, CX + 9, HANGAR + 3, LOBBY_Z0 + 3);
		b.light(CX + 7, HANGAR + 3, LOBBY_Z0 + 1, 11);
	}

	/**
	 * Phase 23: the turbolift's bay behind the lobby: as wide as the lift, three deep, its mouth in
	 * the lobby's end wall, the lit floor of the lift in it.
	 */
	private static void liftBay(InteriorBuilder b) {
		b.room(CX - 3, HANGAR, LIFT_Z0, CX + 3, HANGAR + 6, LIFT_Z1 + 1, DARK);
		b.clear(CX - 2, HANGAR + 1, LIFT_Z0, CX + 2, HANGAR + 4, LIFT_Z0);
		b.box(CX - 2, HANGAR, LIFT_Z0, CX + 2, HANGAR, LIFT_Z1, "white_concrete");
		b.light(CX, HANGAR + 5, LIFT_Z0 + 1, 12);

		// The lift's frame stands 0.3 into the bay on both sides (as drawn): one walked into it.
		for (int y = HANGAR + 1; y <= HANGAR + 4; y++) {
			for (int z = LIFT_Z0; z <= LIFT_Z1; z++) {
				double z0 = z == LIFT_Z0 ? 0.8 : 0.0;
				b.shaped(CX - 2, y, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, z0, 0.3, 1.0, 1.0), false);
				b.shaped(CX + 2, y, z, net.minecraft.world.phys.shapes.Shapes.box(0.7, 0.0, z0, 1.0, 1.0, 1.0), false);
			}
		}
	}

	/** The gallery from the side room to the reactor's catwalk. */
	public static final int GALLERY_X0 = CX + 6;
	public static final int GALLERY_X1 = CX + 10;
	public static final int GALLERY_Z0 = LOBBY_Z0 + 3;
	public static final int GALLERY_Z1 = 11;

	/** Phase 16: the gallery's way through the shaft wall (Phase 23: onto its landing over the open shaft). */
	public static final Doorway GALLERY = new Doorway(GALLERY_X0 + 1, HANGAR + 1, GALLERY_Z1, GALLERY_X1 - 1, HANGAR + 3, GALLERY_Z1 + 3, false);

	/**
	 * Phase 19: whether a point (plot space, row y) is in the gallery's way through the shaft wall,
	 * up to the curve: by its east side the curve passes just beyond the doorway's cells, and the
	 * wall collided there in the open passage.
	 */
	public static boolean inGallery(double x, double z, int y) {
		return y >= GALLERY.y0() && y <= GALLERY.y1() && x >= GALLERY.x0() && x < GALLERY.x1() + 1 && z >= GALLERY.z0() && z < GALLERY.z1() + 2.5;
	}

	/** Phase 16: whether a point (plot space, row y) is in the reactor shaft, up to its drawn wall. */
	public static boolean openReactor(double x, double z, int y) {
		double dx = x - REACTOR_X;
		double dz = z - REACTOR_Z;
		return y > PIT_FLOOR && y < REACTOR_TOP && dx * dx + dz * dz < REACTOR_WALL * REACTOR_WALL;
	}

	/** Phase 19: the way from the shaft floor through its wall to the lab's blast door. */
	public static final Doorway LAB_WAY = new Doorway(LAB_DOOR_X0, REACTOR_BOTTOM, LAB_WALL - 4, LAB_DOOR_X0 + 2, REACTOR_BOTTOM + 2, LAB_WALL - 1, false);

	/** Phase 19: whether a point (plot space, row y) is in the way to the lab, up to the curve. */
	public static boolean inLabWay(double x, double z, int y) {
		return y >= LAB_WAY.y0() && y <= LAB_WAY.y1() && x >= LAB_WAY.x0() && x < LAB_WAY.x1() + 1 && z >= LAB_WAY.z0() + 0.5 && z < LAB_WAY.z1() + 1;
	}

	@Override
	public List<Doorway> doorways() {
		return List.of(GALLERY, LAB_WAY);
	}

	@Override
	public boolean openRound(double x, double z, int y) {
		return openReactor(x, z, y);
	}

	private static void reactor(InteriorBuilder b) {
		double r = REACTOR_R;
		// Phase 23: the shaft goes far down under the platforms (it was a floor round the core).
		b.ringWall(REACTOR_X, PIT_FLOOR, REACTOR_TOP, REACTOR_Z, r - 0.5, r + 1.0, DARK);
		b.disc(REACTOR_X, PIT_FLOOR, REACTOR_Z, r, PROXY);
		b.disc(REACTOR_X, REACTOR_TOP, REACTOR_Z, r, DARK);

		for (int y = PIT_FLOOR + 1; y < REACTOR_TOP; y++) {
			b.disc(REACTOR_X, y, REACTOR_Z, r - 0.5, "air");
		}

		// The gallery from the lobby's side room, through the shaft wall onto its landing.
		b.room(GALLERY_X0, HANGAR, GALLERY_Z0, GALLERY_X1, HANGAR + 4, GALLERY_Z1, DARK);
		b.box(GALLERY_X0 + 1, HANGAR, GALLERY_Z0, GALLERY_X1 - 1, HANGAR, GALLERY_Z1 + 3, FLOOR);
		b.clear(GALLERY_X0 + 1, HANGAR + 1, GALLERY_Z0, GALLERY_X1 - 1, HANGAR + 3, GALLERY_Z1 + 3);
		b.light(CX + 8, HANGAR + 3, 6, 11);
		// Phase 19: the way through the shaft wall to the lab (its blast door is in the lab's wall).
		b.clear(LAB_DOOR_X0, REACTOR_BOTTOM, LAB_WALL - 3, LAB_DOOR_X0 + 2, REACTOR_BOTTOM + 2, LAB_WALL - 1);
		b.box(LAB_DOOR_X0, REACTOR_BOTTOM - 1, LAB_WALL - 3, LAB_DOOR_X0 + 2, REACTOR_BOTTOM - 1, LAB_WALL - 1, FLOOR);
		reactorWorks(b);
		// Phase 16: the wall collides where it is drawn (no more barriers a step in front of it).
		// Phase 23: from the depth up (the steelwork's barriers are left as they are).
		b.roundShell(REACTOR_X, REACTOR_Z, new double[] {REACTOR_WALL}, (int) (REACTOR_X - r - 2), PIT_FLOOR + 1, (int) (REACTOR_Z - r - 2),
				(int) (REACTOR_X + r + 2), REACTOR_TOP - 1, (int) (REACTOR_Z + r + 2), java.util.Set.of(InteriorBuilder.state(DARK).getBlock()),
				DestroyerInterior::openReactor, (x, z, y) -> inGallery(x, z, y) || inLabWay(x, z, y));
		// Phase 22: beside the way to the lab, between the shaft's wall and the lab's, there were
		// empty cells behind the drawn walls: one walked into the wall there. Now they collide.
		for (int x : new int[] {LAB_DOOR_X0 - 1, LAB_DOOR_X0 + 3}) {
			for (int z = LAB_WALL - 4; z <= LAB_WALL - 1; z++) {
				double nx = net.minecraft.util.Mth.clamp(REACTOR_X, x, x + 1) - REACTOR_X;
				double nz = net.minecraft.util.Mth.clamp(REACTOR_Z, z, z + 1) - REACTOR_Z;

				if (Math.sqrt(nx * nx + nz * nz) < REACTOR_WALL) {
					continue;
				}

				for (int y = REACTOR_BOTTOM; y <= REACTOR_BOTTOM + 2; y++) {
					if (b.isAir(x, y, z)) {
						b.set(x, y, z, PROXY);
					}
				}
			}
		}

		// Phase 23: beside the gallery's way through the shaft wall, past the curve, the cells behind
		// its drawn side walls were empty: one walked into the wall there. Now they collide.
		for (int x : new int[] {GALLERY_X0, GALLERY_X1}) {
			for (int z = GALLERY_Z1; z <= GALLERY_Z1 + 3; z++) {
				double nx = net.minecraft.util.Mth.clamp(REACTOR_X, x, x + 1) - REACTOR_X;
				double nz = net.minecraft.util.Mth.clamp(REACTOR_Z, z, z + 1) - REACTOR_Z;

				if (Math.sqrt(nx * nx + nz * nz) < REACTOR_WALL) {
					continue;
				}

				for (int y = HANGAR + 1; y <= HANGAR + 3; y++) {
					if (b.isAir(x, y, z)) {
						b.set(x, y, z, PROXY);
					}
				}
			}
		}

		// The core gives off light.
		b.light((int) REACTOR_X, HANGAR + 3, (int) (REACTOR_Z - 11), 13);
		b.light((int) REACTOR_X, HANGAR + 3, (int) (REACTOR_Z + 11), 13);
		b.light((int) (REACTOR_X - 11), HANGAR + 3, (int) REACTOR_Z, 13);
		b.light((int) (REACTOR_X + 11), HANGAR + 3, (int) REACTOR_Z, 13);
	}

	// --- Bridge deck ---------------------------------------------------------------------------

	private static void bridge(InteriorBuilder b) {
		b.room(BRIDGE_X0, PIT, WINDOW_Z, BRIDGE_X1, BRIDGE_TOP, BRIDGE_BACK, DARK);
		b.box(BRIDGE_X0 + 1, PIT, WINDOW_Z + 1, BRIDGE_X1 - 1, PIT, BRIDGE_BACK - 1, FLOOR);

		// The great window: the whole front wall from the pits to near the ceiling.
		b.box(BRIDGE_X0 + 1, PIT + 1, WINDOW_Z, BRIDGE_X1 - 1, BRIDGE_TOP - 2, WINDOW_Z, PANE);

		// The command platform along the window, and the walkway down the middle, at deck height.
		b.box(BRIDGE_X0 + 1, PIT + 1, WINDOW_Z + 1, BRIDGE_X1 - 1, DECK, PLATFORM_Z1, BLACK);
		b.box(BRIDGE_X0 + 1, DECK, WINDOW_Z + 1, BRIDGE_X1 - 1, DECK, PLATFORM_Z1, FLOOR);
		b.box(WALK_X0, PIT + 1, PLATFORM_Z1, WALK_X1, DECK, BRIDGE_BACK - 1, BLACK);
		b.box(WALK_X0, DECK, PLATFORM_Z1, WALK_X1, DECK, BRIDGE_BACK - 1, FLOOR);

		// Railings along the walkway and the platform edge (drawn by the client). Phase 19: they
		// collide as drawn, slim, with half a block more over them like a fence (whole blocks
		// before: one stopped most of a block before the drawn rail, and the half block over them
		// was as wide).
		net.minecraft.world.phys.shapes.VoxelShape west = net.minecraft.world.phys.shapes.Shapes.box(0.85 - RAIL_HALF, 0.0, 0.0, 0.85 + RAIL_HALF, 1.0, 1.0);
		net.minecraft.world.phys.shapes.VoxelShape east = net.minecraft.world.phys.shapes.Shapes.box(0.15 - RAIL_HALF, 0.0, 0.0, 0.15 + RAIL_HALF, 1.0, 1.0);
		net.minecraft.world.phys.shapes.VoxelShape edge = net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.15 - RAIL_HALF, 1.0, 1.0, 0.15 + RAIL_HALF);

		for (int z = PLATFORM_Z1 + 1; z <= BRIDGE_BACK - 1; z++) {
			if (!isStep(z)) {
				rail(b, WALK_X0, DECK + 1, z, west);
				rail(b, WALK_X1, DECK + 1, z, east);
			}
		}

		for (int x = BRIDGE_X0 + 1; x <= BRIDGE_X1 - 1; x++) {
			if (x < WALK_X0 || x > WALK_X1) {
				rail(b, x, DECK + 1, PLATFORM_Z1, edge);
			}
		}

		// Steps down into the pits.
		for (int z : new int[] {77, 89}) {
			for (int i = 0; i < 4; i++) {
				// Phase 18: drawn steps (no blackstone stairs), each cell shaped like a stair.
				for (int dz = 0; dz <= 1; dz++) {
					b.shaped(WALK_X0 - 1 - i, DECK - i, z + dz, STEP_EAST, false);
					b.shaped(WALK_X1 + 1 + i, DECK - i, z + dz, STEP_WEST, false);
				}
			}
		}

		// Rows of consoles in both pits (proxies for the client's models), everyone facing the window.
		for (int z : PIT_ROWS) {
			for (int x = BRIDGE_X0 + 2; x <= BRIDGE_X1 - 2; x++) {
				if (x >= WALK_X0 - 4 && x <= WALK_X1 + 4) {
					continue;
				}

				b.set(x, PIT + 1, z, PROXY);
				// Phase 17: a cap: nobody climbs onto the console (and stands in its screens).
				// Phase 18: high enough that nobody jumps onto it from a chair, colliding only (no
				// outline over the console). Phase 19: a block (it was a block and a half).
				b.ghost(x, PIT + 2, z, net.minecraft.world.phys.shapes.Shapes.block());

				// A seat behind every other console.
				if ((x & 1) == 0) {
					b.shaped(x, PIT + 1, z + 1, CHAIR, false);
				}
			}
		}

		// The helm at the front of the walkway: the lever on a console.
		b.box(CX - 2, DECK + 1, WINDOW_Z + 2, CX + 2, DECK + 1, WINDOW_Z + 2, PROXY);
		b.set(CX, DECK + 1, WINDOW_Z + 2, BLACK);
		// Phase 18: the helm's throttle is drawn (no vanilla lever); a slim shape to click, over a
		// cap like the rest of the helm's (from the helm's middle one stepped onto the caps).
		b.shaped(CX, DECK + 2, WINDOW_Z + 2, net.minecraft.world.phys.shapes.Shapes.or(net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
				net.minecraft.world.phys.shapes.Shapes.box(0.3, 0.0, 0.3, 0.7, 0.6, 0.7)), false);
		// The officers' stations at the ends of the platform.
		for (int x : new int[] {BRIDGE_X0 + 4, BRIDGE_X1 - 4}) {
			b.set(x, DECK + 1, WINDOW_Z + 2, PROXY);
		}

		// Phase 18: nobody climbs onto the helm or the officers' stations (one stood over them in the
		// air): half a block more that only collides (the throttle keeps its own shape).
		for (int x = BRIDGE_X0 + 1; x <= BRIDGE_X1 - 1; x++) {
			if (x != CX && b.get(x, DECK + 1, WINDOW_Z + 2).is(net.minecraft.world.level.block.Blocks.BARRIER) && b.isAir(x, DECK + 2, WINDOW_Z + 2)) {
				b.ghost(x, DECK + 2, WINDOW_Z + 2, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
			}
		}

		// The door to the command deck.
		b.clear(CX - 2, DECK + 1, BRIDGE_BACK, CX + 2, DECK + 4, BRIDGE_BACK);

		// Light: rows in the ceiling, softer light down in the pits.
		for (int z = WINDOW_Z + 3; z <= BRIDGE_BACK - 2; z += 5) {
			for (int x = BRIDGE_X0 + 4; x <= BRIDGE_X1 - 4; x += 8) {
				b.light(x, BRIDGE_TOP - 2, z, 14);
			}

			b.light(BRIDGE_X0 + 8, PIT + 3, z, 11);
			b.light(BRIDGE_X1 - 8, PIT + 3, z, 11);
		}
	}

	/**
	 * Phase 19: a railing cell: the rail as drawn, and over it the same slim shape half a block
	 * high that only collides (a fence: nobody jumps over or walks along on top of it).
	 */
	private static void rail(InteriorBuilder b, int x, int y, int z, net.minecraft.world.phys.shapes.VoxelShape shape) {
		b.set(x, y, z, PROXY);
		b.shaped(x, y, z, shape, false);

		if (b.isAir(x, y + 1, z)) {
			b.set(x, y + 1, z, PROXY);
			net.minecraft.world.phys.AABB box = shape.bounds();
			b.ghost(x, y + 1, z, net.minecraft.world.phys.shapes.Shapes.box(box.minX, 0.0, box.minZ, box.maxX, 0.5, box.maxZ));
		}
	}

	/** Whether the walkway railing has an opening for the steps there. */
	public static boolean isStep(int z) {
		return z == 77 || z == 78 || z == 89 || z == 90;
	}

	/** The command corridor, the side rooms and the turbolift behind the bridge. */
	private static void commandDeck(InteriorBuilder b) {
		// The corridor.
		b.room(CX - 4, DECK, BRIDGE_BACK, CX + 4, COMMAND_TOP, COMMAND_BACK, DARK);
		b.box(CX - 3, DECK, BRIDGE_BACK, CX + 3, DECK, COMMAND_BACK - 1, FLOOR);
		b.clear(CX - 2, DECK + 1, BRIDGE_BACK, CX + 2, DECK + 4, BRIDGE_BACK);
		b.box(CX - 2, DECK, COMMAND_BACK - 2, CX + 2, DECK, COMMAND_BACK - 1, "white_concrete");

		// The conference room (west) and the sensor room (east), with windows in their outer walls.
		b.room(SIDE_WEST, DECK, SIDE_Z0, CX - 4, COMMAND_TOP, SIDE_Z1, DARK);
		b.box(SIDE_WEST + 1, DECK, SIDE_Z0 + 1, CX - 5, DECK, SIDE_Z1 - 1, FLOOR);
		b.box(SIDE_WEST, DECK + 2, SIDE_Z0 + 2, SIDE_WEST, COMMAND_TOP - 2, SIDE_Z1 - 2, PANE);
		b.room(CX + 4, DECK, SIDE_Z0, SIDE_EAST, COMMAND_TOP, SIDE_Z1, DARK);
		b.box(CX + 5, DECK, SIDE_Z0 + 1, SIDE_EAST - 1, DECK, SIDE_Z1 - 1, FLOOR);
		b.box(SIDE_EAST, DECK + 2, SIDE_Z0 + 2, SIDE_EAST, COMMAND_TOP - 2, SIDE_Z1 - 2, PANE);
		// Doors from the corridor.
		b.clear(CX - 4, DECK + 1, 106, CX - 4, DECK + 3, 108);
		b.clear(CX + 4, DECK + 1, 106, CX + 4, DECK + 3, 108);

		// The conference table with the hologram projector in its middle. Phase 19: it collides as
		// drawn, a long table with round ends (whole blocks before: one stopped at the drawn ends
		// half a block early, round the corners).
		int tx = (SIDE_WEST + CX - 4) / 2;
		double ends = (TABLE_Z1 - TABLE_Z0) / 2;
		b.spans(tx - 6, 106, tx + 6, 108, DECK + 1, (x, z) -> {
			double nearest = Math.max(tx - 6 + 0.05 + ends, Math.min(tx + 7 - 0.05 - ends, x));
			return Math.hypot(x - nearest, z - (TABLE_Z0 + TABLE_Z1) / 2) < ends ? new double[] {0.0, TABLE_TOP} : null;
		}, java.util.Set.of());

		for (int x = tx - 5; x <= tx + 5; x += 2) {
			b.shaped(x, DECK + 1, 105, CHAIR, false);
			b.shaped(x, DECK + 1, 109, CHAIR, false);
		}
		// The sensor room's consoles along its inner wall and the tactical table.
		for (int z = SIDE_Z0 + 2; z <= SIDE_Z1 - 2; z += 2) {
			b.set(CX + 5, DECK + 1, z, z >= 105 && z <= 109 ? "air" : PROXY);

			// Phase 18: nobody climbs onto them (one stood over the drawn console).
			if (z < 105 || z > 109) {
				b.ghost(CX + 5, DECK + 2, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
			}
		}

		int sx = (CX + 4 + SIDE_EAST) / 2 + 4;
		b.box(sx - 2, DECK + 1, 105, sx + 2, DECK + 1, 109, PROXY);

		for (int z = SIDE_Z0 + 3; z <= SIDE_Z1 - 3; z += 5) {
			b.light(tx - 5, COMMAND_TOP - 2, z, 12);
			b.light(tx + 5, COMMAND_TOP - 2, z, 12);
			b.light(sx - 6, COMMAND_TOP - 2, z, 12);
			b.light(sx + 4, COMMAND_TOP - 2, z, 12);
		}

		for (int z = BRIDGE_BACK + 2; z <= COMMAND_BACK - 2; z += 4) {
			b.light(CX, COMMAND_TOP - 2, z, 12);
		}
	}
}
