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
 *
 * <p>Phase 26: the fades follow an equal-power curve (a quarter sine): while one piece fades out
 * and the next fades in, the loudness stays even (with straight fades it sagged in the middle and
 * the new piece seemed to jump in at the start). A piece that gives way to another can wait until
 * that one really plays (the sound engine starts a stream a few ticks later), so that no gap opens.
 */
final class MusicPiece extends AbstractTickableSoundInstance {
	private final float fadeInStep;
	private float gain;
	private float fadeOutStep;
	private float master = 1.0F;
	private int age;
	/** Phase 33: ticks of the story's music this piece waited on its list without the sound engine playing it. */
	int idle;
	/** Phase 26: the piece this one waits for before it fades out, how long it will fade then, how long it waited. */
	private @Nullable MusicPiece waitFor;
	private float pendingOut;
	private int waited;
	/** Phase 26: at most this long (ticks) a piece waits for the next to start before it fades anyway. */
	static final int WAIT_MAX = 12;

	MusicPiece(Identifier id, boolean loop, float fadeInTicks) {
		this(id, loop, fadeInTicks, SoundSource.MUSIC);
	}

	/** A piece in another sound category: the planet sounds (Phase 10) play as ambience. */
	MusicPiece(Identifier id, boolean loop, float fadeInTicks, SoundSource source) {
		super(SoundEvent.createVariableRangeEvent(id), source, RandomSource.create());
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

		if (this.waitFor != null) {
			this.waited++;

			if (this.waitFor.age > 0 || this.waitFor.isStopped() || this.waited > WAIT_MAX) {
				this.fadeOut(this.pendingOut);
			}
		}

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
		this.volume = Math.max(0.0001F, curve(this.gain) * this.master);
	}

	/** Phase 26: the equal-power fade curve: 0..1 of a fade to the loudness (a quarter sine). */
	static float curve(float gain) {
		return net.minecraft.util.Mth.sin(net.minecraft.util.Mth.clamp(gain, 0.0F, 1.0F) * net.minecraft.util.Mth.HALF_PI);
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	void setMaster(float master) {
		this.master = master;
	}

	void fadeOut(float ticks) {
		this.waitFor = null;
		this.fadeOutStep = Math.max(this.gain, 0.05F) / Math.max(1.0F, ticks);
	}

	/** Phase 26: fades out once {@code next} plays (or after a moment at most), so that no gap opens between them. */
	void fadeOutAfter(@Nullable MusicPiece next, float ticks) {
		if (next == null) {
			this.fadeOut(ticks);
			return;
		}

		this.waitFor = next;
		this.pendingOut = ticks;
		this.waited = 0;
	}

	boolean fadingOut() {
		return this.fadeOutStep > 0.0F || this.waitFor != null;
	}

	/** Phase 26: how loud it is now within its fades, 0..1 (tests). */
	float level() {
		return curve(this.gain);
	}

	int age() {
		return this.age;
	}

	boolean isPlaying(Minecraft client) {
		return client.getSoundManager().isActive(this);
	}
}
