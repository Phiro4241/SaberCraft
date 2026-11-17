package com.phiro.sabercraft.interior;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.block.Block;

/**
 * Phase 14: the walkable interior of the Millennium Falcon, at about real size (one block a
 * metre). Phase 15: the blocks are the rooms' shell and collision (barrier proxies where the
 * client draws furniture, smooth round walls along the ring corridor and the hold); modded clients
 * draw the panelling, furniture and the hull around it ({@code FalconVisuals}), vanilla clients see
 * the plain block rooms.
 *
 * <p>Top view, the nose to the north:
 *
 * <pre>
 *        [cargo hold]        [storage + hidden compartments]      (the two forward pods)
 *              \                       /
 *   [quarters] ( ring corridor around the main hold ) --tube-- [cockpit, 4 seats]
 *   [galley +  (  main hold: round game table, couch,  )
 *    medbay ]  (  gun well ladder up and down         ) --[boarding ramp down]
 *              (            engine room behind         )
 * </pre>
 *
 * <p>Every room opens onto the ring corridor. The ladder in the middle of the main hold climbs to
 * the upper gun turret and down to the lower one (glass domes, a seat and the gun controls). The
 * boarding ramp leads down to the hatch: standing on the lit pad at its foot takes you outside,
 * next to the ship. The lever on the pilot's console hands the ship's controls to its owner.
 * Barrels and chests are real storage, also those under the floor hatches of the storage pod.
 */
public final class FalconInterior extends InteriorPlan {
	/** Floor blocks of the main deck; people walk at y + 1. */
	public static final int FLOOR = 6;
	public static final double CX = 20.5;
	public static final double CZ = 30.5;
	/** The main hold (open up to this radius), the ring corridor around it. */
	public static final double HOLD_R = 7.0;
	public static final double RING_IN = 8.0;
	public static final double RING_OUT = 11.0;
	/** How far the barriers along the round walls reach in, so people stay clear of the smooth walls. */
	public static final double MARGIN = 0.6;
	/** The engine room: a curved room behind the corridor, between two radii and two angles (0 = east, 90 = south). */
	public static final double ENGINE_R0 = 12.5;
	public static final double ENGINE_R1 = 18.5;
	public static final double ENGINE_A0 = 50.0;
	public static final double ENGINE_A1 = 130.0;
	/** The hyperdrive in the middle of the engine room. */
	public static final double DRIVE_X = CX;
	public static final double DRIVE_Z = CZ + 15.5;
	public static final double DRIVE_R = 1.6;
	/** The game table in the hold. */
	public static final double TABLE_X = 16.5;
	public static final double TABLE_Z = 27.5;
	public static final double TABLE_R = 1.7;

	/**
	 * Phase 16: the cockpit is a round pod along z: its axis at (x, y), the inner wall's radius, the
	 * walkable room from the window (z) to the back wall (z), floor and ceiling heights.
	 */
	public static final double COCKPIT_X = 44.0;
	public static final double COCKPIT_AXIS = 9.0;
	public static final double COCKPIT_R = 3.1;
	public static final int COCKPIT_FRONT = 19;
	public static final int COCKPIT_BACK = 29;
	public static final int COCKPIT_CEILING = 10;

	private static final String HULL = "white_terracotta";
	private static final String SHELL = "light_gray_terracotta";
	private static final String PANEL = "light_gray_concrete";
	private static final String DECK = "polished_andesite";
	private static final String DARK = "polished_blackstone";
	private static final String TRIM = "gray_concrete";
	private static final String PANE = "barrier";
	private static final String PROXY = "barrier";

	@Override
	public String id() {
		return "falcon";
	}

	@Override
	public int version() {
		// 3 (Phase 16): the curved walls are barriers shaped like the drawn walls, no stepped blocks
		// poking through them any more. 4: the new cockpit, slim seats. 5 (Phase 17): collision
		// shaped like everything drawn (machines, hyperdrive, couch, domes, ramp), the engine
		// room's outer wall round, furniture that cannot be climbed. 6 (Phase 18): the couch and
		// table as drawn, no invisible floors and caps, a floor in the lower turret. 7: the crew
		// (they stand in their places), a cap over the galley's barrel. 8 (Phase 19): nobody of the
		// crew collides, the table, the couch and the gun controls as drawn, the upper dome without
		// a flat invisible ceiling under it.
		return 8;
	}

	@Override
	public BlockPos size() {
		return new BlockPos(48, 18, 52);
	}

	@Override
	public Vec3 entry() {
		return new Vec3(35.5, FLOOR + 1, 31.5);
	}

	@Override
	public float entryYaw() {
		return 90.0F;
	}

	@Override
	public List<AABB> exits() {
		return List.of(new AABB(33.0, 3.0, 39.0, 38.0, 6.0, 42.0));
	}

	@Override
	public List<Lift> lifts() {
		return List.of();
	}

	@Override
	public BlockPos helm() {
		return new BlockPos(42, FLOOR + 2, COCKPIT_FRONT + 1);
	}

	@Override
	public String ambient() {
		return "ship.interior_falcon";
	}

	@Override
	public List<Room> rooms() {
		return List.of(
				new Room("cockpit", new AABB(41, 7, 16, 47, 10, 29)),
				new Room("cockpit_tube", new AABB(30, 7, 23, 39, 9, 25)),
				new Room("top_turret", new AABB(18, 12, 28, 24, 15, 34)),
				new Room("bottom_turret", new AABB(18, 1, 28, 24, 4, 34)),
				new Room("quarters", new AABB(4, 7, 22, 9, 10, 29)),
				new Room("galley", new AABB(5, 7, 33, 9, 10, 40)),
				new Room("engine_room", new AABB(8, 7, 41, 33, 11, 50)),
				new Room("cargo_hold", new AABB(9, 7, 2, 16, 11, 18)),
				new Room("storage", new AABB(26, 7, 2, 33, 11, 18)),
				new Room("ramp", new AABB(33, 3, 30, 38, 11, 42)),
				new Room("main_hold", new AABB(14, 7, 24, 28, 11, 38)),
				new Room("corridor", new AABB(9, 7, 19, 32, 10, 42)));
	}

	/** Doorways from the hold to the corridor, and from the corridor to every room. */
	public static final List<Doorway> DOORWAYS = List.of(
			new Doorway(20, FLOOR + 1, 21, 21, FLOOR + 3, 24, false),   // hold, north
			new Doorway(20, FLOOR + 1, 37, 21, FLOOR + 3, 40, false),   // hold, south
			new Doorway(11, FLOOR + 1, 30, 14, FLOOR + 3, 31, true),   // hold, west
			new Doorway(27, FLOOR + 1, 30, 30, FLOOR + 3, 31, true),   // hold, east
			new Doorway(9, FLOOR + 1, 25, 11, FLOOR + 2, 25, true),    // quarters
			new Doorway(9, FLOOR + 1, 35, 11, FLOOR + 2, 35, true),    // galley
			new Doorway(20, FLOOR + 1, 41, 21, FLOOR + 3, 43, false),   // engine room
			new Doorway(12, FLOOR + 1, 18, 13, FLOOR + 3, 22, false),   // cargo hold
			new Doorway(27, FLOOR + 1, 18, 28, FLOOR + 3, 22, false),   // storage
			new Doorway(31, FLOOR + 1, 30, 32, FLOOR + 3, 31, true),   // ramp
			new Doorway(29, FLOOR + 1, 23, 31, FLOOR + 2, 24, true),   // cockpit tube
			new Doorway(39, FLOOR + 1, 23, 41, FLOOR + 2, 24, true));  // into the cockpit

