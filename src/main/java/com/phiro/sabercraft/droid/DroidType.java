package com.phiro.sabercraft.droid;

import java.util.Locale;
import java.util.Optional;

/**
 * The droids a player can call (Phase 11). Their sizes are the boxes the server collides with and
 * the client picks; their models are made by tools/gen_droids.py.
 *
 * <p>Every droid has one ability (the menu button, or {@code /sabercraft droid ability}):
 * <ul>
 * <li>R2-D2 repairs the item in the owner's main hand a little.</li>
 * <li>C-3PO tells the owner about the danger around, the time and the weather.</li>
 * <li>BB-8 scans for hostile mobs; they glow for a few seconds.</li>
 * <li>Chopper zaps hostile mobs right next to it.</li>
 * <li>The mouse droid fetches dropped items nearby to its owner.</li>
 * <li>The probe droid looks for valuable ores around and reports the nearest.</li>
 * <li>The B1 battle droid fires a short burst of blaster bolts at the nearest hostile mob.</li>
 * <li>K-2SO shoves hostile mobs away from its owner.</li>
 * </ul>
 */
public enum DroidType {
	R2D2("r2d2", "R2-D2", 0.7F, 1.1F, 0.30, 600, false),
	C3PO("c3po", "C-3PO", 0.6F, 1.8F, 0.24, 200, false),
	BB8("bb8", "BB-8", 0.6F, 0.75F, 0.34, 400, false),
	CHOPPER("chopper", "Chopper", 0.6F, 1.05F, 0.30, 300, false),
	MOUSE("mouse", "Mouse Droid", 0.4F, 0.35F, 0.36, 200, false),
	PROBE("probe", "Probe Droid", 0.8F, 2.0F, 0.26, 600, true),
	B1("b1", "B1", 0.6F, 1.95F, 0.28, 100, false),
	K2SO("k2so", "K-2SO", 0.7F, 2.2F, 0.30, 200, false);

	private final String id;
	private final String displayName;
	private final float width;
	private final float height;
	private final double speed;
	private final int cooldown;
	private final boolean hovers;

	DroidType(String id, String displayName, float width, float height, double speed, int cooldown, boolean hovers) {
		this.id = id;
		this.displayName = displayName;
		this.width = width;
		this.height = height;
		this.speed = speed;
		this.cooldown = cooldown;
		this.hovers = hovers;
	}

	/** The short name in commands, models, sounds and translation keys. */
	public String id() {
		return this.id;
	}

	/** The name over the droid; the same in every language. */
	public String displayName() {
		return this.displayName;
	}

	public float width() {
		return this.width;
	}

	public float height() {
		return this.height;
	}

	/** Base movement speed (a pig has 0.25). */
	public double speed() {
		return this.speed;
	}

	/** Ticks between two uses of the ability, before the config multiplier. */
	public int cooldown() {
		return this.cooldown;
	}

	/** Whether the model floats above the ground (the probe droid). */
	public boolean hovers() {
		return this.hovers;
	}

	public String translationKey() {
		return "droid.sabercraft." + this.id;
	}

	public static Optional<DroidType> byId(String id) {
		String wanted = id.toLowerCase(Locale.ROOT);

		for (DroidType type : values()) {
			if (type.id.equals(wanted)) {
				return Optional.of(type);
			}
		}

		return Optional.empty();
	}
}
