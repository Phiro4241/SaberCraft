package com.phiro.sabercraft.client.interior;

import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.DestroyerInterior.Crew;

/**
 * Phase 17: the Star Destroyer's crew, drawn as blocky figures: troopers in white armour with
 * their rifles, officers in grey, technicians in black. They are no entities (nothing for the
 * server to tick or send): each client draws them from the plan's list, moving by the level's
 * game time, so every player sees the same patrol in the same place.
 */
final class CrewModels {
	private CrewModels() {
	}

	/** Proportions of a figure 1.8 blocks tall (a person of the game's own build, a little slimmer). */
	static final float LEG = 0.72F;
	static final float BODY = 0.66F;
	static final float HEAD = 0.42F;
	static final float HALF_W = 0.21F;
	static final float HALF_D = 0.12F;
	static final float LIMB = 0.105F;

	static void draw(MeshBuilder b, double ticks) {
		float t = (float) (ticks / 20.0);
		int seed = 0;

		for (Crew crew : DestroyerInterior.CREW) {
			seed++;
			double x = crew.x();
			double y = crew.y();
			double z = crew.z();
			float yaw = crew.yaw();
			float walk = 0.0F;

			if (!crew.path().isEmpty()) {
				double[] at = along(crew.path(), crew.phase() + t * crew.speed());
				x = at[0];
				y = at[1];
				z = at[2];
				yaw = (float) at[3];
				walk = t * (float) crew.speed() * 3.2F;
			}

			figure(b, crew, x, y, z, yaw, walk, t, seed);
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0);
	}

	/** Where on a closed path one is after walking {@code distance}: x, y, z and the way one faces (yaw), turning smoothly at the corners. */
	static double[] along(List<Vec3> path, double distance) {
		int n = path.size();
		double total = 0.0;

		for (int i = 0; i < n; i++) {
			total += path.get(i).distanceTo(path.get((i + 1) % n));
		}

		double d = (distance % total + total) % total;

		for (int i = 0; i < n; i++) {
			Vec3 a = path.get(i);
			Vec3 c = path.get((i + 1) % n);
			double length = a.distanceTo(c);

			if (d <= length || i == n - 1) {
				double f = length > 0 ? Math.min(1.0, d / length) : 0.0;
				Vec3 p = a.lerp(c, f);
				float yaw = yaw(a, c);
				// Turn towards the next leg over its last stretch (and from the last leg over the first).
				double turn = 0.7;

				if (length - d < turn) {
					Vec3 e = path.get((i + 2) % n);
					yaw = lerpYaw(yaw, yaw(c, e), (float) (0.5 * (1.0 - (length - d) / turn)));
				} else if (d < turn) {
					Vec3 o = path.get((i - 1 + n) % n);
					yaw = lerpYaw(yaw(o, a), yaw, (float) (0.5 + 0.5 * d / turn));
				}

				return new double[] {p.x, p.y, p.z, yaw};
			}

			d -= length;
		}

		return new double[] {path.get(0).x, path.get(0).y, path.get(0).z, 0.0};
	}

	/** Minecraft's yaw for walking from a to c (0 = south, 90 = west). */
	static float yaw(Vec3 a, Vec3 c) {
		return (float) Math.toDegrees(Math.atan2(-(c.x - a.x), c.z - a.z));
	}

	static float lerpYaw(float a, float c, float f) {
		return a + Mth.wrapDegrees(c - a) * f;
	}

