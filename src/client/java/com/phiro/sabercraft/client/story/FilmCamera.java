package com.phiro.sabercraft.client.story;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.client.interior.ArrivalFilm;
import com.phiro.sabercraft.client.interior.InteriorScene;
import com.phiro.sabercraft.client.interior.MeshRays;

/**
 * Phase 29: the camera of the story's films shot where the story is never goes through a wall
 * (Phiro's playtest: the takedown's camera went into the wall of the cell, one saw the outside;
 * the boss's defeat, with the two by the edge of the room, was shot from beyond its wall). Each film
 * still plans its shots as before (a camera gliding from one place to another, turned towards what
 * it shows); here each shot is fitted into the rooms as they are:
 *
 * <ol>
 * <li><b>Before a shot begins</b> its whole glide is looked at, from the point the shot is about
 * (between the two, a person's chest: a point in the room) out to the camera: if a wall or anything
 * solid stands between at any moment of it, or the camera would be in a person, other places are
 * tried: the shot turned round that point (a little, then further, to the other side, behind), or
 * raised. The first that is clear all the way is kept for the whole shot (a shot that is clear as
 * planned stays as planned); so the camera does not jump about within a shot.</li>
 * <li><b>Each frame</b> the camera is still kept on the room's side of everything between it and
 * that point (the people move while it runs): it is pulled in at once in front of a surface (never
 * first into it and then out), and glides back out smoothly when there is room again; it keeps a
 * little way from every surface round it, out of the people's bodies and out of the player's head.
 * If even so it would have to come right up to the point, the shot is placed anew: the camera
 * glides round the point to its new place.</li>
 * <li><b>Never a jump within a shot</b>: from one frame to the next the camera moves at most
 * {@link #SPEED} blocks a tick. Where the fitted place leaps further (the people moved suddenly, a
 * wall came between, the point it is about had to move out of something), the camera follows it
 * that fast, but only through places that are themselves clear (in nothing, in nobody, nothing
 * between it and the point, room round it): straight towards it, or round what stands in the way
 * (turned a little to a side, up or down). Where no step is clear it holds its last place (still
 * clear) and turns to look, at most {@link #HOLD_MAX} ticks; then it takes the fitted place (a cut,
 * never into something: a fitted place that is not clear itself is never taken while the last one
 * still is).</li>
 * </ol>
 *
 * <p>Walls are the blocks one collides with (the ship's barriers, closed doors) and the drawn rooms
 * (the detailed models: their walls are drawn one-sided, see {@link MeshRays}).
 */
public final class FilmCamera {
	/** How far in front of a surface between it and the point it shows the camera stays (blocks). */
	static final double MARGIN = 0.32;
	/** How far from any surface round it the camera keeps (blocks). */
	static final double NEAR = 0.24;
	/** How far out of a person's body (their box) the camera keeps. */
	static final double BODY = 0.22;
	/** How far from the player's eyes the camera keeps (unless it is in them: the film's own way back into the game). */
	static final double EYE_CLEAR = 0.6;
	/** Nearer than this to the point a shot is about, it is no shot (placed anew). */
	static final double MIN_ARM = 0.9;
	/** The places tried for a shot that is not clear: turned round its point (degrees), and raised (blocks). */
	private static final double[] TURNS = {0.0, 25.0, -25.0, 50.0, -50.0, 80.0, -80.0, 115.0, -115.0, 150.0, -150.0, 180.0};
	private static final double[] RAISES = {0.0, 0.7};
	/** Moments of a shot looked at when choosing its place. */
	private static final int SAMPLES = 10;
	/** How fast the camera may move at most within a shot (blocks a tick; at least a quarter tick's way a frame). */
	static final double SPEED = 0.8;
	/** How long (ticks) the camera holds its last place at most while its fitted place, clear, cannot be reached. */
	static final float HOLD_MAX = 6.0F;
	/** How much room round it a camera that follows or holds keeps at least (blocks; the near plane is 0.05). */
	private static final double ROOM = 0.12;
	/** The ways round tried when the straight step towards the fitted place is not clear (degrees: sideways, then up or down). */
	private static final double[][] DETOURS = {{35.0, 0.0}, {-35.0, 0.0}, {0.0, 40.0}, {0.0, -40.0}, {65.0, 0.0}, {-65.0, 0.0}, {35.0, 35.0}, {-35.0, 35.0}};

