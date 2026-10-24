package com.phiro.sabercraft.client.music;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.character.CharacterConfig;
import com.phiro.sabercraft.client.character.OutfitClient;

/**
 * The Star Wars music mode (Phase 8). SaberCraft ships no film music: this mode plays only music
 * files the player puts on their own computer, in {@code config/sabercraft/starwars_music/}. Each
 * situation of the game has a folder, and {@code mapping.json} can pick files for a situation by
 * hand:
 *
 * <ul>
 *   <li>{@code exploration}: exploring by day,</li>
 *   <li>{@code calm}: calm and emotional moments, at night and underground,</li>
 *   <li>{@code duel}: lightsaber duels (a fight with the blade lit),</li>
 *   <li>{@code dramatic}: dramatic encounters (a fight without a lit blade),</li>
 *   <li>{@code boss}: boss fights,</li>
 *   <li>{@code characters}: the theme of a film character when they appear, one file per
 *       character ({@code vader.ogg}, {@code obi_wan.ogg}, ...).</li>
 * </ul>
 *
 * <p>Ogg Vorbis ({@code .ogg}) and uncompressed WAV ({@code .wav}) files are played; MP3 and other
 * formats are listed as not supported, so the menu can say so. The files are read only from that
 * folder, never sent anywhere, and only this player hears them. The mode has its own volume,
 * crossfade and switches, and never changes the settings of the SaberCraft music.
 */
public final class StarWarsMusic {
	private static final long MAX_BYTES = 128L * 1024L * 1024L;
	private static final int MAX_PER_SITUATION = 64;
	private static final String PREFIX = "starwars_music/";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** A character theme is played again for the same character at most every five minutes. */
	private static final int THEME_COOLDOWN = 6000;
	private static final int MOOD_SETTLE = 60;

	/** The situations, also the names of the folders. */
	public enum Situation {
		EXPLORATION("exploration", false, false),
		CALM("calm", false, false),
		DUEL("duel", true, true),
		DRAMATIC("dramatic", true, true),
		BOSS("boss", true, true),
		CHARACTER("characters", false, false);

		private final String folder;
		private final boolean loops;
		private final boolean combat;

		Situation(String folder, boolean loops, boolean combat) {
			this.folder = folder;
			this.loops = loops;
			this.combat = combat;
		}

		public String folder() {
			return this.folder;
		}

		public boolean combat() {
			return this.combat;
		}
	}

	/** One music file the player provided. */
	public record Track(Identifier id, Path file, String name) {
	}

	private static final Map<Identifier, Path> FILES = new HashMap<>();
	private static final Map<Situation, List<Track>> TRACKS = new EnumMap<>(Situation.class);
	private static final Map<String, List<Track>> THEMES = new LinkedHashMap<>();
	private static final List<String> PROBLEMS = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static final List<MusicPiece> FADING = new ArrayList<>();
	private static final Map<String, Long> THEME_PLAYED = new HashMap<>();
	private static final Map<UUID, String> SEEN = new HashMap<>();
	private static @Nullable MusicPiece current;
	private static @Nullable Situation situation;
	private static @Nullable Situation candidate;
	private static @Nullable String pendingTheme;
	private static @Nullable String playingTheme;
	private static int candidateTicks;
	private static int silence;
	private static boolean active;
	private static boolean scanned;
	private static String ownCharacter = "";
	private static int ticks;

