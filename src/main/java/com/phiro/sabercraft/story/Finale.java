package com.phiro.sabercraft.story;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 19: the fight at the end of Kapitel 1. With Ilyra's saber lit the stealth rules end: the
 * troops shoot (real blaster bolts, which the saber sends back as everywhere in SaberCraft: in the
 * guard stance, with a swing); a bolt sent back or a stroke of the saber brings a trooper down.
 *
 * <p><b>Phase 21: the duel.</b> The Schattenklinge does one thing at a time, and each can be seen
 * coming:
 * <ul>
 * <li><b>Guard</b>: he circles at sword's length and parries every stroke. Three strokes in a row
 * into his guard and he answers at once with a quick thrust: hammering at him does not pay.</li>
 * <li><b>Wind-up</b>: he raises the blade for an overhead blow, draws it back for a sweep or
 * pulls it in for a thrust (the client shows which, and how far along). Blocked (the guard,
 * facing him) each blow of a combo; after the last blocked blow he staggers: that is the opening
 * (two strokes hit, then he recovers). A blow not blocked hurts, and he steps back.</li>
 * <li><b>Leap</b> (from the second phase, at a distance): he crouches, then leaps in with an
 * overhead blow, which can be blocked or stepped away from.</li>
 * <li><b>Push</b> (from the second phase): he raises a hand, then pushes with the Force; the
 * guard takes most of it.</li>
 * </ul>
 * At 65 and 35 percent he steps back and changes his way (a short pause in which nothing hits him):
 * the combos grow longer and quicker. At fifteen percent he breaks off, and {@link Chapter1}
 * plays his flight.
 *
 * <p><b>The Focus.</b> The one Force skill of the story (the player learns it when taking Ilyra's
 * saber): it fills while fighting (faster when blocking), and when full the focus key slows the
 * enemies to half speed for four seconds. Nothing else of the Force works on the people of the
 * story.
 */
final class Finale {
	/** The damage of a trooper's bolt (the player has 20). */
	static final float BOLT_DAMAGE = 3.0F;
	/** Ticks between a trooper's shots: at least, and a random part. */
	static final int SHOT_MIN = 40;
	static final int SHOT_RANDOM = 40;
	/** How far a trooper shoots, and how close they come. */
	static final double SHOT_RANGE = 26.0;
	static final double KEEP = 9.0;
	/**
	 * A saber stroke: how far it reaches, and what it takes of a trooper. Phase 22: four strokes
	 * (it was two), like a mob of Minecraft: each one is seen (he reels back, sparks, a bar over his
	 * head), and right after a hit he cannot be hit again ({@link #HURT_TICKS}, as a mob's short
	 * protection after damage), so hammering the key does not fell him faster.
	 */
	static final double STROKE_REACH = 3.6;
	static final float STROKE_TROOPER = 0.26F;
	/** Phase 22: ticks after a hit in which a trooper only takes glancing blows. */
	static final int HURT_TICKS = 9;
	/** Phase 22: how far a hit drives a trooper back (blocks), and how long he is thrown off. */
	static final double KNOCK = 0.45;
	static final int REEL_TICKS = 14;
	/** Phase 22: what a bolt sent back into its own shooter takes (two do it). */
	static final float RETURNED_BOLT = 0.5F;
	/** Phase 22: how far off the shooter the player may face for a bolt sent back to go to him (degrees). */
	static final double RETURN_CONE = 50.0;

	/** Phase 22: who fired a bolt (the figure's number), and the bolts already sent back to them. */
	private static final java.util.Map<BlasterBolt, Integer> SHOOTERS = new java.util.WeakHashMap<>();
	private static final java.util.Set<BlasterBolt> RETURNED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/** What the Schattenklinge does ({@link Figure#action}). */
	static final int GUARD = 0;
	static final int WINDUP = 1;
	static final int OPEN_UP = 2;
	static final int LEAP = 3;
	static final int PUSH = 4;
	static final int TURN = 5;
	static final int RECOIL = 6;
	static final int ESCAPE = 7;
	/** How it looks ({@link Figure#attack}), sent to the client in the top bits of the wind-up byte. */
	static final int OVERHEAD = 1;
	static final int SWEEP = 2;
	static final int THRUST = 3;
	static final int CROUCH = 4;
	static final int HAND = 5;
	static final int STAGGER = 6;
	static final int SPIN = 7;

