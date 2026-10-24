package com.phiro.sabercraft.mixin.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.phiro.sabercraft.client.music.StarWarsMusic;
import com.phiro.sabercraft.client.music.UserMusic;
import com.phiro.sabercraft.client.music.WavAudioStream;

/**
 * Streams the player's own music files ({@link UserMusic}, {@link StarWarsMusic}) from the config
 * folder, the same way vanilla streams music from the resource packs. Every other sound is left
 * to vanilla.
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
	@Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
	private void sabercraft$streamUserMusic(Identifier location, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> info) {
		Path found = UserMusic.fileForSoundPath(location);
		Path file = found != null ? found : StarWarsMusic.fileForSoundPath(location);

		if (file != null) {
			// Ogg Vorbis like vanilla; WAV files of the Star Wars mode through Java's own decoder.
			boolean wav = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav");
			LoopingAudioStream.AudioStreamProvider decoder = wav ? WavAudioStream::new : JOrbisAudioStream::new;
			info.setReturnValue(CompletableFuture.supplyAsync(() -> {
				try {
					InputStream input = Files.newInputStream(file);
					return looping ? new LoopingAudioStream(decoder, input) : decoder.create(input);
				} catch (IOException error) {
					throw new CompletionException(error);
				}
			}, Util.nonCriticalIoPool()));
		}
	}
}
