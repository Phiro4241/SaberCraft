package com.phiro.sabercraft.client.ship;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ship.ShipType;

/**
 * The looping engine of one starship (Phase 11). It follows the ship and rises in volume and pitch
 * with its speed: a quiet idle on the ground, a roar at top speed. It fades in when it starts and
 * stops itself when the ship is gone.
 */
final class ShipEngineSound extends AbstractTickableSoundInstance {
	private static final int FADE_IN_TICKS = 10;

	private final Entity ship;
	private final ShipType type;
	private int age;
	private boolean finished;
	private float level = 0.25F;

	ShipEngineSound(Entity ship, ShipType type) {
		super(event("ship." + type.id() + ".engine"), SoundSource.NEUTRAL, RandomSource.create(ship.getId()));
		this.ship = ship;
		this.type = type;
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
		float target = this.ship.onGround() && throttle < 0.02F ? 0.18F : 0.35F + 0.65F * throttle;
		this.level += (target - this.level) * 0.1F;
		this.volume = this.level * Math.min(1.0F, this.age / (float) FADE_IN_TICKS);
		this.pitch = 0.7F + 0.6F * throttle;
	}

	void finish() {
		this.finished = true;
	}

	private void follow() {
		this.x = this.ship.getX();
		this.y = this.ship.getY() + 1.0;
		this.z = this.ship.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