	/** Per phase (1..3): wind-up of a blow, how much quicker the following blows of a combo, the damage, the opening. */
	static final int[] WINDUPS = {0, 16, 14, 12};
	static final int FASTER = 3;
	static final float[] BLOWS = {0.0F, 4.0F, 3.5F, 3.0F};
	static final int[] OPENINGS = {0, 22, 18, 15};
	/** For the tests: an opening as long as the first phase's. */
	static final int OPEN = 22;
	static final double BLOW_REACH = 3.2;
	static final double THRUST_REACH = 3.7;
	static final int HITS_PER_OPENING = 2;
	/** Strokes into his guard in a row before he answers. */
	static final int PARRIES = 3;
	static final int RIPOSTE = 7;
	static final double LEAP_FROM = 4.5;
	static final int CROUCH_TICKS = 16;
	static final double LEAP_SPEED = 11.0;
	static final int HAND_TICKS = 14;
	static final int PUSH_EVERY = 200;
	static final int LEAP_EVERY = 170;
	static final int TURN_TICKS = 50;
	static final int RECOIL_TICKS = 14;
	/** A hit on him, the phases and the end. */
	static final float HIT = 0.11F;
	static final float PHASE_TWO = 0.65F;
	static final float PHASE_THREE = 0.35F;
	static final float BREAKS_OFF = 0.15F;
	/** Focus: how long, how much a block and each tick adds. */
	static final int FOCUS_TICKS = 80;
	static final float FOCUS_BLOCK = 0.34F;
	static final float FOCUS_TIME = 0.004F;

	private static int strokes;
	private static int ripostes;
	private static int foci;

	private Finale() {
	}

	/** A section of the fight begins: the fighters wake (each a little apart). */
	static void arm(StorySession s) {
		int i = 0;

		for (Figure figure : s.figures) {
			if (figure.hostile && figure.mode != Figure.DOWN) {
				figure.mode = Figure.FIGHT;
				figure.cooldown = 30 + i++ * 17;
				figure.windup = 0;
			}
		}
	}

	/** Every tick of the fight. */
	static void tick(StorySession s, ServerPlayer player, ServerLevel level) {
		String ship = s.here;

		if (ship == null) {
			return;
		}

		focusTick(s);

		// The Focus: the enemies move on every other tick only.
		if (slowed(s) && (level.getGameTime() & 1L) == 1L) {
			return;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.origin(ship));
		Vec3 me = player.position().subtract(corner);
		Stealth.crowd(s.figures);

		try {
			for (Figure figure : s.figures) {
				if (!figure.hostile || figure.mode == Figure.DOWN || !ship.equals(figure.ship)) {
					continue;
				}

				if (figure.mode != Figure.FIGHT) {
					figure.mode = Figure.FIGHT;
				}

				if (figure.saber) {
					duel(s, figure, player, me, level, corner);
				} else {
					trooper(s, figure, player, me, level, corner);
				}

				if (s.completing() || !s.fighting) {
					return;
				}
			}
		} finally {
			Stealth.crowd(java.util.List.of());
		}

		bolts(s, player, level, corner);
	}

	// --- Troopers ----------------------------------------------------------------------------------

	private static void trooper(StorySession s, Figure figure, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 chest = me.add(0.0, 1.0, 0.0);
		double distance = figure.eye().distanceTo(chest);
		boolean sees = distance < SHOT_RANGE && Stealth.clear(level, corner.add(figure.eye()), corner.add(chest));

		if (sees) {
			figure.turnTowards(me, 14.0F, true);

			if (distance > KEEP) {
				Stealth.step(figure, me, Stealth.WALK, level, corner);
			} else {
				figure.speed = 0.0F;
			}

			figure.headYaw = Crew.yaw(figure.position(), me);

			if (--figure.cooldown <= 0) {
				shoot(figure, player, level, corner);
				figure.cooldown = SHOT_MIN + player.getRandom().nextInt(SHOT_RANDOM);
			}

			// The rifle comes up the last ten ticks before a shot.
			figure.windup = figure.cooldown <= 10 ? 10 - figure.cooldown + 1 : 0;
		} else {
			// Out of sight: they come to where the player is (in a fight everyone knows).
			if (!Stealth.step(figure, me, Stealth.WALK, level, corner)) {
				figure.speed = 0.0F;
			}

			figure.windup = 0;
			figure.cooldown = Math.max(figure.cooldown, 12);
		}
	}

