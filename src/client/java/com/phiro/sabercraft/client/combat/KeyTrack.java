package com.phiro.sabercraft.client.combat;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One animated value of a move (for example the grip position or the main arm angles), given by
 * keyframes. Between the keyframes the value follows a Catmull-Rom curve, so the movement runs
 * smoothly through every keyframe instead of changing direction abruptly. Before the first and
 * after the last keyframe the value holds still.
 *
 * <p>{@code tools/preview_moves.py} uses the same curve to draw the moves.
 */
public final class KeyTrack {
	private final float[] times;
	private final float[][] values;
	private final int dimensions;

	private KeyTrack(float[] times, float[][] values, int dimensions) {
		this.times = times;
		this.values = values;
		this.dimensions = dimensions;
	}

	/** Builds a track from (time, value) pairs sorted by time, or returns {@code null} for none. */
	public static @Nullable KeyTrack of(List<Float> times, List<float[]> values, int dimensions) {
		if (times.isEmpty()) {
			return null;
		}

		float[] t = new float[times.size()];
		float[][] v = new float[times.size()][];

		for (int i = 0; i < t.length; i++) {
			t[i] = times.get(i);
			v[i] = values.get(i);

			if (v[i].length != dimensions) {
				throw new IllegalArgumentException("Expected " + dimensions + " values, found " + v[i].length);
			}

			if (i > 0 && t[i] <= t[i - 1]) {
				throw new IllegalArgumentException("Keyframe times must increase: " + t[i - 1] + " then " + t[i]);
			}
		}

		return new KeyTrack(t, v, dimensions);
	}

	public int dimensions() {
		return this.dimensions;
	}

	public float firstTime() {
		return this.times[0];
	}

	public float lastTime() {
		return this.times[this.times.length - 1];
	}

	/** Writes the value at {@code time} (in ticks since the move started) into {@code out}. */
	public void sample(float time, float[] out) {
		int last = this.times.length - 1;

		if (time <= this.times[0]) {
			System.arraycopy(this.values[0], 0, out, 0, this.dimensions);
			return;
		}

		if (time >= this.times[last]) {
			System.arraycopy(this.values[last], 0, out, 0, this.dimensions);
			return;
		}

		int i = 0;

		while (time > this.times[i + 1]) {
			i++;
		}

		float t = (time - this.times[i]) / (this.times[i + 1] - this.times[i]);
		float[] p0 = this.values[Math.max(0, i - 1)];
		float[] p1 = this.values[i];
		float[] p2 = this.values[i + 1];
		float[] p3 = this.values[Math.min(last, i + 2)];
		float t2 = t * t;
		float t3 = t2 * t;

		for (int d = 0; d < this.dimensions; d++) {
			out[d] = 0.5F * (2.0F * p1[d]
					+ (-p0[d] + p2[d]) * t
					+ (2.0F * p0[d] - 5.0F * p1[d] + 4.0F * p2[d] - p3[d]) * t2
					+ (-p0[d] + 3.0F * p1[d] - 3.0F * p2[d] + p3[d]) * t3);
		}
	}
}
