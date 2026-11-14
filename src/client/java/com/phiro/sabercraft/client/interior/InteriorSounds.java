package com.phiro.sabercraft.client.interior;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;

/**
 * Phase 15: the machines and computers of the ships' rooms can be heard where they are: the
 * reactor's drone, consoles chattering on the bridge, the Falcon's hyperdrive. Each spot of a
 * plan's visuals plays a quiet positional loop while the player is near it.
 */
final class InteriorSounds {
	private static final Map<String, Spot> PLAYING = new HashMap<>();

	private InteriorSounds() {
	}

	/** Called every client tick with the models the player can see now. */
	static void tick(Minecraft client, Iterable<InteriorScene.Model> models) {
		if (client.player == null || !ImmersionConfig.get().shipInteriorAmbient) {
			stopAll();
			return;
		}

		Vec3 eye = client.player.getEyePosition();
		java.util.Set<String> wanted = new java.util.HashSet<>();

		for (InteriorScene.Model model : models) {
			int index = 0;

			for (InteriorVisuals.SoundSpot spot : model.visuals.sounds()) {
				String key = model.key + "#" + index++;
				BlockPos o = model.origin;
				Vec3 at = new Vec3(o.getX() + spot.x(), o.getY() + spot.y(), o.getZ() + spot.z());

				if (eye.distanceTo(at) < spot.range() + 2.0) {
					wanted.add(key);

					if (!PLAYING.containsKey(key) || PLAYING.get(key).isStopped()) {
						Spot sound = new Spot(spot, at);
						PLAYING.put(key, sound);
						client.getSoundManager().play(sound);
					}
				}
			}
		}

		PLAYING.entrySet().removeIf(entry -> {
			if (!wanted.contains(entry.getKey())) {
				entry.getValue().leaving = true;
				return true;
			}

			return false;
		});
	}

	static void stopAll() {
		for (Spot spot : PLAYING.values()) {
			spot.leaving = true;
		}

		PLAYING.clear();
	}

	/** How many spots play now (for tests). */
	static int playing() {
		return PLAYING.size();
	}

	private static final class Spot extends AbstractTickableSoundInstance {
		private final float target;
		private boolean leaving;

		Spot(InteriorVisuals.SoundSpot spot, Vec3 at) {
			super(SoundEvent.createVariableRangeEvent(SaberCraft.id(spot.sound())), SoundSource.AMBIENT, RandomSource.create());
			this.looping = true;
			this.delay = 0;
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.target = spot.volume();
			this.volume = 0.0F;
		}

		@Override
		public void tick() {
			if (this.leaving) {
				this.volume -= this.target / 20.0F;

				if (this.volume <= 0.0F) {
					this.stop();
				}
			} else {
				this.volume = Math.min(this.target, this.volume + this.target / 30.0F);
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
