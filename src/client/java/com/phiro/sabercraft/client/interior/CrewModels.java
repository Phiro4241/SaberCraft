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
			float hand, float spin, int react, float reactP, boolean pistol, boolean unarmed, float twin, boolean inquisitor, float ring, float blur,
			float flare) {
		static final Gear NONE = new Gear(false, 0, false, 0.0F, 0.0F, false, false, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

		/**
		 * Phase 24's gear (a saber staff's second blade). Phase 26: {@code inquisitor}: the saber is
		 * an Inquisitor's ({@link InquisitorSaber}): its ring turned by {@code ring} (radians),
		 * spinning fast enough to blur ({@code blur} 0..1), its blades flaring ({@code flare} 0..1).
		 */
		Gear(boolean lamp, int saber, boolean lit, float raise, float swing, boolean down, boolean skirt, int attack, float aim, float crouch, float lean,
				float hand, float spin, int react, float reactP, boolean pistol, boolean unarmed, float twin) {
			this(lamp, saber, lit, raise, swing, down, skirt, attack, aim, crouch, lean, hand, spin, react, reactP, pistol, unarmed, twin, false, 0.0F, 0.0F,
					0.0F);
		}

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
	/** Phase 25: her saber staff drawn back to be thrown (twirling over the shoulder), and away in flight (the hand out after it). */
	static final int THROW_WIND = 11;
	static final int THROWN = 12;
	/** Phase 26: the ring's rush (as the server's {@code Figure.RING}): the ring spins up, then she drives in behind it. */
	static final int RING = 13;
	/** Phase 29: her slam winding up (as the server's {@code Figure.SLAM}): crouching, leaning back, the blade high. */
	static final int SLAM = 14;
	/** Phase 26: how much of the ring's rush is its spinning up (as the server's wind-up of 18 of its 42 ticks). */
	static final float RING_WIND = 18.0F / 42.0F;

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
			// Phase 29: the slam: she leans back as the blade goes up high, more and more (it falls forward when it ends).
			case SLAM -> 9.0F * Mth.clamp(p * 1.6F, 0.0F, 1.0F) + Mth.sin(t * 30.0F + seed) * 0.6F * p;
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
			b.box(-HALF_W - ROBE_SIDE, hip + ROBE_BOTTOM, -HALF_D - ROBE_BACK, HALF_W + ROBE_SIDE, hip + ROBE_TOP, HALF_D + ROBE_FRONT, MeshBuilder.ALL & ~MeshBuilder.UP);
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
			b.rotateX(tilt(gear, reactLean));

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
		// Phase 25: her saber staff (the second phase): held in the middle with both hands, a blade
		// at each end, kept clear of her body; or thrown (the hand out after it, the staff in flight).
		boolean thrown = saber && gear.twin() > 0.0F && gear.react() == THROWN;
		float[] staff = saber && gear.twin() > 0.0F && !thrown ? staffHold(shoulder, gear, hilt) : null;

		if (thrown) {
			hilt = null;
		}

		// Phase 26: her Inquisitor's saber: the ring where the single blade's emitter was, or in the
		// middle of where the staff was held; kept out of her body at the ring's angle now.
		InquisitorSaber.Hold ring = null;
		float second = Mth.clamp((gear.twin() - 0.35F) / 0.65F, 0.0F, 1.0F);

		if (gear.inquisitor() && !thrown && (staff != null || hilt != null)) {
			ring = ringHold(shoulder, gear, staff != null ? InquisitorSaber.centred(staff) : InquisitorSaber.fromEmitter(hilt));
			// (Phase 30: against her body as drawn: her torso leaning over her hips as it is, the robe
			// and legs below it, her head turned as it is drawn below; eased in and out, see steady.)
			float headYaw = Float.isNaN(headTurn) || gear.down() ? glance : headTurn;
			ring = InquisitorSaber.steady(seed, t, ring, gear.ring(), second > 0.0F, gear.blur(), Lean.of(tilt(gear, reactLean), reactRoll, headYaw));
		}

		float fling = gear.down() ? 0.0F : reactFling(gear);
		boolean pistol = gear.pistol() && !gear.down() && !sit;

		for (int side = -1; side <= 1; side += 2) {
			float sx = side * (HALF_W + LIMB * 0.9F);
			float[] hand = hilt != null ? new float[] {hilt[0] - hilt[3] * (side < 0 ? 0.05F : 0.17F), hilt[1] - hilt[4] * (side < 0 ? 0.05F : 0.17F),
					hilt[2] - hilt[5] * (side < 0 ? 0.05F : 0.17F)} : hand(crew, side, shoulder, swing, t, seed, rifle, walking);

			if (ring != null) {
				// Phase 26: both hands on the grip behind the ring, the right nearer it (drawn back to
				// throw: the right hand alone).
				hand = side < 0 ? ring.grip(InquisitorSaber.HAND)
						: gear.react() == THROW_WIND ? hand(crew, side, shoulder, 0.0F, t, seed, false, false) : ring.grip(InquisitorSaber.HAND_2);
			} else if (staff != null) {
				// Phase 25: the right hand on the half of the staff to her right, the left on the other
				// (drawn back to throw: the right hand alone, at its middle).
				float k = staff[3] <= 0.0F ? 1.0F : -1.0F;
				float at = gear.react() == THROW_WIND ? (side < 0 ? 0.0F : Float.NaN) : side < 0 ? 0.17F * k : -0.17F * k;
				hand = Float.isNaN(at) ? hand(crew, side, shoulder, 0.0F, t, seed, false, false)
						: new float[] {staff[0] + staff[3] * at, staff[1] + staff[4] * at, staff[2] + staff[5] * at};
			} else if (thrown && side < 0) {
				// Phase 25: the staff thrown: her right hand out after it (she guides it back with the Force).
				float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);
				float out = Mth.clamp(Math.min(p * 6.0F, 1.0F), 0.0F, 1.0F);
				hand = new float[] {Mth.lerp(out, hand[0], -0.14F), Mth.lerp(out, hand[1], shoulder + 0.06F), Mth.lerp(out, hand[2], 0.6F)};
			}

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

			if ((hilt != null || ring != null) && side > 0 && gear.hand() > 0.0F) {
				// Phase 21: the free hand out towards the player (the Force push coming).
				float k = Mth.clamp(gear.hand(), 0.0F, 1.0F);
				hand = new float[] {Mth.lerp(k, hand[0], sx * 0.6F), Mth.lerp(k, hand[1], shoulder + 0.02F), Mth.lerp(k, hand[2], 0.62F)};
			} else if (rifle && gear.aim() > 0.0F) {
				// Phase 21: the rifle at the shoulder, aimed.
				float k = Mth.clamp(gear.aim(), 0.0F, 1.0F);
				hand = new float[] {hand[0] * (1.0F - k * 0.4F), hand[1] + k * 0.24F, hand[2] + k * 0.04F};
			}
			float[] pole = hilt != null || staff != null ? new float[] {side * 0.9F, -0.8F, -0.2F} : pole(crew, side, rifle, walking);
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

		if (ring != null) {
			InquisitorSaber.draw(b, ring, gear.ring(), 1.0F, second, gear.saber(), gear.blur(), gear.flare());
		} else if (staff != null) {
			saberStaff(b, staff, gear.saber(), gear.twin());
		} else if (hilt != null) {
			lightsaber(b, hilt, gear.saber(), 0.0F);
		} else if (thrown) {
			// (In flight: drawn where it flies, see StoryModels.)
		} else if (gear.inquisitor() && !gear.unarmed()) {
			// Phase 26: her Inquisitor's saber carried on her back before the duel.
			InquisitorSaber.carried(b, shoulder);
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
	 * after it. Returns x, y, z of the hilt's top and the blade's direction. (Phase 25: not private,
	 * for {@link MeshAudit#staffInBody}.)
	 */
	static float[] saberHold(float shoulder, Gear gear) {
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

	/** Phase 26: the Schattenklinge's saber is an Inquisitor's ({@link InquisitorSaber}): thrown and struck from her hand too. */
	static final boolean INQUISITOR = true;

	/** Phase 25: the half length of her saber staff, and the length of each blade. */
	static final float STAFF_HALF = 0.5F;
	static final float STAFF_BLADE = 0.95F;

	/**
	 * Phase 25: how she holds her saber staff in the second phase (body space): its middle and the
	 * direction of the first blade. Held in both hands across her front, at an angle (one blade up
	 * to her right, the other down to her left); raised above the head, drawn back for a sweep,
	 * pulled in at the hip for a thrust as the single blade is; twirled round in a spin; drawn back
	 * over the shoulder, twirling, before a throw. Wherever the animation takes it, neither the
	 * staff nor a blade goes through her: it is moved out in front of her if it would.
	 *
	 * <p>As the second blade comes out (the start of the second phase) the staff moves from where
	 * the single blade was held to its own hold.
	 */
	static float[] staffHold(float shoulder, Gear gear, float @org.jspecify.annotations.Nullable [] single) {
		float[] guardC = {0.0F, shoulder - 0.3F, 0.44F};
		float[] guardD = norm(-0.8F, 0.5F, 0.3F);
		float[] raisedC = {0.0F, shoulder + 0.36F, 0.38F};
		float[] raisedD = norm(-0.95F, 0.2F, -0.15F);
		float[] throughC = {0.04F, shoulder - 0.42F, 0.5F};
		float[] throughD = norm(-0.55F, -0.75F, 0.35F);

		switch (gear.attack()) {
			case SWEEP -> {
				// Drawn round to her right, flat: it sweeps round to her left.
				raisedC = new float[] {-0.15F, shoulder - 0.2F, 0.42F};
				raisedD = norm(-0.9F, 0.1F, -0.42F);
				throughC = new float[] {0.15F, shoulder - 0.25F, 0.45F};
				throughD = norm(0.9F, 0.05F, -0.42F);
			}
			case THRUST -> {
				// Pulled in at the right hip, a blade forward: it comes straight.
				raisedC = new float[] {-0.34F, shoulder - 0.42F, 0.2F};
				raisedD = norm(0.03F, 0.05F, 1.0F);
				throughC = new float[] {-0.22F, shoulder - 0.3F, 0.6F};
				throughD = norm(0.05F, 0.03F, 1.0F);
			}
			case STAGGER -> {
				raisedC = new float[] {0.0F, shoulder - 0.55F, 0.42F};
				raisedD = norm(-0.9F, -0.3F, 0.2F);
			}
			case CROUCH -> {
				raisedC = new float[] {0.0F, shoulder + 0.05F, 0.3F};
				raisedD = norm(-0.6F, 0.75F, -0.25F);
			}
			default -> {
			}
		}

		// Blade on blade: the staff upright before her (a clash, the lock), across (a parry).
		float[] held = switch (gear.react()) {
			case CLASH -> new float[] {0.0F, shoulder - 0.1F, 0.46F, -0.3F, 0.9F, 0.3F};
			case PARRY -> new float[] {0.0F, shoulder - 0.2F, 0.44F, -0.85F, 0.45F, 0.2F};
			case LOCK -> new float[] {0.0F, shoulder - 0.1F, 0.45F, -0.25F, 0.85F, 0.45F};
			default -> null;
		};

		if (held != null) {
			float k = gear.react() == LOCK ? 1.0F : Mth.clamp(Math.min(gear.reactP() * 5.0F, (1.0F - gear.reactP()) * 3.0F), 0.0F, 1.0F);
			float[] dir = norm(held[3], held[4], held[5]);

			for (int i = 0; i < 3; i++) {
				guardC[i] = Mth.lerp(k, guardC[i], held[i]);
				guardD[i] = Mth.lerp(k, guardD[i], dir[i]);
			}
		}

		float r = Mth.clamp(gear.raise(), 0.0F, 1.0F);
		float w = Mth.clamp(gear.swing(), 0.0F, 1.0F);
		float[] out = new float[6];

		for (int i = 0; i < 3; i++) {
			out[i] = Mth.lerp(w, Mth.lerp(r, guardC[i], raisedC[i]), throughC[i]);
			out[i + 3] = Mth.lerp(w, Mth.lerp(r, guardD[i], raisedD[i]), throughD[i]);
		}

		if (gear.react() == THROW_WIND && !gear.inquisitor()) {
			// Drawn back over the right shoulder, twirling in her hand faster and faster.
			// (Phase 26: an Inquisitor's saber is not twirled: its ring spins, see ringHold.)
			float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);
			float k = Mth.clamp(p * 2.5F, 0.0F, 1.0F);
			float a = p * p * 6.0F * Mth.PI;
			float[] back = {-0.42F, shoulder + 0.16F, -0.02F};
			float[] twirl = {0.0F, Mth.cos(a), Mth.sin(a)};

			for (int i = 0; i < 3; i++) {
				out[i] = Mth.lerp(k, out[i], back[i]);
				out[i + 3] = Mth.lerp(k, out[i + 3], twirl[i]);
			}
		}

		if (gear.spin() > 0.0F && !gear.inquisitor()) {
			// The spin: twirled twice round before her while she turns once round.
			// (Phase 26: an Inquisitor's saber is not twirled by the arm: its ring spins round the grip.)
			float a = gear.spin() * 4.0F * Mth.PI;
			float c = Mth.cos(a);
			float si = Mth.sin(a);
			float dx = out[3] * c - out[4] * si;
			float dy = out[3] * si + out[4] * c;
			out[3] = dx;
			out[4] = dy;
			float turn = gear.spin() * Mth.TWO_PI;
			float tc = Mth.cos(turn);
			float ts = Mth.sin(turn);
			float cx = out[0] * tc + out[2] * ts;
			float cz = -out[0] * ts + out[2] * tc;
			out[0] = cx;
			out[2] = cz;
			float ddx = out[3] * tc + out[5] * ts;
			float ddz = -out[3] * ts + out[5] * tc;
			out[3] = ddx;
			out[5] = ddz;
		}

		float twin = Mth.clamp(gear.twin() / 0.35F, 0.0F, 1.0F);

		if (single != null && twin < 1.0F) {
			// The second blade comes out: from the single blade's hold (the staff's top at its emitter) to the staff's.
			// (Phase 26: an Inquisitor's saber: from where its ring was behind the single blade's emitter.)
			float e = twin * twin * (3.0F - 2.0F * twin);
			float back = gear.inquisitor() ? InquisitorSaber.EMIT : STAFF_HALF;
			float[] from = {single[0] - single[3] * back, single[1] - single[4] * back, single[2] - single[5] * back};

			for (int i = 0; i < 3; i++) {
				out[i] = Mth.lerp(e, from[i], out[i]);
				out[i + 3] = Mth.lerp(e, single[i + 3], out[i + 3]);
			}
		}

		float[] dir = norm(out[3], out[4], out[5]);
		out[3] = dir[0];
		out[4] = dir[1];
		out[5] = dir[2];

		// (Phase 26: an Inquisitor's saber is kept out of her body by its own shape, see InquisitorSaber.clear.)
		if (!gear.inquisitor()) {
			clearOfBody(out, shoulder);
		}

		return out;
	}

	/**
	 * Phase 26: where she holds her Inquisitor's saber beyond the blade's own hold: before the ring's
	 * rush it comes up before her chest, the grip pointing at the player, so that the spinning ring
	 * is a disc of blades between them; in a spin it is raised before her face the same way; drawn
	 * back to throw, the grip points up over her shoulder (the ring spinning flat behind her head;
	 * Phase 30: above it). Phase 30: while the ring spins fast, its disc stands upright before her
	 * (unless she holds it high). Elsewhere the blade's hold as it is (the ring's angle does the rest).
	 */
	static InquisitorSaber.Hold ringHold(float shoulder, Gear gear, InquisitorSaber.Hold hold) {
		if (gear.blur() > 0.0F) {
			// Phase 30: while the ring spins fast (its blades a disc round the grip) the grip comes
			// level and a little up, so that the disc stands upright before her: tilted back with the
			// grip, its upper half swept through her head (and to keep it out it had to be held so far
			// off that her hands no longer reached the grip). Fully so from a quarter of the blur on;
			// not with the grip held high (an overhead blow's wind-up: over her head the disc is clear
			// of it as it is, and levelled there the grip went out of her hands).
			float u = Mth.clamp(gear.blur() * 4.0F, 0.0F, 1.0F) * Mth.clamp((shoulder + DISC_HIGH - hold.c()[1]) / DISC_FADE, 0.0F, 1.0F);
			float[] hc = hold.c();
			float[] ha = hold.a();
			hold = InquisitorSaber.of(new float[] {hc[0], hc[1] + DISC_RAISE * u, hc[2]}, norm(ha[0], ha[1] * Mth.lerp(u, 1.0F, DISC_UPRIGHT), ha[2]),
					hold.d());
		}

		float k = 0.0F;
		float[] c = null;
		float[] a = null;
		float[] d = null;

		if (gear.react() == RING) {
			float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);
			// Up quickly as it spins up, held through the rush, down at its very end.
			k = Mth.clamp(Math.min(p / RING_WIND * 1.6F, (1.0F - p) * 10.0F), 0.0F, 1.0F);
			c = new float[] {-0.04F, shoulder - 0.12F, 0.56F};
			a = new float[] {0.0F, 0.12F, 1.0F};
			d = new float[] {0.0F, 1.0F, 0.0F};
		} else if (gear.spin() > 0.0F) {
			k = Mth.sin(Mth.clamp(gear.spin(), 0.0F, 1.0F) * Mth.PI);
			k = Math.max(k, gear.spin() < 0.5F ? Mth.clamp(gear.spin() * 4.0F, 0.0F, 1.0F) : k);
			c = new float[] {0.0F, shoulder - 0.02F, 0.52F};
			a = new float[] {0.0F, 0.3F, 1.0F};
			d = new float[] {-1.0F, 0.0F, 0.0F};
		} else if (gear.react() == THROW_WIND) {
			float p = Mth.clamp(gear.reactP(), 0.0F, 1.0F);
			k = Mth.clamp(p * 2.5F, 0.0F, 1.0F);
			// (Phase 30: over her head, not round it: at the head's height its spinning disc swept
			// through her head. Raised 0.35; the hand still reaches the grip.)
			c = new float[] {-0.4F, shoulder + 0.63F, -0.06F};
			a = new float[] {-0.15F, 1.0F, 0.1F};
			d = new float[] {0.0F, 0.0F, 1.0F};
		}

		if (k <= 0.0F || c == null) {
			return hold;
		}

		float e = k * k * (3.0F - 2.0F * k);
		float[] hc = hold.c();
		float[] ha = hold.a();
		float[] hd = hold.d();
		float[] an = norm(a[0], a[1], a[2]);
		float[] dn = norm(d[0], d[1], d[2]);
		return InquisitorSaber.of(new float[] {Mth.lerp(e, hc[0], c[0]), Mth.lerp(e, hc[1], c[1]), Mth.lerp(e, hc[2], c[2])},
				new float[] {Mth.lerp(e, ha[0], an[0]), Mth.lerp(e, ha[1], an[1]), Mth.lerp(e, ha[2], an[2])},
				new float[] {Mth.lerp(e, hd[0], dn[0]), Mth.lerp(e, hd[1], dn[1]), Mth.lerp(e, hd[2], dn[2])});
	}

	/** Phase 30: the spinning disc stood upright: how much of the grip's upward tilt stays, and how far it is raised (blocks). */
	static final float DISC_UPRIGHT = 0.2F;
	static final float DISC_RAISE = 0.18F;
	/**
	 * Phase 30: up to how high over the shoulder the grip's middle is held for the disc to be stood
	 * upright fully, and over how much more it fades out (blocks).
	 */
	static final float DISC_HIGH = 0.2F;
	static final float DISC_FADE = 0.3F;

	/**
	 * Phase 25: moves the staff away from her, the way its middle lies from her (in front of her,
	 * at her side or, in a spin, behind her back), as far as needed for no part of it (staff and
	 * both blades) to be in her body (body space: the torso, the head over it, the legs under it).
	 * (Pushed only to the front, a staff behind her in a spin was pushed through her.)
	 */
	static void clearOfBody(float[] staff, float shoulder) {
		float rx = staff[0];
		float rz = staff[2];
		float length = Mth.sqrt(rx * rx + rz * rz);

		if (length < 0.05F) {
			rx = 0.0F;
			rz = 1.0F;
		} else {
			rx /= length;
			rz /= length;
		}

		for (int i = 0; i < 80 && staffInBody(staff, shoulder); i++) {
			staff[0] += rx * 0.025F;
			staff[2] += rz * 0.025F;
		}
	}

	/** Phase 25: whether a part of the staff (or its blades) lies in her body. */
	static boolean staffInBody(float[] staff, float shoulder) {
		for (float t = -(STAFF_HALF + STAFF_BLADE); t <= STAFF_HALF + STAFF_BLADE + 0.001F; t += 0.05F) {
			if (inBody(staff[0] + staff[3] * t, staff[1] + staff[4] * t, staff[2] + staff[5] * t, shoulder)) {
				return true;
			}
		}

		return false;
	}

	/** Phase 25: whether that point (body space) is in her body. */
	static boolean inBody(float x, float y, float z, float shoulder) {
		float half = HALF_W + 0.06F;
		float depth = HALF_D + 0.06F;
		return Math.abs(x) < half && Math.abs(z) < depth && y > -LEG && y < shoulder + 0.06F + HEAD;
	}

	/**
	 * Phase 30: whether that point (body space: her torso's) comes within {@code margin} of her body
	 * as she is drawn: the torso; the head (a cube of {@link #HEAD}) turned as her look turns it now
	 * ({@code lean}'s head turn; it was a box only as deep as the torso, and her head is deeper than
	 * that); the robe over her legs and the legs under it, which stay as they are when her torso
	 * leans over its hips ({@code lean}: thrown back by a hit, crouching): the point is turned into
	 * their space first. For her Inquisitor's saber ({@link InquisitorSaber#clear}): Phiro saw its
	 * blades in her head and her belly. (Her saber staff of Phase 25 keeps {@link #inBody}.)
	 */
	static boolean inDrawnBody(float x, float y, float z, Lean lean, float margin) {
		float m = margin;

		// (Its top too, at the shoulders: the head over it is no wider than the torso, and turned it
		// no longer covers the torso's corners there.)
		if (y >= -m && y < BODY + m && Math.abs(x) < HALF_W + m && Math.abs(z) < HALF_D + m) {
			return true;
		}

		if (y >= BODY - m && y < BODY + HEAD + m) {
			float hx = x * lean.hc() - z * lean.hs();
			float hz = x * lean.hs() + z * lean.hc();
			float h = HEAD * 0.5F + m;

			if (Math.abs(hx) < h && Math.abs(hz) < h) {
				return true;
			}
		}

		// The robe (from just over the hips to the knees) and the legs: in the hips' space.
		float x1 = x * lean.cr() - y * lean.sr();
		float y1 = x * lean.sr() + y * lean.cr();
		float hy = y1 * lean.ct() - z * lean.st();
		float hz = y1 * lean.st() + z * lean.ct();
		return hy > -LEG && hy < ROBE_TOP + m && Math.abs(x1) < HALF_W + ROBE_SIDE + m && hz > -(HALF_D + ROBE_BACK + m) && hz < HALF_D + ROBE_FRONT + m;
	}

	/** Phase 30: her robe as drawn round her hips (see {@link #figure}): its top over the hips, how far it stands out at the sides, behind and in front. */
	static final float ROBE_TOP = 0.02F;
	static final float ROBE_BOTTOM = -0.42F;
	static final float ROBE_SIDE = 0.03F;
	static final float ROBE_BACK = 0.04F;
	static final float ROBE_FRONT = 0.05F;

	/**
	 * Phase 30: how her torso is turned over her hips as drawn ({@link #tilt} about x, then the
	 * roll about z, degrees), and her head on her torso (its turn about y, degrees), as cosines and
	 * sines: her robe and legs stay below the torso, her head turns with her look.
	 */
	record Lean(float ct, float st, float cr, float sr, float hc, float hs) {
		static final Lean NONE = new Lean(1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F);

		static Lean of(float tilt, float roll, float head) {
			float t = tilt * Mth.DEG_TO_RAD;
			float r = roll * Mth.DEG_TO_RAD;
			float h = head * Mth.DEG_TO_RAD;
			return new Lean(Mth.cos(t), Mth.sin(t), Mth.cos(r), Mth.sin(r), Mth.cos(h), Mth.sin(h));
		}

		/**
		 * As her torso is drawn in that gear for the figure {@code seed} at the moment {@code t}
		 * (seconds): its lean over the hips and its roll aside (a heavy blow's roll one way or the
		 * other, a stumble's sway, a lock's or slam's tremble), her head turned by {@code head}
		 * degrees.
		 */
		static Lean of(Gear gear, float head, float t, int seed) {
			float reactLean = gear.down() ? 0.0F : reactLean(gear, t, seed);
			float reactRoll = gear.down() ? 0.0F : reactRoll(gear, seed);
			return of(tilt(gear, reactLean), reactRoll, head);
		}
	}

	/** Phase 30: how far her torso is turned over her hips about x (degrees, as drawn): leaning back, crouching, aiming, thrown back. */
	static float tilt(Gear gear, float reactLean) {
		return -gear.lean() * 16.0F + gear.crouch() * 22.0F + gear.aim() * 4.0F - reactLean;
	}

	/**
	 * Phase 25: her saber staff: the hilt a staff held in the middle, a blade out of each end (the
	 * second as far as it is out, {@code twin} 0..1, while it comes out).
	 */
	private static void saberStaff(MeshBuilder b, float[] staff, int colour, float twin) {
		b.push().translate(staff[0], staff[1], staff[2]);
		pointDown(b, -staff[3], -staff[4], -staff[5]);
		staffHilt(b);
		blade(b, STAFF_HALF, STAFF_HALF + STAFF_BLADE, colour);
		float second = Mth.clamp((twin - 0.35F) / 0.65F, 0.0F, 1.0F);

		if (second > 0.0F) {
			blade(b, -STAFF_HALF, -STAFF_HALF - STAFF_BLADE * second, colour);
		}

		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
		b.pop();
	}

	/** Phase 25: the staff's hilt along y (its middle at 0): black, metal bands by the grips and at the emitters. */
	static void staffHilt(MeshBuilder b) {
		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.028F, -STAFF_HALF, -0.028F, 0.028F, STAFF_HALF, 0.028F);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);

		for (int end = -1; end <= 1; end += 2) {
			float e = end * STAFF_HALF;
			b.box(-0.034F, Math.min(e, e - end * 0.05F), -0.034F, 0.034F, Math.max(e, e - end * 0.05F), 0.034F);
			b.box(-0.032F, Math.min(end * 0.3F, end * 0.33F), -0.032F, 0.032F, Math.max(end * 0.3F, end * 0.33F), 0.032F);
		}

		b.box(-0.031F, -0.03F, -0.031F, 0.031F, 0.03F, 0.031F);
	}

	/**
	 * Phase 25: the thrown saber staff in flight (plot space): spinning flat round its middle as it
	 * flies, both blades lit.
	 */
	static void flyingStaff(MeshBuilder b, float x, float y, float z, float yaw, float spin, int colour) {
		// Phase 26: her saber is an Inquisitor's: the ring spinning flat in flight.
		if (INQUISITOR) {
			InquisitorSaber.flying(b, x, y, z, yaw, spin, colour);
			return;
		}

		b.push().translate(x, y, z).rotateY(-yaw).rotateX(80.0F).rotateZ(spin);
		staffHilt(b);
		blade(b, STAFF_HALF, STAFF_HALF + STAFF_BLADE, colour);
		blade(b, -STAFF_HALF, -STAFF_HALF - STAFF_BLADE, colour);
		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
		b.pop();
	}

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
	static void looseHilt(MeshBuilder b, float x, float y, float z, float yaw, float pitch, boolean staff) {
		// Phase 26: her saber is an Inquisitor's: lying on its ring.
		if (INQUISITOR) {
			InquisitorSaber.lying(b, x, y, z, yaw, pitch);
			return;
		}

		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
		b.push().translate(x, y, z).rotateY(-yaw).rotateX(pitch);

		if (staff) {
			// Phase 25: struck from her hand in the second phase: the whole staff, its blades out.
			staffHilt(b);
			b.pop();
			return;
		}

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
