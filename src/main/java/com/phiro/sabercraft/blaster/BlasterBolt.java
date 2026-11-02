package com.phiro.sabercraft.blaster;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.advancement.SaberAdvancements;

/**
 * A blaster bolt, on the server. It flies straight (no gravity, no drag), hurts what it hits and
 * leaves sparks on blocks. Lightsabers can send it back like other projectiles.
 *
 * <p>For the network it is a plain vanilla snowball carrying a snowball item that is marked as a
 * bolt in its model data ({@code strings: ["sabercraft:bolt", <type>]}). So no new entity type has
 * to be registered and players without SaberCraft can still join the server (they see a
 * snowball); clients with SaberCraft draw a glowing bolt instead (the client's
 * {@code BlasterBoltRenderer}). Bolts are never saved with the world: after a world load they
 * would only be snowballs.
 */
public class BlasterBolt extends ThrowableItemProjectile {
	public static final String MARKER = SaberCraft.id("bolt").toString();
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, SaberCraft.id("blaster"));
	private static int blasts;

	private final BlasterType type;
	private final float damage;
	private final boolean fire;
	private final float knockback;

	public BlasterBolt(ServerLevel level, LivingEntity shooter, BlasterType type, float damage, boolean fire, float knockback) {
		super(EntityTypes.SNOWBALL, shooter, level, item(type));
		this.type = type;
		this.damage = damage;
		this.fire = fire;
		this.knockback = knockback;
		this.setNoGravity(true);
	}

	/** The item a bolt carries: a snowball marked as a bolt of that type. */
	public static ItemStack item(BlasterType type) {
		ItemStack stack = new ItemStack(Items.SNOWBALL);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(MARKER, type.id().toString()), List.of()));
		return stack;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.SNOWBALL;
	}

	@Override
	protected float getAirDrag() {
		return 1.0F;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void tick() {
		// Vanilla tests each step as a line against the targets' boxes, grown by a margin that gets
		// larger every tick while the projectile is young. A step that ends just in front of a
		// target can therefore start the next step already inside the grown box, and a line that
		// starts inside a box never hits it: the bolt flew straight through. Fast bolts ran into
		// this often (one bowcaster shot in three in the arsenal test), so such a start is a hit.
		EntityHitResult inside = this.startInsideTarget();

		if (inside != null) {
			this.hitTargetOrDeflectSelf(inside);

			if (!this.isAlive()) {
				return;
			}
		}

		// Phase 14: big ships are much larger than their collision box; their hull is checked too.
		EntityHitResult hull = this.level().isClientSide() ? null : this.hullHit();

		if (hull != null) {
			this.hitTargetOrDeflectSelf(hull);

			if (!this.isAlive()) {
				return;
			}
		}

		super.tick();

		// Bolts that hit nothing fade at the end of their range (Phase 11: per type).
		if (this.isAlive() && this.tickCount > this.type.lifetime()) {
			this.discard();
		}
	}

	/**
	 * Phase 14: a starship whose hull (its {@code ShipFeatures} boxes, as big as the drawn model)
	 * this step of the bolt crosses before any block, or {@code null}. Never the ship of the shooter.
	 */
	private @Nullable EntityHitResult hullHit() {
		Vec3 from = this.position();
		Vec3 to = from.add(this.getDeltaMovement());
		Entity owner = this.getOwner();
		EntityHitResult best = null;
		double bestShare = 2.0;

		for (com.phiro.sabercraft.ship.ShipEntity ship : this.level().getEntitiesOfClass(com.phiro.sabercraft.ship.ShipEntity.class,
				this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(48.0), Entity::isAlive)) {
			if (owner != null && (ship.hasPassenger(owner) || ship.owner().equals(owner.getUUID())) || !this.canHitEntity(ship)) {
				continue;
			}

			double share = com.phiro.sabercraft.ship.ShipFeatures.of(ship.ship()).clip(ship.position(), ship.getYRot(), from, to);

			if (share >= 0.0 && share <= 1.0 && share < bestShare) {
				Vec3 at = from.add(to.subtract(from).scale(share));
				HitResult block = this.level().clip(new net.minecraft.world.level.ClipContext(from, at, net.minecraft.world.level.ClipContext.Block.COLLIDER,
						net.minecraft.world.level.ClipContext.Fluid.NONE, this));

				if (block.getType() == HitResult.Type.MISS) {
					bestShare = share;
					best = new EntityHitResult(ship, at);
				}
			}
		}

		return best;
	}

	/** A target whose hit box, grown by the same margin vanilla uses this tick, already holds the bolt. */
	private @Nullable EntityHitResult startInsideTarget() {
		Vec3 at = this.position();
		float margin = ProjectileUtil.computeMargin(this);
		Entity owner = this.getOwner();

		for (Entity target : this.level().getEntities(this, this.getBoundingBox().inflate(1.0), this::canHitEntity)) {
			// Never the shooter: right after a saber sent the bolt back it still sits inside them.
			if (target != owner && target.getBoundingBox().inflate(margin).contains(at)) {
				return new EntityHitResult(target, at);
			}
		}

		return null;
	}

	@Override
	protected void onHit(HitResult hit) {
		if (hit.getType() == HitResult.Type.ENTITY) {
			this.onHitEntity((EntityHitResult) hit);
		} else if (hit.getType() == HitResult.Type.BLOCK) {
			this.onHitBlock((BlockHitResult) hit);
		}

		if (this.level() instanceof ServerLevel level) {
			Vec3 at = hit.getLocation();
			int rgb = Blasters.boltRgb(this.type);
			boolean ship = this.type.isShipLaser();
			boolean heavy = this.type == BlasterType.TURBOLASER;
			level.sendParticles(new DustParticleOptions(rgb, heavy ? 2.4F : 1.2F), at.x, at.y, at.z, heavy ? 14 : 8, 0.08, 0.08, 0.08, 0.02);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, heavy ? 12 : 6, 0.05, 0.05, 0.05, 0.25);

			if (hit.getType() == HitResult.Type.BLOCK) {
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, ship ? 6 : 3, 0.06, 0.06, 0.06, 0.01);

				// Phase 13: a ship bolt leaves a short scorch: a puff of hot smoke and a few embers.
				if (ship) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, heavy ? 4 : 1, 0.1, 0.1, 0.1, 0.01);
					level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, heavy ? 6 : 2, 0.08, 0.08, 0.08, 0.02);
				}
			}

			// Phase 13: ship bolts have their own, heavier impact, and no two impact sounds pile up
			// on one spot (a salvo, a held trigger): the first one is heard, the rest only seen.
			if (ImpactSounds.allow(level, at)) {
				String sound = !ship ? "blaster.impact" : heavy ? "ship.laser_impact_heavy" : "ship.laser_impact";
				level.playSound(null, at.x, at.y, at.z, Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id(sound))),
						SoundSource.PLAYERS, ship ? 0.8F : 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
			}

			this.discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		// Never the ship its shooter sits in (Phase 11).
		// Starship lasers spare other players if the server says so.
		Entity owner = this.getOwner();

		if (this.type.isShipLaser() && target instanceof Player
				&& !AbilityConfig.get().shipLasersHitPlayers) {
			return false;
		}

		return (owner == null || owner.getVehicle() != target) && super.canHitEntity(target);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}

		Entity target = hit.getEntity();
		Entity owner = this.getOwner();
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), this, owner);

		if (target.hurtServer(level, source, this.damage) && target instanceof LivingEntity living) {
			// Phase 13: a ship bolt that destroys its target makes a small blast (no block damage);
			// only that hit, never every shot, and not twice on one spot at once.
			if (this.type.isShipLaser() && !living.isAlive()) {
				Vec3 at = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0);
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.03);

				if (ImpactSounds.allow(level, at.add(0.0, 1000.0, 0.0), 10, 6.0)) {
					level.playSound(null, at.x, at.y, at.z, Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id("ship.small_blast"))),
							SoundSource.PLAYERS, 0.9F, 0.9F + this.random.nextFloat() * 0.2F);
				}

				blasts++;
			}

			if (owner instanceof ServerPlayer shooter && shooter.distanceTo(living) >= SaberAdvancements.SHARPSHOOTER_DISTANCE) {
				SaberAdvancements.award(shooter, SaberAdvancements.SHARPSHOOTER);
			}

			if (this.fire) {
				living.igniteForSeconds(5.0F);
			}

			if (this.type.stun() > 0) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, this.type.stun(), 2), owner);
			}

			if (this.knockback > 0.0F) {
				Vec3 push = this.getDeltaMovement().horizontal().normalize().scale(this.knockback * 0.6);
				living.push(push.x, 0.1, push.z);
			}
		}
	}

	public BlasterType type() {
		return this.type;
	}

	/** Only for tests: how many small blasts ship bolts made (Phase 13). */
	public static int blasts() {
		return blasts;
	}
}
