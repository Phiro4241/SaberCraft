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
		serviceWay(b);
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	// --- Phase 25: the maintenance way out of the reactor -----------------------------------------

	/** Where the frame round the maintenance way's mouth stands (in the round wall's cut). */
	private static final float SERVICE_MOUTH = 13.45F;
	/** The drawn release box (inside the interaction box, clear of the frame's west post). */
	private static final float RELEASE_Z0 = (float) SERVICE_RELEASE.minZ;
	private static final float RELEASE_Z1 = (float) SERVICE_RELEASE.maxZ - 0.4F;

	/**
	 * Phase 25: the passage at the end of the walkway (a hazard-striped frame round its mouth, a
	 * plate over the door), the emergency release on its west wall, the chamber behind the door with
	 * pipes along its walls, the panel that bolts the door on its east wall. The lamps that show the
	 * way are drawn by {@link #serviceLights} (they come on with the alarm).
	 */
	private static void serviceWay(MeshBuilder b) {
		float x0 = SERVICE_WAY.x0();
		float x1 = SERVICE_WAY.x1() + 1;
		float mouth = SERVICE_MOUTH;
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		// The mouth's frame: posts and a lintel striped like a warning, a sill. (In the round wall's
		// cut, so that the west post stands in the wall and not in the shaft where one walks.)
		b.fit(InteriorAtlas.IMP_HAZARD);
		b.box(x0 - 0.16F, R, mouth - 0.12F, x0 + 0.02F, R + 3.0F, mouth + 0.12F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.box(x1 - 0.02F, R, mouth - 0.12F, x1 + 0.16F, R + 3.0F, mouth + 0.12F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.box(x0 - 0.16F, R + 2.86F, mouth - 0.12F, x1 + 0.16F, R + 3.08F, mouth + 0.12F, MeshBuilder.ALL);
		b.box(x0, R, mouth - 0.3F, x1, R + 0.03F, mouth + 0.1F, MeshBuilder.UP);
		// A plate over the door: the maintenance mark.
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(x0 + 0.3F, R + 2.62F, SERVICE_DOOR_Z + 1.02F, x1 - 0.3F, R + 2.9F, SERVICE_DOOR_Z + 1.06F, MeshBuilder.SOUTH);
		b.layer(Layer.GLOW).fullBright().colour(0xFFE0A040).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.box(x0 + 0.38F, R + 2.66F, SERVICE_DOOR_Z + 1.07F, x1 - 0.38F, R + 2.86F, SERVICE_DOOR_Z + 1.08F, MeshBuilder.SOUTH);
		b.white().layer(Layer.SOLID).worldLight();

		// The emergency release: a box on the west wall, a handle, a screen.
		float rx = (float) SERVICE_RELEASE.maxX;
		float rz0 = RELEASE_Z0;
		float rz1 = RELEASE_Z1;
		b.fit(InteriorAtlas.IMP_CONSOLE);
		b.box(rx - 0.14F, R + 0.78F, rz0, rx, R + 1.62F, rz1, MeshBuilder.ALL & ~MeshBuilder.WEST);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F).colour(0xFFD8A030);
		// (A flat bar: no deeper into the way than where one's body passes.)
		b.box(rx, R + 0.98F, rz0 + 0.25F, rx + 0.045F, R + 1.06F, rz1 - 0.25F, MeshBuilder.ALL & ~MeshBuilder.WEST);
		b.white();

		// The chamber: pipes along the west wall and under the ceiling, a valve wheel, a cable tray.
		b.tile(InteriorAtlas.IMP_PIPE, 1.0F);
		b.pipe(0.11F, 6, 47.18F, R, 6.4F, 47.18F, R + 2.7F, 6.4F, 50.6F, R + 2.7F, 6.4F);
		b.pipe(0.08F, 6, 47.15F, R + 0.4F, 9.6F, 47.15F, R + 0.4F, 6.6F, 47.15F, R + 2.5F, 6.6F);
		b.pipe(0.07F, 6, 50.8F, R + 2.85F, 6.3F, 50.8F, R + 2.85F, 9.7F);
		b.colour(0xFFB03020).tile(InteriorAtlas.IMP_METAL, 1.0F).twoSided(true);
		b.push().translate(47.32F, R + 1.4F, 7.9F).rotateZ(90.0F);
		b.ring(0, 0, 0, 0.18F, 0.23F, 14, true);
		b.pop();
		b.white().twoSided(false);
		// The panel that bolts the door, on the east wall by the door.
		float lx = (float) SERVICE_LOCK.minX + 0.4F;
		float lz0 = (float) SERVICE_LOCK.minZ + 0.1F;
		float lz1 = (float) SERVICE_LOCK.maxZ - 0.1F;
		b.fit(InteriorAtlas.IMP_CONSOLE);
		b.box(lx, R + 0.78F, lz0, lx + 0.15F, R + 1.62F, lz1, MeshBuilder.ALL & ~MeshBuilder.EAST);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F).colour(0xFFD8A030);
		b.box(lx - 0.1F, R + 0.98F, lz0 + 0.3F, lx, R + 1.06F, lz1 - 0.3F, MeshBuilder.ALL & ~MeshBuilder.EAST);
		b.white();
		// Arrows on the crawl way's hatch frame: this way out.
		b.layer(Layer.GLOW).fullBright().colour(0xFFC08030).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.4F, 1.0F, 0.6F);
		b.box(SERVICE_HATCH.getX() + 0.08F, R + 1.08F, SERVICE_HATCH.getZ() + 1.01F, SERVICE_HATCH.getX() + 0.92F, R + 1.14F, SERVICE_HATCH.getZ() + 1.02F,
				MeshBuilder.SOUTH);
		b.white().layer(Layer.SOLID).worldLight();
	}

	/**
	 * Phase 25: what of the maintenance way the story changes, every frame: once the alarm runs,
	 * amber lamps along the walkway's edge run towards the door (the way out), a beacon over the
	 * mouth turns; the release's screen pulses amber until it is pulled, then green; the bolt
	 * panel inside pulses until the door is bolted, then shows red.
	 */
	private static void serviceLights(MeshBuilder b, float seconds, boolean story) {
		boolean lit = story && StoryClient.has("service.lit");
		boolean open = story && StoryClient.has("service.open");
		boolean locked = story && StoryClient.has("service.locked");

		if (lit && !locked) {
			// Running lamps along both edges of the walkway, from the platform to the door.
			for (int i = 0; i < 6; i++) {
				float z = 19.6F - i;
				float phase = (seconds * 3.0F - i * 0.5F) % 3.0F;
				float glow = phase < 0.5F ? 1.0F : 0.28F;
				int amber = 0xFF000000 | Math.round(0xE0 * glow) << 16 | Math.round(0x98 * glow) << 8 | Math.round(0x30 * glow);
				b.layer(Layer.GLOW).fullBright().anim(0).colour(amber).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
				b.box(48.02F, R + 0.01F, z - 0.12F, 48.12F, R + 0.05F, z + 0.12F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
				b.box(49.88F, R + 0.01F, z - 0.12F, 49.98F, R + 0.05F, z + 0.12F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			}

			// The beacon over the mouth: a lamp that flashes as it turns.
			float turn = seconds * 4.0F % ((float) Math.PI * 2.0F);
			float flash = 0.45F + 0.55F * Math.max(0.0F, Mth.cos(turn));
			int c = 0xFF000000 | Math.round(0xF0 * flash) << 16 | Math.round(0xA0 * flash) << 8 | Math.round(0x30 * flash);
			b.layer(Layer.GLOW).fullBright().colour(c).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
			b.box(48.82F, R + 3.08F, SERVICE_MOUTH - 0.18F, 49.18F, R + 3.3F, SERVICE_MOUTH + 0.17F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
			b.layer(Layer.HOLO).fullBright().colourAlpha(Math.round(0x50 * flash) << 24 | 0xF0A040).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW).twoSided(true);
			b.rect(49.0F + Mth.cos(turn) * 0.2F, R + 3.19F, SERVICE_MOUTH + Mth.sin(turn) * 0.2F, Mth.cos(turn) * 1.3F, 0, Mth.sin(turn) * 1.3F, 0, 0.12F, 0);
			b.twoSided(false);
		}

		// The release's screen.
		float rx = (float) SERVICE_RELEASE.maxX;
		int release = open ? 0xFF50E070 : lit ? 0xFFF0A030 : 0xFF904020;
		b.layer(Layer.GLOW).fullBright().anim(open || !lit ? 0 : InteriorScene.ANIM_PULSE).colour(release).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.box(rx + 0.001F, R + 1.18F, RELEASE_Z0 + 0.15F, rx + 0.006F, R + 1.5F, RELEASE_Z1 - 0.15F, MeshBuilder.EAST);
		// The bolt panel's screen.
		float lx = (float) SERVICE_LOCK.minX + 0.4F;
		int bolt = locked ? 0xFFFF4030 : open ? 0xFFF0A030 : 0xFF604020;
		b.colour(bolt).anim(open && !locked ? InteriorScene.ANIM_PULSE : 0);
		b.box(lx - 0.006F, R + 1.18F, (float) SERVICE_LOCK.minZ + 0.25F, lx - 0.001F, R + 1.5F, (float) SERVICE_LOCK.maxZ - 0.25F, MeshBuilder.WEST);
		b.white().anim(0).layer(Layer.SOLID).worldLight();
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
		// Phase 26: with the desk under it half a block north: its end stood in the post of the
		// corridor's frame at z -8, its face in the post's face, and flickered there.
		float wall = CX + 4;
		float dz = (float) (com.phiro.sabercraft.interior.DestroyerInterior.SECURITY_DESK_Z + 8.5);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(wall - 0.22F, H + 0.55F, -9.4F + dz, wall, H + 1.45F, -7.6F + dz, MeshBuilder.ALL & ~MeshBuilder.EAST);
		b.fit(InteriorAtlas.IMP_SCREEN);
		b.rect(wall - 0.228F, H + 1.38F, -9.3F + dz, 0, 0, 0.9F, 0, -0.5F, 0);
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_SCREEN).fit(InteriorAtlas.IMP_SCREEN_TEXT_GLOW);
		b.rect(wall - 0.236F, H + 1.34F, -9.26F + dz, 0, 0, 0.82F, 0, -0.42F, 0);
		b.fit(InteriorAtlas.IMP_CONSOLE_TOP_GLOW);
		b.rect(wall - 0.236F, H + 0.84F, -9.0F + dz, 0, 0, 0.6F, 0, -0.22F, 0);
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

	/**
	 * Phase 26: a cell's field failing ({@code p} 0 to 1): drawn in horizontal bands, each dropping
	 * out now and then (more often towards the end), the whole field blinking off for a frame or
	 * two, its colour paling; the bands near the emitter's end, where M-31 works, go first.
	 */
	static void fieldFlicker(MeshBuilder b, float x0, float x1, float seconds, float p) {
		int frame = (int) (seconds * 30.0F);
		// The whole field off for a moment: rarely at first, often at the end.
		float off = hash(frame, 7);

		if (off < 0.08F + 0.42F * p * p) {
			return;
		}

		int bands = 12;
		float top = H + 2.86F;
		float height = 2.74F / bands;
		int alpha = (int) (0x58 * (1.0F - 0.35F * p) + 0x30 * (hash(frame, 3) - 0.5F));

		for (int k = 0; k < bands; k++) {
			// Low bands (by the emitter) fail first.
			float weak = p * (1.2F - k / (float) bands * 0.6F);

			if (hash(frame / 2 + k * 13, k) < weak * 0.85F) {
				continue;
			}

			// A band now and then shifted sideways (the picture tears).
			float shift = hash(frame + k * 31, 11) < p * 0.4F ? (hash(frame, k + 5) - 0.5F) * 0.3F : 0.0F;
			float a0 = Math.max(x0, x0 + shift);
			float a1 = Math.min(x1, x1 + shift);
			int colour = (Math.max(0, Math.min(255, alpha)) << 24) | (p > 0.6F && hash(frame, k) < 0.3F ? 0xC8E0FF : 0x78B8FF);
			b.layer(Layer.HOLO).fullBright().colourAlpha(colour).tile(InteriorAtlas.IMP_FORCE_FIELD_GLOW, 1.0F).anim(InteriorScene.ANIM_SHIMMER).twoSided(true);
			b.rect(a0, top - k * height, CELL_FRONT + 0.5F, a1 - a0, 0, 0, 0, -height + 0.01F, 0);
		}

		b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
	}

	/** Phase 27: how the security barrier is drawn: its field (red) and the lamps on its pylons. */
	static final int BARRIER_FIELD = 0x58FF4434;
	static final int BARRIER_LAMP = 0xFFFF4030;

	/**
	 * Phase 27: the security barrier security put up before the turbolift to the bridge: two
	 * pylons (hazard bands, a red lamp on top, blinking in turn) with a red force field between
	 * them, glowing bars along its top and foot. It stands in the lobby's last row before the
	 * lift's bay ({@link com.phiro.sabercraft.story.Chapter1#sealBridge}); the field is drawn on its
	 * near face, where one walks into it.
	 */
	static void bridgeBarrier(MeshBuilder b, float seconds) {
		float x0 = com.phiro.sabercraft.story.Chapter1.BRIDGE_BARRIER_X0;
		float x1 = com.phiro.sabercraft.story.Chapter1.BRIDGE_BARRIER_X1 + 1;
		float z = com.phiro.sabercraft.story.Chapter1.BRIDGE_BARRIER_Z;

		for (int side = 0; side < 2; side++) {
			float px = side == 0 ? x0 - 0.17F : x1 + 0.17F;
			b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
			// Foot, post, hazard band and the lamp's housing.
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(px - 0.24F, H, z - 0.2F, px + 0.24F, H + 0.12F, z + 0.28F);
			b.tile(InteriorAtlas.IMP_HULL_DARK, 1.0F);
			b.box(px - 0.13F, H + 0.12F, z - 0.1F, px + 0.13F, H + 2.85F, z + 0.18F);
			b.tile(InteriorAtlas.IMP_HAZARD, 0.5F);
			b.box(px - 0.135F, H + 1.0F, z - 0.105F, px + 0.135F, H + 1.3F, z + 0.185F, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(px - 0.15F, H + 2.85F, z - 0.12F, px + 0.15F, H + 2.95F, z + 0.2F);
			// The lamp: blinking, one pylon after the other.
			boolean on = ((seconds + side * 0.6F) % 1.2F) < 0.6F;
			b.layer(Layer.GLOW).fullBright().colour(on ? BARRIER_LAMP : 0xFF601810).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
			b.box(px - 0.09F, H + 2.95F, z - 0.06F, px + 0.09F, H + 3.07F, z + 0.14F);
			// The emitter's strip down the inner side of the post.
			float inner = side == 0 ? px + 0.13F : px - 0.13F;
			b.colour(BARRIER_LAMP).anim(InteriorScene.ANIM_PULSE);
			b.box(side == 0 ? inner : inner - 0.02F, H + 0.2F, z - 0.04F, side == 0 ? inner + 0.02F : inner, H + 2.75F, z + 0.04F);
			b.anim(0);
		}

		// The field, a hair before the barrier's cells, and its bars.
		b.layer(Layer.HOLO).fullBright().colourAlpha(BARRIER_FIELD).tile(InteriorAtlas.IMP_FORCE_FIELD_GLOW, 1.0F).anim(InteriorScene.ANIM_SHIMMER).twoSided(true);
		b.rect(x0 - 0.04F, H + 2.75F, z - 0.02F, x1 - x0 + 0.08F, 0, 0, 0, -2.6F, 0);
		b.layer(Layer.GLOW).fullBright().colour(BARRIER_LAMP).anim(InteriorScene.ANIM_PULSE).twoSided(false)
				.fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.box(x0 - 0.04F, H + 2.75F, z - 0.05F, x1 + 0.04F, H + 2.79F, z + 0.01F);
		b.box(x0 - 0.04F, H + 0.11F, z - 0.05F, x1 + 0.04F, H + 0.15F, z + 0.01F);
		b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
	}

	/**
	 * Phase 28: the side way's cover in its cell (x 11..12 of the crawl way): from the hangar's
	 * side (east) a grille plate in a frame with a screw in each corner and a fan turning slowly
	 * behind it; from the crawl way from flight control (west) only the plain back of the fan's
	 * housing. Off, the plate lies flat in the next cell east (it tips over onto it as it comes off:
	 * {@code off} 0..1, or -1), the fan and its housing gone with it (the way is open).
	 */
	static void ventCover(MeshBuilder b, float seconds, boolean shut, float off) {
		BlockPos cell = com.phiro.sabercraft.story.Chapter1.VENT_COVER;
		float x = cell.getX();
		float y = cell.getY();
		float z = cell.getZ();
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);

		if (shut) {
			// The housing's back (west), the fan in the middle, the frame and the grille (east).
			b.tile(InteriorAtlas.IMP_HULL_DARK, 1.0F);
			b.box(x + 0.02F, y + 0.02F, z + 0.02F, x + 0.08F, y + 0.98F, z + 0.98F);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(x + 0.08F, y + 0.4F, z + 0.4F, x + 0.2F, y + 0.6F, z + 0.6F);
			b.push().translate(x + 0.45F, y + 0.5F, z + 0.5F).rotateX(seconds * 40.0F % 360.0F);

			for (int k = 0; k < 4; k++) {
				b.push().rotateX(k * 90.0F);
				b.box(-0.03F, -0.05F, 0.04F, 0.03F, 0.05F, 0.4F);
				b.pop();
			}

			b.pop();
			b.box(x + 0.4F, y + 0.44F, z + 0.44F, x + 0.5F, y + 0.56F, z + 0.56F);
			coverPlate(b);
			return;
		}

		// Off: tipping over east onto the floor of the next cell (its foot at the cell's east edge).
		float tip = off < 0.0F ? 1.0F : off;
		tip = tip * tip * (3.0F - 2.0F * tip);
		b.push().translate(x + 1.0F, y + 0.02F, z).rotateZ(-90.0F * tip).translate(-(x + 1.0F), -y, -z);
		coverPlate(b);
		b.pop();
	}

	/** Phase 28: the cover's plate, standing in its cell's east face: frame, grille, four screws. */
	private static void coverPlate(MeshBuilder b) {
		BlockPos cell = com.phiro.sabercraft.story.Chapter1.VENT_COVER;
		float x = cell.getX() + 0.9F;
		float y = cell.getY();
		float z = cell.getZ();
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x, y, z + 0.02F, x + 0.08F, y + 0.08F, z + 0.98F);
		b.box(x, y + 0.92F, z + 0.02F, x + 0.08F, y + 1.0F, z + 0.98F);
		b.box(x, y + 0.08F, z + 0.02F, x + 0.08F, y + 0.92F, z + 0.1F);
		b.box(x, y + 0.08F, z + 0.9F, x + 0.08F, y + 0.92F, z + 0.98F);
		b.fit(InteriorAtlas.IMP_WALL_VENT);
		b.box(x + 0.02F, y + 0.08F, z + 0.1F, x + 0.05F, y + 0.92F, z + 0.9F, MeshBuilder.EAST | MeshBuilder.WEST);
		// A yellow and black band at its top: maintenance access.
		b.tile(InteriorAtlas.IMP_HAZARD, 0.5F);
		b.box(x + 0.08F, y + 0.82F, z + 0.12F, x + 0.085F, y + 0.9F, z + 0.88F, MeshBuilder.EAST);
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);

		for (float[] screw : new float[][] {{0.12F, 0.12F}, {0.12F, 0.88F}, {0.88F, 0.12F}, {0.88F, 0.88F}}) {
			b.box(x + 0.08F, y + screw[0] - 0.035F, z + screw[1] - 0.035F, x + 0.11F, y + screw[0] + 0.035F, z + screw[1] + 0.035F,
					MeshBuilder.ALL & ~MeshBuilder.WEST);
		}
	}

	/**
	 * Phase 28: the technicians' toolbox by the containers: a red metal box with a handle on its
	 * lid; with the screwdriver still in it, its handle shows at the lid's edge; taken, the lid
	 * stands open (the box is empty but for a spanner).
	 */
	static void toolbox(MeshBuilder b, boolean taken) {
		BlockPos cell = com.phiro.sabercraft.story.Chapter1.TOOLBOX;
		float x = cell.getX();
		float y = cell.getY();
		float z = cell.getZ();
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		// A low pallet under it, the box on it.
		b.tile(InteriorAtlas.IMP_CRATE, 1.0F);
		b.box(x + 0.08F, y, z + 0.12F, x + 0.92F, y + 0.1F, z + 0.88F);
		b.colour(0xFFB83A28).tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x + 0.15F, y + 0.1F, z + 0.25F, x + 0.85F, y + 0.48F, z + 0.75F);
		b.white();
		b.tile(InteriorAtlas.IMP_BLACK, 1.0F);
		b.box(x + 0.18F, y + 0.47F, z + 0.28F, x + 0.82F, y + 0.481F, z + 0.72F, MeshBuilder.UP);

		if (taken) {
			// The lid open, swung back on its hinge (north edge); a spanner left inside.
			b.push().translate(x + 0.5F, y + 0.48F, z + 0.25F).rotateX(-105.0F);
			b.colour(0xFFB83A28).tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(-0.35F, 0.0F, 0.0F, 0.35F, 0.05F, 0.5F);
			b.white().pop();
			b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
			b.box(x + 0.3F, y + 0.48F, z + 0.46F, x + 0.7F, y + 0.5F, z + 0.52F);
			return;
		}

		b.colour(0xFFC84A34).tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x + 0.14F, y + 0.48F, z + 0.24F, x + 0.86F, y + 0.53F, z + 0.76F);
		b.white().tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		// The lid's handle.
		b.box(x + 0.35F, y + 0.53F, z + 0.47F, x + 0.38F, y + 0.6F, z + 0.53F);
		b.box(x + 0.62F, y + 0.53F, z + 0.47F, x + 0.65F, y + 0.6F, z + 0.53F);
		b.box(x + 0.35F, y + 0.6F, z + 0.47F, x + 0.65F, y + 0.63F, z + 0.53F);
		// The screwdriver's red and black handle and its shaft, sticking out at the lid's edge.
		b.colour(0xFFD8B020).tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(x + 0.62F, y + 0.53F, z + 0.27F, x + 0.82F, y + 0.57F, z + 0.31F);
		b.white().tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.box(x + 0.82F, y + 0.54F, z + 0.284F, x + 0.95F, y + 0.556F, z + 0.296F);
	}

	/** Phase 26: a repeatable noise 0..1 from two numbers. */
	private static float hash(int a, int b) {
		int h = a * 0x27D4EB2D ^ b * 0x165667B1;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	/** Phase 24: her TIE has left (in her film) and is not drawn parked again. */
	private static boolean tieLeft;

	static void animate(MeshBuilder b, float seconds, InteriorVisuals.PlotView plot) {
		boolean story = StoryClient.storyPlot(plot.origin());
		BlockPos o = plot.origin();
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		collapse(b, seconds, plot, story);
		serviceLights(b, seconds, story);
		// Phase 30: the reactor's lift on its way: light running along its posts.
		LiftHud.lights(b, seconds, plot);

		// The cells' force fields, while their barriers stand.
		// Phase 26: the one M-31 works on stutters first (stripes drop out, the whole field blinks,
		// more and more often, brighter where the sparks fly) before it dies.
		float flicker = story ? StoryClient.effect("field_flicker", 0.0F) : -1.0F;

		for (int i = 0; i < CELLS.length; i++) {
			int[] cell = CELLS[i];

			if (plot.level().getBlockState(o.offset(cell[0], (int) H, CELL_FRONT)).is(Blocks.BARRIER)) {
				float x0 = cell[0];
				float x1 = cell[1] + 1;

				if (i == 1 && flicker >= 0.0F) {
					fieldFlicker(b, x0, x1, seconds, flicker);
					continue;
				}

				b.layer(Layer.HOLO).fullBright().colourAlpha(0x5878B8FF).tile(InteriorAtlas.IMP_FORCE_FIELD_GLOW, 1.0F).anim(InteriorScene.ANIM_SHIMMER)
						.twoSided(true);
				b.rect(x0, H + 2.86F, CELL_FRONT + 0.5F, x1 - x0, 0, 0, 0, -2.74F, 0);
				b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
			}
		}

		// Phase 27: the security barrier before the lift to the bridge, while its cells stand.
		if (story && plot.level().getBlockState(o.offset(com.phiro.sabercraft.story.Chapter1.BRIDGE_BARRIER_X0, (int) H,
				com.phiro.sabercraft.story.Chapter1.BRIDGE_BARRIER_Z)).is(Blocks.BARRIER)) {
			bridgeBarrier(b, seconds);
		}

		// Phase 28: Mission 2's side way: its cover (shut while its cell stands; off, it lies in the
		// crawl way, tipping over as it comes off) and the technicians' toolbox by the containers.
		if (story) {
			BlockPos coverCell = com.phiro.sabercraft.story.Chapter1.VENT_COVER;
			boolean shut = plot.level().getBlockState(o.offset(coverCell)).is(Blocks.BARRIER);
			ventCover(b, seconds, shut, StoryClient.effect("cover_off", 0.0F));

			if (plot.level().getBlockState(o.offset(com.phiro.sabercraft.story.Chapter1.TOOLBOX)).is(Blocks.BARRIER)) {
				toolbox(b, StoryClient.has(com.phiro.sabercraft.story.Chapter1.DRIVER));
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
		float lamp = (float) (com.phiro.sabercraft.interior.DestroyerInterior.SECURITY_DESK_Z + 8.5);
		b.box(wall - 0.25F, H + 0.66F, -8.0F + lamp, wall - 0.22F, H + 0.76F, -7.8F + lamp, MeshBuilder.WEST);
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

		lab(b, seconds, story, plot);

		// Phase 24: her TIE fighter in the hangar for the duel: parked on its wings, then (in the film
		// of her flight) lifting off and gone through the field.
		if (!story || !StoryClient.has("tie.parked")) {
			tieLeft = false;
		} else {
			float t = com.phiro.sabercraft.client.story.StoryCinema.time("duel_end");
			float[] tie = EscapeFilm.hangarTie(t);
			// (Once it has gone it does not stand there again for the moment until the server says so.)
			tieLeft |= tie == null;

			if (tie != null && !tieLeft) {
				EscapeFilm.drawHangarTie(b, tie);
			}

			b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		}
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
	private static void lab(MeshBuilder b, float seconds, boolean story, InteriorVisuals.PlotView plot) {
		boolean taken = story && StoryClient.has("saber.taken");
		// Phase 25: what glows in the lab lights the floor round it: the saber's field green, the
		// tracker magenta (only a weak flicker once it fails), the message's figure blue.
		float fieldX = SABER_FIELD.getX() + 0.5F;
		float fieldZ = SABER_FIELD.getZ() + 0.5F;

		if (!taken) {
			floorLight(b, plot, fieldX, R, fieldZ, 0.3F, 3.2F, 0xFF1C6A50, InteriorScene.ANIM_SHIMMER);
			floorLight(b, plot, (float) TRACKER_X, R, (float) TRACKER_Z, (float) TRACKER_R - 0.2F, (float) TRACKER_R + 2.6F, 0xFF5A1E56, 0);
		} else {
			floorLight(b, plot, (float) TRACKER_X, R, (float) TRACKER_Z, (float) TRACKER_R - 0.2F, (float) TRACKER_R + 1.6F, 0xFF3A1030,
					InteriorScene.ANIM_FLICKER);
		}

		if (story && StoryClient.mission() == 5 && StoryClient.has("lab.open") && !taken) {
			floorLight(b, plot, HOLO.getX() + 0.5F, R, HOLO.getZ() + 0.5F, 0.25F, 2.2F, 0xFF1C3A7C, InteriorScene.ANIM_SHIMMER);
		}

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

	/**
	 * Phase 25: the light of something glowing on the open floor round it (added light, fading out
	 * from {@code inner} to {@code radius}): on each floor cell near it where nothing stands, just
	 * over the floor's plates.
	 */
	static void floorLight(MeshBuilder b, InteriorVisuals.PlotView plot, float x, float y, float z, float inner, float radius, int colour,
			int anim) {
		Vector3f at = new Vector3f(x, y, z);
		b.layer(Layer.LIGHT).fullBright().anim(anim).colour(colour).fit(InteriorAtlas.WHITE).lightFrom(at, at, inner, radius);
		int reach = Mth.ceil(radius);
		int fy = Mth.floor(y);

		for (int cx = Mth.floor(x) - reach; cx <= Mth.floor(x) + reach; cx++) {
			for (int cz = Mth.floor(z) - reach; cz <= Mth.floor(z) + reach; cz++) {
				float dx = Math.max(0.0F, Math.abs(cx + 0.5F - x) - 0.5F);
				float dz = Math.max(0.0F, Math.abs(cz + 0.5F - z) - 0.5F);
				BlockPos cell = plot.origin().offset(cx, fy, cz);

				if (dx * dx + dz * dz >= radius * radius || !plot.level().getBlockState(cell).isAir() || !plot.level().getBlockState(cell.below()).canOcclude()) {
					continue;
				}

				Skin.face(b, cx, fy, cz, net.minecraft.core.Direction.DOWN, Skin.EPS * 3, 0.0F, 0.0F, 1.0F, 1.0F);
			}
		}

		b.noLight().layer(Layer.SOLID).worldLight().white().anim(0);
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
		// Phase 25: she is light, not a picture: a faint glow round her, bands of brighter light
		// running up through her, and now and then the whole picture jumps.
		float top = (1.38F + CrewModels.HEAD) * k;
		b.layer(Layer.HOLO).fullBright().anim(InteriorScene.ANIM_SHIMMER).twoSided(true).colourAlpha(0x186A98F0).fit(InteriorAtlas.IMP_HOLO);
		b.cylinder(new Vector3f(x + flicker, y + 0.22F, z), new Vector3f(0, top + 0.08F, 0), 0.3F, 0.22F, 14, 1.0F, false, false);
		b.anim(0).colourAlpha(0x387AB0FF);

		for (int i = 0; i < 2; i++) {
			float rise = (seconds * 0.4F + i * 0.5F) % 1.0F;
			float band = Mth.lerp(rise, 0.24F, 0.13F);
			b.cylinder(new Vector3f(x + flicker, y + 0.25F + rise * top, z), new Vector3f(0, 0.03F, 0), band, band, 14, 1.0F, false, false);
		}

		b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
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
