package com.phiro.sabercraft.ship;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * The starships. The X-Wing flies since Phase 11, the TIE Fighter and the TIE Interceptor since
 * Phase 12; the Millennium Falcon and the Star Destroyer are planned and shown in the hangar with
 * what they will be (see docs/phase-11-starships.md).
 *
 * @param id          short name in commands, models, sounds and translation keys
 * @param displayName the name over the ship and in messages to players without SaberCraft
 * @param available   whether it can be called and flown already
 * @param width       collision box width, blocks
 * @param height      collision box height, blocks
 * @param maxSpeed    top speed, blocks per tick
 * @param accel       speed gained per tick with the throttle open
 * @param turn        degrees of yaw per tick at full rudder
 * @param interval    ticks between two laser shots (or salvos)
 * @param salvo       Phase 12: all cannons fire together; otherwise one after the other
 * @param hull        Phase 12: hit points of the hull; at 0 the ship is destroyed
 * @param foils       whether it has S-foils that open in flight (the X-Wing)
 * @param laser       the bolt its cannons fire, a {@code BlasterType} id path ({@code xwing}, {@code tie})
 * @param cannons     where the laser cannons are, in blocks in ship space (x right, y up, z forward)
 */
public record ShipType(String id, String displayName, boolean available, float width, float height, double maxSpeed, double accel, float turn,
		int interval, boolean salvo, int hull, boolean foils, String laser, List<Vec3> cannons) {
	/** The X-Wing; its cannons are at the wing tips with the S-foils open (13 degrees). */
	public static final ShipType XWING = new ShipType("xwing", "X-Wing", true, 3.0F, 1.6F, 1.8, 0.035, 3.5F, 4, false, 40, true, "xwing", List.of(
			new Vec3(2.6, 1.85, 1.75), new Vec3(-2.6, 0.4, 1.75), new Vec3(-2.6, 1.85, 1.75), new Vec3(2.6, 0.4, 1.75)));
	/**
	 * Phase 12, the TIE Fighter: smaller, quicker to turn and to speed up than the X-Wing, but no
	 * shields, so its hull is thinner. Two green cannons under the window fire together.
	 */
	public static final ShipType TIE_FIGHTER = new ShipType("tie_fighter", "TIE Fighter", true, 2.6F, 2.7F, 1.9, 0.045, 4.5F, 6, true, 24, false,
			"tie", List.of(new Vec3(0.1625, 1.0375, 0.825), new Vec3(-0.1625, 1.0375, 0.825)));
	/**
	 * Phase 12, the TIE Interceptor: the fastest and most agile ship, four cannons at the points of
	 * its dagger wings firing one after the other, and the thinnest hull.
	 */
	public static final ShipType TIE_INTERCEPTOR = new ShipType("tie_interceptor", "TIE Interceptor", true, 2.6F, 2.7F, 2.0, 0.05, 5.0F, 3, false, 18,
			false, "tie", List.of(new Vec3(1.168, 1.701, 1.775), new Vec3(-1.168, 1.049, 1.775), new Vec3(-1.168, 1.701, 1.775),
					new Vec3(1.168, 1.049, 1.775)));
	public static final ShipType FALCON = planned("falcon", "Millennium Falcon");
	public static final ShipType STAR_DESTROYER = planned("star_destroyer", "Star Destroyer");

	public static final List<ShipType> ALL = List.of(XWING, TIE_FIGHTER, TIE_INTERCEPTOR, FALCON, STAR_DESTROYER);

	private static ShipType planned(String id, String name) {
		return new ShipType(id, name, false, 3.0F, 2.0F, 1.0, 0.03, 3.0F, 5, false, 40, false, "xwing", List.of());
	}

	public String translationKey() {
		return "ship.sabercraft." + this.id;
	}

	public static Optional<ShipType> byId(String id) {
		String wanted = id.toLowerCase(Locale.ROOT);
		return ALL.stream().filter(type -> type.id.equals(wanted)).findFirst();
	}
}
