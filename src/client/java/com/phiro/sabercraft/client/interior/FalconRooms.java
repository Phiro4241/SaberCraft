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
		stores(b, plot);
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	/**
	 * Phase 18: the stores (vanilla barrels, they keep what is put in them) look like the ship's
	 * cargo containers: a container a little bigger than the barrel round it (so that their faces
	 * never lie on each other). A click still opens the barrel inside. The hidden compartments
	 * under the floor are left as they are.
	 */
	private static void stores(MeshBuilder b, VirtualPlot plot) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).fit(InteriorAtlas.FAL_CRATE);
		float e = 0.015F;

		for (var entry : plot.blocks.long2ObjectEntrySet()) {
			if (!entry.getValue().is(Blocks.BARREL)) {
				continue;
			}

			net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(entry.getLongKey());

			if (pos.getY() <= FLOOR) {
				continue;
			}

			// One under another: the lower one without a top, its sides ending where the upper one's begin.
			boolean stacked = plot.is(pos.getX(), pos.getY() + 1, pos.getZ(), Blocks.BARREL);
			int faces = MeshBuilder.ALL & ~MeshBuilder.DOWN & (stacked ? ~MeshBuilder.UP : MeshBuilder.ALL);
			b.box(pos.getX() - e, pos.getY(), pos.getZ() - e, pos.getX() + 1 + e, pos.getY() + 1 + (stacked ? 0.0F : e), pos.getZ() + 1 + e, faces);
		}
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
		if (bx == 42 && by == Y0 && bz == COCKPIT_FRONT + 1 || bx == 20 && bz == 31) {
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
					// Phase 17: small in rooms three high (standing on a crate or barrel, one had the head in it).
					Skin.ceilingChamfer(b, s, s.roomHeight() == 3 ? 0.25F : 0.45F);
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
		RoundWalls.wall(b, CXF, CZF, (float) ENGINE_OUTER_WALL, Y0 + 2, Y0 + 4, true, (float) ENGINE_A0, (float) ENGINE_A1, 32, DOORWAYS, tiles);
		// The sides run on beyond a doorway only inside the saucer's curved walls (not at the cockpit).
		RoundWalls.linings(b, DOORWAYS, com.phiro.sabercraft.interior.FalconInterior::open, (x, y, z) -> x < 38 && plot.wall(x, y, z),
				(door, x, z) -> !com.phiro.sabercraft.interior.FalconInterior.beyondUncrossed(door, x, z), () -> b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F),
				() -> b.fit(InteriorAtlas.FAL_CEILING));
	}

	// --- The ring corridor ---------------------------------------------------------------------

	private static void corridor(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		float y1 = Y0 + 3;

		// Ribs across the corridor every 30 degrees: posts on both walls, a beam under the ceiling.
		for (int i = 0; i < 12; i++) {
			float a = i * 30.0F;

			// Phase 17: no rib in a doorway (four of them stood in the doorways of the hold).
			if (ribInDoorway(a)) {
				continue;
			}

			b.push().translate(CXF, 0, CZF).rotateY(-a);
			// In this frame +x points along the radius.
			b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
			float r0 = (float) RING_IN + 0.05F;
			float r1 = (float) RING_OUT - 0.05F;
			// The posts end under the beam: overlapping, their sides would lie in the beam's.
			b.box(r0, Y0, -0.18F, r0 + 0.08F, y1 - 0.25F, 0.18F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(r1 - 0.08F, Y0, -0.18F, r1, y1 - 0.25F, 0.18F, MeshBuilder.ALL & ~MeshBuilder.EAST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(r0, y1 - 0.25F, -0.18F, r1, y1, 0.18F, MeshBuilder.ALL & ~MeshBuilder.UP);
			// A little lamp under the beam.
			b.layer(Layer.GLOW).fullBright().colour(0xFFE8D8B0).fit(InteriorAtlas.FAL_CEILING_LIGHT_GLOW);
			b.rect(r0 + 1.0F, y1 - 0.262F, 0.1F, r1 - r0 - 2.0F, 0, 0, 0, 0, -0.2F);
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
			// Phase 17: flat, so that one does not walk into them along the tube's walls.
			b.box(x, Y0, 23.0F, x + 0.25F, Y0 + 1.8F, 23.08F, MeshBuilder.ALL & ~MeshBuilder.NORTH & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(x, Y0, 24.92F, x + 0.25F, Y0 + 1.8F, 25.0F, MeshBuilder.ALL & ~MeshBuilder.SOUTH & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
			b.box(x, Y0 + 1.8F, 23.0F, x + 0.25F, Y0 + 2, 25.0F, MeshBuilder.ALL & ~MeshBuilder.UP);
		}
	}

	/** Whether one of the corridor's ribs at that angle would stand in a doorway. */
	static boolean ribInDoorway(float angle) {
		float c = Mth.cos(angle * Mth.DEG_TO_RAD);
		float sn = Mth.sin(angle * Mth.DEG_TO_RAD);

		for (float r = (float) RING_IN - 0.2F; r <= RING_OUT + 0.2F; r += 0.1F) {
			for (float side = -0.25F; side <= 0.25F; side += 0.25F) {
				float x = CXF + c * r - sn * side;
				float z = CZF + sn * r + c * side;

				if (doorAt(Mth.floor(x), Y0, Mth.floor(z)) || inPassage(x, z, Y0)) {
					return true;
				}
			}
		}

		return false;
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
		float runStart = Float.NaN;

		for (int k = 0; k <= 24; k++) {
			float a0 = 60.0F + k * 10.0F;
			float a1 = a0 + 10.0F;
			Vector3f mid = new Vector3f(tx + Mth.cos((a0 + 5.0F) * Mth.DEG_TO_RAD) * 2.85F, 0, tz + Mth.sin((a0 + 5.0F) * Mth.DEG_TO_RAD) * 2.85F);
			boolean there = k < 24 && Mth.cos((a0 + 5.0F) * Mth.DEG_TO_RAD) * 2.85F < 1.2F && dist(mid.x, mid.z) < HOLD_R - MARGIN - 0.1F;

			if (there) {
				couch(b, tx, tz, a0, a1);

				if (Float.isNaN(runStart)) {
					runStart = a0;
				}
			} else if (!Float.isNaN(runStart)) {
				// Both ends of the couch closed (they were open: the missing faces).
				couchEnd(b, tx, tz, runStart, -1.0F);
				couchEnd(b, tx, tz, a0, 1.0F);
				runStart = Float.NaN;
			}
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
			b.rect(26.99F, Y0 + 2.9F, z + 0.1F, 0, 0, 0.8F, 0, -1.2F, 0);
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
		// Frames round the openings in the floor and the ceiling round the ladder. Phase 19: only
		// round them (a dark plate lay over each opening: one climbed through it, right before the eyes).
		for (float y : new float[] {FLOOR + 1.0F, FLOOR + 6.0F}) {
			b.box(19.8F, y, 29.8F, 21.2F, y + 0.03F, 30.0F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			b.box(19.8F, y, 30.0F, 20.0F, y + 0.03F, 31.0F, MeshBuilder.ALL & ~MeshBuilder.DOWN & ~MeshBuilder.EAST);
			b.box(21.0F, y, 30.0F, 21.2F, y + 0.03F, 31.0F, MeshBuilder.ALL & ~MeshBuilder.DOWN & ~MeshBuilder.WEST);
		}

		for (float y : new float[] {FLOOR + 5.0F, 4.0F}) {
			b.box(19.8F, y - 0.03F, 29.8F, 21.2F, y, 30.0F, MeshBuilder.ALL & ~MeshBuilder.UP);
			b.box(19.8F, y - 0.03F, 30.0F, 20.0F, y, 31.0F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.EAST);
			b.box(21.0F, y - 0.03F, 30.0F, 21.2F, y, 31.0F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.WEST);
		}

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

	/** The end of the couch at an angle: its side profile, facing on along the angle (side 1) or back (-1). */
	private static void couchEnd(MeshBuilder b, float tx, float tz, float a, float side) {
		float c = Mth.cos(a * Mth.DEG_TO_RAD);
		float s = Mth.sin(a * Mth.DEG_TO_RAD);
		Vector3f i = new Vector3f(tx + c * 2.4F, 0, tz + s * 2.4F);
		Vector3f o = new Vector3f(tx + c * 3.25F, 0, tz + s * 3.25F);
		Vector3f m = new Vector3f(i).lerp(o, 0.72F);
		float seat = Y0 + 0.45F;
		float back = Y0 + 1.15F;
		// The tangent round the table: the way the angle grows.
		Vector3f facing = new Vector3f(-s * side, 0, c * side);
		// In two convex pieces (under the seat, under the back), side by side.
		b.polyFacing(java.util.List.of(y(i, Y0), y(i, seat), y(m, seat), y(m, Y0)), facing);
		b.polyFacing(java.util.List.of(y(m, Y0), y(m, seat), y(o, back), y(o, Y0)), facing);
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

	/** A point on the cockpit's round wall at an angle round its axis (0 = the right side, 90 = up), at z. */
	private static Vector3f pod(float angle, float r, float z) {
		return new Vector3f((float) COCKPIT_X + Mth.cos(angle * Mth.DEG_TO_RAD) * r, (float) COCKPIT_AXIS + Mth.sin(angle * Mth.DEG_TO_RAD) * r, z);
	}

	/** The angle at which the cockpit's wall (radius r) reaches height y on its right side. */
	private static float podAngle(float r, float y) {
		return (float) Math.toDegrees(Math.asin((y - COCKPIT_AXIS) / r));
	}

	/**
	 * Phase 16: the cockpit, rebuilt: a narrow round pod, longer than wide, the window cone in front.
	 * The round wall from the floor to the low ceiling on both sides (open where the tube comes
	 * in), arches across it, the back wall; the dashboard under the window with the helm lever on
	 * the pilot's side and the hyperdrive lever on the co-pilot's, console bands along both walls,
	 * the overhead switch panel, two rows of seats.
	 */
	private static void cockpit(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		float r = (float) COCKPIT_R;
		float cx = (float) COCKPIT_X;
		float z0 = COCKPIT_FRONT;
		float z1 = COCKPIT_BACK;
		float ceiling = COCKPIT_CEILING;
		float low = podAngle(r, Y0);
		float high = podAngle(r, ceiling);
		float floorHalf = (float) Math.sqrt(r * r - (Y0 - COCKPIT_AXIS) * (Y0 - COCKPIT_AXIS));
		float ceilingHalf = (float) Math.sqrt(r * r - (ceiling - COCKPIT_AXIS) * (ceiling - COCKPIT_AXIS));

		// The floor and the ceiling.
		b.tile(InteriorAtlas.FAL_FLOOR_PLATE, 2.0F);
		b.polyFacing(java.util.List.of(new Vector3f(cx - floorHalf, Y0, z0), new Vector3f(cx + floorHalf, Y0, z0), new Vector3f(cx + floorHalf, Y0, z1),
				new Vector3f(cx - floorHalf, Y0, z1)), new Vector3f(0, 1, 0));
		// Phase 17: the floor goes on into the window cone as a ledge under the glass (one can step
		// up to the window; it collides there too, see FalconInterior.noseSpan).
		for (float z = z0; z > (float) com.phiro.sabercraft.interior.FalconInterior.WINDOW_TIP; z -= 0.25F) {
			float za = z - 0.25F;
			double dy = COCKPIT_AXIS - Y0;
			double ra = com.phiro.sabercraft.interior.FalconInterior.coneRadius(za) + 0.08;
			double rb = com.phiro.sabercraft.interior.FalconInterior.coneRadius(z) + 0.08;

			if (rb <= dy) {
				break;
			}

			float ha = ra > dy ? (float) Math.sqrt(ra * ra - dy * dy) : 0.0F;
			float hb = (float) Math.sqrt(rb * rb - dy * dy);
			b.polyFacing(ha < 0.01F ? java.util.List.of(new Vector3f(cx, Y0, za), new Vector3f(cx + hb, Y0, z), new Vector3f(cx - hb, Y0, z))
					: java.util.List.of(new Vector3f(cx - ha, Y0, za), new Vector3f(cx + ha, Y0, za), new Vector3f(cx + hb, Y0, z), new Vector3f(cx - hb, Y0, z)),
					new Vector3f(0, 1, 0));
		}

		// Phase 18: the floor between the cockpit's door and the pod's own floor (one looked down
		// into space there, right behind the door).
		b.tile(InteriorAtlas.FAL_FLOOR_PLATE, 2.0F);
		b.polyFacing(java.util.List.of(new Vector3f(40.0F, Y0 + Skin.EPS, 23.0F), new Vector3f(cx - floorHalf, Y0 + Skin.EPS, 23.0F),
				new Vector3f(cx - floorHalf, Y0 + Skin.EPS, 25.0F), new Vector3f(40.0F, Y0 + Skin.EPS, 25.0F)), new Vector3f(0, 1, 0));

		b.tile(InteriorAtlas.FAL_CEILING, 1.0F);
		b.polyFacing(java.util.List.of(new Vector3f(cx - ceilingHalf, ceiling, z0), new Vector3f(cx + ceilingHalf, ceiling, z0),
				new Vector3f(cx + ceilingHalf, ceiling, z1), new Vector3f(cx - ceilingHalf, ceiling, z1)), new Vector3f(0, -1, 0));

		// The round wall, in strips a block long, both sides; open where the tube comes in.
		int steps = 10;

		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < steps; i++) {
				float a0 = Mth.lerp((float) i / steps, low, high);
				float a1 = Mth.lerp((float) (i + 1) / steps, low, high);

				if (side == 1) {
					a0 = 180.0F - a0;
					a1 = 180.0F - a1;
				}

				for (int z = (int) z0; z < (int) z1; z++) {
					Vector3f p0 = pod(a0, r, z);
					Vector3f p1 = pod(a1, r, z);
					float ym = (p0.y + p1.y) / 2.0F;

					if (side == 1 && z >= 23 && z < 25 && ym < Y0 + 2) {
						continue;
					}

					b.fit(i < 3 ? InteriorAtlas.FAL_WALL_PAD : (z & 1) == 0 ? InteriorAtlas.FAL_WALL_RIB : InteriorAtlas.FAL_WALL_TECH,
							0.0F, (float) i / steps, 1.0F, (float) (i + 1) / steps);
					Vector3f inward = new Vector3f(cx - (p0.x + p1.x) / 2.0F, (float) COCKPIT_AXIS - ym, 0);
					b.polyFacing(java.util.List.of(p0, p1, pod(a1, r, z + 1), pod(a0, r, z + 1)), inward);
				}
			}
		}

		// The back wall: the circle between floor and ceiling.
		java.util.List<Vector3f> back = new java.util.ArrayList<>();

		for (int i = 0; i <= 12; i++) {
			back.add(pod(Mth.lerp(i / 12.0F, low, high), r, z1));
		}

		for (int i = 0; i <= 12; i++) {
			back.add(pod(180.0F - Mth.lerp(i / 12.0F, high, low), r, z1));
		}

		b.fit(InteriorAtlas.FAL_WALL_TECH);
		b.polyFacing(back, new Vector3f(0, 0, -1));

		// Arches across the pod, on the wall.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);

		for (float z : new float[] {z0 + 0.6F, 26.2F, z1 - 0.3F}) {
			// Up both walls to just under the ceiling, then a flat beam across it, a little thicker
			// than the pipes so that its faces never lie in theirs (a bent pipe's faces met in the
			// corners and flickered there).
			float top = podAngle(r - 0.06F, ceiling - 0.1F);
			int n = 6;

			for (int side = 0; side < 2; side++) {
				float[] arch = new float[3 * (n + 1)];

				for (int i = 0; i <= n; i++) {
					float a = Mth.lerp((float) i / n, low + 10.0F, top);
					Vector3f p = pod(side == 0 ? a : 180.0F - a, r - 0.06F, z);
					arch[i * 3] = p.x;
					arch[i * 3 + 1] = p.y;
					arch[i * 3 + 2] = p.z;
				}

				b.pipe(0.07F, 5, arch);
			}

			float half = pod(top, r - 0.06F, z).x - cx + 0.12F;
			b.box(cx - half, ceiling - 0.17F, z - 0.1F, cx + half, ceiling - 0.01F, z + 0.1F, MeshBuilder.ALL & ~MeshBuilder.UP);
		}

		// Console bands along both walls beside the front seats: switches and lit screens.
		for (int side = 0; side < 2; side++) {
			float a0 = podAngle(r, Y0 + 1.25F);
			float a1 = podAngle(r, Y0 + 0.75F);

			if (side == 1) {
				a0 = 180.0F - a0;
				a1 = 180.0F - a1;
			}

			Vector3f p0 = pod(a0, r - 0.03F, z0 + 0.2F);
			Vector3f p1 = pod(a1, r - 0.03F, z0 + 0.2F);
			Vector3f q0 = pod(a0, r - 0.03F, 23.0F);
			Vector3f q1 = pod(a1, r - 0.03F, 23.0F);
			Vector3f inward = new Vector3f(cx - p0.x, 0.3F, 0);
			b.layer(Layer.SOLID).worldLight().fit(InteriorAtlas.FAL_CONSOLE);
			b.polyFacing(java.util.List.of(p0, p1, q1, q0), inward);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_BLINK).fit(InteriorAtlas.FAL_CONSOLE_GLOW);
			b.polyFacing(java.util.List.of(pod(a0, r - 0.04F, z0 + 0.2F), pod(a1, r - 0.04F, z0 + 0.2F), pod(a1, r - 0.04F, 23.0F),
					pod(a0, r - 0.04F, 23.0F)), inward);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}

		// The dashboard across the front, under the window.
		Props.at(b, cx, Y0, z0 + 1.5, Props.south());
		falconDesk(b, 2.0F * Math.min(floorHalf, 2.6F) - 0.1F, 0.85F, 5);
		b.pop();
		// The helm lever's pedestal (a real block inside the dashboard).
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.box(41.98F, Y0, z0 + 0.98F, 43.02F, Y0 + 1.015F, z0 + 2.02F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		// The hyperdrive lever on the co-pilot's side: a handle in a lit slot.
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);
		b.box(cx + 0.95F, Y0 + 0.86F, z0 + 1.3F, cx + 1.45F, Y0 + 0.9F, z0 + 1.75F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.cylinder(new Vector3f(cx + 1.2F, Y0 + 0.9F, z0 + 1.55F), new Vector3f(0, 0.32F, -0.12F), 0.04F, 0.04F, 6, 1.0F, false, false);
		b.tile(InteriorAtlas.FAL_PIPE, 1.0F);
		b.sphere(new Vector3f(cx + 1.2F, Y0 + 1.25F, z0 + 1.42F), 0.07F, 6, 4);

		// The overhead switch panel above the front seats.
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		// Phase 17: flatter, so that it stays over the head of someone standing on the helm's pedestal.
		b.box(cx - 1.9F, ceiling - 0.17F, z0 + 1.6F, cx + 1.9F, ceiling - 0.02F, z0 + 4.2F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		b.fit(InteriorAtlas.FAL_CONSOLE);
		b.rect(cx - 1.9F, ceiling - 0.172F, z0 + 4.2F, 3.8F, 0, 0, 0, 0, -2.6F);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_BLINK).fit(InteriorAtlas.FAL_CONSOLE_GLOW);
		b.rect(cx - 1.9F, ceiling - 0.182F, z0 + 4.2F, 3.8F, 0, 0, 0, 0, -2.6F);
		// A lamp strip down the middle of the ceiling behind it.
		b.anim(InteriorScene.ANIM_LAMP).colour(0xFFE8DCC0).fit(InteriorAtlas.FAL_CEILING_LIGHT_GLOW);
		b.rect(cx - 0.35F, ceiling - 0.01F, z1 - 0.6F, 0.7F, 0, 0, 0, 0, -(z1 - z0 - 5.0F));
		b.white().layer(Layer.SOLID).worldLight().anim(0);

		// Four seats, high backed, facing the window: pilot (left), co-pilot (right), two behind.
		for (InteriorPlan.Seat seat : SEATS) {
			if (seat.cell().getX() >= 41) {
				Props.at(b, seat.at().x, Y0, seat.at().z, Props.south());
				b.scale(1.25F, 1.15F, 1.25F);
				Props.chair(b, InteriorAtlas.FAL_COUCH);
				b.pop();
			}
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
		// Phase 17: the window round the core, where one stops (it collides there).
		b.layer(Layer.GLASS).worldLight().anim(0).colourAlpha(0x38A0C8F0).fit(InteriorAtlas.FAL_COCKPIT_GLASS);
		b.cylinder(new Vector3f(dx, Y0 + 0.6F, dz), new Vector3f(0, y1 - Y0 - 1.2F, 0), (float) DRIVE_WINDOW, (float) DRIVE_WINDOW, 24, 2.0F, false, false);
		b.white().anim(0).layer(Layer.SOLID).worldLight();
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
		float rIn = (float) MACHINE_R;
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
				// Phase 22: the screen stands a little in front of the machine's face; on it, the two
				// fought for the same depth and the screens around the reactor flickered.
				Vector3f g0 = at(s0, rIn - 0.02F, 0);
				Vector3f g1 = at(s1, rIn - 0.02F, 0);
				b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.FAL_CONSOLE_GLOW);
				b.polyFacing(java.util.List.of(y(g0, Y0 + 1.9F), y(g1, Y0 + 1.9F), y(g1, Y0 + 0.9F), y(g0, Y0 + 0.9F)), inward);
				b.layer(Layer.SOLID).worldLight().anim(0);
			}

			// A pipe from each machine up the wall.
			b.tile(InteriorAtlas.FAL_PIPE_GREY, 1.0F);
			Vector3f p = at((s0 + s1) / 2, rIn + 0.4F, 0);
			b.cylinder(new Vector3f(p.x, Y0 + 2.0F, p.z), new Vector3f(0, 2.0F, 0), 0.16F, 0.16F, 6, 1.0F, false, false);
		}

		// The outer wall above the machines: round (Phase 17: drawn with the other round walls).

		// Phase 22: the ceiling all the way to the outer wall. The ceiling's blocks end half a block
		// short of it (their middles further out are not in the room), and through the gap one
		// saw the stars over the machines. A strip under the ceiling closes it.
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.FAL_CEILING, 1.0F);
		float cIn = (float) ENGINE_R1 - 0.3F;
		float cOut = (float) ENGINE_OUTER_WALL + 0.06F;
		float cy = Y0 + 4 - 0.012F;
		int strips = 32;

		for (int i = 0; i < strips; i++) {
			float s0 = Mth.lerp(i / (float) strips, a0, a1);
			float s1 = Mth.lerp((i + 1) / (float) strips, a0, a1);
			b.polyFacing(java.util.List.of(y(at(s0, cIn, 0), cy), y(at(s0, cOut, 0), cy), y(at(s1, cOut, 0), cy), y(at(s1, cIn, 0), cy)),
					new Vector3f(0, -1, 0));
		}
	}

	private static Vector3f at(float angle, float r, float y) {
		return new Vector3f(CXF + Mth.cos(angle * Mth.DEG_TO_RAD) * r, y, CZF + Mth.sin(angle * Mth.DEG_TO_RAD) * r);
	}

	// --- The ramp and the turrets --------------------------------------------------------------

	private static void ramp(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white();
		// A plated ramp over the steps, with side rails.
		b.tile(InteriorAtlas.FAL_FLOOR_PLATE, 1.0F);
		// Phase 17: right on the ramp one walks on (it was a quarter block over the stairs).
		float top = (float) rampHeight(RAMP_TOP_Z) + Skin.EPS;
		float bottom = (float) rampHeight(RAMP_TOP_Z + 4) + Skin.EPS;
		b.polyFacing(java.util.List.of(new Vector3f(33, top, RAMP_TOP_Z), new Vector3f(38, top, RAMP_TOP_Z), new Vector3f(38, bottom, RAMP_TOP_Z + 4),
				new Vector3f(33, bottom, RAMP_TOP_Z + 4)), new Vector3f(0, 1, 1));
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);

		// The rails close along the walls (one walked into them).
		for (float x : new float[] {33.05F, 37.95F}) {
			b.pipe(0.04F, 5, x, top + 1.0F, RAMP_TOP_Z, x, bottom + 1.0F, RAMP_TOP_Z + 4, x, bottom + 1.0F, 41.5F);
		}
	}

	private static void turretSeats(MeshBuilder b) {
		// Phase 18: a deck plate in the lower turret, where one stands over the glass bowl (one stood
		// on an invisible floor in front of the stars).
		b.layer(Layer.SOLID).worldLight().white().tile(InteriorAtlas.FAL_FLOOR_PLATE, 2.0F);
		b.ring(CXF, 1.0F + Skin.EPS, CZF, 0.0F, 2.75F, 40, true);
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		b.ring(CXF, 1.0F + Skin.EPS, CZF, 2.75F, 2.95F, 40, true);

		// Phase 19: the inside of the collar round each dome, from the deck up to the glass and from
		// the glass up to the hull (one looked out through the hull there, past the floor's edge).
		b.tile(InteriorAtlas.FAL_METAL_DARK, 1.0F);
		float sin15 = Mth.sin(15.0F * Mth.DEG_TO_RAD);
		collar(b, FLOOR + 6.0F, (float) (TOP_DOME_Y + TOP_DOME_R * sin15) + 0.06F, 4.03F);
		collar(b, (float) (BOTTOM_DOME_Y - BOTTOM_DOME_R * sin15) - 0.06F, 5.0F, 4.03F);
		b.tile(InteriorAtlas.FAL_METAL, 1.0F);
		// Under the deck round the opening (just in front of the blocks' faces: on them it flickered).
		b.ring(CXF, 5.0F - Skin.EPS, CZF, 3.0F, 4.05F, 40, false);

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
			b.rect(20.2F, y + 0.85F, 27.17F, 0.6F, 0, 0, 0, -0.4F, 0);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}
	}

	/** Phase 19: a round wall seen from inside, round the middle of the saucer, between two heights. */
	private static void collar(MeshBuilder b, float y0, float y1, float r) {
		int segments = 40;

		for (int i = 0; i < segments; i++) {
			float a0 = Mth.TWO_PI * i / segments;
			float a1 = Mth.TWO_PI * (i + 1) / segments;
			Vector3f p0 = new Vector3f(CXF + Mth.cos(a0) * r, 0, CZF + Mth.sin(a0) * r);
			Vector3f p1 = new Vector3f(CXF + Mth.cos(a1) * r, 0, CZF + Mth.sin(a1) * r);
			Vector3f in = new Vector3f(-Mth.cos((a0 + a1) / 2), 0, -Mth.sin((a0 + a1) / 2));
			b.polyFacing(java.util.List.of(y(p0, y1), y(p1, y1), y(p1, y0), y(p0, y0)), in);
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
