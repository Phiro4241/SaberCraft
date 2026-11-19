package com.phiro.sabercraft.client.interior;

import static com.phiro.sabercraft.interior.DestroyerInterior.*;

import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.interior.Skin.Kind;
import com.phiro.sabercraft.client.interior.Skin.Surface;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;

/**
 * Phase 15: the inside of the Star Destroyer (see {@code DestroyerInterior}): Imperial panelling
 * on every wall, floor and ceiling (by {@link Skin}), and the furniture of each room, made to fit
 * the blocks and proxies of the plan.
 */
final class DestroyerRooms {
	private DestroyerRooms() {
	}

	/** The blocks of the plan's shell; anything else (storage, stairs, chains) shows as itself. */
	private static final Set<Block> SHELL = Set.of(Blocks.CONCRETE.gray(), Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE,
			Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CONCRETE.yellow(), Blocks.CONCRETE.white(), Blocks.CONCRETE.lightGray());

	/** The radius of the reactor's smooth wall (inside the barrier ring at the catwalk's edge). */
	static final float REACTOR_WALL = (float) DestroyerInterior.REACTOR_WALL;

	static void build(MeshBuilder b) {
		InteriorPlan plan = InteriorPlan.of("star_destroyer");
		VirtualPlot plot = VirtualPlot.of(plan);
		Skin.apply(plot, plan, b, DestroyerRooms::skin);
		bridge(b);
		commandDeck(b);
		hangar(b);
		flightControl(b);
		corridors(b);
		reactor(b);
		hiddenRoom(b);
		// Phase 19: the story's rooms.
		StoryRooms.build(b);
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	/**
	 * Phase 18: the hidden room off the crawl way: a store room nobody remembers, with a cot, a
	 * stack of crates, a desk with a terminal someone has been using, a lamp on a cable.
	 */
	private static void hiddenRoom(MeshBuilder b) {
		float y = HANGAR + 1;
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		// The cot: a metal frame, a blanket, a rolled-up jacket for a pillow.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(14.02F, y, -5.95F, 15.98F, y + 0.33F, -5.05F);
		b.fit(InteriorAtlas.IMP_CHAIR);
		b.box(14.08F, y + 0.33F, -5.9F, 15.92F, y + 0.45F, -5.1F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.colour(0xFFB86A2E).fit(InteriorAtlas.CREW_REBEL_LIMB, 0.0F, 0.0F, 1.0F, 0.6F);
		b.box(14.12F, y + 0.45F, -5.8F, 14.5F, y + 0.58F, -5.2F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.white();
		// Crates, stacked in the corner.
		Props.crate(b, 21, y, -7, 23, y + 2, -5, InteriorAtlas.IMP_CRATE);
		Props.crate(b, 22, y, -5, 23, y + 1, -4, InteriorAtlas.IMP_CRATE);
		// The desk against the south wall, a terminal on it, its screen lit.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(17.02F, y + 0.76F, -1.7F, 18.98F, y + 0.82F, -1.0F, MeshBuilder.ALL);
		b.box(17.1F, y, -1.6F, 17.2F, y + 0.76F, -1.1F);
		b.box(18.8F, y, -1.6F, 18.9F, y + 0.76F, -1.1F);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(17.7F, y + 0.82F, -1.35F, 18.3F, y + 1.22F, -1.25F);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.rect(17.74F, y + 1.19F, -1.24F, 0.52F, 0, 0, 0, -0.34F, 0);
		b.layer(Layer.SOLID).worldLight().anim(0);
		// Datapads on the desk, a cup.
		b.fit(InteriorAtlas.IMP_CONSOLE_TOP);
		b.box(17.2F, y + 0.82F, -1.6F, 17.5F, y + 0.84F, -1.4F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.box(18.5F, y + 0.82F, -1.62F, 18.8F, y + 0.84F, -1.38F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(17.55F, y + 0.82F, -1.65F, 17.63F, y + 0.92F, -1.57F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		// A lamp hanging on its cable.
		b.tile(InteriorAtlas.IMP_BLACK, 1.0F);
		b.box(17.98F, y + 2.4F, -4.02F, 18.02F, y + 3.0F, -3.98F);
		b.layer(Layer.GLOW).fullBright().colour(0xFFE8C890).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.box(17.85F, y + 2.3F, -4.15F, 18.15F, y + 2.4F, -3.85F);
		b.white().layer(Layer.SOLID).worldLight();
	}

	// --- The panelling ---------------------------------------------------------------------------

	private static void skin(MeshBuilder b, Surface s, VirtualPlot plot) {
		Block block = s.block().getBlock();

		if (!SHELL.contains(block)) {
			return;
		}

		int bx = s.x() + s.toward().getStepX();
		int by = s.y() + s.toward().getStepY();
		int bz = s.z() + s.toward().getStepZ();

		// Phase 16: the gallery's way through the reactor wall is lined apart (only where the wall is).
		// Phase 20: so is the way to the lab (its panels and the lining were drawn on the same plane).
		// Phase 25: and the maintenance passage at the walkway's end.
		if (s.kind() != Kind.FLOOR && (GALLERY.contains(s.x(), s.y(), s.z()) || LAB_WAY.contains(s.x(), s.y(), s.z())
				|| SERVICE_WAY.contains(s.x(), s.y(), s.z()))) {
			return;
		}

		// The pedestal of the helm's lever is inside the helm console.
		if (bx == CX && by == DECK + 1 && bz == WINDOW_Z + 2) {
			return;
		}

		// The reactor shaft has a smooth round wall of its own.
		if (s.kind() == Kind.WALL && inReactor(s.x() + 0.5, s.z() + 0.5, REACTOR_R - 0.4) && s.y() >= PIT_FLOOR && s.y() <= REACTOR_TOP) {
			return;
		}

		// Phase 23: the shaft's depth has its own drawing (the blocks round it are only its shell).
		if (inReactor(s.x() + 0.5, s.z() + 0.5, REACTOR_R + 1.5) && s.y() >= PIT_FLOOR - 1 && s.y() < REACTOR_BOTTOM - 1) {
			return;
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);

		// Phase 17: the maintenance ways: grating all round, a dim lamp in the ceiling now and then.
		if (s.in("vent")) {
			boolean lamp = s.kind() == Kind.CEILING && plot.is(s.x(), s.y(), s.z(), Blocks.LIGHT);
			b.fit(s.kind() == Kind.WALL ? InteriorAtlas.IMP_WALL_VENT : lamp ? InteriorAtlas.IMP_CEILING_LIGHT : InteriorAtlas.IMP_FLOOR_GRATE);
			Skin.face(b, s, Skin.EPS);

			if (lamp) {
				b.layer(Layer.GLOW).fullBright().colour(0xFF8090B0).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
				Skin.face(b, s, Skin.EPS * 2);
				b.layer(Layer.SOLID).worldLight().white();
			}

			return;
		}

		switch (s.kind()) {
			case FLOOR -> floor(b, s, block);
			case CEILING -> ceiling(b, s, plot);
			case WALL -> wall(b, s, plot);
		}

		// Phase 25: the core's light on the walkways and platforms round it (added light, pulsing with
		// the core, fading out towards the wall).
		if (s.kind() == Kind.FLOOR && s.in("reactor") && inReactor(s.x() + 0.5, s.z() + 0.5, CORE_LIGHT + 1.0)) {
			Vector3f core = new Vector3f((float) REACTOR_X, s.y(), (float) REACTOR_Z);
			b.layer(Layer.LIGHT).fullBright().anim(InteriorScene.ANIM_PULSE).colour(0xFF3A62B4).fit(InteriorAtlas.WHITE)
					.lightFrom(core, core, 2.2F, (float) CORE_LIGHT);
			Skin.face(b, s, Skin.EPS * 3);
			b.noLight().layer(Layer.SOLID).worldLight().white().anim(0);
		}
	}

	/** Phase 25: how far the reactor core's light reaches over the floors round it. */
	static final double CORE_LIGHT = 11.0;

	static boolean inReactor(double x, double z, double r) {
		double dx = x - REACTOR_X;
		double dz = z - REACTOR_Z;
		return dx * dx + dz * dz < r * r;
	}

	private static void floor(MeshBuilder b, Surface s, Block block) {
		if (block == Blocks.CONCRETE.yellow()) {
			b.fit(InteriorAtlas.IMP_HAZARD);
			Skin.face(b, s, Skin.EPS);
			return;
		}

		if ((block == Blocks.CONCRETE.white() || block == Blocks.CONCRETE.lightGray()) && s.in("hangar")) {
			// The landing circle has a round model of its own.
			b.fit(InteriorAtlas.IMP_FLOOR);
			Skin.face(b, s, Skin.EPS);
			return;
		}

		if (block == Blocks.CONCRETE.white() || block == Blocks.CONCRETE.lightGray()) {
			// Lift pads and the landing circle: lit plates.
			b.fit(InteriorAtlas.IMP_METAL_LIGHT);
			Skin.face(b, s, Skin.EPS);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(block == Blocks.CONCRETE.white() ? 0xFF6F8AB8 : 0xFF404858)
					.fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
			Skin.face(b, s, Skin.EPS * 2);
			b.layer(Layer.SOLID).worldLight().white().anim(0);
			return;
		}

		if (s.in("bridge") || s.in("command_corridor")) {
			boolean walkway = s.x() >= WALK_X0 && s.x() <= WALK_X1 && s.y() == DECK + 1 && s.z() > PLATFORM_Z1 || s.in("command_corridor");

			if (walkway && (s.x() == WALK_X0 || s.x() == WALK_X1 || s.in("command_corridor") && (s.x() == CX - 3 || s.x() == CX + 3))) {
				b.fit(InteriorAtlas.IMP_FLOOR_WALKWAY, s.x() <= CX ? 0.0F : 0.5F, 0.0F, s.x() <= CX ? 0.5F : 1.0F, 1.0F);
			} else if (s.y() == PIT + 1) {
				b.fit(InteriorAtlas.IMP_FLOOR_GRATE);
			} else {
				b.fit(InteriorAtlas.IMP_FLOOR);
			}

			Skin.face(b, s, Skin.EPS);
			return;
		}

		if (s.in("hangar")) {
			b.tile(InteriorAtlas.IMP_FLOOR, 3.0F);
		} else if (s.in("reactor")) {
			b.tile(InteriorAtlas.IMP_FLOOR_GRATE, 1.0F);
		} else {
			b.fit(InteriorAtlas.IMP_FLOOR);
		}

		Skin.face(b, s, Skin.EPS);
	}

	private static void ceiling(MeshBuilder b, Surface s, VirtualPlot plot) {
		boolean lamp = false;

		for (int i = 1; i <= 3 && !lamp; i++) {
			lamp = plot.is(s.x(), s.y() - i, s.z(), Blocks.LIGHT);
		}

		lamp |= plot.is(s.x(), s.y(), s.z(), Blocks.LIGHT);

		if (lamp) {
			b.fit(InteriorAtlas.IMP_CEILING_LIGHT);
			Skin.face(b, s, Skin.EPS);
			b.layer(Layer.GLOW).fullBright().colour(0xFFC8D4F0).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
			Skin.face(b, s, Skin.EPS * 2);
			b.layer(Layer.SOLID).worldLight().white();
			return;
		}

		if (s.in("hangar")) {
			b.tile(InteriorAtlas.IMP_CEILING, 2.0F);
		} else if (s.in("reactor")) {
			b.tile(InteriorAtlas.IMP_FLOOR_GRATE, 1.0F);
		} else {
			b.fit(InteriorAtlas.IMP_CEILING);
		}

		Skin.face(b, s, Skin.EPS);
	}

	private static void wall(MeshBuilder b, Surface s, VirtualPlot plot) {
		int h = s.height();
		boolean hangar = s.in("hangar");
		int strip = hangar ? 4 : 2;
		int along = s.along();

		if (h == 0) {
			b.fit(Math.floorMod(along, hangar ? 3 : 4) == 1 ? InteriorAtlas.IMP_WALL_VENT : InteriorAtlas.IMP_WALL);
			Skin.face(b, s, Skin.EPS);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			Skin.skirting(b, s, plot, 0.16F, 0.07F);
		} else if (h == strip) {
			b.fit(InteriorAtlas.IMP_WALL_STRIP);
			Skin.face(b, s, Skin.EPS);
			// Phase 25: the maintenance chamber behind the reactor: a dim amber strip.
			b.layer(Layer.GLOW).fullBright().colour(s.in("service") ? 0xFFD89040 : 0xFFB0C4EC).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW);
			Skin.face(b, s, Skin.EPS * 2);
			b.layer(Layer.SOLID).worldLight().white();
		} else if (h < strip) {
			b.fit(InteriorAtlas.IMP_WALL);
			Skin.face(b, s, Skin.EPS);
		} else if (hangar && h > strip + 2) {
			b.tile(InteriorAtlas.IMP_HULL_DARK, 2.0F);
			Skin.face(b, s, Skin.EPS);
		} else {
			// Tall panels, two blocks high.
			boolean lower = (h - strip - 1) % 2 == 0;
			b.fit(InteriorAtlas.IMP_WALL_TALL, 0.0F, lower ? 0.5F : 0.0F, 1.0F, lower ? 1.0F : 0.5F);
			Skin.face(b, s, Skin.EPS);
		}

		if (s.top() && s.roomHeight() >= 4) {
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			Skin.ceilingChamfer(b, s, 0.55F);
		}

		Direction[] corner = Skin.corner(plot, s.x(), s.y(), s.z());

		if (corner != null && corner[0] == s.toward()) {
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			Skin.cornerPillar(b, s.x(), s.y(), s.y() + 1, s.z(), corner, 0.2F);
		}
	}

	// --- The bridge ----------------------------------------------------------------------------

	private static void bridge(MeshBuilder b) {
		float wx0 = BRIDGE_X0 + 1;
		float wx1 = BRIDGE_X1;
		float wy0 = PIT + 1;
		float wy1 = BRIDGE_TOP - 1;
		// Phase 19: the glass right where one is stopped (it was half a block behind the barrier
		// that stops you: one stood before an invisible wall in front of the window).
		float wz = WINDOW_Z + 0.97F;
		// The glass, and the frames inside matching the mullions outside.
		b.layer(Layer.GLASS).worldLight().colourAlpha(0x38C0D0FF).fit(InteriorAtlas.IMP_GLASS).twoSided(true);
		b.rect(wx0, wy1, wz, wx1 - wx0, 0, 0, 0, wy0 - wy1, 0);
		b.twoSided(false).white().layer(Layer.SOLID).tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (float x = wx0; x <= wx1 + 0.01F; x += (wx1 - wx0) / 9.0F) {
			b.box(x - 0.22F, wy0, WINDOW_Z + 0.4F, x + 0.22F, wy1, WINDOW_Z + 1.0F + 0.05F, MeshBuilder.ALL & ~MeshBuilder.NORTH & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
		}

		// The bars across sit a little behind the mullions' faces, so the two never share a plane.
		for (float y : new float[] {wy0 + 4.0F, wy0 + 9.0F}) {
			b.box(wx0, y - 0.15F, WINDOW_Z + 0.42F, wx1, y + 0.15F, WINDOW_Z + 1.0F + 0.03F, MeshBuilder.UP | MeshBuilder.DOWN | MeshBuilder.SOUTH);
		}

		b.box(wx0, wy1 - 0.3F, wz - 0.1F, wx1, wy1, WINDOW_Z + 1.25F, MeshBuilder.DOWN | MeshBuilder.SOUTH);

		// The helm: one long console across the front of the walkway, the lever in the middle.
		Props.at(b, CX + 0.5, DECK + 1, WINDOW_Z + 2.5, Props.south());
		Props.desk(b, 5.0F, 0.78F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW,
				InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW, false, 1);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(-0.52F, 0.0F, -0.52F, 0.52F, 1.015F, 0.52F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		// Phase 18: the throttle on it (a drawn lever: a black base, two arms leaning forward, the grip across).
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.2F, 1.015F, -0.16F, 0.2F, 1.085F, 0.16F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.push().translate(0, 1.07F, -0.04F).rotateX(28.0F);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(-0.13F, 0.0F, -0.025F, -0.09F, 0.42F, 0.025F);
		b.box(0.09F, 0.0F, -0.025F, 0.13F, 0.42F, 0.025F);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.17F, 0.38F, -0.04F, 0.17F, 0.47F, 0.04F);
		b.pop();
		b.pop();
		// Phase 18: the steps down into the pits, drawn (they were blackstone stairs).
		for (int z : new int[] {77, 89}) {
			for (int i = 0; i < 4; i++) {
				for (int side = 0; side < 2; side++) {
					float x = side == 0 ? WALK_X0 - 1 - i : WALK_X1 + 1 + i;
					float y = DECK - i;
					float hx0 = side == 0 ? x + 0.5F : x;
					b.tile(InteriorAtlas.IMP_FLOOR_WALKWAY, 1.0F);
					b.box(x, y + 0.5F - 0.002F, z, x + 1, y + 0.5F, z + 2, MeshBuilder.UP);
					b.box(hx0, y + 1.0F - 0.002F, z, hx0 + 0.5F, y + 1.0F, z + 2, MeshBuilder.UP);
					b.tile(InteriorAtlas.IMP_METAL, 1.0F);
					b.box(x, y, z, x + 1, y + 0.5F - 0.002F, z + 2, side == 0 ? MeshBuilder.WEST | MeshBuilder.NORTH | MeshBuilder.SOUTH
							: MeshBuilder.EAST | MeshBuilder.NORTH | MeshBuilder.SOUTH);
					b.box(hx0, y + 0.5F, z, hx0 + 0.5F, y + 1.0F - 0.002F, z + 2, side == 0 ? MeshBuilder.WEST | MeshBuilder.NORTH | MeshBuilder.SOUTH
							: MeshBuilder.EAST | MeshBuilder.NORTH | MeshBuilder.SOUTH);
					// A lit edge on each step.
					b.layer(Layer.GLOW).fullBright().colour(0xFF7FA0C8).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.45F, 1.0F, 0.55F);
					float edge = side == 0 ? hx0 - 0.004F : hx0 + 0.504F;
					b.twoSided(true).rect(edge, y + 0.98F, z + 2, 0, 0, -2, 0, -0.04F, 0);
					b.twoSided(false).white().layer(Layer.SOLID).worldLight();
				}
			}
		}

		// A screen hanging over the helm, facing the walkway.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(CX - 2.0F, BRIDGE_TOP - 4.2F, WINDOW_Z + 2.2F, CX + 3.0F, BRIDGE_TOP - 2.6F, WINDOW_Z + 2.5F);
		b.cylinder(new Vector3f(CX + 0.5F, BRIDGE_TOP - 2.6F, WINDOW_Z + 2.35F), new Vector3f(0, 2.6F, 0), 0.08F, 0.08F, 6, 1.0F, false, false);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW);
		b.rect(CX - 1.85F, BRIDGE_TOP - 2.75F, WINDOW_Z + 2.51F, 4.7F, 0, 0, 0, -1.3F, 0);
		b.layer(Layer.SOLID).worldLight().anim(0);

		// The officers' stations at the ends of the platform: a standing console with a hologram.
		for (int x : new int[] {BRIDGE_X0 + 4, BRIDGE_X1 - 4}) {
			Props.at(b, x + 0.5, DECK + 1, WINDOW_Z + 2.5, Props.south());
			Props.desk(b, 1.0F, 0.95F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW,
					InteriorAtlas.IMP_SCREEN_TEXT_GLOW, true, x);
			b.pop();
		}

		// Railings: along both sides of the walkway (gaps for the steps), and the platform's edge.
		for (int side : new int[] {WALK_X0, WALK_X1}) {
			float x = side == WALK_X0 ? WALK_X0 + 0.85F : WALK_X1 + 0.15F;
			int from = PLATFORM_Z1 + 1;

			for (int z = PLATFORM_Z1 + 1; z <= BRIDGE_BACK; z++) {
				if (z == BRIDGE_BACK || isStep(z)) {
					if (z > from) {
						Props.at(b, x, DECK + 1, 0, Props.west());
						Props.railing(b, from, z, 0.95F, true);
						b.pop();
					}

					from = z + 1;
				}
			}
		}

		for (float[] run : new float[][] {{BRIDGE_X0 + 1, WALK_X0 + 0.85F}, {WALK_X1 + 0.15F, BRIDGE_X1}}) {
			Props.at(b, 0, DECK + 1, PLATFORM_Z1 + 0.15F, Props.south());
			Props.railing(b, run[0], run[1], 0.95F, true);
			b.pop();
		}

		// The crew pits: rows of consoles with a seat behind every other one.
		for (int z : PIT_ROWS) {
			for (int[] run : new int[][] {{BRIDGE_X0 + 2, WALK_X0 - 5}, {WALK_X1 + 5, BRIDGE_X1 - 2}}) {
				float width = run[1] - run[0] + 1;
				Props.at(b, run[0] + width / 2.0F, PIT + 1, z + 0.5, Props.south());
				Props.desk(b, width, 0.72F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW,
						InteriorAtlas.IMP_SCREEN_TEXT_GLOW, true, z);
				b.pop();

				for (int x = run[0]; x <= run[1]; x++) {
					if ((x & 1) == 0) {
						Props.at(b, x + 0.5, PIT + 1, z + 1.5, Props.south());
						Props.chair(b, InteriorAtlas.IMP_CHAIR);
						b.pop();
					}
				}
			}
		}

		// Screens along the back wall of the pits, and the big tactical display over the door.
		b.layer(Layer.SOLID).tile(InteriorAtlas.IMP_METAL, 1.0F);
		float back = BRIDGE_BACK - 0.02F;

		for (int[] run : new int[][] {{BRIDGE_X0 + 2, WALK_X0 - 2}, {WALK_X1 + 2, BRIDGE_X1 - 2}}) {
			for (int x = run[0]; x + 3 <= run[1] + 1; x += 4) {
				b.box(x + 0.1F, PIT + 2.0F, back - 0.12F, x + 3.9F, PIT + 3.8F, back, MeshBuilder.ALL & ~MeshBuilder.SOUTH);
				b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN)
						.fit((x / 4 & 1) == 0 ? InteriorAtlas.IMP_SCREEN_TEXT_GLOW : InteriorAtlas.IMP_SCREEN_RADAR_GLOW, 0.0F, 0.1F, 1.0F, 0.9F);
				b.rect(x + 0.25F, PIT + 3.65F, back - 0.125F, 3.5F, 0, 0, 0, -1.5F, 0);
				b.layer(Layer.SOLID).worldLight().anim(0).tile(InteriorAtlas.IMP_METAL, 1.0F);
			}
		}

		b.box(CX - 5.0F, BRIDGE_TOP - 5.5F, back - 0.2F, CX + 6.0F, BRIDGE_TOP - 1.4F, back, MeshBuilder.ALL & ~MeshBuilder.SOUTH);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW);
		b.rect(CX - 4.8F, BRIDGE_TOP - 1.6F, back - 0.205F, 5.3F, 0, 0, 0, -3.7F, 0);
		b.fit(InteriorAtlas.IMP_SCREEN_RADAR_GLOW);
		b.rect(CX + 0.7F, BRIDGE_TOP - 1.6F, back - 0.205F, 5.1F, 0, 0, 0, -3.7F, 0);
		b.layer(Layer.SOLID).worldLight().anim(0);

		// Ceiling beams along the room.
		b.tile(InteriorAtlas.IMP_METAL, 2.0F);

		for (float x : new float[] {BRIDGE_X0 + 6.5F, BRIDGE_X0 + 13.5F, CX - 2.5F, CX + 3.5F, BRIDGE_X1 - 12.5F, BRIDGE_X1 - 5.5F}) {
			b.box(x - 0.35F, BRIDGE_TOP - 0.7F, WINDOW_Z + 1, x + 0.35F, BRIDGE_TOP, BRIDGE_BACK, MeshBuilder.ALL & ~MeshBuilder.UP);
		}

		for (int z = WINDOW_Z + 6; z < BRIDGE_BACK; z += 8) {
			b.box(BRIDGE_X0 + 1, BRIDGE_TOP - 0.45F, z - 0.25F, BRIDGE_X1, BRIDGE_TOP, z + 0.25F, MeshBuilder.ALL & ~MeshBuilder.UP);
		}

		// The door to the command deck.
		doorFrame(b, Direction.Axis.Z, BRIDGE_BACK, CX - 2, CX + 2, DECK + 1, DECK + 4, false);
	}

	// --- The command deck ----------------------------------------------------------------------

	private static void commandDeck(MeshBuilder b) {
		// Arches along the corridor.
		b.layer(Layer.SOLID).worldLight().white().tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (int z : new int[] {BRIDGE_BACK + 3, BRIDGE_BACK + 6, 111, 114}) {
			arch(b, CX - 3, CX + 4, DECK + 1, COMMAND_TOP, z);
		}

		doorFrame(b, Direction.Axis.X, CX - 4, 106, 108, DECK + 1, DECK + 3, false);
		doorFrame(b, Direction.Axis.X, CX + 4, 106, 108, DECK + 1, DECK + 3, false);

		// The conference room: the long table with its hologram, the chairs, the window.
		int tx = (SIDE_WEST + CX - 4) / 2;
		float x0 = tx - 6 + 0.05F;
		float x1 = tx + 7 - 0.05F;
		float z0 = (float) DestroyerInterior.TABLE_Z0;
		float z1 = (float) DestroyerInterior.TABLE_Z1;
		float r = (z1 - z0) / 2;
		float[] outline = stadium(x0, z0, x1, z1, 8);
		b.tile(InteriorAtlas.IMP_BLACK, 2.0F);
		b.prism(outline, DECK + 1.72F, DECK + 1.0F + (float) DestroyerInterior.TABLE_TOP, true, true);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.prism(stadium(x0 + 1.6F, z0 + 0.8F, x1 - 1.6F, z1 - 0.8F, 6), DECK + 1.0F, DECK + 1.72F, false, false);
		b.layer(Layer.GLOW).fullBright().colour(0xFF5070A0);
		b.fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.prism(stadium(x0 - 0.01F, z0 - 0.01F, x1 + 0.01F, z1 + 0.01F, 8), DECK + 1.8F, DECK + 1.84F, false, false);
		b.colour(0xFF7FB6FF).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.ring(tx + 0.5F, DECK + 1.925F, (z0 + z1) / 2, 0.0F, 0.6F, 12, true);
		b.white().layer(Layer.SOLID).worldLight();
		Props.holoBeam(b, tx + 0.5F, DECK + 1.93F, (z0 + z1) / 2, 0.55F, 1.1F, 0.6F, 0x305AA8FF);

		for (int x = tx - 5; x <= tx + 5; x += 2) {
			Props.at(b, x + 0.5, DECK + 1, 105.5, Props.north());
			Props.chair(b, InteriorAtlas.IMP_CHAIR);
			b.pop();
			Props.at(b, x + 0.5, DECK + 1, 109.5, Props.south());
			Props.chair(b, InteriorAtlas.IMP_CHAIR);
			b.pop();
		}

		sideWindow(b, SIDE_WEST + 0.5F);
		sideWindow(b, SIDE_EAST + 0.5F);

		// Screens on the conference room's back wall.
		screenWall(b, Direction.SOUTH, SIDE_Z0 + 1.02F, SIDE_WEST + 4, CX - 8, DECK + 2.0F, DECK + 4.6F);
		screenWall(b, Direction.NORTH, SIDE_Z1 - 0.02F, SIDE_WEST + 4, CX - 8, DECK + 2.0F, DECK + 4.6F);

		// The sensor room: consoles along the inner wall with screens above, the tactical table.
		for (int z : new int[] {SIDE_Z0 + 2, SIDE_Z0 + 4, SIDE_Z1 - 4, SIDE_Z1 - 2}) {
			Props.at(b, CX + 5.5, DECK + 1, z + 0.5, Props.east());
			Props.desk(b, 1.0F, 0.78F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW,
					InteriorAtlas.IMP_SCREEN_RADAR_GLOW, true, z);
			b.pop();
		}

		for (int z0s : new int[] {SIDE_Z0 + 1, SIDE_Z1 - 5}) {
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			float wx = CX + 5 + 0.02F;
			b.box(wx, DECK + 2.6F, z0s + 0.1F, wx + 0.12F, DECK + 4.8F, z0s + 3.9F, MeshBuilder.ALL & ~MeshBuilder.WEST);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_RADAR_GLOW);
			b.rect(wx + 0.125F, DECK + 4.65F, z0s + 3.75F, 0, 0, -1.7F, 0, -1.9F, 0);
			b.fit(InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW);
			b.rect(wx + 0.125F, DECK + 4.65F, z0s + 1.95F, 0, 0, -1.7F, 0, -1.9F, 0);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}

		int sx = (CX + 4 + SIDE_EAST) / 2 + 4;
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(sx - 2 + 0.05F, DECK + 1, 105.05F, sx + 3 - 0.05F, DECK + 0.98F + 1, 109.95F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		b.fit(InteriorAtlas.IMP_SCREEN);
		b.rect(sx - 2 + 0.05F, DECK + 1.98F, 105.05F, 4.9F, 0, 0, 0, 0, 4.9F);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW);
		b.rect(sx - 2 + 0.25F, DECK + 1.99F, 105.25F, 4.5F, 0, 0, 0, 0, 4.5F);
		b.layer(Layer.SOLID).worldLight().anim(0);
		Props.holoBeam(b, sx + 0.5F, DECK + 2.0F, 107.5F, 2.0F, 2.2F, 0.25F, 0x2068B0FF);

		// The lift at the end of the corridor.
		liftFrame(b, CX - 2, CX + 3, DECK + 1, COMMAND_BACK);
	}

	/** A window in a side room's outer wall, at {@code x}. */
	private static void sideWindow(MeshBuilder b, float x) {
		float z0 = SIDE_Z0 + 2;
		float z1 = SIDE_Z1 - 1;
		float y0 = DECK + 2;
		float y1 = COMMAND_TOP - 1;
		b.layer(Layer.GLASS).worldLight().colourAlpha(0x38C0D0FF).fit(InteriorAtlas.IMP_GLASS).twoSided(true);
		b.rect(x, y1, z0, 0, 0, z1 - z0, 0, y0 - y1, 0);
		b.twoSided(false).white().layer(Layer.SOLID).tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (float z = z0; z <= z1 + 0.01F; z += (z1 - z0) / 4.0F) {
			b.box(x - 0.6F, y0, z - 0.15F, x + 0.6F, y1, z + 0.15F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		}

		b.box(x - 0.56F, y0 + 1.95F, z0, x + 0.56F, y0 + 2.15F, z1, MeshBuilder.UP | MeshBuilder.DOWN | MeshBuilder.EAST | MeshBuilder.WEST);
	}

	/** Screens on a wall facing {@code facing}, at {@code z} (the wall's face), from x0 to x1. */
	private static void screenWall(MeshBuilder b, Direction facing, float z, int x0, int x1, float y0, float y1) {
		float out = facing == Direction.SOUTH ? 1.0F : -1.0F;

		for (int x = x0; x + 2 <= x1; x += 3) {
			b.layer(Layer.SOLID).worldLight().tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(x + 0.1F, y0, z, x + 2.9F, y1, z + out * 0.1F, MeshBuilder.ALL & ~(facing == Direction.SOUTH ? MeshBuilder.NORTH : MeshBuilder.SOUTH));
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN)
					.fit((x & 1) == 0 ? InteriorAtlas.IMP_SCREEN_TEXT_GLOW : InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW);

			if (facing == Direction.SOUTH) {
				b.rect(x + 0.25F, y1 - 0.15F, z + 0.105F, 2.5F, 0, 0, 0, -(y1 - y0 - 0.3F), 0);
			} else {
				b.rect(x + 2.75F, y1 - 0.15F, z - 0.105F, -2.5F, 0, 0, 0, -(y1 - y0 - 0.3F), 0);
			}
		}

		b.layer(Layer.SOLID).worldLight().anim(0);
	}

	/** A stadium (a rectangle with round ends along x) as an outline for {@link MeshBuilder#prism}. */
	private static float[] stadium(float x0, float z0, float x1, float z1, int steps) {
		float r = (z1 - z0) / 2;
		float cz = (z0 + z1) / 2;
		float[] out = new float[(steps + 1) * 4];
		int i = 0;

		for (int k = 0; k <= steps; k++) {
			float a = -90.0F + 180.0F * k / steps;
			out[i++] = x1 - r + Mth.cos(a * Mth.DEG_TO_RAD) * r;
			out[i++] = cz + Mth.sin(a * Mth.DEG_TO_RAD) * r;
		}

		for (int k = 0; k <= steps; k++) {
			float a = 90.0F + 180.0F * k / steps;
			out[i++] = x0 + r + Mth.cos(a * Mth.DEG_TO_RAD) * r;
			out[i++] = cz + Mth.sin(a * Mth.DEG_TO_RAD) * r;
		}

		return out;
	}

	// --- The hangar ----------------------------------------------------------------------------

	static final int[] TIE_X = {HANGAR_X0 + 7, HANGAR_X1 - 7};

	static int[] tieZ() {
		int count = (HANGAR_Z1 - 8 - (HANGAR_Z0 + 8)) / 12 + 1;
		int[] out = new int[count];

		for (int i = 0; i < count; i++) {
			out[i] = HANGAR_Z0 + 8 + i * 12;
		}

		return out;
	}

	private static void hangar(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		float top = HANGAR_TOP + 1;
		// Girders across the ceiling, ribs up the walls.
		b.tile(InteriorAtlas.IMP_METAL, 2.0F);

		for (int z = HANGAR_Z0 + 4; z < HANGAR_Z1; z += 6) {
			b.box(HANGAR_X0 + 1, top - 0.9F, z - 0.3F, HANGAR_X1, top, z + 0.3F, MeshBuilder.ALL & ~MeshBuilder.UP);

			for (float x : new float[] {HANGAR_X0 + 1, HANGAR_X1 - 0.45F}) {
				if (x < CX && (z >= -60 && z <= -35 || z >= -28 && z <= -22) || x > CX && z >= -33 && z <= -28) {
					continue;
				}

				b.box(x, HANGAR + 1, z - 0.3F, x + 0.45F, top - 0.9F, z + 0.3F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
			}
		}

		for (int x = HANGAR_X0 + 5; x < HANGAR_X1; x += 6) {
			b.box(x - 0.3F, HANGAR + 1, HANGAR_Z0 + 1, x + 0.3F, top - 0.9F, HANGAR_Z0 + 1.45F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		}

		// Rails for the TIE racks, and the fighters hanging from them.
		for (int x : TIE_X) {
			b.tile(InteriorAtlas.IMP_METAL, 2.0F);
			b.box(x + 0.1F, top - 1.4F, HANGAR_Z0 + 2, x + 0.9F, top - 0.9F, HANGAR_Z1 - 2, MeshBuilder.ALL & ~MeshBuilder.UP);

			for (int z : tieZ()) {
				tie(b, x + 0.5F, HANGAR_TOP - 4.2F, z + 0.5F);
			}
		}

		// The force field over the opening in the floor.
		b.layer(Layer.HOLO).fullBright().colourAlpha(0x4870A8FF).tile(InteriorAtlas.IMP_FORCE_FIELD_GLOW, 2.0F).anim(InteriorScene.ANIM_SHIMMER)
				.twoSided(true);
		b.rect(FIELD_X0, HANGAR + 0.98F, FIELD_Z0, FIELD_X1 + 1 - FIELD_X0, 0, 0, 0, 0, FIELD_Z1 + 1 - FIELD_Z0);
		b.twoSided(false).white().anim(0).layer(Layer.SOLID).worldLight();
		// Emitters along the field's edges.
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);

		for (int x = FIELD_X0; x <= FIELD_X1; x += 3) {
			for (float z : new float[] {FIELD_Z0 - 0.7F, FIELD_Z1 + 1.3F}) {
				b.box(x + 0.25F, HANGAR + 1, z - 0.2F, x + 0.75F, HANGAR + 1.25F, z + 0.2F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			}
		}

		// The landing circle where the tractor beam sets people down.
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.ring(CX + 0.5F, HANGAR + 1.012F, -28.5F, 0.0F, 3.0F, 24, true);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF6F8AB8).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.ring(CX + 0.5F, HANGAR + 1.018F, -28.5F, 2.55F, 2.85F, 24, true);
		b.ring(CX + 0.5F, HANGAR + 1.018F, -28.5F, 1.2F, 1.4F, 16, true);
		b.white().anim(0).layer(Layer.SOLID).worldLight();

		// Phase 18: the hangers holding the TIE racks up (they were iron chains).
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (int x : TIE_X) {
			for (int z : tieZ()) {
				b.box(x + 0.38F, HANGAR_TOP - 3.0F, z + 0.38F, x + 0.62F, top - 0.9F, z + 0.62F, MeshBuilder.ALL & ~MeshBuilder.UP);
				b.box(x + 0.25F, HANGAR_TOP - 3.0F, z + 0.25F, x + 0.75F, HANGAR_TOP - 2.8F, z + 0.75F);
			}
		}

		// Cargo crates on their proxies (Phase 18: also where barrels and chests stood).
		for (int z = HANGAR_Z0 + 3; z <= HANGAR_Z0 + 9; z += 3) {
			Props.crate(b, HANGAR_X1 - 3, HANGAR + 1, z, HANGAR_X1 - 1, HANGAR + 2, z + 2, InteriorAtlas.IMP_CRATE);
			Props.crate(b, HANGAR_X1 - 2, HANGAR + 2, z, HANGAR_X1 - 1, HANGAR + 3, z + 1, InteriorAtlas.IMP_CRATE);
		}

		for (int x = HANGAR_X0 + 3; x <= HANGAR_X0 + 9; x += 3) {
			Props.crate(b, x, HANGAR + 1, HANGAR_Z0 + 2, x + 2, HANGAR + 2, HANGAR_Z0 + 3, InteriorAtlas.IMP_CRATE);
		}

		Props.crate(b, HANGAR_X1 - 3, HANGAR + 1, -32, HANGAR_X1, HANGAR + 3, -28, InteriorAtlas.IMP_CRATE);
		Props.crate(b, HANGAR_X1 - 2, HANGAR + 3, -31, HANGAR_X1, HANGAR + 4, -29, InteriorAtlas.IMP_CRATE);
		Props.crate(b, HANGAR_X0 + 1, HANGAR + 1, -27, HANGAR_X0 + 4, HANGAR + 3, -23, InteriorAtlas.IMP_CRATE);
		Props.crate(b, HANGAR_X0 + 1, HANGAR + 3, -26, HANGAR_X0 + 3, HANGAR + 4, -24, InteriorAtlas.IMP_CRATE);

		// The flight control room's window, seen from the hangar, and the doors.
		doorFrame(b, Direction.Axis.Z, HANGAR_Z1, CX - 2, CX + 2, HANGAR + 1, HANGAR + 4, true);
		doorFrame(b, Direction.Axis.X, HANGAR_X0, -40, -38, HANGAR + 1, HANGAR + 3, true);
	}

	/**
	 * A TIE fighter as it hangs on a rack: a ball cockpit, two pylons and two hexagonal wings
	 * (original shapes after the look), centre at (x, y, z), wings across x, nose to the north.
	 */
	static void tie(MeshBuilder b, float x, float y, float z) {
		b.layer(Layer.SOLID).worldLight().anim(0);
		int grey = 0xFF9EA4AE;
		// The cockpit ball and its round window at the front.
		b.colour(grey).tile(InteriorAtlas.IMP_HULL_B, 1.0F);
		b.sphere(new Vector3f(x, y, z), 1.25F, 12, 8);
		b.colour(0xFFFFFFFF).fit(InteriorAtlas.IMP_BLACK);
		b.push().translate(x, y, z - 1.18F).rotateX(90.0F);
		b.ring(0, 0, 0, 0.0F, 0.72F, 8, false);
		b.pop();
		b.colour(grey).tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.push().translate(x, y, z - 1.2F).rotateX(90.0F);
		b.ring(0, 0, 0, 0.62F, 0.78F, 8, false);
		b.pop();

		for (int i = 0; i < 4; i++) {
			float a = i * 45.0F * Mth.DEG_TO_RAD;
			b.push().translate(x, y, z - 1.22F).rotateZ(i * 45.0F).translate(-x, -y, -(z - 1.22F));
			// Each strut a little behind the last, so where they cross they do not share a plane.
			b.box(x - 0.03F, y - 0.7F, z - 1.25F + i * 0.004F, x + 0.03F, y + 0.7F, z - 1.21F, MeshBuilder.NORTH);
			b.pop();
		}

		// A hatch on top, the pylons.
		b.cylinder(new Vector3f(x, y + 1.15F, z), new Vector3f(0, 0.2F, 0), 0.45F, 0.4F, 8, 1.0F, false, true);

		for (int s = -1; s <= 1; s += 2) {
			b.cylinder(new Vector3f(x + s * 0.9F, y, z), new Vector3f(s * 2.1F, 0, 0), 0.42F, 0.3F, 8, 1.0F, false, false);
			b.cylinder(new Vector3f(x + s * 1.0F, y, z), new Vector3f(s * 0.5F, 0, 0), 0.55F, 0.55F, 8, 1.0F, false, false);
			wing(b, x + s * 3.05F, y, z, s);
		}

		b.white();
	}

	/** A hexagonal wing standing upright across x at {@code x}, panels on both faces. */
	private static void wing(MeshBuilder b, float x, float y, float z, int side) {
		float r = 3.3F;
		float t = 0.12F;
		Vector3f[] corners = new Vector3f[6];

		for (int i = 0; i < 6; i++) {
			float a = (90.0F + i * 60.0F) * Mth.DEG_TO_RAD;
			corners[i] = new Vector3f(0, Mth.sin(a) * r, Mth.cos(a) * r);
		}

		b.colour(0xFF5A6070).tile(InteriorAtlas.IMP_TIE_PANEL, 1.6F);

		for (float face : new float[] {-t, t}) {
			List<Vector3f> points = new java.util.ArrayList<>();

			for (Vector3f c : corners) {
				points.add(new Vector3f(x + face, y + c.y, z + c.z));
			}

			b.polyFacing(points, new Vector3f(Math.signum(face), 0, 0));
		}

		// The frame round the edge and the spars from the hub to the corners.
		b.colour(0xFF9EA4AE).tile(InteriorAtlas.IMP_METAL, 1.0F);

		// (Phase 16: a band, not cylinders from corner to corner, which overlapped at every corner.)
		float rim = 0.28F;
		float ft = t + 0.05F;

		for (int i = 0; i < 6; i++) {
			Vector3f c0 = corners[i];
			Vector3f c1 = corners[(i + 1) % 6];
			Vector3f i0 = new Vector3f(c0).mul((r - rim) / r);
			Vector3f i1 = new Vector3f(c1).mul((r - rim) / r);

			for (float face : new float[] {-ft, ft}) {
				b.polyFacing(List.of(new Vector3f(x + face, y + c0.y, z + c0.z), new Vector3f(x + face, y + c1.y, z + c1.z),
						new Vector3f(x + face, y + i1.y, z + i1.z), new Vector3f(x + face, y + i0.y, z + i0.z)), new Vector3f(Math.signum(face), 0, 0));
			}

			Vector3f out = new Vector3f(c0).add(c1).normalize();
			b.polyFacing(List.of(new Vector3f(x - ft, y + c0.y, z + c0.z), new Vector3f(x + ft, y + c0.y, z + c0.z), new Vector3f(x + ft, y + c1.y, z + c1.z),
					new Vector3f(x - ft, y + c1.y, z + c1.z)), new Vector3f(0, out.y, out.z));
			// A spar from the hub to the corner, raised on both faces.
			Vector3f along = new Vector3f(c0).normalize();
			Vector3f sw = new Vector3f(0, -along.z, along.y).mul(0.07F);
			// It ends short of the band: its corners would reach into it where the band turns.
			Vector3f e0 = new Vector3f(c0).mul((r - rim - 0.15F) / r);

			for (float face : new float[] {-ft, ft}) {
				b.polyFacing(List.of(new Vector3f(x + face, y + sw.y + along.y * 0.6F, z + sw.z + along.z * 0.6F),
						new Vector3f(x + face, y + sw.y + e0.y, z + sw.z + e0.z), new Vector3f(x + face, y - sw.y + e0.y, z - sw.z + e0.z),
						new Vector3f(x + face, y - sw.y + along.y * 0.6F, z - sw.z + along.z * 0.6F)), new Vector3f(Math.signum(face), 0, 0));
			}
		}

		b.cylinder(new Vector3f(x - 0.25F, y, z), new Vector3f(0.5F, 0, 0), 0.6F, 0.6F, 8, 1.0F, true, true);
	}

	/** The flight control room: consoles under its window, screens along the back wall. */
	private static void flightControl(MeshBuilder b) {
		// The window between the room and the hangar.
		float x = HANGAR_X0 + 0.5F;
		b.layer(Layer.GLASS).worldLight().colourAlpha(0x38C0D0FF).fit(InteriorAtlas.IMP_GLASS).twoSided(true);
		b.rect(x, HANGAR + 7, -57, 0, 0, 21, 0, -4, 0);
		b.twoSided(false).white().layer(Layer.SOLID).tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (int z = -57; z <= -36; z += 3) {
			b.box(x - 0.6F, HANGAR + 3, z - 0.12F, x + 0.6F, HANGAR + 7, z + 0.12F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		}

		b.box(x - 0.6F, HANGAR + 2.8F, -57, x + 0.6F, HANGAR + 3.02F, -36.95F, MeshBuilder.UP | MeshBuilder.EAST | MeshBuilder.WEST);
		Props.at(b, HANGAR_X0 - 0.5, HANGAR + 1, -49.0, Props.west());
		Props.desk(b, 16.0F, 0.78F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW,
				InteriorAtlas.IMP_SCREEN_TEXT_GLOW, false, 3);
		b.pop();

		for (int z = -56; z <= -46; z += 2) {
			Props.at(b, HANGAR_X0 - 1.5, HANGAR + 1, z + 0.5, Props.west());
			Props.chair(b, InteriorAtlas.IMP_CHAIR);
			b.pop();
		}

		// Cabinets with screens along the back wall.
		for (int z = -56; z <= -38; z += 3) {
			Props.at(b, CONTROL_X0 + 1.5, HANGAR + 1, z + 0.5, Props.east());
			b.layer(Layer.SOLID).worldLight().tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(-0.46F, 0.0F, -0.45F, 0.46F, 1.0F, 0.42F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			b.fit(InteriorAtlas.IMP_CONSOLE);
			b.rect(-0.46F, 1.0F, 0.425F, 0.92F, 0, 0, 0, -1.0F, 0);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_CONSOLE_GLOW);
			b.rect(-0.46F, 1.0F, 0.43F, 0.92F, 0, 0, 0, -1.0F, 0);
			b.fit((z & 1) == 0 ? InteriorAtlas.IMP_SCREEN_RADAR_GLOW : InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW);
			b.rect(-0.9F, 3.3F, -0.42F, 1.8F, 0, 0, 0, -1.6F, 0);
			b.layer(Layer.SOLID).worldLight().anim(0).tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(-0.98F, 1.62F, -0.5F, 0.98F, 3.38F, -0.43F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
			b.pop();
		}
	}

	// --- Corridors, doors and lifts ------------------------------------------------------------

	private static void corridors(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white().tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (int z = HANGAR_Z1 + 2; z <= LOBBY_Z1 - 2; z += 3) {
			// (Phase 23: nor in front of the detention block's door, a post stood in its way.)
			int door = com.phiro.sabercraft.interior.DestroyerInterior.DETENTION_DOOR_Z0;

			if (z >= LOBBY_Z0 - 1 && z <= LOBBY_Z0 + 3 || z >= door - 1 && z <= door + 2) {
				continue;
			}

			arch(b, CX - 3, CX + 4, HANGAR + 1, HANGAR + 6, z);
		}

		// Pipes along the upper edges of the corridor.
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);

		for (float x : new float[] {CX - 3 + 0.3F, CX + 4 - 0.3F}) {
			b.pipe(0.16F, 8, x, HANGAR + 5.45F, HANGAR_Z1 + 0.6F, x, HANGAR + 5.45F, LOBBY_Z1 + 0.4F);
			b.pipe(0.1F, 6, x + (x < CX ? 0.25F : -0.25F), HANGAR + 5.7F, HANGAR_Z1 + 0.6F, x + (x < CX ? 0.25F : -0.25F), HANGAR + 5.7F,
					LOBBY_Z1 + 0.4F);
		}

		doorFrame(b, Direction.Axis.X, CX + 4, LOBBY_Z0, LOBBY_Z0 + 2, HANGAR + 1, HANGAR + 3, true);
		// Phase 22: the detention block's door gets a frame like the others, on both sides.
		doorFrame(b, Direction.Axis.X, CX - 4, com.phiro.sabercraft.interior.DestroyerInterior.DETENTION_DOOR_Z0,
				com.phiro.sabercraft.interior.DestroyerInterior.DETENTION_DOOR_Z0 + 1, HANGAR + 1, HANGAR + 3, false);
		detentionEntry(b);
		// Phase 23: the lift in its bay behind the lobby (its frame inside the bay's walls).
		liftFrame(b, CX - 1.7F, CX + 2.7F, HANGAR + 1, com.phiro.sabercraft.interior.DestroyerInterior.LIFT_Z1 + 1);
		liftBayMouth(b);

		// Phase 17: a rack of rifles on the west wall (in its two proxy cells).
		float rx = CX - 3;
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(rx, HANGAR + 1, -7.0F, rx + 0.55F, HANGAR + 1.12F, -5.0F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN);
		b.box(rx, HANGAR + 2.75F, -7.0F, rx + 0.55F, HANGAR + 2.85F, -5.0F, MeshBuilder.ALL & ~MeshBuilder.WEST);
		b.box(rx, HANGAR + 1.12F, -7.0F, rx + 0.5F, HANGAR + 2.75F, -6.9F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
		b.box(rx, HANGAR + 1.12F, -5.1F, rx + 0.5F, HANGAR + 2.75F, -5.0F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
		b.fit(InteriorAtlas.IMP_BLACK);

		for (int i = 0; i < 6; i++) {
			float z = -6.65F + i * 0.3F;
			b.box(rx + 0.12F, HANGAR + 1.14F, z - 0.05F, rx + 0.3F, HANGAR + 2.25F, z + 0.05F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			b.box(rx + 0.16F, HANGAR + 2.25F, z - 0.025F, rx + 0.24F, HANGAR + 2.6F, z + 0.025F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		}

		// The security desk on the east wall, facing the corridor. Phase 26: between two of the
		// corridor's frames (at z -8.5 its end stood in a frame's post: the riser and its screen
		// reached into the post and flickered there).
		Props.at(b, CX + 3.5, HANGAR + 1, com.phiro.sabercraft.interior.DestroyerInterior.SECURITY_DESK_Z, Props.west());
		Props.desk(b, 1.9F, 0.82F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW,
				InteriorAtlas.IMP_SCREEN_RADAR_GLOW, true, 5);
		b.pop();

		// Screens along the command corridor's walls.
		for (int side = 0; side < 2; side++) {
			float x = side == 0 ? CX - 3 + 0.04F : CX + 4 - 0.04F;

			for (int z0 : new int[] {101, 110}) {
				b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_METAL, 1.0F);
				float d = side == 0 ? 0.06F : -0.06F;
				b.box(Math.min(x, x + d), DECK + 2.0F, z0, Math.max(x, x + d), DECK + 3.3F, z0 + 2.0F,
						MeshBuilder.ALL & ~(side == 0 ? MeshBuilder.WEST : MeshBuilder.EAST));
				b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN)
						.fit(side == 0 ? InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW : InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
				float fx = x + d + (side == 0 ? 0.004F : -0.004F);

				if (side == 1) {
					b.rect(fx, DECK + 3.2F, z0 + 0.1F, 0, 0, 1.8F, 0, -1.1F, 0);
				} else {
					b.rect(fx, DECK + 3.2F, z0 + 1.9F, 0, 0, -1.8F, 0, -1.1F, 0);
				}
			}
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0);
	}

	/** A frame round the inside of a corridor at z: posts on both walls and a beam under the ceiling. */
	private static void arch(MeshBuilder b, float x0, float x1, float y0, float y1, int z) {
		float d = 0.22F;
		// The posts end under the beam (overlapping, their faces lay in the beam's and flickered).
		b.box(x0, y0, z, x0 + d, y1 - d, z + 0.5F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		b.box(x1 - d, y0, z, x1, y1 - d, z + 0.5F, MeshBuilder.ALL & ~MeshBuilder.EAST & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
		b.box(x0, y1 - d, z, x1, y1, z + 0.5F, MeshBuilder.ALL & ~MeshBuilder.UP);
		b.layer(Layer.GLOW).fullBright().colour(0xFF90A8D8).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.rect(x0 + 0.5F, y1 - d - 0.008F, z + 0.35F, x1 - x0 - 1.0F, 0, 0, 0, 0, -0.2F);
		b.white().layer(Layer.SOLID).worldLight();
	}

	/**
	 * A frame round a doorway through a wall one block thick: the wall is at {@code wall} on the
	 * axis, the opening from a0 to a1 (blocks, both ends in) and y0 to y1. Hazard stripes if asked.
	 */
	static void doorFrame(MeshBuilder b, Direction.Axis axis, int wall, int a0, int a1, int y0, int y1, boolean hazard) {
		b.layer(Layer.SOLID).worldLight().white();
		float w = 0.28F;
		float d = 0.12F;

		for (float face : new float[] {wall - d, wall + 1}) {
			float f0 = face;
			float f1 = face + d;

			// The faces towards the opening stand a hair inside it: where the wall is thicker than one
			// block they would otherwise lie on the faces of the blocks lining the passage.
			float in = 0.016F;
			// No face against the wall itself.
			int mask = MeshBuilder.ALL & ~(axis == Direction.Axis.Z ? (face < wall ? MeshBuilder.SOUTH : MeshBuilder.NORTH)
					: (face < wall ? MeshBuilder.EAST : MeshBuilder.WEST));

			if (axis == Direction.Axis.Z) {
				b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
				b.box(a0 - w, y0, f0, a0 + in, y1 + 1 + w, f1, mask);
				b.box(a1 + 1 - in, y0, f0, a1 + 1 + w, y1 + 1 + w, f1, mask);
				b.box(a0 + in, y1 + 1 - in, f0, a1 + 1 - in, y1 + 1 + w, f1, mask);
			} else {
				b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
				b.box(f0, y0, a0 - w, f1, y1 + 1 + w, a0 + in, mask);
				b.box(f0, y0, a1 + 1 - in, f1, y1 + 1 + w, a1 + 1 + w, mask);
				b.box(f0, y1 + 1 - in, a0 + in, f1, y1 + 1 + w, a1 + 1 - in, mask);
			}
		}

		// The threshold.
		b.fit(hazard ? InteriorAtlas.IMP_HAZARD : InteriorAtlas.IMP_FLOOR_WALKWAY);

		if (axis == Direction.Axis.Z) {
			b.rect(a0, y0 + 0.012F, wall, a1 + 1 - a0, 0, 0, 0, 0, 1);
		} else {
			b.rect(wall, y0 + 0.012F, a1 + 1, 0, 0, -(a1 + 1 - a0), 1, 0, 0);
		}
	}

	/** A lift: a lit frame round the end of a corridor (x0..x1, the end wall at z). */
	/**
	 * Phase 23: the mouth of the lift's bay in the lobby's end wall: a frame round it and a light
	 * strip over it, so the lift reads as its own place behind the lobby.
	 */
	private static void liftBayMouth(MeshBuilder b) {
		float z = com.phiro.sabercraft.interior.DestroyerInterior.LIFT_Z0;
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(CX - 2.3F, HANGAR + 1, z - 0.12F, CX - 2.0F, HANGAR + 5.3F, z + 0.02F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.box(CX + 3.0F, HANGAR + 1, z - 0.12F, CX + 3.3F, HANGAR + 5.3F, z + 0.02F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.box(CX - 2.0F, HANGAR + 5.0F, z - 0.12F, CX + 3.0F, HANGAR + 5.3F, z + 0.02F, MeshBuilder.ALL);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF6F90D0).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.rect(CX + 2.9F, HANGAR + 5.24F, z - 0.13F, -4.8F, 0, 0, 0, -0.14F, 0);
		b.white().layer(Layer.SOLID).worldLight().anim(0);
	}

	/**
	 * Phase 23: the detention block's entrance in the lobby, its own place now the lift is gone from
	 * beside it: a red security lamp over the door and the lock panel beside it.
	 */
	private static void detentionEntry(MeshBuilder b) {
		float wall = CX - 3;
		float z0 = com.phiro.sabercraft.interior.DestroyerInterior.DETENTION_DOOR_Z0;
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(wall, HANGAR + 4.28F, z0 - 0.45F, wall + 0.1F, HANGAR + 4.5F, z0 + 2.45F, MeshBuilder.ALL & ~MeshBuilder.WEST);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFFD03028).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.box(wall + 0.1F, HANGAR + 4.32F, z0 - 0.35F, wall + 0.13F, HANGAR + 4.46F, z0 + 2.35F, MeshBuilder.ALL & ~MeshBuilder.WEST);
		// The lock panel south of the door, at hand height, its screen lit.
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(wall, HANGAR + 1.25F, z0 + 2.35F, wall + 0.08F, HANGAR + 2.05F, z0 + 2.85F, MeshBuilder.ALL & ~MeshBuilder.WEST);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.box(wall + 0.08F, HANGAR + 1.55F, z0 + 2.42F, wall + 0.09F, HANGAR + 1.95F, z0 + 2.78F, MeshBuilder.EAST);
		b.white().layer(Layer.SOLID).worldLight().anim(0);
	}

	private static void liftFrame(MeshBuilder b, float x0, float x1, float y0, float z) {
		b.layer(Layer.SOLID).worldLight().white().tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(x0 - 0.3F, y0, z - 2.2F, x0, y0 + 4.0F, z - 0.02F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.UP);
		b.box(x1, y0, z - 2.2F, x1 + 0.3F, y0 + 4.0F, z - 0.02F, MeshBuilder.ALL & ~MeshBuilder.EAST & ~MeshBuilder.UP);
		b.box(x0 - 0.3F, y0 + 4.0F, z - 2.2F, x1 + 0.3F, y0 + 4.3F, z - 0.02F, MeshBuilder.ALL & ~MeshBuilder.UP);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF6F90D0).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F,
				0.58F);
		b.rect(x0 + 0.01F, y0 + 3.98F, z - 0.02F, x1 - x0 - 0.02F, 0, 0, 0, 0, -2.18F);
		b.white().layer(Layer.SOLID).worldLight().anim(0);
		b.fit(InteriorAtlas.IMP_DOOR);
		b.rect(x1, y0 + 4.0F, z - 0.03F, -(x1 - x0), 0, 0, 0, -4.0F, 0);
	}

	// --- The reactor ---------------------------------------------------------------------------


	/** Phase 19: whether a cell (plot space) is crossed by the reactor shaft's round wall. */
	static boolean reactorWallCell(int x, int y, int z) {
		double far = 0.0;
		double near = Double.MAX_VALUE;

		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				double d = Math.hypot(x + i - REACTOR_X, z + j - REACTOR_Z);
				far = Math.max(far, d);
				near = Math.min(near, d);
			}
		}

		return y >= REACTOR_BOTTOM && y < REACTOR_TOP && far > REACTOR_WALL && near < REACTOR_WALL + 1.0;
	}

	/**
	 * The reactor shaft. Phase 23 (after Phiro's wish and the films' reactor shafts): deep and open,
	 * the round wall going down far under the platforms into the dark, the core a glowing column
	 * rising out of a pool of light in the depth with a heavy ring round it, four converters down
	 * there sending arcs into the ring, haze over the depth; on two levels small platforms at the
	 * wall and narrow steel bridges without railings (see {@link #gantry}).
	 */
	private static void reactor(MeshBuilder b) {
		float cx = (float) REACTOR_X;
		float cz = (float) REACTOR_Z;
		float r = REACTOR_WALL;
		float bottom = PIT_DRAWN;
		float work = REACTOR_BOTTOM;
		float top = REACTOR_TOP;
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		// The round wall, in tall panels, open where the gallery comes in.
		// Phase 16: open exactly between the gallery's sides, the gallery lined through the wall.
		// Phase 20: and the way to the lab on the shaft floor (the server always left it open, but the
		// wall was drawn across it: one walked through a wall that looked closed to find the door).
		// Phase 23: down into the depth, darker the further down.
		// Phase 25: and the maintenance passage at the end of the walkway along the north-east wall.
		List<InteriorPlan.Doorway> ways = List.of(DestroyerInterior.GALLERY, DestroyerInterior.LAB_WAY, DestroyerInterior.SERVICE_WAY);
		RoundWalls.wall(b, cx, cz, r, (int) bottom, (int) top, true, 0.0F, 360.0F, 48, ways,
				(pen, row, index) -> pen.colour(depthShade(bottom + row)).tile(InteriorAtlas.IMP_WALL_TALL, 2.0F));
		b.white();
		// Phase 19: the gallery's sides go on into the wall's cells, up to the curve (by its east side
		// the curve passes just beyond the doorway's cells: one looked into the wall there).
		RoundWalls.linings(b, ways, DestroyerInterior::openReactor, DestroyerRooms::reactorWallCell, (door, x, z) -> true,
				() -> b.tile(InteriorAtlas.IMP_METAL, 1.0F), () -> b.tile(InteriorAtlas.IMP_METAL, 1.0F));
		b.tile(InteriorAtlas.IMP_WALL_TALL, 2.0F);

		// Phase 25: the core's light on the wall round the working level (added light, pulsing with the
		// core), brightest level with the walkways, fading out a few blocks up and down (the wall
		// above and the depth below stay dark).
		Vector3f glowAt = new Vector3f(cx, work + 1.5F, cz);
		b.layer(Layer.LIGHT).fullBright().anim(InteriorScene.ANIM_PULSE).lightFrom(glowAt, glowAt, r + 0.2F, r + 3.0F);
		// The wall itself drawn again, pulled 0.06 in towards the middle: cut into the same pieces as
		// the wall (also beside the doorways), each lying parallel in front of its piece.
		float pull = (r - 0.06F) / r;
		b.push().translate(cx, 0.0, cz).scale(pull, 1.0F, pull).translate(-cx, 0.0, -cz);
		RoundWalls.wall(b, cx, cz, r, (int) work - 6, (int) work + 10, true, 0.0F, 360.0F, 48, ways,
				(pen, row, index) -> pen.colour(0xFF1C3666).fit(InteriorAtlas.WHITE));
		b.pop();
		b.noLight().white().anim(0).layer(Layer.SOLID).worldLight();

		// Ribs and conduits up the wall (Phase 18: where the server has them collide).
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);

		for (float a : RIB_ANGLES) {
			float x = cx + Mth.cos(a * Mth.DEG_TO_RAD) * (float) RIB_R;
			float z = cz + Mth.sin(a * Mth.DEG_TO_RAD) * (float) RIB_R;
			b.cylinder(new Vector3f(x, bottom, z), new Vector3f(0, top - bottom, 0), 0.22F, 0.22F, 6, 6.0F, false, false);
		}

		// Light bands round the wall: up top, at the deck level, under the platforms and down the
		// depth, ever fainter (one sees how far it goes down).
		int segments = 48;
		float[][] bands = {{HANGAR + 6.0F, 0xFF7AA0E0}, {top - 4.0F, 0xFF7AA0E0}, {work - 1.4F, 0xFF6A90D8}, {work - 8.0F, 0xFF5274BC},
				{work - 15.0F, 0xFF3E5CA0}, {work - 22.0F, 0xFF2E4888}};

		for (float[] band : bands) {
			float y = band[0];
			b.layer(Layer.GLOW).fullBright().colour((int) (long) band[1]).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.4F, 1.0F, 0.6F);
			// Phase 20: not across a doorway (the low band ran across the way to the lab).
			List<float[]> gaps = RoundWalls.gaps(cx, cz, r, (int) Math.floor(y), ways);

			for (int i = 0; i < segments; i++) {
				float a0 = 360.0F * i / segments;
				float a1 = 360.0F * (i + 1) / segments;

				for (float[] gap : gaps) {
					if (a1 > gap[0] && a0 < gap[1]) {
						// Only up to the doorway's side (the band ends at the edge of the opening).
						if (a0 < gap[0]) {
							b.arcWall(cx, cz, r - 0.02F, a0, gap[0], y, y + 0.25F, 1, true);
						}

						if (a1 > gap[1]) {
							b.arcWall(cx, cz, r - 0.02F, gap[1], a1, y, y + 0.25F, 1, true);
						}

						a1 = a0;
						break;
					}
				}

				if (a1 > a0) {
					b.arcWall(cx, cz, r - 0.02F, a0, a1, y, y + 0.25F, 1, true);
				}
			}
		}

		// The depth: far down the core's light in a pool; a haze over it (one does not see the end).
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_PULSE).colour(0xFF6F98E8).tile(InteriorAtlas.IMP_REACTOR_GLOW, 3.0F);
		b.ring(cx, bottom + 0.05F, cz, 0.0F, r - 0.05F, 48, true);
		b.layer(Layer.HOLO).fullBright().anim(0).colourAlpha(0x2C3C64B0).fit(InteriorAtlas.IMP_REACTOR_GLOW).twoSided(true);

		for (float y : new float[] {work - 24.0F, work - 17.0F, work - 10.0F}) {
			b.ring(cx, y, cz, 3.05F, r - 0.05F, 48, true);
		}

		b.twoSided(false).white().layer(Layer.SOLID).worldLight().anim(0);

		// The core: a column out of the depth to the top, collars all the way, a wide foot far down,
		// a cone at the top; a heavy ring round it under the working level.
		b.tile(InteriorAtlas.IMP_METAL, 2.0F);
		b.cylinder(new Vector3f(cx, bottom, cz), new Vector3f(0, 6.0F, 0), 6.0F, 2.8F, 24, 4.0F, false, true);
		b.cylinder(new Vector3f(cx, top - 5.0F, cz), new Vector3f(0, 5.0F, 0), 2.8F, 5.5F, 20, 4.0F, true, false);

		for (float y = bottom + 8.0F; y < top - 6.0F; y += 4.0F) {
			if (Math.abs(y - (work - 6.5F)) > 2.0F) {
				b.cylinder(new Vector3f(cx, y, cz), new Vector3f(0, 0.7F, 0), 2.95F, 2.95F, 16, 2.0F, true, true);
			}
		}

		float ring = work - 7.4F;
		b.cylinder(new Vector3f(cx, ring, cz), new Vector3f(0, 0.4F, 0), 3.2F, 4.6F, 24, 2.0F, true, false);
		b.cylinder(new Vector3f(cx, ring + 0.4F, cz), new Vector3f(0, 1.4F, 0), 4.6F, 4.6F, 24, 4.0F, false, false);
		b.cylinder(new Vector3f(cx, ring + 1.8F, cz), new Vector3f(0, 0.4F, 0), 4.6F, 3.2F, 24, 2.0F, false, true);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_PULSE).tile(InteriorAtlas.IMP_REACTOR_GLOW, 3.0F);
		b.cylinder(new Vector3f(cx, bottom + 6.0F, cz), new Vector3f(0, top - bottom - 11.0F, 0), 2.2F, 2.2F, 16, 3.0F, false, false);
		b.colour(0xFF9CC8FF).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.cylinder(new Vector3f(cx, ring + 0.85F, cz), new Vector3f(0, 0.5F, 0), 4.63F, 4.63F, 24, 1.0F, false, false);
		b.white().anim(0).layer(Layer.SOLID).worldLight();

		// Conduits from the wall to the core near the top.
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);

