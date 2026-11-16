package com.phiro.sabercraft.client.interior;

import java.util.List;
import java.util.Random;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.ship.ShipModels;
import com.phiro.sabercraft.ship.ShipType;

/**
 * Phase 20: the Falcon's arrival at the Tenebris as a short film (played by
 * {@link com.phiro.sabercraft.client.story.StoryCinema}), in four shots:
 *
 * <ol>
 * <li>Out of hyperspace: a flash, the Falcon shrinks from a streak of light to a ship and flies on
 * towards the Tenebris, whose bow fills the background.</li>
 * <li>Under the hull: from below and behind, the Falcon ducks under the edge of the hull, which
 * spans the whole sky above it (the Tenebris is drawn about 25 Falcons long).</li>
 * <li>The approach: from outside, the Falcon swings round and slides sideways under the edge to
 * the old maintenance airlock.</li>
 * <li>Docking: close by the hull, the docking collar reaches out, the clamps close, the light turns
 * green; then black.</li>
 * </ol>
 *
 * <p>Everything here is in scene space: the Tenebris at its origin (the flyable model, drawn
 * {@value #SD_SCALE} times as large), its bow to -z. The film is drawn far from every ship's plot
 * (see {@code StoryCinema}), and only on this client: the player stays where they were in the
 * Falcon meanwhile.
 */
public final class ArrivalFilm {
	/** How long the film is (ticks). */
	public static final int LENGTH = 460;
	/** When each shot starts (ticks). */
	public static final int[] CUTS = {0, 115, 235, 350};
	/** How much larger than the flyable model the Tenebris is drawn. */
	static final float SD_SCALE = 4.0F;

	// The film's moments (ticks).
	static final int COLLAR_OUT = 358;
	static final int COLLAR_IN = 388;
	static final int CLAMPED = 390;
	static final int GREEN = 398;

	/** Where the Falcon's model stands (time, x, y, z): out of hyperspace, under the hull, round, docked. */
	private static final float[][] FALCON = {
			{0, 64, 0, -224}, {10, 55, 1, -200}, {40, 46, 2, -168}, {115, 38, 0, -110}, {175, 30, -2, -48}, {235, 37, -1, 0},
			{265, 48, 0.5F, 14}, {300, 57, 2.5F, 25}, {330, 53, 3.3F, 24.4F}, {350, 50.9F, 3.475F, 24.0F}, {460, 50.9F, 3.475F, 24.0F}};
	/** Which way it points (degrees round y; 180 = nose to +z, 360 = nose to -z), and how it banks. */
	private static final float[][] HEADING = {{0, 180}, {235, 182}, {255, 195}, {300, 350}, {320, 360}, {460, 360}};
	private static final float[][] ROLL = {{0, 0}, {110, 0}, {150, -8}, {200, 6}, {235, 0}, {262, -20}, {292, 14}, {325, 0}, {460, 0}};

	/** The camera of each shot: where it is, and what it looks at (null: follows the Falcon). */
	private static final float[][][] CAMERA = {
			{{0, 58, -6, -234}, {115, 53, -5, -222}},
			{{115, 52, -9, -142}, {235, 44, -8, -40}},
			{{235, 92, 1, 46}, {350, 86, 2.5F, 40}},
			{{350, 47.0F, 0.8F, 13.0F}, {460, 46.6F, 1.2F, 15.5F}}};
	private static final float[][] LOOK_FIRST = {{0, 62, -1, -222}, {45, 34, 4, -150}, {115, 16, 8, -105}};
	private static final float[][] LOOK_LAST = {{350, 45.2F, 4.4F, 24.75F}, {460, 45.1F, 4.6F, 24.75F}};

