package com.phiro.sabercraft.client.interior;

import static com.phiro.sabercraft.interior.FalconInterior.*;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.network.InteriorJumpPayload;

/**
 * Phase 16: a hyperspace jump seen from inside the Falcon (through the cockpit window, the
 * turrets' domes): first the stars stretch into lines, then a few seconds in hyperspace with the
 * lines rushing past in a pale blue tunnel, then they shrink back to stars. And what the ship
 * shows of it: the light by the hyperdrive lever (green ready, blinking amber while the drive
 * spools up, blue in hyperspace) and the hyperdrive's glow in the engine room.
 */
final class FalconJump {
	/**
	 * The stars that stretch: angle round the line the cockpit looks along, distance from it, place
	 * along it. They lie only ahead of the ship (north of {@link #FRONT}), so none passes through
	 * the hull or the cockpit: what the jump looks like is what the pilots see through the window.
	 */
	private static final int STARS = 420;
	private static final float[] ANGLE = new float[STARS];
	private static final float[] RADIUS = new float[STARS];
	private static final float[] ALONG = new float[STARS];
	/** Where the space ahead of the ship starts (plot z; the whole interior lies south of it). */
	static final float FRONT = -1.0F;
	/** How far ahead the stars reach. */
	static final float SPAN = 320.0F;
	/** The line the cockpit looks along. */
	static final float AXIS_X = (float) COCKPIT_X;
	static final float AXIS_Y = (float) COCKPIT_AXIS;
	/** The tunnel of light round that line. */
	static final float TUNNEL_R = 22.0F;

	/** Phase 17: the shell round the whole ship in hyperspace: its middle (plot x, z) and radius. */
	static final float SHIP_X = 24.0F;
	static final float SHIP_Z = 25.0F;
	static final float SHELL_R = 95.0F;
	/** Phase 17: the streaks along the ship's sides: round its middle, clear of the hull. */
	private static final int SIDE = 260;
	private static final float SIDE_FRONT = -90.0F;
	private static final float SIDE_BACK = 120.0F;
	private static final float[] SIDE_ANGLE = new float[SIDE];
	private static final float[] SIDE_RADIUS = new float[SIDE];
	private static final float[] SIDE_ALONG = new float[SIDE];

	static {
		java.util.Random random = new java.util.Random(1977L);

		for (int i = 0; i < SIDE; i++) {
			SIDE_ANGLE[i] = random.nextFloat() * Mth.TWO_PI;
			SIDE_RADIUS[i] = 40.0F + random.nextFloat() * 50.0F;
			SIDE_ALONG[i] = random.nextFloat() * (SIDE_BACK - SIDE_FRONT);
		}

		for (int i = 0; i < STARS; i++) {
			ANGLE[i] = random.nextFloat() * Mth.TWO_PI;
			RADIUS[i] = 5.0F + random.nextFloat() * random.nextFloat() * 60.0F;
			ALONG[i] = random.nextFloat() * SPAN;
		}
	}

	/** Phase 17: how opaque the shell was when last drawn (0 = not drawn), for tests. */
	static float shellDrawn;

	private FalconJump() {
	}

