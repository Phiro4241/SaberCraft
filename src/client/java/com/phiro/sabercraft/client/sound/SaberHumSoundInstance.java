package com.phiro.sabercraft.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import com.phiro.sabercraft.client.saber.SaberHandState;

/**
 * The looping hum of one lit blade. It is started once when the blade starts to come out and
 * then follows its entity. Every tick it reads the blade state and sets its own volume and pitch:
 * it fades in and out with the blade and gets a little louder and higher when the saber moves.
 *
 * <p>The sound stops itself when the blade is fully retracted, when the entity is gone, or when
 * the tracker lets go of it. So it can never keep playing after the saber was put away.
 */
public class SaberHumSoundInstance extends AbstractTickableSoundInstance {
	/** Ticks over which a new hum fades in, so it never starts with a click. */
	private static final int FADE_IN_TICKS = 6;

	private final LivingEntity entity;
	private final SaberHandState state;
	private final float baseVolume;
	private int age;
	private boolean released;

	public SaberHumSoundInstance(LivingEntity entity, SaberHandState state, SaberSoundSet sounds) {
		super(sounds.hum(), entity.getSoundSource(), RandomSource.create(entity.getId() * 31L + state.hand().ordinal()));
		this.entity = entity;
		this.state = state;
		this.baseVolume = sounds.humVolume();
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.volume = 0.0F;
		this.followEntity();
	}

	@Override
	public void tick() {
		if (this.released || this.entity.isRemoved() || !this.state.isLit()) {
			this.stop();
			return;
		}

		this.age++;
		this.followEntity();

		float fadeIn = Math.min(1.0F, this.age / (float) FADE_IN_TICKS);
		// The hum follows the visible blade length, so it swells while the blade comes out and dies
		// away while it goes back in. Smoothstep avoids a hard start and end.
		float blade = this.state.extension(1.0F);
		float shape = blade * blade * (3.0F - 2.0F * blade);
		float motion = this.state.motion();
		this.volume = this.baseVolume * fadeIn * shape * (1.0F + 0.45F * motion);
		this.pitch = 1.0F + 0.07F * motion + 0.04F * (blade - 1.0F);
	}

	/** Called by the tracker when the blade or its entity is gone. The engine stops it next tick. */
	public void release() {
		this.released = true;
	}

	private void followEntity() {
		this.x = this.entity.getX();
		this.y = this.entity.getEyeY() - 0.4;
		this.z = this.entity.getZ();
	}

	@Override
	public boolean canStartSilent() {
		// The hum starts at volume 0 and fades in. Without this the engine would skip it.
		return true;
	}

	@Override
	public boolean canPlaySound() {
		return !this.entity.isSilent();
	}

	@Override
	public float getPitch() {
		return Mth.clamp(super.getPitch(), 0.5F, 2.0F);
	}
}
