package com.phiro.sabercraft.client.story;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.phiro.sabercraft.client.interior.ArrivalFilm;
import com.phiro.sabercraft.client.interior.EscapeFilm;

/**
 * Phase 21: the two films of the duel, shot where it happens (in the hangar of the Star
 * Destroyer, with the people of the story as they stand there; the server moves them).
 *
 * <p><b>Before the duel</b> ({@link #LENGTH}): behind the player, the Schattenklinge with his back
 * to them before the force field; closer on him as he turns round; low, as his blade lights (the
 * title); over his shoulder onto the player. <b>Her defeat</b> ({@link #END_LENGTH}, Phase 23): from
 * the side as her saber is struck from her hand and flies off spinning; low by the hilt as it lands,
 * sputters and dies; on her as she stares at her empty hand and up at the player; over the player's
 * shoulder as she runs for the door; the player in front, as the comlink calls.
 *
 * <p>Every shot is placed on the line from the player to him as it was when the film began, so the
 * camera does not swing about when he moves.
 *
 * <p>Phase 24: her flight goes on to the TIE fighter parked in the hangar (she runs, leaps up into
 * it, it lifts off, glides over the field and drops through it; a look from outside, see
 * {@link EscapeFilm#tieCamera}; back to the player). The blade lock at half her strength
 * ({@link #BOSS_LENGTH}): out of the player's eyes to the side, round the crossed blades, close on
 * her, wide as the burst throws them apart, low on her as the second blade of her staff comes out,
 * and smoothly back into the player's eyes. The guard overpowered ({@link #TAKEDOWN_LENGTH}).
 */
final class DuelFilm {
	static final int LENGTH = 220;
	/** Phase 24: her flight, now to the TIE (as the server's {@code DUEL_END_FILM}). */
	static final int END_LENGTH = 346;
	/** Phase 24: her flight's moments: she runs, reaches the TIE and leaps up, the look outside, back. */
	static final int RUN = 96;
	static final int AT_TIE = 150;
	static final int BOARD = 172;
	static final int TIE_OUT = EscapeFilm.TIE_GONE;
	static final int TIE_BACK = TIE_OUT + EscapeFilm.TIE_SHOT;
	/** Phase 24: the blade lock's film (as the server's {@code Finale.BOSS_FILM}) and its moments. */
	static final int BOSS_LENGTH = 150;
	static final int BOSS_LOCK = 86;
	static final int BOSS_REVEAL = 104;
	static final int BOSS_RETURN = 128;
	/** Phase 24: the guard overpowered (as the server's {@code TAKEDOWN_FILM}). */
	static final int TAKEDOWN_LENGTH = 76;

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
	static ArrivalFilm.Shot camera(String film, float t, Vec3 player, Vec3 start, Vec3 shadow, Vec3 axis, ArrivalFilm.Shot eye, @Nullable Vec3 tie) {
		Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);

		if ("boss_mid".equals(film)) {
			return bossMid(t, player, shadow, axis, side, up, eye);
		}

		if ("duel_end".equals(film) && t >= AT_TIE) {
			return toTie(t, player, shadow, axis, side, up, tie);
		}

