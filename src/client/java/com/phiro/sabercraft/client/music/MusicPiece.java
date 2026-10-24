package com.phiro.sabercraft.client.music;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * One piece of the SaberCraft music while it plays: non-positional, in the music category (so the
 * vanilla music slider applies), with its own fade in and fade out for the crossfades.
 */
final class MusicPiece extends AbstractTickableSoundInstance {
	private final float fadeInStep;
	private float gain;
	private float fadeOutStep;
	private float master = 1.0F;
	private int age;

	MusicPiece(Identifier id, boolean loop, float fadeInTicks) {
		super(SoundEvent.createVariableRangeEvent(id), SoundSource.MUSIC, RandomSource.create());
		this.looping = loop;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.fadeInStep = 1.0F / Math.max(1.0F, fadeInTicks);
		this.volume = 0.0001F;
	}

	@Override
	public @Nullable WeighedSoundEvents resolve(SoundManager soundManager) {
		Path file = UserMusic.file(this.identifier);

		if (file == null) {
			file = StarWarsMusic.file(this.identifier);
		}

		if (file == null) {
			return super.resolve(soundManager);
		}

		// A file of the player's own (SaberCraft or Star Wars mode): streamed from the config folder
		// (see SoundBufferLibraryMixin).
		this.sound = new Sound(this.identifier, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, 16);
		return new WeighedSoundEvents(this.identifier, null);
	}

	@Override
	public void tick() {
		this.age++;

		if (this.fadeOutStep > 0.0F) {
			this.gain -= this.fadeOutStep;

			if (this.gain <= 0.0F) {
				this.gain = 0.0F;
				this.stop();
			}
		} else {
			this.gain = Math.min(1.0F, this.gain + this.fadeInStep);
		}

		// Never exactly 0, or the sound engine would drop a piece that is just fading in.
		this.volume = Math.max(0.0001F, this.gain * this.master);
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	void setMaster(float master) {
		this.master = master;
	}

	void fadeOut(float ticks) {
		this.fadeOutStep = Math.max(this.gain, 0.05F) / Math.max(1.0F, ticks);
	}

	boolean fadingOut() {
		return this.fadeOutStep > 0.0F;
	}

	int age() {
		return this.age;
	}

	boolean isPlaying(Minecraft client) {
		return client.getSoundManager().isActive(this);
	}
}
