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
	/** Phase 20: how many more places they search after losing the player. */
	public int searches;
	/** Phase 20: ticks they found no way on (going back: then they slip into place, unseen). */
	public int stuck;
	/** Phase 20: the nearest they came to where they are going back to (to tell going on from going round in circles). */
	public double closest = Double.MAX_VALUE;
	public @Nullable Vec3 closestTo;
	/** Phase 20: whether they called the alarm in already (at a wall comm). */
	public boolean called;

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

	/** Their pose as drawn now: walking whenever they move off their routine. */
	public String pose() {
		if (this.mode == DOWN) {
			return "down";
		}

		if (this.mode == ROUTINE || this.mode == HELD) {
			return this.crew.pose();
		}

		return this.speed > 0.05F ? Crew.PATROL : Crew.STAND;
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
		this.searches = 0;
		this.stuck = 0;
		this.closest = Double.MAX_VALUE;
		this.closestTo = null;
		this.called = false;
	}

	/** Turns the head (and over time the body) towards a point, at most {@code step} degrees. */
	public void turnTowards(Vec3 point, float step, boolean body) {
		float wanted = Crew.yaw(this.position(), point);
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