	/** The smooth walls as drawn: the hold's, both of the ring corridor's, the engine room's inner one. */
	public static final double HOLD_WALL = HOLD_R + 0.05;
	public static final double RING_IN_WALL = RING_IN + 0.05;
	public static final double RING_OUT_WALL = RING_OUT - 0.05;
	public static final double ENGINE_WALL = ENGINE_R0 + 0.05;
	/** Phase 17: the engine room's outer wall (above the machines) is round too, drawn here. */
	public static final double ENGINE_OUTER_WALL = ENGINE_R1 + 0.45;
	/** Phase 17: the front of the machines along that wall, and how high they are. */
	public static final double MACHINE_R = ENGINE_R1 - 1.5;
	public static final double MACHINE_HEIGHT = 2.0;
	/** Phase 17: the hyperdrive's housing, as drawn (its collars are the widest part). */
	public static final double DRIVE_HOUSING = 2.12;
	/** Phase 17: the window round the hyperdrive's core, between its collars (drawn, and where one stops). */
	public static final double DRIVE_WINDOW = 1.95;
	/** Phase 17: the couch round the game table: inner and outer radius (as drawn). */
	public static final double COUCH_IN = 2.4;
	public static final double COUCH_OUT = 3.25;

	/**
	 * Phase 17: whether the couch is drawn at an angle round the game table (degrees, 0 = east,
	 * 90 = south): in pieces of 10 degrees from 60 on, on the table's far side, clear of the
	 * hold's wall. The model and the collision both ask this.
	 */
	public static boolean couchAt(double angle) {
		double a = net.minecraft.util.Mth.positiveModulo(angle - 60.0, 360.0);
		int k = (int) Math.floor(a / 10.0);

		if (k < 0 || k >= 24) {
			return false;
		}

		double mid = Math.toRadians(60.0 + k * 10.0 + 5.0);
		double mx = TABLE_X + Math.cos(mid) * 2.85;
		double mz = TABLE_Z + Math.sin(mid) * 2.85;
		return Math.cos(mid) * 2.85 < 1.2 && Math.hypot(mx - CX, mz - CZ) < HOLD_R - MARGIN - 0.1;
	}

	/** Phase 18: the height of the couch's cushion over the floor, and where its back begins (radius from the table). */
	public static final double COUCH_SEAT = 0.45;
	/** Phase 19: the height of the couch's back and of the game table's top (as drawn). */
	public static final double COUCH_TOP = 1.15;
	public static final double TABLE_TOP = 0.77;
	public static final double COUCH_BACK = COUCH_IN + (COUCH_OUT - COUCH_IN) * 0.72;

	/** Phase 18: whether a point (plot space) is in the couch's back, seen from above. */
	public static boolean inCouchBack(double x, double z) {
		return inCouch(x, z) && Math.hypot(x - TABLE_X, z - TABLE_Z) >= COUCH_BACK;
	}

	/** Phase 17: whether a point (plot space) is in the couch, seen from above. */
	public static boolean inCouch(double x, double z) {
		double d = Math.hypot(x - TABLE_X, z - TABLE_Z);
		return d >= COUCH_IN && d <= COUCH_OUT && couchAt(Math.toDegrees(Math.atan2(z - TABLE_Z, x - TABLE_X)));
	}

