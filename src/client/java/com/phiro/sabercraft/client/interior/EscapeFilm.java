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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.ship.ShipModels;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.ship.ShipType;

/**
 * Phase 24: the end of the Tenebris, seen from outside (played by
 * {@link com.phiro.sabercraft.client.story.StoryCinema}, drawn far from every plot like the
 * arrival, {@link ArrivalFilm}, whose stars and ship it shares), in four shots:
 *
 * <ol>
 * <li>Out of the hangar: the Falcon drops out of the opening under the bow, its engines flare and it
 * races off; the first blasts break out of the hull.</li>
 * <li>The chase: ahead of the Falcon looking back: behind it the Tenebris, blast after blast along
 * its back, the reactor's fire breaking through amidships.</li>
 * <li>The end: far away, the Falcon coming towards the camera; a chain of blasts, then the core:
 * a white flash, a fireball as large as the ship, a ring of fire racing out, the wreck torn
 * apart.</li>
 * <li>The jump: beside the Falcon as the ring comes; it stretches into a line of light and is
 * gone a moment before the ring sweeps through where it was.</li>
 * </ol>
 *
 * <p>Also the short look from outside in the film of her flight ({@link #tieCamera}): her TIE drops
 * out of the hangar and screams off into space, a blast on the hull behind it.
 *
 * <p>Everything is in scene space as in the arrival: the Tenebris at its origin, drawn
 * {@value ArrivalFilm#SD_SCALE} times as large, its bow to -z.
 */
public final class EscapeFilm {
	/** How long the film is (ticks). */
	public static final int LENGTH = 440;
	/** When each shot starts (ticks). */
	public static final int[] CUTS = {0, 100, 200, 320};
	/** The film's moments (ticks): the core goes, the wreck is gone, the jump, the ring's speed (blocks a tick). */
	public static final int CORE = 262;
	static final int WRECK = 266;
	public static final int JUMP = 360;
	static final float RING_SPEED = 6.0F;
	/** How long the look at her TIE leaving is (ticks, the part of her film drawn out here). */
	public static final int TIE_SHOT = 60;

	/** The Falcon's way (time, x, y, z) and roll. */
	private static final float[][] FALCON = {
			{0, 0, 6, -26}, {14, 0, 1.5F, -27}, {34, 2, -7, -36}, {70, 14, -19, -90}, {100, 30, -25, -160}, {150, 60, -29, -260},
			{200, 95, -31, -360}, {260, 130, -30, -470}, {320, 165, -28, -580}, {360, 185, -27, -640}};
	private static final float[][] ROLL = {{0, 0}, {30, 0}, {50, -18}, {80, 10}, {110, -6}, {170, 14}, {230, -8}, {300, 0}, {360, 0}};

	/** The cameras: where (null: relative to the Falcon), and what they look at. */
	private static final float[][] CAMERA_FIRST = {{0, -18, -11, 10}, {100, -11, -16, -24}};
	private static final float[][] CAMERA_WIDE = {{200, 150, -18, -660}, {320, 158, -21, -672}};

	/** Her TIE in the look from outside: its way (time, x, y, z) and the camera's. */
	private static final float[][] TIE = {{0, 0, 3, -30}, {10, 0.5F, -4, -32}, {25, 6, -14, -44}, {40, 20, -22, -72}, {60, 64, -36, -160}};

	/** The blasts on the hull before the core: (time, x, y, z, size), scene space. */
	private static final float[][] BLASTS;
	/** The pieces of the wreck: start (x, y, z), way per tick (x, y, z), size, turn per tick. */
	private static final float[][] WRECKAGE;