	/** A trooper fires at the player: a real bolt, which the saber can send back. */
	private static void shoot(Figure figure, ServerPlayer player, ServerLevel level, Vec3 corner) {
		Vec3 muzzle = corner.add(figure.eye().add(figure.look().scale(0.55)).subtract(0.0, 0.3, 0.0));
		Vec3 aim = player.position().add(0.0, 1.1, 0.0).subtract(muzzle).normalize();
		BlasterBolt bolt = new BlasterBolt(level, muzzle, BlasterType.E11, BOLT_DAMAGE);
		bolt.shoot(aim.x, aim.y, aim.z, BlasterType.E11.speed(), 2.6F);
		level.addFreshEntity(bolt);
		SHOOTERS.put(bolt, figure.id);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ShipInteriors.sound("blaster.e11"), SoundSource.HOSTILE, 0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
	}

	/**
	 * Bolts the player sent back (the saber's guard or a stroke in time, see
	 * {@code SaberAbilities.deflection}): Phase 22: one sent back while the player faces its
	 * shooter flies back to him (to his chest, from where it is), and hits him (half his strength);
	 * one sent elsewhere flies on as it was sent and may hit someone else. The Schattenklinge turns
	 * them aside.
	 */
	private static void bolts(StorySession s, ServerPlayer player, ServerLevel level, Vec3 corner) {
		AABB area = player.getBoundingBox().inflate(40.0);

		for (BlasterBolt bolt : level.getEntitiesOfClass(BlasterBolt.class, area, b -> b.isAlive() && b.getOwner() == player)) {
			Integer shooterId = SHOOTERS.get(bolt);

			if (shooterId != null && RETURNED.add(bolt)) {
				Figure shooter = s.figureById(shooterId);
				returnBolt(s, bolt, shooter, player, corner);
			}

			Vec3 from = bolt.position().subtract(corner);
			Vec3 to = from.add(bolt.getDeltaMovement());

			for (Figure figure : s.figures) {
				if (!figure.hostile || figure.mode == Figure.DOWN || !s.here.equals(figure.ship)) {
					continue;
				}

				AABB box = figure.box().inflate(0.1);

				if (box.contains(from) || box.clip(from, to).isPresent()) {
					Vec3 at = from.add(corner);

					if (figure.saber) {
						// He turns it aside.
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.3);
						level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.parry"), SoundSource.HOSTILE, 0.8F, 1.0F);
					} else {
						hurt(s, figure, RETURNED_BOLT, level, at, true);
						s.counters.merge("returned_hits", 1, Integer::sum);
					}

					bolt.discard();
					break;
				}
			}
		}
	}

	/**
	 * Phase 22: a bolt the player sent back goes to the trooper who fired it, if the player faced
	 * him (within {@link #RETURN_CONE} degrees) and he still stands: straight to his chest, a
	 * little quicker. Otherwise it flies on as the saber sent it.
	 */
	private static void returnBolt(StorySession s, BlasterBolt bolt, @org.jspecify.annotations.Nullable Figure shooter, ServerPlayer player, Vec3 corner) {
		if (shooter == null || shooter.mode == Figure.DOWN || !shooter.hostile || shooter.saber || s.here == null || !s.here.equals(shooter.ship)) {
			return;
		}

		Vec3 chest = shooter.position().add(0.0, 1.2, 0.0).add(corner);
		Vec3 toShooter = chest.subtract(player.getEyePosition()).normalize();

		if (toShooter.dot(player.getLookAngle()) < Math.cos(Math.toRadians(RETURN_CONE))) {
			return;
		}

		Vec3 way = chest.subtract(bolt.position()).normalize();
		double speed = Math.max(1.2, bolt.getDeltaMovement().length()) * 1.1;
		bolt.setDeltaMovement(way.scale(speed));
		bolt.needsSync = true;
		returned++;
	}

	/** Phase 22: for tests: how many bolts went back to their shooter. */
	static int returned;

	/** A fighter is hit: down when nothing is left. */
	private static void hurt(StorySession s, Figure figure, float amount, ServerLevel level, Vec3 at) {
		hurt(s, figure, amount, level, at, false);
	}

	/**
	 * A fighter is hit: down when nothing is left. Phase 22: a trooper reels back from it (driven
	 * back a step, his aim lost for a moment) and, just hit, only takes glancing blows from a
	 * saber; a bolt always counts.
	 */
	private static void hurt(StorySession s, Figure figure, float amount, ServerLevel level, Vec3 at, boolean bolt) {
		if (!figure.saber && !bolt && s.time() - figure.hurtAt < HURT_TICKS) {
			// Glancing off his armour: sparks, a clank, nothing more.
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.06, 0.08, 0.06, 0.15);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 0.35F, 1.4F);
			return;
		}

		figure.hurtAt = s.time();

		if (!figure.saber) {
			reel(s, figure, level);
		}

		figure.health = Math.max(0.0F, figure.health - amount);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.1, 0.2, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.08, 0.1, 0.08, 0.25);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 0.9F, 1.0F);
		// The client flashes the one hit.
		Story.cue(s, Story.CUE_DUEL, "hit", "", figure.id);

		if (figure.health <= 0.0F) {
			figure.mode = Figure.DOWN;
			figure.speed = 0.0F;
			figure.windup = 0;
		} else {
			figure.cooldown = Math.max(figure.cooldown, figure.saber ? 16 : REEL_TICKS + 6);
			figure.windup = 0;
		}

		s.dirty = true;
	}

	/** Phase 22: a trooper hit is driven back from the player a little (where there is room). */
	private static void reel(StorySession s, Figure figure, ServerLevel level) {
		ServerPlayer player = s.player();

		if (player == null || s.here == null) {
			return;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		Vec3 me = player.position().subtract(corner);
		Vec3 away = new Vec3(figure.x - me.x, 0.0, figure.z - me.z);

		if (away.lengthSqr() < 1.0E-4) {
			return;
		}

		away = away.normalize();

		for (double d = KNOCK; d > 0.1; d -= 0.15) {
			double nx = figure.x + away.x * d;
			double nz = figure.z + away.z * d;

			if (Stealth.free(level, corner, nx, figure.y, nz, figure.crew.height()) && !Stealth.crowded(figure, nx, figure.y, nz)) {
				figure.x = nx;
				figure.z = nz;
				break;
			}
		}
	}

	// --- The player's saber ------------------------------------------------------------------------

	/** The player struck (the attack key with the saber lit): the fighter in front within reach is hit or parries. */
	static void strike(StorySession s, ServerPlayer player) {
		ServerLevel level = Chapter1.interiors(s);

		if (!s.fighting || level == null || s.here == null || !SaberVariant.canHold(player.getMainHandItem()) || s.timer("stroke")) {
			return;
		}

		s.timer("stroke", 6);
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		Vec3 eye = player.getEyePosition().subtract(corner);
		Vec3 look = player.getLookAngle();
		Figure best = null;
		double bestDistance = STROKE_REACH;

		for (Figure figure : s.figures) {
			if (!figure.hostile || figure.mode == Figure.DOWN || !s.here.equals(figure.ship)) {
				continue;
			}

			Vec3 middle = figure.box().getCenter();
			double distance = middle.distanceTo(eye);

			if (distance < bestDistance && middle.subtract(eye).normalize().dot(look) > 0.5) {
				best = figure;
				bestDistance = distance;
			}
		}

		if (best == null) {
			return;
		}

		strokes++;
		Vec3 at = best.box().getCenter().add(corner);

		if (!best.saber) {
			hurt(s, best, STROKE_TROOPER, level, at);
			return;
		}

		duelStroke(s, best, player, eye, level, at);
	}

	/** A stroke at the Schattenklinge: it hits when he is open (or from behind), else he parries. */
	private static void duelStroke(StorySession s, Figure f, ServerPlayer player, Vec3 eye, ServerLevel level, Vec3 at) {
		Vec3 toPlayer = new Vec3(eye.x - f.x, 0.0, eye.z - f.z).normalize();
		boolean behind = toPlayer.dot(f.look()) < -0.35;
		boolean open = f.action == OPEN_UP || behind && f.action != TURN && f.action != ESCAPE;

		if (open) {
			int before = phase(f);
			hurt(s, f, HIT, level, at);
			f.parried = 0;
			f.hits++;
			s.counters.merge("duel_hits", 1, Integer::sum);

			if (f.health <= BREAKS_OFF) {
				breaksOff(s, f);
				return;
			}

			if (phase(f) != before) {
				turn(s, f);
			} else if (behind || f.hits >= HITS_PER_OPENING) {
				// He has had enough of that: spins round and steps back.
				act(f, RECOIL, SPIN, RECOIL_TICKS);
				f.hits = 0;
			} else {
				// Each hit takes some of the opening.
				f.timer = Math.max(4, f.timer - 4);
			}

			return;
		}

		// Parried.
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 10, 0.1, 0.1, 0.1, 0.35);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
		Story.cue(s, Story.CUE_DUEL, "parried", "", f.id);

		if (f.action == GUARD || f.action == RECOIL) {
			if (++f.parried >= PARRIES) {
				// Hammering at his guard: he answers at once, with a thrust.
				f.parried = 0;
				f.combo = 1;
				blowStarts(f, THRUST, RIPOSTE, level, s);
				ripostes++;

				if (!s.has("tmp.hint_riposte")) {
					s.set("tmp.hint_riposte");
					s.hint("sabercraft.story.hint.duel_riposte");
				}
			}
		}
	}

	// --- The duel ----------------------------------------------------------------------------------

	/** The Schattenklinge lights his saber: the duel begins. */
	static void duelStarts(StorySession s) {
		Figure shadow = s.figure("shadow");

		if (shadow == null) {
			return;
		}

		shadow.hostile = true;
		shadow.saber = true;
		shadow.lit = true;
		shadow.mode = Figure.FIGHT;
		shadow.cooldown = 30;
		shadow.windup = 0;
		shadow.timer = 0;
		shadow.combo = 0;
		shadow.parried = 0;
		shadow.hits = 0;
		act(shadow, GUARD, 0, 0);
		s.fighting = true;
		s.focus = 1.0F;
		s.focusTicks = 0;
		s.set("tmp.duel");
		s.timer("push", PUSH_EVERY);
		s.timer("leap", LEAP_EVERY);
		Chapter1.music(s, s.mission, s.step().id());
		Story.cue(s, Story.CUE_SOUND, "", "saber.ignite", shadow.id);
		s.hint("sabercraft.story.hint.duel_start");
		s.dirty = true;
	}

	/** Which phase of the duel (1..3) by his strength. */
	static int phase(Figure f) {
		return f.health > PHASE_TWO ? 1 : f.health > PHASE_THREE ? 2 : 3;
	}

	private static void act(Figure f, int action, int attack, int ticks) {
		f.action = action;
		f.attack = attack;
		f.timer = ticks;
		f.windup = 0;
	}

	private static void duel(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		int phase = phase(f);
		double distance = Math.hypot(me.x - f.x, me.z - f.z);

		if (f.action != LEAP || f.windup > 0) {
			f.turnTowards(me, f.action == WINDUP ? 9.0F : 16.0F, true);
		}

		f.headYaw = Crew.yaw(f.position(), me);

		switch (f.action) {
			case WINDUP -> {
				f.speed = 0.0F;

				// A thrust carries him in a little.
				if (f.attack == THRUST && distance > 2.2) {
					Stealth.step(f, me, 2.5, level, corner);
				}

				if (--f.windup <= 0) {
					blow(s, f, player, me, level, corner, phase);
				}
			}
			case OPEN_UP -> {
				f.speed = 0.0F;

				if (--f.timer <= 0) {
					act(f, GUARD, 0, 0);
					f.cooldown = 10;
					f.hits = 0;
				}
			}
			case RECOIL, TURN -> {
				// He steps back to sword's length and further (and turns no stroke into an opening).
				double keep = f.action == TURN ? 4.5 : 3.0;

				if (distance < keep) {
					Vec3 away = f.position().add(f.position().subtract(me).normalize().scale(2.0));
					Stealth.step(f, away, Stealth.WALK * 1.6, level, corner);
				} else {
					f.speed = 0.0F;
				}

				if (--f.timer <= 0) {
					act(f, GUARD, 0, 0);
					f.cooldown = f.action == TURN ? 6 : 12;
				}
			}
			case LEAP -> leap(s, f, player, me, level, corner, phase);
			case PUSH -> {
				f.speed = 0.0F;

				if (--f.windup <= 0) {
					push(s, f, player, level, corner);
					act(f, GUARD, 0, 0);
					f.cooldown = 20;
				}
			}
			case ESCAPE -> f.speed = 0.0F;
			default -> guard(s, f, player, me, level, corner, phase, distance);
		}
	}

	/** On guard: circling at sword's length until he goes in. */
	private static void guard(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase, double distance) {
		RandomSource random = player.getRandom();
		boolean clear = Stealth.clear(level, corner.add(f.eye()), player.getEyePosition());

		if (f.cooldown > 0) {
			f.cooldown--;

			if (distance > 3.0) {
				Stealth.step(f, me, Stealth.WALK * 1.3, level, corner);
			} else if (distance < 1.6) {
				Stealth.step(f, f.position().add(f.position().subtract(me).normalize()), Stealth.WALK, level, corner);
			} else {
				// Circling: a step to the side.
				Vec3 to = f.position().subtract(me).normalize();
				Vec3 side = new Vec3(-to.z, 0.0, to.x).scale(f.strafe);

				if (!Stealth.step(f, f.position().add(side), Stealth.WALK * 0.8, level, corner)) {
					f.strafe = -f.strafe;
				}
			}

			return;
		}

		if (phase >= 2 && !s.timer("push") && distance > 2.5 && distance < 8.0 && clear) {
			act(f, PUSH, HAND, HAND_TICKS);
			f.windup = HAND_TICKS;
			s.timer("push", PUSH_EVERY);
			return;
		}

		if (phase >= 2 && !s.timer("leap") && distance > LEAP_FROM && distance < 9.0 && clear) {
			act(f, LEAP, CROUCH, CROUCH_TICKS);
			f.windup = CROUCH_TICKS;
			f.lure = null;
			s.timer("leap", LEAP_EVERY);
			return;
		}

		if (distance <= BLOW_REACH - 0.4) {
			f.combo = switch (phase) {
				case 1 -> 1 + random.nextInt(2);
				case 2 -> 2 + random.nextInt(2);
				default -> 3;
			};
			blowStarts(f, 1 + random.nextInt(3), WINDUPS[phase], level, s);
		} else if (!Stealth.step(f, me, 3.8, level, corner)) {
			f.speed = 0.0F;
		}
	}

	/** A blow begins: the blade goes up (or back, or in). */
	private static void blowStarts(Figure f, int kind, int ticks, ServerLevel level, StorySession s) {
		act(f, WINDUP, kind, ticks);
		f.windup = ticks;
		Vec3 at = f.position().add(Vec3.atLowerCornerOf(s.origin(f.ship)));
		level.playSound(null, at.x, at.y + 1.2, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.7F, kind == THRUST ? 1.1F : 0.8F);
	}

	/** His blow falls: blocked (guard, facing him), it leads on in the combo or leaves him open; otherwise it hurts. */
	private static void blow(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase) {
		double distance = Math.hypot(me.x - f.x, me.z - f.z);
		double reach = f.attack == THRUST ? THRUST_REACH : BLOW_REACH;
		Vec3 at = f.position().add(corner).add(f.look().scale(0.9)).add(0.0, f.attack == SWEEP ? 0.8 : 1.3, 0.0);
		RandomSource random = player.getRandom();
		f.combo--;

		if (distance > reach) {
			// Missed: the player stepped back.
			if (f.combo > 0) {
				blowStarts(f, 1 + random.nextInt(3), WINDUPS[phase] - FASTER, level, s);
			} else {
				act(f, OPEN_UP, STAGGER, OPENINGS[phase] - 6);
				f.hits = 0;
			}

			return;
		}

		Vec3 toHim = new Vec3(f.x - me.x, 0.0, f.z - me.z).normalize();
		Vec3 look = player.getLookAngle();
		boolean facing = new Vec3(look.x, 0.0, look.z).normalize().dot(toHim) > 0.35;
		boolean guard = SaberAbilities.isGuarding(player) || player.isUsingItem() && SaberVariant.canHold(player.getUseItem());

		if (guard && facing) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.1, 0.1, 0.1, 0.45);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.1F, 0.85F + random.nextFloat() * 0.15F);
			s.counters.merge("blocked", 1, Integer::sum);
			s.focus = Math.min(1.0F, s.focus + FOCUS_BLOCK);
			Story.cue(s, Story.CUE_DUEL, "blocked", "", f.id);

			if (f.combo > 0) {
				blowStarts(f, 1 + random.nextInt(3), WINDUPS[phase] - FASTER, level, s);
			} else {
				// The last blow of the combo blocked: he staggers. The opening.
				act(f, OPEN_UP, STAGGER, OPENINGS[phase]);
				f.hits = 0;
				f.parried = 0;
			}
		} else {
			player.hurtServer(level, level.damageSources().generic(), BLOWS[phase]);
			Vec3 push = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize().scale(0.6);
			player.push(push.x, 0.25, push.z);
			player.hurtMarked = true;
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 1.0F, 0.9F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.1, player.getZ(), 12, 0.15, 0.2, 0.15, 0.3);
			Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(BLOWS[phase]));
			f.combo = 0;
			act(f, RECOIL, 0, 10);
		}
	}

	/** The leap: crouching (the warning), then through the air, and an overhead blow as he lands. */
	private static void leap(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase) {
		if (f.windup > 0) {
			f.speed = 0.0F;

			if (--f.windup == 0) {
				f.lure = me;
				f.attack = OVERHEAD;
				f.timer = 20;
				Vec3 at = f.position().add(corner);
				level.playSound(null, at.x, at.y + 1.0, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 1.0F, 0.7F);
			}

			return;
		}

		Vec3 target = f.lure != null ? f.lure : me;
		boolean moved = Stealth.step(f, target, LEAP_SPEED, level, corner);

		if (!moved || --f.timer <= 0 || Math.hypot(target.x - f.x, target.z - f.z) < 1.8) {
			f.lure = null;
			f.combo = 1;
			act(f, WINDUP, OVERHEAD, 4);
			f.windup = 4;
		}
	}

	/** A push with the Force: the guard takes most of it. */
	private static void push(StorySession s, Figure f, ServerPlayer player, ServerLevel level, Vec3 corner) {
		Vec3 me = player.position().subtract(corner);
		Vec3 away = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize();
		boolean guard = SaberAbilities.isGuarding(player);
		double strength = guard ? 0.5 : 1.4;
		player.push(away.x * strength, guard ? 0.1 : 0.35, away.z * strength);
		player.hurtMarked = true;

		if (!guard) {
			player.hurtServer(level, level.damageSources().generic(), 2.0F);
			Story.cue(s, Story.CUE_DUEL, "hurt", "", 2);
		}

		Vec3 at = f.position().add(corner).add(0.0, 1.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CLOUD, at.x + away.x, at.y, at.z + away.z, 12, 0.3, 0.3, 0.3, 0.05);
		s.hint("sabercraft.story.hint.push");
	}

	/** A new phase: he steps back, and comes on quicker. */
	private static void turn(StorySession s, Figure f) {
		act(f, TURN, 0, TURN_TICKS);
		f.hits = 0;
		f.parried = 0;
		f.combo = 0;
		int phase = phase(f);
		s.hear("shadow", "sabercraft.story.shout.shadow_phase" + phase);
		s.hint("sabercraft.story.hint.duel_phase" + phase);
		Story.cue(s, Story.CUE_DUEL, "phase", "", phase);
		// The push and the leap come soon after.
		s.timer("push", TURN_TICKS + 30);
		s.timer("leap", TURN_TICKS + 90);
	}

	/** At the end of his strength he breaks off: {@link Chapter1} plays his flight. */
	private static void breaksOff(StorySession s, Figure f) {
		act(f, ESCAPE, STAGGER, 0);
		f.hostile = false;
		f.mode = Figure.HELD;
		f.speed = 0.0F;
		f.combo = 0;
		f.target = null;
		s.fighting = false;
		s.focusTicks = 0;
		s.unset("tmp.duel");

		// The troopers still about fall back (the fight is over).
		for (Figure figure : s.figures) {
			if (figure != f && figure.hostile && figure.mode != Figure.DOWN && !figure.saber) {
				figure.mode = Figure.DOWN;
			}
		}

		Story.cue(s, Story.CUE_DUEL, "over", "", f.id);
		Chapter1.duelEnds(s, f);
	}

	// --- The Focus ---------------------------------------------------------------------------------

	/** The focus key: slows the enemies when it is full. Returns a hint's key, or null when it began. */
	static @Nullable String focus(StorySession s) {
		if (!s.has("know.focus")) {
			return "sabercraft.story.hint.focus_unknown";
		}

		if (!s.fighting) {
			return "sabercraft.story.hint.focus_calm";
		}

		if (s.focusTicks > 0) {
			return null;
		}

		if (s.focus < 1.0F) {
			return "sabercraft.story.hint.focus_empty";
		}

		s.focus = 0.0F;
		s.focusTicks = FOCUS_TICKS;
		foci++;
		Story.cue(s, Story.CUE_SOUND, "", "story.focus_in", -1);
		Story.cue(s, Story.CUE_DUEL, "focus_on", "", FOCUS_TICKS);
		return null;
	}

	/** Every tick of a fight: the Focus fills, and ends. */
	static void focusTick(StorySession s) {
		if (s.focusTicks > 0) {
			if (--s.focusTicks == 0) {
				Story.cue(s, Story.CUE_SOUND, "", "story.focus_out", -1);
				Story.cue(s, Story.CUE_DUEL, "focus_off", "", 0);
			}
		} else if (s.has("know.focus")) {
			s.focus = Math.min(1.0F, s.focus + FOCUS_TIME);
		}

		// The meter for the HUD, four times a second.
		if (s.has("know.focus") && s.stepTicks % 5 == 0) {
			Story.cue(s, Story.CUE_DUEL, "meter", "", Math.round(s.focus * 100.0F) + (s.focusTicks > 0 ? 1000 : 0));
		}
	}

	/** Whether the Focus slows the enemies now. */
	static boolean slowed(StorySession s) {
		return s.focusTicks > 0;
	}

	// --- For the client and the tests --------------------------------------------------------------

	/**
	 * What the client is shown of a fighter's blow (one byte): the kind in the top three bits, how
	 * far along in the bottom five (0..31). A trooper's raised rifle is kind 0.
	 */
	static int shown(Figure f) {
		if (!f.saber) {
			return Math.min(31, f.windup);
		}

		int progress = switch (f.action) {
			case WINDUP -> f.timer > 0 ? 31 * (f.timer - f.windup) / f.timer : 31;
			case LEAP -> f.windup > 0 ? 31 * (CROUCH_TICKS - f.windup) / CROUCH_TICKS : 20;
			case PUSH -> 31 * (HAND_TICKS - f.windup) / HAND_TICKS;
			case OPEN_UP, ESCAPE -> 31;
			case RECOIL -> f.attack == SPIN ? 31 * (RECOIL_TICKS - Math.max(0, f.timer)) / RECOIL_TICKS : 0;
			default -> 0;
		};

		return f.attack << 5 | Mth.clamp(progress, 0, 31);
	}

	/** The fighter nearest that point, or null (for tests). */
	static @Nullable Figure nearestFighter(StorySession s, Vec3 at) {
		Figure best = null;
		double bestDistance = Double.MAX_VALUE;

		for (Figure figure : s.figures) {
			double distance = figure.position().distanceTo(at);

			if (figure.hostile && figure.mode != Figure.DOWN && distance < bestDistance) {
				best = figure;
				bestDistance = distance;
			}
		}

		return best;
	}

	static int strokesForTest() {
		return strokes;
	}

	static int ripostesForTest() {
		return ripostes;
	}

	static int fociForTest() {
		return foci;
	}
}
