package com.phiro.sabercraft.ship;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * The starships (Phase 11). The X-Wing flies; the others are planned and shown in the hangar with
 * what they will be (see docs/phase-11-starships.md).
 *
 * @param id         short name in commands, models and translation keys
 * @param available  whether it can be called and flown already
 * @param width      collision box width, blocks
 * @param height     collision box height, blocks
 * @param maxSpeed   top speed, blocks per tick
 * @param accel      speed gained per tick with the throttle open
 * @param turn       degrees of yaw per tick at full rudder
 * @param interval   ticks between two laser shots
 * @param cannons    where the laser cannons are, in blocks in ship space (x right, y up, z forward)
 */
public record ShipType(String id, boolean available, float width, float height, double maxSpeed, double accel, float turn, int interval,
		List<Vec3> cannons) {
	/** The X-Wing; its cannons are at the wing tips with the S-foils open (13 degrees). */
	public static final ShipType XWING = new ShipType("xwing", true, 3.0F, 1.6F, 1.8, 0.035, 3.5F, 4, List.of(
			new Vec3(2.6, 1.85, 1.75), new Vec3(-2.6, 0.4, 1.75), new Vec3(-2.6, 1.85, 1.75), new Vec3(2.6, 0.4, 1.75)));
	public static final ShipType TIE_FIGHTER = planned("tie_fighter");
	public static final ShipType TIE_INTERCEPTOR = planned("tie_interceptor");
	public static final ShipType FALCON = planned("falcon");
	public static final ShipType STAR_DESTROYER = planned("star_destroyer");

	public static final List<ShipType> ALL = List.of(XWING, TIE_FIGHTER, TIE_INTERCEPTOR, FALCON, STAR_DESTROYER);

	private static ShipType planned(String id) {
		return new ShipType(id, false, 3.0F, 2.0F, 1.0, 0.03, 3.0F, 5, List.of());
	}

	public String translationKey() {
		return "ship.sabercraft." + this.id;
	}

	public static Optional<ShipType> byId(String id) {
		String wanted = id.toLowerCase(Locale.ROOT);
		return ALL.stream().filter(type -> type.id.equals(wanted)).findFirst();
	}
}
