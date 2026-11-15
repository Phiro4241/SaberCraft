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
	/** The flight control room on the west side. */
	public static final int CONTROL_X0 = 5;
	/** The corridor and the lift lobby behind the hangar. */
	public static final int LOBBY_Z0 = -2;
	public static final int LOBBY_Z1 = 6;
	/** The reactor room: a round shaft. */
	public static final double REACTOR_X = 40.5;
	public static final double REACTOR_Z = 24.5;
	public static final double REACTOR_R = 14.0;
	public static final int REACTOR_BOTTOM = 14;
	public static final int REACTOR_TOP = 44;
	/** Phase 16: where the reactor shaft's round wall is drawn (and collides). */
	public static final double REACTOR_WALL = REACTOR_R - 0.8;

	private static final String WALL = "gray_concrete";
	private static final String DARK = "polished_deepslate";
	private static final String BLACK = "polished_blackstone";
	private static final String FLOOR = "polished_blackstone_bricks";
	private static final String PANE = "barrier";
	private static final String PROXY = "barrier";

	/** Phase 16: a chair collides only with its seat (people walk past its back and arms). */
	static final net.minecraft.world.phys.shapes.VoxelShape CHAIR = net.minecraft.world.phys.shapes.Shapes.box(0.22, 0.0, 0.22, 0.78, 0.5, 0.78);

	// --- Phase 17: the crew ------------------------------------------------------------------------

	/**
	 * Phase 17: someone of the ship's crew, drawn by the client (no entity: nothing for the server
	 * to tick, the same for every player). {@code kind}: {@link #TROOPER}, {@link #OFFICER},
	 * {@link #TECH}; {@code pose}: {@link #STAND} (on guard), {@link #WORK} (at a console),
	 * {@link #SIT}, {@link #TALK}, {@link #PATROL} (walks round {@code path}, a closed loop, at
	 * {@code speed} blocks a second, starting {@code phase} blocks along it). (x, y, z) are the
	 * feet (plot space), {@code yaw} Minecraft's (0 = facing south, 90 west, 180 north).
	 */
	public record Crew(String kind, String pose, double x, double y, double z, float yaw, List<Vec3> path, double speed, double phase) {
		static Crew at(String kind, String pose, double x, double y, double z, float yaw) {
			return new Crew(kind, pose, x, y, z, yaw, List.of(), 0.0, 0.0);
		}

		static Crew patrol(String kind, double speed, double phase, Vec3... path) {
			return new Crew(kind, PATROL, path[0].x, path[0].y, path[0].z, 0.0F, List.of(path), speed, phase);
		}

		/** Whether this one stays in its place (and collides there). */
		public boolean still() {
			return this.path.isEmpty() && !SIT.equals(this.pose);
		}
	}

	public static final String TROOPER = "trooper";
	public static final String OFFICER = "officer";
	public static final String TECH = "tech";
	public static final String STAND = "stand";
	public static final String WORK = "work";
	public static final String SIT = "sit";
	public static final String TALK = "talk";
	public static final String PATROL = "patrol";

	/** Phase 17: the chairs the crew sits on (not free for players). */
	static final List<BlockPos> CREW_CHAIRS = List.of(new BlockPos(24, PIT + 1, 87), new BlockPos(28, PIT + 1, 75), new BlockPos(52, PIT + 1, 81),
			new BlockPos(58, PIT + 1, 93), new BlockPos(56, PIT + 1, 75), new BlockPos(HANGAR_X0 - 2, HANGAR + 1, -56), new BlockPos(HANGAR_X0 - 2, HANGAR + 1, -50));

	public static final List<Crew> CREW = buildCrew();

	private static List<Crew> buildCrew() {
		List<Crew> list = new java.util.ArrayList<>();
		int deck = DECK + 1;
		int hangar = HANGAR + 1;

		// The bridge: the officer of the watch at the window, a pair talking on the platform, one
		// walking the walkway, guards at the door, the crew at the consoles in the pits.
		list.add(Crew.at(OFFICER, STAND, CX - 5.5, deck, WINDOW_Z + 1.7, 180.0F));
		list.add(Crew.at(OFFICER, TALK, CX + 7.2, deck, WINDOW_Z + 3.4, 100.0F));
		list.add(Crew.at(OFFICER, TALK, CX + 5.9, deck, WINDOW_Z + 3.6, 280.0F));
		list.add(Crew.patrol(OFFICER, 0.9, 0.0, new Vec3(CX + 0.5, deck, PLATFORM_Z1 + 2.5), new Vec3(CX + 0.5, deck, BRIDGE_BACK - 3.5)));
		list.add(Crew.at(TROOPER, STAND, CX - 2.5, deck, BRIDGE_BACK + 1.6, 0.0F));
		list.add(Crew.at(TROOPER, STAND, CX + 3.5, deck, BRIDGE_BACK + 1.6, 0.0F));

		for (BlockPos chair : CREW_CHAIRS) {
			if (chair.getY() == PIT + 1) {
				list.add(Crew.at(TECH, SIT, chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5, 180.0F));
			}
		}

		// Standing at the consoles of the pits, between the chairs.
		list.add(Crew.at(TECH, WORK, 31.5, PIT + 1, 81.35, 180.0F));
		list.add(Crew.at(OFFICER, WORK, 49.5, PIT + 1, 87.35, 180.0F));

		// The command deck: a patrol along the corridor, the sensor room's crew, two in conference.
		list.add(Crew.patrol(TROOPER, 1.1, 0.0, new Vec3(CX - 1.5, deck, BRIDGE_BACK + 2.5), new Vec3(CX - 1.5, deck, COMMAND_BACK - 4.5)));
		int sx = (CX + 4 + SIDE_EAST) / 2 + 4;
		list.add(Crew.at(TECH, WORK, sx + 0.5, deck, 104.3, 0.0F));
		list.add(Crew.at(OFFICER, WORK, sx - 3.3, deck, 107.5, 270.0F));
		list.add(Crew.at(TECH, WORK, CX + 6.35, deck, SIDE_Z0 + 3.5, 90.0F));
		int tx = (SIDE_WEST + CX - 4) / 2;
		list.add(Crew.at(OFFICER, SIT, tx - 2.5, deck, 105.5, 0.0F));
		list.add(Crew.at(OFFICER, SIT, tx + 3.5, deck, 109.5, 180.0F));

		// The hangar: guards at the door, a squad of two walking round the force field, a
		// technician at the crates, an officer by the landing circle.
		list.add(Crew.at(TROOPER, STAND, CX - 3.5, hangar, HANGAR_Z1 - 1.4, 180.0F));
		list.add(Crew.at(TROOPER, STAND, CX + 4.5, hangar, HANGAR_Z1 - 1.4, 180.0F));
		Vec3[] loop = {new Vec3(27.5, hangar, -59.5), new Vec3(53.5, hangar, -59.5), new Vec3(53.5, hangar, -21.5), new Vec3(27.5, hangar, -21.5)};
		list.add(Crew.patrol(TROOPER, 1.2, 0.0, loop));
		list.add(Crew.patrol(TROOPER, 1.2, 1.4, loop));
		list.add(Crew.at(TECH, WORK, HANGAR_X1 - 4.4, hangar, -30.5, 270.0F));
		list.add(Crew.at(OFFICER, STAND, CX + 4.0, hangar, -27.0, 160.0F));

		// Flight control: two at the consoles, one standing behind them.
		for (BlockPos chair : CREW_CHAIRS) {
			if (chair.getY() == HANGAR + 1) {
				list.add(Crew.at(TECH, SIT, chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5, 270.0F));
			}
		}

		list.add(Crew.at(OFFICER, STAND, CONTROL_X0 + 6.5, hangar, -44.5, 270.0F));

		// The corridor to the lift: a patrol; a guard at the reactor gallery; a technician on the catwalk.
		list.add(Crew.patrol(TROOPER, 1.0, 0.0, new Vec3(CX - 1.5, hangar, HANGAR_Z1 + 2.5), new Vec3(CX - 1.5, hangar, LOBBY_Z1 - 4.5)));
		list.add(Crew.at(TROOPER, STAND, CX + 2.6, hangar, LOBBY_Z0 + 1.5, 90.0F));
		list.add(Crew.at(TECH, WORK, REACTOR_X - 0.5, hangar, REACTOR_Z - 9.3, 0.0F));
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
			Door.wall(true, CX + 4, LOBBY_Z0, LOBBY_Z0 + 2, HANGAR + 1, HANGAR + 3));

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

		return list;
	}

	@Override
	public String id() {
		return "star_destroyer";
	}

	@Override
	public int version() {
		// 3 (Phase 16): the reactor shaft's round wall is shaped barriers where the drawn wall runs.
		// 5 (Phase 17): maintenance ways, the crew's places.
		return 5;
	}

	@Override
	public BlockPos size() {
		return new BlockPos(80, 176, 120);
	}

	@Override
	public List<BoundingBox> volumes() {
		return List.of(new BoundingBox(1, 10, -70, 79, 46, 42), new BoundingBox(1, 143, 62, 79, 165, 120));
	}

	@Override
	public List<BoundingBox> clearVolumes() {
		// The Phase 14 rooms stood from the plot corner up to (45, 44, 50).
		return List.of(new BoundingBox(0, 0, 0, 46, 45, 51), new BoundingBox(1, 10, -70, 79, 46, 42), new BoundingBox(1, 143, 62, 79, 165, 120));
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
				new Lift(new AABB(CX - 2, HANGAR + 1, LOBBY_Z1 - 2, CX + 3, HANGAR + 4, LOBBY_Z1 + 1), new Vec3(CX + 0.5, DECK + 1, COMMAND_BACK - 3.5), 180.0F),
				new Lift(new AABB(CX - 2, DECK + 1, COMMAND_BACK - 2, CX + 3, DECK + 4, COMMAND_BACK), new Vec3(CX + 0.5, HANGAR + 1, LOBBY_Z1 - 4.5), 0.0F));
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
				new Room("lift", new AABB(CX - 2, HANGAR + 1, LOBBY_Z1 - 2, CX + 3, HANGAR + 4, LOBBY_Z1 + 1)),
				new Room("lift", new AABB(CX - 2, DECK + 1, COMMAND_BACK - 2, CX + 3, DECK + 4, COMMAND_BACK)),
				new Room("flight_control", new AABB(CONTROL_X0 + 1, HANGAR + 1, -58, HANGAR_X0, HANGAR + 6, -36)),
				new Room("hangar", new AABB(HANGAR_X0 + 1, HANGAR + 1, HANGAR_Z0 + 1, HANGAR_X1, HANGAR_TOP, HANGAR_Z1)),
				new Room("reactor", new AABB(REACTOR_X - REACTOR_R, REACTOR_BOTTOM, REACTOR_Z - REACTOR_R, REACTOR_X + REACTOR_R, REACTOR_TOP,
						REACTOR_Z + REACTOR_R)),
				new Room("hangar_corridor", new AABB(CX - 3, HANGAR + 1, HANGAR_Z1, CX + 4, HANGAR + 5, LOBBY_Z1 + 1)),
				new Room("conference", new AABB(SIDE_WEST + 1, DECK + 1, SIDE_Z0 + 1, CX - 4, COMMAND_TOP, SIDE_Z1)),
				new Room("sensor_room", new AABB(CX + 5, DECK + 1, SIDE_Z0 + 1, SIDE_EAST, COMMAND_TOP, SIDE_Z1)),
				new Room("command_corridor", new AABB(CX - 3, DECK + 1, BRIDGE_BACK, CX + 4, COMMAND_TOP, COMMAND_BACK)),
				new Room("vent", VENTS.get(0)),
				new Room("vent", VENTS.get(1)),
				new Room("vent", VENTS.get(2)),
				new Room("bridge", new AABB(BRIDGE_X0 + 1, PIT + 1, WINDOW_Z + 1, BRIDGE_X1, BRIDGE_TOP, BRIDGE_BACK)));
	}

	@Override
	public void build(InteriorBuilder b) {
		hangar(b);
		flightControl(b);
		corridor(b);
		reactor(b);
		bridge(b);
		commandDeck(b);
		vents(b);
		furnish(b);
		crew(b);
	}

	/**
	 * Phase 17: the corridor to the lift was bare: a rack of rifles on its west wall, a security
	 * desk on its east wall (proxies, drawn by the client).
	 */
	private static void furnish(InteriorBuilder b) {
		b.box(CX - 3, HANGAR + 1, -7, CX - 3, HANGAR + 2, -6, PROXY);
		b.box(CX + 3, HANGAR + 1, -10, CX + 3, HANGAR + 1, -9, PROXY);
	}

	/** Phase 17: the maintenance ways: two blocks high, one wide, grating all round, a dim lamp now and then. */
	public static final List<AABB> VENTS = List.of(
			// From the hangar corridor west, then north into the flight control room.
			new AABB(10, HANGAR + 1, -10, 37, HANGAR + 3, -9),
			new AABB(10, HANGAR + 1, -35, 11, HANGAR + 3, -9),
			// Under the bridge's walkway, from one crew pit to the other.
			new AABB(WALK_X0, PIT + 1, 84, WALK_X1 + 1, PIT + 3, 85));

	private static void vents(InteriorBuilder b) {
		// The hangar deck's way: a shell round it first, then hollowed out.
		b.box(9, HANGAR, -11, CX - 4, HANGAR + 3, -9, DARK);
		b.box(9, HANGAR, -35, 11, HANGAR + 3, -9, DARK);

		for (AABB vent : VENTS) {
			b.clear((int) vent.minX, (int) vent.minY, (int) vent.minZ, (int) vent.maxX - 1, (int) vent.maxY - 1, (int) vent.maxZ - 1);
		}

		b.light(22, HANGAR + 2, -10, 7);
		b.light(10, HANGAR + 2, -22, 7);
		b.light(CX, PIT + 2, 84, 6);
	}

	/** Phase 17: those of the crew who stay in their place collide there (a slim column, two rows). */
	private static void crew(InteriorBuilder b) {
		for (Crew crew : CREW) {
			if (!crew.still()) {
				continue;
			}

			int x = (int) Math.floor(crew.x());
			int y = (int) Math.floor(crew.y());
			int z = (int) Math.floor(crew.z());
			double lx = crew.x() - x;
			double lz = crew.z() - z;
			double r = 0.26;

			for (int row = 0; row < 2; row++) {
				if (b.get(x, y + row, z).isAir()) {
					b.shaped(x, y + row, z, net.minecraft.world.phys.shapes.Shapes.box(Math.max(0.0, lx - r), 0.0, Math.max(0.0, lz - r), Math.min(1.0, lx + r),
							row == 0 ? 1.0 : 0.8, Math.min(1.0, lz + r)), false);
				}
			}
		}
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
		// The shaft below the field, down through the hull.
		b.box(FIELD_X0 - 1, HANGAR - 6, FIELD_Z0 - 1, FIELD_X1 + 1, HANGAR - 1, FIELD_Z1 + 1, DARK);
		b.clear(FIELD_X0, HANGAR - 6, FIELD_Z0, FIELD_X1, HANGAR - 1, FIELD_Z1);

		// The landing circle where the tractor beam sets people down.
		b.ring(CX + 0.5, HANGAR, -28.5, 1.6, 2.6, "white_concrete");
		b.disc(CX + 0.5, HANGAR, -28.5, 1.6, "light_gray_concrete");

		// TIE fighter racks along both sides: a rail under the ceiling, the TIEs hang from it
		// (drawn by the client); each rack has a gantry walkway below.
		for (int z = HANGAR_Z0 + 8; z <= HANGAR_Z1 - 8; z += 12) {
			for (int x : new int[] {HANGAR_X0 + 7, HANGAR_X1 - 7}) {
				for (int y = HANGAR_TOP - 3; y <= HANGAR_TOP; y++) {
					b.set(x, y, z, "iron_chain[axis=y]");
				}
			}
		}

		// Crates, stacked against the walls (the lower ones are real storage).
		for (int z = HANGAR_Z0 + 3; z <= HANGAR_Z0 + 9; z += 3) {
			b.set(HANGAR_X1 - 2, HANGAR + 1, z, "barrel[facing=west]");
			b.set(HANGAR_X1 - 2, HANGAR + 1, z + 1, "chest[facing=west]");
			b.set(HANGAR_X1 - 3, HANGAR + 1, z, "barrel[facing=west]");
			b.set(HANGAR_X1 - 2, HANGAR + 2, z, "iron_block");
		}

		for (int x = HANGAR_X0 + 3; x <= HANGAR_X0 + 9; x += 3) {
			b.set(x, HANGAR + 1, HANGAR_Z0 + 2, "barrel[facing=south]");
			b.set(x + 1, HANGAR + 1, HANGAR_Z0 + 2, "chest[facing=south]");
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
		// The lift at the end of the lobby (a lit floor).
		b.box(CX - 2, HANGAR, LOBBY_Z1 - 2, CX + 2, HANGAR, LOBBY_Z1, "white_concrete");

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

	/** The gallery from the side room to the reactor's catwalk. */
	public static final int GALLERY_X0 = CX + 6;
	public static final int GALLERY_X1 = CX + 10;
	public static final int GALLERY_Z0 = LOBBY_Z0 + 3;
	public static final int GALLERY_Z1 = 11;

	/** The reactor: a round shaft with the core in the middle and a catwalk ring around it. */
	/** Phase 16: the gallery's way through the shaft wall. */
	public static final Doorway GALLERY = new Doorway(GALLERY_X0 + 1, HANGAR + 1, GALLERY_Z1, GALLERY_X1 - 1, HANGAR + 3, GALLERY_Z1 + 3, false);

	/** Phase 16: whether a point (plot space, row y) is in the reactor shaft, up to its drawn wall. */
	public static boolean openReactor(double x, double z, int y) {
		double dx = x - REACTOR_X;
		double dz = z - REACTOR_Z;
		return y >= REACTOR_BOTTOM && y < REACTOR_TOP && dx * dx + dz * dz < REACTOR_WALL * REACTOR_WALL;
	}

	@Override
	public List<Doorway> doorways() {
		return List.of(GALLERY);
	}

	@Override
	public boolean openRound(double x, double z, int y) {
		return openReactor(x, z, y);
	}

	private static void reactor(InteriorBuilder b) {
		double r = REACTOR_R;
		b.ringWall(REACTOR_X, REACTOR_BOTTOM - 1, REACTOR_TOP, REACTOR_Z, r - 0.5, r + 1.0, DARK);
		b.disc(REACTOR_X, REACTOR_BOTTOM - 1, REACTOR_Z, r, DARK);
		b.disc(REACTOR_X, REACTOR_TOP, REACTOR_Z, r, DARK);

		for (int y = REACTOR_BOTTOM; y < REACTOR_TOP; y++) {
			b.disc(REACTOR_X, y, REACTOR_Z, r - 0.5, "air");
		}

		// The catwalk ring at deck height.
		b.ring(REACTOR_X, HANGAR, REACTOR_Z, 8.5, r - 0.5, FLOOR);
		// The gallery from the lobby's side room, through the shaft wall onto the catwalk.
		b.room(GALLERY_X0, HANGAR, GALLERY_Z0, GALLERY_X1, HANGAR + 4, GALLERY_Z1, DARK);
		b.box(GALLERY_X0 + 1, HANGAR, GALLERY_Z0, GALLERY_X1 - 1, HANGAR, GALLERY_Z1 + 3, FLOOR);
		b.clear(GALLERY_X0 + 1, HANGAR + 1, GALLERY_Z0, GALLERY_X1 - 1, HANGAR + 3, GALLERY_Z1 + 3);
		b.light(CX + 8, HANGAR + 3, 6, 11);
		// Railings (invisible here, drawn by the client) keep people out of the shaft, and off the
		// uneven outer edge of the catwalk where the round wall is.
		b.ring(REACTOR_X, HANGAR + 1, REACTOR_Z, 8.0, 8.9, PROXY);
		b.ring(REACTOR_X, HANGAR + 2, REACTOR_Z, 8.0, 8.9, PROXY);

		b.clear(GALLERY_X0 + 1, HANGAR + 1, GALLERY_Z0, GALLERY_X1 - 1, HANGAR + 3, GALLERY_Z1 + 3);
		// Phase 16: the wall collides where it is drawn (no more barriers a step in front of it).
		b.roundShell(REACTOR_X, REACTOR_Z, new double[] {REACTOR_WALL}, (int) (REACTOR_X - r - 2), REACTOR_BOTTOM, (int) (REACTOR_Z - r - 2),
				(int) (REACTOR_X + r + 2), REACTOR_TOP - 1, (int) (REACTOR_Z + r + 2), java.util.Set.of(InteriorBuilder.state(DARK).getBlock()),
				DestroyerInterior::openReactor, (x, z, y) -> y == HANGAR || GALLERY.contains((int) Math.floor(x), y, (int) Math.floor(z)));
		// The core gives off light.
		b.light((int) REACTOR_X, REACTOR_BOTTOM + 4, (int) REACTOR_Z, 15);
		b.light((int) REACTOR_X, HANGAR + 3, (int) (REACTOR_Z - 11), 13);
		b.light((int) REACTOR_X, HANGAR + 3, (int) (REACTOR_Z + 11), 13);
		b.light((int) (REACTOR_X - 11), HANGAR + 3, (int) REACTOR_Z, 13);
		b.light((int) (REACTOR_X + 11), HANGAR + 3, (int) REACTOR_Z, 13);
		b.light((int) REACTOR_X, REACTOR_TOP - 3, (int) REACTOR_Z, 14);
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

		// Railings along the walkway and the platform edge (invisible blocks, drawn by the client).
		for (int z = PLATFORM_Z1 + 1; z <= BRIDGE_BACK - 1; z++) {
			if (!isStep(z)) {
				b.set(WALK_X0, DECK + 1, z, PROXY);
				b.set(WALK_X1, DECK + 1, z, PROXY);
			}
		}

		for (int x = BRIDGE_X0 + 1; x <= BRIDGE_X1 - 1; x++) {
			if (x < WALK_X0 || x > WALK_X1) {
				b.set(x, DECK + 1, PLATFORM_Z1, PROXY);
			}
		}

		// Steps down into the pits.
		for (int z : new int[] {77, 89}) {
			for (int i = 0; i < 4; i++) {
				b.set(WALK_X0 - 1 - i, DECK - i, z, "polished_blackstone_stairs[facing=east]");
				b.set(WALK_X0 - 1 - i, DECK - i, z + 1, "polished_blackstone_stairs[facing=east]");
				b.set(WALK_X1 + 1 + i, DECK - i, z, "polished_blackstone_stairs[facing=west]");
				b.set(WALK_X1 + 1 + i, DECK - i, z + 1, "polished_blackstone_stairs[facing=west]");
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
				b.shaped(x, PIT + 2, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0), false);

				// A seat behind every other console.
				if ((x & 1) == 0) {
					b.shaped(x, PIT + 1, z + 1, CHAIR, false);
				}
			}
		}

		// The helm at the front of the walkway: the lever on a console.
		b.box(CX - 2, DECK + 1, WINDOW_Z + 2, CX + 2, DECK + 1, WINDOW_Z + 2, PROXY);
		b.set(CX, DECK + 1, WINDOW_Z + 2, BLACK);
		b.set(CX, DECK + 2, WINDOW_Z + 2, "lever[face=floor,facing=south]");
		// The officers' stations at the ends of the platform.
		for (int x : new int[] {BRIDGE_X0 + 4, BRIDGE_X1 - 4}) {
			b.set(x, DECK + 1, WINDOW_Z + 2, PROXY);
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

		// The conference table (proxies) with the hologram projector in its middle.
		int tx = (SIDE_WEST + CX - 4) / 2;
		b.box(tx - 6, DECK + 1, 106, tx + 6, DECK + 1, 108, PROXY);

		for (int x = tx - 5; x <= tx + 5; x += 2) {
			b.shaped(x, DECK + 1, 105, CHAIR, false);
			b.shaped(x, DECK + 1, 109, CHAIR, false);
		}
		// The sensor room's consoles along its inner wall and the tactical table.
		for (int z = SIDE_Z0 + 2; z <= SIDE_Z1 - 2; z += 2) {
			b.set(CX + 5, DECK + 1, z, z >= 105 && z <= 109 ? "air" : PROXY);
		}

		int sx = (CX + 4 + SIDE_EAST) / 2 + 4;
		b.box(sx - 2, DECK + 1, 105, sx + 2, DECK + 1, 109, PROXY);
		b.set(SIDE_EAST - 2, DECK + 1, SIDE_Z0 + 2, "chest[facing=west]");

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