	/** A film's planned camera at a moment (ticks). */
	@FunctionalInterface
	interface Plan {
		ArrivalFilm.Shot at(float t);
	}

	private static String shotKey = "";
	private static double turn;
	private static double raise;
	/** A glide from one place round the point to another within a shot: from that turn and raise, since then, for so long (ticks). */
	private static double blendTurn;
	private static double blendRaise;
	private static float blendAt;
	private static float blendTicks;
	/** How fast the camera glides round the point to a new place within a shot (blocks a tick, about). */
	static final double GLIDE = 0.55;
	/** How far out from the point the camera is now (eased out, at once in); negative: new shot. */
	private static double arm = -1.0;
	private static long frameNanos;
	private static float placedAt = -1000.0F;

	private static int frames;
	private static int choices;
	private static int turnedShots;
	private static int clampedFrames;
	private static int replaced;
	private static int pushed;
	private static int rescued;
	private static int glides;
	private static int followed;
	private static int held;
	private static int cut;
	/** The camera last shown (its shot, the film's time), for its way to the next frame. */
	private static @Nullable Vec3 lastShown;
	private static String lastShownKey = "";
	private static float lastShownAt;
	/** Since when (film time) the camera has held its last place, or below 0. */
	private static float holdSince = -1.0F;
	/** What happened to the frame last fitted (for the tests' record). */
	private static String note = "";

	/** The shot's turn round its point now (gliding to a new one, or the one chosen). */
	private static double turnAt(float t) {
		float u = glideShare(t);
		return u >= 1.0F ? turn : blendTurn + Mth.wrapDegrees(turn - blendTurn) * u;
	}

	private static double raiseAt(float t) {
		float u = glideShare(t);
		return u >= 1.0F ? raise : blendRaise + (raise - blendRaise) * u;
	}

	private static float glideShare(float t) {
		if (blendTicks <= 0.0F) {
			return 1.0F;
		}

		float u = Mth.clamp((t - blendAt) / blendTicks, 0.0F, 1.0F);
		return u * u * (3.0F - 2.0F * u);
	}

	private FilmCamera() {
	}

	/** A film ended or a new one begins: nothing kept from the last shot. */
	static void reset() {
		shotKey = "";
		lastShown = null;
		lastShownKey = "";
		holdSince = -1.0F;
		blendTicks = 0.0F;
		arm = -1.0;
		placedAt = -1000.0F;
	}

	/**
	 * The camera of a shot now, fitted into the rooms.
	 *
	 * @param key the film and its shot (a new key: a new shot, its place chosen anew)
	 * @param t the film's time (ticks)
	 * @param from when the shot began
	 * @param to when it ends
	 * @param plan the film's planned camera at any moment of the shot
	 * @param about the point the shot is about (in the room: between the people, a person's chest)
	 * @param bodies the people in the picture (the camera keeps out of them)
	 * @param eye the player's eyes now (the camera keeps out of their head), or null
	 */
	static ArrivalFilm.Shot settle(String key, float t, float from, float to, Plan plan, Vec3 about, List<AABB> bodies, @Nullable Vec3 eye) {
		ArrivalFilm.Shot raw = plan.at(t);
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || eye != null && raw.position().distanceToSqr(eye) < 1.0E-4) {
			// (In the player's eyes: where the film goes back into the game.)
			lastShown = null;
			return raw;
		}

		frames++;
		note = "";
		Vec3 pivot = freePivot(level, about, raw.position(), bodies, eye);

		if (pivot.distanceToSqr(about) > 1.0E-6) {
			note += "pivot ";
		}

		if (!key.equals(shotKey)) {
			note += "new ";
			shotKey = key;
			choose(level, t, from, to, plan, pivot, bodies, eye);
			arm = -1.0;
			blendTicks = 0.0F;
			// (A new place chosen late in the last shot does not hold back a new one in this.)
			placedAt = -1000.0F;
		}

		Placed placed = place(level, transform(raw, pivot, turnAt(t), raiseAt(t)), pivot, bodies, eye);

