package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelPart;

import com.phiro.sabercraft.client.character.OutfitClient;

/**
 * Which parts of the second skin layer a player shows. The game's skin options (hat, jacket,
 * sleeves, trouser legs, cape) are made for the own skin. With a character or outfit the second
 * layer belongs to that skin, so all of it is shown; where the character has none, nothing is
 * there to show. The cape is the own skin's, so it is never shown on a character. Back on the own
 * skin, the own options count again. Only on the client, for the players it draws.
 */
@Mixin(Avatar.class)
public abstract class AvatarMixin {
	@Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
	private void sabercraft$characterLayers(PlayerModelPart part, CallbackInfoReturnable<Boolean> info) {
		if ((Object) this instanceof AbstractClientPlayer player && OutfitClient.look(player.getUUID()) != null) {
			info.setReturnValue(part != PlayerModelPart.CAPE);
		}
	}
}
