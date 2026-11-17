package com.phiro.sabercraft.story;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.interior.Crew;

/**
 * Phase 19: how the people in a story's ship see the player, and what they do about it.
 *
 * <p>Each one fills their own suspicion (0 to 1) while they see the player: in front of them
 * (about 110°, a little more close by), within their sight (troopers far, technicians less), with
 * nothing in between (walls, consoles, crates and closed doors are cover). Close by it fills fast,
 * far off slowly; sneaking halves how far one is seen, sprinting is noticed. In dark areas only a
 * lamp's cone shows the player from afar. Working, sitting, talking people pay less attention;
 * after an alarm everyone looks harder. The armour of a trooper hides the player from the troops
 * as long as they move like one, but not from officers close by (the security bureau looks
 * closest), not where nobody may go.
 *
 * <p>Four stages: calm; suspicious (stops, turns to look); alerted (goes to look where they saw
 * something); detected (raises the alarm and gives chase). Chasers run straight at the player and
 * stop at walls; one who gets within reach catches the player (back to the checkpoint). Out of
 * everyone's sight for ten seconds, the chase becomes a search, and after half a minute it is
 * calm again.
 */
public final class Stealth {
	/** How often each figure looks (ticks); figures take turns. */
	static final int LOOK_EVERY = 4;
	/** Field of view: full within this angle either side, a little to the side close by. */
	static final float FOV = 55.0F;
	static final float SIDE = 85.0F;
	static final double SIDE_RANGE = 5.0;
	/** Seen at once this close in front of someone (not in disguise). */
	static final double AT_ONCE = 2.0;
	/** Seen far in the dark only within a lamp's cone. */
	static final float LAMP_CONE = 24.0F;
	static final double LAMP_RANGE = 13.0;
	static final double DARK_RANGE = 3.5;
	/** Suspicion stages. */
	static final float SUSPICIOUS = 0.25F;
	static final float ALERTED = 0.6F;
	/** Walking and running speeds (blocks a second). */
	static final double WALK = 1.7;
	static final double RUN = 4.6;
	/** How near a chaser catches. */
	static final double CATCH = 1.3;
	/** Out of sight this long, a chase becomes a search; the search lasts this long. */
	static final int LOSE = 200;
	static final int SEARCH = 600;

	private Stealth() {
	}

	/** How far that kind of person sees (blocks). */
	public static double range(Figure figure) {
		if (figure.sight > 0.0) {
			return figure.sight;
		}

		return switch (figure.kind()) {
			case Crew.TROOPER -> 14.0;
			case Crew.GUARD -> 12.0;
			case Crew.OFFICER, Crew.OFFICER_BLACK, Crew.OFFICER_OLIVE -> 11.0;
			case Crew.ADMIRAL -> 10.0;
			default -> 9.0;
		};
	}

