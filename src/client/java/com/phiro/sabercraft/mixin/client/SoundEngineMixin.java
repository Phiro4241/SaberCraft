package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;

import com.phiro.sabercraft.client.sound.SoundVolumes;

/**
 * Phase 12: SaberCraft's own volume sliders. Wherever the sound engine reads the volume of a sound
 * (when it starts, and every tick for sounds that change it, such as the hum), SaberCraft's sounds
 * are scaled by their slider, see {@link SoundVolumes}. Other sounds keep their volume.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@WrapOperation(method = {"play", "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
	private float sabercraft$ownVolume(SoundInstance instance, Operation<Float> original) {
		return original.call(instance) * SoundVolumes.scale(instance);
	}
}