	static void draw(MeshBuilder b, InteriorClient.@Nullable Jump jump, float p, float seconds) {
		byte stage = jump != null ? jump.stage() : -1;
		shellDrawn = 0.0F;
		lamp(b, stage, seconds);

		if (jump == null) {
			return;
		}

		// Phase 17: in hyperspace nothing of the space outside shows anywhere one looks (through the
		// cockpit's window to the sides, the turrets' domes): the whole ship inside a deep blue
		// shell, lines of light rushing past it on every side. Once it is closed it is drawn solid
		// (drawn first and hiding what is behind it, so the glowing lines and the rings in front of
		// it show); only while it fades in or out it is translucent, drawn with the glowing lines
		// (that pass comes before the window's glass; the translucent pass after it would be hidden
		// behind the glass).
		boolean shell = stage == InteriorJumpPayload.TUNNEL || stage == InteriorJumpPayload.ARRIVE && p < 0.25F;
		float cover = 0.0F;

		if (shell) {
			cover = stage == InteriorJumpPayload.TUNNEL ? Math.min(1.0F, p * 8.0F) : 1.0F - p * 4.0F;
			cover = Mth.clamp(cover, 0.0F, 1.0F);
			int alpha = (int) (255 * cover);
			shellDrawn = cover;

			if (cover >= 0.999F) {
				b.layer(Layer.SOLID).fullBright().anim(0).twoSided(true).colour(0xFF0A1438).fit(InteriorAtlas.WHITE);
			} else {
				b.layer(Layer.GLOW).fullBright().anim(0).twoSided(true).colourAlpha(alpha << 24 | 0x0A1438).fit(InteriorAtlas.WHITE);
			}

			b.sphere(new Vector3f(SHIP_X, AXIS_Y, SHIP_Z), SHELL_R, 32, 16);
		}

		float length;
		float travel = 0.0F;
		float bright;

		switch (stage) {
			case InteriorJumpPayload.CHARGE -> {
				length = 0.3F + 26.0F * p * p;
				bright = 0.5F + 0.5F * p;
			}
			case InteriorJumpPayload.TUNNEL -> {
				length = 70.0F;
				travel = seconds * 340.0F;
				bright = 1.0F;
			}
			default -> {
				float q = 1.0F - p;
				length = 0.3F + 70.0F * q * q * q;
				bright = 0.4F + 0.6F * q;
			}
		}

		// The lines of light, crossed quads along the ship's axis (north is where it flies).
		int grey = (int) (255 * bright);
		int colour = 0xFF000000 | (int) (grey * 0.85F) << 16 | (int) (grey * 0.92F) << 8 | grey;
		b.layer(Layer.GLOW).fullBright().anim(0).colour(colour).fit(InteriorAtlas.WHITE).twoSided(true);

		for (int i = 0; i < STARS; i++) {
			// Distance ahead of the ship's front, wrapping round as the lines rush past.
			float ahead = ((ALONG[i] - travel) % SPAN + SPAN) % SPAN;
			float x = AXIS_X + Mth.cos(ANGLE[i]) * RADIUS[i];
			float y = AXIS_Y + Mth.sin(ANGLE[i]) * RADIUS[i];
			float w = 0.05F + RADIUS[i] * 0.004F;
			float z0 = FRONT - ahead;
			float l = Math.min(length, ahead);

			if (l > 0.05F) {
				b.rect(x - w, y, z0, 2.0F * w, 0, 0, 0, 0, l);
				b.rect(x, y - w, z0, 0, 2.0F * w, 0, 0, 0, l);
			}
		}

		// The tunnel ahead of the ship while in hyperspace, fading in and out.
		if (stage == InteriorJumpPayload.TUNNEL) {
			float fade = Math.min(1.0F, Math.min(p * 6.0F, (1.0F - p) * 6.0F));
			int alpha = (int) (150 * fade);
			// Phase 17: with the glowing lines (drawn before the window's glass, so they show through it).
			b.layer(Layer.GLOW).fullBright().twoSided(true).colourAlpha(alpha << 24 | 0x5080FF).fit(InteriorAtlas.FAL_HOLO_GLOW);
			// In rings along the way, so that the rushing shows on the walls too.
			for (int ring = 0; ring < 16; ring++) {
				float z = FRONT - (((ring * 20.0F - travel * 0.5F) % SPAN + SPAN) % SPAN);
				b.cylinder(new Vector3f(AXIS_X, AXIS_Y, z), new Vector3f(0, 0, -10.0F), TUNNEL_R, TUNNEL_R, 24, 6.0F, false, false);
			}
		}

		// Phase 17: the streaks along the sides, over and under the ship.
		if (shell) {
			int line = (int) (255 * cover);
			b.layer(Layer.GLOW).fullBright().anim(0).twoSided(true).colour(0xFF000000 | (int) (line * 0.7F) << 16 | (int) (line * 0.82F) << 8 | line)
					.fit(InteriorAtlas.WHITE);
			float rush = stage == InteriorJumpPayload.TUNNEL ? seconds * 340.0F : seconds * 60.0F;

			for (int i = 0; i < SIDE; i++) {
				float span = SIDE_BACK - SIDE_FRONT;
				float z = SIDE_FRONT + ((SIDE_ALONG[i] - rush) % span + span) % span;
				float x = SHIP_X + Mth.cos(SIDE_ANGLE[i]) * SIDE_RADIUS[i];
				float y = AXIS_Y + Mth.sin(SIDE_ANGLE[i]) * SIDE_RADIUS[i];
				float w = 0.08F + SIDE_RADIUS[i] * 0.003F;
				float l = Math.min(26.0F, SIDE_BACK - z);
				b.rect(x - w, y, z, 2.0F * w, 0, 0, 0, 0, l);
				b.rect(x, y - w, z, 0, 2.0F * w, 0, 0, 0, l);
			}
		}

		b.twoSided(false).white().anim(0).layer(Layer.SOLID).worldLight();

		// The hyperdrive glows brighter while it works.
		float glow = stage == InteriorJumpPayload.CHARGE ? p : stage == InteriorJumpPayload.TUNNEL ? 0.8F + 0.2F * Mth.sin(seconds * 9.0F) : 1.0F - p;
		int a = (int) (110 * Mth.clamp(glow, 0.0F, 1.0F));
		b.layer(Layer.HOLO).fullBright().colourAlpha(a << 24 | 0x70B0FF).fit(InteriorAtlas.FAL_HYPERDRIVE_GLOW).twoSided(true);
		b.cylinder(new Vector3f((float) DRIVE_X, FLOOR + 1.05F, (float) DRIVE_Z), new Vector3f(0, 3.6F, 0), (float) DRIVE_R + 0.25F, (float) DRIVE_R + 0.25F,
				20, 3.0F, false, false);
		b.twoSided(false).white().layer(Layer.SOLID).worldLight();
	}

	/** The light by the hyperdrive lever: green ready, blinking amber spooling up, blue in hyperspace. */
	private static void lamp(MeshBuilder b, byte stage, float seconds) {
		int colour = switch (stage) {
			case InteriorJumpPayload.CHARGE -> (seconds * 6.0F) % 1.0F < 0.5F ? 0xFFFFB030 : 0xFF503010;
			case InteriorJumpPayload.TUNNEL, InteriorJumpPayload.ARRIVE -> 0xFF60A0FF;
			default -> 0xFF50E070;
		};
		float cx = (float) COCKPIT_X;
		float z0 = COCKPIT_FRONT;
		float y0 = FLOOR + 1;
		b.layer(Layer.GLOW).fullBright().anim(0).colour(colour).fit(InteriorAtlas.WHITE);
		b.box(cx + 0.35F, y0 + 0.86F, z0 + 1.3F, cx + 0.7F, y0 + 0.94F, z0 + 1.6F, MeshBuilder.ALL & ~MeshBuilder.DOWN);
		b.white().layer(Layer.SOLID).worldLight();
	}
}
