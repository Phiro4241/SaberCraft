package com.phiro.sabercraft.client.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;

/**
 * Your own music for SaberCraft: Ogg Vorbis files in {@code config/sabercraft/music/<mood>/}
 * ({@code exploration}, {@code night}, {@code combat}, {@code event}) join the built-in pieces of
 * that mood. They are read only from that folder on this computer, never sent anywhere, and only
 * you hear them. Only {@code .ogg} files up to 64 MB are taken, at most 64 per mood; the folder is
 * read again whenever a world is joined.
 */
public final class UserMusic {
	private static final long MAX_BYTES = 64L * 1024L * 1024L;
	private static final int MAX_PER_MOOD = 64;
	private static final String PREFIX = "user_music/";
	private static final Map<Identifier, Path> FILES = new HashMap<>();
	private static final Map<SaberMusic.Mood, List<Identifier>> BY_MOOD = new EnumMap<>(SaberMusic.Mood.class);

	private UserMusic() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(SaberCraft.MOD_ID).resolve("music");
	}

	/** Reads the folders again; creates them, with a short note, the first time. */
	public static synchronized void scan() {
		FILES.clear();
		BY_MOOD.clear();
		Path root = folder();

		for (SaberMusic.Mood mood : SaberMusic.Mood.values()) {
			Path dir = root.resolve(mood.folder());
			List<Identifier> ids = new ArrayList<>();

			try {
				Files.createDirectories(dir);

				try (Stream<Path> files = Files.list(dir)) {
					for (Path file : files.sorted().toList()) {
						if (ids.size() >= MAX_PER_MOOD) {
							break;
						} else if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg") && Files.isRegularFile(file)
								&& Files.size(file) <= MAX_BYTES) {
							Identifier id = SaberCraft.id(PREFIX + mood.folder() + "/" + ids.size());
							FILES.put(id, file);
							ids.add(id);
						}
					}
				}
			} catch (IOException error) {
				SaberCraft.LOGGER.warn("Could not read the music folder {}: {}", dir, error.toString());
			}

			BY_MOOD.put(mood, List.copyOf(ids));
		}

		Path readme = root.resolve("README.txt");
		if (!Files.exists(readme)) {
			try {
				Files.writeString(readme, """
						SaberCraft music: put your own Ogg Vorbis (.ogg) files into the folders
						exploration, night, combat and event. They join the built-in pieces of that mood
						and are only played on this computer. Use only music you are allowed to use.
						Turn them off in the SaberCraft menu (V) under Music.
						""");
			} catch (IOException ignored) {
				// Only a hint for the player.
			}
		}

		int total = FILES.size();
		if (total > 0) {
			SaberCraft.LOGGER.info("Found {} music file(s) of your own in {}", total, root);
		}
	}

	public static synchronized List<Identifier> pieces(SaberMusic.Mood mood) {
		return BY_MOOD.getOrDefault(mood, List.of());
	}

	/** The file of one of your own pieces, or null for a built-in one. */
	public static synchronized @Nullable Path file(Identifier id) {
		return id.getNamespace().equals(SaberCraft.MOD_ID) && id.getPath().startsWith(PREFIX) ? FILES.get(id) : null;
	}

	/**
	 * The file behind the sound path the sound engine asks for ({@code sounds/user_music/...ogg}),
	 * or null when the path is not one of yours.
	 */
	public static @Nullable Path fileForSoundPath(Identifier location) {
		String path = location.getPath();

		if (!location.getNamespace().equals(SaberCraft.MOD_ID) || !path.startsWith("sounds/" + PREFIX) || !path.endsWith(".ogg")) {
			return null;
		}

		return file(SaberCraft.id(path.substring("sounds/".length(), path.length() - ".ogg".length())));
	}
}
