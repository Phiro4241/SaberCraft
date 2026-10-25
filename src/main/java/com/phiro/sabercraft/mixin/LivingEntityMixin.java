package com.phiro.sabercraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import com.phiro.sabercraft.ability.SaberAbilities;

/**
 * Lets the saber stance block hits like a shield. Vanilla already handles blocked damage in
 * {@code hurtServer} (no hurt animation for a full block, reduced knockback), so the stance only
 * adds its share to the damage that items block.
 *
 * <p>Phase 10: the hilt of a saber changes the damage of its melee hits a little, see
 * {@code HiltTraits}.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@ModifyReturnValue(method = "applyItemBlocking", at = @At("RETURN"))
	private float sabercraft$guardBlocks(float blocked, ServerLevel level, DamageSource source, float damage) {
		float left = damage - blocked;
		return left > 0.0F ? blocked + SaberAbilities.blockedDamage((LivingEntity) (Object) this, level, source, left) : blocked;
	}

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float sabercraft$hiltDamage(float damage, ServerLevel level, DamageSource source) {
		return SaberAbilities.hiltDamage(source, damage);
	}
}
