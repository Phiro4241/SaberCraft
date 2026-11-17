package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import com.phiro.sabercraft.interior.Seats;

/**
 * Phase 18: someone sitting on one of the ships' seats is never "in a wall": the seat holds them
 * where the drawn chair or couch is, and the furniture's collision round it must not suffocate
 * them (the couch's old cap at shoulder height did).
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
	private void sabercraft$seatedNotInWall(CallbackInfoReturnable<Boolean> info) {
		Entity vehicle = ((Entity) (Object) this).getVehicle();

		if (vehicle instanceof Display.ItemDisplay display && display.entityTags().contains(Seats.TAG)) {
			info.setReturnValue(false);
		}
	}
}