	/** Runs the people of the ship the player is in, for one tick. */
	static void tick(StorySession s, ServerPlayer player, ServerLevel level) {
		String ship = s.here;
		long time = s.time();
		s.suspicion = 0.0F;
		s.watcher = null;

		if (ship == null) {
			return;
		}

		BlockPos origin = s.origin(ship);
		Vec3 corner = Vec3.atLowerCornerOf(origin);
		Vec3 me = player.position().subtract(corner);
		boolean watched = !s.fighting && s.grace <= 0 && !player.isSpectator() && !player.isCreative() && s.dialog == null;
		boolean anyChaserSees = false;

		if (s.grace > 0) {
			s.grace--;
		}

		for (Figure figure : s.figures) {
			if (!ship.equals(figure.ship)) {
				continue;
			}

			// What they see.
			if (figure.observer && figure.mode != Figure.DOWN && figure.mode != Figure.HELD && figure.mode != Figure.SCRIPTED
					&& (time + figure.id) % LOOK_EVERY == 0) {
				float rate = watched ? sees(s, figure, player, me, level, corner, ship) : 0.0F;

				if (rate > 0.0F) {
					figure.lastSeen = time;
					figure.seenAt = me;
					figure.suspicion = Math.min(1.0F, figure.suspicion + rate * LOOK_EVERY / 20.0F);

					if (figure.mode == Figure.CHASE) {
						anyChaserSees = true;
					}
				} else if (time - figure.lastSeen > 40) {
					// Slowly forgetting (not while chasing: that ends by losing the player).
					if (figure.mode != Figure.CHASE) {
						figure.suspicion = Math.max(0.0F, figure.suspicion - (s.alarmed() ? 0.06F : 0.1F) * LOOK_EVERY / 20.0F);
					}
				}

				react(s, figure, time);
			}

			move(s, figure, me, level, corner, time);

			if (figure.observer && figure.mode != Figure.DOWN && figure.suspicion > s.suspicion) {
				s.suspicion = figure.suspicion;
				s.watcher = figure;
			}

			if (figure.mode == Figure.CHASE && watched && caught(figure, me)) {
				Story.capture(s, figure);
				return;
			}
		}

		// The chase: kept up while anyone chasing sees the player; out of sight long enough, a search.
		if (s.alarm > 0) {
			if (anyChaserSees) {
				s.unseen = 0;
				s.alarm = Math.max(s.alarm, SEARCH + LOSE);
			} else if (++s.unseen == LOSE) {
				for (Figure figure : s.figures) {
					if (figure.mode == Figure.CHASE) {
						figure.mode = Figure.INVESTIGATE;
						figure.target = figure.seenAt;
						figure.timer = 160;
					}
				}

				s.alarm = Math.min(s.alarm, SEARCH);
				s.hint("sabercraft.story.hint.searching");
			}

			if (--s.alarm == 0) {
				s.unseen = 0;
				s.dirty = true;

				for (Figure figure : s.figures) {
					if (figure.mode == Figure.CHASE || figure.mode == Figure.INVESTIGATE) {
						figure.mode = Figure.RETURN;
					}

					figure.suspicion = Math.min(figure.suspicion, 0.2F);
				}

				s.hint("sabercraft.story.hint.calm");
			}
		}
	}

	/** How fast that figure's suspicion fills now (a second), 0 when they do not see the player. */
	static float sees(StorySession s, Figure figure, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, String ship) {
		Vec3 eye = figure.eye();
		Vec3 chest = me.add(0.0, player.getPose() == Pose.SWIMMING ? 0.3 : 0.9, 0.0);
		Vec3 to = chest.subtract(eye);
		double distance = to.length();
		double range = range(figure);
		float factor = 1.0F;

		// Busy people notice less; after an alarm, everyone more.
		String pose = figure.pose();

		if (figure.mode == Figure.ROUTINE) {
			factor *= Crew.WORK.equals(pose) ? 0.7F : Crew.SIT.equals(pose) ? 0.6F : Crew.TALK.equals(pose) ? 0.75F : 1.0F;
		} else {
			factor *= 1.2F;
		}

		if (s.alarmed()) {
			range *= 1.35;
		}

		// How the player moves.
		boolean crawling = player.getPose() == Pose.SWIMMING;
		boolean sneaking = player.isShiftKeyDown() || player.isCrouching();

		if (crawling) {
			range *= 0.4;
		} else if (sneaking) {
			range *= 0.5;
		} else if (player.isSprinting()) {
			range *= 1.25;
		}

		// The dark: far only in a lamp's light.
		boolean dark = s.chapter.dark(s, ship, me.x, me.y, me.z);

		if (dark && !lit(s, ship, me, level, corner)) {
			range = Math.min(range, DARK_RANGE);
		}

		if (distance > range) {
			return 0.0F;
		}

		// In front of them?
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		float angle = flat.lengthSqr() < 1.0E-4 ? 0.0F : (float) Math.toDegrees(Math.acos(Mth.clamp(flat.normalize().dot(figure.look()), -1.0, 1.0)));

		if (angle > SIDE || angle > FOV && distance > SIDE_RANGE) {
			return 0.0F;
		}

		// Nothing in between (eyes or chest).
		if (!clear(level, corner.add(eye), corner.add(me.add(0.0, player.getEyeHeight(), 0.0))) && !clear(level, corner.add(eye), corner.add(chest))) {
			return 0.0F;
		}

		// The armour: hides from the troops while one moves like them, less from officers.
		boolean conspicuous = sneaking || crawling || player.isSprinting() || !player.onGround();

		if (s.disguised && !s.chapter.restricted(s, ship, me.x, me.y, me.z)) {
			float hidden = disguise(figure, distance, conspicuous);

			if (s.burned) {
				hidden = Math.max(hidden, 0.8F);
			}

			factor *= hidden;

			if (factor <= 0.0F) {
				return 0.0F;
			}
		} else if (distance < AT_ONCE && angle <= FOV) {
			return 20.0F;
		}

		if (angle > FOV) {
			factor *= 0.3F;
		}

		double near = 1.0 - distance / range;
		return (float) ((0.2 + 1.6 * Math.pow(near, 1.5)) * factor);
	}

