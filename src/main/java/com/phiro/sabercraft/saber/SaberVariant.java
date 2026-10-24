package com.phiro.sabercraft.saber;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import com.phiro.sabercraft.SaberCraft;

/**
 * Which hilt and which blade color one saber has.
 *
 * <p>The choice is stored on the item itself, in the vanilla {@code minecraft:custom_model_data}
 * component: {@code strings[0]} is the hilt, {@code strings[1]} the color. So every sword keeps its
 * own variant when it is moved, dropped, traded or put in a chest. The component is part of the
 * normal item sync, so every client sees the variant without any extra packets, and players
 * without SaberCraft simply see a netherite sword. The item model
 * ({@code assets/minecraft/items/netherite_sword.json}) picks the hilt from it, and the blade
 * renderer picks the color.
 *
 * <p>A sword without the component looks like before Phase 5: the standard hilt with a red blade.
 * Since Phase 6 {@code strings[2]} holds the fighting style. Swords without one fight in the
 * usual style of their hilt, see {@link #defaultStyle}.
 *
 * <p>Since Phase 10 every vanilla sword from wood to netherite can be a saber ({@link #SWORDS},
 * {@link #canHold}); a converted sword stays the same item with the same material, durability and
 * enchantments, only its model data changes (see {@code ItemConversion}).
 *
 * @param hilt  hilt id, one of {@link #HILTS}
 * @param color blade color id, one of {@link #COLORS}
 * @param style fighting style id, one of {@link #STYLES}
 */
public record SaberVariant(Identifier hilt, Identifier color, Identifier style) {
	public static final Identifier STANDARD = SaberCraft.id("standard");
	public static final Identifier GUARDIAN = SaberCraft.id("guardian");
	public static final Identifier DARK_LORD = SaberCraft.id("dark_lord");
	public static final Identifier SENTINEL = SaberCraft.id("sentinel");
	public static final Identifier DOUBLE = SaberCraft.id("double");
	/** Silver hilt with a flared, vented emitter, after Obi-Wan Kenobi. Since Phase 8. */
	public static final Identifier KENOBI = SaberCraft.id("kenobi");
	/** Black and silver hilt with a long emitter cone, after Luke Skywalker. Since Phase 8. */
	public static final Identifier SKYWALKER = SaberCraft.id("skywalker");
	/** Black grip with silver rings and an angled, clawed emitter, after Anakin Skywalker. Since Phase 9. */
	public static final Identifier ANAKIN = SaberCraft.id("anakin");
	/** Small, stout hilt with a short blade, after Yoda. Since Phase 9. */
	public static final Identifier YODA = SaberCraft.id("yoda");
	/** Curved, elegant hilt for one-handed fencing, after Count Dooku. Since Phase 9. */
	public static final Identifier DOOKU = SaberCraft.id("dooku");
	/** Slim white and silver hilt for the reverse grip, after Ahsoka Tano. Since Phase 9. */
	public static final Identifier TANO = SaberCraft.id("tano");
	/** Rough hilt with two short side blades and an unstable blade, after Kylo Ren. Since Phase 9. */
	public static final Identifier CROSSGUARD = SaberCraft.id("crossguard");
	/** Wrapped, bronze hilt made from a staff, after Rey. Since Phase 9. */
	public static final Identifier REY = SaberCraft.id("rey");

	public static final Identifier RED = SaberCraft.id("red");
	public static final Identifier BLUE = SaberCraft.id("blue");
	public static final Identifier GREEN = SaberCraft.id("green");
	public static final Identifier PURPLE = SaberCraft.id("purple");
	public static final Identifier YELLOW = SaberCraft.id("yellow");
	public static final Identifier WHITE = SaberCraft.id("white");

