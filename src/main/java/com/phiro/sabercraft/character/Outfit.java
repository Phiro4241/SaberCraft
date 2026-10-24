package com.phiro.sabercraft.character;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * A built-in outfit (Phase 7): an original robe or armor drawn as an extra layer over the
 * player's own skin. The skin itself is never changed. Each outfit suggests a saber (hilt, color
 * and fighting style) that fits it.
 *
 * @param id        the outfit, also the name of its texture {@code textures/outfit/<path>.png}
 * @param suggested the saber that goes with it
 * @param coversHead whether it has a hood or helmet, which then hides the hat layer of the skin
 */
public record Outfit(Identifier id, SaberVariant suggested, boolean coversHead) {
	public static final Outfit WANDERER = new Outfit(SaberCraft.id("wanderer"),
			new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.BLUE, SaberVariant.FLOWING), false);
	public static final Outfit SHADOW = new Outfit(SaberCraft.id("shadow"),
			new SaberVariant(SaberVariant.DARK_LORD, SaberVariant.RED, SaberVariant.HEAVY), true);
	public static final Outfit SENTINEL = new Outfit(SaberCraft.id("sentinel"),
			new SaberVariant(SaberVariant.SENTINEL, SaberVariant.GREEN, SaberVariant.DEFENSIVE), false);
	public static final Outfit SCOUT = new Outfit(SaberCraft.id("scout"),
			new SaberVariant(SaberVariant.STANDARD, SaberVariant.YELLOW, SaberVariant.AGILE), false);
	public static final Outfit TWILIGHT = new Outfit(SaberCraft.id("twilight"),
			new SaberVariant(SaberVariant.DOUBLE, SaberVariant.PURPLE, SaberVariant.STAFF), true);
	public static final List<Outfit> ALL = List.of(WANDERER, SHADOW, SENTINEL, SCOUT, TWILIGHT);

	public static Optional<Outfit> byId(Identifier id) {
		return ALL.stream().filter(outfit -> outfit.id().equals(id)).findFirst();
	}

	/** The translation key of the name, {@code sabercraft.outfit.<path>}. */
	public String translationKey() {
		return "sabercraft.outfit." + this.id.getPath();
	}
}