	/** How much the armour leaves of being seen by that kind of person (0 = not at all). */
	static float disguise(Figure figure, double distance, boolean conspicuous) {
		return switch (figure.kind()) {
			// The troops: not at all, unless one does what troopers do not.
			case Crew.TROOPER, Crew.GUARD, Crew.TECH, Crew.PILOT -> conspicuous ? 0.6F : 0.0F;
			// The security bureau looks closely.
			case Crew.OFFICER_BLACK -> distance < 4.0 ? 1.0F : conspicuous ? 0.7F : 0.2F;
			// Other officers: close by, they look twice.
			default -> distance < 4.0 ? 0.5F : conspicuous ? 0.5F : 0.1F;
		};
	}

	/** Whether a lamp's cone falls on the player (with nothing in between). */
	static boolean lit(StorySession s, String ship, Vec3 me, ServerLevel level, Vec3 corner) {
		for (Figure figure : s.figures) {
			if (!figure.lamp || !ship.equals(figure.ship) || figure.mode == Figure.DOWN) {
				continue;
			}

			Vec3 eye = figure.eye();
			Vec3 to = me.add(0.0, 0.9, 0.0).subtract(eye);

			if (to.length() > LAMP_RANGE) {
				continue;
			}

			Vec3 flat = new Vec3(to.x, 0.0, to.z);

			if (flat.lengthSqr() < 1.0E-4) {
				return true;
			}

			float angle = (float) Math.toDegrees(Math.acos(Mth.clamp(flat.normalize().dot(figure.look()), -1.0, 1.0)));

			if (angle <= LAMP_CONE && clear(level, corner.add(eye), corner.add(me.add(0.0, 0.9, 0.0)))) {
				return true;
			}
		}

		return false;
	}

