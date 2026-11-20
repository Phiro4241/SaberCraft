package com.phiro.sabercraft.client.story;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.ArrivalFilm;
import com.phiro.sabercraft.interior.DestroyerInterior;

/**
 * Phase 25: the short scenes of the story shot where it happens, aboard the Star Destroyer, with
 * the people of the story as the server moves them. Each scene is a few shots, each a camera
 * gliding from one place to another while it turns from one point to another (both in the ship's
 * plot space; the server sends the plot's corner with the cue). A shot may begin in the player's
 * eyes or end there (the scene then flows back into the game without a cut).
 *
 * <ul>
 * <li>{@code hale_in}: the ISB officer comes into the armory: the door slides open, he steps in, it
 * closes and bolts behind him, over his shoulder onto the player (then the talk, from the player's eyes).
 * <li>{@code service}: through the maintenance door: the troopers reach the door the player bolted,
 * hammer on it and spread out to search; down the crawl way after the player.
 * <li>{@code saber}: the saber taken in the lab: the player steps up to the field, takes it from
 * its holder; the field dies, the reactor answers, the lights flicker, sparks, the first tremor.
 * </ul>
 */
final class SceneFilm {
	private static final double H = DestroyerInterior.HANGAR + 1;
	private static final double R = DestroyerInterior.REACTOR_BOTTOM;

	/**
	 * One shot: from {@code from} to {@code to} (ticks) the camera glides from {@code camA} to
	 * {@code camB} and turns from looking at {@code lookA} to {@code lookB}. A null place stands for
	 * the player's eyes (and a null point for where they look).
	 */
	record Shot(int from, int to, @Nullable Vec3 camA, @Nullable Vec3 camB, @Nullable Vec3 lookA, @Nullable Vec3 lookB) {
	}

	/** The scenes' lengths (as the server's). */
	static final int HALE_LENGTH = 130;
	static final int SERVICE_LENGTH = 150;
	static final int SABER_LENGTH = 200;

	private static final Map<String, List<Shot>> SHOTS = Map.of(
			"hale_in", List.of(
					// Inside the armory, by the racks: the door, closed; it slides open.
					new Shot(0, 36, v(48.2, H + 1.95, -9.6), v(47.6, H + 1.8, -10.3), v(44.6, H + 1.3, -12.6), v(44.6, H + 1.25, -12.5)),
					// Low in the room: he steps in and comes up to the player.
					new Shot(36, 76, v(50.4, H + 0.95, -12.2), v(49.9, H + 1.05, -11.6), v(45.2, H + 1.55, -12.6), v(47.1, H + 1.6, -9.6)),
					// From the side of the room: he stands before the player, the door shuts behind him, the bolt goes home.
					new Shot(76, 104, v(49.9, H + 1.7, -10.9), v(49.7, H + 1.65, -10.3), v(45.6, H + 1.4, -11.6), v(45.9, H + 1.35, -11.1)),
					// Over his right shoulder onto the player (a cut back to the player's eyes as the talk begins).
					new Shot(104, 130, v(46.0, H + 1.9, -9.85), v(46.2, H + 1.85, -9.7), v(48.6, H + 1.5, -7.6), v(48.6, H + 1.55, -7.6))),
			"service", List.of(
					// Outside, on the walkway: the troopers come running to the door the player bolted.
					new Shot(0, 54, v(50.8, R + 1.6, 21.8), v(50.3, R + 1.7, 21.0), v(48.9, R + 1.3, 16.2), v(49.0, R + 1.2, 14.6)),
					// Close on the door: they hammer on it, one calls it in.
					new Shot(54, 96, v(47.2, R + 1.9, 17.6), v(47.5, R + 2.0, 18.2), v(49.0, R + 1.2, 14.4), v(49.6, R + 1.0, 16.2)),
					// High over the shaft: they spread out to search the floor.
					new Shot(96, 128, v(45.0, R + 6.5, 22.0), v(44.2, R + 7.0, 23.0), v(49.0, R, 18.5), v(48.0, R, 22.0)),
					// In the crawl way, low, after the player (into black, then the hideout).
					new Shot(128, 150, v(48.5, R + 0.35, 7.6), v(48.5, R + 0.32, 6.2), v(48.5, R + 0.3, 2.0), v(48.5, R + 0.3, 1.0))),
			// Phase 29 (Phiro: the camera went into the player, the figure vanished): no shot glides out of
			// the player's eyes or back into them any more (through their head); the scene opens from
			// black behind the player and ends through a moment of black back in their eyes. Every
			// camera stays well clear of the player (more than two blocks), the figure always drawn.
			"saber", List.of(
					// Behind the player, over the right shoulder: the field, the saber in its holder; the player steps up.
					new Shot(0, 40, v(45.7, R + 1.95, 46.1), v(45.4, R + 1.85, 46.6), v(44.5, R + 1.25, 50.4), v(44.5, R + 1.3, 50.3)),
					// By the holder from the side, the player's face and hand in the picture (more than
					// two blocks off them): the hand closes on the hilt and takes it.
					new Shot(40, 80, v(42.4, R + 1.55, 50.3), v(42.6, R + 1.5, 50.1), v(44.4, R + 1.3, 49.6), v(44.4, R + 1.3, 49.9)),
					// Wide over the lab: the field dies, the lights flicker, sparks from the panels.
					new Shot(80, 132, v(39.0, R + 3.6, 43.0), v(39.6, R + 3.4, 43.4), v(44.5, R + 0.9, 48.5), v(45.0, R + 1.0, 47.5)),
					// Out through the door to the shaft: the core flares, the first tremor.
					new Shot(132, 176, v(44.0, R + 2.2, 38.2), v(44.0, R + 2.6, 37.0), v(41.0, R + 2.0, 28.0), v(40.6, R + 4.0, 25.0)),
					// Back to the player (over their shoulder, the empty holder before them); into their eyes through black.
					new Shot(176, 200, v(45.8, R + 2.0, 46.4), v(45.5, R + 1.9, 46.8), v(44.5, R + 1.3, 50.0), v(44.5, R + 1.3, 50.1))));

