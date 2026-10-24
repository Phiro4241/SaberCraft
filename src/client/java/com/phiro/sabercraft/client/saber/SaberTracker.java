package com.phiro.sabercraft.client.saber;

import java.util.function.BiPredicate;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.phiro.sabercraft.client.render.BladeRenderState;
import com.phiro.sabercraft.client.sound.SaberHumSoundInstance;
import com.phiro.sabercraft.client.sound.SaberSoundSet;

/**
 * Keeps the blade state of every entity that holds a saber, on the client only.
 *
 * <p>No packets are needed for multiplayer: the server already tells every client which item each
 * player and mob holds and when they swing. Each client derives the blade animation and the sounds
 * from that. Changing the held item ignites or retracts the blade, a swing plays a swing sound,
 * and everybody nearby sees and hears the same thing.
 *
 * <p>Runs once per client tick after the entities were ticked. Entities that are removed, die,
 * respawn or change dimension lose their state, and their hum is stopped right away.
 */
public final class SaberTracker {
	public static final SaberTracker INSTANCE = new SaberTracker();

	/** Sabers further away than this from the camera make no sound. */
	private static final double SOUND_RANGE = 32.0;
	/** At most this many hum loops play at the same time, so big fights cannot use up all channels. */
	private static final int MAX_HUMS = 8;
	/** Head turn in degrees per tick that counts as a swing. */
	private static final float SWING_TURN_SPEED = 28.0F;
	/** Ticks between two swing sounds of the same entity, so fast swinging does not stutter. */
	private static final int SWING_COOLDOWN = 5;
	/** Ticks before a hum that failed to start is tried again. */
	private static final int HUM_RETRY_TICKS = 20;

	private final Int2ObjectMap<TrackedEntity> tracked = new Int2ObjectOpenHashMap<>();
	private @Nullable ClientLevel level;
	private BiPredicate<LivingEntity, InteractionHand> holstering = (entity, hand) -> false;
	private int humCount;

	private SaberTracker() {
	}

	/** Lets the holster logic force a blade to retract while the saber is still in the hand. */
	public void setHolsterCheck(BiPredicate<LivingEntity, InteractionHand> holstering) {
		this.holstering = holstering;
	}

	public void tick(Minecraft client) {
		ClientLevel currentLevel = client.level;

		if (currentLevel != this.level) {
			// New world, other dimension or disconnect: all entities are new.
			this.clear();
			this.level = currentLevel;
		}

		if (currentLevel == null) {
			return;
		}

		Entity listener = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
		this.humCount = 0;

		for (TrackedEntity entry : this.tracked.values()) {
			this.humCount += entry.humCount();
		}

		for (Entity entity : currentLevel.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}

			TrackedEntity entry = this.tracked.get(living.getId());

			if (entry != null && entry.entity != living) {
				entry.release();
				entry = null;
			}

			if (entry == null) {
				if (!holdsSaber(living)) {
					continue;
				}

				// An entity that has just appeared (came into view, joined) already had its blade
				// out, so it starts lit. The own player is an exception: joining a world or
				// respawning with a saber in the hand plays the ignition.
				boolean alreadyLit = living != client.player && living.tickCount <= 2;
				entry = new TrackedEntity(living, alreadyLit);
				this.tracked.put(living.getId(), entry);
			}

			boolean audible = listener != null && listener.distanceToSqr(living) < SOUND_RANGE * SOUND_RANGE;
			entry.tick(client.getSoundManager(), audible);
		}

		ObjectIterator<TrackedEntity> iterator = this.tracked.values().iterator();