	/** The airlock: the side of the hull's dark lower band there, its middle (scene space). */
	static final float WALL_X = 44.06F;
	static final float PORT_Y = 4.6F;
	static final float PORT_Z = 24.75F;
	/** The Falcon's side the collar reaches, its saucer's middle above the model's ground. */
	static final float FALCON_SIDE = 45.9F;
	static final float FALCON_MIDDLE = 1.125F;
	/** The Falcon's nose (model z), the streak out of hyperspace grows behind it. */
	private static final float NOSE = -5.9F;

	private static final Mesh STARS = new Mesh();
	private static final Mesh HULL_LIGHTS = new Mesh();
	private static final Mesh MOVING = new Mesh();
	private static final Mesh MOVING_HOLO = new Mesh();
	private static final Mesh MOVING_SOLID = new Mesh();
	private static @Nullable List<ItemStackRenderState> destroyer;
	private static @Nullable List<ItemStackRenderState> falcon;
	private static boolean built;
	private static int drawn;

	private ArrivalFilm() {
	}

	/** Where the camera is and looks at a time of the film. */
	public record Shot(Vec3 position, float yaw, float pitch) {
	}

	// --- The camera ------------------------------------------------------------------------------

	/** The camera at that time (scene space). */
	public static Shot camera(float t) {
		int shot = shot(t);
		float[] at = track(CAMERA[shot], t, new float[3]);
		float[] look;

		switch (shot) {
			case 0 -> look = track(LOOK_FIRST, t, new float[3]);
			case 1 -> {
				Vec3 f = falcon(t);
				look = new float[] {(float) f.x - 8.0F, (float) f.y + 10.0F, (float) f.z + 28.0F};
			}
			case 2 -> {
				Vec3 f = falcon(t);
				float k = Mth.clamp((t - 300.0F) / 50.0F, 0.0F, 1.0F);
				look = new float[] {Mth.lerp(k, (float) f.x - 4.0F, 47.0F), Mth.lerp(k, (float) f.y + 1.5F, PORT_Y), Mth.lerp(k, (float) f.z - 1.0F, PORT_Z)};
			}
			default -> look = track(LOOK_LAST, t, new float[3]);
		}

		// A jolt out of hyperspace, a small one as the clamps close.
		float shake = t < 14.0F ? 0.35F * (1.0F - t / 14.0F) : t >= CLAMPED && t < CLAMPED + 12 ? 0.08F * (1.0F - (t - CLAMPED) / 12.0F) : 0.0F;

		if (shake > 0.0F) {
			at[0] += Mth.sin(t * 2.7F) * shake;
			at[1] += Mth.sin(t * 3.9F + 1.0F) * shake;
			at[2] += Mth.sin(t * 2.1F + 2.0F) * shake * 0.5F;
		}

		double dx = look[0] - at[0];
		double dy = look[1] - at[1];
		double dz = look[2] - at[2];
		float yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		return new Shot(new Vec3(at[0], at[1], at[2]), yaw, pitch);
	}

	/** Which shot runs at that time. */
	public static int shot(float t) {
		int shot = 0;

		for (int i = 1; i < CUTS.length; i++) {
			if (t >= CUTS[i]) {
				shot = i;
			}
		}

		return shot;
	}

	/** Where the Falcon's model stands at that time (scene space). */
	static Vec3 falcon(float t) {
		float[] at = track(FALCON, t, new float[3]);
		return new Vec3(at[0], at[1], at[2]);
	}

