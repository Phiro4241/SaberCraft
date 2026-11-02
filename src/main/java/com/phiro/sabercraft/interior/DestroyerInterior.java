package com.phiro.sabercraft.interior;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 14: the walkable part of the Star Destroyer. Not the whole ship (it would be kilometres
 * long), but the two places that matter, connected by turbolifts:
 *
 * <ul>
 * <li><b>Hangar deck</b>, where the tractor beam sets people down: a tall hangar with a glowing
 * force field open to space, a TIE fighter on its rack, crates, and the flight control room with
 * its consoles. Standing on the striped pad at the force field a moment lowers you back to the
 * ground under the ship.</li>
 * <li><b>Command deck</b>, up the turbolift: a corridor with the conference room (long table,
 * holo projector) and the sensor room (screens, consoles), and the bridge: the long raised
 * walkway between the two sunken crew pits full of consoles and seats, the command platform at
 * the front and the great window onto space. The lever on the helm console at the front hands
 * the ship to its owner.</li>
 * </ul>
 *
 * Dark grey and black Imperial surfaces, white ceiling lights, glowing console lights. Vanilla
 * blocks only.
 */
public final class DestroyerInterior extends InteriorPlan {
	static final int HANGAR = 4;
	static final int BRIDGE = 30;
	static final int PIT = 27;

	private static final String WALL = "gray_concrete";
	private static final String DARK = "polished_deepslate";
	private static final String BLACK = "polished_blackstone";
	private static final String FLOOR = "polished_blackstone_bricks";
	private static final String LIGHT = "sea_lantern";
	private static final String GLOW = "pearlescent_froglight";

	@Override
	public String id() {
		return "star_destroyer";
	}

	@Override
	public int version() {
		return 1;
	}

	@Override
	public BlockPos size() {
		return new BlockPos(45, 44, 50);
	}

	@Override
	public Vec3 entry() {
		return new Vec3(22.5, HANGAR + 1, 12.5);
	}

	@Override
	public float entryYaw() {
		return 0.0F;
	}

	@Override
	public List<AABB> exits() {
		return List.of(new AABB(20.0, HANGAR + 1, 3.0, 26.0, HANGAR + 4, 8.0));
	}

	@Override
	public List<Lift> lifts() {
		return List.of(
				new Lift(new AABB(20.0, HANGAR + 1, 27.0, 25.0, HANGAR + 4, 30.0), new Vec3(22.5, BRIDGE + 1, 47.5), 180.0F),
				new Lift(new AABB(20.0, BRIDGE + 1, 46.0, 25.0, BRIDGE + 4, 49.0), new Vec3(22.5, HANGAR + 1, 28.5), 180.0F));
	}

	@Override
	public BlockPos helm() {
		return new BlockPos(22, BRIDGE + 2, 3);
	}

	@Override
	public String ambient() {
		return "ship.interior_bridge";
	}

	@Override
	public List<Room> rooms() {
		return List.of(
				new Room("flight_control", new AABB(2, HANGAR + 1, 20, 9, HANGAR + 5, 30)),
				new Room("lift", new AABB(20, HANGAR + 1, 27, 25, HANGAR + 4, 30)),
				new Room("hangar", new AABB(2, HANGAR + 1, 2, 43, HANGAR + 13, 31)),
				new Room("lift", new AABB(20, BRIDGE + 1, 46, 25, BRIDGE + 4, 49)),
				new Room("conference", new AABB(6, BRIDGE + 1, 36, 19, BRIDGE + 5, 45)),
				new Room("sensor_room", new AABB(26, BRIDGE + 1, 36, 39, BRIDGE + 5, 45)),
				new Room("command_corridor", new AABB(20, BRIDGE + 1, 34, 25, BRIDGE + 5, 46)),
				new Room("bridge", new AABB(2, PIT + 1, 2, 43, BRIDGE + 10, 33)));
	}

	@Override
	public void build(InteriorBuilder b) {
		hangar(b);
		flightControl(b);
		tie(b);
		commandDeck(b);
		bridge(b);
	}

