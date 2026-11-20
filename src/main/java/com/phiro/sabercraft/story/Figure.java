package com.phiro.sabercraft.story;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.Crew;

/**
 * Phase 19: someone in a story's ship, run by the server. Outside the story the crew is drawn by
 * every client on its own (the same everywhere: {@link Crew#where}); in a story the server moves
 * them, because they can notice the player, turn to look, go and search, give chase and come back.
 * While nothing happens they keep to their crew routine (the same place, the same patrol), so the
 * ways can be watched and learnt.
 *
 * <p>Positions are in plot space (as in {@link Crew}); the client gets them a few times a second.
 */
public final class Figure {
	// What one is doing.
	/** Their routine: standing, working, walking their patrol. */
	public static final int ROUTINE = 0;
	/** Stopped, turned towards something they noticed. */
	public static final int LOOK = 1;
	/** Walking to where they saw (or heard) something, then looking round there. */
	public static final int INVESTIGATE = 2;
	/** Running after the player. */
	public static final int CHASE = 3;
	/** Going back to their routine. */
	public static final int RETURN = 4;
	/** Held by the story where they are (a scene, a dialog). */
	public static final int HELD = 5;
	/** Beaten (in the finale): lies on the floor, does nothing more. */
	public static final int DOWN = 6;
	/** Walking along a scripted way once (a scene), then holding. */
	public static final int SCRIPTED = 7;
	/** In the finale: moved by the fight ({@link Finale}), not by seeing. */
	public static final int FIGHT = 8;
	/**
	 * Phase 20: one who is no soldier saw the player: runs to the nearest wall comm and raises the
	 * alarm there (then stays by it, out of the way, until it is calm again).
	 */
	public static final int CALL = 9;

	// How much they suspect (the client shows it).
	public static final int CALM = 0;
	public static final int SUSPICIOUS = 1;
	public static final int ALERTED = 2;
	public static final int DETECTED = 3;

	/** The number the client knows them by (stable while the story session runs). */
	public final int id;
	/** Who: {@code crew:<n>} for the ship's crew, otherwise the story's name for them ({@code kestrel}). */
	public final String key;
	/** Their routine (where they stand, their patrol); also their kind and pose. */
	public Crew crew;
	/** The ship they are in ({@code falcon}, {@code star_destroyer}). */
	public String ship = Story.DESTROYER;
	/** Whose voice and name a dialog shows ({@code sabercraft.story.name.<speaker>}); empty for anonymous crew. */
	public String speaker = "";
	/** Whether they watch for intruders (the Empire's people), or are on the player's side. */
	public boolean observer;
	/** Whether they carry a lamp (in dark areas they see far only in its cone). */
	public boolean lamp;
	/** Whether one can speak to them (right click). */
	public boolean talks = true;
	/** In the finale: whether they fight (shoot, or the duel), their health and their weapon. */
	public boolean hostile;
	public float health = 1.0F;
	public boolean saber;
	/** Their timing in the finale: ticks until the next shot or blow, and a blow being wound up. */
	public int cooldown;
	public int windup;
	/** How far they see, from {@link Stealth#range}; 0 = by their kind. */
	public double sight;

	// Where they are now.
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float headYaw;
	/** How fast they walk now (blocks a second), for the legs. */
	public float speed;

