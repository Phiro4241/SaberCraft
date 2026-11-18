package com.phiro.sabercraft.client.interior;

import static com.phiro.sabercraft.interior.DestroyerInterior.*;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.story.StoryClient;

/**
 * Phase 19: the furniture of the story's rooms in the Star Destroyer, made to fit the plan's
 * blocks and proxies ({@code DestroyerInterior.storyRooms}): the dark maintenance room with its
 * old machine, crates, workbench, a tangle of cable and Kestrel's bird scratched beside the
 * hatch, the airlock's round outer hatch; the armory's lockers, weapon racks and the
 * quartermaster's desk; the detention block's cells behind force fields, the cell console, the
 * clamp that holds a droid; the lab under the reactor with the tracker, the containment field and
 * the holo projector. What the story changes (a field switched off, the card taken, the saber
 * gone, the tracker failing, Ilyra's message) is drawn every frame by {@link #animate}.
 */
final class StoryRooms {
	/** Where people stand on the hangar deck and at the reactor's bottom. */
	static final float H = HANGAR + 1;
	static final float R = REACTOR_BOTTOM;

	private StoryRooms() {
	}

	static void build(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		maintenance(b);
		airlock(b);
		armory(b);
		detention(b);
		lab(b);
		reactorValve(b);
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	// --- The maintenance room and the airlock ------------------------------------------------------

	private static void maintenance(MeshBuilder b) {
		// The old machine in the middle: a grimy housing with gauges, a tank on top, pipes to the ceiling.
		float x0 = (float) MACHINERY.minX;
		float x1 = (float) MACHINERY.maxX;
		float z0 = (float) MACHINERY.minZ;
		float z1 = (float) MACHINERY.maxZ;
		float top = H + 1.72F;
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x0 + 0.02F, top, z0 + 0.02F, x1 - 0.02F, top + 0.06F, z1 - 0.02F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		panels(b, x0 + 0.06F, H, z0 + 0.06F, x1 - 0.06F, top, z1 - 0.06F, InteriorAtlas.STORY_MACHINE);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x0, H, z0, x1, H + 0.12F, z1, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);
		b.cylinder(new Vector3f(x0 + 0.4F, top + 0.36F, (z0 + z1) / 2.0F), new Vector3f(x1 - x0 - 0.8F, 0, 0), 0.32F, 0.32F, 12, 2.0F, true, true);
		b.pipe(0.09F, 6, x0 + 1.0F, top + 0.06F, z0 + 0.6F, x0 + 1.0F, H + 3.0F, z0 + 0.6F);
		b.pipe(0.09F, 6, x1 - 1.2F, top + 0.06F, z1 - 0.5F, x1 - 1.2F, H + 2.6F, z1 - 0.5F, x1 - 1.2F, H + 2.6F, z1 + 1.5F, x1 - 1.2F, H + 3.0F, z1 + 1.5F);
		b.pipe(0.12F, 6, x1 - 0.3F, H + 0.5F, z0 + 0.8F, x1 + 1.2F, H + 0.5F, z0 + 0.8F, x1 + 1.2F, H + 0.05F, z0 + 0.8F);
		// A red lamp on the housing, blinking slowly.
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_PULSE).colour(0xFFFF4030).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.box(x1 - 0.5F, top + 0.06F, z1 - 0.5F, x1 - 0.3F, top + 0.16F, z1 - 0.3F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.layer(Layer.SOLID).worldLight().white().anim(0);

		// Crates as cover.
		Props.crate(b, 3, H, -7, 5, H + 1, -6, InteriorAtlas.IMP_CRATE);
		Props.crate(b, 3, H + 1, -7, 4, H + 2, -6, InteriorAtlas.IMP_CRATE);
		Props.crate(b, 9, H, 4, 11, H + 1, 6, InteriorAtlas.IMP_CRATE);
		Props.crate(b, 10, H + 1, 5, 11, H + 2, 6, InteriorAtlas.IMP_CRATE);
		Props.crate(b, 2, H, 3, 3, H + 1, 5, InteriorAtlas.IMP_CRATE);

		// The workbench against the east wall (the datapad lies on it, drawn by the story).
		float bx = 11.36F;
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(bx, H + 0.78F, 0.04F, 11.98F, H + 0.85F, 1.96F);

		for (float z : new float[] {0.1F, 1.82F}) {
			b.box(bx + 0.04F, H, z, bx + 0.12F, H + 0.78F, z + 0.08F);
			b.box(11.86F, H, z, 11.94F, H + 0.78F, z + 0.08F);
		}

		b.fit(InteriorAtlas.IMP_CONSOLE);
		b.box(bx + 0.1F, H + 0.4F, 0.2F, 11.98F, H + 0.74F, 0.9F);
		// Tools: a spanner, a lamp, a coil of wire.
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(11.5F, H + 0.85F, 1.3F, 11.56F, H + 0.88F, 1.75F);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(11.7F, H + 0.85F, 1.45F, 11.85F, H + 1.02F, 1.6F);
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);
		b.ring(11.62F, H + 0.86F, 0.55F, 0.07F, 0.16F, 10, true);

