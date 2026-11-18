package com.phiro.sabercraft.story;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 21: how the troopers fight in a chase, before the saber is lit. Phase 20's chasers ran
 * straight at the player and never used their rifles; now they act like soldiers:
 *
 * <ul>
 * <li>They shoot from a distance, with the stun setting (short blue bolts): they want the spy
 * alive. A shot is aimed first (the rifle comes up to the shoulder, a moment one can see and
 * use), then fired; bolts can miss, more so from afar and at someone running. Three hits and
 * the player is caught (back to the checkpoint, as before).</li>
 * <li>Each has a way of fighting (by their number, so a squad is mixed): one holds back and
 * covers, one presses forward and fires twice, one works round to the side. Between shots they
 * step aside, back off when the player comes too close, and only run in to seize the player
 * when within a few steps.</li>
 * <li>Out of sight they go where the player was last seen by any of them (they tell each other
 * over the comlink; a moment after losing sight they still know which way the player went), not
 * to where the player really is.</li>
 * <li>They never shoot through one of their own and never step into each other
 * ({@link Stealth#step} keeps them apart).</li>
 * </ul>
 *
 * <p>Guards (no rifles) still run at the player. The duel and the fight after the saber are
 * {@link Finale}'s.
 */
final class Combat {
	/** How far they shoot (blocks) and how close they run in to seize the player. */
	static final double SHOOT_RANGE = 16.0;
	static final double RUSH = 3.4;
	/** Aiming before a shot (ticks): as a rule, after a second alarm in a mission, and the second shot of two. */
	static final int AIM = 14;
	static final int AIM_ALERT = 10;
	static final int AIM_BURST = 4;
	/** Ticks between shots: at least, and a random part. */
	static final int COOLDOWN = 30;
	static final int COOLDOWN_RANDOM = 26;
	/** What a stun bolt takes (the player has 20), and how many hits catch the player. */
	static final float STUN_DAMAGE = 1.0F;
	static final int STUN_HITS = 3;
	/** After the last sighting, this long (ticks) the squad still knows where the player is going. */
	static final int TRACK = 30;
	/** How long one steps to one side between shots (ticks): at least, and a random part. */
	static final int STRAFE = 24;
	static final int STRAFE_RANDOM = 30;

	private Combat() {
	}

	/** How a soldier fights (stays the same for them): 0 holds back and covers, 1 presses forward, 2 works round the side. */
	static int role(Figure figure) {
		return Math.floorMod(figure.id * 7 + 3, 3);
	}

	/** How far from the player they like to stay. */
	static double keep(Figure figure) {
		if (pistol(figure)) {
			// Phase 23: an officer keeps back, as one who gives orders does.
			return 7.5;
		}

		return switch (role(figure)) {
			case 0 -> 9.5;
			case 1 -> 5.5;
			default -> 7.5;
		};
	}

	/** Whether that one fights with a rifle (troopers), or runs at the player (guards). */
	static boolean rifle(Figure figure) {
		return Crew.TROOPER.equals(figure.kind());
	}

	/**
	 * Phase 23: whether that one carries a blaster pistol (officers): they defend themselves (they
	 * keep their distance, shoot and move to a better place), and call for help over the comlink
	 * first.
	 */
	static boolean pistol(Figure figure) {
		String kind = figure.kind();
		return Crew.OFFICER.equals(kind) || Crew.OFFICER_OLIVE.equals(kind) || Crew.OFFICER_BLACK.equals(kind);
	}

	/** Phase 23: whether that one shoots (a rifle or a pistol). */
	static boolean armed(Figure figure) {
		return rifle(figure) || pistol(figure);
	}

	/** Phase 23: whether that one fights when a fight begins (troopers, guards, officers), or keeps out of it. */
	static boolean combatant(Figure figure) {
		return armed(figure) || Crew.GUARD.equals(figure.kind());
	}

	/** Phase 23: an officer's aim (a pistol comes up quicker than a rifle) and how long the call over the comlink takes. */
	static final int AIM_PISTOL = 12;
	static final int COMLINK = 36;
	/** Phase 23: how many fight at once at most (those drawn in by seeing the fight; the hunters always). */
	static final int MAX_FIGHTERS = 6;

	/**
	 * Phase 23: a fight began (the saber taken, a section of the finale, a film): everyone who
	 * was hunting the player (gave chase, raised the alarm, went to where the player was seen, came
	 * as help, patrols after an alarm) fights now, at once and all the same way, and so does any
	 * soldier who sees the fight from close by (as many as {@link #MAX_FIGHTERS}). Nobody stays
	 * behind in an old chase. Run every tick of a fight (the near ones only now and then). Returns
	 * how many joined.
	 */
	static int engage(StorySession s, String ship, Vec3 me, ServerLevel level, Vec3 corner, boolean look) {
		int fighters = 0;

		for (Figure figure : s.figures) {
			if (figure.hostile && figure.mode != Figure.DOWN && ship.equals(figure.ship)) {
				fighters++;
			}
		}

		int joined = 0;

		for (Figure figure : s.figures) {
			if (figure.hostile || !figure.observer || !ship.equals(figure.ship) || !combatant(figure) || figure.mode == Figure.DOWN || figure.mode == Figure.HELD
					|| figure.mode == Figure.SCRIPTED) {
				continue;
			}

			boolean hunting = figure.mode == Figure.CHASE || figure.mode == Figure.INVESTIGATE || figure.mode == Figure.CALL || figure.stage >= Figure.ALERTED
					|| figure.key.startsWith(Stealth.REINFORCEMENT) || figure.key.startsWith("patrol.");
			boolean near = false;

			if (!hunting && look && fighters < MAX_FIGHTERS) {
				double distance = horizontal(figure.position(), me);
				near = distance < 16.0 && sees(figure, me, level, corner, distance);
			}

			if (hunting || near) {
				join(figure, joined++);
				fighters++;
			}
		}

		if (joined > 0) {
			s.dirty = true;
		}

		return joined;
	}

	/** Phase 23: one joins the fight: a fighter from now on (shooting, hit by the saber like the others). */
	static void join(Figure figure, int index) {
		figure.engaged = true;
		figure.hostile = true;
		figure.mode = Figure.FIGHT;
		figure.health = figure.health > 0.0F ? figure.health : 1.0F;
		figure.suspicion = 1.0F;
		figure.stage = Figure.DETECTED;
		figure.cooldown = 20 + index * 13;
		figure.windup = 0;
		figure.burst = 0;
		figure.target = null;
		figure.lure = null;
		figure.path = java.util.List.of();
		figure.pathGoal = null;
		figure.pathReaches = true;
		figure.called = true;

		if (figure.react == Figure.COMLINK) {
			figure.react = 0;
			figure.reactTicks = 0;
		}
	}

	/** One tick of a soldier chasing the player. */
	static void chase(StorySession s, Figure figure, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, long time) {
		figure.target = me;

		if (s.cornerer == figure) {
			figure.speed = 0.0F;
			figure.windup = 0;
			return;
		}

		double distance = horizontal(figure.position(), me);

		// Phase 23: an officer calls for help first (a hand at the ear), stepping back from the
		// player meanwhile; then he fights like the troops (Story.officerCalled brings them).
		if (figure.react == Figure.COMLINK) {
			Hits.slide(figure, level, corner);
			figure.windup = 0;

			if (--figure.reactTicks <= 0) {
				figure.react = 0;
				figure.reactTicks = 0;
				figure.called = true;
				figure.cooldown = 6;
				Story.officerCalled(s, figure);
			} else if (distance < 6.5) {
				Vec3 away = figure.position().add(figure.position().subtract(me).multiply(1.0, 0.0, 1.0).normalize().scale(1.5));

				if (!Stealth.step(figure, away, Stealth.WALK * 1.2, level, corner)) {
					strafe(figure, me, level, corner, time, player);
				}
			} else {
				figure.speed = 0.0F;
			}

			figure.headYaw = Crew.yaw(figure.position(), me);
			figure.yaw = Crew.lerpYaw(figure.yaw, figure.headYaw, 0.3F);
			return;
		}

		// Phase 23: thrown back by something: nothing else until it is over.
		if (Hits.tick(figure, level, corner)) {
			figure.windup = 0;
			return;
		}
		// After a capture (and in the tests) the player is out of everyone's sight for a while.
		boolean sees = s.grace <= 0 && sees(figure, me, level, corner, distance);

		if (sees) {
			figure.lastSeen = time;
			figure.seenAt = me;
			s.huntSeen = time;
			s.huntAt = me;
		}

		if (figure.cooldown > 0) {
			figure.cooldown--;
		}

		// A shot being aimed: they stand, aim and fire (or lower the rifle when the player is gone).
		if (figure.windup > 0) {
			figure.speed = 0.0F;
			figure.turnTowards(me, 25.0F, true);
			figure.headYaw = Crew.yaw(figure.position(), me);

			if (--figure.windup == 0) {
				boolean fired = sees && lineFree(s, figure, me) && s.grace <= 0 && s.cornered <= 0;

				if (fired) {
					fire(s, figure, player, me, level, corner, distance);
				}

				if (fired && role(figure) == 1 && figure.burst == 0 && !pistol(figure)) {
					figure.burst = 1;
					figure.cooldown = 0;
				} else {
					figure.burst = 0;
					figure.cooldown = COOLDOWN + player.getRandom().nextInt(COOLDOWN_RANDOM) - (role(figure) == 0 ? 6 : 0);
				}

				s.dirty = true;
			}

			return;
		}

		if (!sees) {
			// Where the squad last saw the player (just lost: still the way they went).
			Vec3 goal = time - s.huntSeen < TRACK ? me : s.huntAt != null ? s.huntAt : figure.seenAt;
			figure.burst = 0;

			if (goal == null || !Stealth.go(figure, apart(s, figure, goal, level, corner), Stealth.RUN, level, corner, time)) {
				figure.speed = 0.0F;
				figure.headYaw = figure.yaw + Mth.sin(time * 0.1F + figure.id) * 50.0F;
			}

			return;
		}

		// Phase 23: an officer does not run in: too close, he steps back (and shoots from there).
		if (pistol(figure) && distance < 3.5 && figure.cooldown > 0) {
			Vec3 away = figure.position().add(figure.position().subtract(me).multiply(1.0, 0.0, 1.0).normalize().scale(1.5));

			if (!Stealth.step(figure, away, Stealth.WALK * 1.3, level, corner)) {
				strafe(figure, me, level, corner, time, player);
			}

			figure.headYaw = Crew.yaw(figure.position(), me);
			return;
		}

		// Close enough to seize the player: they run in ("Hands up!" when they reach them).
		if (distance < RUSH && !pistol(figure)) {
			Stealth.go(figure, me, Stealth.RUN, level, corner, time);
			figure.headYaw = Crew.yaw(figure.position(), me);
			return;
		}

		// Ready, in sight, nobody of theirs in the way: up with the rifle.
		if (figure.cooldown <= 0 && distance <= SHOOT_RANGE && s.grace <= 0 && lineFree(s, figure, me)) {
			figure.windup = figure.burst > 0 ? AIM_BURST : pistol(figure) ? AIM_PISTOL : Stealth.level(s) >= 2 ? AIM_ALERT : AIM;
			figure.speed = 0.0F;
			s.dirty = true;
			return;
		}

		// Otherwise to where they like to fight from: closer, further back, or aside.
		double keep = keep(figure);

		if (distance > keep + 1.5) {
			Vec3 goal = role(figure) == 2 ? flank(level, corner, figure, me, keep) : me;
			Stealth.go(figure, apart(s, figure, goal, level, corner), distance > keep + 6.0 ? Stealth.RUN : Stealth.WALK * 1.5, level, corner, time);
		} else if (distance < keep - 2.5 && role(figure) != 1) {
			Vec3 away = figure.position().add(figure.position().subtract(me).multiply(1.0, 0.0, 1.0).normalize().scale(1.5));

			if (!Stealth.step(figure, away, Stealth.WALK, level, corner)) {
				strafe(figure, me, level, corner, time, player);
			}
		} else {
			strafe(figure, me, level, corner, time, player);
		}

		figure.headYaw = Crew.yaw(figure.position(), me);
		figure.yaw = Crew.lerpYaw(figure.yaw, figure.headYaw, 0.25F);
	}

	/** Whether a soldier sees the player now: in reach, about level, nothing in between (in a chase they look everywhere). */
	static boolean sees(Figure figure, Vec3 me, ServerLevel level, Vec3 corner, double distance) {
		if (distance > SHOOT_RANGE + 4.0 || Math.abs(me.y - figure.y) > 4.0) {
			return false;
		}

		Vec3 eye = corner.add(figure.eye());
		return Stealth.clear(level, eye, corner.add(me.add(0.0, 1.0, 0.0))) || Stealth.clear(level, eye, corner.add(me.add(0.0, 1.5, 0.0)));
	}

	/** A step to the side (between shots), turning round at walls and after a while. */
	private static void strafe(Figure figure, Vec3 me, ServerLevel level, Vec3 corner, long time, ServerPlayer player) {
		if (time >= figure.strafeUntil) {
			figure.strafe = player.getRandom().nextBoolean() ? 1 : -1;
			figure.strafeUntil = time + STRAFE + player.getRandom().nextInt(STRAFE_RANDOM);
		}

		Vec3 to = me.subtract(figure.position()).multiply(1.0, 0.0, 1.0);

		if (to.lengthSqr() < 1.0E-4) {
			figure.speed = 0.0F;
			return;
		}

		Vec3 side = new Vec3(-to.z, 0.0, to.x).normalize().scale(figure.strafe);

		if (!Stealth.step(figure, figure.position().add(side), Stealth.WALK * 0.8, level, corner)) {
			figure.strafe = -figure.strafe;
			figure.speed = 0.0F;
		}
	}

	/** A place to the side of the player (about {@code keep} away) to shoot from, or the player's own place if there is none. */
	private static Vec3 flank(ServerLevel level, Vec3 corner, Figure figure, Vec3 me, double keep) {
		Vec3 from = figure.position().subtract(me).multiply(1.0, 0.0, 1.0);

		if (from.lengthSqr() < 1.0E-4) {
			return me;
		}

		double side = (figure.id & 1) == 0 ? 1.0 : -1.0;
		double angle = Math.atan2(from.z, from.x) + side * Math.toRadians(55.0);
		double x = me.x + Math.cos(angle) * keep;
		double z = me.z + Math.sin(angle) * keep;
		Double y = Paths.stand(level, corner, x, me.y, z, figure.crew.height());

		if (y == null || !Stealth.clear(level, corner.add(x, y + 1.5, z), corner.add(me.add(0.0, 1.5, 0.0)))) {
			return me;
		}

		return new Vec3(x, y, z);
	}

	/**
	 * Phase 21: where one goes when several go to the same place: a little apart from the others
	 * going there (each their own side), so they do not end up in one spot.
	 */
	static Vec3 apart(StorySession s, Figure figure, Vec3 goal) {
		return apart(s, figure, goal, null, null);
	}

	/**
	 * Phase 23: ... and, knowing the ship, only a place one can stand on with a clear line to the
	 * goal (not behind a console or in a wall, where the chasers walked into each other's corner):
	 * else the next place round, else the goal itself. The places are shared out by the order of
	 * the people (not by who asks first), so each keeps theirs from tick to tick.
	 */
	static Vec3 apart(StorySession s, Figure figure, Vec3 goal, @org.jspecify.annotations.Nullable ServerLevel level,
			@org.jspecify.annotations.Nullable Vec3 corner) {
		int index = 0;
		int count = 0;
		Vec3 aim = figure.target != null ? figure.target : goal;

		for (Figure other : s.figures) {
			if (other.mode == figure.mode && other.ship.equals(figure.ship) && other.mode != Figure.DOWN && other.target != null
					&& other.target.distanceTo(aim) < 3.0) {
				if (other == figure) {
					index = count;
				}

				count++;
			}
		}

		if (count <= 1) {
			return goal;
		}

		double radius = count > 4 ? 2.2 : 1.7;

		for (int tries = 0; tries < count + 2; tries++) {
			double angle = (index + tries) * (Math.PI * 2.0 / count) + 0.4;
			Vec3 spot = goal.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);

			if (level == null || corner == null) {
				return spot;
			}

			Double y = Paths.stand(level, corner, spot.x, goal.y, spot.z, figure.crew.height());

			if (y != null && Math.abs(y - goal.y) < 1.1 && Stealth.clear(level, corner.add(goal.x, goal.y + 1.0, goal.z), corner.add(spot.x, y + 1.0, spot.z))) {
				return new Vec3(spot.x, y, spot.z);
			}
		}

		return goal;
	}

	/** Whether none of their own stands between them and the player (they do not shoot through a comrade). */
	static boolean lineFree(StorySession s, Figure figure, Vec3 me) {
		Vec3 from = figure.eye();
		Vec3 to = me.add(0.0, 1.0, 0.0);

		for (Figure other : s.figures) {
			if (other == figure || other.mode == Figure.DOWN || !other.ship.equals(figure.ship)) {
				continue;
			}

			if (other.box().inflate(0.25).clip(from, to).isPresent()) {
				return false;
			}
		}

		return true;
	}

	/** A stun bolt at the player: aimed at the chest, a little off (more from afar, more at a runner). */
	private static void fire(StorySession s, Figure figure, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, double distance) {
		Vec3 muzzle = corner.add(figure.eye().add(figure.look().scale(0.6)).subtract(0.0, 0.35, 0.0));
		Vec3 aim = player.position().add(0.0, 1.0, 0.0).subtract(muzzle).normalize();
		Vec3 moving = player.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		float spread = (float) (1.4 + distance * 0.12 + (player.isSprinting() ? 2.2 : moving.length() > 0.08 ? 0.9 : 0.0));
		BlasterBolt bolt = new BlasterBolt(level, muzzle, BlasterType.STUN, STUN_DAMAGE);
		bolt.shoot(aim.x, aim.y, aim.z, BlasterType.STUN.speed(), spread);
		level.addFreshEntity(bolt);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ShipInteriors.sound(pistol(figure) ? "blaster.se14c" : "blaster.e11"), SoundSource.HOSTILE, 0.75F,
				1.25F + player.getRandom().nextFloat() * 0.1F);
		shots++;
	}

	/** For the tests: how many stun bolts the troopers fired since the game started. */
	private static int shots;

	static int shotsForTest() {
		return shots;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	/** The soldier of a chase nearest that point, or null (for tests). */
	static @Nullable Figure nearestChaser(StorySession s, Vec3 at) {
		Figure best = null;

		for (Figure figure : s.figures) {
			if (figure.mode == Figure.CHASE && (best == null || figure.position().distanceTo(at) < best.position().distanceTo(at))) {
				best = figure;
			}
		}

		return best;
	}
}
