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

	/** Phase 18: the shaft floor: the core's base, the converters and consoles collide as drawn. */
	private static void reactorFloor(InteriorBuilder b) {
		int x0 = (int) (REACTOR_X - REACTOR_R);
		int x1 = (int) (REACTOR_X + REACTOR_R);
		int z0 = (int) (REACTOR_Z - REACTOR_R);
		int z1 = (int) (REACTOR_Z + REACTOR_R);

		for (int y = REACTOR_BOTTOM; y < HANGAR; y++) {
			b.solid(x0, z0, x1, z1, y, DestroyerInterior::converterAt, 1.0, java.util.Set.of());
			// The core's base collides as drawn, a cone (it stood in steps of a block before: one
			// climbed them and stood in the air beside the drawn cone).
			int row = y - REACTOR_BOTTOM;
			b.spans(x0, z0, x1, z1, y, (x, z) -> {
				double d = Math.hypot(x - REACTOR_X, z - REACTOR_Z);

				if (d < CORE_TOP_R) {
					return new double[] {0.0, 1.0};
				}

				double top = (CORE_BASE_R - d) / (CORE_BASE_R - CORE_TOP_R) * CORE_CONE - row;
				return top <= 0.0 ? null : new double[] {0.0, Math.min(1.0, top)};
			}, java.util.Set.of());
		}

		for (BlockPos console : REACTOR_CONSOLES) {
			b.shaped(console.getX(), console.getY(), console.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0), false);
			// Nobody climbs onto a console (one stood in its screen).
			b.ghost(console.getX(), console.getY() + 1, console.getZ(), net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
		}

		// A railing round the core's foot: the core is no stair (people climbed its cone into the
		// pipes and the glowing column). A rail a block high, a cap half a block over it (collision
		// only) so nobody jumps over.
		ProxyShapes.Span rail = (x, z) -> Math.abs(Math.hypot(x - REACTOR_X, z - REACTOR_Z) - CORE_RAIL_R) <= CORE_RAIL_HALF ? new double[] {0.0, 1.0} : null;
		ProxyShapes.Span railCap = (x, z) -> rail.at(x, z) == null ? null : new double[] {0.0, 0.5};
		int r0 = (int) Math.floor(REACTOR_X - CORE_RAIL_R - 1);
		int r1 = (int) Math.ceil(REACTOR_X + CORE_RAIL_R + 1);
		int q0 = (int) Math.floor(REACTOR_Z - CORE_RAIL_R - 1);
		int q1 = (int) Math.ceil(REACTOR_Z + CORE_RAIL_R + 1);
		b.spans(r0, q0, r1, q1, REACTOR_BOTTOM, rail, java.util.Set.of());

		for (int x = r0; x <= r1; x++) {
			for (int z = q0; z <= q1; z++) {
				net.minecraft.world.phys.shapes.VoxelShape cap = ProxyShapes.spans(x, z, 16, railCap);

				if (!cap.isEmpty() && b.isAir(x, REACTOR_BOTTOM + 1, z)) {
					b.ghost(x, REACTOR_BOTTOM + 1, z, cap);
				}
			}
		}

		// The lift's four posts and the ribs up the shaft wall collide as drawn (one walked into them).
		for (int y = REACTOR_BOTTOM; y < HANGAR; y++) {
			b.solid((int) REACTOR_LIFT.minX - 1, (int) REACTOR_LIFT.minZ - 1, (int) REACTOR_LIFT.maxX, (int) REACTOR_LIFT.maxZ, y,
					DestroyerInterior::liftPostAt, 1.0, java.util.Set.of());
		}

		for (float angle : RIB_ANGLES) {
			double rx = REACTOR_X + Math.cos(Math.toRadians(angle)) * RIB_R;
			double rz = REACTOR_Z + Math.sin(Math.toRadians(angle)) * RIB_R;
			ProxyShapes.Solid rib = (x, z) -> (x - rx) * (x - rx) + (z - rz) * (z - rz) < RIB_SIZE * RIB_SIZE;

			for (int y = REACTOR_BOTTOM; y < REACTOR_TOP; y++) {
				b.solid((int) Math.floor(rx - 1), (int) Math.floor(rz - 1), (int) Math.floor(rx + 1), (int) Math.floor(rz + 1), y, rib, 1.0,
						java.util.Set.of());
			}
		}

		b.light((int) REACTOR_X - 9, REACTOR_BOTTOM + 2, (int) REACTOR_Z, 12);
		b.light((int) REACTOR_X + 9, REACTOR_BOTTOM + 2, (int) REACTOR_Z, 12);
		b.light((int) REACTOR_X, REACTOR_BOTTOM + 2, (int) REACTOR_Z - 9, 12);
		b.light((int) REACTOR_X, REACTOR_BOTTOM + 2, (int) REACTOR_Z + 9, 12);
	}

	/** Phase 18: whether a point (plot space) is inside one of the energy converters on the shaft floor. */
	public static boolean converterAt(double x, double z) {
		for (double angle : CONVERTER_ANGLES) {
			double a = Math.toRadians(angle);

			if (Math.hypot(x - (REACTOR_X + Math.cos(a) * CONVERTER_R), z - (REACTOR_Z + Math.sin(a) * CONVERTER_R)) < CONVERTER_SIZE) {
				return true;
			}
		}

		return false;
	}

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
		list.add(Crew.at(TECH, WORK, REACTOR_X - 0.5, hangar, REACTOR_Z - 9.3, 0.0F).as("reactor"));

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
		// lower level, more crew.
		return 6;
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
				new Lift(new AABB(CX - 2, DECK + 1, COMMAND_BACK - 2, CX + 3, DECK + 4, COMMAND_BACK), new Vec3(CX + 0.5, HANGAR + 1, LOBBY_Z1 - 4.5), 0.0F),
				// Phase 18: the reactor's maintenance lift, from the catwalk down to the shaft floor and back.
				new Lift(REACTOR_LIFT.move(0, HANGAR + 1 - REACTOR_BOTTOM, 0), new Vec3(REACTOR_LIFT.minX - 1.2, REACTOR_BOTTOM, REACTOR_LIFT.getCenter().z), 0.0F),
				new Lift(REACTOR_LIFT, new Vec3(REACTOR_LIFT.minX - 1.2, HANGAR + 1, REACTOR_LIFT.getCenter().z), 180.0F));
	}

	/**
	 * Phase 18: the reactor's lower level, on the shaft floor round the core's base: four energy
	 * converters (tall machines), control consoles along the wall, a lift pad (here at the bottom;
	 * its twin is on the catwalk right above). Angles from east, clockwise seen from above.
	 */
	public static final AABB REACTOR_LIFT = new AABB(30, REACTOR_BOTTOM, 24, 32, REACTOR_BOTTOM + 2, 26);
	public static final double CONVERTER_R = 10.2;
	public static final double CONVERTER_SIZE = 1.3;
	public static final double[] CONVERTER_ANGLES = {45.0, 135.0, 225.0, 315.0};
	/** The consoles of the lower level: cells (at the wall, facing the core). */
	public static final List<BlockPos> REACTOR_CONSOLES = List.of(new BlockPos(52, REACTOR_BOTTOM, 24), new BlockPos(40, REACTOR_BOTTOM, 36),
			new BlockPos(40, REACTOR_BOTTOM, 12));

	/** The radius of the core's housing at the shaft floor, up the cone (row y). */
	public static double coreBase(int y) {
		return y >= REACTOR_BOTTOM + 5 ? 2.8 : 5.5 - (y - REACTOR_BOTTOM) * (5.5 - 2.8) / 5.0;
	}

	/** Phase 18: the cone at the core's foot as drawn: its radius at the floor and at its top, its height. */
	static final double CORE_BASE_R = 5.5;
	static final double CORE_TOP_R = 2.8;
	static final double CORE_CONE = 5.0;
	/** Phase 18: the railing round the core's foot on the lower level: its radius, half its thickness. */
	public static final double CORE_RAIL_R = 6.3;
	public static final double CORE_RAIL_HALF = 0.08;
	/** Phase 18: half the thickness of the lift's posts (one at each corner of {@link #REACTOR_LIFT}). */
	public static final double LIFT_POST = 0.0625;
	/** Phase 18: the ribs up the shaft wall (they collide as drawn): their angles, where they stand, how thick. */
	public static final float[] RIB_ANGLES = {15, 45, 75, 105, 135, 165, 195, 225, 255, 285, 345};
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
				new Room("vent", VENTS.get(3)),
				new Room("hidden_room", HIDDEN),
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
			new AABB(18, HANGAR + 1, -9, 19, HANGAR + 2, -7));

	/** Phase 18: the hidden room off the crawl way (inside, walls included: x 13..23, z -8..-1). */
	public static final AABB HIDDEN = new AABB(14, HANGAR + 1, -7, 23, HANGAR + 4, -1);

	public static final List<Hatch> HATCHES = List.of(
			new Hatch(new BlockPos(CX - 4, HANGAR + 1, -10), net.minecraft.core.Direction.EAST),
			new Hatch(new BlockPos(10, HANGAR + 1, -35), net.minecraft.core.Direction.NORTH),
			new Hatch(new BlockPos(WALK_X0, PIT + 1, 84), net.minecraft.core.Direction.WEST),
			new Hatch(new BlockPos(WALK_X1, PIT + 1, 84), net.minecraft.core.Direction.EAST));

	/** The hidden room's opening (no hatch: from inside, nobody needs to hide it). */
	public static final BlockPos HIDDEN_MOUTH = new BlockPos(18, HANGAR + 1, -8);

	@Override
	public List<Hatch> hatches() {
		return HATCHES;
	}

	@Override
	public List<BlockPos> crawlMouths() {
		return List.of(HATCHES.get(0).cell(), HATCHES.get(1).cell(), HATCHES.get(2).cell(), HATCHES.get(3).cell(), HIDDEN_MOUTH);
	}

	private static void vents(InteriorBuilder b) {
		// The hangar deck's way: a shell round it first (one block of floor, way, ceiling), then hollowed out.
		b.box(9, HANGAR, -11, CX - 4, HANGAR + 2, -9, DARK);
		b.box(9, HANGAR, -35, 11, HANGAR + 2, -9, DARK);
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
		b.light(18, HANGAR + 3, -4, 7);
	}

	/** Phase 17: those of the crew who stay in their place collide there (a slim column, two rows). */
	private static void crew(InteriorBuilder b) {
		Crew.collide(b, CREW);
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
		reactorFloor(b);
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

		// Phase 18: like a fence, half a block more over each railing (only colliding): one jumped
		// onto them and walked along on the invisible blocks.
		for (int z = PLATFORM_Z1; z <= BRIDGE_BACK - 1; z++) {
			for (int x = BRIDGE_X0 + 1; x <= BRIDGE_X1 - 1; x++) {
				if (b.get(x, DECK + 1, z).is(net.minecraft.world.level.block.Blocks.BARRIER) && b.isAir(x, DECK + 2, z)
						&& (x == WALK_X0 || x == WALK_X1 || z == PLATFORM_Z1)) {
					b.ghost(x, DECK + 2, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
				}
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
				// Phase 18: a block and a half (from a chair one jumped onto the half block and stood
				// over the console in the air), colliding only (no outline over the console).
				b.ghost(x, PIT + 2, z, net.minecraft.world.phys.shapes.Shapes.block());

				if (b.isAir(x, PIT + 3, z)) {
					b.ghost(x, PIT + 3, z, net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0));
				}

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

			// Phase 18: nobody climbs onto them (one stood over the drawn console, and from there
			// on the head of the technician at work beside it).
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
