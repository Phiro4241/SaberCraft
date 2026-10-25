package com.phiro.sabercraft.ship;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

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
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;

/**
 * A starship on the server (Phase 11): the X-Wing. Its owner climbs in with a right-click and
 * flies it with the movement keys: forward opens the throttle, back brakes, left and right turn,
 * jump pulls the nose up and sprint pushes it down (when hovering slowly they lift and lower the
 * ship straight up and down instead). Sneaking climbs out as from any vehicle. The server alone
 * moves the ship, so every player sees the same flight; its position goes out every tick.
 *
 * <p>It collides with blocks like any entity and never breaks them. Flying into something fast
 * stops it with a jolt and a puff of smoke (no explosion, nothing breaks). Without a pilot it
 * settles to the ground. For the network it is a vanilla pig marked as a ship (see
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

	private static int crashes;

	private final ShipType ship;
	private final UUID owner;
	private double speed;
	private float yawRate;
	private int fireCooldown;
	private int cannon;
	private int crashCooldown;

	public ShipEntity(ServerLevel level, ShipType ship, ServerPlayer owner) {
		super(EntityTypes.PIG, level);
		this.ship = ship;
		this.owner = owner.getUUID();
		this.setItemSlot(EquipmentSlot.HEAD, ShipMarker.item(ship));
		this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		this.setCustomName(Component.literal("X-Wing"));
		this.setPersistenceRequired();
		this.setNoGravity(true);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
		this.setHealth(40.0F);
		// The third-person camera stays far enough behind to see the whole ship.
		this.getAttribute(Attributes.CAMERA_DISTANCE).setBaseValue(9.0);
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

		if (this.isOnFire()) {
			this.clearFire();
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
		double max = this.ship.maxSpeed() * AbilityConfig.get().shipSpeed;

		// Throttle: forward speeds up, back brakes; let go and the ship keeps its speed.
		if (pilot == null) {
			this.speed = Math.max(0.0, this.speed - 0.05);
		} else if (keys.forward()) {
			this.speed = Math.min(max, this.speed + this.ship.accel());
		} else if (keys.backward()) {
			this.speed = Math.max(0.0, this.speed - this.ship.accel() * 2.0);
		}

		// Rudder: turns faster when slow, eases in and out.
		float rudder = (keys.right() ? 1.0F : 0.0F) - (keys.left() ? 1.0F : 0.0F);
		float turn = this.ship.turn() * (this.speed < HOVER_SPEED ? 1.3F : 1.0F);
		this.yawRate += (rudder * turn - this.yawRate) * 0.3F;
		float yaw = Mth.wrapDegrees(this.getYRot() + this.yawRate);

		// Nose up (jump) or down (sprint) in flight; levels out when let go, and when hovering.
		boolean hovering = this.speed < HOVER_SPEED;
		float pitchTarget = hovering ? 0.0F : keys.jump() ? -MAX_PITCH : keys.sprint() ? MAX_PITCH : 0.0F;
		float pitch = this.getXRot() + Mth.clamp(pitchTarget - this.getXRot(), -2.5F, 2.5F);
		this.setYRot(yaw);
		this.setXRot(pitch);
		this.setYBodyRot(yaw);
		this.setYHeadRot(yaw);

		Vec3 motion = Vec3.directionFromRotation(pitch, yaw).scale(this.speed);

		if (pilot != null && hovering) {
			motion = motion.add(0.0, keys.jump() ? LIFT : keys.sprint() ? -LIFT : 0.0, 0.0);
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

		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);

		if (this.horizontalCollision || this.verticalCollision && motion.y > 0.0) {
			if (this.speed > 0.6 && this.crashCooldown == 0) {
				this.crash();
			}

			this.speed *= 0.4;
		}

		if (this.onGround() && motion.y <= 0.0) {
			this.setXRot(this.getXRot() * 0.5F);
		}

		if (pilot != null && this.speed >= max * 0.95) {
			SaberAdvancements.award(pilot, SaberAdvancements.FIRST_FLIGHT);
		}
	}

	/** Flew into something fast: a jolt, smoke and a bang, no damage to blocks or ship. */
	private void crash() {
		this.crashCooldown = 20;
		crashes++;
		ServerLevel level = (ServerLevel) this.level();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.8, this.getZ(), 16, 1.0, 0.4, 1.0, 0.02);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.8, this.getZ(), 20, 1.0, 0.4, 1.0, 0.2);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), sound("ship.crash"), SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/**
	 * Fires the next laser cannon, if the cannons are ready: a fast red bolt from the wing tip, its
	 * flash on every client and its sound. Returns whether it fired.
	 */
	boolean fire(ServerPlayer pilot) {
		if (this.fireCooldown > 0 || this.ship.cannons().isEmpty()) {
			return false;
		}

		ServerLevel level = (ServerLevel) this.level();
		this.fireCooldown = this.ship.interval();
		int index = this.cannon;
		this.cannon = (this.cannon + 1) % this.ship.cannons().size();
		Vec3 local = this.ship.cannons().get(index);
		Vec3 forward = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
		// Ship space to the world: x to the right of the nose, y up, z along the nose.
		Vec3 right = Vec3.directionFromRotation(0.0F, this.getYRot() + 90.0F);
		Vec3 up = right.cross(forward);
		Vec3 muzzle = this.position().add(right.scale(local.x)).add(up.scale(local.y)).add(forward.scale(local.z));
		BlasterType type = BlasterType.XWING;
		BlasterBolt bolt = new BlasterBolt(level, pilot, type, type.damage() * AbilityConfig.get().shipLaserDamage, false,
				type.knockback());
		bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
		bolt.shoot(forward.x, forward.y, forward.z, (float) (type.speed() + this.speed), type.spread());
		level.addFreshEntity(bolt);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, sound("ship.xwing.laser"), SoundSource.PLAYERS, 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
		Starships.shot(this, index);
		return true;
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
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
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
		super.die(source);
		Starships.died(this);
	}
}
