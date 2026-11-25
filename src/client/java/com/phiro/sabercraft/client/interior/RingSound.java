package com.phiro.sabercraft.client.interior;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.story.StoryCinema;

/**
 * Phase 32: the whirr of an Inquisitor's ring, heard exactly while it is seen to turn. One loop
 * per figure at most; each frame the ring is drawn its speed is handed over here, and the loop
 * follows it: it starts as the ring spins up, gets louder and higher the faster it turns, and
 * fades out as it slows; it stops at once when a film begins or the fight is reset, and by itself
 * when the figure is no longer drawn (gone, out of sight in another plot, the story left).
 *
 * <p>Before, the whirr was a sound the server played once at the start of some moves: it went on
 * when the ring did not turn (her slam, her rush with one blade), stopped while the ring still
 * turned, and could outlast a reset, a capture or a scene.
 */
public final class RingSound {
	/** The ring's speed (radians a second) from which it is heard, and at which it is loudest. */
	static final float HEARD_FROM = 4.0F;
	static final float LOUDEST = InquisitorSaber.SPIN_FAST;
	/** How loud the loop gets at the ring's full speed (and at its steady turning, the ring's mode: less). */
	static final float VOLUME = 0.9F;
	/** Ticks without a frame of the ring drawn after which its loop fades out (not drawn: not seen turning). */
	static final int UNSEEN_TICKS = 4;

	private static final Map<Integer, Loop> LOOPS = new HashMap<>();
	private static int started;

	private RingSound() {
	}

	/**
	 * Each frame the ring is drawn: how fast it turns (radians a second; 0 for a ring that cannot
	 * turn now: one blade, her staff away, unlit), and where it is (world space).
	 */
	static void heard(int id, float speed, double x, double y, double z) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || StoryCinema.active()) {
			return;
		}

		float fast = Math.abs(speed);
		Loop loop = LOOPS.get(id);

		if (loop != null && loop.isStopped()) {
			LOOPS.remove(id);
			loop = null;
		}

		if (loop == null) {
			if (fast < HEARD_FROM) {
				return;
			}

			loop = new Loop(id);
			LOOPS.put(id, loop);
			started++;
			client.getSoundManager().play(loop);
		}

		loop.speed = fast;
		loop.seen = client.level.getGameTime();
		loop.at(x, y, z);
	}

	/** Every loop stops at once (a film begins, the duel is reset, the story left). */
	public static void stopAll() {
		Minecraft client = Minecraft.getInstance();

		for (Loop loop : LOOPS.values()) {
			loop.end();
			client.getSoundManager().stop(loop);
		}

		LOOPS.clear();
	}

	/** Phase 32, for the tests: loops playing now (not stopped). */
	public static int playingForTest() {
		int playing = 0;

		for (Loop loop : LOOPS.values()) {
			if (!loop.isStopped()) {
				playing++;
			}
		}

		return playing;
	}

	/** Phase 32, for the tests: loops begun in all. */
	public static int startedForTest() {
		return started;
	}

	/** Phase 32, for the tests: how loud the loop of that figure is now (0 without one). */
	public static float volumeForTest(int id) {
		Loop loop = LOOPS.get(id);
		return loop == null || loop.isStopped() ? 0.0F : loop.getVolume();
	}

	private static final class Loop extends AbstractTickableSoundInstance {
		float speed;
		long seen;

		Loop(int id) {
			super(SoundEvent.createVariableRangeEvent(SaberCraft.id("story.ring_loop")), SoundSource.HOSTILE, RandomSource.create(id * 131L));
			this.looping = true;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.volume = 0.0F;
			this.pitch = 0.8F;
			this.seen = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
		}

		@Override
		public void tick() {
			Minecraft client = Minecraft.getInstance();
			boolean seenNow = client.level != null && client.level.getGameTime() - this.seen <= UNSEEN_TICKS;
			float share = Mth.clamp((this.speed - HEARD_FROM) / (LOUDEST - HEARD_FROM), 0.0F, 1.0F);
			// (Smooth: a slow ring quietly, the spin at full speed loud; heard down to a little below
			// the speed it began at, so that it does not flicker on and off there.)
			boolean turning = this.speed >= HEARD_FROM * 0.75F;
			float target = seenNow && turning && !StoryCinema.active() ? VOLUME * (0.15F + 0.85F * share * share * (3.0F - 2.0F * share)) : 0.0F;

			if (target > this.volume) {
				this.volume = Math.min(target, this.volume + 0.18F);
			} else {
				this.volume = Math.max(target, this.volume - (seenNow ? 0.12F : 0.3F));
			}

			this.pitch = 0.72F + 0.38F * Mth.clamp(this.speed / LOUDEST, 0.0F, 1.2F);

			if (this.volume <= 0.001F && target <= 0.0F) {
				this.stop();
			}
		}

		void at(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		void end() {
			this.stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
