package com.phiro.sabercraft.client.combat;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The fighting animation of one entity, kept on the client only next to its blade state.
 *
 * <p>Attacks come from the swings vanilla already sends to every client, so everybody sees the
 * same moves without extra packets. Which move a swing starts only depends on things all clients
 * know: the style stored on the saber, the time since the last attack, sprinting and falling.
 *
 * <p>Time is measured in client ticks with the partial tick added, see
 * {@code SaberTracker.time(float)}.
 */
public final class CombatState {
	/** Change of the resting-pose weight per tick, so drawing or sprinting blends in and out. */
	private static final float IDLE_SPEED = 0.2F;
	/** Ticks the defensive stance takes to come up or go down. */
	private static final float GUARD_BLEND = 3.0F;
	/** Change of the running and jumping pose weights per tick. */
	private static final float LAYER_SPEED = 0.25F;
	/** Ticks a move that was cut short takes to settle into the resting pose. */
	private static final float RELEASE_BLEND = 3.0F;

	private @Nullable CombatStyle style;
	private @Nullable CombatMove move;
	private float moveStart;
	private boolean struck;
	private int comboIndex = -1;
	private float lastAttack = -1000.0F;
	private final FirstPersonPose firstPersonFrom = new FirstPersonPose();
	private final ThirdPersonPose thirdPersonFrom = ThirdPersonPose.rest();
	private float idleWeight;
	private float previousIdleWeight;
	private boolean guarding;
	private float guardChanged = -1000.0F;
	private float runWeight;
	private float previousRunWeight;
	private float airWeight;
	private float previousAirWeight;
	/** When a move was cut short, so the resting pose blends in from where the saber was. */
	private float released = -1000.0F;

	/**
	 * Runs once per tick. {@code idleTarget} is 1 when the resting pose should show,
	 * {@code runTarget} and {@code airTarget} are 1 while sprinting or in the air.
	 */
	public void tick(@Nullable CombatStyle newStyle, float now, float idleTarget, float runTarget, float airTarget) {
		if (newStyle != this.style) {
			this.style = newStyle;
			this.comboIndex = -1;
		}

		if (this.move != null && now - this.moveStart >= this.move.duration()) {
			this.move = null;
		}

		this.previousIdleWeight = this.idleWeight;
		this.idleWeight += Mth.clamp(idleTarget - this.idleWeight, -IDLE_SPEED, IDLE_SPEED);
		this.previousRunWeight = this.runWeight;
		this.runWeight += Mth.clamp(runTarget - this.runWeight, -LAYER_SPEED, LAYER_SPEED);
		this.previousAirWeight = this.airWeight;
		this.airWeight += Mth.clamp(airTarget - this.airWeight, -LAYER_SPEED, LAYER_SPEED);
	}

	/**
	 * Ends the current move early and lets the saber settle into the resting pose, for example
	 * when a roll hits a wall or the holder switches items.
	 */
	public void stop(float now) {
		if (this.move != null) {
			this.firstPerson(now, this.firstPersonFrom);
			this.thirdPerson(now, this.thirdPersonFrom);
			this.move = null;
			this.released = now;
		}
	}

	/**
	 * Called when the holder lands after falling {@code height} blocks. Plays the landing move of
	 * the style unless an attack or another move is running.
	 */
	public void onLand(float height, float now) {
		CombatStyle current = this.style;

		if (current == null || this.guarding || this.move != null && now - this.moveStart < this.move.interrupt()) {
			return;
		}

		CombatMove landing = height >= 5.0F && current.landHeavy() != null ? current.landHeavy() : current.land();

		if (landing != null && height >= 1.6F) {
			this.play(landing, now);
		}
	}

	/**
	 * Picks and starts the move for a new swing. Returns {@code null} if the swing is ignored,
	 * because the current move is still in its strike, or if there is no style.
	 */
	public @Nullable CombatMove onSwing(LivingEntity entity, float now) {
		CombatStyle current = this.style;

		if (current == null || this.guarding) {
			return null;
		}

		if (this.move != null && now - this.moveStart < this.move.interrupt()) {
			return null;
		}

		CombatMove next;
		boolean falling = !entity.onGround() && entity.getY() - entity.yo < -0.05 && !entity.isInWater();

		if (falling && current.jump() != null) {
			next = current.jump();
		} else if (entity.isSprinting() && current.sprint() != null) {
			next = current.sprint();
		} else {
			this.comboIndex = now - this.lastAttack > current.comboWindow() ? 0 : (this.comboIndex + 1) % current.combo().size();
			next = current.combo().get(this.comboIndex);
		}

		this.lastAttack = now;
		this.play(next, now);
		return next;
	}

	/** Starts a move right away, for example the move of an ability. */
	public void play(CombatMove next, float now) {
		// Continue from exactly where the saber is now, also in the middle of another move.
		this.firstPerson(now, this.firstPersonFrom);
		this.thirdPerson(now, this.thirdPersonFrom);
		this.move = next;
		this.moveStart = now;
		this.struck = false;
	}