	private static void hangar(InteriorBuilder b) {
		b.box(1, HANGAR - 1, 1, 43, HANGAR + 13, 31, WALL);
		b.clear(2, HANGAR + 1, 2, 42, HANGAR + 12, 30);
		b.box(2, HANGAR, 2, 42, HANGAR, 30, FLOOR);
		b.box(2, HANGAR + 13, 2, 42, HANGAR + 13, 30, DARK);

		// Walls: dark above, a band of light panels, grey below.
		for (int y = HANGAR + 5; y <= HANGAR + 12; y++) {
			b.box(1, y, 1, 1, y, 31, DARK);
			b.box(43, y, 1, 43, y, 31, DARK);
			b.box(1, y, 31, 43, y, 31, DARK);
		}

		for (int z = 3; z <= 29; z += 4) {
			b.set(1, HANGAR + 4, z, GLOW);
			b.set(43, HANGAR + 4, z, GLOW);
		}

		// Ceiling lights and light.
		for (int x = 5; x <= 40; x += 6) {
			for (int z = 5; z <= 29; z += 6) {
				b.set(x, HANGAR + 13, z, LIGHT);
				b.light(x, HANGAR + 10, z, 15);
			}
		}

		// Lanes on the floor.
		for (int z = 2; z <= 30; z++) {
			b.set(14, HANGAR, z, "gray_concrete");
			b.set(31, HANGAR, z, "gray_concrete");
		}

		// The force field, open to space.
		b.box(9, HANGAR + 1, 1, 35, HANGAR + 10, 1, "light_blue_stained_glass");
		b.box(8, HANGAR + 1, 1, 8, HANGAR + 11, 1, BLACK);
		b.box(36, HANGAR + 1, 1, 36, HANGAR + 11, 1, BLACK);
		b.box(8, HANGAR + 11, 1, 36, HANGAR + 11, 1, BLACK);

		// The tractor beam pad where people arrive, and the striped pad to leave.
		b.ring(22.5, HANGAR, 12.5, 1.6, 2.6, "white_concrete");
		b.disc(22.5, HANGAR, 12.5, 1.6, "light_gray_concrete");
		b.set(22, HANGAR, 12, "sea_lantern");

		for (int x = 20; x <= 25; x++) {
			for (int z = 3; z <= 7; z++) {
				b.set(x, HANGAR, z, (x + z) % 2 == 0 ? "yellow_concrete" : "black_concrete");
			}
		}

		b.light(22, HANGAR + 3, 5, 15);

		// Crates along the east wall.
		for (int z = 3; z <= 12; z += 3) {
			b.set(41, HANGAR + 1, z, "barrel[facing=west]");
			b.set(41, HANGAR + 2, z, "iron_block");
			b.set(40, HANGAR + 1, z, "smooth_stone");
			b.set(41, HANGAR + 1, z + 1, "chest[facing=west]");
		}

		// The turbolift at the back: a cabin with a lit floor.
		b.box(19, HANGAR + 1, 26, 25, HANGAR + 4, 30, DARK);
		b.clear(20, HANGAR + 1, 27, 24, HANGAR + 3, 30);
		b.clear(21, HANGAR + 1, 26, 23, HANGAR + 3, 26);
		b.box(20, HANGAR, 27, 24, HANGAR, 30, "white_concrete");
		b.set(22, HANGAR, 29, "sea_lantern");
		b.set(20, HANGAR + 2, 26, "stone_button[face=wall,facing=north]");
		b.light(22, HANGAR + 3, 28, 13);
	}

	/** The flight control room on the west side: consoles behind a window onto the hangar. */
	private static void flightControl(InteriorBuilder b) {
		b.box(1, HANGAR, 19, 9, HANGAR + 5, 30, DARK);
		b.clear(2, HANGAR + 1, 20, 8, HANGAR + 4, 29);
		b.box(2, HANGAR, 20, 8, HANGAR, 29, FLOOR);
		b.box(9, HANGAR + 2, 20, 9, HANGAR + 4, 29, "glass");
		b.clear(9, HANGAR + 1, 24, 9, HANGAR + 2, 25);
		b.set(9, HANGAR + 3, 24, DARK);
		b.set(9, HANGAR + 3, 25, DARK);

		for (int z = 21; z <= 28; z++) {
			if (z == 24 || z == 25) {
				continue;
			}

			b.set(8, HANGAR + 1, z, BLACK);
			b.set(8, HANGAR + 2, z, z % 2 == 0 ? "lever[face=floor,facing=west]" : "stone_button[face=floor,facing=west]");
			b.set(7, HANGAR + 1, z, "polished_blackstone_stairs[facing=west]");
		}

		for (int z = 20; z <= 29; z++) {
			b.set(2, HANGAR + 2, z, z % 3 == 0 ? "verdant_froglight" : "tinted_glass");
			b.set(2, HANGAR + 3, z, z % 4 == 1 ? "ochre_froglight" : "tinted_glass");
			b.set(3, HANGAR + 1, z, z % 2 == 0 ? "observer[facing=east]" : "polished_blackstone");
		}

		b.light(5, HANGAR + 4, 24, 12);
	}