		for (int i = 0; i < 4; i++) {
			float a = (45.0F + i * 90.0F) * Mth.DEG_TO_RAD;
			float dx = Mth.cos(a);
			float dz = Mth.sin(a);
			b.pipe(0.45F, 8, cx + dx * r, top - 3.0F, cz + dz * r, cx + dx * 6.0F, top - 3.0F, cz + dz * 6.0F, cx + dx * 3.2F, top - 6.0F,
					cz + dz * 3.2F);
		}

		// The energy converters in the depth: tall machines with glowing bands, a pipe each into the
		// core's ring (the arcs between them, see animate).
		for (double angle : CONVERTER_ANGLES) {
			float a = (float) Math.toRadians(angle);
			float x = cx + Mth.cos(a) * (float) CONVERTER_R;
			float z = cz + Mth.sin(a) * (float) CONVERTER_R;
			float s = (float) CONVERTER_SIZE - 0.05F;
			float cap = CONVERTER_TOP;
			b.tile(InteriorAtlas.IMP_METAL, 2.0F);
			b.cylinder(new Vector3f(x, bottom, z), new Vector3f(0, 1.0F, 0), s + 0.3F, s, 12, 2.0F, false, true);
			b.tile(InteriorAtlas.IMP_PIPE, 1.0F);
			b.cylinder(new Vector3f(x, bottom + 1.0F, z), new Vector3f(0, cap - bottom - 2.0F, 0), s - 0.2F, s - 0.2F, 12, 3.0F, false, false);
			b.tile(InteriorAtlas.IMP_METAL, 2.0F);
			b.cylinder(new Vector3f(x, cap - 1.0F, z), new Vector3f(0, 1.0F, 0), s, s - 0.35F, 12, 2.0F, false, true);
			b.tile(InteriorAtlas.IMP_PIPE, 1.0F);
			b.pipe(0.3F, 8, x - Mth.cos(a) * (s - 0.2F), cap - 2.2F, z - Mth.sin(a) * (s - 0.2F), cx + Mth.cos(a) * 6.2F, cap - 2.2F, cz + Mth.sin(a) * 6.2F,
					cx + Mth.cos(a) * 4.7F, ring + 0.9F, cz + Mth.sin(a) * 4.7F);
			b.layer(Layer.GLOW).fullBright().colour(0xFF6F9CE0).anim(InteriorScene.ANIM_PULSE).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);

