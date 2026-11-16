package com.phiro.sabercraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.droid.DroidMarker;

/**
 * Lets the saber stance block hits like a shield. Vanilla already handles blocked damage in
 * {@code hurtServer} (no hurt animation for a full block, reduced knockback), so the stance only
 * adds its share to the damage that items block.
 *
 * <p>Phase 10: the hilt of a saber changes the damage of its melee hits a little, see
 * {@code HiltTraits}.
 *
 * <p>Phase 11: droids are pigs with a marked head item; when it arrives they take their size.
 *
 * <p>Phase 20: droids and players no longer push each other (on both sides): in a crawl way one
 * crawls past a droid instead of shoving it ahead, and a droid never nudges its owner aside.
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

	/** Phase 11: a pig that became a droid (its head item arrived) takes the droid's size. */
	@Inject(method = "setItemSlot", at = @At("TAIL"))
	private void sabercraft$droidResize(EquipmentSlot slot, ItemStack stack, CallbackInfo info) {
		if (slot == EquipmentSlot.HEAD && (Object) this instanceof Pig pig) {
			pig.refreshDimensions();
		}
	}

	/** Phase 20: nobody pushes a droid. */
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void sabercraft$droidStays(CallbackInfoReturnable<Boolean> info) {
		if ((Object) this instanceof Pig pig && DroidMarker.of(pig) != null) {
			info.setReturnValue(false);
		}
	}

	/** Phase 20: a droid pushes nobody. */
	@Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
	private void sabercraft$droidPushesNobody(CallbackInfo info) {
		if ((Object) this instanceof Pig pig && DroidMarker.of(pig) != null) {
			info.cancel();
		}
	}
}