	private static void figure(MeshBuilder b, Crew crew, double x, double y, double z, float yaw, float walk, float t, int seed) {
		boolean trooper = DestroyerInterior.TROOPER.equals(crew.kind());
		boolean officer = DestroyerInterior.OFFICER.equals(crew.kind());
		String pose = crew.pose();
		boolean sit = DestroyerInterior.SIT.equals(pose);
		boolean work = DestroyerInterior.WORK.equals(pose);
		boolean talk = DestroyerInterior.TALK.equals(pose);
		int face = trooper ? InteriorAtlas.CREW_TROOPER_HELMET : officer ? InteriorAtlas.CREW_OFFICER_FACE : InteriorAtlas.CREW_TECH_HELMET;
		int head = trooper ? InteriorAtlas.CREW_TROOPER_HELMET_SIDE : officer ? InteriorAtlas.CREW_OFFICER_HEAD : InteriorAtlas.CREW_TECH_HELMET;
		int body = trooper ? InteriorAtlas.CREW_TROOPER_TORSO : officer ? InteriorAtlas.CREW_OFFICER_TUNIC : InteriorAtlas.CREW_TECH_BODY;
		int limb = trooper ? InteriorAtlas.CREW_TROOPER_LIMB : officer ? InteriorAtlas.CREW_OFFICER_LIMB : InteriorAtlas.CREW_TECH_BODY;
		float swing = Mth.sin(walk) * 32.0F;
		// Breathing, a glance aside now and then.
		float breathe = Mth.sin(t * 1.6F + seed) * 0.006F;
		float glance = DestroyerInterior.STAND.equals(pose) ? Mth.sin(t * 0.35F + seed * 1.7F) * 18.0F : talk ? Mth.sin(t * 0.9F + seed) * 10.0F : 0.0F;
		float hip = sit ? 0.42F : LEG;

		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		b.push().translate(x, y, z).rotateY(-yaw);

		// The legs: swinging when walking, bent forward when sitting.
		for (int side = -1; side <= 1; side += 2) {
			b.push().translate(side * LIMB, hip, 0);

			if (sit) {
				b.rotateX(-90.0F);
			} else {
				b.rotateX(side * swing);
			}

			b.fit(limb);
			b.box(-LIMB, -LEG, -LIMB, LIMB, 0, LIMB);

			if (sit) {
				// The shin hangs down from the knee.
				b.push().translate(0, -LEG + LIMB, 0).rotateX(90.0F);
				b.box(-LIMB * 0.98F, -LEG + LIMB, -LIMB * 0.98F, LIMB * 0.98F, 0, LIMB * 0.98F);
				b.pop();
			}

			b.pop();
		}

		// The body.
		b.fit(body);
		b.box(-HALF_W, hip, -HALF_D, HALF_W, hip + BODY + breathe, HALF_D);
		float shoulder = hip + BODY - 0.06F;

		// The arms.
		for (int side = -1; side <= 1; side += 2) {
			b.push().translate(side * (HALF_W + LIMB), shoulder, 0);

			if (trooper && !work) {
				// Holding the rifle across the chest.
				b.rotateX(-50.0F).rotateZ(side * -28.0F);
			} else if (work) {
				// Hands on the console, moving a little.
				b.rotateX(-55.0F + Mth.sin(t * 3.0F + side + seed) * 8.0F);
			} else if (talk) {
				b.rotateX(side < 0 ? -20.0F + Mth.sin(t * 2.2F + seed) * 14.0F : 4.0F);
			} else if (sit) {
				b.rotateX(-40.0F);
			} else if (walk != 0.0F) {
				b.rotateX(-side * swing * 0.7F);
			} else {
				// Hands behind the back.
				b.rotateX(14.0F).rotateZ(side * -8.0F);
			}

			b.fit(limb);
			b.box(-LIMB * 0.95F, -BODY + 0.02F, -LIMB * 0.95F, LIMB * 0.95F, 0.06F, LIMB * 0.95F);
			b.pop();
		}

		if (trooper && !work) {
			// The rifle: a black body and barrel across the chest, the muzzle up to the left.
			b.push().translate(0, shoulder - 0.32F, HALF_D + 0.17F).rotateZ(35.0F);
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(-0.32F, -0.045F, -0.045F, 0.2F, 0.06F, 0.045F);
			b.box(-0.12F, -0.16F, -0.03F, -0.06F, -0.04F, 0.03F);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(0.2F, -0.02F, -0.025F, 0.42F, 0.03F, 0.025F);
			b.pop();
		}

		// The head: looking about, a face on its front.
		b.push().translate(0, hip + BODY, 0).rotateY(glance);
		float h = HEAD / 2.0F;
		b.fit(head);
		b.box(-h, 0, -h, h, HEAD, h, MeshBuilder.ALL & ~MeshBuilder.SOUTH);
		b.fit(face);
		b.box(-h, 0, -h, h, HEAD, h, MeshBuilder.SOUTH);
		b.pop();
		b.pop();
	}
}
