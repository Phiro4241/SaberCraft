package com.phiro.sabercraft.client.sound;

import net.minecraft.sounds.SoundEvent;

import com.phiro.sabercraft.SaberCraft;

/**
 * The sounds of one saber model. Every sound is defined in {@code assets/sabercraft/sounds.json},
 * so a resource pack can replace them, and the swing event has several variants so swings do not
 * all sound the same.
 *
 * <p>The events are only played on the client and are not put into the sound registry. That keeps
 * the mod purely client-side: it works on servers without SaberCraft and never sends packets.
 *
 * @param ignite      one-shot when the blade comes out
 * @param retract     one-shot when the blade goes back in
 * @param hum         seamless loop while the blade is out
 * @param swing       one-shot for swings and fast movements
 * @param humVolume   loudness of the hum while the saber is held still
 * @param swingVolume loudness of a full swing
 */
public record SaberSoundSet(
		SoundEvent ignite,
		SoundEvent retract,
		SoundEvent hum,
		SoundEvent swing,
		float humVolume,
		float swingVolume
) {
	public static final SaberSoundSet DEFAULT = new SaberSoundSet(
			event("saber.ignite"),
			event("saber.retract"),
			event("saber.hum"),
			event("saber.swing"),
			0.3F,
			0.9F
	);

	/** Deeper, rougher hum and a heavier ignition for the dark hilt. */
	public static final SaberSoundSet DARK = new SaberSoundSet(
			event("saber.dark.ignite"),
			event("saber.dark.retract"),
			event("saber.dark.hum"),
			event("saber.dark.swing"),
			0.32F,
			0.9F
	);

	/** Default sounds with a louder hum, because two blades are lit. */
	public static final SaberSoundSet DOUBLE = new SaberSoundSet(
			DEFAULT.ignite(),
			DEFAULT.retract(),
			DEFAULT.hum(),
			DEFAULT.swing(),
			0.42F,
			1.0F
	);

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(SaberCraft.id(name));
	}
}
