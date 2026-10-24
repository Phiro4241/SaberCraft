package com.phiro.sabercraft.client.blade;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Registry of all blade styles, one per blade color.
 *
 * <p>The constants below are the built-in defaults. {@link BladeStyleConfig} can replace any of
 * them with the values from the config file; {@link #get} always returns the configured version, so
 * a later config screen can change a color while the game runs.
 */
public final class BladeStyles {
	private static final Map<Identifier, BladeStyle> DEFAULTS = new LinkedHashMap<>();
	private static final Map<Identifier, BladeStyle> CONFIGURED = new LinkedHashMap<>();

	/** Deep red blade with a warm, almost white core. Unchanged since Phase 3. */
	public static final BladeStyle RED = register(BladeStyle.of(SaberVariant.RED, 0xFFF0EC, 0xFF1A0A));
	/** Cool blue blade with a slightly blue-white core and a light blue bloom. */
	public static final BladeStyle BLUE = register(BladeStyle.of(SaberVariant.BLUE, 0xF2F7FF, 0x8CB8FF, 0x1E5CFF));
	/** Vivid green. Green looks brighter to the eye than red or blue, so the halo is a bit weaker. */
	public static final BladeStyle GREEN = register(
			BladeStyle.of(SaberVariant.GREEN, 0xF2FFF2, 0x9CFFA4, 0x14E03A).withStrength(0.95F, 0.9F)
	);
	/** Violet blade between magenta and blue, with a pink-white core. */
	public static final BladeStyle PURPLE = register(BladeStyle.of(SaberVariant.PURPLE, 0xFBF2FF, 0xD49CFF, 0x9426FF));
	/** Warm yellow-amber blade. Yellow on a bright sky washes out easily, so the halo is warmer. */
	public static final BladeStyle YELLOW = register(
			BladeStyle.of(SaberVariant.YELLOW, 0xFFFDF0, 0xFFE58A, 0xFFB80F).withStrength(0.9F, 0.9F)
	);
	/** White blade with a faint cool halo, so it still reads as light and not as a white stick. */
	public static final BladeStyle WHITE = register(
			BladeStyle.of(SaberVariant.WHITE, 0xFFFFFF, 0xF4F7FF, 0xC9D8FF).withStrength(0.85F, 0.75F)
	);

	private BladeStyles() {
	}

	public static BladeStyle register(BladeStyle style) {
		if (DEFAULTS.putIfAbsent(style.id(), style) != null) {
			throw new IllegalArgumentException("Duplicate blade style " + style.id());
		}

		return style;
	}

	/** Replaces a style with configured values. Only known styles can be configured. */
	public static void configure(BladeStyle style) {
		if (!DEFAULTS.containsKey(style.id())) {
			throw new IllegalArgumentException("Unknown blade style " + style.id());
		}

		CONFIGURED.put(style.id(), style);
	}

	/** Returns the style with the given id, or the red blade if the id is unknown. */
	public static BladeStyle get(Identifier id) {
		BladeStyle style = find(id);

		if (style == null) {
			SaberCraft.LOGGER.warn("Unknown blade style {}, falling back to {}", id, RED.id());
			return get(RED.id());
		}

		return style;
	}

	/** Returns the configured style with the given id, or {@code null} if there is none. */
	public static @Nullable BladeStyle find(Identifier id) {
		BladeStyle configured = CONFIGURED.get(id);
		return configured != null ? configured : DEFAULTS.get(id);
	}

	/**
	 * The blade color stored on a saber, or {@code null} if the stack has none or an unknown one.
	 * Then the item model's own style applies, which is the default color of the hilt.
	 */
	public static @Nullable Identifier colorOf(ItemStack stack) {
		Identifier color = SaberVariant.colorOf(stack);
		return color != null && DEFAULTS.containsKey(color) ? color : null;
	}

	/** The built-in style, ignoring the config file. */
	public static BladeStyle defaultStyle(Identifier id) {
		return DEFAULTS.getOrDefault(id, RED);
	}

	public static Map<Identifier, BladeStyle> all() {
		Map<Identifier, BladeStyle> styles = new LinkedHashMap<>();

		for (Identifier id : DEFAULTS.keySet()) {
			styles.put(id, find(id));
		}

		return Collections.unmodifiableMap(styles);
	}

	public static Collection<Identifier> ids() {
		return Collections.unmodifiableSet(DEFAULTS.keySet());
	}
}
