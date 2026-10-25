package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.pig.Pig;

import com.phiro.sabercraft.droid.DroidMarker;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.ship.ShipMarker;
import com.phiro.sabercraft.ship.ShipType;

/**
 * Phase 11: a droid (a pig marked as one, see {@link DroidMarker}) has the size of the droid, on
 * the server and on clients with SaberCraft alike, so it collides and is clicked like it looks.
 * The same for starships (see {@link ShipMarker}), whose pilot sits in the cockpit.
 */
@Mixin(Pig.class)
abstract class PigMixin {
	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void sabercraft$droidSize(Pose pose, CallbackInfoReturnable<EntityDimensions> info) {
		DroidType droid = DroidMarker.of((Pig) (Object) this);

		if (droid != null) {
			info.setReturnValue(EntityDimensions.scalable(droid.width(), droid.height()).withEyeHeight(droid.height() * 0.85F));
			return;
		}

		ShipType ship = ShipMarker.of((Pig) (Object) this);

		if (ship != null) {
			// The pilot sits in the cockpit, a little in front of the middle.
			info.setReturnValue(EntityDimensions.fixed(ship.width(), ship.height()).withEyeHeight(ship.height() * 0.8F)
					.withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, 0.62F, 0.4F)));
		}
	}
}