	// What they do and think.
	public int mode = ROUTINE;
	public float suspicion;
	public int stage = CALM;
	/** Where they go or look ({@link #INVESTIGATE}, {@link #LOOK}, {@link #SCRIPTED}), and how long they stay. */
	public @Nullable Vec3 target;
	public int timer;
	/** When they last saw the player (game time), and where. */
	public long lastSeen = -1000L;
	public @Nullable Vec3 seenAt;
	/** A scripted way (plot space), walked once in {@link #SCRIPTED}. */
	public java.util.List<Vec3> way = java.util.List.of();
	public int wayIndex;
	/** Phase 20: the way found round walls ({@link Paths}), to where, and when (game time). */
	public java.util.List<Vec3> path = java.util.List.of();
	public int pathIndex;
	public long pathAt = -1000L;
	public @Nullable Vec3 pathGoal;
	/** Phase 23: whether the way found reaches its goal (else they stop at its end, not at the wall). */
	public boolean pathReaches = true;
	/** Phase 20: how many more places they search after losing the player. */
	public int searches;
	/** Phase 20: ticks they found no way on (going back: then they slip into place, unseen). */
	public int stuck;
	/** Phase 20: the nearest they came to where they are going back to (to tell going on from going round in circles). */
	public double closest = Double.MAX_VALUE;
	public @Nullable Vec3 closestTo;
	/** Phase 20: whether they called the alarm in already (at a wall comm). */
	public boolean called;
	/**
	 * Phase 21: in a chase, a second shot right after the first ({@link Combat}); which way they
	 * step aside while waiting for the next shot (-1 or 1) and until when (game time).
	 */
	public int burst;
	public int strafe = 1;
	public long strafeUntil;
	/** Phase 21: how often a droid's noise drew them already (each time they believe it less). */
	public int distracted;
	/**
	 * Phase 27: when a noise last drew them (game time of the story): after a minute they believe
	 * a noise again (before, a lure used once on the way in was spent for the whole mission).
	 */
	public long distractedAt;
	/** Phase 27: how far they hear a droid's noise (blocks; some listen further). */
	public double hearing = Stealth.HEAR;
	/** Phase 27: where they stood when a noise drew them (they search past it, away from there), or null. */
	public @Nullable Vec3 luredFrom;
	/**
	 * Phase 27: a sentry (the lab's guard): seeing the player close by, he raises his rifle and
	 * fires before he rushes in to seize them.
	 */
	public boolean sentry;
	/** Phase 27: when they last fired at the player (game time of the story). */
	public long firedAt = -1000L;
	/** Phase 21: a noise they heard and will go to see once they have listened ({@link #LOOK}), or null. */
	public @Nullable Vec3 lure;
	/** Phase 21: their saber is lit (a scene: the blade is seen before the fight begins). */
	public boolean lit;
	/** Phase 21: the duel ({@link Finale}): what he does now, and a blow's kind; strokes of the player he parried in a row. */
	public int action;
	public int attack;
	public int combo;
	public int parried;
	/** Phase 21: strokes that hit him in this opening. */
	public int hits;
	/** Phase 22: when they were last hurt (game time of the story): a moment after, a stroke only glances off. */
	public long hurtAt = -1000L;
	/** Phase 21: how fast they walk a scripted way (blocks a second; 0 = walking). */
	public double scriptSpeed;

	// Phase 23: what the body does beyond walking (the client shows it), so that every hit, block
	// and call is seen and not only a bar that sinks.
	/** A light hit: a jolt, a short step back. */
	public static final int FLINCH = 1;
	/** A heavy hit: thrown back, arms out, a moment off balance. */
	public static final int HEAVY = 2;
	/** Off balance: stumbling back, the weapon low (after a heavy hit or several quick ones). */
	public static final int STUMBLE = 3;
	/** Falling (then lying {@link #DOWN}). */
	public static final int FALL = 4;
	/** A hand at the helmet or the ear: calling over the comlink. */
	public static final int COMLINK = 5;
	/** The duel: the blades met (both strokes at once): thrown apart a little. */
	public static final int CLASH = 6;
	/** The duel's end: the saber struck from the hand. */
	public static final int DISARMED = 7;
	/** The duel: a stroke of the player turned aside, the blade across. */
	public static final int PARRY = 8;
	/** The duel: a quick step aside. */
	public static final int DODGE = 9;
	/** The duel: the blades locked (between the two phases): pressing against each other. */
	public static final int LOCK = 10;
	/** Phase 25: her saber staff drawn back to be thrown, and away in flight (her hand out after it). */
	public static final int THROW_WIND = 11;
	public static final int THROWN = 12;
	/**
	 * Phase 26: her Inquisitor's saber: the ring with its two blades spinning round the grip as she
	 * rushes at the player (the first part: it spins up, the rest: the rush).
	 */
	public static final int RING = 13;