	private StarWarsMusic() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(SaberCraft.MOD_ID).resolve("starwars_music");
	}

	/** Reads the folders and {@code mapping.json} again; creates both, with a README, the first time. */
	public static synchronized void scan() {
		scanned = true;
		FILES.clear();
		TRACKS.clear();
		THEMES.clear();
		PROBLEMS.clear();
		Path root = folder();

		try {
			Files.createDirectories(root);

			for (Situation each : Situation.values()) {
				Files.createDirectories(root.resolve(each.folder));
			}

			writeHelp(root);
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not create the Star Wars music folder {}: {}", root, error.toString());
		}

		JsonObject mapping = readMapping(root.resolve("mapping.json"));

		for (Situation each : Situation.values()) {
			if (each == Situation.CHARACTER) {
				continue;
			}

			List<Path> files = mapped(root, mapping, each.folder);
			TRACKS.put(each, register(each.folder, files.isEmpty() ? list(root.resolve(each.folder)) : files));
		}

		// Character themes: mapping "characters": {"vader": [...]}, else characters/<id>.ogg or a folder characters/<id>/.
		JsonObject characters = mapping.has("characters") && mapping.get("characters").isJsonObject()
				? mapping.getAsJsonObject("characters") : new JsonObject();
		List<Track> allThemes = new ArrayList<>();

		for (Outfit character : Outfit.CHARACTERS) {
			String id = character.id().getPath();
			List<Path> files = mapped(root.resolve(Situation.CHARACTER.folder), characters, id);

			if (files.isEmpty()) {
				for (String extension : new String[] {".ogg", ".wav"}) {
					Path file = root.resolve(Situation.CHARACTER.folder).resolve(id + extension);

					if (Files.isRegularFile(file)) {
						files.add(file);
					}
				}

				files.addAll(list(root.resolve(Situation.CHARACTER.folder).resolve(id)));
			}

			List<Track> tracks = register("characters/" + id, files);
			THEMES.put(id, tracks);
			allThemes.addAll(tracks);
		}

		TRACKS.put(Situation.CHARACTER, List.copyOf(allThemes));
		SaberCraft.LOGGER.info("Star Wars music: {} file(s) in {}{}", FILES.size(), root, PROBLEMS.isEmpty() ? "" : ", problems: " + PROBLEMS);
	}

	private static void writeHelp(Path root) throws IOException {
		Path readme = root.resolve("README.txt");

		if (!Files.exists(readme)) {
			Files.writeString(readme, """
					SaberCraft - Star Wars music mode
					==================================

					SaberCraft does not contain any film music. This mode plays music files that YOU put here,
					for example music you bought or ripped from your own CDs. Only use files you are allowed to use.
					They are only played on this computer and never sent to a server or to other players.

					Folders (one per situation in the game):
					  exploration/  exploring by day
					  calm/         calm and emotional moments: at night and underground
					  duel/         lightsaber duels: a fight with the blade lit
					  dramatic/     dramatic encounters: a fight without a lit blade
					  boss/         boss fights (Ender Dragon, Wither, Warden, ...)
					  characters/   the theme of a character when they appear, named after the character:
					                vader.ogg, obi_wan.ogg, maul.ogg, luke.ogg (or a folder characters/vader/)

					Formats: Ogg Vorbis (.ogg, recommended) and uncompressed WAV (.wav, 16 or 24 bit PCM).
					MP3, FLAC, M4A and others are not supported: convert them to .ogg, for example with Audacity
					(File > Export > Export as OGG) or ffmpeg:  ffmpeg -i "track.mp3" -c:a libvorbis -q:a 6 "track.ogg"
					At most 64 files per folder, each at most 128 MB.

					mapping.json lets you pick files for a situation by hand instead of using the whole folder.
					Paths are relative to this folder. An empty list means: every file in the folder of that name.
					Example:
					  "duel": ["duel/duel_of_the_fates.ogg", "boss/battle_of_the_heroes.ogg"],
					  "characters": { "vader": ["characters/imperial_march.ogg"] }

					Then switch the music mode to "Star Wars" in the SaberCraft menu (V > Music) and press
					"Reload files". The menu lists every track it found and every file that is missing.
					""");
		}

		Path mapping = root.resolve("mapping.json");

		if (!Files.exists(mapping)) {
			JsonObject json = new JsonObject();
			json.addProperty("_help", "See README.txt. Paths are relative to this folder; an empty list uses every file in the folder of the same name.");

			for (Situation each : Situation.values()) {
				if (each != Situation.CHARACTER) {
					json.add(each.folder, new JsonArray());
				}
			}

			JsonObject characters = new JsonObject();
			Outfit.CHARACTERS.forEach(character -> characters.add(character.id().getPath(), new JsonArray()));
			json.add("characters", characters);

			try (Writer writer = Files.newBufferedWriter(mapping)) {
				GSON.toJson(json, writer);
			}
		}
	}

	private static JsonObject readMapping(Path file) {
		if (!Files.isRegularFile(file)) {
			return new JsonObject();
		}

		try (Reader reader = Files.newBufferedReader(file)) {
			JsonElement json = JsonParser.parseReader(reader);
			return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
		} catch (IOException | RuntimeException error) {
			PROBLEMS.add("mapping.json: " + error.getMessage());
			SaberCraft.LOGGER.warn("Could not read {}: {}", file, error.toString());
			return new JsonObject();
		}
	}

	/** The files a mapping lists under a key, relative to {@code base}; missing ones are noted. */
	private static List<Path> mapped(Path base, JsonObject mapping, String key) {
		List<Path> files = new ArrayList<>();

		if (!mapping.has(key) || !mapping.get(key).isJsonArray()) {
			return files;
		}

		Path root = folder().normalize();

		for (JsonElement entry : mapping.getAsJsonArray(key)) {
			if (!entry.isJsonPrimitive()) {
				continue;
			}

			String name = entry.getAsString();
			// Paths in the mapping are relative to the music folder; for characters also to characters/.
			Path file = root.resolve(name).normalize();

			if (!Files.isRegularFile(file)) {
				file = base.resolve(name).normalize();
			}

			if (!file.startsWith(root)) {
				PROBLEMS.add(key + ": " + name + " (outside the folder)");
			} else if (!Files.isRegularFile(file)) {
				PROBLEMS.add(key + ": " + name + " (missing)");
			} else {
				files.add(file);
			}
		}

		return files;
	}

	private static List<Path> list(Path dir) {
		if (!Files.isDirectory(dir)) {
			return new ArrayList<>();
		}

		try (Stream<Path> files = Files.list(dir)) {
			List<Path> found = new ArrayList<>();

			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				String name = file.getFileName().toString();

				if (supported(name)) {
					found.add(file);
				} else if (!name.equalsIgnoreCase("README.txt") && !name.startsWith(".")) {
					PROBLEMS.add(folder().relativize(file) + " (format not supported, use .ogg or .wav)");
				}
			}

			return found;
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not read the music folder {}: {}", dir, error.toString());
			return new ArrayList<>();
		}
	}

	private static List<Track> register(String group, List<Path> files) {
		List<Track> tracks = new ArrayList<>();

		for (Path file : files) {
			if (tracks.size() >= MAX_PER_SITUATION) {
				break;
			}

			try {
				if (Files.size(file) > MAX_BYTES) {
					PROBLEMS.add(folder().relativize(file) + " (larger than 128 MB)");
					continue;
				}
			} catch (IOException error) {
				continue;
			}

			String extension = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav") ? "wav" : "ogg";
			Identifier id = SaberCraft.id(PREFIX + group + "/" + tracks.size() + "_" + extension);
			FILES.put(id, file);
			tracks.add(new Track(id, file, folder().relativize(file).toString().replace('\\', '/')));
		}

		return List.copyOf(tracks);
	}

	/** Whether a file name has a format this mode can play. */
	public static boolean supported(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".ogg") || lower.endsWith(".wav");
	}

	public static synchronized List<Track> tracks(Situation each) {
		ensureScanned();
		return TRACKS.getOrDefault(each, List.of());
	}

	/** The theme files of one character (by outfit path, {@code vader}). */
	public static synchronized List<Track> theme(String character) {
		ensureScanned();
		return THEMES.getOrDefault(character, List.of());
	}

	/** Files that are listed but missing, or in a format that cannot be played. */
	public static synchronized List<String> problems() {
		ensureScanned();
		return List.copyOf(PROBLEMS);
	}

	public static synchronized int total() {
		ensureScanned();
		return FILES.size();
	}

	private static void ensureScanned() {
		if (!scanned) {
			scan();
		}
	}

	/** The file of one of the tracks, or null. */
	public static synchronized @Nullable Path file(Identifier id) {
		return id.getNamespace().equals(SaberCraft.MOD_ID) && id.getPath().startsWith(PREFIX) ? FILES.get(id) : null;
	}

	/** The file behind a sound path the sound engine asks for ({@code sounds/starwars_music/...ogg}), or null. */
	public static @Nullable Path fileForSoundPath(Identifier location) {
		String path = location.getPath();

		if (!location.getNamespace().equals(SaberCraft.MOD_ID) || !path.startsWith("sounds/" + PREFIX) || !path.endsWith(".ogg")) {
			return null;
		}

		return file(SaberCraft.id(path.substring("sounds/".length(), path.length() - ".ogg".length())));
	}

	/**
	 * Runs the mode for one tick. {@code on} is false while the SaberCraft mode is chosen or the
	 * music is off; then whatever still plays fades out.
	 */
	static void tick(Minecraft client, boolean on) {
		ticks++;
		ImmersionConfig config = ImmersionConfig.get();
		float crossfade = config.starWarsCrossfade ? Math.max(20.0F, config.musicCrossfadeSeconds * 20.0F) : 6.0F;

		if (!on) {
			if (active) {
				active = false;
				fadeOutCurrent(client.level == null ? 1.0F : crossfade * 0.5F);
				situation = null;
				playingTheme = null;
			}
		} else {
			if (!active) {
				active = true;
				ensureScanned();
				client.getMusicManager().stopPlaying();
				silence = 20;
			}

			SaberMusic.observe(client);
			watchCharacters(client);
			Situation wanted = detect(client);

			if (wanted != situation) {
				candidateTicks = wanted == candidate ? candidateTicks + 1 : 0;
				candidate = wanted;

				if (situation == null || wanted == null || wanted.combat() || wanted == Situation.CHARACTER || candidateTicks >= MOOD_SETTLE) {
					switchTo(wanted, crossfade);
				}
			} else {
				candidate = null;
			}

			if (current != null && !current.isPlaying(client) && !current.fadingOut()) {
				current = null;

				if (situation == Situation.CHARACTER) {
					// The theme has been played; back to what is going on.
					playingTheme = null;
					situation = null;
					silence = 20;
				} else {
					silence = situation != null && situation.loops ? 20 : 400 + RANDOM.nextInt(800);
				}
			}

			if (current == null && situation != null && --silence <= 0) {
				start(client, situation, crossfade);
			}
		}

		float volume = config.starWarsVolume * (client.level == null ? 1.0F : client.getMusicVolume());

		if (current != null) {
			current.setMaster(volume);
		}

		for (MusicPiece piece : FADING) {
			piece.setMaster(volume);
		}

		FADING.removeIf(piece -> piece.isStopped() || !piece.isPlaying(client) && piece.age() > 40);
	}

	/** The situation now, or null when its music is switched off. */
	private static @Nullable Situation detect(Minecraft client) {
		ImmersionConfig config = ImmersionConfig.get();
		Situation found;

		if (SaberMusic.bossNow(client)) {
			found = Situation.BOSS;
		} else if (SaberMusic.fightNow(client)) {
			found = SaberMusic.saberLit(client.player) ? Situation.DUEL : Situation.DRAMATIC;
		} else if (playingTheme != null || pendingTheme != null) {
			found = Situation.CHARACTER;
		} else if (SaberMusic.darkNow(client)) {
			found = Situation.CALM;
		} else {
			found = Situation.EXPLORATION;
		}

		if (found.combat() && !config.starWarsCombat || !found.combat() && found != Situation.CHARACTER && !config.starWarsExploration) {
			return null;
		}

		return found;
	}

	/**
	 * Notices film characters appearing: your own choice when it changes, and other players
	 * with a character within 32 blocks. Their theme is queued if there is a file for it.
	 */
	private static void watchCharacters(Minecraft client) {
		if (!ImmersionConfig.get().starWarsCharacterThemes || ticks % 10 != 0) {
			return;
		}

		String own = OutfitClient.builtIn(CharacterConfig.get().outfit).filter(Outfit::character).map(o -> o.id().getPath()).orElse("");

		if (!own.equals(ownCharacter)) {
			ownCharacter = own;

			if (!own.isEmpty()) {
				queueTheme(client, own);
			}
		}

		for (Player other : client.level.players()) {
			if (other == client.player || other.distanceTo(client.player) > 32.0F) {
				continue;
			}

			Identifier outfit = OutfitClient.otherOutfit(other.getUUID());
			String character = outfit == null ? "" : Outfit.byId(outfit).filter(Outfit::character).map(o -> o.id().getPath()).orElse("");

			if (!character.equals(SEEN.put(other.getUUID(), character)) && !character.isEmpty()) {
				queueTheme(client, character);
			}
		}
	}

	private static void queueTheme(Minecraft client, String character) {
		long now = client.level.getGameTime();
		Long last = THEME_PLAYED.get(character);

		if (theme(character).isEmpty() || last != null && now - last < THEME_COOLDOWN && now >= last) {
			return;
		}

		THEME_PLAYED.put(character, now);
		pendingTheme = character;
	}

	private static void switchTo(@Nullable Situation wanted, float crossfade) {
		if (current != null) {
			current.fadeOut(crossfade);
			FADING.add(current);
			current = null;
		}

		if (situation == Situation.CHARACTER && wanted != Situation.CHARACTER) {
			playingTheme = null;
		}

		silence = 1;
		situation = wanted;
		candidate = null;
	}

	private static void start(Minecraft client, Situation wanted, float crossfade) {
		List<Track> choices;

		if (wanted == Situation.CHARACTER) {
			String character = pendingTheme != null ? pendingTheme : playingTheme;
			pendingTheme = null;
			playingTheme = character;
			choices = character == null ? List.of() : theme(character);
		} else {
			choices = tracks(wanted);
		}

		if (choices.isEmpty()) {
			if (wanted == Situation.CHARACTER) {
				playingTheme = null;
				situation = null;
			}

			// Nothing to play for this situation: stay silent, check again in ten seconds.
			silence = 200;
			return;
		}

		Track pick = choices.get(RANDOM.nextInt(choices.size()));
		MusicPiece piece = new MusicPiece(pick.id(), wanted.loops, crossfade);
		current = piece;
		client.getSoundManager().play(piece);
		SaberCraft.LOGGER.debug("Star Wars music: {} ({})", pick.name(), wanted.folder);
	}

	private static void fadeOutCurrent(float ticksToSilence) {
		if (current != null) {
			current.fadeOut(ticksToSilence);
			FADING.add(current);
			current = null;
		}
	}

	/** The track playing now, or empty. */
	public static synchronized Optional<Track> currentTrack() {
		if (current == null) {
			return Optional.empty();
		}

		Path file = FILES.get(current.getIdentifier());
		return file == null ? Optional.empty() : Optional.of(new Track(current.getIdentifier(), file, folder().relativize(file).toString().replace('\\', '/')));
	}

	public static @Nullable Situation situation() {
		return situation;
	}

	/** How many tracks of this mode are audible now. */
	public static int playing(Minecraft client) {
		int count = current != null && current.isPlaying(client) ? 1 : 0;

		for (MusicPiece piece : FADING) {
			if (!piece.isStopped() && piece.isPlaying(client)) {
				count++;
			}
		}

		return count;
	}

	/** Forgets everything when leaving a world. */
	static void reset() {
		Minecraft client = Minecraft.getInstance();

		if (current != null) {
			client.getSoundManager().stop(current);
		}

		FADING.forEach(client.getSoundManager()::stop);
		current = null;
		FADING.clear();
		situation = null;
		candidate = null;
		pendingTheme = null;
		playingTheme = null;
		active = false;
		SEEN.clear();
		ownCharacter = "";
	}
}
