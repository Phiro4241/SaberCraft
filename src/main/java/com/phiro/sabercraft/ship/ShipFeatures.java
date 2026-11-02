package com.phiro.sabercraft.ship;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 14: what each starship has beyond its flight (see {@link ShipType}): where its cockpit is,
 * how its engines sound, how tough its hull is against which weapons, how its hull is shaped for
 * hits, whether it can jump to hyperspace, and whether it has an interior to walk around in.
 *
 * <p>One entry per ship, all in one place, so a new ship only needs a line here (and its model,
 * sounds and, if it has one, an interior plan in {@code com.phiro.sabercraft.interior}).
 *
 * @param cockpit     where the pilot's eyes are in the cockpit view, in blocks in ship space
 *                    (x right, y up from the ground under the middle, z forward)
 * @param frame       the cockpit frame drawn over the view: {@code xwing}, {@code tie}, {@code falcon},
 *                    {@code bridge} or {@code none}
 * @param hideHull    parts of the own model that are left out in the cockpit view (they would
 *                    stand right in front of the eyes); empty: the whole model is drawn
 * @param sound       the engine family: the loops {@code ship.<sound>.engine} and
 *                    {@code ship.<sound>.thrust} and the one-shots for speeding up and braking
 * @param smallArms   how much of the damage of hand weapons (sabers, blasters, arrows, fists) the
 *                    hull takes; big ships shrug off most of it
 * @param heavy       how much of the damage of ship cannons and explosions the hull takes
 * @param hullBoxes   the hull for hits, as boxes in ship space (blocks); the collision box of a big
 *                    ship is much smaller than its model, so bolts are checked against these too
 * @param hyperspace  whether it has a hyperdrive
 * @param jumpScale   how far it jumps compared to {@code hyperspace_distance}
 * @param interior    the interior plan ({@code falcon}, {@code star_destroyer}) or {@code null}
 */
public record ShipFeatures(Vec3 cockpit, String frame, List<String> hideHull, String sound, float smallArms, float heavy, List<AABB> hullBoxes,
		boolean hyperspace, float jumpScale, @Nullable String interior) {
	public static final ShipFeatures XWING = new ShipFeatures(new Vec3(0.0, 1.5, 0.3), "xwing", List.of(), "xwing", 1.0F, 1.0F,
			List.of(new AABB(-0.6, 0.6, -2.0, 0.6, 1.7, 3.1)), true, 1.0F, null);
	public static final ShipFeatures TIE_FIGHTER = new ShipFeatures(new Vec3(0.0, 1.4, 0.1), "tie", List.of(), "tie_fighter", 1.0F, 1.0F,
			List.of(new AABB(-0.8, 0.5, -0.8, 0.8, 2.2, 0.8)), true, 0.85F, null);
	public static final ShipFeatures TIE_INTERCEPTOR = new ShipFeatures(new Vec3(0.0, 1.4, 0.1), "tie", List.of(), "tie_interceptor", 1.0F,
			1.0F, List.of(new AABB(-0.8, 0.5, -0.8, 0.8, 2.2, 0.8)), true, 0.85F, null);
	public static final ShipFeatures FALCON = new ShipFeatures(new Vec3(4.12, 1.2, 2.6), "falcon", List.of(), "falcon", 0.6F, 0.85F,
			List.of(new AABB(-5.2, 0.3, -6.0, 5.2, 2.0, 4.0), new AABB(-2.4, 0.3, 2.0, 2.4, 1.9, 6.0), new AABB(2.4, 0.6, 0.0, 4.7, 1.8, 3.8)),
			true, 1.0F, "falcon");
	public static final ShipFeatures STAR_DESTROYER = new ShipFeatures(new Vec3(0.0, 14.3, -23.0), "bridge", List.of(), "star_destroyer", 0.15F,
			0.5F, List.of(new AABB(-8.0, 0.0, 6.0, 8.0, 6.0, 40.0), new AABB(-16.0, 0.0, -12.0, 16.0, 6.5, 6.0), new AABB(-21.0, 0.0, -32.0, 21.0, 7.5,
					-12.0), new AABB(-10.0, 6.0, -30.0, 10.0, 10.0, -12.0), new AABB(-8.5, 10.0, -26.0, 8.5, 15.0, -22.0)), true, 0.6F,
			"star_destroyer");

	private static final Map<String, ShipFeatures> BY_SHIP = Map.of(ShipType.XWING.id(), XWING, ShipType.TIE_FIGHTER.id(), TIE_FIGHTER,
			ShipType.TIE_INTERCEPTOR.id(), TIE_INTERCEPTOR, ShipType.FALCON.id(), FALCON, ShipType.STAR_DESTROYER.id(), STAR_DESTROYER);

	/** The features of a ship; a ship without an entry gets plain fighter values. */
	public static ShipFeatures of(ShipType type) {
		ShipFeatures features = BY_SHIP.get(type.id());
		return features != null ? features
				: new ShipFeatures(new Vec3(0.0, type.height() * 0.7, 0.0), "none", List.of(), type.id(), 1.0F, 1.0F,
						List.of(new AABB(-type.width() / 2.0, 0.0, -type.width() / 2.0, type.width() / 2.0, type.height(), type.width() / 2.0)),
						false, 1.0F, null);
	}

	/**
	 * A point in ship space (x right, y up, z forward) in the world, for a ship at {@code origin}
	 * turned to {@code yaw} and pitched to {@code pitch} around {@code pivot} blocks above its origin.
	 */
	public static Vec3 toWorld(Vec3 local, Vec3 origin, float yaw, float pitch, double pivot) {
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		Vec3 up = right.cross(forward);
		Vec3 centre = origin.add(0.0, pivot, 0.0);
		return centre.add(right.scale(local.x)).add(up.scale(local.y - pivot)).add(forward.scale(local.z));
	}

	/**
	 * Whether the line from {@code from} to {@code to} (world) crosses the hull of a ship at
	 * {@code origin} turned to {@code yaw} (the pitch is ignored: big ships barely pitch). Returns
	 * the share of the way where it enters, or -1.
	 */
	public double clip(Vec3 origin, float yaw, Vec3 from, Vec3 to) {
		// Into ship space: the inverse turn around y.
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		Vec3 a = from.subtract(origin);
		Vec3 b = to.subtract(origin);
		Vec3 localFrom = new Vec3(a.dot(right), a.y, a.dot(forward));
		Vec3 localTo = new Vec3(b.dot(right), b.y, b.dot(forward));
		double best = -1.0;

		for (AABB box : this.hullBoxes) {
			if (box.contains(localFrom)) {
				return 0.0;
			}

			var hit = box.clip(localFrom, localTo);

			if (hit.isPresent()) {
				double share = hit.get().distanceTo(localFrom) / Math.max(1.0E-6, localTo.distanceTo(localFrom));

				if (best < 0.0 || share < best) {
					best = share;
				}
			}
		}

		return best;
	}
}
