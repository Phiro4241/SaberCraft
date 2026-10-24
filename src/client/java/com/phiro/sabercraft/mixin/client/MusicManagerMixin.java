package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sounds.MusicManager;

import com.phiro.sabercraft.client.music.SaberMusic;

/**
 * Holds the vanilla game music back while the SaberCraft music plays, so two pieces never play
 * over each other. With the SaberCraft music off, and on the title screen, vanilla runs as always.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void sabercraft$holdVanillaMusic(CallbackInfo info) {
		if (SaberMusic.replacesVanilla()) {
			info.cancel();
		}
	}
}
