package com.phiro.sabercraft.client.interior;

import static com.phiro.sabercraft.interior.FalconInterior.*;

import java.util.Set;

import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.interior.Skin.Kind;
import com.phiro.sabercraft.client.interior.Skin.Surface;
import com.phiro.sabercraft.interior.InteriorPlan;

/**
 * Phase 15: the inside of the Millennium Falcon: padded, ribbed and well-worn. Smooth round walls
 * along the ring corridor, the hold and the engine room, ribs and pipes, the game table with its
 * hologram and the curved couch, the engineering station, the cockpit's consoles and seats, the
 * bunks, the galley, the hyperdrive and its machines, crates, the ramp.
 */
final class FalconRooms {
	private FalconRooms() {
	}

	private static final Set<Block> SHELL = Set.of(Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.lightGray(), Blocks.CONCRETE.lightGray(),
			Blocks.POLISHED_ANDESITE, Blocks.POLISHED_BLACKSTONE, Blocks.CONCRETE.gray(), Blocks.CONCRETE.yellow(), Blocks.IRON_BLOCK);

	static final float CXF = (float) CX;
	static final float CZF = (float) CZ;
	static final int Y0 = FLOOR + 1;

	static void build(MeshBuilder b) {
		InteriorPlan plan = InteriorPlan.of("falcon");
		VirtualPlot plot = VirtualPlot.of(plan);
		b.layer(Layer.SOLID).worldLight().white().anim(0).sun(null, 0.0F);
		Skin.apply(plot, plan, b, FalconRooms::skin);
		roundWalls(b, plot);
		corridor(b);
		hold(b);
		cockpit(b);
		quarters(b);
		galley(b);
		engineRoom(b);
		cargo(b);
		ramp(b);
		turretSeats(b);
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	// --- Panelling -------------------------------------------------------------------------------

	static float dist(double x, double z) {
		return (float) Math.sqrt((x - CX) * (x - CX) + (z - CZ) * (z - CZ));
	}

	/**
	 * Whether a wall face seen from an open cell is covered by something else: a smooth round wall
	 * (everything round the hold and the ring corridor, the engine room's inner wall) or a
	 * doorway's lining (Phase 16: flat panels there stood out of the curve and showed in the
	 * corridor).
	 */
	private static boolean coveredWall(Surface s) {
		// The curved walls' cells are barriers that look solid to the panelling, so no flat panel
		// can stand in front of a curve any more; only the doorways through them are lined apart.
		return doorAt(s.x(), s.y(), s.z());
	}

	private static void skin(MeshBuilder b, Surface s, VirtualPlot plot) {
		Block block = s.block().getBlock();

		if (!SHELL.contains(block)) {
			return;
		}

		int bx = s.x() + s.toward().getStepX();
		int by = s.y() + s.toward().getStepY();
		int bz = s.z() + s.toward().getStepZ();

		// The helm lever's pedestal is inside the console; the gun well post has its own model.
		if (bx == 41 && by == Y0 && bz == 19 || bx == 20 && bz == 31) {
			return;
		}

		if (s.kind() == Kind.WALL && coveredWall(s) || s.kind() == Kind.CEILING && doorAt(s.x(), s.y(), s.z())) {
			return;
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0);

		switch (s.kind()) {
			case FLOOR -> {
				if (block == Blocks.CONCRETE.yellow()) {
					b.fit(InteriorAtlas.IMP_HAZARD);
				} else if (s.in("cockpit") || s.in("quarters")) {
					b.fit(InteriorAtlas.FAL_FLOOR_PLATE);
				} else if (s.in("corridor") || s.in("engine_room") || s.in("cockpit_tube")) {
					b.fit(InteriorAtlas.FAL_FLOOR);
				} else {
					b.tile(InteriorAtlas.FAL_FLOOR_PLATE, 2.0F);
				}

				Skin.face(b, s, Skin.EPS);
			}
			case CEILING -> {
				boolean lamp = false;

				for (int i = 0; i <= 2 && !lamp; i++) {
					lamp = plot.is(s.x(), s.y() - i, s.z(), Blocks.LIGHT);
				}

				if (lamp) {
					b.fit(InteriorAtlas.FAL_CEILING_LIGHT);
					Skin.face(b, s, Skin.EPS);
					b.layer(Layer.GLOW).fullBright().colour(0xFFE8DCC0).fit(InteriorAtlas.FAL_CEILING_LIGHT_GLOW);
					Skin.face(b, s, Skin.EPS * 2);
					b.layer(Layer.SOLID).worldLight().white();
				} else {
					b.fit(InteriorAtlas.FAL_CEILING);
					Skin.face(b, s, Skin.EPS);
				}
			}
			case WALL -> {
				int h = s.height();
				int along = s.along();

				if (h == 0) {
					b.fit(Math.floorMod(along * 7 + s.y(), 5) == 0 ? InteriorAtlas.FAL_WALL_TECH : InteriorAtlas.FAL_WALL_RIB);
				} else if (h == 1) {
					b.fit(InteriorAtlas.FAL_WALL_PAD);
				} else {
					b.fit(Math.floorMod(along, 4) == 0 ? InteriorAtlas.FAL_WALL_TECH : InteriorAtlas.FAL_WALL_RIB);
				}

				Skin.face(b, s, Skin.EPS);

				if (h == 0) {
					b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
					Skin.skirting(b, s, plot, 0.12F, 0.06F);
				}

				if (s.top() && s.roomHeight() >= 3) {
					b.tile(InteriorAtlas.FAL_METAL, 1.0F);
					Skin.ceilingChamfer(b, s, 0.45F);
				}
			}
		}
	}

	/**
	 * The smooth round walls: the hold's, both sides of the ring corridor and the engine room's
	 * inner wall; open exactly between the sides of the doorways (Phase 16: they were open by whole
	 * segments, so the wall ended a little before or after the doorway), with the doorways lined.
	 */
	private static void roundWalls(MeshBuilder b, VirtualPlot plot) {
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		RoundWalls.Tiles tiles = (pen, h, index) -> pen.fit(h == 1 ? InteriorAtlas.FAL_WALL_PAD
				: h == 0 && index % 5 == 2 ? InteriorAtlas.FAL_WALL_TECH : InteriorAtlas.FAL_WALL_RIB);
		RoundWalls.wall(b, CXF, CZF, (float) HOLD_WALL, Y0, Y0 + 4, true, 0.0F, 360.0F, 72, DOORWAYS, tiles);
		RoundWalls.wall(b, CXF, CZF, (float) RING_IN_WALL, Y0, Y0 + 3, false, 0.0F, 360.0F, 80, DOORWAYS, tiles);
		RoundWalls.wall(b, CXF, CZF, (float) RING_OUT_WALL, Y0, Y0 + 3, true, 0.0F, 360.0F, 96, DOORWAYS, tiles);
		RoundWalls.wall(b, CXF, CZF, (float) ENGINE_WALL, Y0, Y0 + 4, false, (float) ENGINE_A0, (float) ENGINE_A1, 32, DOORWAYS, tiles);
		RoundWalls.linings(b, DOORWAYS, com.phiro.sabercraft.interior.FalconInterior::open, () -> b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F),
				() -> b.fit(InteriorAtlas.FAL_CEILING));
	}