	static {
		Random random = new Random(1977L);
		List<float[]> blasts = new java.util.ArrayList<>();
		// A few on the belly as the Falcon leaves (seen from below), then more and more along the back.
		float[] early = {16, 30, 46, 62, 80, 94};

		for (float t : early) {
			float z = -60.0F + random.nextFloat() * 120.0F;
			float half = hullHalf(z) * 0.7F;
			blasts.add(new float[] {t, (random.nextFloat() * 2.0F - 1.0F) * half, 2.0F + random.nextFloat() * 2.0F, z, 5.0F + random.nextFloat() * 4.0F});
		}

		for (int i = 0; i < 34; i++) {
			float t = 104.0F + i * 4.6F + random.nextFloat() * 3.0F;
			float z = -130.0F + random.nextFloat() * 240.0F;
			float half = hullHalf(z) * 0.8F;
			float size = 6.0F + random.nextFloat() * 8.0F + i * 0.45F;
			blasts.add(new float[] {t, (random.nextFloat() * 2.0F - 1.0F) * half, 13.0F + random.nextFloat() * 8.0F, z, size});
		}

		// The chain before the core: amidships, out towards bow and stern.
		for (int i = 0; i < 8; i++) {
			float t = 238.0F + i * 3.0F;
			float z = (i % 2 == 0 ? -1.0F : 1.0F) * (10.0F + i * 14.0F);
			blasts.add(new float[] {t, (random.nextFloat() - 0.5F) * 30.0F, 18.0F, z, 22.0F + i * 2.0F});
		}

		BLASTS = blasts.toArray(new float[0][]);
		WRECKAGE = new float[48][];

		for (int i = 0; i < WRECKAGE.length; i++) {
			float z = -140.0F + random.nextFloat() * 260.0F;
			float half = hullHalf(z) * 0.9F;
			float x = (random.nextFloat() * 2.0F - 1.0F) * half;
			float y = random.nextFloat() * 18.0F;
			Vector3f out = new Vector3f(x, y - 6.0F, z * 0.6F).normalize().mul(1.6F + random.nextFloat() * 2.8F);
			WRECKAGE[i] = new float[] {x, y, z, out.x, out.y, out.z, 6.0F + random.nextFloat() * 22.0F, (random.nextFloat() - 0.5F) * 6.0F};
		}
	}

	private static final Mesh MOVING = new Mesh();
	private static final Mesh MOVING_SOLID = new Mesh();
	private static final Mesh MOVING_HOLO = new Mesh();
	private static final Mesh ENGINES = new Mesh();
	private static @Nullable List<ItemStackRenderState> destroyer;
	private static @Nullable List<ItemStackRenderState> falcon;
	private static @Nullable List<ItemStackRenderState> tie;
	private static int drawn;

	private EscapeFilm() {
	}

	/** The Tenebris' half width at that z (scene space), as its wedge narrows to the bow. */
	private static float hullHalf(float z) {
		float model = Mth.clamp(z / ArrivalFilm.SD_SCALE, -40.0F, 32.0F);
		return 21.0F * (model + 40.0F) / 72.0F * ArrivalFilm.SD_SCALE;
	}

	// --- The camera ------------------------------------------------------------------------------

	/** The camera at that time (scene space). */
	public static ArrivalFilm.Shot camera(float t) {
		int shot = shot(t);
		Vec3 f = falcon(Math.min(t, JUMP));
		Vec3 cam;
		Vec3 look;

		switch (shot) {
			case 0 -> {
				float[] at = ArrivalFilm.track(CAMERA_FIRST, t, new float[3]);
				cam = new Vec3(at[0], at[1], at[2]);
				// At the opening first, then on the Falcon as it comes out.
				float k = Mth.clamp((t - 6.0F) / 20.0F, 0.0F, 1.0F);
				look = new Vec3(0.0, 4.0, -27.0).lerp(f.add(0.0, 1.0, 0.0), k * k * (3.0F - 2.0F * k));
			}
			case 1 -> {
				// Ahead of it, looking back past it at the burning ship.
				float u = (t - 100.0F) / 100.0F;
				cam = f.add(9.0 - u * 3.0, 3.5 + u * 1.5, -17.0 + u * 2.0);
				look = f.add(0.0, 1.0, 0.0).lerp(new Vec3(0.0, 12.0, 0.0), 0.12 + u * 0.08);
			}
			case 2 -> {
				float[] at = ArrivalFilm.track(CAMERA_WIDE, t, new float[3]);
				cam = new Vec3(at[0], at[1], at[2]);
				look = new Vec3(20.0, 6.0, -60.0);
			}
			default -> {
				// Beside it as the ring comes; it holds where it was once the Falcon is gone.
				Vec3 still = falcon(Math.min(t, JUMP - 2));
				cam = still.add(-10.0, 2.6, 7.5);
				look = still.add(0.0, 1.2, -5.0);
			}
		}

		float shake = shake(t);

		if (shake > 0.0F) {
			cam = cam.add(Mth.sin(t * 2.9F) * shake, Mth.sin(t * 3.7F + 1.0F) * shake, Mth.sin(t * 2.3F + 2.0F) * shake * 0.6F);
		}

		return look(cam, look);
	}