	/** Whether nothing that collides is between two points (world space). */
	static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 0.04;
	}

	/** What a figure does about their suspicion. */
	static void react(StorySession s, Figure figure, long time) {
		float sus = figure.suspicion;
		int before = figure.stage;
		figure.stage = sus >= 1.0F ? Figure.DETECTED : sus >= ALERTED ? Figure.ALERTED : sus >= SUSPICIOUS ? Figure.SUSPICIOUS : Figure.CALM;

		if (figure.stage != before) {
			s.dirty = true;
		}

		if (figure.mode == Figure.CHASE || figure.mode == Figure.HELD || figure.mode == Figure.SCRIPTED || figure.mode == Figure.DOWN) {
			return;
		}

		if (figure.stage == Figure.DETECTED) {
			if (!s.chapter.detected(s, figure)) {
				Story.detected(s, figure);
			}
		} else if (figure.stage == Figure.ALERTED && figure.seenAt != null && time - figure.lastSeen < 10) {
			figure.mode = Figure.INVESTIGATE;
			figure.target = figure.seenAt;
			figure.timer = 100;
		} else if (figure.stage == Figure.SUSPICIOUS && figure.mode == Figure.ROUTINE && figure.seenAt != null) {
			figure.mode = Figure.LOOK;
			figure.target = figure.seenAt;
			figure.timer = 60;
			Story.cue(s, Story.CUE_SOUND, "", "story.suspicious", figure.id);
		} else if (figure.stage == Figure.CALM && (figure.mode == Figure.LOOK || figure.mode == Figure.INVESTIGATE && figure.timer <= 0)) {
			figure.mode = Figure.RETURN;
		}
	}

	/** Moves a figure a tick along whatever they do. */
	static void move(StorySession s, Figure figure, Vec3 me, ServerLevel level, Vec3 corner, long time) {
		switch (figure.mode) {
			case Figure.ROUTINE -> {
				double[] at = figure.routine(time);
				figure.x = at[0];
				figure.y = at[1];
				figure.z = at[2];
				figure.yaw = (float) at[3];
				figure.headYaw = figure.yaw;
				figure.speed = figure.crew.path().isEmpty() ? 0.0F : (float) figure.crew.speed();
			}
			case Figure.LOOK -> {
				figure.speed = 0.0F;

				if (figure.target != null) {
					figure.turnTowards(figure.target, 9.0F, false);
				}
			}
			case Figure.INVESTIGATE -> {
				if (figure.target == null || figure.position().distanceTo(figure.target) < 1.2 || !step(figure, figure.target, WALK, level, corner)) {
					// There (or stuck): look round a while.
					figure.speed = 0.0F;
					figure.headYaw = figure.yaw + Mth.sin(time * 0.08F + figure.id) * 60.0F;

					if (--figure.timer <= 0 && figure.suspicion < ALERTED) {
						figure.mode = Figure.RETURN;
					}
				}
			}
			case Figure.CHASE -> {
				figure.target = me;

				if (!step(figure, me, RUN, level, corner)) {
					figure.speed = 0.0F;
					figure.turnTowards(me, 20.0F, true);
				}
			}
			case Figure.RETURN -> {
				double[] at = figure.routine(time);
				Vec3 home = new Vec3(at[0], at[1], at[2]);

				if (figure.position().distanceTo(home) < 0.35 || !step(figure, home, WALK, level, corner)) {
					// Home (or no way back: they slip back into place).
					figure.mode = Figure.ROUTINE;
				}
			}
			case Figure.SCRIPTED -> {
				if (figure.wayIndex >= figure.way.size()) {
					figure.speed = 0.0F;
					figure.mode = Figure.HELD;
				} else {
					Vec3 next = figure.way.get(figure.wayIndex);

					if (figure.position().distanceTo(next) < 0.25) {
						figure.wayIndex++;
					} else if (!step(figure, next, WALK, level, corner)) {
						// Scripted ways go where blocks allow; if not, they are put there.
						figure.x = next.x;
						figure.y = next.y;
						figure.z = next.z;
						figure.wayIndex++;
					}
				}
			}
			case Figure.HELD -> {
				figure.speed = 0.0F;

				if (figure.target != null) {
					figure.turnTowards(figure.target, 12.0F, true);
				}
			}
			default -> figure.speed = 0.0F;
		}
	}

	/**
	 * One tick's step towards a point at that speed, round nothing (straight on; sliding along a
	 * wall; up a step). Returns whether they moved.
	 */
	static boolean step(Figure figure, Vec3 goal, double speed, ServerLevel level, Vec3 corner) {
		Vec3 to = new Vec3(goal.x - figure.x, 0.0, goal.z - figure.z);
		double length = to.length();

		if (length < 1.0E-3) {
			figure.speed = 0.0F;
			return false;
		}

		double reach = Math.min(length, speed / 20.0);
		Vec3 dir = to.scale(1.0 / length);
		double[][] tries = {{dir.x * reach, dir.z * reach}, {Math.signum(dir.x) * reach, 0.0}, {0.0, Math.signum(dir.z) * reach}};

		for (double[] t : tries) {
			if (Math.abs(t[0]) < 1.0E-4 && Math.abs(t[1]) < 1.0E-4) {
				continue;
			}

			for (double up : new double[] {0.0, 0.55}) {
				double nx = figure.x + t[0];
				double nz = figure.z + t[1];
				double ny = figure.y + up;

				if (free(level, corner, nx, ny, nz, figure.crew.height())) {
					// Down onto the floor (a step, a ledge: at most a block).
					for (int i = 0; i < 10 && free(level, corner, nx, ny - 0.1, nz, figure.crew.height()) && ny > figure.y - 1.0; i++) {
						ny -= 0.1;
					}

					figure.x = nx;
					figure.y = ny;
					figure.z = nz;
					float yaw = Crew.yaw(Vec3.ZERO, new Vec3(t[0], 0.0, t[1]));
					figure.yaw = Crew.lerpYaw(figure.yaw, yaw, 0.35F);
					figure.headYaw = figure.mode == Figure.CHASE ? Crew.yaw(figure.position(), goal) : figure.yaw;
					figure.speed = (float) speed;
					return true;
				}
			}
		}

		figure.speed = 0.0F;
		return false;
	}

	private static boolean free(ServerLevel level, Vec3 corner, double x, double y, double z, double height) {
		AABB box = new AABB(x - 0.28, y + 0.01, z - 0.28, x + 0.28, y + Math.min(height, 1.8), z + 0.28).move(corner);
		return level.noBlockCollision(null, box);
	}

	/** Whether a chaser is close enough to catch the player. */
	static boolean caught(Figure figure, Vec3 me) {
		double dx = me.x - figure.x;
		double dz = me.z - figure.z;
		return dx * dx + dz * dz < CATCH * CATCH && Math.abs(me.y - figure.y) < 1.5;
	}

	/** Those who could join a chase: observers near the one who saw, or near the player. */
	static void rally(StorySession s, Figure first, Vec3 me) {
		int joined = 0;

		for (Figure figure : s.figures) {
			if (figure == first || !figure.observer || !first.ship.equals(figure.ship) || figure.mode == Figure.DOWN || figure.mode == Figure.HELD
					|| figure.mode == Figure.SCRIPTED || figure.mode == Figure.CHASE) {
				continue;
			}

			if (figure.position().distanceTo(first.position()) < 12.0 || figure.position().distanceTo(me) < 10.0) {
				if (Crew.TROOPER.equals(figure.kind()) && joined < 3) {
					figure.mode = Figure.CHASE;
					figure.suspicion = 1.0F;
					figure.stage = Figure.DETECTED;
					joined++;
				} else {
					figure.mode = Figure.LOOK;
					figure.target = me;
					figure.timer = 80;
					figure.suspicion = Math.max(figure.suspicion, ALERTED);
				}
			}
		}
	}

	/** Sends those near a point (plot space) to look there (a droid's noise). Returns how many went. */
	static int distract(StorySession s, String ship, Vec3 at, double radius) {
		int count = 0;

		for (Figure figure : s.figures) {
			if (!figure.observer || !ship.equals(figure.ship) || figure.mode == Figure.CHASE || figure.mode == Figure.DOWN || figure.mode == Figure.HELD
					|| figure.mode == Figure.SCRIPTED || figure.position().distanceTo(at) > radius) {
				continue;
			}

			figure.mode = Figure.INVESTIGATE;
			figure.target = at;
			figure.timer = 140;
			figure.suspicion = Math.max(figure.suspicion, SUSPICIOUS);
			count++;
		}

		s.dirty = true;
		return count;
	}

	/** The figure nearest a point within reach, of those matching. */
	static @Nullable Figure nearest(StorySession s, String ship, Vec3 at, double reach) {
		Figure best = null;
		double bestD = reach;

		for (Figure figure : s.figures) {
			double d = figure.position().distanceTo(at);

			if (ship.equals(figure.ship) && d < bestD && figure.mode != Figure.DOWN) {
				best = figure;
				bestD = d;
			}
		}

		return best;
	}
}
