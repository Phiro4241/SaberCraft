package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import com.phiro.sabercraft.client.character.OutfitClient;

/**
 * The skin layer fix of Phase 10: a player who chose a character or outfit has that skin, and only
 * that one. Everything in the game that draws a player asks for this skin: the model shape (which
 * renderer), the texture of the body, of the first-person arm and of the preview in the menus,
 * the cape and the elytra. So no texture or model data of the own skin can be left over, and
 * choosing the own skin again simply gives the game's skin back. The tab list keeps the real face.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void sabercraft$characterSkin(CallbackInfoReturnable<PlayerSkin> info) {
		OutfitClient.Look look = OutfitClient.look(((AbstractClientPlayer) (Object) this).getUUID());

		if (look != null) {
			info.setReturnValue(look.skin());
		}
	}
}