		if (!placed.ok() && t - placedAt > 10.0F) {
			// It would have to come right up to the point (or into someone): another place for the rest
			// of the shot. Not a jump there: the camera glides round the point to it (kept in the room
			// all the way, as every frame), quickly but smoothly.
			double oldTurn = turnAt(t);
			double oldRaise = raiseAt(t);
			placedAt = t;
			replaced++;
			note += "replace ";
			choose(level, t, t, to, plan, pivot, bodies, eye);

			if (Math.abs(Mth.wrapDegrees(turn - oldTurn)) > 1.0E-3 || Math.abs(raise - oldRaise) > 1.0E-3) {
				// (The way round the point at the planned arm: the arm, pulled in here, eases back out
				// to it as the camera glides.)
				Vec3 c = raw.position();
				double radius = Math.max(1.0, Math.hypot(c.x - pivot.x, c.z - pivot.z));
				double way = Math.toRadians(Math.abs(Mth.wrapDegrees(turn - oldTurn))) * radius + Math.abs(raise - oldRaise);
				blendTurn = oldTurn;
				blendRaise = oldRaise;
				blendAt = t;
				// (GLIDE at its fastest, in the middle: an eased glide runs half again as fast there as on average.)
				blendTicks = (float) Mth.clamp(way * 1.5 / GLIDE, 8.0, 40.0);
				glides++;
			}

			placed = place(level, transform(raw, pivot, turnAt(t), raiseAt(t)), pivot, bodies, eye);
		}

		Vec3 fitted = placed.shot().position();
		Vec3 c = fitted;
		ArrivalFilm.Shot shown = placed.shot();

		boolean holding = false;

		if (lastShown != null && key.equals(lastShownKey)) {
			// Never a jump within a shot: at most that fast, through clear places only.
			double step = SPEED * Math.max(0.25, t - lastShownAt);
			double gap = fitted.distanceTo(lastShown);
			boolean far = gap > step;
			boolean fittedClear = clear(level, fitted, pivot, bodies, eye);

			if (far || !fittedClear) {
				Vec3 next = far ? toward(level, lastShown, fitted, step, pivot, bodies, eye) : null;

				if (next != null) {
					c = next;
					followed++;
					note += "follow ";
				} else if (clear(level, lastShown, pivot, bodies, eye) && (!fittedClear || holdSince < 0.0F || t - holdSince < HOLD_MAX)) {
					// (Held a while at most when the fitted place is clear: then a cut to it. Never
					// into a fitted place that is not clear while the last one still is.)
					c = lastShown;
					holding = true;
					held++;
					note += "hold ";
				} else {
					cut++;
					note += "cut ";
				}

				if (c != fitted) {
					Vec3 look = placed.target().subtract(c);
					shown = look.lengthSqr() < 0.04 ? new ArrivalFilm.Shot(c, placed.shot().yaw(), placed.shot().pitch()) : DuelFilm.look(c, placed.target());
				}
			}
		}

		holdSince = holding ? holdSince < 0.0F ? t : holdSince : -1.0F;

		lastShown = c;
		lastShownKey = key;
		lastShownAt = t;

		if (auditing && AUDIT.size() < 8000) {
			double reach = c.distanceTo(pivot);
			AUDIT.add(new Frame(key, t, c, pivot, inside(level, c), free(level, pivot, c) < reach - 0.05, inBody(c, bodies, eye), eye == null ? -1.0 : c.distanceTo(eye),
					reach, note.trim(), c.distanceTo(fitted)));
		}

