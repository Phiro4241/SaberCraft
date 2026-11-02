package com.phiro.sabercraft.interior;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 14: the walkable interior of the Millennium Falcon, in the Minecraft style and at about
 * real size (one block a metre: the round hull is 32 blocks across, the forward cargo pods reach
 * 16 blocks further). Built from vanilla blocks only.
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
	static final int FLOOR = 6;
	static final double CX = 20.5;
	static final double CZ = 30.5;

	private static final String HULL = "white_terracotta";
	private static final String SHELL = "light_gray_terracotta";
	private static final String PANEL = "light_gray_concrete";
	private static final String DECK = "polished_andesite";
	private static final String GRATE = "iron_trapdoor[half=top,open=false,facing=north]";
	private static final String DARK = "polished_blackstone";
	private static final String TRIM = "gray_concrete";

	@Override
	public String id() {
		return "falcon";
	}

	@Override
	public int version() {
		return 1;
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
				new Room("quarters", new AABB(3, 7, 22, 10, 10, 29)),
				new Room("galley", new AABB(3, 7, 33, 10, 10, 40)),
				new Room("engine_room", new AABB(11, 7, 43, 31, 11, 50)),
				new Room("cargo_hold", new AABB(9, 7, 2, 16, 11, 18)),
				new Room("storage", new AABB(26, 7, 2, 33, 11, 18)),
				new Room("ramp", new AABB(33, 3, 30, 38, 11, 42)),
				new Room("main_hold", new AABB(14, 7, 24, 28, 11, 38)),
				new Room("corridor", new AABB(9, 7, 19, 32, 10, 42)));
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
	}

	/** The solid hull: the saucer and the shells of everything that sticks out of it. */
	private static void hull(InteriorBuilder b) {
		for (int y = FLOOR - 1; y <= FLOOR + 5; y++) {
			b.disc(CX, y, CZ, 16.0, y == FLOOR - 1 || y == FLOOR + 5 ? SHELL : HULL);
		}

		b.box(2, 5, 21, 10, 11, 29, HULL);      // quarters
		b.box(2, 5, 32, 10, 11, 40, HULL);      // galley and medbay
		b.box(10, 5, 42, 31, 11, 50, HULL);     // engine room
		b.box(8, 5, 1, 16, 11, 18, HULL);       // port cargo pod
		b.box(25, 5, 1, 33, 11, 18, HULL);      // starboard pod
		b.box(29, 6, 22, 39, 9, 25, HULL);      // cockpit tube
		b.box(38, 5, 18, 47, 11, 26, HULL);     // cockpit
		b.box(32, 1, 29, 38, 11, 42, HULL);     // boarding ramp
		// The gun turrets above and below the middle.
		b.disc(CX, FLOOR + 5, CZ, 3.5, SHELL);
		b.disc(CX, 0, CZ, 3.5, "glass");
		b.ringWall(CX, 1, 4, CZ, 2.5, 3.5, "glass");
		b.disc(CX, 4, CZ, 3.5, SHELL);
	}

	/** All rooms cut out of the hull, before anything is put into them. */
	private static void carve(InteriorBuilder b) {
		int air0 = FLOOR + 1;
		b.clear(3, air0, 22, 9, air0 + 2, 28);        // quarters
		b.clear(3, air0, 33, 9, air0 + 2, 39);        // galley
		b.clear(11, air0, 43, 30, air0 + 3, 49);      // engine room
		b.clear(9, air0, 2, 15, air0 + 3, 17);        // cargo hold
		b.clear(26, air0, 2, 32, air0 + 3, 17);       // storage
		b.clear(30, air0, 23, 38, air0 + 1, 24);      // tube
		b.clear(39, air0, 19, 46, air0 + 3, 25);      // cockpit
		b.clear(33, 3, 30, 37, 10, 41);               // ramp hall
		// The ring corridor (3 high) and the main hold in the middle (4 high).
		for (int y = air0; y <= air0 + 2; y++) {
			b.ring(CX, y, CZ, 8.0, 11.0, "air");
		}

		for (int y = air0; y <= air0 + 3; y++) {
			b.disc(CX, y, CZ, 7.0, "air");
		}

		// Doorways from the hold to the corridor, and from the corridor to every room.
		b.clear(20, air0, 21, 21, air0 + 2, 24);
		b.clear(20, air0, 37, 21, air0 + 2, 40);
		b.clear(11, air0, 30, 14, air0 + 2, 31);
		b.clear(27, air0, 30, 30, air0 + 2, 31);
		b.clear(10, air0, 25, 10, air0 + 1, 25);
		b.clear(10, air0, 35, 10, air0 + 1, 35);
		b.clear(20, air0, 42, 21, air0 + 2, 42);
		b.clear(12, air0, 18, 13, air0 + 2, 22);
		b.clear(27, air0, 18, 28, air0 + 2, 22);
		b.clear(32, air0, 30, 32, air0 + 2, 31);
		// Turrets.
		b.disc(CX, 1, CZ, 2.5, "air");
		b.disc(CX, 2, CZ, 2.5, "air");
		b.disc(CX, 3, CZ, 2.5, "air");
		b.ringWall(CX, FLOOR + 6, FLOOR + 8, CZ, 2.5, 3.5, "glass");
		b.disc(CX, FLOOR + 9, CZ, 3.5, "glass");

		for (int y = FLOOR + 6; y <= FLOOR + 8; y++) {
			b.disc(CX, y, CZ, 2.5, "air");
		}

		b.disc(CX, FLOOR + 5, CZ, 2.0, "iron_block");
	}

	/** The ring corridor: deck plates and gratings, a lit ceiling, pipes along the walls. */
	private static void corridor(InteriorBuilder b) {
		for (int x = 8; x <= 33; x++) {
			for (int z = 18; z <= 43; z++) {
				double d = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);

				if (d > 8.0 && d <= 11.0) {
					boolean grate = (x + z) % 3 == 0;
					b.set(x, FLOOR, z, grate ? GRATE : DECK);
					b.set(x, FLOOR + 4, z, (x * 7 + z * 3) % 11 == 0 ? "sea_lantern" : PANEL);
				}
			}
		}

		// A band of padded wall panels along both sides at shoulder height, and light.
		for (int x = 8; x <= 33; x++) {
			for (int z = 18; z <= 43; z++) {
				double d = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);

				if (d > 11.0 && d <= 12.0 && b.get(x, FLOOR + 2, z).is(InteriorBuilder.state(HULL).getBlock())) {
					b.set(x, FLOOR + 2, z, "smooth_quartz");
				} else if (d > 7.0 && d <= 8.0 && b.get(x, FLOOR + 2, z).is(InteriorBuilder.state(HULL).getBlock())) {
					b.set(x, FLOOR + 2, z, "smooth_quartz");
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

	/** The main hold: the round game table with its glowing board, the curved couch, consoles. */
	private static void mainHold(InteriorBuilder b) {
		for (int x = 13; x <= 28; x++) {
			for (int z = 23; z <= 38; z++) {
				double d = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);

				if (d <= 7.0) {
					b.set(x, FLOOR, z, d < 2.0 ? "polished_deepslate" : (x + z) % 2 == 0 ? DECK : "andesite");
					b.set(x, FLOOR + 5, z, d < 5.0 && d > 4.0 ? "pearlescent_froglight" : PANEL);
				}
			}
		}

		// The round game table, front left in the hold, with a checkered board of light.
		double tx = 16.5;
		double tz = 27.5;
		b.disc(tx, FLOOR + 1, tz, 1.7, "polished_blackstone_slab[type=top]");
		b.disc(tx, FLOOR + 2, tz, 1.7, "black_carpet");
		b.disc(tx, FLOOR + 2, tz, 1.2, "lime_carpet");
		b.set(16, FLOOR + 2, 27, "yellow_carpet");
		b.set(16, FLOOR + 2, 28, "lime_carpet");
		b.set(15, FLOOR + 2, 27, "lime_carpet");
		b.set(17, FLOOR + 2, 28, "yellow_carpet");
		b.set(16, FLOOR + 2, 26, "yellow_carpet");
		b.set(15, FLOOR + 2, 28, "yellow_carpet");
		b.set(16, FLOOR + 3, 27, "small_amethyst_bud");
		b.set(17, FLOOR + 3, 27, "amethyst_cluster");

		// The curved couch around the table (back, left and front), facing it.
		for (int x = 12; x <= 21; x++) {
			for (int z = 22; z <= 33; z++) {
				double dx = x + 0.5 - tx;
				double dz = z + 0.5 - tz;
				double d = Math.hypot(dx, dz);

				if (d > 2.4 && d <= 3.3 && dx < 1.2 && Math.hypot(x + 0.5 - CX, z + 0.5 - CZ) < 6.6) {
					String facing = Math.abs(dx) > Math.abs(dz) ? dx > 0 ? "east" : "west" : dz > 0 ? "south" : "north";
					b.set(x, FLOOR + 1, z, "smooth_sandstone_stairs[facing=" + facing + "]");
				}
			}
		}

		// The gun well: the ladder up to the upper turret and down to the lower one, on a pipe post.
		for (int y = 1; y <= FLOOR + 6; y++) {
			b.set(20, y, 31, y >= FLOOR + 1 && y <= FLOOR + 4 ? "iron_block" : "polished_andesite");
			b.set(20, y, 30, "ladder[facing=north]");
		}

		b.set(20, FLOOR + 5, 30, "ladder[facing=north]");
		b.set(20, FLOOR - 1, 30, "ladder[facing=north]");
		b.set(20, FLOOR, 30, "ladder[facing=north]");

		// The engineering station on the right: consoles, screens and switches.
		for (int z = 29; z <= 33; z++) {
			b.set(26, FLOOR + 1, z, DARK);
			b.set(26, FLOOR + 2, z, z % 2 == 0 ? "lever[face=floor,facing=west]" : "stone_button[face=floor,facing=west]");
			b.set(27, FLOOR + 1, z, "observer[facing=west]");
			b.set(27, FLOOR + 2, z, z == 31 ? "verdant_froglight" : "tinted_glass");
		}

		b.set(25, FLOOR + 1, 31, "quartz_stairs[facing=west]");
		b.set(24, FLOOR + 1, 25, "chiseled_bookshelf[facing=south]");
		b.set(23, FLOOR + 1, 24, "barrel[facing=south]");
		b.light(17, FLOOR + 3, 31, 12);
		b.light(23, FLOOR + 3, 29, 12);
	}

	/** The two gun turrets: a seat, the controls and the quad gun barrels in front of the glass. */
	private static void turrets(InteriorBuilder b) {
		int top = FLOOR + 6;
		b.set(20, top, 28, "quartz_stairs[facing=south]");
		b.set(19, top, 28, "lever[face=floor,facing=north]");
		b.set(21, top, 28, "lever[face=floor,facing=north]");
		b.set(20, top, 27, DARK);
		b.light(21, top + 2, 31, 10);
		b.set(20, 1, 28, "quartz_stairs[facing=south]");
		b.set(19, 1, 28, "lever[face=floor,facing=north]");
		b.set(21, 1, 28, "lever[face=floor,facing=north]");
		b.set(20, 1, 27, DARK);
		b.light(21, 2, 31, 10);

		// The barrels, outside the glass, pointing forward.
		for (int x : new int[] {19, 22}) {
			for (int z = 23; z <= 26; z++) {
				b.set(x, top + 1, z, "end_rod[facing=north]");
				b.set(x, 2, z, "end_rod[facing=north]");
			}
		}
	}

	/** The cockpit: four seats, the consoles, the overhead switches and the window frames. */
	private static void cockpit(InteriorBuilder b) {
		int y0 = FLOOR + 1;
		// The access tube: padded walls with lights.
		for (int x = 30; x <= 38; x++) {
			b.set(x, FLOOR, 23, DECK);
			b.set(x, FLOOR, 24, DECK);
			b.set(x, FLOOR + 3, 23, x % 3 == 0 ? "sea_lantern" : PANEL);
			b.set(x, FLOOR + 3, 24, PANEL);
		}

		b.box(39, FLOOR, 19, 46, FLOOR, 25, "gray_concrete");
		b.box(39, FLOOR + 5, 19, 46, FLOOR + 5, 25, PANEL);
		// The window: the front and the front of the roof, and the right side, in a frame.
		b.box(39, y0 + 1, 18, 46, y0 + 3, 18, "glass");
		b.box(39, FLOOR + 5, 19, 46, FLOOR + 5, 20, "glass");
		b.box(47, y0 + 1, 19, 47, y0 + 3, 22, "glass");

		for (int x : new int[] {39, 42, 43, 46}) {
			b.set(x, y0 + 1, 18, "polished_deepslate");
			b.set(x, y0 + 2, 18, "polished_deepslate");
			b.set(x, y0 + 3, 18, "polished_deepslate");
			b.set(x, FLOOR + 5, 19, "polished_deepslate");
		}

		b.box(39, y0 + 2, 18, 46, y0 + 2, 18, "polished_deepslate");
		b.box(39, y0 + 2, 18, 41, y0 + 2, 18, "glass");
		b.box(44, y0 + 2, 18, 45, y0 + 2, 18, "glass");

		// The consoles in front of the seats, with switches and lit displays.
		for (int x = 39; x <= 46; x++) {
			b.set(x, y0, 19, x % 3 == 0 ? "verdant_froglight" : DARK);
			b.set(x, y0 + 1, 19, x == 41 ? "lever[face=floor,facing=south]" : x % 2 == 0 ? "stone_button[face=floor,facing=south]"
					: "polished_blackstone_slab[type=bottom]");
		}

		b.set(41, y0 + 1, 19, "lever[face=floor,facing=south]");
		b.set(42, y0 + 1, 19, "comparator[facing=south]");
		b.set(44, y0 + 1, 19, "repeater[facing=south]");
		b.set(39, y0, 20, DARK);
		b.set(46, y0, 20, DARK);
		b.set(39, y0 + 1, 20, "stone_button[face=floor,facing=east]");
		b.set(46, y0 + 1, 20, "stone_button[face=floor,facing=west]");

		// Seats: pilot (left), co-pilot (right), two behind.
		b.set(41, y0, 21, "quartz_stairs[facing=south]");
		b.set(44, y0, 21, "quartz_stairs[facing=south]");
		b.set(41, y0, 24, "quartz_stairs[facing=south]");
		b.set(44, y0, 24, "quartz_stairs[facing=south]");

		// Overhead switch panel.
		for (int x = 40; x <= 45; x++) {
			b.set(x, FLOOR + 4, 22, x % 2 == 0 ? "stone_button[face=ceiling,facing=south]" : "air");
		}

		b.light(42, FLOOR + 4, 23, 11);
		b.light(45, FLOOR + 4, 25, 9);
	}

	/** The crew quarters: bunks with lockers, a table. */
	private static void quarters(InteriorBuilder b) {
		b.box(3, FLOOR, 22, 9, FLOOR, 28, "spruce_planks");
		b.box(3, FLOOR + 4, 22, 9, FLOOR + 4, 28, PANEL);

		for (int z : new int[] {22, 25, 28}) {
			b.set(4, FLOOR + 1, z, "white_bed[part=foot,facing=west]");
			b.set(3, FLOOR + 1, z, "white_bed[part=head,facing=west]");
		}

		b.set(3, FLOOR + 1, 23, "barrel[facing=east]");
		b.set(3, FLOOR + 1, 26, "barrel[facing=east]");
		b.set(9, FLOOR + 1, 22, "chest[facing=west]");
		b.set(8, FLOOR + 1, 27, "spruce_slab[type=top]");
		b.set(9, FLOOR + 1, 27, "spruce_stairs[facing=east]");
		b.set(8, FLOOR + 3, 25, "lantern[hanging=true]");
		b.light(6, FLOOR + 3, 23, 12);
	}

	/** The galley and the medical bay: a bench, a stove, storage, a sick bed. */
	private static void galley(InteriorBuilder b) {
		b.box(3, FLOOR, 33, 9, FLOOR, 39, "white_concrete");
		b.box(3, FLOOR + 4, 33, 9, FLOOR + 4, 39, PANEL);
		b.set(3, FLOOR + 1, 33, "smoker[facing=east]");
		b.set(3, FLOOR + 1, 34, "white_concrete");
		b.set(3, FLOOR + 1, 35, "cauldron");
		b.set(3, FLOOR + 1, 36, "barrel[facing=east]");
		b.set(3, FLOOR + 2, 34, "brewing_stand");
		// The medical bay at the back.
		b.set(8, FLOOR + 1, 38, "light_blue_bed[part=foot,facing=west]");
		b.set(7, FLOOR + 1, 38, "light_blue_bed[part=head,facing=west]");
		b.set(9, FLOOR + 1, 39, "white_concrete");
		b.set(9, FLOOR + 2, 39, "light_blue_stained_glass");
		b.set(6, FLOOR + 1, 39, "chest[facing=north]");
		b.set(5, FLOOR + 4, 36, "sea_lantern");
		b.light(7, FLOOR + 3, 35, 12);
	}

	/** The engine room: the glowing hyperdrive core, machines, pipes, the exhaust grilles. */
	private static void engineRoom(InteriorBuilder b) {
		b.box(11, FLOOR, 43, 30, FLOOR, 49, "polished_deepslate");
		b.box(11, FLOOR + 5, 43, 30, FLOOR + 5, 49, "gray_concrete");

		for (int x = 11; x <= 30; x += 2) {
			b.set(x, FLOOR, 46, GRATE);
		}

		// The hyperdrive core.
		b.box(19, FLOOR + 1, 45, 22, FLOOR + 4, 48, "light_blue_stained_glass");
		b.box(20, FLOOR + 1, 46, 21, FLOOR + 4, 47, "sea_lantern");
		b.box(19, FLOOR + 1, 45, 22, FLOOR + 1, 48, "polished_blackstone");

		for (int x : new int[] {19, 22}) {
			for (int z : new int[] {45, 48}) {
				b.box(x, FLOOR + 1, z, x, FLOOR + 4, z, "iron_block");
			}
		}

		// Machines along both sides.
		for (int x = 11; x <= 17; x++) {
			b.set(x, FLOOR + 1, 49, x % 2 == 0 ? "blast_furnace[facing=north,lit=true]" : "copper_block");
			b.set(x, FLOOR + 2, 49, x % 3 == 0 ? "copper_bulb[lit=true]" : "waxed_copper_grate");
			b.set(x, FLOOR + 3, 49, "lightning_rod[facing=up]");
		}

		for (int x = 24; x <= 30; x++) {
			b.set(x, FLOOR + 1, 49, x % 2 == 0 ? "piston[facing=north]" : "dispenser[facing=north]");
			b.set(x, FLOOR + 2, 49, x % 3 == 0 ? "copper_bulb[lit=true]" : "observer[facing=north]");
		}

		for (int z = 43; z <= 48; z++) {
			b.set(11, FLOOR + 1, z, z % 2 == 0 ? "crafter[orientation=east_up]" : "smoker[facing=east,lit=true]");
			b.set(30, FLOOR + 1, z, "cauldron");
			b.set(30, FLOOR + 2, z, z % 2 == 0 ? "waxed_oxidized_copper_grate" : "air");
		}

		// Pipes under the ceiling and the exhaust grilles in the back wall.
		for (int x = 11; x <= 30; x++) {
			b.set(x, FLOOR + 4, 43, "iron_chain[axis=x]");
			b.set(x, FLOOR + 4, 49, "iron_chain[axis=x]");
		}

		for (int x = 13; x <= 28; x++) {
			if (x < 19 || x > 22) {
				b.set(x, FLOOR + 2, 50, "iron_bars");
				b.set(x, FLOOR + 3, 50, "iron_bars");
			}
		}

		b.set(13, FLOOR + 1, 44, "lectern[facing=east]");
		b.light(15, FLOOR + 4, 46, 13);
		b.light(26, FLOOR + 4, 46, 13);
	}

	/** The forward pods: crates in the port cargo hold, hidden compartments under the starboard floor. */
	private static void cargo(InteriorBuilder b) {
		b.box(9, FLOOR, 2, 15, FLOOR, 17, "spruce_planks");
		b.box(26, FLOOR, 2, 32, FLOOR, 17, DECK);
		b.box(9, FLOOR + 5, 2, 15, FLOOR + 5, 17, PANEL);
		b.box(26, FLOOR + 5, 2, 32, FLOOR + 5, 17, PANEL);

		for (int z = 3; z <= 15; z += 3) {
			b.set(9, FLOOR + 1, z, "barrel[facing=east]");
			b.set(9, FLOOR + 2, z, z % 2 == 0 ? "barrel[facing=east]" : "air");
			b.set(15, FLOOR + 1, z, "chest[facing=west]");
			b.set(10, FLOOR + 1, z + 1, "scaffolding[distance=0,bottom=false]");
		}

		b.set(12, FLOOR + 1, 3, "barrel[facing=up]");
		b.set(13, FLOOR + 1, 3, "barrel[facing=up]");
		b.set(12, FLOOR + 2, 3, "barrel[facing=up]");
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
				b.set(x, 2, z, (x + z) % 2 == 0 ? "yellow_concrete" : "black_concrete");
			}
		}

		b.box(34, 3, 42, 36, 5, 42, "white_stained_glass");
		b.box(33, 3, 42, 33, 5, 42, TRIM);
		b.box(37, 3, 42, 37, 5, 42, TRIM);
		b.light(35, 4, 40, 15);
		b.light(35, FLOOR + 3, 31, 13);
		b.set(33, FLOOR + 2, 33, "stone_button[face=wall,facing=east]");
	}
}
