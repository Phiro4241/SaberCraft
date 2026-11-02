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
 * Since Phase 13 critical and running attacks choose among several moves of the style, and the
 * normal chain sometimes skips a step; the choice comes from the entity, the shared game time and
 * the attacks seen, so clients that saw the same fight pick the same moves.
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
	/** How many of the further strikes of the move have been played (Phase 9). */
	private int strikes;
	private int comboIndex = -1;
	private float lastAttack = -1000.0F;
	/** Phase 13: chance that the normal chain skips a step. */
	static final float SKIP_CHANCE = 0.2F;
	/** Phase 13: the move of the last attack, attacks since a special move, and all attacks. */
	private @Nullable CombatMove lastMove;
	private int sinceSignature = 100;
	private int attacks;
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
		// The same roll on every client: who swings and when (the game time is shared).
		float roll = roll(entity.getId(), entity.level().getGameTime(), this.attacks);
		CombatConfig config = CombatConfig.get();

		if (falling && current.jump() != null) {
			next = config.moveVariety ? this.pick(current, current.jump(), current.critMoves(), roll, config.signatureMoves) : current.jump();
		} else if (entity.isSprinting() && current.sprint() != null) {
			next = config.moveVariety ? this.pick(current, current.sprint(), current.sprintMoves(), roll, config.signatureMoves) : current.sprint();
		} else {
			boolean fresh = now - this.lastAttack > current.comboWindow();
			this.comboIndex = fresh ? 0 : (this.comboIndex + 1) % current.combo().size();

			// Phase 13: now and then the chain skips a step, so a long fight does not loop one
			// fixed sequence; never onto the move that just played.
			if (config.moveVariety && !fresh && current.combo().size() > 2 && roll < SKIP_CHANCE) {
				int skipped = (this.comboIndex + 1) % current.combo().size();

				if (current.combo().get(skipped) != this.lastMove) {
					this.comboIndex = skipped;
				}
			}

			next = current.combo().get(this.comboIndex);

			if (next == this.lastMove && current.combo().size() > 1) {
				this.comboIndex = (this.comboIndex + 1) % current.combo().size();
				next = current.combo().get(this.comboIndex);
			}
		}

		if (next == current.jump() || next == current.sprint()) {
			this.sinceSignature = 0;
		} else {
			this.sinceSignature++;
		}

		this.attacks++;
		this.lastMove = next;
		this.lastAttack = now;
		this.play(next, now);
		return next;
	}

	/**
	 * Phase 13: the move for a critical or running attack. The style's special move (Ezra's turn,
	 * a flip, a leap) comes now and then, at the style's chance and never right after itself or
	 * another special move; otherwise one of the style's other moves for that situation, never the
	 * one that just played. Without other moves the special move always plays, as before.
	 */
	private CombatMove pick(CombatStyle style, CombatMove signature, java.util.List<CombatMove> others, float roll, float signatureMoves) {
		java.util.List<CombatMove> pool = new java.util.ArrayList<>(others.isEmpty() ? style.combo() : others);
		pool.removeIf(move -> move == signature || move == this.lastMove);

		if (pool.isEmpty()) {
			return signature;
		}

		float chance = Math.min(1.0F, style.signatureChance() * signatureMoves);
		boolean rested = this.sinceSignature >= style.signatureRest() && this.lastMove != signature;

		if (rested && roll < chance) {
			return signature;
		}

		// The rest of the roll picks among the others, so they are evenly spread.
		float rest = chance >= 1.0F ? roll : Mth.clamp((roll - (rested ? chance : 0.0F)) / (1.0F - (rested ? chance : 0.0F)), 0.0F, 0.9999F);
		return pool.get(Math.min(pool.size() - 1, (int) (rest * pool.size())));
	}

	/** A number in 0..1 from the entity, the game time and the count of its attacks. */
	static float roll(int entity, long gameTime, int attacks) {
		long h = entity * 0x9E3779B97F4A7C15L + gameTime * 0xC2B2AE3D27D4EB4FL + attacks * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 40) / (float) (1L << 24);
	}

	/** Starts a move right away, for example the move of an ability. */
	public void play(CombatMove next, float now) {
		// Continue from exactly where the saber is now, also in the middle of another move.
		this.firstPerson(now, this.firstPersonFrom);
		this.thirdPerson(now, this.thirdPersonFrom);
		this.move = next;
		this.moveStart = now;
		this.struck = false;
		this.strikes = 0;
	}

	/** Returns the current move once, when its strike moment is reached. */
	public @Nullable CombatMove pollStrike(float now) {
		if (this.move != null && !this.struck && now - this.moveStart >= this.move.strike()) {
			this.struck = true;
			return this.move;
		}

		// Further blows of the same move.
		if (this.move != null && this.struck && this.strikes < this.move.moreStrikes().length
				&& now - this.moveStart >= this.move.moreStrikes()[this.strikes]) {
			this.strikes++;
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