	/**
	 * A smooth way through keys (time, then values): cubic Hermite curves with Catmull-Rom slopes.
	 * The first key keeps the speed it starts with; a key where the values hold (the same as the
	 * next or the one before) and the last key are still.
	 */
	static float[] track(float[][] keys, float t, float[] out) {
		int n = keys.length;
		int dims = keys[0].length - 1;

		if (t <= keys[0][0] || n == 1) {
			System.arraycopy(keys[0], 1, out, 0, dims);
			return out;
		} else if (t >= keys[n - 1][0]) {
			System.arraycopy(keys[n - 1], 1, out, 0, dims);
			return out;
		}

		int i = 0;

		while (keys[i + 1][0] < t) {
			i++;
		}

		float h = keys[i + 1][0] - keys[i][0];
		float s = (t - keys[i][0]) / h;
		float s2 = s * s;
		float s3 = s2 * s;
		float h00 = 2.0F * s3 - 3.0F * s2 + 1.0F;
		float h10 = s3 - 2.0F * s2 + s;
		float h01 = -2.0F * s3 + 3.0F * s2;
		float h11 = s3 - s2;

		for (int d = 1; d <= dims; d++) {
			out[d - 1] = h00 * keys[i][d] + h10 * h * slope(keys, i, d) + h01 * keys[i + 1][d] + h11 * h * slope(keys, i + 1, d);
		}

		return out;
	}

	private static float slope(float[][] keys, int i, int d) {
		int n = keys.length;

		if (i == n - 1 || holds(keys, i, i + 1) || i > 0 && holds(keys, i - 1, i)) {
			return 0.0F;
		} else if (i == 0) {
			return (keys[1][d] - keys[0][d]) / (keys[1][0] - keys[0][0]);
		}

		return (keys[i + 1][d] - keys[i - 1][d]) / (keys[i + 1][0] - keys[i - 1][0]);
	}

	private static boolean holds(float[][] keys, int a, int b) {
		for (int d = 1; d < keys[a].length; d++) {
			if (Math.abs(keys[a][d] - keys[b][d]) > 1.0E-4F) {
				return false;
			}
		}

		return true;
	}

	// --- Drawing ---------------------------------------------------------------------------------

