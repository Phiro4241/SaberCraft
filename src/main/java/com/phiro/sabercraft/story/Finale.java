package com.phiro.sabercraft.story;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ColorParticleOption;
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
	/** Phase 23: a stroke this long (ticks) after the one before is a full, heavy swing. */
	static final int HEAVY_GAP = 16;
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
	/** Phase 23: the blade lock between the two phases. */
	static final int LOCK = 5;
	static final int RECOIL = 6;
	/** Phase 23: disarmed: the end of the duel (Chapter1 plays the rest). */
	static final int ESCAPE = 7;
	/** Phase 23: the blades met (both struck at once): both thrown apart a little, then on guard. */
	static final int CLASHED = 8;
	/** How it looks ({@link Figure#attack}), sent to the client in the top bits of the wind-up byte. */
	static final int OVERHEAD = 1;
	static final int SWEEP = 2;
	static final int THRUST = 3;
	static final int CROUCH = 4;
	static final int HAND = 5;
	static final int STAGGER = 6;
	static final int SPIN = 7;

	/**
	 * Phase 23: per phase (1, 2): the wind-up of a combo's first blow and of the blows after it,
	 * what a blow takes of the player, how long an opening lasts, how long she circles between
	 * attacks, and how many strokes into her guard before she answers.
	 */
	static final int[] WINDUPS = {0, 18, 14};
	static final int[] FOLLOW = {0, 14, 11};
	static final float[] BLOWS = {0.0F, 3.0F, 3.5F};
	static final int[] OPENINGS = {0, 30, 24};
	static final int[] PAUSES = {0, 34, 18};
	static final int[] PARRIES = {0, 3, 2};
	/** Phase 23: her combos, per phase: the kinds of blow one after the other (the second phase's are longer and mixed). */
	static final int[][][] PATTERNS = {{}, {{OVERHEAD}, {SWEEP}, {THRUST}, {OVERHEAD, SWEEP}, {SWEEP, THRUST}, {SWEEP, OVERHEAD}},
			{{SWEEP, SWEEP, OVERHEAD}, {THRUST, SWEEP, THRUST}, {OVERHEAD, THRUST}, {SWEEP, OVERHEAD, SWEEP}, {OVERHEAD, SWEEP, THRUST}}};
	/** For the tests: an opening as long as the first phase's. */
	static final int OPEN = 30;
	static final double BLOW_REACH = 3.2;
	static final double THRUST_REACH = 3.7;
	/** Strokes that hit her in one opening, at most. */
	static final int HITS_PER_OPENING = 3;
	/** Her answer to strokes into her guard: a quick thrust (still to be seen and blocked). */
	static final int RIPOSTE = 10;
	/**
	 * Phase 23: a stroke of the player into her wind-up meets her blade: the blades clash (nobody
	 * is hurt; both are thrown apart a little). Two clashes in a row and she follows up at once.
	 */
	static final int CLASH_TICKS = 12;
	/** Phase 23: the guard raised this shortly (ticks) before her blow lands: a perfect parry (she reels, a longer opening). */
	static final int PERFECT = 6;
	static final double LEAP_FROM = 4.5;
	static final int CROUCH_TICKS = 18;
	static final double LEAP_SPEED = 11.0;
	static final int HAND_TICKS = 16;
	static final int PUSH_EVERY = 220;
	static final int LEAP_EVERY = 180;
	static final int RECOIL_TICKS = 14;
	/** What a light and a heavy stroke take of her (she has 1); the second phase begins at half. */
	static final float HIT = 0.065F;
	static final float HIT_HEAVY = 0.1F;
	static final float PHASE_TWO = 0.5F;
	/** Phase 23: the blade lock between the phases (ticks), and how much each stroke of the player during it shortens it. */
	static final int LOCK_TICKS = 70;
	static final int LOCK_PRESS = 5;
	/** Phase 23: the end: how long she stares at her empty hand (ticks) before she runs. */
	static final int DISARMED_TICKS = 92;
	/** Focus: how long, how much a block and each tick adds. */
	static final int FOCUS_TICKS = 80;
	static final float FOCUS_BLOCK = 0.34F;
	static final float FOCUS_TIME = 0.004F;

	private static int strokes;
	private static int ripostes;
	private static int foci;
	/** Phase 23: for the tests: clashes, perfect parries, dodged blows, blade locks, disarms since the game started. */
	private static int clashes;
	private static int perfects;
	private static int dodges;
	private static int locks;
	private static int disarms;

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

	/** Phase 23: a fight begins: everyone hunting the player joins it at once (see {@link Combat#engage}). */
	static void engageAll(StorySession s) {
		ServerPlayer player = s.player();
		ServerLevel level = Chapter1.interiors(s);

		if (player == null || level == null || s.here == null) {
			return;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		Combat.engage(s, s.here, player.position().subtract(corner), level, corner, true);
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

		// Phase 23: everyone still hunting the player in an old chase or search fights now (and
		// soldiers who see the fight join, now and then looked for).
		if (!s.has("tmp.duel")) {
			Combat.engage(s, ship, me, level, corner, s.stepTicks % 10 == 0);
		}

		try {
			Hits.settle(s, level, corner);

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
		// Phase 23: thrown back or stumbling: nothing else until it is over.
		if (Hits.tick(figure, level, corner)) {
			figure.windup = 0;
			figure.cooldown = Math.max(figure.cooldown, 8);
			return;
		}

		Vec3 chest = me.add(0.0, 1.0, 0.0);
		double distance = figure.eye().distanceTo(chest);
		boolean sees = distance < SHOT_RANGE && Stealth.clear(level, corner.add(figure.eye()), corner.add(chest));

		// Phase 24: a guard at his post holds it (rifle up, facing the way one comes) until he sees
		// the player, hears them close, or is hit: then a shout to the others, and he fights.
		if (figure.post) {
			if (sees && distance < POST_SIGHT || distance < POST_NEAR || figure.health < 1.0F) {
				figure.post = false;
				figure.cooldown = Math.max(figure.cooldown, POST_FIRST_SHOT);

				if (!s.timer("contact")) {
					s.timer("contact", 120);
					s.hear(Story.voice(figure), "sabercraft.story.shout.contact");
				}
			} else {
				figure.speed = 0.0F;
				figure.windup = 4;
				figure.yaw = figure.crew.yaw();
				figure.headYaw = figure.yaw + 18.0F * (float) Math.sin(s.time() / 30.0 + figure.id);
				return;
			}
		}

		// Phase 23: where they go: to the player along a way they can walk (through doors, round
		// consoles and corners), each a little apart from the others, never straight into the wall
		// between; where no way leads (the reactor's bridge gone) they stop at its end and shoot.
		figure.target = me;

		if (sees) {
			figure.turnTowards(me, 14.0F, true);

			if (distance > KEEP) {
				if (!Stealth.go(figure, Combat.apart(s, figure, me, level, corner), Stealth.WALK, level, corner, s.time())) {
					figure.speed = 0.0F;
				}

				figure.turnTowards(me, 14.0F, true);
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
			// Out of sight: they come to where the player is (in a fight everyone knows), the
			// way round (Phase 23; before they walked straight at the wall between).
			if (!Stealth.go(figure, Combat.apart(s, figure, me, level, corner), Stealth.WALK * 1.4, level, corner, s.time())) {
				figure.speed = 0.0F;
			}

			figure.windup = 0;
			figure.cooldown = Math.max(figure.cooldown, 12);
		}
	}

	/** Phase 24: a guard at his post sees this far, hears one this near; his first shot after a moment. */
	static final double POST_SIGHT = 18.0;
	static final double POST_NEAR = 5.0;
	static final int POST_FIRST_SHOT = 18;

	/** For the tests: the troopers' (and officers') bolts in a fight since the game started. */
	private static int shots;

	static int shotsForTest() {
		return shots;
	}

	/** A trooper fires at the player: a real bolt, which the saber can send back. */
	private static void shoot(Figure figure, ServerPlayer player, ServerLevel level, Vec3 corner) {
		shots++;
		Vec3 muzzle = corner.add(figure.eye().add(figure.look().scale(0.55)).subtract(0.0, 0.3, 0.0));
		Vec3 aim = player.position().add(0.0, 1.1, 0.0).subtract(muzzle).normalize();
		// Phase 23: an officer's pistol.
		boolean pistol = Combat.pistol(figure);
		BlasterType type = pistol ? BlasterType.SE14C : BlasterType.E11;
		BlasterBolt bolt = new BlasterBolt(level, muzzle, type, pistol ? BOLT_DAMAGE * 0.8F : BOLT_DAMAGE);
		bolt.shoot(aim.x, aim.y, aim.z, type.speed(), 2.6F);
		level.addFreshEntity(bolt);
		SHOOTERS.put(bolt, figure.id);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ShipInteriors.sound(pistol ? "blaster.se14c" : "blaster.e11"), SoundSource.HOSTILE, 0.8F,
				0.95F + player.getRandom().nextFloat() * 0.1F);
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
						Hits.bolt(s, figure, RETURNED_BOLT, level, corner, from);
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
		// Phase 23: a full swing (after a pause), one from a jump or a run is a heavy stroke; quick
		// strokes one after the other are light ones.
		long now = s.time();
		boolean heavy = now - s.lastStroke >= HEAVY_GAP || !player.onGround() && player.getDeltaMovement().y < -0.05 || player.isSprinting();
		s.lastStroke = now;

		if (!best.saber) {
			Hits.saber(s, best, heavy, level, corner, eye);
			return;
		}

		duelStroke(s, best, player, eye, level, at, heavy);
	}

	/**
	 * Phase 23: the player struck at the Schattenklinge. What happens depends on what she does, and
	 * it can always be seen: into her wind-up the blades clash (nobody is hurt, both are thrown
	 * apart); while she staggers (an opening) or from behind the stroke hits (she reels, a heavy
	 * stroke throws her back); into her guard she turns it aside, and answers strokes hammered at
	 * her guard with a quick thrust; in the blade lock each stroke pushes against her blade.
	 */
	private static void duelStroke(StorySession s, Figure f, ServerPlayer player, Vec3 eye, ServerLevel level, Vec3 at, boolean heavy) {
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		Vec3 me = player.position().subtract(corner);

		switch (f.action) {
			case LOCK -> {
				// Pressing against her blade: it ends the lock sooner (it lasts a moment at least, to be seen).
				int elapsed = LOCK_TICKS - f.timer;
				f.timer = Math.max(Math.max(1, 30 - elapsed), f.timer - LOCK_PRESS);
				s.counters.merge("lock_presses", 1, Integer::sum);
				Vec3 contact = contact(f, me).add(corner);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 10, 0.06, 0.06, 0.06, 0.4);
				level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 0.7F, 0.8F + player.getRandom().nextFloat() * 0.2F);
				Story.cue(s, Story.CUE_DUEL, "lock_push", "", f.id);
				return;
			}
			case ESCAPE, CLASHED -> {
				return;
			}
			case WINDUP -> {
				clash(s, f, player, me, level, corner);
				return;
			}
			case LEAP -> {
				if (f.windup <= 0) {
					// Into her leap: the blades meet in the air.
					clash(s, f, player, me, level, corner);
					return;
				}
			}
			default -> {
			}
		}

		Vec3 toPlayer = new Vec3(eye.x - f.x, 0.0, eye.z - f.z).normalize();
		boolean behind = toPlayer.dot(f.look()) < -0.35;

		if (f.action == OPEN_UP || behind && f.action != LEAP) {
			bossHit(s, f, player, me, level, corner, at, heavy);
			return;
		}

		parried(s, f, player, me, level, corner);
	}

	/** Phase 23: a stroke hits her: she reels (a heavy one throws her back), the phases and the end. */
	private static void bossHit(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, Vec3 at, boolean heavy) {
		int before = phase(f);
		f.health = Math.max(0.0F, f.health - (heavy ? HIT_HEAVY : HIT));
		f.hurtAt = s.time();
		f.parried = 0;
		f.burst = 0;
		f.hits++;
		s.counters.merge("duel_hits", 1, Integer::sum);
		s.counters.merge(heavy ? "duel_heavy" : "duel_light", 1, Integer::sum);
		f.react(heavy ? Figure.HEAVY : Figure.FLINCH, heavy ? 14 : 8);
		Hits.push(f, me, heavy ? 0.32 : 0.15);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, heavy ? 10 : 6, 0.1, 0.2, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, heavy ? 24 : 12, 0.1, 0.12, 0.1, heavy ? 0.4 : 0.25);

		if (heavy) {
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFD0C0), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}

		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, heavy ? 1.15F : 0.9F, heavy ? 0.8F : 1.0F);

		if (heavy) {
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.scorch"), SoundSource.HOSTILE, 0.9F, 0.9F);
		}

		Story.cue(s, Story.CUE_DUEL, heavy ? "hit_heavy" : "hit", "", f.id);
		s.dirty = true;

		if (f.health <= 0.0F) {
			disarm(s, f, player, me, level, corner);
			return;
		}

		if (before == 1 && f.health <= PHASE_TWO) {
			lockStarts(s, f, player, me, level, corner);
			return;
		}

		if (f.action != OPEN_UP || f.hits >= HITS_PER_OPENING) {
			// Enough (or struck from behind): she spins round and steps back.
			act(f, RECOIL, SPIN, RECOIL_TICKS);
			f.hits = 0;
		} else {
			// Each hit takes a little of the opening.
			f.timer = Math.max(6, f.timer - 3);
		}
	}

	/** Phase 23: a stroke into her guard: turned aside, blade on blade (strokes hammered at her are answered). */
	private static void parried(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 contact = contact(f, me).add(corner);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 10, 0.08, 0.08, 0.08, 0.35);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
		Story.cue(s, Story.CUE_DUEL, "parried", "", f.id, java.util.List.of(contact));
		s.counters.merge("parried", 1, Integer::sum);

		if (f.action == GUARD || f.action == RECOIL) {
			f.react(Figure.PARRY, 6);

			if (++f.parried >= PARRIES[phase(f)]) {
				// Hammering at her guard: she answers at once, with a thrust.
				riposte(s, f, level);
			}
		}
	}

	/** Phase 23: her answer: a quick thrust (one blow; blocked or dodged it leaves her open as any other). */
	private static void riposte(StorySession s, Figure f, ServerLevel level) {
		f.parried = 0;
		f.burst = 0;
		f.combo = 1;
		f.pattern = -1;
		blowStarts(f, THRUST, RIPOSTE, level, s);
		ripostes++;

		if (!s.has("tmp.hint_riposte")) {
			s.set("tmp.hint_riposte");
			s.hint("sabercraft.story.hint.duel_riposte");
		}
	}

	/**
	 * Phase 23: both struck at once: the blades meet. Sparks where they cross, a crack of sound, the
	 * camera jolts; both are thrown apart a little and nobody is hurt. Her blow is gone; a moment
	 * later she is on guard again (two clashes in a row and she follows up at once).
	 */
	private static void clash(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 contact = contact(f, me).add(corner);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 26, 0.1, 0.1, 0.1, 0.5);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF0E0), contact.x, contact.y, contact.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("story.clash_big"), SoundSource.HOSTILE, 1.2F, 0.95F + player.getRandom().nextFloat() * 0.1F);
		Vec3 away = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		away = away.lengthSqr() < 1.0E-4 ? f.look() : away.normalize();
		player.push(away.x * 0.45, 0.12, away.z * 0.45);
		player.hurtMarked = true;
		Hits.push(f, me, 0.26);
		f.react(Figure.CLASH, CLASH_TICKS);
		f.combo = 0;
		f.burst++;
		act(f, CLASHED, f.attack, CLASH_TICKS);
		clashes++;
		s.counters.merge("clashes", 1, Integer::sum);
		Story.cue(s, Story.CUE_DUEL, "clash", "", f.id, java.util.List.of(contact));

		if (!s.has("tmp.hint_clash")) {
			s.set("tmp.hint_clash");
			s.hint("sabercraft.story.hint.duel_clash");
		}
	}

	/** Phase 23: where the two blades meet (plot space): between them, at chest height. */
	static Vec3 contact(Figure f, Vec3 me) {
		Vec3 between = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		double length = between.length();
		Vec3 dir = length < 1.0E-3 ? f.look() : between.scale(1.0 / length);
		double along = Math.min(length * 0.5, 1.1);
		return new Vec3(f.x + dir.x * along, Math.max(f.y, me.y) + 1.35, f.z + dir.z * along);
	}

	// --- The duel ----------------------------------------------------------------------------------

	/** The Schattenklinge lights her saber: the duel begins. */
	static void duelStarts(StorySession s) {
		Figure shadow = s.figure("shadow");

		if (shadow == null) {
			return;
		}

		shadow.hostile = true;
		shadow.saber = true;
		shadow.lit = true;
		shadow.disarmed = false;
		shadow.mode = Figure.FIGHT;
		shadow.cooldown = 30;
		shadow.windup = 0;
		shadow.timer = 0;
		shadow.combo = 0;
		shadow.parried = 0;
		shadow.hits = 0;
		shadow.burst = 0;
		shadow.pattern = -1;
		shadow.react = 0;
		shadow.reactTicks = 0;
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

	/** Which phase of the duel (1, 2) by her strength: exactly two (Phase 23). */
	static int phase(Figure f) {
		return f.health > PHASE_TWO ? 1 : 2;
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

		switch (f.action) {
			case LOCK -> {
				lockTick(s, f, player, me, level, corner);
				return;
			}
			case ESCAPE -> {
				Hits.tick(f, level, corner);
				f.speed = 0.0F;
				return;
			}
			default -> {
			}
		}

		// Phase 23: thrown back by a hit, turning a stroke aside, reeling from a perfect parry.
		boolean held = Hits.tick(f, level, corner);

		if (f.action == CLASHED) {
			f.turnTowards(me, 10.0F, true);
			f.headYaw = Crew.yaw(f.position(), me);

			if (--f.timer <= 0) {
				if (f.burst >= 2) {
					// Two clashes in a row: she follows up at once.
					riposte(s, f, level);
				} else {
					act(f, GUARD, 0, 0);
					f.cooldown = PAUSES[phase] / 2;
				}
			}

			return;
		}

		if (held) {
			f.turnTowards(me, 6.0F, true);
			f.headYaw = Crew.yaw(f.position(), me);

			if (f.action == WINDUP) {
				act(f, GUARD, 0, 0);
				f.cooldown = PAUSES[phase];
			}

			return;
		}

		if (f.action != LEAP || f.windup > 0) {
			f.turnTowards(me, f.action == WINDUP ? 9.0F : 16.0F, true);
		}

		f.headYaw = Crew.yaw(f.position(), me);

		switch (f.action) {
			case WINDUP -> {
				f.speed = 0.0F;

				// A thrust carries her in a little.
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
			case RECOIL -> {
				// She steps back to sword's length and further (and turns no stroke into an opening).
				if (distance < 3.0) {
					Vec3 away = f.position().add(f.position().subtract(me).normalize().scale(2.0));
					Stealth.step(f, away, Stealth.WALK * 1.6, level, corner);
				} else {
					f.speed = 0.0F;
				}

				if (--f.timer <= 0) {
					act(f, GUARD, 0, 0);
					f.cooldown = PAUSES[phase] / 2;
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
			default -> guard(s, f, player, me, level, corner, phase, distance);
		}
	}

	/** On guard: circling at sword's length until she goes in (in the second phase sooner, and with more ways to attack). */
	private static void guard(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase, double distance) {
		RandomSource random = player.getRandom();
		boolean clear = Stealth.clear(level, corner.add(f.eye()), player.getEyePosition());

		if (f.cooldown > 0) {
			f.cooldown--;

			if (distance > 3.0) {
				Stealth.step(f, me, Stealth.WALK * (phase >= 2 ? 1.6 : 1.3), level, corner);
			} else if (distance < 1.6) {
				Stealth.step(f, f.position().add(f.position().subtract(me).normalize()), Stealth.WALK, level, corner);
			} else {
				// Circling: a step to the side.
				Vec3 to = f.position().subtract(me).normalize();
				Vec3 side = new Vec3(-to.z, 0.0, to.x).scale(f.strafe);

				if (!Stealth.step(f, f.position().add(side), Stealth.WALK * (phase >= 2 ? 1.1 : 0.8), level, corner)) {
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
			// A combo: one of her patterns (not the same twice running).
			int[][] patterns = PATTERNS[phase];
			int pick = random.nextInt(patterns.length);

			if (pick == f.pattern && patterns.length > 1) {
				pick = (pick + 1) % patterns.length;
			}

			f.pattern = pick;
			f.combo = patterns[pick].length;
			blowStarts(f, patterns[pick][0], WINDUPS[phase], level, s);
		} else if (!Stealth.step(f, me, 3.8, level, corner)) {
			f.speed = 0.0F;
		}
	}

	/** The kind of her next blow in the combo (a riposte or a leap's blow: by chance). */
	private static int nextKind(Figure f, int phase, RandomSource random) {
		int[][] patterns = PATTERNS[phase];
		int[] pattern = f.pattern >= 0 && f.pattern < patterns.length ? patterns[f.pattern] : null;
		int index = pattern == null ? -1 : pattern.length - f.combo;
		return pattern != null && index >= 0 && index < pattern.length ? pattern[index] : 1 + random.nextInt(3);
	}

	/** A blow begins: the blade goes up (or back, or in). */
	private static void blowStarts(Figure f, int kind, int ticks, ServerLevel level, StorySession s) {
		act(f, WINDUP, kind, ticks);
		f.windup = ticks;
		Vec3 at = f.position().add(Vec3.atLowerCornerOf(s.origin(f.ship)));
		level.playSound(null, at.x, at.y + 1.2, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.7F, kind == THRUST ? 1.1F : 0.8F);
	}

	/**
	 * Her blow falls. Phase 23: blocked (the guard up, facing her) it never hurts: the combo goes
	 * on, or after its last blow she staggers (an opening); a guard raised just in time (a perfect
	 * parry) throws her off balance at once, a longer opening. Dodged (the dash) or out of reach,
	 * it misses (after the last blow an opening too). Only a blow neither blocked nor dodged hurts.
	 */
	private static void blow(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase) {
		double distance = Math.hypot(me.x - f.x, me.z - f.z);
		double reach = f.attack == THRUST ? THRUST_REACH : BLOW_REACH;
		Vec3 at = contact(f, me).add(corner);
		RandomSource random = player.getRandom();
		f.combo--;
		boolean evading = SaberAbilities.evading(player);

		if (distance > reach || evading || Math.abs(me.y - f.y) > 2.0) {
			// Missed: the player stepped back or dodged.
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.6F, 0.7F);

			if (evading) {
				dodges++;
				s.counters.merge("dodged", 1, Integer::sum);
				Story.cue(s, Story.CUE_DUEL, "dodged", "", f.id);
			}

			if (f.combo > 0) {
				blowStarts(f, nextKind(f, phase, random), FOLLOW[phase], level, s);
			} else {
				act(f, OPEN_UP, STAGGER, OPENINGS[phase] - (evading ? 0 : 6));
				f.hits = 0;
			}

			return;
		}

		Vec3 toHim = new Vec3(f.x - me.x, 0.0, f.z - me.z).normalize();
		Vec3 look = player.getLookAngle();
		boolean facing = new Vec3(look.x, 0.0, look.z).normalize().dot(toHim) > 0.35;
		long guardTicks = guardTicks(player);

		if (guardTicks >= 0 && facing) {
			boolean perfect = guardTicks <= PERFECT;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, perfect ? 28 : 16, 0.1, 0.1, 0.1, 0.45);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound(perfect ? "story.clash_big" : "saber.clash"), SoundSource.HOSTILE, 1.1F,
					perfect ? 1.1F : 0.85F + random.nextFloat() * 0.15F);
			s.counters.merge(perfect ? "perfect" : "blocked", 1, Integer::sum);
			s.focus = Math.min(1.0F, s.focus + (perfect ? 0.5F : FOCUS_BLOCK));
			Story.cue(s, Story.CUE_DUEL, perfect ? "perfect" : "blocked", "", f.id, java.util.List.of(at));
			f.burst = 0;

			if (perfect) {
				// Just in time: her blade is thrown back, she reels: the opening, longer.
				perfects++;
				f.combo = 0;
				f.react(Figure.STUMBLE, 14);
				Hits.push(f, me, 0.25);
				act(f, OPEN_UP, STAGGER, OPENINGS[phase] + 8);
				f.hits = 0;
				f.parried = 0;

				if (!s.has("tmp.hint_perfect")) {
					s.set("tmp.hint_perfect");
					s.hint("sabercraft.story.hint.duel_perfect");
				}
			} else if (f.combo > 0) {
				if (phase >= 2) {
					// The second phase presses: each blocked blow drives the player back a little.
					Vec3 back = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize();
					player.push(back.x * 0.3, 0.0, back.z * 0.3);
					player.hurtMarked = true;
				}

				blowStarts(f, nextKind(f, phase, random), FOLLOW[phase], level, s);
			} else {
				// The last blow of the combo blocked: she staggers. The opening.
				act(f, OPEN_UP, STAGGER, OPENINGS[phase]);
				f.hits = 0;
				f.parried = 0;
			}

			return;
		}

		// Neither blocked nor dodged: it hurts.
		s.counters.merge("duel_hurt", 1, Integer::sum);
		player.hurtServer(level, level.damageSources().generic(), BLOWS[phase]);
		Vec3 push = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize().scale(0.6);
		player.push(push.x, 0.25, push.z);
		player.hurtMarked = true;
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 1.0F, 0.9F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.1, player.getZ(), 12, 0.15, 0.2, 0.15, 0.3);
		Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(BLOWS[phase]), java.util.List.of(f.position().add(corner)));
		f.combo = 0;
		f.burst = 0;
		act(f, RECOIL, 0, 10);
	}

	/** Phase 23: how long the player's guard has been up (ticks), or -1 when it is not. */
	static long guardTicks(ServerPlayer player) {
		long saber = SaberAbilities.guardTicks(player);

		if (saber >= 0) {
			return saber;
		}

		return player.isUsingItem() && SaberVariant.canHold(player.getUseItem()) ? player.getTicksUsingItem() : -1;
	}

	/** The leap: crouching (the warning), then through the air, and an overhead blow as she lands. */
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
			f.pattern = -1;
			act(f, WINDUP, OVERHEAD, 6);
			f.windup = 6;
		}
	}

	/** A push with the Force: the guard takes most of it. */
	private static void push(StorySession s, Figure f, ServerPlayer player, ServerLevel level, Vec3 corner) {
		Vec3 me = player.position().subtract(corner);
		Vec3 away = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize();
		boolean guard = guardTicks(player) >= 0;
		double strength = guard ? 0.5 : 1.4;
		player.push(away.x * strength, guard ? 0.1 : 0.35, away.z * strength);
		player.hurtMarked = true;

		if (!guard) {
			player.hurtServer(level, level.damageSources().generic(), 2.0F);
			Story.cue(s, Story.CUE_DUEL, "hurt", "", 2, java.util.List.of(f.position().add(corner)));
		}

		Vec3 at = f.position().add(corner).add(0.0, 1.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CLOUD, at.x + away.x, at.y, at.z + away.z, 12, 0.3, 0.3, 0.3, 0.05);
		s.hint("sabercraft.story.hint.push");
	}

	/**
	 * Phase 23: between the two phases: the blade lock. She comes in close and the blades lock,
	 * crackling, sparks spraying where they cross; the two press against each other (the player is
	 * held in place and pushed back now and then; each stroke pushes against her blade and ends it
	 * sooner); the camera closes in. Then a burst throws both apart, and the second phase begins:
	 * she is more aggressive from now on. Nobody is hurt in it.
	 */
	private static void lockStarts(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		f.health = PHASE_TWO;
		f.combo = 0;
		f.hits = 0;
		f.parried = 0;
		f.burst = 0;
		f.pushX = 0.0;
		f.pushZ = 0.0;
		act(f, LOCK, 0, LOCK_TICKS);
		f.react(Figure.LOCK, LOCK_TICKS);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, LOCK_TICKS + 4, 6, false, false));
		Vec3 contact = contact(f, me).add(corner);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("story.blade_lock"), SoundSource.HOSTILE, 1.2F, 1.0F);
		s.hear("shadow", "sabercraft.story.shout.shadow_lock");
		Story.cue(s, Story.CUE_DUEL, "lock", "", LOCK_TICKS, java.util.List.of(contact));
		locks++;
		s.dirty = true;
	}

	private static void lockTick(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 between = new Vec3(f.x - me.x, 0.0, f.z - me.z);
		Vec3 dir = between.lengthSqr() < 1.0E-4 ? f.look().scale(-1.0) : between.normalize();
		double distance = between.length();

		// She keeps right at the player's blade.
		if (distance > 1.55) {
			Stealth.step(f, me.add(dir.scale(1.35)), 4.0, level, corner);
		} else {
			f.speed = 0.0F;
		}

		f.turnTowards(me, 20.0F, true);
		f.headYaw = Crew.yaw(f.position(), me);
		f.react = Figure.LOCK;
		f.reactLength = LOCK_TICKS;
		f.reactTicks = Math.max(1, f.timer);
		int t = LOCK_TICKS - f.timer;
		Vec3 contact = contact(f, me).add(corner);

		if (t % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 6, 0.06, 0.06, 0.06, 0.3);
		}

		if (t > 0 && t % 14 == 0) {
			// The pressure: she leans in, the player gives half a step.
			player.push(-dir.x * 0.16, 0.0, -dir.z * 0.16);
			player.hurtMarked = true;
			level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 0.6F, 0.7F);
		}

		if (--f.timer <= 0) {
			lockEnds(s, f, player, me, level, corner, dir);
		}
	}

	/** The burst at the end of the lock: both thrown apart; the second phase. */
	private static void lockEnds(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, Vec3 dir) {
		Vec3 contact = contact(f, me).add(corner);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 40, 0.15, 0.15, 0.15, 0.6);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4E8), contact.x, contact.y, contact.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("story.clash_big"), SoundSource.HOSTILE, 1.4F, 0.7F);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 1.0F, 1.1F);
		player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
		player.push(-dir.x * 0.9, 0.3, -dir.z * 0.9);
		player.hurtMarked = true;
		f.react = 0;
		f.reactTicks = 0;
		Hits.push(f, me, 0.4);
		f.react(Figure.HEAVY, 14);
		act(f, GUARD, 0, 0);
		f.cooldown = 26;
		s.hear("shadow", "sabercraft.story.shout.shadow_phase2");
		s.hint("sabercraft.story.hint.duel_phase2");
		Story.cue(s, Story.CUE_DUEL, "phase", "", 2);
		Story.cue(s, Story.CUE_DUEL, "lock_end", "", f.id, java.util.List.of(contact));
		// The push and the leap come soon after.
		s.timer("push", 60);
		s.timer("leap", 110);
		s.dirty = true;
	}

	/**
	 * Phase 23: the end of the duel. The last stroke strikes the saber from her hand: it flies off
	 * spinning, lands a few steps away, sputters and dies (the client draws its flight and where it
	 * lies; the sparks and the sound as it lands come from {@link Chapter1}). She stares at her empty
	 * hand: she has lost. Then she runs (the film of her flight). Nobody kills her: she escapes.
	 */
	private static void disarm(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		act(f, ESCAPE, STAGGER, 0);
		f.disarmed = true;
		f.lit = false;
		f.hostile = false;
		f.mode = Figure.HELD;
		f.speed = 0.0F;
		f.combo = 0;
		f.target = null;
		f.react(Figure.DISARMED, DISARMED_TICKS);
		Hits.push(f, me, 0.3);
		Vec3 hand = f.position().add(f.look().scale(0.35)).add(0.0, 1.15, 0.0);
		Vec3 land = landing(f, me, level, corner);
		s.dropped = land;
		Story.cue(s, Story.CUE_DUEL, "disarm", "", f.id, java.util.List.of(hand.add(corner), land.add(corner)));
		Vec3 at = hand.add(corner);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.clash_big"), SoundSource.HOSTILE, 1.3F, 0.75F);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.retract"), SoundSource.HOSTILE, 0.8F, 1.3F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, 0.1, 0.1, 0.1, 0.5);
		s.timer("saber_lands", SABER_FLIGHT);
		s.fighting = false;
		s.focusTicks = 0;
		s.unset("tmp.duel");
		player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);

		// The troopers still about fall back (the fight is over).
		for (Figure figure : s.figures) {
			if (figure != f && figure.hostile && figure.mode != Figure.DOWN && !figure.saber) {
				figure.mode = Figure.DOWN;
			}
		}

		disarms++;
		Story.cue(s, Story.CUE_DUEL, "over", "", f.id);
		Chapter1.duelEnds(s, f);
	}

	/** Phase 23: how long the struck saber flies (ticks) before it lands (as the client's flight). */
	static final int SABER_FLIGHT = 16;

	/** Phase 23: where her saber lands: a few steps to her side, away from the player, on the floor; else behind her. */
	private static Vec3 landing(Figure f, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 from = new Vec3(f.x - me.x, 0.0, f.z - me.z);
		Vec3 dir = from.lengthSqr() < 1.0E-4 ? f.look().scale(-1.0) : from.normalize();
		Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
		Vec3[] tries = {side.scale(3.6).add(dir.scale(1.2)), side.scale(-3.6).add(dir.scale(1.2)), side.scale(4.5), side.scale(-4.5), dir.scale(3.2), side.scale(2.4)};

		for (Vec3 off : tries) {
			double x = f.x + off.x;
			double z = f.z + off.z;
			Double y = Paths.stand(level, corner, x, f.y + 0.5, z, 0.4);

			if (y != null && Math.abs(y - f.y) < 1.2 && Stealth.clear(level, corner.add(f.x, f.y + 1.1, f.z), corner.add(x, y + 0.3, z))) {
				return new Vec3(x, y, z);
			}
		}

		return new Vec3(f.x + dir.x * 1.5, f.y, f.z + dir.z * 1.5);
	}

	/** Phase 23: whether that fighter's saber was struck from the hand. */
	static boolean disarmed(Figure f) {
		return f.disarmed;
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

	static int[] countsForTest() {
		return new int[] {clashes, perfects, dodges, locks, disarms};
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