	/** Phase 23: drawn into a fight (they hunted the player when it began): a fighter until it ends. */
	public boolean engaged;
	/**
	 * Phase 23: does not look while on a scripted way (a guard leaving a prisoner locked in). Anyone
	 * else of the Empire sent somewhere still sees the player on the way, and stops for it.
	 */
	public boolean blind;
	/**
	 * Phase 24: a fighter guarding a post (the corridor out of the reactor): it holds it, rifle
	 * ready, until it sees the player, the player comes close, or it is hit; then it fights.
	 */
	public boolean post;
	/** Phase 24: her blade lock runs as a scene (its words and the second phase come with it). */
	public boolean lockScene;
	/** Phase 23: what the body does now (a kind above, 0 = nothing), ticks left of it and its whole length. */
	public int react;
	public int reactTicks;
	public int reactLength;
	/** Phase 23: being driven back by a blow (blocks a tick; it slows down). */
	public double pushX;
	public double pushZ;
	/** Phase 23: falling (blocks a tick down; 0 while on their feet): with the reactor's bridge into the depth. */
	public double drop;
	/** Phase 23: the duel: which of her combos she is in (-1: a single blow), and whether her saber was struck from her hand. */
	public int pattern = -1;
	public boolean disarmed;
	/**
	 * Phase 25: her thrown saber staff: where it left her hand, its far end, the hand it comes back
	 * to (world space), how long out and back (ticks), how long it has flown, and which ways of
	 * its flight hit already (1 out, 2 back).
	 */
	public @Nullable Vec3 throwFrom;
	public @Nullable Vec3 throwFar;
	public @Nullable Vec3 throwHand;
	public int throwOut;
	public int throwBack;
	public int throwAge;
	public int throwHit;
	/** Phase 26: her thrust carries her in fast (a lunge from a few steps away). */
	public boolean lunge;
	/** Phase 28: how often she threw her staff in this fight (at most {@code Finale.THROWS_MAX}). */
	public int thrown;
	/**
	 * Phase 28: her Inquisitor's ring keeps turning (sent to the client): it is set going by her
	 * first ring move in the second phase and stays on for a long while (in her last quarter for
	 * good); it stops only at a clear moment (her guard while circling, the scene, the end).
	 */
	public boolean ringOn;
	/**
	 * Phase 27: the boss by the story's difficulty (set each tick of the duel): how fast she
	 * attacks (shorter pauses and wind-ups above 1), how long her openings last, how hard her blows are.
	 */
	public float tempo = 1.0F;
	public float openness = 1.0F;
	public float power = 1.0F;
	/** Phase 23: quick hits in a row (several light hits throw one off balance too). */
	public int strokes;
	public long strokeAt = -1000L;

	/** Phase 23: starts a reaction of that kind for that many ticks. */
	public void react(int kind, int ticks) {
		this.react = kind;
		this.reactTicks = Math.max(1, ticks);
		this.reactLength = this.reactTicks;
	}

	/** Phase 23: how far along the reaction is (0 just begun, 1 over). */
	public float reactProgress() {
		return this.reactLength <= 0 ? 1.0F : 1.0F - (float) this.reactTicks / this.reactLength;
	}

	/** Phase 23: what the client is shown of it: the kind in the top bits, how far along (0..255) in the low byte. */
	public int reactShown() {
		if (this.react == 0) {
			return 0;
		}

		return this.react << 8 | Math.round(Mth.clamp(this.reactProgress(), 0.0F, 1.0F) * 255.0F);
	}

	public Figure(int id, String key, Crew crew) {
		this.id = id;
		this.key = key;
		this.crew = crew;
		double[] at = crew.where(0.0);
		this.x = at[0];
		this.y = at[1];
		this.z = at[2];
		this.yaw = (float) at[3];
		this.headYaw = this.yaw;
	}

	public String kind() {
		return this.crew.kind();
	}

	/**
	 * Phase 26: speaking with the player now (a dialog with them is open): standing, they talk with
	 * their hands; sitting on their seat, they stay sitting.
	 */
	public boolean talking;

	/**
	 * Phase 26: whether they stand at their routine's place (for a place with a seat or a console:
	 * within a step of it). Anyone walking a round is always at their routine.
	 */
	public boolean atPlace() {
		if (!this.crew.path().isEmpty()) {
			return true;
		}

		double[] at = this.crew.where(0L);
		double dx = this.x - at[0];
		double dz = this.z - at[2];
		return dx * dx + dz * dz < 0.36 && Math.abs(this.y - at[1]) < 0.6;
	}

	/** Their pose as drawn now: walking whenever they move off their routine. */
	public String pose() {
		if (this.mode == DOWN) {
			return "down";
		}

		if (this.mode == ROUTINE || this.mode == HELD) {
			String place = this.crew.pose();

			// Phase 26: a seat's pose (or a console's) only on the seat (at the console). Spoken to
			// on the way there (Kestrel on her way to her cot) one stood in the middle of the room
			// with the legs of someone sitting: now they stop and stand (and talk).
			if ((Crew.SIT.equals(place) || Crew.WORK.equals(place)) && !this.atPlace()) {
				return this.speed > 0.05F ? Crew.PATROL : this.talking && this.gestures() ? Crew.TALK : Crew.STAND;
			}

			if (this.talking && Crew.STAND.equals(place) && this.speed <= 0.05F && this.gestures()) {
				return Crew.TALK;
			}

			return place;
		}

		// Phase 22: someone whose place is a seat stays sitting while they are on it (looking
		// round, listening, just after a dialog): they stood upright in their chairs before.
		if (this.onSeat() && this.speed <= 0.05F) {
			return this.crew.pose();
		}

		return this.speed > 0.05F ? Crew.PATROL : Crew.STAND;
	}

