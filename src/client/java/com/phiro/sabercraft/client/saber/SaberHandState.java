package com.phiro.sabercraft.client.saber;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.client.sound.SaberHumSoundInstance;

/**
 * Blade state of one hand of one entity, kept on the client only.
 *
 * <p>The animation runs on game ticks: {@link #progress} moves linearly towards 1 while the blade
 * should be out and towards 0 otherwise, and the render code interpolates between the last two
 * ticks for smooth movement at any frame rate. The visible blade length is the progress put through
 * the curve of the current direction. When the direction changes halfway, the progress is mapped
 * onto the other curve so the blade continues from exactly where it is.
 */
public final class SaberHandState {
	/** How long the blade flashes up after it struck another blade, in ticks (Phase 10). */
	static final int CLASH_TICKS = 5;
	/** Extra brightness right at the clash. */
	private static final float CLASH_FLARE = 0.7F;

	private final InteractionHand hand;
	private SaberProfile profile;
	private boolean active;
	private float progress;
	private float previousProgress;
	private float motion;
	private int igniteWait;
	private @Nullable SaberHumSoundInstance hum;
	private int humRetryCooldown;
	private int clash;

	SaberHandState(InteractionHand hand, SaberProfile profile) {
		this.hand = hand;
		this.profile = profile;
	}

	/** What happened to the blade during one tick. */
	enum Transition {
		NONE,
		IGNITE,
		RETRACT
	}

	/**
	 * Advances the animation by one tick.
	 *
	 * @param shouldBeLit whether the blade should be out
	 * @param newProfile  the profile of the saber in this hand, or {@code null} if there is none.
	 *                    A retracting blade keeps the profile it had.
	 */
	Transition tick(boolean shouldBeLit, @Nullable SaberProfile newProfile) {
		if (newProfile != null && newProfile != this.profile) {
			this.switchProfile(newProfile);
		}

		Transition transition = Transition.NONE;

		if (this.clash > 0) {
			this.clash--;
		}

		SaberAnimation animation = this.profile.animation();

		if (shouldBeLit != this.active) {
			// Continue from the current blade length on the other curve.
			float visible = this.visible(this.progress);
			this.active = shouldBeLit;
			this.progress = this.curve().inverse(visible);
			this.igniteWait = 0;

			if (!shouldBeLit) {
				transition = Transition.RETRACT;
			} else if (this.progress <= 0.0F && animation.igniteDelay() > 0) {
				// Freshly drawn: give the hand time to come up before the blade ignites.
				this.igniteWait = animation.igniteDelay();
			} else {
				transition = Transition.IGNITE;
			}
		}

		this.previousProgress = this.progress;

		if (this.active && this.igniteWait > 0) {
			this.igniteWait--;

			if (this.igniteWait > 0) {
				return transition;
			}

			transition = Transition.IGNITE;
		}

		if (this.active) {
			this.progress = Math.min(1.0F, this.progress + 1.0F / animation.igniteTicks());
		} else {
			this.progress = Math.max(0.0F, this.progress - 1.0F / animation.retractTicks());
		}

		return transition;
	}

	/** Starts fully lit without animation, for sabers that were already lit before we saw them. */
	void snapLit() {
		this.active = true;
		this.igniteWait = 0;
		this.progress = 1.0F;
		this.previousProgress = 1.0F;
	}

	private void switchProfile(SaberProfile newProfile) {
		float visible = this.visible(this.progress);
		this.profile = newProfile;
		this.progress = this.curve().inverse(visible);
		this.previousProgress = this.progress;
	}

	private Easing curve() {
		return this.active ? this.profile.animation().igniteCurve() : this.profile.animation().retractCurve();
	}

	private float visible(float linear) {
		return Mth.clamp(this.curve().apply(Mth.clamp(linear, 0.0F, 1.0F)), 0.0F, 1.0F);
	}

	/** How far the blade is out, 0 = only the hilt, 1 = full length. */
	public float extension(float partialTick) {
		return this.visible(Mth.lerp(partialTick, this.previousProgress, this.progress));
	}

	/**
	 * Extra brightness during the ignition, fading out as the blade reaches full length, and for a
	 * moment after the blade struck another one.
	 */
	public float flare(float partialTick) {
		if (!this.active || this.igniteWait > 0) {
			return 0.0F;
		}

		float linear = Mth.lerp(partialTick, this.previousProgress, this.progress);
		float clash = this.clash > 0 ? CLASH_FLARE * Math.max(0.0F, this.clash - partialTick) / CLASH_TICKS : 0.0F;
		return this.profile.animation().igniteFlare() * (1.0F - linear) * (1.0F - linear) + clash;
	}

	/** The blade struck another blade or blocked a hit: it flashes up for a moment (Phase 10). */
	public void clash() {
		if (this.active) {
			this.clash = CLASH_TICKS;
		}
	}

	/** True while the blade still flashes from a clash. */
	public boolean isClashing() {
		return this.clash > 0;
	}

	/** True while any part of the blade is visible or the blade is on its way out. */
	public boolean isLit() {
		return this.active || this.progress > 0.0F || this.previousProgress > 0.0F;
	}

	public boolean isActive() {
		return this.active;
	}

	/** True when the blade is fully back in the hilt. */
	public boolean isRetracted() {
		return !this.active && this.progress <= 0.0F;
	}

	public InteractionHand hand() {
		return this.hand;
	}

	public SaberProfile profile() {
		return this.profile;
	}

	/** Smoothed movement speed of the saber, 0 = still, 1 = very fast. */
	public float motion() {
		return this.motion;
	}

	void setMotion(float motion) {
		this.motion = motion;
	}

	@Nullable SaberHumSoundInstance hum() {
		return this.hum;
	}

	void setHum(@Nullable SaberHumSoundInstance hum) {
		this.hum = hum;
	}

	int humRetryCooldown() {
		return this.humRetryCooldown;
	}

	void setHumRetryCooldown(int ticks) {
		this.humRetryCooldown = ticks;
	}

	/** Stops the hum of this hand, if any. */
	void releaseHum() {
		if (this.hum != null) {
			this.hum.release();
			this.hum = null;
		}
	}
}
