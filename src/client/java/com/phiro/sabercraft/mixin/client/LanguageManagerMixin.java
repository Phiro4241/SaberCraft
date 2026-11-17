package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.ResourceManager;

import com.phiro.sabercraft.client.lang.SaberLanguage;

/** Phase 19: after Minecraft loaded its language, the mod's own language setting goes over it. */
@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin {
	@Inject(method = "onResourceManagerReload", at = @At("TAIL"))
	private void sabercraft$ownLanguage(ResourceManager manager, CallbackInfo info) {
		SaberLanguage.reload(manager);
	}
}