	/** Phase 26: whether they talk with their hands (not a trooper with a rifle, not a droid). */
	private boolean gestures() {
		String kind = this.kind();
		return !Crew.TROOPER.equals(kind) && !Crew.GUARD.equals(kind) && !kind.startsWith("droid") && !this.saber;
	}

	public Vec3 position() {
		return new Vec3(this.x, this.y, this.z);
	}

	/** Their eyes (plot space). */
	public Vec3 eye() {
		return new Vec3(this.x, this.y + (Crew.SIT.equals(this.pose()) ? 1.15 : this.crew.height() - 0.18), this.z);
	}

	/** Which way they look, as a flat unit vector. */
	public Vec3 look() {
		float rad = this.headYaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
	}

	/** The box they fill (plot space), for clicking on them and for blows and bolts. */
	public AABB box() {
		// The same box as the client aims at (StoryInput): lower when sitting, small for droids.
		boolean sit = Crew.SIT.equals(this.pose());
		String kind = this.kind();
		double half = kind.startsWith("droid") ? 0.36 : sit ? 0.4 : 0.32;
		double top = this.mode == DOWN ? 0.5 : switch (kind) {
			case "droid_mouse" -> 0.45;
			case "droid_r2" -> 1.15;
			case Crew.WOOKIEE -> sit ? 1.6 : 2.25;
			default -> sit ? 1.45 : 1.8;
		};
		return new AABB(this.x - half, this.y, this.z - half, this.x + half, this.y + top, this.z + half);
	}

	/** Where their routine has them at that game time: x, y, z, yaw. */
	public double[] routine(long time) {
		return this.crew.where(time);
	}

	/** Sets them where their routine has them now (after a checkpoint, at the start). */
	public void snapToRoutine(long time) {
		double[] at = this.routine(time);
		this.x = at[0];
		this.y = at[1];
		this.z = at[2];
		this.yaw = (float) at[3];
		this.headYaw = this.yaw;
		this.mode = ROUTINE;
		this.speed = this.crew.path().isEmpty() ? 0.0F : (float) this.crew.speed();
		this.suspicion = 0.0F;
		this.stage = CALM;
		this.target = null;
		this.timer = 0;
		this.path = java.util.List.of();
		this.pathGoal = null;
		this.pathReaches = true;
		this.searches = 0;
		this.stuck = 0;
		this.closest = Double.MAX_VALUE;
		this.closestTo = null;
		this.called = false;
		this.windup = 0;
		this.burst = 0;
		this.lure = null;
		this.luredFrom = null;
		this.react = 0;
		this.reactTicks = 0;
		this.pushX = 0.0;
		this.pushZ = 0.0;
		this.talking = false;
	}

	/**
	 * Phase 22: whether their place is a seat (a chair, a cot: they sit there and do not walk a
	 * round).
	 */
	public boolean seated() {
		return Crew.SIT.equals(this.crew.pose()) && this.crew.path().isEmpty();
	}

	/** Phase 22: whether they are on their seat now (within a step of it). */
	public boolean onSeat() {
		if (!this.seated()) {
			return false;
		}

		double[] at = this.crew.where(0L);
		double dx = this.x - at[0];
		double dz = this.z - at[2];
		return dx * dx + dz * dz < 0.36 && Math.abs(this.y - at[1]) < 0.6;
	}

	/** Phase 22: how far a seated person turns their head (and shoulders) from the way the seat faces. */
	public static final float SEATED_TURN = 80.0F;

	/** Turns the head (and over time the body) towards a point, at most {@code step} degrees. */
	public void turnTowards(Vec3 point, float step, boolean body) {
		float wanted = Crew.yaw(this.position(), point);

		if (this.onSeat()) {
			// Phase 22: sitting, the chair keeps facing its way; only the head turns, so far.
			float seat = (float) this.crew.where(0L)[3];
			float target = seat + Mth.clamp(Mth.wrapDegrees(wanted - seat), -SEATED_TURN, SEATED_TURN);
			this.yaw = seat;
			this.headYaw = this.headYaw + Mth.clamp(Mth.wrapDegrees(target - this.headYaw), -step, step);
			return;
		}
		this.headYaw = this.headYaw + Mth.clamp(Mth.wrapDegrees(wanted - this.headYaw), -step, step);

		if (body) {
			this.yaw = this.yaw + Mth.clamp(Mth.wrapDegrees(this.headYaw - this.yaw), -step * 0.6F, step * 0.6F);
		} else {
			// The head turns at most so far from the body; then the body follows.
			float off = Mth.wrapDegrees(this.headYaw - this.yaw);

			if (Math.abs(off) > 70.0F) {
				this.yaw = this.headYaw - Math.signum(off) * 70.0F;
			}
		}
	}
}