	// --- The ring corridor ---------------------------------------------------------------------

	private static void corridor(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		float y1 = Y0 + 3;

		// Ribs across the corridor every 30 degrees: posts on both walls, a beam under the ceiling.
		for (int i = 0; i < 12; i++) {
			float a = i * 30.0F;
			b.push().translate(CXF, 0, CZF).rotateY(-a);
			// In this frame +x points along the radius.
			b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
			float r0 = (float) RING_IN + 0.05F;
			float r1 = (float) RING_OUT - 0.05F;
			// The posts end under the beam: overlapping, their sides would lie in the beam's.
			b.box(r0, Y0, -0.18F, r0 + 0.22F, y1 - 0.25F, 0.18F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(r1 - 0.22F, Y0, -0.18F, r1, y1 - 0.25F, 0.18F, MeshBuilder.ALL & ~MeshBuilder.EAST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(r0, y1 - 0.25F, -0.18F, r1, y1, 0.18F, MeshBuilder.ALL & ~MeshBuilder.UP);
			// A little lamp under the beam.
			b.layer(Layer.GLOW).fullBright().colour(0xFFE8D8B0).fit(InteriorAtlas.FAL_CEILING_LIGHT_GLOW);
			b.rect(r0 + 1.0F, y1 - 0.252F, 0.1F, r1 - r0 - 2.0F, 0, 0, 0, 0, -0.2F);
			b.layer(Layer.SOLID).worldLight().white();
			b.pop();
		}

		// Pipes along both sides under the ceiling, all round.
		for (float[] pipe : new float[][] {{(float) RING_IN + 0.35F, y1 - 0.35F, 0.12F}, {(float) RING_OUT - 0.4F, y1 - 0.35F, 0.15F},
				{(float) RING_OUT - 0.4F, y1 - 0.75F, 0.08F}}) {
			b.tile(pipe[2] > 0.1F ? InteriorAtlas.FAL_PIPE : InteriorAtlas.FAL_PIPE_GREY, 1.0F);
			int steps = 48;
			float[] points = new float[(steps + 1) * 3];

			for (int i = 0; i <= steps; i++) {
				float a = i * Mth.TWO_PI / steps;
				points[i * 3] = CXF + Mth.cos(a) * pipe[0];
				points[i * 3 + 1] = pipe[1];
				points[i * 3 + 2] = CZF + Mth.sin(a) * pipe[0];
			}

			b.pipe(pipe[2], 6, points);
		}

		// The cockpit tube: ribs every two blocks.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);

		for (int x = 31; x <= 38; x += 2) {
			b.box(x, Y0, 23.0F, x + 0.25F, Y0 + 1.8F, 23.2F, MeshBuilder.ALL & ~MeshBuilder.NORTH & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(x, Y0, 24.8F, x + 0.25F, Y0 + 1.8F, 25.0F, MeshBuilder.ALL & ~MeshBuilder.SOUTH & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(x, Y0 + 1.8F, 23.0F, x + 0.25F, Y0 + 2, 25.0F, MeshBuilder.ALL & ~MeshBuilder.UP);
		}
	}

	// --- The main hold -------------------------------------------------------------------------

	private static void hold(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		float tx = (float) TABLE_X;
		float tz = (float) TABLE_Z;
		// The game table: a pedestal, the round top with the board.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.cylinder(new Vector3f(tx, Y0, tz), new Vector3f(0, 0.55F, 0), 1.0F, 0.8F, 16, 2.0F, false, false);
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);
		b.cylinder(new Vector3f(tx, Y0 + 0.55F, tz), new Vector3f(0, 0.22F, 0), 1.75F, 1.75F, 20, 3.0F, true, false);
		b.fit(InteriorAtlas.FAL_TABLE);
		b.ring(tx, Y0 + 0.77F, tz, 0.0F, 1.75F, 20, true);
		b.layer(Layer.GLOW).fullBright().colour(0xFF60C090).anim(InteriorScene.ANIM_SLOW).fit(InteriorAtlas.FAL_HOLO_GLOW);
		b.ring(tx, Y0 + 0.775F, tz, 0.2F, 0.35F, 16, true);
		b.anim(0).white().layer(Layer.SOLID).worldLight();

		// The curved couch round the table's far side.
		b.fit(InteriorAtlas.FAL_COUCH);

		for (int k = 0; k < 24; k++) {
			float a0 = 60.0F + k * 10.0F;
			float a1 = a0 + 10.0F;
			Vector3f mid = new Vector3f(tx + Mth.cos((a0 + 5.0F) * Mth.DEG_TO_RAD) * 2.85F, 0, tz + Mth.sin((a0 + 5.0F) * Mth.DEG_TO_RAD) * 2.85F);

			if (Mth.cos((a0 + 5.0F) * Mth.DEG_TO_RAD) * 2.85F >= 1.2F || dist(mid.x, mid.z) >= HOLD_R - MARGIN - 0.1F) {
				continue;
			}

			couch(b, tx, tz, a0, a1);
		}

		// The engineering station: a desk facing the hold, the tall bank of instruments behind it.
		Props.at(b, 26.5, Y0, 31.5, Props.west());
		falconDesk(b, 5.0F, 0.8F, 3);
		b.pop();
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(27.02F, Y0, 28.02F, 27.98F, Y0 + 3.0F, 34.98F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN);

		for (int z = 28; z <= 34; z++) {
			b.fit(z % 2 == 0 ? InteriorAtlas.FAL_MACHINE : InteriorAtlas.FAL_CONSOLE);
			b.rect(27.02F, Y0 + 3.0F, z, 0, 0, 1, 0, -1.4F, 0);
			b.fit(InteriorAtlas.FAL_WALL_TECH);
			b.rect(27.02F, Y0 + 1.6F, z, 0, 0, 1, 0, -1.6F, 0);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(z % 2 == 0 ? InteriorAtlas.FAL_SCREEN_GLOW : InteriorAtlas.FAL_CONSOLE_GLOW);
			b.rect(27.01F, Y0 + 2.9F, z + 0.1F, 0, 0, 0.8F, 0, -1.2F, 0);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}

		Props.at(b, 25.5, Y0, 31.5, Props.west());
		Props.chair(b, InteriorAtlas.FAL_COUCH);
		b.pop();

		// The locker by the corridor door.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(24.04F, Y0, 25.04F, 24.96F, Y0 + 1.96F, 25.96F);
		b.fit(InteriorAtlas.FAL_HATCH);
		b.rect(24.04F, Y0 + 1.9F, 26.0F, 0.92F, 0, 0, 0, -1.8F, 0);

		// The gun well's post: a pipe with the ladder on its front.
		b.tile(InteriorAtlas.FAL_PIPE_GREY, 1.0F);
		// It stands on a real block (the ladder needs it): a little bigger, so that its faces never
		// lie on the block's own (that flickered).
		b.box(19.985F, 1.0F, 30.985F, 21.015F, FLOOR + 7.015F, 32.015F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		// Openings in the floor and the ceiling round the ladder.
		b.box(19.8F, FLOOR + 0.98F, 29.8F, 21.2F, FLOOR + 1.03F, 31.0F, MeshBuilder.UP);
		b.box(19.8F, FLOOR + 4.97F, 29.8F, 21.2F, FLOOR + 5.02F, 31.0F, MeshBuilder.DOWN);

		// A ring of light in the ceiling.
		b.layer(Layer.GLOW).fullBright().colour(0xFFE8D8B0).fit(InteriorAtlas.FAL_CEILING_LIGHT_GLOW);
		b.ring(CXF, FLOOR + 4.99F, CZF, 4.1F, 4.5F, 40, false);
		b.layer(Layer.SOLID).worldLight().white();
	}

	/** One segment of the curved couch: seat and back between two angles round the table. */
	private static void couch(MeshBuilder b, float tx, float tz, float a0, float a1) {
		float rIn = 2.4F;
		float rOut = 3.25F;
		float seat = Y0 + 0.45F;
		float back = Y0 + 1.15F;
		Vector3f i0 = new Vector3f(tx + Mth.cos(a0 * Mth.DEG_TO_RAD) * rIn, 0, tz + Mth.sin(a0 * Mth.DEG_TO_RAD) * rIn);
		Vector3f i1 = new Vector3f(tx + Mth.cos(a1 * Mth.DEG_TO_RAD) * rIn, 0, tz + Mth.sin(a1 * Mth.DEG_TO_RAD) * rIn);
		Vector3f o0 = new Vector3f(tx + Mth.cos(a0 * Mth.DEG_TO_RAD) * rOut, 0, tz + Mth.sin(a0 * Mth.DEG_TO_RAD) * rOut);
		Vector3f o1 = new Vector3f(tx + Mth.cos(a1 * Mth.DEG_TO_RAD) * rOut, 0, tz + Mth.sin(a1 * Mth.DEG_TO_RAD) * rOut);
		Vector3f m0 = new Vector3f(i0).lerp(o0, 0.72F);
		Vector3f m1 = new Vector3f(i1).lerp(o1, 0.72F);
		// The seat.
		b.polyFacing(java.util.List.of(y(i0, seat), y(i1, seat), y(m1, seat), y(m0, seat)), new Vector3f(0, 1, 0));
		b.polyFacing(java.util.List.of(y(i0, seat), y(i1, seat), y(i1, Y0), y(i0, Y0)), new Vector3f(i0.x + i1.x - 2 * tx, 0, i0.z + i1.z - 2 * tz).negate());
		// The back: leaning slightly outwards.
		b.polyFacing(java.util.List.of(y(m0, seat), y(m1, seat), y(o1, back), y(o0, back)), new Vector3f(tx * 2 - m0.x - m1.x, 0.8F, tz * 2 - m0.z - m1.z));
		b.polyFacing(java.util.List.of(y(o0, back), y(o1, back), y(o1, Y0), y(o0, Y0)), new Vector3f(o0.x + o1.x - 2 * tx, 0, o0.z + o1.z - 2 * tz));
	}

	private static Vector3f y(Vector3f p, float y) {
		return new Vector3f(p.x, y, p.z);
	}

	/** A desk in the Falcon's style (worn panels, rows of switches). */
	static void falconDesk(MeshBuilder b, float width, float height, int seed) {
		Props.desk(b, width, height, InteriorAtlas.FAL_CONSOLE, InteriorAtlas.FAL_CONSOLE, InteriorAtlas.FAL_CONSOLE_GLOW, InteriorAtlas.FAL_SCREEN_GLOW,
				false, seed);
	}

	// --- The cockpit ---------------------------------------------------------------------------

	private static void cockpit(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		// The main console across the front, the helm lever on the pilot's side.
		Props.at(b, 43.0, Y0, 19.5, Props.south());
		falconDesk(b, 8.0F, 0.85F, 5);
		b.pop();
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(40.98F, Y0, 18.98F, 42.02F, Y0 + 1.015F, 20.02F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		// Side consoles.
		for (float x : new float[] {39.5F, 46.5F}) {
			Props.at(b, x, Y0, 20.5, x < 43 ? Props.east() : Props.west());
			falconDesk(b, 1.0F, 0.85F, 7);
			b.pop();
		}

		// The overhead switch panel.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(40.0F, Y0 + 3.55F, 20.5F, 46.0F, Y0 + 4.0F, 23.0F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		b.fit(InteriorAtlas.FAL_CONSOLE);
		b.rect(40.0F, Y0 + 3.548F, 23.0F, 6.0F, 0, 0, 0, 0, -2.5F);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_BLINK).fit(InteriorAtlas.FAL_CONSOLE_GLOW);
		b.rect(40.0F, Y0 + 3.545F, 23.0F, 6.0F, 0, 0, 0, 0, -2.5F);
		b.layer(Layer.SOLID).worldLight().anim(0);

		// Four seats, high backed, facing the window.
		for (int[] seat : new int[][] {{41, 21}, {44, 21}, {41, 24}, {44, 24}}) {
			Props.at(b, seat[0] + 0.5, Y0, seat[1] + 0.5, Props.south());
			b.scale(1.25F, 1.15F, 1.25F);
			Props.chair(b, InteriorAtlas.FAL_COUCH);
			b.pop();
		}

		// Frames round the inside of the pod.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);

		for (int z = 21; z <= 25; z += 2) {
			b.box(39.0F, Y0, z, 39.25F, Y0 + 3.75F, z + 0.3F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(46.75F, Y0, z, 47.0F, Y0 + 3.75F, z + 0.3F, MeshBuilder.ALL & ~MeshBuilder.EAST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(39.0F, Y0 + 3.75F, z, 47.0F, Y0 + 4.0F, z + 0.3F, MeshBuilder.ALL & ~MeshBuilder.UP);
		}
	}

	// --- Quarters, galley, cargo ---------------------------------------------------------------

	private static void quarters(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();

		for (int z : new int[] {22, 25, 28}) {
			bunk(b, 4.0F, 7.0F, z, Y0, true);
			bunk(b, 4.0F, 7.0F, z, Y0 + 1.5F, false);
		}

		// The table.
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);
		b.box(8.05F, Y0 + 0.72F, 27.05F, 8.95F, Y0 + 0.8F, 27.95F);
		b.box(8.4F, Y0, 27.4F, 8.6F, Y0 + 0.72F, 27.6F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
	}

	/** A bunk from x0 to x1 in the row z (blocks), its mattress at y. */
	private static void bunk(MeshBuilder b, float x0, float x1, int z, float y, boolean lower) {
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(x0 + 0.02F, y + (lower ? 0.0F : 0.3F), z + 0.05F, x1 - 0.02F, y + 0.45F, z + 0.95F);
		b.fit(InteriorAtlas.FAL_BUNK);
		b.box(x0 + 0.1F, y + 0.45F, z + 0.1F, x1 - 0.15F, y + 0.62F, z + 0.9F, MeshBuilder.ALL & ~MeshBuilder.DOWN);

		if (!lower) {
			// Posts holding the upper bunk.
			b.tile(InteriorAtlas.FAL_METAL, 1.0F);

			for (float x : new float[] {x1 - 0.1F}) {
				for (float dz : new float[] {0.08F, 0.92F}) {
					b.box(x - 0.05F, Y0 + 0.45F, z + dz - 0.05F, x + 0.05F, y + 0.3F, z + dz + 0.05F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
				}
			}
		}
	}

	private static void galley(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		// The counter along the west wall.
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);
		b.box(5.02F, Y0, 33.02F, 5.98F, Y0 + 0.92F, 35.98F, MeshBuilder.ALL & ~MeshBuilder.DOWN);

		for (int z = 33; z <= 35; z++) {
			b.fit(z == 34 ? InteriorAtlas.FAL_MACHINE : InteriorAtlas.FAL_WALL_TECH);
			b.rect(5.985F, Y0 + 0.9F, z + 1, 0, 0, -1, 0, -0.9F, 0);
		}

		// The medical bed, a scanner over it.
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);
		b.box(7.05F, Y0, 38.1F, 8.95F, Y0 + 0.55F, 38.9F);
		b.fit(InteriorAtlas.FAL_BUNK);
		b.box(7.1F, Y0 + 0.55F, 38.15F, 8.9F, Y0 + 0.68F, 38.85F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(7.3F, Y0 + 2.5F, 38.2F, 8.7F, Y0 + 2.75F, 38.8F);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF80D0FF).fit(InteriorAtlas.FAL_SCREEN_GLOW);
		b.rect(7.35F, Y0 + 2.495F, 38.75F, 1.3F, 0, 0, 0, 0, -0.5F);
		b.layer(Layer.SOLID).worldLight().white().anim(0);
	}

	private static void cargo(MeshBuilder b) {
		Props.crate(b, 12, Y0, 3, 14, Y0 + 2, 5, InteriorAtlas.FAL_CRATE);
		Props.crate(b, 11, Y0, 10, 13, Y0 + 1, 12, InteriorAtlas.FAL_CRATE);
		Props.crate(b, 31, Y0, 9, 33, Y0 + 1, 11, InteriorAtlas.FAL_CRATE);
		// Pipes along the pods' ceilings.
		b.tile(InteriorAtlas.FAL_PIPE, 1.0F);

		for (float x : new float[] {9.35F, 15.65F, 26.35F, 32.65F}) {
			b.pipe(0.14F, 6, x, Y0 + 3.6F, 2.2F, x, Y0 + 3.6F, 17.8F);
		}
	}

	// --- The engine room ------------------------------------------------------------------------

	private static void engineRoom(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		float dx = (float) DRIVE_X;
		float dz = (float) DRIVE_Z;
		float y1 = Y0 + 4;
		// The hyperdrive: a housing with windows onto the glowing core, collars, pipes up and down.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.cylinder(new Vector3f(dx, Y0, dz), new Vector3f(0, 0.6F, 0), 2.1F, 2.0F, 20, 3.0F, false, true);
		b.cylinder(new Vector3f(dx, y1 - 0.6F, dz), new Vector3f(0, 0.6F, 0), 2.0F, 2.1F, 20, 3.0F, true, false);
		b.tile(InteriorAtlas.FAL_PIPE_GREY, 1.0F);

		for (int i = 0; i < 6; i++) {
			float a = i * 60.0F * Mth.DEG_TO_RAD;
			b.box(dx + Mth.cos(a) * 1.75F - 0.15F, Y0 + 0.6F, dz + Mth.sin(a) * 1.75F - 0.15F, dx + Mth.cos(a) * 1.75F + 0.15F, y1 - 0.6F,
					dz + Mth.sin(a) * 1.75F + 0.15F);
		}

		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_PULSE).tile(InteriorAtlas.FAL_HYPERDRIVE_GLOW, 1.5F);
		b.cylinder(new Vector3f(dx, Y0 + 0.6F, dz), new Vector3f(0, y1 - Y0 - 1.2F, 0), 1.45F, 1.45F, 16, 2.0F, false, false);
		b.anim(0).layer(Layer.SOLID).worldLight();
		// Pipes from the drive to the ceiling and round to the machines.
		b.tile(InteriorAtlas.FAL_PIPE, 1.0F);

		for (int i = 0; i < 4; i++) {
			float a = (45.0F + i * 90.0F) * Mth.DEG_TO_RAD;
			b.pipe(0.2F, 6, dx + Mth.cos(a) * 1.2F, y1 - 0.6F, dz + Mth.sin(a) * 1.2F, dx + Mth.cos(a) * 1.2F, y1 - 0.3F, dz + Mth.sin(a) * 1.2F,
					dx + Mth.cos(a) * 3.0F, y1 - 0.3F, dz + Mth.sin(a) * 3.0F);
		}

		// Machines along the outer wall, in segments.
		float a0 = (float) ENGINE_A0;
		float a1 = (float) ENGINE_A1;
		float rIn = (float) ENGINE_R1 - 1.5F;
		float rOut = (float) ENGINE_R1 + 0.4F;
		int segments = 16;

		for (int i = 0; i < segments; i++) {
			float s0 = Mth.lerp(i / (float) segments, a0, a1);
			float s1 = Mth.lerp((i + 1) / (float) segments, a0, a1);
			Vector3f f0 = at(s0, rIn, 0);
			Vector3f f1 = at(s1, rIn, 0);
			Vector3f k0 = at(s0, rOut, 0);
			Vector3f k1 = at(s1, rOut, 0);
			Vector3f inward = at((s0 + s1) / 2, 1.0F, 0).sub(CXF, 0, CZF).negate();
			b.fit(i % 3 == 1 ? InteriorAtlas.FAL_MACHINE : i % 3 == 0 ? InteriorAtlas.FAL_WALL_TECH : InteriorAtlas.FAL_CONSOLE);
			b.polyFacing(java.util.List.of(y(f0, Y0 + 2.0F), y(f1, Y0 + 2.0F), y(f1, Y0), y(f0, Y0)), inward);
			b.tile(InteriorAtlas.FAL_METAL, 1.0F);
			b.polyFacing(java.util.List.of(y(f0, Y0 + 2.0F), y(f1, Y0 + 2.0F), y(k1, Y0 + 2.0F), y(k0, Y0 + 2.0F)), new Vector3f(0, 1, 0));

			if (i % 3 == 2) {
				b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.FAL_CONSOLE_GLOW);
				b.polyFacing(java.util.List.of(y(f0, Y0 + 1.9F), y(f1, Y0 + 1.9F), y(f1, Y0 + 0.9F), y(f0, Y0 + 0.9F)), inward);
				b.layer(Layer.SOLID).worldLight().anim(0);
			}

			// A pipe from each machine up the wall.
			b.tile(InteriorAtlas.FAL_PIPE_GREY, 1.0F);
			Vector3f p = at((s0 + s1) / 2, rIn + 0.4F, 0);
			b.cylinder(new Vector3f(p.x, Y0 + 2.0F, p.z), new Vector3f(0, 2.0F, 0), 0.16F, 0.16F, 6, 1.0F, false, false);
		}

		// The outer wall above the machines: ribbed, round.
		b.fit(InteriorAtlas.FAL_WALL_RIB);
		b.arcWall(CXF, CZF, (float) ENGINE_R1 + 0.45F, a0, a1, Y0 + 2.0F, y1, 16, true);
	}

	private static Vector3f at(float angle, float r, float y) {
		return new Vector3f(CXF + Mth.cos(angle * Mth.DEG_TO_RAD) * r, y, CZF + Mth.sin(angle * Mth.DEG_TO_RAD) * r);
	}

	// --- The ramp and the turrets --------------------------------------------------------------

	private static void ramp(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		// A plated ramp over the steps, with side rails.
		b.tile(InteriorAtlas.FAL_FLOOR_PLATE, 1.0F);
		b.polyFacing(java.util.List.of(new Vector3f(33, FLOOR + 1.25F, 34.0F), new Vector3f(38, FLOOR + 1.25F, 34.0F), new Vector3f(38, 3.25F, 38.0F),
				new Vector3f(33, 3.25F, 38.0F)), new Vector3f(0, 1, -1));
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);

		for (float x : new float[] {33.1F, 37.9F}) {
			b.pipe(0.06F, 5, x, FLOOR + 2.0F, 34.0F, x, 4.0F, 38.0F, x, 4.0F, 41.5F);
		}
	}

	private static void turretSeats(MeshBuilder b) {
		for (int y : new int[] {FLOOR + 6, 1}) {
			Props.at(b, 20.5, y, 28.5, Props.south());
			Props.chair(b, InteriorAtlas.FAL_COUCH);
			b.pop();
			// The gun controls in front of the seat: two handles on a column.
			b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
			b.box(20.1F, y, 27.2F, 20.9F, y + 0.9F, 27.9F);

			for (float x : new float[] {20.15F, 20.85F}) {
				b.cylinder(new Vector3f(x, y + 0.9F, 27.55F), new Vector3f(0, 0.3F, 0), 0.06F, 0.06F, 5, 1.0F, false, true);
			}

			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.FAL_SCREEN_GLOW);
			b.rect(20.2F, y + 0.85F, 27.19F, 0.6F, 0, 0, 0, -0.4F, 0);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}
	}

	// --- Moving parts ----------------------------------------------------------------------------

	static void animate(MeshBuilder b, float seconds) {
		// The game's hologram: four little original creatures standing on the board, bobbing.
		float tx = (float) TABLE_X;
		float tz = (float) TABLE_Z;
		b.layer(Layer.HOLO).fullBright().colourAlpha(0x7060E0A0).fit(InteriorAtlas.FAL_HOLO_GLOW).twoSided(true).anim(0);

		for (int i = 0; i < 4; i++) {
			float a = i * 90.0F + 45.0F;
			float px = tx + Mth.cos(a * Mth.DEG_TO_RAD) * 0.85F;
			float pz = tz + Mth.sin(a * Mth.DEG_TO_RAD) * 0.85F;
			float bob = 0.04F * Mth.sin(seconds * 2.0F + i);
			b.push().translate(px, Y0 + 0.8F + bob, pz).rotateY(seconds * 25.0F + i * 90.0F);

			switch (i) {
				case 0 -> {
					b.cylinder(new Vector3f(0, 0, 0), new Vector3f(0, 0.3F, 0), 0.12F, 0.08F, 6, 1.0F, false, false);
					b.sphere(new Vector3f(0, 0.38F, 0), 0.1F, 6, 4);
				}
				case 1 -> {
					b.box(-0.1F, 0.0F, -0.06F, 0.1F, 0.22F, 0.06F);
					b.cylinder(new Vector3f(0, 0.22F, 0), new Vector3f(0, 0.14F, 0.06F), 0.07F, 0.03F, 5, 1.0F, false, true);
				}
				case 2 -> {
					b.sphere(new Vector3f(0, 0.14F, 0), 0.14F, 6, 4);
					b.cylinder(new Vector3f(0, 0.25F, 0), new Vector3f(0, 0.18F, 0), 0.05F, 0.0F, 5, 1.0F, false, false);
				}
				default -> {
					b.cylinder(new Vector3f(0, 0, 0), new Vector3f(0, 0.36F, 0), 0.1F, 0.1F, 4, 1.0F, false, true);
					b.box(-0.18F, 0.26F, -0.03F, 0.18F, 0.3F, 0.03F);
				}
			}

			b.pop();
		}

		// The beam up from the board.
		b.colourAlpha(0x3060E0A0);
		b.cylinder(new Vector3f(tx, Y0 + 0.78F, tz), new Vector3f(0, 0.5F, 0), 0.3F, 1.2F, 12, 1.0F, false, false);
		b.twoSided(false).white().layer(Layer.SOLID).worldLight();
	}
}