		if ("duel_end".equals(film)) {
			Vec3 land = StoryClient.hiltLanding();
			Vec3 hand = StoryClient.hiltStart();

			if (t < 18.0F) {
				// From the side, wide: the saber struck from her hand flies off.
				float u = ease(t / 18.0F);
				Vec3 middle = player.add(start).scale(0.5);
				Vec3 follow = land != null && hand != null ? hand.lerp(land, u * 0.8) : middle.add(up.scale(1.2));
				Vec3 cam = middle.add(side.scale(6.4 - u * 0.6)).add(up.scale(2.0)).subtract(axis.scale(0.5));
				return look(cam, middle.add(up.scale(1.2)).lerp(follow, 0.6));
			}

			if (t < 56.0F && land != null) {
				// Low by the hilt on the floor: it sputters and dies (the camera creeps closer).
				float u = ease((t - 18.0F) / 38.0F);
				Vec3 away = new Vec3(land.x - shadow.x, 0.0, land.z - shadow.z);
				away = away.lengthSqr() < 1.0E-4 ? axis : away.normalize();
				Vec3 across = new Vec3(-away.z, 0.0, away.x);
				Vec3 cam = land.add(away.scale(1.9 - u * 0.6)).add(across.scale(0.7)).add(up.scale(0.45 - u * 0.1));
				return look(cam, land.add(up.scale(0.08)));
			}

			if (t < 96.0F) {
				// On her: she stares at her empty hand, then up at the player.
				float u = ease((t - 56.0F) / 40.0F);
				Vec3 toPlayer = new Vec3(player.x - shadow.x, 0.0, player.z - shadow.z);
				toPlayer = toPlayer.lengthSqr() < 1.0E-4 ? axis.scale(-1.0) : toPlayer.normalize();
				Vec3 across = new Vec3(-toPlayer.z, 0.0, toPlayer.x);
				Vec3 cam = shadow.add(toPlayer.scale(2.4 - u * 0.5)).add(across.scale(0.8)).add(up.scale(1.65));
				return look(cam, shadow.add(up.scale(1.5 + 0.05 * u)));
			}

			// Over the player's shoulder as she runs (Phase 24: for her TIE).
			Vec3 cam = player.add(up.scale(2.0)).subtract(axis.scale(0.7)).add(side.scale(0.65));
			return look(cam, shadow.add(up.scale(1.2)));
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

	/**
	 * Phase 24: her flight from the moment she reaches the TIE: beside it as she leaps up into the
	 * ball; high in the hangar as it lifts, glides over the field and drops through it (the look
	 * from outside is drawn elsewhere); the player in front as the comlink calls.
	 */
	private static ArrivalFilm.Shot toTie(float t, Vec3 player, Vec3 shadow, Vec3 axis, Vec3 side, Vec3 up, @Nullable Vec3 tie) {
		if (t >= TIE_BACK || tie == null) {
			float u = ease((t - TIE_BACK) / 30.0F);
			Vec3 cam = player.add(axis.scale(2.9 - u * 0.5)).add(up.scale(1.75)).add(side.scale(0.4));
			return look(cam, player.add(up.scale(1.5)));
		}

		Vec3 corner = tie.subtract(com.phiro.sabercraft.interior.DestroyerInterior.ESCAPE_TIE);

		if (t < EscapeFilm.TIE_LIFT) {
			// In front of it (it faces east), low, a little to the south, between the planes of its
			// wings (from beside them a wing fills the picture): she comes round the wing to the
			// front of the ball and leaps up into it.
			float u = ease((t - AT_TIE) / (EscapeFilm.TIE_LIFT - AT_TIE));
			Vec3 cam = tie.add(8.8 - u * 1.4, -1.5 + u * 0.4, 1.6 - u * 0.4);
			float k = Mth.clamp((t - (BOARD - 6)) / 14.0F, 0.0F, 1.0F);
			Vec3 her = shadow.add(up.scale(1.3));

			if (her.distanceTo(tie) > 12.0) {
				// In the ball already (her figure gone): not back to where the film began.
				her = tie.add(2.7, -1.0, 0.0);
			}

			return look(cam, her.lerp(tie.add(0.0, 0.6, 0.0), k));
		}

		// High in the hangar, following it out through the field.
		float[] at = EscapeFilm.hangarTie(t);
		Vec3 now = at != null ? corner.add(at[0], at[1], at[2]) : tie.add(17.0, -8.0, 1.0);
		Vec3 cam = tie.add(9.5, 3.4, 15.0);
		return look(cam, now);
	}

	/**
	 * Phase 24: the blade lock as a scene: out of the player's eyes to the side (both and the
	 * crossed blades in the picture), low round them, close on her over the player's shoulder, wide
	 * as the burst throws them apart, low on her as the second blade comes out of her hilt, and
	 * back over the player's shoulder into their eyes (where the fight goes on).
	 */
	private static ArrivalFilm.Shot bossMid(float t, Vec3 player, Vec3 shadow, Vec3 axis, Vec3 side, Vec3 up, ArrivalFilm.Shot eye) {
		Vec3 mid = player.lerp(shadow, 0.5).add(up.scale(1.35));
		Vec3 eyeLook = eye.position().add(Vec3.directionFromRotation(eye.pitch(), eye.yaw()).scale(4.0));

		if (t < 26.0F) {
			float u = ease(t / 26.0F);
			Vec3 to = mid.add(side.scale(4.4)).add(up.scale(0.35)).subtract(axis.scale(0.6));
			return look(eye.position().lerp(to, u), eyeLook.lerp(mid, u));
		}

		if (t < 58.0F) {
			// Low, slowly round to the front of her (her face over the blades).
			float u = ease((t - 26.0F) / 32.0F);
			double a = Math.toRadians(35.0 * u);
			Vec3 dir = side.scale(Math.cos(a)).subtract(axis.scale(Math.sin(a)));
			Vec3 cam = mid.add(dir.scale(4.4 - 0.6 * u)).add(up.scale(-0.55 + 0.25 * u));
			return look(cam, mid.add(up.scale(0.1)));
		}

		if (t < DuelFilm.BOSS_LOCK) {
			// Close on her, over the player's shoulder.
			float u = ease((t - 58.0F) / (BOSS_LOCK - 58.0F));
			Vec3 cam = player.add(up.scale(1.78)).subtract(axis.scale(0.55 - 0.3 * u)).add(side.scale(0.55));
			return look(cam, shadow.add(up.scale(1.55)));
		}

		if (t < BOSS_REVEAL) {
			// Wide and high: the burst throws them apart.
			float u = ease((t - BOSS_LOCK) / (BOSS_REVEAL - BOSS_LOCK));
			Vec3 cam = mid.add(side.scale(6.2 + u * 0.8)).add(up.scale(2.6 + u * 0.4));
			return look(cam, mid.subtract(up.scale(0.4)));
		}

		if (t < BOSS_RETURN) {
			// Low in front of her: the second blade comes out.
			float u = ease((t - BOSS_REVEAL) / (BOSS_RETURN - BOSS_REVEAL));
			Vec3 toPlayer = new Vec3(player.x - shadow.x, 0.0, player.z - shadow.z);
			toPlayer = toPlayer.lengthSqr() < 1.0E-4 ? axis.scale(-1.0) : toPlayer.normalize();
			Vec3 across = new Vec3(-toPlayer.z, 0.0, toPlayer.x);
			Vec3 cam = shadow.add(toPlayer.scale(2.7 - 0.5 * u)).add(across.scale(0.9)).add(up.scale(0.5 + 0.2 * u));
			return look(cam, shadow.add(up.scale(1.3 + 0.15 * u)));
		}

		// Back over the player's shoulder into their eyes.
		float u = ease((t - BOSS_RETURN) / (BOSS_LENGTH - BOSS_RETURN));
		Vec3 from = player.add(up.scale(1.95)).subtract(axis.scale(1.4)).add(side.scale(0.6));
		Vec3 fromLook = shadow.add(up.scale(1.3));
		return look(from.lerp(eye.position(), u), fromLook.lerp(eyeLook, u));
	}

	/**
	 * Phase 24: the guard overpowered: from the side as the player is on him; low in front of him as
	 * the blow lands and he sags; high over the player's shoulder as he lies there.
	 */
	static ArrivalFilm.Shot takedown(float t, Vec3 player, Vec3 guard, Vec3 axis) {
		Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 mid = player.lerp(guard, 0.5);

		if (t < 24.0F) {
			float u = ease(t / 24.0F);
			Vec3 cam = mid.add(side.scale(3.4 - 0.4 * u)).add(up.scale(1.5)).subtract(axis.scale(0.3));
			return look(cam, mid.add(up.scale(1.3)));
		}

		if (t < 50.0F) {
			float u = ease((t - 24.0F) / 26.0F);
			Vec3 cam = guard.add(axis.scale(2.6 - 0.3 * u)).add(side.scale(-0.8)).add(up.scale(0.7));
			return look(cam, guard.add(up.scale(1.2 - 0.5 * u)));
		}

		float u = ease((t - 50.0F) / 26.0F);
		Vec3 cam = player.add(up.scale(2.4 + 0.3 * u)).subtract(axis.scale(1.2 + 0.3 * u)).add(side.scale(0.7));
		return look(cam, guard.add(up.scale(0.3)));
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