			for (float band = bottom + 2.5F; band < cap - 1.5F; band += 2.6F) {
				b.cylinder(new Vector3f(x, band, z), new Vector3f(0, 0.18F, 0), s - 0.17F, s - 0.17F, 12, 1.0F, false, false);
			}

			b.white().anim(0).layer(Layer.SOLID).worldLight();
		}

		reactorWorks(b);
	}

	/** Phase 23: where the converters in the reactor's depth end (well under the platforms). */
	static final float CONVERTER_TOP = REACTOR_BOTTOM - 4.0F;

	/** Phase 23: how dark the reactor's wall is drawn at that height (lighter up where the platforms are). */
	static int depthShade(float y) {
		float f = Mth.clamp(1.0F - (REACTOR_BOTTOM - 1.0F - y) / 26.0F * 0.72F, 0.28F, 1.0F);
		int g = Math.round(255 * f);
		int bl = Math.min(255, g + Math.round(18 * (1.0F - f)));
		return 0xFF000000 | g << 16 | g << 8 | bl;
	}

	/**
	 * Phase 23: the reactor's steelwork (all but the bridge that can break, which the story's
	 * rooms draw while it stands), the lift, the consoles, struts under the platforms.
	 */
	private static void reactorWorks(MeshBuilder b) {
		float cx = (float) REACTOR_X;
		float cz = (float) REACTOR_Z;

		for (Gantry g : GANTRIES) {
			if (!COLLAPSE.contains(g)) {
				gantry(b, g, 0.0F, 0.0F);
			}
		}

		// Struts from the wall to the platforms' undersides (the bridges hang between them).
		b.layer(Layer.SOLID).worldLight().white().anim(0).tile(InteriorAtlas.IMP_PIPE, 1.0F);

		for (Gantry g : GANTRIES) {
			if (g.bridge() || g.id().equals("desk")) {
				continue;
			}

			Vec3 c = g.centre();
			double dx = c.x - REACTOR_X;
			double dz = c.z - REACTOR_Z;
			double len = Math.max(0.1, Math.hypot(dx, dz));
			double ux = dx / len;
			double uz = dz / len;
			double half = Math.min(g.x1() + 1 - g.x0(), g.z1() + 1 - g.z0()) * 0.3;

			for (int k = -1; k <= 1; k += 2) {
				float sx = (float) (c.x - uz * half * k);
				float sz = (float) (c.z + ux * half * k);
				double sl = Math.hypot(sx - REACTOR_X, sz - REACTOR_Z);
				float ex = (float) (REACTOR_X + (sx - REACTOR_X) / sl * (REACTOR_WALL - 0.1));
				float ez = (float) (REACTOR_Z + (sz - REACTOR_Z) / sl * (REACTOR_WALL - 0.1));
				b.pipe(0.11F, 6, sx, g.y() - 0.45F, sz, ex, g.y() - 3.6F, ez);
			}
		}

		// The consoles on the platforms, facing the core.
		int[] glow = {InteriorAtlas.IMP_SCREEN_RADAR_GLOW, InteriorAtlas.IMP_SCREEN_TEXT_GLOW, InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW};
		float[] side = {Props.west(), Props.north(), Props.south()};

		for (int i = 0; i < REACTOR_CONSOLES.size(); i++) {
			net.minecraft.core.BlockPos c = REACTOR_CONSOLES.get(i);
			Props.at(b, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, side[i]);
			Props.desk(b, 1.0F, 0.78F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW, glow[i], true, i + 7);
			b.pop();
		}

		// Phase 20: the console up on the deck level, for the technician there: its screen faces him.
		Props.at(b, CATWALK_DESK.getX() + 0.5, CATWALK_DESK.getY(), CATWALK_DESK.getZ() + 0.5, Props.north());
		Props.desk(b, 1.0F, 0.78F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW,
				InteriorAtlas.IMP_SCREEN_TEXT_GLOW, true, 11);
		b.pop();
		b.layer(Layer.SOLID).worldLight().white().anim(0);

		// The lift pads: a lit plate with a ring, below and on the deck level right above.
		float floor = REACTOR_BOTTOM;

		for (float y : new float[] {floor, HANGAR + 1}) {
			float x0 = (float) REACTOR_LIFT.minX;
			float z0 = (float) REACTOR_LIFT.minZ;
			b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
			b.box(x0 + 0.05F, y, z0 + 0.05F, x0 + 1.95F, y + 0.04F, z0 + 1.95F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF6F8AB8).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
			b.ring(x0 + 1.0F, y + 0.045F, z0 + 1.0F, 0.6F, 0.75F, 16, true);
			b.white().anim(0).layer(Layer.SOLID).worldLight();
		}

		// The lift's shaft: four posts from the platform up to the one above.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		float lx0 = (float) REACTOR_LIFT.minX;
		float lz0 = (float) REACTOR_LIFT.minZ;
		// Phase 18: as thick as they collide (LIFT_POST either side of the corner).
		float p = (float) LIFT_POST;

		for (float[] post : new float[][] {{lx0 - p, lz0 - p}, {lx0 + 2.0F - p, lz0 - p}, {lx0 - p, lz0 + 2.0F - p}, {lx0 + 2.0F - p, lz0 + 2.0F - p}}) {
			b.box(post[0], floor, post[1], post[0] + 2.0F * p, HANGAR - 0.01F, post[1] + 2.0F * p, MeshBuilder.ALL & ~MeshBuilder.DOWN & ~MeshBuilder.UP);
		}
	}

	/** Phase 23: whether one walks on the reactor's steelwork in that cell at that level (people standing at y). */
	static boolean onGantry(int x, int z, int y) {
		for (Gantry g : GANTRIES) {
			if (g.y() == y && g.contains(x + 0.5, z + 0.5)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 23: one piece of the reactor's steelwork: grating on top, a steel deck, beams under it
	 * (a frame under a platform, two girders and cross ribs under a narrow bridge), no railing:
	 * along every open edge (the drop beside it) a hazard band, and small lamps under it. It
	 * trembles while the story's bridge groans ({@code tremble} 0..1).
	 */
	static void gantry(MeshBuilder b, Gantry g, float tremble, float seconds) {
		float y = g.y();
		float x0 = g.x0();
		float z0 = g.z0();
		float x1 = g.x1() + 1;
		float z1 = g.z1() + 1;
		float thick = g.bridge() ? 0.14F : 0.3F;
		b.layer(Layer.SOLID).worldLight().white().anim(0);

		if (tremble > 0.0F) {
			b.push().translate(0.0, Mth.sin(seconds * 43.0F) * 0.03F * tremble - 0.04F * tremble, 0.0);
		}

		b.tile(InteriorAtlas.IMP_FLOOR_GRATE, 1.0F);
		b.box(x0, y - 0.02F, z0, x1, y, z1, MeshBuilder.UP);

		// Phase 25: the core's light on the grating (pulsing with it, fading out towards the wall),
		// block by block so that it fades over the platform.
		Vector3f core = new Vector3f((float) REACTOR_X, y, (float) REACTOR_Z);
		b.layer(Layer.LIGHT).fullBright().anim(InteriorScene.ANIM_PULSE).colour(0xFF3A62B4).fit(InteriorAtlas.WHITE)
				.lightFrom(core, core, 2.2F, (float) CORE_LIGHT);

		for (int x = g.x0(); x <= g.x1(); x++) {
			for (int z = g.z0(); z <= g.z1(); z++) {
				if (inReactor(x + 0.5, z + 0.5, CORE_LIGHT + 1.0)) {
					// (Over the hazard bands at the edges, which lie 0.006 over the grating.)
					b.box(x, y, z, x + 1, y + 0.012F, z + 1, MeshBuilder.UP);
				}
			}
		}

		b.noLight().layer(Layer.SOLID).worldLight().white().anim(0);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x0, y - thick, z0, x1, y - 0.002F, z1, MeshBuilder.ALL & ~MeshBuilder.UP);
		b.colour(0xFF9098A8);
		float under = y - thick;

		if (g.bridge()) {
			boolean alongX = x1 - x0 > z1 - z0;

			if (alongX) {
				b.box(x0, under - 0.3F, z0 + 0.04F, x1, under, z0 + 0.16F, MeshBuilder.ALL);
				b.box(x0, under - 0.3F, z1 - 0.16F, x1, under, z1 - 0.04F, MeshBuilder.ALL);

				for (float x = x0 + 0.5F; x < x1; x += 1.0F) {
					b.box(x - 0.05F, under - 0.2F, z0 + 0.16F, x + 0.05F, under, z1 - 0.16F, MeshBuilder.ALL & ~MeshBuilder.UP);
				}
			} else {
				b.box(x0 + 0.04F, under - 0.3F, z0, x0 + 0.16F, under, z1, MeshBuilder.ALL);
				b.box(x1 - 0.16F, under - 0.3F, z0, x1 - 0.04F, under, z1, MeshBuilder.ALL);

				for (float z = z0 + 0.5F; z < z1; z += 1.0F) {
					b.box(x0 + 0.16F, under - 0.2F, z - 0.05F, x1 - 0.16F, under, z + 0.05F, MeshBuilder.ALL & ~MeshBuilder.UP);
				}
			}
		} else {
			b.box(x0, under - 0.4F, z0, x1, under, z0 + 0.18F, MeshBuilder.ALL & ~MeshBuilder.UP);
			b.box(x0, under - 0.4F, z1 - 0.18F, x1, under, z1, MeshBuilder.ALL & ~MeshBuilder.UP);
			b.box(x0, under - 0.4F, z0 + 0.18F, x0 + 0.18F, under, z1 - 0.18F, MeshBuilder.ALL & ~MeshBuilder.UP);
			b.box(x1 - 0.18F, under - 0.4F, z0 + 0.18F, x1, under, z1 - 0.18F, MeshBuilder.ALL & ~MeshBuilder.UP);

			for (float x = x0 + 2.0F; x < x1 - 0.5F; x += 2.0F) {
				b.box(x - 0.08F, under - 0.3F, z0 + 0.18F, x + 0.08F, under, z1 - 0.18F, MeshBuilder.ALL & ~MeshBuilder.UP);
			}
		}

		b.white();

		// The open edges: a hazard band on top, a lamp under the edge every other block.
		int level = g.y();

		for (int x = g.x0(); x <= g.x1(); x++) {
			for (int z = g.z0(); z <= g.z1(); z++) {
				int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

				for (int[] d : sides) {
					int nx = x + d[0];
					int nz = z + d[1];

					if (g.contains(nx + 0.5, nz + 0.5) || onGantry(nx, nz, level) || !inReactor(nx + 0.5, nz + 0.5, REACTOR_WALL)
							|| !inReactor(x + 0.5, z + 0.5, REACTOR_WALL - 0.3)) {
						continue;
					}

					float ex0 = d[0] == 0 ? x : d[0] < 0 ? x : x + 0.86F;
					float ex1 = d[0] == 0 ? x + 1 : d[0] < 0 ? x + 0.14F : x + 1;
					float ez0 = d[1] == 0 ? z : d[1] < 0 ? z : z + 0.86F;
					float ez1 = d[1] == 0 ? z + 1 : d[1] < 0 ? z + 0.14F : z + 1;
					b.tile(InteriorAtlas.IMP_HAZARD, 1.0F);
					b.box(ex0, y, ez0, ex1, y + 0.006F, ez1, MeshBuilder.UP);

					if ((x + z) % 2 == 0) {
						float lx = d[0] == 0 ? x + 0.5F : d[0] < 0 ? x + 0.05F : x + 0.95F;
						float lz = d[1] == 0 ? z + 0.5F : d[1] < 0 ? z + 0.05F : z + 0.95F;
						b.layer(Layer.GLOW).fullBright().colour(g.bridge() ? 0xFF8CB8FF : 0xFFFFB45A).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
						b.box(lx - 0.07F, under - 0.06F, lz - 0.07F, lx + 0.07F, under, lz + 0.07F, MeshBuilder.ALL & ~MeshBuilder.UP);
						b.white().layer(Layer.SOLID).worldLight();
					}
				}
			}
		}

		if (tremble > 0.0F) {
			b.pop();
		}
	}

	// --- Moving parts ----------------------------------------------------------------------------

	static void animate(MeshBuilder b, float seconds) {
		// Energy rings round the reactor core, turning and tilting (Phase 23: down into the depth too).
		float cx = (float) REACTOR_X;
		float cz = (float) REACTOR_Z;
		b.layer(Layer.HOLO).fullBright().colourAlpha(0x90A8D8FF).fit(InteriorAtlas.IMP_REACTOR_GLOW).twoSided(true).anim(0);

		for (int i = 0; i < 6; i++) {
			float y = REACTOR_BOTTOM - 20.0F + i * 6.4F;

			if (Math.abs(y - (REACTOR_BOTTOM - 6.5F)) < 2.0F) {
				// (the core's ring is there)
				y += 3.0F;
			}

			b.push().translate(cx, y + 0.6F * Mth.sin(seconds * 0.8F + i), cz).rotateY(seconds * (20.0F + i * 9.0F) * (i % 2 == 0 ? 1 : -1))
					.rotateX(12.0F * Mth.sin(seconds * 0.5F + i * 2.0F));
			b.ring(0, 0, 0, 3.6F, 4.0F, 24, true);
			b.pop();
		}

		// Phase 18: the core's state over the south console (a column of rings rising), and the
		// converters' collars turning (Phase 23: down in the depth).
		b.colourAlpha(0x4870C0FF).fit(InteriorAtlas.IMP_HOLO);

		for (int i = 0; i < 4; i++) {
			float rise = (seconds * 0.35F + i * 0.25F) % 1.0F;
			b.ring(40.5F, REACTOR_BOTTOM + 1.3F + rise * 1.1F, 36.5F, 0.18F + rise * 0.12F, 0.24F + rise * 0.12F, 12, true);
		}

		b.colourAlpha(0x5878B8FF).fit(InteriorAtlas.IMP_REACTOR_GLOW);

		for (double angle : CONVERTER_ANGLES) {
			float a = (float) Math.toRadians(angle);
			float x = cx + Mth.cos(a) * (float) CONVERTER_R;
			float z = cz + Mth.sin(a) * (float) CONVERTER_R;
			b.push().translate(x, CONVERTER_TOP - 1.6F, z).rotateY(seconds * 45.0F + (float) angle);
			b.ring(0, 0, 0, (float) CONVERTER_SIZE + 0.05F, (float) CONVERTER_SIZE + 0.2F, 6, true);
			b.pop();
		}

		// Phase 23: arcs of energy from the converters into the core's ring, now here, now there.
		b.twoSided(false).layer(Layer.GLOW).fullBright().colour(0xFFC4DCFF).fit(InteriorAtlas.IMP_REACTOR_GLOW);
		float ringY = REACTOR_BOTTOM - 6.55F;

		for (int i = 0; i < CONVERTER_ANGLES.length; i++) {
			if (Mth.sin(seconds * 2.3F + i * 1.9F) + Mth.sin(seconds * 5.1F + i * 0.7F) < 0.6F) {
				continue;
			}

			float a = (float) Math.toRadians(CONVERTER_ANGLES[i]);
			float sx = cx + Mth.cos(a) * (float) (CONVERTER_R - CONVERTER_SIZE + 0.3);
			float sz = cz + Mth.sin(a) * (float) (CONVERTER_R - CONVERTER_SIZE + 0.3);
			float ex = cx + Mth.cos(a) * 4.7F;
			float ez = cz + Mth.sin(a) * 4.7F;
			int seed = (int) (seconds * 14.0F) * 31 + i * 7;
			float[] points = new float[7 * 3];

			for (int k = 0; k < 7; k++) {
				float t = k / 6.0F;
				float jitter = k == 0 || k == 6 ? 0.0F : 0.45F;
				float jx = ((seed * (k + 3) * 2654435761L >>> 8) % 1000) / 1000.0F - 0.5F;
				float jy = ((seed * (k + 5) * 40503L >>> 4) % 1000) / 1000.0F - 0.5F;
				points[k * 3] = Mth.lerp(t, sx, ex) - Mth.sin(a) * jx * jitter;
				points[k * 3 + 1] = Mth.lerp(t, CONVERTER_TOP - 0.6F, ringY) + jy * jitter + Mth.sin(t * Mth.PI) * 0.8F;
				points[k * 3 + 2] = Mth.lerp(t, sz, ez) + Mth.cos(a) * jx * jitter;
			}

			b.pipe(0.045F, 4, points);
		}

		b.layer(Layer.HOLO).twoSided(true);

		// The conference hologram: a slowly turning planet with a ring and a small ship circling it.
		int tx = (SIDE_WEST + CX - 4) / 2;
		float hx = tx + 0.5F;
		float hy = DECK + 3.4F;
		float hz = 107.5F;
		// Phase 25: its light on the table under it (added light, fading out round the beam).
		float tableLight = (float) (DestroyerInterior.TABLE_Z1 - DestroyerInterior.TABLE_Z0) / 2 - 0.15F;
		tableLight(b, hx, DECK + 1.93F, hz, 0.5F, tableLight, 0xFF1A3466);
		b.layer(Layer.HOLO).twoSided(true);
		b.colourAlpha(0x3868B8FF).fit(InteriorAtlas.IMP_HOLO);
		b.push().translate(hx, hy, hz).rotateY(seconds * 15.0F);
		b.sphere(new Vector3f(0, 0, 0), 0.8F, 12, 8);
		b.rotateX(20.0F);
		b.ring(0, 0, 0, 1.05F, 1.25F, 20, true);
		b.pop();
		b.push().translate(hx, hy, hz).rotateY(-seconds * 40.0F).translate(1.6F, 0.2F, 0).rotateY(90.0F);
		b.scale(0.06F, 0.06F, 0.06F);
		miniDestroyer(b);
		b.pop();

		// The tactical table: a small fleet in formation over the map.
		int sx = (CX + 4 + SIDE_EAST) / 2 + 4;
		// Phase 25: the map's light on the table round it.
		tableLight(b, sx + 0.5F, DECK + 2.0F, 107.5F, 1.2F, 1.95F, 0xFF173A30);
		b.layer(Layer.HOLO).twoSided(true);
		b.colourAlpha(0x70A0C8FF);

		for (int i = 0; i < 3; i++) {
			b.push().translate(sx + 0.5F - 1.2F + i * 1.2F, DECK + 2.6F + 0.08F * Mth.sin(seconds * 1.3F + i), 107.5F + (i == 1 ? -0.8F : 0.4F))
					.rotateY(180.0F).scale(0.035F, 0.035F, 0.035F);
			miniDestroyer(b);
			b.pop();
		}

		// A scanning sweep over the tactical table.
		b.colourAlpha(0x5060FF90).fit(InteriorAtlas.IMP_HOLO);
		b.push().translate(sx + 0.5F, DECK + 2.04F, 107.5F).rotateY(seconds * 90.0F);
		b.rect(0, 0, -0.05F, 2.2F, 0, 0, 0, 0, 0.1F);
		b.pop();

		b.twoSided(false).white().layer(Layer.SOLID).worldLight();
	}

	/** Phase 25: a hologram's light on the table it stands on: a disc of added light, fading out. */
	private static void tableLight(MeshBuilder b, float x, float y, float z, float inner, float radius, int colour) {
		Vector3f at = new Vector3f(x, y, z);
		b.twoSided(false).layer(Layer.LIGHT).fullBright().anim(InteriorScene.ANIM_SHIMMER).colour(colour).fit(InteriorAtlas.WHITE)
				.lightFrom(at, at, inner, radius);

		for (int k = 0; k < 4; k++) {
			b.ring(x, y + Skin.EPS * 3, z, radius * k / 4, radius * (k + 1) / 4, 32, true);
		}

		b.noLight().anim(0).white();
	}

	/** A small wedge (for holograms), nose at -z, about 40 units long. */
	private static void miniDestroyer(MeshBuilder b) {
		float l = 20.0F;
		float w = 12.0F;
		b.polyFacing(List.of(new Vector3f(0, 0, -l), new Vector3f(w, 0, l), new Vector3f(0, 4, l)), new Vector3f(1, 1, 0));
		b.polyFacing(List.of(new Vector3f(0, 0, -l), new Vector3f(-w, 0, l), new Vector3f(0, 4, l)), new Vector3f(-1, 1, 0));
		b.polyFacing(List.of(new Vector3f(0, 0, -l), new Vector3f(w, 0, l), new Vector3f(0, -3, l)), new Vector3f(1, -1, 0));
		b.polyFacing(List.of(new Vector3f(0, 0, -l), new Vector3f(-w, 0, l), new Vector3f(0, -3, l)), new Vector3f(-1, -1, 0));
		b.box(-3, 4, 8, 3, 9, 14);
	}
}
