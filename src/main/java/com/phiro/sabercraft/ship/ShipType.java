package com.phiro.sabercraft.ship;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * The starships. The X-Wing flies since Phase 11, the TIE Fighter and the TIE Interceptor since
 * Phase 12, the Millennium Falcon and the Star Destroyer since Phase 13 (see
 * docs/phase-13-status.md).
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
 * @param handling    Phase 13: how it flies, how far the camera stays behind, how long it can fly
 *                    and how hard it may hit the ground (see {@link Handling})
 */
public record ShipType(String id, String displayName, boolean available, float width, float height, double maxSpeed, double accel, float turn,
		int interval, boolean salvo, int hull, boolean foils, String laser, List<Vec3> cannons, Handling handling) {
	/**
	 * Phase 13: the parts of a ship's flight that differ between a fighter and a capital ship.
	 *
	 * @param capital    a capital ship (the Star Destroyer): it never pitches its nose and never
	 *                   lands, it keeps {@code hoverHeight} blocks above the ground and climbs or
	 *                   sinks straight with jump and sprint
	 * @param hoverHeight for a capital ship, the least height above the ground, blocks
	 * @param camera     how far the third-person camera stays behind, blocks
	 * @param bank       how far the model leans into a turn, 1 for a fighter, 0 for none
	 * @param energy     seconds it can fly with full energy (before {@code ship_flight_seconds})
	 * @param recharge   seconds from empty to full when landed or parked (before {@code ship_recharge_seconds})
	 * @param safeImpact fastest touch-down or bump that does no harm, blocks per tick; faster hits
	 *                   cost hull, at {@code wreckImpact} all of it
	 * @param wreckImpact a hit this fast destroys the ship
	 * @param modelRadius how far the model reaches from the middle, blocks (for drawing it when
	 *                   only a part of it is on screen)
	 */
	public record Handling(boolean capital, float hoverHeight, float camera, float bank, int energy, int recharge, double safeImpact,
			double wreckImpact, float modelRadius) {
		static Handling fighter(int energy, int recharge, float modelRadius) {
			return new Handling(false, 0.0F, 9.0F, 1.0F, energy, recharge, 0.5, 1.75, modelRadius);
		}
	}

	/** The X-Wing; its cannons are at the wing tips with the S-foils open (13 degrees). */
	public static final ShipType XWING = new ShipType("xwing", "X-Wing", true, 3.0F, 1.6F, 1.8, 0.035, 3.5F, 4, false, 40, true, "xwing", List.of(
			new Vec3(2.6, 1.85, 1.75), new Vec3(-2.6, 0.4, 1.75), new Vec3(-2.6, 1.85, 1.75), new Vec3(2.6, 0.4, 1.75)), Handling.fighter(300, 45, 6.0F));
	/**
	 * Phase 12, the TIE Fighter: smaller, quicker to turn and to speed up than the X-Wing, but no
	 * shields, so its hull is thinner. Two green cannons under the window fire together.
	 */
	public static final ShipType TIE_FIGHTER = new ShipType("tie_fighter", "TIE Fighter", true, 2.6F, 2.7F, 1.9, 0.045, 4.5F, 6, true, 24, false,
			"tie", List.of(new Vec3(0.1625, 1.0375, 0.825), new Vec3(-0.1625, 1.0375, 0.825)), Handling.fighter(180, 30, 3.5F));
	/**
	 * Phase 12, the TIE Interceptor: the fastest and most agile ship, four cannons at the points of
	 * its dagger wings firing one after the other, and the thinnest hull.
	 */
	public static final ShipType TIE_INTERCEPTOR = new ShipType("tie_interceptor", "TIE Interceptor", true, 2.6F, 2.7F, 2.0, 0.05, 5.0F, 3, false, 18,
			false, "tie", List.of(new Vec3(1.168, 1.701, 1.775), new Vec3(-1.168, 1.049, 1.775), new Vec3(-1.168, 1.701, 1.775),
					new Vec3(1.168, 1.049, 1.775)), Handling.fighter(150, 30, 3.5F));
	/**
	 * Phase 13, the Millennium Falcon: a light freighter about twice as wide as an X-Wing. Fast in
	 * a straight line but heavy to turn and slow to speed up, with a strong hull and the longest
	 * range of the small ships. Its upper and lower quad cannons fire together. The radar dish
	 * turns and the landing legs come out near the ground (client only).
	 */
	public static final ShipType FALCON = new ShipType("falcon", "Millennium Falcon", true, 7.0F, 2.6F, 1.85, 0.022, 2.4F, 5, true, 90, false,
			"xwing", List.of(new Vec3(0.0, 2.2, 1.8), new Vec3(0.0, 0.1, 1.8)),
			new Handling(false, 0.0F, 16.0F, 0.6F, 480, 60, 0.45, 1.6, 7.5F));
	/**
	 * Phase 13, the Star Destroyer: a capital ship many times the size of every other ship. It
	 * cannot land and never dips its nose: it hovers high above the ground, turns and speeds up
	 * very slowly, climbs and sinks straight, and fires heavy green turbolasers from batteries
	 * along both sides. Its collision box is much smaller than the model (the wedge would get
	 * stuck everywhere); it keeps its height above the ground instead.
	 */
	public static final ShipType STAR_DESTROYER = new ShipType("star_destroyer", "Star Destroyer", true, 14.0F, 7.0F, 0.85, 0.006, 0.8F, 8, false,
			400, false, "turbolaser", List.of(new Vec3(6.0, 4.8, 14.0), new Vec3(-6.0, 4.8, 14.0), new Vec3(10.2, 4.9, -2.0),
					new Vec3(-10.2, 4.9, -2.0), new Vec3(1.8, 4.4, 30.0), new Vec3(-1.8, 4.4, 30.0)),
			new Handling(true, 14.0F, 72.0F, 0.0F, 900, 120, 0.4, 1.2, 40.0F));

	public static final List<ShipType> ALL = List.of(XWING, TIE_FIGHTER, TIE_INTERCEPTOR, FALCON, STAR_DESTROYER);

	/** Phase 13: a capital ship (see {@link Handling#capital()}). */
	public boolean capital() {
		return this.handling.capital();
	}

	public String translationKey() {
		return "ship.sabercraft." + this.id;
	}

	public static Optional<ShipType> byId(String id) {
		String wanted = id.toLowerCase(Locale.ROOT);
		return ALL.stream().filter(type -> type.id.equals(wanted)).findFirst();
	}
}
