package com.phiro.sabercraft.ship;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.network.ShipEnergyPayload;

/**
 * A starship on the server (Phase 11): the X-Wing, since Phase 12 also the TIE Fighter and the TIE
 * Interceptor, since Phase 13 the Millennium Falcon and the Star Destroyer, each with its own speed,
 * agility, cannons and hull (see {@link ShipType}). Its owner climbs in with a right-click and
 * flies it with the movement keys: forward opens the throttle, back brakes, left and right turn,
 * jump pulls the nose up and sprint pushes it down (when hovering slowly they lift and lower the
 * ship straight up and down instead). Sneaking climbs out as from any vehicle. The server alone
 * moves the ship, so every player sees the same flight; its position goes out every tick.
 *
 * <p>Since Phase 12 ships can be hit (lasers, blasters, sabers, explosions) unless the server turns
 * that off; at 0 hull the ship explodes without breaking blocks, the pilot glides down, and the
 * owner can call a new one after {@code ship_respawn_seconds}. A hull that is not hit for ten
 * seconds mends itself.
 *
 * <p>It collides with blocks like any entity and never breaks them. Phase 13: neither the ship nor
 * its pilot ever takes fall damage. A gentle landing is free; a hard landing or a crash into a wall
 * costs hull, more the faster it was, and a very hard one wrecks the ship (see {@link #impact}).
 * Ships have flight energy that runs down in flight and charges on the ground; when empty, the
 * engines cut out and a fighter glides down slowly. The Star Destroyer is a capital ship: it never
 * dips its nose, climbs and sinks straight and keeps its height above the ground. Without a pilot
 * a ship settles to the ground. For the network it is a vanilla pig marked as a ship (see
 * {@link ShipMarker}); like the droids it is never saved with the world, where it was parked is
 * saved with its owner (see {@link Starships}).
 */
public class ShipEntity extends Pig {
	/** Below this speed the ship hovers: jump and sprint lift and lower it. */
	static final double HOVER_SPEED = 0.25;
	/** Climb or sink speed while hovering, blocks per tick. */
	static final double LIFT = 0.22;
	/** How fast the ship sinks without a pilot. */
	static final double SETTLE = 0.2;
	/** Steepest climb or dive, degrees. */
	static final float MAX_PITCH = 35.0F;
	/** Phase 12: ticks without a hit before the hull starts to mend (one point a second). */
	static final int REPAIR_DELAY = 200;
	/** Phase 13: share of the top speed left when the energy is empty. */
	static final double NO_POWER_SPEED = 0.3;
	/** Phase 13: how fast a ship without energy sinks, blocks per tick (a safe landing). */
	static final double GLIDE = 0.14;
	/** Phase 13: the nose drops this far while gliding without energy, degrees. */
	static final float GLIDE_PITCH = 6.0F;
	/** Phase 13: how fast a capital ship climbs or sinks, blocks per tick. */
	static final double CAPITAL_LIFT = 0.12;

	private static int crashes;
	private static int explosions;
	private static double lastImpact;

	private final ShipType ship;
	private final UUID owner;
	private double speed;
	private float yawRate;
	private int fireCooldown;
	private int cannon;
	private int crashCooldown;
	/** Phase 13: ticks before the next soft-landing sound. */
	private int landCooldown;
	/** Phase 13: flight energy in ticks of full throttle, -1 until the first tick sets it. */
	private float energy = -1.0F;
	private boolean depleted;
	private float lastUse;
	/** Phase 13: the hull is taking crash damage, which nothing else may do without an attacker. */
	private boolean crashing;
	/** Phase 13: ticks since the last laser shot, to soften the sound of long bursts. */
	private int sinceShot = 100;
	private int burst;
	/** Phase 12: ticks since the hull was last hit; it mends itself after a while. */
	private int sinceHit = Integer.MAX_VALUE / 2;