	/** A TIE fighter on its rack: a ball cockpit between two hexagonal wings. */
	private static void tie(InteriorBuilder b) {
		int cy = HANGAR + 7;
		int cz = 20;

		for (int x = 32; x <= 36; x++) {
			for (int y = cy - 2; y <= cy + 2; y++) {
				for (int z = cz - 2; z <= cz + 2; z++) {
					double d = Math.sqrt((x - 34) * (x - 34) + (y - cy) * (y - cy) + (z - cz) * (z - cz));

					if (d <= 2.3) {
						b.set(x, y, z, "gray_concrete");
					}
				}
			}
		}

		// Its round front window with frame spokes, facing north.
		b.set(34, cy, cz - 2, "black_stained_glass");
		b.set(33, cy, cz - 2, "black_stained_glass");
		b.set(35, cy, cz - 2, "black_stained_glass");
		b.set(34, cy + 1, cz - 2, "black_stained_glass");
		b.set(34, cy - 1, cz - 2, "black_stained_glass");

		// Pylons and the wings: tall six-sided panels with a frame and a grid.
		b.box(30, cy, cz, 31, cy, cz, "light_gray_concrete");
		b.box(37, cy, cz, 38, cy, cz, "light_gray_concrete");

		for (int x : new int[] {29, 39}) {
			for (int y = cy - 5; y <= cy + 5; y++) {
				int half = 5 - Math.max(0, Math.abs(y - cy) - 2);

				for (int z = cz - half; z <= cz + half; z++) {
					boolean edge = Math.abs(z - cz) == half || Math.abs(y - cy) == 5 || z == cz || y == cy;
					b.set(x, y, z, edge ? "gray_concrete" : "black_concrete");
				}
			}
		}

		// The rack it hangs from.
		for (int y = cy + 3; y <= HANGAR + 12; y++) {
			b.set(34, y, cz, "chain[axis=y]");
		}
	}

	/** The command deck: the turbolift, the corridor and the two rooms off it. */
	private static void commandDeck(InteriorBuilder b) {
		b.box(18, BRIDGE - 1, 33, 26, BRIDGE + 5, 50, DARK);
		b.box(5, BRIDGE - 1, 35, 19, BRIDGE + 6, 46, DARK);
		b.box(25, BRIDGE - 1, 35, 40, BRIDGE + 6, 46, DARK);
		b.clear(20, BRIDGE + 1, 34, 24, BRIDGE + 4, 45);
		b.clear(20, BRIDGE + 1, 46, 24, BRIDGE + 3, 48);
		b.clear(6, BRIDGE + 1, 36, 18, BRIDGE + 5, 44);
		b.clear(26, BRIDGE + 1, 36, 39, BRIDGE + 5, 44);
		b.box(20, BRIDGE, 34, 24, BRIDGE, 48, FLOOR);
		b.box(6, BRIDGE, 36, 18, BRIDGE, 44, "dark_oak_planks");
		b.box(26, BRIDGE, 36, 39, BRIDGE, 44, FLOOR);
		b.box(20, BRIDGE, 46, 24, BRIDGE, 48, "white_concrete");
		b.set(22, BRIDGE, 47, LIGHT);
		// Doors into the side rooms.
		b.clear(19, BRIDGE + 1, 39, 19, BRIDGE + 3, 41);
		b.clear(25, BRIDGE + 1, 39, 25, BRIDGE + 3, 41);

		for (int z = 35; z <= 45; z += 3) {
			b.set(22, BRIDGE + 5, z, LIGHT);
			b.set(19, BRIDGE + 3, z, z == 38 || z == 41 ? DARK : GLOW);
			b.set(25, BRIDGE + 3, z, z == 38 || z == 41 ? DARK : GLOW);
		}

		b.light(22, BRIDGE + 3, 40, 14);
		b.light(22, BRIDGE + 2, 47, 13);

		// The conference room: a long table, chairs and the holo projector in its middle.
		b.box(9, BRIDGE + 1, 39, 15, BRIDGE + 1, 41, "dark_oak_slab[type=top]");

		for (int x = 9; x <= 15; x += 2) {
			b.set(x, BRIDGE + 1, 38, "dark_oak_stairs[facing=north]");
			b.set(x, BRIDGE + 1, 42, "dark_oak_stairs[facing=south]");
		}

		b.set(12, BRIDGE + 2, 40, "beacon");
		b.set(12, BRIDGE + 3, 40, "light_blue_stained_glass");
		b.set(12, BRIDGE + 5, 40, LIGHT);
		b.light(8, BRIDGE + 4, 37, 12);
		b.light(16, BRIDGE + 4, 43, 12);

		// The sensor room: a wall of screens with blips, consoles in front.
		for (int x = 27; x <= 38; x++) {
			for (int y = BRIDGE + 1; y <= BRIDGE + 4; y++) {
				boolean blip = (x * 5 + y * 3) % 7 == 0;
				b.set(x, y, 44, blip ? (x % 2 == 0 ? "verdant_froglight" : "ochre_froglight") : "black_stained_glass");
			}

			if (x % 3 != 0) {
				b.set(x, BRIDGE + 1, 41, BLACK);
				b.set(x, BRIDGE + 2, 41, x % 2 == 0 ? "comparator[facing=south]" : "stone_button[face=floor,facing=south]");
				b.set(x, BRIDGE + 1, 40, "polished_blackstone_stairs[facing=south]");
			}
		}

		b.set(38, BRIDGE + 1, 36, "lectern[facing=west]");
		b.light(32, BRIDGE + 4, 38, 12);
	}