	/**
	 * Draws the film's scene at that time: {@code scene} is where the scene's origin is, relative to
	 * the camera (the pose stack is the camera's).
	 */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 scene, float t) {
		build();
		Minecraft client = Minecraft.getInstance();
		ShipType sd = ShipType.byId("star_destroyer").orElse(null);
		ShipType freighter = ShipType.byId("falcon").orElse(null);

		if (sd == null || freighter == null) {
			return;
		}

		destroyer = ShipModels.resolve(sd, destroyer, client.level, null, 1313);
		falcon = ShipModels.resolve(freighter, falcon, client.level, null, 1977);
		int light = LightCoordsUtil.pack(13, 15);

		// The stars round the camera, as far as the sky.
		submit(collector, poseStack, Layer.GLOW, STARS);

		poseStack.pushPose();
		poseStack.translate(scene.x, scene.y, scene.z);

		// The Tenebris, its windows and engines.
		poseStack.pushPose();
		poseStack.scale(SD_SCALE, SD_SCALE, SD_SCALE);
		ShipModels.submit(poseStack, collector, sd, ShipModels.Pose.FLYING, destroyer, light, 0);
		poseStack.popPose();
		submit(collector, poseStack, Layer.GLOW, HULL_LIGHTS);

		// The Falcon: a streak of light at first, then the ship, its engines glowing.
		Vec3 at = falcon(t);
		float heading = track(HEADING, t, new float[1])[0];
		float roll = track(ROLL, t, new float[1])[0];
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(heading));
		poseStack.translate(0.0F, FALCON_MIDDLE, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
		poseStack.translate(0.0F, -FALCON_MIDDLE, 0.0F);
		float stretch = t < 10.0F ? 1.0F + 36.0F * (float) Math.pow(1.0F - t / 10.0F, 3.0) : 1.0F;

		if (stretch > 1.0F) {
			poseStack.translate(0.0F, 0.0F, NOSE);
			poseStack.scale(1.0F, 1.0F, stretch);
			poseStack.translate(0.0F, 0.0F, -NOSE);
		}

		ShipModels.submit(poseStack, collector, freighter, ShipModels.Pose.FLYING, falcon, light, 0);
		MOVING.clear();
		MOVING_HOLO.clear();
		MOVING_SOLID.clear();
		MeshBuilder glow = builder(MOVING);
		glow.layer(Layer.GLOW).fullBright().twoSided(true);
		engines(glow, t);
		poseStack.popPose();

		submit(collector, poseStack, Layer.GLOW, MOVING);

		// Scene-space things: the flash, the airlock, the collar, the vapour.
		MOVING.clear();
		MeshBuilder scenery = builder(MOVING);
		scenery.layer(Layer.GLOW).fullBright().twoSided(true);
		flash(scenery, at, t);
		airlockLights(scenery, t);
		MeshBuilder solid = builder(MOVING_SOLID);
		solid.layer(Layer.SOLID).fixedLight(11, 15);
		MeshBuilder holo = builder(MOVING_HOLO);
		holo.layer(Layer.HOLO).fullBright().twoSided(true);
		collar(solid, scenery, holo, t);
		submit(collector, poseStack, Layer.SOLID, MOVING_SOLID);
		submit(collector, poseStack, Layer.GLOW, MOVING);
		submit(collector, poseStack, Layer.HOLO, MOVING_HOLO);
		poseStack.popPose();
		drawn++;
	}

	/** Engine glow along the back of the saucer, bright while it flies, low once docked. */
	private static void engines(MeshBuilder b, float t) {
		float power = t < 300.0F ? 1.0F : t < 350.0F ? Mth.lerp((t - 300.0F) / 50.0F, 1.0F, 0.4F) : Math.max(0.06F, 0.4F - (t - 350.0F) / 90.0F);
		int colour = argb(1.0F, 0.55F * power, 0.78F * power, 1.0F * power);
		float y = FALCON_MIDDLE;

		for (float angle = -54.0F; angle <= 54.0F; angle += 9.0F) {
			float a0 = (angle - 4.5F) * Mth.DEG_TO_RAD;
			float a1 = (angle + 4.5F) * Mth.DEG_TO_RAD;
			float r = 5.06F;
			Vector3f p0 = new Vector3f(r * Mth.sin(a0), y - 0.2F, 0.75F + r * Mth.cos(a0));
			Vector3f p1 = new Vector3f(r * Mth.sin(a1), y - 0.2F, 0.75F + r * Mth.cos(a1));
			b.colour(colour).rect(p0, new Vector3f(p1).sub(p0), new Vector3f(0.0F, 0.42F, 0.0F));
		}
	}

	/** Out of hyperspace: a white flash round the Falcon and a fading line of light behind it. */
	private static void flash(MeshBuilder b, Vec3 at, float t) {
		if (t >= 12.0F) {
			return;
		}

		float k = 1.0F - t / 12.0F;
		Vector3f middle = new Vector3f((float) at.x, (float) at.y + FALCON_MIDDLE, (float) at.z);

		if (t < 7.0F) {
			float f = 1.0F - t / 7.0F;
			b.colour(argb(1.0F, 0.7F * f, 0.82F * f, 1.0F * f)).sphere(middle, 1.5F + 5.0F * (1.0F - f), 10, 6);
		}

		// The line it came along (from the far side, behind it).
		float length = 260.0F * k * k;
		b.colour(argb(1.0F, 0.55F * k, 0.75F * k, 1.0F * k));
		b.rect(new Vector3f(middle).add(-0.35F, 0.0F, 0.0F), new Vector3f(0.7F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, -length));
		b.rect(new Vector3f(middle).add(0.0F, -0.35F, 0.0F), new Vector3f(0.0F, 0.7F, 0.0F), new Vector3f(0.0F, 0.0F, -length));
	}

	/** The lights round the airlock: amber, blinking while the Falcon comes, then green. */
	private static void airlockLights(MeshBuilder b, float t) {
		boolean green = t >= GREEN;
		boolean on = green || (int) (t / 6.0F) % 2 == 0;
		int colour = green ? argb(1.0F, 0.35F, 1.0F, 0.45F) : on ? argb(1.0F, 1.0F, 0.62F, 0.18F) : argb(1.0F, 0.25F, 0.15F, 0.05F);
		float x = WALL_X - 0.03F;

		for (float dz : new float[] {-1.7F, 1.7F}) {
			b.colour(colour).rect(new Vector3f(x, PORT_Y + 1.7F, PORT_Z + dz - 0.18F), new Vector3f(0.0F, 0.0F, 0.36F), new Vector3f(0.0F, 0.26F, 0.0F));
		}

		// The frame's edge, faint.
		int edge = argb(1.0F, 0.28F, 0.32F, 0.38F);
		b.colour(edge).rect(new Vector3f(x, PORT_Y - 1.5F, PORT_Z - 1.5F), new Vector3f(0.0F, 0.0F, 3.0F), new Vector3f(0.0F, 0.08F, 0.0F));
		b.colour(edge).rect(new Vector3f(x, PORT_Y + 1.42F, PORT_Z - 1.5F), new Vector3f(0.0F, 0.0F, 3.0F), new Vector3f(0.0F, 0.08F, 0.0F));
	}

	/** The docking collar: it reaches out from the airlock to the Falcon, clamps, and lets off steam. */
	private static void collar(MeshBuilder solid, MeshBuilder glow, MeshBuilder holo, float t) {
		float out = Mth.clamp((t - COLLAR_OUT) / (float) (COLLAR_IN - COLLAR_OUT), 0.0F, 1.0F);
		out = out * out * (3.0F - 2.0F * out);
		float reach = (FALCON_SIDE + 0.3F - WALL_X) * out;
		Vector3f base = new Vector3f(WALL_X - 0.2F, PORT_Y, PORT_Z);

		// The ring round the airlock, then the collar itself.
		solid.colour(0xFF3A3F47).cylinder(new Vector3f(base), new Vector3f(0.45F, 0.0F, 0.0F), 1.45F, 1.45F, 16, 1.0F, false, true);

		if (reach > 0.05F) {
			solid.colour(0xFF545A63).cylinder(new Vector3f(base).add(0.45F, 0.0F, 0.0F), new Vector3f(Math.max(0.05F, reach - 0.25F), 0.0F, 0.0F), 1.05F,
					1.05F, 16, 1.0F, false, true);
			// Light rings along it, amber until it holds.
			int colour = t >= GREEN ? argb(1.0F, 0.35F, 1.0F, 0.45F) : argb(1.0F, 1.0F, 0.6F, 0.2F);
			glow.colour(colour).cylinder(new Vector3f(base).add(0.45F + reach * 0.5F, 0.0F, 0.0F), new Vector3f(0.12F, 0.0F, 0.0F), 1.09F, 1.09F, 16,
					1.0F, false, false);
		}

		// Steam where the clamps closed.
		if (t >= CLAMPED && t < CLAMPED + 36) {
			float k = (t - CLAMPED) / 36.0F;
			int alpha = Math.round((1.0F - k) * 150.0F);

			for (int i = 0; i < 5; i++) {
				float angle = i * 1.256F + 0.4F;
				Vector3f puff = new Vector3f(FALCON_SIDE - 0.2F, PORT_Y + Mth.sin(angle) * (1.1F + k * 1.6F), PORT_Z + Mth.cos(angle) * (1.1F + k * 1.6F));
				holo.colourAlpha(alpha << 24 | 0xDCE4EC).sphere(puff, 0.35F + k * 0.9F, 8, 5);
			}
		}
	}

	// --- Built once ------------------------------------------------------------------------------

	private static void build() {
		if (built) {
			return;
		}

		built = true;
		Random random = new Random(1313L);
		MeshBuilder stars = builder(STARS);
		stars.layer(Layer.GLOW).fullBright().twoSided(true);

		for (int i = 0; i < 900; i++) {
			// Evenly over the sphere, a little denser in a band (the galaxy).
			float y = random.nextFloat() * 2.0F - 1.0F;
			float lon = random.nextFloat() * Mth.TWO_PI;

			if (i % 3 == 0) {
				y = (random.nextFloat() - 0.5F) * 0.35F;
			}

			float r = Mth.sqrt(1.0F - y * y);
			Vector3f dir = new Vector3f(r * Mth.cos(lon), y, r * Mth.sin(lon));
			float distance = 620.0F;
			float size = (0.9F + random.nextFloat() * random.nextFloat() * 2.6F) * distance / 620.0F;
			float bright = 0.35F + random.nextFloat() * 0.65F;
			float warm = random.nextFloat();
			int colour = argb(1.0F, bright * (warm < 0.2F ? 1.0F : 0.82F), bright * 0.88F, bright * (warm > 0.75F ? 1.0F : 0.86F));
			Vector3f side = Math.abs(dir.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F).cross(dir).normalize() : new Vector3f(1.0F, 0.0F, 0.0F).cross(dir).normalize();
			Vector3f up = new Vector3f(dir).cross(side).normalize();
			Vector3f centre = new Vector3f(dir).mul(distance);
			stars.colour(colour).rect(new Vector3f(centre).sub(new Vector3f(side).mul(size / 2.0F)).sub(new Vector3f(up).mul(size / 2.0F)),
					new Vector3f(side).mul(size), new Vector3f(up).mul(size));
		}

		// A far sun behind the Tenebris, and its glow.
		Vector3f sun = new Vector3f(-0.55F, 0.32F, 0.77F).normalize();
		Vector3f side = new Vector3f(0.0F, 1.0F, 0.0F).cross(sun).normalize();
		Vector3f up = new Vector3f(sun).cross(side).normalize();

		for (float[] disc : new float[][] {{16.0F, 1.0F}, {44.0F, 0.32F}, {110.0F, 0.1F}}) {
			Vector3f centre = new Vector3f(sun).mul(600.0F);
			float size = disc[0];
			stars.colour(argb(1.0F, disc[1], disc[1] * 0.86F, disc[1] * 0.62F)).rect(
					new Vector3f(centre).sub(new Vector3f(side).mul(size / 2.0F)).sub(new Vector3f(up).mul(size / 2.0F)), new Vector3f(side).mul(size),
					new Vector3f(up).mul(size));
		}

		// The Tenebris' windows along its flanks (tiny: the ship is huge), its bridge and engines.
		MeshBuilder lights = builder(HULL_LIGHTS);
		lights.layer(Layer.GLOW).fullBright().twoSided(true);
		float s = SD_SCALE;

		for (float z = -36.0F; z < 31.0F; z += 0.9F) {
			float zb = z < 0.0F ? Math.min(0.0F, -40.0F + (float) Math.ceil((z + 40.0F) / 2.4F) * 2.4F) : Math.min(32.0F, (float) Math.ceil(z / 2.4F) * 2.4F);
			float half = 21.0F * (Math.max(zb, z + 0.01F) - -40.0F) / 72.0F;
			float k = 0.35F + 0.65F * (zb - -40.0F) / 72.0F;

			for (int row = 0; row < 2; row++) {
				if (random.nextFloat() < 0.45F) {
					continue;
				}

				float y = (3.6F - (row == 0 ? 0.7F : 1.5F) * k) * s;
				int colour = random.nextFloat() < 0.15F ? argb(1.0F, 0.6F, 0.75F, 1.0F) : argb(1.0F, 0.95F, 0.9F, 0.72F);

				for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
					float x = sideSign * (half * s + 0.04F);
					lights.colour(colour).rect(new Vector3f(x, y, z * s), new Vector3f(0.0F, 0.0F, 0.9F), new Vector3f(0.0F, 0.32F, 0.0F));
				}
			}
		}

		// The bridge's long window.
		lights.colour(argb(1.0F, 0.95F, 0.88F, 0.66F)).rect(new Vector3f(-8.0F * s, 13.6F * s, 21.88F * s), new Vector3f(16.0F * s, 0.0F, 0.0F),
				new Vector3f(0.0F, 0.6F * s, 0.0F));

		// The engines: three big, four small, glowing blue out of the stern.
		for (float[] engine : new float[][] {{0.0F, 3.0F, 2.9F}, {-7.0F, 3.0F, 2.4F}, {7.0F, 3.0F, 2.4F}, {-3.6F, 1.0F, 1.1F}, {3.6F, 1.0F, 1.1F},
				{-11.5F, 2.6F, 1.1F}, {11.5F, 2.6F, 1.1F}}) {
			float z = (engine[2] > 2.0F ? 33.95F : 33.25F) * s;
			Vector3f centre = new Vector3f(engine[0] * s, engine[1] * s, z);
			float r = engine[2] * 0.82F * s;
			lights.colour(argb(1.0F, 0.55F, 0.78F, 1.0F)).cylinder(centre, new Vector3f(0.0F, 0.0F, 0.05F), r, r, 16, 1.0F, true, true);
			lights.colour(argb(1.0F, 0.18F, 0.3F, 0.55F)).cylinder(new Vector3f(centre).add(0.0F, 0.0F, 0.1F), new Vector3f(0.0F, 0.0F, 0.05F), r * 1.35F,
					r * 1.35F, 16, 1.0F, true, true);
		}

		// The hangar's light under the bow, and a few beacons along the edges.
		lights.colour(argb(1.0F, 0.7F, 0.8F, 0.95F)).rect(new Vector3f(-3.0F * s, 0.98F * s, -12.0F * s), new Vector3f(6.0F * s, 0.0F, 0.0F),
				new Vector3f(0.0F, 0.0F, 10.0F * s));

		for (float z : new float[] {-38.0F, -20.0F, 0.0F, 20.0F, 31.0F}) {
			float half = 21.0F * (z + 40.0F) / 72.0F;

			for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
				int colour = sideSign < 0 ? argb(1.0F, 1.0F, 0.25F, 0.2F) : argb(1.0F, 0.35F, 1.0F, 0.4F);
				lights.colour(colour).sphere(new Vector3f(sideSign * half * s, 3.6F * s + 0.3F, z * s), 0.55F, 8, 4);
			}
		}
	}

	// --- Helpers ---------------------------------------------------------------------------------

	private static MeshBuilder builder(Mesh mesh) {
		return new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> mesh.add(xyzuv, nx, ny, nz, colours,
				lightMode, fixedLight, anim, samples));
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, Layer layer, Mesh mesh) {
		if (mesh.isEmpty()) {
			return;
		}

		boolean alpha = layer == Layer.GLASS || layer == Layer.HOLO;
		collector.submitCustomGeometry(poseStack, renderType(layer), (pose, buffer) -> mesh.emit(pose, buffer, (anim, quad) -> 1.0F, alpha));
	}

	private static RenderType renderType(Layer layer) {
		return switch (layer) {
			case SOLID -> RenderTypes.entityCutoutCull(InteriorScene.ATLAS);
			case GLOW -> RenderTypes.eyes(InteriorScene.ATLAS);
			case GLASS -> RenderTypes.bannerPattern(InteriorScene.ATLAS);
			case HOLO -> RenderTypes.entityTranslucentEmissive(InteriorScene.ATLAS);
		};
	}

	private static int argb(float a, float r, float g, float b) {
		return Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F) << 24 | Math.round(Mth.clamp(r, 0.0F, 1.0F) * 255.0F) << 16
				| Math.round(Mth.clamp(g, 0.0F, 1.0F) * 255.0F) << 8 | Math.round(Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
	}

	/** Only for tests: how many frames of the film were drawn. */
	public static int drawnForTest() {
		return drawn;
	}
}
