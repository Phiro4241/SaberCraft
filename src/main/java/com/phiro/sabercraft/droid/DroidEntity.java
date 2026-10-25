package com.phiro.sabercraft.droid;

import java.util.EnumSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;

/**
 * A droid on the server (Phase 11). It follows its owner, talks in beeps, can be told to stay, and
 * has one ability (see {@link DroidAbilities}). For the network it is a vanilla pig marked as a
 * droid (see {@link DroidMarker}); its look, size and animations come from the client.
 *
 * <p>A droid is never saved with the world. What a player has called is saved with the player (see
 * {@link Droids}), and the droid is put back next to them when they join, change dimension, or
 * when it got lost (its chunk unloaded, it fell into the void). So there are never two of the same
 * droid, and none is left behind somewhere without its owner. Only damage that also kills creative
 * players (the void, /kill) hurts it.
 */
public class DroidEntity extends Pig {
	/** Teleports to its owner from this far (squared), like a tamed wolf. */
	static final double TELEPORT_DISTANCE_SQR = 14.0 * 14.0;
	/** Seconds without getting closer before it teleports anyway (stuck behind something). */
	static final int STUCK_TICKS = 60;

	private final DroidType droid;
	private final UUID owner;
	private boolean staying;
	private double lastDistance = Double.MAX_VALUE;
	private int stuckTicks;
	private @Nullable LivingEntity burstTarget;
	private int burstLeft;
	private int burstDelay;

	public DroidEntity(ServerLevel level, DroidType droid, ServerPlayer owner) {
		super(EntityTypes.PIG, level);
		this.droid = droid;
		this.owner = owner.getUUID();
		this.setItemSlot(EquipmentSlot.HEAD, DroidMarker.item(droid));
		this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		this.setCustomName(Component.literal(droid.displayName()));
		this.setPersistenceRequired();
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(droid.speed());
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
		this.setHealth(20.0F);
		this.refreshDimensions();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new FollowGoal());
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	public DroidType droid() {
		return this.droid;
	}

	public UUID owner() {
		return this.owner;
	}

	public boolean isStaying() {
		return this.staying;
	}

	public void setStaying(boolean staying) {
		this.staying = staying;
		this.getNavigation().stop();
	}

	/** The owner if they are online in the same level. */
	public @Nullable ServerPlayer ownerPlayer() {
		if (this.level().getServer() == null) {
			return null;
		}

		ServerPlayer player = this.level().getServer().getPlayerList().getPlayer(this.owner);
		return player != null && player.level() == this.level() ? player : null;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level() instanceof ServerLevel && !Droids.isCurrent(this)) {
			// A droid nobody has called (any more): gone, so there is never a second one.
			this.discard();
			return;
		}

		if (this.isOnFire()) {
			this.clearFire();
		}