	private SceneFilm() {
	}

	private static Vec3 v(double x, double y, double z) {
		return new Vec3(x, y, z);
	}

	/** Whether that film is one of these scenes. */
	static boolean has(String film) {
		return SHOTS.containsKey(film);
	}

	/** How long that scene runs at most (ticks). */
	static int length(String film) {
		return switch (film) {
			case "hale_in" -> HALE_LENGTH;
			case "service" -> SERVICE_LENGTH;
			case "saber" -> SABER_LENGTH;
			default -> 100;
		};
	}

	/**
	 * The camera at a moment of a scene.
	 *
	 * @param corner the plot's corner (world space), from the server
	 * @param eye the player's eyes now
	 */
	static ArrivalFilm.Shot camera(String film, float t, Vec3 corner, ArrivalFilm.Shot eye) {
		List<Shot> shots = SHOTS.get(film);
		Shot shot = shots.getLast();

		for (Shot candidate : shots) {
			if (t < candidate.to()) {
				shot = candidate;
				break;
			}
		}

		float u = ease(Mth.clamp((t - shot.from()) / (float) Math.max(1, shot.to() - shot.from()), 0.0F, 1.0F));
		Vec3 eyeAt = eye.position();
		Vec3 eyeLook = eyeAt.add(Vec3.directionFromRotation(eye.pitch(), eye.yaw()).scale(4.0));
		Vec3 camA = shot.camA() != null ? corner.add(shot.camA()) : eyeAt;
		Vec3 camB = shot.camB() != null ? corner.add(shot.camB()) : eyeAt;
		Vec3 lookA = shot.lookA() != null ? corner.add(shot.lookA()) : eyeLook;
		Vec3 lookB = shot.lookB() != null ? corner.add(shot.lookB()) : eyeLook;
		Vec3 cam = camA.lerp(camB, u);
		Vec3 at = lookA.lerp(lookB, u);

		if (shot.camB() == null && u >= 0.999F) {
			// Back in the player's eyes exactly (no last twitch of the camera).
			return eye;
		}

		return DuelFilm.look(cam, at);
	}

	/** Phase 29: when the shot a scene is in now begins and ends (ticks), for the film camera. */
	static float[] span(String film, float t) {
		Shot shot = SHOTS.get(film).get(shotIndex(film, t));
		return new float[] {shot.from(), shot.to()};
	}

	/** Phase 29: whether a scene's shot begins or ends in the player's eyes (none does any more: for the tests). */
	static boolean touchesEyes(String film) {
		for (Shot shot : SHOTS.get(film)) {
			if (shot.camA() == null || shot.camB() == null) {
				return true;
			}
		}

		return false;
	}

	/** Phase 29: what a scene's shot looks at now (world space), or null where it looks out of the player's eyes. */
	static @Nullable Vec3 lookAt(String film, float t, Vec3 corner) {
		Shot shot = SHOTS.get(film).get(shotIndex(film, t));

		if (shot.lookA() == null || shot.lookB() == null) {
			return null;
		}

		float u = ease(Mth.clamp((t - shot.from()) / (float) Math.max(1, shot.to() - shot.from()), 0.0F, 1.0F));
		return corner.add(shot.lookA()).lerp(corner.add(shot.lookB()), u);
	}

	/** Phase 25: the shot a scene is in now (for the tests: each shot is a different picture). */
	static int shotIndex(String film, float t) {
		List<Shot> shots = SHOTS.get(film);

		for (int i = 0; i < shots.size(); i++) {
			if (t < shots.get(i).to()) {
				return i;
			}
		}

		return shots.size() - 1;
	}

	private static float ease(float u) {
		return u * u * (3.0F - 2.0F * u);
	}
}
