package com.phiro.sabercraft.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import com.phiro.sabercraft.SaberCraft;

/**
 * Reads a small settings object from {@code config/sabercraft/<name>.json}.
 *
 * <p>Missing keys keep their built-in value, and the file is written back afterwards, so it always
 * lists every setting with its current value. A broken file is reported in the log and the
 * built-in values are used, without overwriting the file.
 */
public final class JsonConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private JsonConfig() {
	}

	public static Path path(String name) {
		return FabricLoader.getInstance().getConfigDir().resolve(SaberCraft.MOD_ID).resolve(name + ".json");
	}

	public static <T> T load(String name, Class<T> type, Supplier<T> defaults) {
		Path path = path(name);
		T config = defaults.get();
		boolean write = true;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				T read = GSON.fromJson(reader, type);

				if (read != null) {
					config = read;
				}
			} catch (IOException | JsonParseException e) {
				SaberCraft.LOGGER.error("Could not read {}, using the built-in settings", path, e);
				write = false;
			}
		}

		if (write) {
			try {
				Files.createDirectories(path.getParent());

				try (Writer writer = Files.newBufferedWriter(path)) {
					GSON.toJson(config, writer);
				}
			} catch (IOException e) {
				SaberCraft.LOGGER.warn("Could not write {}", path, e);
			}
		}

		return config;
	}
}
