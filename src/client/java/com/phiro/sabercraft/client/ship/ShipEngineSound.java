package com.phiro.sabercraft.client.ship;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.network.ShipEnergyPayload;
import com.phiro.sabercraft.ship.ShipType;

/**
 * The looping engine of one starship (Phase 11, reworked in Phase 13). It follows the ship and
 * changes with what the ship does, so it never sits at one loud, unchanging level:
 *
 * <ul>
 * <li><b>Parked</b> on the ground the engine only idles very quietly; <b>hovering</b> it hums low.</li>
 * <li><b>In flight</b> volume and pitch rise with the speed, and <b>speeding up</b> adds a short surge.</li>
 * <li><b>Cruising</b> at an even speed for a few seconds lets it settle a little quieter, as the ear
 * gets used to it; any change of speed brings it back.</li>
 * <li><b>No energy</b> (Phase 13): the engine sputters, quiet and uneven.</li>
 * <li>The pitch drifts slowly by a few percent, so the loop does not sound like a loop.</li>
 * <li>The own ship is a little quieter than other ships, as the camera sits right behind it.</li>
 * </ul>
 *
 * It fades in when it starts and stops itself when the ship is gone. The ship slider of the sound
 * menu applies on top (see {@code SoundVolumes}).
 */
final class ShipEngineSound extends AbstractTickableSoundInstance {
	private static final int FADE_IN_TICKS = 15;
	/** Ticks at an even speed before the engine settles quieter. */
	private static final int SETTLE_TICKS = 60;
	/** The loudest the engine ever gets. */
	static final float MAX_VOLUME = 0.6F;

	private final Entity ship;
	private final ShipType type;
	private final RandomSource drift;
	private int age;
	private boolean finished;
	private float level = 0.1F;
	private float tone = 0.8F;
	private float surge;
	private int steady;
	private float wobble;
	private float wobbleTarget;

	ShipEngineSound(Entity ship, ShipType type) {
		super(event("ship." + type.id() + ".engine"), SoundSource.NEUTRAL, RandomSource.create(ship.getId()));
		this.ship = ship;
		this.type = type;
		this.drift = RandomSource.create(ship.getId() * 7L + 1L);
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.volume = 0.0F;
		this.follow();
	}

	static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(SaberCraft.id(name));
	}

	@Override
	public void tick() {
		if (this.finished || this.ship.isRemoved()) {
			this.stop();
			return;
		}

		this.age++;
		this.follow();
		ShipClient.Visual visual = ShipClient.visual(this.ship);
		float throttle = (float) Math.min(1.0, visual.speed() / this.type.maxSpeed());
		ShipEnergyPayload energy = ShipClient.energy(this.ship);
		boolean empty = energy != null && energy.state() == ShipEnergyPayload.EMPTY;
		boolean parked = this.ship.onGround() && throttle < 0.02F;
		float target = target(parked, throttle, empty);

		// A surge while speeding up, fading quickly.
		this.surge = Math.max(this.surge * 0.9F, Mth.clamp(visual.accel() * 6.0F, 0.0F, 0.15F));

		// Settles a little quieter while nothing changes.
		this.steady = Math.abs(visual.accel()) < 0.004F ? this.steady + 1 : 0;
		float settle = parked ? 1.0F : 1.0F - 0.2F * Mth.clamp((this.steady - SETTLE_TICKS) / 40.0F, 0.0F, 1.0F);
		float own = this.isOwn() ? 0.75F : 1.0F;

		if (empty) {
			// Sputtering: the volume jumps around irregularly.
			target *= 0.5F + 0.5F * this.drift.nextFloat();
		}

		this.level += ((target + this.surge) * settle * own - this.level) * 0.08F;

		// A slow random drift of the pitch, a few percent.
		if (this.age % 40 == 0) {
			this.wobbleTarget = (this.drift.nextFloat() - 0.5F) * 0.05F;
		}

		this.wobble += (this.wobbleTarget - this.wobble) * 0.03F;
		float pitchTarget = (this.type.capital() ? 0.8F : 0.75F) + 0.45F * throttle + this.surge * 0.4F;
		this.tone += (pitchTarget - this.tone) * 0.1F;
		this.volume = Math.min(MAX_VOLUME, this.level) * Math.min(1.0F, this.age / (float) FADE_IN_TICKS);
		this.pitch = Mth.clamp(this.tone + this.wobble, 0.5F, 2.0F);
	}

	/** How loud the engine wants to be, before the surge, settling and fade-in. */
	static float target(boolean parked, float throttle, boolean empty) {
		if (empty) {
			return 0.08F;
		}

		if (parked) {
			return 0.07F;
		}

		return 0.2F + 0.32F * throttle;
	}

	private boolean isOwn() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.player.getVehicle() == this.ship;
	}

	void finish() {
		this.finished = true;
	}

	private void follow() {
		this.x = this.ship.getX();
		this.y = this.ship.getY() + Math.min(2.0, this.ship.getBbHeight() * 0.5);
		this.z = this.ship.getZ();
	}

	/** Only for tests: the volume now. */
	float currentVolume() {
		return this.volume;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
