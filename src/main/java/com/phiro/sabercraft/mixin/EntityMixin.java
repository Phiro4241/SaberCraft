package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import com.phiro.sabercraft.interior.InteriorSteps;
import com.phiro.sabercraft.interior.Seats;

/**
 * Phase 18: someone sitting on one of the ships' seats is never "in a wall": the seat holds them
 * where the drawn chair or couch is, and the furniture's collision round it must not suffocate
 * them (the couch's old cap at shoulder height did). Phase 26: aboard, steps sound like a deck
 * plate (and crawling in a vent like sheet metal), not like stone (see InteriorSteps).
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

	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void sabercraft$deckSteps(BlockPos pos, BlockState state, CallbackInfo info) {
		if (InteriorSteps.step((Entity) (Object) this, state)) {
			info.cancel();
		}
	}
}
