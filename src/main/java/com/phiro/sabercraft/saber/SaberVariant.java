package com.phiro.sabercraft.saber;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
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

	/** All hilts in menu order. The server only accepts these. */
	public static final List<Identifier> HILTS = List.of(STANDARD, GUARDIAN, DARK_LORD, SENTINEL, DOUBLE);
	/** All blade colors in menu order. The server only accepts these. */
	public static final List<Identifier> COLORS = List.of(RED, BLUE, GREEN, PURPLE, YELLOW, WHITE);
	/** All fighting styles. The staff style belongs to the double-bladed hilt only. */
	public static final List<Identifier> STYLES = List.of(FLOWING, HEAVY, STAFF);
	/** Styles a one-bladed hilt can use, in menu order. */
	public static final List<Identifier> SINGLE_STYLES = List.of(FLOWING, HEAVY);

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

		return DARK_LORD.equals(hilt) ? HEAVY : FLOWING;
	}

	/** True if the style fits the hilt: the staff style for the double hilt, the others otherwise. */
	public static boolean styleFits(Identifier hilt, Identifier style) {
		return DOUBLE.equals(hilt) ? STAFF.equals(style) : SINGLE_STYLES.contains(style);
	}

	/** True if all ids are known and fit together. Everything sent to the server is checked with this. */
	public boolean isKnown() {
		return HILTS.contains(this.hilt) && COLORS.contains(this.color) && styleFits(this.hilt, this.style);
	}

	/** Items that turn into a saber. */
	public static boolean canHold(ItemStack stack) {
		return stack.is(Items.NETHERITE_SWORD);
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
