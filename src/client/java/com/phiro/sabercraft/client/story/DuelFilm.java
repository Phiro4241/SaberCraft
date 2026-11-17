package com.phiro.sabercraft.client.story;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.ArrivalFilm;

/**
 * Phase 21: the two films of the duel, shot where it happens (in the hangar of the Star
 * Destroyer, with the people of the story as they stand there; the server moves them).
 *
 * <p><b>Before the duel</b> ({@link #LENGTH}): behind the player, the Schattenklinge with his back
 * to them before the force field; closer on him as he turns round; low, as his blade lights (the
 * title); over his shoulder onto the player. <b>His flight</b> ({@link #END_LENGTH}): both from the
 * side as he staggers and puts out his blade; over the player's shoulder as he runs for the door;
 * the player in front, as the comlink calls.
 *
 * <p>Every shot is placed on the line from the player to him as it was when the film began, so the
 * camera does not swing about when he moves.
 */
final class DuelFilm {
	static final int LENGTH = 220;
	static final int END_LENGTH = 130;

	private DuelFilm() {
	}

	/**
	 * The camera at a moment of the film.
	 *
	 * @param player where the player stands (feet, world space)
	 * @param start where he stood when the film began (feet)
	 * @param shadow where he is now (feet)
	 * @param axis the way from the player to him at the start (level, length 1)
	 */
	static ArrivalFilm.Shot camera(String film, float t, Vec3 player, Vec3 start, Vec3 shadow, Vec3 axis) {
		Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);

		if ("duel_end".equals(film)) {
			if (t < 40.0F) {
				float u = ease(t / 40.0F);
				Vec3 middle = player.add(start).scale(0.5);
				Vec3 cam = middle.add(side.scale(6.2 - u * 0.8)).add(up.scale(1.9)).subtract(axis.scale(0.5));
				return look(cam, middle.add(up.scale(1.2)));
			}

			if (t < 100.0F) {
				Vec3 cam = player.add(up.scale(2.0)).subtract(axis.scale(0.7)).add(side.scale(0.65));
				return look(cam, shadow.add(up.scale(1.2)));
			}

			float u = ease((t - 100.0F) / 30.0F);
			Vec3 cam = player.add(axis.scale(2.9 - u * 0.5)).add(up.scale(1.75)).add(side.scale(0.4));
			return look(cam, player.add(up.scale(1.5)));
		}

		if (t < 70.0F) {
			// Behind the player: he stands there, his back to them.
			float u = ease(t / 70.0F);
			Vec3 from = player.subtract(axis.scale(5.5)).add(up.scale(2.6)).add(side.scale(1.8));
			Vec3 to = player.subtract(axis.scale(3.2)).add(up.scale(2.1)).add(side.scale(1.1));
			return look(from.lerp(to, u), shadow.add(up.scale(1.4)));
		}

		if (t < 140.0F) {
			// Closer on him as he turns round.
			float u = ease((t - 70.0F) / 70.0F);
			Vec3 cam = shadow.subtract(axis.scale(3.1 - u * 0.5)).add(side.scale(1.3 - u * 0.9)).add(up.scale(1.7));
			return look(cam, shadow.add(up.scale(1.55)));
		}

		if (t < 178.0F) {
			// Low, as the blade lights.
			float u = ease((t - 140.0F) / 38.0F);
			Vec3 cam = shadow.subtract(axis.scale(2.0)).subtract(side.scale(1.0)).add(up.scale(0.45 + u * 0.15));
			return look(cam, shadow.add(up.scale(1.45 + u * 0.3)));
		}

		// Over his shoulder onto the player.
		float u = ease((t - 178.0F) / 42.0F);
		Vec3 cam = shadow.add(axis.scale(1.6 - u * 0.3)).add(side.scale(0.75)).add(up.scale(2.0));
		return look(cam, player.add(up.scale(1.35)));
	}

	/** A camera at that point looking at another. */
	static ArrivalFilm.Shot look(Vec3 cam, Vec3 at) {
		Vec3 d = at.subtract(cam);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
		return new ArrivalFilm.Shot(cam, yaw, pitch);
	}

	private static float ease(float u) {
		float x = Mth.clamp(u, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
