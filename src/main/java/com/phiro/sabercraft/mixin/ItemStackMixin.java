package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.phiro.sabercraft.interior.ShipInteriors;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Phase 14: nothing can be placed or used on the blocks of a ship interior (blocks, buckets, flint
 * and steel, bone meal...), except in creative mode. Levers, doors and chests still work: those
 * are used by the block, not by the item.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void sabercraft$protectInterior(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (ShipInteriors.protectedFor(context.getLevel(), context.getPlayer())) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