	/** How hard the camera shakes then (the core's blast, the ring sweeping past). */
	private static float shake(float t) {
		float s = 0.0F;

		if (t >= CORE && t < CORE + 30) {
			s = Math.max(s, 1.2F * (1.0F - (t - CORE) / 30.0F));
		}

		if (t >= 372 && t < 400) {
			s = Math.max(s, 0.9F * (1.0F - (t - 372.0F) / 28.0F));
		}

		if (t < 30.0F) {
			s = Math.max(s, 0.12F);
		}

		return s;
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

	/** Where the Falcon's model stands at that time (scene space); after the jump far ahead. */
	static Vec3 falcon(float t) {
		float[] at = ArrivalFilm.track(FALCON, Math.min(t, JUMP), new float[3]);
		Vec3 p = new Vec3(at[0], at[1], at[2]);

		if (t > JUMP) {
			// Into hyperspace: gone ahead in a moment.
			Vec3 d = heading(FALCON, JUMP);
			float k = Math.min(1.0F, (t - JUMP) / 6.0F);
			p = p.add(d.scale(900.0 * k * k));
		}

		return p;
	}

	/** The way something moves at that time on its track (length 1). */
	private static Vec3 heading(float[][] keys, float t) {
		float[] a = ArrivalFilm.track(keys, Math.max(keys[0][0], t - 1.0F), new float[3]);
		float[] b = ArrivalFilm.track(keys, Math.min(keys[keys.length - 1][0], t + 1.0F), new float[3]);
		Vec3 d = new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]);

		if (d.lengthSqr() < 1.0E-6) {
			float[] c = ArrivalFilm.track(keys, keys[keys.length - 2][0], new float[3]);
			float[] e = keys[keys.length - 1];
			d = new Vec3(e[1] - c[0], e[2] - c[1], e[3] - c[2]);
		}

