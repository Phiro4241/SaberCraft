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
	/** Phase 12: ticks between two voices of the same droid. */
	static final int VOICE_GAP = 30;
	/** Phase 12: ticks none of an owner's droids may have spoken before one of them chatters. */
	static final int CHATTER_GAP = 300;

	private final DroidType droid;
	private final UUID owner;
	private boolean staying;
	private double lastDistance = Double.MAX_VALUE;
	private int stuckTicks;
	/** Phase 23: ticks since its owner was last in a narrow passage (crawling through a vent or a gap). */
	private int ownerNarrowAgo = 1000;
	private @Nullable LivingEntity burstTarget;
	private int burstLeft;
	private int burstDelay;
	/** Phase 13: damage of each bolt of the current burst ({@code droid_balance}, group penalty). */
	private float burstDamage = 3.0F;
	private long lastVoice = Long.MIN_VALUE / 2;

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

	/**
	 * Phase 21: where a droid should keep itself instead of right at its owner's heels (the story
	 * places its droids behind and beside the player), or null for the normal following.
	 */
	public interface Advisor {
		@Nullable Spot spot(DroidEntity droid, ServerPlayer owner);
	}

	/** Phase 21: how close a droid that stood in another goes to its place. */
	static final double TIGHT = 0.35;

	/**
	 * Phase 21: a place to keep to (in the level), how far from it the droid may be before it sets
	 * off, and how close it comes before it stops.
	 */
	public record Spot(Vec3 at, double start, double stop) {
	}

	/** Phase 21: set by the story; null outside it. */
	public static @Nullable Advisor advisor;

	/** Phase 21: an errand (the story sends M-31 somewhere): none, on the way, there, it could not get there, back to where it waited. */
	public static final int ERRAND_NONE = 0;
	public static final int ERRAND_GOING = 1;
	public static final int ERRAND_THERE = 2;
	public static final int ERRAND_FAILED = 3;
	public static final int ERRAND_BACK = 4;

	private int errand = ERRAND_NONE;
	private @Nullable Vec3 errandAt;
	private int errandTicks;
	/** Phase 21: where it was told to wait (it goes back there after an errand). */
	private @Nullable Vec3 stayAt;

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new ErrandGoal());
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
		this.stayAt = staying ? this.position() : null;
		this.getNavigation().stop();
	}

	/**
	 * Phase 21: sends the droid to a place (it drives there on its own way, round walls). Returns
	 * false if it cannot get there from here. It stays there until {@link #endErrand}.
	 */
	public boolean startErrand(Vec3 at, int ticks) {
		net.minecraft.world.level.pathfinder.Path path = this.getNavigation().createPath(at.x, at.y, at.z, 0);

		if (path == null || !path.canReach() && Vec3.atBottomCenterOf(path.getTarget()).distanceTo(at) > 1.5) {
			return false;
		}

		this.errand = ERRAND_GOING;
		this.errandAt = at;
		this.errandTicks = ticks;
		this.getNavigation().moveTo(path, 1.5);
		return true;
	}

	/** Phase 21: where the errand is (none, going, there, failed, back). */
	public int errand() {
		return this.errand;
	}

	/** Phase 21: the errand is over: back to following (or to where it waited). */
	public void endErrand() {
		if (this.staying && this.stayAt != null && this.errand != ERRAND_NONE) {
			this.errand = ERRAND_BACK;
			this.errandTicks = 200;
			this.getNavigation().moveTo(this.stayAt.x, this.stayAt.y, this.stayAt.z, 1.2);
			return;
		}

		this.errand = ERRAND_NONE;
		this.errandAt = null;
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

		if (this.level() instanceof ServerLevel) {
			ServerPlayer owner = this.ownerPlayer();
			this.ownerNarrowAgo = owner != null && narrow(owner) ? 0 : Math.min(1000, this.ownerNarrowAgo + 1);

			// Phase 23: fell off a walkway into an open shaft (the reactor's bridges have no
			// railings): back by its owner, unhurt, instead of lost in the depth.
			if (owner != null && !this.onGround() && this.fallDistance > 5.0 && this.getY() < owner.getY() - 4.0
					&& com.phiro.sabercraft.interior.ShipInteriors.isInterior(this.level()) && this.teleportToOwner()) {
				this.resetFallDistance();
				this.setDeltaMovement(Vec3.ZERO);
			}

			if (this.tickCount % 2 == 0 && this.onGround()) {
				this.stepOffProps();
			}
		}
	}

	/**
	 * Phase 26: whether a point (world space, a droid's feet) is on or at a prop of a ship's
	 * interior that droids keep off ({@link com.phiro.sabercraft.interior.InteriorPlan#keepClear}),
	 * its body ({@code half} wide either way) counted.
	 */
	public static boolean keptClear(net.minecraft.world.level.Level level, double x, double y, double z, double half) {
		if (!com.phiro.sabercraft.interior.ShipInteriors.isInterior(level)) {
			return false;
		}

		com.phiro.sabercraft.interior.InteriorPlan plan = com.phiro.sabercraft.interior.ShipInteriors.planAt(x, z);
		BlockPos origin = com.phiro.sabercraft.interior.ShipInteriors.originAt(x, z);

		if (plan == null || origin == null || plan.keepClear().isEmpty()) {
			return false;
		}

		net.minecraft.world.phys.AABB feet = new net.minecraft.world.phys.AABB(x - half, y, z - half, x + half, y + 0.4, z + half)
				.move(-origin.getX(), -origin.getY(), -origin.getZ());

		for (net.minecraft.world.phys.AABB box : plan.keepClear()) {
			if (box.intersects(feet)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 26: its ways never lead over a prop it keeps off (the cells of such props count as
	 * blocked for its path finding); everywhere else it walks as before.
	 */
	@Override
	protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(net.minecraft.world.level.Level level) {
		return new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level) {
			@Override
			protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int maxVisitedNodes) {
				this.nodeEvaluator = new WalkNodeEvaluator() {
					@Override
					public PathType getPathType(net.minecraft.world.level.pathfinder.PathfindingContext context, int x, int y, int z) {
						if (keptClear(context.level() instanceof net.minecraft.world.level.Level l ? l : level, x + 0.5, y + 0.05, z + 0.5, 0.2)) {
							return PathType.BLOCKED;
						}

						return super.getPathType(context, x, y, z);
					}
				};
				return new net.minecraft.world.level.pathfinder.PathFinder(this.nodeEvaluator, maxVisitedNodes);
			}
		};
	}

	/** Phase 26: ticks it has stood on or at a prop it keeps off (it steps off; after a while it is put beside it). */
	private int onProp;

	/**
	 * Phase 26: standing on or at a prop it keeps off (pushed there, or the prop's room changed
	 * round it): it steps off to the nearest free place by it, and is put there if it cannot.
	 */
	private void stepOffProps() {
		if (!keptClear(this.level(), this.getX(), this.getY() + 0.05, this.getZ(), this.getBbWidth() / 2.0)) {
			this.onProp = 0;
			return;
		}

		this.onProp++;
		BlockPos here = this.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = here.offset(dx, dy, dz);
					double distance = dx * dx + dz * dz + dy * dy * 0.5;

					if (distance < bestDistance && this.canTeleportTo(pos)) {
						bestDistance = distance;
						best = pos;
					}
				}
			}
		}

		if (best == null) {
			return;
		}

		if (this.onProp > 30) {
			this.snapTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, this.getYRot(), this.getXRot());
			this.getNavigation().stop();
			this.onProp = 0;
			propsLeft++;
		} else if (this.onProp % 10 == 1) {
			this.getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 1.0);
		}
	}

	private static int propsLeft;

	/** Phase 26, for tests: how often a droid had to be put off a prop it kept off. */
	public static int propsLeftForTest() {
		return propsLeft;
	}

	/** Phase 23: how long (ticks) after its owner came out of a narrow passage before the droid catches up by jumping to them. */
	static final int NARROW_GRACE = 30;

	/**
	 * Phase 23: whether a player is in a narrow passage: crawling (a vent, a one-block-high gap),
	 * or with no headroom to stand.
	 */
	public static boolean narrow(Player player) {
		return player.getPose() == net.minecraft.world.entity.Pose.SWIMMING && !player.isInWater() || player.isVisuallyCrawling();
	}

	/** Phase 23: whether the droid itself is in a narrow passage (no room above it to stand as a person). */
	boolean inPassage() {
		return !this.level().noCollision(this, this.getBoundingBox().setMaxY(this.getY() + 1.8).deflate(0.05, 0.0, 0.05));
	}

	/**
	 * Phase 23: while its owner crawls through a narrow passage the droid never jumps to them (it
	 * would land in the passage, in front of them, in the way and in their view): one already in the
	 * passage behind them follows on, one outside waits outside; it catches up once the owner is out.
	 */
	boolean waitsForOwner() {
		return this.ownerNarrowAgo < NARROW_GRACE;
	}

	/** B1: fires {@code bolts} blaster bolts of {@code damage} at the target, one every few ticks. */
	void startBurst(LivingEntity target, int bolts, float damage) {
		this.burstTarget = target;
		this.burstDamage = damage;
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
		BlasterBolt bolt = new BlasterBolt(level, this, BlasterType.E11, this.burstDamage * AbilityConfig.get().blasterDamage, false, 0.3F);
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

	/**
	 * Plays one of the droid's sounds ({@code ambient}, {@code interact}, {@code ability}). Phase 12:
	 * never two voices of the same droid right after each other (within {@value #VOICE_GAP}
	 * ticks); the ability always speaks, as it says what the droid does.
	 */
	public void playVoice(String kind, float volume) {
		long now = this.level().getGameTime();

		if (!kind.equals("ability") && now - this.lastVoice < VOICE_GAP && now >= this.lastVoice) {
			return;
		}

		this.lastVoice = now;
		Droids.spoke(this, now);
		this.level().playSound(null, this.getX(), this.getY() + this.droid.height() * 0.7, this.getZ(), sound(this.droid, kind), SoundSource.NEUTRAL,
				volume, 0.95F + this.random.nextFloat() * 0.1F);
	}

	/**
	 * Phase 12: idle chatter is rare. A droid only chatters when none of its owner's droids spoke
	 * for a while (so they never beep one after the other), its owner is close, and it is not
	 * busy (following at a distance, firing).
	 */
	@Override
	public void playAmbientSound() {
		ServerPlayer owner = this.ownerPlayer();

		if (owner == null || this.distanceToSqr(owner) > 16.0 * 16.0 || this.burstLeft > 0 || AbilityConfig.get().droidChatterSeconds <= 0
				|| !Droids.quietFor(this, this.level().getGameTime(), CHATTER_GAP)) {
			return;
		}

		this.playVoice("ambient", 0.55F);
	}

	static Holder<SoundEvent> sound(DroidType droid, String kind) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id("droid." + droid.id() + "." + kind)));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return sound(this.droid, "ambient").value();
	}

	/** Phase 12: on average about {@code droid_chatter_seconds} between two idle chatters of one droid. */
	@Override
	public int getAmbientSoundInterval() {
		int seconds = AbilityConfig.get().droidChatterSeconds;
		return seconds <= 0 ? 72000 : Math.max(100, seconds * 20 - 40);
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
			this.playSound(Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id("droid.step"))).value(), 0.07F, 1.0F);
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

	/**
	 * Puts the droid next to its owner if there is room (as a tamed wolf does). Phase 23: behind
	 * or beside them, never in front of them (in their way or their view), never in a crawl way or
	 * right at its mouth, and not while they crawl through one (see {@link #waitsForOwner}).
	 */
	public boolean teleportToOwner() {
		ServerPlayer player = this.ownerPlayer();

		if (player == null || narrow(player)) {
			return false;
		}

		BlockPos target = player.blockPosition();
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 moving = player.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		Vec3 ahead = moving.lengthSqr() > 0.002 ? moving.normalize().add(look).normalize() : look;
		BlockPos best = null;
		double bestScore = -Double.MAX_VALUE;

		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
					continue;
				}

				Vec3 way = new Vec3(dx, 0.0, dz).normalize();
				double front = way.dot(ahead);

				// In front of the player (within about 60 degrees either side): not there.
				if (front > 0.5) {
					continue;
				}

				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = target.offset(dx, dy, dz);

					// Phase 24: never onto a turbolift's pad either (it got stuck on the reactor's).
					if (!this.canTeleportTo(pos) || this.mouthOfPassage(pos) || onLift(this.level(), pos) || !this.inSightOf(player, pos)) {
						continue;
					}

					// Behind is best, then beside; about two blocks off; a little chance between equals.
					double score = -front * 2.0 - Math.abs(Math.sqrt(dx * dx + dz * dz) - 2.5) * 0.4 - Math.abs(dy) * 0.3 + this.random.nextDouble() * 0.2;

					if (score > bestScore) {
						bestScore = score;
						best = pos;
					}
				}
			}
		}

		if (best == null) {
			return false;
		}

		this.snapTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, this.getYRot(), this.getXRot());
		this.getNavigation().stop();
		this.lastDistance = Double.MAX_VALUE;
		this.stuckTicks = 0;
		return true;
	}

	/**
	 * Phase 23: whether the place is on the player's side of every wall (a clear line from them to
	 * it, low or at the chest): behind a wall it could land in a locked room and never come out.
	 */
	private boolean inSightOf(ServerPlayer player, BlockPos pos) {
		for (double h : new double[] {0.6, 1.3}) {
			Vec3 from = player.position().add(0.0, h, 0.0);
			Vec3 to = Vec3.atBottomCenterOf(pos).add(0.0, h, 0.0);
			net.minecraft.world.phys.HitResult hit = this.level().clip(new net.minecraft.world.level.ClipContext(from, to,
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));

			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
				return true;
			}
		}

		return false;
	}

	private boolean canTeleportTo(BlockPos pos) {
		if (WalkNodeEvaluator.getPathTypeStatic(this, pos) != PathType.WALKABLE
				|| this.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock
				// Phase 26: never onto a pedestal or a console of the story, nor right at it.
				|| keptClear(this.level(), pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 0.35)) {
			return false;
		}

		BlockPos delta = pos.subtract(this.blockPosition());
		return this.level().noCollision(this, this.getBoundingBox().move(delta));
	}

	/**
	 * Phase 23: whether a place is in a narrow passage or right at the mouth of one (a gap one
	 * block high beside it): a droid put there would block the way out of it.
	 */
	private boolean mouthOfPassage(BlockPos pos) {
		if (this.lowCeiling(pos) || this.byCrawlMouth(pos)) {
			return true;
		}

		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(side);

			if (this.level().getBlockState(next).getCollisionShape(this.level(), next).isEmpty() && this.lowCeiling(next)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 23: whether a place is within two blocks of where a crawl way of the ship opens (its
	 * hatch, closed or open: a closed hatch is a barrier, so the cell beside it looked free). One
	 * coming out of it would find the droid right in their way.
	 */
	private boolean byCrawlMouth(BlockPos pos) {
		if (!com.phiro.sabercraft.interior.ShipInteriors.isInterior(this.level())) {
			return false;
		}

		com.phiro.sabercraft.interior.InteriorPlan plan = com.phiro.sabercraft.interior.ShipInteriors.planAt(pos.getX() + 0.5, pos.getZ() + 0.5);
		BlockPos origin = com.phiro.sabercraft.interior.ShipInteriors.originAt(pos.getX() + 0.5, pos.getZ() + 0.5);

		if (plan == null || origin == null) {
			return false;
		}

		for (BlockPos mouth : plan.crawlMouths()) {
			BlockPos at = origin.offset(mouth);

			if (Math.abs(at.getY() - pos.getY()) <= 1 && Math.abs(at.getX() - pos.getX()) <= 2 && Math.abs(at.getZ() - pos.getZ()) <= 2) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 24: whether a place is on (or right at) a turbolift of a ship's interior: a droid put
	 * there stood on the moving pad and got stuck in its cage.
	 */
	public static boolean onLift(net.minecraft.world.level.Level level, BlockPos pos) {
		if (!com.phiro.sabercraft.interior.ShipInteriors.isInterior(level)) {
			return false;
		}

		com.phiro.sabercraft.interior.InteriorPlan plan = com.phiro.sabercraft.interior.ShipInteriors.planAt(pos.getX() + 0.5, pos.getZ() + 0.5);
		BlockPos origin = com.phiro.sabercraft.interior.ShipInteriors.originAt(pos.getX() + 0.5, pos.getZ() + 0.5);

		if (plan == null || origin == null) {
			return false;
		}

		Vec3 local = Vec3.atBottomCenterOf(pos).subtract(Vec3.atLowerCornerOf(origin)).add(0.0, 0.1, 0.0);

		for (com.phiro.sabercraft.interior.InteriorPlan.Lift lift : plan.lifts()) {
			if (lift.area().inflate(0.6, 0.5, 0.6).contains(local)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 24: whether a droid standing there would be in someone's way: in a narrow passage, by
	 * the mouth of a crawl way, on a turbolift (for the bigger astromech when it picks where to keep).
	 */
	public boolean inTheWay(BlockPos pos) {
		return this.mouthOfPassage(pos) || onLift(this.level(), pos);
	}

	/** Phase 23: whether a person could not stand in that place (a solid block right above its floor cell). */
	private boolean lowCeiling(BlockPos pos) {
		BlockPos above = pos.above();
		return !this.level().getBlockState(above).getCollisionShape(this.level(), above).isEmpty();
	}

	/** Phase 21: drives to the errand's place and waits there (or back to where it was told to wait). */
	final class ErrandGoal extends Goal {
		private int repath;
		/** Ticks it has stood still (its way at an end) short of the place. */
		private int idle;

		ErrandGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return DroidEntity.this.errand != ERRAND_NONE && !DroidEntity.this.isPassenger();
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			DroidEntity droid = DroidEntity.this;

			if (droid.errand == ERRAND_BACK) {
				if (droid.stayAt == null || droid.position().distanceTo(droid.stayAt) < 0.8 || --droid.errandTicks <= 0) {
					droid.errand = ERRAND_NONE;
					droid.getNavigation().stop();
				} else if (--this.repath <= 0) {
					this.repath = 20;
					droid.getNavigation().moveTo(droid.stayAt.x, droid.stayAt.y, droid.stayAt.z, 1.2);
				}

				return;
			}

			Vec3 at = droid.errandAt;

			if (at == null) {
				droid.errand = ERRAND_NONE;
				return;
			}

			if (droid.errand == ERRAND_GOING) {
				double distance = Math.hypot(droid.getX() - at.x, droid.getZ() - at.z);

				if (distance < 1.1 && Math.abs(droid.getY() - at.y) < 1.5) {
					droid.errand = ERRAND_THERE;
					droid.getNavigation().stop();
				} else if ((this.idle = droid.getNavigation().isDone() ? this.idle + 1 : 0) > 20 && distance < 2.2) {
					// As close as it gets (someone in the way): there.
					droid.errand = ERRAND_THERE;
				} else if (--droid.errandTicks <= 0) {
					droid.errand = distance < 2.5 ? ERRAND_THERE : ERRAND_FAILED;
					droid.getNavigation().stop();
				} else if (droid.getNavigation().isDone() && --this.repath <= 0) {
					this.repath = 10;
					droid.getNavigation().moveTo(at.x, at.y, at.z, 1.5);
				}
			} else if (droid.errand == ERRAND_THERE) {
				// There: it turns about, busy with something.
				droid.setYRot(droid.getYRot() + 9.0F);
				droid.yBodyRot = droid.getYRot();
			}
		}
	}

	/** Walks after the owner; teleports when far away or stuck for a few seconds. */
	final class FollowGoal extends Goal {
		private @Nullable ServerPlayer target;
		private int recalc;
		/** Phase 21: set off because it stood in another droid or at the player's feet: on to the place itself. */
		private boolean tight;

		FollowGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			ServerPlayer player = DroidEntity.this.ownerPlayer();

			if (DroidEntity.this.staying || player == null || player.isSpectator() || DroidEntity.this.isPassenger() || DroidEntity.this.errand != ERRAND_NONE) {
				return false;
			}

			// Phase 23: the owner crawls through a narrow passage: one outside it waits there.
			if (narrow(player) && !DroidEntity.this.inPassage()) {
				return false;
			}

			// Phase 21: a place the story keeps it at (behind and beside the player): it sets off
			// only when that place is a few steps away, not at every step of the player.
			Spot spot = advisor != null ? advisor.spot(DroidEntity.this, player) : null;

			if (spot != null) {
				double away = DroidEntity.this.position().distanceTo(spot.at());
				// Standing in another droid or at the player's feet: off to its place even when it is near.
				boolean crowded = DroidEntity.this.distanceToSqr(player) < 1.2 * 1.2 || !DroidEntity.this.level()
						.getEntitiesOfClass(DroidEntity.class, DroidEntity.this.getBoundingBox().inflate(0.5), other -> other != DroidEntity.this).isEmpty();

				// Two droids that crossed (each one's place beyond the other) both go, right onto their
				// places: the places are apart, and only one of them going left them stuck in each other.
				if (away < spot.start() && !(crowded && away > TIGHT + 0.1)) {
					return false;
				}

				this.tight = crowded && away < spot.start();
			} else if (DroidEntity.this.distanceToSqr(player) < 3.0 * 3.0) {
				return false;
			}

			this.target = player;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			if (this.target == null || DroidEntity.this.staying || !this.target.isAlive() || this.target.level() != DroidEntity.this.level()
					|| DroidEntity.this.errand != ERRAND_NONE || narrow(this.target) && !DroidEntity.this.inPassage()) {
				return false;
			}

			Spot spot = advisor != null ? advisor.spot(DroidEntity.this, this.target) : null;
			return spot != null ? DroidEntity.this.position().distanceTo(spot.at()) > (this.tight ? TIGHT : spot.stop())
					: DroidEntity.this.distanceToSqr(this.target) > 2.0 * 2.0;
		}

		@Override
		public void start() {
			this.recalc = 0;
			DroidEntity.this.lastDistance = Double.MAX_VALUE;
			DroidEntity.this.stuckTicks = 0;
		}

		@Override
		public void stop() {
			Spot spot = this.target != null && advisor != null && !DroidEntity.this.staying && DroidEntity.this.errand == ERRAND_NONE
					? advisor.spot(DroidEntity.this, this.target) : null;
			this.target = null;
			this.tight = false;
			DroidEntity.this.getNavigation().stop();

			// Phase 22: near its place, it rolls the last bit onto it (it used to stop up to a
			// block short, which could be right beside the player).
			if (spot != null && DroidEntity.this.position().distanceTo(spot.at()) < 2.0 && DroidEntity.this.clearTo(spot.at())) {
				DroidEntity.this.getMoveControl().setWantedPosition(spot.at().x, spot.at().y, spot.at().z, 1.0);
			}
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

				Spot spot = advisor != null ? advisor.spot(DroidEntity.this, this.target) : null;

				if ((distance >= TELEPORT_DISTANCE_SQR || DroidEntity.this.stuckTicks > STUCK_TICKS) && !DroidEntity.this.waitsForOwner()) {
					DroidEntity.this.teleportToOwner();
				} else if (spot != null) {
					double speed = 1.0 + Math.min(0.6, distance / 200.0);
					Vec3 at = spot.at();

					// Phase 22: the last steps straight onto the place. A path ends within a block of
					// its goal, so a place in the next block counted as reached at once, and the droid
					// stood a step short of it, at the player's feet, for good.
					if (DroidEntity.this.position().distanceTo(at) < 2.0 && clearTo(at)) {
						DroidEntity.this.getNavigation().stop();
						DroidEntity.this.getMoveControl().setWantedPosition(at.x, at.y, at.z, speed);
					} else {
						DroidEntity.this.getNavigation().moveTo(at.x, at.y, at.z, 0, speed);
					}
				} else {
					DroidEntity.this.getNavigation().moveTo(this.target, 1.0 + Math.min(0.6, distance / 200.0));
				}
			}
		}
	}

	/** Phase 22: whether the droid can roll straight to a place near it (no block in between). */
	private boolean clearTo(Vec3 at) {
		Vec3 from = this.position().add(0.0, 0.3, 0.0);
		return this.level().clip(new net.minecraft.world.level.ClipContext(from, at.add(0.0, 0.3, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}
}
