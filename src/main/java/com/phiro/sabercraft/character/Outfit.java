package com.phiro.sabercraft.character;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * A built-in outfit (Phase 7): an original robe or armor. Each outfit suggests a saber (hilt,
 * color and fighting style) that fits it.
 *
 * <p>Since Phase 8 there are also film characters. Their textures are own pixel art, and each
 * player can swap them for a skin file of their own (see the client's character menu).
 *
 * <p>Since Phase 10 every outfit and character is a complete skin with its own face and its own
 * second layer. On the client it replaces the player's skin itself (texture, model shape, second
 * layer, cape), instead of being painted over it; see {@code OutfitClient}.
 *
 * @param id        the outfit, also the name of its texture {@code textures/outfit/<path>.png}
 * @param suggested the saber that goes with it
 * @param character  whether it is a film character (Phase 8): choosing one also gives the saber
 *                   in hand the character's saber
 * @param side       which side of the Force a film character fights for (Phase 9), {@link Side#NONE}
 *                   for the original outfits
 * @param scale      how big the figure is drawn, 1 for everyone but small characters like Yoda
 *                   (Phase 9). Only the picture is smaller; the hitbox and the camera stay.
 */
public record Outfit(Identifier id, SaberVariant suggested, boolean character, Side side, float scale) {
	/** The side of the Force, shown in the character menu. */
	public enum Side {
		NONE, LIGHT, DARK, GREY;

		public String translationKey() {
			return "sabercraft.side." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** An original outfit of Phase 7. */
	private Outfit(Identifier id, SaberVariant suggested) {
		this(id, suggested, false, Side.NONE, 1.0F);
	}

	/** A film character at the normal size. */
	private static Outfit character(String id, SaberVariant suggested, Side side) {
		return new Outfit(SaberCraft.id(id), suggested, true, side, 1.0F);
	}

	public static final Outfit WANDERER = new Outfit(SaberCraft.id("wanderer"),
			new SaberVariant(SaberVariant.GUARDIAN, SaberVariant.BLUE, SaberVariant.FLOWING));
	public static final Outfit SHADOW = new Outfit(SaberCraft.id("shadow"),
			new SaberVariant(SaberVariant.DARK_LORD, SaberVariant.RED, SaberVariant.HEAVY));
	public static final Outfit SENTINEL = new Outfit(SaberCraft.id("sentinel"),
			new SaberVariant(SaberVariant.SENTINEL, SaberVariant.GREEN, SaberVariant.DEFENSIVE));
	public static final Outfit SCOUT = new Outfit(SaberCraft.id("scout"),
			new SaberVariant(SaberVariant.STANDARD, SaberVariant.YELLOW, SaberVariant.AGILE));
	public static final Outfit TWILIGHT = new Outfit(SaberCraft.id("twilight"),
			new SaberVariant(SaberVariant.DOUBLE, SaberVariant.PURPLE, SaberVariant.STAFF));
	/** Darth Vader: black armor, helmet and cape; red blade, Djem So. */
	public static final Outfit VADER = character("vader",
			new SaberVariant(SaberVariant.DARK_LORD, SaberVariant.RED, SaberVariant.DJEM_SO), Side.DARK);
	/** Obi-Wan Kenobi: Jedi robes, auburn hair and beard; blue blade, Soresu. */
	public static final Outfit OBI_WAN = character("obi_wan",
			new SaberVariant(SaberVariant.KENOBI, SaberVariant.BLUE, SaberVariant.SORESU), Side.LIGHT);
	/** Darth Maul: black robes, red and black face, horns; red double blade, Juyo. */
	public static final Outfit MAUL = character("maul",
			new SaberVariant(SaberVariant.DOUBLE, SaberVariant.RED, SaberVariant.JUYO), Side.DARK);
	/** Luke Skywalker as a Jedi knight: black tunic, one black glove; green blade, flowing style. */
	public static final Outfit LUKE = character("luke",
			new SaberVariant(SaberVariant.SKYWALKER, SaberVariant.GREEN, SaberVariant.FLOWING), Side.LIGHT);
	/** Anakin Skywalker as a Jedi knight: dark tunic and robe, a scar over the eye; blue blade, Ansturm. */
	public static final Outfit ANAKIN = character("anakin",
			new SaberVariant(SaberVariant.ANAKIN, SaberVariant.BLUE, SaberVariant.ONSLAUGHT), Side.LIGHT);
	/** Yoda: small, green, big ears and a brown robe; a short green blade, Ataru. Drawn at a bit over half size. */
	public static final Outfit YODA = new Outfit(SaberCraft.id("yoda"),
			new SaberVariant(SaberVariant.YODA, SaberVariant.GREEN, SaberVariant.ATARU), true, Side.LIGHT, 0.56F);
	/** Count Dooku: white beard, dark suit and a cape with a silver clasp; red blade with a curved hilt, Makashi. */
	public static final Outfit DOOKU = character("dooku",
			new SaberVariant(SaberVariant.DOOKU, SaberVariant.RED, SaberVariant.MAKASHI), Side.DARK);
	/** Ahsoka Tano: orange skin with white markings, blue and white striped head-tails; white blade, reverse grip. */
	public static final Outfit AHSOKA = character("ahsoka",
			new SaberVariant(SaberVariant.TANO, SaberVariant.WHITE, SaberVariant.REVERSE_SHIEN), Side.GREY);
	/** Kylo Ren: black helmet with silver trim, black hooded robes; unstable red crossguard blade, Ungezügelt. */
	public static final Outfit KYLO = character("kylo",
			new SaberVariant(SaberVariant.CROSSGUARD, SaberVariant.RED, SaberVariant.UNBRIDLED), Side.DARK);
	/** Rey: light wrapped desert clothes, brown hair; yellow blade on her own hilt, Shii-Cho. */
	public static final Outfit REY = character("rey",
			new SaberVariant(SaberVariant.REY, SaberVariant.YELLOW, SaberVariant.SHII_CHO), Side.LIGHT);
	/** The original outfits of Phase 7, in menu order. */
	public static final List<Outfit> OUTFITS = List.of(WANDERER, SHADOW, SENTINEL, SCOUT, TWILIGHT);
	/** The film characters of Phase 8 and 9, in menu order: by era, light and dark side in turns. */
	public static final List<Outfit> CHARACTERS = List.of(YODA, DOOKU, OBI_WAN, MAUL, ANAKIN, AHSOKA, VADER, LUKE, KYLO, REY);
	/** Everything a player can wear; the server only accepts these. */
	public static final List<Outfit> ALL = java.util.stream.Stream.concat(OUTFITS.stream(), CHARACTERS.stream()).toList();

	public static Optional<Outfit> byId(Identifier id) {
		return ALL.stream().filter(outfit -> outfit.id().equals(id)).findFirst();
	}

	/** The translation key of the name, {@code sabercraft.outfit.<path>} or {@code sabercraft.character.<path>}. */
	public String translationKey() {
		return (this.character ? "sabercraft.character." : "sabercraft.outfit.") + this.id.getPath();
	}
}
