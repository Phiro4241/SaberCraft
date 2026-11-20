package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import com.phiro.sabercraft.interior.Vents;

/**
 * Phase 18: crouching in front of the opening of a ship's crawl way puts a player down on all
 * fours (the game's crawling pose), so they fit into the way one block high. On both sides.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void sabercraft$crawlIntoVent(CallbackInfo info) {
		Player player = (Player) (Object) this;
		// Phase 27: a little faster on all fours in a crawl way (the server sets it, the client gets it).
		Vents.crawlSpeed(player);

		if (Vents.crawl(player)) {
			player.setPose(Pose.SWIMMING);
			info.cancel();
		}
	}
}
