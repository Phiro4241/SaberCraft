package com.phiro.sabercraft.client.lang;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;

/**
 * Phase 19: SaberCraft's own language, independent of Minecraft's: automatic (as Minecraft),
 * German or English. It covers every text of the mod (story, menus, hints, the crew), that is
 * every key of the mod's own language files: they are read once more on every resource reload,
 * and a thin layer over Minecraft's language answers those keys in the chosen language (English
 * where a text is missing). Minecraft's own texts stay in Minecraft's language.
 *
 * <p>Changing the setting puts a new layer in place: texts remember the language they were
 * made in and are made again for a new one, so everything on the screen changes at once.
 */
public final class SaberLanguage {
	public static final String AUTO = "auto";
	public static final String GERMAN = "de_de";
	public static final String ENGLISH = "en_us";
	public static final List<String> CHOICES = List.of(AUTO, GERMAN, ENGLISH);

	/** The mod's own texts per language code. */
	private static Map<String, Map<String, String>> tables = Map.of();
	/** Minecraft's language under the layer. */
	private static @Nullable Language base;

	private SaberLanguage() {
	}

	/** After Minecraft (re)loaded its language: read the mod's texts and lay the layer over it. */
	public static void reload(ResourceManager manager) {
		Map<String, Map<String, String>> read = new HashMap<>();

		for (String code : List.of(ENGLISH, GERMAN)) {
			Map<String, String> table = new HashMap<>();

			for (Resource resource : manager.getResourceStack(Identifier.fromNamespaceAndPath(SaberCraft.MOD_ID, "lang/" + code + ".json"))) {
				try (InputStream stream = resource.open()) {
					Language.loadFromJson(stream, table::put);
				} catch (Exception e) {
					SaberCraft.LOGGER.warn("Could not read the SaberCraft texts for {}: {}", code, e.toString());
				}
			}

			read.put(code, Map.copyOf(table));
		}

		tables = Map.copyOf(read);
		Language current = Language.getInstance();
		base = current instanceof Layer layer ? layer.base : current;
		apply();
	}

	/** Lays a new layer for the current setting over Minecraft's language. */
	public static void apply() {
		if (base == null) {
			return;
		}

		Map<String, String> own = new HashMap<>();
		String code = chosen();

		if (code != null) {
			own.putAll(tables.getOrDefault(ENGLISH, Map.of()));

			if (!ENGLISH.equals(code)) {
				own.putAll(tables.getOrDefault(code, Map.of()));
			}
		}

		Language.inject(new Layer(base, Map.copyOf(own)));
	}

	/** Sets and saves the language ({@link #AUTO}, {@link #GERMAN} or {@link #ENGLISH}). */
	public static void set(String code) {
		ImmersionConfig.get().language = CHOICES.contains(code) ? code : AUTO;
		ImmersionConfig.save();
		apply();
	}

	/** The setting. */
	public static String setting() {
		String code = ImmersionConfig.get().language;
		return code == null || !CHOICES.contains(code) ? AUTO : code;
	}

	/** The language the mod's texts are in when not automatic, or {@code null} (as Minecraft). */
	public static @Nullable String chosen() {
		String code = setting();
		return AUTO.equals(code) ? null : code;
	}

	/** The language the mod's texts are shown in now (for tests and the menu). */
	public static String effective() {
		String code = chosen();

		if (code != null) {
			return code;
		}

		Minecraft client = Minecraft.getInstance();
		return client != null && client.getLanguageManager() != null ? client.getLanguageManager().getSelected().toLowerCase(Locale.ROOT) : ENGLISH;
	}

	/** Whether every key of the mod's English texts has a German one too, and the other way round (for tests). */
	public static List<String> missingForTest() {
		Map<String, String> en = tables.getOrDefault(ENGLISH, Map.of());
		Map<String, String> de = tables.getOrDefault(GERMAN, Map.of());
		List<String> missing = new java.util.ArrayList<>();
		en.keySet().stream().filter(key -> !de.containsKey(key)).map(key -> "de_de: " + key).forEach(missing::add);
		de.keySet().stream().filter(key -> !en.containsKey(key)).map(key -> "en_us: " + key).forEach(missing::add);
		return missing;
	}

	/** Whether the mod's texts in that language ({@link #GERMAN}, {@link #ENGLISH}) have that key (for tests). */
	public static boolean hasForTest(String code, String key) {
		return tables.getOrDefault(code, Map.of()).containsKey(key);
	}

	/**
	 * Phase 33: where a text of the mod names the player (the story's people speak to them, the
	 * name over their own lines in a dialog), it holds this mark, and the layer puts the player's
	 * own Minecraft name in its place. Each client fills in its own player, so in a game of several
	 * everyone reads their own name; the server only ever sends the text's key.
	 */
	public static final String PLAYER = "{player}";

	/** Phase 33: the name put in for {@link #PLAYER}: the player's Minecraft name (the account's before a world is joined). */
	public static String playerName() {
		Minecraft client = Minecraft.getInstance();

		if (client != null && client.player != null) {
			String name = client.player.getGameProfile().name();

			if (name != null && !name.isBlank()) {
				return name;
			}
		}

		return client != null && client.getUser() != null ? client.getUser().getName() : "";
	}

	/** Phase 33: the text with the player's name put in (for the layer, and for tests with any name). */
	public static String named(String text, String name) {
		return text.indexOf('{') >= 0 && text.contains(PLAYER) ? text.replace(PLAYER, name) : text;
	}

	/** Minecraft's language with the mod's texts answered in the chosen language. */
	static final class Layer extends Language {
		final Language base;
		private final Map<String, String> own;

		Layer(Language base, Map<String, String> own) {
			this.base = base;
			this.own = own;
		}

		@Override
		public String getOrDefault(String key, String fallback) {
			String text = this.own.get(key);

			if (text == null) {
				text = this.base.getOrDefault(key, fallback);
			}

			// (Also with the language on automatic: the mod's texts come from Minecraft's language then.)
			return text != null && key.startsWith(SaberCraft.MOD_ID + ".") ? named(text, playerName()) : text;
		}

		@Override
		public boolean has(String key) {
			return this.own.containsKey(key) || this.base.has(key);
		}

		@Override
		public boolean isDefaultRightToLeft() {
			return this.base.isDefaultRightToLeft();
		}

		@Override
		public FormattedCharSequence getVisualOrder(FormattedText text) {
			return this.base.getVisualOrder(text);
		}
	}
}
