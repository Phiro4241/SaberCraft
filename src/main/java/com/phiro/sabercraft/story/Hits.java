package com.phiro.sabercraft.story;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 23: what a hit does to the body. A trooper hit by the saber no longer just stands there
 * until the bar is empty: a light stroke makes him flinch and step back, a heavy one throws him
 * back with his arms out (he slides a good step and needs a moment to find his balance), several
 * quick strokes in a row put him off balance too, and the last one knocks him over (he falls,
 * then lies still). While he reels he neither aims nor shoots. Each kind of hit has its own sound
 * and sparks; the client plays the matching movement ({@link Figure#react}).
 *
 * <p>Being driven back slides the figure over a few ticks (slowing down), never through walls,
 * into someone else or off a ledge: a hit never throws anyone off a bridge.
 */
final class Hits {
	/** How fast a light and a heavy hit throw one back (blocks a tick at first), and how quickly that slows. */
	static final double LIGHT_PUSH = 0.13;
	static final double HEAVY_PUSH = 0.36;
	static final double FALL_PUSH = 0.3;
	static final double DRAG = 0.7;
	/** How long each reaction lasts (ticks). */
	static final int FLINCH_TICKS = 8;
	static final int HEAVY_TICKS = 18;
	static final int STUMBLE_TICKS = 24;
	static final int FALL_TICKS = 16;
	/** What a light and a heavy stroke take of a trooper (he has 1): four light strokes (as in Phase 22) or two heavy ones. */
	static final float LIGHT = Finale.STROKE_TROOPER;
	static final float HEAVY = 0.5F;
	/** Quick hits in a row: this many within this long (ticks) put him off balance. */
	static final int ROW = 3;
	static final int ROW_TICKS = 30;

	private Hits() {
	}

	/**
	 * One tick of what a hit does: the slide back, the reaction running out. Returns whether it
	 * still holds them (they do nothing else this tick).
	 */
	static boolean tick(Figure f, ServerLevel level, Vec3 corner) {
		slide(f, level, corner);

		if (f.reactTicks <= 0) {
			return false;
		}

		int kind = f.react;

		if (--f.reactTicks <= 0) {
			f.react = 0;
			f.reactTicks = 0;
		}

		return holds(kind);
	}

	/** Whether a reaction of that kind keeps one from doing anything else (a call over the comlink does not). */
	static boolean holds(int kind) {
		return switch (kind) {
			case Figure.FLINCH, Figure.HEAVY, Figure.STUMBLE, Figure.FALL, Figure.CLASH, Figure.DISARMED, Figure.LOCK, Figure.PARRY -> true;
			default -> false;
		};
	}

	/** Throws one back from a point (plot space) at that speed (blocks a tick at first). */
	static void push(Figure f, Vec3 from, double speed) {
		Vec3 away = new Vec3(f.x - from.x, 0.0, f.z - from.z);

		if (away.lengthSqr() < 1.0E-4) {
			away = f.look().scale(-1.0);
		}

		away = away.normalize();
		f.pushX = away.x * speed;
		f.pushZ = away.z * speed;
	}

	/** The slide back: as far as there is floor and room, slowing down each tick. */
	static void slide(Figure f, ServerLevel level, Vec3 corner) {
		if (Math.abs(f.pushX) < 0.005 && Math.abs(f.pushZ) < 0.005) {
			f.pushX = 0.0;
			f.pushZ = 0.0;
			return;
		}

		double height = f.crew.height();

		for (double k = 1.0; k > 0.2; k -= 0.4) {
			double nx = f.x + f.pushX * k;
			double nz = f.z + f.pushZ * k;

			if (Stealth.free(level, corner, nx, f.y, nz, height) && !Stealth.crowded(f, nx, f.y, nz) && floor(level, corner, nx, f.y, nz)) {
				f.x = nx;
				f.z = nz;
				break;
			}
		}

		f.pushX *= DRAG;
		f.pushZ *= DRAG;
		f.speed = 0.0F;
	}

	/** Whether there is floor under that spot (no more than a short step down): nobody is pushed off a ledge. */
	static boolean floor(ServerLevel level, Vec3 corner, double x, double y, double z) {
		Double stand = Paths.stand(level, corner, x, y + 0.5, z, 1.0);
		return stand != null && stand > y - 0.6 && stand < y + 0.6;
	}

	/**
	 * A saber stroke hits a trooper (or an officer): light or heavy. Returns whether it counted
	 * (a light stroke right after a hit only glances off the armour).
	 */
	static boolean saber(StorySession s, Figure f, boolean heavy, ServerLevel level, Vec3 corner, Vec3 player) {
		long now = s.time();
		Vec3 at = f.box().getCenter().add(corner);

		if (!heavy && now - f.hurtAt < Finale.HURT_TICKS) {
			// Glancing off the armour: sparks, a clank, nothing more.
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 5, 0.06, 0.08, 0.06, 0.15);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 0.35F, 1.4F);
			return false;
		}

		f.strokes = now - f.strokeAt < ROW_TICKS ? f.strokes + 1 : 1;
		f.strokeAt = now;
		f.hurtAt = now;
		f.health = Math.max(0.0F, f.health - (heavy ? HEAVY : LIGHT));
		f.windup = 0;
		f.burst = 0;
		f.speed = 0.0F;

		if (f.health <= 0.0F) {
			fall(s, f, player, level, at);
		} else if (heavy) {
			f.react(Figure.HEAVY, HEAVY_TICKS);
			push(f, player, HEAVY_PUSH);
			f.cooldown = Math.max(f.cooldown, HEAVY_TICKS + 10);
		} else if (f.strokes >= ROW) {
			// Hammered: off balance.
			f.react(Figure.STUMBLE, STUMBLE_TICKS);
			push(f, player, HEAVY_PUSH * 0.75);
			f.cooldown = Math.max(f.cooldown, STUMBLE_TICKS + 8);
			f.strokes = 0;
		} else {
			f.react(Figure.FLINCH, FLINCH_TICKS);
			push(f, player, LIGHT_PUSH);
			f.cooldown = Math.max(f.cooldown, FLINCH_TICKS + 12);
		}

		// The burn of the blade on the armour: sparks and smoke; a heavy one with a flash and a deeper sound.
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, heavy ? 10 : 5, 0.1, 0.2, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, heavy ? 22 : 10, 0.1, 0.12, 0.1, heavy ? 0.4 : 0.25);

		if (heavy) {
			level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE0C0), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}

		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, heavy ? 1.1F : 0.85F, heavy ? 0.8F : 1.05F);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.armour_hit"), SoundSource.HOSTILE, heavy ? 1.0F : 0.6F, heavy ? 0.9F : 1.15F);
		Story.cue(s, Story.CUE_DUEL, heavy || f.health <= 0.0F ? "hit_heavy" : "hit", "", f.id);
		s.counters.merge(heavy ? "heavy_hits" : "light_hits", 1, Integer::sum);
		s.dirty = true;
		return true;
	}

	/** A bolt sent back hits a trooper: a heavy jolt (it burns), or the last one. */
	static void bolt(StorySession s, Figure f, float amount, ServerLevel level, Vec3 corner, Vec3 from) {
		Vec3 at = f.box().getCenter().add(corner);
		f.hurtAt = s.time();
		f.health = Math.max(0.0F, f.health - amount);
		f.windup = 0;
		f.burst = 0;

		if (f.health <= 0.0F) {
			fall(s, f, from, level, at);
		} else {
			f.react(Figure.HEAVY, HEAVY_TICKS - 4);
			push(f, from, HEAVY_PUSH * 0.6);
			f.cooldown = Math.max(f.cooldown, HEAVY_TICKS + 6);
		}

		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.1, 0.2, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.08, 0.1, 0.08, 0.3);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("blaster.impact"), SoundSource.HOSTILE, 1.0F, 0.9F);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.armour_hit"), SoundSource.HOSTILE, 0.8F, 1.0F);
		Story.cue(s, Story.CUE_DUEL, "hit_heavy", "", f.id);
		s.dirty = true;
	}

	/** The last hit: thrown back and over, then lying still. */
	static void fall(StorySession s, Figure f, Vec3 from, ServerLevel level, Vec3 at) {
		f.mode = Figure.DOWN;
		f.speed = 0.0F;
		f.windup = 0;
		f.react(Figure.FALL, FALL_TICKS);
		push(f, from, FALL_PUSH);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.body_fall"), SoundSource.HOSTILE, 0.9F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		s.counters.merge("downed", 1, Integer::sum);
		s.dirty = true;
	}

	/** Phase 23: figures lying down still slide and finish falling (their reaction runs out). */
	static void settle(StorySession s, ServerLevel level, Vec3 corner) {
		for (Figure f : s.figures) {
			if (f.mode == Figure.DOWN && (f.reactTicks > 0 || f.pushX != 0.0 || f.pushZ != 0.0) && s.here != null && s.here.equals(f.ship)) {
				tick(f, level, corner);
			}
		}
	}
}