	/**
	 * Phase 17: whether a point (plot space, row y) lies in one of the passages of the doorways
	 * through the curved walls: between the doorway's sides, a little beyond its cells along the
	 * way through. The curved wall is drawn open there (between the sides), so it is open to walk
	 * through too; it used to collide in the slivers where the curve passes just outside the
	 * doorway's cells.
	 */
	public static boolean inPassage(double x, double z, int y) {
		for (Doorway door : DOORWAYS) {
			if (y < door.y0() || y > door.y1()) {
				continue;
			}

			boolean in = door.alongX() ? z >= door.z0() && z <= door.z1() + 1 && x >= door.x0() - 1.5 && x <= door.x1() + 2.5
					: x >= door.x0() && x <= door.x1() + 1 && z >= door.z0() - 1.5 && z <= door.z1() + 2.5;

			if (in && !beyondUncrossed(door, x, z)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 17: whether a point lies beyond one of the full round walls that a doorway does not
	 * go through (the hold's doorways reach out 1.5 blocks, into the ring corridor's outer wall,
	 * which is drawn closed there).
	 */
	public static boolean beyondUncrossed(Doorway door, double x, double z) {
		double near = Math.hypot(Math.max(0.0, Math.max(door.x0() - CX, CX - door.x1() - 1)), Math.max(0.0, Math.max(door.z0() - CZ, CZ - door.z1() - 1)));
		double far = Math.hypot(Math.max(Math.abs(door.x0() - CX), Math.abs(door.x1() + 1 - CX)), Math.max(Math.abs(door.z0() - CZ), Math.abs(door.z1() + 1 - CZ)));
		double d = Math.hypot(x - CX, z - CZ);

		for (double r : new double[] {HOLD_WALL, RING_IN_WALL, RING_OUT_WALL}) {
			if (far <= r && d >= r || near >= r && d <= r) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 16: whether a point (plot space) at row y is inside one of the round rooms, up to their
	 * smooth walls: the hold, the ring corridor, the engine room (its inner wall only is round).
	 */
	public static boolean open(double x, double z, int y) {
		int air0 = FLOOR + 1;
		double dx = x - CX;
		double dz = z - CZ;
		double d = Math.sqrt(dx * dx + dz * dz);

		if (y >= air0 && y <= air0 + 3 && d < HOLD_WALL) {
			return true;
		}

		if (y >= air0 && y <= air0 + 2 && d > RING_IN_WALL && d < RING_OUT_WALL) {
			return true;
		}

		if (cockpitOpen(x, z, y)) {
			return true;
		}

		if (y >= air0 && y <= air0 + 3 && d > ENGINE_WALL && d < ENGINE_OUTER_WALL) {
			// The ends of the engine room are walls of whole blocks: by the cell's middle.
			double angle = Math.toDegrees(Math.atan2(Math.floor(z) + 0.5 - CZ, Math.floor(x) + 0.5 - CX));
			return angle >= ENGINE_A0 && angle <= ENGINE_A1;
		}

		return false;
	}

	/** Half the cockpit's width in row y (the narrowest within the row), or -1 above or below the room. */
	public static double cockpitHalfWidth(int y) {
		if (y < FLOOR + 1 || y >= COCKPIT_CEILING) {
			return -1.0;
		}

		double dy = Math.max(Math.abs(y - COCKPIT_AXIS), Math.abs(y + 1 - COCKPIT_AXIS));
		return Math.sqrt(COCKPIT_R * COCKPIT_R - dy * dy);
	}

	/**
	 * Phase 17: the solid part of the cockpit's row y at a distance from its axis (x): below the
	 * round wall's lower half and above its upper half (shares of the row), or {@code null}.
	 */
	public static double @Nullable [] podSpan(double x, int y) {
		double w = Math.abs(x - COCKPIT_X);

		if (w >= COCKPIT_R) {
			return new double[] {0.0, 1.0};
		}

		double h = Math.sqrt(COCKPIT_R * COCKPIT_R - w * w);
		double low = COCKPIT_AXIS - h - y;
		double high = COCKPIT_AXIS + h - y;
		boolean below = low > 0.0;
		boolean above = high < 1.0;

		if (below && above) {
			return new double[] {0.0, 1.0};
		}

		return below ? new double[] {0.0, Math.min(1.0, low)} : above ? new double[] {Math.max(0.0, high), 1.0} : null;
	}

	/** Phase 17: where the window cone ends in front (plot z) and its radius there; its radius where it begins (z = COCKPIT_FRONT). */
	public static final double WINDOW_TIP = 15.8;
	public static final double WINDOW_TIP_R = 1.3;
	public static final double WINDOW_R0 = COCKPIT_R + 0.28 - 0.05;

	/** Phase 17: the inner radius of the window cone at z (in front of the pod), a little inside the glass. */
	public static double coneRadius(double z) {
		double t = (COCKPIT_FRONT - z) / (COCKPIT_FRONT - WINDOW_TIP);
		return WINDOW_R0 + (WINDOW_TIP_R - WINDOW_R0) * t - 0.08;
	}

	/**
	 * Phase 17: as {@link #podSpan}, at any z: in the pod its round wall, in front of it the window
	 * cone (whose floor is level with the pod's: drawn as a ledge under the glass) up to the
	 * ceiling's height.
	 */
	public static double @Nullable [] noseSpan(double x, double z, int y) {
		double[] span = noseSpan0(x, z, y);

		// In the bottom row, wherever the wall is at all the column is solid to the top: one does
		// not climb onto the round wall's lower lip (and stand with the body in the wall above).
		if (y == FLOOR + 1) {
			return span != null && span[0] <= 0.0 ? new double[] {0.0, 1.0} : span;
		}

		// In the pod the lip is two rows high: on the bottom row's lip one stood with the shoulder in
		// the arches on the wall above (one cannot jump two rows; walking along the wall, the
		// bottom row stops one at the same place).
		if (y == FLOOR + 2 && z >= COCKPIT_FRONT) {
			double[] below = noseSpan0(x, z, FLOOR + 1);

			if (below != null && below[0] <= 0.0) {
				return new double[] {0.0, 1.0};
			}
		}

		return span;
	}

	private static double @Nullable [] noseSpan0(double x, double z, int y) {
		if (z >= COCKPIT_FRONT) {
			return podSpan(x, y);
		}

		if (z < WINDOW_TIP) {
			return new double[] {0.0, 1.0};
		}

		double r = coneRadius(z);
		double w = Math.abs(x - COCKPIT_X);

		if (w >= r) {
			return new double[] {0.0, 1.0};
		}

		double h = Math.sqrt(r * r - w * w);
		double lowY = Math.max(FLOOR + 1, COCKPIT_AXIS - h);
		double highY = Math.min(COCKPIT_CEILING, COCKPIT_AXIS + h);

		if (highY - lowY < 0.1) {
			return new double[] {0.0, 1.0};
		}

		double low = lowY - y;
		double high = highY - y;
		boolean below = low > 0.0;
		boolean above = high < 1.0;

		if (below && above) {
			return new double[] {0.0, 1.0};
		}

		return below ? new double[] {0.0, Math.min(1.0, low)} : above ? new double[] {Math.max(0.0, high), 1.0} : null;
	}

	/** Phase 16: whether a point (plot space, row y) is inside the round cockpit, up to its drawn wall. */
	public static boolean cockpitOpen(double x, double z, int y) {
		double half = cockpitHalfWidth(y);
		return half > 0 && z >= COCKPIT_FRONT + 1 && z < COCKPIT_BACK && Math.abs(x - COCKPIT_X) < half;
	}

	/** The seats: four in the cockpit, the engineer's, the couch round the table, the two gunners'. */
	public static final List<Seat> SEATS = List.of(
			// The front seats a little back in their cells: room for the knees under the dashboard.
			new Seat(new BlockPos(42, FLOOR + 1, 21), new Vec3(42.5, FLOOR + 1.575, 21.7), 180.0F, Seat.PILOT),
			new Seat(new BlockPos(45, FLOOR + 1, 21), new Vec3(45.5, FLOOR + 1.575, 21.7), 180.0F, Seat.COPILOT),
			Seat.chair(42, FLOOR + 1, 24, 0.575, 180.0F, Seat.CHAIR),
			Seat.chair(45, FLOOR + 1, 24, 0.575, 180.0F, Seat.CHAIR),
			Seat.chair(25, FLOOR + 1, 31, 0.5, -90.0F, Seat.CHAIR),
			Seat.chair(20, FLOOR + 6, 28, 0.5, 180.0F, Seat.GUNNER),
			Seat.chair(20, 1, 28, 0.5, 180.0F, Seat.GUNNER),
			couch(80.0F), couch(105.0F), couch(130.0F));

	/** A place on the couch at that angle round the table, facing the table. */
	private static Seat couch(float angle) {
		double a = Math.toRadians(angle);
		double x = TABLE_X + Math.cos(a) * 2.75;
		double z = TABLE_Z + Math.sin(a) * 2.75;
		// Facing the table: Minecraft yaw of the direction (table - seat).
		float yaw = (float) Math.toDegrees(Math.atan2(-(TABLE_X - x), TABLE_Z - z));
		return new Seat(BlockPos.containing(x, FLOOR + 1, z), new Vec3(x, FLOOR + 1.45, z), yaw, Seat.COUCH);
	}

	@Override
	public List<Seat> seats() {
		return SEATS;
	}

	/** The doors: to the quarters, the galley, the engine room, both forward holds, the cockpit. */
	public static final List<Door> DOORS = List.of(
			Door.wall(true, 10, 25, 25, FLOOR + 1, FLOOR + 2),
			Door.wall(true, 10, 35, 35, FLOOR + 1, FLOOR + 2),
			Door.wall(false, 42, 20, 21, FLOOR + 1, FLOOR + 3),
			Door.wall(false, 18, 12, 13, FLOOR + 1, FLOOR + 3),
			Door.wall(false, 18, 27, 28, FLOOR + 1, FLOOR + 3),
			Door.wall(true, 38, 23, 24, FLOOR + 1, FLOOR + 2));

	@Override
	public List<Door> doors() {
		return DOORS;
	}

	/** The hyperdrive lever on the dashboard (co-pilot's side), the engineering station's desk and bank. */
	public static final List<Console> CONSOLES = List.of(
			new Console(new BlockPos(44, FLOOR + 1, COCKPIT_FRONT + 1), Console.HYPERSPACE),
			new Console(new BlockPos(45, FLOOR + 1, COCKPIT_FRONT + 1), Console.HYPERSPACE),
			// Phase 17: the caps over the dashboard and the engineering desk (one cannot climb onto them).
			new Console(new BlockPos(44, FLOOR + 2, COCKPIT_FRONT + 1), Console.HYPERSPACE),
			new Console(new BlockPos(45, FLOOR + 2, COCKPIT_FRONT + 1), Console.HYPERSPACE),
			new Console(new BlockPos(26, FLOOR + 2, 30), Console.STATUS),
			new Console(new BlockPos(26, FLOOR + 2, 31), Console.STATUS),
			new Console(new BlockPos(26, FLOOR + 2, 32), Console.STATUS),
			new Console(new BlockPos(26, FLOOR + 1, 29), Console.STATUS),
			new Console(new BlockPos(26, FLOOR + 1, 30), Console.STATUS),
			new Console(new BlockPos(26, FLOOR + 1, 31), Console.STATUS),
			new Console(new BlockPos(26, FLOOR + 1, 32), Console.STATUS),
			new Console(new BlockPos(26, FLOOR + 1, 33), Console.STATUS),
			new Console(new BlockPos(27, FLOOR + 2, 31), Console.LIGHTS),
			new Console(new BlockPos(27, FLOOR + 1, 31), Console.LIGHTS));

	@Override
	public List<Console> consoles() {
		return CONSOLES;
	}

	@Override
	public @Nullable Seat seatFor(BlockPos cell, Vec3 hit) {
		Seat exact = super.seatFor(cell, hit);

		if (exact != null || cell.getY() != FLOOR + 1 && cell.getY() != FLOOR + 2) {
			return exact;
		}

		// The couch: the nearest place on it to where it was clicked (Phase 18: judged by the point
		// clicked, not the cell's middle, so its ends and its back count too).
		double hx = Math.min(Math.max(hit.x, cell.getX() + 0.001), cell.getX() + 0.999);
		double hz = Math.min(Math.max(hit.z, cell.getZ() + 0.001), cell.getZ() + 0.999);
		double d = Math.hypot(hx - TABLE_X, hz - TABLE_Z);

		if (d < COUCH_IN - 0.15 || d > COUCH_OUT + 0.15 || !couchAt(Math.toDegrees(Math.atan2(hz - TABLE_Z, hx - TABLE_X)))) {
			return null;
		}

		Seat best = null;

		for (Seat seat : SEATS) {
			if (seat.role().equals(Seat.COUCH) && (best == null || seat.at().distanceToSqr(hit) < best.at().distanceToSqr(hit))) {
				best = seat;
			}
		}

		return best;
	}

	@Override
	public void build(InteriorBuilder b) {
		hull(b);
		carve(b);
		corridor(b);
		mainHold(b);
		turrets(b);
		cockpit(b);
		quarters(b);
		galley(b);
		engineRoom(b);
		cargo(b);
		ramp(b);
		roundShell(b);
	}

	/**
	 * Phase 18: the Falcon's people: the smuggler by the game table, the tall furry mechanic at the
	 * hyperdrive, a rebel pilot checking the cargo. They stand clear of the walkways.
	 */
	public static final List<Crew> CREW = List.of(
			Crew.at(Crew.SMUGGLER, Crew.TALK, 19.2, FLOOR + 1, 24.8, -35.0F).as("smuggler"),
			Crew.at(Crew.WOOKIEE, Crew.WORK, DRIVE_X - DRIVE_HOUSING - 0.85, FLOOR + 1, DRIVE_Z, 270.0F).as("wookiee"),
			Crew.at(Crew.REBEL, Crew.WORK, 13.0, FLOOR + 1, 6.4, 180.0F).as("rebel"));

	@Override
	public List<Crew> crew() {
		return CREW;
	}

	/**
	 * Phase 16: the cells the smooth walls run through. A block there would stick out of the drawn
	 * wall on one side (its corners showed through the curve and flickered, and people bumped into
	 * nothing or walked into the wall); instead each is a barrier that collides exactly up to the
	 * drawn wall. Furniture (barriers already) and the doorways stay as they are.
	 */
	private static void roundShell(InteriorBuilder b) {
		int air0 = FLOOR + 1;
		java.util.Set<net.minecraft.world.level.block.Block> shell = java.util.Set.of(InteriorBuilder.state(HULL).getBlock(),
				InteriorBuilder.state(SHELL).getBlock(), InteriorBuilder.state(PANEL).getBlock(), InteriorBuilder.state(DECK).getBlock(),
				InteriorBuilder.state(DARK).getBlock(), InteriorBuilder.state(TRIM).getBlock());
		b.roundShell(CX, CZ, new double[] {HOLD_WALL, RING_IN_WALL, RING_OUT_WALL, ENGINE_WALL, ENGINE_OUTER_WALL}, 0, air0, 0, 47, air0 + 3, 51, shell,
				// The tube to the cockpit is cleared air; the engine room's outer radius passes through it.
				(x, z, y) -> open(x, z, y) || inPassage(x, z, y) || x >= 30 && x < 39 && z >= 23 && z < 25 && y >= air0 && y <= air0 + 1,
				(x, z, y) -> doorAt((int) Math.floor(x), y, (int) Math.floor(z)));
		solids(b, shell);
	}

	/**
	 * Phase 17: collision shaped like what is drawn, for everything that is not a whole block:
	 * the machines along the engine room's outer wall and the hyperdrive, the couch and the game
	 * table, the glass domes of the gun turrets, the ramp; caps over furniture one should not
	 * climb onto (seats, desks, bunks, the counter, the bed), so nobody stands with their head in
	 * the ceiling's fittings.
	 */
	private static void solids(InteriorBuilder b, java.util.Set<net.minecraft.world.level.block.Block> shell) {
		int y0 = FLOOR + 1;
		java.util.Set<net.minecraft.world.level.block.Block> proxies = java.util.Set.of(net.minecraft.world.level.block.Blocks.BARRIER);
		// The machines: from their front to the wall behind, two high; the hyperdrive.
		ProxyShapes.Solid machines = (x, z) -> {
			double d = Math.hypot(x - CX, z - CZ);
			double angle = Math.toDegrees(Math.atan2(Math.floor(z) + 0.5 - CZ, Math.floor(x) + 0.5 - CX));
			return d > MACHINE_R && angle >= ENGINE_A0 && angle <= ENGINE_A1;
		};

		for (int y = y0; y < y0 + MACHINE_HEIGHT; y++) {
			b.solid(4, 38, 37, 51, y, machines, 1.0, proxies);
		}

		// The hyperdrive: the window round its core, the wider collars at its foot and head.
		for (int y = y0; y <= y0 + 3; y++) {
			int row = y - y0;
			b.spans((int) DRIVE_X - 3, (int) DRIVE_Z - 3, (int) DRIVE_X + 3, (int) DRIVE_Z + 3, y, (x, z) -> {
				double d = Math.hypot(x - DRIVE_X, z - DRIVE_Z);

				if (d < DRIVE_WINDOW) {
					return new double[] {0.0, 1.0};
				}

				if (d < DRIVE_HOUSING) {
					return row == 0 ? new double[] {0.0, 0.6} : row == 3 ? new double[] {0.4, 1.0} : null;
				}

				return null;
			}, java.util.Set.of());
		}

		// Phase 18: the game table and the couch as drawn. The couch's seat collides up to the
		// cushion (one can step onto it, and nothing is over the head of someone sitting there:
		// the old cap at shoulder height suffocated them), its back and the table two rows high
		// (nobody climbs onto them and stands in the air over the table top).
		// Phase 19: the table's first row up to its drawn top (a whole block before: one bumped
		// into air over its edge).
		b.spans(12, 22, 22, 33, y0, (x, z) -> Math.hypot(x - TABLE_X, z - TABLE_Z) < TABLE_R + 0.08 ? new double[] {0.0, TABLE_TOP}
				: inCouchBack(x, z) ? new double[] {0.0, 1.0} : inCouch(x, z) ? new double[] {0.0, COUCH_SEAT} : null, java.util.Set.of());
		// Nobody stands on them: the couch's back and the table are two rows high, as before (the
		// second row, collision only above the table, is never met: one is stopped by the table's
		// edge first). Out of a jump's reach from the cushion; lower, one stood in the air over the
		// back's rounded top.
		b.spans(12, 22, 22, 33, y0 + 1, (x, z) -> Math.hypot(x - TABLE_X, z - TABLE_Z) < TABLE_R + 0.08 || inCouchBack(x, z) ? new double[] {0.0, 1.0} : null,
				java.util.Set.of());

		// The glass domes: one walks up to the glass, not into an invisible ring a block before it.
		// The lower one is a bowl: its glass rises from under the feet, so one can step a little
		// way up it; the upper one closes over the head.
		java.util.Set<net.minecraft.world.level.block.Block> pane = java.util.Set.of(net.minecraft.world.level.block.Blocks.BARRIER);

		for (int y : new int[] {1, 2, 3, FLOOR + 6, FLOOR + 7, FLOOR + 8}) {
			// The old ring of plain barriers goes (not the gunner's seat and controls).
			for (int x = 15; x <= 26; x++) {
				for (int z = 25; z <= 36; z++) {
					if (b.get(x, y, z).is(net.minecraft.world.level.block.Blocks.BARRIER) && Math.hypot(x + 0.5 - CX, z + 0.5 - CZ) > 2.4
							&& !(x == 20 && (z == 27 || z == 28) && (y == 1 || y == FLOOR + 6))) {
						// Phase 18: only the gunner's seat and controls stay (the ring's barriers over the
						// controls stayed too: an invisible wall at head height in front of the gunner).
						b.set(x, y, z, "air");
					}
				}
			}
		}

		for (int y = 1; y <= 3; y++) {
			int row = y;
			b.spans(15, 25, 26, 36, y, (x, z) -> domeSpan(Math.hypot(x - CX, z - CZ), row, false), pane);
		}

		// Phase 19: up to the top of the dome (a flat invisible ceiling closed it a block lower).
		for (int x = 15; x <= 26; x++) {
			for (int z = 25; z <= 36; z++) {
				if (b.get(x, FLOOR + 9, z).is(net.minecraft.world.level.block.Blocks.BARRIER)) {
					b.set(x, FLOOR + 9, z, "air");
				}
			}
		}

		for (int y = FLOOR + 6; y <= FLOOR + 9; y++) {
			int row = y;
			b.spans(15, 25, 26, 36, y, (x, z) -> domeSpan(Math.hypot(x - CX, z - CZ), row, true), pane);
		}

		// Caps over furniture: they stand right over what is drawn (one is stopped by the furniture
		// itself, the cap only keeps people from climbing onto it and standing with the head in
		// what hangs over it). Phase 19: none over the game table and the couch any more.
		// Phase 18: a whole block high (one stepped from a half cap onto the next).
		for (int x = 43; x <= 45; x++) {
			cap(b, x, y0 + 1, COCKPIT_FRONT + 1, (px, pz) -> true, 1.0);
		}

		for (int z = 29; z <= 33; z++) {
			cap(b, 26, y0 + 1, z, (px, pz) -> true, 1.0);
		}

		// (and over the barrel beside it: from its top one walked into the counter's cap).
		for (int z = 33; z <= 36; z++) {
			cap(b, 5, y0 + 1, z, (px, pz) -> true, 1.0);
		}

		// Phase 18: nobody stands on the chest beside the bed (the shelf over the bed was in the
		// eyes); the cap only collides, so the chest is still clicked through it.
		b.ghost(6, y0 + 1, 39, Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));

		// Phase 18: the medical bed collides as drawn (one may sit or stand on it), no cap.
		for (int x = 7; x <= 8; x++) {
			b.set(x, y0, 38, "air");
			b.shaped(x, y0, 38, Shapes.box(x == 7 ? 0.05 : 0.0, 0.0, 0.1, x == 7 ? 1.0 : 0.95, 0.68, 0.9), false);
		}

		for (int z : new int[] {22, 25, 28}) {
			for (int x = 4; x <= 6; x++) {
				// The upper bunk: the space between the bunks is closed, nobody lies in the lower one standing up.
				if (b.isAir(x, y0 + 1, z)) {
					b.set(x, y0 + 1, z, PROXY);
				}
			}
		}


		// The gun controls collide as drawn, with the cap over them (one stood on them with the head
		// in their screen). Phase 19: the lower dome's glass shape had replaced the lower one's (one
		// walked into the controls).
		for (int y : new int[] {1, FLOOR + 6}) {
			b.set(20, y, 27, "air");
			b.shaped(20, y, 27, Shapes.box(0.1, 0.0, 0.2, 0.9, 0.9, 0.9), false);
			cap(b, 20, y + 1, 27, (px, pz) -> true);
		}

		for (Seat seat : SEATS) {
			if (!seat.role().equals(Seat.COUCH)) {
				double sx = seat.at().x;
				double sz = seat.at().z;
				cap(b, seat.cell().getX(), seat.cell().getY() + 1, seat.cell().getZ(), (px, pz) -> Math.abs(px - sx) < 0.32 && Math.abs(pz - sz) < 0.32);
			}
		}
	}

	/** Phase 17: the turrets' glass domes as drawn: centre height, radius, and the collar round each (from, to, radius). */
	public static final double TOP_DOME_Y = FLOOR + 6.0;
	public static final double TOP_DOME_R = 4.0;
	public static final double BOTTOM_DOME_Y = 4.0;
	public static final double BOTTOM_DOME_R = 4.2;
	public static final double COLLAR_R = 4.05;

	/**
	 * Phase 17: the solid part of a turret's row at a distance from the middle: outside the glass
	 * (above the upper dome, below the lower one), and the collar round the dome's rim; only out
	 * to the glass's outer edge, nothing in the open space round the hull.
	 */
	static double @Nullable [] domeSpan(double r, int y, boolean upper) {
		if (r > 4.6) {
			return null;
		}

		if (upper) {
			// Below 15 degrees up the dome stands the collar.
			double rim = TOP_DOME_Y + TOP_DOME_R * Math.sin(Math.toRadians(15.0));

			if (y + 1 <= rim) {
				return r > COLLAR_R ? new double[] {0.0, 1.0} : null;
			}

			if (r >= TOP_DOME_R) {
				return new double[] {0.0, 1.0};
			}

			double surface = TOP_DOME_Y + Math.sqrt(TOP_DOME_R * TOP_DOME_R - r * r);
			double from = Math.max(surface, r > COLLAR_R ? y : rim) - y;
			return from < 1.0 ? new double[] {Math.max(0.0, from), 1.0} : null;
		}

		double rim = BOTTOM_DOME_Y - BOTTOM_DOME_R * Math.sin(Math.toRadians(15.0));

		if (y >= rim) {
			return r > COLLAR_R ? new double[] {0.0, 1.0} : null;
		}

		if (r >= BOTTOM_DOME_R) {
			return new double[] {0.0, 1.0};
		}

		double surface = BOTTOM_DOME_Y - Math.sqrt(BOTTOM_DOME_R * BOTTOM_DOME_R - r * r);
		double to = surface - y;
		return to > 0.0 ? new double[] {0.0, Math.min(1.0, to)} : null;
	}

	/** Phase 17: a barrier half a block high over a piece of furniture (where {@code solid}), if nothing else is there. */
	private static void cap(InteriorBuilder b, int x, int y, int z, ProxyShapes.Solid solid) {
		cap(b, x, y, z, solid, 0.5);
	}

	/**
	 * Phase 18: a cap {@code height} high over a piece of furniture; where a shaped barrier is
	 * already (the cockpit's round wall), both together (the rear seats by the wall had no cap, so
	 * one climbed onto them and stood against the wall's invisible lip).
	 */
	private static void cap(InteriorBuilder b, int x, int y, int z, ProxyShapes.Solid solid, double height) {
		b.spans(x, z, x, z, y, (px, pz) -> solid.at(px, pz) ? new double[] {0.0, height} : null, java.util.Set.of());
	}


	/** Whether a cell is in one of the doorways. */
	public static boolean doorAt(int x, int y, int z) {
		for (Doorway door : DOORWAYS) {
			if (door.contains(x, y, z)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public List<Doorway> doorways() {
		return DOORWAYS;
	}

	@Override
	public boolean openRound(double x, double z, int y) {
		return open(x, z, y);
	}

	/** Whether a cell lies in the engine room's ring sector (radius and angle around the centre). */
	public static boolean inEngineSector(double x, double z, double r0, double r1, double widen) {
		double dx = x - CX;
		double dz = z - CZ;
		double d = Math.sqrt(dx * dx + dz * dz);
		double angle = Math.toDegrees(Math.atan2(dz, dx));
		return d > r0 && d <= r1 && angle >= ENGINE_A0 - widen && angle <= ENGINE_A1 + widen;
	}

	private static void sector(InteriorBuilder b, int y0, int y1, double r0, double r1, double widen, String state) {
		for (int x = 0; x <= 47; x++) {
			for (int z = 30; z <= 51; z++) {
				if (inEngineSector(x + 0.5, z + 0.5, r0, r1, widen)) {
					for (int y = y0; y <= y1; y++) {
						b.set(x, y, z, state);
					}
				}
			}
		}
	}

	/** The solid hull: the saucer and the shells of everything that sticks out of it. */
	private static void hull(InteriorBuilder b) {
		for (int y = FLOOR - 1; y <= FLOOR + 5; y++) {
			b.disc(CX, y, CZ, 16.0, y == FLOOR - 1 || y == FLOOR + 5 ? SHELL : HULL);
		}

		b.box(3, 5, 21, 10, 11, 29, HULL);      // quarters
		b.box(4, 5, 32, 10, 11, 40, HULL);      // galley and medbay
		sector(b, 5, 11, ENGINE_R0 - 1.0, ENGINE_R1 + 0.5, 6.0, HULL); // engine room
		b.box(8, 5, 1, 16, 11, 18, HULL);       // port cargo pod
		b.box(25, 5, 1, 33, 11, 18, HULL);      // starboard pod
		b.box(29, 6, 22, 41, 9, 25, HULL);      // cockpit tube (the pod has no blocks: see cockpit())
		b.box(32, 1, 29, 38, 11, 42, HULL);     // boarding ramp
		// The gun turrets above and below the middle (the glass is drawn by the client).
		b.disc(CX, FLOOR + 5, CZ, 3.5, SHELL);
		b.disc(CX, 0, CZ, 3.5, PANE);
		b.ringWall(CX, 1, 4, CZ, 2.5, 3.5, PANE);
		b.disc(CX, 4, CZ, 3.5, SHELL);
	}

	/** All rooms cut out of the hull, before anything is put into them. */
	private static void carve(InteriorBuilder b) {
		int air0 = FLOOR + 1;
		b.clear(4, air0, 22, 8, air0 + 2, 28);        // quarters (Phase 16: x to 8, clear of the corridor's curve)
		b.clear(5, air0, 33, 8, air0 + 2, 39);        // galley
		sector(b, air0, air0 + 3, ENGINE_R0, ENGINE_R1, 0.0, "air"); // engine room (Phase 17: its outer wall's cells shaped, see roundShell)
		b.clear(9, air0, 2, 15, air0 + 3, 17);        // cargo hold
		b.clear(26, air0, 2, 32, air0 + 3, 17);       // storage
		b.clear(30, air0, 23, 38, air0 + 1, 24);      // tube
		b.clear(33, 3, 30, 37, 10, 41);               // ramp hall
		// The ring corridor (3 high) and the main hold in the middle (4 high).
		for (int y = air0; y <= air0 + 2; y++) {
			b.ring(CX, y, CZ, RING_IN, RING_OUT, "air");
		}

		for (int y = air0; y <= air0 + 3; y++) {
			b.disc(CX, y, CZ, HOLD_R, "air");
		}

		doorways(b);
		// Turrets.
		b.disc(CX, 1, CZ, 2.5, "air");
		b.disc(CX, 2, CZ, 2.5, "air");
		b.disc(CX, 3, CZ, 2.5, "air");
		b.ringWall(CX, FLOOR + 6, FLOOR + 8, CZ, 2.5, 3.5, PANE);
		b.disc(CX, FLOOR + 9, CZ, 3.5, PANE);

		for (int y = FLOOR + 6; y <= FLOOR + 8; y++) {
			b.disc(CX, y, CZ, 2.5, "air");
		}

		b.disc(CX, FLOOR + 5, CZ, 2.0, "iron_block");
	}

	private static void doorways(InteriorBuilder b) {
		for (Doorway door : DOORWAYS) {
			b.clear(door.x0(), door.y0(), door.z0(), door.x1(), door.y1(), door.z1());
		}
	}

	/** The ring corridor: deck and ceiling (panels and lights are drawn by the client), light. */
	private static void corridor(InteriorBuilder b) {
		for (int x = 8; x <= 33; x++) {
			for (int z = 18; z <= 43; z++) {
				double d = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);

				if (d > RING_IN - 0.5 && d <= RING_OUT + 0.5) {
					b.set(x, FLOOR, z, DECK);
					b.set(x, FLOOR + 4, z, PANEL);
				}
			}
		}

		for (int i = 0; i < 12; i++) {
			double angle = i * Math.PI / 6.0;
			int x = (int) Math.floor(CX + Math.cos(angle) * 9.5);
			int z = (int) Math.floor(CZ + Math.sin(angle) * 9.5);

			if (b.isAir(x, FLOOR + 3, z)) {
				b.light(x, FLOOR + 3, z, 13);
			}
		}
	}

	/** The main hold: the round game table, the curved couch, the engineering station, the gun well. */
	private static void mainHold(InteriorBuilder b) {
		for (int x = 13; x <= 28; x++) {
			for (int z = 23; z <= 38; z++) {
				double d = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);

				if (d <= HOLD_R + 0.5) {
					b.set(x, FLOOR, z, DECK);
					b.set(x, FLOOR + 5, z, PANEL);
				}
			}
		}

		// The game table and the curved couch around it collide as drawn (Phase 17: see solids()).

		// The gun well: the ladder up to the upper turret and down to the lower one, on a post.
		for (int y = 1; y <= FLOOR + 6; y++) {
			b.set(20, y, 31, TRIM);
			b.set(20, y, 30, "ladder[facing=north]");
		}

		// The engineering station on the right: a bank of consoles (proxies) and its seat.
		b.box(26, FLOOR + 1, 29, 26, FLOOR + 1, 33, PROXY);
		b.box(27, FLOOR + 1, 28, 27, FLOOR + 3, 34, PROXY);
		b.shaped(25, FLOOR + 1, 31, Shapes.box(0.2, 0.0, 0.2, 0.8, 0.5, 0.8), false);
		// A locker and a barrel by the corridor door.
		b.set(24, FLOOR + 1, 25, PROXY);
		b.set(24, FLOOR + 2, 25, PROXY);
		b.set(23, FLOOR + 1, 24, "barrel[facing=south]");
		b.light(17, FLOOR + 3, 31, 12);
		b.light(23, FLOOR + 3, 29, 12);
		b.light(20, FLOOR + 4, 26, 11);
	}

	/** The two gun turrets: a seat (proxy) and the controls; the glass and the guns are drawn by the client. */
	private static void turrets(InteriorBuilder b) {
		int top = FLOOR + 6;
		b.shaped(20, top, 28, Shapes.box(0.2, 0.0, 0.2, 0.8, 0.5, 0.8), false);
		b.set(20, top, 27, PROXY);
		b.light(21, top + 2, 31, 10);
		b.shaped(20, 1, 28, Shapes.box(0.2, 0.0, 0.2, 0.8, 0.5, 0.8), false);
		b.set(20, 1, 27, PROXY);
		b.light(21, 2, 31, 10);
	}

	/**
	 * The cockpit (Phase 16: rebuilt): a round pod, narrow and long, the window cone at its front.
	 * Nothing of it is a visible block: the floor, ceiling and window are barriers, the curved
	 * walls barriers shaped like the drawn wall (blocks would stick out of the round pod outside),
	 * the client draws all of it. Four seats in two rows, the dashboard under the window, the helm
	 * lever on the pilot's side, the hyperdrive lever on the co-pilot's.
	 */
	private static void cockpit(InteriorBuilder b) {
		int y0 = FLOOR + 1;

		for (int x = 39; x <= 48; x++) {
			for (int z = COCKPIT_FRONT - 4; z <= COCKPIT_BACK; z++) {
				double half = cockpitHalfWidth(y0);
				boolean under = Math.abs(x + 0.5 - COCKPIT_X) < half + 0.5;

				for (int y = FLOOR; y <= COCKPIT_CEILING; y++) {
					if (doorAt(x, y, z)) {
						continue;
					}

					// Phase 17: the room goes on into the window cone (one walks up to the glass, not
					// into an invisible wall where the pod ends and the window begins).
					boolean room = y > FLOOR && y < COCKPIT_CEILING && z >= COCKPIT_FRONT - 4 && z < COCKPIT_BACK;

					if (!room) {
						// Floor, ceiling, window and back wall, as far as the pod reaches.
						if (Math.abs(x + 0.5 - COCKPIT_X) < COCKPIT_R + 0.2 && (under || y > FLOOR)) {
							b.set(x, y, z, PANE);
						}

						continue;
					}

					// Phase 17: the round wall leans, so each column collides from the floor up to
					// where the wall is at its distance from the axis (and down from the wall above
					// the axis): one walks up to the wall at every height, not into an invisible
					// step at the narrowest place of the row.
					int row = y;
					net.minecraft.world.phys.shapes.VoxelShape shape = ProxyShapes.spans(x, z, 16, (px, pz) -> noseSpan(px, pz, row));

					if (shape.isEmpty()) {
						b.set(x, y, z, "air");
					} else if (Block.isShapeFullBlock(shape)) {
						if (Math.abs(x + 0.5 - COCKPIT_X) < COCKPIT_R + 1.0) {
							b.set(x, y, z, PANE);
						}
					} else {
						b.shaped(x, y, z, shape, false);
					}
				}
			}
		}

		// The dashboard across the front (proxies), the helm lever on the pilot's side, the
		// hyperdrive lever on the co-pilot's.
		b.box(42, y0, COCKPIT_FRONT + 1, 45, y0, COCKPIT_FRONT + 1, PROXY);
		// Phase 17: the dashboard is drawn a little wider than its four cells.
		for (int side = 0; side < 2; side++) {
			int x = side == 0 ? 41 : 46;
			int z = COCKPIT_FRONT + 1;
			net.minecraft.world.phys.shapes.VoxelShape pod = ProxyShapes.spans(x, z, 16, (px, pz) -> noseSpan(px, pz, y0));
			b.shaped(x, y0, z, Shapes.or(pod, side == 0 ? Shapes.box(0.66, 0.0, 0.0, 1.0, 1.0, 0.95) : Shapes.box(0.0, 0.0, 0.0, 0.34, 1.0, 0.95)), false);
			// A cap over it, as over the dashboard (one does not climb onto it and on from there).
			net.minecraft.world.phys.shapes.VoxelShape above = ProxyShapes.spans(x, z, 16, (px, pz) -> noseSpan(px, pz, y0 + 1));
			b.shaped(x, y0 + 1, z, Shapes.or(above, side == 0 ? Shapes.box(0.66, 0.0, 0.0, 1.0, 0.5, 0.95) : Shapes.box(0.0, 0.0, 0.0, 0.34, 0.5, 0.95)), false);
		}

		b.set(42, y0, COCKPIT_FRONT + 1, DARK);
		b.set(42, y0 + 1, COCKPIT_FRONT + 1, "lever[face=floor,facing=south]");

		// The seats: pilot (left), co-pilot (right), two behind; slim, so one can walk between them.
		for (Seat seat : SEATS) {
			if (seat.cell().getX() >= 41) {
				double dx = seat.at().x - seat.cell().getX();
				double dz = seat.at().z - seat.cell().getZ();
				b.shaped(seat.cell().getX(), seat.cell().getY(), seat.cell().getZ(),
						Shapes.box(Math.max(0.0, dx - 0.32), 0.0, Math.max(0.0, dz - 0.32), Math.min(1.0, dx + 0.32), 0.58, Math.min(1.0, dz + 0.32)), false);
			}
		}

		b.light(44, COCKPIT_CEILING - 1, 23, 11);
		b.light(44, COCKPIT_CEILING - 1, 27, 9);
	}

	/** The crew quarters: bunks (proxies), lockers, a table. */
	private static void quarters(InteriorBuilder b) {
		b.box(4, FLOOR, 22, 8, FLOOR, 28, DECK);
		b.box(4, FLOOR + 4, 22, 8, FLOOR + 4, 28, PANEL);

		for (int z : new int[] {22, 25, 28}) {
			b.box(4, FLOOR + 1, z, 6, FLOOR + 1, z, PROXY);
		}

		b.set(4, FLOOR + 1, 23, "barrel[facing=east]");
		b.set(4, FLOOR + 1, 26, "barrel[facing=east]");
		// Phase 18: barrels everywhere one sees them (the client draws a cargo container round each).
		b.set(8, FLOOR + 1, 22, "barrel[facing=up]");
		// Phase 18: the table as drawn (its top at 0.8), a cap over it: nobody stands on it in the air.
		b.shaped(8, FLOOR + 1, 27, Shapes.box(0.05, 0.0, 0.05, 0.95, 0.8, 0.95), false);
		b.shaped(8, FLOOR + 2, 27, Shapes.box(0.05, 0.0, 0.05, 0.95, 1.0, 0.95), false);
		b.light(6, FLOOR + 3, 23, 12);
		b.light(7, FLOOR + 3, 27, 10);
	}

	/** The galley and the medical bay: a counter (proxies), storage, a sick bed. */
	private static void galley(InteriorBuilder b) {
		b.box(5, FLOOR, 33, 8, FLOOR, 39, DECK);
		b.box(5, FLOOR + 4, 33, 8, FLOOR + 4, 39, PANEL);
		b.box(5, FLOOR + 1, 33, 5, FLOOR + 1, 35, PROXY);
		b.set(5, FLOOR + 1, 36, "barrel[facing=east]");
		b.box(7, FLOOR + 1, 38, 8, FLOOR + 1, 38, PROXY);
		b.set(6, FLOOR + 1, 39, "barrel[facing=up]");
		b.light(7, FLOOR + 3, 35, 12);
		b.light(8, FLOOR + 3, 38, 10);
	}

	/** The engine room: the hyperdrive in the middle, machines along the outer wall (proxies). */
	private static void engineRoom(InteriorBuilder b) {
		sector(b, FLOOR, FLOOR, ENGINE_R0 - 0.5, ENGINE_R1 + 0.5, 1.0, DECK);
		sector(b, FLOOR + 5, FLOOR + 5, ENGINE_R0 - 0.5, ENGINE_R1 + 0.5, 1.0, TRIM);
		// The machines along the outer wall and the hyperdrive collide as drawn (Phase 17: see solids()).

		b.light(15, FLOOR + 4, 45, 13);
		b.light(26, FLOOR + 4, 45, 13);
		b.light((int) DRIVE_X, FLOOR + 4, (int) DRIVE_Z - 2, 12);
	}

	/** The forward pods: crates in the port cargo hold, hidden compartments under the starboard floor. */
	private static void cargo(InteriorBuilder b) {
		b.box(9, FLOOR, 2, 15, FLOOR, 17, DECK);
		b.box(26, FLOOR, 2, 32, FLOOR, 17, DECK);
		b.box(9, FLOOR + 5, 2, 15, FLOOR + 5, 17, PANEL);
		b.box(26, FLOOR + 5, 2, 32, FLOOR + 5, 17, PANEL);

		for (int z = 3; z <= 15; z += 3) {
			b.set(9, FLOOR + 1, z, "barrel[facing=east]");
			b.set(9, FLOOR + 2, z, z % 2 == 0 ? "barrel[facing=east]" : "air");
			b.set(15, FLOOR + 1, z, "barrel[facing=up]");
		}

		// Stacks of cargo crates (proxies).
		b.box(12, FLOOR + 1, 3, 13, FLOOR + 2, 4, PROXY);
		b.box(11, FLOOR + 1, 10, 12, FLOOR + 1, 11, PROXY);
		b.light(12, FLOOR + 4, 8, 12);
		b.light(12, FLOOR + 4, 14, 12);

		// The hidden compartments: closed hatches in the floor with chests below.
		for (int[] spot : new int[][] {{28, 6}, {30, 6}, {28, 12}, {30, 12}}) {
			b.set(spot[0], FLOOR - 1, spot[1], "chest[facing=south]");
			b.set(spot[0], FLOOR, spot[1], "spruce_trapdoor[half=top,open=false,facing=south]");
		}

		b.set(26, FLOOR + 1, 15, "barrel[facing=east]");
		b.set(32, FLOOR + 1, 3, "barrel[facing=west]");
		b.set(32, FLOOR + 1, 4, "barrel[facing=west]");
		b.box(31, FLOOR + 1, 9, 32, FLOOR + 1, 10, PROXY);
		b.light(29, FLOOR + 4, 9, 11);
	}

	/** Phase 17: where the ramp starts at the top (z), and its height (the walking surface) along z. */
	public static final int RAMP_TOP_Z = 34;

	public static double rampHeight(double z) {
		return FLOOR + 1.0 - Math.max(0.0, Math.min(4.0, z - RAMP_TOP_Z));
	}

	/** The boarding ramp: a landing, the ramp down, the pad at the open hatch (the way out). */
	private static void ramp(InteriorBuilder b) {
		b.box(33, FLOOR, 30, 37, FLOOR, 33, DECK);

		// Phase 17: a smooth ramp, collision exactly under the plated ramp that is drawn (it was
		// stairs under a ramp drawn above them: one walked with the feet in the plates).
		for (int z = RAMP_TOP_Z; z < RAMP_TOP_Z + 4; z++) {
			for (int y = 1; y <= FLOOR; y++) {
				double low = rampHeight(z + 1.0);

				if (y + 1 <= low) {
					b.box(33, y, z, 37, y, z, TRIM);
				} else if (y < rampHeight(z)) {
					net.minecraft.world.phys.shapes.VoxelShape shape = net.minecraft.world.phys.shapes.Shapes.empty();
					int res = 16;

					for (int j = 0; j < res; j++) {
						double top = Math.min(1.0, rampHeight(z + (j + 1.0) / res) - y);

						if (top > 0.0) {
							shape = net.minecraft.world.phys.shapes.Shapes.or(shape,
									net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, (double) j / res, 1.0, top, (j + 1.0) / res));
						}
					}

					for (int x = 33; x <= 37; x++) {
						b.shaped(x, y, z, shape.optimize(), false);
					}
				} else {
					b.box(33, y, z, 37, y, z, "air");
				}
			}
		}

		b.box(33, 1, 38, 37, 2, 41, TRIM);
		// The pad: hazard stripes and light from the open hatch.
		for (int x = 33; x <= 37; x++) {
			for (int z = 39; z <= 41; z++) {
				b.set(x, 2, z, "yellow_concrete");
			}
		}

		b.box(34, 3, 42, 36, 5, 42, PANE);
		b.box(33, 3, 42, 33, 5, 42, TRIM);
		b.box(37, 3, 42, 37, 5, 42, TRIM);
		b.light(35, 4, 40, 15);
		b.light(35, FLOOR + 3, 31, 13);
	}
}
