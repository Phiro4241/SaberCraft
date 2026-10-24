package com.phiro.sabercraft.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.music.SaberMusic;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.music.StarWarsMusic;
import com.phiro.sabercraft.client.music.UserMusic;

/**
 * Checks the optional music in a real client (the test client writes its sound into
 * {@code build/run/clientGameTest/game-audio.wav}): a calm piece by day instead of the vanilla
 * music, a crossfade into combat music with never more than two pieces at once, night music, a
 * file of the player's own, and silence again when the option is turned off.
 */
public class MusicGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Identifier own = SaberCraft.id("user_music/combat/0");
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			// The test client starts with the music slider at 0; turn it up so the music is heard.
			client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(1.0);
			ImmersionConfig config = ImmersionConfig.get();
			config.music = true;
			config.musicVolume = 1.0F;
			config.musicUserFiles = true;
			config.musicCrossfadeSeconds = 2.0F;
			// A file of "your own": any Ogg file will do, here one of the mod's sounds.
			Path target = UserMusic.folder().resolve("combat").resolve("my_duel.ogg");
			try (InputStream in = SaberCraft.class.getResourceAsStream("/assets/sabercraft/sounds/combat/force_grip.ogg")) {
				Files.createDirectories(target.getParent());
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException error) {
				throw new UncheckedIOException(error);
			}

			// Star Wars mode files (Phase 8): an Ogg for exploration and as Vader's theme, a WAV for
			// dramatic fights. The calm and boss folders stay empty, to be listed as missing.
			Path sw = StarWarsMusic.folder();
			try {
				for (String name : new String[] {"exploration/test_explore.ogg", "characters/vader.ogg"}) {
					// A long piece, so it still plays when the test looks.
					Path file = sw.resolve(name);
					Files.createDirectories(file.getParent());
					try (InputStream in = SaberCraft.class.getResourceAsStream("/assets/sabercraft/sounds/music/night_stars.ogg")) {
						Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				writeWav(sw.resolve("dramatic").resolve("test_drama.wav"));
				Files.createDirectories(sw.resolve("calm"));
				Files.writeString(sw.resolve("calm").resolve("not_supported.mp3"), "x");
			} catch (IOException error) {
				throw new UncheckedIOException(error);
			}
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("tp @a 0 ~ 0");
			context.waitTicks(120);

			check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.EXPLORATION), "by day the mood is exploration");
			check(context.computeOnClient(client -> SaberMusic.currentPiece() != null && SaberMusic.playing(client) == 1),
					"one calm piece plays: " + context.computeOnClient(client -> SaberMusic.currentPiece()));
			check(context.computeOnClient(client -> client.getMusicManager().getCurrentMusicTranslationKey() == null),
					"the vanilla music is held back");
			check(context.computeOnClient(client -> UserMusic.pieces(SaberMusic.Mood.COMBAT).contains(own)), "your own file is found");

			// A fight: crossfade into the combat music.
			context.runOnClient(SaberMusic::markCombatForTests);
			int most = 0;
			for (int i = 0; i < 30; i++) {
				context.waitTicks(1);
				most = Math.max(most, context.computeOnClient(client -> SaberMusic.playing(client)));
			}
			check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.COMBAT), "a fight brings the combat mood");
			check(most <= 2, "never more than two pieces at once: " + most);
			check(most == 2, "the pieces crossfade: " + most);
			context.waitTicks(40);
			check(context.computeOnClient(client -> SaberMusic.playing(client) == 1), "after the crossfade one piece plays");
			String combat = context.computeOnClient(client -> String.valueOf(SaberMusic.currentPiece()));
			check(combat.contains("combat"), "a combat piece plays: " + combat);

			// Night after the fight is over.
			context.runOnClient(client -> SaberMusic.reset());
			server.runCommand("time set midnight");
			context.waitTicks(80);
			check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.NIGHT), "at night the mood is night");

			// Your own file.
			context.runOnClient(client -> {
				SaberMusic.pickForTests(own);
				SaberMusic.markCombatForTests(client);
			});
			context.waitTicks(60);
			check(context.computeOnClient(client -> own.equals(SaberMusic.currentPiece()) && SaberMusic.playing(client) >= 1),
					"your own file plays: " + context.computeOnClient(client -> SaberMusic.currentPiece()));
			context.runOnClient(client -> SaberMusic.pickForTests(null));

			this.starWars(context, server);

			// Off: everything fades out and vanilla may play again.
			context.runOnClient(client -> ImmersionConfig.get().music = false);
			context.waitTicks(60);
			check(context.computeOnClient(client -> SaberMusic.playing(client) == 0), "with the music off nothing of it plays");
			check(!context.computeOnClient(client -> SaberMusic.replacesVanilla()), "with the music off vanilla music may play");
			context.runOnClient(client -> {
				ImmersionConfig.get().music = true;
				client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
			});
		}
	}

	/** The Star Wars mode: its own files per situation, WAV playback, a character theme, and both modes kept apart. */
	private void starWars(ClientGameTestContext context, TestServerContext server) {
		float sabercraftVolume = context.computeOnClient(client -> ImmersionConfig.get().musicVolume);
		context.runOnClient(client -> {
			SaberMusic.reset();
			ImmersionConfig config = ImmersionConfig.get();
			config.musicMode = ImmersionConfig.MODE_STARWARS;
			config.starWarsVolume = 1.0F;
			StarWarsMusic.scan();
		});
		server.runCommand("time set day");
		context.waitTicks(80);
		check(context.computeOnClient(client -> SaberMusic.playing(client) == 0), "in the Star Wars mode no SaberCraft piece plays");
		check(context.computeOnClient(client -> StarWarsMusic.tracks(StarWarsMusic.Situation.EXPLORATION).size() == 1), "the exploration file is found");
		check(context.computeOnClient(client -> StarWarsMusic.tracks(StarWarsMusic.Situation.CALM).isEmpty()), "the calm folder is listed as empty");
		check(context.computeOnClient(client -> StarWarsMusic.problems().stream().anyMatch(p -> p.contains("not_supported.mp3"))),
				"an MP3 file is reported as not supported: " + context.computeOnClient(client -> StarWarsMusic.problems()));
		check(context.computeOnClient(client -> StarWarsMusic.situation() == StarWarsMusic.Situation.EXPLORATION), "by day: exploration");
		check(context.computeOnClient(client -> StarWarsMusic.currentTrack().map(t -> t.name().equals("exploration/test_explore.ogg")).orElse(false)
				&& StarWarsMusic.playing(client) >= 1), "your exploration track plays: " + context.computeOnClient(client -> StarWarsMusic.currentTrack()));
		check(context.computeOnClient(client -> client.getMusicManager().getCurrentMusicTranslationKey() == null), "the vanilla music is held back");
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.MUSIC));
		context.waitTicks(3);
		context.takeScreenshot("menu_music_starwars");
		context.runOnClient(client -> client.gui.setScreen(null));

		// A character appears: you pick Vader, his theme plays.
		context.runOnClient(client -> OutfitClient.choose(Outfit.VADER.id().toString()));
		context.waitTicks(30);
		check(context.computeOnClient(client -> StarWarsMusic.situation() == StarWarsMusic.Situation.CHARACTER
				&& StarWarsMusic.currentTrack().map(t -> t.name().equals("characters/vader.ogg")).orElse(false)),
				"picking Vader plays his theme: " + context.computeOnClient(client -> StarWarsMusic.currentTrack()));
		context.runOnClient(client -> OutfitClient.choose(""));

		// A fight without a lit blade: the dramatic WAV file.
		context.runOnClient(SaberMusic::markCombatForTests);
		context.waitTicks(40);
		check(context.computeOnClient(client -> StarWarsMusic.situation() == StarWarsMusic.Situation.DRAMATIC), "a fight: dramatic");
		check(context.computeOnClient(client -> StarWarsMusic.currentTrack().map(t -> t.name().endsWith(".wav")).orElse(false)
				&& StarWarsMusic.playing(client) >= 1), "a WAV file plays: " + context.computeOnClient(client -> StarWarsMusic.currentTrack()));

		// The mode is saved and survives a restart; the SaberCraft settings are untouched.
		context.runOnClient(client -> {
			ImmersionConfig.save();
			ImmersionConfig.load();
		});
		check(context.computeOnClient(client -> ImmersionConfig.get().starWarsMode()), "the music mode is kept after loading the settings again");
		check(context.computeOnClient(client -> ImmersionConfig.get().musicVolume) == sabercraftVolume, "the SaberCraft volume is untouched");

		// Back to the SaberCraft mode: the Star Wars tracks fade out.
		context.runOnClient(client -> {
			ImmersionConfig.get().musicMode = ImmersionConfig.MODE_SABERCRAFT;
			SaberMusic.reset();
		});
		context.waitTicks(60);
		check(context.computeOnClient(client -> StarWarsMusic.playing(client) == 0), "in the SaberCraft mode no Star Wars track plays");
		check(context.computeOnClient(client -> SaberMusic.currentPiece() != null), "the SaberCraft music is back");
	}

	/** Two seconds of a soft chord, 16 bit stereo, as a WAV file. */
	private static void writeWav(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		float rate = 22050.0F;
		int frames = (int) (rate * 2);
		byte[] data = new byte[frames * 4];

		for (int i = 0; i < frames; i++) {
			double t = i / rate;
			short value = (short) (6000 * (Math.sin(2 * Math.PI * 220 * t) + Math.sin(2 * Math.PI * 330 * t)) / 2);
			for (int channel = 0; channel < 2; channel++) {
				data[i * 4 + channel * 2] = (byte) value;
				data[i * 4 + channel * 2 + 1] = (byte) (value >> 8);
			}
		}

		javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(rate, 16, 2, true, false);
		try (javax.sound.sampled.AudioInputStream stream = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(data), format, frames)) {
			javax.sound.sampled.AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, file.toFile());
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