		while (iterator.hasNext()) {
			TrackedEntity entry = iterator.next();

			if (entry.entity.isRemoved() || entry.entity.level() != currentLevel || entry.isIdle()) {
				entry.release();
				iterator.remove();
			}
		}
	}

	/** Stops all hums and forgets all state. */
	public void clear() {
		for (TrackedEntity entry : this.tracked.values()) {
			entry.release();
		}

		this.tracked.clear();
		this.humCount = 0;
	}

	public @Nullable SaberHandState state(LivingEntity entity, InteractionHand hand) {
		TrackedEntity entry = this.tracked.get(entity.getId());
		return entry != null && entry.entity == entity ? entry.hands[hand.ordinal()] : null;
	}

	/** Blade length and flare for rendering the saber in the given hand. */
	public BladeRenderState renderState(LivingEntity entity, InteractionHand hand, float partialTick) {
		SaberHandState state = this.state(entity, hand);

		if (state == null) {
			return BladeRenderState.RETRACTED;
		}

		return BladeRenderState.of(state.extension(partialTick), state.flare(partialTick));
	}

	/** Number of entities with saber state, for tests and debugging. */
	public int trackedCount() {
		return this.tracked.size();
	}

	/** Number of hum loops currently playing, for tests and debugging. */
	public int activeHums() {
		int count = 0;

		for (TrackedEntity entry : this.tracked.values()) {
			count += entry.humCount();
		}

		return count;
	}

	private static boolean holdsSaber(LivingEntity entity) {
		return SaberProfiles.isSaber(entity.getMainHandItem()) || SaberProfiles.isSaber(entity.getOffhandItem());
	}

	private final class TrackedEntity {
		private final LivingEntity entity;
		private final SaberHandState[] hands = new SaberHandState[InteractionHand.values().length];
		private boolean startLit;
		private boolean wasSwinging;
		private int lastSwingTime;
		private float lastYaw;
		private float lastPitch;
		private int swingCooldown;
		private float motion;

		TrackedEntity(LivingEntity entity, boolean startLit) {
			this.entity = entity;
			this.startLit = startLit;
			this.lastYaw = entity.getYHeadRot();
			this.lastPitch = entity.getXRot();
			this.wasSwinging = entity.swinging;
			this.lastSwingTime = entity.swingTime;
		}

		void tick(SoundManager soundManager, boolean audible) {
			for (InteractionHand hand : InteractionHand.values()) {
				this.tickHand(soundManager, hand, audible);
			}

			this.startLit = false;
			this.tickMotion(soundManager, audible);
		}

		private void tickHand(SoundManager soundManager, InteractionHand hand, boolean audible) {
			SaberProfile profile = SaberProfiles.of(this.entity.getItemInHand(hand));
			SaberHandState state = this.hands[hand.ordinal()];

			if (state == null) {
				if (profile == null) {
					return;
				}

				state = new SaberHandState(hand, profile);
				this.hands[hand.ordinal()] = state;
			}

			boolean shouldBeLit = profile != null
					&& this.entity.isAlive()
					&& !SaberTracker.this.holstering.test(this.entity, hand);

			if (this.startLit && shouldBeLit) {
				state.snapLit();
			}

			float before = state.extension(1.0F);
			SaberHandState.Transition transition = state.tick(shouldBeLit, profile);
			SaberSoundSet sounds = state.profile().sounds();

			if (audible) {
				if (transition == SaberHandState.Transition.IGNITE) {
					this.play(soundManager, sounds.ignite(), 1.0F, 1.0F);
				} else if (transition == SaberHandState.Transition.RETRACT && before > 0.05F) {
					this.play(soundManager, sounds.retract(), 1.0F, 1.0F);
				}
			}

			state.setMotion(this.motion);
			this.tickHum(soundManager, state, audible);

			if (profile == null && state.isRetracted()) {
				state.releaseHum();
				this.hands[hand.ordinal()] = null;
			}
		}

		private void tickHum(SoundManager soundManager, SaberHandState state, boolean audible) {
			if (!audible || !state.isLit()) {
				if (state.hum() != null) {
					state.releaseHum();
					SaberTracker.this.humCount--;
				}

				return;
			}

			SaberHumSoundInstance hum = state.hum();

			if (hum != null && !hum.isStopped()) {
				return;
			}

			if (hum != null) {
				state.setHum(null);
				SaberTracker.this.humCount--;
			}

			if (state.humRetryCooldown() > 0) {
				state.setHumRetryCooldown(state.humRetryCooldown() - 1);
				return;
			}

			if (SaberTracker.this.humCount >= MAX_HUMS) {
				return;
			}

			// The hum is started once and then runs until the blade is gone. It is never restarted
			// per frame or per tick.
			SaberHumSoundInstance newHum = new SaberHumSoundInstance(this.entity, state, state.profile().sounds());

			if (soundManager.play(newHum) == SoundEngine.PlayResult.NOT_STARTED) {
				state.setHumRetryCooldown(HUM_RETRY_TICKS);
				return;
			}

			state.setHum(newHum);
			SaberTracker.this.humCount++;
		}

		private void tickMotion(SoundManager soundManager, boolean audible) {
			float yaw = this.entity.getYHeadRot();
			float pitch = this.entity.getXRot();
			float turnSpeed = Math.abs(Mth.wrapDegrees(yaw - this.lastYaw)) + Math.abs(pitch - this.lastPitch);
			this.lastYaw = yaw;
			this.lastPitch = pitch;

			boolean swinging = this.entity.swinging;
			boolean newSwing = swinging && (!this.wasSwinging || this.entity.swingTime < this.lastSwingTime);
			this.wasSwinging = swinging;
			this.lastSwingTime = this.entity.swingTime;

			// The hum reacts to movement: quick turns and swings make it swell for a moment.
			float target = Mth.clamp((turnSpeed - 4.0F) / 40.0F, 0.0F, 1.0F);
			if (newSwing) {
				target = 1.0F;
			}

			this.motion = Math.max(target, this.motion * 0.75F);

			if (this.swingCooldown > 0) {
				this.swingCooldown--;
			}

			if (!audible || this.swingCooldown > 0) {
				return;
			}

			SaberHandState swingHand = null;

			if (newSwing) {
				InteractionHand hand = this.entity.swingingArm != null ? this.entity.swingingArm : InteractionHand.MAIN_HAND;
				swingHand = litHand(this.hands[hand.ordinal()]);
			}

			float strength = 1.0F;

			if (swingHand == null && turnSpeed > SWING_TURN_SPEED) {
				// A fast turn with a lit blade sounds like a swing, softer for slower turns.
				swingHand = litHand(this.hands[InteractionHand.MAIN_HAND.ordinal()]);

				if (swingHand == null) {
					swingHand = litHand(this.hands[InteractionHand.OFF_HAND.ordinal()]);
				}

				strength = Mth.clamp(0.45F + (turnSpeed - SWING_TURN_SPEED) / 60.0F, 0.45F, 1.0F);
			}

			if (swingHand != null) {
				SaberSoundSet sounds = swingHand.profile().sounds();
				float pitchVariation = 0.92F + this.entity.getRandom().nextFloat() * 0.18F;
				this.play(soundManager, sounds.swing(), sounds.swingVolume() * strength, pitchVariation);
				this.swingCooldown = SWING_COOLDOWN;
			}
		}

		private static @Nullable SaberHandState litHand(@Nullable SaberHandState state) {
			return state != null && state.isActive() && state.extension(1.0F) > 0.8F ? state : null;
		}

		private void play(SoundManager soundManager, SoundEvent event, float volume, float pitch) {
			soundManager.play(new EntityBoundSoundInstance(
					event, this.entity.getSoundSource(), volume, pitch, this.entity, this.entity.getRandom().nextLong()
			));
		}

		int humCount() {
			int count = 0;

			for (SaberHandState state : this.hands) {
				if (state != null && state.hum() != null) {
					count++;
				}
			}

			return count;
		}

		/** No saber in either hand and nothing left to animate. */
		boolean isIdle() {
			for (SaberHandState state : this.hands) {
				if (state != null) {
					return false;
				}
			}

			return true;
		}

		void release() {
			for (SaberHandState state : this.hands) {
				if (state != null) {
					state.releaseHum();
				}
			}
		}
	}
}