		// The cable tangle on the west wall: a junction box torn open, cables spilling to the floor.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(2.0F, H + 0.35F, -5.0F, 2.28F, H + 1.0F, -4.05F, MeshBuilder.ALL & ~MeshBuilder.WEST);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(2.28F, H + 0.45F, -4.9F, 2.3F, H + 0.9F, -4.15F, MeshBuilder.EAST);
		float[][] cables = {{2.3F, H + 0.8F, -4.3F, 3.6F, H + 0.04F, -6.2F}, {2.3F, H + 0.7F, -4.6F, 2.5F, H + 0.04F, -6.8F},
				{2.3F, H + 0.6F, -4.5F, 3.2F, H + 0.04F, -5.4F}, {2.3F, H + 0.85F, -4.8F, 2.9F, H + 0.4F, -6.4F}};

		for (int i = 0; i < cables.length; i++) {
			float[] c = cables[i];
			b.tile(i % 2 == 0 ? InteriorAtlas.IMP_BLACK : InteriorAtlas.IMP_PIPE, 1.0F);
			b.cable(new Vector3f(c[0], c[1], c[2]), new Vector3f(c[3], c[4] + 0.04F, c[5]), 0.18F, 0.035F);
		}

		// Pipes along the walls under the ceiling.
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);
		b.pipe(0.11F, 6, 11.8F, H + 2.62F, -8.0F, 11.8F, H + 2.62F, 7.0F);
		b.pipe(0.08F, 6, 11.8F, H + 2.35F, -8.0F, 11.8F, H + 2.35F, 7.0F);
		b.pipe(0.1F, 6, 2.2F, H + 2.65F, -8.0F, 2.2F, H + 2.65F, 7.0F);

		// Kestrel's sign: a bird scratched into a panel beside the hatch.
		b.fit(InteriorAtlas.STORY_BIRD_SIGN);
		b.rect(8.6F, H + 1.8F, -7.985F, 1.0F, 0, 0, 0, -0.9F, 0);
	}

	private static void airlock(MeshBuilder b) {
		// The round outer hatch the Falcon docks at, in the south wall.
		float cx = 6.5F;
		float cy = H + 1.45F;
		float wall = (float) AIRLOCK.maxZ - 0.015F;
		b.push().translate(cx, cy, wall).rotateX(-90.0F);
		b.fit(InteriorAtlas.IMP_DOOR);
		b.ring(0, 0, 0, 0.0F, 1.15F, 24, true);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.translate(0, 0.01F, 0);
		b.ring(0, 0, 0, 1.15F, 1.42F, 24, true);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);

		// The locking bolts round the hatch.
		for (int i = 0; i < 8; i++) {
			float a = i * Mth.TWO_PI / 8.0F;
			float x = Mth.cos(a) * 1.0F;
			float z = Mth.sin(a) * 1.0F;
			b.box(x - 0.07F, 0.0F, z - 0.07F, x + 0.07F, 0.05F, z + 0.07F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		}

		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF60E080).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.translate(0, 0.01F, 0);
		b.ring(0, 0, 0, 1.43F, 1.5F, 24, true);
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		b.pop();
		// Hazard stripes on the floor before it.
		b.fit(InteriorAtlas.IMP_HAZARD);

		for (float x = 4.0F; x < 9.0F; x += 1.0F) {
			b.rect(x, H + 0.006F, (float) AIRLOCK.maxZ - 1.0F, 1.0F, 0, 0, 0, 0, 0.5F);
		}

		// Grab handles beside it.
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);

		for (float x : new float[] {cx - 1.85F, cx + 1.75F}) {
			b.box(x, H + 0.9F, wall - 0.12F, x + 0.1F, H + 1.9F, wall - 0.05F);
		}
	}

	// --- The armory ----------------------------------------------------------------------------------

	private static void armory(MeshBuilder b) {
		// The lockers along the east wall: two blocks high, a door each.
		float face = ARMORY_LOCKER_X + 0.3F;

		for (int z : ARMORY_LOCKERS) {
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(face, H, z + 0.02F, ARMORY_LOCKER_X + 1.0F, H + 2.0F, z + 0.98F, MeshBuilder.UP | MeshBuilder.NORTH | MeshBuilder.SOUTH);
			b.fit(InteriorAtlas.STORY_LOCKER);
			b.rect(face - 0.002F, H + 2.0F, z + 0.04F, 0, 0, 0.92F, 0, -2.0F, 0);
		}

		// Helmets on the shelf above.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(face - 0.15F, H + 2.0F, ARMORY_LOCKERS[0], ARMORY_LOCKER_X + 1.0F, H + 2.06F, ARMORY_LOCKERS[ARMORY_LOCKERS.length - 1] + 1.0F);

		for (int i = 0; i < ARMORY_LOCKERS.length; i += 2) {
			float z = ARMORY_LOCKERS[i] + 0.5F;
			float x = face + 0.18F;
			b.fit(InteriorAtlas.CREW_TROOPER_HELMET_SIDE);
			b.box(x - 0.17F, H + 2.06F, z - 0.17F, x + 0.17F, H + 2.4F, z + 0.17F, MeshBuilder.ALL & ~MeshBuilder.WEST & ~MeshBuilder.DOWN);
			b.fit(InteriorAtlas.CREW_TROOPER_HELMET);
			b.box(x - 0.17F, H + 2.06F, z - 0.17F, x + 0.17F, H + 2.4F, z + 0.17F, MeshBuilder.WEST);
		}

		// The weapon racks along the north wall: a back board, a tray, rifles standing in a row.
		float back = (float) ARMORY.minZ;
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(47.0F, H, back, 54.0F, H + 1.9F, back + 0.12F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
		b.box(47.0F, H, back + 0.12F, 54.0F, H + 0.16F, back + 0.45F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.box(47.0F, H + 1.1F, back + 0.12F, 54.0F, H + 1.15F, back + 0.4F);

		for (float x = 47.35F; x < 53.8F; x += 0.52F) {
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(x - 0.035F, H + 0.16F, back + 0.2F, x + 0.035F, H + 1.45F, back + 0.29F);
			b.box(x - 0.03F, H + 0.55F, back + 0.29F, x + 0.03F, H + 0.66F, back + 0.38F);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(x - 0.022F, H + 1.45F, back + 0.22F, x + 0.022F, H + 1.72F, back + 0.27F);
		}

		// The quartermaster's desk: a flat top, a front panel, a terminal facing the room.
		float dz0 = DESK_Z + 0.15F;
		float dz1 = DESK_Z + 0.85F;
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(47.02F, H + 0.79F, dz0, 49.98F, H + 0.85F, dz1);
		b.fit(InteriorAtlas.IMP_CONSOLE);
		b.box(47.06F, H, dz1 - 0.08F, 49.94F, H + 0.79F, dz1 - 0.02F, MeshBuilder.SOUTH | MeshBuilder.NORTH);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(47.04F, H, dz0, 47.12F, H + 0.79F, dz1);
		b.box(49.88F, H, dz0, 49.96F, H + 0.79F, dz1);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(47.6F, H + 0.85F, dz0 + 0.12F, 48.4F, H + 1.35F, dz0 + 0.2F);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.rect(47.64F, H + 1.31F, dz0 + 0.21F, 0.72F, 0, 0, 0, -0.42F, 0);
		b.layer(Layer.SOLID).worldLight().anim(0);
		b.fit(InteriorAtlas.IMP_CONSOLE_TOP);
		b.box(48.5F, H + 0.85F, dz0 + 0.3F, 48.95F, H + 0.87F, dz0 + 0.55F, MeshBuilder.ALL & ~MeshBuilder.DOWN);

		// Ammunition crates in the middle.
		Props.crate(b, 50, H, -10, 52, H + 1, -8, InteriorAtlas.IMP_CRATE);
		Props.crate(b, 51, H + 1, -10, 52, H + 2, -9, InteriorAtlas.IMP_CRATE);

		// The security panel on the corridor's east wall (the story opens the armory with it).
		float wall = CX + 4;
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(wall - 0.22F, H + 0.55F, -9.4F, wall, H + 1.45F, -7.6F, MeshBuilder.ALL & ~MeshBuilder.EAST);
		b.fit(InteriorAtlas.IMP_SCREEN);
		b.rect(wall - 0.228F, H + 1.38F, -9.3F, 0, 0, 0.9F, 0, -0.5F, 0);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.rect(wall - 0.236F, H + 1.34F, -9.26F, 0, 0, 0.82F, 0, -0.42F, 0);
		b.fit(InteriorAtlas.IMP_CONSOLE_TOP_GLOW);
		b.rect(wall - 0.236F, H + 0.84F, -9.0F, 0, 0, 0.6F, 0, -0.22F, 0);
		b.layer(Layer.SOLID).worldLight().anim(0);
	}

	// --- The detention block -----------------------------------------------------------------------

	private static void detention(MeshBuilder b) {
		for (int[] cell : CELLS) {
			float x0 = cell[0];
			float x1 = cell[1] + 1;
			// A bench along the back wall.
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(x0 + 0.08F, H + 0.4F, -0.98F, x1 - 0.08F, H + 0.48F, -0.42F);
			b.box(x0 + 0.15F, H, -0.9F, x0 + 0.25F, H + 0.4F, -0.5F);
			b.box(x1 - 0.25F, H, -0.9F, x1 - 0.15F, H + 0.4F, -0.5F);
			// The field's emitters, above and below.
			b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
			b.box(x0, H, CELL_FRONT + 0.32F, x1, H + 0.12F, CELL_FRONT + 0.68F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			b.box(x0, H + 2.86F, CELL_FRONT + 0.32F, x1, H + 3.0F, CELL_FRONT + 0.68F, MeshBuilder.ALL & ~MeshBuilder.UP);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF70A8E8).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
			b.rect(x0, H + 0.13F, CELL_FRONT + 0.42F, x1 - x0, 0, 0, 0, 0, 0.16F);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}

		// The cell console in the corner, facing north.
		Props.at(b, CELL_CONSOLE.getX() + 0.5, H, CELL_CONSOLE.getZ() + 0.5, Props.north());
		Props.desk(b, 1.0F, 0.82F, InteriorAtlas.IMP_CONSOLE, InteriorAtlas.IMP_CONSOLE_TOP, InteriorAtlas.IMP_CONSOLE_TOP_GLOW, InteriorAtlas.IMP_SCREEN_TEXT_GLOW,
				true, 3);
		b.pop();
		// The clamp beside it that holds a droid: a plate on the floor, an arm from the wall.
		float cx = CELL_CONSOLE.getX() + 1.5F;
		float wall = (float) DETENTION.maxZ;
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(cx - 0.42F, H, wall - 0.5F, cx + 0.42F, H + 0.2F, wall, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(cx - 0.08F, H + 0.2F, wall - 0.22F, cx + 0.08F, H + 1.1F, wall);
		b.box(cx - 0.12F, H + 1.0F, wall - 0.3F, cx + 0.12F, H + 1.12F, wall);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_BLINK).colour(0xFFFF4030).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.box(cx - 0.03F, H + 1.12F, wall - 0.25F, cx + 0.03F, H + 1.16F, wall - 0.19F, MeshBuilder.UP);
		b.layer(Layer.SOLID).worldLight().anim(0);

		// A guard's terminal on a column in the corridor.
		b.fit(InteriorAtlas.IMP_CONSOLE);
		b.box(30.12F, H, 6.12F, 30.88F, H + 1.95F, 6.88F);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_RADAR_GLOW);
		b.rect(30.2F, H + 1.75F, 6.89F, 0.6F, 0, 0, 0, -0.5F, 0);
		b.fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.rect(30.8F, H + 1.75F, 6.11F, -0.6F, 0, 0, 0, -0.5F, 0);
		b.layer(Layer.SOLID).worldLight().anim(0);
		// The guards' bench.
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(33.05F, H + 0.42F, 9.08F, 34.95F, H + 0.5F, 9.92F);
		b.box(33.15F, H, 9.2F, 33.25F, H + 0.42F, 9.8F);
		b.box(34.75F, H, 9.2F, 34.85F, H + 0.42F, 9.8F);
	}

	// --- The lab -------------------------------------------------------------------------------------

	private static void lab(MeshBuilder b) {
		labDoor(b);
		float tx = (float) TRACKER_X;
		float tz = (float) TRACKER_Z;
		float r = (float) TRACKER_R;
		// The tracker: a wide base, a ribbed body, a crown of emitters (its lights move, see animate).
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.cylinder(new Vector3f(tx, R, tz), new Vector3f(0, 0.35F, 0), r - 0.02F, r - 0.1F, 24, 6.0F, false, true);
		b.fit(InteriorAtlas.IMP_CONSOLE);
		b.cylinder(new Vector3f(tx, R + 0.35F, tz), new Vector3f(0, 1.5F, 0), r - 0.3F, r - 0.45F, 24, 8.0F, false, false);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.cylinder(new Vector3f(tx, R + 1.85F, tz), new Vector3f(0, 0.2F, 0), r - 0.2F, r - 0.2F, 24, 6.0F, true, true);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.cylinder(new Vector3f(tx, R + 2.05F, tz), new Vector3f(0, 0.5F, 0), 1.0F, 0.3F, 16, 3.0F, false, true);

		for (int i = 0; i < 6; i++) {
			float a = i * Mth.TWO_PI / 6.0F;
			float x = tx + Mth.cos(a) * (r - 0.55F);
			float z = tz + Mth.sin(a) * (r - 0.55F);
			b.box(x - 0.1F, R + 2.05F, z - 0.1F, x + 0.1F, R + 2.55F, z + 0.1F);
		}

		// Cables over the floor from the consoles to the tracker.
		b.tile(InteriorAtlas.IMP_BLACK, 1.0F);

		for (int z = 42; z <= 48; z += 2) {
			b.pipe(0.05F, 5, 51.0F, R + 0.05F, z + 0.5F, 48.5F, R + 0.05F, z + 0.5F, tx + r - 0.1F, R + 0.05F, tz + (z - 45) * 0.25F);
		}

		// The consoles along the east wall: tall cabinets with screens.
		for (int z = 42; z <= 48; z += 2) {
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(51.04F, R, z + 0.04F, 52.0F, R + 1.85F, z + 0.96F, MeshBuilder.ALL & ~MeshBuilder.EAST & ~MeshBuilder.DOWN);
			b.fit(InteriorAtlas.IMP_SCREEN);
			b.rect(51.03F, R + 1.75F, z + 0.08F, 0, 0, 0.84F, 0, -0.7F, 0);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN)
					.fit(z % 4 == 0 ? InteriorAtlas.IMP_SCREEN_TACTICAL_GLOW : InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
			b.rect(51.02F, R + 1.7F, z + 0.12F, 0, 0, 0.76F, 0, -0.62F, 0);
			b.fit(InteriorAtlas.IMP_CONSOLE_TOP_GLOW);
			b.rect(51.03F, R + 0.95F, z + 0.12F, 0, 0, 0.76F, 0, -0.3F, 0);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}

		// The containment field's pedestal and emitter ring.
		float sx = SABER_FIELD.getX() + 0.5F;
		float sz = SABER_FIELD.getZ() + 0.5F;
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.cylinder(new Vector3f(sx, R, sz), new Vector3f(0, 0.95F, 0), 0.34F, 0.28F, 12, 2.0F, false, true);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.cylinder(new Vector3f(sx, R + 2.35F, sz), new Vector3f(0, 0.1F, 0), 0.34F, 0.34F, 12, 2.0F, true, true);
		b.pipe(0.04F, 5, sx + 0.3F, R + 2.45F, sz, sx + 0.3F, R + 5.0F, sz);
		// The holo projector.
		float hx = HOLO.getX() + 0.5F;
		float hz = HOLO.getZ() + 0.5F;
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.cylinder(new Vector3f(hx, R, hz), new Vector3f(0, 0.14F, 0), 0.38F, 0.34F, 12, 2.0F, false, true);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SLOW).colour(0xFF5A8AE8).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.ring(hx, R + 0.145F, hz, 0.0F, 0.2F, 12, true);
		b.layer(Layer.SOLID).worldLight().anim(0);
	}

	/** The coolant valve on the reactor floor's wall (the story can turn it). */
	private static void reactorValve(MeshBuilder b) {
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);
		b.pipe(0.12F, 8, VALVE_X + 0.45F, R - 0.2F, VALVE_Z + 0.45F, VALVE_X + 0.45F, R + 3.6F, VALVE_Z + 0.45F);
		b.pipe(0.08F, 8, VALVE_X + 0.45F, R + 1.0F, VALVE_Z + 0.45F, VALVE_X + 0.05F, R + 1.0F, VALVE_Z + 0.05F);
	}

	static final float VALVE_X = (float) com.phiro.sabercraft.interior.DestroyerInterior.VALVE_X;
	static final float VALVE_Z = (float) com.phiro.sabercraft.interior.DestroyerInterior.VALVE_Z;

	// --- Moving parts and what the story changes ---------------------------------------------------

	/** Phase 23: per plot: whether the reactor's bridge stood when last drawn, and when it was seen to fall (seconds). */
	private static final java.util.Map<BlockPos, Boolean> BRIDGE_STOOD = new java.util.HashMap<>();
	private static final java.util.Map<BlockPos, Float> BRIDGE_FELL = new java.util.HashMap<>();
	/** Phase 23: how long its pieces are seen falling (seconds). */
	static final float BRIDGE_FALL = 3.2F;

	/**
	 * Phase 23: the reactor's bridge that breaks in the escape: drawn while its barriers stand
	 * (trembling while it groans under the player); the moment they are gone, its pieces fall into
	 * the depth one after another from where it snapped, turning, and are lost in the haze.
	 */
	private static void collapse(MeshBuilder b, float seconds, InteriorVisuals.PlotView plot, boolean story) {
		BlockPos o = plot.origin();
		Gantry first = COLLAPSE.getFirst();
		BlockPos probe = o.offset(first.x0(), first.y() - 1, first.z1());

		if (!plot.level().isLoaded(probe)) {
			return;
		}

		boolean stands = collapseStands(plot.level(), o);
		Boolean before = BRIDGE_STOOD.put(o.immutable(), stands);

		if (stands) {
			BRIDGE_FELL.remove(o);
			float tremble = story ? StoryClient.creak() : 0.0F;

			for (Gantry g : COLLAPSE) {
				DestroyerRooms.gantry(b, g, tremble, seconds);
			}

			return;
		}

		if (Boolean.TRUE.equals(before)) {
			BRIDGE_FELL.put(o.immutable(), seconds);
		}

		Float fell = BRIDGE_FELL.get(o);

		if (fell == null || seconds - fell > BRIDGE_FALL) {
			return;
		}

		float t0 = seconds - fell;
		// The pieces: a block's length of the bridge each, in the order they go (from the snap at the
		// lift's platform along to the lab's end).
		int index = 0;

		for (Gantry g : COLLAPSE) {
			boolean alongX = g.x1() - g.x0() > g.z1() - g.z0();
			int count = alongX ? g.x1() - g.x0() + 1 : g.z1() - g.z0() + 1;

			for (int k = 0; k < count; k++) {
				Gantry piece = alongX ? new Gantry(g.id(), g.x0() + k, g.z0(), g.x0() + k, g.z1(), g.y(), true)
						: new Gantry(g.id(), g.x0(), g.z0() + k, g.x1(), g.z0() + k, g.y(), true);
				float t = t0 - index * 0.07F;
				index++;

				if (t < 0.0F) {
					DestroyerRooms.gantry(b, piece, 1.0F, seconds);
					continue;
				}

				float drop = 0.5F * 15.0F * t * t;

				if (g.y() - drop < PIT_DRAWN + 2.0F) {
					continue;
				}

				net.minecraft.world.phys.Vec3 c = piece.centre();
				float spin = (index % 2 == 0 ? 1.0F : -1.0F) * (70.0F + index * 13 % 50);
				float drift = 0.6F * t;
				double ox = c.x - REACTOR_X;
				double oz = c.z - REACTOR_Z;
				double len = Math.max(0.1, Math.hypot(ox, oz));
				b.push().translate(c.x - ox / len * drift, c.y - 0.07 - drop, c.z - oz / len * drift).rotateX(spin * t).rotateZ(spin * 0.6F * t)
						.translate(-c.x, -c.y + 0.07, -c.z);
				DestroyerRooms.gantry(b, piece, 0.0F, seconds);
				b.pop();
			}
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	static void animate(MeshBuilder b, float seconds, InteriorVisuals.PlotView plot) {
		boolean story = StoryClient.storyPlot(plot.origin());
		BlockPos o = plot.origin();
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		collapse(b, seconds, plot, story);

		// The cells' force fields, while their barriers stand.
		for (int[] cell : CELLS) {
			if (plot.level().getBlockState(o.offset(cell[0], (int) H, CELL_FRONT)).is(Blocks.BARRIER)) {
				float x0 = cell[0];
				float x1 = cell[1] + 1;
				b.layer(Layer.HOLO).fullBright().colourAlpha(0x5878B8FF).tile(InteriorAtlas.IMP_FORCE_FIELD_GLOW, 1.0F).anim(InteriorScene.ANIM_SHIMMER)
						.twoSided(true);
				b.rect(x0, H + 2.86F, CELL_FRONT + 0.5F, x1 - x0, 0, 0, 0, -2.74F, 0);
				b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
			}
		}

		// The coolant valve's wheel: turned (and a warning lamp) once the story turned it.
		boolean turned = story && StoryClient.has("opt.coolant");
		b.push().translate(VALVE_X, R + 1.0F, VALVE_Z).rotateY(45.0F).rotateX(-90.0F).rotateY(turned ? 90.0F : 0.0F);
		b.colour(0xFFC03828).tile(InteriorAtlas.IMP_METAL, 1.0F).twoSided(true);
		b.ring(0, 0, 0, 0.24F, 0.3F, 16, true);
		b.box(-0.3F, -0.01F, -0.025F, 0.3F, 0.01F, 0.025F);
		b.box(-0.025F, -0.01F, -0.3F, 0.025F, 0.01F, 0.3F);
		b.white().twoSided(false);
		b.pop();

		if (turned) {
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_BLINK).colour(0xFFFF3020).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
			b.box(VALVE_X + 0.3F, R + 2.0F, VALVE_Z + 0.3F, VALVE_X + 0.5F, R + 2.2F, VALVE_Z + 0.5F);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}

		// The armory's panel: red while locked, green once open.
		boolean open = story && StoryClient.has("armory.open");
		float wall = CX + 4;
		b.layer(Layer.GLOW).fullBright().anim(open ? 0 : InteriorScene.ANIM_PULSE).colour(open ? 0xFF50E070 : 0xFFFF4030)
				.fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.box(wall - 0.25F, H + 0.66F, -8.0F, wall - 0.22F, H + 0.76F, -7.8F, MeshBuilder.WEST);
		b.layer(Layer.SOLID).worldLight().anim(0);

		// Things lying about that the story lets one take.
		if (story && StoryClient.point("pad")) {
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(11.45F, H + 0.85F, 0.35F, 11.85F, H + 0.88F, 0.75F);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
			b.rect(11.48F, H + 0.881F, 0.38F, 0.34F, 0, 0, 0, 0, 0.34F);
			b.layer(Layer.SOLID).worldLight().anim(0);
		}

		if (!story || !StoryClient.has("item.card")) {
			b.fit(InteriorAtlas.STORY_KEYCARD);
			b.push().translate(49.45F, H + 0.851F, DESK_Z + 0.5F).rotateY(20.0F);
			b.rect(-0.12F, 0, -0.08F, 0.24F, 0, 0, 0, 0, 0.16F);
			b.pop();
		}

		if (story && StoryClient.point("recorder")) {
			// Kestrel's recorder on the crate, its light blinking, a little picture of her above it.
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(22.3F, H + 1.0F, -4.7F, 22.7F, H + 1.12F, -4.3F);
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_BLINK).colour(0xFF4A8AFF).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
			b.box(22.45F, H + 1.12F, -4.55F, 22.55F, H + 1.14F, -4.45F, MeshBuilder.UP);
			b.layer(Layer.SOLID).worldLight().anim(0);
			Props.holoBeam(b, 22.5F, H + 1.12F, -4.5F, 0.05F, 0.14F, 0.25F, 0x405A8AFF);
		}

		lab(b, seconds, story);
	}

	/**
	 * Phase 20: the lab's blast door, plain to see from the reactor floor: hazard stripes on the
	 * threshold, a lit frame round the opening (it was a dark door at the end of a dark passage).
	 * Its lamp (red while it is locked) is drawn with the moving parts.
	 */
	private static void labDoor(MeshBuilder b) {
		float x0 = LAB_DOOR_X0;
		float x1 = LAB_DOOR_X0 + 3;
		float y0 = R;
		float y1 = R + 3;
		float z1 = LAB_WALL;
		float z0 = z1 - 0.28F;
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false).tile(InteriorAtlas.IMP_HAZARD, 1.0F);
		b.box(x0, y0, z1 - 1.25F, x1, y0 + 0.012F, z1, MeshBuilder.UP);
		// The frame: a light strip up each side and across the top, a step before the door.
		b.layer(Layer.GLOW).fullBright().colour(0xFFE8A040).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.4F, 1.0F, 0.6F);
		b.box(x0, y0, z0, x0 + 0.03F, y1, z1 - 0.06F, MeshBuilder.EAST);
		b.box(x1 - 0.03F, y0, z0, x1, y1, z1 - 0.06F, MeshBuilder.WEST);
		b.box(x0, y1 - 0.03F, z0, x1, y1, z1 - 0.06F, MeshBuilder.DOWN);
		b.white().layer(Layer.SOLID).worldLight();
	}

	/** The tracker running (or failing once the saber is gone), the saber in its field, Ilyra's message. */
	private static void lab(MeshBuilder b, float seconds, boolean story) {
		boolean taken = story && StoryClient.has("saber.taken");
		// Phase 20: the lab door's lamp over the threshold: red and pulsing while it is locked, green
		// once it opens (in the story it opens only with the code; outside it is an ordinary door).
		boolean open = !story || StoryClient.has("lab.open");
		b.layer(Layer.GLOW).fullBright().anim(open ? 0 : InteriorScene.ANIM_PULSE).colour(open ? 0xFF58E078 : 0xFFE84030)
				.fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.box(LAB_DOOR_X0 + 1.2F, R + 2.84F, LAB_WALL - 0.42F, LAB_DOOR_X0 + 1.8F, R + 2.995F, LAB_WALL - 0.1F, MeshBuilder.ALL & ~MeshBuilder.UP);
		b.white().anim(0).layer(Layer.SOLID).worldLight();
		float tx = (float) TRACKER_X;
		float tz = (float) TRACKER_Z;
		float r = (float) TRACKER_R;

		if (!taken) {
			// The tracker's lights: rings sweeping round its body, the crown's glow, a beam to the ceiling.
			b.layer(Layer.GLOW).fullBright().anim(0).colour(0xFFE050C8).fit(InteriorAtlas.STORY_TRACKER_GLOW);
			b.push().translate(tx, R + 2.071F, tz).rotateY(seconds * 40.0F);
			b.ring(0, 0, 0, 0.0F, r - 0.25F, 24, true);
			b.pop();
			b.layer(Layer.HOLO).fullBright().anim(InteriorScene.ANIM_SHIMMER).twoSided(true).colourAlpha(0x60D060E0).fit(InteriorAtlas.IMP_REACTOR_GLOW);

			for (int i = 0; i < 3; i++) {
				float rise = (seconds * 0.5F + i / 3.0F) % 1.0F;
				b.ring(tx, R + 0.4F + rise * 1.45F, tz, r - 0.44F + 0.02F, r - 0.3F, 24, true);
			}

			b.colourAlpha(0x50FF70E0).fit(InteriorAtlas.IMP_HOLO);
			b.cylinder(new Vector3f(tx, R + 2.55F, tz), new Vector3f(0, 2.4F, 0), 0.12F, 0.32F, 10, 1.0F, false, false);
			b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
		} else {
			// Failing: its lights flicker out now and then, sparks over the crown.
			b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_FLICKER).colour(0xFF502038).fit(InteriorAtlas.STORY_TRACKER_GLOW);
			b.ring(tx, R + 2.071F, tz, 0.0F, r - 0.25F, 24, true);

			if (Mth.sin(seconds * 7.0F) > 0.85F) {
				b.colour(0xFFFFE0A0).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
				float a = seconds * 3.0F;
				b.box(tx + Mth.cos(a) * 0.8F - 0.04F, R + 2.6F, tz + Mth.sin(a) * 0.8F - 0.04F, tx + Mth.cos(a) * 0.8F + 0.04F, R + 2.68F,
						tz + Mth.sin(a) * 0.8F + 0.04F);
			}

			b.layer(Layer.SOLID).worldLight().white().anim(0);
		}

		float sx = SABER_FIELD.getX() + 0.5F;
		float sz = SABER_FIELD.getZ() + 0.5F;

		if (!taken) {
			// The saber, hanging in its field and turning slowly; the field round it.
			b.push().translate(sx, R + 1.55F + 0.04F * Mth.sin(seconds * 1.3F), sz).rotateY(seconds * 18.0F).rotateZ(90.0F);
			b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
			b.box(-0.03F, -0.14F, -0.03F, 0.03F, 0.14F, 0.03F);
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(-0.034F, -0.06F, -0.034F, 0.034F, 0.04F, 0.034F);
			b.box(-0.036F, 0.12F, -0.036F, 0.036F, 0.15F, 0.036F);
			b.colour(0xFF3AA860).fit(InteriorAtlas.WHITE);
			b.box(-0.012F, -0.02F, 0.029F, 0.012F, 0.02F, 0.036F);
			b.white();
			b.pop();
			b.layer(Layer.HOLO).fullBright().anim(InteriorScene.ANIM_SHIMMER).twoSided(true).colourAlpha(0x4870E8B0).tile(InteriorAtlas.IMP_FORCE_FIELD_GLOW, 1.0F);
			b.cylinder(new Vector3f(sx, R + 0.95F, sz), new Vector3f(0, 1.4F, 0), 0.3F, 0.3F, 16, 2.0F, false, true);
			b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
		}

		// Ilyra Voss's message: her figure in blue light over the projector, until the saber is taken.
		if (story && StoryClient.mission() == 5 && StoryClient.has("lab.open") && !taken) {
			hologram(b, HOLO.getX() + 0.5F, R + 0.16F, HOLO.getZ() + 0.5F, 90.0F, seconds);
		}
	}

	/** A small standing figure of light: a robe, the head with her face, the hands folded; flickering. */
	private static void hologram(MeshBuilder b, float x, float y, float z, float yaw, float seconds) {
		Props.holoBeam(b, x, y, z, 0.2F, 0.32F, 0.25F, 0x305A8AFF);
		float k = 0.62F;
		float flicker = Mth.sin(seconds * 23.0F) > 0.94F ? 0.03F : 0.0F;
		b.layer(Layer.HOLO).fullBright().anim(InteriorScene.ANIM_SHIMMER).twoSided(false).colourAlpha(0x8C7AA8FF);
		b.push().translate(x + flicker, y + 0.25F, z).rotateY(-yaw).scale(k, k, k);
		// The robe, wider at the hem.
		b.fit(InteriorAtlas.STORY_ILYRA_ROBE);
		b.cylinder(new Vector3f(0, 0, 0), new Vector3f(0, 1.35F, 0), 0.34F, 0.2F, 10, 1.0F, false, true);
		// The arms folded in front.
		b.box(-0.17F, 0.85F, 0.12F, 0.17F, 1.0F, 0.28F);
		// The head: her face to the front.
		float h = CrewModels.HEAD / 2.0F;
		b.fit(InteriorAtlas.STORY_ILYRA_FACE);
		b.box(-h, 1.38F, -h, h, 1.38F + CrewModels.HEAD, h, MeshBuilder.SOUTH);
		b.colourAlpha(0x8C8CB0FF).fit(InteriorAtlas.CREW_HAIR_TOP);
		b.box(-h, 1.38F, -h, h, 1.38F + CrewModels.HEAD, h, MeshBuilder.ALL & ~MeshBuilder.SOUTH);
		b.pop();
		b.layer(Layer.SOLID).worldLight().white().anim(0);
	}

	/** A housing panelled with a tile per block column, the full height each. */
	private static void panels(MeshBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, int tile) {
		float h = y1 - y0;

		for (float x = x0; x < x1 - 0.01F; x += 1.0F) {
			float w = Math.min(1.0F, x1 - x);
			b.fit(tile, 0.0F, 0.0F, w, 1.0F);
			b.rect(x, y1, z1, w, 0, 0, 0, -h, 0);
			b.rect(x1 - (x - x0), y1, z0, -w, 0, 0, 0, -h, 0);
		}

		for (float z = z0; z < z1 - 0.01F; z += 1.0F) {
			float w = Math.min(1.0F, z1 - z);
			b.fit(tile, 0.0F, 0.0F, w, 1.0F);
			b.rect(x0, y1, z, 0, 0, w, 0, -h, 0);
			b.rect(x1, y1, z1 - (z - z0), 0, 0, -w, 0, -h, 0);
		}
	}
}
