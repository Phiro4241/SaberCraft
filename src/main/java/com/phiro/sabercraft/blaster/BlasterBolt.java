package com.phiro.sabercraft.blaster;

import java.util.List;

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
		super.tick();

		// Bolts that hit nothing fade at the end of their range (Phase 11: per type).
		if (this.isAlive() && this.tickCount > this.type.lifetime()) {
			this.discard();
		}
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
			level.sendParticles(new DustParticleOptions(rgb, 1.2F), at.x, at.y, at.z, 8, 0.08, 0.08, 0.08, 0.02);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.25);

			if (hit.getType() == HitResult.Type.BLOCK) {
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.04, 0.04, 0.04, 0.01);
			}

			level.playSound(null, at.x, at.y, at.z, Holder.direct(SoundEvent.createVariableRangeEvent(SaberCraft.id("blaster.impact"))),
					SoundSource.PLAYERS, 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
			this.discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		// Never the ship its shooter sits in (Phase 11).
		// Starship lasers spare other players if the server says so.
		Entity owner = this.getOwner();

		if (this.type == BlasterType.XWING && target instanceof Player
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
}
