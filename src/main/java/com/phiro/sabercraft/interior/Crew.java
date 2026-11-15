package com.phiro.sabercraft.interior;

import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 17: someone of a ship's crew, drawn by the client (no entity: nothing for the server to
 * tick, the same for every player). Phase 18: its own type, used by both ships; more kinds of
 * people, a role (what they say when spoken to) and where they are at a moment, worked out the
 * same way on the server (to talk to them) and on every client (to draw them).
 *
 * <p>{@code kind}: who it is ({@link #TROOPER}, {@link #OFFICER}, ...); {@code pose}: {@link #STAND}
 * (on guard), {@link #WORK} (at a console), {@link #SIT}, {@link #TALK}, {@link #PATROL} (walks round
 * {@code path}, a closed loop, at {@code speed} blocks a second, starting {@code phase} blocks along
 * it). (x, y, z) are the feet (plot space), {@code yaw} Minecraft's (0 = facing south, 90 west, 180
 * north). {@code role} picks what they say (empty: by their kind).
 */
public record Crew(String kind, String pose, double x, double y, double z, float yaw, List<Vec3> path, double speed, double phase, String role) {
	// Who.
	public static final String TROOPER = "trooper";
	public static final String OFFICER = "officer";
	/** Phase 18: officers in the black uniform (the security bureau), in olive (the army), in white (an admiral). */
	public static final String OFFICER_BLACK = "officer_black";
	public static final String OFFICER_OLIVE = "officer_olive";
	public static final String ADMIRAL = "admiral";
	public static final String TECH = "tech";
	/** Phase 18: a starfighter pilot (black flight suit, helmet with breathing hoses). */
	public static final String PILOT = "pilot";
	/** Phase 18: an honour guard in red robes and helmet, with a pike. */
	public static final String GUARD = "guard";
	/** Phase 18: the Falcon's people: a smuggler, a tall furry mechanic, a rebel passenger. */
	public static final String SMUGGLER = "smuggler";
	public static final String WOOKIEE = "wookiee";
	public static final String REBEL = "rebel";

	// What they do.
	public static final String STAND = "stand";
	public static final String WORK = "work";
	public static final String SIT = "sit";
	public static final String TALK = "talk";
	public static final String PATROL = "patrol";

	public static Crew at(String kind, String pose, double x, double y, double z, float yaw) {
		return new Crew(kind, pose, x, y, z, yaw, List.of(), 0.0, 0.0, "");
	}

	public static Crew patrol(String kind, double speed, double phase, Vec3... path) {
		return new Crew(kind, PATROL, path[0].x, path[0].y, path[0].z, 0.0F, List.of(path), speed, phase, "");
	}

	/** The same person with a role (what they talk about). */
	public Crew as(String role) {
		return new Crew(this.kind, this.pose, this.x, this.y, this.z, this.yaw, this.path, this.speed, this.phase, role);
	}

	/** Whether this one stays in its place (and collides there). */
	public boolean still() {
		return this.path.isEmpty() && !SIT.equals(this.pose);
	}

	/** Whether this one wears armour or a helmet over the face (troopers, technicians, pilots, guards). */
	public boolean helmeted() {
		return TROOPER.equals(this.kind) || TECH.equals(this.kind) || PILOT.equals(this.kind) || GUARD.equals(this.kind);
	}

	/** Whether this one is an officer of any uniform. */
	public boolean officer() {
		return OFFICER.equals(this.kind) || OFFICER_BLACK.equals(this.kind) || OFFICER_OLIVE.equals(this.kind) || ADMIRAL.equals(this.kind);
	}

	/** How tall (blocks): the furry mechanic is a head taller than everyone else. */
	public double height() {
		return WOOKIEE.equals(this.kind) ? 2.25 : 1.8;
	}

	/** Where this one is after {@code ticks} game ticks: x, y, z (plot space) and the yaw. */
	public double[] where(double ticks) {
		if (this.path.isEmpty()) {
			return new double[] {this.x, this.y, this.z, this.yaw};
		}

		return along(this.path, this.phase + ticks / 20.0 * this.speed);
	}

	/** The box this one fills at that moment (plot space), for clicking on them. */
	public AABB box(double ticks) {
		double[] at = this.where(ticks);
		double half = SIT.equals(this.pose) ? 0.4 : 0.32;
		double top = SIT.equals(this.pose) ? 1.45 : this.height();
		return new AABB(at[0] - half, at[1], at[2] - half, at[0] + half, at[1] + top, at[2] + half);
	}

	/** Where on a closed path one is after walking {@code distance}: x, y, z and the way one faces (yaw), turning smoothly at the corners. */
	public static double[] along(List<Vec3> path, double distance) {
		int n = path.size();
		double total = 0.0;

		for (int i = 0; i < n; i++) {
			total += path.get(i).distanceTo(path.get((i + 1) % n));
		}

		double d = (distance % total + total) % total;

		for (int i = 0; i < n; i++) {
			Vec3 a = path.get(i);
			Vec3 c = path.get((i + 1) % n);
			double length = a.distanceTo(c);

			if (d <= length || i == n - 1) {
				double f = length > 0 ? Math.min(1.0, d / length) : 0.0;
				Vec3 p = a.lerp(c, f);
				float yaw = yaw(a, c);
				// Turn towards the next leg over its last stretch (and from the last leg over the first).
				double turn = 0.7;

				if (length - d < turn) {
					Vec3 e = path.get((i + 2) % n);
					yaw = lerpYaw(yaw, yaw(c, e), (float) (0.5 * (1.0 - (length - d) / turn)));
				} else if (d < turn) {
					Vec3 o = path.get((i - 1 + n) % n);
					yaw = lerpYaw(yaw(o, a), yaw, (float) (0.5 + 0.5 * d / turn));
				}

				return new double[] {p.x, p.y, p.z, yaw};
			}

			d -= length;
		}

		return new double[] {path.get(0).x, path.get(0).y, path.get(0).z, 0.0};
	}

	/**
	 * Phase 17: those of a crew who stay in their place collide there (two rows; a third row for the
	 * tall). Phase 18: as wide as they are drawn with their arms (one walked into their shoulders
	 * before), across the cells it reaches, and only colliding: a click there is meant for them.
	 */
	public static void collide(InteriorBuilder b, List<Crew> crew) {
		for (Crew one : crew) {
			if (!one.still()) {
				continue;
			}

			// The hands reach a little in front (at work) or further (talking with gestures).
			double r = WOOKIEE.equals(one.kind()) ? 0.62 : TALK.equals(one.pose()) ? 0.55 : 0.46;
			int y = (int) Math.floor(one.y());
			// Phase 18: a little over the head (nobody can stand on someone's head: from a chair one
			// jumped onto it and on from there to the tops of consoles).
			double tall = one.height() + 0.6;
			int rows = (int) Math.ceil(tall);

			for (int x = (int) Math.floor(one.x() - r); x <= (int) Math.floor(one.x() + r); x++) {
				for (int z = (int) Math.floor(one.z() - r); z <= (int) Math.floor(one.z() + r); z++) {
					double x0 = Math.max(0.0, one.x() - r - x);
					double x1 = Math.min(1.0, one.x() + r - x);
					double z0 = Math.max(0.0, one.z() - r - z);
					double z1 = Math.min(1.0, one.z() + r - z);

					if (x1 - x0 < 0.01 || z1 - z0 < 0.01) {
						continue;
					}

					for (int row = 0; row < rows; row++) {
						if (b.get(x, y + row, z).isAir()) {
							double top = row < rows - 1 ? 1.0 : tall - (rows - 1);
							b.ghost(x, y + row, z, net.minecraft.world.phys.shapes.Shapes.box(x0, 0.0, z0, x1, Math.max(0.1, top), z1));
						}
					}
				}
			}
		}
	}

	/** Minecraft's yaw for walking from a to c (0 = south, 90 = west). */
	public static float yaw(Vec3 a, Vec3 c) {
		return (float) Math.toDegrees(Math.atan2(-(c.x - a.x), c.z - a.z));
	}

	public static float lerpYaw(float a, float c, float f) {
		return a + Mth.wrapDegrees(c - a) * f;
	}
}
