package com.phiro.sabercraft.interior;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
		// poking through them any more.
		return 3;
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
		return new BlockPos(41, FLOOR + 2, 19);
	}

	@Override
	public String ambient() {
		return "ship.interior_falcon";
	}

	@Override
	public List<Room> rooms() {
		return List.of(
				new Room("cockpit", new AABB(39, 7, 19, 47, 11, 26)),
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
			new Doorway(29, FLOOR + 1, 23, 31, FLOOR + 2, 24, true));  // cockpit tube

	/** The smooth walls as drawn: the hold's, both of the ring corridor's, the engine room's inner one. */
	public static final double HOLD_WALL = HOLD_R + 0.05;
	public static final double RING_IN_WALL = RING_IN + 0.05;
	public static final double RING_OUT_WALL = RING_OUT - 0.05;
	public static final double ENGINE_WALL = ENGINE_R0 + 0.05;

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

		if (y >= air0 && y <= air0 + 3 && d > ENGINE_WALL && d < ENGINE_R1) {
			// The ends of the engine room are walls of whole blocks: by the cell's middle.
			double angle = Math.toDegrees(Math.atan2(Math.floor(z) + 0.5 - CZ, Math.floor(x) + 0.5 - CX));
			return angle >= ENGINE_A0 && angle <= ENGINE_A1;
		}

		return false;
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
		b.roundShell(CX, CZ, new double[] {HOLD_WALL, RING_IN_WALL, RING_OUT_WALL, ENGINE_WALL}, 0, air0, 0, 47, air0 + 3, 51, shell, FalconInterior::open,
				(x, z, y) -> doorAt((int) Math.floor(x), y, (int) Math.floor(z)));
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
		b.box(29, 6, 22, 39, 9, 25, HULL);      // cockpit tube
		b.box(38, 5, 18, 47, 11, 26, HULL);     // cockpit
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
		sector(b, air0, air0 + 3, ENGINE_R0, ENGINE_R1, 0.0, "air"); // engine room
		b.clear(9, air0, 2, 15, air0 + 3, 17);        // cargo hold
		b.clear(26, air0, 2, 32, air0 + 3, 17);       // storage
		b.clear(30, air0, 23, 38, air0 + 1, 24);      // tube
		b.clear(39, air0, 19, 46, air0 + 3, 25);      // cockpit
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

		// The game table and the curved couch around it (proxies for the client's models).
		b.disc(TABLE_X, FLOOR + 1, TABLE_Z, TABLE_R, PROXY);

		for (int x = 12; x <= 21; x++) {
			for (int z = 22; z <= 33; z++) {
				double dx = x + 0.5 - TABLE_X;
				double dz = z + 0.5 - TABLE_Z;
				double d = Math.hypot(dx, dz);

				if (d > 2.4 && d <= 3.3 && dx < 1.2 && Math.hypot(x + 0.5 - CX, z + 0.5 - CZ) < HOLD_R - MARGIN) {
					b.set(x, FLOOR + 1, z, PROXY);
				}
			}
		}

		// The gun well: the ladder up to the upper turret and down to the lower one, on a post.
		for (int y = 1; y <= FLOOR + 6; y++) {
			b.set(20, y, 31, TRIM);
			b.set(20, y, 30, "ladder[facing=north]");
		}

		// The engineering station on the right: a bank of consoles (proxies) and its seat.
		b.box(26, FLOOR + 1, 29, 26, FLOOR + 1, 33, PROXY);
		b.box(27, FLOOR + 1, 28, 27, FLOOR + 3, 34, PROXY);
		b.set(25, FLOOR + 1, 31, PROXY);
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
		b.set(20, top, 28, PROXY);
		b.set(20, top, 27, PROXY);
		b.light(21, top + 2, 31, 10);
		b.set(20, 1, 28, PROXY);
		b.set(20, 1, 27, PROXY);
		b.light(21, 2, 31, 10);
	}

	/** The cockpit: four seats, the consoles, the window (drawn by the client), the helm lever. */
	private static void cockpit(InteriorBuilder b) {
		int y0 = FLOOR + 1;

		for (int x = 30; x <= 38; x++) {
			b.set(x, FLOOR, 23, DECK);
			b.set(x, FLOOR, 24, DECK);
			b.set(x, FLOOR + 3, 23, PANEL);
			b.set(x, FLOOR + 3, 24, PANEL);
		}

		for (int x = 31; x <= 38; x += 3) {
			b.light(x, FLOOR + 2, 23, 11);
		}

		b.box(39, FLOOR, 19, 46, FLOOR, 25, DECK);
		b.box(39, FLOOR + 5, 19, 46, FLOOR + 5, 25, PANEL);
		// The window: the front (the whole front row, the client draws the round window beyond),
		// the front of the roof, and the right side.
		b.box(38, FLOOR - 1, 18, 47, FLOOR + 5, 18, PANE);
		b.box(39, FLOOR + 5, 19, 46, FLOOR + 5, 20, PANE);
		b.box(47, y0 + 1, 19, 47, y0 + 3, 22, PANE);

		// The consoles in front of the seats (proxies), the helm lever on the pilot's.
		b.box(39, y0, 19, 46, y0, 19, PROXY);
		b.set(41, y0, 19, DARK);
		b.set(41, y0 + 1, 19, "lever[face=floor,facing=south]");
		b.set(39, y0, 20, PROXY);
		b.set(46, y0, 20, PROXY);

		// Seats: pilot (left), co-pilot (right), two behind.
		for (int[] seat : new int[][] {{41, 21}, {44, 21}, {41, 24}, {44, 24}}) {
			b.set(seat[0], y0, seat[1], PROXY);
		}

		b.light(42, FLOOR + 4, 23, 11);
		b.light(45, FLOOR + 4, 25, 9);
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
		b.set(8, FLOOR + 1, 22, "chest[facing=west]");
		b.set(8, FLOOR + 1, 27, PROXY);
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
		b.set(6, FLOOR + 1, 39, "chest[facing=north]");
		b.light(7, FLOOR + 3, 35, 12);
		b.light(8, FLOOR + 3, 38, 10);
	}

	/** The engine room: the hyperdrive in the middle, machines along the outer wall (proxies). */
	private static void engineRoom(InteriorBuilder b) {
		sector(b, FLOOR, FLOOR, ENGINE_R0 - 0.5, ENGINE_R1 + 0.5, 1.0, DECK);
		sector(b, FLOOR + 5, FLOOR + 5, ENGINE_R0 - 0.5, ENGINE_R1 + 0.5, 1.0, TRIM);
		// Machines along the outer wall, two high, gaps for the hatches to the engines.
		sector(b, FLOOR + 1, FLOOR + 2, ENGINE_R1 - 1.2, ENGINE_R1, 0.0, PROXY);
		// The hyperdrive.
		for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
			b.disc(DRIVE_X, y, DRIVE_Z, DRIVE_R, PROXY);
		}

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
			b.set(15, FLOOR + 1, z, "chest[facing=west]");
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

	/** The boarding ramp: a landing, the ramp down, the pad at the open hatch (the way out). */
	private static void ramp(InteriorBuilder b) {
		b.box(33, FLOOR, 30, 37, FLOOR, 33, DECK);

		for (int i = 0; i < 4; i++) {
			int z = 34 + i;
			b.box(33, 1, z, 37, FLOOR - 1 - i, z, TRIM);
			b.box(33, FLOOR - i, z, 37, FLOOR - i, z, "polished_andesite_stairs[facing=north]");
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
