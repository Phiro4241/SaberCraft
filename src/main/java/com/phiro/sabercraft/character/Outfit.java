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
 * <p>Since Phase 8 there are also film characters. They are drawn the same way, as a layer over
 * the skin, but cover the whole figure including the face. Their textures are own pixel art, and
 * each player can swap them for a skin file of their own (see the client's character menu).
 *
 * @param id        the outfit, also the name of its texture {@code textures/outfit/<path>.png}
 * @param suggested the saber that goes with it
 * @param coversHead whether it has a hood or helmet, which then hides the hat layer of the skin
 * @param character  whether it is a film character (Phase 8): those also paint the face, and
 *                   choosing one also gives the saber in hand the character's saber
 */
public record Outfit(Identifier id, SaberVariant suggested, boolean coversHead, boolean character) {
	public static final Outfit WANDERER = new Outfit(SaberCraft.id("wanderer"),
			new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.BLUE, SaberVariant.FLOWING), false, false);
	public static final Outfit SHADOW = new Outfit(SaberCraft.id("shadow"),
			new SaberVariant(SaberVariant.DARK_LORD, SaberVariant.RED, SaberVariant.HEAVY), true, false);
	public static final Outfit SENTINEL = new Outfit(SaberCraft.id("sentinel"),
			new SaberVariant(SaberVariant.SENTINEL, SaberVariant.GREEN, SaberVariant.DEFENSIVE), false, false);
	public static final Outfit SCOUT = new Outfit(SaberCraft.id("scout"),
			new SaberVariant(SaberVariant.STANDARD, SaberVariant.YELLOW, SaberVariant.AGILE), false, false);
	public static final Outfit TWILIGHT = new Outfit(SaberCraft.id("twilight"),
			new SaberVariant(SaberVariant.DOUBLE, SaberVariant.PURPLE, SaberVariant.STAFF), true, false);
	/** Darth Vader: black armor, helmet and cape; red blade, Djem So. */
	public static final Outfit VADER = new Outfit(SaberCraft.id("vader"),
			new SaberVariant(SaberVariant.DARK_LORD, SaberVariant.RED, SaberVariant.DJEM_SO), true, true);
	/** Obi-Wan Kenobi: Jedi robes, auburn hair and beard; blue blade, Soresu. */
	public static final Outfit OBI_WAN = new Outfit(SaberCraft.id("obi_wan"),
			new SaberVariant(SaberVariant.KENOBI, SaberVariant.BLUE, SaberVariant.SORESU), true, true);
	/** Darth Maul: black robes, red and black face, horns; red double blade, Juyo. */
	public static final Outfit MAUL = new Outfit(SaberCraft.id("maul"),
			new SaberVariant(SaberVariant.DOUBLE, SaberVariant.RED, SaberVariant.JUYO), true, true);
	/** Luke Skywalker as a Jedi knight: black tunic, one black glove; green blade, flowing style. */
	public static final Outfit LUKE = new Outfit(SaberCraft.id("luke"),
			new SaberVariant(SaberVariant.SKYWALKER, SaberVariant.GREEN, SaberVariant.FLOWING), true, true);
	/** The original outfits of Phase 7, in menu order. */
	public static final List<Outfit> OUTFITS = List.of(WANDERER, SHADOW, SENTINEL, SCOUT, TWILIGHT);
	/** The film characters of Phase 8, in menu order. */
	public static final List<Outfit> CHARACTERS = List.of(VADER, OBI_WAN, MAUL, LUKE);
	/** Everything a player can wear; the server only accepts these. */
	public static final List<Outfit> ALL = List.of(WANDERER, SHADOW, SENTINEL, SCOUT, TWILIGHT, VADER, OBI_WAN, MAUL, LUKE);

	public static Optional<Outfit> byId(Identifier id) {
		return ALL.stream().filter(outfit -> outfit.id().equals(id)).findFirst();
	}

	/** The translation key of the name, {@code sabercraft.outfit.<path>} or {@code sabercraft.character.<path>}. */
	public String translationKey() {
		return (this.character ? "sabercraft.character." : "sabercraft.outfit.") + this.id.getPath();
	}
}
