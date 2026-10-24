package com.phiro.sabercraft.client.blade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;

/**
 * Registry of all known blade styles. Phase 3 ships the red blade; blue, green and other variants
 * are added here later with one line each.
 */
public final class BladeStyles {
	private static final Map<Identifier, BladeStyle> STYLES = new LinkedHashMap<>();

	/** Deep red blade with a warm, almost white core. */
	public static final BladeStyle RED = register(BladeStyle.of(SaberCraft.id("red"), 0xFFF0EC, 0xFF1A0A));

	private BladeStyles() {
	}

	public static BladeStyle register(BladeStyle style) {
		if (STYLES.putIfAbsent(style.id(), style) != null) {
			throw new IllegalArgumentException("Duplicate blade style " + style.id());
		}

		return style;
	}

	/** Returns the style with the given id, or the red blade if the id is unknown. */
	public static BladeStyle get(Identifier id) {
		BladeStyle style = STYLES.get(id);

		if (style == null) {
			SaberCraft.LOGGER.warn("Unknown blade style {}, falling back to {}", id, RED.id());
			return RED;
		}

		return style;
	}

	public static Map<Identifier, BladeStyle> all() {
		return Collections.unmodifiableMap(STYLES);
	}
}
