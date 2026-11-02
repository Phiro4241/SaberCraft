package com.phiro.sabercraft.client.ship;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 14: the quiet sound inside a walkable ship: the Falcon's humming, ticking machinery, the
 * deep drone and distant chimes of a Star Destroyer's bridge. It fades in on boarding and out on
 * leaving. Which ship it is follows from where the player stands (each plan has its own row of
 * plots), so the server sends nothing for it.
 */
public final class InteriorAmbience {
	private static @Nullable Loop playing;

	private InteriorAmbience() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(InteriorAmbience::tick);
	}

	private static void tick(Minecraft client) {
		String wanted = null;

		if (client.level != null && client.player != null && ShipInteriors.isInterior(client.level) && ImmersionConfig.get().shipInteriorAmbient) {
			InteriorPlan plan = ShipInteriors.planAt(client.player.getX(), client.player.getZ());
			wanted = plan != null ? plan.ambient() : null;
		}

		Loop loop = playing;

		if (loop != null && (loop.isStopped() || !loop.name.equals(wanted))) {
			loop.leaving = true;

			if (loop.isStopped()) {
				playing = null;
			}
		}

		if (wanted != null && (playing == null || playing.leaving)) {
			if (playing != null) {
				playing.leaving = true;
			}

			Loop fresh = new Loop(wanted);
			playing = fresh;
			client.getSoundManager().play(fresh);
		}
	}

	/** The name of the ambient loop playing now, or {@code null}; for tests. */
	public static @Nullable String playing() {
		Loop loop = playing;
		return loop != null && !loop.leaving && !loop.isStopped() ? loop.name : null;
	}

	private static final class Loop extends AbstractTickableSoundInstance {
		private static final float VOLUME = 0.35F;
		private final String name;
		private boolean leaving;
		private int age;

		Loop(String name) {
			super(ShipEngineSound.event(name), SoundSource.AMBIENT, RandomSource.create());
			this.name = name;
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.0F;
		}

		@Override
		public void tick() {
			this.age++;

			if (this.leaving) {
				this.volume -= VOLUME / 30.0F;

				if (this.volume <= 0.0F) {
					this.stop();
				}
			} else {
				this.volume = Math.min(VOLUME, this.age * VOLUME / 40.0F);
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
