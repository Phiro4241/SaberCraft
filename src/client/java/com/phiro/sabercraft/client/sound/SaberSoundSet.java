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

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(SaberCraft.id(name));
	}
}