	public ShipEntity(ServerLevel level, ShipType ship, ServerPlayer owner) {
		super(EntityTypes.PIG, level);
		this.ship = ship;
		this.owner = owner.getUUID();
		this.setItemSlot(EquipmentSlot.HEAD, ShipMarker.item(ship));
		this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		this.setCustomName(Component.literal(ship.displayName()));
		this.setPersistenceRequired();
		this.setNoGravity(true);
		// Phase 12: the hull; every ship has its own.
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ship.hull());
		this.setHealth(ship.hull());
		// The third-person camera stays far enough behind to see the whole ship.
		this.getAttribute(Attributes.CAMERA_DISTANCE).setBaseValue(ship.handling().camera());
		// Keeps the nose where the pilot puts it (a mob's look control would level it every tick).
		this.lookControl = new LookControl(this) {
			@Override
			protected boolean resetXRotOnTick() {
				return false;
			}
		};
		this.refreshDimensions();
	}

	@Override
	protected void registerGoals() {
	}

	public ShipType ship() {
		return this.ship;
	}

	public UUID owner() {
		return this.owner;
	}

	public double speed() {
		return this.speed;
	}

	/** Comes to a standstill (when called somewhere else). */
	void stop() {
		this.speed = 0.0;
		this.yawRate = 0.0F;
		this.setXRot(0.0F);
		this.setDeltaMovement(Vec3.ZERO);
	}

	/** The pilot, if someone sits in the cockpit. */
	public @Nullable ServerPlayer pilot() {
		return this.getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	@Override
	public void tick() {
		super.tick();

		if (!(this.level() instanceof ServerLevel)) {
			return;
		}

		if (!Starships.isCurrent(this)) {
			this.ejectPassengers();
			this.discard();
			return;
		}

		if (this.fireCooldown > 0) {
			this.fireCooldown--;
		}

		if (this.crashCooldown > 0) {
			this.crashCooldown--;
		}

		if (this.landCooldown > 0) {
			this.landCooldown--;
		}

		this.sinceShot++;

		// Phase 13: whoever sits in a ship never builds up a fall (see causeFallDamage).
		for (Entity passenger : this.getPassengers()) {
			passenger.resetFallDistance();
		}

		if (this.isOnFire()) {
			this.clearFire();
		}

		// Phase 12: a damaged hull mends slowly once the ship has not been hit for a while, and
		// smokes while it is badly hurt.
		this.sinceHit++;

		if (this.sinceHit > REPAIR_DELAY && this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
			this.heal(1.0F);
		}

		if (this.getHealth() < this.getMaxHealth() * 0.35F && this.tickCount % 4 == 0) {
			((ServerLevel) this.level()).sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + this.ship.height() * 0.6, this.getZ(),
					1, 0.3, 0.2, 0.3, 0.01);
		}

		// Every tick, not every third as for pigs: the flight stays smooth for everyone.
		this.needsSync = true;
	}

	@Override
	public void travel(Vec3 input) {
		if (this.level() instanceof ServerLevel) {
			this.fly();
		}
	}

	private void fly() {
		ServerPlayer pilot = this.pilot();
		Input keys = pilot != null && pilot.getUUID().equals(this.owner) ? pilot.getLastClientInput() : Input.EMPTY;
		ShipType.Handling handling = this.ship.handling();
		boolean capital = handling.capital();
		boolean powered = !this.depleted;
		double full = this.ship.maxSpeed() * AbilityConfig.get().shipSpeed;
		// Phase 13: without energy the engines only give a little thrust to steer the glide down.
		double max = powered ? full : full * NO_POWER_SPEED;

		// Throttle: forward speeds up, back brakes; let go and the ship keeps its speed.
		if (pilot == null) {
			this.speed = Math.max(0.0, this.speed - 0.05);
		} else if (keys.forward() && powered) {
			this.speed = Math.min(max, this.speed + this.ship.accel());
		} else if (keys.backward()) {
			this.speed = Math.max(0.0, this.speed - this.ship.accel() * 2.0);
		}

		if (this.speed > max) {
			this.speed = Math.max(max, this.speed - Math.max(this.ship.accel(), 0.02));
		}

		// Rudder: turns faster when slow, eases in and out (a capital ship very slowly).
		float rudder = (keys.right() ? 1.0F : 0.0F) - (keys.left() ? 1.0F : 0.0F);
		float turn = this.ship.turn() * (this.speed < HOVER_SPEED ? 1.3F : 1.0F);
		this.yawRate += (rudder * turn - this.yawRate) * (capital ? 0.08F : 0.3F);
		float yaw = Mth.wrapDegrees(this.getYRot() + this.yawRate);

		// Nose up (jump) or down (sprint) in flight; levels out when let go, and when hovering. A
		// capital ship never dips its nose, and without power the nose goes down a little.
		boolean hovering = this.speed < HOVER_SPEED;
		float pitchTarget = capital || hovering ? 0.0F
				: !powered ? GLIDE_PITCH
				: keys.jump() ? -MAX_PITCH : keys.sprint() ? MAX_PITCH : 0.0F;
		float pitch = this.getXRot() + Mth.clamp(pitchTarget - this.getXRot(), -2.5F, 2.5F);
		this.setYRot(yaw);
		this.setXRot(pitch);
		this.setYBodyRot(yaw);
		this.setYHeadRot(yaw);

		Vec3 motion = Vec3.directionFromRotation(pitch, yaw).scale(this.speed);

		if (capital) {
			// Climbs and sinks straight, and always keeps its height above the ground.
			double vertical = pilot != null && powered ? keys.jump() ? CAPITAL_LIFT : keys.sprint() ? -CAPITAL_LIFT : 0.0 : 0.0;
			double clearance = this.clearance(handling.hoverHeight() + 8.0F);

			if (clearance < handling.hoverHeight()) {
				vertical = Math.max(vertical, Math.min(CAPITAL_LIFT * 1.5, (handling.hoverHeight() - clearance) * 0.1));
			} else if (pilot == null && clearance > handling.hoverHeight() + 1.0) {
				vertical = -0.04;
			} else if (vertical < 0.0 && clearance - handling.hoverHeight() < CAPITAL_LIFT) {
				vertical = 0.0;
			}

			motion = new Vec3(motion.x, vertical, motion.z);
		} else if (!powered && !this.onGround()) {
			// Engines out: a slow, steady glide down, never faster than a safe landing.
			motion = new Vec3(motion.x, Math.min(motion.y, 0.0) * 0.3 - GLIDE, motion.z);
		} else if (pilot != null && hovering) {
			motion = motion.add(0.0, keys.jump() && powered ? LIFT : keys.sprint() ? -LIFT : 0.0, 0.0);
		} else if (pilot == null && !this.onGround()) {
			motion = motion.add(0.0, -SETTLE, 0.0);
		}

		// Not above the build limit by much, and not out of the world border.
		if (this.getY() > this.level().getMaxY() + 48 && motion.y > 0.0) {
			motion = new Vec3(motion.x, 0.0, motion.z);
		}

		if (!this.level().getWorldBorder().isWithinBounds(this.getBoundingBox().move(motion))) {
			motion = Vec3.ZERO;
			this.speed = 0.0;
		}

		boolean wasOnGround = this.onGround();
		Vec3 before = this.position();
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
		Vec3 moved = this.position().subtract(before);
		// Never fall damage: neither for the ship nor (through it) for the pilot.
		this.resetFallDistance();

		// Phase 13: how hard it hit something: the speed it lost against a wall, a ceiling or the
		// ground. Gentle touch-downs are free, hard ones cost hull.
		double impact = 0.0;
		boolean landing = false;

		if (this.horizontalCollision) {
			impact = Math.max(impact, motion.horizontal().subtract(moved.horizontal()).length());
		}

		if (this.verticalCollision) {
			impact = Math.max(impact, Math.abs(motion.y - moved.y));
			landing = motion.y < 0.0;
		}

		if (impact > 0.0) {
			this.impact(impact, landing, wasOnGround, pilot);
		}

		if (this.horizontalCollision || this.verticalCollision && motion.y > 0.0) {
			this.speed *= 0.4;
		}

		if (this.onGround() && motion.y <= 0.0) {
			this.setXRot(this.getXRot() * 0.5F);
		}

		this.tickEnergy(pilot, keys, full);

		if (pilot != null && this.speed >= full * 0.95) {
			SaberAdvancements.award(pilot, SaberAdvancements.FIRST_FLIGHT);
		}
	}

	/**
	 * Phase 13: blocks of free air under the ship, measured at its middle and (for a capital ship)
	 * under its nose, its stern and its sides, at most {@code limit}.
	 */
	private double clearance(double limit) {
		double bottom = this.getY();
		double lowest = limit;
		float reach = this.ship.capital() ? this.ship.handling().modelRadius() * 0.6F : 0.0F;
		Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		Vec3[] points = reach > 0.0F
				? new Vec3[] {Vec3.ZERO, forward.scale(reach), forward.scale(-reach * 0.8), side.scale(reach * 0.5), side.scale(-reach * 0.5)}
				: new Vec3[] {Vec3.ZERO};

		for (Vec3 offset : points) {
			int x = Mth.floor(this.getX() + offset.x);
			int z = Mth.floor(this.getZ() + offset.z);

			if (!this.level().isLoaded(BlockPos.containing(x, bottom, z))) {
				continue;
			}

			int top = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			lowest = Math.min(lowest, bottom - top);
		}

		return lowest;
	}

	/**
	 * Phase 13: the ship touched down or hit something at {@code impact} blocks per tick. Up to the
	 * ship's safe speed nothing happens (a soft landing makes a quiet thud); above it the hull takes
	 * damage that grows with the speed, up to a wreck. The pilot never gets hurt by it.
	 */
	private void impact(double impact, boolean landing, boolean wasOnGround, @Nullable ServerPlayer pilot) {
		ShipType.Handling handling = this.ship.handling();
		ServerLevel level = (ServerLevel) this.level();

		if (impact <= handling.safeImpact()) {
			if (landing && !wasOnGround && impact > 0.08 && this.landCooldown == 0) {
				this.landCooldown = 20;
				level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.land"), SoundSource.NEUTRAL,
						(float) Math.min(0.7, 0.25 + impact), 0.9F + this.random.nextFloat() * 0.15F);
				level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 6, this.ship.width() * 0.3, 0.05,
						this.ship.width() * 0.3, 0.02);
			}

			return;
		}

		if (this.crashCooldown > 0) {
			return;
		}

		this.crashCooldown = 20;
		crashes++;
		lastImpact = impact;
		// 0 just above the safe speed, 1 at a wreck.
		float share = (float) Mth.clamp((impact - handling.safeImpact()) / (handling.wreckImpact() - handling.safeImpact()), 0.0, 1.0);
		float multiplier = AbilityConfig.get().shipCrashDamage;
		float damage = multiplier <= 0.0F ? 0.0F
				: share >= 1.0F ? this.getMaxHealth() * 2.0F
				: (float) (this.getMaxHealth() * Math.pow(share, 1.3) * 0.9) * multiplier;
		boolean hard = share < 0.35F;
		double y = this.getY() + this.ship.height() * 0.3;
		double spread = this.ship.width() * 0.35;

		if (hard) {
			// A hard landing: a jolt, dust and a heavy thud.
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 14, spread, 0.1, spread, 0.05);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), y, this.getZ(), 10, spread, 0.3, spread, 0.02);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.impact"), SoundSource.NEUTRAL, 0.9F,
					0.9F + this.random.nextFloat() * 0.1F);
		} else {
			// A crash: smoke, sparks, flames and the bang of tearing metal.
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), y, this.getZ(), 18, spread, 0.4, spread, 0.03);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), y, this.getZ(), 24, spread, 0.4, spread, 0.25);
			level.sendParticles(ParticleTypes.FLAME, this.getX(), y, this.getZ(), 8, spread * 0.6, 0.3, spread * 0.6, 0.04);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.crash"), SoundSource.NEUTRAL, 1.0F,
					0.85F + this.random.nextFloat() * 0.15F);
		}

		if (damage <= 0.0F) {
			return;
		}

		if (pilot != null && damage < this.getHealth()) {
			pilot.sendOverlayMessage(Component.translatableWithFallback(hard ? "sabercraft.ship.hard_landing" : "sabercraft.ship.crashed",
					hard ? "Hard landing! Hull %s %%" : "Crash! Hull %s %%",
					Math.round(Math.max(0.0F, this.getHealth() - damage) / this.getMaxHealth() * 100.0F)));
		}

		this.crashing = true;
		this.invulnerableTime = 0;

		try {
			this.hurtServer(level, level.damageSources().flyIntoWall(), damage);
		} finally {
			this.crashing = false;
		}
	}

	/** Phase 13: never fall damage, for the ship or anyone in it; landings are handled by {@link #impact}. */
	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	/**
	 * Phase 13: flight energy. It drains while the ship flies (hovering at half the rate of full
	 * throttle) and charges again on the ground or parked; a capital ship charges while it holds
	 * still. Empty, the engines cut out until {@code ship_restart_share} is charged again.
	 */
	private void tickEnergy(@Nullable ServerPlayer pilot, Input keys, double full) {
		AbilityConfig config = AbilityConfig.get();
		float max = config.flightTicks(this.ship);

		if (!config.shipEnergy) {
			this.energy = max;
			this.depleted = false;
			this.lastUse = 0.0F;
			this.sendEnergy(pilot, false);
			return;
		}

		if (this.energy < 0.0F) {
			this.energy = max;
		}

		this.energy = Math.min(this.energy, max);
		boolean capital = this.ship.capital();
		boolean flying = pilot != null && (capital ? this.speed > 0.02 || keys.jump() || keys.sprint() : !this.onGround());
		float rate = max / config.rechargeTicks(this.ship);
		boolean wasDepleted = this.depleted;
		float use = 0.0F;

		if (flying && !this.depleted) {
			use = (float) (0.5 + 0.5 * Math.min(1.0, this.speed / Math.max(0.01, full)));
			this.energy = Math.max(0.0F, this.energy - use);
		} else if (!flying && (this.onGround() || pilot == null || capital)) {
			this.energy = Math.min(max, this.energy + rate);
		}

		ServerLevel level = (ServerLevel) this.level();

		if (!this.depleted && this.energy <= 0.0F) {
			this.depleted = true;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.power_down"), SoundSource.NEUTRAL, 0.9F, 1.0F);

			if (pilot != null) {
				pilot.sendOverlayMessage(Component.translatableWithFallback("sabercraft.ship.energy_empty",
						"Energy empty! The engines cut out. Land and let them charge."));
			}
		} else if (this.depleted && this.energy >= max * config.shipRestartShare) {
			this.depleted = false;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.power_up"), SoundSource.NEUTRAL, 0.8F, 1.0F);

			if (pilot != null) {
				pilot.sendOverlayMessage(Component.translatableWithFallback("sabercraft.ship.energy_ready", "Engines charged. Ready to fly."));
			}
		}

		this.lastUse = use;
		Starships.storeEnergy(this);
		this.sendEnergy(pilot, wasDepleted != this.depleted);
	}

	private void sendEnergy(@Nullable ServerPlayer pilot, boolean now) {
		if (pilot == null || !now && this.tickCount % 5 != 0 || !ServerPlayNetworking.canSend(pilot, ShipEnergyPayload.TYPE)) {
			return;
		}

		AbilityConfig config = AbilityConfig.get();
		float max = config.flightTicks(this.ship);
		byte state = !config.shipEnergy ? ShipEnergyPayload.UNLIMITED : this.depleted ? ShipEnergyPayload.EMPTY : ShipEnergyPayload.NORMAL;
		float rate = max / config.rechargeTicks(this.ship);
		float seconds = this.depleted ? Math.max(0.0F, max * config.shipRestartShare - this.energy) / rate / 20.0F
				: this.energy / Math.max(0.5F, this.lastUse) / 20.0F;
		ServerPlayNetworking.send(pilot, new ShipEnergyPayload(this.getId(), Mth.clamp(this.energy / max, 0.0F, 1.0F), state,
				(short) Math.min(Short.MAX_VALUE, Math.round(seconds))));
	}

	/** Phase 13: flight energy left, in ticks at full throttle (-1 before the first tick). */
	public float energy() {
		return this.energy;
	}

	/** Phase 13: whether the engines are out until the energy is charged again. */
	public boolean depleted() {
		return this.depleted;
	}

	/** Phase 13: takes over the energy the owner's ship had (parked ships charge meanwhile). */
	void restoreEnergy(float energy, boolean depleted) {
		this.energy = energy;
		this.depleted = depleted;
	}

	/** Only for tests: sets the energy, in ticks of flight at full throttle. */
	public void setEnergyForTest(float energy) {
		this.energy = energy;
	}

	/** Only for tests: as if the ship hit something at {@code impact} blocks per tick. */
	public void impactForTest(double impact, boolean landing) {
		this.crashCooldown = 0;
		this.landCooldown = 0;
		this.impact(impact, landing, false, this.pilot());
	}

	/** Only for tests: how fast the last hard impact was, blocks per tick. */
	public static double lastImpact() {
		return lastImpact;
	}

	/**
	 * Fires the next laser cannon, if the cannons are ready: a fast bolt from the cannon, its flash
	 * on every client and its sound. Ships with a salvo (the TIE Fighter) fire all cannons at once.
	 * Returns whether it fired.
	 */
	boolean fire(ServerPlayer pilot) {
		if (this.fireCooldown > 0 || this.ship.cannons().isEmpty()) {
			return false;
		}

		ServerLevel level = (ServerLevel) this.level();
		this.fireCooldown = this.ship.interval();
		int count = this.ship.salvo() ? this.ship.cannons().size() : 1;
		Vec3 forward = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
		// Ship space to the world: x to the right of the nose, y up, z along the nose.
		Vec3 right = Vec3.directionFromRotation(0.0F, this.getYRot() + 90.0F);
		Vec3 up = right.cross(forward);
		BlasterType type = BlasterType.byId(SaberCraft.id(this.ship.laser())).orElse(BlasterType.XWING);
		Vec3 sound = null;

		for (int i = 0; i < count; i++) {
			int index = this.cannon;
			this.cannon = (this.cannon + 1) % this.ship.cannons().size();
			Vec3 local = this.ship.cannons().get(index);
			Vec3 muzzle = this.position().add(right.scale(local.x)).add(up.scale(local.y)).add(forward.scale(local.z));
			BlasterBolt bolt = new BlasterBolt(level, pilot, type, type.damage() * AbilityConfig.get().shipLaserDamage, false, type.knockback());
			bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
			bolt.shoot(forward.x, forward.y, forward.z, (float) (type.speed() + this.speed), type.spread());
			level.addFreshEntity(bolt);
			Starships.shot(this, index);
			sound = sound == null ? muzzle : sound.add(muzzle).scale(0.5);
		}

		// Phase 13: a long burst gets a little quieter, so held fire does not drown everything.
		this.burst = this.sinceShot <= this.ship.interval() + 2 ? this.burst + 1 : 0;
		this.sinceShot = 0;
		float volume = (this.ship.capital() ? 1.0F : 0.8F) * (this.burst > 3 ? 0.75F : 1.0F);
		level.playSound(null, sound.x, sound.y, sound.z, sound("ship." + this.ship.id() + ".laser"), SoundSource.PLAYERS, volume,
				0.94F + this.random.nextFloat() * 0.12F);
		return true;
	}

	/**
	 * Phase 12: ships can be hit, when the server allows it ({@code ship_damage}): by attacks of
	 * creatures and other players (lasers, blasters, arrows, sabers) and by explosions. Never by
	 * their own owner or pilot, by falling, fire or anything else without an attacker, and never
	 * when crashing (that only costs speed).
	 */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || this.crashing) {
			return false;
		}

		if (!AbilityConfig.get().shipDamage) {
			return true;
		}

		Entity attacker = source.getEntity();

		if (attacker == null) {
			return !source.is(DamageTypeTags.IS_EXPLOSION);
		}

		return attacker == this || attacker.getUUID().equals(this.owner) || this.hasPassenger(attacker);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);

		if (hurt && !this.crashing) {
			this.sinceHit = 0;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + this.ship.height() * 0.5, this.getZ(), 10, 0.6, 0.4, 0.6, 0.2);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.hit"), SoundSource.NEUTRAL, 0.9F,
					0.9F + this.random.nextFloat() * 0.2F);
		}

		return hurt;
	}

	/** Phase 12: the hull at 0: a blast of fire and smoke (no block breaks), the pilot falls out gently. */
	private void explode() {
		ServerLevel level = (ServerLevel) this.level();
		explosions++;
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + this.ship.height() * 0.5, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + this.ship.height() * 0.5, this.getZ(), 30, 1.2, 0.8, 1.2, 0.05);
		level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + this.ship.height() * 0.5, this.getZ(), 30, 1.0, 0.6, 1.0, 0.08);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.explode"), SoundSource.NEUTRAL, 1.5F, 1.0F);

		for (Entity passenger : this.getPassengers()) {
			passenger.stopRiding();

			if (passenger instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, true));
			}
		}
	}

	/** Only for tests: how many ships were destroyed. */
	public static int explosions() {
		return explosions;
	}

	/** Only for tests: how many crashes there were. */
	public static int crashes() {
		return crashes;
	}

	static Holder<SoundEvent> sound(String name) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id(name)));
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			Starships.board(serverPlayer, this);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		Vec3 spot = super.getDismountLocationForPassenger(passenger);

		// Climbing out high up: the flight suit's repulsor lets the pilot glide down.
		BlockPos below = BlockPos.containing(spot);
		boolean high = true;

		for (int i = 1; i <= 4 && high; i++) {
			high = this.level().getBlockState(below.below(i)).isAir();
		}

		if (high) {
			passenger.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, true));
		}

		return spot;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty() && passenger instanceof Player;
	}

	/** Only the server steers, never a client (a pig would follow a carrot on a stick). */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUseSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.HEAD;
	}

	@Override
	protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
		return false;
	}

	@Override
	public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState blockState) {
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.explode();
			Starships.destroyed(this);
		}

		super.die(source);
		Starships.died(this);
	}
}
