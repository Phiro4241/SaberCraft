package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;

import com.phiro.sabercraft.ship.ShipEntity;

/**
 * Phase 11: a player who logs out in a starship is not saved with it. Vanilla would load the ship
 * back as a plain pig; {@link com.phiro.sabercraft.ship.Starships} puts the player back into the
 * real ship instead.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Inject(method = "saveParentVehicle", at = @At("HEAD"), cancellable = true)
	private void sabercraft$notWithShip(ValueOutput output, CallbackInfo info) {
		if (((ServerPlayer) (Object) this).getRootVehicle() instanceof ShipEntity) {
			info.cancel();
		}
	}
}