		return d.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : d.normalize();
	}

	/** A camera at that point looking at another. */
	private static ArrivalFilm.Shot look(Vec3 cam, Vec3 at) {
		Vec3 d = at.subtract(cam);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
		return new ArrivalFilm.Shot(cam, yaw, pitch);
	}

	/** Her TIE leaving, from outside: the camera under the hull as it drops out and screams past. */
	public static ArrivalFilm.Shot tieCamera(float t) {
		Vec3 cam = new Vec3(11.0 - t * 0.04, -12.0, -37.0 - t * 0.05);
		Vec3 at = tie(t);
		float k = Mth.clamp((t - 2.0F) / 10.0F, 0.0F, 1.0F);
		Vec3 look = new Vec3(0.0, 3.0, -30.0).lerp(at, k);
		return look(cam, look);
	}

	/** Where her TIE is in the look from outside (scene space). */
	static Vec3 tie(float t) {
		float[] at = ArrivalFilm.track(TIE, Mth.clamp(t, 0.0F, TIE_SHOT), new float[3]);
		return new Vec3(at[0], at[1], at[2]);
	}

	// --- Her TIE in the hangar ---------------------------------------------------------------------

	/** Her film's moments (ticks): it lifts off, glides over the field, drops through it. */
	public static final int TIE_LIFT = 192;
	static final int TIE_GLIDE = 212;
	static final int TIE_DIVE = 236;
	public static final int TIE_GONE = 256;

	/**
	 * Phase 24: where her TIE is in the hangar (plot space) at that time of her film, and how it is
	 * turned: {x, y, z, heading, pitch, roll, engines 0..1}; parked before it lifts (a negative time:
	 * no film runs), null once it is gone through the field.
	 */
	public static float @Nullable [] hangarTie(float t) {
		Vec3 park = DestroyerInterior.ESCAPE_TIE;
		float x = (float) park.x;
		float y = (float) park.y;
		float z = (float) park.z;

		if (t < TIE_LIFT) {
			return new float[] {x, y, z, -90.0F, 0.0F, 0.0F, t > TIE_LIFT - 14 ? 0.4F : 0.0F};
		}

		if (t >= TIE_GONE) {
			return null;
		}

		if (t < TIE_GLIDE) {
			// Up off its wings, rocking a little.
			float u = smooth((t - TIE_LIFT) / (TIE_GLIDE - TIE_LIFT));
			return new float[] {x, y + 1.9F * u, z, -90.0F, Mth.sin(t * 0.4F) * 2.0F * u, Mth.sin(t * 0.3F) * 3.0F * u, 0.5F + 0.3F * u};
		}

		if (t < TIE_DIVE) {
			// Across to the middle of the field, banking into it.
			float u = smooth((t - TIE_GLIDE) / (TIE_DIVE - TIE_GLIDE));
			float gx = DestroyerInterior.FIELD_X0 + 9.0F;
			return new float[] {Mth.lerp(u, x, gx), y + 1.9F - 0.4F * u, Mth.lerp(u, z, z + 1.0F), -90.0F, -6.0F * u, Mth.sin(u * Mth.PI) * -16.0F, 0.85F};
		}

		// Nose down and through the field, faster and faster.
		float u = (t - TIE_DIVE) / (TIE_GONE - TIE_DIVE);
		float gx = DestroyerInterior.FIELD_X0 + 9.0F;
		float drop = 18.0F * u * u;
		return new float[] {gx + 2.5F * u, y + 1.5F - drop, z + 1.0F, -90.0F, -6.0F - 74.0F * smooth(Math.min(1.0F, u * 2.0F)), 0.0F, 1.0F};
	}

	private static float smooth(float u) {
		float x = Mth.clamp(u, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** Draws her TIE in the hangar (plot space) into the moving parts. */
	public static void drawHangarTie(MeshBuilder b, float[] at) {
		if (at[1] < DestroyerInterior.HANGAR - 5.0F) {
			return;
		}

		b.push().translate(at[0], at[1], at[2]).rotateY(at[3]).rotateX(at[4]).rotateZ(at[5]);
		DestroyerRooms.tie(b, 0.0F, 0.0F, 0.0F);

		if (at[6] > 0.0F) {
			// The engines' glow at the back of the ball.
			int glow = ArrivalFilm.argb(1.0F, 0.95F * at[6], 0.45F * at[6], 0.25F * at[6]);
			b.layer(Layer.GLOW).fullBright().twoSided(true).colour(glow).fit(InteriorAtlas.WHITE);
			b.rect(-0.35F, 0.35F, 1.27F, 0.7F, 0.0F, 0.0F, 0.0F, -0.7F, 0.0F);
			b.layer(Layer.SOLID).worldLight().white().twoSided(false);
		}

		b.pop();
	}

	// --- Drawing ---------------------------------------------------------------------------------

	/** Draws the film's scene at that time ({@code scene}: its origin relative to the camera). */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 scene, float t) {
		ArrivalFilm.build();
		Minecraft client = Minecraft.getInstance();
		ShipType sd = ShipType.byId("star_destroyer").orElse(null);
		ShipType freighter = ShipType.byId("falcon").orElse(null);

		if (sd == null || freighter == null) {
			return;
		}

		destroyer = ShipModels.resolve(sd, destroyer, client.level, null, 1313);
		falcon = ShipModels.resolve(freighter, falcon, client.level, null, 1977);
		int light = LightCoordsUtil.pack(13, 15);
		ArrivalFilm.submit(collector, poseStack, Layer.GLOW, ArrivalFilm.STARS);

		poseStack.pushPose();
		poseStack.translate(scene.x, scene.y, scene.z);

		if (t < WRECK) {
			poseStack.pushPose();
			poseStack.scale(ArrivalFilm.SD_SCALE, ArrivalFilm.SD_SCALE, ArrivalFilm.SD_SCALE);
			ShipModels.submit(poseStack, collector, sd, ShipModels.Pose.FLYING, destroyer, light, 0);
			poseStack.popPose();

			// Its lights go out as it burns.
			if (t < 200.0F || (int) (t * 0.7F) % 5 != 0) {
				ArrivalFilm.submit(collector, poseStack, Layer.GLOW, ArrivalFilm.HULL_LIGHTS);
			}
		}

		// The Falcon, its engines glowing (until it is a line of light).
		if (t < JUMP + 6) {
			Vec3 at = falcon(t);
			Vec3 d = heading(FALCON, Math.min(t, JUMP));
			float heading = (float) Math.toDegrees(Math.atan2(-d.x, -d.z));
			float pitch = (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
			float roll = ArrivalFilm.track(ROLL, Math.min(t, JUMP), new float[1])[0];
			poseStack.pushPose();
			poseStack.translate(at.x, at.y, at.z);
			poseStack.mulPose(Axis.YP.rotationDegrees(heading));
			poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
			poseStack.translate(0.0F, ArrivalFilm.FALCON_MIDDLE, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
			poseStack.translate(0.0F, -ArrivalFilm.FALCON_MIDDLE, 0.0F);

			if (t > JUMP) {
				// Stretched into a line of light as it goes.
				float k = Math.min(1.0F, (t - JUMP) / 6.0F);
				poseStack.translate(0.0F, 0.0F, -5.9F);
				poseStack.scale(1.0F - 0.6F * k, 1.0F - 0.6F * k, 1.0F + 40.0F * k);
				poseStack.translate(0.0F, 0.0F, 5.9F);
			}

			ShipModels.submit(poseStack, collector, freighter, ShipModels.Pose.FLYING, falcon, light, 0);
			ENGINES.clear();
			MeshBuilder glow = ArrivalFilm.builder(ENGINES);
			glow.layer(Layer.GLOW).fullBright().twoSided(true).fit(InteriorAtlas.WHITE);
			engines(glow, t);
			ArrivalFilm.submit(collector, poseStack, Layer.GLOW, ENGINES);
			poseStack.popPose();
		}

		MOVING.clear();
		MOVING_SOLID.clear();
		MOVING_HOLO.clear();
		MeshBuilder fire = ArrivalFilm.builder(MOVING);
		fire.layer(Layer.GLOW).fullBright().twoSided(true).fit(InteriorAtlas.WHITE);
		MeshBuilder solid = ArrivalFilm.builder(MOVING_SOLID);
		solid.layer(Layer.SOLID).fixedLight(9, 15).fit(InteriorAtlas.WHITE);
		MeshBuilder smoke = ArrivalFilm.builder(MOVING_HOLO);
		smoke.layer(Layer.HOLO).fullBright().twoSided(true).fit(InteriorAtlas.WHITE);
		blasts(fire, smoke, t);
		core(fire, smoke, solid, t);
		jumpFlash(fire, t);
		ArrivalFilm.submit(collector, poseStack, Layer.SOLID, MOVING_SOLID);
		ArrivalFilm.submit(collector, poseStack, Layer.GLOW, MOVING);
		ArrivalFilm.submit(collector, poseStack, Layer.HOLO, MOVING_HOLO);
		poseStack.popPose();
		drawn++;
	}

	/** Her TIE leaving, from outside: the Tenebris, the TIE dropping out of the hangar, a blast behind it. */
	public static void submitTie(PoseStack poseStack, SubmitNodeCollector collector, Vec3 scene, float t) {
		ArrivalFilm.build();
		Minecraft client = Minecraft.getInstance();
		ShipType sd = ShipType.byId("star_destroyer").orElse(null);
		ShipType fighter = ShipType.byId("tie_fighter").orElse(null);

		if (sd == null || fighter == null) {
			return;
		}

		destroyer = ShipModels.resolve(sd, destroyer, client.level, null, 1313);
		tie = ShipModels.resolve(fighter, tie, client.level, null, 66);
		int light = LightCoordsUtil.pack(13, 15);
		ArrivalFilm.submit(collector, poseStack, Layer.GLOW, ArrivalFilm.STARS);
		poseStack.pushPose();
		poseStack.translate(scene.x, scene.y, scene.z);
		poseStack.pushPose();
		poseStack.scale(ArrivalFilm.SD_SCALE, ArrivalFilm.SD_SCALE, ArrivalFilm.SD_SCALE);
		ShipModels.submit(poseStack, collector, sd, ShipModels.Pose.FLYING, destroyer, light, 0);
		poseStack.popPose();
		ArrivalFilm.submit(collector, poseStack, Layer.GLOW, ArrivalFilm.HULL_LIGHTS);

		Vec3 at = tie(t);
		Vec3 d = heading(TIE, Mth.clamp(t, 0.0F, TIE_SHOT));
		float heading = (float) Math.toDegrees(Math.atan2(-d.x, -d.z));
		float pitch = (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		poseStack.pushPose();
		poseStack.translate(at.x, at.y, at.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(heading));
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.12F) * 25.0F));
		ShipModels.submit(poseStack, collector, fighter, ShipModels.Pose.FLYING, tie, light, 0);
		poseStack.popPose();

		// A blast breaks out of the hull behind it (the reactor's fire has reached the hangar deck).
		MOVING.clear();
		MOVING_HOLO.clear();
		MeshBuilder fire = ArrivalFilm.builder(MOVING);
		fire.layer(Layer.GLOW).fullBright().twoSided(true).fit(InteriorAtlas.WHITE);
		MeshBuilder smoke = ArrivalFilm.builder(MOVING_HOLO);
		smoke.layer(Layer.HOLO).fullBright().twoSided(true).fit(InteriorAtlas.WHITE);
		blast(fire, smoke, new Vector3f(-14.0F, 2.5F, -52.0F), 7.0F, t - 30.0F);
		blast(fire, smoke, new Vector3f(9.0F, 2.0F, -10.0F), 5.0F, t - 44.0F);
		ArrivalFilm.submit(collector, poseStack, Layer.GLOW, MOVING);
		ArrivalFilm.submit(collector, poseStack, Layer.HOLO, MOVING_HOLO);
		poseStack.popPose();
		drawn++;
	}

	/** The Falcon's engines along the back of its saucer: full power all the way. */
	private static void engines(MeshBuilder b, float t) {
		float power = t < 10.0F ? 0.5F + t / 20.0F : 1.0F;
		float flare = t > 14.0F && t < 34.0F ? 1.0F - Math.abs(t - 24.0F) / 10.0F : 0.0F;
		int colour = ArrivalFilm.argb(1.0F, 0.55F * power + 0.4F * flare, 0.78F * power + 0.2F * flare, 1.0F);
		float y = ArrivalFilm.FALCON_MIDDLE;

		for (float angle = -54.0F; angle <= 54.0F; angle += 9.0F) {
			float a0 = (angle - 4.5F) * Mth.DEG_TO_RAD;
			float a1 = (angle + 4.5F) * Mth.DEG_TO_RAD;
			float r = 5.06F;
			Vector3f p0 = new Vector3f(r * Mth.sin(a0), y - 0.2F, 0.75F + r * Mth.cos(a0));
			Vector3f p1 = new Vector3f(r * Mth.sin(a1), y - 0.2F, 0.75F + r * Mth.cos(a1));
			b.colour(colour).rect(p0, new Vector3f(p1).sub(p0), new Vector3f(0.0F, 0.42F, 0.0F));
		}
	}

	/** The blasts along the hull so far: each a fireball growing and fading, a smoke cloud, a fire left burning. */
	private static void blasts(MeshBuilder fire, MeshBuilder smoke, float t) {
		if (t >= WRECK) {
			return;
		}

		for (float[] blast : BLASTS) {
			float age = t - blast[0];

			if (age < 0.0F) {
				continue;
			}

			Vector3f at = new Vector3f(blast[1], blast[2], blast[3]);
			blast(fire, smoke, at, blast[4], age);

			if (age > 24.0F) {
				// Burning on where it broke out, flickering.
				float flicker = 0.55F + 0.45F * Mth.sin(t * 1.3F + blast[3]);
				fire.colour(ArrivalFilm.argb(1.0F, 0.9F * flicker, 0.42F * flicker, 0.12F * flicker)).sphere(at, blast[4] * 0.32F, 8, 5);
			}
		}

		// Amidships the reactor's fire breaks through the back and grows.
		if (t > 140.0F) {
			float k = Math.min(1.0F, (t - 140.0F) / 120.0F);
			float flicker = 0.8F + 0.2F * Mth.sin(t * 0.9F);
			fire.colour(ArrivalFilm.argb(1.0F, 1.0F * flicker, 0.55F * k * flicker, 0.2F * k)).sphere(new Vector3f(0.0F, 16.0F, 10.0F), 6.0F + 22.0F * k, 14, 8);
		}
	}

	/** One blast: a white-hot core, then orange, then dark smoke rising; {@code age} in ticks (negative: not yet). */
	static void blast(MeshBuilder fire, MeshBuilder smoke, Vector3f at, float size, float age) {
		if (age < 0.0F || age > 60.0F) {
			return;
		}

		if (age < 22.0F) {
			float k = age / 22.0F;
			float r = size * (1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k));
			float heat = 1.0F - k;
			fire.colour(ArrivalFilm.argb(1.0F, 1.0F * (0.4F + 0.6F * heat), (0.35F + 0.6F * heat * heat) * (0.4F + 0.6F * heat), 0.15F + 0.75F * heat * heat * heat))
					.sphere(at, r, 12, 7);

			if (age < 5.0F) {
				fire.colour(ArrivalFilm.argb(1.0F, 1.0F, 1.0F, 0.95F)).sphere(at, size * 0.35F * (1.0F - age / 5.0F) + 0.5F, 8, 5);
			}
		}

		if (age > 8.0F) {
			float k = (age - 8.0F) / 52.0F;
			int alpha = Math.round((1.0F - k) * 110.0F);
			smoke.colourAlpha(alpha << 24 | 0x2A2624).sphere(new Vector3f(at).add(0.0F, size * 0.5F * k, 0.0F), size * (0.7F + 0.6F * k), 10, 6);
		}
	}

	/** The core: the flash, the fireball as large as the ship, the ring racing out, the wreck. */
	private static void core(MeshBuilder fire, MeshBuilder smoke, MeshBuilder solid, float t) {
		float age = t - CORE;

		if (age < 0.0F) {
			return;
		}

		Vector3f middle = new Vector3f(0.0F, 10.0F, 0.0F);

		if (age < 90.0F) {
			float k = age / 90.0F;
			float r = 30.0F + 190.0F * (1.0F - (1.0F - k) * (1.0F - k));
			float heat = 1.0F - k;
			fire.colour(ArrivalFilm.argb(1.0F, heat * 1.0F + 0.1F, heat * heat * 0.85F + 0.05F, heat * heat * heat * 0.7F)).sphere(middle, r, 20, 12);

			if (age < 10.0F) {
				fire.colour(ArrivalFilm.argb(1.0F, 1.0F, 1.0F, 1.0F)).sphere(middle, 40.0F + age * 9.0F, 16, 10);
			}
		}

		// The ring racing out and fading: tilted a little, so that it sweeps through where the Falcon
		// flies (seen nearly edge-on from beside it: a band of light, not a ceiling overhead).
		float radius = 40.0F + age * RING_SPEED * 2.0F;

		if (age < 150.0F) {
			float k = age / 150.0F;
			float fade = 1.0F - k;
			Vec3 away = falcon(JUMP).subtract(middle.x, middle.y, middle.z);
			float heading = (float) Math.toDegrees(Math.atan2(away.x, away.z));
			float tilt = (float) Math.toDegrees(Math.atan2(-away.y, Math.sqrt(away.x * away.x + away.z * away.z)));
			fire.push().translate(middle.x, middle.y, middle.z).rotateY(heading).rotateX(tilt);
			fire.colour(ArrivalFilm.argb(1.0F, 0.75F * fade, 0.85F * fade, 1.0F * fade)).ring(0.0F, 0.0F, 0.0F, radius * 0.94F, radius, 96, true);
			fire.colour(ArrivalFilm.argb(1.0F, 0.75F * fade, 0.85F * fade, 1.0F * fade)).ring(0.0F, 0.0F, 0.0F, radius * 0.94F, radius, 96, false);
			fire.colour(ArrivalFilm.argb(1.0F, 0.55F * fade, 0.3F * fade, 0.12F * fade)).ring(0.0F, 0.3F, 0.0F, radius * 0.8F, radius * 0.93F, 96, true);
			fire.colour(ArrivalFilm.argb(1.0F, 0.55F * fade, 0.3F * fade, 0.12F * fade)).ring(0.0F, 0.3F, 0.0F, radius * 0.8F, radius * 0.93F, 96, false);
			fire.pop();
		}

		// The wreck torn apart, the pieces tumbling away, glowing at their edges a while.
		if (t >= WRECK) {
			float w = t - WRECK;

			for (float[] piece : WRECKAGE) {
				float s = piece[6];
				solid.push().translate(piece[0] + piece[3] * w, piece[1] + piece[4] * w, piece[2] + piece[5] * w).rotateX(piece[7] * w).rotateY(piece[7] * w * 0.7F);
				solid.colour(0xFF5E636C).box(-s * 0.5F, -s * 0.12F, -s * 0.35F, s * 0.5F, s * 0.12F, s * 0.35F);
				solid.pop();

				if (w < 70.0F) {
					float glow = 1.0F - w / 70.0F;
					fire.push().translate(piece[0] + piece[3] * w, piece[1] + piece[4] * w, piece[2] + piece[5] * w).rotateX(piece[7] * w).rotateY(piece[7] * w * 0.7F);
					fire.colour(ArrivalFilm.argb(1.0F, glow, 0.45F * glow, 0.1F * glow)).box(-s * 0.52F, -s * 0.13F, -s * 0.37F, s * 0.52F, -s * 0.11F, s * 0.37F);
					fire.pop();
				}
			}

			if (w < 120.0F) {
				float k = w / 120.0F;
				int alpha = Math.round((1.0F - k) * 90.0F);
				smoke.colourAlpha(alpha << 24 | 0x3A302C).sphere(middle, 150.0F + 120.0F * k, 18, 10);
			}
		}
	}

	/** The jump: a flash where the Falcon went, a line of light fading ahead of it. */
	private static void jumpFlash(MeshBuilder fire, float t) {
		float age = t - JUMP;

		if (age < 0.0F || age > 24.0F) {
			return;
		}

		Vec3 at = falcon(JUMP);
		Vec3 d = heading(FALCON, JUMP);
		Vector3f middle = new Vector3f((float) at.x, (float) at.y + ArrivalFilm.FALCON_MIDDLE, (float) at.z);
		float k = 1.0F - age / 24.0F;

		// A short white flash (small: it is seen from close beside the Falcon).
		if (age < 3.0F) {
			float f = 1.0F - age / 3.0F;
			fire.colour(ArrivalFilm.argb(1.0F, 0.85F * f + 0.15F, 0.92F * f + 0.08F, f)).sphere(middle, 0.9F + 1.4F * (1.0F - f), 10, 6);
		}

		Vector3f along = new Vector3f((float) d.x, (float) d.y, (float) d.z).mul(500.0F * Math.min(1.0F, age / 4.0F));
		fire.colour(ArrivalFilm.argb(1.0F, 0.55F * k, 0.75F * k, k));
		fire.rect(new Vector3f(middle).add(-0.4F, 0.0F, 0.0F), new Vector3f(0.8F, 0.0F, 0.0F), along);
		fire.rect(new Vector3f(middle).add(0.0F, -0.4F, 0.0F), new Vector3f(0.0F, 0.8F, 0.0F), along);
	}

	/** Only for tests: where the camera and the Falcon are at that time. */
	public static String describeForTest(float t) {
		ArrivalFilm.Shot shot = camera(t);
		return "camera " + shot.position() + " yaw " + shot.yaw() + " pitch " + shot.pitch() + ", falcon " + falcon(t);
	}

	/** Only for tests: how many frames were drawn. */
	public static int drawnForTest() {
		return drawn;
	}
}
