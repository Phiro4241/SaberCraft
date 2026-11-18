package com.phiro.sabercraft.client.interior;

import java.util.List;

import net.minecraft.util.Mth;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.interior.Crew;

/**
 * Phase 17: a ship's crew, drawn as blocky figures. They are no entities (nothing for the server
 * to tick or send): each client draws them from the plan's list, moving by the level's game time,
 * so every player sees the same patrol in the same place.
 *
 * <p>Phase 18: arms and legs in two parts (upper arm and forearm, thigh and shin) put where the
 * hands and feet should be, so a trooper holds the rifle with both hands (the right on the grip,
 * the left under the barrel), walks with bent knees and a little bob, and slings the rifle over the
 * back at a console; officers clasp their hands behind the back, swing their arms when walking and
 * gesture when talking. More kinds of people (officers in four uniforms with caps, pilots, honour
 * guards with pikes, the Falcon's people), four faces, and heads with a top of their own.
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
	/** Upper arm and forearm (together a little shorter than the body). */
	static final float UPPER = 0.31F;
	static final float FORE = 0.31F;

	static final int[] FACES = {InteriorAtlas.CREW_FACE_0, InteriorAtlas.CREW_FACE_1, InteriorAtlas.CREW_FACE_2, InteriorAtlas.CREW_FACE_3};
	static final int[] HEADS = {InteriorAtlas.CREW_HEAD_0, InteriorAtlas.CREW_HEAD_1, InteriorAtlas.CREW_HEAD_2, InteriorAtlas.CREW_HEAD_3};
	/** The hair colours of the four faces (as in the texture generator). */
	static final int[] HAIR = {0xFF463224, 0xFF28221E, 0xFF96928C, 0xFF28221E};

	static void draw(MeshBuilder b, List<Crew> crew, double ticks) {
		float t = (float) (ticks / 20.0);
		int index = 0;

		for (Crew one : crew) {
			index++;
			double[] at = one.where(ticks);
			float walk = one.path().isEmpty() ? 0.0F : t * (float) one.speed() * 3.4F;
			int seed = index * 7 + (int) Math.floor(one.x() * 3.0 + one.z() * 5.0);
			figure(b, one, at[0], at[1], at[2], (float) at[3], walk, t, seed);
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0);
	}

	/** What a figure looks like: the tiles of its head, body, arms and legs and a colour for the uniform's light tiles. */
	record Look(int face, int head, int top, int bottom, int body, int arm, int leg, int tint, int hairTint, boolean cap) {
	}

	/**
	 * Phase 19: what a story's figure carries or does beyond the crew's routine: a lamp on the
	 * rifle, a lightsaber (its blade colour, 0 = none; raised for a blow 0..1, the blow's follow
	 * through 0..1, lit or not), lying beaten on the floor, a robe's skirt over the legs.
	 */
	record Gear(boolean lamp, int saber, boolean lit, float raise, float swing, boolean down, boolean skirt, int attack, float aim, float crouch, float lean,
			float hand, float spin, int react, float reactP, boolean pistol, boolean unarmed, float twin) {
		static final Gear NONE = new Gear(false, 0, false, 0.0F, 0.0F, false, false, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

		/** Phase 23's gear (one blade). Phase 24: {@code twin} is how far the second blade of a saber staff is out (0..1). */
		Gear(boolean lamp, int saber, boolean lit, float raise, float swing, boolean down, boolean skirt, int attack, float aim, float crouch, float lean,
				float hand, float spin, int react, float reactP, boolean pistol, boolean unarmed) {
			this(lamp, saber, lit, raise, swing, down, skirt, attack, aim, crouch, lean, hand, spin, react, reactP, pistol, unarmed, 0.0F);
		}

		/** Phase 19's gear (no blow of a kind, no aim). */
		Gear(boolean lamp, int saber, boolean lit, float raise, float swing, boolean down, boolean skirt) {
			this(lamp, saber, lit, raise, swing, down, skirt, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		}

		/** Phase 21's gear (no reaction). */
		Gear(boolean lamp, int saber, boolean lit, float raise, float swing, boolean down, boolean skirt, int attack, float aim, float crouch, float lean,
				float hand, float spin) {
			this(lamp, saber, lit, raise, swing, down, skirt, attack, aim, crouch, lean, hand, spin, 0, 0.0F, false, false);
		}
	}

	/** Phase 23: what the body does (as the server's {@code Figure}). */
	static final int FLINCH = 1;
	static final int HEAVY = 2;
	static final int STUMBLE = 3;
	static final int FALL = 4;
	static final int COMLINK = 5;
	static final int CLASH = 6;
	static final int DISARMED = 7;
	static final int PARRY = 8;
	static final int DODGE = 9;
	static final int LOCK = 10;

	/** Phase 23: how far a reaction throws the body back (degrees; negative is forward) at that point of it. */
	static float reactLean(Gear gear, float t, int seed) {
		float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);
		float bell = Mth.sin(p * Mth.PI);

		return switch (gear.react()) {
			case FLINCH -> bell * 11.0F;
			// Thrown back hard at once, slowly upright again.
			case HEAVY -> (float) Math.pow(1.0F - p, 1.6) * 30.0F * Math.min(1.0F, p * 6.0F);
			// Off balance: rocking back and forth.
			case STUMBLE -> bell * 16.0F + Mth.sin(p * 13.0F) * 7.0F * (1.0F - p);
			case CLASH -> bell * 12.0F;
			case LOCK -> -10.0F + Mth.sin(t * 38.0F + seed) * 0.9F;
			// Thrown back by the blow (the first few ticks of it), then standing, stooped a little.
			case DISARMED -> p < 0.08F ? p / 0.08F * 14.0F : Math.max(3.0F, 14.0F - (p - 0.08F) / 0.32F * 11.0F);
			default -> 0.0F;
		};
	}

	/** Phase 23: a sideways roll of the body (stumbling, a heavy hit). */
	static float reactRoll(Gear gear, int seed) {
		float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);

		return switch (gear.react()) {
			case STUMBLE -> Mth.sin(p * 9.0F + seed) * 8.0F * (1.0F - p);
			case HEAVY -> Mth.sin(p * Mth.PI) * ((seed & 1) == 0 ? 6.0F : -6.0F);
			default -> 0.0F;
		};
	}

	/** Phase 23: how far the arms are flung out by a hit (0..1). */
	static float reactFling(Gear gear) {
		float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);

		return switch (gear.react()) {
			case HEAVY -> (float) Math.pow(1.0F - p, 1.3) * Math.min(1.0F, p * 8.0F);
			case STUMBLE -> Mth.sin(p * Mth.PI) * 0.6F;
			case FLINCH -> Mth.sin(p * Mth.PI) * 0.3F;
			default -> 0.0F;
		};
	}

	static Look look(Crew crew, int seed) {
		int f = Math.floorMod(seed, 4);
		int face = FACES[f];
		int head = HEADS[f];

		return switch (crew.kind()) {
			case Crew.TROOPER -> new Look(InteriorAtlas.CREW_TROOPER_HELMET, InteriorAtlas.CREW_TROOPER_HELMET_SIDE, InteriorAtlas.CREW_TROOPER_HELMET_TOP,
					InteriorAtlas.IMP_BLACK, InteriorAtlas.CREW_TROOPER_TORSO, InteriorAtlas.CREW_TROOPER_LIMB, InteriorAtlas.CREW_TROOPER_LIMB, 0, 0, false);
			case Crew.TECH -> new Look(InteriorAtlas.CREW_TECH_HELMET, InteriorAtlas.CREW_TECH_HELMET_SIDE, InteriorAtlas.CREW_TECH_HELMET_SIDE,
					InteriorAtlas.IMP_BLACK, InteriorAtlas.CREW_TECH_BODY, InteriorAtlas.CREW_TECH_LIMB, InteriorAtlas.CREW_TECH_LIMB, 0, 0, false);
			case Crew.PILOT -> new Look(InteriorAtlas.CREW_PILOT_HELMET, InteriorAtlas.CREW_PILOT_HELMET_SIDE, InteriorAtlas.CREW_PILOT_HELMET_SIDE,
					InteriorAtlas.IMP_BLACK, InteriorAtlas.CREW_PILOT_BODY, InteriorAtlas.CREW_PILOT_LIMB, InteriorAtlas.CREW_PILOT_LIMB, 0, 0, false);
			case Crew.GUARD -> new Look(InteriorAtlas.CREW_GUARD_HELMET, InteriorAtlas.CREW_GUARD_ROBE, InteriorAtlas.CREW_GUARD_ROBE, InteriorAtlas.IMP_BLACK,
					InteriorAtlas.CREW_GUARD_ROBE, InteriorAtlas.CREW_GUARD_ROBE, InteriorAtlas.CREW_GUARD_ROBE, 0, 0, false);
			case Crew.SMUGGLER -> new Look(InteriorAtlas.CREW_SMUGGLER_FACE, InteriorAtlas.CREW_SMUGGLER_HEAD, InteriorAtlas.CREW_HAIR_TOP,
					InteriorAtlas.CREW_SMUGGLER_HEAD, InteriorAtlas.CREW_SMUGGLER_TORSO, InteriorAtlas.CREW_SMUGGLER_ARM, InteriorAtlas.CREW_SMUGGLER_LEGS, 0,
					0xFF5A3C28, false);
			case Crew.WOOKIEE -> new Look(InteriorAtlas.CREW_WOOKIEE_FACE, InteriorAtlas.CREW_WOOKIEE_FUR, InteriorAtlas.CREW_WOOKIEE_FUR,
					InteriorAtlas.CREW_WOOKIEE_FUR, InteriorAtlas.CREW_WOOKIEE_TORSO, InteriorAtlas.CREW_WOOKIEE_FUR, InteriorAtlas.CREW_WOOKIEE_FUR, 0, 0, false);
			case Crew.REBEL -> new Look(InteriorAtlas.CREW_REBEL_FACE, InteriorAtlas.CREW_REBEL_HEAD, InteriorAtlas.CREW_HAIR_TOP, InteriorAtlas.CREW_REBEL_HEAD,
					InteriorAtlas.CREW_REBEL_SUIT, InteriorAtlas.CREW_REBEL_LIMB, InteriorAtlas.CREW_REBEL_LIMB, 0, 0xFF2A2420, false);
			case Crew.OFFICER_BLACK -> new Look(face, head, InteriorAtlas.CREW_HAIR_TOP, head, InteriorAtlas.CREW_UNIFORM_TUNIC, InteriorAtlas.CREW_UNIFORM_LIMB,
					InteriorAtlas.CREW_UNIFORM_LIMB, 0xFF34343A, HAIR[f], true);
			case Crew.OFFICER_OLIVE -> new Look(face, head, InteriorAtlas.CREW_HAIR_TOP, head, InteriorAtlas.CREW_UNIFORM_TUNIC, InteriorAtlas.CREW_UNIFORM_LIMB,
					InteriorAtlas.CREW_UNIFORM_LIMB, 0xFF6E7354, HAIR[f], true);
			// The admiral: white, grey hair, no cap.
			case Crew.ADMIRAL -> new Look(InteriorAtlas.CREW_FACE_3, InteriorAtlas.CREW_HEAD_3, InteriorAtlas.CREW_HAIR_TOP, InteriorAtlas.CREW_HEAD_3,
					InteriorAtlas.CREW_UNIFORM_TUNIC, InteriorAtlas.CREW_UNIFORM_LIMB, InteriorAtlas.CREW_UNIFORM_LIMB, 0xFFF2F0E8, HAIR[2], false);
			// Grey officers: with or without the cap, by face.
			default -> new Look(face, head, InteriorAtlas.CREW_HAIR_TOP, head, InteriorAtlas.CREW_UNIFORM_TUNIC, InteriorAtlas.CREW_UNIFORM_LIMB,
					InteriorAtlas.CREW_UNIFORM_LIMB, 0xFF8C9188, HAIR[f], (seed & 4) == 0);
		};
	}

	private static void figure(MeshBuilder b, Crew crew, double x, double y, double z, float yaw, float walk, float t, int seed) {
		figure(b, crew, look(crew, seed), x, y, z, yaw, walk, t, seed, Float.NaN, Gear.NONE);
	}

	/**
	 * A figure: its kind and pose from {@code crew}, its look, where and which way it faces, how
	 * far its legs walked, the time (seconds) and its seed. Phase 19: the head turned by
	 * {@code headTurn} degrees from the body (NaN: looking about by itself), and what it carries.
	 */
	static void figure(MeshBuilder b, Crew crew, Look look, double x, double y, double z, float yaw, float walk, float t, int seed, float headTurn, Gear gear) {
		String pose = gear.down() ? Crew.STAND : crew.pose();
		String kind = crew.kind();
		boolean sit = Crew.SIT.equals(pose);
		boolean work = Crew.WORK.equals(pose);
		boolean talk = Crew.TALK.equals(pose);
		boolean walking = walk != 0.0F && !gear.down();
		boolean trooper = Crew.TROOPER.equals(kind);
		boolean guard = Crew.GUARD.equals(kind);
		boolean wookiee = Crew.WOOKIEE.equals(kind);
		boolean saber = gear.saber() != 0 && gear.lit() && !gear.down();
		boolean rifle = trooper && !sit && !saber;
		float swing = walking ? Mth.sin(walk) * 30.0F : 0.0F;
		// Breathing, a glance aside now and then, a step's bob.
		float breathe = Mth.sin(t * 1.6F + seed) * 0.006F;
		float glance = Crew.STAND.equals(pose) ? Mth.sin(t * 0.35F + seed * 1.7F) * 18.0F + Mth.sin(t * 0.13F + seed) * 10.0F
				: talk ? Mth.sin(t * 0.9F + seed) * 10.0F : walking ? Mth.sin(walk * 0.25F + seed) * 6.0F : 0.0F;
		float bob = walking ? Math.abs(Mth.cos(walk)) * 0.035F - 0.03F : 0.0F;
		// Phase 21: crouching before a leap.
		float hip = (sit ? 0.42F : LEG) + bob - gear.crouch() * 0.28F;

		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		b.push().translate(x, y, z).rotateY(-yaw);

		if (gear.down()) {
			// Beaten: on the back, the head towards where they faced, a little off the floor.
			// Phase 23: falling there first (quicker and quicker, as one falls).
			float fall = gear.react() == FALL ? Mth.clamp(gear.reactP(), 0.0F, 1.0F) : 1.0F;
			float e = fall * fall;
			b.translate(0, (HEAD / 2.0F - 0.01F) * e, 0).rotateX(-90.0F * e);
			breathe = 0.0F;
			glance = 0.0F;
		} else if (gear.react() == DODGE) {
			// Phase 23: a quick step aside: leaning into it.
			b.rotateZ(Mth.sin(Mth.clamp(gear.reactP(), 0.0F, 1.0F) * Mth.PI) * 12.0F);
		}

		if (wookiee) {
			float k = (float) (crew.height() / 1.8);
			b.scale(k, k, k);
		}

		// The legs (under a guard's robe: the robe instead).
		if (gear.skirt()) {
			// Phase 19: a robe's skirt over the legs, to the knees.
			for (int side = -1; side <= 1; side += 2) {
				legs(b, look, side, hip, sit, side * swing, walking ? Math.max(0.0F, -side * Mth.cos(walk)) * 38.0F : 0.0F);
			}

			b.white().fit(look.body(), 0.0F, 0.75F, 1.0F, 1.0F);
			b.box(-HALF_W - 0.03F, hip - 0.42F, -HALF_D - 0.04F, HALF_W + 0.03F, hip + 0.02F, HALF_D + 0.05F, MeshBuilder.ALL & ~MeshBuilder.UP);
		} else if (guard) {
			b.colour(0xFFFFFFFF).fit(look.leg());
			b.box(-HALF_W - 0.04F, 0.02F, -HALF_D - 0.05F, HALF_W + 0.04F, hip, HALF_D + 0.06F);
		} else {
			for (int side = -1; side <= 1; side += 2) {
				legs(b, look, side, hip, sit, side * swing, walking ? Math.max(0.0F, -side * Mth.cos(walk)) * 38.0F : 0.0F);
			}
		}

		// The body: a little turn with each step.
		b.push().translate(0, hip, 0).rotateY(walking ? Mth.sin(walk) * 4.0F : 0.0F);

		// Phase 21: leaning back when hit or staggering, forward when crouching or aiming. Phase 23:
		// thrown back by a hit (more by a heavy one), rocking when off balance, leaning into a lock.
		float reactLean = gear.down() ? 0.0F : reactLean(gear, t, seed);
		float reactRoll = gear.down() ? 0.0F : reactRoll(gear, seed);

		if (gear.lean() != 0.0F || gear.crouch() != 0.0F || gear.aim() != 0.0F || reactLean != 0.0F || reactRoll != 0.0F) {
			b.rotateX(-gear.lean() * 16.0F + gear.crouch() * 22.0F + gear.aim() * 4.0F - reactLean);

			if (reactRoll != 0.0F) {
				b.rotateZ(reactRoll);
			}
		}
		// The chest in front only; sides, back and shoulders from the tile's edge (its belt line
		// goes round, the badges and buttons stay in front).
		tint(b, look.tint()).fit(look.body());
		b.box(-HALF_W, 0, -HALF_D, HALF_W, BODY + breathe, HALF_D, MeshBuilder.SOUTH);
		b.fit(look.body(), 0.0F, 0.0F, 0.14F, 1.0F);
		b.box(-HALF_W, 0, -HALF_D, HALF_W, BODY + breathe, HALF_D, MeshBuilder.ALL & ~MeshBuilder.SOUTH);
		b.white();
		float shoulder = BODY - 0.06F;

		if (Crew.SMUGGLER.equals(kind)) {
			// The holster on the right hip, the blaster in it.
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(-HALF_W - 0.06F, -0.22F, -0.04F, -HALF_W + 0.02F, 0.04F, 0.08F);
		}

		// The arms: where the hands go.
		float[] hilt = saber ? saberHold(shoulder, gear) : null;
		float fling = gear.down() ? 0.0F : reactFling(gear);
		boolean pistol = gear.pistol() && !gear.down() && !sit;

		for (int side = -1; side <= 1; side += 2) {
			float sx = side * (HALF_W + LIMB * 0.9F);
			float[] hand = hilt != null ? new float[] {hilt[0] - hilt[3] * (side < 0 ? 0.05F : 0.17F), hilt[1] - hilt[4] * (side < 0 ? 0.05F : 0.17F),
					hilt[2] - hilt[5] * (side < 0 ? 0.05F : 0.17F)} : hand(crew, side, shoulder, swing, t, seed, rifle, walking);

			if (pistol && side < 0) {
				// Phase 23: an officer's pistol in the right hand: low and ready, aimed out at shoulder height.
				float k = Mth.clamp(gear.aim(), 0.0F, 1.0F);
				hand = new float[] {Mth.lerp(k, sx * 0.9F, -0.12F), Mth.lerp(k, shoulder - 0.46F, shoulder + 0.02F), Mth.lerp(k, 0.24F, 0.52F)};
			} else if (gear.react() == COMLINK && side > 0 && !gear.down()) {
				// Phase 23: the left hand at the ear (the comlink), the head a little down.
				float k = Mth.clamp(Math.min(gear.reactP() * 6.0F, (1.0F - gear.reactP()) * 6.0F), 0.0F, 1.0F);
				hand = new float[] {Mth.lerp(k, hand[0], 0.17F), Mth.lerp(k, hand[1], shoulder + 0.3F), Mth.lerp(k, hand[2], 0.04F)};
			} else if (gear.react() == DISARMED && side < 0 && !gear.down()) {
				// Phase 23: the empty sword hand: flung back by the blow, then raised before the face.
				float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);
				// (It lasts until she runs: the hand stays before her face while she stares at it,
				// and sinks at the end, as she looks up at the player.)
				float out = p < 0.08F ? p / 0.08F : Math.max(0.0F, 1.0F - (p - 0.08F) * 4.5F);
				float up = Mth.clamp((p - 0.14F) * 5.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((p - 0.82F) * 5.0F, 0.0F, 0.7F));
				hand = new float[] {Mth.lerp(out, -0.12F, sx - 0.25F), Mth.lerp(out, shoulder - 0.5F, shoulder + 0.15F) + up * 0.42F, Mth.lerp(out, 0.2F, -0.1F)
						+ up * 0.24F};
			}

			if (fling > 0.0F && hilt == null) {
				// Phase 23: thrown back by a hit: the arms fly out and up.
				hand = new float[] {Mth.lerp(fling, hand[0], sx + side * 0.32F), Mth.lerp(fling, hand[1], shoulder + 0.05F), Mth.lerp(fling, hand[2], -0.12F)};
			}

			if (hilt != null && side > 0 && gear.hand() > 0.0F) {
				// Phase 21: the free hand out towards the player (the Force push coming).
				float k = Mth.clamp(gear.hand(), 0.0F, 1.0F);
				hand = new float[] {Mth.lerp(k, hand[0], sx * 0.6F), Mth.lerp(k, hand[1], shoulder + 0.02F), Mth.lerp(k, hand[2], 0.62F)};
			} else if (rifle && gear.aim() > 0.0F) {
				// Phase 21: the rifle at the shoulder, aimed.
				float k = Mth.clamp(gear.aim(), 0.0F, 1.0F);
				hand = new float[] {hand[0] * (1.0F - k * 0.4F), hand[1] + k * 0.24F, hand[2] + k * 0.04F};
			}
			float[] pole = hilt != null ? new float[] {side * 0.9F, -0.8F, -0.2F} : pole(crew, side, rifle, walking);
			arm(b, look, sx, shoulder, 0.0F, hand[0], hand[1], hand[2], pole);
		}

		if (pistol) {
			// Phase 23: the pistol in the right hand, pointing where the forearm points.
			float k = Mth.clamp(gear.aim(), 0.0F, 1.0F);
			float sx = -(HALF_W + LIMB * 0.9F);
			float[] at = {Mth.lerp(k, sx * 0.9F, -0.12F), Mth.lerp(k, shoulder - 0.46F, shoulder + 0.02F), Mth.lerp(k, 0.24F, 0.52F)};
			float[] dir = norm(Mth.lerp(k, -0.1F, 0.02F), Mth.lerp(k, -0.75F, 0.0F), Mth.lerp(k, 0.65F, 1.0F));
			pistol(b, at[0], at[1], at[2], dir[0], dir[1], dir[2]);
		}

		if (hilt != null) {
			lightsaber(b, hilt, gear.saber(), gear.twin());
		} else if (gear.saber() != 0 && !gear.unarmed()) {
			// The hilt at the belt.
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(HALF_W - 0.02F, -0.3F, -0.03F, HALF_W + 0.04F, 0.0F, 0.03F);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(HALF_W - 0.025F, -0.06F, -0.035F, HALF_W + 0.045F, -0.02F, 0.035F);
		}

		if (rifle && !work) {
			// The rifle in both hands: the grip in the right, the barrel forward and to the left.
			float[] grip = rifleGrip(walking, swing, t, seed);
			float[] dir = RIFLE_DIR;

			if (gear.aim() > 0.0F) {
				// Phase 21: aimed: up at the shoulder, the barrel straight ahead.
				float k = Mth.clamp(gear.aim(), 0.0F, 1.0F);
				grip = new float[] {grip[0] * (1.0F - k * 0.4F), grip[1] + k * 0.24F, grip[2] + k * 0.04F};
				dir = norm(Mth.lerp(k, RIFLE_DIR[0], 0.08F), Mth.lerp(k, RIFLE_DIR[1], 0.02F), Mth.lerp(k, RIFLE_DIR[2], 1.0F));
			}

			if (fling > 0.0F) {
				// Phase 23: thrown back: the rifle goes with the right hand, its muzzle up.
				float sxr = -(HALF_W + LIMB * 0.9F);
				grip = new float[] {Mth.lerp(fling, grip[0], sxr - 0.32F), Mth.lerp(fling, grip[1], shoulder + 0.05F), Mth.lerp(fling, grip[2], -0.12F)};
				dir = norm(Mth.lerp(fling, dir[0], -0.2F), Mth.lerp(fling, dir[1], 0.9F), Mth.lerp(fling, dir[2], 0.3F));
			}

			blaster(b, grip[0], grip[1], grip[2], dir[0], dir[1], dir[2]);

			if (gear.lamp()) {
				// Phase 19: a lamp under the barrel, its lens lit.
				float lx = grip[0] + RIFLE_DIR[0] * LAMP_ALONG;
				float ly = grip[1] + RIFLE_DIR[1] * LAMP_ALONG - 0.07F;
				float lz = grip[2] + RIFLE_DIR[2] * LAMP_ALONG;
				b.fit(InteriorAtlas.IMP_BLACK);
				b.box(lx - 0.04F, ly - 0.04F, lz - 0.06F, lx + 0.04F, ly + 0.04F, lz + 0.06F);
				b.layer(Layer.GLOW).fullBright().colour(0xFFFFF0C8).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
				b.box(lx - 0.032F, ly - 0.032F, lz + 0.06F, lx + 0.032F, ly + 0.032F, lz + 0.065F, MeshBuilder.SOUTH);
				b.layer(Layer.SOLID).worldLight().white();
			}
		} else if (rifle) {
			// At a console: slung over the back, the muzzle down to the right.
			blaster(b, 0.17F, shoulder - 0.05F, -HALF_D - 0.05F, -0.55F, -0.83F, 0.0F);
		}

		if (guard) {
			// The pike, upright in the right hand, its blade above the helmet.
			float px = -HALF_W - 0.16F;
			float pz = 0.16F;
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(px - 0.022F, -hip + 0.02F, pz - 0.022F, px + 0.022F, BODY + HEAD + 0.45F, pz + 0.022F);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(px - 0.012F, BODY + HEAD + 0.45F, pz - 0.05F, px + 0.012F, BODY + HEAD + 0.72F, pz + 0.05F);
			b.box(px - 0.05F, BODY + HEAD + 0.43F, pz - 0.05F, px + 0.05F, BODY + HEAD + 0.47F, pz + 0.05F);
		}

		// The head: looking about (Phase 19: or where the story has them look).
		b.push().translate(0, BODY, 0).rotateY(Float.isNaN(headTurn) || gear.down() ? glance : headTurn);
		head(b, look, Crew.TROOPER.equals(kind) ? 0.01F : 0.0F);
		b.pop();
		b.pop();
		b.pop();
		b.white();
	}

	/** Phase 19: how far along the rifle the lamp sits, and where it is (figure space, from the feet) when standing. */
	static final float LAMP_ALONG = 0.42F;
	static final float[] LAMP_AT = {-0.13F + 0.58F * 0.42F, LEG + 0.3F + 0.16F * 0.42F - 0.07F, 0.27F + 0.8F * 0.42F};

	/**
	 * Phase 19: where a lightsaber is held (body space) and which way its blade points: on guard
	 * (low, forward and up), raised over the right shoulder for a blow, swept down to the left
	 * after it. Returns x, y, z of the hilt's top and the blade's direction.
	 */
	private static float[] saberHold(float shoulder, Gear gear) {
		float[] guard = {-0.02F, shoulder - 0.4F, 0.36F};
		float[] raised = {-0.18F, shoulder + 0.14F, 0.04F};
		float[] through = {0.14F, shoulder - 0.56F, 0.34F};
		float[] guardDir = norm(0.12F, 0.8F, 0.6F);
		float[] raisedDir = norm(-0.3F, 0.6F, -0.75F);
		float[] throughDir = norm(0.55F, -0.3F, 0.78F);

		// Phase 21: each kind of blow is wound up its own way (to be seen coming).
		switch (gear.attack()) {
			case SWEEP -> {
				// Drawn back to the right, flat: it comes from the side.
				raised = new float[] {-0.36F, shoulder - 0.3F, -0.06F};
				raisedDir = norm(-0.9F, 0.12F, -0.4F);
				through = new float[] {0.3F, shoulder - 0.36F, 0.3F};
				throughDir = norm(0.9F, 0.06F, 0.42F);
			}
			case THRUST -> {
				// Pulled in at the hip, the point forward: it comes straight.
				raised = new float[] {-0.12F, shoulder - 0.46F, -0.1F};
				raisedDir = norm(0.0F, 0.08F, 1.0F);
				through = new float[] {0.0F, shoulder - 0.3F, 0.62F};
				throughDir = norm(0.0F, 0.02F, 1.0F);
			}
			case STAGGER -> {
				// Knocked aside: the blade low and out.
				raised = new float[] {-0.3F, shoulder - 0.62F, 0.12F};
				raisedDir = norm(-0.6F, -0.5F, 0.4F);
			}
			case CROUCH -> {
				// Before the leap: the blade back over the shoulder.
				raised = new float[] {-0.16F, shoulder + 0.02F, -0.12F};
				raisedDir = norm(-0.2F, 0.3F, -0.95F);
			}
			default -> {
			}
		}

		// Phase 23: blade on blade: raised across towards the other (a clash), turned aside across
		// the body (a parry), pressed forward and up against the other's blade (the lock).
		float[] held = switch (gear.react()) {
			case CLASH -> new float[] {0.02F, shoulder - 0.08F, 0.44F, 0.3F, 0.82F, 0.48F};
			case PARRY -> new float[] {-0.06F, shoulder - 0.2F, 0.42F, 0.88F, 0.42F, 0.22F};
			case LOCK -> new float[] {0.0F, shoulder - 0.1F, 0.38F, 0.22F, 0.72F, 0.66F};
			default -> null;
		};

		if (held != null) {
			float k = gear.react() == LOCK ? 1.0F : Mth.clamp(Math.min(gear.reactP() * 5.0F, (1.0F - gear.reactP()) * 3.0F), 0.0F, 1.0F);
			float[] dir = norm(held[3], held[4], held[5]);

			for (int i = 0; i < 3; i++) {
				guard[i] = Mth.lerp(k, guard[i], held[i]);
				guardDir[i] = Mth.lerp(k, guardDir[i], dir[i]);
			}
		}

		if (gear.spin() > 0.0F) {
			// Spinning round: the guard's blade swept once round him.
			float a = gear.spin() * Mth.TWO_PI;
			float c = Mth.cos(a);
			float si = Mth.sin(a);
			guard = new float[] {guard[0] * c + guard[2] * si, guard[1], -guard[0] * si + guard[2] * c};
			guardDir = norm(guardDir[0] * c + guardDir[2] * si, 0.25F, -guardDir[0] * si + guardDir[2] * c);
		}
		float r = Mth.clamp(gear.raise(), 0.0F, 1.0F);
		float w = Mth.clamp(gear.swing(), 0.0F, 1.0F);
		float[] out = new float[6];

		for (int i = 0; i < 3; i++) {
			float p = Mth.lerp(r, guard[i], raised[i]);
			float d = Mth.lerp(r, guardDir[i], raisedDir[i]);
			out[i] = Mth.lerp(w, p, through[i]);
			out[i + 3] = Mth.lerp(w, d, throughDir[i]);
		}

		float[] dir = norm(out[3], out[4], out[5]);
		out[3] = dir[0];
		out[4] = dir[1];
		out[5] = dir[2];
		return out;
	}

	/** Phase 21: the kinds of a blow, as the server sends them ({@code Finale}). */
	static final int OVERHEAD = 1;
	static final int SWEEP = 2;
	static final int THRUST = 3;
	static final int CROUCH = 4;
	static final int HAND = 5;
	static final int STAGGER = 6;
	static final int SPIN = 7;

	/** Phase 19: a lightsaber: the hilt below {@code at}, the blade along its direction, glowing in its colour. */
	private static void lightsaber(MeshBuilder b, float[] at, int colour, float twin) {
		b.push().translate(at[0], at[1], at[2]);
		pointDown(b, -at[3], -at[4], -at[5]);
		// Now +y runs along the blade.
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.028F, -0.27F, -0.028F, 0.028F, 0.0F, 0.028F);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(-0.034F, -0.05F, -0.034F, 0.034F, 0.0F, 0.034F);
		b.box(-0.032F, -0.2F, -0.032F, 0.032F, -0.17F, 0.032F);

		if (twin > 0.0F) {
			// Phase 24: the second phase: the hilt is a staff, its second blade comes out of the lower end.
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(-0.028F, -0.52F, -0.028F, 0.028F, -0.27F, 0.028F);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(-0.034F, -0.52F, -0.034F, 0.034F, -0.47F, 0.034F);
			b.box(-0.032F, -0.36F, -0.032F, 0.032F, -0.33F, 0.032F);
		}

		blade(b, 0.0F, 1.0F, colour);

		if (twin > 0.0F) {
			blade(b, -0.52F, -0.52F - 0.95F * Math.min(1.0F, twin), colour);
		}

		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
		b.pop();
	}

	/** A blade from {@code from} to {@code to} along y: a white core in its colour's glow. */
	private static void blade(MeshBuilder b, float from, float to, int colour) {
		float lo = Math.min(from, to);
		float hi = Math.max(from, to);
		float out = Math.signum(to - from);
		b.layer(Layer.GLOW).fullBright().colour(0xFFFFFFFF).fit(InteriorAtlas.WHITE);
		b.box(-0.016F, lo, -0.016F, 0.016F, hi, 0.016F);
		b.layer(Layer.HOLO).fullBright().colourAlpha(0xB0000000 | colour & 0xFFFFFF).twoSided(true);
		b.box(-0.04F, Math.min(from - out * 0.01F, to + out * 0.03F), -0.04F, 0.04F, Math.max(from - out * 0.01F, to + out * 0.03F), 0.04F);
		b.colourAlpha(0x48000000 | colour & 0xFFFFFF);
		b.box(-0.075F, Math.min(from - out * 0.03F, to + out * 0.06F), -0.075F, 0.075F, Math.max(from - out * 0.03F, to + out * 0.06F), 0.075F);
	}

	/**
	 * Phase 23: a saber struck from a hand (no blade): the hilt at that spot (plot space), turned by
	 * yaw, tipped by pitch (90: lying flat). Its emitter is scorched where the blade broke off.
	 */
	static void looseHilt(MeshBuilder b, float x, float y, float z, float yaw, float pitch) {
		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
		b.push().translate(x, y, z).rotateY(-yaw).rotateX(pitch);
		// Its middle at the spot, +y along it towards the emitter.
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.028F, -0.135F, -0.028F, 0.028F, 0.135F, 0.028F);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(-0.034F, 0.085F, -0.034F, 0.034F, 0.135F, 0.034F);
		b.box(-0.032F, -0.065F, -0.032F, 0.032F, -0.035F, 0.032F);
		b.fit(InteriorAtlas.IMP_BLACK).colour(0xFF3A2A22);
		b.box(-0.022F, 0.135F, -0.022F, 0.022F, 0.15F, 0.022F);
		b.white();
		b.pop();
	}

	/** The way the rifle points (figure space): forward, to the left, a little up. */
	static final float[] RIFLE_DIR = norm(0.58F, 0.16F, 0.8F);
	/** How far along the rifle the left hand holds it. */
	static final float FOREGRIP = 0.27F;

	/** Where the right hand holds the rifle's grip (body space: from the hip). */
	private static float[] rifleGrip(boolean walking, float swing, float t, int seed) {
		float sway = walking ? Mth.sin(swing * Mth.DEG_TO_RAD * 2.0F) * 0.01F : Mth.sin(t * 0.7F + seed) * 0.006F;
		return new float[] {-0.13F, 0.3F + sway, 0.27F};
	}

	/** Where a hand goes (body space, the origin at the hip). */
	private static float[] hand(Crew crew, int side, float shoulder, float swing, float t, int seed, boolean rifle, boolean walking) {
		String pose = crew.pose();
		float sx = side * (HALF_W + LIMB * 0.9F);

		if (Crew.WORK.equals(pose) || Crew.SIT.equals(pose) && !Crew.TROOPER.equals(crew.kind())) {
			// Hands on the console, typing.
			float reach = Crew.WOOKIEE.equals(crew.kind()) ? 0.2F : 0.0F;
			float tap = Mth.sin(t * (5.0F + side) + seed * 1.3F) * 0.025F;
			float slide = Mth.sin(t * 0.8F + side * 2.0F + seed) * 0.05F;
			return new float[] {side * 0.15F + slide, shoulder - 0.32F - reach + Math.max(0.0F, tap), 0.42F};
		}

		if (rifle) {
			float[] grip = rifleGrip(walking, swing, t, seed);

			if (side < 0) {
				return new float[] {grip[0], grip[1] + 0.02F, grip[2]};
			}

			return new float[] {grip[0] + RIFLE_DIR[0] * FOREGRIP, grip[1] + RIFLE_DIR[1] * FOREGRIP - 0.03F, grip[2] + RIFLE_DIR[2] * FOREGRIP};
		}

		if (Crew.GUARD.equals(crew.kind())) {
			// The right hand on the pike, the left at the side under the robe.
			return side < 0 ? new float[] {-HALF_W - 0.16F, shoulder - 0.36F, 0.16F} : new float[] {sx + 0.03F, shoulder - 0.6F, 0.04F};
		}

		if (Crew.TALK.equals(pose)) {
			// One hand making a point, the other on the hip or by the side.
			if (side > 0) {
				float lift = Mth.sin(t * 2.1F + seed) * 0.08F + Mth.sin(t * 3.3F + seed) * 0.03F;
				return new float[] {0.24F, shoulder - 0.3F + lift, 0.3F + lift * 0.5F};
			}

			return Crew.SMUGGLER.equals(crew.kind()) ? new float[] {sx - 0.08F, shoulder - 0.52F, 0.08F} : new float[] {sx + 0.02F, shoulder - 0.6F, 0.06F};
		}

		if (walking) {
			// Swinging opposite the leg on that side.
			float a = -side * swing * 0.75F * Mth.DEG_TO_RAD;
			float len = UPPER + FORE - 0.04F;
			return new float[] {sx + side * 0.02F, shoulder - Mth.cos(a) * len, -Mth.sin(a) * len + 0.05F};
		}

		if (crew.officer()) {
			// Hands clasped behind the back.
			return new float[] {side * 0.06F, shoulder - 0.42F, -HALF_D - 0.1F};
		}

		// Hands by the side, a little forward.
		return new float[] {sx + side * 0.03F, shoulder - 0.6F, 0.05F + Mth.sin(t * 0.5F + seed + side) * 0.01F};
	}

	/** Which way the elbow bends: out and down, behind the back out and back. */
	private static float[] pole(Crew crew, int side, boolean rifle, boolean walking) {
		if (rifle) {
			return new float[] {side * 0.8F, -1.0F, -0.15F};
		}

		if (crew.officer() && Crew.STAND.equals(crew.pose()) && !walking) {
			return new float[] {side * 1.0F, -0.4F, 0.2F};
		}

		return new float[] {side * 0.35F, -0.3F, -1.0F};
	}

	/** An arm from the shoulder to the hand, the elbow bent towards {@code pole}. */
	private static void arm(MeshBuilder b, Look look, float sx, float sy, float sz, float hx, float hy, float hz, float[] pole) {
		float dx = hx - sx;
		float dy = hy - sy;
		float dz = hz - sz;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float max = UPPER + FORE - 0.002F;

		if (d > max) {
			dx *= max / d;
			dy *= max / d;
			dz *= max / d;
			d = max;
		}

		float nx = dx / d;
		float ny = dy / d;
		float nz = dz / d;
		// The elbow: off the line by the bend, towards the pole.
		float along = (UPPER * UPPER - FORE * FORE + d * d) / (2.0F * d);
		float off = (float) Math.sqrt(Math.max(0.0F, UPPER * UPPER - along * along));
		float dot = pole[0] * nx + pole[1] * ny + pole[2] * nz;
		float[] p = norm(pole[0] - dot * nx, pole[1] - dot * ny, pole[2] - dot * nz);
		float ex = sx + nx * along + p[0] * off;
		float ey = sy + ny * along + p[1] * off;
		float ez = sz + nz * along + p[2] * off;

		tint(b, look.tint());
		b.fit(look.arm(), 0.0F, 0.0F, 1.0F, 0.5F);
		segment(b, sx, sy, sz, ex, ey, ez, LIMB * 0.95F);
		b.fit(look.arm(), 0.0F, 0.5F, 1.0F, 1.0F);
		segment(b, ex, ey, ez, sx + dx, sy + dy, sz + dz, LIMB * 0.88F);
		b.white();
	}

	/** A leg: the thigh swung by {@code thigh} degrees, the knee bent by {@code knee}. */
	private static void legs(MeshBuilder b, Look look, int side, float hip, boolean sit, float thigh, float knee) {
		float half = LEG / 2.0F;
		b.push().translate(side * LIMB, hip, 0);
		b.rotateX(sit ? -90.0F : thigh);
		tint(b, look.tint());
		b.fit(look.leg(), 0.0F, 0.0F, 1.0F, 0.5F);
		b.box(-LIMB, -half - 0.01F, -LIMB, LIMB, 0, LIMB);
		b.translate(0, -half, 0).rotateX(sit ? 90.0F : knee);
		b.fit(look.leg(), 0.0F, 0.5F, 1.0F, 1.0F);
		b.box(-LIMB * 0.97F, -half, -LIMB * 0.97F, LIMB * 0.97F, 0.01F, LIMB * 0.97F);
		// The foot, a little forward.
		b.box(-LIMB * 0.97F, -half, LIMB * 0.97F, LIMB * 0.97F, -half + 0.08F, LIMB * 0.97F + 0.05F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
		b.pop();
		b.white();
	}

	/** A box from a to c (a limb), {@code half} thick. */
	private static void segment(MeshBuilder b, float ax, float ay, float az, float cx, float cy, float cz, float half) {
		float dx = cx - ax;
		float dy = cy - ay;
		float dz = cz - az;
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (len < 1.0E-4F) {
			return;
		}

		b.push().translate(ax, ay, az);
		pointDown(b, dx / len, dy / len, dz / len);
		b.box(-half, -len - half * 0.4F, -half, half, half * 0.4F, half);
		b.pop();
	}

	/** Turns so that the local -y points along the unit vector (x, y, z). */
	private static void pointDown(MeshBuilder b, float x, float y, float z) {
		float az = (float) Math.asin(Mth.clamp(x, -1.0F, 1.0F));
		float ax = (float) Math.atan2(-z, -y);
		b.rotateX(ax * Mth.RAD_TO_DEG).rotateZ(az * Mth.RAD_TO_DEG);
	}

	/** A blaster rifle: the grip at (x, y, z), the barrel along (dx, dy, dz). */
	private static void blaster(MeshBuilder b, float x, float y, float z, float dx, float dy, float dz) {
		b.push().translate(x, y, z);
		pointDown(b, dx, dy, dz);
		// Along -y: the stock behind the grip, the body, the barrel; "down" for the rifle is -z here.
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.035F, -0.36F, -0.02F, 0.035F, 0.12F, 0.075F);
		// The grip, under the body at the hand.
		b.box(-0.025F, -0.04F, -0.12F, 0.025F, 0.02F, -0.02F);
		// The magazine sticking out to the left, the sight on top.
		b.box(0.035F, -0.2F, 0.0F, 0.11F, -0.16F, 0.04F);
		b.box(-0.015F, -0.14F, 0.075F, 0.015F, -0.02F, 0.11F);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(-0.022F, -0.56F, 0.012F, 0.022F, -0.36F, 0.056F);
		b.pop();
	}

	/** Phase 23: a blaster pistol: the grip at (x, y, z), the barrel along (dx, dy, dz). */
	private static void pistol(MeshBuilder b, float x, float y, float z, float dx, float dy, float dz) {
		b.push().translate(x, y, z);
		pointDown(b, dx, dy, dz);
		// Along -y: the body forward from the hand, the grip down (-z).
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.025F, -0.2F, -0.01F, 0.025F, 0.04F, 0.05F);
		b.box(-0.02F, -0.02F, -0.1F, 0.02F, 0.03F, -0.01F);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(-0.014F, -0.27F, 0.008F, 0.014F, -0.2F, 0.036F);
		b.pop();
	}

	/** A head: its face to the front, its own top and bottom; a cap on officers. */
	private static void head(MeshBuilder b, Look look, float grow) {
		float h = HEAD / 2.0F + grow;
		float top = HEAD + grow;
		b.white().fit(look.head());
		b.box(-h, 0, -h, h, top, h, MeshBuilder.EAST | MeshBuilder.WEST | MeshBuilder.NORTH);
		b.fit(look.face());
		b.box(-h, 0, -h, h, top, h, MeshBuilder.SOUTH);
		// Underneath: the chin (the lowest part of the head's side tile).
		b.fit(look.bottom(), 0.0F, 0.85F, 1.0F, 1.0F);
		b.box(-h, 0, -h, h, top, h, MeshBuilder.DOWN);

		if (look.cap()) {
			// The cap: a flat crown over the hair, a short peak; the rank disc on its front.
			tint(b, look.tint()).fit(InteriorAtlas.CREW_CAP);
			b.box(-h - 0.02F, top - 0.09F, -h - 0.02F, h + 0.02F, top + 0.05F, h + 0.02F);
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(-h + 0.01F, top - 0.09F, h + 0.02F, h - 0.01F, top - 0.06F, h + 0.09F);
			b.white();
		} else {
			if (look.hairTint() != 0) {
				b.colour(look.hairTint());
			}

			b.fit(look.top());
			b.box(-h, 0, -h, h, top, h, MeshBuilder.UP);
			b.white();
		}
	}

	private static MeshBuilder tint(MeshBuilder b, int colour) {
		return colour != 0 ? b.colour(colour) : b.white();
	}

	static float[] norm(float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		return l < 1.0E-6F ? new float[] {0.0F, -1.0F, 0.0F} : new float[] {x / l, y / l, z / l};
	}
}
