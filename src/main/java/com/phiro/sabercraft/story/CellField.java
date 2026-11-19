package com.phiro.sabercraft.story;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;

/**
 * Phase 26: M-31 takes down the field of the cell Hale's trap left the player in, as a small
 * scene of its own instead of the field vanishing in the same second as the droid key:
 *
 * <ol>
 * <li>M-31 drives from the bench to the end of the field's floor emitter, outside the cell;</li>
 * <li>it sets to work there (it jolts at the emitter, servos whir, sparks fly);</li>
 * <li>the field flickers and stutters, its hum breaks up;</li>
 * <li>a moment later it dies, and the way is free.</li>
 * </ol>
 *
 * The marks it uses start with {@code tmp.} (never saved): caught in the middle of it, one is back
 * in the cell with the field on and M-31 at the bench, as before.
 */
final class CellField {
	private CellField() {
	}

	/** Where M-31 sets to work: outside the cell, at the east end of the field's floor emitter. */
	static final Vec3 PANEL = new Vec3(33.35, Chapter1.H, 3.2);
	/** Where the sparks fly (the emitter's end, low). */
	static final Vec3 EMITTER = new Vec3(32.85, Chapter1.H + 0.15, 2.62);
	/** At most so long (ticks) on the way to the emitter (it is put there if it cannot drive). */
	static final int GO_TICKS = 100;
	/** How long it works before the field fails (ticks): the flicker grows over this time. */
	static final int WORK_TICKS = 56;

	static final String GOING = "tmp.field_go";
	static final String WORKING = "tmp.field_work";

	/** Whether the field is being taken down now (M-31 on its way or at work). */
	static boolean running(StorySession s) {
		return s.has(GOING) || s.has(WORKING);
	}

	/** The droid key in the cell: M-31 sets off for the emitter. Returns the droid's message. */
	static String start(StorySession s, ServerPlayer player) {
		if (running(s)) {
			return "sabercraft.story.droid.m31_busy";
		}

		s.set(GOING);
		s.timer("field_go", GO_TICKS);
		DroidEntity mouse = Droids.droid(player, DroidType.MOUSE);

		if (mouse != null) {
			mouse.setStaying(false);

			if (!mouse.startErrand(PANEL.add(Vec3.atLowerCornerOf(s.destroyer)), GO_TICKS)) {
				// No way there (it never happened in the tests): it is at the emitter at once.
				put(s, mouse);
			}
		}

		Story.cue(s, Story.CUE_SOUND, "", "story.m31_rattle", 0);
		started++;
		return "sabercraft.story.droid.m31_field_go";
	}

	/** Every tick of the cell's step: the way there, the work, the end. */
	static void tick(StorySession s, ServerLevel level) {
		ServerPlayer player = s.player();
		DroidEntity mouse = player != null ? Droids.droid(player, DroidType.MOUSE) : null;

		if (s.has(GOING)) {
			boolean there = mouse == null || mouse.errand() == DroidEntity.ERRAND_THERE || mouse.errand() == DroidEntity.ERRAND_FAILED;

			if (!there && !Chapter1.fired(s, "field_go")) {
				return;
			}

			if (mouse != null && mouse.position().distanceTo(PANEL.add(Vec3.atLowerCornerOf(s.destroyer))) > 1.6) {
				put(s, mouse);
			}

			s.flags.remove(GOING);
			s.timers.remove("field_go");
			s.set(WORKING);
			s.timer("field_work", WORK_TICKS);
			Vec3 emitter = EMITTER.add(Vec3.atLowerCornerOf(s.destroyer));
			// The client: M-31 at work, the field stuttering (it draws the flicker), the sounds.
			Story.cue(s, Story.CUE_EFFECT, "field_flicker", "", WORK_TICKS, List.of(emitter));
			Story.cue(s, Story.CUE_SOUND, "", "story.droid_work", 0);

			if (mouse != null) {
				mouse.getLookControl().setLookAt(emitter);
				Droids.working(mouse);
			}

			return;
		}

		if (!s.has(WORKING)) {
			return;
		}

		int left = s.timers.getOrDefault("field_work", 0);
		int age = WORK_TICKS - left;
		Vec3 emitter = EMITTER.add(Vec3.atLowerCornerOf(s.destroyer));

		if (mouse != null) {
			mouse.getLookControl().setLookAt(emitter);

			if (age > 0 && age % 26 == 0) {
				Droids.working(mouse);
			}
		}

		// Sparks at the emitter, more and more as the field fails.
		if (age % 3 == 0 && level.getRandom().nextFloat() < 0.35F + 0.6F * age / WORK_TICKS) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, emitter.x, emitter.y, emitter.z, 2 + age / 12, 0.08, 0.06, 0.08, 0.15);
		}

		if (age == WORK_TICKS / 2) {
			Story.cue(s, Story.CUE_SOUND, "", "story.field_flicker", 0);
		}

		if (!Chapter1.fired(s, "field_work")) {
			return;
		}

		s.flags.remove(WORKING);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, emitter.x, emitter.y + 0.4, emitter.z, 18, 0.25, 0.5, 0.1, 0.3);
		level.sendParticles(ParticleTypes.SMOKE, emitter.x, emitter.y + 0.1, emitter.z, 6, 0.1, 0.05, 0.1, 0.01);
		s.set("cell2.open");
		Chapter1.field(s, 1, true);
		Story.cue(s, Story.CUE_SOUND, "", "story.field_off", 0);
		s.hint("sabercraft.story.hint.cell_open");

		if (mouse != null) {
			mouse.endErrand();
			mouse.setStaying(false);
		}

		finished++;
		s.dirty = true;
	}

	/** Puts M-31 at the emitter (no way to drive there, or not there in time). */
	private static void put(StorySession s, DroidEntity mouse) {
		Vec3 at = PANEL.add(Vec3.atLowerCornerOf(s.destroyer));
		mouse.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mouse.getNavigation().stop();
	}

	private static int started;
	private static int finished;

	/** For tests: how often the field's taking down began, and ended with the field off. */
	static int startedForTest() {
		return started;
	}

	static int finishedForTest() {
		return finished;
	}
}