	/** The bridge: the walkway between the crew pits, the command platform and the great window. */
	private static void bridge(InteriorBuilder b) {
		b.box(1, PIT - 1, 1, 43, BRIDGE + 10, 33, DARK);
		b.clear(2, PIT + 1, 2, 42, BRIDGE + 9, 32);
		// The crew pits, three blocks below the walkway.
		b.box(2, PIT, 2, 42, PIT, 32, FLOOR);
		// The command platform across the front, the walkway down the middle, at bridge height.
		b.box(2, PIT + 1, 2, 42, BRIDGE, 5, DARK);
		b.box(2, BRIDGE, 2, 42, BRIDGE, 5, FLOOR);
		b.box(19, PIT + 1, 6, 25, BRIDGE, 32, DARK);
		b.box(19, BRIDGE, 6, 25, BRIDGE, 32, FLOOR);
		b.box(21, BRIDGE, 6, 23, BRIDGE, 32, "gray_concrete");
		// The entrance from the corridor.
		b.clear(20, BRIDGE + 1, 33, 24, BRIDGE + 4, 33);

		// The great window: the front wall from side to side, with a frame.
		b.box(3, BRIDGE + 1, 1, 41, BRIDGE + 8, 1, "glass");

		for (int x = 3; x <= 41; x += 4) {
			b.box(x, BRIDGE + 1, 1, x, BRIDGE + 8, 1, BLACK);
		}

		b.box(3, BRIDGE + 4, 1, 41, BRIDGE + 4, 1, BLACK);
		// Railings along the walkway and the platform.
		for (int z = 6; z <= 32; z++) {
			if (z % 8 != 3) {
				b.set(19, BRIDGE + 1, z, "iron_bars");
				b.set(25, BRIDGE + 1, z, "iron_bars");
			}
		}

		// Steps down into the pits.
		for (int z : new int[] {11, 19, 27}) {
			for (int i = 0; i < 3; i++) {
				b.set(18 - i, BRIDGE - i, z, "polished_blackstone_stairs[facing=east]");
				b.set(26 + i, BRIDGE - i, z, "polished_blackstone_stairs[facing=west]");
			}
		}

		// Rows of consoles with seats in both pits, every crew member looking to the window.
		for (int z = 8; z <= 30; z += 4) {
			for (int x = 3; x <= 41; x++) {
				if (x >= 16 && x <= 28 || x == 3 || x == 41) {
					continue;
				}

				b.set(x, PIT + 1, z, BLACK);
				b.set(x, PIT + 2, z, (x + z) % 5 == 0 ? "cyan_stained_glass" : (x + z) % 3 == 0 ? "stone_button[face=floor,facing=south]"
						: "polished_blackstone_slab[type=bottom]");

				if (x % 2 == 0) {
					b.set(x, PIT + 1, z + 1, "polished_blackstone_stairs[facing=south]");
				}
			}

			b.set(9, PIT + 1, z, "verdant_froglight");
			b.set(35, PIT + 1, z, "ochre_froglight");
		}

		// The helm and the command station at the front of the walkway.
		b.box(20, BRIDGE + 1, 3, 24, BRIDGE + 1, 3, BLACK);
		b.set(22, BRIDGE + 2, 3, "lever[face=floor,facing=south]");
		b.set(21, BRIDGE + 2, 3, "cyan_stained_glass");
		b.set(23, BRIDGE + 2, 3, "cyan_stained_glass");
		b.set(20, BRIDGE + 2, 3, "stone_button[face=floor,facing=south]");
		b.set(24, BRIDGE + 2, 3, "stone_button[face=floor,facing=south]");
		b.set(10, BRIDGE + 1, 3, BLACK);
		b.set(34, BRIDGE + 1, 3, BLACK);
		b.set(10, BRIDGE + 2, 3, "comparator[facing=south]");
		b.set(34, BRIDGE + 2, 3, "comparator[facing=south]");

		// Ceiling lights in lines along the bridge, and light down into the pits.
		for (int z = 3; z <= 31; z += 4) {
			for (int x = 6; x <= 38; x += 8) {
				b.set(x, BRIDGE + 9, z, LIGHT);
			}

			b.light(10, PIT + 4, z, 13);
			b.light(34, PIT + 4, z, 13);
			b.light(22, BRIDGE + 4, z, 12);
		}
	}
}
