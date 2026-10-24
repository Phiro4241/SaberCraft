package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.phiro.sabercraft.ability.SaberAbilities;

/**
 * Lets a saber send projectiles back. Runs where vanilla decides between hitting an entity and
 * bouncing off it, on the server only. The deflected projectile then belongs to the player, so it
 * can hit its shooter and counts as the player's.
 */
@Mixin(Projectile.class)
abstract class ProjectileMixin {
	@Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
	private void sabercraft$deflect(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
		Projectile self = (Projectile) (Object) this;

		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer player && !self.level().isClientSide()) {
			ProjectileDeflection deflection = SaberAbilities.deflection(player, self);

			if (deflection != null) {
				self.deflect(deflection, player, EntityReference.of(player), false);
				cir.setReturnValue(deflection);
			}
		}
	}
}
