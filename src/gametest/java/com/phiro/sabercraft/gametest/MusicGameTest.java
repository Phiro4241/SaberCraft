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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
