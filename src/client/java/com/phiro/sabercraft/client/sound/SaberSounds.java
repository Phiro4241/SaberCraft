package com.phiro.sabercraft.client.sound;

import net.minecraft.sounds.SoundEvent;

import com.phiro.sabercraft.SaberCraft;

/**
 * Combat sounds shared by all sabers, defined in {@code assets/sabercraft/sounds.json}. Like the
 * saber sounds they are only played on the client and are not registered.
 */
public final class SaberSounds {
	/** Long whoosh of a spinning attack or the twirl of the double-bladed staff. */
	public static final SoundEvent SPIN = event("saber.spin");
	/** The blade cuts into a target: short sizzle. */
	public static final SoundEvent HIT = event("saber.hit");
	/** The blade strikes a wall or the floor: crackling sparks. */
	public static final SoundEvent SCORCH = event("saber.scorch");
	/** Raising the defensive stance. */
	public static final SoundEvent GUARD = event("saber.guard");
	/** A blocked hit: energy clash. */
	public static final SoundEvent CLASH = event("saber.clash");
	/** A perfectly timed parry: bright, ringing clash. */
	public static final SoundEvent PARRY = event("saber.parry");
	/** The force push: deep thump with rushing air. */
	public static final SoundEvent PUSH = event("saber.push");
	/** Lunge forward: short rush of air. */
	public static final SoundEvent DASH = event("saber.dash");

	private SaberSounds() {
	}

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(SaberCraft.id(name));
	}
}
