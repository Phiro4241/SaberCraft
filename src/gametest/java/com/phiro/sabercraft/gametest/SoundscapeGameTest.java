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

import net.minecraft.sounds.SoundSource;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.music.PlanetAmbience;
import com.phiro.sabercraft.client.music.SaberMusic;
import com.phiro.sabercraft.client.music.StarWarsMusic;

/**
 * Phase 10: the music for situations and the sound of the planets, in a real client.
 *
 * <ul>
 *   <li>SaberCraft mode: after a won fight the victory fanfare plays once, when you fall the
 *       lament, and afterwards the music goes back to what is going on; with
 *       {@code music_stingers} off neither plays.</li>
 *   <li>Star Wars mode: the new {@code defeat/} folder plays when you fall.</li>
 *   <li>Planet sounds: the chosen planet's ambience plays outdoors, fades to a quarter under a
 *       roof, a new planet fades over the old one, and the option and the volume work.</li>
 * </ul>
 *
 * <p>The sound itself goes to {@code build/run/clientGameTest/game-audio.wav}; screenshots of the
 * menus: {@code build/run/clientGameTest/screenshots/soundscape_*}.
 */
public class SoundscapeGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(1.0);
			client.options.getSoundSourceOptionInstance(SoundSource.AMBIENT).set(1.0);
			ImmersionConfig config = ImmersionConfig.get();
			config.music = true;
			config.musicMode = ImmersionConfig.MODE_SABERCRAFT;
			config.musicStingers = true;
			config.musicCrossfadeSeconds = 1.0F;
			config.planet = "";
			config.planetAmbient = true;
			config.planetAmbientVolume = 0.8F;
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule immediate_respawn true");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("time set day");
			server.runCommand("tp @a 0 ~ 0");
			context.waitTicks(120);

			this.stingers(context, server);
			this.starWarsDefeat(context, server);
			this.ambience(context, server);
		}
	}

	private void stingers(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.EXPLORATION), "by day: exploration");

		// A won fight: the fanfare, once, then back to exploring.
		context.runOnClient(SaberMusic::markCombatForTests);
		context.waitTicks(40);
		check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.COMBAT), "a fight: combat music");
		context.runOnClient(client -> SaberMusic.endCombatForTests());
		context.waitTicks(30);
		check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.VICTORY
				&& SaberCraft.id("music.victory_fanfare").equals(SaberMusic.currentPiece())), "after the fight the victory fanfare plays: "
				+ context.computeOnClient(client -> SaberMusic.mood() + " " + SaberMusic.currentPiece()));
		context.takeScreenshot("soundscape_victory");
		// The fanfare is 13 seconds long.
		context.waitTicks(320);
		check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.EXPLORATION), "after the fanfare the music goes back to exploring: "
				+ context.computeOnClient(client -> SaberMusic.mood()));

		// You fall: the lament, also over the respawn.
		server.runCommand("kill @a");
		context.waitTicks(10);
		check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.DEFEAT
				&& SaberCraft.id("music.defeat_lament").equals(SaberMusic.currentPiece())), "when you fall the lament plays: "
				+ context.computeOnClient(client -> SaberMusic.mood() + " " + SaberMusic.currentPiece()));
		context.waitTicks(40);
		check(context.computeOnClient(client -> client.player.isAlive()), "you are back after the respawn");
		check(context.computeOnClient(client -> SaberMusic.mood() == SaberMusic.Mood.DEFEAT), "the lament goes on after the respawn");

		// Switched off: a won fight plays no fanfare.
		context.runOnClient(client -> {
			SaberMusic.reset();
			ImmersionConfig.get().musicStingers = false;
		});
		context.waitTicks(40);
		context.runOnClient(SaberMusic::markCombatForTests);
		context.waitTicks(40);
		context.runOnClient(client -> SaberMusic.endCombatForTests());
		context.waitTicks(30);
		check(context.computeOnClient(client -> SaberMusic.mood() != SaberMusic.Mood.VICTORY), "with the option off no fanfare");
		context.runOnClient(client -> {
			ImmersionConfig.get().musicStingers = true;
			SaberMenuScreen.open(client, SaberMenuScreen.Tab.MUSIC);
		});
		context.waitTicks(3);
		context.takeScreenshot("soundscape_menu_music");
		context.runOnClient(client -> client.gui.setScreen(null));
	}

	private void starWarsDefeat(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(client -> {
			Path file = StarWarsMusic.folder().resolve("defeat").resolve("test_defeat.ogg");

			try (InputStream in = SaberCraft.class.getResourceAsStream("/assets/sabercraft/sounds/music/night_stars.ogg")) {
				Files.createDirectories(file.getParent());
				Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException error) {
				throw new UncheckedIOException(error);
			}

			SaberMusic.reset();
			ImmersionConfig.get().musicMode = ImmersionConfig.MODE_STARWARS;
			StarWarsMusic.scan();
		});
		context.waitTicks(60);
		check(context.computeOnClient(client -> StarWarsMusic.tracks(StarWarsMusic.Situation.DEFEAT).size() == 1), "the defeat folder is read");
		server.runCommand("kill @a");
		context.waitTicks(10);
		check(context.computeOnClient(client -> StarWarsMusic.situation() == StarWarsMusic.Situation.DEFEAT
				&& StarWarsMusic.currentTrack().map(t -> t.name().equals("defeat/test_defeat.ogg")).orElse(false)),
				"Star Wars mode: when you fall your defeat piece plays: " + context.computeOnClient(client -> StarWarsMusic.situation() + " " + StarWarsMusic.currentTrack()));
		context.waitTicks(40);
		context.runOnClient(client -> {
			SaberMusic.reset();
			ImmersionConfig.get().musicMode = ImmersionConfig.MODE_SABERCRAFT;

			try {
				Files.deleteIfExists(StarWarsMusic.folder().resolve("defeat").resolve("test_defeat.ogg"));
			} catch (IOException error) {
				throw new UncheckedIOException(error);
			}

			StarWarsMusic.scan();
		});
		context.waitTicks(20);
	}

	private void ambience(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> PlanetAmbience.playing()) == null, "without a planet no planet sound");
		server.runCommand("tp @a 0 ~ 0");
		context.runOnClient(client -> ImmersionConfig.get().planet = "hoth");
		context.waitTicks(80);
		check("hoth".equals(context.computeOnClient(client -> PlanetAmbience.playing())), "on Hoth the blizzard plays");
		check(context.computeOnClient(client -> PlanetAmbience.volume()) > 0.7F, "outdoors at full volume: " + context.computeOnClient(client -> PlanetAmbience.volume()));

		// Under a roof it gets quieter.
		server.runCommand("execute at @p run fill ~-3 ~2 ~-3 ~3 ~3 ~3 minecraft:stone");
		context.waitTicks(80);
		float indoors = context.computeOnClient(client -> PlanetAmbience.volume());
		check(indoors > 0.1F && indoors < 0.3F, "under a roof a quarter: " + indoors);
		server.runCommand("execute at @p run fill ~-3 ~2 ~-3 ~3 ~3 ~3 minecraft:air");
		context.waitTicks(80);
		check(context.computeOnClient(client -> PlanetAmbience.volume()) > 0.7F, "outdoors again: full volume");

		// Another planet fades over the old one.
		context.runOnClient(client -> ImmersionConfig.get().planet = "mustafar");
		context.waitTicks(5);
		check("mustafar".equals(context.computeOnClient(client -> PlanetAmbience.playing())), "on Mustafar the lava plays");
		check(context.computeOnClient(client -> PlanetAmbience.volume()) < 0.5F, "the new planet sound fades in");
		context.waitTicks(80);

		for (String planet : StarWarsMusic.PLANETS) {
			context.runOnClient(client -> ImmersionConfig.get().planet = planet);
			context.waitTicks(20);
			check(planet.equals(context.computeOnClient(client -> PlanetAmbience.playing())), planet + " has its own sound");
		}

		// The volume setting and the switch.
		context.runOnClient(client -> ImmersionConfig.get().planetAmbientVolume = 0.2F);
		// Long enough for the last planet's sound to have faded in.
		context.waitTicks(80);
		check(Math.abs(context.computeOnClient(client -> PlanetAmbience.volume()) - 0.2F) < 0.02F, "the volume setting applies");
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SPACE));
		context.waitTicks(3);
		context.takeScreenshot("soundscape_menu_sky");
		context.runOnClient(client -> {
			client.gui.setScreen(null);
			ImmersionConfig.get().planetAmbient = false;
		});
		context.waitTicks(80);
		check(context.computeOnClient(client -> PlanetAmbience.playing()) == null, "switched off: no planet sound");
		context.runOnClient(client -> {
			ImmersionConfig config = ImmersionConfig.get();
			config.planetAmbient = true;
			config.planetAmbientVolume = 0.5F;
			config.planet = "";
		});
		context.waitTicks(80);
		check(context.computeOnClient(client -> PlanetAmbience.playing()) == null, "without a planet the sound stops");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
