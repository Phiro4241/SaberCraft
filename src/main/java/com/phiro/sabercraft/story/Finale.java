package com.phiro.sabercraft.story;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 19: the fight at the end of Kapitel 1. With Ilyra's saber lit the stealth rules end: the
 * troops shoot (real blaster bolts, which the saber sends back as everywhere in SaberCraft: in the
 * guard stance, with a swing); a bolt sent back or a stroke of the saber brings a trooper down.
 *
 * <p>The Schattenklinge fights with a red saber: he closes in, winds up a blow (one sees the
 * blade go up) and strikes. Blocked (the guard, facing him), the blow leaves him open for a
 * moment; a stroke then (or from behind) hits. Otherwise he parries, and sometimes strikes back
 * at once. Below sixty percent he gets faster and pushes the player away with the Force. At
 * fifteen percent he breaks off: the Falcon comes.
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
	/** A saber stroke: how far it reaches, and what it takes of a trooper. */
	static final double STROKE_REACH = 3.6;
	static final float STROKE_TROOPER = 0.55F;
	/** The duel: the wind-up of a blow (ticks; second phase), the open moment after it, a hit on him, the end. */
	static final int WINDUP = 13;
	static final int WINDUP_FAST = 9;
	static final int OPEN = 18;
	static final float HIT = 0.11F;
	static final float SECOND_PHASE = 0.6F;
	static final float BREAKS_OFF = 0.15F;
	static final float BLOW = 4.0F;
	static final float BLOW_FAST = 5.0F;
	static final int PUSH_EVERY = 160;

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

		Vec3 corner = Vec3.atLowerCornerOf(s.origin(ship));
		Vec3 me = player.position().subtract(corner);

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

			if (s.completing()) {
				return;
			}
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

			figure.headYaw = com.phiro.sabercraft.interior.Crew.yaw(figure.position(), me);

			if (--figure.cooldown <= 0) {
				shoot(figure, player, level, corner);
				figure.cooldown = SHOT_MIN + player.getRandom().nextInt(SHOT_RANDOM);
			}
		} else {
			// Out of sight: they come to where the player is (in a fight everyone knows).
			if (!Stealth.step(figure, me, Stealth.WALK, level, corner)) {
				figure.speed = 0.0F;
			}

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
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ShipInteriors.sound("blaster.e11"), SoundSource.HOSTILE, 0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
	}

	/** Bolts the player sent back: a trooper they hit goes down (the Schattenklinge turns them aside). */
	private static void bolts(StorySession s, ServerPlayer player, ServerLevel level, Vec3 corner) {
		AABB area = player.getBoundingBox().inflate(40.0);

		for (BlasterBolt bolt : level.getEntitiesOfClass(BlasterBolt.class, area, b -> b.isAlive() && b.getOwner() == player)) {
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
						hurt(s, figure, 1.0F, level, at);
					}

					bolt.discard();
					break;
				}
			}
		}
	}

	/** A fighter is hit: down when nothing is left. */
	private static void hurt(StorySession s, Figure figure, float amount, ServerLevel level, Vec3 at) {
		figure.health = Math.max(0.0F, figure.health - amount);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.1, 0.2, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.2);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 0.9F, 1.0F);

		if (figure.health <= 0.0F) {
			figure.mode = Figure.DOWN;
			figure.speed = 0.0F;
			figure.windup = 0;
		} else {
			figure.cooldown = Math.max(figure.cooldown, 16);
		}

		s.dirty = true;
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

		Vec3 at = best.box().getCenter().add(corner);

		if (!best.saber) {
			hurt(s, best, STROKE_TROOPER, level, at);
			return;
		}

		// The Schattenklinge: open after a blow, or struck from behind, or caught early in his wind-up.
		Vec3 toPlayer = new Vec3(eye.x - best.x, 0.0, eye.z - best.z).normalize();
		boolean behind = toPlayer.dot(best.look()) < -0.35;

		if (best.timer > 0 || behind || best.windup > WINDUP_FAST) {
			best.timer = 0;
			best.windup = 0;
			best.cooldown = Math.max(best.cooldown, 14);
			hurt(s, best, HIT, level, at);
			best.mode = Figure.FIGHT;

			if (best.health <= BREAKS_OFF) {
				breaksOff(s, best);
			}
		} else {
			// Parried.
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 10, 0.1, 0.1, 0.1, 0.35);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);

			if (player.getRandom().nextFloat() < 0.35F && best.windup == 0) {
				best.windup = 6;
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
		shadow.mode = Figure.FIGHT;
		shadow.cooldown = 30;
		shadow.windup = 0;
		shadow.timer = 0;
		s.fighting = true;
		s.set("tmp.duel");
		s.timer("push", PUSH_EVERY);
		Chapter1.music(s, s.mission, s.step().id());
		Story.cue(s, Story.CUE_SOUND, "", "saber.ignite", shadow.id);
		s.dirty = true;
	}

	private static void duel(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner) {
		boolean second = f.health <= SECOND_PHASE;
		double distance = Math.hypot(me.x - f.x, me.z - f.z);
		f.turnTowards(me, 16.0F, true);
		f.headYaw = com.phiro.sabercraft.interior.Crew.yaw(f.position(), me);

		if (f.timer > 0) {
			f.timer--;
		}

		if (f.windup > 0) {
			f.speed = 0.0F;

			if (--f.windup == 0) {
				blow(s, f, player, me, level, corner, second);
			}

			return;
		}

		if (f.cooldown > 0) {
			f.cooldown--;

			if (distance > 2.6) {
				Stealth.step(f, me, Stealth.WALK * 1.3, level, corner);
			} else if (distance < 1.4) {
				Vec3 away = f.position().add(f.position().subtract(me).normalize());
				Stealth.step(f, away, Stealth.WALK, level, corner);
			} else {
				f.speed = 0.0F;
			}

			return;
		}

		// The Force: in the second phase, now and then, a push from a few blocks away.
		if (second && !s.timer("push") && distance > 2.0 && distance < 8.0 && Stealth.clear(level, corner.add(f.eye()), player.getEyePosition())) {
			push(s, f, player, level, corner);
			s.timer("push", PUSH_EVERY);
			f.cooldown = 24;
			return;
		}

		if (distance <= 2.7) {
			f.windup = second ? WINDUP_FAST : WINDUP;
			Vec3 at = f.position().add(corner);
			level.playSound(null, at.x, at.y + 1.2, at.z, ShipInteriors.sound("saber.swing"), SoundSource.HOSTILE, 0.9F, 0.8F);
		} else {
			Stealth.step(f, me, 3.8, level, corner);
		}
	}

	/** His blow falls: blocked (guard, facing him), it leaves him open; otherwise it hurts. */
	private static void blow(StorySession s, Figure f, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, boolean second) {
		double distance = Math.hypot(me.x - f.x, me.z - f.z);
		Vec3 at = f.position().add(corner).add(f.look().scale(0.9)).add(0.0, 1.3, 0.0);

		if (distance > 3.2) {
			// Missed: the player stepped back. He is open.
			f.timer = OPEN + 4;
			f.cooldown = 26;
			return;
		}

		Vec3 toHim = new Vec3(f.x - me.x, 0.0, f.z - me.z).normalize();
		Vec3 look = player.getLookAngle();
		boolean facing = new Vec3(look.x, 0.0, look.z).normalize().dot(toHim) > 0.35;
		boolean guard = SaberAbilities.isGuarding(player) || player.isUsingItem() && SaberVariant.canHold(player.getUseItem());

		if (guard && facing) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.1, 0.1, 0.1, 0.4);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.clash"), SoundSource.HOSTILE, 1.1F, 0.85F + player.getRandom().nextFloat() * 0.15F);
			f.timer = OPEN;
			f.cooldown = second ? 18 : 26;
			s.counters.merge("blocked", 1, Integer::sum);
		} else {
			player.hurtServer(level, level.damageSources().generic(), second ? BLOW_FAST : BLOW);
			Vec3 push = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize().scale(0.6);
			player.push(push.x, 0.25, push.z);
			player.hurtMarked = true;
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.hit"), SoundSource.HOSTILE, 1.0F, 0.9F);
			f.timer = 0;
			f.cooldown = second ? 16 : 24;
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
		}

		Vec3 at = f.position().add(corner).add(0.0, 1.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("saber.push"), SoundSource.HOSTILE, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CLOUD, at.x + away.x, at.y, at.z + away.z, 12, 0.3, 0.3, 0.3, 0.05);
		s.hint("sabercraft.story.hint.push");
	}

	/** At the end of his strength he breaks off: the Falcon is coming. */
	private static void breaksOff(StorySession s, Figure f) {
		f.hostile = false;
		f.mode = Figure.HELD;
		f.windup = 0;
		f.timer = 0;
		f.speed = 0.0F;
		ServerPlayer player = s.player();
		f.target = player != null && s.here != null ? s.local(s.here) : null;
		s.fighting = false;
		Story.cue(s, Story.CUE_SOUND, "", "saber.retract", f.id);
		Story.cue(s, Story.CUE_MUSIC, "", "music.event_rise", 0);
		s.radio("shadow", "sabercraft.story.radio.shadow_retreat");
		s.radio("dax", "sabercraft.story.radio.dax_arrives");
		s.complete();
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
}
