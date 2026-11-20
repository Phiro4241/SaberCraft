package com.phiro.sabercraft.story;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 25: the flight of the Schattenklinge's thrown saber staff, the same on the server (where
 * it hits) and on the client (where it is drawn): out of her hand at the player, slowing towards
 * the far end, then back, quicker and quicker, into her hand. It rises a little on the way out
 * and sinks a little on the way back, spinning all the time.
 *
 * <p>Phase 29: the low throw (thrown from low down, skimming at shin height: jumped over) comes
 * back in a high arc over the player's head into her hand, not along the floor into their legs
 * from behind: jumped over once, it has been escaped (a fair move, not a trap from behind).
 */
public final class StaffThrow {
	/** How fast it spins (degrees a tick). */
	public static final float SPIN = 47.0F;
	/** Phase 29: how high (blocks) the low throw's way back goes over the straight line to her hand. */
	public static final double LOW_ARC = 2.4;

	private StaffThrow() {
	}

	/**
	 * Where it is {@code t} ticks after it left her hand: from {@code start} out to {@code far} in
	 * {@code out} ticks, back to {@code hand} in {@code back} ticks (at the hand from then on).
	 */
	public static Vec3 at(Vec3 start, Vec3 far, Vec3 hand, int out, int back, float t) {
		if (t <= out) {
			float u = Mth.clamp(t / Math.max(1, out), 0.0F, 1.0F);
			float e = 1.0F - (1.0F - u) * (1.0F - u);
			return start.lerp(far, e).add(0.0, Math.sin(u * Math.PI) * 0.25, 0.0);
		}

		float u = Mth.clamp((t - out) / Math.max(1, back), 0.0F, 1.0F);
		float e = u * u;

		if (low(start, hand)) {
			// Pulled up off the floor at once (its first sixth of the way back), high over the player,
			// down into her hand at the end (its last fifth).
			float rise = u < 0.15F ? smooth(u / 0.15F) : u > 0.8F ? smooth((1.0F - u) / 0.2F) : 1.0F;
			return far.lerp(hand, e).add(0.0, rise * LOW_ARC, 0.0);
		}

		return far.lerp(hand, e).add(0.0, -Math.sin(u * Math.PI) * 0.15, 0.0);
	}

	private static float smooth(float x) {
		float c = Mth.clamp(x, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** Phase 29: whether a throw is the low one: let go well below the hand it comes back to (the high one at the hand's height). */
	public static boolean low(Vec3 start, Vec3 hand) {
		return hand.y - start.y > 0.5;
	}

	/** Which way it flies at {@code t} (yaw, degrees, as the game's), for drawing. */
	public static float yaw(Vec3 start, Vec3 far, Vec3 hand, int out, int back, float t) {
		Vec3 a = at(start, far, hand, out, back, Math.max(0.0F, t - 0.5F));
		Vec3 b = at(start, far, hand, out, back, t + 0.5F);
		Vec3 d = b.subtract(a);

		if (d.horizontalDistanceSqr() < 1.0E-6) {
			d = far.subtract(start);
		}

		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}
}