		if (this.burstLeft > 0 && --this.burstDelay <= 0) {
			this.fireBolt();
		}
	}

	/** B1: fires {@code bolts} blaster bolts at the target, one every few ticks. */
	void startBurst(LivingEntity target, int bolts) {
		this.burstTarget = target;
		this.burstLeft = bolts;
		this.burstDelay = 0;
	}

	private void fireBolt() {
		LivingEntity target = this.burstTarget;
		this.burstLeft--;
		this.burstDelay = 4;

		if (target == null || !target.isAlive() || !(this.level() instanceof ServerLevel level) || !this.hasLineOfSight(target)) {
			this.burstLeft = 0;
			return;
		}

		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		Vec3 from = this.getEyePosition();
		Vec3 aim = target.getBoundingBox().getCenter().subtract(from);
		BlasterBolt bolt = new BlasterBolt(level, this, BlasterType.E11, 3.0F * AbilityConfig.get().blasterDamage, false, 0.3F);
		Vec3 start = from.add(aim.normalize().scale(0.6));
		bolt.setPos(start.x, start.y, start.z);
		bolt.shoot(aim.x, aim.y, aim.z, BlasterType.E11.speed(), 1.0F);
		level.addFreshEntity(bolt);
		level.playSound(null, start.x, start.y, start.z, Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id("blaster.e11"))),
				SoundSource.NEUTRAL, 0.9F, 1.1F);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			if (player.getUUID().equals(this.owner) && player.isSecondaryUseActive()) {
				Droids.toggleStay(serverPlayer, this.droid);
			} else {
				this.playVoice("interact", 1.0F);
				Droids.event(this, Droids.EVENT_INTERACT);
			}
		}

		return InteractionResult.SUCCESS;
	}

	/** Plays one of the droid's sounds ({@code ambient}, {@code interact}, {@code ability}). */
	public void playVoice(String kind, float volume) {
		this.level().playSound(null, this.getX(), this.getY() + this.droid.height() * 0.7, this.getZ(), sound(this.droid, kind), SoundSource.NEUTRAL,
				volume, 0.95F + this.random.nextFloat() * 0.1F);
	}

	static Holder<SoundEvent> sound(DroidType droid, String kind) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id("droid." + droid.id() + "." + kind)));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return sound(this.droid, "ambient").value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
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
		// Servos instead of pig steps; quiet, and not for the rolling or floating ones.
		if (this.droid != DroidType.BB8 && this.droid != DroidType.PROBE && this.droid != DroidType.MOUSE) {
			this.playSound(Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id("droid.step"))).value(), 0.15F, 1.0F);
		}
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
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
		// No zombified piglin: a droid only gets a little scorched.
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

	/** Droids never go through portals on their own; their owner's tick brings them along. */
	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
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
	public void die(DamageSource source) {
		super.die(source);
		Droids.died(this);
	}

	/** Puts the droid next to its owner if there is room (as a tamed wolf does). */
	public boolean teleportToOwner() {
		ServerPlayer player = this.ownerPlayer();

		if (player == null) {
			return false;
		}

		BlockPos target = player.blockPosition();

		for (int attempt = 0; attempt < 16; attempt++) {
			int dx = this.random.nextIntBetweenInclusive(-3, 3);
			int dz = this.random.nextIntBetweenInclusive(-3, 3);

			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue;
			}

			BlockPos pos = target.offset(dx, this.random.nextIntBetweenInclusive(-1, 1), dz);

			if (this.canTeleportTo(pos)) {
				this.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this.getYRot(), this.getXRot());
				this.getNavigation().stop();
				this.lastDistance = Double.MAX_VALUE;
				this.stuckTicks = 0;
				return true;
			}
		}

		return false;
	}

	private boolean canTeleportTo(BlockPos pos) {
		if (WalkNodeEvaluator.getPathTypeStatic(this, pos) != PathType.WALKABLE
				|| this.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
			return false;
		}

		BlockPos delta = pos.subtract(this.blockPosition());
		return this.level().noCollision(this, this.getBoundingBox().move(delta));
	}

	/** Walks after the owner; teleports when far away or stuck for a few seconds. */
	final class FollowGoal extends Goal {
		private @Nullable ServerPlayer target;
		private int recalc;

		FollowGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			ServerPlayer player = DroidEntity.this.ownerPlayer();

			if (DroidEntity.this.staying || player == null || player.isSpectator() || DroidEntity.this.isPassenger()
					|| DroidEntity.this.distanceToSqr(player) < 3.0 * 3.0) {
				return false;
			}

			this.target = player;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.target != null && !DroidEntity.this.staying && this.target.isAlive() && this.target.level() == DroidEntity.this.level()
					&& DroidEntity.this.distanceToSqr(this.target) > 2.0 * 2.0;
		}

		@Override
		public void start() {
			this.recalc = 0;
			DroidEntity.this.lastDistance = Double.MAX_VALUE;
			DroidEntity.this.stuckTicks = 0;
		}

		@Override
		public void stop() {
			this.target = null;
			DroidEntity.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (this.target == null) {
				return;
			}

			double distance = DroidEntity.this.distanceToSqr(this.target);
			DroidEntity.this.getLookControl().setLookAt(this.target, 10.0F, DroidEntity.this.getMaxHeadXRot());

			// Stuck: not one block closer in a few seconds while still away.
			if (distance < DroidEntity.this.lastDistance - 1.0) {
				DroidEntity.this.lastDistance = distance;
				DroidEntity.this.stuckTicks = 0;
			} else if (distance > 5.0 * 5.0) {
				DroidEntity.this.stuckTicks++;
			}

			if (--this.recalc <= 0) {
				this.recalc = this.adjustedTickDelay(10);

				if (distance >= TELEPORT_DISTANCE_SQR || DroidEntity.this.stuckTicks > STUCK_TICKS) {
					DroidEntity.this.teleportToOwner();
				} else {
					DroidEntity.this.getNavigation().moveTo(this.target, 1.0 + Math.min(0.6, distance / 200.0));
				}
			}
		}
	}
}
