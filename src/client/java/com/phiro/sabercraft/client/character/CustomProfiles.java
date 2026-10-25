package com.phiro.sabercraft.client.character;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Character profiles of your own (Phase 10): new characters without changing the mod. Each file
 * {@code config/sabercraft/profiles/<name>.json} describes one, for example:
 *
 * <pre>{@code
 * {
 *   "name": "Cal Kestis",
 *   "skin": "cal.png",
 *   "hilt": "sabercraft:skywalker",
 *   "color": "sabercraft:blue",
 *   "style": "sabercraft:flowing",
 *   "side": "light"
 * }
 * }</pre>
 *
 * <p>{@code skin} is a 64 x 64 skin file in the same folder; instead of it, {@code base} can name a
 * built-in character or outfit (for example {@code "sabercraft:obi_wan"}) whose look is used. The
 * saber ({@code hilt}, {@code color}, {@code style}) must be one the server knows; without it the
 * profile only changes the look. {@code side} is {@code light}, {@code dark}, {@code grey} or
 * {@code none}; {@code scale} (0.5 to 1) draws a small figure like Yoda.
 *
 * <p>Like skin files of your own, a profile stays on this computer. Others see the {@code base}
 * character when there is one, otherwise your plain skin; the saber is a normal saber variant, so
 * everybody sees it.
 */
public final class CustomProfiles {
	/** At most this many profiles are read, so a full folder cannot slow the menu down. */
	private static final int LIMIT = 32;

	/**
	 * One profile from a file.
	 *
	 * @param key   the file name without {@code .json}, stored as the choice
	 * @param name  the name shown in the menu
	 * @param skin  the skin file, or {@code null} to use {@code base} (or the plain skin)
	 * @param base  a built-in outfit whose look is used when there is no skin file
	 * @param saber the saber of the profile, or {@code null} when it has none
	 * @param side  the side of the Force shown in the menu
	 * @param scale how big the figure is drawn
	 */
	public record Profile(String key, String name, @Nullable Path skin, @Nullable Outfit base, @Nullable SaberVariant saber, Outfit.Side side,
			float scale) {
	}

	private static List<Profile> profiles = List.of();

	private CustomProfiles() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve(SaberCraft.MOD_ID).resolve("profiles");
	}

	/** Reads the folder again. Called when the character menu opens, so new files show up at once. */
	public static List<Profile> reload() {
		Path dir = folder();
		List<Profile> loaded = new ArrayList<>();

		try {
			Files.createDirectories(dir);

			try (Stream<Path> files = Files.list(dir)) {
				for (Path file : files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).sorted().limit(LIMIT).toList()) {
					read(file).ifPresent(loaded::add);
				}
			}
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not read the profile folder {}: {}", dir, error.toString());
		}

		profiles = List.copyOf(loaded);
		return profiles;
	}

	/** The profiles read last time. */
	public static List<Profile> all() {
		return profiles;
	}

	public static Optional<Profile> byKey(String key) {
		return profiles.stream().filter(profile -> profile.key().equals(key)).findFirst();
	}

	/** Reads one profile file; problems are logged and the file is skipped. */
	static Optional<Profile> read(Path file) {
		String fileName = file.getFileName().toString();
		String key = fileName.substring(0, fileName.length() - ".json".length());

		try (Reader reader = Files.newBufferedReader(file)) {
			JsonElement root = JsonParser.parseReader(reader);

			if (!root.isJsonObject()) {
				SaberCraft.LOGGER.warn("The profile {} is not a JSON object", fileName);
				return Optional.empty();
			}

			JsonObject json = root.getAsJsonObject();
			String name = string(json, "name");
			Path skin = null;
			String skinName = string(json, "skin");

			if (skinName != null) {
				Path candidate = file.getParent().resolve(skinName).normalize();

				if (candidate.startsWith(file.getParent().normalize()) && Files.isRegularFile(candidate)) {
					skin = candidate;
				} else {
					SaberCraft.LOGGER.warn("The profile {} names the skin {}, which is not a file in {}", fileName, skinName, file.getParent());
				}
			}

			Outfit base = null;
			String baseName = string(json, "base");

			if (baseName != null) {
				Identifier id = Identifier.tryParse(baseName.contains(":") ? baseName : SaberCraft.MOD_ID + ":" + baseName);
				base = id == null ? null : Outfit.byId(id).orElse(null);

				if (base == null) {
					SaberCraft.LOGGER.warn("The profile {} names the unknown character {}", fileName, baseName);
				}
			}

			SaberVariant saber = saber(json, fileName);
			Outfit.Side side = side(string(json, "side"));
			float scale = json.has("scale") && json.get("scale").isJsonPrimitive() ? Mth.clamp(json.get("scale").getAsFloat(), 0.5F, 1.0F) : 1.0F;
			return Optional.of(new Profile(key, name != null && !name.isBlank() ? name : key, skin, base, saber, side, scale));
		} catch (IOException | RuntimeException error) {
			SaberCraft.LOGGER.warn("Could not read the profile {}: {}", fileName, error.toString());
			return Optional.empty();
		}
	}

	private static @Nullable SaberVariant saber(JsonObject json, String fileName) {
		String hilt = string(json, "hilt");

		if (hilt == null) {
			return null;
		}

		Identifier hiltId = id(hilt);
		Identifier colorId = id(Optional.ofNullable(string(json, "color")).orElse("sabercraft:red"));
		Identifier styleId = hiltId == null ? null : id(Optional.ofNullable(string(json, "style")).orElse(SaberVariant.defaultStyle(hiltId).toString()));

		if (hiltId == null || colorId == null || styleId == null) {
			SaberCraft.LOGGER.warn("The profile {} has a saber id that is not valid", fileName);
			return null;
		}

		SaberVariant saber = new SaberVariant(hiltId, colorId, styleId);

		if (!saber.isKnown()) {
			SaberCraft.LOGGER.warn("The profile {} has a saber the mod does not know or that does not fit together: {}", fileName, saber);
			return null;
		}

		return saber;
	}

	private static @Nullable Identifier id(String value) {
		return Identifier.tryParse(value.contains(":") ? value : SaberCraft.MOD_ID + ":" + value);
	}

	private static Outfit.Side side(@Nullable String value) {
		if (value == null) {
			return Outfit.Side.NONE;
		}

		return switch (value.toLowerCase(Locale.ROOT)) {
			case "light" -> Outfit.Side.LIGHT;
			case "dark" -> Outfit.Side.DARK;
			case "grey", "gray" -> Outfit.Side.GREY;
			default -> Outfit.Side.NONE;
		};
	}

	private static @Nullable String string(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
	}
}