		return shown;
	}

	/**
	 * The camera's next place on its way from its last place towards its fitted one, that far at
	 * most: straight on, or round what stands in the way; kept off surfaces and out of people as
	 * every frame; null when no such step is clear and brings it closer.
	 */
	private static @Nullable Vec3 toward(ClientLevel level, Vec3 from, Vec3 to, double step, Vec3 pivot, List<AABB> bodies, @Nullable Vec3 eye) {
		Vec3 d = to.subtract(from);
		double gap = d.length();

		if (gap < 1.0E-6) {
			return null;
		}

		Vec3 dir = d.scale(1.0 / gap);
		Vec3 straight = settled(level, from.add(dir.scale(step)), bodies, eye);

		if (straight.distanceTo(to) < gap - 1.0E-3 && clear(level, straight, pivot, bodies, eye)) {
			return straight;
		}

		Vec3 flat = new Vec3(dir.x, 0.0, dir.z);

		for (double[] detour : DETOURS) {
			Vec3 way = rotate(dir, detour[0]);

			if (detour[1] != 0.0 && flat.lengthSqr() > 1.0E-6) {
				// Tilted up or down out of its way (towards the upright through it).
				way = way.scale(Math.cos(Math.toRadians(detour[1]))).add(0.0, Math.sin(Math.toRadians(detour[1])), 0.0).normalize();
			}

			Vec3 p = settled(level, from.add(way.scale(step)), bodies, eye);

			if (p.distanceTo(to) < gap - 1.0E-3 && clear(level, p, pivot, bodies, eye)) {
				return p;
			}
		}

		return null;
	}

	/** A place kept off the surfaces round it and out of people (as {@link #place} does). */
	private static Vec3 settled(ClientLevel level, Vec3 p, List<AABB> bodies, @Nullable Vec3 eye) {
		Vec3 at = pushOut(level, p);

		for (int i = 0; i < 3 && nearBody(at, bodies, eye); i++) {
			at = outOfBodies(at, bodies, eye);
		}

		return at;
	}

	/**
	 * Whether the camera may be at that place: in nothing solid, in nobody (nor right at them), with
	 * room round it, nothing solid between it and the point the shot is about.
	 */
	private static boolean clear(ClientLevel level, Vec3 p, Vec3 pivot, List<AABB> bodies, @Nullable Vec3 eye) {
		if (inside(level, p) || inBody(p, bodies, eye) || !roomy(level, p, ROOM)) {
			return false;
		}

		for (AABB body : bodies) {
			// (outOfBodies puts a place just outside the box with BODY round it.)
			if (body.inflate(BODY - 0.03).contains(p)) {
				return false;
			}
		}

		double reach = p.distanceTo(pivot);
		return reach < 1.0E-3 || free(level, pivot, p) >= reach - 0.05;
	}

	/**
	 * For the tests: one frame of a film's camera as fitted: its shot, the film's time, where the
	 * camera was, the point the shot is about, whether the camera was inside something solid,
	 * whether something solid stood between it and that point, whether it was in a person (or the
	 * player's head), how far it was from the player's eyes (-1 without them), and from the point;
	 * what happened to it (its point moved out of something, a new shot, placed anew, the camera
	 * following its fitted place, holding, a cut; in {@link #place} pulled in, pushed off a surface or
	 * out of someone, taken back out of something), and how far it was from its fitted place.
	 */
	public record Frame(String key, float t, Vec3 camera, Vec3 about, boolean inside, boolean blocked, boolean inBody, double fromEyes, double reach, String note,
			double lag) {
	}

	private static final List<Frame> AUDIT = new java.util.ArrayList<>();
	private static boolean auditing;

	/** For the tests: records every frame fitted from now on (or stops), the record emptied. */
	public static void auditForTest(boolean on) {
		auditing = on;
		AUDIT.clear();
	}

	/** For the tests: the frames recorded so far. */
	public static List<Frame> framesForTest() {
		return List.copyOf(AUDIT);
	}

	private record Placed(ArrivalFilm.Shot shot, boolean ok, Vec3 target) {
	}

	/** Chooses the shot's place (its turn round the point and its height) for the rest of it. */
	private static void choose(ClientLevel level, float now, float from, float to, Plan plan, Vec3 pivot, List<AABB> bodies, @Nullable Vec3 eye) {
		choices++;
		float start = Math.max(from, now);
		float end = Math.max(start, to - 0.05F);
		double bestScore = -Double.MAX_VALUE;
		double bestTurn = 0.0;
		double bestRaise = 0.0;

		search:
		for (double r : RAISES) {
			for (double a : TURNS) {
				double score = 0.0;
				boolean allClear = true;

				for (int i = 0; i < SAMPLES; i++) {
					float ts = start + (end - start) * i / (SAMPLES - 1);
					ArrivalFilm.Shot shot = transform(plan.at(ts), pivot, a, r);
					Vec3 cam = shot.position();

					if (eye != null && plan.at(ts).position().distanceToSqr(eye) < 1.0E-4) {
						score += 1.0;
						continue;
					}

					double length = cam.distanceTo(pivot);
					double free = length < 1.0E-3 ? 0.0 : free(level, pivot, cam);
					boolean clear = length >= MIN_ARM && free >= length - 1.0E-3 && !inside(level, cam) && !inBody(cam, bodies, eye) && roomy(level, cam);

					if (!clear) {
						allClear = false;
					}

					score += clear ? 1.0 : 0.6 * Mth.clamp(free / Math.max(1.0E-3, length), 0.0, 1.0);

					// The people should be in view, not behind something.
					for (AABB body : bodies) {
						Vec3 middle = body.getCenter();

						if (free(level, cam, middle) < cam.distanceTo(middle) - 0.4) {
							score -= 0.25;
						}
					}
				}

				if (allClear && a == 0.0 && r == 0.0) {
					bestTurn = 0.0;
					bestRaise = 0.0;
					break search;
				}

				score += allClear ? 3.0 : 0.0;
				score -= Math.abs(a) / 180.0 * 1.5 + r * 0.8;

				if (score > bestScore) {
					bestScore = score;
					bestTurn = a;
					bestRaise = r;
				}
			}
		}

		turn = bestTurn;
		raise = bestRaise;

		if (turn != 0.0 || raise != 0.0) {
			turnedShots++;
		}
	}

	/** The camera placed this frame: on the room's side of what stands between it and the point, out of surfaces and people. */
	private static Placed place(ClientLevel level, ArrivalFilm.Shot planned, Vec3 pivot, List<AABB> bodies, @Nullable Vec3 eye) {
		Vec3 cam = planned.position();
		Vec3 d = cam.subtract(pivot);
		double length = d.length();

		if (length < 1.0E-3) {
			return new Placed(planned, false, cam.add(Vec3.directionFromRotation(planned.pitch(), planned.yaw())));
		}

		// Where the planned camera looks (it keeps looking there wherever it is moved to).
		Vec3 target = cam.add(Vec3.directionFromRotation(planned.pitch(), planned.yaw()).scale(Math.max(1.0, length)));
		Vec3 dir = d.scale(1.0 / length);
		double free = free(level, pivot, cam);
		double allowed = free >= length - 1.0E-3 ? length : Math.max(0.0, free - MARGIN);
		long now = System.nanoTime();
		double dt = frameNanos == 0L ? 0.05 : Mth.clamp((now - frameNanos) / 1.0E9, 0.0, 0.1);
		frameNanos = now;

		if (arm < 0.0 || allowed < arm) {
			// In at once: never into the wall first.
			if (arm >= 0.0 && arm - allowed > 0.3) {
				note += "in ";
			}

			arm = allowed;
		} else {
			// Out again smoothly.
			arm += (allowed - arm) * (1.0 - Math.exp(-dt * 7.0));
		}

		double use = Math.min(arm, length);

		if (use < length - 1.0E-3) {
			clampedFrames++;
		}

		Vec3 at = pivot.add(dir.scale(use));
		Vec3 before = at;
		at = pushOut(level, at);

		if (at.distanceToSqr(before) > 1.0E-6) {
			note += "push ";
		}

		// (Out of one person can be into another standing close: until it is out of all, a few times.)
		// (Kept BODY out of everyone's box and EYE_CLEAR from the player's eyes, every frame.)
		for (int i = 0; i < 3 && nearBody(at, bodies, eye); i++) {
			at = outOfBodies(at, bodies, eye);
			note += i == 0 ? "body " : "";
		}

		// The pushes off surfaces and people moved it without looking: still nothing between it and the point?
		double reach = at.distanceTo(pivot);

		if (reach > 1.0E-3) {
			double clear = free(level, pivot, at);

			if (clear < reach - 0.05) {
				at = pivot.add(at.subtract(pivot).scale(Math.max(0.0, clear - MARGIN) / reach));
				arm = Math.min(arm, at.distanceTo(pivot));
				rescued++;
				note += "rescue ";
			}
		}

		boolean ok = at.distanceTo(pivot) >= Math.min(MIN_ARM, length * 0.95) && !inBody(at, bodies, eye) && !inside(level, at);

		if (inside(level, at) || inBody(at, bodies, eye)) {
			// Pushed off one surface into another (a corner, a narrow doorway), or into someone: never
			// shown so. Back along the way to the point (which is free) to the first free place; the
			// arm is kept there, so the camera comes out again smoothly, not with a jump.
			at = backToFree(level, at, pivot, bodies, eye);
			arm = Math.min(arm, at.distanceTo(pivot));
			rescued++;
			note += "back ";
		}
		Vec3 look = target.subtract(at);
		ArrivalFilm.Shot shot = look.lengthSqr() < 0.04 ? new ArrivalFilm.Shot(at, planned.yaw(), planned.pitch()) : DuelFilm.look(at, target);
		return new Placed(shot, ok, target);
	}

	/** A shot turned round a point (degrees, about the upright through it) and raised (blocks): camera and what it looks at alike. */
	static ArrivalFilm.Shot transform(ArrivalFilm.Shot shot, Vec3 pivot, double degrees, double up) {
		if (degrees == 0.0 && up == 0.0) {
			return shot;
		}

		Vec3 cam = shot.position();
		Vec3 target = cam.add(Vec3.directionFromRotation(shot.pitch(), shot.yaw()).scale(Math.max(1.0, cam.distanceTo(pivot))));
		Vec3 c = rotate(cam.subtract(pivot), degrees).add(pivot).add(0.0, up, 0.0);
		Vec3 t = rotate(target.subtract(pivot), degrees).add(pivot);
		return DuelFilm.look(c, t);
	}

	private static Vec3 rotate(Vec3 v, double degrees) {
		double a = Math.toRadians(degrees);
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	/**
	 * How far from {@code a} towards {@code b} the way is free (blocks): up to the first block one
	 * collides with or drawn surface facing back towards {@code a}; the whole way when nothing is there.
	 */
	public static double free(ClientLevel level, Vec3 a, Vec3 b) {
		double length = a.distanceTo(b);

		if (length < 1.0E-6) {
			return 0.0;
		}

		double share = 1.0;
		BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

		if (hit.getType() != HitResult.Type.MISS) {
			share = Math.min(share, a.distanceTo(hit.getLocation()) / length);
		}

		MeshRays rays = InteriorScene.rays(a);

		if (rays != null) {
			share = Math.min(share, rays.clip(a, b, false));
		}

		return share * length;
	}

	/** Whether a point is inside something solid: a block one collides with, or behind a drawn surface (inside a wall, outside the rooms). */
	public static boolean inside(ClientLevel level, Vec3 point) {
		if (!level.noCollision(new AABB(point, point).inflate(0.04))) {
			return true;
		}

		MeshRays rays = InteriorScene.rays(point);
		return rays != null && rays.behind(point, 2.5);
	}

	/** Whether no surface is nearer than {@link #NEAR} round a point. */
	private static boolean roomy(ClientLevel level, Vec3 point) {
		return roomy(level, point, NEAR);
	}

	/** Whether no surface is nearer than that round a point. */
	private static boolean roomy(ClientLevel level, Vec3 point, double near) {
		for (Vec3 axis : AXES) {
			if (free(level, point, point.add(axis.scale(near))) < near - 1.0E-3) {
				return false;
			}
		}

		return true;
	}

	private static final Vec3[] AXES = {new Vec3(1.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, -1.0, 0.0),
			new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0)};

	/** The first place on the way from {@code from} back to the point a shot is about that is free (in nothing, in nobody). */
	private static Vec3 backToFree(ClientLevel level, Vec3 from, Vec3 pivot, List<AABB> bodies, @Nullable Vec3 eye) {
		Vec3 d = pivot.subtract(from);
		double length = d.length();

		for (double k = 0.08; k < length; k += 0.08) {
			Vec3 p = from.add(d.scale(k / length));

			if (!inside(level, p) && !inBody(p, bodies, eye)) {
				return p;
			}
		}

		return pivot;
	}

	/** Moves a point away from any surface nearer than {@link #NEAR} round it. */
	private static Vec3 pushOut(ClientLevel level, Vec3 point) {
		Vec3 at = point;

		for (Vec3 axis : AXES) {
			double free = free(level, at, at.add(axis.scale(NEAR)));

			if (free < NEAR - 1.0E-3) {
				at = at.subtract(axis.scale(NEAR - free));
				pushed++;
			}
		}

		return at;
	}

	/** Moves a point out of the people's bodies (sideways, the shortest way) and out of the player's head. */
	private static Vec3 outOfBodies(Vec3 point, List<AABB> bodies, @Nullable Vec3 eye) {
		Vec3 at = point;

		for (AABB body : bodies) {
			AABB around = body.inflate(BODY);

			if (!around.contains(at)) {
				continue;
			}

			Vec3 middle = body.getCenter();
			Vec3 out = new Vec3(at.x - middle.x, 0.0, at.z - middle.z);
			out = out.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
			double half = Math.max(around.getXsize(), around.getZsize()) * 0.5;
			at = new Vec3(middle.x + out.x * (half + 0.02), at.y, middle.z + out.z * (half + 0.02));
		}

		if (eye != null && at.distanceTo(eye) < EYE_CLEAR) {
			Vec3 out = at.subtract(eye);
			out = out.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : out.normalize();
			at = eye.add(out.scale(EYE_CLEAR));
		}

		return at;
	}

	/** Whether a point is in a person's body or in the player's head. */
	static boolean inBody(Vec3 point, List<AABB> bodies, @Nullable Vec3 eye) {
		for (AABB body : bodies) {
			if (body.inflate(0.05).contains(point)) {
				return true;
			}
		}

		return eye != null && point.distanceTo(eye) < EYE_CLEAR - 0.1;
	}

	/**
	 * The point a shot is about moved out of anything solid: towards the camera (at most three
	 * blocks), else up, as before; where people stand near it (a shot about them) only to a place
	 * they can be seen from (never through the wall into the room beyond). Failing that, towards
	 * the nearest of them (the room's side of the wall), never into anyone.
	 */
	private static Vec3 freePivot(ClientLevel level, Vec3 wanted, Vec3 toward, List<AABB> bodies, @Nullable Vec3 eye) {
		if (!inside(level, wanted)) {
			return wanted;
		}

		List<Vec3> anchors = new java.util.ArrayList<>();

		for (AABB body : bodies) {
			Vec3 chest = new Vec3(body.getCenter().x, body.minY + body.getYsize() * 0.7, body.getCenter().z);

			if (chest.distanceTo(wanted) < 6.0) {
				anchors.add(chest);
			}
		}

		anchors.sort(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(wanted)));
		Vec3 d = toward.subtract(wanted);
		double length = d.length();
		List<Vec3> tries = new java.util.ArrayList<>();

		for (double k = 0.25; k <= Math.min(3.0, length); k += 0.25) {
			tries.add(wanted.add(d.scale(k / length)));
		}

		for (double up = 0.3; up <= 1.5; up += 0.3) {
			tries.add(wanted.add(0.0, up, 0.0));
		}

		for (Vec3 p : tries) {
			if (!inside(level, p) && !nearBody(p, bodies, eye) && (anchors.isEmpty() || seesAny(level, p, anchors))) {
				return p;
			}
		}

		for (Vec3 anchor : anchors) {
			Vec3 to = anchor.subtract(wanted);
			double far = to.length();

			for (double k = 0.2; k < far; k += 0.2) {
				Vec3 p = wanted.add(to.scale(k / far));

				if (nearBody(p, bodies, eye)) {
					break;
				}

				if (!inside(level, p)) {
					return p;
				}
			}
		}

		for (Vec3 p : tries) {
			if (!inside(level, p) && !nearBody(p, bodies, eye)) {
				return p;
			}
		}

		return wanted;
	}

	/** Whether a point is in or right at someone (their body with room round it, the player's head). */
	private static boolean nearBody(Vec3 point, List<AABB> bodies, @Nullable Vec3 eye) {
		for (AABB body : bodies) {
			if (body.inflate(BODY).contains(point)) {
				return true;
			}
		}

		return eye != null && point.distanceTo(eye) < EYE_CLEAR;
	}

	private static boolean seesAny(ClientLevel level, Vec3 p, List<Vec3> anchors) {
		for (Vec3 anchor : anchors) {
			if (free(level, p, anchor) >= p.distanceTo(anchor) - 0.05) {
				return true;
			}
		}

		return false;
	}

	/**
	 * For the tests: frames fitted, shots placed (and of them turned or raised), frames pulled in,
	 * shots placed anew, pushes off a surface, frames taken back out of something, glides round the
	 * point to a new place; frames the camera followed its fitted place, held its last, cut to it.
	 */
	public static int[] countsForTest() {
		return new int[] {frames, choices, turnedShots, clampedFrames, replaced, pushed, rescued, glides, followed, held, cut};
	}

	/** For the tests: the turn (degrees) and raise (blocks) of the shot now. */
	public static double[] placeForTest() {
		return new double[] {turn, raise};
	}
}