	/** Returns the current move once, when its strike moment is reached. */
	public @Nullable CombatMove pollStrike(float now) {
		if (this.move != null && !this.struck && now - this.moveStart >= this.move.strike()) {
			this.struck = true;
			return this.move;
		}

		return null;
	}

	/** Raises or lowers the defensive stance. A running attack is stopped. */
	public void setGuarding(boolean guarding, float now) {
		if (guarding != this.guarding) {
			this.guarding = guarding;
			this.guardChanged = now;

			if (guarding && this.move != null) {
				this.firstPerson(now, this.firstPersonFrom);
				this.thirdPerson(now, this.thirdPersonFrom);
				this.move = null;
				this.released = now;
			}
		}
	}

	public boolean isGuarding() {
		return this.guarding;
	}

	public @Nullable CombatMove move() {
		return this.move;
	}

	public @Nullable CombatStyle style() {
		return this.style;
	}

	/** Ticks since the current move started, or -1. */
	public float moveAge(float now) {
		return this.move != null ? now - this.moveStart : -1.0F;
	}

	/**
	 * How strongly the blade is swinging right now, 0..1: rises during the wind-up, is full around
	 * the strike and fades out in the follow-through. Drives the light trail and the glow.
	 */
	public float swing(float now) {
		if (this.move == null || !this.move.trail()) {
			return 0.0F;
		}

		float age = now - this.moveStart;
		float strike = this.move.strike();
		float rise = Mth.clamp((age - strike * 0.35F) / Math.max(1.0F, strike * 0.5F), 0.0F, 1.0F);
		float end = Math.max(strike + 3.0F, this.move.duration() - this.move.blendOut() - 1.0F);
		float fall = Mth.clamp(1.0F - (age - end) / 2.5F, 0.0F, 1.0F);
		return rise * fall;
	}

	/** Weight of the combat poses against vanilla's own animation, 0..1. */
	public float weight(float now, float partialTick) {
		if (this.move != null) {
			return 1.0F;
		}

		return smooth(Mth.lerp(partialTick, this.previousIdleWeight, this.idleWeight));
	}

	/** First-person pose at {@code now}, written into {@code out}. */
	public FirstPersonPose firstPerson(float now, FirstPersonPose out) {
		CombatStyle current = this.style;

		if (current == null) {
			return out.set(FirstPersonPose.REST);
		}

		FirstPersonPose rest = current.idleFirstPerson().copy().lerpTo(current.guardFirstPerson(), this.guardAmount(now));

		if (this.move == null) {
			return out.set(this.firstPersonFrom).lerpTo(rest, this.releaseAmount(now));
		}

		float age = now - this.moveStart;
		FirstPersonPose raw = rest.copy();
		this.move.firstPerson(age, raw);
		out.set(this.firstPersonFrom).lerpTo(raw, smooth(age / this.move.blendIn()));
		return out.lerpTo(rest, this.blendOut(age));
	}

	/** Third-person pose at {@code now}, written into {@code out}. */
	public ThirdPersonPose thirdPerson(float now, ThirdPersonPose out) {
		CombatStyle current = this.style;

		if (current == null) {
			return out.set(ThirdPersonPose.rest());
		}

		ThirdPersonPose rest = this.restThirdPerson(current, now);

		if (this.move == null) {
			return out.set(this.thirdPersonFrom).lerpTo(rest, this.releaseAmount(now));
		}

		float age = now - this.moveStart;
		ThirdPersonPose raw = rest.copy();
		this.move.thirdPerson(age, raw);
		out.set(this.thirdPersonFrom).lerpTo(raw, smooth(age / this.move.blendIn()));
		return out.lerpTo(rest, this.blendOut(age));
	}

	private float blendOut(float age) {
		CombatMove current = this.move;

		if (current == null || current.blendOut() <= 0.0F) {
			return 0.0F;
		}

		return smooth((age - (current.duration() - current.blendOut())) / current.blendOut());
	}

	/** The resting pose with running, jumping and the stance layered on top. */
	private ThirdPersonPose restThirdPerson(CombatStyle current, float now) {
		ThirdPersonPose rest = current.idleThirdPerson().copy();
		float partial = Mth.frac(now);

		if (current.runThirdPerson() != null) {
			rest.lerpTo(current.runThirdPerson(), smooth(Mth.lerp(partial, this.previousRunWeight, this.runWeight)));
		}

		if (current.airThirdPerson() != null) {
			rest.lerpTo(current.airThirdPerson(), smooth(Mth.lerp(partial, this.previousAirWeight, this.airWeight)));
		}

		return rest.lerpTo(current.guardThirdPerson(), this.guardAmount(now));
	}

	private float releaseAmount(float now) {
		return smooth((now - this.released) / RELEASE_BLEND);
	}

	private float guardAmount(float now) {
		float amount = smooth((now - this.guardChanged) / GUARD_BLEND);
		return this.guarding ? amount : 1.0F - amount;
	}

	/** Smoothstep, clamped to 0..1. */
	static float smooth(float t) {
		float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
