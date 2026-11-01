package com.phiro.sabercraft.client.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;

/**
 * Phase 12: SaberCraft's own volume sliders (menu area "Sound"), on top of Minecraft's sliders.
 *
 * <p>Every sound of the mod has an id under {@code sabercraft:}; the group is read from the start
 * of its path. {@code SoundEngineMixin} multiplies each sound's volume with {@link #scale} when it
 * starts and every tick while it plays, so a moved slider is heard at once, also on the running
 * hum. Sounds of other mods and of vanilla are never touched.
 *
 * <ul>
 * <li>Lightsabers ({@code saber.*}, {@code force.*}): {@code saber_volume}; the hum loop
 * ({@code saber.hum}, {@code saber.dark.hum}) also {@code saber_hum_volume}.</li>
 * <li>Droids ({@code droid.*}): {@code droid_volume}; their idle chatter ({@code droid.*.ambient})
 * only with {@code droid_chatter} on.</li>
 * <li>Starships ({@code ship.*}): {@code ship_volume}.</li>
 * </ul>
 */
public final class SoundVolumes {
	private SoundVolumes() {
	}

	/** The factor for this sound, 1 for every sound that is not SaberCraft's. */
	public static float scale(SoundInstance instance) {
		Identifier id = instance.getIdentifier();
		return id != null && SaberCraft.MOD_ID.equals(id.getNamespace()) ? scale(id.getPath()) : 1.0F;
	}

	/** The factor for a SaberCraft sound path such as {@code saber.hum}. */
	public static float scale(String path) {
		ImmersionConfig config = ImmersionConfig.get();

		if (path.startsWith("saber.") || path.startsWith("force.")) {
			return path.endsWith(".hum") ? config.saberVolume * config.saberHumVolume : config.saberVolume;
		}

		if (path.startsWith("droid.")) {
			return path.endsWith(".ambient") && !config.droidChatter ? 0.0F : config.droidVolume;
		}

		if (path.startsWith("ship.")) {
			return config.shipVolume;
		}

		return 1.0F;
	}
}