	/** Flowing style inspired by the knights of light: quick cuts that flow into each other. */
	public static final Identifier FLOWING = SaberCraft.id("flowing");
	/** Heavy style inspired by the dark side: slower, wide, powerful two-handed blows. */
	public static final Identifier HEAVY = SaberCraft.id("heavy");
	/** Style of the double-bladed staff: spins and sweeps with both blades. */
	public static final Identifier STAFF = SaberCraft.id("staff");
	/** Defensive style: compact stance, long parry window, saves stamina. Since Phase 7. */
	public static final Identifier DEFENSIVE = SaberCraft.id("defensive");
	/** Agile style: short quick cuts, jumps and turns, fast dodges. Since Phase 7. */
	public static final Identifier AGILE = SaberCraft.id("agile");
	/** Djem So, Darth Vader's style: slow, crushing two-handed blows and a firm guard. Since Phase 8. */
	public static final Identifier DJEM_SO = SaberCraft.id("djem_so");
	/** Soresu, Obi-Wan Kenobi's style: controlled defense from a raised guard, quick ripostes. Since Phase 8. */
	public static final Identifier SORESU = SaberCraft.id("soresu");
	/** Juyo, Darth Maul's style with the double blade: fast spins and sweeps. Since Phase 8. */
	public static final Identifier JUYO = SaberCraft.id("juyo");
	/** Ansturm, Anakin Skywalker's style: relentless, fast power blows with leaps forward. Since Phase 9. */
	public static final Identifier ONSLAUGHT = SaberCraft.id("onslaught");
	/** Ataru, Yoda's style: acrobatic somersaults, spinning leaps and whirling cuts. Since Phase 9. */
	public static final Identifier ATARU = SaberCraft.id("ataru");
	/** Makashi, Count Dooku's style: one-handed fencing, precise thrusts and flicks. Since Phase 9. */
	public static final Identifier MAKASHI = SaberCraft.id("makashi");
	/** Shien with the reverse grip, Ahsoka Tano's style: the blade points back from the fist. Since Phase 9. */
	public static final Identifier REVERSE_SHIEN = SaberCraft.id("reverse_shien");
	/** Ungezügelt, Kylo Ren's style: wild, raw hammering blows driven by rage. Since Phase 9. */
	public static final Identifier UNBRIDLED = SaberCraft.id("unbridled");
	/** Shii-Cho, Rey's style: wide sweeping two-handed cuts, trained with a staff. Since Phase 9. */
	public static final Identifier SHII_CHO = SaberCraft.id("shii_cho");

	/** All hilts in menu order. The server only accepts these. */
	public static final List<Identifier> HILTS = List.of(STANDARD, GUARDIAN, DARK_LORD, SENTINEL, KENOBI, SKYWALKER, ANAKIN, YODA, DOOKU, TANO, CROSSGUARD, REY, DOUBLE);
	/** All blade colors in menu order. The server only accepts these. */
	public static final List<Identifier> COLORS = List.of(RED, BLUE, GREEN, PURPLE, YELLOW, WHITE);
	/** All fighting styles. The staff and Juyo styles belong to the double-bladed hilt only. */
	public static final List<Identifier> STYLES = List.of(FLOWING, HEAVY, DEFENSIVE, AGILE, DJEM_SO, SORESU, ONSLAUGHT, ATARU, MAKASHI, REVERSE_SHIEN,
			UNBRIDLED, SHII_CHO, STAFF, JUYO);
	/** Styles a one-bladed hilt can use, in menu order. */
	public static final List<Identifier> SINGLE_STYLES = List.of(FLOWING, HEAVY, DEFENSIVE, AGILE, DJEM_SO, SORESU, ONSLAUGHT, ATARU, MAKASHI,
			REVERSE_SHIEN, UNBRIDLED, SHII_CHO);
	/** Styles of the double-bladed hilt, in menu order. */
	public static final List<Identifier> DOUBLE_STYLES = List.of(STAFF, JUYO);

	/** The look of a sword that was never changed. */
	public static final SaberVariant DEFAULT = new SaberVariant(STANDARD, RED);

