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
	/**
	 * Ticks between a trooper's shots: at least, and a random part. Phase 26: about half as long as
	 * before (40 + up to 40: they hardly shot), and now and then a second shot right after the
	 * first ({@link #DOUBLE_CHANCE}, {@link #DOUBLE_GAP}). Fair stays fair: the rifle comes up
	 * before each shot (the wind-up the client shows), and two troopers never fire within
	 * {@link #VOLLEY_GAP} ticks of each other (one can turn each bolt aside, one after the other).
	 */
	static final int SHOT_MIN = 22;
	static final int SHOT_RANDOM = 22;
	static final float DOUBLE_CHANCE = 0.3F;
	static final int DOUBLE_GAP = 7;
	static final int VOLLEY_GAP = 7;
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
	/** Phase 25: her saber staff thrown (the second phase): drawn back, out at the player, back to her hand. */
	static final int THROW = 9;
	/** How it looks ({@link Figure#attack}), sent to the client in the top bits of the wind-up byte. */
	static final int OVERHEAD = 1;
	static final int SWEEP = 2;
	static final int THRUST = 3;
	static final int CROUCH = 4;
	static final int HAND = 5;
	static final int STAGGER = 6;
	static final int SPIN = 7;
	/**
	 * Phase 29: her slam ({@link Figure#action}; it looks like an overhead blow raised high, see
	 * {@link #slamStarts}).
	 */
	static final int SLAM = 11;
	/** Phase 29: her moves as remembered ({@link Figure#moves}): besides the actions, a lunge and a shove. */
	static final int MOVE_LUNGE = 12;
	static final int MOVE_SHOVE = 13;
	static final int MOVE_TURN = 14;

	/**
	 * Phase 23: per phase (1, 2): the wind-up of a combo's first blow and of the blows after it,
	 * what a blow takes of the player, how long an opening lasts, how long she circles between
	 * attacks, and how many strokes into her guard before she answers.
	 */
	static final int[] WINDUPS = {0, 16, 14};
	/** (Phase 29: a tick quicker, 13 and 11 before; and a tick or two of chance in it: no beat to parry by.) */
	static final int[] FOLLOW = {0, 12, 10};
	/** (Phase 29: harder, 3.0 and 3.5 before: a blow not blocked or dodged is felt.) */
	static final float[] BLOWS = {0.0F, 3.5F, 4.0F};
	/** (Phase 29: a little shorter, 30 and 24 before; still long enough for two or three strokes.) */
	static final int[] OPENINGS = {0, 28, 22};
	/** (Phase 27: the first phase's pause shorter, 34 before: she presses on sooner. Phase 29: shorter still, 28 and 18 before.) */
	static final int[] PAUSES = {0, 22, 14};
	static final int[] PARRIES = {0, 3, 2};
	/**
	 * Phase 23: her combos, per phase: the kinds of blow one after the other (the second phase's are
	 * longer and mixed). Phase 26: a few more of each (a three-blow combo in the first phase too).
	 */
	static final int[][][] PATTERNS = {{}, {{OVERHEAD}, {SWEEP}, {THRUST}, {OVERHEAD, SWEEP}, {SWEEP, THRUST}, {SWEEP, OVERHEAD}, {THRUST, OVERHEAD},
			{OVERHEAD, SWEEP, THRUST},
			// Phase 27: the first phase harder and more varied: longer and mixed combos too.
			{THRUST, SWEEP, OVERHEAD}, {SWEEP, THRUST, SWEEP}, {OVERHEAD, OVERHEAD, THRUST}},
			{{SWEEP, SWEEP, OVERHEAD}, {THRUST, SWEEP, THRUST}, {OVERHEAD, THRUST}, {SWEEP, OVERHEAD, SWEEP}, {OVERHEAD, SWEEP, THRUST},
					// Phase 25: with the staff she spins too (both blades round her: blocked or dodged as any blow).
					{SPIN, THRUST}, {SWEEP, SPIN}, {OVERHEAD, SPIN, SWEEP}}};
	/**
	 * Phase 26: the last quarter of her strength: she fights harder (shorter pauses, quicker
	 * follow-up blows, the ring's rush more often, longer combos that end in a spin), still with an
	 * opening after each combo.
	 */
	static final float FIERCE = 0.25F;
	static final int[][] FIERCE_PATTERNS = {{SWEEP, OVERHEAD, SPIN}, {THRUST, SWEEP, THRUST, SPIN}, {OVERHEAD, THRUST, SWEEP}, {SWEEP, SPIN, OVERHEAD},
			{THRUST, THRUST, SWEEP}};
	/**
	 * Phase 26: the lunge of the first phase: from this far to this far she drives in with a thrust
	 * (the wind-up a little longer: to be seen coming), at this speed (blocks a second), at most
	 * this often (ticks).
	 */
	static final double LUNGE_FROM = 3.4;
	static final double LUNGE_TO = 5.8;
	static final double LUNGE_SPEED = 6.5;
	static final int LUNGE_EVERY = 120;
	/**
	 * Phase 26: the ring's rush (the second phase): the ring with both blades spins up round the grip
	 * (the warning: its whirr rising, the blades blurring into a disc), then she drives at the
	 * player behind the spinning disc. Blocked it throws the player back, dodged it goes past;
	 * either way it ends with her off balance as the ring winds down: an opening, longer than the
	 * others. A stroke into it while it spins up meets the blades (a clash).
	 */
	static final int RUSH = 10;
	static final int RUSH_WIND = 18;
	static final int RUSH_TICKS = 24;
	static final double RUSH_SPEED = 5.5;
	static final double RUSH_REACH = 1.9;
	static final double RUSH_FROM = 2.6;
	static final double RUSH_TO = 9.0;
	static final int RUSH_EVERY = 210;
	static final int RUSH_EVERY_FIERCE = 150;
	/**
	 * Phase 27: the ring's rush in the first phase too (one blade whirling round the grip), rarer,
	 * and only once she has lost some strength ({@link #RING_FROM}): the fight begins with plain
	 * blade work and grows. Her push likewise, rarer than in the second phase.
	 */
	static final int RUSH_EVERY_FIRST = 300;
	static final float RING_FROM = 0.85F;
	static final int PUSH_EVERY_FIRST = 340;
	/**
	 * Phase 27: her strength (times Phase 26's): the light and heavy strokes take this much less of
	 * her (on Normal; the difficulty scales it further).
	 */
	static final float STRENGTH = 1.15F;
	/** Her push's damage to one not on guard (Phase 27: times her power by the difficulty; Phase 29: 2.0 before). */
	static final float PUSH_DAMAGE = 2.5F;
	static final int RUSH_OPENING = 12;
	/** Phase 25: a spin's wind-up is longer (the staff twirls round her: to be seen coming), and it reaches a little further. */
	static final int SPIN_WINDUP = 20;
	static final double SPIN_REACH = 3.5;
	/**
	 * Phase 25: the throw: how long she draws the staff back (the warning), how often at most, from
	 * how far to how far she throws, what it takes when it hits, how near it must come to hit, and
	 * how far past the player it flies.
	 */
	static final int THROW_WIND = 22;
	static final int THROW_EVERY = 260;
	static final double THROW_FROM = 4.0;
	static final double THROW_TO = 12.0;
	/** (Phase 29: 3.0 before.) */
	static final float THROW_DAMAGE = 3.5F;
	static final double THROW_HIT = 0.85;
	static final double THROW_PAST = 2.5;
	/**
	 * Phase 28: the throw is a moment of the fight, not her habit: once or twice a fight (in the
	 * second phase). When it is due she backs off while circling to throw from there
	 * ({@code THROW_BACK_TO}), and throws from a little nearer too if the player follows her
	 * ({@code THROW_NEAR}); in her last quarter the second one comes soon ({@code THROW_FIERCE}).
	 */
	static final int THROWS_MAX = 3;
	static final double THROW_BACK_TO = 5.2;
	static final double THROW_NEAR = 3.3;
	static final int THROW_FIERCE = 90;
	/**
	 * Phase 28: her ring, once set going in the second phase, keeps turning at least this long; then
	 * it may stop at a calm moment (circling on guard), not in her last quarter.
	 */
	static final int RING_MIN = 400;
	/**
	 * Phase 28: the strain of her blows on the player's guard (shares of the most stamina, see
	 * {@link SaberAbilities#strainGuard}): a blow of the first phase, of the second, the spin, the
	 * ring's rush, the thrown staff, a perfect parry. A single block always holds; a guard kept up
	 * through combo after combo (no stamina comes back while it is up) shakes ({@code UNSTABLE}: less
	 * than that share left) and breaks. By the difficulty ({@link StoryDifficulty#bossStrain}).
	 */
	static final float STRAIN_BLOW = 0.14F;
	static final float STRAIN_BLOW_2 = 0.17F;
	static final float STRAIN_SPIN = 0.24F;
	static final float STRAIN_RUSH = 0.26F;
	static final float STRAIN_THROW = 0.12F;
	static final float STRAIN_PERFECT = 0.04F;
	static final float UNSTABLE = 0.35F;
	/** Phase 28: a blow that breaks the guard still comes through, weakened. */
	static final float BREAK_DAMAGE = 0.6F;
	/** For the tests: an opening as long as the first phase's. */
	static final int OPEN = 30;
	static final double BLOW_REACH = 3.2;
	static final double THRUST_REACH = 3.7;
	/** Strokes that hit her in one opening, at most. */
	static final int HITS_PER_OPENING = 3;
	/** Her answer to strokes into her guard: a quick thrust (still to be seen and blocked). */
	static final int RIPOSTE = 9;
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
	static final int PUSH_EVERY = 200;
	static final int LEAP_EVERY = 160;
	static final int RECOIL_TICKS = 14;
	/**
	 * What a light and a heavy stroke take of her (she has 1); the second phase begins at half.
	 * Phase 26: about a quarter more strength (0.065 and 0.1 before): the fight lasts a little
	 * longer, her blows hurt as much as before.
	 */
	static final float HIT = 0.052F;
	static final float HIT_HEAVY = 0.08F;
	static final float PHASE_TWO = 0.5F;
	/** Phase 23: the blade lock between the phases (ticks), and how much each stroke of the player during it shortens it. */
	static final int LOCK_TICKS = 70;
	static final int LOCK_PRESS = 5;
	/**
	 * Phase 24: the blade lock is a scene now (the client's film {@code boss_mid}): how long it is,
	 * when the burst throws the two apart, when her second blade comes out, when the player is
	 * turned towards her again (the camera goes back into their eyes).
	 */
	static final int BOSS_FILM = 150;
	static final int BOSS_LOCK = 86;
	static final int BOSS_REVEAL = 104;
	static final int BOSS_TURN = 128;
	static final String BOSS_SCENE = "tmp.boss_mid";
	/** Phase 24: the scene was played in this fight (it is never played twice in one). */
	static final String BOSS_DONE = "tmp.boss_mid_done";
	/** Phase 23: the end: how long she stares at her empty hand (ticks) before she runs. */
	static final int DISARMED_TICKS = 92;
	/**
	 * Phase 29: the player behind her (Phiro: one jumped behind her and struck again and again while
	 * she did nothing). Her head no longer turns to the player at once each tick (that made every
	 * stroke one from the front, while her body, drawn as it turned slowly, still showed her back):
	 * she turns as fast as one can ({@link #TURN}; towards one at her side or behind her faster,
	 * {@link #TURN_AWARE}, once she noticed: after {@link #NOTICE} ticks by the difficulty), her body
	 * close behind her head. A stroke from behind hits once; the next within {@link #REAR_MEMORY}
	 * ticks (by the difficulty) she turns aside, whirling round, and strikes back at once (a
	 * turn-about blow that reaches behind her, {@link #TURNABOUT}). Standing behind her she turns
	 * round, steps round or strikes back; standing in her or on her one is pushed out
	 * ({@link #BODY}). Her blows reach only before her and to the sides ({@link #arc}): behind her
	 * one is safe from a plain blow, but not for long.
	 */
	static final float BEHIND_ANGLE = 100.0F;
	static final double BEHIND_NEAR = 4.5;
	static final int[] NOTICE = {14, 9, 6};
	static final int[] REAR_MEMORY = {50, 70, 90};
	static final float TURN = 16.0F;
	static final float TURN_AWARE = 26.0F;
	static final float TURN_HELD = 12.0F;
	static final int TURNABOUT = 9;
	static final float TURNABOUT_TURN = 24.0F;
	static final double BODY = 0.75;
	/** Phase 29: a stroke from behind: she flinches only this long (ticks) before she whirls round. */
	static final int REAR_FLINCH = 5;
	/** Phase 29: how far (degrees) she whirls round at once as she turns a stroke from behind aside (the rest in her blow's wind-up). */
	static final float REAR_WHIRL = 45.0F;
	/**
	 * Phase 29: close pressure: one this near her (blocks) is shoved away with the Force, a quick
	 * hand (to be seen: {@link #SHOVE_TICKS}), at most this often; it hurts a little, costs the
	 * guard a little stamina.
	 */
	static final double SHOVE_NEAR = 1.5;
	static final int SHOVE_TICKS = 8;
	static final int SHOVE_EVERY = 70;
	static final float SHOVE_DAMAGE = 1.5F;
	static final float STRAIN_SHOVE = 0.1F;
	/** Phase 29: her push from afar on the guard costs stamina too. */
	static final float STRAIN_PUSH = 0.16F;
	/**
	 * Phase 29: the slam, her strong attack (from the last fifth of her first phase on): she raises
	 * the blade high in both hands and crouches, the floor before her glows where it will strike
	 * (the warning, {@link #SLAM_WIND} ticks at her tempo, the way locked {@link #SLAM_LOCK} ticks
	 * before it falls), then the blade goes down into the floor and a shock wave runs out before
	 * her ({@link #SLAM_REACH} blocks, {@link #SLAM_ARC} degrees to each side). It hurts more than
	 * any blow ({@link #SLAM_DAMAGE}); a step aside out of its way, a dash, or a jump over the wave
	 * escapes it; the guard holds it at a high cost of stamina. Afterwards her blade is stuck a moment
	 * (a longer opening).
	 */
	static final int SLAM_WIND = 24;
	// (Locked 12 ticks before it falls, 40 degrees to each side: a real step aside, walking, gets out
	// of its way in time; at first 8 ticks and 55 degrees, which only a dash or a jump escaped.)
	static final int SLAM_LOCK = 12;
	static final double SLAM_REACH = 3.9;
	static final float SLAM_ARC = 40.0F;
	static final float SLAM_DAMAGE = 6.0F;
	static final float STRAIN_SLAM = 0.42F;
	static final int SLAM_EVERY = 200;
	static final float SLAM_FROM = 0.8F;
	static final int SLAM_OPENING = 10;
	/** Phase 29: strokes that hit her while her staff is away: at most this many, then she dodges. */
	static final int FLIGHT_HITS = 2;
	/** Phase 29: a guard held this long (ticks) when she chooses: she goes for what breaks it (the push, the slam). */
	static final int TURTLE = 20;
	/** Phase 29: a guard raised again this soon after it came down parries nothing perfectly (no parrying by mashing the key). */
	// (6, not 12: the guard lowered after a blow and raised in time for her next blow of the same
	// combo still parries it perfectly; only raising it again at once, mashing, does not.)
	static final int RAISE_LOCK = 6;
	/**
	 * Phase 29: the scene at half her strength never starts with the player in the air (Phiro: frozen
	 * in mid-air): she steps back and waits for them to land (nothing hurts meanwhile), at most this
	 * long (ticks); then they are set down on the floor under them.
	 */
	static final int SCENE_WAIT = 40;
	static final String SCENE_PENDING = "tmp.boss_pending";

	/**
	 * Phase 29: the stricter guard in her fight, by the difficulty (Easy, Normal, Hard; see
	 * {@link SaberAbilities.StrictGuard}): holding the guard costs stamina all the time, blows in a
	 * row cost more each, stamina comes back later and slower, a broken guard leaves one worn out.
	 */
	static final SaberAbilities.StrictGuard[] STRICT = {
			new SaberAbilities.StrictGuard(0.35F, 30, 0.2F, 1.6F, 18, 20, 0.75F, 0.6F, 0.4F, 1.3F, 24),
			new SaberAbilities.StrictGuard(0.55F, 36, 0.3F, 2.0F, 24, 32, 0.6F, 0.5F, 0.5F, 1.5F, 32),
			new SaberAbilities.StrictGuard(0.75F, 42, 0.4F, 2.4F, 30, 44, 0.5F, 0.45F, 0.55F, 1.7F, 40)};

	static SaberAbilities.StrictGuard strictGuard(StoryDifficulty difficulty) {
		return STRICT[Math.min(STRICT.length - 1, difficulty.ordinal())];
	}

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
	/** Phase 25: throws begun, hits (out, back), blocked, dodged. */
	private static int throwsStarted;
	private static int throwHits;
	private static int throwBlocks;
	private static int throwDodges;
	private static int spins;

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
					float body = figure.yaw;
					duel(s, figure, player, me, level, corner);
					keepHead(s, figure, body);
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
				if (s.time() - s.lastVolley < VOLLEY_GAP) {
					// Phase 26: another just fired: a moment later (the rifle stays up).
					figure.cooldown = VOLLEY_GAP - (int) (s.time() - s.lastVolley) + 1;
				} else {
					shoot(figure, player, level, corner);
					s.lastVolley = s.time();
					// Phase 26: now and then a second shot right after the first (never a third).
					boolean again = figure.burst == 0 && player.getRandom().nextFloat() < DOUBLE_CHANCE;
					figure.burst = again ? 1 : 0;
					// Phase 27: by the difficulty (more often on Hard, less on Easy).
					figure.cooldown = again ? DOUBLE_GAP : s.difficulty().ticks(SHOT_MIN + player.getRandom().nextInt(SHOT_RANDOM), s.difficulty().cooldown);
				}
			}

			// The rifle comes up the last ten ticks before a shot (and stays up for a second one).
			figure.windup = figure.burst > 0 ? 10 : figure.cooldown <= 10 ? 10 - figure.cooldown + 1 : 0;
		} else {
			// Out of sight: they come to where the player is (in a fight everyone knows), the
			// way round (Phase 23; before they walked straight at the wall between).
			if (!Stealth.go(figure, Combat.apart(s, figure, me, level, corner), Stealth.WALK * 1.4, level, corner, s.time())) {
				figure.speed = 0.0F;
			}

			figure.windup = 0;
			figure.burst = 0;
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

		// (Phase 26: nor in a scene: the blade lock's at half her strength holds the player still.)
		if (!s.fighting || level == null || s.here == null || !SaberVariant.canHold(player.getMainHandItem()) || s.timer("stroke") || s.has("tmp.film")) {
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

		// Phase 29: she waits for the player to land before the scene at half her strength: nothing counts meanwhile.
		if (s.has(SCENE_PENDING)) {
			return;
		}

		switch (f.action) {
			case SLAM -> {
				if (f.windup > 0) {
					// Phase 29: in her slam she is set on it: the stroke is turned aside, she does not flinch.
					turnedAside(s, f, player, me, level, corner);
					return;
				}
			}
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
			case THROW -> {
				if (f.windup > 0) {
					// Into the staff she draws back: the blades meet.
					f.react = 0;
					f.reactTicks = 0;
					s.timer("throw", THROW_EVERY / 2);
					clash(s, f, player, me, level, corner);
				} else if (++f.flightHits > FLIGHT_HITS) {
					// Phase 29: her staff is away, but she is not a target to strike at at will: she dodges.
					flightEvades(s, f, me, level, corner);
				} else {
					// Her staff is away: she has nothing to parry with.
					bossHit(s, f, player, me, level, corner, at, heavy, false);
				}

				return;
			}
			case LEAP -> {
				if (f.windup <= 0) {
					// Into her leap: the blades meet in the air.
					clash(s, f, player, me, level, corner);
					return;
				}
			}
			case RUSH -> {
				Vec3 toMe = new Vec3(eye.x - f.x, 0.0, eye.z - f.z).normalize();

				if (f.windup > 0) {
					// Phase 26: into the ring as it spins up: the blades meet, her rush is gone.
					f.react = 0;
					f.reactTicks = 0;
					clash(s, f, player, me, level, corner);
				} else if (toMe.dot(f.look()) < -0.35) {
					// From behind, while she drives on: it hits (Phase 29: once; the next soon after she turns aside).
					fromBehind(s, f, player, me, level, corner, at, heavy);
				} else {
					// Into the spinning disc: turned aside, and one is thrown back a little.
					discTurns(s, f, player, me, level, corner);
				}

				return;
			}
			default -> {
			}
		}

		Vec3 toPlayer = new Vec3(eye.x - f.x, 0.0, eye.z - f.z).normalize();
		boolean behind = toPlayer.dot(f.look()) < -0.35;

		if (f.action == OPEN_UP) {
			bossHit(s, f, player, me, level, corner, at, heavy, false);
			return;
		}

		if (behind && f.action != LEAP) {
			fromBehind(s, f, player, me, level, corner, at, heavy);
			return;
		}

		parried(s, f, player, me, level, corner);
	}

	/**
	 * Phase 29: a stroke from behind her. The first hits (she flinches only a moment, then whirls
	 * round with a blow that reaches behind her); the next soon after ({@link #REAR_MEMORY}) she
	 * turns aside, whirling round, and strikes back at once. Jumping behind her and striking again
	 * and again does not work any more.
	 */
	private static void fromBehind(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, Vec3 at, boolean heavy) {
		long now = s.time();

		if (f.rearHits > 0 && now - f.rearAt <= REAR_MEMORY[Math.min(2, s.difficulty().ordinal())]) {
			rearParry(s, f, player, me, level, corner);
			return;
		}

		f.rearHits = 1;
		f.rearAt = now;
		rearHits++;
		s.counters.merge("rear_hits", 1, Integer::sum);
		bossHit(s, f, player, me, level, corner, at, heavy, true);
	}

	/** Phase 29: she turns a stroke from behind aside, whirling round (a good part of the way at once), and strikes back. */
	private static void rearParry(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 contact = contact(f, me).add(corner);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 14, 0.08, 0.08, 0.08, 0.4);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.05F, 0.8F + player.getRandom().nextFloat() * 0.15F);
		Story.cue(s, Story.CUE_DUEL, "parried", "", f.id, java.util.List.of(contact));
		s.counters.merge("rear_parried", 1, Integer::sum);
		rearParries++;
		// The blade swept round behind her: she whirls a good part of the way with it (the rest in
		// her turn-about blow's wind-up, quickly but not in one jump).
		float wanted = Crew.yaw(f.position(), me);
		f.headYaw += Mth.clamp(Mth.wrapDegrees(wanted - f.headYaw), -REAR_WHIRL, REAR_WHIRL);
		float off = Mth.wrapDegrees(f.headYaw - f.yaw);

		if (Math.abs(off) > 25.0F) {
			f.yaw = f.headYaw - Math.signum(off) * 25.0F;
		}

		f.rearAt = s.time();
		f.react = 0;
		f.reactTicks = 0;
		turnaboutBlow(s, f, level, TURNABOUT - 2);
	}

	/** Phase 29: a blow that turns her round with it (towards one behind her): a sweep that reaches round behind her. */
	private static void turnaboutBlow(StorySession s, Figure f, ServerLevel level, int ticks) {
		f.parried = 0;
		f.burst = 0;
		f.combo = 1;
		f.pattern = -1;
		f.behindTicks = 0;
		blowStarts(f, SWEEP, scaled(f, ticks), level, s);
		f.turnabout = true;
		turnabouts++;
		remember(f, MOVE_TURN);
	}

	/** Phase 29: a stroke into her slam's wind-up: turned aside, blade on blade; she goes on with it. */
	private static void turnedAside(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 contact = contact(f, me).add(corner);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 8, 0.08, 0.08, 0.08, 0.3);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 0.9F, 0.7F);
		Story.cue(s, Story.CUE_DUEL, "parried", "", f.id, java.util.List.of(contact));
		s.counters.merge("parried", 1, Integer::sum);
	}

	/** Phase 29: strokes while her staff is away: after a few she dodges back out of reach. */
	private static void flightEvades(StorySession s, Figure f, Vec3 me, ServerLevel level, Vec3 corner) {
		f.react(Figure.DODGE, 8);
		Hits.push(f, me, 0.38);
		Vec3 at = f.position().add(corner).add(0.0, 1.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.6F, 1.3F);
		s.counters.merge("flight_evaded", 1, Integer::sum);
		flightEvasions++;
	}

	/** Phase 23: a stroke hits her: she reels (a heavy one throws her back), the phases and the end. */
	private static void bossHit(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, Vec3 at, boolean heavy, boolean rear) {
		int before = phase(f);
		// Phase 27: she is stronger (more on Hard, less on Easy).
		f.health = Math.max(0.0F, f.health - (heavy ? HIT_HEAVY : HIT) / (STRENGTH * s.difficulty().bossHealth));
		f.hurtAt = s.time();
		f.parried = 0;
		f.burst = 0;
		f.hits++;
		s.counters.merge("duel_hits", 1, Integer::sum);
		s.counters.merge(heavy ? "duel_heavy" : "duel_light", 1, Integer::sum);
		boolean flying = f.action == THROW && f.windup <= 0 && f.throwFrom != null;

		if (flying) {
			// (Not finished while her staff is out: it comes back to her hand first.)
			f.health = Math.max(0.01F, f.health);
		} else if (rear) {
			// Phase 29: struck from behind she flinches only a moment (a heavy stroke throws her no longer): then she whirls round.
			f.react(Figure.FLINCH, REAR_FLINCH);
		} else {
			f.react(heavy ? Figure.HEAVY : Figure.FLINCH, heavy ? 14 : 8);
		}

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

		// Phase 24: once per fight (a mark of this fight, gone with a checkpoint's restart).
		// Phase 29: never with the player in the air or in a dash: she waits for them to land.
		if (before == 1 && f.health <= PHASE_TWO && !s.has(BOSS_DONE)) {
			if (sceneReady(player)) {
				lockStarts(s, f, player, me, level, corner);
			} else {
				sceneWaits(s, f);
			}

			return;
		}

		// Phase 26: the last quarter: the ring flares and whirls once round the grip, and she
		// fights harder from now on (seen and heard; no words).
		if (fierce(f) && !s.has("tmp.boss_fierce")) {
			s.set("tmp.boss_fierce");
			Vec3 ring = f.position().add(corner).add(0.0, 1.3, 0.0);
			level.playSound(null, ring.x, ring.y, ring.z, ShipInteriors.sound("story.ring_spin"), SoundSource.HOSTILE, 1.1F, 0.85F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ring.x, ring.y, ring.z, 24, 0.3, 0.3, 0.3, 0.4);
			Story.cue(s, Story.CUE_DUEL, "fierce", "", f.id);
			s.timer("rush", Math.min(s.timers.getOrDefault("rush", 0), 60));
			// Phase 28: her ring turns from now on, and her second throw (if any is left) comes soon.
			ringGoes(s, f);

			if (f.thrown < THROWS_MAX) {
				s.timer("throw", Math.min(s.timers.getOrDefault("throw", 0), THROW_FIERCE));
			}
		}

		if (flying) {
			// She waits for her staff (the throw goes on).
			return;
		}

		if (rear) {
			// Phase 29: she whirls round with a blow that reaches behind her (after the short flinch).
			f.hits = 0;
			turnaboutBlow(s, f, level, TURNABOUT);
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
		// (Phase 26: no words on the screen for what she does: her thrust, its sound, and the
		// blades are what one goes by.)
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
		// Phase 28: a new fight: no throws yet, the ring still.
		shadow.thrown = 0;
		shadow.ringOn = false;
		// Phase 29: nothing remembered of a fight before (a checkpoint's restart), the guard's rules afresh.
		shadow.rearHits = 0;
		shadow.rearAt = -1000L;
		shadow.behindTicks = 0;
		shadow.turnabout = false;
		java.util.Arrays.fill(shadow.moves, 0);
		shadow.slamDir = null;
		shadow.shove = false;
		shadow.flightHits = 0;
		shadow.guardSeenUp = false;
		shadow.raiseSpam = false;
		s.unset(SCENE_PENDING);
		s.timers.remove("boss_pending");

		if (s.player() != null) {
			SaberAbilities.strictGuard(s.player(), null);
		}
		s.timers.remove("throw_back");
		s.timers.remove("guard_broken");
		act(shadow, GUARD, 0, 0);
		s.fighting = true;
		s.focus = 1.0F;
		s.focusTicks = 0;
		s.set("tmp.duel");
		s.timer("push", PUSH_EVERY);
		s.timer("leap", LEAP_EVERY);
		Chapter1.music(s, s.mission, s.step().id());
		Story.cue(s, Story.CUE_SOUND, "", "saber.ignite", shadow.id);
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
		f.turnabout = false;
	}

	private static void duel(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		int phase = phase(f);
		// Phase 27: how she fights by the story's difficulty.
		StoryDifficulty difficulty = s.difficulty();
		f.tempo = difficulty.bossTempo;
		f.openness = difficulty.bossOpening;
		f.power = difficulty.bossDamage;
		double distance = Math.hypot(me.x - f.x, me.z - f.z);
		// Phase 29: the stricter guard in her fight (renewed each tick: it lapses by itself when the fight ends).
		SaberAbilities.strictGuard(player, strictGuard(difficulty));
		raiseWatch(s, f, player);

		// Phase 28: a block held with the use key gets no stamina back while it is up (as the guard).
		if (SaberAbilities.guardTicks(player) < 0 && player.isUsingItem() && SaberVariant.canHold(player.getUseItem())) {
			SaberAbilities.holdStamina(player);
		}

		// Phase 29: the scene at half her strength waits until the player stands on the floor.
		if (s.has(SCENE_PENDING)) {
			scenePending(s, f, player, me, level, corner, distance);
			return;
		}

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

		// Phase 29: nobody stands in her (or on her): pushed out, as against anyone.
		bodyContact(f, player, me, distance);

		// Phase 23: thrown back by a hit, turning a stroke aside, reeling from a perfect parry.
		boolean held = Hits.tick(f, level, corner);

		if (f.action == CLASHED) {
			aware(s, f, me, distance, 10.0F);

			if (--f.timer <= 0) {
				if (f.burst >= 2) {
					// Two clashes in a row: she follows up at once.
					riposte(s, f, level);
				} else {
					act(f, GUARD, 0, 0);
					f.cooldown = pause(f, phase) / 2;
				}
			}

			return;
		}

		if (held) {
			// (Phase 29: reeling she still turns towards one at her side or behind her, faster than before.)
			aware(s, f, me, distance, 6.0F);

			if (f.action == WINDUP && !f.turnabout || f.action == RUSH) {
				act(f, GUARD, 0, 0);
				f.lunge = false;
				f.cooldown = pause(f, phase);
			}

			return;
		}

		// (Phase 26: driving on in the ring's rush she turns slowly, see rushTick; Phase 29: in her
		// slam and her throw she turns by their own rules; her head no longer snaps to the player.)
		if ((f.action != LEAP || f.windup > 0) && (f.action != RUSH || f.windup > 0) && f.action != SLAM && f.action != THROW) {
			aware(s, f, me, distance, f.action == WINDUP ? f.turnabout ? TURNABOUT_TURN : 9.0F : TURN);
		}

		switch (f.action) {
			case WINDUP -> {
				f.speed = 0.0F;

				// A thrust carries her in a little (Phase 26: a lunge from further off: fast).
				if (f.attack == THRUST && distance > 2.2) {
					Stealth.step(f, me, f.lunge ? LUNGE_SPEED : 2.5, level, corner);
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
					f.cooldown = pause(f, phase) / 2;
				}
			}
			case LEAP -> leap(s, f, player, me, level, corner, phase);
			case THROW -> throwTick(s, f, player, me, level, corner);
			case RUSH -> rushTick(s, f, player, me, level, corner);
			case SLAM -> slamTick(s, f, player, me, level, corner, phase);
			case PUSH -> {
				f.speed = 0.0F;

				if (--f.windup <= 0) {
					push(s, f, player, level, corner);
					act(f, GUARD, 0, 0);
					f.cooldown = f.shove ? 12 : 20;
					f.shove = false;
				}
			}
			default -> guard(s, f, player, me, level, corner, phase, distance);
		}
	}

	/**
	 * Phase 29: she turns towards the player as one does: on guard quickly ({@code base} degrees a
	 * tick), towards one at her side or behind her faster still once she noticed them there (after
	 * {@link #NOTICE} ticks by the difficulty), her body close behind her head. Counts how long the
	 * player has been behind her.
	 */
	private static void aware(StorySession s, Figure f, Vec3 me, double distance, float base) {
		float off = Math.abs(Mth.wrapDegrees(Crew.yaw(f.position(), me) - f.headYaw));

		if (off > BEHIND_ANGLE && distance < BEHIND_NEAR) {
			f.behindTicks++;
		} else {
			f.behindTicks = Math.max(0, f.behindTicks - 2);
		}

		float step = base;

		if (off > 60.0F) {
			step = Math.max(base, f.behindTicks >= notice(s) ? TURN_AWARE : Math.max(TURN_HELD, base * 1.3F));
		}

		turn(f, me, step);
		f.aimHead = f.headYaw;
		f.aimAt = s.time();
	}

	/** Phase 29: how far her body turns towards where she walks in a tick at most. */
	static final float BODY_STEP = 20.0F;

	/**
	 * Phase 29: her head stays where her own turning put it this tick ({@link #aware}): a step of
	 * hers (circling, backing off, closing in) no longer swings her head round to where she walks
	 * (that made her snap round and lose sight of the player). Her body turns towards her way only
	 * so fast, and stays close behind her head.
	 */
	private static void keepHead(StorySession s, Figure f, float bodyBefore) {
		if (f.aimAt != s.time()) {
			return;
		}

		f.headYaw = f.aimHead;
		float body = bodyBefore + Mth.clamp(Mth.wrapDegrees(f.yaw - bodyBefore), -BODY_STEP, BODY_STEP);
		float off = Mth.wrapDegrees(f.headYaw - body);

		if (Math.abs(off) > 35.0F) {
			body = f.headYaw - Math.signum(off) * 35.0F;
		}

		f.yaw = body;
	}

	/** Phase 29: after how many ticks behind her she notices the player there (by the difficulty). */
	static int notice(StorySession s) {
		return NOTICE[Math.min(NOTICE.length - 1, s.difficulty().ordinal())];
	}

	/** Phase 29: turns her towards a point, at most {@code step} degrees a tick, her body at most 35 degrees behind her head. */
	static void turn(Figure f, Vec3 to, float step) {
		f.turnTowards(to, step, true);
		float off = Mth.wrapDegrees(f.headYaw - f.yaw);

		if (Math.abs(off) > 35.0F) {
			f.yaw = f.headYaw - Math.signum(off) * 35.0F;
		}
	}

	/**
	 * Phase 29: the player's guard as she sees it each tick: raised again right after it came down
	 * ({@link #RAISE_LOCK}) it parries nothing perfectly.
	 */
	private static void raiseWatch(StorySession s, Figure f, ServerPlayer player) {
		boolean up = guardTicks(player) >= 0;
		long now = s.time();

		if (up && !f.guardSeenUp) {
			f.raiseSpam = now - f.guardDownAt < RAISE_LOCK;
		}

		if (!up && f.guardSeenUp) {
			f.guardDownAt = now;
		}

		f.guardSeenUp = up;
	}

	/**
	 * Phase 29: whether a guard raised just now parries perfectly: not shaking, not worn out, not
	 * raised again at once, and (the saber's guard) its parry not locked by the settings.
	 */
	static boolean perfectReady(Figure f, ServerPlayer player) {
		return !shaky(player) && !SaberAbilities.tired(player) && !f.raiseSpam && SaberAbilities.parryReady(player);
	}

	/** Phase 29: one standing in her (or on her) is pushed out, a little each tick, as against anyone. */
	private static void bodyContact(Figure f, ServerPlayer player, Vec3 me, double distance) {
		double dy = me.y - f.y;

		if (distance >= BODY || dy < -1.0 || dy > f.crew.height() + 0.3) {
			return;
		}

		Vec3 away = distance < 1.0E-3 ? f.look().scale(-1.0) : new Vec3(me.x - f.x, 0.0, me.z - f.z).scale(1.0 / distance);
		double by = (BODY - distance) * 0.4 + (dy > 1.0 ? 0.1 : 0.0);
		player.push(away.x * by, 0.0, away.z * by);
		player.hurtMarked = true;
		bodyPushes++;
	}

	/** Phase 29: whether the scene at half her strength may begin now: the player stands on the floor, not in a dash. */
	static boolean sceneReady(ServerPlayer player) {
		return standing(player) && !SaberAbilities.evading(player) && !player.isPassenger();
	}

	/**
	 * Phase 29: whether the player really stands on something: the floor right under their feet
	 * (what the world holds, not only the on-ground flag their game sent last: after a teleport it
	 * can be stale either way), and not on their way up.
	 */
	static boolean standing(ServerPlayer player) {
		net.minecraft.world.phys.AABB box = player.getBoundingBox();
		net.minecraft.world.phys.AABB under = new net.minecraft.world.phys.AABB(box.minX + 0.02, box.minY - 0.08, box.minZ + 0.02, box.maxX - 0.02, box.minY + 0.01, box.maxZ - 0.02);
		return player.getDeltaMovement().y < 0.05 && !player.level().noCollision(player, under);
	}

	/**
	 * Phase 29: half her strength reached with the player in the air (or in a dash): she steps back
	 * on guard and waits for them to land ({@link #SCENE_WAIT} at most); nothing hurts meanwhile.
	 */
	private static void sceneWaits(StorySession s, Figure f) {
		f.health = PHASE_TWO;
		ringStops(f);
		f.throwFrom = null;
		f.react = 0;
		f.reactTicks = 0;
		f.combo = 0;
		f.hits = 0;
		f.parried = 0;
		f.burst = 0;
		f.lunge = false;
		act(f, GUARD, 0, 0);
		f.cooldown = SCENE_WAIT + 10;
		s.set(SCENE_PENDING);
		s.timer("boss_pending", SCENE_WAIT);
		scenesWaited++;
		s.dirty = true;
	}

	/** Phase 29: each tick while the scene waits: she keeps her distance facing the player; when they stand (or the wait ran out), it begins. */
	private static void scenePending(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, double distance) {
		Hits.tick(f, level, corner);
		turn(f, me, TURN);

		if (distance < 2.0) {
			Stealth.step(f, f.position().add(f.position().subtract(me).normalize().scale(1.5)), Stealth.WALK, level, corner);
		} else {
			f.speed = 0.0F;
		}

		if (!sceneReady(player) && s.timer("boss_pending")) {
			return;
		}

		if (!standing(player)) {
			// Still in the air when the wait ran out: set down on the floor under them first.
			setDown(player, level);
		}

		s.unset(SCENE_PENDING);
		s.timers.remove("boss_pending");
		lockStarts(s, f, player, player.position().subtract(corner), level, corner);
	}

	/** Phase 29: sets the player down on the floor under them (no flight left over), when there is one within reach. */
	static boolean setDown(ServerPlayer player, ServerLevel level) {
		Vec3 top = player.position().add(0.0, 0.1, 0.0);
		net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(top, top.subtract(0.0, 12.0, 0.0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;

		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
			return false;
		}

		player.connection.teleport(player.getX(), hit.getLocation().y, player.getZ(), player.getYRot(), player.getXRot());
		setDowns++;
		return true;
	}

	/** Phase 29: remembers a move of hers (newest first). */
	private static void remember(Figure f, int move) {
		System.arraycopy(f.moves, 0, f.moves, 1, f.moves.length - 1);
		f.moves[0] = move;

		if (move >= 0 && move < MOVES_MADE.length) {
			MOVES_MADE[move]++;
		}
	}

	/** Phase 29, for the tests: how often she chose each move (by its number: the actions, a lunge, a shove, a turn). */
	private static final int[] MOVES_MADE = new int[16];

	static int[] movesForTest() {
		return MOVES_MADE.clone();
	}

	/** Phase 29: how much she wants a move again: less right after it, a little less the time before. */
	private static double fresh(Figure f, int move) {
		return f.moves[0] == move ? 0.3 : f.moves[1] == move ? 0.6 : f.moves[2] == move ? 0.85 : 1.0;
	}

	/** On guard: circling at sword's length until she goes in (in the second phase sooner, and with more ways to attack). */
	private static void guard(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase, double distance) {
		RandomSource random = player.getRandom();
		boolean clear = Stealth.clear(level, corner.add(f.eye()), player.getEyePosition());

		// Phase 28: her throw is due (once or twice a fight): she keeps off to throw from there.
		boolean throwDue = throwDue(s, f, phase);

		// Phase 29: the player behind her for a moment (she noticed): she answers it, whatever else she meant to do.
		if (f.behindTicks >= notice(s) && distance < BEHIND_NEAR && !s.timer("rear_answer")) {
			rearAnswer(s, f, player, me, level, corner, distance, random);
			return;
		}

		// Phase 29: one crowding her is shoved away (close pressure), also while she circles.
		if (distance < SHOVE_NEAR && !s.timer("shove") && f.cooldown <= pause(f, phase) / 2 + 4) {
			shoveStarts(s, f, level, corner);
			return;
		}

		if (f.cooldown > 0) {
			f.cooldown--;
			ringRests(s, f);

			if (throwDue && distance < THROW_BACK_TO - 0.3) {
				Vec3 away = f.position().add(f.position().subtract(me).normalize().scale(1.5));

				if (!Stealth.step(f, away, Stealth.WALK * 1.6, level, corner)) {
					Vec3 to = f.position().subtract(me).normalize();

					if (!Stealth.step(f, f.position().add(new Vec3(-to.z, 0.0, to.x).scale(f.strafe)), Stealth.WALK * 1.1, level, corner)) {
						f.strafe = -f.strafe;
					}
				}
			} else if (distance > (throwDue ? THROW_TO - 1.5 : 3.0)) {
				Stealth.step(f, me, Stealth.WALK * (phase >= 2 ? 1.6 : 1.3), level, corner);
			} else if (distance < 1.6) {
				Stealth.step(f, f.position().add(f.position().subtract(me).normalize()), Stealth.WALK, level, corner);
			} else {
				// Circling: a step to the side (Phase 29: now and then the other way, not round and round one way).
				if (random.nextInt(60) == 0) {
					f.strafe = -f.strafe;
				}

				Vec3 to = f.position().subtract(me).normalize();
				Vec3 side = new Vec3(-to.z, 0.0, to.x).scale(f.strafe);

				if (!Stealth.step(f, f.position().add(side), Stealth.WALK * (phase >= 2 ? 1.1 : 0.8), level, corner)) {
					f.strafe = -f.strafe;
				}
			}

			return;
		}

		// Phase 28: the throw first, when it is due (else push and leap took every chance of it).
		if (throwDue && clear && distance > THROW_NEAR && distance < THROW_TO) {
			throwStarts(s, f, level, corner, random);
			remember(f, THROW);
			return;
		}

		if (throwDue && distance <= THROW_NEAR && !s.timers.containsKey("throw_back")) {
			// Too close to throw: she backs off a moment first (once; then she fights on and throws later).
			s.timer("throw_back", 40);
		}

		if (throwDue && s.timer("throw_back")) {
			f.cooldown = 3;
			return;
		}

		// Phase 29: what she does next is chosen by weight among what she can do now (no fixed order:
		// the order made her predictable), less what she did just now, more of what answers how the
		// player fights (a guard held up all the time draws the push and the slam; one far off the
		// leap, the lunge, the rush).
		boolean turtle = guardTicks(s, player) >= TURTLE;
		double[] weights = new double[15];

		if (!s.timer("push") && distance > 2.5 && distance < 8.0 && clear && (phase >= 2 || f.health <= RING_FROM)) {
			weights[PUSH] = 1.4 * (turtle ? 2.4 : 1.0) * fresh(f, PUSH);
		}

		if (phase >= 2 && !s.timer("leap") && distance > LEAP_FROM && distance < 9.0 && clear) {
			weights[LEAP] = (distance > 6.0 ? 2.2 : 1.4) * fresh(f, LEAP);
		}

		// Phase 26: the ring's rush (not right after her leap or throw). Phase 27: in the first
		// phase too, rarer, once she has lost some strength (one blade whirling), not right after a push.
		if ((phase >= 2 || f.health <= RING_FROM) && !s.timer("rush") && distance > RUSH_FROM && distance < RUSH_TO && clear
				&& since(s, f, "leap", LEAP_EVERY, 50) && since(s, f, "throw", THROW_EVERY, 60)
				&& (phase >= 2 || since(s, f, "push", PUSH_EVERY_FIRST, 60))) {
			weights[RUSH] = 1.3 * fresh(f, RUSH);
		}

		// Phase 26: she lunges in with a thrust from a few steps away (Phase 29: in the second phase too, less often).
		if (!s.timer("lunge") && distance > LUNGE_FROM && distance < LUNGE_TO && clear) {
			weights[MOVE_LUNGE] = (phase == 1 ? 1.6 : 0.8) * fresh(f, MOVE_LUNGE);
		}

		// Phase 29: her slam, from the last fifth of her first phase on, with the player before her at its reach.
		if ((phase >= 2 || f.health <= SLAM_FROM) && !s.timer("slam") && distance > 1.2 && distance < SLAM_REACH - 0.4 && clear
				&& Math.abs(Mth.wrapDegrees(Crew.yaw(f.position(), me) - f.headYaw)) < 40.0F) {
			weights[SLAM] = 1.3 * (turtle ? 2.8 : 1.0) * fresh(f, SLAM);
		}

		if (distance <= BLOW_REACH - 0.4) {
			// Close: her blade work first of all (the pressure).
			weights[WINDUP] = (distance < 2.0 ? 3.6 : 3.0) * Math.max(0.55, fresh(f, WINDUP));
		}

		double total = 0.0;

		for (double weight : weights) {
			total += weight;
		}

		if (total <= 0.0) {
			if (!(clear && Stealth.step(f, me, 3.8, level, corner)) && !Stealth.go(f, me, 3.8, level, corner, s.time())) {
				// Phase 28: out of her sight (round a corner, through a door) she comes the way round, as
				// the troopers do, instead of standing at the wall between for the rest of the fight.
				f.speed = 0.0F;
			}

			return;
		}

		double roll = random.nextDouble() * total;
		int move = WINDUP;

		for (int m = 0; m < weights.length; m++) {
			if (weights[m] <= 0.0) {
				continue;
			}

			move = m;
			roll -= weights[m];

			if (roll < 0.0) {
				break;
			}
		}

		remember(f, move);

		switch (move) {
			case PUSH -> {
				act(f, PUSH, HAND, HAND_TICKS);
				f.windup = HAND_TICKS;
				f.shove = false;
				// (Phase 27: in the first phase too, rarer.)
				s.timer("push", scaled(f, phase >= 2 ? PUSH_EVERY : PUSH_EVERY_FIRST));
			}
			case LEAP -> {
				act(f, LEAP, CROUCH, CROUCH_TICKS);
				f.windup = CROUCH_TICKS;
				f.lure = null;
				s.timer("leap", scaled(f, LEAP_EVERY));
			}
			case RUSH -> rushStarts(s, f, level, corner);
			case MOVE_LUNGE -> {
				s.timer("lunge", LUNGE_EVERY);
				f.pattern = -1;
				f.combo = 1;
				f.lunge = true;
				blowStarts(f, THRUST, scaled(f, WINDUPS[1] + 4), level, s);
				lunges++;
			}
			case SLAM -> slamStarts(s, f, level, corner);
			default -> {
				// A combo: one of her patterns (not the same twice running).
				int[][] patterns = patterns(f, phase);
				int pick = random.nextInt(patterns.length);

				if (pick == f.pattern && patterns.length > 1) {
					pick = (pick + 1) % patterns.length;
				}

				f.pattern = pick;
				f.combo = patterns[pick].length;
				blowStarts(f, patterns[pick][0], scaled(f, WINDUPS[phase]), level, s);
			}
		}
	}

	/**
	 * Phase 29: the player has been behind her for a moment: she whirls round with a blow that
	 * reaches behind her, shoves one close away with a turn, or steps round to have them before her
	 * again (by chance, not the same twice running).
	 */
	private static void rearAnswer(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, double distance, RandomSource random) {
		s.timer("rear_answer", 24);
		rearAnswers++;
		s.counters.merge("rear_answers", 1, Integer::sum);
		double roll = random.nextDouble();

		if (distance < SHOVE_NEAR + 0.3 && !s.timer("shove") && roll < 0.35 && f.moves[0] != MOVE_SHOVE) {
			shoveStarts(s, f, level, corner);
			return;
		}

		if (roll < 0.8 || f.moves[0] == MOVE_TURN && f.moves[1] == MOVE_TURN) {
			turnaboutBlow(s, f, level, TURNABOUT);
			return;
		}

		// A step round (away and to the side) while turning: the player is before her again.
		Vec3 away = f.position().subtract(me);
		away = away.lengthSqr() < 1.0E-4 ? f.look() : away.normalize();
		Vec3 side = new Vec3(-away.z, 0.0, away.x).scale(f.strafe);

		if (!Stealth.step(f, f.position().add(away.add(side).scale(1.2)), Stealth.WALK * 2.0, level, corner)) {
			f.strafe = -f.strafe;
		}

		// (She turns as she steps: aware() turned her this tick already, quickly; no second turn on top.)
		f.behindTicks = 0;
		f.cooldown = Math.max(f.cooldown, 6);
		remember(f, MOVE_TURN);
	}

	/** Phase 29: a quick shove with the Force at one too close (her hand comes up first: to be seen). */
	private static void shoveStarts(StorySession s, Figure f, ServerLevel level, Vec3 corner) {
		act(f, PUSH, HAND, scaled(f, SHOVE_TICKS));
		f.windup = f.timer;
		f.shove = true;
		f.behindTicks = 0;
		s.timer("shove", scaled(f, SHOVE_EVERY));
		shoves++;
		remember(f, MOVE_SHOVE);
		Vec3 at = f.position().add(corner).add(0.0, 1.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.5F, 0.6F);
	}

	/**
	 * Phase 25: from a distance she throws her staff (not too soon after her leap or push; Phase 26:
	 * nor right after the ring's rush). Phase 28: once or twice a fight ({@link #THROWS_MAX}), and it
	 * comes when it is due (she makes the distance for it), no longer only when the player happens
	 * to stand far enough off while her push and leap are both waiting. (The rush's gap is measured
	 * against the time its timer was really set to, as the other gaps.)
	 */
	static boolean throwDue(StorySession s, Figure f, int phase) {
		return phase >= 2 && f.thrown < THROWS_MAX && !s.timer("throw") && since(s, f, "leap", LEAP_EVERY, 60) && since(s, f, "push", PUSH_EVERY, 40)
				&& since(s, f, "rush", fierce(f) ? RUSH_EVERY_FIERCE : RUSH_EVERY, 50);
	}

	/**
	 * Phase 28: her ring is set going (a ring move of the second phase: the spin, the rush, the
	 * throw; her last quarter): it keeps turning from now on, at least {@link #RING_MIN} ticks after
	 * the last such move.
	 */
	private static void ringGoes(StorySession s, Figure f) {
		if (phase(f) < 2 || !f.lit) {
			return;
		}

		if (!f.ringOn) {
			f.ringOn = true;
			ringStarted++;
		}

		s.timer("ring_min", RING_MIN);
	}

	/** Phase 28: a calm moment (circling on guard, not in her last quarter, long after a ring move): the ring may stop. */
	private static void ringRests(StorySession s, Figure f) {
		if (f.ringOn && !fierce(f) && !s.timer("ring_min") && f.react == 0) {
			f.ringOn = false;
			ringStopped++;
		}
	}

	/** Phase 28: the ring stops at once (the blade lock, her defeat, a new fight). */
	private static void ringStops(Figure f) {
		if (f.ringOn) {
			f.ringOn = false;
			ringStopped++;
		}
	}

	/** Phase 26: her combos now (the last quarter of her strength has its own). */
	static int[][] patterns(Figure f, int phase) {
		return fierce(f) ? FIERCE_PATTERNS : PATTERNS[phase];
	}

	/** Phase 26: whether she is in the last quarter of her strength (she fights harder). */
	static boolean fierce(Figure f) {
		return f.health <= FIERCE;
	}

	/** Phase 26: how long she circles between attacks (shorter in the last quarter). */
	static int pause(Figure f, int phase) {
		return scaled(f, fierce(f) ? PAUSES[2] * 2 / 3 : PAUSES[phase]);
	}

	/** Phase 26: the wind-up of the blows after a combo's first (quicker in the last quarter). */
	static int follow(Figure f, int phase) {
		return scaled(f, fierce(f) ? FOLLOW[2] - 2 : FOLLOW[phase]);
	}

	/** Phase 27: a time of hers (a pause, a wind-up, the time until she does it again) by her tempo (the difficulty). */
	static int scaled(Figure f, int ticks) {
		return Math.max(1, Math.round(ticks / Math.max(0.1F, f.tempo)));
	}

	/**
	 * Phase 27: whether at least {@code gap} ticks (at her tempo) passed since a move whose timer
	 * was set to {@code every} (at her tempo): the gaps between her moves scale as the timers do.
	 */
	static boolean since(StorySession s, Figure f, String timer, int every, int gap) {
		return s.timers.getOrDefault(timer, 0) <= scaled(f, every) - scaled(f, gap);
	}

	/** Phase 27: how long an opening of hers lasts by the difficulty (never shorter than a stroke needs). */
	static int opening(Figure f, int ticks) {
		return Math.max(12, Math.round(ticks * f.openness));
	}

	/** The kind of her next blow in the combo (a riposte or a leap's blow: by chance). */
	private static int nextKind(Figure f, int phase, RandomSource random) {
		int[][] patterns = patterns(f, phase);
		int[] pattern = f.pattern >= 0 && f.pattern < patterns.length ? patterns[f.pattern] : null;
		int index = pattern == null ? -1 : pattern.length - f.combo;
		return pattern != null && index >= 0 && index < pattern.length ? pattern[index] : 1 + random.nextInt(3);
	}

	/** A blow begins: the blade goes up (or back, or in). */
	private static void blowStarts(Figure f, int kind, int ticks, ServerLevel level, StorySession s) {
		if (kind == SPIN) {
			ticks = Math.max(ticks, SPIN_WINDUP);
			spins++;
			ringGoes(s, f);
		}

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
		double reach = f.attack == THRUST ? THRUST_REACH : f.attack == SPIN ? SPIN_REACH : BLOW_REACH;
		Vec3 at = contact(f, me).add(corner);
		RandomSource random = player.getRandom();
		f.combo--;
		f.lunge = false;
		boolean evading = SaberAbilities.evading(player);
		boolean turnabout = f.turnabout;
		f.turnabout = false;

		// Phase 29: a plain blow reaches before her and to her sides, not behind her (a spin all
		// round, a turn-about blow round behind her): one who got behind her is not hit, but no
		// opening comes of it either: she whirls round at once.
		Vec3 flat = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		boolean outside = flat.lengthSqr() > 0.09 && Math.toDegrees(Math.acos(Mth.clamp(flat.normalize().dot(f.look()), -1.0, 1.0))) > arc(f.attack, turnabout);

		if (outside && !evading && distance <= reach && Math.abs(me.y - f.y) <= 2.0) {
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.6F, 0.75F);
			s.counters.merge("blow_behind", 1, Integer::sum);

			if (f.combo > 0) {
				blowStarts(f, nextKind(f, phase, random), follow(f, phase), level, s);
			} else {
				turnaboutBlow(s, f, level, TURNABOUT);
			}

			return;
		}

		if (distance > reach || evading || Math.abs(me.y - f.y) > 2.0) {
			// Missed: the player stepped back or dodged.
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.6F, 0.7F);

			if (evading) {
				dodges++;
				s.counters.merge("dodged", 1, Integer::sum);
				Story.cue(s, Story.CUE_DUEL, "dodged", "", f.id);
			}

			if (f.combo > 0) {
				blowStarts(f, nextKind(f, phase, random), follow(f, phase) + random.nextInt(3) - 1, level, s);
			} else {
				act(f, OPEN_UP, STAGGER, opening(f, OPENINGS[phase] - (evading ? 0 : 6)));
				f.hits = 0;
			}

			return;
		}

		Vec3 toHim = new Vec3(f.x - me.x, 0.0, f.z - me.z).normalize();
		Vec3 look = player.getLookAngle();
		boolean facing = new Vec3(look.x, 0.0, look.z).normalize().dot(toHim) > 0.35;
		long guardTicks = guardTicks(s, player);

		if (guardTicks >= 0 && facing) {
			// Phase 28: a guard already shaking parries nothing perfectly any more; each blow costs stamina.
			// (Phase 29: nor one worn out, or raised again at once; a perfect parry does not count as one more blow in a row.)
			boolean perfect = guardTicks <= PERFECT && perfectReady(f, player);
			int held = strain(s, f, player, perfect ? STRAIN_PERFECT : f.attack == SPIN ? STRAIN_SPIN : phase >= 2 ? STRAIN_BLOW_2 : STRAIN_BLOW, at, level, !perfect);

			if (held == SaberAbilities.GUARD_BROKEN) {
				// Kept up through blow after blow: the guard breaks, the blow comes through (weaker).
				breaksThrough(s, f, player, me, level, corner, BLOWS[phase] * BREAK_DAMAGE);
				f.combo = 0;
				f.burst = 0;
				// (She steps back and gathers herself: a broken guard is a setback, not the end.)
				act(f, RECOIL, 0, 10 + pause(f, phase) / 2);
				return;
			}

			boolean strained = held == SaberAbilities.GUARD_STRAINED;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, perfect ? 28 : strained ? 22 : 16, 0.1, 0.1, 0.1, 0.45);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound(perfect ? "story.clash_big" : "saber.clash"), SoundSource.HOSTILE, 1.1F,
					perfect ? 1.1F : strained ? 0.68F + random.nextFloat() * 0.08F : 0.85F + random.nextFloat() * 0.15F);
			s.counters.merge(perfect ? "perfect" : "blocked", 1, Integer::sum);
			s.focus = Math.min(1.0F, s.focus + (perfect ? 0.5F : strained ? FOCUS_BLOCK * 0.5F : FOCUS_BLOCK));
			Story.cue(s, Story.CUE_DUEL, perfect ? "perfect" : strained ? "strained" : "blocked", "", f.id, java.util.List.of(at));
			f.burst = 0;

			if (perfect) {
				// Just in time: her blade is thrown back, she reels: the opening, longer.
				perfects++;
				f.combo = 0;
				f.react(Figure.STUMBLE, 14);
				Hits.push(f, me, 0.25);
				act(f, OPEN_UP, STAGGER, opening(f, OPENINGS[phase] + 8));
				f.hits = 0;
				f.parried = 0;
			} else if (f.combo > 0) {
				if (phase >= 2 || strained) {
					// The second phase presses: each blocked blow drives the player back a little
					// (Phase 28: a shaking guard further, in either phase).
					Vec3 back = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize();
					double by = strained ? 0.45 : 0.3;
					player.push(back.x * by, 0.0, back.z * by);
					player.hurtMarked = true;
				}

				blowStarts(f, nextKind(f, phase, random), follow(f, phase) + random.nextInt(3) - 1, level, s);
			} else {
				// The last blow of the combo blocked: she staggers. The opening.
				act(f, OPEN_UP, STAGGER, opening(f, OPENINGS[phase]));
				f.hits = 0;
				f.parried = 0;
			}

			return;
		}

		// Neither blocked nor dodged: it hurts.
		s.counters.merge("duel_hurt", 1, Integer::sum);
		player.hurtServer(level, level.damageSources().generic(), BLOWS[phase] * f.power);
		Vec3 push = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize().scale(0.6);
		player.push(push.x, 0.25, push.z);
		player.hurtMarked = true;
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 1.0F, 0.9F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.1, player.getZ(), 12, 0.15, 0.2, 0.15, 0.3);
		Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(BLOWS[phase] * f.power), java.util.List.of(f.position().add(corner)));
		f.combo = 0;
		f.burst = 0;
		act(f, RECOIL, 0, 10);
	}

	/**
	 * Phase 28: the player's guard against her: how long it has been up (ticks), or -1 when it is
	 * not, and -1 too while a guard she broke is still down (also one raised with the use key).
	 */
	static long guardTicks(StorySession s, ServerPlayer player) {
		// (Phase 29: nor while the stricter guard keeps a broken guard down.)
		return s.timer("guard_broken") || SaberAbilities.guardLocked(player) ? -1L : guardTicks(player);
	}

	/**
	 * Phase 29: how far round her a blow reaches (degrees off the way she faces, each side): a
	 * thrust and an overhead blow before her, a sweep to her sides too, a turn-about blow round
	 * behind her, a spin all round.
	 */
	static double arc(int attack, boolean turnabout) {
		if (attack == SPIN) {
			return 181.0;
		}

		if (turnabout) {
			return 165.0;
		}

		return attack == SWEEP ? 115.0 : 80.0;
	}

	/** Phase 28: whether the player's guard shakes already (little stamina left): no perfect parry then. */
	static boolean shaky(ServerPlayer player) {
		com.phiro.sabercraft.ability.AbilityConfig config = com.phiro.sabercraft.ability.AbilityConfig.get();
		return config.staminaEnabled && SaberAbilities.stamina(player) < UNSTABLE * config.staminaMax;
	}

	/**
	 * Phase 28: one of her blows (a share of the most stamina, see the constants) taken on the
	 * player's guard: it costs stamina by the difficulty; a guard shaking or broken is felt and
	 * heard, and said once a fight. Returns what {@link SaberAbilities#strainGuard} did.
	 */
	private static int strain(StorySession s, Figure f, ServerPlayer player, float share, Vec3 at, ServerLevel level) {
		return strain(s, f, player, share, at, level, true);
	}

	/** Phase 29: as above; {@code chained} false for a perfect parry (not one more blow in a row). */
	private static int strain(StorySession s, Figure f, ServerPlayer player, float share, Vec3 at, ServerLevel level, boolean chained) {
		StoryDifficulty difficulty = s.difficulty();
		int held = SaberAbilities.strainGuard(player, share * difficulty.bossStrain, UNSTABLE, difficulty.guardBreak, chained);

		if (held == SaberAbilities.GUARD_BROKEN) {
			guardBreaks++;
			s.counters.merge("guard_broken", 1, Integer::sum);
			s.timer("guard_broken", difficulty.guardBreak);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.clash_big"), SoundSource.HOSTILE, 1.25F, 0.62F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 34, 0.15, 0.15, 0.15, 0.55);
			Story.cue(s, Story.CUE_DUEL, "guard_break", "", f.id, java.util.List.of(at));

			if (!s.has("tmp.hint_guard_break")) {
				s.set("tmp.hint_guard_break");
				s.hint("sabercraft.story.hint.guard_broken");
			}
		} else if (held == SaberAbilities.GUARD_STRAINED) {
			strains++;
			s.counters.merge("guard_strained", 1, Integer::sum);

			if (!s.has("tmp.hint_guard_strain")) {
				s.set("tmp.hint_guard_strain");
				s.hint("sabercraft.story.hint.guard_strained");
			}
		}

		return held;
	}

	/** Phase 28: a blow (or the thrown staff, the rush) breaks through the guard: it hurts, weaker, and throws the player back. */
	private static void breaksThrough(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, float damage) {
		s.counters.merge("duel_hurt", 1, Integer::sum);
		player.hurtServer(level, level.damageSources().generic(), damage * f.power);
		Vec3 push = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		push = push.lengthSqr() < 1.0E-4 ? f.look().scale(0.7) : push.normalize().scale(0.7);
		player.push(push.x, 0.2, push.z);
		player.hurtMarked = true;
		Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(damage * f.power), java.util.List.of(f.position().add(corner)));
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

	/**
	 * Phase 25: she draws her staff back over her shoulder to throw it, twirling (the warning: a
	 * sound, the staff spinning in her hand, a word the first time).
	 */
	private static void throwStarts(StorySession s, Figure f, ServerLevel level, Vec3 corner, RandomSource random) {
		// Phase 29: thrown at the chest (blocked or dashed under) or low at the legs (jumped over,
		// dashed under, blocked): the first by chance, then the other way each time.
		f.throwLow = f.thrown == 0 ? random.nextBoolean() : !f.throwLow;
		f.flightHits = 0;
		f.thrown++;
		s.timers.remove("throw_back");
		ringGoes(s, f);
		act(f, THROW, 0, THROW_WIND);
		f.windup = THROW_WIND;
		f.throwFrom = null;
		f.throwAge = 0;
		f.throwHit = 0;
		f.react(Figure.THROW_WIND, THROW_WIND);
		s.timer("throw", scaled(f, THROW_EVERY));
		throwsStarted++;
		s.counters.merge("throws", 1, Integer::sum);
		Vec3 at = f.position().add(corner).add(0.0, 1.4, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.9F, 0.6F);
		// Phase 26: the ring spins up as she draws it back (heard and seen: the warning, no words).
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.ring_spin"), SoundSource.HOSTILE, 0.9F, 1.0F);
	}

	/**
	 * Phase 25: the throw, each tick: drawn back, then let go at the player (past where they stand,
	 * not through a wall), and back to her hand. It hurts on the way out and on the way back (once
	 * each); a guard raised towards it turns it aside, a dash goes under it. She stands meanwhile,
	 * her hand out after it: a stroke at her then hits.
	 */
	private static void throwTick(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		f.speed = 0.0F;

		if (f.windup > 0) {
			// (Phase 29: she aims while she draws it back.)
			turn(f, me, 10.0F);

			if (--f.windup > 0) {
				return;
			}

			// Let go.
			Vec3 look = f.look();
			// (Phase 29: thrown low from low down, skimming at shin height: a jump clears it.)
			Vec3 start = f.position().add(corner).add(0.0, f.throwLow ? 0.6 : 1.45, 0.0).add(look.scale(0.35)).add(new Vec3(-look.z, 0.0, look.x).scale(-0.3));
			Vec3 target = player.position().add(0.0, f.throwLow ? 0.25 : 1.0, 0.0);
			Vec3 dir = target.subtract(start).normalize();
			// (Phase 29: a low throw skims on level past the player, not down into the floor.)
			Vec3 flatDir = new Vec3(dir.x, 0.0, dir.z).lengthSqr() < 1.0E-4 ? dir : new Vec3(dir.x, 0.0, dir.z).normalize();
			Vec3 far = target.add((f.throwLow ? flatDir : dir).scale(THROW_PAST));
			net.minecraft.world.phys.BlockHitResult wall = level.clip(new net.minecraft.world.level.ClipContext(start, far,
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));

			if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
				far = wall.getLocation().subtract(dir.scale(0.45));
			}

			int out = Mth.clamp((int) Math.round(start.distanceTo(far) / 0.7), 8, 20);
			f.throwFrom = start;
			f.throwFar = far;
			f.throwHand = f.position().add(corner).add(0.0, 1.4, 0.0).add(look.scale(0.45));
			f.throwOut = out;
			f.throwBack = out + 2;
			f.throwAge = 0;
			f.throwHit = 0;
			f.react(Figure.THROWN, f.throwOut + f.throwBack);
			level.playSound(null, start.x, start.y, start.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 1.1F, 1.2F);
			Story.cue(s, Story.CUE_DUEL, "throw", "", f.throwOut * 1000 + f.throwBack, java.util.List.of(start, far, f.throwHand));
			return;
		}

		if (f.throwFrom == null || f.throwFar == null || f.throwHand == null) {
			act(f, GUARD, 0, 0);
			return;
		}

		turn(f, me, 8.0F);
		int t = ++f.throwAge;
		Vec3 was = StaffThrow.at(f.throwFrom, f.throwFar, f.throwHand, f.throwOut, f.throwBack, t - 1);
		Vec3 now = StaffThrow.at(f.throwFrom, f.throwFar, f.throwHand, f.throwOut, f.throwBack, t);

		if (t % 4 == 0) {
			level.playSound(null, now.x, now.y, now.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.8F, 1.3F);
		}

		int leg = t <= f.throwOut ? 1 : 2;
		// (Phase 29: the low throw reaches round the player as the high one, but hardly below their
		// feet: in the air over it one is not hit. The high one is gone under with a dash.)
		net.minecraft.world.phys.AABB box = player.getBoundingBox();
		double round = THROW_HIT - 0.3;
		net.minecraft.world.phys.AABB body = f.throwLow
				? new net.minecraft.world.phys.AABB(box.minX - round, box.minY - 0.05, box.minZ - round, box.maxX + round, box.maxY + round, box.maxZ + round)
				: box.inflate(round);

		// (Phase 29: the low throw hits at most once: struck on its way out, one is not struck again
		// from behind as it is pulled back up over one's head.)
		boolean spent = f.throwLow ? f.throwHit != 0 : (f.throwHit & leg) != 0;

		if (!spent && (body.contains(now) || body.clip(was, now).isPresent())) {
			f.throwHit |= leg;
			throwMeets(s, f, player, level, corner, now);
		}

		if (t >= f.throwOut + f.throwBack) {
			// Back in her hand.
			level.playSound(null, f.throwHand.x, f.throwHand.y, f.throwHand.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.8F, 0.6F);
			Story.cue(s, Story.CUE_DUEL, "caught", "", f.id);
			f.throwFrom = null;
			f.react = 0;
			f.reactTicks = 0;
			act(f, GUARD, 0, 0);
			f.cooldown = pause(f, phase(f));
		}
	}

	/** Phase 25: the thrown staff reaches the player: dodged, turned aside by the guard, or it hurts. */
	private static void throwMeets(StorySession s, Figure f, ServerPlayer player, ServerLevel level, Vec3 corner, Vec3 at) {
		if (SaberAbilities.evading(player)) {
			throwDodges++;
			s.counters.merge("throw_dodged", 1, Integer::sum);
			Story.cue(s, Story.CUE_DUEL, "dodged", "", f.id);
			return;
		}

		Vec3 look = player.getLookAngle();
		Vec3 to = at.subtract(player.getEyePosition());
		boolean facing = to.lengthSqr() < 1.0E-4 || new Vec3(look.x, 0.0, look.z).normalize().dot(new Vec3(to.x, 0.0, to.z).normalize()) > 0.2;

		if (guardTicks(s, player) >= 0 && facing) {
			// Phase 28: the staff's weight on the guard costs stamina too.
			int held = strain(s, f, player, STRAIN_THROW, at, level);

			if (held == SaberAbilities.GUARD_BROKEN) {
				Vec3 me = player.position().subtract(corner);
				breaksThrough(s, f, player, me, level, corner, THROW_DAMAGE * BREAK_DAMAGE);
				return;
			}

			boolean strained = held == SaberAbilities.GUARD_STRAINED;
			throwBlocks++;
			s.counters.merge("throw_blocked", 1, Integer::sum);
			s.focus = Math.min(1.0F, s.focus + (strained ? FOCUS_BLOCK * 0.5F : FOCUS_BLOCK));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 20, 0.12, 0.12, 0.12, 0.45);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.1F, strained ? 0.75F : 1.05F);
			Story.cue(s, Story.CUE_DUEL, strained ? "strained" : "blocked", "", f.id, java.util.List.of(at));
			return;
		}

		throwHits++;
		s.counters.merge("throw_hit", 1, Integer::sum);
		player.hurtServer(level, level.damageSources().generic(), THROW_DAMAGE * f.power);
		Vec3 push = new Vec3(player.getX() - at.x, 0.0, player.getZ() - at.z);
		push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.45);
		player.push(push.x, 0.2, push.z);
		player.hurtMarked = true;
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.15, 0.2, 0.15, 0.3);
		Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(THROW_DAMAGE * f.power), java.util.List.of(at));
	}

	// --- Phase 26: the ring's rush ------------------------------------------------------------------

	/** The ring spins up round the grip (its whirr rising, the blades blurring into a disc): the warning. */
	private static void rushStarts(StorySession s, Figure f, ServerLevel level, Vec3 corner) {
		ringGoes(s, f);
		act(f, RUSH, 0, RUSH_TICKS);
		f.windup = RUSH_WIND;
		f.react(Figure.RING, RUSH_WIND + RUSH_TICKS);
		s.timer("rush", scaled(f, fierce(f) ? RUSH_EVERY_FIERCE : phase(f) == 1 ? RUSH_EVERY_FIRST : RUSH_EVERY));
		rushes++;
		Vec3 at = f.position().add(corner).add(0.0, 1.3, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.ring_spin"), SoundSource.HOSTILE, 1.0F, 1.0F);
	}

	/** Each tick of the rush: spinning up in place, then driving at the player behind the disc. */
	private static void rushTick(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		if (f.windup > 0) {
			f.speed = 0.0F;
			turn(f, me, 12.0F);
			--f.windup;
			return;
		}

		// She turns after the player only slowly while she drives on (a step aside gets one out of her way).
		// (Phase 29: really slowly: her head no longer snaps to the player, so she drives where she faces.)
		turn(f, me, 4.5F);
		Vec3 ahead = f.position().add(f.look().scale(2.0));
		boolean moved = Stealth.step(f, ahead, RUSH_SPEED, level, corner);
		Vec3 at = f.position().add(corner).add(0.0, 1.3, 0.0);

		if (f.timer % 5 == 0) {
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.7F, 1.45F);
		}

		Vec3 toMe = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		double distance = toMe.length();

		if (distance < RUSH_REACH && Math.abs(me.y - f.y) < 2.0 && (distance < 0.6 || toMe.normalize().dot(f.look()) > 0.3)) {
			rushMeets(s, f, player, me, level, corner);
			return;
		}

		if (!moved || --f.timer <= 0) {
			// Into a wall, or out of breath: the ring winds down; she stands open.
			rushEnds(f, level, corner, 0);
		}
	}

	/** The rush reaches the player: dodged, blocked (a perfect parry throws her off balance), or it hurts. */
	private static void rushMeets(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 at = contact(f, me).add(corner);
		Vec3 back = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		back = back.lengthSqr() < 1.0E-4 ? f.look() : back.normalize();

		if (SaberAbilities.evading(player)) {
			// Gone past: she overshoots.
			dodges++;
			s.counters.merge("dodged", 1, Integer::sum);
			Story.cue(s, Story.CUE_DUEL, "dodged", "", f.id);
			rushEnds(f, level, corner, 6);
			return;
		}

		Vec3 look = player.getLookAngle();
		boolean facing = new Vec3(look.x, 0.0, look.z).normalize().dot(back.scale(-1.0)) > 0.35;
		long guard = guardTicks(s, player);

		if (guard >= 0 && facing) {
			// Phase 28: the whirling disc driven into the guard costs the most stamina.
			boolean perfect = guard <= PERFECT && perfectReady(f, player);
			int held = strain(s, f, player, perfect ? STRAIN_PERFECT : STRAIN_RUSH, at, level, !perfect);

			if (held == SaberAbilities.GUARD_BROKEN) {
				rushHits++;
				breaksThrough(s, f, player, me, level, corner, BLOWS[2] * BREAK_DAMAGE);
				rushEnds(f, level, corner, -RUSH_OPENING);
				return;
			}

			rushBlocks++;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, perfect ? 34 : 22, 0.15, 0.15, 0.15, 0.5);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.clash_big"), SoundSource.HOSTILE, 1.1F, perfect ? 1.15F : 0.9F);
			s.counters.merge(perfect ? "perfect" : "blocked", 1, Integer::sum);
			s.focus = Math.min(1.0F, s.focus + (perfect ? 0.5F : FOCUS_BLOCK));
			Story.cue(s, Story.CUE_DUEL, perfect ? "perfect" : "blocked", "", f.id, java.util.List.of(at));
			player.push(back.x * (perfect ? 0.4 : 0.8), 0.15, back.z * (perfect ? 0.4 : 0.8));
			player.hurtMarked = true;

			if (perfect) {
				perfects++;
				f.react = 0;
				rushEnds(f, level, corner, 8);
				f.react(Figure.STUMBLE, 14);
			} else {
				rushEnds(f, level, corner, 0);
			}

			return;
		}

		rushHits++;
		s.counters.merge("duel_hurt", 1, Integer::sum);
		player.hurtServer(level, level.damageSources().generic(), BLOWS[2] * f.power);
		player.push(back.x * 0.7, 0.25, back.z * 0.7);
		player.hurtMarked = true;
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 1.0F, 0.85F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.1, player.getZ(), 14, 0.15, 0.2, 0.15, 0.3);
		Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(BLOWS[2] * f.power), java.util.List.of(f.position().add(corner)));
		// (Still an opening after it, a shorter one.)
		rushEnds(f, level, corner, -RUSH_OPENING);
	}

	/** The rush is over: the ring winds down and she staggers (the opening: longer than after a combo). */
	private static void rushEnds(Figure f, ServerLevel level, Vec3 corner, int more) {
		if (f.react == Figure.RING) {
			f.react = 0;
			f.reactTicks = 0;
		}

		f.speed = 0.0F;
		f.combo = 0;
		f.hits = 0;
		f.parried = 0;
		act(f, OPEN_UP, STAGGER, opening(f, OPENINGS[2] + RUSH_OPENING + more));
		Vec3 at = f.position().add(corner).add(0.0, 1.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.7F, 0.55F);
	}

	/** A stroke into the spinning disc from the front: turned aside, the player thrown back a little. */
	private static void discTurns(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		Vec3 contact = contact(f, me).add(corner);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, contact.x, contact.y, contact.z, 14, 0.1, 0.1, 0.1, 0.45);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.0F, 1.2F);
		Vec3 back = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		back = back.lengthSqr() < 1.0E-4 ? f.look() : back.normalize();
		player.push(back.x * 0.5, 0.1, back.z * 0.5);
		player.hurtMarked = true;
		Story.cue(s, Story.CUE_DUEL, "parried", "", f.id, java.util.List.of(contact));
		s.counters.merge("parried", 1, Integer::sum);
	}

	/**
	 * Phase 29, for the tests: strokes from behind that hit, and those turned aside; her turn-about
	 * blows, her answers to one behind her; shoves; the player pushed out of her; slams begun,
	 * dodged, hit, blocked; strokes dodged while her staff was away; the scene waiting for the
	 * player to land, and the player set down on the floor.
	 */
	private static int rearHits;
	private static int rearParries;
	private static int turnabouts;
	private static int rearAnswers;
	private static int shoves;
	private static int bodyPushes;
	private static int slams;
	private static int slamMisses;
	private static int slamHits;
	private static int slamBlocks;
	private static int flightEvasions;
	private static int scenesWaited;
	private static int setDowns;

	static int[] phase29ForTest() {
		return new int[] {rearHits, rearParries, turnabouts, rearAnswers, shoves, bodyPushes, slams, slamMisses, slamHits, slamBlocks, flightEvasions,
				scenesWaited, setDowns};
	}

	private static int rushes;
	/** Phase 28: the ring set going and stopped; guards strained and broken. */
	private static int ringStarted;
	private static int ringStopped;
	private static int strains;
	private static int guardBreaks;
	private static int rushBlocks;
	private static int rushHits;
	private static int lunges;

	/** Phase 26, for the tests: rushes begun, blocked, hits; lunges begun. */
	static int[] rushesForTest() {
		return new int[] {rushes, rushBlocks, rushHits, lunges};
	}

	/** Phase 26, for the tests: she begins the ring's rush now. */
	static void rushForTest(StorySession s, Figure f) {
		ServerLevel level = Chapter1.interiors(s);

		if (level != null) {
			rushStarts(s, f, level, Vec3.atLowerCornerOf(s.origin(f.ship)));
		}
	}

	// --- Phase 29: the slam -------------------------------------------------------------------------

	/** Her slam begins: the blade goes up high in both hands, she crouches, the floor before her begins to glow. */
	private static void slamStarts(StorySession s, Figure f, ServerLevel level, Vec3 corner) {
		int wind = scaled(f, SLAM_WIND);
		act(f, SLAM, OVERHEAD, wind);
		f.windup = wind;
		f.slamDir = null;
		f.react(Figure.SLAM, wind);
		s.timer("slam", scaled(f, SLAM_EVERY));
		slams++;
		s.counters.merge("slams", 1, Integer::sum);
		Vec3 at = f.position().add(corner).add(0.0, 1.6, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.ring_spin"), SoundSource.HOSTILE, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.9F, 0.5F);
		Story.cue(s, Story.CUE_DUEL, "slam_wind", "", wind, java.util.List.of(at));
	}

	/**
	 * Each tick of the slam's wind-up: she aims at the player (slowly), until its way is set a few
	 * ticks before it falls; the floor glows along the arc it will strike, further out the nearer it
	 * comes. Then it falls.
	 */
	private static void slamTick(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase) {
		f.speed = 0.0F;

		if (f.windup > SLAM_LOCK) {
			turn(f, me, 7.0F);
		} else if (f.slamDir == null) {
			f.slamDir = f.look();
		}

		Vec3 dir = f.slamDir != null ? f.slamDir : f.look();
		float progress = 1.0F - (float) f.windup / Math.max(1, f.timer);

		if (f.windup % 2 == 0) {
			// The warning on the floor: the arc it will strike, drawn out as the wind-up goes on.
			double reach = 1.0 + (SLAM_REACH - 1.0) * Math.min(1.0, progress * 1.3);
			net.minecraft.core.particles.DustParticleOptions glow = new net.minecraft.core.particles.DustParticleOptions(0xFF3A1E, 1.1F);

			for (int k = -3; k <= 3; k++) {
				double a = Math.toRadians(SLAM_ARC * k / 3.0);
				Vec3 ray = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0.0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
				Vec3 p = f.position().add(ray.scale(reach)).add(corner).add(0.0, 0.08, 0.0);
				level.sendParticles(glow, p.x, p.y, p.z, 1, 0.05, 0.0, 0.05, 0.0);
			}
		}

		if (--f.windup <= 0) {
			slamFalls(s, f, player, me, level, corner, phase, dir);
		}
	}

	/**
	 * The slam falls: the shock wave runs out before her. Out of its way (a step aside, behind her,
	 * further than it reaches), in a dash or in the air over it, one is not touched; on guard it
	 * costs much stamina and throws one back; else it hurts more than any blow. Afterwards her
	 * blade is stuck in the floor a moment.
	 */
	private static void slamFalls(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, int phase, Vec3 dir) {
		f.slamDir = null;
		f.react = 0;
		f.reactTicks = 0;
		Vec3 impact = f.position().add(dir.scale(1.3)).add(corner).add(0.0, 0.1, 0.0);
		level.playSound(null, impact.x, impact.y, impact.z, ShipInteriors.sound("story.clash_big"), SoundSource.HOSTILE, 1.4F, 0.5F);
		level.playSound(null, impact.x, impact.y, impact.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 1.1F, 0.6F);

		for (int k = -3; k <= 3; k++) {
			double a = Math.toRadians(SLAM_ARC * k / 3.0);
			Vec3 ray = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0.0, dir.x * Math.sin(a) + dir.z * Math.cos(a));

			for (double r = 1.0; r <= SLAM_REACH; r += 0.9) {
				Vec3 p = f.position().add(ray.scale(r)).add(corner).add(0.0, 0.15, 0.0);
				level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.1, 0.05, 0.1, 0.02);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.15, 0.1, 0.15, 0.25);
			}
		}

		Story.cue(s, Story.CUE_DUEL, "slam", "", f.id, java.util.List.of(impact));
		Vec3 flat = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		double distance = flat.length();
		// (Right at her the wave catches one before her or at her side, not one pressed against her back.)
		double off = distance < 1.0E-3 ? 0.0 : Math.toDegrees(Math.acos(Mth.clamp(flat.scale(1.0 / distance).dot(dir), -1.0, 1.0)));
		boolean inArc = distance <= SLAM_REACH && (off <= SLAM_ARC || distance < 0.8 && off <= 90.0) && Math.abs(me.y - f.y) < 1.5;
		boolean jumped = !player.onGround() && me.y - f.y > 0.45;
		boolean evading = SaberAbilities.evading(player);

		if (!inArc || jumped || evading) {
			slamMisses++;
			s.counters.merge("slam_dodged", 1, Integer::sum);

			if (evading || jumped) {
				Story.cue(s, Story.CUE_DUEL, "dodged", "", f.id);
			}

			act(f, OPEN_UP, STAGGER, opening(f, OPENINGS[phase] + SLAM_OPENING));
			f.hits = 0;
			f.parried = 0;
			return;
		}

		Vec3 back = distance < 1.0E-3 ? dir : flat.scale(1.0 / distance);
		Vec3 look = player.getLookAngle();
		boolean facing = new Vec3(look.x, 0.0, look.z).normalize().dot(back.scale(-1.0)) > 0.35;
		long guard = guardTicks(s, player);

		if (guard >= 0 && facing) {
			boolean perfect = guard <= PERFECT && perfectReady(f, player);
			int held = strain(s, f, player, perfect ? STRAIN_PERFECT * 2.0F : STRAIN_SLAM, impact, level, !perfect);

			if (held == SaberAbilities.GUARD_BROKEN) {
				slamHits++;
				s.counters.merge("slam_hit", 1, Integer::sum);
				breaksThrough(s, f, player, me, level, corner, SLAM_DAMAGE * 0.7F);
				act(f, RECOIL, 0, 12);
				return;
			}

			slamBlocks++;
			s.counters.merge(perfect ? "perfect" : "slam_blocked", 1, Integer::sum);
			s.focus = Math.min(1.0F, s.focus + (perfect ? 0.5F : FOCUS_BLOCK));
			Story.cue(s, Story.CUE_DUEL, perfect ? "perfect" : "strained", "", f.id, java.util.List.of(impact));
			player.push(back.x * (perfect ? 0.35 : 0.9), 0.15, back.z * (perfect ? 0.35 : 0.9));
			player.hurtMarked = true;
			act(f, OPEN_UP, STAGGER, opening(f, OPENINGS[phase] + (perfect ? SLAM_OPENING + 6 : 0)));
			f.hits = 0;
			f.parried = 0;
			return;
		}

		slamHits++;
		s.counters.merge("slam_hit", 1, Integer::sum);
		s.counters.merge("duel_hurt", 1, Integer::sum);
		float damage = SLAM_DAMAGE * f.power;
		player.hurtServer(level, level.damageSources().generic(), damage);
		player.push(back.x * 0.9, 0.35, back.z * 0.9);
		player.hurtMarked = true;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.6, player.getZ(), 16, 0.2, 0.2, 0.2, 0.35);
		Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(damage), java.util.List.of(f.position().add(corner)));
		act(f, RECOIL, 0, 12);
	}

	/** Phase 29, for the tests: she begins her slam now. */
	static void slamForTest(StorySession s, Figure f) {
		ServerLevel level = Chapter1.interiors(s);

		if (level != null) {
			slamStarts(s, f, level, Vec3.atLowerCornerOf(s.origin(f.ship)));
		}
	}

	/** A push with the Force: the guard takes most of it. */
	private static void push(StorySession s, Figure f, ServerPlayer player, ServerLevel level, Vec3 corner) {
		Vec3 me = player.position().subtract(corner);
		Vec3 away = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		away = away.lengthSqr() < 1.0E-4 ? f.look() : away.normalize();
		// Phase 29: the short shove at one too close (who stepped away from her hand in time escapes it).
		boolean shove = f.shove;
		Vec3 at = f.position().add(corner).add(0.0, 1.2, 0.0);

		if (shove && Math.hypot(me.x - f.x, me.z - f.z) > 3.0) {
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 0.6F, 1.0F);
			return;
		}

		boolean guard = guardTicks(s, player) >= 0;
		float damage = (shove ? SHOVE_DAMAGE : PUSH_DAMAGE) * f.power;

		if (guard) {
			// Phase 29: the guard takes most of it, but not for nothing: it costs stamina too.
			int held = strain(s, f, player, shove ? STRAIN_SHOVE : STRAIN_PUSH, at, level);

			if (held == SaberAbilities.GUARD_BROKEN) {
				breaksThrough(s, f, player, me, level, corner, damage * BREAK_DAMAGE);
				level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 1.0F, 0.8F);
				return;
			}
		}

		double strength = shove ? guard ? 0.55 : 0.95 : guard ? 0.5 : 1.4;
		player.push(away.x * strength, guard ? 0.1 : shove ? 0.22 : 0.35, away.z * strength);
		player.hurtMarked = true;

		if (!guard) {
			// (Phase 27: by the difficulty, as her other attacks.)
			player.hurtServer(level, level.damageSources().generic(), damage);
			Story.cue(s, Story.CUE_DUEL, "hurt", "", Math.round(damage), java.util.List.of(f.position().add(corner)));
		}

		if (shove) {
			s.counters.merge(guard ? "shove_blocked" : "shove_hit", 1, Integer::sum);
		}

		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CLOUD, at.x + away.x, at.y, at.z + away.z, 12, 0.3, 0.3, 0.3, 0.05);
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
		ringStops(f);
		f.combo = 0;
		f.hits = 0;
		f.parried = 0;
		f.burst = 0;
		f.pushX = 0.0;
		f.pushZ = 0.0;
		// Phase 26: the scene begins the same way whatever the player was doing (running past her,
		// striking, blocking, dodging): first held still (a guard or dash going on ends, no stroke
		// counts any more), then both set at the lock's distance, facing each other, and only then
		// the camera. (Phiro's playtest: struck while running forward, the player stood with their
		// back to her after the scene.)
		f.react = 0;
		f.reactTicks = 0;
		f.throwFrom = null;
		me = settleForScene(s, f, player, level, corner);
		// Phase 24: as a scene: the lock lasts until the burst in it (the player cannot press against it then).
		act(f, LOCK, 0, BOSS_LOCK);
		f.react(Figure.LOCK, BOSS_LOCK);
		f.lockScene = true;
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, BOSS_LOCK + 4, 6, false, false));
		Vec3 contact = contact(f, me).add(corner);
		level.playSound(null, contact.x, contact.y, contact.z, ShipInteriors.sound("story.blade_lock"), SoundSource.HOSTILE, 1.2F, 1.0F);
		// (Her words are the scene's lines; nothing is shouted into the corner afterwards.)
		Story.cue(s, Story.CUE_DUEL, "lock", "", BOSS_LOCK, java.util.List.of(contact));
		s.set("tmp.film");
		s.set(BOSS_SCENE);
		s.set(BOSS_DONE);
		s.unset("tmp.boss_reveal");
		s.timer("boss_mid", BOSS_FILM);
		Story.cue(s, Story.CUE_CINEMA, "boss_mid", "", BOSS_FILM, java.util.List.of(contact));
		locks++;
		bossScenes++;
		s.dirty = true;
	}

	/** Phase 26: how far apart the two stand when the blades lock (feet to feet). */
	static final double LOCK_DISTANCE = 1.45;
	/** Phase 26: after the burst the player lands this many ticks later (and is held there again). */
	static final int BURST_LAND = 12;

	/**
	 * Phase 26: steps 1 to 4 of the blade lock's scene: the player stops (no movement left over,
	 * no item in use, no guard, dash or spin going on), she stands at the lock's distance in front
	 * of them on the line between them (or the player before her, where she has no room), and both
	 * face each other; the player's place and view are kept for the scene ({@link #holdForScene}).
	 * Returns where the player stands now (plot space).
	 */
	private static Vec3 settleForScene(StorySession s, Figure f, ServerPlayer player, ServerLevel level, Vec3 corner) {
		player.stopUsingItem();
		SaberAbilities.interrupt(player);
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		Vec3 me = player.position().subtract(corner);
		Vec3 dir = new Vec3(f.x - me.x, 0.0, f.z - me.z);

		if (dir.lengthSqr() < 1.0E-4) {
			Vec3 look = player.getLookAngle();
			dir = new Vec3(look.x, 0.0, look.z);
		}

		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
		double height = f.crew.height();
		// Phase 29: from wherever it happens (beside her, behind her, by a wall): she steps to the
		// nearest place before the player with room, floor and a clear view between them (the line
		// to her first, then turned further and further round the player); only where there is none
		// the player steps before her instead.
		boolean placed = false;

		for (double turn : new double[] {0.0, 25.0, -25.0, 50.0, -50.0, 80.0, -80.0, 115.0, -115.0, 150.0, -150.0, 180.0}) {
			double a = Math.toRadians(turn);
			Vec3 d = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0.0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
			Vec3 her = me.add(d.scale(LOCK_DISTANCE));

			if (Stealth.free(level, corner, her.x, f.y, her.z, height) && Hits.floor(level, corner, her.x, f.y, her.z)
					&& Stealth.clear(level, corner.add(me.x, me.y + 1.3, me.z), corner.add(her.x, f.y + 1.3, her.z))) {
				f.x = her.x;
				f.z = her.z;
				placed = true;
				scenePlaced[turn == 0.0 ? 0 : 1]++;
				break;
			}
		}

		if (!placed) {
			// No room for her there: the player steps before her instead (if there is room).
			Vec3 mine = f.position().subtract(dir.scale(LOCK_DISTANCE));
			Vec3 world = mine.add(corner);

			if (level.noCollision(player, player.getBoundingBox().move(world.x - player.getX(), 0.0, world.z - player.getZ()))
					&& Hits.floor(level, corner, mine.x, me.y, mine.z)) {
				player.connection.teleport(world.x, player.getY(), world.z, player.getYRot(), player.getXRot());
				me = new Vec3(mine.x, me.y, mine.z);
				scenePlaced[2]++;
			}
		}

		f.speed = 0.0F;
		f.yaw = Crew.yaw(f.position(), me);
		f.headYaw = f.yaw;
		Vec3 eye = player.getEyePosition();
		Vec3 at = f.position().add(corner).add(0.0, 1.5, 0.0);
		Vec3 d = at.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))), -30.0F, 30.0F);
		player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
		s.sceneAt = player.position();
		scenesSettled++;
		return me;
	}

	/**
	 * Phase 26: while the scene runs the player stays where it put them (the burst may throw them
	 * back: they land and are held there), with nothing in their hands going on.
	 */
	private static void holdForScene(StorySession s, ServerPlayer player, int t) {
		if (t > BOSS_LOCK && t < BOSS_LOCK + BURST_LAND) {
			// Thrown back by the burst: the flight is part of the scene.
			s.sceneAt = null;
			return;
		}

		if (s.sceneAt == null) {
			// Phase 29: held only where they stand on the floor: never in the air. Still flying, they
			// land first; if that takes too long, they are set down on the floor under them.
			if (!standing(player) && t < BOSS_LOCK + BURST_LAND + 24 && t < BOSS_FILM - 2) {
				return;
			}

			if (!standing(player) && player.level() instanceof ServerLevel level) {
				setDown(player, level);
			}

			s.sceneAt = player.position();
		}

		Vec3 at = s.sceneAt;

		if (player.isUsingItem()) {
			player.stopUsingItem();
		}

		if (player.position().distanceToSqr(at) > 0.0025 || player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
			player.setDeltaMovement(Vec3.ZERO);
			player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
			held++;
		}
	}

	private static int scenesSettled;
	private static int held;
	/** Phase 29, for the tests: the scene set her before the player on the line between them, turned round the player, or moved the player. */
	private static final int[] scenePlaced = new int[3];

	static int[] scenePlacedForTest() {
		return scenePlaced.clone();
	}

	/** Phase 26, for the tests: how often the scene's start set the two up, and how often the player was held in place. */
	static int[] sceneHoldsForTest() {
		return new int[] {scenesSettled, held};
	}

	/** Phase 24, only for tests: a light stroke hits her now (as from behind). */
	static void bossHitForTest(StorySession s, Figure f, ServerPlayer player) {
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		Vec3 me = player.position().subtract(corner);
		bossHit(s, f, player, me, (ServerLevel) player.level(), corner, f.position().add(corner).add(0.0, 1.2, 0.0), false, false);
	}

	/** Phase 24: how often the blade lock's scene began (for the tests). */
	private static int bossScenes;

	static int bossScenesForTest() {
		return bossScenes;
	}

	/**
	 * Phase 24: the blade lock's scene, each tick while it runs (the fight itself waits meanwhile:
	 * nobody strikes, nobody is hurt): the lock and its burst as before, then she stands, turns
	 * to the player and her second blade comes out; the player is turned towards her before the
	 * camera goes back into their eyes. When it ends (or is skipped) the second phase begins.
	 */
	static void bossScene(StorySession s, ServerPlayer player, ServerLevel level) {
		Figure f = null;

		for (Figure figure : s.figures) {
			if (figure.saber && figure.hostile) {
				f = figure;
			}
		}

		int left = s.timers.getOrDefault("boss_mid", 0);
		int t = BOSS_FILM - left;

		if (f == null || s.here == null) {
			endBossScene(s, null, player);
			return;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		holdForScene(s, player, t);
		Vec3 me = player.position().subtract(corner);

		if (f.action == LOCK) {
			lockTick(s, f, player, me, level, corner);
		} else {
			// Thrown back by the burst; then she stands and faces the player.
			Hits.tick(f, level, corner);
			f.speed = 0.0F;
			f.turnTowards(me, 8.0F, true);
			f.headYaw = Crew.yaw(f.position(), me);
		}

		if (t >= BOSS_REVEAL) {
			reveal(s, f, level, corner);
		}

		if (t == BOSS_TURN) {
			face(s, player, f, corner);
		}

		if (!s.timer("boss_mid")) {
			endBossScene(s, f, player);
		}
	}

	/** Phase 24: her second blade comes out (once). */
	private static void reveal(StorySession s, Figure f, ServerLevel level, Vec3 corner) {
		if (s.has("tmp.boss_reveal")) {
			return;
		}

		s.set("tmp.boss_reveal");
		Story.cue(s, Story.CUE_DUEL, "phase", "", 2);
		Vec3 at = f.position().add(corner).add(0.0, 1.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.ignite"), SoundSource.HOSTILE, 1.1F, 0.8F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.2, 0.3, 0.2, 0.2);
	}

	/** Phase 24: the player turned towards her (where the camera comes back to). */
	private static void face(StorySession s, ServerPlayer player, Figure f, Vec3 corner) {
		Vec3 eye = player.getEyePosition();
		Vec3 her = f.position().add(corner).add(0.0, 1.5, 0.0);
		Vec3 d = her.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, Mth.clamp(pitch, -30.0F, 30.0F));
	}

	/** Phase 24: the scene is over (played through or skipped): the second phase, as after the old lock. */
	static void endBossScene(StorySession s, @Nullable Figure f, ServerPlayer player) {
		if (!s.has(BOSS_SCENE)) {
			return;
		}

		s.unset(BOSS_SCENE);
		s.unset("tmp.film");
		s.timers.remove("boss_mid");

		if (f == null) {
			return;
		}

		ServerLevel level = player.level() instanceof ServerLevel l ? l : null;
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here != null ? s.here : Story.DESTROYER));
		Vec3 me = player.position().subtract(corner);

		if (f.action == LOCK && level != null) {
			// Skipped before the burst: it comes now.
			Vec3 between = new Vec3(f.x - me.x, 0.0, f.z - me.z);
			Vec3 dir = between.lengthSqr() < 1.0E-4 ? f.look().scale(-1.0) : between.normalize();
			lockEnds(s, f, player, me, level, corner, dir);
		}

		if (level != null) {
			reveal(s, f, level, corner);
		}

		f.react = 0;
		f.reactTicks = 0;
		f.lockScene = false;
		f.pushX = 0.0;
		f.pushZ = 0.0;
		act(f, GUARD, 0, 0);
		f.cooldown = 24;
		player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
		// Phase 26: step 6: the fight as before the scene, the player facing her (also when the
		// scene was skipped before its own turn towards her), nothing left over in their hands.
		player.stopUsingItem();
		SaberAbilities.interrupt(player);
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		face(s, player, f, corner);
		s.sceneAt = null;
		s.lastStroke = s.time();
		s.timer("stroke", 4);
		// Her push and leap come a little after the scene, not at once (Phase 25: and her throw;
		// Phase 26: and the ring's rush).
		s.timer("push", 70);
		s.timer("rush", 95);
		s.timer("leap", 130);
		s.timer("throw", 175);
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

		if (!f.lockScene) {
			s.hear("shadow", "sabercraft.story.shout.shadow_phase2");
			Story.cue(s, Story.CUE_DUEL, "phase", "", 2);
		}

		Story.cue(s, Story.CUE_DUEL, "lock_end", "", f.id, java.util.List.of(contact));
		// The push and the leap come soon after.
		s.timer("push", 60);
		s.timer("rush", 85);
		s.timer("leap", 110);
		s.timer("throw", 160);
		s.dirty = true;
	}

	/**
	 * Phase 23: the end of the duel. The last stroke strikes the saber from her hand: it flies off
	 * spinning, lands a few steps away, sputters and dies (the client draws its flight and where it
	 * lies; the sparks and the sound as it lands come from {@link Chapter1}). She stares at her empty
	 * hand: she has lost. Then she runs (the film of her flight). Nobody kills her: she escapes.
	 */
	private static void disarm(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		// Phase 29: the end begins cleanly whatever the player was doing: no guard, dash or swing
		// going on, no run left over (a jump still comes down: the film follows them to the floor),
		// and the guard's stricter rules end with the fight.
		player.stopUsingItem();
		SaberAbilities.interrupt(player);
		player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
		player.hurtMarked = true;
		SaberAbilities.strictGuard(player, null);
		s.unset(SCENE_PENDING);
		act(f, ESCAPE, STAGGER, 0);
		ringStops(f);
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
			case PUSH -> f.shove ? 31 * Math.max(0, f.timer - f.windup) / Math.max(1, f.timer) : 31 * (HAND_TICKS - f.windup) / HAND_TICKS;
			// Phase 29: her slam is shown as an overhead blow raised high (and the client's crouch from its reaction).
			case SLAM -> f.timer > 0 ? 31 * (f.timer - f.windup) / f.timer : 31;
			case OPEN_UP, ESCAPE -> 31;
			case RECOIL -> f.attack == SPIN ? 31 * (RECOIL_TICKS - Math.max(0, f.timer)) / RECOIL_TICKS : 0;
			case THROW -> 0;
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

	/** Phase 25, for the tests: she throws now. */
	static void throwForTest(StorySession s, Figure f) {
		ServerLevel level = Chapter1.interiors(s);

		if (level != null) {
			throwStarts(s, f, level, Vec3.atLowerCornerOf(s.origin(f.ship)), level.getRandom());
		}
	}

	/** Phase 25, for the tests: she spins now (a single blow). */
	static void spinForTest(StorySession s, Figure f) {
		ServerLevel level = Chapter1.interiors(s);

		if (level != null) {
			f.combo = 1;
			f.pattern = -1;
			blowStarts(f, SPIN, SPIN_WINDUP, level, s);
		}
	}

	/** Phase 25, for the tests: throws begun, hits, blocked, dodged; spins begun. */
	static int[] throwsForTest() {
		return new int[] {throwsStarted, throwHits, throwBlocks, throwDodges, spins};
	}

	/** Phase 28, for the tests: the ring set going, stopped; guards strained, broken. */
	static int[] ringAndGuardForTest() {
		return new int[] {ringStarted, ringStopped, strains, guardBreaks};
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
