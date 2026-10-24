package com.phiro.sabercraft.client.blade;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.SaberCraft;

/**
 * Reads the per-color blade settings from {@code config/sabercraft/blade_styles.json}.
 *
 * <p>On the first start the file is written with the built-in values of every color. Each color
 * can then be tuned on its own: core, bloom and halo color, length, thickness, glow radius, bloom
 * and halo strength and shimmer. Missing entries keep their default, invalid ones are reported in
 * the log and ignored, and values are clamped to a sensible range. Example:
 *
 * <pre>{@code
 * "sabercraft:blue": {
 *   "core_color": "#F2F7FF",
 *   "bloom_color": "#8CB8FF",
 *   "glow_color": "#1E5CFF",
 *   "length": 1.2,
 *   "core_radius": 0.022,
 *   "glow_radius": 0.12,
 *   "bloom_strength": 1.0,
 *   "glow_strength": 1.0,
 *   "shimmer": 0.04
 * }
 * }</pre>
 *
 * <p>The file is read once at startup. The config screen of a later phase will write the same file.
 */
public final class BladeStyleConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private BladeStyleConfig() {
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(SaberCraft.MOD_ID).resolve("blade_styles.json");
	}

	public static void load() {
		Path path = path();
		boolean complete = false;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				complete = apply(JsonParser.parseReader(reader).getAsJsonObject());
			} catch (IOException | JsonParseException | IllegalStateException e) {
				SaberCraft.LOGGER.error("Could not read {}, using the built-in blade colors", path, e);
				return;
			}
		}

		if (!complete) {
			// First start, or a color was added in a newer version: write every color, keeping the
			// values that were already configured.
			save(path);
		}
	}

	/** Applies all entries of the file. Returns true if every known color had an entry. */
	static boolean apply(JsonObject root) {
		boolean complete = true;

		for (Identifier id : BladeStyles.ids()) {
			JsonElement entry = root.get(id.toString());

			if (entry == null || !entry.isJsonObject()) {
				complete = false;
				continue;
			}

			BladeStyles.configure(read(BladeStyles.defaultStyle(id), entry.getAsJsonObject()));
		}

		return complete;
	}

	static BladeStyle read(BladeStyle base, JsonObject json) {
		return new BladeStyle(
				base.id(),
				color(json, "core_color", base.coreColor(), base),
				color(json, "bloom_color", base.bloomColor(), base),
				color(json, "glow_color", base.glowColor(), base),
				number(json, "length", base.length(), 0.1F, 4.0F, base),
				number(json, "core_radius", base.coreRadius(), 0.002F, 0.2F, base),
				number(json, "glow_radius", base.glowRadius(), 0.01F, 0.5F, base),
				number(json, "bloom_strength", base.bloomStrength(), 0.0F, 3.0F, base),
				number(json, "glow_strength", base.glowStrength(), 0.0F, 3.0F, base),
				number(json, "shimmer", base.shimmer(), 0.0F, 0.5F, base)
		);
	}

	private static int color(JsonObject json, String key, int fallback, BladeStyle style) {
		if (!json.has(key)) {
			return fallback;
		}

		try {
			String value = json.get(key).getAsString().trim();
			String hex = value.startsWith("#") ? value.substring(1) : value;

			if (hex.length() != 6) {
				throw new NumberFormatException(value);
			}

			return Integer.parseInt(hex, 16);
		} catch (RuntimeException e) {
			SaberCraft.LOGGER.warn("Blade style {}: '{}' must be a color like \"#FF2010\", keeping the default", style.id(), key);
			return fallback;
		}
	}

	private static float number(JsonObject json, String key, float fallback, float min, float max, BladeStyle style) {
		if (!json.has(key)) {
			return fallback;
		}

		try {
			return Mth.clamp(json.get(key).getAsFloat(), min, max);
		} catch (RuntimeException e) {
			SaberCraft.LOGGER.warn("Blade style {}: '{}' must be a number, keeping the default", style.id(), key);
			return fallback;
		}
	}

	private static void save(Path path) {
		JsonObject root = new JsonObject();

		for (Map.Entry<Identifier, BladeStyle> entry : BladeStyles.all().entrySet()) {
			root.add(entry.getKey().toString(), write(entry.getValue()));
		}

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			SaberCraft.LOGGER.error("Could not write {}", path, e);
		}
	}

	static JsonObject write(BladeStyle style) {
		JsonObject json = new JsonObject();
		json.addProperty("core_color", hex(style.coreColor()));
		json.addProperty("bloom_color", hex(style.bloomColor()));
		json.addProperty("glow_color", hex(style.glowColor()));
		json.addProperty("length", style.length());
		json.addProperty("core_radius", style.coreRadius());
		json.addProperty("glow_radius", style.glowRadius());
		json.addProperty("bloom_strength", style.bloomStrength());
		json.addProperty("glow_strength", style.glowStrength());
		json.addProperty("shimmer", style.shimmer());
		return json;
	}

	private static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}
}