	public static final StreamCodec<ByteBuf, SaberVariant> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, SaberVariant::hilt,
			Identifier.STREAM_CODEC, SaberVariant::color,
			Identifier.STREAM_CODEC, SaberVariant::style,
			SaberVariant::new
	);

	private static final int HILT_INDEX = 0;
	private static final int COLOR_INDEX = 1;
	private static final int STYLE_INDEX = 2;

	/** A variant with the usual fighting style of the hilt. */
	public SaberVariant(Identifier hilt, Identifier color) {
		this(hilt, color, defaultStyle(hilt));
	}

	/** The style a hilt fights with unless another one was chosen. */
	public static Identifier defaultStyle(@Nullable Identifier hilt) {
		if (DOUBLE.equals(hilt)) {
			return STAFF;
		}

		if (KENOBI.equals(hilt)) {
			return SORESU;
		}

		if (ANAKIN.equals(hilt)) {
			return ONSLAUGHT;
		} else if (YODA.equals(hilt)) {
			return ATARU;
		} else if (DOOKU.equals(hilt)) {
			return MAKASHI;
		} else if (TANO.equals(hilt)) {
			return REVERSE_SHIEN;
		} else if (CROSSGUARD.equals(hilt)) {
			return UNBRIDLED;
		} else if (REY.equals(hilt)) {
			return SHII_CHO;
		}

		return DARK_LORD.equals(hilt) ? HEAVY : FLOWING;
	}

	/** True if the style fits the hilt: the staff styles for the double hilt, the others otherwise. */
	public static boolean styleFits(Identifier hilt, Identifier style) {
		return DOUBLE.equals(hilt) ? DOUBLE_STYLES.contains(style) : SINGLE_STYLES.contains(style);
	}

	/** True for the styles of the double-bladed staff, which spin it in a whirl and never roll. */
	public static boolean isStaffStyle(@Nullable Identifier style) {
		return STAFF.equals(style) || JUYO.equals(style);
	}

	/** True if all ids are known and fit together. Everything sent to the server is checked with this. */
	public boolean isKnown() {
		return HILTS.contains(this.hilt) && COLORS.contains(this.color) && styleFits(this.hilt, this.style);
	}

	/**
	 * Marks a netherite sword that was turned back into a plain sword (Phase 10). Every other
	 * netherite sword is a saber, as in Phases 1 to 9.
	 */
	public static final Identifier PLAIN = SaberCraft.id("plain");
	/**
	 * The swords that can become a saber (Phase 10), from wood to netherite. The netherite sword is
	 * a saber from the start, the others after {@code /sabercraft convert}.
	 */
	public static final List<Item> SWORDS = List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD,
			Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);

	/**
	 * Items that are a saber: a netherite sword (unless turned back into a plain one), or any other
	 * sword of {@link #SWORDS} that was converted and so carries a known hilt. The material stays
	 * the sword's own: a wooden saber hits like a wooden sword.
	 */
	public static boolean canHold(ItemStack stack) {
		if (stack.is(Items.NETHERITE_SWORD)) {
			return !PLAIN.equals(hiltOf(stack));
		}

		Identifier hilt = isSword(stack) ? hiltOf(stack) : null;
		return hilt != null && HILTS.contains(hilt);
	}

	/** True for the swords that can be converted into a saber. */
	public static boolean isSword(ItemStack stack) {
		for (Item sword : SWORDS) {
			if (stack.is(sword)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The hilt a sword gets when it is converted without choosing one: one that fits its material.
	 * The netherite sword keeps the standard hilt of the earlier phases.
	 */
	public static Identifier hiltForMaterial(ItemStack stack) {
		if (stack.is(Items.WOODEN_SWORD)) {
			return REY;
		} else if (stack.is(Items.STONE_SWORD)) {
			return SENTINEL;
		} else if (stack.is(Items.COPPER_SWORD)) {
			return ANAKIN;
		} else if (stack.is(Items.IRON_SWORD)) {
			return GUARDIAN;
		} else if (stack.is(Items.GOLDEN_SWORD)) {
			return DOOKU;
		} else if (stack.is(Items.DIAMOND_SWORD)) {
			return KENOBI;
		}

		return STANDARD;
	}

	/** The hilt stored on the stack, or {@code null} if none is stored. */
	public static @Nullable Identifier hiltOf(ItemStack stack) {
		return read(stack, HILT_INDEX);
	}

	/** The blade color stored on the stack, or {@code null} if none is stored. */
	public static @Nullable Identifier colorOf(ItemStack stack) {
		return read(stack, COLOR_INDEX);
	}

	/**
	 * The fighting style of the stack: the stored one if it fits the hilt, otherwise the usual
	 * style of the hilt.
	 */
	public static Identifier styleOf(ItemStack stack) {
		Identifier hilt = hiltOf(stack);
		Identifier stored = read(stack, STYLE_INDEX);
		return stored != null && styleFits(hilt != null ? hilt : STANDARD, stored) ? stored : defaultStyle(hilt);
	}

	private static @Nullable Identifier read(ItemStack stack, int index) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		String value = data != null ? data.getString(index) : null;
		return value != null && !value.isEmpty() ? Identifier.tryParse(value) : null;
	}

	/**
	 * Stores this variant on the stack. Other values in the component (floats, flags, colors and
	 * further strings) are kept, in case a data pack uses them as well.
	 */
	public void applyTo(ItemStack stack) {
		CustomModelData old = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		List<String> strings = new ArrayList<>(old.strings());

		while (strings.size() <= STYLE_INDEX) {
			strings.add("");
		}

		strings.set(HILT_INDEX, this.hilt.toString());
		strings.set(COLOR_INDEX, this.color.toString());
		strings.set(STYLE_INDEX, this.style.toString());
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(old.floats(), old.flags(), List.copyOf(strings), old.colors()));
	}
}
